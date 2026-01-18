package com.baidu.paddle.lite.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import java.io.FileNotFoundException

/**
 * A unified embedded Paddle OCR engine API for both host (INRT) and plugin APK.
 * zh-CN: 面向宿主 (INRT) 与插件 APK 的统一 Paddle OCR 内置引擎 API.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Jan 17, 2026.
 * Modified by SuperMonster003 as of Jan 18, 2026.
 */
class PaddleOcrEngine(
    private val appContext: Context,
    private val variant: VariantSpec,
    private val bridge: NativeBridge = PredictorNativeBridge(),
) {

    @Volatile
    private var initialized: Boolean = false

    private val lock = Any()

    /**
     * Initialize native libs and load models.
     * zh-CN: 初始化 native 库并加载模型.
     */
    fun ensureInitialized(options: OcrOptions) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return

            // Resolve model profile by options + variant.
            // zh-CN: 根据 options + variant 决定模型组合.
            val profile = variant.resolveProfile(options)

            // Ensure required assets exist.
            // zh-CN: 确保所需 assets 存在.
            variant.assertAssetsExist(appContext, profile)

            // Prepare checking bitmap from drawable resource.
            // zh-CN: 从 drawable 资源准备检查用 bitmap.
            val checkingBitmap = decodeDrawable(appContext, variant.checkingDrawableRes)

            // Delegate to bridge for real initialization.
            // zh-CN: 委托给 bridge 执行真实初始化.
            bridge.init(
                context = appContext,
                profile = profile,
                checkingBitmap = checkingBitmap,
            )

            initialized = true
        }
    }

    /**
     * Recognize text only.
     * zh-CN: 仅识别文本.
     */
    fun recognizeText(bitmap: Bitmap, options: OcrOptions): List<String> {
        ensureInitialized(options)
        return bridge.recognizeText(bitmap, options)
    }

    /**
     * Detect with boxes.
     * zh-CN: 检测并返回文本框信息.
     */
    fun detect(bitmap: Bitmap, options: OcrOptions): List<OcrResult> {
        ensureInitialized(options)
        return bridge.detect(bitmap, options)
    }

    private fun decodeDrawable(context: Context, resId: Int): Bitmap {
        return BitmapFactory.decodeResource(context.resources, resId)
            ?: throw IllegalStateException(
                context.getString(R.string.error_failed_to_decode_checking_drawable_resource)
            )
    }
}

/**
 * A single point for real predictor/native invocation.
 * zh-CN: 真实 predictor/native 调用的单点封装.
 */
interface NativeBridge {

    /**
     * Initialize predictor with model paths.
     * zh-CN: 用模型路径初始化 predictor.
     */
    fun init(
        context: Context,
        profile: ModelProfile,
        checkingBitmap: Bitmap,
    )

    /**
     * Recognize text.
     * zh-CN: 识别文本.
     */
    fun recognizeText(bitmap: Bitmap, options: OcrOptions): List<String>

    /**
     * Detect results with boxes.
     * zh-CN: 检测并返回带框结果.
     */
    fun detect(bitmap: Bitmap, options: OcrOptions): List<OcrResult>
}

/**
 * Resolved model paths for one run configuration.
 * zh-CN: 单次运行配置解析出的模型路径集合.
 */
data class ModelProfile(
    val variantName: String,
    val labelAssetPath: String,

    // v5 uses Predictor's internal default dirs; v3 uses explicit dir.
    // zh-CN: v5 由 Predictor 内部默认目录决定, v3 使用显式目录.
    val modelDir: String?,

    val useSlim: Boolean,
    val useOpenCL: Boolean,
    val cpuThreadNum: Int,
    val detModelFile: String,
    val recModelFile: String,
    val clsModelFile: String,
    val assetsToCheck: List<String>,
)

/**
 * Engine variant specification (v3/v5).
 * zh-CN: 引擎变体规范 (v3/v5).
 */
data class VariantSpec(
    val name: String,
    val supportsOpenCL: Boolean,
    val labelAssetPath: String,

    // Explicit model directories.
    // zh-CN: 显式模型目录配置.

    val modelDirCpu: String,
    val modelDirCpuSlim: String? = null,
    val modelDirOpenCL: String? = null,
    val modelDirOpenCLSlim: String? = null,

    val detModelFile: String,
    val recModelFile: String,
    val clsModelFile: String,

    val checkingDrawableRes: Int,
) {

    fun resolveProfile(options: OcrOptions): ModelProfile {
        val useSlim = options.useSlim
        val useOpenCLRequested = options.useOpenCL && supportsOpenCL

        val resolvedDir: String? = if (name == NAME_V5) {
            // v5: let Predictor decide between CPU/OpenCL + fallback internally.
            // zh-CN: v5: 让 Predictor 内部决定 CPU/OpenCL 并自动回退.
            null
        } else {
            // v3: choose deterministic dir here (OpenCL ignored).
            // zh-CN: v3: 在此确定性选择目录 (OpenCL 被忽略).
            if (useSlim) (modelDirCpuSlim ?: modelDirCpu) else modelDirCpu
        }

        // Assets to check:
        // - Always check label file.
        // - For v3: check the resolved model dir files.
        // - For v5: check CPU + slim dirs always, and additionally OpenCL dirs if requested.
        // zh-CN:
        // - 总是检查 label 文件.
        // - v3: 检查解析出的模型目录及其文件.
        // - v5: 总是检查 CPU/INT8 目录, 若用户请求 OpenCL 再额外检查 OpenCL 目录.
        val assets = buildList {
            add(labelAssetPath)

            val cpuDir = modelDirCpu
            add("$cpuDir/$detModelFile")
            add("$cpuDir/$recModelFile")
            add("$cpuDir/$clsModelFile")

            modelDirCpuSlim?.let { slimDir ->
                add("$slimDir/$detModelFile")
                add("$slimDir/$recModelFile")
                add("$slimDir/$clsModelFile")
            }

            if (useOpenCLRequested) {
                modelDirOpenCL?.let { clDir ->
                    add("$clDir/$detModelFile")
                    add("$clDir/$recModelFile")
                    add("$clDir/$clsModelFile")
                }
                modelDirOpenCLSlim?.let { clSlimDir ->
                    add("$clSlimDir/$detModelFile")
                    add("$clSlimDir/$recModelFile")
                    add("$clSlimDir/$clsModelFile")
                }
            }

            // v3 deterministic dir check (override list to minimal set).
            // zh-CN: v3 确定性目录检查 (覆盖为最小集合).
            if (name != NAME_V5) {
                clear()
                add(labelAssetPath)
                val dir = requireNotNull(resolvedDir)
                add("$dir/$detModelFile")
                add("$dir/$recModelFile")
                add("$dir/$clsModelFile")
            }
        }

        return ModelProfile(
            variantName = name,
            labelAssetPath = labelAssetPath,
            modelDir = resolvedDir,
            useSlim = useSlim,
            useOpenCL = useOpenCLRequested,
            cpuThreadNum = options.cpuThreadNum,
            detModelFile = detModelFile,
            recModelFile = recModelFile,
            clsModelFile = clsModelFile,
            assetsToCheck = assets.distinct(),
        )
    }

    fun assertAssetsExist(context: Context, profile: ModelProfile) {
        fun assertOne(path: String) {
            try {
                context.assets.open(path).use { }
            } catch (e: FileNotFoundException) {
                throw IllegalStateException(context.getString(R.string.error_missing_required_paddle_ocr_asset, path), e)
            }
        }
        profile.assetsToCheck.forEach(::assertOne)
    }

    companion object {
        const val NAME_V5 = "v5"
        const val NAME_V3 = "v3"

        fun v5(): VariantSpec = VariantSpec(
            name = NAME_V5,
            supportsOpenCL = true,
            labelAssetPath = "labels/ppocr_keys_ocrv5.txt",

            modelDirCpu = "models/pp-ocrv5-arm",
            modelDirCpuSlim = "models/pp-ocrv5-arm-int8",
            modelDirOpenCL = "models/pp-ocrv5-arm-opencl",
            modelDirOpenCLSlim = "models/pp-ocrv5-arm-opencl-int8",

            detModelFile = "PP-OCRv5_mobile_det.nb",
            recModelFile = "PP-OCRv5_mobile_rec.nb",
            clsModelFile = "PP-LCNet_x1_0_textline_ori.nb",

            checkingDrawableRes = R.drawable.paddle_ocr_test,
        )

        fun v3(): VariantSpec = VariantSpec(
            name = NAME_V3,
            supportsOpenCL = false,
            labelAssetPath = "labels/ppocr_keys_v1.txt",

            modelDirCpu = "models/ocr_v3_for_cpu",
            modelDirCpuSlim = "models/ocr_v3_for_cpu(slim)",

            modelDirOpenCL = null,
            modelDirOpenCLSlim = null,

            detModelFile = "det_opt.nb",
            recModelFile = "rec_opt.nb",
            clsModelFile = "cls_opt.nb",

            checkingDrawableRes = R.drawable.paddle_ocr_test,
        )
    }
}