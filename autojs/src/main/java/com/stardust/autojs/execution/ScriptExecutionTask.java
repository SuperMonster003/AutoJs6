package com.stardust.autojs.execution;

import com.stardust.autojs.script.ScriptSource;

import java.io.Serializable;

/**
 * Created by Stardust on 2017/4/2.
 */
public class ScriptExecutionTask implements Serializable {

    private final ScriptSource mScriptSource;
    private ScriptExecutionListener mExecutionListener;
    private final ExecutionConfig mExecutionConfig;

    public ScriptExecutionTask(ScriptSource source, ScriptExecutionListener listener, ExecutionConfig config) {
        mScriptSource = source;
        mExecutionListener = listener;
        mExecutionConfig = config;
    }

    public ScriptSource getSource() {
        return mScriptSource;
    }

    public ScriptExecutionListener getListener() {
        return mExecutionListener;
    }

    public ExecutionConfig getConfig() {
        return mExecutionConfig;
    }

    public void setExecutionListener(ScriptExecutionListener executionListener) {
        mExecutionListener = executionListener;
    }
}
