package com.baidu.paddle.lite.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author PaddleOCR
 * Modified by TonyJiangWJ
 * @since 2023-08-06
 */
public class OCRPredictorNative {

    private static final AtomicBoolean isSOLoaded = new AtomicBoolean();
    private static final ReentrantLock lock = new ReentrantLock();

    public static void loadLibrary() throws RuntimeException {
        if (!isSOLoaded.get() && isSOLoaded.compareAndSet(false, true)) {
            try {
                // 可能和AJ中的OpenCV冲突，直接初始化一遍
                OpenCVLoader.initDebug();
                System.loadLibrary("Native");
            } catch (Throwable e) {
                throw new RuntimeException(
                        "Load libNative.so failed, please check it exists in apk file.", e);
            }
        }
    }

    private Config config;

    private long nativePointer = 0;

    public OCRPredictorNative(Config config) {
        lock.lock();
        try {
            this.config = config;
            loadLibrary();
            nativePointer = init(config.detModelFilename, config.recModelFilename, config.clsModelFilename, config.useOpencl,
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
        public int useOpencl;
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

    protected native long init(String detModelPath, String recModelPath, String clsModelPath, int useOpencl, int threadNum, String cpuMode);

    protected native float[] forward(long pointer, Bitmap originalImage, int max_size_len, int run_det, int run_cls, int run_rec);

    protected native void release(long pointer);

    private ArrayList<OcrResultModel> postprocess(float[] raw) {
        ArrayList<OcrResultModel> results = new ArrayList<OcrResultModel>();
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
        Log.i("OCRPredictorNative", "word finished " + wordNum);
        return model;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        destroy();
    }
}
