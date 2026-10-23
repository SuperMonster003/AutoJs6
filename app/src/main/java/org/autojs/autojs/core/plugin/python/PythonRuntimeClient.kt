package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.scope.BridgeExecutionScope
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonRuntimeChannelPolicy
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonExecutionMode
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonInputPrompt
import org.autojs.plugin.python.runtime.api.PythonInputReply
import org.autojs.plugin.python.runtime.api.PythonInteractiveInputPolicy
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonResultPolicy
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Minimal production client for the stable Python Runtime plugin host channel. */
internal class PythonRuntimeClient(
    context: Context,
    private val globalConsole: GlobalConsole,
) {

    private val applicationContext = context.applicationContext
    private val transport = PythonRuntimeOfficialTransport(applicationContext)

    fun createExecution(
        source: PythonFileSource,
        entryMode: PythonEntryMode = PythonEntryMode.FILE,
        stdinSnapshot: ByteArray? = null,
        interactiveInputGrant: PythonInteractiveInputGrant? = null,
        executionMode: PythonExecutionMode = PythonExecutionMode.BOUNDED,
        longRunningGrant: PythonLongRunningExecutionGrant? = null,
        configuredTimeoutMillis: Long? = null,
        engineId: Int = -1,
        engineStartedAtMillis: Long = 0L,
        stopSelf: () -> Unit = {},
    ): Execution {
        val interactiveInputController = interactiveInputGrant?.openController()
        val longRunningController = try {
            when (executionMode) {
                PythonExecutionMode.BOUNDED -> null
                PythonExecutionMode.LONG_RUNNING -> (
                    longRunningGrant
                        ?: throw PythonRuntimeHostException(
                            PythonRuntimeHostError.LONG_RUNNING_NOT_ALLOWED,
                            "Long-running Python execution requires a foreground user launch",
                        )
                    ).openController(source.semanticFile.name, stopSelf)
            }
        } catch (error: Throwable) {
            interactiveInputController?.close()
            throw error
        }
        return Execution(
            source,
            entryMode,
            stdinSnapshot?.copyOf(),
            interactiveInputController,
            executionMode,
            longRunningController,
            configuredTimeoutMillis,
            engineId,
            engineStartedAtMillis,
            stopSelf,
        )
    }

    inner class Execution internal constructor(
        private val source: PythonFileSource,
        private val entryMode: PythonEntryMode,
        stdinSnapshot: ByteArray?,
        private val interactiveInputController: PythonInteractiveInputController?,
        private val executionMode: PythonExecutionMode,
        private val longRunningController: PythonLongRunningExecutionController?,
        private val configuredTimeoutMillis: Long?,
        private val engineId: Int,
        private val engineStartedAtMillis: Long,
        private val stopSelf: () -> Unit,
    ) {

        private val cancelled = AtomicBoolean(false)
        private val terminalClaimed = AtomicBoolean(false)
        private val terminal = CountDownLatch(1)
        private val result = AtomicReference<PythonRuntimeExecutionResult?>()
        private val failure = AtomicReference<Throwable?>()
        private val pluginStateFailure = AtomicReference<PythonRuntimeHostException?>()
        private val binding = AtomicReference<PythonRuntimeOfficialTransport.Binding?>()
        private val executionSnapshot = AtomicReference<PythonPrivateExecutionSnapshot?>()
        private val pendingStdinSnapshot = AtomicReference(stdinSnapshot)
        private val reservation = AtomicReference<PythonRuntimeDispatchRegistry.Reservation?>()
        private val registry = PythonRuntimeDispatchRegistry<IPythonExecutionSession>()
        private val stdout = PythonUtf8OutputDecoder()
        private val stderr = PythonUtf8OutputDecoder()
        private val outputFlushed = AtomicBoolean(false)
        private val remoteStartDispatched = AtomicBoolean(false)
        private val outputLock = Any()
        private val inputTask = AtomicReference<FutureTask<*>?>()
        private val hostCapabilityBroker = AtomicReference<PythonHostCapabilityBroker?>()
        private val pluginStateLock = Any()

        private val pluginStateChangedListener: (String) -> Unit = { packageName ->
            if (packageName == PythonRuntimeOfficialTransport.OFFICIAL_PACKAGE) {
                synchronized(pluginStateLock) {
                    val enabled = runCatching { transport.isPluginEnabled() }.getOrDefault(false)
                    PythonRuntimePluginStateChangePolicy.failureFor(
                        changedPackageName = packageName,
                        officialPackageName = PythonRuntimeOfficialTransport.OFFICIAL_PACKAGE,
                        pluginEnabled = enabled,
                    )?.let(::failClosedForPluginStateChange)
                }
            }
        }

        fun run(): PythonRuntimeExecutionResult {
            PluginStateChangeRegistry.addListener(pluginStateChangedListener)
            return try {
                runWithPluginStateObserver()
            } catch (error: InterruptedException) {
                pluginStateFailure.get()?.let { throw it }
                throw error
            } finally {
                pendingStdinSnapshot.getAndSet(null)?.fill(0)
                closeInteractiveInput()
                closeLongRunningExecution()
                PluginStateChangeRegistry.removeListener(pluginStateChangedListener)
            }
        }

        private fun runWithPluginStateObserver(): PythonRuntimeExecutionResult {
            requireRuntimeChannelEnabled()
            checkExecutionActive()
            val installed = inspectInstalledProvider()
            checkProviderPreconditions(installed)
            checkExecutionActive()
            val requestId = PythonRequestId.fromUuid(UUID.randomUUID())
            val stdinSnapshot = pendingStdinSnapshot.getAndSet(null)
            val snapshot = try {
                PythonPrivateExecutionSnapshot.create(
                    applicationContext,
                    source,
                    requestId,
                    entryMode,
                    stdinSnapshot,
                    { cancelled.get() || pluginStateFailure.get() != null },
                ).also(executionSnapshot::set)
            } finally {
                stdinSnapshot?.fill(0)
            }
            // Released in the order of the former finally (docs/memory-phase2/ROADMAP.md T6): the broker, the callbacks the plugin
            // keeps (C42), the session with the binding that waits for its process to retire, then the live output and the
            // snapshot, which do not depend on each other and now follow the stage order of the other engines.
            // zh-CN: 按原 finally 的顺序释放 (docs/memory-phase2/ROADMAP.md T6): broker, 插件持有的回调 (C42), 会话及等待其进程退役的绑定,
            // 然后是实时输出与快照; 后两者互不依赖, 现按其他引擎的阶段顺序释放.
            val scope = BridgeExecutionScope("python", requestId.toString()).apply {
                own(BridgeExecutionScope.Stage.FILES, "snapshot") { executionSnapshot.getAndSet(null)?.close() }
                own(BridgeExecutionScope.Stage.CONSOLE, "output", ::finishLiveOutput)
                own(BridgeExecutionScope.Stage.CALLBACKS, "session", ::releaseSessionAndBinding)
                own(BridgeExecutionScope.Stage.BROKER, "broker", ::closeHostCapabilityBroker)
            }
            try {
                checkExecutionActive()
                val connection = transport.bind(
                    timeoutMillis = BIND_TIMEOUT_MILLIS,
                    cancelled = cancelled::get,
                    onConnectionLost = { error ->
                        finishFailure(
                            error as? PythonRuntimeHostException
                                ?: PythonRuntimeHostException(
                                    PythonRuntimeHostError.EXECUTION_LOST,
                                    "Python Runtime connection was lost",
                                    error,
                                ),
                        )
                    },
                    abortFailure = {
                        pluginStateFailure.get() ?: failure.get()
                    },
                    important = executionMode == PythonExecutionMode.LONG_RUNNING,
                ).also(binding::set)
                checkExecutionActive()
                val provider = connection.provider()
                val initialMetadata = callProviderRpc(
                    operation = "metadata inspection",
                    timeoutError = PythonRuntimeHostError.BIND_TIMEOUT,
                    failureError = PythonRuntimeHostError.BIND_FAILED,
                ) {
                    provider.runtimeInfo to provider.capabilities
                }
                val runtimeInfo = decodeRuntimeInfo(initialMetadata.first)
                val capabilities = decodeCapabilities(initialMetadata.second)
                requireExpectedRuntime(runtimeInfo, capabilities.supportsStructuredTraceback)

                val environment = currentEnvironment()
                val outputBytes = minOf(DEFAULT_OUTPUT_BYTES, capabilities.limits.maxOutputBytes)
                val outputChunkBytes = minOf(DEFAULT_OUTPUT_CHUNK_BYTES, capabilities.limits.maxOutputChunkBytes)
                val outputChunks = minOf(DEFAULT_OUTPUT_CHUNKS, capabilities.limits.maxOutputChunks, outputBytes)
                val timeoutMillis = PythonExecutionTimeoutPolicy.resolve(
                    executionMode,
                    configuredTimeoutMillis,
                    capabilities.limits.maxTimeoutMillis,
                )
                val inputPromptBytes = minOf(DEFAULT_INPUT_PROMPT_BYTES, capabilities.limits.maxInputPromptBytes)
                val inputReplyBytes = minOf(DEFAULT_INPUT_REPLY_BYTES, capabilities.limits.maxInputReplyBytes)
                val inputPrompts = minOf(DEFAULT_INPUT_PROMPTS, capabilities.limits.maxInputPrompts)
                val inputWaitMillis = minOf(
                    DEFAULT_INPUT_WAIT_MILLIS,
                    capabilities.limits.maxInputWaitMillis,
                    if (executionMode == PythonExecutionMode.BOUNDED) timeoutMillis else Long.MAX_VALUE,
                )
                val structuredJsonBytes = minOf(
                    DEFAULT_STRUCTURED_JSON_BYTES,
                    capabilities.limits.maxStructuredJsonBytes,
                )
                val outputArtifacts = minOf(
                    DEFAULT_OUTPUT_ARTIFACTS,
                    capabilities.limits.maxOutputArtifacts,
                )
                val outputArtifactPathBytes = minOf(
                    DEFAULT_OUTPUT_ARTIFACT_PATH_BYTES,
                    capabilities.limits.maxOutputArtifactPathBytes,
                )
                val outputArtifactBytes = minOf(
                    DEFAULT_OUTPUT_ARTIFACT_BYTES,
                    capabilities.limits.maxOutputArtifactBytes,
                )
                val totalOutputArtifactBytes = minOf(
                    DEFAULT_TOTAL_OUTPUT_ARTIFACT_BYTES,
                    capabilities.limits.maxTotalOutputArtifactBytes,
                )
                val requirement = PythonRuntimeCapabilityRequirement(
                    workspaceArchive = snapshot.hasWorkspace,
                    moduleEntry = snapshot.entryMode == PythonEntryMode.MODULE,
                    stdinSnapshot = snapshot.hasStdin,
                    hostCapabilitySnapshot = true,
                    interactiveInput = interactiveInputController != null,
                    structuredJsonResult = true,
                    outputArtifacts = true,
                    longRunningExecution = executionMode == PythonExecutionMode.LONG_RUNNING,
                    minimumSourceBytes = maxOf(1L, snapshot.sourceLengthBytes),
                    minimumWorkspaceArchiveBytes = snapshot.workspaceLengthBytes ?: 0L,
                    minimumWorkspaceEntries = snapshot.workspaceEntryCount ?: 0,
                    minimumWorkspaceUncompressedBytes = snapshot.workspaceUncompressedBytes ?: 0L,
                    minimumStdinBytes = snapshot.stdinLengthBytes ?: 0L,
                    minimumHostCapabilitySnapshotBytes = requireNotNull(snapshot.hostCapabilityLengthBytes),
                    minimumInputPromptBytes = if (interactiveInputController == null) 0 else maxOf(1, inputPromptBytes),
                    minimumInputReplyBytes = if (interactiveInputController == null) 0 else maxOf(1, inputReplyBytes),
                    minimumInputPrompts = if (interactiveInputController == null) 0 else maxOf(1, inputPrompts),
                    minimumInputWaitMillis = if (interactiveInputController == null) 0L else maxOf(1L, inputWaitMillis),
                    minimumStructuredJsonBytes = maxOf(1, structuredJsonBytes),
                    minimumOutputArtifacts = maxOf(1, outputArtifacts),
                    minimumOutputArtifactPathBytes = maxOf(1, outputArtifactPathBytes),
                    minimumOutputArtifactBytes = maxOf(1L, outputArtifactBytes),
                    minimumTotalOutputArtifactBytes = maxOf(1L, totalOutputArtifactBytes),
                    minimumOutputBytes = outputBytes,
                    minimumOutputChunkBytes = outputChunkBytes,
                    minimumOutputChunks = outputChunks,
                    minimumOutstandingOutputCredits = 1,
                    minimumTimeoutMillis = if (executionMode == PythonExecutionMode.BOUNDED) timeoutMillis else 0L,
                )
                val pinned = selectPinnedProvider(
                    installed.toCandidate(runtimeInfo, capabilities),
                    environment,
                    requirement,
                )
                val descriptorLayout = snapshot.descriptorLayout
                val interactiveInputPolicy = interactiveInputController?.let {
                    PythonInteractiveInputPolicy(
                        maxPrompts = inputPrompts,
                        maxPromptBytes = inputPromptBytes,
                        maxReplyBytes = inputReplyBytes,
                        replyTimeoutMillis = inputWaitMillis,
                    )
                }
                val resultPolicy = PythonResultPolicy(
                    maxStructuredJsonBytes = structuredJsonBytes,
                    maxArtifacts = outputArtifacts,
                    maxArtifactPathBytes = outputArtifactPathBytes,
                    maxArtifactBytes = outputArtifactBytes,
                    maxTotalArtifactBytes = totalOutputArtifactBytes,
                )
                val request = PythonExecutionRequest(
                    requestId = requestId,
                    protocolVersion = pinned.negotiatedProtocolVersion,
                    entryPoint = snapshot.entryPoint,
                    source = PythonPayloadReference(
                        kind = PythonPayloadKind.SOURCE,
                        descriptorIndex = descriptorLayout.sourceIndex,
                        declaredLengthBytes = snapshot.sourceLengthBytes,
                        sha256 = snapshot.sourceSha256,
                    ),
                    workspaceArchive = snapshot.workspaceLengthBytes?.let { length ->
                        PythonPayloadReference(
                            kind = PythonPayloadKind.WORKSPACE_ARCHIVE,
                            descriptorIndex = requireNotNull(descriptorLayout.workspaceIndex),
                            declaredLengthBytes = length,
                            sha256 = requireNotNull(snapshot.workspaceSha256),
                        )
                    },
                    stdin = snapshot.stdinLengthBytes?.let { length ->
                        PythonPayloadReference(
                            kind = PythonPayloadKind.STDIN,
                            descriptorIndex = requireNotNull(descriptorLayout.stdinIndex),
                            declaredLengthBytes = length,
                            sha256 = requireNotNull(snapshot.stdinSha256),
                        )
                    },
                    hostCapabilitySnapshot = PythonPayloadReference(
                        kind = PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT,
                        descriptorIndex = requireNotNull(descriptorLayout.hostCapabilityIndex),
                        declaredLengthBytes = requireNotNull(snapshot.hostCapabilityLengthBytes),
                        sha256 = requireNotNull(snapshot.hostCapabilitySha256),
                    ),
                    timeoutMillis = timeoutMillis,
                    maxOutputBytes = outputBytes,
                    maxOutputChunkBytes = outputChunkBytes,
                    maxOutputChunks = outputChunks,
                    entryMode = snapshot.entryMode,
                    interactiveInput = interactiveInputPolicy,
                    resultPolicy = resultPolicy,
                    executionMode = executionMode,
                )
                val refreshedMetadata = callProviderRpc(
                    operation = "pre-dispatch metadata revalidation",
                    timeoutError = PythonRuntimeHostError.BIND_TIMEOUT,
                    failureError = PythonRuntimeHostError.BIND_FAILED,
                ) {
                    provider.runtimeInfo to provider.capabilities
                }
                val current = transport.inspect()?.let { refreshed ->
                    checkProviderPreconditions(refreshed)
                    refreshed.toCandidate(
                        decodeRuntimeInfo(refreshedMetadata.first),
                        decodeCapabilities(refreshedMetadata.second),
                    )
                }
                val plan = when (
                    val planning = PythonRuntimeHostRequestPlanner.plan(
                        pinned = pinned,
                        current = current,
                        environment = environment,
                        requirement = requirement,
                        request = request,
                        descriptorCount = snapshot.descriptorCount,
                    )
                ) {
                    PythonRuntimeHostRequestPlanningResult.ProviderIdentityChanged -> throw PythonRuntimeHostException(
                        PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED,
                        "Python Runtime identity changed before dispatch",
                    )
                    is PythonRuntimeHostRequestPlanningResult.RequestRejected -> throw PythonRuntimeHostException(
                        PythonRuntimeHostError.PROVIDER_INCOMPATIBLE,
                        "Python Runtime rejected the request contract: ${planning.violation}",
                    )
                    is PythonRuntimeHostRequestPlanningResult.Planned -> planning.plan
                }
                checkExecutionActive()
                val reserved = registry.reserve(plan)
                    ?: throw PythonRuntimeHostException(
                        PythonRuntimeHostError.CONTRACT_VIOLATION,
                        "Python Runtime request ID was already reserved",
                    )
                reservation.set(reserved)
                checkExecutionActive()
                openSession(provider, pinned, reserved, snapshot, scope)
                awaitTerminal(request)
                checkExecutionActive()
                failure.get()?.let { throw it }
                return result.get()
                    ?: throw PythonRuntimeHostException(
                        PythonRuntimeHostError.EXECUTION_LOST,
                        "Python Runtime ended without a result",
                    )
            } catch (error: InterruptedException) {
                pluginStateFailure.get()?.let { throw it }
                cancel()
                throw error
            } catch (error: Throwable) {
                pluginStateFailure.get()?.let { throw it }
                if (cancelled.get()) {
                    throw InterruptedException("Python execution was cancelled").apply {
                        initCause(error)
                    }
                }
                throw error
            } finally {
                scope.close()
            }
        }

        private fun releaseSessionAndBinding() {
            val ownedBinding = binding.get()
            val ownedSession = reservation.getAndSet(null)?.let(registry::release)
            ownedSession?.let { session -> runCatching { session.close() } }
            if (ownedSession != null && remoteStartDispatched.get()) {
                // Session close retires this CPython generation. Keep the binding alive until
                // Binder death so the next FIFO owner cannot race a RETIRING provider.
                if (ownedBinding?.awaitRemoteProcessExit(PROCESS_RETIREMENT_WAIT_MILLIS) == false) {
                    globalConsole.println(
                        Log.WARN,
                        "Python Runtime process retirement confirmation timed out; " +
                            "provider admission remains fail-closed",
                    )
                }
            }
            binding.compareAndSet(ownedBinding, null)
            ownedBinding?.close()
        }

        fun cancel() {
            cancelled.set(true)
            closeHostCapabilityBroker()
            closeInteractiveInput()
            closeLongRunningExecution()
            pendingStdinSnapshot.getAndSet(null)?.fill(0)
            cancelRemoteSession()
            executionSnapshot.getAndSet(null)?.close()
            terminalClaimed.compareAndSet(false, true)
            terminal.countDown()
        }

        private fun failClosedForPluginStateChange(error: PythonRuntimeHostException) {
            if (!terminalClaimed.compareAndSet(false, true)) return
            pluginStateFailure.set(error)
            failure.set(error)
            closeHostCapabilityBroker()
            closeInteractiveInput()
            retireRemoteSession()
            binding.getAndSet(null)?.close()
            executionSnapshot.getAndSet(null)?.close()
            terminal.countDown()
        }

        private fun retireRemoteSession() {
            val reserved = reservation.getAndSet(null) ?: return
            when (val cancellation = registry.cancel(reserved.requestId)) {
                is PythonRuntimeDispatchRegistry.Cancellation.BeforeStart ->
                    runCatching { cancellation.session.cancel() }
                is PythonRuntimeDispatchRegistry.Cancellation.AfterStart ->
                    runCatching { cancellation.session.cancel() }
                PythonRuntimeDispatchRegistry.Cancellation.BeforeOpened,
                PythonRuntimeDispatchRegistry.Cancellation.Unknown,
                -> Unit
            }
            registry.release(reserved)?.let { session -> runCatching { session.close() } }
        }

        private fun cancelRemoteSession() {
            reservation.get()?.let { reserved ->
                when (val cancellation = registry.cancel(reserved.requestId)) {
                    is PythonRuntimeDispatchRegistry.Cancellation.BeforeStart ->
                        runCatching { cancellation.session.cancel() }
                    is PythonRuntimeDispatchRegistry.Cancellation.AfterStart ->
                        runCatching { cancellation.session.cancel() }
                    PythonRuntimeDispatchRegistry.Cancellation.BeforeOpened,
                    PythonRuntimeDispatchRegistry.Cancellation.Unknown,
                    -> Unit
                }
            }
        }

        private fun openSession(
            provider: org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider,
            pinned: PythonRuntimeProviderPinnedIdentity,
            reserved: PythonRuntimeDispatchRegistry.Reservation,
            snapshot: PythonPrivateExecutionSnapshot,
            scope: BridgeExecutionScope,
        ) {
            val sessionRef = AtomicReference<IPythonExecutionSession?>()
            val broker = if (pinned.negotiatedProtocolVersion >= HOST_CAPABILITY_BROKER_PROTOCOL) {
                check(pinned.capabilities.supportsHostCapabilityBroker) {
                    "Python Runtime protocol 1.5 was negotiated without broker support"
                }
                PythonHostCapabilityBroker(
                    context = applicationContext,
                    executionId = reserved.requestId.toString(),
                    expectedPluginUid = pinned.packageUid,
                    fileRoot = PythonHostFilePolicy.rootFor(source),
                    source = source,
                    engineId = engineId,
                    engineStartedAtMillis = engineStartedAtMillis,
                    stopSelf = stopSelf,
                    globalConsole = globalConsole,
                    interactiveInputController = interactiveInputController,
                    onViolation = { error ->
                        finishFailure(
                            PythonRuntimeHostException(
                                PythonRuntimeHostError.CONTRACT_VIOLATION,
                                "Python Runtime host capability broker received an invalid call",
                                error,
                            ),
                        )
                        cancelRemoteSession()
                    },
                ).also { created ->
                    check(hostCapabilityBroker.compareAndSet(null, created)) {
                        "Python Runtime host capability broker was already installed"
                    }
                    scope.track(created, "broker")
                }
            } else {
                null
            }

            fun fromPinnedProvider(action: () -> Unit) {
                if (!PythonRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, Binder.getCallingUid())) {
                    finishFailure(
                        PythonRuntimeHostException(
                            PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED,
                            "Python Runtime callback UID differs from the pinned provider UID",
                        ),
                    )
                    cancelRemoteSession()
                    return
                }
                runCatching(action).onFailure { error ->
                    finishFailure(
                        when (error) {
                            is PythonRuntimeHostException -> error
                            is RemoteException -> PythonRuntimeHostException(
                                PythonRuntimeHostError.EXECUTION_LOST,
                                "Python Runtime was lost while dispatching a callback response",
                                error,
                            )
                            else -> PythonRuntimeHostException(
                                PythonRuntimeHostError.CONTRACT_VIOLATION,
                                "Python Runtime emitted an invalid callback",
                                error,
                            )
                        },
                    )
                    cancelRemoteSession()
                }
            }

            val executionHandler = object : IPythonExecutionCallback.Default() {
                override fun onStarted(metadata: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Python Runtime started metadata is null" },
                    )
                    reserved.plan.admissionPlan.sessionPolicy?.onStarted(decoded)
                        ?: error("Python Runtime started before admission")
                    longRunningController?.onStarted()
                }

                override fun onStdout(chunk: ByteArray?) = acceptOutput(
                    chunk = chunk,
                    callbackStream = PythonOutputStream.STDOUT,
                    session = sessionRef,
                    reserved = reserved,
                    fromPinnedProvider = ::fromPinnedProvider,
                )

                override fun onStderr(chunk: ByteArray?) = acceptOutput(
                    chunk = chunk,
                    callbackStream = PythonOutputStream.STDERR,
                    session = sessionRef,
                    reserved = reserved,
                    fromPinnedProvider = ::fromPinnedProvider,
                )

                override fun onResult(
                    encodedResult: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    val received = descriptors?.filterNotNull().orEmpty()
                    try {
                        fromPinnedProvider {
                            val descriptorArray = requireNotNull(descriptors) {
                                "Python Runtime result descriptor array is null"
                            }
                            require(received.size == descriptorArray.size) {
                                "Python Runtime result descriptor array contains null"
                            }
                            val decoded = PythonRuntimeCodec.decodeExecutionResult(
                                requireNotNull(encodedResult) { "Python Runtime result is null" },
                            )
                            val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                                "Python Runtime result arrived before admission"
                            }
                            policy.onResult(decoded, descriptorArray.size)
                            result.set(
                                PythonRuntimeResultReceiver.receive(
                                    decoded,
                                    received.toTypedArray(),
                                ),
                            )
                            finishTerminal()
                        }
                    } finally {
                        received.forEach { descriptor -> runCatching { descriptor.close() } }
                    }
                }

                override fun onFailed(error: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Python Runtime failure is null" },
                    )
                    val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                        "Python Runtime failure arrived before admission"
                    }
                    policy.onFailed(decoded)
                    reportPythonFailure(source, decoded)
                    finishFailure(decoded.toHostException())
                }

                override fun onCancelled(cancellation: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Python Runtime cancellation is null" },
                    )
                    val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                        "Python Runtime cancellation arrived before admission"
                    }
                    policy.onCancelled(decoded)
                    // Only the local cancel() path marks user cancellation. Provider
                    // cancellation is a protocol terminal and must not mask timeout/lost errors.
                    finishTerminal()
                }

                override fun onInputPrompt(prompt: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeInputPrompt(
                        requireNotNull(prompt) { "Python Runtime input prompt is null" },
                    )
                    val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                        "Python Runtime input prompt arrived before admission"
                    }
                    policy.onInputPrompt(decoded)
                    dispatchInteractiveInput(decoded, sessionRef, reserved)
                }

                override fun onHeartbeat(heartbeat: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionHeartbeat(
                        requireNotNull(heartbeat) { "Python Runtime heartbeat is null" },
                    )
                    val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                        "Python Runtime heartbeat arrived before admission"
                    }
                    policy.onHeartbeat(decoded)
                    requireNotNull(longRunningController) {
                        "Python Runtime emitted a heartbeat for a bounded execution"
                    }.onHeartbeat(decoded.elapsedMillis)
                }
            }
            val admissionHandler = object : IPythonSessionOpenCallback.Default() {
                override fun onOpened(session: IPythonExecutionSession?) {
                    if (!PythonRuntimeProviderCallbackCallerPolicy.isExpectedUid(pinned, Binder.getCallingUid())) {
                        session?.let { untrusted -> runCatching { untrusted.close() } }
                        finishFailure(
                            PythonRuntimeHostException(
                                PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED,
                                "Python Runtime admission callback UID differs from the pinned provider UID",
                            ),
                        )
                        cancelRemoteSession()
                        return
                    }
                    fromPinnedProvider {
                        val safeSession = requireNotNull(session) { "Python Runtime opened a null session" }
                        when (val opened = registry.acceptOpened(reserved, safeSession)) {
                            is PythonRuntimeDispatchRegistry.Opened.CloseLate ->
                                opened.sessions.forEach { late -> runCatching { late.close() } }
                            is PythonRuntimeDispatchRegistry.Opened.Owned -> {
                                sessionRef.set(opened.session)
                                if (cancelled.get()) {
                                    cancel()
                                    return@fromPinnedProvider
                                }
                                synchronized(pluginStateLock) {
                                    if (pluginStateFailure.get() != null) {
                                        runCatching { opened.session.close() }
                                    } else {
                                        when (val start = registry.beginStart(reserved)) {
                                            PythonRuntimeDispatchRegistry.Start.Rejected -> {
                                                runCatching { opened.session.close() }
                                                finishFailure(
                                                    PythonRuntimeHostException(
                                                        PythonRuntimeHostError.CONTRACT_VIOLATION,
                                                        "Python Runtime start ownership was rejected",
                                                    ),
                                                )
                                            }
                                            is PythonRuntimeDispatchRegistry.Start.Dispatch -> {
                                                val credits = reserved.plan.admissionPlan.maxOutstandingOutputCredits
                                                start.policy.grantOutputCredits(credits)
                                                start.session.grantOutputCredits(credits)
                                                // The registry forbids replay before this one-way dispatch.
                                                remoteStartDispatched.set(true)
                                                start.session.start()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onRejected(error: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Python Runtime admission rejection is null" },
                    )
                    reserved.plan.admissionPlan.onRejected(decoded)
                    finishFailure(decoded.toHostException())
                }
            }
            // The plugin keeps these Binders until it collects its proxies, also when no session starts and its process does not
            // retire; once the execution ends they let go of it (docs/memory-phase2/ROADMAP.md C42).
            // zh-CN: 插件在回收代理之前一直持有这些 Binder, 没有会话启动, 其进程也不退役时同样如此; 执行结束后它们放开本执行
            // (docs/memory-phase2/ROADMAP.md C42).
            val executionCallback = ExportedExecutionCallback(executionHandler)
            val admissionCallback = ExportedSessionOpenCallback(admissionHandler)
            scope.own(BridgeExecutionScope.Stage.CALLBACKS, "callbacks") {
                executionCallback.clear()
                admissionCallback.clear()
            }
            scope.track(executionHandler, "callback")
            scope.track(admissionHandler, "admission")

            try {
                val descriptors = snapshot.openReadOnlyDescriptors()
                try {
                    try {
                        callProviderRpc(
                            operation = "session admission",
                            timeoutError = PythonRuntimeHostError.SESSION_REJECTED,
                            failureError = PythonRuntimeHostError.SESSION_REJECTED,
                        ) {
                            val encodedRequest = PythonRuntimeCodec.encodeExecutionRequest(reserved.plan.request)
                            if (broker == null) {
                                provider.openSession(
                                    encodedRequest,
                                    descriptors,
                                    admissionCallback,
                                    executionCallback,
                                )
                            } else {
                                provider.openSessionWithHostCapabilities(
                                    encodedRequest,
                                    descriptors,
                                    broker.binder,
                                    admissionCallback,
                                    executionCallback,
                                )
                            }
                        }
                    } catch (error: PythonRuntimeHostException) {
                        if (error.error == PythonRuntimeHostError.SESSION_REJECTED) {
                            when (val cancellation = registry.cancel(reserved.requestId)) {
                                is PythonRuntimeDispatchRegistry.Cancellation.AfterStart -> {
                                    runCatching { cancellation.session.cancel() }
                                    throw PythonRuntimeHostException(
                                        PythonRuntimeHostError.EXECUTION_LOST,
                                        "Python Runtime session admission failed after dispatch; execution state is unknown",
                                        error,
                                    )
                                }
                                is PythonRuntimeDispatchRegistry.Cancellation.BeforeStart -> {
                                    runCatching { cancellation.session.cancel() }
                                    runCatching { cancellation.session.close() }
                                }
                                PythonRuntimeDispatchRegistry.Cancellation.BeforeOpened -> Unit
                                PythonRuntimeDispatchRegistry.Cancellation.Unknown -> {
                                    if (!cancelled.get()) {
                                        throw PythonRuntimeHostException(
                                            PythonRuntimeHostError.EXECUTION_LOST,
                                            "Python Runtime session admission ownership was lost",
                                            error,
                                        )
                                    }
                                }
                            }
                        }
                        throw error
                    }
                } finally {
                    descriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            } catch (error: PythonRuntimeHostException) {
                throw error
            } catch (error: RuntimeException) {
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.SESSION_REJECTED,
                    "Python Runtime session could not be opened",
                    error,
                )
            }
        }

        private fun acceptOutput(
            chunk: ByteArray?,
            callbackStream: PythonOutputStream,
            session: AtomicReference<IPythonExecutionSession?>,
            reserved: PythonRuntimeDispatchRegistry.Reservation,
            fromPinnedProvider: (() -> Unit) -> Unit,
        ) = fromPinnedProvider {
            val decoded = PythonRuntimeCodec.decodeOutputChunk(
                requireNotNull(chunk) { "Python Runtime output chunk is null" },
            )
            val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                "Python Runtime output arrived before admission"
            }
            synchronized(outputLock) {
                policy.onOutput(decoded, callbackStream)
                val output = if (callbackStream == PythonOutputStream.STDOUT) stdout else stderr
                val logLevel = if (callbackStream == PythonOutputStream.STDOUT) Log.INFO else Log.ERROR
                writeLiveOutput(output, decoded.toByteArray(), logLevel)
            }
            if (!cancelled.get()) {
                policy.grantOutputCredits(1)
                requireNotNull(session.get()) { "Python Runtime output arrived without a session" }
                    .grantOutputCredits(1)
            }
        }

        private fun awaitTerminal(request: PythonExecutionRequest) {
            if (request.executionMode == PythonExecutionMode.LONG_RUNNING) {
                awaitLongRunningTerminal()
            } else {
                awaitBoundedTerminal(request.timeoutMillis)
            }
        }

        private fun awaitBoundedTerminal(timeoutMillis: Long) {
            val waitMillis = Math.addExact(timeoutMillis, TERMINAL_GRACE_MILLIS)
            try {
                if (!terminal.await(waitMillis, TimeUnit.MILLISECONDS)) {
                    if (abortForHostTimeout()) {
                        throw PythonRuntimeHostException(
                            PythonRuntimeHostError.EXECUTION_TIMEOUT,
                            "Python Runtime did not produce a terminal callback",
                        )
                    }
                    // A callback won the terminal CAS at the deadline edge; let that winner
                    // publish its result/failure before run() reads the terminal state.
                    terminal.await(PROVIDER_RPC_POLL_MILLIS, TimeUnit.MILLISECONDS)
                }
            } catch (error: InterruptedException) {
                cancel()
                throw error
            }
        }

        private fun awaitLongRunningTerminal() {
            val controller = requireNotNull(longRunningController) {
                "Long-running Python request has no foreground controller"
            }
            try {
                while (!terminal.await(LONG_RUNNING_WAIT_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    checkExecutionActive()
                    failure.get()?.let { throw it }
                    controller.leaseFailure()?.let { leaseFailure ->
                        if (abortForLongRunningLease(leaseFailure)) throw leaseFailure
                        terminal.await(PROVIDER_RPC_POLL_MILLIS, TimeUnit.MILLISECONDS)
                        return
                    }
                }
            } catch (error: InterruptedException) {
                cancel()
                throw error
            }
        }

        private fun abortForLongRunningLease(error: PythonRuntimeHostException): Boolean {
            if (!terminalClaimed.compareAndSet(false, true)) return false
            failure.set(error)
            closeHostCapabilityBroker()
            closeInteractiveInput()
            cancelRemoteSession()
            executionSnapshot.getAndSet(null)?.close()
            terminal.countDown()
            return true
        }

        private fun abortForHostTimeout(): Boolean {
            if (!terminalClaimed.compareAndSet(false, true)) return false
            closeHostCapabilityBroker()
            closeInteractiveInput()
            cancelRemoteSession()
            executionSnapshot.getAndSet(null)?.close()
            terminal.countDown()
            return true
        }

        private fun <T> callProviderRpc(
            operation: String,
            timeoutError: PythonRuntimeHostError,
            failureError: PythonRuntimeHostError,
            block: () -> T,
        ): T {
            val future = try {
                PROVIDER_RPC_EXECUTOR.submit<T> { block() }
            } catch (error: RejectedExecutionException) {
                throw PythonRuntimeHostException(
                    failureError,
                    "Python Runtime $operation could not enter the bounded RPC lane",
                    error,
                )
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROVIDER_RPC_TIMEOUT_MILLIS)
            try {
                while (true) {
                    checkExecutionActive()
                    failure.get()?.let { throw it }
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0L) {
                        throw PythonRuntimeHostException(
                            timeoutError,
                            "Python Runtime $operation timed out",
                        )
                    }
                    try {
                        return future.get(
                            minOf(remaining, TimeUnit.MILLISECONDS.toNanos(PROVIDER_RPC_POLL_MILLIS)),
                            TimeUnit.NANOSECONDS,
                        )
                    } catch (_: TimeoutException) {
                        // Poll cancellation/death without blocking the ScriptThread in Binder.
                    }
                }
            } catch (error: ExecutionException) {
                val cause = error.cause ?: error
                throw when (cause) {
                    is PythonRuntimeHostException -> cause
                    is SecurityException -> PythonRuntimeHostException(
                        PythonRuntimeHostError.PROVIDER_UNTRUSTED,
                        "Python Runtime denied $operation",
                        cause,
                    )
                    is RemoteException -> PythonRuntimeHostException(
                        failureError,
                        "Python Runtime was lost during $operation",
                        cause,
                    )
                    else -> PythonRuntimeHostException(
                        failureError,
                        "Python Runtime failed during $operation",
                        cause,
                    )
                }
            } catch (error: CancellationException) {
                checkExecutionActive()
                throw PythonRuntimeHostException(
                    failureError,
                    "Python Runtime $operation was cancelled by the transport",
                    error,
                )
            } finally {
                if (!future.isDone) future.cancel(true)
                PROVIDER_RPC_EXECUTOR.purge()
            }
        }

        private fun finishTerminal() {
            if (terminalClaimed.compareAndSet(false, true)) {
                closeHostCapabilityBroker()
                closeInteractiveInput()
                terminal.countDown()
            }
        }

        private fun dispatchInteractiveInput(
            prompt: PythonInputPrompt,
            session: AtomicReference<IPythonExecutionSession?>,
            reserved: PythonRuntimeDispatchRegistry.Reservation,
        ) {
            val controller = requireNotNull(interactiveInputController) {
                "Python Runtime emitted input without a foreground controller"
            }
            inputTask.get()?.takeIf { it.isDone }?.let { completed ->
                inputTask.compareAndSet(completed, null)
            }
            val taskHolder = AtomicReference<FutureTask<*>?>()
            val task = FutureTask<Unit> {
                try {
                    val response = controller.request(prompt)
                    val reply = PythonInputReply(
                        requestId = prompt.requestId,
                        promptId = prompt.promptId,
                        status = response.status,
                        value = response.value,
                    )
                    val policy = requireNotNull(reserved.plan.admissionPlan.sessionPolicy) {
                        "Python Runtime input reply lost its session policy"
                    }
                    if (policy.replyInput(reply)) {
                        // Release the Host task slot before the remote reply wakes Python. A second
                        // input() may synchronously emit its prompt before this FutureTask reaches
                        // its completed state, but it cannot do so before replyInput returns there.
                        inputTask.compareAndSet(taskHolder.get(), null)
                        requireNotNull(session.get()) { "Python Runtime input prompt has no open session" }
                            .replyInput(PythonRuntimeCodec.encodeInputReply(reply))
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Throwable) {
                    if (!terminalClaimed.get()) {
                        finishFailure(
                            PythonRuntimeHostException(
                                PythonRuntimeHostError.INTERACTIVE_INPUT_FAILED,
                                "Python interactive input could not produce a bounded reply",
                                error,
                            ),
                        )
                        cancelRemoteSession()
                    }
                } finally {
                    inputTask.compareAndSet(taskHolder.get(), null)
                }
            }
            taskHolder.set(task)
            check(inputTask.compareAndSet(null, task)) { "A Python input task is already active" }
            try {
                INPUT_EXECUTOR.execute(task)
            } catch (error: RejectedExecutionException) {
                inputTask.compareAndSet(task, null)
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.INTERACTIVE_INPUT_FAILED,
                    "Python interactive input could not enter the bounded UI lane",
                    error,
                )
            }
        }

        private fun finishFailure(error: Throwable) {
            if (terminalClaimed.compareAndSet(false, true)) {
                failure.set(error)
                closeHostCapabilityBroker()
                closeInteractiveInput()
                terminal.countDown()
            }
        }

        private fun closeInteractiveInput() {
            inputTask.getAndSet(null)?.cancel(true)
            interactiveInputController?.cancel()
        }

        private fun closeLongRunningExecution() {
            longRunningController?.close()
        }

        private fun closeHostCapabilityBroker() {
            hostCapabilityBroker.getAndSet(null)?.close()
        }

        private fun finishLiveOutput() {
            if (!outputFlushed.compareAndSet(false, true)) return
            synchronized(outputLock) {
                writeLiveOutput(stdout.finish(), Log.INFO)
                writeLiveOutput(stderr.finish(), Log.ERROR)
            }
        }

        private fun writeLiveOutput(output: PythonUtf8OutputDecoder, bytes: ByteArray, logLevel: Int) =
            writeLiveOutput(output.decode(bytes), logLevel)

        private fun writeLiveOutput(text: String, logLevel: Int) {
            if (text.isNotEmpty()) globalConsole.write(logLevel, text)
        }

        private fun checkExecutionActive() {
            pluginStateFailure.get()?.let { throw it }
            if (cancelled.get()) throw InterruptedException("Python execution was cancelled")
        }
    }

    private fun requireRuntimeChannelEnabled() {
        if (!PythonRuntimeChannelPolicy.isEnabledForCurrentBuild) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PREVIEW_DISABLED,
                "Python Runtime is not supported by this host flavor",
            )
        }
    }

    private fun inspectInstalledProvider(): PythonRuntimeOfficialTransport.InstalledProvider =
        try {
            transport.inspect()
                ?: throw PythonRuntimeHostException(
                    PythonRuntimeHostError.PLUGIN_MISSING,
                    "Official Python Runtime plugin is not installed",
                )
        } catch (error: PythonRuntimeHostException) {
            throw error
        } catch (error: RuntimeException) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_INCOMPATIBLE,
                "Official Python Runtime identity could not be inspected",
                error,
            )
        }

    private fun checkProviderPreconditions(installed: PythonRuntimeOfficialTransport.InstalledProvider) {
        if (!installed.pluginEnabled || !installed.applicationEnabled || !installed.serviceEnabled) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PLUGIN_DISABLED,
                "Official Python Runtime plugin is disabled",
            )
        }
        if (!installed.sameSignerAsHost) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_UNTRUSTED,
                "Official Python Runtime signer differs from the host",
            )
        }
    }

    private fun selectPinnedProvider(
        candidate: PythonRuntimeProviderCandidate,
        environment: PythonRuntimeProviderEnvironment,
        requirement: PythonRuntimeCapabilityRequirement,
    ): PythonRuntimeProviderPinnedIdentity = when (
        val selection = PythonRuntimeProviderSelectionPolicy.select(
            candidates = listOf(candidate),
            environment = environment,
            requirement = requirement,
            options = PythonRuntimeProviderSelectionOptions(
                enabled = true,
                preferredComponent = PythonRuntimeOfficialTransport.OFFICIAL_PROVIDER,
            ),
        )
    ) {
        PythonRuntimeProviderSelectionResult.Disabled -> throw PythonRuntimeHostException(
            PythonRuntimeHostError.PREVIEW_DISABLED,
            "Python Runtime provider selection is disabled",
        )
        is PythonRuntimeProviderSelectionResult.NoMatch -> throw PythonRuntimeHostException(
            PythonRuntimeHostError.PROVIDER_INCOMPATIBLE,
            "Official Python Runtime is incompatible: ${selection.evaluations.joinToString { it.rejection.name }}",
        )
        is PythonRuntimeProviderSelectionResult.Ambiguous -> throw PythonRuntimeHostException(
            PythonRuntimeHostError.CONTRACT_VIOLATION,
            "Official Python Runtime selection was ambiguous",
        )
        is PythonRuntimeProviderSelectionResult.Selected -> selection.pinned
    }

    private fun currentEnvironment(): PythonRuntimeProviderEnvironment {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: throw PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_INCOMPATIBLE,
                "Android did not report a primary ABI",
            )
        return PythonRuntimeProviderEnvironment(
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            currentAbi = abi,
            releaseBuild = !BuildConfig.DEBUG,
            allowUserApprovedProviders = false,
        )
    }

    private fun decodeRuntimeInfo(bytes: ByteArray?) = try {
        PythonRuntimeCodec.decodeRuntimeInfo(
            requireNotNull(bytes) { "Python Runtime metadata is null" },
        )
    } catch (error: RuntimeException) {
        throw PythonRuntimeHostException(
            PythonRuntimeHostError.CONTRACT_VIOLATION,
            "Python Runtime metadata is invalid",
            error,
        )
    }

    private fun decodeCapabilities(bytes: ByteArray?) = try {
        PythonRuntimeCodec.decodeCapabilities(
            requireNotNull(bytes) { "Python Runtime capabilities are null" },
        )
    } catch (error: RuntimeException) {
        throw PythonRuntimeHostException(
            PythonRuntimeHostError.CONTRACT_VIOLATION,
            "Python Runtime capabilities are invalid",
            error,
        )
    }

    private fun requireExpectedRuntime(
        runtimeInfo: org.autojs.plugin.python.runtime.api.PythonRuntimeInfo,
        structuredTraceback: Boolean,
    ) {
        if (
            runtimeInfo.providerId != EXPECTED_PROVIDER_ID ||
            runtimeInfo.implementation != PythonImplementation.CPYTHON ||
            runtimeInfo.isolationMode != PythonIsolationMode.DEDICATED_PLUGIN_PROCESS ||
            !structuredTraceback
        ) {
            throw PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_INCOMPATIBLE,
                "Official Python Runtime metadata does not match the preview contract",
            )
        }
    }

    private fun reportPythonFailure(source: PythonFileSource, error: PythonExecutionError) {
        globalConsole.write(Log.ERROR, PythonRuntimeFailurePresentationPolicy.messageFor(source, error))
    }

    /**
     * Forwards to the execution callback until cleared; afterwards only closes the descriptors of a late result.
     * zh-CN: 清空之前转发到执行回调; 之后只关闭迟到结果所附的描述符.
     */
    private class ExportedExecutionCallback(callback: IPythonExecutionCallback) : IPythonExecutionCallback.Stub() {

        private val target = AtomicReference<IPythonExecutionCallback?>(callback)

        fun clear() {
            target.set(null)
        }

        override fun onStarted(metadata: ByteArray?) {
            target.get()?.onStarted(metadata)
        }

        override fun onStdout(chunk: ByteArray?) {
            target.get()?.onStdout(chunk)
        }

        override fun onStderr(chunk: ByteArray?) {
            target.get()?.onStderr(chunk)
        }

        override fun onResult(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
            target.get()?.onResult(result, descriptors)
                ?: descriptors?.forEach { descriptor -> runCatching { descriptor?.close() } }
        }

        override fun onFailed(error: ByteArray?) {
            target.get()?.onFailed(error)
        }

        override fun onCancelled(cancellation: ByteArray?) {
            target.get()?.onCancelled(cancellation)
        }

        override fun onInputPrompt(prompt: ByteArray?) {
            target.get()?.onInputPrompt(prompt)
        }

        override fun onHeartbeat(heartbeat: ByteArray?) {
            target.get()?.onHeartbeat(heartbeat)
        }
    }

    /**
     * Forwards to the admission callback until cleared; afterwards closes a late session, as a late admission is closed while the
     * execution runs.
     * zh-CN: 清空之前转发到准入回调; 之后关闭迟到的会话, 与执行期间关闭迟到的准入一致.
     */
    private class ExportedSessionOpenCallback(callback: IPythonSessionOpenCallback) : IPythonSessionOpenCallback.Stub() {

        private val target = AtomicReference<IPythonSessionOpenCallback?>(callback)

        fun clear() {
            target.set(null)
        }

        override fun onOpened(session: IPythonExecutionSession?) {
            target.get()?.onOpened(session) ?: session?.let { late -> runCatching { late.close() } }
        }

        override fun onRejected(error: ByteArray?) {
            target.get()?.onRejected(error)
        }
    }

    companion object {
        private const val EXPECTED_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        private const val BIND_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_RPC_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_RPC_POLL_MILLIS = 100L
        private const val TERMINAL_GRACE_MILLIS = 15_000L
        private const val LONG_RUNNING_WAIT_POLL_MILLIS = 1_000L
        private const val PROCESS_RETIREMENT_WAIT_MILLIS = 3_000L
        private const val DEFAULT_OUTPUT_BYTES = 16L * 1024L * 1024L
        private const val DEFAULT_OUTPUT_CHUNK_BYTES = 32 * 1024
        private const val DEFAULT_OUTPUT_CHUNKS = 16_384L
        private const val DEFAULT_INPUT_PROMPT_BYTES = 4 * 1024
        private const val DEFAULT_INPUT_REPLY_BYTES = 64 * 1024
        private const val DEFAULT_INPUT_PROMPTS = 128
        private const val DEFAULT_INPUT_WAIT_MILLIS = 2L * 60L * 1_000L
        private const val DEFAULT_STRUCTURED_JSON_BYTES = 64 * 1024
        private const val DEFAULT_OUTPUT_ARTIFACTS = 8
        private const val DEFAULT_OUTPUT_ARTIFACT_PATH_BYTES = 512
        private const val DEFAULT_OUTPUT_ARTIFACT_BYTES = 2L * 1024L * 1024L
        private const val DEFAULT_TOTAL_OUTPUT_ARTIFACT_BYTES = 4L * 1024L * 1024L
        private val HOST_CAPABILITY_BROKER_PROTOCOL = PythonProtocolVersion(1, 5)

        private val PROVIDER_RPC_EXECUTOR = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable ->
                Thread(runnable, "python-runtime-provider-rpc").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )

        private val INPUT_EXECUTOR = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable ->
                Thread(runnable, "python-runtime-interactive-input").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}

/** Pure mapping for process-local enablement or signer-authorization invalidation events. */
internal object PythonRuntimePluginStateChangePolicy {

    fun failureFor(
        changedPackageName: String,
        officialPackageName: String,
        pluginEnabled: Boolean,
    ): PythonRuntimeHostException? {
        if (changedPackageName != officialPackageName) return null
        return if (pluginEnabled) {
            PythonRuntimeHostException(
                PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED,
                "Official Python Runtime authorization changed during execution",
            )
        } else {
            PythonRuntimeHostException(
                PythonRuntimeHostError.PLUGIN_DISABLED,
                "Official Python Runtime was disabled during execution",
            )
        }
    }
}

private fun PythonExecutionError.toHostException(): PythonRuntimeHostException {
    val hostError = when (code) {
        PythonErrorCode.BUSY -> PythonRuntimeHostError.BUSY
        PythonErrorCode.SOURCE_REJECTED -> PythonRuntimeHostError.SOURCE_INVALID
        PythonErrorCode.WORKSPACE_REJECTED -> PythonRuntimeHostError.WORKSPACE_INVALID
        PythonErrorCode.STDIN_REJECTED -> PythonRuntimeHostError.STDIN_INVALID
        PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED ->
            PythonRuntimeHostError.HOST_CAPABILITY_SNAPSHOT_INVALID
        PythonErrorCode.SESSION_OPEN_FAILED,
        PythonErrorCode.INVALID_REQUEST,
        PythonErrorCode.UNSUPPORTED_PROTOCOL,
        PythonErrorCode.UNSUPPORTED_CAPABILITY,
        -> PythonRuntimeHostError.SESSION_REJECTED
        PythonErrorCode.RUNTIME_CRASHED -> PythonRuntimeHostError.EXECUTION_LOST
        PythonErrorCode.TIMEOUT -> PythonRuntimeHostError.EXECUTION_TIMEOUT
        PythonErrorCode.INPUT_TIMEOUT,
        PythonErrorCode.INPUT_LIMIT_EXCEEDED,
        -> PythonRuntimeHostError.INTERACTIVE_INPUT_FAILED
        else -> PythonRuntimeHostError.EXECUTION_FAILED
    }
    return PythonRuntimeHostException(
        hostError,
        "${code.name}/${phase.name}: $message",
    )
}
