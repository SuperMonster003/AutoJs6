package org.autojs.autojs.ui.enhancedfloaty;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.opencv.core.Size;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by Stardust on 2017/5/1.
 */
public class FloatyService extends Service {

    private static final CopyOnWriteArraySet<FloatyWindow> windows = new CopyOnWriteArraySet<>();

    public Size initialSize;

    public static void addWindow(FloatyWindow window) {
        if (windows.add(window) && instance != null) {
            window.onCreate(instance, instance.mWindowManager);
        }
    }

    public static void removeWindow(FloatyWindow window) {
        windows.remove(window);
    }

    private static FloatyService instance;
    private WindowManager mWindowManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        for (FloatyWindow delegate : windows) {
            delegate.onCreate(this, mWindowManager);
        }
        instance = this;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        for (FloatyWindow delegate : windows) {
            delegate.onServiceDestroy(this);
        }
    }

    public static void addInitialMeasure(@NotNull Size size) {
        instance.initialSize = size;
    }

}
