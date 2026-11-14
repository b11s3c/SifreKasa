package dev.sifrekasa.core

import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.totp.TotpConfig
import dev.sifrekasa.core.vault.ExportFormat
import dev.sifrekasa.core.vault.ExportKey
import dev.sifrekasa.core.vault.VaultFormat
import dev.sifrekasa.core.vault.VaultFormatException
import dev.sifrekasa.core.vault.VaultMerge
import dev.sifrekasa.core.vault.WrongKeyException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ExportFormatTest {
    private val engine = TestEngine.engine
    private val kdf = TestEngine.fastKdf
    private val password = "dışa aktarım parolası".toByteArray()
    private val data = VaultData(
        entries = listOf(
            LoginEntry("1", "E-posta", "ali@example.com", "gizli-şifre", "https://mail.example.com", "not", 10, 20, TotpConfig("JBSWY3DPEHPK3PXP")),
            LoginEntry("2", "Banka", updatedAt = 5),
        )
    )

    private fun export(vault: VaultData = data) = ExportFormat.seal(engine, ExportFormat.deriveKey(engine, password, kdf), vault, exportedAt = 1234)

    @Test
    fun roundTripWithPasswordOnly() {
        val payload = ExportFormat.open(engine, export(), password)
        assertEquals(data, payload.vault)
        assertEquals(1234, payload.exportedAt)
        assertEquals(1, payload.format)
    }

    @Test
    fun headerLayoutIsStable() {
        val file = export()
        assertEquals(54, ExportFormat.HEADER_LENGTH)
        val b = ByteBuffer.wrap(file)
        assertEquals("SKX1", String(ByteArray(4).also { b.get(it) }, Charsets.US_ASCII))
        assertEquals(1, b.get().toInt())
        assertEquals(1, b.get().toInt())
        assertEquals(kdf.opsLimit, b.getInt())
        assertEquals(kdf.memKiB, b.getInt())
        assertTrue(ExportFormat.looksLikeExport(file))
    }

    @Test
    fun plaintextDoesNotAppearInFile() {
        val text = String(export(), Charsets.ISO_8859_1)
        for (needle in listOf("ali@example.com", "gizli", "JBSWY3DPEHPK3PXP", "entries", "vault")) assertFalse(needle, text.contains(needle))
    }

    @Test
    fun wrongPasswordIsRejected() {
        assertThrows(WrongKeyException::class.java) { ExportFormat.open(engine, export(), "yanlış".toByteArray()) }
    }

    @Test
    fun everyByteIsAuthenticated() {
        val file = export()
        for (i in file.indices) {
            val bad = file.clone().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val detected = try {
                ExportFormat.open(engine, bad, password)
                false
            } catch (e: WrongKeyException) {
                true
            } catch (e: VaultFormatException) {
                true
            } catch (e: IllegalStateException) {
                // KDF parametresi alanı bozulunca türetme başarısız olabilir.
                true
            }
            assertTrue("bayt $i değişikliği fark edilmedi", detected)
        }
    }

    @Test
    fun truncatedForeignAndFutureFilesAreRejected() {
        val file = export()
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, ByteArray(0), password) }
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, file.copyOf(40), password) }
        assertThrows(WrongKeyException::class.java) { ExportFormat.open(engine, file.copyOf(file.size - 1), password) }
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, "PK\u0003\u0004".toByteArray() + ByteArray(200), password) }
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, file.clone().also { it[4] = 2 }, password) }
        val huge = file.clone().also { ByteBuffer.wrap(it).putInt(10, Int.MAX_VALUE) }
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, huge, password) }
    }

    /** Cihazdaki kasa dosyası ile dışa aktarım dosyası birbirinin yerine kabul edilmemeli. */
    @Test
    fun vaultFileAndExportFileAreNotInterchangeable() {
        val dek = VaultFormat.newDek(engine)
        val vaultFile = VaultFormat.seal(engine, VaultFormat.createKeySlot(engine, password, kdf, dek), dek, data)
        assertFalse(ExportFormat.looksLikeExport(vaultFile))
        assertThrows(VaultFormatException::class.java) { ExportFormat.open(engine, vaultFile, password) }
        assertThrows(VaultFormatException::class.java) { VaultFormat.parse(export()) }
    }

    /** Otomatik yedek: kasada saklanan anahtarla yazılan dosya yalnızca parolayla açılabilmeli. */
    @Test
    fun storedBackupKeyProducesFilesOpenableWithPassword() {
        val key = ExportFormat.deriveKey(engine, password, kdf)
        val config = key.toBackupConfig()
        val restored = ExportKey.from(config)
        assertArrayEquals(key.key, restored.key)
        assertArrayEquals(key.salt, restored.salt)
        assertEquals(kdf, restored.kdf)

        val first = ExportFormat.seal(engine, restored, data, 1)
        val second = ExportFormat.seal(engine, restored, data, 2)
        assertFalse("nonce yinelenmemeli", first.copyOfRange(30, 54).contentEquals(second.copyOfRange(30, 54)))
        assertEquals(data, ExportFormat.open(engine, first, password).vault)
        assertEquals(2, ExportFormat.open(engine, second, password).exportedAt)
    }

    /** Yedek anahtarı dosyaya yazılmamalı; içe aktarılan dosyadan da alınmamalı. */
    @Test
    fun backupKeyNeverLeavesOrEntersThroughExport() {
        val key = ExportFormat.deriveKey(engine, password, kdf)
        val withBackup = data.copy(backup = key.toBackupConfig())
        val payload = ExportFormat.open(engine, ExportFormat.seal(engine, key, withBackup, 0), password)
        assertNull(payload.vault.backup)
        assertEquals(data, payload.vault)
        assertFalse(withBackup.toString().contains(key.toBackupConfig().key))
    }

    @Test
    fun vaultWithBackupConfigRoundTripsInVaultFile() {
        val withBackup = data.copy(backup = ExportFormat.deriveKey(engine, password, kdf).toBackupConfig())
        val dek = VaultFormat.newDek(engine)
        val file = VaultFormat.seal(engine, VaultFormat.createKeySlot(engine, password, kdf, dek), dek, withBackup)
        assertEquals(withBackup, VaultFormat.open(engine, VaultFormat.parse(file), dek))
    }

    @Test
    fun mergeAddsNewKeepsNewerAndNeverDeletes() {
        val current = VaultData(
            entries = listOf(
                LoginEntry("a", "Yerel A", updatedAt = 100),
                LoginEntry("b", "Yerel B (yeni)", updatedAt = 300),
                LoginEntry("c", "Yalnızca yerelde", updatedAt = 50),
            ),
            backup = ExportFormat.deriveKey(engine, password, kdf).toBackupConfig(),
        )
        val imported = VaultData(
            entries = listOf(
                LoginEntry("a", "Yedek A (yeni)", updatedAt = 200),
                LoginEntry("b", "Yedek B (eski)", updatedAt = 250),
                LoginEntry("d", "Yalnızca yedekte", updatedAt = 10),
            )
        )
        val result = VaultMerge.merge(current, imported)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(1, result.unchanged)
        assertEquals(
            listOf("Yedek A (yeni)", "Yerel B (yeni)", "Yalnızca yerelde", "Yalnızca yedekte"),
            result.data.entries.map { it.title },
        )
        assertEquals(current.backup, result.data.backup)
        // Aynı dosyayı ikinci kez içe aktarmak hiçbir şeyi değiştirmemeli.
        val again = VaultMerge.merge(result.data, imported)
        assertEquals(0, again.added + again.updated)
        assertEquals(result.data, again.data)
    }
}
