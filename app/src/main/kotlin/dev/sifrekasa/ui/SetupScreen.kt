package dev.sifrekasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.vault.ExportFormat
import dev.sifrekasa.core.vault.VaultFormatException
import dev.sifrekasa.core.vault.WrongKeyException
import dev.sifrekasa.data.BackupManager
import dev.sifrekasa.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.launch

private const val MIN_MASTER_PASSWORD_LENGTH = 10

@Composable
fun SetupScreen(vault: VaultRepository) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Yedekten geri yükleme: önce dosya çözülür, sonra bu cihaz için yeni bir ana parola belirlenir.
    var restored by remember { mutableStateOf<VaultData?>(null) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { BackupManager.readDocument(context, uri) }
                    if (ExportFormat.looksLikeExport(bytes)) {
                        error = null
                        restoreBytes = bytes
                    } else {
                        error = "Bu dosya bir Şifre Kasa yedeği değil."
                    }
                } catch (e: IOException) {
                    error = "Dosya okunamadı: ${e.message}"
                }
            }
        }
    }

    val tooShort = password.isNotEmpty() && password.length < MIN_MASTER_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canCreate = !working && password.length >= MIN_MASTER_PASSWORD_LENGTH && confirm == password

    fun create() {
        if (!canCreate) return
        val chars = password.toCharArray()
        password = ""
        confirm = ""
        working = true
        error = null
        scope.launch {
            try {
                vault.create(chars, restored ?: VaultData())
            } catch (e: Exception) {
                error = "Kasa oluşturulamadı. Cihazda yeterli boş bellek olmayabilir."
            } finally {
                working = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Kasanı oluştur", style = MaterialTheme.typography.headlineMedium)
            restored?.let {
                Text(
                    "Yedekten ${it.entries.size} kayıt yüklenecek. Şimdi bu cihaz için bir ana parola belirle.",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Ana parola kasanın tek anahtarıdır ve hiçbir yere kaydedilmez. " +
                    "Unutursan kasayı kurtarmanın bir yolu yoktur.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Ana parola",
                imeAction = ImeAction.Next,
                isError = tooShort,
                enabled = !working,
                supportingText = "En az $MIN_MASTER_PASSWORD_LENGTH karakter. Birkaç kelimelik bir parola cümlesi önerilir.",
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Ana parola (tekrar)",
                onImeAction = ::create,
                isError = mismatch,
                enabled = !working,
                supportingText = if (mismatch) "Parolalar eşleşmiyor" else null,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::create, enabled = canCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Kasayı oluştur")
            }
            if (restored == null) {
                OutlinedButton(
                    onClick = { restorePicker.launch(arrayOf("*/*")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Yedekten geri yükle") }
            }
            if (working) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Anahtar türetiliyor…", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (restoreBytes != null) {
        AskPasswordDialog(
            title = "Yedekten geri yükle",
            description = "Yedeğin (ya da dışa aktarım dosyasının) parolasını gir.",
            onDismiss = { restoreBytes = null },
            onConfirm = { chars ->
                val bytes = restoreBytes
                restoreBytes = null
                if (bytes != null) {
                    working = true
                    scope.launch {
                        try {
                            restored = vault.openExport(bytes, chars).vault
                        } catch (e: WrongKeyException) {
                            error = "Yedek parolası yanlış ya da dosya bozuk."
                        } catch (e: VaultFormatException) {
                            error = "Dosya okunamadı: ${e.message}"
                        } catch (e: Exception) {
                            error = "Yedek açılamadı."
                        } finally {
                            working = false
                        }
                    }
                }
            },
        )
    }
}
