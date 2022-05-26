package org.autojs.autojs.ui.main.task;

import android.app.Activity;
import android.os.Bundle;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs6.R;
import org.autojs.autojs.autojs.AutoJs;
import org.autojs.autojs.ui.main.ViewPagerFragment;
import org.autojs.autojs.ui.widget.SimpleAdapterDataObserver;

import java.util.Objects;

/**
 * Created by Stardust on 2017/3/24.
 */
@EFragment(R.layout.fragment_task_manager)
public class TaskManagerFragment extends ViewPagerFragment {

    @ViewById(R.id.task_list)
    TaskListRecyclerView mTaskListRecyclerView;

    @ViewById(R.id.swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;

    public TaskManagerFragment() {
        super(45);
        setArguments(new Bundle());
    }

    @AfterViews
    void setUpViews() {
        init();
    }

    private void init() {
        Objects.requireNonNull(mTaskListRecyclerView.getAdapter()).registerAdapterDataObserver(new SimpleAdapterDataObserver() {
            @Override
            public void onSomethingChanged() {
                // To do something here maybe some day.
            }
        });
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            mTaskListRecyclerView.refresh();
            mTaskListRecyclerView.postDelayed(() -> {
                if (mSwipeRefreshLayout != null)
                    mSwipeRefreshLayout.setRefreshing(false);
            }, 800);
        });
    }

    @Override
    protected void onFabClick(FloatingActionButton fab) {
        AutoJs.getInstance().getScriptEngineService().stopAll();
    }

    @Override
    public boolean onBackPressed(Activity activity) {
        return false;
    }
}
