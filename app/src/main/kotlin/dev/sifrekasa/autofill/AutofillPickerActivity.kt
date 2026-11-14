package dev.sifrekasa.autofill

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.Presentations
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sifrekasa.R
import dev.sifrekasa.SifreKasaApp
import dev.sifrekasa.core.autofill.DomainMatcher
import dev.sifrekasa.core.model.LoginEntry
import dev.sifrekasa.core.totp.Totp
import dev.sifrekasa.data.VaultState
import dev.sifrekasa.ui.SifreKasaTheme
import dev.sifrekasa.ui.UnlockScreen
import java.util.Locale

/** Otomatik doldurma için kilit açma ve kayıt seçimi. Seçilen kaydın değerleri yalnızca burada forma verilir. */
class AutofillPickerActivity : FragmentActivity() {
    private val container get() = (application as SifreKasaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val callerPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val usernameId = intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        val passwordIds = intent.getParcelableArrayListExtra(EXTRA_PASSWORD_IDS, AutofillId::class.java).orEmpty()
        val callerLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(callerPackage, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            callerPackage
        }

        setContent {
            SifreKasaTheme {
                val state by container.vault.state.collectAsStateWithLifecycle()
                when (val current = state) {
                    VaultState.NoVault -> Message("Önce Şifre Kasa uygulamasını açıp kasanı oluştur.")
                    VaultState.Locked -> UnlockScreen(container, this)
                    is VaultState.Unlocked -> Picker(
                        entries = current.data.entries,
                        domain = domain,
                        callerLabel = callerLabel,
                        callerPackage = callerPackage,
                        onPick = { fill(it, usernameId, passwordIds) },
                        onCancel = { cancel() },
                    )
                }
            }
        }
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun fill(entry: LoginEntry, usernameId: AutofillId?, passwordIds: List<AutofillId>) {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_text, entry.title.ifBlank { entry.username })
        }
        val dataset = Dataset.Builder(Presentations.Builder().setMenuPresentation(presentation).build())
        var any = false
        if (usernameId != null && entry.username.isNotEmpty()) {
            dataset.setField(usernameId, Field.Builder().setValue(AutofillValue.forText(entry.username)).build())
            any = true
        }
        if (entry.password.isNotEmpty()) {
            passwordIds.forEach { id ->
                dataset.setField(id, Field.Builder().setValue(AutofillValue.forText(entry.password)).build())
                any = true
            }
        }
        if (!any) {
            cancel()
            return
        }
        // Kayıtta 2FA varsa güncel kod panoya alınır: giriş ekranının bir sonraki adımında yapıştırılabilir.
        entry.totp?.let { container.clipboard.copySensitive(Totp.code(it, System.currentTimeMillis() / 1000)) }
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset.build()))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_USERNAME_ID = "username_id"
        const val EXTRA_PASSWORD_IDS = "password_ids"
    }
}

@Composable
private fun Message(text: String) {
    Scaffold { padding -> Text(text, modifier = Modifier.padding(padding).padding(24.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    entries: List<LoginEntry>,
    domain: String?,
    callerLabel: String,
    callerPackage: String,
    onPick: (LoginEntry) -> Unit,
    onCancel: () -> Unit,
) {
    val turkish = remember { Locale.forLanguageTag("tr") }
    val fillable = remember(entries) { entries.filter { it.username.isNotEmpty() || it.password.isNotEmpty() } }
    val matches = remember(fillable, domain) {
        if (domain == null) emptyList() else fillable.filter { DomainMatcher.matches(it.url, domain) }
    }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(matches.isEmpty()) }
    val visible = if (!showAll) matches else fillable.filter {
        val q = query.trim().lowercase(turkish)
        q.isEmpty() || it.title.lowercase(turkish).contains(q) || it.username.lowercase(turkish).contains(q) || it.url.lowercase(turkish).contains(q)
    }.sortedBy { it.title.lowercase(turkish) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Şifre Kasa ile doldur") },
                actions = { TextButton(onClick = onCancel) { Text("Vazgeç") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (domain != null) {
                    Text("Site: $domain", style = MaterialTheme.typography.titleMedium)
                    Text("Tarayıcı: $callerLabel", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Uygulama: $callerLabel", style = MaterialTheme.typography.titleMedium)
                    Text(callerPackage, style = MaterialTheme.typography.bodySmall)
                }
                if (showAll) {
                    Text(
                        if (domain != null && matches.isEmpty()) {
                            "Bu siteyle eşleşen kayıt yok. Başka bir kaydı doldurmadan önce adresin doğru site olduğundan emin ol."
                        } else if (domain == null) {
                            "Uygulamalar için otomatik eşleştirme yapılmaz. Doldurmadan önce doğru uygulama olduğundan emin ol."
                        } else {
                            "Tüm kayıtlar gösteriliyor. Adresin doğru site olduğundan emin ol."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Ara") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title.ifBlank { "(başlıksız)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(listOf(entry.username, entry.url).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.clickable { onPick(entry) },
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
