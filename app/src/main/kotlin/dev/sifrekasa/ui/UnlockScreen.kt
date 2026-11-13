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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.sifrekasa.AppContainer
import dev.sifrekasa.data.UnlockResult
import dev.sifrekasa.security.BiometricAuth
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException

@Composable
fun UnlockScreen(container: AppContainer, activity: FragmentActivity) {
    val vault = container.vault
    val biometrics = container.biometricKeyStore
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(biometrics.isEnabled && BiometricAuth.isAvailable(activity)) }
    val scope = rememberCoroutineScope()

    fun report(result: UnlockResult, wrongKeyMessage: String) {
        error = when (result) {
            UnlockResult.SUCCESS -> null
            UnlockResult.WRONG_KEY -> wrongKeyMessage
            UnlockResult.CORRUPTED -> "Kasa dosyası okunamadı ya da bozuk."
        }
    }

    fun unlockWithPassword() {
        if (working || password.isEmpty()) return
        val chars = password.toCharArray()
        password = ""
        working = true
        error = null
        scope.launch {
            try {
                report(vault.unlockWithPassword(chars), "Ana parola yanlış.")
            } catch (e: Exception) {
                error = "Kilit açılamadı. Cihazda yeterli boş bellek olmayabilir."
            } finally {
                working = false
            }
        }
    }

    fun unlockWithBiometrics() {
        val cipher = biometrics.decryptCipherOrNull()
        if (cipher == null) {
            biometricEnabled = false
            error = "Biyometrik kilit geçersiz oldu (parmak izi kayıtları değişmiş olabilir). " +
                "Ana parolayla açıp Ayarlar'dan yeniden etkinleştir."
            return
        }
        BiometricAuth.prompt(
            activity = activity,
            title = "Kasayı aç",
            negativeText = "Parola kullan",
            cipher = cipher,
            onSuccess = { authenticated ->
                val dek = try {
                    biometrics.unwrap(authenticated)
                } catch (e: GeneralSecurityException) {
                    null
                }
                if (dek == null) {
                    biometrics.disable()
                    biometricEnabled = false
                    error = "Biyometrik anahtar okunamadı. Ana parolayla aç."
                } else {
                    working = true
                    scope.launch {
                        try {
                            val result = vault.unlockWithDek(dek)
                            if (result == UnlockResult.WRONG_KEY) {
                                biometrics.disable()
                                biometricEnabled = false
                            }
                            report(result, "Biyometrik anahtar bu kasayla eşleşmiyor. Ana parolayla aç.")
                        } finally {
                            working = false
                        }
                    }
                }
            },
            onFailure = { message -> error = message },
        )
    }

    LaunchedEffect(Unit) {
        if (biometricEnabled) unlockWithBiometrics()
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
            Text("Şifre Kasa", style = MaterialTheme.typography.headlineMedium)
            Text("Kasa kilitli.", style = MaterialTheme.typography.bodyMedium)
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Ana parola",
                onImeAction = ::unlockWithPassword,
                enabled = !working,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::unlockWithPassword,
                enabled = !working && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Kilidi aç") }
            if (biometricEnabled) {
                OutlinedButton(
                    onClick = ::unlockWithBiometrics,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Biyometriyle aç") }
            }
            if (working) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
