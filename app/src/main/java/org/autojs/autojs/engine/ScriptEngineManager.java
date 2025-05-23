package org.autojs.autojs.engine;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.tool.Supplier;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Stardust on Jan 27, 2017.
 */
public class ScriptEngineManager {

    public interface EngineLifecycleCallback {

        void onEngineCreate(ScriptEngine<? extends ScriptSource> engine);

        void onEngineRemove(ScriptEngine<? extends ScriptSource> engine);
    }

    private final Set<ScriptEngine<? extends ScriptSource>> mEngines = new HashSet<>();
    private EngineLifecycleCallback mEngineLifecycleCallback;
    private final Map<String, Supplier<ScriptEngine<? extends ScriptSource>>> mEngineSuppliers = new HashMap<>();
    private final Map<String, Object> mGlobalVariableMap = new HashMap<>();
    private final android.content.Context mAndroidContext;
    private final ScriptEngine.OnDestroyListener mOnEngineDestroyListener = this::removeEngine;

    public ScriptEngineManager(Context androidContext) {
        mAndroidContext = androidContext;
    }

    private void addEngine(ScriptEngine<? extends ScriptSource> engine) {
        engine.setOnDestroyListener(mOnEngineDestroyListener);
        synchronized (mEngines) {
            mEngines.add(engine);
            if (mEngineLifecycleCallback != null) {
                mEngineLifecycleCallback.onEngineCreate(engine);
            }
        }
    }

    public void setEngineLifecycleCallback(EngineLifecycleCallback engineLifecycleCallback) {
        mEngineLifecycleCallback = engineLifecycleCallback;
    }

    public Set<ScriptEngine<? extends ScriptSource>> getEngines() {
        synchronized (mEngines) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(mEngines));
        }
    }

    public android.content.Context getAndroidContext() {
        return mAndroidContext;
    }

    public void removeEngine(ScriptEngine<? extends ScriptSource> engine) {
        synchronized (mEngines) {
            if (mEngines.remove(engine) && mEngineLifecycleCallback != null) {
                mEngineLifecycleCallback.onEngineRemove(engine);
            }
        }
    }

    public int stopAll() {
        synchronized (mEngines) {
            int n = mEngines.size();
            for (ScriptEngine<? extends ScriptSource> engine : mEngines) {
                engine.forceStop();
            }
            return n;
        }
    }

    public void putGlobal(String varName, Object value) {
        mGlobalVariableMap.put(varName, value);
    }

    public void removeGlobal(String varName) {
        mGlobalVariableMap.remove(varName);
    }

    protected void putProperties(ScriptEngine<? extends ScriptSource> engine) {
        for (Map.Entry<String, Object> variable : mGlobalVariableMap.entrySet()) {
            engine.put(variable.getKey(), variable.getValue());
        }
    }

    @Nullable
    public ScriptEngine<? extends ScriptSource> createEngine(String name, int id) {
        Supplier<ScriptEngine<? extends ScriptSource>> s = mEngineSuppliers.get(name);
        if (s == null) {
            return null;
        }
        ScriptEngine<? extends ScriptSource> engine = s.get();
        engine.setId(id);
        putProperties(engine);
        addEngine(engine);
        return engine;
    }

    @Nullable
    public ScriptEngine<? extends ScriptSource> createEngineOfSource(ScriptSource source, int id) {
        return createEngine(source.getEngineName(), id);
    }

    @NonNull
    public ScriptEngine<? extends ScriptSource> createEngineOfSourceOrThrow(ScriptSource source, int id) {
        ScriptEngine<? extends ScriptSource> engine = createEngineOfSource(source, id);
        if (engine == null)
            throw new ScriptEngineFactory.EngineNotFoundException("source: " + source);
        return engine;
    }

    @NonNull
    public ScriptEngine<? extends ScriptSource> createEngineOfSourceOrThrow(ScriptSource source) {
       return createEngineOfSourceOrThrow(source, ScriptExecution.NO_ID);
    }

    public void registerEngine(String name, Supplier<ScriptEngine<? extends ScriptSource>> supplier) {
        mEngineSuppliers.put(name, supplier);
    }

    public void unregisterEngine(String name) {
        mEngineSuppliers.remove(name);
    }

}
