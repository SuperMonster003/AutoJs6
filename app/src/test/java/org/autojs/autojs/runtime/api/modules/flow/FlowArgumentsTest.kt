package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.flow.FlowScope
import org.autojs.autojs.core.automator.flow.FlowStep
import org.autojs.autojs.core.automator.flow.MissingPolicy
import org.autojs.autojs.core.automator.flow.Stability
import org.autojs.autojs.core.automator.flow.StabilityCompare
import org.autojs.autojs.core.automator.query.PollPolicy
import org.autojs.autojs.core.automator.toolkit.Humanize
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper

/**
 * The argument rules of the Flow API (roadmap Appendix A.2) on a bare Rhino context: positional
 * numbers, the options object, trailing callbacks, the `*After` delay, the `sleep` / `timeout` /
 * `retry` / `scope` forms, the selector forms with their descriptions, and the JavaScript shape
 * of a [FlowError].
 * zh-CN: Flow API 的参数规则 (Roadmap 附录 A.2), 在裸 Rhino 上下文上: 位置数字, 选项对象, 尾随回调, `*After` 的延迟, `sleep` /
 * `timeout` / `retry` / `scope` 形式, 选择器形式及其描述, 以及 [FlowError] 的 JavaScript 形态.
 */
class FlowArgumentsTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    private val defaults = FlowDefaults()

    private val conditions = ArrayList<Triple<Any?, String, FlowScope>>()

    private val arguments = FlowArguments(defaults) { value, name, flowScope ->
        conditions += Triple(value, name, flowScope)
        FlowCondition.Native("cond") { value }
    }

    @Before
    fun enterContext() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    private fun js(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun fn(): Callable = js("(function () {})") as Callable

    @Test
    fun controlArgumentsAndSnapshotHaveExplicitContracts() {
        val handler = fn()
        assertSame(handler, arguments.whenPresent("whenPresent", arrayOf("dialog", handler)).handler)
        assertThrows(IllegalArgumentException::class.java) { arguments.whenPresent("whenPresent", arrayOf("dialog", handler, js("({ timeuot: 10 })"))) }
        val repeat = arguments.repeatUntil("repeatUntil", arrayOf(handler, "done", js("({ timeout: 500, maxAttempts: 3, interval: 20 })")))
        assertEquals(500L, repeat.timeout)
        assertEquals(3, repeat.maxAttempts)
        assertEquals(0, arguments.repeatUntil("repeatUntil", arrayOf(handler, "done")).maxAttempts)
        assertEquals(0, arguments.repeatUntil("repeatUntil", arrayOf(handler, "done", js("({ maxAttempts: 0 })"))).maxAttempts)
        assertEquals(20L, repeat.interval)
        assertThrows(IllegalArgumentException::class.java) { arguments.repeatUntil("repeatUntil", arrayOf(handler, "done", js("({ maxAttempts: 1.5 })"))) }
        assertThrows(IllegalArgumentException::class.java) { arguments.repeatUntil("repeatUntil", arrayOf(handler, "done", js("({ timeout: Infinity })"))) }
        val snapshot = arguments.wait("waitForStable", arrayOf("row", js("({ snapshot: function (x) { return x; } })"))).stability.snapshot
        assertTrue(snapshot is Callable)
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("waitForStable", arrayOf("row", js("({ snapshot: 1 })"))) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("waitForStable", arrayOf("row", js("({ snapshot: function () {}, compare: 'content' })"))) }
    }

    @Test
    fun recoverServiceComesFromTheOptionOrTheDefaults() {
        assertFalse(arguments.wait("t", arrayOf<Any?>("text")).recoverService)
        assertTrue(arguments.wait("t", arrayOf<Any?>("text", js("({ recoverService: true })"))).recoverService)

        arguments.applyDefaults(js("({ recoverService: true })") as ScriptableObject, "flow.defaults")
        assertTrue(defaults.recoverService)
        assertTrue(arguments.wait("t", arrayOf<Any?>("text")).recoverService)
        assertFalse(arguments.wait("t", arrayOf<Any?>("text", js("({ recoverService: false })"))).recoverService)
    }

    @Test
    fun positionalNumbersAreTimeoutAndIntervalAndTrailingFunctionsTheCallbacks() {
        val ok = fn()
        val err = fn()
        val spec = arguments.wait("t", arrayOf<Any?>("text", 3000, 50, ok, err))
        assertEquals(3000L, spec.policy.timeout)
        assertEquals(50L, spec.policy.interval)
        assertEquals(PollPolicy.UNBOUNDED, spec.policy.times)
        assertSame(ok, spec.onOk)
        assertSame(err, spec.onErr)
        assertEquals(Stability.DEFAULT_STABLE_FOR, spec.stability.stableFor)
        assertEquals(StabilityCompare.Fingerprint, spec.stability.compare)
        assertTrue(spec.strict)
        assertNull(spec.delay)
        assertEquals("text", conditions.single().first)
        assertEquals("t", conditions.single().second)
        assertEquals(FlowScope.NONE, conditions.single().third)

        val bare = arguments.wait("t", arrayOf<Any?>("text"))
        assertEquals(FlowDefaults.DEFAULT_TIMEOUT, bare.policy.timeout)
        assertEquals(FlowDefaults.DEFAULT_INTERVAL, bare.policy.interval)
        assertNull(bare.onOk)
    }

    @Test
    fun theOptionsObjectCarriesPollingStabilityStrictnessDelayAndScope() {
        val options = js("({ timeout: Infinity, interval: 10, times: 5, stableFor: 100, compare: 'bounds', missing: 'fail', strict: false, delay: [20, 30], compass: 'p2' })")
        val spec = arguments.wait("t", arrayOf<Any?>("text", options))
        assertEquals(PollPolicy.UNBOUNDED, spec.policy.timeout)
        assertEquals(10L, spec.policy.interval)
        assertEquals(5L, spec.policy.times)
        assertEquals(100L, spec.stability.stableFor)
        assertEquals(StabilityCompare.Bounds, spec.stability.compare)
        assertEquals(MissingPolicy.FAIL, spec.stability.missing)
        assertFalse(spec.strict)
        assertEquals(FlowStep.Sleep(20, 30), spec.delay)
        assertEquals("p2", conditions.single().third.compass)

        val custom = fn()
        val withFunction = arguments.wait("t", arrayOf<Any?>("text", js("({ compare: null })").also { ScriptableObject.putProperty(it as ScriptableObject, "compare", custom) }))
        assertEquals(StabilityCompare.Custom(custom), withFunction.stability.compare)
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("text", js("({ compare: 'shape' })"))) }
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("text", js("({ missing: 'wait' })"))) }
    }

    @Test
    fun defaultsAreReadWhenTheStepIsBuilt() {
        defaults.timeout = 42
        defaults.interval = 7
        defaults.stableFor = 99
        defaults.strictActions = false
        val spec = arguments.wait("t", arrayOf<Any?>("text"))
        assertEquals(42L, spec.policy.timeout)
        assertEquals(7L, spec.policy.interval)
        assertEquals(99L, spec.stability.stableFor)
        assertFalse(spec.strict)
    }

    @Test
    fun applyDefaultsWritesTheGivenKeysAndReturnsTheTraceOption() {
        assertEquals(true, arguments.applyDefaults(js("({ timeout: Infinity, interval: 5, stableFor: 7, strictActions: false, trace: true })") as ScriptableObject, "flow.defaults"))
        assertEquals(PollPolicy.UNBOUNDED, defaults.timeout)
        assertEquals(5L, defaults.interval)
        assertEquals(7L, defaults.stableFor)
        assertFalse(defaults.strictActions)
        assertNull(arguments.applyDefaults(js("({ timeout: 250 })") as ScriptableObject, "flow.defaults"))
        assertEquals(250L, defaults.timeout)
        assertEquals(5L, defaults.interval)
        assertThrows(IllegalArgumentException::class.java) { arguments.applyDefaults(js("({ timeOut: 1 })") as ScriptableObject, "flow.defaults") }
        assertThrows(IllegalArgumentException::class.java) { arguments.applyDefaults(js("({ interval: -1 })") as ScriptableObject, "flow.defaults") }
        assertEquals(250L, defaults.timeout)
    }

    @Test
    fun applyDefaultsAcceptsHumanize() {
        assertEquals(Humanize.NONE, defaults.humanize)
        arguments.applyDefaults(js("({ humanize: { offset: [3, 4], delay: [10, 20] } })") as ScriptableObject, "flow.defaults")
        assertEquals(Humanize(offsetX = 3, offsetY = 4, minDelay = 10, maxDelay = 20), defaults.humanize)
        arguments.applyDefaults(js("({ humanize: { delay: 5 } })") as ScriptableObject, "flow.defaults")
        assertEquals(Humanize(minDelay = 5, maxDelay = 5), defaults.humanize)
        arguments.applyDefaults(js("({ humanize: true, interval: 9 })") as ScriptableObject, "flow.defaults")
        assertEquals(Humanize(minDelay = 5, maxDelay = 5), defaults.humanize)
        assertEquals(9L, defaults.interval)
        arguments.applyDefaults(js("({ humanize: false })") as ScriptableObject, "flow.defaults")
        assertEquals(Humanize.NONE, defaults.humanize)
        assertThrows(IllegalArgumentException::class.java) { arguments.applyDefaults(js("({ humanize: { offset: -1 } })") as ScriptableObject, "flow.defaults") }
        assertThrows(IllegalArgumentException::class.java) { arguments.applyDefaults(js("({ humanize: { delay: [20, 10] } })") as ScriptableObject, "flow.defaults") }
        assertThrows(IllegalArgumentException::class.java) { arguments.applyDefaults(js("({ humanize: { jitter: 1 } })") as ScriptableObject, "flow.defaults") }
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.applyDefaults(js("({ humanize: 'yes' })") as ScriptableObject, "flow.defaults") }
    }

    @Test
    fun aZeroTimeoutMeansASingleAttempt() {
        val spec = arguments.wait("t", arrayOf<Any?>("x", 0))
        assertEquals(0L, spec.policy.timeout)
        assertEquals(1L, spec.policy.times)
        val bounded = arguments.wait("t", arrayOf<Any?>("x", js("({ timeout: 0, times: 3 })")))
        assertEquals(1L, bounded.policy.times)
    }

    @Test
    fun theDelayOfAnAfterFormIsTheThirdAndFourthNumber() {
        val spec = arguments.wait("clickWhenStableAfter", arrayOf<Any?>("x", 1000, 100, 200, 300), withDelay = true)
        assertEquals(1000L, spec.policy.timeout)
        assertEquals(100L, spec.policy.interval)
        assertEquals(FlowStep.Sleep(200, 300), spec.delay)
        val single = arguments.wait("clickWhenStableAfter", arrayOf<Any?>("x", 1000, 100, 200), withDelay = true)
        assertEquals(FlowStep.Sleep(200, 200), single.delay)
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("wait", arrayOf<Any?>("x", 1000, 100, 200)) }
    }

    @Test
    fun invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", emptyArray()) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>(null)) }
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("x", "y")) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("x", js("({})"), js("({})"))) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("x", -1)) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("x", fn(), fn(), fn())) }
        assertThrows(IllegalArgumentException::class.java) { arguments.wait("t", arrayOf<Any?>("x", js("({ interval: Infinity })"))) }
    }

    @Test
    fun sleepTimeoutAndRetryForms() {
        assertEquals(FlowStep.Sleep(100, 100), arguments.sleep("s", arrayOf<Any?>(100)))
        assertEquals(FlowStep.Sleep(100, 200), arguments.sleep("s", arrayOf<Any?>(100, 200)))
        assertEquals(FlowStep.Sleep(50, 150), arguments.sleep("s", arrayOf<Any?>(100, "±50")))
        assertThrows(IllegalArgumentException::class.java) { arguments.sleep("s", emptyArray()) }
        assertThrows(IllegalArgumentException::class.java) { arguments.sleep("s", arrayOf<Any?>(-5)) }
        assertThrows(IllegalArgumentException::class.java) { arguments.sleep("s", arrayOf<Any?>(1, 2, 3)) }
        assertEquals(FlowStep.Timeout(500), arguments.timeout("timeout", arrayOf<Any?>(500)))
        assertThrows(IllegalArgumentException::class.java) { arguments.timeout("timeout", emptyArray()) }
        assertEquals(FlowStep.Retry(1, 0), arguments.retry("retry", emptyArray()))
        assertEquals(FlowStep.Retry(3, 100), arguments.retry("retry", arrayOf<Any?>(3, 100)))
    }

    @Test
    fun actionArgumentsSplitLeadingValuesFromTrailingCallbacks() {
        val ok = fn()
        val err = fn()
        val (args, callbacks) = arguments.actionArguments("setText", arrayOf<Any?>("abc", ok, err))
        assertEquals(listOf("abc"), args)
        assertSame(ok, callbacks.first)
        assertSame(err, callbacks.second)
        val (none, noCallbacks) = arguments.actionArguments("click", emptyArray())
        assertTrue(none.isEmpty())
        assertNull(noCallbacks.first)
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.actionArguments("setText", arrayOf<Any?>("abc", ok, "x")) }
        assertThrows(IllegalArgumentException::class.java) { arguments.callbacks("then", arrayOf<Any?>(ok, err, fn())) }
        val (first, second) = arguments.callbacks("then", arrayOf<Any?>(null, err))
        assertSame(err, first)
        assertNull(second)
    }

    @Test
    fun sourcesAreOneArrayOrTheArgumentsThemselves() {
        val a = fn()
        val b = fn()
        assertEquals(listOf<Any?>(a, b), arguments.sources("all", arrayOf<Any?>(listOf(a, b))))
        assertEquals(listOf<Any?>(a, b), arguments.sources("all", arrayOf<Any?>(a, b)))
        assertTrue(arguments.sources("all", emptyArray()).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { arguments.sources("all", arrayOf<Any?>(a, null)) }
    }

    @Test
    fun waitNativeProbesTheExpectedName() {
        val probed = ArrayList<String>()
        val spec = arguments.waitNative("waitForActivity", arrayOf<Any?>("Main", 500)) { probed += it; it == "Main" }
        assertEquals("waitForActivity(\"Main\")", spec.cond.description)
        assertEquals(500L, spec.policy.timeout)
        assertEquals(true, (spec.cond as FlowCondition.Native).fn())
        assertEquals(listOf("Main"), probed)
        assertTrue(conditions.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { arguments.waitNative("waitForActivity", arrayOf<Any?>(1)) { null } }
    }

    @Test
    fun scopeReadsRootCompassAndResultType() {
        val flowScope = arguments.scope(js("({ compass: 'p2', resultType: ['text', 'bounds'] })"), "scope")
        assertNull(flowScope.root)
        assertEquals("p2", flowScope.compass)
        assertEquals(listOf("text", "bounds"), (flowScope.resultType as List<*>).toList())
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.scope(js("({ root: 1 })"), "scope") }
        assertThrows(IllegalArgumentException::class.java) { arguments.scope("x", "scope") }
        assertThrows(IllegalArgumentException::class.java) { arguments.scope(fn(), "scope") }
    }

    @Test
    fun selectorFormsAndTheirDescriptions() {
        assertTrue(FlowCondition.isSelectorForm("x"))
        assertTrue(FlowCondition.isSelectorForm(js("/re/")))
        assertTrue(FlowCondition.isSelectorForm(js("({ text: 'x' })")))
        assertTrue(FlowCondition.isSelectorForm(js("['a']")))
        assertTrue(FlowCondition.isSelectorForm(1))
        assertFalse(FlowCondition.isSelectorForm(fn()))
        assertFalse(FlowCondition.isSelectorForm(true))
        assertFalse(FlowCondition.isSelectorForm(null))
        assertEquals("content(\"x\")", FlowCondition.describeSelector("x"))
        assertEquals("contentMatch(/re/)", FlowCondition.describeSelector(js("/re/")!!))
        assertEquals("content(\"7\")", FlowCondition.describeSelector(7.0))
        assertEquals("{text: \"x\", depth: 2}", FlowCondition.describeSelector(js("({ text: 'x', depth: 2 })")!!))
    }

    @Test
    fun flowErrorsBecomeJsErrorsWithTheirFields() {
        val error = FlowError.Timeout("wait", "text(\"x\")", 1500, 8)
        val js = FlowJsErrors.toJs(cx, scope, error) as ScriptableObject
        assertEquals("Error", js.className)
        assertEquals("FlowError", ScriptableObject.getProperty(js, "name"))
        assertEquals("TIMEOUT", ScriptableObject.getProperty(js, "code"))
        assertEquals("wait", ScriptableObject.getProperty(js, "step"))
        assertEquals("text(\"x\")", ScriptableObject.getProperty(js, "selector"))
        assertEquals(1500.0, ScriptableObject.getProperty(js, "elapsed"))
        assertEquals(8.0, ScriptableObject.getProperty(js, "attempts"))
        assertEquals(error.message, ScriptableObject.getProperty(js, "message"))
        assertSame(error, (ScriptableObject.getProperty(js, "javaException") as Wrapper).unwrap())

        val thrown = assertThrows(JavaScriptException::class.java) { FlowJsErrors.throwInto(cx, scope, error) }
        assertEquals("TIMEOUT", ScriptableObject.getProperty(thrown.value as ScriptableObject, "code"))

        val value = js("({ custom: 1 })")
        val jsException = JavaScriptException(value, "test", 1)
        assertSame(value, FlowJsErrors.toJs(cx, scope, jsException))
        assertSame(jsException, assertThrows(JavaScriptException::class.java) { FlowJsErrors.throwInto(cx, scope, jsException) })

        val plain = FlowJsErrors.toJs(cx, scope, IllegalStateException("plain")) as ScriptableObject
        assertEquals("Error", plain.className)
        assertTrue(ScriptableObject.getProperty(plain, "message").toString().contains("plain"))
    }

}
