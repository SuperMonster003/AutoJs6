package org.autojs.autojs.ui.main.task

import android.app.Activity
import android.os.Bundle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById
import org.autojs.autojs.AutoJs
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.ViewStatesManageable
import org.autojs.autojs.ui.widget.SimpleAdapterDataObserver
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/3/24.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Mar 31, 2023.
 */
@EFragment(R.layout.fragment_task_manager)
open class TaskManagerFragment : ViewPagerFragment(45), ViewStatesManageable {

    // private var mListState: Parcelable? = null

    @JvmField
    @ViewById(R.id.task_list)
    var mTaskListRecyclerView: TaskListRecyclerView? = null

    @JvmField
    @ViewById(R.id.swipe_refresh_layout)
    var mSwipeRefreshLayout: SwipeRefreshLayout? = null

    init {
        arguments = Bundle()
    }

    @AfterViews
    fun setUpViews() {
        mTaskListRecyclerView?.let { recyclerView ->
            recyclerView.adapter?.registerAdapterDataObserver(object : SimpleAdapterDataObserver() {
                override fun onSomethingChanged() {
                    // To do something here maybe some day.
                }
            })
            mSwipeRefreshLayout?.let { refreshLayout ->
                refreshLayout.setOnRefreshListener {
                    recyclerView.refresh()
                    recyclerView.postDelayed({ refreshLayout.isRefreshing = false }, 800)
                }
            }
        }
        restoreViewStates()
    }

    override fun onStop() {
        super.onStop()
        saveViewStates()
    }

    override fun onFabClick(fab: FloatingActionButton) {
        AutoJs.instance.scriptEngineService.stopAll()
    }

    override fun onBackPressed(activity: Activity) = false

    override fun saveViewStates() {
        // mTaskListRecyclerView?.layoutManager?.onSaveInstanceState().let { mListState = it }
    }

    override fun restoreViewStates() {
        // mListState?.let { mTaskListRecyclerView?.layoutManager?.onRestoreInstanceState(it) }
    }

}