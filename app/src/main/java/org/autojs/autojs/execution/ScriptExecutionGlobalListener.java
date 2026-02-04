package org.autojs.autojs.execution;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.runtime.api.Console;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs6.R;

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
        engine.setTag(ENGINE_TAG_START_TIME, System.currentTimeMillis());
    }

    @Override
    public void onSuccess(ScriptExecution execution, Object result) {
        onFinish(execution);
    }

    private void onFinish(ScriptExecution execution) {
        ScriptEngine<? extends ScriptSource> engine = execution.getEngine();
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
        onFinish(execution);
    }
}