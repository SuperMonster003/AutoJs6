package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.events.Events
import org.autojs.autojs.runtime.api.augment.events.Keys
import org.autojs.autojs.runtime.api.augment.flow.Flow
import org.autojs.autojs.runtime.api.augment.global.Global
import org.autojs.autojs.runtime.api.augment.global.GlobalClasses
import org.autojs.autojs.runtime.api.modules.CurrentModuleResults
import org.autojs.autojs.runtime.api.modules.ObsoleteMembers
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Roadmap P3.3, first group: `keys`, `events` and `flow` on the new script module layer. The surface (with `global`, whose
 * synchronous `wait` carries `wait.async`) is compared with the current augment calls in the same JVM
 * ([RuntimeModulesFixture]); the argument rules that fail before the flow executor or the host is reached run here, and
 * go with the cases that need them (a flow, an emitter) to `build/script-modules-device/FlowModulesTest.json` for
 * `device_eval.py --new-modules on --strict-errors`. The rules the current parsers still check are rows of appendix E (P3.3).
 *
 * zh-CN: P3.3 第一组: 新层上的 keys, events 与 flow. 表面 (连同 global, 其同步的 wait 上有 wait.async) 在同一 JVM 中与现有 augment 调用比较; 在到达 flow
 * 执行器或宿主之前就失败的参数规则在此检查, 并与需要它们的用例 (Flow 对象, 发射器) 一起写入设备用例. 仍由现有解析器检查的规则记入附录 E (P3.3).
 */
class FlowModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT, cases)
    }

    /**
     * Refusals of the new layer (appendix C Q65 (2)), run on the device with the switch on only: the current parsers refuse
     * them as well before any wait (only the error name differs), and a case they let through would start one there.
     */
    private fun checkFields(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT_FIELDS, cases)
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, PATHS) }
        assertEquals(expected, actual)
    }

    /** Installing builds nothing but the global `wait` (`wait.async` is defined on it at install, D1). */
    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        GLOBALS.filter { it != "wait" }.forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
        assertTrue(LazyDataSlot.isPending(global, "isPrimitive"))
    }

    /**
     * Every member of the obsolete `__events__.js` (P3.6) exists on the new layer with the same type: `events` (its
     * prototype and the runtime's Java events object behind it) and the global `keys` it put on the global scope, the only
     * global it added, are compared by [SurfaceDiff]. Of the obsolete `__globals__.js`, the functions this group took over
     * (`wait`, `waitForActivity`, `waitForPackage`, and `currentPackage` / `currentActivity` their conditions read) are
     * globals of the same type. Neither module needs more than the runtime's events object to load.
     *
     * zh-CN: 旧 __events__.js 的每个成员 (P3.6) 都以相同类型存在于新层: events (其原型及其后的运行时 Java events 对象) 与它放到全局的 keys (它添加的唯一全局)
     * 用 SurfaceDiff 比较. 旧 __globals__.js 中本组接管的函数 (wait, waitForActivity, waitForPackage, 以及它们的条件读取的 currentPackage / currentActivity)
     * 在此为同类型的全局. 两个模块加载时只需要运行时的 events 对象.
     */
    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.setRuntimeProperty("events", rhino.ktRuntime.events)
        val before = host.global.allIds.toSet()
        val events = host.load("events")
        assertEquals(listOf("keys"), host.global.allIds.filterIsInstance<String>().filterNot { it in before })
        listOf(events to "events", ScriptableObject.getProperty(host.global, "keys") to "keys").forEach { (obsolete, name) ->
            val report = SurfaceDiff.compare(SurfaceDiff.surface(obsolete, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt(name), rhino.ktScope.globalThis))
            assertTrue("$name: " + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        }
        host.load("globals")
        OBSOLETE_GLOBAL_FUNCTIONS.forEach { name ->
            assertEquals(name, "function", ObsoleteMembers.typeOf(host.global, name))
            assertEquals(name, ObsoleteMembers.typeOf(host.global, name), ObsoleteMembers.typeOf(rhino.ktScope.globalThis, name))
        }
    }

    @Test
    fun keysAndEvents() = check(keysAndEventsCases)

    private val keysAndEventsCases
        get() = listOf(
            "Object.keys(keys).join()" to "\"home,HOME,menu,MENU,back,BACK,volumeUp,volume_up,VOLUME_UP,volumeDown,volume_down,VOLUME_DOWN\"",
            "[keys.home, keys.MENU, keys.back, keys.volume_up, keys.VOLUME_DOWN]" to "[3,82,4,24,25]",
            "\$keys === keys" to "true",
            "Object.getOwnPropertyDescriptor(events, '__asEmitter__').enumerable" to "false",
            "Object.getOwnPropertyDescriptor(events, '__asEmitter__').writable" to "false",
            "events.__asEmitter__(1)" to "throws TypeError",
            "events.__asEmitter__('x')" to "throws TypeError",
            "events.__asEmitter__({}, 1)" to "throws TypeError",
            "events.__asEmitter__({}, {})" to "throws TypeError",
            "events.__asEmitter__({}, null, 1)" to "throws TypeError",
        )

    @Test
    fun waitArguments() = check(waitArgumentCases)

    private val waitArgumentCases
        get() = listOf(
            "typeof wait.async" to "\"function\"",
            "Object.getOwnPropertyDescriptor(wait, 'async').enumerable" to "false",
            "flow.waitAsync === flow.wait" to "true",
            "waitAsync === flow.wait" to "false",
            "clickWait === flow.clickWait" to "false",
            "clickWait.name" to "\"waitThenClick\"",
            "flow()" to "throws TypeError",
            "flow.wait()" to "throws TypeError",
            "flow.wait(null)" to "throws TypeError",
            "waitAsync()" to "throws TypeError",
            "wait.async()" to "throws TypeError",
            "wait.async(undefined, 1)" to "throws TypeError",
            "flow.wait(function () {}, 'x')" to "throws TypeError",
            "flow.wait(function () {}, NaN)" to "throws RangeError",
            "flow.wait(function () {}, -1)" to "throws RangeError",
            "flow.wait(function () {}, 1, Infinity)" to "throws RangeError",
            "flow.wait(function () {}, 1, -1)" to "throws RangeError",
            "flow.wait(function () {}, 1, 2, 3)" to "throws TypeError",
            "flow.wait(function () {}, {}, {})" to "throws TypeError",
            "flow.wait(function () {}, [])" to "throws TypeError",
            "flow.wait(function () {}, function () {}, function () {}, function () {})" to "throws TypeError",
            "flow.waitUntil()" to "throws TypeError",
            "flow.waitWhile(function () {}, true)" to "throws TypeError",
            "flow.whenPresent('a')" to "throws TypeError",
            "flow.whenPresent('a', 1)" to "throws TypeError",
            "flow.whenPresent('a', function () {}, 1)" to "throws TypeError",
            "flow.whenPresent('a', function () {}, {}, 1)" to "throws TypeError",
            "flow.repeatUntil(1, 'a')" to "throws TypeError",
            "flow.repeatUntil(function () {})" to "throws TypeError",
            "flow.waitForActivity()" to "throws TypeError",
            "flow.waitForActivity(1)" to "throws TypeError",
            "flow.waitForPackage('a', -1)" to "throws RangeError",
            "flow.waitForPackage('a', 1, 2, 3)" to "throws TypeError",
            "waitForStable()" to "throws TypeError",
            "waitForVisible('a', NaN)" to "throws RangeError",
            "waitForGone(null)" to "throws TypeError",
            "waitThenClick()" to "throws TypeError",
            "clickBoundsWait('a', 'b')" to "throws TypeError",
            "flow.waitThenLongClick('a', 1, 2, 3)" to "throws TypeError",
            "clickWhenStable('a', -1)" to "throws RangeError",
            "clickBoundsWhenStable()" to "throws TypeError",
            "clickWhenStableAfter('a', 1, 2, -1)" to "throws RangeError",
            "clickBoundsWhenStableAfter('a', 1, 2, 3, NaN)" to "throws RangeError",
            "clickBoundsWhenStableAfter('a', 1, 2, 3, 4, 5)" to "throws TypeError",
        )

    /** JVM only: on a device the current module starts waiting for the target before it rejects these arguments. */
    @Test
    fun waitArgumentsAfterTheWaitStarts() = RuntimeModulesFixture.check(rhino, waitStartedArgumentCases)

    private val waitStartedArgumentCases
        get() = listOf(
            "clickWhenStableAfter('a')" to "throws TypeError",
            "clickWhenStableAfter('a', 1, 2)" to "throws TypeError",
            "clickWhenStableAfter('a', { timeout: 1 })" to "throws TypeError",
        )

    /**
     * The fields of the options of the waits, `whenPresent`, `repeatUntil` and `defaults`, and the kinds of conditions
     * (appendix C Q65 (2)): what the current parser refused, as a TypeError or RangeError before it runs.
     */
    @Test
    fun optionFields() = checkFields(optionFieldCases)

    private val optionFieldCases
        get() = listOf(
            "flow.wait('a', { timeout: -1 })" to "throws RangeError",
            "flow.wait('a', { timeout: 'x' })" to "throws TypeError",
            "flow.wait('a', { interval: Infinity })" to "throws RangeError",
            "flow.wait('a', { times: NaN })" to "throws RangeError",
            "flow.wait('a', { stableFor: -1 })" to "throws RangeError",
            "flow.wait('a', { snapshot: 1 })" to "throws TypeError",
            "flow.wait('a', { snapshot: function () {}, compare: 'bounds' })" to "throws TypeError",
            "flow.wait('a', { compare: 'nope' })" to "throws RangeError",
            "flow.wait('a', { compare: 1 })" to "throws TypeError",
            "flow.wait('a', { missing: 'skip' })" to "throws RangeError",
            "flow.wait('a', { delay: [] })" to "throws TypeError",
            "flow.wait('a', { delay: [1, 2, 3] })" to "throws TypeError",
            "flow.wait('a', { delay: -1 })" to "throws RangeError",
            "flow.wait('a', { delay: [1, 'x'] })" to "throws RangeError",
            "flow.wait('a', { delay: [1, {}] })" to "throws TypeError",
            "flow.wait('a', { root: 1 })" to "throws TypeError",
            "flow.wait('a', { compass: 1 })" to "throws TypeError",
            "flow.wait('a', { resultType: 1 })" to "throws TypeError",
            "clickWhenStableAfter('a', 1, 2, { delay: NaN })" to "throws RangeError",
            "flow.wait(true)" to "throws TypeError",
            "flow.whenPresent(true, function () {})" to "throws TypeError",
            "flow.whenPresent('a', function () {}, { nope: 1 })" to "throws TypeError",
            "flow.whenPresent('a', function () {}, { times: -1 })" to "throws RangeError",
            "flow.repeatUntil(function () {}, true)" to "throws TypeError",
            "flow.repeatUntil(function () {}, 'a', { timeout: Infinity })" to "throws RangeError",
            "flow.repeatUntil(function () {}, 'a', { maxAttempts: 1.5 })" to "throws RangeError",
            "flow.repeatUntil(function () {}, 'a', { retryOn: 1 })" to "throws TypeError",
            "flow.defaults({ nope: 1 })" to "throws TypeError",
            "flow.defaults({ timeout: -1 })" to "throws RangeError",
            "flow.defaults({ stableFor: 'x' })" to "throws TypeError",
            "flow.defaults({ humanize: 1 })" to "throws TypeError",
            "flow.defaults({ humanize: { nope: 1 } })" to "throws TypeError",
            "flow.defaults({ humanize: { offset: [1] } })" to "throws TypeError",
            "flow.defaults({ humanize: { offset: -1 } })" to "throws RangeError",
            "flow.defaults({ humanize: { delay: [5, 1] } })" to "throws RangeError",
        )

    /** The fields of the toolkit options, the targets, and the filters and types of the event waits (appendix C Q65 (2)). */
    @Test
    fun toolkitOptionFields() = checkFields(toolkitOptionFieldCases)

    private val toolkitOptionFieldCases
        get() = listOf(
            "flow.smartClick('aj6-none', { nope: 1 })" to "throws TypeError",
            "flow.smartClick('aj6-none', { timeout: NaN })" to "throws RangeError",
            "flow.smartClick('aj6-none', { offset: [1, 2, 3] })" to "throws TypeError",
            "flow.smartClick('aj6-none', { offset: 2147483648 })" to "throws RangeError",
            "flow.smartClick('aj6-none', { maxClimb: -1 })" to "throws RangeError",
            "flow.smartClick('aj6-none', { verify: true })" to "throws TypeError",
            "flow.smartClick('aj6-none', { verifyTimeout: -1 })" to "throws RangeError",
            "flow.smartClick('aj6-none', { root: {} })" to "throws TypeError",
            "flow.smartClick('aj6-none', { humanize: { delay: 'x' } })" to "throws TypeError",
            "flow.smartClick(true)" to "throws TypeError",
            "flow.smartClick('aj6-none', 1)" to "throws TypeError",
            "flow.smartClickBounds('aj6-none', { maxClimb: 1 })" to "throws TypeError",
            "flow.clickIfExists('aj6-none', -1)" to "throws RangeError",
            "flow.clickAny([])" to "throws TypeError",
            "flow.clickAny(['aj6-none', null])" to "throws TypeError",
            "flow.clickAny(['aj6-none', true])" to "throws TypeError",
            "flow.findAny(['aj6-none'], { verify: 'a' })" to "throws TypeError",
            "flow.scrollUntil('aj6-none', { direction: 'x' })" to "throws RangeError",
            "flow.scrollUntil('aj6-none', { method: 1 })" to "throws TypeError",
            "flow.scrollUntil('aj6-none', { maxSteps: -1 })" to "throws RangeError",
            "flow.scrollUntil('aj6-none', { container: true })" to "throws TypeError",
            "flow.typeInto('aj6-none', 'b', { verifyTimeout: 'x' })" to "throws TypeError",
            "flow.dismissPopups(['aj6-none'], { watch: true })" to "throws TypeError",
            "flow.dismissPopups(['aj6-none'], { onDismissed: function () {} })" to "throws TypeError",
            "flow.dismissPopups(['aj6-none'], { maxRounds: 0 })" to "throws RangeError",
            "flow.dismissPopups(['aj6-none'], { click: 1 })" to "throws TypeError",
            "flow.dismissPopups(['aj6-none'], { click: { nope: 1 } })" to "throws TypeError",
            "flow.collectList(null, 'aj6-none', { maxItems: 0 })" to "throws RangeError",
            "flow.collectList(null, 'aj6-none', { resultType: 1 })" to "throws TypeError",
            "flow.launchAndWait('aj6.none', { timeout: -1 })" to "throws RangeError",
            "flow.backUntil('aj6-none', { interval: 0 })" to "throws RangeError",
            "flow.backUntil('aj6-none', { maxTimes: 1.5 })" to "throws RangeError",
            "flow.toggle('aj6-none', true, { click: { verify: true } })" to "throws TypeError",
            "flow.waitForIdle({ nope: 1 })" to "throws TypeError",
            "flow.waitForIdle({ quietFor: -1 })" to "throws RangeError",
            "flow.waitForIdle({ eventTypes: ['nope'] })" to "throws RangeError",
            "flow.waitForIdle({ eventTypes: [1] })" to "throws TypeError",
            "flow.waitForEvent('nope')" to "throws RangeError",
            "flow.waitForEvent(['click', 1])" to "throws TypeError",
            "flow.waitForEvent('click', { nope: 1 })" to "throws TypeError",
            "flow.waitForEvent('click', { text: 1 })" to "throws TypeError",
            "flow.waitForToast({ title: 'a' })" to "throws TypeError",
            "flow.waitForNotification({ title: 1 })" to "throws TypeError",
        )

    /**
     * The selector forms of conditions and targets and the compass and result type of their scope (appendix C Q66 (2), Q67):
     * what `pickup` refuses, now before the wait or the tool instead of while it polls or once a node is found.
     */
    @Test
    fun selectorForms() = checkFields(selectorFormCases)

    private val selectorFormCases
        get() = listOf(
            "flow.wait({ nope: 1 })" to "throws TypeError",
            "flow.wait(['aj6-none', { nope: 1 }])" to "throws TypeError",
            "flow.wait({ depth: NaN })" to "throws RangeError",
            "flow.wait('aj6-none', { compass: 'c' })" to "throws RangeError",
            "flow.wait('aj6-none', { compass: 'x2' })" to "throws RangeError",
            "flow.wait('aj6-none', { resultType: 'nope' })" to "throws RangeError",
            "flow.wait('aj6-none', { compass: 'p', resultType: '@' })" to "throws TypeError",
            "flow.whenPresent({ nope: 1 }, function () {})" to "throws TypeError",
            "flow.repeatUntil(function () {}, 'aj6-none', { compass: 'c' })" to "throws RangeError",
            "flow.smartClick({ nope: 1 })" to "throws TypeError",
            "flow.smartClick('aj6-none', { verify: { nope: 1 } })" to "throws TypeError",
            "flow.clickAny(['aj6-none', { textMatches: '[' }])" to "throws RangeError",
            "flow.collectList(null, 'aj6-none', { resultType: 'widgets' })" to "throws RangeError",
            "flow.collectList(null, 'aj6-none', { resultType: ['click', 1] })" to "throws RangeError",
        )

    @Test
    fun flowArguments() = check(flowArgumentCases)

    private val flowArgumentCases
        get() = listOf(
            "flow.sleep()" to "throws TypeError",
            "flow.sleep(-1)" to "throws RangeError",
            "flow.delay(NaN)" to "throws RangeError",
            "flow.sleep(Infinity)" to "throws RangeError",
            "flow.sleep('x')" to "throws TypeError",
            "flow.sleep(1, {})" to "throws TypeError",
            "flow.sleep(1, 'abc')" to "throws RangeError",
            "flow.sleep(1, NaN)" to "throws RangeError",
            "flow.sleep(1, 2, 3)" to "throws TypeError",
            "flow.run()" to "throws TypeError",
            "flow.run(1)" to "throws TypeError",
            "flow.run(function () {}, function () {})" to "throws TypeError",
            "flow.of(1, 2)" to "throws TypeError",
            "flow.all(null)" to "throws TypeError",
            "flow.race([1, null])" to "throws TypeError",
            "flow.any(1, undefined, 2)" to "throws TypeError",
            "flow.cancelAll(1)" to "throws TypeError",
            "flow.defaults(1)" to "throws TypeError",
            "flow.defaults([])" to "throws TypeError",
            "flow.defaults({}, 1)" to "throws TypeError",
            "flow.trace(true, 1)" to "throws TypeError",
        )

    @Test
    fun toolkitRootArguments() = check(toolkitRootArgumentCases)

    private val toolkitRootArgumentCases
        get() = listOf(
            "flow.smartClick()" to "throws TypeError",
            "flow.smartClick(null)" to "throws TypeError",
            "flow.smartClick('a', NaN)" to "throws RangeError",
            "flow.smartClick('a', [])" to "throws TypeError",
            "flow.smartClick('a', 'b')" to "throws TypeError",
            "flow.smartClick('a', {}, 1)" to "throws TypeError",
            "flow.smartClick('a', {}, function () {}, function () {}, function () {})" to "throws TypeError",
            "flow.clickAny()" to "throws TypeError",
            "flow.findAny(undefined, {})" to "throws TypeError",
            "flow.typeInto('a')" to "throws TypeError",
            "flow.typeInto(null, null)" to "throws TypeError",
            "flow.collectList('a')" to "throws TypeError",
            "flow.toggle('a')" to "throws TypeError",
            "flow.launchAndWait()" to "throws TypeError",
            "flow.backUntil(null, {})" to "throws TypeError",
            "flow.backToApp('a', true)" to "throws TypeError",
            "flow.scrollUntil('a', {}, 'b')" to "throws TypeError",
            "flow.dismissPopups()" to "throws TypeError",
            "flow.waitForIdle(1, 2)" to "throws TypeError",
            "flow.waitForIdle(-1)" to "throws RangeError",
            "flow.waitForIdle('x')" to "throws TypeError",
            "flow.waitForIdle([])" to "throws TypeError",
            "flow.waitForEvent(1)" to "throws TypeError",
            "flow.waitForEvent('a', -1)" to "throws RangeError",
            "flow.waitForEvent('a', 1, 2)" to "throws TypeError",
            "flow.waitForEvent('a', {}, function () {})" to "throws TypeError",
            "flow.waitForEvent('a', 1, {}, 2)" to "throws TypeError",
            "flow.waitForToast(1, 2)" to "throws TypeError",
            "flow.waitForToast(-1)" to "throws RangeError",
            "flow.waitForToast([], 1)" to "throws TypeError",
            "flow.waitForNotification('a', NaN)" to "throws RangeError",
            "flow.waitForNotification('a', 'b')" to "throws TypeError",
        )

    /**
     * Roadmap P3.6, evidence A: every expression of the cases above gives the result of the current modules, with error
     * names compared loosely ([CurrentModuleResults]); the differences that remain are registered in appendix E (P3.3) and
     * listed in [KNOWN]. Both sides are built in the test body, which the timeout runs on a thread of its own.
     *
     * zh-CN: P3.6 A 类证据: 上述用例的每个表达式都得到现有模块的结果, 错误名宽松比较 (CurrentModuleResults); 余下的差异登记在附录 E (P3.3) 并列入 KNOWN.
     * 两侧都在测试体内构造, 因为超时设置让测试体在独立线程运行.
     */
    @Test(timeout = 600_000)
    fun resultsAgreeWithTheCurrentModules() {
        val expressions = (keysAndEventsCases + waitArgumentCases + waitStartedArgumentCases + optionFieldCases + toolkitOptionFieldCases +
            selectorFormCases + flowArgumentCases + toolkitRootArgumentCases).map { it.first }
        val summary = newSide(newLayer = true).use { new ->
            newSide(newLayer = false).use { current -> CurrentModuleResults.assertAgree(new, current, expressions.distinct(), KNOWN) }
        }
        println("P3.6 A flow: $summary")
    }

    /** Cases that reach the flow executor or the host: exported for the device only. */
    @Test
    fun exportsDeviceCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT, listOf(
            "typeof flow.Flow" to "\"function\"",
            "flow.of(1) instanceof flow.Flow" to "true",
            "flow(function () { return true; }) instanceof flow.Flow" to "true",
            "wait.async(function () { return true; }) instanceof flow.Flow" to "true",
            "[typeof flow.pending, typeof flow.workers]" to "[\"number\",\"number\"]",
            "typeof flow.cancelAll()" to "\"number\"",
            "typeof flow.trace()" to "\"boolean\"",
            "typeof flow.defaults().timeout" to "\"number\"",
            "flow.wait(function () { return true; }, { timeout: '5', compare: 'bounds', missing: 'reset', delay: [0, '1'] }) instanceof flow.Flow" to "true",
            "flow.sleep(1, 2) instanceof flow.Flow" to "true",
            "flow.all(flow.of(1), flow.of(2)) instanceof flow.Flow" to "true",
            "flow.waitForIdle({ quietFor: 1, timeout: 1 }) instanceof flow.Flow" to "true",
            "Object.keys(events.__asEmitter__({})).indexOf('emit') >= 0" to "true",
            "(function () { var o = events.__asEmitter__({ emit: 1 }); return o.emit; })()" to "1",
            "typeof events.__asEmitter__(null, threads.currentThread()).on" to "\"function\"",
            "typeof events.emitter" to "\"function\"",
        )
    )

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        val runtime = side.ktRuntime
        RuntimeModulesFixture.prepareHost(runtime)
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "scale", Scale(runtime))
        // `flow.Flow`, `flow.pending` and `flow.workers` read the runtime's flow executor, which is kept in its properties and closed with it.
        // zh-CN: flow.Flow, flow.pending 与 flow.workers 读取运行时的 Flow 执行器, 它存于运行时属性中并随运行时关闭.
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "mProperties", ConcurrentHashMap<String, Any>())
        RuntimeModulesFixture.setField(runtime, ScriptRuntime::class.java, "closeableManager", RuntimeResourceScope("flow-modules-test"))
        RuntimeModulesFixture.mount(side, MODULES, newLayer) { name, global, rt ->
            when (name) {
                "global" -> Global(rt).assignWithRuntime(global, rt, GlobalClasses)
                "flow" -> Flow(rt).augment(global)
                "events" -> Events(rt).augment(global, rt.events)
                "keys" -> Keys.augmentWithRuntime(global, rt)
                else -> throw AssertionError(name)
            }
        }
    }

    private companion object {
        const val EXPORT = "FlowModulesTest"
        const val EXPORT_FIELDS = "FlowOptionFieldsTest"
        val MODULES = listOf("global", "flow", "events", "keys")
        val PATHS = listOf("flow", "events", "keys")
        val GLOBALS = listOf(
            "wait", "waitAsync", "waitThenClick", "clickWait", "waitThenClickBounds", "clickBoundsWait", "waitForStable",
            "waitForStableThenClick", "clickWhenStable", "waitForStableThenClickBounds", "clickBoundsWhenStable", "waitForVisible",
            "waitForHidden", "waitForGone", "clickWhenStableAfter", "clickBoundsWhenStableAfter",
            "flow", "\$flow", "events", "\$events", "keys", "\$keys",
        )

        /** The functions of the obsolete `__globals__.js` this group took over. */
        val OBSOLETE_GLOBAL_FUNCTIONS = listOf("wait", "waitForActivity", "waitForPackage", "currentPackage", "currentActivity")

        /**
         * Differences from the current modules registered in appendix E of docs/augment/ROADMAP.md (P3.3): expression to
         * the new result and the line of the row. In the `flow.wait` cases the current module starts waiting first (the
         * fixture has no accessibility bridge, NullPointerException).
         *
         * zh-CN: 已在 docs/augment/ROADMAP.md 附录 E (P3.3) 登记的与现有模块的差异: 表达式到新层结果与该行行号. flow.wait 各条中现有模块先开始等待
         * (夹具没有无障碍桥, NullPointerException).
         */
        val KNOWN = mapOf(
            // docs/augment/ROADMAP.md L1119: an alias is the same function object.
            "flow.waitAsync === flow.wait" to ("true" to "E L1119"),
            // docs/augment/ROADMAP.md L1128: an uncatchable IllegalArgumentException of `RhinoUtils.callable`.
            "wait.async()" to ("throws TypeError" to "E L1128"),
            "wait.async(undefined, 1)" to ("throws TypeError" to "E L1128"),
            // docs/augment/ROADMAP.md L1142 (Q66 (2)): conditions inside a selector form.
            "flow.wait({ nope: 1 })" to ("throws TypeError" to "E L1142"),
            "flow.wait(['aj6-none', { nope: 1 }])" to ("throws TypeError" to "E L1142"),
            // docs/augment/ROADMAP.md L1143 (Q66 (2)): values a condition refuses.
            "flow.wait({ depth: NaN })" to ("throws RangeError" to "E L1143"),
            // docs/augment/ROADMAP.md L1146 (Q67): the syntax of a compass.
            "flow.wait('aj6-none', { compass: 'c' })" to ("throws RangeError" to "E L1146"),
            "flow.wait('aj6-none', { compass: 'x2' })" to ("throws RangeError" to "E L1146"),
            // docs/augment/ROADMAP.md L1144 (Q66 (2)): result types.
            "flow.wait('aj6-none', { resultType: 'nope' })" to ("throws RangeError" to "E L1144"),
            // docs/augment/ROADMAP.md L1145 (Q66 (2)): a selector result type with a compass.
            "flow.wait('aj6-none', { compass: 'p', resultType: '@' })" to ("throws TypeError" to "E L1145"),
        )
    }

}
