package com.baidu.paddle.lite.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import androidx.preference.PreferenceManager;
import org.opencv.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Predictor for Paddle-Lite OCR engine, managing model loading, runtime configuration,
 * and end-to-end inference flow.
 * zh-CN: 面向 Paddle-Lite OCR 引擎的预测器, 负责模型加载/运行时配置以及端到端推理流程.
 *
 * @author <a href="https://github.com/TonyJiangWJ">TonyJiangWJ</a>
 * @see <a href="https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/android_demo/app/src/main/java/com/baidu/paddle/lite/demo/ocr/Predictor.java">
 * PaddlePaddle/PaddleOCR (Predictor.java)</a>
 * @since Aug 6, 2023
 *
 * <p> Modified by TonyJiangWJ as of Aug 7, 2023. </p>
 * <p> Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 17, 2026. </p>
 * <p> Modified by SuperMonster003 as of Jan 18, 2026. </p>
 */
@SuppressWarnings("unused")
public class Predictor {

    public static final int DEFAULT_CPU_THREAD_NUM = 4;
    public static final boolean DEFAULT_USE_SLIM = true;
    public static final boolean DEFAULT_USE_OPENCL = false;

    private static final String TAG = Predictor.class.getSimpleName();

    /**
     * Probe bitmap cache for init-check.
     * zh-CN: 初始化校验使用的探测位图缓存.
     */
    private static Bitmap checkingBitmap;
    /**
     * Default label file path.
     * zh-CN: 默认字典文件路径.
     */
    private final String defaultLabelPath = "labels/ppocr_keys_ocrv5.txt";
    /**
     * Default CPU model directory (standard).
     * zh-CN: 默认 CPU 标准模型目录.
     */
    private final String defaultModelPath = "models/pp-ocrv5-arm";
    /**
     * Default OpenCL model directory (standard).
     * zh-CN: 默认 OpenCL 标准模型目录.
     */
    private final String defaultModelPathOpenCL = "models/pp-ocrv5-arm-opencl";
    /**
     * Default CPU model directory (INT8 slim).
     * zh-CN: 默认 CPU INT8 slim 模型目录.
     */
    private final String defaultModelPathSlim = "models/pp-ocrv5-arm-int8";
    /**
     * Default OpenCL model directory (INT8 slim).
     * zh-CN: 默认 OpenCL INT8 slim 模型目录.
     */
    private final String defaultModelPathOpenCLSlim = "models/pp-ocrv5-arm-opencl-int8";

    /** Detection model. [zh-CN: 检测模型]. */
    public String detModelFilename = "PP-OCRv5_mobile_det.nb";
    /** Recognition model. [zh-CN: 识别模型]. */
    public String recModelFilename = "PP-OCRv5_mobile_rec.nb";
    /** Text direction (cls) model. [zh-CN: 方向分类 (cls) 模型]. */
    public String clsModelFilename = "PP-LCNet_x1_0_textline_ori.nb";

    /** Whether the model is loaded. [zh-CN: 模型是否已加载]. */
    public boolean isLoaded = false;
    /** Warm-up iteration count. [zh-CN: 预热迭代次数]. */
    public int warmupIterNum = 1;
    /** Inference iteration count for timing. [zh-CN: 用于计时的推理迭代次数]. */
    public int inferIterNum = 1;
    /** CPU thread count. [zh-CN: CPU 线程数]. */
    public int cpuThreadNum = DEFAULT_CPU_THREAD_NUM;
    /** CPU power mode string (Lite power hint). [zh-CN: CPU 能耗模式字符串 (Lite 电源提示)]. */
    public String cpuPowerMode = "LITE_POWER_HIGH";

    /** Selected model resolved absolute path. [zh-CN: 选定模型解析后的绝对路径]. */
    public String modelPath = "";
    /** Selected model directory name. [zh-CN: 选定模型目录名]. */
    public String modelName = "";

    /** Use slim (INT8) model. [zh-CN: 是否使用 slim (INT8) 模型]. */
    public boolean useSlim = DEFAULT_USE_SLIM;
    /** Use OpenCL backend (if available). [zh-CN: 是否启用 OpenCL 后端 (若可用)]. */
    public boolean useOpenCL = DEFAULT_USE_OPENCL;
    /** Validate initialization with a preset image. [zh-CN: 是否通过预设图片校验初始化]. */
    public boolean checkModelLoaded = BuildConfig.DEBUG;

    /** Enable classification (cls). [zh-CN: 启用方向分类 (cls)]. */
    public boolean isClassificationEnabled = false;
    /** Enable detection (det). [zh-CN: 启用文本检测 (det)]. */
    public boolean isDetectionEnabled = false;
    /** Enable recognition (rec). [zh-CN: 启用文本识别 (rec)]. */
    public boolean isRecognitionEnabled = true;

    /** Score threshold for filtering results. [zh-CN: 结果过滤的置信度阈值]. */
    public float scoreThreshold = 0.1f;
    /** Max long side for det input resize. [zh-CN: 检测输入缩放的最长边]. */
    protected int detLongSize = 960;

    /** Native predictor bridge. [zh-CN: Native 预测器桥接对象]. */
    protected OCRPredictorNative paddlePredictor = null;
    /** Inference time in milliseconds. [zh-CN: 推理耗时 (毫秒)]. */
    protected float inferenceTime = 0;
    /** Labels for recognition post-processing. [zh-CN: 识别后处理所需的字典标签]. */
    protected List<String> wordLabels = new ArrayList<>();
    /** Input image buffer (ARGB_8888). [zh-CN: 输入图像缓冲 (ARGB_8888)]. */
    protected Bitmap inputImage = null;
    /** Preprocess time in milliseconds. [zh-CN: 预处理耗时 (毫秒)]. */
    protected float preprocessTime = 0;

    /** Validation attempt counter. [zh-CN: 初始化校验重试计数器]. */
    private int validationAttempt = 1;
    /** Initialization attempt counter. [zh-CN: 初始化尝试计数器]. */
    private int initializationAttempt = 1;

    // @Archived by SuperMonster003 on Nov 7, 2025.
    //  ! Legacy default paths and filenames for PP-OCRv3 are archived here.
    //  ! zh-CN: 旧版 PP-OCRv3 的默认路径与文件名在此归档保留.
    //  # private final String defaultLabelPath = "labels/ppocr_keys_v1.txt";
    //  # private final String defaultModelPath = "models/ocr_v3_for_cpu";
    //  # public String detModelFilename = "det_opt.nb";
    //  # public String recModelFilename = "rec_opt.nb";
    //  # public String clsModelFilename = "cls_opt.nb";
    //  # // Slim model converted by opt 2.10; 2.11 had issues.
    //  # // zh-CN: Slim 模型由 2.10 版 opt 转换; 2.11 存在兼容问题.
    //  # private final String defaultModelPathSlim = "models/ocr_v3_for_cpu(slim)";

    public Predictor() {
        /* Empty body. */
    }

    public static String md5(String text) {
        MessageDigest md;
        byte[] bytesOfMessage = text.getBytes();
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return Base64.encodeToString(md.digest(bytesOfMessage), Base64.DEFAULT);
    }

    public boolean init(Context appCtx) {
        return this.init(appCtx, defaultModelPath, defaultLabelPath);
    }

    public boolean init(Context appCtx, boolean useSlim) {
        return this.init(appCtx, useSlim, DEFAULT_USE_OPENCL);
    }

    public boolean init(Context appCtx, boolean useSlim, boolean useOpenCL) {
        Log.d(TAG, "use slim: " + useSlim);
        Log.d(TAG, "use opencl: " + useOpenCL);

        // If already loaded and switches are consistent, reuse directly.
        // zh-CN: 若已加载且开关一致, 直接复用.
        if (this.isLoaded && this.useSlim == useSlim && this.useOpenCL == useOpenCL) {
            return true;
        }

        boolean openclAvailable = false;
        if (useOpenCL) {
            try {
                openclAvailable = OpenCLGuard.isOpenCLRuntimeAvailable(appCtx);
                if (!openclAvailable) {
                    Log.w(TAG, "[OpenCL] Unavailable or fused, fallback to CPU.");
                }
            } catch (Throwable t) {
                Log.w(TAG, "[OpenCL] Probe exception, fallback to CPU: " + t.getMessage());
            }
        }

        this.useSlim = useSlim;
        this.useOpenCL = openclAvailable;

        String modelDir;
        if (this.useSlim) {
            modelDir = this.useOpenCL ? defaultModelPathOpenCLSlim : defaultModelPathSlim;
        } else {
            modelDir = this.useOpenCL ? defaultModelPathOpenCL : defaultModelPath;
        }

        boolean ok;
        if (this.useOpenCL) {
            OpenCLGuard.markInitStart(appCtx);
            try {
                ok = this.init(appCtx, modelDir, defaultLabelPath);
            } finally {
                OpenCLGuard.markInitEnd(appCtx);
            }
            if (!ok) {
                Log.w(TAG, "[OpenCL] Init failed without crash, fallback to CPU.");
                this.useOpenCL = false;
                String cpuModelDir = this.useSlim ? defaultModelPathSlim : defaultModelPath;
                ok = this.init(appCtx, cpuModelDir, defaultLabelPath);
            }
            return ok;
        } else {
            return this.init(appCtx, modelDir, defaultLabelPath);
        }
    }

    public boolean init(Context appCtx, String modelPath, String labelPath) {
        Log.d(TAG, "init whit model: " + modelPath + " label: " + labelPath);
        isLoaded = loadModel(appCtx, modelPath, cpuThreadNum, cpuPowerMode);
        if (!isLoaded) {
            return false;
        }
        isLoaded = loadLabel(appCtx, labelPath);
        if (!checkModelLoadedSuccess(appCtx)) {
            if (initializationAttempt++ < 3) {
                return init(appCtx, modelPath, labelPath);
            } else {
                return false;
            }
        }
        return isLoaded;
    }

    /**
     * Initialize and validate models by running inference on a preset test image.
     * Retry up to several times as a workaround (deeper cause needs investigation).
     * zh-CN:
     * 初始化模型后通过识别预设图片校验是否初始化成功.
     * 曲线救国, 深层的失败原因需要后续排查.
     */
    private boolean checkModelLoadedSuccess(Context context) {
        if (!checkModelLoaded) {
            return true;
        }
        if (!isLoaded) {
            return false;
        }
        List<OcrResult> results = runOcr(getCheckingBitmap(context));
        StringBuilder sb = new StringBuilder();
        for (OcrResult result : results) {
            sb.append(result.getLabel());
        }
        // The image contains a single recognizable text string "测试" (Chinese word "test").
        // zh-CN: 图片中包含唯一可识别文本 "测试".
        boolean check = sb.toString().contains("测试");
        Log.d(TAG, "Validation attempt " + validationAttempt + ": { initialized: " + check + ", result: " + sb + " }");
        boolean result = check || validationAttempt++ >= 5;
        if (!check && validationAttempt >= 5) {
            Log.e(TAG, "Model initialization failed");
        }
        return result;
    }

    private Bitmap getCheckingBitmap(Context context) {
        if (checkingBitmap == null) {
            checkingBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.paddle_ocr_test);
        }
        return checkingBitmap;
    }

    public boolean init(Context appCtx, String modelPath, String labelPath, int cpuThreadNum, String cpuPowerMode) {
        isLoaded = loadModel(appCtx, modelPath, cpuThreadNum, cpuPowerMode);
        if (!isLoaded) {
            return false;
        }
        isLoaded = loadLabel(appCtx, labelPath);
        return isLoaded;
    }

    public boolean init(Context appCtx, String modelPath, String labelPath, int cpuThreadNum, String cpuPowerMode, int detLongSize, float scoreThreshold) {
        boolean isLoaded = init(appCtx, modelPath, labelPath, cpuThreadNum, cpuPowerMode);
        if (!isLoaded) {
            return false;
        }
        this.detLongSize = detLongSize;
        this.scoreThreshold = scoreThreshold;
        return true;
    }

    protected boolean loadModel(Context appCtx, String modelPath, int cpuThreadNum, String cpuPowerMode) {
        // Release model if exists.
        // zh-CN: 释放模型如果存在.
        releaseModel();

        // Load model.
        // zh-CN: 加载模型.
        if (modelPath.isEmpty()) {
            return false;
        }
        String realPath = modelPath;
        if (modelPath.charAt(0) != '/') {
            // Read model files from custom path if the first character of mode path is '/'
            // otherwise copy model to cache from assets.
            // zh-CN: 如果模型路径首字符为 '/' 则从自定义路径读取模型文件, 否则从 assets 复制模型到缓存.
            realPath = appCtx.getCacheDir() + File.separator + modelPath;

            // @SectionBegin("copyModelAssets") by TonyJiangWJ on Aug 7, 2023.
            String key = "PADDLE_MODEL_LOADED" + md5(modelPath);
            // Model has been updated, force override the old model.
            // zh-CN: 进行了模型更新, 需要强制覆盖旧模型.
            boolean loaded = PreferenceManager.getDefaultSharedPreferences(appCtx).getBoolean(key, false);
            if (loaded) {
                // No need to copy every time.
                // zh-CN: 没有必要每次都复制.
                Utils.copyDirectoryFromAssetsIfNeeded(appCtx, modelPath, realPath);
            } else {
                Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath);
                PreferenceManager.getDefaultSharedPreferences(appCtx).edit().putBoolean(key, true).apply();
            }
            // @SectionEnd("copyModelAssets")
        }

        OCRPredictorNative.Config config = new OCRPredictorNative.Config();

        // Whether to use GPU (OpenCL), only set to 1 when useOpenCL is confirmed available at Java level.
        // zh-CN: 是否使用 GPU (OpenCL), 只有在 Java 层确认 useOpenCL 可用时才真正置 1.
        config.useOpenCL = useOpenCL ? 1 : 0;
        config.cpuThreadNum = cpuThreadNum;
        config.detModelFilename = realPath + File.separator + detModelFilename;
        config.recModelFilename = realPath + File.separator + recModelFilename;
        config.clsModelFilename = realPath + File.separator + clsModelFilename;
        Log.i("Predictor", "model path" + config.detModelFilename + " ; " + config.recModelFilename + ";" + config.clsModelFilename);
        config.cpuPower = cpuPowerMode;

        paddlePredictor = new OCRPredictorNative(config);

        this.cpuThreadNum = cpuThreadNum;
        this.cpuPowerMode = cpuPowerMode;
        this.modelPath = realPath;
        this.modelName = realPath.substring(realPath.lastIndexOf(File.separator) + 1);

        return true;
    }

    public void releaseModel() {
        if (paddlePredictor != null) {
            paddlePredictor.destroy();
            paddlePredictor = null;
        }
        isLoaded = false;
        modelPath = "";
        modelName = "";
    }

    protected boolean loadLabel(Context appCtx, String labelPath) {
        wordLabels.clear();
        wordLabels.add("black");
        // Load word labels from file.
        // zh-CN: 从文件中加载字典标签.
        try {
            InputStream labelInputStream;
            if (labelPath.startsWith(File.separator)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    labelInputStream = Files.newInputStream(Paths.get(labelPath));
                } else {
                    labelInputStream = new FileInputStream(labelPath);
                }
            } else {
                labelInputStream = appCtx.getAssets().open(labelPath);
            }
            int available = labelInputStream.available();
            byte[] lines = new byte[available];
            if (labelInputStream.read(lines) <= 0) {
                Log.e(TAG, "Failed to read label");
                return false;
            }
            labelInputStream.close();
            String words = new String(lines);
            // Compatible with \r\n line endings on Windows.
            // zh-CN: 兼容 Windows 系统下的 \r\n 换行符.
            String[] contents = words.split("(\r)?\n");
            wordLabels.addAll(Arrays.asList(contents));
            wordLabels.add(" ");
            Log.i(TAG, "Word label size: " + wordLabels.size());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
        return true;
    }

    public List<OcrResult> runOcr(Bitmap inputImage) {
        if (inputImage == null || !isLoaded()) {
            return Collections.emptyList();
        }
        int run_det = isDetectionEnabled ? 1 : 0;
        int run_cls = isClassificationEnabled ? 1 : 0;
        int run_rec = isRecognitionEnabled ? 1 : 0;
        // Warm up.
        // zh-CN: 预热.
        for (int i = 0; i < warmupIterNum; i++) {
            paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
        }
        // Do not need warm.
        // zh-CN: 不需要预热.
        warmupIterNum = 0;
        // Run inference. 
        // zh-CN: 执行推理.
        Date start = new Date();
        ArrayList<OcrResultModel> results = paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
        Date end = new Date();
        inferenceTime = (end.getTime() - start.getTime()) / (float) inferIterNum;

        postProcess(results);
        Log.i(TAG, "[stat] Preprocess Time: " + preprocessTime + "; Inference Time: " + inferenceTime + "; Box Size: " + results.size());
        List<OcrResult> ocrResults = new ArrayList<>();
        for (OcrResultModel resultModel : results) {
            // Log.d(TAG, "Recognize: " + resultModel);
            if (resultModel.getConfidence() >= scoreThreshold) {
                ocrResults.add(new OcrResult(resultModel));
            } else {
                // Log.d(TAG, "Discard: " + resultModel);
            }
        }
        Collections.sort(ocrResults);
        return ocrResults;
    }

    /**
     * Whether model is loaded and predictor is valid.
     * zh-CN: 模型是否已加载且预测器有效.
     */
    public boolean isLoaded() {
        return paddlePredictor != null && isLoaded;
    }

    public String modelPath() {
        return modelPath;
    }

    public String modelName() {
        return modelName;
    }

    public int cpuThreadNum() {
        return cpuThreadNum;
    }

    public String cpuPowerMode() {
        return cpuPowerMode;
    }

    public float inferenceTime() {
        return inferenceTime;
    }

    public Bitmap inputImage() {
        return inputImage;
    }

    public float preprocessTime() {
        return preprocessTime;
    }

    public String getDefaultLabelPath() {
        return defaultLabelPath;
    }

    public String getDefaultModelPath() {
        return defaultModelPath;
    }

    /**
     * Get default OpenCL model directory (standard).
     * zh-CN: 获取默认的 OpenCL 标准模型目录.
     */
    public String getDefaultModelPathOpenCL() {
        return defaultModelPathOpenCL;
    }

    /**
     * Get default CPU model directory (INT8 slim).
     * zh-CN: 获取默认的 CPU INT8 slim 模型目录.
     */
    public String getDefaultModelPathSlim() {
        return defaultModelPathSlim;
    }

    /**
     * Get default OpenCL model directory (INT8 slim).
     * zh-CN: 获取默认的 OpenCL INT8 slim 模型目录.
     */
    public String getDefaultModelPathOpenCLSlim() {
        return defaultModelPathOpenCLSlim;
    }

    public boolean isUseSlim() {
        return useSlim;
    }

    public boolean isUseOpenCL() {
        return useOpenCL;
    }

    /**
     * Set input image buffer (copy to ARGB_8888).
     * zh-CN: 设置输入图像缓冲 (复制为 ARGB_8888).
     */
    public void setInputImage(Bitmap image) {
        if (image != null) {
            this.inputImage = image.copy(Bitmap.Config.ARGB_8888, true);
        }
    }

    /**
     * Convert raw recognition outputs to text labels and metadata.
     * zh-CN: 将识别原始输出转换为文本标签及元数据.
     */
    private void postProcess(ArrayList<OcrResultModel> results) {
        for (OcrResultModel r : results) {
            StringBuilder word = new StringBuilder();
            for (int index : r.getWordIndex()) {
                if (index >= 0 && index < wordLabels.size()) {
                    word.append(wordLabels.get(index));
                } else {
                    Log.e(TAG, "Word index is not in label list:" + index);
                    word.append(" ");
                }
            }
            r.setLabel(word.toString());
            r.setClsLabel(r.getClsIdx() == 1 ? "180" : "0");
        }
    }

    /**
     * Enable/disable classification (cls).
     * zh-CN: 启用/禁用方向分类 (cls).
     */
    public void setClassificationEnabled(boolean enable) {
        this.isClassificationEnabled = enable;
    }

    /**
     * Enable/disable detection (det).
     * zh-CN: 启用/禁用文本检测 (det).
     */
    public void setDetectionEnabled(boolean enable) {
        this.isDetectionEnabled = enable;
    }

    /**
     * Enable/disable recognition (rec).
     * zh-CN: 启用/禁用文本识别 (rec).
     */
    public void setRecognitionEnabled(boolean enable) {
        this.isRecognitionEnabled = enable;
    }

    /**
     * Set max long side for detection input (smaller is usually faster, e.g., 736-960).
     * zh-CN: 设置检测输入的最长边 (越小通常越快, 例如 736-960).
     */
    public void setDetLongSize(int detLongSize) {
        this.detLongSize = detLongSize;
    }

    /**
     * Set score threshold (slightly improves speed by pruning noisy boxes).
     * zh-CN: 设置置信度阈值 (通过剔除噪声框可略微提速).
     */
    public void setScoreThreshold(float scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

}
