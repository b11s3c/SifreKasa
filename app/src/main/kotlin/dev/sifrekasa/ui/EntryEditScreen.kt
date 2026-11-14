package dev.sifrekasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontFamily
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.totp.OtpAuth
import dev.sifrekasa.core.totp.OtpAuthEntry
import dev.sifrekasa.core.totp.OtpAuthException
import dev.sifrekasa.core.totp.Totp
import dev.sifrekasa.core.totp.TotpConfig
import dev.sifrekasa.data.VaultRepository
import dev.sifrekasa.security.ClipboardHelper
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    existing: LoginEntry?,
    prefill: OtpAuthEntry?,
    vault: VaultRepository,
    clipboard: ClipboardHelper,
    onClose: () -> Unit,
) {
    // Ekran açıkken kayıt değişse bile form ilk değerlerle kalsın.
    val original = remember { existing }
    var title by remember { mutableStateOf(original?.title ?: prefill?.issuer.orEmpty()) }
    var username by remember { mutableStateOf(original?.username ?: prefill?.account.orEmpty()) }
    var totp by remember { mutableStateOf(original?.totp ?: prefill?.config) }
    var passkey by remember { mutableStateOf(original?.passkey) }
    var confirmPasskeyDelete by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showManualTotp by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf(original?.password.orEmpty()) }
    var url by remember { mutableStateOf(original?.url.orEmpty()) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var showGenerator by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dirty = title != original?.title.orEmpty() || username != original?.username.orEmpty() ||
        password != original?.password.orEmpty() || url != original?.url.orEmpty() || notes != original?.notes.orEmpty() ||
        totp != original?.totp || passkey != original?.passkey

    fun back() {
        if (dirty) confirmDiscard = true else onClose()
    }

    fun save() {
        if (saving) return
        saving = true
        val now = System.currentTimeMillis()
        val entry = LoginEntry(
            id = original?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            username = username.trim(),
            password = password,
            url = url.trim(),
            notes = notes,
            totp = totp,
            passkey = passkey,
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
        )
        scope.launch {
            vault.upsert(entry)
            onClose()
        }
    }

    /** Taranan ya da elle girilen 2FA bilgisini forma uygular; boş alanları yayıncı/hesapla doldurur. */
    fun applyTotp(entry: OtpAuthEntry) {
        totp = entry.config
        if (title.isBlank()) title = entry.issuer
        if (username.isBlank()) username = entry.account
    }

    if (scanning) {
        QrScannerScreen(
            title = "2FA QR kodunu tara",
            error = scanError,
            onScanned = { text ->
                try {
                    applyTotp(OtpAuth.parse(text))
                    scanning = false
                } catch (e: OtpAuthException) {
                    scanError = "Bu QR bir 2FA kodu değil: ${e.message}"
                }
            },
            onCancel = { scanning = false },
        )
        return
    }

    BackHandler(onBack = ::back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Yeni kayıt" else "Kaydı düzenle") },
                navigationIcon = {
                    IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Sil") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Başlık") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı adı") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Şifre",
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showGenerator = true }) { Text("Üret") }
                OutlinedButton(onClick = { clipboard.copySensitive(password) }, enabled = password.isNotEmpty()) {
                    Text("Şifreyi kopyala")
                }
                OutlinedButton(onClick = { clipboard.copySensitive(username) }, enabled = username.isNotEmpty()) {
                    Text("Kullanıcı adı")
                }
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TotpSection(
                totp = totp,
                onCopy = { clipboard.copySensitive(it) },
                onScan = {
                    scanError = null
                    scanning = true
                },
                onManual = { showManualTotp = true },
                onRemove = { totp = null },
            )
            passkey?.let { key ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Geçiş anahtarı", style = MaterialTheme.typography.titleSmall)
                        Text("Site: ${key.rpId}")
                        if (key.userName.isNotBlank()) Text("Hesap: ${key.userName}")
                        Text(
                            "Oluşturma: " + java.text.DateFormat.getDateInstance().format(java.util.Date(key.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { confirmPasskeyDelete = true }) { Text("Geçiş anahtarını sil") }
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Not") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = ::save,
                enabled = !saving && (title.isNotBlank() || username.isNotBlank() || password.isNotEmpty() || totp != null || passkey != null),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Kaydet") }
        }
    }

    if (confirmPasskeyDelete) {
        AlertDialog(
            onDismissRequest = { confirmPasskeyDelete = false },
            title = { Text("Geçiş anahtarı silinsin mi?") },
            text = { Text("Kaydettiğinde anahtar kasadan kalkar ve bu siteye onunla giremezsin. Sitedeki kaydını ayrıca sitenin ayarlarından kaldırman gerekir.") },
            confirmButton = {
                TextButton(onClick = {
                    passkey = null
                    confirmPasskeyDelete = false
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmPasskeyDelete = false }) { Text("Vazgeç") } },
        )
    }
    if (showManualTotp) {
        ManualTotpDialog(
            onAccept = {
                applyTotp(it)
                showManualTotp = false
            },
            onDismiss = { showManualTotp = false },
        )
    }
    if (showGenerator) {
        GeneratorDialog(
            onUse = {
                password = it
                showGenerator = false
            },
            onDismiss = { showGenerator = false },
        )
    }
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("\"${original.title.ifBlank { "(başlıksız)" }}\" kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        vault.delete(original.id)
                        onClose()
                    }
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Değişiklikler kaydedilmedi") },
            text = { Text("Kaydetmeden çıkılsın mı?") },
            confirmButton = { TextButton(onClick = onClose) { Text("Çık") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Düzenlemeye dön") } },
        )
    }
}

@Composable
private fun TotpSection(
    totp: TotpConfig?,
    onCopy: (String) -> Unit,
    onScan: () -> Unit,
    onManual: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("İki adımlı doğrulama (2FA)", style = MaterialTheme.typography.titleSmall)
            if (totp == null) {
                Text("Bu kayda bağlı bir doğrulama kodu yok.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan) { Text("QR tara") }
                    OutlinedButton(onClick = onManual) { Text("Elle gir") }
                }
            } else {
                val now = rememberEpochSeconds()
                val code = Totp.code(totp, now)
                val remaining = Totp.secondsRemaining(totp, now)
                Text(formatCode(code), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                LinearProgressIndicator(
                    progress = { remaining / totp.period.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$remaining sn sonra yenilenir", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCopy(code) }) { Text("Kodu kopyala") }
                    TextButton(onClick = onRemove) { Text("2FA'yı kaldır") }
                }
            }
        }
    }
}

@Composable
private fun ManualTotpDialog(onAccept: (OtpAuthEntry) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("2FA anahtarını gir") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                label = { Text("Gizli anahtar ya da otpauth:// bağlantısı") },
                isError = error != null,
                supportingText = { Text(error ?: "Sitenin \"QR okutamıyorum\" seçeneğinde gösterdiği anahtar.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    try {
                        onAccept(OtpAuth.parseManual(input))
                    } catch (e: OtpAuthException) {
                        error = e.message
                    }
                },
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
