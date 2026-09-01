package com.mcal.apksigner.utils

import org.spongycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.Security
import java.util.Locale

object KeyStoreHelper {
    val provider by lazy(LazyThreadSafetyMode.NONE) {
        BouncyCastleProvider().also {
            Security.addProvider(it)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun loadJks(jksFile: File?, password: CharArray): KeyStore {
        return try {
            JksKeyStore().apply {
                if (jksFile == null) {
                    load(null, password)
                } else {
                    FileInputStream(jksFile).use { input -> load(input, password) }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load JKS keystore.", e)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun loadBks(bksFile: File?, password: CharArray): KeyStore {
        return try {
            loadBksWithProvider(bksFile, password, KeyStore.getInstance("BKS", "BC"))
        } catch (primaryError: Exception) {
            try {
                loadBksWithProvider(bksFile, password, KeyStore.getInstance("BKS", provider))
            } catch (fallbackError: Exception) {
                primaryError.addSuppressed(fallbackError)
                throw RuntimeException("Failed to load BKS keystore.", primaryError)
            }
        }
    }

    private fun loadBksWithProvider(
        bksFile: File?,
        password: CharArray,
        keyStore: KeyStore,
    ): KeyStore = keyStore.apply {
        if (bksFile == null) {
            load(null, password)
        } else {
            FileInputStream(bksFile).use { input -> load(input, password) }
        }
    }

    @JvmStatic
    @Throws(java.lang.Exception::class)
    fun loadKeyStore(keystoreFile: File, password: CharArray): KeyStore {
        return if (keystoreFile.path.lowercase(Locale.getDefault()).endsWith(".bks")) {
            loadBks(keystoreFile, password)
        } else {
            loadJks(keystoreFile, password)
        }
    }

    @JvmStatic
    @Throws(java.lang.Exception::class)
    fun createKeyStore(keystoreFile: File, password: CharArray): KeyStore {
        return if (keystoreFile.path.lowercase(Locale.getDefault()).endsWith(".bks")) {
            loadBks(null, password)
        } else {
            loadJks(null, password)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun writeKeyStore(ks: KeyStore, keystorePath: File, password: CharArray) {
        FileOutputStream(keystorePath).use { fos ->
            ks.store(fos, password)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateKeystorePassword(keystoreFile: File, password: String): Boolean {
        return try {
            loadKeyStore(keystoreFile, password.toCharArray())
            true
        } catch (e: Exception) {
            false
        }
    }
}
