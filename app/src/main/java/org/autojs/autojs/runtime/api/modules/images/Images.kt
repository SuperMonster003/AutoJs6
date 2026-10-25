package org.autojs.autojs.runtime.api.modules.images

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.Gravity
import androidx.core.net.toUri
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ColorDetector
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component5
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component6
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component7
import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.rhino.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.util.StringUtils.isUri
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.runtime.api.ImageSimilarity
import org.autojs.autojs.runtime.api.PngQuantBridge
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.AsEmitter
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.runtime.api.modules.s13n.S13n
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.BitmapUtils
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.coerceStringLowercase
import org.autojs.autojs.util.RhinoUtils.handleAsyncOperation
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.opencv.core.CvType
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgproc.Imgproc
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
        ::initOpenCvIfNeeded.name,
        ::read.name,
        ::imread.name,
        ::copy.name,
        ::load.name,
        ::loadAsync.name,
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
        ::resize.name,
        ::scale.name,
        ::rotate.name,
        ::flip.name,
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
        ::countPointsByColor.name,
        ::getMeanColor.name,
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
        ::quantize.name,
        ::quantizeToFile.name,
        ::compress.name,
        ::compressToBytes.name,
        ::downsample.name,
        ::getSize.name,
        ::getWidth.name,
        ::getHeight.name,
        ::buildRegion.name,
        ::__buildRegion.name,
        ::psnr.name,
        ::ssim.name,
        ::mssim.name,
        ::hist.name,
        ::mse.name,
        ::ncc.name,
        ::isEqual.name,
        ::getSimilarity.name,
    )

    override val selfAssignmentJavaClasses = listOf(
        "ObjectFrame" to ObjectFrame::class,
    )

    @Suppress("MayBeConstant")
    companion object : ArgumentGuards() {

        private val TAG = Images::class.java.simpleName

        @JvmField
        val DEFAULT_COLOR_THRESHOLD = 4

        @JvmField
        val DEFAULT_IMAGE_SAVE_QUALITY = 100

        @JvmField
        val DEFAULT_IMAGE_COMPRESS_QUALITY = 60

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
        fun initOpenCvIfNeeded(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            undefined { ApiImages.initOpenCvIfNeeded() }
        }

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
            ApiImages.imread(scriptRuntime.files.nonNullPath(coerceString(path)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun copy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.copy must be a ImageWrapper" }
            try {
                scriptRuntime.images.copy(image)
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun load(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper? = ensureArgumentsOnlyOne(args) { src ->
            scriptRuntime.images.load(coerceString(src))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun loadAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) { src ->
            val url = coerceString(src)
            handleAsyncOperation(scriptRuntime) { scriptRuntime.images.load(url) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..5) {
            when (it.size) {
                5 -> {
                    val (o, x, y, w, h) = it
                    val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                    require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.clip must be a ImageWrapper" }
                    try {
                        scriptRuntime.images.clip(
                            image,
                            coerceIntNumber(x),
                            coerceIntNumber(y),
                            coerceIntNumber(w),
                            coerceIntNumber(h),
                        )
                    } finally {
                        image.shoot()
                    }
                }
                2 -> {
                    val (o, region) = it
                    val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                    require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.clip must be a ImageWrapper" }
                    when {
                        region.isJsNullish() -> throw WrappedIllegalArgumentException("Argument \"region\" ${region.jsBrief()} for images.clip must be non-nullish")
                        else -> try {
                            require(region.isJsArray() || region is OpencvRect || region is AndroidRect) {
                                "Argument region ${region.jsSpecies()} is invalid for images.clip"
                            }
                            // Build the region directly: going through images.buildRegion() would shoot
                            // a one-shot image (e.g. one loaded from a path) before it is clipped.
                            // zh-CN: 直接构建区域: 经由 images.buildRegion() 会在裁剪前回收一次性图像 (如从路径加载的图像).
                            val rect = buildRegionInternal(image, region)
                            scriptRuntime.images.clip(image, rect.x, rect.y, rect.width, rect.height)
                        } finally {
                            image.shoot()
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
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.pixel must be a ImageWrapper" }
            try {
                ApiImages.pixel(image, coerceIntNumber(x), coerceIntNumber(y))
            } finally {
                image.shoot()
            }
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
                path.isJsNullish() -> rtImages.captureScreen()
                else -> rtImages.captureScreen(scriptRuntime.files.nonNullPath(coerceString(path)))
            } ?: throw WrappedRuntimeException("Failed to capture screen image")
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestScreenCapture(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsAtMost(args, 2) { argList ->
            require(!isUiThread()) {
                "requestScreenCapture() called in ui thread, please use requestScreenCaptureAsync() instead"
            }
            if (scriptRuntime.images.hasActiveScreenCapturer()) {
                return@ensureArgumentsAtMost true
            }
            callFunction(
                scriptRuntime,
                scriptRuntime.js_ResultAdapter,
                "wait",
                arrayOf(requestScreenCaptureInternal(scriptRuntime, *argList)),
            ) as Boolean
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestScreenCaptureAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsAtMost(args, 2) { argList ->
            if (scriptRuntime.images.hasActiveScreenCapturer()) {
                return@ensureArgumentsAtMost callFunction(
                    scriptRuntime,
                    scriptRuntime.js_Promise,
                    "resolve",
                    arrayOf(true),
                ) as NativeObject
            }
            callFunction(
                scriptRuntime,
                scriptRuntime.js_ResultAdapter,
                "promise",
                arrayOf(requestScreenCaptureInternal(scriptRuntime, *argList)),
            ) as NativeObject
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stopScreenCapture(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            scriptRuntime.images.stopScreenCapture()
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScreenCaptureOptions(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScreenCapturer.Options? = ensureArgumentsIsEmpty(args) {
            scriptRuntime.images.screenCaptureOptions
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun save(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, path, format, quality) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.save must be a ImageWrapper" }
            try {
                require(!path.isJsNullish()) { "Argument \"path\" ${path.jsBrief()} for images.save must be non-nullish" }
                val nicePath = scriptRuntime.files.nonNullPath(coerceString(path))
                val niceFormat = parseImageFormat(format)
                if (quality is NativeObject) {
                    scriptRuntime.images.save(
                        image,
                        nicePath,
                        niceFormat,
                        parseImageQuantizationOptions(quality, DEFAULT_IMAGE_SAVE_QUALITY),
                    )
                } else {
                    scriptRuntime.images.save(
                        image,
                        nicePath,
                        niceFormat,
                        parseQuality(quality, DEFAULT_IMAGE_SAVE_QUALITY),
                    )
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun saveImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, path, format, quality) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.saveImage must be a ImageWrapper" }
            try {
                require(!path.isJsNullish()) { "Argument \"path\" ${path.jsBrief()} for images.saveImage must be non-nullish" }
                val nicePath = scriptRuntime.files.nonNullPath(coerceString(path))
                val niceFormat = parseImageFormat(format)
                if (quality is NativeObject) {
                    scriptRuntime.images.save(
                        image,
                        nicePath,
                        niceFormat,
                        parseImageQuantizationOptions(quality, DEFAULT_IMAGE_SAVE_QUALITY),
                    )
                } else {
                    scriptRuntime.images.save(
                        image,
                        nicePath,
                        niceFormat,
                        parseQuality(quality, DEFAULT_IMAGE_SAVE_QUALITY),
                    )
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun invert(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.invert must be a ImageWrapper" }
            try {
                scriptRuntime.images.invert(image)
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun grayscale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, dstCn) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.grayscale must be a ImageWrapper" }
            try {
                cvtColor(scriptRuntime, arrayOf(image, "BGR2GRAY", dstCn))
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isGrayscale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { o ->
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            ApiImages.initOpenCvIfNeeded()
            try {
                when (image) {
                    is OpencvMat -> isGrayscaleInternal(image)
                    is ImageWrapper -> isGrayscaleInternal(image.mat)
                    else -> throw WrappedIllegalArgumentException("Argument \"image\" ${image.jsBrief()} for images.isGrayscale must be a ImageWrapper or a Mat")
                }
            } finally {
                (image as? ImageWrapper)?.shoot()
            }
        }

        private fun isGrayscaleInternal(mat: OpencvMat): Boolean {
            if (mat.channels() == 1) {
                return true
            }
            // 检查每个像素的所有颜色通道值是否相同 (alpha 通道不是颜色通道, 不参与比较)
            val channels = mutableListOf<OpencvMat>()
            val result = OpencvMat()
            try {
                OpencvCore.split(mat, channels)
                val colorChannels = if (channels.size == 4) 3 else channels.size
                for (i in 1 until colorChannels) {
                    OpencvCore.compare(channels[0], channels[i], result, OpencvCore.CMP_NE)
                    if (OpencvCore.countNonZero(result) > 0) {
                        return false
                    }
                }
                return true
            } finally {
                channels.forEach { it.release() }
                result.release()
            }
        }

        /**
         * Creates an image from a freshly allocated output Mat filled by [block];
         * the Mat is released instead of leaked when [block] throws.
         * zh-CN: 由 [block] 填充新分配的输出 Mat 并包装为图像; [block] 抛出异常时释放该 Mat 而非泄漏.
         */
        private inline fun newImageFromMat(scriptRuntime: ScriptRuntime, block: (AutoJsMat) -> Unit): ImageWrapper {
            ApiImages.initOpenCvIfNeeded()
            val mat = AutoJsMat()
            try {
                block(mat)
            } catch (t: Throwable) {
                mat.release()
                throw t
            }
            return matToImage(scriptRuntime, arrayOf(mat))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun threshold(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, threshold, maxVal, type) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.threshold must be a ImageWrapper" }
            try {
                newImageFromMat(scriptRuntime) { mat ->
                    Imgproc.threshold(image.mat, mat, coerceNumber(threshold), coerceNumber(maxVal), parseThresholdType(type))
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun inRange(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 3) {
            val (o, lowerBound, upperBound) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.inRange must be a ImageWrapper" }
            try {
                newImageFromMat(scriptRuntime) { dst ->
                    OpencvCore.inRange(image.mat, parseScalar(Colors.toIntRhino(lowerBound)), parseScalar(Colors.toIntRhino(upperBound)), dst)
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun interval(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 3) {
            val (o, color, threshold) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.interval must be a ImageWrapper" }
            try {
                val (lowerBound, upperBound) = parseScalars(Colors.toIntRhino(color), coerceNumber(threshold))
                newImageFromMat(scriptRuntime) { dst ->
                    OpencvCore.inRange(image.mat, lowerBound, upperBound, dst)
                }
            } finally {
                image.shoot()
            }
        }

        @Suppress("LocalVariableName")
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun adaptiveThreshold(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 6) {
            val (o, maxValue, adaptiveMethod, thresholdType, blockSize, C) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.adaptiveThreshold must be a ImageWrapper" }
            try {
                val adaptiveThresholdValue = Imgproc::class.java.getField("ADAPTIVE_THRESH_${coerceString(adaptiveMethod).uppercase()}").getInt(null)
                val thresholdValue = Imgproc::class.java.getField("THRESH_${coerceString(thresholdType).uppercase()}").getInt(null)
                newImageFromMat(scriptRuntime) { mat ->
                    Imgproc.adaptiveThreshold(image.mat, mat, coerceNumber(maxValue), adaptiveThresholdValue, thresholdValue, coerceIntNumber(blockSize), coerceNumber(C))
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun blur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, ksize, anchor, type) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.blur must be a ImageWrapper" }
            try {
                newImageFromMat(scriptRuntime) { mat ->
                    when (anchor) {
                        null -> Imgproc.blur(image.mat, mat, parseSize(ksize))
                        else -> Imgproc.blur(image.mat, mat, parseSize(ksize), S13n.point(arrayOf(anchor)).run {
                            OpencvPoint(x.toDouble(), y.toDouble())
                        }, parseBorderType(type))
                    }
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun medianBlur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLength(args, 2) {
            val (o, size) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.medianBlur must be a ImageWrapper" }
            try {
                ApiImages.initOpenCvIfNeeded()
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
                newImageFromMat(scriptRuntime) { mat ->
                    Imgproc.medianBlur(image.mat, mat, ksize)
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun gaussianBlur(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..5) {
            val (o, size, sigmaX, sigmaY, type) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.gaussianBlur must be a ImageWrapper" }
            try {
                /* sigmaX. */
                val x = parseNumber(sigmaX)
                /* sigmaY. */
                val y = parseNumber(sigmaY)
                /* 边缘点插值类型. */
                val borderType = parseBorderType(type)
                /* 高斯模糊. */
                newImageFromMat(scriptRuntime) { mat ->
                    Imgproc.GaussianBlur(image.mat, mat, parseSize(size), x, y, borderType)
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun bilateralFilter(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..5) {
            val (o, d, sigmaColor, sigmaSpace, borderType) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.bilateralFilter must be a ImageWrapper" }
            try {
                val dValue = parseIntNumber(d, 0)
                val sigmaColorValue = parseNumber(sigmaColor, 40)
                val sigmaSpaceValue = parseNumber(sigmaSpace, 20)
                newImageFromMat(scriptRuntime) { mat ->
                    Imgproc.bilateralFilter(image.mat, mat, dValue, sigmaColorValue, sigmaSpaceValue, parseBorderType(borderType))
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cvtColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, code, dstCn) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.cvtColor must be a ImageWrapper" }
            try {
                val colorCode = Imgproc::class.java.getField("COLOR_$code").getInt(null)
                cvtColorInternal(scriptRuntime, image, colorCode, dstCn?.let { coerceIntNumber(it) })
            } finally {
                image.shoot()
            }
        }

        private fun cvtColorInternal(scriptRuntime: ScriptRuntime, image: ImageWrapper, colorCode: Int, dstCn: Int?): ImageWrapper {
            return newImageFromMat(scriptRuntime) { mat ->
                when (dstCn) {
                    null -> Imgproc.cvtColor(image.mat, mat, colorCode)
                    else -> Imgproc.cvtColor(image.mat, mat, colorCode, dstCn)
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findCircles(scriptRuntime: ScriptRuntime, args: Array<out Any?>): List<Map<String, Double>> = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, optionsArg) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"grayImage\" ${image.jsBrief()} for images.findCircles must be a ImageWrapper" }
            val options = optionsArg as? NativeObject ?: newNativeObject()
            val region = options.prop("region")
            var mat: AutoJsMat? = null
            var grayImage: ImageWrapper? = null
            var circles: AutoJsMat? = null
            try {
                ApiImages.initOpenCvIfNeeded()
                val output = AutoJsMat().also { circles = it }
                // HoughCircles requires a single-channel image; convert without shooting the source,
                // which may be a one-shot image loaded from a path.
                // zh-CN: HoughCircles 需要单通道图像; 转换时不回收源图像 (可能是从路径加载的一次性图像).
                grayImage = when (image.mat.channels()) {
                    1 -> image
                    else -> cvtColorInternal(scriptRuntime, image, Imgproc.COLOR_BGR2GRAY, null).oneShot()
                }
                val regionRect = region.takeUnless { it.isJsNullish() }?.let {
                    buildRegionInternal(grayImage, it)
                }
                mat = regionRect?.let { AutoJsMat(grayImage.mat, it) } ?: grayImage.mat
                val results = mutableListOf<Map<String, Double>>()

                Imgproc.HoughCircles(
                    mat,
                    output,
                    Imgproc.CV_HOUGH_GRADIENT,
                    parseNumber(options.prop("dp"), 1),
                    parseNumber(options.prop("minDst")) { grayImage.height / 8 },
                    parseNumber(options.prop("param1"), 100),
                    parseNumber(options.prop("param2"), 100),
                    parseIntNumber(options.prop("minRadius"), 0),
                    parseIntNumber(options.prop("maxRadius"), 0),
                )
                for (i in 0 until output.rows()) {
                    for (j in 0 until output.cols()) {
                        val (x, y, radius) = output[i, j]
                        results += buildCircleResult(
                            x,
                            y,
                            radius,
                            regionRect?.x ?: 0,
                            regionRect?.y ?: 0,
                        )
                    }
                }
                results
            } finally {
                image.shoot()
                grayImage?.shoot()
                mat.takeIf { !region.isJsNullish() }?.release()
                circles?.release()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun resize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (o, size, interpolation) = it
            val resizeArguments = OpenCvImageArgumentPolicy.parseResize(size, interpolation)
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.resize must be a ImageWrapper" }
            try {
                ApiImages.initOpenCvIfNeeded()
                val mat = AutoJsMat()
                var ownershipTransferred = false
                try {
                    resizeInternal(image, mat, resizeArguments.size, 0.0, 0.0, resizeArguments.interpolation)
                    matToImage(scriptRuntime, arrayOf(mat)).also { ownershipTransferred = true }
                } finally {
                    if (!ownershipTransferred) mat.release()
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun scale(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, fx, fy, interpolation) = it
            val scaleX = coerceNumber(fx)
            val scaleY = coerceNumber(fy)
            val interpolationValue = OpenCvImageArgumentPolicy.parseInterpolation(interpolation)
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.scale must be a ImageWrapper" }
            try {
                ApiImages.initOpenCvIfNeeded()
                val mat = AutoJsMat()
                var ownershipTransferred = false
                try {
                    resizeInternal(image, mat, OpencvSize(0.0, 0.0), scaleX, scaleY, interpolationValue)
                    matToImage(scriptRuntime, arrayOf(mat)).also { ownershipTransferred = true }
                } finally {
                    if (!ownershipTransferred) mat.release()
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun rotate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..4) {
            val (o, degree, centerXArg, centerYArg) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.rotate must be a ImageWrapper" }
            try {
                ApiImages.initOpenCvIfNeeded()
                val centerX = parseNumber(centerXArg) { image.width / 2 }.toFloat()
                val centerY = parseNumber(centerYArg) { image.height / 2 }.toFloat()
                scriptRuntime.images.rotate(image, centerX, centerY, coerceFloatNumber(degree))
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun flip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            val (o, arg1, arg2) = argList
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.flip must be a ImageWrapper" }
            try {
                ApiImages.initOpenCvIfNeeded()
                val (horizontal, vertical) = when (argList.size) {
                    1 -> true to false
                    2 -> parseFlipOrientations(arg1)
                    3 -> coerceBoolean(arg1, false) to coerceBoolean(arg2, false)
                    else -> throw WrappedIllegalArgumentException("Arguments length (${args.size}) is unacceptable for images.flip")
                }
                scriptRuntime.images.flip(image, horizontal, vertical)
            } finally {
                image.shoot()
            }
        }

        private fun parseFlipOrientations(o: Any?): Pair<Boolean, Boolean> = when {
            o.isJsNullish() -> false to false
            o is Boolean -> o to false
            o is String -> {
                val lower = o.lowercase().trim()
                val bothPattern = Regex("^(both|all|h\\|v|v\\|h|xy|yx|x\\|y|y\\|x)$")
                val hPattern = Regex("^(h|horizontal|x)$")
                val vPattern = Regex("^(v|vertical|y)$")
                when {
                    bothPattern.matches(lower) -> true to true
                    hPattern.matches(lower) -> true to false
                    vPattern.matches(lower) -> false to true
                    else -> coerceBoolean(o, false) to false
                }
            }
            o is NativeArray -> when (o.size) {
                0 -> false to false
                1 -> coerceBoolean(o[0], false) to false
                else -> coerceBoolean(o[0], false) to coerceBoolean(o[1], false)
            }
            o is NativeObject -> {
                val h = o.inquire(listOf("x", "h", "horizontal"), ::coerceBoolean, false)
                val v = o.inquire(listOf("y", "v", "vertical"), ::coerceBoolean, false)
                h to v
            }
            else -> false to false
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun concat(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 2..3) {
            val (oA, oB, direction) = it
            var imageA: ImageWrapper? = null
            var imageB: ImageWrapper? = null
            try {
                val imageARaw = if (oA.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oA), true))?.oneShot() else oA
                require(imageARaw is ImageWrapper) { "Argument \"imageA\" ${imageARaw.jsBrief()} for images.concat must be a ImageWrapper" }
                imageA = imageARaw
                val imageBRaw = if (oB.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oB), true))?.oneShot() else oB
                require(imageBRaw is ImageWrapper) { "Argument \"imageB\" ${imageBRaw.jsBrief()} for images.concat must be a ImageWrapper" }
                imageB = imageBRaw
                ApiImages.initOpenCvIfNeeded()
                ApiImages.concat(scriptRuntime, imageA, imageB, directionToGravityToConcat(direction))
            } finally {
                ApiImages.shoot(imageA, imageB)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 4..6) { argList ->
            detectColorRhino(scriptRuntime, argList, ::detectColor.name)
        }

        @JvmStatic
        fun detectColorRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): Boolean {
            val (o, color, x, y, threshold, algorithm) = args
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.detectColor must be a ImageWrapper" }
                imageRef = image
                ApiImages.initOpenCvIfNeeded()
                val pixel = pixel(scriptRuntime, arrayOf(image, coerceIntNumber(x), coerceIntNumber(y)))
                return ColorDetector
                    .get(Colors.toIntRhino(color), coerceString(algorithm, DEFAULT_COLOR_ALGORITHM), parseIntNumber(threshold, DEFAULT_COLOR_THRESHOLD))
                    .detectColor(Colors.redRhino(pixel).roundToInt(), Colors.greenRhino(pixel).roundToInt(), Colors.blueRhino(pixel).roundToInt())
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("detectColor(image, color, x, y, threshold, algorithm)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectsColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 4..6) { argList ->
            @Suppress("DEPRECATION")
            detectColorRhino(scriptRuntime, argList, ::detectsColor.name)
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on May 10, 2025.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 5..6) { argList ->
            detectMultiColorsRhino(scriptRuntime, argList, ::detectMultiColors.name)
        }

        @JvmStatic
        fun detectMultiColorsRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): Boolean {
            val (o, x, y, firstColor, paths, options) = args
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.detectMultiColors must be a ImageWrapper" }
                imageRef = image
                require(paths is NativeArray) { "Argument \"paths\" ${paths.jsBrief()} for images.detectMultiColors must be a JavaScript Array" }
                ApiImages.initOpenCvIfNeeded()
                val opt = options as? NativeObject ?: newNativeObject()
                return scriptRuntime.images.colorFinder.detectMultiColors(
                    image,
                    coerceIntNumber(x),
                    coerceIntNumber(y),
                    Colors.toIntRhino(firstColor),
                    parseThreshold(opt).roundToInt(),
                    opt.inquire("region") { region -> buildRegionInternal(image, region) },
                    paths.flatMap { path ->
                        val (px, py, color) = path as NativeArray
                        listOf(coerceIntNumber(px), coerceIntNumber(py), Colors.toIntRhino(color))
                    }.toIntArray(),
                )
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("detectMultiColors(image, x, y, firstColor, paths, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectsMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 5..6) { argList ->
            @Suppress("DEPRECATION")
            detectMultiColorsRhino(scriptRuntime, argList, ::detectsMultiColors.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            findPointByColorRhino(scriptRuntime, argList, ::findPointByColor.name)
        }

        @JvmStatic
        fun findPointByColorRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String, overriddenThreshold: Int? = null): OpencvPoint? {
            val (o, color, xOrOptions, y, width, height, threshold) = args
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.findPointByColor must be a ImageWrapper" }
                imageRef = image
                ApiImages.initOpenCvIfNeeded()
                val opt = when {
                    args.size < 3 -> newNativeObject()
                    args.size == 3 -> when {
                        xOrOptions.isJsNullish() -> newNativeObject()
                        else -> xOrOptions as? NativeObject
                    }
                    else -> null
                }
                return scriptRuntime.images.colorFinder.findPointByColor(
                    image,
                    Colors.toIntRhino(color),
                    overriddenThreshold ?: opt?.let { parseThreshold(it).roundToInt() } ?: coerceIntNumber(threshold, DEFAULT_COLOR_THRESHOLD),
                    buildRegionInternal(image, opt?.prop("region")?.takeUnless { it.isJsNullish() } ?: listOf(/* x = */ xOrOptions, y, width, height))
                ).also { image.shoot() }
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            @Suppress("DEPRECATION")
            findPointByColorRhino(scriptRuntime, argList, ::findColor.name)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColorInRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            @Suppress("DEPRECATION")
            findPointByColorRhino(scriptRuntime, argList, ::findColorInRegion.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColorExactly(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..6) { argList ->
            findPointByColorExactlyRhino(scriptRuntime, argList, ::findPointByColorExactly.name)
        }

        @JvmStatic
        fun findPointByColorExactlyRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): OpencvPoint? {
            return findPointByColorRhino(scriptRuntime, args, funcName, 0)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColorExactly(image, color, x, y, width, height)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findColorEquals(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..6) { argList ->
            @Suppress("DEPRECATION")
            findPointByColorExactlyRhino(scriptRuntime, argList, ::findColorEquals.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointsByColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            findPointsByColorRhino(scriptRuntime, argList, ::findPointsByColor.name)
        }

        @JvmStatic
        fun findPointsByColorRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): NativeArray {
            val (o, color, options) = args
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.findPointsByColor must be a ImageWrapper" }
                imageRef = image
                ApiImages.initOpenCvIfNeeded()
                val opt = options as? NativeObject ?: newNativeObject()
                return scriptRuntime.images.colorFinder.findPointsByColor(
                    image,
                    Colors.toIntRhino(color),
                    parseThreshold(opt).roundToInt(),
                    opt.inquire("region") { region -> buildRegionInternal(image, region) },
                ).toNativeArray()
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointsByColor(image, color, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findAllPointsForColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            @Suppress("DEPRECATION")
            findPointsByColorRhino(scriptRuntime, argList, ::findAllPointsForColor.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 3..4) { argList ->
            findPointByColorsRhino(scriptRuntime, argList, ::findPointByColors.name)
        }

        @JvmStatic
        fun findPointByColorsRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): OpencvPoint? {
            val (o, firstColor, paths, options) = args
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.findPointByColors must be a ImageWrapper" }
                imageRef = image
                require(paths is NativeArray) { "Argument \"paths\" ${paths.jsBrief()} for images.findPointByColors must be a JavaScript Array" }
                ApiImages.initOpenCvIfNeeded()
                val opt = options as? NativeObject ?: newNativeObject()
                return scriptRuntime.images.colorFinder.findPointByColors(
                    image,
                    Colors.toIntRhino(firstColor),
                    parseThreshold(opt).roundToInt(),
                    opt.inquire("region") { region -> buildRegionInternal(image, region) },
                    paths.flatMap { path ->
                        val (px, py, color) = path as NativeArray
                        listOf(coerceIntNumber(px), coerceIntNumber(py), Colors.toIntRhino(color))
                    }.toIntArray(),
                )
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByColors(image, firstColor, paths, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findMultiColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 3..4) { argList ->
            @Suppress("DEPRECATION")
            findPointByColorsRhino(scriptRuntime, argList, ::findMultiColors.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointsByColors(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 3..4) {
            val (o, firstColor, paths, options) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.findPointsByColors must be a ImageWrapper" }
            try {
                require(paths is NativeArray) { "Argument \"paths\" ${paths.jsBrief()} for images.findPointsByColors must be a JavaScript Array" }
                ApiImages.initOpenCvIfNeeded()
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
            } finally {
                image.shoot()
            }
        }

        /**
         * Counts the pixels matching a color (per-channel threshold) in the image or in `options.region`.
         * zh-CN: 统计图像 (或 `options.region` 区域) 中匹配指定颜色 (各通道阈值) 的像素数量.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun countPointsByColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (o, color, options) = argList
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.countPointsByColor must be a ImageWrapper" }
                imageRef = image
                ApiImages.initOpenCvIfNeeded()
                val opt = options as? NativeObject ?: newNativeObject()
                scriptRuntime.images.colorFinder.countPointsByColor(
                    image,
                    Colors.toIntRhino(color),
                    parseThreshold(opt).roundToInt(),
                    opt.inquire("region") { region -> buildRegionInternal(image, region) },
                )
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        /**
         * Average color of the image or of a region of it, as a color int.
         * zh-CN: 图像或其中一个区域的平均颜色 (颜色整数).
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getMeanColor(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (o, region) = argList
            var imageRef: ImageWrapper? = null
            try {
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.getMeanColor must be a ImageWrapper" }
                imageRef = image
                ApiImages.initOpenCvIfNeeded()
                val rect = region.takeUnless { it.isJsNullish() }?.let { buildRegionInternal(image, it) }
                scriptRuntime.images.getMeanColor(image, rect)
            } finally {
                ApiImages.shoot(imageRef)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findPointByImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            findPointByImageRhino(scriptRuntime, argList, ::findPointByImage.name)
        }

        @JvmStatic
        fun findPointByImageRhino(scriptRuntime: ScriptRuntime, args: Array<out Any?>, funcName: String): OpencvPoint? {
            val (oA, oB, xOrOptions, y, width, height, thresholdArg) = args
            var imageA: ImageWrapper? = null
            var imageB: ImageWrapper? = null
            try {
                val image = if (oA.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oA), true))?.oneShot() else oA
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.$funcName must be a ImageWrapper" }
                imageA = image
                val template = if (oB.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oB), true))?.oneShot() else oB
                require(template is ImageWrapper) { "Argument \"template\" ${oB.jsBrief()} for images.$funcName must be a ImageWrapper" }
                imageB = template
                ApiImages.initOpenCvIfNeeded()
                return when {
                    args.size > 2 && xOrOptions !is NativeObject -> {
                        val options = mapOf(
                            "region" to listOf(/* x = */ xOrOptions, y, width, height),
                            "threshold" to thresholdArg,
                        ).toNativeObject()
                        findPointByImageRhino(scriptRuntime, arrayOf(image, template, options), funcName)
                    }
                    else -> {
                        val opt = when {
                            args.size > 2 -> xOrOptions as? NativeObject
                            else -> null
                        } ?: newNativeObject()
                        val weakThreshold = parseWeakThreshold(opt, 0.6).toFloat()
                        val threshold = parseThreshold(opt, 0.9).toFloat()
                        val region = buildRegionInternal(image, opt.prop("region").takeUnless { it.isJsNullish() } ?: listOf(/* x = */ xOrOptions, y, width, height))
                        val level = parseIntNumber(opt.prop("level"), -1)
                        val scales = parseScalesInternal(opt, "images.$funcName")
                        scriptRuntime.images.findImage(image, template, weakThreshold, threshold, region, level, scales)
                    }
                }
            } finally {
                ApiImages.shoot(imageA, imageB)
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByImage(image, template, options)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            @Suppress("DEPRECATION")
            findPointByImageRhino(scriptRuntime, argList, ::findImage.name)
        }

        @Deprecated("Deprecated in Java", ReplaceWith("findPointByImage(image, template, x, y, width, height, threshold)"))
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun findImageInRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvPoint? = ensureArgumentsLengthInRange(args, 2..7) { argList ->
            @Suppress("DEPRECATION")
            findPointByImageRhino(scriptRuntime, argList, ::findImageInRegion.name)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matchTemplate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): MatchingResult = ensureArgumentsLengthInRange(args, 2..3) {
            val (oA, oB, options) = it
            var imageA: ImageWrapper? = null
            var imageB: ImageWrapper? = null
            try {
                val image = if (oA.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oA), true))?.oneShot() else oA
                require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.matchTemplate must be a ImageWrapper" }
                imageA = image
                val template = if (oB.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oB), true))?.oneShot() else oB
                require(template is ImageWrapper) { "Argument \"template\" ${template.jsBrief()} for images.matchTemplate must be a ImageWrapper" }
                imageB = template
                ApiImages.initOpenCvIfNeeded()
                val opt = options as? NativeObject ?: newNativeObject()
                val weakThreshold = parseWeakThreshold(opt, 0.6).toFloat()
                val threshold = parseThreshold(opt, 0.9).toFloat()
                val region = opt.inquire("region") { region -> buildRegionInternal(image, region) }
                val level = parseIntNumber(opt.prop("level"), -1)
                val max = parseIntNumber(opt.prop("max"), 5)
                val useTransparentMask = when {
                    opt.hasProp("useTransparentMask") -> coerceBoolean(opt.prop("useTransparentMask"))
                    opt.hasProp("transparentMask") -> /* @Compatible with Auto.js Pro. */ coerceBoolean(opt.prop("transparentMask"))
                    else -> false
                }
                val scales = parseScalesInternal(opt, "images.matchTemplate")
                MatchingResult(scriptRuntime.images.matchTemplate(image, template, weakThreshold, threshold, region, level, max, useTransparentMask, scales))
            } finally {
                ApiImages.shoot(imageA, imageB)
            }
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
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.toBase64 must be a ImageWrapper" }
            try {
                scriptRuntime.images.toBase64(image, parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_TO_BASE64_QUALITY))
            } finally {
                image.shoot()
            }
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
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.toBytes must be a ImageWrapper" }
            try {
                scriptRuntime.images.toBytes(image, parseImageFormat(format), parseQuality(quality, DEFAULT_IMAGE_TO_BYTES_QUALITY))
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun readPixels(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLength(args, 1) { argList ->
            val (o) = argList
            val image = when {
                o.isJsString() -> read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot()
                else -> o
            }
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.readPixels must be a ImageWrapper or a path" }
            try {
                val bitmap = image.bitmap
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                mapOf("data" to pixels, "width" to w, "height" to h).toNativeObject()
            } finally {
                // An image read from a path is one-shot and gets recycled here; a caller-owned image is kept.
                // zh-CN: 从路径读取的图像为一次性图像, 在此回收; 调用方持有的图像保持不变.
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matToImage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsOnlyOne(args) {
            ApiImages.initOpenCvIfNeeded()
            when (it) {
                is AutoJsMat -> ImageWrapper.ofMat(scriptRuntime, it)
                is OpencvMat -> ImageWrapper.ofMat(scriptRuntime, it)
                else -> throw WrappedIllegalArgumentException("Argument mat ${it.jsBrief()} is invalid for images.matToImage()")
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectAndComputeFeatures(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageFeatures = ensureArgumentsLengthInRange(args, 1..2) {
            val (o, options) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.detectAndComputeFeatures must be a ImageWrapper" }
            var mat: AutoJsMat? = null
            try {
                ApiImages.initOpenCvIfNeeded()
                val opt = fillDetectAndComputeFeaturesOptions(image.height, image.width, options as? NativeObject ?: newNativeObject())
                val rect = buildRegionInternal(image, opt.region)
                mat = scriptRuntime.images.newMat(image.mat, rect)
                val result = scriptRuntime.images.detectAndComputeFeatures(mat, opt.scale, opt.cvtColor, opt.method, opt.maxFeatures)
                /* The descriptor reports the scale it actually applied (1 when the option was non-positive). */
                ImageFeatures(result, result.scale, rect)
            } finally {
                image.shoot()
                mat?.release()
            }
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun matchFeatures(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ObjectFrame? = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (sceneArg, objectArg, options) = argList
            val opt = options as? NativeObject ?: newNativeObject()
            var sceneFeatures: ImageFeatures? = null
            var objectFeatures: ImageFeatures? = null
            var result: ImageFeatureMatching.FeatureMatchingResult? = null
            try {
                ApiImages.initOpenCvIfNeeded()
                sceneFeatures = toImageFeaturesInternal(scriptRuntime, sceneArg, opt, "sceneFeatures")
                objectFeatures = toImageFeaturesInternal(scriptRuntime, objectArg, opt, "objectFeatures")
                require(!sceneFeatures.isRecycled() && !objectFeatures.isRecycled()) {
                    "ImageFeatures arguments for images.matchFeatures must not be recycled"
                }
                val isBinaryDescriptor = objectFeatures.javaObject.descriptors.depth() == CvType.CV_8U
                val matcherType = opt.inquire("matcher") { parseMatcherTypeInternal(it) } ?: -1
                val drawMatches = opt.inquire("drawMatches") { scriptRuntime.files.nonNullPath(coerceString(it)) }
                val threshold = opt.inquire("threshold", ::coerceFloatNumber, if (isBinaryDescriptor) 0.8f else 0.7f)
                require(threshold > 0f && threshold <= 1f) { "Option \"threshold\" ($threshold) for images.matchFeatures must be in (0, 1]" }
                val ransacThreshold = opt.inquire("ransacThreshold", ::coerceNumber, ImageFeatureMatching.DEFAULT_RANSAC_REPROJECTION_THRESHOLD)
                require(ransacThreshold > 0 && ransacThreshold.isFinite()) { "Option \"ransacThreshold\" ($ransacThreshold) for images.matchFeatures must be a positive number" }
                val minInliers = opt.inquire("minInliers", ::coerceIntNumber, ImageFeatureMatching.MIN_POINTS_FOR_HOMOGRAPHY)

                result = ImageFeatureMatching.featureMatching(
                    /* sceneDesc = */ sceneFeatures.javaObject,
                    /* objectDesc = */ objectFeatures.javaObject,
                    ImageFeatureMatching.MatchingOptions(matcherType, threshold, ransacThreshold, minInliers, !drawMatches.isJsNullish()),
                )

                if (!drawMatches.isJsNullish()) {
                    val matchesImage = result.matches?.let { matToImage(scriptRuntime, arrayOf(it)) }
                    if (matchesImage != null) {
                        try {
                            save(scriptRuntime, arrayOf(matchesImage, drawMatches, "jpg", 100))
                        } finally {
                            matchesImage.recycle()
                        }
                    }
                }

                val region = sceneFeatures.region
                val scale = sceneFeatures.scale.takeIf { it > 0f } ?: 1f
                fun toSceneCoordinates(p: OpencvPoint) = OpencvPoint(p.x / scale + region.x, p.y / scale + region.y)

                val quad = result.quad ?: return@ensureArgumentsLengthInRange null
                require(quad.size == 4) { "Quad size of feature matching result must be 4 instead of ${quad.size}" }
                /* Corners keep the order of the object image: TL, TR, BL, BR. */
                val (tl, tr, bl, br) = quad.map(::toSceneCoordinates)
                ObjectFrame(tl, tr, bl, br, result.matchCount, result.inlierCount, result.points.map(::toSceneCoordinates))
            } finally {
                result?.release()
                ApiImages.shoot(sceneFeatures, objectFeatures)
            }
        }

        /**
         * Accepts ImageFeatures as they are, or detects the features of an ImageWrapper / image path as a one-shot
         * object, forwarding the detection-related options (method, scale, maxFeatures).
         * zh-CN: ImageFeatures 原样使用; ImageWrapper 或图片路径则按检测相关选项 (method, scale, maxFeatures) 检测特征并标记为一次性对象.
         */
        private fun toImageFeaturesInternal(scriptRuntime: ScriptRuntime, arg: Any?, opt: NativeObject, argName: String): ImageFeatures = when {
            arg is ImageFeatures -> arg
            arg is ImageWrapper || arg.isJsString() -> {
                val detectOptions = newNativeObject()
                listOf("method", "scale", "grayscale", "maxFeatures").forEach { key ->
                    if (opt.hasProp(key)) detectOptions.put(key, detectOptions, opt.prop(key))
                }
                detectAndComputeFeatures(scriptRuntime, arrayOf(arg, detectOptions)).setOneShot(true)
            }
            else -> throw WrappedIllegalArgumentException(
                "Argument \"$argName\" ${arg.jsBrief()} for images.matchFeatures must be a ImageFeatures, ImageWrapper or image path",
            )
        }

        /** Parses a DescriptorMatcher constant name (case-insensitive, "-" as "_") or number. zh-CN: 解析匹配器常量名或数值. */
        private fun parseMatcherTypeInternal(o: Any?): Int = when {
            o.isJsNumber() -> coerceIntNumber(o)
            else -> {
                val name = coerceString(o).trim().uppercase().replace("-", "_")
                runCatching { DescriptorMatcher::class.java.getField(name).getInt(null) }.getOrElse {
                    throw WrappedIllegalArgumentException("Unknown matcher ${o.jsBrief()} for images.matchFeatures; expected a DescriptorMatcher constant name such as \"FLANNBASED\" or \"BRUTEFORCE_HAMMING\"")
                }
            }
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
        fun quantize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): PngQuantizationResult =
            ensureArgumentsLengthInRange(args, 1..2) {
                val (o, optionsArg) = it
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) {
                    "Argument \"image\" ${image.jsBrief()} for images.quantize must be a ImageWrapper"
                }
                val options = when {
                    optionsArg.isJsNullish() -> NativeObject()
                    optionsArg is NativeObject -> optionsArg
                    else -> throw WrappedIllegalArgumentException(
                        "Argument \"options\" ${optionsArg.jsBrief()} for images.quantize must be an object",
                    )
                }
                try {
                    PngQuantizationResult(
                        scriptRuntime.images.quantize(
                            image,
                            parseImageQuantizationOptions(options, DEFAULT_IMAGE_SAVE_QUALITY),
                        ),
                    )
                } finally {
                    image.shoot()
                }
            }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun quantizeToFile(scriptRuntime: ScriptRuntime, args: Array<out Any?>): PngQuantizationFileResult =
            ensureArgumentsLengthInRange(args, 2..3) {
                val (o, pathArg, optionsArg) = it
                val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
                require(image is ImageWrapper) {
                    "Argument \"image\" ${image.jsBrief()} for images.quantizeToFile must be a ImageWrapper"
                }
                require(!pathArg.isJsNullish()) {
                    "Argument \"path\" ${pathArg.jsBrief()} for images.quantizeToFile must be non-nullish"
                }
                val options = when {
                    optionsArg.isJsNullish() -> NativeObject()
                    optionsArg is NativeObject -> optionsArg
                    else -> throw WrappedIllegalArgumentException(
                        "Argument \"options\" ${optionsArg.jsBrief()} for images.quantizeToFile must be an object",
                    )
                }
                val path = scriptRuntime.files.nonNullPath(coerceString(pathArg))
                try {
                    PngQuantizationFileResult(
                        path,
                        scriptRuntime.images.quantizeToFile(
                            image,
                            path,
                            parseImageQuantizationOptions(options, DEFAULT_IMAGE_SAVE_QUALITY),
                        ),
                    )
                } finally {
                    image.shoot()
                }
            }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun compress(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 1..3) {

            // @Comment by SuperMonster003 on May 19, 2025.
            //  ! Option `inSampleSize` performs pixel downsampling during decoding
            //  ! by discarding pixels to reduce resolution, rather than changing encoding quality.
            //  ! zh-CN:
            //  ! 选项 `inSampleSize` 在解码阶段做像素降采样,
            //  ! 通过降低分辨率丢弃像素的方式来实现文件压缩, 而非改变编码质量.
            //  !
            //  # val (o, compressLevelArg) = it
            //  # val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            //  # require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.compress must be a ImageWrapper" }
            //  # val compressLevel = coerceNumber(compressLevelArg, 1.0)
            //  # val level = 2.0.pow(floor(ln(compressLevel.coerceAtLeast(1.0)) / ln(2.0))).toInt()
            //  # val outputStream = ByteArrayOutputStream()
            //  # image.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            //  # val byteArray = outputStream.toByteArray()
            //  # val options = BitmapFactory.Options().apply { inSampleSize = level }
            //  # val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            //  # ImageWrapper.ofBitmap(bitmap).also { image.shoot() }

            val (o, format, quality) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.compress must be a ImageWrapper" }
            try {
                val niceFormat = parseImageFormat(format)
                if (quality is NativeObject) {
                    scriptRuntime.images.compress(
                        image,
                        niceFormat,
                        parseImageQuantizationOptions(quality, DEFAULT_IMAGE_COMPRESS_QUALITY),
                    )
                } else {
                    scriptRuntime.images.compress(
                        image,
                        niceFormat,
                        parseQuality(quality, DEFAULT_IMAGE_COMPRESS_QUALITY),
                    )
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun compressToBytes(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ByteArray = ensureArgumentsLengthInRange(args, 1..3) {
            val (o, format, quality) = it
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.compressToBytes must be a ImageWrapper" }
            try {
                val niceFormat = parseImageFormat(format)
                if (quality is NativeObject) {
                    scriptRuntime.images.compressToBytes(
                        image,
                        niceFormat,
                        parseImageQuantizationOptions(quality, DEFAULT_IMAGE_COMPRESS_QUALITY),
                    )
                } else {
                    scriptRuntime.images.compressToBytes(
                        image,
                        niceFormat,
                        parseQuality(quality, DEFAULT_IMAGE_COMPRESS_QUALITY),
                    )
                }
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun downsample(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ImageWrapper = ensureArgumentsLengthInRange(args, 3..4) { argList ->
            val (src, reqWidthArg, reqHeightArg, withAlphaArg) = argList
            val reqWidth = coerceIntNumber(reqWidthArg)
            val reqHeight = coerceIntNumber(reqHeightArg)
            val withAlpha = coerceBoolean(withAlphaArg, true)
            when (src) {
                is ByteArray -> BitmapUtils.downsample(src, reqWidth, reqHeight, withAlpha)
                is String -> when {
                    src.isUri() -> BitmapUtils.downsample(scriptRuntime.uiHandler.applicationContext, src.toUri(), reqWidth, reqHeight, withAlpha)
                    else -> BitmapUtils.downsample(scriptRuntime.files.nonNullPath(src), reqWidth, reqHeight, withAlpha)
                }
                is URL -> BitmapUtils.downsample(src, reqWidth, reqHeight, withAlpha)
                is Uri -> BitmapUtils.downsample(scriptRuntime.uiHandler.applicationContext, src, reqWidth, reqHeight, withAlpha)
                is Bitmap -> BitmapUtils.downsample(src, reqWidth, reqHeight, withAlpha)
                is ImageWrapper -> try {
                    BitmapUtils.downsample(src.bitmap, reqWidth, reqHeight, withAlpha)
                } finally {
                    src.shoot()
                }
                else -> throw WrappedIllegalArgumentException("Argument src ${src.jsBrief()} is invalid for images.downsample()")
            }?.let {
                ImageWrapper.ofBitmap(scriptRuntime, it)
            } ?: throw WrappedRuntimeException("Failed to downsample image from ${src.jsBrief()}")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getSize(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvSize = ensureArgumentsLength(args, 1) { argList ->
            val (o) = argList
            when (o) {
                is ImageWrapper -> o.size.also { o.shoot() }
                is OpencvMat, is Bitmap -> OpencvSize(getWidth(scriptRuntime, argList), getHeight(scriptRuntime, argList))
                is String -> BitmapFactory.Options().apply { inJustDecodeBounds = true }.let { opt ->
                    require(scriptRuntime.files.exists(o)) { "Image source ($o) doesn't exist" }
                    BitmapFactory.decodeFile(scriptRuntime.files.nonNullPath(o), opt)
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
                is ImageWrapper -> o.width.toDouble().also { o.shoot() }
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
                is ImageWrapper -> o.height.toDouble().also { o.shoot() }
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
            val image = if (o.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(o), true))?.oneShot() else o
            require(image is ImageWrapper) { "Argument \"image\" ${image.jsBrief()} for images.buildRegion must be a ImageWrapper" }
            try {
                require(region.isJsNullish() || region.isJsArray() || region is OpencvRect || region is AndroidRect) {
                    "Argument region ${region.jsSpecies()} is invalid for images.buildRegion"
                }
                buildRegionInternal(image, region)
            } finally {
                image.shoot()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun psnr(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::psnr.name, ImageSimilarity::psnr)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ssim(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::ssim.name, ImageSimilarity::ssim)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun mssim(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::mssim.name, ImageSimilarity::mssim)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun hist(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::hist.name, ImageSimilarity::hist)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun mse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::mse.name, ImageSimilarity::mse)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ncc(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            extractMatPair(scriptRuntime, it, ::ncc.name, ImageSimilarity::ncc)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isEqual(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 2) { argList ->
            val (oA, oB) = argList
            var imageARef: ImageWrapper? = null
            var imageBRef: ImageWrapper? = null
            try {
                val imageA = if (oA.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oA), true))?.oneShot() else oA
                require(imageA is ImageWrapper) { "Argument \"imageA\" ${imageA.jsBrief()} for images.isEqual must be a ImageWrapper}" }
                imageARef = imageA
                val imageB = if (oB.isJsString()) read(scriptRuntime, arrayOf<Any>(coerceString(oB), true))?.oneShot() else oB
                require(imageB is ImageWrapper) { "Argument \"imageB\" ${imageB.jsBrief()} for images.isEqual must be a ImageWrapper" }
                imageBRef = imageB
                ImageSimilarity.isEqual(imageA.mat, imageB.mat)
            } finally {
                ApiImages.shoot(imageARef, imageBRef)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getSimilarity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (oA, oB, options) = argList
            extractMatPair(scriptRuntime, arrayOf(oA, oB), ::getSimilarity.name) { matPair ->
                val (matA, matB) = matPair
                val metric = parseSimilarityMetric(options)
                val similarityMethod = runCatching {
                    ImageSimilarity::class.java.getDeclaredMethod(metric, OpencvMat::class.java, OpencvMat::class.java)
                }.getOrElse { throw WrappedIllegalArgumentException("Unknown similarity metric: $metric") }
                if (similarityMethod.returnType != Double::class.java) {
                    throw WrappedIllegalArgumentException("Similarity metric ($metric) method must return Double type. Found: ${similarityMethod.returnType}")
                }
                similarityMethod.invoke(ImageSimilarity, matA, matB) as Double
            }
        }

        @Suppress("FunctionName")
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun __buildRegion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): OpencvRect? = ensureArgumentsAtMost(args, 3) {
            val (region, imageWidth, imageHeight) = it
            if (imageWidth.isJsNullish() || imageHeight.isJsNullish()) {
                null
            } else {
                buildRegionForDimensions(region, coerceIntNumber(imageWidth), coerceIntNumber(imageHeight))
            }
        }

        internal fun parseSimilarityMetric(options: Any?): String {
            val opt = options as? NativeObject ?: return DEFAULT_IMAGE_SIMILARITY_METRIC
            return when {
                opt.hasProp("metric") -> coerceStringLowercase(opt.prop("metric"))
                opt.hasProp("type") -> coerceStringLowercase(opt.prop("type"))
                else -> DEFAULT_IMAGE_SIMILARITY_METRIC
            }.let { if (it == "pnsr") "psnr" else it }
        }

        internal fun buildRegionInternal(image: ImageWrapper, region: Any?) = buildRegionInternal(region, image.width, image.height)

        internal fun buildRegionForDimensions(region: Any?, imageWidth: Int, imageHeight: Int): OpencvRect {
            require(imageWidth >= 0 && imageHeight >= 0) {
                "Image dimensions must be non-negative: [$imageWidth, $imageHeight]"
            }
            val values = when {
                region is OpencvRect -> listOf(region.x, region.y, region.width, region.height)
                region is AndroidRect -> listOf(region.left, region.top, region.width(), region.height())
                region is List<*> -> region.map { value ->
                    value.takeUnless { it.isJsNullish() }?.let(::coerceIntNumber)
                }
                region.isJsNullish() -> emptyList()
                else -> throw WrappedIllegalArgumentException("Invalid region ${region.jsBrief()} for images.__buildRegion")
            }
            val x = values.getOrNull(0) ?: 0
            val y = values.getOrNull(1) ?: 0
            val width = values.getOrNull(2) ?: imageWidth - x
            val height = values.getOrNull(3) ?: imageHeight - y
            require(
                x >= 0 && y >= 0 && width >= 0 && height >= 0
                    && x.toLong() + width <= imageWidth
                    && y.toLong() + height <= imageHeight
            ) {
                "Out of region: region = [$x, $y, $width, $height], image.size = [$imageWidth, $imageHeight]"
            }
            return OpencvRect(x, y, width, height)
        }

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
                require(it >= 0) { "Width of region must be non-negative rather than $it" }
                require(x.toLong() + it <= imageWidth) { "Excessive width: region [$x + $it] > image [$imageWidth]" }
            }
            h = parseScreenMetric(parseNumber(h) { imageHeight - y }, ScreenMetrics.deviceScreenHeight).also {
                require(it >= 0) { "Height of region must be non-negative rather than $it" }
                require(y.toLong() + it <= imageHeight) { "Excessive height: region [$y + $it] > image [$imageHeight]" }
            }
            return OpencvRect(x, y, w, h)
        }

        private fun requestScreenCaptureInternal(scriptRuntime: ScriptRuntime, vararg args: Any?): ScriptPromiseAdapter {
            val rtImages = scriptRuntime.images
            val images = scriptRuntime.topLevelScope.prop("images") as ScriptableObject
            val emitFunc = images.prop("emit") as Callable

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
                    callFunction(scriptRuntime, emitFunc, getDeclarationScope(images), images, arrayOf("screen_capture_available", image))
                    /* @Reference to `org.autojs.autojs.runtime.api.Images.OnScreenCaptureAvailableListener#onCaptureAvailable` */
                    callFunction(scriptRuntime, emitFunc, getDeclarationScope(images), images, arrayOf("capture_available", image))
                    /* @Compatible with Auto.js Pro. */
                    callFunction(scriptRuntime, emitFunc, getDeclarationScope(images), images, arrayOf("screen_capture", image))
                }
            }

            return rtImages.requestScreenCapture(orientation, width, height, isAsync)
        }

        private fun parseScreenMetric(reference: Double, dimension: Int): Int = when (reference) {
            -1.0 -> dimension
            in 0.0..<1.0 -> (dimension * reference).roundToInt()
            else -> reference.roundToInt()
        }

        private fun parseIntNumber(x: Any?, def: Any? = null): Int {
            return parseNumber(x, def).roundToInt()
        }

        private fun parseNumber(x: Any?, def: Any? = null): Double {
            return coerceNumber(x, coerceNumber(if (def.isJsNullish()) 0 else def))
        }

        private fun parseIntNumber(x: Any?, def: () -> Any?): Int {
            return parseNumber(x, def).roundToInt()
        }

        private fun parseNumber(x: Any?, def: () -> Any?): Double {
            val tmp = coerceNumber(x, Double.NaN)
            return when {
                tmp.isNaN() -> def.invoke().let { result ->
                    if (result.isJsNullish()) 0.0 else coerceNumber(result)
                }
                else -> tmp
            }
        }

        private fun coerceIn(num: Double?, minValue: Double, maxValue: Double, defValue: Double = 0.0): Double {
            return parseNumber(num, defValue).coerceIn(minValue, maxValue)
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

        internal fun parseImageQuantizationOptions(
            options: NativeObject,
            defaultQuality: Int,
        ): PngQuantBridge.Options {
            val qualityArg = options.inquire("quality")
            val quality = parseQuality(qualityArg, defaultQuality)
            val maxColors = options.inquire("maxColors") { coerceIntNumber(it) }
                ?: PngQuantBridge.Options.DEFAULT_MAX_COLORS
            val speed = options.inquire("speed") { coerceIntNumber(it) }
                ?: PngQuantBridge.Options.DEFAULT_SPEED
            val minQuality = options.inquire("minQuality") { coerceIntNumber(it) }
                ?: if (qualityArg.isJsNullish()) PngQuantBridge.Options.DEFAULT_MIN_QUALITY else quality
            val maxQuality = options.inquire("maxQuality") { coerceIntNumber(it) } ?: quality
            val ditheringLevel = options.inquire("ditheringLevel") { coerceFloatNumber(it) }
                ?: PngQuantBridge.Options.DEFAULT_DITHERING_LEVEL
            val posterizeBits = options.inquire("posterizeBits") { coerceIntNumber(it) }
                ?: PngQuantBridge.Options.DEFAULT_POSTERIZE_BITS
            val preserveAlpha = options.inquire("preserveAlpha") { coerceBoolean(it) }
                ?: PngQuantBridge.Options.DEFAULT_PRESERVE_ALPHA
            val maxPixels = options.inquire("maxPixels") { coerceLongNumber(it) }
            val maxMemoryBytes = options.inquire("maxMemoryBytes") { coerceLongNumber(it) }
            return if (maxPixels == null && maxMemoryBytes == null) {
                PngQuantBridge.Options(
                    maxColors,
                    speed,
                    minQuality,
                    maxQuality,
                    ditheringLevel,
                    posterizeBits,
                    preserveAlpha,
                )
            } else {
                PngQuantBridge.Options(
                    maxColors,
                    speed,
                    minQuality,
                    maxQuality,
                    ditheringLevel,
                    posterizeBits,
                    preserveAlpha,
                    maxPixels ?: PngQuantBridge.Options.DEFAULT_MAX_PIXELS,
                    maxMemoryBytes ?: PngQuantBridge.Options.DEFAULT_MAX_MEMORY_BYTES,
                )
            }
        }

        private fun parseImageFormat(o: Any?): String = when {
            o.isJsNullish() -> "png"
            else -> when (val format = coerceString(o).lowercase().trim()) {
                "" -> "png"
                "png", "jpg", "jpeg", "webp" -> format
                "webp_lossless", "webp-lossless" -> format
                "webp_lossy", "webp-lossy" -> format
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

        /**
         * Reads the template scale factors from the "scales" (or "scale") option: a number or an array of numbers.
         * Returns null when the option is absent, meaning the original template size only.
         * zh-CN: 读取 "scales" (或 "scale") 选项中的模板缩放比例 (数字或数字数组); 选项缺失时返回 null, 表示仅使用原始尺寸.
         */
        private fun parseScalesInternal(options: Any?, funcName: String): DoubleArray? {
            val opt = options as? NativeObject ?: return null
            val raw = opt.prop("scales").takeUnless { it.isJsNullish() }
                ?: opt.prop("scale").takeUnless { it.isJsNullish() }
                ?: return null
            val items: List<Any?> = when (raw) {
                is Iterable<*> -> raw.toList()
                else -> listOf(raw)
            }
            require(items.isNotEmpty()) { "Option \"scales\" for $funcName must not be empty" }
            return items.map { item ->
                val value = coerceNumber(item)
                require(value.isFinite() && value > 0) { "Option \"scales\" ${item.jsBrief()} for $funcName must consist of positive finite numbers" }
                value
            }.toDoubleArray()
        }

        private fun resizeInternal(image: ImageWrapper, mat: AutoJsMat, size: OpencvSize, fx: Double, fy: Double, interpolation: Int) {
            Imgproc.resize(image.mat, mat, size, fx, fy, interpolation)
        }

        @SuppressLint("RtlHardcoded")
        private fun directionToGravityToConcat(direction: Any?): Int = when {
            direction.isJsNullish() -> Gravity.END
            direction.isJsString() -> {
                val s = coerceString(direction).uppercase().replace("-", "_")
                Gravity::class.java.getField(s).getInt(null)
            }
            direction.isJsNumber() -> coerceIntNumber(direction)
            else -> throw WrappedIllegalArgumentException("Unknown image concat direction: ${direction.jsBrief()}")
        }

        private fun parseBorderType(o: Any?): Int = when {
            o.isJsNullish() -> OpencvCore.BORDER_DEFAULT
            o.isJsString() -> {
                val s = coerceString(o)
                val name = when {
                    s.startsWith("BORDER_", true) -> s
                    else -> "BORDER_$s"
                }.uppercase().replace("-", "_")
                OpencvCore::class.java.getField(name).getInt(null)
            }
            o.isJsNumber() -> coerceIntNumber(o)
            else -> throw WrappedIllegalArgumentException("Unknown border type: ${o.jsBrief()}")
        }

        private fun parseThresholdType(o: Any?): Int = when {
            o.isJsNullish() -> Imgproc.THRESH_BINARY
            o.isJsString() -> {
                val s = coerceString(o)
                val name = when {
                    s.startsWith("THRESH_", true) -> s
                    else -> "THRESH_$s"
                }.uppercase().replace("-", "_")
                Imgproc::class.java.getField(name).getInt(null)
            }
            o.isJsNumber() -> coerceIntNumber(o)
            else -> throw WrappedIllegalArgumentException("Unknown threshold type: ${o.jsBrief()}")
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        private fun getDetectFeatureMethod(method: Any?): Int = when (method) {
            is Number -> method.toInt()
            is String -> {
                when (method.uppercase()) {
                    "SIFT" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT
                    "ORB" -> ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB
                    else -> throw WrappedIllegalArgumentException("Unknown method: ${method.jsBrief()}")
                }
            }
            else -> throw WrappedIllegalArgumentException("Non-recognized method: ${method.jsBrief()}")
        }

        // @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
        private fun fillDetectAndComputeFeaturesOptions(rows: Int, cols: Int, options: NativeObject): DetectAndComputeFeaturesOptions {
            val region = buildRegionInternal(options.prop("region"), cols, rows)
            /* A non-positive or missing scale means automatic; values above 1 enlarge small objects before detection. */
            val scale = options.inquire("scale") { coerceFloatNumber(it) }?.takeIf { it > 0f } ?: calcScale(region.height, region.width)
            require(scale.isFinite() && scale <= MAX_FEATURE_DETECTION_SCALE) {
                "Option \"scale\" ($scale) for images.detectAndComputeFeatures must be a positive number not greater than $MAX_FEATURE_DETECTION_SCALE"
            }
            /* Detection always works on grayscale (SIFT and ORB use intensity only); "grayscale" is accepted for compatibility. */
            val cvtColor = -1
            val method = getDetectFeatureMethod(options.inquire("method", ::coerceString, "SIFT"))
            val maxFeatures = options.inquire("maxFeatures", ::coerceIntNumber, 0)
            require(maxFeatures >= 0) { "Option \"maxFeatures\" ($maxFeatures) for images.detectAndComputeFeatures must not be negative" }

            return DetectAndComputeFeaturesOptions(scale, cvtColor, method, region, maxFeatures)
        }

        private fun calcScale(rows: Int, cols: Int, targetArea: Int = 1_000_000, maxSide: Int = 1600): Float {
            val total = rows * cols
            if (total < targetArea) return 1f
            val scaleByArea = sqrt(targetArea.toDouble() / total).toFloat()
            val scaleBySide = maxSide.toFloat() / max(rows, cols)
            return min(scaleByArea, scaleBySide).also {
                Log.d(TAG, "Calculated scale: $it")
            }
        }

        private fun <T> extractMatPair(scriptRuntime: ScriptRuntime, argList: Array<Any?>, funcName: String, func: (matPair: Pair<OpencvMat, OpencvMat>) -> T): T {
            var imageA: ImageWrapper? = null
            var imageB: ImageWrapper? = null
            try {
                var arg0: Any? = null
                var arg1: Any? = null
                when (argList.size) {
                    1 -> arg0 = argList[0]
                    2 -> {
                        arg0 = argList[0]
                        arg1 = argList[1]
                    }
                }
                val matA = when (arg0) {
                    is ImageWrapper -> arg0.also { imageA = it }.bgrMat
                    is OpencvMat -> arg0
                    is String -> read(scriptRuntime, arrayOf<Any>(arg0, true))!!.also { imageA = it }.bgrMat
                    else -> throw WrappedIllegalArgumentException(
                        "The first image argument for images.$funcName must be a ImageWrapper or a Mat instead of ${arg0.jsBrief()}"
                    )
                }
                val matB = when (arg1) {
                    is ImageWrapper -> arg1.also { imageB = it }.bgrMat
                    is OpencvMat -> arg1
                    is String -> read(scriptRuntime, arrayOf<Any>(arg1, true))!!.also { imageB = it }.bgrMat
                    else -> throw WrappedIllegalArgumentException(
                        "The second image argument for images.$funcName must be a ImageWrapper or a Mat instead of ${arg1.jsBrief()}"
                    )
                }
                return func(matA to matB)
            } finally {
                ApiImages.shoot(imageA, imageB)
            }
        }

        internal fun buildCircleResult(
            x: Double,
            y: Double,
            radius: Double,
            regionX: Int = 0,
            regionY: Int = 0,
        ): Map<String, Double> = linkedMapOf(
            "x" to x + regionX,
            "y" to y + regionY,
            "radius" to radius,
        )

        private class DetectAndComputeFeaturesOptions(val scale: Float, val cvtColor: Int, val method: Int, val region: OpencvRect, val maxFeatures: Int)

        private const val MAX_FEATURE_DETECTION_SCALE = 8f

    }

}
