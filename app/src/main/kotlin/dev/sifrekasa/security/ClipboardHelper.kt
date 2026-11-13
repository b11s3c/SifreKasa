package dev.sifrekasa.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import dev.sifrekasa.data.AppSettings

/**
 * Panoya kopyalama: içerik "hassas" işaretlenir (sistem önizlemesinde gizlenir) ve ayarlanan süre
 * sonunda silinir. Silme hem süreç içi zamanlayıcıyla (tam zamanında) hem de süreç öldürülürse
 * diye AlarmManager ile (yaklaşık zamanda) planlanır.
 */
class ClipboardHelper(private val context: Context, private val settings: AppSettings) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clearNow = Runnable { clear(context) }

    fun copySensitive(text: String) {
        val clip = ClipData.newPlainText("", text).apply {
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        clipboard.setPrimaryClip(clip)

        handler.removeCallbacks(clearNow)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, ClearClipboardReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        val seconds = settings.clipboardClearSeconds
        if (seconds <= 0) return
        handler.postDelayed(clearNow, seconds * 1000L)
        alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + seconds * 1000L, pending)
    }

    companion object {
        fun clear(context: Context) {
            runCatching { context.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }
        }
    }
}

class ClearClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ClipboardHelper.clear(context)
}
