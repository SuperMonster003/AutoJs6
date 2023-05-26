package org.autojs.autojs.ui.main.task;

import android.content.Context;

import org.autojs.autojs.engine.ScriptEngine;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.AutoFileSource;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.timing.IntentTask;
import org.autojs.autojs.timing.TimedTask;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.joda.time.format.DateTimeFormat;

/**
 * Created by Stardust on 2017/11/28.
 */
public abstract class Task {

    public abstract String getName();

    public abstract String getDesc();

    public abstract Context getContext();

    public abstract void cancel();

    public abstract String getEngineName();

    public static class PendingTask extends Task {

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
                        DateTimeFormat.forPattern("yyyy/MM/dd HH:mm").print(nextTime);
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

        @Override
        public String getEngineName() {
            if (getScriptPath().endsWith(".js")) {
                return JavaScriptSource.ENGINE;
            } else {
                return AutoFileSource.ENGINE;
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
            ScriptEngine engine = mScriptExecution.getEngine();
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
