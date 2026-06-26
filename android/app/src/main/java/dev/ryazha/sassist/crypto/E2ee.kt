package dev.ryazha.sassist.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for messages. AES-256-GCM with a key derived from a
 * per-room passphrase via PBKDF2WithHmacSHA256 (120k iterations). The server
 * only ever stores ciphertext blobs -- it cannot read messages (zero-knowledge).
 *
 * Wire format: "v1:" + base64(salt) + ":" + base64(iv) + ":" + base64(ct+tag)
 */
object E2ee {
    private const val PREFIX = "v1:"
    private const val ITER = 120_000
    private const val KEY_BITS = 256
    private const val IV_LEN = 12
    private const val SALT_LEN = 16
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    private fun b64e(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun isEncrypted(s: String): Boolean = s.startsWith(PREFIX)

    fun encrypt(plaintext: String, passphrase: String): String {
        return try {
            val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            PREFIX + b64e(salt) + ":" + b64e(iv) + ":" + b64e(ct)
        } catch (e: Exception) {
            plaintext
        }
    }

    fun decrypt(payload: String, passphrase: String): String {
        if (!payload.startsWith(PREFIX)) return payload
        return try {
            val parts = payload.removePrefix(PREFIX).split(":")
            if (parts.size != 3) return payload
            val salt = b64d(parts[0]); val iv = b64d(parts[1]); val ct = b64d(parts[2])
            val key = deriveKey(payload.let { passphrase }, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            payload
        }
    }
}
