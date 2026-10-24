package org.autojs.autojs.rhino.continuation

import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.rhino.TopLevelScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * docs/augment/ROADMAP.md Q98: [Continuation.resumeWith] called on another thread before the script suspends, as
 * `示例代码/协程/协程HelloWorld` does when the thread it starts comes first. The resume is posted to the timer of the
 * coroutine and takes the pending continuation there, after the script has suspended; it formerly took it on the calling
 * thread and threw "resume() should be called after suspend()".
 *
 * A real Rhino continuation is captured by `executeScriptWithContinuations` in an [AutoJsContext]; the timer is a queue
 * that the test thread (the script thread) runs once the script has suspended, as its looper would.
 */
class ContinuationResumeTest {

    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = AutoJsContext(this).apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
    }

    private lateinit var cx: AutoJsContext
    private lateinit var scope: TopLevelScope
    private lateinit var continuation: Continuation
    private val timer = ConcurrentLinkedQueue<Runnable>()

    @Before
    fun setUp() {
        cx = factory.enterContext() as AutoJsContext
        scope = TopLevelScope().apply { initStandardObjects(cx, false) }
        continuation = Continuation(cx, scope) { timer += it }
        val global = scope.globalThis as ScriptableObject
        global.defineProperty("suspendHere", object : BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope)) {
            override fun call(cx: Context, nt: Any?, s: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = continuation.suspend()
        }, ScriptableObject.DONTENUM)
        cx.evaluateString(scope, "var steps = [];", "setup", 1, null)
    }

    @After
    fun tearDown() = Context.exit()

    @Test
    fun aResumeFromAThreadThatComesFirstWaitsForTheSuspension() {
        var failure: Throwable? = null
        val worker = Thread {
            try {
                continuation.resumeWith(Continuation.Result.success("from thread"))
            } catch (e: Throwable) {
                failure = e
            }
        }
        worker.start()
        worker.join()
        assertNull("The resume must not fail on the calling thread", failure)
        assertEquals("The resume waits on the timer", 1, timer.size)

        suspendScript()
        runTimer()
        assertEquals("from thread,done", steps())
    }

    @Test
    fun aResumeFromAThreadAfterTheSuspensionStillGoesThroughTheTimer() {
        suspendScript()
        val worker = Thread { continuation.resumeWith(Continuation.Result.success("later")) }
        worker.start()
        worker.join()
        assertEquals("", steps())

        runTimer()
        assertEquals("later,done", steps())
    }

    @Test
    fun aResumeOnTheScriptThreadResumesAtOnce() {
        suspendScript()
        continuation.resumeWith(Continuation.Result.success("here"))
        assertTrue(timer.isEmpty())
        assertEquals("here,done", steps())
    }

    private fun suspendScript() {
        val script = cx.compileString("steps.push(suspendHere()); steps.push('done');", "main", 1, null)
        try {
            cx.executeScriptWithContinuations(script, scope)
            fail("The script did not suspend")
        } catch (_: ContinuationPending) {
            /* Suspended, as expected. */
        }
        assertTrue(continuation.pending != null)
    }

    private fun runTimer() {
        while (true) {
            val r = timer.poll() ?: return
            try {
                r.run()
            } catch (_: ContinuationPending) {
                /* Suspended again (Timer.runPosted). */
            }
        }
    }

    private fun steps(): String = Context.toString(cx.evaluateString(scope, "steps.join()", "steps", 1, null))

}
