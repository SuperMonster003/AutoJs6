package org.autojs.autojs.engine

import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Unicode property escapes of `RegExp` in the production script engine. Rhino parsed `\p{...}` with java.util.regex
 * named groups, which Android supports from API 26 only, so on Android 7.x every such expression threw
 * UnsupportedOperationException. Run it on an API 24 device or emulator to cover the fix.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.engine.RhinoUnicodePropertyEscapeDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 生产脚本引擎中 `RegExp` 的 Unicode 属性转义. Rhino 曾用 java.util.regex 命名分组解析 `\p{...}`, Android 自 API 26 起
 * 才支持, 因此在 Android 7.x 上这类表达式全部抛出 UnsupportedOperationException. 在 API 24 设备或模拟器上运行即可覆盖该修复.
 */
class RhinoUnicodePropertyEscapeDeviceTest {

    @Test
    fun unicodePropertyEscapesWorkInScripts() {
        val script = """
            var han = "\u7167\u7247";
            var results = [
                /^\p{L}+$/u.test(han + "abc"),
                /\p{Script=Han}/u.test("a" + han),
                /\p{sc=Latin}/u.test("a"),
                /^\p{Lu}$/u.test("A") && !/^\p{Lu}$/u.test("a"),
                "a1b2".replace(/\P{L}/gu, "_") === "a_b_",
                /\p{gc=Nd}/u.test("5"),
                /^\p{Lu}$/ui.test("a"),
            ];
            runtime.putProperty("rhino.unicodePropertyEscapes", results.join(","));
        """.trimIndent()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) = completed.countDown()
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("rhino-unicode-property-escapes", script), listener, ExecutionConfig(),
        )
        assertTrue("the script did not finish", completed.await(30, TimeUnit.SECONDS))
        assertNull("the script failed: ${failure.get()}", failure.get())
        val engine = execution.engine as RhinoJavaScriptEngine
        assertEquals("true,true,true,true,true,true,true", engine.runtime.getProperty("rhino.unicodePropertyEscapes"))
    }
}
