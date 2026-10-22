package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.ocr.Ocr
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.augment.ocr.OcrMLKit
import org.autojs.autojs.runtime.api.augment.ocr.OcrPaddle
import org.autojs.autojs.runtime.api.augment.ocr.OcrRapid
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ScriptableObject
import java.io.File
import org.autojs.autojs.runtime.api.Ocr as ApiOcr

/**
 * Roadmap P3.1: `ocr` with its engine modules `ocr.mlkit` / `ocr.paddle` / `ocr.rapid` on the new script module layer.
 * The surface (module objects, `$ocr`, key order) and the engine selection (`tap`, `mode`, an engine module as mode, which
 * the current functions compare through `ScriptRuntime.augmentedOcrMLKit` and the others) are compared with the current
 * `augment/ocr/Ocr` in the same JVM; the members of the obsolete `__ocr__.js` (with `ocr-mlkit.js` / `ocr-paddle.js`) must
 * exist on the new module, except its internal `_modeName` / `_forcibleModeArgIndex`. The JVM checks the argument errors,
 * raised before an engine is selected or an image is read (paths of missing files stand for images); the device repeats
 * those the current module also raises without a screen capture and runs the recognition on a generated image (results
 * depend on the installed OCR plugins and are printed, not compared).
 *
 * Errors of calls without an image (`ocr(5)`, `ocr({}, {})`) stay on the JVM: the current module captures the screen for
 * them (a permission dialog).
 *
 * zh-CN: P3.1: 新层的 ocr 及其引擎模块 ocr.mlkit / ocr.paddle / ocr.rapid. 表面 (模块对象, $ocr, 键顺序) 与引擎选择 (tap, mode, 以引擎模块作为模式, 现有函数经
 * ScriptRuntime.augmentedOcrMLKit 等比较) 在同一 JVM 中与现有 augment/ocr/Ocr 比较; 旧 __ocr__.js (及 ocr-mlkit.js / ocr-paddle.js) 的成员须存在于新模块上,
 * 其内部的 _modeName / _forcibleModeArgIndex 除外. JVM 检查参数错误 (在选择引擎或读取图片之前抛出, 以不存在的文件路径代表图片); 真机重复其中现有模块不截屏也会
 * 抛出的用例, 并在生成的图片上运行识别 (结果取决于已安装的 OCR 插件, 只打印不比较). 不带图片的调用 (ocr(5), ocr({}, {})) 的错误只在 JVM 检查: 现有模块对其截屏
 * (权限对话框).
 */
class OcrModulesTest {

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
    fun surfaceMatchesTheCurrentModule() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, PATHS) }
        assertEquals(expected, actual)
        PATHS.forEach { HostModuleTestSupport.assertSameSurface(rhino, it, ::prepare, ::mountCurrent) }
        val jvmSelection = selection(JVM_MODE)
        val compared = HostModuleTestSupport.assertSameResults(rhino, LAYOUT + jvmSelection.keys, emptyMap(), ::prepare, ::mountCurrent)
        assertEquals(LAYOUT.size + jvmSelection.size, compared)
        HostModuleTestSupport.check(rhino, SHAPE + jvmSelection.toList() + kept(JVM_MODE).toList())
    }

    /** The obsolete module loads its engine modules with `require` and reads `scriptRuntime.ocrMLKit` / `ocrPaddle` lazily. */
    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        val factories = rhino.context.newObject(rhino.jsScope)
        listOf("ocr-mlkit", "ocr-paddle").forEach { factories.put(it, factories, factory(it)) }
        rhino.jsScope.globalThis.put("__obsoleteFactories__", rhino.jsScope.globalThis, factories)
        rhino.evalJs("global.require = function (name) { return __obsoleteFactories__[name]; }")
        val obsolete = host.load("ocr")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(obsolete, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt("ocr"), rhino.ktScope.globalThis))
        assertEquals(SurfaceDiff.describe(report, "js", "kt"), setOf("_modeName", "_forcibleModeArgIndex"), report.onlyInFirst)
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.typeMismatches.isEmpty())
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS + JVM_ONLY_ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(SHAPE + selection("''").toList() + kept("''").toList() + DEVICE_ONLY)
    }

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        prepare(side)
        val mounter = RuntimeModulesFixture.mount(side, MODULES, newLayer) { _, global, runtime -> mountLegacy(global, runtime) }
        // The runtime reads the engine module objects from the mounter, as with the switch on.
        if (mounter != null) HostModuleTestSupport.setField(side.ktRuntime, "scriptModules", mounter)
    }

    /** The JVM starts with an engine selected: the `mode` getter (also read by `Object.getOwnPropertyDescriptor`) needs the OCR plugins when automatic. */
    private fun prepare(side: DifferentialRhino) {
        RuntimeModulesFixture.prepareHost(side.ktRuntime)
        HostModuleTestSupport.setField(side.ktRuntime, "ocr", ApiOcr().apply { mode = OcrMode.MLKIT })
    }

    private fun mountCurrent(side: DifferentialRhino) {
        mountLegacy(side.ktScope.globalThis as ScriptableObject, side.ktRuntime)
    }

    /** The current modules as `ScriptRuntime.augmentLegacy` mounts them, keeping the engine module objects. */
    private fun mountLegacy(global: ScriptableObject, runtime: ScriptRuntime) {
        Ocr(runtime).augment(global).also { ocr ->
            HostModuleTestSupport.setField(runtime, "legacyAugmentedOcrMLKit", OcrMLKit(runtime).augment(ocr))
            HostModuleTestSupport.setField(runtime, "legacyAugmentedOcrPaddle", OcrPaddle(runtime).augment(ocr))
            HostModuleTestSupport.setField(runtime, "legacyAugmentedOcrRapid", OcrRapid(runtime).augment(ocr))
        }
    }

    /** The factory exported by the obsolete engine module `[name].js`. */
    private fun factory(name: String): Any? {
        val file = File(ObsoleteModuleHost.OBSOLETE_DIR, "$name.js")
        val wrapper = rhino.context.evaluateString(rhino.jsScope, "(function (module, exports) {\n${file.readText()}\nreturn module.exports; })", file.name, 0, null) as Callable
        val module = rhino.context.newObject(rhino.jsScope)
        module.put("exports", module, rhino.context.newObject(rhino.jsScope))
        return wrapper.call(rhino.context, rhino.jsScope, rhino.jsScope.globalThis, arrayOf(module, module.get("exports", module)))
    }

    companion object {

        private val DEVICE = DeviceCases("OcrModulesTest", "new script module layer (P3.1)")

        private val MODULES = listOf("ocr")

        private val GLOBALS = listOf("ocr", "\$ocr")

        private val PATHS = listOf("ocr", "ocr.mlkit", "ocr.paddle", "ocr.rapid")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private const val MISSING = "'aj6-p31-missing.png'"

        /** The engine the JVM starts with and returns to after each selection case ([prepare]); the device returns to automatic. */
        private const val JVM_MODE = "'mlkit'"

        /** A white 320 x 96 image with "AJ6 OCR 123" in black, drawn in the script. */
        private const val IMG = "(function () { var b = android.graphics.Bitmap.createBitmap(320, 96, android.graphics.Bitmap.Config.ARGB_8888); " +
            "var c = new android.graphics.Canvas(b); c.drawColor(-1); var p = new android.graphics.Paint(); p.setColor(-16777216); p.setTextSize(48); " +
            "p.setAntiAlias(true); c.drawText('AJ6 OCR 123', 12, 64, p); return org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, b); })()"

        private const val DIR = "/sdcard/Download/aj6-augment-bench"

        /** The same image as a PNG under the temporary directory of device_eval.py (removed after the run), as a path. */
        private const val PNG = "(function () { var f = new java.io.File('$DIR/aj6-p31-ocr.png'); if (!f.exists()) { " +
            "var o = new java.io.FileOutputStream(f); $IMG.getBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); o.close(); } return String(f.getPath()); })()"

        /** Whether [call] returns an array, its length and whether it found "123", or the error and its Java exception class. */
        private fun recognized(call: String) =
            "(function () { try { var r = $call; return [Array.isArray(r), r.length, JSON.stringify(r).indexOf('123') >= 0]; } " +
                "catch (e) { var j = e.javaException; return [String(e.name), j ? String(j.getClass().getSimpleName()) : null]; } })()"

        /** The key order of the module objects and the own names on the prototype chain of `ocr`. */
        private val LAYOUT = listOf(
            "Object.keys(ocr).join()",
            "[Object.keys(ocr.mlkit).join(), Object.keys(ocr.paddle).join(), Object.keys(ocr.rapid).join()]",
            "(function () { var out = []; for (var p = Object.getPrototypeOf(ocr); p !== null; p = Object.getPrototypeOf(p)) " +
                "out.push(p === Object.prototype ? 'Object.prototype' : p === Function.prototype ? 'Function.prototype' : Object.getOwnPropertyNames(p).sort().join()); return out.join(' > '); })()",
            "(function () { var d = Object.getOwnPropertyDescriptor(ocr, 'mode'); return [typeof d.get, typeof d.set, typeof d.value, d.writable, d.enumerable, d.configurable]; })()",
        )

        private val SHAPE = listOf(
            "[typeof ocr, ocr === \$ocr, typeof ocr.mlkit, typeof ocr.paddle, typeof ocr.rapid, typeof ocr.tap, typeof ocr.summary]" to
                "[\"function\",true,\"function\",\"function\",\"function\",\"function\",\"function\"]",
            "[typeof ocr.recognizeText, typeof ocr.detect, typeof ocr.mlkit.recognizeText, typeof ocr.paddle.detect, typeof ocr.rapid.detect]" to
                "[\"function\",\"function\",\"function\",\"function\",\"function\"]",
            "(function () { var d = Object.getOwnPropertyDescriptor(ocr, 'mode'); return [typeof d.value, d.enumerable, d.configurable]; })()" to
                "[\"string\",true,false]",
        )

        /** The selection of [body] read back with `ocr.mode`, then [reset] (a mode literal) selected. */
        private fun selected(body: String, reset: String) = "(function () { $body; var m = ocr.mode; ocr.tap($reset); return m; })()"

        /** The engine selection of the script, compared with the current module. */
        private fun selection(reset: String): Map<String, String> = linkedMapOf(
            selected("ocr.tap('PADDLE')", reset) to "\"paddle\"",
            selected("ocr.mode = 'Rapid'", reset) to "\"rapid\"",
            selected("ocr.tap(ocr.mlkit)", reset) to "\"mlkit\"",
            selected("ocr.mode = ocr.paddle", reset) to "\"paddle\"",
            "(function () { var r = [ocr.tap(null), ocr.tap(undefined), ocr.tap(''), ocr.tap('AUTO'), ocr.mode = null]; ocr.tap($reset); return r; })()" to
                "[null,null,null,null,null]",
        )

        /**
         * A rejected mode keeps the selection. Not compared on the JVM: the WrappedIllegalArgumentException of the current
         * module is a plain Java exception there, which a script `catch` does not catch.
         */
        private fun kept(reset: String): Map<String, String> = linkedMapOf(
            selected("ocr.tap(ocr.rapid); try { ocr.tap('xyz'); } catch (e) {}", reset) to "\"rapid\"",
            selected("ocr.tap('paddle'); try { ocr.mode = {}; } catch (e) {}", reset) to "\"paddle\"",
        )

        private val ERRORS = listOf(
            "ocr.tap()" to TYPE,
            "ocr.tap('mlkit', 1)" to TYPE,
            "ocr.tap('xyz')" to RANGE,
            "ocr.tap('  ')" to RANGE,
            "ocr.tap(5)" to TYPE,
            "ocr.tap(true)" to TYPE,
            "ocr.tap({})" to TYPE,
            "ocr.tap([])" to TYPE,
            "ocr.mode = 5" to TYPE,
            "ocr.mode = 'xyz'" to RANGE,
            "ocr.summary(1)" to TYPE,
            "ocr.toString(1)" to TYPE,
            "ocr($MISSING, 5)" to TYPE,
            "ocr($MISSING, 'region')" to TYPE,
            "ocr($MISSING, {}, 1)" to TYPE,
            "ocr(' ')" to RANGE,
            "ocr('')" to RANGE,
            "ocr.recognizeText($MISSING, true)" to TYPE,
            "ocr.recognizeText(' ', {})" to RANGE,
            "ocr.detect($MISSING, 5)" to TYPE,
            "ocr.detect($MISSING, [0, 0, 1, 1], 1)" to TYPE,
            "ocr.mlkit($MISSING, 5)" to TYPE,
            "ocr.mlkit.recognizeText(' ')" to RANGE,
            "ocr.mlkit.detect($MISSING, {}, 1)" to TYPE,
            "ocr.paddle($MISSING, 'x')" to TYPE,
            "ocr.paddle.recognizeText($MISSING, 5)" to TYPE,
            "ocr.paddle.detect('')" to RANGE,
            "ocr.rapid($MISSING, {}, 1)" to TYPE,
            "ocr.rapid.recognizeText($MISSING, true)" to TYPE,
            "ocr.rapid.detect(' ')" to RANGE,
        )

        /** The current module captures the screen for these (no image first). */
        private val JVM_ONLY_ERRORS = listOf(
            "ocr(5)" to TYPE,
            "ocr(true)" to TYPE,
            "ocr({}, {})" to TYPE,
            "ocr([0, 0, 1, 1], 1)" to TYPE,
            "ocr(null, {})" to TYPE,
            "ocr.recognizeText(5)" to TYPE,
            "ocr.detect({}, [0, 0, 1, 1])" to TYPE,
            "ocr.mlkit(true)" to TYPE,
            "ocr.paddle.recognizeText([0, 0, 1, 1], {})" to TYPE,
            "ocr.rapid.detect(5)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "ocr.summary().split('\\n')[0]" to "\"[ OCR summary ]\"",
            "[String(ocr) === ocr.summary(), typeof ocr.mode]" to "[true,\"string\"]",
            "ocr.summary()" to DeviceCases.INFO,
            recognized("ocr($IMG)") to DeviceCases.INFO,
            recognized("ocr.recognizeText($PNG)") to DeviceCases.INFO,
            recognized("ocr.detect($IMG, [0, 0, 160, 96])") to DeviceCases.INFO,
            recognized("ocr.detect($PNG, { region: [0, 0, 320, 96] })") to DeviceCases.INFO,
            recognized("ocr.recognizeText($MISSING)") to DeviceCases.INFO,
            recognized("ocr.mlkit($IMG)") to DeviceCases.INFO,
            recognized("ocr.mlkit.detect($PNG)") to DeviceCases.INFO,
            recognized("ocr.paddle($IMG)") to DeviceCases.INFO,
            recognized("ocr.paddle.detect($IMG, [0, 0, 160, 96])") to DeviceCases.INFO,
            recognized("ocr.rapid($PNG)") to DeviceCases.INFO,
            recognized("ocr.rapid.detect($IMG)") to DeviceCases.INFO,
        )

    }

}
