package org.autojs.autojs.ui.main.task;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import org.autojs.autojs.engine.NodeProjectRunner;
import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.AutoFileSource;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.LuaScriptSource;
import org.autojs.autojs.script.NodeProjectLaunchKind;
import org.autojs.autojs.script.PythonScriptSource;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs.script.ScriptSource;
import org.autojs.autojs.timing.IntentTask;
import org.autojs.autojs.timing.TimedTask;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.joda.time.format.DateTimeFormat;

import java.io.File;

/**
 * Created by Stardust on Nov 28, 2017.
 */
public abstract class Task {

    public abstract String getName();

    public abstract String getDesc();

    public abstract Context getContext();

    public abstract void cancel();

    public abstract String getEngineName();

    public static class PendingTask extends Task {

        private static final String LUA_FILE_SOURCE_KIND_V1 = "lua_file_v1";
        private static final String PYTHON_FILE_SOURCE_KIND_V1 = "python_file_v1";
        private static final String PYTHON_PROJECT_SOURCE_KIND_V1 = "python_project_v1";

        private final Context mContext;
        private TimedTask mTimedTask;
        private IntentTask mIntentTask;

        public PendingTask(Context context, TimedTask timedTask) {
            mContext = context;
            mTimedTask = timedTask;
            mIntentTask = null;
        }

        public PendingTask(Context context, IntentTask intentTask) {
            mContext = context;
            mIntentTask = intentTask;
            mTimedTask = null;
        }

        public boolean taskEquals(Object task) {
            if (mTimedTask != null) {
                return mTimedTask.equals(task);
            }
            return mIntentTask.equals(task);
        }

        public TimedTask getTimedTask() {
            return mTimedTask;
        }

        @Override
        public String getName() {
            return PFiles.getElegantPath(getScriptPath(), WorkingDirectoryUtils.getPath(), true);
        }

        @Override
        public String getDesc() {
            if (mTimedTask != null) {
                long nextTime = mTimedTask.getNextTime(mContext);
                return mContext.getString(R.string.text_next_run_time) + ": " +
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").print(nextTime);
            } else {
                assert mIntentTask != null;
                Integer desc = TimedTaskSettingActivity.ACTION_DESC_MAP.get(mIntentTask.getAction());
                if (desc != null) {
                    return mContext.getString(desc);
                }
                return mIntentTask.getAction();
            }

        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public void cancel() {
            if (mTimedTask != null) {
                TimedTaskManager.removeTask(mTimedTask);
            } else {
                TimedTaskManager.removeTask(mIntentTask);
            }
        }

        private String getScriptPath() {
            if (mTimedTask != null) {
                return mTimedTask.getScriptPath();
            } else {
                assert mIntentTask != null;
                return mIntentTask.getScriptPath();
            }
        }

        private String getSourceKind() {
            if (mTimedTask != null) {
                return mTimedTask.getSourceKind();
            }
            assert mIntentTask != null;
            return mIntentTask.getSourceKind();
        }

        private String getProjectRoot() {
            if (mTimedTask != null) {
                return mTimedTask.getProjectRoot();
            }
            assert mIntentTask != null;
            return mIntentTask.getProjectRoot();
        }

        @Override
        public String getEngineName() {
            String scriptPath = getScriptPath();
            try {
                return createLaunch().getEngineName();
            } catch (Exception ignored) {
                return resolveFallbackEngineName(scriptPath, getSourceKind(), getProjectRoot());
            }
        }

        @NonNull
        @VisibleForTesting
        static String resolveFallbackEngineName(
                @NonNull String scriptPath,
                @Nullable String sourceKind,
                @Nullable String projectRoot) {
            if (LUA_FILE_SOURCE_KIND_V1.equals(sourceKind)) {
                return LuaScriptSource.ENGINE;
            }
            if (PYTHON_FILE_SOURCE_KIND_V1.equals(sourceKind)
                    || PYTHON_PROJECT_SOURCE_KIND_V1.equals(sourceKind)
                    || projectRoot != null) {
                return PythonScriptSource.ENGINE;
            }
            if (LuaScriptSource.isLuaFileName(scriptPath)) {
                return LuaScriptSource.ENGINE;
            }
            if (!JavaScriptSource.isJavaScriptFileName(scriptPath)) {
                return AutoFileSource.ENGINE;
            }
            if (JavaScriptSource.isNodeJsFileName(scriptPath)) {
                return JavaScriptSource.ENGINE_NODEJS;
            }
            return JavaScriptSource.ENGINE;
        }

        @NonNull
        public ScriptLaunchSourceFactory.Launch createLaunch() {
            String parent = new File(getScriptPath()).getAbsoluteFile().getParent();
            return ScriptLaunchSourceFactory.createPersistedPathLaunch(
                    getScriptPath(), getSourceKind(), getProjectRoot(), null, null,
                    parent == null ? WorkingDirectoryUtils.getPath() : parent);
        }

        @NonNull
        public ExecutionConfig createLaunchExecutionConfig() {
            return createLaunchExecutionConfig(new ExecutionConfig());
        }

        @NonNull
        public ExecutionConfig createLaunchExecutionConfig(@NonNull ExecutionConfig base) {
            return createLaunchExecutionConfig(createLaunch(), base);
        }

        @NonNull
        public ExecutionConfig createLaunchExecutionConfig(
                @NonNull ScriptLaunchSourceFactory.Launch launch,
                @NonNull ExecutionConfig base) {
            return launch.createExecutionConfig(base);
        }

        @Nullable
        public NodeProjectRunner.ProjectRequest createNodeProjectRequestOrNull() {
            try {
                return createLaunch().buildNodeProjectRequestOrNull();
            } catch (Exception ignored) {
                return null;
            }
        }

        @VisibleForTesting
        public boolean isNodeProjectLaunch() {
            return createNodeProjectRequestOrNull() != null;
        }

        @NonNull
        @VisibleForTesting
        public String getNodeProjectLaunchKindDescriptor() {
            try {
                NodeProjectLaunchKind kind = createLaunch().getNodeProjectLaunchKind();
                return kind == null ? "" : kind.getDescriptorValue();
            } catch (Exception ignored) {
                return "";
            }
        }

        public void setTimedTask(TimedTask timedTask) {
            mTimedTask = timedTask;
        }

        public void setIntentTask(IntentTask intentTask) {
            mIntentTask = intentTask;
        }

        public long getId() {
            if (mTimedTask != null)
                return mTimedTask.getId();
            return mIntentTask.getId();
        }
    }

    public static class RunningTask extends Task {
        private final ScriptExecution mScriptExecution;
        private final Context mContext;

        public RunningTask(Context context, ScriptExecution scriptExecution) {
            mScriptExecution = scriptExecution;
            mContext = context;
        }

        public ScriptExecution getScriptExecution() {
            return mScriptExecution;
        }

        @Override
        public String getName() {
            return mScriptExecution.getSource().getName();
        }

        @Override
        public String getDesc() {
            return mScriptExecution.getSource().getElegantPath();
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public void cancel() {
            ScriptEngine<? extends ScriptSource> engine = mScriptExecution.getEngine();
            if (engine != null) {
                engine.forceStop();
            }
        }

        @Override
        public String getEngineName() {
            return mScriptExecution.getSource().getEngineName();
        }

    }

}
