package dev.sifrekasa.core.passkey

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64

class PasskeyException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind {
        /** İstek ayrıştırılamadı ya da zorunlu alan eksik. */
        INVALID_REQUEST,
        /** Site ES256 kabul etmiyor. */
        NOT_SUPPORTED,
        /** excludeCredentials: bu hesap için bu kasada zaten bir geçiş anahtarı var. */
        ALREADY_REGISTERED,
    }
}

/** `navigator.credentials.create()` seçeneklerinden (PublicKeyCredentialCreationOptions JSON) gerekenler. */
class CreationOptions(
    val rpId: String,
    val rpName: String,
    val userHandle: String,
    val userName: String,
    val userDisplayName: String,
    val challenge: String,
    val excludeCredentialIds: Set<String>,
)

/** `navigator.credentials.get()` seçeneklerinden (PublicKeyCredentialRequestOptions JSON) gerekenler. */
class RequestOptions(val rpId: String, val challenge: String, val allowCredentialIds: Set<String>)

class CreatedPasskey(val passkey: Passkey, val responseJson: String)

/**
 * WebAuthn kayıt (attestation) ve oturum açma (assertion) yanıtlarını üretir.
 *
 * Çağıran taraf iki biçimde gelir (Android Credential Manager kılavuzu):
 *  - Ayrıcalıklı çağıran (tarayıcı, hybrid akışta Play Services): [clientDataHash] hazır gelir,
 *    clientDataJSON'u çağıran kendisi kurar; yanıta yer tutucu konur.
 *  - Yerel uygulama: clientDataJSON burada, `android:apk-key-hash:…` kökeniyle kurulur.
 *
 * Köken ile RP kimliği burada karşılaştırılmaz; kılavuz bunu sağlayıcıya bırakmaz (Related Origin
 * Requests). Bu denetim tarayıcının ve sitenin sunucusunun işidir.
 */
object WebAuthn {
    private const val FLAG_UP = 0x01
    private const val FLAG_UV = 0x04
    private const val FLAG_BE = 0x08
    private const val FLAG_BS = 0x10
    private const val FLAG_AT = 0x40
    /** Kullanıcı var + doğrulandı; anahtar yedeklenebilir ve (kasa yedeğiyle) yedeklenmiş sayılır. */
    private const val BASE_FLAGS = FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS
    private const val ES256 = -7
    private val AAGUID = ByteArray(16)
    private val PLACEHOLDER_CLIENT_DATA = "{}".toByteArray()
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    fun parseCreationOptions(optionsJson: String, origin: String?): CreationOptions {
        val root = parse(optionsJson)
        val rp = root["rp"] as? JsonObject ?: invalid("rp eksik")
        val user = root["user"] as? JsonObject ?: invalid("user eksik")
        val algorithms = (root["pubKeyCredParams"] as? JsonArray).orEmpty()
            .mapNotNull { ((it as? JsonObject)?.get("alg") as? JsonPrimitive)?.intOrNull }
        if (ES256 !in algorithms) throw PasskeyException(PasskeyException.Kind.NOT_SUPPORTED, "Site ES256 kabul etmiyor")
        val rpId = rp.string("id") ?: hostOf(origin) ?: invalid("rp.id eksik")
        return CreationOptions(
            rpId = rpId,
            rpName = rp.string("name").orEmpty(),
            userHandle = normalizeId(user.string("id") ?: invalid("user.id eksik")),
            userName = user.string("name").orEmpty(),
            userDisplayName = user.string("displayName").orEmpty(),
            challenge = root.string("challenge") ?: invalid("challenge eksik"),
            excludeCredentialIds = credentialIds(root["excludeCredentials"]),
        )
    }

    fun parseRequestOptions(optionsJson: String, origin: String?): RequestOptions {
        val root = parse(optionsJson)
        return RequestOptions(
            rpId = root.string("rpId") ?: hostOf(origin) ?: invalid("rpId eksik"),
            challenge = root.string("challenge") ?: invalid("challenge eksik"),
            allowCredentialIds = credentialIds(root["allowCredentials"]),
        )
    }

    /** Verilen isteğe uyan geçiş anahtarları: aynı RP ve (liste verilmişse) izin verilen kimlikler. */
    fun matching(passkeys: List<Passkey>, options: RequestOptions): List<Passkey> =
        passkeys.filter {
            it.rpId == options.rpId && (options.allowCredentialIds.isEmpty() || it.credentialId in options.allowCredentialIds)
        }

    /**
     * Yeni bir geçiş anahtarı üretir.
     * @param existing kasadaki mevcut geçiş anahtarları (excludeCredentials denetimi için)
     * @param clientDataHash ayrıcalıklı çağıranın verdiği özet; yoksa null
     * @param androidPackageName yerel uygulama çağırıyorsa paket adı
     */
    fun create(
        optionsJson: String,
        origin: String,
        clientDataHash: ByteArray?,
        androidPackageName: String?,
        existing: List<Passkey>,
        now: Long,
    ): CreatedPasskey {
        val options = parseCreationOptions(optionsJson, origin)
        if (existing.any { it.rpId == options.rpId && it.credentialId in options.excludeCredentialIds }) {
            throw PasskeyException(PasskeyException.Kind.ALREADY_REGISTERED, "Bu hesap için zaten bir geçiş anahtarı var")
        }

        val credentialId = ByteArray(32).also(random::nextBytes)
        val key = PasskeyCrypto.generate()
        val coseKey = PasskeyCrypto.coseKey(key.publicKey)
        val attested = ByteBuffer.allocate(AAGUID.size + 2 + credentialId.size + coseKey.size)
            .put(AAGUID).putShort(credentialId.size.toShort()).put(credentialId).put(coseKey).array()
        val authData = authenticatorData(options.rpId, BASE_FLAGS or FLAG_AT, attested)
        // "none" doğrulaması: kişisel bir kasa cihaz/üretici kanıtı sunmaz.
        val attestationObject = CborWriter().mapHeader(3)
            .text("fmt").text("none")
            .text("attStmt").mapHeader(0)
            .text("authData").bytes(authData)
            .toByteArray()
        val clientData = clientDataHash?.let { PLACEHOLDER_CLIENT_DATA }
            ?: clientDataJson("webauthn.create", options.challenge, origin, androidPackageName)

        val id = Base64Url.encode(credentialId)
        val response = buildJsonObject {
            put("id", id)
            put("rawId", id)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                put("clientDataJSON", Base64Url.encode(clientData))
                put("attestationObject", Base64Url.encode(attestationObject))
                put("authenticatorData", Base64Url.encode(authData))
                put("publicKey", Base64Url.encode(key.publicKey.encoded))
                put("publicKeyAlgorithm", ES256)
                put("transports", buildJsonArray { add(JsonPrimitive("internal")); add(JsonPrimitive("hybrid")) })
            }
            putJsonObject("clientExtensionResults") {
                putJsonObject("credProps") { put("rk", true) }
            }
        }
        val passkey = Passkey(
            credentialId = id,
            rpId = options.rpId,
            rpName = options.rpName,
            userHandle = options.userHandle,
            userName = options.userName,
            userDisplayName = options.userDisplayName,
            privateKey = Base64.getEncoder().encodeToString(key.privateKeyPkcs8),
            createdAt = now,
        )
        key.privateKeyPkcs8.fill(0)
        return CreatedPasskey(passkey, response.toString())
    }

    /** [passkey] ile oturum açma yanıtını (imzayı) üretir. */
    fun assert(
        passkey: Passkey,
        optionsJson: String,
        origin: String,
        clientDataHash: ByteArray?,
        androidPackageName: String?,
    ): String {
        val options = parseRequestOptions(optionsJson, origin)
        val authData = authenticatorData(passkey.rpId, BASE_FLAGS, null)
        val clientData = clientDataHash?.let { PLACEHOLDER_CLIENT_DATA }
            ?: clientDataJson("webauthn.get", options.challenge, origin, androidPackageName)
        val hash = clientDataHash ?: PasskeyCrypto.sha256(clientData)
        val privateKey = Base64.getDecoder().decode(passkey.privateKey)
        val signature = try {
            PasskeyCrypto.sign(privateKey, authData + hash)
        } finally {
            privateKey.fill(0)
        }
        return buildJsonObject {
            put("id", passkey.credentialId)
            put("rawId", passkey.credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            putJsonObject("response") {
                put("clientDataJSON", Base64Url.encode(clientData))
                put("authenticatorData", Base64Url.encode(authData))
                put("signature", Base64Url.encode(signature))
                put("userHandle", passkey.userHandle)
            }
            putJsonObject("clientExtensionResults") {}
        }.toString()
    }

    /** rpIdHash(32) || bayraklar(1) || imza sayacı(4, hep 0: eşitlenen anahtarlarda sayaç tutulmaz) [|| attested]. */
    private fun authenticatorData(rpId: String, flags: Int, attested: ByteArray?): ByteArray {
        val rpIdHash = PasskeyCrypto.sha256(rpId.toByteArray(Charsets.UTF_8))
        return ByteBuffer.allocate(37 + (attested?.size ?: 0))
            .put(rpIdHash).put(flags.toByte()).putInt(0)
            .apply { attested?.let(::put) }
            .array()
    }

    private fun clientDataJson(type: String, challenge: String, origin: String, androidPackageName: String?): ByteArray =
        buildJsonObject {
            put("type", type)
            put("challenge", challenge)
            put("origin", origin)
            put("crossOrigin", false)
            androidPackageName?.let { put("androidPackageName", it) }
        }.toString().toByteArray(Charsets.UTF_8)

    private fun parse(text: String): JsonObject =
        try {
            json.parseToJsonElement(text) as? JsonObject ?: invalid("JSON nesnesi değil")
        } catch (e: SerializationException) {
            invalid("JSON ayrıştırılamadı")
        }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

    private fun credentialIds(element: JsonElement?): Set<String> =
        (element as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("id")?.let(::normalizeIdOrNull) }.toSet()

    private fun normalizeId(value: String): String = normalizeIdOrNull(value) ?: invalid("Geçersiz Base64url")

    private fun normalizeIdOrNull(value: String): String? =
        try {
            Base64Url.encode(Base64Url.decode(value))
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun hostOf(origin: String?): String? =
        origin?.takeIf { it.startsWith("https://") }?.let { runCatching { URI(it).host }.getOrNull() }

    private fun invalid(message: String): Nothing = throw PasskeyException(PasskeyException.Kind.INVALID_REQUEST, message)
}
