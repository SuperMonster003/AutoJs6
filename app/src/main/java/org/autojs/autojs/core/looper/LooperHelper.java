package org.autojs.autojs.core.looper;

import android.os.Looper;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Stardust on Dec 27, 2017.
 */
public class LooperHelper {

    private static final ConcurrentHashMap<Thread, Looper> sLoopers = new ConcurrentHashMap<>();

    public static void prepare() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return;
        }
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Looper l = Looper.myLooper();
        if (l != null) {
            sLoopers.put(Thread.currentThread(), l);
        }
    }

    public static void quitForThread(Thread thread) {
        Looper looper = sLoopers.remove(thread);
        if (looper != null && looper != Looper.getMainLooper()) {
            looper.quitSafely();
        }
    }

    public static boolean contains(Thread thread) {
        return sLoopers.containsKey(thread);
    }

}
