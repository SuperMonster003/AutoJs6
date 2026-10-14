package org.autojs.autojs.engine;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.autojs.autojs.core.plugin.jvm.JvmSourceUserFacingException;
import org.autojs.autojs.core.plugin.lua.AndroidLuaRuntimeFailureMessages;
import org.autojs.autojs.core.plugin.typescript.TypeScriptRuntimeStackTraceFormatter;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.execution.LoopedBasedJavaScriptExecution;
import org.autojs.autojs.execution.RunnableScriptExecution;
import org.autojs.autojs.execution.ScriptExecuteActivity;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.execution.ScriptExecutionListener;
import org.autojs.autojs.execution.ScriptExecutionObserver;
import org.autojs.autojs.execution.ScriptExecutionTask;
import org.autojs.autojs.execution.SimpleScriptExecutionListener;
import org.autojs.autojs.lang.ThreadCompat;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.Console;
import org.autojs.autojs.runtime.api.augment.engines.Engines;
import org.autojs.autojs.runtime.exception.ScriptCompilationException;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODES;

/**
 * Created by Stardust on Jan 23, 2017.
 */
public class ScriptEngineService {

    private static final String TAG = ScriptEngineService.class.getSimpleName();

    private static ScriptEngineService sInstance;
    private final Context mApplicationContext;
    private final Console mGlobalConsole;
    private final ScriptEngineManager mScriptEngineManager;
    private final EngineLifecycleObserver mEngineLifecycleObserver = new EngineLifecycleObserver() {
        @Override
        public void onEngineRemove(ScriptEngine<? extends ScriptSource> engine) {
            mScriptExecutions.remove(engine.getId());
            super.onEngineRemove(engine);
        }
    };
    private final ScriptExecutionObserver mScriptExecutionObserver = new ScriptExecutionObserver();
    private final Map<Integer, ScriptExecution> mScriptExecutions = Collections.synchronizedMap(new LinkedHashMap<>());

    ScriptEngineService(ScriptEngineServiceBuilder builder) {
        mApplicationContext = builder.uiHandler.getApplicationContext();
        mScriptEngineManager = builder.scriptEngineManager;
        mGlobalConsole = builder.globalConsole;
        mScriptEngineManager.setEngineLifecycleCallback(mEngineLifecycleObserver);
        mScriptExecutionObserver.registerScriptExecutionListener(new SimpleScriptExecutionListener() {
            @Override
            public void onStart(ScriptExecution execution) {
                Log.d(TAG, "onStart");
                ScriptSource scriptSource = execution.getSource();
                if (execution.getEngine() instanceof JavaScriptEngine) {
                    ((JavaScriptEngine) execution.getEngine()).getRuntime().console.setTitle(scriptSource.getName());
                }
                emitEngineEvent("start", execution.getEngine());
                mGlobalConsole.verbose(MessageFormat.format("{0} [{1}].", getLanguageContext().getString(R.string.text_start_running), scriptSource.getElegantPath()));
            }

            @Override
            public void onSuccess(ScriptExecution execution, Object result) {
                onFinish(execution);
            }

            private void onFinish(ScriptExecution execution) {
                // Registered first, so the flag is set before global listeners such as the task list hear of the end.
                // zh-CN: 此监听器最先注册, 因此在任务列表等全局监听器得知结束之前, 标记已经设置.
                if (execution instanceof ScriptExecution.AbstractScriptExecution abstractExecution) {
                    abstractExecution.markFinished();
                }
                var engine = execution.getEngine();
                // Preparation may fail before any engine exists to emit onEngineRemove.
                if (engine == null) {
                    removeExecutionIfSame(mScriptExecutions, execution);
                }
                emitEngineEvent("finish", engine);
                emitEngineEvent("exit", engine);
                emitEngineEvent("stop", engine);
            }

            @Override
            public void onException(ScriptExecution execution, Throwable e) {
                Log.d(TAG, "onException");
                e.printStackTrace();

                var engine = execution.getEngine();
                emitEngineEvent("exception", engine, e);
                emitEngineEvent("error", engine, e);

                onFinish(execution);
                String message = null;
                if (!ScriptInterruptedException.causedByInterrupt(e)) {
                    message = AndroidLuaRuntimeFailureMessages.resolve(getLanguageContext(), e);
                    if (e instanceof ScriptCompilationException compilationException) {
                        message = compilationException.getMessage();
                        mGlobalConsole.error(compilationException.getConsoleMessage());
                    } else if (engine instanceof JavaScriptEngine scriptEngine) {
                        logJavaScriptRuntimeFailure(scriptEngine, e);
                    } else if (e instanceof JvmSourceUserFacingException) {
                        // Provider/compiler details are streamed by RemoteJvmScriptEngine. Keep the
                        // stable terminal message in Console without exposing the Binder cause chain.
                        message = e.getMessage();
                        mGlobalConsole.error(message);
                    } else {
                        // TODO by SuperMonster003 on Aug 15, 2026.
                        //  ! engine.getRuntime().console.error(e);
                        mGlobalConsole.error(e);
                    }
                }
                if (engine instanceof JavaScriptEngine scriptEngine) {
                    Throwable uncaughtException = engine.getUncaughtException();
                    if (uncaughtException != null) {
                        message = uncaughtException.getMessage();
                        logJavaScriptRuntimeFailure(scriptEngine, uncaughtException);
                    }
                }
                if (message != null) {
                    Log.e(TAG, message);
                    // ViewUtils.showToast(mApplicationContext, getLanguageContext().getString(R.string.text_error) + ": " + message, true);
                    ViewUtils.showToast(mApplicationContext, message, true);
                } else {
                    Log.e(TAG, "No exception message");
                }
            }

        });
        mScriptEngineManager.putGlobal("context", mApplicationContext);
        ScriptRuntime.setApplicationContext(mApplicationContext.getApplicationContext());
    }

    public Console getGlobalConsole() {
        return mGlobalConsole;
    }

    private static void logJavaScriptRuntimeFailure(
            JavaScriptEngine engine,
            Throwable error
    ) {
        String mappedStackTrace = TypeScriptRuntimeStackTraceFormatter.format(engine, error);
        engine.getRuntime().console.error(mappedStackTrace == null ? error : mappedStackTrace);
    }

    public Context getLanguageContext() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? mApplicationContext : ContextCompat.getContextForLanguage(mApplicationContext);
    }

    public void registerEngineLifecycleCallback(ScriptEngineManager.EngineLifecycleCallback engineLifecycleCallback) {
        mEngineLifecycleObserver.registerCallback(engineLifecycleCallback);
    }

    public void unregisterEngineLifecycleCallback(ScriptEngineManager.EngineLifecycleCallback engineLifecycleCallback) {
        mEngineLifecycleObserver.unregisterCallback(engineLifecycleCallback);
    }

    public boolean registerGlobalScriptExecutionListener(ScriptExecutionListener listener) {
        return mScriptExecutionObserver.registerScriptExecutionListener(listener);
    }

    public boolean unregisterGlobalScriptExecutionListener(ScriptExecutionListener listener) {
        return mScriptExecutionObserver.removeScriptExecutionListener(listener);
    }

    public ScriptExecution execute(ScriptExecutionTask task) {
        if (task.getConfig().nodeLaunchStartedAtMs == 0L) {
            task.getConfig().nodeLaunchStartedAtMs = android.os.SystemClock.elapsedRealtime();
        }
        if (task.getListener() != null) {
            task.setExecutionListener(new ScriptExecutionObserver.Wrapper(mScriptExecutionObserver, task.getListener()));
        } else {
            task.setExecutionListener(mScriptExecutionObserver);
        }
        ScriptSource source = task.getSource();
        if (source instanceof JavaScriptSource src) {
            if (JavaScriptSource.ENGINE_NODEJS.equals(src.getEngineName())) {
                NodeJsRuntimePluginHost.prepareForExecution(mApplicationContext);
            }
            int mode = src.getExecutionMode();
            if ((mode & JavaScriptSource.EXECUTION_MODE_UI) != 0
                    && !JavaScriptSource.ENGINE_BUN.equals(src.getEngineName())
                    && !JavaScriptSource.ENGINE_QUICKJS.equals(src.getEngineName())) {
                var execution = new ScriptExecuteActivity.ActivityScriptExecution(mScriptEngineManager, task);
                return startRegisteredExecution(mScriptExecutions, execution,
                        () -> ScriptExecuteActivity.start(mApplicationContext, execution));
            }
        }
        RunnableScriptExecution r;
        if (source instanceof JavaScriptSource) {
            Log.d(TAG, "JavaScriptSource: true");
            r = new LoopedBasedJavaScriptExecution(mScriptEngineManager, task);
        } else {
            Log.d(TAG, "JavaScriptSource: false");
            r = new RunnableScriptExecution(mScriptEngineManager, task);
        }
        return startRegisteredExecution(mScriptExecutions, r, () -> {
            new ThreadCompat(r).start();
            return true;
        });
    }

    /** Publish before a launched Activity or worker can observe its ID; never hold the table lock while starting. */
    static <T extends ScriptExecution> T startRegisteredExecution(
            Map<Integer, ScriptExecution> executions, T execution, BooleanSupplier start) {
        int id = execution.getId();
        synchronized (executions) {
            executions.put(id, execution);
        }
        boolean started = false;
        try {
            started = start.getAsBoolean();
            return execution;
        } finally {
            if (!started) {
                removeExecutionIfSame(executions, execution);
            }
        }
    }

    private static void removeExecutionIfSame(Map<Integer, ScriptExecution> executions, ScriptExecution execution) {
        int id = execution.getId();
        synchronized (executions) {
            if (executions.get(id) == execution) {
                executions.remove(id);
            }
        }
    }

    @Nullable
    public static String getModuleNameFromMode(Integer value) {
        return EXECUTION_MODES.entrySet().stream()
                .filter(entry -> value.equals(entry.getValue()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public ScriptExecution execute(ScriptSource source, ScriptExecutionListener listener, ExecutionConfig config) {
        return execute(new ScriptExecutionTask(source, listener, config));
    }

    public ScriptExecution execute(ScriptSource source, ExecutionConfig config) {
        return execute(new ScriptExecutionTask(source, null, config));
    }

    public int stopAll() {
        return mScriptEngineManager.stopAll();
    }

    public int stopAllAndToast() {
        int n = stopAll();
        if (n > 0) {
            ViewUtils.showToast(mApplicationContext, mApplicationContext.getResources().getQuantityString(R.plurals.text_already_stop_n_scripts, n, n));
        }
        return n;
    }

    public Set<ScriptEngine<? extends ScriptSource>> getEngines() {
        return mScriptEngineManager.getEngines();
    }

    public Collection<ScriptExecution> getScriptExecutions() {
        synchronized (mScriptExecutions) {
            return new ArrayList<>(mScriptExecutions.values());
        }
    }

    @Nullable
    public ScriptExecution getScriptExecution(int id) {
        if (id == ScriptExecution.NO_ID) {
            return null;
        }
        return mScriptExecutions.get(id);
    }

    public void removeScriptExecution(int id) {
        mScriptExecutions.remove(id);
    }

    private void emitEngineEvent(String eventName, ScriptEngine<? extends ScriptSource> engine, Object... args) {
        for (ScriptEngine<?> scriptEngine : getEngines()) {
            if (scriptEngine instanceof JavaScriptEngine jsEngine) {
                try {
                    ScriptRuntime runtime = jsEngine.getRuntime();
                    Engines.emit(runtime, eventName, engine, args);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to emit engine event: " + eventName + ", engine=" + scriptEngine, e);
                }
            }
        }
    }

    public static void setInstance(ScriptEngineService service) {
        if (sInstance != null) {
            throw new IllegalStateException();
        }
        sInstance = service;
    }

    public static ScriptEngineService getInstance() {
        return sInstance;
    }

    private static class EngineLifecycleObserver implements ScriptEngineManager.EngineLifecycleCallback {

        private final Set<ScriptEngineManager.EngineLifecycleCallback> mEngineLifecycleCallbacks = new LinkedHashSet<>();

        @Override
        public void onEngineCreate(ScriptEngine<? extends ScriptSource> engine) {
            synchronized (mEngineLifecycleCallbacks) {
                for (ScriptEngineManager.EngineLifecycleCallback callback : mEngineLifecycleCallbacks) {
                    callback.onEngineCreate(engine);
                }
            }
        }

        @Override
        public void onEngineRemove(ScriptEngine<? extends ScriptSource> engine) {
            synchronized (mEngineLifecycleCallbacks) {
                for (ScriptEngineManager.EngineLifecycleCallback callback : mEngineLifecycleCallbacks) {
                    callback.onEngineRemove(engine);
                }
            }
        }

        void registerCallback(ScriptEngineManager.EngineLifecycleCallback callback) {
            synchronized (mEngineLifecycleCallbacks) {
                mEngineLifecycleCallbacks.add(callback);
            }
        }

        void unregisterCallback(ScriptEngineManager.EngineLifecycleCallback callback) {
            synchronized (mEngineLifecycleCallbacks) {
                mEngineLifecycleCallbacks.remove(callback);
            }
        }
    }

    private static class ScriptExecutionEvent {

        private final int mCode;
        private final String mMessage;

        ScriptExecutionEvent(int code, String message) {
            mCode = code;
            mMessage = message;
        }

        public int getCode() {
            return mCode;
        }

        public String getMessage() {
            return mMessage;
        }

    }

}
