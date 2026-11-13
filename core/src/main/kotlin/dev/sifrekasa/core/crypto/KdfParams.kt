package dev.sifrekasa.core.crypto

/** Argon2id maliyet parametreleri. Dosya başlığında saklanır, sır değildir. */
data class KdfParams(val opsLimit: Int, val memKiB: Int) {
    init {
        require(opsLimit in MIN_OPS..MAX_OPS) { "opsLimit aralık dışı" }
        require(memKiB in MIN_MEM_KIB..MAX_MEM_KIB) { "memKiB aralık dışı" }
    }

    companion object {
        const val MIN_OPS = 1
        const val MAX_OPS = 16
        const val MIN_MEM_KIB = 8
        /** Bozuk/kötü niyetli bir dosyanın cihazı belleksiz bırakmasını önleyen üst sınır (1 GiB). */
        const val MAX_MEM_KIB = 1024 * 1024

        /** Yeni kasalar için: 3 geçiş, 128 MiB. */
        val DEFAULT = KdfParams(opsLimit = 3, memKiB = 128 * 1024)
    }
}
