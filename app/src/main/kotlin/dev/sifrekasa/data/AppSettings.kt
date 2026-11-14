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

    /** Yedek dosyasının SAF adresi (content://…); sır değildir. */
    var backupUri: String?
        get() = prefs.getString("backup_uri", null)
        set(value) = prefs.edit().putString("backup_uri", value).apply()

    /** Kasa her değiştiğinde yedeği güncelle. */
    var autoBackup: Boolean
        get() = prefs.getBoolean("auto_backup", true)
        set(value) = prefs.edit().putBoolean("auto_backup", value).apply()

    var lastBackupAt: Long
        get() = prefs.getLong("last_backup_at", 0)
        set(value) = prefs.edit().putLong("last_backup_at", value).apply()

    var lastBackupError: String?
        get() = prefs.getString("last_backup_error", null)
        set(value) = prefs.edit().putString("last_backup_error", value).apply()

    /** Panoya kopyalanan değeri şu kadar saniye sonra sil; 0 = kapalı. */
    var clipboardClearSeconds: Int
        get() = prefs.getInt("clipboard_clear_seconds", 30)
        set(value) = prefs.edit().putInt("clipboard_clear_seconds", value).apply()
}
