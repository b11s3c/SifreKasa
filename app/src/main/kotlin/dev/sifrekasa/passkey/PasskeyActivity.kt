package dev.sifrekasa.passkey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sifrekasa.SifreKasaApp
import dev.sifrekasa.core.passkey.CreationOptions
import dev.sifrekasa.core.passkey.PasskeyException
import dev.sifrekasa.core.passkey.WebAuthn
import dev.sifrekasa.data.VaultState
import dev.sifrekasa.security.BiometricAuth
import dev.sifrekasa.ui.SifreKasaTheme
import dev.sifrekasa.ui.UnlockScreen
import kotlinx.coroutines.launch

/**
 * Geçiş anahtarı işlemlerinin kullanıcıya göründüğü ekran. Üç iş yapar:
 *  - UNLOCK: kasa kilitliyken sistemin hesap seçicisinden gelir; kilit açılınca eşleşen anahtarlar listelenir.
 *  - CREATE: yeni geçiş anahtarı üretir ve kasaya kaydeder.
 *  - GET: seçilen geçiş anahtarıyla oturum açma imzasını üretir.
 *
 * İmza ve anahtar üretimi her zaman kullanıcı doğrulamasından sonra yapılır: ya kasa az önce
 * (parola/biyometriyle) açılmıştır ya da ayrıca biyometri / ekran kilidi istenir.
 */
class PasskeyActivity : FragmentActivity() {
    private val container get() = (application as SifreKasaApp).container
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val action = intent.action

        // İstek geçersizse arayüz göstermeden hata dön.
        val createRequest = if (action == PasskeyRequests.ACTION_CREATE) PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent) else null
        val createOptions: CreationOptions? = createRequest?.let { request ->
            val origin = PasskeyRequests.callerOrigin(this, request.callingAppInfo)
            val json = (request.callingRequest as? CreatePublicKeyCredentialRequest)?.requestJson
            if (origin == null || json == null) null else try {
                WebAuthn.parseCreationOptions(json, origin.origin)
            } catch (e: PasskeyException) {
                null
            }
        }
        if (action == PasskeyRequests.ACTION_CREATE && createOptions == null) {
            failCreate(CreateCredentialUnknownException("Geçersiz istek"))
            return
        }

        // Yerel bir uygulama (tarayıcı değil) geçiş anahtarı istiyorsa, alan adı ile uygulama arasındaki
        // bağı (Digital Asset Links) doğrulayamayız: ağ erişimimiz yok. İmzalanan veride uygulamanın imza
        // özeti köken olarak yer alır ve site bunu reddeder; yine de kullanıcıya kimin istediği gösterilir.
        val nativeCaller: String? = if (action == PasskeyRequests.ACTION_GET) {
            PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)?.callingAppInfo
                ?.let { PasskeyRequests.callerOrigin(this, it) }
                ?.takeIf { !it.privileged }?.packageName
        } else null

        setContent {
            SifreKasaTheme {
                val state by container.vault.state.collectAsStateWithLifecycle()
                var message by remember { mutableStateOf<String?>(null) }
                var nativeConfirmed by remember { mutableStateOf(nativeCaller == null) }
                when (state) {
                    VaultState.NoVault -> Info("Önce ŞifreKasa uygulamasını açıp kasanı oluştur.", onClose = ::cancel)
                    VaultState.Locked -> UnlockScreen(container, this)
                    is VaultState.Unlocked -> when (action) {
                        PasskeyRequests.ACTION_UNLOCK -> {
                            LaunchedEffect(Unit) { finishUnlock() }
                            Working()
                        }
                        PasskeyRequests.ACTION_GET -> if (!nativeConfirmed) {
                            NativeCallerConfirmation(nativeCaller.orEmpty(), onConfirm = { nativeConfirmed = true }, onCancel = ::cancel)
                        } else {
                            LaunchedEffect(Unit) { verifyThen(onDenied = { message = it ?: "Doğrulama iptal edildi." }) { finishGet() } }
                            if (message == null) Working() else Info(message!!, onClose = ::cancel)
                        }
                        PasskeyRequests.ACTION_CREATE -> CreateConfirmation(
                            options = createOptions!!,
                            message = message,
                            onConfirm = { verifyThen(onDenied = { message = it ?: "Doğrulama iptal edildi." }) { finishCreate() } },
                            onCancel = ::cancel,
                        )
                        else -> Info("Bilinmeyen istek.", onClose = ::cancel)
                    }
                }
            }
        }
    }

    /** Kasa bu işlem için az önce açıldıysa yeniden sormaz; aksi halde biyometri ya da ekran kilidi ister. */
    private fun verifyThen(onDenied: (String?) -> Unit, block: () -> Unit) {
        if (container.vault.unlockedWithin(RECENT_UNLOCK_MS)) {
            block()
        } else {
            BiometricAuth.verifyUser(this, "ŞifreKasa", "Geçiş anahtarını kullanmak için doğrula", onSuccess = block, onFailure = onDenied)
        }
    }

    private fun finishUnlock() {
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
        val data = (container.vault.state.value as? VaultState.Unlocked)?.data
        if (request == null || data == null) return cancel()
        val result = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(result, PasskeyRequests.buildGetResponse(this, data, request))
        // Kullanıcı birazdan listeden anahtar seçecek; bu arada kasa arka plan kilidiyle kapanmasın.
        container.lockManager.expectExternalActivity()
        finishWith(result)
    }

    private fun finishGet() {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val option = request?.credentialOptions?.filterIsInstance<GetPublicKeyCredentialOption>()?.firstOrNull()
        val origin = request?.let { PasskeyRequests.callerOrigin(this, it.callingAppInfo) }
        val credentialId = intent.getStringExtra(PasskeyRequests.EXTRA_CREDENTIAL_ID)
        val data = (container.vault.state.value as? VaultState.Unlocked)?.data
        val passkey = data?.entries?.mapNotNull { it.passkey }?.firstOrNull { it.credentialId == credentialId }
        if (option == null || origin == null) return failGet(GetCredentialUnknownException("Geçersiz istek"))
        if (passkey == null) return failGet(NoCredentialException("Geçiş anahtarı bulunamadı"))
        val json = try {
            WebAuthn.assert(
                passkey = passkey,
                optionsJson = option.requestJson,
                origin = origin.origin,
                clientDataHash = option.clientDataHash.takeIf { origin.privileged },
                androidPackageName = origin.packageName.takeIf { !origin.privileged },
            )
        } catch (e: PasskeyException) {
            return failGet(GetCredentialUnknownException("Geçersiz istek"))
        } catch (e: Exception) {
            return failGet(GetCredentialUnknownException("İmza üretilemedi"))
        }
        val result = Intent()
        PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(PublicKeyCredential(json)))
        finishWith(result)
    }

    private fun finishCreate() {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val publicKeyRequest = request?.callingRequest as? CreatePublicKeyCredentialRequest
        val origin = request?.let { PasskeyRequests.callerOrigin(this, it.callingAppInfo) }
        val data = (container.vault.state.value as? VaultState.Unlocked)?.data
        if (publicKeyRequest == null || origin == null || data == null) return failCreate(CreateCredentialUnknownException("Geçersiz istek"))
        val created = try {
            WebAuthn.create(
                optionsJson = publicKeyRequest.requestJson,
                origin = origin.origin,
                clientDataHash = publicKeyRequest.clientDataHash.takeIf { origin.privileged },
                androidPackageName = origin.packageName.takeIf { !origin.privileged },
                existing = data.entries.mapNotNull { it.passkey },
                now = System.currentTimeMillis(),
            )
        } catch (e: PasskeyException) {
            return failCreate(
                when (e.kind) {
                    PasskeyException.Kind.ALREADY_REGISTERED -> CreatePublicKeyCredentialDomException(InvalidStateError(), e.message)
                    PasskeyException.Kind.NOT_SUPPORTED -> CreatePublicKeyCredentialDomException(NotSupportedError(), e.message)
                    PasskeyException.Kind.INVALID_REQUEST -> CreateCredentialUnknownException("Geçersiz istek")
                }
            )
        } catch (e: Exception) {
            return failCreate(CreateCredentialUnknownException("Geçiş anahtarı üretilemedi"))
        }
        lifecycleScope.launch {
            // Yanıt siteye ancak anahtar kasaya (diske) yazıldıktan sonra verilir.
            val saved = try {
                container.vault.savePasskey(created.passkey)
            } catch (e: Exception) {
                false
            }
            if (!saved) return@launch failCreate(CreateCredentialUnknownException("Geçiş anahtarı kaydedilemedi"))
            val result = Intent()
            PendingIntentHandler.setCreateCredentialResponse(result, CreatePublicKeyCredentialResponse(created.responseJson))
            finishWith(result)
        }
    }

    private fun cancel() {
        when (intent.action) {
            PasskeyRequests.ACTION_CREATE -> failCreate(CreateCredentialCancellationException("İptal edildi"))
            PasskeyRequests.ACTION_GET -> failGet(GetCredentialCancellationException("İptal edildi"))
            else -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun failCreate(exception: CreateCredentialException) {
        val result = Intent()
        PendingIntentHandler.setCreateCredentialException(result, exception)
        finishWith(result)
    }

    private fun failGet(exception: GetCredentialException) {
        val result = Intent()
        PendingIntentHandler.setGetCredentialException(result, exception)
        finishWith(result)
    }

    private fun finishWith(result: Intent) {
        if (done) return
        done = true
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private companion object {
        /** Kilit açma ile işlem arasında bu kadar süre geçtiyse kullanıcı yeniden doğrulanır. */
        const val RECENT_UNLOCK_MS = 60_000L
    }
}

@Composable
private fun Working() {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) { CircularProgressIndicator() }
    }
}

@Composable
private fun Info(text: String, onClose: () -> Unit) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text)
            OutlinedButton(onClick = onClose) { Text("Kapat") }
        }
    }
}

@Composable
private fun CreateConfirmation(options: CreationOptions, message: String?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Geçiş anahtarı oluştur", style = MaterialTheme.typography.headlineSmall)
            Text("Site: ${options.rpId}", style = MaterialTheme.typography.titleMedium)
            if (options.rpName.isNotBlank()) Text(options.rpName)
            Text("Hesap: ${options.userName.ifBlank { options.userDisplayName }}")
            Text(
                "Anahtar ŞifreKasa'da saklanır ve şifreli yedeğine dahil olur.",
                style = MaterialTheme.typography.bodySmall,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm) { Text("Oluştur") }
                OutlinedButton(onClick = onCancel) { Text("Vazgeç") }
            }
        }
    }
}

@Composable
private fun NativeCallerConfirmation(packageName: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Geçiş anahtarıyla oturum aç", style = MaterialTheme.typography.headlineSmall)
            Text("İsteyen uygulama: $packageName", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bu istek bir tarayıcıdan değil, bir uygulamadan geliyor. Yalnızca bu uygulamaya güveniyorsan devam et.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm) { Text("Devam") }
                OutlinedButton(onClick = onCancel) { Text("Vazgeç") }
            }
        }
    }
}
