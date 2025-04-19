package org.autojs.autojs.execution;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.engine.JavaScriptEngine;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.Console;
import org.autojs.autojs.runtime.api.augment.engines.Engines;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs6.R;

import java.util.List;
import java.util.stream.Collectors;

import static org.autojs.autojs.util.StringUtils.str;

/**
 * Created by Stardust on May 3, 2017.
 * Modified by SuperMonster003 as of Jan 1, 2022.
 */
public class ScriptExecutionGlobalListener implements ScriptExecutionListener {

    private static final String ENGINE_TAG_START_TIME = "org.autojs.autojs.autojs.Goodbye, World";

    @Override
    public void onStart(ScriptExecution execution) {
        ScriptEngine<? extends ScriptSource> engine = execution.getEngine();

        emitEngineEvent("start", engine);

        engine.setTag(ENGINE_TAG_START_TIME, System.currentTimeMillis());
    }

    @Override
    public void onSuccess(ScriptExecution execution, Object result) {
        onFinish(execution);
    }

    private void onFinish(ScriptExecution execution) {
        ScriptEngine<? extends ScriptSource> engine = execution.getEngine();

        emitEngineEvent("finish", engine);
        emitEngineEvent("exit", engine);
        emitEngineEvent("stop", engine);

        Long startTime = (Long) engine.getTag(ENGINE_TAG_START_TIME);
        if (startTime != null) {
            printSeconds(execution, startTime);
        }
    }

    private void printSeconds(ScriptExecution execution, Long startTime) {
        double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
        String secondsString = String.format(Language.getPrefLanguage().getLocale(), "%.3f", seconds).stripTrailing();
        Console console = AutoJs.getInstance().getScriptEngineService().getGlobalConsole();
        String path = execution.getSource().getElegantPath();
        console.verbose(str(R.string.text_execution_finished, path, secondsString));
    }

    @Override
    public void onException(ScriptExecution execution, Throwable e) {
        ScriptEngine<? extends ScriptSource> engine = execution.getEngine();

        emitEngineEvent("exception", engine, e);
        emitEngineEvent("error", engine, e);

        onFinish(execution);
    }

    private void emitEngineEvent(String eventName, ScriptEngine<? extends ScriptSource> engine, Object... args) {
        List<JavaScriptEngine> jsEngines = AutoJs.getInstance()
                .getScriptEngineService()
                .getEngines()
                .stream()
                .filter(JavaScriptEngine.class::isInstance)
                .map(JavaScriptEngine.class::cast)
                .collect(Collectors.toList());

        for (JavaScriptEngine jsEngine : jsEngines) {
            ScriptRuntime runtime = jsEngine.getRuntime();
            Engines.emit(runtime, eventName, engine, args);
        }
    }

}