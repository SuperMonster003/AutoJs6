package org.autojs.autojs.autojs;

import android.annotation.SuppressLint;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.execution.ScriptExecution;
import com.stardust.autojs.execution.ScriptExecutionListener;
import com.stardust.autojs.runtime.api.Console;

import org.autojs.autojs.R;

import java.math.BigDecimal;

/**
 * Created by Stardust on 2017/5/3.
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

        @SuppressLint("DefaultLocale")
        BigDecimal secondsString = new BigDecimal(String.format("%.3f", seconds)).stripTrailingZeros();

        printSeconds(execution, secondsString);
    }

    private void printSeconds(ScriptExecution execution, BigDecimal seconds) {
        Console console = AutoJs.getInstance().getScriptEngineService().getGlobalConsole();
        console.verbose(GlobalAppContext.getString(R.string.text_execution_finished), execution.getSource().toString(), seconds);
    }

    @Override
    public void onException(ScriptExecution execution, Throwable e) {
        onFinish(execution);
    }

}
