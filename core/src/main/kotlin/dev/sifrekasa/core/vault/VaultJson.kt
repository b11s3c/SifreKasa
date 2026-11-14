package dev.sifrekasa.core.vault

import kotlinx.serialization.json.Json

internal object VaultJson {
    /** Bilinmeyen alanlar yok sayılır: yeni sürümün yazdığı dosyayı eski sürüm de okuyabilir. */
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
