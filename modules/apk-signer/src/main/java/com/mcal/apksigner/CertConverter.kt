package com.mcal.apksigner

import com.mcal.apksigner.utils.Base64
import com.mcal.apksigner.utils.KeyStoreHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.PrivateKey

object CertConverter {
    @JvmStatic
    fun convert(
        inputKeyFile: File,
        outputKeyFile: File,
        password: String,
        alias: String,
        aliasPassword: String
    ): Boolean {
        return try {
            val isJks = inputKeyFile.name.endsWith(".jks")

            val inputStream = FileInputStream(inputKeyFile)
            val inputKeyStore = if (isJks) {
                KeyStoreHelper.loadJks(inputKeyFile, password.toCharArray())
            } else {
                KeyStoreHelper.loadBks(inputKeyFile, password.toCharArray())
            }
            inputStream.close()

            val outputKeyStore = if (isJks) {
                KeyStoreHelper.loadBks(null, /* new password */ password.toCharArray())
            } else {
                KeyStoreHelper.loadJks(null, /* new password */ password.toCharArray())
            }
            val key = inputKeyStore.getKey(alias, aliasPassword.toCharArray())
            val chain = inputKeyStore.getCertificateChain(alias)
            outputKeyStore.setKeyEntry(alias, key, /* new password */ password.toCharArray(), chain)

            val outputStream = FileOutputStream(outputKeyFile)
            outputKeyStore.store(outputStream, /* new password */ password.toCharArray())
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun convert(
        inputKeyFile: File,
        outputKeyFile: File,
        password: String,
        aliasPassword: String
    ): Boolean {
        return try {
            val isJks = inputKeyFile.name.endsWith(".jks")

            val inputStream = FileInputStream(inputKeyFile)
            val inputKeyStore = if (isJks) {
                KeyStoreHelper.loadJks(inputKeyFile, password.toCharArray())
            } else {
                KeyStoreHelper.loadBks(inputKeyFile, password.toCharArray())
            }
            inputStream.close()

            val outputKeyStore = if (isJks) {
                KeyStoreHelper.loadBks(null, /* new password */ password.toCharArray())
            } else {
                KeyStoreHelper.loadJks(null, /* new password */ password.toCharArray())
            }
            val alias = inputKeyStore.aliases().nextElement()
            val key = inputKeyStore.getKey(alias, aliasPassword.toCharArray())
            val chain = inputKeyStore.getCertificateChain(alias)
            outputKeyStore.setKeyEntry(alias, key, /* new password */ password.toCharArray(), chain)

            val outputStream = FileOutputStream(outputKeyFile)
            outputKeyStore.store(outputStream, /* new password */ password.toCharArray())
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun convert(
        inputKeyFile: File,
        password: String,
        alias: String,
        aliasPassword: String,
        pk8File: File,
        pemFile: File
    ): Boolean {
        return try {
            val jksInputStream = FileInputStream(inputKeyFile)
            val keyStore = KeyStoreHelper.loadKeyStore(inputKeyFile, password.toCharArray())
            jksInputStream.close()

            val key = keyStore.getKey(alias, aliasPassword.toCharArray())
            val publicKey = keyStore.getCertificate(alias)
            val keyPair = KeyPair(publicKey.publicKey, key as PrivateKey)

            FileOutputStream(pk8File).use { pk8OutputStream ->
                pk8OutputStream.write(keyPair.private.encoded)
            }

            FileOutputStream(pemFile).use { pemOutputStream ->
                pemOutputStream.write("-----BEGIN CERTIFICATE-----\n".toByteArray())
                pemOutputStream.write(formatCertificate(publicKey.encoded))
                pemOutputStream.write("\n-----END CERTIFICATE-----\n".toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun convert(
        inputKeyFile: File,
        password: String,
        aliasPassword: String,
        pk8File: File,
        pemFile: File
    ): Boolean {
        return try {
            val jksInputStream = FileInputStream(inputKeyFile)
            val keyStore = KeyStoreHelper.loadKeyStore(inputKeyFile, password.toCharArray())
            jksInputStream.close()

            val alias = keyStore.aliases().nextElement()
            val key = keyStore.getKey(alias, aliasPassword.toCharArray())
            val publicKey = keyStore.getCertificate(alias)
            val keyPair = KeyPair(publicKey.publicKey, key as PrivateKey)

            FileOutputStream(pk8File).use { pk8OutputStream ->
                pk8OutputStream.write(keyPair.private.encoded)
            }

            FileOutputStream(pemFile).use { pemOutputStream ->
                pemOutputStream.write("-----BEGIN CERTIFICATE-----\n".toByteArray())
                pemOutputStream.write(formatCertificate(publicKey.encoded))
                pemOutputStream.write("\n-----END CERTIFICATE-----\n".toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun formatCertificate(byteArray: ByteArray): ByteArray {
        return Base64.encode(byteArray).replace("(.{64})".toRegex(), "$1\n").toByteArray()
    }
}
