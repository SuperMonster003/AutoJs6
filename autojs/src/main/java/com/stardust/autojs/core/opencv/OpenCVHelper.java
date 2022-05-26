package com.stardust.autojs.core.opencv;

import android.content.Context;

import androidx.annotation.Nullable;

import android.os.Looper;

import org.opencv.android.OpenCVLoader;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * Created by Stardust on 2018/4/2.
 */
public class OpenCVHelper {

    public interface InitializeCallback {
        void onInitFinish();
    }

    private static final String TAG = "OpenCVHelper";
    private static final Executor executor = Executors.newSingleThreadExecutor();
    private static boolean sInitialized = false;

    public static MatOfPoint newMatOfPoint(Mat mat) {
        return new MatOfPoint(mat);
    }

    public static void release(@Nullable MatOfPoint mat) {
        if (mat == null)
            return;
        mat.release();
    }

    public static void release(@Nullable Mat mat) {
        if (mat == null)
            return;
        mat.release();
    }

    public synchronized static boolean isInitialized() {
        return sInitialized;
    }

    public synchronized static void initIfNeeded(Context context, InitializeCallback callback) {
        if (!sInitialized) {
            sInitialized = true;
            if (Looper.getMainLooper() == Looper.myLooper()) {
                executor.execute(() -> init(callback));
            } else {
                init(callback);
            }
        } else {
            callback.onInitFinish();
        }
    }

    private static void init(InitializeCallback callback) {
        OpenCVLoader.initDebug();
        callback.onInitFinish();
    }
}