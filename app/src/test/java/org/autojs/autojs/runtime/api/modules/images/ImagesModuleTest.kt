package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.images.Images
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.1: `images` on the new script module layer. The surface (the module object with its emitter prototype and
 * `ObjectFrame`, `$images` and the nine global functions with their order) is compared with the current
 * `augment/images/Images` in the same JVM ([RuntimeModulesFixture]); the members of the obsolete `__images__.js` must
 * exist on the new module. Images need Android, so the JVM checks the argument errors, which are raised before any image
 * is read (D10; paths of missing files stand for images), and the device repeats them and runs the functions on a
 * generated image (a bitmap drawn in the script and a PNG of it under the temporary directory of `device_eval.py`, never
 * a screen capture).
 *
 * The argument errors of the screen capture functions and of `loadAsync` stay on the JVM: the current module requests
 * the screen capture permission (a dialog) or rejects a promise asynchronously for them.
 *
 * zh-CN: P3.1: 新层的 images. 表面 (模块对象及其发射器原型与 ObjectFrame, $images, 九个全局函数及其顺序) 在同一 JVM 中与现有 augment/images/Images 比较,
 * 旧 __images__.js 的成员须存在于新模块上. 图片需要 Android, 因此 JVM 只检查参数错误 (在读取图片之前抛出, D10; 以不存在的文件路径代表图片), 真机重复这些用例,
 * 并在生成的图片 (脚本内绘制的位图及其在 device_eval.py 临时目录下的 PNG, 绝不截屏) 上运行各函数. 截屏函数与 loadAsync 的参数错误只在 JVM 检查: 现有模块对其
 * 申请截屏权限 (对话框) 或异步拒绝 Promise.
 */
class ImagesModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    /**
     * The globals with their order ([RuntimeModulesFixture.describe]), the members of the module object ([SurfaceDiff];
     * `describe` cannot list the own names of the `ObjectFrame` class) and its key order and prototype chain.
     */
    @Test
    fun surfaceMatchesTheCurrentModule() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, emptyList())
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, emptyList()) }
        assertEquals(expected, actual)
        HostModuleTestSupport.assertSameSurface(rhino, "images", prepare = { RuntimeModulesFixture.prepareHost(it.ktRuntime) }, mountCurrent = ::mountCurrent)
        val compared = HostModuleTestSupport.assertSameResults(rhino, LAYOUT, emptyMap(), prepare = { RuntimeModulesFixture.prepareHost(it.ktRuntime) }, mountCurrent = ::mountCurrent)
        assertEquals(LAYOUT.size, compared)
        HostModuleTestSupport.check(rhino, SHAPE)
    }

    /** The obsolete module needs `scriptRuntime.threads.atomic`, `events.__asEmitter__` and `__asGlobal__` when it is created. */
    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.setRuntimeProperty("threads", rhino.evalJs("({ atomic: function () { return {}; } })"))
        rhino.evalJs("global.events = { __asEmitter__: function (o) { return o; } }; global.__asGlobal__ = function () {}")
        val obsolete = host.load("images")
        val report = SurfaceDiff.compare(SurfaceDiff.surface(obsolete, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt("images"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS + JVM_ONLY_ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(SHAPE + DEVICE_ONLY + DEVICE_ERRORS)
    }

    /**
     * 6.7.0 fixed a similarity of 1 read as a percentage. The new layer passes options and regions on, so the conversions of
     * the current module are checked: a similarity becomes the color threshold `round(255 * (1 - similarity))` (1 is an exact
     * match), a threshold stays as given, and a region component is a fraction of the screen only below 1 (1 is one pixel).
     * The device runs findColor with these values on the generated image ([DEVICE_ONLY]).
     */
    @Test
    fun similarityOfOneIsNotAPercentage() {
        val companion = Images.Companion::class.java
        val threshold = companion.getDeclaredMethod("parseThreshold", Any::class.java, Double::class.javaObjectType).apply { isAccessible = true }
        listOf("({ similarity: 1 })" to 0.0, "({ similarity: 0 })" to 255.0, "({ similarity: 0.98 })" to 5.0, "({ threshold: 1 })" to 1.0, "({})" to 4.0).forEach { (options, expected) ->
            assertEquals(options, expected, threshold.invoke(Images.Companion, rhino.evalKt(options), null) as Double, 0.0)
        }
        val metric = companion.getDeclaredMethod("parseScreenMetric", Double::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        listOf(1.0 to 1, 0.5 to 540, 0.0 to 0, -1.0 to 1080, 1.5 to 2).forEach { (reference, expected) ->
            assertEquals("$reference", expected, metric.invoke(Images.Companion, reference, 1080))
        }
    }

    /**
     * Q28: the current functions take a color text of the paths and the orientation of the screen capture options only
     * as a String, so a checked concatenated text (a ConsString) is passed as a String, the given objects left as they are.
     */
    @Test
    fun concatenatedTextsArePassedAsStrings() {
        val paths = rhino.evalKt("(function () { var s = '#FF'; s = s + '0000'; return [[1, 0, '#00FF00'], [5, 5, s]]; })()") as NativeArray
        val passedPaths = ImagesCall("findMultiColors", "aj6-p31-missing.png", "#FF0000", paths).paths(2).args[2] as NativeArray
        assertEquals(listOf<Any?>("#00FF00", "#FF0000"), passedPaths.map { (it as NativeArray)[2] })
        assertTrue((paths[1] as NativeArray)[2] !is String)
        val options = rhino.evalKt("(function () { var s = 'land'; s = s + 'scape'; return { orientation: s, width: 100 }; })()") as NativeObject
        val passedOptions = ImagesCall("requestScreenCapture", options).screenCapture().args[0] as NativeObject
        assertEquals("landscape", ScriptableObject.getProperty(passedOptions, "orientation"))
        assertEquals(100, (ScriptableObject.getProperty(passedOptions, "width") as Number).toInt())
        assertTrue(ScriptableObject.getProperty(options, "orientation") !is String)
    }

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        RuntimeModulesFixture.prepareHost(side.ktRuntime)
        RuntimeModulesFixture.mount(side, MODULES, newLayer) { _, global, runtime -> Images(runtime).augment(global) }
    }

    private fun mountCurrent(side: DifferentialRhino) {
        Images(side.ktRuntime).augment(side.ktScope.globalThis as ScriptableObject)
    }

    companion object {

        private val DEVICE = DeviceCases("ImagesModuleTest", "new script module layer (P3.1)")

        private val MODULES = listOf("images")

        private val GLOBALS = listOf(
            "images", "\$images",
            "captureScreen", "requestScreenCapture", "requestScreenCaptureAsync",
            "findColor", "findColorInRegion", "findColorEquals", "findMultiColors", "findImage", "findImageInRegion",
        )

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private const val MISSING = "'aj6-p31-missing.png'"

        /**
         * A white 64 x 48 image with a red square from (10, 8) to (19, 17), drawn in the script. The colors go through
         * `drawARGB` / `setARGB`: on Android 10+ Rhino may pick `drawColor(long)` (a ColorLong) for a number.
         */
        private const val IMG = "(function () { var b = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888); " +
            "var c = new android.graphics.Canvas(b); c.drawARGB(255, 255, 255, 255); var p = new android.graphics.Paint(); p.setARGB(255, 255, 0, 0); c.drawRect(10, 8, 20, 18, p); " +
            "return org.autojs.autojs.core.image.ImageWrapper.ofBitmap(runtime, b); })()"

        private const val DIR = "/sdcard/Download/aj6-augment-bench"

        /** The same image as a PNG under the temporary directory of device_eval.py (removed after the run), as a path. */
        private const val PNG = "(function () { var f = new java.io.File('$DIR/aj6-p31-img.png'); if (!f.exists()) { " +
            "var b = $IMG.getBitmap(); var o = new java.io.FileOutputStream(f); b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); o.close(); } return String(f.getPath()); })()"

        /** The width, the height and the pixel at (x, y) of the image [call] returns. */
        private fun probe(call: String, x: Int, y: Int) = "(function () { var r = $call; return [r.width, r.height, images.pixel(r, $x, $y)]; })()"

        private fun point(call: String) = "(function () { var p = $call; return p && [p.x, p.y]; })()"

        /** The key order of the module object and the own names of the objects on its prototype chain. */
        private val LAYOUT = listOf(
            "Object.keys(images).join()",
            "(function () { var out = []; for (var p = Object.getPrototypeOf(images); p !== null; p = Object.getPrototypeOf(p)) " +
                "out.push(p === Object.prototype ? 'Object.prototype' : Object.getOwnPropertyNames(p).sort().join()); return out.join(' > '); })()",
        )

        private val SHAPE = listOf(
            "[typeof images, images === \$images, typeof images.emit, typeof images.on, images.listenerCount('screen_capture_available')]" to "[\"object\",true,\"function\",\"function\",0]",
            "[typeof images.ObjectFrame, typeof findColor, findColor === images.findColor, typeof requestScreenCapture, typeof images.saveImage, images.saveImage === images.save]" to
                "[\"function\",\"function\",true,\"function\",\"function\",false]",
            "[Object.keys(images).length, Object.keys(images).indexOf('ObjectFrame') >= 0]" to "[82,true]",
        )

        /** Argument errors raised before an image is read (the current module failed to read the missing image instead). */
        private val ERRORS = listOf(
            "images.initOpenCvIfNeeded(1)" to TYPE,
            "images.read()" to TYPE,
            "images.read('')" to RANGE,
            "images.read('  ')" to RANGE,
            "images.read(5)" to TYPE,
            "images.read($MISSING, 'yes')" to TYPE,
            "images.imread(null)" to TYPE,
            "images.copy($MISSING, 1)" to TYPE,
            "images.load('')" to RANGE,
            "images.clip($MISSING, 1, 2)" to TYPE,
            "images.clip($MISSING, 1, 2, 3)" to TYPE,
            "images.clip($MISSING, 5)" to TYPE,
            "images.clip($MISSING, null)" to TYPE,
            "images.clip($MISSING, 'a', 0, 1, 1)" to TYPE,
            "images.clip($MISSING, NaN, 0, 1, 1)" to RANGE,
            "images.clip(5, [0, 0, 1, 1])" to TYPE,
            "images.clip('', [0, 0, 1, 1])" to RANGE,
            "images.pixel($MISSING, 'x', 1)" to TYPE,
            "images.pixel($MISSING, 1, NaN)" to RANGE,
            "images.pixel($MISSING, 1)" to TYPE,
            "images.pixel({}, 1, 1)" to TYPE,
            "images.stopScreenCapture(1)" to TYPE,
            "images.getScreenCaptureOptions(1)" to TYPE,
            "images.save($MISSING)" to TYPE,
            "images.save($MISSING, '')" to RANGE,
            "images.save($MISSING, 5)" to TYPE,
            "images.save($MISSING, 'aj6-p31-out.png', 5)" to TYPE,
            "images.save($MISSING, 'aj6-p31-out.png', 'png', 'high')" to TYPE,
            "images.save($MISSING, 'aj6-p31-out.png', 'png', 100, 1)" to TYPE,
            "images.saveImage($MISSING, 'aj6-p31-out.png', 'png', NaN)" to RANGE,
            "images.invert()" to TYPE,
            "images.grayscale($MISSING, 'x')" to TYPE,
            "images.isGrayscale(5)" to TYPE,
            "images.threshold($MISSING, 'a', 255)" to TYPE,
            "images.threshold($MISSING, 100)" to TYPE,
            "images.threshold($MISSING, 100, 255, {})" to TYPE,
            "images.interval($MISSING, '#FF0000', 'x')" to TYPE,
            "images.adaptiveThreshold($MISSING, 255, 1, 'BINARY', 3, 0)" to TYPE,
            "images.adaptiveThreshold($MISSING, 255, 'MEAN_C', 'BINARY', NaN, 0)" to RANGE,
            "images.blur($MISSING, {})" to TYPE,
            "images.blur($MISSING, 'x')" to TYPE,
            "images.blur($MISSING, 3, null, {})" to TYPE,
            "images.medianBlur($MISSING, true)" to TYPE,
            "images.gaussianBlur($MISSING, 3, 'a')" to TYPE,
            "images.bilateralFilter($MISSING, 'd')" to TYPE,
            "images.bilateralFilter($MISSING, 9, 75, 75, [])" to TYPE,
            "images.cvtColor($MISSING)" to TYPE,
            "images.cvtColor($MISSING, 5)" to TYPE,
            "images.findCircles($MISSING, 5)" to TYPE,
            "images.findCircles($MISSING, 'opt')" to TYPE,
            "images.resize($MISSING, 32)" to TYPE,
            "images.resize($MISSING, [32, 24], {})" to TYPE,
            "images.scale($MISSING, 'a', 1)" to TYPE,
            "images.scale($MISSING, 0.5)" to TYPE,
            "images.rotate($MISSING, 'deg')" to TYPE,
            "images.rotate($MISSING, 90, 'x')" to TYPE,
            "images.flip($MISSING, 5)" to TYPE,
            "images.flip($MISSING, 'h', 1)" to TYPE,
            "images.flip($MISSING, true, true, true)" to TYPE,
            "images.concat($MISSING, 5)" to TYPE,
            "images.concat($MISSING, $MISSING, {})" to TYPE,
            "images.detectColor($MISSING, '#FF0000', 0)" to TYPE,
            "images.detectColor($MISSING, '#FF0000', 'x', 0)" to TYPE,
            "images.detectColor($MISSING, '#FF0000', 0, 0, 'big')" to TYPE,
            "images.detectsColor($MISSING, '#FF0000', 0, 0, 4, 5)" to TYPE,
            "images.detectMultiColors($MISSING, 0, 0, '#FF0000', 5)" to TYPE,
            "images.detectsMultiColors($MISSING, 0, 0, '#FF0000', [], 5)" to TYPE,
            "images.findPointByColor($MISSING, '#FF0000', 0, 0, 10, 10, NaN)" to RANGE,
            "images.findColor($MISSING, '#FF0000', 'opt')" to TYPE,
            "images.findColor($MISSING, '#FF0000', [])" to TYPE,
            "images.findColor($MISSING, '#FF0000', {}, 1)" to TYPE,
            "findColor($MISSING, '#FF0000', true)" to TYPE,
            "images.findColorInRegion($MISSING, '#FF0000', 0, 'y')" to TYPE,
            "images.findPointByColorExactly($MISSING, '#FF0000', {}, 0)" to TYPE,
            "images.findColorEquals($MISSING, '#FF0000', 0, 0, 10, 10, 4)" to TYPE,
            "images.findPointsByColor($MISSING, '#FF0000', 5)" to TYPE,
            "images.findAllPointsForColor($MISSING, '#FF0000', [])" to TYPE,
            "images.findPointByColors($MISSING, '#FF0000', 5)" to TYPE,
            "images.findMultiColors($MISSING, '#FF0000', [], 'opt')" to TYPE,
            "images.findPointsByColors($MISSING, '#FF0000')" to TYPE,
            "images.countPointsByColor($MISSING, '#FF0000', true)" to TYPE,
            "images.getMeanColor($MISSING, 5)" to TYPE,
            "images.findPointByImage($MISSING, 5)" to TYPE,
            "images.findImage($MISSING, '')" to RANGE,
            "images.findImage($MISSING, $MISSING, 'opt')" to TYPE,
            "images.findImageInRegion($MISSING, $MISSING, 0, 0, 'w')" to TYPE,
            "images.matchTemplate($MISSING, $MISSING, 5)" to TYPE,
            "images.fromBase64()" to TYPE,
            "images.fromBase64(5)" to TYPE,
            "images.toBase64($MISSING, 5)" to TYPE,
            "images.toBase64($MISSING, 'png', {})" to TYPE,
            "images.toBytes($MISSING, 'png', 'q')" to TYPE,
            "images.fromBytes('abc')" to TYPE,
            "images.fromBytes({})" to TYPE,
            "images.readPixels(5)" to TYPE,
            "images.matToImage($MISSING)" to TYPE,
            "images.detectAndComputeFeatures($MISSING, 5)" to TYPE,
            "images.matchFeatures(5, $MISSING)" to TYPE,
            "images.matchFeatures($MISSING, $MISSING, [])" to TYPE,
            "images.quantize($MISSING, 5)" to TYPE,
            "images.quantizeToFile($MISSING, '')" to RANGE,
            "images.quantizeToFile($MISSING, 'aj6-p31-out.png', 5)" to TYPE,
            "images.compress($MISSING, 'jpg', 'q')" to TYPE,
            "images.compressToBytes($MISSING, 5)" to TYPE,
            "images.downsample($MISSING, 'w', 10)" to TYPE,
            "images.downsample(5, 10, 10)" to TYPE,
            "images.downsample($MISSING, 10, 10, 'yes')" to TYPE,
            "images.getSize(5)" to TYPE,
            "images.getWidth({})" to TYPE,
            "images.getHeight('')" to RANGE,
            "images.buildRegion($MISSING)" to TYPE,
            "images.buildRegion($MISSING, 5)" to TYPE,
            "images.__buildRegion(5)" to TYPE,
            "images.__buildRegion(null, 'w')" to TYPE,
            "images.psnr($MISSING, 5)" to TYPE,
            "images.ssim(5, $MISSING)" to TYPE,
            "images.mse($MISSING)" to TYPE,
            "images.ncc($MISSING, $MISSING, 1)" to TYPE,
            "images.isEqual($MISSING, 5)" to TYPE,
            "images.getSimilarity($MISSING, $MISSING, 5)" to TYPE,
            // Q28: the documented values of the arguments and the option fields (appendix C).
            "images.inRange($MISSING, 'nocolor', '#000000')" to RANGE,
            "images.inRange($MISSING, {}, 0)" to TYPE,
            "images.interval($MISSING, [], 10)" to TYPE,
            "images.threshold($MISSING, 100, 255, 'XYZ')" to RANGE,
            "images.adaptiveThreshold($MISSING, 255, 'MEDIAN_C', 'BINARY', 3, 0)" to RANGE,
            "images.adaptiveThreshold($MISSING, 255, 'MEAN_C', 'TRUNC', 3, 0)" to RANGE,
            "images.adaptiveThreshold($MISSING, 255, 'MEAN_C', 'BINARY', 3.5e10, 0)" to RANGE,
            "images.blur($MISSING, 3, 'a')" to TYPE,
            "images.blur($MISSING, [3, 3, 3])" to RANGE,
            "images.blur($MISSING, 3, null, 'NOWHERE')" to RANGE,
            "images.medianBlur($MISSING, [3, 5])" to RANGE,
            "images.gaussianBlur($MISSING, 3, 0, 0, 'NOWHERE')" to RANGE,
            "images.cvtColor($MISSING, 'NOPE')" to RANGE,
            "images.cvtColor($MISSING, 'BGR2GRAY', 'x')" to TYPE,
            "images.findCircles($MISSING, { dp: 'x' })" to TYPE,
            "images.findCircles($MISSING, { minRadius: Infinity })" to RANGE,
            "images.findCircles($MISSING, { region: [0, 'a'] })" to TYPE,
            "images.resize($MISSING, [0, 10])" to RANGE,
            "images.resize($MISSING, [1, 2, 3])" to RANGE,
            "images.resize($MISSING, [32, 24], 'BAD')" to RANGE,
            "images.resize($MISSING, [32, 24], 9)" to RANGE,
            "images.scale($MISSING, 0.5, 0.5, 'BAD')" to RANGE,
            "images.concat($MISSING, $MISSING, 'LEFT')" to RANGE,
            "images.concat($MISSING, $MISSING, 4)" to RANGE,
            "images.flip($MISSING, 'diag')" to RANGE,
            "images.flip($MISSING, [1])" to TYPE,
            "images.flip($MISSING, { x: 'y' })" to TYPE,
            "images.detectColor($MISSING, '#FF0000', 0, 0, 4, 'xyz')" to RANGE,
            "images.detectColor($MISSING, 'nocolor', 0, 0)" to RANGE,
            "images.detectColor($MISSING, '#FF0000', -1.5e10, 0)" to RANGE,
            "images.detectMultiColors($MISSING, 0, 0, '#FF0000', [[0, 0]])" to TYPE,
            "images.detectMultiColors($MISSING, 0, 0, '#FF0000', [5])" to TYPE,
            "images.findMultiColors($MISSING, '#FF0000', [[0, 'a', '#FF0000']])" to TYPE,
            "images.findMultiColors($MISSING, '#FF0000', [[0, 0, 'nocolor']])" to RANGE,
            "images.findColor($MISSING, '#FF0000', { threshold: 4, similarity: 0.9 })" to TYPE,
            "images.findColor($MISSING, '#FF0000', { threshold: '4' })" to TYPE,
            "images.findColor($MISSING, '#FF0000', { similarity: NaN })" to RANGE,
            "images.findColor($MISSING, '#FF0000', { region: [0, 'a'] })" to TYPE,
            "images.findColor($MISSING, '#FF0000', { region: [-2, 0] })" to RANGE,
            "images.findColor($MISSING, '#FF0000', { region: 5 })" to TYPE,
            "images.findColorInRegion($MISSING, '#FF0000', 0, 0, 10, 10, 'x')" to TYPE,
            "images.findPointsByColor($MISSING, '#FF0000', { threshold: {} })" to TYPE,
            "images.findImage($MISSING, $MISSING, { scales: [] })" to RANGE,
            "images.findImage($MISSING, $MISSING, { scale: -1 })" to RANGE,
            "images.findImage($MISSING, $MISSING, { scales: [1, 'x'] })" to TYPE,
            "images.findImage($MISSING, $MISSING, { level: 'x' })" to TYPE,
            "images.findImage($MISSING, $MISSING, { threshold: 0.1, similarity: 0.9 })" to TYPE,
            "images.findImageInRegion($MISSING, $MISSING, 0, 0, 10, 10, '0.9')" to TYPE,
            "images.matchTemplate($MISSING, $MISSING, { max: NaN })" to RANGE,
            "images.matchTemplate($MISSING, $MISSING, { useTransparentMask: 1 })" to TYPE,
            "images.matchFeatures($MISSING, $MISSING, { matcher: 'nope' })" to RANGE,
            "images.matchFeatures($MISSING, $MISSING, { matcher: 7 })" to RANGE,
            "images.matchFeatures($MISSING, $MISSING, { threshold: 2 })" to RANGE,
            "images.matchFeatures($MISSING, $MISSING, { ransacThreshold: 0 })" to RANGE,
            "images.matchFeatures($MISSING, $MISSING, { drawMatches: 5 })" to TYPE,
            "images.matchFeatures($MISSING, $MISSING, { method: 'SURF' })" to RANGE,
            "images.detectAndComputeFeatures($MISSING, { method: 3 })" to RANGE,
            "images.matchFeatures($MISSING, $MISSING, { method: 0 })" to RANGE,
            "images.detectAndComputeFeatures($MISSING, { maxFeatures: -1 })" to RANGE,
            "images.detectAndComputeFeatures($MISSING, { scale: 9 })" to RANGE,
            "images.detectAndComputeFeatures($MISSING, { grayscale: 'yes' })" to TYPE,
            "images.quantize($MISSING, { maxColors: 'x' })" to TYPE,
            "images.quantize($MISSING, { minQuality: 90, maxQuality: 10 })" to RANGE,
            "images.quantize($MISSING, { preserveAlpha: 1 })" to TYPE,
            "images.quantize($MISSING, { maxPixels: 0 })" to RANGE,
            "images.save($MISSING, 'aj6-p31-out.png', 'gif')" to RANGE,
            "images.save($MISSING, 'aj6-p31-out.png', 'jpg', {})" to TYPE,
            "images.save($MISSING, 'aj6-p31-out.png', 'png', { speed: 1.5e10 })" to RANGE,
            "images.toBase64($MISSING, 'gif')" to RANGE,
            "images.compress($MISSING, 'webp', { maxColors: 16 })" to TYPE,
            "images.getSimilarity($MISSING, $MISSING, { metric: 'abc' })" to RANGE,
            "images.getSimilarity($MISSING, $MISSING, { metric: null })" to TYPE,
            "images.getSimilarity($MISSING, $MISSING, { type: 5 })" to TYPE,
            "images.downsample($MISSING, 0, 10)" to RANGE,
            "images.downsample($MISSING, 10, -1)" to RANGE,
            "images.fromBytes(['a'])" to TYPE,
            "images.fromBytes([1, NaN])" to RANGE,
            "images.__buildRegion([0, 0, 100, 1], 64, 48)" to RANGE,
            "images.__buildRegion(null, -1, 5)" to RANGE,
            "images.__buildRegion([-1, 0], 64, 48)" to RANGE,
            "images.clip($MISSING, -1, 0, 1, 1)" to RANGE,
            "images.clip($MISSING, 0, 0, 0, 1)" to RANGE,
            "images.clip($MISSING, [0, 0, 0, 5])" to RANGE,
            "images.getMeanColor($MISSING, [0, 0, 'w'])" to TYPE,
        )

        /** Argument errors for which the current module requests a screen capture or rejects a promise (JVM only). */
        private val JVM_ONLY_ERRORS = listOf(
            "images.captureScreen(5)" to TYPE,
            "images.captureScreen('')" to RANGE,
            "images.captureScreen('a', 'b')" to TYPE,
            "captureScreen(5)" to TYPE,
            "images.requestScreenCapture(5)" to TYPE,
            "images.requestScreenCapture('landscape')" to TYPE,
            "images.requestScreenCapture(100, 'h')" to TYPE,
            "images.requestScreenCapture(1, 2, 3)" to TYPE,
            "requestScreenCapture([])" to TYPE,
            "images.requestScreenCaptureAsync(5)" to TYPE,
            "images.loadAsync(5)" to TYPE,
            "images.loadAsync('')" to RANGE,
            "images.requestScreenCapture({ orientation: 'up' })" to RANGE,
            "images.requestScreenCapture({ isAsync: 1 })" to TYPE,
            "images.requestScreenCapture({ width: 'w' })" to TYPE,
            "images.requestScreenCaptureAsync({ orientation: 3 })" to RANGE,
        )

        /** The functions on the generated image; both switch states must give these results (they call the same functions). */
        private val DEVICE_ONLY = listOf(
            "images.pixel($IMG, 12, 10)" to "-65536",
            "images.pixel($IMG, 0, 0)" to "-1",
            "[images.getWidth($IMG), images.getHeight($IMG)]" to "[64,48]",
            "(function () { var s = images.getSize($IMG); return [s.width, s.height]; })()" to "[64,48]",
            "images.getScreenCaptureOptions()" to "null",
            point("images.findColor($IMG, '#FF0000')") to "[10,8]",
            point("findColor($IMG, '#FF0000', { threshold: 0 })") to "[10,8]",
            point("images.findColorInRegion($IMG, '#FF0000', 15, 12, 20, 20)") to "[15,12]",
            point("images.findColorEquals($IMG, '#FF0000')") to "[10,8]",
            point("images.findColor($IMG, '#00FF00')") to "null",
            point("images.findMultiColors($IMG, '#FF0000', [[5, 5, '#FF0000']])") to "[10,8]",
            "images.findPointsByColor($IMG, '#FF0000').length" to "100",
            "images.countPointsByColor($IMG, '#FF0000')" to "100",
            "[images.detectColor($IMG, '#FF0000', 12, 10), images.detectsColor($IMG, '#FF0000', 0, 0)]" to "[true,false]",
            "images.getMeanColor($IMG, [10, 8, 10, 10])" to "-65536",
            probe("images.clip($IMG, 10, 8, 10, 10)", 0, 0) to "[10,10,-65536]",
            probe("images.clip($IMG, [10, 8, 10, 10])", 9, 9) to "[10,10,-65536]",
            probe("images.flip($IMG, 'horizontal')", 50, 10) to "[64,48,-65536]",
            probe("images.flip($IMG, false, true)", 12, 35) to "[64,48,-65536]",
            probe("images.resize($IMG, [32, 24])", 0, 0) to "[32,24,-1]",
            probe("images.scale($IMG, 0.5, 0.5)", 0, 0) to "[32,24,-1]",
            probe("images.concat($IMG, $IMG)", 76, 10) to "[128,48,-65536]",
            probe("images.copy($IMG)", 12, 10) to "[64,48,-65536]",
            probe("images.read($PNG)", 12, 10) to "[64,48,-65536]",
            probe("images.fromBase64(images.toBase64($IMG))", 12, 10) to "[64,48,-65536]",
            probe("images.fromBytes(images.toBytes($IMG))", 12, 10) to "[64,48,-65536]",
            probe("images.matToImage(images.imread($PNG))", 0, 0) to "[64,48,-1]",
            "[images.isGrayscale($IMG), images.isGrayscale(images.grayscale($IMG))]" to "[false,true]",
            "[images.isEqual($IMG, images.copy($IMG)), images.isEqual($IMG, images.invert($IMG))]" to "[true,false]",
            "(function () { var g = images.grayscale($IMG); return [images.threshold(g, 100, 255).width, images.adaptiveThreshold(g, 255, 'MEAN_C', 'BINARY', 3, 0).height]; })()" to "[64,48]",
            "[images.blur($IMG, 3).width, images.medianBlur($IMG, 3).width, images.gaussianBlur($IMG, [3, 3]).width, images.bilateralFilter(images.grayscale($IMG)).width, images.cvtColor($IMG, 'BGR2GRAY').width]" to
                "[64,64,64,64,64]",
            "(function () { var r = images.readPixels($IMG); return [r.width, r.height, r.data.length]; })()" to "[64,48,3072]",
            "(function () { var r = images.buildRegion($IMG, [10, 8]); return [r.x, r.y, r.width, r.height]; })()" to "[10,8,54,40]",
            "(function () { var r = images.__buildRegion([1, 2, 3, 4], 64, 48); return [r.x, r.y, r.width, r.height]; })()" to "[1,2,3,4]",
            point("images.findImage($IMG, images.clip($IMG, 8, 6, 14, 14))") to "[8,6]",
            "(function () { var c = images.copy($IMG); var before = images.isRecycled(c); images.recycle(c); return [before, images.isRecycled(c)]; })()" to "[false,true]",
            "images.save($IMG, '$DIR/aj6-p31-out.png', 'png', 100) && files.exists('$DIR/aj6-p31-out.png')" to "true",
            "images.compress($IMG, 'jpg', 50).width" to "64",
            "images.compressToBytes($IMG, 'jpg', 80).length > 0" to "true",
            "images.mse($IMG, images.copy($IMG))" to "0",
            "images.getSimilarity($IMG, images.copy($IMG), { metric: 'mse' })" to "0",
            "Array.isArray(images.findCircles(images.grayscale($IMG)))" to "true",
            "images.matchTemplate($IMG, images.clip($IMG, 8, 6, 14, 14), { max: 1 }).points.length" to "1",
            "typeof images.detectAndComputeFeatures($IMG)" to "\"object\"",
            "images.downsample($IMG, 32, 24).width <= 64" to "true",
            // Q28: names and values the argument checks accept (appendix C).
            "images.threshold(images.grayscale($IMG), 100, 255, 'binary').width" to "64",
            "images.threshold(images.grayscale($IMG), 100, 255, 'THRESH_BINARY_INV').width" to "64",
            "images.resize($IMG, [32, 24], 'inter-linear').width" to "32",
            "images.concat($IMG, $IMG, 'start').width" to "128",
            "[images.blur($IMG, 3, [1, 1]).width, images.blur($IMG, [3, 3], 1).width, images.blur($IMG, 3, { x: 1, y: 1 }, 'REFLECT').width]" to "[64,64,64]",
            "images.cvtColor($IMG, 'BGR2GRAY', 1).width" to "64",
            "[images.detectColor($IMG, '#FF0000', 12, 10, 4, 'rgb+'), images.detectColor($IMG, '#FF0000', 12, 10, 4, 'hs')]" to "[true,true]",
            point("images.findColor($IMG, '#FF0000', { region: [10, 8, null, undefined] })") to "[10,8]",
            point("images.findImage($IMG, images.clip($IMG, 8, 6, 14, 14), { scales: [1], level: 0 })") to "[8,6]",
            "typeof images.matchFeatures($IMG, images.copy($IMG), { matcher: 'BRUTEFORCE' })" to "\"object\"",
            "images.getSimilarity($IMG, images.copy($IMG), { type: 'MSE' })" to "0",
            "images.flip($IMG, { horizontal: true }).width" to "64",
            // Q29: the documented integers of the feature detection method (the current module failed on them).
            "typeof images.detectAndComputeFeatures($IMG, { method: 2, maxFeatures: 50 })" to "\"object\"",
            "typeof images.matchFeatures($IMG, images.copy($IMG), { method: 1 })" to "\"object\"",
            // Q28: a concatenated color text in the paths (the current module failed on it).
            point("(function () { var s = '#FF'; s = s + '0000'; return images.findMultiColors($IMG, '#FF0000', [[5, 5, s]]); })()") to "[10,8]",
            // 6.7.0: a similarity of 1 is an exact match (it was read as a percentage); region components of 1 are pixels.
            point("images.findColor($IMG, '#FF0000', { similarity: 1 })") to "[10,8]",
            point("images.findColor($IMG, '#FE0000', { similarity: 1 })") to "null",
            point("images.findColor($IMG, '#FE0000', { similarity: 0.98 })") to "[10,8]",
            point("images.findColor($IMG, '#FF0000', { region: [10, 8, 1, 1] })") to "[10,8]",
            point("images.findColor($IMG, '#FF0000', { region: [1, 1] })") to "[10,8]",
            point("images.findColorInRegion($IMG, '#FF0000', 10, 8, 1, 1)") to "[10,8]",
        )

        /**
         * Argument errors that need the size of an image (Q28: a pixel, a region or a clip beyond it), on the generated
         * image; the current module failed in Android or OpenCV with other errors for them.
         */
        private val DEVICE_ERRORS = listOf(
            "images.pixel($IMG, 64, 0)" to RANGE,
            "images.pixel($IMG, 0, -1)" to RANGE,
            "images.clip($IMG, 60, 0, 10, 10)" to RANGE,
            "images.clip($IMG, [0, 0, 65, 1])" to RANGE,
            "images.getMeanColor($IMG, [60, 0, 10, 10])" to RANGE,
            "images.findColor($IMG, '#FF0000', { region: [0, 40, 10, 10] })" to RANGE,
            "images.findColorInRegion($IMG, '#FF0000', 60, 0, 10, 10)" to RANGE,
            "images.findImage($IMG, images.clip($IMG, 0, 0, 4, 4), { region: [0, 0, 100] })" to RANGE,
            "images.detectColor($IMG, '#FF0000', 99, 0)" to RANGE,
            "images.detectMultiColors($IMG, 0, 48, '#FF0000', [])" to RANGE,
            // WEBP lossless takes the quality 100 alone on Android 11+ (Images.saveInternal); older versions accept any.
            "images.compress($IMG, device.sdkInt >= 30 ? 'webp_lossless' : 'gif', 60)" to RANGE,
        )

    }

}
