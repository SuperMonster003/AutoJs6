package org.autojs.autojs.core.pref

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.annotation.ScriptInterfaceCompatible
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
import org.autojs.autojs.core.pref.store.StoreId
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.AutoNightMode.Companion.isFunctional
import org.autojs.autojs.util.ViewUtils.isNightModeYes
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.LinkedList

/**
 * Created by Stardust on Jan 31, 2017.
 * Modified by SuperMonster003 as of Jan 17, 2026.
 */
object Pref {

    private val globalAppContext: Context = GlobalAppContext.get()
    private val resources: Resources = globalAppContext.resources

    // Reads and writes go through the default settings store (docs/settings/ROADMAP.md P1.4, T3); the SharedPreferences
    // behind it is still the one of PreferenceManager, so get() and the registered change listeners stay the same.
    // zh-CN: 读写经默认设置存储 (ROADMAP P1.4, T3); 其背后仍是 PreferenceManager 的 SharedPreferences, get() 与已注册的变化监听不变.
    private val sStore: SettingsStore by lazy { SettingsStores.default }

    private val sPref by lazy { (sStore as? SharedPreferencesStore)?.preferences ?: SharedPreferencesStore.open(globalAppContext, StoreId.DEFAULT).preferences }

    private val onSharedPreferenceChangeListener = OnSharedPreferenceChangeListener { _, _ /* key */ ->
        // if (key == key(R.string.key_guard_mode)) {
        //     AccessibilityConfig.refreshUnintendedGuardState()
        // }
    }

    init {
        // AccessibilityConfig.refreshUnintendedGuardState()
        registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        if (!AppearanceKeys.AUTO_NIGHT_MODE_ENABLED.isStored()) {
            ViewUtils.isAutoNightModeEnabled = isFunctional().also {
                AppCompatDelegate.setDefaultNightMode(
                    when (it) {
                        true -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                    }
                )
            }
        }
        if (!AppearanceKeys.NIGHT_MODE_ENABLED.isStored()) {
            ViewUtils.isNightModeEnabled = isNightModeYes(app)
        }
    }

    @JvmStatic
    fun get(): SharedPreferences = sPref

    @JvmStatic
    val lastUpdatesCheckedTimeString: String?
        get() {
            val ts = MiscStateKeys.LAST_UPDATES_CHECKED.get()
            if (ts < 0) {
                return null
            }
            val dt = DateTime(ts)
            val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")
            return fmt.print(dt)
        }

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

    private fun write(commit: Boolean = false, block: SettingsStore.Editor.() -> Unit) {
        sStore.edit(commit, block)
    }

    @JvmStatic
    fun putString(@KeyRes keyRes: Int, value: String?) = putString(key(keyRes), value)

    @JvmStatic
    fun putString(key: String, value: String?) = write { putString(key, value) }

    @JvmStatic
    fun putStringSync(key: String, value: String?) = write(commit = true) { putString(key, value) }

    @JvmStatic
    fun getString(@KeyRes keyRes: Int, @KeyRes defKeyRes: Int): String = sStore.getString(key(keyRes), key(defKeyRes))!!

    @JvmStatic
    fun getString(@KeyRes keyRes: Int, defValue: String): String = sStore.getString(key(keyRes), defValue)!!

    @JvmStatic
    fun getStringOrNull(@KeyRes keyRes: Int): String? = sStore.getString(key(keyRes), null)

    @JvmStatic
    fun getString(key: String, defValue: String): String = sStore.getString(key, defValue)!!

    @JvmStatic
    fun getStringOrNull(key: String): String? = sStore.getString(key, null)

    @JvmStatic
    fun putBoolean(@KeyRes keyRes: Int, value: Boolean) = putBoolean(key(keyRes), value)

    @JvmStatic
    fun putBooleanSync(@KeyRes keyRes: Int, value: Boolean) = putBooleanSync(key(keyRes), value)

    @JvmStatic
    fun putBoolean(key: String, value: Boolean) = write { putBoolean(key, value) }

    @JvmStatic
    fun putBooleanSync(key: String, value: Boolean) = write(commit = true) { putBoolean(key, value) }

    @JvmStatic
    fun getBoolean(@KeyRes keyRes: Int, defValue: Boolean) = getBoolean(key(keyRes), defValue)

    @JvmStatic
    fun getBoolean(@KeyRes keyRes: Int, @KeyRes defValue: Int) = getBoolean(key(keyRes), resources.getBoolean(defValue))

    @JvmStatic
    fun getBoolean(key: String, defValue: Boolean) = sStore.getBoolean(key, defValue)

    @JvmStatic
    fun putInt(@KeyRes keyRes: Int, value: Int) = putInt(key(keyRes), value)

    @JvmStatic
    fun putInt(key: String, value: Int) = write { putInt(key, value) }

    @JvmStatic
    fun putIntSync(key: String, value: Int) = write(commit = true) { putInt(key, value) }

    @JvmStatic
    fun putFloat(key: String, value: Float) = write { putFloat(key, value) }

    @JvmStatic
    fun putFloatSync(key: String, value: Float) = write(commit = true) { putFloat(key, value) }

    @JvmStatic
    fun getInt(@KeyRes keyRes: Int, defValue: Int): Int = getInt(key(keyRes), defValue)

    @JvmStatic
    fun getInt(key: String, defValue: Int): Int = sStore.getInt(key, defValue)

    @JvmStatic
    fun getFloat(key: String, defValue: Float): Float = sStore.getFloat(key, defValue)

    @JvmStatic
    fun putLong(@KeyRes keyRes: Int, value: Long) = write { putLong(key(keyRes), value) }

    @JvmStatic
    fun putLong(key: String, value: Long) = write { putLong(key, value) }

    @JvmStatic
    fun getLong(@KeyRes keyRes: Int, defValue: Long) = sStore.getLong(key(keyRes), defValue)

    @JvmStatic
    fun getLong(key: String, defValue: Long) = sStore.getLong(key, defValue)

    @JvmStatic
    fun putStringSet(@KeyRes keyRes: Int, values: MutableSet<String>) = write { putStringSet(key(keyRes), values) }

    @JvmStatic
    fun getStringSet(@KeyRes keyRes: Int, defValues: MutableSet<String>?): MutableSet<String>? = sStore.getStringSet(key(keyRes), defValues) as MutableSet<String>?

    @JvmStatic
    fun containsKey(vararg keyResList: Int): Boolean = keyResList.all { containsKey(key(it)) }

    @JvmStatic
    fun containsKey(@KeyRes keyRes: Int): Boolean = containsKey(key(keyRes))

    @JvmStatic
    fun containsKey(key: String): Boolean = sStore.contains(key)

    @JvmStatic
    fun remove(key: String) = write { remove(key) }

    // String lists are stored as JSON arrays; SettingsJson writes the same text that Gson used to write (P1.4, R1).
    // zh-CN: 字符串列表以 JSON 数组存储; SettingsJson 写出的文本与以前 Gson 写的相同 (P1.4, R1).
    @JvmStatic
    fun getLinkedHashSet(@KeyRes keyRes: Int): LinkedHashSet<String> = when {
        !containsKey(keyRes) -> linkedSetOf()
        else -> LinkedHashSet(SettingsJson.decodeStringList(getStringOrNull(keyRes)!!))
    }

    @JvmStatic
    fun putLinkedHashSet(@KeyRes keyRes: Int, value: LinkedHashSet<String>) {
        putString(keyRes, SettingsJson.encodeStringList(value))
    }

    @JvmStatic
    fun getLinkedList(key: String): LinkedList<String> = when {
        !containsKey(key) -> LinkedList()
        else -> LinkedList(SettingsJson.decodeStringList(getStringOrNull(key)!!))
    }

    @JvmStatic
    fun putLinkedList(key: String, value: LinkedList<String>) {
        putString(key, SettingsJson.encodeStringList(value))
    }

}
