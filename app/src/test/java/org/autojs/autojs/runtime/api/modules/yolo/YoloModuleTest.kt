package org.autojs.autojs.runtime.api.modules.yolo

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.yolo.Yolo
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.1: `yolo` on the new script module layer. `load` hands its arguments to the current function, whose failures
 * are coded errors (`YoloScriptException`, read from `e.javaException.code` on the new layer); the JVM checks the surface
 * against the current `augment/yolo/Yolo` and the failures raised before a provider is contacted, the device repeats
 * them with both switch states (the current module turned every failure into a WrappedRuntimeException whose message
 * starts with the code). There is no obsolete JS.
 *
 * zh-CN: P3.1: 新层的 yolo. load 把参数交给现有函数, 其失败为带错误码的异常 (YoloScriptException, 新层上读取 e.javaException.code); JVM 检查表面 (对照现有
 * augment/yolo/Yolo) 与联系 Provider 之前的失败, 真机在两种开关状态下重复这些用例 (现有模块把一切失败转为消息以错误码开头的 WrappedRuntimeException). 没有旧 JS.
 */
class YoloModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("yolo")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "yolo") { Yolo(it.ktRuntime).augment(it.ktScope.globalThis as ScriptableObject) }
        HostModuleTestSupport.check(rhino, SHAPE)
    }

    @Test
    fun codedErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(SHAPE + DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("YoloModuleTest", "new script module layer (P3.1)")

        private const val DIR = "'/sdcard/Download/aj6-augment-bench/aj6-p31-no-model'"

        /** The error name, the code of the Java exception, `typeof e.code` and whether the message names a code. */
        private fun coded(call: String) =
            "(function () { try { $call; return 'no error'; } catch (e) { var j = e.javaException; " +
                "return [String(e.name), j && j.code !== undefined ? String(j.code) : null, typeof e.code, /YOLO_[A-Z_]+/.test(String(e.message))]; } })()"

        private fun failure(code: String) = "[\"JavaException\",\"$code\",\"undefined\",true]"

        private val SHAPE = listOf(
            "[typeof yolo, typeof \$yolo, yolo === \$yolo, Object.keys(yolo).join(), typeof yolo.load]" to "[\"object\",\"object\",true,\"load\",\"function\"]",
        )

        private val ERRORS = listOf(
            coded("yolo.load()") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, {}, 1)") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, 5)") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, [])") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR)") to failure("YOLO_COMPONENT_REQUIRED"),
            coded("yolo.load($DIR, null)") to failure("YOLO_COMPONENT_REQUIRED"),
            coded("yolo.load($DIR, undefined)") to failure("YOLO_COMPONENT_REQUIRED"),
            coded("yolo.load($DIR, { component: '  ' })") to failure("YOLO_COMPONENT_REQUIRED"),
        )

        private val DEVICE_ONLY = listOf(
            coded("yolo.load($DIR, { component: 'no-slash' })") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, { component: 'a.b/.C', device: 'gpu' })") to failure("YOLO_UNSUPPORTED_CAPABILITY"),
            coded("yolo.load($DIR, { component: 'a.b/.C', threads: 0 })") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, { component: 'a.b/.C' })") to failure("YOLO_MODEL_INVALID"),
            coded("yolo.load(' ', { component: 'a.b/.C' })") to failure("YOLO_MODEL_INVALID"),
        )

    }

}
