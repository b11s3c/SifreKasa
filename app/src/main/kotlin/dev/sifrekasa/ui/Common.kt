package dev.sifrekasa.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.LocalTextStyle

/** Her saniye başında güncellenen Unix zamanı (saniye). */
@Composable
fun rememberEpochSeconds(): Long {
    val seconds by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now / 1000
            delay(1000 - now % 1000)
        }
    }
    return seconds
}

/** "123456" -> "123 456" */
fun formatCode(code: String): String = code.substring(0, code.length / 2) + " " + code.substring(code.length / 2)

/**
 * Parola alanı. Değer bilerek rememberSaveable ile SAKLANMAZ: kayıtlı durum paketine
 * (ve oradan diske) gizli veri yazılmasın.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    isError: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Gizle" else "Göster") }
        },
        modifier = modifier,
    )
}
