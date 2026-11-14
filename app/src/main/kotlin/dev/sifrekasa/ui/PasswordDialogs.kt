package dev.sifrekasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

const val MIN_EXPORT_PASSWORD_LENGTH = 10

/** Yeni bir parola belirletir (iki kez). [onConfirm]'a verilen diziyi alan taraf siler. */
@Composable
fun NewPasswordDialog(title: String, description: String, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_EXPORT_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password
    val valid = password.length >= MIN_EXPORT_PASSWORD_LENGTH && confirm == password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Parola",
                    imeAction = ImeAction.Next,
                    isError = tooShort,
                    supportingText = "En az $MIN_EXPORT_PASSWORD_LENGTH karakter",
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Parola (tekrar)",
                    isError = mismatch,
                    supportingText = if (mismatch) "Parolalar eşleşmiyor" else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password.toCharArray()) }) { Text("Devam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Var olan bir parolayı sorar. [onConfirm]'a verilen diziyi alan taraf siler. */
@Composable
fun AskPasswordDialog(title: String, description: String, onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Parola",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password.toCharArray()) }) { Text("Aç") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
