package org.autojs.autojs.core.plugin.r8

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.r8compiler.api.IR8CompilerCallback
import org.autojs.plugin.r8compiler.api.IR8CompilerProvider
import org.autojs.plugin.r8compiler.api.IR8CompilerSession
import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8Cancellation
import org.autojs.plugin.r8compiler.api.R8CancellationReason
import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerFamily
import org.autojs.plugin.r8compiler.api.R8CompilerIntent
import org.autojs.plugin.r8compiler.api.R8CompilerProfile
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8Error
import org.autojs.plugin.r8compiler.api.R8ErrorCode
import org.autojs.plugin.r8compiler.api.R8FailurePhase
import org.autojs.plugin.r8compiler.api.R8FallbackPolicy
import org.autojs.plugin.r8compiler.api.R8InputBundleCodec
import org.autojs.plugin.r8compiler.api.R8InputIdentity
import org.autojs.plugin.r8compiler.api.R8InputLayout
import org.autojs.plugin.r8compiler.api.R8InputRole
import org.autojs.plugin.r8compiler.api.R8InputSetFingerprint
import org.autojs.plugin.r8compiler.api.R8InputSource
import org.autojs.plugin.r8compiler.api.R8OutputLayout
import org.autojs.plugin.r8compiler.api.R8Progress
import org.autojs.plugin.r8compiler.api.R8ProgressStage
import org.autojs.plugin.r8compiler.api.R8RequestId
import org.autojs.plugin.r8compiler.api.R8RequestedArtifact
import org.autojs.plugin.r8compiler.api.R8Result
import org.autojs.plugin.r8compiler.api.R8Sha256
import org.autojs.plugin.r8compiler.api.R8Started
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in lifecycle and hostile-input acceptance against the independently installed real R8 APK.
 *
 * The blocked sessions below use real reliable pipes and the production process-wide provider
 * gate; no provider test hook or fake transport is involved. Process death requires a second,
 * package-exact consent argument and always restores an authenticated, bindable provider before
 * the test returns.
 */
class R8CompilerRealProviderLifecycleAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun blockedSessionRejectsBusyCancelsOwnsDescriptorsAndFailsHostileBundle() =
        synchronized(SERIAL_LOCK) {
            val environment = requireLifecycleEnvironment(requireForceStopConsent = false)
            bindProvider(environment.identity).use { binding ->
                val contract = readContract(binding.provider, environment.identity)
                val firstCallback = RecordingCallback()
                val first = DirectPipeSession.open(
                    provider = binding.provider,
                    fixture = requestFixture(contract, "active"),
                    callback = firstCallback,
                )
                var busy: DirectPipeSession? = null
                var hostile: DirectPipeSession? = null
                var primaryFailure: Throwable? = null
                try {
                    assertTrue(
                        "The real R8 provider never entered input validation for the blocked session",
                        firstCallback.awaitValidating(CALLBACK_TIMEOUT_MILLIS),
                    )
                    firstCallback.throwIfCallbackFailed()
                    assertActivePrefix(firstCallback.snapshot(), first.fixture.request.requestId)

                    val busyCallback = RecordingCallback()
                    val busySession = DirectPipeSession.open(
                        provider = binding.provider,
                        fixture = requestFixture(contract, "busy"),
                        callback = busyCallback,
                    )
                    busy = busySession
                    assertTrue(
                        "The second real R8 session did not receive a BUSY terminal",
                        busyCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                    )
                    busyCallback.throwIfCallbackFailed()
                    busySession.closeRemote()
                    busySession.cancelRemote()
                    busySession.closeProgramWriter()
                    busySession.assertOutputEof("BUSY R8 session")
                    SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)

                    val busyEvents = busyCallback.snapshot()
                    assertEquals("BUSY emitted callbacks other than its terminal", 1, busyEvents.size)
                    val busyFailure = (busyEvents.single() as? WireEvent.Failed)?.value
                    assertNotNull("The second real R8 session did not fail structurally", busyFailure)
                    busyFailure!!
                    assertEquals(busySession.fixture.request.requestId, busyFailure.requestId)
                    assertEquals(R8ErrorCode.BUSY, busyFailure.code)
                    assertEquals(R8FailurePhase.NEGOTIATION, busyFailure.phase)
                    assertTerminalDiscipline(busyCallback, expectedTerminalCount = 1)

                    // Repeated one-way cancel/close calls must converge on the first cancellation
                    // terminal and release every provider-owned descriptor exactly once.
                    first.cancelRemote()
                    first.closeRemote()
                    first.cancelRemote()
                    assertTrue(
                        "The active real R8 session did not emit its cancellation terminal",
                        firstCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                    )
                    firstCallback.throwIfCallbackFailed()
                    first.closeProgramWriter()
                    first.assertOutputEof("cancelled active R8 session")
                    SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)

                    val firstEvents = firstCallback.snapshot()
                    assertActivePrefix(firstEvents, first.fixture.request.requestId)
                    assertEquals("The cancelled R8 session emitted an unexpected callback count", 3, firstEvents.size)
                    val cancellation = (firstEvents.last() as? WireEvent.Cancelled)?.value
                    assertNotNull("The active real R8 session did not terminate as cancelled", cancellation)
                    cancellation!!
                    assertEquals(first.fixture.request.requestId, cancellation.requestId)
                    assertEquals(R8CancellationReason.REQUESTED, cancellation.reason)
                    assertEquals(R8FailurePhase.CLEANUP, cancellation.phase)
                    assertTerminalDiscipline(firstCallback, expectedTerminalCount = 1)

                    // Polling through strict BUSY terminals proves the process gate is reusable.
                    // The newly admitted session then receives a deliberately non-canonical bundle.
                    val admitted = awaitGateAdmission(binding.provider, contract)
                    val hostileCallback = admitted.callback
                    val hostileSession = admitted.session
                    hostile = hostileSession
                    hostileCallback.throwIfCallbackFailed()
                    assertActivePrefix(hostileCallback.snapshot(), hostileSession.fixture.request.requestId)
                    hostileSession.writeProgramAndClose(HOSTILE_INPUT_BUNDLE)
                    assertTrue(
                        "The hostile real R8 input did not produce a terminal",
                        hostileCallback.awaitTerminal(CALLBACK_TIMEOUT_MILLIS),
                    )
                    hostileCallback.throwIfCallbackFailed()
                    hostileSession.cancelRemote()
                    hostileSession.closeRemote()
                    hostileSession.assertOutputEof("hostile-input R8 session")
                    SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)

                    val hostileEvents = hostileCallback.snapshot()
                    assertActivePrefix(hostileEvents, hostileSession.fixture.request.requestId)
                    assertEquals("The hostile R8 session emitted an unexpected callback count", 3, hostileEvents.size)
                    val hostileFailure = (hostileEvents.last() as? WireEvent.Failed)?.value
                    assertNotNull("The hostile R8 bundle did not fail structurally", hostileFailure)
                    hostileFailure!!
                    assertEquals(hostileSession.fixture.request.requestId, hostileFailure.requestId)
                    assertEquals(R8ErrorCode.INVALID_BUNDLE, hostileFailure.code)
                    assertEquals(R8FailurePhase.INPUT_VALIDATION, hostileFailure.phase)
                    assertTerminalDiscipline(hostileCallback, expectedTerminalCount = 1)

                    sendEvidence(
                        environment,
                        roadmapStage = "G6-REAL-R8-LIFECYCLE",
                        values = mapOf(
                            "crossApkBinder" to true,
                            "blockedInputPipe" to true,
                            "busyRejected" to true,
                            "singleTerminal" to true,
                            "idempotentCancelClose" to true,
                            "calleeDescriptorOwnership" to true,
                            "outputPipeEof" to true,
                            "sessionGateRecovered" to true,
                            "hostileBundleRejected" to true,
                            "hostileErrorCode" to hostileFailure.code.name,
                            "hostileFailurePhase" to hostileFailure.phase.name,
                        ),
                    )
                } catch (error: Throwable) {
                    primaryFailure = error
                    throw error
                } finally {
                    var cleanupFailure: Throwable? = null
                    cleanupFailure = captureCleanupFailure(cleanupFailure) { hostile?.close() }
                    cleanupFailure = captureCleanupFailure(cleanupFailure) { busy?.close() }
                    cleanupFailure = captureCleanupFailure(cleanupFailure) { first.close() }
                    reportCleanupFailure(primaryFailure, cleanupFailure)
                }
            }
        }

    @Test
    fun forceStoppedProviderSignalsBinderDeathClosesPipesAndRecovers() = synchronized(SERIAL_LOCK) {
        val environment = requireLifecycleEnvironment(requireForceStopConsent = true)
        var binding: BoundProvider? = null
        var session: DirectPipeSession? = null
        var linkedBinder: IBinder? = null
        var deathRecipient: IBinder.DeathRecipient? = null
        var forceStopIssued = false
        var binderDeathObserved = false
        var primaryFailure: Throwable? = null
        try {
            val activeBinding = bindProvider(environment.identity)
            binding = activeBinding
            val contract = readContract(activeBinding.provider, environment.identity)
            val callback = RecordingCallback()
            val activeSession = DirectPipeSession.open(
                provider = activeBinding.provider,
                fixture = requestFixture(contract, "binder-death"),
                callback = callback,
            )
            session = activeSession
            assertTrue(
                "The real R8 provider never entered input validation before force-stop",
                callback.awaitValidating(CALLBACK_TIMEOUT_MILLIS),
            )
            callback.throwIfCallbackFailed()
            assertActivePrefix(callback.snapshot(), activeSession.fixture.request.requestId)

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
                "The real R8 provider Binder did not die after its package-exact force-stop",
                binderDeath.await(BINDER_DEATH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertFalse("The force-stopped real R8 provider Binder still reports alive", binder.isBinderAlive)
            binderDeathObserved = true
            activeSession.closeProgramWriter()
            activeSession.assertOutputEof("force-stopped R8 session")
            SystemClock.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            callback.throwIfCallbackFailed()
            assertEquals("The real R8 provider delivered duplicate Binder-death notifications", 1, binderDeathCount.get())

            // Process death is a transport terminal. The dead provider must not forge a callback
            // completion, failure, or cancellation after the host observes Binder death.
            assertEquals("Provider death unexpectedly emitted an AIDL callback terminal", 0, callback.terminalCount())
            assertFalse("A callback arrived after a terminal during provider death", callback.callbackAfterTerminal())
            assertActivePrefix(callback.snapshot(), activeSession.fixture.request.requestId)
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
                        "The live R8 provider Binder did not own the registered death recipient"
                    }
                }
            }
            cleanupFailure = captureCleanupFailure(cleanupFailure) { binding?.close() }
            if (forceStopIssued) {
                val recoveryFailure = runCatching { recoverProvider(environment.identity) }.exceptionOrNull()
                if (recoveryFailure != null) cleanupFailure = appendFailure(cleanupFailure, recoveryFailure)
            }
            reportCleanupFailure(primaryFailure, cleanupFailure)
        }

        sendEvidence(
            environment,
            roadmapStage = "G6-REAL-R8-PROCESS-DEATH",
            values = mapOf(
                "crossApkBinder" to true,
                "packageExactForceStop" to true,
                "binderDeathObserved" to true,
                "providerCallbackTerminalCount" to 0,
                "outputPipeEof" to true,
                "identityRevalidated" to true,
                "authenticatedRebind" to true,
                "providerRecovered" to true,
            ),
        )
    }

    private fun requireLifecycleEnvironment(requireForceStopConsent: Boolean): LifecycleEnvironment {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real R8 lifecycle instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertTrue("Real R8 lifecycle instrumentation requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals("Lifecycle instrumentation must target canonical AutoJs6", HOST_PACKAGE, context.packageName)

        val expectedIdentity = arguments.getString(ARG_DEVICE_IDENTITY).orEmpty().trim()
        assertTrue("Pass the exact device identity through -e $ARG_DEVICE_IDENTITY", expectedIdentity.isNotEmpty())
        val actualIdentity = currentDeviceIdentity()
        assertEquals("Lifecycle instrumentation is running on a different device", expectedIdentity, actualIdentity)
        val expectedSdk = arguments.getString(ARG_DEVICE_SDK)?.toIntOrNull()
        assertEquals("Pass a numeric device SDK through -e $ARG_DEVICE_SDK", Build.VERSION.SDK_INT, expectedSdk)
        if (requireForceStopConsent) {
            assertEquals(
                "Binder-death coverage requires consent for the exact package being force-stopped",
                REAL_PLUGIN_PACKAGE,
                arguments.getString(ARG_FORCE_STOP_PACKAGE),
            )
        }

        val packageManager = context.packageManager
        try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(REAL_PLUGIN_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real R8 compiler package is not installed: $REAL_PLUGIN_PACKAGE").also {
                it.initCause(error)
            }
        }
        assertEquals(
            "The lifecycle test requires the real R8 provider and host to have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )

        val candidates = AndroidR8CompilerTransport(context)
            .discoverExactAction(R8CompilerContract.SERVICE_ACTION)
            .filter { candidate -> candidate.component.packageName == REAL_PLUGIN_PACKAGE }
        assertEquals(
            "The real package must expose exactly the expected R8 component",
            listOf(REAL_COMPONENT),
            candidates.map(R8CompilerServiceCandidate::component),
        )
        val candidate = candidates.single()
        assertTrue("The real R8 provider is not enabled", candidate.applicationEnabled && candidate.serviceEnabled)
        assertTrue("The real R8 provider is not exported", candidate.exported)
        assertEquals(R8CompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertTrue("The transport did not confirm the host/provider signer match", candidate.sameSignerAsHost)
        assertTrue(
            "The real R8 provider is rejected by the production host policy",
            R8CompilerSelectionPolicy.rejection(candidate) == null,
        )
        return LifecycleEnvironment(actualIdentity, candidate.pin())
    }

    private fun readContract(
        provider: IR8CompilerProvider,
        identity: R8CompilerPinnedIdentity,
    ): R8CompilerNegotiation {
        val negotiation = R8CompilerProtocolGate.negotiate(
            identity = identity,
            currentCandidate = AndroidR8CompilerTransport(context).inspectExact(REAL_COMPONENT),
            encodedInfo = provider.compilerInfo,
            encodedCapabilities = provider.capabilities,
            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
            minApi = minOf(Build.VERSION.SDK_INT, R8CompilerContract.MAX_SUPPORTED_API),
        )
        assertEquals(R8CompilerContract.MAX_CONCURRENT_SESSIONS, negotiation.capabilities.limits.maxConcurrentSessions)
        assertEquals("8.13.17", negotiation.capabilities.compilerVersion)
        return negotiation
    }

    private fun requestFixture(contract: R8CompilerNegotiation, label: String): RequestFixture {
        val program = "real-r8-lifecycle-$label-${UUID.randomUUID()}".toByteArray(StandardCharsets.UTF_8)
        val keepRules = "-keep class org.autojs.fixture.r8compiler.Lifecycle { public *; }\n"
            .toByteArray(StandardCharsets.UTF_8)
        val identities = listOf(
            R8InputIdentity(
                role = R8InputRole.PROGRAM_JAR,
                ordinal = 0,
                ownerClasspathOrdinal = -1,
                sizeBytes = program.size.toLong(),
                contentSha256 = R8Sha256.digest(program),
                ownerClasspathSha256 = R8Sha256.ZERO,
            ),
            R8InputIdentity(
                role = R8InputRole.KEEP_RULES,
                ordinal = 0,
                ownerClasspathOrdinal = -1,
                sizeBytes = keepRules.size.toLong(),
                contentSha256 = R8Sha256.digest(keepRules),
                ownerClasspathSha256 = R8Sha256.ZERO,
            ),
        )
        val sources = listOf(
            R8InputSource(identities[0]) { ByteArrayInputStream(program) },
            R8InputSource(identities[1]) { ByteArrayInputStream(keepRules) },
        )
        val canonicalBundle = ByteArrayOutputStream()
        val summary = R8InputBundleCodec.write(
            canonicalBundle,
            sources,
            contract.capabilities,
        )
        val capabilities = contract.capabilities
        val request = R8CompileRequest(
            requestId = R8RequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = R8CompilerContract.PROTOCOL_V1,
            compilerFamily = R8CompilerFamily.R8,
            compilerIntent = R8CompilerIntent.R8_EXPLICIT,
            fallbackPolicy = R8FallbackPolicy.NONE,
            profile = R8CompilerProfile.FULL_RELEASE,
            minApi = minOf(Build.VERSION.SDK_INT, R8CompilerContract.MAX_SUPPORTED_API),
            inputLayout = R8InputLayout.PROGRAM_CLASSPATH_AND_RULES_BUNDLE_V1,
            inputIdentities = summary.identities,
            inputSetFingerprint = R8InputSetFingerprint.compute(summary.identities),
            inputBundleSizeBytes = summary.sizeBytes,
            inputBundleSha256 = summary.contentSha256,
            runtimeLibraryModel = capabilities.runtimeLibraryModel,
            expectedRuntimeLibraryFingerprint = capabilities.runtimeLibraryFingerprint,
            expectedCapabilityFingerprint = capabilities.capabilityFingerprint,
            canonicalizationPolicyVersion = capabilities.canonicalizationPolicyVersion,
            rulePolicyVersion = capabilities.rulePolicyVersion,
            outputLayout = R8OutputLayout.DEX_AND_REPORTS_BUNDLE_V1,
            requestedArtifacts = R8ArtifactRole.values().map { role ->
                R8RequestedArtifact(role, maximumFor(capabilities, role))
            },
            maxOutputBundleBytes = capabilities.limits.maxOutputBundleBytes,
            diagnosticByteLimit = capabilities.limits.maxDiagnosticBytes,
            timeoutMillis = capabilities.limits.defaultTimeoutMillis,
        )
        R8CompilerValidation.validateRequestAgainst(request, capabilities)
        return RequestFixture(request, canonicalBundle.toByteArray())
    }

    private fun maximumFor(capabilities: R8CompilerCapabilities, role: R8ArtifactRole): Long =
        when (role) {
            R8ArtifactRole.DEX_ZIP -> capabilities.limits.maxDexZipBytes
            R8ArtifactRole.MAPPING_TEXT -> capabilities.limits.maxMappingBytes
            R8ArtifactRole.SEEDS_TEXT -> capabilities.limits.maxSeedsBytes
            R8ArtifactRole.USAGE_TEXT -> capabilities.limits.maxUsageBytes
            R8ArtifactRole.RETRACE_METADATA -> capabilities.limits.maxRetraceMetadataBytes
        }

    private fun bindProvider(
        identity: R8CompilerPinnedIdentity,
        timeoutMillis: Long = BIND_TIMEOUT_MILLIS,
    ): BoundProvider {
        check(identity.component == REAL_COMPONENT) { "Refusing to bind an unexpected R8 compiler identity" }
        val connected = CountDownLatch(1)
        val provider = AtomicReference<IR8CompilerProvider?>()
        val failure = AtomicReference<Throwable?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (name != REAL_COMPONENT_NAME) {
                    failure.compareAndSet(null, SecurityException("Unexpected real R8 provider component: $name"))
                } else {
                    provider.compareAndSet(null, IR8CompilerProvider.Stub.asInterface(service))
                }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit

            override fun onBindingDied(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("Real R8 binding died before connection"))
                connected.countDown()
            }

            override fun onNullBinding(name: ComponentName) {
                failure.compareAndSet(null, IllegalStateException("Real R8 provider returned a null binding"))
                connected.countDown()
            }
        }
        val intent = Intent(R8CompilerContract.SERVICE_ACTION).apply {
            component = REAL_COMPONENT_NAME
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val registered = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!registered) throw IllegalStateException("Unable to bind the exact real R8 compiler component")
        try {
            if (!connected.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw TimeoutException("Timed out binding the real R8 compiler")
            }
            failure.get()?.let { throw it }
            return BoundProvider(
                context = context,
                connection = connection,
                provider = checkNotNull(provider.get()) { "Real R8 binding completed without a Binder" },
            )
        } catch (error: Throwable) {
            runCatching { context.unbindService(connection) }
            throw error
        }
    }

    private fun recoverProvider(identity: R8CompilerPinnedIdentity) {
        var lastFailure: Throwable? = null
        val deadline = SystemClock.elapsedRealtime() + RECOVERY_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val recoveredIdentity = AndroidR8CompilerTransport(context)
                    .inspectExact(REAL_COMPONENT)
                    ?.pin()
                assertEquals("The real R8 identity changed during force-stop recovery", identity, recoveredIdentity)
                bindProvider(identity, RECOVERY_BIND_SLICE_MILLIS).use { recovered ->
                    readContract(recovered.provider, identity)
                    assertTrue("Recovered real R8 Binder is not alive", recovered.provider.asBinder().isBinderAlive)
                }
                return
            } catch (error: Throwable) {
                lastFailure = error
                SystemClock.sleep(RECOVERY_POLL_MILLIS)
            }
        }
        throw AssertionError("The force-stopped real R8 provider could not be restored and rebound").also { failure ->
            lastFailure?.let(failure::initCause)
        }
    }

    private fun awaitGateAdmission(
        provider: IR8CompilerProvider,
        contract: R8CompilerNegotiation,
    ): AdmittedPipeSession {
        val deadline = SystemClock.elapsedRealtime() + CALLBACK_TIMEOUT_MILLIS
        var attempt = 0
        var lastBusy: R8Error? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            attempt++
            val callback = RecordingCallback()
            val session = DirectPipeSession.open(
                provider = provider,
                fixture = requestFixture(contract, "reused-after-cancel-$attempt"),
                callback = callback,
            )
            var admitted = false
            var attemptFailure: Throwable? = null
            try {
                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                if (!callback.awaitFirstEvent(remaining)) {
                    throw TimeoutException("R8 gate-reuse attempt emitted no callback before the deadline")
                }
                callback.throwIfCallbackFailed()
                when (val first = callback.snapshot().firstOrNull()) {
                    is WireEvent.Started -> {
                        val outcomeRemaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
                        if (!callback.awaitValidatingOrTerminal(outcomeRemaining)) {
                            throw TimeoutException("Started R8 gate-reuse attempt reached neither VALIDATING nor terminal")
                        }
                        callback.throwIfCallbackFailed()
                        val outcome = callback.snapshot()
                        val validating = (outcome.getOrNull(1) as? WireEvent.Progress)?.value
                        if (
                            outcome.size != 2 ||
                            validating?.stage != R8ProgressStage.VALIDATING ||
                            callback.terminalCount() != 0
                        ) {
                            throw AssertionError("Started R8 gate-reuse attempt terminated or reordered callbacks: $outcome")
                        }
                        assertActivePrefix(outcome, session.fixture.request.requestId)
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
                            failure.requestId != session.fixture.request.requestId ||
                            failure.code != R8ErrorCode.BUSY ||
                            failure.phase != R8FailurePhase.NEGOTIATION
                        ) {
                            throw AssertionError("R8 gate-reuse attempt did not produce one strict BUSY terminal: $events")
                        }
                        session.closeProgramWriter()
                        session.assertOutputEof("BUSY R8 gate-reuse attempt")
                        assertTerminalDiscipline(callback, expectedTerminalCount = 1)
                        lastBusy = failure
                    }
                    null -> throw AssertionError("R8 gate-reuse callback latch opened without an event")
                    else -> throw AssertionError("R8 gate-reuse attempt emitted an invalid first callback: $first")
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
            "The real R8 provider did not release its process session gate after cancellation; last BUSY=$lastBusy",
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

    private fun assertActivePrefix(events: List<WireEvent>, requestId: R8RequestId) {
        assertTrue("The real R8 session emitted too few lifecycle callbacks: $events", events.size >= 2)
        val started = (events[0] as? WireEvent.Started)?.value
        val validating = (events[1] as? WireEvent.Progress)?.value
        assertNotNull("The first real R8 callback was not onStarted", started)
        assertNotNull("The second real R8 callback was not validating progress", validating)
        started!!
        validating!!
        assertEquals(requestId, started.requestId)
        assertEquals(requestId, validating.requestId)
        assertEquals(0L, started.sequence)
        assertEquals(1L, validating.sequence)
        assertEquals(R8ProgressStage.VALIDATING, validating.stage)
    }

    private fun assertTerminalDiscipline(callback: RecordingCallback, expectedTerminalCount: Int) {
        assertEquals("The real R8 provider emitted the wrong number of terminals", expectedTerminalCount, callback.terminalCount())
        assertFalse("The real R8 provider emitted a callback after its terminal", callback.callbackAfterTerminal())
        callback.throwIfCallbackFailed()
    }

    private fun currentDeviceIdentity(): String {
        val serial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device exposed no stable serial property", serial.isNotEmpty())
        val avdName = executeShellCommand("getprop ro.boot.qemu.avd_name").trim().ifEmpty {
            executeShellCommand("getprop ro.kernel.qemu.avd_name").trim()
        }
        return if (avdName.isEmpty()) serial else "$serial@$avdName"
    }

    private fun executeShellCommand(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }

    private fun sendEvidence(
        environment: LifecycleEnvironment,
        roadmapStage: String,
        values: Map<String, Any>,
    ) {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("roadmapStage", roadmapStage)
            .put("status", "PASS")
            .put("deviceIdentity", environment.deviceIdentity)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("hostPackage", context.packageName)
            .put("hostVersionCode", BuildConfig.VERSION_CODE)
            .put("providerPackage", REAL_PLUGIN_PACKAGE)
            .put("providerComponent", "${REAL_COMPONENT.packageName}/${REAL_COMPONENT.className}")
            .put("providerVersionCode", environment.identity.versionCode)
            .put("providerSignerSha256", environment.identity.signerSha256.single())
        values.forEach(json::put)
        instrumentation.sendStatus(0, Bundle().apply {
            putString(STATUS_RESULT_KEY, json.toString())
        })
    }

    private data class LifecycleEnvironment(
        val deviceIdentity: String,
        val identity: R8CompilerPinnedIdentity,
    )

    private data class RequestFixture(
        val request: R8CompileRequest,
        val canonicalBundle: ByteArray,
    )

    private data class AdmittedPipeSession(
        val session: DirectPipeSession,
        val callback: RecordingCallback,
    )

    private class BoundProvider(
        private val context: Context,
        private val connection: ServiceConnection,
        val provider: IR8CompilerProvider,
    ) : Closeable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) context.unbindService(connection)
        }
    }

    private class DirectPipeSession private constructor(
        val fixture: RequestFixture,
        private val session: IR8CompilerSession,
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

        fun writeProgramAndClose(bytes: ByteArray) {
            val writer = checkNotNull(programWriter) { "R8 program writer was already consumed" }
            programWriter = null
            ParcelFileDescriptor.AutoCloseOutputStream(writer).use { output ->
                output.write(bytes)
                output.flush()
            }
        }

        fun assertOutputEof(label: String) {
            val reader = checkNotNull(outputReader) { "$label output reader was already consumed" }
            outputReader = null
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "r8-real-lifecycle-eof").apply { isDaemon = true }
            }
            val future = executor.submit<Int> {
                ParcelFileDescriptor.AutoCloseInputStream(reader).use { input -> input.read() }
            }
            try {
                assertEquals(
                    "$label output pipe did not reach EOF",
                    -1,
                    future.get(FD_EOF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                )
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
                provider: IR8CompilerProvider,
                fixture: RequestFixture,
                callback: IR8CompilerCallback,
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
                            R8CompilerCodec.encodeRequest(fixture.request),
                            program[0],
                            output[1],
                            callback,
                        ),
                    ) { "The real R8 provider returned a null session" }

                    // Closing the caller's passed PFD instances immediately proves that the
                    // cross-process provider duplicated ownership before scheduling its work.
                    program[0].close()
                    output[1].close()
                    return DirectPipeSession(
                        fixture = fixture,
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

    private class RecordingCallback : IR8CompilerCallback.Stub() {
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
            WireEvent.Started(R8CompilerCodec.decodeStarted(metadata))
        }

        override fun onProgress(progress: ByteArray) = decodeAndRecord {
            WireEvent.Progress(R8CompilerCodec.decodeProgress(progress))
        }

        override fun onCompleted(result: ByteArray) = decodeAndRecord {
            WireEvent.Completed(R8CompilerCodec.decodeResult(result))
        }

        override fun onFailed(error: ByteArray) = decodeAndRecord {
            WireEvent.Failed(R8CompilerCodec.decodeError(error))
        }

        override fun onCancelled(cancellation: ByteArray) = decodeAndRecord {
            WireEvent.Cancelled(R8CompilerCodec.decodeCancellation(cancellation))
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
                throw AssertionError("Unable to decode a real R8 provider callback").also { it.initCause(failure) }
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
            val isTerminal = event is WireEvent.Completed ||
                event is WireEvent.Failed ||
                event is WireEvent.Cancelled
            synchronized(lock) {
                if (terminals.get() > 0) afterTerminal.set(true)
                events += event
                if (isTerminal) terminals.incrementAndGet()
            }
            firstEvent.countDown()
            if (event is WireEvent.Progress && event.value.stage == R8ProgressStage.VALIDATING) {
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
        data class Started(val value: R8Started) : WireEvent()
        data class Progress(val value: R8Progress) : WireEvent()
        data class Completed(val value: R8Result) : WireEvent()
        data class Failed(val value: R8Error) : WireEvent()
        data class Cancelled(val value: R8Cancellation) : WireEvent()
    }

    private companion object {
        val SERIAL_LOCK = Any()

        const val ARG_ENABLED = "autojs.r8Compiler.realProviderLifecycle.enabled"
        const val ARG_DEVICE_IDENTITY = "autojs.r8Compiler.realProviderLifecycle.deviceIdentity"
        const val ARG_DEVICE_SDK = "autojs.r8Compiler.realProviderLifecycle.sdk"
        const val ARG_FORCE_STOP_PACKAGE = "autojs.r8Compiler.realProviderLifecycle.forceStopPackage"
        const val STATUS_RESULT_KEY = "autojs.r8Compiler.realProviderLifecycle.result"

        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.r8compiler"
        const val REAL_SERVICE_CLASS = "$REAL_PLUGIN_PACKAGE.R8CompilerService"
        val REAL_COMPONENT = R8CompilerComponent(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)
        val REAL_COMPONENT_NAME = ComponentName(REAL_PLUGIN_PACKAGE, REAL_SERVICE_CLASS)

        val HOSTILE_INPUT_BUNDLE = "not-a-canonical-r8-input-bundle".toByteArray(StandardCharsets.US_ASCII)
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
