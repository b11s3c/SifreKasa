package dev.sifrekasa.passkey

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import dev.sifrekasa.R
import dev.sifrekasa.SifreKasaApp
import dev.sifrekasa.data.VaultState

/**
 * Credential Manager geçiş anahtarı sağlayıcısı (ES256).
 *
 * Bilgisayardaki tarayıcının gösterdiği QR ile oturum açmada (hybrid akış) taşıma katmanını Android
 * yönetir; bu servise sıradan bir istek olarak, çağıran Play Services olacak şekilde ulaşır.
 *
 * Servis hiçbir zaman imza üretmez ve özel anahtara dokunmaz; yalnızca seçenekleri listeler.
 * Asıl işlem kullanıcı doğrulamasından sonra [PasskeyActivity] içinde yapılır.
 */
class SifreKasaCredentialService : CredentialProviderService() {
    private val vault get() = (application as SifreKasaApp).container.vault

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest || vault.state.value == VaultState.NoVault) {
            callback.onError(CreateCredentialUnknownException("Desteklenmeyen istek"))
            return
        }
        val entry = CreateEntry.Builder(
            getString(R.string.app_name),
            PasskeyRequests.pendingIntent(this, PasskeyRequests.ACTION_CREATE),
        ).build()
        callback.onResult(BeginCreateCredentialResponse(createEntries = listOf(entry)))
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val wantsPasskey = request.beginGetCredentialOptions.any { it is BeginGetPublicKeyCredentialOption }
        val state = vault.state.value
        val response = when {
            !wantsPasskey || state == VaultState.NoVault -> BeginGetCredentialResponse()
            // Kilitliyken hangi sitelerde geçiş anahtarı olduğu bilinemez: kilit açma seçeneği sunulur.
            state is VaultState.Unlocked -> PasskeyRequests.buildGetResponse(this, state.data, request)
            else -> BeginGetCredentialResponse(
                authenticationActions = listOf(
                    AuthenticationAction(
                        getString(R.string.passkey_unlock),
                        PasskeyRequests.pendingIntent(this, PasskeyRequests.ACTION_UNLOCK),
                    )
                )
            )
        }
        callback.onResult(response)
    }

    /** Oturum kapatma bildirimi: saklanan bir oturum durumu olmadığı için yapılacak iş yok. */
    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) = callback.onResult(null)
}
