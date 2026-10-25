package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.util.ArrayDeque

class ComposeReactiveRuntimeTest {
    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var runtime: ComposeReactiveRuntime
    private val tasks = ArrayDeque<() -> Unit>()
    private val errors = ArrayList<Throwable>()
    private var scriptThread = true
    private var rejectPost = false

    @Before fun enter() {
        context = Context.enter().apply { languageVersion = Context.VERSION_ES6; isInterpretedMode = true }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
        ScriptableObject.putProperty(scope, "ComposeError", ComposeJsErrors.constructor(scope))
        runtime = ComposeReactiveRuntime(scope, {
            if (!scriptThread) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Use compose.post")
        }, { action ->
            if (rejectPost) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED)
            tasks.addLast(action)
        }, errors::add)
    }

    @After fun exit() { runtime.close(); Context.exit() }

    @Test fun tenStateWritesScheduleOneRenderAfterTheCurrentTurn() {
        val state = runtime.state(0)
        val rendered = ArrayList<Any?>()
        runtime.reaction { rendered += state.read() }.run()
        repeat(10) { state.write(it + 1) }
        assertEquals(listOf(0), rendered)
        assertEquals(1, tasks.size)
        drain()
        assertEquals(listOf(0, 10), rendered)
        assertTrue(errors.isEmpty())
        state.write(10)
        assertTrue("Writing the existing primitive value does not rerender", tasks.isEmpty())
    }

    @Test fun aRejectedPostDoesNotPermanentlyDisarmReactiveUpdates() {
        val state = runtime.state(0)
        val rendered = ArrayList<Any?>()
        runtime.reaction { rendered += state.read() }.run()
        rejectPost = true
        assertThrows(ComposeUiContractException::class.java) { state.write(1) }
        assertTrue(tasks.isEmpty())
        rejectPost = false
        state.write(2)
        drain()
        assertEquals(listOf(0, 2), rendered)
        assertTrue(errors.isEmpty())
    }

    @Test fun aSuccessfulBranchSwitchUnsubscribesStateThatTheNewRenderDoesNotRead() {
        val branch = runtime.state(true)
        val left = runtime.state("left")
        val right = runtime.state("right")
        val rendered = ArrayList<Any?>()
        runtime.reaction { rendered += if (branch.read() == true) left.read() else right.read() }.run()
        right.write("updated right")
        assertTrue(tasks.isEmpty())
        branch.write(false)
        drain()
        assertEquals(listOf("left", "updated right"), rendered)
        left.write("obsolete left")
        assertTrue("Old branch dependency was released", tasks.isEmpty())
        right.write("active right")
        drain()
        assertEquals(listOf("left", "updated right", "active right"), rendered)
    }

    @Test fun aFailedBranchRetainsNewReadsForRecoveryThenPrunesTheOldDependencyOnSuccess() {
        val branch = runtime.state(true)
        val left = runtime.state(1)
        val right = runtime.state(-1)
        val rendered = ArrayList<Int>()
        runtime.reaction {
            val value = (if (branch.read() == true) left.read() else right.read()) as Int
            check(value >= 0) { "Invalid rendered value" }
            rendered += value
        }.run()
        branch.write(false)
        drain()
        assertEquals(listOf(1), rendered)
        assertEquals(1, errors.size)
        right.write(2)
        drain()
        assertEquals(listOf(1, 2), rendered)
        left.write(3)
        assertTrue("Recovered branch no longer subscribes the old value", tasks.isEmpty())
    }

    @Test fun anInitiallyFailingReactionCanRecoverWhenItsFirstDependencyIsRepaired() {
        val state = runtime.state(false)
        var renders = 0
        val reaction = runtime.reaction {
            check(state.read() == true) { "Not ready" }
            renders++
        }
        assertThrows(IllegalStateException::class.java) { reaction.run() }
        state.write(true)
        drain()
        assertEquals(1, renders)
        assertTrue(errors.isEmpty())
    }

    @Test fun aReactionClosingItselfDoesNotResubscribeWhenItsActionContinuesReading() {
        val state = runtime.state(0)
        var renders = 0
        lateinit var reaction: ComposeReactiveRuntime.Reaction
        reaction = runtime.reaction {
            state.read()
            reaction.close()
            state.read()
            renders++
        }
        reaction.run()
        state.write(1)
        assertTrue(tasks.isEmpty())
        assertEquals(1, renders)
        assertTrue(state.subscribers.isEmpty())
    }

    @Test fun runtimeCloseCancelsAnAlreadyPostedRenderWithoutReportingAnError() {
        val state = runtime.state(0)
        var renders = 0
        runtime.reaction { state.read(); renders++ }.run()
        state.write(1)
        assertEquals(1, tasks.size)
        runtime.close()
        drain()
        assertEquals(1, renders)
        assertTrue(state.subscribers.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test fun closingDuringADrainCancelsRemainingReactionsWithoutSessionClosedErrors() {
        val state = runtime.state(0)
        var closeOnRender = false
        var laterRuns = 0
        runtime.reaction { state.read(); if (closeOnRender) runtime.close() }.run()
        runtime.reaction { state.read(); laterRuns++ }.run()
        closeOnRender = true
        state.write(1)
        drain()
        assertEquals(1, laterRuns)
        assertTrue(errors.toString(), errors.isEmpty())
        assertTrue(state.subscribers.isEmpty())
    }

    @Test fun nestedBatchFailureStillSchedulesOneRenderForAlreadyWrittenState() {
        val state = runtime.state(0)
        val rendered = ArrayList<Any?>()
        runtime.reaction { rendered += state.read() }.run()
        val failure = IllegalStateException("Batch callback failed")
        val actual = assertThrows(IllegalStateException::class.java) {
            runtime.batch {
                state.write(1)
                runtime.batch { state.write(2); state.write(3) }
                assertTrue(tasks.isEmpty())
                throw failure
            }
        }
        assertSame(failure, actual)
        assertEquals(1, tasks.size)
        assertEquals(3, state.read())
        drain()
        assertEquals(listOf(0, 3), rendered)
        assertTrue(errors.isEmpty())
    }

    @Test fun statePropertyGettersAndSettersExposeTypedComposeErrorsToJavaScript() {
        val state = runtime.state(1)
        ScriptableObject.putProperty(scope, "state", state)
        scriptThread = false
        assertEquals("true,INVALID_ARGUMENT", evaluate("""
            (function() { try { return state.value; } catch (error) {
                return [error instanceof ComposeError, error.code].join(',');
            } })()
        """))
        assertEquals("true,INVALID_ARGUMENT", evaluate("""
            (function() { try { state.value = 2; } catch (error) {
                return [error instanceof ComposeError, error.code].join(',');
            } })()
        """))
        scriptThread = true
        assertEquals(1, state.read())
        runtime.close()
        assertEquals("true,SESSION_CLOSED", evaluate("""
            (function() { try { return state.value; } catch (error) {
                return [error instanceof ComposeError, error.code].join(',');
            } })()
        """))
        assertEquals("true,SESSION_CLOSED", evaluate("""
            (function() { try { state.value = 3; } catch (error) {
                return [error instanceof ComposeError, error.code].join(',');
            } })()
        """))
    }

    private fun drain() {
        var count = 0
        while (tasks.isNotEmpty()) {
            assertTrue("Unexpected self-sustaining render loop", count++ < 32)
            tasks.removeFirst()()
        }
    }

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "compose-state-test.js", 1, null)
}
