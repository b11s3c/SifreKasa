package dev.sifrekasa.core.crypto

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.interfaces.PwHash
import com.sun.jna.NativeLong
import java.security.SecureRandom

/**
 * libsodium (Lazysodium bağlayıcısı) üzerinden [CryptoEngine].
 * JVM testlerinde LazySodiumJava, uygulamada LazySodiumAndroid ile kurulur.
 */
class SodiumCryptoEngine(private val sodium: LazySodium) : CryptoEngine {
    private val random = SecureRandom()

    override fun randomBytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)

    override fun argon2id(password: ByteArray, salt: ByteArray, opsLimit: Int, memKiB: Int, outLength: Int): ByteArray {
        require(salt.size == CryptoEngine.SALT_LENGTH) { "Tuz 16 bayt olmalı" }
        val out = ByteArray(outLength)
        val ok = sodium.cryptoPwHash(
            out, out.size,
            password, password.size,
            salt,
            opsLimit.toLong(),
            NativeLong(memKiB.toLong() * 1024),
            PwHash.Alg.PWHASH_ALG_ARGON2ID13,
        )
        check(ok) { "Argon2id başarısız (bellek yetersiz olabilir)" }
        return out
    }

    override fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        checkSizes(key, nonce)
        val out = ByteArray(plaintext.size + CryptoEngine.TAG_LENGTH)
        val ok = sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            out, null,
            plaintext, plaintext.size.toLong(),
            associatedData, associatedData.size.toLong(),
            null, nonce, key,
        )
        check(ok) { "Şifreleme başarısız" }
        return out
    }

    override fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        checkSizes(key, nonce)
        if (ciphertext.size < CryptoEngine.TAG_LENGTH) throw AeadAuthenticationException()
        val out = ByteArray(ciphertext.size - CryptoEngine.TAG_LENGTH)
        val ok = sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            out, null, null,
            ciphertext, ciphertext.size.toLong(),
            associatedData, associatedData.size.toLong(),
            nonce, key,
        )
        if (!ok) {
            out.fill(0)
            throw AeadAuthenticationException()
        }
        return out
    }

    private fun checkSizes(key: ByteArray, nonce: ByteArray) {
        require(key.size == CryptoEngine.KEY_LENGTH) { "Anahtar 32 bayt olmalı" }
        require(nonce.size == CryptoEngine.NONCE_LENGTH) { "Nonce 24 bayt olmalı" }
    }
}
