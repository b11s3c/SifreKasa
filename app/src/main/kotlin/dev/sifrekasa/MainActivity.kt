package dev.sifrekasa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.sifrekasa.ui.AppRoot
import dev.sifrekasa.ui.SifreKasaTheme

class MainActivity : FragmentActivity() {
    private val container get() = (application as SifreKasaApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ekran görüntüsü, ekran kaydı ve "son uygulamalar" önizlemesini engelle.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SifreKasaTheme {
                AppRoot(container = container, activity = this)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.lockManager.onUserInteraction()
    }
}
