package dev.sifrekasa.core.send

import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import dev.sifrekasa.core.passkey.Base64Url
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.GeneralSecurityException

/**
 * Telefondan tarayıcı eklentisine tek seferlik, uçtan uca şifreli kimlik bilgisi gönderimi (sürüm 1).
 *
 * ```
 * paylaşılan = X25519(telefonun tek kullanımlık özel anahtarı, eklentinin QR'daki açık anahtarı)
 * anahtar    = HKDF-SHA256(ikm = paylaşılan, tuz = oturum kimliği,
 *                          bilgi = "SifreKasa/send/v1" ‖ eklenti açık anahtarı ‖ telefon açık anahtarı, 32 bayt)
 * kutu       = AES-256-GCM(anahtar, rastgele 12 bayt nonce, düz metin, AD = "SifreKasa/send/v1|" ‖ alan adı)
 * ileti      = {"v":1, "k": telefon açık anahtarı, "c": nonce ‖ şifreli metin ‖ etiket}   (Base64url)
 * düz metin  = {"d": alan adı, "u": kullanıcı adı, "p": şifre, "t": TOTP kodu ya da null}
 * ```
 *
 * İlkeller Tink'ten gelir. İki taraf da her istekte yeni anahtar çifti üretir; aktarıcı yalnızca
 * [seal]'in döndürdüğü iletiyi görür ve açık anahtar QR ile (aktarıcının dışından) taşındığı için araya giremez.
 */
object SendProtocol {
    private const val LABEL = "SifreKasa/send/v1"

    /** @return aktarıcıya POST edilecek JSON iletisi */
    fun seal(request: SendRequest, username: String, password: String, totpCode: String?): String {
        val plaintext = buildJsonObject {
            put("d", request.domain)
            put("u", username)
            put("p", password)
            if (totpCode != null) put("t", totpCode) else put("t", JsonNull)
        }.toString().toByteArray(Charsets.UTF_8)

        val privateKey = X25519.generatePrivateKey()
        var shared: ByteArray? = null
        var key: ByteArray? = null
        try {
            val publicKey = X25519.publicFromPrivate(privateKey)
            shared = X25519.computeSharedSecret(privateKey, request.extensionPublicKey)
            // Düşük dereceli (kötü niyetli) bir açık anahtar tümü sıfır bir sır üretir; kabul edilmez.
            if (shared.all { it == 0.toByte() }) throw SendException("Geçersiz açık anahtar")
            key = Hkdf.computeHkdf(
                "HMACSHA256", shared, request.sessionId,
                LABEL.toByteArray(Charsets.US_ASCII) + request.extensionPublicKey + publicKey, 32,
            )
            val box = AesGcmJce(key).encrypt(plaintext, associatedData(request.domain))
            return buildJsonObject {
                put("v", 1)
                put("k", Base64Url.encode(publicKey))
                put("c", Base64Url.encode(box))
            }.toString()
        } catch (e: GeneralSecurityException) {
            throw SendException("Şifreleme başarısız")
        } finally {
            privateKey.fill(0)
            shared?.fill(0)
            key?.fill(0)
            plaintext.fill(0)
        }
    }

    fun associatedData(domain: String): ByteArray = "$LABEL|$domain".toByteArray(Charsets.UTF_8)
}
