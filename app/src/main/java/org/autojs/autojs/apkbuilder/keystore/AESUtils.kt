package org.autojs.autojs.apkbuilder.keystore

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator

object AESUtils {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128

    private const val KEY_ALIAS = "autojs6_key_store_aes_key"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val key = keyStore.getKey(KEY_ALIAS, null)
        if (key != null) {
            return key as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    // 加密
    fun encrypt(data: String): String {
        val secretKey: SecretKey = getKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encryptedData = cipher.doFinal(data.toByteArray())
        val encryptedBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)

        return "$ivBase64:$encryptedBase64"
    }

    // 解密
    fun decrypt(encryptedData: String): String {
        val parts = encryptedData.split(":")
        val ivBase64 = parts[0]
        val encryptedBase64 = parts[1]

        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        val secretKey: SecretKey = getKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedData = cipher.doFinal(encrypted)

        return String(decryptedData)
    }
}
