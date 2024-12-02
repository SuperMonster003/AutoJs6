package org.autojs.autojs.runtime.api.augment.images

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.autojs.autojs.extension.AnyExtensions.isJsArray
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.toNativeObject
import org.autojs.autojs.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.runtime.api.ImageSimilarity
import org.autojs.autojs.runtime.api.Images.initOpenCvIfNeeded
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.AsEmitter
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringLowercase
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.contains
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect
import org.autojs.autojs.core.opencv.Mat as AutoJsMat
import org.autojs.autojs.runtime.api.Images as ApiImages
import org.opencv.core.Core as OpencvCore
import org.opencv.core.Mat as OpencvMat
import org.opencv.core.Point as OpencvPoint
import org.opencv.core.Rect as OpencvRect
import org.opencv.core.Scalar as OpencvScalar
import org.opencv.core.Size as OpencvSize

@Suppress("SameParameterValue", "unused", "UNUSED_PARAMETER")
class Images(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), AsEmitter {

    @Suppress("DEPRECATION")
    override val selfAssignmentFunctions = listOf(
        ::read.name,
        ::imread.name,
        ::copy.name,
        ::load.name,
        ::clip.name,
        ::pixel.name,
        ::captureScreen.name to AS_GLOBAL,
        ::requestScreenCapture.name to AS_GLOBAL,
        ::requestScreenCaptureAsync.name to AS_GLOBAL,
        ::stopScreenCapture.name,
        ::getScreenCaptureOptions.name,
        ::save.name,
        ::saveImage.name,
        ::invert.name,
        ::grayscale.name,
        ::isGrayscale.name,
        ::threshold.name,
        ::inRange.name,
        ::interval.name,
        ::adaptiveThreshold.name,
        ::blur.name,
        ::medianBlur.name,
        ::gaussianBlur.name,
        ::bilateralFilter.name,
        ::cvtColor.name,
        ::findCircles.name,
        ::resizeInternal.name,
        ::scale.name,
        ::rotate.name,
        ::concat.name,
        ::detectColor.name,
        ::detectsColor.name,
        ::detectMultiColors.name,
        ::detectsMultiColors.name,
        ::findPointByColor.name,
        ::findColor.name to AS_GLOBAL,
        ::findColorInRegion.name to AS_GLOBAL,
        ::findPointByColorExactly.name,
        ::findColorEquals.name to AS_GLOBAL,
        ::findPointsByColor.name,
        ::findAllPointsForColor.name,
        ::findPointByColors.name,
        ::findMultiColors.name to AS_GLOBAL,
        ::findPointsByColors.name,
        ::findPointByImage.name,
        ::findImage.name to AS_GLOBAL,
        ::findImageInRegion.name to AS_GLOBAL,
        ::matchTemplate.name,
        ::fromBase64.name,
        ::toBase64.name,
        ::fromBytes.name,
        ::toBytes.name,
        ::readPixels.name,
        ::matToImage.name,
        ::detectAndComputeFeatures.name,
        ::matchFeatures.name,
        ::isRecycled.name,
        ::recycle.name,
        ::compress.name,
        ::getSize.name,
        ::getWidth.name,
        ::getHeight.name,
        ::buildRegion.name,
        ::psnr.name,
        ::ssim.name,
        ::mssim.name,
        ::hist.name,
        ::mse.name,
        ::ncc.name,
        ::isEqual.name,
        ::getSimilarity.name,
    )

    @Suppress("MayBeConstant")
    companion object {

        @JvmField
        val DEFAULT_COLOR_THRESHOLD = 4

        @JvmField
        val DEFAULT_IMAGE_SAVE_QUALITY = 100

        @JvmField
        val DEFAULT_IMAGE_TO_BYTES_QUALITY = 100

        @JvmField
        val DEFAULT_IMAGE_TO_BASE64_QUALITY = 100

        @JvmField
        val DEFAULT_COLOR_ALGORITHM = "diff"

        @JvmField
        val DEFAULT_IMAGE_SIMILARITY_METRIC = "mssim"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun read(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper? = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (path, isStrict) = argList
            when (argList.size) {
                2 -> scriptRuntime.images.read(coerceString(path), coerceBoolean(isStrict, false))
                1 -> scriptRuntime.images.read(coerceString(path))
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun imread(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AutoJsMat = ensureArgumentsOnlyOne(args) { path ->
            ApiImages.imread(scriptRuntime.files.path(coerceString(path)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun copy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.copy must be a ImageWrapper" }
            scriptRuntime.images.copy(image)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun load(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { src ->
            scriptRuntime.images.load(coerceString(src))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..5) {
            when (it.size) {
                5 -> {
                    val (o, x, y, w, h) = it
                    val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
                    require(image is ImageWrapper) { "Argument image for images.clip must be a ImageWrapper" }
                    scriptRuntime.images.clip(image, coerceIntNumber(x), coerceIntNumber(y), coerceIntNumber(w), coerceIntNumber(h))
                }
                2 -> {
                    val (o, region) = it
                    val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
                    require(image is ImageWrapper) { "Argument image for images.clip must be a ImageWrapper" }
                    when {
                        region.isJsNullish() -> image.apply { shoot() }
                        else -> {
                            val rect = buildRegion(scriptRuntime, arrayOf(image, region))
                            clip(scriptRuntime, arrayOf(image, rect.x, rect.y, rect.width, rect.height))
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Arguments length ${it.size} is unacceptable for images.clip")
            }
        }

        // @Overwrite by SuperMonster003 on Apr 19, 2022.
        //  ! Method org.autojs.autojs.runtime.api.Images.pixel is static.
        //  # util.__assignFunctions__(rtImages, images, [ ... , 'pixel']);
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun pixel(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsLength(args, 3) {
            val (o, x, y) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.pixel must be a ImageWrapper" }
            ApiImages.pixel(image, coerceIntNumber(x), coerceIntNumber(y))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun captureScreen(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsAtMost(args, 1) {
            val (path) = it
            val rtImages = scriptRuntime.images
            if (isBackgroundThread() && rtImages.screenCapturer == null) {
                requestScreenCapture(scriptRuntime, arrayOf())
            }
            when {
                path.isJsNullish() -> rtImages.captureScreen() as ImageWrapper
                else -> rtImages.captureScreen(scriptRuntime.files.path(coerceString(path)))
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestScreenCapture(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsAtMost(args, 2) { argList ->
            require(!isUiThread()) {
                "requestScreenCapture() called in ui thread, please use requestScreenCaptureAsync() instead"
            }
            val requestScreenCaptureBadge = scriptRuntime.threads.atomic(0)
            when {
                requestScreenCaptureBadge.get() > 0 -> true
                else -> {
                    requestScreenCaptureBadge.incrementAndGet()
                    val result = callFunction(scriptRuntime, scriptRuntime.js_ResultAdapter, "wait", arrayOf(run {
                        stopScreenCapture(scriptRuntime, emptyArray<Any?>())
                        requestScreenCaptureInternal(scriptRuntime, *argList)
                    })) as Boolean
                    requestScreenCaptureBadge.decrementAndGet()
                    result
                }
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestScreenCaptureAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsAtMost(args, 2) { argList ->
            callFunction(scriptRuntime, scriptRuntime.js_ResultAdapter, "promise", arrayOf(run {
                stopScreenCapture(scriptRuntime, emptyArray())
                requestScreenCaptureInternal(scriptRuntime, *argList)
            })) as NativeObject
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stopScreenCapture(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            scriptRuntime.images.stopScreenCapture()
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScreenCaptureOptions(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScreenCapturer.Options? = ensureArgumentsIsEmpty(args) {
            scriptRuntime.images.screenCaptureOptions
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun save(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, path, format, quality) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.save must be a ImageWrapper" }
            require(!path.isJsNullish()) { "Argument path for images.save must be non-nullish" }
            scriptRuntime.images.save(image, scriptRuntime.files.path(coerceString(path)), parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_SAVE_QUALITY))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun saveImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, path, format, quality) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.saveImage must be a ImageWrapper" }
            require(!path.isJsNullish()) { "Argument path for images.saveImage must be non-nullish" }
            scriptRuntime.images.save(image, scriptRuntime.files.path(coerceString(path)), parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_SAVE_QUALITY))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun invert(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.invert must be a ImageWrapper" }
            scriptRuntime.images.invert(image)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun grayscale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, dstCn) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.grayscale must be a ImageWrapper" }
            cvtColor(scriptRuntime, arrayOf(image, "BGR2GRAY", dstCn))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isGrayscale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            val mat = when (image) {
                is OpencvMat -> image
                is ImageWrapper -> image.mat
                else -> throw WrappedIllegalArgumentException("Argument image for images.isGrayscale must be a ImageWrapper or a Mat instead of ${image.jsBrief()}")
            }
            if (mat.channels() == 1) {
                return@ensureArgumentsOnlyOne true
            }
            // 检查每个像素的所有通道值是否相同
            val channels: List<OpencvMat> = mutableListOf()
            OpencvCore.split(mat, channels)
            for (i in 1 until channels.size) {
                val result = OpencvMat()
                OpencvCore.compare(channels[0], channels[i], result, OpencvCore.CMP_NE)
                if (OpencvCore.countNonZero(result) > 0) {
                    return@ensureArgumentsOnlyOne false
                }
            }
            return@ensureArgumentsOnlyOne true
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun threshold(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, threshold, maxVal, type) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.threshold must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            Imgproc.threshold(image.mat, mat, coerceNumber(threshold), coerceNumber(maxVal), parseThresholdType(type))
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun inRange(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 3) {
            val (o, lowerBound, upperBound) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.inRange must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val dst = AutoJsMat()
            OpencvCore.inRange(image.mat, parseScalar(Colors.toIntRhino(lowerBound)), parseScalar(Colors.toIntRhino(upperBound)), dst)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(dst))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun interval(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 3) {
            val (o, color, threshold) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.interval must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val (lowerBound, upperBound) = parseScalars(Colors.toIntRhino(color), coerceNumber(threshold))
            val dst = AutoJsMat()
            OpencvCore.inRange(image.mat, lowerBound, upperBound, dst)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(dst))
        }

        @Suppress("LocalVariableName")
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun adaptiveThreshold(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 6) {
            val (o, maxValue, adaptiveMethod, thresholdType, blockSize, C) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.adaptiveThreshold must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            val adaptiveThresholdValue = Imgproc::class.java.getField("ADAPTIVE_THRESH_${coerceString(adaptiveMethod).uppercase()}").getInt(null)
            val thresholdValue = Imgproc::class.java.getField("THRESH_${coerceString(thresholdType).uppercase()}").getInt(null)
            Imgproc.adaptiveThreshold(image.mat, mat, coerceNumber(maxValue), adaptiveThresholdValue, thresholdValue, coerceIntNumber(blockSize), coerceNumber(C))
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun blur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, ksize, anchor, type) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.blur must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            when (anchor) {
                null -> Imgproc.blur(image.mat, mat, parseSize(ksize))
                else -> Imgproc.blur(image.mat, mat, parseSize(ksize), S13n.point(arrayOf(anchor)).run {
                    OpencvPoint(x.toDouble(), y.toDouble())
                }, parseBorderType(type))
            }
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun medianBlur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 2) {
            val (o, size) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.medianBlur must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val ksize = when (size) {
                is NativeArray -> {
                    require(size.size == 2) {
                        "The argument size must be either a number or an array with the same TWO number elements"
                    }
                    require(size.all { el -> el is Number }) {
                        "The argument size must be either a number or an array with the same two NUMBER elements"
                    }
                    require(size[0] == size[1]) {
                        "The argument size must be either a number or an array with SAME two number elements"
                    }
                    coerceIntNumber(size[0])
                }
                is OpencvSize -> {
                    require(size.width == size.height) {
                        "The argument size must be a OpenCV size with same width and height"
                    }
                    coerceIntNumber(size.width)
                }
                else -> coerceIntNumber(size)
            }
            val mat = AutoJsMat()
            Imgproc.medianBlur(image.mat, mat, ksize)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gaussianBlur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..5) {
            val (o, size, sigmaX, sigmaY, type) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.gaussianBlur must be a ImageWrapper" }
            /* 按需初始化 OpenCV. */
            initOpenCvIfNeeded()
            /* Mat (矩阵) 对象. */
            val mat = AutoJsMat()
            /* sigmaX. */
            val x = parseNumber(sigmaX).toDouble()
            /* sigmaY. */
            val y = parseNumber(sigmaY).toDouble()
            /* 边缘点插值类型. */
            val borderType = parseBorderType(type)
            /* 高斯模糊. */
            Imgproc.GaussianBlur(image.mat, mat, parseSize(size), x, y, borderType)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun bilateralFilter(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..5) {
            val (o, d, sigmaColor, sigmaSpace, borderType) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.bilateralFilter must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            val dValue = parseNumber(d, 0)
            val sigmaColorValue = parseNumber(sigmaColor, 40).toDouble()
            val sigmaSpaceValue = parseNumber(sigmaSpace, 20).toDouble()
            Imgproc.bilateralFilter(image.mat, mat, dValue, sigmaColorValue, sigmaSpaceValue, parseBorderType(borderType))
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cvtColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, code, dstCn) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.cvtColor must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            val colorCode = Imgproc::class.java.getField("COLOR_$code").getInt(null)
            when (dstCn) {
                null -> Imgproc.cvtColor(image.mat, mat, colorCode)
                else -> Imgproc.cvtColor(image.mat, mat, colorCode, coerceIntNumber(dstCn))
            }
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findCircles(scriptRuntime: ScriptRuntime, args: Array<out Any?>): List<HoughCirclesResult> = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, optionsArg) = it
            val rawImage = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(rawImage is ImageWrapper) { "Argument grayImage for images.findCircles must be a ImageWrapper" }
            val grayImage = if (isGrayscale(scriptRuntime, arrayOf(rawImage))) rawImage else grayscale(scriptRuntime, arrayOf(rawImage))
            initOpenCvIfNeeded()

            val options = optionsArg as? NativeObject ?: newNativeObject()
            val region = options.prop("region")
            val image = when {
                region.isJsNullish() -> grayImage.mat
                else -> AutoJsMat(grayImage.mat, buildRegionInternal(grayImage, region))
            }
            val circles = AutoJsMat()
            val results = mutableListOf<HoughCirclesResult>()

            Imgproc.HoughCircles(
                image,
                circles,
                Imgproc.CV_HOUGH_GRADIENT,
                parseNumber(options.prop("dp"), 1).toDouble(),
                parseNumber(options.prop("minDst")) { grayImage.height / 8 }.toDouble(),
                parseNumber(options.prop("param1"), 100).toDouble(),
                parseNumber(options.prop("param2"), 100).toDouble(),
                parseNumber(options.prop("minRadius"), 0),
                parseNumber(options.prop("maxRadius"), 0),
            )
            for (i in 0 until circles.rows()) {
                for (j in 0 until circles.cols()) {
                    val (x, y, radius) = circles[i, j]
                    results += HoughCirclesResult(x, y, radius)
                }
            }
            if (!region.isJsNullish()) {
                image.release()
            }
            circles.release()
            results
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun resize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, size, interpolation) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.resize must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            resizeInternal(image, mat, size, 0.0, 0.0, interpolation)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, fx, fy, interpolation) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.scale must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val mat = AutoJsMat()
            resizeInternal(image, mat, 0, coerceNumber(fx), coerceNumber(fy), interpolation)
            image.shoot()
            matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rotate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, degree, centerXArg, centerYArg) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.rotate must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val centerX = parseNumber(centerXArg) { image.width / 2 }.toFloat()
            val centerY = parseNumber(centerYArg) { image.height / 2 }.toFloat()
            scriptRuntime.images.rotate(image, centerX, centerY, coerceFloatNumber(degree))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun concat(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (oA, oB, direction) = it
            val imageA = if (oA is String) read(scriptRuntime, arrayOf(oA, true)) else oA
            require(imageA is ImageWrapper) { "Argument imageA for images.concat must be a ImageWrapper" }
            val imageB = if (oB is String) read(scriptRuntime, arrayOf(oB, true)) else oB
            require(imageB is ImageWrapper) { "Argument imageB for images.concat must be a ImageWrapper" }
            initOpenCvIfNeeded()
            ApiImages.concat(imageA, imageB, directionToGravityToConcat(direction))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 4..6) {
            val (o, color, x, y, threshold, algorithm) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.detectColor must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val pixel = pixel(scriptRuntime, arrayOf(image, coerceIntNumber(x), coerceIntNumber(y)))
            ColorDetector
                .get(Colors.toIntRhino(color), coerceString(algorithm, DEFAULT_COLOR_ALGORITHM), parseNumber(threshold, DEFAULT_COLOR_THRESHOLD))
                .detectColor(Colors.redRhino(pixel).roundToInt(), Colors.greenRhino(pixel).roundToInt(), Colors.blueRhino(pixel).roundToInt())
        }

        @Deprecated("Deprecated in Java", ReplaceWith("detectColor(image, color, x, y, threshold, algorithm)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectsColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 4..6) {
            val (o) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.detectsColor must be a ImageWrapper" }
            detectColor(scriptRuntime, it)
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 5..6) {

            TODO("detectMultiColors 尚未实现")

        }

        @Deprecated("Deprecated in Java", ReplaceWith("detectMultiColors(image, x, y, firstColor, paths, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectsMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 5..6) {
            val (o, _, _, _, paths) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.detectMultiColors must be a ImageWrapper" }
            require(paths is NativeArray) { "Argument paths for images.detectMultiColors must be a JavaScript Array" }
            detectMultiColors(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            val (o, color, xOrOptions, y, width, height, threshold) = argList
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointByColor must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val opt = when {
                argList.size < 3 -> newNativeObject()
                argList.size == 3 -> when {
                    xOrOptions.isJsNullish() -> newNativeObject()
                    else -> xOrOptions as? NativeObject
                }
                else -> null
            }
            scriptRuntime.images.colorFinder.findPointByColor(
                image,
                Colors.toIntRhino(color),
                opt?.let { parseThreshold(it).roundToInt() } ?: coerceIntNumber(threshold, DEFAULT_COLOR_THRESHOLD),
                buildRegionInternal(image, opt?.prop("region")?.takeUnless { it.isJsNullish() } ?: listOf(/* x = */ xOrOptions, y, width, height))
            ).also { image.shoot() }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, color, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findColor must be a ImageWrapper" }
            findPointByColor(scriptRuntime, arrayOf(image, color, options))
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColorInRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) {
            val (o) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findColorInRegion must be a ImageWrapper" }
            findPointByColor(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColorExactly(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..6) {
            val (o) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointByColorExactly must be a ImageWrapper" }
            findPointByColor(scriptRuntime, it + /* threshold = */ 0)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColorExactly(image, color, x, y, width, height)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColorEquals(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..6) {
            val (o) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findColorEquals must be a ImageWrapper" }
            findPointByColorExactly(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointsByColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, color, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointsByColor must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val opt = options as? NativeObject ?: newNativeObject()
            scriptRuntime.images.colorFinder.findPointsByColor(
                image,
                Colors.toIntRhino(color),
                parseThreshold(opt).roundToInt(),
                opt.inquire("region") { region -> buildRegionInternal(image, region) },
            ).toNativeArray()
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointsByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findAllPointsForColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 2..3) {
            val (o) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findAllPointsForColor must be a ImageWrapper" }
            findPointsByColor(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, firstColor, paths, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointByColors must be a ImageWrapper" }
            require(paths is NativeArray) { "Argument paths for images.findPointByColors must be a JavaScript Array" }
            initOpenCvIfNeeded()
            val opt = options as? NativeObject ?: newNativeObject()
            scriptRuntime.images.colorFinder.findPointByColors(
                image,
                Colors.toIntRhino(firstColor),
                parseThreshold(opt).roundToInt(),
                opt.inquire("region") { region -> buildRegionInternal(image, region) },
                paths.flatMap { path ->
                    val (px, py, color) = path as NativeArray
                    listOf(coerceIntNumber(px), coerceIntNumber(py), Colors.toIntRhino(color))
                }.toIntArray(),
            )
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColors(image, firstColor, paths, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, _, paths) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findMultiColors must be a ImageWrapper" }
            require(paths is NativeArray) { "Argument paths for images.findMultiColors must be a JavaScript Array" }
            findPointByColors(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointsByColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, firstColor, paths, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointsByColors must be a ImageWrapper" }
            require(paths is NativeArray) { "Argument paths for images.findPointsByColors must be a JavaScript Array" }
            initOpenCvIfNeeded()
            val opt = options as? NativeObject ?: newNativeObject()
            scriptRuntime.images.colorFinder.findPointsByColors(
                image,
                Colors.toIntRhino(firstColor),
                parseThreshold(opt).roundToInt(),
                opt.inquire("region") { region -> buildRegionInternal(image, region) },
                paths.flatMap { path ->
                    val (px, py, color) = path as NativeArray
                    listOf(coerceIntNumber(px), coerceIntNumber(py), Colors.toIntRhino(color))
                }.toIntArray(),
            ).toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            val (o, template, xOrOptions, y, width, height, thresholdArg) = argList
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findPointByImage must be a ImageWrapper" }
            require(template is ImageWrapper) { "Argument template for images.findPointByImage must be a ImageWrapper" }
            initOpenCvIfNeeded()
            when {
                argList.size > 2 && xOrOptions !is NativeObject -> {
                    val options = mapOf(
                        "region" to listOf(/* x = */ xOrOptions, y, width, height),
                        "threshold" to thresholdArg,
                    ).toNativeObject()
                    findPointByImage(scriptRuntime, arrayOf(image, template, options))
                }
                else -> {
                    val opt = when {
                        argList.size > 2 -> xOrOptions as? NativeObject
                        else -> null
                    } ?: newNativeObject()
                    val weakThreshold = parseWeakThreshold(opt, 0.6).toFloat()
                    val threshold = parseThreshold(opt, 0.9).toFloat()
                    val region = buildRegionInternal(image, opt.prop("region").takeUnless { it.isJsNullish() } ?: listOf(/* x = */ xOrOptions, y, width, height))
                    val level = parseNumber(opt.prop("level"), -1)
                    scriptRuntime.images.findImage(image, template, weakThreshold, threshold, region, level)
                }
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByImage(image, template, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, template) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findImage must be a ImageWrapper" }
            require(template is ImageWrapper) { "Argument template for images.findImage must be a ImageWrapper" }
            findPointByImage(scriptRuntime, it)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByImage(image, template, x, y, width, height, threshold)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findImageInRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) {
            val (o, template) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.findImageInRegion must be a ImageWrapper" }
            require(template is ImageWrapper) { "Argument template for images.findImageInRegion must be a ImageWrapper" }
            findPointByImage(scriptRuntime, it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matchTemplate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): MatchingResult = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, template, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.matchTemplate must be a ImageWrapper" }
            require(template is ImageWrapper) { "Argument template for images.matchTemplate must be a ImageWrapper" }
            initOpenCvIfNeeded()
            val opt = options as? NativeObject ?: newNativeObject()
            val weakThreshold = parseWeakThreshold(opt, 0.6).toFloat()
            val threshold = parseThreshold(opt, 0.9).toFloat()
            val region = opt.inquire("region") { region -> buildRegionInternal(image, region) }
            val level = parseNumber(opt.prop("level"), -1)
            val max = parseNumber(opt.prop("max"), 5)
            val useTransparentMask = when {
                opt.hasProp("useTransparentMask") -> coerceBoolean(opt.prop("useTransparentMask"))
                opt.hasProp("transparentMask") -> /* @Compatible with Auto.js Pro. */ coerceBoolean(opt.prop("transparentMask"))
                else -> false
            }
            MatchingResult(scriptRuntime.images.matchTemplate(image, template, weakThreshold, threshold, region, level, max, useTransparentMask))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun fromBase64(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { base64 ->
            scriptRuntime.images.fromBase64(coerceString(base64))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toBase64(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) {
            val (o, format, quality) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.toBase64 must be a ImageWrapper" }
            scriptRuntime.images.toBase64(image, parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_TO_BASE64_QUALITY))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun fromBytes(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { bytes ->
            when (bytes) {
                is NativeArray -> scriptRuntime.images.fromBytes(bytes.map { coerceIntNumber(it).toByte() }.toByteArray())
                is ByteArray -> scriptRuntime.images.fromBytes(bytes)
                else -> throw WrappedIllegalArgumentException("Argument bytes ${bytes.jsBrief()} is invalid for images.fromBytes")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toBytes(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ByteArray = ensureArgumentsLengthInRange(args, 1..3) {
            val (o, format, quality) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.toBytes must be a ImageWrapper" }
            scriptRuntime.images.toBytes(image, parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_TO_BYTES_QUALITY))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun readPixels(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLength(args, 1) { argList ->
            val image = read(scriptRuntime, argList)
            require(image != null) { "Image path ${argList[0]} is invalid for images.readPixels" }
            val bitmap = image.bitmap
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            image.recycle()
            bitmap.recycle()
            mapOf("data" to pixels, "width" to w, "height" to h).toNativeObject()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matToImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) {
            initOpenCvIfNeeded()
            when (it) {
                is AutoJsMat -> ImageWrapper.ofMat(it)
                is OpencvMat -> ImageWrapper.ofMat(it)
                else -> throw WrappedIllegalArgumentException("Argument mat ${it.jsBrief()} is invalid for images.matToImage()")
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectAndComputeFeatures(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageFeatures = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, options) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.detectAndComputeFeatures must be a ImageWrapper instead of ${image.jsBrief()}" }
            val opt = fillDetectAndComputeFeaturesOptions(image.height, image.width, options as? NativeObject ?: newNativeObject())
            val rect = buildRegionInternal(image, opt.region)
            val mat = scriptRuntime.images.newMat(image.mat, rect)
            val result = scriptRuntime.images.detectAndComputeFeatures(mat, opt.scale, opt.cvtColor, opt.method)
            mat.release()
            ImageFeatures(result, opt.scale, rect)
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matchFeatures(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ObjectFrame? = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (sceneFeatures, objectFeatures, options) = argList
            val opt = options as? NativeObject ?: newNativeObject()
            require(sceneFeatures is ImageFeatures) {
                "Argument sceneFeatures ${sceneFeatures.jsBrief()} for images.matchFeatures must be a ImageFeatures"
            }
            require(objectFeatures is ImageFeatures) {
                "Argument objectFeatures ${objectFeatures.jsBrief()} for images.matchFeatures must be a ImageFeatures"
            }
            val matcher = opt.inquire("matcher") {
                coerceIntNumber(DescriptorMatcher::class.java.getField(coerceString(it)).get(null))
            } ?: DescriptorMatcher.FLANNBASED
            val drawMatches = opt.inquire("drawMatches") { scriptRuntime.files.path(coerceString(it)) }
            val threshold = opt.inquire("threshold", ::coerceFloatNumber, 0.7f)

            val result = ImageFeatureMatching.featureMatching(sceneFeatures.javaObject, objectFeatures.javaObject, matcher, drawMatches, threshold) ?: return@ensureArgumentsLengthInRange null

            val javaMatchesImage = result.matches
            val points = result.points

            if (!drawMatches.isJsNullish()) {
                val matchesImage = javaMatchesImage?.let { matToImage(scriptRuntime, arrayOf(it)) }
                if (matchesImage != null) {
                    save(scriptRuntime, arrayOf(matchesImage, drawMatches, "jpg", 100))
                    matchesImage.recycle()
                }
            }

            val region = sceneFeatures.region
            val scale = sceneFeatures.scale
            val size = points.size
            val offsetX = region.x
            val offsetY = region.y

            (0 until size).forEach { i ->
                val point = points[i]
                point.x = offsetX + point.x / scale
                point.y = offsetY + point.y / scale
            }

            ObjectFrame(points[0], points[1], points[3], points[2])
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRecycled(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) { argList ->
            argList.fold(true) { acc, it -> acc && it is ImageWrapper && it.isRecycled }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recycle(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) { argList ->
            argList.fold(true) { acc, it -> acc && it is ImageWrapper && runCatching { if (!it.isRecycled) it.recycle() }.isSuccess }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun compress(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 2) {
            val (o, compressLevelArg) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.compress must be a ImageWrapper" }
            val compressLevel = coerceNumber(compressLevelArg, 1.0)
            val level = 2.0.pow(floor(ln(compressLevel.coerceAtLeast(1.0)) / ln(2.0))).toInt()
            val outputStream = ByteArrayOutputStream()
            image.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            val options = BitmapFactory.Options().apply { inSampleSize = level }
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            ImageWrapper.ofBitmap(bitmap).also { image.shoot() }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvSize = ensureArgumentsLength(args, 1) { argList ->
            val (o) = argList
            when (o) {
                is ImageWrapper -> o.size
                is OpencvMat, is Bitmap -> OpencvSize(getWidth(scriptRuntime, argList), getHeight(scriptRuntime, argList))
                is String -> BitmapFactory.Options().apply { inJustDecodeBounds = true }.let { opt ->
                    require(scriptRuntime.files.exists(o)) { "Image source ($o) doesn't exist" }
                    BitmapFactory.decodeFile(scriptRuntime.files.path(o), opt)
                    OpencvSize(opt.outWidth.toDouble(), opt.outHeight.toDouble())
                }
                else -> throw WrappedIllegalArgumentException("Unknown source to parse its size: $o")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getWidth(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) { argList ->
            val (o) = argList
            when (o) {
                is ImageWrapper -> o.width.toDouble()
                is Bitmap -> o.width.toDouble()
                is OpencvMat -> o.cols().toDouble()
                is String -> getSize(scriptRuntime, argList).width
                else -> throw WrappedIllegalArgumentException("Unknown source to parse its width: $o")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getHeight(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) { argList ->
            val (o) = argList
            when (o) {
                is ImageWrapper -> o.height.toDouble()
                is Bitmap -> o.height.toDouble()
                is OpencvMat -> o.rows().toDouble()
                is String -> getSize(scriptRuntime, arrayOf(o)).height
                else -> throw WrappedIllegalArgumentException("Unknown source to parse its height: $o")
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun buildRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvRect = ensureArgumentsLength(args, 2) {
            val (o, region) = it
            val image = if (o is String) read(scriptRuntime, arrayOf(o, true)) else o
            require(image is ImageWrapper) { "Argument image for images.buildRegion must be a ImageWrapper" }
            require(region.isJsNullish() || region.isJsArray() || region is OpencvRect || region is AndroidRect) {
                "Argument region ${region.jsSpecies()} is invalid for images.buildRegion"
            }
            buildRegionInternal(image, region)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun psnr(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.psnr(extractMatPair(scriptRuntime, it, ::psnr.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ssim(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.ssim(extractMatPair(scriptRuntime, it, ::ssim.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun mssim(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.mssim(extractMatPair(scriptRuntime, it, ::mssim.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hist(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.hist(extractMatPair(scriptRuntime, it, ::hist.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun mse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.mse(extractMatPair(scriptRuntime, it, ::mse.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ncc(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            ImageSimilarity.ncc(extractMatPair(scriptRuntime, it, ::ncc.name))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isEqual(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 2) { argList ->
            val (oA, oB) = argList
            val imageA = if (oA is String) read(scriptRuntime, arrayOf(oA, true)) else oA
            require(imageA is ImageWrapper) { throw WrappedIllegalArgumentException("The first image argument for images.isEqual must be a ImageWrapper instead of ${imageA.jsBrief()}") }
            val imageB = if (oB is String) read(scriptRuntime, arrayOf(oB, true)) else oB
            require(imageB is ImageWrapper) { throw WrappedIllegalArgumentException("The second image argument for images.isEqual must be a ImageWrapper instead of ${imageA.jsBrief()}") }
            ImageSimilarity.isEqual(imageA.mat, imageB.mat)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getSimilarity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (oA, oB, options) = argList
            val (matA, matB) = extractMatPair(scriptRuntime, arrayOf(oA, oB), ::getSimilarity.name)
            val opt = options as? NativeObject ?: newNativeObject()
            val metric = opt.inquire("metric", ::coerceStringLowercase, DEFAULT_IMAGE_SIMILARITY_METRIC)
            val similarityMethod = kotlin.runCatching {
                ImageSimilarity::class.java.getDeclaredMethod(metric, OpencvMat::class.java, OpencvMat::class.java)
            }.getOrElse { throw WrappedIllegalArgumentException("Unknown similarity metric: $metric") }
            if (similarityMethod.returnType != Double::class.java && similarityMethod.returnType != java.lang.Double::class.java) {
                throw WrappedIllegalArgumentException("Similarity metric ($metric) method must return Double type. Found: ${similarityMethod.returnType}")
            }
            similarityMethod.invoke(ImageSimilarity, matA, matB) as Double
        }

        private fun buildRegionInternal(image: ImageWrapper, region: Any?) = buildRegionInternal(region, image.width, image.height)

        private fun buildRegionInternal(region: Any?, imageWidth: Int, imageHeight: Int): OpencvRect {
            var (x, y, w, h) = when {
                region is OpencvRect -> arrayOf(region.x, region.y, region.width, region.height)
                region is AndroidRect -> arrayOf(region.left, region.top, region.width(), region.height())
                region is List<*> -> region.map { runCatching { coerceIntNumber(it) }.getOrNull() }.toTypedArray()
                region.isJsNullish() -> arrayOfNulls<Int>(4)
                else -> throw WrappedIllegalArgumentException("Invalid region ${region.jsBrief()} for Images#buildRegionInternal")
            }
            x = parseScreenMetric(parseNumber(x, 0), ScreenMetrics.deviceScreenWidth).also {
                require(it >= 0) { "X of region must be non-negative rather than $it" }
            }
            y = parseScreenMetric(parseNumber(y, 0), ScreenMetrics.deviceScreenHeight).also {
                require(it >= 0) { "Y of region must be non-negative rather than $it" }
            }
            w = parseScreenMetric(parseNumber(w) { imageWidth - x }, ScreenMetrics.deviceScreenWidth).also {
                require(x + it <= imageWidth) { "Excessive width: region [$x + $it] > image [$imageWidth]" }
            }
            h = parseScreenMetric(parseNumber(h) { imageHeight - y }, ScreenMetrics.deviceScreenHeight).also {
                require(y + it <= imageHeight) { "Excessive height: region [$y + $it] > image [$imageHeight]" }
            }
            return OpencvRect(x, y, w, h)
        }

        private fun requestScreenCaptureInternal(scriptRuntime: ScriptRuntime, vararg args: Any?): ScriptPromiseAdapter {
            val rtImages = scriptRuntime.images
            val images = scriptRuntime.topLevelScope.prop("images") as ScriptableObject
            val emitFunc = images.prop("emit") as BaseFunction

            var orientation = ScreenCapturer.ORIENTATION_AUTO
            var width = -1
            var height = -1
            var isAsync = false

            when (args.size) {
                2 -> {
                    orientation = ScreenCapturer.ORIENTATION_NONE
                    width = coerceIntNumber(args[0])
                    height = coerceIntNumber(args[1])
                }
                1 -> when (val o = args[0]) {
                    is NativeObject -> {
                        isAsync = when {
                            o.hasProp("isAsync") -> coerceBoolean(o.prop("isAsync"))
                            o.hasProp("async") -> /* @Compatible with Auto.js Pro. */ coerceBoolean(o.prop("async"))
                            else -> false
                        }
                        o.inquire("width") { coerceIntNumber(it) }?.let { width = it }
                        o.inquire("height") { coerceIntNumber(it) }?.let { height = it }
                        o.inquire("orientation") {
                            when (it) {
                                is Number -> it.toDouble().roundToInt()
                                is String -> when (it.lowercase()) {
                                    "none" -> ScreenCapturer.ORIENTATION_NONE
                                    "auto" -> ScreenCapturer.ORIENTATION_AUTO
                                    "portrait" -> ScreenCapturer.ORIENTATION_PORTRAIT
                                    "landscape" -> ScreenCapturer.ORIENTATION_LANDSCAPE
                                    else -> throw WrappedIllegalArgumentException("Unknown orientation \"$it\"")
                                }
                                else -> null
                            }
                        }?.let { orientation = it }
                    }
                    is Boolean -> orientation = when (o) {
                        true -> ScreenCapturer.ORIENTATION_LANDSCAPE
                        else -> ScreenCapturer.ORIENTATION_PORTRAIT
                    }
                }
            }

            if (isAsync) {
                rtImages.setImageCaptureCallback { image ->
                    /* @Reference to `org.autojs.autojs.runtime.api.Images.OnScreenCaptureAvailableListener` */
                    callFunction(scriptRuntime, emitFunc, arrayOf("screen_capture_available", image))
                    /* @Reference to `org.autojs.autojs.runtime.api.Images.OnScreenCaptureAvailableListener#onCaptureAvailable` */
                    callFunction(scriptRuntime, emitFunc, arrayOf("capture_available", image))
                    /* @Compatible with Auto.js Pro. */
                    callFunction(scriptRuntime, emitFunc, arrayOf("screen_capture", image))
                }
            }

            return rtImages.requestScreenCapture(orientation, width, height, isAsync)
        }

        private fun parseScreenMetric(reference: Int, dimension: Int) = when (reference) {
            -1 -> dimension
            in 1 downTo 0 -> dimension * reference
            else -> reference
        }

        private fun parseNumber(x: Any?, def: Any? = null) = coerceIntNumber(x, coerceIntNumber(if (def.isJsNullish()) 0 else def))

        private fun parseNumber(x: Any?, def: () -> Any?): Int {
            val tmp = coerceNumber(x, Double.NaN)
            return when {
                tmp.isNaN() -> def.invoke().let { result ->
                    if (result.isJsNullish()) 0 else coerceIntNumber(result)
                }
                else -> tmp.roundToInt()
            }
        }

        private fun coerceIn(num: Double?, minValue: Double, maxValue: Double, defValue: Double = 0.0): Double {
            return parseNumber(num, defValue).toDouble().coerceIn(minValue, maxValue)
        }

        private fun parseSize(size: Any?): OpencvSize {
            val (width, height) = when (size) {
                is Number -> size.toDouble() to size.toDouble()
                is NativeArray -> when (size.size) {
                    2 -> coerceNumber(size[0]) to coerceNumber(size[1])
                    1 -> coerceNumber(size[0]) to coerceNumber(size[0])
                    else -> throw WrappedIllegalArgumentException("The length (${size.size}) of argument \"size\" for Images#parseSize is unacceptable")
                }
                else -> coerceNumber(size, 0) to coerceNumber(size, 0)
            }
            return OpencvSize(width, height)
        }

        private fun parseQuality(q: Any?, def: Number): Int {
            return coerceIn(coerceNumber(q, def), 0.0, 100.0, def.toDouble()).roundToInt()
        }

        private fun parseImageFormat(o: Any?): String = when {
            o.isJsNullish() -> "png"
            else -> when (val format = coerceString(o).lowercase()) {
                "png", "jpg", "jpeg", "webp" -> format
                else -> throw WrappedIllegalArgumentException("Unknown image format: $format")
            }
        }

        private fun parseScalar(color: Int, offset: Double? = null): OpencvScalar {
            val d = coerceIn(offset, -255.0, 255.0, 0.0)
            return OpencvScalar(
                Colors.redRhino(color) + d,
                Colors.greenRhino(color) + d,
                Colors.blueRhino(color) + d,
                Colors.alphaRhino(color),
            )
        }

        private fun parseScalars(color: Int, threshold: Double): Pair<OpencvScalar, OpencvScalar> {
            val offset = coerceIn(threshold, 0.0, 255.0, 0.0)
            val lowerBound = parseScalar(color, -offset)
            val upperBound = parseScalar(color, +offset)
            return lowerBound to upperBound
        }

        private fun parseThreshold(options: Any?, def: Double? = null): Double {
            val opt = options as? NativeObject ?: newNativeObject()
            val similarity = opt.prop("similarity")
            val threshold = opt.prop("threshold")
            return when {
                !similarity.isJsNullish() -> when {
                    !threshold.isJsNullish() -> throw WrappedIllegalArgumentException(
                        "Options for Images#parseThreshold cannot hold both 'similarity' and 'threshold' properties",
                    )
                    else -> round(255 * (1 - coerceNumber(similarity)))
                }
                threshold is Number -> threshold.toDouble()
                !def.isJsNullish() -> coerceNumber(def)
                else -> DEFAULT_COLOR_THRESHOLD.toDouble()
            }
        }

        private fun parseWeakThreshold(options: Any?, def: Double): Double {
            val opt = options as? NativeObject ?: newNativeObject()
            return opt.inquire("weakThreshold", ::coerceNumber, def)
        }

        private fun resizeInternal(image: ImageWrapper, mat: AutoJsMat, size: Any?, fx: Double, fy: Double, interpolation: Any?) {
            Imgproc.resize(image.mat, mat, parseSize(size), fx, fy, parseInterpolation(interpolation))
        }

        @SuppressLint("RtlHardcoded")
        private fun directionToGravityToConcat(direction: Any?): Int = when {
            direction.isJsNullish() -> Gravity.END
            else -> when (coerceString(direction).lowercase()) {
                "left" -> Gravity.LEFT
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "right" -> Gravity.RIGHT
                "start" -> Gravity.START
                "end" -> Gravity.END
                else -> throw WrappedIllegalArgumentException("Unknown image concat direction: $direction")
            }
        }

        private fun parseBorderType(o: Any?): Int {
            var border = o
            if (o is String) {
                border = when {
                    o.startsWith("BORDER_", true) -> o
                    else -> "BORDER_$o"
                }.uppercase()
            }
            return when {
                border.isJsNullish() -> OpencvCore.BORDER_DEFAULT
                else -> OpencvCore::class.java.getField(coerceString(border)).getInt(null)
            }
        }

        private fun parseThresholdType(o: Any?): Int {
            var threshold = o
            if (o is String) {
                threshold = when {
                    o.startsWith("THRESH_", true) -> o
                    else -> "THRESH_$o"
                }.uppercase()
            }
            return when {
                threshold.isJsNullish() -> Imgproc.THRESH_BINARY
                else -> Imgproc::class.java.getField(coerceString(threshold)).getInt(null)
            }
        }

        private fun parseInterpolation(o: Any?): Int {
            var interpolation = o
            if (o is String) {
                interpolation = when {
                    o.startsWith("INTER_", true) -> o
                    else -> "INTER_$o"
                }.uppercase()
            }
            return when {
                interpolation.isJsNullish() -> Imgproc.INTER_LINEAR
                else -> Imgproc::class.java.getField(coerceString(interpolation)).getInt(null)
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        private fun getDetectFeatureMethod(method: Any?): Int = when (method) {
            is Number -> method.toInt()
            is String -> {
                when (method.uppercase()) {
                    "SIFT" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT
                    "ORB" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB
                    else -> throw Error("Unknown method: $method")
                }
            }
            else -> throw Error("Non-recognized method: $method")
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19. 2023.
        private fun fillDetectAndComputeFeaturesOptions(rows: Int, cols: Int, options: NativeObject): DetectAndComputeFeaturesOptions {
            val scale = options.inquire("scale") { coerceFloatNumber(it) } ?: when {
                rows * cols >= 1e6 -> 0.5f
                else -> 1.0f
            }
            val cvtColor = when {
                options.inquire("grayscale", ::coerceBoolean, false) -> Imgproc.COLOR_RGBA2GRAY
                else -> -1
            }
            val method = getDetectFeatureMethod(options.inquire("method", ::coerceString, "SIFT"))
            val region = buildRegionInternal(options.prop("region"), cols, rows)

            return DetectAndComputeFeaturesOptions(scale, cvtColor, method, region)
        }

        private fun extractMatPair(scriptRuntime: ScriptRuntime, argList: Array<Any?>, funcName: String): Pair<OpencvMat, OpencvMat> {
            val (imageA, imageB) = argList
            val matA = when (imageA) {
                is ImageWrapper -> imageA.bgrMat
                is OpencvMat -> imageA
                is String -> read(scriptRuntime, arrayOf(imageA, true))!!.bgrMat
                else -> throw WrappedIllegalArgumentException("The first image argument for images.$funcName must be a ImageWrapper or a Mat instead of ${imageA.jsBrief()}")
            }
            val matB = when (imageB) {
                is ImageWrapper -> imageB.bgrMat
                is OpencvMat -> imageB
                is String -> read(scriptRuntime, arrayOf(imageB, true))!!.bgrMat
                else -> throw WrappedIllegalArgumentException("The second image argument for images.$funcName must be a ImageWrapper or a Mat instead of ${imageB.jsBrief()}")
            }
            return matA to matB
        }

        class HoughCirclesResult(val x: Double, val y: Double, val radius: Double)

        private class DetectAndComputeFeaturesOptions(val scale: Float, val cvtColor: Int, val method: Int, val region: OpencvRect)

    }

}