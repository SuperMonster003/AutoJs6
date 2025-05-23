package org.autojs.autojs.engine;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODES;
import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODE_MODULE_AXIOS;
import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODE_MODULE_CHEERIO;
import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODE_MODULE_DAYJS;
import static org.autojs.autojs.script.JavaScriptSource.EXECUTION_MODE_MODULE_I18N;

/**
 * Created by Stardust on Jan 23, 2017.
 */
public class ScriptEngineService {

    private static final String TAG = ScriptEngineService.class.getSimpleName();

    public static final List<Integer> GLOBAL_MODULES = List.of(
            EXECUTION_MODE_MODULE_AXIOS,
            EXECUTION_MODE_MODULE_CHEERIO,
            EXECUTION_MODE_MODULE_DAYJS,
            EXECUTION_MODE_MODULE_I18N);

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
    private final LinkedHashMap<Integer, ScriptExecution> mScriptExecutions = new LinkedHashMap<>();

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
                mGlobalConsole.verbose(MessageFormat.format("{0} [{1}].", getLanguageContext().getString(R.string.text_start_running), scriptSource.getElegantPath()));
            }

            @Override
            public void onSuccess(ScriptExecution execution, Object result) {
                onFinish(execution);
            }

            private void onFinish(ScriptExecution execution) {
                /* Empty function body. */
            }

            @Override
            public void onException(ScriptExecution execution, Throwable e) {
                Log.d(TAG, "onException");
                e.printStackTrace();
                onFinish(execution);
                String message = null;
                if (!ScriptInterruptedException.causedByInterrupt(e)) {
                    message = e.getMessage();
                    if (execution.getEngine() instanceof JavaScriptEngine engine) {
                        engine.getRuntime().console.error(e);
                    }
                }
                if (execution.getEngine() instanceof JavaScriptEngine engine) {
                    Throwable uncaughtException = engine.getUncaughtException();
                    if (uncaughtException != null) {
                        message = uncaughtException.getMessage();
                        engine.getRuntime().console.error(uncaughtException);
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
        ScriptExecution execution = executeInternal(task);
        mScriptExecutions.put(execution.getId(), execution);
        return execution;
    }

    private ScriptExecution executeInternal(ScriptExecutionTask task) {
        if (task.getListener() != null) {
            task.setExecutionListener(new ScriptExecutionObserver.Wrapper(mScriptExecutionObserver, task.getListener()));
        } else {
            task.setExecutionListener(mScriptExecutionObserver);
        }
        ScriptSource source = task.getSource();
        if (source instanceof JavaScriptSource src) {
            GLOBAL_MODULES.forEach((mode) -> handleGlobalModuleExecution(src, mode, getModuleNameFromMode(mode)));
            int mode = src.getExecutionMode();
            if ((mode & JavaScriptSource.EXECUTION_MODE_UI) != 0) {
                return ScriptExecuteActivity.execute(mApplicationContext, mScriptEngineManager, task);
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
        new ThreadCompat(r).start();
        return r;
    }

    @Nullable
    public static String getModuleNameFromMode(Integer value) {
        return EXECUTION_MODES.entrySet().stream()
                .filter(entry -> value.equals(entry.getValue()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void handleGlobalModuleExecution(JavaScriptSource source, int executionMode, @Nullable String moduleName) {
        if (moduleName == null) return;
        if ((source.getExecutionMode() & executionMode) != 0) {
            mScriptEngineManager.putGlobal(moduleName, new ScriptModuleIdentifier(moduleName));
        } else {
            mScriptEngineManager.removeGlobal(moduleName);
        }
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
        return mScriptExecutions.values();
    }

    @Nullable
    public ScriptExecution getScriptExecution(int id) {
        if (id == ScriptExecution.NO_ID) {
            return null;
        }
        return mScriptExecutions.get(id);
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

    public static class ScriptModuleIdentifier {

        public String moduleFileName;

        public ScriptModuleIdentifier(String moduleFileName) {
            this.moduleFileName = moduleFileName;
        }

    }

}
