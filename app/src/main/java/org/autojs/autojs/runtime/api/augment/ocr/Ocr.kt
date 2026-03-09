package org.autojs.autojs.runtime.api.augment.ocr

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.reflect.full.declaredMemberProperties
import android.graphics.Rect as AndroidRect
import org.autojs.autojs.runtime.api.augment.images.Images as AugmentableImages
import org.opencv.core.Rect as OpencvRect

class Ocr(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        "toString" to AS_LITERAL_TO_STRING,
        ::tap.name,
        ::recognizeText.name,
        ::detect.name,
        ::summary.name,
    )

    override val selfAssignmentGettersAndSetters = listOf(
        Triple("mode", Supplier<Any?> { scriptRuntime.ocr.mode }, Consumer<Any?> { tap(scriptRuntime, arrayOf(it)) }),
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun tap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { mode ->
            scriptRuntime.ocr.mode = mode.toOcrModeOrNull(scriptRuntime) ?: OcrMode.MLKIT.also {
                throw WrappedIllegalArgumentException("Unknown mode ${mode.jsSpecies()} for ocr.tap")
            }
            return@ensureArgumentsOnlyOne UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            recognizeTextWith(scriptRuntime, null, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            detectWith(scriptRuntime, null, argList)
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

        internal fun recognizeTextWith(scriptRuntime: ScriptRuntime, overrideMode: OcrMode?, unwrappedArgs: Array<out Any?>): NativeArray {
            return dispatchOcrWith<String>(scriptRuntime, ::recognizeText.name, unwrappedArgs, overrideMode).toNativeArray()
        }

        internal fun detectWith(scriptRuntime: ScriptRuntime, overrideMode: OcrMode?, unwrappedArgs: Array<out Any?>): NativeArray {
            return dispatchOcrWith<OcrResult>(scriptRuntime, ::detect.name, unwrappedArgs, overrideMode) { results, rect ->
                results.apply { forEach { it.bounds.offset(rect.x, rect.y) } }
            }.toNativeArray()
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <T> dispatchOcrWith(
            scriptRuntime: ScriptRuntime,
            funcName: String,
            unwrappedArgs: Array<out Any?>,
            overrideMode: OcrMode? = null,
            resultsHandler: ((results: List<T>, rect: OpencvRect) -> List<T>)? = null,
        ): List<T> {
            val (arg0, arg1, arg2) = unwrappedArgs

            return when {
                arg0.isJsString() -> {

                    // @Signature
                    // funcName(imgPath: string, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(imgPath: string, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val imgPath = arg0
                    val img = AugmentableImages.read(scriptRuntime, arrayOf(imgPath)) ?: throw WrappedIllegalArgumentException(
                        "Invalid image of path \"$imgPath\" for ocr.$funcName(img, options?)",
                    )

                    // @Overload
                    // funcName(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(img.oneShot(), arg1, arg2), overrideMode, resultsHandler)
                }
                arg0 !is ImageWrapper -> {
                    // @Signature
                    // funcName(options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    // @Overload
                    // funcName(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(capt, arg0, arg1), overrideMode, resultsHandler)
                }
                shouldTakenAsRegion(arg1) -> {

                    // @Signature funcName(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val region = arg1
                    val options = newNativeObject().also { o ->
                        o.defineProp("region", region)
                    }

                    // @Overload funcName(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(arg0, options), overrideMode, resultsHandler)
                }
                else -> {

                    // @Signature funcName(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject()
                    val region = opt.prop("region") ?: return emptyList()

                    var shouldShoot = false
                    val (image, options) = when {
                        region.isJsNullish() -> img to opt
                        else -> {
                            val clip = AugmentableImages.clip(scriptRuntime, arrayOf(img, region))
                            shouldShoot = true
                            clip.oneShot() to opt
                        }
                    }
                    val optMode = overrideMode ?: opt.inquire("mode", scriptRuntime.ocr.mode)
                    val ocrMode = optMode.toOcrModeOrNull(scriptRuntime)
                        ?: throw WrappedIllegalArgumentException("Cannot call ocr.$funcName with an unknown mode: ${optMode.jsBrief()}")
                    val prop = OcrMode::class.declaredMemberProperties.firstOrNull { it.name.contentEquals(funcName, true) }
                        ?: throw WrappedIllegalArgumentException("Cannot find ocr dispatcher property for $funcName")
                    val fn = prop.get(ocrMode) as? (ScriptRuntime, ImageWrapper, NativeObject) -> List<T>
                        ?: throw WrappedIllegalArgumentException("Dispatcher type mismatch for $funcName")
                    var results = fn.invoke(scriptRuntime, image, options)
                    resultsHandler?.let {
                        val rect = AugmentableImages.buildRegion(scriptRuntime, arrayOf(img, region))
                        results = resultsHandler(results, rect)
                    }
                    if (shouldShoot) img.shoot()
                    results
                }
            }
        }

        private fun shouldTakenAsRegion(o: Any?) = o is OpencvRect || o is AndroidRect || o is NativeArray

        private fun Any?.toOcrModeOrNull(scriptRuntime: ScriptRuntime? = null) = when (this) {
            is ScriptableObject -> when (this) {
                scriptRuntime?.augmentedOcrMLKit -> OcrMode.MLKIT
                scriptRuntime?.augmentedOcrPaddle -> OcrMode.PADDLE
                scriptRuntime?.augmentedOcrRapid -> OcrMode.RAPID
                else -> null
            }
            else -> {
                val modeValue = coerceString(this, "")
                OcrMode.entries.find { it.value.contentEquals(modeValue, true) }
            }
        }

        @Suppress("unused")
        private fun <T> nullImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject) = emptyList<T>()

        enum class OcrMode(
            val value: String,
            val recognizeText: ((ScriptRuntime, ImageWrapper, NativeObject) -> List<String>)?,
            val detect: ((ScriptRuntime, ImageWrapper, NativeObject) -> List<OcrResult>)?,
        ) {
            MLKIT("mlkit", OcrMLKit::recognizeTextImpl, OcrMLKit::detectImpl),
            PADDLE("paddle", OcrPaddle::recognizeTextImpl, OcrPaddle::detectImpl),
            RAPID("rapid", OcrRapid::recognizeTextImpl, OcrRapid::detectImpl),
            UNKNOWN("unknown", ::nullImpl, ::nullImpl);
        }

    }

}
