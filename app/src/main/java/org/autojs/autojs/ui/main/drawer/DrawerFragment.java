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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by Stardust on Jan 30, 2017.
 * Modified by SuperMonster003 on Nov 16, 2021.
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

    private final DrawerMenuItem mNotificationPermissionItem = new DrawerMenuItem(R.drawable.ic_ali_notification, R.string.text_notification_permission, 0, this::goToNotificationServiceSettings);
    private final DrawerMenuItem mUsageStatsPermissionItem = new DrawerMenuItem(R.drawable.ic_assessment_black_48dp, R.string.text_usage_stats_permission, 0, this::goToUsageStatsSettings);
    private final DrawerMenuItem mIgnoreBatteryOptimizationsItem = new DrawerMenuItem(R.drawable.ic_battery_std_black_48dp, R.string.text_ignore_battery_optimizations, 0, this::toggleIgnoreBatteryOptimizations);
    private final DrawerMenuItem mDisplayOverOtherAppsItem = new DrawerMenuItem(R.drawable.ic_layers_black_48dp, R.string.text_display_over_other_app, 0, this::goToDisplayOverOtherAppsSettings);
    private final DrawerMenuItem mWriteSystemSettingsItem = new DrawerMenuItem(R.drawable.ic_settings_black_48dp, R.string.text_write_system_settings, 0, this::goToWriteSystemSettings);
    private final DrawerMenuItem mWriteSecuritySettingsItem = new DrawerMenuItem(R.drawable.ic_security_black_48dp, R.string.text_write_secure_settings, 0, this::toggleWriteSecureSettings);

    private final DrawerMenuItem mFloatingWindowItem = new DrawerMenuItem(R.drawable.ic_robot_64, R.string.text_floating_window, 0, this::showOrDismissFloatingWindow);
    private final DrawerMenuItem mConnectionItem = new DrawerMenuItem(R.drawable.ic_computer_black_48dp, R.string.debug, 0, this::connectOrDisconnectToRemote);

    private DrawerMenuAdapter mDrawerMenuAdapter;
    private Disposable mConnectionStateDisposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnectionStateDisposable = DevPluginService.getInstance().connectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    setChecked(mConnectionItem, state.getState() == DevPluginService.State.CONNECTED);
                    setProgress(mConnectionItem, state.getState() == DevPluginService.State.CONNECTING);
                    if (state.getException() != null) {
                        showMessage(state.getException().getMessage());
                    }
                });
        EventBus.getDefault().register(this);
    }

    @AfterViews
    void setUpViews() {
        ThemeColorManager.addViewBackground(mHeaderView);
        initMenuItems();
        if (Pref.isFloatingMenuShown()) {
            FloatyWindowManger.showCircularMenuIfNeeded();
            setChecked(mFloatingWindowItem, true);
        }
        setChecked(mConnectionItem, DevPluginService.getInstance().isConnected());
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

                new DrawerMenuGroup(R.string.text_permission),
                mNotificationPermissionItem,
                mUsageStatsPermissionItem,
                mIgnoreBatteryOptimizationsItem,
                mDisplayOverOtherAppsItem,
                mWriteSystemSettingsItem,
                mWriteSecuritySettingsItem,

                new DrawerMenuGroup(R.string.text_tools),
                mFloatingWindowItem,
                mConnectionItem,

                new DrawerMenuGroup(R.string.text_appearance),
                new DrawerMenuItem(R.drawable.ic_night_mode, R.string.text_night_mode, R.string.key_night_mode, this::toggleNightMode),
                new DrawerMenuItem(R.drawable.ic_personalize, R.string.text_theme_color, this::openThemeColorSettings)
        )));
        mDrawerMenu.setAdapter(mDrawerMenuAdapter);
        mDrawerMenu.setLayoutManager(new LinearLayoutManager(getContext()));
    }


    void enableOrDisableAccessibilityService(DrawerMenuItemViewHolder holder) {
        boolean isAccessibilityServiceEnabled = isAccessibilityServiceEnabled();
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !isAccessibilityServiceEnabled) {
            enableAccessibilityService();
        } else if (!checked && isAccessibilityServiceEnabled) {
            if (!AccessibilityService.Companion.disable()) {
                AccessibilityServiceTool.goToAccessibilitySetting();
            }
        }
    }

    void goToNotificationServiceSettings(DrawerMenuItemViewHolder holder) {
        boolean enabled = NotificationListenerService.Companion.getInstance() != null;
        boolean checked = holder.getSwitchCompat().isChecked();
        if ((checked && !enabled) || (!checked && enabled)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    void goToUsageStatsSettings(DrawerMenuItemViewHolder holder) {
        Context context = getContext();
        boolean enabled = false;
        if (context != null) {
            enabled = AppOpsKt.isUsageStatsPermissionGranted(context);
        }
        boolean checked = holder.getSwitchCompat().isChecked();
        if (checked && !enabled) {
            if (new NotAskAgainDialog.Builder(context, "DrawerFragment.usage_stats")
                    .title(R.string.text_usage_stats_permission)
                    .content(R.string.description_usage_stats_permission)
                    .positiveText(R.string.ok)
                    .dismissListener(dialog -> {
                        if (context != null) {
                            IntentUtil.requestAppUsagePermission(context);
                        }
                    })
                    .show() == null) {
                if (context != null) {
                    IntentUtil.requestAppUsagePermission(context);
                }
            }
        }
        if (!checked && enabled) {
            IntentUtil.requestAppUsagePermission(context);
        }
    }

    void showOrDismissFloatingWindow(DrawerMenuItemViewHolder holder) {
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
    void toggleIgnoreBatteryOptimizations(DrawerMenuItemViewHolder holder) {
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

    void openThemeColorSettings(DrawerMenuItemViewHolder holder) {
        SettingsActivity.selectThemeColor(getActivity());
    }

    void toggleNightMode(DrawerMenuItemViewHolder holder) {
        ((BaseActivity) requireActivity()).setNightModeEnabled(holder.getSwitchCompat().isChecked());
    }

    void goToDisplayOverOtherAppsSettings(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        Context context = getContext();
        if (checked != FloatingPermission.canDrawOverlays(context)) {
            FloatingPermission.manageDrawOverlays(context);
        }
    }

    void goToWriteSystemSettings(DrawerMenuItemViewHolder holder) {
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
        final int SNACKBAR_DURATION = 1000;
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
                        Snackbar.make(view, resultRes, SNACKBAR_DURATION).show();
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
                        Snackbar.make(view, textRes, SNACKBAR_DURATION).show();
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
                    if (needed) {
                        enableAccessibilityServiceByRoot();
                    }
                });

    }

    void connectOrDisconnectToRemote(DrawerMenuItemViewHolder holder) {
        boolean checked = holder.getSwitchCompat().isChecked();
        boolean connected = DevPluginService.getInstance().isConnected();
        if (checked && !connected) {
            inputRemoteHost();
        } else if (!checked && connected) {
            DevPluginService.getInstance().disconnectIfNeeded();
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
                .title(R.string.text_server_address)
                .input("", host, (dialog, input) -> {
                    Pref.saveServerAddress(input.toString());
                    DevPluginService.getInstance().connectToServer(input.toString())
                            .subscribe(Observers.emptyConsumer(), this::onConnectException);
                })
                .neutralText(R.string.text_help)
                .onNeutral((dialog, which) -> {
                    setChecked(mConnectionItem, false);
                    IntentUtil.browse(activity, URL_DEV_PLUGIN);
                })
                .cancelListener(dialog -> setChecked(mConnectionItem, false))
                .show();
    }

    private void onConnectException(Throwable e) {
        setChecked(mConnectionItem, false);
        Toast.makeText(GlobalAppContext.get(), getString(R.string.error_connect_to_remote, e.getMessage()),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncSwitchState();
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

    private void enableAccessibilityService() {
        if (!Pref.shouldEnableAccessibilityServiceByRoot()) {
            AccessibilityServiceTool.goToAccessibilitySetting();
            return;
        }
        enableAccessibilityServiceByRoot();
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


    @Subscribe
    public void onCircularMenuStateChange(CircularMenu.StateChangeEvent event) {
        setChecked(mFloatingWindowItem, event.getCurrentState() != CircularMenu.STATE_CLOSED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mConnectionStateDisposable.dispose();
        EventBus.getDefault().unregister(this);
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

    private boolean isAccessibilityServiceEnabled() {
        return AccessibilityServiceTool.isAccessibilityServiceEnabled(getActivity());
    }
}
