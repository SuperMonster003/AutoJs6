package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
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
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in R2 acceptance against the independently built and installed NCNN provider.
 *
 * The verifier stages the local-only model and image under the target application's private files
 * directory. Nothing in this test or either production APK embeds those bytes. This deliberately
 * exercises the production host boundary rather than binding the provider AIDL directly.
 */
class YoloRealProviderR2AndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun productionHostUsesRemoteBinderOwnsPfdAndReopensAfterIdempotentClose() {
        assumeTrue(
            "R2 YOLO instrumentation is opt-in; the verifier must pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(TEST_PACKAGE, instrumentation.context.packageName)

        val resultFile = File(context.filesDir, RESULT_FILE_NAME)
        assertTrue("Unable to remove stale R2 result", !resultFile.exists() || resultFile.delete())
        val fixtureRoot = File(context.filesDir, FIXTURE_DIRECTORY_NAME)
        val manifest = fixtureRoot.requireFixture("model-manifest-v1.json")
        val param = fixtureRoot.requireFixture("model.ncnn.param")
        val bin = fixtureRoot.requireFixture("model.ncnn.bin")
        val imageFile = fixtureRoot.requireFixture("bus.jpg")
        val expectedFile = fixtureRoot.requireFixture("expected-bus-detections.json")

        val packageManager = context.packageManager
        val hostUid = packageManager.requireUid(HOST_PACKAGE)
        val providerUid = packageManager.requireUid(PROVIDER_PACKAGE)
        val testUid = instrumentation.context.applicationInfo.uid
        assertTrue("Host and provider unexpectedly share a UID", hostUid != providerUid)
        assertTrue("Provider and instrumentation APK unexpectedly share a UID", providerUid != testUid)
        assertEquals(
            "Host and provider must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, PROVIDER_PACKAGE),
        )
        assertEquals(
            "Host and instrumentation APK must have the same signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(HOST_PACKAGE, TEST_PACKAGE),
        )

        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, PROVIDER_PACKAGE)
        assertTrue("The installed provider has no signer fingerprint", fingerprints.isNotEmpty())
        val state = PluginStateSnapshot.capture(context, PROVIDER_PACKAGE, fingerprints)
        var firstSession: YoloPluginHost.Session? = null
        var reopenedSession: YoloPluginHost.Session? = null
        var primaryFailure: Throwable? = null
        try {
            assertFalse(
                "The independently installed YOLO provider must be default-off before explicit opt-in",
                PluginEnableStore.isEnabled(context, PROVIDER_PACKAGE, defaultEnabled = false),
            )
            fingerprints.forEach { fingerprint ->
                PluginAuthorizationStore.grant(context, PROVIDER_PACKAGE, fingerprint)
            }
            PluginEnableStore.setEnabled(context, PROVIDER_PACKAGE, true)
            assertTrue(PluginAuthorizationStore.isGranted(context, PROVIDER_PACKAGE, fingerprints))
            assertTrue(PluginEnableStore.isEnabled(context, PROVIDER_PACKAGE, defaultEnabled = false))

            val target = runBlocking {
                YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT)
            }
            assertEquals(PROVIDER_COMPONENT, target.component)
            assertEquals(providerUid, target.providerUid)

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
            val session = runBlocking {
                YoloPluginHost.openSession(context, target, model, openOptions)
            }
            firstSession = session
            assertTrue("Production Host retained a local model descriptor", session.modelDescriptorsClosedAfterOpen)
            assertTrue("Production Host did not use a remote IYoloSession Binder", session.isRemoteBinder)
            assertEquals(PROVIDER_COMPONENT, session.component)
            assertEquals(providerUid, session.providerUid)

            val bitmap = requireNotNull(BitmapFactory.decodeFile(imageFile.absolutePath)) {
                "Unable to decode the externally staged bus image"
            }
            val expected = ExpectedFixture.parse(expectedFile)
            try {
                assertEquals(expected.width, bitmap.width)
                assertEquals(expected.height, bitmap.height)
                val detected = runBlocking {
                    session.detect(
                        context,
                        bitmap,
                        YoloPluginHost.DetectOptions(
                            confidenceThreshold = expected.confidenceThreshold,
                            iouThreshold = expected.iouThreshold,
                            maxDetections = expected.maxDetections,
                            timeoutMillis = DETECT_TIMEOUT_MILLIS,
                        ),
                    )
                }
                SystemClock.sleep(LATE_TERMINAL_SETTLE_MILLIS)
                assertTrue(
                    "Production Host retained the local image descriptor after one-way dispatch",
                    session.lastImageDescriptorClosedAfterDispatch,
                )
                assertEquals("Exactly one terminal callback is required", 1, session.lastTerminalCount)
                assertEquals("Terminal callback did not originate from the pinned provider UID", providerUid, session.lastCallbackUid)
                val observation = requireNotNull(session.lastDetectionObservation) {
                    "Production Host did not retain its immutable detection observation"
                }
                assertEquals(1, observation.terminalCount)
                assertEquals(providerUid, observation.callbackUid)
                assertTrue(observation.imageDescriptorClosedAfterDispatch)
                assertEquals(detected.requestId, observation.requestId)
                assertEquals(detected.sequence, observation.sequence)

                val comparisons = expected.compare(detected.detections)
                assertEquals("Expected exactly one bus", 1, detected.detections.count { it.classId == 5 && it.label == "bus" })
                assertEquals("Expected exactly four people", 4, detected.detections.count { it.classId == 0 && it.label == "person" })
                assertEquals("Unexpected extra detections", 5, detected.detections.size)

                session.close()
                session.close()
                assertTrue("Session did not become closed after idempotent close", session.isClosed)
                // close() is a one-way Binder dispatch. Give the provider one bounded cleanup window,
                // then make exactly one reopen attempt; this is not an inference retry.
                SystemClock.sleep(PROVIDER_CLOSE_SETTLE_MILLIS)

                val reopened = runBlocking {
                    YoloPluginHost.openSession(context, target, model, openOptions)
                }
                reopenedSession = reopened
                assertTrue("Reopened session did not use a remote Binder", reopened.isRemoteBinder)
                assertTrue("Reopened session retained a model descriptor", reopened.modelDescriptorsClosedAfterOpen)
                reopened.close()
                reopened.close()
                assertTrue("Reopened session did not close", reopened.isClosed)

                writeResult(
                    resultFile = resultFile,
                    hostUid = hostUid,
                    providerUid = providerUid,
                    testUid = testUid,
                    session = session,
                    detected = detected.detections,
                    comparisons = comparisons,
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
            runCatching { firstSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { state.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun writeResult(
        resultFile: File,
        hostUid: Int,
        providerUid: Int,
        testUid: Int,
        session: YoloPluginHost.Session,
        detected: List<YoloDetection>,
        comparisons: List<DetectionComparison>,
    ) {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("status", "PASS")
            .put("hostPackage", HOST_PACKAGE)
            .put("providerPackage", PROVIDER_PACKAGE)
            .put("testPackage", TEST_PACKAGE)
            .put("component", PROVIDER_COMPONENT.flattenToString())
            .put("hostUid", hostUid)
            .put("providerUid", providerUid)
            .put("testUid", testUid)
            .put("sameSigner", true)
            .put("remoteBinder", session.isRemoteBinder)
            .put("modelDescriptorsClosedAfterOpen", session.modelDescriptorsClosedAfterOpen)
            .put("imageDescriptorClosedAfterDispatch", session.lastImageDescriptorClosedAfterDispatch)
            .put("terminalCount", session.lastTerminalCount)
            .put("callbackUid", session.lastCallbackUid)
            .put("idempotentClose", session.isClosed)
            .put("reopenSucceeded", true)
            .put("detections", JSONArray().apply {
                detected.forEach { detection ->
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
            })
            .put("comparisons", JSONArray().apply {
                comparisons.forEach { comparison ->
                    put(JSONObject()
                        .put("classId", comparison.classId)
                        .put("label", comparison.label)
                        .put("iou", comparison.iou)
                        .put("confidenceDelta", comparison.confidenceDelta))
                }
            })
        resultFile.writeText(json.toString(2), Charsets.UTF_8)
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
        fun compare(actual: List<YoloDetection>): List<DetectionComparison> {
            assertEquals("Detection count differs from the locked fixture", detections.size, actual.size)
            val unmatched = actual.indices.toMutableSet()
            return detections.mapIndexed { expectedIndex, expected ->
                val candidate = requireNotNull(unmatched
                    .asSequence()
                    .filter { index -> actual[index].classId == expected.classId && actual[index].label == expected.label }
                    .map { index -> index to expected.iou(actual[index]) }
                    .maxByOrNull { it.second }) {
                    "No class/label match for expected detection $expectedIndex"
                }
                unmatched.remove(candidate.first)
                val found = actual[candidate.first]
                val confidenceDelta = abs(found.confidence - expected.confidence)
                assertTrue(
                    "Detection $expectedIndex IoU ${candidate.second} is below $minimumBoxIou",
                    candidate.second >= minimumBoxIou,
                )
                assertTrue(
                    "Detection $expectedIndex confidence delta $confidenceDelta exceeds $maximumConfidenceDelta",
                    confidenceDelta <= maximumConfidenceDelta,
                )
                DetectionComparison(expected.classId, expected.label, candidate.second, confidenceDelta)
            }.also {
                assertTrue("Actual result contains unmatched detections: $unmatched", unmatched.isEmpty())
            }
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
                        val item = array.getJSONObject(index)
                        val bounds = item.getJSONArray("bounds")
                        ExpectedDetection(
                            classId = item.getInt("classId"),
                            label = item.getString("label"),
                            confidence = item.getDouble("confidence"),
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

    private data class DetectionComparison(
        val classId: Int,
        val label: String,
        val iou: Double,
        val confidenceDelta: Double,
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
                val enableKey = enableKey(packageName)
                val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
                return PluginStateSnapshot(
                    packageName = packageName,
                    enable = StoredBoolean(
                        present = enablePreferences.contains(enableKey),
                        value = enablePreferences.getBoolean(enableKey, false),
                    ),
                    authorizations = fingerprints.associateWith { fingerprint ->
                        val key = authorizationKey(packageName, fingerprint)
                        StoredBoolean(
                            present = authorizationPreferences.contains(key),
                            value = authorizationPreferences.getBoolean(key, false),
                        )
                    },
                )
            }
        }
    }

    companion object {
        private const val ARG_ENABLED = "yoloR2Enabled"
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        private const val TEST_PACKAGE = "$HOST_PACKAGE.test"
        private const val FIXTURE_DIRECTORY_NAME = "yolo-r2-fixture"
        private const val RESULT_FILE_NAME = "yolo-r2-result.json"
        private const val ENABLE_SP_NAME = "plugin_center_enable_state"
        private const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val DETECT_TIMEOUT_MILLIS = 120_000L
        private const val PROVIDER_CLOSE_SETTLE_MILLIS = 500L
        private const val LATE_TERMINAL_SETTLE_MILLIS = 250L
        private val PROVIDER_COMPONENT = ComponentName(
            PROVIDER_PACKAGE,
            "$PROVIDER_PACKAGE.provider.YoloProviderService",
        )

        private fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"
        private fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}
