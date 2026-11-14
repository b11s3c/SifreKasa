package dev.sifrekasa.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

data class BackupStatus(val location: String?, val lastSuccessAt: Long, val lastError: String?)

/**
 * Şifreli yedek dosyasını Storage Access Framework üzerinden kullanıcının seçtiği belgeye yazar.
 * Bu sınıf yalnızca ŞİFRELİ baytları görür. Ağ erişimi yoktur; hedef Google Drive ise yüklemeyi
 * Drive uygulamasının kendi belge sağlayıcısı yapar.
 */
class BackupManager(
    private val context: Context,
    private val vault: VaultRepository,
    private val settings: AppSettings,
) {
    private val resolver = context.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    val location: Uri? get() = settings.backupUri?.let(Uri::parse)

    init {
        vault.onBackupReady = { bytes ->
            val target = location
            if (target != null && settings.autoBackup) scope.launch { backupTo(target, bytes) }
        }
    }

    /** Seçilen belgeyi kalıcı yedek konumu yapar (izin yeniden başlatmalardan sonra da geçerli kalır). */
    fun adoptLocation(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.backupUri = uri.toString()
        _status.value = currentStatus()
    }

    fun forgetLocation() {
        location?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        settings.backupUri = null
        settings.lastBackupAt = 0
        settings.lastBackupError = null
        _status.value = currentStatus()
    }

    /** Elle yedek. @return hata iletisi; başarılıysa null */
    suspend fun backupNow(): String? {
        val target = location ?: return "Yedek konumu seçilmemiş."
        val bytes = vault.sealBackup() ?: return "Kasa kilitli ya da yedekleme kurulu değil."
        return backupTo(target, bytes)
    }

    /** Yedek konumuna yazar ve sonucu durum olarak kaydeder. */
    suspend fun backupTo(target: Uri, bytes: ByteArray): String? {
        val error = writeVerified(target, bytes)
        if (error == null) settings.lastBackupAt = System.currentTimeMillis()
        settings.lastBackupError = error
        _status.value = currentStatus()
        return error
    }

    /**
     * [bytes]'ı belgeye yazar, sonra geri okuyup bayt bayt karşılaştırır: yarım kalmış ya da
     * sağlayıcının bozduğu bir yedek fark edilmeden kalmasın. @return hata iletisi ya da null
     */
    suspend fun writeVerified(target: Uri, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        writeLock.withLock {
            try {
                val out = resolver.openOutputStream(target, "wt") ?: return@withLock "Dosya açılamadı."
                out.use { it.write(bytes) }
                val written = readDocument(context, target, bytes.size.toLong() + 1)
                if (written.contentEquals(bytes)) null else "Yazılan dosya doğrulanamadı."
            } catch (e: IOException) {
                "Dosya yazılamadı (${e.javaClass.simpleName})."
            } catch (e: SecurityException) {
                "Dosyaya erişim izni yok. Yedek konumunu yeniden seç."
            } catch (e: RuntimeException) {
                // Belge sağlayıcıları (ör. bulut sürücüleri) beklenmedik hatalar fırlatabilir.
                "Dosya yazılamadı (${e.javaClass.simpleName})."
            }
        }
    }

    private fun currentStatus() = BackupStatus(
        location = location?.let { displayName(context, it) ?: "seçili dosya" },
        lastSuccessAt = settings.lastBackupAt,
        lastError = settings.lastBackupError,
    )

    companion object {
        /** İçe aktarımda kabul edilen en büyük dosya. */
        const val MAX_IMPORT_BYTES = 64L * 1024 * 1024

        /** @throws IOException okunamazsa ya da [maxBytes]'tan büyükse */
        fun readDocument(context: Context, uri: Uri, maxBytes: Long = MAX_IMPORT_BYTES): ByteArray {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: SecurityException) {
                throw IOException("Erişim izni yok")
            } ?: throw IOException("Dosya açılamadı")
            input.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                    if (buffer.size() > maxBytes) throw IOException("Dosya çok büyük")
                }
                return buffer.toByteArray()
            }
        }

        fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
