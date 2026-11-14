package dev.sifrekasa

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.sifrekasa.ui.AppRoot
import dev.sifrekasa.ui.SifreKasaTheme

class MainActivity : FragmentActivity() {
    private val container get() = (application as SifreKasaApp).container

    /** Yalnızca hata ayıklama derlemesinde bağlantıyla gelen gönderme isteği (bkz. src/debug/AndroidManifest.xml). */
    private val debugSendUri = mutableStateOf<String?>(null)
    private val debuggable get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun readDebugLink(intent: Intent?) {
        if (debuggable && intent?.action == Intent.ACTION_VIEW) debugSendUri.value = intent.dataString
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDebugLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ekran görüntüsü, ekran kaydı ve "son uygulamalar" önizlemesini engelle.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readDebugLink(intent)
        setContent {
            SifreKasaTheme {
                AppRoot(
                    container = container,
                    activity = this,
                    allowInsecureLocalRelay = debuggable,
                    pendingSendUri = debugSendUri.value,
                    onSendUriConsumed = { debugSendUri.value = null },
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.lockManager.onUserInteraction()
    }
}
