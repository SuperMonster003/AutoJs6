package org.autojs.autojs.execution;

import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.engine.ScriptEngineManager;
import org.autojs.autojs.script.JavaScriptSource;

/**
 * Created by Stardust on 2017/10/27.
 */
public class LoopedBasedJavaScriptExecution extends RunnableScriptExecution {

    public LoopedBasedJavaScriptExecution(ScriptEngineManager manager, ScriptExecutionTask task) {
        super(manager, task);
    }


    protected Object doExecution(final ScriptEngine engine) {
        engine.setTag(ScriptEngine.TAG_SOURCE, getSource());
        getListener().onStart(this);
        long delay = getConfig().getDelay();
        sleep(delay);
        final LoopBasedJavaScriptEngine javaScriptEngine = (LoopBasedJavaScriptEngine) engine;
        final long interval = getConfig().getInterval();
        javaScriptEngine.getRuntime().loopers.setMainLooperQuitHandler(new Loopers.LooperQuitHandler() {
            long times = getConfig().getLoopTimes() == 0 ? Integer.MAX_VALUE : getConfig().getLoopTimes();

            @Override
            public boolean shouldQuit() {
                times--;
                if (times > 0) {
                    sleep(interval);
                    javaScriptEngine.execute(getSource());
                    return false;
                }
                javaScriptEngine.getRuntime().loopers.setMainLooperQuitHandler(null);
                return true;
            }
        });
        javaScriptEngine.execute(getSource());
        return null;
    }

    @Override
    public JavaScriptSource getSource() {
        return (JavaScriptSource) super.getSource();
    }

}
