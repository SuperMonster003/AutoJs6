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
import org.autojs.plugin.python.runtime.api.PythonInputEcho
import org.autojs.plugin.python.runtime.api.PythonInputReply
import org.autojs.plugin.python.runtime.api.PythonInputReplyStatus
import org.autojs.plugin.python.runtime.api.PythonInteractiveInputPolicy
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonOutputStream
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonResultPolicy
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in U1-R2 evidence through the exact real-plugin Binder boundary.
 *
 * Every method is safe to launch as an independent instrumentation selector. It admits only the
 * canonical Host/plugin identity and validates callback UIDs, protocol order, exact typed payloads,
 * one terminal, and descriptor absence on the covered result paths.
 */
class PythonRuntimeU1R2BinderInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireExplicitBinderAcceptanceOptIn() {
        assertEquals(
            "U1-R2 Binder acceptance requires an explicit opt-in argument",
            "true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED),
        )
        assertTrue("U1-R2 Binder acceptance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("U1-R2 Binder acceptance requires the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real Python Runtime plugin must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
    }

    @Test
    fun interactivePromptReplyStreamsBeforeTerminalAndKeepsStdoutOutOfResult() {
        val snapshot = discoverRealProvider()
        bindProvider(snapshot).use { bound ->
            val runtime = inspectRuntime(bound, snapshot)
            val observation = execute(
                bound = bound,
                snapshot = snapshot,
                runtime = runtime,
                caseName = "interactive",
                sourceText = """
                    import sys
                    from autojs6 import result

                    sys.stdout.write('$STDOUT_JSON_LOOKALIKE_ESCAPED\n')
                    sys.stdout.flush()
                    reply = input("$PROMPT_TEXT")
                    result.set({"reply": reply, "stdoutIsResult": False})
                """.trimIndent() + "\n",
                interactiveInput = PythonInteractiveInputPolicy(
                    maxPrompts = 1,
                    maxPromptBytes = PROMPT_LIMIT_BYTES,
                    maxReplyBytes = REPLY_LIMIT_BYTES,
                    replyTimeoutMillis = INPUT_WAIT_MILLIS,
                ),
                resultPolicy = JSON_ONLY_RESULT_POLICY,
                expectedPrompt = PROMPT_TEXT,
                replyValue = REPLY_VALUE,
            )

            observation.throwIfCallbackFailed()
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, observation.policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, observation.policy.terminal)
            assertEquals(1, observation.startedCount)
            assertEquals(2, observation.stdoutCount)
            assertEquals(1, observation.promptCount)
            assertEquals(1, observation.replyCount)
            assertEquals(1, observation.resultCount)
            assertEquals(0, observation.failedCount)
            assertEquals(0, observation.cancelledCount)
            assertEquals(0, observation.resultDescriptorCount)
            assertArrayEquals(EXPECTED_INTERACTIVE_STDOUT.toByteArray(Charsets.UTF_8), observation.stdout)
            assertArrayEquals(ByteArray(0), observation.stderr)
            assertTrue(
                "Execution-time stdout was not observed before the interactive prompt",
                observation.startedOrder in 1 until observation.stdoutOrder &&
                    observation.stdoutOrder < observation.promptOrder,
            )
            assertTrue(
                "Interactive reply did not precede the terminal result",
                observation.promptOrder < observation.replyOrder &&
                    observation.replyOrder < observation.terminalOrder,
            )
            val result = requireNotNull(observation.result)
            assertEquals(0, result.exitCode)
            assertEquals(EXPECTED_INTERACTIVE_STDOUT.toByteArray(Charsets.UTF_8).size.toLong(), result.stdoutBytes)
            assertEquals(0L, result.stderrBytes)
            assertEquals(2L, result.stdoutChunks)
            assertEquals(0L, result.stderrChunks)
            assertEquals(EXPECTED_INTERACTIVE_JSON, result.structuredJson)
            assertTrue(result.outputArtifacts.isEmpty())
            assertFalse(
                "JSON-looking stdout was incorrectly promoted to the explicit result",
                result.structuredJson == STDOUT_JSON_LOOKALIKE.trim(),
            )
            assertPinnedCallbacks(snapshot, observation)
            bound.throwIfTransportFailed()
        }
    }

    @Test
    fun oversizedArtifactPublishesNoPartialResultThenNextExactBindSucceeds() {
        val snapshot = discoverRealProvider()
        val rejected = bindProvider(snapshot).use { bound ->
            val runtime = inspectRuntime(bound, snapshot)
            execute(
                bound = bound,
                snapshot = snapshot,
                runtime = runtime,
                caseName = "artifact-rejected",
                sourceText = """
                    from autojs6 import artifacts, result

                    target = artifacts.path("too-large.bin")
                    with open(target, "wb") as output:
                        output.write(b"123456789")
                    result.set({"mustNotPublish": True})
                """.trimIndent() + "\n",
                interactiveInput = null,
                resultPolicy = PythonResultPolicy(
                    maxStructuredJsonBytes = 256,
                    maxArtifacts = 1,
                    maxArtifactPathBytes = 64,
                    maxArtifactBytes = 8,
                    maxTotalArtifactBytes = 8,
                ),
            ).also { observation ->
                observation.throwIfCallbackFailed()
                assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, observation.policy.state)
                assertEquals(PythonExecutionSessionPolicy.Terminal.FAILED, observation.policy.terminal)
                assertEquals(1, observation.startedCount)
                assertEquals(0, observation.stdoutCount)
                assertEquals(0, observation.stderrCount)
                assertEquals(0, observation.promptCount)
                assertEquals(0, observation.replyCount)
                assertNull("Rejected artifact session published a partial result", observation.result)
                assertEquals(0, observation.resultCount)
                assertEquals(1, observation.failedCount)
                assertEquals(0, observation.cancelledCount)
                assertEquals(0, observation.resultDescriptorCount)
                assertArrayEquals(ByteArray(0), observation.stdout)
                assertArrayEquals(ByteArray(0), observation.stderr)
                val error = requireNotNull(observation.error)
                assertEquals(PythonErrorCode.OUTPUT_ARTIFACT_REJECTED, error.code)
                assertEquals(PythonFailurePhase.RESULT, error.phase)
                assertFalse(error.retryableBeforeDispatch)
                assertPinnedCallbacks(snapshot, observation)
                bound.throwIfTransportFailed()
            }
        }

        val current = requireNotNull(transport.inspectExact(snapshot.component)) {
            "Real Python Runtime identity disappeared before recovery"
        }
        assertEquals("Real Python Runtime identity changed after result rejection", snapshot, current)
        val recovered = bindProvider(current).use { bound ->
            val runtime = inspectRuntime(bound, current)
            execute(
                bound = bound,
                snapshot = current,
                runtime = runtime,
                caseName = "artifact-recovery",
                sourceText = """
                    from autojs6 import result
                    result.set({"recovered": True})
                """.trimIndent() + "\n",
                interactiveInput = null,
                resultPolicy = JSON_ONLY_RESULT_POLICY,
            ).also { observation ->
                observation.throwIfCallbackFailed()
                assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, observation.policy.state)
                assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, observation.policy.terminal)
                assertEquals(1, observation.startedCount)
                assertEquals(0, observation.stdoutCount)
                assertEquals(0, observation.stderrCount)
                assertEquals(0, observation.promptCount)
                assertEquals(0, observation.replyCount)
                assertEquals(1, observation.resultCount)
                assertEquals(0, observation.failedCount)
                assertEquals(0, observation.cancelledCount)
                assertEquals(0, observation.resultDescriptorCount)
                val result = requireNotNull(observation.result)
                assertEquals(0, result.exitCode)
                assertEquals(0L, result.stdoutBytes)
                assertEquals(0L, result.stderrBytes)
                assertEquals(EXPECTED_RECOVERY_JSON, result.structuredJson)
                assertTrue(result.outputArtifacts.isEmpty())
                assertPinnedCallbacks(current, observation)
                bound.throwIfTransportFailed()
            }
        }
        assertFalse("Rejected and recovery requests reused an execution ID", rejected.requestId == recovered.requestId)
    }

    private fun execute(
        bound: BoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        runtime: RuntimeSnapshot,
        caseName: String,
        sourceText: String,
        interactiveInput: PythonInteractiveInputPolicy?,
        resultPolicy: PythonResultPolicy,
        expectedPrompt: String? = null,
        replyValue: String? = null,
    ): Observation {
        val source = sourceText.toByteArray(Charsets.UTF_8)
        val request = PythonExecutionRequest(
            requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = runtime.protocolVersion,
            entryPoint = ENTRY_POINT,
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
            interactiveInput = interactiveInput,
            resultPolicy = resultPolicy,
        )
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = runtime.capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
        val policy = PythonExecutionSessionPolicy(
            expectedRequest = request,
            maximumCredits = runtime.capabilities.limits.maxOutstandingOutputCredits,
            expectedPythonVersion = runtime.info.pythonVersion,
        )
        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackFailure = AtomicReference<Throwable?>()
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result = AtomicReference<PythonExecutionResult?>()
        val error = AtomicReference<PythonExecutionError?>()
        val terminal = CountDownLatch(1)
        val eventSequence = AtomicLong(0)
        val startedOrder = AtomicLong(0)
        val stdoutOrder = AtomicLong(0)
        val promptOrder = AtomicLong(0)
        val replyOrder = AtomicLong(0)
        val terminalOrder = AtomicLong(0)
        val startedCount = AtomicInteger(0)
        val stdoutCount = AtomicInteger(0)
        val stderrCount = AtomicInteger(0)
        val promptCount = AtomicInteger(0)
        val replyCount = AtomicInteger(0)
        val resultCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)
        val descriptorCount = AtomicInteger(0)

        fun recordOnce(target: AtomicLong, label: String) {
            check(target.compareAndSet(0L, eventSequence.incrementAndGet())) { "$label was recorded more than once" }
        }

        fun failCallback(failure: Throwable) {
            callbackFailure.compareAndSet(null, failure)
            terminal.countDown()
        }

        fun fromPinnedProvider(action: () -> Unit): Boolean {
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
            return runCatching(action).onFailure(::failCallback).isSuccess
        }

        val executionCallback = object : IPythonExecutionCallback.Stub() {
            override fun onStarted(metadata: ByteArray?) {
                fromPinnedProvider {
                    val started = PythonRuntimeCodec.decodeSessionStarted(
                        requireNotNull(metadata) { "Python Runtime started metadata is null" },
                    )
                    policy.onStarted(started)
                    check(started.runtimeGeneration > 0L)
                    startedCount.incrementAndGet()
                    recordOnce(startedOrder, "started event")
                }
            }

            override fun onStdout(chunk: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Python Runtime stdout chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDOUT)
                    synchronized(stdout) { stdout.write(decoded.toByteArray()) }
                    stdoutCount.incrementAndGet()
                    val order = eventSequence.incrementAndGet()
                    stdoutOrder.compareAndSet(0L, order)
                }
            }

            override fun onStderr(chunk: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeOutputChunk(
                        requireNotNull(chunk) { "Python Runtime stderr chunk is null" },
                    )
                    policy.onOutput(decoded, PythonOutputStream.STDERR)
                    synchronized(stderr) { stderr.write(decoded.toByteArray()) }
                    stderrCount.incrementAndGet()
                }
            }

            override fun onHeartbeat(heartbeat: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionHeartbeat(
                        requireNotNull(heartbeat) { "Python Runtime heartbeat is null" },
                    )
                    policy.onHeartbeat(decoded)
                }
            }

            override fun onInputPrompt(encodedPrompt: ByteArray?) {
                fromPinnedProvider {
                    val expectedText = requireNotNull(expectedPrompt) {
                        "Python Runtime emitted an unauthorized input prompt"
                    }
                    val expectedReply = requireNotNull(replyValue) {
                        "Interactive test did not provide a bounded reply"
                    }
                    val prompt = PythonRuntimeCodec.decodeInputPrompt(
                        requireNotNull(encodedPrompt) { "Python Runtime input prompt is null" },
                    )
                    policy.onInputPrompt(prompt)
                    assertEquals(request.requestId, prompt.requestId)
                    assertEquals(expectedText, prompt.text)
                    assertEquals(PythonInputEcho.VISIBLE, prompt.echo)
                    assertEquals(interactiveInput?.maxReplyBytes, prompt.maxReplyBytes)
                    assertEquals(interactiveInput?.replyTimeoutMillis, prompt.replyTimeoutMillis)
                    assertTrue("Input prompt arrived before execution-time stdout", stdoutOrder.get() > 0L)
                    assertEquals("Input prompt arrived after a terminal callback", 1L, terminal.count)
                    promptCount.incrementAndGet()
                    recordOnce(promptOrder, "input prompt event")
                    val reply = PythonInputReply(
                        requestId = request.requestId,
                        promptId = prompt.promptId,
                        status = PythonInputReplyStatus.VALUE,
                        value = expectedReply,
                    )
                    check(policy.replyInput(reply)) { "Python Runtime session policy rejected the one-shot input reply" }
                    replyCount.incrementAndGet()
                    recordOnce(replyOrder, "input reply event")
                    requireNotNull(openedSession.get()) { "Input prompt arrived without an opened session" }
                        .replyInput(PythonRuntimeCodec.encodeInputReply(reply))
                }
            }

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
                        descriptorCount.addAndGet(received.size)
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Python Runtime result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Python Runtime emitted more than one result" }
                        resultCount.incrementAndGet()
                        recordOnce(terminalOrder, "result terminal")
                        terminal.countDown()
                    }
                } finally {
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(encodedError: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Python Runtime failure is null" },
                    )
                    policy.onFailed(decoded)
                    check(error.compareAndSet(null, decoded)) { "Python Runtime emitted more than one failure" }
                    failedCount.incrementAndGet()
                    recordOnce(terminalOrder, "failure terminal")
                    terminal.countDown()
                }
            }

            override fun onCancelled(cancellation: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeCancellation(
                        requireNotNull(cancellation) { "Python Runtime cancellation is null" },
                    )
                    policy.onCancelled(decoded)
                    cancelledCount.incrementAndGet()
                    recordOnce(terminalOrder, "cancellation terminal")
                    terminal.countDown()
                }
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val accepted = fromPinnedProvider {
                    val safeSession = requireNotNull(session) { "Python Runtime opened a null session" }
                    check(openedSession.compareAndSet(null, safeSession)) {
                        "Python Runtime opened more than one session"
                    }
                    check(policy.requestStart()) { "Python Runtime session start was not accepted" }
                    safeSession.start()
                    policy.grantOutputCredits(OUTPUT_CREDITS)
                    safeSession.grantOutputCredits(OUTPUT_CREDITS)
                }
                if (!accepted) session?.let { rejected -> runCatching { rejected.close() } }
            }

            override fun onRejected(encodedError: ByteArray?) {
                fromPinnedProvider {
                    val decoded = PythonRuntimeCodec.decodeExecutionError(
                        requireNotNull(encodedError) { "Python Runtime admission rejection is null" },
                    )
                    failCallback(AssertionError("Python Runtime admission was rejected: ${decoded.message}"))
                }
            }
        }

        val sourceFile = File(context.cacheDir, "python-u1r2-$caseName-${UUID.randomUUID()}.py")
        sourceFile.writeBytes(source)
        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                bound.provider.openSessionForNegotiatedProtocol(
                    request,
                    arrayOf(descriptor),
                    admissionCallback,
                    executionCallback,
                )
            }
            assertTrue(
                "Python Runtime $caseName session did not reach a terminal callback",
                bound.awaitTerminalOrThrow(terminal, SESSION_WAIT_MILLIS),
            )
            return Observation(
                requestId = request.requestId,
                policy = policy,
                callbackUids = callbackUids.toList(),
                callbackFailure = callbackFailure,
                result = result.get(),
                error = error.get(),
                stdout = synchronized(stdout) { stdout.toByteArray() },
                stderr = synchronized(stderr) { stderr.toByteArray() },
                startedCount = startedCount.get(),
                stdoutCount = stdoutCount.get(),
                stderrCount = stderrCount.get(),
                promptCount = promptCount.get(),
                replyCount = replyCount.get(),
                resultCount = resultCount.get(),
                failedCount = failedCount.get(),
                cancelledCount = cancelledCount.get(),
                resultDescriptorCount = descriptorCount.get(),
                startedOrder = startedOrder.get(),
                stdoutOrder = stdoutOrder.get(),
                promptOrder = promptOrder.get(),
                replyOrder = replyOrder.get(),
                terminalOrder = terminalOrder.get(),
            )
        } finally {
            source.fill(0)
            // Unlink the expected provider Binder before closing the terminal session. The real
            // provider may retire its worker process on close, which is cleanup rather than an
            // unexpected transport failure after the terminal was already validated.
            bound.binding.close()
            openedSession.get()?.let { session -> runCatching { session.close() } }
            assertTrue("Unable to remove $caseName source snapshot", !sourceFile.exists() || sourceFile.delete())
        }
    }

    private fun discoverRealProvider(): AndroidPythonRuntimeProviderIdentitySnapshot {
        val component = PythonRuntimeProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val matches = transport.discover().filter { snapshot -> snapshot.component == component }
        assertEquals("Exact-action discovery must return one real Python Runtime service", 1, matches.size)
        val snapshot = matches.single()
        assertEquals(PythonRuntimeContract.RUNTIME_ACTION, snapshot.declaredAction)
        assertTrue("Real Python Runtime service is not exported", snapshot.exported)
        assertTrue("Real Python Runtime application is disabled", snapshot.applicationEnabled)
        assertTrue("Real Python Runtime service is disabled", snapshot.serviceEnabled)
        assertEquals(PythonRuntimeContract.PLUGIN_PERMISSION, snapshot.requiredPermission)
        assertTrue("Real Python Runtime signer differs from Host", snapshot.sameSignerAsHost)
        assertEquals(snapshot, transport.inspectExact(component))
        return snapshot
    }

    private fun inspectRuntime(
        bound: BoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
    ): RuntimeSnapshot {
        assertNull("Exact Binder handshake reused the discovery action", bound.binding.intentAction)
        val info = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
        val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, info)
        val protocolVersion = PythonRuntimeNegotiation.requireNegotiated(info)
        assertEquals(
            "Real plugin does not negotiate the newest protocol of this Host",
            PythonRuntimeNegotiation.HOST_MAX,
            protocolVersion,
        )
        assertEquals(REAL_PROVIDER_ID, info.providerId)
        assertEquals(PythonImplementation.CPYTHON, info.implementation)
        assertEquals(PythonIsolationMode.DEDICATED_PLUGIN_PROCESS, info.isolationMode)
        assertEquals(EXPECTED_PYTHON_VERSION, info.pythonVersion)
        assertEquals(info.pythonVersion, capabilities.pythonVersion)
        assertTrue("Real plugin does not advertise interactive input", capabilities.supportsInteractiveInput)
        assertTrue("Real plugin does not advertise structured JSON results", capabilities.supportsStructuredJsonResult)
        assertTrue("Real plugin does not advertise output artifacts", capabilities.supportsOutputArtifacts)
        assertTrue(capabilities.limits.maxInputPromptBytes >= PROMPT_LIMIT_BYTES)
        assertTrue(capabilities.limits.maxInputReplyBytes >= REPLY_LIMIT_BYTES)
        assertTrue(capabilities.limits.maxInputWaitMillis >= INPUT_WAIT_MILLIS)
        assertTrue(capabilities.limits.maxOutstandingOutputCredits >= OUTPUT_CREDITS)
        assertTrue(capabilities.limits.maxStructuredJsonBytes >= JSON_ONLY_RESULT_POLICY.maxStructuredJsonBytes)
        assertTrue(capabilities.limits.maxOutputArtifactBytes >= 9L)
        assertEquals(snapshot, transport.inspectExact(snapshot.component))
        bound.throwIfTransportFailed()
        return RuntimeSnapshot(info, capabilities, protocolVersion)
    }

    private fun bindProvider(snapshot: AndroidPythonRuntimeProviderIdentitySnapshot): BoundProvider {
        val providerRef = AtomicReference<IPythonRuntimeProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
        val binding = transport.bindExact(
            snapshot,
            object : AndroidPythonRuntimeBindingCallback {
                override fun onConnected(provider: IPythonRuntimeProvider) {
                    if (!providerRef.compareAndSet(null, provider)) {
                        failure.compareAndSet(null, SecurityException("Python Runtime connected more than once"))
                    }
                    connected.countDown()
                }

                override fun onBindingFailure(cause: Throwable?) {
                    failure.compareAndSet(
                        null,
                        cause ?: IllegalStateException("Python Runtime disconnected while binding"),
                    )
                    connected.countDown()
                }

                override fun onBinderDeath() {
                    failure.compareAndSet(null, IllegalStateException("Python Runtime Binder died"))
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
            provider = checkNotNull(providerRef.get()) { "Python Runtime binding completed without provider" },
            binding = binding,
            transportFailure = failure,
        )
    }

    private fun assertPinnedCallbacks(
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        observation: Observation,
    ) {
        assertTrue("Python Runtime emitted no callbacks", observation.callbackUids.isNotEmpty())
        assertTrue(
            "At least one callback came from an unpinned UID: ${observation.callbackUids}",
            observation.callbackUids.all { uid -> uid == snapshot.packageUid },
        )
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private data class RuntimeSnapshot(
        val info: PythonRuntimeInfo,
        val capabilities: PythonRuntimeCapabilities,
        val protocolVersion: PythonProtocolVersion,
    )

    private data class Observation(
        val requestId: PythonRequestId,
        val policy: PythonExecutionSessionPolicy,
        val callbackUids: List<Int>,
        private val callbackFailure: AtomicReference<Throwable?>,
        val result: PythonExecutionResult?,
        val error: PythonExecutionError?,
        val stdout: ByteArray,
        val stderr: ByteArray,
        val startedCount: Int,
        val stdoutCount: Int,
        val stderrCount: Int,
        val promptCount: Int,
        val replyCount: Int,
        val resultCount: Int,
        val failedCount: Int,
        val cancelledCount: Int,
        val resultDescriptorCount: Int,
        val startedOrder: Long,
        val stdoutOrder: Long,
        val promptOrder: Long,
        val replyOrder: Long,
        val terminalOrder: Long,
    ) {
        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { error ->
                throw AssertionError("Python Runtime callback validation failed").apply { initCause(error) }
            }
        }
    }

    private class BoundProvider(
        val provider: IPythonRuntimeProvider,
        val binding: AndroidPythonRuntimeBinding,
        private val transportFailure: AtomicReference<Throwable?>,
    ) : Closeable {
        fun awaitTerminalOrThrow(terminal: CountDownLatch, timeoutMillis: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                throwIfTransportFailed()
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                if (terminal.await(
                        minOf(remaining, TimeUnit.MILLISECONDS.toNanos(TRANSPORT_POLL_MILLIS)),
                        TimeUnit.NANOSECONDS,
                    )
                ) {
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
        const val ARG_ENABLED = "autojs.python.u1r2.binder.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE = "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val ENTRY_POINT = "main.py"
        const val PROMPT_TEXT = "U1-R2 reply> "
        const val REPLY_VALUE = "你好🙂-reply"
        const val PROMPT_LIMIT_BYTES = 256
        const val REPLY_LIMIT_BYTES = 256
        const val INPUT_WAIT_MILLIS = 10_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val SESSION_WAIT_MILLIS = 45_000L
        const val BIND_WAIT_MILLIS = 10_000L
        const val TRANSPORT_POLL_MILLIS = 100L
        const val MAX_OUTPUT_BYTES = 4_096L
        const val MAX_OUTPUT_CHUNK_BYTES = 1_024
        const val MAX_OUTPUT_CHUNKS = 16L
        const val OUTPUT_CREDITS = 4
        const val STDOUT_JSON_LOOKALIKE = "{\"stdout\":\"diagnostic-only\"}\n"
        const val STDOUT_JSON_LOOKALIKE_ESCAPED = "{\\\"stdout\\\":\\\"diagnostic-only\\\"}"
        const val EXPECTED_INTERACTIVE_STDOUT = "$STDOUT_JSON_LOOKALIKE$PROMPT_TEXT"
        const val EXPECTED_INTERACTIVE_JSON =
            "{\"reply\":\"$REPLY_VALUE\",\"stdoutIsResult\":false}"
        const val EXPECTED_RECOVERY_JSON = "{\"recovered\":true}"
        val JSON_ONLY_RESULT_POLICY = PythonResultPolicy(
            maxStructuredJsonBytes = 1_024,
            maxArtifacts = 0,
            maxArtifactPathBytes = 0,
            maxArtifactBytes = 0,
            maxTotalArtifactBytes = 0,
        )
    }
}
