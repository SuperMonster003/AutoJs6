package org.autojs.autojs.apkbuilder.template

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreRequest
import org.autojs.plugin.apkbuilder.template.ApkKeyStoreResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkBuilderKeyStoreClient {

    @JvmStatic
    fun createBlocking(
        context: Context,
        outputFile: File,
        request: ApkKeyStoreRequest,
    ): ApkKeyStoreResult {
        if (outputFile.exists()) {
            return failed("File already exists: ${outputFile.path}")
        }
        outputFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                return failed("Failed to create keystore directory: ${parent.path}")
            }
        }
        request.operation = ApkKeyStoreRequest.OPERATION_CREATE
        val parent = outputFile.parentFile
            ?: return failed("Keystore output path has no parent directory: ${outputFile.path}")
        val temporary = runCatching {
            File.createTempFile(".${outputFile.name}.", ".tmp", parent)
        }.getOrElse { error ->
            return failed(error.message ?: error.toString())
        }
        return try {
            val result = executeWithFileDescriptor(
                context = context,
                file = temporary,
                mode = ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY,
                request = request,
            )
            if (!result.isSuccess) {
                result
            } else if (!publishWithoutOverwrite(temporary, outputFile)) {
                failed("File already exists or could not be published safely: ${outputFile.path}")
            } else {
                result
            }
        } finally {
            temporary.delete()
        }
    }

    @JvmStatic
    fun verifyBlocking(
        context: Context,
        inputFile: File,
        request: ApkKeyStoreRequest,
    ): ApkKeyStoreResult {
        if (!inputFile.isFile) {
            return failed("Keystore file does not exist: ${inputFile.path}")
        }
        if (inputFile.length() > MAX_KEYSTORE_SIZE_BYTES) {
            return failed("Keystore file exceeds the $MAX_KEYSTORE_SIZE_BYTES byte size limit.")
        }
        request.operation = ApkKeyStoreRequest.OPERATION_VERIFY
        return executeWithFileDescriptor(
            context = context,
            file = inputFile,
            mode = ParcelFileDescriptor.MODE_READ_ONLY,
            request = request,
        )
    }

    private fun executeWithFileDescriptor(
        context: Context,
        file: File,
        mode: Int,
        request: ApkKeyStoreRequest,
    ): ApkKeyStoreResult {
        return runCatching {
            ParcelFileDescriptor.open(file, mode).use { descriptor ->
                request.keyStoreFd = descriptor
                runBlocking(Dispatchers.IO) {
                    ApkBuilderTemplatePluginHost.manageKeyStore(
                        context = context.applicationContext,
                        request = request,
                        preferredPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
                    )
                } ?: ApkKeyStoreResult(
                    status = ApkKeyStoreResult.STATUS_UNAVAILABLE,
                    message = "The enabled official APK Builder plugin does not support keystore operations.",
                )
            }
        }.getOrElse { error ->
            failed(error.message ?: error.toString())
        }.also {
            request.keyStoreFd = null
        }
    }

    private fun failed(message: String): ApkKeyStoreResult {
        return ApkKeyStoreResult(
            status = ApkKeyStoreResult.STATUS_FAILED,
            message = message,
        )
    }

    /**
     * A hard-link publication is atomic and fails if the destination appeared concurrently.
     * Some emulated-storage file systems reject hard links, so O_EXCL is the API-21-compatible
     * no-replace fallback. A failed fallback removes only the file descriptor it created.
     */
    private fun publishWithoutOverwrite(temporary: File, outputFile: File): Boolean {
        return try {
            Os.link(temporary.absolutePath, outputFile.absolutePath)
            true
        } catch (_: Exception) {
            var createdDestination = false
            try {
                val destinationFd = Os.open(
                    outputFile.absolutePath,
                    OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL,
                    OWNER_READ_WRITE_MODE,
                )
                createdDestination = true
                try {
                    FileInputStream(temporary).use { input ->
                        FileOutputStream(destinationFd).use { output ->
                            input.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                } finally {
                    runCatching { Os.close(destinationFd) }
                }
                true
            } catch (_: Exception) {
                if (createdDestination) {
                    outputFile.delete()
                }
                false
            }
        }
    }

    private const val OWNER_READ_WRITE_MODE = 0x180 // 0600
    private const val MAX_KEYSTORE_SIZE_BYTES = 64L * 1024L * 1024L
}
