package com.baidu.paddle.lite.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author PaddleOCR
 * Modified by TonyJiangWJ
 * @since 2023-08-06
 */
public class OCRPredictorNative {

    @FunctionalInterface
    public interface OpenCvRuntimeLoader {
        void ensureLoaded();
    }

    private static final NativeLibraryLoadGuard libraryLoadGuard = new NativeLibraryLoadGuard();
    private static final ReentrantLock lock = new ReentrantLock();
    private static volatile OpenCvRuntimeLoader openCvRuntimeLoader;

    /**
     * Installs the host's OpenCV runtime loader before OCR can initialize.
     * Standalone Paddle OCR plugins may omit this and keep using their bundled OpenCV runtime.
     */
    public static synchronized void installOpenCvRuntimeLoader(OpenCvRuntimeLoader loader) {
        Objects.requireNonNull(loader, "loader");
        if (openCvRuntimeLoader != null) {
            throw new IllegalStateException("The OpenCV runtime loader has already been installed");
        }
        openCvRuntimeLoader = loader;
    }

    public static void loadLibrary() throws RuntimeException {
        try {
            libraryLoadGuard.load(
                    OCRPredictorNative::ensureOpenCvRuntimeLoaded,
                    () -> System.loadLibrary("Native")
            );
        } catch (RuntimeException e) {
            // Preserve actionable host errors, such as a missing or incompatible OpenCV plugin.
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(
                    "Load OCR native runtime failed, please check OpenCV and libNative.so.", e);
        }
    }

    private static void ensureOpenCvRuntimeLoaded() {
        OpenCvRuntimeLoader loader = openCvRuntimeLoader;
        if (loader != null) {
            loader.ensureLoaded();
            return;
        }
        if (!OpenCVLoader.initDebug()) {
            throw new UnsatisfiedLinkError("OpenCVLoader.initDebug() returned false");
        }
    }

    private long nativePointer;

    public OCRPredictorNative(Config config) {
        lock.lock();
        try {
            loadLibrary();
            nativePointer = init(config.detModelFilename, config.recModelFilename, config.clsModelFilename, config.useOpenCL,
                    config.cpuThreadNum, config.cpuPower);
            Log.i("OCRPredictorNative", "load success " + nativePointer);
        } finally {
            lock.unlock();
        }
    }

    public ArrayList<OcrResultModel> runImage(Bitmap originalImage, int max_size_len, int run_det, int run_cls, int run_rec) {
        lock.lock();
        try {
            Log.i("OCRPredictorNative", "begin to run image");
            float[] rawResults = forward(nativePointer, originalImage, max_size_len, run_det, run_cls, run_rec);
            return postprocess(rawResults);
        } finally {
            lock.unlock();
        }
    }

    public static class Config {
        public int useOpenCL;
        public int cpuThreadNum;
        public String cpuPower;
        public String detModelFilename;
        public String recModelFilename;
        public String clsModelFilename;

    }

    public void destroy() {
        if (nativePointer != 0) {
            release(nativePointer);
            nativePointer = 0;
        }
    }

    protected native long init(String detModelPath, String recModelPath, String clsModelPath, int useOpenCL, int threadNum, String cpuMode);

    protected native float[] forward(long pointer, Bitmap originalImage, int max_size_len, int run_det, int run_cls, int run_rec);

    protected native void release(long pointer);

    private ArrayList<OcrResultModel> postprocess(float[] raw) {
        ArrayList<OcrResultModel> results = new ArrayList<>();
        int begin = 0;

        while (begin < raw.length) {
            int point_num = Math.round(raw[begin]);
            int word_num = Math.round(raw[begin + 1]);
            OcrResultModel model = parse(raw, begin + 2, point_num, word_num);
            begin += 2 + 1 + point_num * 2 + word_num + 2;
            results.add(model);
        }

        return results;
    }

    private OcrResultModel parse(float[] raw, int begin, int pointNum, int wordNum) {
        int current = begin;
        OcrResultModel model = new OcrResultModel();
        model.setConfidence(raw[current]);
        current++;
        for (int i = 0; i < pointNum; i++) {
            model.addPoints(Math.round(raw[current + i * 2]), Math.round(raw[current + i * 2 + 1]));
        }
        current += (pointNum * 2);
        for (int i = 0; i < wordNum; i++) {
            int index = Math.round(raw[current + i]);
            model.addWordIndex(index);
        }
        current += wordNum;
        model.setClsIdx(raw[current]);
        model.setClsConfidence(raw[current + 1]);
        // Log.i("OCRPredictorNative", "word finished " + wordNum);
        return model;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        destroy();
    }
}
