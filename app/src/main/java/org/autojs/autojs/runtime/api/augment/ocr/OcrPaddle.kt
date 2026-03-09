package org.autojs.autojs.runtime.api.augment.ocr

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.apkbuilder.ApkBuilder
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.ocr.PaddleOcrPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.OcrResult
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs6.R
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Modified by JetBrains AI Assistant (GPT-5.2-Codex (xhigh)) as of Feb 13, 2026.
 */
@Suppress("unused")
class OcrPaddle(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val key = OcrMode.PADDLE.value

    override val selfAssignmentFunctions = listOf(
        ::recognizeText.name,
        ::detect.name,
    )

    override fun invoke(vararg args: Any?): NativeArray = recognizeText(scriptRuntime, args)

    companion object : ArgumentGuards() {

        private const val DEFAULT_CPU_THREAD_NUM = 4

        private const val DEFAULT_USE_SLIM = true
        private const val DEFAULT_USE_RAW = true

        private const val DEFAULT_USE_OPENCL = false
        private const val DEFAULT_MERGE_LINE = false

        private const val EXTRA_RAW_IMAGE = "rawImage"

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
            ApkBuilder.Lib.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val parsed = getOcrOptions(options)
            val (cpuThreadNum, useSlim, useOpenCL) = parsed
            val ocrOptions = OcrOptions().apply {
                this.cpuThreadNum = cpuThreadNum
                this.useSlim = useSlim
                this.useOpenCL = useOpenCL
                this.detLongSize = parsed.detLongSize
                this.scoreThreshold = parsed.scoreThreshold
                this.extras = parsed.extras
            }
            return if (!isInrt) {
                runBlocking(scriptRuntime.coroutineContext) {
                    val target = PaddleOcrPluginHost.select(globalContext)
                        ?: throw WrappedIllegalArgumentException(globalContext.getString(R.string.error_no_paddle_ocr_plugins_available))
                    PaddleOcrPluginHost.recognizeText(globalContext, target, image.bitmap, ocrOptions)
                }
            } else {
                // Use embedded engine in packaged (INRT) app.
                // zh-CN: 打包应用 (INRT) 使用内置引擎 (本地推理), 不依赖插件.
                PaddleOcrEmbeddedEngine.recognizeText(globalContext, image.bitmap, ocrOptions)
            }
        }

        fun detectImpl(scriptRuntime: ScriptRuntime, image: ImageWrapper, options: NativeObject): List<OcrResult> {
            ApkBuilder.Lib.PADDLE_OCR.ensureLibFiles(OcrMode.PADDLE.value)
            val parsed = getOcrOptions(options)
            val (cpuThreadNum, useSlim, useOpenCL) = parsed
            val ocrOptions = OcrOptions().apply {
                this.cpuThreadNum = cpuThreadNum
                this.useSlim = useSlim
                this.useOpenCL = useOpenCL
                this.detLongSize = parsed.detLongSize
                this.scoreThreshold = parsed.scoreThreshold
                this.extras = parsed.extras
            }
            val results = if (!isInrt) {
                runBlocking(scriptRuntime.coroutineContext) {
                    val target = PaddleOcrPluginHost.select(globalContext)
                        ?: throw WrappedIllegalArgumentException(globalContext.getString(R.string.error_no_paddle_ocr_plugins_available))
                    PaddleOcrPluginHost.detect(globalContext, target, image.bitmap, ocrOptions)
                }.map { OcrResult(it.text, it.confidence, it.bounds) }
            } else {
                // Use embedded engine in packaged (INRT) app.
                // zh-CN: 打包应用 (INRT) 使用内置引擎 (本地推理), 不依赖插件.
                PaddleOcrEmbeddedEngine.detect(globalContext, image.bitmap, ocrOptions).map {
                    OcrResult(it.text, it.confidence, it.bounds)
                }
            }
            return if (parsed.mergeLine) mergeByLine(results) else results
        }

        private fun getOcrOptions(options: NativeObject): ParsedOptions {
            val cpuThreadNum = options.inquire("cpuThreadNum", ::coerceIntNumber, DEFAULT_CPU_THREAD_NUM)
            val useSlim = options.inquire("useSlim", ::coerceBoolean, DEFAULT_USE_SLIM)
            val useOpenCL = options.inquire("useOpenCL", ::coerceBoolean, DEFAULT_USE_OPENCL)
            val detLongSize = options.inquire("detLongSize", ::coerceIntNumber, 0)
            val scoreThreshold = options.inquire("scoreThreshold", ::coerceFloatNumber, -1f)
            val mergeLine = options.inquire("mergeLine", ::coerceBoolean, DEFAULT_MERGE_LINE)
            val splitWords = options.inquire("splitWords", ::coerceBoolean, false)
            val useWordSegmentation = options.inquire("useWordSegmentation", ::coerceBoolean, false)
            val useRaw = options.inquire("useRaw", ::coerceBoolean, DEFAULT_USE_RAW) ||
                options.inquire("raw", ::coerceBoolean, false)
            val imageQuality = options.inquire("imageQuality", ::coerceIntNumber, -1)
            val imageFormat = options.inquire("imageFormat", ::coerceString, "")

            val extras = Bundle()
            if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                extras.putBoolean(EXTRA_RAW_IMAGE, true)
            }
            if (imageQuality > 0) {
                extras.putInt("imageQuality", imageQuality)
            }
            if (imageFormat.isNotBlank()) {
                extras.putString("imageFormat", imageFormat)
            }

            return ParsedOptions(
                cpuThreadNum = cpuThreadNum,
                useSlim = useSlim,
                useOpenCL = useOpenCL,
                detLongSize = detLongSize,
                scoreThreshold = scoreThreshold,
                mergeLine = mergeLine && !splitWords && !useWordSegmentation,
                extras = extras.takeIf { !it.isEmpty } ?: Bundle(),
            )
        }

        private data class ParsedOptions(
            val cpuThreadNum: Int,
            val useSlim: Boolean,
            val useOpenCL: Boolean,
            val detLongSize: Int,
            val scoreThreshold: Float,
            val mergeLine: Boolean,
            val extras: Bundle,
        )

        private fun mergeByLine(results: List<OcrResult>): List<OcrResult> {
            if (results.size <= 1) return results
            val sorted = results.sorted()
            val merged = ArrayList<OcrResult>(sorted.size)

            var text = StringBuilder()
            var bounds = Rect()
            var weightSum = 0
            var confidenceSum = 0f
            var last = sorted.first()

            fun add(r: OcrResult) {
                if (text.isEmpty()) {
                    bounds = Rect(r.bounds)
                } else {
                    bounds.union(r.bounds)
                }
                text.append(r.text)
                val weight = max(1, r.text.length)
                weightSum += weight
                confidenceSum += r.confidence * weight
                last = r
            }

            fun flush() {
                if (text.isNotEmpty()) {
                    val confidence = if (weightSum > 0) confidenceSum / weightSum else 0f
                    merged.add(OcrResult(text.toString(), confidence, Rect(bounds)))
                }
                text = StringBuilder()
                weightSum = 0
                confidenceSum = 0f
            }

            add(last)
            for (i in 1 until sorted.size) {
                val r = sorted[i]
                val deviation = max(last.bounds.height(), r.bounds.height()) / 2
                val lastCenter = (last.bounds.top + last.bounds.bottom) / 2
                val rCenter = (r.bounds.top + r.bounds.bottom) / 2
                if (abs(lastCenter - rCenter) < deviation) {
                    add(r)
                } else {
                    flush()
                    add(r)
                }
            }
            flush()
            return merged
        }

    }

}
