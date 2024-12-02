package org.autojs.autojs.runtime.api.augment.ocr

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.images.Images
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import java.util.function.Consumer
import java.util.function.Supplier
import android.graphics.Rect as AndroidRect
import org.autojs.autojs.runtime.api.augment.images.Images as AugmentableImages
import org.opencv.core.Rect as OpencvRect

class Ocr(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        "toString",
        ::tap.name,
        ::recognizeText.name,
        ::detect.name,
        ::summary.name,
    )

    override val selfAssignmentGettersAndSetters = listOf(
        Triple("mode", Supplier<Any?> { scriptRuntime.ocr.mode }, Consumer<Any?> { tap(scriptRuntime, arrayOf(it)) }),
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object {

        enum class OcrMode(val value: String) {
            MLKIT("mlkit"),
            PADDLE("paddle"),
            RAPID("rapid"),
            UNKNOWN("unknown")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun tap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { mode ->
            scriptRuntime.ocr.mode = when (mode) {
                scriptRuntime.augmentedOcrMLKit -> OcrMode.MLKIT
                scriptRuntime.augmentedOcrPaddle -> OcrMode.PADDLE
                scriptRuntime.augmentedOcrRapid -> OcrMode.RAPID
                is String -> when (mode.lowercase()) {
                    OcrMode.MLKIT.value -> OcrMode.MLKIT
                    OcrMode.PADDLE.value -> OcrMode.PADDLE
                    OcrMode.RAPID.value -> OcrMode.RAPID
                    else -> null
                }
                else -> null
            } ?: OcrMode.MLKIT.also { throw WrappedIllegalArgumentException("Unknown mode ${mode.jsSpecies()} for ocr.tap") }
            return@ensureArgumentsOnlyOne UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            val (arg0, arg1, arg2) = argList

            when {
                arg0 is String -> {

                    // @Signature
                    // recognizeText(imgPath: string, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                    // recognizeText(imgPath: string, region: OmniRegion): string[];

                    val imgPath = arg0
                    val img = AugmentableImages.read(scriptRuntime, arrayOf(imgPath)) ?: throw WrappedIllegalArgumentException(
                        "Invalid image of path \"$imgPath\" for ocr.recognizeText(img, options?)",
                    )

                    // @Overload
                    // recognizeText(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                    // recognizeText(img: ImageWrapper, region: OmniRegion): string[];
                    recognizeText(scriptRuntime, arrayOf(img.oneShot(), arg1, arg2))
                }
                arg0 !is ImageWrapper -> {
                    // @Signature
                    // recognizeText(options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                    // recognizeText(region: OmniRegion): string[];

                    // @Overload
                    // recognizeText(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                    // recognizeText(img: ImageWrapper, region: OmniRegion): string[];

                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    recognizeText(scriptRuntime, arrayOf(capt, arg0, arg1))
                }
                shouldTakenAsRegion(arg1) -> {

                    // @Signature recognizeText(img: ImageWrapper, region: OmniRegion): string[];

                    val region = arg1
                    val options = newNativeObject().also { o ->
                        o.defineProp("region", region)
                    }

                    // @Overload recognizeText(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): string[];
                    recognizeText(scriptRuntime, arrayOf(arg0, options))
                }
                else -> {

                    // @Signature recognizeText(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): string[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject()
                    when (val region = opt.prop("region")) {
                        null -> newNativeArray()
                        else -> {
                            var shouldShoot = false
                            val (image, options) = when {
                                region.isJsNullish() -> img to opt
                                else -> {
                                    val clip = AugmentableImages.clip(scriptRuntime, arrayOf(img, region))
                                    shouldShoot = true
                                    clip.oneShot() to opt
                                }
                            }
                            val results = when (opt.prop("mode").takeIf { it is OcrMode } ?: scriptRuntime.ocr.mode) {
                                OcrMode.MLKIT -> OcrMLKit.recognizeTextInternal(scriptRuntime, image, options)
                                OcrMode.PADDLE -> OcrPaddle.recognizeTextInternal(scriptRuntime, image, options)
                                OcrMode.RAPID -> OcrRapid.recognizeTextInternal(scriptRuntime, image, options)
                                else -> throw WrappedIllegalArgumentException("Cannot call ocr.recognizeText with an unknown mode")
                            }
                            if (shouldShoot) img.shoot()
                            results.toNativeArray()
                        }
                    }
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            val (arg0, arg1, arg2) = argList

            when {
                arg0 is String -> {

                    // @Signature
                    // detect(imgPath: string, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // detect(imgPath: string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val imgPath = arg0
                    val img = AugmentableImages.read(scriptRuntime, arrayOf(imgPath)) ?: throw WrappedIllegalArgumentException(
                        "Invalid image of path \"$imgPath\" for ocr.detect(img, options?)",
                    )

                    // @Overload
                    // detect(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // detect(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
                    detect(scriptRuntime, arrayOf(img.oneShot(), arg1, arg2))
                }
                arg0 !is ImageWrapper -> {
                    // @Signature
                    // detect(options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // detect(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    // @Overload
                    // detect(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // detect(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    detect(scriptRuntime, arrayOf(capt, arg0, arg1))
                }
                shouldTakenAsRegion(arg1) -> {

                    // @Signature detect(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val region = arg1
                    val options = newNativeObject().also { o ->
                        o.defineProp("region", region)
                    }

                    // @Overload detect(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    detect(scriptRuntime, arrayOf(arg0, options))
                }
                else -> {

                    // @Signature detect(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject()
                    when (val region = opt.prop("region")) {
                        null -> newNativeArray()
                        else -> {
                            var shouldShoot = false
                            val (image, options) = when {
                                region.isJsNullish() -> img to opt
                                else -> {
                                    val clip = AugmentableImages.clip(scriptRuntime, arrayOf(img, region))
                                    shouldShoot = true
                                    clip.oneShot() to opt
                                }
                            }
                            val results = when (scriptRuntime.ocr.mode) {
                                OcrMode.MLKIT -> OcrMLKit.detectInternal(scriptRuntime, image, options)
                                OcrMode.PADDLE -> OcrPaddle.detectInternal(scriptRuntime, image, options)
                                OcrMode.RAPID -> OcrRapid.detectInternal(scriptRuntime, image, options)
                                else -> throw WrappedIllegalArgumentException("Cannot call ocr.detect with an unknown mode")
                            }.also {
                                val rect = Images.buildRegion(scriptRuntime, arrayOf(img, region))
                                it.forEach { result -> result.bounds.offset(rect.x, rect.y) }
                            }
                            if (shouldShoot) img.shoot()
                            results.toNativeArray()
                        }
                    }
                }
            }
        }

        @Suppress("EnumValuesSoftDeprecate")
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun summary(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            listOf(
                "[ OCR summary ]",
                "Current mode: ${scriptRuntime.ocr.mode.value}",
                "Available modes: [ ${OcrMode.values().filterNot { it == OcrMode.UNKNOWN }.joinToString(", ") { it.value }} ]"
            ).joinToString("\n")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toString(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            summary(scriptRuntime, args)
        }

        fun commonRecognizeText(scriptRuntime: ScriptRuntime, mode: OcrMode, vararg args: Any?): NativeArray = ensureArgumentsAtMost(args, 2) { argList ->
            commonInvoker(scriptRuntime, mode, ::recognizeText, *argList)
        }

        fun commonDetect(scriptRuntime: ScriptRuntime, mode: OcrMode, vararg args: Any?): NativeArray = ensureArgumentsAtMost(args, 2) { argList ->
            commonInvoker(scriptRuntime, mode, ::detect, *argList)
        }

        private fun commonInvoker(
            scriptRuntime: ScriptRuntime,
            mode: OcrMode,
            invoker: (ScriptRuntime, Array<out Any?>) -> NativeArray,
            vararg args: Any?,
        ): NativeArray = ensureArgumentsAtMost(args, 2) { argList ->
            val (arg0, arg1) = argList
            when (argList.size) {
                2 -> when {
                    shouldTakenAsRegion(arg1) -> newNativeObject().let { options ->
                        options.defineProp("region", arg1)
                        options.defineProp("mode", mode)
                        invoker(scriptRuntime, arrayOf(arg0, options))
                    }
                    arg1 is NativeObject -> arg1.let { options ->
                        options.defineProp("mode", mode)
                        invoker(scriptRuntime, arrayOf(arg0, options))
                    }
                    else -> throw WrappedIllegalArgumentException("Unknown argument[1] ${arg1.jsBrief()} for ${mode.value}")
                }
                1 -> when {
                    arg0 is NativeObject -> invoker(scriptRuntime, arrayOf(arg0.also { options ->
                        options.defineProp("mode", mode)
                    }))
                    arg0 is ImageWrapper -> invoker(scriptRuntime, arrayOf(arg0, newNativeObject().also { options ->
                        options.defineProp("mode", mode)
                    }))
                    shouldTakenAsRegion(arg0) -> newNativeObject().let { options ->
                        options.defineProp("region", arg0)
                        options.defineProp("mode", mode)
                        invoker(scriptRuntime, arrayOf(arg0, options))
                    }
                    else -> throw WrappedIllegalArgumentException("Unknown argument[0] ${arg0.jsBrief()} for ${mode.value}")
                }
                0 -> newNativeObject().let { options ->
                    options.defineProp("mode", mode)
                    invoker(scriptRuntime, arrayOf(options))
                }
                else -> throw ShouldNeverHappenException()
            }
        }

        private fun shouldTakenAsRegion(o: Any?) = o is OpencvRect || o is AndroidRect || o is NativeArray

    }

}