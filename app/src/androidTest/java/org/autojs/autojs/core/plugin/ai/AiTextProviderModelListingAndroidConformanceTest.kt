package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
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
import java.io.FileInputStream
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

    /**
     * Read-only real-plugin gate. An exact-serial outer transaction owns provisioning and cleanup
     * of the three-model catalog; this method never accesses the plugin's private files, imports a
     * model, stops a package, or performs a process-wide stop-all.
     */
    @Test
    fun exactRealPluginListsExternallyProvisionedThreeModelCatalogAcrossStablePages() {
        val fixture = requireRealPluginFixture()
        val component = AiTextProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val initial = discoverExact(component)
        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            namedThreadFactory("ai-text-real-listing-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("ai-text-real-listing-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("ai-text-real-listing-deadline"),
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
                "Real AI text provider model listing did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val completed = outcome.get() as AiTextProviderModelListingRunOutcome.Completed
            assertEquals(pinned, completed.catalog.pinnedProvider)
            assertEquals(fixture.listingGeneration, completed.catalog.listingGeneration)
            assertEquals(fixture.modelIds, completed.catalog.models.map { it.modelId })
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
            assertFalse("Model-list completion ran on the Android main thread", callbackOnMain.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Real model listing emitted more than one terminal outcome", 1, outcomeCount.get())
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

    @Suppress("DEPRECATION")
    private fun requireRealPluginFixture(): RealPluginFixture {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real AI text model-list conformance is opt-in; pass -e $ARG_REAL_ENABLED true",
            arguments.getString(ARG_REAL_ENABLED) == "true",
        )
        assertTrue("Real AI text conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)

        val expectedSerial = arguments.getString(ARG_REAL_DEVICE_SERIAL).orEmpty().trim()
        assertTrue(
            "Pass the target device ro.serialno value through -e $ARG_REAL_DEVICE_SERIAL",
            expectedSerial.isNotEmpty(),
        )
        val actualSerial = executeShellCommand("getprop ro.serialno").trim().ifEmpty {
            executeShellCommand("getprop ro.boot.serialno").trim()
        }
        assertTrue("The device did not expose a serial property", actualSerial.isNotEmpty())
        assertEquals(
            "Instrumentation is running on a different device than the declared serial",
            expectedSerial,
            actualSerial,
        )

        val expectedModelIds = arguments.getString(ARG_REAL_MODEL_IDS)
            .orEmpty()
            .split(',')
            .map { it.trim() }
        assertEquals(
            "The outer fixture must declare exactly three ordered model IDs through " +
                "-e $ARG_REAL_MODEL_IDS id1,id2,id3",
            REAL_FIXTURE_MODEL_COUNT,
            expectedModelIds.size,
        )
        assertTrue(
            "The outer fixture model IDs must not be empty",
            expectedModelIds.all { it.isNotEmpty() },
        )
        assertEquals(
            "The outer fixture must declare three distinct model IDs",
            expectedModelIds.size,
            expectedModelIds.distinct().size,
        )
        val expectedGeneration = arguments.getString(ARG_REAL_LISTING_GENERATION).orEmpty().trim()
        assertTrue(
            "Pass the frozen fixture generation through -e $ARG_REAL_LISTING_GENERATION",
            expectedGeneration.isNotEmpty(),
        )

        val service = try {
            context.packageManager.getServiceInfo(
                ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE),
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real AI text provider is not installed", error)
        }
        assertEquals(REAL_PLUGIN_PACKAGE, service.packageName)
        assertEquals(REAL_PLUGIN_SERVICE, service.name)
        assertTrue("The real AI text provider application is disabled", service.applicationInfo.enabled)
        assertTrue("The real AI text provider service is disabled", service.enabled)
        assertTrue("The real AI text provider service is not exported", service.exported)
        assertEquals(
            "The real AI text provider and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        return RealPluginFixture(expectedModelIds, expectedGeneration)
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
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

    private data class RealPluginFixture(
        val modelIds: List<String>,
        val listingGeneration: String,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.aiText.modelListing.conformance.enabled"
        const val ARG_REAL_ENABLED = "autojs.aiText.realModelListing.conformance.enabled"
        const val ARG_REAL_DEVICE_SERIAL = "autojs.aiText.realModelListing.conformance.serial"
        const val ARG_REAL_MODEL_IDS = "autojs.aiText.realModelListing.conformance.modelIds"
        const val ARG_REAL_LISTING_GENERATION =
            "autojs.aiText.realModelListing.conformance.listingGeneration"
        const val FAKE_PACKAGE = "org.autojs.plugin.ai.text.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService"
        const val FAKE_LISTING_GENERATION = "fake-models-v1"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val LISTING_TIMEOUT_MILLIS = 20_000L
        const val OUTCOME_WAIT_MILLIS = 30_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        const val REAL_FIXTURE_MODEL_COUNT = 3
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.ai.text"
        const val REAL_PLUGIN_SERVICE =
            "io.github.supermonster003.autojs6.plugin.ai.text.provider.AiTextProviderService"
        val FAKE_MODEL_IDS = listOf(
            "fake.echo",
            "fake.stream",
            "fake.reasoning",
            "fake.tools",
        )
    }
}
