package org.autojs.autojs.execution;

import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.script.ScriptSource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stardust on Apr 3, 2017.
 */
public interface ScriptExecution {

    int NO_ID = -1;

    ScriptEngine<? extends ScriptSource> getEngine();

    ScriptSource getSource();

    ScriptExecutionListener getListener();

    ExecutionConfig getConfig();

    int getId();

    abstract class AbstractScriptExecution implements ScriptExecution {

        private static final AtomicInteger sMaxId = new AtomicInteger(0);

        protected ScriptExecutionTask mScriptExecutionTask;
        protected int mId;

        public AbstractScriptExecution(ScriptExecutionTask task) {
            mScriptExecutionTask = task;
            mId = sMaxId.getAndIncrement();
        }

        @Override
        public abstract ScriptEngine<? extends ScriptSource> getEngine();

        @Override
        public ScriptSource getSource() {
            return mScriptExecutionTask.getSource();
        }

        @Override
        public ScriptExecutionListener getListener() {
            return mScriptExecutionTask.getListener();
        }

        @Override
        public ExecutionConfig getConfig() {
            return mScriptExecutionTask.getConfig();
        }

        @Override
        public int getId() {
            return mId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AbstractScriptExecution that = (AbstractScriptExecution) o;
            return mId == that.mId;
        }

        @Override
        public int hashCode() {
            return mId;
        }
    }
}
