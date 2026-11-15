package dev.sifrekasa.core.passkey

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * ES256 (ECDSA P-256 + SHA-256) işlemleri. Anahtar üretimi ve imza platformun kripto
 * sağlayıcısından gelir; burada algoritma uygulanmaz.
 */
object PasskeyCrypto {
    class GeneratedKey(val privateKeyPkcs8: ByteArray, val publicKey: ECPublicKey)

    fun generate(): GeneratedKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        return GeneratedKey(pair.private.encoded, pair.public as ECPublicKey)
    }

    /** @return ASN.1 DER kodlu ECDSA imzası (WebAuthn'in beklediği biçim) */
    fun sign(privateKeyPkcs8: ByteArray, data: ByteArray): ByteArray {
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8))
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(data)
            sign()
        }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        javax.crypto.Mac.getInstance("HmacSHA256").run {
            init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** COSE_Key (RFC 9052): kty=EC2, alg=ES256, crv=P-256, x, y. */
    fun coseKey(publicKey: ECPublicKey): ByteArray =
        CborWriter().mapHeader(5)
            .int(1).int(2)
            .int(3).int(-7)
            .int(-1).int(1)
            .int(-2).bytes(fixed32(publicKey.w.affineX))
            .int(-3).bytes(fixed32(publicKey.w.affineY))
            .toByteArray()

    private fun fixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(32)
        val length = minOf(raw.size, 32)
        System.arraycopy(raw, raw.size - length, out, 32 - length, length)
        return out
    }
}
