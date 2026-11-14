package dev.sifrekasa.core.vault

import dev.sifrekasa.core.crypto.AeadAuthenticationException
import dev.sifrekasa.core.crypto.CryptoEngine
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.KEY_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.NONCE_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.SALT_LENGTH
import dev.sifrekasa.core.crypto.CryptoEngine.Companion.TAG_LENGTH
import dev.sifrekasa.core.crypto.KdfParams
import dev.sifrekasa.core.crypto.Secrets
import dev.sifrekasa.core.model.VaultData
import kotlinx.serialization.SerializationException
import java.nio.ByteBuffer

/** Dosya kasa dosyası değil, bozuk ya da desteklenmeyen sürümde. */
class VaultFormatException(message: String) : Exception(message)

/** Parola (veya anahtar) yanlış ya da dosya değiştirilmiş; AEAD ikisini ayırt edemez. */
class WrongKeyException : Exception("Anahtar doğrulanamadı")

/** Ana paroladan türetilen anahtarla sarılmış veri anahtarı (DEK) ve türetme parametreleri. */
class KeySlot(
    val kdf: KdfParams,
    val salt: ByteArray,
    val wrapNonce: ByteArray,
    val wrappedDek: ByteArray,
)

class ParsedVault(
    val keySlot: KeySlot,
    internal val header: ByteArray,
    internal val bodyNonce: ByteArray,
    internal val body: ByteArray,
)

/**
 * Cihazdaki kasa dosyası, sürüm 1 (tüm tamsayılar big-endian):
 *
 * ```
 *   0   4  sihirli değer "SKV1"
 *   4   1  biçim sürümü (1)
 *   5   1  KDF kimliği (1 = Argon2id v1.3)
 *   6   4  Argon2 opsLimit
 *  10   4  Argon2 bellek (KiB)
 *  14  16  tuz
 *  30  24  DEK sarma nonce'u
 *  54  48  sarılmış DEK (32 + 16 etiket)   AD = bayt 0..29
 * 102  24  gövde nonce'u
 * 126   …  gövde: XChaCha20-Poly1305(DEK, JSON)   AD = bayt 0..125 (tüm başlık)
 * ```
 */
object VaultFormat {
    const val VERSION = 1
    private const val KDF_ARGON2ID13 = 1
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'K'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    private const val KDF_SECTION_LENGTH = 4 + 1 + 1 + 4 + 4 + SALT_LENGTH
    private const val WRAPPED_DEK_LENGTH = KEY_LENGTH + TAG_LENGTH
    const val HEADER_LENGTH = KDF_SECTION_LENGTH + NONCE_LENGTH + WRAPPED_DEK_LENGTH + NONCE_LENGTH

    private val json = VaultJson.json

    fun newDek(engine: CryptoEngine): ByteArray = engine.randomBytes(KEY_LENGTH)

    /** [dek]'i [password]'dan türetilen anahtarla sarar. Parola değişiminde de bu kullanılır. */
    fun createKeySlot(engine: CryptoEngine, password: ByteArray, kdf: KdfParams, dek: ByteArray): KeySlot {
        require(dek.size == KEY_LENGTH)
        val salt = engine.randomBytes(SALT_LENGTH)
        val nonce = engine.randomBytes(NONCE_LENGTH)
        val kek = engine.argon2id(password, salt, kdf.opsLimit, kdf.memKiB, KEY_LENGTH)
        try {
            val wrapped = engine.aeadEncrypt(kek, nonce, dek, kdfSection(kdf, salt))
            return KeySlot(kdf, salt, nonce, wrapped)
        } finally {
            Secrets.wipe(kek)
        }
    }

    /** @throws WrongKeyException parola yanlışsa */
    fun unwrapDek(engine: CryptoEngine, slot: KeySlot, password: ByteArray): ByteArray {
        val kek = engine.argon2id(password, slot.salt, slot.kdf.opsLimit, slot.kdf.memKiB, KEY_LENGTH)
        try {
            return engine.aeadDecrypt(kek, slot.wrapNonce, slot.wrappedDek, kdfSection(slot.kdf, slot.salt))
        } catch (e: AeadAuthenticationException) {
            throw WrongKeyException()
        } finally {
            Secrets.wipe(kek)
        }
    }

    /** Tam dosya içeriğini üretir. Her çağrıda yeni bir gövde nonce'u kullanılır. */
    fun seal(engine: CryptoEngine, slot: KeySlot, dek: ByteArray, data: VaultData): ByteArray {
        val bodyNonce = engine.randomBytes(NONCE_LENGTH)
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .put(kdfSection(slot.kdf, slot.salt))
            .put(slot.wrapNonce)
            .put(slot.wrappedDek)
            .put(bodyNonce)
            .array()
        val plaintext = json.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8)
        try {
            return header + engine.aeadEncrypt(dek, bodyNonce, plaintext, header)
        } finally {
            Secrets.wipe(plaintext)
        }
    }

    /** Yalnızca yapıyı ayrıştırır; hiçbir şeyin şifresini çözmez. */
    fun parse(bytes: ByteArray): ParsedVault {
        if (bytes.size < HEADER_LENGTH + TAG_LENGTH) throw VaultFormatException("Dosya çok kısa")
        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw VaultFormatException("Kasa dosyası değil")
        val version = buffer.get().toInt()
        if (version != VERSION) throw VaultFormatException("Desteklenmeyen sürüm: $version")
        if (buffer.get().toInt() != KDF_ARGON2ID13) throw VaultFormatException("Desteklenmeyen KDF")
        val kdf = try {
            KdfParams(opsLimit = buffer.getInt(), memKiB = buffer.getInt())
        } catch (e: IllegalArgumentException) {
            throw VaultFormatException("Geçersiz KDF parametreleri")
        }
        val salt = ByteArray(SALT_LENGTH).also { buffer.get(it) }
        val wrapNonce = ByteArray(NONCE_LENGTH).also { buffer.get(it) }
        val wrappedDek = ByteArray(WRAPPED_DEK_LENGTH).also { buffer.get(it) }
        val bodyNonce = ByteArray(NONCE_LENGTH).also { buffer.get(it) }
        val body = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return ParsedVault(
            keySlot = KeySlot(kdf, salt, wrapNonce, wrappedDek),
            header = bytes.copyOfRange(0, HEADER_LENGTH),
            bodyNonce = bodyNonce,
            body = body,
        )
    }

    /** @throws WrongKeyException DEK yanlışsa ya da başlık/gövde değiştirilmişse */
    fun open(engine: CryptoEngine, parsed: ParsedVault, dek: ByteArray): VaultData {
        val plaintext = try {
            engine.aeadDecrypt(dek, parsed.bodyNonce, parsed.body, parsed.header)
        } catch (e: AeadAuthenticationException) {
            throw WrongKeyException()
        }
        try {
            return json.decodeFromString(VaultData.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: SerializationException) {
            // İletiyi aktarma: içinde gizli veri parçası bulunabilir.
            throw VaultFormatException("Kasa içeriği okunamadı")
        } finally {
            Secrets.wipe(plaintext)
        }
    }

    private fun kdfSection(kdf: KdfParams, salt: ByteArray): ByteArray {
        require(salt.size == SALT_LENGTH)
        return ByteBuffer.allocate(KDF_SECTION_LENGTH)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(KDF_ARGON2ID13.toByte())
            .putInt(kdf.opsLimit)
            .putInt(kdf.memKiB)
            .put(salt)
            .array()
    }
}
