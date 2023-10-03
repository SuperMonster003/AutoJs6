package org.autojs.autojs.engine;

import android.content.Context;

import androidx.annotation.Nullable;

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
import org.autojs.autojs.tool.UiHandler;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by Stardust on 2017/1/23.
 */
public class ScriptEngineService {

    private static final String LOG_TAG = "ScriptEngineService";
    private static final EventBus EVENT_BUS = new EventBus();
    private static final ScriptExecutionListener GLOBAL_LISTENER = new SimpleScriptExecutionListener() {
        @Override
        public void onStart(ScriptExecution execution) {
            ScriptSource scriptSource = execution.getSource();
            if (execution.getEngine() instanceof JavaScriptEngine) {
                ((JavaScriptEngine) execution.getEngine()).getRuntime().console.setTitle(scriptSource.getName());
            }
            EVENT_BUS.post(new ScriptExecutionEvent(ScriptExecutionEvent.ON_START, scriptSource.getElegantPath()));
        }

        @Override
        public void onSuccess(ScriptExecution execution, Object result) {
            onFinish(execution);
        }

        private void onFinish(ScriptExecution execution) {

        }

        @Override
        public void onException(ScriptExecution execution, Throwable e) {
            e.printStackTrace();
            onFinish(execution);
            String message = null;
            if (!ScriptInterruptedException.causedByInterrupted(e)) {
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
                EVENT_BUS.post(new ScriptExecutionEvent(ScriptExecutionEvent.ON_EXCEPTION, message));
            }
        }

    };


    private static ScriptEngineService sInstance;
    private final Context mContext;
    private final UiHandler mUiHandler;
    private final Console mGlobalConsole;
    private final ScriptEngineManager mScriptEngineManager;
    private final EngineLifecycleObserver mEngineLifecycleObserver = new EngineLifecycleObserver() {

        @Override
        public void onEngineRemove(ScriptEngine engine) {
            mScriptExecutions.remove(engine.getId());
            super.onEngineRemove(engine);
        }
    };
    private final ScriptExecutionObserver mScriptExecutionObserver = new ScriptExecutionObserver();
    private final LinkedHashMap<Integer, ScriptExecution> mScriptExecutions = new LinkedHashMap<>();

    ScriptEngineService(ScriptEngineServiceBuilder builder) {
        mUiHandler = builder.mUiHandler;
        mContext = mUiHandler.getContext();
        mScriptEngineManager = builder.mScriptEngineManager;
        mGlobalConsole = builder.mGlobalConsole;
        mScriptEngineManager.setEngineLifecycleCallback(mEngineLifecycleObserver);
        mScriptExecutionObserver.registerScriptExecutionListener(GLOBAL_LISTENER);
        EVENT_BUS.register(this);
        mScriptEngineManager.putGlobal("context", mContext);
        ScriptRuntime.setApplicationContext(mContext.getApplicationContext());
    }

    public Console getGlobalConsole() {
        return mGlobalConsole;
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
        if (source instanceof JavaScriptSource) {
            int mode = ((JavaScriptSource) source).getExecutionMode();
            if ((mode & JavaScriptSource.EXECUTION_MODE_UI) != 0) {
                return ScriptExecuteActivity.execute(mContext, mScriptEngineManager, task);
            }
        }
        RunnableScriptExecution r;
        if (source instanceof JavaScriptSource) {
            r = new LoopedBasedJavaScriptExecution(mScriptEngineManager, task);
        } else {
            r = new RunnableScriptExecution(mScriptEngineManager, task);
        }
        new ThreadCompat(r).start();
        return r;
    }

    public ScriptExecution execute(ScriptSource source, ScriptExecutionListener listener, ExecutionConfig config) {
        return execute(new ScriptExecutionTask(source, listener, config));
    }

    public ScriptExecution execute(ScriptSource source, ExecutionConfig config) {
        return execute(new ScriptExecutionTask(source, null, config));
    }

    @Subscribe
    public void onScriptExecution(ScriptExecutionEvent event) {
        switch (event.getCode()) {
            case ScriptExecutionEvent.ON_START -> mGlobalConsole.verbose(MessageFormat.format("{0} [{1}].", mContext.getString(R.string.text_start_running), event.getMessage()));
            case ScriptExecutionEvent.ON_EXCEPTION -> mUiHandler.toast(mContext.getString(R.string.text_error) + ": " + event.getMessage());
        }
    }

    public int stopAll() {
        return mScriptEngineManager.stopAll();
    }

    public int stopAllAndToast() {
        int n = stopAll();
        if (n > 0) {
            mUiHandler.toast(mContext.getResources().getQuantityString(R.plurals.text_already_stop_n_scripts, n, n));
        }
        return n;
    }

    public Set<ScriptEngine> getEngines() {
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
        public void onEngineCreate(ScriptEngine engine) {
            synchronized (mEngineLifecycleCallbacks) {
                for (ScriptEngineManager.EngineLifecycleCallback callback : mEngineLifecycleCallbacks) {
                    callback.onEngineCreate(engine);
                }
            }
        }

        @Override
        public void onEngineRemove(ScriptEngine engine) {
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

        static final int ON_START = 1001;
        static final int ON_SUCCESS = 1002;
        static final int ON_EXCEPTION = 1003;

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
