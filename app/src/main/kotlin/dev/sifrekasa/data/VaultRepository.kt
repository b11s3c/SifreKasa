package dev.sifrekasa.data

import android.os.SystemClock
import android.util.AtomicFile
import dev.sifrekasa.core.autofill.DomainMatcher
import dev.sifrekasa.core.passkey.Passkey
import java.util.UUID
import dev.sifrekasa.core.crypto.CryptoEngine
import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.crypto.Secrets
import dev.sifrekasa.core.model.BackupConfig
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.vault.ExportFormat
import dev.sifrekasa.core.vault.ExportKey
import dev.sifrekasa.core.vault.ExportPayload
import dev.sifrekasa.core.vault.KeySlot
import dev.sifrekasa.core.vault.MergeResult
import dev.sifrekasa.core.vault.VaultMerge
import dev.sifrekasa.core.vault.VaultFormat
import dev.sifrekasa.core.vault.VaultFormatException
import dev.sifrekasa.core.vault.WrongKeyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface VaultState {
    data object NoVault : VaultState
    data object Locked : VaultState
    data class Unlocked(val data: VaultData) : VaultState
}

enum class UnlockResult { SUCCESS, WRONG_KEY, CORRUPTED }

/**
 * Kasa dosyasının tek sahibi. Veri anahtarı (DEK) ve çözülmüş içerik yalnızca kilit açıkken
 * bellekte durur; [lock] ikisini de bırakır ve DEK'i sıfırlar.
 */
class VaultRepository(private val file: File, private val engine: CryptoEngine) {
    private val guard = Any()
    private var dek: ByteArray? = null
    private var keySlot: KeySlot? = null

    @Volatile
    private var lastUnlockAt = 0L

    private val _state = MutableStateFlow<VaultState>(if (file.exists()) VaultState.Locked else VaultState.NoVault)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    /**
     * Kasa her değiştiğinde, yedekleme kuruluysa, güncel içeriğin ŞİFRELİ yedek dosyası baytlarıyla çağrılır.
     * Dinleyici düz veriye hiç erişmez.
     */
    @Volatile
    var onBackupReady: ((ByteArray) -> Unit)? = null

    /** [password] çağrı sonunda silinir. [initial]: yedekten geri yüklenen içerik. */
    suspend fun create(password: CharArray, initial: VaultData = VaultData()) = withContext(Dispatchers.Default) {
        val pw = Secrets.utf8(password)
        Secrets.wipe(password)
        try {
            val newDek = VaultFormat.newDek(engine)
            val slot = VaultFormat.createKeySlot(engine, pw, KdfParams.DEFAULT, newDek)
            synchronized(guard) {
                check(!file.exists()) { "Kasa zaten var" }
                val data = initial.copy(backup = null)
                write(slot, newDek, data)
                dek = newDek
                keySlot = slot
                _state.value = VaultState.Unlocked(data)
            }
        } finally {
            Secrets.wipe(pw)
        }
    }

    /** [password] çağrı sonunda silinir. */
    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val pw = Secrets.utf8(password)
        Secrets.wipe(password)
        try {
            val parsed = VaultFormat.parse(file.readBytes())
            val candidate = VaultFormat.unwrapDek(engine, parsed.keySlot, pw)
            val data = VaultFormat.open(engine, parsed, candidate)
            adopt(candidate, parsed.keySlot, data)
            UnlockResult.SUCCESS
        } catch (e: WrongKeyException) {
            UnlockResult.WRONG_KEY
        } catch (e: VaultFormatException) {
            UnlockResult.CORRUPTED
        } catch (e: IOException) {
            UnlockResult.CORRUPTED
        } finally {
            Secrets.wipe(pw)
        }
    }

    /** Biyometri yolu. [candidate] sahipliği depoya geçer; başarısızlıkta silinir. */
    suspend fun unlockWithDek(candidate: ByteArray): UnlockResult = withContext(Dispatchers.Default) {
        try {
            val parsed = VaultFormat.parse(file.readBytes())
            val data = VaultFormat.open(engine, parsed, candidate)
            adopt(candidate, parsed.keySlot, data)
            UnlockResult.SUCCESS
        } catch (e: WrongKeyException) {
            Secrets.wipe(candidate)
            UnlockResult.WRONG_KEY
        } catch (e: VaultFormatException) {
            Secrets.wipe(candidate)
            UnlockResult.CORRUPTED
        } catch (e: IOException) {
            Secrets.wipe(candidate)
            UnlockResult.CORRUPTED
        }
    }

    fun lock() {
        synchronized(guard) {
            Secrets.wipe(dek)
            dek = null
            keySlot = null
            if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
        }
    }

    suspend fun upsert(entry: LoginEntry) = mutate { data ->
        val exists = data.entries.any { it.id == entry.id }
        data.copy(entries = if (exists) data.entries.map { if (it.id == entry.id) entry else it } else data.entries + entry)
    }

    suspend fun delete(id: String) = mutate { data ->
        data.copy(entries = data.entries.filterNot { it.id == id })
    }

    /** Kasa son [withinMs] içinde (parola ya da biyometriyle) açıldıysa true: kullanıcı az önce doğrulandı. */
    fun unlockedWithin(withinMs: Long): Boolean =
        state.value is VaultState.Unlocked && SystemClock.elapsedRealtime() - lastUnlockAt <= withinMs

    /**
     * Yeni geçiş anahtarını kaydeder: aynı site ve kullanıcı adına sahip, geçiş anahtarı olmayan bir
     * kayıt varsa ona bağlar; yoksa yeni kayıt açar.
     */
    suspend fun savePasskey(passkey: Passkey) = mutate { data ->
        val now = System.currentTimeMillis()
        val target = data.entries.firstOrNull { entry ->
            entry.passkey == null && entry.username.equals(passkey.userName, ignoreCase = true) &&
                (DomainMatcher.matches(entry.url, passkey.rpId) || DomainMatcher.matches(passkey.rpId, entry.url))
        }
        if (target != null) {
            data.copy(entries = data.entries.map { if (it.id == target.id) it.copy(passkey = passkey, updatedAt = now) else it })
        } else {
            data.copy(
                entries = data.entries + LoginEntry(
                    id = UUID.randomUUID().toString(),
                    title = passkey.rpName.ifBlank { passkey.rpId },
                    username = passkey.userName,
                    url = "https://${passkey.rpId}",
                    createdAt = now,
                    updatedAt = now,
                    passkey = passkey,
                )
            )
        }
    }

    /** Yedekleme anahtarını kaydeder ya da (null ile) siler. */
    suspend fun setBackup(config: BackupConfig?) = mutate { it.copy(backup = config) }

    /** İçe aktarılan kayıtları birleştirir; kasa kilitliyse null. */
    suspend fun merge(imported: VaultData): MergeResult? {
        var result: MergeResult? = null
        val saved = mutate { current -> VaultMerge.merge(current, imported).also { result = it }.data }
        return if (saved) result else null
    }

    /** Dışa aktarım parolasından anahtar türetir (yavaş). [password] silinir. */
    suspend fun deriveExportKey(password: CharArray): ExportKey = withContext(Dispatchers.Default) {
        val pw = Secrets.utf8(password)
        Secrets.wipe(password)
        try {
            ExportFormat.deriveKey(engine, pw)
        } finally {
            Secrets.wipe(pw)
        }
    }

    /**
     * Bir dışa aktarım dosyasını çözer (yavaş). [password] silinir.
     * @throws WrongKeyException, VaultFormatException
     */
    suspend fun openExport(bytes: ByteArray, password: CharArray): ExportPayload = withContext(Dispatchers.Default) {
        val pw = Secrets.utf8(password)
        Secrets.wipe(password)
        try {
            ExportFormat.open(engine, bytes, pw)
        } finally {
            Secrets.wipe(pw)
        }
    }

    /** Güncel içeriğin [key] ile şifrelenmiş dışa aktarım dosyası; kasa kilitliyse null. */
    fun sealExport(key: ExportKey): ByteArray? = synchronized(guard) {
        val data = (_state.value as? VaultState.Unlocked)?.data ?: return null
        ExportFormat.seal(engine, key, data, System.currentTimeMillis())
    }

    /** Kasada saklanan yedek anahtarıyla şifrelenmiş yedek dosyası; yedekleme kurulu değilse ya da kasa kilitliyse null. */
    fun sealBackup(): ByteArray? = synchronized(guard) {
        val data = (_state.value as? VaultState.Unlocked)?.data ?: return null
        sealBackup(data)
    }

    private fun sealBackup(data: VaultData): ByteArray? {
        val config = data.backup ?: return null
        val key = try {
            ExportKey.from(config)
        } catch (e: IllegalArgumentException) {
            return null
        }
        try {
            return ExportFormat.seal(engine, key, data, System.currentTimeMillis())
        } finally {
            Secrets.wipe(key.key)
        }
    }

    /** Biyometrik sarma için DEK'in kopyası; çağıran kullanınca silmelidir. Kasa kilitliyse null. */
    fun copyDek(): ByteArray? = synchronized(guard) { dek?.clone() }

    private fun adopt(newDek: ByteArray, slot: KeySlot, data: VaultData) {
        synchronized(guard) {
            Secrets.wipe(dek)
            dek = newDek
            keySlot = slot
            lastUnlockAt = SystemClock.elapsedRealtime()
            _state.value = VaultState.Unlocked(data)
        }
    }

    /** Önce diske yazar, başarılıysa bellekteki durumu günceller. Kasa kilitliyse hiçbir şey yapmaz. */
    private suspend fun mutate(change: (VaultData) -> VaultData): Boolean = withContext(Dispatchers.IO) {
        synchronized(guard) {
            val current = (_state.value as? VaultState.Unlocked)?.data ?: return@withContext false
            val key = dek ?: return@withContext false
            val slot = keySlot ?: return@withContext false
            val updated = change(current)
            write(slot, key, updated)
            _state.value = VaultState.Unlocked(updated)
            onBackupReady?.let { listener -> sealBackup(updated)?.let(listener) }
            true
        }
    }

    /** Atomik yazma: yarıda kesilirse eski dosya bozulmadan kalır. */
    private fun write(slot: KeySlot, key: ByteArray, data: VaultData) {
        val bytes = VaultFormat.seal(engine, slot, key, data)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: IOException) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
