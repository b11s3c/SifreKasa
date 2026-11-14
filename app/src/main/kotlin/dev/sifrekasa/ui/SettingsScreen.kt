package dev.sifrekasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.sifrekasa.AppContainer
import dev.sifrekasa.core.crypto.Secrets
import dev.sifrekasa.security.BiometricAuth
import java.security.GeneralSecurityException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, activity: FragmentActivity, onBack: () -> Unit) {
    val settings = container.settings
    val biometrics = container.biometricKeyStore
    val biometricAvailable = remember { BiometricAuth.isAvailable(activity) }
    var biometricEnabled by remember { mutableStateOf(biometrics.isEnabled) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var lockOnBackground by remember { mutableStateOf(settings.lockOnBackground) }
    var autoLockMinutes by remember { mutableIntStateOf(settings.autoLockMinutes) }
    var clipboardSeconds by remember { mutableIntStateOf(settings.clipboardClearSeconds) }

    fun enableBiometrics() {
        biometricError = null
        val cipher = try {
            biometrics.newEncryptCipher()
        } catch (e: GeneralSecurityException) {
            biometricError = "Biyometrik anahtar oluşturulamadı."
            return
        }
        BiometricAuth.prompt(
            activity = activity,
            title = "Biyometrik kilidi etkinleştir",
            negativeText = "Vazgeç",
            cipher = cipher,
            onSuccess = { authenticated ->
                val dek = container.vault.copyDek()
                try {
                    if (dek == null) {
                        biometrics.disable()
                    } else {
                        biometrics.storeWrapped(authenticated, dek)
                        biometricEnabled = true
                    }
                } catch (e: Exception) {
                    biometrics.disable()
                    biometricError = "Biyometrik kilit etkinleştirilemedi."
                } finally {
                    Secrets.wipe(dek)
                }
            },
            onFailure = { message ->
                biometrics.disable()
                biometricError = message
            },
        )
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Kilit")
            SwitchRow(
                title = "Biyometriyle aç",
                subtitle = when {
                    !biometricAvailable -> "Cihazda kayıtlı güçlü biyometri (parmak izi) yok."
                    else -> "Ana parola yine de geçerlidir. Yeni parmak izi eklenirse yeniden etkinleştirmen gerekir."
                },
                checked = biometricEnabled,
                enabled = biometricAvailable || biometricEnabled,
                onChange = { on ->
                    if (on) enableBiometrics() else {
                        biometrics.disable()
                        biometricEnabled = false
                    }
                },
            )
            biometricError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SwitchRow(
                title = "Arka plana geçince kilitle",
                subtitle = "Uygulamadan çıkıldığında ya da ekran kapandığında kasa hemen kilitlenir.",
                checked = lockOnBackground,
                onChange = {
                    lockOnBackground = it
                    settings.lockOnBackground = it
                },
            )
            HorizontalDivider()
            SectionTitle("İşlem yapılmayınca kilitle")
            ChoiceGroup(
                options = listOf(1 to "1 dakika", 5 to "5 dakika", 15 to "15 dakika", 0 to "Kapalı"),
                selected = autoLockMinutes,
                onSelect = {
                    autoLockMinutes = it
                    settings.autoLockMinutes = it
                    container.lockManager.onUserInteraction()
                },
            )
            HorizontalDivider()
            SectionTitle("Panoyu temizle")
            ChoiceGroup(
                options = listOf(15 to "15 saniye", 30 to "30 saniye", 60 to "60 saniye", 0 to "Kapalı"),
                selected = clipboardSeconds,
                onSelect = {
                    clipboardSeconds = it
                    settings.clipboardClearSeconds = it
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    // Yalnızca anahtar değil, tüm satır dokunulabilir.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ChoiceGroup(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    options.forEach { (value, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                .padding(vertical = 4.dp),
        ) {
            RadioButton(selected = value == selected, onClick = null)
            Text(label, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
