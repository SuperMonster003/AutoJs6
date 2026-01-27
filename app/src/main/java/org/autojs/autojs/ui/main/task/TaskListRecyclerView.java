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
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Created by Stardust on Mar 24, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 27, 2026.
 * Modified by SuperMonster003 as of Jan 27, 2026.
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
                final Adapter adapter = mAdapter;
                post(() -> {
                    // Constrain list mutation to main thread to avoid ConcurrentModificationException in adapter iteration.
                    // zh-CN: 将列表修改约束在主线程执行, 避免适配器迭代时触发 ConcurrentModificationException.

                    if (adapter != mAdapter) {
                        return;
                    }

                    int insertedPos = mRunningTaskGroup.addTask(execution);
                    if (insertedPos == -1) {
                        return;
                    }

                    // Recompute childPosition at execution time to avoid stale index caused by frequent mutations.
                    // zh-CN: 在执行通知时重新计算 childPosition, 避免频繁变动导致的索引过期.
                    int childPosition = mRunningTaskGroup.indexOf(execution);
                    if (childPosition < 0) {
                        // Task disappeared before UI update; fallback to refresh to recover consistency.
                        // zh-CN: UI 更新前任务已消失; 回退到 refresh 以恢复一致性.
                        refresh();
                        return;
                    }

                    notifyChildInsertedSafe(adapter, 0, childPosition);
                    notifyParentChangedSafe(adapter, 0);
                });
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
                    notifyChildRemovedSafe(adapter, 0, i);
                    notifyParentChangedSafe(adapter, 0);
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

    private void onTaskChange(ModelChange<?> taskChange) {
        final Adapter adapter = mAdapter;
        if (taskChange.getAction() == ModelChange.INSERT) {
            notifyChildInsertedSafe(adapter, 1, mPendingTaskGroup.addTask(taskChange.getData()));
            notifyParentChangedSafe(adapter, 1);
        } else if (taskChange.getAction() == ModelChange.DELETE) {
            final int i = mPendingTaskGroup.removeTask(taskChange.getData());
            if (i >= 0) {
                notifyChildRemovedSafe(adapter, 1, i);
                notifyParentChangedSafe(adapter, 1);
            } else {
                Log.w(LOG_TAG, "data inconsistent on change: " + taskChange);
                refresh();
            }
        } else if (taskChange.getAction() == ModelChange.UPDATE) {
            final int i = mPendingTaskGroup.updateTask(taskChange.getData());
            if (i >= 0) {
                notifyChildChangedSafe(adapter, 1, i);
            } else {
                refresh();
            }
        }
    }

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

    private void notifyChildInsertedSafe(@NonNull Adapter adapter, int parentPosition, int childPosition) {
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

        // Avoid negative childPosition caused by transient state mismatch.
        // zh-CN: 避免由于瞬时状态不一致导致的 childPosition 为负数.
        if (childPosition < 0) {
            return;
        }

        // RecyclerView may be computing layout/dispatching updates; postpone to next loop.
        // zh-CN: RecyclerView 可能正在计算布局/分发更新, 将通知延迟到下一次消息循环.
        if (isComputingLayout()) {
            post(() -> notifyChildInsertedSafe(adapter, parentPosition, childPosition));
            return;
        }

        try {
            adapter.notifyChildInserted(parentPosition, childPosition);
        } catch (IndexOutOfBoundsException e) {
            // Self-heal on transient inconsistency to avoid crashes during frequent list mutations.
            // zh-CN: 在频繁列表变动导致的瞬时不一致场景下自愈, 避免崩溃.
            Log.w(LOG_TAG, "notifyChildInsertedSafe: inconsistent state, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
        } catch (ConcurrentModificationException e) {
            // Self-heal when child list is modified during adapter iteration.
            // zh-CN: 当适配器迭代 child 列表期间发生修改时自愈.
            Log.w(LOG_TAG, "notifyChildInsertedSafe: CME, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
        }
    }

    private void notifyChildRemovedSafe(@NonNull Adapter adapter, int parentPosition, int childPosition) {
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

        // Avoid negative childPosition caused by transient state mismatch.
        // zh-CN: 避免由于瞬时状态不一致导致的 childPosition 为负数.
        if (childPosition < 0) {
            return;
        }

        // RecyclerView may be computing layout/dispatching updates; postpone to next loop.
        // zh-CN: RecyclerView 可能正在计算布局/分发更新, 将通知延迟到下一次消息循环.
        if (isComputingLayout()) {
            post(() -> notifyChildRemovedSafe(adapter, parentPosition, childPosition));
            return;
        }

        try {
            adapter.notifyChildRemoved(parentPosition, childPosition);
        } catch (IndexOutOfBoundsException e) {
            // Self-heal on transient inconsistency to avoid crashes during frequent list mutations.
            // zh-CN: 在频繁列表变动导致的瞬时不一致场景下自愈, 避免崩溃.
            Log.w(LOG_TAG, "notifyChildRemovedSafe: inconsistent state, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
        } catch (ConcurrentModificationException e) {
            // Self-heal when child list is modified during adapter iteration.
            // zh-CN: 当适配器迭代 child 列表期间发生修改时自愈.
            Log.w(LOG_TAG, "notifyChildRemovedSafe: CME, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
        }
    }

    private void notifyChildChangedSafe(@NonNull Adapter adapter, int parentPosition, int childPosition) {
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

        // Avoid negative childPosition caused by transient state mismatch.
        // zh-CN: 避免由于瞬时状态不一致导致的 childPosition 为负数.
        if (childPosition < 0) {
            return;
        }

        // RecyclerView may be computing layout/dispatching updates; postpone to next loop.
        // zh-CN: RecyclerView 可能正在计算布局/分发更新, 将通知延迟到下一次消息循环.
        if (isComputingLayout()) {
            post(() -> notifyChildChangedSafe(adapter, parentPosition, childPosition));
            return;
        }

        try {
            adapter.notifyChildChanged(parentPosition, childPosition);
        } catch (IndexOutOfBoundsException e) {
            // Self-heal on transient inconsistency to avoid crashes during frequent list mutations.
            // zh-CN: 在频繁列表变动导致的瞬时不一致场景下自愈, 避免崩溃.
            Log.w(LOG_TAG, "notifyChildChangedSafe: inconsistent state, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
        } catch (ConcurrentModificationException e) {
            // Self-heal when child list is modified during adapter iteration.
            // zh-CN: 当适配器迭代 child 列表期间发生修改时自愈.
            Log.w(LOG_TAG, "notifyChildChangedSafe: CME, fallback to refresh(). parentPosition="
                           + parentPosition + ", childPosition=" + childPosition, e);
            refresh();
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

    private class TaskViewHolder extends ChildViewHolder<Task> {

        @NonNull
        private final ExplorerFirstCharIconBinding firstCharIconBinding;

        @NonNull
        private final TaskListRecyclerViewItemBinding itemBinding;
        private final int mItemIconThemeColorForContrast;

        private Task mTask;

        TaskViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this::onItemClick);
            itemBinding = TaskListRecyclerViewItemBinding.bind(itemView);
            firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView);
            mItemIconThemeColorForContrast = ColorUtils.adjustColorForContrast(
                    getContext().getColor(R.color.window_background),
                    ThemeColorManagerCompat.getColorPrimary(), 2.3);
        }

        public void bind(Task task) {
            mTask = task;
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
                    .setIconTextColor(mItemIconThemeColorForContrast)
                    .setStrokeColor(mItemIconThemeColorForContrast)
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