package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Roadmap P2.5: `continuation.await` / `continuation.delay` in Kotlin ([ContinuationPrototype]) against the module
 * `assets/modules/continuation.js`, both as the prototype of a `continuation` object whose `create()` returns a
 * [TestCreator] that captures a real Rhino continuation, run with `executeScriptWithContinuations` and resumed as the
 * engine does: the capture works through the native call, and the resumed value becomes its result.
 */
class ContinuationPrototypeTest {

    private lateinit var sides: BootstrapSides
    private val creators = mutableMapOf<TopLevelScope, MutableList<TestCreator>>()

    @Before
    fun setUp() {
        sides = BootstrapSides()
        install(sides.jsScope, sides.module(sides.jsScope, sides.original("continuation.js")) as ScriptableObject)
        install(sides.ktScope, ContinuationPrototype.create(sides.ktScope))
    }

    @After
    fun tearDown() = sides.close()

    private fun install(scope: TopLevelScope, prototype: ScriptableObject) {
        val continuation = sides.cx.newObject(scope) as NativeObject
        continuation.prototype = prototype
        val list = creators.getOrPut(scope) { mutableListOf() }
        continuation.defineProperty("create", object : org.mozilla.javascript.BaseFunction(scope, null) {
            override fun call(cx: Context, nt: Any?, s: org.mozilla.javascript.VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
                return Context.javaToJS(TestCreator().also { list += it }, s)
            }
        }, ScriptableObject.EMPTY)
        ScriptableObject.putProperty(scope.globalThis, "continuation", continuation)
        sides.cx.evaluateString(
            scope,
            """
            var thenCallback, catchCallback, timer;
            var promise = { then: function (f) { thenCallback = f; return { 'catch': function (g) { catchCallback = g; } }; } };
            var setTimeout = function (f, millis) { timer = { f: f, millis: millis }; };
            """.trimIndent(),
            "setup", 1, null,
        )
    }

    @Test
    fun surfaceIsTheModules() = sides.assertSame(
        listOf(
            "Object.keys(Object.getPrototypeOf(continuation)).join()",
            "['await', 'delay'].map(function (k) { var f = Object.getPrototypeOf(continuation)[k]; var d = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(continuation), k); return [typeof f, f.name, f.length, f.hasOwnProperty('prototype'), d.enumerable, d.writable, d.configurable]; })",
            "Object.getPrototypeOf(Object.getPrototypeOf(continuation)) === Object.prototype",
        )
    )

    @Test
    fun surfaceValues() = sides.assertRenders(
        Side.KT,
        listOf(
            "Object.keys(Object.getPrototypeOf(continuation)).map(function (k) { var f = Object.getPrototypeOf(continuation)[k]; return [k, f.name, f.length, f.hasOwnProperty('prototype')].join(':'); }).join()" to "\"await:await:1:false,delay:delay:1:false\"",
        )
    )

    @Test
    fun awaitSuspendsAndResumesWithTheResult() = both { scope ->
        val (pending, creator) = suspend(scope, "var r = continuation.await(promise); 'resumed with ' + r;")
        callGlobal(scope, "thenCallback", "value")
        assertEquals("value", creator.resumed)
        assertEquals("resumed with value", sides.cx.resumeContinuation(pending.continuation, scope, creator.resumed))
    }

    @Test
    fun awaitPassesTheRejectionToResumeError() = both { scope ->
        val (pending, creator) = suspend(scope, "var r = continuation.await(promise); 'resumed with ' + r;")
        callGlobal(scope, "catchCallback", "reason")
        assertEquals("reason", creator.error)
        assertNull(creator.resumed)
        assertEquals("resumed with x", sides.cx.resumeContinuation(pending.continuation, scope, "x"))
    }

    @Test
    fun awaitWorksFromAScriptFunctionAsPromiseAwaitCallsIt() = both { scope ->
        val (pending, creator) = suspend(scope, "promise.await = function () { return continuation.await(this); }; 'got ' + promise.await();")
        callGlobal(scope, "thenCallback", 7)
        assertEquals("got 7", sides.cx.resumeContinuation(pending.continuation, scope, creator.resumed))
    }

    @Test
    fun delaySchedulesAResumeAndReturnsUndefined() = both { scope ->
        val (pending, creator) = suspend(scope, "var r = continuation.delay(250); [typeof r, timer.millis].join();")
        val timer = ScriptableObject.getProperty(scope.globalThis, "timer") as ScriptableObject
        sides.cx.evaluateString(scope, "timer.f()", "timer", 1, null)
        assertEquals(Undefined.instance, creator.resumed)
        assertEquals("undefined,250", sides.cx.resumeContinuation(pending.continuation, scope, Undefined.instance))
        assertNotNull(timer)
    }

    @Test
    fun requireContinuationReturnsThePrototype() {
        // assets/modules/continuation.js now only forwards, for require('continuation') (documented in api/modules.md).
        // zh-CN: assets/modules/continuation.js 现在只做转发, 供 require('continuation') 使用 (api/modules.md 有说明).
        val exported = sides.module(sides.ktScope, sides.asset("continuation.js"))
        assertTrue(exported === (ScriptableObject.getProperty(sides.ktGlobal, "continuation") as ScriptableObject).prototype)
    }

    private fun both(block: (TopLevelScope) -> Unit) {
        block(sides.jsScope)
        block(sides.ktScope)
    }

    private fun suspend(scope: TopLevelScope, source: String): Pair<ContinuationPending, TestCreator> {
        val script = sides.cx.compileString(source, "main", 1, null)
        val pending = try {
            sides.cx.executeScriptWithContinuations(script, scope)
            throw AssertionError("The script did not suspend")
        } catch (e: ContinuationPending) {
            e
        }
        return pending to creators.getValue(scope).last().also { assertEquals(pending, it.pending) }
    }

    private fun callGlobal(scope: TopLevelScope, name: String, vararg args: Any?) {
        val f = ScriptableObject.getProperty(scope.globalThis, name) as Callable
        f.call(sides.cx, scope, scope.globalThis, arrayOf(*args))
    }

    /** A continuation creator like `org.autojs.autojs.runtime.api.modules.continuation.Creator`, recording what it gets. */
    class TestCreator {

        var pending: ContinuationPending? = null
        var resumed: Any? = null
        var error: Any? = null

        fun await(): Any? {
            val captured = Context.getCurrentContext().captureContinuation()
            pending = captured
            throw captured
        }

        fun resume() {
            resumed = Undefined.instance
        }

        fun resume(result: Any?) {
            resumed = result
        }

        fun resumeError(error: Any?) {
            this.error = error
        }

    }

}
