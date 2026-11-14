package dev.sifrekasa.core.send

import dev.sifrekasa.core.autofill.DomainMatcher
import dev.sifrekasa.core.passkey.Base64Url
import java.net.URI
import java.net.URLDecoder

class SendException(message: String) : Exception(message)

/**
 * Tarayıcı eklentisinin QR ile gösterdiği tek kullanımlık istek:
 * `sifrekasa://send?v=1&r=<aktarıcı>&s=<oturum>&k=<açık anahtar>&d=<alan adı>&e=<son geçerlilik>`
 */
class SendRequest(
    /** Aktarıcının kök adresi, sonda eğik çizgi olmadan. */
    val relayUrl: String,
    /** 16 baytlık rastgele oturum kimliği. */
    val sessionId: ByteArray,
    /** Eklentinin bu istek için ürettiği X25519 açık anahtarı (32 bayt). */
    val extensionPublicKey: ByteArray,
    /** Formun bulunduğu sitenin alan adı (küçük harf, "www." olmadan). */
    val domain: String,
    /** İsteğin geçerliliğinin bittiği an (Unix saniye). */
    val expiresAt: Long,
) {
    val sessionIdText: String get() = Base64Url.encode(sessionId)

    fun isExpired(nowSeconds: Long): Boolean = nowSeconds >= expiresAt

    /** Şifreli yanıtın gönderileceği adres. */
    val postUrl: String get() = "$relayUrl/v1/s/$sessionIdText"

    companion object {
        const val SCHEME_PREFIX = "sifrekasa://send"
        /** Eklenti en çok bu kadar ileri tarihli istek üretebilir; daha uzunu reddedilir. */
        const val MAX_LIFETIME_SECONDS = 120L

        fun isSendUri(text: String): Boolean = text.trim().startsWith(SCHEME_PREFIX, ignoreCase = true)

        /**
         * @param allowInsecureLocal yalnızca geliştirme derlemelerinde: http://localhost ve emülatörün
         *        ana makine adresine (10.0.2.2) izin verir. Diğer tüm durumlarda https zorunludur.
         * @throws SendException istek geçersiz ya da süresi dolmuşsa
         */
        fun parse(text: String, nowSeconds: Long, allowInsecureLocal: Boolean = false): SendRequest {
            val uri = try {
                URI(text.trim())
            } catch (e: Exception) {
                throw SendException("Geçersiz QR")
            }
            if (!uri.scheme.equals("sifrekasa", true) || !uri.host.equals("send", true)) throw SendException("Şifre Kasa gönderme QR'ı değil")
            val params = HashMap<String, String>()
            uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.forEach { pair ->
                try {
                    params[pair.substringBefore('=')] = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                } catch (e: IllegalArgumentException) {
                    throw SendException("Geçersiz QR")
                }
            }
            if (params["v"] != "1") throw SendException("Desteklenmeyen sürüm; eklentiyi ya da uygulamayı güncelle")

            val sessionId = decode(params["s"], 16, "oturum kimliği")
            val publicKey = decode(params["k"], 32, "açık anahtar")
            val domain = params["d"]?.let(DomainMatcher::hostOf)?.takeIf { '.' in it || it == "localhost" }
                ?: throw SendException("Geçersiz alan adı")
            val expiresAt = params["e"]?.toLongOrNull() ?: throw SendException("Geçersiz süre")
            if (expiresAt <= nowSeconds) throw SendException("Bu QR'ın süresi dolmuş. Eklentide yeni bir QR oluştur.")
            if (expiresAt - nowSeconds > MAX_LIFETIME_SECONDS) throw SendException("Geçersiz süre")
            return SendRequest(relayUrl(params["r"], allowInsecureLocal), sessionId, publicKey, domain, expiresAt)
        }

        private fun decode(value: String?, length: Int, name: String): ByteArray {
            val bytes = try {
                Base64Url.decode(value.orEmpty())
            } catch (e: IllegalArgumentException) {
                throw SendException("Geçersiz $name")
            }
            if (bytes.size != length) throw SendException("Geçersiz $name")
            return bytes
        }

        /** Aktarıcı adresini doğrular ve normalleştirir: https, kullanıcı bilgisi/sorgu/parça yok. */
        fun relayUrl(value: String?, allowInsecureLocal: Boolean = false): String {
            val uri = try {
                URI(value.orEmpty().trim().trimEnd('/'))
            } catch (e: Exception) {
                throw SendException("Geçersiz aktarıcı adresi")
            }
            val host = uri.host?.lowercase() ?: throw SendException("Geçersiz aktarıcı adresi")
            val local = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            val schemeOk = uri.scheme.equals("https", true) || (allowInsecureLocal && local && uri.scheme.equals("http", true))
            if (!schemeOk) throw SendException("Aktarıcı adresi https olmalı")
            if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) throw SendException("Geçersiz aktarıcı adresi")
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            return "${uri.scheme.lowercase()}://$host$port${uri.rawPath.orEmpty().trimEnd('/')}"
        }
    }
}
