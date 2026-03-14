package org.autojs.autojs.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import org.autojs.autojs.project.LaunchConfig;
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
import org.autojs.autojs.ui.widget.ToolbarMenuItem;
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
import org.autojs.autojs6.databinding.FragmentSymbolsToolbarBinding;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 *
 * @noinspection ResultOfMethodCallIgnored, unused
 */
public class BuildActivity extends BaseActivity implements ApkBuilder.ProgressCallback {

    private static final int REQUEST_CODE = 44401;
    private static final int REQ_EXPORT_JSON = 44402;
    private static final int REQ_IMPORT_JSON = 44403;
    public static final String EXTRA_SOURCE = BuildActivity.class.getName() + ".extra_source_file";
    private static final String LOG_TAG = "BuildActivity";
    private static final int MAX_BUILD_UNDO_STACK = 120;
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
    CheckBox mLaunchLogsVisibleView;
    CheckBox mLaunchSplashVisibleView;
    CheckBox mLaunchLauncherVisibleView;
    CheckBox mLaunchRunOnBootView;
    EditText mLaunchSlugView;

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

    private Spinner mBuildProfileSpinner;
    private ImageView mIvBuildProfileNew;
    private ImageView mIvBuildProfileDelete;
    private ImageView mIvBuildProfileMore;
    private ToolbarMenuItem mActionBuildUndo;
    private ToolbarMenuItem mActionBuildRedo;
    private ToolbarMenuItem mActionBuildSave;

    private final ArrayList<ProfileEntry> mBuildProfileEntries = new ArrayList<>();
    private String mCurrentBuildProfileName = BuildConfigStore.PROFILE_DEFAULT_ID;
    @NonNull
    private BuildFieldPolicy mCurrentBuildFieldPolicy = BuildFieldPolicy.defaultPolicy();
    @NonNull
    private BuildFieldPolicy mProjectJsonFieldPolicyInSession = BuildFieldPolicy.projectJsonPolicy();
    @Nullable
    private BuildUiState mAutoBuildUiState;

    private final ArrayDeque<BuildUiState> mUndoStateStack = new ArrayDeque<>();
    private final ArrayDeque<BuildUiState> mRedoStateStack = new ArrayDeque<>();
    @Nullable
    private BuildUiState mBaselineBuildUiState;
    @Nullable
    private BuildUiState mTrackedBuildUiState;

    private boolean mBuildConfigSaveSticky = false;
    private boolean mBuildProfilesInitialized = false;
    private boolean mApplyingBuildUiState = false;
    private boolean mSuppressBuildProfileSpinnerCallback = false;
    private boolean mSuppressBuildStateTracking = false;
    private boolean mDeferBuildProfilesInitUntilSourceResolved = false;
    @Nullable
    private String mPendingKeyStorePathForStateApply;

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
        mLaunchLogsVisibleView = binding.checkboxLaunchLogsVisible;
        mLaunchSplashVisibleView = binding.checkboxLaunchSplashVisible;
        mLaunchLauncherVisibleView = binding.checkboxLaunchLauncherVisible;
        mLaunchRunOnBootView = binding.checkboxLaunchRunOnBoot;
        mLaunchSlugView = binding.launchSlug;

        LaunchConfig defaultLaunchConfig = new LaunchConfig();
        mLaunchLogsVisibleView.setChecked(defaultLaunchConfig.isLogsVisible());
        mLaunchSplashVisibleView.setChecked(defaultLaunchConfig.isSplashVisible());
        mLaunchLauncherVisibleView.setChecked(defaultLaunchConfig.isLauncherVisible());
        mLaunchRunOnBootView.setChecked(defaultLaunchConfig.isRunOnBoot());
        mLaunchSlugView.setText(defaultLaunchConfig.getSlug());

        mFlexboxAbisView = binding.flexboxAbis;
        initAbisChildren();

        mFlexboxLibsView = binding.flexboxLibraries;
        initLibsChildren();

        mKeyStoreViewModel = new ViewModelProvider(this, new KeyStoreViewModel.Factory(getApplicationContext())).get(KeyStoreViewModel.class);
        mKeyStoreViewModel.updateVerifiedKeyStores();

        mSignatureSchemesView = binding.spinnerSignatureSchemes;
        mVerifiedKeyStoresView = binding.spinnerVerifiedKeyStores;
        mFlexboxPermissionsView = binding.flexboxPermissions;

        mBuildProfileSpinner = binding.buildProfileSpinner;
        mIvBuildProfileNew = binding.ivBuildProfileNew;
        mIvBuildProfileDelete = binding.ivBuildProfileDelete;
        mIvBuildProfileMore = binding.ivBuildProfileMore;
        FragmentSymbolsToolbarBinding toolbarBinding = FragmentSymbolsToolbarBinding.inflate(getLayoutInflater(), binding.toolbarMenu, true);
        mActionBuildUndo = toolbarBinding.actionUndo;
        mActionBuildRedo = toolbarBinding.actionRedo;
        mActionBuildSave = toolbarBinding.actionSave;

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

        mActionBuildUndo.setOnClickListener(v -> performBuildConfigUndo());
        mActionBuildRedo.setOnClickListener(v -> performBuildConfigRedo());
        mActionBuildSave.setOnClickListener(v -> saveCurrentBuildProfile());
        mActionBuildSave.setOnLongClickListener(v -> {
            promptBuildFieldPolicyDialog(getCurrentBuildFieldPolicy(), false, selectedPolicy ->
                    promptCreateBuildProfileByName(captureCurrentBuildUiState(), selectedPolicy, null));
            return true;
        });

        mIvBuildProfileNew.setOnClickListener(v -> runWithUnsavedBuildConfigGuard(this::promptCreateBuildProfileWithBasePicker));
        mIvBuildProfileDelete.setOnClickListener(v -> runWithUnsavedBuildConfigGuard(this::promptDeleteBuildProfile));
        mIvBuildProfileMore.setOnClickListener(this::showBuildProfileMorePopup);

        setToolbarAsBack(R.string.text_build_apk);
        mSource = getIntent().getStringExtra(EXTRA_SOURCE);
        if (mSource != null) {
            mDeferBuildProfilesInitUntilSourceResolved = true;
            setupWithSourceFile(new ScriptFile(mSource));
        }

        initSignatureSchemeSpinner();
        initVerifiedKeyStoresSpinner();
        initPermissionsChildren();
        attachBuildStateChangeListeners();
        updateBuildProfileActionButtons();

        syncAbisCheckedStates();
        syncLibsCheckedStates();
        if (mDeferBuildProfilesInitUntilSourceResolved
            && mSource != null
            && new File(mSource).isDirectory()) {
            onSourcePresetReady();
        }
        refreshAutoBuildUiStateSnapshot();

        if (!mDeferBuildProfilesInitUntilSourceResolved) {
            initBuildProfilesIfNeeded();
        }

        showHintDialogIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mKeyStoreViewModel.updateVerifiedKeyStores();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_build_profile_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_import) {
            runWithUnsavedBuildConfigGuard(this::startImportBuildProfileJson);
            return true;
        }
        if (item.getItemId() == R.id.action_export) {
            runWithUnsavedBuildConfigGuard(this::startExportBuildProfileJson);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        saveCurrentBuildProfileSilently();
        super.onPause();
    }

    @Override
    public void finish() {
        if (!mBuildProfilesInitialized || !mBuildConfigSaveSticky) {
            super.finish();
            return;
        }
        runWithUnsavedBuildConfigGuard(() -> BuildActivity.super.finish());
    }

    private void onSourcePresetReady() {
        refreshAutoBuildUiStateSnapshot();
        if (!mDeferBuildProfilesInitUntilSourceResolved) {
            return;
        }
        mDeferBuildProfilesInitUntilSourceResolved = false;
        initBuildProfilesIfNeeded();
    }

    private void initBuildProfilesIfNeeded() {
        if (mBuildProfilesInitialized) {
            return;
        }

        BuildUiState fallback = getAutoBuildUiState();
        BuildConfigStore.ensureDefaultProfileExists(this, fallback.toJson(), BuildFieldPolicy.defaultPolicy().toJson());

        mBuildProfilesInitialized = true;
        String active = mIsProjectLevelBuilding
                ? BuildConfigStore.PROFILE_PROJECT_JSON
                : BuildConfigStore.getActiveProfileName(this);
        rebuildBuildProfilesSpinnerAndSelect(active);
    }

    private void rebuildBuildProfilesSpinnerAndSelect(@NonNull String internalName) {
        ArrayList<String> internalProfiles = new ArrayList<>();
        internalProfiles.add(BuildConfigStore.PROFILE_DEFAULT_ID);
        if (mIsProjectLevelBuilding) {
            internalProfiles.add(BuildConfigStore.PROFILE_PROJECT_JSON);
        }
        BuildConfigStore.listProfiles(this).forEach(name -> {
            if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(name)) return;
            if (BuildConfigStore.PROFILE_PROJECT_JSON.equals(name)) return;
            internalProfiles.add(name);
        });

        mBuildProfileEntries.clear();
        ArrayList<String> displayNames = new ArrayList<>(internalProfiles.size());
        for (String internal : internalProfiles) {
            String display = BuildConfigStore.PROFILE_DEFAULT_ID.equals(internal)
                    ? BuildConfigStore.getDefaultProfileDisplayName(this)
                    : internal;
            mBuildProfileEntries.add(new ProfileEntry(internal, display));
            displayNames.add(display);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mSuppressBuildProfileSpinnerCallback = true;
        mBuildProfileSpinner.setAdapter(adapter);
        int index = 0;
        for (int i = 0; i < mBuildProfileEntries.size(); i++) {
            if (mBuildProfileEntries.get(i).internalName.equals(internalName)) {
                index = i;
                break;
            }
        }
        if (mIsProjectLevelBuilding) {
            for (int i = 0; i < mBuildProfileEntries.size(); i++) {
                if (BuildConfigStore.PROFILE_PROJECT_JSON.equals(mBuildProfileEntries.get(i).internalName)) {
                    index = i;
                    break;
                }
            }
        }
        mBuildProfileSpinner.setSelection(index, false);
        mSuppressBuildProfileSpinnerCallback = false;

        switchToBuildProfile(mBuildProfileEntries.get(index).internalName);
    }

    private void reselectCurrentBuildProfileInSpinner() {
        if (mBuildProfileEntries.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < mBuildProfileEntries.size(); i++) {
            if (mBuildProfileEntries.get(i).internalName.equals(mCurrentBuildProfileName)) {
                index = i;
                break;
            }
        }
        mSuppressBuildProfileSpinnerCallback = true;
        mBuildProfileSpinner.setSelection(index, false);
        mSuppressBuildProfileSpinnerCallback = false;
    }

    private void switchToBuildProfile(@NonNull String internalName) {
        boolean isProjectJsonProfile = isProjectJsonProfile(internalName);
        BuildUiState storedState;
        BuildFieldPolicy fieldPolicy;
        if (isProjectJsonProfile) {
            storedState = buildUiStateFromProjectConfig();
            fieldPolicy = mProjectJsonFieldPolicyInSession.copy();
        } else {
            BuildConfigStore.ProfilePayload payload = BuildConfigStore.loadProfilePayload(this, internalName);
            storedState = payload == null
                    ? captureCurrentBuildUiState()
                    : BuildUiState.fromJson(payload.state);
            fieldPolicy = payload == null
                    ? BuildFieldPolicy.defaultPolicy()
                    : BuildFieldPolicy.fromJson(payload.fields);
        }
        BuildUiState state = resolveBuildUiStateByFieldPolicy(storedState, getAutoBuildUiState(), fieldPolicy);

        mCurrentBuildProfileName = internalName;
        mCurrentBuildFieldPolicy = fieldPolicy.copy();
        if (!mIsProjectLevelBuilding) {
            BuildConfigStore.setActiveProfileName(this, internalName);
        }

        applyBuildUiState(state);

        mUndoStateStack.clear();
        mRedoStateStack.clear();
        mTrackedBuildUiState = state.copy();
        mBaselineBuildUiState = state.copy();
        mBuildConfigSaveSticky = false;
        updateBuildProfileActionButtons();
    }

    private void refreshAutoBuildUiStateSnapshot() {
        mAutoBuildUiState = captureCurrentBuildUiState().copy();
    }

    @NonNull
    private BuildUiState getAutoBuildUiState() {
        return mAutoBuildUiState == null ? captureCurrentBuildUiState() : mAutoBuildUiState.copy();
    }

    @NonNull
    private BuildFieldPolicy getCurrentBuildFieldPolicy() {
        return mCurrentBuildFieldPolicy == null ? BuildFieldPolicy.defaultPolicy() : mCurrentBuildFieldPolicy.copy();
    }

    private boolean isProjectJsonProfile(@Nullable String internalName) {
        return BuildConfigStore.PROFILE_PROJECT_JSON.equals(internalName);
    }

    @NonNull
    private BuildUiState buildUiStateFromProjectConfig() {
        if (mProjectConfig == null) {
            return captureCurrentBuildUiState();
        }
        BuildUiState state = captureCurrentBuildUiState();
        String sourcePath = state.sourcePath == null ? "" : state.sourcePath.trim();
        if (TextUtils.isEmpty(sourcePath)) {
            sourcePath = mSource == null ? "" : mSource;
        }
        state.sourcePath = sourcePath;
        if (TextUtils.isEmpty(state.outputPath)) {
            state.outputPath = new File(sourcePath, mProjectConfig.getBuildDir()).getPath();
        }
        state.appName = Objects.requireNonNullElse(mProjectConfig.getName(), "");
        state.packageName = Objects.requireNonNullElse(mProjectConfig.getPackageName(), "");
        state.versionName = Objects.requireNonNullElse(mProjectConfig.getVersionName(), "");
        state.versionCode = String.valueOf(mProjectConfig.getVersionCode());
        state.signatureScheme = Objects.requireNonNullElse(mProjectConfig.getSignatureScheme(), "");
        LaunchConfig launchConfig = mProjectConfig.getLaunchConfig();
        if (launchConfig != null) {
            state.launchLogsVisible = launchConfig.isLogsVisible();
            state.launchSplashVisible = launchConfig.isSplashVisible();
            state.launchLauncherVisible = launchConfig.isLauncherVisible();
            state.launchRunOnBoot = launchConfig.isRunOnBoot();
            state.launchSlug = Objects.requireNonNullElse(launchConfig.getSlug(), "");
        }
        if (!mProjectConfig.getAbis().isEmpty()) {
            state.abis = new ArrayList<>(mProjectConfig.getAbis());
        }
        state.libs = new ArrayList<>(mProjectConfig.getLibs());
        state.permissions = new ArrayList<>(mProjectConfig.getPermissions());
        return state;
    }

    @NonNull
    private BuildUiState resolveBuildUiStateByFieldPolicy(@NonNull BuildUiState fixedState, @NonNull BuildUiState autoState, @NonNull BuildFieldPolicy policy) {
        BuildUiState out = autoState.copy();
        if (policy.fixedSourcePath) out.sourcePath = fixedState.sourcePath;
        if (policy.fixedOutputPath) out.outputPath = fixedState.outputPath;
        if (policy.fixedAppName) out.appName = fixedState.appName;
        if (policy.fixedPackageName) out.packageName = fixedState.packageName;
        if (policy.fixedVersionName) out.versionName = fixedState.versionName;
        if (policy.fixedVersionCode) out.versionCode = fixedState.versionCode;
        if (policy.fixedLaunchLogsVisible) out.launchLogsVisible = fixedState.launchLogsVisible;
        if (policy.fixedLaunchSplashVisible) out.launchSplashVisible = fixedState.launchSplashVisible;
        if (policy.fixedLaunchLauncherVisible) out.launchLauncherVisible = fixedState.launchLauncherVisible;
        if (policy.fixedLaunchRunOnBoot) out.launchRunOnBoot = fixedState.launchRunOnBoot;
        if (policy.fixedLaunchSlug) out.launchSlug = fixedState.launchSlug;
        if (policy.fixedAbis && !fixedState.abis.isEmpty()) out.abis = new ArrayList<>(fixedState.abis);
        if (policy.fixedLibs) out.libs = new ArrayList<>(fixedState.libs);
        if (policy.fixedSignatureScheme) out.signatureScheme = fixedState.signatureScheme;
        if (policy.fixedKeyStore) out.keyStorePath = fixedState.keyStorePath;
        if (policy.fixedPermissions) out.permissions = new ArrayList<>(fixedState.permissions);
        return out;
    }

    private void attachBuildStateChangeListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                /* Ignored. */
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                /* Ignored. */
            }

            @Override
            public void afterTextChanged(Editable s) {
                onBuildUiControlChanged();
            }
        };

        mSourcePathView.addTextChangedListener(watcher);
        mOutputPathView.addTextChangedListener(watcher);
        mAppNameView.addTextChangedListener(watcher);
        mPackageNameView.addTextChangedListener(watcher);
        mVersionNameView.addTextChangedListener(watcher);
        mVersionCodeView.addTextChangedListener(watcher);
        mLaunchSlugView.addTextChangedListener(watcher);

        mLaunchLogsVisibleView.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
        mLaunchSplashVisibleView.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
        mLaunchLauncherVisibleView.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
        mLaunchRunOnBootView.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());

        AdapterView.OnItemSelectedListener stateSpinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onBuildUiControlChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                /* Ignored. */
            }
        };
        mSignatureSchemesView.setOnItemSelectedListener(stateSpinnerListener);
        mVerifiedKeyStoresView.setOnItemSelectedListener(stateSpinnerListener);

        mBuildProfileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSuppressBuildProfileSpinnerCallback) {
                    return;
                }
                if (position < 0 || position >= mBuildProfileEntries.size()) {
                    return;
                }
                String nextProfile = mBuildProfileEntries.get(position).internalName;
                if (Objects.equals(nextProfile, mCurrentBuildProfileName)) {
                    return;
                }
                runWithUnsavedBuildConfigGuard(() -> switchToBuildProfile(nextProfile), BuildActivity.this::reselectCurrentBuildProfileInSpinner);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                /* Ignored. */
            }
        });
    }

    private boolean canTrackBuildStateChanges() {
        return mBuildProfilesInitialized
               && !mApplyingBuildUiState
               && !mSuppressBuildStateTracking;
    }

    private void onBuildUiControlChanged() {
        if (!canTrackBuildStateChanges()) {
            return;
        }
        BuildUiState current = captureCurrentBuildUiState();
        if (mTrackedBuildUiState != null && current.equals(mTrackedBuildUiState)) {
            return;
        }

        if (mTrackedBuildUiState != null) {
            mUndoStateStack.addLast(mTrackedBuildUiState.copy());
            while (mUndoStateStack.size() > MAX_BUILD_UNDO_STACK) {
                mUndoStateStack.removeFirst();
            }
        }
        mTrackedBuildUiState = current.copy();
        mRedoStateStack.clear();

        mBuildConfigSaveSticky = mBaselineBuildUiState == null || !current.equals(mBaselineBuildUiState);
        updateBuildProfileActionButtons();
    }

    private void performBuildConfigUndo() {
        if (mUndoStateStack.isEmpty()) {
            return;
        }
        BuildUiState current = captureCurrentBuildUiState();
        BuildUiState target = mUndoStateStack.removeLast();
        mRedoStateStack.addLast(current.copy());

        applyBuildUiState(target);
        mTrackedBuildUiState = target.copy();
        mBuildConfigSaveSticky = mBaselineBuildUiState == null || !target.equals(mBaselineBuildUiState);
        updateBuildProfileActionButtons();
    }

    private void performBuildConfigRedo() {
        if (mRedoStateStack.isEmpty()) {
            return;
        }
        BuildUiState current = captureCurrentBuildUiState();
        BuildUiState target = mRedoStateStack.removeLast();
        mUndoStateStack.addLast(current.copy());

        applyBuildUiState(target);
        mTrackedBuildUiState = target.copy();
        mBuildConfigSaveSticky = mBaselineBuildUiState == null || !target.equals(mBaselineBuildUiState);
        updateBuildProfileActionButtons();
    }

    private void saveCurrentBuildProfile() {
        saveCurrentBuildProfile(null);
    }

    private void saveCurrentBuildProfile(@Nullable Runnable afterSaved) {
        if (!mBuildProfilesInitialized || mCurrentBuildProfileName == null || mCurrentBuildProfileName.trim().isEmpty()) {
            return;
        }
        if (!mBuildConfigSaveSticky && !BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)) {
            if (afterSaved != null) {
                afterSaved.run();
            }
            return;
        }

        BuildUiState current = captureCurrentBuildUiState();
        promptBuildFieldPolicyDialog(getCurrentBuildFieldPolicy(), selectedPolicy -> {
            if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)) {
                ViewUtils.showToast(this, R.string.text_default_profile_overwrite_hint, true);
                promptCreateBuildProfileByName(current, selectedPolicy, afterSaved);
                return;
            }
            if (isProjectJsonProfile(mCurrentBuildProfileName)) {
                persistProjectJsonSnapshot(current, selectedPolicy, false);
                if (afterSaved != null) {
                    afterSaved.run();
                }
                return;
            }
            persistProfileSnapshot(mCurrentBuildProfileName, current, selectedPolicy, false);
            if (afterSaved != null) {
                afterSaved.run();
            }
        });
    }

    private void saveCurrentBuildProfileSilently() {
        if (!mBuildProfilesInitialized || mCurrentBuildProfileName == null || mCurrentBuildProfileName.trim().isEmpty()) {
            return;
        }
        if (!mBuildConfigSaveSticky) {
            return;
        }
        if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)) {
            return;
        }
        if (isProjectJsonProfile(mCurrentBuildProfileName)) {
            persistProjectJsonSnapshot(captureCurrentBuildUiState(), getCurrentBuildFieldPolicy(), true);
            return;
        }
        persistProfileSnapshot(mCurrentBuildProfileName, captureCurrentBuildUiState(), getCurrentBuildFieldPolicy(), true);
    }

    private void persistProfileSnapshot(@NonNull String profileName, @NonNull BuildUiState state, @NonNull BuildFieldPolicy fieldPolicy, boolean silent) {
        BuildConfigStore.saveProfile(this, profileName, state.toJson(), fieldPolicy.toJson());
        mCurrentBuildFieldPolicy = fieldPolicy.copy();
        if (isProjectJsonProfile(profileName)) {
            mProjectJsonFieldPolicyInSession = fieldPolicy.copy();
        }
        mCurrentBuildProfileName = profileName;
        BuildUiState current = captureCurrentBuildUiState();
        mBaselineBuildUiState = current.copy();
        mTrackedBuildUiState = current.copy();
        mBuildConfigSaveSticky = false;
        updateBuildProfileActionButtons();
        if (!silent) {
            ViewUtils.showToast(this, R.string.text_done);
        }
    }

    private void persistProjectJsonSnapshot(@NonNull BuildUiState state, @NonNull BuildFieldPolicy fieldPolicy, boolean silent) {
        if (mProjectConfig == null) {
            if (!silent) {
                ViewUtils.showToast(this, R.string.text_failed_to_save_project_json, true);
            }
            return;
        }
        BuildUiState effectiveState = resolveBuildUiStateByFieldPolicy(state, getAutoBuildUiState(), fieldPolicy);
        try {
            ProjectConfig configToPersist = determineProjectConfig(effectiveState);
            String sourcePath = configToPersist.getSourcePath();
            if (TextUtils.isEmpty(sourcePath)) {
                throw new IllegalStateException("Source path is empty");
            }
            mSource = sourcePath;
            String projectConfigPath = ProjectConfig.configFileOfDir(sourcePath);
            PFiles.write(projectConfigPath, configToPersist.toJson(true));

            mProjectConfig = ProjectConfig.fromProjectDir(sourcePath);
            if (mProjectConfig == null) {
                mProjectConfig = configToPersist;
            }
            mProjectJsonFieldPolicyInSession = fieldPolicy.copy();
            mCurrentBuildFieldPolicy = fieldPolicy.copy();
            BuildUiState current = captureCurrentBuildUiState();
            mBaselineBuildUiState = current.copy();
            mTrackedBuildUiState = current.copy();
            mBuildConfigSaveSticky = false;
            updateBuildProfileActionButtons();
            if (!silent) {
                ViewUtils.showToast(this, R.string.text_done);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            if (!silent) {
                ViewUtils.showToast(this, throwable.getMessage(), true);
            }
        }
    }

    private void promptCreateBuildProfileWithBasePicker() {
        BuildUiState baseState = captureCurrentBuildUiState();
        promptBuildFieldPolicyDialog(getCurrentBuildFieldPolicy(), false, selectedPolicy ->
                promptCreateBuildProfileByName(baseState, selectedPolicy, null));
    }

    private void promptCreateBuildProfileByName(@NonNull BuildUiState baseState, @NonNull BuildFieldPolicy fieldPolicy, @Nullable Runnable afterCreated) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_save_configuration_as)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_build_profile_name_hint), BuildConfigStore.getPrefillProfileName(this), false, (dialog, input) -> {
                    String raw = input == null ? "" : input.toString().trim();
                    if (raw.isEmpty()) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_invalid, true);
                        return;
                    }
                    if (BuildConfigStore.isReservedProfileName(this, raw)) {
                        ViewUtils.showToast(this, R.string.text_build_profile_name_cannot_be_reserved, true);
                        return;
                    }
                    if (BuildConfigStore.listProfiles(this).contains(raw)) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_conflict, true);
                        return;
                    }
                    BuildConfigStore.saveProfile(this, raw, baseState.toJson(), fieldPolicy.toJson());
                    if (!mIsProjectLevelBuilding) {
                        BuildConfigStore.setActiveProfileName(this, raw);
                    }
                    rebuildBuildProfilesSpinnerAndSelect(raw);
                    dialog.dismiss();
                    ViewUtils.showToast(this, R.string.text_done);
                    if (afterCreated != null) {
                        afterCreated.run();
                    }
                })
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .autoDismiss(false);
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    private void promptDeleteBuildProfile() {
        if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)) {
            ViewUtils.showToast(this, R.string.text_build_profile_cannot_delete_default, true);
            return;
        }
        if (isProjectJsonProfile(mCurrentBuildProfileName)) {
            ViewUtils.showToast(this, R.string.text_build_profile_cannot_delete_project_json, true);
            return;
        }

        String profileName = mCurrentBuildProfileName;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(getString(R.string.text_delete) + getString(R.string.symbol_colon_with_blank) + profileName)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive((dialog, which) -> {
                    BuildConfigStore.deleteProfile(this, profileName);
                    BuildConfigStore.ensureDefaultProfileExists(this, getAutoBuildUiState().toJson(), BuildFieldPolicy.defaultPolicy().toJson());
                    rebuildBuildProfilesSpinnerAndSelect(BuildConfigStore.PROFILE_DEFAULT_ID);
                    dialog.dismiss();
                    ViewUtils.showToast(this, R.string.text_done);
                })
                .autoDismiss(false);
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    private void showBuildProfileMorePopup(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, getString(R.string.text_save_configuration_as));
        popupMenu.getMenu().add(0, 2, 1, getString(R.string.text_rename));
        popupMenu.getMenu().add(0, 3, 2, getString(R.string.text_build_profile_fields_configure));
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                promptBuildFieldPolicyDialog(getCurrentBuildFieldPolicy(), false, selectedPolicy ->
                        promptCreateBuildProfileByName(captureCurrentBuildUiState(), selectedPolicy, null));
                return true;
            }
            if (item.getItemId() == 2) {
                promptRenameCurrentBuildProfile();
                return true;
            }
            if (item.getItemId() == 3) {
                promptEditCurrentBuildProfileFields();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void promptRenameCurrentBuildProfile() {
        if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)
            || isProjectJsonProfile(mCurrentBuildProfileName)) {
            ViewUtils.showToast(this, R.string.text_build_profile_name_cannot_be_reserved, true);
            return;
        }

        String oldName = mCurrentBuildProfileName;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_rename)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_build_profile_name_hint), oldName, false, (dialog, input) -> {
                    String raw = input == null ? "" : input.toString().trim();
                    if (raw.isEmpty()) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_invalid, true);
                        return;
                    }
                    if (BuildConfigStore.isReservedProfileName(this, raw)) {
                        ViewUtils.showToast(this, R.string.text_build_profile_name_cannot_be_reserved, true);
                        return;
                    }
                    if (Objects.equals(raw, oldName)) {
                        dialog.dismiss();
                        return;
                    }
                    if (BuildConfigStore.listProfiles(this).contains(raw)) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_conflict, true);
                        return;
                    }
                    BuildConfigStore.ProfilePayload oldPayload = BuildConfigStore.loadProfilePayload(this, oldName);
                    JSONObject oldState = oldPayload == null ? captureCurrentBuildUiState().toJson() : oldPayload.state;
                    JSONObject oldFields = oldPayload == null ? getCurrentBuildFieldPolicy().toJson() : oldPayload.fields;
                    BuildConfigStore.saveProfile(this, raw, oldState, oldFields);
                    BuildConfigStore.deleteProfile(this, oldName);
                    if (!mIsProjectLevelBuilding) {
                        BuildConfigStore.setActiveProfileName(this, raw);
                    }
                    rebuildBuildProfilesSpinnerAndSelect(raw);
                    dialog.dismiss();
                    ViewUtils.showToast(this, R.string.text_done);
                })
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .autoDismiss(false);
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    private void promptEditCurrentBuildProfileFields() {
        BuildUiState current = captureCurrentBuildUiState();
        promptBuildFieldPolicyDialog(getCurrentBuildFieldPolicy(), selectedPolicy -> {
            if (BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)) {
                ViewUtils.showToast(this, R.string.text_default_profile_overwrite_hint, true);
                promptCreateBuildProfileByName(current, selectedPolicy, null);
                return;
            }
            if (isProjectJsonProfile(mCurrentBuildProfileName)) {
                persistProjectJsonSnapshot(current, selectedPolicy, false);
                return;
            }
            persistProfileSnapshot(mCurrentBuildProfileName, current, selectedPolicy, false);
        });
    }

    private void promptBuildFieldPolicyDialog(@NonNull BuildFieldPolicy initialPolicy, @NonNull BuildFieldPolicySelectedCallback onSelected) {
        promptBuildFieldPolicyDialog(initialPolicy, isProjectJsonProfile(mCurrentBuildProfileName), onSelected);
    }

    private void promptBuildFieldPolicyDialog(
            @NonNull BuildFieldPolicy initialPolicy,
            boolean editableSourceAndIcon,
            @NonNull BuildFieldPolicySelectedCallback onSelected
    ) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = (int) (8 * getResources().getDisplayMetrics().density);
        int vertical = (int) (4 * getResources().getDisplayMetrics().density);
        container.setPadding(horizontal, vertical, horizontal, vertical);

        TextView hint = new TextView(this);
        hint.setText(R.string.text_build_profile_fields_hint);
        hint.setTextSize(13f);
        hint.setPadding(0, 0, 0, (int) (6 * getResources().getDisplayMetrics().density));
        hint.setTextColor(getColor(R.color.text_color_primary_alpha_70));
        container.addView(hint);

        CheckBox icon = createFieldPolicyCheckBox(container, R.string.text_icon, initialPolicy.fixedIcon, editableSourceAndIcon);
        addFieldPolicyDivider(container);

        CheckBox sourcePath = createFieldPolicyCheckBox(container, R.string.text_source_file_path, initialPolicy.fixedSourcePath, editableSourceAndIcon);
        CheckBox outputPath = createFieldPolicyCheckBox(container, R.string.text_output_apk_path, initialPolicy.fixedOutputPath, true);
        addFieldPolicyDivider(container);

        CheckBox appName = createFieldPolicyCheckBox(container, R.string.text_app_name, initialPolicy.fixedAppName, true);
        CheckBox packageName = createFieldPolicyCheckBox(container, R.string.text_package_name, initialPolicy.fixedPackageName, true);
        CheckBox versionName = createFieldPolicyCheckBox(container, R.string.text_version_name, initialPolicy.fixedVersionName, true);
        CheckBox versionCode = createFieldPolicyCheckBox(container, R.string.text_version_code, initialPolicy.fixedVersionCode, true);
        addFieldPolicyDivider(container);

        CheckBox launchRunOnBoot = createFieldPolicyCheckBox(container, R.string.text_launch_run_on_boot, initialPolicy.fixedLaunchRunOnBoot, true);
        CheckBox launchLogsVisible = createFieldPolicyCheckBox(container, R.string.text_launch_logs_visible, initialPolicy.fixedLaunchLogsVisible, true);
        CheckBox launchSplashVisible = createFieldPolicyCheckBox(container, R.string.text_launch_splash_visible, initialPolicy.fixedLaunchSplashVisible, true);
        CheckBox launchLauncherVisible = createFieldPolicyCheckBox(container, R.string.text_launch_launcher_visible, initialPolicy.fixedLaunchLauncherVisible, true);
        CheckBox launchSlug = createFieldPolicyCheckBox(container, R.string.text_launch_slug, initialPolicy.fixedLaunchSlug, true);
        addFieldPolicyDivider(container);

        CheckBox abis = createFieldPolicyCheckBox(container, R.string.text_supported_abis_short, initialPolicy.fixedAbis, true);
        CheckBox libs = createFieldPolicyCheckBox(container, R.string.text_supported_libraries_short, initialPolicy.fixedLibs, true);
        CheckBox signatureScheme = createFieldPolicyCheckBox(container, R.string.text_signature_scheme, initialPolicy.fixedSignatureScheme, true);
        CheckBox keyStore = createFieldPolicyCheckBox(container, R.string.text_key_store, initialPolicy.fixedKeyStore, true);
        CheckBox permissions = createFieldPolicyCheckBox(container, R.string.text_supported_permissions_short, initialPolicy.fixedPermissions, true);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_build_profile_fields)
                .customView(container, true)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onPositive((dialog, which) -> {
                    BuildFieldPolicy selected = new BuildFieldPolicy();
                    selected.fixedSourcePath = sourcePath.isChecked();
                    selected.fixedIcon = icon.isChecked();
                    selected.fixedOutputPath = outputPath.isChecked();
                    selected.fixedAppName = appName.isChecked();
                    selected.fixedPackageName = packageName.isChecked();
                    selected.fixedVersionName = versionName.isChecked();
                    selected.fixedVersionCode = versionCode.isChecked();
                    selected.fixedLaunchRunOnBoot = launchRunOnBoot.isChecked();
                    selected.fixedLaunchLogsVisible = launchLogsVisible.isChecked();
                    selected.fixedLaunchSplashVisible = launchSplashVisible.isChecked();
                    selected.fixedLaunchLauncherVisible = launchLauncherVisible.isChecked();
                    selected.fixedLaunchSlug = launchSlug.isChecked();
                    selected.fixedAbis = abis.isChecked();
                    selected.fixedLibs = libs.isChecked();
                    selected.fixedSignatureScheme = signatureScheme.isChecked();
                    selected.fixedKeyStore = keyStore.isChecked();
                    selected.fixedPermissions = permissions.isChecked();
                    onSelected.onSelected(selected);
                });
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    @NonNull
    private CheckBox createFieldPolicyCheckBox(@NonNull LinearLayout container, int textRes, boolean checked, boolean enabled) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        checkBox.setText(textRes);
        checkBox.setChecked(checked);
        checkBox.setEnabled(enabled);
        if (!enabled) {
            checkBox.setAlpha(0.62f);
        }
        container.addView(checkBox);
        return checkBox;
    }

    private void addFieldPolicyDivider(@NonNull LinearLayout container) {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, (int) (getResources().getDisplayMetrics().density))
        );
        int marginV = (int) (4 * getResources().getDisplayMetrics().density);
        lp.setMargins(0, marginV, 0, marginV);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(getColor(android.R.color.darker_gray));
        container.addView(divider);
    }

    private interface BuildFieldPolicySelectedCallback {
        void onSelected(@NonNull BuildFieldPolicy selectedPolicy);
    }

    private void runWithUnsavedBuildConfigGuard(@NonNull Runnable onProceed) {
        runWithUnsavedBuildConfigGuard(onProceed, null);
    }

    private void runWithUnsavedBuildConfigGuard(@NonNull Runnable onProceed, @Nullable Runnable onCancel) {
        if (!mBuildConfigSaveSticky) {
            onProceed.run();
            return;
        }

        int unsavedChangesTextRes = isProjectJsonProfile(mCurrentBuildProfileName)
                ? R.string.text_build_profile_unsaved_changes_project_json
                : R.string.text_build_profile_unsaved_changes;

        DialogUtils.buildAndShowAdaptive(() -> {
            MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                    .title(R.string.text_prompt)
                    .content(unsavedChangesTextRes)
                    .neutralText(R.string.dialog_button_back)
                    .neutralColorRes(R.color.dialog_button_default)
                    .negativeText(R.string.text_exit_directly)
                    .negativeColorRes(R.color.dialog_button_caution)
                    .positiveText(R.string.text_save_and_exit)
                    .positiveColorRes(R.color.dialog_button_warn)
                    .onPositive((dialog, which) -> {
                        saveCurrentBuildProfile(onProceed);
                    })
                    .onNeutral((dialog, which) -> onProceed.run())
                    .onNegative((dialog, which) -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    });
            DialogUtils.widgetThemeColor(builder);
            return builder.build();
        });
    }

    @SuppressWarnings("deprecation")
    private void startExportBuildProfileJson() {
        String name = mCurrentBuildProfileName;
        if (name == null || name.trim().isEmpty() || BuildConfigStore.PROFILE_DEFAULT_ID.equals(name)) {
            name = "default";
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "autojs6-build-profile-" + name + ".json");
        startActivityForResult(i, REQ_EXPORT_JSON);
    }

    @SuppressWarnings("deprecation")
    private void startImportBuildProfileJson() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, REQ_IMPORT_JSON);
    }

    private void doExportBuildProfileToUri(@NonNull Uri uri) {
        try {
            JSONObject json;
            if (isProjectJsonProfile(mCurrentBuildProfileName)) {
                json = new JSONObject();
                json.put("name", BuildConfigStore.PROFILE_PROJECT_JSON);
                json.put("state", captureCurrentBuildUiState().toJson());
                json.put("fields", getCurrentBuildFieldPolicy().toJson());
            } else {
                json = BuildConfigStore.exportProfileToJson(this, mCurrentBuildProfileName);
            }
            try (var out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IllegalStateException("Cannot open output stream");
                out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            ViewUtils.showToast(this, R.string.text_done);
        } catch (Throwable e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

    private void doImportBuildProfileFromUri(@NonNull Uri uri) {
        try {
            byte[] raw;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("Cannot open input stream");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8 * 1024];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                raw = buffer.toByteArray();
            }
            JSONObject json = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            BuildConfigStore.ProfilePayload payload = BuildConfigStore.importProfileFromJson(json);

            boolean reservedName = BuildConfigStore.isReservedProfileName(this, payload.name);
            if (reservedName) {
                handleImportNameConflictAndSave(payload.name, payload, false, true);
                return;
            }
            handleImportNameConflictAndSave(payload.name, payload, true, false);
        } catch (Throwable e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

    private void handleImportNameConflictAndSave(
            @NonNull String desiredName,
            @NonNull BuildConfigStore.ProfilePayload importedPayload,
            boolean overwriteEnabled,
            boolean forceConflict
    ) {
        LinkedHashSet<String> existing = new LinkedHashSet<>(BuildConfigStore.listProfiles(this));
        boolean conflict = forceConflict || existing.contains(desiredName);

        if (!conflict) {
            saveImportedProfileAndSwitch(desiredName, importedPayload);
            return;
        }

        ArrayList<String> options = new ArrayList<>();
        options.add(getString(R.string.text_import_strategy_overwrite));
        options.add(getString(R.string.text_import_strategy_auto_rename));
        options.add(getString(R.string.text_import_strategy_manual_rename));

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_import)
                .content(getString(R.string.text_import_name_conflict, desiredName))
                .items(options)
                .itemsCallback((dialog, itemView, which, text) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        if (!overwriteEnabled) return;
                        saveImportedProfileAndSwitch(desiredName, importedPayload);
                        return;
                    }
                    if (which == 1) {
                        String unique = makeUniqueProfileName(existing, desiredName);
                        saveImportedProfileAndSwitch(unique, importedPayload);
                        return;
                    }
                    if (which == 2) {
                        promptManualRenameAndImport(existing, desiredName, importedPayload);
                    }
                });
        if (!overwriteEnabled) {
            builder.itemsDisabledIndices(0);
        }
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    private void promptManualRenameAndImport(
            @NonNull LinkedHashSet<String> existing,
            @NonNull String suggestedName,
            @NonNull BuildConfigStore.ProfilePayload payload
    ) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(R.string.text_import_strategy_manual_rename)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_build_profile_name_hint), suggestedName, false, (dialog, input) -> {
                    String raw = input == null ? "" : input.toString().trim();
                    if (raw.isEmpty()) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_invalid, true);
                        return;
                    }
                    if (BuildConfigStore.isReservedProfileName(this, raw)) {
                        ViewUtils.showToast(this, R.string.text_build_profile_name_cannot_be_reserved, true);
                        return;
                    }
                    if (existing.contains(raw)) {
                        ViewUtils.showToast(this, R.string.text_symbols_profile_name_conflict, true);
                        return;
                    }
                    saveImportedProfileAndSwitch(raw, payload);
                    dialog.dismiss();
                })
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .autoDismiss(false);
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    @NonNull
    private String makeUniqueProfileName(@NonNull LinkedHashSet<String> existing, @NonNull String baseName) {
        if (!existing.contains(baseName) && !BuildConfigStore.isReservedProfileName(this, baseName)) return baseName;
        int i = 2;
        while (true) {
            String candidate = baseName + " (" + i + ")";
            if (!existing.contains(candidate) && !BuildConfigStore.isReservedProfileName(this, candidate)) {
                return candidate;
            }
            i++;
        }
    }

    private void saveImportedProfileAndSwitch(@NonNull String name, @NonNull BuildConfigStore.ProfilePayload payload) {
        BuildConfigStore.saveProfile(this, name, payload.state, payload.fields);
        if (!mIsProjectLevelBuilding) {
            BuildConfigStore.setActiveProfileName(this, name);
        }
        rebuildBuildProfilesSpinnerAndSelect(name);
        ViewUtils.showToast(this, R.string.text_done);
    }

    private void updateBuildProfileActionButtons() {
        setBuildActionEnabled(mActionBuildUndo, !mUndoStateStack.isEmpty());
        setBuildActionEnabled(mActionBuildRedo, !mRedoStateStack.isEmpty());
        setBuildActionEnabled(mActionBuildSave, mBuildConfigSaveSticky);
        setBuildActionEnabled(
                mIvBuildProfileDelete,
                mBuildProfilesInitialized
                && !BuildConfigStore.PROFILE_DEFAULT_ID.equals(mCurrentBuildProfileName)
                && !isProjectJsonProfile(mCurrentBuildProfileName)
        );
    }

    private void setBuildActionEnabled(@Nullable View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof ImageView) {
            view.setAlpha(enabled ? 1.0f : 0.45f);
        }
    }

    private void applyBuildUiState(@NonNull BuildUiState state) {
        boolean previousApplying = mApplyingBuildUiState;
        boolean previousSuppress = mSuppressBuildStateTracking;
        mApplyingBuildUiState = true;
        mSuppressBuildStateTracking = true;
        try {
            setTextIfChanged(mSourcePathView, state.sourcePath);
            setTextIfChanged(mOutputPathView, state.outputPath);
            setTextIfChanged(mAppNameView, state.appName);
            setTextIfChanged(mPackageNameView, state.packageName);
            setTextIfChanged(mVersionNameView, state.versionName);
            setTextIfChanged(mVersionCodeView, state.versionCode);
            setCheckedIfChanged(mLaunchLogsVisibleView, state.launchLogsVisible);
            setCheckedIfChanged(mLaunchSplashVisibleView, state.launchSplashVisible);
            setCheckedIfChanged(mLaunchLauncherVisibleView, state.launchLauncherVisible);
            setCheckedIfChanged(mLaunchRunOnBootView, state.launchRunOnBoot);
            setTextIfChanged(mLaunchSlugView, state.launchSlug);

            applyStringSelectionsToFlexbox(mFlexboxAbisView, state.abis);
            applyStringSelectionsToFlexbox(mFlexboxLibsView, state.libs);
            applyStringSelectionsToFlexbox(mFlexboxPermissionsView, state.permissions);

            setSignatureSchemeByLabel(state.signatureScheme);
            mPendingKeyStorePathForStateApply = state.keyStorePath;
            tryApplyPendingKeyStorePathSelection();
        } finally {
            mApplyingBuildUiState = previousApplying;
            mSuppressBuildStateTracking = previousSuppress;
        }
    }

    private void setTextIfChanged(@NonNull EditText editText, @Nullable String value) {
        String target = value == null ? "" : value;
        String current = editText.getText() == null ? "" : editText.getText().toString();
        if (!Objects.equals(current, target)) {
            editText.setText(target);
        }
    }

    private void setCheckedIfChanged(@NonNull CheckBox checkBox, boolean checked) {
        if (checkBox.isChecked() != checked) {
            checkBox.setChecked(checked);
        }
    }

    private void applyStringSelectionsToFlexbox(@NonNull FlexboxLayout flexboxLayout, @NonNull List<String> selectedValues) {
        LinkedHashSet<String> normalized = selectedValues.stream()
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (int i = 0; i < flexboxLayout.getChildCount(); i++) {
            View child = flexboxLayout.getChildAt(i);
            String label = parseWidgetLabel(child);
            if (label == null) continue;
            boolean checked = normalized.contains(label.trim().toLowerCase(Locale.ROOT));
            if (child instanceof RoundCheckboxWithText) {
                ((RoundCheckboxWithText) child).setChecked(checked);
            } else if (child instanceof CheckBox) {
                ((CheckBox) child).setChecked(checked);
            }
        }
    }

    @Nullable
    private String parseWidgetLabel(@NonNull View child) {
        CharSequence text = null;
        if (child instanceof RoundCheckboxWithText) {
            text = ((RoundCheckboxWithText) child).getText();
        } else if (child instanceof CheckBox) {
            text = ((CheckBox) child).getText();
        }
        if (text == null) return null;
        return text.toString().split("\n")[0].trim();
    }

    private void setSignatureSchemeByLabel(@Nullable String rawLabel) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return;
        }
        String target = rawLabel.trim();
        int index = -1;
        for (int i = 0; i < SIGNATURE_SCHEMES.size(); i++) {
            String standard = SIGNATURE_SCHEMES.get(i).first;
            if (standard.equalsIgnoreCase(target)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            String normalized = normalizeSignatureSchemeLabel(target);
            for (int i = 0; i < SIGNATURE_SCHEMES.size(); i++) {
                String standard = SIGNATURE_SCHEMES.get(i).first;
                if (standard.equalsIgnoreCase(normalized)) {
                    index = i;
                    break;
                }
            }
        }
        if (index >= 0 && mSignatureSchemesView.getSelectedItemPosition() != index) {
            mSignatureSchemesView.setSelection(index);
        }
    }

    private void tryApplyPendingKeyStorePathSelection() {
        if (mVerifiedKeyStoresView == null) return;
        String pendingPath = mPendingKeyStorePathForStateApply;
        if (pendingPath == null) return;

        if (pendingPath.trim().isEmpty()) {
            mVerifiedKeyStoresView.setSelection(0);
            mPendingKeyStorePathForStateApply = null;
            return;
        }

        for (int i = 1; i < mVerifiedKeyStoresView.getCount(); i++) {
            Object item = mVerifiedKeyStoresView.getItemAtPosition(i);
            if (!(item instanceof KeyStore)) continue;
            KeyStore keyStore = (KeyStore) item;
            if (pendingPath.equalsIgnoreCase(keyStore.getAbsolutePath())) {
                mVerifiedKeyStoresView.setSelection(i);
                mPendingKeyStorePathForStateApply = null;
                return;
            }
        }
    }

    @NonNull
    private BuildUiState captureCurrentBuildUiState() {
        BuildUiState state = new BuildUiState();
        state.sourcePath = mSourcePathView.getText() == null ? "" : mSourcePathView.getText().toString();
        state.outputPath = mOutputPathView.getText() == null ? "" : mOutputPathView.getText().toString();
        state.appName = mAppNameView.getText() == null ? "" : mAppNameView.getText().toString();
        state.packageName = mPackageNameView.getText() == null ? "" : mPackageNameView.getText().toString();
        state.versionName = mVersionNameView.getText() == null ? "" : mVersionNameView.getText().toString();
        state.versionCode = mVersionCodeView.getText() == null ? "" : mVersionCodeView.getText().toString();
        state.launchLogsVisible = mLaunchLogsVisibleView.isChecked();
        state.launchSplashVisible = mLaunchSplashVisibleView.isChecked();
        state.launchLauncherVisible = mLaunchLauncherVisibleView.isChecked();
        state.launchRunOnBoot = mLaunchRunOnBootView.isChecked();
        state.launchSlug = mLaunchSlugView.getText() == null ? "" : mLaunchSlugView.getText().toString();
        state.abis = collectCheckedItems(mFlexboxAbisView);
        state.libs = collectCheckedItems(mFlexboxLibsView);
        state.permissions = collectCheckedItems(mFlexboxPermissionsView);
        state.signatureScheme = normalizeSignatureSchemeLabel(
                mSignatureSchemesView.getSelectedItem() == null ? "" : mSignatureSchemesView.getSelectedItem().toString()
        );
        if (mVerifiedKeyStoresView.getSelectedItemPosition() > 0) {
            Object selected = mVerifiedKeyStoresView.getSelectedItem();
            if (selected instanceof KeyStore) {
                state.keyStorePath = ((KeyStore) selected).getAbsolutePath();
            }
        }
        return state;
    }

    @NonNull
    private String normalizeSignatureSchemeLabel(@Nullable String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return value;
        int index = value.indexOf(" (");
        if (index > 0) {
            return value.substring(0, index).trim();
        }
        int selectedIndex = mSignatureSchemesView == null ? -1 : mSignatureSchemesView.getSelectedItemPosition();
        if (selectedIndex >= 0 && selectedIndex < SIGNATURE_SCHEMES.size()) {
            return SIGNATURE_SCHEMES.get(selectedIndex).first;
        }
        return value;
    }

    private void toggleAllFlexboxChildren(FlexboxLayout mFlexboxLibs) {
        boolean shouldTrack = canTrackBuildStateChanges();
        boolean previousSuppress = mSuppressBuildStateTracking;
        if (shouldTrack) {
            mSuppressBuildStateTracking = true;
        }

        boolean isAllChecked = true;
        try {
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
        } finally {
            mSuppressBuildStateTracking = previousSuppress;
        }
        if (shouldTrack) {
            onBuildUiControlChanged();
        }
    }

    private void initAbisChildren() {
        SUPPORTED_ABIS.forEach((abiText) -> {
            RoundCheckboxWithText child = new RoundCheckboxWithText(this, null);
            child.setText(abiText);
            child.setChecked(false);
            child.setEnabled(false);
            child.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
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
                        getString(R.string.text_the_following_solutions_can_be_referred_to) + getString(R.string.symbol_colon_with_blank) + "\n\n" +
                        "- " + getString(R.string.text_download_and_install_autojs6_including_above_abi, abiText) + "\n" +
                        "- " + getString(R.string.text_download_and_install_autojs6_including_all_abis) + " [" + getString(R.string.text_recommended) + "]\n\n" +
                        getString(R.string.text_download_link_for_autojs6) + getString(R.string.symbol_colon_with_blank) + "\n" +
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
        mInvalidAbis.clear();
        mUnavailableStandardAbis.clear();
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
            child.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
            mFlexboxLibsView.addView(child);
        });
    }

    private void syncLibsCheckedStates() {
        mInvalidLibs.clear();
        if (mProjectConfig == null) return;

        var configLibs = mProjectConfig.getLibs();
        if (configLibs.isEmpty()) return;

        // Create a new copy.
        // zh-CN: 创建一个新的副本
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
        // Add "default keystore" dropdown option.
        // zh-CN: 添加 "默认密钥库" 下拉选项.
        KeyStore defaultKeyStore = new KeyStore("", getString(R.string.text_default_key_store), "", "", "", false);
        verifiedKeyStores.add(defaultKeyStore);

        ArrayAdapter<KeyStore> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, verifiedKeyStores);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mVerifiedKeyStoresView.setAdapter(adapter);

        mKeyStoreViewModel.getVerifiedKeyStores().observe(this, keyStores -> {
            String selectedPath = null;
            Object selected = mVerifiedKeyStoresView.getSelectedItem();
            if (selected instanceof KeyStore) {
                selectedPath = ((KeyStore) selected).getAbsolutePath();
            }

            // Clear existing options, but keep the first element,
            // that is, the default keystore.
            // zh-CN: 清空现有的选项, 但保留第一个元素, 即默认密钥库.
            if (verifiedKeyStores.size() > 1) {
                verifiedKeyStores.subList(1, verifiedKeyStores.size()).clear();
            }
            verifiedKeyStores.addAll(keyStores);
            adapter.notifyDataSetChanged();

            if (!TextUtils.isEmpty(selectedPath)) {
                mPendingKeyStorePathForStateApply = selectedPath;
            }
            tryApplyPendingKeyStorePathSelection();
            onBuildUiControlChanged();
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
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> onBuildUiControlChanged());
            mFlexboxPermissionsView.addView(checkBox);
        });
    }

    private void syncPermissionsCheckedStates(@NonNull List<String> permissions) {
        for (int i = 0; i < mFlexboxPermissionsView.getChildCount(); i += 1) {
            View child = mFlexboxPermissionsView.getChildAt(i);
            CharSequence text = child instanceof RoundCheckboxWithText
                    ? ((RoundCheckboxWithText) child).getText()
                    : child instanceof CheckBox ? ((CheckBox) child).getText() : null;
            if (text == null) {
                continue;
            }
            String permission = text.toString().split("\n")[0].trim();
            boolean checked = hasPermission(permissions, permission);
            if (child instanceof RoundCheckboxWithText) {
                ((RoundCheckboxWithText) child).setChecked(checked);
            } else if (child instanceof CheckBox) {
                ((CheckBox) child).setChecked(checked);
            }
        }
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
        if (file.isDirectory()) {
            setSource(file);
            return;
        }
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
                    onSourcePresetReady();
                }, throwable -> {
                    mPackageNameView.setText(getString(R.string.format_default_package_name, file.getSimplifiedName().toLowerCase(Language.getPrefLanguage().getLocale())));
                    mVersionNameView.setText(R.string.default_build_apk_version_name);
                    mVersionCodeView.setText(R.string.default_build_apk_version_code);
                    mPackageNameParentView.setHint(R.string.text_package_name);
                    mVersionNameParentView.setHint(R.string.text_version_name);
                    mVersionCodeParentView.setHint(R.string.text_version_code);
                    mIconView.setVisibility(View.VISIBLE);
                    onSourcePresetReady();
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
        mSource = file.getPath();
        mSourcePathView.setText(file.getPath());
        if (!file.isDirectory()) {
            mIsProjectLevelBuilding = false;
            mProjectConfig = null;
            mAppConfigView.setVisibility(View.VISIBLE);
            mSourcePathContainerView.setVisibility(View.VISIBLE);
            return;
        }
        mProjectConfig = ProjectConfig.fromProjectDir(file.getPath());
        if (mProjectConfig == null) {
            return;
        }
        mIsProjectLevelBuilding = true;
        mProjectConfig.setSourcePath(file.getPath());
        mOutputPathView.setText(new File(file, mProjectConfig.getBuildDir()).getPath());

        mAppNameView.setText(mProjectConfig.getName());
        mPackageNameView.setText(mProjectConfig.getPackageName());
        mVersionNameView.setText(mProjectConfig.getVersionName());
        mVersionCodeView.setText(String.valueOf(mProjectConfig.getVersionCode()));

        mPackageNameParentView.setHint(R.string.text_package_name);
        mVersionNameParentView.setHint(R.string.text_version_name);
        mVersionCodeParentView.setHint(R.string.text_version_code);

        String iconPath = mProjectConfig.getIconPath();
        if (!TextUtils.isEmpty(iconPath)) {
            String absoluteIconPath = new File(file, iconPath).getPath();
            Drawable iconDrawable = Drawable.createFromPath(absoluteIconPath);
            if (iconDrawable != null) {
                mIconView.setImageDrawable(iconDrawable);
                mIsDefaultIcon = false;
            } else {
                mIsDefaultIcon = true;
            }
        } else {
            mIsDefaultIcon = true;
        }
        mIconView.setVisibility(View.VISIBLE);

        boolean previousSuppress = mSuppressBuildStateTracking;
        mSuppressBuildStateTracking = true;
        try {
            LaunchConfig launchConfig = mProjectConfig.getLaunchConfig();
            if (launchConfig != null) {
                mLaunchLogsVisibleView.setChecked(launchConfig.isLogsVisible());
                mLaunchSplashVisibleView.setChecked(launchConfig.isSplashVisible());
                mLaunchLauncherVisibleView.setChecked(launchConfig.isLauncherVisible());
                mLaunchRunOnBootView.setChecked(launchConfig.isRunOnBoot());
                mLaunchSlugView.setText(launchConfig.getSlug());
            }
            syncAbisCheckedStates();
            syncLibsCheckedStates();
            setSignatureSchemeByLabel(mProjectConfig.getSignatureScheme());
            syncPermissionsCheckedStates(mProjectConfig.getPermissions());
        } finally {
            mSuppressBuildStateTracking = previousSuppress;
        }

        mAppConfigView.setVisibility(View.VISIBLE);
        mSourcePathContainerView.setVisibility(View.VISIBLE);
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
        saveCurrentBuildProfileSilently();
        doBuildingApk();
    }

    private boolean checkInputs() {
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

        // Compute splitLineLength dynamically, considering wide characters.
        // zh-CN: 动态计算 splitLineLength, 考虑广范围的双宽字符.
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

        // Generate dialog content.
        // zh-CN: 生成对话框内容.
        String content = info.stream()
                .map(mapsList -> mapsList.stream()
                        .map(map -> map.entrySet().stream()
                                .map(entry -> getString(entry.getKey()) + getString(R.string.symbol_colon_with_blank) + "\n[ " + String.join(", ", entry.getValue()) + " ]")
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

    // Determine whether a character is a wide glyph.
    // zh-CN: 判断字符是否是广范围双宽字符.
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
        return determineProjectConfig(captureCurrentBuildUiState());
    }

    private ProjectConfig determineProjectConfig(@NonNull BuildUiState state) {
        ArrayList<String> abis = new ArrayList<>(state.abis);
        ArrayList<String> libs = new ArrayList<>(state.libs);
        ArrayList<String> permissions = new ArrayList<>(state.permissions);

        String sourcePath = state.sourcePath == null ? "" : state.sourcePath.trim();
        if (TextUtils.isEmpty(sourcePath)) {
            sourcePath = mSource == null ? "" : mSource;
        }

        ProjectConfig projectConfig;
        if (mProjectConfig != null) {
            projectConfig = mProjectConfig
                    .excludeDir(mProjectConfig.getBuildDir())
                    .setSourcePath(sourcePath);
            if (!mIsDefaultIcon && mIconView.getDrawable() != null) {
                projectConfig.setIconGetter(() -> BitmapUtils.drawableToBitmap(mIconView.getDrawable()));
            } else if (!TextUtils.isEmpty(mProjectConfig.getIconPath())) {
                projectConfig.setIconPath(new File(sourcePath, Objects.requireNonNull(mProjectConfig.getIconPath())).getPath());
            }
        } else {
            projectConfig = new ProjectConfig()
                    .setSourcePath(sourcePath)
                    .setIconGetter(mIsDefaultIcon ? null : () -> BitmapUtils.drawableToBitmap(mIconView.getDrawable()));
        }

        int fallbackVersionCode = mProjectConfig == null ? 1 : mProjectConfig.getVersionCode();
        if (fallbackVersionCode <= 0) {
            fallbackVersionCode = 1;
        }
        int versionCode = parseVersionCodeOrDefault(state.versionCode, fallbackVersionCode);
        String signatureScheme = TextUtils.isEmpty(state.signatureScheme)
                ? normalizeSignatureSchemeLabel(mSignatureSchemesView.getSelectedItem() == null ? "" : mSignatureSchemesView.getSelectedItem().toString())
                : state.signatureScheme;
        LaunchConfig launchConfig = projectConfig.getLaunchConfig();
        if (launchConfig == null) {
            launchConfig = new LaunchConfig();
        }
        launchConfig.setLogsVisible(state.launchLogsVisible);
        launchConfig.setSplashVisible(state.launchSplashVisible);
        launchConfig.setLauncherVisible(state.launchLauncherVisible);
        launchConfig.setRunOnBoot(state.launchRunOnBoot);
        launchConfig.setSlug(state.launchSlug);

        ProjectConfig resolved = projectConfig
                .setName(state.appName)
                .setPackageName(state.packageName)
                .setVersionName(state.versionName)
                .setVersionCode(versionCode)
                .setAbis(abis)
                .setLibs(libs)
                .setKeyStore(resolveKeyStoreByPath(state.keyStorePath))
                .setSignatureScheme(signatureScheme)
                .setPermissions(permissions);
        resolved.setLaunchConfig(launchConfig);
        return resolved;
    }

    @Nullable
    private KeyStore resolveKeyStoreByPath(@Nullable String keyStorePath) {
        if (TextUtils.isEmpty(keyStorePath) || mVerifiedKeyStoresView == null) {
            return null;
        }
        for (int i = 1; i < mVerifiedKeyStoresView.getCount(); i++) {
            Object item = mVerifiedKeyStoresView.getItemAtPosition(i);
            if (!(item instanceof KeyStore)) {
                continue;
            }
            KeyStore keyStore = (KeyStore) item;
            if (keyStorePath.equalsIgnoreCase(keyStore.getAbsolutePath())) {
                return keyStore;
            }
        }
        return null;
    }

    private int parseVersionCodeOrDefault(@Nullable String rawVersionCode, int fallback) {
        String value = rawVersionCode == null ? "" : rawVersionCode.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
    // zh-CN: Abort build cooperatively and trigger cleanup as early as possible.
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
    // zh-CN: Best-effort cleanup for workspace and output artifacts.
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
    @SuppressLint("CheckResult")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_CODE) {
            AppsIconSelectActivity.getDrawableFromIntent(getApplicationContext(), data)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(drawable -> {
                        mIconView.setImageDrawable(drawable);
                        mIsDefaultIcon = false;
                    }, Throwable::printStackTrace);
            return;
        }

        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_EXPORT_JSON) {
            doExportBuildProfileToUri(uri);
            return;
        }
        if (requestCode == REQ_IMPORT_JSON) {
            doImportBuildProfileFromUri(uri);
        }
    }

    private static final class BuildFieldPolicy {
        boolean fixedSourcePath = false;
        boolean fixedIcon = false;
        boolean fixedOutputPath = false;
        boolean fixedAppName = false;
        boolean fixedPackageName = false;
        boolean fixedVersionName = false;
        boolean fixedVersionCode = false;
        boolean fixedLaunchLogsVisible = false;
        boolean fixedLaunchSplashVisible = false;
        boolean fixedLaunchLauncherVisible = false;
        boolean fixedLaunchRunOnBoot = false;
        boolean fixedLaunchSlug = false;
        boolean fixedAbis = false;
        boolean fixedLibs = true;
        boolean fixedSignatureScheme = true;
        boolean fixedKeyStore = true;
        boolean fixedPermissions = true;

        @NonNull
        static BuildFieldPolicy defaultPolicy() {
            return new BuildFieldPolicy();
        }

        @NonNull
        static BuildFieldPolicy projectJsonPolicy() {
            BuildFieldPolicy policy = new BuildFieldPolicy();
            policy.fixedSourcePath = true;
            policy.fixedIcon = true;
            policy.fixedOutputPath = true;
            policy.fixedAppName = true;
            policy.fixedPackageName = true;
            policy.fixedVersionName = true;
            policy.fixedVersionCode = true;
            policy.fixedLaunchLogsVisible = true;
            policy.fixedLaunchSplashVisible = true;
            policy.fixedLaunchLauncherVisible = true;
            policy.fixedLaunchRunOnBoot = true;
            policy.fixedLaunchSlug = true;
            policy.fixedAbis = true;
            policy.fixedLibs = true;
            policy.fixedSignatureScheme = true;
            policy.fixedKeyStore = true;
            policy.fixedPermissions = true;
            return policy;
        }

        @NonNull
        BuildFieldPolicy copy() {
            BuildFieldPolicy copied = new BuildFieldPolicy();
            copied.fixedSourcePath = fixedSourcePath;
            copied.fixedIcon = fixedIcon;
            copied.fixedOutputPath = fixedOutputPath;
            copied.fixedAppName = fixedAppName;
            copied.fixedPackageName = fixedPackageName;
            copied.fixedVersionName = fixedVersionName;
            copied.fixedVersionCode = fixedVersionCode;
            copied.fixedLaunchLogsVisible = fixedLaunchLogsVisible;
            copied.fixedLaunchSplashVisible = fixedLaunchSplashVisible;
            copied.fixedLaunchLauncherVisible = fixedLaunchLauncherVisible;
            copied.fixedLaunchRunOnBoot = fixedLaunchRunOnBoot;
            copied.fixedLaunchSlug = fixedLaunchSlug;
            copied.fixedAbis = fixedAbis;
            copied.fixedLibs = fixedLibs;
            copied.fixedSignatureScheme = fixedSignatureScheme;
            copied.fixedKeyStore = fixedKeyStore;
            copied.fixedPermissions = fixedPermissions;
            return copied;
        }

        @NonNull
        JSONObject toJson() {
            JSONObject out = new JSONObject();
            try {
                out.put(BuildConfigStore.FIELD_FIXED_SOURCE_PATH, fixedSourcePath);
                out.put(BuildConfigStore.FIELD_FIXED_ICON, fixedIcon);
                out.put(BuildConfigStore.FIELD_FIXED_OUTPUT_PATH, fixedOutputPath);
                out.put(BuildConfigStore.FIELD_FIXED_APP_NAME, fixedAppName);
                out.put(BuildConfigStore.FIELD_FIXED_PACKAGE_NAME, fixedPackageName);
                out.put(BuildConfigStore.FIELD_FIXED_VERSION_NAME, fixedVersionName);
                out.put(BuildConfigStore.FIELD_FIXED_VERSION_CODE, fixedVersionCode);
                out.put(BuildConfigStore.FIELD_FIXED_LAUNCH_LOGS_VISIBLE, fixedLaunchLogsVisible);
                out.put(BuildConfigStore.FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, fixedLaunchSplashVisible);
                out.put(BuildConfigStore.FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, fixedLaunchLauncherVisible);
                out.put(BuildConfigStore.FIELD_FIXED_LAUNCH_RUN_ON_BOOT, fixedLaunchRunOnBoot);
                out.put(BuildConfigStore.FIELD_FIXED_LAUNCH_SLUG, fixedLaunchSlug);
                out.put(BuildConfigStore.FIELD_FIXED_ABIS, fixedAbis);
                out.put(BuildConfigStore.FIELD_FIXED_LIBS, fixedLibs);
                out.put(BuildConfigStore.FIELD_FIXED_SIGNATURE_SCHEME, fixedSignatureScheme);
                out.put(BuildConfigStore.FIELD_FIXED_KEY_STORE, fixedKeyStore);
                out.put(BuildConfigStore.FIELD_FIXED_PERMISSIONS, fixedPermissions);
            } catch (JSONException ignored) {
                // Keep best-effort snapshot for profile persistence.
            }
            return out;
        }

        @NonNull
        static BuildFieldPolicy fromJson(@Nullable JSONObject json) {
            BuildFieldPolicy policy = defaultPolicy();
            if (json == null) return policy;
            policy.fixedSourcePath = json.optBoolean(BuildConfigStore.FIELD_FIXED_SOURCE_PATH, policy.fixedSourcePath);
            policy.fixedIcon = json.optBoolean(BuildConfigStore.FIELD_FIXED_ICON, policy.fixedIcon);
            policy.fixedOutputPath = json.optBoolean(BuildConfigStore.FIELD_FIXED_OUTPUT_PATH, policy.fixedOutputPath);
            policy.fixedAppName = json.optBoolean(BuildConfigStore.FIELD_FIXED_APP_NAME, policy.fixedAppName);
            policy.fixedPackageName = json.optBoolean(BuildConfigStore.FIELD_FIXED_PACKAGE_NAME, policy.fixedPackageName);
            policy.fixedVersionName = json.optBoolean(BuildConfigStore.FIELD_FIXED_VERSION_NAME, policy.fixedVersionName);
            policy.fixedVersionCode = json.optBoolean(BuildConfigStore.FIELD_FIXED_VERSION_CODE, policy.fixedVersionCode);
            policy.fixedLaunchLogsVisible = json.optBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_LOGS_VISIBLE, policy.fixedLaunchLogsVisible);
            policy.fixedLaunchSplashVisible = json.optBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, policy.fixedLaunchSplashVisible);
            policy.fixedLaunchLauncherVisible = json.optBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, policy.fixedLaunchLauncherVisible);
            policy.fixedLaunchRunOnBoot = json.optBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_RUN_ON_BOOT, policy.fixedLaunchRunOnBoot);
            policy.fixedLaunchSlug = json.optBoolean(BuildConfigStore.FIELD_FIXED_LAUNCH_SLUG, policy.fixedLaunchSlug);
            policy.fixedAbis = json.optBoolean(BuildConfigStore.FIELD_FIXED_ABIS, policy.fixedAbis);
            policy.fixedLibs = json.optBoolean(BuildConfigStore.FIELD_FIXED_LIBS, policy.fixedLibs);
            policy.fixedSignatureScheme = json.optBoolean(BuildConfigStore.FIELD_FIXED_SIGNATURE_SCHEME, policy.fixedSignatureScheme);
            policy.fixedKeyStore = json.optBoolean(BuildConfigStore.FIELD_FIXED_KEY_STORE, policy.fixedKeyStore);
            policy.fixedPermissions = json.optBoolean(BuildConfigStore.FIELD_FIXED_PERMISSIONS, policy.fixedPermissions);
            return policy;
        }
    }

    private static final class BuildUiState {
        String sourcePath = "";
        String outputPath = "";
        String appName = "";
        String packageName = "";
        String versionName = "";
        String versionCode = "";
        boolean launchLogsVisible = true;
        boolean launchSplashVisible = true;
        boolean launchLauncherVisible = true;
        boolean launchRunOnBoot = false;
        String launchSlug = "";
        String signatureScheme = "";
        String keyStorePath = "";
        ArrayList<String> abis = new ArrayList<>();
        ArrayList<String> libs = new ArrayList<>();
        ArrayList<String> permissions = new ArrayList<>();

        @NonNull
        BuildUiState copy() {
            BuildUiState copied = new BuildUiState();
            copied.sourcePath = sourcePath;
            copied.outputPath = outputPath;
            copied.appName = appName;
            copied.packageName = packageName;
            copied.versionName = versionName;
            copied.versionCode = versionCode;
            copied.launchLogsVisible = launchLogsVisible;
            copied.launchSplashVisible = launchSplashVisible;
            copied.launchLauncherVisible = launchLauncherVisible;
            copied.launchRunOnBoot = launchRunOnBoot;
            copied.launchSlug = launchSlug;
            copied.signatureScheme = signatureScheme;
            copied.keyStorePath = keyStorePath;
            copied.abis = new ArrayList<>(abis);
            copied.libs = new ArrayList<>(libs);
            copied.permissions = new ArrayList<>(permissions);
            return copied;
        }

        @NonNull
        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put(BuildConfigStore.STATE_SOURCE_PATH, sourcePath);
                json.put(BuildConfigStore.STATE_OUTPUT_PATH, outputPath);
                json.put(BuildConfigStore.STATE_APP_NAME, appName);
                json.put(BuildConfigStore.STATE_PACKAGE_NAME, packageName);
                json.put(BuildConfigStore.STATE_VERSION_NAME, versionName);
                json.put(BuildConfigStore.STATE_VERSION_CODE, versionCode);
                json.put(BuildConfigStore.STATE_LAUNCH_LOGS_VISIBLE, launchLogsVisible);
                json.put(BuildConfigStore.STATE_LAUNCH_SPLASH_VISIBLE, launchSplashVisible);
                json.put(BuildConfigStore.STATE_LAUNCH_LAUNCHER_VISIBLE, launchLauncherVisible);
                json.put(BuildConfigStore.STATE_LAUNCH_RUN_ON_BOOT, launchRunOnBoot);
                json.put(BuildConfigStore.STATE_LAUNCH_SLUG, launchSlug);
                json.put(BuildConfigStore.STATE_SIGNATURE_SCHEME, signatureScheme);
                json.put(BuildConfigStore.STATE_KEY_STORE_PATH, keyStorePath);
                json.put(BuildConfigStore.STATE_ABIS, new JSONArray(abis));
                json.put(BuildConfigStore.STATE_LIBS, new JSONArray(libs));
                json.put(BuildConfigStore.STATE_PERMISSIONS, new JSONArray(permissions));
            } catch (JSONException ignored) {
                // Keep best-effort snapshot for profile persistence.
            }
            return json;
        }

        @NonNull
        static BuildUiState fromJson(@NonNull JSONObject json) {
            BuildUiState state = new BuildUiState();
            state.sourcePath = json.optString(BuildConfigStore.STATE_SOURCE_PATH, "");
            state.outputPath = json.optString(BuildConfigStore.STATE_OUTPUT_PATH, "");
            state.appName = json.optString(BuildConfigStore.STATE_APP_NAME, "");
            state.packageName = json.optString(BuildConfigStore.STATE_PACKAGE_NAME, "");
            state.versionName = json.optString(BuildConfigStore.STATE_VERSION_NAME, "");
            state.versionCode = json.optString(BuildConfigStore.STATE_VERSION_CODE, "");
            state.launchLogsVisible = json.optBoolean(BuildConfigStore.STATE_LAUNCH_LOGS_VISIBLE, true);
            state.launchSplashVisible = json.optBoolean(BuildConfigStore.STATE_LAUNCH_SPLASH_VISIBLE, true);
            state.launchLauncherVisible = json.optBoolean(BuildConfigStore.STATE_LAUNCH_LAUNCHER_VISIBLE, true);
            state.launchRunOnBoot = json.optBoolean(BuildConfigStore.STATE_LAUNCH_RUN_ON_BOOT, false);
            state.launchSlug = json.optString(BuildConfigStore.STATE_LAUNCH_SLUG, "");
            state.signatureScheme = json.optString(BuildConfigStore.STATE_SIGNATURE_SCHEME, "");
            state.keyStorePath = json.optString(BuildConfigStore.STATE_KEY_STORE_PATH, "");
            state.abis = readStringArray(json.optJSONArray(BuildConfigStore.STATE_ABIS));
            state.libs = readStringArray(json.optJSONArray(BuildConfigStore.STATE_LIBS));
            state.permissions = readStringArray(json.optJSONArray(BuildConfigStore.STATE_PERMISSIONS));
            return state;
        }

        @NonNull
        private static ArrayList<String> readStringArray(@Nullable JSONArray array) {
            ArrayList<String> out = new ArrayList<>();
            if (array == null) return out;
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) {
                    dedup.add(value);
                }
            }
            out.addAll(dedup);
            return out;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BuildUiState)) return false;
            BuildUiState that = (BuildUiState) other;
            return Objects.equals(sourcePath, that.sourcePath)
                   && Objects.equals(outputPath, that.outputPath)
                   && Objects.equals(appName, that.appName)
                   && Objects.equals(packageName, that.packageName)
                   && Objects.equals(versionName, that.versionName)
                   && Objects.equals(versionCode, that.versionCode)
                   && launchLogsVisible == that.launchLogsVisible
                   && launchSplashVisible == that.launchSplashVisible
                   && launchLauncherVisible == that.launchLauncherVisible
                   && launchRunOnBoot == that.launchRunOnBoot
                   && Objects.equals(launchSlug, that.launchSlug)
                   && Objects.equals(signatureScheme, that.signatureScheme)
                   && Objects.equals(keyStorePath, that.keyStorePath)
                   && Objects.equals(abis, that.abis)
                   && Objects.equals(libs, that.libs)
                   && Objects.equals(permissions, that.permissions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    sourcePath,
                    outputPath,
                    appName,
                    packageName,
                    versionName,
                    versionCode,
                    launchLogsVisible,
                    launchSplashVisible,
                    launchLauncherVisible,
                    launchRunOnBoot,
                    launchSlug,
                    signatureScheme,
                    keyStorePath,
                    abis,
                    libs,
                    permissions
            );
        }
    }

    private static final class ProfileEntry {
        final String internalName;
        final String displayName;

        ProfileEntry(@NonNull String internalName, @NonNull String displayName) {
            this.internalName = internalName;
            this.displayName = displayName;
        }
    }

    public static void launch(Context context, String extraSource) {
        Intent intent = new Intent(context, BuildActivity.class)
                .putExtra(BuildActivity.EXTRA_SOURCE, extraSource);
        IntentUtils.startSafely(intent, context);
    }
}
