package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.automator.RootAutomator
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import java.util.concurrent.ConcurrentHashMap

/**
 * Roadmap P3.3, second group: `auto`, `automator` and `RootAutomator` on the new script module layer. The surface of
 * `automator` and `RootAutomator` and the 39 global actions is compared with the current augment calls in the same JVM
 * ([RuntimeModulesFixture]); `auto` is compared by its global only, since its getters need the accessibility service (the
 * device snapshot covers them). The argument rules that fail before the accessibility service, root or Shizuku is reached
 * run here; only the cases that do not depend on the device state go to `build/script-modules-device/AutomatorModulesTest.json`
 * (the `RootAutomator` checks would construct an instance on a device with Shizuku under the current module). The rules the
 * current parsers still check are rows of appendix E (P3.3).
 *
 * zh-CN: P3.3 第二组: 新层上的 auto, automator 与 RootAutomator. automator 与 RootAutomator 的表面及 39 个全局动作在同一 JVM 中与现有 augment 调用比较; auto
 * 只比较其全局项, 因为它的 getter 需要无障碍服务 (由设备快照覆盖). 在到达无障碍服务, root 或 Shizuku 之前就失败的参数规则在此检查; 只有不依赖设备状态的用例写入
 * 设备用例 (现有模块在有 Shizuku 的设备上会对 RootAutomator 的这些检查构造出实例). 仍由现有解析器检查的规则记入附录 E (P3.3).
 */
class AutomatorModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        val globals = GLOBALS - AUTO_NAMES
        val actual = RuntimeModulesFixture.describe(rhino, globals, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, globals, PATHS) }
        assertEquals(expected, actual)
    }

    /** `auto` without reading its getters, which need the accessibility service: names, attributes and the functions' names. */
    @Test
    fun autoSurfaceMatchesTheCurrentModule() {
        val actual = rhino.evalKt(DESCRIBE_AUTO)
        val expected = newSide(newLayer = false).use { it.evalKt(DESCRIBE_AUTO) }
        assertEquals(expected, actual)
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        GLOBALS.filter { it != "\$RootAutomator" }.forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun automatorArguments() = RuntimeModulesFixture.check(
        rhino, listOf(
            "click()" to "throws TypeError",
            "click(null)" to "throws TypeError",
            "click(1, 'a')" to "throws TypeError",
            "click(1, 2, 3)" to "throws TypeError",
            "click(1, NaN)" to "throws RangeError",
            "click('a', 'b')" to "throws TypeError",
            "click(1, 2, 3, 'x')" to "throws TypeError",
            "longClick(undefined)" to "throws TypeError",
            "press()" to "throws TypeError",
            "press('a')" to "throws TypeError",
            "press(1, 2, NaN)" to "throws RangeError",
            "press([1, 2], 'x')" to "throws TypeError",
            "swipe(1)" to "throws TypeError",
            "swipe(1, 2, 3, 4, NaN)" to "throws RangeError",
            "swipe([1, 2], [3, 4])" to "throws TypeError",
            "gesture()" to "throws TypeError",
            "gesture('a', [1, 2])" to "throws TypeError",
            "gesture(-1, [1, 2])" to "throws RangeError",
            "gesture(1, 'a')" to "throws TypeError",
            "gestures()" to "throws TypeError",
            "gestures(1)" to "throws TypeError",
            "gesturesAsync([1], [2], 3)" to "throws TypeError",
            "scrollDown(1, 2, 3)" to "throws TypeError",
            "scrollDown(NaN)" to "throws RangeError",
            "scrollUp(null)" to "throws TypeError",
            "input()" to "throws TypeError",
            "input(NaN, 'a')" to "throws RangeError",
            "input(1, null)" to "throws TypeError",
            "setText(null)" to "throws TypeError",
            "setText(1, 2, 3)" to "throws TypeError",
            "automator.waitForService('a')" to "throws TypeError",
            "automator.waitForService(NaN)" to "throws RangeError",
            "automator.isServiceRunning(1)" to "throws TypeError",
            "automator.switchToInputMethod(null)" to "throws TypeError",
            "automator.switchToInputMethodWithId()" to "throws TypeError",
            "back(1)" to "throws TypeError",
            "home(1)" to "throws TypeError",
            "smartClick()" to "throws TypeError",
            "smartClick(null)" to "throws TypeError",
            "smartClick('a', NaN)" to "throws RangeError",
            "typeInto()" to "throws TypeError",
            "toggle('a')" to "throws TypeError",
            "retry(1)" to "throws TypeError",
            "waitForIdle('a')" to "throws TypeError",
            "waitForIdle(-1)" to "throws RangeError",
            "waitForIdle(1, 2)" to "throws TypeError",
            "waitForEvent(1)" to "throws TypeError",
            "waitForToast(-1)" to "throws RangeError",
            "waitForNotification('a', 'b')" to "throws TypeError",
            // The global and the module member are one function object on the new layer (appendix E, as for `timers.keepAlive`).
            // zh-CN: 新层的全局成员与模块成员是同一个函数对象 (附录 E, 同 timers.keepAlive).
            "automator.click === click" to "true",
            "click.name" to "\"click\"",
            "automator.headsetHook === automator.headsethook" to "true",
        )
    )

    /**
     * Points, point lists, strokes and indexes (appendix C Q65 (2)): what the current module refused, as a TypeError or
     * RangeError. The current module may act on some of them first, so the device runs them with the switch on.
     */
    @Test
    fun pointArguments() = checkFields(
        listOf(
            "click([1])" to "throws TypeError",
            "click([])" to "throws TypeError",
            "click([1, 2147483648])" to "throws RangeError",
            "click([1, NaN])" to "throws RangeError",
            "click({ x: 1 })" to "throws TypeError",
            "click({ x: 'a', y: 1 })" to "throws TypeError",
            "click({ x: 1, y: Infinity })" to "throws RangeError",
            "click(1, 2147483648)" to "throws RangeError",
            "click('a', 2147483648)" to "throws RangeError",
            "click(1, 2, 3, Infinity)" to "throws RangeError",
            "longClick({ x: NaN, y: 1 })" to "throws RangeError",
            "press([1])" to "throws TypeError",
            "press([1, 'a'])" to "throws TypeError",
            "press([1, 2], 2147483648)" to "throws RangeError",
            "press(100, [1])" to "throws TypeError",
            "press(100, [1, null])" to "throws TypeError",
            "press(1, 2, 'a')" to "throws TypeError",
            "press([[1, null], 100])" to "throws TypeError",
            "swipe(100, [1, 2, 3])" to "throws TypeError",
            "swipe(100, [1, 2])" to "throws TypeError",
            "swipe(100, [[1, 2]])" to "throws TypeError",
            "swipe(100, [[1, 2], []])" to "throws TypeError",
            "swipe(100, [[1, 2], [3, 4, 5]])" to "throws TypeError",
            "swipe(100, [[1, 2], 'a'])" to "throws TypeError",
            "swipe(100, [[1, NaN], [3, 4]])" to "throws RangeError",
            "swipe(100, [1, 'a'], [3, 4])" to "throws TypeError",
            "swipe([1, 2], [3, 4], 2147483648)" to "throws RangeError",
            "swipe(1, 2, 3, 4, 'a')" to "throws TypeError",
            "gesture(100, [1, 2, 3])" to "throws TypeError",
            "gesture(100, [[1, 2], [3, 'a']])" to "throws TypeError",
            "gesture(100, [1, 2], [])" to "throws TypeError",
            "gesture(Infinity, [1, 2])" to "throws RangeError",
            "gesture(100, 1, 2, 3)" to "throws TypeError",
            "gestureAsync(100, [1, 2], 3)" to "throws TypeError",
            "gestures([100])" to "throws TypeError",
            "gestures([0, 100])" to "throws TypeError",
            "gestures([100, [1]])" to "throws TypeError",
            "gestures([-1, 100, [1, 2]])" to "throws RangeError",
            "gestures([100, [1, 2], 'a'])" to "throws TypeError",
            "gestures([100, [1, 2147483648]])" to "throws RangeError",
            "gestures(['a', [1, 2]])" to "throws TypeError",
            "gestures([100, [1, 2]], [1, 'a', [1, 2]])" to "throws TypeError",
            "scrollDown(2147483648)" to "throws RangeError",
            "scrollUp('a', 2147483648)" to "throws RangeError",
            "input(2147483648, 'a')" to "throws RangeError",
            "automator.waitForService(Infinity)" to "throws RangeError",
            "automator.waitForService(1e20)" to "throws RangeError",
        )
    )

    /** The options of the synchronous toolkit and the event waits (appendix C Q65 (2)), as [pointArguments]. */
    @Test
    fun toolkitOptionFields() = checkFields(
        listOf(
            "smartClick('aj6-none', { nope: 1 })" to "throws TypeError",
            "clickAny([])" to "throws TypeError",
            "dismissPopups(['aj6-none'], { watch: true, onDismissed: 1 })" to "throws TypeError",
            "dismissPopups(['aj6-none'], { onError: function () {} })" to "throws TypeError",
            "backUntil('aj6-none', { interval: 0 })" to "throws RangeError",
            "retry(function () {}, { backoff: 0.5 })" to "throws RangeError",
            "retry(function () {}, 1.5)" to "throws RangeError",
            "launchAndWait('aj6.none', { nope: 1 })" to "throws TypeError",
            "collectList(null, 'aj6-none', { direction: 'x' })" to "throws RangeError",
            "toggle('aj6-none', true, { click: 1 })" to "throws TypeError",
            "waitForIdle({ eventTypes: 'nope' })" to "throws RangeError",
            "waitForEvent('click', { text: 1 })" to "throws TypeError",
            "waitForToast({ text: 1 })" to "throws TypeError",
            "waitForNotification({ nope: 1 })" to "throws TypeError",
        )
    )

    /**
     * JVM only (they act on a device): what the current module accepts passes the new checks, numeric strings and the
     * lenient point list forms included (appendix C Q65 (2)); without the service the host then fails otherwise.
     */
    @Test
    fun acceptedArguments() = RuntimeModulesFixture.check(
        rhino, listOf(
            "click('10', '20')", "click([1, 'a'])", "click({ x: '1', y: 2 })", "click(1, 2, '3', 4)",
            "press([[1, 2], 100])", "press(1, '2', 3)",
            "swipe('1', '2', 3, 4, '5')", "swipe(100, [[1, 2], [3]])", "swipe(100, [['1', 2], [3, 4]])", "swipe(100, [1, Infinity], [3, 4])",
            "gesture('100', [1, 2], [3])", "gesture(100, [[[1, 2], [3, 4]]])", "gestures([0, 100, [1, 2], [3, 4, 5]])",
            "scrollDown('1')", "input('1', 'a')",
        ).map(::passes)
    )

    /** JVM only: under the current module some of these change the mode, start the service or wait for it on a device. */
    @Test
    fun autoArguments() = RuntimeModulesFixture.check(
        rhino, listOf(
            "typeof auto" to "\"function\"",
            "\$auto === auto" to "true",
            "auto(1)" to "throws TypeError",
            "auto('x')" to "throws RangeError",
            "auto('fast', 1)" to "throws TypeError",
            "auto('x', true)" to "throws RangeError",
            "auto(1, true)" to "throws TypeError",
            "auto('fast', true, 1)" to "throws TypeError",
            "auto.setMode()" to "throws TypeError",
            "auto.setMode(1)" to "throws TypeError",
            "auto.setMode('x')" to "throws RangeError",
            "auto.setFlags(1)" to "throws TypeError",
            "auto.setFlags([1])" to "throws TypeError",
            "auto.setFlags(['x'])" to "throws RangeError",
            "auto.setFlags('x')" to "throws RangeError",
            "auto.waitFor('a')" to "throws TypeError",
            "auto.waitFor(NaN)" to "throws RangeError",
            "auto.waitFor(1, 2)" to "throws TypeError",
            "auto.registerEvent()" to "throws TypeError",
            "auto.registerEvent(null, function () {})" to "throws TypeError",
            "auto.registerEvent('a', 1)" to "throws TypeError",
            "auto.registerEvent('a', null, 1)" to "throws TypeError",
            "auto.removeEvent(null)" to "throws TypeError",
            "auto.removeEvents()" to "throws TypeError",
            "auto.getWindowRoot({})" to "throws TypeError",
            "auto.getWindowRoot()" to "throws TypeError",
            "auto.setWindowFilter(1)" to "throws TypeError",
            "auto.findWindows(1)" to "throws TypeError",
            "auto.findWindowRoots('a')" to "throws TypeError",
            "auto.dump(1)" to "throws TypeError",
            "auto.explain()" to "throws TypeError",
            "auto.explain('a', 1)" to "throws TypeError",
            "auto.start(1)" to "throws TypeError",
            "auto.isRunning(1)" to "throws TypeError",
            "auto.stateListener(1)" to "throws TypeError",
            "auto.wait()" to "throws TypeError",
            "auto.wait(null)" to "throws TypeError",
            "auto.wait(function () {}, NaN)" to "throws RangeError",
        )
    )

    /** `RootAutomator` checks its argument before root or Shizuku is looked for; on a device with Shizuku the current module constructs. */
    @Test
    fun rootAutomatorArguments() = RuntimeModulesFixture.check(
        rhino, listOf(
            "new RootAutomator('x')" to "throws TypeError",
            "new RootAutomator({})" to "throws TypeError",
            "new RootAutomator(-1)" to "throws RangeError",
            "new RootAutomator(NaN)" to "throws RangeError",
            "new RootAutomator(true, 1)" to "throws TypeError",
        )
    )

    /**
     * Cases for the device, in both states of the switch: what does not depend on the accessibility service, root or
     * Shizuku, and the argument errors that the current module also rejects before it acts.
     */
    @Test
    fun exportsDeviceCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT, listOf(
            "typeof RootAutomator" to "\"function\"",
            "typeof auto" to "\"function\"",
            "\$auto === auto" to "true",
            "auto(1)" to "throws TypeError",
            "auto('x')" to "throws RangeError",
            "auto('x', true)" to "throws RangeError",
            "auto(1, true)" to "throws TypeError",
            "auto('fast', true, 1)" to "throws TypeError",
            "auto.setMode()" to "throws TypeError",
            "auto.setMode(1)" to "throws TypeError",
            "auto.setMode('x')" to "throws RangeError",
            "auto.waitFor(1, 2)" to "throws TypeError",
            "auto.registerEvent()" to "throws TypeError",
            "auto.registerEvent(null, function () {})" to "throws TypeError",
            "auto.wait()" to "throws TypeError",
            "auto.wait(null)" to "throws TypeError",
            "typeof \$RootAutomator" to "\"undefined\"",
            "typeof auto.isRunning()" to "\"boolean\"",
            "typeof auto.hasService()" to "\"boolean\"",
            "typeof auto.state.isRunning" to "\"boolean\"",
            "Array.isArray(auto.services)" to "true",
            "typeof auto.stats" to "\"object\"",
            "typeof automator.isServiceRunning()" to "\"boolean\"",
            "auto.wait(function () { return true; }) instanceof flow.Flow" to "true",
        )
    )

    private fun checkFields(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT_FIELDS, cases)
    }

    /** [expression] is not refused by a TypeError or RangeError (whatever the host does after the checks). */
    private fun passes(expression: String) =
        "(function () { try { $expression; } catch (e) { if (e instanceof TypeError || e instanceof RangeError) return e.name + ': ' + e.message; } return 'passed'; })()" to "\"passed\""

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        val runtime = side.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        // `auto.wait` reads the runtime's flow executor, which is kept in its properties and closed with it.
        // zh-CN: auto.wait 读取运行时的 Flow 执行器, 它存于运行时属性中并随运行时关闭.
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("automator-modules-test"))
        RuntimeModulesFixture.mount(side, MODULES, newLayer) { name, global, rt ->
            when (name) {
                "auto" -> Auto(rt).augment(global)
                "automator" -> Automator(rt).augment(global)
                "RootAutomator" -> RootAutomator(rt).augment(global)
                else -> throw AssertionError(name)
            }
        }
    }

    private companion object {
        const val EXPORT = "AutomatorModulesTest"
        const val EXPORT_FIELDS = "AutomatorFieldsTest"
        val MODULES = listOf("auto", "automator", "RootAutomator")
        val AUTO_NAMES = listOf("auto", "\$auto")
        val AUTO_GETTERS = listOf("service", "services", "windows", "root", "rootInActiveWindow", "windowRoots", "stats", "state")
        val DESCRIBE_AUTO = """
            (function () {
                var getters = ${AUTO_GETTERS.joinToString(", ", "[", "]") { "'$it'" }};
                function attrs(d) { return (d.enumerable ? 'E' : '-') + (d.writable ? 'W' : '-') + (d.configurable ? 'C' : '-') + (d.get || d.set ? 'A' : ''); }
                var g = Object.getOwnPropertyDescriptor(globalThis, 'auto');
                var out = ['auto ' + attrs(g) + ' ' + typeof g.value + ' ' + g.value.name, '${'$'}auto ' + attrs(Object.getOwnPropertyDescriptor(globalThis, '${'$'}auto')) + ' ' + (${'$'}auto === auto)];
                out.push('proto ' + (Object.getPrototypeOf(auto) === Function.prototype));
                out.push('keys ' + Object.keys(auto).join(','));
                Object.getOwnPropertyNames(auto).filter(function (k) { return ['length', 'arity', 'name'].indexOf(k) < 0; }).forEach(function (k) {
                    if (getters.indexOf(k) >= 0) return out.push(k + ' getter');
                    var d = Object.getOwnPropertyDescriptor(auto, k);
                    out.push(k + ' ' + attrs(d) + ' ' + (typeof d.value === 'function' ? 'function ' + d.value.name : typeof d.value));
                });
                return out.join(' | ');
            })()
        """.trimIndent()
        val PATHS = listOf("automator", "RootAutomator")
        val GLOBALS = listOf(
            "click", "longClick", "press", "swipe", "gesture", "gestureAsync", "gestures", "gesturesAsync", "scrollDown", "scrollUp",
            "input", "setText", "back", "home", "powerDialog", "notifications", "quickSettings", "recents", "splitScreen",
            "smartClick", "smartClickBounds", "waitForIdle", "waitForEvent", "waitForToast", "waitForNotification", "clickIfExists",
            "clickBoundsIfExists", "clickAny", "clickBoundsAny", "findAny", "scrollUntil", "typeInto", "dismissPopups", "collectList",
            "launchAndWait", "backUntil", "backToApp", "toggle", "retry",
            "auto", "\$auto", "automator", "\$automator", "RootAutomator", "\$RootAutomator",
        )
    }

}
