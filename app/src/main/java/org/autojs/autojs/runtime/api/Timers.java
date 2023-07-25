package org.autojs.autojs.runtime.api;

import android.os.Looper;
import android.os.SystemClock;

import org.autojs.autojs.concurrent.VolatileBox;
import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.core.looper.TimerThread;
import org.autojs.autojs.runtime.ScriptRuntime;

/**
 * Created by Stardust on 2017/7/21.
 */
public class Timers {

    private final VolatileBox<Long> mMaxCallbackUptimeMillisForAllThreads = new VolatileBox<>(0L);
    private final Threads mThreads;
    private final Timer mMainTimer;
    private final Timer mUiTimer;


    public Timers(ScriptRuntime runtime) {
        mMainTimer = new Timer(runtime, mMaxCallbackUptimeMillisForAllThreads);
        mUiTimer = new Timer(runtime, mMaxCallbackUptimeMillisForAllThreads, Looper.getMainLooper());
        mThreads = runtime.threads;
    }

    public Timer getMainTimer() {
        return mMainTimer;
    }

    VolatileBox<Long> getMaxCallbackUptimeMillisForAllThreads() {
        return mMaxCallbackUptimeMillisForAllThreads;
    }

    public Timer getTimerForCurrentThread() {
        return getTimerForThread(Thread.currentThread());
    }

    public Timer getTimerForThread(Thread thread) {
        if (thread == mThreads.getMainThread()) {
            return mMainTimer;
        }
        Timer timer = TimerThread.getTimerForThread(thread);
        if (timer == null && Looper.myLooper() == Looper.getMainLooper()) {
            return mUiTimer;
        }
        return timer;
    }

    public int setTimeout(Object callback, long delay, Object... args) {
        return getTimerForCurrentThread().setTimeout(callback, delay, args);
    }

    public boolean clearTimeout(int id) {
        return getTimerForCurrentThread().clearTimeout(id);
    }

    public int setInterval(Object listener, long interval, Object... args) {
        return getTimerForCurrentThread().setInterval(listener, interval, args);
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

    public boolean hasPendingCallbacks() {
        // 如果是脚本主线程，则检查所有子线程中的定时回调。mFutureCallbackUptimeMillis用来记录所有子线程中定时最久的一个。
        if (mThreads.getMainThread() == Thread.currentThread()) {
            return mMaxCallbackUptimeMillisForAllThreads.get() > SystemClock.uptimeMillis();
        }
        // 否则检查当前线程的定时回调
        return getTimerForCurrentThread().hasPendingCallbacks();
    }

    public void recycle() {
        mMainTimer.removeAllCallbacks();
    }

}
