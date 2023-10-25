package org.autojs.autojs.pref

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.event.GlobalKeyObserver
import org.autojs.autojs.runtime.accessibility.AccessibilityConfig
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.AutoNightMode.Companion.isFunctional
import org.autojs.autojs.util.ViewUtils.isNightModeYes
import org.autojs.autojs6.R
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.LinkedList

/**
 * Created by Stardust on 2017/1/31.
 */
object Pref {

    private val globalAppContext: Context = GlobalAppContext.get()
    private val resources: Resources = globalAppContext.resources

    private val sPref by lazy { PreferenceManager.getDefaultSharedPreferences(globalAppContext) }

    private val onSharedPreferenceChangeListener = OnSharedPreferenceChangeListener { _, key ->
        if (key == key(R.string.key_guard_mode)) {
            AccessibilityConfig.refreshUnintendedGuardState()
        }
    }

    init {
        AccessibilityConfig.refreshUnintendedGuardState()
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
    val isCompatibilityWithClassesForVer4xEnabled
        get() = getBoolean(
            R.string.key_compatibility_with_classes_for_ver_4_x,
            resources.getBoolean(R.bool.pref_compatibility_with_classes_for_ver_4_x),
        )

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
    val isAutoCheckForUpdatesEnabled: Boolean
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
            val fmt = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm")
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
    val keyKeepScreenOnWhenInForeground: String?
        get() = getString(R.string.key_keep_screen_on_when_in_foreground, key(R.string.default_key_keep_screen_on_when_in_foreground))

    @JvmStatic
    var rootMode: RootUtils.RootMode
        get() {
            val key = getString(R.string.key_root_mode, RootUtils.RootMode.AUTO_DETECT.key)
            return RootUtils.RootMode.getRootMode(key!!)
        }
        set(mode) {
            putString(key(R.string.key_root_mode), mode.key)
        }

    val isStableModeEnabled
        get() = getBoolean(
            R.string.key_stable_mode,
            resources.getBoolean(R.bool.pref_stable_mode)
        )

    // @Hint by SuperMonster003 on Oct 20, 2022.
    //  ! Unused so far.
    val isGestureObservingEnabled
        get() = getBoolean(
            R.string.key_gesture_observing,
            resources.getBoolean(R.bool.pref_gesture_observing)
        )

    @JvmStatic
    val isHiddenFilesShow
        get() = getString(R.string.key_hidden_files, key(R.string.default_key_hidden_files)) == key(R.string.key_hidden_files_show)

    private fun getTimestamp(@KeyRes keyRes: Int): Long = getLong(keyRes, -1)

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
    fun clearUpdatesCheckedStates() = sPref.edit()
        .remove(key(R.string.key_last_no_newer_updates))
        .remove(key(R.string.key_last_updates_postponed))
        .remove(key(R.string.key_last_updates_checked))
        .remove(key(R.string.key_last_updates_auto_checked))
        .apply()

    @JvmStatic
    fun shouldEnableA11yServiceWithRoot() = getBoolean(
        R.string.key_enable_a11y_service_with_root_access,
        resources.getBoolean(R.bool.pref_enable_a11y_service_with_root_access),
    )

    @JvmStatic
    fun shouldEnableA11yServiceWithSecureSettings() = getBoolean(
        R.string.key_enable_a11y_service_with_secure_settings,
        resources.getBoolean(R.bool.pref_enable_a11y_service_with_secure_settings)
    )

    private fun updateTimestamp(@KeyRes keyRes: Int) = putLong(keyRes, System.currentTimeMillis())

    @JvmStatic
    fun getServerAddress(): String = getString(R.string.key_server_address, NetworkUtils.getGatewayAddress())!!

    @JvmStatic
    fun setServerAddress(address: String?) = putString(R.string.key_server_address, address)

    @JvmStatic
    val isRecordToastEnabled
        get() = getBoolean(
            R.string.key_record_toast,
            resources.getBoolean(R.bool.pref_record_toast)
        )

    @JvmStatic
    fun rootRecordGeneratesBinary() = getString(R.string.key_root_record_out_file_type, key(R.string.default_key_root_record_out_file_type)) == key(R.string.key_root_record_out_file_type_binary)

    @JvmStatic
    var currentTheme: String?
        get() = getString(R.string.key_editor_theme, null)
        set(theme) {
            putString(R.string.key_editor_theme, theme)
        }

    @JvmStatic
    fun setEditorTextSize(value: Int) = putInt(R.string.key_editor_text_size, value)

    @JvmStatic
    fun getEditorTextSize(defValue: Int): Int = getInt(R.string.key_editor_text_size, defValue)

    @JvmStatic
    fun putString(@KeyRes keyRes: Int, value: String?) = putString(key(keyRes), value)

    @JvmStatic
    fun putString(key: String?, value: String?) = sPref.edit().putString(key, value).apply()

    @JvmStatic
    fun getString(@KeyRes keyRes: Int, defValue: String?): String? = getString(key(keyRes), defValue)

    @JvmStatic
    fun getString(key: String?, defValue: String?): String? = sPref.getString(key, defValue)

    @JvmStatic
    fun putBoolean(@KeyRes keyRes: Int, value: Boolean) = putBoolean(key(keyRes), value)

    @JvmStatic
    fun putBooleanSync(@KeyRes keyRes: Int, value: Boolean) = putBooleanSync(key(keyRes), value)

    @JvmStatic
    fun putBoolean(key: String?, value: Boolean) = sPref.edit().putBoolean(key, value).apply()

    @JvmStatic
    fun putBooleanSync(key: String?, value: Boolean) = sPref.edit().putBoolean(key, value).commit()

    @JvmStatic
    fun getBoolean(@KeyRes keyRes: Int, defValue: Boolean) = getBoolean(key(keyRes), defValue)

    @JvmStatic
    fun getBoolean(key: String?, defValue: Boolean) = sPref.getBoolean(key, defValue)

    @JvmStatic
    fun putInt(@KeyRes keyRes: Int, value: Int) = putInt(key(keyRes), value)

    @JvmStatic
    fun putInt(key: String?, value: Int) = sPref.edit().putInt(key, value).apply()

    @JvmStatic
    fun putIntSync(key: String?, value: Int) = sPref.edit().putInt(key, value).commit()

    @JvmStatic
    fun putFloat(key: String?, value: Float) = sPref.edit().putFloat(key, value).apply()

    @JvmStatic
    fun putFloatSync(key: String?, value: Float) = sPref.edit().putFloat(key, value).commit()

    @JvmStatic
    fun getInt(@KeyRes keyRes: Int, defValue: Int): Int = getInt(key(keyRes), defValue)

    @JvmStatic
    fun getInt(key: String?, defValue: Int): Int = sPref.getInt(key, defValue)

    @JvmStatic
    fun getFloat(key: String?, defValue: Float): Float = sPref.getFloat(key, defValue)

    @JvmStatic
    fun putLong(@KeyRes keyRes: Int, value: Long) = sPref.edit().putLong(key(keyRes), value).apply()

    @JvmStatic
    fun getLong(@KeyRes keyRes: Int, defValue: Long) = sPref.getLong(key(keyRes), defValue)

    @JvmStatic
    fun putStringSet(@KeyRes keyRes: Int, values: MutableSet<String>) = sPref.edit().putStringSet(key(keyRes), values).apply()

    @JvmStatic
    fun getStringSet(@KeyRes keyRes: Int, defValues: MutableSet<String>?): MutableSet<String>? = sPref.getStringSet(key(keyRes), defValues)

    @JvmStatic
    fun containsKey(vararg keyResList: Int): Boolean = keyResList.all { containsKey(key(it)) }

    @JvmStatic
    fun containsKey(@KeyRes keyRes: Int): Boolean = containsKey(key(keyRes))

    @JvmStatic
    fun containsKey(key: String?): Boolean = sPref.all.containsKey(key)

    @JvmStatic
    fun remove(key: String?) = sPref.edit().remove(key).apply()

    @JvmStatic
    fun getLinkedHashSet(@KeyRes keyRes: Int): LinkedHashSet<String> = when {
        !containsKey(keyRes) -> linkedSetOf()
        else -> Gson().fromJson(getString(keyRes, null), linkedSetOf<String>().javaClass)
    }

    @JvmStatic
    fun putLinkedHashSet(@KeyRes keyRes: Int, value: LinkedHashSet<String>) {
        putString(keyRes, Gson().toJson(value))
    }

    @JvmStatic
    fun getLinkedList(key: String): LinkedList<String> = when {
        !containsKey(key) -> LinkedList()
        else -> Gson().fromJson(getString(key, null), LinkedList<String>().javaClass)
    }

    @JvmStatic
    fun putLinkedList(key: String, value: LinkedList<String>) {
        putString(key, Gson().toJson(value))
    }

}