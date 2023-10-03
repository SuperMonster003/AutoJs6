package org.autojs.autojs.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.autojs.autojs.App
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.FragmentPagerAdapterBuilder
import org.autojs.autojs.app.FragmentPagerAdapterBuilder.StoredFragmentPagerAdapter
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.app.OnActivityResultDelegate.DelegateHost
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.permission.RequestPermissionCallbacks
import org.autojs.autojs.event.BackPressedHandler
import org.autojs.autojs.event.BackPressedHandler.DoublePressExit
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.external.foreground.MainActivityForegroundService
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.ManageAllFilesPermission
import org.autojs.autojs.permission.PostNotificationPermission
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.runtime.api.AppUtils.Companion.hideLauncherIcon
import org.autojs.autojs.runtime.api.AppUtils.Companion.showLauncherIcon
import org.autojs.autojs.theme.ThemeColorManager.addViewBackground
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.doc.DocumentationFragment
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.OnDrawerClosed
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.OnDrawerOpened
import org.autojs.autojs.ui.main.scripts.ExplorerFragment
import org.autojs.autojs.ui.main.task.TaskManagerFragment
import org.autojs.autojs.ui.pager.ViewPager
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.ui.widget.DrawerAutoClose
import org.autojs.autojs.ui.widget.SearchViewItem
import org.autojs.autojs.util.ForegroundServiceUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.UpdateUtils.autoCheckForUpdatesIfNeededWithSnackbar
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityMainBinding
import org.greenrobot.eventbus.EventBus
import rikka.shizuku.Shizuku

/**
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on May 11, 2023.
 */
class MainActivity : BaseActivity(), DelegateHost, HostActivity {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mViewPager: ViewPager
    private lateinit var mFab: FloatingActionButton
    private lateinit var mPagerAdapter: StoredFragmentPagerAdapter
    private lateinit var mLogMenuItem: MenuItem

    private val mActivityResultMediator = OnActivityResultDelegate.Mediator()
    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private val mBackPressObserver = BackPressedHandler.Observer()
    private var mSearchViewItem: SearchViewItem? = null
    private var mDocsSearchItemExpanded = false

    val requestMultiplePermissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) {
        it.forEach { (key: String, isGranted: Boolean) ->
            Log.d(TAG, "$key: $isGranted")
            if (key == Manifest.permission.POST_NOTIFICATIONS) {
                Pref.putBoolean(R.string.key_post_notification_permission_requested, true)
            }
        }
    }

    private val shizukuBinderReceivedListener = {
        // if (Shizuku.isPreV11()) {
        //     binding.text1.setText("Shizuku pre-v11 is not supported")
        // } else {
        //     binding.text1.setText("Binder received")
        // }
    }
    private val shizukuBinderDeadListener = {
        // binding.text1.setText("Binder dead")
    }
    private val shizukuRequestPermissionResultListener: (requestCode: Int, grantResult: Int) -> Unit = { /* requestCode: */ _: Int, /* grantResult */ _: Int ->
        // if (grantResult == PackageManager.PERMISSION_GRANTED) {
        //     switch (requestCode) {
        //         case REQUEST_CODE_BUTTON1: {
        //             getUsers();
        //             break;
        //         }
        //         case REQUEST_CODE_BUTTON4: {
        //             getSystemProperty();
        //             break;
        //         }
        //         case REQUEST_CODE_BUTTON7: {
        //             bindUserService();
        //             break;
        //         }
        //         case REQUEST_CODE_BUTTON8: {
        //             unbindUserService();
        //             break;
        //         }
        //         case REQUEST_CODE_BUTTON9: {
        //             peekUserService();
        //             break;
        //         }
        //     }
        // } else {
        //     binding.text1.setText("User denied permission");
        // }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater).also {
            setContentView(it.root)
            mDrawerLayout = it.drawerLayout
            mViewPager = it.viewpager
            mFab = it.fab
        }

        PostNotificationPermission(this).urgeIfNeeded()
        ManageAllFilesPermission(this).urgeIfNeeded()
        WorkingDirectoryUtils.determineIfNeeded()
        DisplayOverOtherAppsPermission(this).urgeIfNeeded()
        FloatyWindowManger.refreshCircularMenuIfNeeded(this)

        @Suppress("DEPRECATION")
        ViewUtils.appendSystemUiVisibility(this, View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        Pref.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == StringUtils.key(R.string.key_keep_screen_on_when_in_foreground)) {
                configKeepScreenOnWhenInForeground()
            }
        }

        ExplorerView.clearViewStates()
        setUpToolbar()
        setUpTabViewPager()
        registerBackPressHandlers()
        addViewBackground(binding.appBar)

        Log.d("Shizuku", "${App::class.java.simpleName} onCreate | Process=${App.getProcessNameCompat()}")
        Shizuku.addBinderReceivedListenerSticky(this.shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuRequestPermissionResultListener)
    }

    override fun onPostResume() {
        recreateIfNeeded()
        autoCheckForUpdatesIfNeededWithSnackbar(this, android.R.id.content)
        super.onPostResume()
    }

    private fun recreateIfNeeded() {
        if (sShouldRecreateMainActivity) {
            setShouldRecreateMainActivity(false)
            recreate()
            Explorers.workspace().refreshAll()
        }
    }

    private fun registerBackPressHandlers() {
        mBackPressObserver.registerHandler(DrawerAutoClose(mDrawerLayout, Gravity.START))
        mBackPressObserver.registerHandler(DoublePressExit(this, R.string.text_press_again_to_exit))
    }

    private fun setUpToolbar() {
        val toolbar = binding.toolbar.also {
            setSupportActionBar(it)
            it.setTitle(R.string.app_name)
            it.setOnLongClickListener { true.also { PreferencesActivity.launch(this) } }
        }

        object : ActionBarDrawerToggle(
            this,
            mDrawerLayout,
            toolbar,
            R.string.text_drawer_open,
            R.string.text_drawer_close
        ) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                EventBus.getDefault().post(object : OnDrawerOpened {})
            }

            override fun onDrawerClosed(drawerView: View) {
                super.onDrawerClosed(drawerView)
                EventBus.getDefault().post(object : OnDrawerClosed {})
            }
        }.also {
            it.syncState()
            mDrawerLayout.addDrawerListener(it)
        }
    }

    private fun setUpTabViewPager() {
        mPagerAdapter = FragmentPagerAdapterBuilder(this)
            .add(ExplorerFragment(), R.string.text_file)
            .add(DocumentationFragment(), R.string.text_documentation)
            .add(TaskManagerFragment(), R.string.text_task)
            .build()
            .apply {
                setOnFragmentInstantiateListener { pos: Int, fragment: Fragment ->
                    val viewPagerFragment = fragment as ViewPagerFragment
                    viewPagerFragment.setFab(mFab)
                    if (pos == mViewPager.currentItem) {
                        viewPagerFragment.onPageShow()
                    }
                }
            }
        mViewPager.let {
            it.adapter = mPagerAdapter
            binding.tab.setupWithViewPager(it)
            it.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
                private var mPreviousFragment: ViewPagerFragment? = null
                override fun onPageSelected(position: Int) {
                    val fragment = mPagerAdapter.getStoredFragment(position) ?: return
                    mPreviousFragment?.onPageHide()
                    mPreviousFragment = fragment as ViewPagerFragment
                    mPreviousFragment?.onPageShow()
                    if (mSearchViewItem?.isExpanded == true) {
                        mSearchViewItem?.collapse()
                    }
                }
            })
        }
    }

    fun rebirth(view: View) {
        val context = view.context as MainActivity
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(Intent.makeRestartActivityTask(it.component))
        }
        context.exitCompletely()
    }

    fun exitCompletely() {
        FloatyWindowManger.hideCircularMenuAndSaveState()
        ForegroundServiceUtils.stopServiceIfNeeded(this, MainActivityForegroundService::class.java)
        stopService(Intent(this, FloatyService::class.java))
        AutoJs.instance.scriptEngineService.stopAll()
        AccessibilityService.disable()
        Process.killProcess(Process.myPid())
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        mActivityResultMediator.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (mRequestPermissionCallbacks.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }
        if (getGrantResult(permissions, grantResults) == PackageManager.PERMISSION_GRANTED) {
            Explorers.workspace().refreshAll()
        }
    }

    private fun getGrantResult(permissions: Array<String>, grantResults: IntArray): Int {
        val i = listOf(*permissions).indexOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return if (i < 0) 2 else grantResults[i]
    }

    override fun getOnActivityResultDelegateMediator() = mActivityResultMediator

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val fragment = mPagerAdapter.getStoredFragment(mViewPager.currentItem)
        if (fragment is BackPressedHandler) {
            if ((fragment as BackPressedHandler).onBackPressed(this)) {
                return
            }
        }
        if (!mBackPressObserver.onBackPressed(this)) {
            super.onBackPressed()
        }
    }

    override fun getBackPressedObserver() = mBackPressObserver

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        mLogMenuItem = menu.findItem(R.id.action_log)
        setUpSearchMenuItem(menu.findItem(R.id.action_search))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_log) {
            if (mDocsSearchItemExpanded) {
                submitForwardQuery()
            } else {
                LogActivity.launch(this)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setUpSearchMenuItem(searchMenuItem: MenuItem) {
        mSearchViewItem = object : SearchViewItem(this, searchMenuItem) {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                if (isCurrentPageDocs) {
                    mDocsSearchItemExpanded = true
                    mLogMenuItem.setIcon(R.drawable.ic_ali_up)
                }
                return super.onMenuItemActionExpand(item)
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (mDocsSearchItemExpanded) {
                    mDocsSearchItemExpanded = false
                    mLogMenuItem.setIcon(R.drawable.ic_ali_log)
                }
                return super.onMenuItemActionCollapse(item)
            }
        }.apply {
            setQueryCallback { query: String? -> submitQuery(query) }
        }
    }

    val docsItemIndex: Int
        get() {
            var i = 0
            while (i < mPagerAdapter.count) {
                val pageTitle = mPagerAdapter.getPageTitle(i)
                if (pageTitle != null && getString(R.string.text_documentation).contentEquals(pageTitle)) {
                    return i
                }
                i += 1
            }
            return -1
        }
    private val isCurrentPageDocs: Boolean
        get() {
            val pageTitle = mPagerAdapter.getPageTitle(mViewPager.currentItem)
            return pageTitle != null && getString(R.string.text_documentation).contentEquals(pageTitle)
        }

    private fun submitQuery(query: String?) {
        if (query == null) {
            EventBus.getDefault().post(QueryEvent.CLEAR)
            return
        }
        val event = QueryEvent(query)
        EventBus.getDefault().post(event)
        if (event.shouldCollapseSearchView()) {
            mSearchViewItem!!.collapse()
        }
    }

    private fun submitForwardQuery() {
        val event = QueryEvent.FIND_FORWARD
        EventBus.getDefault().post(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)

        Shizuku.removeBinderReceivedListener(this.shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuRequestPermissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        configKeepScreenOnWhenInForeground()
        configDocumentationLauncherIcon()
    }

    private fun configKeepScreenOnWhenInForeground() {
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.let { flags ->
            if (ViewUtils.isKeepScreenOnWhenInForegroundEnabled) {
                window.addFlags(flags)
            } else {
                window.clearFlags(flags)
            }
        }
    }

    private fun configDocumentationLauncherIcon() {
        DocumentationActivity::class.java.let { clazz ->
            if (Pref.isDocumentationLauncherIconShouldShow) {
                showLauncherIcon(this, clazz)
            } else {
                hideLauncherIcon(this, clazz)
            }
        }
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName

        private var sShouldRecreateMainActivity = false

        fun setShouldRecreateMainActivity(b: Boolean) {
            sShouldRecreateMainActivity = b
        }

        @JvmStatic
        fun launch(context: Context) {
            context.startActivity(getIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        @JvmStatic
        fun getIntent(context: Context?) = Intent(context, MainActivity::class.java)

    }

}