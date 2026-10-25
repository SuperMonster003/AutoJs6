package org.autojs.autojs.runtime.api.modules.ocr

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
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
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import android.graphics.Rect as AndroidRect
import org.autojs.autojs.runtime.api.modules.images.Images as AugmentableImages
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
        Triple("mode", Supplier<Any?> { currentMode(scriptRuntime)?.value.orEmpty() }, Consumer<Any?> { tap(scriptRuntime, arrayOf(it)) }),
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun tap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { mode ->
            scriptRuntime.ocr.mode = mode.toOcrModeOrNull(scriptRuntime)
                ?: throw WrappedIllegalArgumentException("Unknown mode ${mode.jsSpecies()} for ocr.tap")
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

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun summary(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            val available = availableModes()
            val requested = scriptRuntime.ocr.mode
            val current = requested.takeUnless { it == OcrMode.AUTO } ?: available.firstOrNull()
            listOf(
                "[ OCR summary ]",
                "Current mode: ${current?.value.orEmpty()}",
                "Mode selection: ${requested.value}",
                "Available modes: [ ${available.joinToString(", ") { it.value }} ]"
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
            selection: Selection? = null,
            resultsHandler: ((results: List<T>, rect: OpencvRect) -> List<T>)? = null,
        ): List<T> {
            val (arg0, arg1, arg2) = unwrappedArgs
            val selected = selection ?: try {
                selectForCall(
                    scriptRuntime,
                    overrideMode,
                    (if (arg0 is ImageWrapper || arg0.isJsString()) arg1 else arg0) as? NativeObject,
                    funcName,
                )
            } catch (e: Throwable) {
                (arg0 as? ImageWrapper)?.shoot()
                throw e
            }

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
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(img.oneShot(), arg1, arg2), overrideMode, selected, resultsHandler)
                }
                arg0 !is ImageWrapper -> {
                    // @Signature
                    // funcName(options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    // @Overload
                    // funcName(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    // funcName(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    require(capt is ImageWrapper) { "Failed to capture a valid image for ocr.$funcName" }
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(capt.oneShot(), arg0, arg1), overrideMode, selected, resultsHandler)
                }
                shouldTakenAsRegion(arg1) -> {

                    // @Signature funcName(img: ImageWrapper, region: OmniRegion): org.autojs.autojs.runtime.api.OcrResult[];

                    val region = arg1
                    val options = newNativeObject(scriptRuntime.topLevelScope).also { o ->
                        o.defineProp("region", region)
                    }

                    // @Overload funcName(img: ImageWrapper, options: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];
                    dispatchOcrWith(scriptRuntime, funcName, arrayOf(arg0, options), overrideMode, selected, resultsHandler)
                }
                else -> {

                    // @Signature funcName(img: ImageWrapper, options?: DetectOptionsMLKit | DetectOptionsPaddle): org.autojs.autojs.runtime.api.OcrResult[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject(scriptRuntime.topLevelScope)
                    var clippedImage: ImageWrapper? = null
                    try {
                        val region = opt.prop("region")
                        val rect = region.takeUnless { it.isJsNullish() }?.let {
                            AugmentableImages.buildRegionInternal(img, it)
                        }
                        val image = rect?.let {
                            scriptRuntime.images.clip(img, it.x, it.y, it.width, it.height)
                                .oneShot()
                                .also { clip -> clippedImage = clip }
                        } ?: img
                        val prop = OcrMode.dispatcherProperty(funcName)
                            ?: throw WrappedIllegalArgumentException("Cannot find ocr dispatcher property for $funcName")
                        val fn = prop.get(selected.mode) as? (ScriptRuntime, ImageWrapper, NativeObject, OcrPluginHost.Discovered?) -> List<T>
                            ?: throw WrappedIllegalArgumentException("Dispatcher type mismatch for $funcName")
                        var results = fn.invoke(scriptRuntime, image, opt, selected.plugin)
                        if (rect != null) {
                            resultsHandler?.let { results = it(results, rect) }
                        }
                        results
                    } finally {
                        clippedImage?.shoot()
                        img.shoot()
                    }
                }
            }
        }

        private fun shouldTakenAsRegion(o: Any?) = o is OpencvRect || o is AndroidRect || o is NativeArray

        private fun currentMode(scriptRuntime: ScriptRuntime): OcrMode? {
            return scriptRuntime.ocr.mode.takeUnless { it == OcrMode.AUTO } ?: availableModes().firstOrNull()
        }

        private fun availableModes(): List<OcrMode> {
            val engines = OcrPluginHost.availableEngines(globalContext).toMutableSet()
            if (isInrt) {
                engines.remove("paddle-ocr")
                engines.remove("rapid-ocr")
                val paddleAvailable = runCatching {
                    ApkBuilder.Lib.PADDLE_OCR.ensureLibFiles()
                    PaddleOcrEmbeddedEngine.resolveVariantName(globalContext) != null
                }.getOrDefault(false)
                if (paddleAvailable) engines += "paddle-ocr"
                if (org.autojs.autojs.runtime.api.OcrRapid.isEmbeddedAvailable(globalContext)) engines += "rapid-ocr"
            }
            return OcrMode.entries.filter { it.engine in engines }
        }

        internal data class Selection(val mode: OcrMode, val plugin: OcrPluginHost.Discovered? = null)

        private fun selectForCall(scriptRuntime: ScriptRuntime, overrideMode: OcrMode?, options: NativeObject?, funcName: String): Selection {
            val optMode = overrideMode ?: if (options != null && ScriptableObject.hasProperty(options, "mode")) {
                options.prop("mode")
            } else scriptRuntime.ocr.mode
            val requested = optMode.toOcrModeOrNull(scriptRuntime)
                ?: throw WrappedIllegalArgumentException("Cannot call ocr.$funcName with an unknown mode: ${optMode.jsBrief()}")
            if (requested != OcrMode.AUTO) return Selection(requested)
            if (isInrt) {
                val embedded = availableModes().firstOrNull()
                    ?: throw IllegalStateException(globalContext.getString(org.autojs.autojs6.R.string.error_no_available_ocr_engine))
                return Selection(embedded)
            }
            val plugin = runBlocking(scriptRuntime.coroutineContext) {
                OcrPluginHost.selectOrThrow(
                    context = globalContext,
                    engineId = options?.inquire(PaddleOcrOptionExtraKeys.ENGINE_ID, ::coerceString, "")?.takeIf { it.isNotBlank() },
                    engine = options?.inquire(PaddleOcrOptionExtraKeys.ENGINE, ::coerceString, "")?.takeIf { it.isNotBlank() },
                    variant = options?.inquire(PaddleOcrOptionExtraKeys.VARIANT, ::coerceString, "")?.takeIf { it.isNotBlank() },
                    moduleLabel = "ML Kit OCR / Paddle OCR / Rapid OCR",
                    automatic = true,
                )
            }
            val mode = OcrMode.entries.first { it.engine == plugin.pluginInfo?.engine }
            return Selection(mode, plugin)
        }

        private fun Any?.toOcrModeOrNull(scriptRuntime: ScriptRuntime? = null) = when (this) {
            is OcrMode -> this
            is ScriptableObject -> when (this) {
                scriptRuntime?.augmentedOcrMLKit -> OcrMode.MLKIT
                scriptRuntime?.augmentedOcrPaddle -> OcrMode.PADDLE
                scriptRuntime?.augmentedOcrRapid -> OcrMode.RAPID
                else -> null
            }
            else -> {
                val modeValue = if (isJsNullish()) "" else coerceString(this, "")
                if (modeValue.isEmpty()) OcrMode.AUTO else OcrMode.entries.find { it.value.contentEquals(modeValue, true) }
            }
        }

        enum class OcrMode(
            val value: String,
            val recognizeText: ((ScriptRuntime, ImageWrapper, NativeObject, OcrPluginHost.Discovered?) -> List<String>)?,
            val detect: ((ScriptRuntime, ImageWrapper, NativeObject, OcrPluginHost.Discovered?) -> List<OcrResult>)?,
            val engine: String? = null,
        ) {
            MLKIT("mlkit", OcrMLKit::recognizeTextImpl, OcrMLKit::detectImpl, "mlkit-ocr"),
            PADDLE("paddle", OcrPaddle::recognizeTextImpl, OcrPaddle::detectImpl, "paddle-ocr"),
            RAPID("rapid", OcrRapid::recognizeTextImpl, OcrRapid::detectImpl, "rapid-ocr"),
            AUTO("auto", null, null);

            companion object {

                internal fun dispatcherProperty(name: String): KProperty1<OcrMode, *>? =
                    OcrMode::class.declaredMemberProperties.firstOrNull { it.name.equals(name, ignoreCase = true) }

            }
        }

    }

}
