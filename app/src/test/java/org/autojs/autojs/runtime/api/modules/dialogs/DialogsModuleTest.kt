package org.autojs.autojs.runtime.api.modules.dialogs

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.2 (group 2): `dialogs` on the new script module layer. Building and showing a dialog needs Android, so the
 * JVM covers the surface against the current `augment/dialogs/Dialogs` and the obsolete `__dialogs__.js` (members and
 * global functions), and the argument errors, which the new layer raises before it calls the current module (T4; the
 * current module coerced most values and threw IllegalArgumentException for the rest). The device builds dialogs
 * without showing them; the one shown (with the overlay permission) is dismissed at once.
 *
 * zh-CN: P3.2 (第 2 组): 新层的 dialogs. 构建与显示对话框需要 Android, 因此 JVM 覆盖表面 (对照现有 augment/dialogs/Dialogs 与旧 __dialogs__.js 的成员与全局
 * 函数) 与参数错误, 新层在调用现有模块之前抛出 (T4; 现有模块多数值强制转换, 其余抛 IllegalArgumentException). 真机只构建对话框而不显示; 唯一显示的一个
 * (有悬浮窗权限时) 随即关闭.
 */
class DialogsModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("dialogs")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun mountCurrent(side: DifferentialRhino) {
        Dialogs(side.ktRuntime).augment(side.ktScope.globalThis as ScriptableObject)
    }

    @Test
    fun installingBuildsNothing() {
        listOf("dialogs", "\$dialogs").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "dialogs", mountCurrent = ::mountCurrent)
        val names = GLOBALS.joinToString(", ") { "'$it'" }
        val globals = "[$names].map(function (k) { var d = Object.getOwnPropertyDescriptor(this, k); " +
            "return k + ':' + typeof this[k] + ':' + (d ? [d.writable, d.enumerable, d.configurable].join('/') : 'none'); }, this).join()"
        assertEquals(1, HostModuleTestSupport.assertSameResults(rhino, listOf(globals), emptyMap(), mountCurrent = ::mountCurrent))
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val obsolete = ScriptModuleExpectations.obsoleteSurface("surface: dialogs") {
            val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
            // The obsolete module reads the preset texts from the global `R` and assigns its globals with `__asGlobal__` when it loads.
            rhino.evalJs("var R = { strings: {} }; var __asGlobal__ = function () {};")
            SurfaceDiff.surface(host.load("dialogs"), rhino.jsScope.globalThis)
        }
        val report = SurfaceDiff.compare(obsolete, SurfaceDiff.surface(rhino.evalKt("dialogs"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        HostModuleTestSupport.check(rhino, listOf("rawInput", "alert", "confirm", "prompt").map { "typeof $it" to "\"function\"" })
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun multiChoiceResultsBecomeJsArrays() {
        // Issues #322 / #518: the indices multiChoice returned reached the script as a Java array. Showing the dialog needs Android,
        // so the conversion of the current module that the new layer calls (`Dialogs.toJsArrayIfNeeded`) is given what the dialog
        // returns: an int[] (empty when dismissed), the same wrapped as a callback receives it, or a list.
        val convert = Dialogs.Companion::class.java.getDeclaredMethod("toJsArrayIfNeeded", Any::class.java).apply { isAccessible = true }
        val results = mapOf<String, () -> Any?>(
            "int[]" to { intArrayOf(0, 2) },
            "empty" to { intArrayOf() },
            "wrapped" to { Context.javaToJS(intArrayOf(1), rhino.ktScope) },
            "list" to { arrayListOf(0, 2) },
            "null" to { null },
        )
        val function = RhinoUtils.newBaseFunction(rhino.ktScope, RESULT, { args ->
            convert.invoke(Dialogs.Companion, results.getValue(args[0].toString()).invoke())
        }, RhinoUtils.NOT_CONSTRUCTABLE)
        ScriptableObject.putProperty(rhino.ktScope.globalThis, RESULT, function)
        HostModuleTestSupport.check(rhino, MULTI_CHOICE_RESULTS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("DialogsModuleTest", "new script module layer (P3.2)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val GLOBALS = listOf("rawInput", "prompt", "alert", "confirm", "input", "select")

        /** The global through which the JVM case reads a converted multiChoice result. */
        private const val RESULT = "__multiChoiceResult__"

        private val MULTI_CHOICE_RESULTS = listOf(
            "(function () { var r = $RESULT('int[]'); return [Array.isArray(r), JSON.stringify(r), r[1] + 1]; })()" to "[true,\"[0,2]\",3]",
            "(function () { var r = $RESULT('empty'); return [Array.isArray(r), r.length]; })()" to "[true,0]",
            "(function () { var r = $RESULT('wrapped'); return [Array.isArray(r), JSON.stringify(r)]; })()" to "[true,\"[1]\"]",
            "(function () { var r = $RESULT('list'); return [Array.isArray(r), JSON.stringify(r), r.indexOf(2)]; })()" to "[true,\"[0,2]\",1]",
            "$RESULT('null')" to "null",
        )

        private val SURFACE = listOf(
            // On a device the global input and select of other modules exist; neither is the dialogs function.
            "[typeof dialogs, dialogs === \$dialogs, typeof rawInput, typeof prompt, typeof alert, typeof confirm, " +
                "typeof input === 'undefined' || input !== dialogs.input, typeof select === 'undefined' || select !== dialogs.select]" to
                "[\"object\",true,\"function\",\"function\",\"function\",\"function\",true,true]",
            "Object.keys(dialogs).join()" to "\"build,rawInput,input,prompt,alert,confirm,select,singleChoice,multiChoice\"",
        )

        private val ERRORS = listOf(
            "dialogs.build(1)" to TYPE,
            "dialogs.build([])" to TYPE,
            "dialogs.build('t')" to TYPE,
            "dialogs.build({}, 1)" to TYPE,
            "dialogs.build({ nope: 1 })" to RANGE,
            "dialogs.build({ inputSingleLine: 1 })" to TYPE,
            "dialogs.build({ stubborn: 'true' })" to TYPE,
            "dialogs.build({ inputHint: Symbol() })" to TYPE,
            "dialogs.build({ itemsSelectMode: 'x' })" to RANGE,
            "dialogs.build({ itemsSelectMode: 1 })" to TYPE,
            "dialogs.build({ itemsSelectedIndex: 1.5 })" to RANGE,
            "dialogs.build({ itemsSelectedIndices: [0, 0.5] })" to RANGE,
            "dialogs.build({ itemsSelectedIndex: {} })" to TYPE,
            "dialogs.build({ progress: 1 })" to TYPE,
            "dialogs.build({ progress: { max: 1.5 } })" to RANGE,
            "dialogs.build({ progress: { horizontal: 1 } })" to TYPE,
            "dialogs.build({ theme: 'blue' })" to RANGE,
            "dialogs.build({ theme: 1 })" to TYPE,
            "dialogs.build({ customView: 1 })" to TYPE,
            "dialogs.build({ linkify: 1 })" to TYPE,
            "dialogs.build({ animation: {} })" to TYPE,
            "dialogs.build({ dimAmount: NaN })" to RANGE,
            "dialogs.build({ dimAmount: {} })" to TYPE,
            "dialogs.build({ onBackKey: 'enabled' })" to RANGE,
            "dialogs.build({ onBackPressed: 1 })" to TYPE,
            "dialogs.rawInput()" to TYPE,
            "dialogs.rawInput(Symbol())" to TYPE,
            "dialogs.rawInput('t', Symbol())" to TYPE,
            "dialogs.rawInput('t', 'p', 1)" to TYPE,
            "dialogs.rawInput('t', function () {}, function () {})" to TYPE,
            "dialogs.rawInput('t', 'p', function () {}, 1)" to TYPE,
            "dialogs.input('t', 'p', 'cb')" to TYPE,
            "dialogs.prompt('t', 'p', {})" to TYPE,
            "dialogs.alert()" to TYPE,
            "dialogs.alert('t', 'c', 1)" to TYPE,
            "dialogs.confirm('t', 'c', [])" to TYPE,
            "dialogs.confirm('t', function () {}, function () {})" to TYPE,
            "dialogs.select('t', function () {})" to TYPE,
            "dialogs.select('t', 'a', function () {})" to TYPE,
            "dialogs.select('t', 'a', Symbol())" to TYPE,
            "dialogs.select('t', [Symbol()])" to TYPE,
            "dialogs.select('t', ['a'], 1)" to TYPE,
            "dialogs.select('t', ['a'], function () {}, 1)" to TYPE,
            "dialogs.singleChoice('t')" to TYPE,
            "dialogs.singleChoice('t', 'a')" to TYPE,
            "dialogs.singleChoice('t', ['a'], 1.5)" to RANGE,
            "dialogs.singleChoice('t', ['a'], {})" to TYPE,
            "dialogs.singleChoice('t', ['a'], 0, 1)" to TYPE,
            "dialogs.singleChoice('t', ['a'], 0, function () {}, 1)" to TYPE,
            "dialogs.multiChoice('t', null)" to TYPE,
            "dialogs.multiChoice('t', ['a'], 1)" to TYPE,
            "dialogs.multiChoice('t', ['a'], [1.5])" to RANGE,
            "dialogs.multiChoice('t', ['a'], [{}])" to TYPE,
            "dialogs.multiChoice('t', ['a'], [0], 'cb')" to TYPE,
            "rawInput()" to TYPE,
            "prompt('t', 'p', 1)" to TYPE,
            "alert()" to TYPE,
            "confirm('t', 'c', 1)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            // Words are matched as the current module does (StringUtils.looseMatches, whose class needs Android to load).
            "dialogs.build({ linkify: 'nope' })" to RANGE,
            "dialogs.build({ animation: 'spin' })" to RANGE,
            "typeof dialogs.build({ title: 'aj6-p32', content: 'c' }).dismiss" to "\"function\"",
            "dialogs.build() !== null" to "true",
            "dialogs.build(null) !== null" to "true",
            "dialogs.build({ items: ['a', 'b'], itemsSelectMode: 'single', itemsSelectedIndex: 1 }) !== null" to "true",
            "dialogs.build({ items: ['a', 'b'], itemsSelectMode: 'multi', itemsSelectedIndices: [0, 1] }) !== null" to "true",
            "dialogs.build({ linkify: 'web', animation: 'dialog', theme: 'dark', dimAmount: 0.5, onBackKey: 'disabled' }) !== null" to "true",
            "dialogs.build({ progress: { max: 100, horizontal: true, showMinMax: false } }) !== null" to "true",
            "dialogs.build({ checkBoxPrompt: 'p', checkBoxChecked: true, inputHint: 'h', inputPrefill: 'p', inputSingleLine: true }) !== null" to "true",
            "dialogs.build({ positive: 'OK', positiveColor: 'red', titleColor: '#FF0000', stubborn: false, keepScreenOn: false }) !== null" to "true",
            "dialogs.build({ preset: true, title: 'aj6-p32' }) !== null" to "true",
            // Issue #364: the custom view (layout XML as a string, or an inflated view) reaches the dialog.
            "dialogs.build({ customView: '<frame><text text=\"aj6\"/></frame>' }).getCustomView() !== null" to "true",
            "(function () { var v = ui.run(function () { return ui.inflate('<frame/>'); }); " +
                "return dialogs.build({ customView: v, wrapInScrollView: false }).getCustomView() !== null; })()" to "true",
            "(function () { if (!floaty.hasPermission()) return 'no permission'; var d = dialogs.build({ title: 'aj6-p32' }); d.show(); d.dismiss(); return 'shown'; })()" to DeviceCases.INFO,
        )

    }

}
