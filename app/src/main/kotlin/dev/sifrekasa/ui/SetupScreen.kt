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
import dev.sifrekasa.data.VaultRepository
import kotlinx.coroutines.launch

private const val MIN_MASTER_PASSWORD_LENGTH = 10

@Composable
fun SetupScreen(vault: VaultRepository) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
                vault.create(chars)
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
            if (working) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Anahtar türetiliyor…", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
