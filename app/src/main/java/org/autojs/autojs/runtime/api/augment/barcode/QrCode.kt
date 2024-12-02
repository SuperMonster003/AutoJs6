package org.autojs.autojs.runtime.api.augment.barcode

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.WrappedBarcode
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.BarcodeDetectData
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.Parseable
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.Companion.detectAllInternal
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.Companion.detectInternal
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.Companion.recognizeTextInternal
import org.autojs.autojs.runtime.api.augment.barcode.Barcode.Companion.recognizeTextsInternal
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject
import com.google.mlkit.vision.barcode.common.Barcode as GoogleBarcode
import org.autojs.autojs.runtime.api.augment.images.Images as AugmentableImages

class QrCode(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

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

    companion object : Parseable {

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

        override fun parse(scriptRuntime: ScriptRuntime, args: Array<out Any?>): BarcodeDetectData = unwrapArguments(args) { argList ->
            val (arg0, arg1, arg2) = argList

            when (arg0) {
                is String -> {

                    // @Signature
                    // detectAll(imgPath: string): QrCode.Result[];
                    // detectAll(imgPath: string, options: DetectOptions): QrCode.Result[];

                    val imgPath = arg0
                    val img = AugmentableImages.read(scriptRuntime, arrayOf(imgPath)) ?: throw WrappedIllegalArgumentException(
                        "Invalid image of path \"$imgPath\" for qrcode.detectAll(img, options?)",
                    )

                    // @Overload
                    // detectAll(img: ImageWrapper): QrCode.Result[];
                    // detectAll(img: ImageWrapper, options: DetectOptions): QrCode.Result[];
                    parse(scriptRuntime, arrayOf(img.oneShot(), arg1, arg2))
                }
                !is ImageWrapper -> {
                    // @Signature detectAll(options?: DetectOptions): QrCode.Result[];

                    // @Overload detectAll(img: ImageWrapper, options?: DetectOptions): QrCode.Result[];
                    val capt = AugmentableImages.captureScreen(scriptRuntime, emptyArray())
                    parse(scriptRuntime, arrayOf(capt, arg0, arg1))
                }
                else -> {

                    // @Signature detectAll(img: ImageWrapper, options?: DetectOptions): QrCode.Result[];

                    val img = arg0
                    val opt = arg1 as? NativeObject ?: newNativeObject()

                    val formats = listOf(GoogleBarcode.FORMAT_QR_CODE).toIntArray()
                    val enableAllPotentialBarcodes = opt.inquire("enableAllPotentialQrCodes") { coerceBoolean(it, false) }
                        ?: opt.inquire("enableAllPotentialBarcodes", ::coerceBoolean, false)

                    BarcodeDetectData(img, formats, enableAllPotentialBarcodes, opt)
                }
            }
        }

    }

}