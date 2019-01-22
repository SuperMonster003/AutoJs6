package org.autojs.autojs.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.NonNull
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import butterknife.OnClick
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
import kotlinx.android.synthetic.main.activity_main.*
import org.autojs.autojs.BuildConfig
import org.autojs.autojs.Pref
import org.autojs.autojs.R
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.external.foreground.ForegroundService
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.doc.DocsFragment
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.community.CommunityFragment
import org.autojs.autojs.ui.main.sample.MarketFragment
import org.autojs.autojs.ui.main.scripts.MyScriptListFragment
import org.autojs.autojs.ui.main.task.TaskManagerFragment
import org.autojs.autojs.ui.settings.SettingsActivity
import org.autojs.autojs.ui.update.VersionGuard
import org.autojs.autojs.ui.widget.CommonMarkdownView
import org.autojs.autojs.ui.widget.SearchViewItem
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.*

class MainActivity : BaseActivity(R.layout.activity_main), OnActivityResultDelegate.DelegateHost, BackPressedHandler.HostActivity, PermissionRequestProxyActivity {

    private var mPagerAdapter: FragmentPagerAdapterBuilder.StoredFragmentPagerAdapter? = null
    private val mActivityResultMediator = OnActivityResultDelegate.Mediator()
    private val mRequestPermissionCallbacks = RequestPermissionCallbacks()
    private var mVersionGuard: VersionGuard? = null
    private val mBackPressObserver = BackPressedHandler.Observer()
    private var mSearchViewItem: SearchViewItem? = null
    private var mLogMenuItem: MenuItem? = null
    private var mDocsSearchItemExpanded: Boolean = false

    object DrawerOpenEvent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        showAccessibilitySettingPromptIfDisabled()
        mVersionGuard = VersionGuard(this)
        showAnnunciationIfNeeded()
        EventBus.getDefault().register(this)
        applyDayNightMode()
    }

    override fun setUpViews() {
        setUpToolbar()
        setUpTabViewPager()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        registerBackPressHandlers()
        ThemeColorManager.addViewBackground(findViewById(R.id.app_bar))
        drawer_layout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                EventBus.getDefault().post(DrawerOpenEvent)
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
        mBackPressObserver.registerHandler(DrawerAutoClose(drawer_layout, Gravity.START))
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
                .onPositive { _, _ -> AccessibilityServiceTool.enableAccessibilityService() }.show()
    }

    private fun setUpToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setTitle(R.string.app_name)
        val drawerToggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.text_drawer_open,
                R.string.text_drawer_close)
        drawerToggle.syncState()
        drawer_layout.addDrawerListener(drawerToggle)
    }

    private fun setUpTabViewPager() {
        mPagerAdapter = FragmentPagerAdapterBuilder(this)
                .add(MyScriptListFragment(), R.string.text_file)
                .add(DocsFragment(), R.string.text_tutorial)
                .add(CommunityFragment(), R.string.text_community)
                .add(MarketFragment(), R.string.text_market)
                .add(TaskManagerFragment(), R.string.text_manage)
                .build()
        viewpager.adapter = mPagerAdapter
        tab.setupWithViewPager(viewpager)
        setUpViewPagerFragmentBehaviors()
    }

    private fun setUpViewPagerFragmentBehaviors() {


        mPagerAdapter!!.setOnFragmentInstantiateListener { pos, fragment ->
            (fragment as ViewPagerFragment).setFab(fab)
            if (pos == viewpager.currentItem) {
                fragment.onPageShow()
            }
        }
        viewpager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
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


    @OnClick(R.id.setting)
    internal fun startSettingActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    @OnClick(R.id.exit)
    fun exitCompletely() {
        finish()
        FloatyWindowManger.hideCircularMenu()
        ForegroundService.stop(this)
        stopService(Intent(this, FloatyService::class.java))
        AutoJs.getInstance().scriptEngineService.stopAll()
    }

    override fun onResume() {
        super.onResume()
        mVersionGuard!!.checkForDeprecatesAndUpdates()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        mActivityResultMediator.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<out String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (mRequestPermissionCallbacks.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }
        if (getGrantResult(Manifest.permission.READ_EXTERNAL_STORAGE, permissions, grantResults) == PackageManager.PERMISSION_GRANTED) {
            Explorers.workspace().refreshAll()
        }
    }

    private fun getGrantResult(permission: String, permissions: Array<out String>, grantResults: IntArray): Int {
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
        val fragment = mPagerAdapter!!.getStoredFragment(viewpager.currentItem)
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
                startActivity(Intent(this, LogActivity::class.java))
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Subscribe
    fun onLoadUrl(loadUrl: CommunityFragment.LoadUrl) {
        drawer_layout.closeDrawer(GravityCompat.START)
    }


    private fun setUpSearchMenuItem(searchMenuItem: MenuItem) {
        mSearchViewItem = object : SearchViewItem(this, searchMenuItem) {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                if (viewpager.currentItem == 1) {
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

}