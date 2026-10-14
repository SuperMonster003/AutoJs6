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

    /**
     * True once the execution has reported its result, even if its engine is still being torn down
     * (a UI script keeps its execution registered until its Activity is destroyed).
     * zh-CN: 执行已报告结果后即为 true, 即使其引擎仍在销毁过程中 (UI 脚本的执行会保持注册, 直到其 Activity 被销毁).
     */
    default boolean isFinished() {
        return false;
    }

    /** Null for an ordinary execution. Agent context is host-owned, separate from execArgv. */
    default AgentScriptExecutionState getAgentContext() {
        return getConfig().getAgentExecutionState();
    }

    default String getAgentRunId() {
        var context = getAgentContext();
        return context == null ? null : context.getRunId();
    }

    default String getAgentParametersJson() {
        var context = getAgentContext();
        return context == null ? null : context.getParametersJson();
    }

    default String getAgentPresetName() {
        var context = getAgentContext();
        return context == null ? null : context.getPresetName();
    }

    /** Stores at most 64 KiB of valid JSON, and returns false without an active Agent marker. */
    default boolean setAgentResult(String json) {
        var context = getAgentContext();
        return context != null && context.reportResult(json);
    }

    default String getAgentResult() {
        var context = getAgentContext();
        return context == null ? null : context.getResultJson();
    }

    abstract class AbstractScriptExecution implements ScriptExecution {

        private static final AtomicInteger sMaxId = new AtomicInteger(0);

        protected ScriptExecutionTask mScriptExecutionTask;
        protected int mId;
        private volatile boolean mFinished;

        public AbstractScriptExecution(ScriptExecutionTask task) {
            mScriptExecutionTask = task;
            mId = sMaxId.getAndIncrement();
        }

        @Override
        public boolean isFinished() {
            return mFinished;
        }

        public void markFinished() {
            mFinished = true;
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
