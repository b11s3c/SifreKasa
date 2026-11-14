package dev.sifrekasa.core.totp

/** RFC 4648 Base32. Yalnızca kodlama; kripto değildir. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Boşluk, tire ve dolgu (=) yok sayılır; küçük harf kabul edilir. */
    fun normalize(text: String): String =
        text.filterNot { it.isWhitespace() || it == '-' }.trimEnd('=').uppercase()

    /** @throws IllegalArgumentException geçersiz karakter ya da uzunluk */
    fun decode(text: String): ByteArray {
        val clean = normalize(text)
        // 8 karakterlik blokta kalan 1, 3 ya da 6 karakter geçerli bir bayt sınırına denk gelmez.
        require(clean.length % 8 !in setOf(1, 3, 6)) { "Geçersiz Base32 uzunluğu" }
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Geçersiz Base32 karakteri" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out
    }

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer shr bits) and 31])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }
}
