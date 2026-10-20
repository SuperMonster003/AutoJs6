package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P2.5: `ResultAdapter` in Kotlin ([ResultAdapterFunction]) against the module `assets/modules/result-adapter.js`.
 * The module is loaded once per variant with `RhinoUtils.isUiThread()` stubbed (the JVM has no Looper), with Rhino's own
 * Promise (plus `wait` / `await` stubs) as the global `Promise`, a stub `continuation` and `runtime.threads.disposable()`
 * returning a real `VolatileDispose`.
 */
class ResultAdapterFunctionTest {

    private lateinit var sides: BootstrapSides

    @Before
    fun setUp() {
        sides = BootstrapSides()
        sides.both(
            """
            var uiThread = false;
            var RhinoUtils = { isUiThread: function () { return uiThread; } };
            var ContinuationResult = org.autojs.autojs.rhino.continuation.Continuation.Result;
            var runtime = { threads: { disposable: function () { return new org.autojs.autojs.concurrent.VolatileDispose(); } } };
            var continuation = {
                enabled: false,
                create: function () {
                    return {
                        resume: function (v) { this.value = 'resumed:' + v; },
                        resumeError: function (e) { this.value = 'error:' + e; },
                        await: function () { return 'awaited ' + this.value; },
                    };
                },
            };
            Promise.prototype.wait = function () { return 'wait()'; };
            Promise.prototype.await = function () { return 'await()'; };
            var out = [];
            """.trimIndent()
        )
    }

    @After
    fun tearDown() = sides.close()

    private fun install(uiThread: Boolean) {
        sides.both("uiThread = $uiThread;")
        val exported = sides.module(sides.jsScope, sides.original("result-adapter.js"))
        ScriptableObject.defineProperty(sides.jsGlobal, "ResultAdapter", exported, ScriptableObject.READONLY or ScriptableObject.PERMANENT)
        ScriptableObject.defineProperty(sides.ktGlobal, "ResultAdapter", ResultAdapterFunction(sides.ktScope, sides.ktGlobal, uiThread), ScriptableObject.READONLY or ScriptableObject.PERMANENT)
    }

    @Test
    fun surfaceIsTheModules() {
        install(uiThread = false)
        sides.assertSame(
            listOf(
                "[typeof ResultAdapter, ResultAdapter.name, ResultAdapter.length, ResultAdapter.arity]",
                "Object.getOwnPropertyNames(ResultAdapter).sort().join()",
                "(function () { var d = Object.getOwnPropertyDescriptor(ResultAdapter, 'prototype'); return [d.enumerable, d.writable, d.configurable]; })()",
                "Object.keys(ResultAdapter.prototype).join()",
                "Object.keys(ResultAdapter.prototype).map(function (k) { var f = ResultAdapter.prototype[k]; return k + ':' + f.length + ':' + f.hasOwnProperty('prototype'); }).join()",
                "ResultAdapter.prototype.constructor === ResultAdapter",
                "Object.getPrototypeOf(ResultAdapter.prototype) === Object.prototype",
                "Object.keys(Object.getPrototypeOf(ResultAdapter)).join()",
                "[ResultAdapter.promise.length, ResultAdapter.wait.length, ResultAdapter.promise.name, ResultAdapter.wait.name]",
                "Object.getPrototypeOf(Object.getPrototypeOf(ResultAdapter)) === Object.prototype",
                "[ResultAdapter instanceof Function, typeof ResultAdapter.call, Object.prototype.toString.call(ResultAdapter)]",
                "(function () { var d = Object.getOwnPropertyDescriptor(global, 'ResultAdapter'); return [d.enumerable, d.writable, d.configurable]; })()",
            )
        )
        sides.assertRenders(
            Side.KT,
            listOf(
                "[typeof ResultAdapter, ResultAdapter.name, ResultAdapter.length, Object.keys(Object.getPrototypeOf(ResultAdapter)).join(), Object.keys(ResultAdapter.prototype).join(), ResultAdapter instanceof Function]" to
                    "[\"function\",\"constructor\",0,\"promise,wait\",\"constructor,setResult,setError,callback,get\",false]",
                "(function () { var d = Object.getOwnPropertyDescriptor(ResultAdapter, 'prototype'); return [d.enumerable, d.writable, d.configurable]; })()" to "[false,true,false]",
            )
        )
    }

    @Test
    fun instancesOffTheUiThreadUseADisposable() {
        install(uiThread = false)
        sides.assertSame(
            listOf(
                "(function () { var a = new ResultAdapter(); return [Object.keys(a).join(), Object.keys(a.impl).join(), a instanceof ResultAdapter, String(a.disposable.getClass().getSimpleName())]; })()",
                "(function () { var a = new ResultAdapter(); var cb = a.callback(); return [typeof cb, cb.length]; })()",
                "(function () { var c = new ResultAdapter(); c.result = null; c.callback()(8, undefined); return JSON.stringify(c.result); })()",
                "(function () { var c = new ResultAdapter(); c.result = null; c.callback()(8, 'e'); return JSON.stringify(c.result); })()",
            )
        )
        // The module returned the { result } / { error } holder; the Kotlin get() unwraps it like Auto.js did.
        // zh-CN: 原模块返回 { result } / { error } 包装对象; Kotlin 的 get() 与 Auto.js 一样拆开它.
        val cases = listOf(
            "(function () { var a = new ResultAdapter(); a.setResult(3); return a.get(); })()",
            "(function () { var a = new ResultAdapter(); a.setError(new RangeError('r')); return a.get(); })()",
            "(function () { var b = new ResultAdapter(); b.callback()(7); return b.get(); })()",
            "(function () { var b = new ResultAdapter(); b.callback()(7, new TypeError('t')); return b.get(); })()",
            "(function () { var c = new ResultAdapter(); c.result = { result: 9 }; return c.get(); })()",
        )
        sides.assertRenders(
            Side.JS,
            cases.zip(listOf("{\"result\":3}", "{\"error\":{}}", "{\"result\":7}", "{\"error\":{}}", "{\"result\":9}")),
        )
        sides.assertRenders(Side.KT, cases.zip(listOf("3", "throws RangeError [r]", "7", "throws TypeError [t]", "9")))
    }

    @Test
    fun instancesOnTheUiThreadUseAContinuation() {
        install(uiThread = true)
        sides.assertRenders(Side.KT, listOf("(function () { var a = new ResultAdapter(); a.setResult(5); return [Object.keys(a).join(), a.get()]; })()" to "[\"cont,impl\",\"awaited resumed:5\"]"))
        sides.assertSame(
            listOf(
                "(function () { var a = new ResultAdapter(); return [Object.keys(a).join(), Object.keys(a.impl).join()]; })()",
                "(function () { var a = new ResultAdapter(); a.setResult(5); return a.get(); })()",
                "(function () { var a = new ResultAdapter(); a.setError('bad'); return a.get(); })()",
                "(function () { var a = new ResultAdapter(); a.callback()(6); return a.get(); })()",
                "(function () { var a = new ResultAdapter(); a.callback()(6, 'oops'); return a.get(); })()",
            )
        )
    }

    @Test
    fun requireResultAdapterReturnsTheGlobal() {
        // assets/modules/result-adapter.js now only forwards, for require('result-adapter') (api/modules.md, d.ts, ui-ext.js).
        // zh-CN: assets/modules/result-adapter.js 现在只做转发, 供 require('result-adapter') 使用 (api/modules.md, d.ts, ui-ext.js).
        install(uiThread = false)
        assertTrue(sides.module(sides.ktScope, sides.asset("result-adapter.js")) === ScriptableObject.getProperty(sides.ktGlobal, "ResultAdapter"))
    }

    @Test
    fun promiseSettlesWithTheAdapter() {
        install(uiThread = false)
        sides.both(
            """
            var adapter = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
            var p = ResultAdapter.promise(adapter);
            p.then(function (v) { out.push('resolved ' + v); }, function (e) { out.push('rejected ' + e); });
            adapter.resolve('first');
            var early = new org.autojs.autojs.runtime.api.ScriptPromiseAdapter();
            early.reject('early');
            ResultAdapter.promise(early).then(null, function (e) { out.push('rejected ' + e); });
            var duck = { onResolve: function (f) { this.f = f; return this; }, onReject: function (g) { this.g = g; return this; } };
            ResultAdapter.promise(duck).then(function (v) { out.push('duck ' + v); });
            duck.f('quack');
            """.trimIndent()
        )
        sides.assertRenders(Side.KT, listOf("out.join(' | ')" to "\"resolved first | rejected early | duck quack\""))
        sides.assertSame(
            listOf(
                "out.join(' | ')",
                "p instanceof Promise",
                "ResultAdapter.promise({})",
                "ResultAdapter.promise({ onResolve: function () { return null; } })",
            )
        )
    }

    @Test
    fun waitUsesAwaitOnlyWithContinuations() {
        install(uiThread = false)
        sides.assertRenders(
            Side.KT,
            listOf(
                "ResultAdapter.wait(Promise.resolve(1))" to "\"wait()\"",
                "(function () { continuation.enabled = true; try { return ResultAdapter.wait(1); } finally { continuation.enabled = false; } })()" to "\"await()\"",
            )
        )
        sides.assertSame(
            listOf(
                "ResultAdapter.wait(Promise.resolve(1))",
                "ResultAdapter.wait(new org.autojs.autojs.runtime.api.ScriptPromiseAdapter())",
                "(function () { continuation.enabled = true; try { return ResultAdapter.wait(Promise.resolve(1)); } finally { continuation.enabled = false; } })()",
                "(function () { var promise = Object.getPrototypeOf(ResultAdapter).promise; Object.getPrototypeOf(ResultAdapter).promise = function () { return Promise.resolve('replaced'); }; try { return ResultAdapter.wait({}); } finally { Object.getPrototypeOf(ResultAdapter).promise = promise; } })()",
            )
        )
    }

}
