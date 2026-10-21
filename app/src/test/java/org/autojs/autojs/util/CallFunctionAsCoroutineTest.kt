package org.autojs.autojs.util

import org.autojs.autojs.rhino.TopLevelScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.ContinuationTopCall
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * docs/augment/ROADMAP.md Q23 (follow-up): a listener of an EventEmitter dispatched while no script code runs is called as the
 * top of a coroutine ([RhinoUtils.callFunctionAsCoroutine], [ContinuationTopCall]), so `await()` in it suspends instead of
 * failing with "Cannot capture continuation from JavaScript code not called directly by executeScriptWithContinuations"
 * (the back_pressed listener of the sample "协程/UI模式中使用协程"). Real Rhino continuations, resumed as
 * `org.autojs.autojs.rhino.continuation.Continuation.resumeWith` does.
 */
class CallFunctionAsCoroutineTest {

    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope
    private val pendings = mutableListOf<ContinuationPending>()

    @Before
    fun setUp() {
        cx = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
        define("suspendHere") { _, _ ->
            throw cx.captureContinuation().also { pendings += it }
        }
        define("canCallHere") { c, args ->
            ContinuationTopCall.canCall(c, args[0])
        }
        eval(
            """
            var log = [];
            var emitter = { name: 'emitter' };
            var event = { consumed: false };
            var suspending = function (e) {
                e.consumed = true;
                log.push('before ' + this.name);
                log.push('got ' + suspendHere());
                log.push('after');
            };
            var plain = function (e) { log.push('plain ' + this.name); return 'returned'; };
            var arrow = (e) => { log.push('arrow got ' + suspendHere()); };
            var twice = function () { log.push('first ' + suspendHere()); log.push('second ' + suspendHere()); };
            """
        )
    }

    @After
    fun tearDown() = Context.exit()

    @Test
    fun aListenerSuspendsAndIsResumedLater() {
        assertSame(Undefined.instance, callListener("suspending"))
        assertEquals("before emitter", log())
        assertEquals(true, eval("event.consumed"))
        assertEquals(1, pendings.size)

        cx.resumeContinuation(pendings.single().continuation, scope, "x")
        assertEquals("before emitter,got x,after", log())
    }

    @Test
    fun theNextListenerRunsWhileOneIsSuspended() {
        callListener("suspending")
        assertEquals("returned", callListener("plain"))
        assertEquals("before emitter,plain emitter", log())

        cx.resumeContinuation(pendings.single().continuation, scope, "later")
        assertEquals("before emitter,plain emitter,got later,after", log())
    }

    @Test
    fun arrowFunctionsCanSuspend() {
        callListener("arrow")
        assertEquals(1, pendings.size)
        cx.resumeContinuation(pendings.single().continuation, scope, 7)
        assertEquals("arrow got 7", log())
    }

    @Test
    fun aResumedListenerCanSuspendAgain() {
        callListener("twice")
        try {
            cx.resumeContinuation(pendings.last().continuation, scope, "a")
            fail("The listener did not suspend again")
        } catch (e: ContinuationPending) {
            // Reaches whoever resumed it, as for the main script (Timer ignores it, see TimerContinuationTest).
            // zh-CN: 抛给恢复它的一方, 与主脚本相同 (Timer 会忽略它, 见 TimerContinuationTest).
            assertSame(pendings.last(), e)
        }
        cx.resumeContinuation(pendings.last().continuation, scope, "b")
        assertEquals("first a,second b", log())
    }

    @Test
    fun onlyInterpretedScriptFunctionsWithoutAPendingTopCallQualify() {
        assertTrue(ContinuationTopCall.canCall(cx, global("plain")))
        assertTrue(ContinuationTopCall.canCall(cx, global("arrow")))
        assertFalse("A native function", ContinuationTopCall.canCall(cx, global("suspendHere")))
        assertFalse("A bound function", ContinuationTopCall.canCall(cx, eval("plain.bind(emitter)")))
        assertEquals("Inside running script code", false, eval("canCallHere(plain)"))
    }

    private fun callListener(name: String): Any? {
        val thisObj = global("emitter") as Scriptable
        return RhinoUtils.callFunctionAsCoroutine(null, global(name) as Callable, scope, thisObj, arrayOf(global("event")))
    }

    private fun global(name: String): Any? = ScriptableObject.getProperty(scope.globalThis, name)

    private fun log(): String = eval("log.join()") as String

    private fun eval(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun define(name: String, body: (Context, Array<out Any?>) -> Any?) {
        val function = object : BaseFunction(scope, null) {
            override fun call(cx: Context, nt: Any?, s: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = body(cx, args)
        }
        ScriptableObject.putProperty(scope.globalThis, name, function)
    }

}
