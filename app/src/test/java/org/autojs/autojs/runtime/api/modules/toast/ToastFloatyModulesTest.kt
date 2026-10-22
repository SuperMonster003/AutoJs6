package org.autojs.autojs.runtime.api.modules.toast

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.floaty.Floaty
import org.autojs.autojs.runtime.api.augment.toast.Toast
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.2 (group 1): `toast` and `floaty` on the new script module layer. Showing a toast or a window needs
 * Android, so the JVM covers the surface against the current `augment/toast/Toast` / `augment/floaty/Floaty` and the
 * obsolete `__toast__.js` / `__floaty__.js`, and the argument errors, which the new layer raises before it calls the
 * current module (T4; the current modules threw IllegalArgumentException or ignored the value). The calls run on the
 * device; they close what they open (a toast goes away by itself, a window is closed at once).
 *
 * zh-CN: P3.2 (第 1 组): 新层的 toast 与 floaty. 显示消息框或悬浮窗需要 Android, 因此 JVM 覆盖表面 (对照现有 augment/toast/Toast / augment/floaty/Floaty 与
 * 旧 __toast__.js / __floaty__.js) 与参数错误, 新层在调用现有模块之前抛出 (T4; 现有模块抛 IllegalArgumentException 或忽略该值). 调用在真机运行, 打开的界面
 * 自行关闭 (消息框自动消失, 悬浮窗随即关闭).
 */
class ToastFloatyModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("toast", "floaty")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun mountCurrent(side: DifferentialRhino) {
        val global = side.ktScope.globalThis as ScriptableObject
        Toast(side.ktRuntime).augment(global)
        Floaty(side.ktRuntime).augment(global)
    }

    @Test
    fun installingBuildsNothing() {
        listOf("toast", "\$toast", "floaty", "\$floaty").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        HostModuleTestSupport.assertSameSurface(rhino, "toast", mountCurrent = ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "floaty", mountCurrent = ::mountCurrent)
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.setRuntimeProperty("getUiHandler", rhino.evalJs("(function () { return null; })"))
        listOf("toast", "floaty").forEach { name ->
            val report = SurfaceDiff.compare(SurfaceDiff.surface(host.load(name), rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt(name), rhino.ktScope.globalThis))
            assertTrue(name + "\n" + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        }
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

        private val DEVICE = DeviceCases("ToastFloatyModulesTest", "new script module layer (P3.2)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val SURFACE = listOf(
            "[typeof toast, typeof \$toast, toast === \$toast, typeof toast.dismissAll, typeof floaty, typeof \$floaty, floaty === \$floaty]" to
                "[\"function\",\"function\",true,\"function\",\"object\",\"object\",true]",
            "Object.keys(toast).join()" to "\"dismissAll\"",
            "Object.keys(floaty).join()" to "\"window,rawWindow,hasPermission,checkPermission,requestPermission,ensurePermission,closeAll,getClip\"",
        )

        private val ERRORS = listOf(
            "toast('a', 'x')" to RANGE,
            "toast('a', 'longer')" to RANGE,
            "toast('a', true, 'xx')" to RANGE,
            "toast('a', {})" to TYPE,
            "toast('a', [])" to TYPE,
            "toast('a', null, function () {})" to TYPE,
            "toast('a', 1, 2, 3)" to TYPE,
            "toast.dismissAll(1)" to TYPE,
            "floaty.window()" to TYPE,
            "floaty.window(1)" to TYPE,
            "floaty.window({})" to TYPE,
            "floaty.rawWindow(null)" to TYPE,
            "floaty.window('<frame/>', 1)" to TYPE,
            "floaty.getClip('x')" to TYPE,
            "floaty.getClip(1.5)" to RANGE,
            "floaty.getClip(Infinity)" to RANGE,
            "floaty.getClip(1, 2)" to TYPE,
            "floaty.hasPermission(1)" to TYPE,
            "floaty.requestPermission(1)" to TYPE,
            "floaty.closeAll(1)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "toast('aj6-p32', 's')" to "undefined",
            "toast(undefined)" to "undefined",
            "toast('aj6-p32', 'l' + 'ong', 'F')" to "undefined",
            "toast.dismissAll()" to "undefined",
            "typeof floaty.hasPermission()" to "\"boolean\"",
            "floaty.checkPermission() === floaty.hasPermission()" to "true",
            "floaty.closeAll()" to "undefined",
            "(function () { if (!floaty.hasPermission()) return 'no permission'; var w = floaty.rawWindow('<frame/>'); var r = typeof w.close; w.close(); return r; })()" to DeviceCases.INFO,
        )

    }

}
