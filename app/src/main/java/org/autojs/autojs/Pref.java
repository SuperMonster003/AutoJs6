package org.autojs.autojs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.runtime.accessibility.AccessibilityConfig;

import org.autojs.autojs.autojs.key.GlobalKeyObserver;

import java.io.File;
import java.util.Objects;

/**
 * Created by Stardust on 2017/1/31.
 */
public class Pref {

    private static final SharedPreferences DISPOSABLE_BOOLEAN = GlobalAppContext.get().getSharedPreferences("DISPOSABLE_BOOLEAN", Context.MODE_PRIVATE);
    private static final String KEY_SERVER_ADDRESS = "KEY_SERVER_ADDRESS";
    private static final String KEY_FLOATING_MENU_SHOWN = "KEY_FLOATING_MENU_SHOWN";
    private static final String KEY_APP_LANG_INDEX = "KEY_APP_LANG_INDEX";
    private static final String KEY_EDITOR_THEME = "editor.theme";
    private static final String KEY_EDITOR_TEXT_SIZE = "editor.textSize";

    private static final SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener = (p, key) -> {
        if (key.equals(getString(R.string.key_guard_mode))) {
            AccessibilityConfig.setIsUnintendedGuardEnabled(p.getBoolean(getString(R.string.key_guard_mode), false));
        } else if ((key.equals(getString(R.string.key_use_volume_control_record)) || key.equals(getString(R.string.key_use_volume_control_running)))
                && p.getBoolean(key, false)) {
            GlobalKeyObserver.init();
        }
    };

    static {
        AccessibilityConfig.setIsUnintendedGuardEnabled(def().getBoolean(getString(R.string.key_guard_mode), false));
    }

    private static SharedPreferences def() {
        return PreferenceManager.getDefaultSharedPreferences(GlobalAppContext.get());
    }

    private static boolean getDisposableBoolean(String key, boolean defaultValue) {
        boolean b = DISPOSABLE_BOOLEAN.getBoolean(key, defaultValue);
        if (b == defaultValue) {
            DISPOSABLE_BOOLEAN.edit().putBoolean(key, !defaultValue).apply();
        }
        return b;
    }

    public static boolean isNightModeEnabled() {
        return def().getBoolean(getString(R.string.key_night_mode), false);
    }

    public static boolean isFirstGoToAccessibilitySetting() {
        return getDisposableBoolean("isFirstGoToAccessibilitySetting", false);
    }

    public static boolean isRunningVolumeControlEnabled() {
        return def().getBoolean(getString(R.string.key_use_volume_control_running), true);
    }

    public static boolean shouldEnableAccessibilityServiceByRoot() {
        return def().getBoolean(getString(R.string.key_enable_a11y_service_with_root_access), true);
    }

    public static boolean shouldEnableAccessibilityServiceBySecureSettings() {
        return def().getBoolean(getString(R.string.key_enable_a11y_service_with_secure_settings), true);
    }

    private static String getString(int id) {
        return GlobalAppContext.getString(id);
    }

    public static boolean isFirstUsing() {
        return getDisposableBoolean("isFirstUsing", true);
    }

    static {
        def().registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    }

    public static String getServerAddressOrDefault(String defaultAddress) {
        return def().getString(KEY_SERVER_ADDRESS, defaultAddress);
    }

    public static void saveServerAddress(String address) {
        def().edit().putString(KEY_SERVER_ADDRESS, address).apply();
    }

    public static boolean isRecordToastEnabled() {
        return def().getBoolean(getString(R.string.key_record_toast), true);
    }

    public static boolean rootRecordGeneratesBinary() {
        return Objects.equals(def().getString(getString(R.string.key_root_record_out_file_type), getString(R.string.default_value_binary)), getString(R.string.value_root_record_out_file_type_binary));
    }

    public static boolean isStableModeEnabled() {
        return def().getBoolean(getString(R.string.key_stable_mode), false);
    }

    public static String getDocumentationUrl() {
        String docSource = def().getString(getString(R.string.key_documentation_source), null);
        if (docSource == null || docSource.equals(getString(R.string.value_documentation_source_local))) {
            return "file:///android_asset/docs/";
        } else {
            return "https://www.autojs.org/assets/autojs/docs/";
        }
    }

    public static boolean isFloatingMenuShown() {
        return def().getBoolean(KEY_FLOATING_MENU_SHOWN, false);
    }

    public static void setFloatingMenuShown(boolean checked) {
        def().edit().putBoolean(KEY_FLOATING_MENU_SHOWN, checked).apply();
    }

    public static String getCurrentTheme() {
        return def().getString(KEY_EDITOR_THEME, null);
    }

    public static void setCurrentTheme(String theme) {
        def().edit().putString(KEY_EDITOR_THEME, theme).apply();
    }

    public static void setAppLanguageIndex(int index) {
        def().edit().putInt(KEY_APP_LANG_INDEX, index).apply();
    }

    public static void setEditorTextSize(int value) {
        def().edit().putInt(KEY_EDITOR_TEXT_SIZE, value).apply();
    }

    public static int getEditorTextSize(int defValue) {
        return def().getInt(KEY_EDITOR_TEXT_SIZE, defValue);
    }

    public static String getScriptDirPath() {
        String dir = def().getString(getString(R.string.key_script_dir_path),
                getString(R.string.default_value_script_dir_path));
        return new File(Environment.getExternalStorageDirectory(), dir).getPath();
    }

    public static boolean isForegroundServiceEnabled() {
        return def().getBoolean(getString(R.string.key_foreground_service), false);
    }

    public static int getAppLanguageIndex() {
        return def().getInt(KEY_APP_LANG_INDEX, 0);
    }
}
