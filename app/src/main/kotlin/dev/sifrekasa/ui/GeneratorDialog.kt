package dev.sifrekasa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.sifrekasa.core.generator.GeneratorOptions
import dev.sifrekasa.core.generator.PasswordGenerator
import kotlin.math.roundToInt

@Composable
fun GeneratorDialog(onUse: (String) -> Unit, onDismiss: () -> Unit) {
    val generator = remember { PasswordGenerator() }
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var password by remember { mutableStateOf(generator.generate(options)) }

    fun update(changed: GeneratorOptions) {
        // Son açık sınıfın kapatılmasına izin verme.
        if (!changed.anyClassSelected) return
        options = changed
        password = generator.generate(changed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Şifre üret") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        password,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { password = generator.generate(options) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yeniden üret")
                    }
                }
                Text("Uzunluk: ${options.length}")
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { update(options.copy(length = it.roundToInt())) },
                    valueRange = PasswordGenerator.MIN_LENGTH.toFloat()..64f,
                )
                OptionRow("Büyük harf (A-Z)", options.upper) { update(options.copy(upper = it)) }
                OptionRow("Küçük harf (a-z)", options.lower) { update(options.copy(lower = it)) }
                OptionRow("Rakam (0-9)", options.digits) { update(options.copy(digits = it)) }
                OptionRow("Simge (!@#…)", options.symbols) { update(options.copy(symbols = it)) }
                OptionRow("Karışan karakterleri çıkar (0 O 1 l I)", options.avoidAmbiguous) {
                    update(options.copy(avoidAmbiguous = it))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(password) }) { Text("Kullan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
