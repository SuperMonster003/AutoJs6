package com.stardust.app;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

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
        if (sApplicationContext == null)
            throw new IllegalStateException("Call GlobalAppContext.set() to set a application context");
        return sApplicationContext;
    }

    public static String getString(int resId) {
        return get().getString(resId);
    }

    public static int getColor(int id) {
        return get().getColor(id);
    }

    public static void toast(final String message) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastMessage(message);
        } else {
            sHandler.post(() -> toastMessage(message));
        }
    }

    public static void toast(final String message, int duration) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastMessage(message, duration);
        } else {
            sHandler.post(() -> toastMessage(message, duration));
        }
    }

    public static void toast(final int resId) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastResId(resId);
        } else {
            sHandler.post(() -> toastResId(resId));
        }
    }

    public static void toast(final int resId, int duration) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            toastResId(resId, duration);
        } else {
            sHandler.post(() -> toastResId(resId, duration));
        }
    }

    private static void toastMessage(String message) {
        Toast.makeText(get(), message, Toast.LENGTH_SHORT).show();
    }

    private static void toastMessage(String message, int duration) {
        Toast.makeText(get(), message, duration).show();
    }

    private static void toastResId(int resId) {
        Toast.makeText(get(), resId, Toast.LENGTH_SHORT).show();
    }

    private static void toastResId(int resId, int duration) {
        Toast.makeText(get(), resId, duration).show();
    }

    public static void post(Runnable r) {
        sHandler.post(r);
    }

    public static void postDelayed(Runnable r, long m) {
        sHandler.postDelayed(r, m);
    }
}
