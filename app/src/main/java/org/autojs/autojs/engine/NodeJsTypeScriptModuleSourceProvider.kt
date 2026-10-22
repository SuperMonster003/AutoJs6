package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilationOutput
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilerException
import org.autojs.autojs.core.plugin.typescript.TypeScriptCompilerPluginClient
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler
import org.autojs.autojs.core.plugin.typescript.TypeScriptProjectArchive
import org.autojs.autojs.core.plugin.typescript.TypeScriptSourceMapBinding
import org.autojs.autojs.core.plugin.typescript.TypeScriptUtf8
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Execution-scoped v3 provider used only by compiler-backed Node TypeScript projects.
 *
 * Existing encrypted-source operations are delegated to the published v1 provider. The v3
 * operation accepts one descriptor-bound runtime-created TypeScript source, overlays it on a
 * complete Host project snapshot, and invokes the compiler from the Host UID so the T3 resident
 * session key remains the same as the initial project compilation.
 */
internal class NodeJsTypeScriptModuleSourceProvider(
    context: Context,
    rootDirectory: File,
    entryFile: File,
    private val executionId: String,
    private val timeoutMs: Long,
    initialSourceCount: Int,
    initialSourceBytes: Long,
) : INodeJsModuleSourceProvider.Stub() {

    private val appContext = context.applicationContext
    private val root = rootDirectory.canonicalFile
    private val entry = entryFile.canonicalFile
    private val rootIdentity = RootIdentity.capture(root)
    private val encryptedDelegate = NodeJsEncryptedModuleSourceProvider(
        rootDirectory = root,
        executionId = executionId,
        timeoutMs = timeoutMs,
        initialSourceCount = initialSourceCount,
        initialSourceBytes = initialSourceBytes,
    )
    private val cancelled = AtomicBoolean(false)
    private val compileCount = AtomicInteger(0)
    private val compileFailureCount = AtomicInteger(0)
    private val activePipeWrites = ConcurrentHashMap.newKeySet<ParcelFileDescriptor>()
    private val overlays = LinkedHashMap<String, ByteArray>()
    private val bindings = LinkedHashMap<String, TypeScriptSourceMapBinding>()
    private val compilationLock = Any()

    init {
        require(root.isDirectory) { "TypeScript on-demand project root is unavailable." }
        require(entry.isFile && entry.path.startsWith(root.path + File.separator)) {
            "TypeScript on-demand entry escapes its project root."
        }
        require(rootIdentity.matches(root)) { "TypeScript on-demand project root identity is unstable." }
    }

    override fun resolveModuleSource(request: Bundle?): Bundle {
        val startedAt = SystemClock.elapsedRealtime()
        val requestId = request
            ?.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID)
            .orEmpty()
        val operation = request
            ?.getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION)
            .orEmpty()
        if (request == null) {
            return failure(
                requestId = requestId,
                operation = operation,
                code = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
                message = "Module-source provider request is missing.",
                startedAt = startedAt,
            )
        }
        if (
            request.getInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, 0) !=
            CONTRACT_VERSION ||
            requestId.isBlank() || requestId.length > MAX_REQUEST_ID_CHARS ||
            request.getString(NodeJsRuntimeContract.KEY_EXECUTION_ID).orEmpty() != executionId
        ) {
            return failure(
                requestId = requestId,
                operation = operation,
                code = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
                message = "Module-source provider request identity or contract version is invalid.",
                startedAt = startedAt,
            )
        }
        if (cancelled.get()) {
            return failure(
                requestId = requestId,
                operation = operation,
                code = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_CANCELLED,
                message = "Module-source provider was cancelled.",
                status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED,
                startedAt = startedAt,
            )
        }
        if (!rootIdentity.matches(root)) {
            return failure(
                requestId = requestId,
                operation = operation,
                code = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_DENIED,
                message = "TypeScript project root identity changed during execution.",
                status = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED,
                startedAt = startedAt,
            )
        }
        return when (operation) {
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING,
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT ->
                delegateExisting(request, requestId, operation)
            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_COMPILE_MISSING_TYPESCRIPT ->
                compileMissing(request, requestId, operation, startedAt)
            else -> failure(
                requestId = requestId,
                operation = operation,
                code = NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
                message = "Module-source provider operation is unsupported.",
                startedAt = startedAt,
            )
        }
    }

    override fun getNativeDiagnostics(): Bundle {
        val delegate = encryptedDelegate.nativeDiagnostics
        val values = delegate
            .getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD)
            .orEmpty()
            .toMutableList()
            .apply {
                add("embedded_script.module_provider.contract_version=$CONTRACT_VERSION")
                add("embedded_script.module_provider.typescript_compile_count=${compileCount.get()}")
                add(
                    "embedded_script.module_provider.typescript_compile_failure_count=" +
                        compileFailureCount.get(),
                )
                add("embedded_script.module_provider.typescript_overlay_count=${synchronized(compilationLock) { overlays.size }}")
            }
        return Bundle(delegate).apply {
            putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, CONTRACT_VERSION)
            putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, values.toTypedArray())
        }
    }

    override fun cancel(reason: String?) {
        if (!cancelled.compareAndSet(false, true)) return
        encryptedDelegate.cancel(reason)
        activePipeWrites.concurrentSnapshot().forEach { descriptor -> runCatching { descriptor.close() } }
        synchronized(compilationLock) {
            overlays.values.forEach { bytes -> bytes.fill(0) }
            overlays.clear()
        }
    }

    fun sourceMapBindings(): List<TypeScriptSourceMapBinding> = synchronized(compilationLock) {
        bindings.values.toList()
    }

    private fun delegateExisting(
        request: Bundle,
        requestId: String,
        operation: String,
    ): Bundle {
        val delegated = Bundle(request).apply {
            putInt(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION,
                NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_PROVIDER_CONTRACT_VERSION,
            )
            remove(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_FD)
            remove(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_BYTES)
            remove(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_SHA256)
        }
        val response = encryptedDelegate.resolveModuleSource(delegated)
        return Bundle(response).apply {
            putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, CONTRACT_VERSION)
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID, requestId)
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION, operation)
            if (
                operation ==
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT
            ) {
                when (getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS)) {
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED ->
                        putString(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT,
                        )
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED -> {
                        closeSourceDescriptor(this)
                        remove(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD)
                        remove(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES)
                        putString(
                            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                            NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND,
                        )
                    }
                }
            }
        }
    }

    private fun compileMissing(
        request: Bundle,
        requestId: String,
        operation: String,
        startedAt: Long,
    ): Bundle = synchronized(compilationLock) {
        if (cancelled.get()) {
            return@synchronized failure(
                requestId,
                operation,
                NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_CANCELLED,
                "TypeScript on-demand compilation was cancelled.",
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_CANCELLED,
                startedAt,
            )
        }
        val requestedPath = request
            .getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_PATH)
            .orEmpty()
        val relativePath = runCatching { requireOverlayRelativePath(requestedPath) }
            .getOrElse { error ->
                return@synchronized failure(
                    requestId,
                    operation,
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_DENIED,
                    error.message ?: "TypeScript on-demand source path was denied.",
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_DENIED,
                    startedAt,
                )
            }
        val input = runCatching { readCompilationInput(request, relativePath) }
            .getOrElse { error ->
                return@synchronized failure(
                    requestId,
                    operation,
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST,
                    error.message ?: "TypeScript on-demand source input is invalid.",
                    startedAt = startedAt,
                )
            }
        try {
            val requestDeadline = request
                .getLong(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS,
                    deadlineAfter(startedAt, timeoutMs),
                )
            val remaining = requestDeadline - SystemClock.elapsedRealtime()
            if (remaining < TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS) {
                return@synchronized failure(
                    requestId,
                    operation,
                    NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_TIMED_OUT,
                    "TypeScript on-demand compilation deadline expired.",
                    NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT,
                    startedAt,
                )
            }
            val compilationTimeout = minOf(
                remaining,
                timeoutMs,
                TypeScriptCompilerContract.MAX_TIMEOUT_MS,
            ).coerceAtLeast(TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS)
            val nextOverlays = LinkedHashMap<String, ByteArray>(overlays.size + 1).apply {
                overlays.forEach { (path, bytes) -> put(path, bytes.copyOf()) }
                put(relativePath, input.copyOf())
            }
            val output = try {
                runBlocking {
                    TypeScriptCompilerPluginClient(appContext).compileProject(
                        projectRoot = root,
                        entryFile = entry,
                        targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
                        timeoutMs = compilationTimeout,
                        usePersistentCache = false,
                        sourceOverlays = nextOverlays,
                    )
                }
            } finally {
                nextOverlays.values.forEach { bytes -> bytes.fill(0) }
            }
            when (output) {
                is TypeScriptCompilationOutput.Diagnostics -> {
                    compileFailureCount.incrementAndGet()
                    failure(
                        requestId,
                        operation,
                        NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILATION_FAILED,
                        diagnosticMessage(relativePath, output),
                        startedAt = startedAt,
                    )
                }
                is TypeScriptCompilationOutput.Emitted -> {
                    val module = output.modules.singleOrNull { emitted ->
                        emitted.source.projectRelativePath == relativePath
                    } ?: run {
                        compileFailureCount.incrementAndGet()
                        return@synchronized failure(
                            requestId,
                            operation,
                            NodeJsRuntimeContract.ERROR_TYPESCRIPT_COMPILATION_FAILED,
                            "TypeScript compiler emitted no module for '$relativePath'.",
                            startedAt = startedAt,
                        )
                    }
                    val generatedRelativePath = module.generatedMapping?.generatedPath
                        ?.removePrefix("/outputs/")
                        ?: module.javaScriptPath.removePrefix("modules/")
                    requireGeneratedPath(relativePath, generatedRelativePath)
                    val javaScriptBytes = TypeScriptUtf8.encode(
                        module.javaScript,
                        "TypeScript on-demand JavaScript output",
                    )
                    var handedToPipe = false
                    try {
                        if (javaScriptBytes.size > NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                            return@synchronized failure(
                                requestId,
                                operation,
                                NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED,
                                "TypeScript on-demand JavaScript output exceeds its byte budget.",
                                startedAt = startedAt,
                            )
                        }
                        val generatedFile = File(root, generatedRelativePath).absoluteFile.normalize()
                        requireGeneratedTargetAbsent(generatedFile)
                        val binding = TypeScriptSourceMapBinding.parse(
                            sourceMapText = module.sourceMap,
                            generatedSourceName = generatedFile.path,
                            expectedGeneratedFileName = module.javaScriptPath.substringAfterLast('/'),
                            originalSourceName = File(root, relativePath).absoluteFile.normalize().path,
                        )
                        overlays.remove(relativePath)?.fill(0)
                        overlays[relativePath] = input.copyOf()
                        bindings[generatedFile.path] = binding
                        compileCount.incrementAndGet()
                        success(
                            requestId = requestId,
                            operation = operation,
                            resolvedPath = generatedFile.path,
                            source = javaScriptBytes,
                            startedAt = startedAt,
                        ).also { handedToPipe = true }
                    } finally {
                        if (!handedToPipe) javaScriptBytes.fill(0)
                    }
                }
            }
        } catch (error: TypeScriptCompilerException) {
            compileFailureCount.incrementAndGet()
            failure(
                requestId,
                operation,
                error.code,
                error.message ?: "TypeScript compiler request failed.",
                startedAt = startedAt,
            )
        } catch (error: Throwable) {
            compileFailureCount.incrementAndGet()
            failure(
                requestId,
                operation,
                NodeJsRuntimeContract.ERROR_MODULE_SOURCE_PROVIDER_FAILED,
                error.message ?: "TypeScript on-demand compilation failed.",
                startedAt = startedAt,
            )
        } finally {
            input.fill(0)
        }
    }

    @Suppress("DEPRECATION")
    private fun readCompilationInput(request: Bundle, relativePath: String): ByteArray {
        val rawBytes = request.get(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_BYTES)
        require(rawBytes is Long) { "TypeScript on-demand input byte count has an invalid type." }
        require(rawBytes in 0..MAX_INPUT_BYTES) {
            "TypeScript on-demand input exceeds its byte budget."
        }
        val expectedSha256 = request
            .getString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_SHA256)
            .orEmpty()
        require(expectedSha256.matches(LOWER_SHA256)) {
            "TypeScript on-demand input SHA-256 is invalid."
        }
        val descriptor = request.getParcelable<ParcelFileDescriptor>(
            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_INPUT_FD,
        ) ?: error("TypeScript on-demand input descriptor is missing.")
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val output = ByteArrayOutputStream(minOf(rawBytes.toInt(), COPY_BUFFER_BYTES))
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var copied = 0L
            while (true) {
                check(!cancelled.get()) { "TypeScript on-demand input read was cancelled." }
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                copied = Math.addExact(copied, count.toLong())
                require(copied <= rawBytes && copied <= MAX_INPUT_BYTES) {
                    "TypeScript on-demand input byte count changed during read."
                }
                output.write(buffer, 0, count)
            }
            require(copied == rawBytes) {
                "TypeScript on-demand input byte count does not match its descriptor."
            }
            output.toByteArray()
        }
        var retained = false
        try {
            require(sha256(bytes) == expectedSha256) {
                "TypeScript on-demand input SHA-256 does not match its descriptor."
            }
            TypeScriptUtf8.decode(bytes, "TypeScript on-demand source $relativePath")
            retained = true
            return bytes
        } finally {
            if (!retained) bytes.fill(0)
        }
    }

    private fun requireOverlayRelativePath(requestedPath: String): String {
        require(requestedPath.isNotBlank() && '\u0000' !in requestedPath) {
            "TypeScript on-demand source path is missing or contains NUL."
        }
        val requested = File(requestedPath).let { file ->
            if (file.isAbsolute) file.absoluteFile.normalize() else File(root, requestedPath).absoluteFile.normalize()
        }
        require(requested.path.startsWith(root.path + File.separator)) {
            "TypeScript on-demand source path escapes its project root."
        }
        val relativePath = requested.path
            .removePrefix(root.path + File.separator)
            .replace(File.separatorChar, '/')
        val segments = relativePath.split('/')
        require(
            relativePath.length <= MAX_PATH_CHARS &&
                segments.all { segment ->
                    segment.isNotBlank() && segment != "." && segment != ".." &&
                        segment.none { character -> character.code < 0x20 || character.code == 0x7f }
                },
        ) { "TypeScript on-demand source path is not canonical." }
        require(
            (relativePath.endsWith(".ts") || relativePath.endsWith(".mts") ||
                relativePath.endsWith(".cts")) &&
                !relativePath.endsWith(".d.ts") && !relativePath.endsWith(".d.mts") &&
                !relativePath.endsWith(".d.cts") &&
                TypeScriptProjectArchive.isSnapshotSource(
                    relativePath,
                    TypeScriptCompilerContract.PROFILE_NODE,
                ),
        ) { "TypeScript on-demand source extension is unsupported." }
        var current = root
        segments.dropLast(1).forEach { segment ->
            current = File(current, segment)
            val stat = try {
                Os.lstat(current.path)
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) return@forEach
                throw error
            }
            require(!OsConstants.S_ISLNK(stat.st_mode) && OsConstants.S_ISDIR(stat.st_mode)) {
                "TypeScript on-demand source parent is linked or not a directory."
            }
        }
        try {
            Os.lstat(requested.path)
            error("TypeScript on-demand source already exists in the Host project.")
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ENOENT) throw error
        }
        requireGeneratedTargetAbsent(File(root, generatedRelativePath(relativePath)))
        return relativePath
    }

    private fun requireGeneratedPath(sourcePath: String, generatedPath: String) {
        val expected = generatedRelativePath(sourcePath)
        require(generatedPath == expected) {
            "TypeScript compiler emitted an unexpected generated module path."
        }
    }

    private fun generatedRelativePath(sourcePath: String): String = when {
        sourcePath.endsWith(".mts") -> sourcePath.removeSuffix(".mts") + ".mjs"
        sourcePath.endsWith(".cts") -> sourcePath.removeSuffix(".cts") + ".cjs"
        else -> sourcePath.removeSuffix(".ts") + ".js"
    }

    private fun requireGeneratedTargetAbsent(target: File) {
        try {
            Os.lstat(target.path)
            error("TypeScript generated module collides with an existing Host project path.")
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.ENOENT) throw error
        }
    }

    private fun diagnosticMessage(
        relativePath: String,
        output: TypeScriptCompilationOutput.Diagnostics,
    ): String {
        val details = output.diagnostics
            .take(MAX_DIAGNOSTICS)
            .joinToString("\n") { diagnostic ->
                TypeScriptExecutionCompiler.formatDiagnostic(relativePath, diagnostic)
            }
        return buildString {
            append("TypeScript on-demand compilation failed for '")
            append(relativePath)
            append("'.")
            if (details.isNotBlank()) {
                append('\n')
                append(details)
            }
        }.take(MAX_ERROR_MESSAGE_CHARS)
    }

    private fun success(
        requestId: String,
        operation: String,
        resolvedPath: String,
        source: ByteArray,
        startedAt: Long,
    ): Bundle {
        val descriptors = ParcelFileDescriptor.createPipe()
        val readDescriptor = descriptors[0]
        val writeDescriptor = descriptors[1]
        activePipeWrites += writeDescriptor
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor).use { output ->
                    output.write(source)
                    output.flush()
                }
            } finally {
                source.fill(0)
                activePipeWrites -= writeDescriptor
            }
        }, "AutoJs6TypeScriptModulePipe").apply {
            isDaemon = true
            start()
        }
        return Bundle().apply {
            putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, CONTRACT_VERSION)
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID, requestId)
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION, operation)
            putString(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS,
                NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_COMPILED_TYPESCRIPT,
            )
            putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH, resolvedPath)
            putParcelable(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD, readDescriptor)
            putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES, source.size.toLong())
            putLong(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS,
                elapsedSince(startedAt),
            )
            putString(NodeJsRuntimeContract.KEY_ERROR_CODE, "")
            putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, "")
        }
    }

    private fun failure(
        requestId: String,
        operation: String,
        code: String,
        message: String,
        status: String = NodeJsRuntimeContract.MODULE_SOURCE_PROVIDER_STATUS_FAILED,
        startedAt: Long,
    ): Bundle = Bundle().apply {
        putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, CONTRACT_VERSION)
        putString(
            NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID,
            requestId.ifBlank { "invalid" },
        )
        putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_OPERATION, operation)
        putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_STATUS, status)
        putString(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH, "")
        putLong(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS, elapsedSince(startedAt))
        putString(NodeJsRuntimeContract.KEY_ERROR_CODE, code)
        putString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE, message.take(MAX_ERROR_MESSAGE_CHARS))
    }

    private data class RootIdentity(val device: Long, val inode: Long) {
        fun matches(root: File): Boolean = runCatching {
            val stat = Os.lstat(root.path)
            !OsConstants.S_ISLNK(stat.st_mode) && OsConstants.S_ISDIR(stat.st_mode) &&
                stat.st_dev == device && stat.st_ino == inode
        }.getOrDefault(false)

        companion object {
            fun capture(root: File): RootIdentity {
                val stat = Os.lstat(root.path)
                require(!OsConstants.S_ISLNK(stat.st_mode) && OsConstants.S_ISDIR(stat.st_mode))
                return RootIdentity(stat.st_dev, stat.st_ino)
            }
        }
    }

    private companion object {
        const val CONTRACT_VERSION = 3
        const val MAX_REQUEST_ID_CHARS = 160
        const val MAX_PATH_CHARS = 4096
        const val COPY_BUFFER_BYTES = 16 * 1024
        const val MAX_DIAGNOSTICS = 20
        const val MAX_ERROR_MESSAGE_CHARS = 8192
        val MAX_INPUT_BYTES = minOf(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES.toLong(),
        )
        val LOWER_SHA256 = Regex("[0-9a-f]{64}")

        fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte ->
                "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
            }

        fun elapsedSince(startedAt: Long): Long =
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

        fun deadlineAfter(startedAt: Long, durationMs: Long): Long =
            if (startedAt > Long.MAX_VALUE - durationMs) Long.MAX_VALUE else startedAt + durationMs

        @Suppress("DEPRECATION")
        fun closeSourceDescriptor(bundle: Bundle) {
            runCatching {
                bundle.getParcelable<ParcelFileDescriptor>(
                    NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD,
                )?.close()
            }
        }
    }
}
