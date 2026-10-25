package org.autojs.autojs.runtime.api.modules.toolkit

import org.autojs.autojs.core.automator.toolkit.Humanize
import org.autojs.autojs.core.automator.toolkit.ListCollector
import org.autojs.autojs.core.automator.toolkit.PopupDismisser
import org.autojs.autojs.core.automator.toolkit.ScrollDirection
import org.autojs.autojs.core.automator.toolkit.ScrollMethod
import org.autojs.autojs.core.automator.toolkit.ScrollSearch
import org.autojs.autojs.core.automator.toolkit.SmartClick
import org.autojs.autojs.core.automator.toolkit.TextEntry
import org.autojs.autojs.core.automator.toolkit.Toggle
import org.autojs.autojs.core.automator.toolkit.Toolkit
import org.autojs.autojs.runtime.api.modules.flow.FlowDefaults
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/**
 * The argument rules of the toolkit surface (roadmap P4.2, Appendix B) on a bare Rhino context:
 * the positional / options / callbacks split, the shorthand number, the known-key check, the
 * `humanize` forms with their fallback to the flow defaults, and every tool's option object.
 * zh-CN: 工具集表面的参数规则 (Roadmap P4.2, 附录 B), 在裸 Rhino 上下文上: 位置参数 / 选项 / 回调的拆分, 裸数字简写, 已知键检查,
 * `humanize` 的各种形式及其对 flow 默认值的回退, 以及每个工具的选项对象.
 */
class ToolkitArgumentsTest {

    private lateinit var cx: Context
    private lateinit var scope: VarScope

    private val defaults = FlowDefaults()

    private val invoked = ArrayList<Pair<Callable, List<Any?>>>()

    private val arguments = ToolkitArguments(defaults) { fn, args ->
        invoked += fn to args
        fn.call(cx, scope, org.mozilla.javascript.Undefined.SCRIPTABLE_UNDEFINED, args.toTypedArray())
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

    private fun fn(body: String = ""): Callable = js("(function () { $body })") as Callable

    private fun obj(source: String): ScriptableObject = js("($source)") as ScriptableObject

    private fun call(name: String, vararg args: Any?, positional: Int = 1, callbacks: Boolean = true) = arguments.split(name, args, positional, callbacks)

    private fun reader(options: ScriptableObject?, keys: Set<String> = ToolkitArguments.CLICK_KEYS, shorthand: String? = null, name: String = "t") =
        arguments.reader(ToolkitArguments.Call(name, emptyList(), options, null, null, null), keys, shorthand)

    // Splitting. zh-CN: 拆分.

    @Test
    fun positionalsThenOptionsThenCallbacks() {
        val ok = fn()
        val err = fn()
        val options = obj("{ timeout: 5 }")
        val c = call("t", "target", "text", options, ok, err, positional = 2)
        assertEquals(listOf<Any?>("target", "text"), c.positionals)
        assertSame(options, c.options)
        assertNull(c.shorthand)
        assertSame(ok, c.onOk)
        assertSame(err, c.onErr)
        assertEquals("text", c.require(1, "text"))
    }

    @Test
    fun aFunctionInAPositionalSlotIsAPositionalAndTheNextOneACallback() {
        val cond = fn()
        val ok = fn()
        val c = call("backUntil", cond, ok)
        assertSame(cond, c.positionals[0])
        assertSame(ok, c.onOk)
        assertNull(c.onErr)
        assertNull(c.options)
    }

    @Test
    fun missingPositionalsAreNullAndRequiredOnesAreChecked() {
        val c = call("typeInto", "x", positional = 2)
        assertNull(c.positional(1))
        assertNull(c.positional(7))
        assertEquals("x", c.require(0, "target"))
        val e = assertThrows(WrappedIllegalArgumentException::class.java) { c.require(1, "text") }
        assertTrue(e.message!!.contains("\"text\" is required for typeInto"))
        assertNull(call("t", org.mozilla.javascript.Undefined.instance).positional(0))
    }

    @Test
    fun aBareNumberIsTheShorthandAndNullsInTheOptionsSlotAreSkipped() {
        val c = call("findAny", "x", 3000)
        assertEquals(3000.0, c.shorthand!!, 0.0)
        assertNull(c.options)
        val ok = fn()
        val nulled = call("findAny", "x", null, ok)
        assertNull(nulled.options)
        assertSame(ok, nulled.onOk)
        val undefined = call("findAny", "x", org.mozilla.javascript.Undefined.instance, ok)
        assertSame(ok, undefined.onOk)
    }

    @Test
    fun synchronousCallsRefuseCallbacksAndOddArguments() {
        assertThrows(IllegalArgumentException::class.java) { call("t", "x", fn(), callbacks = false) }
        assertThrows(IllegalArgumentException::class.java) { call("t", "x", obj("{}"), 5, callbacks = false) }
        assertThrows(WrappedIllegalArgumentException::class.java) { call("t", "x", "not options") }
        assertThrows(WrappedIllegalArgumentException::class.java) { call("t", "x", js("[1, 2]")) }
        assertThrows(WrappedIllegalArgumentException::class.java) { call("t", "x", obj("{}"), fn(), "trailing") }
        assertThrows(IllegalArgumentException::class.java) { call("t", "x", fn(), fn(), fn()) }
        // A trailing null is fine either way. zh-CN: 尾随的 null 在两种形式下都无妨.
        assertNull(call("t", "x", null, null, callbacks = false).options)
    }

    // Readers. zh-CN: 读取器.

    @Test
    fun unknownKeysAreRejectedAndTheShorthandNeedsAKey() {
        val e = assertThrows(IllegalArgumentException::class.java) { reader(obj("{ climb: true, gestureFallBack: false }")) }
        assertTrue(e.message!!.contains("gestureFallBack"))
        assertTrue(e.message!!.contains("gestureFallback"))
        assertThrows(IllegalArgumentException::class.java) {
            arguments.reader(ToolkitArguments.Call("t", emptyList(), null, 5.0, null, null), ToolkitArguments.CLICK_KEYS)
        }
        val r = arguments.reader(ToolkitArguments.Call("t", emptyList(), null, 5.0, null, null), ToolkitArguments.FIND_KEYS, "timeout")
        assertEquals(5.0, r.number("timeout")!!, 0.0)
        assertEquals(5L, r.locate(0).timeout)
        assertNull(r.number("interval"))
    }

    @Test
    fun locateUsesTheDefaultTimeoutAndAcceptsInfinity() {
        assertEquals(Toolkit.Locate.ONCE, reader(null).locate(0))
        assertEquals(Toolkit.Locate(10000), reader(null).locate(10000))
        assertEquals(Toolkit.Locate(3000, 50), reader(obj("{ timeout: 3000, interval: 50 }")).locate(0))
        assertEquals(Long.MAX_VALUE, reader(obj("{ timeout: Infinity }")).locate(0).timeout)
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ timeout: -1 }")).locate(0) }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ interval: Infinity }")).locate(0) }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ timeout: 'soon' }")).locate(0) }
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ root: 'not a node' }")).root() }
        assertNull(reader(obj("{ root: null }")).root())
    }

    @Test
    fun humanizeFormsAndTheFallbackToTheFlowDefaults() {
        assertEquals(Humanize.NONE, reader(null).humanize())
        defaults.humanize = Humanize(offsetX = 2, minDelay = 10, maxDelay = 20)
        assertEquals(defaults.humanize, reader(null).humanize())
        assertEquals(defaults.humanize, reader(obj("{ humanize: true }")).humanize())
        assertEquals(Humanize.NONE, reader(obj("{ humanize: false }")).humanize())
        assertEquals(Humanize(offsetX = 3, offsetY = 4, minDelay = 100, maxDelay = 200), reader(obj("{ humanize: { offset: [3, 4], delay: [100, 200] } }")).humanize())
        assertEquals(Humanize(offsetX = 5, offsetY = 5), reader(obj("{ humanize: { offset: 5 } }")).humanize())
        assertEquals(Humanize(minDelay = 50, maxDelay = 50), reader(obj("{ humanize: { delay: 50 } }")).humanize())
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ humanize: { offset: [1] } }")).humanize() }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ humanize: { offset: 1.5 } }")).humanize() }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ humanize: { delay: [30, 10] } }")).humanize() }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ humanize: { pause: 1 } }")).humanize() }
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ humanize: 3 }")).humanize() }
    }

    // Tool options. zh-CN: 各工具选项.

    @Test
    fun smartClickOptions() {
        assertEquals(SmartClick.Options(), arguments.smartClick(reader(null)))
        val r = reader(obj("{ climb: false, maxClimb: 2, gestureFallback: false, verify: 'done', verifyTimeout: 500, verifyInterval: 50, offset: [1, 2], humanize: { delay: 5 } }"))
        val o = arguments.smartClick(r)
        assertFalse(o.climb)
        assertEquals(2, o.maxClimb)
        assertFalse(o.gestureFallback)
        assertEquals("done", o.verify)
        assertEquals(500L, o.verifyTimeout)
        assertEquals(50L, o.verifyInterval)
        assertEquals(1, o.offsetX)
        assertEquals(2, o.offsetY)
        assertEquals(Humanize(minDelay = 5, maxDelay = 5), o.humanize)
        assertEquals(3 to 3, reader(obj("{ offset: 3 }")).offset())
        val withFn = arguments.smartClick(reader(obj("{ verify: function () {} }")))
        assertTrue(withFn.verify is Callable)
        assertThrows(IllegalArgumentException::class.java) { arguments.smartClick(reader(obj("{ verify: true }"))) }
        assertThrows(IllegalArgumentException::class.java) { arguments.smartClick(reader(obj("{ maxClimb: -1 }"))) }
        assertThrows(IllegalArgumentException::class.java) { arguments.smartClick(reader(obj("{ maxClimb: 1.5 }"))) }
        // An explicit humanize on the flat options wins over the defaults. zh-CN: 扁平选项上显式的 humanize 优先于默认值.
        defaults.humanize = Humanize(offsetX = 9)
        assertEquals(Humanize(offsetX = 9), arguments.smartClick(reader(null)).humanize)
        assertEquals(Humanize.NONE, arguments.smartClick(reader(obj("{ humanize: false }"))).humanize)
    }

    @Test
    fun boundsClickOptionsSupportSignedOffsetsAndRejectNodePolicies() {
        val options = arguments.smartClick(reader(obj("{ offset: [-12, 7], verify: 'done' }"), ToolkitArguments.CLICK_BOUNDS_KEYS))
        assertEquals(-12, options.offsetX)
        assertEquals(7, options.offsetY)
        assertEquals("done", options.verify)
        assertEquals(-3 to -3, reader(obj("{ offset: -3 }")).offset())
        for (invalid in listOf("{ climb: false }", "{ maxClimb: 0 }", "{ gestureFallback: true }")) {
            assertThrows(IllegalArgumentException::class.java) { reader(obj(invalid), ToolkitArguments.CLICK_BOUNDS_KEYS) }
        }
        for (invalid in listOf("{ offset: 1.5 }", "{ offset: Infinity }", "{ offset: -2147483649 }")) {
            assertThrows(IllegalArgumentException::class.java) { arguments.smartClick(reader(obj(invalid), ToolkitArguments.CLICK_BOUNDS_KEYS)) }
        }
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ humanize: { offset: -1 } }")).humanize() }
    }

    @Test
    fun nestedClickOptionsInheritTheOuterHumanizeUnlessTheyHaveTheirOwn() {
        val outer = reader(obj("{ humanize: { delay: 7 }, click: { climb: false } }"), ToolkitArguments.TOGGLE_KEYS)
        val nested = arguments.nestedClick(outer)
        assertFalse(nested.climb)
        assertEquals(Humanize(minDelay = 7, maxDelay = 7), nested.humanize)
        val own = arguments.nestedClick(reader(obj("{ humanize: { delay: 7 }, click: { humanize: false } }"), ToolkitArguments.TOGGLE_KEYS))
        assertEquals(Humanize.NONE, own.humanize)
        assertEquals(SmartClick.Options(), arguments.nestedClick(reader(null, ToolkitArguments.TOGGLE_KEYS)))
        assertThrows(IllegalArgumentException::class.java) { arguments.nestedClick(reader(obj("{ click: { climbing: true } }"), ToolkitArguments.TOGGLE_KEYS)) }
        assertThrows(IllegalArgumentException::class.java) { arguments.nestedClick(reader(obj("{ click: 'fast' }"), ToolkitArguments.TOGGLE_KEYS)) }
    }

    @Test
    fun scrollSearchOptions() {
        val locate = Toolkit.Locate(1000)
        assertEquals(ScrollSearch.Options(container = "list", locate = locate), arguments.scrollSearch(reader(null, ToolkitArguments.SCROLL_KEYS), "list", locate))
        val r = reader(obj("{ container: 'c', direction: 'Up', maxSteps: 5, method: 'GESTURE', stopAtEnd: false, settle: 100, gestureDuration: 250 }"), ToolkitArguments.SCROLL_KEYS)
        val o = arguments.scrollSearch(r, r.raw("container"), locate)
        assertEquals("c", o.container)
        assertEquals(ScrollDirection.UP, o.direction)
        assertEquals(5, o.maxSteps)
        assertEquals(ScrollMethod.GESTURE, o.method)
        assertFalse(o.stopAtEnd)
        assertEquals(100L, o.settleMillis)
        assertEquals(250L, o.gestureDuration)
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ direction: 'sideways' }"), ToolkitArguments.SCROLL_KEYS).direction() }
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ method: 'swipe' }"), ToolkitArguments.SCROLL_KEYS).method() }
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ direction: 1 }"), ToolkitArguments.SCROLL_KEYS).direction() }
    }

    @Test
    fun textEntryOptions() {
        assertEquals(TextEntry.Options(), arguments.textEntry(reader(null, ToolkitArguments.TEXT_KEYS), Toolkit.Locate.ONCE))
        val o = arguments.textEntry(reader(obj("{ clear: false, submit: true, verify: false, verifyTimeout: 300, verifyInterval: 30 }"), ToolkitArguments.TEXT_KEYS), Toolkit.Locate(2000))
        assertFalse(o.clear)
        assertTrue(o.submit)
        assertFalse(o.verify)
        assertEquals(300L, o.verifyTimeout)
        assertEquals(30L, o.verifyInterval)
        assertEquals(Toolkit.Locate(2000), o.locate)
    }

    @Test
    fun popupOptions() {
        assertEquals(PopupDismisser.Options(), arguments.popups(reader(null, ToolkitArguments.POPUP_KEYS), Toolkit.Locate.ONCE))
        val r = reader(obj("{ once: false, watch: true, settle: 50, maxRounds: 3, debounce: 100, click: { gestureFallback: false }, humanize: { delay: 1 } }"), ToolkitArguments.POPUP_KEYS)
        val o = arguments.popups(r, Toolkit.Locate(500))
        assertEquals(500L, o.locate.timeout)
        assertEquals(50L, o.settleMillis)
        assertEquals(3, o.maxRounds)
        assertEquals(100L, o.debounceMillis)
        assertFalse(o.click.gestureFallback)
        assertEquals(Humanize(minDelay = 1, maxDelay = 1), o.click.humanize)
        assertFalse(r.bool("once", true))
        assertTrue(r.bool("watch", false))
        assertThrows(IllegalArgumentException::class.java) { arguments.popups(reader(obj("{ maxRounds: 0 }"), ToolkitArguments.POPUP_KEYS), Toolkit.Locate.ONCE) }
        assertThrows(WrappedIllegalArgumentException::class.java) { reader(obj("{ onDismissed: 'log' }"), ToolkitArguments.POPUP_KEYS).fn("onDismissed") }
        assertNotNull(reader(obj("{ onError: function () {} }"), ToolkitArguments.POPUP_KEYS).fn("onError"))
    }

    @Test
    fun listCollectorOptionsIncludingTheUntilPredicate() {
        val plain = arguments.listCollector(reader(null, ToolkitArguments.COLLECT_KEYS), Toolkit.Locate.ONCE)
        assertEquals(ListCollector.Options(), plain)
        assertNull(plain.until)
        val r = reader(obj("{ direction: 'left', maxSteps: 2, method: 'gesture', resultType: ['txt', 'desc'], dedupe: false, until: function (item) { return item === 'stop'; }, maxItems: 4, settle: 10, gestureDuration: 20 }"), ToolkitArguments.COLLECT_KEYS)
        val o = arguments.listCollector(r, Toolkit.Locate(100))
        assertEquals(ScrollDirection.LEFT, o.direction)
        assertEquals(2, o.maxSteps)
        assertEquals(ScrollMethod.GESTURE, o.method)
        assertEquals(listOf("txt", "desc"), o.resultType)
        assertFalse(o.dedupe)
        assertEquals(4, o.maxItems)
        assertEquals(10L, o.settleMillis)
        assertEquals(20L, o.gestureDuration)
        assertEquals(Toolkit.Locate(100), o.locate)
        assertTrue(o.until!!("stop"))
        assertFalse(o.until("go"))
        assertEquals(2, invoked.size)
        assertEquals(listOf<Any?>("go"), invoked[1].second)
        assertEquals("$", arguments.listCollector(reader(obj("{ resultType: '$' }"), ToolkitArguments.COLLECT_KEYS), Toolkit.Locate.ONCE).resultType)
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.listCollector(reader(obj("{ resultType: 3 }"), ToolkitArguments.COLLECT_KEYS), Toolkit.Locate.ONCE) }
        assertThrows(IllegalArgumentException::class.java) { arguments.listCollector(reader(obj("{ maxItems: 0 }"), ToolkitArguments.COLLECT_KEYS), Toolkit.Locate.ONCE) }
        assertThrows(WrappedIllegalArgumentException::class.java) { arguments.listCollector(reader(obj("{ until: 'stop' }"), ToolkitArguments.COLLECT_KEYS), Toolkit.Locate.ONCE) }
    }

    @Test
    fun launchBackToggleAndRetryOptions() {
        val launch = arguments.launch(reader(obj("{ timeout: 3000, interval: 100, bringToFront: false }"), ToolkitArguments.LAUNCH_KEYS))
        assertEquals(3000L, launch.timeout)
        assertEquals(100L, launch.interval)
        assertFalse(launch.bringToFront)
        assertEquals(10000L, arguments.launch(reader(null, ToolkitArguments.LAUNCH_KEYS)).timeout)

        val back = arguments.back(reader(obj("{ maxTimes: 4, delay: 100, interval: 25, humanize: { delay: 2 } }"), ToolkitArguments.BACK_KEYS))
        assertEquals(4, back.maxTimes)
        assertEquals(100L, back.delay)
        assertEquals(25L, back.interval)
        assertThrows(IllegalArgumentException::class.java) { arguments.back(reader(obj("{ interval: 0 }"), ToolkitArguments.BACK_KEYS)) }
        assertEquals(Humanize(minDelay = 2, maxDelay = 2), back.humanize)
        val shorthand = arguments.back(arguments.reader(ToolkitArguments.Call("backUntil", emptyList(), null, 6.0, null, null), ToolkitArguments.BACK_KEYS, "maxTimes"))
        assertEquals(6, shorthand.maxTimes)
        assertEquals(500L, shorthand.delay)
        assertThrows(IllegalArgumentException::class.java) { reader(obj("{ timeout: 5 }"), ToolkitArguments.BACK_KEYS) }

        val toggle = arguments.toggle(reader(obj("{ verifyTimeout: 700, verifyInterval: 70, click: { maxClimb: 1 } }"), ToolkitArguments.TOGGLE_KEYS), Toolkit.Locate(9))
        assertEquals(Toggle.Options(locate = Toolkit.Locate(9), verifyTimeout = 700, verifyInterval = 70, click = SmartClick.Options(maxClimb = 1)), toggle)

        assertEquals(Toolkit.RetryOptions(), arguments.retry(reader(null, ToolkitArguments.RETRY_KEYS)))
        val retry = arguments.retry(reader(obj("{ times: 2, delay: 100, backoff: 2 }"), ToolkitArguments.RETRY_KEYS))
        assertEquals(Toolkit.RetryOptions(times = 2, delay = 100, backoff = 2.0), retry)
        assertEquals(5, arguments.retry(arguments.reader(ToolkitArguments.Call("retry", emptyList(), null, 5.0, null, null), ToolkitArguments.RETRY_KEYS, "times")).times)
        assertThrows(IllegalArgumentException::class.java) { arguments.retry(reader(obj("{ backoff: 0.5 }"), ToolkitArguments.RETRY_KEYS)) }
        assertThrows(IllegalArgumentException::class.java) { arguments.retry(reader(obj("{ times: -1 }"), ToolkitArguments.RETRY_KEYS)) }
    }

    @Test
    fun targetsAreAnArrayOrASingleValue() {
        assertEquals(listOf<Any?>("a", "b"), arguments.targets(js("['a', 'b']")!!, "t"))
        assertEquals(listOf<Any?>("a"), arguments.targets("a", "t"))
        assertThrows(IllegalArgumentException::class.java) { arguments.targets(js("[]")!!, "t") }
        assertThrows(IllegalArgumentException::class.java) { arguments.targets(js("['a', null]")!!, "t") }
    }

}
