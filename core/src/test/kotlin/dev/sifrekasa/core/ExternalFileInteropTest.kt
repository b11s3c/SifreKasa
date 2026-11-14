package dev.sifrekasa.core

import com.google.crypto.tink.subtle.XChaCha20Poly1305
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * Uygulamanın ürettiği gerçek bir .skx dosyasını, uygulamanın kodunu ve libsodium'u HİÇ kullanmadan,
 * yalnızca docs/dosya-bicimi.md'deki tarife göre Bouncy Castle (Argon2id) ve Tink (XChaCha20-Poly1305)
 * ile açar. Dosya verilmezse atlanır.
 */
class ExternalFileInteropTest {
    @Test
    fun exportFileOpensWithIndependentLibraries() {
        val path = System.getProperty("skx.file")
        val password = System.getProperty("skx.password")
        assumeTrue("skx.file verilmedi", path != null && password != null)

        val bytes = File(path).readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals("SKX1", String(ByteArray(4).also { buffer.get(it) }, Charsets.US_ASCII))
        assertEquals(1, buffer.get().toInt())
        assertEquals(1, buffer.get().toInt())
        val ops = buffer.getInt()
        val memKiB = buffer.getInt()
        val salt = ByteArray(16).also { buffer.get(it) }
        val nonceAndCiphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val header = bytes.copyOfRange(0, 54)

        val key = ByteArray(32)
        Argon2BytesGenerator().apply {
            init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(ops).withMemoryAsKB(memKiB).withParallelism(1)
                    .withSalt(salt).build()
            )
        }.generateBytes(password.toByteArray(Charsets.UTF_8), key)

        // Tink'in girdi düzeni nonce || şifreli metin || etiket; dosyada da aynı sırayla durur.
        val json = String(XChaCha20Poly1305(key).decrypt(nonceAndCiphertext, header), Charsets.UTF_8)
        assertTrue(json.startsWith("{"))
        val entries = Regex("\"id\":").findAll(json).count()
        val withTotp = Regex("\"totp\":\\{").findAll(json).count()
        println("INTEROP: Argon2 ops=$ops mem=${memKiB}KiB; $entries kayıt, $withTotp tanesi 2FA'lı; yedek anahtarı var mı: ${json.contains("\"backup\":{")}")
    }
}
