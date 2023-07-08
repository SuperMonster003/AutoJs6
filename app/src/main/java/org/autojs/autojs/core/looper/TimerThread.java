package org.autojs.autojs.core.looper;

import static org.autojs.autojs.util.StringUtils.str;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.autojs.autojs.concurrent.VolatileBox;
import org.autojs.autojs.engine.RhinoJavaScriptEngine;
import org.autojs.autojs.lang.ThreadCompat;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs6.R;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Stardust on 2017/12/27.
 */
public class TimerThread extends ThreadCompat {

    private static final ConcurrentHashMap<Thread, Timer> sTimerMap = new ConcurrentHashMap<>();

    private Timer mTimer;
    private final VolatileBox<Long> mMaxCallbackUptimeMillisForAllThreads;
    private final ScriptRuntime mRuntime;
    private final Runnable mTarget;
    private boolean mRunning = false;
    private final Object mRunningLock = new Object();

    public TimerThread(ScriptRuntime runtime, VolatileBox<Long> maxCallbackUptimeMillisForAllThreads, Runnable target) {
        super(target);
        mRuntime = runtime;
        mTarget = target;
        mMaxCallbackUptimeMillisForAllThreads = maxCallbackUptimeMillisForAllThreads;
    }

    @Override
    public void run() {
        mRuntime.loopers.prepare();
        mTimer = new Timer(mRuntime, mMaxCallbackUptimeMillisForAllThreads);
        sTimerMap.put(Thread.currentThread(), mTimer);
        ((RhinoJavaScriptEngine) mRuntime.engines.myEngine()).enterContext();
        notifyRunning();
        new Handler().post(mTarget);
        try {
            Looper.loop();
        } catch (Throwable e) {
            if (!ScriptInterruptedException.causedByInterrupted(e)) {
                mRuntime.console.error(Thread.currentThread() + ": ", e);
            }
        } finally {
            onExit();
            mTimer = null;
            org.mozilla.javascript.Context.exit();
            sTimerMap.remove(Thread.currentThread(), mTimer);
        }
    }

    @Override
    public void interrupt() {
        LooperHelper.quitForThread(this);
        super.interrupt();
    }

    private void notifyRunning() {
        synchronized (mRunningLock) {
            mRunning = true;
            mRunningLock.notifyAll();
        }
    }

    @CallSuper
    protected void onExit() {
        mRuntime.loopers.notifyThreadExit(this);
    }

    public static Timer getTimerForThread(Thread thread) {
        return sTimerMap.get(thread);
    }

    public static Timer getTimerForCurrentThread() {
        return getTimerForThread(Thread.currentThread());
    }

    public int setTimeout(Object callback, long delay, Object... args) {
        return getTimer().setTimeout(callback, delay, args);
    }

    public Timer getTimer() {
        if (mTimer == null) {
            throw new IllegalStateException(str(R.string.error_thread_is_not_alive));
        }
        return mTimer;
    }

    public boolean clearTimeout(int id) {
        return getTimer().clearTimeout(id);
    }

    public int setInterval(Object listener, long interval, Object... args) {
        return getTimer().setInterval(listener, interval, args);
    }

    public boolean clearInterval(int id) {
        return getTimer().clearInterval(id);
    }

    public int setImmediate(Object listener, Object... args) {
        return getTimer().setImmediate(listener, args);
    }

    public boolean clearImmediate(int id) {
        return getTimer().clearImmediate(id);
    }

    public void waitFor() throws InterruptedException {
        synchronized (mRunningLock) {
            if (!mRunning) {
                mRunningLock.wait();
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Thread[" + getName() + "," + getPriority() + "]";
    }

}
