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
 * Block-scoped `const` and the Uint8Array Base64 / hex methods in the production script engine, brought by the Rhino
 * sync through mozilla/rhino 34abaca89. A `const` inside a loop body of a function kept the value of the first
 * iteration, `for (const x of a)` gave undefined on every iteration, and assigning to a `const` was ignored silently
 * in sloppy mode. The Uint8Array methods use java.util.Base64 (API 26, desugared for older releases) and Buffer
 * positioning, so run it on an API 24 device or emulator as well.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.engine.RhinoConstScopingDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: 生产脚本引擎中的块级 `const` 与 Uint8Array 的 Base64 / 十六进制方法, 来自同步至 mozilla/rhino 34abaca89 的 Rhino.
 * 函数内循环体中的 `const` 曾一直保持第一轮的值, `for (const x of a)` 每轮均为 undefined, 非严格模式下给 `const` 赋值被静默忽略.
 * Uint8Array 的这些方法用到 java.util.Base64 (API 26 起提供, 更早的版本经脱糖) 与 Buffer 定位, 因此也要在 API 24 设备或模拟器上运行.
 */
class RhinoConstScopingDeviceTest {

    @Test
    fun loopBodyConstIsReboundOnEveryIteration() {
        val result = runScript(
            "rhino-loop-const",
            """
            var a = ['x', 'y', 'z'];
            function forBody() { var r = []; for (var i = 0; i < a.length; i++) { const p = a[i]; r.push(p); } return r.join(); }
            function whileBody() { var r = []; var i = 0; while (i < a.length) { const p = a[i]; r.push(p); i++; } return r.join(); }
            function forOfHead() { var r = []; for (const x of a) { r.push(x); } return r.join(); }
            function forInHead() { var r = []; for (const k in { m: 1, n: 2 }) { r.push(k); } return r.join(); }
            result = [forBody(), whileBody(), forOfHead(), forInHead()].join('|');
            """,
        )
        assertEquals("x,y,z|x,y,z|x,y,z|m,n", result)
    }

    @Test
    fun assigningToConstThrowsTypeError() {
        val result = runScript(
            "rhino-const-assignment",
            """
            function sloppy() { const x = 1; try { x = 2; return 'no error'; } catch (e) { return e.name; } }
            function strict() { 'use strict'; const x = 1; try { x = 2; return 'no error'; } catch (e) { return e.name; } }
            result = [sloppy(), strict()].join('|');
            """,
        )
        assertEquals("TypeError|TypeError", result)
    }

    @Test
    fun uint8ArrayBase64AndHexMethodsWork() {
        val result = runScript(
            "rhino-uint8array-base64-hex",
            """
            var bytes = new Uint8Array([104, 105, 251, 255]);
            var target = new Uint8Array(6);
            var written = target.subarray(1).setFromHex('6869fb').written;
            result = [
                bytes.toBase64(),
                bytes.toBase64({ alphabet: 'base64url', omitPadding: true }),
                bytes.toHex(),
                Array.prototype.join.call(Uint8Array.fromBase64('aGn7_w', { alphabet: 'base64url' })),
                Array.prototype.join.call(Uint8Array.fromHex('6869')),
                written,
                Array.prototype.join.call(target),
            ].join('|');
            """,
        )
        assertEquals("aGn7/w==|aGn7_w|6869fbff|104,105,251,255|104,105|3|0,104,105,251,0,0", result)
    }

    /** Runs [body], which assigns the global `result`, and returns that value as a string. */
    private fun runScript(name: String, body: String): Any? {
        val script = "var result;\n" + body.trimIndent() + "\nruntime.putProperty('rhino.result', String(result));"
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) = completed.countDown()
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource(name, script), listener, ExecutionConfig())
        assertTrue("the script did not finish", completed.await(30, TimeUnit.SECONDS))
        assertNull("the script failed: ${failure.get()}", failure.get())
        return (execution.engine as RhinoJavaScriptEngine).runtime.getProperty("rhino.result")
    }
}
