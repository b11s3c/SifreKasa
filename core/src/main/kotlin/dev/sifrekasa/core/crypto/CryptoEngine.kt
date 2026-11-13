package dev.sifrekasa.core.crypto

/** AEAD doğrulaması başarısız: yanlış anahtar ya da değiştirilmiş veri. */
class AeadAuthenticationException : Exception("AEAD doğrulaması başarısız")

/**
 * Kasanın ihtiyaç duyduğu kripto ilkel işlemleri. Algoritmaların kendisi burada YAZILMAZ;
 * uygulama denetlenmiş bir kütüphaneye (libsodium) devreder. Bkz. SodiumCryptoEngine.
 */
interface CryptoEngine {
    /** İşletim sisteminin CSPRNG'sinden [length] bayt. */
    fun randomBytes(length: Int): ByteArray

    /** Argon2id v1.3. [memKiB] kibibayt cinsindendir. */
    fun argon2id(password: ByteArray, salt: ByteArray, opsLimit: Int, memKiB: Int, outLength: Int): ByteArray

    /** XChaCha20-Poly1305 (IETF). Dönüş: şifreli metin || 16 baytlık etiket. */
    fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** @throws AeadAuthenticationException doğrulama başarısızsa */
    fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray

    companion object {
        const val KEY_LENGTH = 32
        const val NONCE_LENGTH = 24
        const val TAG_LENGTH = 16
        const val SALT_LENGTH = 16
    }
}
