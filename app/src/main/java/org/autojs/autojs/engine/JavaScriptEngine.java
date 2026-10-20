package org.autojs.autojs.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.tool.CrashHandlerTestFunction;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static org.autojs.autojs.util.StringUtils.str;

/**
 * Created by Stardust on Aug 3, 2017.
 */
public abstract class JavaScriptEngine extends ScriptEngine.AbstractScriptEngine<JavaScriptSource> {
    private ScriptRuntime mRuntime;
    private Object mExecArgv;

    @Override
    @Nullable
    public Object execute(@NotNull JavaScriptSource scriptSource) {
        if ((scriptSource.getExecutionMode() & JavaScriptSource.EXECUTION_MODE_AUTO) != 0) {
            mRuntime.accessibilityBridge.ensureServiceStarted();
        }
        if ((scriptSource.getExecutionMode() & JavaScriptSource.EXECUTION_MODE_JSOX) != 0) {
            // Roadmap P2.1: through the new script module layer when it serves jsox.
            mRuntime.extendJsBuiltIns();
        }
        return doExecution(scriptSource);
    }

    @Nullable
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
        put("crash", new CrashHandlerTestFunction());
    }

    public void emit(String eventName, Object... args) {
        mRuntime.timers.getMainTimer().postDelayed(() -> mRuntime.events.emit(eventName, args), 0);
    }

    public ScriptSource getSource() {
        return (ScriptSource) getTag(TAG_SOURCE);
    }

    @Nullable
    public String getSourcePath() {
        ScriptSource source = getSource();
        if (source == null) {
            return null;
        }

        String originPath = source.getOriginPath();
        if (originPath != null) {
            return originPath;
        }

        if (source.getOriginUri() != null) {
            return null;
        }

        if (source instanceof JavaScriptFileSource fileSource) {
            return fileSource.getFile().getAbsolutePath();
        }

        return null;
    }

    @Nullable
    public String getSourceUri() {
        ScriptSource source = getSource();
        return source == null ? null : source.getOriginUri();
    }

    @Nullable
    public String getBackingSourcePath() {
        ScriptSource source = getSource();
        if (source instanceof JavaScriptFileSource fileSource) {
            return fileSource.getFile().getAbsolutePath();
        }
        return null;
    }

    @Nullable
    public String getSourceDirectory() {
        String sourcePath = getSourcePath();
        if (sourcePath != null) {
            File parent = new File(sourcePath).getParentFile();
            if (parent != null) {
                return parent.getPath();
            }
        }
        return cwd();
    }

    public boolean hasFeature(String feature) {
        return getTag(ExecutionConfig.tag) instanceof ExecutionConfig tag && tag.getScriptConfig().hasFeature(feature);
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
