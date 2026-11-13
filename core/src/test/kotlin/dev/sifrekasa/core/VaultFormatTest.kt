package dev.sifrekasa.core

import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.vault.VaultFormat
import dev.sifrekasa.core.vault.VaultFormatException
import dev.sifrekasa.core.vault.WrongKeyException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class VaultFormatTest {
    private val engine = TestEngine.engine
    private val kdf = TestEngine.fastKdf
    private val password = "doğru at pil zımba".toByteArray()
    private val data = VaultData(
        entries = listOf(
            LoginEntry("1", "E-posta", "ali@example.com", "p@ss \"w\\ord\" ğüşiöç", "https://mail.example.com", "satır1\nsatır2", 10, 20),
            LoginEntry("2", "Banka"),
        )
    )

    private fun sealed(): Pair<ByteArray, ByteArray> {
        val dek = VaultFormat.newDek(engine)
        val slot = VaultFormat.createKeySlot(engine, password, kdf, dek)
        return VaultFormat.seal(engine, slot, dek, data) to dek
    }

    @Test
    fun roundTripWithPassword() {
        val (file, dek) = sealed()
        val parsed = VaultFormat.parse(file)
        val unwrapped = VaultFormat.unwrapDek(engine, parsed.keySlot, password)
        assertArrayEquals(dek, unwrapped)
        assertEquals(data, VaultFormat.open(engine, parsed, unwrapped))
    }

    /** Biyometri yolu: parola olmadan, doğrudan DEK ile açma. */
    @Test
    fun roundTripWithDekOnly() {
        val (file, dek) = sealed()
        assertEquals(data, VaultFormat.open(engine, VaultFormat.parse(file), dek))
    }

    @Test
    fun headerLayoutIsStable() {
        val (file, _) = sealed()
        assertEquals(126, VaultFormat.HEADER_LENGTH)
        val b = ByteBuffer.wrap(file)
        assertEquals("SKV1", String(ByteArray(4).also { b.get(it) }, Charsets.US_ASCII))
        assertEquals(1, b.get().toInt())
        assertEquals(1, b.get().toInt())
        assertEquals(kdf.opsLimit, b.getInt())
        assertEquals(kdf.memKiB, b.getInt())
    }

    @Test
    fun plaintextDoesNotAppearInFile() {
        val (file, _) = sealed()
        val text = String(file, Charsets.ISO_8859_1)
        assertFalse(text.contains("ali@example.com"))
        assertFalse(text.contains("E-posta"))
        assertFalse(text.contains("entries"))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val (file, _) = sealed()
        val slot = VaultFormat.parse(file).keySlot
        assertThrows(WrongKeyException::class.java) { VaultFormat.unwrapDek(engine, slot, "yanlış".toByteArray()) }
    }

    @Test
    fun wrongDekIsRejected() {
        val (file, _) = sealed()
        assertThrows(WrongKeyException::class.java) { VaultFormat.open(engine, VaultFormat.parse(file), VaultFormat.newDek(engine)) }
    }

    /** Dosyadaki her baytın değiştirilmesi fark edilmeli (başlık dahil). */
    @Test
    fun everyByteIsAuthenticated() {
        val (file, dek) = sealed()
        for (i in file.indices) {
            val bad = file.clone().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val detected = try {
                val parsed = VaultFormat.parse(bad)
                // Sarılmış DEK bölümündeki değişiklik parola yolunda, gerisi gövdede yakalanır.
                VaultFormat.unwrapDek(engine, parsed.keySlot, password)
                VaultFormat.open(engine, parsed, dek)
                false
            } catch (e: WrongKeyException) {
                true
            } catch (e: VaultFormatException) {
                true
            }
            assertEquals("bayt $i değişikliği fark edilmedi", true, detected)
        }
    }

    @Test
    fun truncatedAndForeignFilesAreRejected() {
        val (file, _) = sealed()
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(ByteArray(0)) }
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(file.copyOf(100)) }
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse("%PDF-1.7".toByteArray() + ByteArray(300)) }
        val parsed = VaultFormat.parse(file.copyOf(file.size - 1))
        assertThrows(WrongKeyException::class.java) { VaultFormat.open(engine, parsed, sealed().second) }
    }

    @Test
    fun unsupportedVersionAndHugeKdfAreRejected() {
        val (file, _) = sealed()
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(file.clone().also { it[4] = 2 }) }
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(file.clone().also { it[5] = 9 }) }
        // Bellek alanı 0x7FFFFFFF KiB: türetmeye başlamadan reddedilmeli.
        val huge = file.clone().also { ByteBuffer.wrap(it).putInt(10, Int.MAX_VALUE) }
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(huge) }
    }

    @Test
    fun sealingTwiceUsesFreshNonce() {
        val dek = VaultFormat.newDek(engine)
        val slot = VaultFormat.createKeySlot(engine, password, kdf, dek)
        val a = VaultFormat.seal(engine, slot, dek, data)
        val b = VaultFormat.seal(engine, slot, dek, data)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(102, 126).contentEquals(b.copyOfRange(102, 126)))
    }

    /** Parola değişimi: aynı DEK yeni parolayla sarılır, eski parola artık açmaz. */
    @Test
    fun changingPasswordKeepsDek() {
        val dek = VaultFormat.newDek(engine)
        val newPassword = "yeni parola".toByteArray()
        val newSlot = VaultFormat.createKeySlot(engine, newPassword, KdfParams(2, 128), dek)
        val parsed = VaultFormat.parse(VaultFormat.seal(engine, newSlot, dek, data))
        assertArrayEquals(dek, VaultFormat.unwrapDek(engine, parsed.keySlot, newPassword))
        assertThrows(WrongKeyException::class.java) { VaultFormat.unwrapDek(engine, parsed.keySlot, password) }
        assertEquals(KdfParams(2, 128), parsed.keySlot.kdf)
    }

    /** Gelecekte eklenecek alanlar eski sürümün dosyayı okumasını bozmamalı. */
    @Test
    fun emptyVaultRoundTrips() {
        val dek = VaultFormat.newDek(engine)
        val slot = VaultFormat.createKeySlot(engine, password, kdf, dek)
        assertEquals(VaultData(), VaultFormat.open(engine, VaultFormat.parse(VaultFormat.seal(engine, slot, dek, VaultData())), dek))
    }
}
