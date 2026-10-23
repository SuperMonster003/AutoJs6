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
import org.autojs.plugin.python.runtime.api.PythonOutputStream
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
import org.autojs.plugin.python.runtime.api.PythonTracebackOrigin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in device diagnostics for real CPython execution outcomes.
 *
 * Every method is launched independently by the exact-serial runner. This is intentionally not an
 * R2 acceptance suite: cancellation, process death/rebind, timeout, update races and hostile callers
 * are outside this matrix. The runner never writes a formal receipt.
 */
class PythonRuntimeRealPluginExecutionMatrixDiagnosticTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireExplicitRealExecutionMatrixOptIn() {
        assertEquals(
            "Real CPython execution matrix requires an explicit opt-in argument",
            "true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED),
        )
        assertTrue("Real CPython execution matrix requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real plugin admits only the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real plugin must share the signature-permission signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
    }

    @Test
    fun systemExitReturnsBoundedExitCode() {
        val observation = executeRealSource(
            caseName = "system-exit",
            sourceText = "import sys\nsys.exit(23)\n",
        )

        assertCommonEnvelope(observation, expectedCallbackCount = 3)
        val result = assertNotNullResult(observation)
        assertEquals(23, result.exitCode)
        assertEquals(0L, result.stdoutBytes)
        assertEquals(0L, result.stderrBytes)
        assertEquals(0L, result.stdoutChunks)
        assertEquals(0L, result.stderrChunks)
        assertArrayEquals(byteArrayOf(), observation.stdout)
        assertArrayEquals(byteArrayOf(), observation.stderr)
        assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, observation.terminal)
    }

    @Test
    fun syntaxErrorReturnsStructuredProjectTraceback() {
        val observation = executeRealSource(
            caseName = "syntax-error",
            sourceText = "def broken(:\n    pass\n",
        )

        assertCommonEnvelope(observation, expectedCallbackCount = 3)
        val error = assertPythonException(observation, "SyntaxError")
        assertFalse("SyntaxError message must not be blank", error.exceptionMessage.orEmpty().isBlank())
        assertTrue("SyntaxError must include a structured traceback", error.traceback.isNotEmpty())
        assertTrue(
            "SyntaxError traceback must include the exact project entry and source line",
            error.traceback.any { frame ->
                frame.origin == PythonTracebackOrigin.PROJECT &&
                    frame.fileName == ENTRY_POINT &&
                    frame.lineNumber == 1 &&
                    frame.functionName == "<module>" &&
                    frame.sourceLine == "def broken(:"
            },
        )
    }

    @Test
    fun runtimeExceptionReturnsStructuredProjectTraceback() {
        val observation = executeRealSource(
            caseName = "runtime-exception",
            sourceText = """
                def explode():
                    marker = "structured"
                    raise ValueError("matrix-boom")
                explode()
            """.trimIndent() + "\n",
        )

        assertCommonEnvelope(observation, expectedCallbackCount = 3)
        val error = assertPythonException(observation, "ValueError")
        assertEquals("matrix-boom", error.exceptionMessage)
        assertTrue("Runtime exception must include a structured traceback", error.traceback.isNotEmpty())
        val projectFrames = error.traceback.filter { frame ->
            frame.origin == PythonTracebackOrigin.PROJECT && frame.fileName == ENTRY_POINT
        }
        assertTrue(
            "Runtime traceback must include the module call site",
            projectFrames.any { frame -> frame.functionName == "<module>" && frame.lineNumber == 4 },
        )
        assertTrue(
            "Runtime traceback must include the raising function",
            projectFrames.any { frame -> frame.functionName == "explode" && frame.lineNumber == 3 },
        )
    }

    @Test
    fun outputLimitFailsAfterDeliveringOnlyBudgetedChunks() {
        val observation = executeRealSource(
            caseName = "output-limit",
            sourceText = """
                import sys
                sys.stdout.write("0123456789abcdef")
                sys.stderr.write("X")
            """.trimIndent() + "\n",
            maxOutputBytes = OUTPUT_LIMIT_BYTES,
            maxOutputChunkBytes = OUTPUT_LIMIT_CHUNK_BYTES,
            maxOutputChunks = OUTPUT_LIMIT_CHUNKS,
        )

        assertCommonEnvelope(observation, expectedCallbackCount = 5)
        assertNull("Output-limit execution unexpectedly returned a result", observation.result)
        val error = requireNotNull(observation.error) { "Output-limit execution returned no error" }
        assertEquals(PythonErrorCode.OUTPUT_LIMIT_EXCEEDED, error.code)
        assertEquals(PythonFailurePhase.OUTPUT, error.phase)
        assertFalse("Output-limit failure must not be retryable after dispatch", error.retryableBeforeDispatch)
        assertNull("Output-limit failure must not expose an exception type", error.exceptionType)
        assertNull("Output-limit failure must not expose an exception message", error.exceptionMessage)
        assertTrue("Output-limit failure must not expose traceback frames", error.traceback.isEmpty())
        assertArrayEquals(OUTPUT_LIMIT_ACCEPTED_BYTES, observation.stdout)
        assertArrayEquals(byteArrayOf(), observation.stderr)
        assertEquals(OUTPUT_LIMIT_BYTES, observation.policyStdoutBytes)
        assertEquals(0L, observation.policyStderrBytes)
        assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, observation.terminal)
    }

    private fun assertCommonEnvelope(observation: ExecutionObservation, expectedCallbackCount: Int) {
        assertTrue("Source ParcelFileDescriptor remained open", observation.sourceDescriptorClosed)
        assertTrue("At least one result ParcelFileDescriptor remained open", observation.resultDescriptorsClosed)
        assertEquals(
            "Result descriptor count must remain zero in protocol v1.0",
            0,
            observation.resultDescriptorCount,
        )
        assertEquals("Unexpected callback count", expectedCallbackCount, observation.callbackCount)
        assertEquals(expectedCallbackCount, observation.callbackUids.size)
        assertTrue(
            "At least one callback came from an unpinned UID: ${observation.callbackUids}",
            observation.callbackUids.all { uid -> uid == observation.pinnedProviderUid },
        )
        assertArrayEquals(byteArrayOf(), observation.stderr)
    }

    private fun assertNotNullResult(observation: ExecutionObservation): PythonExecutionResult {
        assertNull("Execution unexpectedly failed", observation.error)
        return requireNotNull(observation.result) { "Execution returned no result" }
    }

    private fun assertPythonException(
        observation: ExecutionObservation,
        expectedType: String,
    ): PythonExecutionError {
        assertNull("Python exception unexpectedly returned a result", observation.result)
        assertArrayEquals(byteArrayOf(), observation.stdout)
        val error = requireNotNull(observation.error) { "Python exception returned no error" }
        assertEquals(PythonErrorCode.PYTHON_EXCEPTION, error.code)
        assertEquals(PythonFailurePhase.EXECUTION, error.phase)
        assertFalse("Python exception must not be retryable after dispatch", error.retryableBeforeDispatch)
        assertEquals(expectedType, error.exceptionType)
        assertNotNull("Python exception message is absent", error.exceptionMessage)
        assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, observation.terminal)
        return error
    }

    private fun executeRealSource(
        caseName: String,
        sourceText: String,
        maxOutputBytes: Long = DEFAULT_MAX_OUTPUT_BYTES,
        maxOutputChunkBytes: Int = DEFAULT_MAX_OUTPUT_CHUNK_BYTES,
        maxOutputChunks: Long = DEFAULT_MAX_OUTPUT_CHUNKS,
    ): ExecutionObservation {
        val expectedComponent = PythonRuntimeProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val matches = transport.discover().filter { snapshot -> snapshot.component == expectedComponent }
        assertEquals("Exact-action discovery must return one real Python Runtime service", 1, matches.size)
        val snapshot = matches.single()
        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, snapshot.declaredAction)
        assertTrue("Real Python Runtime service is not exported", snapshot.exported)
        assertTrue("Real Python Runtime application is disabled", snapshot.applicationEnabled)
        assertTrue("Real Python Runtime service is disabled", snapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, snapshot.requiredPermission)
        assertTrue("Real Python Runtime has no signer identity", snapshot.signerSha256.isNotEmpty())
        assertTrue("Real Python Runtime signer differs from the host", snapshot.sameSignerAsHost)
        assertTrue("Real Python Runtime package UID is invalid", snapshot.packageUid >= 0)
        assertEquals(snapshot, transport.inspectExact(expectedComponent))

        val bound = bindProvider(snapshot)
        val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
        val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)
        assertRuntimeIdentity(runtimeInfo, capabilities)
        assertTrue(
            "Real Python Runtime cannot admit the requested output budget",
            maxOutputBytes <= capabilities.limits.maxOutputBytes &&
                maxOutputChunkBytes <= capabilities.limits.maxOutputChunkBytes &&
                maxOutputChunks <= capabilities.limits.maxOutputChunks,
        )
        return executeBoundSource(
            bound = bound,
            snapshot = snapshot,
            runtimeInfo = runtimeInfo,
            capabilities = capabilities,
            caseName = caseName,
            sourceText = sourceText,
            maxOutputBytes = maxOutputBytes,
            maxOutputChunkBytes = maxOutputChunkBytes,
            maxOutputChunks = maxOutputChunks,
        )
    }

    private fun assertRuntimeIdentity(
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
    ) {
        assertEquals(REAL_PROVIDER_ID, runtimeInfo.providerId)
        assertEquals(PythonImplementation.CPYTHON, runtimeInfo.implementation)
        assertEquals(PythonIsolationMode.DEDICATED_PLUGIN_PROCESS, runtimeInfo.isolationMode)
        assertEquals(EXPECTED_PYTHON_VERSION, runtimeInfo.pythonVersion)
        assertEquals(runtimeInfo.pythonVersion, capabilities.pythonVersion)
        assertEquals(PythonImplementation.CPYTHON, capabilities.implementation)
        assertTrue("Real plugin must advertise structured traceback", capabilities.supportsStructuredTraceback)
        val currentAbi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull()) { "Device primary ABI is absent" }
        assertTrue("Real plugin does not advertise device ABI $currentAbi", currentAbi in capabilities.supportedAbis)
    }

    private fun executeBoundSource(
        bound: BoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
        caseName: String,
        sourceText: String,
        maxOutputBytes: Long,
        maxOutputChunkBytes: Int,
        maxOutputChunks: Long,
    ): ExecutionObservation {
        val source = sourceText.toByteArray(Charsets.UTF_8)
        val request = PythonExecutionRequest(
            requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo),
            entryPoint = ENTRY_POINT,
            source = PythonPayloadReference(
                kind = PythonPayloadKind.SOURCE,
                descriptorIndex = 0,
                declaredLengthBytes = source.size.toLong(),
                sha256 = PythonSha256.digest(source),
            ),
            timeoutMillis = SESSION_TIMEOUT_MILLIS,
            maxOutputBytes = maxOutputBytes,
            maxOutputChunkBytes = maxOutputChunkBytes,
            maxOutputChunks = maxOutputChunks,
        )
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
        val maximumCredits = capabilities.limits.maxOutstandingOutputCredits
        val grantedCredits = minOf(maxOutputChunks, maximumCredits.toLong()).toInt()
        assertTrue("Diagnostic requires at least one output credit", grantedCredits > 0)
        val policy = PythonExecutionSessionPolicy(
            expectedRequest = request,
            maximumCredits = maximumCredits,
            expectedPythonVersion = runtimeInfo.pythonVersion,
        )

        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val result = AtomicReference<PythonExecutionResult?>()
        val executionError = AtomicReference<PythonExecutionError?>()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val terminal = CountDownLatch(1)
        val resultDescriptorCount = AtomicInteger(0)
        val resultDescriptorsClosed = AtomicBoolean(true)

        fun failCallback(error: Throwable) {
            callbackFailure.compareAndSet(null, error)
            terminal.countDown()
        }

        fun fromPinnedProvider(action: () -> Unit): Boolean {
            val callingUid = Binder.getCallingUid()
            callbackUids += callingUid
            callbackCount.incrementAndGet()
            if (callingUid != snapshot.packageUid) {
                failCallback(
                    SecurityException(
                        "Real Python Runtime callback UID $callingUid differs from pinned UID ${snapshot.packageUid}",
                    ),
                )
                return false
            }
            return runCatching(action).onFailure(::failCallback).isSuccess
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) {
                fromPinnedProvider {
                    val started = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Real Python Runtime started metadata is null" },
                    )
                    policy.onStarted(started)
                    check(started.runtimeGeneration > 0L)
                }
            }

            override fun onStdout(chunk: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Real Python Runtime stdout chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDOUT)
                    synchronized(stdout) { stdout.write(decoded.toByteArray()) }
                }
            }

            override fun onStderr(chunk: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Real Python Runtime stderr chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDERR)
                    synchronized(stderr) { stderr.write(decoded.toByteArray()) }
                }
            }

            override fun onResult(
                encodedResult: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
            ) {
                val received = descriptors?.filterNotNull().orEmpty()
                val receivedFileDescriptors = received.map { descriptor -> descriptor.fileDescriptor }
                try {
                    fromPinnedProvider {
                        val descriptorArray = requireNotNull(descriptors) {
                            "Real Python Runtime result descriptor array is null"
                        }
                        require(received.size == descriptorArray.size) {
                            "Real Python Runtime result descriptor array contains null"
                        }
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Real Python Runtime result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Real Python Runtime emitted multiple results" }
                    }
                } finally {
                    resultDescriptorCount.addAndGet(received.size)
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                    if (receivedFileDescriptors.any { descriptor -> descriptor.valid() }) {
                        resultDescriptorsClosed.set(false)
                    }
                    if (result.get() != null) terminal.countDown()
                }
            }

            override fun onFailed(error: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Real Python Runtime failure is null" },
                    )
                    policy.onFailed(decoded)
                    check(executionError.compareAndSet(null, decoded)) {
                        "Real Python Runtime emitted multiple failures"
                    }
                    terminal.countDown()
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Real Python Runtime cancellation is null" },
                    )
                    failCallback(AssertionError("Unexpected cancellation: ${decoded.reason}"))
                }
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val accepted = fromPinnedProvider {
                    val safeSession = requireNotNull(session) { "Real Python Runtime opened a null session" }
                    check(openedSession.compareAndSet(null, safeSession)) {
                        "Real Python Runtime opened more than one session"
                    }
                    check(policy.requestStart()) { "Real Python Runtime start was not accepted" }
                    safeSession.start()
                    policy.grantOutputCredits(grantedCredits)
                    safeSession.grantOutputCredits(grantedCredits)
                }
                if (!accepted) session?.let { rejected -> runCatching { rejected.close() } }
            }

            override fun onRejected(error: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Real Python Runtime admission rejection is null" },
                    )
                    failCallback(
                        AssertionError("Real Python Runtime admission rejected: ${decoded.code}/${decoded.phase}"),
                    )
                }
            }
        }

        val sourceFile = File(context.cacheDir, "python-runtime-r2-matrix-$caseName-${UUID.randomUUID()}.py")
        sourceFile.writeBytes(source)
        var sourceDescriptorClosed = false
        try {
            lateinit var sourceFileDescriptor: java.io.FileDescriptor
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                sourceFileDescriptor = descriptor.fileDescriptor
                bound.provider.openSessionForNegotiatedProtocol(
                    request,
                    arrayOf(descriptor),
                    admissionCallback,
                    executionCallback,
                )
            }
            sourceDescriptorClosed = !sourceFileDescriptor.valid()
            assertTrue("Local source ParcelFileDescriptor did not close after dispatch", sourceDescriptorClosed)
            assertTrue(
                "Real CPython execution did not reach a terminal callback",
                bound.awaitTerminalOrThrow(terminal, SESSION_WAIT_MILLIS),
            )
            callbackFailure.get()?.let { error ->
                throw AssertionError("Real CPython callback validation failed").apply { initCause(error) }
            }
            bound.throwIfTransportFailed()
            return ExecutionObservation(
                pinnedProviderUid = snapshot.packageUid,
                result = result.get(),
                error = executionError.get(),
                stdout = synchronized(stdout) { stdout.toByteArray() },
                stderr = synchronized(stderr) { stderr.toByteArray() },
                callbackCount = callbackCount.get(),
                callbackUids = callbackUids.toList(),
                sourceDescriptorClosed = sourceDescriptorClosed,
                resultDescriptorCount = resultDescriptorCount.get(),
                resultDescriptorsClosed = resultDescriptorsClosed.get(),
                terminal = policy.terminal,
                policyStdoutBytes = policy.stdoutBytes,
                policyStderrBytes = policy.stderrBytes,
            )
        } finally {
            // A real session retires its worker on close. Unlink first so expected retirement is
            // not reported as a transport failure after the terminal callback was validated.
            bound.binding.close()
            openedSession.get()?.let { session -> runCatching { session.close() } }
            assertTrue(
                "Unable to remove the real Python Runtime source snapshot",
                !sourceFile.exists() || sourceFile.delete(),
            )
        }
    }

    private fun bindProvider(snapshot: AndroidPythonRuntimeProviderIdentitySnapshot): BoundProvider {
        val providerReference = AtomicReference<IPythonRuntimeProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
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
                }

                override fun onBinderDeath() {
                    failure.compareAndSet(null, IllegalStateException("Real Python Runtime Binder died while binding"))
                    connected.countDown()
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
        return BoundProvider(
            provider = checkNotNull(providerReference.get()) { "Real Python Runtime binding completed without provider" },
            binding = binding,
            transportFailure = failure,
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private data class ExecutionObservation(
        val pinnedProviderUid: Int,
        val result: PythonExecutionResult?,
        val error: PythonExecutionError?,
        val stdout: ByteArray,
        val stderr: ByteArray,
        val callbackCount: Int,
        val callbackUids: List<Int>,
        val sourceDescriptorClosed: Boolean,
        val resultDescriptorCount: Int,
        val resultDescriptorsClosed: Boolean,
        val terminal: PythonExecutionSessionPolicy.Terminal?,
        val policyStdoutBytes: Long,
        val policyStderrBytes: Long,
    )

    private class BoundProvider(
        val provider: IPythonRuntimeProvider,
        val binding: AndroidPythonRuntimeBinding,
        private val transportFailure: AtomicReference<Throwable?>,
    ) : Closeable {
        fun awaitTerminalOrThrow(terminal: CountDownLatch, timeoutMillis: Long): Boolean {
            val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                throwIfTransportFailed()
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) return false
                val waitNanos = minOf(
                    remainingNanos,
                    TimeUnit.MILLISECONDS.toNanos(TRANSPORT_FAILURE_POLL_MILLIS),
                )
                if (terminal.await(waitNanos, TimeUnit.NANOSECONDS)) {
                    throwIfTransportFailed()
                    return true
                }
            }
        }

        fun throwIfTransportFailed() {
            transportFailure.get()?.let { error ->
                throw AssertionError("Real Python Runtime transport failed after binding").apply { initCause(error) }
            }
        }

        override fun close() = binding.close()
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r2.execution.matrix.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE =
            "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val ENTRY_POINT = "main.py"

        const val BIND_WAIT_MILLIS = 10_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val SESSION_WAIT_MILLIS = 45_000L
        const val TRANSPORT_FAILURE_POLL_MILLIS = 100L
        const val DEFAULT_MAX_OUTPUT_BYTES = 4_096L
        const val DEFAULT_MAX_OUTPUT_CHUNK_BYTES = 1_024
        const val DEFAULT_MAX_OUTPUT_CHUNKS = 16L

        const val OUTPUT_LIMIT_BYTES = 16L
        const val OUTPUT_LIMIT_CHUNK_BYTES = 8
        const val OUTPUT_LIMIT_CHUNKS = 16L
        val OUTPUT_LIMIT_ACCEPTED_BYTES = "0123456789abcdef".toByteArray(Charsets.UTF_8)
    }
}
