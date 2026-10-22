package org.autojs.autojs.runtime.api.modules.barcode

import org.autojs.autojs.runtime.api.augment.barcode.Barcode
import org.autojs.autojs.runtime.api.augment.barcode.QrCode
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.1: `barcode` and `qrcode` on the new script module layer. Detection needs the ML Kit barcode plugin, so the
 * JVM checks the surface (against the obsolete `__barcode__.js` / `__qrcode__.js` and the current
 * `augment/barcode/Barcode` / `QrCode`) and the argument errors, which are raised before any image is read (D10). The
 * device decodes a QR code generated with ZXing (in memory and as a file under the temporary directory of
 * `device_eval.py`); those results depend on whether the plugin is installed and are printed for comparing the two
 * switch states (`<info>`).
 *
 * The argument errors whose first argument is neither an image nor a path stay on the JVM: the current modules capture
 * the screen for them, which needs the screen capture permission on the device.
 *
 * zh-CN: P3.1: 新层的 barcode 与 qrcode. 识别需要 ML Kit 条码插件, 因此 JVM 只检查表面 (对照旧 __barcode__.js / __qrcode__.js 与现有
 * augment/barcode/Barcode / QrCode) 与参数错误 (在读取图片之前抛出, D10). 真机识别以 ZXing 生成的二维码 (内存中, 以及 device_eval.py 临时目录下的文件);
 * 这些结果取决于插件是否安装, 只打印供两种开关状态比较 (<info>). 第一个参数既不是图片也不是路径的参数错误只在 JVM 检查: 现有模块对其截取屏幕, 在真机上需要
 * 截屏授权.
 */
class BarcodeModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("barcode", "qrcode")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun mountCurrent(side: DifferentialRhino) {
        val global = side.ktScope.globalThis as ScriptableObject
        Barcode(side.ktRuntime).augment(global)
        QrCode(side.ktRuntime).augment(global)
    }

    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        listOf("barcode", "qrcode").forEach { name ->
            val obsolete = ObsoleteModuleHost(rhino.context, rhino.jsScope).load(name)
            val report = SurfaceDiff.compare(SurfaceDiff.surface(obsolete, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt(name), rhino.ktScope.globalThis))
            assertTrue("$name: " + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        }
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        HostModuleTestSupport.assertSameSurface(rhino, "barcode", mountCurrent = ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "qrcode", mountCurrent = ::mountCurrent)
        val compared = HostModuleTestSupport.assertSameResults(rhino, SHAPE.map { it.first }, emptyMap(), mountCurrent = ::mountCurrent)
        assertTrue("compared $compared", compared == SHAPE.size)
        HostModuleTestSupport.check(rhino, SHAPE)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS + JVM_ONLY_ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(SHAPE + DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("BarcodeModulesTest", "new script module layer (P3.1)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"
        private const val INFO = DeviceCases.INFO

        private const val MISSING = "'aj6-p31-missing.png'"

        /** A QR code of "aj6-p31" generated with ZXing (bundled with the host), as an ImageWrapper. */
        private const val QR = "(function () { var m = new com.google.zxing.MultiFormatWriter().encode('aj6-p31', com.google.zxing.BarcodeFormat.QR_CODE, 240, 240); " +
            "return org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, new com.journeyapps.barcodescanner.BarcodeEncoder().createBitmap(m)); })()"

        /** The same QR code as a PNG under the temporary directory of device_eval.py (removed after the run), as a path. */
        private const val QR_PATH = "(function () { var f = new java.io.File('/sdcard/Download/aj6-augment-bench/aj6-p31-qr.png'); if (!f.exists()) { " +
            "var o = new java.io.FileOutputStream(f); $QR.getBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); o.close(); } return String(f.getPath()); })()"

        /** Runs [call] and renders the result or the error (the plugin may be missing, unauthorized or disabled). */
        private fun outcome(call: String) =
            "(function () { try { var r = $call; return r === null || r === undefined ? String(r) : JSON.stringify(r); } " +
                "catch (e) { return 'throws ' + e.name + (e.javaException ? ' ' + e.javaException.getClass().getSimpleName() : ''); } })()"

        private val SHAPE = listOf(
            "[typeof barcode, typeof \$barcode, barcode === \$barcode, typeof qrcode, typeof \$qrcode, qrcode === \$qrcode]" to "[\"function\",\"function\",true,\"function\",\"function\",true]",
            "Object.keys(barcode).join()" to "\"recognizeText,recognizeTexts,detect,detectAll\"",
            "Object.keys(qrcode).join()" to "\"recognizeText,recognizeTexts,detect,detectAll\"",
        )

        /** Argument errors whose first argument is a path (the current modules failed to read the image instead). */
        private val ERRORS = listOf(
            "barcode('')" to RANGE,
            "barcode('  ', {})" to RANGE,
            "barcode($MISSING, 5)" to TYPE,
            "barcode($MISSING, [])" to TYPE,
            "barcode($MISSING, 'opt')" to TYPE,
            "barcode($MISSING, true, true)" to TYPE,
            "barcode($MISSING, {}, 1)" to TYPE,
            "barcode($MISSING, {}, {})" to TYPE,
            "barcode($MISSING, {}, true, 1)" to TYPE,
            "barcode.recognizeText($MISSING, 5)" to TYPE,
            "barcode.recognizeText($MISSING, null, 'all')" to TYPE,
            "barcode.recognizeTexts($MISSING, true)" to TYPE,
            "barcode.recognizeTexts($MISSING, {}, true)" to TYPE,
            "barcode.recognizeTexts('')" to RANGE,
            "barcode.detect($MISSING, function () {})" to TYPE,
            "barcode.detect(' ')" to RANGE,
            "barcode.detectAll($MISSING, true)" to TYPE,
            "barcode.detectAll($MISSING, 5)" to TYPE,
            "barcode.detectAll($MISSING, {}, {})" to TYPE,
            "qrcode('')" to RANGE,
            "qrcode($MISSING, 5)" to TYPE,
            "qrcode($MISSING, true, true)" to TYPE,
            "qrcode.recognizeText($MISSING, {}, 'x')" to TYPE,
            "qrcode.recognizeTexts($MISSING, false)" to TYPE,
            "qrcode.detect($MISSING, [])" to TYPE,
            "qrcode.detectAll('')" to RANGE,
            "qrcode.detectAll($MISSING, {}, null)" to TYPE,
        )

        /** Argument errors for which the current modules capture the screen (JVM only). */
        private val JVM_ONLY_ERRORS = listOf(
            "barcode(5)" to TYPE,
            "barcode([])" to TYPE,
            "barcode(function () {})" to TYPE,
            "barcode(true, {})" to TYPE,
            "barcode(false, false)" to TYPE,
            "barcode({}, {})" to TYPE,
            "barcode({}, 5)" to TYPE,
            "barcode(null, true, true)" to TYPE,
            "barcode({}, true, 1)" to TYPE,
            "barcode.recognizeTexts(5)" to TYPE,
            "barcode.recognizeTexts(true)" to TYPE,
            "barcode.recognizeTexts({}, {})" to TYPE,
            "barcode.detect(5, {})" to TYPE,
            "barcode.detectAll(null, {})" to TYPE,
            "qrcode(5)" to TYPE,
            "qrcode({}, 'all')" to TYPE,
            "qrcode.detectAll(true)" to TYPE,
        )

        private val DEVICE_ONLY = listOf(
            "barcode($MISSING)" to INFO,
            "qrcode.detectAll($MISSING, {})" to INFO,
            outcome("barcode($QR)") to INFO,
            outcome("barcode($QR_PATH)") to INFO,
            outcome("barcode($QR, true)") to INFO,
            outcome("barcode($QR_PATH, { isAll: true })") to INFO,
            outcome("barcode($QR, undefined)") to INFO,
            outcome("barcode.recognizeText($QR, { format: 'qr_code' })") to INFO,
            outcome("barcode.recognizeTexts($QR_PATH)") to INFO,
            outcome("(function () { var r = barcode.detect($QR); return r && [String(r.getRawValue()), String(r.getFormatName())]; })()") to INFO,
            outcome("barcode.detectAll($QR, { format: ['qrcode', 'ean_13'] }).length") to INFO,
            outcome("barcode.detectAll($QR, { format: 'no_such_format' })") to INFO,
            outcome("qrcode($QR)") to INFO,
            outcome("qrcode($QR_PATH, true)") to INFO,
            outcome("qrcode.recognizeTexts($QR, null)") to INFO,
            outcome("(function () { var r = qrcode.detect($QR_PATH, { enableAllPotentialQrCodes: true }); return r && String(r.getRawValue()); })()") to INFO,
            outcome("qrcode.detectAll($QR).length") to INFO,
        )

    }

}
