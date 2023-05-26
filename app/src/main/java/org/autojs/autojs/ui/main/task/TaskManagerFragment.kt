package org.autojs.autojs.ui.main.task

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.autojs.autojs.AutoJs
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.ViewStatesManageable
import org.autojs.autojs.ui.widget.SimpleAdapterDataObserver
import org.autojs.autojs6.databinding.FragmentTaskManagerBinding

/**
 * Created by Stardust on 2017/3/24.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Mar 31, 2023.
 */
open class TaskManagerFragment : ViewPagerFragment(45), ViewStatesManageable {

    private lateinit var binding: FragmentTaskManagerBinding

    private var mTaskListRecyclerView: TaskListRecyclerView? = null
    private var mSwipeRefreshLayout: SwipeRefreshLayout? = null

    init {
        arguments = Bundle()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentTaskManagerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mSwipeRefreshLayout = binding.swipeRefreshLayout
        mTaskListRecyclerView = binding.taskList.also { recyclerView ->
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