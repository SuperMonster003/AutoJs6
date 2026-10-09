package org.autojs.autojs.ui.main.plugin

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginBottom
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginCenterFragment
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginInstallActions
import org.autojs.autojs.core.plugin.center.PluginInstaller
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardDialog
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.QueryEvent
import org.autojs.autojs.ui.main.ViewPagerFragment
import org.autojs.autojs.ui.main.plugin.PluginFloatingActionMenu.OnFloatingActionButtonClickListener
import org.autojs.autojs.ui.widget.ScrollAwareFABBehavior
import org.autojs.autojs6.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Created by SuperMonster003 on Jan 17, 2026.
 */
class PluginFragment : ViewPagerFragment(0), OnFloatingActionButtonClickListener {

    private val mPickApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            PluginInstaller.installFromFileUriWithPrompt(requireContext(), uri)
        }
    }

    private var mFloatingActionMenu: PluginFloatingActionMenu? = null
    private var mIsCurrentPagePlugins = false
    private var mPluginCenterInitJob: Job? = null
    private var mPluginCenterCreationRequested = false
    private var mNightModeRefreshJob: Job? = null

    private val mNightModePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!isAdded || view == null) return@OnSharedPreferenceChangeListener
        val context = requireContext()
        val nightModeKeys = setOf(
            context.getString(R.string.key_night_mode),
            context.getString(R.string.key_night_mode_enabled),
            context.getString(R.string.key_auto_night_mode_enabled),
        )
        if (key in nightModeKeys) {
            schedulePluginCenterNightModeRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_plugin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.apply {
            val tabLayout: TabLayout = findViewById(R.id.tab)
            val pluginsTag = tabLayout.getTabAt(pluginsIndex)
            pluginsTag?.view?.let { setTabViewClickListeners(it) }
        }

        if (mIsCurrentPagePlugins) {
            schedulePluginCenterInitialization()
        }
    }

    private fun setTabViewClickListeners(tabView: TabLayout.TabView) {
        tabView.setOnLongClickListener { if (mIsCurrentPagePlugins) true.also { toggleFabVisibility() } else false }
    }

    private fun toggleFabVisibility() {
        val behavior = (fab.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? ScrollAwareFABBehavior

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

    override fun onFabClick(fab: FloatingActionButton) {
        initFloatingActionMenuIfNeeded(fab).run { if (isExpanded) collapse() else expand() }
    }

    override fun onBackPressed(activity: Activity) = false

    private fun initFloatingActionMenuIfNeeded(fab: FloatingActionButton): PluginFloatingActionMenu {
        return mFloatingActionMenu ?: requireActivity().findViewById<PluginFloatingActionMenu>(R.id.plugin_floating_action_menu).also { menu ->
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
            mFloatingActionMenu = menu
        }
    }

    override fun onPageShow() {
        super.onPageShow()
        mIsCurrentPagePlugins = true
        fab.setOnLongClickListener {
            mFloatingActionMenu?.let { menu -> if (menu.isExpanded) menu.collapse() }
            PluginCenterActivity.launch(requireContext())
            true
        }
        schedulePluginCenterInitialization()
    }

    override fun onPageHide() {
        super.onPageHide()
        clearSearch()
        mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
        fab.setOnLongClickListener(null)
        mIsCurrentPagePlugins = false
        cancelPluginCenterInitialization()
        cancelPluginCenterNightModeRefresh()
    }

    override fun onPageSettled() {
        super.onPageSettled()
        if (mIsCurrentPagePlugins) {
            schedulePluginCenterInitialization()
        }
    }

    @Subscribe
    fun onQuerySummit(event: QueryEvent) {
        // Tab selection can hide this page before the toolbar sends its collapse event.
        // zh-CN: 标签切换可能先隐藏本页, 再由工具栏发送搜索收起事件.
        if (event === QueryEvent.CLEAR) {
            clearSearch()
            return
        }
        if (!isShown) {
            return
        }

        val child = childFragmentManager.findFragmentByTag(TAG_PLUGIN_CENTER) as? PluginCenterFragment ?: return

        if (event === QueryEvent.FIND_FORWARD) {
            return
        }
        if (event === QueryEvent.FIND_BACKWARD) {
            return
        }

        child.setQuery(event.query)
    }

    private fun clearSearch() {
        (childFragmentManager.findFragmentByTag(TAG_PLUGIN_CENTER) as? PluginCenterFragment)?.setQuery(null)
    }

    override fun onStart() {
        super.onStart()
        Pref.registerOnSharedPreferenceChangeListener(mNightModePrefListener)
    }

    override fun onStop() {
        super.onStop()
        Pref.get().unregisterOnSharedPreferenceChangeListener(mNightModePrefListener)
        cancelPluginCenterInitialization()
        cancelPluginCenterNightModeRefresh()
    }

    private fun schedulePluginCenterInitialization() {
        if (!mIsCurrentPagePlugins || !isAdded || view == null) return
        mPluginCenterInitJob?.cancel()
        mPluginCenterInitJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!mIsCurrentPagePlugins || !isAdded || view == null) return@launch
            when (ensurePluginCenterFragment()) {
                PluginCenterEnsureResult.EXISTS -> schedulePluginCenterNightModeRefresh()
                PluginCenterEnsureResult.CREATED,
                PluginCenterEnsureResult.PENDING,
                    -> Unit
            }
        }
    }

    private fun ensurePluginCenterFragment(): PluginCenterEnsureResult {
        if (findPluginCenterFragment() != null) return PluginCenterEnsureResult.EXISTS
        if (mPluginCenterCreationRequested) return PluginCenterEnsureResult.PENDING

        mPluginCenterCreationRequested = true
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.plugin_center_container, PluginCenterFragment(), TAG_PLUGIN_CENTER)
            runOnCommit {
                mPluginCenterCreationRequested = false
            }
        }
        return PluginCenterEnsureResult.CREATED
    }

    private fun findPluginCenterFragment(): PluginCenterFragment? {
        return childFragmentManager.findFragmentByTag(TAG_PLUGIN_CENTER) as? PluginCenterFragment
    }

    private fun cancelPluginCenterInitialization() {
        mPluginCenterInitJob?.cancel()
        mPluginCenterInitJob = null
    }

    private fun schedulePluginCenterNightModeRefresh() {
        if (!mIsCurrentPagePlugins || !isAdded || view == null) return
        mNightModeRefreshJob?.cancel()
        mNightModeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(PLUGIN_CENTER_NIGHT_MODE_REFRESH_DELAY_MS)
            if (!mIsCurrentPagePlugins || !isAdded || view == null) return@launch
            val child = findPluginCenterFragment() ?: return@launch
            child.refreshPluginIconsForNightModeChange(force = false)
        }
    }

    private fun cancelPluginCenterNightModeRefresh() {
        mNightModeRefreshJob?.cancel()
        mNightModeRefreshJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelPluginCenterInitialization()
        cancelPluginCenterNightModeRefresh()
        mFloatingActionMenu = null
    }

    override fun onDetach() {
        super.onDetach()
        mFloatingActionMenu?.setOnFloatingActionButtonClickListener(null)
    }

    override fun onClick(button: FloatingActionButton, pos: Int) {
        val context = requireContext()
        when (pos) {
            4 -> {
                PluginInstallWizardDialog.show(requireActivity(), lifecycleScope)
            }
            3 -> {
                PluginCenterActivity.launch(context, PluginIndexSource.OFFICIAL)
            }
            2 -> {
                PluginCenterActivity.launch(context, PluginIndexSource.THIRD_PARTY)
            }
            1 -> {
                PluginInstallActions.showInstallFromUrlDialog(context, lifecycleScope)
            }
            0 -> {
                PluginInstallActions.installFromLocalFile(mPickApkLauncher)
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG_PLUGIN_CENTER = "plugin_center"
        private const val PLUGIN_CENTER_NIGHT_MODE_REFRESH_DELAY_MS = 180L
    }

    private enum class PluginCenterEnsureResult {
        CREATED,
        EXISTS,
        PENDING,
    }

}
