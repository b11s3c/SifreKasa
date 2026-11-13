package dev.sifrekasa.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Veri anahtarının (DEK) ikinci sarması: Android Keystore'da duran, yalnızca güçlü biyometriyle
 * (her kullanımda) açılan AES-256-GCM anahtarı. Anahtar cihazdan çıkarılamaz; yeni bir parmak izi
 * kaydedilirse kalıcı olarak geçersizleşir ve ana parola gerekir.
 *
 * Sarılmış DEK ayrı bir dosyada durur (IV || şifreli metin), kasa dosyasına ve yedeğe girmez.
 */
class BiometricKeyStore(context: Context) {
    private val wrapFile = File(context.filesDir, "dek.bio")
    private val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    val isEnabled: Boolean
        get() = wrapFile.exists() && keyStore.containsAlias(ALIAS)

    /** Yeni bir Keystore anahtarı üretir ve BiometricPrompt'a verilecek şifreleme Cipher'ını döner. */
    fun newEncryptCipher(): Cipher {
        disable()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Biyometriyle doğrulanmış [cipher] ile DEK'i sarıp diske yazar. */
    fun storeWrapped(cipher: Cipher, dek: ByteArray) {
        val wrapped = cipher.doFinal(dek)
        check(cipher.iv.size == IV_LENGTH)
        wrapFile.writeBytes(cipher.iv + wrapped)
    }

    /** Anahtar geçersizleşmişse (yeni parmak izi vb.) biyometriyi kapatır ve null döner. */
    fun decryptCipherOrNull(): Cipher? {
        if (!isEnabled) return null
        return try {
            val iv = wrapFile.readBytes().copyOfRange(0, IV_LENGTH)
            val key = keyStore.getKey(ALIAS, null) as SecretKey
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            null
        } catch (e: GeneralSecurityException) {
            disable()
            null
        }
    }

    /** @throws GeneralSecurityException sarılmış veri bozuksa */
    fun unwrap(cipher: Cipher): ByteArray {
        val bytes = wrapFile.readBytes()
        return cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH)
    }

    fun disable() {
        wrapFile.delete()
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "sifrekasa.dek.biometric"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
