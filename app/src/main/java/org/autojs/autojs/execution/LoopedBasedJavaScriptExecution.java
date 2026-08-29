package org.autojs.autojs.execution;

import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine;
import org.autojs.autojs.engine.LoopBasedJavaScriptEngine.ExecuteCallback;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.engine.ScriptEngineManager;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Stardust on Oct 27, 2017.
 */
public class LoopedBasedJavaScriptExecution extends RunnableScriptExecution {

    public LoopedBasedJavaScriptExecution(ScriptEngineManager manager, ScriptExecutionTask task) {
        super(manager, task);
    }

    protected Object doExecution(ScriptEngine<? extends ScriptSource> engine) {
        if (!(engine instanceof LoopBasedJavaScriptEngine javaScriptEngine)) {
            return super.doExecution(engine);
        }
        JavaScriptSource source = (JavaScriptSource) getExecutionSource();
        engine.setTag(ScriptEngine.TAG_SOURCE, source);
        getListener().onStart(this);
        long delay = getConfig().getDelay();
        sleep(delay);
        long interval = getConfig().getInterval();
        AtomicReference<Object> lastResult = new AtomicReference<>();
        ExecuteCallback callback = new ExecuteCallback() {
            @Override
            public void onResult(Object result) {
                lastResult.set(result);
            }

            @Override
            public void onException(Throwable error) {
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (error instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new RuntimeException(error);
            }
        };

        javaScriptEngine.getRuntime().loopers.setMainLooperQuitHandler(new Loopers.LooperQuitHandler() {
            long times = getConfig().getLoopTimes() == 0 ? Integer.MAX_VALUE : getConfig().getLoopTimes();

            @Override
            public boolean shouldQuit() {
                times--;
                if (times > 0) {
                    sleep(interval);
                    javaScriptEngine.execute(source, callback);
                    return false;
                }
                javaScriptEngine.getRuntime().loopers.setMainLooperQuitHandler(null);
                return true;
            }
        });
        javaScriptEngine.execute(source, callback);

        return lastResult.get();
    }

    @Override
    public JavaScriptSource getSource() {
        return (JavaScriptSource) super.getSource();
    }

}
