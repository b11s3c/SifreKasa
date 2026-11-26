package dev.sifrekasa.core

import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.model.BackupConfig
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.vault.VaultMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultMergeTest {

    private fun entry(id: String, title: String, updatedAt: Long) = LoginEntry(
        id = id,
        title = title,
        username = "user",
        password = "secret",
        updatedAt = updatedAt,
    )

    @Test
    fun `merge adds new entries, updates newer, keeps older or equal, and never deletes`() {
        val backup = BackupConfig(
            opsLimit = KdfParams.DEFAULT.opsLimit,
            memKiB = KdfParams.DEFAULT.memKiB,
            salt = "BBBB",
            key = "AAAA",
        )
        val current = VaultData(
            entries = listOf(
                entry("keep-only-local", "Yerel", 100),
                entry("update-me", "Eski", 100),
                entry("keep-current-newer", "Güncel", 200),
                entry("equal-timestamp", "Mevcut", 150),
            ),
            backup = backup,
        )
        val imported = VaultData(
            entries = listOf(
                entry("update-me", "Yeni", 300),
                entry("keep-current-newer", "Eski İçe Aktarım", 100),
                entry("equal-timestamp", "İçe Aktarılan", 150),
                entry("brand-new", "Yeni Kayıt", 250),
            ),
            backup = null,
        )

        val result = VaultMerge.merge(current, imported)

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(2, result.unchanged)
        assertEquals(backup, result.data.backup)

        val titlesById = result.data.entries.associate { it.id to it.title }
        assertEquals(5, titlesById.size)
        assertEquals("Yerel", titlesById["keep-only-local"])
        assertEquals("Yeni", titlesById["update-me"])
        assertEquals("Güncel", titlesById["keep-current-newer"])
        assertEquals("Mevcut", titlesById["equal-timestamp"])
        assertEquals("Yeni Kayıt", titlesById["brand-new"])
    }
}
