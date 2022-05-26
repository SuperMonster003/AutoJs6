package org.autojs.autojs.ui.main.drawer;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.AppOpsKt;
import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.accessibility.AccessibilityServiceTool;
import com.stardust.autojs.core.util.ProcessShell;
import com.stardust.autojs.util.FloatingPermission;
import com.stardust.autojs.util.ForegroundServiceUtils;
import com.stardust.notification.NotificationListenerService;
import com.stardust.theme.ThemeColorManager;
import com.stardust.util.IntentUtil;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.Pref;
import org.autojs.autojs.external.foreground.MainActivityForegroundService;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.pluginclient.JsonSocketClient;
import org.autojs.autojs.pluginclient.JsonSocketServer;
import org.autojs.autojs.tool.Observers;
import org.autojs.autojs.tool.PermissionTool;
import org.autojs.autojs.tool.RootTool;
import org.autojs.autojs.tool.SettingsTool;
import org.autojs.autojs.tool.WifiTool;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.floating.CircularMenu;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.settings.SettingsActivity;
import org.autojs.autojs6.R;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/*
    @RefactorNeeded by SuperMonster003 on May 3, 2022.
     ! Class DrawerFragment is much too heavy.
     ! Disassemble methods or classes not related to Fragment and assemble them to other class (*.java/*.kt) files.
     ! Or, make DrawerMenuItem more functional may also be a good idea.
 */

/**
 * Created by Stardust on Jan 30, 2017.
 * Modified by SuperMonster003 as of Nov 16, 2021.
 */
@SuppressLint("NonConstantResourceId")
@SuppressWarnings("ResultOfMethodCallIgnored")
@EFragment(R.layout.fragment_drawer)
public class DrawerFragment extends androidx.fragment.app.Fragment {

    private static final String URL_DEV_PLUGIN = "https://www.autojs.org/topic/968/";

    private static final String WRITE_SECURE_SETTINGS_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String PROJECT_MEDIA_PERMISSION = "PROJECT_MEDIA";

    private Context mContext;
    private String mPackageName;
    private AccessibilityServiceTool mAccessibilityServiceTool;

    @ViewById(R.id.header)
    View mHeaderView;
    @ViewById(R.id.drawer_menu)
    RecyclerView mDrawerMenu;

    private final DrawerMenuItem mAccessibilityServiceItem = new DrawerMenuItem(R.drawable.ic_accessibility_black_48dp, R.string.text_a11y_service, 0, this::enableOrDisableAccessibilityService);
    private final DrawerMenuItem mForegroundServiceItem = new DrawerMenuItem(R.drawable.ic_service_green, R.string.text_foreground_service, R.string.key_foreground_service, this::toggleForegroundService);

    private final DrawerMenuItem mFloatingWindowItem = new DrawerMenuItem(R.drawable.ic_robot_64, R.string.text_floating_window, 0, this::showOrDismissFloatingWindow);

    private final DrawerMenuItem mClientModeItem = new DrawerMenuItem(R.drawable.ic_computer_black_48dp, R.string.text_client_mode, 0, this::toggleRemoteServerCxn);
    private final DrawerMenuItem mServerModeItem = new DrawerMenuItem(R.drawable.ic_smartphone_black_48dp, R.string.text_server_mode, 0, this::toggleLocalServerCxn);

    private final DrawerMenuItem mNotificationPermissionItem = new DrawerMenuItem(R.drawable.ic_ali_notification, R.string.text_notification_permission, 0, this::goToNotificationServiceSettings);
    private final DrawerMenuItem mUsageStatsPermissionItem = new DrawerMenuItem(R.drawable.ic_assessment_black_48dp, R.string.text_usage_stats_permission, 0, this::goToUsageStatsSettings);
    private final DrawerMenuItem mIgnoreBatteryOptimizationsItem = new DrawerMenuItem(R.drawable.ic_battery_std_black_48dp, R.string.text_ignore_battery_optimizations, 0, this::toggleIgnoreBatteryOptimizations);
    private final DrawerMenuItem mDisplayOverOtherAppsItem = new DrawerMenuItem(R.drawable.ic_layers_black_48dp, R.string.text_display_over_other_app, 0, this::goToDisplayOverOtherAppsSettings);
    private final DrawerMenuItem mWriteSystemSettingsItem = new DrawerMenuItem(R.drawable.ic_settings_black_48dp, R.string.text_write_system_settings, 0, this::goToWriteSystemSettings);
    private final DrawerMenuItem mWriteSecuritySettingsItem = new DrawerMenuItem(R.drawable.ic_security_black_48dp, R.string.text_write_secure_settings, 0, this::toggleWriteSecureSettings);
    private final DrawerMenuItem mProjectMediaAccessItem = new DrawerMenuItem(R.drawable.ic_cast_connected_black_48dp, R.string.text_project_media_access, 0, this::toggleProjectMediaAccess);

    private final DevPluginService devPlugin = DevPluginService.getInstance();

    private DrawerMenuAdapter mDrawerMenuAdapter;
    private Disposable mClientConnectionStateDisposable;
    private Disposable mServerConnectionStateDisposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mClientConnectionStateDisposable = JsonSocketClient.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> setCxnItemState(mClientModeItem, state));

        mServerConnectionStateDisposable = JsonSocketServer.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> setCxnItemState(mServerModeItem, state));

        EventBus.getDefault().register(this);

        mContext = requireContext();
        mPackageName = mContext.getPackageName();
        mAccessibilityServiceTool = new AccessibilityServiceTool(mContext);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncSwitchState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mClientConnectionStateDisposable.dispose();
        mServerConnectionStateDisposable.dispose();
        EventBus.getDefault().unregister(this);
    }

    @AfterViews
    public void setUpViews() {
        ThemeColorManager.addViewBackground(mHeaderView);

        initMenuItems();

        mAccessibilityServiceTool.enableAccessibilityServiceAutomaticallyIfNeeded();

        if (Pref.isFloatingMenuShown()) {
            FloatyWindowManger.showCircularMenuIfNeeded();
            setChecked(mFloatingWindowItem, true);
        }

        if (Pref.isForegroundServiceEnabled()) {
            MainActivityForegroundService.start(mContext);
            syncForegroundServiceState();
        }
    }

    private void initMenuItems() {
        mDrawerMenuAdapter = new DrawerMenuAdapter(new ArrayList<>(Arrays.asList(
                new DrawerMenuGroup(R.string.text_service),
                mAccessibilityServiceItem,
                mForegroundServiceItem,

                new DrawerMenuGroup(R.string.text_tools),
                mFloatingWindowItem,

                new DrawerMenuGroup(R.string.text_connect_to_pc),
                mClientModeItem,
                mServerModeItem,

                new DrawerMenuGroup(R.string.text_permissions),
                mNotificationPermissionItem,
                mUsageStatsPermissionItem,
                mIgnoreBatteryOptimizationsItem,
                mDisplayOverOtherAppsItem,
                mWriteSystemSettingsItem,
                mWriteSecuritySettingsItem,
                mProjectMediaAccessItem,

                new DrawerMenuGroup(R.string.text_appearance),
                new DrawerMenuItem(R.drawable.ic_night_mode, R.string.text_night_mode, R.string.key_night_mode, this::toggleNightMode),
                new DrawerMenuItem(R.drawable.ic_personalize, R.string.text_theme_color, this::openThemeColorSettings)
        )));
        mDrawerMenu.setAdapter(mDrawerMenuAdapter);
        mDrawerMenu.setLayoutManager(new LinearLayoutManager(mContext));
    }

    public void enableOrDisableAccessibilityService(@NonNull DrawerMenuItemViewHolder holder) {
        boolean isAccessibilityServiceEnabled = mAccessibilityServiceTool.isAccessibilityServiceEnabled();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !isAccessibilityServiceEnabled) {
            enableAccessibilityService();
        } else if (!checked && isAccessibilityServiceEnabled) {
            disableAccessibilityService();
        }
    }

    @SuppressLint("CheckResult")
    private void enableAccessibilityService() {
        setProgress(mAccessibilityServiceItem, true);

        Observable.fromCallable(mAccessibilityServiceTool::enableAccessibilityService)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(succeed -> {
                    if (!succeed) {
                        mAccessibilityServiceTool.goToAccessibilitySetting();
                    }
                    setProgress(mAccessibilityServiceItem, false);
                    syncAccessibilityServiceState();
                });
    }

    @SuppressLint("CheckResult")
    private void disableAccessibilityService() {
        setProgress(mAccessibilityServiceItem, true);

        Observable.fromCallable(mAccessibilityServiceTool::disableAccessibilityService)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(succeed -> {
                    if (!succeed) {
                        toast(R.string.text_disable_a11y_service_failed);
                        mAccessibilityServiceTool.goToAccessibilitySetting();
                    }
                    setProgress(mAccessibilityServiceItem, false);
                    syncAccessibilityServiceState();
                });
    }

    private void enableAccessibilityServiceIfNeeded() {
        if (!mAccessibilityServiceTool.isAccessibilityServiceEnabled()) {
            enableAccessibilityService();
        }
    }

    private void toggleForegroundService(@NonNull DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked) {
            if (getForegroundServiceDialog() == null) {
                MainActivityForegroundService.start(mContext);
            }
        } else {
            MainActivityForegroundService.stop(mContext);
        }
    }

    private MaterialDialog getForegroundServiceDialog() {
        return new NotAskAgainDialog.Builder(mContext, "DrawerFragment.foreground_service")
                .title(R.string.text_foreground_service)
                .content(R.string.text_foreground_service_description)
                .negativeText(R.string.text_back)
                .positiveText(R.string.text_continue)
                .onNegative((dialog, which) -> {
                    dialog.dismiss();
                    syncForegroundServiceState();
                })
                .onPositive((dialog, which) -> MainActivityForegroundService.start(mContext))
                .canceledOnTouchOutside(false)
                .cancelable(false)
                .show();
    }

    public void goToNotificationServiceSettings(@NonNull DrawerMenuItemViewHolder holder) {
        boolean enabled = NotificationListenerService.Companion.getInstance() != null;
        boolean checked = holder.getSwitchCompat().isChecked();
        if ((checked && !enabled) || (!checked && enabled)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    public void goToUsageStatsSettings(@NonNull DrawerMenuItemViewHolder holder) {
        Context context = requireContext();
        boolean enabled = AppOpsKt.isUsageStatsPermissionGranted(context);
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !enabled) {
            if (getUsageStatsDialog() == null) {
                requestAppUsagePermission();
            }
        } else if (!checked && enabled) {
            requestAppUsagePermission();
        }
    }

    private void requestAppUsagePermission() {
        IntentUtil.requestAppUsagePermission(mContext);
    }

    private MaterialDialog getUsageStatsDialog() {
        return new NotAskAgainDialog.Builder(mContext, "DrawerFragment.usage_stats")
                .title(R.string.text_usage_stats_permission)
                .content(R.string.text_usage_stats_permission_description)
                .negativeText(R.string.text_back)
                .positiveText(R.string.text_continue)
                .onNegative((dialog, which) -> {
                    dialog.dismiss();
                    syncUsageStatsPermissionState();
                })
                .onPositive((dialog, which) -> requestAppUsagePermission())
                .canceledOnTouchOutside(false)
                .cancelable(false)
                .show();
    }

    public void showOrDismissFloatingWindow(@NonNull DrawerMenuItemViewHolder holder) {
        boolean isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (getActivity() != null && !getActivity().isFinishing()) {
            Pref.setFloatingMenuShown(checked);
        }
        if (checked && !isFloatingWindowShowing) {
            if (FloatyWindowManger.showCircularMenu()) {
                setChecked(mFloatingWindowItem, true);
                enableAccessibilityServiceIfNeeded();
            }
        } else if (!checked && isFloatingWindowShowing) {
            FloatyWindowManger.hideCircularMenu();
        }
    }

    @SuppressLint("BatteryLife")
    public void toggleIgnoreBatteryOptimizations(DrawerMenuItemViewHolder holder) {
        try {
            Intent intent = new Intent();
            boolean isIgnoring = isIgnoringBatteryOptimizations();
            if (isIgnoring) {
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + mPackageName));
            }
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            toast(R.string.text_failed);
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        return ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(mPackageName);
    }

    public void openThemeColorSettings(DrawerMenuItemViewHolder holder) {
        SettingsActivity.selectThemeColor(getActivity());
    }

    public void toggleNightMode(@NonNull DrawerMenuItemViewHolder holder) {
        ((BaseActivity) requireActivity()).setNightModeEnabled(holder.getSwitchCompat().isChecked());
    }

    public void goToDisplayOverOtherAppsSettings(@NonNull DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked != canDrawOverlays()) {
            FloatingPermission.manageDrawOverlays(mContext);
        }
    }

    private boolean canDrawOverlays() {
        return FloatingPermission.canDrawOverlays(mContext);
    }

    public void goToWriteSystemSettings(@NonNull DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked != Settings.System.canWrite(mContext)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void toggleWriteSecureSettings(@NonNull DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        boolean hasAccess = hasWriteSecureSettingsAccess();
        if (checked && !hasAccess) {
            if (getWriteSecureSettingsDialog() == null) {
                grantWriteSecureSettingsAccess();
            }
        } else if (!checked && hasAccess) {
            revokeWriteSecureSettingsAccess();
        }
    }

    private MaterialDialog getWriteSecureSettingsDialog() {
        return new NotAskAgainDialog.Builder(mContext, "DrawerFragment.write_secure_settings")
                .title(R.string.text_write_secure_settings)
                .content(R.string.text_write_secure_settings_description)
                .negativeText(R.string.text_back)
                .positiveText(R.string.text_continue)
                .onNegative((dialog, which) -> {
                    dialog.dismiss();
                    syncWriteSecuritySettingsState();
                })
                .onPositive((dialog, which) -> grantWriteSecureSettingsAccess())
                .canceledOnTouchOutside(false)
                .cancelable(false)
                .show();
    }

    private void toggleProjectMediaAccess(@NonNull DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        boolean hasAccess = hasProjectMediaAccess();
        if (checked && !hasAccess) {
            if (getProjectMediaAccessDialog() == null) {
                grantProjectMediaAccess();
            }
        } else if (!checked && hasAccess) {
            revokeProjectMediaAccess();
        }
    }

    private MaterialDialog getProjectMediaAccessDialog() {
        return new NotAskAgainDialog.Builder(mContext, "DrawerFragment.project_media_access")
                .title(R.string.text_project_media_access)
                .content(R.string.text_project_media_access_description)
                .negativeText(R.string.text_back)
                .positiveText(R.string.text_continue)
                .onNegative((dialog, which) -> {
                    dialog.dismiss();
                    syncProjectMediaAccessState();
                })
                .onPositive((dialog, which) -> grantProjectMediaAccess())
                .canceledOnTouchOutside(false)
                .cancelable(false)
                .show();
    }

    private boolean hasWriteSecureSettingsAccess() {
        return SettingsTool.SecureSettings.isGranted(mContext);
    }

    private boolean hasProjectMediaAccess() {
        return AppOpsKt.isProjectMediaAccessGranted(mContext);
    }

    @SuppressLint("CheckResult")
    private void grantWriteSecureSettingsAccess() {
        setProgress(mWriteSecuritySettingsItem, true);

        Callable<Boolean> grant = () -> {
            if (!RootTool.isRootAvailable()) {
                return true;
            }
            setWriteSecureSettingsAccessByRoot(true);
            return false;
        };

        Observable.fromCallable(grant)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isAdbNeeded -> {
                    setProgress(mWriteSecuritySettingsItem, false);
                    if (isAdbNeeded) {
                        setWriteSecureSettingsAccessByAdb(true);
                    } else {
                        syncWriteSecuritySettingsState();
                    }
                });
    }

    @SuppressLint("CheckResult")
    private void revokeWriteSecureSettingsAccess() {
        setProgress(mWriteSecuritySettingsItem, true);

        Callable<Boolean> revoke = () -> {
            if (!RootTool.isRootAvailable()) {
                return true;
            }
            setWriteSecureSettingsAccessByRoot(false);
            return false;
        };

        Observable.fromCallable(revoke)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isAdbNeeded -> {
                    setProgress(mWriteSecuritySettingsItem, false);
                    if (isAdbNeeded) {
                        setWriteSecureSettingsAccessByAdb(false);
                    } else {
                        syncWriteSecuritySettingsState();
                    }
                });
    }

    @SuppressLint("CheckResult")
    private void grantProjectMediaAccess() {
        setProgress(mProjectMediaAccessItem, true);

        Callable<Boolean> grant = () -> {
            if (!RootTool.isRootAvailable()) {
                return true;
            }
            setProjectMediaAccessByRoot(true);
            return false;
        };
        Observable.fromCallable(grant)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isAdbNeeded -> {
                    setProgress(mProjectMediaAccessItem, false);
                    if (isAdbNeeded) {
                        setProjectMediaAccessByAdb(true);
                    } else {
                        syncProjectMediaAccessState();
                    }
                });
    }

    @SuppressLint("CheckResult")
    private void revokeProjectMediaAccess() {
        setProgress(mProjectMediaAccessItem, true);

        Callable<Boolean> revoke = () -> {
            if (!RootTool.isRootAvailable()) {
                return true;
            }
            setProjectMediaAccessByRoot(false);
            return false;
        };

        Observable.fromCallable(revoke)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isAdbNeeded -> {
                    setProgress(mProjectMediaAccessItem, false);
                    if (isAdbNeeded) {
                        setProjectMediaAccessByAdb(false);
                    } else {
                        syncProjectMediaAccessState();
                    }
                });
    }

    private void setWriteSecureSettingsAccessByRoot(boolean state) {
        new WriteSecureSettings(state).byRoot();
    }

    private void setWriteSecureSettingsAccessByAdb(boolean state) {
        new WriteSecureSettings(state).byAdb();
    }

    private void setProjectMediaAccessByRoot(boolean state) {
        new ProjectMediaAccess(state).byRoot();
    }

    private void setProjectMediaAccessByAdb(boolean state) {
        new ProjectMediaAccess(state).byAdb();
    }

    private void toggleRemoteServerCxn(@NonNull DrawerMenuItemViewHolder holder) {
        boolean isChecked = holder.getSwitchCompat().isChecked();
        boolean isDisconnected = !isJsonSocketClientConnected();
        if (isChecked && isDisconnected) {
            inputRemoteHost();
        } else {
            disconnectJsonSocketClientIFN();
        }
    }

    private void disconnectJsonSocketClientIFN() {
        JsonSocketClient client = devPlugin.getJsonSocketClient();
        if (client != null) {
            try {
                client.switchOff();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isJsonSocketClientConnected() {
        JsonSocketClient client = devPlugin.getJsonSocketClient();
        return client != null && client.isSocketReady();
    }

    @SuppressLint("CheckResult")
    private void toggleLocalServerCxn(@NonNull DrawerMenuItemViewHolder holder) {
        boolean isChecked = holder.getSwitchCompat().isChecked();
        boolean isDisconnected = !isServerSocketConnected();

        if (isChecked && isDisconnected) {
            devPlugin
                    .enableLocalServer()
                    .subscribe(Observers.emptyConsumer(), this::onAJServerConnectException);
        } else {
            disconnectServerSocketIFN();
        }
    }

    private boolean isServerSocketConnected() {
        ServerSocket serverSocket = devPlugin.getServerSocket();
        return serverSocket != null && !serverSocket.isClosed();
    }

    private void disconnectServerSocketIFN() {
        ServerSocket serverSocket = devPlugin.getServerSocket();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressLint("CheckResult")
    private void inputRemoteHost() {
        Context activity = getActivity();
        String host = Pref.getServerAddressOrDefault(WifiTool.getRouterIp(Objects.requireNonNull(activity)));
        new MaterialDialog.Builder(activity)
                .title(R.string.text_pc_server_address)
                .input(getInputHint(), host, (dialog, input) -> {
                    Pref.saveServerAddress(input.toString());
                    devPlugin.connectToRemoteServer(input.toString())
                            .subscribe(Observers.emptyConsumer(), this::onPCServerConnectException);
                })
                .neutralText(R.string.text_help)
                .negativeText(R.string.text_back)
                .onNeutral((dialog, which) -> {
                    setChecked(mClientModeItem, false);
                    IntentUtil.browse(activity, URL_DEV_PLUGIN);
                })
                .onNegative((dialog, which) -> dialog.dismiss())
                .cancelListener(dialog -> setChecked(mClientModeItem, false))
                .show();
    }

    private String getInputHint() {
        Context context = requireContext();
        return context.getString(R.string.text_pc_server_address);
    }

    private void onPCServerConnectException(@NonNull Throwable e) {
        setChecked(mClientModeItem, false);
        toast(getString(R.string.error_connect_to_remote, e.getMessage()));
    }

    private void onAJServerConnectException(@NonNull Throwable e) {
        setChecked(mServerModeItem, false);
        toast(getString(R.string.error_enable_server, e.getMessage()));
    }

    public void syncSwitchState() {
        syncAccessibilityServiceState();
        syncForegroundServiceState();

        syncFloatingWindowState();

        syncClientModeState();
        syncServerModeState();

        syncNotificationPermissionState();
        syncUsageStatsPermissionState();
        syncIgnoreBatteryOptimizationsState();
        syncDisplayOverOtherAppsState();
        syncWriteSystemSettingsState();
        syncWriteSecuritySettingsState();
        syncProjectMediaAccessState();
    }

    private void syncForegroundServiceState() {
        setCheckedIfNeeded(mForegroundServiceItem, ForegroundServiceUtils.isRunning(mContext, MainActivityForegroundService.class));
    }

    private void syncWriteSecuritySettingsState() {
        setCheckedIfNeeded(mWriteSecuritySettingsItem, hasWriteSecureSettingsAccess());
    }

    private void syncProjectMediaAccessState() {
        setCheckedIfNeeded(mProjectMediaAccessItem, hasProjectMediaAccess());
    }

    private void syncWriteSystemSettingsState() {
        setCheckedIfNeeded(mWriteSystemSettingsItem, Settings.System.canWrite(getContext()));
    }

    private void syncDisplayOverOtherAppsState() {
        setCheckedIfNeeded(mDisplayOverOtherAppsItem, canDrawOverlays());
    }

    private void syncIgnoreBatteryOptimizationsState() {
        setCheckedIfNeeded(mIgnoreBatteryOptimizationsItem, isIgnoringBatteryOptimizations());
    }

    private void syncUsageStatsPermissionState() {
        setCheckedIfNeeded(mUsageStatsPermissionItem, AppOpsKt.isUsageStatsPermissionGranted(mContext));
    }

    private void syncNotificationPermissionState() {
        setCheckedIfNeeded(mNotificationPermissionItem, NotificationListenerService.Companion.getInstance() != null);
    }

    private void syncServerModeState() {
        setCheckedIfNeeded(mServerModeItem, isServerSocketConnected());
    }

    private void syncClientModeState() {
        if (devPlugin.getJsonSocketClient() != null) {
            setCheckedIfNeeded(mClientModeItem, isJsonSocketClientConnected());
        }
    }

    private void syncFloatingWindowState() {
        setCheckedIfNeeded(mFloatingWindowItem, FloatyWindowManger.isCircularMenuShowing() && canDrawOverlays());
    }

    private void syncAccessibilityServiceState() {
        setCheckedIfNeeded(mAccessibilityServiceItem, mAccessibilityServiceTool.isAccessibilityServiceEnabled());
    }

    @Subscribe
    public void onCircularMenuStateChange(@NonNull CircularMenu.StateChangeEvent event) {
        setCheckedIfNeeded(mFloatingWindowItem, event.getCurrentState() != CircularMenu.STATE_CLOSED);
    }

    @Subscribe
    public void onDrawerOpened(Class<DrawerFragment> drawerFragmentClass) {
        syncSwitchState();
    }

    private void toast(CharSequence text) {
        toast(mContext, text);
    }

    private void toast(int resId) {
        toast(mContext, resId);
    }

    private void toast(Context context, CharSequence text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private void toast(Context context, int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }

    private void setProgress(@NonNull DrawerMenuItem item, boolean progress) {
        item.setProgress(progress);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private void setCheckedIfNeeded(@NonNull DrawerMenuItem item, boolean b) {
        if (item.isChecked() != b) {
            setChecked(item, b);
        }
    }

    private void setChecked(@NonNull DrawerMenuItem item, boolean checked) {
        item.setChecked(checked);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private void setCxnItemState(DrawerMenuItem item, @NonNull DevPluginService.State state) {
        setCheckedIfNeeded(item, state.getState() == DevPluginService.State.CONNECTED);
        setProgress(item, state.getState() == DevPluginService.State.CONNECTING);
        if (state.getException() != null) {
            toast(state.getException().getMessage());
        }
    }

    // @RefactorNeeded by SuperMonster003 on May 3, 2022.
    private class WriteSecureSettings implements PermissionTool.ByRoot, PermissionTool.ByAdb {

        private final boolean mState;
        private final String mCommand;

        WriteSecureSettings(boolean state) {
            mState = state;
            mCommand = "pm " + (state ? "grant" : "revoke") + " " + mContext.getPackageName() + " " + WRITE_SECURE_SETTINGS_PERMISSION;
        }

        @Override
        public void byRoot() {
            try {
                ProcessShell.execCommand(mCommand, true);
                if (mState == hasWriteSecureSettingsAccess()) {
                    GlobalAppContext.toast(mState ? R.string.text_permission_granted_with_root : R.string.text_permission_revoked_with_root);
                    return;
                }
            } catch (Exception ignore) {
                // nothing to do here
            }
            GlobalAppContext.toast(mState ? R.string.text_permission_granted_failed_with_root : R.string.text_permission_revoked_failed_with_root);
        }

        @Override
        public void byAdb() {
            new PermissionTool.AdbMaterialDialog(mContext, mCommand)
                    .setChecker(DrawerFragment.this::hasWriteSecureSettingsAccess)
                    .setDismissListener(() -> dialog -> setCheckedIfNeeded(mWriteSecuritySettingsItem, hasWriteSecureSettingsAccess()))
                    .show();
        }

    }

    // @RefactorNeeded by SuperMonster003 on May 3, 2022.
    private class ProjectMediaAccess implements PermissionTool.ByRoot, PermissionTool.ByAdb {

        private final boolean mState;
        private final String mCommand;

        ProjectMediaAccess(boolean state) {
            mState = state;
            mCommand = "appops set " + mContext.getPackageName() + " " + PROJECT_MEDIA_PERMISSION + " " + (state ? "allow" : "ignore");
        }

        @Override
        public void byAdb() {
            new PermissionTool.AdbMaterialDialog(mContext, mCommand)
                    .setChecker(DrawerFragment.this::hasProjectMediaAccess)
                    .setDismissListener(() -> dialog -> setCheckedIfNeeded(mProjectMediaAccessItem, hasProjectMediaAccess()))
                    .show();
        }

        @Override
        public void byRoot() {
            try {
                ProcessShell.execCommand(mCommand, true);
                if (mState == hasProjectMediaAccess()) {
                    GlobalAppContext.toast(mState ? R.string.text_permission_granted_with_root : R.string.text_permission_revoked_with_root);
                    return;
                }
            } catch (Exception ignore) {
                // nothing to do here
            }
            GlobalAppContext.toast(mState ? R.string.text_permission_granted_failed_with_root : R.string.text_permission_revoked_failed_with_root);
        }
    }

}
