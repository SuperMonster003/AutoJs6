package org.autojs.autojs.execution;

import java.io.Serializable;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public interface ScriptExecutionListener extends Serializable {

    void onStart(ScriptExecution execution);

    void onSuccess(ScriptExecution execution, Object result);

    void onException(ScriptExecution execution, Throwable e);
}
