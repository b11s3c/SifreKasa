package dev.sifrekasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.sifrekasa.AppContainer
import dev.sifrekasa.core.autofill.DomainMatcher
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.send.SendException
import dev.sifrekasa.core.send.SendProtocol
import dev.sifrekasa.core.send.SendRequest
import dev.sifrekasa.core.totp.Totp
import dev.sifrekasa.data.RelayClient
import dev.sifrekasa.security.BiometricAuth
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

/**
 * Bilgisayardaki tarayıcı eklentisine şifre gönderme. Sıra: alan adını göster → eşleşen kaydı seç →
 * biyometri / ekran kilidiyle onayla → uçtan uca şifrele → aktarıcıya bırak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    container: AppContainer,
    activity: FragmentActivity,
    request: SendRequest,
    entries: List<LoginEntry>,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val turkish = remember { Locale.forLanguageTag("tr") }
    val now = rememberEpochSeconds()
    val remaining = (request.expiresAt - now).coerceAtLeast(0)
    var savedRelay by remember { mutableStateOf(container.settings.relayUrl) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val sendable = remember(entries) { entries.filter { it.username.isNotEmpty() || it.password.isNotEmpty() } }
    val matches = remember(sendable) { sendable.filter { DomainMatcher.matches(it.url, request.domain) } }
    var showAll by remember { mutableStateOf(false) }
    val relayHost = remember { runCatching { URI(request.relayUrl).host }.getOrNull() ?: request.relayUrl }

    fun send(entry: LoginEntry) {
        if (sending || sent) return
        error = null
        BiometricAuth.verifyUser(
            activity = activity,
            title = "${request.domain} için gönder",
            subtitle = entry.title.ifBlank { entry.username },
            onFailure = { message -> error = message ?: "Onay verilmedi; hiçbir şey gönderilmedi." },
            onSuccess = {
                if (request.isExpired(System.currentTimeMillis() / 1000)) {
                    error = "QR'ın süresi doldu. Eklentide yeni bir QR oluştur."
                    return@verifyUser
                }
                sending = true
                scope.launch {
                    val failure = try {
                        val code = entry.totp?.let { Totp.code(it, System.currentTimeMillis() / 1000) }
                        RelayClient.post(request.postUrl, SendProtocol.seal(request, entry.username, entry.password, code))
                    } catch (e: SendException) {
                        e.message
                    }
                    sending = false
                    if (failure == null) sent = true else error = failure
                }
            },
        )
    }

    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bilgisayara gönder") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Site: ${request.domain}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Bu adres, bilgisayarındaki tarayıcının adres çubuğundakiyle aynı olmalı. Emin değilsen gönderme.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Aktarıcı: $relayHost", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            when {
                sent -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Gönderildi. Tarayıcıdaki form birkaç saniye içinde dolacak.", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = onClose) { Text("Tamam") }
                }
                sending -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                savedRelay != null && savedRelay != request.relayUrl -> Column(
                    modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Bu QR, kayıtlı aktarıcından farklı bir aktarıcıyı gösteriyor. QR senin eklentinden gelmiyor olabilir; gönderim yapılmadı.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text("Kayıtlı: $savedRelay\nQR'daki: ${request.relayUrl}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Aktarıcını gerçekten değiştirdiysen Ayarlar'dan \"Aktarıcıyı unut\" seçip yeniden dene.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onClose) { Text("Kapat") }
                }
                savedRelay == null -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bu aktarıcı ilk kez kullanılıyor:", style = MaterialTheme.typography.titleSmall)
                    Text(request.relayUrl)
                    Text(
                        "Kendi kurduğun aktarıcının adresiyle aynıysa kaydet. Bundan sonra yalnızca bu aktarıcıyı gösteren QR'lar kabul edilir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            container.settings.relayUrl = request.relayUrl
                            savedRelay = request.relayUrl
                        }) { Text("Kaydet ve devam et") }
                        OutlinedButton(onClick = onClose) { Text("Vazgeç") }
                    }
                }
                remaining == 0L -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("QR'ın süresi doldu. Eklentide yeni bir QR oluştur.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onClose) { Text("Kapat") }
                }
                else -> {
                    val listed = if (!showAll) matches else sendable.filter {
                        val q = query.trim().lowercase(turkish)
                        q.isEmpty() || it.title.lowercase(turkish).contains(q) || it.username.lowercase(turkish).contains(q) || it.url.lowercase(turkish).contains(q)
                    }.sortedBy { it.title.lowercase(turkish) }
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Kalan süre: $remaining sn", style = MaterialTheme.typography.bodySmall)
                        if (showAll || matches.isEmpty()) {
                            Text(
                                if (matches.isEmpty()) "Bu siteyle eşleşen kayıt yok." else "Tüm kayıtlar gösteriliyor.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (showAll) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Ara") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            Text("Göndermek için kayda dokun:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(listed, key = { it.id }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.title.ifBlank { "(başlıksız)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOf(entry.username, entry.url, if (entry.totp != null) "2FA" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.clickable { send(entry) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (!showAll) {
                        TextButton(onClick = { showAll = true }, modifier = Modifier.padding(8.dp)) { Text("Başka bir kayıt ara") }
                    }
                }
            }
        }
    }
}
