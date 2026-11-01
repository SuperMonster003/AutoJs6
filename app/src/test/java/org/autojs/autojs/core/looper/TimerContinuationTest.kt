package org.autojs.autojs.core.looper

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/**
 * docs/augment/ROADMAP.md Q23: a coroutine resumed by work the [Timer] runs suspends again. The new [ContinuationPending]
 * comes out of that work while the coroutine is already captured and waits to be resumed, so it is not a failure: the
 * timer neither exits the script (formerly on the main looper, in UI mode) nor lets it reach the looper (a resume posted
 * from another thread crashed the app on the main looper).
 *
 * A real Rhino continuation is suspended by `executeScriptWithContinuations` and resumed the way
 * `org.autojs.autojs.rhino.continuation.Continuation.resumeWith` does it: inside a script callback that the timer calls
 * through `RhinoUtils.callFunction` (same thread), or from a runnable posted to the timer outside any top call (another
 * thread).
 */
class TimerContinuationTest {

    private lateinit var cx: Context
    private lateinit var scope: TopLevelScope
    private val pendings = mutableListOf<ContinuationPending>()
    private val exits = mutableListOf<Throwable>()

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
        define("resume") { c, args ->
            c.resumeContinuation(pendings.last().continuation, scope, args[0])
            Undefined.instance
        }
        cx.evaluateString(scope, "var steps = []; var callback = function (value) { resume(value); };", "setup", 1, null)
    }

    @After
    fun tearDown() = Context.exit()

    @Test
    fun aCoroutineResumedInATimerCallbackSuspendsAgainWithoutExitingTheScript() {
        suspendScript()

        mainLooperCallback("first")
        assertTrue("The script must not exit: $exits", exits.isEmpty())
        assertEquals(2, pendings.size)
        assertEquals("first", steps())

        mainLooperCallback("second")
        assertTrue("The script must not exit: $exits", exits.isEmpty())
        assertEquals(3, pendings.size)

        mainLooperCallback("third")
        assertTrue("The script must not exit: $exits", exits.isEmpty())
        assertEquals("first,second,third,done", steps())
    }

    @Test
    fun aResumeSuspendingAgainOffTheMainLooperEndsTheCallbackQuietly() {
        suspendScript()

        // The script thread of a non-UI script: the pending continuation formerly reached its looper, and a callback of
        // setInterval which let it through was cancelled.
        // zh-CN: 非 UI 脚本的脚本线程: 原先该 pending 会抛到其 Looper, 让它抛出的 setInterval 回调会被取消.
        Timer.runCallback({ false }, { exits += it }) { callScriptCallback("first") }
        assertTrue(exits.isEmpty())
        assertEquals(2, pendings.size)
        assertEquals("first", steps())
    }

    @Test
    fun aResumePostedToTheTimerSuspendsAgainWithoutReachingTheLooper() {
        suspendScript()

        // Continuation.resumeWith posts the resume when it is called on another thread; it runs outside any top call.
        // zh-CN: 在其他线程调用 Continuation.resumeWith 时会投递恢复操作, 它在任何顶层调用之外运行.
        posted { cx.resumeContinuation(pendings.last().continuation, scope, "posted") }
        assertEquals(2, pendings.size)
        posted { cx.resumeContinuation(pendings.last().continuation, scope, "again") }
        assertEquals(3, pendings.size)
        posted { cx.resumeContinuation(pendings.last().continuation, scope, "last") }
        assertEquals("posted,again,last,done", steps())
    }

    @Test
    fun otherFailuresOfACallbackAreHandledAsBefore() {
        val error = IllegalStateException("boom")

        Timer.runCallback({ true }, { exits += it }) { throw error }
        assertEquals(listOf<Throwable>(error), exits)

        exits.clear()
        try {
            Timer.runCallback({ false }, { exits += it }) { throw error }
            fail("A failure off the main looper must propagate")
        } catch (e: IllegalStateException) {
            assertSame(error, e)
        }
        assertTrue(exits.isEmpty())

        try {
            posted { throw error }
            fail("A failure of a posted runnable must propagate")
        } catch (e: IllegalStateException) {
            assertSame(error, e)
        }
    }

    @Test
    fun anErrorOfAResumedScriptExitsTheScriptOnTheMainLooperInsteadOfCrashingTheApp() {
        // docs/api-coverage/ROADMAP.md Q205: the error of a rejected await() that no script catches, resumed from another thread.
        // zh-CN: docs/api-coverage/ROADMAP.md Q205: 脚本未捕获的 await() 拒绝, 由其他线程恢复.
        suspendScript()

        Timer.runPosted({ true }, { exits += it }) { cx.resumeContinuationWithError(pendings.last().continuation, scope, "rejected") }

        assertEquals("rejected", (exits.single() as JavaScriptException).value)
    }

    private fun posted(r: Runnable) = Timer.runPosted({ false }, { exits += it }, r)

    private fun suspendScript() {
        val script = cx.compileString(
            "for (var i = 0; i < 3; i++) steps.push(suspendHere()); steps.push('done');",
            "main", 1, null,
        )
        try {
            cx.executeScriptWithContinuations(script, scope)
            fail("The script did not suspend")
        } catch (e: ContinuationPending) {
            assertSame(pendings.single(), e)
        }
    }

    /** A callback of setTimeout on the main looper (UI mode): the timer calls the script function through the bridges. */
    private fun mainLooperCallback(value: String) = Timer.runCallback({ true }, { exits += it }) { callScriptCallback(value) }

    private fun callScriptCallback(value: String) {
        val callback = ScriptableObject.getProperty(scope.globalThis, "callback") as Callable
        RhinoUtils.callFunction(callback, scope, null, arrayOf(value))
    }

    private fun steps(): String = cx.evaluateString(scope, "steps.join()", "steps", 1, null) as String

    private fun define(name: String, body: (Context, Array<out Any?>) -> Any?) {
        val function = object : BaseFunction(scope, null) {
            override fun call(cx: Context, nt: Any?, s: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = body(cx, args)
        }
        ScriptableObject.putProperty(scope.globalThis, name, function)
    }

}
