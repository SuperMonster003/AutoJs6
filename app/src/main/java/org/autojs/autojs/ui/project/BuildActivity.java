package org.autojs.autojs.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.textfield.TextInputLayout;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import org.autojs.autojs.apkbuilder.ApkBuilder;
import org.autojs.autojs.apkbuilder.keystore.KeyStore;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.external.fileprovider.AppFileProvider;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.runtime.api.AppUtils;
import org.autojs.autojs.runtime.api.AppUtils.Companion.SimpleVersionInfo;
import org.autojs.autojs.runtime.api.augment.pinyin.Pinyin;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.error.ErrorDialogActivity;
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder;
import org.autojs.autojs.ui.keystore.ManageKeyStoreActivity;
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager;
import org.autojs.autojs.ui.shortcut.AppsIconSelectActivity;
import org.autojs.autojs.ui.viewmodel.KeyStoreViewModel;
import org.autojs.autojs.ui.widget.RoundCheckboxWithText;
import org.autojs.autojs.util.AndroidUtils;
import org.autojs.autojs.util.AndroidUtils.Abi;
import org.autojs.autojs.util.BitmapUtils;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.StringUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityBuildBinding;
import org.autojs.autojs6.databinding.DialogBuildProgressBinding;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.autojs.autojs.apkbuilder.ApkBuilder.TEMPLATE_APK_NAME;
import static org.autojs.autojs.util.StringUtils.key;

/**
 * Created by Stardust on Oct 22, 2017.
 * Modified by SuperMonster003 as of Jan 6, 2026.
 *
 * @noinspection ResultOfMethodCallIgnored, unused
 */
public class BuildActivity extends BaseActivity implements ApkBuilder.ProgressCallback {

    private static final int REQUEST_CODE = 44401;
    public static final String EXTRA_SOURCE = BuildActivity.class.getName() + ".extra_source_file";
    private static final String LOG_TAG = "BuildActivity";
    private static final Pattern REGEX_PACKAGE_NAME = Pattern.compile("^([A-Za-z][A-Za-z\\d_]*\\.)+([A-Za-z][A-Za-z\\d_]*)$");

    private static final ArrayList<String> SUPPORTED_ABIS = new ArrayList<>() {{
        add(Abi.ARM64_V8A);
        add(Abi.X86_64);
        add(Abi.ARMEABI_V7A);
        add(Abi.X86);
        add(Abi.ARMEABI);
    }};

    private static final Map<String, List<String>> ABI_ALIASES = new HashMap<>() {{
        put(Abi.ARM64_V8A, /* "arm64-v8a" */ List.of("arm64_v8a", "arm64_v8", "arm64_8", "arm_v8a", "v8a", "arm_v8", "v8", "arm_8", "8", "arm64-v8a", "arm64-v8", "arm64-8", "arm-v8a", "arm-v8", "arm-8", "arm64v8a", "64v8a", "arm64v8", "64v8", "arm648", "armv8a", "armv8", "arm8", "a64_v8a", "a64_v8", "a64_8", "a_v8a", "a_v8", "a_8", "a64-v8a", "a64-v8", "a64-8", "a-v8a", "a-v8", "a-8", "a64v8a", "a64v8", "a648", "av8a", "av8", "a8"));
        put(Abi.X86_64, /* x86_64 */ List.of("x86_64", "x8664", "86_64", "8664"));
        put(Abi.ARMEABI_V7A, /* armeabi-v7a */ List.of("armeabi_v7a", "armeabi_v7", "armeabi_7", "arme_v7a", "arme_v7", "arme_7", "arm_v7a", "v7a", "arm_v7", "v7", "arm_7", "7", "armeabi-v7a", "armeabi-v7", "armeabi-7", "arme-v7a", "arme-v7", "arme-7", "arm-v7a", "arm-v7", "arm-7", "armeabiv7a", "armeabiv7", "armeabi7", "armev7a", "armev7", "arme7", "armv7a", "armv7", "arm7", "a_v7a", "a_v7", "a_7", "a-v7a", "a-v7", "a-7", "av7a", "av7", "a7"));
        put(Abi.X86, /* x86 */ List.of("x86", "86"));
        put(Abi.ARMEABI, /* armeabi */ List.of("armeabi", "arme", "armv5te", "arm5te", "armv5", "v5", "arm5", "5"));
    }};

    private static final ArrayList<String> SUPPORTED_LIBS = new ArrayList<>();

    private static final Map<String, List<String>> LIB_ALIASES = new HashMap<>();

    private static final List<Pair<String, String>> SIGNATURE_SCHEMES = new ArrayList<>() {{
        add(new Pair<>("V1 + V2", null));
        add(new Pair<>("V1 + V3", null));
        add(new Pair<>("V1 + V2 + V3", null));
        add(new Pair<>("V1", null));
        add(new Pair<>("V2 + V3", "Android 7.0+"));
        add(new Pair<>("V2", "Android 7.0+"));
        add(new Pair<>("V3", "Android 9.0+"));
    }};

    private final Map<String, String> PERMISSION_ALIAS = new HashMap<>() {{
        put("shizuku", "moe.shizuku.manager.permission.API_V23");
        put("termux", "com.termux.permission.RUN_COMMAND");
    }};

    private final Map<String, Integer> SUPPORTED_PERMISSIONS = new TreeMap<>() {{
        put("android.permission.ACCESS_COARSE_LOCATION", R.string.text_permission_desc_access_coarse_location);
        put("android.permission.ACCESS_FINE_LOCATION", R.string.text_permission_desc_access_fine_location);
        put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", R.string.text_permission_desc_access_location_extra_commands);
        put("android.permission.ACCESS_NETWORK_STATE", R.string.text_permission_desc_access_network_state);
        put("android.permission.ACCESS_WIFI_STATE", R.string.text_permission_desc_access_wifi_state);
        put("android.permission.BLUETOOTH", R.string.text_permission_desc_bluetooth);
        put("android.permission.BLUETOOTH_ADMIN", R.string.text_permission_desc_bluetooth_admin);
        put("android.permission.BLUETOOTH_CONNECT", R.string.text_permission_desc_bluetooth_connect);
        put("android.permission.BLUETOOTH_SCAN", R.string.text_permission_desc_bluetooth_scan);
        put("android.permission.BLUETOOTH_ADVERTISE", R.string.text_permission_desc_bluetooth_advertise);
        put("android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS", R.string.text_permission_desc_broadcast_close_system_dialogs);
        put("android.permission.CALL_PHONE", R.string.text_permission_desc_call_phone);
        put("android.permission.CAMERA", R.string.text_permission_desc_camera);
        put("android.permission.CAPTURE_VIDEO_OUTPUT", R.string.text_permission_desc_capture_video_output);
        put("android.permission.CHANGE_NETWORK_STATE", R.string.text_permission_desc_change_network_state);
        put("android.permission.CHANGE_WIFI_MULTICAST_STATE", R.string.text_permission_desc_change_wifi_multicast_state);
        put("android.permission.CHANGE_WIFI_STATE", R.string.text_permission_desc_change_wifi_state);
        put("android.permission.DISABLE_KEYGUARD", R.string.text_permission_desc_disable_keyguard);
        put("android.permission.DUMP", R.string.text_permission_desc_dump);
        put("android.permission.EXPAND_STATUS_BAR", R.string.text_permission_desc_expand_status_bar);
        put("android.permission.FLASHLIGHT", R.string.text_permission_desc_flashlight);
        put("android.permission.FOREGROUND_SERVICE", R.string.text_permission_desc_foreground_service);
        put("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION", R.string.text_permission_desc_foreground_service_media_projection);
        put("android.permission.FOREGROUND_SERVICE_SPECIAL_USE", R.string.text_permission_desc_foreground_service_special_use);
        put("android.permission.GET_ACCOUNTS", R.string.text_permission_desc_get_accounts);
        put("android.permission.INTERNET", R.string.text_permission_desc_internet);
        put("android.permission.INTERACT_ACROSS_USERS_FULL", R.string.text_permission_desc_interact_across_users_full);
        put("android.permission.KILL_BACKGROUND_PROCESSES", R.string.text_permission_desc_kill_background_processes);
        put("android.permission.MANAGE_EXTERNAL_STORAGE", R.string.text_permission_desc_manage_external_storage);
        put("android.permission.MANAGE_USERS", R.string.text_permission_desc_manage_users);
        put("android.permission.MODIFY_AUDIO_SETTINGS", R.string.text_permission_desc_modify_audio_settings);
        put("android.permission.MOUNT_FORMAT_FILESYSTEMS", R.string.text_permission_desc_mount_format_filesystems);
        put("android.permission.MOUNT_UNMOUNT_FILESYSTEMS", R.string.text_permission_desc_mount_unmount_filesystems);
        put("android.permission.NFC", R.string.text_permission_desc_nfc);
        put("android.permission.POST_NOTIFICATIONS", R.string.text_permission_desc_post_notifications);
        put("android.permission.QUERY_ALL_PACKAGES", R.string.text_permission_desc_query_all_packages);
        put("android.permission.READ_CALENDAR", R.string.text_permission_desc_read_calendar);
        put("android.permission.READ_CONTACTS", R.string.text_permission_desc_read_contacts);
        put("android.permission.READ_EXTERNAL_STORAGE", R.string.text_permission_desc_read_external_storage);
        put("android.permission.READ_MEDIA_AUDIO", R.string.text_permission_desc_read_media_audio);
        put("android.permission.READ_MEDIA_IMAGES", R.string.text_permission_desc_read_media_images);
        put("android.permission.READ_MEDIA_VIDEO", R.string.text_permission_desc_read_media_video);
        put("android.permission.READ_PHONE_STATE", R.string.text_permission_desc_read_phone_state);
        put("android.permission.READ_PRIVILEGED_PHONE_STATE", R.string.text_permission_desc_read_privileged_phone_state);
        put("android.permission.READ_SMS", R.string.text_permission_desc_read_sms);
        put("android.permission.RECEIVE_BOOT_COMPLETED", R.string.text_permission_desc_receive_boot_completed);
        put("android.permission.RECEIVE_SMS", R.string.text_permission_desc_receive_sms);
        put("android.permission.RECORD_AUDIO", R.string.text_permission_desc_record_audio);
        put("android.permission.REORDER_TASKS", R.string.text_permission_desc_reorder_tasks);
        put("android.permission.REQUEST_DELETE_PACKAGES", R.string.text_permission_desc_request_delete_packages);
        put("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", R.string.text_permission_desc_request_ignore_battery_optimizations);
        put("android.permission.REQUEST_INSTALL_PACKAGES", R.string.text_permission_desc_request_install_packages);
        put("android.permission.SCHEDULE_EXACT_ALARM", R.string.text_permission_desc_schedule_exact_alarm);
        put("android.permission.SEND_SMS", R.string.text_permission_desc_send_sms);
        put("android.permission.SET_WALLPAPER", R.string.text_permission_desc_set_wallpaper);
        put("android.permission.SET_WALLPAPER_HINTS", R.string.text_permission_desc_set_wallpaper_hints);
        put("android.permission.SYSTEM_ALERT_WINDOW", R.string.text_permission_desc_system_alert_window);
        put("android.permission.UNLIMITED_TOASTS", R.string.text_permission_desc_unlimited_toasts);
        put("android.permission.USE_EXACT_ALARM", R.string.text_permission_desc_use_exact_alarm);
        put("android.permission.VIBRATE", R.string.text_permission_desc_vibrate);
        put("android.permission.WAKE_LOCK", R.string.text_permission_desc_wake_lock);
        put("android.permission.WRITE_CALENDAR", R.string.text_permission_desc_write_calendar);
        put("android.permission.WRITE_CONTACTS", R.string.text_permission_desc_write_contacts);
        put("android.permission.WRITE_EXTERNAL_STORAGE", R.string.text_permission_desc_write_external_storage);
        put("android.permission.WRITE_SECURE_SETTINGS", R.string.text_permission_desc_write_secure_settings);
        put("android.permission.WRITE_SETTINGS", R.string.text_permission_desc_write_settings);
        put("com.android.launcher.permission.INSTALL_SHORTCUT", R.string.text_permission_desc_install_shortcut);
        put("com.android.launcher.permission.UNINSTALL_SHORTCUT", R.string.text_permission_desc_uninstall_shortcut);
        put("com.android.vending.BILLING", R.string.text_permission_desc_billing);
        put(PERMISSION_ALIAS.get("termux"), R.string.text_permission_desc_termux_run_command);
        put(PERMISSION_ALIAS.get("shizuku"), R.string.text_permission_desc_shizuku);
    }};

    EditText mSourcePathView;
    View mSourcePathContainerView;
    EditText mOutputPathView;
    EditText mAppNameView;
    EditText mPackageNameView;
    TextInputLayout mPackageNameParentView;
    EditText mVersionNameView;
    TextInputLayout mVersionNameParentView;
    EditText mVersionCodeView;
    TextInputLayout mVersionCodeParentView;
    ImageView mIconView;
    LinearLayout mAppConfigView;

    @Nullable
    private ProjectConfig mProjectConfig;

    private MaterialDialog mProgressDialog;
    private ImageView mProgressPrepareIcon;
    private TextView mProgressPrepareText;
    private ImageView mProgressBuildIcon;
    private TextView mProgressBuildText;
    private ImageView mProgressSignIcon;
    private TextView mProgressSignText;
    private ImageView mProgressCleanIcon;
    private TextView mProgressCleanText;
    private TextView mProgressPrepareDurationText;
    private TextView mProgressBuildDurationText;
    private TextView mProgressSignDurationText;
    private TextView mProgressCleanDurationText;
    private TextView mStateTitleText;
    private TextView mStateContentText;
    private final EnumMap<BuildStep, Long> mStepDurationMs = new EnumMap<>(BuildStep.class);
    @Nullable
    private BuildStep mCurrentTimingStep;
    private long mCurrentTimingStepStartedAtMs;
    // Track cancellation state for cooperative build abort.
    // zh-CN: 跟踪取消状态, 用于协作式中止构建.
    private final AtomicBoolean mBuildCancelled = new AtomicBoolean(false);
    @Nullable
    private File mBuildWorkspace;
    @Nullable
    private File mBuildOutputApk;
    @Nullable
    private File mBuildOutputApkBackup;
    @Nullable
    private Thread mBuildThread;
    private String mSource;
    private boolean mIsDefaultIcon = true;
    private boolean mIsProjectLevelBuilding;

    private FlexboxLayout mFlexboxAbisView;
    private FlexboxLayout mFlexboxLibsView;
    private Spinner mSignatureSchemesView;
    private Spinner mVerifiedKeyStoresView;
    private FlexboxLayout mFlexboxPermissionsView;

    private final ArrayList<String> mInvalidAbis = new ArrayList<>();
    private final ArrayList<String> mUnavailableAbis = new ArrayList<>();
    private final ArrayList<String> mUnavailableStandardAbis = new ArrayList<>();
    private final ArrayList<String> mInvalidLibs = new ArrayList<>();
    private final ArrayList<String> mUnavailableLibs = new ArrayList<>();

    private KeyStoreViewModel mKeyStoreViewModel;

    static {
        for (ApkBuilder.Lib entry : ApkBuilder.Lib.getEntries()) {
            if (entry.enumerable) {
                SUPPORTED_LIBS.add(entry.label);
                LIB_ALIASES.put(entry.label, entry.aliases);
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityBuildBinding binding = ActivityBuildBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mSourcePathView = binding.sourcePath;
        mSourcePathView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mOutputPathView.requestFocus();
                }
                return true;
            }
            return false;
        });

        mSourcePathContainerView = binding.sourcePathContainer;

        mOutputPathView = binding.outputPath;
        mOutputPathView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    TextView nextField = (TextView) mOutputPathView.focusSearch(View.FOCUS_DOWN);
                    if (nextField != null) {
                        nextField.requestFocus();
                    }
                }
                return true;
            }
            return false;
        });

        mAppNameView = binding.appName;

        mPackageNameView = binding.packageName;
        mPackageNameView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    mVersionNameView.requestFocus();
                }
                return true;
            }
            return false;
        });
        mPackageNameParentView = binding.packageNameParent;

        mVersionNameView = binding.versionName;
        mVersionNameParentView = binding.versionNameParent;
        mVersionCodeView = binding.versionCode;
        mVersionCodeParentView = binding.versionCodeParent;

        mIconView = binding.appIcon;
        mIconView.setVisibility(View.INVISIBLE);
        mIconView.setOnClickListener(v -> selectIcon());

        mAppConfigView = binding.appConfig;

        mFlexboxAbisView = binding.flexboxAbis;
        initAbisChildren();

        mFlexboxLibsView = binding.flexboxLibraries;
        initLibsChildren();

        mKeyStoreViewModel = new ViewModelProvider(this, new KeyStoreViewModel.Factory(getApplicationContext())).get(KeyStoreViewModel.class);
        mKeyStoreViewModel.updateVerifiedKeyStores();

        mSignatureSchemesView = binding.spinnerSignatureSchemes;
        mVerifiedKeyStoresView = binding.spinnerVerifiedKeyStores;
        mFlexboxPermissionsView = binding.flexboxPermissions;

        binding.fab.setOnClickListener(v -> buildApk());
        ViewUtils.excludeFloatingActionButtonFromBottomNavigationBar(binding.fab);

        ViewUtils.excludePaddingClippableViewFromBottomNavigationBar(binding.scrollView);

        binding.selectSource.setOnClickListener(v -> selectSourceFilePath());
        binding.selectOutput.setOnClickListener(v -> selectOutputDirPath());
        binding.textAbis.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxAbisView));
        binding.textAbis.setOnLongClickListener(v -> {
            syncAbisCheckedStates();
            return true;
        });
        binding.textLibs.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxLibsView));
        binding.manageKeyStore.setOnClickListener(v -> ManageKeyStoreActivity.Companion.startActivity(this));
        binding.textPermissions.setOnClickListener(v -> toggleAllFlexboxChildren(mFlexboxPermissionsView));

        setToolbarAsBack(R.string.text_build_apk);
        mSource = getIntent().getStringExtra(EXTRA_SOURCE);
        if (mSource != null) {
            setupWithSourceFile(new ScriptFile(mSource));
        }

        initSignatureSchemeSpinner();
        initVerifiedKeyStoresSpinner();
        initPermissionsChildren();

        syncAbisCheckedStates();
        syncLibsCheckedStates();

        showHintDialogIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mKeyStoreViewModel.updateVerifiedKeyStores();
    }

    private void toggleAllFlexboxChildren(FlexboxLayout mFlexboxLibs) {
        boolean isAllChecked = true;
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!child.isEnabled()) {
                    continue;
                }
                if (!((RoundCheckboxWithText) child).isChecked()) {
                    isAllChecked = false;
                    break;
                }
            } else if (child instanceof CheckBox) {
                if (!child.isEnabled()) {
                    continue;
                }
                if (!((CheckBox) child).isChecked()) {
                    isAllChecked = false;
                    break;
                }
            }
        }
        for (int i = 0; i < mFlexboxLibs.getChildCount(); i += 1) {
            View child = mFlexboxLibs.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (!child.isEnabled()) {
                    continue;
                }
                ((RoundCheckboxWithText) child).setChecked(!isAllChecked);
            } else if (child instanceof CheckBox) {
                if (!child.isEnabled()) {
                    continue;
                }
                ((CheckBox) child).setChecked(!isAllChecked);
            }
        }
    }

    private void initAbisChildren() {
        SUPPORTED_ABIS.forEach((abiText) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(abiText);
            child.setChecked(false);
            child.setEnabled(false);
            child.setOnBeingUnavailableListener(this::promptForUnavailability);
            mFlexboxAbisView.addView(child);
        });
    }

    private void promptForUnavailability(RoundCheckboxWithText view) {
        CharSequence abiText = view.getText();
        Context context = BuildActivity.this;
        String key = key(R.string.key_dialog_selected_abi_is_unavailable);
        NotAskAgainDialog.Builder builder = new NotAskAgainDialog.Builder(context, key);
        builder.title(R.string.text_prompt);
        builder.content(getString(R.string.text_unable_to_build_apk_as_autojs6_does_not_include_selected_abi, abiText) + "\n\n" +
                        getString(R.string.text_the_following_solutions_can_be_referred_to) + ":\n\n" +
                        "- " + getString(R.string.text_download_and_install_autojs6_including_above_abi, abiText) + "\n" +
                        "- " + getString(R.string.text_download_and_install_autojs6_including_all_abis) + " [" + getString(R.string.text_recommended) + "]\n\n" +
                        getString(R.string.text_download_link_for_autojs6) + ":\n" +
                        getString(R.string.uri_autojs6_download_link));
        builder.positiveText(R.string.dialog_button_dismiss);
        builder.positiveColorRes(R.color.dialog_button_hint);
        DialogUtils.widgetThemeColor(builder);
        MaterialDialog dialog = builder.show();
        if (dialog != null) {
            TextView contentView = dialog.getContentView();
            if (contentView != null) {
                Linkify.addLinks(contentView, Pattern.compile("https?://.*"), null);
            }
        } else {
            ViewUtils.showToast(context, getString(R.string.text_unavailable_abi_for, abiText));
        }
    }

    private void syncAbisCheckedStates() {
        if (mProjectConfig != null) {
            List<String> projectConfigAbis = mProjectConfig.getAbis();
            if (!projectConfigAbis.isEmpty()) {
                var candidates = new ArrayList<>(projectConfigAbis);
                syncAbisWithDefaultCheckedFilter(standardAbi -> isAliasMatching(ABI_ALIASES, standardAbi, candidates));
                mInvalidAbis.addAll(candidates);
                return;
            }
        }
        syncAbisWithDefaultCheckedFilter(standardAbi -> AndroidUtils.getDeviceFilteredAbiList().contains(standardAbi));
    }

    private void syncAbisWithDefaultCheckedFilter(Function<String, Boolean> filterForDefaultChecked) {
        List<String> appSupportedAbiList = AndroidUtils.getAppSupportedAbiList();
        for (int i = 0; i < mFlexboxAbisView.getChildCount(); i += 1) {
            View child = mFlexboxAbisView.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                CharSequence standardAbi = ((RoundCheckboxWithText) child).getText();
                if (standardAbi != null) {
                    boolean isEnabled = appSupportedAbiList.contains(standardAbi.toString());
                    boolean isDefaultChecked = filterForDefaultChecked.apply(standardAbi.toString());
                    child.setEnabled(isEnabled);
                    ((RoundCheckboxWithText) child).setChecked(isEnabled && isDefaultChecked);
                    if (isDefaultChecked && !isEnabled) {
                        mUnavailableStandardAbis.add(standardAbi.toString());
                    }
                }
            }
        }
    }

    private void initLibsChildren() {
        SUPPORTED_LIBS.forEach((text) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(text);
            child.setChecked(false);
            mFlexboxLibsView.addView(child);
        });
    }

    private void syncLibsCheckedStates() {
        if (mProjectConfig == null) return;

        var configLibs = mProjectConfig.getLibs();
        if (configLibs.isEmpty()) return;

        // 创建一个新的副本
        var candidates = new ArrayList<>(configLibs);

        for (int i = 0; i < mFlexboxLibsView.getChildCount(); i += 1) {
            View child = mFlexboxLibsView.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                CharSequence standardLib = ((RoundCheckboxWithText) child).getText();
                if (standardLib != null) {
                    boolean shouldChecked = isAliasMatching(LIB_ALIASES, standardLib.toString(), candidates);
                    ((RoundCheckboxWithText) child).setChecked(shouldChecked);
                }
            }
        }

        mInvalidLibs.addAll(candidates);
    }

    private void initSignatureSchemeSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SIGNATURE_SCHEMES.stream().map(pair -> {
            if (pair.second == null || pair.second.isEmpty()) {
                return pair.first;
            }
            return pair.first + " (" + pair.second + ")";
        }).collect(Collectors.toList()));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSignatureSchemesView.setAdapter(adapter);
        if (mProjectConfig != null) {
            int initialSelection = IntStream.range(0, SIGNATURE_SCHEMES.size())
                    .filter(i -> SIGNATURE_SCHEMES.get(i).first.equalsIgnoreCase(mProjectConfig.getSignatureScheme()))
                    .findFirst()
                    .orElse(-1);
            if (initialSelection >= 0) {
                mSignatureSchemesView.setSelection(initialSelection);
            }
        }
    }

    private void initVerifiedKeyStoresSpinner() {
        ArrayList<KeyStore> verifiedKeyStores = new ArrayList<>();
        // 添加 默认密钥库 下拉选项
        KeyStore defaultKeyStore = new KeyStore("", getString(R.string.text_default_key_store), "", "", "", false); // 仅用于显示下拉列表
        verifiedKeyStores.add(defaultKeyStore);

        ArrayAdapter<KeyStore> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, verifiedKeyStores);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mVerifiedKeyStoresView.setAdapter(adapter);

        mKeyStoreViewModel.getVerifiedKeyStores().observe(this, keyStores -> {
            // 清空现有的选项, 但保留第一个元素, 即默认密钥库
            if (verifiedKeyStores.size() > 1) {
                verifiedKeyStores.subList(1, verifiedKeyStores.size()).clear();
            }
            verifiedKeyStores.addAll(keyStores);
            adapter.notifyDataSetChanged();
        });
    }

    @SuppressLint("SetTextI18n")
    private void initPermissionsChildren() {
        SUPPORTED_PERMISSIONS.forEach((permission, descriptionResId) -> {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            checkBox.setAlpha(0.87f);
            checkBox.setText(permission + "\n" + getString(descriptionResId));
            checkBox.setButtonDrawable(R.drawable.round_checkbox);
            checkBox.setBackground(null);
            checkBox.setGravity(Gravity.CENTER_VERTICAL);
            checkBox.setTextSize(12);
            int marginInPixels = (int) (8 * getResources().getDisplayMetrics().density);
            checkBox.setPadding(marginInPixels, 0, 0, 0);
            List<String> permissions = mProjectConfig != null
                    ? mProjectConfig.getPermissions()
                    : ProjectConfig.DEFAULT_PERMISSIONS;
            boolean checked = hasPermission(permissions, permission);
            checkBox.setChecked(checked);
            mFlexboxPermissionsView.addView(checkBox);
        });
    }

    private boolean hasPermission(List<String> permissions, String permission) {
        return permissions.stream().anyMatch(p -> {
            var lc = p.toLowerCase(Locale.ROOT);
            var uc = p.toUpperCase(Locale.ROOT);
            if (p.equalsIgnoreCase(permission)) {
                return true;
            }
            if (permission.contains("android")) {
                String refined = uc.substring(uc.lastIndexOf(".") + 1).replaceAll("\\W", "_");
                return Objects.equals(refined, permission.substring(permission.lastIndexOf(".") + 1));
            }
            if (PERMISSION_ALIAS.containsKey(lc)) {
                return Objects.equals(permission, PERMISSION_ALIAS.get(lc));
            }
            return false;
        });
    }

    private boolean isAliasMatching(Map<String, List<String>> aliases, String aliasKey, List<String> candidates) {
        AtomicBoolean result = new AtomicBoolean(false);
        var aliasList = aliases.getOrDefault(aliasKey, Collections.emptyList());
        ArrayList<String> aliasesToCheck = new ArrayList<>(aliasList);
        aliasesToCheck.add(aliasKey);
        aliasesToCheck.forEach(alias -> {
            if (containsIgnoreCase(candidates, alias)) {
                candidates.remove(alias);
                result.set(true);
            }
        });
        return result.get();
    }

    private boolean containsIgnoreCase(List<String> list, String s) {
        return list.stream().anyMatch(item -> item.equalsIgnoreCase(s));
    }

    @SuppressLint("CheckResult")
    private void setupWithSourceFile(ScriptFile file) {
        String dir = file.getParent();
        if (dir != null && dir.startsWith(getFilesDir().getPath())) {
            dir = WorkingDirectoryUtils.getPath();
        }
        mOutputPathView.setText(dir);
        mAppNameView.setText(file.getSimplifiedName());

        Observable.fromCallable(() -> {
                    String packageNameSuffix = generatePackageNameSuffix(file);
                    return getString(R.string.format_default_package_name, packageNameSuffix);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(packageName -> {
                    mPackageNameView.setText(packageName);
                    mPackageNameParentView.setHint(R.string.text_package_name);

                    SimpleVersionInfo nextVersionInfo = AppUtils.generateNextVersionInfo(packageName);
                    if (nextVersionInfo != null) {
                        mVersionNameView.setText(nextVersionInfo.versionName);
                        mVersionCodeView.setText(nextVersionInfo.versionCodeString);
                    } else {
                        mVersionNameView.setText(R.string.default_build_apk_version_name);
                        mVersionCodeView.setText(R.string.default_build_apk_version_code);
                    }
                    mVersionNameParentView.setHint(R.string.text_version_name);
                    mVersionCodeParentView.setHint(R.string.text_version_code);

                    Drawable iconDrawable = AppUtils.getInstalledAppIcon(packageName);
                    if (iconDrawable != null) {
                        mIconView.setImageDrawable(iconDrawable);
                        mIsDefaultIcon = false;
                    }
                    mIconView.setVisibility(View.VISIBLE);

                    updatePermissionsCheckboxes(packageName);
                }, throwable -> {
                    mPackageNameView.setText(getString(R.string.format_default_package_name, file.getSimplifiedName().toLowerCase(Language.getPrefLanguage().getLocale())));
                    mVersionNameView.setText(R.string.default_build_apk_version_name);
                    mVersionCodeView.setText(R.string.default_build_apk_version_code);
                    mPackageNameParentView.setHint(R.string.text_package_name);
                    mVersionNameParentView.setHint(R.string.text_version_name);
                    mVersionCodeParentView.setHint(R.string.text_version_code);
                    mIconView.setVisibility(View.VISIBLE);
                });

        setSource(file);
    }

    private void updatePermissionsCheckboxes(String packageName) {
        ApkFile apkFile;
        try {
            apkFile = new ApkFile(new File(getPackageManager().getApplicationInfo(packageName, 0).sourceDir));
        } catch (PackageManager.NameNotFoundException | IOException e) {
            return;
        }

        try {
            ApkMeta meta = apkFile.getApkMeta();
            if (meta == null) {
                return;
            }
            var initialPermissions = meta.usesPermissions;
            IntStream.range(0, mFlexboxPermissionsView.getChildCount())
                    .mapToObj(i -> mFlexboxPermissionsView.getChildAt(i))
                    .forEach(child -> {
                        CharSequence viewCharSequence = child instanceof RoundCheckboxWithText
                                ? ((RoundCheckboxWithText) child).getText()
                                : child instanceof CheckBox ? ((CheckBox) child).getText() : null;
                        if (viewCharSequence == null) {
                            return;
                        }
                        String viewText = viewCharSequence.toString().split("\n")[0].trim();
                        if (!initialPermissions.contains(viewText)) {
                            return;
                        }
                        if (child instanceof RoundCheckboxWithText) {
                            ((RoundCheckboxWithText) child).setChecked(true);
                        } else {
                            ((CheckBox) child).setChecked(true);
                        }
                    });
        } catch (Exception ignored) {
            /* Ignored. */
        } finally {
            try {
                apkFile.close();
            } catch (IOException e) {
                /* Ignored. */
            }
        }
    }

    private static String generatePackageNameSuffix(ScriptFile file) {
        String name = file.getSimplifiedName();
        if (name.matches(".*[\\u4e00-\\u9fff].*")) {
            // name = Pinyin4j.ofRhino(name);
            name = Pinyin.INSTANCE.simpleRhino(name, false, true);
        }
        name = name
                .replaceAll("\\W+", "_")
                .replaceAll("(.+)_+$", "$1");
        if (name.matches("^\\d.*")) {
            name = "app_" + name;
        }
        return name.toLowerCase(Language.getPrefLanguage().getLocale());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
    }

    void selectSourceFilePath() {
        String initialDir = new File(mSourcePathView.getText().toString()).getParent();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_source_file_path)
                .dir(EnvironmentUtils.getExternalStoragePath(),
                        initialDir == null ? WorkingDirectoryUtils.getPath() : initialDir)
                .singleChoice(this::setSource)
                .show();
    }

    private void setSource(File file) {
        if (!file.isDirectory()) {
            mSourcePathView.setText(file.getPath());
            return;
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.getPath());
        if (mProjectConfig == null) {
            return;
        }
        mIsProjectLevelBuilding = true;
        mOutputPathView.setText(new File(mSource, mProjectConfig.getBuildDir()).getPath());
        mAppConfigView.setVisibility(View.GONE);
        mSourcePathContainerView.setVisibility(View.GONE);
    }

    void selectOutputDirPath() {
        String initialDir = new File(mOutputPathView.getText().toString()).exists()
                ? mOutputPathView.getText().toString()
                : WorkingDirectoryUtils.getPath();
        new FileChooserDialogBuilder(this)
                .title(R.string.text_output_apk_path)
                .dir(initialDir)
                .chooseDir()
                .singleChoice(dir -> mOutputPathView.setText(dir.getPath()))
                .positiveColorRes(R.color.dialog_button_attraction)
                .show();
    }

    void selectIcon() {
        AppsIconSelectActivity.launchForResult(this, REQUEST_CODE);
    }

    void buildApk() {
        if (!checkInputs()) {
            ViewUtils.showToast(this, getString(R.string.error_input_fields_check_failed));
            return;
        }
        if (!checkAbis()) {
            ViewUtils.showToast(this, getString(R.string.error_at_least_one_abi_needs_to_be_selected));
            return;
        }
        doBuildingApk();
    }

    private boolean checkInputs() {
        if (mIsProjectLevelBuilding) {
            return checkNotEmpty(mOutputPathView);
        }
        return checkNotEmpty(mSourcePathView)
               & checkNotEmpty(mOutputPathView)
               & checkNotEmpty(mAppNameView)
               & checkNotEmpty(mVersionCodeView)
               & checkNotEmpty(mVersionNameView)
               & checkPackageNameValid(mPackageNameView);
    }

    private boolean checkAbis() {
        for (int i = 0; i < mFlexboxAbisView.getChildCount(); i += 1) {
            View child = mFlexboxAbisView.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked() && child.isEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPackageNameValid(EditText editText) {
        Editable text = editText.getText();
        if (TextUtils.isEmpty(text)) {
            editText.setError(getString(R.string.text_should_not_be_empty));
            return false;
        }
        if (!REGEX_PACKAGE_NAME.matcher(text).matches()) {
            editText.setError(getString(R.string.text_invalid_package_name));
            return false;
        }
        return true;
    }

    private boolean checkNotEmpty(EditText editText) {
        if (TextUtils.isEmpty(editText.getText()) && editText.isShown()) {
            editText.setError(getString(R.string.text_should_not_be_empty));
            return false;
        }
        return true;
    }

    private void showHintDialogIfNeeded() {
        ArrayList<ArrayList<Map<Integer, List<String>>>> info = new ArrayList<>();

        if (!mUnavailableStandardAbis.isEmpty() && mProjectConfig != null) {
            var projectConfigAbis = mProjectConfig.getAbis();
            mUnavailableStandardAbis.forEach(unavailableStandardAbi -> {
                var unavailableAbiAliasList = ABI_ALIASES.getOrDefault(unavailableStandardAbi, Collections.emptyList());
                ArrayList<String> unavailableAbisToCheck = new ArrayList<>(unavailableAbiAliasList);
                unavailableAbisToCheck.add(unavailableStandardAbi);
                projectConfigAbis.forEach(projectConfigAbi -> {
                    if (containsIgnoreCase(unavailableAbisToCheck, projectConfigAbi)) {
                        mUnavailableAbis.add(projectConfigAbi);
                    }
                });
            });
        }

        int splitLineLength = 0;

        if (!mInvalidAbis.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_abi_options_contains_invalid, mInvalidAbis));
            }});
        }
        if (!mUnavailableAbis.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_abi_options_contains_unavailable, mUnavailableAbis));
                add(Map.of(R.string.current_available_abi_options, AndroidUtils.getAppSupportedAbiList()));
            }});
        }
        if (!mInvalidLibs.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_lib_options_contains_invalid, mInvalidLibs));
            }});
        }
        if (!mUnavailableLibs.isEmpty()) {
            info.add(new ArrayList<>() {{
                add(Map.of(R.string.config_lib_options_contains_unavailable, mUnavailableLibs));
            }});
        }

        if (info.isEmpty()) return;

        // 动态计算 splitLineLength, 考虑广范围的双宽字符
        for (ArrayList<Map<Integer, List<String>>> mapsList : info) {
            for (Map<Integer, List<String>> map : mapsList) {
                for (Map.Entry<Integer, List<String>> entry : map.entrySet()) {
                    String text = getString(entry.getKey());
                    int length = 0;
                    for (char c : text.toCharArray()) {
                        length += isWideCharacter(c) ? 2 : 1;
                    }
                    splitLineLength = Math.max(splitLineLength, length);
                }
            }
        }

        final int finalSplitLineLength = splitLineLength;

        // 生成对话框内容
        String content = info.stream()
                .map(mapsList -> mapsList.stream()
                        .map(map -> map.entrySet().stream()
                                .map(entry -> getString(entry.getKey()) + ":\n[ " + String.join(", ", entry.getValue()) + " ]")
                                .collect(Collectors.joining("\n")))
                        .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n" + "-".repeat(finalSplitLineLength) + "\n"));

        new MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(content)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_failure)
                .cancelable(false)
                .show();
    }

    // 判断字符是否是广范围双宽字符
    private boolean isWideCharacter(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT ||
               block == Character.UnicodeBlock.HIRAGANA ||
               block == Character.UnicodeBlock.KATAKANA ||
               block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
               block == Character.UnicodeBlock.HANGUL_JAMO ||
               block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
               block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS ||
               block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    @SuppressLint("CheckResult")
    private void doBuildingApk() {
        ProjectConfig projectConfig = determineProjectConfig();
        File buildPath = new File(getCacheDir(), "build/");
        File outApk = new File(mOutputPathView.getText().toString(),
                String.format("%s_v%s.apk", projectConfig.getName(), projectConfig.getVersionName()));
        File outApkBackup = null;
        try {
            outApkBackup = backupExistingOutputApkIfNeeded(outApk);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to backup existing output apk", e);
            ErrorDialogActivity.showErrorDialog(this, R.string.text_failed_to_build, e.getMessage());
            return;
        }
        mBuildCancelled.set(false);
        mBuildWorkspace = buildPath;
        mBuildOutputApk = outApk;
        mBuildOutputApkBackup = outApkBackup;
        showProgressDialog();
        Observable.fromCallable(() -> {
                    mBuildThread = Thread.currentThread();
                    try {
                        return callApkBuilder(buildPath, outApk, projectConfig);
                    } finally {
                        mBuildThread = null;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apkBuilder -> {
                    if (mBuildCancelled.get()) {
                        handleBuildAborted();
                        return;
                    }
                    if (apkBuilder != null) {
                        onBuildSuccessful(outApk);
                    } else {
                        onBuildFailed(new FileNotFoundException(TEMPLATE_APK_NAME));
                    }
                }, this::onBuildFailed);
    }

    @Nullable
    private File backupExistingOutputApkIfNeeded(@NonNull File outApk) throws IOException {
        if (!outApk.exists()) {
            return null;
        }
        File parentDir = outApk.getAbsoluteFile().getParentFile();
        if (parentDir == null) {
            throw new IOException("Invalid output apk path: " + outApk.getPath());
        }
        File backupApk = new File(parentDir, outApk.getName() + "." + System.nanoTime() + ".bak");
        if (backupApk.exists() && !backupApk.delete()) {
            throw new IOException("Failed to remove stale output apk backup: " + backupApk.getPath());
        }
        if (outApk.renameTo(backupApk)) {
            return backupApk;
        }
        if (!PFiles.copy(outApk.getPath(), backupApk.getPath())) {
            throw new IOException("Failed to backup existing output apk: " + outApk.getPath());
        }
        if (!outApk.delete()) {
            if (!backupApk.delete()) {
                Log.w(LOG_TAG, "Failed to delete incomplete backup apk after backup cleanup failure: " + backupApk.getPath());
            }
            throw new IOException("Failed to remove original output apk after backup copy: " + outApk.getPath());
        }
        return backupApk;
    }

    private ProjectConfig determineProjectConfig() {
        ArrayList<String> abis = collectCheckedItems(mFlexboxAbisView);
        ArrayList<String> libs = collectCheckedItems(mFlexboxLibsView);
        ArrayList<String> permissions = collectCheckedItems(mFlexboxPermissionsView);

        ProjectConfig projectConfig;
        if (mProjectConfig != null) {
            projectConfig = mProjectConfig
                    .excludeDir(mProjectConfig.getBuildDir())
                    .setSourcePath(mSource)
                    .setIconPath(mProjectConfig.getIconPath() == null ? null : new File(mSource, mProjectConfig.getIconPath()).getPath());
        } else {
            projectConfig = new ProjectConfig()
                    .setName(mAppNameView.getText().toString())
                    .setSourcePath(mSourcePathView.getText().toString())
                    .setPackageName(mPackageNameView.getText().toString())
                    .setVersionName(mVersionNameView.getText().toString())
                    .setVersionCode(Integer.parseInt(mVersionCodeView.getText().toString()))
                    .setIconGetter(mIsDefaultIcon ? null : () -> BitmapUtils.drawableToBitmap(mIconView.getDrawable()));
        }

        return projectConfig
                .setAbis(abis)
                .setLibs(libs)
                .setKeyStore(mVerifiedKeyStoresView.getSelectedItemPosition() > 0 ? (KeyStore) mVerifiedKeyStoresView.getSelectedItem() : null)
                .setSignatureScheme(mSignatureSchemesView.getSelectedItem().toString())
                .setPermissions(permissions);
    }

    @NotNull
    private ArrayList<String> collectCheckedItems(FlexboxLayout flexboxLayout) {
        ArrayList<String> result = new ArrayList<>();

        for (int i = 0; i < flexboxLayout.getChildCount(); i += 1) {
            View child = flexboxLayout.getChildAt(i);
            if (child instanceof RoundCheckboxWithText) {
                if (((RoundCheckboxWithText) child).isChecked()) {
                    CharSequence charSequence = ((RoundCheckboxWithText) child).getText();
                    if (charSequence != null) {
                        result.add(charSequence.toString());
                    }
                }
            } else if (child instanceof CheckBox) {
                if (((CheckBox) child).isChecked()) {
                    CharSequence charSequence = ((CheckBox) child).getText();
                    if (charSequence != null) {
                        result.add(charSequence.toString().split("\n")[0]);
                    }
                }
            }
        }
        return result;
    }

    private ApkBuilder callApkBuilder(File buildPath, File outApk, ProjectConfig projectConfig) throws Exception {
        InputStream templateApk = getAssets().open(TEMPLATE_APK_NAME);
        return new ApkBuilder(templateApk, outApk, buildPath.getPath())
                .setProgressCallback(BuildActivity.this)
                .setCancelSignal(mBuildCancelled)
                .prepare(BuildActivity.this)
                .withConfig(BuildActivity.this, projectConfig)
                .build(BuildActivity.this)
                .sign(BuildActivity.this)
                .commitProjectConfigIfNeeded(BuildActivity.this)
                .cleanWorkspace(BuildActivity.this)
                .finish();
    }

    private enum BuildStep {
        PREPARE,
        BUILD,
        SIGN,
        CLEAN,
        FINISHED,
    }

    private void showProgressDialog() {
        dismissProgressDialog();
        View contentView = DialogBuildProgressBinding.inflate(getLayoutInflater()).getRoot();
        mProgressPrepareIcon = contentView.findViewById(R.id.icon_prepare);
        mProgressPrepareText = contentView.findViewById(R.id.text_prepare);
        mProgressBuildIcon = contentView.findViewById(R.id.icon_build);
        mProgressBuildText = contentView.findViewById(R.id.text_build);
        mProgressSignIcon = contentView.findViewById(R.id.icon_sign);
        mProgressSignText = contentView.findViewById(R.id.text_sign);
        mProgressCleanIcon = contentView.findViewById(R.id.icon_clean);
        mProgressCleanText = contentView.findViewById(R.id.text_clean);
        mProgressPrepareDurationText = contentView.findViewById(R.id.text_prepare_duration);
        mProgressBuildDurationText = contentView.findViewById(R.id.text_build_duration);
        mProgressSignDurationText = contentView.findViewById(R.id.text_sign_duration);
        mProgressCleanDurationText = contentView.findViewById(R.id.text_clean_duration);
        mStateTitleText = contentView.findViewById(R.id.text_state_title);
        mStateContentText = contentView.findViewById(R.id.text_state_content);
        resetStepDurations();
        applyThemeColorIcons(mProgressPrepareIcon, mProgressBuildIcon, mProgressSignIcon, mProgressCleanIcon);
        mProgressDialog = new MaterialDialog.Builder(this)
                .title(R.string.text_building_apk)
                .customView(contentView, false)
                .neutralText("")
                .negativeText("")
                .positiveText(R.string.dialog_button_abort)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive((dialog, which) -> requestBuildAbort())
                .autoDismiss(false)
                .cancelable(false)
                .show();
        updateProgressDialog(BuildStep.PREPARE);
        mStateTitleText.setText(getString(R.string.text_property_colon, getString(R.string.text_processing)));
    }

    private void applyThemeColorIcons(ImageView... imageView) {
        int backgroundColor = getColor(R.color.window_background);
        int adjustedColor = ColorUtils.adjustColorForContrast(backgroundColor, ThemeColorManager.getColorPrimary(), 2.3);
        for (ImageView iv : imageView) {
            iv.setImageTintList(ColorStateList.valueOf(adjustedColor));
        }
    }

    private void updateProgressDialog(BuildStep currentStep) {
        if (mProgressDialog == null) {
            return;
        }
        recordStepDuration(currentStep);
        setProgressStep(mProgressPrepareIcon, mProgressPrepareText, mProgressPrepareDurationText, BuildStep.PREPARE, currentStep);
        setProgressStep(mProgressBuildIcon, mProgressBuildText, mProgressBuildDurationText, BuildStep.BUILD, currentStep);
        setProgressStep(mProgressSignIcon, mProgressSignText, mProgressSignDurationText, BuildStep.SIGN, currentStep);
        setProgressStep(mProgressCleanIcon, mProgressCleanText, mProgressCleanDurationText, BuildStep.CLEAN, currentStep);
    }

    private void setProgressStep(@Nullable ImageView iconView, @Nullable TextView stateView, @Nullable TextView durationView, BuildStep step, BuildStep currentStep) {
        if (step.ordinal() < currentStep.ordinal()) {
            if (iconView != null) iconView.setImageResource(R.drawable.ic_check_mark);
            if (stateView != null) stateView.setText(ensureTextEndsWithDot(stateView.getText()));
            if (durationView != null && currentStep.ordinal() - step.ordinal() == 1) {
                updateStepDurationText(durationView, step, SystemClock.elapsedRealtime());
            }
        } else if (step == currentStep) {
            if (iconView != null) iconView.setImageResource(R.drawable.ic_right_arrow);
            if (stateView != null) stateView.setText(ensureTextEndsWithHalfEllipsis(stateView.getText()));
        } else {
            if (iconView != null) iconView.setImageResource(R.drawable.transparent);
            if (stateView != null) stateView.setText(ensureTextEndsWithoutDot(stateView.getText()));
        }
    }

    private void resetStepDurations() {
        mStepDurationMs.clear();
        mCurrentTimingStep = null;
        mCurrentTimingStepStartedAtMs = 0L;
    }

    private void recordStepDuration(@NonNull BuildStep currentStep) {
        long now = SystemClock.elapsedRealtime();
        if (mCurrentTimingStep != null && mCurrentTimingStep != currentStep && isTrackedStep(mCurrentTimingStep)) {
            mStepDurationMs.put(mCurrentTimingStep, Math.max(0L, now - mCurrentTimingStepStartedAtMs));
        }
        if (isTrackedStep(currentStep) && !mStepDurationMs.containsKey(currentStep)) {
            if (mCurrentTimingStep != currentStep) {
                mCurrentTimingStep = currentStep;
                mCurrentTimingStepStartedAtMs = now;
            }
        } else if (!isTrackedStep(currentStep)) {
            mCurrentTimingStep = null;
            mCurrentTimingStepStartedAtMs = 0L;
        }
    }

    private boolean isTrackedStep(@NonNull BuildStep step) {
        return step == BuildStep.PREPARE
               || step == BuildStep.BUILD
               || step == BuildStep.SIGN
               || step == BuildStep.CLEAN;
    }

    @Nullable
    private Long resolveStepDurationMs(@NonNull BuildStep step, long now) {
        Long duration = mStepDurationMs.get(step);
        if (duration != null) {
            return duration;
        }
        if (step == mCurrentTimingStep) {
            return Math.max(0L, now - mCurrentTimingStepStartedAtMs);
        }
        return null;
    }

    private void updateStepDurationText(@NonNull TextView textView, @NonNull BuildStep step, long now) {
        Long durationMs = resolveStepDurationMs(step, now);
        textView.setText(durationMs == null ? "" : formatStepDuration(durationMs));
    }

    @NonNull
    private String formatStepDuration(long durationMs) {
        long safeDurationMs = Math.max(0L, durationMs);
        long tenths = Math.round(safeDurationMs / 100.0d);
        long integerPart = tenths / 10;
        long decimalPart = tenths % 10;
        // if (decimalPart == 0L) {
        //     return "[ " + integerPart + " s ]";
        // }
        return "[ " + integerPart + "." + decimalPart + " s ]";
    }

    private String ensureTextEndsWithDot(CharSequence text) {
        return ensureTextEndsWithoutDot(text) + ".";
    }

    private String ensureTextEndsWithHalfEllipsis(CharSequence text) {
        return ensureTextEndsWithoutDot(text) + StringUtils.str(R.string.text_half_ellipsis);
    }

    private String ensureTextEndsWithoutDot(CharSequence text) {
        var tmp = text.toString();
        while (tmp.length() > 0 && tmp.charAt(tmp.length() - 1) == '.') {
            tmp = tmp.subSequence(0, tmp.length() - 1).toString();
        }
        return tmp;
    }

    // Abort build cooperatively and trigger cleanup as early as possible.
    // zh-CN: 协作式中止构建, 并尽早触发清理.
    private void requestBuildAbort() {
        if (!mBuildCancelled.compareAndSet(false, true)) {
            return;
        }
        if (mProgressDialog != null) {
            mProgressDialog.setTitle(R.string.text_aborting);
            View button = mProgressDialog.getActionButton(DialogAction.POSITIVE);
            if (button != null) {
                button.setEnabled(false);
            }
        }
        Thread buildThread = mBuildThread;
        if (buildThread != null) {
            buildThread.interrupt();
        }
        cleanupBuildArtifactsAsync(false);
    }

    private void cleanupBuildArtifactsAsync(boolean restoreOutputApk) {
        File workspace = mBuildWorkspace;
        File outApk = mBuildOutputApk;
        File outApkBackup = mBuildOutputApkBackup;
        if (workspace == null && outApk == null && outApkBackup == null) {
            return;
        }
        Schedulers.io().scheduleDirect(() -> cleanupBuildArtifacts(workspace, outApk, outApkBackup, restoreOutputApk));
    }

    // Best-effort cleanup for workspace and output artifacts.
    // zh-CN: 对工作区和输出产物执行尽力清理.
    private void cleanupBuildArtifacts(@Nullable File workspace, @Nullable File outApk, @Nullable File outApkBackup, boolean restoreOutputApk) {
        if (workspace != null && workspace.exists()) {
            PFiles.deleteRecursively(workspace);
        }
        if (outApk != null && outApk.exists()) {
            outApk.delete();
        }
        if (restoreOutputApk) {
            restoreOutputApkIfNeeded(outApk, outApkBackup);
        }
    }

    private void restoreOutputApkIfNeeded(@Nullable File outApk, @Nullable File outApkBackup) {
        if (outApk == null || outApkBackup == null || !outApkBackup.exists()) {
            return;
        }
        if (outApk.exists() && !outApk.delete()) {
            Log.w(LOG_TAG, "Failed to delete output apk before restore: " + outApk.getPath());
        }
        if (!outApkBackup.renameTo(outApk)) {
            if (!PFiles.copy(outApkBackup.getPath(), outApk.getPath())) {
                Log.w(LOG_TAG, "Failed to restore output apk backup: " + outApk.getPath());
                return;
            }
            if (!outApkBackup.delete()) {
                Log.w(LOG_TAG, "Failed to delete output apk backup: " + outApkBackup.getPath());
            }
        }
    }

    private void discardOutputApkBackup() {
        File outApkBackup = mBuildOutputApkBackup;
        if (outApkBackup != null && outApkBackup.exists() && !outApkBackup.delete()) {
            Log.w(LOG_TAG, "Failed to delete output apk backup: " + outApkBackup.getPath());
        }
    }

    private void handleBuildAborted() {
        dismissProgressDialog();
        cleanupBuildArtifactsAsync(true);
        ViewUtils.showToast(this, getString(R.string.text_operation_aborted));
        finishBuildState();
    }

    private void finishBuildState() {
        mBuildWorkspace = null;
        mBuildOutputApk = null;
        mBuildOutputApkBackup = null;
        mBuildThread = null;
        mBuildCancelled.set(false);
    }

    private void dismissProgressDialog() {
        if (mProgressDialog == null) {
            return;
        }
        mProgressDialog.dismiss();
        mProgressDialog = null;
        mProgressPrepareIcon = null;
        mProgressPrepareText = null;
        mProgressBuildIcon = null;
        mProgressBuildText = null;
        mProgressSignIcon = null;
        mProgressSignText = null;
        mProgressCleanIcon = null;
        mProgressCleanText = null;
        mProgressPrepareDurationText = null;
        mProgressBuildDurationText = null;
        mProgressSignDurationText = null;
        mProgressCleanDurationText = null;
        mStateTitleText = null;
        mStateContentText = null;
        resetStepDurations();
    }

    private void onBuildFailed(Throwable error) {
        if (mBuildCancelled.get() || error instanceof CancellationException) {
            handleBuildAborted();
            return;
        }
        dismissProgressDialog();
        restoreOutputApkIfNeeded(mBuildOutputApk, mBuildOutputApkBackup);
        ErrorDialogActivity.showErrorDialog(this, R.string.text_failed_to_build, error.getMessage());
        Log.e(LOG_TAG, "Failed to build", error);
        finishBuildState();
    }

    private void onBuildSuccessful(File outApk) {
        if (mBuildCancelled.get()) {
            handleBuildAborted();
            return;
        }
        discardOutputApkBackup();
        Explorers.workspace().refreshAll();
        // dismissProgressDialog();
        if (mProgressDialog != null) {
            mProgressDialog.setTitle(R.string.text_build_succeeded);
            mStateTitleText.setText(getString(R.string.text_property_colon, getString(R.string.text_built_apk_file_path)));
            mStateContentText.setText(outApk.getPath());
            var positiveButton = mProgressDialog.getActionButton(DialogAction.POSITIVE);
            positiveButton.setEnabled(true);
            positiveButton.setText(getString(R.string.text_install));
            positiveButton.setTextColor(getColor(R.color.dialog_button_attraction));
            positiveButton.setOnClickListener(v -> {
                IntentUtils.installApk(
                        BuildActivity.this,
                        outApk.getPath(),
                        AppFileProvider.AUTHORITY,
                        new ToastExceptionHolder(BuildActivity.this)
                );
                dismissProgressDialog();
            });
            var negativeButton = mProgressDialog.getActionButton(DialogAction.NEGATIVE);
            negativeButton.setEnabled(true);
            negativeButton.setText(getString(R.string.text_cancel));
            negativeButton.setTextColor(getColor(R.color.dialog_button_default));
            negativeButton.setOnClickListener(v -> {
                dismissProgressDialog();
            });
            var neutralButton = mProgressDialog.getActionButton(DialogAction.NEUTRAL);
            neutralButton.setEnabled(true);
            neutralButton.setText(getString(R.string.dialog_button_file_information));
            neutralButton.setTextColor(getColor(R.color.dialog_button_hint));
            neutralButton.setOnClickListener(v -> {
                ApkInfoDialogManager.showApkInfoDialog(this, outApk);
            });
        }
        finishBuildState();
    }

    @Override
    public void onPrepare(@NonNull ApkBuilder builder) {
        updateProgressDialog(BuildStep.PREPARE);
    }

    @Override
    public void onBuild(@NonNull ApkBuilder builder) {
        updateProgressDialog(BuildStep.BUILD);
    }

    @Override
    public void onSign(@NonNull ApkBuilder builder) {
        updateProgressDialog(BuildStep.SIGN);
    }

    @Override
    public void onClean(@NonNull ApkBuilder builder) {
        updateProgressDialog(BuildStep.CLEAN);
    }

    @Override
    public void onStepProgress(@NonNull ApkBuilder builder, @NonNull String title, @Nullable String detail) {
        if (mStateTitleText != null) {
            mStateTitleText.setText(getString(R.string.text_property_colon, title));
        }
        if (!TextUtils.isEmpty(detail) && mStateContentText != null) {
            mStateContentText.setText(detail);
        }
    }

    @Override
    public void onFinished(@NotNull ApkBuilder builder) {
        updateProgressDialog(BuildStep.FINISHED);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint({"CheckResult", "MissingSuperCall"})
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            AppsIconSelectActivity.getDrawableFromIntent(getApplicationContext(), data)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(drawable -> {
                        mIconView.setImageDrawable(drawable);
                        mIsDefaultIcon = false;
                    }, Throwable::printStackTrace);
        }
    }

    public static void launch(Context context, String extraSource) {
        Intent intent = new Intent(context, BuildActivity.class)
                .putExtra(BuildActivity.EXTRA_SOURCE, extraSource);
        IntentUtils.startSafely(intent, context);
    }

}
