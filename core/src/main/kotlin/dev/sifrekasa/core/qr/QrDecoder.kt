package dev.sifrekasa.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Kamera karesinin parlaklık (Y) düzleminden QR okur. ZXing kullanır; ağ ya da Google servisi gerekmez. */
class QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    /** @return QR içeriği; karede okunabilir QR yoksa null */
    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        // Koyu zemin üstüne açık renkli QR'lar için ters çevrilmiş görüntü de denenir.
        return tryDecode(source) ?: tryDecode(source.invert())
    }

    private fun tryDecode(source: LuminanceSource): String? =
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }
}
