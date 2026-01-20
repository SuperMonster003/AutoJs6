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
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.storage.database.ModelChange;
import org.autojs.autojs.theme.ThemeColorManagerCompat;
import org.autojs.autojs.timing.TimedTaskManager;
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.FileUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding;
import org.autojs.autojs6.databinding.TaskListRecyclerViewItemBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Mar 24, 2017.
 * Modified by SuperMonster003 as of Jan 7, 2026.
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
                    final Adapter adapter = mAdapter;
                    post(() -> {
                        if (adapter != mAdapter) {
                            return;
                        }
                        adapter.notifyChildInserted(0, i);
                        notifyParentChangedSafe(adapter, 0);
                    });
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
            final Adapter adapter = mAdapter;
            post(() -> {
                if (adapter != mAdapter) {
                    return;
                }
                final int i = mRunningTaskGroup.removeTask(execution);
                if (i >= 0) {
                    adapter.notifyChildRemoved(0, i);
                    notifyParentChangedSafe(adapter, 0);
                } else {
                    refresh();
                }
            });
        }
    };

    private void notifyParentChangedSafe(@NonNull Adapter adapter, int parentPosition) {
        // Avoid notifying an adapter that has been replaced asynchronously.
        // zh-CN: 避免对已经被异步替换的适配器发送通知.
        if (adapter != mAdapter) {
            return;
        }

        // Avoid out-of-range parentPosition caused by transient state mismatch.
        // zh-CN: 避免由于瞬时状态不一致导致的 parentPosition 越界.
        if (parentPosition < 0 || parentPosition >= mTaskGroups.size()) {
            return;
        }

        // RecyclerView may be computing layout/dispatching updates; postpone to next loop.
        // zh-CN: RecyclerView 可能正在计算布局/分发更新, 将通知延迟到下一次消息循环.
        if (isComputingLayout()) {
            post(() -> notifyParentChangedSafe(adapter, parentPosition));
            return;
        }

        try {
            adapter.notifyParentChanged(parentPosition);
        } catch (IndexOutOfBoundsException e) {
            /* Ignored. */
        }
    }

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
        final Adapter adapter = mAdapter;
        if (taskChange.getAction() == ModelChange.INSERT) {
            adapter.notifyChildInserted(1, mPendingTaskGroup.addTask(taskChange.getData()));
            notifyParentChangedSafe(adapter, 1);
        } else if (taskChange.getAction() == ModelChange.DELETE) {
            final int i = mPendingTaskGroup.removeTask(taskChange.getData());
            if (i >= 0) {
                adapter.notifyChildRemoved(1, i);
                notifyParentChangedSafe(adapter, 1);
            } else {
                Log.w(LOG_TAG, "data inconsistent on change: " + taskChange);
                refresh();
            }
        } else if (taskChange.getAction() == ModelChange.UPDATE) {
            final int i = mPendingTaskGroup.updateTask(taskChange.getData());
            if (i >= 0) {
                adapter.notifyChildChanged(1, i);
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
            int itemIconThemeColorForContrast = ColorUtils.adjustColorForContrast(getContext().getColor(R.color.window_background), ThemeColorManagerCompat.getColorPrimary(), 2.3);
            FileUtils.TYPE.Icon icon;
            if (JavaScriptFileSource.ENGINE.equals(mTask.getEngineName())) {
                icon = FileUtils.TYPE.JAVASCRIPT.icon;
            } else if (AutoFileSource.ENGINE.equals(mTask.getEngineName())) {
                icon = FileUtils.TYPE.AUTO.icon;
            } else {
                icon = FileUtils.TYPE.UNKNOWN.icon;
            }
            firstCharIconBinding.firstChar
                    .setIcon(icon)
                    .setIconTextColor(itemIconThemeColorForContrast)
                    .setStrokeColor(itemIconThemeColorForContrast)
                    .setFillTransparent();
            itemBinding.name.setText(task.getName());
            itemBinding.taskListFilePath.setText(task.getDesc());
            itemBinding.stop.setOnClickListener(view -> {
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
                IntentUtils.startSafely(intent, getContext());
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
            itemView.setOnClickListener(view -> {
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