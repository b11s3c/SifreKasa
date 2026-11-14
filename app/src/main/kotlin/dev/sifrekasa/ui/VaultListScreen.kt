package dev.sifrekasa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.totp.Totp
import java.util.Locale

private val turkish = Locale.forLanguageTag("tr")

/** Başlık, kullanıcı adı ve URL'de büyük/küçük harf duyarsız arama (Türkçe İ/ı kurallarıyla). Şifre ve notlarda aranmaz. */
private fun LoginEntry.matches(query: String): Boolean {
    val q = query.trim().lowercase(turkish)
    if (q.isEmpty()) return true
    return title.lowercase(turkish).contains(q) ||
        username.lowercase(turkish).contains(q) ||
        url.lowercase(turkish).contains(q)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    entries: List<LoginEntry>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    onCopyCode: (String) -> Unit,
    onLock: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Saat yalnızca listede 2FA kodu gösterilecekse işler.
    val now = if (entries.any { it.totp != null }) rememberEpochSeconds() else 0L
    val visible = remember(entries, query) {
        entries.filter { it.matches(query) }.sortedBy { it.title.lowercase(turkish) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasa") },
                actions = {
                    TextButton(onClick = onScan) { Text("QR tara") }
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, contentDescription = "Kilitle") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Kayıt ekle") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Aramayı temizle") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "Kasa boş. İlk kaydı eklemek için + düğmesine dokun." else "Eşleşen kayıt yok.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(entry.title.ifBlank { "(başlıksız)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val secondary = entry.username.ifBlank { entry.url }
                                if (secondary.isNotBlank()) Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = entry.totp?.let { totp ->
                                {
                                    val code = Totp.code(totp, now)
                                    TextButton(onClick = { onCopyCode(code) }) {
                                        Text(formatCode(code), fontFamily = FontFamily.Monospace)
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpen(entry.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
