package org.autojs.plugin.apkbuilder.template.impl

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import com.mcal.apksigner.CertCreator
import com.mcal.apksigner.utils.DistinguishedNameValues
import com.mcal.apksigner.utils.KeyStoreHelper
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreRequest
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object RemoteKeyStoreManager {

    @Synchronized
    fun execute(context: Context, request: ApkKeyStoreRequest): ApkKeyStoreResult {
        val requestFd = request.keyStoreFd
        return try {
            runCatching {
                when (request.operation) {
                    ApkKeyStoreRequest.OPERATION_CREATE -> create(context, request)
                    ApkKeyStoreRequest.OPERATION_VERIFY -> verify(context, request)
                    else -> ApkKeyStoreResult(
                        status = ApkKeyStoreResult.STATUS_UNSUPPORTED,
                        message = "Unsupported keystore operation: ${request.operation}",
                    )
                }
            }.getOrElse { error ->
                ApkKeyStoreResult(
                    status = ApkKeyStoreResult.STATUS_FAILED,
                    message = error.message ?: error.toString(),
                )
            }
        } finally {
            // Also covers unsupported operations, which never enter create()/verify().
            runCatching { requestFd?.close() }
        }
    }

    private fun create(context: Context, request: ApkKeyStoreRequest): ApkKeyStoreResult {
        val outputFd = request.keyStoreFd ?: throw IOException("Keystore output fd is missing.")
        try {
            val storePassword = request.keyStorePassword.requireField("Keystore password", MAX_PASSWORD_LENGTH)
            val alias = request.keyAlias.requireField("Key alias", MAX_ALIAS_LENGTH)
            val aliasPassword = request.keyAliasPassword.requireField("Key alias password", MAX_PASSWORD_LENGTH)
            val type = request.normalizedType()
            val keyAlgorithm = request.keyAlgorithm.requireField("Key algorithm", MAX_ALGORITHM_LENGTH)
            if (keyAlgorithm != "RSA") {
                throw IOException("Unsupported key algorithm: $keyAlgorithm")
            }
            if (request.keySize !in MIN_RSA_KEY_SIZE..MAX_RSA_KEY_SIZE) {
                throw IOException("RSA key size must be between $MIN_RSA_KEY_SIZE and $MAX_RSA_KEY_SIZE.")
            }
            val signatureAlgorithm = request.certificateSignatureAlgorithm
                .requireField("Certificate signature algorithm", MAX_ALGORITHM_LENGTH)
            if (signatureAlgorithm !in SUPPORTED_SIGNATURE_ALGORITHMS) {
                throw IOException("Unsupported certificate signature algorithm: $signatureAlgorithm")
            }
            if (request.certificateValidityYears !in MIN_VALIDITY_YEARS..MAX_VALIDITY_YEARS) {
                throw IOException(
                    "Certificate validity must be between $MIN_VALIDITY_YEARS and $MAX_VALIDITY_YEARS years."
                )
            }
            request.validateDistinguishedNameLengths()
            val temporary = temporaryKeyStoreFile(context, type)
            try {
                // CertCreator deliberately refuses to overwrite. The name was reserved with
                // createTempFile in the plugin-private cache and operations are serialized.
                if (!temporary.delete()) {
                    throw IOException("Failed to prepare temporary keystore: ${temporary.path}")
                }
                val distinguishedName = DistinguishedNameValues().apply {
                    setCommonName(request.commonName.orEmpty())
                    setOrganization(request.organization.orEmpty())
                    setOrganizationalUnit(request.organizationalUnit.orEmpty())
                    setCountry(request.country.orEmpty())
                    setState(request.state.orEmpty())
                    setLocality(request.locality.orEmpty())
                    setStreet(request.street.orEmpty())
                }
                CertCreator.createKeystoreAndKey(
                    temporary,
                    storePassword.toCharArray(),
                    keyAlgorithm,
                    request.keySize,
                    alias,
                    aliasPassword.toCharArray(),
                    signatureAlgorithm,
                    request.certificateValidityYears,
                    distinguishedName,
                )
                ParcelFileDescriptor.AutoCloseOutputStream(outputFd).use { output ->
                    FileInputStream(temporary).use { input ->
                        input.copyTo(output, BUFFER_SIZE)
                        output.flush()
                    }
                }
                return ApkKeyStoreResult(status = ApkKeyStoreResult.STATUS_OK)
            } finally {
                temporary.delete()
            }
        } finally {
            runCatching { outputFd.close() }
        }
    }

    private fun verify(context: Context, request: ApkKeyStoreRequest): ApkKeyStoreResult {
        val inputFd = request.keyStoreFd ?: throw IOException("Keystore input fd is missing.")
        try {
            val storePassword = request.keyStorePassword.requireField("Keystore password", MAX_PASSWORD_LENGTH)
            val alias = request.keyAlias.requireField("Key alias", MAX_ALIAS_LENGTH)
            val aliasPassword = request.keyAliasPassword.requireField("Key alias password", MAX_PASSWORD_LENGTH)
            val type = request.normalizedType()
            val temporary = temporaryKeyStoreFile(context, type)
            try {
                val declaredSize = runCatching { Os.fstat(inputFd.fileDescriptor).st_size }.getOrDefault(0L)
                if (declaredSize > MAX_KEYSTORE_SIZE_BYTES) {
                    throw IOException("Keystore file is too large: $declaredSize bytes.")
                }
                ParcelFileDescriptor.AutoCloseInputStream(inputFd).use { input ->
                    FileOutputStream(temporary, false).use { output ->
                        input.copyToWithLimit(output, MAX_KEYSTORE_SIZE_BYTES)
                        output.flush()
                    }
                }
                val keyStore = KeyStoreHelper.loadKeyStore(temporary, storePassword.toCharArray())
                if (!keyStore.containsAlias(alias)) {
                    throw IOException("Keystore alias does not exist: $alias")
                }
                if (keyStore.getKey(alias, aliasPassword.toCharArray()) == null) {
                    throw IOException("Keystore key could not be read: $alias")
                }
                return ApkKeyStoreResult(status = ApkKeyStoreResult.STATUS_OK)
            } finally {
                temporary.delete()
            }
        } finally {
            runCatching { inputFd.close() }
        }
    }

    private fun temporaryKeyStoreFile(context: Context, type: String): File {
        val directory = File(context.cacheDir, "keystore-operations").apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Failed to create keystore operation directory: $path")
            }
        }
        return File.createTempFile("keystore-", ".${type.lowercase()}", directory)
    }

    private fun java.io.InputStream.copyToWithLimit(
        output: java.io.OutputStream,
        maxBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) {
                return
            }
            total += read
            if (total > maxBytes) {
                throw IOException("Keystore file exceeds the $maxBytes byte size limit.")
            }
            output.write(buffer, 0, read)
        }
    }

    private fun ApkKeyStoreRequest.normalizedType(): String {
        return when (keyStoreType?.trim()?.uppercase()) {
            ApkKeyStoreRequest.TYPE_BKS -> ApkKeyStoreRequest.TYPE_BKS
            ApkKeyStoreRequest.TYPE_JKS -> ApkKeyStoreRequest.TYPE_JKS
            else -> throw IOException("Unsupported keystore type: $keyStoreType")
        }
    }

    private fun ApkKeyStoreRequest.validateDistinguishedNameLengths() {
        listOf(
            "Common name" to commonName,
            "Organization" to organization,
            "Organizational unit" to organizationalUnit,
            "Country" to country,
            "State" to state,
            "Locality" to locality,
            "Street" to street,
        ).forEach { (label, value) ->
            if (value.orEmpty().length > MAX_DISTINGUISHED_NAME_VALUE_LENGTH) {
                throw IOException("$label is too long.")
            }
        }
    }

    private fun String?.requireField(label: String, maxLength: Int): String {
        return this?.takeIf { it.isNotEmpty() && it.length <= maxLength }
            ?: throw IOException("$label is missing or too long.")
    }

    private const val BUFFER_SIZE = 64 * 1024
    private const val MIN_RSA_KEY_SIZE = 2048
    private const val MAX_RSA_KEY_SIZE = 4096
    private const val MIN_VALIDITY_YEARS = 1
    private const val MAX_VALIDITY_YEARS = 100
    private const val MAX_PASSWORD_LENGTH = 1024
    private const val MAX_ALIAS_LENGTH = 256
    private const val MAX_ALGORITHM_LENGTH = 64
    private const val MAX_DISTINGUISHED_NAME_VALUE_LENGTH = 1024
    private const val MAX_KEYSTORE_SIZE_BYTES = 64L * 1024L * 1024L
    // MD5/SHA1 are retained only to preserve the existing keystore UI and saved workflow
    // compatibility. SHA256withRSA remains the modern choice presented by the host.
    private val SUPPORTED_SIGNATURE_ALGORITHMS = setOf(
        "MD5withRSA",
        "SHA1withRSA",
        "SHA256withRSA",
        "SHA512withRSA",
    )
}
