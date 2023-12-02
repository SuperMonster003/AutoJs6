package org.autojs.autojs.engine;

import static org.autojs.autojs.util.StringUtils.str;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.tool.Supplier;
import org.autojs.autojs6.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Stardust on Aug 2, 2017.
 */
public class ScriptEngineFactory {

    public static class EngineNotFoundException extends RuntimeException {

        public EngineNotFoundException(String s) {
            super(s);
        }

    }

    private static final ScriptEngineFactory sInstance = new ScriptEngineFactory();
    private final Map<String, Supplier<ScriptEngine<? extends ScriptSource>>> mEngines = new HashMap<>();
    private final Map<String, Object> mGlobalVariableMap = new HashMap<>();

    public static ScriptEngineFactory getInstance() {
        return sInstance;
    }

    public void putGlobal(String varName, Object value) {
        mGlobalVariableMap.put(varName, value);
    }

    protected void putProperties(ScriptEngine<? extends ScriptSource> engine) {
        for (Map.Entry<String, Object> variable : mGlobalVariableMap.entrySet()) {
            engine.put(variable.getKey(), variable.getValue());
        }
    }

    @Nullable
    public ScriptEngine<? extends ScriptSource> createEngine(String name) {
        Supplier<ScriptEngine<? extends ScriptSource>> s = mEngines.get(name);
        if (s == null) {
            return null;
        }
        ScriptEngine<? extends ScriptSource> engine = s.get();
        putProperties(engine);
        return engine;
    }

    @Nullable
    public ScriptEngine<? extends ScriptSource> createEngineOfSource(ScriptSource source) {
        return createEngine(source.getEngineName());
    }

    @NonNull
    public ScriptEngine<? extends ScriptSource> createEngineByNameOrThrow(String name) {
        ScriptEngine<? extends ScriptSource> engine = createEngine(name);
        if (engine == null) {
            throw new EngineNotFoundException(str(R.string.error_illegal_argument, "name", name));
        }
        return engine;
    }

    @NonNull
    public ScriptEngine<? extends ScriptSource> createEngineOfSourceOrThrow(ScriptSource source) {
        ScriptEngine<? extends ScriptSource> engine = createEngineOfSource(source);
        if (engine == null) {
            throw new EngineNotFoundException(str(R.string.error_illegal_argument, "source", source));
        }
        return engine;
    }

    public void registerEngine(String name, Supplier<ScriptEngine<? extends ScriptSource>> supplier) {
        mEngines.put(name, supplier);
    }

    public void unregisterEngine(String name) {
        mEngines.remove(name);
    }

}
