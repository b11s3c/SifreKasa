package dev.sifrekasa.security

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.sifrekasa.data.AppSettings
import dev.sifrekasa.data.VaultRepository
import dev.sifrekasa.data.VaultState

/**
 * Otomatik kilit: (1) uygulama arka plana geçince, (2) belirli süre işlem yapılmayınca.
 * Süre, cihaz uykudayken de ilerleyen elapsedRealtime ile ölçülür.
 */
class LockManager(
    private val vault: VaultRepository,
    private val settings: AppSettings,
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable { evaluate() }
    private var lastInteraction = SystemClock.elapsedRealtime()

    /** Kullanıcı ekrana dokundu ya da kasa az önce açıldı. */
    fun onUserInteraction() {
        lastInteraction = SystemClock.elapsedRealtime()
        evaluate()
    }

    override fun onStart(owner: LifecycleOwner) = evaluate()

    override fun onStop(owner: LifecycleOwner) {
        if (settings.lockOnBackground) vault.lock() else evaluate()
    }

    private fun evaluate() {
        handler.removeCallbacks(check)
        if (vault.state.value !is VaultState.Unlocked) return
        val minutes = settings.autoLockMinutes
        if (minutes <= 0) return
        val remaining = lastInteraction + minutes * 60_000L - SystemClock.elapsedRealtime()
        if (remaining <= 0) vault.lock() else handler.postDelayed(check, remaining)
    }
}
