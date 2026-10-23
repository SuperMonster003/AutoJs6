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
import org.autojs.plugin.python.runtime.api.PythonCancellationMode
import org.autojs.plugin.python.runtime.api.PythonErrorCode
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
import org.autojs.plugin.python.runtime.api.PythonFailurePhase
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeNegotiation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in diagnostic for the real provider's execution deadline and process retirement.
 *
 * This is intentionally separate from the positive real-plugin diagnostic. It proves one narrow
 * sequence only: an infinite pure-Python bytecode loop reaches the provider deadline, a typed
 * TIMEOUT/EXECUTION failure arrives from the pinned plugin UID before the old provider Binder dies,
 * and an exact rebind starts a new runtime generation which executes one distinct finite request
 * once. The host never calls cancel or replays the timed-out request. This is not a native-extension
 * or blocking-I/O timeout claim, and it never writes an acceptance receipt.
 */
class PythonRuntimeRealPluginTimeoutRebindDiagnosticTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireExplicitTimeoutRebindDiagnosticOptIn() {
        assertEquals(
            "Real Python Runtime timeout/rebind diagnostic requires an explicit opt-in argument",
            "true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED),
        )
        assertTrue("Real Python Runtime timeout/rebind diagnostic requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Timeout/rebind diagnostic requires the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real plugin must share the signature-permission signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
    }

    @Test
    fun providerTimeoutFailsBeforeOldBinderDeathThenExactRebindRunsFiniteRequest() {
        val component = PythonRuntimeProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val discovered = transport.discover().filter { snapshot -> snapshot.component == component }
        assertEquals("Exact-action discovery must return one real Python Runtime service", 1, discovered.size)
        val originalSnapshot = discovered.single()
        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, originalSnapshot.declaredAction)
        assertTrue("Real Python Runtime service is not exported", originalSnapshot.exported)
        assertTrue("Real Python Runtime application is disabled", originalSnapshot.applicationEnabled)
        assertTrue("Real Python Runtime service is disabled", originalSnapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, originalSnapshot.requiredPermission)
        assertTrue("Real Python Runtime signer differs from host", originalSnapshot.sameSignerAsHost)
        assertEquals(originalSnapshot, transport.inspectExact(component))

        val dispatchLedger = ConcurrentLinkedQueue<PythonRequestId>()
        val oldBound = bindProvider(originalSnapshot)
        val oldProviderBinder = oldBound.provider.asBinder()
        val timedOut: TimeoutObservation
        try {
            assertNull("Old exact bind reused the discovery action", oldBound.binding.intentAction)
            val oldRuntimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(oldBound.provider.runtimeInfo)
            val oldCapabilities = PythonRuntimeCodec.decodeCapabilities(oldBound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(oldCapabilities, oldRuntimeInfo)
            assertEquals(PythonCancellationMode.PROCESS_RESTART_ONLY, oldCapabilities.cancellationMode)
            assertEquals(EXPECTED_PYTHON_VERSION, oldRuntimeInfo.pythonVersion)

            timedOut = executeUntilProviderTimeout(
                bound = oldBound,
                snapshot = originalSnapshot,
                pythonVersion = oldRuntimeInfo.pythonVersion,
                maximumCredits = oldCapabilities.limits.maxOutstandingOutputCredits,
                dispatchLedger = dispatchLedger,
            )
            assertTrue(
                "Old provider Binder did not die after typed provider timeout",
                oldBound.awaitExpectedBinderDeath(BINDER_DEATH_WAIT_MILLIS),
            )
            assertTrue(
                "Typed TIMEOUT/EXECUTION callback did not precede old provider Binder death",
                oldBound.typedTimeoutTerminalOrder > 0L &&
                    oldBound.binderDeathEventOrder > oldBound.typedTimeoutTerminalOrder,
            )
            oldBound.throwIfBindingFailed()
            assertEquals("Old provider emitted more than one Binder-death observation", 1, oldBound.binderDeathCount)
            assertFalse("Old provider Binder still reports alive after death callback", oldProviderBinder.isBinderAlive)
            timedOut.throwIfCallbackFailed()
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, timedOut.policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, timedOut.policy.terminal)
            assertEquals(1, timedOut.openCount)
            assertEquals(1, timedOut.startDispatchCount)
            assertEquals(1, timedOut.startedCount)
            assertEquals(1, timedOut.failedCount)
            assertEquals(0, timedOut.hostCancelInvocationCount)
            assertEquals(0, timedOut.unexpectedDataOrTerminalCallbackCount)
            assertEquals(EXPECTED_TIMEOUT_CALLBACK_COUNT, timedOut.callbackCount)
            assertTrue(
                "At least one timeout-phase callback came from an unpinned UID: ${timedOut.callbackUids}",
                timedOut.callbackUids.all { uid -> uid == originalSnapshot.packageUid },
            )
        } finally {
            oldBound.close()
        }

        val postDeathSnapshot = transport.inspectExact(component)
        assertEquals("Real plugin package identity changed across process retirement", originalSnapshot, postDeathSnapshot)
        val newBound = bindProvider(requireNotNull(postDeathSnapshot))
        try {
            assertNull("New exact bind reused the discovery action", newBound.binding.intentAction)
            assertTrue("New provider Binder is not alive", newBound.provider.asBinder().isBinderAlive)
            assertTrue("Exact rebind returned the dead provider Binder", newBound.provider.asBinder() != oldProviderBinder)

            val newRuntimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(newBound.provider.runtimeInfo)
            val newCapabilities = PythonRuntimeCodec.decodeCapabilities(newBound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(newCapabilities, newRuntimeInfo)
            assertEquals(EXPECTED_PYTHON_VERSION, newRuntimeInfo.pythonVersion)

            val completed = executeFiniteOnce(
                bound = newBound,
                snapshot = originalSnapshot,
                pythonVersion = newRuntimeInfo.pythonVersion,
                maximumCredits = newCapabilities.limits.maxOutstandingOutputCredits,
                dispatchLedger = dispatchLedger,
            )
            completed.throwIfCallbackFailed()
            assertNotEquals(
                "Exact rebind reused the retired CPython runtime generation",
                timedOut.runtimeGeneration,
                completed.runtimeGeneration,
            )
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, completed.policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, completed.policy.terminal)
            assertEquals(1, completed.openCount)
            assertEquals(1, completed.startDispatchCount)
            assertEquals(1, completed.startedCount)
            assertEquals(1, completed.stdoutCount)
            assertEquals(1, completed.resultCount)
            assertEquals(0, completed.unexpectedTerminalCount)
            assertEquals(EXPECTED_FINITE_CALLBACK_COUNT, completed.callbackCount)
            assertArrayEquals(completed.expectedStdout, completed.stdout)
            assertTrue(
                "At least one rebound-phase callback came from an unpinned UID: ${completed.callbackUids}",
                completed.callbackUids.all { uid -> uid == originalSnapshot.packageUid },
            )
            newBound.throwIfUnexpectedBinderDeath()
        } finally {
            // Avoid reporting the finite session's expected close-time process retirement as a
            // transport failure. The provider-timeout phase above is the only death under test.
            newBound.binding.close()
            newBound.openedSession.get()?.let { session -> runCatching { session.close() } }
        }

        val dispatched = dispatchLedger.toList()
        assertEquals("Timeout/rebind diagnostic must dispatch exactly two distinct requests", 2, dispatched.size)
        assertEquals("Timed-out request was replayed after Binder death", 1, dispatched.count { it == timedOut.requestId })
        assertEquals("Rebound finite request was dispatched more than once", 1, dispatched.count { it != timedOut.requestId })
        assertEquals("Timed-out and rebound requests must not share an ID", 2, dispatched.distinct().size)
    }

    private fun executeUntilProviderTimeout(
        bound: TimeoutBoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        pythonVersion: String,
        maximumCredits: Int,
        dispatchLedger: ConcurrentLinkedQueue<PythonRequestId>,
    ): TimeoutObservation {
        val source = INFINITE_SOURCE.toByteArray(Charsets.UTF_8)
        val request = newRequest(
            source = source,
            entryPoint = TIMEOUT_ENTRY_POINT,
            timeoutMillis = PROVIDER_TIMEOUT_MILLIS,
            protocolVersion = PythonRuntimeNegotiation.requireNegotiated(
                PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo),
            ),
        )
        val policy = PythonExecutionSessionPolicy(request, maximumCredits, pythonVersion)
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val terminal = CountDownLatch(1)
        val openedSession = bound.openedSession
        val openCount = AtomicInteger(0)
        val startDispatchCount = AtomicInteger(0)
        val startedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val hostCancelInvocationCount = AtomicInteger(0)
        val unexpectedDataOrTerminalCallbackCount = AtomicInteger(0)
        val runtimeGeneration = AtomicLong(0L)

        fun failCallback(error: Throwable) {
            callbackFailure.compareAndSet(null, error)
            terminal.countDown()
        }

        fun accept(callingUid: Int, action: () -> Unit): Boolean {
            callbackUids += callingUid
            callbackCount.incrementAndGet()
            if (callingUid != snapshot.packageUid) {
                failCallback(
                    SecurityException(
                        "Callback UID " + callingUid + " differs from pinned UID " + snapshot.packageUid,
                    ),
                )
                return false
            }
            return runCatching(action).onFailure(::failCallback).isSuccess
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(startedCount.incrementAndGet() == 1) { "Timeout session started more than once" }
                    val started = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Timeout session started metadata is null" },
                    )
                    policy.onStarted(started)
                    check(runtimeGeneration.compareAndSet(0L, started.runtimeGeneration)) {
                        "Timeout session runtime generation was assigned more than once"
                    }
                }
            }

            override fun onStdout(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedDataOrTerminalCallbackCount.incrementAndGet()
                    error("Infinite timeout source unexpectedly emitted stdout")
                }
            }

            override fun onStderr(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedDataOrTerminalCallbackCount.incrementAndGet()
                    error("Infinite timeout source unexpectedly emitted stderr")
                }
            }

            override fun onResult(result: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
                val callingUid = Binder.getCallingUid()
                val receivedDescriptors = descriptors?.filterNotNull().orEmpty()
                try {
                    accept(callingUid) {
                        unexpectedDataOrTerminalCallbackCount.incrementAndGet()
                        error("Infinite timeout source unexpectedly returned a result")
                    }
                } finally {
                    receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(failedCount.incrementAndGet() == 1) { "Timeout failure arrived more than once" }
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Timeout failure is null" },
                    )
                    policy.onFailed(decoded)
                    check(decoded.code == PythonErrorCode.TIMEOUT) {
                        "Expected provider TIMEOUT but received " + decoded.code
                    }
                    check(decoded.phase == PythonFailurePhase.EXECUTION) {
                        "Expected EXECUTION timeout phase but received " + decoded.phase
                    }
                    check(!decoded.retryableBeforeDispatch) {
                        "A dispatched provider timeout must not be retryable"
                    }
                    check(decoded.exceptionType == null && decoded.exceptionMessage == null && decoded.traceback.isEmpty()) {
                        "Provider timeout must not masquerade as a Python exception"
                    }
                    bound.recordTypedTimeoutTerminal()
                    terminal.countDown()
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedDataOrTerminalCallbackCount.incrementAndGet()
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Unexpected timeout cancellation is null" },
                    )
                    error("Host did not request cancellation; provider emitted " + decoded.reason)
                }
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val callingUid = Binder.getCallingUid()
                val accepted = accept(callingUid) {
                    check(openCount.incrementAndGet() == 1) { "Timeout session opened more than once" }
                    val safeSession = requireNotNull(session) { "Timeout session opened null" }
                    check(openedSession.compareAndSet(null, safeSession)) {
                        "Timeout session Binder assigned twice"
                    }
                    check(policy.requestStart()) { "Timeout session start was not accepted" }
                    startDispatchCount.incrementAndGet()
                    safeSession.start()
                }
                if (!accepted) session?.let { rejected -> runCatching { rejected.close() } }
            }

            override fun onRejected(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Timeout admission rejection is null" },
                    )
                    throw AssertionError("Timeout admission rejected: " + decoded.code + "/" + decoded.phase)
                }
            }
        }

        withSourceDescriptor(
            request = request,
            source = source,
            label = "timeout",
            capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities),
        ) { descriptor ->
            dispatchLedger += request.requestId
            bound.provider.openSessionForNegotiatedProtocol(
                request,
                arrayOf(descriptor),
                admissionCallback,
                executionCallback,
            )
        }
        assertTrue(
            "Real CPython provider timeout did not produce TIMEOUT/EXECUTION before Binder death",
            bound.awaitTerminalBeforeDeath(terminal, TIMEOUT_TERMINAL_WAIT_MILLIS),
        )
        callbackFailure.get()?.let { error ->
            throw AssertionError("Provider timeout callback validation failed").apply { initCause(error) }
        }
        assertTrue("Provider timeout did not expose a positive runtime generation", runtimeGeneration.get() > 0L)
        return TimeoutObservation(
            requestId = request.requestId,
            runtimeGeneration = runtimeGeneration.get(),
            policy = policy,
            callbackUids = callbackUids.toList(),
            callbackCount = callbackCount.get(),
            openCount = openCount.get(),
            startDispatchCount = startDispatchCount.get(),
            startedCount = startedCount.get(),
            failedCount = failedCount.get(),
            hostCancelInvocationCount = hostCancelInvocationCount.get(),
            unexpectedDataOrTerminalCallbackCount = unexpectedDataOrTerminalCallbackCount.get(),
            callbackFailure = callbackFailure,
        )
    }

    private fun executeFiniteOnce(
        bound: TimeoutBoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        pythonVersion: String,
        maximumCredits: Int,
        dispatchLedger: ConcurrentLinkedQueue<PythonRequestId>,
    ): FiniteObservation {
        val token = "R2_REBIND_ONCE_${UUID.randomUUID()}"
        val expectedStdout = "$token\n".toByteArray(Charsets.UTF_8)
        val sourceText = FINITE_SOURCE_TEMPLATE.replace("__EXPECTED_TOKEN__", token)
        val source = sourceText.toByteArray(Charsets.UTF_8)
        val request = newRequest(
            source = source,
            entryPoint = FINITE_ENTRY_POINT,
            timeoutMillis = FINITE_TIMEOUT_MILLIS,
            arguments = listOf(token),
            protocolVersion = PythonRuntimeNegotiation.requireNegotiated(
                PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo),
            ),
        )
        val policy = PythonExecutionSessionPolicy(request, maximumCredits, pythonVersion)
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val terminal = CountDownLatch(1)
        val openedSession = bound.openedSession
        val openCount = AtomicInteger(0)
        val startDispatchCount = AtomicInteger(0)
        val startedCount = AtomicInteger(0)
        val stdoutCount = AtomicInteger(0)
        val resultCount = AtomicInteger(0)
        val unexpectedTerminalCount = AtomicInteger(0)
        val runtimeGeneration = AtomicLong(0L)
        val stdout = ByteArrayOutputStream()
        val result = AtomicReference<PythonExecutionResult?>()

        fun failCallback(error: Throwable) {
            callbackFailure.compareAndSet(null, error)
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

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(startedCount.incrementAndGet() == 1) { "Rebound session started more than once" }
                    val started = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Rebound session started metadata is null" },
                    )
                    policy.onStarted(started)
                    check(runtimeGeneration.compareAndSet(0L, started.runtimeGeneration)) {
                        "Rebound runtime generation was assigned more than once"
                    }
                }
            }

            override fun onStdout(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    check(stdoutCount.incrementAndGet() == 1) { "Rebound finite source emitted stdout more than once" }
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Rebound stdout chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDOUT)
                    synchronized(stdout) { stdout.write(decoded.toByteArray()) }
                }
            }

            override fun onStderr(chunk: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) { error("Rebound finite source unexpectedly emitted stderr") }
            }

            override fun onResult(encodedResult: ByteArray?, descriptors: Array<out ParcelFileDescriptor?>?) {
                val callingUid = Binder.getCallingUid()
                val receivedDescriptors = descriptors?.filterNotNull().orEmpty()
                try {
                    accept(callingUid) {
                        val descriptorArray = requireNotNull(descriptors) { "Rebound result descriptor array is null" }
                        require(receivedDescriptors.size == descriptorArray.size) {
                            "Rebound result descriptor array contains null"
                        }
                        check(resultCount.incrementAndGet() == 1) { "Rebound result arrived more than once" }
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Rebound result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Rebound result was assigned more than once" }
                        terminal.countDown()
                    }
                } finally {
                    receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedTerminalCount.incrementAndGet()
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Rebound failure is null" },
                    )
                    throw AssertionError("Rebound finite source failed: ${decoded.code}/${decoded.phase}")
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    unexpectedTerminalCount.incrementAndGet()
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Rebound cancellation is null" },
                    )
                    throw AssertionError("Rebound finite source was cancelled: ${decoded.reason}")
                }
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val callingUid = Binder.getCallingUid()
                val accepted = accept(callingUid) {
                    check(openCount.incrementAndGet() == 1) { "Rebound session opened more than once" }
                    val safeSession = requireNotNull(session) { "Rebound session opened null" }
                    check(openedSession.compareAndSet(null, safeSession)) { "Rebound session Binder assigned twice" }
                    check(policy.requestStart()) { "Rebound session start was not accepted" }
                    startDispatchCount.incrementAndGet()
                    safeSession.start()
                    policy.grantOutputCredits(FINITE_OUTPUT_CREDITS)
                    safeSession.grantOutputCredits(FINITE_OUTPUT_CREDITS)
                }
                if (!accepted) session?.let { rejected -> runCatching { rejected.close() } }
            }

            override fun onRejected(error: ByteArray?) {
                val callingUid = Binder.getCallingUid()
                accept(callingUid) {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Rebound admission rejection is null" },
                    )
                    throw AssertionError("Rebound admission rejected: ${decoded.code}/${decoded.phase}")
                }
            }
        }

        withSourceDescriptor(
            request = request,
            source = source,
            label = "rebind",
            capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities),
        ) { descriptor ->
            dispatchLedger += request.requestId
            bound.provider.openSessionForNegotiatedProtocol(
                request,
                arrayOf(descriptor),
                admissionCallback,
                executionCallback,
            )
        }
        assertTrue(
            "Rebound finite source did not reach one terminal callback",
            bound.awaitTerminalWithoutDeath(terminal, FINITE_TERMINAL_WAIT_MILLIS),
        )
        callbackFailure.get()?.let { error ->
            throw AssertionError("Rebound callback validation failed").apply { initCause(error) }
        }
        val acceptedResult = requireNotNull(result.get()) { "Rebound finite source returned no result" }
        assertEquals(0, acceptedResult.exitCode)
        assertEquals(expectedStdout.size.toLong(), acceptedResult.stdoutBytes)
        assertEquals(0L, acceptedResult.stderrBytes)
        assertEquals(1L, acceptedResult.stdoutChunks)
        assertEquals(0L, acceptedResult.stderrChunks)
        return FiniteObservation(
            runtimeGeneration = runtimeGeneration.get(),
            policy = policy,
            callbackUids = callbackUids.toList(),
            callbackCount = callbackCount.get(),
            openCount = openCount.get(),
            startDispatchCount = startDispatchCount.get(),
            startedCount = startedCount.get(),
            stdoutCount = stdoutCount.get(),
            resultCount = resultCount.get(),
            unexpectedTerminalCount = unexpectedTerminalCount.get(),
            expectedStdout = expectedStdout,
            stdout = synchronized(stdout) { stdout.toByteArray() },
            callbackFailure = callbackFailure,
        )
    }

    private fun newRequest(
        source: ByteArray,
        entryPoint: String,
        timeoutMillis: Long,
        arguments: List<String> = emptyList(),
        protocolVersion: org.autojs.plugin.python.runtime.api.PythonProtocolVersion,
    ): PythonExecutionRequest = PythonExecutionRequest(
        requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
        protocolVersion = protocolVersion,
        entryPoint = entryPoint,
        source = PythonPayloadReference(
            kind = PythonPayloadKind.SOURCE,
            descriptorIndex = 0,
            declaredLengthBytes = source.size.toLong(),
            sha256 = PythonSha256.digest(source),
        ),
        arguments = arguments,
        timeoutMillis = timeoutMillis,
        maxOutputBytes = MAX_OUTPUT_BYTES,
        maxOutputChunkBytes = MAX_OUTPUT_CHUNK_BYTES,
        maxOutputChunks = MAX_OUTPUT_CHUNKS,
    )

    private fun withSourceDescriptor(
        request: PythonExecutionRequest,
        source: ByteArray,
        label: String,
        capabilities: PythonRuntimeCapabilities,
        action: (ParcelFileDescriptor) -> Unit,
    ) {
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
        val sourceFile = File(context.cacheDir, "python-runtime-r2-$label-${UUID.randomUUID()}.py")
        sourceFile.writeBytes(source)
        val sourceDescriptor = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val sourceFileDescriptor = sourceDescriptor.fileDescriptor
        try {
            sourceDescriptor.use(action)
        } finally {
            assertFalse("$label source descriptor remained valid after host ownership closed", sourceFileDescriptor.valid())
            source.fill(0)
            assertTrue("Unable to remove $label source snapshot", !sourceFile.exists() || sourceFile.delete())
        }
    }

    private fun bindProvider(snapshot: AndroidPythonRuntimeProviderIdentitySnapshot): TimeoutBoundProvider {
        val providerReference = AtomicReference<IPythonRuntimeProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
        val binderDeath = CountDownLatch(1)
        val binderDeathCount = AtomicInteger(0)
        val eventSequence = AtomicLong(0L)
        val typedTimeoutTerminalOrder = AtomicLong(0L)
        val binderDeathEventOrder = AtomicLong(0L)
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
                    val eventOrder = eventSequence.incrementAndGet()
                    binderDeathEventOrder.compareAndSet(0L, eventOrder)
                    binderDeathCount.incrementAndGet()
                    binderDeath.countDown()
                }
            },
        )
        if (!connected.await(BIND_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            binding.close()
            throw AssertionError("Timed out binding exact real Python Runtime component")
        }
        failure.get()?.let { error ->
            binding.close()
            throw AssertionError("Unable to bind exact real Python Runtime component").apply { initCause(error) }
        }
        val safeProvider = checkNotNull(providerReference.get()) { "Real Python Runtime binding completed without provider" }
        return TimeoutBoundProvider(
            provider = safeProvider,
            binding = binding,
            bindingFailure = failure,
            binderDeath = binderDeath,
            binderDeathCounter = binderDeathCount,
            eventSequence = eventSequence,
            typedTimeoutTerminalOrderState = typedTimeoutTerminalOrder,
            binderDeathEventOrderState = binderDeathEventOrder,
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private class TimeoutBoundProvider(
        val provider: IPythonRuntimeProvider,
        val binding: AndroidPythonRuntimeBinding,
        private val bindingFailure: AtomicReference<Throwable?>,
        private val binderDeath: CountDownLatch,
        private val binderDeathCounter: AtomicInteger,
        private val eventSequence: AtomicLong,
        private val typedTimeoutTerminalOrderState: AtomicLong,
        private val binderDeathEventOrderState: AtomicLong,
    ) : Closeable {
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val binderDeathCount: Int get() = binderDeathCounter.get()
        val typedTimeoutTerminalOrder: Long get() = typedTimeoutTerminalOrderState.get()
        val binderDeathEventOrder: Long get() = binderDeathEventOrderState.get()

        fun recordTypedTimeoutTerminal() {
            val eventOrder = eventSequence.incrementAndGet()
            check(typedTimeoutTerminalOrderState.compareAndSet(0L, eventOrder)) {
                "Typed provider timeout terminal was recorded more than once"
            }
        }

        fun awaitTerminalBeforeDeath(terminal: CountDownLatch, timeoutMillis: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                throwIfBindingFailed()
                if (terminal.count == 0L) return true
                if (binderDeathCounter.get() != 0) {
                    throw AssertionError("Old provider Binder died before the typed provider-timeout terminal")
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                terminal.await(
                    minOf(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)),
                    TimeUnit.NANOSECONDS,
                )
            }
        }

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
            bindingFailure.get()?.let { error ->
                throw AssertionError("Real Python Runtime binding failed").apply { initCause(error) }
            }
        }

        fun throwIfUnexpectedBinderDeath() {
            check(binderDeathCounter.get() == 0) { "Rebound provider Binder died unexpectedly" }
        }

        override fun close() = binding.close()
    }

    private data class TimeoutObservation(
        val requestId: PythonRequestId,
        val runtimeGeneration: Long,
        val policy: PythonExecutionSessionPolicy,
        val callbackUids: List<Int>,
        val callbackCount: Int,
        val openCount: Int,
        val startDispatchCount: Int,
        val startedCount: Int,
        val failedCount: Int,
        val hostCancelInvocationCount: Int,
        val unexpectedDataOrTerminalCallbackCount: Int,
        private val callbackFailure: AtomicReference<Throwable?>,
    ) {
        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { error ->
                throw AssertionError("Provider timeout callback validation failed").apply { initCause(error) }
            }
        }
    }

    private data class FiniteObservation(
        val runtimeGeneration: Long,
        val policy: PythonExecutionSessionPolicy,
        val callbackUids: List<Int>,
        val callbackCount: Int,
        val openCount: Int,
        val startDispatchCount: Int,
        val startedCount: Int,
        val stdoutCount: Int,
        val resultCount: Int,
        val unexpectedTerminalCount: Int,
        val expectedStdout: ByteArray,
        val stdout: ByteArray,
        private val callbackFailure: AtomicReference<Throwable?>,
    ) {
        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { error ->
                throw AssertionError("Rebound callback validation failed").apply { initCause(error) }
            }
        }
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r2.timeoutRebind.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE = "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val TIMEOUT_ENTRY_POINT = "provider_timeout_forever.py"
        const val FINITE_ENTRY_POINT = "rebind_once.py"

        const val BIND_WAIT_MILLIS = 10_000L
        const val PROVIDER_TIMEOUT_MILLIS = 4_000L
        const val TIMEOUT_TERMINAL_WAIT_MILLIS = 20_000L
        const val BINDER_DEATH_WAIT_MILLIS = 20_000L
        const val FINITE_TIMEOUT_MILLIS = 30_000L
        const val FINITE_TERMINAL_WAIT_MILLIS = 45_000L
        const val POLL_MILLIS = 100L
        const val MAX_OUTPUT_BYTES = 4_096L
        const val MAX_OUTPUT_CHUNK_BYTES = 1_024
        const val MAX_OUTPUT_CHUNKS = 8L
        const val FINITE_OUTPUT_CREDITS = 1
        const val EXPECTED_TIMEOUT_CALLBACK_COUNT = 3
        const val EXPECTED_FINITE_CALLBACK_COUNT = 4

        val INFINITE_SOURCE = """
            value = 0
            while True:
                value = (value + 1) & 0x7fffffff
        """.trimIndent() + "\n"

        val FINITE_SOURCE_TEMPLATE = """
            import sys
            assert sys.implementation.name == "cpython", sys.implementation.name
            assert sys.version_info[:3] == (3, 13, 9), sys.version
            assert sys.argv == ["rebind_once.py", "__EXPECTED_TOKEN__"], sys.argv
            sys.stdout.write("__EXPECTED_TOKEN__\n")
        """.trimIndent() + "\n"
    }
}
