package org.autojs.autojs.app;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Created by Stardust on Mar 22, 2018.
 */
public class GlobalAppContext {

    @SuppressLint("StaticFieldLeak")
    private static volatile Context sApplicationContext;
    private static volatile Handler sHandler;

    public static void set(Application app) {
        sHandler = new Handler(Looper.getMainLooper());
        // LoadedApk may not yet expose getApplicationContext() during attachBaseContext.
        sApplicationContext = app;
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
