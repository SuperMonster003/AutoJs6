package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.plugin.yolo.api.YoloDetectResult
import org.autojs.plugin.yolo.api.YoloDetection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in R5 resource acceptance against the production Host and independent NCNN provider.
 *
 * The verifier stages the local-only model and image in Host-private storage. Provider resource
 * counters are read through its release-safe Service.dump surface; this test never binds AIDL
 * directly and never uses a test-only provider hook.
 */
class YoloRealProviderR5ResourceAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun productionHostMaintainsBoundedResourcesAcrossTwoHundredFrames() {
        assumeTrue(
            "R5 YOLO resource instrumentation is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(TEST_PACKAGE, instrumentation.context.packageName)

        val resultFile = File(context.filesDir, RESULT_FILE_NAME)
        val progressTemp = File(context.filesDir, "$RESULT_FILE_NAME.tmp")
        assertTrue("Unable to remove stale R5 result", !resultFile.exists() || resultFile.delete())
        assertTrue("Unable to remove stale R5 progress", !progressTemp.exists() || progressTemp.delete())

        val fixtureRoot = File(context.filesDir, FIXTURE_DIRECTORY_NAME)
        val manifest = fixtureRoot.requireFixture("model-manifest-v1.json")
        val param = fixtureRoot.requireFixture("model.ncnn.param")
        val bin = fixtureRoot.requireFixture("model.ncnn.bin")
        val imageFile = fixtureRoot.requireFixture("bus.jpg")
        val expected = ExpectedFixture.parse(fixtureRoot.requireFixture("expected-bus-detections.json"))
        val model = listOf(
            YoloPluginHost.ModelArtifactFile("manifest", manifest.name, manifest),
            YoloPluginHost.ModelArtifactFile("ncnn-param", param.name, param),
            YoloPluginHost.ModelArtifactFile("ncnn-bin", bin.name, bin),
        )
        val openOptions = YoloPluginHost.OpenSessionOptions(
            cpuThreads = 4,
            decoderId = "ultralytics-detect",
            timeoutMillis = SESSION_TIMEOUT_MILLIS,
        )
        val detectOptions = YoloPluginHost.DetectOptions(
            confidenceThreshold = expected.confidenceThreshold,
            iouThreshold = expected.iouThreshold,
            maxDetections = expected.maxDetections,
            timeoutMillis = DETECT_TIMEOUT_MILLIS,
        )

        val packageManager = context.packageManager
        val hostUid = packageManager.requireUid(HOST_PACKAGE)
        val providerUid = packageManager.requireUid(PROVIDER_PACKAGE)
        val testUid = instrumentation.context.applicationInfo.uid
        assertTrue("Host and provider unexpectedly share a UID", hostUid != providerUid)
        assertTrue("Provider and instrumentation unexpectedly share a UID", providerUid != testUid)
        assertEquals(
            "Host and provider must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, PROVIDER_PACKAGE),
        )
        assertEquals(
            "Host and instrumentation must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, TEST_PACKAGE),
        )

        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, PROVIDER_PACKAGE)
        assertTrue("The installed provider has no signer fingerprint", fingerprints.isNotEmpty())
        val state = PluginStateSnapshot.capture(context, PROVIDER_PACKAGE, fingerprints)
        var primarySession: YoloPluginHost.Session? = null
        var reopenedSession: YoloPluginHost.Session? = null
        var primaryFailure: Throwable? = null
        try {
            assertFalse(
                "The independently installed YOLO provider must be default-off before opt-in",
                PluginEnableStore.isEnabled(context, PROVIDER_PACKAGE, defaultEnabled = false),
            )
            assertTrue(
                "R5 requires a cold provider process before the first production Host call",
                providerPids().isEmpty(),
            )
            fingerprints.forEach { fingerprint ->
                PluginAuthorizationStore.grant(context, PROVIDER_PACKAGE, fingerprint)
            }
            PluginEnableStore.setEnabled(context, PROVIDER_PACKAGE, true)

            val coldTarget = measureWall {
                runBlocking { YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT) }
            }
            val target = coldTarget.value
            assertEquals(PROVIDER_COMPONENT, target.component)
            assertEquals(providerUid, target.providerUid)

            val opened = measureWall {
                runBlocking { YoloPluginHost.openSession(context, target, model, openOptions) }
            }
            val session = opened.value
            primarySession = session
            assertTrue("Production Host did not use a remote session Binder", session.isRemoteBinder)
            assertTrue("Production Host retained a model descriptor", session.modelDescriptorsClosedAfterOpen)

            val openDiagnostics = providerDiagnostics()
            openDiagnostics.assertActive()
            val providerPid = openDiagnostics.pid
            assertOnlyProviderPid(providerPid)

            val bitmap = requireNotNull(BitmapFactory.decodeFile(imageFile.absolutePath)) {
                "Unable to decode the externally staged bus image"
            }
            try {
                assertEquals(expected.width, bitmap.width)
                assertEquals(expected.height, bitmap.height)

                val warmupWallMillis = ArrayList<Double>(WARMUP_FRAMES)
                val warmupProviderMillis = ArrayList<Long>(WARMUP_FRAMES)
                val measuredWallMillis = ArrayList<Double>(MEASURED_FRAMES)
                val measuredProviderMillis = ArrayList<Long>(MEASURED_FRAMES)
                val providerSamples = ArrayList<ProviderDiagnostics>(EXPECTED_RESOURCE_SAMPLES)
                val hostSamples = ArrayList<HostResourceSample>(EXPECTED_RESOURCE_SAMPLES)
                var previousSequence = 0L
                var minimumIou = 1.0
                var maximumConfidenceDelta = 0.0
                var representativeDetections: List<YoloDetection>? = null

                repeat(WARMUP_FRAMES) { index ->
                    val detected = measureWall {
                        runBlocking { session.detect(context, bitmap, detectOptions) }
                    }
                    warmupWallMillis += detected.wallMillis
                    warmupProviderMillis += detected.value.elapsedMillis
                    val frame = verifyFrame(
                        session = session,
                        result = detected.value,
                        expected = expected,
                        providerUid = providerUid,
                        providerPid = providerPid,
                        previousSequence = previousSequence,
                        label = "warmup-${index + 1}",
                    )
                    previousSequence = detected.value.sequence
                    minimumIou = min(minimumIou, frame.minimumIou)
                    maximumConfidenceDelta = max(maximumConfidenceDelta, frame.maximumConfidenceDelta)
                    if (representativeDetections == null) representativeDetections = detected.value.detections
                }

                captureResourceSample(
                    measuredFrames = 0,
                    expectedProviderPid = providerPid,
                    providerSamples = providerSamples,
                    hostSamples = hostSamples,
                )

                repeat(MEASURED_FRAMES) { index ->
                    val detected = measureWall {
                        runBlocking { session.detect(context, bitmap, detectOptions) }
                    }
                    measuredWallMillis += detected.wallMillis
                    measuredProviderMillis += detected.value.elapsedMillis
                    val frame = verifyFrame(
                        session = session,
                        result = detected.value,
                        expected = expected,
                        providerUid = providerUid,
                        providerPid = providerPid,
                        previousSequence = previousSequence,
                        label = "measured-${index + 1}",
                    )
                    previousSequence = detected.value.sequence
                    minimumIou = min(minimumIou, frame.minimumIou)
                    maximumConfidenceDelta = max(maximumConfidenceDelta, frame.maximumConfidenceDelta)

                    val completed = index + 1
                    if (completed % SAMPLE_INTERVAL_FRAMES == 0) {
                        captureResourceSample(
                            measuredFrames = completed,
                            expectedProviderPid = providerPid,
                            providerSamples = providerSamples,
                            hostSamples = hostSamples,
                        )
                        writeProgress(
                            resultFile = resultFile,
                            tempFile = progressTemp,
                            measuredFrames = completed,
                            providerPid = providerPid,
                            resourceSamples = providerSamples.size,
                        )
                    }
                }

                assertEquals(EXPECTED_RESOURCE_SAMPLES, providerSamples.size)
                assertEquals(EXPECTED_RESOURCE_SAMPLES, hostSamples.size)
                val providerFdAnalysis = boundedFdAnalysis(providerSamples.map { it.selfFdCount.toLong() })
                val hostFdAnalysis = boundedFdAnalysis(hostSamples.map { it.fdCount.toLong() })
                assertTrue(
                    "Provider FD count grew beyond the R5 settle allowance: $providerFdAnalysis",
                    providerFdAnalysis.withinAllowance,
                )
                assertTrue(
                    "Host FD count grew beyond the R5 settle allowance: $hostFdAnalysis",
                    hostFdAnalysis.withinAllowance,
                )
                val providerRssAnalysis = rssRunawayAnalysis(providerSamples.map { it.selfRssKb })
                val hostRssAnalysis = rssRunawayAnalysis(hostSamples.map { it.rssKb })
                assertFalse("Provider RSS shows sustained runaway growth: $providerRssAnalysis", providerRssAnalysis.runaway)
                assertFalse("Host RSS shows sustained runaway growth: $hostRssAnalysis", hostRssAnalysis.runaway)

                // Refresh the normal production pooled lease so Service.dump remains observable
                // while the oneway session close drains on the provider control lane.
                val refreshedTarget = runBlocking {
                    YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT)
                }
                assertEquals(target.identity, refreshedTarget.identity)
                session.close()
                session.close()
                assertTrue("Primary session did not become locally closed", session.isClosed)
                val afterPrimaryClose = awaitClosedDiagnostics(providerPid)

                val reopen = measureWall {
                    runBlocking { YoloPluginHost.openSession(context, refreshedTarget, model, openOptions) }
                }
                val secondSession = reopen.value
                reopenedSession = secondSession
                assertTrue(secondSession.isRemoteBinder)
                assertTrue(secondSession.modelDescriptorsClosedAfterOpen)
                providerDiagnostics().also {
                    assertEquals(providerPid, it.pid)
                    it.assertActive()
                }
                val reopenedDetect = measureWall {
                    runBlocking { secondSession.detect(context, bitmap, detectOptions) }
                }
                val reopenedFrame = verifyFrame(
                    session = secondSession,
                    result = reopenedDetect.value,
                    expected = expected,
                    providerUid = providerUid,
                    providerPid = providerPid,
                    previousSequence = 0L,
                    label = "reopened",
                )
                minimumIou = min(minimumIou, reopenedFrame.minimumIou)
                maximumConfidenceDelta = max(maximumConfidenceDelta, reopenedFrame.maximumConfidenceDelta)
                runBlocking { YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT) }
                secondSession.close()
                secondSession.close()
                assertTrue("Reopened session did not become locally closed", secondSession.isClosed)
                val afterReopenedClose = awaitClosedDiagnostics(providerPid)

                writeFinalResult(
                    resultFile = resultFile,
                    tempFile = progressTemp,
                    hostUid = hostUid,
                    providerUid = providerUid,
                    testUid = testUid,
                    providerPid = providerPid,
                    coldTargetWallMillis = coldTarget.wallMillis,
                    sessionOpenWallMillis = opened.wallMillis,
                    warmupWallMillis = warmupWallMillis,
                    warmupProviderMillis = warmupProviderMillis,
                    measuredWallMillis = measuredWallMillis,
                    measuredProviderMillis = measuredProviderMillis,
                    reopenWallMillis = reopen.wallMillis,
                    reopenedDetectWallMillis = reopenedDetect.wallMillis,
                    reopenedProviderMillis = reopenedDetect.value.elapsedMillis,
                    providerSamples = providerSamples,
                    hostSamples = hostSamples,
                    providerFdAnalysis = providerFdAnalysis,
                    hostFdAnalysis = hostFdAnalysis,
                    providerRssAnalysis = providerRssAnalysis,
                    hostRssAnalysis = hostRssAnalysis,
                    afterPrimaryClose = afterPrimaryClose,
                    afterReopenedClose = afterReopenedClose,
                    representativeDetections = checkNotNull(representativeDetections),
                    minimumIou = minimumIou,
                    maximumConfidenceDelta = maximumConfidenceDelta,
                )
            } finally {
                bitmap.recycle()
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { reopenedSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { primarySession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { state.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { if (progressTemp.exists()) check(progressTemp.delete()) }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun captureResourceSample(
        measuredFrames: Int,
        expectedProviderPid: Int,
        providerSamples: MutableList<ProviderDiagnostics>,
        hostSamples: MutableList<HostResourceSample>,
    ) {
        assertOnlyProviderPid(expectedProviderPid)
        val provider = providerDiagnostics()
        assertEquals(expectedProviderPid, provider.pid)
        provider.assertActive()
        providerSamples += provider.copy(measuredFrames = measuredFrames)

        // executeShellCommand's result PFD is already closed before this Host snapshot.
        val host = HostResourceSample.capture(measuredFrames)
        assertTrue(
            "Host retains deleted YOLO transport descriptors: ${host.deletedYoloTargets}",
            host.deletedYoloTargets.isEmpty(),
        )
        assertTrue(
            "Host YOLO transport directory retained a frame",
            File(context.cacheDir, YOLO_IMAGE_CACHE_DIRECTORY).listFiles().isNullOrEmpty(),
        )
        hostSamples += host
    }

    private fun verifyFrame(
        session: YoloPluginHost.Session,
        result: YoloDetectResult,
        expected: ExpectedFixture,
        providerUid: Int,
        providerPid: Int,
        previousSequence: Long,
        label: String,
    ): DetectionComparisonSummary {
        assertEquals("$label image width changed", expected.width, result.imageWidth)
        assertEquals("$label image height changed", expected.height, result.imageHeight)
        assertEquals("$label sequence did not advance once", previousSequence + 1L, result.sequence)
        assertTrue("$label Host retained its image PFD", session.lastImageDescriptorClosedAfterDispatch)
        assertEquals("$label did not receive exactly one terminal", 1, session.lastTerminalCount)
        assertEquals("$label terminal came from an unexpected UID", providerUid, session.lastCallbackUid)
        val observation = requireNotNull(session.lastDetectionObservation) {
            "$label omitted its immutable Host observation"
        }
        assertEquals(result.requestId, observation.requestId)
        assertEquals(result.sequence, observation.sequence)
        assertEquals(1, observation.terminalCount)
        assertTrue(observation.imageDescriptorClosedAfterDispatch)
        assertEquals(providerUid, observation.callbackUid)
        assertOnlyProviderPid(providerPid)
        assertTrue(
            "$label left a materialized Host frame",
            File(context.cacheDir, YOLO_IMAGE_CACHE_DIRECTORY).listFiles().isNullOrEmpty(),
        )
        return expected.compare(result.detections, label)
    }

    private fun providerDiagnostics(): ProviderDiagnostics {
        val output = executeShellCommand(
            "dumpsys activity service ${PROVIDER_COMPONENT.flattenToShortString()}",
        )
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(PROVIDER_DIAGNOSTICS_PREFIX) }
            ?: throw AssertionError("Provider Service.dump omitted $PROVIDER_DIAGNOSTICS_PREFIX: $output")
        return ProviderDiagnostics.parse(line.removePrefix(PROVIDER_DIAGNOSTICS_PREFIX))
    }

    private fun awaitClosedDiagnostics(expectedPid: Int): ProviderDiagnostics {
        val deadline = SystemClock.elapsedRealtime() + PROVIDER_CLOSE_TIMEOUT_MILLIS
        var last: ProviderDiagnostics? = null
        while (SystemClock.elapsedRealtime() <= deadline) {
            assertOnlyProviderPid(expectedPid)
            val current = providerDiagnostics()
            assertEquals(expectedPid, current.pid)
            last = current
            if (current.isClosed()) {
                current.assertMetricsAvailable()
                return current
            }
            SystemClock.sleep(PROVIDER_CLOSE_POLL_MILLIS)
        }
        throw AssertionError("Provider resources did not settle after close: $last")
    }

    private fun providerPids(): List<Int> = executeShellCommand("pidof $PROVIDER_PROCESS_NAME")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .mapNotNull { it.toIntOrNull() }
        .distinct()
        .sorted()

    private fun assertOnlyProviderPid(expectedPid: Int) {
        assertEquals("Provider process identity changed during R5", listOf(expectedPid), providerPids())
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    private fun boundedFdAnalysis(values: List<Long>): FdAnalysis {
        require(values.size >= WINDOW_SAMPLES * 2)
        require(values.all { it >= 0L }) { "FD samples contain an unavailable value: $values" }
        val earlyMedian = median(values.take(WINDOW_SAMPLES))
        val lateMedian = median(values.takeLast(WINDOW_SAMPLES))
        return FdAnalysis(
            earlyMedian = earlyMedian,
            lateMedian = lateMedian,
            delta = lateMedian - earlyMedian,
            allowance = FD_GROWTH_ALLOWANCE,
            withinAllowance = lateMedian <= earlyMedian + FD_GROWTH_ALLOWANCE,
        )
    }

    private fun rssRunawayAnalysis(values: List<Long>): RssAnalysis {
        require(values.size >= WINDOW_SAMPLES * 2)
        require(values.all { it >= 0L }) { "RSS samples contain an unavailable value: $values" }
        val earlyMedian = median(values.take(WINDOW_SAMPLES))
        val lateMedian = median(values.takeLast(WINDOW_SAMPLES))
        val delta = lateMedian - earlyMedian
        val tolerance = max(RSS_MINIMUM_TOLERANCE_KB, ceil(earlyMedian * RSS_RELATIVE_TOLERANCE).toLong())
        val recentIncreases = values.takeLast(RECENT_RSS_VALUES).zipWithNext().count { (before, after) -> after > before }
        val finalIsMaximum = values.last() == values.maxOrNull()
        return RssAnalysis(
            earlyMedianKb = earlyMedian,
            lateMedianKb = lateMedian,
            deltaKb = delta,
            toleranceKb = tolerance,
            recentIncreases = recentIncreases,
            finalIsMaximum = finalIsMaximum,
            runaway = delta > tolerance && recentIncreases >= REQUIRED_RECENT_RSS_INCREASES && finalIsMaximum,
        )
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun writeProgress(
        resultFile: File,
        tempFile: File,
        measuredFrames: Int,
        providerPid: Int,
        resourceSamples: Int,
    ) {
        writeAtomically(
            resultFile,
            tempFile,
            JSONObject()
                .put("schemaVersion", 1)
                .put("status", "RUNNING")
                .put("phase", "measured")
                .put("measuredFrames", measuredFrames)
                .put("totalMeasuredFrames", MEASURED_FRAMES)
                .put("resourceSamples", resourceSamples)
                .put("providerPid", providerPid)
                .put("updatedAtEpochMillis", System.currentTimeMillis()),
        )
    }

    @Suppress("LongParameterList")
    private fun writeFinalResult(
        resultFile: File,
        tempFile: File,
        hostUid: Int,
        providerUid: Int,
        testUid: Int,
        providerPid: Int,
        coldTargetWallMillis: Double,
        sessionOpenWallMillis: Double,
        warmupWallMillis: List<Double>,
        warmupProviderMillis: List<Long>,
        measuredWallMillis: List<Double>,
        measuredProviderMillis: List<Long>,
        reopenWallMillis: Double,
        reopenedDetectWallMillis: Double,
        reopenedProviderMillis: Long,
        providerSamples: List<ProviderDiagnostics>,
        hostSamples: List<HostResourceSample>,
        providerFdAnalysis: FdAnalysis,
        hostFdAnalysis: FdAnalysis,
        providerRssAnalysis: RssAnalysis,
        hostRssAnalysis: RssAnalysis,
        afterPrimaryClose: ProviderDiagnostics,
        afterReopenedClose: ProviderDiagnostics,
        representativeDetections: List<YoloDetection>,
        minimumIou: Double,
        maximumConfidenceDelta: Double,
    ) {
        val result = JSONObject()
            .put("schemaVersion", 1)
            .put("status", "PASS")
            .put("hostPackage", HOST_PACKAGE)
            .put("providerPackage", PROVIDER_PACKAGE)
            .put("testPackage", TEST_PACKAGE)
            .put("component", PROVIDER_COMPONENT.flattenToString())
            .put("hostUid", hostUid)
            .put("providerUid", providerUid)
            .put("testUid", testUid)
            .put("providerPid", providerPid)
            .put("sameSigner", true)
            .put("productionHost", true)
            .put("warmupFrames", WARMUP_FRAMES)
            .put("measuredFrames", MEASURED_FRAMES)
            .put("resourceSampleIntervalFrames", SAMPLE_INTERVAL_FRAMES)
            .put("resourceSampleCount", providerSamples.size)
            .put("uniqueTerminalEveryFrame", true)
            .put("imagePfdClosedEveryFrame", true)
            .put("providerPidStableEveryFrame", true)
            .put("performanceThresholdApplied", false)
            .put("timings", JSONObject()
                .put("clock", "SystemClock.elapsedRealtimeNanos")
                .put("coldTargetWallMillis", coldTargetWallMillis)
                .put("sessionOpenWallMillis", sessionOpenWallMillis)
                .put("firstDetectWallMillis", warmupWallMillis.first())
                .put("firstProviderInferenceMillis", warmupProviderMillis.first())
                .put("warmupWall", TimingSummary.from(warmupWallMillis).toJson())
                .put("warmupProvider", TimingSummary.from(warmupProviderMillis.map { it.toDouble() }).toJson())
                .put("measuredWall", TimingSummary.from(measuredWallMillis).toJson())
                .put("measuredProvider", TimingSummary.from(measuredProviderMillis.map { it.toDouble() }).toJson())
                .put("measuredWallSamplesMillis", JSONArray(measuredWallMillis))
                .put("measuredProviderSamplesMillis", JSONArray(measuredProviderMillis))
                .put("reopenWallMillis", reopenWallMillis)
                .put("reopenedDetectWallMillis", reopenedDetectWallMillis)
                .put("reopenedProviderInferenceMillis", reopenedProviderMillis))
            .put("resources", JSONObject()
                .put("providerSamples", JSONArray().apply { providerSamples.forEach { put(it.toJson()) } })
                .put("hostSamples", JSONArray().apply { hostSamples.forEach { put(it.toJson()) } })
                .put("providerFd", providerFdAnalysis.toJson())
                .put("hostFd", hostFdAnalysis.toJson())
                .put("providerRss", providerRssAnalysis.toJson())
                .put("hostRss", hostRssAnalysis.toJson())
                .put("afterPrimaryClose", afterPrimaryClose.toJson())
                .put("afterReopenedClose", afterReopenedClose.toJson()))
            .put("fixture", JSONObject()
                .put("minimumObservedIou", minimumIou)
                .put("maximumObservedConfidenceDelta", maximumConfidenceDelta)
                .put("representativeDetections", detectionsToJson(representativeDetections)))
            .put("claims", JSONObject()
                .put("resource200Frame", "PASS")
                .put("hostFd", "PASS")
                .put("providerFd", "PASS")
                .put("sessionHandleLifecycle", "PASS")
                .put("rssRunaway", "NOT_OBSERVED")
                .put("performance", "RECORDED_NO_THRESHOLD")
                .put("soak", "NOT_RUN")
                .put("production", "NOT_CLAIMED"))
        writeAtomically(resultFile, tempFile, result)
    }

    private fun writeAtomically(resultFile: File, tempFile: File, value: JSONObject) {
        tempFile.writeText(value.toString(2), Charsets.UTF_8)
        check(!resultFile.exists() || resultFile.delete()) { "Unable to replace the R5 result" }
        check(tempFile.renameTo(resultFile)) { "Unable to publish the R5 result" }
    }

    private fun detectionsToJson(values: List<YoloDetection>) = JSONArray().apply {
        values.forEach { detection ->
            put(JSONObject()
                .put("classId", detection.classId)
                .put("label", detection.label)
                .put("confidence", detection.confidence)
                .put("bounds", JSONArray(listOf(
                    detection.bounds.left,
                    detection.bounds.top,
                    detection.bounds.right,
                    detection.bounds.bottom,
                ))))
        }
    }

    private fun File.requireFixture(name: String): File {
        val child = File(this, name)
        assertTrue("Externally staged fixture is missing: $child", child.isFile)
        assertTrue("Externally staged fixture is empty: $child", child.length() > 0L)
        return child
    }

    private fun PackageManager.requireUid(packageName: String): Int = try {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0).uid
    } catch (error: PackageManager.NameNotFoundException) {
        throw AssertionError("Required package is not installed: $packageName").also { it.initCause(error) }
    }

    private data class Timed<T>(val value: T, val wallMillis: Double)

    private fun <T> measureWall(block: () -> T): Timed<T> {
        val started = SystemClock.elapsedRealtimeNanos()
        val value = block()
        return Timed(value, (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0)
    }

    private data class ProviderDiagnostics(
        val pid: Int,
        val activeSessions: Int,
        val sessionGate: Boolean,
        val activeNativeHandles: Int,
        val selfFdCount: Int,
        val selfRssKb: Long,
        val modelDirCount: Int,
        val measuredFrames: Int = -1,
    ) {
        fun assertActive() {
            assertMetricsAvailable()
            assertEquals("Provider active session count changed", 1, activeSessions)
            assertTrue("Provider session gate was released while active", sessionGate)
            assertEquals("Provider native handle count changed", 1, activeNativeHandles)
            assertEquals("Provider materialized model directory count changed", 1, modelDirCount)
        }

        fun assertMetricsAvailable() {
            assertTrue("Provider diagnostics PID is invalid", pid > 0)
            assertTrue("Provider self FD count is unavailable", selfFdCount >= 0)
            assertTrue("Provider self RSS is unavailable", selfRssKb >= 0L)
            assertTrue("Provider model directory count is unavailable", modelDirCount >= 0)
        }

        fun isClosed(): Boolean =
            activeSessions == 0 && !sessionGate && activeNativeHandles == 0 && modelDirCount == 0

        fun toJson() = JSONObject()
            .put("measuredFrames", measuredFrames)
            .put("pid", pid)
            .put("activeSessions", activeSessions)
            .put("sessionGate", sessionGate)
            .put("activeNativeHandles", activeNativeHandles)
            .put("selfFdCount", selfFdCount)
            .put("selfRssKb", selfRssKb)
            .put("modelDirCount", modelDirCount)

        companion object {
            fun parse(json: String): ProviderDiagnostics {
                val value = JSONObject(json)
                assertEquals(1, value.getInt("schemaVersion"))
                return ProviderDiagnostics(
                    pid = value.getInt("pid"),
                    activeSessions = value.getInt("activeSessions"),
                    sessionGate = value.getBoolean("sessionGate"),
                    activeNativeHandles = value.getInt("activeNativeHandles"),
                    selfFdCount = value.getInt("selfFdCount"),
                    selfRssKb = value.getLong("selfRssKb"),
                    modelDirCount = value.getInt("modelDirCount"),
                )
            }
        }
    }

    private data class HostResourceSample(
        val measuredFrames: Int,
        val pid: Int,
        val fdCount: Int,
        val rssKb: Long,
        val deletedYoloTargets: List<String>,
    ) {
        fun toJson() = JSONObject()
            .put("measuredFrames", measuredFrames)
            .put("pid", pid)
            .put("fdCount", fdCount)
            .put("rssKb", rssKb)
            .put("deletedYoloTargets", JSONArray(deletedYoloTargets))

        companion object {
            fun capture(measuredFrames: Int): HostResourceSample {
                val pid = Process.myPid()
                val entries = requireNotNull(File("/proc/self/fd").listFiles()) {
                    "Unable to enumerate Host file descriptors"
                }
                val targets = entries.mapNotNull { entry ->
                    runCatching { Os.readlink(entry.absolutePath) }.getOrNull()
                }
                val rssKb = File("/proc/self/status").useLines { lines ->
                    lines.firstOrNull { it.startsWith(PROC_RSS_PREFIX) }
                        ?.removePrefix(PROC_RSS_PREFIX)
                        ?.trim()
                        ?.substringBefore(' ')
                        ?.toLongOrNull()
                } ?: throw AssertionError("Host VmRSS is unavailable")
                return HostResourceSample(
                    measuredFrames = measuredFrames,
                    pid = pid,
                    fdCount = entries.size,
                    rssKb = rssKb,
                    deletedYoloTargets = targets.filter {
                        it.contains(YOLO_IMAGE_CACHE_DIRECTORY) && it.contains("(deleted)")
                    }.sorted(),
                )
            }
        }
    }

    private data class FdAnalysis(
        val earlyMedian: Long,
        val lateMedian: Long,
        val delta: Long,
        val allowance: Long,
        val withinAllowance: Boolean,
    ) {
        fun toJson() = JSONObject()
            .put("method", "early3_late3_median")
            .put("earlyMedian", earlyMedian)
            .put("lateMedian", lateMedian)
            .put("delta", delta)
            .put("allowance", allowance)
            .put("withinAllowance", withinAllowance)
    }

    private data class RssAnalysis(
        val earlyMedianKb: Long,
        val lateMedianKb: Long,
        val deltaKb: Long,
        val toleranceKb: Long,
        val recentIncreases: Int,
        val finalIsMaximum: Boolean,
        val runaway: Boolean,
    ) {
        fun toJson() = JSONObject()
            .put("method", "thresholded_sustained_growth")
            .put("earlyMedianKb", earlyMedianKb)
            .put("lateMedianKb", lateMedianKb)
            .put("deltaKb", deltaKb)
            .put("toleranceKb", toleranceKb)
            .put("recentIncreases", recentIncreases)
            .put("finalIsMaximum", finalIsMaximum)
            .put("runaway", runaway)
    }

    private data class TimingSummary(
        val count: Int,
        val minimum: Double,
        val p50: Double,
        val p95: Double,
        val maximum: Double,
        val mean: Double,
    ) {
        fun toJson() = JSONObject()
            .put("count", count)
            .put("minimumMillis", minimum)
            .put("p50Millis", p50)
            .put("p95Millis", p95)
            .put("maximumMillis", maximum)
            .put("meanMillis", mean)

        companion object {
            fun from(values: List<Double>): TimingSummary {
                require(values.isNotEmpty())
                require(values.all { it.isFinite() && it >= 0.0 })
                val sorted = values.sorted()
                fun percentile(fraction: Double): Double {
                    val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
                    return sorted[index]
                }
                return TimingSummary(
                    count = sorted.size,
                    minimum = sorted.first(),
                    p50 = percentile(0.50),
                    p95 = percentile(0.95),
                    maximum = sorted.last(),
                    mean = sorted.average(),
                )
            }
        }
    }

    private data class ExpectedFixture(
        val width: Int,
        val height: Int,
        val confidenceThreshold: Double,
        val iouThreshold: Double,
        val maxDetections: Int,
        val minimumBoxIou: Double,
        val maximumConfidenceDelta: Double,
        val detections: List<ExpectedDetection>,
    ) {
        fun compare(actual: List<YoloDetection>, label: String): DetectionComparisonSummary {
            assertEquals("$label detection count differs", detections.size, actual.size)
            val unmatched = actual.indices.toMutableSet()
            var minimumIou = 1.0
            var maximumObservedConfidenceDelta = 0.0
            detections.forEachIndexed { expectedIndex, expected ->
                val candidate = requireNotNull(unmatched
                    .asSequence()
                    .filter { index -> actual[index].classId == expected.classId && actual[index].label == expected.label }
                    .map { index -> index to expected.iou(actual[index]) }
                    .maxByOrNull { it.second }) {
                    "$label has no class/label match for expected detection $expectedIndex"
                }
                unmatched.remove(candidate.first)
                val confidenceDelta = abs(actual[candidate.first].confidence - expected.confidence)
                assertTrue("$label IoU ${candidate.second} is below $minimumBoxIou", candidate.second >= minimumBoxIou)
                assertTrue(
                    "$label confidence delta $confidenceDelta exceeds ${this.maximumConfidenceDelta}",
                    confidenceDelta <= this.maximumConfidenceDelta,
                )
                minimumIou = min(minimumIou, candidate.second)
                maximumObservedConfidenceDelta = max(maximumObservedConfidenceDelta, confidenceDelta)
            }
            assertTrue("$label contains unmatched detections: $unmatched", unmatched.isEmpty())
            return DetectionComparisonSummary(minimumIou, maximumObservedConfidenceDelta)
        }

        companion object {
            fun parse(file: File): ExpectedFixture {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                assertEquals(1, root.getInt("schemaVersion"))
                val image = root.getJSONObject("image")
                val options = root.getJSONObject("options")
                val comparison = root.getJSONObject("comparison")
                val array = root.getJSONArray("detections")
                return ExpectedFixture(
                    width = image.getInt("width"),
                    height = image.getInt("height"),
                    confidenceThreshold = options.getDouble("confidenceThreshold"),
                    iouThreshold = options.getDouble("iouThreshold"),
                    maxDetections = options.getInt("maxDetections"),
                    minimumBoxIou = comparison.getDouble("minimumBoxIou"),
                    maximumConfidenceDelta = comparison.getDouble("maximumConfidenceDelta"),
                    detections = (0 until array.length()).map { index ->
                        val value = array.getJSONObject(index)
                        val bounds = value.getJSONArray("bounds")
                        ExpectedDetection(
                            classId = value.getInt("classId"),
                            label = value.getString("label"),
                            confidence = value.getDouble("confidence"),
                            left = bounds.getDouble(0),
                            top = bounds.getDouble(1),
                            right = bounds.getDouble(2),
                            bottom = bounds.getDouble(3),
                        )
                    },
                )
            }
        }
    }

    private data class ExpectedDetection(
        val classId: Int,
        val label: String,
        val confidence: Double,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    ) {
        fun iou(actual: YoloDetection): Double {
            val intersectionWidth = max(0.0, min(right, actual.bounds.right) - max(left, actual.bounds.left))
            val intersectionHeight = max(0.0, min(bottom, actual.bounds.bottom) - max(top, actual.bounds.top))
            val intersection = intersectionWidth * intersectionHeight
            val expectedArea = max(0.0, right - left) * max(0.0, bottom - top)
            val actualArea = max(0.0, actual.bounds.right - actual.bounds.left) *
                max(0.0, actual.bounds.bottom - actual.bounds.top)
            val union = expectedArea + actualArea - intersection
            return if (union > 0.0) intersection / union else 0.0
        }
    }

    private data class DetectionComparisonSummary(
        val minimumIou: Double,
        val maximumConfidenceDelta: Double,
    )

    private data class StoredBoolean(val present: Boolean, val value: Boolean)

    private data class PluginStateSnapshot(
        val packageName: String,
        val enable: StoredBoolean,
        val authorizations: Map<String, StoredBoolean>,
    ) {
        fun restore(context: Context) {
            val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
            val enableEditor = enablePreferences.edit().remove(enableKey(packageName))
            if (enable.present) enableEditor.putBoolean(enableKey(packageName), enable.value)
            check(enableEditor.commit()) { "Unable to restore YOLO provider enable state" }

            val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
            val authorizationEditor = authorizationPreferences.edit()
            authorizations.forEach { (fingerprint, stored) ->
                val key = authorizationKey(packageName, fingerprint)
                authorizationEditor.remove(key)
                if (stored.present) authorizationEditor.putBoolean(key, stored.value)
            }
            check(authorizationEditor.commit()) { "Unable to restore YOLO provider authorization state" }
        }

        companion object {
            fun capture(context: Context, packageName: String, fingerprints: List<String>): PluginStateSnapshot {
                val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
                val key = enableKey(packageName)
                val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
                return PluginStateSnapshot(
                    packageName = packageName,
                    enable = StoredBoolean(
                        present = enablePreferences.contains(key),
                        value = enablePreferences.getBoolean(key, false),
                    ),
                    authorizations = fingerprints.associateWith { fingerprint ->
                        val authorizationKey = authorizationKey(packageName, fingerprint)
                        StoredBoolean(
                            present = authorizationPreferences.contains(authorizationKey),
                            value = authorizationPreferences.getBoolean(authorizationKey, false),
                        )
                    },
                )
            }
        }
    }

    companion object {
        private const val ARG_ENABLED = "yoloR5ResourceEnabled"
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        private const val PROVIDER_PROCESS_NAME = "$PROVIDER_PACKAGE:provider"
        private const val TEST_PACKAGE = "$HOST_PACKAGE.test"
        private const val FIXTURE_DIRECTORY_NAME = "yolo-r5-resource-fixture"
        private const val RESULT_FILE_NAME = "yolo-r5-resource-result.json"
        private const val ENABLE_SP_NAME = "plugin_center_enable_state"
        private const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        private const val PROVIDER_DIAGNOSTICS_PREFIX = "AUTOJS6_YOLO_DIAGNOSTICS_V1="
        private const val YOLO_IMAGE_CACHE_DIRECTORY = "yolo-ipc"
        private const val PROC_RSS_PREFIX = "VmRSS:"
        private const val WARMUP_FRAMES = 20
        private const val MEASURED_FRAMES = 200
        private const val SAMPLE_INTERVAL_FRAMES = 20
        private const val EXPECTED_RESOURCE_SAMPLES = 11
        private const val WINDOW_SAMPLES = 3
        private const val RECENT_RSS_VALUES = 5
        private const val REQUIRED_RECENT_RSS_INCREASES = 3
        private const val FD_GROWTH_ALLOWANCE = 2L
        private const val RSS_MINIMUM_TOLERANCE_KB = 32L * 1024L
        private const val RSS_RELATIVE_TOLERANCE = 0.20
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val DETECT_TIMEOUT_MILLIS = 120_000L
        private const val PROVIDER_CLOSE_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_CLOSE_POLL_MILLIS = 100L
        private val PROVIDER_COMPONENT = ComponentName(
            PROVIDER_PACKAGE,
            "$PROVIDER_PACKAGE.provider.YoloProviderService",
        )

        private fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"
        private fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}
