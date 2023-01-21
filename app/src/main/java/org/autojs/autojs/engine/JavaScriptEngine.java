package org.autojs.autojs.engine;

import static org.autojs.autojs.util.StringUtils.str;

import androidx.annotation.NonNull;

import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/8/3.
 */
public abstract class JavaScriptEngine extends ScriptEngine.AbstractScriptEngine<JavaScriptSource> {
    private ScriptRuntime mRuntime;
    private Object mExecArgv;

    @Override
    public Object execute(JavaScriptSource scriptSource) {
        if ((scriptSource.getExecutionMode() & JavaScriptSource.EXECUTION_MODE_AUTO) != 0) {
            getRuntime().ensureAccessibilityServiceEnabled();
        }
        return doExecution(scriptSource);
    }

    protected abstract Object doExecution(JavaScriptSource scriptSource);

    public ScriptRuntime getRuntime() {
        return mRuntime;
    }

    public void setRuntime(ScriptRuntime runtime) {
        if (mRuntime != null) {
            throw new IllegalStateException(str(R.string.error_a_runtime_has_been_set));
        }
        mRuntime = runtime;
        mRuntime.engines.setCurrentEngine(this);
        put("runtime", runtime);
    }

    public void emit(String eventName, Object... args) {
        mRuntime.timers.getMainTimer().postDelayed(() -> mRuntime.events.emit(eventName, args), 0);
    }

    public ScriptSource getSource() {
        return (ScriptSource) getTag(TAG_SOURCE);
    }

    public void setExecArgv(Object execArgv) {
        if (mExecArgv != null) {
            return;
        }
        mExecArgv = execArgv;
    }

    public Object getExecArgv() {
        return mExecArgv;
    }

    @Override
    public synchronized void destroy() {
        mRuntime.onExit();
        super.destroy();
    }

    @NonNull
    @Override
    public String toString() {
        return "ScriptEngine@" + Integer.toHexString(hashCode()) + "{" +
                "id=" + getId() + "," +
                "source='" + getTag(TAG_SOURCE) + "'," +
                "cwd='" + cwd() + "'" +
                "}";
    }
}
