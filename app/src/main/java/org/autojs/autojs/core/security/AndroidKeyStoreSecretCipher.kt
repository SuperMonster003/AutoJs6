package org.autojs.autojs.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound AES-GCM secret cipher backed by AndroidKeyStore.
 *
 * The payload format is `v1:<base64 iv>:<base64 ciphertext-and-tag>`.
 */
internal class AndroidKeyStoreSecretCipher @JvmOverloads constructor(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {

    init {
        require(keyAlias.isNotBlank()) { "Key alias must not be blank" }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
        requireAssociatedData(associatedData)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(associatedData)
            val ciphertext = cipher.doFinal(plaintext)
            listOf(
                PAYLOAD_VERSION,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ).joinToString(PAYLOAD_SEPARATOR)
        } catch (e: Exception) {
            throw SecretCipherException("Failed to encrypt secret", e)
        }
    }

    override fun decrypt(payload: String, associatedData: ByteArray): ByteArray {
        requireAssociatedData(associatedData)
        return try {
            val parts = payload.split(PAYLOAD_SEPARATOR, limit = PAYLOAD_PARTS)
            if (parts.size != PAYLOAD_PARTS || parts[0] != PAYLOAD_VERSION) {
                throw IllegalArgumentException("Unsupported encrypted payload format")
            }
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            if (iv.size != GCM_IV_LENGTH_BYTES || ciphertext.size < GCM_TAG_LENGTH_BYTES) {
                throw IllegalArgumentException("Invalid encrypted payload")
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SecretCipherException("Failed to decrypt secret", e)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val candidateLock = Any()
        val lock = keyCreationLocks.putIfAbsent(keyAlias, candidateLock) ?: candidateLock
        return synchronized(lock) {
            loadKey() ?: createOrReloadAfterConcurrentCreation(
                create = ::generateKey,
                reload = ::loadKey,
            )
        }
    }

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val key = keyStore.getKey(keyAlias, null) ?: return null
        return (key as? SecretKey)
            ?.takeIf { it.algorithm.equals(KeyProperties.KEY_ALGORITHM_AES, ignoreCase = true) }
            ?: throw KeyStoreException("Key alias does not contain an AES secret key")
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun requireAssociatedData(associatedData: ByteArray) {
        if (associatedData.isEmpty()) {
            throw SecretCipherException("Associated data must not be empty")
        }
    }

    companion object {

        internal const val DEFAULT_KEY_ALIAS = "autojs6_ai_provider_credentials_v1"

        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PAYLOAD_VERSION = "v1"
        private const val PAYLOAD_SEPARATOR = ":"
        private const val PAYLOAD_PARTS = 3
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS
        private const val KEY_SIZE_BITS = 256

        private val keyCreationLocks = ConcurrentHashMap<String, Any>()
    }
}

/**
 * A process-local lock cannot serialize first use of an AndroidKeyStore alias across AutoJs6's
 * secondary processes. If generation loses that race, perform one fresh lookup and recover only
 * when the exact alias now resolves. Otherwise the original generation failure remains primary.
 *
 * Kept free of Android types so the race semantics can be covered by local JVM tests.
 */
internal fun <T> createOrReloadAfterConcurrentCreation(
    create: () -> T,
    reload: () -> T?,
): T {
    return try {
        create()
    } catch (creationFailure: Exception) {
        val reloaded = try {
            reload()
        } catch (reloadFailure: Exception) {
            creationFailure.addSuppressed(reloadFailure)
            throw creationFailure
        }
        reloaded ?: throw creationFailure
    }
}
