package org.autojs.autojs.ui.enhancedfloaty;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import org.opencv.core.Size;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by Stardust on May 1, 2017.
 */
public class FloatyService extends Service {

    private static final CopyOnWriteArraySet<FloatyWindow> windows = new CopyOnWriteArraySet<>();

    public static FloatyService instance;

    private WindowManager windowManager;

    public Size initialSize;

    public FloatyService() {
        /* Empty constructor. */
    }

    public static void addWindow(FloatyWindow window) {
        if (windows.add(window) && instance != null) {
            window.onCreate(instance, instance.windowManager);
        }
    }

    public static void removeWindow(FloatyWindow window) {
        windows.remove(window);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        for (FloatyWindow delegate : windows) {
            delegate.onCreate(this, windowManager);
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

    public static void setInitialMeasure(@Nullable Size size) {
        if (instance != null) {
            instance.initialSize = size;
        }
    }

    public static void stopService() {
        if (instance != null) {
            instance.stopSelf();
        }
    }

}
