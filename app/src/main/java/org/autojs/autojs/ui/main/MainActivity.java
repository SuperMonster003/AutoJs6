package org.autojs.autojs.ui.main;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.stardust.enhancedfloaty.FloatyService;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.FragmentPagerAdapterBuilder;
import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs.core.accessibility.AccessibilityService;
import org.autojs.autojs.core.permission.RequestPermissionCallbacks;
import org.autojs.autojs.event.BackPressedHandler;
import org.autojs.autojs.external.foreground.MainActivityForegroundService;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission;
import org.autojs.autojs.permission.ManageAllFilesPermission;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.widget.ThemeColorToolbar;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.doc.DocsFragment_;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.log.LogActivity_;
import org.autojs.autojs.ui.main.drawer.DrawerFragment;
import org.autojs.autojs.ui.main.scripts.MyScriptListFragment_;
import org.autojs.autojs.ui.main.task.TaskManagerFragment_;
import org.autojs.autojs.ui.pager.ViewPager;
import org.autojs.autojs.ui.settings.SettingsActivity_;
import org.autojs.autojs.ui.widget.DrawerAutoClose;
import org.autojs.autojs.ui.widget.SearchViewItem;
import org.autojs.autojs.util.DeveloperUtils;
import org.autojs.autojs.util.ForegroundServiceUtils;
import org.autojs.autojs.util.UpdateUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_main)
public class MainActivity extends BaseActivity implements OnActivityResultDelegate.DelegateHost, BackPressedHandler.HostActivity {

    @ViewById(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;

    @ViewById(R.id.viewpager)
    ViewPager mViewPager;

    @ViewById(R.id.fab)
    FloatingActionButton mFab;

    private FragmentPagerAdapterBuilder.StoredFragmentPagerAdapter mPagerAdapter;
    private final OnActivityResultDelegate.Mediator mActivityResultMediator = new OnActivityResultDelegate.Mediator();
    private final RequestPermissionCallbacks mRequestPermissionCallbacks = new RequestPermissionCallbacks();
    private final BackPressedHandler.Observer mBackPressObserver = new BackPressedHandler.Observer();

    private SearchViewItem mSearchViewItem;
    private MenuItem mLogMenuItem;
    private boolean mDocsSearchItemExpanded;

    private static boolean sShouldRestartApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new ManageAllFilesPermission(this).urgeIfNeeded();
        WorkingDirectoryUtils.determineIfNeeded();

        new DisplayOverOtherAppsPermission(this).urgeIfNeeded();
        FloatyWindowManger.refreshCircularMenuIfNeeded(this);

        ViewUtils.appendSystemUiVisibility(this, View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPostResume() {
        restartIfNeeded();
        UpdateUtils.autoCheckForUpdatesIfNeededWithSnackbar(this, android.R.id.content);
        super.onPostResume();
    }

    private void restartIfNeeded() {
        if (sShouldRestartApplication) {
            setShouldRestartApplication(false);
            recreate();
            Explorers.workspace().refreshAll();
        }
    }

    public static void setShouldRestartApplication(boolean b) {
        sShouldRestartApplication = b;
    }

    @AfterViews
    public void setUpViews() {
        setUpToolbar();
        setUpTabViewPager();
        registerBackPressHandlers();
        ThemeColorManager.addViewBackground(findViewById(R.id.app_bar));
    }

    private void registerBackPressHandlers() {
        mBackPressObserver.registerHandler(new DrawerAutoClose(mDrawerLayout, Gravity.START));
        mBackPressObserver.registerHandler(new BackPressedHandler.DoublePressExit(this, R.string.text_press_again_to_exit));
    }

    private void setUpToolbar() {
        ThemeColorToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setOnLongClickListener(v -> {
            SettingsActivity_.intent(this)
                    .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .start();
            return true;
        });

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.text_drawer_open, R.string.text_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                EventBus.getDefault().post(new DrawerFragment.Companion.Event.OnDrawerOpened() {
                    // Blank.
                });
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                EventBus.getDefault().post(new DrawerFragment.Companion.Event.OnDrawerClosed() {
                    // Blank.
                });
            }
        };
        drawerToggle.syncState();
        mDrawerLayout.addDrawerListener(drawerToggle);
    }

    private void setUpTabViewPager() {
        TabLayout tabLayout = findViewById(R.id.tab);

        mPagerAdapter = new FragmentPagerAdapterBuilder(this)
                .add(new MyScriptListFragment_(), R.string.text_file)
                .add(new DocsFragment_(), R.string.text_documentation)
                .add(new TaskManagerFragment_(), R.string.text_task)
                .build();
        mViewPager.setAdapter(mPagerAdapter);

        // mViewPager.onInterceptTouchEvent()

        tabLayout.setupWithViewPager(mViewPager);
        setUpViewPagerFragmentBehaviors();

        TabLayout.Tab docsTab = tabLayout.getTabAt(getDocsItemIndex());
        if (docsTab != null) {
            TabLayout.TabView docsTabView = docsTab.view;
            docsTabView.setOnClickListener(v -> {
                if (isCurrentPageDocs()) {
                    // FIXME by SuperMonster003 on Aug 24, 2022.
                    //  ! In normal circumstances, getWebView() should not be nullable.
                    WebView webView = getDocsFragment().getWebView();
                    if (webView != null) {
                        webView.scrollTo(0, 0);
                    }
                }
            });
            docsTabView.setOnLongClickListener(v -> {
                if (isCurrentPageDocs()) {
                    // FIXME by SuperMonster003 on Aug 24, 2022.
                    //  ! In normal circumstances, getWebView() should not be nullable.
                    WebView webView = getDocsFragment().getWebView();
                    if (webView != null) {
                        getDocsFragment().loadMainPage();
                    }
                    return true;
                }
                return false;
            });
        }
    }

    @NonNull
    private DocsFragment_ getDocsFragment() {
        return (DocsFragment_) mPagerAdapter.getItem(getDocsItemIndex());
    }

    private void setUpViewPagerFragmentBehaviors() {
        mPagerAdapter.setOnFragmentInstantiateListener((pos, fragment) -> {
            ViewPagerFragment viewPagerFragment = (ViewPagerFragment) fragment;
            viewPagerFragment.setFab(mFab);
            if (pos == mViewPager.getCurrentItem()) {
                viewPagerFragment.onPageShow();
            }
        });
        mViewPager.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            private ViewPagerFragment mPreviousFragment;

            @Override
            public void onPageSelected(int position) {
                Fragment fragment = mPagerAdapter.getStoredFragment(position);
                if (fragment == null)
                    return;
                if (mPreviousFragment != null) {
                    mPreviousFragment.onPageHide();
                }
                mPreviousFragment = (ViewPagerFragment) fragment;
                mPreviousFragment.onPageShow();

                if (mSearchViewItem.isExpanded()) {
                    mSearchViewItem.collapse();
                }
            }
        });
    }

    public void exitCompletely(View view) {
        FloatyWindowManger.hideCircularMenu(!isFinishing());
        ForegroundServiceUtils.stopServiceIfNeeded(this, MainActivityForegroundService.class);
        stopService(new Intent(this, FloatyService.class));
        AutoJs.getInstance().getScriptEngineService().stopAll();
        AccessibilityService.disable();
        Process.killProcess(Process.myPid());
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mActivityResultMediator.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mRequestPermissionCallbacks.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            return;
        }
        if (getGrantResult(permissions, grantResults) == PackageManager.PERMISSION_GRANTED) {
            Explorers.workspace().refreshAll();
        }
    }

    private int getGrantResult(String[] permissions, int[] grantResults) {
        int i = Arrays.asList(permissions).indexOf(READ_EXTERNAL_STORAGE);
        if (i < 0) {
            return 2;
        }
        return grantResults[i];
    }

    @Override
    protected void onStart() {
        super.onStart();
        verifyApkIfNeeded();
    }

    private void verifyApkIfNeeded() {
        if (!BuildConfig.DEBUG) {
            DeveloperUtils.verifyApk(this);
        }
    }

    @NonNull
    @Override
    public OnActivityResultDelegate.Mediator getOnActivityResultDelegateMediator() {
        return mActivityResultMediator;
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = mPagerAdapter.getStoredFragment(mViewPager.getCurrentItem());
        if (fragment instanceof BackPressedHandler) {
            if (((BackPressedHandler) fragment).onBackPressed(this)) {
                return;
            }
        }
        if (!mBackPressObserver.onBackPressed(this)) {
            super.onBackPressed();
        }
    }

    @Override
    public BackPressedHandler.Observer getBackPressedObserver() {
        return mBackPressObserver;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        mLogMenuItem = menu.findItem(R.id.action_log);
        setUpSearchMenuItem(searchMenuItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_log) {
            if (mDocsSearchItemExpanded) {
                submitForwardQuery();
            } else {
                LogActivity_.intent(this).start();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpSearchMenuItem(MenuItem searchMenuItem) {
        mSearchViewItem = new SearchViewItem(this, searchMenuItem) {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                if (isCurrentPageDocs()) {
                    mDocsSearchItemExpanded = true;
                    mLogMenuItem.setIcon(R.drawable.ic_ali_up);
                }
                return super.onMenuItemActionExpand(item);
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (mDocsSearchItemExpanded) {
                    mDocsSearchItemExpanded = false;
                    mLogMenuItem.setIcon(R.drawable.ic_ali_log);
                }
                return super.onMenuItemActionCollapse(item);
            }
        };
        mSearchViewItem.setQueryCallback(this::submitQuery);
    }

    private int getDocsItemIndex() {
        for (int i = 0; i < mPagerAdapter.getCount(); i += 1) {
            CharSequence pageTitle = mPagerAdapter.getPageTitle(i);
            if (pageTitle != null && getString(R.string.text_documentation).contentEquals(pageTitle)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isCurrentPageDocs() {
        CharSequence pageTitle = mPagerAdapter.getPageTitle(mViewPager.getCurrentItem());
        return pageTitle != null && getString(R.string.text_documentation).contentEquals(pageTitle);
    }

    private void submitQuery(String query) {
        if (query == null) {
            EventBus.getDefault().post(QueryEvent.CLEAR);
            return;
        }
        QueryEvent event = new QueryEvent(query);
        EventBus.getDefault().post(event);
        if (event.shouldCollapseSearchView()) {
            mSearchViewItem.collapse();
        }
    }

    private void submitForwardQuery() {
        QueryEvent event = QueryEvent.FIND_FORWARD;
        EventBus.getDefault().post(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

}