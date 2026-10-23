package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import org.autojs.plugin.python.runtime.api.PythonRuntimeNegotiation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in diagnostic for preserving reliable-pipe communication errors across Binder.
 *
 * The first request supplies byte-exact source metadata and bytes, then closes the producer with
 * an error. SOURCE_REJECTED/INPUT_VALIDATION must arrive before any CPython-start observation.
 * Closing that terminal session intentionally retires the dispatched generation, so recovery is
 * proved with an exact component rebind and one distinct finite request. This remains a narrow
 * diagnostic and never constitutes R2 device acceptance.
 */
class PythonRuntimeRealPluginReliablePipeDiagnosticTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireExplicitReliablePipeOptIn() {
        assertEquals(
            "Real Python Runtime reliable-pipe diagnostic requires explicit opt-in",
            "true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED),
        )
        assertTrue("Reliable-pipe diagnostic requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Reliable-pipe diagnostic requires the canonical host", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real plugin must share the signature-permission signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
    }

    @Test
    fun producerErrorRejectsBeforeCpythonThenExactRebindExecutesFiniteSource() {
        val component = PythonRuntimeProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val originalSnapshot = discoverRealProvider(component)
        val dispatchLedger = ConcurrentLinkedQueue<PythonRequestId>()

        val oldBound = bindProvider(originalSnapshot)
        val oldProviderBinder = oldBound.provider.asBinder()
        val rejected: RejectedObservation
        try {
            assertNull("Old exact bind reused the discovery action", oldBound.binding.intentAction)
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(oldBound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(oldBound.provider.capabilities)
            assertRuntimeIdentity(runtimeInfo, capabilities)

            rejected = executeProducerError(
                bound = oldBound,
                snapshot = originalSnapshot,
                runtimeInfo = runtimeInfo,
                capabilities = capabilities,
                dispatchLedger = dispatchLedger,
            )
            rejected.throwIfCallbackFailed()
            assertEquals(PythonErrorCode.SOURCE_REJECTED, rejected.error.code)
            assertEquals(PythonFailurePhase.INPUT_VALIDATION, rejected.error.phase)
            assertFalse("Source rejection must not be retryable after start dispatch", rejected.error.retryableBeforeDispatch)
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, rejected.policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, rejected.policy.terminal)
            assertEquals(1, rejected.openedCount)
            assertEquals(1, rejected.startDispatchCount)
            assertEquals(1, rejected.failedCount)
            assertEquals(0, rejected.startedCount)
            assertEquals(0, rejected.stdoutCount)
            assertEquals(0, rejected.stderrCount)
            assertEquals(0, rejected.resultCount)
            assertEquals(0, rejected.cancelledCount)
            assertEquals(EXPECTED_REJECTED_CALLBACK_COUNT, rejected.callbackCount)
            assertTrue("Reliable-pipe sender descriptors were not both closed", rejected.senderDescriptorsClosed)
            assertTrue(
                "At least one rejection callback came from an unpinned UID: ${rejected.callbackUids}",
                rejected.callbackUids.all { uid -> uid == originalSnapshot.packageUid },
            )
            oldBound.throwIfBindingFailed()
            oldBound.throwIfUnexpectedBinderDeath()

            // This provider retires a generation when a dispatched terminal session is closed.
            // Recovery must therefore use an exact rebind, never a replay of this request.
            requireNotNull(oldBound.openedSession.get()) { "Reliable-pipe session Binder was not retained" }.close()
            assertTrue(
                "Old provider Binder did not die after terminal-session close",
                oldBound.awaitExpectedBinderDeath(BINDER_DEATH_WAIT_MILLIS),
            )
            assertEquals("Old provider emitted more than one Binder-death observation", 1, oldBound.binderDeathCount)
            assertFalse("Old provider Binder still reports alive after retirement", oldProviderBinder.isBinderAlive)
        } finally {
            oldBound.binding.close()
            oldBound.openedSession.get()?.let { session -> runCatching { session.close() } }
        }

        val postDeathSnapshot = requireNotNull(transport.inspectExact(component)) {
            "Real plugin component disappeared after runtime-process retirement"
        }
        assertEquals("Real plugin identity changed across exact rebind", originalSnapshot, postDeathSnapshot)

        val newBound = bindProvider(postDeathSnapshot)
        try {
            assertNull("New exact bind reused the discovery action", newBound.binding.intentAction)
            assertTrue("New exact provider Binder is not alive", newBound.provider.asBinder().isBinderAlive)
            assertTrue("Exact rebind returned the retired provider Binder", newBound.provider.asBinder() != oldProviderBinder)

            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(newBound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(newBound.provider.capabilities)
            assertRuntimeIdentity(runtimeInfo, capabilities)
            val recovered = executeFiniteSource(
                bound = newBound,
                snapshot = postDeathSnapshot,
                runtimeInfo = runtimeInfo,
                capabilities = capabilities,
                dispatchLedger = dispatchLedger,
            )
            recovered.throwIfCallbackFailed()
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, recovered.policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, recovered.policy.terminal)
            assertEquals(1, recovered.openedCount)
            assertEquals(1, recovered.startDispatchCount)
            assertEquals(1, recovered.startedCount)
            assertEquals(1, recovered.resultCount)
            assertEquals(0, recovered.unexpectedCount)
            assertEquals(EXPECTED_RECOVERY_CALLBACK_COUNT, recovered.callbackCount)
            assertTrue("Recovered runtime generation must be positive", recovered.runtimeGeneration > 0L)
            assertTrue("Recovery sender descriptors were not both closed", recovered.senderDescriptorsClosed)
            assertEquals(0, recovered.result.exitCode)
            assertEquals(0L, recovered.result.stdoutBytes)
            assertEquals(0L, recovered.result.stderrBytes)
            assertEquals(0L, recovered.result.stdoutChunks)
            assertEquals(0L, recovered.result.stderrChunks)
            assertEquals(0, recovered.resultDescriptorCount)
            assertTrue("At least one recovery result descriptor remained open", recovered.resultDescriptorsClosed)
            assertTrue(
                "At least one recovery callback came from an unpinned UID: ${recovered.callbackUids}",
                recovered.callbackUids.all { uid -> uid == postDeathSnapshot.packageUid },
            )
            newBound.throwIfUnexpectedBinderDeath()
        } finally {
            // Closing the completed session may retire this second generation. Close the host
            // binding first so that cleanup is not misreported as part of the recovery proof.
            newBound.binding.close()
            newBound.openedSession.get()?.let { session -> runCatching { session.close() } }
        }

        val dispatched = dispatchLedger.toList()
        assertEquals("Diagnostic must dispatch exactly two requests", 2, dispatched.size)
        assertEquals("Reliable-pipe rejected request was replayed", 1, dispatched.count { it == rejected.requestId })
        assertEquals("Recovery request was dispatched more than once", 1, dispatched.count { it != rejected.requestId })
        assertEquals("Rejected and recovery requests must have distinct IDs", 2, dispatched.distinct().size)
    }

    private fun discoverRealProvider(
        component: PythonRuntimeProviderComponent,
    ): AndroidPythonRuntimeProviderIdentitySnapshot {
        val matches = transport.discover().filter { snapshot -> snapshot.component == component }
        assertEquals("Exact-action discovery must return one real Python Runtime service", 1, matches.size)
        val snapshot = matches.single()
        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, snapshot.declaredAction)
        assertTrue("Real Python Runtime service is not exported", snapshot.exported)
        assertTrue("Real Python Runtime application is disabled", snapshot.applicationEnabled)
        assertTrue("Real Python Runtime service is disabled", snapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, snapshot.requiredPermission)
        assertTrue("Real Python Runtime signer differs from host", snapshot.sameSignerAsHost)
        assertTrue("Real Python Runtime package UID is invalid", snapshot.packageUid >= 0)
        assertEquals(snapshot, transport.inspectExact(component))
        return snapshot
    }

    private fun assertRuntimeIdentity(
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
    ) {
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)
        assertEquals(REAL_PROVIDER_ID, runtimeInfo.providerId)
        assertEquals(PythonImplementation.CPYTHON, runtimeInfo.implementation)
        assertEquals(PythonIsolationMode.DEDICATED_PLUGIN_PROCESS, runtimeInfo.isolationMode)
        assertEquals(EXPECTED_PYTHON_VERSION, runtimeInfo.pythonVersion)
        assertEquals(runtimeInfo.pythonVersion, capabilities.pythonVersion)
        assertEquals(PythonImplementation.CPYTHON, capabilities.implementation)
        val deviceAbi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull()) { "Device primary ABI is absent" }
        assertTrue("Real Python Runtime does not advertise $deviceAbi", deviceAbi in capabilities.supportedAbis)
    }

    private fun executeProducerError(
        bound: LifecycleBoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
        dispatchLedger: ConcurrentLinkedQueue<PythonRequestId>,
    ): RejectedObservation {
        val source = REJECTED_SOURCE.toByteArray(Charsets.UTF_8)
        val request = newRequest(source, REJECTED_ENTRY_POINT, runtimeInfo)
        validateRequest(request, capabilities)
        val policy = PythonExecutionSessionPolicy(
            expectedRequest = request,
            maximumCredits = capabilities.limits.maxOutstandingOutputCredits,
            expectedPythonVersion = runtimeInfo.pythonVersion,
        )
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val terminal = CountDownLatch(1)
        val executionError = AtomicReference<PythonExecutionError?>()
        val openedCount = AtomicInteger(0)
        val startDispatchCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val startedCount = AtomicInteger(0)
        val stdoutCount = AtomicInteger(0)
        val stderrCount = AtomicInteger(0)
        val resultCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)

        fun failCallback(cause: Throwable) {
            callbackFailure.compareAndSet(null, cause)
            terminal.countDown()
        }

        fun accept(callingUid: Int, action: () -> Unit): Boolean {
            callbackUids += callingUid
            callbackCount.incrementAndGet()
            if (callingUid != snapshot.packageUid) {
                failCallback(SecurityException("Callback UID $callingUid differs from pinned UID ${snapshot.packageUid}"))
                return false
            }
            return runCatching(action).onFailure(::failCallback).isSuccess
        }

        fun unexpected(callingUid: Int, counter: AtomicInteger, label: String) {
            accept(callingUid) {
                counter.incrementAndGet()
                error("Reliable-pipe source rejection unexpectedly emitted $label")
            }
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, startedCount, "onStarted")
            }

            override fun onStdout(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, stdoutCount, "stdout")
            }

            override fun onStderr(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, stderrCount, "stderr")
            }

            override fun onResult(
                encodedResult: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
            ) {
                val callingUid = Binder.getCallingUid()
                val received = descriptors?.filterNotNull().orEmpty()
                try {
                    accept(callingUid) {
                        resultCount.incrementAndGet()
                        val descriptorArray = requireNotNull(descriptors) { "Unexpected result descriptor array is null" }
                        require(received.size == descriptorArray.size) { "Unexpected result descriptor array contains null" }
                        error("Reliable-pipe source rejection unexpectedly returned a result")
                    }
                } finally {
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(encodedError: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(failedCount.incrementAndGet() == 1) { "Reliable-pipe session failed more than once" }
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Reliable-pipe failure is null" },
                    )
                    policy.onFailed(decoded)
                    check(executionError.compareAndSet(null, decoded)) { "Reliable-pipe error was assigned twice" }
                    terminal.countDown()
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, cancelledCount, "cancellation")
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val callingUid = Binder.getCallingUid()
                val accepted = accept(callingUid) {
                    check(openedCount.incrementAndGet() == 1) { "Reliable-pipe session opened more than once" }
                    val safeSession = requireNotNull(session) { "Reliable-pipe session Binder is null" }
                    check(bound.openedSession.compareAndSet(null, safeSession)) { "Session Binder was assigned twice" }
                    check(policy.requestStart()) { "Reliable-pipe start dispatch was not accepted" }
                    startDispatchCount.incrementAndGet()
                    safeSession.start()
                }
                if (!accepted) session?.let { rejectedSession -> runCatching { rejectedSession.close() } }
            }

            override fun onRejected(encodedError: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Reliable-pipe admission rejection is null" },
                    )
                    throw AssertionError("Reliable-pipe session was rejected during admission: ${decoded.code}/${decoded.phase}")
                }
            }
        }

        val pipe = ParcelFileDescriptor.createReliablePipe()
        val sourceReader = pipe[0]
        val sourceWriter = pipe[1]
        val readerFileDescriptor = sourceReader.fileDescriptor
        val writerFileDescriptor = sourceWriter.fileDescriptor
        var output: ParcelFileDescriptor.AutoCloseOutputStream? = null
        try {
            dispatchLedger += request.requestId
            bound.provider.openSessionForNegotiatedProtocol(
                request,
                arrayOf(sourceReader),
                admissionCallback,
                executionCallback,
            )
            // AIDL duplicates both reliable-pipe descriptors for an in parameter. Closing this
            // original reader before staging completes would emit an OK comm-channel status from
            // a duplicate endpoint and can mask closeWithError. Keep it open but unread until the
            // terminal callback; the receiver copy remains the only reader of data and status.
            output = ParcelFileDescriptor.AutoCloseOutputStream(sourceWriter)
            output.write(source)
            output.flush()
            // Length, digest, and bytes are exact. The retained reliable-pipe communication
            // channel is the only admissible reason for rejecting this source snapshot.
            sourceWriter.closeWithError(RELIABLE_PIPE_PRODUCER_ERROR)

            assertTrue(
                "Reliable-pipe producer error did not reach one terminal callback",
                bound.awaitTerminalWithoutDeath(terminal, TERMINAL_WAIT_MILLIS),
            )
            callbackFailure.get()?.let { cause ->
                throw AssertionError("Reliable-pipe callback validation failed").apply { initCause(cause) }
            }
        } finally {
            source.fill(0)
            runCatching { output?.close() }
            runCatching { sourceReader.close() }
            runCatching { sourceWriter.close() }
        }
        return RejectedObservation(
            requestId = request.requestId,
            policy = policy,
            error = requireNotNull(executionError.get()) { "Reliable-pipe producer error returned no typed failure" },
            callbackUids = callbackUids.toList(),
            callbackCount = callbackCount.get(),
            openedCount = openedCount.get(),
            startDispatchCount = startDispatchCount.get(),
            failedCount = failedCount.get(),
            startedCount = startedCount.get(),
            stdoutCount = stdoutCount.get(),
            stderrCount = stderrCount.get(),
            resultCount = resultCount.get(),
            cancelledCount = cancelledCount.get(),
            senderDescriptorsClosed = !readerFileDescriptor.valid() && !writerFileDescriptor.valid(),
            callbackFailure = callbackFailure,
        )
    }

    private fun executeFiniteSource(
        bound: LifecycleBoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
        dispatchLedger: ConcurrentLinkedQueue<PythonRequestId>,
    ): RecoveryObservation {
        val source = RECOVERY_SOURCE.toByteArray(Charsets.UTF_8)
        val request = newRequest(source, RECOVERY_ENTRY_POINT, runtimeInfo)
        validateRequest(request, capabilities)
        val policy = PythonExecutionSessionPolicy(
            expectedRequest = request,
            maximumCredits = capabilities.limits.maxOutstandingOutputCredits,
            expectedPythonVersion = runtimeInfo.pythonVersion,
        )
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val terminal = CountDownLatch(1)
        val result = AtomicReference<PythonExecutionResult?>()
        val runtimeGeneration = AtomicLong(0L)
        val openedCount = AtomicInteger(0)
        val startDispatchCount = AtomicInteger(0)
        val startedCount = AtomicInteger(0)
        val resultCount = AtomicInteger(0)
        val unexpectedCount = AtomicInteger(0)
        val resultDescriptorCount = AtomicInteger(0)
        val resultDescriptorsClosed = AtomicBoolean(true)

        fun failCallback(cause: Throwable) {
            callbackFailure.compareAndSet(null, cause)
            terminal.countDown()
        }

        fun accept(callingUid: Int, action: () -> Unit): Boolean {
            callbackUids += callingUid
            callbackCount.incrementAndGet()
            if (callingUid != snapshot.packageUid) {
                failCallback(SecurityException("Callback UID $callingUid differs from pinned UID ${snapshot.packageUid}"))
                return false
            }
            return runCatching(action).onFailure(::failCallback).isSuccess
        }

        fun unexpected(callingUid: Int, label: String) {
            accept(callingUid) {
                unexpectedCount.incrementAndGet()
                error("Recovered finite source unexpectedly emitted $label")
            }
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(startedCount.incrementAndGet() == 1) { "Recovered session started more than once" }
                    val decoded = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Recovered started metadata is null" },
                    )
                    policy.onStarted(decoded)
                    check(runtimeGeneration.compareAndSet(0L, decoded.runtimeGeneration)) {
                        "Recovered runtime generation was assigned twice"
                    }
                }
            }

            override fun onStdout(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, "stdout")
            }

            override fun onStderr(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, "stderr")
            }

            override fun onResult(
                encodedResult: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
            ) {
                val callingUid = Binder.getCallingUid()
                val received = descriptors?.filterNotNull().orEmpty()
                val receivedFileDescriptors = received.map { descriptor -> descriptor.fileDescriptor }
                try {
                    accept(callingUid) {
                        check(resultCount.incrementAndGet() == 1) { "Recovered session returned more than one result" }
                        val descriptorArray = requireNotNull(descriptors) { "Recovered result descriptor array is null" }
                        require(received.size == descriptorArray.size) { "Recovered result descriptor array contains null" }
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Recovered result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Recovered result was assigned twice" }
                        terminal.countDown()
                    }
                } finally {
                    resultDescriptorCount.addAndGet(received.size)
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                    if (receivedFileDescriptors.any { descriptor -> descriptor.valid() }) {
                        resultDescriptorsClosed.set(false)
                    }
                }
            }

            override fun onFailed(encodedError: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedCount.incrementAndGet()
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Recovered failure is null" },
                    )
                    throw AssertionError("Recovered finite source failed: ${decoded.code}/${decoded.phase}")
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                unexpected(callingUid, "cancellation")
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val callingUid = Binder.getCallingUid()
                val accepted = accept(callingUid) {
                    check(openedCount.incrementAndGet() == 1) { "Recovered session opened more than once" }
                    val safeSession = requireNotNull(session) { "Recovered session Binder is null" }
                    check(bound.openedSession.compareAndSet(null, safeSession)) { "Recovered session Binder was assigned twice" }
                    check(policy.requestStart()) { "Recovered start dispatch was not accepted" }
                    startDispatchCount.incrementAndGet()
                    safeSession.start()
                }
                if (!accepted) session?.let { rejectedSession -> runCatching { rejectedSession.close() } }
            }

            override fun onRejected(encodedError: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Recovered admission rejection is null" },
                    )
                    throw AssertionError("Recovered session admission failed: ${decoded.code}/${decoded.phase}")
                }
            }
        }

        val pipe = ParcelFileDescriptor.createReliablePipe()
        val sourceReader = pipe[0]
        val sourceWriter = pipe[1]
        val readerFileDescriptor = sourceReader.fileDescriptor
        val writerFileDescriptor = sourceWriter.fileDescriptor
        var output: ParcelFileDescriptor.AutoCloseOutputStream? = null
        try {
            dispatchLedger += request.requestId
            bound.provider.openSessionForNegotiatedProtocol(
                request,
                arrayOf(sourceReader),
                admissionCallback,
                executionCallback,
            )
            // Retain the original reliable-pipe reader until staging publishes onStarted or a
            // terminal callback. A normal close here would interfere with the shared comm status.
            output = ParcelFileDescriptor.AutoCloseOutputStream(sourceWriter)
            output.write(source)
            output.flush()
            output.close()

            assertTrue(
                "Exact-rebind finite source did not reach one terminal callback",
                bound.awaitTerminalWithoutDeath(terminal, RECOVERY_WAIT_MILLIS),
            )
            callbackFailure.get()?.let { cause ->
                throw AssertionError("Exact-rebind recovery callback validation failed").apply { initCause(cause) }
            }
        } finally {
            source.fill(0)
            runCatching { output?.close() }
            runCatching { sourceReader.close() }
            runCatching { sourceWriter.close() }
        }
        return RecoveryObservation(
            policy = policy,
            result = requireNotNull(result.get()) { "Recovered finite source returned no result" },
            runtimeGeneration = runtimeGeneration.get(),
            callbackUids = callbackUids.toList(),
            callbackCount = callbackCount.get(),
            openedCount = openedCount.get(),
            startDispatchCount = startDispatchCount.get(),
            startedCount = startedCount.get(),
            resultCount = resultCount.get(),
            unexpectedCount = unexpectedCount.get(),
            senderDescriptorsClosed = !readerFileDescriptor.valid() && !writerFileDescriptor.valid(),
            resultDescriptorCount = resultDescriptorCount.get(),
            resultDescriptorsClosed = resultDescriptorsClosed.get(),
            callbackFailure = callbackFailure,
        )
    }

    private fun newRequest(
        source: ByteArray,
        entryPoint: String,
        runtimeInfo: PythonRuntimeInfo,
    ): PythonExecutionRequest = PythonExecutionRequest(
        requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
        protocolVersion = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo),
        entryPoint = entryPoint,
        source = PythonPayloadReference(
            kind = PythonPayloadKind.SOURCE,
            descriptorIndex = 0,
            declaredLengthBytes = source.size.toLong(),
            sha256 = PythonSha256.digest(source),
        ),
        timeoutMillis = SESSION_TIMEOUT_MILLIS,
        maxOutputBytes = MAX_OUTPUT_BYTES,
        maxOutputChunkBytes = MAX_OUTPUT_CHUNK_BYTES,
        maxOutputChunks = MAX_OUTPUT_CHUNKS,
    )

    private fun validateRequest(
        request: PythonExecutionRequest,
        capabilities: PythonRuntimeCapabilities,
    ) {
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
    }

    private fun bindProvider(snapshot: AndroidPythonRuntimeProviderIdentitySnapshot): LifecycleBoundProvider {
        val providerReference = AtomicReference<IPythonRuntimeProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
        val binderDeath = CountDownLatch(1)
        val binderDeathCount = AtomicInteger(0)
        val binding = transport.bindExact(
            snapshot,
            object : AndroidPythonRuntimeBindingCallback {
                override fun onConnected(provider: IPythonRuntimeProvider) {
                    if (!providerReference.compareAndSet(null, provider)) {
                        failure.compareAndSet(null, SecurityException("Real Python Runtime connected more than once"))
                    }
                    connected.countDown()
                }

                override fun onBindingFailure(cause: Throwable?) {
                    failure.compareAndSet(
                        null,
                        cause ?: IllegalStateException("Real Python Runtime disconnected while binding"),
                    )
                    connected.countDown()
                    binderDeath.countDown()
                }

                override fun onBinderDeath() {
                    binderDeathCount.incrementAndGet()
                    binderDeath.countDown()
                }
            },
        )
        if (!connected.await(BIND_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            binding.close()
            throw AssertionError("Timed out binding exact real Python Runtime component")
        }
        failure.get()?.let { cause ->
            binding.close()
            throw AssertionError("Unable to bind exact real Python Runtime component").apply { initCause(cause) }
        }
        return LifecycleBoundProvider(
            provider = checkNotNull(providerReference.get()) { "Real Python Runtime binding completed without provider" },
            binding = binding,
            bindingFailure = failure,
            binderDeath = binderDeath,
            binderDeathCounter = binderDeathCount,
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private class LifecycleBoundProvider(
        val provider: IPythonRuntimeProvider,
        val binding: AndroidPythonRuntimeBinding,
        private val bindingFailure: AtomicReference<Throwable?>,
        private val binderDeath: CountDownLatch,
        private val binderDeathCounter: AtomicInteger,
    ) : Closeable {
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val binderDeathCount: Int get() = binderDeathCounter.get()

        fun awaitTerminalWithoutDeath(terminal: CountDownLatch, timeoutMillis: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                throwIfBindingFailed()
                throwIfUnexpectedBinderDeath()
                if (terminal.count == 0L) return true
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                terminal.await(
                    minOf(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)),
                    TimeUnit.NANOSECONDS,
                )
            }
        }

        fun awaitExpectedBinderDeath(timeoutMillis: Long): Boolean =
            binderDeath.await(timeoutMillis, TimeUnit.MILLISECONDS) && binderDeathCounter.get() == 1

        fun throwIfBindingFailed() {
            bindingFailure.get()?.let { cause ->
                throw AssertionError("Real Python Runtime binding failed").apply { initCause(cause) }
            }
        }

        fun throwIfUnexpectedBinderDeath() {
            check(binderDeathCounter.get() == 0) { "Real Python Runtime Binder died unexpectedly" }
        }

        override fun close() = binding.close()
    }

    private data class RejectedObservation(
        val requestId: PythonRequestId,
        val policy: PythonExecutionSessionPolicy,
        val error: PythonExecutionError,
        val callbackUids: List<Int>,
        val callbackCount: Int,
        val openedCount: Int,
        val startDispatchCount: Int,
        val failedCount: Int,
        val startedCount: Int,
        val stdoutCount: Int,
        val stderrCount: Int,
        val resultCount: Int,
        val cancelledCount: Int,
        val senderDescriptorsClosed: Boolean,
        private val callbackFailure: AtomicReference<Throwable?>,
    ) {
        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { cause ->
                throw AssertionError("Reliable-pipe callback validation failed").apply { initCause(cause) }
            }
        }
    }

    private data class RecoveryObservation(
        val policy: PythonExecutionSessionPolicy,
        val result: PythonExecutionResult,
        val runtimeGeneration: Long,
        val callbackUids: List<Int>,
        val callbackCount: Int,
        val openedCount: Int,
        val startDispatchCount: Int,
        val startedCount: Int,
        val resultCount: Int,
        val unexpectedCount: Int,
        val senderDescriptorsClosed: Boolean,
        val resultDescriptorCount: Int,
        val resultDescriptorsClosed: Boolean,
        private val callbackFailure: AtomicReference<Throwable?>,
    ) {
        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { cause ->
                throw AssertionError("Recovery callback validation failed").apply { initCause(cause) }
            }
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r2.reliablePipe.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE = "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val REJECTED_ENTRY_POINT = "reliable_pipe_must_not_run.py"
        const val RECOVERY_ENTRY_POINT = "reliable_pipe_recovery.py"
        const val RELIABLE_PIPE_PRODUCER_ERROR = "AUTOJS6_R2_REAL_RELIABLE_PIPE_PRODUCER_ERROR"

        const val BIND_WAIT_MILLIS = 10_000L
        const val TERMINAL_WAIT_MILLIS = 20_000L
        const val BINDER_DEATH_WAIT_MILLIS = 20_000L
        const val RECOVERY_WAIT_MILLIS = 45_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 100L
        const val MAX_OUTPUT_BYTES = 4_096L
        const val MAX_OUTPUT_CHUNK_BYTES = 1_024
        const val MAX_OUTPUT_CHUNKS = 8L
        const val EXPECTED_REJECTED_CALLBACK_COUNT = 2
        const val EXPECTED_RECOVERY_CALLBACK_COUNT = 3

        const val REJECTED_SOURCE = "raise AssertionError('reliable-pipe source must never execute')\n"
        val RECOVERY_SOURCE = """
            import sys
            assert sys.implementation.name == "cpython", sys.implementation.name
            assert sys.version_info[:3] == (3, 13, 9), sys.version
            assert sys.argv == ["reliable_pipe_recovery.py"], sys.argv
        """.trimIndent() + "\n"
    }
}
