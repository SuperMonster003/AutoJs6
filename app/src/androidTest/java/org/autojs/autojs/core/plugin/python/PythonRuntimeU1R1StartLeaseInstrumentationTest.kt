package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonExecutionError
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * U1-R1 exact-provider proof for the CREATED-session start lease.
 *
 * The first session deliberately never calls start. Its reliable-pipe reader must remain owned
 * while the session is CREATED, then close after the bounded lease without running Python or
 * retiring the provider process. A second request is dispatched over the same bound provider and
 * must complete exactly once, which also proves that lease cleanup released the single-session
 * gate. Package installation and restoration remain the responsibility of the exact-device runner.
 */
class PythonRuntimeU1R1StartLeaseInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Test
    fun openedButNeverStartedSessionExpiresAndNextExecutionSucceeds() {
        assertEquals(
            "U1-R1 start-lease instrumentation requires an explicit opt-in argument",
            "true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED),
        )
        assertTrue("U1-R1 start-lease instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Start-lease instrumentation requires the canonical Host", HOST_PACKAGE, context.packageName)

        val expectedComponent = PythonRuntimeProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val matches = transport.discover().filter { snapshot -> snapshot.component == expectedComponent }
        assertEquals("Exact-action discovery must return one Python Runtime provider", 1, matches.size)
        val snapshot = matches.single()
        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, snapshot.declaredAction)
        assertTrue("Python Runtime service is not exported", snapshot.exported)
        assertTrue("Python Runtime application is disabled", snapshot.applicationEnabled)
        assertTrue("Python Runtime service is disabled", snapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, snapshot.requiredPermission)
        assertTrue("Python Runtime signer identity is absent", snapshot.signerSha256.isNotEmpty())
        assertTrue("Python Runtime signer differs from the Host", snapshot.sameSignerAsHost)
        assertEquals(
            "Host and Python Runtime must share the signature-permission signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        assertTrue("Python Runtime package UID is invalid", snapshot.packageUid >= 0)
        assertEquals(snapshot, transport.inspectExact(expectedComponent))

        val bound = bindProvider(snapshot)
        var expiredSession: IPythonExecutionSession? = null
        var recoverySession: IPythonExecutionSession? = null
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val sourceReader = pipe[0]
        val sourceOutput = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try {
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)
            assertRuntimeIdentity(runtimeInfo, capabilities)

            val neverRunSource = NEVER_RUN_SOURCE.toByteArray(Charsets.UTF_8)
            val neverRunRequest = newRequest(
                source = neverRunSource,
                runtimeInfo = runtimeInfo,
                entryPoint = NEVER_RUN_ENTRY_POINT,
            )
            PythonRuntimeValidation.validateRequestAgainst(
                request = neverRunRequest,
                capabilities = capabilities,
                negotiatedVersion = neverRunRequest.protocolVersion,
                descriptorCount = 1,
            )

            val admissionDone = CountDownLatch(1)
            val openedSession = AtomicReference<IPythonExecutionSession?>()
            val admissionFailure = AtomicReference<Throwable?>()
            val executionFailure = AtomicReference<Throwable?>()
            val executionCallbacks = AtomicInteger(0)
            val callbackUids = ConcurrentLinkedQueue<Int>()

            fun acceptProviderCallback(action: () -> Unit) {
                val callingUid = Binder.getCallingUid()
                callbackUids += callingUid
                if (callingUid != snapshot.packageUid) {
                    admissionFailure.compareAndSet(
                        null,
                        SecurityException(
                            "Start-lease callback UID $callingUid differs from pinned UID ${snapshot.packageUid}",
                        ),
                    )
                    admissionDone.countDown()
                    return
                }
                runCatching(action).onFailure { error ->
                    admissionFailure.compareAndSet(null, error)
                    admissionDone.countDown()
                }
            }

            fun unexpectedExecutionCallback(label: String) {
                executionCallbacks.incrementAndGet()
                executionFailure.compareAndSet(
                    null,
                    AssertionError("Never-started session unexpectedly emitted $label"),
                )
            }

            val executionCallback = object : NonInteractivePythonExecutionCallback() {
                override fun onStarted(metadata: ByteArray?) = acceptProviderCallback {
                    unexpectedExecutionCallback("started metadata")
                }

                override fun onStdout(chunk: ByteArray?) = acceptProviderCallback {
                    unexpectedExecutionCallback("stdout")
                }

                override fun onStderr(chunk: ByteArray?) = acceptProviderCallback {
                    unexpectedExecutionCallback("stderr")
                }

                override fun onResult(
                    result: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    val received = descriptors?.filterNotNull().orEmpty()
                    try {
                        acceptProviderCallback { unexpectedExecutionCallback("result") }
                    } finally {
                        received.forEach { descriptor -> runCatching { descriptor.close() } }
                    }
                }

                override fun onFailed(error: ByteArray?) = acceptProviderCallback {
                    val detail = runCatching {
                        PythonRuntimeCodec.decodeExecutionError(requireNotNull(error)).let { decoded ->
                            "${decoded.code}/${decoded.phase}"
                        }
                    }.getOrElse { "malformed failure" }
                    unexpectedExecutionCallback("failure $detail")
                }

                override fun onCancelled(cancellation: ByteArray?) = acceptProviderCallback {
                    unexpectedExecutionCallback("cancellation")
                }
            }
            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
                override fun onOpened(session: IPythonExecutionSession?) = acceptProviderCallback {
                    check(openedSession.compareAndSet(null, requireNotNull(session))) {
                        "Provider opened more than one never-started session"
                    }
                    admissionDone.countDown()
                }

                override fun onRejected(error: ByteArray?) = acceptProviderCallback {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Start-lease admission rejection is null" },
                    )
                    admissionFailure.compareAndSet(
                        null,
                        AssertionError("Provider rejected start-lease probe: ${decoded.code}/${decoded.phase}"),
                    )
                    admissionDone.countDown()
                }
            }

            sourceOutput.write(neverRunSource)
            sourceOutput.flush()
            try {
                bound.provider.openSessionForNegotiatedProtocol(
                    neverRunRequest,
                    arrayOf(sourceReader),
                    admissionCallback,
                    executionCallback,
                )
            } finally {
                sourceReader.close()
            }
            assertTrue(
                "Timed out waiting for the never-started session admission",
                admissionDone.await(OPEN_WAIT_SECONDS, TimeUnit.SECONDS),
            )
            admissionFailure.get()?.let { error ->
                throw AssertionError("Never-started session admission failed", error)
            }
            expiredSession = requireNotNull(openedSession.get()) {
                "Provider admitted the never-started request without a session"
            }
            assertTrue("Exact provider Binder died before start-lease expiry", bound.provider.asBinder().isBinderAlive)

            // The receiver must still own its pipe while the admitted session is CREATED.
            sourceOutput.write(PRE_EXPIRY_PROBE)
            sourceOutput.flush()
            assertFalse(
                "Start-lease settle sentinel unexpectedly completed",
                CountDownLatch(1).await(START_LEASE_SETTLE_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertTrue(
                "Provider did not close its never-started SOURCE PFD after start-lease expiry",
                awaitReliablePipeReaderClosure(sourceOutput),
            )
            assertEquals("Never-started session emitted execution callbacks", 0, executionCallbacks.get())
            assertNull("Never-started session callback failed", executionFailure.get())
            assertTrue(
                "Never-started callback came from an unpinned UID: $callbackUids",
                callbackUids.isNotEmpty() && callbackUids.all { uid -> uid == snapshot.packageUid },
            )
            assertTrue("Start-lease expiry unexpectedly retired the provider", bound.provider.asBinder().isBinderAlive)
            bound.throwIfTransportFailed()

            val recovery = executeFiniteSource(
                bound = bound,
                snapshot = snapshot,
                runtimeInfo = runtimeInfo,
                capabilities = capabilities,
            )
            recoverySession = recovery.session
            assertArrayEquals(EXPECTED_RECOVERY_OUTPUT, recovery.stdout)
            assertArrayEquals(byteArrayOf(), recovery.stderr)
            assertEquals(1, recovery.startedCount)
            assertEquals(1, recovery.resultCount)
            assertEquals(1, recovery.terminalCount)
            assertEquals(0, recovery.failedCount)
            assertEquals(0, recovery.cancelledCount)
            assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, recovery.policy.terminal)
            assertEquals(0, recovery.result.exitCode)
            assertEquals(EXPECTED_RECOVERY_OUTPUT.size.toLong(), recovery.result.stdoutBytes)
            assertEquals(0L, recovery.result.stderrBytes)
            assertEquals(recovery.stdoutCallbackCount.toLong(), recovery.result.stdoutChunks)
            assertEquals(0L, recovery.result.stderrChunks)
            assertTrue("Recovered execution has no runtime generation", recovery.runtimeGeneration > 0L)
            assertTrue(
                "Recovered callback came from an unpinned UID: ${recovery.callbackUids}",
                recovery.callbackUids.isNotEmpty() &&
                    recovery.callbackUids.all { uid -> uid == snapshot.packageUid },
            )
            assertEquals(
                "Never-started session emitted a late execution callback during recovery",
                0,
                executionCallbacks.get(),
            )
            assertNull("Never-started session emitted a late callback failure", executionFailure.get())
            bound.throwIfTransportFailed()
        } finally {
            // Closing the binding first prevents the expected close-time runtime retirement from
            // being misclassified as an unexpected transport death.
            bound.binding.close()
            runCatching { recoverySession?.close() }
            runCatching { expiredSession?.close() }
            runCatching { sourceOutput.close() }
            runCatching { sourceReader.close() }
        }
    }

    private fun assertRuntimeIdentity(
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
    ) {
        assertEquals(REAL_PROVIDER_ID, runtimeInfo.providerId)
        assertEquals(PythonImplementation.CPYTHON, runtimeInfo.implementation)
        assertEquals(PythonIsolationMode.DEDICATED_PLUGIN_PROCESS, runtimeInfo.isolationMode)
        assertEquals(EXPECTED_PYTHON_VERSION, runtimeInfo.pythonVersion)
        assertEquals(EXPECTED_PYTHON_VERSION, capabilities.pythonVersion)
        assertEquals(PythonImplementation.CPYTHON, capabilities.implementation)
        assertTrue("Python Runtime must advertise stdin snapshots", capabilities.supportsStdinSnapshot)
        assertTrue("Python Runtime stdin limit must be positive", capabilities.limits.maxStdinBytes > 0L)
        assertTrue(
            "Python Runtime minimum Host version exceeds this Host",
            requireNotNull(runtimeInfo.minHostVersionCode) <= BuildConfig.VERSION_CODE.toLong(),
        )
        val currentAbi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull()) { "Device primary ABI is absent" }
        assertTrue("Python Runtime does not advertise device ABI $currentAbi", currentAbi in capabilities.supportedAbis)
    }

    private fun newRequest(
        source: ByteArray,
        runtimeInfo: PythonRuntimeInfo,
        entryPoint: String,
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

    private fun awaitReliablePipeReaderClosure(
        output: ParcelFileDescriptor.AutoCloseOutputStream,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + PFD_CLOSE_WAIT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                output.write(POST_EXPIRY_PROBE)
                output.flush()
            } catch (_: IOException) {
                return true
            }
            SystemClock.sleep(PFD_CLOSE_POLL_MILLIS)
        }
        return false
    }

    private fun executeFiniteSource(
        bound: BoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        runtimeInfo: PythonRuntimeInfo,
        capabilities: PythonRuntimeCapabilities,
    ): RecoveryObservation {
        val source = RECOVERY_SOURCE.toByteArray(Charsets.UTF_8)
        val request = newRequest(source, runtimeInfo, RECOVERY_ENTRY_POINT)
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
        val grantedCredits = minOf(MAX_OUTPUT_CHUNKS.toInt(), capabilities.limits.maxOutstandingOutputCredits)
        assertTrue("Recovery requires at least two output credits", grantedCredits >= 2)
        val policy = PythonExecutionSessionPolicy(request, capabilities.limits.maxOutstandingOutputCredits, runtimeInfo.pythonVersion)
        policy.grantOutputCredits(grantedCredits)

        val terminal = CountDownLatch(1)
        val callbackFailure = AtomicReference<Throwable?>()
        val admissionError = AtomicReference<PythonExecutionError?>()
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val result = AtomicReference<PythonExecutionResult?>()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val startedCount = AtomicInteger(0)
        val stdoutCallbackCount = AtomicInteger(0)
        val resultCount = AtomicInteger(0)
        val terminalCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)
        val runtimeGeneration = AtomicLong(0L)

        fun failCallback(error: Throwable) {
            callbackFailure.compareAndSet(null, error)
            terminal.countDown()
        }

        fun acceptProviderCallback(action: () -> Unit) {
            val callingUid = Binder.getCallingUid()
            callbackUids += callingUid
            if (callingUid != snapshot.packageUid) {
                failCallback(
                    SecurityException("Recovery callback UID $callingUid differs from pinned UID ${snapshot.packageUid}"),
                )
                return
            }
            runCatching(action).onFailure(::failCallback)
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) = acceptProviderCallback {
                check(startedCount.incrementAndGet() == 1) { "Recovery session started more than once" }
                val decoded = PythonRuntimeCodec.decodeSessionStarted(
                    requireNotNull(metadata) { "Recovery started metadata is null" },
                )
                policy.onStarted(decoded)
                check(runtimeGeneration.compareAndSet(0L, decoded.runtimeGeneration)) {
                    "Recovery runtime generation was assigned more than once"
                }
            }

            override fun onStdout(chunk: ByteArray?) = acceptProviderCallback {
                val decoded = PythonRuntimeCodec.decodeOutputChunk(
                    requireNotNull(chunk) { "Recovery stdout chunk is null" },
                )
                policy.onOutput(decoded, PythonOutputStream.STDOUT)
                stdoutCallbackCount.incrementAndGet()
                synchronized(stdout) { stdout.write(decoded.toByteArray()) }
            }

            override fun onStderr(chunk: ByteArray?) = acceptProviderCallback {
                val decoded = PythonRuntimeCodec.decodeOutputChunk(
                    requireNotNull(chunk) { "Recovery stderr chunk is null" },
                )
                policy.onOutput(decoded, PythonOutputStream.STDERR)
                synchronized(stderr) { stderr.write(decoded.toByteArray()) }
            }

            override fun onResult(
                encodedResult: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
            ) {
                val received = descriptors?.filterNotNull().orEmpty()
                try {
                    acceptProviderCallback {
                        val descriptorArray = requireNotNull(descriptors) { "Recovery result descriptors are null" }
                        require(received.size == descriptorArray.size) {
                            "Recovery result descriptor array contains null"
                        }
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Recovery result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Recovery returned more than one result" }
                        resultCount.incrementAndGet()
                        terminalCount.incrementAndGet()
                        terminal.countDown()
                    }
                } finally {
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(error: ByteArray?) = acceptProviderCallback {
                failedCount.incrementAndGet()
                val decoded = PythonRuntimeCodec.decodeExecutionError(
                    requireNotNull(error) { "Recovery failure is null" },
                )
                policy.onFailed(decoded)
                callbackFailure.compareAndSet(
                    null,
                    AssertionError("Recovery execution failed: ${decoded.code}/${decoded.phase}"),
                )
                terminalCount.incrementAndGet()
                terminal.countDown()
            }

            override fun onCancelled(cancellation: ByteArray?) = acceptProviderCallback {
                cancelledCount.incrementAndGet()
                callbackFailure.compareAndSet(null, AssertionError("Recovery execution was cancelled"))
                terminalCount.incrementAndGet()
                terminal.countDown()
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) = acceptProviderCallback {
                val safeSession = requireNotNull(session) { "Recovery opened a null session" }
                check(openedSession.compareAndSet(null, safeSession)) { "Recovery opened more than one session" }
                safeSession.grantOutputCredits(grantedCredits)
                check(policy.requestStart()) { "Recovery start dispatch was not accepted" }
                safeSession.start()
            }

            override fun onRejected(error: ByteArray?) = acceptProviderCallback {
                val decoded = PythonRuntimeCodec.decodeExecutionError(
                    requireNotNull(error) { "Recovery admission rejection is null" },
                )
                admissionError.compareAndSet(null, decoded)
                terminal.countDown()
            }
        }

        val sourceFile = File(context.cacheDir, "python-u1-r1-start-lease-${UUID.randomUUID()}.py")
        var completed = false
        try {
            sourceFile.writeBytes(source)
            val descriptor = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val localFileDescriptor = descriptor.fileDescriptor
            descriptor.use {
                bound.provider.openSessionForNegotiatedProtocol(
                    request,
                    arrayOf(it),
                    admissionCallback,
                    executionCallback,
                )
            }
            assertFalse("Recovery caller-owned SOURCE PFD remained open", localFileDescriptor.valid())
            assertTrue(
                "Recovery execution did not reach a terminal callback",
                terminal.await(SESSION_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertFalse(
                "Recovery callback settle sentinel unexpectedly completed",
                CountDownLatch(1).await(CALLBACK_SETTLE_MILLIS, TimeUnit.MILLISECONDS),
            )
            callbackFailure.get()?.let { error ->
                throw AssertionError("Recovery callback validation failed", error)
            }
            admissionError.get()?.let { error ->
                throw AssertionError("Recovery admission was rejected: ${error.code}/${error.phase}")
            }
            bound.throwIfTransportFailed()
            val completedResult = requireNotNull(result.get()) { "Recovery returned no result" }
            val session = requireNotNull(openedSession.get()) { "Recovery returned no session" }
            completed = true
            return RecoveryObservation(
                session = session,
                result = completedResult,
                stdout = synchronized(stdout) { stdout.toByteArray() },
                stderr = synchronized(stderr) { stderr.toByteArray() },
                callbackUids = callbackUids.toList(),
                startedCount = startedCount.get(),
                stdoutCallbackCount = stdoutCallbackCount.get(),
                resultCount = resultCount.get(),
                terminalCount = terminalCount.get(),
                failedCount = failedCount.get(),
                cancelledCount = cancelledCount.get(),
                runtimeGeneration = runtimeGeneration.get(),
                policy = policy,
            )
        } finally {
            if (!completed) runCatching { openedSession.get()?.close() }
            check(!sourceFile.exists() || sourceFile.delete()) {
                "Unable to remove recovery source: $sourceFile"
            }
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
                        failure.compareAndSet(null, SecurityException("Provider connected more than once"))
                    }
                    connected.countDown()
                }

                override fun onBindingFailure(cause: Throwable?) {
                    failure.compareAndSet(
                        null,
                        cause ?: IllegalStateException("Provider disconnected while binding"),
                    )
                    connected.countDown()
                }

                override fun onBinderDeath() {
                    failure.compareAndSet(null, IllegalStateException("Provider Binder died unexpectedly"))
                    connected.countDown()
                }
            },
        )
        if (!connected.await(BIND_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            binding.close()
            throw AssertionError("Timed out binding the exact Python Runtime provider")
        }
        failure.get()?.let { error ->
            binding.close()
            throw AssertionError("Unable to bind the exact Python Runtime provider", error)
        }
        return BoundProvider(
            provider = requireNotNull(providerReference.get()) { "Binding completed without a provider" },
            binding = binding,
            failure = failure,
        )
    }

    private class BoundProvider(
        val provider: IPythonRuntimeProvider,
        val binding: AndroidPythonRuntimeBinding,
        private val failure: AtomicReference<Throwable?>,
    ) {
        fun throwIfTransportFailed() {
            failure.get()?.let { error ->
                throw AssertionError("Python Runtime transport failed", error)
            }
        }
    }

    private data class RecoveryObservation(
        val session: IPythonExecutionSession,
        val result: PythonExecutionResult,
        val stdout: ByteArray,
        val stderr: ByteArray,
        val callbackUids: List<Int>,
        val startedCount: Int,
        val stdoutCallbackCount: Int,
        val resultCount: Int,
        val terminalCount: Int,
        val failedCount: Int,
        val cancelledCount: Int,
        val runtimeGeneration: Long,
        val policy: PythonExecutionSessionPolicy,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.python.u1r1.startLease.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE = "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"

        const val NEVER_RUN_ENTRY_POINT = "start_lease_must_not_run.py"
        const val NEVER_RUN_SOURCE = "raise AssertionError('never-started source executed')\n"
        const val RECOVERY_ENTRY_POINT = "start_lease_recovery.py"
        const val RECOVERY_TOKEN = "U1_R1_START_LEASE_RECOVERY"
        const val RECOVERY_SOURCE = "print('$RECOVERY_TOKEN', flush=True)\n"
        val EXPECTED_RECOVERY_OUTPUT = "$RECOVERY_TOKEN\n".toByteArray(Charsets.UTF_8)

        const val BIND_WAIT_MILLIS = 10_000L
        const val OPEN_WAIT_SECONDS = 10L
        const val START_LEASE_SETTLE_MILLIS = 6_500L
        const val PFD_CLOSE_WAIT_MILLIS = 5_000L
        const val PFD_CLOSE_POLL_MILLIS = 100L
        const val CALLBACK_SETTLE_MILLIS = 250L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val SESSION_WAIT_MILLIS = 45_000L
        const val MAX_OUTPUT_BYTES = 4_096L
        const val MAX_OUTPUT_CHUNK_BYTES = 256
        const val MAX_OUTPUT_CHUNKS = 8L
        val PRE_EXPIRY_PROBE = byteArrayOf('#'.code.toByte())
        val POST_EXPIRY_PROBE = byteArrayOf('!'.code.toByte())
    }
}
