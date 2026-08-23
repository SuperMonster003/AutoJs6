package org.autojs.autojs.execution;

import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.script.ScriptSource;
import org.junit.Test;

public class ScriptExecutionGlobalListenerTest {

    @Test
    public void preEngineFailureDoesNotCrashTheGlobalListener() {
        ScriptExecution execution = new ScriptExecution() {
            @Override
            public ScriptEngine<? extends ScriptSource> getEngine() {
                return null;
            }

            @Override
            public ScriptSource getSource() {
                return null;
            }

            @Override
            public ScriptExecutionListener getListener() {
                return null;
            }

            @Override
            public ExecutionConfig getConfig() {
                return null;
            }

            @Override
            public int getId() {
                return 0;
            }
        };

        new ScriptExecutionGlobalListener().onException(execution, new RuntimeException("pre-engine failure"));
    }
}
