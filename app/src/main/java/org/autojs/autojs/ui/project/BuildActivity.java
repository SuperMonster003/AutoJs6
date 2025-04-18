package org.autojs.autojs.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.jaredrummler.apkparser.ApkParser;
import com.jaredrummler.apkparser.model.ApkMeta;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import org.autojs.autojs.apkbuilder.ApkBuilder;
import org.autojs.autojs.apkbuilder.keystore.KeyStore;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.project.ProjectConfig;
import org.autojs.autojs.runtime.api.AppUtils;
import org.autojs.autojs.runtime.api.AppUtils.Companion.SimpleVersionInfo;
import org.autojs.autojs.runtime.api.augment.pinyin.Pinyin;
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
import org.autojs.autojs.util.EnvironmentUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.ActivityBuildBinding;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.autojs.autojs.apkbuilder.ApkBuilder.TEMPLATE_APK_NAME;
import static org.autojs.autojs.util.StringUtils.key;

/**
 * Created by Stardust on Oct 22, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2023.
 *
 * @noinspection ResultOfMethodCallIgnored
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

    private final Map<String, Integer> SUPPORTED_PERMISSIONS = new TreeMap<>() {{
        put("android.permission.ACCESS_COARSE_LOCATION", R.string.text_permission_desc_access_coarse_location);
        put("android.permission.ACCESS_FINE_LOCATION", R.string.text_permission_desc_access_fine_location);
        put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", R.string.text_permission_desc_access_location_extra_commands);
        put("android.permission.ACCESS_NETWORK_STATE", R.string.text_permission_desc_access_network_state);
        put("android.permission.ACCESS_WIFI_STATE", R.string.text_permission_desc_access_wifi_state);
        put("android.permission.BLUETOOTH", R.string.text_permission_desc_bluetooth);
        put("android.permission.BLUETOOTH_ADMIN", R.string.text_permission_desc_bluetooth_admin);
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
        put("com.termux.permission.RUN_COMMAND", R.string.text_permission_desc_termux_run_command);
        put("moe.shizuku.manager.permission.API_V23", R.string.text_permission_desc_shizuku);
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
        for (ApkBuilder.Libs entry : ApkBuilder.Libs.getEntries()) {
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
        ViewUtils.excludeFloatingActionButtonFromNavigationBar(binding.fab);

        ViewUtils.excludePaddingClippableViewFromNavigationBar(binding.scrollView);

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
            // 清空现有的选项，但保留第一个元素，即默认密钥库
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
            checkBox.setGravity(Gravity.CENTER_VERTICAL);
            checkBox.setTextSize(12);
            int marginInPixels = (int) (8 * getResources().getDisplayMetrics().density);
            checkBox.setPadding(marginInPixels, 0, 0, 0);
            checkBox.setChecked(false);
            mFlexboxPermissionsView.addView(checkBox);
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
        ApkParser parser;
        try {
            parser = ApkParser.create(getPackageManager(), packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        try {
            ApkMeta meta = parser.getApkMeta();
            if (meta == null || meta.usesPermissions == null) {
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
            parser.close();
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
    protected void onNewIntent(Intent intent) {
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void doBuildingApk() {
        ProjectConfig projectConfig = determineProjectConfig();
        File buildPath = new File(getCacheDir(), "build/");
        File outApk = new File(mOutputPathView.getText().toString(),
                String.format("%s_v%s.apk", projectConfig.getName(), projectConfig.getVersionName()));
        showProgressDialog();
        Observable.fromCallable(() -> callApkBuilder(buildPath, outApk, projectConfig))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apkBuilder -> {
                    if (apkBuilder != null) {
                        onBuildSuccessful(outApk);
                    } else {
                        onBuildFailed(new FileNotFoundException(TEMPLATE_APK_NAME));
                    }
                }, this::onBuildFailed);
    }

    private ProjectConfig determineProjectConfig() {
        ArrayList<String> abis = collectCheckedItems(mFlexboxAbisView);
        ArrayList<String> libs = collectCheckedItems(mFlexboxLibsView);
        ArrayList<String> permissions = collectCheckedItems(mFlexboxPermissionsView);

        ProjectConfig projectConfig;
        if (mProjectConfig != null) {
            projectConfig = mProjectConfig
                    .excludeDir(new File(mSource, mProjectConfig.getBuildDir()))
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
                .prepare()
                .withConfig(projectConfig)
                .build()
                .sign()
                .cleanWorkspace();
    }

    private void showProgressDialog() {
        mProgressDialog = new MaterialDialog.Builder(this)
                .progress(true, 100)
                .content(R.string.text_in_progress)
                .cancelable(false)
                .show();
    }

    private void onBuildFailed(Throwable error) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        ErrorDialogActivity.showErrorDialog(this, R.string.text_failed_to_build, error.getMessage());
        Log.e(LOG_TAG, "Failed to build", error);
    }

    private void onBuildSuccessful(File outApk) {
        Explorers.workspace().refreshAll();
        mProgressDialog.dismiss();
        mProgressDialog = null;
        new MaterialDialog.Builder(this)
                .title(R.string.text_build_succeeded)
                .content(getString(R.string.format_build_succeeded, outApk.getPath()))
                .positiveText(R.string.text_install)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> IntentUtils.installApk(BuildActivity.this, outApk.getPath()))
                .negativeText(R.string.text_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.dialog_button_file_information)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral((dialog, which) -> ApkInfoDialogManager.showApkInfoDialog(dialog.getContext(), outApk))
                .show();
    }

    @Override
    public void onPrepare(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_prepare);
    }

    @Override
    public void onBuild(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_build);
    }

    @Override
    public void onSign(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_package);
    }

    @Override
    public void onClean(@NonNull ApkBuilder builder) {
        mProgressDialog.setContent(R.string.apk_builder_clean);
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
        context.startActivity(intent);
    }

}
