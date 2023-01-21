package org.autojs.autojs.app;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Created by Stardust on 2018/3/22.
 */
public class GlobalAppContext {

    @SuppressLint("StaticFieldLeak")
    private static Context sApplicationContext;
    private static Handler sHandler;

    public static void set(Application a) {
        sHandler = new Handler(Looper.getMainLooper());
        sApplicationContext = a.getApplicationContext();
    }

    public static Context get() {
        if (sApplicationContext == null) {
            throw new IllegalStateException("Should call GlobalAppContext.set() to set a application context first");
        }
        return sApplicationContext;
    }

    public static void post(Runnable r) {
        sHandler.post(r);
    }

    public static void postDelayed(Runnable r, long delay) {
        sHandler.postDelayed(r, delay);
    }

}
