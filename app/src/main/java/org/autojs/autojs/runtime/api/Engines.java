package org.autojs.autojs.runtime.api;

import org.autojs.autojs.engine.JavaScriptEngine;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.script.AutoFileSource;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.script.StringScriptSource;

import java.util.Set;

/**
 * Created by Stardust on 2017/8/4.
 */
public class Engines {

    private final ScriptEngineService mEngineService;
    private JavaScriptEngine mScriptEngine;
    private final ScriptRuntime mScriptRuntime;

    public Engines(ScriptEngineService engineService, ScriptRuntime scriptRuntime) {
        mEngineService = engineService;
        mScriptRuntime = scriptRuntime;
    }

    public ScriptExecution execScript(String name, String script, ExecutionConfig config) {
        StringScriptSource scriptSource = new StringScriptSource(name, script);
        scriptSource.setPrefix("$engine/");
        return mEngineService.execute(scriptSource, config);
    }

    public ScriptExecution execScriptFile(String path, ExecutionConfig config) {
        return mEngineService.execute(new JavaScriptFileSource(mScriptRuntime.files.path(path)), config);
    }

    public ScriptExecution execAutoFile(String path, ExecutionConfig config) {
        return mEngineService.execute(new AutoFileSource(mScriptRuntime.files.path(path)), config);
    }

    public Object all() {
        return mScriptRuntime.bridges.toArray(mEngineService.getEngines());
    }

    public int stopAll() {
        return mEngineService.stopAll();
    }

    public void stopAllAndToast() {
        mEngineService.stopAllAndToast();
    }

    public void setCurrentEngine(JavaScriptEngine engine) {
        if (mScriptEngine != null)
            throw new IllegalStateException();
        mScriptEngine = engine;
    }

    public Set<ScriptEngine> getEngines() {
        return mEngineService.getEngines();
    }

    public JavaScriptEngine myEngine() {
        return mScriptEngine;
    }

}
