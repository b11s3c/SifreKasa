package dev.sifrekasa.core

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import dev.sifrekasa.core.crypto.CryptoEngine
import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.crypto.SodiumCryptoEngine

object TestEngine {
    val engine: CryptoEngine by lazy { SodiumCryptoEngine(LazySodiumJava(SodiumJava())) }
    /** Testler hızlı çalışsın diye küçük maliyet. */
    val fastKdf = KdfParams(opsLimit = 1, memKiB = 64)
}

fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
