package dev.sifrekasa.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PublicKeyCredentialEntry
import dev.sifrekasa.core.model.VaultData
import dev.sifrekasa.core.passkey.Base64Url
import dev.sifrekasa.core.passkey.PasskeyCrypto
import dev.sifrekasa.core.passkey.PasskeyException
import dev.sifrekasa.core.passkey.WebAuthn
import dev.sifrekasa.security.PrivilegedApps

/** İsteği yapan tarafın WebAuthn kökeni. */
class CallerOrigin(
    val origin: String,
    /** true: tarayıcı ya da Play Services başka bir site adına istiyor (clientDataHash hazır gelir). */
    val privileged: Boolean,
    val packageName: String,
)

object PasskeyRequests {
    const val ACTION_CREATE = "dev.sifrekasa.passkey.CREATE"
    const val ACTION_GET = "dev.sifrekasa.passkey.GET"
    const val ACTION_UNLOCK = "dev.sifrekasa.passkey.UNLOCK"
    const val EXTRA_CREDENTIAL_ID = "credential_id"

    /**
     * Ayrıcalıklı çağıranlarda (listede olan ve imzası tutan tarayıcılar, Play Services) sistemin
     * ilettiği web kökeni; yerel uygulamalarda `android:apk-key-hash:…`.
     * @return null: çağıran bir web kökeni ileri sürüyor ama güvenilenler listesinde değil.
     */
    fun callerOrigin(context: Context, info: CallingAppInfo): CallerOrigin? {
        val webOrigin = try {
            info.getOrigin(PrivilegedApps.allowlistJson(context))
        } catch (e: IllegalStateException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (webOrigin != null) return CallerOrigin(webOrigin, privileged = true, packageName = info.packageName)
        val certificate = info.signingInfoCompat.signingCertificateHistory.firstOrNull()?.toByteArray() ?: return null
        val hash = Base64Url.encode(PasskeyCrypto.sha256(certificate))
        return CallerOrigin("android:apk-key-hash:$hash", privileged = false, packageName = info.packageName)
    }

    /** Sistemin gösterdiği hesap seçicisine konacak, isteğe uyan geçiş anahtarları. Gizli veri içermez. */
    fun buildGetResponse(context: Context, data: VaultData, request: BeginGetCredentialRequest): BeginGetCredentialResponse {
        val origin = request.callingAppInfo?.let { callerOrigin(context, it) }
        val passkeys = data.entries.mapNotNull { it.passkey }
        val entries = ArrayList<PublicKeyCredentialEntry>()
        for (option in request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>()) {
            val options = try {
                WebAuthn.parseRequestOptions(option.requestJson, origin?.origin)
            } catch (e: PasskeyException) {
                continue
            }
            for (passkey in WebAuthn.matching(passkeys, options)) {
                val name = passkey.userName.ifBlank { passkey.userDisplayName }.ifBlank { passkey.rpId }
                val entry = PublicKeyCredentialEntry.Builder(
                    context, name, pendingIntent(context, ACTION_GET, passkey.credentialId), option,
                )
                if (passkey.userDisplayName.isNotBlank()) entry.setDisplayName(passkey.userDisplayName)
                entries.add(entry.build())
            }
        }
        return BeginGetCredentialResponse(credentialEntries = entries)
    }

    /** Sistem istek ayrıntılarını bu PendingIntent'e eklediği için değiştirilebilir; hedef açıkça belirtilir. */
    fun pendingIntent(context: Context, action: String, credentialId: String? = null): PendingIntent {
        val intent = Intent(context, PasskeyActivity::class.java).setAction(action)
        credentialId?.let { intent.putExtra(EXTRA_CREDENTIAL_ID, it) }
        val requestCode = (action + credentialId.orEmpty()).hashCode()
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
