package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
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
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeNegotiation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in Android proof for the debug-only Python Runtime R1 transport and fake provider.
 *
 * The fixture APK must be installed with the same debug/test signer as the host. Ordinary
 * connected tests skip this class unless [ARG_ENABLED] is explicitly set to true. This slice
 * covers positive identity/execution and reliable-pipe producer failure. Hostile callers,
 * process death, cancellation, timeout, and identity replacement remain separate required
 * device cases.
 */
class PythonRuntimeAndroidConformanceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireOptInFakeProvider() {
        assumeTrue(
            "Python Runtime Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("Python Runtime conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("The fake provider only admits the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Python Runtime fake package is not installed", isPackageInstalled(FAKE_PACKAGE))
        assertEquals(
            "Python Runtime fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    @Test
    fun exactActionDiscoveryBindsWithNullActionAndReturnsValidatedMetadata() {
        val snapshot = discoverFakeProvider()

        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, snapshot.declaredAction)
        assertTrue("Python Runtime fake service is not exported", snapshot.exported)
        assertTrue("Python Runtime fake application is disabled", snapshot.applicationEnabled)
        assertTrue("Python Runtime fake service is disabled", snapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, snapshot.requiredPermission)
        assertTrue("Python Runtime fake has no signer identity", snapshot.signerSha256.isNotEmpty())
        assertTrue("Python Runtime fake signer differs from the host", snapshot.sameSignerAsHost)
        assertTrue("Python Runtime fake package UID is invalid", snapshot.packageUid >= 0)
        assertTrue("Python Runtime fake version is invalid", snapshot.versionCode > 0L)
        assertTrue("Python Runtime fake update time is invalid", snapshot.lastUpdateTime > 0L)
        assertEquals(snapshot, transport.inspectExact(snapshot.component))

        bindProvider(snapshot).use { bound ->
            assertNull(
                "The explicit Python Runtime bind reused the discovery action",
                bound.binding.intentAction,
            )
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)

            assertEquals(FAKE_PROVIDER_ID, runtimeInfo.providerId)
            assertEquals(FAKE_PYTHON_VERSION, runtimeInfo.pythonVersion)
            assertEquals(runtimeInfo.pythonVersion, capabilities.pythonVersion)
            assertEquals(1, capabilities.limits.maxConcurrentSessions)
            val currentAbi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull()) {
                "The device did not report a primary ABI"
            }
            assertTrue("Python Runtime fake does not advertise the current ABI", currentAbi in capabilities.supportedAbis)

            val environment = PythonRuntimeProviderEnvironment(
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentAbi = currentAbi,
                releaseBuild = false,
            )
            val selected = PythonRuntimeProviderSelectionPolicy.select(
                candidates = listOf(snapshot.toCandidate(runtimeInfo, capabilities)),
                environment = environment,
                options = PythonRuntimeProviderSelectionOptions(
                    enabled = true,
                    preferredComponent = snapshot.component,
                ),
            )
            assertTrue(
                "Validated Python Runtime metadata did not produce the exact pinned provider",
                selected is PythonRuntimeProviderSelectionResult.Selected &&
                    selected.pinned.component == snapshot.component &&
                    selected.pinned.packageUid == snapshot.packageUid,
            )
            val pinned = (selected as PythonRuntimeProviderSelectionResult.Selected).pinned
            val reinspection = transport.inspectExact(snapshot.component)
            assertEquals("Python Runtime package identity changed after metadata negotiation", snapshot, reinspection)
            assertTrue(
                "Python Runtime pinned identity did not survive dispatch-time revalidation",
                PythonRuntimeProviderSelectionPolicy.revalidate(
                    pinned = pinned,
                    current = requireNotNull(reinspection).toCandidate(runtimeInfo, capabilities),
                    environment = environment,
                ),
            )
            bound.throwIfTransportFailed()
        }
    }

    @Test
    fun realPfdNormalSessionEnforcesCreditsOutputResultAndCallbackUid() {
        val snapshot = discoverFakeProvider()
        bindProvider(snapshot).use { bound ->
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)

            val source = "print('python-runtime-r1')\n".toByteArray(Charsets.UTF_8)
            val request = PythonExecutionRequest(
                requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
                protocolVersion = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo),
                entryPoint = "main.py",
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
            PythonRuntimeValidation.validateRequestAgainst(
                request = request,
                capabilities = capabilities,
                negotiatedVersion = request.protocolVersion,
                descriptorCount = 1,
            )
            val policy = PythonExecutionSessionPolicy(
                expectedRequest = request,
                maximumCredits = capabilities.limits.maxOutstandingOutputCredits,
                expectedPythonVersion = runtimeInfo.pythonVersion,
            )

            val callbackUids = ConcurrentLinkedQueue<Int>()
            val callbackFailure = AtomicReference<Throwable?>()
            val openedSession = AtomicReference<IPythonExecutionSession?>()
            val stdout = AtomicReference<ByteArray?>()
            val result = AtomicReference<PythonExecutionResult?>()
            val terminal = CountDownLatch(1)

            fun failCallback(error: Throwable) {
                callbackFailure.compareAndSet(null, error)
                terminal.countDown()
            }

            fun acceptFromPinnedProvider(action: () -> Unit): Boolean {
                val callingUid = Binder.getCallingUid()
                callbackUids += callingUid
                if (callingUid != snapshot.packageUid) {
                    failCallback(
                        SecurityException(
                            "Python Runtime callback UID $callingUid differs from pinned UID ${snapshot.packageUid}",
                        ),
                    )
                    return false
                }
                return runCatching(action)
                    .onFailure(::failCallback)
                    .isSuccess
            }

            fun fromPinnedProvider(action: () -> Unit) {
                acceptFromPinnedProvider(action)
            }

            val executionCallback = object : NonInteractivePythonExecutionCallback() {
                override fun onStarted(metadata: ByteArray?) = fromPinnedProvider {
                    policy.onStarted(
                        PythonRuntimeCodec.decodeSessionStarted(
                            requireNotNull(metadata) { "Python Runtime started metadata is null" },
                        ),
                    )
                }

                override fun onStdout(chunk: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Python Runtime stdout chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDOUT)
                    check(stdout.compareAndSet(null, decoded.toByteArray())) {
                        "Normal Python Runtime session emitted more than one stdout chunk"
                    }
                }

                override fun onStderr(chunk: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Python Runtime stderr chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDERR)
                    error("Normal Python Runtime session unexpectedly emitted stderr")
                }

                override fun onResult(
                    encodedResult: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    val receivedDescriptors = descriptors?.filterNotNull().orEmpty()
                    try {
                        fromPinnedProvider {
                            val descriptorArray = requireNotNull(descriptors) {
                                "Python Runtime result descriptor array is null"
                            }
                            require(receivedDescriptors.size == descriptorArray.size) {
                                "Python Runtime result descriptor array contains a null element"
                            }
                            val decoded = PythonRuntimeCodec.decodeExecutionResult(
                                requireNotNull(encodedResult) { "Python Runtime result is null" },
                            )
                            policy.onResult(decoded, descriptorArray.size)
                            check(result.compareAndSet(null, decoded)) {
                                "Normal Python Runtime session emitted more than one result"
                            }
                            terminal.countDown()
                        }
                    } finally {
                        receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                    }
                }

                override fun onFailed(error: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Python Runtime failure is null" },
                    )
                    policy.onFailed(decoded)
                    failCallback(AssertionError("Normal Python Runtime session failed: ${decoded.message}"))
                }

                override fun onCancelled(cancellation: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Python Runtime cancellation is null" },
                    )
                    policy.onCancelled(decoded)
                    failCallback(AssertionError("Normal Python Runtime session was cancelled"))
                }
            }
            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
                override fun onOpened(session: IPythonExecutionSession?) {
                    val accepted = acceptFromPinnedProvider {
                        val safeSession = requireNotNull(session) { "Python Runtime opened a null session" }
                        check(openedSession.compareAndSet(null, safeSession)) {
                            "Python Runtime opened more than one session"
                        }
                        check(policy.requestStart()) { "Python Runtime session start was not accepted" }
                        safeSession.start()
                        policy.grantOutputCredits(NORMAL_OUTPUT_CREDITS)
                        safeSession.grantOutputCredits(NORMAL_OUTPUT_CREDITS)
                    }
                    if (!accepted) {
                        session?.let { rejectedSession -> runCatching { rejectedSession.close() } }
                    }
                }

                override fun onRejected(error: ByteArray?) = fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(error) { "Python Runtime admission rejection is null" },
                    )
                    failCallback(AssertionError("Normal Python Runtime admission was rejected: ${decoded.message}"))
                }
            }

            val sourceFile = File(context.cacheDir, "python-runtime-r1-${UUID.randomUUID()}.py")
            sourceFile.writeBytes(source)
            try {
                ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    bound.provider.openSession(
                        PythonRuntimeCodec.encodeExecutionRequest(request),
                        arrayOf(descriptor),
                        admissionCallback,
                        executionCallback,
                    )
                }
                val reachedTerminal = bound.awaitTerminalOrThrow(terminal, SESSION_WAIT_MILLIS)
                assertTrue("Normal Python Runtime session did not reach a terminal callback", reachedTerminal)
                callbackFailure.get()?.let { error ->
                    throw AssertionError("Python Runtime callback validation failed").apply { initCause(error) }
                }

                val acceptedResult = result.get()
                assertNotNull("Normal Python Runtime session did not return a result", acceptedResult)
                requireNotNull(acceptedResult)
                assertEquals(0, acceptedResult.exitCode)
                assertEquals(NORMAL_STDOUT.size.toLong(), acceptedResult.stdoutBytes)
                assertEquals(0L, acceptedResult.stderrBytes)
                assertEquals(1L, acceptedResult.stdoutChunks)
                assertEquals(0L, acceptedResult.stderrChunks)
                assertArrayEquals(
                    NORMAL_STDOUT,
                    requireNotNull(stdout.get()) { "Normal Python Runtime session emitted no stdout" },
                )
                assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, policy.state)
                assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, policy.terminal)
                assertEquals(
                    "Normal session must receive opened, started, stdout, and result from the pinned UID",
                    EXPECTED_NORMAL_CALLBACK_COUNT,
                    callbackUids.size,
                )
                assertTrue(
                    "At least one Python Runtime callback came from an unpinned UID: $callbackUids",
                    callbackUids.all { uid -> uid == snapshot.packageUid },
                )
                bound.throwIfTransportFailed()
            } finally {
                openedSession.get()?.let { session -> runCatching { session.close() } }
                assertTrue(
                    "Unable to remove the Python Runtime conformance source snapshot",
                    !sourceFile.exists() || sourceFile.delete(),
                )
            }
        }
    }

    @Test
    fun reliablePipeProducerErrorRejectsByteExactSourceBeforeExecution() {
        val expectedComponent = PythonRuntimeProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val snapshot = discoverFakeProvider()
        assertEquals("Reliable-pipe proof must pin the exact fake component", expectedComponent, snapshot.component)
        assertEquals(snapshot, transport.inspectExact(expectedComponent))

        bindProvider(snapshot).use { bound ->
            assertNull(
                "Reliable-pipe proof reused the discovery action for its explicit bind",
                bound.binding.intentAction,
            )
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)

            val source = "print('must-not-run-after-producer-error')\n".toByteArray(Charsets.UTF_8)
            val request = PythonExecutionRequest(
                requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
                protocolVersion = PythonRuntimeNegotiation.requireNegotiated(runtimeInfo),
                entryPoint = "main.py",
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
            PythonRuntimeValidation.validateRequestAgainst(
                request = request,
                capabilities = capabilities,
                negotiatedVersion = request.protocolVersion,
                descriptorCount = 1,
            )
            val policy = PythonExecutionSessionPolicy(
                expectedRequest = request,
                maximumCredits = capabilities.limits.maxOutstandingOutputCredits,
                expectedPythonVersion = runtimeInfo.pythonVersion,
            )

            val callbackUids = ConcurrentLinkedQueue<Int>()
            val callbackFailure = AtomicReference<Throwable?>()
            val openedSession = AtomicReference<IPythonExecutionSession?>()
            val executionError = AtomicReference<PythonExecutionError?>()
            val terminal = CountDownLatch(1)

            fun failCallback(error: Throwable) {
                callbackFailure.compareAndSet(null, error)
                terminal.countDown()
            }

            fun acceptFromPinnedProvider(callingUid: Int, action: () -> Unit): Boolean {
                callbackUids += callingUid
                if (callingUid != snapshot.packageUid) {
                    failCallback(
                        SecurityException(
                            "Python Runtime callback UID $callingUid differs from pinned UID ${snapshot.packageUid}",
                        ),
                    )
                    return false
                }
                return runCatching(action)
                    .onFailure(::failCallback)
                    .isSuccess
            }

            fun rejectUnexpectedCallback(callingUid: Int, label: String) {
                acceptFromPinnedProvider(callingUid) {
                    error("Reliable-pipe failure session unexpectedly emitted $label")
                }
            }

            val executionCallback = object : NonInteractivePythonExecutionCallback() {
                override fun onStarted(metadata: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    rejectUnexpectedCallback(callingUid, "started metadata")
                }

                override fun onStdout(chunk: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    rejectUnexpectedCallback(callingUid, "stdout")
                }

                override fun onStderr(chunk: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    rejectUnexpectedCallback(callingUid, "stderr")
                }

                override fun onResult(
                    encodedResult: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    val callingUid = Binder.getCallingUid()
                    val receivedDescriptors = descriptors?.filterNotNull().orEmpty()
                    try {
                        acceptFromPinnedProvider(callingUid) {
                            val descriptorArray = requireNotNull(descriptors) {
                                "Python Runtime result descriptor array is null"
                            }
                            require(receivedDescriptors.size == descriptorArray.size) {
                                "Python Runtime result descriptor array contains a null element"
                            }
                            error("Reliable-pipe failure session unexpectedly returned a result")
                        }
                    } finally {
                        receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                    }
                }

                override fun onFailed(error: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    acceptFromPinnedProvider(callingUid) {
                        val decoded = PythonRuntimeCodec.decodeExecutionError(
                            requireNotNull(error) { "Python Runtime failure is null" },
                        )
                        policy.onFailed(decoded)
                        check(executionError.compareAndSet(null, decoded)) {
                            "Reliable-pipe failure session emitted more than one failure"
                        }
                        terminal.countDown()
                    }
                }

                override fun onCancelled(cancellation: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    rejectUnexpectedCallback(callingUid, "cancellation")
                }
            }
            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
                override fun onOpened(session: IPythonExecutionSession?) {
                    val callingUid = Binder.getCallingUid()
                    val accepted = acceptFromPinnedProvider(callingUid) {
                        val safeSession = requireNotNull(session) { "Python Runtime opened a null session" }
                        check(openedSession.compareAndSet(null, safeSession)) {
                            "Python Runtime opened more than one reliable-pipe session"
                        }
                        check(policy.requestStart()) { "Python Runtime reliable-pipe session start was not accepted" }
                        safeSession.start()
                    }
                    if (!accepted) {
                        session?.let { rejectedSession -> runCatching { rejectedSession.close() } }
                    }
                }

                override fun onRejected(error: ByteArray?) {
                    val callingUid = Binder.getCallingUid()
                    acceptFromPinnedProvider(callingUid) {
                        val decoded = PythonRuntimeCodec.decodeExecutionError(
                            requireNotNull(error) { "Python Runtime admission rejection is null" },
                        )
                        failCallback(
                            AssertionError(
                                "Reliable-pipe Python Runtime admission was rejected: ${decoded.message}",
                            ),
                        )
                    }
                }
            }

            val pipe = ParcelFileDescriptor.createReliablePipe()
            val sourceReader = pipe[0]
            val sourceWriter = pipe[1]
            var output: ParcelFileDescriptor.AutoCloseOutputStream? = null
            try {
                bound.provider.openSession(
                    PythonRuntimeCodec.encodeExecutionRequest(request),
                    arrayOf(sourceReader),
                    admissionCallback,
                    executionCallback,
                )
                // Keep the caller's reliable-pipe reader open and unread until terminal. Closing
                // a duplicated AIDL in-parameter endpoint here emits Status.OK and can mask the
                // writer's closeWithError signal before the provider consumes it.
                output = ParcelFileDescriptor.AutoCloseOutputStream(sourceWriter)
                output.write(source)
                output.flush()
                // Bytes, declared length, and digest are all exact. The reliable-pipe status is
                // therefore the only reason the receiver may reject this pre-execution snapshot.
                sourceWriter.closeWithError(RELIABLE_PIPE_PRODUCER_ERROR)

                val reachedTerminal = bound.awaitTerminalOrThrow(terminal, SESSION_WAIT_MILLIS)
                assertTrue("Reliable-pipe failure session did not reach a terminal callback", reachedTerminal)
                callbackFailure.get()?.let { error ->
                    throw AssertionError("Python Runtime reliable-pipe callback validation failed").apply {
                        initCause(error)
                    }
                }

                val rejected = requireNotNull(executionError.get()) {
                    "Reliable-pipe producer failure did not produce an execution error"
                }
                assertEquals(request.requestId, rejected.requestId)
                assertEquals(PythonErrorCode.SOURCE_REJECTED, rejected.code)
                assertEquals(PythonFailurePhase.INPUT_VALIDATION, rejected.phase)
                assertEquals(false, rejected.retryableBeforeDispatch)
                assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, policy.state)
                assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, policy.terminal)
                assertEquals(
                    "Reliable-pipe session must receive opened and failed from the pinned UID",
                    EXPECTED_RELIABLE_PIPE_CALLBACK_COUNT,
                    callbackUids.size,
                )
                assertTrue(
                    "At least one reliable-pipe callback came from an unpinned UID: $callbackUids",
                    callbackUids.all { uid -> uid == snapshot.packageUid },
                )
                bound.throwIfTransportFailed()
            } finally {
                runCatching { output?.close() }
                runCatching { sourceReader.close() }
                runCatching { sourceWriter.close() }
                openedSession.get()?.let { session -> runCatching { session.close() } }
            }
        }
    }

    private fun discoverFakeProvider(): AndroidPythonRuntimeProviderIdentitySnapshot {
        val expected = PythonRuntimeProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val matches = transport.discover().filter { snapshot -> snapshot.component == expected }
        assertEquals(
            "Exact-action discovery must return exactly one Python Runtime fake service",
            1,
            matches.size,
        )
        return matches.single()
    }

    private fun bindProvider(
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
    ): BoundProvider {
        val providerReference = AtomicReference<IPythonRuntimeProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
        val binding = transport.bindExact(
            snapshot,
            object : AndroidPythonRuntimeBindingCallback {
                override fun onConnected(provider: IPythonRuntimeProvider) {
                    if (!providerReference.compareAndSet(null, provider)) {
                        failure.compareAndSet(null, SecurityException("Python Runtime connected more than once"))
                    }
                    connected.countDown()
                }

                override fun onBindingFailure(cause: Throwable?) {
                    failure.compareAndSet(
                        null,
                        cause ?: IllegalStateException("Python Runtime service disconnected while binding"),
                    )
                    connected.countDown()
                }

                override fun onBinderDeath() {
                    failure.compareAndSet(null, IllegalStateException("Python Runtime Binder died while binding"))
                    connected.countDown()
                }
            },
        )
        if (!connected.await(BIND_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            binding.close()
            throw AssertionError("Timed out binding the exact Python Runtime fake component")
        }
        failure.get()?.let { error ->
            binding.close()
            throw AssertionError("Unable to bind the exact Python Runtime fake component").apply { initCause(error) }
        }
        return BoundProvider(
            provider = checkNotNull(providerReference.get()) { "Python Runtime binding completed without a provider" },
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
                throw AssertionError("Python Runtime transport failed after binding").apply { initCause(error) }
            }
        }

        override fun close() = binding.close()
    }

    private companion object {
        const val ARG_ENABLED = "autojs.python.r1.conformance.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val FAKE_PACKAGE = "org.autojs.plugin.python.runtime.fake"
        const val FAKE_SERVICE = "$FAKE_PACKAGE.service.FakePythonRuntimeService"
        const val FAKE_PROVIDER_ID = "org.autojs.python.runtime.fake"
        const val FAKE_PYTHON_VERSION = "3.13.0"

        const val BIND_WAIT_MILLIS = 10_000L
        const val SESSION_TIMEOUT_MILLIS = 10_000L
        const val SESSION_WAIT_MILLIS = 15_000L
        const val TRANSPORT_FAILURE_POLL_MILLIS = 100L
        const val MAX_OUTPUT_BYTES = 1024L
        const val MAX_OUTPUT_CHUNK_BYTES = 1024
        const val MAX_OUTPUT_CHUNKS = 16L
        const val NORMAL_OUTPUT_CREDITS = 1
        const val EXPECTED_NORMAL_CALLBACK_COUNT = 4
        const val EXPECTED_RELIABLE_PIPE_CALLBACK_COUNT = 2
        const val RELIABLE_PIPE_PRODUCER_ERROR = "intentional exact-source producer failure"
        val NORMAL_STDOUT = "fake-python-ok\n".toByteArray(Charsets.UTF_8)
    }
}
