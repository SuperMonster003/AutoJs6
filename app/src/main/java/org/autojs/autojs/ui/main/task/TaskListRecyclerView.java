package org.autojs.autojs.ui.main.task;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ThemeColorRecyclerView;
import com.bignerdranch.expandablerecyclerview.ChildViewHolder;
import com.bignerdranch.expandablerecyclerview.ExpandableRecyclerAdapter;
import com.bignerdranch.expandablerecyclerview.ParentViewHolder;
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.execution.ScriptExecutionListener;
import org.autojs.autojs.execution.SimpleScriptExecutionListener;
import org.autojs.autojs.groundwork.WrapContentLinearLayoutManager;
import org.autojs.autojs.script.AutoFileSource;
import org.autojs.autojs.storage.database.ModelChange;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding;
import org.autojs.autojs6.databinding.TaskListRecyclerViewItemBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Mar 24, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
public class TaskListRecyclerView extends ThemeColorRecyclerView {

    private static final String LOG_TAG = "TaskListRecyclerView";

    private final List<TaskGroup> mTaskGroups = new ArrayList<>();
    private TaskGroup.RunningTaskGroup mRunningTaskGroup;
    private TaskGroup.PendingTaskGroup mPendingTaskGroup;
    private Adapter mAdapter;
    private Disposable mTimedTaskChangeDisposable;
    private Disposable mIntentTaskChangeDisposable;
    private final ScriptExecutionListener mScriptExecutionListener = new SimpleScriptExecutionListener() {
        @Override
        public void onStart(final ScriptExecution execution) {
            try {
                int i = mRunningTaskGroup.addTask(execution);
                if (i != -1) {
                    post(() -> mAdapter.notifyChildInserted(0, i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSuccess(ScriptExecution execution, Object result) {
            onFinish(execution);
        }

        @Override
        public void onException(ScriptExecution execution, Throwable e) {
            onFinish(execution);
        }

        private void onFinish(ScriptExecution execution) {
            post(() -> {
                final int i = mRunningTaskGroup.removeTask(execution);
                if (i >= 0) {
                    mAdapter.notifyChildRemoved(0, i);
                } else {
                    refresh();
                }
            });
        }
    };

    public TaskListRecyclerView(Context context) {
        super(context);
        init();
    }

    public TaskListRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaskListRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setLayoutManager(new WrapContentLinearLayoutManager(getContext()));

        addItemDecoration(new HorizontalDividerItemDecoration.Builder(getContext())
                .color(ContextCompat.getColor(getContext(), R.color.divider))
                .size(2)
                .marginResId(R.dimen.script_and_folder_list_divider_left_margin, R.dimen.script_and_folder_list_divider_right_margin)
                .showLastDivider()
                .build());

        mRunningTaskGroup = new TaskGroup.RunningTaskGroup(getContext());
        mTaskGroups.add(mRunningTaskGroup);

        mPendingTaskGroup = new TaskGroup.PendingTaskGroup(getContext());
        mTaskGroups.add(mPendingTaskGroup);

        mAdapter = new Adapter(mTaskGroups);
        setAdapter(mAdapter);
    }

    public void refresh() {
        mAdapter = new Adapter(mTaskGroups);
        setAdapter(mAdapter);
        for (TaskGroup group : mTaskGroups) {
            group.refresh();
        }
        // notifyDataSetChanged doesn't work?
        // mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mTimedTaskChangeDisposable = TimedTaskManager.getTimeTaskChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onTaskChange);

        mIntentTaskChangeDisposable = TimedTaskManager.getIntentTaskChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onTaskChange);

        AutoJs.getInstance().getScriptEngineService().registerGlobalScriptExecutionListener(mScriptExecutionListener);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            refresh();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AutoJs.getInstance().getScriptEngineService().unregisterGlobalScriptExecutionListener(mScriptExecutionListener);
        mTimedTaskChangeDisposable.dispose();
        mIntentTaskChangeDisposable.dispose();
    }

    void onTaskChange(ModelChange<?> taskChange) {
        if (taskChange.getAction() == ModelChange.INSERT) {
            mAdapter.notifyChildInserted(1, mPendingTaskGroup.addTask(taskChange.getData()));
        } else if (taskChange.getAction() == ModelChange.DELETE) {
            final int i = mPendingTaskGroup.removeTask(taskChange.getData());
            if (i >= 0) {
                mAdapter.notifyChildRemoved(1, i);
            } else {
                Log.w(LOG_TAG, "data inconsistent on change: " + taskChange);
                refresh();
            }
        } else if (taskChange.getAction() == ModelChange.UPDATE) {
            final int i = mPendingTaskGroup.updateTask(taskChange.getData());
            if (i >= 0) {
                mAdapter.notifyChildChanged(1, i);
            } else {
                refresh();
            }
        }
    }

    private class Adapter extends ExpandableRecyclerAdapter<TaskGroup, Task, TaskGroupViewHolder, TaskViewHolder> {

        public Adapter(@NonNull List<TaskGroup> parentList) {
            super(parentList);
        }

        @NonNull
        @Override
        public TaskGroupViewHolder onCreateParentViewHolder(@NonNull ViewGroup parentViewGroup, int viewType) {
            return new TaskGroupViewHolder(LayoutInflater.from(parentViewGroup.getContext())
                    .inflate(R.layout.dialog_code_generate_option_group, parentViewGroup, false));
        }

        @NonNull
        @Override
        public TaskViewHolder onCreateChildViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new TaskViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.task_list_recycler_view_item, parent, false));
        }

        @Override
        public void onBindParentViewHolder(@NonNull TaskGroupViewHolder viewHolder, int parentPosition, @NonNull TaskGroup taskGroup) {
            viewHolder.title.setText(taskGroup.getTitle());
            viewHolder.title.setTextColor(getContext().getColor(R.color.day_night));
        }

        @Override
        public void onBindChildViewHolder(@NonNull TaskViewHolder viewHolder, int parentPosition, int childPosition, @NonNull Task task) {
            viewHolder.bind(task);
        }
    }

    class TaskViewHolder extends ChildViewHolder<Task> {

        @NonNull
        private final ExplorerFirstCharIconBinding firstCharIconBinding;

        @NonNull
        private final TaskListRecyclerViewItemBinding itemBinding;

        private Task mTask;

        TaskViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this::onItemClick);
            itemBinding = TaskListRecyclerViewItemBinding.bind(itemView);
            firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView);
        }

        public void bind(Task task) {
            mTask = task;
            firstCharIconBinding.firstChar
                    .setIcon(AutoFileSource.ENGINE.equals(mTask.getEngineName()) ? FileUtils.TYPE.AUTO.icon : FileUtils.TYPE.JAVASCRIPT.icon)
                    // .setIconTextThemeColor()
                    // .setStrokeThemeColor()
                    .setFillTransparent();
            itemBinding.name.setText(task.getName());
            itemBinding.taskListFilePath.setText(task.getDesc());
            itemBinding.stop.setOnClickListener(_ -> {
                if (mTask != null) {
                    mTask.cancel();
                }
            });
        }

        void onItemClick(View view) {
            if (mTask instanceof Task.PendingTask task) {
                String extra = task.getTimedTask() == null
                        ? TimedTaskSettingActivity.EXTRA_INTENT_TASK_ID
                        : TimedTaskSettingActivity.EXTRA_TASK_ID;
                Intent intent = new Intent(getContext(), TimedTaskSettingActivity.class)
                        .putExtra(extra, task.getId());
                getContext().startActivity(intent);
            }
        }
    }

    private static class TaskGroupViewHolder extends ParentViewHolder<TaskGroup, Task> {

        TextView title;
        ImageView icon;

        TaskGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            icon = itemView.findViewById(R.id.icon);
            itemView.setOnClickListener(_ -> {
                if (isExpanded()) {
                    collapseView();
                } else {
                    expandView();
                }
            });
        }

        @Override
        public void onExpansionToggled(boolean expanded) {
            icon.setRotation(expanded ? -90 : 0);
        }

    }

}