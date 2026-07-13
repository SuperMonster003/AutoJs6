package org.autojs.autojs.ui.main.scripts

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginBottom
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.ui.fragment.BindingDelegates.viewBinding
import org.autojs.autojs.ui.main.FloatingActionMenu
import org.autojs.autojs.ui.main.FloatingActionMenu.OnFloatingActionButtonClickListener
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.QueryEvent
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.ViewStatesManageable
import org.autojs.autojs.ui.project.ProjectConfigActivity
import org.autojs.autojs.ui.widget.ScrollAwareFABBehavior
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.SnackExceptionHolder
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentExplorerBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by Stardust on Mar 13, 2017.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 * Transformed by SuperMonster003 on Mar 31, 2023.
 */
class ExplorerFragment : ViewPagerFragment(0), OnFloatingActionButtonClickListener, ViewStatesManageable {

    private val binding by viewBinding(FragmentExplorerBinding::bind)

    private var mExplorerView: ExplorerView? = null
    private var mFloatingActionMenu: FloatingActionMenu? = null
    private var mIsCurrentPageFiles = false
    private var mSelectionToolbarShown = false
    private var mSelectionToolbarFabOffset = 0
    private var mFabHiddenBySelectionToolbar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentExplorerBinding.inflate(inflater, container, false).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mExplorerView = binding.itemList.also { explorerView ->
            explorerView.setOnItemClickListener(object : ExplorerView.OnItemClickListener {
                override fun onItemClick(view: View?, item: ExplorerItem) {
                    when {
                        item.isTextEditable -> Scripts.edit(requireActivity(), item.toScriptFile())
                        item.isInstallable -> ApkInfoDialogManager.showApkInfoDialog(requireActivity(), item.toScriptFile())
                        item.isMediaMenu || item.isMediaPlayable -> MediaInfoDialogManager.showMediaInfoDialog(requireActivity(), item)
                        else -> viewFile(item)
                    }
                }
            })
            explorerView.excludeListPaddingFromBottomNavigationBar()
            explorerView.setOnSelectionToolbarStateChangeListener(object : ExplorerView.OnSelectionToolbarStateChangeListener {
                override fun onSelectionToolbarStateChanged(shown: Boolean, fabOffset: Int) {
                    handleSelectionToolbarStateChanged(shown, fabOffset)
                }
            })
        }
        (activity as? MainActivity)?.apply {
            val tabLayout: TabLayout = findViewById(R.id.tab)
            val filesTab = tabLayout.getTabAt(filesItemIndex)
            filesTab?.view?.let { setTabViewClickListeners(it) }
        }
        restoreViewStates()
    }

    private fun setTabViewClickListeners(tabView: TabLayout.TabView) {
        tabView.setOnLongClickListener { if (mIsCurrentPageFiles) true.also { toggleFabVisibility() } else false }
    }

    private fun toggleFabVisibility() {
        val behavior = (fab.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ScrollAwareFABBehavior

        if (mSelectionToolbarShown) {
            if (fab.isShown && fab.translationY <= 0f) {
                mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
                fab.hide()
            } else {
                showFabAboveSelectionToolbar(behavior)
            }
            return
        }

        when {
            behavior == null || fab.translationY == 0f -> {
                if (fab.isShown) fab.hide() else fab.show()
            }
            else -> fab.animate()
                .translationY(0f)
                .setDuration(ScrollAwareFABBehavior.DURATION)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        behavior.setHidden(false)
                    }
                })
                .start()
        }
    }

    private fun handleSelectionToolbarStateChanged(shown: Boolean, fabOffset: Int) {
        val wasShown = mSelectionToolbarShown
        mSelectionToolbarShown = shown
        mSelectionToolbarFabOffset = fabOffset

        if (shown) {
            mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
            when {
                !wasShown && isFabVisiblyShown() -> {
                    mFabHiddenBySelectionToolbar = true
                    fab.hide()
                }
                !mFabHiddenBySelectionToolbar && fab.isShown && fab.translationY <= 0f -> {
                    applySelectionToolbarFabPosition()
                }
            }
            return
        }

        restoreFabAfterSelectionToolbar()
    }

    private fun showFabAboveSelectionToolbar(behavior: ScrollAwareFABBehavior?) {
        mFabHiddenBySelectionToolbar = false
        fab.animate().cancel()
        fab.translationY = selectionToolbarFabTranslationY()
        behavior?.setHidden(false)
        applyFloatingActionMenuSelectionTranslation()
        if (!fab.isShown || fab.visibility != View.VISIBLE) {
            fab.show()
        }
    }

    private fun applySelectionToolbarFabPosition() {
        fab.animate()
            .translationY(selectionToolbarFabTranslationY())
            .setDuration(ScrollAwareFABBehavior.DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mFloatingActionMenu?.let { updateFloatingActionMenuToggleFabLocation(it) }
                }
            })
            .start()
        applyFloatingActionMenuSelectionTranslation()
    }

    private fun restoreFabAfterSelectionToolbar() {
        mFloatingActionMenu?.let { menu ->
            menu.translationY = 0f
        }
        val behavior = (fab.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ScrollAwareFABBehavior
        if (mFabHiddenBySelectionToolbar) {
            mFabHiddenBySelectionToolbar = false
            fab.animate().cancel()
            fab.translationY = 0f
            behavior?.setHidden(false)
            mFloatingActionMenu?.let { updateFloatingActionMenuToggleFabLocation(it) }
            fab.show()
            return
        }
        if (fab.isShown && fab.translationY < 0f) {
            fab.animate()
                .translationY(0f)
                .setDuration(ScrollAwareFABBehavior.DURATION)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        behavior?.setHidden(false)
                        mFloatingActionMenu?.let { updateFloatingActionMenuToggleFabLocation(it) }
                    }
                })
                .start()
        }
    }

    private fun applyFloatingActionMenuSelectionTranslation() {
        mFloatingActionMenu?.let { menu ->
            menu.translationY = selectionToolbarFabTranslationY()
            updateFloatingActionMenuToggleFabLocation(menu)
        }
    }

    private fun updateFloatingActionMenuToggleFabLocation(menu: FloatingActionMenu) {
        val locFab = IntArray(2)
        fab.getLocationOnScreen(locFab)
        menu.setToggleFab(fab, locFab)
    }

    private fun selectionToolbarFabTranslationY() = -mSelectionToolbarFabOffset.toFloat()

    private fun isFabVisiblyShown(): Boolean {
        return fab.isShown && fab.visibility == View.VISIBLE && fab.translationY <= 0f
    }

    private fun viewFile(item: ExplorerItem): Boolean {
        val context = context ?: GlobalAppContext.get()
        val exceptionHolder = view?.let { SnackExceptionHolder(it) } ?: ToastExceptionHolder(context)
        return IntentUtils.viewFile(
            context = context,
            path = item.path,
            mimeType = null,
            fileProviderAuthority = AppFileProvider.AUTHORITY,
            exceptionHolder = exceptionHolder,
        )
    }

    override fun onFabClick(fab: FloatingActionButton) {
        initFloatingActionMenuIfNeeded(fab).run { if (isExpanded) collapse() else expand() }
    }

    private fun initFloatingActionMenuIfNeeded(fab: FloatingActionButton): FloatingActionMenu {
        return mFloatingActionMenu ?: requireActivity().findViewById<FloatingActionMenu>(R.id.floating_action_menu).also { menu ->
            val locFab = IntArray(2)
            fab.getLocationOnScreen(locFab)
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
            menu.layoutParams.runCatching {
                javaClass.getField("bottomMargin").setInt(this, fab.marginBottom)
            }
            menu.setToggleFab(fab, locFab)
            if (mSelectionToolbarShown) {
                menu.translationY = selectionToolbarFabTranslationY()
            }
            mFloatingActionMenu = menu
        }
    }

    override fun onBackPressed(activity: Activity): Boolean {
        mFloatingActionMenu?.let {
            if (it.isExpanded) {
                it.collapse()
                return@onBackPressed true
            }
        }
        mExplorerView?.let {
            if (it.canGoBack()) {
                it.goBack()
                return@onBackPressed true
            }
        }
        return false
    }

    override fun onPageShow() {
        super.onPageShow()
        mIsCurrentPageFiles = true
        if (mSelectionToolbarShown && isFabVisiblyShown()) {
            mFabHiddenBySelectionToolbar = true
            fab.hide()
        }
    }

    override fun onPageHide() {
        super.onPageHide()
        mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
        mIsCurrentPageFiles = false
    }

    @Subscribe
    fun onQuerySummit(event: QueryEvent) {
        if (!isShown) {
            return
        }
        if (event === QueryEvent.CLEAR) {
            mExplorerView?.setFilter(null)
            return
        }
        if (event === QueryEvent.FIND_FORWARD) {
            return
        }
        if (event === QueryEvent.FIND_BACKWARD) {
            return
        }
        mExplorerView?.setFilter { item: ExplorerItem -> item.name.contains(event.query, true) }
    }

    override fun onStop() {
        super.onStop()
        if (activity?.isFinishing == false) {
            saveViewStates()
        }
        mExplorerView?.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        ExplorerView.clearViewStates()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mExplorerView?.setOnSelectionToolbarStateChangeListener(null)
        restoreFabAfterSelectionToolbar()
        mExplorerView = null
        mFloatingActionMenu = null
    }

    override fun onDetach() {
        super.onDetach()
        mFloatingActionMenu?.setOnFloatingActionButtonClickListener(null)
    }

    override fun onClick(button: FloatingActionButton, pos: Int) {
        mExplorerView?.let { view ->
            val ctx = context ?: return@let
            when (pos) {
                3 -> Intent(ctx, ProjectConfigActivity::class.java)
                    .putExtra(ProjectConfigActivity.EXTRA_PARENT_DIRECTORY, view.currentPage.path)
                    .putExtra(ProjectConfigActivity.EXTRA_NEW_PROJECT, true)
                    .startSafely(ctx)
                2 -> ScriptOperations(ctx, view, view.currentPage)
                    .importFile()
                1 -> ScriptOperations(ctx, view, view.currentPage)
                    .newFile()
                0 -> ScriptOperations(ctx, view, view.currentPage)
                    .newDirectory()
                else -> Unit
            }
        }
    }

    override fun saveViewStates() {
        mExplorerView?.saveViewStates()
    }

    override fun restoreViewStates() {
        mExplorerView?.restoreViewStates()
    }

}
