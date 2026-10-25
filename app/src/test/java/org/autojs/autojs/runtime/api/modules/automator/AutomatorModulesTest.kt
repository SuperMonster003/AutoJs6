package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.automator.EventWaitArguments
import org.autojs.autojs.runtime.api.augment.automator.RootAutomator
import org.autojs.autojs.runtime.api.augment.automator.RootAutomatorNativeObject
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.CurrentModuleResults
import org.autojs.autojs.runtime.api.modules.ObsoleteMembers
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.auto.AutoOptions
import org.autojs.autojs.runtime.api.modules.toolkit.ToolkitArgs
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.autojs.autojs.core.inputevent.RootAutomator as CoreRootAutomator
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

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
        val expected = ScriptModuleExpectations.obsolete("describe") { newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, globals, PATHS) } }
        assertEquals(expected, actual)
    }

    /** `auto` without reading its getters, which need the accessibility service: names, attributes and the functions' names. */
    @Test
    fun autoSurfaceMatchesTheCurrentModule() {
        val actual = rhino.evalKt(DESCRIBE_AUTO).toString()
        val expected = ScriptModuleExpectations.obsolete("describe: auto") { newSide(newLayer = false).use { it.evalKt(DESCRIBE_AUTO).toString() } }
        assertEquals(expected, actual)
    }

    /**
     * Every member of the obsolete `__automator__.js` and `__RootAutomator__.js` (P3.6) exists on the new layer with the same
     * type. `automator` (the methods of its constructor's prototype) is compared by [ObsoleteMembers]; `auto`, which the
     * obsolete module put on the global scope, by its own properties without calling the getters, which need the
     * accessibility service on both sides (as [autoSurfaceMatchesTheCurrentModule]): an obsolete getter is one of the new
     * getters, any other member a property of the same type, also one the new object inherits (the obsolete prototypes had
     * their own `toString`, returning `'automator'` and `'auto'`; the new objects inherit `Object.prototype.toString`). The names the module put on the global scope (twelve automator functions and seven
     * SimpleActionAutomator actions by `__asGlobal__`, and `auto`) are globals of the same type here. The factory runs
     * against stub runtime members (`automator` and `accessibilityBridge` are only kept at load time; `context` is the
     * global the module passed to AccessibilityTool, which then takes the application context of the fixture).
     *
     * Neither module can build a RootAutomator on the JVM (root or Shizuku is looked for first, and the Java RootAutomator
     * starts a root shell), so its instance members are compared by name: the 17 members the obsolete constructor bound,
     * parsed from its source, are functions of a RootAutomatorNativeObject of the new layer read through its own `get` (which
     * binds the members of the Java RootAutomator, as on a device), both objects made without their constructors.
     *
     * zh-CN: 旧 __automator__.js 与 __RootAutomator__.js 的每个成员 (P3.6) 都以相同类型存在于新层. automator (其构造器原型上的方法) 用 ObsoleteMembers 比较;
     * 旧模块放到全局的 auto 按自有属性比较, 不调用 getter (两侧都需要无障碍服务, 同 autoSurfaceMatchesTheCurrentModule): 旧 getter 须为新层的 getter, 其他
     * 成员须为同类型属性, 继承来的也可以 (旧原型有自己的 toString, 返回 'automator' 与 'auto'; 新对象继承 Object.prototype.toString). 旧模块放到全局的名称 (__asGlobal__ 放的 12 个 automator 函数与 7 个 SimpleActionAutomator 动作, 以及 auto) 在此为同类型的全局.
     * 工厂函数以 stub 运行时成员加载 (automator 与 accessibilityBridge 加载时只被保存; context 是旧模块传给 AccessibilityTool 的全局, 随后取夹具的应用上下文).
     * 两个模块在 JVM 上都无法构造 RootAutomator (先查找 root 或 Shizuku, Java RootAutomator 会启动 root shell), 所以实例成员按名称比较: 从旧构造器源码解析出
     * 它绑定的 17 个成员, 须为新层 RootAutomatorNativeObject 经自身 get 读到的函数 (与真机一样绑定 Java RootAutomator 的成员), 两个对象都不经构造器创建.
     */
    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        // The obsolete modules run only when recording; what is read of them is replayed otherwise (ScriptModuleExpectations).
        // zh-CN: 旧模块只在记录时运行, 其余时候回放读到的内容.
        val host by lazy { ObsoleteModuleHost(rhino.context, rhino.jsScope) }
        val before by lazy {
            rhino.evalJs(OBSOLETE_STUBS)
            host.setRuntimeProperty("automator", rhino.evalJs("({})"))
            host.setRuntimeProperty("accessibilityBridge", rhino.evalJs("({})"))
            host.global.allIds.toSet()
        }
        val automator = ScriptModuleExpectations.obsoleteSurface("surface: automator") {
            before
            SurfaceDiff.surface(host.load("automator"), rhino.jsScope.globalThis)
        }
        val problems = ObsoleteMembers.problems(automator, rhino.evalKt("automator"), rhino.ktScope.globalThis)
        assertTrue("automator:\n" + problems.joinToString("\n"), problems.isEmpty())

        val jsAuto = memberKinds(ScriptModuleExpectations.obsolete("member kinds: auto") { rhino.evalJs("$MEMBER_KINDS(global.auto, [])").toString() })
        val ktAuto = memberKinds(rhino.evalKt("$MEMBER_KINDS(auto, $AUTO_GETTER_NAMES)"))
        val autoMismatches = jsAuto.filter { (name, kind) -> ktAuto[name] != kind && !(ktAuto[name] == null && rhino.evalKt("typeof auto['$name']") == kind) }
            .map { (name, kind) -> "$name: js $kind, kt ${ktAuto[name]}" }
        assertTrue("auto:\n" + autoMismatches.joinToString("\n"), jsAuto.isNotEmpty() && autoMismatches.isEmpty())

        val globals = ScriptModuleExpectations.obsolete("globals") {
            host.global.allIds.filterIsInstance<String>().filterNot { it in before }.joinToString("\n") { it + "\t" + ObsoleteMembers.typeOf(host.global, it) }
        }.lines().associate { it.substringBefore('\t') to it.substringAfter('\t') }
        assertEquals(OBSOLETE_GLOBALS.sorted(), globals.keys.sorted())
        globals.forEach { (name, type) -> assertEquals(name, type, ObsoleteMembers.typeOf(rhino.ktScope.globalThis, name)) }

        val rootAutomator = ScriptModuleExpectations.obsoleteSurface("surface: RootAutomator") { SurfaceDiff.surface(host.load("RootAutomator"), rhino.jsScope.globalThis) }
        val constructorProblems = ObsoleteMembers.problems(rootAutomator, rhino.evalKt("RootAutomator"), rhino.ktScope.globalThis)
        assertTrue("RootAutomator:\n" + constructorProblems.joinToString("\n"), constructorProblems.isEmpty())
        val bound = ScriptModuleExpectations.obsolete("bound: RootAutomator") {
            File(ObsoleteModuleHost.OBSOLETE_DIR, "__RootAutomator__.js").readText()
                .substringAfter("this.__ra__ = ").substringAfter("[").substringBefore("].forEach")
                .split(",").map { it.trim().removeSurrounding("'") }.filter { it.isNotEmpty() }.joinToString(",")
        }.split(",")
        assertEquals(bound.toString(), 17, bound.size)
        val instance = RuntimeModulesFixture.allocate(RootAutomatorNativeObject::class.java)
        RuntimeModulesFixture.setField(instance, RootAutomatorNativeObject::class.java, "scriptRuntime", rhino.ktRuntime)
        RuntimeModulesFixture.setField(
            instance, RootAutomatorNativeObject::class.java, "mRootAutomatorObject",
            RhinoScriptRuntime.toObject(rhino.ktRuntime.topLevelScope, RuntimeModulesFixture.allocate(CoreRootAutomator::class.java)),
        )
        // Read from a script, as on a device: the bound functions take the scope of the running call.
        // zh-CN: 与真机一样从脚本读取: 绑定函数取正在执行的调用的作用域.
        ScriptableObject.putProperty(rhino.ktScope.globalThis, "rootAutomatorOfTest", instance)
        val types = rhino.evalKt("[${bound.joinToString { "'$it'" }}].map(function (name) { return name + ':' + typeof rootAutomatorOfTest[name]; }).join()")
        assertEquals(bound.joinToString(",") { "$it:function" }, types.toString())
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        GLOBALS.filter { it != "\$RootAutomator" }.forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun automatorArguments() = RuntimeModulesFixture.check(rhino, automatorArgumentCases)

    private val automatorArgumentCases
        get() = listOf(
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

    /**
     * Points, point lists, strokes and indexes (appendix C Q65 (2)): what the current module refused, as a TypeError or
     * RangeError. The current module may act on some of them first, so the device runs them with the switch on.
     */
    @Test
    fun pointArguments() = checkFields(pointArgumentCases)

    private val pointArgumentCases
        get() = listOf(
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

    /** The options of the synchronous toolkit and the event waits (appendix C Q65 (2)), as [pointArguments]. */
    @Test
    fun toolkitOptionFields() = checkFields(toolkitOptionFieldCases)

    private val toolkitOptionFieldCases
        get() = listOf(
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

    /**
     * The fields of the options of `auto` and the selector of `auto.explain` (appendix C Q66 (2)), as [pointArguments]: the
     * current parsers refuse them as well, and a case they let through would register a listener or read the windows there.
     */
    @Test
    fun autoOptionFields() = checkFields(autoOptionFieldCases)

    private val autoOptionFieldCases
        get() = listOf(
            "auto.registerEvent('a', function () {}, { distinct: 'x' })" to "throws RangeError",
            "auto.registerEvent('a', function () {}, { distinct: 1 })" to "throws TypeError",
            "auto.registerEvent('a', function () {}, { within: -1 })" to "throws RangeError",
            "auto.registerEvents(['a'], function () {}, { debounce: -5 })" to "throws RangeError",
            "auto.setWindowFilter({ nope: 1 })" to "throws TypeError",
            "auto.setWindowFilter({ type: 'x' })" to "throws RangeError",
            "auto.setWindowFilter({ type: [1] })" to "throws TypeError",
            "auto.findWindows({ packageName: 1 })" to "throws TypeError",
            "auto.findWindows({ title: /a{,2}/ })" to "throws RangeError",
            "auto.findWindows({ active: 1 })" to "throws TypeError",
            "auto.findWindowRoots({ displayId: 1.5 })" to "throws RangeError",
            "auto.findWindowRoots({ layer: 'a' })" to "throws TypeError",
            "auto.dump({ nope: 1 })" to "throws TypeError",
            "auto.dump('x')" to "throws RangeError",
            "auto.dump({ format: 1 })" to "throws TypeError",
            "auto.dump({ maxDepth: NaN })" to "throws RangeError",
            "auto.dump({ properties: ['nope'] })" to "throws RangeError",
            "auto.dump({ properties: [1] })" to "throws TypeError",
            "auto.dump({ visibleOnly: 1 })" to "throws TypeError",
            "auto.dump({ maxNodes: 0 })" to "throws RangeError",
            "auto.dump({ indent: true })" to "throws TypeError",
            "auto.dump({ root: 1 })" to "throws TypeError",
            "auto.explain({ nope: 1 })" to "throws TypeError",
            "auto.explain({ depth: NaN })" to "throws RangeError",
        )

    /** JVM only (they register or read on a device): what the current parsers accept passes the checks of [autoOptionFields]. */
    @Test
    fun acceptedAutoOptions() {
        listOf("({ distinct: 'window', within: '5', debounce: NaN })", "({ distinct: true, within: -0.5 })", "({ distinct: function () {}, nope: 1 })")
            .forEach { AutoOptions.event("auto.registerEvent", rhino.evalKt(it)) }
        listOf(
            "({ type: ['application', 'TYPE_SYSTEM'], packageName: /a/, title: 'b', displayId: 0, id: 1, layer: -1, active: true, focused: null })",
            "({ type: 'input_method', packageName: 'a' + 'b' })",
        ).forEach { AutoOptions.windowFilter("auto.findWindows", "options", rhino.evalKt(it) as NativeObject) }
        listOf(
            "'XML'", "({ format: 'json', maxDepth: 2, properties: ['text', 'desc'], visibleOnly: false, maxNodes: 1, indent: 2 })",
            "({ properties: 'text', indent: '  ', root: null, format: undefined })",
        ).forEach { AutoOptions.dump("auto.dump", rhino.evalKt(it)) }
    }

    /**
     * The event type names: normalized as the current parser normalizes them, without its regular expression, and accepted
     * when the result names an `AccessibilityEvent.TYPE_*` field, as `getField` found it.
     */
    @Test
    fun eventTypeNames() {
        listOf(
            "windowStateChanged", "window_state_changed", "TYPE_WINDOW_STATE_CHANGED", "  viewClicked\t", "typeViewClicked",
            "a1B", "aBC", "aBcD", "ABc", "x9Y", "type_", "TYPE_TYPE_X", "", "_a_B", "a-B", "aZz",
        ).forEach { assertEquals(it, EventWaitArguments.normalizeTypeName(it), ToolkitArgs.normalizeTypeName(it)) }
        listOf("windowStateChanged", "window_state_changed", "TYPE_WINDOW_STATE_CHANGED", " viewClicked ", "typeViewClicked", "notificationStateChanged")
            .forEach { ToolkitArgs.eventArgs("waitForEvent", arrayOf(it)) }
    }

    /**
     * JVM only (they act on a device): what the current module accepts passes the new checks, numeric strings and the
     * lenient point list forms included (appendix C Q65 (2)); without the service the host then fails otherwise.
     */
    @Test
    fun acceptedArguments() = RuntimeModulesFixture.check(rhino, acceptedArgumentCases.map(::passes))

    private val acceptedArgumentCases
        get() = listOf(
            "click('10', '20')", "click([1, 'a'])", "click({ x: '1', y: 2 })", "click(1, 2, '3', 4)",
            "press([[1, 2], 100])", "press(1, '2', 3)",
            "swipe('1', '2', 3, 4, '5')", "swipe(100, [[1, 2], [3]])", "swipe(100, [['1', 2], [3, 4]])", "swipe(100, [1, Infinity], [3, 4])",
            "gesture('100', [1, 2], [3])", "gesture(100, [[[1, 2], [3, 4]]])", "gestures([0, 100, [1, 2], [3, 4, 5]])",
            "scrollDown('1')", "input('1', 'a')",
        )

    /**
     * Issue #275 (`click("文本", 1)` failed: the parameter types of `automator.click` / `longClick` were restricted wrongly).
     * A text with the index of its match is a form of its own, told from a point by its first argument, so the argument
     * checks refuse none of these with a TypeError or RangeError, as [acceptedArguments]. JVM only: on a device they act;
     * the device cases of [exportsDeviceCases] call them when the accessibility service is running, and both modules reach
     * the host alike here ([resultsAgreeWithTheCurrentModules]).
     *
     * zh-CN: issue #275 (`click("文本", 1)` 失效: automator.click / longClick 的参数类型限制有误). 文本加匹配序号是独立的形式, 以第一个参数与坐标区分, 参数检查
     * 不以 TypeError 或 RangeError 拒绝这些调用. 只在 JVM 检查: 真机上会执行动作; exportsDeviceCases 的设备用例在无障碍服务运行时调用它们, 此处两个模块
     * 同样到达宿主 (resultsAgreeWithTheCurrentModules).
     */
    @Test
    fun textWithIndexIsAccepted() = RuntimeModulesFixture.check(rhino, ISSUE_275.map(::passes))

    /** JVM only: under the current module some of these change the mode, start the service or wait for it on a device. */
    @Test
    fun autoArguments() = RuntimeModulesFixture.check(rhino, autoArgumentCases)

    private val autoArgumentCases
        get() = listOf(
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
            "auto.setFlags(['!x'])" to "throws RangeError",
            "auto.setFlags(['noFastPath', '!noFastPath'])" to "throws TypeError",
            "auto.clearFlags()" to "throws TypeError",
            "auto.clearFlags(1)" to "throws TypeError",
            "auto.clearFlags(['x'])" to "throws RangeError",
            "auto.clearFlags('!noFastPath')" to "throws RangeError",
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

    /** `RootAutomator` checks its argument before root or Shizuku is looked for; on a device with Shizuku the current module constructs. */
    @Test
    fun rootAutomatorArguments() = RuntimeModulesFixture.check(rhino, rootAutomatorArgumentCases)

    private val rootAutomatorArgumentCases
        get() = listOf(
            "new RootAutomator('x')" to "throws TypeError",
            "new RootAutomator({})" to "throws TypeError",
            "new RootAutomator(-1)" to "throws RangeError",
            "new RootAutomator(NaN)" to "throws RangeError",
            "new RootAutomator(true, 1)" to "throws TypeError",
        )

    /**
     * Roadmap P3.6, evidence A: every expression of the cases above (argument and field checks, the accepted forms and
     * issue #275) gives the result of the current module, with error names compared loosely ([CurrentModuleResults]); the
     * differences that remain are registered in appendix E (P3.3) and listed in [KNOWN], but for the one in
     * [UNREGISTERED], which this test found. Both sides are built in the test body, which the timeout runs on a thread of
     * its own.
     *
     * zh-CN: P3.6 A 类证据: 上述用例 (参数与字段检查, 接受的形式与 issue #275) 的每个表达式都得到现有模块的结果, 错误名宽松比较 (CurrentModuleResults);
     * 余下的差异登记在附录 E (P3.3) 并列入 KNOWN, 本测试发现的未登记差异列入 UNREGISTERED. 两侧都在测试体内构造, 因为超时设置让测试体在独立线程运行.
     */
    @Test(timeout = 600_000)
    fun resultsAgreeWithTheCurrentModules() {
        val expressions = (automatorArgumentCases + pointArgumentCases + toolkitOptionFieldCases + autoOptionFieldCases + autoArgumentCases + rootAutomatorArgumentCases)
            .map { it.first } + acceptedArgumentCases + ISSUE_275
        val summary = newSide(newLayer = true).use { new ->
            newSide(newLayer = false).use { current -> CurrentModuleResults.assertAgree(new, current, expressions.distinct(), KNOWN) }
        }
        println("P3.6 A automator: $summary")
    }

    /**
     * Cases for the device, in both states of the switch: what does not depend on the accessibility service, root or
     * Shizuku, and the argument errors that the current module also rejects before it acts. The last two are issue #275
     * (`click("文本", 1)`): with the service running the text form looks for a text no window has and returns false; without
     * it they return false before the call, which would open the accessibility settings and wait.
     *
     * zh-CN: 最后两条是 issue #275 (`click("文本", 1)`): 服务运行时以文本形式查找任何窗口都没有的文本, 返回 false; 服务未运行时在调用前返回 false
     * (否则会打开无障碍设置并等待).
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
            ISSUE_275_ON_DEVICE.format("click") to "false",
            ISSUE_275_ON_DEVICE.format("longClick") to "false",
        )
    )

    /**
     * Refusals of the new layer (appendix C Q65 (2)), run on the device with the switch on only: the current module refuses
     * them as well before any action (only the error name differs), and a case it let through would touch the screen there.
     */
    private fun checkFields(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT_FIELDS, cases)
    }

    /** The `name:kind` list of [MEMBER_KINDS] as a map. */
    private fun memberKinds(list: Any?): Map<String, String> = list.toString().split(",").filter { it.isNotEmpty() }
        .associate { it.substringBefore(':') to it.substringAfter(':') }

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

        /** Issue #275: a text with the index of its match (also the forms the current module coerces). */
        val ISSUE_275 = listOf(
            "click('文本', 1)", "longClick('文本', 1)", "click('文本', 0)", "longClick('文本', -1)", "click('文本', '1')",
            "automator.click('文本', 1)", "automator.longClick('文本', 1)",
        )

        /** Issue #275 on a device, `%s` being `click` or `longClick`; the text is on no screen. */
        const val ISSUE_275_ON_DEVICE = "(function () { var t = 'aj6-p36-' + 'issue275-absent'; return automator.isServiceRunning() ? %s(t, 1) : false; })()"

        /** Globals of the old `init.js` the obsolete automator module needs at load time. */
        val OBSOLETE_STUBS = """
            global.__asGlobal__ = function (o, names, scope) {
                names.forEach(function (name) { scope[name] = function () { return o[name].apply(o, arguments); }; });
            };
            global.context = null;
        """.trimIndent()

        /**
         * `name:kind` of the members of an object and its prototypes below `Object.prototype` / `Function.prototype`
         * (`accessor`, or `typeof` of the value), without calling a getter; the names of the second argument are taken as
         * accessors without reading them.
         */
        val MEMBER_KINDS = """
            (function (o, getters) {
                var out = [], seen = Object.create(null);
                for (var p = o; p !== null && p !== Object.prototype && p !== Function.prototype; p = Object.getPrototypeOf(p)) {
                    Object.getOwnPropertyNames(p).forEach(function (k) {
                        if (seen[k] || ['length', 'name', 'arity', 'arguments', 'caller', 'prototype', 'constructor'].indexOf(k) >= 0 || /^__.*__${'$'}/.test(k)) return;
                        seen[k] = true;
                        if (getters.indexOf(k) >= 0) return out.push(k + ':accessor');
                        var d = Object.getOwnPropertyDescriptor(p, k);
                        out.push(k + ':' + (d.get || d.set ? 'accessor' : typeof d.value));
                    });
                }
                return out.join(',');
            })
        """.trimIndent()

        val AUTO_GETTER_NAMES = AUTO_GETTERS.joinToString(", ", "[", "]") { "'$it'" }

        /** The names `__automator__.js` put on the global scope. */
        val OBSOLETE_GLOBALS = listOf(
            "click", "longClick", "press", "swipe", "gesture", "gestures", "gestureAsync", "gesturesAsync", "scrollDown", "scrollUp",
            "input", "setText", "back", "home", "powerDialog", "notifications", "quickSettings", "recents", "splitScreen", "auto",
        )

        /**
         * Differences found by P3.6 that appendix E does not register (reported, the product left as it is):
         * `auto.explain('a', 1)` is refused before the accessibility service is started, while the current module starts the
         * service first (on a device without it, the settings open) and then refuses the root.
         *
         * zh-CN: P3.6 发现, 未在附录 E 登记, 已报告 (产品不改): auto.explain('a', 1) 在启动无障碍服务之前被拒绝, 现有模块先启动服务 (未开服务的真机上会
         * 打开设置页), 再拒绝 root.
         */
        val UNREGISTERED = mapOf(
            "auto.explain('a', 1)" to ("throws TypeError" to "unregistered (P3.6)"),
        )

        /**
         * Differences from the current modules registered in appendix E of docs/augment/ROADMAP.md (P3.3): expression to
         * the new result and the line of the row. On the JVM the current module reaches the host (NullPointerException,
         * UninitializedPropertyAccessException) or fails in its old parser (IndexOutOfBoundsException).
         *
         * zh-CN: 已在 docs/augment/ROADMAP.md 附录 E (P3.3) 登记的与现有模块的差异: 表达式到新层结果与该行行号. JVM 上现有模块到达宿主
         * (NullPointerException, UninitializedPropertyAccessException) 或在旧解析中失败 (IndexOutOfBoundsException).
         */
        val KNOWN = mapOf(
            // docs/augment/ROADMAP.md L1119: an alias and a global function are the module's function object.
            "automator.click === click" to ("true" to "E L1119"),
            "automator.headsetHook === automator.headsethook" to ("true" to "E L1119"),
            // docs/augment/ROADMAP.md L1122: the argument is a timeout handed to the host.
            "automator.waitForService('a')" to ("throws TypeError" to "E L1122"),
            "automator.waitForService(NaN)" to ("throws RangeError" to "E L1122"),
            "auto.waitFor('a')" to ("throws TypeError" to "E L1122"),
            "auto.waitFor(NaN)" to ("throws RangeError" to "E L1122"),
            // docs/augment/ROADMAP.md L1125: a null or undefined text or index.
            "click(null)" to ("throws TypeError" to "E L1125"),
            "longClick(undefined)" to ("throws TypeError" to "E L1125"),
            "scrollUp(null)" to ("throws TypeError" to "E L1125"),
            "input(1, null)" to ("throws TypeError" to "E L1125"),
            "setText(null)" to ("throws TypeError" to "E L1125"),
            // docs/augment/ROADMAP.md L1126: gestures.
            "gesture(-1, [1, 2])" to ("throws RangeError" to "E L1126"),
            "gestures()" to ("throws TypeError" to "E L1126"),
            "gesturesAsync([1], [2], 3)" to ("throws TypeError" to "E L1126"),
            // docs/augment/ROADMAP.md L1137 (Q65 (2)): the elements of point lists and strokes.
            "click([1])" to ("throws TypeError" to "E L1137"),
            "click([])" to ("throws TypeError" to "E L1137"),
            "click({ x: 'a', y: 1 })" to ("throws TypeError" to "E L1137"),
            "press([1])" to ("throws TypeError" to "E L1137"),
            "press(100, [1])" to ("throws TypeError" to "E L1137"),
            "swipe(100, [1, 2])" to ("throws TypeError" to "E L1137"),
            "swipe(100, [[1, 2]])" to ("throws TypeError" to "E L1137"),
            "gestures([100])" to ("throws TypeError" to "E L1137"),
            "gestures([0, 100])" to ("throws TypeError" to "E L1137"),
            "gestures([100, [1]])" to ("throws TypeError" to "E L1137"),
            "gestures([100, [1, 2], 'a'])" to ("throws TypeError" to "E L1137"),
            "gestures(['a', [1, 2]])" to ("throws TypeError" to "E L1137"),
            "gestures([100, [1, 2]], [1, 'a', [1, 2]])" to ("throws TypeError" to "E L1137"),
            // docs/augment/ROADMAP.md L1138 (Q65 (2)): coordinates, indexes and durations out of range.
            "gestures([-1, 100, [1, 2]])" to ("throws RangeError" to "E L1138"),
            "gestures([100, [1, 2147483648]])" to ("throws RangeError" to "E L1138"),
            "automator.waitForService(Infinity)" to ("throws RangeError" to "E L1138"),
            "automator.waitForService(1e20)" to ("throws RangeError" to "E L1138"),
        ) + UNREGISTERED
    }

}
