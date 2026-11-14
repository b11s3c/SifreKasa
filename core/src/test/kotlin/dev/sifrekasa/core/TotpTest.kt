package dev.sifrekasa.core

import dev.sifrekasa.core.totp.Base32
import dev.sifrekasa.core.totp.OtpAuth
import dev.sifrekasa.core.totp.OtpAuthException
import dev.sifrekasa.core.totp.Totp
import dev.sifrekasa.core.totp.TotpAlgorithm
import dev.sifrekasa.core.totp.TotpConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TotpTest {
    /** RFC 4648 bölüm 10 test vektörleri. */
    @Test
    fun base32Rfc4648Vectors() {
        val vectors = mapOf(
            "" to "", "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6",
            "foob" to "MZXW6YQ", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI",
        )
        for ((plain, encoded) in vectors) {
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            assertArrayEquals(plain.toByteArray(), Base32.decode(encoded))
        }
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb-oi======"))
    }

    @Test
    fun base32RejectsInvalidInput() {
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("MZXW1") }
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("M") }
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("MZX") }
        assertThrows(IllegalArgumentException::class.java) { Base32.decode("şifre") }
    }

    /** RFC 4226 Ek D: HOTP test değerleri. */
    @Test
    fun hotpRfc4226Vectors() {
        val key = "12345678901234567890".toByteArray()
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code -> assertEquals(code, Totp.hotp(key, counter.toLong(), 6)) }
    }

    /** RFC 6238 Ek B: SHA1, SHA256 ve SHA512 için TOTP test değerleri (8 hane, 30 sn). */
    @Test
    fun totpRfc6238Vectors() {
        val seeds = mapOf(
            TotpAlgorithm.SHA1 to "12345678901234567890",
            TotpAlgorithm.SHA256 to "12345678901234567890123456789012",
            TotpAlgorithm.SHA512 to "1234567890123456789012345678901234567890123456789012345678901234",
        )
        val table = listOf(
            59L to listOf("94287082", "46119246", "90693936"),
            1111111109L to listOf("07081804", "68084774", "25091201"),
            1111111111L to listOf("14050471", "67062674", "99943326"),
            1234567890L to listOf("89005924", "91819424", "93441116"),
            2000000000L to listOf("69279037", "90698825", "38618901"),
            20000000000L to listOf("65353130", "77737706", "47863826"),
        )
        for ((time, codes) in table) {
            TotpAlgorithm.entries.forEachIndexed { i, algorithm ->
                val config = TotpConfig(Base32.encode(seeds.getValue(algorithm).toByteArray()), algorithm, digits = 8, period = 30)
                assertEquals("t=$time $algorithm", codes[i], Totp.code(config, time))
            }
        }
    }

    @Test
    fun secondsRemainingCountsDownWithinPeriod() {
        val config = TotpConfig("MZXW6YTBOI")
        assertEquals(30, Totp.secondsRemaining(config, 60))
        assertEquals(1, Totp.secondsRemaining(config, 89))
        assertEquals(30, Totp.secondsRemaining(config, 90))
        assertEquals(Totp.code(config, 60), Totp.code(config, 89))
        assertFalse(Totp.code(config, 89) == Totp.code(config, 90))
    }

    @Test
    fun parsesFullOtpAuthUri() {
        val entry = OtpAuth.parse(
            "otpauth://totp/ACME%20Co:ali%40example.com?secret=jbsw%20y3dp-ehpk3pxp&issuer=ACME+Co&algorithm=SHA256&digits=8&period=60"
        )
        assertEquals("ACME Co", entry.issuer)
        assertEquals("ali@example.com", entry.account)
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP", TotpAlgorithm.SHA256, 8, 60), entry.config)
    }

    @Test
    fun parsesMinimalUriWithDefaults() {
        val entry = OtpAuth.parse("otpauth://totp/GitHub:ali?secret=JBSWY3DPEHPK3PXP")
        assertEquals("GitHub", entry.issuer)
        assertEquals("ali", entry.account)
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP"), entry.config)
        assertEquals("ali", OtpAuth.parse("OTPAUTH://TOTP/ali?secret=JBSWY3DPEHPK3PXP").account)
    }

    @Test
    fun rejectsUnsupportedOrMalformedUris() {
        val bad = listOf(
            "https://example.com/?secret=JBSWY3DPEHPK3PXP",
            "otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1",
            "otpauth://totp/x",
            "otpauth://totp/x?secret=",
            "otpauth://totp/x?secret=not!base32",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=12",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&period=0",
            "otpauth://totp/x y?secret=JBSWY3DPEHPK3PXP",
            "rastgele metin",
        )
        for (uri in bad) assertThrows(uri, OtpAuthException::class.java) { OtpAuth.parse(uri) }
    }

    /** Hata iletileri ve toString gizli anahtarı sızdırmamalı. */
    @Test
    fun secretsDoNotLeakIntoMessages() {
        val secret = "JBSWY3DPEHPK3PXP"
        val error = assertThrows(OtpAuthException::class.java) { OtpAuth.parse("otpauth://totp/x?secret=$secret&digits=99") }
        assertFalse(error.message.orEmpty().contains(secret))
        assertFalse(TotpConfig(secret).toString().contains(secret))
        assertFalse(OtpAuth.parse("otpauth://totp/x?secret=$secret").toString().contains(secret))
    }

    @Test
    fun manualEntryAcceptsBareSecretOrUri() {
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP"), OtpAuth.parseManual(" jbsw y3dp ehpk 3pxp ").config)
        assertEquals("GitHub", OtpAuth.parseManual("otpauth://totp/GitHub:ali?secret=JBSWY3DPEHPK3PXP").issuer)
        assertThrows(OtpAuthException::class.java) { OtpAuth.parseManual("merhaba dünya!") }
        assertThrows(OtpAuthException::class.java) { OtpAuth.parseManual("") }
    }
}
