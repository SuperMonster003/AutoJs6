package org.autojs.autojs.apkbuilder.template

import android.content.Context
import com.mcal.apksigner.utils.KeyStoreHelper
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Locale

/**
 * Resolves the signing certificate selected by the host without trusting plugin-supplied output
 * metadata. Only the certificate digest crosses into the prepared request state; keystore paths,
 * aliases, passwords, and certificate details are deliberately absent from errors and diagnostics.
 */
internal object RemoteBuildExpectedSignerResolver {

    private const val DEFAULT_SIGNER_DIGEST_ASSET =
        "apk_builder_default_signer_certificate_sha256.txt"
    private val SHA_256_HEX = Regex("[0-9a-f]{64}")

    @Throws(IOException::class)
    fun resolve(
        context: Context,
        keyStoreFile: File?,
        keyStorePassword: String?,
        keyAlias: String?,
    ): String = if (keyStoreFile == null) {
        resolveDefault(context.applicationContext)
    } else {
        resolveCustom(
            keyStoreFile = keyStoreFile,
            keyStorePassword = keyStorePassword,
            keyAlias = keyAlias,
        )
    }

    @Throws(IOException::class)
    internal fun resolveCustom(
        keyStoreFile: File,
        keyStorePassword: String?,
        keyAlias: String?,
    ): String {
        val storePassword = keyStorePassword
            ?.takeIf(String::isNotBlank)
            ?: throw unresolvedSigner()
        val alias = keyAlias
            ?.takeIf(String::isNotBlank)
            ?: throw unresolvedSigner()
        val storePasswordChars = storePassword.toCharArray()
        try {
            val keyStore = loadKeyStoreWithFallback(keyStoreFile, storePasswordChars)
            if (!keyStore.isKeyEntry(alias)) {
                throw unresolvedSigner()
            }
            val certificate = keyStore.getCertificate(alias) ?: throw unresolvedSigner()
            return certificateSha256(certificate)
        } catch (_: IOException) {
            throw unresolvedSigner()
        } catch (_: Exception) {
            throw unresolvedSigner()
        } finally {
            storePasswordChars.fill('\u0000')
        }
    }

    @Throws(IOException::class)
    internal fun requireExactSignerMatch(
        signerCertificates: List<Certificate>,
        expectedCertificateSha256: String,
    ) {
        val actualDigests = try {
            signerCertificates.map(::certificateSha256)
        } catch (_: Exception) {
            throw signerMismatch()
        }
        requireExactSignerDigestMatch(actualDigests, expectedCertificateSha256)
    }

    @Throws(IOException::class)
    internal fun requireExactSignerDigestMatch(
        actualCertificateSha256: List<String>,
        expectedCertificateSha256: String,
    ) {
        if (
            !SHA_256_HEX.matches(expectedCertificateSha256) ||
            actualCertificateSha256.size != 1 ||
            !SHA_256_HEX.matches(actualCertificateSha256.single()) ||
            actualCertificateSha256.single() != expectedCertificateSha256
        ) {
            throw signerMismatch()
        }
    }

    internal fun certificateSha256(certificate: Certificate): String =
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun resolveDefault(context: Context): String {
        val digest = try {
            context.assets.open(DEFAULT_SIGNER_DIGEST_ASSET).bufferedReader(Charsets.US_ASCII).use {
                it.readText().trim()
            }
        } catch (_: Exception) {
            throw unresolvedSigner()
        }
        if (!SHA_256_HEX.matches(digest)) {
            throw unresolvedSigner()
        }
        return digest
    }

    private fun loadKeyStoreWithFallback(file: File, password: CharArray): KeyStore {
        val prefersBks = file.path.lowercase(Locale.ROOT).endsWith(".bks")
        val primary = if (prefersBks) {
            { KeyStoreHelper.loadBks(file, password) }
        } else {
            { KeyStoreHelper.loadJks(file, password) }
        }
        val fallback = if (prefersBks) {
            { KeyStoreHelper.loadJks(file, password) }
        } else {
            { KeyStoreHelper.loadBks(file, password) }
        }
        return try {
            primary()
        } catch (_: Exception) {
            fallback()
        }
    }

    private fun unresolvedSigner() = IOException(
        "Remote build expected signing certificate could not be resolved.",
    )

    private fun signerMismatch() = IOException(
        "Remote output APK signer does not match the requested signing identity.",
    )
}
