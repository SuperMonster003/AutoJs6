package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal class TypeScriptCompilerException(
    val code: String,
    message: String,
    val outcome: String? = null,
    cause: Throwable? = null,
) : Exception("$code: $message", cause)

internal class TypeScriptCompilerPluginClient(
    context: Context,
) {

    private val appContext = context.applicationContext
    private val host = AidlPluginHost(
        action = TypeScriptPluginActions.COMPILER,
        tag = LOG_TAG,
        moduleLabel = MODULE_LABEL,
        proxyFactory = ITypeScriptCompilerPlugin.Stub::asInterface,
        infoReader = { compiler -> compiler.compilerInfo.toPluginInfo() },
    )

    suspend fun compileSingleFile(
        sourceFile: File,
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        sourceName: String = sourceFile.name,
        sourceIdentity: String? = null,
    ): TypeScriptCompilationOutput = withContext(Dispatchers.IO) {
        require(timeoutMs in TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS..
            TypeScriptCompilerContract.MAX_TIMEOUT_MS)
        require(sourceFile.isFile) { "TypeScript source is not a regular file." }
        require(sourceName.endsWith(".ts", ignoreCase = true)) {
            "The Rhino TypeScript compiler accepts only a .ts single-file entry."
        }
        require(
            sourceName.isNotBlank() &&
                sourceName != "." &&
                sourceName != ".." &&
                sourceName.none { character ->
                    character == '/' ||
                        character == '\\' ||
                        character.code in 0x00..0x1f ||
                        character.code in 0x7f..0x9f
                },
        ) {
            "The TypeScript source name is not a canonical single-file name."
        }

        val sourceBytes = readBoundedSource(sourceFile)
        val requestId = "host-" + UUID.randomUUID()
        val canonicalPath = sourceIdentity?.takeIf(String::isNotBlank)
            ?: runCatching { sourceFile.canonicalPath }.getOrElse { sourceFile.absolutePath }
        val projectId = "single-file-" +
            sha256(TypeScriptUtf8.encode(canonicalPath, "TypeScript source path")).take(32)
        val entrySourceUri = "autojs://source/$projectId/" + percentEncode(sourceName)
        val inputFile = File.createTempFile("typescript-input-", ".zip", appContext.cacheDir)
        val outputFile = File.createTempFile("typescript-output-", ".zip", appContext.cacheDir)
        var leased: AidlPluginHost.LeasedCall<StartedCompilation>? = null
        var terminalReceived = false

        try {
            val target = host.selectOrThrow(
                context = appContext,
                engineId = TypeScriptPluginIds.ID,
                engine = TypeScriptPluginIds.ENGINE,
                label = MODULE_LABEL,
            )
            leased = host.callWithDedicatedBindingLease(
                context = appContext,
                target = target,
                retryOnRemoteException = false,
            ) { compiler ->
                requireContract(compiler)
                val prewarmResult = compiler.prewarm(prewarmRequest(timeoutMs))
                prewarmResult.throwCompilerErrorIfPresent()
                val authority = authorityFrom(compiler.compilerInfo)
                val prepared = TypeScriptSingleFileArchive.write(
                    sourceName = sourceName,
                    sourceBytes = sourceBytes,
                    requestId = requestId,
                    projectId = projectId,
                    entrySourceUri = entrySourceUri,
                    authority = authority,
                    destination = inputFile,
                )
                val callback = CompilationCallback()
                val ticket = startCompilation(
                    compiler = compiler,
                    prepared = prepared,
                    outputFile = outputFile,
                    timeoutMs = timeoutMs,
                    callback = callback,
                )
                StartedCompilation(
                    compiler = compiler,
                    prepared = prepared,
                    callback = callback,
                    ticket = ticket,
                )
            }

            val started = leased.value
            val jobToken = requireAcceptedTicket(started.ticket, requestId)
            val acknowledgement = cancellationRequest(
                requestId = requestId,
                jobToken = jobToken,
                operation = TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE,
            )
            if (!started.compiler.cancelCompilation(acknowledgement)) {
                throw TypeScriptCompilerException(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "The compiler did not acknowledge the accepted compilation.",
                )
            }

            val terminalBundle = try {
                withTimeout(timeoutMs + LOCAL_TERMINAL_GRACE_MS) {
                    started.callback.terminal.await()
                }
            } catch (error: TimeoutCancellationException) {
                runCatching {
                    started.compiler.cancelCompilation(
                        cancellationRequest(
                            requestId = requestId,
                            jobToken = jobToken,
                            operation = TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL,
                        ),
                    )
                }
                throw TypeScriptCompilerException(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    "The host did not receive a compiler terminal callback in time.",
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                    error,
                )
            }
            terminalReceived = true
            val terminal = terminalResult(terminalBundle, requestId)
            when (terminal.outcome) {
                TypeScriptCompilerContract.OUTCOME_EMITTED,
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                -> TypeScriptCompilationOutputReader.read(
                    outputFile = outputFile,
                    prepared = started.prepared,
                    terminal = terminal,
                )

                else -> throw TypeScriptCompilerException(
                    terminal.errorCode ?: TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    terminal.errorMessage ?: "TypeScript compilation did not produce output.",
                    terminal.outcome,
                )
            }
        } catch (error: TypeScriptCompilerException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TypeScriptCompilerException(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                error.message ?: "The TypeScript compiler is unavailable.",
                cause = error,
            )
        } finally {
            val started = leased?.value
            if (started != null && !terminalReceived && started.ticket.accepted()) {
                started.ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN)
                    ?.let { jobToken ->
                        runCatching {
                            started.compiler.cancelCompilation(
                                cancellationRequest(
                                    requestId = requestId,
                                    jobToken = jobToken,
                                    operation =
                                        TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL,
                                ),
                            )
                        }
                    }
            }
            runCatching { leased?.bindingLease?.close() }
            sourceBytes.fill(0)
            runCatching { inputFile.delete() }
            runCatching { outputFile.delete() }
        }
    }

    private fun requireContract(compiler: ITypeScriptCompilerPlugin) {
        if (
            compiler.contractVersion != TypeScriptCompilerContract.VERSION ||
            compiler.contractHash != TypeScriptCompilerContract.HASH
        ) {
            throw TypeScriptCompilerException(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "The TypeScript compiler Binder contract is incompatible.",
            )
        }
    }

    private fun authorityFrom(info: Bundle): TypeScriptRhinoCompilerAuthority {
        info.throwCompilerErrorIfPresent()
        requireBundleInt(
            info,
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        requireBundleString(
            info,
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        requireBundleBoolean(info, TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, true)
        requireBundleBoolean(info, TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION, true)
        requireBundleBoolean(info, TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS, true)
        requireBundleBoolean(info, TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3, true)
        requireBundleBoolean(info, TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, false)
        requireBundleString(
            info,
            TypeScriptPluginCapabilityKeys.COMPILER_SERVICE_ACTION,
            TypeScriptPluginActions.COMPILER,
        )
        requireBundleInt(
            info,
            TypeScriptPluginCapabilityKeys.INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        requireBundleInt(
            info,
            TypeScriptPluginCapabilityKeys.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        requireBundleInt(
            info,
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        requireBundleInt(
            info,
            TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
        )
        requireBundleInt(
            info,
            TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
        )
        requireBundleInt(
            info,
            TypeScriptPluginCapabilityKeys.PROFILE_SCHEMA_REVISION,
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        )
        requireBundleInt(
            info,
            TypeScriptPluginCapabilityKeys.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        require(
            info.getStringArray(TypeScriptPluginCapabilityKeys.TARGET_PROFILES)
                ?.contains(TypeScriptCompilerContract.PROFILE_RHINO) == true,
        ) {
            "The TypeScript compiler does not advertise the Rhino profile."
        }
        require(
            info.getStringArray(TypeScriptPluginCapabilityKeys.RHINO_SUPPORTED_EXTENSIONS)
                ?.contains(".ts") == true,
        ) {
            "The TypeScript compiler does not advertise Rhino .ts input."
        }
        requireBundleString(
            info,
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        )
        requireBundleInt(
            info,
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        )
        requireBundleInt(
            info,
            TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        )
        val status = requireNotNull(
            info.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
        )
        require(status == TypeScriptCompilerContract.STATUS_READY) {
            "The TypeScript compiler did not become ready after prewarm."
        }
        requireBundleString(
            info,
            TypeScriptCompilerContract.KEY_BACKEND,
            TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
        )

        return TypeScriptRhinoCompilerAuthority(
            compilerVersion = info.requireString(
                TypeScriptPluginCapabilityKeys.COMPILER_VERSION,
            ),
            compilerDistributionFingerprint = info.requireString(
                TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            ),
            declarationsFingerprint = info.requireString(
                TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
            ),
            declarationProfileFingerprint = info.requireString(
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            ),
            runtimeCapabilitiesFingerprint = info.requireString(
                TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            ),
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = info.getInt(TypeScriptR2Contract.KEY_PROFILE_REVISION, -1),
            configSchemaRevision = info.getInt(
                TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
                -1,
            ),
            configKind = info.requireString(TypeScriptR2Contract.KEY_CONFIG_KIND),
            configPolicyRevision = info.getInt(
                TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
                -1,
            ),
            configPolicyFingerprint = info.requireString(
                TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
            ),
        )
    }

    private fun startCompilation(
        compiler: ITypeScriptCompilerPlugin,
        prepared: TypeScriptPreparedCompilation,
        outputFile: File,
        timeoutMs: Long,
        callback: ITypeScriptCompilationCallback,
    ): Bundle {
        val inputDescriptor = ParcelFileDescriptor.open(
            prepared.archiveFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val outputDescriptor = ParcelFileDescriptor.open(
            outputFile,
            ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return try {
            compiler.startCompilation(
                compilationRequest(prepared, timeoutMs),
                inputDescriptor,
                outputDescriptor,
                callback,
            )
        } finally {
            inputDescriptor.close()
            outputDescriptor.close()
        }
    }

    private fun compilationRequest(
        prepared: TypeScriptPreparedCompilation,
        timeoutMs: Long,
    ) = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putInt(
            TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, prepared.requestId)
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, prepared.projectId)
        putString(TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI, prepared.entrySourceUri)
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            TypeScriptCompilerContract.PROFILE_RHINO,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILATION_MODE,
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        putString(TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}")
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs)
        putInt(
            TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
            prepared.authority.profileSchemaRevision,
        )
        putInt(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        putInt(TypeScriptR2Contract.KEY_PROFILE_REVISION, prepared.authority.profileRevision)
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
            prepared.authority.configSchemaRevision,
        )
        putString(TypeScriptR2Contract.KEY_CONFIG_KIND, prepared.authority.configKind)
        putLong(TypeScriptR2Contract.KEY_CONFIG_BYTE_LENGTH, prepared.configByteLength)
        putString(TypeScriptR2Contract.KEY_CONFIG_SHA256, prepared.configSha256)
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
            prepared.authority.configPolicyRevision,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
            prepared.authority.configPolicyFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            prepared.authority.compilerDistributionFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
            prepared.authority.declarationsFingerprint,
        )
        putString(
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            prepared.authority.declarationProfileFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            prepared.authority.runtimeCapabilitiesFingerprint,
        )
        putString(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
            prepared.inputArchiveSha256,
        )
        putString(
            TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
            prepared.requestManifestSha256,
        )
    }

    private fun prewarmRequest(timeoutMs: Long) = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs)
    }

    private fun cancellationRequest(
        requestId: String,
        jobToken: String,
        operation: String,
    ) = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_JOB_TOKEN, jobToken)
        putString(TypeScriptR2Contract.KEY_CANCELLATION_OPERATION, operation)
    }

    private fun requireAcceptedTicket(ticket: Bundle, requestId: String): String {
        requireBundleInt(
            ticket,
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        requireBundleString(
            ticket,
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        requireBundleString(ticket, TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        if (!ticket.accepted()) {
            throw TypeScriptCompilerException(
                ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE)
                    ?: TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                ticket.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE)
                    ?: "The TypeScript compiler rejected the request.",
            )
        }
        require(
            ticket.getBoolean(TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_REQUIRED, false),
        )
        require(
            ticket.getLong(TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS, -1L) in
                1L..TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
        )
        return ticket.requireString(TypeScriptCompilerContract.KEY_JOB_TOKEN).also { token ->
            require(token.length <= TypeScriptCompilerContract.MAX_JOB_TOKEN_CHARS)
        }
    }

    private fun terminalResult(bundle: Bundle, requestId: String): TypeScriptTerminalResult {
        requireBundleString(bundle, TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        val outcome = bundle.requireString(TypeScriptCompilerContract.KEY_OUTCOME)
        require(outcome in TypeScriptCompilerContract.TERMINAL_OUTCOMES) {
            "The TypeScript compiler returned an unsupported terminal outcome."
        }
        if (
            outcome == TypeScriptCompilerContract.OUTCOME_EMITTED ||
            outcome == TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
        ) {
            requireBundleInt(
                bundle,
                TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
            )
            requireBundleInt(
                bundle,
                TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
                TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
            )
        }
        val diagnosticCount = bundle.getInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, -1)
        val emittedModuleCount =
            bundle.getInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, -1)
        require(diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        require(
            emittedModuleCount in 0..
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
        return TypeScriptTerminalResult(
            requestId = requestId,
            outcome = outcome,
            succeeded = bundle.getBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, false),
            errorCode = bundle.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            errorMessage = bundle.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
            diagnosticCount = diagnosticCount,
            emittedModuleCount = emittedModuleCount,
            outputManifestSha256 =
                bundle.getString(TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256),
        )
    }

    private fun Bundle.throwCompilerErrorIfPresent() {
        val code = getString(TypeScriptCompilerContract.KEY_ERROR_CODE) ?: return
        throw TypeScriptCompilerException(
            code,
            getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE)
                ?: "The TypeScript compiler reported an error.",
        )
    }

    private fun Bundle.toPluginInfo(): PluginInfo {
        val version = getString(TypeScriptPluginCapabilityKeys.COMPILER_VERSION)
            ?.takeIf(String::isNotBlank)
            ?: "unknown"
        return PluginInfo(
            name = MODULE_LABEL,
            description = null,
            instruction = null,
            author = "",
            collaborators = null,
            versionName = version,
            versionCode = 0L,
            versionDate = null,
            id = TypeScriptPluginIds.ID,
            engine = TypeScriptPluginIds.ENGINE,
            variant = TypeScriptPluginIds.variant(version),
            supportedAbis = null,
            capabilities = Bundle(this),
        )
    }

    private fun readBoundedSource(file: File): ByteArray {
        if (
            file.length() < 0L ||
            file.length() >
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES
        ) {
            throw IOException("TypeScript source exceeds the ordinary per-file byte limit.")
        }
        FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream(file.length().toInt().coerceAtLeast(32))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total = Math.addExact(total, count.toLong())
                if (
                    total + EMPTY_TSCONFIG_BYTES >
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES
                ) {
                    throw IOException("TypeScript source exceeds the ordinary input byte limit.")
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun percentEncode(value: String): String {
        val bytes = TypeScriptUtf8.encode(value, "TypeScript source name")
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (
                    unsigned in 'a'.code..'z'.code ||
                    unsigned in 'A'.code..'Z'.code ||
                    unsigned in '0'.code..'9'.code ||
                    unsigned == '-'.code ||
                    unsigned == '.'.code ||
                    unsigned == '_'.code ||
                    unsigned == '~'.code
                ) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
        }
    }

    private fun requireBundleBoolean(bundle: Bundle, key: String, expected: Boolean) {
        require(bundle.containsKey(key) && bundle.getBoolean(key) == expected) {
            "The TypeScript compiler capability $key is incompatible."
        }
    }

    private fun requireBundleInt(bundle: Bundle, key: String, expected: Int) {
        require(bundle.containsKey(key) && bundle.getInt(key) == expected) {
            "The TypeScript compiler field $key is incompatible."
        }
    }

    private fun requireBundleString(bundle: Bundle, key: String, expected: String) {
        require(bundle.getString(key) == expected) {
            "The TypeScript compiler field $key is incompatible."
        }
    }

    private fun Bundle.requireString(key: String): String =
        getString(key)?.takeIf(String::isNotEmpty)
            ?: error("The TypeScript compiler field $key is missing.")

    private fun Bundle.accepted(): Boolean =
        containsKey(TypeScriptCompilerContract.KEY_ACCEPTED) &&
            getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED)

    private data class StartedCompilation(
        val compiler: ITypeScriptCompilerPlugin,
        val prepared: TypeScriptPreparedCompilation,
        val callback: CompilationCallback,
        val ticket: Bundle,
    )

    private class CompilationCallback : ITypeScriptCompilationCallback.Stub() {
        val terminal = CompletableDeferred<Bundle>()

        override fun onProgress(progress: Bundle?) = Unit

        override fun onCompleted(result: Bundle?) {
            if (result == null) {
                terminal.completeExceptionally(
                    IllegalStateException("The TypeScript compiler returned a null terminal result."),
                )
            } else {
                terminal.complete(result)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "TypeScriptCompiler"
        const val MODULE_LABEL = "TypeScript compiler"
        const val LOCAL_TERMINAL_GRACE_MS = 5_000L
        const val EMPTY_TSCONFIG_BYTES = 2L
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}
