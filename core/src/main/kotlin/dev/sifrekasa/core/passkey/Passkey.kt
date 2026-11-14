package dev.sifrekasa.core.passkey

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Bir giriş kaydına bağlı geçiş anahtarı (WebAuthn, keşfedilebilir kimlik bilgisi).
 * Özel anahtar bilerek Android Keystore'da DEĞİL kasanın içindedir: böylece şifreli yedeğe girer
 * ve başka bir cihaza geri yüklenebilir.
 */
@Serializable
data class Passkey(
    /** Base64url (dolgusuz). */
    val credentialId: String,
    val rpId: String,
    val rpName: String = "",
    /** Sitenin verdiği user.id; Base64url (dolgusuz). */
    val userHandle: String,
    val userName: String = "",
    val userDisplayName: String = "",
    /** P-256 özel anahtarı, PKCS#8 DER, Base64. */
    val privateKey: String,
    val createdAt: Long = 0,
) {
    override fun toString(): String = "Passkey(rpId=$rpId)"
}

object Base64Url {
    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Dolgulu/dolgusuz ve standart alfabeyi de kabul eder. @throws IllegalArgumentException */
    fun decode(text: String): ByteArray =
        Base64.getUrlDecoder().decode(text.trim().replace('+', '-').replace('/', '_').trimEnd('='))
}
