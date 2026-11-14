package dev.sifrekasa.core

import dev.sifrekasa.core.passkey.Base64Url
import dev.sifrekasa.core.send.SendException
import dev.sifrekasa.core.send.SendProtocol
import dev.sifrekasa.core.send.SendRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SendProtocolTest {
    private val now = 1_800_000_000L
    private val sid = ByteArray(16) { (it * 5 + 1).toByte() }

    /** Eklenti tarafını taklit eder: anahtar çifti JDK'nın kendi X25519 uygulamasından (Tink değil). */
    private val extension: KeyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
    private val extensionPublic: ByteArray = extension.public.encoded.takeLast(32).toByteArray()

    private fun uri(
        relay: String = "https://relay.example.workers.dev",
        domain: String = "example.com",
        expires: Long = now + 60,
        key: ByteArray = extensionPublic,
        session: ByteArray = sid,
        version: String = "1",
    ) = "sifrekasa://send?v=$version&r=${java.net.URLEncoder.encode(relay, "UTF-8")}&s=${Base64Url.encode(session)}" +
        "&k=${Base64Url.encode(key)}&d=$domain&e=$expires"

    /** Eklentinin yapacağı çözme; Tink yerine JDK (X25519, AES-GCM) ve Bouncy Castle (HKDF) ile. */
    private fun decrypt(message: String, domain: String, session: ByteArray = sid): String {
        val json = Json.parseToJsonElement(message).jsonObject
        assertEquals("1", json.getValue("v").jsonPrimitive.content)
        val phonePublic = Base64Url.decode(json.getValue("k").jsonPrimitive.content)
        val box = Base64Url.decode(json.getValue("c").jsonPrimitive.content)
        val x509Prefix = extension.public.encoded.copyOfRange(0, extension.public.encoded.size - 32)
        val peer = KeyFactory.getInstance("X25519").generatePublic(X509EncodedKeySpec(x509Prefix + phonePublic))
        val shared = KeyAgreement.getInstance("X25519").run { init(extension.private); doPhase(peer, true); generateSecret() }
        val key = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(shared, session, "SifreKasa/send/v1".toByteArray() + extensionPublic + phonePublic))
        }.generateBytes(key, 0, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 0, 12))
        cipher.updateAAD("SifreKasa/send/v1|$domain".toByteArray())
        return String(cipher.doFinal(box, 12, box.size - 12), Charsets.UTF_8)
    }

    @Test
    fun independentImplementationDecryptsMessage() {
        val request = SendRequest.parse(uri(), now)
        val message = SendProtocol.seal(request, "ali@example.com", "p@ss \"ğüş\" 🔑", "123456")
        val plain = Json.parseToJsonElement(decrypt(message, "example.com")).jsonObject
        assertEquals("example.com", plain.getValue("d").jsonPrimitive.content)
        assertEquals("ali@example.com", plain.getValue("u").jsonPrimitive.content)
        assertEquals("p@ss \"ğüş\" 🔑", plain.getValue("p").jsonPrimitive.content)
        assertEquals("123456", plain.getValue("t").jsonPrimitive.content)
        assertFalse(message.contains("ali@example.com"))

        val withoutTotp = Json.parseToJsonElement(decrypt(SendProtocol.seal(request, "u", "p", null), "example.com")).jsonObject
        assertEquals(JsonNull, withoutTotp.getValue("t"))
    }

    @Test
    fun messageIsBoundToDomainSessionAndKeys() {
        val request = SendRequest.parse(uri(), now)
        val message = SendProtocol.seal(request, "u", "p", null)
        assertThrows(Exception::class.java) { decrypt(message, "evil.example") }
        assertThrows(Exception::class.java) { decrypt(message, "example.com", ByteArray(16)) }
        // Her gönderimde yeni telefon anahtarı ve nonce.
        val again = SendProtocol.seal(request, "u", "p", null)
        assertFalse(message == again)
        // Aktarıcı iletiyi değiştirirse çözme başarısız olmalı.
        val json = Json.parseToJsonElement(message).jsonObject
        val box = Base64Url.decode(json.getValue("c").jsonPrimitive.content).also { it[20] = (it[20].toInt() xor 1).toByte() }
        val tampered = """{"v":1,"k":"${json.getValue("k").jsonPrimitive.content}","c":"${Base64Url.encode(box)}"}"""
        assertThrows(Exception::class.java) { decrypt(tampered, "example.com") }
    }

    @Test
    fun lowOrderPublicKeyIsRejected() {
        val request = SendRequest.parse(uri(key = ByteArray(32)), now)
        assertThrows(SendException::class.java) { SendProtocol.seal(request, "u", "p", null) }
    }

    @Test
    fun parsesAndNormalizesRequest() {
        val request = SendRequest.parse(uri(relay = "https://Relay.Example.workers.dev/", domain = "WWW.Example.com"), now)
        assertEquals("https://relay.example.workers.dev", request.relayUrl)
        assertEquals("example.com", request.domain)
        assertEquals("https://relay.example.workers.dev/v1/s/${Base64Url.encode(sid)}", request.postUrl)
        assertFalse(request.isExpired(now + 59))
        assertTrue(request.isExpired(now + 60))
        assertTrue(SendRequest.isSendUri(uri()))
        assertFalse(SendRequest.isSendUri("otpauth://totp/x?secret=A"))
    }

    @Test
    fun rejectsInvalidExpiredAndInsecureRequests() {
        val bad = listOf(
            uri(version = "2"),
            uri(expires = now),
            uri(expires = now - 5),
            uri(expires = now + 3600),
            uri(key = ByteArray(31)),
            uri(session = ByteArray(8)),
            uri(domain = "localhost-olmayan"),
            uri(domain = ""),
            uri(relay = "http://relay.example.workers.dev"),
            uri(relay = "https://user:pw@relay.example.workers.dev"),
            uri(relay = "https://relay.example.workers.dev/?x=1"),
            uri(relay = "ftp://relay.example"),
            uri(relay = "http://10.0.2.2:8787"),
            "sifrekasa://baska?v=1",
            "https://example.com",
            "rastgele",
        )
        for (text in bad) assertThrows(text, SendException::class.java) { SendRequest.parse(text, now) }
        // Geliştirme derlemesinde yerel aktarıcıya izin verilir; uzak http'ye yine verilmez.
        assertEquals("http://10.0.2.2:8787", SendRequest.parse(uri(relay = "http://10.0.2.2:8787"), now, allowInsecureLocal = true).relayUrl)
        assertThrows(SendException::class.java) { SendRequest.parse(uri(relay = "http://evil.example"), now, allowInsecureLocal = true) }
    }

    /** Eklentinin gerçek JavaScript kodunun çözebilmesi için örnek ileti üretir (extension/test/interop.test.mjs okur). */
    @Test
    fun writesFixtureForExtensionInteropTest() {
        val request = SendRequest.parse(uri(domain = "login.example.com"), now)
        val message = SendProtocol.seal(request, "ali@example.com", "kotlin→js şifre", "654321")
        val fixture = """{"extensionPrivateKeyPkcs8":"${Base64.getEncoder().encodeToString(extension.private.encoded)}",
            |"extensionPublicKey":"${Base64Url.encode(extensionPublic)}","sessionId":"${Base64Url.encode(sid)}",
            |"domain":"login.example.com","message":$message,
            |"expected":{"d":"login.example.com","u":"ali@example.com","p":"kotlin→js şifre","t":"654321"}}""".trimMargin()
        File("build/send-fixture.json").apply { parentFile.mkdirs() }.writeText(fixture)
    }
}
