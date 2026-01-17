package org.autojs.autojs.runtime.api.augment.ocr

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.PaddleOcrPluginHost
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.OcrOptions
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

@Suppress("unused")
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
        private const val DEFAULT_USE_OPENCL = false

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun recognizeText(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.recognizeTextWith(scriptRuntime, OcrMode.PADDLE, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 3) { argList ->
            Ocr.detectWith(scriptRuntime, OcrMode.PADDLE, argList)
        }

        fun recognizeTextImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<String> {
            ApkBuilder.Libs.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val (cpuThreadNum, useSlim, useOpenCL) = getOcrOptions(options)
            val ocrOptions = OcrOptions().apply {
                this.cpuThreadNum = cpuThreadNum
                this.useSlim = useSlim
                this.useOpenCL = useOpenCL
            }
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = PaddleOcrPluginHost.select(globalContext)
                    ?: throw WrappedIllegalArgumentException(globalContext.getString(R.string.error_no_paddle_ocr_plugins_available))
                PaddleOcrPluginHost.recognizeText(globalContext, target, image.bitmap, ocrOptions)
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<OcrResult> {
            ApkBuilder.Libs.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val (cpuThreadNum, useSlim, useOpenCL) = getOcrOptions(options)
            val ocrOptions = OcrOptions().apply {
                this.cpuThreadNum = cpuThreadNum
                this.useSlim = useSlim
                this.useOpenCL = useOpenCL
            }
            return runBlocking(scriptRuntime.coroutineContext) {
                val target = PaddleOcrPluginHost.select(globalContext)
                    ?: throw WrappedIllegalArgumentException(globalContext.getString(R.string.error_no_paddle_ocr_plugins_available))
                PaddleOcrPluginHost.detect(globalContext, target, image.bitmap, ocrOptions)
            }.map { OcrResult(it.text, it.confidence, it.bounds) }
        }

        private fun getOcrOptions(options: NativeObject): OcrOptions {
            val cpuThreadNum = options.inquire("cpuThreadNum", ::coerceIntNumber, DEFAULT_CPU_THREAD_NUM)
            val useSlim = options.inquire("useSlim", ::coerceBoolean, DEFAULT_USE_SLIM)
            val useOpenCL = options.inquire("useOpenCL", ::coerceBoolean, DEFAULT_USE_OPENCL)
            return OcrOptions(cpuThreadNum, useSlim, useOpenCL)
        }

        private data class OcrOptions(val cpuThreadNum: Int, val useSlim: Boolean, val useOpenCL: Boolean)

    }

}