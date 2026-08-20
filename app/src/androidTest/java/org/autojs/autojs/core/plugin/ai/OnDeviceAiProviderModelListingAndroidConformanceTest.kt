package org.autojs.autojs.core.plugin.ai

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
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
class OnDeviceAiProviderModelListingAndroidConformanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun exactComponentModelListingRevalidatesAndCollectsAllFakeModels() {
        requireConformanceEnvironment()
        val component = OnDeviceAiProviderComponent(FAKE_PACKAGE, FAKE_SERVICE)
        val initial = discoverExact(component)
        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            namedThreadFactory("on-device-ai-listing-conformance-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("on-device-ai-listing-conformance-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("on-device-ai-listing-conformance-deadline"),
        )
        var listingHandle: Closeable = Closeable {}
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val outcome = AtomicReference<OnDeviceAiProviderModelListingRunOutcome>()
            val outcomeCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(true)
            val terminal = CountDownLatch(1)
            val coordinator = OnDeviceAiProviderModelListingCoordinator(
                transport = AndroidOnDeviceAiProviderModelListing(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                deadlineScheduler = scheduler.asModelListingDeadlineScheduler(),
                clock = OnDeviceAiProviderModelListingClock { android.os.SystemClock.elapsedRealtime() },
                owner = OnDeviceAiProviderModelListingProcessOwner(),
            )
            listingHandle = coordinator.start(
                OnDeviceAiProviderModelListingRunRequest(
                    snapshot = initial,
                    pinnedProvider = pinned,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = OnDeviceAiProviderModelListingRunOptions(
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
                "on-device AI provider model listing did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val completed = outcome.get() as OnDeviceAiProviderModelListingRunOutcome.Completed
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
        val component = OnDeviceAiProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val initial = discoverExact(component)
        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            namedThreadFactory("on-device-ai-real-listing-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("on-device-ai-real-listing-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("on-device-ai-real-listing-deadline"),
        )
        var listingHandle: Closeable = Closeable {}
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val outcome = AtomicReference<OnDeviceAiProviderModelListingRunOutcome>()
            val outcomeCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(true)
            val terminal = CountDownLatch(1)
            val coordinator = OnDeviceAiProviderModelListingCoordinator(
                transport = AndroidOnDeviceAiProviderModelListing(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                deadlineScheduler = scheduler.asModelListingDeadlineScheduler(),
                clock = OnDeviceAiProviderModelListingClock { android.os.SystemClock.elapsedRealtime() },
                owner = OnDeviceAiProviderModelListingProcessOwner(),
            )
            listingHandle = coordinator.start(
                OnDeviceAiProviderModelListingRunRequest(
                    snapshot = initial,
                    pinnedProvider = pinned,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = OnDeviceAiProviderModelListingRunOptions(
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
                "Real on-device AI provider model listing did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val completed = outcome.get() as OnDeviceAiProviderModelListingRunOutcome.Completed
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

    /**
     * Opt-in real-plugin selection gate. An exact-serial outer transaction owns the two-model
     * schema-2 fixture and package cleanup. This method selects through the exported production
     * activity, restarts only that plugin package, and separately proves that the public Binder
     * listing is unchanged. It never opens a model session or invokes LiteRT-LM.
     */
    @Test
    fun exactRealPluginSelectionPersistsAcrossManagerProcessRestartWithoutChangingBinderCatalog() {
        val fixture = requireRealPluginSelectionFixture()
        val component = OnDeviceAiProviderComponent(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val before = collectRealPluginCatalog(component)
        assertEquals(fixture.listingGeneration, before.catalog.listingGeneration)
        assertEquals(fixture.modelIds, before.catalog.models.map { it.modelId })
        assertExactModelsPlannable(before.catalog, fixture.modelIds)

        restartRealPluginModelManager()
        awaitRealPluginModelRow(fixture.initialModelId, expectedChecked = true).recycle()
        val target = awaitRealPluginModelRow(fixture.targetModelId, expectedChecked = false)
        try {
            assertTrue("The target model row is not clickable", target.isClickable)
            assertTrue(
                "The target model row did not accept an accessibility click",
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK),
            )
        } finally {
            target.recycle()
        }
        awaitRealPluginModelRow(fixture.targetModelId, expectedChecked = true).recycle()
        awaitRealPluginModelRow(fixture.initialModelId, expectedChecked = false).recycle()

        restartRealPluginModelManager()
        awaitRealPluginModelRow(fixture.targetModelId, expectedChecked = true).recycle()
        awaitRealPluginModelRow(fixture.initialModelId, expectedChecked = false).recycle()

        val after = collectRealPluginCatalog(component)
        assertTrue(
            "The real plugin package identity changed across the manager restart",
            before.packageSnapshot.samePackageIdentityAs(after.packageSnapshot),
        )
        assertEquals(before.catalog.listingGeneration, after.catalog.listingGeneration)
        assertEquals(before.catalog.models, after.catalog.models)
        assertExactModelsPlannable(after.catalog, fixture.modelIds)
    }

    private fun collectRealPluginCatalog(
        component: OnDeviceAiProviderComponent,
    ): RealPluginCatalogObservation {
        val initial = discoverExact(component)
        val worker = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(32),
            namedThreadFactory("on-device-ai-real-selection-control"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val blocking = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(16),
            namedThreadFactory("on-device-ai-real-selection-binder"),
            ThreadPoolExecutor.AbortPolicy(),
        )
        val scheduler = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("on-device-ai-real-selection-deadline"),
        )
        var listingHandle: Closeable = Closeable {}
        try {
            val pinned = runHandshake(initial, worker, blocking, scheduler)
            val outcome = AtomicReference<OnDeviceAiProviderModelListingRunOutcome>()
            val outcomeCount = AtomicInteger()
            val callbackOnMain = AtomicBoolean(true)
            val terminal = CountDownLatch(1)
            val coordinator = OnDeviceAiProviderModelListingCoordinator(
                transport = AndroidOnDeviceAiProviderModelListing(context),
                workerExecutor = worker,
                blockingExecutor = blocking,
                deadlineScheduler = scheduler.asModelListingDeadlineScheduler(),
                clock = OnDeviceAiProviderModelListingClock { SystemClock.elapsedRealtime() },
                owner = OnDeviceAiProviderModelListingProcessOwner(),
            )
            listingHandle = coordinator.start(
                OnDeviceAiProviderModelListingRunRequest(
                    snapshot = initial,
                    pinnedProvider = pinned,
                    environment = environment(),
                    hostCapabilities = hostCapabilities(),
                    options = OnDeviceAiProviderModelListingRunOptions(
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
                "Real on-device AI provider selection listing did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            val completed = outcome.get() as OnDeviceAiProviderModelListingRunOutcome.Completed
            assertEquals(pinned, completed.catalog.pinnedProvider)
            assertFalse("Model-list completion ran on the Android main thread", callbackOnMain.get())
            Thread.sleep(LATE_CALLBACK_SETTLE_MILLIS)
            assertEquals("Real selection listing emitted more than one terminal outcome", 1, outcomeCount.get())
            assertTrue(initial.samePackageIdentityAs(discoverExact(component)))
            return RealPluginCatalogObservation(initial, completed.catalog)
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

    private fun assertExactModelsPlannable(
        catalog: OnDeviceAiProviderModelCatalog,
        modelIds: List<String>,
    ) {
        modelIds.forEach { modelId ->
            assertTrue(
                "Exact model lookup failed for $modelId",
                catalog.planExact(modelId) is OnDeviceAiProviderExactModelPlanResult.Planned,
            )
        }
    }

    private fun restartRealPluginModelManager() {
        executeShellCommand("am force-stop $REAL_PLUGIN_PACKAGE")
        val output = executeShellCommand(
            "am start -W -n $REAL_PLUGIN_PACKAGE/$REAL_PLUGIN_MANAGER_ACTIVITY",
        )
        assertFalse(
            "Model-manager activity launch failed: $output",
            output.contains("Error:", ignoreCase = true),
        )
        assertTrue(
            "Model-manager activity did not report a successful launch: $output",
            output.contains("Status: ok", ignoreCase = true),
        )
    }

    @Suppress("DEPRECATION")
    private fun awaitRealPluginModelRow(
        modelId: String,
        expectedChecked: Boolean,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + MODEL_MANAGER_UI_WAIT_MILLIS
        var lastObservation = "no active accessibility root"
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root == null) {
                lastObservation = "no active accessibility root"
            } else {
                val nodes = root.findAccessibilityNodeInfosByText(modelId)
                val row = nodes.firstOrNull { node ->
                    node.packageName?.toString() == REAL_PLUGIN_PACKAGE &&
                        node.className?.toString() == "android.widget.RadioButton" &&
                        node.text?.toString()?.contains(modelId) == true
                }
                nodes.filterNot { it === row }.forEach(AccessibilityNodeInfo::recycle)
                root.recycle()
                if (row != null) {
                    lastObservation =
                        "checked=${row.isChecked}, enabled=${row.isEnabled}, clickable=${row.isClickable}"
                    if (row.isChecked == expectedChecked && row.isEnabled) return row
                    row.recycle()
                } else {
                    lastObservation = "the model row was not present"
                }
            }
            SystemClock.sleep(MODEL_MANAGER_UI_POLL_MILLIS)
        }
        throw AssertionError(
            "Model row $modelId did not reach checked=$expectedChecked: $lastObservation",
        )
    }

    @Suppress("DEPRECATION")
    private fun requireRealPluginFixture(): RealPluginFixture {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real on-device AI model-list conformance is opt-in; pass -e $ARG_REAL_ENABLED true",
            arguments.getString(ARG_REAL_ENABLED) == "true",
        )
        assertTrue("Real on-device AI conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)

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
            throw AssertionError("The real on-device AI provider is not installed", error)
        }
        assertEquals(REAL_PLUGIN_PACKAGE, service.packageName)
        assertEquals(REAL_PLUGIN_SERVICE, service.name)
        assertTrue("The real on-device AI provider application is disabled", service.applicationInfo.enabled)
        assertTrue("The real on-device AI provider service is disabled", service.enabled)
        assertTrue("The real on-device AI provider service is not exported", service.exported)
        assertEquals(
            "The real on-device AI provider and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )
        return RealPluginFixture(expectedModelIds, expectedGeneration)
    }

    @Suppress("DEPRECATION")
    private fun requireRealPluginSelectionFixture(): RealPluginSelectionFixture {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real on-device AI model-selection conformance is opt-in; pass -e $ARG_REAL_SELECTION_ENABLED true",
            arguments.getString(ARG_REAL_SELECTION_ENABLED) == "true",
        )
        assertTrue("Real on-device AI conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)

        val expectedSerial = arguments.getString(ARG_REAL_SELECTION_DEVICE_SERIAL).orEmpty().trim()
        assertTrue(
            "Pass the target device ro.serialno value through -e $ARG_REAL_SELECTION_DEVICE_SERIAL",
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

        val initialModelId = arguments.getString(ARG_REAL_SELECTION_INITIAL_MODEL_ID).orEmpty().trim()
        val targetModelId = arguments.getString(ARG_REAL_SELECTION_TARGET_MODEL_ID).orEmpty().trim()
        assertTrue(
            "Pass the initially selected model ID through -e $ARG_REAL_SELECTION_INITIAL_MODEL_ID",
            initialModelId.isNotEmpty(),
        )
        assertTrue(
            "Pass the target model ID through -e $ARG_REAL_SELECTION_TARGET_MODEL_ID",
            targetModelId.isNotEmpty(),
        )
        assertTrue("The initial and target model IDs must differ", initialModelId != targetModelId)
        val expectedGeneration = arguments.getString(ARG_REAL_SELECTION_LISTING_GENERATION).orEmpty().trim()
        assertTrue(
            "Pass the frozen fixture generation through -e $ARG_REAL_SELECTION_LISTING_GENERATION",
            expectedGeneration.isNotEmpty(),
        )

        val service = try {
            context.packageManager.getServiceInfo(
                ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE),
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real on-device AI provider is not installed", error)
        }
        assertEquals(REAL_PLUGIN_PACKAGE, service.packageName)
        assertEquals(REAL_PLUGIN_SERVICE, service.name)
        assertTrue("The real on-device AI provider application is disabled", service.applicationInfo.enabled)
        assertTrue("The real on-device AI provider service is disabled", service.enabled)
        assertTrue("The real on-device AI provider service is not exported", service.exported)
        assertEquals(
            "The real on-device AI provider and host must use the same signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, REAL_PLUGIN_PACKAGE),
        )

        val activity = try {
            context.packageManager.getActivityInfo(
                ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_MANAGER_ACTIVITY),
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        } catch (error: PackageManager.NameNotFoundException) {
            throw AssertionError("The real on-device AI model manager is not installed", error)
        }
        assertEquals(REAL_PLUGIN_PACKAGE, activity.packageName)
        assertEquals(REAL_PLUGIN_MANAGER_ACTIVITY, activity.name)
        assertTrue("The real on-device AI model-manager application is disabled", activity.applicationInfo.enabled)
        assertTrue("The real on-device AI model-manager activity is disabled", activity.enabled)
        assertTrue("The real on-device AI model-manager activity is not exported", activity.exported)
        return RealPluginSelectionFixture(
            initialModelId = initialModelId,
            targetModelId = targetModelId,
            listingGeneration = expectedGeneration,
        )
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun runHandshake(
        initial: OnDeviceAiProviderPackageSnapshot,
        worker: ThreadPoolExecutor,
        blocking: ThreadPoolExecutor,
        scheduler: ScheduledExecutorService,
    ): OnDeviceAiProviderPinnedIdentity {
        val outcome = AtomicReference<OnDeviceAiProviderHandshakeOutcome>()
        val terminal = CountDownLatch(1)
        val coordinator = OnDeviceAiProviderHandshakeCoordinator(
            transport = AndroidOnDeviceAiProviderHandshake(context),
            workerExecutor = worker,
            blockingExecutor = blocking,
            deadlineScheduler = scheduler.asHandshakeDeadlineScheduler(),
            gate = OnDeviceAiProviderHandshakeGate(),
        )
        val handle = coordinator.start(
            OnDeviceAiProviderHandshakeRequest(
                snapshot = initial,
                environment = environment(),
                hostCapabilities = hostCapabilities(),
                options = OnDeviceAiProviderHandshakeOptions(
                    enabled = true,
                    timeoutMillis = HANDSHAKE_TIMEOUT_MILLIS,
                ),
            ),
        ) { value ->
            if (outcome.compareAndSet(null, value)) terminal.countDown()
        }
        try {
            assertTrue(
                "on-device AI provider metadata handshake did not terminate",
                terminal.await(OUTCOME_WAIT_MILLIS, TimeUnit.MILLISECONDS),
            )
            return (outcome.get() as OnDeviceAiProviderHandshakeOutcome.Selected).pinned
        } finally {
            handle.close()
        }
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "on-device AI model-list Android conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED).toBoolean(),
        )
        assertTrue("on-device AI conformance requires API 24 or newer", Build.VERSION.SDK_INT >= 24)
        assertEquals(
            "on-device AI fake and host must use the same dedicated debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(context.packageName, FAKE_PACKAGE),
        )
    }

    private fun discoverExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderPackageSnapshot {
        val result = OnDeviceAiProviderDiscoveryPolicy.inspectExact(
            source = AndroidOnDeviceAiProviderDiscovery(context),
            component = component,
            options = OnDeviceAiProviderDiscoveryOptions(enabled = true),
        ) as OnDeviceAiProviderDiscoveryResult.Completed
        return (result.evaluations.single() as OnDeviceAiProviderDiscoveryEvaluation.Discovered).snapshot
    }

    private fun environment() = OnDeviceAiProviderEnvironment(
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentAbi = Build.SUPPORTED_ABIS.firstOrNull(),
        releaseBuild = false,
    )

    private fun hostCapabilities() = OnDeviceAiCapabilities(
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
        acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(OnDeviceAiMimeType.JSON),
    )

    private fun namedThreadFactory(name: String) = java.util.concurrent.ThreadFactory { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    private fun ScheduledExecutorService.asHandshakeDeadlineScheduler() =
        OnDeviceAiProviderHandshakeDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private fun ScheduledExecutorService.asModelListingDeadlineScheduler() =
        OnDeviceAiProviderModelListingDeadlineScheduler { delayMillis, action ->
            val future = schedule(action, delayMillis, TimeUnit.MILLISECONDS)
            Closeable { future.cancel(false) }
        }

    private data class RealPluginFixture(
        val modelIds: List<String>,
        val listingGeneration: String,
    )

    private data class RealPluginSelectionFixture(
        val initialModelId: String,
        val targetModelId: String,
        val listingGeneration: String,
    ) {
        val modelIds = listOf(initialModelId, targetModelId).sorted()
    }

    private data class RealPluginCatalogObservation(
        val packageSnapshot: OnDeviceAiProviderPackageSnapshot,
        val catalog: OnDeviceAiProviderModelCatalog,
    )

    private companion object {
        const val ARG_ENABLED = "autojs.onDeviceAi.modelListing.conformance.enabled"
        const val ARG_REAL_ENABLED = "autojs.onDeviceAi.realModelListing.conformance.enabled"
        const val ARG_REAL_DEVICE_SERIAL = "autojs.onDeviceAi.realModelListing.conformance.serial"
        const val ARG_REAL_MODEL_IDS = "autojs.onDeviceAi.realModelListing.conformance.modelIds"
        const val ARG_REAL_LISTING_GENERATION =
            "autojs.onDeviceAi.realModelListing.conformance.listingGeneration"
        const val ARG_REAL_SELECTION_ENABLED = "autojs.onDeviceAi.realModelSelection.conformance.enabled"
        const val ARG_REAL_SELECTION_DEVICE_SERIAL = "autojs.onDeviceAi.realModelSelection.conformance.serial"
        const val ARG_REAL_SELECTION_INITIAL_MODEL_ID =
            "autojs.onDeviceAi.realModelSelection.conformance.initialModelId"
        const val ARG_REAL_SELECTION_TARGET_MODEL_ID =
            "autojs.onDeviceAi.realModelSelection.conformance.targetModelId"
        const val ARG_REAL_SELECTION_LISTING_GENERATION =
            "autojs.onDeviceAi.realModelSelection.conformance.listingGeneration"
        const val FAKE_PACKAGE = "org.autojs.plugin.ondeviceai.fake"
        const val FAKE_SERVICE =
            "org.autojs.plugin.ondeviceai.fake.service.FakeOnDeviceAiProviderService"
        const val FAKE_LISTING_GENERATION = "fake-models-v1"
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val LISTING_TIMEOUT_MILLIS = 20_000L
        const val OUTCOME_WAIT_MILLIS = 30_000L
        const val LATE_CALLBACK_SETTLE_MILLIS = 250L
        const val EXECUTOR_SHUTDOWN_WAIT_MILLIS = 2_000L
        const val MODEL_MANAGER_UI_WAIT_MILLIS = 15_000L
        const val MODEL_MANAGER_UI_POLL_MILLIS = 100L
        const val REAL_FIXTURE_MODEL_COUNT = 3
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.ondeviceai"
        const val REAL_PLUGIN_SERVICE =
            "io.github.supermonster003.autojs6.plugin.ondeviceai.provider.OnDeviceAiProviderService"
        const val REAL_PLUGIN_MANAGER_ACTIVITY =
            "io.github.supermonster003.autojs6.plugin.ondeviceai.ModelManagerActivity"
        val FAKE_MODEL_IDS = listOf(
            "fake.echo",
            "fake.stream",
            "fake.reasoning",
            "fake.tools",
        )
    }
}
