package dev.sifrekasa.core

import com.google.crypto.tink.subtle.XChaCha20Poly1305
import dev.sifrekasa.core.crypto.AeadAuthenticationException
import dev.sifrekasa.core.crypto.Secrets
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoEngineTest {
    private val engine = TestEngine.engine

    /** libsodium Argon2id çıktısı bağımsız bir uygulamayla (Bouncy Castle) aynı olmalı. */
    @Test
    fun argon2idMatchesBouncyCastle() {
        val cases = listOf(Triple("parola", 1, 64), Triple("çğıöşü İstanbul 🔐", 3, 1024), Triple("", 2, 8192))
        for ((password, ops, memKiB) in cases) {
            val pw = password.toByteArray(Charsets.UTF_8)
            val salt = ByteArray(16) { (it * 7 + ops).toByte() }
            val expected = ByteArray(32)
            Argon2BytesGenerator().apply {
                init(
                    Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withIterations(ops).withMemoryAsKB(memKiB).withParallelism(1)
                        .withSalt(salt).build()
                )
            }.generateBytes(pw, expected)
            assertArrayEquals("ops=$ops mem=$memKiB", expected, engine.argon2id(pw, salt, ops, memKiB, 32))
        }
    }

    /** libsodium ile şifrelenen veriyi Tink çözebilmeli; Tink'in şifrelediğini de libsodium. */
    @Test
    fun xchachaInteroperatesWithTink() {
        val key = engine.randomBytes(32)
        val nonce = engine.randomBytes(24)
        val ad = "başlık".toByteArray()
        val message = "gizli mesaj".toByteArray()
        val tink = XChaCha20Poly1305(key)

        val ours = engine.aeadEncrypt(key, nonce, message, ad)
        assertEquals(message.size + 16, ours.size)
        assertArrayEquals(message, tink.decrypt(nonce + ours, ad))

        val theirs = tink.encrypt(message, ad)
        val decrypted = engine.aeadDecrypt(key, theirs.copyOfRange(0, 24), theirs.copyOfRange(24, theirs.size), ad)
        assertArrayEquals(message, decrypted)
    }

    @Test
    fun aeadRejectsTampering() {
        val key = engine.randomBytes(32)
        val nonce = engine.randomBytes(24)
        val ad = byteArrayOf(1, 2, 3)
        val ct = engine.aeadEncrypt(key, nonce, "veri".toByteArray(), ad)

        for (i in ct.indices) {
            val bad = ct.clone().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows(AeadAuthenticationException::class.java) { engine.aeadDecrypt(key, nonce, bad, ad) }
        }
        assertThrows(AeadAuthenticationException::class.java) { engine.aeadDecrypt(key, nonce, ct, byteArrayOf(1, 2, 4)) }
        assertThrows(AeadAuthenticationException::class.java) { engine.aeadDecrypt(engine.randomBytes(32), nonce, ct, ad) }
        assertThrows(AeadAuthenticationException::class.java) { engine.aeadDecrypt(key, engine.randomBytes(24), ct, ad) }
        assertThrows(AeadAuthenticationException::class.java) { engine.aeadDecrypt(key, nonce, ByteArray(5), ad) }
    }

    @Test
    fun emptyPlaintextRoundTrips() {
        val key = engine.randomBytes(32)
        val nonce = engine.randomBytes(24)
        val ct = engine.aeadEncrypt(key, nonce, ByteArray(0), ByteArray(0))
        assertEquals(16, ct.size)
        assertEquals(0, engine.aeadDecrypt(key, nonce, ct, ByteArray(0)).size)
    }

    @Test
    fun rejectsWrongKeyAndNonceSizes() {
        assertThrows(IllegalArgumentException::class.java) { engine.aeadEncrypt(ByteArray(16), ByteArray(24), ByteArray(1), ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { engine.aeadEncrypt(ByteArray(32), ByteArray(12), ByteArray(1), ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { engine.argon2id(ByteArray(1), ByteArray(8), 1, 64, 32) }
    }

    @Test
    fun randomBytesAreNotRepeated() {
        assertFalse(engine.randomBytes(32).contentEquals(engine.randomBytes(32)))
    }

    @Test
    fun utf8EncodesWithoutStringAndWipes() {
        val chars = "şifre🔑".toCharArray()
        assertArrayEquals("şifre🔑".toByteArray(Charsets.UTF_8), Secrets.utf8(chars))
        Secrets.wipe(chars)
        assertEquals(String(CharArray(chars.size)), String(chars))
    }
}
