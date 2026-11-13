package dev.sifrekasa.core.crypto

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

object Secrets {
    /** Parolayı String'e çevirmeden UTF-8 baytlarına dönüştürür. Çağıran, sonucu [wipe] ile silmelidir. */
    fun utf8(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val out = ByteArray(buffer.remaining())
        buffer.get(out)
        if (buffer.hasArray()) buffer.array().fill(0)
        return out
    }

    fun wipe(vararg arrays: ByteArray?) {
        arrays.forEach { it?.fill(0) }
    }

    fun wipe(chars: CharArray?) {
        chars?.fill('\u0000')
    }
}
