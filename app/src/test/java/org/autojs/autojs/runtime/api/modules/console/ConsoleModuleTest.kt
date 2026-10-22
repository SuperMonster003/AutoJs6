package org.autojs.autojs.runtime.api.modules.console

import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import java.io.File

/**
 * Roadmap P3.2 (group 2): `console` on the new script module layer. The runtime's console is a recording `ConsoleImpl`
 * (allocated without its Android constructor), so the JVM compares what `console.log` and the other printing functions
 * print against the current `augment/console/Console` (`Util.formatRhino`, the differences are listed in appendix E),
 * checks that `console.log(o)` prints `util.inspect(o)` and `console.log(format, ...args)` prints `util.format(...)`
 * (decision b), the surface (the members behind the proxy and the global functions), the obsolete `__console__.js`
 * members and the argument errors. Windows, sizes, colors and the time table need Android: their calls run on the device
 * and leave the window hidden (`show` only with the overlay permission, hidden at once).
 *
 * zh-CN: P3.2 (第 2 组): 新层的 console. 运行时的控制台是一个记录输出的 ConsoleImpl (不经其 Android 构造函数分配), 因此 JVM 对照现有 augment/console/Console
 * (Util.formatRhino, 差异列在附录 E) 比较 console.log 等打印函数的输出, 检查 console.log(o) 打印 util.inspect(o), console.log(format, ...args) 打印
 * util.format(...) (决定 b), 检查表面 (代理背后的成员与全局函数), 旧 __console__.js 的成员与参数错误. 窗口, 尺寸, 颜色与计时表需要 Android: 这些调用在真机
 * 运行, 窗口保持隐藏 (仅在有悬浮窗权限时 show, 并随即隐藏).
 */
class ConsoleModuleTest {

    /** The runtime console: records each printed line (static, an instance allocated without constructor has no fields). */
    class RecordingConsole(uiHandler: UiHandler) : ConsoleImpl(uiHandler) {

        override fun println(level: Int, charSequence: CharSequence): String? {
            synchronized(RECORDS) { RECORDS.add("${LEVELS[level] ?: level}|$charSequence") }
            return null
        }

        companion object {

            val RECORDS = ArrayList<String>()

            private val LEVELS = mapOf(2 to "V", 3 to "D", 4 to "I", 5 to "W", 6 to "E", 7 to "A")

            /** The lines printed since the last call, joined with " ## ", or "none". */
            fun take(): String = synchronized(RECORDS) { (RECORDS.joinToString(" ## ").ifEmpty { "none" }).also { RECORDS.clear() } }

        }

    }

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        RecordingConsole.take()
        rhino = DifferentialRhino()
        prepare(rhino)
        HostModuleTestSupport.setField(rhino.ktRuntime, "scriptModules", rhino.mountNewModules("util", "console"))
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /** Both sides: the recording console and `printed()`, which returns the lines printed since its last call. */
    private fun prepare(side: DifferentialRhino) {
        HostModuleTestSupport.setField(side.ktRuntime, "console", DifferentialRhino.allocateWithoutConstructor(RecordingConsole::class.java))
        ScriptableObject.putProperty(side.ktScope.globalThis, "printed", object : BaseFunction() {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any = RecordingConsole.take()
        })
    }

    /**
     * The current module as a plain object with the same members (`augment`): a read through its proxy fails on the JVM
     * (`AugmentableProxy.get` derives the names with `StringUtils`, whose class needs Android to load), the device
     * compares the proxies. Its setters return the proxy of the old augment call, made on a throwaway object.
     */
    private fun mountCurrent(side: DifferentialRhino) {
        val runtime = side.ktRuntime
        HostModuleTestSupport.setField(runtime, "legacyConsoleProxyObject", Console(runtime).proxying(RhinoUtils.newNativeObject(runtime.topLevelScope), runtime.console))
        Console(runtime).augment(side.ktScope.globalThis as ScriptableObject, runtime.console)
    }

    @Test
    fun installingBuildsNothing() {
        listOf("console", "\$console").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        val actual = SurfaceDiff.surface(rhino.evalKt("console.__augmented__"), rhino.ktScope.globalThis)
        val expected = DifferentialRhino().use { current ->
            prepare(current)
            mountCurrent(current)
            SurfaceDiff.surface(current.evalKt("console"), current.ktScope.globalThis)
        }
        val report = SurfaceDiff.compare(expected, actual)
        assertTrue(SurfaceDiff.describe(report, "current", "new"), report.namesMatch && report.attributeMismatches.isEmpty())
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
    }

    @Test
    fun globalFunctionsMatchTheCurrentModule() {
        val names = GLOBALS.joinToString(", ") { "'$it'" }
        val expression = "[$names].map(function (k) { var d = Object.getOwnPropertyDescriptor(this, k); " +
            "return k + ':' + typeof this[k] + ':' + (d ? [d.writable, d.enumerable, d.configurable].join('/') : 'none'); }, this).join()"
        assertEquals(1, HostModuleTestSupport.assertSameResults(rhino, listOf(expression), emptyMap(), ::prepare, ::mountCurrent))
    }

    @Test
    fun printedLinesMatchTheCurrentModule() {
        val compared = HostModuleTestSupport.assertSameResults(rhino, PRINTED, KNOWN, ::prepare, ::mountCurrent)
        assertTrue("compared $compared", compared >= PRINTED.size - 2)
    }

    /** Decision b of P3.2: console formats as util.format / util.inspect do. zh-CN: P3.2 决定 b: console 与 util.format / util.inspect 格式一致. */
    @Test
    fun printingFollowsUtilFormat() {
        HostModuleTestSupport.check(rhino, INSPECTED.map { value -> "(console.log($value), printed()) === 'D|' + util.inspect($value)" to "true" })
        HostModuleTestSupport.check(rhino, FORMATTED.map { args -> "(console.log($args), printed()) === 'D|' + util.format($args)" to "true" })
        HostModuleTestSupport.check(rhino, LEVELLED)
        HostModuleTestSupport.check(rhino, SYMBOLS)
        DEVICE.add(SYMBOLS)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val source = File(ObsoleteModuleHost.OBSOLETE_DIR, "__console__.js").readText()
        val members = Regex("^ {16}([A-Za-z]+)(?:\\(|: function)", RegexOption.MULTILINE).findAll(source).map { it.groupValues[1] }.toList()
        assertTrue(members.toString(), members.size >= 44 && "trace" in members && "printAllStackTrace" in members)
        val globals = listOf("verbose", "print", "log", "warn", "err", "openConsole", "showConsole", "clearConsole", "launchConsole")
        HostModuleTestSupport.check(rhino, members.map { "typeof console.$it" to "\"function\"" } + globals.map { "typeof $it" to "\"function\"" })
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("ConsoleModuleTest", "new script module layer (P3.2)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val GLOBALS = listOf("log", "verbose", "warn", "print", "err", "openConsole", "showConsole", "clearConsole", "launchConsole")

        private val SURFACE = listOf(
            "[typeof console, console === \$console, typeof console.log, typeof console.__augmented__.log]" to "[\"object\",true,\"function\",\"function\"]",
            "Object.keys(console.__augmented__).slice(0, 12).join()" to "\"trace,show,hide,reset,clear,expand,collapse,assert,input,rawInput,log,verbose\"",
            "Object.keys(console.__augmented__).slice(-4).join()" to "\"setGlobalLogConfig,resetGlobalLogConfig,launch,printAllStackTrace\"",
            "typeof console.isShowing" to "\"function\"",
            // The bean property of ConsoleImpl#isShowing is found first, as through the current proxy.
            "typeof console.showing" to "\"boolean\"",
            // Configurable, as the names the current proxy mounts.
            "['console', '\$console'].map(function (k) { var d = Object.getOwnPropertyDescriptor(this, k); " +
                "return [d.writable, d.enumerable, d.configurable].join('/'); }, this).join()" to "\"true/true/true,true/true/true\"",
        )

        /** Each expression prints, then returns what was printed (`printed()`). */
        private val PRINTED = listOf(
            "'a'", "'a', 1, true, null, undefined", "'%s=%d', 'x', 5", "'%s=%d', 'x', '5'", "'%j', { a: [1, 'x'] }", "'100%%'",
            "'%o', { a: 1 }", "'%s'", "'%d %d', 1", "'%s', { a: 1 }", "'%s', [1, [2, 3]]", "'a\\nb'",
            "1", "1.5, -0, NaN, Infinity", "0x10", "{ a: 1, b: 'x' }", "{ a: { b: { c: { d: 1 } } } }", "[1, 'a', [2, [3]]]",
            "[]", "{}", "null", "undefined", "", "function foo() {}", "Symbol('s')", "new Date(0)", "/re/g",
            "new Map([[1, 2]])", "['a\\'b']", "{ 'b-c': 1, _d: 2 }", "true, { a: 1 }", "{ a: 1 }, 'x'",
            "java.lang.Integer.valueOf(7)", "new java.lang.StringBuilder('sb')", "(function () { var o = {}; o.o = o; return o; })()",
        ).flatMap { args ->
            listOf("console.log", "console.error").map { f -> "($f($args), printed())" }
        } + listOf(
            "(console.verbose('v', 1), printed())",
            "(console.info({ a: 1 }), printed())",
            "(console.warn('w', [1]), printed())",
            "(console.print('p', { a: 1 }), printed())",
            "(console.error(new Error('e1')), printed().split('\\n')[0])",
            "(console.error({ flowStack: 'f', stack: 'flow stack' }), printed())",
            "(console.error('x', { flowStack: 'f', stack: 'flow stack' }), printed())",
            "(console.error({ flowStack: 'f', stack: 'flow %s' }, 1), printed())",
            "(log('g', 1), verbose('g'), warn('g'), print('g'), err('g'), printed())",
            "(console.trace('t'), printed().split('\\n')[0])",
            "(console.trace('t', 'info'), printed().split('\\n')[0])",
            "(console.trace('t', 'WARN'), printed().split('\\n')[0])",
            "(console.trace('t', 'Verbose'), printed().split('\\n')[0])",
            "(console.trace('t', 6), printed().split('\\n')[0])",
            "(console.trace('t', 3), printed().split('\\n')[0])",
            "(console.trace({ a: 1 }), printed().split('\\n')[0])",
            "(console.trace('t', 'x'), printed())",
            "(console.trace('t', 9), printed())",
            "(console.trace('t'), printed().split('\\n').length > 1)",
            // Each assertion first takes the lines left over (an assertion failing past try / catch leaves its line).
            "(function () { printed(); try { console.assert(false, 'm'); return 'no'; } catch (e) { return printed() + '|' + e.name; } })()",
            "(function () { printed(); try { console.assert(0); return 'no'; } catch (e) { return printed(); } })()",
            "(function () { printed(); try { console.assert(function () { return ''; }, 'f'); return 'no'; } catch (e) { return printed(); } })()",
            "(printed(), console.assert(true, 'm'), printed())",
            "(printed(), console.assert(function () { return 1; }), printed())",
            "(console.constructor = 1, 'ok')",
        )

        private const val ASSERTION_REASON = "a failed assertion is a Java exception of the error model (T5), caught as JavaException (the current " +
            "module's ScriptException passed try / catch and ended the script; uncaught, it still does), appendix C Q40"

        /** The known differences to the current module (appendix E "P3.2"): the new value and the reason. */
        private val KNOWN = mapOf(
            "(console.trace('t', 'x'), printed())" to (RANGE to "an unknown level word is a RangeError (the current module printed at 'debug')"),
            "(console.trace('t', 9), printed())" to (RANGE to "an unknown level number is a RangeError (the current module printed at 'debug')"),
            "(console.error(new Error('e1')), printed().split('\\n')[0])" to ("\"E|[Error: e1]\"" to "an Error object is printed as util.inspect prints it (the current module called its toString)"),
            "(function () { printed(); try { console.assert(false, 'm'); return 'no'; } catch (e) { return printed() + '|' + e.name; } })()" to ("\"A|m|JavaException\"" to ASSERTION_REASON),
            "(function () { printed(); try { console.assert(0); return 'no'; } catch (e) { return printed(); } })()" to ("\"A|java.lang.AssertionError\"" to ASSERTION_REASON),
            "(function () { printed(); try { console.assert(function () { return ''; }, 'f'); return 'no'; } catch (e) { return printed(); } })()" to ("\"A|f\"" to ASSERTION_REASON),
        ) + listOf("console.log" to "D", "console.error" to "E").flatMap { (f, level) ->
            listOf(
                "($f(Symbol('s')), printed())" to (TYPE to "a TypeError as util.inspect(Symbol()) throws, caught by try / catch (the current module's passed it)"),
                "($f(['a\\'b']), printed())" to ("\"$level|[ 'a\\\\'b' ]\"" to "a quote in a string is escaped, as by util.inspect (the current module did not escape it)"),
                "($f({ 'b-c': 1, _d: 2 }), printed())" to ("\"$level|{ 'b-c': 1, _d: 2 }\"" to "a key that is no identifier is single-quoted, as by util.inspect (the current module double-quoted it)"),
                "($f(java.lang.Integer.valueOf(7)), printed())" to ("\"$level|[JavaObject: 7]\"" to "a boxed Java value is marked, as by util.inspect (the current module printed the value)"),
            )
        }

        private val INSPECTED = listOf(
            "1", "-0", "null", "undefined", "true", "{ a: 1, b: 'x' }", "{ a: { b: { c: { d: 1 } } } }", "[1, 'a', [2, [3]]]",
            "[]", "{}", "function foo() {}", "new Date(0)", "/re/g", "new Map([[1, 2]])", "new Set([1])",
            "new Error('e')", "java.lang.Integer.valueOf(7)", "(function () { var o = {}; o.o = o; return o; })()",
        )

        /** A symbol is refused as by util.inspect. */
        private val SYMBOLS = listOf(
            "util.inspect(Symbol('s'))" to TYPE,
            "console.log(Symbol('s'))" to TYPE,
            "console.error(Symbol('s'))" to TYPE,
        )

        private val FORMATTED = listOf(
            "'%s=%d', 'x', 5", "'%j', { a: [1, 'x'] }", "'100%%'", "'%o', { a: 1 }", "'%s'", "'%d %d', 1",
            "'a', { a: 1 }, [1]", "1, 'a'", "{ a: 1 }, 'x'", "null, undefined", "true, { a: 1 }",
        )

        private val LEVELLED = listOf(
            "(console.verbose({ a: 1 }), printed())" to "\"V|{ a: 1 }\"",
            "(console.info({ a: 1 }), printed())" to "\"I|{ a: 1 }\"",
            "(console.warn({ a: 1 }), printed())" to "\"W|{ a: 1 }\"",
            "(console.error({ a: 1 }), printed())" to "\"E|{ a: 1 }\"",
            "(console.print({ a: 1 }), printed())" to "\"D|{ a: 1 }\"",
            "(console.error('%s!', 'e'), printed())" to "\"E|e!\"",
            "(console.trace({ a: 1 }, 'warn'), printed().split('\\n')[0])" to "\"W|{ a: 1 }\"",
            "(console.log(), printed())" to "\"D|\"",
        )

        private val ERRORS = listOf(
            "console.trace('t', 'x')" to RANGE,
            "console.trace('t', 1)" to RANGE,
            "console.trace('t', 7)" to RANGE,
            "console.trace('t', 2.5)" to RANGE,
            "console.trace('t', {})" to TYPE,
            "console.trace('t', true)" to TYPE,
            "console.trace('t', 'info', 1)" to TYPE,
            "console.show(1)" to TYPE,
            "console.show('true')" to TYPE,
            "console.show(true, 1)" to TYPE,
            "console.hide(1)" to TYPE,
            "console.clear(1)" to TYPE,
            "console.assert()" to TYPE,
            "console.assert(true, Symbol())" to TYPE,
            "console.assert(true, 'm', 1)" to TYPE,
            "console.time(Symbol())" to TYPE,
            "console.time('a', 1)" to TYPE,
            "console.timeEnd('a', 1)" to TYPE,
            "console.build(1)" to TYPE,
            "console.build([])" to TYPE,
            "console.build({}, 1)" to TYPE,
            "console.build({ nope: 1 })" to RANGE,
            "console.setSize()" to TYPE,
            "console.setSize(1)" to TYPE,
            "console.setSize(null, 1)" to TYPE,
            "console.setSize('a', 1)" to TYPE,
            "console.setSize(NaN, 1)" to RANGE,
            "console.setSize(1, Infinity)" to RANGE,
            "console.setSize(1, 2, 3)" to TYPE,
            "console.setPosition({}, 1)" to TYPE,
            "console.setTitle(Symbol())" to TYPE,
            "console.setTitle('a', 1)" to TYPE,
            "console.setTitleTextSize()" to TYPE,
            "console.setTitleTextSize(NaN)" to RANGE,
            "console.setTitleTextColor()" to TYPE,
            "console.setTitleTextColor(true)" to TYPE,
            "console.setTitleTextColor({})" to TYPE,
            "console.setTitleTextColor('not a color')" to RANGE,
            "console.setTitleBackgroundTint(true)" to TYPE,
            "console.setTitleBackgroundAlpha('x')" to TYPE,
            "console.setTitleBackgroundAlpha(NaN)" to RANGE,
            "console.setTitleIconsTint([])" to TYPE,
            "console.setContentTextColor(1, 2, 3, 4, 5, 6, 7)" to TYPE,
            "console.setContentTextColor({ nope: 1 })" to RANGE,
            "console.setContentTextColor({ log: true })" to TYPE,
            "console.setContentTextColor([true])" to TYPE,
            "console.setContentTextColor(1, true)" to TYPE,
            "console.setContentTextColors(1, 2, 3, 4, 5, 6, 7)" to TYPE,
            "console.setBackgroundTint(true)" to TYPE,
            "console.setBackgroundAlpha(Infinity)" to RANGE,
            "console.setTextSize('x')" to TYPE,
            "console.setExitOnClose('1000')" to TYPE,
            "console.setExitOnClose({})" to TYPE,
            "console.setExitOnClose(1.5)" to RANGE,
            "console.setExitOnClose(true, 1)" to TYPE,
            "console.setTouchable(1)" to TYPE,
            "console.setTouchable('true')" to TYPE,
            "console.setTimeVisible('yes')" to TYPE,
            "console.setColorful(1)" to TYPE,
            "console.setAvoidStatusBar({})" to TYPE,
            "console.setInputVisible(0)" to TYPE,
            "console.setTimeFormat(Symbol())" to TYPE,
            "console.setGlobalLogConfig()" to TYPE,
            "console.setGlobalLogConfig(1)" to TYPE,
            "console.setGlobalLogConfig({ nope: 1 })" to RANGE,
            "console.setGlobalLogConfig({ rootLevel: 'loud' })" to RANGE,
            "console.setGlobalLogConfig({ rootLevel: 1 })" to RANGE,
            "console.setGlobalLogConfig({ maxFileSize: 1.5 })" to RANGE,
            "console.setGlobalLogConfig({ maxBackupSize: 'x' })" to TYPE,
            "console.setGlobalLogConfig({ file: Symbol() })" to TYPE,
            "console.setGlobalLogConfig({ immediateFlush: 1 })" to TYPE,
            "console.setGlobalLogConfig({}, 1)" to TYPE,
            "console.resetGlobalLogConfig(1)" to TYPE,
            "console.launch(1)" to TYPE,
            "console.printAllStackTrace()" to TYPE,
            "console.printAllStackTrace(1)" to TYPE,
            "console.printAllStackTrace({})" to TYPE,
            "(console.nope = 1)" to TYPE,
            "(console.isShowing = 1)" to TYPE,
            "openConsole(1)" to TYPE,
            "showConsole('x')" to TYPE,
            "clearConsole(1)" to TYPE,
            "launchConsole(1)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "(console.log('aj6-p32 %s', 'log'), 'ok')" to "\"ok\"",
            "(console.trace('aj6-p32 trace', 'verbose'), 'ok')" to "\"ok\"",
            "(console.time('aj6-p32'), console.timeEnd('aj6-p32'), 'ok')" to "\"ok\"",
            "(console.time(), console.timeEnd(), 'ok')" to "\"ok\"",
            "typeof console.isShowing()" to "\"boolean\"",
            "typeof console.build({ title: 'aj6-p32', size: [0.5, 0.4], position: [0, 0.1], touchable: true })" to "\"object\"",
            "typeof console.build()" to "\"object\"",
            "console.setTitle('aj6-p32') === console" to "true",
            "console.setTitle(null) === console" to "true",
            "(console.title = 'aj6-p32 b', 'ok')" to "\"ok\"",
            "(console.size = [0.5, 0.4], 'ok')" to "\"ok\"",
            "console.setSize(0.5, 0.4) === console" to "true",
            "console.setSize(-1, 300) === console" to "true",
            "console.setPosition(0, 0.1) === console" to "true",
            "console.setTitleTextSize(16) === console" to "true",
            "console.setTitleTextColor('#FF6600') === console" to "true",
            "console.setTitleTextColor(colors.toInt('red')) === console" to "true",
            "console.setTitleBackgroundColor('blue') === console" to "true",
            "console.setTitleBackgroundTint(null) === console" to "true",
            "console.setTitleBackgroundAlpha(0.8) === console" to "true",
            "console.setTitleIconsTint('white') === console" to "true",
            "console.setContentTextSize(14) === console" to "true",
            "console.setContentTextColor({ log: '#FF0000', error: 'orange' }) === console" to "true",
            "console.setContentTextColor(['red', null, 'blue']) === console" to "true",
            "console.setContentTextColor(colors.build('red')) === console" to "true",
            "console.setContentTextColor({ log: colors.build('red') }) === console" to "true",
            "console.setContentTextColor('green') === console" to "true",
            "console.setContentTextColor('red', 'green') === console" to "true",
            "console.setContentTextColor() === console" to "true",
            "console.setContentTextColors(['red', 'green']) === console" to "true",
            "console.setContentBackgroundColor('black') === console" to "true",
            "console.setContentBackgroundTint(null) === console" to "true",
            "console.setContentBackgroundAlpha(0.6) === console" to "true",
            "console.setTextSize(14) === console" to "true",
            "console.setTextColor('white') === console" to "true",
            "console.setBackgroundColor('black') === console" to "true",
            "console.setBackgroundTint(null) === console" to "true",
            "console.setBackgroundAlpha(0.9) === console" to "true",
            "console.setExitOnClose(false) === console" to "true",
            "console.setExitOnClose(5000) === console" to "true",
            "console.setExitOnClose() === console" to "true",
            "console.setTouchable(true) === console" to "true",
            "console.setTimeVisible() === console" to "true",
            "console.setTimeFormat('HH:mm:ss') === console" to "true",
            "console.setTimeFormat(null) === console" to "true",
            "console.setColorful(false) === console" to "true",
            "console.setAvoidStatusBar(true) === console" to "true",
            "console.setInputVisible(false) === console" to "true",
            "console.reset() === console" to "true",
            "console.collapse() === console" to "true",
            "console.expand() === console" to "true",
            "console.hide() === console" to "true",
            "(console.printAllStackTrace(new Error('aj6-p32')), 'ok')" to "\"ok\"",
            "(console.printAllStackTrace('aj6-p32'), 'ok')" to "\"ok\"",
            "(function () { if (!floaty.hasPermission()) return 'no permission'; console.show(); var r = console.isShowing(); console.hide(); return r; })()" to DeviceCases.INFO,
        )

    }

}
