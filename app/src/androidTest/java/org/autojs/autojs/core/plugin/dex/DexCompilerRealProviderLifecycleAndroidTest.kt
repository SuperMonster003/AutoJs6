package org.autojs.autojs.core.plugin.dex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.dexcompiler.api.DexCompileCancellation
import org.autojs.plugin.dexcompiler.api.DexCompileError
import org.autojs.plugin.dexcompiler.api.DexCompileProgress
import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompileResult
import org.autojs.plugin.dexcompiler.api.DexCompileStarted
import org.autojs.plugin.dexcompiler.api.DexCompilerCancellationReason
import org.autojs.plugin.dexcompiler.api.DexCompilerCapabilities
import org.autojs.plugin.dexcompiler.api.DexCompilerCodec
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerNegotiation
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerProgressStage
import org.autojs.plugin.dexcompiler.api.DexCompilerValidation
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import org.autojs.plugin.dexcompiler.api.DexSha256
import org.autojs.plugin.dexcompiler.api.IDexCompilerCallback
import org.autojs.plugin.dexcompiler.api.IDexCompilerProvider
import org.autojs.plugin.dexcompiler.api.IDexCompilerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Explicitly serialized hostile lifecycle checks against the independently installed real plugin.
 *
 * The first test occupies the production process-wide session gate with a real Binder request whose
 * program pipe intentionally remains open and empty. No plugin test hook is involved. The second
 * test force-stops the real package and must therefore run alone on the explicitly named device.
 * It restores the package to a bindable, authenticated state in `finally` before returning.
 */
class DexCompilerRealProviderLifecycleAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun blockedRealSessionRejectsBusyAndOwnsEveryDescriptorAndTerminal() = synchronized(SERIAL_LOCK) {
        val identity = requireLifecycleEnvironment(requireForceStopConsent = false)
        bindProvider(identity).use { binding ->
            val contract = readContract(binding.provider, identity)
            val firstCallback = RecordingCallback()
            val first = DirectPipeSession.open(
                provider = binding.provider,
                request = request(contract, "active"),
                callback = firstCallback,
            )
            var busy: DirectPipeSession? = null
            var reused: DirectPipeSession? = null
            var primaryFailure: Throwable? = null
            try {
                assertTrue(
                    "The real provider never entered input validation for the blocked session",
                    firstCallback.awaitValidating(CALLBACK_TIMEOUT_MILLIS),
                )
                firstCallback.throwIfCallbackFailed()
                assertActivePrefix(firstCallback.snapshot(), first.request.requestId)

                val busyCallback = RecordingCallback()
                val busySession = DirectPipeSession.open(
                    provider = binding.provider,
                    request = request(contract, "busy"),
                    callback = busyCallback,
                )
                busy = busySession
                assertTrue(
                    "The second real session did not receive a BUSY terminal",
                    busyCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                )
                busyCallback.throwIfCallbackFailed()
                busySession.closeRemote()
                busySession.cancelRemote()
                busySession.closeProgramWriter()
                busySession.assertOutputEof("BUSY session")
                SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)

                val busyEvents = busyCallback.snapshot()
                assertEquals("BUSY emitted callbacks other than its terminal", 1, busyEvents.size)
                val busyFailure = (busyEvents.single() as? WireEvent.Failed)?.value
                assertNotNull("The second real session did not fail with a structured error", busyFailure)
                busyFailure!!
                assertEquals(busySession.request.requestId, busyFailure.requestId)
                assertEquals(DexCompilerErrorCode.BUSY, busyFailure.code)
                assertEquals(DexCompilerFailurePhase.QUEUE, busyFailure.phase)
                assertTrue("BUSY must be retryable for a later caller-owned attempt", busyFailure.retryable)
                assertTerminalDiscipline(busyCallback, expectedTerminalCount = 1)

                // Repeated cancel/close calls exercise the real one-way AIDL session and must not
                // race a second terminal against the already queued cancellation.
                first.cancelRemote()
                first.closeRemote()
                first.cancelRemote()
                assertTrue(
                    "The active real session did not emit its cancellation terminal",
                    firstCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                )
                firstCallback.throwIfCallbackFailed()
                first.closeProgramWriter()
                first.assertOutputEof("cancelled active session")
                SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)

                val firstEvents = firstCallback.snapshot()
                assertActivePrefix(firstEvents, first.request.requestId)
                assertEquals("The active real session emitted an unexpected callback count", 3, firstEvents.size)
                val cancellation = (firstEvents.last() as? WireEvent.Cancelled)?.value
                assertNotNull("The active real session did not terminate as cancelled", cancellation)
                cancellation!!
                assertEquals(first.request.requestId, cancellation.requestId)
                assertEquals(DexCompilerCancellationReason.REQUESTED, cancellation.reason)
                assertEquals(DexCompilerFailurePhase.CLEANUP, cancellation.phase)
                assertTerminalDiscipline(firstCallback, expectedTerminalCount = 1)

                val admitted = awaitGateAdmission(binding.provider, contract)
                val reusedCallback = admitted.callback
                val reusedSession = admitted.session
                reused = reusedSession
                reusedCallback.throwIfCallbackFailed()
                assertActivePrefix(reusedCallback.snapshot(), reusedSession.request.requestId)
                reusedSession.cancelRemote()
                assertTrue(
                    "The session admitted after cancellation did not terminate",
                    reusedCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                )
                reusedSession.closeProgramWriter()
                reusedSession.assertOutputEof("session admitted after cancellation")
                SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                val reusedEvents = reusedCallback.snapshot()
                assertEquals(3, reusedEvents.size)
                val reusedCancellation = (reusedEvents.last() as? WireEvent.Cancelled)?.value
                assertNotNull("The reused real session did not terminate as cancelled", reusedCancellation)
                reusedCancellation!!
                assertEquals(reusedSession.request.requestId, reusedCancellation.requestId)
                assertEquals(DexCompilerCancellationReason.REQUESTED, reusedCancellation.reason)
                assertEquals(DexCompilerFailurePhase.CLEANUP, reusedCancellation.phase)
                assertTerminalDiscipline(reusedCallback, expectedTerminalCount = 1)
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                var cleanupFailure: Throwable? = null
                cleanupFailure = captureCleanupFailure(cleanupFailure) { reused?.close() }
                cleanupFailure = captureCleanupFailure(cleanupFailure) { busy?.close() }
                cleanupFailure = captureCleanupFailure(cleanupFailure) { first.close() }
                reportCleanupFailure(primaryFailure, cleanupFailure)
            }
        }
    }

    @Test
    fun forceStoppedRealProviderSignalsBinderDeathClosesPipesAndRecovers() = synchronized(SERIAL_LOCK) {
        val identity = requireLifecycleEnvironment(requireForceStopConsent = true)
        var binding: BoundProvider? = null
        var session: DirectPipeSession? = null
        var linkedBinder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var forceStopIssued = false
        var binderDeathObserved = false
        var primaryFailure: Throwable? = null
        try {
            val activeBinding = bindProvider(identity)
            binding = activeBinding
            val contract = readContract(activeBinding.provider, identity)
            val callback = RecordingCallback()
            val activeSession = DirectPipeSession.open(
                provider = activeBinding.provider,
                request = request(contract, "binder-death"),
                callback = callback,
            )
            session = activeSession
            assertTrue(
                "The real provider never entered input validation before force-stop",
                callback.awaitValidating(CALLBACK_TIMEOUT_MILLIS),
            )
            callback.throwIfCallbackFailed()
            assertActivePrefix(callback.snapshot(), activeSession.request.requestId)

            val binderDeath = CountDownLatch(1)
            val binderDeathCount = AtomicInteger(0)
            val binder = activeBinding.provider.asBinder()
            val recipient = IBinder.DeathRecipient {
                binderDeathCount.incrementAndGet()
                binderDeath.countDown()
            }
            binder.linkToDeath(recipient, 0)
            linkedBinder = binder
            deathRecipient = recipient

            forceStopIssued = true
            executeShellCommand("am force-stop --user current $REAL_PLUGIN_PACKAGE")
            assertTrue(
                "The real provider Binder did not die after its explicitly consented force-stop",
                binderDeath.await(BINDER_DEATH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertFalse("The force-stopped real provider Binder still reports alive", binder.isBinderAlive)
            binderDeathObserved = true
            activeSession.closeProgramWriter()
            activeSession.assertOutputEof("force-stopped session")
            SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            callback.throwIfCallbackFailed()
            assertEquals("The real provider delivered duplicate Binder-death notifications", 1, binderDeathCount.get())

            // Process death is a transport terminal, not a provider callback terminal. The host's
            // binding death recipient owns this outcome; the dead process must not forge a late
            // completion, failure, or cancellation callback.
            assertEquals("Provider death unexpectedly emitted an AIDL callback terminal", 0, callback.terminalCount())
            assertFalse("A callback arrived after a terminal during provider death", callback.callbackAfterTerminal())
            assertActivePrefix(callback.snapshot(), activeSession.request.requestId)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            cleanupFailure = captureCleanupFailure(cleanupFailure) {
                session?.close(remoteAlreadyDead = binderDeathObserved)
            }
            val binder = linkedBinder
            val recipient = deathRecipient
            if (binder != null && recipient != null && !binderDeathObserved) {
                cleanupFailure = captureCleanupFailure(cleanupFailure) {
                    check(binder.unlinkToDeath(recipient, 0)) {
                        "The live provider Binder did not own the registered death recipient"
                    }
                }
            }
            cleanupFailure = captureCleanupFailure(cleanupFailure) { binding?.close() }

            if (forceStopIssued) {
                val recoveryFailure = runCatching { recoverProvider(identity) }.exceptionOrNull()
                if (recoveryFailure != null) {
                    cleanupFailure = appendFailure(cleanupFailure, recoveryFailure)
                }
            }
            reportCleanupFailure(primaryFailure, cleanupFailure)
        }
    }

    private fun requireLifecycleEnvironment(requireForceStopConsent: Boolean): DexCompilerPinnedIdentity {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real-provider lifecycle instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Real-provider lifecycle instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Lifecycle instrumentation must target the canonical host", HOST_PACKAGE, context.packageName)

        val expectedSerial = arguments.getString(ARG_DEVICE_SERIAL).orEmpty().trim()
        assertTrue("Pass the explicit adb target serial through -e $ARG_DEVICE_SERIAL", expectedSerial.isNotEmpty())
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device did not expose a serial to the instrumentation shell", actualSerial.isNotEmpty())
        assertEquals("Instrumentation is running on a different device than the declared serial", expectedSerial, actualSerial)
        if (requireForceStopConsent) {
            assertEquals(
                "Binder-death coverage requires explicit consent for the exact package being force-stopped",
                REAL_PLUGIN_PACKAGE,
                arguments.getString(ARG_FORCE_STOP_PACKAGE),
            )
        }

        val packageManager = context.packageManager
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real DEX compiler package is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(
            "The lifecycle test requires the real plugin and host to have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )

        val candidates = AndroidDexCompilerExperimentalTransport(context)
            .discoverExactAction(DexCompilerContract.SERVICE_ACTION)
            .filter { candidate -> candidate.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(
            "The real package must expose exactly the expected lifecycle-test component",
            listOf(REAL_COMPONENT),
            candidates.map(DexCompilerServiceCandidate::component),
        )
        val candidate = candidates.single()
        assertTrue("The real provider is not enabled", candidate.applicationEnabled && candidate.serviceEnabled)
        assertTrue("The real provider is not exported", candidate.exported)
        assertEquals(DexCompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue("The transport did not confirm the host/plugin signer match", candidate.sameSignerAsHost)
        assertTrue(
            "The real provider is rejected by the production host policy",
            DexCompilerExperimentalPolicy.rejection(candidate, releaseBuild = !BuildConfig.DEBUG) == null,
        )
        return candidate.pin()
    }

    private fun readContract(
        provider: IDexCompilerProvider,
        identity: DexCompilerPinnedIdentity,
    ): ProviderContract {
        val info = DexCompilerCodec.decodeInfo(provider.compilerInfo)
        val capabilities = DexCompilerCodec.decodeCapabilities(provider.capabilities)
        assertEquals("Provider version changed after exact identity pinning", identity.versionCode, info.providerVersionCode)
        assertEquals(DexCompilerContract.MAX_CONCURRENT_SESSIONS, capabilities.limits.maxConcurrentSessions)
        assertTrue(Build.VERSION.SDK_INT in capabilities.minApi..capabilities.maxApi)
        assertEquals(DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1, capabilities.runtimeLibraryModel)
        return ProviderContract(
            capabilities = capabilities,
            protocolVersion = DexCompilerNegotiation.requireNegotiated(info),
        )
    }

    private fun request(contract: ProviderContract, label: String): DexCompileRequest {
        val declaredProgram = "real-provider-lifecycle-$label-${UUID.randomUUID()}".toByteArray()
        val request = DexCompileRequest(
            requestId = DexRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = contract.protocolVersion,
            inputFormat = DexCompilerInputFormat.JAR,
            outputFormat = DexCompilerOutputFormat.DEX_ZIP,
            mode = DexCompilerMode.RELEASE,
            minApi = Build.VERSION.SDK_INT,
            programSizeBytes = declaredProgram.size.toLong(),
            programSha256 = DexSha256.digest(declaredProgram),
            maxOutputBytes = minOf(
                DexCompilerExperimentalHostLimits.MAX_IN_MEMORY_OUTPUT_BYTES,
                contract.capabilities.limits.maxOutputBytes,
            ),
            runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            expectedRuntimeLibraryFingerprint = contract.capabilities.runtimeLibraryFingerprint,
            canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
            diagnosticByteLimit = minOf(
                DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
                contract.capabilities.limits.maxDiagnosticBytes,
            ),
            timeoutMillis = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
        )
        DexCompilerValidation.validateRequestAgainst(
            request,
            contract.capabilities,
            contract.protocolVersion,
        )
        return request
    }

    private fun bindProvider(
        identity: DexCompilerPinnedIdentity,
        timeoutMillis: Long = BIND_TIMEOUT_MILLIS,
    ): BoundProvider {
        check(identity.component == REAL_COMPONENT) { "Refusing to bind an unexpected DEX compiler identity" }
        val connected = CountDownLatch(1)
        val provider = AtomicReference<IDexCompilerProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (name != REAL_COMPONENT_NAME) {
                    failure.compareAndSet(null, SecurityException("Unexpected real-provider component: $name"))
                } else {
                    provider.compareAndSet(null, IDexCompilerProvider.Stub.asInterface(service))
                }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onBindingDied(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("Real-provider binding died before connection"))
                connected.countDown()
            }

            override fun onNullBinding(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("Real provider returned a null binding"))
                connected.countDown()
            }
        }
        val intent = Intent(DexCompilerContract.SERVICE_ACTION).apply {
            component = REAL_COMPONENT_NAME
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val registered = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!registered) throw IllegalStateException("Unable to bind the exact real DEX compiler component")
        try {
            if (!connected.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw TimeoutException("Timed out binding the real DEX compiler")
            }
            failure.get()?.let { throw it }
            return BoundProvider(
                context = context,
                connection = connection,
                provider = checkNotNull(provider.get()) { "Real-provider binding completed without a Binder" },
            )
        } catch (error: Throwable) {
            runCatching { context.unbindService(connection) }
            throw error
        }
    }

    private fun recoverProvider(identity: DexCompilerPinnedIdentity) {
        // The authoritative recovery proof is an exact INCLUDE_STOPPED bind followed by a fresh
        // identity inspection and authenticated capabilities handshake. Starting the service also
        // clears Android's stopped-package state; do not depend on non-portable package-shell APIs.
        var lastFailure: Throwable? = null
        val deadline = SystemClock.elapsedRealtime() + RECOVERY_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val recoveredIdentity = AndroidDexCompilerExperimentalTransport(context)
                    .inspectExact(REAL_COMPONENT)
                    ?.pin()
                assertEquals("The real-provider identity changed during force-stop recovery", identity, recoveredIdentity)
                bindProvider(identity, RECOVERY_BIND_SLICE_MILLIS).use { recovered ->
                    readContract(recovered.provider, identity)
                    assertTrue("Recovered real-provider Binder is not alive", recovered.provider.asBinder().isBinderAlive)
                }
                return
            } catch (error: Throwable) {
                lastFailure = error
                SystemClock.sleep(RECOVERY_POLL_MILLIS)
            }
        }
        throw AssertionError("The force-stopped real provider could not be restored and rebound").also { failure ->
            lastFailure?.let(failure::initCause)
        }
    }

    private fun awaitGateAdmission(
        provider: IDexCompilerProvider,
        contract: ProviderContract,
    ): AdmittedPipeSession {
        val deadline = SystemClock.elapsedRealtime() + CALLBACK_TIMEOUT_MILLIS
        var attempt = 0
        var lastBusy: DexCompileError? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            attempt++
            val callback = RecordingCallback()
            val session = DirectPipeSession.open(
                provider = provider,
                request = request(contract, "reused-after-cancel-$attempt"),
                callback = callback,
            )
            var admitted = false
            var attemptFailure: Throwable? = null
            try {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                if (!callback.awaitFirstEvent(remaining)) {
                    throw TimeoutException("Gate-reuse attempt emitted no callback before the deadline")
                }
                callback.throwIfCallbackFailed()
                when (val first = callback.snapshot().firstOrNull()) {
                    is WireEvent.Started -> {
                        val outcomeRemaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                        if (!callback.awaitValidatingOrTerminal(outcomeRemaining)) {
                            throw TimeoutException("Started gate-reuse attempt reached neither VALIDATING nor terminal")
                        }
                        callback.throwIfCallbackFailed()
                        val outcome = callback.snapshot()
                        val validating = (outcome.getOrNull(1) as? WireEvent.Progress)?.value
                        if (
                            outcome.size != 2 ||
                            validating?.stage != DexCompilerProgressStage.VALIDATING ||
                            callback.terminalCount() != 0
                        ) {
                            throw AssertionError("Started gate-reuse attempt terminated or reordered callbacks: $outcome")
                        }
                        assertActivePrefix(outcome, session.request.requestId)
                        assertTerminalDiscipline(callback, expectedTerminalCount = 0)
                        admitted = true
                        return AdmittedPipeSession(session, callback)
                    }
                    is WireEvent.Failed -> {
                        SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)
                        val events = callback.snapshot()
                        val failure = first.value
                        if (
                            events.size != 1 ||
                            failure.requestId != session.request.requestId ||
                            failure.code != DexCompilerErrorCode.BUSY ||
                            failure.phase != DexCompilerFailurePhase.QUEUE ||
                            !failure.retryable
                        ) {
                            throw AssertionError("Gate-reuse attempt did not produce one strict BUSY terminal: $events")
                        }
                        assertTerminalDiscipline(callback, expectedTerminalCount = 1)
                        lastBusy = failure
                    }
                    null -> throw AssertionError("Gate-reuse callback latch opened without an event")
                    else -> throw AssertionError("Gate-reuse attempt emitted an invalid first callback: $first")
                }
            } catch (error: Throwable) {
                attemptFailure = error
                throw error
            } finally {
                if (!admitted) {
                    val cleanupFailure = captureCleanupFailure(null) { session.close() }
                    reportCleanupFailure(attemptFailure, cleanupFailure)
                }
            }
            SystemClock.sleep(GATE_REUSE_POLL_MILLIS)
        }
        throw AssertionError(
            "The real provider did not release its process session gate after cancellation; last BUSY=$lastBusy",
        )
    }

    private fun captureCleanupFailure(current: Throwable?, cleanup: () -> Unit): Throwable? =
        try {
            cleanup()
            current
        } catch (error: Throwable) {
            appendFailure(current, error)
        }

    private fun appendFailure(current: Throwable?, error: Throwable): Throwable {
        if (current == null) return error
        if (current !== error) current.addSuppressed(error)
        return current
    }

    private fun reportCleanupFailure(primary: Throwable?, cleanup: Throwable?) {
        if (cleanup == null) return
        if (primary != null) {
            if (primary !== cleanup) primary.addSuppressed(cleanup)
        } else {
            throw cleanup
        }
    }

    private fun assertActivePrefix(events: List<WireEvent>, requestId: DexRequestId) {
        assertTrue("The real session emitted too few lifecycle callbacks: $events", events.size >= 2)
        val started = (events[0] as? WireEvent.Started)?.value
        val validating = (events[1] as? WireEvent.Progress)?.value
        assertNotNull("The first real callback was not onStarted", started)
        assertNotNull("The second real callback was not validating progress", validating)
        started!!
        validating!!
        assertEquals(requestId, started.requestId)
        assertEquals(requestId, validating.requestId)
        assertEquals(0L, started.sequence)
        assertEquals(1L, validating.sequence)
        assertEquals(DexCompilerProgressStage.VALIDATING, validating.stage)
    }

    private fun assertTerminalDiscipline(callback: RecordingCallback, expectedTerminalCount: Int) {
        assertEquals("The real provider emitted the wrong number of terminals", expectedTerminalCount, callback.terminalCount())
        assertFalse("The real provider emitted a callback after its terminal", callback.callbackAfterTerminal())
        callback.throwIfCallbackFailed()
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private data class ProviderContract(
        val capabilities: DexCompilerCapabilities,
        val protocolVersion: org.autojs.plugin.dexcompiler.api.DexProtocolVersion,
    )

    private data class AdmittedPipeSession(
        val session: DirectPipeSession,
        val callback: RecordingCallback,
    )

    private class BoundProvider(
        private val context: Context,
        private val connection: ServiceConnection,
        val provider: IDexCompilerProvider,
    ) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) context.unbindService(connection)
        }
    }

    private class DirectPipeSession private constructor(
        val request: DexCompileRequest,
        private val session: IDexCompilerSession,
        private var programWriter: ParcelFileDescriptor?,
        private var outputReader: ParcelFileDescriptor?,
    ) : Closeable {

        fun cancelRemote() {
            session.cancel()
        }

        fun closeRemote() {
            session.close()
        }

        fun closeProgramWriter() {
            val writer = programWriter
            programWriter = null
            writer?.close()
        }

        fun assertOutputEof(label: String) {
            val reader = checkNotNull(outputReader) { "$label output reader was already consumed" }
            outputReader = null
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "dex-real-lifecycle-eof").apply { isDaemon = true }
            }
            val future = executor.submit<Int> {
                ParcelFileDescriptor.AutoCloseInputStream(reader).use { input -> input.read() }
            }
            try {
                assertEquals("$label output pipe did not reach EOF", -1, future.get(FD_EOF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            } finally {
                future.cancel(true)
                executor.shutdownNow()
                runCatching { reader.close() }
            }
        }

        override fun close() {
            close(remoteAlreadyDead = false)
        }

        fun close(remoteAlreadyDead: Boolean) {
            var failure: Throwable? = null
            if (!remoteAlreadyDead) {
                failure = capture(failure) { session.cancel() }
                failure = capture(failure) { session.close() }
            }
            val reader = outputReader
            outputReader = null
            val writer = programWriter
            programWriter = null
            failure = capture(failure) { writer?.close() }
            failure = capture(failure) { reader?.close() }
            failure?.let { throw it }
        }

        private fun capture(current: Throwable?, close: () -> Unit): Throwable? =
            try {
                close()
                current
            } catch (error: Throwable) {
                if (current == null) error else current.apply {
                    if (this !== error) addSuppressed(error)
                }
            }

        companion object {
            fun open(
                provider: IDexCompilerProvider,
                request: DexCompileRequest,
                callback: IDexCompilerCallback,
            ): DirectPipeSession {
                val program = ParcelFileDescriptor.createReliablePipe()
                val output = try {
                    ParcelFileDescriptor.createReliablePipe()
                } catch (error: Throwable) {
                    program.forEach { descriptor -> runCatching { descriptor.close() } }
                    throw error
                }
                try {
                    val remote = checkNotNull(
                        provider.openSession(
                            DexCompilerCodec.encodeRequest(request),
                            program[0],
                            output[1],
                            callback,
                        ),
                    ) { "The real provider returned a null session" }

                    // These are the caller's PFD instances. Closing them immediately proves that
                    // the cross-process provider duplicated ownership before scheduling work.
                    program[0].close()
                    output[1].close()
                    return DirectPipeSession(
                        request = request,
                        session = remote,
                        programWriter = program[1],
                        outputReader = output[0],
                    )
                } catch (error: Throwable) {
                    program.forEach { descriptor -> runCatching { descriptor.close() } }
                    output.forEach { descriptor -> runCatching { descriptor.close() } }
                    throw error
                }
            }
        }
    }

    private class RecordingCallback : IDexCompilerCallback.Stub() {
        private val lock = Any()
        private val events = mutableListOf<WireEvent>()
        private val firstEvent = CountDownLatch(1)
        private val validating = CountDownLatch(1)
        private val validatingOrTerminal = CountDownLatch(1)
        private val terminal = CountDownLatch(1)
        private val terminals = AtomicInteger(0)
        private val afterTerminal = AtomicBoolean(false)
        private val callbackFailure = AtomicReference<Throwable?>()

        override fun onStarted(metadata: ByteArray) = decodeAndRecord {
            WireEvent.Started(DexCompilerCodec.decodeStarted(metadata))
        }

        override fun onProgress(progress: ByteArray) = decodeAndRecord {
            WireEvent.Progress(DexCompilerCodec.decodeProgress(progress))
        }

        override fun onCompleted(result: ByteArray) = decodeAndRecord {
            WireEvent.Completed(DexCompilerCodec.decodeResult(result))
        }

        override fun onFailed(error: ByteArray) = decodeAndRecord {
            WireEvent.Failed(DexCompilerCodec.decodeError(error))
        }

        override fun onCancelled(cancellation: ByteArray) = decodeAndRecord {
            WireEvent.Cancelled(DexCompilerCodec.decodeCancellation(cancellation))
        }

        fun awaitValidating(timeoutMillis: Long): Boolean {
            val reached = validating.await(timeoutMillis, TimeUnit.MILLISECONDS)
            throwIfCallbackFailed()
            return reached
        }

        fun awaitFirstEvent(timeoutMillis: Long): Boolean {
            val reached = firstEvent.await(timeoutMillis, TimeUnit.MILLISECONDS)
            throwIfCallbackFailed()
            return reached
        }

        fun awaitValidatingOrTerminal(timeoutMillis: Long): Boolean {
            val reached = validatingOrTerminal.await(timeoutMillis, TimeUnit.MILLISECONDS)
            throwIfCallbackFailed()
            return reached
        }

        fun awaitTerminal(timeoutMillis: Long): Boolean {
            val reached = terminal.await(timeoutMillis, TimeUnit.MILLISECONDS)
            throwIfCallbackFailed()
            return reached
        }

        fun snapshot(): List<WireEvent> = synchronized(lock) { events.toList() }

        fun terminalCount(): Int = terminals.get()

        fun callbackAfterTerminal(): Boolean = afterTerminal.get()

        fun throwIfCallbackFailed() {
            callbackFailure.get()?.let { failure ->
                throw AssertionError("Unable to decode a real-provider callback").also { it.initCause(failure) }
            }
        }

        private fun decodeAndRecord(decode: () -> WireEvent) {
            try {
                record(decode())
            } catch (error: Throwable) {
                callbackFailure.compareAndSet(null, error)
                firstEvent.countDown()
                validating.countDown()
                validatingOrTerminal.countDown()
                terminal.countDown()
            }
        }

        private fun record(event: WireEvent) {
            val isTerminal = event is WireEvent.Completed || event is WireEvent.Failed || event is WireEvent.Cancelled
            synchronized(lock) {
                if (terminals.get() > 0) afterTerminal.set(true)
                events += event
                if (isTerminal) terminals.incrementAndGet()
            }
            firstEvent.countDown()
            if (event is WireEvent.Progress && event.value.stage == DexCompilerProgressStage.VALIDATING) {
                validating.countDown()
                validatingOrTerminal.countDown()
            }
            if (isTerminal) {
                validatingOrTerminal.countDown()
                terminal.countDown()
            }
        }
    }

    private sealed class WireEvent {
        data class Started(val value: DexCompileStarted) : WireEvent()
        data class Progress(val value: DexCompileProgress) : WireEvent()
        data class Completed(val value: DexCompileResult) : WireEvent()
        data class Failed(val value: DexCompileError) : WireEvent()
        data class Cancelled(val value: DexCompileCancellation) : WireEvent()
    }

    private companion object {
        val SERIAL_LOCK = Any()

        const val ARG_ENABLED = "autojs.dexCompiler.realProviderLifecycle.enabled"
        const val ARG_DEVICE_SERIAL = "autojs.dexCompiler.realProviderLifecycle.serial"
        const val ARG_FORCE_STOP_PACKAGE = "autojs.dexCompiler.realProviderLifecycle.forceStopPackage"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.dexcompiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.DexCompilerService"
        val REAL_COMPONENT = DexCompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        val REAL_COMPONENT_NAME = ComponentName(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        const val BIND_TIMEOUT_MILLIS = 10_000L
        const val CALLBACK_TIMEOUT_MILLIS = 10_000L
        const val BINDER_DEATH_TIMEOUT_MILLIS = 10_000L
        const val FD_EOF_TIMEOUT_MILLIS = 5_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 500L
        const val RECOVERY_TIMEOUT_MILLIS = 20_000L
        const val RECOVERY_BIND_SLICE_MILLIS = 3_000L
        const val RECOVERY_POLL_MILLIS = 250L
        const val GATE_REUSE_POLL_MILLIS = 100L
    }
}
