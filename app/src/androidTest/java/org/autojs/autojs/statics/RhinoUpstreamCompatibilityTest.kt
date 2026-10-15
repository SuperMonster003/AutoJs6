package org.autojs.autojs.statics

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RhinoUpstreamCompatibilityTest {
    @Test
    fun fromEntriesPreservesNumericPropertiesAndConsoleOutput() = runScript(false, """
        console.log(Object.fromEntries([0, 6].map((v) => [v, true])));
        function check(value, label) { if (!value) throw new Error(label); }
        var obj = Object.fromEntries([0, 6].map((v) => [v, true]));
        check(obj[0] === true && obj[6] === true, "numeric property reads");
        check(obj["0"] === true && obj["6"] === true, "string property reads");
        check(JSON.stringify(obj) === '{"0":true,"6":true}', "JSON serialization");
        [0, 6].forEach(function(key) {
            var desc = Object.getOwnPropertyDescriptor(obj, String(key));
            check(Object.prototype.hasOwnProperty.call(obj, key), "own property " + key);
            check(desc && desc.value === true && desc.writable && desc.enumerable && desc.configurable,
                  "data descriptor " + key);
        });
        var strings = Object.fromEntries([["0", true], ["6", true]]);
        check(strings[0] === true && strings[6] === true, "numeric string keys");
        var duplicate = Object.fromEntries([[6, "first"], ["6", "second"], [6, "last"]]);
        check(duplicate[6] === "last" && Object.keys(duplicate).length === 1, "duplicate keys");
        var symbol = Symbol("key");
        var symbols = Object.fromEntries([[Object(symbol), true]]);
        check(symbols[symbol] === true && Object.getOwnPropertySymbols(symbols)[0] === symbol,
              "wrapped symbol key");
    """.trimIndent(), verifyLogs = { logs ->
        assertTrue("Original example did not log both keys: $logs", logs.any { line ->
            runCatching { JSONObject(line) }.getOrNull()?.let { value ->
                value.length() == 2 && value.opt("0") == true && value.opt("6") == true
            } == true
        })
    })

    @Test
    fun productionEngineSupportsUpstreamFeaturesAndJavaInterop() = runScript(false, """
        function check(value, label) { if (!value) throw new Error(label); }
        function Target() { this.target = new.target; }
        check(new Target().target === Target, "new.target");
        function Alternate() {}
        check(Reflect.construct(Target, [], Alternate).target === Alternate, "Reflect.construct");
        var buffer = new ArrayBuffer(8, { maxByteLength: 16 });
        var view = new DataView(buffer);
        view.setInt32(0, 0x12345678, true);
        check(new DataView(buffer.slice(0, 4)).getInt32(0, true) === 0x12345678, "ArrayBuffer.slice");
        buffer.resize(12);
        var moved = buffer.transfer();
        check(buffer.detached && moved.byteLength === 12, "ArrayBuffer.transfer");
        check(new BigInt64Array([1n, 2n]).with(1, 9n)[1] === 9n, "BigInt typed array");
        check(String(new java.lang.StringBuilder("a").append("b")) === "ab", "Java reflection");
        var invoked = false;
        new JavaAdapter(java.lang.Runnable, { run: function() { invoked = true; } }).run();
        check(invoked, "JavaAdapter callback");
        var literal = <vertical><text id="label" text="Rhino"/></vertical>;
        check(String(literal.text.@text) === "Rhino", "E4X");
        var result = sync(function(value) { return value + 1; })(41);
        check(result === 42, "host callback");
    """.trimIndent())

    @Test
    fun uiModeParsesAndInflatesXmlWithAutoJsConfiguration() = runScript(true, """
        ui.layout(<vertical><text id="rhinoSyncLabel" text="Rhino upstream"/></vertical>);
        if (String(ui.rhinoSyncLabel.getText()) !== "Rhino upstream") {
            throw new Error("UI XML did not produce the expected text view");
        }
    """.trimIndent())

    @Test
    fun restoredKotlinReflectionBindingsPreserveScriptBehavior() = runScript(false, """
        function check(value, label) { if (!value) throw new Error(label); }
        function rejects(action, label) {
            var rejected = false;
            try { action(); } catch (e) { rejected = true; }
            check(rejected, label);
        }
        check(s13n.time(1500, "ms", "s") === 1.5, "time conversion");
        check(s13n.time(12, "h", "days") === 0.5, "time conversion to days");
        check(WebSocket.CODE_CLOSE_NORMAL === 1000, "WebSocket constant");
        check(WebSocket.EVENT_OPEN === "open", "WebSocket event");
        check(typeof text === "function" && typeof selector === "function", "selector globals");
        var configField = runtime.notice.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        var config = configField.get(runtime.notice);
        var originalDynamic = String(config.useDynamicDefaultNotificationId) === "true";
        var originalChannel = String(config.defaultChannelId);
        notice.config({
            defaultTitle: "reflect-title",
            defaultAutoCancel: true,
            useDynamicDefaultNotificationId: !originalDynamic,
            defaultChannelId: "reflect-channel"
        });
        check(String(config.defaultTitle) === "reflect-title", "notice string setter");
        check(String(config.defaultAutoCancel) === "true", "notice nullable boolean setter");
        check(String(config.useDynamicDefaultNotificationId) === String(!originalDynamic), "notice boolean setter");
        check(String(config.defaultChannelId) === "reflect-channel", "notice custom accessor");
        notice.config({
            defaultTitle: null,
            defaultAutoCancel: undefined,
            useDynamicDefaultNotificationId: null,
            defaultChannelId: undefined
        });
        check(config.defaultTitle === null && config.defaultAutoCancel === null, "notice nullable reset");
        check(String(config.useDynamicDefaultNotificationId) === String(originalDynamic), "notice default switch reset");
        check(String(config.defaultChannelId) === originalChannel, "notice computed default reset");
        rejects(function() { notice.config({privateDefaultChannelId: "hidden"}); }, "private property");
        rejects(function() { notice.config({pristine: true}); }, "method is not a property");
        rejects(function() { notice.config({missing: true}); }, "unknown property");
        rejects(function() { notice.config({useDynamicDefaultNotificationId: "wrong type"}); }, "wrong type");
        rejects(function() { notice.config([]); }, "non-object configuration");
    """.trimIndent())

    private fun runScript(uiMode: Boolean, body: String, verifyLogs: ((List<String>) -> Unit)? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "rhino-upstream-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        val marker = File(directory, "result.txt")
        val script = File(directory, "regression.js")
        script.writeText(buildString {
            appendLine(if (uiMode) "\"ui rhino\";" else "\"rhino\";")
            appendLine(body)
            appendLine("files.write(${JSONObject.quote(marker.absolutePath)}, 'ok');")
            if (uiMode) appendLine("ui.finish();")
        })
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val engineClass = AtomicReference<Class<*>>()
        val console = AtomicReference<ConsoleImpl>()
        val logs = CopyOnWriteArrayList<String>()
        val logListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) {
                logs += logEntry.content.toString()
            }

            override fun onLogClear() = Unit
        }
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                engineClass.set(execution.engine.javaClass)
                ((execution.engine as? RhinoJavaScriptEngine)?.runtime?.console as? ConsoleImpl)?.let {
                    console.set(it)
                    it.addLogListener(logListener)
                }
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        var execution: ScriptExecution? = null
        try {
            val source = JavaScriptFileSource(script)
            assertEquals(JavaScriptSource.ENGINE, source.engineName)
            execution = AutoJs.instance.scriptEngineService.execute(
                source, listener,
                ExecutionConfig(workingDirectory = directory.absolutePath),
            )
            assertTrue("Rhino regression script timed out", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Rhino regression script failed", it) }
            assertTrue(RhinoJavaScriptEngine::class.java.isAssignableFrom(engineClass.get()))
            assertTrue("Rhino script did not complete its assertions", marker.isFile)
            assertEquals("ok", marker.readText())
            verifyLogs?.invoke(logs.toList())
        } finally {
            console.get()?.removeLogListener(logListener)
            execution?.engine?.let { if (!it.isDestroyed) it.forceStop() }
            marker.delete()
            script.delete()
            directory.delete()
        }
    }
}
