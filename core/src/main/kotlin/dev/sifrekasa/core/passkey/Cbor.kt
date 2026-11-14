package dev.sifrekasa.core.passkey

import java.io.ByteArrayOutputStream

/**
 * WebAuthn'in gerektirdiği kadarıyla CBOR (RFC 8949) kodlayıcı: tamsayı, bayt dizisi, metin, harita.
 * Yalnızca kodlama; kripto değildir. Haritalar verilen sırayla yazılır (çağıran kanonik sırayı verir).
 */
internal class CborWriter {
    private val out = ByteArrayOutputStream()

    fun int(value: Long) = apply { if (value >= 0) head(0, value) else head(1, -1 - value) }
    fun bytes(value: ByteArray) = apply { head(2, value.size.toLong()); out.write(value) }
    fun text(value: String) = apply { val b = value.toByteArray(Charsets.UTF_8); head(3, b.size.toLong()); out.write(b) }
    fun mapHeader(size: Int) = apply { head(5, size.toLong()) }
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun head(major: Int, value: Long) {
        val m = major shl 5
        when {
            value < 24 -> out.write(m or value.toInt())
            value < 0x100 -> { out.write(m or 24); out.write(value.toInt()) }
            value < 0x10000 -> { out.write(m or 25); out.write((value shr 8).toInt()); out.write(value.toInt()) }
            else -> { out.write(m or 26); for (s in 24 downTo 0 step 8) out.write((value shr s).toInt()) }
        }
    }
}
