package org.autojs.autojs.runtime.api.modules.tts

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.tts.Tts
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.tts.TtsService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.5: `tts` on the new script module layer. There is no obsolete JS (6.8.0), and the TtsService needs
 * Android, so the JVM gives the runtime a service allocated without its constructor: the surface (the four properties,
 * the functions and their aliases, names and order) against the current `augment/tts/Tts`, and the argument errors,
 * which the new layer raises before calling the service (D10; the current module threw IllegalArgumentException or
 * WrappedIllegalArgumentException). The calls into the service run on the device without speaking aloud: queries,
 * silences, synthesis to a file and refused texts; results that depend on the device's TTS engine are informational
 * (compared between the switch states by hand).
 *
 * zh-CN: P3.5: 新层的 tts. 没有旧 JS (6.8.0), TtsService 需要 Android, 因此 JVM 给运行时一个不经构造函数分配的服务: 表面 (四个属性, 函数及其别名, 名称与顺序)
 * 对照现有 augment/tts/Tts, 以及新层在调用服务之前抛出的参数错误 (D10; 现有模块为 IllegalArgumentException 或 WrappedIllegalArgumentException). 调用服务的用例在
 * 真机运行且不出声: 查询, 静音, 合成到文件与被拒绝的文本; 取决于设备 TTS 引擎的结果只作参考 (在开关两态之间人工比较).
 */
class TtsModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("tts")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) {
        HostModuleTestSupport.setField(side.ktRuntime, "tts", DifferentialRhino.allocateWithoutConstructor(TtsService::class.java))
    }

    private fun mountCurrent(side: DifferentialRhino) {
        Tts(side.ktRuntime, TtsServices.of(side.ktRuntime)).augment(side.ktScope.globalThis as ScriptableObject)
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "tts", ::prepare, ::mountCurrent)
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
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

        private val DEVICE = DeviceCases("TtsModuleTest", "new script module layer (P3.5)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val SURFACE = listOf(
            "[tts.maxInputLength, tts.maxSynthesisFileSize, tts.QUEUE_FLUSH, tts.QUEUE_ADD, tts.getMaxInputLength()]" to "[3999,536870912,\"flush\",\"add\",3999]",
            "[tts.speakTask === tts.utterance, tts.enqueueTask === tts.enqueueUtterance, tts.synthesizeToFile === tts.synthesize, tts.synthesizeTask === tts.synthesis, " +
                "tts.synthesisTask === tts.synthesis, tts.stopAll === tts.stop, tts.config === tts.defaults]" to "[true,true,true,true,true,true,true]",
            "[tts.length, tts.speak.length, tts.utterance.length, tts.synthesize.length, tts.silence.length, tts.ready.length, tts.voices.length, " +
                "tts.isLanguageAvailable.length, tts.configure.length, tts.stop.length]" to "[1,1,1,2,1,0,0,1,1,0]",
            "Object.keys(tts).length" to "35",
        )

        private val ERRORS = listOf(
            "tts()" to TYPE,
            "tts(null)" to TYPE,
            "tts('')" to RANGE,
            "tts.speak('  ')" to RANGE,
            "tts.speak('a'.repeat(1000001))" to RANGE,
            "tts.speak(undefined)" to TYPE,
            "tts.speak('a', 5)" to TYPE,
            "tts.speak('a', 'b')" to TYPE,
            "tts.speak('a', [])" to TYPE,
            "tts.speak('a', function () {})" to TYPE,
            "tts.speak('a', {}, 1)" to TYPE,
            "tts.enqueue()" to TYPE,
            "tts.utterance('')" to RANGE,
            "tts.enqueueUtterance('a', true)" to TYPE,
            "tts.synthesize('a')" to TYPE,
            "tts.synthesize('a', null)" to TYPE,
            "tts.synthesize('', 'x.wav')" to RANGE,
            "tts.synthesis('a', undefined)" to TYPE,
            "tts.synthesize('a', 'x.wav', 1)" to TYPE,
            "tts.silence()" to TYPE,
            "tts.silence(null)" to TYPE,
            "tts.silence('abc')" to TYPE,
            "tts.silence(-1)" to RANGE,
            "tts.silence(1.5)" to RANGE,
            "tts.silence(Infinity)" to RANGE,
            "tts.silence(NaN)" to RANGE,
            "tts.silenceTask(10, 'x')" to TYPE,
            "tts.ready(1)" to TYPE,
            "tts.ready({ engine: 1 })" to TYPE,
            "tts.ready({ other: 'x' })" to TYPE,
            "tts.ready('a', 'b')" to TYPE,
            "tts.engines([])" to TYPE,
            "tts.languages(true)" to TYPE,
            "tts.voices(5)" to TYPE,
            "tts.voices({ foo: 1 })" to TYPE,
            "tts.voices({ offline: 'yes' })" to TYPE,
            "tts.voices({ installedOnly: 1 })" to TYPE,
            "tts.voices({ engine: 2 })" to TYPE,
            "tts.isLanguageAvailable()" to TYPE,
            "tts.isLanguageAvailable(null)" to TYPE,
            "tts.isLanguageAvailable('en', 5)" to TYPE,
            "tts.isLanguageAvailable('en', { x: 1 })" to TYPE,
            "tts.languageAvailability(undefined)" to TYPE,
            "tts.stop(1)" to TYPE,
            "tts.reset(1)" to TYPE,
            "tts.isSpeaking(1)" to TYPE,
            "tts.status(1)" to TYPE,
            "tts.defaults(1)" to TYPE,
            "tts.configure()" to TYPE,
            "tts.configure(null)" to TYPE,
            "tts.configure([1])" to TYPE,
            "tts.configure(1)" to TYPE,
            "tts.getMaxInputLength(1)" to TYPE,
            "tts.openSettings(1)" to TYPE,
            "tts.installData(1)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "[tts.isSpeaking(), tts.isBusy(), typeof tts.status(), typeof tts.defaults(), typeof tts.stop(), typeof tts.reset()]" to "[false,false,\"object\",\"object\",\"number\",\"number\"]",
            "[tts.ready() instanceof Promise, tts.ready(null) instanceof Promise, tts.engines() instanceof Promise, tts.voices() instanceof Promise, tts.languages() instanceof Promise]" to "[true,true,true,true,true]",
            "[tts.isLanguageAvailable('en-US') instanceof Promise, tts.languageAvailability('en_US', null) instanceof Promise, tts.voices({ locale: 'en-US', offline: true }) instanceof Promise]" to "[true,true,true]",
            "typeof tts.configure({})" to "\"object\"",
            "tts.speak('a'.repeat(4000), { autoSplit: false })" to "throws JavaException IllegalArgumentException",
            "tts.synthesize('a'.repeat(4000), 'aj6-p35-tts.wav')" to "throws JavaException IllegalArgumentException",
            "tts.silence(100000000)" to "throws JavaException IllegalArgumentException",
            HostModuleTestSupport.settled("tts.engines()", "Array.isArray(v)") to DeviceCases.INFO,
            HostModuleTestSupport.settled("tts.ready()", "typeof v") to DeviceCases.INFO,
            HostModuleTestSupport.settled("tts.voices({ offline: true })", "Array.isArray(v)") to DeviceCases.INFO,
            HostModuleTestSupport.settled("tts.isLanguageAvailable('en-US')", "typeof v") to DeviceCases.INFO,
            HostModuleTestSupport.settled("tts.silence(10)", "typeof v") to DeviceCases.INFO,
            "(function () { var u = tts.silenceTask(10); return String(u.getClass().getSimpleName()); })()" to DeviceCases.INFO,
            "(function () { try { var r = tts.synthesize('ok', 'aj6-p35-tts.wav'); return typeof r; } catch (e) { return 'failed ' + e; } })()" to DeviceCases.INFO,
        )

    }

}
