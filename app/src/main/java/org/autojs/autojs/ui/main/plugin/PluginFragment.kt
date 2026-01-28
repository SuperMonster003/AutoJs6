package org.autojs.autojs.ui.main.plugin

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
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
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.center.PluginCenterFragment
import org.autojs.autojs.core.plugin.center.PluginInstallActions
import org.autojs.autojs.core.plugin.center.PluginInstaller
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_plugin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (childFragmentManager.findFragmentByTag(TAG_PLUGIN_CENTER) == null) {
            childFragmentManager.commit {
                replace(R.id.plugin_center_container, PluginCenterFragment(), TAG_PLUGIN_CENTER)
            }
        }

        (activity as? MainActivity)?.apply {
            val tabLayout: TabLayout = findViewById(R.id.tab)
            val pluginsTag = tabLayout.getTabAt(pluginsIndex)
            pluginsTag?.view?.let { setTabViewClickListeners(it) }
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
    }

    override fun onPageHide() {
        super.onPageHide()
        mFloatingActionMenu?.let { if (it.isExpanded) it.collapse() }
        mIsCurrentPagePlugins = false
    }

    @Subscribe
    fun onQuerySummit(event: QueryEvent) {
        if (!isShown) {
            return
        }

        val child = childFragmentManager.findFragmentByTag(TAG_PLUGIN_CENTER) as? PluginCenterFragment ?: return

        if (event === QueryEvent.CLEAR) {
            child.setQuery(null)
            return
        }
        if (event === QueryEvent.FIND_FORWARD) {
            return
        }
        if (event === QueryEvent.FIND_BACKWARD) {
            return
        }

        child.setQuery(event.query)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mFloatingActionMenu = null
    }

    override fun onDetach() {
        super.onDetach()
        mFloatingActionMenu?.setOnFloatingActionButtonClickListener(null)
    }

    override fun onClick(button: FloatingActionButton, pos: Int) {
        when (pos) {
            1 -> {
                PluginInstallActions.showInstallFromUrlDialog(requireContext(), lifecycleScope)
            }
            0 -> {
                PluginInstallActions.installFromLocalFile(mPickApkLauncher)
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG_PLUGIN_CENTER = "plugin_center"
    }

}
