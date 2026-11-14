package dev.sifrekasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sifrekasa.AppContainer
import dev.sifrekasa.core.send.SendException
import dev.sifrekasa.core.send.SendRequest
import dev.sifrekasa.core.totp.OtpAuth
import dev.sifrekasa.core.totp.OtpAuthEntry
import dev.sifrekasa.core.totp.OtpAuthException
import dev.sifrekasa.data.VaultState

private sealed interface Screen {
    data object List : Screen
    data class Edit(val id: String?, val prefill: OtpAuthEntry? = null) : Screen
    data object ScanNew : Screen
    data class Send(val request: SendRequest) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(
    container: AppContainer,
    activity: FragmentActivity,
    allowInsecureLocalRelay: Boolean = false,
    pendingSendUri: String? = null,
    onSendUriConsumed: () -> Unit = {},
) {
    val state by container.vault.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val unlocked = state is VaultState.Unlocked

    LaunchedEffect(unlocked) {
        if (unlocked) container.lockManager.onUserInteraction() else screen = Screen.List
    }

    // Hata ayıklama derlemesinde bağlantıyla gelen istek, kasa açılınca işlenir.
    LaunchedEffect(pendingSendUri, unlocked) {
        if (pendingSendUri != null && unlocked) {
            runCatching { SendRequest.parse(pendingSendUri, System.currentTimeMillis() / 1000, allowInsecureLocalRelay) }
                .onSuccess { screen = Screen.Send(it) }
            onSendUriConsumed()
        }
    }

    when (val current = state) {
        VaultState.NoVault -> SetupScreen(container.vault)
        VaultState.Locked -> UnlockScreen(container, activity)
        is VaultState.Unlocked -> when (val s = screen) {
            Screen.List -> VaultListScreen(
                entries = current.data.entries,
                onAdd = { screen = Screen.Edit(null) },
                onOpen = { screen = Screen.Edit(it) },
                onSettings = { screen = Screen.Settings },
                onScan = { screen = Screen.ScanNew },
                onCopyCode = { container.clipboard.copySensitive(it) },
                onLock = { container.vault.lock() },
            )
            is Screen.Edit -> EntryEditScreen(
                existing = current.data.entries.firstOrNull { it.id == s.id },
                prefill = s.prefill,
                vault = container.vault,
                clipboard = container.clipboard,
                onClose = { screen = Screen.List },
            )
            Screen.ScanNew -> {
                var scanError by remember { mutableStateOf<String?>(null) }
                QrScannerScreen(
                    title = "QR tara",
                    error = scanError,
                    onScanned = { text ->
                        // İki tür QR tanınır: tarayıcı eklentisinin gönderme isteği ve 2FA (otpauth) kodu.
                        try {
                            screen = if (SendRequest.isSendUri(text)) {
                                Screen.Send(SendRequest.parse(text, System.currentTimeMillis() / 1000, allowInsecureLocalRelay))
                            } else {
                                Screen.Edit(id = null, prefill = OtpAuth.parse(text))
                            }
                        } catch (e: SendException) {
                            scanError = e.message
                        } catch (e: OtpAuthException) {
                            scanError = "Tanınmayan QR: ${e.message}"
                        }
                    },
                    onCancel = { screen = Screen.List },
                )
            }
            is Screen.Send -> SendScreen(
                container = container,
                activity = activity,
                request = s.request,
                entries = current.data.entries,
                onClose = { screen = Screen.List },
            )
            Screen.Settings -> SettingsScreen(container, activity, onBack = { screen = Screen.List })
        }
    }
}
