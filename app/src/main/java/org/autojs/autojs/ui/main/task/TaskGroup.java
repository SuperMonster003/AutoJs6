package org.autojs.autojs.ui.main.task;

import static org.autojs.autojs.util.StringUtils.str;

import android.content.Context;

import com.bignerdranch.expandablerecyclerview.model.Parent;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.timing.IntentTask;
import org.autojs.autojs.timing.TimedTask;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs6.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Stardust on 2017/11/28.
 */
public abstract class TaskGroup implements Parent<Task> {

    protected List<Task> mTasks = new ArrayList<>();
    private final String mTitle;

    protected TaskGroup(String title) {
        mTitle = title;
    }

    @Override
    public List<Task> getChildList() {
        return mTasks;
    }

    @Override
    public boolean isInitiallyExpanded() {
        return true;
    }

    public String getTitle() {
        return mTitle;
    }

    public abstract Context getContext();

    public abstract void refresh();

    public static class PendingTaskGroup extends TaskGroup {

        private final Context mContext;

        public PendingTaskGroup(Context context) {
            super(context.getString(R.string.text_timed_task));
            mContext = context;
            refresh();
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public void refresh() {
            mTasks.clear();
            for (TimedTask timedTask : TimedTaskManager.getInstance().getAllTasksAsList()) {
                mTasks.add(new Task.PendingTask(getContext(), timedTask));
            }
            for (IntentTask intentTask : TimedTaskManager.getInstance().getAllIntentTasksAsList()) {
                mTasks.add(new Task.PendingTask(getContext(), intentTask));
            }
        }

        public int addTask(Object task) {
            int pos = mTasks.size();
            if (task instanceof TimedTask) {
                mTasks.add(new Task.PendingTask(getContext(), (TimedTask) task));
            } else if (task instanceof IntentTask) {
                mTasks.add(new Task.PendingTask(getContext(), (IntentTask) task));
            } else {
                throw new IllegalArgumentException(str(R.string.error_illegal_argument, "task", task));
            }
            return pos;
        }

        public int removeTask(Object data) {
            int i = indexOf(data);
            if (i >= 0)
                mTasks.remove(i);
            return i;
        }

        private int indexOf(Object data) {
            for (int i = 0; i < mTasks.size(); i++) {
                Task.PendingTask task = (Task.PendingTask) mTasks.get(i);
                if (task.taskEquals(data)) {
                    return i;
                }
            }
            return -1;
        }

        public int updateTask(Object task) {
            int i = indexOf(task);
            if (i >= 0) {
                if (task instanceof TimedTask) {
                    ((Task.PendingTask) mTasks.get(i)).setTimedTask((TimedTask) task);
                } else if (task instanceof IntentTask) {
                    ((Task.PendingTask) mTasks.get(i)).setIntentTask((IntentTask) task);
                } else {
                    throw new IllegalArgumentException(str(R.string.error_illegal_argument, "task", task));
                }
            }
            return i;
        }
    }

    public static class RunningTaskGroup extends TaskGroup {

        private final Context mContext;

        public RunningTaskGroup(Context context) {
            super(context.getString(R.string.text_running_task));
            mContext = context;
            refresh();
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public void refresh() {
            Collection<ScriptExecution> executions = AutoJs.getInstance().getScriptEngineService().getScriptExecutions();
            mTasks.clear();
            for (ScriptExecution execution : executions) {
                mTasks.add(new Task.RunningTask(getContext(), execution));
            }
        }

        public int addTask(ScriptExecution engine) {
            int pos = mTasks.size();
            mTasks.add(new Task.RunningTask(getContext(), engine));
            return pos;
        }

        public int removeTask(ScriptExecution engine) {
            int i = indexOf(engine);
            if (i >= 0) {
                mTasks.remove(i);
            }
            return i;
        }

        public int indexOf(ScriptExecution engine) {
            for (int i = 0; i < mTasks.size(); i++) {
                if (((Task.RunningTask) mTasks.get(i)).getScriptExecution().equals(engine)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
