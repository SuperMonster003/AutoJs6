package org.autojs.autojs.runtime.api.augment.ocr

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class OcrPaddle(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = OcrMode.PADDLE.value

    override val selfAssignmentFunctions = listOf(
        ::recognizeText.name,
        ::detect.name,
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object {

        private const val DEFAULT_CPU_THREAD_NUM = 4
        private const val DEFAULT_USE_SLIM = true

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) {
            Ocr.commonRecognizeText(scriptRuntime, OcrMode.PADDLE, *it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) {
            Ocr.commonDetect(scriptRuntime, OcrMode.PADDLE, *it)
        }

        fun recognizeTextInternal(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<String> {
            ApkBuilder.Libs.ensure(OcrMode.PADDLE.value, ApkBuilder.Libs.PADDLE_LITE)
            val (cpuThreadNum, useSlim) = getOptions(options)
            return scriptRuntime.ocrPaddle.recognizeText(image, cpuThreadNum, useSlim)
        }

        fun detectInternal(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<OcrResult> {
            ApkBuilder.Libs.ensure(OcrMode.PADDLE.value, ApkBuilder.Libs.PADDLE_LITE)
            val (cpuThreadNum, useSlim) = getOptions(options)
            return scriptRuntime.ocrPaddle.detect(image, cpuThreadNum, useSlim).map { result ->
                OcrResult(result.label, result.confidence, result.bounds)
            }
        }

        private fun getOptions(options: NativeObject): OcrOptions {
            val opt = options as? NativeObject ?: newNativeObject()
            val cpuThreadNum = opt.inquire("cpuThreadNum", ::coerceIntNumber, DEFAULT_CPU_THREAD_NUM)
            val useSlim = opt.inquire("useSlim", ::coerceBoolean, DEFAULT_USE_SLIM)
            return OcrOptions(cpuThreadNum, useSlim)
        }

        data class OcrOptions(val cpuThreadNum: Int, val useSlim: Boolean)

    }

}