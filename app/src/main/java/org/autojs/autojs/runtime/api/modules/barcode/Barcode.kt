package org.autojs.autojs.runtime.api.modules.barcode

import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.WrappedBarcode
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.autojs.plugin.mlkit.barcode.api.BarcodeFormats
import org.autojs.autojs.runtime.api.modules.images.Images as AugmentableImages

class Barcode(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    fun invoke(vararg args: Any?): Any? = ensureArgumentsAtMost(args, 3) { argList ->
        when {
            shouldTakeAsAll(argList) -> recognizeTexts(scriptRuntime, argList)
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
                    parseOwnedImage(scriptRuntime, img, arg1, arg2)
                }
                !is ImageWrapper -> {
                    // @Signature detectAll(options?: DetectOptions): Barcode.Result[];

                    // @Overload detectAll(img: ImageWrapper, options?: DetectOptions): Barcode.Result[];
                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    require(capt is ImageWrapper) { "Failed to capture a valid image for barcode" }
                    parseOwnedImage(scriptRuntime, capt, arg0, arg1)
                }
                else -> {

                    // @Signature detectAll(img: ImageWrapper, options?: DetectOptions): Barcode.Result[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject(scriptRuntime.topLevelScope)

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
                                    BarcodeFormats.BY_NAME[tmp.removePrefix(FORMAT_PREFIX)]
                                        ?: throw WrappedIllegalArgumentException("Unknown format ${format.jsBrief()} for barcode")
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

        private fun parseOwnedImage(
            scriptRuntime: ScriptRuntime,
            image: ImageWrapper,
            options: Any?,
            isAll: Any?,
        ): BarcodeDetectData {
            val ownedImage = image.oneShot()
            return try {
                parse(scriptRuntime, arrayOf(ownedImage, options, isAll))
            } catch (throwable: Throwable) {
                ownedImage.shoot()
                throw throwable
            }
        }

        fun recognizeTextInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): Any? {
            return withBarcodeDetectData(scriptRuntime, argList, parser) { (img, formats, enableAllPotentialBarcodes) ->
                val isAll = shouldTakeAsAll(argList)
                when {
                    isAll -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false).mapNotNull { it.getRawValue() }
                    else -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, true).firstOrNull()?.getRawValue()
                }
            }
        }

        fun recognizeTextsInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): List<String> {
            return detectAllInternal(scriptRuntime, argList, parser).mapNotNull { it.getRawValue() }
        }

        fun detectInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): Any? {
            return withBarcodeDetectData(scriptRuntime, argList, parser) { (img, formats, enableAllPotentialBarcodes) ->
                val isAll = shouldTakeAsAll(argList)
                when {
                    isAll -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false)
                    else -> scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, true).firstOrNull()
                }
            }
        }

        fun detectAllInternal(scriptRuntime: ScriptRuntime, argList: Array<Any?>, parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData): List<WrappedBarcode> {
            return withBarcodeDetectData(scriptRuntime, argList, parser) { (img, formats, enableAllPotentialBarcodes) ->
                scriptRuntime.barcode.detect(img, formats, enableAllPotentialBarcodes, false)
            }
        }

        private inline fun <T> withBarcodeDetectData(
            scriptRuntime: ScriptRuntime,
            argList: Array<Any?>,
            parser: (ScriptRuntime, Array<out Any?>) -> BarcodeDetectData,
            block: (BarcodeDetectData) -> T,
        ): T {
            val data = parser.invoke(scriptRuntime, argList)
            return try {
                block(data)
            } finally {
                data.img.shoot()
            }
        }

        internal fun shouldTakeAsAll(args: Array<out Any?>): Boolean {
            val last = args.lastOrNull() ?: return false
            return last == true || (last is NativeObject && (last.inquire("isAll") { coerceBoolean(it, false) } ?: false))
        }

    }

}
