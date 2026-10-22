package org.autojs.autojs.engine

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Request-scoped host provider for encrypted Node.js module source.
 *
 * The runtime must resolve a concrete candidate before calling this provider.
 * The provider never enumerates the project. It re-authorizes the exact path,
 * probes only its encrypted header, and returns decrypted bytes through a pipe.
 */
internal class NodeJsEncryptedModuleSourceProvider(
    rootDirectory: File,
    private val executionId: String,
    timeoutMs: Long,
    initialSourceCount: Int = 0,
    initialSourceBytes: Long = 0L,
    private val afterCanonicalizationForTest: (() -> Unit)? = null,
    private val afterOpenForTest: (() -> Unit)? = null,
) : INodeJsModuleSourceProvider.Stub() {

    init {
        require(executionId.isNotBlank()) { "Module-source provider execution id must not be blank." }
        require(initialSourceCount in 0..SOURCE_MAX_COUNT) {
            "Initial module-source count is outside the allowed range: $initialSourceCount."
        }
        require(initialSourceBytes in 0L..TOTAL_SOURCE_MAX_BYTES.toLong()) {
            "Initial module-source bytes are outside the allowed range: $initialSourceBytes."
        }
    }

    // Snapshot first: another packaged project may replace the process-global
    // encryption key while this execution is still resolving modules.
    private val decryptor = ScriptEncryption.snapshotDecryptor()
    private val root = RootIdentity.capture(rootDirectory)
        ?: throw IllegalArgumentException(
            "Module-source provider root is unavailable or is not a directory: ${rootDirectory.absolutePath}.",
        )
    private val providerId = "module-source-$executionId"
    private val startedAt = SystemClock.elapsedRealtime()
    private val deadline = deadlineAfter(startedAt, timeoutMs.coerceAtLeast(1L))
    private val resolutionLock = Any()
    private var usedSourceCount = initialSourceCount
    private var usedSourceBytes = initialSourceBytes

    private val cancelled = AtomicBoolean(false)
    private val cancellationReason = AtomicReference("")
    private val writerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6NodeModulePipe-${safeName(executionId)}").apply {
            isDaemon = true
        }
    }
    private val pipeDeadlineExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AutoJs6NodeModulePipeDeadline-${safeName(executionId)}").apply {
            isDaemon = true
        }
    }
    private val activeWriteDescriptors = ConcurrentHashMap.newKeySet<ParcelFileDescriptor>()

    private val requestCount = AtomicInteger(0)
    // Requests that produced no source: not_found and not_encrypted.
    private val probeRequestCount = AtomicInteger(0)
    private val resolvedSourceCount = AtomicInteger(0)
    private val decryptedSourceCount = AtomicInteger(0)
    private val notEncryptedCount = AtomicInteger(0)
    private val notFoundCount = AtomicInteger(0)
    private val deniedCount = AtomicInteger(0)
    private val cancelledCount = AtomicInteger(0)
    private val timedOutCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val budgetRejectedCount = AtomicInteger(0)
    private val headerProbeCount = AtomicInteger(0)
    private val headerBytesRead = AtomicLong(0L)
    private val encryptedPayloadBytesRead = AtomicLong(0L)
    private val decryptedSourceBytes = AtomicLong(0L)
    private val identityReadFailureCount = AtomicInteger(0)
    private val identityMismatchCount = AtomicInteger(0)
    private val pipeFailureCount = AtomicInteger(0)
    private val pipeTimeoutCount = AtomicInteger(0)
    private val cancelSignalCount = AtomicInteger(0)
    private val totalElapsedMs = AtomicLong(0L)
    private val lastStatus = AtomicReference("")
    private val lastErrorCode = AtomicReference("")
    private val lastDenialReason = AtomicReference("")

    override fun resolveModuleSource(request: Bundle?): Bundle {
        val requestStartedAt = SystemClock.elapsedRealtime()
        val currentRequestCount = requestCount.incrementAndGet()
        val requestId = request
            ?.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID)
            .orEmpty()
        // Probe budget first: it is the loose ceiling that ordinary resolution
        // lives under. The absolute ceiling below only guards against a runtime
        // that never stops asking.
        val result = if (probeRequestCount.get() >= PROBE_MAX_COUNT) {
            budgetRejectedCount.incrementAndGet()
            budgetExceeded(budgetMessage("Module-source provider zero-yield probe budget is exhausted."))
        } else if (currentRequestCount > REQUEST_MAX_COUNT) {
            budgetRejectedCount.incrementAndGet()
            budgetExceeded(budgetMessage("Module-source provider request-count budget is exhausted."))
        } else {
            synchronized(resolutionLock) {
                runCatching { resolveLocked(request) }
                    .getOrElse { error ->
                        failed(
                            message = error.message ?: error.javaClass.name,
                            denialReason = "provider_exception",
                        )
                    }
            }
        }
        val elapsedMs = (SystemClock.elapsedRealtime() - requestStartedAt).coerceAtLeast(0L)
        recordResult(result, elapsedMs)
        return result.toBundle(requestId, elapsedMs)
    }

    /**
     * Appends the per-category counters so an exhausted budget is self
     * explanatory. Without them a plaintext project reports an exhausted source
     * budget while having resolved zero sources, which reads as a contradiction.
     */
    private fun budgetMessage(reason: String): String = buildString {
        append(reason)
        append(" (requests=").append(requestCount.get())
        append(", probes=").append(probeRequestCount.get())
        append('/').append(PROBE_MAX_COUNT)
        append(", notFound=").append(notFoundCount.get())
        append(", notEncrypted=").append(notEncryptedCount.get())
        append(", decrypted=").append(decryptedSourceCount.get())
        append('/').append(SOURCE_MAX_COUNT)
        append(", sourceBytes=").append(decryptedSourceBytes.get())
        append('/').append(TOTAL_SOURCE_MAX_BYTES)
        append(')')
    }

    override fun getNativeDiagnostics(): Bundle {
        val budgetSnapshot = synchronized(resolutionLock) {
            usedSourceCount to usedSourceBytes
        }
        val rootStatus = root.currentStatus().diagnosticName
        val values = linkedMapOf(
            "embedded_script.module_provider.contract_version" to
                    PROVIDER_CONTRACT_VERSION.toString(),
            "embedded_script.module_provider.id" to providerId,
            "embedded_script.module_provider.runtime_module" to
                    NodeJsRuntimeContract.RUNTIME_MODULE_SOURCE_PROVIDER_NAME,
            "embedded_script.module_provider.request_count" to requestCount.get().toString(),
            "embedded_script.module_provider.probe_request_count" to probeRequestCount.get().toString(),
            "embedded_script.module_provider.resolved_source_count" to
                    resolvedSourceCount.get().toString(),
            "embedded_script.module_provider.decrypted_source_count" to
                    decryptedSourceCount.get().toString(),
            "embedded_script.module_provider.not_encrypted_count" to notEncryptedCount.get().toString(),
            "embedded_script.module_provider.not_found_count" to notFoundCount.get().toString(),
            "embedded_script.module_provider.denied_count" to deniedCount.get().toString(),
            "embedded_script.module_provider.cancelled_count" to cancelledCount.get().toString(),
            "embedded_script.module_provider.timed_out_count" to timedOutCount.get().toString(),
            "embedded_script.module_provider.failed_count" to failedCount.get().toString(),
            "embedded_script.module_provider.budget_rejected_count" to
                    budgetRejectedCount.get().toString(),
            "embedded_script.module_provider.source_bytes" to decryptedSourceBytes.get().toString(),
            "embedded_script.module_provider.elapsed_ms" to totalElapsedMs.get().toString(),
            "embedded_script.module_provider.header_probe_count" to headerProbeCount.get().toString(),
            "embedded_script.module_provider.header_bytes_read" to headerBytesRead.get().toString(),
            "embedded_script.module_provider.encrypted_payload_bytes_read" to
                    encryptedPayloadBytesRead.get().toString(),
            "embedded_script.module_provider.identity_read_failure_count" to
                    identityReadFailureCount.get().toString(),
            "embedded_script.module_provider.identity_mismatch_count" to
                    identityMismatchCount.get().toString(),
            "embedded_script.module_provider.pipe_failure_count" to pipeFailureCount.get().toString(),
            "embedded_script.module_provider.pipe_timeout_count" to pipeTimeoutCount.get().toString(),
            "embedded_script.module_provider.active_pipe_count" to
                    activeWriteDescriptors.size.toString(),
            "embedded_script.module_provider.cancel_signal_count" to cancelSignalCount.get().toString(),
            "embedded_script.module_provider.cancelled" to cancelled.get().toString(),
            "embedded_script.module_provider.root_identity" to rootStatus,
            "embedded_script.module_provider.used_source_count" to budgetSnapshot.first.toString(),
            "embedded_script.module_provider.used_source_bytes" to budgetSnapshot.second.toString(),
            "embedded_script.module_provider.source_count_limit" to SOURCE_MAX_COUNT.toString(),
            "embedded_script.module_provider.request_count_limit" to REQUEST_MAX_COUNT.toString(),
            "embedded_script.module_provider.probe_request_count_limit" to PROBE_MAX_COUNT.toString(),
            "embedded_script.module_provider.source_bytes_limit" to SOURCE_MAX_BYTES.toString(),
            "embedded_script.module_provider.aggregate_source_bytes_limit" to
                    TOTAL_SOURCE_MAX_BYTES.toString(),
            "embedded_script.module_provider.last_status" to lastStatus.get(),
            "embedded_script.module_provider.last_error_code" to lastErrorCode.get(),
            "embedded_script.module_provider.denial_reason" to lastDenialReason.get(),
        )
        return Bundle().apply {
            putInt(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                PROVIDER_CONTRACT_VERSION,
            )
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ID, providerId)
            putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId)
            putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_MAX_BYTES, SOURCE_MAX_BYTES)
            putLong(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_TOTAL_MAX_BYTES,
                TOTAL_SOURCE_MAX_BYTES.toLong(),
            )
            putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_MAX_COUNT, SOURCE_MAX_COUNT)
            putInt(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_MAX_COUNT,
                REQUEST_MAX_COUNT,
            )
            putInt(KEY_PROBE_MAX_COUNT, PROBE_MAX_COUNT)
            values.forEach(::putString)
            putStringArray(
                NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD,
                values.map { (key, value) -> "$key=$value" }.toTypedArray(),
            )
        }
    }

    override fun cancel(reason: String?) {
        cancelSignalCount.incrementAndGet()
        cancellationReason.compareAndSet("", reason.orEmpty().ifBlank { "cancelled" })
        cancelled.set(true)
        closeActivePipes(cancellationReason.get())
        writerExecutor.shutdownNow()
        pipeDeadlineExecutor.shutdownNow()
    }

    private fun resolveLocked(request: Bundle?): ProviderResult {
        abortResult(deadline)?.let { return it }
        if (request == null) {
            return invalidRequest("Module-source provider request is missing.", "missing_request")
        }
        val requestVersion = request.getInt(
            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
            0,
        )
        if (requestVersion != PROVIDER_CONTRACT_VERSION) {
            return invalidRequest(
                "Unsupported module-source provider request version: $requestVersion.",
                "contract_version_mismatch",
            )
        }
        val requestId = request
            .getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return invalidRequest("Module-source provider request id is missing.", "missing_request_id")
        if (requestId.length > REQUEST_ID_MAX_CHARS) {
            return invalidRequest("Module-source provider request id is too long.", "request_id_too_long")
        }
        val requestedExecutionId = request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID).orEmpty()
        if (requestedExecutionId != executionId) {
            return denied(
                message = "Module-source provider execution id mismatch.",
                reason = "execution_id_mismatch",
            )
        }
        val requestedPath = request
            .getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: return invalidRequest("Module-source provider path is missing.", "missing_path")
        if (requestedPath.length > REQUEST_PATH_MAX_CHARS) {
            return denied("Module-source provider path is too long.", "path_too_long")
        }
        if ('\u0000' in requestedPath) {
            return denied("Module-source provider path contains a NUL byte.", "nul_byte")
        }
        val now = SystemClock.elapsedRealtime()
        val requestedTimeoutMs = request
            .takeIf { it.containsKey(NodeJsRuntimeContract.KEY_TIMEOUT_MS) }
            ?.getLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS)
            ?.coerceAtLeast(1L)
            ?: (deadline - now).coerceAtLeast(1L)
        val requestDeadline = minOf(deadline, deadlineAfter(now, requestedTimeoutMs))
        abortResult(requestDeadline)?.let { return it }

        when (root.currentStatus()) {
            IdentityStatus.MATCH -> Unit
            IdentityStatus.READ_FAILURE -> {
                identityReadFailureCount.incrementAndGet()
                return denied("Module-source provider root is unavailable.", "root_identity_read_failed")
            }
            IdentityStatus.MISMATCH -> {
                identityMismatchCount.incrementAndGet()
                return denied("Module-source provider root identity changed.", "root_identity_changed")
            }
        }

        val requestedFile = File(requestedPath).let { file ->
            if (file.isAbsolute) file else File(root.canonicalFile, requestedPath)
        }
        val canonicalFile = try {
            requestedFile.canonicalFile
        } catch (error: IOException) {
            return failed(
                message = error.message ?: "Unable to canonicalize module-source path.",
                denialReason = "canonicalization_failed",
            )
        } catch (error: SecurityException) {
            return denied(
                message = error.message ?: "Module-source path canonicalization was denied.",
                reason = "canonicalization_denied",
            )
        }
        val canonicalPath = canonicalFile.absolutePath
        if (!root.contains(canonicalPath)) {
            return denied("Module-source path escapes the authorized root.", "canonical_path_escape")
        }
        if (canonicalFile.extension.lowercase() !in SUPPORTED_SOURCE_EXTENSIONS) {
            return denied("Module-source path has an unsupported extension.", "unsupported_extension")
        }
        runCatching { afterCanonicalizationForTest?.invoke() }
            .onFailure {
                return failed("Module-source test hook failed.", "test_hook_failed")
            }

        val pathStat = try {
            Os.lstat(canonicalPath)
        } catch (error: ErrnoException) {
            return resultForErrno(error, "Unable to inspect module-source path.")
        } catch (error: Exception) {
            identityReadFailureCount.incrementAndGet()
            return failed(
                message = error.message ?: "Unable to inspect module-source path.",
                denialReason = "file_identity_read_failed",
            )
        }
        if (!OsConstants.S_ISREG(pathStat.st_mode)) {
            return denied("Module-source candidate is not a regular file.", "not_regular_file")
        }
        val expectedIdentity = FileIdentity.capture(pathStat)

        var parcelDescriptor: ParcelFileDescriptor? = null
        val descriptor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                Os.open(
                    canonicalPath,
                    OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                    0,
                )
            } else {
                ParcelFileDescriptor.open(
                    canonicalFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
                ).also { parcelDescriptor = it }.fileDescriptor
            }
        } catch (error: ErrnoException) {
            return resultForErrno(error, "Unable to open module-source path.")
        } catch (error: Exception) {
            return failed(
                message = error.message ?: "Unable to open module-source path.",
                denialReason = "open_failed",
            )
        }
        val input = try {
            FileInputStream(descriptor)
        } catch (error: Exception) {
            runCatching { parcelDescriptor?.close() ?: Os.close(descriptor) }
            return failed(
                message = error.message ?: "Unable to read module-source path.",
                denialReason = "stream_open_failed",
            )
        }

        val encryptedPayload = try {
            input.use { stream ->
                val openedStat = try {
                    Os.fstat(stream.fd)
                } catch (error: Exception) {
                    identityReadFailureCount.incrementAndGet()
                    return failed(
                        message = error.message ?: "Unable to inspect opened module-source file.",
                        denialReason = "opened_file_identity_read_failed",
                    )
                }
                if (!OsConstants.S_ISREG(openedStat.st_mode) || !expectedIdentity.matches(openedStat)) {
                    identityMismatchCount.incrementAndGet()
                    return denied("Module-source file identity changed before open.", "file_identity_changed")
                }
                runCatching { afterOpenForTest?.invoke() }
                    .onFailure {
                        return failed("Module-source test hook failed.", "test_hook_failed")
                    }
                val openedCanonicalPath = openedDescriptorPath(stream.fd)
                if (openedCanonicalPath != canonicalPath || !root.contains(openedCanonicalPath)) {
                    identityMismatchCount.incrementAndGet()
                    return denied(
                        "Opened module-source file escapes or differs from the authorized candidate.",
                        "opened_file_path_mismatch",
                    )
                }

                headerProbeCount.incrementAndGet()
                val header = ByteArray(EncryptedScriptFileHeader.BLOCK_SIZE)
                var headerOffset = 0
                while (headerOffset < header.size) {
                    abortResult(requestDeadline)?.let { return it }
                    val count = stream.read(header, headerOffset, header.size - headerOffset)
                    if (count < 0) break
                    if (count == 0) continue
                    headerOffset += count
                    headerBytesRead.addAndGet(count.toLong())
                }
                if (
                    headerOffset != EncryptedScriptFileHeader.BLOCK_SIZE ||
                    !EncryptedScriptFileHeader.isValidFile(header)
                ) {
                    return ProviderResult(
                        status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED,
                        resolvedPath = canonicalPath,
                    )
                }

                if (usedSourceCount >= SOURCE_MAX_COUNT) {
                    budgetRejectedCount.incrementAndGet()
                    return budgetExceeded("Module-source count budget is exhausted.")
                }
                val remainingBytes = TOTAL_SOURCE_MAX_BYTES.toLong() - usedSourceBytes
                if (remainingBytes <= 0L) {
                    budgetRejectedCount.incrementAndGet()
                    return budgetExceeded("Module-source aggregate byte budget is exhausted.")
                }
                val maximumCleartextBytes = minOf(SOURCE_MAX_BYTES.toLong(), remainingBytes)
                val maximumCiphertextBytes = maximumCleartextBytes + AES_CIPHER_BLOCK_BYTES
                val knownPayloadBytes = (openedStat.st_size - EncryptedScriptFileHeader.BLOCK_SIZE)
                    .coerceAtLeast(0L)
                if (knownPayloadBytes > maximumCiphertextBytes) {
                    budgetRejectedCount.incrementAndGet()
                    return budgetExceeded("Encrypted module source exceeds the remaining byte budget.")
                }

                val output = ByteArrayOutputStream(
                    knownPayloadBytes.coerceAtMost(READ_BUFFER_BYTES.toLong()).toInt(),
                )
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var payloadBytes = 0L
                while (true) {
                    abortResult(requestDeadline)?.let { return it }
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    payloadBytes += count
                    if (payloadBytes > maximumCiphertextBytes) {
                        budgetRejectedCount.incrementAndGet()
                        return budgetExceeded("Encrypted module source exceeds the remaining byte budget.")
                    }
                    output.write(buffer, 0, count)
                }
                encryptedPayloadBytesRead.addAndGet(payloadBytes)

                val completedStat = try {
                    Os.fstat(stream.fd)
                } catch (error: Exception) {
                    identityReadFailureCount.incrementAndGet()
                    return failed(
                        message = error.message ?: "Unable to revalidate opened module-source file.",
                        denialReason = "opened_file_revalidation_failed",
                    )
                }
                if (
                    !expectedIdentity.matches(completedStat) ||
                    completedStat.st_size != openedStat.st_size
                ) {
                    identityMismatchCount.incrementAndGet()
                    return denied("Module-source file identity changed while reading.", "file_identity_changed")
                }
                if (openedDescriptorPath(stream.fd) != openedCanonicalPath) {
                    identityMismatchCount.incrementAndGet()
                    return denied(
                        "Opened module-source path changed while reading.",
                        "opened_file_path_changed",
                    )
                }
                output.toByteArray()
            }
        } catch (error: IOException) {
            return failed(
                message = error.message ?: "Unable to read encrypted module source.",
                denialReason = "read_failed",
            )
        } catch (error: SecurityException) {
            return denied(
                message = error.message ?: "Reading encrypted module source was denied.",
                reason = "read_denied",
            )
        } finally {
            // The stream borrows the descriptor on Android, including early plaintext returns.
            runCatching { parcelDescriptor?.close() ?: Os.close(descriptor) }
        }

        abortResult(requestDeadline)?.let { return it }
        if (root.currentStatus() != IdentityStatus.MATCH) {
            identityMismatchCount.incrementAndGet()
            return denied("Module-source provider root identity changed while reading.", "root_identity_changed")
        }
        val currentCanonicalPath = try {
            requestedFile.canonicalFile.absolutePath
        } catch (_: Exception) {
            identityReadFailureCount.incrementAndGet()
            return denied("Module-source path could not be revalidated.", "path_revalidation_failed")
        }
        if (currentCanonicalPath != canonicalPath || !root.contains(currentCanonicalPath)) {
            identityMismatchCount.incrementAndGet()
            return denied("Module-source path changed while reading.", "canonical_path_changed")
        }

        val cleartext = try {
            decryptor.decrypt(encryptedPayload)
        } catch (error: Exception) {
            return failed(
                message = error.message ?: "Unable to decrypt module source.",
                denialReason = "decrypt_failed",
            )
        }
        val remainingBytes = TOTAL_SOURCE_MAX_BYTES.toLong() - usedSourceBytes
        if (cleartext.size > SOURCE_MAX_BYTES || cleartext.size.toLong() > remainingBytes) {
            budgetRejectedCount.incrementAndGet()
            return budgetExceeded("Decrypted module source exceeds the remaining byte budget.")
        }
        abortResult(requestDeadline)?.let { return it }

        val sourceDescriptor = try {
            createSourcePipe(cleartext, requestDeadline)
        } catch (error: Exception) {
            return failed(
                message = error.message ?: "Unable to create module-source pipe.",
                denialReason = "pipe_creation_failed",
            )
        }
        abortResult(requestDeadline)?.let { aborted ->
            runCatching { sourceDescriptor.close() }
            return aborted
        }
        usedSourceCount += 1
        usedSourceBytes += cleartext.size.toLong()
        return ProviderResult(
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED,
            resolvedPath = canonicalPath,
            sourceBytes = cleartext.size.toLong(),
            sourceDescriptor = sourceDescriptor,
        )
    }

    private fun createSourcePipe(
        source: ByteArray,
        requestDeadline: Long,
    ): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readDescriptor = pipe[0]
        val writeDescriptor = pipe[1]
        activeWriteDescriptors += writeDescriptor
        val deadlineClose = try {
            pipeDeadlineExecutor.schedule(
                {
                    if (activeWriteDescriptors.remove(writeDescriptor)) {
                        pipeTimeoutCount.incrementAndGet()
                        runCatching {
                            writeDescriptor.closeWithError("Module-source provider pipe timed out.")
                        }
                    }
                },
                (requestDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
        } catch (error: RejectedExecutionException) {
            activeWriteDescriptors -= writeDescriptor
            runCatching { readDescriptor.close() }
            runCatching { writeDescriptor.closeWithError("Module-source provider is cancelled.") }
            throw error
        }
        try {
            writerExecutor.execute {
                var output: ParcelFileDescriptor.AutoCloseOutputStream? = null
                try {
                    output = ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor)
                    var offset = 0
                    while (offset < source.size) {
                        if (cancelled.get()) {
                            throw InterruptedIOException("Module-source provider was cancelled.")
                        }
                        if (SystemClock.elapsedRealtime() >= requestDeadline) {
                            pipeTimeoutCount.incrementAndGet()
                            throw InterruptedIOException("Module-source provider pipe timed out.")
                        }
                        val count = minOf(PIPE_WRITE_BYTES, source.size - offset)
                        output.write(source, offset, count)
                        offset += count
                    }
                    output.close()
                    output = null
                } catch (error: Exception) {
                    pipeFailureCount.incrementAndGet()
                    runCatching {
                        writeDescriptor.closeWithError(
                            (error.message ?: error.javaClass.name).take(PIPE_ERROR_MAX_CHARS),
                        )
                    }
                } finally {
                    deadlineClose.cancel(false)
                    activeWriteDescriptors -= writeDescriptor
                    runCatching { output?.close() }
                }
            }
        } catch (error: RejectedExecutionException) {
            deadlineClose.cancel(false)
            activeWriteDescriptors -= writeDescriptor
            runCatching { readDescriptor.close() }
            runCatching { writeDescriptor.closeWithError("Module-source provider is cancelled.") }
            throw error
        } catch (error: Exception) {
            deadlineClose.cancel(false)
            activeWriteDescriptors -= writeDescriptor
            runCatching { readDescriptor.close() }
            runCatching { writeDescriptor.closeWithError("Module-source pipe writer failed.") }
            throw error
        }
        return readDescriptor
    }

    private fun closeActivePipes(reason: String) {
        activeWriteDescriptors.concurrentSnapshot().forEach { descriptor ->
            runCatching {
                descriptor.closeWithError(reason.take(PIPE_ERROR_MAX_CHARS))
            }
            activeWriteDescriptors -= descriptor
        }
    }

    private fun abortResult(requestDeadline: Long): ProviderResult? {
        if (cancelled.get()) {
            return ProviderResult(
                status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED,
                errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_CANCELLED,
                errorMessage = cancellationReason.get().ifBlank { "Module-source provider was cancelled." },
                denialReason = "cancelled",
            )
        }
        if (SystemClock.elapsedRealtime() >= requestDeadline) {
            return ProviderResult(
                status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT,
                errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_TIMED_OUT,
                errorMessage = "Module-source provider request timed out.",
                denialReason = "timed_out",
            )
        }
        return null
    }

    private fun resultForErrno(error: ErrnoException, fallbackMessage: String): ProviderResult {
        return when (error.errno) {
            OsConstants.ENOENT,
            OsConstants.ENOTDIR,
            -> ProviderResult(
                status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND,
                errorMessage = "",
                denialReason = "not_found",
            )
            OsConstants.EACCES,
            OsConstants.EPERM,
            OsConstants.ELOOP,
            -> denied(
                message = error.message ?: fallbackMessage,
                reason = "open_denied_errno_${error.errno}",
            )
            else -> failed(
                message = error.message ?: fallbackMessage,
                denialReason = "io_errno_${error.errno}",
            )
        }
    }

    private fun denied(message: String, reason: String): ProviderResult {
        return ProviderResult(
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED,
            errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_DENIED,
            errorMessage = message,
            denialReason = reason,
        )
    }

    private fun failed(message: String, denialReason: String): ProviderResult {
        return ProviderResult(
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
            errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED,
            errorMessage = message,
            denialReason = denialReason,
        )
    }

    private fun invalidRequest(message: String, denialReason: String): ProviderResult {
        return ProviderResult(
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
            errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
            errorMessage = message,
            denialReason = denialReason,
        )
    }

    private fun budgetExceeded(message: String): ProviderResult {
        return ProviderResult(
            status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
            errorCode = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED,
            errorMessage = message,
            denialReason = "budget_exceeded",
        )
    }

    private fun recordResult(result: ProviderResult, elapsedMs: Long) {
        totalElapsedMs.addAndGet(elapsedMs)
        lastStatus.set(result.status)
        lastErrorCode.set(result.errorCode)
        lastDenialReason.set(result.denialReason)
        when (result.status) {
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED -> {
                resolvedSourceCount.incrementAndGet()
                decryptedSourceCount.incrementAndGet()
                decryptedSourceBytes.addAndGet(result.sourceBytes)
            }
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED -> {
                resolvedSourceCount.incrementAndGet()
                notEncryptedCount.incrementAndGet()
                probeRequestCount.incrementAndGet()
            }
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND -> {
                notFoundCount.incrementAndGet()
                probeRequestCount.incrementAndGet()
            }
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED -> deniedCount.incrementAndGet()
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED ->
                cancelledCount.incrementAndGet()
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT ->
                timedOutCount.incrementAndGet()
            else -> failedCount.incrementAndGet()
        }
    }

    private data class ProviderResult(
        val status: String,
        val resolvedPath: String = "",
        val sourceBytes: Long = 0L,
        val sourceDescriptor: ParcelFileDescriptor? = null,
        val errorCode: String = "",
        val errorMessage: String = "",
        val denialReason: String = "",
    ) {

        fun toBundle(requestId: String, elapsedMs: Long): Bundle {
            return Bundle().apply {
                putInt(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                    PROVIDER_CONTRACT_VERSION,
                )
                putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID, requestId)
                putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS, status)
                putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH, resolvedPath)
                putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, sourceBytes)
                putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS, elapsedMs)
                putString(NodeJsRuntimeContract.KEY_ERROR_CODE, errorCode)
                putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, errorMessage)
                putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DENIAL_REASON, denialReason)
                sourceDescriptor?.let {
                    putParcelable(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD, it)
                }
            }
        }
    }

    private data class RootIdentity(
        val canonicalFile: File,
        val canonicalPath: String,
        val identity: FileIdentity,
    ) {

        fun contains(path: String): Boolean {
            return path == canonicalPath || path.startsWith(canonicalPathWithSeparator)
        }

        fun currentStatus(): IdentityStatus {
            val stat = try {
                Os.lstat(canonicalPath)
            } catch (_: Exception) {
                return IdentityStatus.READ_FAILURE
            }
            return if (OsConstants.S_ISDIR(stat.st_mode) && identity.matches(stat)) {
                IdentityStatus.MATCH
            } else {
                IdentityStatus.MISMATCH
            }
        }

        private val canonicalPathWithSeparator: String
            get() = if (canonicalPath.endsWith(File.separator)) canonicalPath else canonicalPath + File.separator

        companion object {

            fun capture(rootDirectory: File): RootIdentity? {
                val canonicalFile = try {
                    rootDirectory.canonicalFile
                } catch (_: Exception) {
                    return null
                }
                val canonicalPath = canonicalFile.absolutePath
                val stat = try {
                    Os.lstat(canonicalPath)
                } catch (_: Exception) {
                    return null
                }
                if (!OsConstants.S_ISDIR(stat.st_mode)) {
                    return null
                }
                return RootIdentity(
                    canonicalFile = canonicalFile,
                    canonicalPath = canonicalPath,
                    identity = FileIdentity.capture(stat),
                )
            }
        }
    }

    private data class FileIdentity(
        val device: Long,
        val inode: Long,
    ) {

        fun matches(stat: StructStat): Boolean {
            return device == stat.st_dev && inode == stat.st_ino
        }

        companion object {

            fun capture(stat: StructStat): FileIdentity {
                return FileIdentity(device = stat.st_dev, inode = stat.st_ino)
            }
        }
    }

    private enum class IdentityStatus(val diagnosticName: String) {
        MATCH("match"),
        READ_FAILURE("read_failure"),
        MISMATCH("mismatch"),
    }

    private companion object {

        // The shared contract in nodejs-api advanced to v2; this provider still
        // speaks v1 resolve semantics and the plugin negotiates down to it.
        const val PROVIDER_CONTRACT_VERSION =
            NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_PROVIDER_CONTRACT_VERSION

        // Host-local diagnostics key; the shared v2 contract no longer carries it.
        const val KEY_PROBE_MAX_COUNT = "moduleSourceProviderProbeMaxCount"

        const val SOURCE_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES
        const val TOTAL_SOURCE_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES
        const val SOURCE_MAX_COUNT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT
        const val PROBE_MAX_COUNT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_PROBE_MAX_COUNT
        const val REQUEST_MAX_COUNT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_REQUEST_MAX_COUNT
        const val REQUEST_ID_MAX_CHARS = 256
        const val REQUEST_PATH_MAX_CHARS = 4_096
        const val AES_CIPHER_BLOCK_BYTES = 16L
        const val READ_BUFFER_BYTES = 32 * 1024
        const val PIPE_WRITE_BYTES = 32 * 1024
        const val PIPE_ERROR_MAX_CHARS = 256
        val SUPPORTED_SOURCE_EXTENSIONS = setOf("js", "cjs", "cts", "ts", "json", "mjs", "mts")

        fun deadlineAfter(now: Long, timeoutMs: Long): Long {
            return if (timeoutMs >= Long.MAX_VALUE - now) Long.MAX_VALUE else now + timeoutMs
        }

        fun safeName(value: String): String {
            return value
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
                .take(64)
                .ifBlank { "request" }
        }

        fun openedDescriptorPath(descriptor: java.io.FileDescriptor): String? {
            return try {
                ParcelFileDescriptor.dup(descriptor).use { duplicate ->
                    Os.readlink("/proc/self/fd/${duplicate.fd}")
                        .takeIf { it.startsWith(File.separator) && !it.endsWith(" (deleted)") }
                        ?.let { File(it).absolutePath }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
