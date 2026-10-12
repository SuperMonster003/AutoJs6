package org.autojs.autojs.runtime.api.augment.flow

import org.autojs.autojs.core.automator.flow.FakeBindings
import org.autojs.autojs.core.automator.flow.CallbackKind
import org.autojs.autojs.core.automator.flow.FakeLane
import org.autojs.autojs.core.automator.flow.FlowBindings
import org.autojs.autojs.core.automator.flow.FlowNode
import org.autojs.autojs.core.automator.flow.FlowScheduler
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.flow.FlowTaskStack
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

class FlowDiagnosticsTest {
    private lateinit var cx: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun exitContext() = Context.exit()

    private fun failLater(error: Throwable): FlowNode {
        val worker = FakeLane("worker")
        val script = FakeLane("script")
        val bindings = object : FlowBindings by FakeBindings() {
            override fun captureSourceStack() = RuntimeFlowBindings.captureScriptStack()
        }
        val scheduler = FlowScheduler(worker, script, bindings, { 0 }, {})
        var tail: FlowNode? = null
        ScriptableObject.putProperty(scope, "append", object : BaseFunction() {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? {
                val name = Context.toString(args[0])
                val step = FlowStep.Tool(name, null) { value, _ ->
                    if (name == "smartClickBounds") throw error
                    value
                }
                tail = scheduler.start(step, tail)
                return null
            }
        })
        cx.evaluateString(scope, """
            append('wait');
            function selectProperDoctor() {
                append('smartClickBounds');
            }
            selectProperDoctor();
            append('clickBounds');
        """.trimIndent(), "reservation.js", 1, null)
        // Execute only after the creating JavaScript call stack has unwound.
        worker.runAll()
        script.runAll()
        return tail!!
    }

    @Test
    fun nativeFailureRetainsCreationMethodsAndLinesWithoutBlamingSkippedSteps() {
        val error = IllegalArgumentException("Failed to coerce nullish (Null) into a boolean")
        val flow = failLater(error)
        assertSame(error, flow.error)
        val trace = FlowTaskStack.format(error)!!
        assertTrue(trace, trace.contains("Flow.smartClickBounds (reservation.js:3)"))
        assertTrue(trace, trace.contains("selectProperDoctor (reservation.js:3)"))
        assertTrue(trace, trace.contains("Flow.wait (reservation.js:1)"))
        assertFalse(trace, trace.contains("Flow.clickBounds ("))
        val value = FlowJsErrors.toJs(cx, scope, error) as ScriptableObject
        assertEquals(trace, ScriptableObject.getProperty(value, "flowStack"))
        assertFalse(value.ids.contains("flowStack"))
        val output = Util.formatRhino(FlowJsErrors.consoleValue(value))
        assertTrue(output, output.contains(error.message!!))
        assertTrue(output, output.contains("reservation.js:3"))
        assertTrue(output, output.contains("Flow task stack:"))
    }

    @Test
    fun scriptErrorsKeepIdentityAndOriginalStackWhenAsyncFramesAreAdded() {
        val error = assertThrows(JavaScriptException::class.java) {
            cx.evaluateString(scope, "function fail() { throw Error('original'); } fail();", "failure.js", 17, null)
        }
        failLater(error)
        val value = FlowJsErrors.toJs(cx, scope, error) as ScriptableObject
        assertSame(error.value, value)
        val first = ScriptableObject.getProperty(value, "stack").toString()
        assertTrue(first, first.contains("Error: original"))
        assertTrue(first, first.contains("failure.js:17"))
        assertTrue(first, first.contains("reservation.js:3"))
        assertSame(value, FlowJsErrors.toJs(cx, scope, error))
        assertEquals(first, ScriptableObject.getProperty(value, "stack").toString())
        assertSame(error, assertThrows(JavaScriptException::class.java) { FlowJsErrors.throwInto(cx, scope, error) })
    }

    @Test
    fun errorLoggingPreservesFormatStringsAndDoesNotInterpretErrorMessagesAsTemplates() {
        val error = IllegalArgumentException("failed %s")
        failLater(error)
        val value = FlowJsErrors.toJs(cx, scope, error)
        val message = FlowJsErrors.formatConsole(arrayOf(value, "context"))
        assertTrue(message, message.contains("failed %s"))
        // A string after a non-string first argument is inspected, hence quoted (Auto.js 4 / Node.js util.format).
        assertTrue(message, message.endsWith(" 'context'"))
        val formatted = FlowJsErrors.formatConsole(arrayOf("failure: %s", value))
        assertTrue(formatted, formatted.startsWith("failure: Error:"))
        assertTrue(formatted, formatted.contains("reservation.js:3"))
        assertEquals(Util.formatRhino("%s = %d", "n", 2), FlowJsErrors.formatConsole(arrayOf<Any?>("%s = %d", "n", 2)))
        assertEquals(Util.formatRhino(2, "50%%"), FlowJsErrors.formatConsole(arrayOf<Any?>(2, "50%%")))
    }

    @Test
    fun adoptedSubflowFailuresRetainBothInnerAndOuterTaskSites() {
        val worker = FakeLane("worker")
        val script = FakeLane("script")
        val scheduler = FlowScheduler(worker, script, FakeBindings(), { 0 }, {})
        val error = IllegalStateException("nested failure")
        val child = scheduler.start(FlowStep.Tool("smartClickBounds") { _, _ -> throw error })
        child.sourceStack = listOf(StackTraceElement("", "submit", "nested.js", 2))
        val root = scheduler.of(null)
        root.sourceStack = listOf(StackTraceElement("", "<script>", "outer.js", 8))
        val outer = scheduler.start(FlowStep.Callback(CallbackKind.THEN, onOk = { _: List<Any?> -> child }), root)
        outer.sourceStack = listOf(StackTraceElement("", "<script>", "outer.js", 9))
        worker.runAll()
        script.runAll()
        assertSame(error, outer.error)
        val trace = FlowTaskStack.format(error)!!
        assertTrue(trace, trace.contains("Flow.smartClickBounds (nested.js:2)"))
        assertTrue(trace, trace.contains("submit (nested.js:2)"))
        assertTrue(trace, trace.contains("Flow.then (outer.js:9)"))
        assertTrue(trace, trace.contains("outer.js:8"))
    }

    @Test
    fun primitiveAndFrozenRejectionsArePreserved() {
        val primitive = JavaScriptException("rejected value", "script.js", 1)
        failLater(primitive)
        assertEquals("rejected value", FlowJsErrors.toJs(cx, scope, primitive))
        val frozen = cx.evaluateString(scope, "Object.freeze({ custom: true })", "frozen.js", 1, null)
        val rejection = JavaScriptException(frozen, "frozen.js", 1)
        failLater(rejection)
        assertSame(frozen, FlowJsErrors.toJs(cx, scope, rejection))
    }

    @Test
    fun nullShortCircuitReproducesTheReportedBooleanCoercionAndExplicitCheckFixesIt() {
        val result = cx.evaluateString(scope, "let current = null; current && current.available", "filter.js", 1, null)
        assertNull(result)
        val error = assertThrows(IllegalArgumentException::class.java) { RhinoUtils.coerceBoolean(result) }
        assertTrue(error.message!!, error.message!!.contains("Failed to coerce nullish (Null) into a boolean"))
        val fixed = cx.evaluateString(scope, "current != null && current.available", "filter.js", 2, null)
        assertFalse(RhinoUtils.coerceBoolean(fixed))
    }
}
