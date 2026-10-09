package org.autojs.autojs.execution;

import android.util.Log;
import androidx.annotation.Nullable;
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler;
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionPerformance;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.engine.ScriptEngineManager;
import org.autojs.autojs.lang.ThreadCompat;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.script.ScriptSource;

/**
 * Created by Stardust on May 1, 2017.
 */
public class RunnableScriptExecution extends ScriptExecution.AbstractScriptExecution implements Runnable {

    private static final String TAG = "RunnableJSExecution";
    private ScriptEngine<? extends ScriptSource> mScriptEngine;
    private ScriptSource mExecutionSource;
    private final ScriptEngineManager mScriptEngineManager;

    public RunnableScriptExecution(ScriptEngineManager manager, ScriptExecutionTask task) {
        super(task);
        mScriptEngineManager = manager;
    }

    @Override
    public void run() {
        ThreadCompat.currentThread().setName("ScriptThread-" + getId() + "[" + getSource() + "]");
        execute();
    }

    public Object execute() {
        try {
            boolean typeScriptPreparation = TypeScriptExecutionCompiler.requiresPreparation(getSource());
            long compileStartedAt = android.os.SystemClock.elapsedRealtime();
            mExecutionSource = TypeScriptExecutionCompiler.prepare(
                    mScriptEngineManager.getAndroidContext(),
                    getSource()
            );
            long prepareMs = android.os.SystemClock.elapsedRealtime() - compileStartedAt;
            getConfig().nodeTypeScriptPrepareMs = prepareMs;
            long engineCreateStartedAt = android.os.SystemClock.elapsedRealtime();
            mScriptEngine = mScriptEngineManager.createEngineOfSourceOrThrow(mExecutionSource, getId());
            mScriptEngine.setTag(ExecutionConfig.tag, getConfig());
            if (typeScriptPreparation) {
                TypeScriptExecutionPerformance.report(
                        TypeScriptExecutionPerformance.ENTRY_THREAD,
                        prepareMs,
                        android.os.SystemClock.elapsedRealtime() - engineCreateStartedAt
                );
            }
        } catch (Throwable e) {
            if (mScriptEngine != null) {
                mScriptEngine.destroy();
            }
            onException(mScriptEngine, e);
            return null;
        }
        return execute(mScriptEngine);
    }

    private Object execute(ScriptEngine<? extends ScriptSource> engine) {
        try {
            prepare(engine);
            Object r = doExecution(engine);
            Throwable uncaughtException = engine.getUncaughtException();
            if (uncaughtException != null) {
                onException(engine, uncaughtException);
                return null;
            }
            getListener().onSuccess(this, r);
            return r;
        } catch (Throwable e) {
            onException(engine, e);
            return null;
        } finally {
            Log.d(TAG, "Engine destroy");
            engine.destroy();
        }
    }

    protected void onException(@Nullable ScriptEngine<? extends ScriptSource> engine, Throwable e) {
        Log.w(TAG, "onException: engine = " + engine, e);
        getListener().onException(this, e);
    }

    private void prepare(ScriptEngine<? extends ScriptSource> engine) {
        engine.setTag(ScriptEngine.TAG_WORKING_DIRECTORY, getConfig().getWorkingDirectory());
        engine.setTag(ScriptEngine.TAG_ENV_PATH, getConfig().getEnvPath());
        engine.setTag(ScriptEngine.TAG_SOURCE, getExecutionSource());
        engine.init();
    }

    protected Object doExecution(ScriptEngine<? extends ScriptSource> engine) {
        engine.setTag(ScriptEngine.TAG_SOURCE, getExecutionSource());
        getListener().onStart(this);
        Object result = null;
        long delay = getConfig().getDelay();
        int times = getConfig().getLoopTimes();
        if (times == 0) {
            times = Integer.MAX_VALUE;
        }
        long interval = getConfig().getInterval();
        sleep(delay);
        ScriptSource source = getExecutionSource();
        for (int i = 0; i < times; i++) {
            result = execute(engine, source);
            sleep(interval);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected Object execute(ScriptEngine engine, ScriptSource source) {
        return engine.execute(source);
    }

    protected void sleep(long i) {
        if (i <= 0) {
            return;
        }
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            throw new ScriptInterruptedException();
        }
    }

    protected ScriptSource getExecutionSource() {
        return mExecutionSource == null ? getSource() : mExecutionSource;
    }

    @Override
    public ScriptEngine<? extends ScriptSource> getEngine() {
        return mScriptEngine;
    }

}
