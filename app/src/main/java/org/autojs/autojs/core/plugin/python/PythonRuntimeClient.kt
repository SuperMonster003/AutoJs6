package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonRuntimeChannelPolicy
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
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

    fun createExecution(source: PythonFileSource): Execution = Execution(source)

    inner class Execution internal constructor(
        private val source: PythonFileSource,
    ) {

        private val cancelled = AtomicBoolean(false)
        private val terminalClaimed = AtomicBoolean(false)
        private val terminal = CountDownLatch(1)
        private val result = AtomicReference<PythonExecutionResult?>()
        private val failure = AtomicReference<Throwable?>()
        private val pluginStateFailure = AtomicReference<PythonRuntimeHostException?>()
        private val binding = AtomicReference<PythonRuntimeOfficialTransport.Binding?>()
        private val executionSnapshot = AtomicReference<PythonPrivateExecutionSnapshot?>()
        private val reservation = AtomicReference<PythonRuntimeDispatchRegistry.Reservation?>()
        private val registry = PythonRuntimeDispatchRegistry<IPythonExecutionSession>()
        private val stdout = ByteArrayOutputStream()
        private val stderr = ByteArrayOutputStream()
        private val outputFlushed = AtomicBoolean(false)
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

        fun run(): PythonExecutionResult {
            PluginStateChangeRegistry.addListener(pluginStateChangedListener)
            return try {
                runWithPluginStateObserver()
            } catch (error: InterruptedException) {
                pluginStateFailure.get()?.let { throw it }
                throw error
            } finally {
                PluginStateChangeRegistry.removeListener(pluginStateChangedListener)
            }
        }

        private fun runWithPluginStateObserver(): PythonExecutionResult {
            requireRuntimeChannelEnabled()
            checkExecutionActive()
            val installed = inspectInstalledProvider()
            checkProviderPreconditions(installed)
            checkExecutionActive()
            val requestId = PythonRequestId.fromUuid(UUID.randomUUID())
            val snapshot = PythonPrivateExecutionSnapshot.create(
                applicationContext,
                source,
                requestId,
                { cancelled.get() || pluginStateFailure.get() != null },
            ).also(executionSnapshot::set)
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
                val timeoutMillis = minOf(DEFAULT_TIMEOUT_MILLIS, capabilities.limits.maxTimeoutMillis)
                val requirement = PythonRuntimeCapabilityRequirement(
                    workspaceArchive = snapshot.hasWorkspace,
                    hostCapabilitySnapshot = true,
                    minimumSourceBytes = maxOf(1L, snapshot.sourceLengthBytes),
                    minimumHostCapabilitySnapshotBytes = requireNotNull(snapshot.hostCapabilityLengthBytes),
                    minimumOutputBytes = outputBytes,
                    minimumOutputChunkBytes = outputChunkBytes,
                    minimumOutputChunks = outputChunks,
                    minimumOutstandingOutputCredits = 1,
                    minimumTimeoutMillis = timeoutMillis,
                )
                val pinned = selectPinnedProvider(
                    installed.toCandidate(runtimeInfo, capabilities),
                    environment,
                    requirement,
                )
                val request = PythonExecutionRequest(
                    requestId = requestId,
                    protocolVersion = pinned.negotiatedProtocolVersion,
                    entryPoint = snapshot.entryPoint,
                    source = PythonPayloadReference(
                        kind = PythonPayloadKind.SOURCE,
                        descriptorIndex = 0,
                        declaredLengthBytes = snapshot.sourceLengthBytes,
                        sha256 = snapshot.sourceSha256,
                    ),
                    workspaceArchive = snapshot.workspaceLengthBytes?.let { length ->
                        PythonPayloadReference(
                            kind = PythonPayloadKind.WORKSPACE_ARCHIVE,
                            descriptorIndex = 1,
                            declaredLengthBytes = length,
                            sha256 = requireNotNull(snapshot.workspaceSha256),
                        )
                    },
                    hostCapabilitySnapshot = PythonPayloadReference(
                        kind = PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT,
                        descriptorIndex = if (snapshot.hasWorkspace) 2 else 1,
                        declaredLengthBytes = requireNotNull(snapshot.hostCapabilityLengthBytes),
                        sha256 = requireNotNull(snapshot.hostCapabilitySha256),
                    ),
                    timeoutMillis = timeoutMillis,
                    maxOutputBytes = outputBytes,
                    maxOutputChunkBytes = outputChunkBytes,
                    maxOutputChunks = outputChunks,
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
                openSession(provider, pinned, reserved, snapshot)
                awaitTerminal(request.timeoutMillis)
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
                // Suppress the expected death caused by closing a dispatched process-retirement session.
                binding.getAndSet(null)?.close()
                reservation.getAndSet(null)?.let { reserved ->
                    registry.release(reserved)?.let { session -> runCatching { session.close() } }
                }
                executionSnapshot.getAndSet(null)?.close()
                runCatching { flushBufferedOutput() }
            }
        }

        fun cancel() {
            cancelled.set(true)
            cancelRemoteSession()
            binding.getAndSet(null)?.close()
            executionSnapshot.getAndSet(null)?.close()
            terminalClaimed.compareAndSet(false, true)
            terminal.countDown()
        }

        private fun failClosedForPluginStateChange(error: PythonRuntimeHostException) {
            if (!terminalClaimed.compareAndSet(false, true)) return
            pluginStateFailure.set(error)
            failure.set(error)
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
        ) {
            val sessionRef = AtomicReference<IPythonExecutionSession?>()

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

            val executionCallback = object : IPythonExecutionCallback.Stub() {
                override fun onStarted(metadata: ByteArray?) = fromPinnedProvider {
                    reserved.plan.admissionPlan.sessionPolicy?.onStarted(
                        PythonRuntimeCodec.decodeSessionStarted(
                            requireNotNull(metadata) { "Python Runtime started metadata is null" },
                        ),
                    ) ?: error("Python Runtime started before admission")
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
                            result.set(decoded)
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
                    reportPythonFailure(decoded)
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
            }
            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
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

            try {
                val descriptors = snapshot.openReadOnlyDescriptors()
                try {
                    try {
                        callProviderRpc(
                            operation = "session admission",
                            timeoutError = PythonRuntimeHostError.SESSION_REJECTED,
                            failureError = PythonRuntimeHostError.SESSION_REJECTED,
                        ) {
                            provider.openSession(
                                PythonRuntimeCodec.encodeExecutionRequest(reserved.plan.request),
                                descriptors,
                                admissionCallback,
                                executionCallback,
                            )
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
            policy.onOutput(decoded, callbackStream)
            val output = if (callbackStream == PythonOutputStream.STDOUT) stdout else stderr
            synchronized(output) {
                output.write(decoded.toByteArray())
            }
            if (!cancelled.get()) {
                policy.grantOutputCredits(1)
                requireNotNull(session.get()) { "Python Runtime output arrived without a session" }
                    .grantOutputCredits(1)
            }
        }

        private fun awaitTerminal(timeoutMillis: Long) {
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

        private fun abortForHostTimeout(): Boolean {
            if (!terminalClaimed.compareAndSet(false, true)) return false
            cancelRemoteSession()
            binding.getAndSet(null)?.close()
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
            if (terminalClaimed.compareAndSet(false, true)) terminal.countDown()
        }

        private fun finishFailure(error: Throwable) {
            if (terminalClaimed.compareAndSet(false, true)) {
                failure.set(error)
                terminal.countDown()
            }
        }

        private fun flushBufferedOutput() {
            if (!outputFlushed.compareAndSet(false, true)) return
            writeBufferedOutput(stdout, Log.INFO)
            writeBufferedOutput(stderr, Log.ERROR)
        }

        private fun writeBufferedOutput(output: ByteArrayOutputStream, logLevel: Int) {
            val bytes = synchronized(output) { output.toByteArray() }
            if (bytes.isNotEmpty()) {
                // Decode once at the terminal boundary so a UTF-8 code point split across chunks
                // is never replaced by per-chunk decoding.
                globalConsole.write(logLevel, bytes.toString(Charsets.UTF_8))
            }
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

    private fun reportPythonFailure(error: PythonExecutionError) {
        val traceback = error.traceback.joinToString(separator = "\n") { frame ->
            "  at ${frame.fileName}:${frame.lineNumber} in ${frame.functionName}" +
                frame.sourceLine?.let { "\n    $it" }.orEmpty()
        }
        val message = buildString {
            append(error.code.name).append('/').append(error.phase.name).append(": ").append(error.message)
            error.exceptionType?.let { append(" [").append(it).append(']') }
            error.exceptionMessage?.let { append(' ').append(it) }
            if (traceback.isNotEmpty()) append('\n').append(traceback)
        }
        globalConsole.write(Log.ERROR, message)
    }

    companion object {
        private const val EXPECTED_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        private const val BIND_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_RPC_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_RPC_POLL_MILLIS = 100L
        private const val DEFAULT_TIMEOUT_MILLIS = 5L * 60L * 1_000L
        private const val TERMINAL_GRACE_MILLIS = 15_000L
        private const val DEFAULT_OUTPUT_BYTES = 4L * 1024L * 1024L
        private const val DEFAULT_OUTPUT_CHUNK_BYTES = 32 * 1024
        private const val DEFAULT_OUTPUT_CHUNKS = 16_384L

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
        PythonErrorCode.HOST_CAPABILITY_SNAPSHOT_REJECTED ->
            PythonRuntimeHostError.HOST_CAPABILITY_SNAPSHOT_INVALID
        PythonErrorCode.SESSION_OPEN_FAILED,
        PythonErrorCode.INVALID_REQUEST,
        PythonErrorCode.UNSUPPORTED_PROTOCOL,
        PythonErrorCode.UNSUPPORTED_CAPABILITY,
        -> PythonRuntimeHostError.SESSION_REJECTED
        PythonErrorCode.RUNTIME_CRASHED -> PythonRuntimeHostError.EXECUTION_LOST
        PythonErrorCode.TIMEOUT -> PythonRuntimeHostError.EXECUTION_TIMEOUT
        else -> PythonRuntimeHostError.EXECUTION_FAILED
    }
    return PythonRuntimeHostException(
        hostError,
        "${code.name}/${phase.name}: $message",
    )
}
