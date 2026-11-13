package dev.sifrekasa.data

import android.content.Context

/** Gizli olmayan tercihler. Sır içeren hiçbir şey buraya yazılmaz. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Uygulama arka plana geçince hemen kilitle. */
    var lockOnBackground: Boolean
        get() = prefs.getBoolean("lock_on_background", true)
        set(value) = prefs.edit().putBoolean("lock_on_background", value).apply()

    /** Şu kadar dakika işlem yapılmazsa kilitle; 0 = kapalı. */
    var autoLockMinutes: Int
        get() = prefs.getInt("auto_lock_minutes", 5)
        set(value) = prefs.edit().putInt("auto_lock_minutes", value).apply()

    /** Panoya kopyalanan değeri şu kadar saniye sonra sil; 0 = kapalı. */
    var clipboardClearSeconds: Int
        get() = prefs.getInt("clipboard_clear_seconds", 30)
        set(value) = prefs.edit().putInt("clipboard_clear_seconds", value).apply()
}
