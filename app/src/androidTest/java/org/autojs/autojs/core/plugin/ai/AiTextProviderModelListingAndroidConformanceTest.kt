package org.autojs.autojs.core.plugin.ai

import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextMimeType
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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opt-in positive cross-process evidence for the default-off Android model-list transport. */
class AiTextProviderModelListingAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun exactComponentModelListingRevalidatesAndCollectsAllFakeModels() {
        requireConformanceEnvironment()
        val component = AiTextProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            namedThreadFactory("ai-text-listing-conformance-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("ai-text-listing-conformance-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("ai-text-listing-conformance-deadline"),
        )
        var listingHandle: Closeable = Closeable {}
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val outcome = AtomicReference<AiTextProviderModelListingRunOutcome>()
            val outcomeCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(true)
            val terminal = CountDownLatch(1)
            val coordinator = AiTextProviderModelListingCoordinator(
                transport = AndroidAiTextProviderModelListing(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                deadlineScheduler = scheduler.asModelListingDeadlineScheduler(),
                clock = AiTextProviderModelListingClock { android.os.SystemClock.elapsedRealtime() },
                owner = AiTextProviderModelListingProcessOwner(),
            )
            listingHandle = coordinator.start(
                AiTextProviderModelListingRunRequest(
                    snapshot = initial,
                    pinnedProvider = pinned,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = AiTextProviderModelListingRunOptions(
                        enabled = true,
                        pageSize = 1,
                        timeoutMillis = LISTING_TIMEOUT_MILLIS,
                    ),
                ),
            ) { value ->
                callbackOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                outcomeCount.incrementAndGet()
                if (outcome.compareAndSet(null, value)) terminal.countDown()
            }

            assertTrue(
                "AI text provider model listing did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val completed = outcome.get() as AiTextProviderModelListingRunOutcome.Completed
            assertEquals(pinned, completed.catalog.pinnedProvider)
            assertEquals(FAKE_LISTING_GENERATION, completed.catalog.listingGeneration)
            assertEquals(FAKE_MODEL_IDS, completed.catalog.models.map { it.modelId })
            assertFalse("Model-list completion ran on the Android main thread", callbackOnMain.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Model listing emitted more than one terminal outcome", 1, outcomeCount.get())
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
        } finally {
            listingHandle.close()
            worker.shutdownNow()
            blocking.shutdownNow()
            scheduler.shutdownNow()
            worker.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            blocking.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            scheduler.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun runHandshake(
        initial: AiTextProviderPackageSnapshot,
        worker: ThreadPoolExecutor,
        blocking: ThreadPoolExecutor,
        scheduler: ScheduledExecutorService,
    ): AiTextProviderPinnedIdentity {
        val outcome = AtomicReference<AiTextProviderHandshakeOutcome>()
        val terminal = CountDownLatch(1)
        val coordinator = AiTextProviderHandshakeCoordinator(
            transport = AndroidAiTextProviderHandshake(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asHandshakeDeadlineScheduler(),
            gate = AiTextProviderHandshakeGate(),
        )
        val handle = coordinator.start(
            AiTextProviderHandshakeRequest(
                snapshot = initial,
                environment = environment(),
                hostCapabilities = hostCapabilities(),
                options = AiTextProviderHandshakeOptions(
                    enabled = true,
                    timeoutMillis = HANDSHAKE_TIMEOUT_MILLIS,
                ),
            ),
        ) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        try {
            assertTrue(
                "AI text provider metadata handshake did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            return (outcome.get() as AiTextProviderHandshakeOutcome.Selected).pinned
        } finally {
            handle.close()
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "AI text model-list Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("AI text conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "AI text fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    private fun discoverExact(component: AiTextProviderComponent): AiTextProviderPackageSnapshot {
        val result = AiTextProviderDiscoveryPolicy.inspectExact(
            source = AndroidAiTextProviderDiscovery(context),
            component = component,
            options = AiTextProviderDiscoveryOptions(enabled = true),
        ) as AiTextProviderDiscoveryResult.Completed
        return (result.evaluations.single() as AiTextProviderDiscoveryEvaluation.Discovered).snapshot
    }

    private fun environment() = AiTextProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = false,
    )

    private fun hostCapabilities() = AiTextCapabilities(
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
        acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiTextMimeType.JSON),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asHandshakeDeadlineScheduler() =
        AiTextProviderHandshakeDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun ScheduledExecutorService.asModelListingDeadlineScheduler() =
        AiTextProviderModelListingDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private companion object {
        const val ARG_ENABLED = "autojs.aiText.modelListing.conformance.enabled"
        const val FAKE_PACKAGE = "org.autojs.plugin.ai.text.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService"
        const val FAKE_LISTING_GENERATION = "fake-models-v1"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val LISTING_TIMEOUT_MILLIS = 20_000L
        const val OUTCOME_WAIT_MILLIS = 30_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        val FAKE_MODEL_IDS = listOf(
            "fake.echo",
            "fake.stream",
            "fake.reasoning",
            "fake.tools",
        )
    }
}
