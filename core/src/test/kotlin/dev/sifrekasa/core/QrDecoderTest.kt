package dev.sifrekasa.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.sifrekasa.core.qr.QrDecoder
import dev.sifrekasa.core.totp.OtpAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    private val uri = "otpauth://totp/ACME:ali%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME"

    /** Kamera karesini taklit eder: satır adımı (stride) genişlikten büyük, QR çerçevenin ortasında. */
    private fun frame(text: String, inverted: Boolean = false): Triple<ByteArray, Int, Pair<Int, Int>> {
        val qr = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val width = 640; val height = 480; val stride = 672
        val white: Byte = if (inverted) 20 else 230.toByte()
        val black: Byte = if (inverted) 230.toByte() else 20
        val data = ByteArray(stride * (height - 1) + width) { white }
        for (y in 0 until 300) for (x in 0 until 300) {
            if (qr.get(x, y)) data[(y + 90) * stride + x + 170] = black
        }
        return Triple(data, stride, width to height)
    }

    @Test
    fun decodesQrFromLuminancePlaneAndParsesIt() {
        val (data, stride, size) = frame(uri)
        val text = QrDecoder().decode(data, stride, size.first, size.second)
        assertEquals(uri, text)
        assertEquals("ali@example.com", OtpAuth.parse(text!!).account)
    }

    @Test
    fun decodesInvertedQr() {
        val (data, stride, size) = frame(uri, inverted = true)
        assertEquals(uri, QrDecoder().decode(data, stride, size.first, size.second))
    }

    @Test
    fun returnsNullWhenNoQrInFrame() {
        val decoder = QrDecoder()
        assertNull(decoder.decode(ByteArray(640 * 480) { (it % 251).toByte() }, 640, 640, 480))
        // Başarısız kareden sonra da çalışmaya devam etmeli.
        val (data, stride, size) = frame(uri)
        assertEquals(uri, decoder.decode(data, stride, size.first, size.second))
    }
}
