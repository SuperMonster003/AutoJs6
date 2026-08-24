package org.autojs.autojs.core.plugin.ai

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opt-in positive cross-process evidence for the default-off metadata-only handshake. */
class AiProviderAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun exactComponentHandshakeDecodesAndPinsFakeProviderMetadata() {
        requireConformanceEnvironment()
        val component = AiProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        assertTrue(initial.sameSignerAsHost)
        assertEquals(AiProviderProtocol.PLUGIN_PERMISSION, initial.requiredPermission)

        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("ai-provider-conformance-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            0,
            1,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            namedThreadFactory("ai-provider-conformance-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("ai-provider-conformance-deadline"),
        )
        val outcome = AtomicReference<AiProviderHandshakeOutcome>()
        val outcomeCount = AtomicInteger()
        val callbackOnMain = AtomicBoolean(true)
        val terminal = CountDownLatch(1)
        val coordinator = AiProviderHandshakeCoordinator(
            transport = AndroidAiProviderHandshake(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asHandshakeDeadlineScheduler(),
            gate = AiProviderHandshakeGate(),
        )
        val handle = coordinator.start(
            AiProviderHandshakeRequest(
                snapshot = initial,
                environment = AiProviderEnvironment(
                    hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
                    releaseBuild = false,
                ),
                hostCapabilities = hostCapabilities(),
                options = AiProviderHandshakeOptions(
                    enabled = true,
                    timeoutMillis = HANDSHAKE_TIMEOUT_MILLIS,
                ),
            ),
        ) { value ->
            callbackOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            outcomeCount.incrementAndGet()
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }

        try {
            assertTrue(
                "local AI provider metadata handshake did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val selected = outcome.get() as AiProviderHandshakeOutcome.Selected
            assertEquals(component, selected.pinned.component)
            assertEquals(FAKE_PROVIDER_ID, selected.pinned.providerId)
            assertEquals(1L, selected.pinned.implementationVersionCode)
            assertEquals(AiDataLocality.ON_DEVICE, selected.pinned.locality)
            assertEquals(AiCredentialMode.NONE, selected.pinned.credentialMode)
            assertTrue(selected.pinned.negotiatedCapabilities.supportsStreaming)
            assertTrue(selected.pinned.negotiatedCapabilities.supportsTools)
            assertTrue(selected.pinned.negotiatedCapabilities.supportsUsage)
            assertFalse("Handshake completion ran on the Android main thread", callbackOnMain.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Handshake emitted more than one terminal outcome", 1, outcomeCount.get())
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
        } finally {
            handle.close()
            worker.shutdownNow()
            blocking.shutdownNow()
            scheduler.shutdownNow()
            worker.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            blocking.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            scheduler.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "local AI Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("local AI conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "local AI fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    private fun discoverExact(component: AiProviderComponent): AiProviderPackageSnapshot {
        val result = AiProviderDiscoveryPolicy.inspectExact(
            source = AndroidAiProviderDiscovery(context),
            component = component,
            options = AiProviderDiscoveryOptions(enabled = true),
        ) as AiProviderDiscoveryResult.Completed
        val discovered = result.evaluations.single() as AiProviderDiscoveryEvaluation.Discovered
        return discovered.snapshot
    }

    private fun hostCapabilities() = AiProviderCapabilities(
        supportsStreaming = true,
        supportsReasoning = true,
        supportsTools = true,
        supportsStructuredJson = true,
        supportsUsage = true,
        maximumMessages = 128,
        maximumContentParts = 256,
        maximumToolDefinitions = 32,
        maximumContextBytes = 2L * 1024L * 1024L,
        maximumOutputBytes = 2L * 1024L * 1024L,
        maximumToolRounds = 8,
        maximumOutstandingToolCalls = 16,
        maximumRequestDescriptors = 16,
        maximumSessionDescriptors = 32,
        acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asHandshakeDeadlineScheduler() =
        AiProviderHandshakeDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private companion object {
        const val ARG_ENABLED = "autojs.aiProvider.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.ai.provider.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ai.provider.fake.service.FakeAiProviderService"
        const val FAKE_PROVIDER_ID = "fake.local"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val OUTCOME_WAIT_MILLIS = 25_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
    }
}
