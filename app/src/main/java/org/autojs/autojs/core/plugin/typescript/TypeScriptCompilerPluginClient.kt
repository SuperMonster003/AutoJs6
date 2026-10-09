package org.autojs.autojs.core.plugin.typescript

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.App
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.ServiceBindingLease
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
    private val performanceReporter: TypeScriptCompilationPerformanceReporter =
        TypeScriptCompilationPerformanceReporter.LOGCAT,
) {

    private val appContext = context.applicationContext
    private val host = AidlPluginHost(
        action = TypeScriptPluginActions.COMPILER,
        tag = LOG_TAG,
        moduleLabel = MODULE_LABEL,
        proxyFactory = ITypeScriptCompilerPlugin.Stub::asInterface,
        infoReader = { compiler -> compiler.compilerInfo.toPluginInfo() },
        // Selection only needs the compiler's static authority fingerprints; reusing them for an
        // unchanged installed plugin keeps the compiler process out of the cache-hit path.
        cacheDiscovery = true,
    )

    suspend fun isAvailable(): Boolean = host.select(
        context = appContext,
        engineId = TypeScriptPluginIds.ID,
        engine = TypeScriptPluginIds.ENGINE,
    ) != null

    internal suspend fun openWarmBinding(
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
    ): TypeScriptCompilerWarmBinding = withContext(Dispatchers.IO) {
        require(timeoutMs in TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS..
            TypeScriptCompilerContract.MAX_TIMEOUT_MS)
        val target = host.selectOrThrow(
            context = appContext,
            engineId = TypeScriptPluginIds.ID,
            engine = TypeScriptPluginIds.ENGINE,
            label = MODULE_LABEL,
        )
        val leased = host.callWithDedicatedBindingLease(
            context = appContext,
            target = target,
            retryOnRemoteException = false,
        ) { compiler ->
            requireContract(compiler)
            val prewarmResult = compiler.prewarm(prewarmRequest(timeoutMs))
            prewarmResult.throwCompilerErrorIfPresent()
            authorityFrom(
                info = prewarmResult,
                targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
                sourceName = PREWARM_SOURCE_NAME,
                requireReady = true,
            )
            WarmCompiler(
                binder = compiler.asBinder(),
                projectSessionRevision = negotiatedProjectSessionRevision(prewarmResult),
            )
        }
        TypeScriptCompilerWarmBinding(
            bindingLease = leased.bindingLease,
            binder = leased.value.binder,
            projectSessionRevision = leased.value.projectSessionRevision,
        )
    }

    suspend fun compileSingleFile(
        sourceFile: File,
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        sourceName: String = sourceFile.name,
        sourceIdentity: String? = null,
        targetProfile: String = TypeScriptCompilerContract.PROFILE_RHINO,
        sourceContentReader: TypeScriptSourceContentReader =
            PlaintextTypeScriptSourceContentReader,
        usePersistentCache: Boolean = true,
    ): TypeScriptCompilationOutput = withContext(Dispatchers.IO) {
        require(sourceFile.isFile) { "TypeScript source is not a regular file." }
        validateSingleSourceRequest(timeoutMs, sourceName, targetProfile)

        (appContext as? App)?.cancelDeferredTypeScriptCompilerPrewarm()

        observeCompilation(targetProfile, usePersistentCache) { performance ->
            val sourceBytes = sourceContentReader.read(
                sourceFile,
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            )
            try {
                val canonicalPath = sourceIdentity?.takeIf(String::isNotBlank)
                    ?: runCatching { sourceFile.canonicalPath }
                        .getOrElse { sourceFile.absolutePath }
                compileSingleSourceBytes(
                    sourceBytes = sourceBytes,
                    timeoutMs = timeoutMs,
                    sourceName = sourceName,
                    sourceIdentity = canonicalPath,
                    targetProfile = targetProfile,
                    usePersistentCache = usePersistentCache,
                    performance = performance,
                )
            } finally {
                sourceBytes.fill(0)
            }
        }
    }

    suspend fun compileSingleSource(
        source: String,
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        sourceName: String = "main.ts",
        sourceIdentity: String = "autojs://string/$sourceName",
        targetProfile: String = TypeScriptCompilerContract.PROFILE_RHINO,
        usePersistentCache: Boolean = true,
    ): TypeScriptCompilationOutput = withContext(Dispatchers.IO) {
        validateSingleSourceRequest(timeoutMs, sourceName, targetProfile)
        require(
            source.length <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        ) { "TypeScript source exceeds the ordinary single-file byte limit." }

        (appContext as? App)?.cancelDeferredTypeScriptCompilerPrewarm()

        observeCompilation(targetProfile, usePersistentCache) { performance ->
            val sourceBytes = TypeScriptUtf8.encode(source, "TypeScript source")
            try {
                require(
                    sourceBytes.size <=
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
                ) { "TypeScript source exceeds the ordinary single-file byte limit." }
                compileSingleSourceBytes(
                    sourceBytes = sourceBytes,
                    timeoutMs = timeoutMs,
                    sourceName = sourceName,
                    sourceIdentity = sourceIdentity,
                    targetProfile = targetProfile,
                    usePersistentCache = usePersistentCache,
                    performance = performance,
                )
            } finally {
                sourceBytes.fill(0)
            }
        }
    }

    private fun validateSingleSourceRequest(
        timeoutMs: Long,
        sourceName: String,
        targetProfile: String,
    ) {
        require(timeoutMs in TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS..
            TypeScriptCompilerContract.MAX_TIMEOUT_MS)
        require(
            listOf(".d.ts", ".d.mts", ".d.cts").none { declarationExtension ->
                sourceName.endsWith(declarationExtension, ignoreCase = true)
            },
        ) {
            "TypeScript declaration files are not executable entries."
        }
        require(targetProfile in TypeScriptCompilerContract.TARGET_PROFILES)
        require(
            TypeScriptSourceExtensions.forProfile(targetProfile)
                .any(sourceName::endsWith),
        ) {
            "The $targetProfile TypeScript profile does not accept $sourceName."
        }
        require(
            targetProfile != TypeScriptCompilerContract.PROFILE_NODE ||
                sourceName.endsWith(".mts") || sourceName.endsWith(".cts"),
        ) {
            "Single-file Node .ts compilation requires project package metadata; " +
                "use .mts/.cts until project compilation is enabled."
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
    }

    private suspend fun compileSingleSourceBytes(
        sourceBytes: ByteArray,
        timeoutMs: Long,
        sourceName: String,
        sourceIdentity: String,
        targetProfile: String,
        usePersistentCache: Boolean,
        performance: TypeScriptCompilationPerformanceRecorder,
    ): TypeScriptCompilationOutput {
        require(
            sourceBytes.size.toLong() + EMPTY_TSCONFIG_BYTES <=
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        ) {
            "TypeScript source exceeds the ordinary input byte limit."
        }
        val canonicalIdentity = sourceIdentity.takeIf(String::isNotBlank)
            ?: "autojs://string/$sourceName"
        val projectId = "single-file-" + sha256(
            TypeScriptUtf8.encode(canonicalIdentity, "TypeScript source identity"),
        ).take(32)
        return compileArchive(
            timeoutMs = timeoutMs,
            targetProfile = targetProfile,
            entrySourceName = sourceName,
            usePersistentCache = usePersistentCache,
            performance = performance,
        ) { requestId, authority, inputFile ->
            TypeScriptSingleFileArchive.write(
                sourceName = sourceName,
                sourceBytes = sourceBytes,
                requestId = requestId,
                projectId = projectId,
                authority = authority,
                destination = inputFile,
            )
        }
    }

    suspend fun compileProject(
        projectRoot: File,
        entryFile: File,
        targetProfile: String,
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        sourceContentReader: TypeScriptSourceContentReader =
            PlaintextTypeScriptSourceContentReader,
        usePersistentCache: Boolean = true,
        sourceOverlays: Map<String, ByteArray> = emptyMap(),
    ): TypeScriptCompilationOutput = withContext(Dispatchers.IO) {
        require(timeoutMs in TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS..
            TypeScriptCompilerContract.MAX_TIMEOUT_MS)
        val root = runCatching { projectRoot.canonicalFile }.getOrElse { projectRoot.absoluteFile }
        val entry = runCatching { entryFile.canonicalFile }.getOrElse { entryFile.absoluteFile }
        require(root.isDirectory) { "TypeScript project root is not a directory." }
        require(entry.isFile) { "TypeScript project entry is not a regular file." }
        require(targetProfile in TypeScriptCompilerContract.TARGET_PROFILES)
        require(
            TypeScriptSourceExtensions.forProfile(targetProfile)
                .any(entry.name::endsWith),
        ) {
            "The $targetProfile TypeScript profile does not accept ${entry.name}."
        }
        require(
            listOf(".d.ts", ".d.mts", ".d.cts").none(entry.name::endsWith),
        ) {
            "TypeScript declaration files are not executable entries."
        }
        (appContext as? App)?.cancelDeferredTypeScriptCompilerPrewarm()
        observeCompilation(targetProfile, usePersistentCache) { performance ->
            val projectId = "directory-" + sha256(
                TypeScriptUtf8.encode(root.path, "TypeScript project root"),
            ).take(32)
            compileArchive(
                timeoutMs = timeoutMs,
                targetProfile = targetProfile,
                entrySourceName = entry.name,
                usePersistentCache = usePersistentCache,
                performance = performance,
            ) { requestId, authority, inputFile ->
                TypeScriptProjectArchive.write(
                    projectRoot = root,
                    entryFile = entry,
                    requestId = requestId,
                    projectId = projectId,
                    authority = authority,
                    destination = inputFile,
                    sourceContentReader = sourceContentReader,
                    sourceOverlays = sourceOverlays,
                )
            }
        }
    }

    private suspend fun observeCompilation(
        targetProfile: String,
        usePersistentCache: Boolean,
        compile: suspend (TypeScriptCompilationPerformanceRecorder) ->
            TypeScriptCompilationOutput,
    ): TypeScriptCompilationOutput {
        val performance = TypeScriptCompilationPerformanceRecorder(
            targetProfile = targetProfile,
            cacheEnabled = usePersistentCache,
            reporter = performanceReporter,
        )
        return try {
            compile(performance).also(performance::recordSuccess)
        } catch (error: Throwable) {
            performance.recordFailure(error)
            throw error
        } finally {
            performance.finish()
        }
    }

    private suspend fun compileArchive(
        timeoutMs: Long,
        targetProfile: String,
        entrySourceName: String,
        usePersistentCache: Boolean,
        performance: TypeScriptCompilationPerformanceRecorder,
        prepare: (String, TypeScriptCompilerAuthority, File) -> TypeScriptPreparedCompilation,
    ): TypeScriptCompilationOutput {
        val requestId = "host-" + UUID.randomUUID()
        val temporaryArchives = TypeScriptCompilerTemporaryArchives.create(appContext.cacheDir)
        val inputFile = temporaryArchives.inputFile
        val outputFile = temporaryArchives.outputFile
        var bindingLease: ServiceBindingLease? = null
        var started: StartedCompilation? = null
        var terminalReceived = false

        return try {
            val awaitPrewarmStartedAt = performance.checkpoint()
            TypeScriptCompilerStartupPrewarm.awaitPendingPrewarm()
            performance.recordAwaitPrewarmSince(awaitPrewarmStartedAt)
            val discoveryStartedAt = performance.checkpoint()
            val target = host.selectOrThrow(
                context = appContext,
                engineId = TypeScriptPluginIds.ID,
                engine = TypeScriptPluginIds.ENGINE,
                label = MODULE_LABEL,
            )
            val advertisedInfo = requireNotNull(target.pluginInfo?.capabilities) {
                "The TypeScript compiler did not publish capabilities."
            }
            val advertisedAuthority = authorityFrom(
                info = advertisedInfo,
                targetProfile = targetProfile,
                sourceName = entrySourceName,
                requireReady = false,
            )
            performance.recordDiscoverySince(discoveryStartedAt)
            val prepareStartedAt = performance.checkpoint()
            var prepared = prepare(requestId, advertisedAuthority, inputFile)
            performance.recordPreparedSince(
                startedAtNanos = prepareStartedAt,
                inputArchiveBytes = prepared.archiveFile.length(),
                sourceFileCount = prepared.sourceFiles.size,
            )

            compilationMutex.withLock {
                var cacheKey = TypeScriptCompilationCacheKey.sha256(prepared)
                val cache = if (usePersistentCache) {
                    compilationCacheOrNull(appContext)
                } else {
                    null
                }
                performance.recordCacheAvailability(cache != null)
                readCachedWithPerformance(cache, cacheKey, prepared, performance)?.let { cached ->
                    return@withLock cached
                }

                // From here on the script thread waits for the plugin; tell the user once.
                val progressNotice =
                    TypeScriptCompilationProgressNotices.compilationStarted(appContext)
                val warmBindingGeneration =
                    TypeScriptCompilerStartupPrewarm.generationToken()
                val bindAndPrewarmStartedAt = performance.checkpoint()
                val leased = try {
                    host.callWithDedicatedBindingLease(
                        context = appContext,
                        target = target,
                        retryOnRemoteException = false,
                    ) { compiler ->
                        requireContract(compiler)
                        val prewarmResult = compiler.prewarm(prewarmRequest(timeoutMs))
                        prewarmResult.throwCompilerErrorIfPresent()
                        val readyAuthority = authorityFrom(
                            info = prewarmResult,
                            targetProfile = targetProfile,
                            sourceName = entrySourceName,
                            requireReady = true,
                        )
                        PreparedCompiler(
                            compiler = compiler,
                            authority = readyAuthority,
                            projectSessionRevision =
                                negotiatedProjectSessionRevision(prewarmResult),
                        )
                    }
                } finally {
                    performance.recordBindAndPrewarmSince(bindAndPrewarmStartedAt)
                }
                bindingLease = leased.bindingLease
                val ready = leased.value
                if (ready.authority != prepared.authority) {
                    val additionalPreparationStartedAt = performance.checkpoint()
                    prepared = prepare(requestId, ready.authority, inputFile)
                    performance.recordAdditionalPreparationSince(
                        startedAtNanos = additionalPreparationStartedAt,
                        inputArchiveBytes = prepared.archiveFile.length(),
                        sourceFileCount = prepared.sourceFiles.size,
                    )
                    cacheKey = TypeScriptCompilationCacheKey.sha256(prepared)
                    readCachedWithPerformance(
                        cache,
                        cacheKey,
                        prepared,
                        performance,
                    )?.let { cached ->
                        return@withLock cached
                    }
                }

                val callback = CompilationCallback(
                    expectedRequestId = requestId,
                    performance = performance,
                )
                val remoteRoundTripStartedAt = performance.checkpoint()
                val terminalBundle = try {
                    val ticket = startCompilation(
                        compiler = ready.compiler,
                        prepared = prepared,
                        outputFile = outputFile,
                        timeoutMs = timeoutMs,
                        projectSessionRevision = ready.projectSessionRevision,
                        callback = callback,
                    )
                    val active = StartedCompilation(
                        compiler = ready.compiler,
                        prepared = prepared,
                        callback = callback,
                        ticket = ticket,
                    )
                    started = active
                    val jobToken = requireAcceptedTicket(ticket, requestId)
                    val acknowledgement = cancellationRequest(
                        requestId = requestId,
                        jobToken = jobToken,
                        operation = TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE,
                    )
                    if (!active.compiler.cancelCompilation(acknowledgement)) {
                        throw TypeScriptCompilerException(
                            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                            "The compiler did not acknowledge the accepted compilation.",
                        )
                    }

                    try {
                        withTimeout(timeoutMs + LOCAL_TERMINAL_GRACE_MS) {
                            active.callback.terminal.await()
                        }
                    } catch (error: TimeoutCancellationException) {
                        runCatching {
                            active.compiler.cancelCompilation(
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
                } finally {
                    performance.recordRemoteRoundTripSince(remoteRoundTripStartedAt)
                }
                terminalReceived = true
                val terminal = terminalResult(terminalBundle, requestId)
                performance.recordCompilerElapsed(terminal.elapsedMs)
                val output = when (terminal.outcome) {
                    TypeScriptCompilerContract.OUTCOME_EMITTED,
                    TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                    -> {
                        val outputProcessingStartedAt = performance.checkpoint()
                        val read = try {
                            TypeScriptCompilationOutputReader.read(
                                outputFile = outputFile,
                                prepared = prepared,
                                terminal = terminal,
                            )
                        } finally {
                            performance.recordOutputProcessingSince(
                                outputProcessingStartedAt,
                            )
                        }
                        val cachePublishStartedAt = performance.checkpoint()
                        try {
                            publishCached(cache, cacheKey, prepared, terminal, outputFile)
                        } finally {
                            performance.recordCachePublishSince(cachePublishStartedAt)
                        }
                        TypeScriptCompilationProgressNotices.compilationFinished(
                            appContext,
                            progressNotice,
                        )
                        read
                    }

                    else -> throw TypeScriptCompilerException(
                        terminal.errorCode
                            ?: TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        terminal.errorMessage
                            ?: "TypeScript compilation did not produce output.",
                        terminal.outcome,
                    )
                }
                if (ready.projectSessionRevision != null) {
                    TypeScriptCompilerStartupPrewarm.retainCompilationBinding(
                        TypeScriptCompilerWarmBinding(
                            bindingLease = requireNotNull(bindingLease),
                            binder = ready.compiler.asBinder(),
                            projectSessionRevision = ready.projectSessionRevision,
                        ),
                        expectedGeneration = warmBindingGeneration,
                    )
                    bindingLease = null
                }
                output
            }
        } catch (error: TypeScriptCompilerException) {
            throw error
        } catch (error: TypeScriptDependencyBoundaryException) {
            throw TypeScriptCompilerException(
                code = error.code,
                message = error.detail,
                cause = error,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw TypeScriptCompilerException(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                error.message ?: "The TypeScript compiler is unavailable.",
                cause = error,
            )
        } finally {
            val active = started
            if (active != null && !terminalReceived && active.ticket.accepted()) {
                active.ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN)
                    ?.let { jobToken ->
                        runCatching {
                            active.compiler.cancelCompilation(
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
            runCatching { bindingLease?.close() }
            try {
                temporaryArchives.close()
            } catch (error: IOException) {
                Log.e(LOG_TAG, "TypeScript compiler temporary archive cleanup failed.", error)
                throw TypeScriptCompilerException(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Unable to securely delete TypeScript compiler temporary archives.",
                    cause = error,
                )
            }
        }
    }

    private fun readCachedWithPerformance(
        cache: TypeScriptCompilationPersistentCache?,
        cacheKey: String,
        prepared: TypeScriptPreparedCompilation,
        performance: TypeScriptCompilationPerformanceRecorder,
    ): TypeScriptCompilationOutput? {
        val startedAt = performance.checkpoint()
        return try {
            readCached(cache, cacheKey, prepared)?.also {
                performance.recordCacheHit()
            }
        } finally {
            performance.recordCacheLookupSince(startedAt)
        }
    }

    private fun readCached(
        cache: TypeScriptCompilationPersistentCache?,
        cacheKey: String,
        prepared: TypeScriptPreparedCompilation,
    ): TypeScriptCompilationOutput? {
        if (cache == null) return null
        val hit = try {
            cache.lookup(cacheKey)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "TypeScript cache lookup failed; compiling normally.", error)
            null
        } ?: return null
        return try {
            TypeScriptCompilationOutputReader.read(
                outputFile = hit.outputFile,
                prepared = hit.entry.replayPrepared(prepared),
                terminal = hit.entry.terminal,
            ).also {
                Log.i(LOG_TAG, "TypeScript compilation cache hit: ${cacheKey.take(12)}")
            }
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Discarding an invalid TypeScript compilation cache entry.", error)
            runCatching { cache.invalidate(hit.entry) }
            null
        }
    }

    private fun publishCached(
        cache: TypeScriptCompilationPersistentCache?,
        cacheKey: String,
        prepared: TypeScriptPreparedCompilation,
        terminal: TypeScriptTerminalResult,
        outputFile: File,
    ) {
        if (cache == null) return
        try {
            cache.publish(cacheKey, prepared, terminal, outputFile)
            Log.i(LOG_TAG, "Published TypeScript compilation cache: ${cacheKey.take(12)}")
        } catch (error: Exception) {
            Log.w(LOG_TAG, "TypeScript cache publication failed; keeping compiler output.", error)
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

    private fun authorityFrom(
        info: Bundle,
        targetProfile: String,
        sourceName: String,
        requireReady: Boolean,
    ): TypeScriptCompilerAuthority {
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
                ?.contains(targetProfile) == true,
        ) {
            "The TypeScript compiler does not advertise the $targetProfile profile."
        }
        val supportedExtensionsKey = when (targetProfile) {
            TypeScriptCompilerContract.PROFILE_RHINO ->
                TypeScriptPluginCapabilityKeys.RHINO_SUPPORTED_EXTENSIONS
            TypeScriptCompilerContract.PROFILE_NODE ->
                TypeScriptPluginCapabilityKeys.NODE_SUPPORTED_EXTENSIONS
            else -> error("Unsupported TypeScript target profile: $targetProfile")
        }
        val sourceExtension = TypeScriptSourceExtensions.forProfile(targetProfile)
            .single { extension -> sourceName.endsWith(extension) }
        require(
            info.getStringArray(supportedExtensionsKey)?.contains(sourceExtension) == true,
        ) {
            "The TypeScript compiler does not advertise $targetProfile $sourceExtension input."
        }
        val profileRevisionKey: String
        val configSchemaRevisionKey: String
        val configKindKey: String
        val declarationProfileFingerprintKey: String
        val runtimeCapabilitiesFingerprintKey: String
        if (targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
            profileRevisionKey = TypeScriptR2Contract.KEY_NODE_PROFILE_REVISION
            configSchemaRevisionKey = TypeScriptR2Contract.KEY_NODE_CONFIG_SCHEMA_REVISION
            configKindKey = TypeScriptR2Contract.KEY_NODE_CONFIG_KIND
            declarationProfileFingerprintKey =
                TypeScriptR2Contract.KEY_NODE_DECLARATION_PROFILE_FINGERPRINT
            runtimeCapabilitiesFingerprintKey =
                TypeScriptR2Contract.KEY_NODE_RUNTIME_CAPABILITIES_FINGERPRINT
        } else {
            profileRevisionKey = TypeScriptR2Contract.KEY_PROFILE_REVISION
            configSchemaRevisionKey = TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION
            configKindKey = TypeScriptR2Contract.KEY_CONFIG_KIND
            declarationProfileFingerprintKey =
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT
            runtimeCapabilitiesFingerprintKey =
                TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT
        }
        val configKind = info.requireString(configKindKey)
        require(
            configKind == if (targetProfile == TypeScriptCompilerContract.PROFILE_NODE) {
                TypeScriptR2Contract.CONFIG_KIND_NONE
            } else {
                TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
            },
        ) {
            "The TypeScript compiler advertises an incompatible $targetProfile config kind."
        }
        requireBundleInt(
            info,
            configSchemaRevisionKey,
            if (configKind == TypeScriptR2Contract.CONFIG_KIND_NONE) {
                TypeScriptCompilerAuthority.CONFIG_NONE_SCHEMA_REVISION
            } else {
                TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION
            },
        )
        if (configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG) {
            requireBundleInt(
                info,
                TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
                TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            )
        }
        val status = requireNotNull(
            info.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
        )
        require(
            if (requireReady) {
                status == TypeScriptCompilerContract.STATUS_READY
            } else {
                status in COMPILER_SERVICE_STATUSES
            },
        ) {
            if (requireReady) {
                "The TypeScript compiler did not become ready after prewarm."
            } else {
                "The TypeScript compiler advertised an unknown lifecycle state."
            }
        }
        requireBundleString(
            info,
            TypeScriptCompilerContract.KEY_BACKEND,
            TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
        )

        return TypeScriptCompilerAuthority(
            targetProfile = targetProfile,
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
                declarationProfileFingerprintKey,
            ),
            runtimeCapabilitiesFingerprint = info.requireString(
                runtimeCapabilitiesFingerprintKey,
            ),
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = info.getInt(profileRevisionKey, -1),
            configSchemaRevision = info.getInt(configSchemaRevisionKey, -1),
            configKind = configKind,
            configPolicyRevision = if (
                configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
            ) {
                info.getInt(TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION, -1)
            } else {
                null
            },
            configPolicyFingerprint = if (
                configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
            ) {
                info.requireString(TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT)
            } else {
                null
            },
            packageMetadataSchemaRevision = if (
                targetProfile == TypeScriptCompilerContract.PROFILE_NODE
            ) {
                info.getInt(TypeScriptR2Contract.KEY_PACKAGE_METADATA_SCHEMA_REVISION, -1)
            } else {
                null
            },
            packageMetadataPolicyRevision = if (
                targetProfile == TypeScriptCompilerContract.PROFILE_NODE
            ) {
                info.getInt(TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_REVISION, -1)
            } else {
                null
            },
            packageMetadataPolicyFingerprint = if (
                targetProfile == TypeScriptCompilerContract.PROFILE_NODE
            ) {
                info.requireString(TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_FINGERPRINT)
            } else {
                null
            },
            dependencyLayerSchemaRevision =
                info.getInt(TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_SCHEMA_REVISION, -1),
            dependencyLayerPolicyRevision =
                info.getInt(TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_REVISION, -1),
            dependencyLayerPolicyFingerprint = info.requireString(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_FINGERPRINT,
            ),
            dependencyResolverPolicyRevision =
                info.getInt(TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_REVISION, -1),
            dependencyResolverPolicyFingerprint = info.requireString(
                TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            ),
        )
    }

    private fun startCompilation(
        compiler: ITypeScriptCompilerPlugin,
        prepared: TypeScriptPreparedCompilation,
        outputFile: File,
        timeoutMs: Long,
        projectSessionRevision: Int?,
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
                compilationRequest(prepared, timeoutMs, projectSessionRevision),
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
        projectSessionRevision: Int?,
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
        projectSessionRevision?.let { revision ->
            putInt(TypeScriptR2Contract.KEY_PROJECT_SESSION_REVISION, revision)
        }
        putString(TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI, prepared.entrySourceUri)
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            prepared.authority.targetProfile,
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
        prepared.configByteLength?.let { byteLength ->
            putLong(TypeScriptR2Contract.KEY_CONFIG_BYTE_LENGTH, byteLength)
            putString(TypeScriptR2Contract.KEY_CONFIG_SHA256, requireNotNull(prepared.configSha256))
            putInt(
                TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
                requireNotNull(prepared.authority.configPolicyRevision),
            )
            putString(
                TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
                requireNotNull(prepared.authority.configPolicyFingerprint),
            )
        }
        prepared.packageMetadata?.let { packageMetadata ->
            putInt(
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_SCHEMA_REVISION,
                requireNotNull(prepared.authority.packageMetadataSchemaRevision),
            )
            putInt(
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_REVISION,
                requireNotNull(prepared.authority.packageMetadataPolicyRevision),
            )
            putString(
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_FINGERPRINT,
                requireNotNull(prepared.authority.packageMetadataPolicyFingerprint),
            )
            putString(
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_INVENTORY_FINGERPRINT,
                packageMetadata.inventoryFingerprint,
            )
        }
        prepared.dependencyLayer?.let { dependencyLayer ->
            putInt(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_SCHEMA_REVISION,
                requireNotNull(prepared.authority.dependencyLayerSchemaRevision),
            )
            putInt(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_REVISION,
                requireNotNull(prepared.authority.dependencyLayerPolicyRevision),
            )
            putString(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_FINGERPRINT,
                requireNotNull(prepared.authority.dependencyLayerPolicyFingerprint),
            )
            putString(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_INVENTORY_FINGERPRINT,
                dependencyLayer.inventoryFingerprint,
            )
            putString(
                TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_FINGERPRINT,
                dependencyLayer.fingerprint,
            )
            putInt(
                TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_REVISION,
                requireNotNull(prepared.authority.dependencyResolverPolicyRevision),
            )
            putString(
                TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
                requireNotNull(prepared.authority.dependencyResolverPolicyFingerprint),
            )
        }
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

    private fun negotiatedProjectSessionRevision(info: Bundle): Int? =
        TypeScriptProjectSessionPolicy.negotiatedRevision(
            if (info.containsKey(TypeScriptR2Contract.KEY_PROJECT_SESSION_REVISION)) {
                runCatching {
                    info.getInt(TypeScriptR2Contract.KEY_PROJECT_SESSION_REVISION)
                }.getOrNull()
            } else {
                null
            },
        )

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
        val elapsedMs = bundle.getLong(TypeScriptCompilerContract.KEY_ELAPSED_MS, -1L)
        require(diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        require(
            emittedModuleCount in 0..
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
        require(elapsedMs >= 0L) {
            "The TypeScript compiler returned an invalid elapsed time."
        }
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
            elapsedMs = elapsedMs,
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

    private data class PreparedCompiler(
        val compiler: ITypeScriptCompilerPlugin,
        val authority: TypeScriptCompilerAuthority,
        val projectSessionRevision: Int?,
    )

    private data class WarmCompiler(
        val binder: IBinder,
        val projectSessionRevision: Int?,
    )

    private class CompilationCallback(
        private val expectedRequestId: String,
        private val performance: TypeScriptCompilationPerformanceRecorder,
    ) : ITypeScriptCompilationCallback.Stub() {
        val terminal = CompletableDeferred<Bundle>()

        override fun onProgress(progress: Bundle?) {
            if (progress == null) return
            runCatching {
                if (
                    progress.getString(TypeScriptCompilerContract.KEY_REQUEST_ID) !=
                    expectedRequestId ||
                    progress.getInt(
                        TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION,
                        -1,
                    ) != TypeScriptR2Contract.PROGRESS_SCHEMA_REVISION ||
                    progress.getString(TypeScriptR2Contract.KEY_PROGRESS_KIND) !=
                    TypeScriptR2Contract.PROGRESS_KIND_PHASE
                ) {
                    return@runCatching
                }
                val phase = progress.getString(TypeScriptR2Contract.KEY_PHASE)
                    ?: return@runCatching
                val state = progress.getString(TypeScriptR2Contract.KEY_PHASE_STATE)
                    ?: return@runCatching
                val elapsedNanos = progress.getLong(
                    TypeScriptR2Contract
                        .KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START,
                    -1L,
                )
                performance.recordCompilerPhase(
                    phase = phase,
                    state = state,
                    elapsedNanosSinceRequestStart = elapsedNanos,
                )
            }
        }

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
        const val PREWARM_SOURCE_NAME = "startup-prewarm.ts"
        const val LOCAL_TERMINAL_GRACE_MS = 5_000L
        const val EMPTY_TSCONFIG_BYTES = 2L
        const val CACHE_DIRECTORY_NAME = "typescript-compiler-cache-v1"
        val COMPILER_SERVICE_STATUSES = setOf(
            TypeScriptCompilerContract.STATUS_BOOTSTRAP,
            TypeScriptCompilerContract.STATUS_STARTING,
            TypeScriptCompilerContract.STATUS_READY,
            TypeScriptCompilerContract.STATUS_DEGRADED,
            TypeScriptCompilerContract.STATUS_FAILED,
        )
        val compilationMutex = Mutex()
        val cacheStores = mutableMapOf<String, TypeScriptCompilationPersistentCache>()

        fun compilationCacheOrNull(context: Context): TypeScriptCompilationPersistentCache? =
            try {
                val cacheParent = context.cacheDir.canonicalFile
                val root = File(cacheParent, CACHE_DIRECTORY_NAME).canonicalFile
                require(root.parentFile == cacheParent) {
                    "TypeScript compiler cache escaped the application cache directory."
                }
                if (!root.isDirectory) {
                    check(!root.exists() && root.mkdir()) {
                        "Unable to create the TypeScript compiler cache directory."
                    }
                }
                synchronized(cacheStores) {
                    cacheStores.getOrPut(root.path) {
                        TypeScriptCompilationPersistentCache(root)
                    }
                }
            } catch (error: Exception) {
                Log.w(LOG_TAG, "Persistent TypeScript compilation cache is unavailable.", error)
                null
            }
    }
}
