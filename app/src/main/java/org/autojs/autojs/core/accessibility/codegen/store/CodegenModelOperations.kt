package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageRejectionReason
import org.autojs.autojs.core.accessibility.codegen.model.Rejected
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class ModelImportException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { TOO_LARGE, INVALID_PACKAGE, INCOMPATIBLE }
}

/** Owns the reviewed staging bytes while the duplicate-choice dialog is open. */
class PreparedModelImport internal constructor(
    internal val stage: ModelStaging,
    internal val origin: String,
    val manifest: ModelManifest,
    val sha256: String,
    val sizeBytes: Long,
    val source: ModelSource,
) : Closeable {
    override fun close() = stage.close()
}

/** Synchronous I/O boundary for a management screen; callers execute operations on their worker.
 * zh-CN: 管理界面的同步 I/O 边界, 由调用方在工作线程执行. */
class CodegenModelOperations(
    private val store: ModelStore,
    private val repository: ModelCatalogRepository,
    hostVersionCode: Int,
    temporaryDirectory: File,
) {
    private val reader = ModelPackageReader(hostVersionCode, temporaryDirectory)

    /** The caller owns input. The returned preparation must be installed or closed. */
    fun prepareImport(input: InputStream, origin: String): PreparedModelImport {
        val stage = store.beginStaging()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            FileOutputStream(stage.file).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    checkCancelled()
                    var count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        val next = input.read()
                        if (next < 0) break
                        buffer[0] = next.toByte(); count = 1
                    }
                    size += count
                    if (size > ModelStore.MAX_PACKAGE_BYTES) throw ModelImportException(ModelImportException.Reason.TOO_LARGE, "Model file exceeds 100 MiB")
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
                output.flush(); output.fd.sync()
            }
            checkCancelled()
            val model = when (val result = reader.read(stage.file)) {
                is ModelPackage -> result
                is Rejected -> {
                    val reason = when (result.reason) {
                        ModelPackageRejectionReason.HOST_TOO_OLD, ModelPackageRejectionReason.INCOMPATIBLE_SCHEMA,
                        ModelPackageRejectionReason.FEATURE_MISMATCH -> ModelImportException.Reason.INCOMPATIBLE
                        ModelPackageRejectionReason.PACKAGE_TOO_LARGE -> ModelImportException.Reason.TOO_LARGE
                        else -> ModelImportException.Reason.INVALID_PACKAGE
                    }
                    throw ModelImportException(reason, "Model package rejected: ${result.detail}")
                }
            }
            checkCancelled()
            val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            val official = listOfNotNull(repository.loadBestAvailable(), repository.bootstrap()).any { catalog ->
                catalog.models.any { entry -> entry.versions.any { it.artifact.sha256 == sha } }
            }
            return PreparedModelImport(stage, origin, model.manifest, sha, size,
                if (official) ModelSource.OFFICIAL else ModelSource.CUSTOM)
        } catch (failure: Throwable) {
            runCatching { stage.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    fun duplicates(prepared: PreparedModelImport): DuplicateCheck.Result = DuplicateCheck.evaluate(
        IncomingModel.fromManifest(prepared.manifest, prepared.sha256), store.listInstalled(), store.current()?.key,
    )

    /** Revalidates the exact previewed bytes, including the chosen replacement's current existence. */
    fun install(prepared: PreparedModelImport, replaceKey: String? = null): InstalledModel =
        store.installStaged(prepared.stage, prepared.origin, replaceKey, prepared.sha256, prepared.sizeBytes) { manifest ->
            if (manifest != prepared.manifest) throw ModelImportException(ModelImportException.Reason.INVALID_PACKAGE, "Prepared model changed")
        }

    fun readModelCard(key: String): String = store.readModelCard(key)

    private fun checkCancelled() { if (Thread.currentThread().isInterrupted) throw CancellationException("Model import cancelled") }
}
