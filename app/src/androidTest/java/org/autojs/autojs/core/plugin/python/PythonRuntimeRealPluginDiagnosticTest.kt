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
import org.autojs.plugin.python.runtime.api.PythonCancellationMode
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
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
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in, debug-only diagnostic for the independently built real CPython runtime plugin.
 *
 * This is deliberately one positive case, not an R2 acceptance suite. The external runner pins
 * the device serial and all three APK hashes/signers, performs reversible installs, invokes only
 * this method, and never writes a formal PASS receipt. Cancellation, worker death/rebind, timeout,
 * update/uninstall races, hostile callers, and the after-dispatch no-replay rule remain separate.
 */
class PythonRuntimeRealPluginDiagnosticTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val transport = AndroidPythonRuntimeConformanceTransport(context)

    @Before
    fun requireExplicitRealPluginOptIn() {
        assumeTrue(
            "Real Python Runtime diagnostic is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("Real Python Runtime diagnostic requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Real plugin admits only the canonical host package", HOST_PACKAGE, context.packageName)
        assertTrue("Real Python Runtime plugin is not installed", isPackageInstalled(REAL_PLUGIN_PACKAGE))
        assertEquals(
            "Host and real plugin must share the signature permission signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
    }

    @Test
    fun realPluginMetadataActionNullBindAndCpythonPfdExecution() {
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
        assertTrue("Real Python Runtime version is invalid", snapshot.versionCode > 0L)
        assertTrue("Real Python Runtime update time is invalid", snapshot.lastUpdateTime > 0L)
        assertEquals(snapshot, transport.inspectExact(expectedComponent))

        bindProvider(snapshot).use { bound ->
            assertNull("Exact real-plugin bind reused the discovery action", bound.binding.intentAction)
            val runtimeInfo = PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo)
            val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(capabilities, runtimeInfo)

            assertEquals(REAL_PROVIDER_ID, runtimeInfo.providerId)
            assertEquals(PythonImplementation.CPYTHON, runtimeInfo.implementation)
            assertEquals(PythonIsolationMode.DEDICATED_PLUGIN_PROCESS, runtimeInfo.isolationMode)
            assertEquals(EXPECTED_PYTHON_VERSION, runtimeInfo.pythonVersion)
            assertEquals(runtimeInfo.pythonVersion, capabilities.pythonVersion)
            assertEquals(PythonImplementation.CPYTHON, capabilities.implementation)
            assertEquals(PythonCancellationMode.PROCESS_RESTART_ONLY, capabilities.cancellationMode)
            assertTrue("Real plugin must advertise structured traceback", capabilities.supportsStructuredTraceback)
            assertEquals(1, capabilities.limits.maxConcurrentSessions)
            assertTrue(
                "Real plugin cannot accept the two diagnostic output records",
                capabilities.limits.maxOutstandingOutputCredits >= OUTPUT_CREDITS,
            )

            val currentAbi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull()) {
                "Device did not report a primary ABI"
            }
            assertTrue("Real plugin does not advertise device ABI $currentAbi", currentAbi in capabilities.supportedAbis)
            val environment = PythonRuntimeProviderEnvironment(
                hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentAbi = currentAbi,
                releaseBuild = false,
            )
            val selection = PythonRuntimeProviderSelectionPolicy.select(
                candidates = listOf(snapshot.toCandidate(runtimeInfo, capabilities)),
                environment = environment,
                options = PythonRuntimeProviderSelectionOptions(
                    enabled = true,
                    preferredComponent = expectedComponent,
                ),
            )
            assertTrue("Validated real plugin was not selected exactly", selection is PythonRuntimeProviderSelectionResult.Selected)
            val pinned = (selection as PythonRuntimeProviderSelectionResult.Selected).pinned
            assertEquals(expectedComponent, pinned.component)
            assertEquals(snapshot.packageUid, pinned.packageUid)
            val reinspection = transport.inspectExact(expectedComponent)
            assertEquals("Real plugin identity changed after negotiation", snapshot, reinspection)
            assertTrue(
                "Real plugin pinned identity failed dispatch-time revalidation",
                PythonRuntimeProviderSelectionPolicy.revalidate(
                    pinned = pinned,
                    current = requireNotNull(reinspection).toCandidate(runtimeInfo, capabilities),
                    environment = environment,
                ),
            )

            executeRealSource(bound, snapshot, runtimeInfo.pythonVersion, capabilities.limits.maxOutstandingOutputCredits)
        }
    }

    private fun executeRealSource(
        bound: BoundProvider,
        snapshot: AndroidPythonRuntimeProviderIdentitySnapshot,
        pythonVersion: String,
        maximumCredits: Int,
    ) {
        val source = REAL_SOURCE.toByteArray(Charsets.UTF_8)
        val request = PythonExecutionRequest(
            requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = PythonRuntimeNegotiation.requireNegotiated(
                PythonRuntimeCodec.decodeRuntimeInfo(bound.provider.runtimeInfo),
            ),
            entryPoint = ENTRY_POINT,
            source = PythonPayloadReference(
                kind = PythonPayloadKind.SOURCE,
                descriptorIndex = 0,
                declaredLengthBytes = source.size.toLong(),
                sha256 = PythonSha256.digest(source),
            ),
            arguments = listOf(SCRIPT_ARGUMENT),
            timeoutMillis = SESSION_TIMEOUT_MILLIS,
            maxOutputBytes = MAX_OUTPUT_BYTES,
            maxOutputChunkBytes = MAX_OUTPUT_CHUNK_BYTES,
            maxOutputChunks = MAX_OUTPUT_CHUNKS,
        )
        val capabilities = PythonRuntimeCodec.decodeCapabilities(bound.provider.capabilities)
        PythonRuntimeValidation.validateRequestAgainst(
            request = request,
            capabilities = capabilities,
            negotiatedVersion = request.protocolVersion,
            descriptorCount = 1,
        )
        val policy = PythonExecutionSessionPolicy(
            expectedRequest = request,
            maximumCredits = maximumCredits,
            expectedPythonVersion = pythonVersion,
        )

        val callbackUids = ConcurrentLinkedQueue<Int>()
        val callbackCount = AtomicInteger(0)
        val callbackFailure = AtomicReference<Throwable?>()
        val openedSession = AtomicReference<IPythonExecutionSession?>()
        val result = AtomicReference<PythonExecutionResult?>()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val terminal = CountDownLatch(1)

        fun failCallback(error: Throwable) {
            callbackFailure.compareAndSet(null, error)
            terminal.countDown()
        }

        fun acceptFromPinnedProvider(action: () -> Unit): Boolean {
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

        fun fromPinnedProvider(action: () -> Unit) {
            acceptFromPinnedProvider(action)
        }

        val executionCallback = object : NonInteractivePythonExecutionCallback() {
            override fun onStarted(metadata: ByteArray?) = fromPinnedProvider {
                val started = PythonRuntimeCodec.decodeSessionStarted(
                    requireNotNull(metadata) { "Real Python Runtime started metadata is null" },
                )
                policy.onStarted(started)
                check(started.pythonVersion == EXPECTED_PYTHON_VERSION)
                check(started.runtimeGeneration > 0L)
            }

            override fun onStdout(chunk: ByteArray?) = fromPinnedProvider {
                val decoded = PythonRuntimeCodec.decodeOutputChunk(
                    requireNotNull(chunk) { "Real Python Runtime stdout chunk is null" },
                )
                policy.onOutput(decoded, PythonOutputStream.STDOUT)
                synchronized(stdout) { stdout.write(decoded.toByteArray()) }
            }

            override fun onStderr(chunk: ByteArray?) = fromPinnedProvider {
                val decoded = PythonRuntimeCodec.decodeOutputChunk(
                    requireNotNull(chunk) { "Real Python Runtime stderr chunk is null" },
                )
                policy.onOutput(decoded, PythonOutputStream.STDERR)
                synchronized(stderr) { stderr.write(decoded.toByteArray()) }
            }

            override fun onResult(
                encodedResult: ByteArray?,
                descriptors: Array<out ParcelFileDescriptor?>?,
            ) {
                val receivedDescriptors = descriptors?.filterNotNull().orEmpty()
                try {
                    fromPinnedProvider {
                        val descriptorArray = requireNotNull(descriptors) {
                            "Real Python Runtime result descriptor array is null"
                        }
                        require(receivedDescriptors.size == descriptorArray.size) {
                            "Real Python Runtime result descriptor array contains null"
                        }
                        val decoded = PythonRuntimeCodec.decodeExecutionResult(
                            requireNotNull(encodedResult) { "Real Python Runtime result is null" },
                        )
                        policy.onResult(decoded, descriptorArray.size)
                        check(result.compareAndSet(null, decoded)) { "Real Python Runtime emitted multiple results" }
                        terminal.countDown()
                    }
                } finally {
                    receivedDescriptors.forEach { descriptor -> runCatching { descriptor.close() } }
                }
            }

            override fun onFailed(error: ByteArray?) = fromPinnedProvider {
                val decoded = PythonRuntimeCodec.decodeExecutionError(
                    requireNotNull(error) { "Real Python Runtime failure is null" },
                )
                policy.onFailed(decoded)
                failCallback(
                    AssertionError(
                        "Real CPython execution failed: code=${decoded.code}, phase=${decoded.phase}, " +
                            "exception=${decoded.exceptionType}: ${decoded.exceptionMessage}",
                    ),
                )
            }

            override fun onCancelled(cancellation: ByteArray?) = fromPinnedProvider {
                val decoded = PythonRuntimeCodec.decodeCancellation(
                    requireNotNull(cancellation) { "Real Python Runtime cancellation is null" },
                )
                failCallback(AssertionError("Unexpected real Python Runtime cancellation: ${decoded.reason}"))
            }
        }
        val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
            override fun onOpened(session: IPythonExecutionSession?) {
                val accepted = acceptFromPinnedProvider {
                    val safeSession = requireNotNull(session) { "Real Python Runtime opened a null session" }
                    check(openedSession.compareAndSet(null, safeSession)) {
                        "Real Python Runtime opened more than one session"
                    }
                    check(policy.requestStart()) { "Real Python Runtime start was not accepted" }
                    safeSession.start()
                    policy.grantOutputCredits(OUTPUT_CREDITS)
                    safeSession.grantOutputCredits(OUTPUT_CREDITS)
                }
                if (!accepted) session?.let { rejected -> runCatching { rejected.close() } }
            }

            override fun onRejected(error: ByteArray?) = fromPinnedProvider {
                val decoded = PythonRuntimeCodec.decodeExecutionError(
                    requireNotNull(error) { "Real Python Runtime admission rejection is null" },
                )
                failCallback(
                    AssertionError("Real Python Runtime admission rejected: ${decoded.code}/${decoded.phase}"),
                )
            }
        }

        val sourceFile = File(context.cacheDir, "python-runtime-r2-real-${UUID.randomUUID()}.py")
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
                "Real CPython session did not reach a terminal callback",
                bound.awaitTerminalOrThrow(terminal, SESSION_WAIT_MILLIS),
            )
            callbackFailure.get()?.let { error ->
                throw AssertionError("Real CPython callback validation failed").apply { initCause(error) }
            }

            val acceptedResult = result.get()
            assertNotNull("Real CPython session returned no result", acceptedResult)
            requireNotNull(acceptedResult)
            assertEquals(0, acceptedResult.exitCode)
            assertEquals(EXPECTED_STDOUT.size.toLong(), acceptedResult.stdoutBytes)
            assertEquals(EXPECTED_STDERR.size.toLong(), acceptedResult.stderrBytes)
            assertEquals(1L, acceptedResult.stdoutChunks)
            assertEquals(1L, acceptedResult.stderrChunks)
            assertArrayEquals(EXPECTED_STDOUT, synchronized(stdout) { stdout.toByteArray() })
            assertArrayEquals(EXPECTED_STDERR, synchronized(stderr) { stderr.toByteArray() })
            assertEquals(PythonExecutionSessionPolicy.State.TERMINAL, policy.state)
            assertEquals(PythonExecutionSessionPolicy.Terminal.RESULT, policy.terminal)
            assertEquals(EXPECTED_CALLBACK_COUNT, callbackCount.get())
            assertTrue(
                "At least one real Python Runtime callback came from an unpinned UID: $callbackUids",
                callbackUids.all { uid -> uid == snapshot.packageUid },
            )
            bound.throwIfTransportFailed()
        } finally {
            // A dispatched real session intentionally retires its dedicated process on close.
            // Unlink the transport death recipient first so expected retirement is not mistaken
            // for an unexpected Binder failure after the result has already been validated.
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
        const val ARG_ENABLED = "autojs.python.r2.real.enabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE =
            "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val REAL_PROVIDER_ID = "org.autojs.python.runtime.cpython"
        const val EXPECTED_PYTHON_VERSION = "3.13.9"
        const val ENTRY_POINT = "main.py"
        const val SCRIPT_ARGUMENT = "r2-real-device"

        const val BIND_WAIT_MILLIS = 10_000L
        const val SESSION_TIMEOUT_MILLIS = 30_000L
        const val SESSION_WAIT_MILLIS = 45_000L
        const val TRANSPORT_FAILURE_POLL_MILLIS = 100L
        const val MAX_OUTPUT_BYTES = 4_096L
        const val MAX_OUTPUT_CHUNK_BYTES = 1_024
        const val MAX_OUTPUT_CHUNKS = 16L
        const val OUTPUT_CREDITS = 2
        const val EXPECTED_CALLBACK_COUNT = 5

        const val STDOUT_TEXT = "CPYTHON=3.13.9;STREAM=STDOUT\n"
        const val STDERR_TEXT = "CPYTHON=3.13.9;STREAM=STDERR\n"
        val EXPECTED_STDOUT = STDOUT_TEXT.toByteArray(Charsets.UTF_8)
        val EXPECTED_STDERR = STDERR_TEXT.toByteArray(Charsets.UTF_8)
        val REAL_SOURCE = """
            import sys
            assert sys.implementation.name == "cpython", sys.implementation.name
            assert sys.version_info[:3] == (3, 13, 9), sys.version
            assert sys.argv == ["main.py", "r2-real-device"], sys.argv
            sys.stdout.write("CPYTHON=3.13.9;STREAM=STDOUT\n")
            sys.stderr.write("CPYTHON=3.13.9;STREAM=STDERR\n")
        """.trimIndent() + "\n"
    }
}
