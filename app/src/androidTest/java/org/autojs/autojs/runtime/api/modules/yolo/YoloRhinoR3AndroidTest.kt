package org.autojs.autojs.runtime.api.modules.yolo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Opt-in R3 acceptance for the public Rhino API against the independently installed provider.
 *
 * The test deliberately starts a production [JavaScriptFileSource] through ScriptEngineService.
 * The script only knows `yolo.load`, `detector.detect`, and `detector.close`; it never calls the
 * Kotlin Host or provider AIDL directly. Model and image bytes are staged externally by the gate.
 */
class YoloRhinoR3AndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val service
        get() = AutoJs.instance.scriptEngineService

    @Before
    fun settleBefore() {
        service.stopAll()
        awaitNoRegisteredEngines("before YOLO R3 Rhino test")
    }

    @After
    fun settleAfter() {
        service.stopAll()
        awaitNoRegisteredEngines("after YOLO R3 Rhino test")
    }

    @Test
    fun publicYoloApiRunsProductionRhinoThroughExactRemoteProvider() {
        assumeTrue(
            "R3 YOLO instrumentation is opt-in; the verifier must pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(TEST_PACKAGE, instrumentation.context.packageName)

        val fixtureRoot = File(context.filesDir, FIXTURE_DIRECTORY_NAME)
        val modelRoot = File(fixtureRoot, MODEL_DIRECTORY_NAME)
        modelRoot.requireFixture("model.json")
        modelRoot.requireFixture("model.ncnn.param")
        modelRoot.requireFixture("model.ncnn.bin")
        val imageFile = fixtureRoot.requireFixture("bus.jpg")
        val expectedFile = fixtureRoot.requireFixture("expected-bus-detections.json")
        val resultFile = File(context.filesDir, RESULT_FILE_NAME)
        assertTrue("Unable to remove stale R3 result", !resultFile.exists() || resultFile.delete())

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
        val workDir = freshWorkDir()
        var primaryFailure: Throwable? = null
        try {
            val defaultOffObserved = !PluginEnableStore.isEnabled(
                context,
                PROVIDER_PACKAGE,
                defaultEnabled = false,
            )
            assertTrue("The YOLO provider was not default-off before R3 opt-in", defaultOffObserved)
            fingerprints.forEach { fingerprint ->
                PluginAuthorizationStore.grant(context, PROVIDER_PACKAGE, fingerprint)
            }
            PluginEnableStore.setEnabled(context, PROVIDER_PACKAGE, true)

            val expected = ExpectedFixture.parse(expectedFile)
            val marker = File(workDir, SCRIPT_MARKER_NAME)
            val sourceText = createRhinoSource(
                modelDirectory = modelRoot.absolutePath,
                imagePath = imageFile.absolutePath,
                markerPath = marker.absolutePath,
                expected = expected,
            )
            val execution = runViaScriptEngineService(workDir, sourceText)
            assertTrue(
                "Expected the production Rhino engine, but started ${execution.engineClass.name}",
                RhinoJavaScriptEngine::class.java.isAssignableFrom(execution.engineClass),
            )
            assertTrue("The production Rhino script did not write its result marker", marker.isFile)

            val scriptResult = JSONObject(marker.readText(Charsets.UTF_8))
            assertEquals(1, scriptResult.getInt("schemaVersion"))
            assertEquals(PROVIDER_COMPONENT.flattenToShortString(), scriptResult.getString("component"))
            assertTrue("Public yolo global/load surface was not observed", scriptResult.getBoolean("publicApiPresent"))
            assertTrue("The public detector did not tolerate repeated close", scriptResult.getBoolean("idempotentClose"))
            assertTrue("The script did not expose RectF centers", scriptResult.getBoolean("rectCentersUsable"))
            assertEquals(expected.width, scriptResult.getInt("imageWidth"))
            assertEquals(expected.height, scriptResult.getInt("imageHeight"))

            val actual = ActualDetection.parse(scriptResult.getJSONArray("detections"))
            val comparisons = expected.compare(actual)
            assertEquals("Expected exactly one bus", 1, actual.count { it.classId == 5 && it.label == "bus" })
            assertEquals("Expected exactly four people", 4, actual.count { it.classId == 0 && it.label == "person" })
            assertEquals("Unexpected extra detections", 5, actual.size)

            writeResult(
                resultFile = resultFile,
                hostUid = hostUid,
                providerUid = providerUid,
                testUid = testUid,
                defaultOffObserved = defaultOffObserved,
                execution = execution,
                scriptResult = scriptResult,
                detections = actual,
                comparisons = comparisons,
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { state.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                check(workDir.deleteRecursively()) { "Unable to remove YOLO R3 Rhino work directory" }
            }
                .exceptionOrNull()
                ?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun runViaScriptEngineService(workDir: File, sourceText: String): RhinoExecution {
        val completed = CountDownLatch(1)
        val engineClass = AtomicReference<Class<*>>()
        val failure = AtomicReference<Throwable>()
        val sourceFile = File(workDir, SCRIPT_FILE_NAME).apply { writeText(sourceText, Charsets.UTF_8) }
        val source = JavaScriptFileSource(sourceFile)
        assertEquals(JavaScriptSource.ENGINE, source.engineName)

        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                engineClass.set(execution.engine.javaClass)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = try {
            service.execute(
                source,
                listener,
                ExecutionConfig(workingDirectory = workDir.absolutePath),
            )
        } catch (error: Throwable) {
            throw AssertionError("Failed to start the YOLO R3 Rhino script", error)
        }
        if (!completed.await(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            service.stopAll()
            throw AssertionError("Timed out waiting for the YOLO R3 Rhino script")
        }
        awaitEngineRemoved(execution.id)
        failure.get()?.let { error -> throw AssertionError("YOLO R3 Rhino script failed", error) }
        return RhinoExecution(
            engineClass = checkNotNull(engineClass.get()) {
                "ScriptEngineService completed without reporting the started engine"
            },
            sourceSha256 = sha256(sourceFile),
        )
    }

    private fun createRhinoSource(
        modelDirectory: String,
        imagePath: String,
        markerPath: String,
        expected: ExpectedFixture,
    ): String {
        val modelLiteral = JSONObject.quote(modelDirectory)
        val imageLiteral = JSONObject.quote(imagePath)
        val markerLiteral = JSONObject.quote(markerPath)
        val componentLiteral = JSONObject.quote(PROVIDER_COMPONENT.flattenToShortString())
        return """
            "rhino";
            var detector = null;
            var image = null;
            var publicApiPresent = typeof yolo === "object" && typeof yolo.load === "function";
            if (!publicApiPresent) {
              throw new Error("Public yolo.load API is unavailable");
            }
            try {
              detector = yolo.load($modelLiteral, {
                component: $componentLiteral,
                device: "cpu",
                threads: 4,
                decoderId: "ultralytics-detect",
                timeoutMillis: $SESSION_TIMEOUT_MILLIS
              });
              if (detector === null || typeof detector.detect !== "function" || typeof detector.close !== "function") {
                throw new Error("yolo.load returned an invalid detector object");
              }
              image = images.read($imageLiteral, true);
              var detections = detector.detect(image, {
                confidence: ${expected.confidenceThreshold},
                iouThreshold: ${expected.iouThreshold},
                maxDetections: ${expected.maxDetections},
                timeoutMillis: $DETECT_TIMEOUT_MILLIS
              });
              var normalized = detections.map(function (detection) {
                var bounds = detection.bounds;
                return {
                  classId: detection.classId,
                  label: String(detection.label),
                  confidence: detection.confidence,
                  bounds: [bounds.left, bounds.top, bounds.right, bounds.bottom],
                  center: [bounds.centerX(), bounds.centerY()]
                };
              });
              var rectCentersUsable = normalized.every(function (item) {
                var bounds = item.bounds;
                return Math.abs(item.center[0] - ((bounds[0] + bounds[2]) / 2)) < 0.001 &&
                    Math.abs(item.center[1] - ((bounds[1] + bounds[3]) / 2)) < 0.001;
              });
              detector.close();
              detector.close();
              files.write($markerLiteral, JSON.stringify({
                schemaVersion: 1,
                component: $componentLiteral,
                publicApiPresent: publicApiPresent,
                idempotentClose: true,
                rectCentersUsable: rectCentersUsable,
                imageWidth: image.getWidth(),
                imageHeight: image.getHeight(),
                detections: normalized
              }));
            } finally {
              if (image !== null) {
                image.recycle();
              }
              if (detector !== null) {
                detector.close();
              }
            }
        """.trimIndent()
    }

    private fun writeResult(
        resultFile: File,
        hostUid: Int,
        providerUid: Int,
        testUid: Int,
        defaultOffObserved: Boolean,
        execution: RhinoExecution,
        scriptResult: JSONObject,
        detections: List<ActualDetection>,
        comparisons: List<DetectionComparison>,
    ) {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("status", "PASS")
            .put("hostPackage", HOST_PACKAGE)
            .put("providerPackage", PROVIDER_PACKAGE)
            .put("testPackage", TEST_PACKAGE)
            .put("component", PROVIDER_COMPONENT.flattenToShortString())
            .put("hostUid", hostUid)
            .put("providerUid", providerUid)
            .put("testUid", testUid)
            .put("sameSigner", true)
            .put("separateProviderUid", hostUid != providerUid)
            .put("defaultOffObserved", defaultOffObserved)
            .put("productionRhino", true)
            .put("engineClass", execution.engineClass.name)
            .put("scriptSha256", execution.sourceSha256)
            .put("publicApiPresent", scriptResult.getBoolean("publicApiPresent"))
            .put("idempotentClose", scriptResult.getBoolean("idempotentClose"))
            .put("rectCentersUsable", scriptResult.getBoolean("rectCentersUsable"))
            .put("imageWidth", scriptResult.getInt("imageWidth"))
            .put("imageHeight", scriptResult.getInt("imageHeight"))
            .put("detections", JSONArray().apply {
                detections.forEach { detection ->
                    put(JSONObject()
                        .put("classId", detection.classId)
                        .put("label", detection.label)
                        .put("confidence", detection.confidence)
                        .put("bounds", JSONArray(listOf(
                            detection.left,
                            detection.top,
                            detection.right,
                            detection.bottom,
                        )))
                        .put("center", JSONArray(listOf(detection.centerX, detection.centerY))))
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

    private fun freshWorkDir(): File =
        File(context.cacheDir, "yolo-r3-rhino-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "Unable to create YOLO R3 Rhino work directory: $absolutePath" }
        }

    private fun awaitEngineRemoved(executionId: Int) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.none { engine -> engine.id == executionId }) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError("Engine $executionId remained registered after the YOLO R3 Rhino script")
    }

    private fun awaitNoRegisteredEngines(label: String) {
        val deadline = SystemClock.elapsedRealtime() + ENGINE_REMOVAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.engines.isEmpty() && service.scriptExecutions.isEmpty()) return
            SystemClock.sleep(ENGINE_REMOVAL_POLL_MILLIS)
        }
        throw AssertionError(
            "$label left registered engines ${service.engines.map { engine -> engine.id }} " +
                "or tracked executions ${service.scriptExecutions}",
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class RhinoExecution(
        val engineClass: Class<*>,
        val sourceSha256: String,
    )

    private data class ActualDetection(
        val classId: Int,
        val label: String,
        val confidence: Double,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
        val centerX: Double,
        val centerY: Double,
    ) {
        companion object {
            fun parse(array: JSONArray): List<ActualDetection> = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val bounds = item.getJSONArray("bounds")
                val center = item.getJSONArray("center")
                ActualDetection(
                    classId = item.getInt("classId"),
                    label = item.getString("label"),
                    confidence = item.getDouble("confidence"),
                    left = bounds.getDouble(0),
                    top = bounds.getDouble(1),
                    right = bounds.getDouble(2),
                    bottom = bounds.getDouble(3),
                    centerX = center.getDouble(0),
                    centerY = center.getDouble(1),
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
        fun compare(actual: List<ActualDetection>): List<DetectionComparison> {
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
                assertTrue("Detection $expectedIndex has invalid left/right bounds", found.left <= found.right)
                assertTrue("Detection $expectedIndex has invalid top/bottom bounds", found.top <= found.bottom)
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
        fun iou(actual: ActualDetection): Double {
            val intersectionWidth = max(0.0, min(right, actual.right) - max(left, actual.left))
            val intersectionHeight = max(0.0, min(bottom, actual.bottom) - max(top, actual.top))
            val intersection = intersectionWidth * intersectionHeight
            val expectedArea = max(0.0, right - left) * max(0.0, bottom - top)
            val actualArea = max(0.0, actual.right - actual.left) * max(0.0, actual.bottom - actual.top)
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
        private const val ARG_ENABLED = "yoloR3Enabled"
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        private const val TEST_PACKAGE = "$HOST_PACKAGE.test"
        private const val FIXTURE_DIRECTORY_NAME = "yolo-r3-fixture"
        private const val MODEL_DIRECTORY_NAME = "model"
        private const val RESULT_FILE_NAME = "yolo-r3-result.json"
        private const val SCRIPT_FILE_NAME = "yolo-r3-e2e.js"
        private const val SCRIPT_MARKER_NAME = "script-result.json"
        private const val ENABLE_SP_NAME = "plugin_center_enable_state"
        private const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val DETECT_TIMEOUT_MILLIS = 120_000L
        private const val SCRIPT_TIMEOUT_SECONDS = 240L
        private const val ENGINE_REMOVAL_TIMEOUT_MILLIS = 10_000L
        private const val ENGINE_REMOVAL_POLL_MILLIS = 25L
        private val PROVIDER_COMPONENT = ComponentName(
            PROVIDER_PACKAGE,
            "$PROVIDER_PACKAGE.provider.YoloProviderService",
        )

        private fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"
        private fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}
