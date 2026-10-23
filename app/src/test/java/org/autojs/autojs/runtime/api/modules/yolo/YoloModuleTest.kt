package org.autojs.autojs.runtime.api.modules.yolo

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.yolo.Yolo
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertEquals
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

    /**
     * The eight coded errors against the current module (P3.6; appendix E P3.1: both hand the arguments to `Yolo.load`
     * unchanged): the same exception class and the same code at the start of its message. The calls are compared bare:
     * in the JVM the current module's wrapper prints the original exception and then fails to build its own message
     * (`Context.getString` is not mocked), so a `catch` in the script (the form of [codedErrors]) never sees the code
     * there; the rendering recovers the printed original instead (on the device the current module throws a
     * WrappedRuntimeException whose message starts with the code).
     *
     * zh-CN: 八个带错误码的失败对照现有模块: 异常类相同, 消息开头的错误码相同. 对照裸调用: JVM 上现有模块的包装先打印原始异常, 再构造自己的消息时失败 (Context.getString 未
     * mock), 因此脚本内 catch (codedErrors 的写法) 在 JVM 上拿不到错误码; 渲染改从打印的原始异常恢复 (真机上现有模块抛出消息以错误码开头的 WrappedRuntimeException).
     */
    @Test
    fun codedErrorsGiveTheSameResultsAsTheCurrentModule() {
        val calls = CODED_CALLS.map { it.first }
        val mountCurrent: (DifferentialRhino) -> Unit = { Yolo(it.ktRuntime).augment(it.ktScope.globalThis as ScriptableObject) }
        assertEquals(calls.size, HostModuleTestSupport.assertSameResults(rhino, calls, emptyMap(), mountCurrent = mountCurrent))
        val current = DifferentialRhino().use { side -> mountCurrent(side); calls.map { side.render(DifferentialRhino.Side.KT, it) } }
        CODED_CALLS.forEachIndexed { i, (call, code) ->
            val new = rhino.render(DifferentialRhino.Side.KT, call)
            assertEquals("$call\n    current ${current[i]}\n    new     $new", listOf(code, code), listOf(current[i], new).map { CODE.find(it.detail.orEmpty())?.value })
        }
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

        private val CODE = Regex("YOLO_[A-Z_]+")

        /** The calls that fail before a provider is contacted, with their codes. */
        private val CODED_CALLS = listOf(
            "yolo.load()" to "YOLO_INVALID_ARGUMENT",
            "yolo.load($DIR, {}, 1)" to "YOLO_INVALID_ARGUMENT",
            "yolo.load($DIR, 5)" to "YOLO_INVALID_ARGUMENT",
            "yolo.load($DIR, [])" to "YOLO_INVALID_ARGUMENT",
            "yolo.load($DIR)" to "YOLO_COMPONENT_REQUIRED",
            "yolo.load($DIR, null)" to "YOLO_COMPONENT_REQUIRED",
            "yolo.load($DIR, undefined)" to "YOLO_COMPONENT_REQUIRED",
            "yolo.load($DIR, { component: '  ' })" to "YOLO_COMPONENT_REQUIRED",
        )

        private val ERRORS = CODED_CALLS.map { (call, code) -> coded(call) to failure(code) }

        private val DEVICE_ONLY = listOf(
            coded("yolo.load($DIR, { component: 'no-slash' })") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, { component: 'a.b/.C', device: 'gpu' })") to failure("YOLO_UNSUPPORTED_CAPABILITY"),
            coded("yolo.load($DIR, { component: 'a.b/.C', threads: 0 })") to failure("YOLO_INVALID_ARGUMENT"),
            coded("yolo.load($DIR, { component: 'a.b/.C' })") to failure("YOLO_MODEL_INVALID"),
            coded("yolo.load(' ', { component: 'a.b/.C' })") to failure("YOLO_MODEL_INVALID"),
        )

    }

}
