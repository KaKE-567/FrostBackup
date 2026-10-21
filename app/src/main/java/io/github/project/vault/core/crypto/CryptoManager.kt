package io.github.project.vault.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val MAGIC_HEADER = "VAULT1"
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    suspend fun encryptFile(
        sourceFile: File,
        destinationFile: File,
        password: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val totalBytes = sourceFile.length()
        var processedBytes = 0L

        FileOutputStream(destinationFile).use { fos ->
            // Write Header: Magic (6B) + Salt (16B) + IV (12B)
            fos.write(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
            fos.write(salt)
            fos.write(iv)

            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(sourceFile).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        cos.write(buffer, 0, bytesRead)
                        processedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(processedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }
        }
    }

    suspend fun decryptFile(
        sourceFile: File,
        destinationFile: File,
        password: String,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        FileInputStream(sourceFile).use { fis ->
            // Read and verify Magic Header
            val magicBuffer = ByteArray(MAGIC_HEADER.length)
            if (fis.read(magicBuffer) != MAGIC_HEADER.length || String(magicBuffer, Charsets.US_ASCII) != MAGIC_HEADER) {
                throw IllegalArgumentException("Invalid file format: Missing AppVault magic header")
            }

            val salt = ByteArray(SALT_LENGTH_BYTES)
            val iv = ByteArray(IV_LENGTH_BYTES)
            if (fis.read(salt) != SALT_LENGTH_BYTES || fis.read(iv) != IV_LENGTH_BYTES) {
                throw IllegalArgumentException("Corrupted backup archive header")
            }

            val secretKey = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val totalBytes = sourceFile.length() - (MAGIC_HEADER.length + SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
            var processedBytes = 0L

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(destinationFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (cis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        processedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(processedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
