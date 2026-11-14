package dev.sifrekasa.core.vault

import dev.sifrekasa.core.crypto.AeadAuthenticationException
import dev.sifrekasa.core.crypto.CryptoEngine
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.KEY_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.NONCE_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.SALT_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.TAG_LENGTH
import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.crypto.Secrets
import dev.sifrekasa.core.model.BackupConfig
import dev.sifrekasa.core.model.VaultData
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.nio.ByteBuffer
import java.util.Base64

/** Dışa aktarım parolasından türetilmiş anahtar. [key] gizlidir. */
class ExportKey(val kdf: KdfParams, val salt: ByteArray, val key: ByteArray) {
    fun toBackupConfig(): BackupConfig {
        val encoder = Base64.getEncoder()
        return BackupConfig(kdf.opsLimit, kdf.memKiB, encoder.encodeToString(salt), encoder.encodeToString(key))
    }

    companion object {
        /** @throws IllegalArgumentException saklanan değerler geçersizse */
        fun from(config: BackupConfig): ExportKey {
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(config.salt)
            val key = decoder.decode(config.key)
            require(salt.size == SALT_LENGTH && key.size == KEY_LENGTH)
            return ExportKey(KdfParams(config.opsLimit, config.memKiB), salt, key)
        }
    }
}

@Serializable
data class ExportPayload(
    val format: Int = 1,
    /** Dışa aktarım anı (Unix ms). */
    val exportedAt: Long = 0,
    val vault: VaultData = VaultData(),
) {
    override fun toString(): String = "ExportPayload(exportedAt=$exportedAt, entries=${vault.entries.size})"
}

/**
 * Dışa aktarım / yedek dosyası (.skx), sürüm 1 (tamsayılar big-endian):
 *
 * ```
 *   0   4  sihirli değer "SKX1"
 *   4   1  biçim sürümü (1)
 *   5   1  KDF kimliği (1 = Argon2id v1.3)
 *   6   4  Argon2 opsLimit
 *  10   4  Argon2 bellek (KiB)
 *  14  16  tuz
 *  30  24  nonce
 *  54   …  XChaCha20-Poly1305(anahtar = Argon2id(dışa aktarım parolası, tuz), JSON)   AD = bayt 0..53
 * ```
 *
 * Cihazdaki kasa dosyasından bilerek ayrıdır: ana paroladan bağımsız bir parolayla şifrelenir ve
 * cihaza özgü hiçbir şey (biyometrik sarma, yedek anahtarı) içermez.
 */
object ExportFormat {
    const val VERSION = 1
    const val FILE_EXTENSION = "skx"
    private const val KDF_ARGON2ID13 = 1
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'K'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte())
    const val HEADER_LENGTH = 4 + 1 + 1 + 4 + 4 + SALT_LENGTH + NONCE_LENGTH

    /** Yeni bir tuzla anahtar türetir (yavaş). */
    fun deriveKey(engine: CryptoEngine, password: ByteArray, kdf: KdfParams = KdfParams.DEFAULT): ExportKey {
        val salt = engine.randomBytes(SALT_LENGTH)
        return ExportKey(kdf, salt, engine.argon2id(password, salt, kdf.opsLimit, kdf.memKiB, KEY_LENGTH))
    }

    /**
     * Aynı [key] ile tekrar tekrar çağrılabilir (otomatik yedek): her dosyada yeni 192 bitlik rastgele
     * nonce kullanılır. Yedek anahtarı dosyaya yazılmaz.
     */
    fun seal(engine: CryptoEngine, key: ExportKey, data: VaultData, exportedAt: Long): ByteArray {
        val nonce = engine.randomBytes(NONCE_LENGTH)
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(KDF_ARGON2ID13.toByte())
            .putInt(key.kdf.opsLimit)
            .putInt(key.kdf.memKiB)
            .put(key.salt)
            .put(nonce)
            .array()
        val payload = ExportPayload(exportedAt = exportedAt, vault = data.copy(backup = null))
        val plaintext = VaultJson.json.encodeToString(ExportPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        try {
            return header + engine.aeadEncrypt(key.key, nonce, plaintext, header)
        } finally {
            Secrets.wipe(plaintext)
        }
    }

    fun looksLikeExport(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /**
     * @throws VaultFormatException dosya dışa aktarım dosyası değil, bozuk ya da desteklenmeyen sürümde
     * @throws WrongKeyException parola yanlış ya da dosya değiştirilmiş
     */
    fun open(engine: CryptoEngine, bytes: ByteArray, password: ByteArray): ExportPayload {
        if (bytes.size < HEADER_LENGTH + TAG_LENGTH) throw VaultFormatException("Dosya çok kısa")
        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw VaultFormatException("Şifre Kasa dışa aktarım dosyası değil")
        val version = buffer.get().toInt()
        if (version != VERSION) throw VaultFormatException("Desteklenmeyen sürüm: $version")
        if (buffer.get().toInt() != KDF_ARGON2ID13) throw VaultFormatException("Desteklenmeyen KDF")
        val kdf = try {
            KdfParams(opsLimit = buffer.getInt(), memKiB = buffer.getInt())
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException("Geçersiz KDF parametreleri")
        }
        val salt = ByteArray(SALT_LENGTH).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_LENGTH).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val header = bytes.copyOfRange(0, HEADER_LENGTH)

        val key = engine.argon2id(password, salt, kdf.opsLimit, kdf.memKiB, KEY_LENGTH)
        val plaintext = try {
            engine.aeadDecrypt(key, nonce, ciphertext, header)
        } catch (e: AeadAuthenticationException) {
            throw WrongKeyException()
        } finally {
            Secrets.wipe(key)
        }
        try {
            val payload = VaultJson.json.decodeFromString(ExportPayload.serializer(), String(plaintext, Charsets.UTF_8))
            // Başka bir cihazın yedek anahtarı hiçbir koşulda içeri alınmaz.
            return payload.copy(vault = payload.vault.copy(backup = null))
        } catch (e: SerializationException) {
            throw VaultFormatException("Dosya içeriği okunamadı")
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException("Dosya içeriği okunamadı")
        } finally {
            Secrets.wipe(plaintext)
        }
    }
}
