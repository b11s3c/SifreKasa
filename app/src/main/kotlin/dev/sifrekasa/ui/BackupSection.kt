package dev.sifrekasa.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sifrekasa.AppContainer
import dev.sifrekasa.core.crypto.Secrets
import dev.sifrekasa.core.vault.ExportFormat
import dev.sifrekasa.core.vault.VaultFormatException
import dev.sifrekasa.core.vault.WrongKeyException
import dev.sifrekasa.data.BackupManager
import dev.sifrekasa.data.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SKX_MIME = "application/octet-stream"

private enum class BackupDialog { EXPORT_PASSWORD, BACKUP_PASSWORD, IMPORT_PASSWORD, CONFIRM_DISABLE }

/** Ayarlar ekranının dışa/içe aktarım ve yedekleme bölümü. */
@Composable
fun BackupSection(container: AppContainer) {
    val context = LocalContext.current
    val vault = container.vault
    val backup = container.backup
    val scope = rememberCoroutineScope()
    val status by backup.status.collectAsStateWithLifecycle()
    val state by vault.state.collectAsStateWithLifecycle()
    val backupConfigured = (state as? VaultState.Unlocked)?.data?.backup != null && status.location != null

    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var autoBackup by remember { mutableStateOf(container.settings.autoBackup) }
    // Dosya seçici açıkken bekleyen ŞİFRELİ baytlar; düz veri burada tutulmaz.
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBackup by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    // true: mevcut yedekleme için yalnızca konum değiştiriliyor; seçim iptal edilirse hiçbir şey bozulmaz.
    var changingLocation by remember { mutableStateOf(false) }

    fun report(text: String, error: Boolean = false) {
        message = text
        isError = error
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SKX_MIME)) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) {
            busy = true
            scope.launch {
                val error = backup.writeVerified(uri, bytes)
                busy = false
                if (error == null) report("Dışa aktarıldı ve doğrulandı.") else report(error, error = true)
            }
        }
    }

    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SKX_MIME)) { uri ->
        val bytes = pendingBackup
        val changing = changingLocation
        pendingBackup = null
        changingLocation = false
        busy = true
        scope.launch {
            if (uri == null || bytes == null) {
                // Konum seçilmedi: ilk kurulumsa yarım kalan kurulumu geri al; konum değişikliğiyse eskisi kalır.
                if (!changing) vault.setBackup(null)
                busy = false
                return@launch
            }
            val previous = backup.location
            try {
                backup.adoptLocation(uri)
                if (changing && previous != null && previous != uri) backup.releaseLocation(previous)
            } catch (e: SecurityException) {
                if (!changing) vault.setBackup(null)
                busy = false
                report("Bu konum kalıcı erişime izin vermiyor. Başka bir konum seç.", error = true)
                return@launch
            }
            val error = backup.backupTo(uri, bytes)
            busy = false
            if (error == null) report(if (changing) "Yedek konumu değişti; yedek yeni konuma yazıldı ve doğrulandı." else "Yedekleme kuruldu; ilk yedek yazıldı ve doğrulandı.") else report(error, error = true)
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { BackupManager.readDocument(context, uri) }
                    if (ExportFormat.looksLikeExport(bytes)) {
                        pendingImport = bytes
                        dialog = BackupDialog.IMPORT_PASSWORD
                    } else {
                        report("Bu dosya bir Şifre Kasa dışa aktarım dosyası değil.", error = true)
                    }
                } catch (e: IOException) {
                    report("Dosya okunamadı: ${e.message}", error = true)
                }
            }
        }
    }

    fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Dışa aktarılan dosya, ana paroladan ayrı bir parolayla şifrelenir. Bu parolayı unutursan dosya açılamaz.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { dialog = BackupDialog.EXPORT_PASSWORD }) { Text("Dışa aktar") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    container.lockManager.expectExternalActivity()
                    importPicker.launch(arrayOf("*/*"))
                },
            ) { Text("İçe aktar") }
        }

        Text("Yedekleme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        if (!backupConfigured) {
            Text(
                "Aynı şifreli dosyayı seçtiğin bir konuma yazar ve kasa değiştikçe günceller. Google Drive için açılan " +
                    "kaydetme ekranında sol üstteki menüden (☰) Drive'ı seç; varsayılan konum telefonun depolamasıdır. " +
                    "Uygulama yedek için internete bağlanmaz; yüklemeyi seçtiğin konumun kendi uygulaması yapar.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(enabled = !busy, onClick = { dialog = BackupDialog.BACKUP_PASSWORD }) { Text("Yedeklemeyi kur") }
        } else {
            Text("Konum: ${status.location}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (status.lastSuccessAt > 0) {
                    "Son başarılı yedek: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(status.lastSuccessAt))
                } else {
                    "Henüz yedek alınmadı."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            status.lastError?.let {
                Text("Son deneme başarısız: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = autoBackup, role = Role.Switch) {
                        autoBackup = it
                        container.settings.autoBackup = it
                    }
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Otomatik yedekle", style = MaterialTheme.typography.bodyLarge)
                    Text("Kasa her değiştiğinde yedek dosyası güncellenir.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoBackup, onCheckedChange = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val error = backup.backupNow()
                            busy = false
                            if (error == null) report("Yedek yazıldı ve doğrulandı.") else report(error, error = true)
                        }
                    },
                ) { Text("Şimdi yedekle") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val bytes = vault.sealBackup()
                        if (bytes != null) {
                            pendingBackup = bytes
                            changingLocation = true
                            container.lockManager.expectExternalActivity()
                            backupPicker.launch("sifrekasa-yedek.${ExportFormat.FILE_EXTENSION}")
                        }
                    },
                ) { Text("Konumu değiştir") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { dialog = BackupDialog.CONFIRM_DISABLE }) { Text("Yedeklemeyi kapat") }
            }
        }
        if (busy) CircularProgressIndicator()
        message?.let {
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }

    when (dialog) {
        BackupDialog.EXPORT_PASSWORD -> NewPasswordDialog(
            title = "Dışa aktarım parolası",
            description = "Dosya bu parolayla şifrelenecek. Ana parolandan farklı bir parola kullan.",
            onDismiss = { dialog = null },
            onConfirm = { chars ->
                dialog = null
                busy = true
                scope.launch {
                    try {
                        val key = vault.deriveExportKey(chars)
                        val bytes = vault.sealExport(key)
                        Secrets.wipe(key.key)
                        if (bytes == null) {
                            report("Kasa kilitli.", error = true)
                        } else {
                            pendingExport = bytes
                            container.lockManager.expectExternalActivity()
                            exportPicker.launch("sifrekasa-${today()}.${ExportFormat.FILE_EXTENSION}")
                        }
                    } catch (e: Exception) {
                        report("Dışa aktarım hazırlanamadı.", error = true)
                    } finally {
                        busy = false
                    }
                }
            },
        )
        BackupDialog.BACKUP_PASSWORD -> NewPasswordDialog(
            title = "Yedek parolası",
            description = "Yedek dosyası bu parolayla şifrelenir. Parola saklanmaz; yedeği geri yüklerken sorulur.",
            onDismiss = { dialog = null },
            onConfirm = { chars ->
                dialog = null
                busy = true
                scope.launch {
                    try {
                        val key = vault.deriveExportKey(chars)
                        vault.setBackup(key.toBackupConfig())
                        Secrets.wipe(key.key)
                        val bytes = vault.sealBackup()
                        if (bytes == null) {
                            report("Kasa kilitli.", error = true)
                        } else {
                            pendingBackup = bytes
                            container.lockManager.expectExternalActivity()
                            backupPicker.launch("sifrekasa-yedek.${ExportFormat.FILE_EXTENSION}")
                        }
                    } catch (e: Exception) {
                        report("Yedekleme kurulamadı.", error = true)
                    } finally {
                        busy = false
                    }
                }
            },
        )
        BackupDialog.IMPORT_PASSWORD -> AskPasswordDialog(
            title = "İçe aktar",
            description = "Dosyanın dışa aktarım (ya da yedek) parolasını gir. Mevcut kayıtların silinmez.",
            onDismiss = {
                dialog = null
                pendingImport = null
            },
            onConfirm = { chars ->
                val bytes = pendingImport
                dialog = null
                pendingImport = null
                if (bytes != null) {
                    busy = true
                    scope.launch {
                        try {
                            val payload = vault.openExport(bytes, chars)
                            val result = vault.merge(payload.vault)
                            if (result == null) report("Kasa kilitli.", error = true)
                            else report("İçe aktarıldı: ${result.added} yeni, ${result.updated} güncellendi, ${result.unchanged} değişmedi.")
                        } catch (e: WrongKeyException) {
                            report("Parola yanlış ya da dosya bozuk.", error = true)
                        } catch (e: VaultFormatException) {
                            report("Dosya okunamadı: ${e.message}", error = true)
                        } catch (e: Exception) {
                            report("İçe aktarılamadı.", error = true)
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        )
        BackupDialog.CONFIRM_DISABLE -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Yedekleme kapatılsın mı?") },
            text = { Text("Yedek anahtarı kasadan silinir. Daha önce yazılmış yedek dosyası yerinde kalır.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        vault.setBackup(null)
                        backup.forgetLocation()
                        report("Yedekleme kapatıldı.")
                    }
                }) { Text("Kapat") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Vazgeç") } },
        )
        null -> Unit
    }
}
