package org.autojs.autojs.ui.enhancedfloaty;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by Stardust on May 1, 2017.
 */
public class FloatyService extends Service {

    private static final CopyOnWriteArraySet<FloatyWindow> windows = new CopyOnWriteArraySet<>();

    public static FloatyService instance;

    private WindowManager windowManager;

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

    /**
     * Windows the service hosts or is about to host, for the memory gate (docs/memory/ROADMAP.md P4.2).
     * zh-CN: 服务正在承载或即将承载的窗口数, 供内存门禁使用 (docs/memory/ROADMAP.md P4.2).
     */
    public static int windowCount() {
        return windows.size();
    }

    /** Whether the service process has created its window manager and can host windows right now. zh-CN: 服务是否已就绪并可立即承载窗口. */
    public static boolean isRunning() {
        return instance != null;
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

    public static void stopService() {
        if (instance != null) {
            instance.stopSelf();
        }
    }

}
