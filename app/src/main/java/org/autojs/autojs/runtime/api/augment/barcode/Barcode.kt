package org.autojs.autojs.runtime.api.augment.barcode

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.WrappedBarcode
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import com.google.mlkit.vision.barcode.common.Barcode as GoogleBarcode
import org.autojs.autojs.runtime.api.augment.images.Images as AugmentableImages

class Barcode(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::recognizeText.name,
        ::recognizeTexts.name,
        ::detect.name,
        ::detectAll.name,
    )

    override fun invoke(vararg args: Any?): Any? = ensureArgumentsAtMost(args, 3) { argList ->
        when {
            argList.firstOrNull() as? Boolean == true -> {
                recognizeTexts(scriptRuntime, argList)
            }
            else -> recognizeText(scriptRuntime, argList)
        }
    }

    interface Parseable {
        fun parse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): BarcodeDetectData
    }

    @Suppress("ArrayInDataClass")
    data class BarcodeDetectData(val img: ImageWrapper, val formats: IntArray, val enableAllPotentialBarcodes: Boolean, val options: NativeObject)

    @Suppress("UNUSED_PARAMETER")
    companion object : Parseable {

        private const val FORMAT_PREFIX = "FORMAT_"

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 3) { argList ->
            recognizeTextInternal(scriptRuntime, argList, ::parse)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeTexts(scriptRuntime: ScriptRuntime, args: Array<out Any?>): List<String> = ensureArgumentsAtMost(args, 3) { argList ->
            recognizeTextsInternal(scriptRuntime, argList, ::parse)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 3) { argList ->
            detectInternal(scriptRuntime, argList, ::parse)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detectAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): List<WrappedBarcode> = ensureArgumentsAtMost(args, 3) { argList ->
            detectAllInternal(scriptRuntime, argList, ::parse)
        }

        override fun parse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): BarcodeDetectData = ensureArgumentsAtMost(args, 3) { argList ->
            val (arg0, arg1, arg2) = argList

            when (arg0) {
                is String -> {

                    // @Signature
                    // detectAll(imgPath: string): Barcode.Result[];
                    // detectAll(imgPath: string, options: DetectOptions): Barcode.Result[];

                    val imgPath = arg0
                    val img = AugmentableImages.read(scriptRuntime, arrayOf(imgPath)) ?: throw WrappedIllegalArgumentException(
                        "Invalid image of path \"$imgPath\" for barcode.detectAll(img, options?)",
                    )

                    // @Overload
                    // detectAll(img: ImageWrapper): Barcode.Result[];
                    // detectAll(img: ImageWrapper, options: DetectOptions): Barcode.Result[];
                    parse(scriptRuntime, arrayOf(img.oneShot(), arg1, arg2))
                }
                !is ImageWrapper -> {
                    // @Signature detectAll(options?: DetectOptions): Barcode.Result[];

                    // @Overload detectAll(img: ImageWrapper, options?: DetectOptions): Barcode.Result[];
                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    parse(scriptRuntime, arrayOf(capt, arg0, arg1))
                }
                else -> {

                    // @Signature detectAll(img: ImageWrapper, options?: DetectOptions): Barcode.Result[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject()

                    val transformer = mapOf(
                        "FORMAT_QRCODE" to "FORMAT_QR_CODE"
                    )
                    val formats = run {
                        val optFormat = opt.prop("format")
                        if (optFormat.isJsNullish()) return@run listOf<Int>()
                        val formatList = if (optFormat !is NativeArray) listOf(optFormat) else optFormat.toList()
                        formatList.map { format ->
                            when (format) {
                                is Number -> coerceIntNumber(format)
                                is String -> {
                                    val tmp = format.uppercase().replace(Regex("\\W+"), "_").let { transformed ->
                                        when {
                                            transformed.startsWith(FORMAT_PREFIX) -> transformed
                                            else -> "$FORMAT_PREFIX$transformed"
                                        }
                                    }.let { transformer[it] ?: it }
                                    coerceIntNumber(GoogleBarcode::class.java.declaredFields.find { it.name == tmp && it.type == Int::class.java }?.get(null))
                                }
                                else -> throw WrappedIllegalArgumentException("Unknown format ${format.jsBrief()} for barcode")
                            }
                        }
                    }.toIntArray()

                    val enableAllPotentialBarcodes = opt.inquire("enableAllPotentialBarcodes", ::coerceBoolean, false)

                    BarcodeDetectData(img, formats, enableAllPotentialBarcodes, opt)
                }
            }
        }

        fun recognizeTextInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): Any? {
            val (img, formats, enableAllPotentialBarcodes, options) = parser.invoke(scriptRuntime, argList)
            val isAll = options.inquire("isAll") { coerceBoolean(it, false) } ?: false
            return when {
                isAll -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false).mapNotNull { it.rawValue }
                else -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, true).firstOrNull()?.rawValue
            }
        }

        fun recognizeTextsInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): List<String> {
            return detectAll(scriptRuntime, argList).mapNotNull { it.rawValue }
        }

        fun detectInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): Any? {
            val (img, formats, enableAllPotentialBarcodes, options) = parser.invoke(scriptRuntime, argList)
            val isAll = options.inquire("isAll") { coerceBoolean(it, false) } ?: false
            return when {
                isAll -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false)
                else -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, true).firstOrNull()
            }
        }

        fun detectAllInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): List<WrappedBarcode> {
            val (img, formats, enableAllPotentialBarcodes) = parser.invoke(scriptRuntime, argList)
            return scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false)
        }

    }

}