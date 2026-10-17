package org.autojs.autojs.engine

import android.app.Activity
import android.app.UiAutomation
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor

/** End-to-end public module checks. Requires the Node, Bun, Pinyin and Pinyin4j plugins. */
class RuntimeApiBridgeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val global get() = AutoJs.instance.globalConsole
    private val evidence = JSONArray()

    @Test
    fun bunRevocationAppliesToTheNextExecutionWithoutDisablingOtherGroups() = withBunGrants {
        val before = execute("bun", "return {text: await pinyin.simple('北京上海')};")
        assertEquals("beijingshanghai", before.getString("text"))
        BunHostCapabilityGrants.setTextPinyinGranted(context, false)
        val denied = execute("bun", """
            let code = '';
            try { await pinyin.simple('北京上海'); } catch (error) { code = error.code; }
            const response = await fetch('http://autojs6/v1/text.pinyin', {
              unix: process.env.AUTOJS6_HOST_BRIDGE_SOCKET, method: 'POST',
              headers: {'Content-Type': 'application/json'}, body: JSON.stringify({method:'simple',args:['北京上海']})
            });
            return {granted:host.hasCapability('text.pinyin'), code, status:response.status,
              width:await coordinates.cX(), other:await pinyin4j.of('北京上海')};
        """.trimIndent())
        assertEquals(false, denied.getBoolean("granted"))
        assertEquals("NOT_GRANTED", denied.getString("code"))
        assertEquals(403, denied.getInt("status"))
        assertTrue(denied.getInt("width") > 0)
        assertEquals("beijingshanghai", denied.getString("other"))
        BunHostCapabilityGrants.setTextPinyinGranted(context, true)
        assertEquals("beijingshanghai", execute("bun", "return {text:await pinyin.simple('北京上海')};").getString("text"))
        writeEvidence("bun-revocation")
    }

    @Test
    fun publicPinyinModulesAndConsoleControlsReachTheHost() = withBunGrants {
        for (kind in listOf("node-cjs", "node-esm", "bun")) {
            val report = execute(kind, """
                const text = '北京上海';
                const words = await pinyin.convert(text, {style: 'NORMAL'});
                const simple = await pinyin.simple(text);
                const four = await pinyin4j.of(text, {separator: ' '});
                const readings = await pinyin4j.readings('北京', {tone: 'WITH_TONE_NUMBER'});
                await controls.show();
                await controls.setTitle('runtime-api-$kind');
                await controls.setTitleTextColor('#123456');
                await controls.setContentTextColors({info: '#345678', error: '#ff0000'});
                await controls.setSize(0.7, 0.4);
                await controls.setPosition(0.1, 0.1);
                await controls.setTouchable(true);
                await new Promise(resolve => setTimeout(resolve, 250));
                await controls.hide();
                return {words, simple, four, readings};
            """.trimIndent())
            assertEquals(kind, "[[\"bei\"],[\"jing\"],[\"shang\"],[\"hai\"]]", report.getJSONArray("words").toString())
            assertEquals(kind, "beijingshanghai", report.getString("simple"))
            assertEquals(kind, "bei jing shang hai", report.getString("four"))
            assertEquals(kind, "[[\"bei3\"],[\"jing1\"]]", report.getJSONArray("readings").toString())
        }
        writeEvidence("text-console")
    }

    @Test
    fun coordinateModulesMatchIndependentDisplayAcrossRotationsAndBases() = withBunGrants {
        withRotationHost {
            for (rotation in listOf(0, 1, 2, 3)) {
                turn(rotation)
                val (width, height) = displaySize()
                for (kind in listOf("node-cjs", "node-esm", "bun")) {
                    for ((baseX, baseY) in listOf(720 to 1280, 1080 to 1920, 1920 to 1080)) {
                        val report = execute(kind, """
                            const original = await coordinates.getScaleBases();
                            if (original.x !== 720 || original.y !== 1280) throw Error('Scale state leaked across executions');
                            await coordinates.setScaleBases($baseX, $baseY);
                            const values = [];
                            for (const [method, args] of [
                              ['cX', []], ['cY', []], ['cX', [0.5]], ['cY', [0.5]],
                              ['cX', [360]], ['cY', [640]], ['cXy', [0.5]], ['cYx', [0.5]],
                              ['cXy', [0.5, '16:9']], ['cYx', [0.5, '16:9']],
                              ['cX', [0.5, false]], ['cY', [-0.25, true]],
                              ['cX', [360, 1080, false]], ['cXy', [640, 1920, false]], ['cYx', [360,1080,false]],
                              ['cX', ['360',1080,false]], ['cY', ['640',1920,false]],
                              ['cXy', ['0.5','16:9']], ['cYx', ['0.5','16:9']]
                            ]) values.push(await coordinates[method](...args));
                            let rejected = 0;
                            for (const action of [() => coordinates.cX(NaN), () => coordinates.cY(Infinity), () => coordinates.cX(undefined),
                              () => coordinates.cXy(), () => coordinates.cYx(0.5, '16:0'),
                              () => coordinates.setScaleBases(720, 1280)]) {
                              try { await action(); } catch (_) { rejected++; }
                            }
                            return {info: await coordinates.getInfo(), values, rejected};
                        """.trimIndent())
                        val label = "$kind rotation=$rotation bases=$baseX/$baseY"
                        val expected = listOf(
                            width.toDouble(), height.toDouble(), width * 0.5, height * 0.5,
                            360.0 * width / baseX, 640.0 * height / baseY,
                            height * 0.5 * baseX / baseY, width * 0.5 * baseY / baseX,
                            height * 0.5 * 16 / 9, width * 0.5 * 9 / 16,
                            width * 0.5 / baseX, height * -0.25, width / 3.0, height / 3.0, width / 3.0,
                            width / 3.0, height / 3.0, height * 0.5 * 16 / 9, width * 0.5 * 9 / 16,
                        ).map { floor(it + 0.5) }
                        expected.forEachIndexed { index, value -> assertEquals("$label case=$index", value, report.getJSONArray("values").getDouble(index), 0.0) }
                        assertEquals(label, width, report.getJSONObject("info").getInt("width"))
                        assertEquals(label, height, report.getJSONObject("info").getInt("height"))
                        assertEquals(label, 6, report.getInt("rejected"))
                    }
                }
            }
        }
        writeEvidence("coordinates")
    }

    @Test
    fun longLivedNodeAndBunExecutionsObserveRotationChanges() = withBunGrants {
        withRotationHost {
            for (kind in listOf("node-cjs", "bun")) {
                turn(0)
                val marker = "runtime-rotation-${UUID.randomUUID()}"
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val future = executor.submit<JSONObject> {
                        execute(kind, """
                            const dimensions = [];
                            for (const [index, rotation] of [0,1,3,0].entries()) {
                              let info;
                              const deadline = Date.now() + 20000;
                              do {
                                info = await coordinates.getInfo();
                                if (info.rotation === rotation) break;
                                await new Promise(resolve => setTimeout(resolve, 50));
                              } while (Date.now() < deadline);
                              if (info.rotation !== rotation) throw Error('Rotation did not update');
                              const width = await coordinates.cX(); const height = await coordinates.cY();
                              if (width !== info.width || height !== info.height) throw Error('Stale dimensions');
                              dimensions.push({rotation, width, height});
                              console.log('$marker:' + index);
                            }
                            return {dimensions};
                        """.trimIndent())
                    }
                    for ((index, rotation) in listOf(0, 1, 3, 0).withIndex()) {
                        if (index > 0) turn(rotation)
                        await("$kind rotation report $index") { global.snapshotLogEntries().any { it.content.toString().contains("$marker:$index") } }
                    }
                    assertEquals(4, future.get(30, TimeUnit.SECONDS).getJSONArray("dimensions").length())
                } finally { executor.shutdownNow() }
            }
        }
        writeEvidence("live-rotation")
    }

    private fun execute(kind: String, body: String): JSONObject {
        val marker = "RUNTIME_API_${UUID.randomUUID()}:"
        val imports = if (kind == "bun") """
            const {createHostClient} = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
            const host = await createHostClient();
            const pinyin = host.pinyin, pinyin4j = host.pinyin4j, controls = host.console, coordinates = host.coordinates;
        """.trimIndent() else if (kind == "node-esm") """
            const pinyin = (await import('pinyin')).default, pinyin4j = (await import('pinyin4j')).default;
            const controls = (await import('console')).default, coordinates = (await import('screen_metrics')).default;
        """.trimIndent() else """
            const pinyin = require('pinyin'), pinyin4j = require('pinyin4j');
            const controls = require('console'), coordinates = require('screen_metrics');
        """.trimIndent()
        val source = """
            ${if (kind == "bun") "\"bun\";" else "\"nodejs\";"}
            (async function() {
              $imports
              const result = await (async function() { $body })();
              console.log(${JSONObject.quote(marker)} + JSON.stringify(result));
            })().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; });
        """.trimIndent()
        val since = global.latestLogId
        val output: String
        if (kind == "bun") {
            val console = ConsoleImpl(global.uiHandler, global)
            val engine = BunPluginScriptEngine(context, global) { console }
            try {
                engine.init()
                engine.execute(StringScriptSource("runtime-api.bun.js", source))
            } catch (error: Throwable) {
                throw AssertionError("Bun execution failed: ${console.snapshotLogEntries().joinToString("\n") { it.content.toString() }}", error)
            } finally { engine.destroy() }
            output = console.snapshotLogEntries().joinToString("\n") { it.content.toString() }
            verifyConsole(kind, body, console)
        } else {
            val console = ConsoleImpl(global.uiHandler, global)
            try {
                val capabilities = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("pinyin", "pinyin4j", "console", "device"))
                val result = NodeJsRuntimePluginBridge.runScript(context, NodePluginScriptRequest(
                    source = source, sourceName = if (kind == "node-esm") "runtime-api.mjs" else "runtime-api.cjs",
                    workingDirectory = context.cacheDir.path, timeoutMs = 90_000,
                    runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to capabilities.toJson()),
                    esmEnabled = kind == "node-esm", dynamicImportEnabled = true,
                ), console = console, outputListener = { _, text -> console.println(android.util.Log.DEBUG, text) })
                assertTrue("$kind failed: ${result.errorCode} ${result.errorMessage}\nstdout: ${result.stdout}\nstderr: ${result.stderr}", result.succeeded && !result.timedOut)
                output = result.stdout
                verifyConsole(kind, body, console)
            } finally { console.hide(); console.onRuntimeExit() }
        }
        val resultLine = output.lineSequence().lastOrNull { it.contains(marker) }
            ?: throw AssertionError("$kind did not produce its result: $output")
        val result = JSONObject(resultLine.substringAfter(marker))
        assertTrue("$kind output must also reach the shared log", global.snapshotLogEntries().any { it.id > since && it.content.toString().contains(marker) })
        synchronized(evidence) { evidence.put(JSONObject().put("runtime", kind).put("result", result)) }
        return result
    }

    private fun verifyConsole(kind: String, body: String, console: ConsoleImpl) {
        if (!body.contains("controls.setTitle(")) return
        instrumentation.waitForIdleSync()
        assertEquals("runtime-api-$kind", console.getTitle())
        assertEquals(0xff123456.toInt(), console.getTitleTextColor())
        assertEquals(0xff345678.toInt(), console.configurator.contentTextColors?.get(2))
    }

    private fun withBunGrants(action: () -> Unit) {
        val store = context.getSharedPreferences("bun_host_capability_grants", Context.MODE_PRIVATE)
        val keys = listOf(BunHostCapabilityGrants.TEXT_PINYIN, BunHostCapabilityGrants.TEXT_PINYIN4J, BunHostCapabilityGrants.SCREEN_METRICS, BunHostCapabilityGrants.CONSOLE_CONTROL)
        val previous = keys.associateWith { if (store.contains(it)) store.getBoolean(it, false) else null }
        store.edit().apply { keys.forEach { putBoolean(it, true) } }.commit()
        try { action() } finally {
            store.edit().apply { previous.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) } }.commit()
        }
    }

    private fun withRotationHost(action: () -> Unit) {
        val saved = listOf(Settings.System.USER_ROTATION, Settings.System.ACCELEROMETER_ROTATION)
            .associateWith { Settings.System.getString(context.contentResolver, it) }
        val originalRotation = displayRotation()
        val owned = AtomicReference<RhinoJavaScriptEngine>()
        val failure = AtomicReference<Throwable>()
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("runtime-rotation-fixture.js", """
            "ui rhino";
            ui.layout(<frame><text text="Runtime coordinate validation" gravity="center"/></frame>);
            setInterval(function() {}, 1000);
        """.trimIndent()), object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) { owned.set(execution.engine as RhinoJavaScriptEngine) }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error) }
        }, ExecutionConfig())
        try {
            await("Rotation fixture activity") { failure.get()?.let { throw AssertionError(it) }; owned.get()?.getTag("activity") is Activity }
            instrumentation.runOnMainSync { (owned.get().getTag("activity") as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            action()
        } finally {
            try {
                automation.setRotation(originalRotation)
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
                saved.forEach { (key, value) ->
                    val command = if (value == null) "settings --user current delete system $key" else "settings --user current put system $key $value"
                    ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
                    assertEquals(value, Settings.System.getString(context.contentResolver, key))
                }
            } finally {
                val engine = owned.get() ?: execution.engine
                try {
                    (engine?.getTag("activity") as? Activity)?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
                } finally { engine?.forceStop() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun displaySize(): Pair<Int, Int> = Point().also { display().getRealSize(it) }.let { it.x to it.y }
    private fun display() = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    private fun displayRotation() = display().rotation
    private fun turn(rotation: Int) {
        assertTrue(automation.setRotation(rotation))
        await("Rotation $rotation") { displayRotation() == rotation }
        SystemClock.sleep(350)
        instrumentation.waitForIdleSync()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; SystemClock.sleep(50) }
        throw AssertionError("Timed out: $label")
    }
    private fun writeEvidence(name: String) {
        File(context.getExternalFilesDir(null), "runtime-api-$name.json").writeText(JSONObject().put("cases", evidence).toString(2))
    }
}
