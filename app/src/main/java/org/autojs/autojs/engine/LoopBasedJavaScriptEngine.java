package org.autojs.autojs.engine;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.looper.LooperHelper;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.ContinuationPending;

import static org.autojs.autojs.util.RhinoUtils.isBackgroundThread;

/**
 * Created by Stardust on Jul 28, 2017.
 */
public class LoopBasedJavaScriptEngine extends RhinoJavaScriptEngine {

    private final String TAG = LoopBasedJavaScriptEngine.class.getSimpleName();

    private Handler mHandler;
    private boolean mLooping = false;

    public LoopBasedJavaScriptEngine(ScriptRuntime scriptRuntime, Context context) {
        super(scriptRuntime, context);
    }

    public interface ExecuteCallback {
        void onResult(@Nullable Object r);

        void onException(Throwable e);
    }

    @Override
    public Object execute(@NotNull JavaScriptSource source) {
        execute(source, null);
        return null;
    }

    public void execute(ScriptSource source, ExecuteCallback callback) {
        Runnable r = () -> {
            try {
                Object o = LoopBasedJavaScriptEngine.super.execute((JavaScriptSource) source);
                if (callback != null) {
                    callback.onResult(o);
                }
            } catch (ContinuationPending ignored) {
                /* Ignored. */
            } catch (Throwable e) {
                if (e.getMessage() != null) {
                    Log.e(TAG, e.getMessage());
                }
                if (callback == null) {
                    throw e;
                } else {
                    callback.onException(e);
                }
            }
        };

        mHandler.post(r);

        if (!mLooping && isBackgroundThread()) {
            mLooping = true;
            while (true) {
                try {
                    Looper.loop();
                } catch (ContinuationPending ignored) {
                    continue;
                } catch (Throwable t) {
                    mLooping = false;
                    if (AbstractAutoJs.isInrt() && t.getMessage() != null) {
                        ScriptRuntime.popException(t.getMessage());
                    }
                    throw t;
                }
                break;
            }
        }
    }

    @Override
    public void forceStop() {
        LooperHelper.quitForThread(getThread());
        Activity activity = (Activity) getTag("activity");
        if (activity != null) {
            activity.finish();
        }
        super.forceStop();
    }

    public boolean isRunning() {
        return LooperHelper.contains(getThread());
    }

    public boolean isStopped() {
        return !isRunning();
    }

    @Override
    public synchronized void destroy() {
        Thread thread = getThread();
        LooperHelper.quitForThread(thread);
        super.destroy();
    }

    @Override
    public void init() {
        LooperHelper.prepare();
        mHandler = new Handler();
        super.init();
    }

}
