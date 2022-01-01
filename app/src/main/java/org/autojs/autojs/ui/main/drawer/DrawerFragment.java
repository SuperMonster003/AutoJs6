package org.autojs.autojs.ui.main.drawer;

import static android.content.Context.POWER_SERVICE;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.stardust.app.AppOpsKt;
import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.util.ProcessShell;
import com.stardust.autojs.util.FloatingPermission;
import com.stardust.notification.NotificationListenerService;
import com.stardust.theme.ThemeColorManager;
import com.stardust.util.ClipboardUtil;
import com.stardust.util.IntentUtil;
import com.stardust.view.accessibility.AccessibilityService;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.Pref;
import org.autojs.autojs.R;
import org.autojs.autojs.external.foreground.ForegroundService;
import org.autojs.autojs.pluginclient.DevPluginService;
import org.autojs.autojs.pluginclient.JsonSocketClient;
import org.autojs.autojs.pluginclient.JsonSocketServer;
import org.autojs.autojs.tool.AccessibilityServiceTool;
import org.autojs.autojs.tool.Observers;
import org.autojs.autojs.tool.RootTool;
import org.autojs.autojs.tool.WifiTool;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.floating.CircularMenu;
import org.autojs.autojs.ui.floating.FloatyWindowManger;
import org.autojs.autojs.ui.settings.SettingsActivity;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

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

    @ViewById(R.id.header)
    View mHeaderView;
    @ViewById(R.id.drawer_menu)
    RecyclerView mDrawerMenu;

    private final DrawerMenuItem mAccessibilityServiceItem = new DrawerMenuItem(R.drawable.ic_accessibility_black_48dp, R.string.text_accessibility_service, 0, this::enableOrDisableAccessibilityService);
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

    private final DevPluginService devPlugin = DevPluginService.getInstance();

    private DrawerMenuAdapter mDrawerMenuAdapter;
    private Disposable mClientConnectionStateDisposable;
    private Disposable mServerConnectionStateDisposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mClientConnectionStateDisposable = JsonSocketClient.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> setItemState(mClientModeItem, state));

        mServerConnectionStateDisposable = JsonSocketServer.cxnState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> setItemState(mServerModeItem, state));

        EventBus.getDefault().register(this);
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
        if (Pref.isFloatingMenuShown()) {
            FloatyWindowManger.showCircularMenuIfNeeded();
            setChecked(mFloatingWindowItem, true);
        }

        JsonSocketClient jsonSocketClient = devPlugin.getJsonSocketClient();
        if (jsonSocketClient != null) {
            setChecked(mClientModeItem, jsonSocketClient.isConnected());
        }

        if (Pref.isForegroundServiceEnabled()) {
            ForegroundService.start(GlobalAppContext.get());
            setChecked(mForegroundServiceItem, true);
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

                new DrawerMenuGroup(R.string.text_permission),
                mNotificationPermissionItem,
                mUsageStatsPermissionItem,
                mIgnoreBatteryOptimizationsItem,
                mDisplayOverOtherAppsItem,
                mWriteSystemSettingsItem,
                mWriteSecuritySettingsItem,

                new DrawerMenuGroup(R.string.text_appearance),
                new DrawerMenuItem(R.drawable.ic_night_mode, R.string.text_night_mode, R.string.key_night_mode, this::toggleNightMode),
                new DrawerMenuItem(R.drawable.ic_personalize, R.string.text_theme_color, this::openThemeColorSettings)
        )));
        mDrawerMenu.setAdapter(mDrawerMenuAdapter);
        mDrawerMenu.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public void enableOrDisableAccessibilityService(DrawerMenuItemViewHolder holder) {
        boolean isAccessibilityServiceEnabled = isAccessibilityServiceEnabled();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !isAccessibilityServiceEnabled) {
            enableAccessibilityService();
        } else if (!checked && isAccessibilityServiceEnabled) {
            disableAccessibilityService();
        }
    }

    public void goToNotificationServiceSettings(DrawerMenuItemViewHolder holder) {
        boolean enabled = NotificationListenerService.Companion.getInstance() != null;
        boolean checked = holder.getSwitchCompat().isChecked();
        if ((checked && !enabled) || (!checked && enabled)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    public void goToUsageStatsSettings(DrawerMenuItemViewHolder holder) {
        Context context = getContext();
        boolean enabled = false;
        if (context != null) {
            enabled = AppOpsKt.isUsageStatsPermissionGranted(context);
        }
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !enabled) {
            if (getUsageStatsDialog(context) == null) {
                syncSwitchState();
            }
        } else if (!checked && enabled) {
            IntentUtil.requestAppUsagePermission(context);
        }
    }

    private MaterialDialog getUsageStatsDialog(Context context) {
        return new NotAskAgainDialog.Builder(context, "DrawerFragment.usage_stats")
                .title(R.string.text_usage_stats_permission)
                .content(R.string.description_usage_stats_permission)
                .positiveText(R.string.ok)
                .dismissListener(dialog -> {
                    if (context != null) {
                        IntentUtil.requestAppUsagePermission(context);
                    }
                })
                .show();
    }

    public void showOrDismissFloatingWindow(DrawerMenuItemViewHolder holder) {
        boolean isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (getActivity() != null && !getActivity().isFinishing()) {
            Pref.setFloatingMenuShown(checked);
        }
        if (checked && !isFloatingWindowShowing) {
            setChecked(mFloatingWindowItem, FloatyWindowManger.showCircularMenu());
            enableAccessibilityServiceByRootIfNeeded();
        } else if (!checked && isFloatingWindowShowing) {
            FloatyWindowManger.hideCircularMenu();
        }
    }

    @SuppressLint("BatteryLife")
    public void toggleIgnoreBatteryOptimizations(DrawerMenuItemViewHolder holder) {
        Context context = getContext();
        try {
            Intent intent = new Intent();
            boolean isIgnoring = false;
            if (context != null) {
                isIgnoring = ((PowerManager) context.getSystemService(POWER_SERVICE))
                        .isIgnoringBatteryOptimizations(context.getPackageName());
            }
            if (isIgnoring) {
                intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else {
                if (context != null) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:" + context.getPackageName()));
                }
            }
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.text_failed, Toast.LENGTH_LONG).show();
        }
    }

    public void openThemeColorSettings(DrawerMenuItemViewHolder holder) {
        SettingsActivity.selectThemeColor(getActivity());
    }

    public void toggleNightMode(DrawerMenuItemViewHolder holder) {
        ((BaseActivity) requireActivity()).setNightModeEnabled(holder.getSwitchCompat().isChecked());
    }

    public void goToDisplayOverOtherAppsSettings(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        Context context = getContext();
        if (checked != FloatingPermission.canDrawOverlays(context)) {
            FloatingPermission.manageDrawOverlays(context);
        }
    }

    public void goToWriteSystemSettings(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked != Settings.System.canWrite(getContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    private void toggleWriteSecureSettings(DrawerMenuItemViewHolder holder) {
        if (holder.getSwitchCompat().isChecked() && !hasWriteSecureSettingsAccess()) {
            grantWriteSecureSettingsAccess();
        } else if (!holder.getSwitchCompat().isChecked() && hasWriteSecureSettingsAccess()) {
            revokeWriteSecureSettingsAccess();
        }
    }

    private boolean hasWriteSecureSettingsAccess() {
        Context context = getContext();
        if (context != null) {
            @SuppressLint("WrongConstant")
            int checkVal = context.checkCallingOrSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION);
            return checkVal == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    @SuppressLint("CheckResult")
    private void grantWriteSecureSettingsAccess() {
        Observable.fromCallable(RootTool::isRootAvailable)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasRoot -> {
                    if (hasRoot) {
                        setWriteSecureSettingsAccessByRoot(true);
                    } else {
                        setWriteSecureSettingsAccessByAdb(true);
                    }
                });
    }

    @SuppressLint("CheckResult")
    private void revokeWriteSecureSettingsAccess() {
        Observable.fromCallable(RootTool::isRootAvailable)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasRoot -> {
                    if (hasRoot) {
                        setWriteSecureSettingsAccessByRoot(false);
                    } else {
                        setWriteSecureSettingsAccessByAdb(false);
                    }
                });
    }

    private void setWriteSecureSettingsAccessByRoot(boolean state) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String cmdAction = state ? "grant" : "revoke";
        String cmd = "pm " + cmdAction + " " + context.getPackageName() + " " + WRITE_SECURE_SETTINGS_PERMISSION;
        try {
            ProcessShell.execCommand(cmd, true);
            if (state == hasWriteSecureSettingsAccess()) {
                int successRes = state ? R.string.text_permission_granted_by_root : R.string.text_permission_revoked_by_root;
                Toast.makeText(context, successRes, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception ignore) {
            // nothing to do here
        }
        int successRes = state ? R.string.text_permission_granted_failed_by_root : R.string.text_permission_revoked_failed_by_root;
        Toast.makeText(context, successRes, Toast.LENGTH_LONG).show();
    }

    private void setWriteSecureSettingsAccessByAdb(boolean state) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        final int SNACK_BAR_DURATION = 1000;
        String scriptAction = state ? "grant" : "revoke";
        String script = "adb shell pm " + scriptAction + " " + context.getPackageName() + " " + WRITE_SECURE_SETTINGS_PERMISSION;

        new MaterialDialog.Builder(context)
                .title(R.string.text_adb_tool_needed)
                .content(script)
                .neutralText(R.string.text_permission_test)
                .onNeutral((dialog, which) -> {
                    View view = dialog.getView();
                    int resultRes = hasWriteSecureSettingsAccess() ? R.string.text_granted : R.string.text_not_granted;
                    if (view != null) {
                        Snackbar.make(view, resultRes, SNACK_BAR_DURATION).show();
                    } else {
                        Toast.makeText(context, resultRes, Toast.LENGTH_SHORT).show();
                    }
                })
                .negativeText(R.string.text_back)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.text_copy_command)
                .onPositive((dialog, which) -> {
                    ClipboardUtil.setClip(context, script);
                    View view = dialog.getView();
                    int textRes = R.string.text_command_already_copied_to_clip;
                    if (view != null) {
                        Snackbar.make(view, textRes, SNACK_BAR_DURATION).show();
                    } else {
                        Toast.makeText(context, textRes, Toast.LENGTH_SHORT).show();
                    }
                })
                .cancelable(false)
                .autoDismiss(false)
                .dismissListener(dialog -> setChecked(mWriteSecuritySettingsItem, hasWriteSecureSettingsAccess()))
                .show();

    }

    @SuppressLint("CheckResult")
    private void enableAccessibilityServiceByRootIfNeeded() {
        Observable.fromCallable(() -> Pref.shouldEnableAccessibilityServiceByRoot() && !isAccessibilityServiceEnabled())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(needed -> {
                    if (needed && RootTool.isRootAvailable()) {
                        enableAccessibilityServiceByRoot();
                    }
                });

    }

    private void toggleRemoteServerCxn(DrawerMenuItemViewHolder holder) throws IOException {
        JsonSocketClient jsonSocketClient = devPlugin.getJsonSocketClient();
        boolean disconnected = jsonSocketClient == null || !jsonSocketClient.isSocketReady();
        boolean checked = holder.getSwitchCompat().isChecked();

        if (checked) {
            if (disconnected) {
                inputRemoteHost();
            }
        } else {
            if (jsonSocketClient != null) {
                jsonSocketClient.switchOff();
            }
        }
    }

    @SuppressLint("CheckResult")
    private void toggleLocalServerCxn(DrawerMenuItemViewHolder holder) throws IOException {
        JsonSocketServer jsonSocketServer = devPlugin.getJsonSocketServer();
        boolean checked = holder.getSwitchCompat().isChecked();

        if (checked) {
            devPlugin.enableLocalServer()
                    .subscribe(Observers.emptyConsumer(), this::onAJServerConnectException);
        } else {
            if (jsonSocketServer != null) {
                jsonSocketServer.switchOff();
            }
        }
    }

    private void toggleForegroundService(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked) {
            ForegroundService.start(GlobalAppContext.get());
        } else {
            ForegroundService.stop(GlobalAppContext.get());
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
                .onNeutral((dialog, which) -> {
                    setChecked(mClientModeItem, false);
                    IntentUtil.browse(activity, URL_DEV_PLUGIN);
                })
                .cancelListener(dialog -> setChecked(mClientModeItem, false))
                .show();
    }

    private String getInputHint() {
        Context context = getContext();
        if (context != null) {
            return context.getString(R.string.text_pc_server_address);
        }
        return "Input a server address";
    }

    private void onPCServerConnectException(Throwable e) {
        setChecked(mClientModeItem, false);
        Toast.makeText(getContext(),
                getString(R.string.error_connect_to_remote, e.getMessage()),
                Toast.LENGTH_LONG).show();
    }

    private void onAJServerConnectException(Throwable e) {
        setChecked(mServerModeItem, false);
        Toast.makeText(getContext(),
                getString(R.string.error_enable_server, e.getMessage()),
                Toast.LENGTH_LONG).show();
    }

    private void syncSwitchState() {
        Context context = getContext();

        if (context != null) {
            setChecked(mUsageStatsPermissionItem, AppOpsKt.isUsageStatsPermissionGranted(context));
        }

        if (mFloatingWindowItem.isChecked() && !FloatingPermission.canDrawOverlays(context)) {
            setChecked(mFloatingWindowItem, false);
        }

        if (context != null) {
            setChecked(mIgnoreBatteryOptimizationsItem, ((PowerManager) context.getSystemService(POWER_SERVICE))
                    .isIgnoringBatteryOptimizations(context.getPackageName()));
        }

        setChecked(mAccessibilityServiceItem, AccessibilityServiceTool.isAccessibilityServiceEnabled(getActivity()));
        setChecked(mNotificationPermissionItem, NotificationListenerService.Companion.getInstance() != null);
        setChecked(mDisplayOverOtherAppsItem, FloatingPermission.canDrawOverlays(context));
        setChecked(mWriteSystemSettingsItem, Settings.System.canWrite(getContext()));
        setChecked(mWriteSecuritySettingsItem, hasWriteSecureSettingsAccess());
    }

    private boolean isAccessibilityServiceEnabled() {
        return AccessibilityServiceTool.isAccessibilityServiceEnabled(getActivity());
    }

    private void enableAccessibilityService() {
        if (Pref.shouldEnableAccessibilityServiceByRoot() && RootTool.isRootAvailable()) {
            enableAccessibilityServiceByRoot();
        } else {
            AccessibilityServiceTool.goToAccessibilitySetting();
        }
    }

    private void disableAccessibilityService() {
        if (!AccessibilityService.Companion.disable()) {
            AccessibilityServiceTool.goToAccessibilitySetting();
        }
    }

    @SuppressLint("CheckResult")
    private void enableAccessibilityServiceByRoot() {
        setProgress(mAccessibilityServiceItem, true);
        Observable.fromCallable(() -> AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(4000))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(succeed -> {
                    if (!succeed) {
                        Toast.makeText(getContext(), R.string.text_enable_accessibility_service_by_root_failed, Toast.LENGTH_SHORT).show();
                        AccessibilityServiceTool.goToAccessibilitySetting();
                    }
                    setProgress(mAccessibilityServiceItem, false);
                });
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onCircularMenuStateChange(CircularMenu.StateChangeEvent event) {
        setChecked(mFloatingWindowItem, event.getCurrentState() != CircularMenu.STATE_CLOSED);
    }

    private void showMessage(CharSequence text) {
        if (getContext() == null)
            return;
        Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
    }

    private void setProgress(DrawerMenuItem item, boolean progress) {
        item.setProgress(progress);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private void setChecked(DrawerMenuItem item, boolean checked) {
        item.setChecked(checked);
        mDrawerMenuAdapter.notifyItemChanged(item);
    }

    private void setItemState(DrawerMenuItem item, DevPluginService.State state) {
        setChecked(item, state.getState() == DevPluginService.State.CONNECTED);
        setProgress(item, state.getState() == DevPluginService.State.CONNECTING);
        if (state.getException() != null) {
            showMessage(state.getException().getMessage());
        }
    }
}
