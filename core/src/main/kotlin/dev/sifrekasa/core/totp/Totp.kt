package dev.sifrekasa.core.totp

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class TotpAlgorithm(internal val macName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}

/** Bir giriş kaydına bağlı TOTP ayarı. [secret] Base32'dir (boşluksuz, dolgusuz, büyük harf). */
@Serializable
data class TotpConfig(
    val secret: String,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
) {
    init {
        require(digits in 6..8) { "Hane sayısı 6-8 olmalı" }
        require(period in 1..300) { "Periyot 1-300 saniye olmalı" }
        require(Base32.decode(secret).isNotEmpty()) { "Gizli anahtar boş" }
    }

    override fun toString(): String = "TotpConfig(algorithm=$algorithm, digits=$digits, period=$period)"
}

/**
 * RFC 6238 (TOTP) ve RFC 4226 (HOTP). HMAC platformun kripto sağlayıcısından gelir;
 * buradaki kod yalnızca protokolün sayaç ve kısaltma adımlarıdır.
 */
object Totp {
    fun code(config: TotpConfig, epochSeconds: Long): String {
        val key = Base32.decode(config.secret)
        try {
            return hotp(key, epochSeconds / config.period, config.digits, config.algorithm)
        } finally {
            key.fill(0)
        }
    }

    /** Geçerli kodun süresinin dolmasına kalan saniye (1..period). */
    fun secondsRemaining(config: TotpConfig, epochSeconds: Long): Int =
        (config.period - epochSeconds % config.period).toInt()

    fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: TotpAlgorithm = TotpAlgorithm.SHA1): String {
        require(digits in 6..8)
        val mac = Mac.getInstance(algorithm.macName)
        mac.init(SecretKeySpec(key, algorithm.macName))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(digits, '0')
    }
}
