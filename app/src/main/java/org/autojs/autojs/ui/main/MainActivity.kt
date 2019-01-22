package org.autojs.autojs.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.annotation.NonNull
import com.google.android.material.floatingactionbutton.FloatingActionButton

import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.viewpager.widget.ViewPager
import butterknife.BindView

import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View

import com.google.android.material.tabs.TabLayout
import com.stardust.app.FragmentPagerAdapterBuilder
import com.stardust.app.OnActivityResultDelegate
import com.stardust.autojs.core.permission.OnRequestPermissionsResultCallback
import com.stardust.autojs.core.permission.PermissionRequestProxyActivity
import com.stardust.autojs.core.permission.RequestPermissionCallbacks
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.pio.PFiles
import com.stardust.theme.ThemeColorManager
import com.stardust.util.BackPressedHandler
import com.stardust.util.DeveloperUtils
import com.stardust.util.DrawerAutoClose

import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Click
import org.androidannotations.annotations.EActivity
import org.autojs.autojs.BuildConfig
import org.autojs.autojs.Pref
import org.autojs.autojs.R
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.external.foreground.ForegroundService
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.doc.DocsFragment_
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.log.LogActivity_
import org.autojs.autojs.ui.main.community.CommunityFragment
import org.autojs.autojs.ui.main.community.CommunityFragment_
import org.autojs.autojs.ui.main.sample.MarketFragment_
import org.autojs.autojs.ui.main.scripts.MyScriptListFragment_
import org.autojs.autojs.ui.main.task.TaskManagerFragment_
import org.autojs.autojs.ui.settings.SettingsActivity_
import org.autojs.autojs.ui.update.VersionGuard
import org.autojs.autojs.ui.widget.CommonMarkdownView
import org.autojs.autojs.ui.widget.SearchViewItem
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

import java.util.Arrays

class MainActivity : BaseActivity(), OnActivityResultDelegate.DelegateHost, BackPressedHandler.HostActivity, PermissionRequestProxyActivity {

    @BindView(R.id.drawer_layout)
    internal var mDrawerLayout: DrawerLayout? = null

    @BindView(R.id.viewpager)
    internal var mViewPager: ViewPager? = null

    @BindView(R.id.fab)
    internal var mFab: FloatingActionButton? = null

    private var mPagerAdapter: FragmentPagerAdapterBuilder.StoredFragmentPagerAdapter? = null
    private val mActivityResultMediator = OnActivityResultDelegate.Mediator()
    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mVersionGuard: VersionGuard? = null
    private val mBackPressObserver = BackPressedHandler.Observer()
    private var mSearchViewItem: SearchViewItem? = null
    private var mLogMenuItem: MenuItem? = null
    private var mDocsSearchItemExpanded: Boolean = false


    object DrawerOpenEvent {
        internal var SINGLETON = DrawerOpenEvent()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        showAccessibilitySettingPromptIfDisabled()
        mVersionGuard = VersionGuard(this)
        showAnnunciationIfNeeded()
        EventBus.getDefault().register(this)
        applyDayNightMode()
    }

    @AfterViews
    internal override fun setUpViews() {
        setUpToolbar()
        setUpTabViewPager()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        registerBackPressHandlers()
        ThemeColorManager.addViewBackground(findViewById(R.id.app_bar))
        mDrawerLayout!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View?) {
                EventBus.getDefault().post(DrawerOpenEvent.SINGLETON)
            }
        })
    }

    private fun showAnnunciationIfNeeded() {
        if (!Pref.shouldShowAnnunciation()) {
            return
        }
        CommonMarkdownView.DialogBuilder(this)
                .padding(36, 0, 36, 0)
                .markdown(PFiles.read(resources.openRawResource(R.raw.annunciation)))
                .title(R.string.text_annunciation)
                .positiveText(R.string.ok)
                .canceledOnTouchOutside(false)
                .show()
    }


    private fun registerBackPressHandlers() {
        mBackPressObserver.registerHandler(DrawerAutoClose(mDrawerLayout, Gravity.START))
        mBackPressObserver.registerHandler(BackPressedHandler.DoublePressExit(this, R.string.text_press_again_to_exit))
    }

    private fun checkPermissions() {
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun showAccessibilitySettingPromptIfDisabled() {
        if (AccessibilityServiceTool.isAccessibilityServiceEnabled(this)) {
            return
        }
        NotAskAgainDialog.Builder(this, "MainActivity.accessibility")
                .title(R.string.text_need_to_enable_accessibility_service)
                .content(R.string.explain_accessibility_permission)
                .positiveText(R.string.text_go_to_setting)
                .negativeText(R.string.text_cancel)
                .onPositive { dialog, which -> AccessibilityServiceTool.enableAccessibilityService() }.show()
    }

    private fun setUpToolbar() {
        val toolbar = `$`(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setTitle(R.string.app_name)
        val drawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.text_drawer_open,
                R.string.text_drawer_close)
        drawerToggle.syncState()
        mDrawerLayout!!.addDrawerListener(drawerToggle)
    }

    private fun setUpTabViewPager() {
        val tabLayout = `$`(R.id.tab)
        mPagerAdapter = FragmentPagerAdapterBuilder(this)
                .add(MyScriptListFragment_(), R.string.text_file)
                .add(DocsFragment_(), R.string.text_tutorial)
                .add(CommunityFragment_(), R.string.text_community)
                .add(MarketFragment_(), R.string.text_market)
                .add(TaskManagerFragment_(), R.string.text_manage)
                .build()
        mViewPager!!.adapter = mPagerAdapter
        tabLayout.setupWithViewPager(mViewPager)
        setUpViewPagerFragmentBehaviors()
    }

    private fun setUpViewPagerFragmentBehaviors() {


        mPagerAdapter!!.setOnFragmentInstantiateListener { pos, fragment ->
            (fragment as ViewPagerFragment).setFab(mFab)
            if (pos == mViewPager!!.currentItem) {
                fragment.onPageShow()
            }
        }
        mViewPager!!.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private var mPreviousFragment: ViewPagerFragment? = null

            override fun onPageSelected(position: Int) {
                val fragment = mPagerAdapter!!.getStoredFragment(position) ?: return
                if (mPreviousFragment != null) {
                    mPreviousFragment!!.onPageHide()
                }
                mPreviousFragment = fragment as ViewPagerFragment
                mPreviousFragment!!.onPageShow()
            }
        })
    }


    @Click(R.id.setting)
    internal fun startSettingActivity() {
        startActivity(Intent(this, SettingsActivity_::class.java))
    }

    @Click(R.id.exit)
    fun exitCompletely() {
        finish()
        FloatyWindowManger.hideCircularMenu()
        ForegroundService.stop(this)
        stopService(Intent(this, FloatyService::class.java))
        AutoJs.getInstance().scriptEngineService.stopAll()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mVersionGuard!!.checkForDeprecatesAndUpdates()
    }

    protected override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        mActivityResultMediator.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (mRequestPermissionCallbacks.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }
        if (getGrantResult(Manifest.permission.READ_EXTERNAL_STORAGE, permissions, grantResults) == PackageManager.PERMISSION_GRANTED) {
            Explorers.workspace().refreshAll()
        }
    }

    private fun getGrantResult(permission: String, permissions: Array<String>, grantResults: IntArray): Int {
        val i = Arrays.asList(*permissions).indexOf(permission)
        return if (i < 0) {
            2
        } else grantResults[i]
    }

    override fun onStart() {
        super.onStart()
        if (!BuildConfig.DEBUG) {
            DeveloperUtils.verifyApk(this, R.string.dex_crcs)
        }
    }


    @NonNull
    override fun getOnActivityResultDelegateMediator(): OnActivityResultDelegate.Mediator {
        return mActivityResultMediator
    }

    override fun onBackPressed() {
        val fragment = mPagerAdapter!!.getStoredFragment(mViewPager!!.currentItem)
        if (fragment is BackPressedHandler) {
            if ((fragment as BackPressedHandler).onBackPressed(this)) {
                return
            }
        }
        if (!mBackPressObserver.onBackPressed(this)) {
            super.onBackPressed()
        }
    }

    override fun addRequestPermissionsCallback(callback: OnRequestPermissionsResultCallback) {
        mRequestPermissionCallbacks.addCallback(callback)
    }

    override fun removeRequestPermissionsCallback(callback: OnRequestPermissionsResultCallback): Boolean {
        return mRequestPermissionCallbacks.removeCallback(callback)
    }


    override fun getBackPressedObserver(): BackPressedHandler.Observer {
        return mBackPressObserver
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchMenuItem = menu.findItem(R.id.action_search)
        mLogMenuItem = menu.findItem(R.id.action_log)
        setUpSearchMenuItem(searchMenuItem)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_log) {
            if (mDocsSearchItemExpanded) {
                submitForwardQuery()
            } else {
                LogActivity_.intent(this).start()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Subscribe
    fun onLoadUrl(loadUrl: CommunityFragment.LoadUrl) {
        mDrawerLayout!!.closeDrawer(GravityCompat.START)
    }


    private fun setUpSearchMenuItem(searchMenuItem: MenuItem) {
        mSearchViewItem = object : SearchViewItem(this, searchMenuItem) {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                if (mViewPager!!.currentItem == 1) {
                    mDocsSearchItemExpanded = true
                    mLogMenuItem!!.setIcon(R.drawable.ic_ali_up)
                }
                return super.onMenuItemActionExpand(item)
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                if (mDocsSearchItemExpanded) {
                    mDocsSearchItemExpanded = false
                    mLogMenuItem!!.setIcon(R.drawable.ic_ali_log)
                }
                return super.onMenuItemActionCollapse(item)
            }
        }
        mSearchViewItem!!.setQueryCallback { this.submitQuery(it) }
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
    }

    companion object {

        private val LOG_TAG = "MainActivity"
    }
}