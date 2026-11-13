package dev.sifrekasa.core.generator

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** 0/O, 1/l/I gibi karışan karakterleri dışarıda bırak. */
    val avoidAmbiguous: Boolean = false,
) {
    val anyClassSelected: Boolean get() = upper || lower || digits || symbols
}

class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: GeneratorOptions): String {
        val classes = buildList {
            if (options.upper) add(UPPER)
            if (options.lower) add(LOWER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { set -> if (options.avoidAmbiguous) set.filterNot { it in AMBIGUOUS } else set }
        require(classes.isNotEmpty()) { "En az bir karakter sınıfı seçilmeli" }
        require(options.length in classes.size..MAX_LENGTH) { "Geçersiz uzunluk" }

        val all = classes.joinToString("")
        val chars = CharArray(options.length)
        // Seçilen her sınıftan en az bir karakter, kalanı tüm kümeden; sonra karıştır.
        classes.forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        for (i in classes.size until chars.size) chars[i] = all[random.nextInt(all.length)]
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return String(chars).also { chars.fill('\u0000') }
    }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 128
        const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
        const val AMBIGUOUS = "0O1lI|"
    }
}
