package org.autojs.autojs.ui.main;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.stardust.app.FragmentPagerAdapterBuilder;
import com.stardust.app.OnActivityResultDelegate;
import com.stardust.autojs.core.permission.OnRequestPermissionsResultCallback;
import com.stardust.autojs.core.permission.PermissionRequestProxyActivity;
import com.stardust.autojs.core.permission.RequestPermissionCallbacks;
import com.stardust.autojs.util.FloatingPermission;
import com.stardust.enhancedfloaty.FloatyService;
import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;
import com.stardust.util.BackPressedHandler;
import com.stardust.util.DeveloperUtils;
import com.stardust.util.DrawerAutoClose;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.Pref;
import org.autojs.autojs.autojs.AutoJs;
import org.autojs.autojs.external.foreground.MainActivityForegroundService;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.network.UpdateChecker;
import org.autojs.autojs.tool.UpdateUtils;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.doc.DocsFragment_;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.log.LogActivity_;
import org.autojs.autojs.ui.main.drawer.DrawerFragment;
import org.autojs.autojs.ui.main.scripts.MyScriptListFragment_;
import org.autojs.autojs.ui.main.task.TaskManagerFragment_;
import org.autojs.autojs.ui.settings.SettingsActivity_;
import org.autojs.autojs.ui.widget.SearchViewItem;
import org.autojs.autojs6.BuildConfig;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_main)
public class MainActivity extends BaseActivity implements OnActivityResultDelegate.DelegateHost, BackPressedHandler.HostActivity, PermissionRequestProxyActivity {

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
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private SearchViewItem mSearchViewItem;
    private MenuItem mLogMenuItem;
    private boolean mDocsSearchItemExpanded;
    private boolean mShouldRestartApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        applyThemeColor();
        applyDayNightMode();
    }

    @Override
    protected void onPostResume() {
        restartIfNeeded();
        autoCheckForUpdatesIfNeeded();
        super.onPostResume();
    }

    private void restartIfNeeded() {
        if (mShouldRestartApplication) {
            mShouldRestartApplication = false;
            mHandler.post(() -> restartAfterPendingIntent(this));
        }
    }

    private void autoCheckForUpdatesIfNeeded() {
        if (Pref.isAutoCheckForUpdatesEnabled()) {
            long minCheckedInterval = 2 * 60 * 60 * 1000; // 2 hours
            long lastChecked = Pref.getLastUpdatesCheckedTimestamp();

            if (System.currentTimeMillis() - lastChecked > minCheckedInterval) {
                View rootView = findViewById(android.R.id.content);
                UpdateChecker checker = UpdateUtils.getSnackbarChecker(rootView);
                checker.checkNow();
            }
        }
    }

    public void applyThemeColor() {
        if (ThemeColor.fromPreferences(PreferenceManager.getDefaultSharedPreferences(this), null) == null) {
            ThemeColor defaultThemeColor = new ThemeColor(
                    ContextCompat.getColor(this, R.color.colorPrimary),
                    ContextCompat.getColor(this, R.color.colorPrimaryDark),
                    ContextCompat.getColor(this, R.color.colorAccent)
            );
            ThemeColorManager.setThemeColor(defaultThemeColor);
        }
    }

    @AfterViews
    public void setUpViews() {
        setUpToolbar();
        setUpTabViewPager();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        registerBackPressHandlers();
        ThemeColorManager.addViewBackground(findViewById(R.id.app_bar));
    }

    private void registerBackPressHandlers() {
        mBackPressObserver.registerHandler(new DrawerAutoClose(mDrawerLayout, Gravity.START));
        mBackPressObserver.registerHandler(new BackPressedHandler.DoublePressExit(this, R.string.text_press_again_to_exit));
    }

    private void checkPermissions() {
        checkDisplayOverOtherApps();
        checkStorageAccess();
    }

    private void checkStorageAccess() {
        if (!hasStorageAccess()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                new NotAskAgainDialog.Builder(this, "MainActivity.check_storage_access")
                        .title(R.string.text_all_files_access)
                        .content(String.format("%s\n\n%s",
                                getString(R.string.text_all_files_access_is_needed),
                                getString(R.string.text_click_ok_to_go_to_access_settings)))
                        .negativeText(R.string.text_cancel)
                        .positiveText(R.string.text_ok)
                        .onNegative((dialog, which) -> dialog.dismiss())
                        .onPositive((dialog, which) -> {
                            requestStorageAccess();
                            mShouldRestartApplication = true;
                            dialog.dismiss();
                        })
                        .cancelable(false)
                        .autoDismiss(false)
                        .show();
            } else {
                requestStorageAccess();
            }
        }
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Environment.isExternalStorageManager()
                : getPermissionsToRequest(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}).length == 0;
    }

    private void checkDisplayOverOtherApps() {
        if (!FloatingPermission.canDrawOverlays(this)) {
            new NotAskAgainDialog.Builder(this, "MainActivity.check_display_over_other_apps")
                    .title(R.string.text_display_over_other_app)
                    .content(String.format("%s\n\n%s",
                            getString(R.string.text_display_over_other_app_is_recommended),
                            getString(R.string.text_click_ok_to_go_to_access_settings)))
                    .negativeText(R.string.text_cancel)
                    .positiveText(R.string.text_ok)
                    .onNegative((dialog, which) -> dialog.dismiss())
                    .onPositive((dialog, which) -> {
                        goToManageDisplayOverOtherApps();
                        dialog.dismiss();
                    })
                    .cancelable(false)
                    .autoDismiss(false)
                    .show();
        }
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            String[] permissions = {READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            return;
        }
        try {
            startActivity(new Intent()
                    .setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.fromParts("package", getPackageName(), null)));
        } catch (Exception e) {
            startActivity(new Intent()
                    .setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void goToManageDisplayOverOtherApps() {
        FloatingPermission.manageDrawOverlays(this);
    }

    private void setUpToolbar() {
        Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.app_name);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.text_drawer_open, R.string.text_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                EventBus.getDefault().post(DrawerFragment.class);
            }
        };
        drawerToggle.syncState();
        mDrawerLayout.addDrawerListener(drawerToggle);
    }

    private void setUpTabViewPager() {
        TabLayout tabLayout = $(R.id.tab);
        mPagerAdapter = new FragmentPagerAdapterBuilder(this)
                .add(new MyScriptListFragment_(), R.string.text_file)
                .add(new DocsFragment_(), R.string.text_documentation)
                .add(new TaskManagerFragment_(), R.string.text_task)
                .build();
        mViewPager.setAdapter(mPagerAdapter);
        tabLayout.setupWithViewPager(mViewPager);
        setUpViewPagerFragmentBehaviors();
    }

    private void setUpViewPagerFragmentBehaviors() {
        mPagerAdapter.setOnFragmentInstantiateListener((pos, fragment) -> {
            ((ViewPagerFragment) fragment).setFab(mFab);
            if (pos == mViewPager.getCurrentItem()) {
                ((ViewPagerFragment) fragment).onPageShow();
            }
        });
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
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
            }
        });
    }

    @Click(R.id.setting)
    public void startSettingActivity(View view) {
        startActivity(new Intent(view.getContext(), SettingsActivity_.class));
    }

    @Click(R.id.exit)
    public void exitCompletely(View view) {
        FloatyWindowManger.hideCircularMenu();
        MainActivityForegroundService.stop(this);
        stopService(new Intent(view.getContext(), FloatyService.class));
        AutoJs.getInstance().getScriptEngineService().stopAll();
        finish();
        Process.killProcess(Process.myPid());
    }

    @Click(R.id.restart)
    @Override
    public void restartAfterPendingIntent(@NonNull View view) {
        super.restartAfterPendingIntent(view);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    public void addRequestPermissionsCallback(OnRequestPermissionsResultCallback callback) {
        mRequestPermissionCallbacks.addCallback(callback);
    }

    @Override
    public boolean removeRequestPermissionsCallback(OnRequestPermissionsResultCallback callback) {
        return mRequestPermissionCallbacks.removeCallback(callback);
    }

    @Override
    public BackPressedHandler.Observer getBackPressedObserver() {
        return mBackPressObserver;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
                if (mViewPager.getCurrentItem() == 1) {
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