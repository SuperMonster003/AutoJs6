package org.autojs.plugin.apkbuilder.template

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated, one-request envelope for JavaScript emitted from an encrypted TypeScript source.
 *
 * This is transport protection only. The APK Builder decrypts an envelope in memory and then
 * encrypts the script with the packaged project's final runtime key.
 */
object TypeScriptBuildStagingCipher {

    const val VERSION = 1

    private const val KEY_SIZE_BYTES = 32
    private const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val magic = byteArrayOf(
        0x41,
        0x4a,
        0x36,
        0x54,
        0x53,
        0x47,
        0x31,
        0x00,
    )

    fun generateKey(): ByteArray = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)

    fun encrypt(cleartext: ByteArray, key: ByteArray): ByteArray {
        requireKey(key)
        require(cleartext.isNotEmpty()) { "TypeScript staging cleartext is empty." }
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val encrypted = newCipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(cleartext)
        return try {
            ByteArray(magic.size + NONCE_SIZE_BYTES + encrypted.size).also { envelope ->
                magic.copyInto(envelope)
                nonce.copyInto(envelope, magic.size)
                encrypted.copyInto(envelope, magic.size + NONCE_SIZE_BYTES)
            }
        } finally {
            nonce.fill(0)
            encrypted.fill(0)
        }
    }

    fun decrypt(envelope: ByteArray, key: ByteArray): ByteArray {
        requireKey(key)
        require(isEncrypted(envelope)) { "Invalid TypeScript staging envelope." }
        val nonceStart = magic.size
        val cipherTextStart = nonceStart + NONCE_SIZE_BYTES
        val nonce = envelope.copyOfRange(nonceStart, cipherTextStart)
        return try {
            newCipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(
                envelope,
                cipherTextStart,
                envelope.size - cipherTextStart,
            )
        } finally {
            nonce.fill(0)
        }
    }

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < magic.size + NONCE_SIZE_BYTES + TAG_SIZE_BYTES) return false
        return magic.indices.all { index -> bytes[index] == magic[index] }
    }

    private fun newCipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(
                mode,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_SIZE_BITS, nonce),
            )
            updateAAD(magic)
        }

    private fun requireKey(key: ByteArray) {
        require(key.size == KEY_SIZE_BYTES) {
            "TypeScript staging encryption requires a $KEY_SIZE_BYTES-byte key."
        }
    }
}
