package org.autojs.autojs.ui.main.scripts

import android.app.Activity
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.android.schedulers.AndroidSchedulers
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.script.Scripts.edit
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.ui.main.FloatingActionMenu
import org.autojs.autojs.ui.main.FloatingActionMenu.OnFloatingActionButtonClickListener
import org.autojs.autojs.ui.main.QueryEvent
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.ViewStatesManageable
import org.autojs.autojs.ui.project.ProjectConfigActivity
import org.autojs.autojs.ui.project.ProjectConfigActivity_
import org.autojs.autojs.util.IntentUtils.viewFile
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by Stardust on 2017/3/13.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 * Transformed by SuperMonster003 on Mar 31, 2023.
 */
@EFragment(R.layout.fragment_my_script_list)
open class MyScriptListFragment : ViewPagerFragment(0), OnFloatingActionButtonClickListener, ViewStatesManageable {

    @JvmField
    @ViewById(R.id.script_file_list)
    var explorerView: ExplorerView? = null

    private var mFloatingActionMenu: FloatingActionMenu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    @AfterViews
    fun setUpViews() {
        restoreViewStates()
        explorerView?.apply {
            setOnItemClickListener { _, item: ExplorerItem ->
                if (item.isEditable) {
                    edit(requireActivity(), item.toScriptFile())
                } else {
                    viewFile(GlobalAppContext.get(), item.path, AppFileProvider.AUTHORITY)
                }
            }
        }
    }

    override fun onFabClick(fab: FloatingActionButton) {
        mFloatingActionMenu ?: let {
            mFloatingActionMenu = requireActivity().findViewById<FloatingActionMenu?>(R.id.floating_action_menu).also { menu ->
                menu.state
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : SimpleObserver<Boolean?>() {
                        override fun onNext(expanding: Boolean) {
                            fab.animate()
                                .rotation((if (expanding) 45 else 0).toFloat())
                                .setDuration(300)
                                .start()
                        }
                    })
                menu.setOnFloatingActionButtonClickListener(this)
            }
        }
        mFloatingActionMenu!!.let { if (it.isExpanded) it.collapse() else it.expand() }
    }

    override fun onBackPressed(activity: Activity): Boolean {
        mFloatingActionMenu?.let {
            if (it.isExpanded) {
                it.collapse()
                return@onBackPressed true
            }
        }
        explorerView?.let {
            if (it.canGoBack()) {
                it.goBack()
                return@onBackPressed true
            }
        }
        return false
    }

    override fun onPageHide() {
        super.onPageHide()
        mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
    }

    @Subscribe
    fun onQuerySummit(event: QueryEvent) {
        if (!isShown) {
            return
        }
        if (event === QueryEvent.CLEAR) {
            explorerView?.setFilter(null)
            return
        }
        explorerView?.setFilter { item: ExplorerItem -> item.name.contains(event.query) }
    }

    override fun onStop() {
        super.onStop()
        if (activity?.isFinishing == false) {
            saveViewStates()
        }
        explorerView?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        ExplorerView.clearViewStates()
        EventBus.getDefault().unregister(this)
    }

    override fun onDetach() {
        super.onDetach()
        mFloatingActionMenu?.setOnFloatingActionButtonClickListener(null)
    }

    override fun onClick(button: FloatingActionButton, pos: Int) {
        explorerView?.let { view ->
            when (pos) {
                0 -> ScriptOperations(context, view, view.currentPage)
                    .newDirectory()
                1 -> ScriptOperations(context, view, view.currentPage)
                    .newFile()
                2 -> ScriptOperations(context, view, view.currentPage)
                    .importFile()
                3 -> ProjectConfigActivity_.intent(context)
                    .extra(ProjectConfigActivity.EXTRA_PARENT_DIRECTORY, view.currentPage.path)
                    .extra(ProjectConfigActivity.EXTRA_NEW_PROJECT, true)
                    .start()
                else -> {}
            }
        }
    }

    override fun saveViewStates() {
        explorerView?.saveViewStates()
    }

    override fun restoreViewStates() {
        explorerView?.restoreViewStates()
    }

}