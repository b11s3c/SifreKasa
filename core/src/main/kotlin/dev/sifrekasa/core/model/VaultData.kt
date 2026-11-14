package dev.sifrekasa.core.model

import dev.sifrekasa.core.totp.TotpConfig
import kotlinx.serialization.Serializable

@Serializable
data class LoginEntry(
    val id: String,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Bu kayda bağlı 2FA (TOTP) ayarı; yoksa null. */
    val totp: TotpConfig? = null,
) {
    // Yanlışlıkla loglanırsa gizli alanlar görünmesin.
    override fun toString(): String = "LoginEntry(id=$id)"
}

/** Kasanın şifresi çözülmüş içeriği. Yeni alanlar varsayılan değerle eklenir; eski dosyalar okunmaya devam eder. */
@Serializable
data class VaultData(
    val schema: Int = 1,
    val entries: List<LoginEntry> = emptyList(),
    /** Otomatik/elle yedek için saklanan anahtar; yedekleme kurulmadıysa null. Dışa aktarılan dosyaya girmez. */
    val backup: BackupConfig? = null,
) {
    override fun toString(): String = "VaultData(entries=${entries.size})"
}

/**
 * Yedek parolasından Argon2id ile türetilmiş anahtar ve türetme parametreleri.
 * Parolanın kendisi saklanmaz; anahtar kasanın içinde (şifreli) durur ki kasa açıkken
 * parola sormadan yedek yazılabilsin. Bayt dizileri Base64'tür.
 */
@Serializable
data class BackupConfig(
    val opsLimit: Int,
    val memKiB: Int,
    val salt: String,
    val key: String,
) {
    override fun toString(): String = "BackupConfig()"
}
