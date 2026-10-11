package org.autojs.autojs.apkbuilder

import android.os.IBinder
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.mediainfo.MediainfoPluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.autojs.plugin.mediainfo.api.IMediainfoPlugin
import org.autojs.plugin.mediainfo.api.MediainfoPluginIds
import org.autojs.plugin.mlkit.barcode.api.IBarcodePlugin
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.autojs.plugin.nodejs.api.NodeJsPluginActions
import org.autojs.plugin.opencc.api.IOpenccPlugin
import org.autojs.plugin.opencc.api.OpenccPluginIds
import org.autojs.plugin.paddle.ocr.api.IOcrPlugin
import org.autojs.plugin.pinyin.api.IPinyinPlugin
import org.autojs.plugin.pinyin.api.PinyinPluginIds
import org.autojs.plugin.pinyin4j.api.IPinyin4jPlugin
import org.autojs.plugin.pinyin4j.api.Pinyin4jPluginIds

/**
 * Lightweight host-side description of optional runtime payloads that may be copied into a
 * packaged script APK. APK mutation, resource rewriting and signing live in the APK Builder
 * plugin; this catalog only describes the inputs the host must collect from installed plugins.
 */
@Suppress("SpellCheckingInspection")
enum class ApkBuildLibrary(
    @JvmField val label: String,
    @JvmField val aliases: List<String> = emptyList(),
    @JvmField val enumerable: Boolean = true,
    internal val plugin: ApkPluginLibrary? = null,
    internal val libsToInclude: List<String> = emptyList(),
    val externalRuntimeAction: String? = null,
) {

    TERMINAL_EMULATOR(
        label = "Terminal Emulator",
        enumerable = false,
        libsToInclude = listOf(
            "libjackpal-androidterm5.so",
            "libjackpal-termexec2.so",
        ),
    ),

    // Retained only so legacy project configs can receive an explicit migration error.
    EMBEDDED_NODE_JS(
        label = "Embedded Node.js",
        aliases = listOf("embedded-node", "embedded-nodejs", "embedded_nodejs"),
        enumerable = false,
    ),

    NODEJS_RUNTIME(
        label = "Node.js Runtime",
        aliases = listOf("node", "nodejs", "node.js", "node-runtime", "nodejs-runtime"),
        externalRuntimeAction = NodeJsPluginActions.PACKAGED_RUNTIME,
    ),

    OPENCV(
        label = "OpenCV",
        aliases = listOf("cv"),
        plugin = ApkPluginLibrary(
            action = OpenCvPluginContract.ACTION_RUNTIME,
            engine = OpenCvPluginContract.ENGINE,
            onServiceConnected = { binder: IBinder ->
                IPluginInfoProvider.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = OpenCvPluginContract.VARIANT_4_8_0,
                    libsToInclude = listOf(
                        "libc++_shared.so",
                        OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
                    ),
                ),
            ),
        ),
    ),

    MLKIT_OCR(
        label = "MLKit OCR",
        aliases = listOf("mlkit", "mlkitocr", "mlkit-ocr", "mlkit_ocr"),
        plugin = ApkPluginLibrary(
            action = OcrPluginHost.ACTION_OCR,
            onServiceConnected = { binder: IBinder ->
                IOcrPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = "chinese",
                    assetsToInclude = listOf(
                        "assets/mlkit-google-ocr-models/",
                    ) to emptyList(),
                    libsToInclude = listOf(
                        "libmlkit_google_ocr_pipeline.so",
                    ),
                ),
            ),
        ),
    ),

    PADDLE_OCR(
        label = "Paddle OCR",
        aliases = listOf("paddle", "paddleocr", "paddle-ocr", "paddle_ocr"),
        plugin = ApkPluginLibrary(
            action = OcrPluginHost.ACTION_OCR,
            engine = "paddle-ocr",
            onServiceConnected = { binder: IBinder ->
                IOcrPlugin.Stub.asInterface(binder).info
            },
            // Packaged applications run the embedded Paddle Lite engine only. PP-OCRv4 / v5 / v6
            // plugins use ONNX pipelines that exist in their own processes, so they are not packaged.
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = "v3",
                    assetsToInclude = listOf(
                        "assets/labels/ppocr_keys_v1.txt",
                        "assets/models/ocr_v3_for_cpu/",
                    ) to listOf(
                        "assets/models/ocr_v3_for_cpu(slim)/",
                    ),
                    libsToInclude = listOf(
                        "libc++_shared.so",
                        "libpaddle_light_api_shared.so",
                        "libNative.so",
                    ),
                    neededOnlyLibsToInclude = listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME),
                ),
            ),
        ),
    ),

    RAPID_OCR(
        label = "Rapid OCR",
        aliases = listOf("rapid", "rapidocr", "rapid-ocr", "rapid_ocr"),
        plugin = ApkPluginLibrary(
            action = OcrPluginHost.ACTION_OCR,
            engine = "rapid-ocr",
            onServiceConnected = { binder: IBinder ->
                IOcrPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = "pp-ocrv3",
                    assetsToInclude = listOf(
                        "assets/models/ch_PP-OCRv3_det_infer.onnx",
                        "assets/models/ch_ppocr_mobile_v2.0_cls_infer.onnx",
                        "assets/models/ch_PP-OCRv3_rec_infer.onnx",
                        "assets/models/ppocr_keys_v1.txt",
                    ) to emptyList(),
                    libsToInclude = listOf(
                        "libRapidOcr.so",
                        "libonnxruntime.so",
                    ),
                    neededOnlyLibsToInclude = listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME),
                ),
                ApkPluginLibraryVariant(
                    variant = "pp-ocrv6",
                    assetsToInclude = listOf(
                        "assets/models/PP-OCRv6_det_small.onnx",
                        "assets/models/ch_ppocr_mobile_v2.0_cls_mobile.onnx",
                        "assets/models/PP-OCRv6_rec_small.onnx",
                        "assets/models/ppocrv6_dict.txt",
                    ) to emptyList(),
                    libsToInclude = listOf(
                        "libRapidOcr.so",
                        "libonnxruntime.so",
                    ),
                    neededOnlyLibsToInclude = listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME),
                ),
            ),
        ),
    ),

    OPENCC(
        label = "OpenCC",
        aliases = listOf("cc"),
        plugin = ApkPluginLibrary(
            action = OpenccPluginHost.ACTION_OPENCC,
            onServiceConnected = { binder: IBinder ->
                IOpenccPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = OpenccPluginIds.VARIANT_DEFAULT,
                    assetsToInclude = listOf("assets/openccdata/") to emptyList(),
                    libsToInclude = listOf("libChineseConverter.so"),
                ),
            ),
        ),
    ),

    PINYIN(
        label = "Pinyin",
        aliases = listOf("pin"),
        plugin = ApkPluginLibrary(
            action = PinyinPluginHost.ACTION_PINYIN,
            onServiceConnected = { binder: IBinder ->
                IPinyinPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = PinyinPluginIds.VARIANT_DEFAULT,
                    assetsToInclude = listOf(
                        "assets/dict-chinese-words.db.gzip",
                        "assets/dict-chinese-phrases.db.gzip",
                        "assets/dict-chinese-chars.db.gzip",
                        "assets/prob_emit.txt",
                    ) to emptyList(),
                ),
            ),
        ),
    ),

    PINYIN4J(
        label = "Pinyin4j",
        aliases = listOf("pinyin4j", "pinyin-4j", "pinyin_4j"),
        plugin = ApkPluginLibrary(
            action = Pinyin4jPluginHost.ACTION_PINYIN4J,
            onServiceConnected = { binder: IBinder ->
                IPinyin4jPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(variant = Pinyin4jPluginIds.VARIANT_DEFAULT),
            ),
        ),
    ),

    MLKIT_BARCODE(
        label = "MLKit Barcode",
        aliases = listOf("barcode", "mlkit-barcode", "mlkit_barcode"),
        plugin = ApkPluginLibrary(
            action = BarcodePluginHost.ACTION_BARCODE,
            onServiceConnected = { binder: IBinder ->
                IBarcodePlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = "default",
                    assetsToInclude = listOf("assets/mlkit_barcode_models") to emptyList(),
                    libsToInclude = listOf("libbarhopper_v3.so"),
                ),
            ),
        ),
    ),

    MEDIA_INFO(
        label = "MediaInfo",
        aliases = listOf("mediainfo", "media-info", "media_info"),
        plugin = ApkPluginLibrary(
            action = MediainfoPluginHost.ACTION_MEDIAINFO,
            onServiceConnected = { binder: IBinder ->
                IMediainfoPlugin.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = MediainfoPluginIds.VARIANT_DEFAULT,
                    libsToInclude = listOf(
                        "libc++_shared.so",
                        "libmediainfo.so",
                    ),
                ),
            ),
        ),
    ),

    IMAGE_QUANT(
        label = "Image Quantization",
        aliases = listOf("imagequant", "image-quant", "image-quantization", "image_quant", "image_quantization"),
        plugin = ApkPluginLibrary(
            action = ImageQuantPluginContract.ACTION_RUNTIME,
            engine = ImageQuantPluginContract.ENGINE,
            onServiceConnected = { binder: IBinder ->
                IPluginInfoProvider.Stub.asInterface(binder).info
            },
            variants = listOf(
                ApkPluginLibraryVariant(
                    variant = ImageQuantPluginContract.VARIANT_2_17_0,
                    assetsToInclude = listOf("assets/licenses/imagequant/") to emptyList(),
                    libsToInclude = listOf(ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME),
                ),
            ),
        ),
    ),
    ;

    val isPlugin: Boolean
        get() = plugin != null

    fun requirePlugin(): ApkPluginLibrary = checkNotNull(plugin) {
        "$label is not backed by a runtime plugin"
    }
}

data class ApkPluginLibrary(
    val action: String,
    val engine: String? = null,
    val onServiceConnected: (IBinder) -> PluginInfo?,
    val variants: List<ApkPluginLibraryVariant>,
)

data class ApkPluginLibraryVariant(
    val variant: String? = null,
    /** <Required List> to <Optional List>. */
    val assetsToInclude: Pair<List<String>, List<String>> = emptyList<String>() to emptyList(),
    val libsToInclude: List<String> = emptyList(),
    /** Present in newer plugin releases; older releases remain valid without these payloads. */
    val optionalLibsToInclude: List<String> = emptyList(),
    /**
     * Copied only when another library packaged from the same plugin lists it in DT_NEEDED.
     * Older OCR plugins link the shared OpenCV runtime this way; decoupled releases link a
     * private static OpenCV and need no such file (opencv-ocr-runtime-decoupling-roadmap.md, D12).
     */
    val neededOnlyLibsToInclude: List<String> = emptyList(),
)

object ApkBuildLibraryCatalog {

    @JvmStatic
    @VisibleForTesting
    fun <T> selectBestPluginVariantCandidate(
        infos: List<Pair<T, ApkPluginLibraryVariant>>,
        supportedVariants: List<ApkPluginLibraryVariant>,
    ): Pair<T, ApkPluginLibraryVariant>? {
        val supportedVariantKeys = supportedVariants
            .map { it.variant.normalizedPluginVariantKey() }
            .toSet()
        return infos
            .filter { (_, info) -> info.variant.normalizedPluginVariantKey() in supportedVariantKeys }
            .maxByOrNull { (_, info) -> info.variant.pluginVariantNumber() }
            ?: infos.firstOrNull()
    }

    private fun String?.normalizedPluginVariantKey(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
    }

    private fun String?.pluginVariantNumber(): Int {
        return normalizedPluginVariantKey()
            ?.replace(Regex("\\D"), "")
            ?.toIntOrNull()
            ?: 0
    }
}
