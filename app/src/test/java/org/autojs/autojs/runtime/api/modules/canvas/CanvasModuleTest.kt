package org.autojs.autojs.runtime.api.modules.canvas

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Roadmap P3.1: the `Canvas` constructor on the new script module layer. A canvas needs Android's graphics, so the JVM
 * checks the surface against the current `augment/canvas/Canvas` and the argument errors, which are raised before
 * anything is created (D10); the four overloads run on the device (`build/script-modules-device/CanvasModuleTest.json`).
 * There is no obsolete JS (the constructor came with 6.x).
 *
 * zh-CN: P3.1: 新层的 Canvas 构造器. 画布需要 Android 图形库, 因此 JVM 只检查表面 (对照现有 augment/canvas/Canvas) 与参数错误 (在创建之前抛出, D10); 四个
 * 重载在真机运行. 没有旧 JS (构造器随 6.x 加入).
 */
class CanvasModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("Canvas")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "Canvas")
        HostModuleTestSupport.check(rhino, SHAPE)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    /**
     * The argument errors against the current module (P3.6): every one is a known difference of appendix E (P3.1), the
     * current module threw WrappedIllegalArgumentException / IllegalArgumentException or failed in `Bitmap.createBitmap`
     * (not available on the JVM); the comparison fails if the current module ever gives the new result.
     */
    @Test
    fun argumentErrorsDifferFromTheCurrentModuleAsKnown() {
        val known = ERRORS.associate { (expression, expected) -> expression to (expected to "appendix E P3.1 Canvas (D10, Q7)") }
        val compared = HostModuleTestSupport.assertSameResults(rhino, ERRORS.map { it.first }, known)
        assertEquals(ERRORS.size, compared)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(SHAPE + DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("CanvasModuleTest", "new script module layer (P3.1)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private const val BITMAP = "android.graphics.Bitmap.createBitmap(5, 6, android.graphics.Bitmap.Config.ARGB_8888)"

        private val SHAPE = listOf(
            "[typeof Canvas, typeof \$Canvas, Object.getOwnPropertyNames(Canvas).filter(function (k) { return ['length', 'name', 'prototype', 'arguments', 'caller', 'arity'].indexOf(k) < 0; }).length]" to "[\"function\",\"undefined\",0]",
        )

        /** Argument errors (the current module threw WrappedIllegalArgumentException or failed in Bitmap.createBitmap). */
        private val ERRORS = listOf(
            "new Canvas(5)" to TYPE,
            "new Canvas('a')" to TYPE,
            "new Canvas(null)" to TYPE,
            "new Canvas({})" to TYPE,
            "new Canvas(undefined, 10)" to TYPE,
            "new Canvas(10, '10')" to TYPE,
            "new Canvas('10', 10)" to TYPE,
            "new Canvas(10, null)" to TYPE,
            "new Canvas(0, 10)" to RANGE,
            "new Canvas(10, -1)" to RANGE,
            "new Canvas(0.5, 10)" to RANGE,
            "new Canvas(NaN, 10)" to RANGE,
            "new Canvas(Infinity, 10)" to RANGE,
            "new Canvas(10, Math.pow(2, 31))" to RANGE,
            "Canvas(0, 10)" to RANGE,
            "Canvas(10)" to TYPE,
            "new Canvas(10, 10, 10)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "String(new Canvas().getClass().getName())" to "\"org.autojs.autojs.core.graphics.ScriptCanvas\"",
            "String(new Canvas(undefined).getClass().getSimpleName())" to "\"ScriptCanvas\"",
            "(function () { var c = new Canvas(12.9, 7); return [c.getWidth(), c.getHeight()]; })()" to "[12,7]",
            "(function () { var c = Canvas(3, 4); return [c.getWidth(), c.getHeight(), String(c.getClass().getSimpleName())]; })()" to "[3,4,\"ScriptCanvas\"]",
            "(function () { var c = new Canvas($BITMAP); return [c.getWidth(), c.getHeight()]; })()" to "[5,6]",
            "(function () { var i = org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, $BITMAP); var c = new Canvas(i); var r = [c.getWidth(), c.getHeight()]; i.recycle(); return r; })()" to "[5,6]",
            "(function () { var c = new Canvas(10, 10); c.drawColor(android.graphics.Color.RED); var i = c.toImage(); var r = [i.getWidth(), i.getHeight(), i.getBitmap().getPixel(3, 3) === android.graphics.Color.RED]; i.recycle(); return r; })()" to "[10,10,true]",
            "new Canvas($BITMAP.copy(android.graphics.Bitmap.Config.ARGB_8888, false))" to "throws JavaException IllegalStateException",
        )

    }

}
