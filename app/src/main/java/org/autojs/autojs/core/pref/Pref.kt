package org.autojs.autojs.core.pref

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.annotation.ScriptInterfaceCompatible
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.AutoNightMode.Companion.isFunctional
import org.autojs.autojs.util.ViewUtils.isNightModeYes
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.R
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.*

/**
 * Created by Stardust on Jan 31, 2017.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 */
object Pref {

    private val globalAppContext: Context = GlobalAppContext.get()
    private val resources: Resources = globalAppContext.resources

    private val sPref by lazy { PreferenceManager.getDefaultSharedPreferences(globalAppContext) }

    private val onSharedPreferenceChangeListener = OnSharedPreferenceChangeListener { _, _ /* key */ ->
        // if (key == key(R.string.key_guard_mode)) {
        //     AccessibilityConfig.refreshUnintendedGuardState()
        // }
    }

    init {
        // AccessibilityConfig.refreshUnintendedGuardState()
        registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        if (!containsKey(R.string.key_auto_night_mode_enabled)) {
            ViewUtils.isAutoNightModeEnabled = isFunctional().also {
                AppCompatDelegate.setDefaultNightMode(
                    when (it) {
                        true -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                    }
                )
            }
        }
        if (!containsKey(R.string.key_night_mode_enabled)) {
            ViewUtils.isNightModeEnabled = isNightModeYes(app)
        }
    }

    @JvmStatic
    fun get(): SharedPreferences = sPref

    @JvmStatic
    val screenCaptureRequestDelay: Int
        get() {
            // Default 350ms is a practical value for most ROMs' permission dialog fade-out animation.
            // zh-CN: 默认 350ms 通常可覆盖多数 ROM 授权弹窗的渐隐动画时间.
            val defValue = resources.getInteger(R.integer.screen_capture_request_delay_default_value)
            val minValue = resources.getInteger(R.integer.screen_capture_request_delay_min_value)
            val maxValue = resources.getInteger(R.integer.screen_capture_request_delay_max_value)
            val value = getInt(R.string.key_screen_capture_request_delay, defValue)
            return value.coerceIn(minValue, maxValue)
        }

    @JvmStatic
    val isExtendingJsBuildInObjectsEnabled
        get() = getBoolean(
            R.string.key_extending_js_build_in_objects,
            resources.getBoolean(R.bool.pref_extending_js_build_in_objects),
        )

    @JvmStatic
    val isGuardModeEnabled
        get() = getBoolean(
            R.string.key_guard_mode,
            resources.getBoolean(R.bool.pref_guard_mode)
        )

    @JvmStatic
    val isUseVolumeControlRunningEnabled
        get() = getBoolean(
            R.string.key_use_volume_control_running,
            resources.getBoolean(R.bool.pref_use_volume_control_running),
        )

    @JvmStatic
    val isUseVolumeControlRecordEnabled
        get() = getBoolean(
            R.string.key_use_volume_control_record,
            resources.getBoolean(R.bool.pref_use_volume_control_record),
        )

    @JvmStatic
    val isAutoCheckForUpdatesEnabled
        get() = getBoolean(
            R.string.key_auto_check_for_updates,
            resources.getBoolean(R.bool.pref_auto_check_for_updates),
        )

    private val lastUpdatesCheckedTimestamp: Long
        get() = getTimestamp(R.string.key_last_updates_checked)

    @JvmStatic
    val lastUpdatesCheckedTimeString: String?
        get() {
            val ts = lastUpdatesCheckedTimestamp
            if (ts < 0) {
                return null
            }
            val dt = DateTime(ts)
            val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")
            return fmt.print(dt)
        }

    @JvmStatic
    val lastNoNewerUpdatesTimestamp: Long
        get() = getTimestamp(R.string.key_last_no_newer_updates)

    @JvmStatic
    val lastUpdatesPostponedTimestamp: Long
        get() = getTimestamp(R.string.key_last_updates_postponed)

    @JvmStatic
    val lastUpdatesAutoCheckedTimestamp: Long
        get() = getTimestamp(R.string.key_last_updates_auto_checked)

    @JvmStatic
    val keyKeepScreenOnWhenInForeground: String
        get() = getString(R.string.key_keep_screen_on_when_in_foreground, key(R.string.default_key_keep_screen_on_when_in_foreground))

    @JvmStatic
    var rootMode: RootUtils.RootMode
        get() {
            val key = getString(R.string.key_root_mode, RootUtils.RootMode.AUTO_DETECT.key)
            return RootUtils.RootMode.getRootMode(key)
        }
        set(mode) {
            putString(key(R.string.key_root_mode), mode.key)
        }

    val isStableModeEnabled
        get() = getBoolean(
            R.string.key_stable_mode,
            resources.getBoolean(R.bool.pref_stable_mode)
        )

    val isGestureObservingEnabled
        get() = getBoolean(
            R.string.key_gesture_observing,
            resources.getBoolean(R.bool.pref_gesture_observing)
        )

    @JvmStatic
    val isQuickRestartEnabled
        get() = getString(R.string.key_restart_strategy, key(R.string.default_key_restart_strategy)) == key(R.string.key_restart_strategy_quick)

    @JvmStatic
    val isScheduledRestartPreferredWorkManager
        get() = getString(R.string.key_scheduled_restart_backend, key(R.string.default_key_scheduled_restart_backend)) == key(R.string.key_scheduled_restart_backend_work_manager)

    @JvmStatic
    val isHiddenFilesShown
        get() = getString(R.string.key_hidden_files, key(R.string.default_key_hidden_files)) == key(R.string.key_hidden_files_show)

    @JvmStatic
    val isFileExtensionsShownForAll
        get() = getString(R.string.key_file_extensions, key(R.string.default_key_file_extensions)) == key(R.string.key_file_extensions_show_all)

    @JvmStatic
    val isFileExtensionsShownForAllButExecutable
        get() = getString(R.string.key_file_extensions, key(R.string.default_key_file_extensions)) == key(R.string.key_file_extensions_show_all_but_executable)

    @JvmStatic
    val isFileExtensionsHidden
        get() = getString(R.string.key_file_extensions, key(R.string.default_key_file_extensions)) == key(R.string.key_file_extensions_not_show)

    @JvmStatic
    val isRecordToastEnabled
        get() = getBoolean(
            R.string.key_record_toast,
            resources.getBoolean(R.bool.pref_record_toast)
        )

    @JvmStatic
    var currentTheme: String?
        get() = getStringOrNull(R.string.key_editor_theme)
        set(theme) {
            putString(R.string.key_editor_theme, theme)
        }

    private fun getTimestamp(@KeyRes keyRes: Int): Long = getLong(keyRes, -1)

    @JvmStatic
    @ScriptInterfaceCompatible
    fun getScriptDirPath() = WorkingDirectoryUtils.path

    @JvmStatic
    fun getKeyStorePath(): String {
        return getScriptDirPath() + "/.KeyStore/"
    }

    @JvmStatic
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {
        sPref.registerOnSharedPreferenceChangeListener(listener)
    }

    @JvmStatic
    fun refreshLastUpdatesCheckedTimestamp() = updateTimestamp(R.string.key_last_updates_checked)

    @JvmStatic
    fun refreshLastNoNewerUpdatesTimestamp() = updateTimestamp(R.string.key_last_no_newer_updates)

    @JvmStatic
    fun refreshLastUpdatesPostponedTimestamp() = updateTimestamp(R.string.key_last_updates_postponed)

    @JvmStatic
    fun refreshLastUpdatesAutoCheckedTimestamp() = updateTimestamp(R.string.key_last_updates_auto_checked)

    @JvmStatic
    fun clearUpdatesCheckedStates() = sPref.edit {
        remove(key(R.string.key_last_no_newer_updates))
            .remove(key(R.string.key_last_updates_postponed))
            .remove(key(R.string.key_last_updates_checked))
            .remove(key(R.string.key_last_updates_auto_checked))
    }

    @JvmStatic
    fun shouldStartA11yServiceWithRoot() = getBoolean(
        R.string.key_enable_a11y_service_with_root_access,
        resources.getBoolean(R.bool.pref_enable_a11y_service_with_root_access),
    )

    @JvmStatic
    fun shouldStartA11yServiceWithSecureSettings() = getBoolean(
        R.string.key_enable_a11y_service_with_secure_settings,
        resources.getBoolean(R.bool.pref_enable_a11y_service_with_secure_settings)
    )

    private fun updateTimestamp(@KeyRes keyRes: Int) = putLong(keyRes, System.currentTimeMillis())

    @JvmStatic
    fun getServerAddress(): String = getString(R.string.key_server_address, if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS)

    @JvmStatic
    fun setServerAddress(address: String?) = putString(R.string.key_server_address, address)

    @JvmStatic
    fun rootRecordGeneratesBinary() = getString(R.string.key_root_record_out_file_type, key(R.string.default_key_root_record_out_file_type)) == key(R.string.key_root_record_out_file_type_binary)

    @JvmStatic
    fun setEditorTextSize(value: Int) = putInt(R.string.key_editor_text_size, value)

    @JvmStatic
    fun getEditorTextSize(defValue: Int): Int = getInt(R.string.key_editor_text_size, defValue)

    @JvmStatic
    fun putString(@KeyRes keyRes: Int, value: String?) = putString(key(keyRes), value)

    @JvmStatic
    fun putString(key: String, value: String?) = sPref.edit { putString(key, value) }

    @JvmStatic
    fun putStringSync(key: String, value: String?) = sPref.edit(commit = true) { putString(key, value) }

    @JvmStatic
    fun getString(@KeyRes keyRes: Int, @KeyRes defKeyRes: Int): String = sPref.getString(key(keyRes), key(defKeyRes))!!

    @JvmStatic
    fun getString(@KeyRes keyRes: Int, defValue: String): String = sPref.getString(key(keyRes), defValue)!!

    @JvmStatic
    fun getStringOrNull(@KeyRes keyRes: Int): String? = sPref.getString(key(keyRes), null)

    @JvmStatic
    fun getString(key: String, defValue: String): String = sPref.getString(key, defValue)!!

    @JvmStatic
    fun getStringOrNull(key: String): String? = sPref.getString(key, null)

    @JvmStatic
    fun putBoolean(@KeyRes keyRes: Int, value: Boolean) = putBoolean(key(keyRes), value)

    @JvmStatic
    fun putBooleanSync(@KeyRes keyRes: Int, value: Boolean) = putBooleanSync(key(keyRes), value)

    @JvmStatic
    fun putBoolean(key: String, value: Boolean) = sPref.edit { putBoolean(key, value) }

    @JvmStatic
    fun putBooleanSync(key: String, value: Boolean) = sPref.edit(commit = true) { putBoolean(key, value) }

    @JvmStatic
    fun getBoolean(@KeyRes keyRes: Int, defValue: Boolean) = getBoolean(key(keyRes), defValue)

    @JvmStatic
    fun getBoolean(@KeyRes keyRes: Int, @KeyRes defValue: Int) = getBoolean(key(keyRes), resources.getBoolean(defValue))

    @JvmStatic
    fun getBoolean(key: String, defValue: Boolean) = sPref.getBoolean(key, defValue)

    @JvmStatic
    fun putInt(@KeyRes keyRes: Int, value: Int) = putInt(key(keyRes), value)

    @JvmStatic
    fun putInt(key: String, value: Int) = sPref.edit { putInt(key, value) }

    @JvmStatic
    fun putIntSync(key: String, value: Int) = sPref.edit(commit = true) { putInt(key, value) }

    @JvmStatic
    fun putFloat(key: String, value: Float) = sPref.edit { putFloat(key, value) }

    @JvmStatic
    fun putFloatSync(key: String, value: Float) = sPref.edit(commit = true) { putFloat(key, value) }

    @JvmStatic
    fun getInt(@KeyRes keyRes: Int, defValue: Int): Int = getInt(key(keyRes), defValue)

    @JvmStatic
    fun getInt(key: String, defValue: Int): Int = sPref.getInt(key, defValue)

    @JvmStatic
    fun getFloat(key: String, defValue: Float): Float = sPref.getFloat(key, defValue)

    @JvmStatic
    fun putLong(@KeyRes keyRes: Int, value: Long) = sPref.edit { putLong(key(keyRes), value) }

    @JvmStatic
    fun putLong(key: String, value: Long) = sPref.edit { putLong(key, value) }

    @JvmStatic
    fun getLong(@KeyRes keyRes: Int, defValue: Long) = sPref.getLong(key(keyRes), defValue)

    @JvmStatic
    fun getLong(key: String, defValue: Long) = sPref.getLong(key, defValue)

    @JvmStatic
    fun putStringSet(@KeyRes keyRes: Int, values: MutableSet<String>) = sPref.edit { putStringSet(key(keyRes), values) }

    @JvmStatic
    fun getStringSet(@KeyRes keyRes: Int, defValues: MutableSet<String>?): MutableSet<String>? = sPref.getStringSet(key(keyRes), defValues)

    @JvmStatic
    fun containsKey(vararg keyResList: Int): Boolean = keyResList.all { containsKey(key(it)) }

    @JvmStatic
    fun containsKey(@KeyRes keyRes: Int): Boolean = containsKey(key(keyRes))

    @JvmStatic
    fun containsKey(key: String): Boolean = sPref.all.containsKey(key)

    @JvmStatic
    fun remove(key: String) = sPref.edit { remove(key) }

    @JvmStatic
    fun getLinkedHashSet(@KeyRes keyRes: Int): LinkedHashSet<String> = when {
        !containsKey(keyRes) -> linkedSetOf()
        else -> Gson().fromJson(getStringOrNull(keyRes), linkedSetOf<String>().javaClass)
    }

    @JvmStatic
    fun putLinkedHashSet(@KeyRes keyRes: Int, value: LinkedHashSet<String>) {
        putString(keyRes, Gson().toJson(value))
    }

    @JvmStatic
    fun getLinkedList(key: String): LinkedList<String> = when {
        !containsKey(key) -> LinkedList()
        else -> Gson().fromJson(getStringOrNull(key), LinkedList<String>().javaClass)
    }

    @JvmStatic
    fun putLinkedList(key: String, value: LinkedList<String>) {
        putString(key, Gson().toJson(value))
    }

}
