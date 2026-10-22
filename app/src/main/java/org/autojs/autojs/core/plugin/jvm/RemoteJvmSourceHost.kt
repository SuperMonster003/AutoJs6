package org.autojs.autojs.core.plugin.jvm

import android.content.Context
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.autojs.script.JvmFileSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs.util.ViewUtils
import org.autojs.plugin.jvmsource.api.JvmIsolationCapability
import org.autojs.plugin.jvmsource.api.JvmJsonArguments
import org.autojs.plugin.jvmsource.api.JvmProviderInfo
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptCapability
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCancellation
import org.autojs.plugin.jvmsource.api.JvmSourceCapabilities
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceNegotiation
import org.autojs.plugin.jvmsource.api.JvmSourceObservation
import org.autojs.plugin.jvmsource.api.JvmSourcePayloadKind
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceStarted
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class RemoteJvmScriptResult(
    val requestId: String,
    val requestSha256: String,
    val sourceSizeBytes: Long,
    val sourceSha256: String,
    val sourceFileName: String,
    val entryClassName: String,
    val sourcePayloadKind: String,
    val sourceFileCount: Int,
    val sourceContentBytes: Long,
    val resultJson: String,
    val resultSha256: String,
    val stdout: String,
    val stderr: String,
    val stdoutSizeBytes: Long,
    val stdoutSha256: String,
    val stderrSizeBytes: Long,
    val stderrSha256: String,
    val diagnostics: List<JvmSourceDiagnostic>,
    val providerId: String,
    val providerPackageName: String,
    val providerComponent: String,
    val providerVersionName: String,
    val providerVersionCode: Long,
    val providerSignerSha256: List<String>,
    val entryApiVersion: Int,
    val sourceCompilerFamily: String,
    val sourceCompilerVersion: String,
    val d8Version: String,
    val minApi: Int,
    val runtimeLibrarySha256: String,
    val toolchainSha256: String,
    val classArtifactSizeBytes: Long,
    val classArtifactSha256: String,
    val dexArtifactSizeBytes: Long,
    val dexArtifactSha256: String,
    val dexVersion: String,
    val loaderKind: String,
    val hostPid: Int,
    val hostUid: Int,
    val hostProcessName: String,
    val providerUid: Int,
    val compilerPid: Int,
    val compilerUid: Int,
    val compilerProcessName: String,
    val workerPid: Int,
    val workerUid: Int,
    val workerProcessName: String,
    val workerGeneration: Long,
    val internalBridgeCallerPid: Int,
    val internalBridgeCallerUid: Int,
    val internalBridgeCallCount: Int,
    val compilationElapsedMillis: Long,
    val executionElapsedMillis: Long,
    val elapsedMillis: Long,
    val observation: JvmSourceObservation?,
    val allowlistedBridgeOperationCount: Int,
    val rejectedBridgeOperationCount: Int,
    val hostBridgeCallerPids: List<Int>,
)

internal data class JvmSourceHostProcessIdentity(
    val pid: Int,
    val uid: Int,
    val processName: String,
) {
    init {
        require(pid > 0)
        require(uid > 0)
        require(processName.isNotBlank())
    }
}

open class RemoteJvmSourceException(
    message: String,
    cause: Throwable? = null,
    val userFailure: JvmSourceUserFailure = JvmSourceUserExperiencePolicy.hostUnavailable(),
) : RuntimeException(message, cause) {
    internal fun withContainment(containment: JvmSourceContainment): RemoteJvmSourceException {
        if (userFailure.containment == containment || containment == JvmSourceContainment.NOT_DISPATCHED) return this
        return RemoteJvmSourceException(
            message = message ?: "JVM source host failed",
            cause = this,
            userFailure = userFailure.withContainment(containment),
        )
    }

    fun toUserFacingException(): JvmSourceUserFacingException = JvmSourceUserFacingException(userFailure)
}

internal class JvmSourceExecutionControl : Closeable {
    private val cancelled = AtomicBoolean(false)
    private val actions = CopyOnWriteArrayList<() -> Unit>()

    val isCancelled: Boolean
        get() = cancelled.get()

    fun onCancel(cancelAction: () -> Unit) {
        actions += cancelAction
        if (cancelled.get() && actions.remove(cancelAction)) cancelAction()
    }

    override fun close() {
        if (!cancelled.compareAndSet(false, true)) return
        actions.concurrentSnapshot().forEach { action ->
            if (actions.remove(action)) runCatching(action)
        }
    }
}

internal class RemoteJvmSourceHost(
    private val transport: JvmSourceTransport,
    private val artifactStore: JvmSourceArtifactStore,
    private val blockingExecutor: ExecutorService,
    private val featureEnabled: Boolean,
    private val selectedComponent: (JvmSourceLanguage) -> JvmSourceComponent?,
    private val hostVersionCode: Long,
    private val hostRuntimeApi: Int,
    private val hostIdentity: JvmSourceHostProcessIdentity,
    private val launchPackage: (String) -> Boolean,
    private val showToast: (String) -> Unit = {},
    private val readClipboard: () -> String = { "" },
    private val writeClipboard: (String) -> Unit = {},
    private val ownedExecutors: List<ExecutorService> = emptyList(),
    private val providerRetirementTimeoutMillis: Long = PROVIDER_CANCELLATION_ACK_TIMEOUT_MILLIS,
) : Closeable {
    private val executionGate = JvmSourceHostExecutionGate<JvmSourceExecutionControl>()
    private val resourcesClosed = AtomicBoolean(false)

    init {
        require(hostVersionCode > 0L)
        require(hostRuntimeApi >= JvmSourceContract.MIN_ANDROID_API)
        require(providerRetirementTimeoutMillis in 1L..PROVIDER_CANCELLATION_ACK_TIMEOUT_MILLIS)
    }

    fun execute(
        source: JvmFileSource,
        control: JvmSourceExecutionControl,
        sink: JvmSourceExecutionSink = JvmSourceExecutionSink.NONE,
        argsJson: String = "{}",
    ): RemoteJvmScriptResult {
        require(JvmSourceExplicitRunner.isAuthorized(source)) {
            "JVM source execution requires runJvmSourceExplicit; generic script execution is not authorized"
        }
        JvmJsonArguments.decode(argsJson)
        JvmSourceR1Policy.requireSupportedFileName(source.sourceKind, source.file.name)
        val language = JvmSourceR1Policy.language(source.sourceKind)
        when (executionGate.tryAcquire(control)) {
            JvmSourceHostExecutionGate.Admission.ACQUIRED -> Unit
            JvmSourceHostExecutionGate.Admission.BUSY ->
                throw RemoteJvmSourceException(
                    "JVM source provider is busy",
                    userFailure = JvmSourceUserExperiencePolicy.providerBusy(),
                )
            JvmSourceHostExecutionGate.Admission.CLOSED ->
                throw RemoteJvmSourceException("JVM source host is closed")
        }
        try {
            return executeOne(source, language, control, sink, argsJson)
        } finally {
            check(executionGate.release(control))
        }
    }

    override fun close() {
        executionGate.close()?.close()
        if (!resourcesClosed.compareAndSet(false, true)) return
        runCatching { artifactStore.close() }
        ownedExecutors.forEach(ExecutorService::shutdownNow)
    }

    private fun executeOne(
        source: JvmFileSource,
        language: JvmSourceLanguage,
        control: JvmSourceExecutionControl,
        sink: JvmSourceExecutionSink,
        argsJson: String,
    ): RemoteJvmScriptResult {
        if (!featureEnabled) {
            throw RemoteJvmSourceException(
                "JVM source experiment is disabled",
                userFailure = JvmSourceUserExperiencePolicy.experimentDisabled(),
            )
        }
        val selected = try {
            selectedComponent(language)
        } catch (error: Throwable) {
            throw RemoteJvmSourceException(
                "Unable to inspect JVM source provider selection",
                error,
                JvmSourceUserExperiencePolicy.hostUnavailable(),
            )
        }
        if (selected == null) {
            val candidates = try {
                transport.discoverExactAction(JvmSourceContract.SERVICE_ACTION)
            } catch (error: Throwable) {
                throw RemoteJvmSourceException(
                    "Unable to discover a JVM source provider",
                    error,
                    JvmSourceUserExperiencePolicy.hostUnavailable(),
                )
            }
            val failure = JvmSourceUserExperiencePolicy.providerUnavailableWithoutSelection(
                JvmSourceProviderLanguagePolicy.matching(candidates, language),
            )
            throw RemoteJvmSourceException(
                "JVM source provider is unavailable [${failure.code.stableCode}]",
                userFailure = failure,
            )
        }
        val exact = selected
        val deadline = Deadline(JvmSourceContract.DEFAULT_TIMEOUT_MILLIS)
        var snapshot: JvmSourceInputSnapshot? = null
        var stdoutCapture: JvmSourceOutputCapture? = null
        var stderrCapture: JvmSourceOutputCapture? = null
        var binding: JvmSourceBinding? = null
        var session: JvmSourceRemoteSession? = null
        var sourceDescriptor: JvmSourceOwnedDescriptor? = null
        var stdoutDescriptor: JvmSourceOwnedDescriptor? = null
        var stderrDescriptor: JvmSourceOwnedDescriptor? = null
        var bridge: JvmHostAppLaunchBridge? = null
        var collectorForCleanup: CallbackCollector? = null
        var retirementSignal: Future<ProviderRetirement>? = null
        val providerSessionDispatchStarted = AtomicBoolean(false)
        var terminalAwaitEntered = false
        var retirementWaitAttempted = false
        var completedNormally = false
        val diagnosticsPublished = AtomicBoolean(false)
        val outputPublishLock = Any()
        val stdoutObserver = JvmSourceStreamingOutputObserver { line ->
            synchronized(outputPublishLock) { sink.publishOutput(line, "") }
        }
        val stderrObserver = JvmSourceStreamingOutputObserver { line ->
            synchronized(outputPublishLock) { sink.publishOutput("", line) }
        }
        fun publishDiagnostics() {
            val collector = collectorForCleanup ?: return
            if (!diagnosticsPublished.compareAndSet(false, true)) return
            val values = runCatching { collector.diagnostics() }.getOrNull().orEmpty()
            if (values.isNotEmpty()) runCatching { sink.publishDiagnostics(values) }
        }
        fun publishUnstreamedOutput(stdout: JvmSourceCapturedOutput?, stderr: JvmSourceCapturedOutput?) {
            if (stdout == null && stderr == null) return
            val out = if (stdoutObserver.hasObservedBytes) {
                ""
            } else {
                stdout?.let { runCatching { it.text }.getOrNull() }.orEmpty()
            }
            val err = if (stderrObserver.hasObservedBytes) {
                ""
            } else {
                stderr?.let { runCatching { it.text }.getOrNull() }.orEmpty()
            }
            if (out.isNotEmpty() || err.isNotEmpty()) {
                runCatching { synchronized(outputPublishLock) { sink.publishOutput(out, err) } }
            }
        }
        fun requestProviderQuarantine() {
            collectorForCleanup?.requestHostCancellation()
            bridge?.close()
            runCatching { session?.cancel() }
        }
        fun awaitProviderQuarantineIfDispatched(): JvmSourceContainment {
            requestProviderQuarantine()
            if (!providerSessionDispatchStarted.get()) return JvmSourceContainment.NOT_DISPATCHED
            val signal = retirementSignal ?: return JvmSourceContainment.QUARANTINED
            if (retirementWaitAttempted) {
                return if (signal.isDone) JvmSourceContainment.RETIRED else JvmSourceContainment.QUARANTINED
            }
            retirementWaitAttempted = true
            return try {
                awaitProviderRetirement(signal)
                JvmSourceContainment.RETIRED
            } catch (error: Throwable) {
                if (error is InterruptedException) Thread.currentThread().interrupt()
                // Keep the original host failure. The slot is released only after a trusted
                // retirement signal, transport death, or this bounded quarantine expires.
                JvmSourceContainment.QUARANTINED
            }
        }
        try {
            ensureActive(control)
            val packageInput = JvmSourceExplicitRunner.packageInput(source)
            val entrySimpleName = requireNotNull(JvmSourceExplicitRunner.entrySimpleName(source)) {
                "Authorized JVM source omitted its entry selection"
            }
            val inputSnapshot = try {
                if (packageInput == null) {
                    artifactStore.snapshotSource(
                        source.file,
                        JvmSourceContract.MAX_SOURCE_BYTES,
                        entrySimpleName,
                    )
                } else {
                    require(language == JvmSourceLanguage.JAVA) { "Source packages currently admit Java only" }
                    artifactStore.snapshotSourcePackage(
                        packageInput.sourceRoot,
                        packageInput.entryFile,
                        JvmSourceContract.MAX_SOURCE_ARCHIVE_BYTES,
                        entrySimpleName,
                    )
                }
            } catch (error: IllegalArgumentException) {
                throw RemoteJvmSourceException(
                    "JVM source failed host input policy",
                    error,
                    JvmSourceUserExperiencePolicy.sourcePolicyViolation(language),
                )
            }
            snapshot = inputSnapshot
            val maximumPayloadBytes = when (inputSnapshot.sourcePayloadKind) {
                JvmSourcePayloadKind.SINGLE_FILE -> JvmSourceContract.MAX_SOURCE_BYTES
                JvmSourcePayloadKind.SOURCE_ARCHIVE -> JvmSourceContract.MAX_SOURCE_ARCHIVE_BYTES
            }
            require(inputSnapshot.sizeBytes in 1L..maximumPayloadBytes)
            require(inputSnapshot.language == language) { "JVM source snapshot language changed during admission" }
            val candidates = JvmSourceProviderLanguagePolicy.matching(
                transport.discoverExactAction(JvmSourceContract.SERVICE_ACTION),
                language,
            )
            JvmSourceUserExperiencePolicy.providerAdmission(candidates, exact)?.let { failure ->
                throw RemoteJvmSourceException(
                    "Selected JVM source provider failed admission [${failure.code.stableCode}]",
                    userFailure = failure,
                )
            }
            val identity = JvmSourceProviderPolicy.selectAndPin(candidates, exact)
                ?: throw RemoteJvmSourceException("Selected JVM source provider failed deterministic admission")
            if (identity.packageUid == hostIdentity.uid) {
                throw RemoteJvmSourceException(
                    "JVM source provider must use a UID distinct from the host",
                    userFailure = JvmSourceUserExperiencePolicy.providerIsolationViolation(),
                )
            }

            val connectionFuture = CompletableFuture<JvmSourceRemoteConnection>()
            val fatalCause = AtomicReference<Throwable?>()
            val collectorSlot = AtomicReference<CallbackCollector?>()
            fun signalFatal(cause: Throwable, transportRetired: Boolean) {
                fatalCause.compareAndSet(null, cause)
                connectionFuture.completeExceptionally(cause)
                collectorSlot.get()?.let { collector ->
                    if (transportRetired) collector.transportDied(cause) else collector.protocolViolation(cause)
                }
            }
            fun requireNoFatalTransportEvent() {
                fatalCause.get()?.let { throw it }
            }
            control.onCancel {
                connectionFuture.completeExceptionally(JvmSourceHostCancelledException())
            }
            val exactBinding = transport.bindExact(identity, object : JvmSourceBindingCallback {
                override fun onConnected(connection: JvmSourceRemoteConnection) {
                    connectionFuture.complete(connection)
                }

                override fun onBindingFailure(cause: Throwable) {
                    signalFatal(cause, transportRetired = false)
                }

                override fun onBinderDeath(loss: JvmSourceProviderTransportLoss) {
                    signalFatal(
                        RemoteJvmSourceException(
                            "JVM source provider became unavailable [$loss]",
                            userFailure = JvmSourceUserExperiencePolicy.hostUnavailable(),
                        ),
                        transportRetired = true,
                    )
                }
            })
            binding = exactBinding
            val connection = await(connectionFuture, deadline)
            requireNoFatalTransportEvent()
            ensureActive(control)
            requireIdentity(identity)

            val handshake = blocking(deadline, control) {
                val encodedInfo = connection.getProviderInfo().requireMetadataSize()
                val encodedCapabilities = connection.getCapabilities().requireMetadataSize()
                Handshake(
                    info = JvmSourceCodec.decodeProviderInfo(encodedInfo),
                    capabilities = JvmSourceCodec.decodeCapabilities(encodedCapabilities),
                )
            }
            requireNoFatalTransportEvent()
            val negotiated = validateHandshake(identity, handshake, language)
            val request = request(inputSnapshot, handshake.capabilities, negotiated, argsJson)
            val encodedRequest = JvmSourceCodec.encodeRequest(request).requireMetadataSize()
            val requestSha256 = JvmSha256.digest(encodedRequest).toHexString()
            val collector = CallbackCollector(request, handshake.info, handshake.capabilities, identity)
            collectorSlot.set(collector)
            collectorForCleanup = collector
            retirementSignal = collector.retirement
            fatalCause.get()?.let { cause ->
                throw cause
            }
            val hostBridge = JvmHostAppLaunchBridge(
                requestId = request.requestId,
                allowedMethods = request.allowedHostCalls,
                launchPackage = launchPackage,
                showToast = showToast,
                readClipboard = readClipboard,
                writeClipboard = writeClipboard,
                onViolation = collector::protocolViolation,
            )
            bridge = hostBridge
            control.onCancel {
                collector.requestHostCancellation()
                hostBridge.close()
            }

            val stdoutOutput = artifactStore.outputCapture(request.maxStdoutBytes, stdoutObserver)
            stdoutCapture = stdoutOutput
            val stderrOutput = artifactStore.outputCapture(request.maxStderrBytes, stderrObserver)
            stderrCapture = stderrOutput
            val sourceFd = inputSnapshot.claimReadDescriptor()
            sourceDescriptor = sourceFd
            val stdoutFd = stdoutOutput.claimWriteDescriptor()
            stdoutDescriptor = stdoutFd
            val stderrFd = stderrOutput.claimWriteDescriptor()
            stderrDescriptor = stderrFd

            ensureActive(control)
            requireNoFatalTransportEvent()
            requireIdentity(identity) // Final TOCTOU check immediately before dispatch.
            session = openSession(
                connection = connection,
                encodedRequest = encodedRequest,
                sourceDescriptor = sourceFd,
                stdoutDescriptor = stdoutFd,
                stderrDescriptor = stderrFd,
                bridge = hostBridge,
                callback = collector,
                deadline = deadline,
                control = control,
                dispatchStarted = providerSessionDispatchStarted,
            )
            sourceFd.close()
            sourceDescriptor = null
            stdoutFd.close()
            stdoutDescriptor = null
            stderrFd.close()
            stderrDescriptor = null

            val activeSession = checkNotNull(session)
            control.onCancel {
                collector.requestHostCancellation()
                hostBridge.close()
                runCatching { activeSession.cancel() }
            }
            val dispatchDeadline = Deadline(minOf(request.timeoutMillis, deadline.remainingMillis()))
            terminalAwaitEntered = true
            var executionTimedOut = false
            val terminal = try {
                awaitTerminal(collector.terminal, dispatchDeadline, control)
            } catch (_: TimeoutException) {
                executionTimedOut = true
                requestProviderQuarantine()
                retirementWaitAttempted = true
                awaitProviderRetirement(collector.retirement)
                await(collector.terminal, Deadline(providerRetirementTimeoutMillis))
            }
            activeSession.close()
            session = null
            hostBridge.close()
            bridge = null
            exactBinding.close()
            binding = null

            val stdout = stdoutOutput.await(minOf(deadline.remainingMillis(), OUTPUT_DRAIN_TIMEOUT_MILLIS))
            val stderr = stderrOutput.await(minOf(deadline.remainingMillis(), OUTPUT_DRAIN_TIMEOUT_MILLIS))
            // Diagnostics and program output describe the user's script, not the terminal outcome.
            // Publishing here keeps a compilation failure's ECJ diagnostics visible in the console.
            publishDiagnostics()
            publishUnstreamedOutput(stdout, stderr)
            requireNoFatalTransportEvent()
            val terminalObservation = when (terminal) {
                is Terminal.Completed -> terminal.result.observation
                is Terminal.Failed -> terminal.error.observation
                is Terminal.Cancelled -> terminal.cancellation.observation
            }
            // Callback admission enforces presence for Protocol 1.5+ and absence for older peers.
            terminalObservation?.let { runCatching { sink.publishObservation(it) } }
            if (executionTimedOut) throw RemoteJvmSourceException(
                "JVM source execution timed out",
                userFailure = JvmSourceUserExperiencePolicy.timeout(),
            )
            val result = when (terminal) {
                is Terminal.Completed -> {
                    requireOutputIntegrity(terminal.result, stdout, stderr)
                    val started = collector.started()
                    val bridgeCallerPids = collector.bridgeCallerPids()
                    val bridgeStats = hostBridge.stats()
                    requireExecutionIdentity(identity, started, terminal.result, bridgeCallerPids, bridgeStats)
                    collector.requireNoViolation()
                    RemoteJvmScriptResult(
                        requestId = request.requestId.toString(),
                        requestSha256 = requestSha256,
                        sourceSizeBytes = inputSnapshot.sizeBytes,
                        sourceSha256 = inputSnapshot.sha256Hex,
                        sourceFileName = request.sourceFileName,
                        entryClassName = request.entryClassName,
                        sourcePayloadKind = inputSnapshot.sourcePayloadKind.wireName,
                        sourceFileCount = inputSnapshot.sourceFileCount,
                        sourceContentBytes = inputSnapshot.sourceContentBytes,
                        resultJson = terminal.result.resultJson,
                        resultSha256 = JvmSha256.digest(
                            terminal.result.resultJson.toByteArray(Charsets.UTF_8),
                        ).toHexString(),
                        stdout = stdout.text,
                        stderr = stderr.text,
                        stdoutSizeBytes = terminal.result.stdoutSizeBytes,
                        stdoutSha256 = terminal.result.stdoutSha256.toHexString(),
                        stderrSizeBytes = terminal.result.stderrSizeBytes,
                        stderrSha256 = terminal.result.stderrSha256.toHexString(),
                        diagnostics = collector.diagnostics(),
                        providerId = handshake.info.providerId,
                        providerPackageName = identity.component.packageName,
                        providerComponent = identity.component.flattened,
                        providerVersionName = handshake.info.providerVersionName,
                        providerVersionCode = identity.versionCode,
                        providerSignerSha256 = identity.signerSha256,
                        entryApiVersion = handshake.info.entryApiVersion,
                        sourceCompilerFamily = handshake.capabilities.sourceCompilerFamily.wireName,
                        sourceCompilerVersion = handshake.capabilities.sourceCompilerVersion,
                        d8Version = handshake.capabilities.d8Version,
                        minApi = request.minApi,
                        runtimeLibrarySha256 = handshake.capabilities.runtimeLibraryFingerprint.toHexString(),
                        toolchainSha256 = handshake.capabilities.toolchainFingerprint.toHexString(),
                        classArtifactSizeBytes = terminal.result.classArtifactSizeBytes,
                        classArtifactSha256 = terminal.result.classArtifactSha256.toHexString(),
                        dexArtifactSizeBytes = terminal.result.dexArtifactSizeBytes,
                        dexArtifactSha256 = terminal.result.dexArtifactSha256.toHexString(),
                        dexVersion = terminal.result.dexVersion,
                        loaderKind = terminal.result.loaderKind.wireName,
                        hostPid = hostIdentity.pid,
                        hostUid = hostIdentity.uid,
                        hostProcessName = hostIdentity.processName,
                        providerUid = identity.packageUid,
                        compilerPid = started.compilerPid,
                        compilerUid = started.compilerUid,
                        compilerProcessName = started.compilerProcessName,
                        workerPid = terminal.result.workerPid,
                        workerUid = terminal.result.workerUid,
                        workerProcessName = terminal.result.workerProcessName,
                        workerGeneration = terminal.result.workerGeneration,
                        internalBridgeCallerPid = terminal.result.internalBridgeCallerPid,
                        internalBridgeCallerUid = terminal.result.internalBridgeCallerUid,
                        internalBridgeCallCount = terminal.result.internalBridgeCallCount,
                        compilationElapsedMillis = terminal.result.compilationElapsedMillis,
                        executionElapsedMillis = terminal.result.executionElapsedMillis,
                        elapsedMillis = terminal.result.elapsedMillis,
                        observation = terminalObservation,
                        allowlistedBridgeOperationCount = bridgeStats.allowlistedOperationCount,
                        rejectedBridgeOperationCount = bridgeStats.rejectedOperationCount,
                        hostBridgeCallerPids = bridgeCallerPids.sorted(),
                    )
                }
                is Terminal.Failed -> throw RemoteJvmSourceException(
                    "JVM source provider failed [${terminal.error.phase}/${terminal.error.code}]",
                    userFailure = JvmSourceUserExperiencePolicy.providerError(terminal.error, language),
                )
                is Terminal.Cancelled -> {
                    if (terminal.cancellation.reason == org.autojs.plugin.jvmsource.api.JvmCancellationReason.TIMEOUT) {
                        throw RemoteJvmSourceException(
                            "JVM source execution timed out",
                            userFailure = JvmSourceUserExperiencePolicy.timeout(),
                        )
                    }
                    throw JvmSourceHostCancelledException(
                        "JVM source execution was cancelled [${terminal.cancellation.reason}]",
                    )
                }
            }
            ensureActive(control)
            collector.requireNoViolation()
            completedNormally = true
            return result
        } catch (error: JvmSourceHostCancelledException) {
            if (terminalAwaitEntered && control.isCancelled && retirementSignal?.isDone != true) {
                // awaitTerminal already held the slot for the full cancellation-ack budget.
                retirementWaitAttempted = true
            }
            throw error.withContainment(awaitProviderQuarantineIfDispatched())
        } catch (error: TimeoutException) {
            val containment = awaitProviderQuarantineIfDispatched()
            throw RemoteJvmSourceException(
                "JVM source execution timed out",
                error,
                JvmSourceUserExperiencePolicy.timeout().withContainment(containment),
            )
        } catch (error: RemoteJvmSourceException) {
            throw error.withContainment(awaitProviderQuarantineIfDispatched())
        } catch (error: Throwable) {
            val containment = awaitProviderQuarantineIfDispatched()
            if (control.isCancelled) {
                throw JvmSourceHostCancelledException().withContainment(containment)
            }
            val failure = if (containment == JvmSourceContainment.QUARANTINED) {
                JvmSourceUserExperiencePolicy.providerQuarantined()
            } else {
                JvmSourceUserExperiencePolicy.hostUnavailable().withContainment(containment)
            }
            // Never pass an arbitrary Throwable.message through the user-facing result path.
            throw RemoteJvmSourceException("JVM source host failed", error, failure)
        } finally {
            runCatching { session?.close() }
            runCatching { bridge?.close() }
            runCatching { sourceDescriptor?.close() }
            runCatching { stdoutDescriptor?.close() }
            runCatching { stderrDescriptor?.close() }
            runCatching { binding?.close() }
            // A provider failure aborts before the success drain. Publish whatever the bounded
            // captures already hold, then release them; abort() below closes the pipes for good.
            publishDiagnostics()
            if (!completedNormally) {
                publishUnstreamedOutput(
                    runCatching { stdoutCapture?.await(OUTPUT_DRAIN_TIMEOUT_MILLIS) }.getOrNull(),
                    runCatching { stderrCapture?.await(OUTPUT_DRAIN_TIMEOUT_MILLIS) }.getOrNull(),
                )
                stdoutCapture?.abort()
                stderrCapture?.abort()
            }
            runCatching { stdoutCapture?.close() }
            runCatching { stderrCapture?.close() }
            runCatching { snapshot?.close() }
        }
    }

    private fun requireIdentity(identity: JvmSourcePinnedIdentity) {
        val current = runCatching { transport.inspectExact(identity.component) }.getOrNull()
        val loss = JvmSourceProviderLifecyclePolicy.loss(identity, current)
        if (loss != null) {
            throw RemoteJvmSourceException(
                "JVM source provider changed before dispatch [$loss]",
                userFailure = JvmSourceUserExperiencePolicy.lifecycleLoss(loss),
            )
        }
    }

    private fun requireOutputIntegrity(
        result: JvmSourceResult,
        stdout: JvmSourceCapturedOutput,
        stderr: JvmSourceCapturedOutput,
    ) {
        require(result.stdoutSizeBytes == stdout.bytes.size.toLong()) {
            "Provider stdout size metadata differs from the bounded host drain"
        }
        require(result.stderrSizeBytes == stderr.bytes.size.toLong()) {
            "Provider stderr size metadata differs from the bounded host drain"
        }
        require(result.stdoutSha256.toHexString() == stdout.sha256Hex) {
            "Provider stdout SHA-256 differs from the bounded host drain"
        }
        require(result.stderrSha256.toHexString() == stderr.sha256Hex) {
            "Provider stderr SHA-256 differs from the bounded host drain"
        }
    }

    private fun requireExecutionIdentity(
        identity: JvmSourcePinnedIdentity,
        started: JvmSourceStarted,
        result: JvmSourceResult,
        bridgeCallerPids: Set<Int>,
        bridgeStats: JvmHostAppLaunchBridge.Stats,
    ) {
        require(started.compilerUid == identity.packageUid && started.compilerUid != hostIdentity.uid) {
            "Compiler UID does not preserve host/provider isolation"
        }
        require(started.compilerProcessName == identity.compilerProcessName) {
            "Compiler process name differs from the pinned service process"
        }
        require(started.compilerPid != hostIdentity.pid) { "Compiler PID must differ from the host PID" }
        require(result.workerUid == identity.packageUid && result.workerUid != hostIdentity.uid) {
            "Worker UID does not preserve host/provider isolation"
        }
        require(result.workerProcessName == "${identity.component.packageName}:worker") {
            "Worker process name is not the dedicated provider worker process"
        }
        require(result.workerPid != hostIdentity.pid && result.workerPid != started.compilerPid) {
            "Host, compiler, and worker PIDs must be distinct"
        }
        require(bridgeCallerPids.all { it == started.compilerPid }) {
            "Host bridge calls must be relayed only by the pinned compiler proxy process"
        }
        val observedBridgeCallCount = Math.addExact(
            bridgeStats.allowlistedOperationCount,
            bridgeStats.rejectedOperationCount,
        )
        require(result.internalBridgeCallCount == observedBridgeCallCount) {
            "Provider internal bridge count differs from host-observed bridge dispatches"
        }
        if (bridgeCallerPids.isNotEmpty()) {
            require(result.internalBridgeCallCount > 0 &&
                result.internalBridgeCallerPid == result.workerPid &&
                result.internalBridgeCallerUid == result.workerUid
            ) {
                "Internal bridge identity does not attest the execution worker"
            }
        }
        require((result.internalBridgeCallCount > 0) == bridgeCallerPids.isNotEmpty()) {
            "Internal and external bridge observations disagree"
        }
    }

    private fun validateHandshake(
        identity: JvmSourcePinnedIdentity,
        handshake: Handshake,
        language: JvmSourceLanguage,
    ): org.autojs.plugin.jvmsource.api.JvmProtocolVersion {
        val negotiated = JvmSourceNegotiation.requireNegotiated(handshake.info, hostVersionCode)
        require(handshake.info.providerVersionCode == identity.versionCode) {
            "Provider version differs from pinned package identity"
        }
        JvmSourceValidation.validateProviderInfo(handshake.info)
        JvmSourceValidation.validateCapabilities(handshake.capabilities, handshake.info.entryApiVersion)
        val isolation = handshake.capabilities.isolationCapabilities.toSet()
        val current = transport.inspectExact(identity.component)
        val gate = JvmSourceExperimentalGate.decide(
            JvmSourceExperimentalGate.Input(
                featureEnabled = featureEnabled,
                preferredComponent = identity.component.flattened,
                pluginEnabled = current?.pluginEnabled == true,
                providerAuthorized = current?.providerAuthorized == true,
                identityStable = JvmSourceProviderPolicy.revalidate(identity, current),
                protocolCompatible = true,
                languageSupported = language in handshake.capabilities.languages,
                dedicatedWorker = isolation.containsAll(
                    setOf(
                        JvmIsolationCapability.COMPILER_AND_WORKER_SEPARATED,
                        JvmIsolationCapability.FRESH_WORKER_PER_EXECUTION,
                    ),
                ),
                hardTerminationSupported = JvmIsolationCapability.HARD_KILL_AFTER_GRACE in isolation,
            ),
        )
        if (gate !is JvmSourceExperimentalGate.Decision.Admitted) {
            throw RemoteJvmSourceException(
                "JVM source provider failed experimental admission: ${gate::class.java.simpleName}",
                userFailure = JvmSourceUserExperiencePolicy.hostUnavailable(),
            )
        }
        return negotiated
    }

    private fun request(
        snapshot: JvmSourceInputSnapshot,
        capabilities: JvmSourceCapabilities,
        protocolVersion: org.autojs.plugin.jvmsource.api.JvmProtocolVersion,
        argsJson: String,
    ): JvmSourceRequest {
        val request = JvmSourceRequest(
            requestId = JvmRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = protocolVersion,
            language = snapshot.language,
            sourceFileName = snapshot.sourceFileName,
            sourceSizeBytes = snapshot.sizeBytes,
            sourceSha256 = JvmSha256.fromBytes(snapshot.sha256Hex.hexToBytes()),
            expectedToolchainFingerprint = capabilities.toolchainFingerprint,
            entryClassName = snapshot.entryClassName,
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = minOf(JvmSourceContract.DEFAULT_TIMEOUT_MILLIS, capabilities.maxTimeoutMillis),
            maxStdoutBytes = minOf(JvmSourceContract.MAX_STDOUT_BYTES, capabilities.maxStdoutBytes),
            maxStderrBytes = minOf(JvmSourceContract.MAX_STDERR_BYTES, capabilities.maxStderrBytes),
            diagnosticByteLimit = minOf(JvmSourceContract.MAX_DIAGNOSTIC_BYTES, capabilities.maxDiagnosticBytes),
            allowedHostCalls = capabilities.scriptCapabilities.mapNotNull(JvmScriptCapability::hostMethod),
            grantedCapabilities = capabilities.scriptCapabilities,
            argsJson = argsJson,
            sourcePayloadKind = snapshot.sourcePayloadKind,
            sourceFileCount = snapshot.sourceFileCount,
            sourceContentBytes = snapshot.sourceContentBytes,
        )
        JvmSourceValidation.validateRequestAgainst(request, capabilities, protocolVersion)
        return request
    }

    private fun openSession(
        connection: JvmSourceRemoteConnection,
        encodedRequest: ByteArray,
        sourceDescriptor: JvmSourceOwnedDescriptor,
        stdoutDescriptor: JvmSourceOwnedDescriptor,
        stderrDescriptor: JvmSourceOwnedDescriptor,
        bridge: JvmSourceHostBridgeEndpoint,
        callback: JvmSourceRemoteCallback,
        deadline: Deadline,
        control: JvmSourceExecutionControl,
        dispatchStarted: AtomicBoolean,
    ): JvmSourceRemoteSession {
        val accept = AtomicBoolean(true)
        val future = submit {
            dispatchStarted.set(true)
            connection.openSession(
                encodedRequest,
                sourceDescriptor,
                stdoutDescriptor,
                stderrDescriptor,
                bridge,
                callback,
            ).also { opened ->
                if (!accept.get()) {
                    runCatching { opened.cancel() }
                    runCatching { opened.close() }
                }
            }
        }
        control.onCancel {
            accept.set(false)
            future.cancel(true)
        }
        return try {
            await(future, deadline)
        } catch (error: Throwable) {
            accept.set(false)
            future.cancel(true)
            throw error
        }
    }

    private fun <T> blocking(
        deadline: Deadline,
        control: JvmSourceExecutionControl,
        action: () -> T,
    ): T {
        val future = submit(action)
        control.onCancel { future.cancel(true) }
        return await(future, deadline)
    }

    private fun <T> submit(action: () -> T): Future<T> = try {
        blockingExecutor.submit<T> { action() }
    } catch (error: RuntimeException) {
        throw RemoteJvmSourceException(
            "JVM source Binder executor is unavailable",
            error,
            JvmSourceUserExperiencePolicy.hostUnavailable(),
        )
    }

    private fun <T> await(future: Future<T>, deadline: Deadline): T {
        val remaining = deadline.remainingMillis()
        if (remaining <= 0L) throw TimeoutException()
        return try {
            future.get(remaining, TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    /**
     * Cancellation is a protocol state, not a transport violation. Give the provider enough time
     * to interrupt and hard-stop its worker, while still bounding a provider that never sends the
     * required CANCELLED terminal callback.
     */
    private fun <T> awaitTerminal(
        future: Future<T>,
        deadline: Deadline,
        control: JvmSourceExecutionControl,
    ): T {
        var cancellationStartedNanos: Long? = null
        while (true) {
            if (future.isDone) return await(future, deadline)
            val now = System.nanoTime()
            if (control.isCancelled && cancellationStartedNanos == null) {
                cancellationStartedNanos = now
            }
            val cancellationElapsedMillis = cancellationStartedNanos?.let { started ->
                TimeUnit.NANOSECONDS.toMillis(now - started)
            }
            if (cancellationElapsedMillis != null &&
                cancellationElapsedMillis >= providerRetirementTimeoutMillis
            ) {
                throw JvmSourceHostCancelledException("JVM source provider did not acknowledge cancellation")
            }
            if (cancellationElapsedMillis == null && deadline.isExpired()) throw TimeoutException()
            val cancellationRemaining = cancellationElapsedMillis?.let { elapsed ->
                providerRetirementTimeoutMillis - elapsed
            } ?: TERMINAL_POLL_MILLIS
            val waitMillis = minOf(
                if (cancellationElapsedMillis == null) deadline.remainingMillis() else cancellationRemaining,
                TERMINAL_POLL_MILLIS,
                cancellationRemaining,
            ).coerceAtLeast(1L)
            try {
                return future.get(waitMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // Re-check cancellation and both deadlines before the next bounded wait.
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
    }

    /**
     * A provider terminal is also the R2 retirement acknowledgement: its process-local session
     * gate has been released and any submitted worker has died. Keep the host slot owned while
     * waiting for that acknowledgement after the execution deadline.
     */
    private fun awaitProviderRetirement(future: Future<ProviderRetirement>): ProviderRetirement {
        val retirementDeadline = Deadline(providerRetirementTimeoutMillis)
        while (true) {
            if (future.isDone) return await(future, retirementDeadline)
            if (retirementDeadline.isExpired()) {
                throw TimeoutException("JVM source provider did not acknowledge retirement")
            }
            try {
                return future.get(
                    minOf(retirementDeadline.remainingMillis(), TERMINAL_POLL_MILLIS),
                    TimeUnit.MILLISECONDS,
                )
            } catch (_: TimeoutException) {
                // Keep the execution gate owned until terminal/transport death or the 3s bound.
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
    }

    private fun ensureActive(control: JvmSourceExecutionControl) {
        if (control.isCancelled) throw JvmSourceHostCancelledException()
    }

    private inner class CallbackCollector(
        private val request: JvmSourceRequest,
        private val providerInfo: JvmProviderInfo,
        private val capabilities: JvmSourceCapabilities,
        private val identity: JvmSourcePinnedIdentity,
    ) : JvmSourceRemoteCallback {
        private val lifecycle = JvmSourceSessionLifecycle(request.diagnosticByteLimit)
        private val diagnosticValues = ArrayList<JvmSourceDiagnostic>()
        private val violation = AtomicReference<Throwable?>()
        private val startedValue = AtomicReference<JvmSourceStarted?>()
        private val bridgeCallerPidValues = AtomicReference<Set<Int>?>(null)
        private val quarantineRequested = AtomicBoolean(false)
        val terminal = CompletableFuture<Terminal>()
        val retirement = CompletableFuture<ProviderRetirement>()

        override fun onStarted(metadata: ByteArray) = decode(metadata, JvmSourceCodec::decodeStarted) { started ->
            JvmSourceValidation.validateStartedAgainst(started, request, providerInfo, capabilities)
            accept(started.compilerUid == identity.packageUid, "Started compiler UID differs from pinned provider UID")
            accept(
                started.compilerProcessName == identity.compilerProcessName,
                "Started compiler process differs from pinned service process",
            )
            accept(lifecycle.acceptStarted(), "Invalid onStarted ordering")
            accept(startedValue.compareAndSet(null, started), "Started metadata was already recorded")
        }

        override fun onDiagnostic(diagnostic: ByteArray) = decode(
            diagnostic,
            JvmSourceCodec::decodeDiagnostic,
        ) { value ->
            JvmSourceValidation.validateDiagnosticAgainst(value, request)
            accept(lifecycle.acceptDiagnostic(diagnostic.size), "Invalid or over-quota diagnostic")
            synchronized(diagnosticValues) {
                diagnosticValues += JvmSourcePublicDiagnosticPolicy.sanitize(value)
            }
        }

        override fun onCompleted(
            result: ByteArray,
            bridgeCallerPids: Set<Int>,
        ) = decode(result, JvmSourceCodec::decodeResult) { value ->
            JvmSourceValidation.validateResultAgainstRuntime(value, request, hostRuntimeApi)
            JvmSourceValidation.validateTerminalObservationAgainst(value.observation, request)
            accept(bridgeCallerPids.all { it > 0 }, "Host bridge caller PID is invalid")
            accept(
                bridgeCallerPidValues.compareAndSet(null, bridgeCallerPids.toSet()),
                "Host bridge caller identity was already recorded",
            )
            accept(lifecycle.acceptCompleted(), "Invalid onCompleted ordering")
            retirement.complete(ProviderRetirement.VALID_TERMINAL)
            accept(terminal.complete(Terminal.Completed(value)), "Terminal callback was already recorded")
        }

        override fun onFailed(error: ByteArray) = decode(error, JvmSourceCodec::decodeError) { value ->
            JvmSourceValidation.validateError(value)
            accept(value.requestId == request.requestId, "Error request ID differs")
            JvmSourceValidation.validateTerminalObservationAgainst(value.observation, request)
            accept(lifecycle.acceptFailed(), "Invalid onFailed ordering")
            retirement.complete(ProviderRetirement.VALID_TERMINAL)
            accept(terminal.complete(Terminal.Failed(value)), "Terminal callback was already recorded")
        }

        override fun onCancelled(cancellation: ByteArray) {
            try {
                val value = JvmSourceCodec.decodeCancellation(cancellation.requireMetadataSize())
                JvmSourceValidation.validateCancellation(value)
                accept(value.requestId == request.requestId, "Cancellation request ID differs")
                JvmSourceValidation.validateTerminalObservationAgainst(value.observation, request)
                if (tryAcceptQuarantineCancellation()) return
                val lifecycleAccepted = lifecycle.acceptCancelled()
                if (!lifecycleAccepted && tryAcceptQuarantineCancellation()) return
                accept(lifecycleAccepted, "Invalid onCancelled ordering")
                retirement.complete(ProviderRetirement.VALID_TERMINAL)
                val terminalAccepted = terminal.complete(Terminal.Cancelled(value))
                if (!terminalAccepted && tryAcceptQuarantineCancellation()) return
                accept(terminalAccepted, "Terminal callback was already recorded")
            } catch (error: Throwable) {
                protocolViolation(error)
            }
        }

        override fun onTransportViolation(cause: Throwable) = protocolViolation(cause)

        fun requestHostCancellation(): Boolean {
            quarantineRequested.set(true)
            return lifecycle.requestCancel()
        }

        fun protocolViolation(cause: Throwable) {
            violation.compareAndSet(null, cause)
            lifecycle.abort()
            terminal.completeExceptionally(cause)
        }

        fun transportDied(cause: Throwable) {
            violation.compareAndSet(null, cause)
            lifecycle.abort()
            terminal.completeExceptionally(cause)
            retirement.complete(ProviderRetirement.TRANSPORT_DEATH)
        }

        /** A callback delivered after a successful terminal cannot rewrite CompletableFuture. */
        fun requireNoViolation() {
            violation.get()?.let { throw it }
        }

        fun diagnostics(): List<JvmSourceDiagnostic> = synchronized(diagnosticValues) {
            diagnosticValues.toList()
        }

        fun started(): JvmSourceStarted = checkNotNull(startedValue.get()) {
            "Provider completed without accepted compiler identity metadata"
        }

        fun bridgeCallerPids(): Set<Int> = checkNotNull(bridgeCallerPidValues.get()) {
            "Provider completed without host bridge caller identity metadata"
        }

        private fun tryAcceptQuarantineCancellation(): Boolean =
            violation.get() != null &&
                quarantineRequested.get() &&
                retirement.complete(ProviderRetirement.VALID_TERMINAL)

        private fun <T> decode(encoded: ByteArray, decoder: (ByteArray) -> T, acceptValue: (T) -> Unit) {
            if (terminal.isDone) {
                protocolViolation(SecurityException("Callback arrived after terminal state"))
                return
            }
            try {
                acceptValue(decoder(encoded.requireMetadataSize()))
            } catch (error: Throwable) {
                protocolViolation(error)
            }
        }

        private fun accept(condition: Boolean, message: String) {
            if (!condition) throw SecurityException(message)
        }
    }

    private data class Handshake(
        val info: JvmProviderInfo,
        val capabilities: JvmSourceCapabilities,
    )

    private sealed interface Terminal {
        data class Completed(val result: JvmSourceResult) : Terminal
        data class Failed(val error: JvmSourceError) : Terminal
        data class Cancelled(val cancellation: JvmSourceCancellation) : Terminal
    }

    private enum class ProviderRetirement {
        VALID_TERMINAL,
        TRANSPORT_DEATH,
    }

    companion object {
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 2_000L
        private const val PROVIDER_CANCELLATION_ACK_TIMEOUT_MILLIS = 3_000L
        private const val TERMINAL_POLL_MILLIS = 100L

        fun createAndroid(
            context: Context,
            featureEnabled: Boolean,
            hostVersionCode: Long,
            launchPackage: (String) -> Boolean,
            hasResumedActivity: () -> Boolean,
        ): RemoteJvmSourceHost {
            val drains = java.util.concurrent.Executors.newFixedThreadPool(
                2,
                NamedDaemonThreadFactory("JvmSourceOutput"),
            )
            val binderCalls = ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                SynchronousQueue(),
                NamedDaemonThreadFactory("JvmSourceBinder"),
                ThreadPoolExecutor.AbortPolicy(),
            )
            val transport = AndroidJvmSourceTransport(context)
            val clipboard = AndroidJvmClipboardActions(context, hasResumedActivity)
            return RemoteJvmSourceHost(
                transport = transport,
                artifactStore = AndroidJvmSourceArtifactStore(context, drains),
                blockingExecutor = binderCalls,
                featureEnabled = featureEnabled,
                selectedComponent = { language ->
                    JvmSourceProviderAutoSelection.getOrAutoSelect(context, transport, language)
                },
                hostVersionCode = hostVersionCode,
                hostRuntimeApi = android.os.Build.VERSION.SDK_INT,
                hostIdentity = JvmSourceHostProcessIdentity(
                    pid = android.os.Process.myPid(),
                    uid = android.os.Process.myUid(),
                    processName = context.applicationInfo.processName,
                ),
                launchPackage = launchPackage,
                showToast = { message -> ViewUtils.showToast(context.applicationContext, message) },
                readClipboard = clipboard::getText,
                writeClipboard = clipboard::setText,
                ownedExecutors = listOf(drains, binderCalls),
            )
        }
    }
}

private class Deadline(timeoutMillis: Long) {
    private val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

    fun isExpired(): Boolean = System.nanoTime() - deadlineNanos >= 0L

    fun remainingMillis(): Long = maxOf(
        1L,
        TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()),
    )
}

private class NamedDaemonThreadFactory(private val prefix: String) : ThreadFactory {
    private val next = AtomicInteger()
    override fun newThread(runnable: Runnable): Thread = Thread(runnable, "$prefix-${next.incrementAndGet()}").apply {
        isDaemon = true
    }
}

private class JvmSourceHostCancelledException(message: String = "JVM source execution was cancelled") :
    RemoteJvmSourceException(
        message,
        userFailure = JvmSourceUserExperiencePolicy.cancelled(),
    )

private fun ByteArray.requireMetadataSize(): ByteArray = apply {
    require(size in 1..JvmSourceContract.MAX_METADATA_BYTES) { "JVM source metadata exceeds its quota" }
}

private fun String.hexToBytes(): ByteArray {
    require(length == 64)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
