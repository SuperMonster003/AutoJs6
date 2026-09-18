package com.mcal.apksigner.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Locale

/**
 * Keystore loading shared by the host (remote-build signer resolution) and the APK builder plugin.
 * JKS files go through the bundled pure-Java reader ([JksKeyStore]); BKS files go through the platform's own
 * BouncyCastle provider (`"BKS", "BC"`), which reads BKS v1 and v2 written by SpongyCastle 1.58 and
 * BouncyCastle 1.70 / 1.81 on API 24 (BC 1.54), 28 (1.57) and 31 - 35 (1.68); see
 * `docs/dev/evidence/apk-size-p4-bks-20260918.md`. There is no SpongyCastle fallback any more, so a plain
 * desktop JVM needs a provider registered under the name "BC" before it can load BKS.
 *
 * zh-CN: 宿主 (远程构建签名解析) 与 APK 构建插件共用的密钥库加载. JKS 文件经内置的纯 Java 读取器 ([JksKeyStore]);
 * BKS 文件经平台自带的 BouncyCastle provider (`"BKS", "BC"`), 它在 API 24 (BC 1.54), 28 (1.57) 与 31 - 35 (1.68)
 * 上都能读取 SpongyCastle 1.58 与 BouncyCastle 1.70 / 1.81 写出的 BKS v1 / v2, 见
 * `docs/dev/evidence/apk-size-p4-bks-20260918.md`. 不再有 SpongyCastle 回退, 因此桌面 JVM 需要先注册名为 "BC"
 * 的 provider 才能加载 BKS.
 */
object KeyStoreHelper {

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
            KeyStore.getInstance("BKS", "BC").apply {
                if (bksFile == null) {
                    load(null, password)
                } else {
                    FileInputStream(bksFile).use { input -> load(input, password) }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load BKS keystore.", e)
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
