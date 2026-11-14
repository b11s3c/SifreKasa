package dev.sifrekasa.core.autofill

import java.net.URI

/**
 * Kayıtlı URL ile doldurma isteyen sitenin alan adını eşler.
 *
 * Kural bilerek dardır (oltalamaya karşı): alan adı ya kayıttakiyle aynıdır ya da onun bir alt alan
 * adıdır. "example.com" kaydı "login.example.com" ile eşleşir; "evil-example.com",
 * "example.com.evil.net" ya da kardeş alt alan adlarıyla eşleşmez. Baştaki "www." yok sayılır.
 */
object DomainMatcher {
    /** URL ya da çıplak alan adından küçük harfli ana makine adı; çıkarılamazsa null. */
    fun hostOf(urlOrHost: String): String? {
        val text = urlOrHost.trim()
        if (text.isEmpty()) return null
        val withScheme = if ("://" in text) text else "https://$text"
        val host = try {
            URI(withScheme).host
        } catch (e: Exception) {
            null
        } ?: return null
        return host.lowercase().trimEnd('.').removePrefix("www.").takeIf { it.isNotEmpty() }
    }

    fun matches(entryUrl: String, requestDomain: String): Boolean {
        val saved = hostOf(entryUrl) ?: return false
        val requested = hostOf(requestDomain) ?: return false
        // Tek etiketli (noktasız) kayıtlar, ör. "com", alt alan adı kuralıyla eşleşmez.
        return requested == saved || ('.' in saved && requested.endsWith(".$saved"))
    }
}
