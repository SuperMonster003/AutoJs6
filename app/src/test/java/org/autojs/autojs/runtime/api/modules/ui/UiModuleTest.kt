package org.autojs.autojs.runtime.api.modules.ui

import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import org.autojs.autojs.runtime.api.UI as ApiUI

/**
 * Roadmap P3.2 (group 3): `ui` on the new script module layer, a proxy module. The runtime's `ui` is allocated without
 * its constructor (its layout inflater registers the inflaters of the Android widgets), so the JVM covers what needs no
 * view: installing binds the runtime's `ui` once, the surface against the current `augment/ui/UI` (the members behind
 * the proxy, `__autojs6Tsx` and `__autojs6TsxFragment` with their names and attributes, R1) and the obsolete `__ui__.js`
 * members, reads and writes through the proxy, `useAndroidLayout`, `registerWidget` and the `Widget` constructor, and
 * the argument errors, which the new layer raises before it calls the current module (T4). The global `isUiThread` is
 * the global module's (decision a). Inflating, posting to the UI thread, colors and the bar heights run on the device
 * in a script without an Activity: inflating inside `ui.run`, no window function called with a valid color.
 *
 * zh-CN: P3.2 (第 3 组): 新层的 ui, 代理模块. 运行时的 ui 不经其构造函数分配 (其布局填充器注册各 Android 控件的填充器), 因此 JVM 覆盖不需要视图的部分: 安装时
 * 绑定一次运行时的 ui, 表面 (对照现有 augment/ui/UI 的代理背后的成员, __autojs6Tsx 与 __autojs6TsxFragment 的名称与特性, R1) 与旧 __ui__.js 的成员,
 * 经代理的读写, useAndroidLayout, registerWidget 与 Widget 构造函数, 以及参数错误, 新层在调用现有模块之前抛出 (T4). 全局 isUiThread 属于 global 模块
 * (决定 a). 填充布局, 投递到 UI 线程, 颜色与栏高度在真机上没有 Activity 的脚本里运行: 在 ui.run 里填充布局, 不以有效颜色调用窗口函数.
 */
class UiModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("ui")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /** Both sides: the runtime's `ui`, which the module (and the current module) binds when installed. */
    private fun prepare(side: DifferentialRhino) {
        HostModuleTestSupport.setField(side.ktRuntime, "ui", newRuntimeUi(side.ktRuntime))
    }

    /** The current module as a plain object with the same members (`augment`), as for console: its proxy needs Android. */
    private fun mountCurrent(side: DifferentialRhino) {
        val runtime = side.ktRuntime
        UI(runtime).augment(side.ktScope.globalThis as ScriptableObject, runtime.ui)
    }

    @Test
    fun installingBindsTheRuntimeUiAndBuildsNothing() {
        listOf("ui", "\$ui").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
        assertSame(rhino.ktScope.globalThis, rhino.ktRuntime.ui.bindingContext)
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        val actual = SurfaceDiff.surface(rhino.evalKt("ui.__augmented__"), rhino.ktScope.globalThis)
        val expected = DifferentialRhino().use { current ->
            prepare(current)
            mountCurrent(current)
            SurfaceDiff.surface(current.evalKt("ui"), current.ktScope.globalThis)
        }
        val report = SurfaceDiff.compare(expected, actual)
        assertTrue(SurfaceDiff.describe(report, "current", "new"), report.namesMatch && report.attributeMismatches.isEmpty())
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
    }

    @Test
    fun globalsMatchTheCurrentModule() {
        val names = GLOBALS.joinToString(", ") { "'$it'" }
        val expression = "[$names].map(function (k) { var d = Object.getOwnPropertyDescriptor(this, k); " +
            "return k + ':' + typeof this[k] + ':' + (d ? [d.writable, d.enumerable, d.configurable].join('/') : 'none'); }, this).join()"
        assertEquals(1, HostModuleTestSupport.assertSameResults(rhino, listOf(expression), emptyMap(), ::prepare, ::mountCurrent))
        // Decision a of P3.2: the current module binds a global isUiThread of its own, the new layer leaves it to the global module.
        HostModuleTestSupport.check(rhino, listOf("typeof isUiThread" to "\"undefined\"", "typeof ui.isUiThread" to "\"function\""))
    }

    @Test
    fun proxiedMembersMatchTheCurrentModule() {
        assertEquals(PROXIED.size, HostModuleTestSupport.assertSameResults(rhino, PROXIED.map { it.first }, emptyMap(), ::prepare, ::mountCurrent))
        HostModuleTestSupport.check(rhino, PROXIED)
        // A write goes to the runtime's ui, which keeps a name it does not hold on the members behind the proxy.
        HostModuleTestSupport.check(rhino, WRITES)
        DEVICE.add(PROXIED + WRITES)
    }

    @Test
    fun useAndroidLayoutSetsTheRuntimeFlag() {
        listOf("ui.useAndroidLayout(false)" to false, "ui.useAndroidLayout(null)" to null, "ui.useAndroidLayout()" to true, "ui.useAndroidLayout(undefined)" to true).forEach { (call, expected) ->
            rhino.evalKt(call)
            assertEquals(call, expected, rhino.ktRuntime.ui.isAndroidLayout)
        }
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val source = File(ObsoleteModuleHost.OBSOLETE_DIR, "__ui__.js").readText()
        val block = source.substringAfter("UI.prototype = {").substringBefore("\n            };")
        // `view` is the runtime's ui property, read through the proxy.
        val members = Regex("^ {16}(?:get )?([A-Za-z_]+)(?:[(]|: )", RegexOption.MULTILINE).findAll(block).map { it.groupValues[1] }.filter { it != "constructor" && it != "view" }.toList()
        assertTrue(members.toString(), members.size >= 20 && "Widget" in members && "__widgets__" in members && "finish" in members)
        HostModuleTestSupport.check(rhino, members.map { "'$it' in ui.__augmented__" to "true" } + listOf("ui.view" to "null"))
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

        private val DEVICE = DeviceCases("UiModuleTest", "new script module layer (P3.2)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        // ui and $ui themselves are left out: the host mounts the current module with `proxying` (writable, enumerable,
        // configurable, as SURFACE checks), while the plain `augment` used here makes them permanent.
        private val GLOBALS = listOf("__autojs6Tsx", "__autojs6TsxFragment")

        /**
         * The runtime's `ui` without its constructor, which needs Android: the slots of a fresh object, `Object.prototype`,
         * the properties map with the layout inflater, an inflater without fields (the binding only sets its delegate) and
         * the widgets object.
         */
        private fun newRuntimeUi(runtime: ScriptRuntime): ApiUI {
            val ui = RuntimeModulesFixture.allocate(ApiUI::class.java)
            val fresh = NativeObject()
            generateSequence<Class<*>>(NativeObject::class.java) { it.superclass }.flatMap { it.declaredFields.asSequence() }.filter { !Modifier.isStatic(it.modifiers) }.forEach { field ->
                field.isAccessible = true
                field.set(ui, field.get(fresh))
            }
            RhinoUtils.initNativeObjectPrototype(runtime.topLevelScope, ui)
            val inflater = RuntimeModulesFixture.allocate(DynamicLayoutInflater::class.java)
            RuntimeModulesFixture.setField(ui, ApiUI::class.java, "scriptRuntime", runtime)
            RuntimeModulesFixture.setField(ui, ApiUI::class.java, "layoutInflater", inflater)
            RuntimeModulesFixture.setField(ui, ApiUI::class.java, "mProperties", ConcurrentHashMap<String, Any>(mapOf("layoutInflater" to inflater)))
            RuntimeModulesFixture.setField(ui, ApiUI::class.java, "widgets\$delegate", lazyOf(RhinoUtils.newNativeObject(runtime.topLevelScope)))
            return ui
        }

        private val SURFACE = listOf(
            "[typeof ui, ui === \$ui, typeof ui.run, typeof ui.__augmented__.run, typeof __autojs6Tsx, typeof __autojs6TsxFragment]" to
                "[\"object\",true,\"function\",\"function\",\"function\",\"object\"]",
            "Object.keys(ui.__augmented__).slice(0, 6).join()" to "\"Widget,__autojs6TsxFragment,run,inflate,useAndroidLayout,isUiThread\"",
            "Object.keys(ui.__augmented__).slice(-4).join()" to "\"statusBarHeight,visibleStatusBarHeight,navigationBarHeight,visibleNavigationBarHeight\"",
            "['__inflate__', '__widgets__'].map(function (k) { return Object.keys(ui.__augmented__).indexOf(k) < 0 && k in ui.__augmented__; }).join()" to "\"true,true\"",
            // Configurable, as the names the current proxy mounts.
            "['ui', '\$ui'].map(function (k) { var d = Object.getOwnPropertyDescriptor(this, k); " +
                "return [d.writable, d.enumerable, d.configurable].join('/'); }, this).join()" to "\"true/true/true,true/true/true\"",
        )

        /** Members that need no view, the same through the current module. */
        private val PROXIED = listOf(
            "typeof ui.Widget" to "\"function\"",
            "Object.keys(new ui.Widget()).join()" to "\"renderInternal,defineAttr,hasAttr,setAttr,getAttr,notifyViewCreated,notifyAfterInflation\"",
            "typeof ui.useAndroidLayout(null)" to "\"undefined\"",
            "(ui.registerWidget('aj6p32', function () {}), typeof ui.__widgets__.aj6p32)" to "\"function\"",
            "ui.view" to "null",
            "typeof ui.layoutInflater" to "\"object\"",
            "ui.bindingContext === this" to "true",
        )

        private val WRITES = listOf(
            "(ui.aj6p32 = 5, [ui.aj6p32, ui.__augmented__.aj6p32].join())" to "\"5,5\"",
        )

        private val ERRORS = listOf(
            "ui.run()" to TYPE,
            "ui.run(1)" to TYPE,
            "ui.run(function () {}, 1)" to TYPE,
            "ui.__inflate__(1, '<frame/>')" to TYPE,
            "ui.inflate()" to TYPE,
            "ui.inflate(1)" to TYPE,
            "ui.inflate(null)" to TYPE,
            "ui.inflate('<frame/>', 1)" to TYPE,
            "ui.inflate('<frame/>', null, 1)" to TYPE,
            "ui.inflate('<frame/>', null, false, 1)" to TYPE,
            "ui.useAndroidLayout(1)" to TYPE,
            "ui.useAndroidLayout('true')" to TYPE,
            "ui.useAndroidLayout(true, 1)" to TYPE,
            "ui.isUiThread(1)" to TYPE,
            "ui.post()" to TYPE,
            "ui.post(1)" to TYPE,
            "ui.post(function () {}, 1.5)" to RANGE,
            "ui.post(function () {}, 'soon')" to TYPE,
            "ui.post(function () {}, 1, 2)" to TYPE,
            "ui.layout()" to TYPE,
            "ui.layout(1)" to TYPE,
            "ui.layout('<frame/>', 1)" to TYPE,
            "ui.layoutFile()" to TYPE,
            "ui.layoutFile(Symbol())" to TYPE,
            "ui.registerWidget()" to TYPE,
            "ui.registerWidget('', function () {})" to RANGE,
            "ui.registerWidget('w', 1)" to TYPE,
            "ui.registerWidget('w', function () {}, 1)" to TYPE,
            "ui.setContentView(1)" to TYPE,
            "ui.setContentView(null)" to TYPE,
            "ui.statusBarColor()" to TYPE,
            "ui.statusBarColor({})" to TYPE,
            "ui.statusBarColor(true)" to TYPE,
            "ui.statusBarColor('#FF0000', 1)" to TYPE,
            "ui.statusBarIconLight(1)" to TYPE,
            "ui.statusBarIconLight('true')" to TYPE,
            "ui.statusBarIconLightBy([])" to TYPE,
            "ui.backgroundColor(null)" to TYPE,
            "ui.navigationBarColor({})" to TYPE,
            "ui.navigationBarIconLight(0)" to TYPE,
            "ui.navigationBarIconLightBy(true)" to TYPE,
            "ui.findById(Symbol())" to TYPE,
            "ui.findById('a', 1)" to TYPE,
            "ui.findByStringId(1, 'id')" to TYPE,
            "ui.findView(Symbol())" to TYPE,
            "ui.finish(1)" to TYPE,
            "ui.keepScreenOn(1)" to TYPE,
            "ui.getStatusBarHeight(1)" to TYPE,
            "ui.getStatusBarHeight([])" to TYPE,
            "ui.getStatusBarHeight({ nope: true })" to RANGE,
            "ui.getStatusBarHeight({ withDimen: 1 })" to TYPE,
            "ui.getVisibleStatusBarHeight({}, 1)" to TYPE,
            "ui.getNavigationBarHeight({ ignoreVisibility: 'yes' })" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "typeof ui.isUiThread()" to "\"boolean\"",
            "[typeof isUiThread, isUiThread !== ui.isUiThread]" to "[\"function\",true]",
            "ui.run(function () { return ui.isUiThread(); })" to "true",
            "ui.run(function () { return 7; })" to "7",
            "ui.post(function () {})" to "true",
            "ui.post(function () {}, 10)" to "true",
            "ui.post(function () {}, '10')" to "true",
            "ui.run(function () { return ui.inflate('<frame/>') !== null; })" to "true",
            "typeof __autojs6Tsx('frame', null)" to "\"object\"",
            "ui.run(function () { return ui.inflate(__autojs6Tsx('frame', null)) !== null; })" to "true",
            // 6.6.2: the views ui.inflate returned had lost the members of the view prototype (attr, on, click).
            "ui.run(function () { var v = ui.inflate('<frame/>'); return [typeof v.attr, typeof v.on, typeof v.once, typeof v.click, typeof v.longClick].join(); })" to
                "\"function,function,function,function,function\"",
            "ui.run(function () { var v = ui.inflate(__autojs6Tsx('frame', null)); return [typeof v.attr, typeof v.on, typeof v.click].join(); })" to
                "\"function,function,function\"",
            // Issue #399: setOnTouchListener on an inflated view; the listener runs for a dispatched event (on('touch') emits later, so the Java listener is set).
            "ui.run(function () { var v = ui.inflate('<frame/>'); var got = []; v.setOnTouchListener(function (view, e) { got.push(e.getAction()); return true; }); " +
                "var e = android.view.MotionEvent.obtain(0, 0, 0, 1, 1, 0); var handled = v.dispatchTouchEvent(e); e.recycle(); " +
                "return [typeof v.setOnTouchListener, handled, got.join()].join(); })" to "\"function,true,0\"",
            // Color names are resolved as the current module resolves them (ColorUtils, Color.parseColor needs Android).
            "ui.statusBarColor('aj6-no-such-color')" to RANGE,
            "ui.backgroundColor('#GG0000')" to RANGE,
            "ui.navigationBarIconLightBy('aj6-no-such-color')" to RANGE,
            "typeof ui.getStatusBarHeight()" to "\"number\"",
            "ui.getStatusBarHeight({ withComputed: false, withDimen: true, ignoreVisibility: true }) >= 0" to "true",
            "ui.getVisibleNavigationBarHeight({ ignoreVisibility: false }) >= 0" to "true",
            "[typeof ui.statusBarHeight, typeof ui.visibleStatusBarHeight, typeof ui.navigationBarHeight, typeof ui.visibleNavigationBarHeight].join()" to "\"number,number,number,number\"",
            "typeof ui.R" to "\"object\"",
            "[ui.root, ui.emitter, ui.findById('aj6p32')].join()" to DeviceCases.INFO,
        )

    }

}
