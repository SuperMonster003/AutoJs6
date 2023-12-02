package org.autojs.autojs.execution;

import static org.autojs.autojs.util.StringUtils.str;

import android.annotation.SuppressLint;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.pref.Language;
import org.autojs.autojs.runtime.api.Console;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on May 3, 2017.
 */
public class ScriptExecutionGlobalListener implements ScriptExecutionListener {
    private static final String ENGINE_TAG_START_TIME = "org.autojs.autojs.autojs.Goodbye, World";

    @Override
    public void onStart(ScriptExecution execution) {
        execution.getEngine().setTag(ENGINE_TAG_START_TIME, System.currentTimeMillis());
    }

    @Override
    public void onSuccess(ScriptExecution execution, Object result) {
        onFinish(execution);
    }

    @SuppressLint("DefaultLocale")
    private void onFinish(ScriptExecution execution) {
        Long millis = (Long) execution.getEngine().getTag(ENGINE_TAG_START_TIME);
        if (millis != null) {
            printSeconds(execution, millis);
        }
    }

    private void printSeconds(ScriptExecution execution, Long millis) {
        double seconds = (System.currentTimeMillis() - millis) / 1000.0;
        String secondsString = String.format(Language.getPrefLanguage().getLocale(), "%.3f", seconds).stripTrailing();
        printSeconds(execution, secondsString);
    }

    private void printSeconds(ScriptExecution execution, String seconds) {
        Console console = AutoJs.getInstance().getScriptEngineService().getGlobalConsole();
        String path = execution.getSource().getElegantPath();
        console.verbose(str(R.string.text_execution_finished, path, seconds));
    }

    @Override
    public void onException(ScriptExecution execution, Throwable e) {
        onFinish(execution);
    }

}
