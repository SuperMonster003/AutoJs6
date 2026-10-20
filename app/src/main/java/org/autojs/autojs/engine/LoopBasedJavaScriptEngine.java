package org.autojs.autojs.engine;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.core.looper.LooperHelper;
import org.autojs.autojs.core.looper.TimerThread;
import org.autojs.autojs.core.ui.UiContentHost;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
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

    public LoopBasedJavaScriptEngine(Context context) {
        super(context);
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

        // Posted through a runnable that drops its reference to this engine once it runs (docs/memory/ROADMAP.md H21).
        // zh-CN: 经由一开始运行就丢弃对本引擎引用的 Runnable 投递 (docs/memory/ROADMAP.md H21).
        mHandler.post(new TimerThread.ReleasingRunnable(r));

        if (!mLooping && isBackgroundThread()) {
            mLooping = true;
            while (true) {
                try {
                    Looper.loop();
                } catch (ContinuationPending ignored) {
                    continue;
                } catch (Throwable t) {
                    mLooping = false;
                    if (AbstractAutoJs.isInrt() && !ScriptInterruptedException.causedByInterrupt(t)) {
                        if (t.getMessage() != null) {
                            ScriptRuntime.popException(t.getMessage());
                        }
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
        // A ui-mode preview root (3-Block Compose roadmap P10.2) releases its session the way the Activity finishes.
        // zh-CN: ui 模式预览根容器 (3-Block Compose 路线图 P10.2) 像 Activity 结束一样释放其会话.
        if (getTag(UiContentHost.ENGINE_TAG) instanceof UiContentHost host) {
            host.onEngineForceStop();
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
