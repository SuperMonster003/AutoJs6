package org.autojs.autojs.runtime.api.augment.ocr

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrMLKit(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = OcrMode.MLKIT.value

    override val selfAssignmentFunctions = listOf(
        ::recognizeText.name,
        ::detect.name,
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    @Suppress("unused")
    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.recognizeTextWith(scriptRuntime, OcrMode.MLKIT, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.detectWith(scriptRuntime, OcrMode.MLKIT, argList)
        }

        // @Hint by SuperMonster003 on Nov 1, 2024.
        //  ! Reserved param `options`.
        //  ! zh-CN: 预留参数 `options`.
        fun recognizeTextImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<String> {
            ApkBuilder.Lib.MLKIT_OCR.ensureLibFiles(OcrMode.MLKIT.value)
            return scriptRuntime.ocrMLKit.recognizeText(image)
        }

        // @Hint by SuperMonster003 on Nov 1, 2024.
        //  ! Reserved param `options`.
        //  ! zh-CN: 预留参数 `options`.
        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<OcrResult> {
            ApkBuilder.Lib.MLKIT_OCR.ensureLibFiles(OcrMode.MLKIT.value)
            return scriptRuntime.ocrMLKit.detect(image)
        }

    }

}