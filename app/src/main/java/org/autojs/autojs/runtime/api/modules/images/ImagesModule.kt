package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.images.ImagesCall.Companion.ABSENT
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.autojs.autojs.core.opencv.Mat as AutoJsMat
import org.opencv.core.Point as OpencvPoint
import org.opencv.core.Rect as OpencvRect
import org.opencv.core.Size as OpencvSize

/**
 * The `images` module on the new script module layer (roadmap P3.1): an event emitter (`emitter = true`, the methods of
 * `events.emitter()` end the prototype chain, as with the current AsEmitter; asynchronous screen capture emits
 * `screen_capture_available` on it), the functions of the current `augment/images/Images` in its order, nine of them
 * also global (`captureScreen`, `requestScreenCapture(Async)`, `findColor`, `findColorInRegion`, `findColorEquals`,
 * `findMultiColors`, `findImage`, `findImageInRegion`), and the Java class `ObjectFrame`. Each function hands the
 * checked arguments to the current function of the same name, which reads the images, colors, regions and options and
 * returns the current objects as before (appendix C Q24; to be moved out before P5.2). Deprecated aliases stay
 * function objects of their own, as before.
 *
 * Arguments follow the real signatures (T4, appendix C Q7) and are checked first ([ImagesCall], D10): an image that is
 * neither an ImageWrapper nor a path, a number argument without a number, options that are not a plain object (the
 * current module ignored them in the find / detect / feature functions) and a `paths` that is not an array are
 * TypeErrors, a blank path and NaN RangeErrors; `clip` takes two or five arguments (the current module failed on three
 * or four), and an argument of `requestScreenCapture` that is neither a boolean nor an options object is a TypeError
 * (the current module ignored it). Colors, the parts of regions and pixels, constant names, sizes and the documented
 * fields of the options are checked as well (appendix C Q28: [ColorValues][org.autojs.autojs.runtime.api.modules.colors.ColorValues],
 * [ImageRegions], [ImageConstant], [ImageOptions]; the differences from the current module are listed in appendix E).
 * Errors of the current functions keep their types (T5).
 *
 * zh-CN: 新脚本模块层上的 images 模块 (P3.1): 事件发射器 (emitter = true, events.emitter() 的方法位于原型链末尾, 同现有 AsEmitter; 异步截屏在其上发出
 * screen_capture_available), 函数同现有 augment/images/Images 且顺序照旧, 其中九个同时位于全局 (captureScreen, requestScreenCapture(Async), findColor,
 * findColorInRegion, findColorEquals, findMultiColors, findImage, findImageInRegion), 以及 Java 类 ObjectFrame. 各函数把检查后的参数交给现有同名函数, 由其
 * 照旧读取图片, 颜色, 区域与选项并返回现有对象 (附录 C Q24; P5.2 前迁出). 已弃用的别名仍是各自独立的函数对象, 同现有模块. 参数按真实签名 (T4, 附录 C Q7) 并先行
 * 检查 (ImagesCall, D10): 图片既不是 ImageWrapper 也不是路径, 数值参数不是数值, 选项不是普通对象 (现有模块在查找 / 检测 / 特征函数中忽略它), paths 不是数组
 * 均为 TypeError, 空白路径与 NaN 为 RangeError; clip 接受两个或五个参数 (现有模块在三个或四个参数时失败), requestScreenCapture 的参数既不是布尔值也不是选项
 * 对象时为 TypeError (现有模块忽略它). 颜色, 区域与像素的各分量, 常量名, 尺寸以及选项的文档字段同样先行检查 (附录 C Q28: ColorValues, ImageRegions,
 * ImageConstant, ImageOptions; 与现有模块的差异列在附录 E). 现有函数的错误保留其类型 (T5).
 */
@Suppress("DEPRECATION")
@ScriptModule(name = "images", emitter = true)
@ScriptJavaClass(name = "ObjectFrame", type = ObjectFrame::class)
class ImagesModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun initOpenCvIfNeeded(): Undefined = Images.initOpenCvIfNeeded(runtime, emptyArray())

    @ScriptFunction
    fun read(path: Any?, isStrict: Any? = ABSENT): ImageWrapper? =
        Images.read(runtime, call("read", path, isStrict).path(0).booleanOrNullish(1, "isStrict").args)

    @ScriptFunction
    fun imread(path: Any?): AutoJsMat = Images.imread(runtime, call("imread", path).path(0).args)

    @ScriptFunction
    fun copy(image: Any?): ImageWrapper = Images.copy(runtime, call("copy", image).image(0).args)

    @ScriptFunction
    fun load(src: Any?): ImageWrapper? = Images.load(runtime, call("load", src).path(0, "src").args)

    @ScriptFunction
    fun loadAsync(src: Any?): Scriptable = Images.loadAsync(runtime, call("loadAsync", src).path(0, "src").args)

    @ScriptFunction
    fun clip(image: Any?, region: Any?, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT): ImageWrapper =
        Images.clip(runtime, call("clip", image, region, y, width, height).clip().args)

    @ScriptFunction
    fun pixel(image: Any?, x: Any?, y: Any?): Any? = Images.pixel(runtime, call("pixel", image, x, y).image(0).pixel(1, 2).args)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun captureScreen(path: Any? = ABSENT): Any = Images.captureScreen(runtime, call("captureScreen", path).pathOrNullish(0).args)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun requestScreenCapture(first: Any? = ABSENT, second: Any? = ABSENT): Boolean =
        Images.requestScreenCapture(runtime, call("requestScreenCapture", first, second).screenCapture().args)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun requestScreenCaptureAsync(first: Any? = ABSENT, second: Any? = ABSENT): Scriptable =
        Images.requestScreenCaptureAsync(runtime, call("requestScreenCaptureAsync", first, second).screenCapture().args)

    @ScriptFunction
    fun stopScreenCapture(): Any? = Images.stopScreenCapture(runtime, emptyArray())

    @ScriptFunction
    fun getScreenCaptureOptions(): ScreenCapturer.Options? = Images.getScreenCaptureOptions(runtime, emptyArray())

    @ScriptFunction
    fun save(image: Any?, path: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): Boolean =
        Images.save(runtime, saving("save", image, path, format, quality))

    @ScriptFunction
    fun saveImage(image: Any?, path: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): Boolean =
        Images.saveImage(runtime, saving("saveImage", image, path, format, quality))

    @ScriptFunction
    fun invert(image: Any?): ImageWrapper = Images.invert(runtime, call("invert", image).image(0).args)

    @ScriptFunction
    fun grayscale(image: Any?, dstCn: Any? = ABSENT): ImageWrapper =
        Images.grayscale(runtime, call("grayscale", image, dstCn).image(0).intOrNullish(1, "dstCn").args)

    @ScriptFunction
    fun isGrayscale(image: Any?): Boolean = Images.isGrayscale(runtime, call("isGrayscale", image).matOrImage(0, "image").args)

    @ScriptFunction
    fun threshold(image: Any?, threshold: Any?, maxVal: Any?, type: Any? = ABSENT): ImageWrapper =
        Images.threshold(runtime, call("threshold", image, threshold, maxVal, type).image(0).number(1, "threshold").number(2, "maxVal").constantOrNullish(3, "type", ImageConstant.THRESHOLD_TYPE).args)

    @ScriptFunction
    fun inRange(image: Any?, lowerBound: Any?, upperBound: Any?): ImageWrapper =
        Images.inRange(runtime, call("inRange", image, lowerBound, upperBound).image(0).color(1, "lowerBound").color(2, "upperBound").args)

    @ScriptFunction
    fun interval(image: Any?, color: Any?, threshold: Any?): ImageWrapper =
        Images.interval(runtime, call("interval", image, color, threshold).image(0).color(1).number(2, "threshold").args)

    @Suppress("LocalVariableName")
    @ScriptFunction
    fun adaptiveThreshold(image: Any?, maxValue: Any?, adaptiveMethod: Any?, thresholdType: Any?, blockSize: Any?, C: Any?): ImageWrapper =
        Images.adaptiveThreshold(
            runtime,
            call("adaptiveThreshold", image, maxValue, adaptiveMethod, thresholdType, blockSize, C).image(0).number(1, "maxValue")
                .constant(2, "adaptiveMethod", ImageConstant.ADAPTIVE_METHOD).constant(3, "thresholdType", ImageConstant.ADAPTIVE_TYPE).int(4, "blockSize").number(5, "C").args,
        )

    @ScriptFunction
    fun blur(image: Any?, size: Any?, anchor: Any? = ABSENT, type: Any? = ABSENT): ImageWrapper =
        Images.blur(runtime, call("blur", image, size, anchor, type).image(0).ksize(1).anchor(2).constantOrNullish(3, "type", ImageConstant.BORDER_TYPE).args)

    @ScriptFunction
    fun medianBlur(image: Any?, size: Any?): ImageWrapper = Images.medianBlur(runtime, call("medianBlur", image, size).image(0).medianSize(1).args)

    @ScriptFunction
    fun gaussianBlur(image: Any?, size: Any?, sigmaX: Any? = ABSENT, sigmaY: Any? = ABSENT, type: Any? = ABSENT): ImageWrapper =
        Images.gaussianBlur(
            runtime,
            call("gaussianBlur", image, size, sigmaX, sigmaY, type).image(0).ksize(1).numbersOrNullish(2, "sigmaX", "sigmaY").constantOrNullish(4, "type", ImageConstant.BORDER_TYPE).args,
        )

    @ScriptFunction
    fun bilateralFilter(image: Any?, d: Any? = ABSENT, sigmaColor: Any? = ABSENT, sigmaSpace: Any? = ABSENT, borderType: Any? = ABSENT): ImageWrapper =
        Images.bilateralFilter(
            runtime,
            call("bilateralFilter", image, d, sigmaColor, sigmaSpace, borderType).image(0).numbersOrNullish(1, "d", "sigmaColor", "sigmaSpace").constantOrNullish(4, "borderType", ImageConstant.BORDER_TYPE).args,
        )

    @ScriptFunction
    fun cvtColor(image: Any?, code: Any?, dstCn: Any? = ABSENT): ImageWrapper =
        Images.cvtColor(runtime, call("cvtColor", image, code, dstCn).image(0).constant(1, "code", ImageConstant.CVT_CODE).intOrNullish(2, "dstCn").args)

    @ScriptFunction
    fun findCircles(image: Any?, options: Any? = ABSENT): List<Map<String, Double>> =
        Images.findCircles(runtime, call("findCircles", image, options).image(0).options(1) { ImageOptions.circles("images.findCircles", it, image) }.args)

    @ScriptFunction
    fun resize(image: Any?, size: Any?, interpolation: Any? = ABSENT): ImageWrapper =
        Images.resize(runtime, call("resize", image, size, interpolation).image(0).resizeSize(1).constantOrNullish(2, "interpolation", ImageConstant.INTERPOLATION).args)

    @ScriptFunction
    fun scale(image: Any?, fx: Any?, fy: Any?, interpolation: Any? = ABSENT): ImageWrapper =
        Images.scale(runtime, call("scale", image, fx, fy, interpolation).image(0).number(1, "fx").number(2, "fy").constantOrNullish(3, "interpolation", ImageConstant.INTERPOLATION).args)

    @ScriptFunction
    fun rotate(image: Any?, degree: Any?, x: Any? = ABSENT, y: Any? = ABSENT): ImageWrapper =
        Images.rotate(runtime, call("rotate", image, degree, x, y).image(0).number(1, "degree").numbersOrNullish(2, "x", "y").args)

    @ScriptFunction
    fun flip(image: Any?, first: Any? = ABSENT, second: Any? = ABSENT): ImageWrapper = Images.flip(runtime, call("flip", image, first, second).flip().args)

    @ScriptFunction
    fun concat(imageA: Any?, imageB: Any?, direction: Any? = ABSENT): ImageWrapper =
        Images.concat(runtime, call("concat", imageA, imageB, direction).image(0, "imageA").image(1, "imageB").constantOrNullish(2, "direction", ImageConstant.CONCAT_DIRECTION).args)

    @ScriptFunction
    fun detectColor(image: Any?, color: Any?, x: Any?, y: Any?, threshold: Any? = ABSENT, algorithm: Any? = ABSENT): Boolean =
        Images.detectColor(runtime, detectingColor("detectColor", image, color, x, y, threshold, algorithm))

    @ScriptFunction
    fun detectsColor(image: Any?, color: Any?, x: Any?, y: Any?, threshold: Any? = ABSENT, algorithm: Any? = ABSENT): Boolean =
        Images.detectsColor(runtime, detectingColor("detectsColor", image, color, x, y, threshold, algorithm))

    @ScriptFunction
    fun detectMultiColors(image: Any?, x: Any?, y: Any?, firstColor: Any?, paths: Any?, options: Any? = ABSENT): Boolean =
        Images.detectMultiColors(runtime, detectingColors("detectMultiColors", image, x, y, firstColor, paths, options))

    @ScriptFunction
    fun detectsMultiColors(image: Any?, x: Any?, y: Any?, firstColor: Any?, paths: Any?, options: Any? = ABSENT): Boolean =
        Images.detectsMultiColors(runtime, detectingColors("detectsMultiColors", image, x, y, firstColor, paths, options))

    @ScriptFunction
    fun findPointByColor(image: Any?, color: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT, threshold: Any? = ABSENT): OpencvPoint? =
        Images.findPointByColor(runtime, findingColor("findPointByColor", image, color, x, y, width, height, threshold))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findColor(image: Any?, color: Any?, options: Any? = ABSENT): OpencvPoint? = Images.findColor(runtime, findingColor("findColor", image, color, options))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findColorInRegion(image: Any?, color: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT, threshold: Any? = ABSENT): OpencvPoint? =
        Images.findColorInRegion(runtime, findingColor("findColorInRegion", image, color, x, y, width, height, threshold))

    @ScriptFunction
    fun findPointByColorExactly(image: Any?, color: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT): OpencvPoint? =
        Images.findPointByColorExactly(runtime, findingColor("findPointByColorExactly", image, color, x, y, width, height, withThreshold = false))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findColorEquals(image: Any?, color: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT): OpencvPoint? =
        Images.findColorEquals(runtime, findingColor("findColorEquals", image, color, x, y, width, height, withThreshold = false))

    @ScriptFunction
    fun findPointsByColor(image: Any?, color: Any?, options: Any? = ABSENT): NativeArray =
        Images.findPointsByColor(runtime, colorPoints("findPointsByColor", image, color, options))

    @ScriptFunction
    fun findAllPointsForColor(image: Any?, color: Any?, options: Any? = ABSENT): NativeArray =
        Images.findAllPointsForColor(runtime, colorPoints("findAllPointsForColor", image, color, options))

    @ScriptFunction
    fun findPointByColors(image: Any?, firstColor: Any?, paths: Any?, options: Any? = ABSENT): OpencvPoint? =
        Images.findPointByColors(runtime, findingColors("findPointByColors", image, firstColor, paths, options))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findMultiColors(image: Any?, firstColor: Any?, paths: Any?, options: Any? = ABSENT): OpencvPoint? =
        Images.findMultiColors(runtime, findingColors("findMultiColors", image, firstColor, paths, options))

    @ScriptFunction
    fun findPointsByColors(image: Any?, firstColor: Any?, paths: Any?, options: Any? = ABSENT): NativeArray =
        Images.findPointsByColors(runtime, findingColors("findPointsByColors", image, firstColor, paths, options))

    @ScriptFunction
    fun countPointsByColor(image: Any?, color: Any?, options: Any? = ABSENT): Int =
        Images.countPointsByColor(runtime, colorPoints("countPointsByColor", image, color, options))

    @ScriptFunction
    fun getMeanColor(image: Any?, region: Any? = ABSENT): Int = Images.getMeanColor(runtime, call("getMeanColor", image, region).image(0).region(1, nullable = true).args)

    @ScriptFunction
    fun findPointByImage(image: Any?, template: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT, threshold: Any? = ABSENT): OpencvPoint? =
        Images.findPointByImage(runtime, findingImage("findPointByImage", image, template, x, y, width, height, threshold))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findImage(image: Any?, template: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT, threshold: Any? = ABSENT): OpencvPoint? =
        Images.findImage(runtime, findingImage("findImage", image, template, x, y, width, height, threshold))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun findImageInRegion(image: Any?, template: Any?, x: Any? = ABSENT, y: Any? = ABSENT, width: Any? = ABSENT, height: Any? = ABSENT, threshold: Any? = ABSENT): OpencvPoint? =
        Images.findImageInRegion(runtime, findingImage("findImageInRegion", image, template, x, y, width, height, threshold))

    @ScriptFunction
    fun matchTemplate(image: Any?, template: Any?, options: Any? = ABSENT): MatchingResult =
        Images.matchTemplate(
            runtime,
            call("matchTemplate", image, template, options).image(0).image(1, "template").options(2) { ImageOptions.templates("images.matchTemplate", it, image, matching = true) }.args,
        )

    @ScriptFunction
    fun fromBase64(base64: Any?): ImageWrapper = Images.fromBase64(runtime, call("fromBase64", base64).string(0, "base64").args)

    @ScriptFunction
    fun toBase64(image: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): String =
        Images.toBase64(runtime, call("toBase64", image, format, quality).image(0).formatOrNullish(1).quality(2, format = 1, withOptions = false).args)

    @ScriptFunction
    fun fromBytes(bytes: Any?): ImageWrapper = Images.fromBytes(runtime, call("fromBytes", bytes).bytes(0).args)

    @ScriptFunction
    fun toBytes(image: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): ByteArray =
        Images.toBytes(runtime, call("toBytes", image, format, quality).image(0).formatOrNullish(1).quality(2, format = 1, withOptions = false).args)

    @ScriptFunction
    fun readPixels(image: Any?): NativeObject = Images.readPixels(runtime, call("readPixels", image).image(0).args)

    @ScriptFunction
    fun matToImage(mat: Any?): ImageWrapper = Images.matToImage(runtime, call("matToImage", mat).mat(0).args)

    @ScriptFunction
    fun detectAndComputeFeatures(image: Any?, options: Any? = ABSENT): ImageFeatures =
        Images.detectAndComputeFeatures(runtime, call("detectAndComputeFeatures", image, options).image(0).options(1) { ImageOptions.features("images.detectAndComputeFeatures", it, image) }.featureMethod(1).args)

    @ScriptFunction
    fun matchFeatures(scene: Any?, `object`: Any?, options: Any? = ABSENT): ObjectFrame? =
        Images.matchFeatures(
            runtime,
            call("matchFeatures", scene, `object`, options).features(0, "scene").features(1, "object").options(2) { ImageOptions.matching("images.matchFeatures", it, scene, `object`) }.featureMethod(2).args,
        )

    @ScriptFunction
    fun isRecycled(vararg images: Any?): Boolean = Images.isRecycled(runtime, images)

    @ScriptFunction
    fun recycle(vararg images: Any?): Boolean = Images.recycle(runtime, images)

    @ScriptFunction
    fun quantize(image: Any?, options: Any? = ABSENT): PngQuantizationResult =
        Images.quantize(runtime, call("quantize", image, options).image(0).options(1) { ImageOptions.quantization("images.quantize", it, "options") }.args)

    @ScriptFunction
    fun quantizeToFile(image: Any?, path: Any?, options: Any? = ABSENT): PngQuantizationFileResult =
        Images.quantizeToFile(runtime, call("quantizeToFile", image, path, options).image(0).path(1).options(2) { ImageOptions.quantization("images.quantizeToFile", it, "options") }.args)

    @ScriptFunction
    fun compress(image: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): ImageWrapper =
        Images.compress(runtime, call("compress", image, format, quality).image(0).formatOrNullish(1).quality(2, format = 1, withOptions = true).args)

    @ScriptFunction
    fun compressToBytes(image: Any?, format: Any? = ABSENT, quality: Any? = ABSENT): ByteArray =
        Images.compressToBytes(runtime, call("compressToBytes", image, format, quality).image(0).formatOrNullish(1).quality(2, format = 1, withOptions = true).args)

    @ScriptFunction
    fun downsample(src: Any?, reqWidth: Any?, reqHeight: Any?, withAlpha: Any? = ABSENT): ImageWrapper =
        Images.downsample(
            runtime,
            call("downsample", src, reqWidth, reqHeight, withAlpha).downsampleSource(0).positiveInt(1, "reqWidth").positiveInt(2, "reqHeight").booleanOrNullish(3, "withAlpha").args,
        )

    @ScriptFunction
    fun getSize(src: Any?): OpencvSize = Images.getSize(runtime, call("getSize", src).sizeSource(0).args)

    @ScriptFunction
    fun getWidth(src: Any?): Double = Images.getWidth(runtime, call("getWidth", src).sizeSource(0).args)

    @ScriptFunction
    fun getHeight(src: Any?): Double = Images.getHeight(runtime, call("getHeight", src).sizeSource(0).args)

    @ScriptFunction
    fun buildRegion(image: Any?, region: Any?): OpencvRect = Images.buildRegion(runtime, call("buildRegion", image, region).image(0).region(1, nullable = true).args)

    @ScriptFunction
    fun __buildRegion(region: Any? = ABSENT, imageWidth: Any? = ABSENT, imageHeight: Any? = ABSENT): OpencvRect? =
        Images.__buildRegion(
            runtime,
            call("__buildRegion", region, imageWidth, imageHeight).region(0, nullable = true, bounds = false).numbersOrNullish(1, "imageWidth", "imageHeight")
                .also { ImageRegions.dimensions("images.__buildRegion", it.args.getOrNull(0), it.args.getOrNull(1), it.args.getOrNull(2)) }.args,
        )

    @ScriptFunction
    fun psnr(imageA: Any?, imageB: Any?): Double = Images.psnr(runtime, comparing("psnr", imageA, imageB))

    @ScriptFunction
    fun ssim(imageA: Any?, imageB: Any?): Double = Images.ssim(runtime, comparing("ssim", imageA, imageB))

    @ScriptFunction
    fun mssim(imageA: Any?, imageB: Any?): Double = Images.mssim(runtime, comparing("mssim", imageA, imageB))

    @ScriptFunction
    fun hist(imageA: Any?, imageB: Any?): Double = Images.hist(runtime, comparing("hist", imageA, imageB))

    @ScriptFunction
    fun mse(imageA: Any?, imageB: Any?): Double = Images.mse(runtime, comparing("mse", imageA, imageB))

    @ScriptFunction
    fun ncc(imageA: Any?, imageB: Any?): Double = Images.ncc(runtime, comparing("ncc", imageA, imageB))

    @ScriptFunction
    fun isEqual(imageA: Any?, imageB: Any?): Boolean = Images.isEqual(runtime, call("isEqual", imageA, imageB).image(0, "imageA").image(1, "imageB").args)

    @ScriptFunction
    fun getSimilarity(imageA: Any?, imageB: Any?, options: Any? = ABSENT): Double =
        Images.getSimilarity(runtime, call("getSimilarity", imageA, imageB, options).matOrImage(0, "imageA").matOrImage(1, "imageB")
                .options(2) { ImageOptions.similarity("images.getSimilarity", it) }.args)

    private companion object {

        fun call(name: String, vararg values: Any?) = ImagesCall(name, *values)

        fun saving(name: String, image: Any?, path: Any?, format: Any?, quality: Any?) =
            call(name, image, path, format, quality).image(0).path(1).formatOrNullish(2).quality(3, format = 2, withOptions = true).args

        fun detectingColor(name: String, image: Any?, color: Any?, x: Any?, y: Any?, threshold: Any?, algorithm: Any?) =
            call(name, image, color, x, y, threshold, algorithm).image(0).color(1).pixel(2, 3).intOrNullish(4, "threshold")
                .constantOrNullish(5, "algorithm", ImageConstant.COLOR_ALGORITHM).args

        fun detectingColors(name: String, image: Any?, x: Any?, y: Any?, firstColor: Any?, paths: Any?, options: Any?) =
            call(name, image, x, y, firstColor, paths, options).image(0).pixel(1, 2).color(3, "firstColor").paths(4)
                .options(5) { ImageOptions.colors("images.$name", it, image) }.args

        fun findingColor(name: String, vararg values: Any?, withThreshold: Boolean = true) =
            call(name, *values).image(0).color(1).colorOptionsOrRegion(2, withThreshold).args

        fun findingColors(name: String, image: Any?, firstColor: Any?, paths: Any?, options: Any?) =
            call(name, image, firstColor, paths, options).image(0).color(1, "firstColor").paths(2).options(3) { ImageOptions.colors("images.$name", it, image) }.args

        fun colorPoints(name: String, image: Any?, color: Any?, options: Any?) =
            call(name, image, color, options).image(0).color(1).options(2) { ImageOptions.colors("images.$name", it, image) }.args

        fun findingImage(name: String, vararg values: Any?) =
            call(name, *values).image(0).image(1, "template").templateOptionsOrRegion(2).args

        fun comparing(name: String, imageA: Any?, imageB: Any?) = call(name, imageA, imageB).matOrImage(0, "imageA").matOrImage(1, "imageB").args

    }

}
