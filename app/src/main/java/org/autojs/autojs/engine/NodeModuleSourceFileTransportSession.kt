package org.autojs.autojs.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

/**
 * Per-execution transport between the synchronous Node module resolver and the
 * host-owned encrypted source provider. The response source itself travels over
 * a file descriptor so Binder's transaction limit is never part of the source
 * budget.
 */
internal class NodeModuleSourceFileTransportSession(
    cacheDirectory: File,
    private val executionId: String,
    private val provider: INodeJsModuleSourceProvider,
    timeoutMs: Long,
) {

    private val root = File(cacheDirectory, "$TRANSPORT_ROOT/${UUID.randomUUID()}")
    private val requestDirectory = File(root, "requests")
    private val responseDirectory = File(root, "responses")
    private val running = AtomicBoolean(false)
    private val requestCount = AtomicInteger(0)
    // Requests that produced no source: not_found and not_encrypted.
    private val probeRequestCount = AtomicInteger(0)
    private val responseCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val sourceBytes = AtomicLong(0L)
    private val elapsedMs = AtomicLong(0L)
    private val lastFailure = java.util.concurrent.atomic.AtomicReference("")
    private val activeSourceDescriptors = ConcurrentHashMap.newKeySet<ParcelFileDescriptor>()
    private val sourceDeadlineExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "AutoJs6NodeModuleSourceDeadline-${safeName(executionId)}").apply {
            isDaemon = true
        }
    }
    private val normalizedTimeoutMs = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
    private val thread = Thread(::loop, "AutoJs6NodeModuleSource-${safeName(executionId)}").apply {
        isDaemon = true
    }

    fun configJson(): String = JSONObject().apply {
        put("enabled", true)
        put("version", CONTRACT_VERSION)
        put("executionId", executionId)
        put("requestDir", requestDirectory.absolutePath)
        put("responseDir", responseDirectory.absolutePath)
        put("timeoutMs", normalizedTimeoutMs)
        put("pollIntervalMs", POLL_INTERVAL_MS)
    }.toString()

    fun start() {
        if (running.compareAndSet(false, true)) {
            try {
                check(requestDirectory.mkdirs() && responseDirectory.mkdirs()) {
                    "Failed to create Node module-source transport directories."
                }
                thread.start()
            } catch (error: Throwable) {
                running.set(false)
                root.deleteRecursively()
                throw error
            }
        }
    }

    fun stop() {
        running.set(false)
        Thread(
            { runCatching { provider.cancel("Node.js module-source transport stopped.") } },
            "AutoJs6NodeModuleCancel-${safeName(executionId)}",
        ).apply {
            isDaemon = true
            start()
        }
        closeActiveSourceDescriptors()
        sourceDeadlineExecutor.shutdownNow()
        if (thread.state != Thread.State.NEW) {
            thread.interrupt()
            runCatching { thread.join(STOP_JOIN_MS) }
            closeActiveSourceDescriptors()
            if (thread.isAlive) {
                runCatching { thread.join(STOP_JOIN_MS) }
            }
        }
        if (thread.isAlive) {
            failureCount.incrementAndGet()
            lastFailure.set("thread_stop_timeout")
        } else {
            root.deleteRecursively()
        }
    }

    fun nativeValues(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("embedded_script.module_provider.transport", "file_pfd_v1")
        put("embedded_script.module_provider.transport_request_count", requestCount.get().toString())
        put(
            "embedded_script.module_provider.transport_probe_request_count",
            probeRequestCount.get().toString(),
        )
        put(
            "embedded_script.module_provider.transport_probe_request_count_limit",
            PROBE_COUNT_LIMIT.toString(),
        )
        put("embedded_script.module_provider.transport_response_count", responseCount.get().toString())
        put("embedded_script.module_provider.transport_failure_count", failureCount.get().toString())
        put("embedded_script.module_provider.transport_source_bytes", sourceBytes.get().toString())
        put("embedded_script.module_provider.transport_elapsed_ms", elapsedMs.get().toString())
        put("embedded_script.module_provider.transport_last_failure", lastFailure.get())
    }

    private fun loop() {
        try {
            while (running.get()) {
                drainRequests()
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        } finally {
            closeActiveSourceDescriptors()
            root.deleteRecursively()
        }
    }

    private fun drainRequests() {
        val files = requestDirectory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .sortedBy(File::getName)
        for (file in files) {
            if (!running.get()) break
            serve(file)
        }
    }

    private fun serve(requestFile: File) {
        val startedAt = SystemClock.elapsedRealtime()
        val currentRequestCount = requestCount.incrementAndGet()
        var sourceFile: File? = null
        val response = runCatching {
            if (probeRequestCount.get() >= PROBE_COUNT_LIMIT) {
                throw TransportBudgetException(
                    budgetMessage("Module-source transport zero-yield probe budget is exhausted."),
                )
            }
            if (currentRequestCount > REQUEST_COUNT_LIMIT) {
                throw TransportBudgetException(
                    budgetMessage("Module-source transport request-count budget is exhausted."),
                )
            }
            val requestJson = try {
                JSONObject(readRequestTextBounded(requestFile))
            } catch (error: InvalidTransportRequestException) {
                throw error
            } catch (error: Exception) {
                throw InvalidTransportRequestException("Module-source request JSON is malformed.")
            }
            if (requestJson.optInt("version", 0) != CONTRACT_VERSION) {
                throw InvalidTransportRequestException("Module-source request version is invalid.")
            }
            val requestId = requestJson.optString("id").takeIf { it.isNotBlank() }
                ?: throw InvalidTransportRequestException("Module-source request id is empty.")
            if (requestId != safeName(requestId) || requestFile.name != "$requestId.json") {
                throw InvalidTransportRequestException("Module-source request identity does not match its file.")
            }
            val requestedExecutionId = requestJson.optString("executionId")
            if (requestedExecutionId != executionId) {
                throw DeniedTransportRequestException("Module-source execution id mismatch.")
            }
            val requestedPath = requestJson.optString("path").takeIf { it.isNotBlank() }
                ?: throw InvalidTransportRequestException("Module-source request path is empty.")
            val requestTimeoutMs = requestJson.optLong("timeoutMs", normalizedTimeoutMs)
                .coerceIn(MIN_TIMEOUT_MS, normalizedTimeoutMs)
            val requestDeadline = deadlineAfter(startedAt, requestTimeoutMs)
            val providerRequest = Bundle().apply {
                putInt(KEY_VERSION, CONTRACT_VERSION)
                putString(KEY_REQUEST_ID, requestId)
                putString(KEY_EXECUTION_ID, executionId)
                putString(KEY_PATH, requestedPath)
                putLong(
                    KEY_TIMEOUT_MS,
                    requestTimeoutMs,
                )
            }
            val providerWaitMs = remainingMs(requestDeadline)
                ?: throw ProviderCallTimeoutException("Module-source provider request deadline expired.")
            val providerResponse = resolveProvider(
                requestId = requestId,
                providerRequest = providerRequest,
                timeoutMs = providerWaitMs,
            )
            if (providerResponse.getInt(KEY_VERSION, 0) != CONTRACT_VERSION) {
                throw InvalidTransportRequestException("Module-source provider response version is invalid.")
            }
            if (providerResponse.getString(KEY_REQUEST_ID).orEmpty() != requestId) {
                throw InvalidTransportRequestException("Module-source provider response identity is invalid.")
            }
            val status = providerResponse.getString(KEY_STATUS).orEmpty()
            if (status !in STATUSES) {
                throw InvalidTransportRequestException("Unknown module-source provider status.")
            }
            if (status in PROBE_STATUSES) {
                probeRequestCount.incrementAndGet()
            }
            val descriptor = providerResponse.sourceDescriptor()
            JSONObject().apply {
                put("version", CONTRACT_VERSION)
                put("id", requestId)
                put("status", status)
                put("resolvedPath", providerResponse.getString(KEY_RESOLVED_PATH).orEmpty())
                put("sourceBytes", providerResponse.getLong(KEY_SOURCE_BYTES, 0L))
                put("elapsedMs", providerResponse.getLong(KEY_ELAPSED_MS, 0L))
                put("errorCode", providerResponse.getString(KEY_ERROR_CODE).orEmpty())
                put("errorMessage", providerResponse.getString(KEY_ERROR_MESSAGE).orEmpty())
                if (status == STATUS_DECRYPTED) {
                    val requiredDescriptor = descriptor
                        ?: throw InvalidTransportRequestException(
                            "Decrypted module-source response has no file descriptor.",
                        )
                    val expectedBytes = providerResponse.getLong(KEY_SOURCE_BYTES, -1L)
                    try {
                        if (expectedBytes < 0L) {
                            throw InvalidTransportRequestException(
                                "Decrypted module-source byte count is invalid.",
                            )
                        }
                        if (expectedBytes > MAX_SOURCE_BYTES.toLong()) {
                            throw TransportBudgetException(
                                "Decrypted module source exceeds the single-source transport budget.",
                            )
                        }
                        if (sourceBytes.get() > MAX_TOTAL_SOURCE_BYTES - expectedBytes) {
                            throw TransportBudgetException(
                                "Decrypted module sources exceed the aggregate transport budget.",
                            )
                        }
                        sourceFile = copySourceDescriptor(
                            requestId,
                            requiredDescriptor,
                            expectedBytes,
                            requestDeadline,
                        )
                    } catch (error: Throwable) {
                        runCatching { requiredDescriptor.close() }
                        throw error
                    }
                    sourceBytes.addAndGet(expectedBytes)
                    put("sourcePath", sourceFile.absolutePath)
                } else {
                    runCatching { descriptor?.close() }
                }
            }
        }.getOrElse { error ->
            failureCount.incrementAndGet()
            lastFailure.set(failureCategory(error))
            sourceFile?.delete()
            val timedOut = error is ProviderCallTimeoutException
            val denied = error is DeniedTransportRequestException
            val invalid = error is InvalidTransportRequestException
            val budget = error is TransportBudgetException
            JSONObject().apply {
                put("version", CONTRACT_VERSION)
                put("id", requestFile.name.removeSuffix(".json"))
                put("status", if (timedOut) STATUS_TIMED_OUT else if (denied) STATUS_DENIED else STATUS_FAILED)
                put("resolvedPath", "")
                put("sourceBytes", 0)
                put("elapsedMs", (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L))
                put(
                    "errorCode",
                    when {
                        timedOut -> ERROR_PROVIDER_TIMEOUT
                        denied -> ERROR_PROVIDER_DENIED
                        invalid -> ERROR_PROVIDER_INVALID_REQUEST
                        budget -> ERROR_BUDGET_EXCEEDED
                        else -> ERROR_PROVIDER_FAILED
                    },
                )
                put("errorMessage", publicFailureMessage(error))
            }
        }
        elapsedMs.addAndGet((SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L))
        runCatching { writeResponse(response) }
            .onSuccess { responseCount.incrementAndGet() }
            .onFailure { error ->
                failureCount.incrementAndGet()
                lastFailure.set(failureCategory(error))
                sourceFile?.delete()
            }
        requestFile.delete()
    }

    private fun readRequestTextBounded(file: File): String {
        if (file.length() !in 0..MAX_REQUEST_JSON_BYTES.toLong()) {
            throw InvalidTransportRequestException("Module-source request JSON is too large.")
        }
        FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > MAX_REQUEST_JSON_BYTES) {
                    throw InvalidTransportRequestException("Module-source request JSON is too large.")
                }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun resolveProvider(
        requestId: String,
        providerRequest: Bundle,
        timeoutMs: Long,
    ): Bundle {
        val abandoned = AtomicBoolean(false)
        val task = FutureTask {
            val response = provider.resolveModuleSource(providerRequest)
            if (abandoned.get()) {
                runCatching { response?.sourceDescriptor()?.close() }
            }
            response
        }
        Thread(task, "AutoJs6NodeModuleBinder-${safeName(requestId)}").apply {
            isDaemon = true
            start()
        }
        return try {
            task.get(timeoutMs.coerceAtLeast(MIN_TIMEOUT_MS), TimeUnit.MILLISECONDS)
                ?: throw IllegalStateException("Module-source provider returned no response.")
        } catch (error: TimeoutException) {
            abandoned.set(true)
            task.cancel(true)
            Thread(
                {
                    runCatching {
                        provider.cancel("Module-source provider request timed out: $requestId")
                    }
                },
                "AutoJs6NodeModuleCancel-${safeName(requestId)}",
            ).apply {
                isDaemon = true
                start()
            }
            throw ProviderCallTimeoutException(
                "Module-source provider request timed out after $timeoutMs ms.",
                error,
            )
        } catch (error: InterruptedException) {
            abandoned.set(true)
            task.cancel(true)
            Thread(
                { runCatching { provider.cancel("Module-source provider transport was interrupted.") } },
                "AutoJs6NodeModuleCancel-${safeName(requestId)}",
            ).apply {
                isDaemon = true
                start()
            }
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException("Module-source provider transport was interrupted.")
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private fun copySourceDescriptor(
        requestId: String,
        descriptor: ParcelFileDescriptor,
        expectedBytes: Long,
        deadline: Long,
    ): File {
        val safeRequestId = safeName(requestId)
        // The runtime validates this exact response-directory path before it
        // reads and unlinks the source. Keeping JSON and source publication in
        // one request-scoped directory also makes path substitution fail
        // closed on both embedded and plugin transports.
        val target = File(responseDirectory, "$safeRequestId.source")
        val temporary = File(responseDirectory, "$safeRequestId.source.tmp")
        var copied = 0L
        var published = false
        val deadlineExpired = AtomicBoolean(false)
        activeSourceDescriptors += descriptor
        val deadlineClose = try {
            sourceDeadlineExecutor.schedule(
                {
                    deadlineExpired.set(true)
                    if (activeSourceDescriptors.remove(descriptor)) {
                        runCatching { descriptor.close() }
                    }
                },
                (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
        } catch (error: RejectedExecutionException) {
            activeSourceDescriptors -= descriptor
            runCatching { descriptor.close() }
            throw java.io.InterruptedIOException(
                "Module-source provider transport stopped before source transfer.",
            ).apply { initCause(error) }
        }
        try {
            temporary.delete()
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        if (!running.get()) {
                            throw java.io.InterruptedIOException(
                                "Module-source transport stopped while reading source.",
                            )
                        }
                        if (SystemClock.elapsedRealtime() >= deadline) {
                            throw ProviderCallTimeoutException(
                                "Module-source provider source transfer timed out.",
                            )
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= MAX_SOURCE_BYTES.toLong() && copied <= expectedBytes) {
                            "Decrypted module source exceeds its declared or permitted byte count."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(copied == expectedBytes) {
                "Decrypted module-source byte count changed in transport ($copied != $expectedBytes)."
            }
            target.delete()
            check(temporary.renameTo(target)) {
                "Failed to publish decrypted module source for request $requestId."
            }
            published = true
            return target
        } catch (error: IOException) {
            if (deadlineExpired.get() || SystemClock.elapsedRealtime() >= deadline) {
                throw ProviderCallTimeoutException(
                    "Module-source provider source transfer timed out.",
                    error,
                )
            }
            throw error
        } finally {
            deadlineClose.cancel(false)
            activeSourceDescriptors -= descriptor
            if (!published) {
                temporary.delete()
                target.delete()
            }
        }
    }

    private fun closeActiveSourceDescriptors() {
        activeSourceDescriptors.concurrentSnapshot().forEach { descriptor ->
            runCatching { descriptor.close() }
            activeSourceDescriptors -= descriptor
        }
    }

    private fun writeResponse(response: JSONObject) {
        val requestId = safeName(response.optString("id"))
        val target = File(responseDirectory, "$requestId.json")
        val temporary = File(responseDirectory, "$requestId.json.tmp")
        temporary.writeText(response.toString(), Charsets.UTF_8)
        check(temporary.renameTo(target)) {
            "Failed to publish module-source response for request $requestId."
        }
    }

    /**
     * Appends the per-category counters so an exhausted budget is self
     * explanatory. Without them a plaintext project reports an exhausted source
     * budget while having transported zero sources, which reads as a
     * contradiction.
     */
    private fun budgetMessage(reason: String): String = buildString {
        append(reason)
        append(" (requests=").append(requestCount.get())
        append(", probes=").append(probeRequestCount.get())
        append('/').append(PROBE_COUNT_LIMIT)
        append(", sourceBytes=").append(sourceBytes.get())
        append('/').append(MAX_TOTAL_SOURCE_BYTES)
        append(')')
    }

    private fun failureCategory(error: Throwable): String = when (error) {
        is ProviderCallTimeoutException -> "timed_out"
        is DeniedTransportRequestException -> "denied"
        is InvalidTransportRequestException -> "invalid_request"
        is TransportBudgetException -> "budget_exceeded"
        is InterruptedException,
        is java.io.InterruptedIOException,
        -> "interrupted"
        else -> error.javaClass.simpleName.ifBlank { "failed" }
    }

    private fun publicFailureMessage(error: Throwable): String = when (error) {
        is ProviderCallTimeoutException -> "Module-source provider transport timed out."
        is DeniedTransportRequestException -> "Module-source provider transport denied the request."
        is InvalidTransportRequestException -> "Module-source provider transport received an invalid request."
        // Budget messages carry only counters and limits, never a path or
        // source, and the counters are what make an exhausted budget
        // diagnosable at all.
        is TransportBudgetException ->
            error.message ?: "Module-source provider transport budget is exhausted."
        is InterruptedException,
        is java.io.InterruptedIOException,
        -> "Module-source provider transport was interrupted."
        else -> "Module-source provider transport failed."
    }

    @Suppress("DEPRECATION")
    private fun Bundle.sourceDescriptor(): ParcelFileDescriptor? =
        getParcelable(KEY_SOURCE_FD) as? ParcelFileDescriptor

    private companion object {
        const val CONTRACT_VERSION = 1
        const val TRANSPORT_ROOT = "embedded-node-module-source"
        const val POLL_INTERVAL_MS = 2L
        const val STOP_JOIN_MS = 1_000L
        const val MIN_TIMEOUT_MS = 1L
        const val MAX_TIMEOUT_MS = 30_000L
        const val MAX_REQUEST_JSON_BYTES = 64 * 1024
        const val PROBE_COUNT_LIMIT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_PROBE_MAX_COUNT
        const val REQUEST_COUNT_LIMIT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_REQUEST_MAX_COUNT
        const val COPY_BUFFER_BYTES = 32 * 1024
        const val MAX_SOURCE_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES
        const val MAX_TOTAL_SOURCE_BYTES =
            NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES.toLong()
        const val KEY_VERSION = "moduleSourceProviderVersion"
        const val KEY_REQUEST_ID = "moduleSourceProviderRequestId"
        const val KEY_EXECUTION_ID = "executionId"
        const val KEY_PATH = "moduleSourceProviderPath"
        const val KEY_TIMEOUT_MS = "timeoutMs"
        const val KEY_STATUS = "moduleSourceProviderStatus"
        const val KEY_RESOLVED_PATH = "moduleSourceProviderResolvedPath"
        const val KEY_SOURCE_FD = "moduleSourceProviderSourceFd"
        const val KEY_SOURCE_BYTES = "moduleSourceProviderSourceBytes"
        const val KEY_ELAPSED_MS = "moduleSourceProviderElapsedMs"
        const val KEY_ERROR_CODE = "errorCode"
        const val KEY_ERROR_MESSAGE = "errorMessage"

        const val STATUS_DECRYPTED = "decrypted"
        const val STATUS_NOT_ENCRYPTED = "not_encrypted"
        const val STATUS_NOT_FOUND = "not_found"
        const val STATUS_DENIED = "denied"
        const val STATUS_TIMED_OUT = "timed_out"
        const val STATUS_FAILED = "failed"
        val STATUSES = setOf(
            STATUS_DECRYPTED,
            STATUS_NOT_ENCRYPTED,
            STATUS_NOT_FOUND,
            STATUS_DENIED,
            "cancelled",
            STATUS_TIMED_OUT,
            STATUS_FAILED,
        )
        val PROBE_STATUSES = setOf(STATUS_NOT_ENCRYPTED, STATUS_NOT_FOUND)
        const val ERROR_PROVIDER_FAILED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED"
        const val ERROR_PROVIDER_TIMEOUT = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT"
        const val ERROR_PROVIDER_DENIED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED"
        const val ERROR_PROVIDER_INVALID_REQUEST = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_INVALID_REQUEST"
        const val ERROR_BUDGET_EXCEEDED = "ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED"

        fun safeName(value: String): String = value
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(96)
            .ifBlank { "request" }

        fun deadlineAfter(now: Long, timeoutMs: Long): Long =
            if (timeoutMs >= Long.MAX_VALUE - now) Long.MAX_VALUE else now + timeoutMs

        fun remainingMs(deadline: Long): Long? =
            (deadline - SystemClock.elapsedRealtime()).takeIf { it > 0L }
    }

    private class ProviderCallTimeoutException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)

    private class InvalidTransportRequestException(message: String) : IOException(message)

    private class DeniedTransportRequestException(message: String) : IOException(message)

    private class TransportBudgetException(message: String) : IOException(message)
}
