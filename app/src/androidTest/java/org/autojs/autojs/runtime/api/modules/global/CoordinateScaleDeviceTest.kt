package org.autojs.autojs.runtime.api.modules.global

import android.app.Activity
import android.app.UiAutomation
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the public Rhino functions against Android's current display, not ScreenMetrics or
 * CoordinateScale as the expected-value oracle. Each base configuration gets a new production
 * script runtime; one owned UI runtime also observes every rotation without being recreated.
 *
 * The test restores both rotation settings and stops only the scripts it owns. No app data is cleared.
 */
@RunWith(AndroidJUnit4::class)
class CoordinateScaleDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun publicFunctionsTrackRealRotationAndIndependentBaseConfigurations() {
        val saved = RotationState.capture(context, displaySnapshot().rotation)
        recordEvidence("matrix") {
            it.put("status", "running")
                .put("originalRotation", saved.rotation)
                .put("originalUserRotation", saved.userRotation ?: JSONObject.NULL)
                .put("originalAccelerometerRotation", saved.accelerometer ?: JSONObject.NULL)
                .put("verifiedProbeCount", 0)
        }
        var host: RotationHost? = null
        var failure: Throwable? = null
        try {
            host = RotationHost()
            val orientations = mutableSetOf<String>()
            var checked = 0
            val rotations = listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270, Surface.ROTATION_0)
            for ((index, rotation) in rotations.withIndex()) {
                assertTrue("Cannot freeze display at rotation $rotation", automation.setRotation(rotation))
                val display = awaitStableDisplay(rotation)
                orientations += if (display.width > display.height) "landscape" else "portrait"
                host.verifyCurrentDimensions(display, index)
                // The last 0 proves that the same UI runtime updates after 0 -> 90 -> 180 -> 270 -> 0.
                if (index == rotations.lastIndex) continue
                for (bases in BASES) {
                    val probes = matrix(display, bases)
                    val report = runScript("rotation-$rotation-${bases.name}", buildString {
                        appendLine("if (getScaleBaseX() !== 720 || getScaleBaseY() !== 1280) throw new Error('Runtime reused scale bases');")
                        appendLine(bases.setup)
                        appendLine("let results = [];")
                        appendLine("function probe(label, action) {")
                        appendLine("  try { let value = action(); results.push({label: label, value: value, type: typeof value, threw: false}); }")
                        appendLine("  catch (error) { results.push({label: label, threw: true, error: String(error)}); }")
                        appendLine("}")
                        probes.forEach { probe ->
                            appendLine("probe(${JSONObject.quote(probe.label)}, function () { return ${probe.expression}; });")
                        }
                        appendLine("return JSON.stringify({baseX: getScaleBaseX(), baseY: getScaleBaseY(), width: device.width, height: device.height, results: results});")
                    })
                    val label = "rotation=$rotation, display=${display.width}x${display.height}, bases=${bases.name}"
                    recordEvidence("matrix") { evidence ->
                        val samples = JSONArray()
                        val actualResults = report.getJSONArray("results")
                        probes.forEachIndexed { probeIndex, probe ->
                            samples.put(JSONObject()
                                .put("label", probe.label)
                                .put("expression", probe.expression)
                                .put("expectedToThrow", probe.expected == null)
                                .put("expected", probe.expected ?: JSONObject.NULL)
                                .put("actual", actualResults.optJSONObject(probeIndex) ?: JSONObject.NULL))
                        }
                        evidence.array("cases").put(JSONObject()
                            .put("rotation", rotation)
                            .put("independentDisplayWidth", display.width)
                            .put("independentDisplayHeight", display.height)
                            .put("baseConfiguration", bases.name)
                            .put("expectedBaseX", bases.x)
                            .put("expectedBaseY", bases.y)
                            .put("setup", bases.setup)
                            .put("observedBaseX", report.getInt("baseX"))
                            .put("observedBaseY", report.getInt("baseY"))
                            .put("observedDeviceWidth", report.getInt("width"))
                            .put("observedDeviceHeight", report.getInt("height"))
                            .put("probeCount", probes.size)
                            .put("probes", samples))
                        evidence.put("executedProbeCount", checked + probes.size)
                    }
                    assertEquals(label, bases.x, report.getInt("baseX"))
                    assertEquals(label, bases.y, report.getInt("baseY"))
                    assertEquals("$label device.width", display.width, report.getInt("width"))
                    assertEquals("$label device.height", display.height, report.getInt("height"))
                    val results = report.getJSONArray("results")
                    assertEquals(label, probes.size, results.length())
                    probes.forEachIndexed { probeIndex, probe ->
                        val actual = results.getJSONObject(probeIndex)
                        val message = "$label, ${probe.label}: $actual"
                        assertEquals(message, probe.label, actual.getString("label"))
                        if (probe.expected == null) {
                            assertTrue("Expected rejection: $message", actual.getBoolean("threw"))
                            assertTrue(message, actual.optString("error").isNotEmpty())
                        } else {
                            assertTrue("Unexpected rejection: $message", !actual.getBoolean("threw"))
                            assertEquals(message, "number", actual.getString("type"))
                            assertTrue("Non-finite result: $message", !actual.isNull("value"))
                            val tolerance = if (kotlin.math.abs(probe.expected) < 9_007_199_254_740_992.0) 0.0 else Math.ulp(probe.expected) * 4
                            assertEquals(message, probe.expected, actual.getDouble("value"), tolerance)
                        }
                    }
                    checked += probes.size
                    recordEvidence("matrix") { it.put("verifiedProbeCount", checked) }
                    Log.i(TAG, "PASS $label, probes=${probes.size}")
                }
            }
            assertEquals("Both physical display orientations must be exercised", setOf("portrait", "landscape"), orientations)
            recordEvidence("matrix") {
                it.put("status", "passed")
                    .put("orientations", JSONArray(orientations.toList()))
                    .put("sameRuntimeObservationCount", rotations.size)
            }
            Log.i(TAG, "PASS rotation sequence=$rotations, fresh-runtime probes=$checked, same-runtime dimension observations=${rotations.size}")
        } catch (error: Throwable) {
            failure = error
            recordEvidence("matrix") { it.put("status", "failed").put("failure", error.toString()) }
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { restoreRotation(saved) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { host?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            recordEvidence("matrix") {
                it.put("cleanupSucceeded", cleanupFailures.isEmpty())
                    .put("restoredUserRotation", Settings.System.getString(context.contentResolver, Settings.System.USER_ROTATION) ?: JSONObject.NULL)
                    .put("restoredAccelerometerRotation", Settings.System.getString(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) ?: JSONObject.NULL)
                    .put("cleanupFailures", JSONArray(cleanupFailures.map(Throwable::toString)))
                if (cleanupFailures.isNotEmpty()) it.put("status", "failed")
            }
            if (failure != null) cleanupFailures.forEach(failure::addSuppressed)
            else if (cleanupFailures.isNotEmpty()) {
                val first = cleanupFailures.first()
                cleanupFailures.drop(1).forEach(first::addSuppressed)
                throw first
            }
        }
    }

    @Test
    fun invalidPairDoesNotPartiallyConsumeBases() {
        val cases = listOf(
            Triple("invalid-y", "", "setScaleBases(1080, 0)"),
            Triple("same-value-x-invalid-y", "", "setScaleBases(720, 0)"),
            Triple("coercion-y", "", "setScaleBases(1080, {valueOf: function () { throw new Error('Y conversion rejected'); }})"),
            Triple("already-y", "setScaleBaseY(1920);", "setScaleBases(1080, 1280)"),
            Triple("already-x", "setScaleBaseX(1080);", "setScaleBases(720, 1920)"),
        )
        recordEvidence("atomicPair") { it.put("status", "running").put("expectedCaseCount", cases.size) }
        val failures = mutableListOf<String>()
        for ((label, setup, invalidCall) in cases) {
            val recovery = when (label) {
                "already-y" -> "setScaleBaseX(1080)"
                "already-x" -> "setScaleBaseY(1920)"
                else -> "setScaleBases(1080, 1920)"
            }
            val report = runScript("atomic-base-pair-$label", """
                $setup
                let before = [getScaleBaseX(), getScaleBaseY()];
                let rejected = false;
                try { $invalidCall; } catch (error) { rejected = true; }
                let after = [getScaleBaseX(), getScaleBaseY()];
                let recovered = true;
                let recoveryError = '';
                try { $recovery; } catch (error) { recovered = false; recoveryError = String(error); }
                return JSON.stringify({before: before, rejected: rejected, after: after, recovered: recovered,
                    recoveryError: recoveryError, finalBases: [getScaleBaseX(), getScaleBaseY()]});
            """.trimIndent())
            Log.i(TAG, "atomic-base-pair-$label=$report")
            recordEvidence("atomicPair") {
                it.array("cases").put(JSONObject()
                    .put("label", label).put("setup", setup).put("invalidCall", invalidCall)
                    .put("recoveryCall", recovery).put("actual", report))
            }
            if (!report.getBoolean("rejected") || !report.getBoolean("recovered") ||
                report.getJSONArray("before").toString() != report.getJSONArray("after").toString() ||
                report.getJSONArray("finalBases").toString() != "[1080,1920]") {
                failures += "$label ($invalidCall): $report"
            }
        }
        recordEvidence("atomicPair") { it.put("status", if (failures.isEmpty()) "passed" else "failed").put("failures", JSONArray(failures)) }
        assertTrue("Invalid pair must not mutate or consume either base:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun scaleSettersKeepIntegerCoercionAndRejectTruncatedZeroBeforeMutation() {
        recordEvidence("integerCoercion") { it.put("status", "running") }
        for ((label, call) in listOf(
            "x" to "setScaleBaseX(720.5)", "y" to "setScaleBaseY(1280.5)",
            "pair" to "setScaleBases(720.5, 1280.5)",
        )) {
            val report = runScript("fractional-coercion-$label", """
                $call;
                return JSON.stringify({baseX: getScaleBaseX(), baseY: getScaleBaseY()});
            """.trimIndent())
            Log.i(TAG, "fractional-coercion-$label=$report")
            recordEvidence("integerCoercion") {
                it.array("cases").put(JSONObject().put("label", label).put("expression", call)
                    .put("expectedBaseX", 720).put("expectedBaseY", 1280).put("actual", report))
            }
            assertEquals("$call uses the existing integer conversion: $report", 720, report.getInt("baseX"))
            assertEquals("$call uses the existing integer conversion: $report", 1280, report.getInt("baseY"))
        }
        for ((label, invalidCall) in listOf("x" to "setScaleBaseX(0.5)", "y" to "setScaleBaseY(0.5)")) {
            val report = runScript("truncated-zero-$label", """
                let before = [getScaleBaseX(), getScaleBaseY()];
                let rejected = false;
                try { $invalidCall; } catch (error) { rejected = true; }
                let after = [getScaleBaseX(), getScaleBaseY()];
                let recovered = true;
                let recoveryError = '';
                try { setScaleBases(1080, 1920); } catch (error) { recovered = false; recoveryError = String(error); }
                return JSON.stringify({rejected: rejected, before: before, after: after,
                    recovered: recovered, recoveryError: recoveryError});
            """.trimIndent())
            Log.i(TAG, "truncated-zero-$label=$report")
            recordEvidence("integerCoercion") {
                it.array("cases").put(JSONObject().put("label", "truncated-zero-$label")
                    .put("expression", invalidCall).put("expectedToThrow", true).put("actual", report))
            }
            assertTrue("$invalidCall must reject after integer conversion: $report", report.getBoolean("rejected"))
            assertTrue("$invalidCall must leave setters reusable: $report", report.getBoolean("recovered"))
            assertEquals(report.toString(), report.getJSONArray("before").toString(), report.getJSONArray("after").toString())
        }
        recordEvidence("integerCoercion") { it.put("status", "passed") }
    }

    private fun matrix(display: DisplaySnapshot, bases: Bases): List<Probe> = buildList {
        fun ok(label: String, expression: String, expected: Double) { add(Probe(label, expression, expected)) }
        fun reject(label: String, expression: String) { add(Probe(label, expression, null)) }
        ok("cX dimension", "cX()", display.width.toDouble())
        ok("cY dimension", "cY()", display.height.toDouble())
        reject("cXy requires coordinate", "cXy()")
        reject("cYx requires coordinate", "cYx()")
        for (axis in AXES) {
            val dimension = if (axis.width) display.width else display.height
            val absoluteBase = if (axis.width) bases.x else bases.y
            fun expected(value: Double, ratio: Boolean): Double = if (!ratio) reference(value, dimension, absoluteBase.toDouble(), true)
                else if (!axis.cross) reference(value, dimension, 1.0, false)
                else reference(value, dimension, bases.x.toDouble() / bases.y, axis.width)
            for (value in listOf(-384.0, -2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0, 384.0)) {
                val literal = value.toString()
                val automaticRatio = kotlin.math.abs(value) < 1
                val automatic = "${axis.name}($literal)"
                if (axis.cross && !bases.consistent && automaticRatio) reject("$automatic partial default ratio", automatic)
                else ok("$automatic automatic", automatic, expected(value, automaticRatio))
                for (mode in listOf(false, true)) {
                    val call = "${axis.name}($literal, $mode)"
                    if (axis.cross && !bases.consistent && mode) reject("$call partial default ratio", call)
                    else ok("$call shortcut", call, expected(value, mode))
                }
            }
            for (mode in listOf("null", "undefined", "'auto'")) {
                val call = "${axis.name}(0.5, null, $mode)"
                if (axis.cross && !bases.consistent) reject("$call partial default ratio", call)
                else ok("$call automatic mode", call, expected(0.5, true))
            }
            for (shortcut in listOf("false", "true", "'auto'")) {
                val ratio = shortcut != "false"
                for (third in listOf("null", "undefined")) {
                    val call = "${axis.name}(0.5, $shortcut, $third)"
                    if (axis.cross && !bases.consistent && ratio) reject("$call partial default ratio", call)
                    else ok("$call nullish third", call, expected(0.5, ratio))
                }
            }
            // Identity scaling makes these half ties independent of display density or design bases.
            for (value in listOf(-1.5, -0.5, 0.0, 0.5, 1.5)) {
                ok("${axis.name} half tie $value", "${axis.name}($value, $dimension, false)", reference(value, 1, 1.0, false))
            }
            ok("${axis.name} three-argument absolute", "${axis.name}(0.5, $dimension, false)", 1.0)
            ok("${axis.name} beyond Long", "${axis.name}(1e20, $dimension, false)", 1e20)
            ok("${axis.name} intermediate overflow", "${axis.name}(1e308, $dimension, false)", 1e308)
            for (invalid in listOf("NaN", "Infinity", "-Infinity")) {
                reject("${axis.name} nonfinite $invalid", "${axis.name}($invalid)")
            }
            for (invalid in listOf("0", "-1", "0.5", "2147483648", "NaN", "Infinity", "'720'", "{}")) {
                reject("${axis.name} invalid absolute base $invalid", "${axis.name}(100, $invalid, false)")
            }
            reject("${axis.name} invalid mode", "${axis.name}(0.5, null, 'false')")
            reject("${axis.name} duplicate shortcut", "${axis.name}(0.5, false, true)")
            reject("${axis.name} duplicate auto shortcut", "${axis.name}(0.5, 'auto', false)")
            reject("${axis.name} too many arguments", "${axis.name}(1, 720, false, 4)")
            if (axis.cross) {
                for ((number, text) in listOf(9.0 / 16 to "9:16", 16.0 / 9 to "16:9")) {
                    for (value in listOf(-0.25, 0.0, 0.25, 2.0)) {
                        val answer = reference(value, dimension, number, axis.width)
                        ok("${axis.name} numeric $number/$value", "${axis.name}($value, $number, true)", answer)
                        ok("${axis.name} literal $text/$value", "${axis.name}($value, '$text')", answer)
                        ok("${axis.name} literal auto $text/$value", "${axis.name}($value, ' $text ', 'auto')", answer)
                    }
                }
                ok("${axis.name} dynamic auto string", "${axis.name}(100, 'au' + String('to'))", expected(100.0, false))
                for (invalid in listOf("0", "-1", "NaN", "Infinity", "'0:9'", "'1:0'", "'-9:-16'", "'NaN:1'", "'1:Infinity'", "'16:9:1'", "'9'", "{}")) {
                    reject("${axis.name} invalid ratio $invalid", "${axis.name}(0.5, $invalid)")
                }
                reject("${axis.name} literal/absolute conflict", "${axis.name}(0.5, '9:16', false)")
                reject("${axis.name} numeric ratio without forced mode", "${axis.name}(2, 16 / 9)")
                reject("${axis.name} actual overflow", "${axis.name}(Number.MAX_VALUE, 1, true)")
                if (!axis.width) ok("${axis.name} large times tiny ratio", "${axis.name}(1e308, 1e-308, true)", dimension.toDouble())
            } else {
                ok("${axis.name} unused base", "${axis.name}(2, 0, true)", dimension * 2.0)
                ok("${axis.name} dynamic auto string", "${axis.name}(0.5, 'au' + String('to'))", reference(0.5, dimension, 1.0, false))
                reject("${axis.name} actual overflow", "${axis.name}(Number.MAX_VALUE, true)")
            }
        }
    }

    /** Evaluate the documented formulas; decimal rounding avoids a Long clamp or a signed-half shortcut. */
    private fun reference(value: Double, dimension: Int, baseOrRatio: Double, divide: Boolean): Double {
        // Ordinary matrix values are small. Separate probes use known finite results beyond Long
        // and through intermediate overflow, so this oracle does not copy the production fallback.
        val product = dimension.toDouble() * value
        val scaled = BigDecimal.valueOf(if (divide) product / baseOrRatio else product * baseOrRatio)
        val lower = scaled.setScale(0, RoundingMode.FLOOR)
        return (if (scaled.subtract(lower) >= BigDecimal("0.5")) lower.add(BigDecimal.ONE) else lower).toDouble()
    }

    private fun runScript(label: String, body: String): JSONObject {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<Throwable>()
        val engine = AtomicReference<RhinoJavaScriptEngine>()
        val source = StringScriptSource("coordinate-device-$label-${UUID.randomUUID()}.js", "\"rhino\";\n(function () {\n$body\n})();")
        val execution = AutoJs.instance.scriptEngineService.execute(source, object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                val candidate = execution.engine as? RhinoJavaScriptEngine
                if (candidate == null) failure.set(AssertionError("$label did not use the production Rhino engine"))
                else engine.set(candidate)
            }
            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                result.set(value.toString())
                completed.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }, ExecutionConfig())
        try {
            assertTrue("$label timed out", completed.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("$label failed through the Rhino script entry", it) }
            assertTrue("$label never started Rhino", engine.get() != null)
            return JSONObject(requireNotNull(result.get()) { "$label did not return a report" })
        } finally {
            val owned = engine.get() ?: execution.engine as? RhinoJavaScriptEngine
            owned?.let {
                if (it.thread !== Looper.getMainLooper().thread) it.thread.join(5_000)
                if (!it.isDestroyed) it.forceStop()
            }
        }
    }

    private inner class RotationHost : AutoCloseable {
        private val marker = UUID.randomUUID().toString()
        private val engine = AtomicReference<RhinoJavaScriptEngine>()
        private val failure = AtomicReference<Throwable>()
        private var originalOrientation: Int? = null
        private val execution: ScriptExecution

        init {
            val source = StringScriptSource("coordinate-rotation-host-$marker.js", """
                "ui rhino";
                ui.layout(<frame><text text="Coordinate API device validation" gravity="center"/></frame>);
                let previousRequest = '';
                setInterval(function () {
                    let request = String(runtime.getProperty('coordinate.device.request'));
                    if (request !== previousRequest) {
                        runtime.putProperty('coordinate.device.report', JSON.stringify({request: request,
                            width: cX(), height: cY(), deviceWidth: device.width, deviceHeight: device.height}));
                        previousRequest = request;
                    }
                }, 25);
                runtime.putProperty('coordinate.device.ready', true);
            """.trimIndent())
            execution = AutoJs.instance.scriptEngineService.execute(source, object : SimpleScriptExecutionListener() {
                override fun onStart(execution: ScriptExecution) {
                    val candidate = execution.engine as? RhinoJavaScriptEngine
                    if (candidate == null) failure.set(AssertionError("Rotation host is not Rhino")) else engine.set(candidate)
                }
                override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error) }
            }, ExecutionConfig())
            try {
                await("Owned UI script is ready") {
                    assertHealthy()
                    engine.get()?.runtime?.getProperty("coordinate.device.ready") == true && engine.get()?.getTag("activity") is Activity
                }
                instrumentation.runOnMainSync {
                    val activity = activity()
                    originalOrientation = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            } catch (error: Throwable) { close(); throw error }
        }

        private fun activity() = requireNotNull(engine.get()?.getTag("activity") as? Activity)
        private fun assertHealthy() { failure.get()?.let { throw AssertionError("Owned rotation UI script failed", it) } }

        fun verifyCurrentDimensions(display: DisplaySnapshot, index: Int) {
            assertHealthy()
            val current = requireNotNull(engine.get())
            assertTrue("Rotation must not replace the owned UI runtime", !current.isDestroyed)
            val request = "$marker-$index"
            current.runtime.putProperty("coordinate.device.request", request)
            var report: JSONObject? = null
            await("UI runtime observes rotation ${display.rotation}") {
                assertHealthy()
                report = current.runtime.getProperty("coordinate.device.report")?.toString()?.let(::JSONObject)
                report?.optString("request") == request
            }
            val actual = requireNotNull(report)
            recordEvidence("matrix") {
                it.array("sameRuntimeObservations").put(JSONObject()
                    .put("rotation", display.rotation)
                    .put("independentDisplayWidth", display.width)
                    .put("independentDisplayHeight", display.height)
                    .put("actual", actual))
            }
            assertEquals("Same-runtime cX after rotation ${display.rotation}: $actual", display.width, actual.getInt("width"))
            assertEquals("Same-runtime cY after rotation ${display.rotation}: $actual", display.height, actual.getInt("height"))
            assertEquals(actual.toString(), display.width, actual.getInt("deviceWidth"))
            assertEquals(actual.toString(), display.height, actual.getInt("deviceHeight"))
        }

        override fun close() {
            val owned = engine.get() ?: execution.engine as? RhinoJavaScriptEngine ?: return
            (owned.getTag("activity") as? Activity)?.let { activity ->
                instrumentation.runOnMainSync {
                    originalOrientation?.let { activity.requestedOrientation = it }
                    activity.finish()
                }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (!owned.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            if (!owned.isDestroyed) owned.forceStop()
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySnapshot(): DisplaySnapshot {
        val display = requireNotNull((context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY))
        val size = Point()
        display.getRealSize(size)
        return DisplaySnapshot(display.rotation, size.x, size.y)
    }

    private fun awaitStableDisplay(rotation: Int): DisplaySnapshot {
        var previous: DisplaySnapshot? = null
        var stable = 0
        var result: DisplaySnapshot? = null
        await("Real display reaches stable rotation $rotation") {
            val current = displaySnapshot()
            stable = if (current == previous && current.rotation == rotation && current.width > 0 && current.height > 0) stable + 1 else 0
            previous = current
            result = current
            stable >= 4
        }
        instrumentation.waitForIdleSync()
        return requireNotNull(result)
    }

    private fun restoreRotation(saved: RotationState) {
        assertTrue("Restore initial display rotation ${saved.rotation}", automation.setRotation(saved.rotation))
        assertTrue("Release test rotation override", automation.setRotation(UiAutomation.ROTATION_UNFREEZE))
        for ((key, value) in listOf(Settings.System.USER_ROTATION to saved.userRotation, Settings.System.ACCELEROMETER_ROTATION to saved.accelerometer)) {
            val command = if (value == null) "settings --user current delete system $key" else "settings --user current put system $key $value"
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
            assertEquals("Restore system rotation setting $key", value, Settings.System.getString(context.contentResolver, key))
        }
        Log.i(TAG, "Restored rotation settings: $saved")
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        do {
            if (condition()) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(message)
    }

    /** Keep all test methods in this instrumentation invocation in one pullable, crash-safe report. */
    private fun recordEvidence(section: String, update: (JSONObject) -> Unit) = synchronized(EVIDENCE_LOCK) {
        val report = evidence ?: JSONObject()
            .put("schemaVersion", 1)
            .put("runId", UUID.randomUUID().toString())
            .put("startedAtMillis", System.currentTimeMillis())
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("packageName", context.packageName)
            .put("sections", JSONObject())
            .also { evidence = it }
        val sections = report.getJSONObject("sections")
        val current = sections.optJSONObject(section) ?: JSONObject().also { sections.put(section, it) }
        update(current)
        report.put("updatedAtMillis", System.currentTimeMillis())
        val directory = requireNotNull(context.getExternalFilesDir(null)) { "External files directory is unavailable for coordinate evidence" }
        val file = File(directory, "coordinate-scale-device.json")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(report.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray().also { put(name, it) }

    private data class Probe(val label: String, val expression: String, val expected: Double?)
    private data class Axis(val name: String, val width: Boolean, val cross: Boolean)
    private data class DisplaySnapshot(val rotation: Int, val width: Int, val height: Int)
    private data class Bases(val name: String, val x: Int, val y: Int, val consistent: Boolean, val setup: String)
    private data class RotationState(val rotation: Int, val userRotation: String?, val accelerometer: String?) {
        companion object {
            fun capture(context: Context, rotation: Int): RotationState {
                fun read(key: String): String? = Settings.System.getString(context.contentResolver, key)?.also {
                    require(it.matches(Regex("[0-3]"))) { "Unexpected rotation setting $key=$it" }
                }
                return RotationState(rotation, read(Settings.System.USER_ROTATION), read(Settings.System.ACCELEROMETER_ROTATION))
            }
        }
    }

    companion object {
        private const val TAG = "CoordinateScaleDevice"
        private val EVIDENCE_LOCK = Any()
        private var evidence: JSONObject? = null
        private val AXES = listOf(Axis("cX", true, false), Axis("cY", false, false), Axis("cYx", true, true), Axis("cXy", false, true))
        private val BASES = listOf(
            Bases("default", 720, 1280, true, ""),
            Bases("explicit-720x1280", 720, 1280, true, "setScaleBases(720, 1280);"),
            Bases("1080x1920", 1080, 1920, true, "setScaleBases(1080, 1920);"),
            Bases("1920x1080", 1920, 1080, true, "setScaleBases(1920, 1080);"),
            Bases("partial-x", 1080, 1280, false, "setScaleBaseX(1080);"),
            Bases("partial-y", 720, 1920, false, "setScaleBaseY(1920);"),
        )
    }
}
