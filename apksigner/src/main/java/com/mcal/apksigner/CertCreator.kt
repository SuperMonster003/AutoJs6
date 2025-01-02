package com.mcal.apksigner

import com.mcal.apksigner.utils.DistinguishedNameValues
import com.mcal.apksigner.utils.KeySet
import com.mcal.apksigner.utils.KeyStoreHelper
import org.spongycastle.x509.X509V3CertificateGenerator
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date

object CertCreator {
    /**
     * Creates a new keystore and self-signed key.  The key will have the same password as the key, and will be
     * RSA 2048, with the cert signed using SHA1withRSA.  The certificate will have a validity of
     * 30 years).
     *
     * @param keyFile               - new keystore file
     * @param password                - keystore and key password
     * @param keyName                 - the new key will have this as its alias within the keystore
     * @param distinguishedNameValues - contains Country, State, Locality,...,Common Name, etc.
     */
    @JvmStatic
    fun createKeystoreAndKey(
        keyFile: File,
        password: CharArray,
        keyName: String,
        distinguishedNameValues: DistinguishedNameValues
    ) {
        createKeystoreAndKey(
            keyFile, password, "RSA", 2048, keyName, password,
            "SHA1withRSA", 30, distinguishedNameValues
        )
    }

    @JvmStatic
    fun createKeystoreAndKey(
        keyFile: File,
        storePass: CharArray,
        keyAlgorithm: String,
        keySize: Int,
        keyName: String,
        keyPass: CharArray,
        certSignatureAlgorithm: String,
        certValidityYears: Int,
        distinguishedNameValues: DistinguishedNameValues
    ): KeySet {
        return try {
            val keySet = createKey(
                keyAlgorithm, keySize, keyName, certSignatureAlgorithm, certValidityYears,
                distinguishedNameValues
            )
            val privateKS = KeyStoreHelper.createKeyStore(keyFile, storePass)
            privateKS.setKeyEntry(
                keyName, keySet.privateKey,
                keyPass, arrayOf<Certificate>(keySet.publicKey)
            )
            if (keyFile.exists()) {
                throw IOException("File already exists: $keyFile")
            }
            KeyStoreHelper.writeKeyStore(privateKS, keyFile, storePass)
            keySet
        } catch (x: RuntimeException) {
            throw x
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }

    /**
     * Create a new key and store it in an existing keystore.
     */
    @JvmStatic
    fun createKey(
        keyFile: File,
        storePass: CharArray,
        keyAlgorithm: String,
        keySize: Int,
        keyName: String,
        keyPass: CharArray,
        certSignatureAlgorithm: String,
        certValidityYears: Int,
        distinguishedNameValues: DistinguishedNameValues
    ): KeySet {
        return try {
            val keySet = createKey(
                keyAlgorithm, keySize, keyName, certSignatureAlgorithm, certValidityYears,
                distinguishedNameValues
            )
            val privateKS = KeyStoreHelper.createKeyStore(keyFile, storePass)
            privateKS.setKeyEntry(
                keyName, keySet.privateKey,
                keyPass, arrayOf<Certificate>(keySet.publicKey)
            )
            KeyStoreHelper.writeKeyStore(privateKS, keyFile, storePass)
            keySet
        } catch (x: RuntimeException) {
            throw x
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }

    @JvmStatic
    fun createKey(
        keyAlgorithm: String,
        keySize: Int,
        keyName: String,
        certSignatureAlgorithm: String,
        certValidityYears: Int,
        distinguishedNameValues: DistinguishedNameValues
    ): KeySet {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(keyAlgorithm)
            keyPairGenerator.initialize(keySize)
            val keyPair = keyPairGenerator.generateKeyPair()
            val v3CertGen = X509V3CertificateGenerator()
            val principal = distinguishedNameValues.principal

            // generate a postitive serial number
            var serialNumber = BigInteger.valueOf(SecureRandom().nextInt().toLong())
            while (serialNumber < BigInteger.ZERO) {
                serialNumber = BigInteger.valueOf(SecureRandom().nextInt().toLong())
            }
            v3CertGen.setSerialNumber(serialNumber)
            v3CertGen.setIssuerDN(principal)
            v3CertGen.setNotBefore(Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L * 30L))
            v3CertGen.setNotAfter(Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 366L * certValidityYears.toLong()))
            v3CertGen.setSubjectDN(principal)
            v3CertGen.setPublicKey(keyPair.public)
            v3CertGen.setSignatureAlgorithm(certSignatureAlgorithm)
            val certificate =
                v3CertGen.generate(keyPair.private/*, "BC" */)
            KeySet().apply {
                name = keyName
                privateKey = keyPair.private
                publicKey = certificate
            }
        } catch (x: Exception) {
            throw RuntimeException(x.message, x)
        }
    }
}
