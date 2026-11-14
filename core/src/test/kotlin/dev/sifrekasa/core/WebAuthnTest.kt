package dev.sifrekasa.core

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import dev.sifrekasa.core.autofill.DomainMatcher
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.passkey.Base64Url
import dev.sifrekasa.core.passkey.Passkey
import dev.sifrekasa.core.passkey.PasskeyCrypto
import dev.sifrekasa.core.passkey.PasskeyException
import dev.sifrekasa.core.passkey.WebAuthn
import dev.sifrekasa.core.vault.ExportFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ürettiğimiz WebAuthn yanıtları, bir sitenin sunucusunun yapacağı gibi bağımsız bir kütüphaneyle
 * (webauthn4j) baştan sona doğrulanır: köken, RP özeti, sınama değeri, bayraklar, COSE anahtarı, imza.
 */
class WebAuthnTest {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val es256 = listOf(PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256))
    private val webOrigin = "https://login.example.com"
    private val rpId = "example.com"

    private fun challenge(seed: Int) = ByteArray(32) { (it * seed + 3).toByte() }

    private fun creationJson(challenge: ByteArray, exclude: List<String> = emptyList(), algs: List<Int> = listOf(-8, -7, -257)) = """
        {"rp":{"id":"$rpId","name":"Example A.Ş."},
         "user":{"id":"${Base64Url.encode("kullanici-42".toByteArray())}","name":"ali@example.com","displayName":"Ali Çğış"},
         "challenge":"${Base64Url.encode(challenge)}",
         "pubKeyCredParams":[${algs.joinToString(",") { """{"type":"public-key","alg":$it}""" }}],
         "excludeCredentials":[${exclude.joinToString(",") { """{"type":"public-key","id":"$it"}""" }}],
         "authenticatorSelection":{"residentKey":"required","userVerification":"required"},"attestation":"none","timeout":60000}
    """.trimIndent()

    private fun requestJson(challenge: ByteArray, allow: List<String> = emptyList()) =
        """{"rpId":"$rpId","challenge":"${Base64Url.encode(challenge)}","userVerification":"required",
            "allowCredentials":[${allow.joinToString(",") { """{"type":"public-key","id":"$it"}""" }}]}"""

    private fun server(origin: String, challenge: ByteArray) = ServerProperty(Origin(origin), rpId, DefaultChallenge(challenge))

    /** Yanıttaki clientDataJSON'u değiştirir (tarayıcının yaptığı gibi kendi clientDataJSON'unu koymak için). */
    private fun withClientData(responseJson: String, clientData: ByteArray): String {
        val root = Json.parseToJsonElement(responseJson).jsonObject
        val response = JsonObject(root.getValue("response").jsonObject + ("clientDataJSON" to JsonPrimitive(Base64Url.encode(clientData))))
        return JsonObject(root + ("response" to response)).toString()
    }

    private fun register(origin: String, packageName: String? = null): Pair<Passkey, CredentialRecordImpl> {
        val c = challenge(7)
        val created = WebAuthn.create(creationJson(c), origin, null, packageName, emptyList(), now = 1000)
        val data = manager.parseRegistrationResponseJSON(created.responseJson)
        manager.verify(data, RegistrationParameters(server(origin, c), es256, true, true))
        val record = CredentialRecordImpl(data.attestationObject!!, data.collectedClientData, data.clientExtensions, data.transports)
        return created.passkey to record
    }

    @Test
    fun registrationAndAssertionVerifyForNativeStyleCaller() {
        val (passkey, record) = register(webOrigin)
        assertEquals(rpId, passkey.rpId)
        assertEquals("Example A.Ş.", passkey.rpName)
        assertEquals("ali@example.com", passkey.userName)
        assertEquals("Ali Çğış", passkey.userDisplayName)
        assertEquals("kullanici-42", String(Base64Url.decode(passkey.userHandle)))
        assertEquals(1000, passkey.createdAt)
        assertEquals(32, Base64Url.decode(passkey.credentialId).size)

        val c = challenge(11)
        val assertion = WebAuthn.assert(passkey, requestJson(c), webOrigin, null, null)
        val data = manager.parseAuthenticationResponseJSON(assertion)
        manager.verify(data, AuthenticationParameters(server(webOrigin, c), record, null, true, true))
        assertEquals(passkey.userHandle, Base64Url.encode(data.userHandle!!))
        assertEquals(passkey.credentialId, Base64Url.encode(data.credentialId!!))
    }

    @Test
    fun androidAppOriginVerifies() {
        val origin = "android:apk-key-hash:" + Base64Url.encode(PasskeyCrypto.sha256("sertifika".toByteArray()))
        val (passkey, record) = register(origin, packageName = "com.example.app")
        val c = challenge(13)
        val assertion = WebAuthn.assert(passkey, requestJson(c), origin, null, "com.example.app")
        manager.verify(manager.parseAuthenticationResponseJSON(assertion), AuthenticationParameters(server(origin, c), record, null, true, true))
        val clientData = String(Base64Url.decode(Json.parseToJsonElement(assertion).jsonObject.getValue("response").jsonObject.getValue("clientDataJSON").jsonPrimitive.content))
        assertTrue(clientData.contains("\"androidPackageName\":\"com.example.app\""))
    }

    /** Tarayıcı / hybrid akış: clientDataJSON'u çağıran kurar, bize yalnızca özetini verir. */
    @Test
    fun privilegedCallerWithClientDataHashVerifies() {
        fun browserClientData(type: String, c: ByteArray) =
            """{"type":"$type","challenge":"${Base64Url.encode(c)}","origin":"$webOrigin","crossOrigin":false}""".toByteArray()

        val c1 = challenge(17)
        val createClientData = browserClientData("webauthn.create", c1)
        val created = WebAuthn.create(creationJson(c1), webOrigin, PasskeyCrypto.sha256(createClientData), null, emptyList(), 0)
        val regData = manager.parseRegistrationResponseJSON(withClientData(created.responseJson, createClientData))
        manager.verify(regData, RegistrationParameters(server(webOrigin, c1), es256, true, true))
        val record = CredentialRecordImpl(regData.attestationObject!!, regData.collectedClientData, regData.clientExtensions, regData.transports)

        val c2 = challenge(19)
        val getClientData = browserClientData("webauthn.get", c2)
        val assertion = WebAuthn.assert(created.passkey, requestJson(c2), webOrigin, PasskeyCrypto.sha256(getClientData), null)
        val authData = manager.parseAuthenticationResponseJSON(withClientData(assertion, getClientData))
        manager.verify(authData, AuthenticationParameters(server(webOrigin, c2), record, null, true, true))

        // Yanlış özetle üretilen imza reddedilmeli.
        val bad = WebAuthn.assert(created.passkey, requestJson(c2), webOrigin, PasskeyCrypto.sha256("başka".toByteArray()), null)
        assertThrows(Exception::class.java) {
            manager.verify(manager.parseAuthenticationResponseJSON(withClientData(bad, getClientData)), AuthenticationParameters(server(webOrigin, c2), record, null, true, true))
        }
    }

    @Test
    fun authenticatorDataFlagsAndCounter() {
        val c = challenge(23)
        val created = WebAuthn.create(creationJson(c), webOrigin, null, null, emptyList(), 0)
        val reg = manager.parseRegistrationResponseJSON(created.responseJson).attestationObject!!.authenticatorData
        assertTrue(reg.isFlagUP && reg.isFlagUV && reg.isFlagBE && reg.isFlagBS && reg.isFlagAT)
        assertEquals(0, reg.signCount)
        assertEquals("none", manager.parseRegistrationResponseJSON(created.responseJson).attestationObject!!.format)

        val auth = manager.parseAuthenticationResponseJSON(WebAuthn.assert(created.passkey, requestJson(c), webOrigin, null, null)).authenticatorData!!
        assertTrue(auth.isFlagUP && auth.isFlagUV && auth.isFlagBE && auth.isFlagBS)
        assertFalse(auth.isFlagAT)
        assertEquals(0, auth.signCount)
    }

    @Test
    fun everyPasskeyGetsFreshKeyAndId() {
        val c = challenge(29)
        val a = WebAuthn.create(creationJson(c), webOrigin, null, null, emptyList(), 0).passkey
        val b = WebAuthn.create(creationJson(c), webOrigin, null, null, emptyList(), 0).passkey
        assertFalse(a.credentialId == b.credentialId)
        assertFalse(a.privateKey == b.privateKey)
        assertFalse(a.toString().contains(a.privateKey))
    }

    @Test
    fun rejectsExcludedUnsupportedAndMalformedRequests() {
        val c = challenge(31)
        val existing = WebAuthn.create(creationJson(c), webOrigin, null, null, emptyList(), 0).passkey
        val excluded = assertThrows(PasskeyException::class.java) {
            WebAuthn.create(creationJson(c, exclude = listOf(existing.credentialId)), webOrigin, null, null, listOf(existing), 0)
        }
        assertEquals(PasskeyException.Kind.ALREADY_REGISTERED, excluded.kind)
        // Başka bir sitenin aynı kimliği engel olmamalı.
        WebAuthn.create(creationJson(c, exclude = listOf(existing.credentialId)), webOrigin, null, null, listOf(existing.copy(rpId = "baska.example")), 0)

        val unsupported = assertThrows(PasskeyException::class.java) {
            WebAuthn.create(creationJson(c, algs = listOf(-257, -8)), webOrigin, null, null, emptyList(), 0)
        }
        assertEquals(PasskeyException.Kind.NOT_SUPPORTED, unsupported.kind)

        for (bad in listOf("", "[]", "{}", """{"rp":{"id":"x"}}""", "değil json")) {
            val e = assertThrows(bad, PasskeyException::class.java) { WebAuthn.create(bad, webOrigin, null, null, emptyList(), 0) }
            assertEquals(PasskeyException.Kind.INVALID_REQUEST, e.kind)
        }
        assertThrows(PasskeyException::class.java) { WebAuthn.parseRequestOptions("""{"rpId":"x"}""", null) }
    }

    @Test
    fun rpIdFallsBackToOriginHostOnlyForHttps() {
        val c = Base64Url.encode(challenge(37))
        assertEquals("login.example.com", WebAuthn.parseRequestOptions("""{"challenge":"$c"}""", webOrigin).rpId)
        assertThrows(PasskeyException::class.java) { WebAuthn.parseRequestOptions("""{"challenge":"$c"}""", "android:apk-key-hash:abc") }
    }

    @Test
    fun matchingFiltersByRpAndAllowList() {
        fun passkey(id: String, rp: String) = Passkey(Base64Url.encode(id.toByteArray()), rp, userHandle = "dQ", privateKey = "")
        val all = listOf(passkey("a", rpId), passkey("b", rpId), passkey("c", "baska.example"))
        val c = challenge(41)
        assertEquals(2, WebAuthn.matching(all, WebAuthn.parseRequestOptions(requestJson(c), null)).size)
        // allowCredentials dolgulu Base64 ile de gelebilir.
        val allow = listOf(Base64Url.encode("b".toByteArray()) + "==", Base64Url.encode("c".toByteArray()))
        assertEquals(listOf(all[1]), WebAuthn.matching(all, WebAuthn.parseRequestOptions(requestJson(c, allow), null)))
    }

    /** Geçiş anahtarı kasada ve şifreli yedekte taşınmalı; geri yüklenen anahtar çalışmalı. */
    @Test
    fun passkeySurvivesExportAndStillSigns() {
        val (passkey, record) = register(webOrigin)
        val engine = TestEngine.engine
        val password = "yedek".toByteArray()
        val vault = VaultData(entries = listOf(LoginEntry("1", "Example", passkey = passkey)))
        val file = ExportFormat.seal(engine, ExportFormat.deriveKey(engine, password, TestEngine.fastKdf), vault, 0)
        assertFalse(String(file, Charsets.ISO_8859_1).contains(passkey.privateKey))
        val restored = ExportFormat.open(engine, file, password).vault.entries.single().passkey!!
        assertEquals(passkey, restored)

        val c = challenge(43)
        val assertion = WebAuthn.assert(restored, requestJson(c), webOrigin, null, null)
        manager.verify(manager.parseAuthenticationResponseJSON(assertion), AuthenticationParameters(server(webOrigin, c), record, null, true, true))
        assertNull(VaultData(entries = listOf(LoginEntry("2"))).entries.single().passkey)
    }

    @Test
    fun domainMatcherIsStrict() {
        assertTrue(DomainMatcher.matches("https://example.com/login", "example.com"))
        assertTrue(DomainMatcher.matches("example.com", "login.example.com"))
        assertTrue(DomainMatcher.matches("https://www.Example.com", "EXAMPLE.com"))
        assertTrue(DomainMatcher.matches("https://accounts.google.com", "accounts.google.com"))
        // Oltalama biçimleri ve fazla geniş eşleşmeler.
        assertFalse(DomainMatcher.matches("example.com", "evil-example.com"))
        assertFalse(DomainMatcher.matches("example.com", "example.com.evil.net"))
        assertFalse(DomainMatcher.matches("example.com", "notexample.com"))
        assertFalse(DomainMatcher.matches("login.example.com", "example.com"))
        assertFalse(DomainMatcher.matches("a.example.com", "b.example.com"))
        assertFalse(DomainMatcher.matches("com", "example.com"))
        assertFalse(DomainMatcher.matches("", "example.com"))
        assertFalse(DomainMatcher.matches("example.com", ""))
        assertFalse(DomainMatcher.matches("not a url ::", "example.com"))
        assertEquals("example.com", DomainMatcher.hostOf("https://www.example.com:8443/a?b#c"))
    }
}
