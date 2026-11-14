package dev.sifrekasa.core.totp

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

class OtpAuthException(message: String) : Exception(message)

data class OtpAuthEntry(val config: TotpConfig, val issuer: String, val account: String) {
    override fun toString(): String = "OtpAuthEntry(issuer=$issuer)"
}

/** `otpauth://totp/Yayıncı:hesap?secret=…&issuer=…&algorithm=…&digits=…&period=…` ayrıştırıcısı. */
object OtpAuth {
    fun isOtpAuthUri(text: String): Boolean = text.trim().startsWith("otpauth://", ignoreCase = true)

    /** @throws OtpAuthException bağlantı geçersiz ya da desteklenmiyorsa. İleti gizli anahtarı içermez. */
    fun parse(text: String): OtpAuthEntry {
        val uri = try {
            URI(text.trim())
        } catch (e: URISyntaxException) {
            throw OtpAuthException("Geçersiz otpauth bağlantısı")
        }
        if (!uri.scheme.equals("otpauth", ignoreCase = true)) throw OtpAuthException("otpauth bağlantısı değil")
        when (uri.host?.lowercase()) {
            "totp" -> Unit
            "hotp" -> throw OtpAuthException("Sayaç tabanlı (HOTP) kodlar desteklenmiyor")
            else -> throw OtpAuthException("Desteklenmeyen kod türü")
        }

        val params = HashMap<String, String>()
        uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.forEach { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            try {
                params[URLDecoder.decode(key, "UTF-8").lowercase()] = URLDecoder.decode(value, "UTF-8")
            } catch (e: IllegalArgumentException) {
                throw OtpAuthException("Geçersiz otpauth bağlantısı")
            }
        }

        val label = uri.path.orEmpty().trimStart('/')
        val labelIssuer = if (':' in label) label.substringBefore(':').trim() else ""
        val account = (if (':' in label) label.substringAfter(':') else label).trim()
        val issuer = params["issuer"]?.trim().orEmpty().ifEmpty { labelIssuer }

        val secret = params["secret"]?.let(Base32::normalize)
        if (secret.isNullOrEmpty()) throw OtpAuthException("Bağlantıda gizli anahtar yok")
        val algorithm = when (params["algorithm"]?.uppercase()?.replace("-", "")) {
            null, "", "SHA1" -> TotpAlgorithm.SHA1
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> throw OtpAuthException("Desteklenmeyen algoritma")
        }
        val digits = params["digits"]?.toIntOrNull() ?: 6
        val period = params["period"]?.toIntOrNull() ?: 30
        val config = try {
            TotpConfig(secret, algorithm, digits, period)
        } catch (e: IllegalArgumentException) {
            throw OtpAuthException("Geçersiz TOTP parametreleri")
        }
        return OtpAuthEntry(config, issuer, account)
    }

    /** Elle girişte: ya tam otpauth bağlantısı ya da yalnızca Base32 gizli anahtar. */
    fun parseManual(text: String): OtpAuthEntry {
        if (isOtpAuthUri(text)) return parse(text)
        val config = try {
            TotpConfig(Base32.normalize(text))
        } catch (e: IllegalArgumentException) {
            throw OtpAuthException("Geçersiz gizli anahtar (Base32 olmalı)")
        }
        return OtpAuthEntry(config, issuer = "", account = "")
    }
}
