package com.stardust.autojs.core.opencv;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Created by Stardust on 2018/4/2.
 */

public class OpenCVHelper {

    public interface InitializeCallback {
        void onInitFinish();
    }

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
        return org.autojs.autojs.core.opencv.OpenCVHelper.isInitialized();
    }

    public synchronized static void initIfNeeded(Context context, InitializeCallback callback) {
        org.autojs.autojs.core.opencv.OpenCVHelper.initIfNeeded(context, callback::onInitFinish);
    }
}
