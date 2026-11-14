package dev.sifrekasa.core.vault

import dev.sifrekasa.core.model.VaultData

data class MergeResult(val data: VaultData, val added: Int, val updated: Int, val unchanged: Int)

object VaultMerge {
    /**
     * İçe aktarılan kayıtları mevcut kasayla birleştirir. Hiçbir kayıt silinmez.
     * Aynı kimlikli kayıtta daha yeni [updatedAt] kazanır; eşitlikte mevcut kayıt kalır.
     * Kasanın kendi ayarları (ör. yedek anahtarı) içe aktarımdan etkilenmez.
     */
    fun merge(current: VaultData, imported: VaultData): MergeResult {
        val byId = LinkedHashMap(current.entries.associateBy { it.id })
        var added = 0
        var updated = 0
        var unchanged = 0
        for (entry in imported.entries) {
            val existing = byId[entry.id]
            when {
                existing == null -> { byId[entry.id] = entry; added++ }
                entry.updatedAt > existing.updatedAt -> { byId[entry.id] = entry; updated++ }
                else -> unchanged++
            }
        }
        return MergeResult(current.copy(entries = byId.values.toList()), added, updated, unchanged)
    }
}
