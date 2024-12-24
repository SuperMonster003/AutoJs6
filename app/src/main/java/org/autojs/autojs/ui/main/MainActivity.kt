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
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.huaban.analysis.jieba.CharsDictionaryDatabase
import com.huaban.analysis.jieba.PhrasesDictionaryDatabase
import com.huaban.analysis.jieba.WordDictionaryDatabase
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.FragmentPagerAdapterBuilder
import org.autojs.autojs.app.FragmentPagerAdapterBuilder.StoredFragmentPagerAdapter
import org.autojs.autojs.app.OnActivityResultDelegate
import org.autojs.autojs.app.OnActivityResultDelegate.DelegateHost
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.image.capture.ScreenCapturerForegroundService
import org.autojs.autojs.core.permission.RequestPermissionCallbacks
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.event.BackPressedHandler
import org.autojs.autojs.event.BackPressedHandler.DoublePressExit
import org.autojs.autojs.event.BackPressedHandler.HostActivity
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.permission.ManageAllFilesPermission
import org.autojs.autojs.permission.PostNotificationPermission
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.service.ForegroundService
import org.autojs.autojs.theme.ThemeColorManager.addViewBackground
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.doc.DocumentationFragment
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.OnDrawerClosed
import org.autojs.autojs.ui.main.drawer.DrawerFragment.Companion.Event.OnDrawerOpened
import org.autojs.autojs.ui.main.scripts.ExplorerFragment
import org.autojs.autojs.ui.main.task.TaskManagerFragment
import org.autojs.autojs.ui.settings.PreferencesActivity
import org.autojs.autojs.ui.widget.DrawerAutoClose
import org.autojs.autojs.ui.widget.SearchViewItem
import org.autojs.autojs.util.UpdateUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityMainBinding
import org.greenrobot.eventbus.EventBus

/**
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on May 11, 2023.
 */
class MainActivity : BaseActivity(), DelegateHost, HostActivity {

    private var binding: ActivityMainBinding? = null

    private lateinit var mViewPager: ViewPager
    private lateinit var mFab: FloatingActionButton
    private lateinit var mPagerAdapter: StoredFragmentPagerAdapter
    private lateinit var mLogMenuItem: MenuItem

    private val mActivityResultMediator = OnActivityResultDelegate.Mediator()
    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private val mBackPressObserver = BackPressedHandler.Observer()
    private val mForeGroundService = ForegroundService(this)
    private var mSearchViewItem: SearchViewItem? = null
    private var mDocsSearchItemExpanded = false
    private val mA11yTool = AccessibilityTool(this)

    private val isCurrentPageDocs: Boolean
        get() {
            val pageTitle = mPagerAdapter.getPageTitle(mViewPager.currentItem)
            return pageTitle != null && getString(R.string.text_documentation).contentEquals(pageTitle)
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

    val requestMultiplePermissionsLauncher = registerForActivityResult(RequestMultiplePermissions()) {
        it.forEach { (key: String, isGranted: Boolean) ->
            Log.d(TAG, "$key: $isGranted")
            if (key == Manifest.permission.POST_NOTIFICATIONS) {
                Pref.putBoolean(R.string.key_post_notification_permission_requested, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ActivityMainBinding.inflate(layoutInflater).also {
            val drawerLayout = it.drawerLayout
            binding = it
            setContentView(it.root)
            mViewPager = it.viewpager
            mFab = it.fab
            addViewBackground(it.appBar)
            setUpToolbar(it, drawerLayout)
            setUpTabViewPager(it)
            registerBackPressHandlers(drawerLayout)
        }
        @Suppress("DEPRECATION")
        ViewUtils.appendSystemUiVisibility(this, View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        ViewUtils.registerOnSharedPreferenceChangeListener(this)

        WorkingDirectoryUtils.determineIfNeeded()
        ExplorerView.clearViewStates()

        FloatyWindowManger.refreshCircularMenuIfNeeded(this)

        PostNotificationPermission(this).urgeIfNeeded()
        ManageAllFilesPermission(this).urgeIfNeeded()
        DisplayOverOtherAppsPermission(this).urgeIfNeeded()
    }

    override fun onPostResume() {
        recreateIfNeeded()
        UpdateUtils.autoCheckForUpdatesIfNeededWithSnackbar(this)
        super.onPostResume()
    }

    override fun onStart() {
        super.onStart()
        WrappedShizuku.bindUserServiceIfNeeded()
    }

    private fun recreateIfNeeded() {
        if (shouldRecreateMainActivity) {
            shouldRecreateMainActivity = false
            recreate()
            Explorers.workspace().refreshAll()
        }
    }

    private fun registerBackPressHandlers(drawerLayout: DrawerLayout) {
        mBackPressObserver.registerHandler(DrawerAutoClose(drawerLayout, Gravity.START))
        mBackPressObserver.registerHandler(DoublePressExit(this, R.string.text_press_again_to_exit))
    }

    private fun setUpToolbar(binding: ActivityMainBinding, drawerLayout: DrawerLayout) {
        val toolbar = binding.toolbar.also {
            setSupportActionBar(it)
            it.setTitle(R.string.app_name)
            it.setOnLongClickListener { true.also { PreferencesActivity.launch(this) } }
        }

        object : ActionBarDrawerToggle(
            this,
            drawerLayout,
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
            drawerLayout.addDrawerListener(it)
        }
    }

    private fun setUpTabViewPager(binding: ActivityMainBinding) {
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

    fun rebirth() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(Intent.makeRestartActivityTask(it.component))
        }
        exitCompletely()
    }

    fun exitCompletely() {
        FloatyWindowManger.hideCircularMenuAndSaveState()

        stopService(Intent(this, FloatyService::class.java))
        stopService(Intent(this, ScreenCapturerForegroundService::class.java))

        AutoJs.instance.scriptEngineService.stopAll()

        WordDictionaryDatabase.getInstance(this).close()
        CharsDictionaryDatabase.getInstance(this).close()
        PhrasesDictionaryDatabase.getInstance(this).close()

        mA11yTool.stopService(false)
        mForeGroundService.stopIfNeeded()
        WrappedShizuku.onDestroy()

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
        if (getReadExternalStoragePermissionResult(permissions, grantResults) == PackageManager.PERMISSION_GRANTED) {
            Explorers.workspace().refreshAll()
        }
    }

    private fun getReadExternalStoragePermissionResult(permissions: Array<String>, grantResults: IntArray): Int {
        val i = permissions.indexOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return if (i > -1) grantResults[i] else PackageManager.PERMISSION_DENIED
    }

    override fun getOnActivityResultDelegateMediator() = mActivityResultMediator

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val fragment = mPagerAdapter.getStoredFragment(mViewPager.currentItem)
        if ((fragment as? BackPressedHandler)?.onBackPressed(this) == true) {
            return
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

    override fun onResume() {
        super.onResume()
        ViewUtils.configKeepScreenOnWhenInForeground(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
        mSearchViewItem = null
        EventBus.getDefault().unregister(this)
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName

        var shouldRecreateMainActivity = false

        @JvmStatic
        fun launch(context: Context) = context.startActivity(getIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        @JvmStatic
        fun getIntent(context: Context?) = Intent(context, MainActivity::class.java)

    }

}
