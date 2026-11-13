package dev.sifrekasa

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import dev.sifrekasa.core.crypto.CryptoEngine
import dev.sifrekasa.core.crypto.SodiumCryptoEngine
import dev.sifrekasa.data.AppSettings
import dev.sifrekasa.data.VaultRepository
import dev.sifrekasa.security.BiometricKeyStore
import dev.sifrekasa.security.ClipboardHelper
import dev.sifrekasa.security.LockManager
import java.io.File

/** Elle bağımlılık kurulumu; uygulama tek süreçte tek örnek kullanır. */
class AppContainer(app: Application) {
    val engine: CryptoEngine = SodiumCryptoEngine(LazySodiumAndroid(SodiumAndroid()))
    val settings = AppSettings(app)
    val vault = VaultRepository(File(app.filesDir, "vault.skv"), engine)
    val biometricKeyStore = BiometricKeyStore(app)
    val clipboard = ClipboardHelper(app, settings)
    val lockManager = LockManager(vault, settings)
}

class SifreKasaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lockManager)
    }
}
