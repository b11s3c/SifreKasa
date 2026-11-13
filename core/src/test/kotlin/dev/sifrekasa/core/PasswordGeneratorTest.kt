package dev.sifrekasa.core

import dev.sifrekasa.core.generator.GeneratorOptions
import dev.sifrekasa.core.generator.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    private val generator = PasswordGenerator()

    @Test
    fun containsEverySelectedClass() {
        repeat(300) {
            val p = generator.generate(GeneratorOptions(length = 8))
            assertEquals(8, p.length)
            assertTrue(p.any { it in PasswordGenerator.UPPER })
            assertTrue(p.any { it in PasswordGenerator.LOWER })
            assertTrue(p.any { it in PasswordGenerator.DIGITS })
            assertTrue(p.any { it in PasswordGenerator.SYMBOLS })
        }
    }

    @Test
    fun respectsDisabledClassesAndAmbiguousFilter() {
        repeat(200) {
            val p = generator.generate(GeneratorOptions(length = 40, symbols = false, upper = false, avoidAmbiguous = true))
            assertTrue(p.all { it in PasswordGenerator.LOWER || it in PasswordGenerator.DIGITS })
            assertTrue(p.none { it in PasswordGenerator.AMBIGUOUS })
        }
    }

    @Test
    fun usesWholeAlphabet() {
        val seen = HashSet<Char>()
        repeat(400) { seen.addAll(generator.generate(GeneratorOptions(length = 64)).toList()) }
        val all = PasswordGenerator.UPPER + PasswordGenerator.LOWER + PasswordGenerator.DIGITS + PasswordGenerator.SYMBOLS
        assertEquals(all.toSet(), seen)
    }

    @Test
    fun rejectsInvalidOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(GeneratorOptions(upper = false, lower = false, digits = false, symbols = false))
        }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(GeneratorOptions(length = 3)) }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(GeneratorOptions(length = 1000)) }
    }
}
