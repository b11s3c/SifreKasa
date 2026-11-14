package dev.sifrekasa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktarıcıya tek bir şifreli ileti bırakır. Uygulamanın internete çıkan tek kodu budur:
 * yalnızca kullanıcı bir gönderimi onayladığında, yalnızca QR'daki (ve kayıtlı) aktarıcıya,
 * yalnızca [dev.sifrekasa.core.send.SendProtocol.seal] çıktısını yollar.
 */
object RelayClient {
    /** @return hata iletisi; başarılıysa null */
    suspend fun post(url: String, message: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            when (val code = connection.responseCode) {
                201 -> null
                409 -> "Bu QR zaten kullanılmış. Eklentide yeni bir QR oluştur."
                else -> "Aktarıcı isteği reddetti (HTTP $code)."
            }
        } catch (e: IOException) {
            "Aktarıcıya ulaşılamadı. İnternet bağlantını kontrol et."
        } catch (e: SecurityException) {
            "Ağ erişimi engellendi."
        } finally {
            connection?.disconnect()
        }
    }
}
