package org.autojs.autojs.runtime.api.modules.device

import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DevicePageSizeInstrumentationTest {

    @Test
    fun pageSizeIsAReadOnlyJsNumberMatchingTheRunningSystem() {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<Throwable>()
        val engineClass = AtomicReference<Class<*>>()
        val source = StringScriptSource("device-page-size.js", """
            "rhino";
            (function () {
                let original = device.pageSize;
                let descriptor = Object.getOwnPropertyDescriptor(device, "pageSize");
                try { device.pageSize = 1; } catch (ignored) {}
                let afterAssignment = device.pageSize;
                let deleted = delete device.pageSize;
                let redefined = false;
                try {
                    Object.defineProperty(device, "pageSize", { value: 1 });
                    redefined = true;
                } catch (ignored) {}
                return JSON.stringify({
                    value: original,
                    valueType: typeof original,
                    aliasMatches: ${'$'}device === device && ${'$'}device.pageSize === original,
                    readOnly: descriptor.writable === false ||
                        (typeof descriptor.get === "function" && descriptor.set === undefined),
                    afterAssignment: afterAssignment,
                    deleted: deleted,
                    redefined: redefined,
                    finalValue: device.pageSize
                });
            })();
        """.trimIndent())
        val execution = AutoJs.instance.scriptEngineService.execute(source, object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) {
                engineClass.set(execution.engine.javaClass)
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
        if (!completed.await(30, TimeUnit.SECONDS)) {
            execution.engine?.forceStop()
            throw AssertionError("Timed out reading device.pageSize through Rhino")
        }
        failure.get()?.let { throw AssertionError("device.pageSize script failed", it) }
        assertTrue(RhinoJavaScriptEngine::class.java.isAssignableFrom(engineClass.get()))
        val actual = JSONObject(result.get())
        val expected = Os.sysconf(OsConstants._SC_PAGESIZE)
        assertTrue(expected > 0)
        assertEquals("number", actual.getString("valueType"))
        assertEquals(expected, actual.getLong("value"))
        assertEquals(expected, actual.getLong("afterAssignment"))
        assertEquals(expected, actual.getLong("finalValue"))
        assertTrue(actual.getBoolean("aliasMatches"))
        assertTrue(actual.toString(), actual.getBoolean("readOnly"))
        assertFalse(actual.getBoolean("deleted"))
        assertFalse(actual.getBoolean("redefined"))
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("expectedPageSize")?.let { assertEquals(it.toLong(), expected) }
        Log.i("DevicePageSizeTest", "sysconf=$expected result=$actual")
    }
}
