package org.autojs.autojs.runtime.api.augment.ocr

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrRapid(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = OcrMode.RAPID.value

    override val selfAssignmentFunctions = listOf(
        ::recognizeText.name,
        ::detect.name,
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) {
            Ocr.commonRecognizeText(scriptRuntime, OcrMode.RAPID, *it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) {
            Ocr.commonDetect(scriptRuntime, OcrMode.RAPID, *it)
        }

        // @Hint by SuperMonster003 on Nov 1, 2024.
        //  ! Reserved param `options`.
        //  ! zh-CN: 预留参数 `options`.
        fun recognizeTextInternal(scriptRuntime: ScriptRuntime, image: ImageWrapper, @Suppress("UNUSED_PARAMETER") options: NativeObject): List<String> {
            ApkBuilder.Libs.RAPID_OCR.ensureLibFiles(OcrMode.RAPID.value)
            return scriptRuntime.ocrRapid.recognizeText(image)
        }

        // @Hint by SuperMonster003 on Nov 1, 2024.
        //  ! Reserved param `options`.
        //  ! zh-CN: 预留参数 `options`.
        fun detectInternal(scriptRuntime: ScriptRuntime, image: ImageWrapper, @Suppress("UNUSED_PARAMETER") options: NativeObject): List<OcrResult> {
            ApkBuilder.Libs.RAPID_OCR.ensureLibFiles(OcrMode.RAPID.value)
            return scriptRuntime.ocrRapid.detect(image)
        }

    }

}