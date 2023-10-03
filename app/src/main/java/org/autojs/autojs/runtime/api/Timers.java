package org.autojs.autojs.runtime.api;

import android.os.Looper;

import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.core.looper.TimerThread;
import org.autojs.autojs.runtime.ScriptRuntime;

/**
 * Created by Stardust on 2017/7/21.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Modified by aiselp as of Jun 10, 2023.
 */
public class Timers {

    private static final String LOG_TAG = "Timers";

    // private VolatileBox<Long> mMaxCallbackUptimeMillisForAllThreads = new VolatileBox<>(0L);
    private Threads mThreads;
    private Timer mUiTimer;
    private ScriptRuntime mRuntime;

    public Timers(ScriptRuntime runtime) {
        mUiTimer = new Timer(runtime, Looper.getMainLooper());
        mThreads = runtime.threads;
        mRuntime = runtime;
    }

    public Timer getMainTimer() {
        return mRuntime.loopers.getTimer();
    }

    public Timer getTimerForCurrentThread() {
        return getTimerForThread(Thread.currentThread());
    }

    public Timer getTimerForThread(Thread thread) {
        if (thread == mThreads.getMainThread()) {
            return mRuntime.loopers.getTimer();
        }
        Timer timer = TimerThread.getTimerForThread(thread);
        if (timer == null && Looper.myLooper() == Looper.getMainLooper()) {
            return mUiTimer;
        }
        if (timer == null) {
            return mRuntime.loopers.getTimer();
        } else {
            return timer;
        }
    }

    public int setTimeout(Object callback, long delay, Object... args) {
        return getTimerForCurrentThread().setTimeout(callback, delay, args);
    }

    public int setTimeout(Object callback) {
        return setTimeout(callback, 1);
    }

    public boolean clearTimeout(int id) {
        return getTimerForCurrentThread().clearTimeout(id);
    }

    public int setInterval(Object listener, long interval, Object... args) {
        return getTimerForCurrentThread().setInterval(listener, interval, args);
    }

    public int setInterval(Object listener) {
        return setInterval(listener, 1);
    }

    public boolean clearInterval(int id) {
        return getTimerForCurrentThread().clearInterval(id);
    }

    public int setImmediate(Object listener, Object... args) {
        return getTimerForCurrentThread().setImmediate(listener, args);
    }

    public boolean clearImmediate(int id) {
        return getTimerForCurrentThread().clearImmediate(id);
    }

    public void recycle() {
        mRuntime.loopers.getTimer().removeAllCallbacks();
    }

}
