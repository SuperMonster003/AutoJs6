package org.autojs.autojs.core.pref

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.App.Companion.app
import org.autojs.autojs.annotation.KeyRes
import org.autojs.autojs.annotation.ScriptInterfaceCompatible
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.A11yKeys
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys
import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.keys.RecordKeys
import org.autojs.autojs.core.pref.keys.RunningKeys
import org.autojs.autojs.core.pref.keys.UpdatesKeys
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
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

    private val sPref by lazy { (sStore as? SharedPreferencesStore)?.preferences ?: PreferenceManager.getDefaultSharedPreferences(globalAppContext) }

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
    @Deprecated("Use RunningKeys.screenCaptureRequestDelay (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RunningKeys.screenCaptureRequestDelay", "org.autojs.autojs.core.pref.keys.RunningKeys"))
    val screenCaptureRequestDelay: Int
        get() = RunningKeys.screenCaptureRequestDelay

    @JvmStatic
    @Deprecated("Use EditorKeys.codeHighlightThresholdChars (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.codeHighlightThresholdChars", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    val codeHighlightThresholdChars: Int
        get() = EditorKeys.codeHighlightThresholdChars

    @JvmStatic
    @Deprecated("Use EditorKeys.largeFileModeThresholdBytes (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.largeFileModeThresholdBytes", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    val largeFileModeThresholdBytes: Int
        get() = EditorKeys.largeFileModeThresholdBytes

    @JvmStatic
    @Deprecated("Use EditorKeys.isCodeHighlightEnabledForLength (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.isCodeHighlightEnabledForLength(length)", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun isCodeHighlightEnabledForLength(length: Int): Boolean = EditorKeys.isCodeHighlightEnabledForLength(length)

    @JvmStatic
    @Deprecated("Use EditorKeys.shouldEnableLargeFileModeForSize (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.shouldEnableLargeFileModeForSize(size)", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun shouldEnableLargeFileModeForSize(size: Long): Boolean = EditorKeys.shouldEnableLargeFileModeForSize(size)

    @JvmStatic
    @Deprecated("Use ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS (docs/settings/ROADMAP.md P2.4)", ReplaceWith("ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS.get()", "org.autojs.autojs.core.pref.keys.ExtensibilityKeys"))
    val isExtendingJsBuildInObjectsEnabled
        get() = ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS.get()

    /**
     * Switch of the Augment refactor (docs/augment/ROADMAP.md T8), off by default, in the developer options (P1.3): scripts
     * started while it is on mount the migrated modules from the new script module layer.
     * zh-CN: Augment 重构的开关 (ROADMAP T8), 默认关, 位于开发者选项 (P1.3): 打开后新启动的脚本从新脚本模块层挂载已迁移的模块.
     */
    @JvmStatic
    @Deprecated("Use DeveloperKeys.NEW_SCRIPT_MODULE_LAYER (docs/settings/ROADMAP.md P2.5)", ReplaceWith("DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get()", "org.autojs.autojs.core.pref.keys.DeveloperKeys"))
    val isNewScriptModuleLayerEnabled
        get() = DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get()

    /**
     * Debug printing of the new script module layer (ROADMAP T5, appendix C Q4): the master switch, off by default, and the
     * top-level modules excluded from printing (empty by default, so every module prints once the master switch is on).
     * Set from the developer options (P1.3).
     * zh-CN: 新脚本模块层的调试打印 (ROADMAP T5, 附录 C Q4): 总开关默认关; 不打印的顶层模块列表默认为空 (总开关打开后全部模块都打印). 在开发者选项中设置 (P1.3).
     */
    @JvmStatic
    @Deprecated("Use DeveloperKeys.SCRIPT_MODULE_DEBUG_PRINT (docs/settings/ROADMAP.md P2.5)", ReplaceWith("DeveloperKeys.SCRIPT_MODULE_DEBUG_PRINT.get()", "org.autojs.autojs.core.pref.keys.DeveloperKeys"))
    val isScriptModuleDebugPrintEnabled
        get() = DeveloperKeys.SCRIPT_MODULE_DEBUG_PRINT.get()

    @JvmStatic
    @Deprecated("Use DeveloperKeys.scriptModuleDebugPrintDisabledModules (docs/settings/ROADMAP.md P2.5)", ReplaceWith("DeveloperKeys.scriptModuleDebugPrintDisabledModules", "org.autojs.autojs.core.pref.keys.DeveloperKeys"))
    var scriptModuleDebugPrintDisabledModules: Set<String>
        get() = DeveloperKeys.scriptModuleDebugPrintDisabledModules
        set(value) {
            DeveloperKeys.scriptModuleDebugPrintDisabledModules = value
        }

    @JvmStatic
    @Deprecated("Use RunningKeys.GUARD_MODE (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RunningKeys.GUARD_MODE.get()", "org.autojs.autojs.core.pref.keys.RunningKeys"))
    val isGuardModeEnabled
        get() = RunningKeys.GUARD_MODE.get()

    @JvmStatic
    @Deprecated("Use RunningKeys.USE_VOLUME_CONTROL_RUNNING (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RunningKeys.USE_VOLUME_CONTROL_RUNNING.get()", "org.autojs.autojs.core.pref.keys.RunningKeys"))
    val isUseVolumeControlRunningEnabled
        get() = RunningKeys.USE_VOLUME_CONTROL_RUNNING.get()

    @JvmStatic
    @Deprecated("Use RecordKeys.USE_VOLUME_CONTROL_RECORD (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RecordKeys.USE_VOLUME_CONTROL_RECORD.get()", "org.autojs.autojs.core.pref.keys.RecordKeys"))
    val isUseVolumeControlRecordEnabled
        get() = RecordKeys.USE_VOLUME_CONTROL_RECORD.get()

    @JvmStatic
    @Deprecated("Use UpdatesKeys.AUTO_CHECK_FOR_UPDATES (docs/settings/ROADMAP.md P2.4)", ReplaceWith("UpdatesKeys.AUTO_CHECK_FOR_UPDATES.get()", "org.autojs.autojs.core.pref.keys.UpdatesKeys"))
    val isAutoCheckForUpdatesEnabled
        get() = UpdatesKeys.AUTO_CHECK_FOR_UPDATES.get()

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
    @Deprecated("Use MiscStateKeys.LAST_NO_NEWER_UPDATES (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.LAST_NO_NEWER_UPDATES.get()", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    val lastNoNewerUpdatesTimestamp: Long
        get() = MiscStateKeys.LAST_NO_NEWER_UPDATES.get()

    @JvmStatic
    @Deprecated("Use MiscStateKeys.LAST_UPDATES_POSTPONED (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.LAST_UPDATES_POSTPONED.get()", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    val lastUpdatesPostponedTimestamp: Long
        get() = MiscStateKeys.LAST_UPDATES_POSTPONED.get()

    @JvmStatic
    @Deprecated("Use MiscStateKeys.LAST_UPDATES_AUTO_CHECKED (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.LAST_UPDATES_AUTO_CHECKED.get()", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    val lastUpdatesAutoCheckedTimestamp: Long
        get() = MiscStateKeys.LAST_UPDATES_AUTO_CHECKED.get()

    @JvmStatic
    @Deprecated("Use AppearanceKeys.keepScreenOnWhenInForegroundKey (docs/settings/ROADMAP.md P2.1)", ReplaceWith("AppearanceKeys.keepScreenOnWhenInForegroundKey", "org.autojs.autojs.core.pref.keys.AppearanceKeys"))
    val keyKeepScreenOnWhenInForeground: String
        get() = AppearanceKeys.keepScreenOnWhenInForegroundKey

    @JvmStatic
    @Deprecated("Use RunningKeys.rootMode (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RunningKeys.rootMode", "org.autojs.autojs.core.pref.keys.RunningKeys"))
    var rootMode: RootUtils.RootMode
        get() = RunningKeys.rootMode
        set(mode) {
            RunningKeys.rootMode = mode
        }

    @Deprecated("Use A11yKeys.STABLE_MODE (docs/settings/ROADMAP.md P2.4)", ReplaceWith("A11yKeys.STABLE_MODE.get()", "org.autojs.autojs.core.pref.keys.A11yKeys"))
    val isStableModeEnabled
        get() = A11yKeys.STABLE_MODE.get()

    @Deprecated("Use A11yKeys.GESTURE_OBSERVING (docs/settings/ROADMAP.md P2.4)", ReplaceWith("A11yKeys.GESTURE_OBSERVING.get()", "org.autojs.autojs.core.pref.keys.A11yKeys"))
    val isGestureObservingEnabled
        get() = A11yKeys.GESTURE_OBSERVING.get()

    @JvmStatic
    @Deprecated("Use GeneralKeys.isQuickRestartEnabled (docs/settings/ROADMAP.md P2.1)", ReplaceWith("GeneralKeys.isQuickRestartEnabled", "org.autojs.autojs.core.pref.keys.GeneralKeys"))
    val isQuickRestartEnabled
        get() = GeneralKeys.isQuickRestartEnabled

    @JvmStatic
    @Deprecated("Use GeneralKeys.isScheduledRestartPreferredWorkManager (docs/settings/ROADMAP.md P2.1)", ReplaceWith("GeneralKeys.isScheduledRestartPreferredWorkManager", "org.autojs.autojs.core.pref.keys.GeneralKeys"))
    val isScheduledRestartPreferredWorkManager
        get() = GeneralKeys.isScheduledRestartPreferredWorkManager

    @JvmStatic
    @Deprecated("Use ExplorerKeys.isHiddenFilesShown (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys.isHiddenFilesShown", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
    val isHiddenFilesShown
        get() = ExplorerKeys.isHiddenFilesShown

    @JvmStatic
    @Deprecated("Use ExplorerKeys.EXPLORER_PLAYBACK_HISTORY (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get()", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
    val isExplorerPlaybackHistoryEnabled
        get() = ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get()

    @JvmStatic
    @Deprecated("Use ExplorerKeys.isFileExtensionsShownForAll (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys.isFileExtensionsShownForAll", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
    val isFileExtensionsShownForAll
        get() = ExplorerKeys.isFileExtensionsShownForAll

    @JvmStatic
    @Deprecated("Use ExplorerKeys.isFileExtensionsShownForAllButExecutable (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys.isFileExtensionsShownForAllButExecutable", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
    val isFileExtensionsShownForAllButExecutable
        get() = ExplorerKeys.isFileExtensionsShownForAllButExecutable

    @JvmStatic
    @Deprecated("Use ExplorerKeys.isFileExtensionsHidden (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys.isFileExtensionsHidden", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
    val isFileExtensionsHidden
        get() = ExplorerKeys.isFileExtensionsHidden

    @JvmStatic
    @Deprecated("Use RecordKeys.RECORD_TOAST (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RecordKeys.RECORD_TOAST.get()", "org.autojs.autojs.core.pref.keys.RecordKeys"))
    val isRecordToastEnabled
        get() = RecordKeys.RECORD_TOAST.get()

    @JvmStatic
    @Deprecated("Use EditorKeys.EDITOR_THEME (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.EDITOR_THEME", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    var currentTheme: String?
        get() = EditorKeys.EDITOR_THEME.storedOrNull()
        set(theme) {
            EditorKeys.EDITOR_THEME.set(theme)
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

    @JvmStatic
    @Deprecated("Use MiscStateKeys.markNow (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_CHECKED)", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    fun refreshLastUpdatesCheckedTimestamp() = MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_CHECKED)

    @JvmStatic
    @Deprecated("Use MiscStateKeys.markNow (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.markNow(MiscStateKeys.LAST_NO_NEWER_UPDATES)", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    fun refreshLastNoNewerUpdatesTimestamp() = MiscStateKeys.markNow(MiscStateKeys.LAST_NO_NEWER_UPDATES)

    @JvmStatic
    @Deprecated("Use MiscStateKeys.markNow (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_POSTPONED)", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    fun refreshLastUpdatesPostponedTimestamp() = MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_POSTPONED)

    @JvmStatic
    @Deprecated("Use MiscStateKeys.markNow (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_AUTO_CHECKED)", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    fun refreshLastUpdatesAutoCheckedTimestamp() = MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_AUTO_CHECKED)

    @JvmStatic
    @Deprecated("Use MiscStateKeys.clearUpdatesCheckedStates (docs/settings/ROADMAP.md P4.2)", ReplaceWith("MiscStateKeys.clearUpdatesCheckedStates()", "org.autojs.autojs.core.pref.keys.MiscStateKeys"))
    fun clearUpdatesCheckedStates() = MiscStateKeys.clearUpdatesCheckedStates()

    @JvmStatic
    @Deprecated("Use A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS (docs/settings/ROADMAP.md P2.4)", ReplaceWith("A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get()", "org.autojs.autojs.core.pref.keys.A11yKeys"))
    fun shouldStartA11yServiceWithRoot() = A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get()

    @JvmStatic
    @Deprecated("Use A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS (docs/settings/ROADMAP.md P2.4)", ReplaceWith("A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get()", "org.autojs.autojs.core.pref.keys.A11yKeys"))
    fun shouldStartA11yServiceWithSecureSettings() = A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get()

    @JvmStatic
    @Deprecated("Use A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU (docs/settings/ROADMAP.md P2.4)", ReplaceWith("A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get()", "org.autojs.autojs.core.pref.keys.A11yKeys"))
    fun shouldStartA11yServiceWithShizuku() = A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get()

    private fun write(commit: Boolean = false, block: SettingsStore.Editor.() -> Unit) {
        sStore.edit(commit, block)
    }

    @JvmStatic
    @Deprecated("Use ConnectionKeys.SERVER_ADDRESS (docs/settings/ROADMAP.md P2.5)", ReplaceWith("ConnectionKeys.SERVER_ADDRESS.get()", "org.autojs.autojs.core.pref.keys.ConnectionKeys"))
    fun getServerAddress(): String = ConnectionKeys.SERVER_ADDRESS.get()

    @JvmStatic
    @Deprecated("Use ConnectionKeys.SERVER_ADDRESS (docs/settings/ROADMAP.md P2.5)", ReplaceWith("ConnectionKeys.SERVER_ADDRESS.set(address)", "org.autojs.autojs.core.pref.keys.ConnectionKeys"))
    fun setServerAddress(address: String?) = ConnectionKeys.SERVER_ADDRESS.set(address)

    @JvmStatic
    @Deprecated("Use RecordKeys.rootRecordGeneratesBinary (docs/settings/ROADMAP.md P2.4)", ReplaceWith("RecordKeys.rootRecordGeneratesBinary()", "org.autojs.autojs.core.pref.keys.RecordKeys"))
    fun rootRecordGeneratesBinary() = RecordKeys.rootRecordGeneratesBinary()

    @JvmStatic
    @Deprecated("Use EditorKeys.EDITOR_TEXT_SIZE (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.EDITOR_TEXT_SIZE.set(value)", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun setEditorTextSize(value: Int) = EditorKeys.EDITOR_TEXT_SIZE.set(value)

    @JvmStatic
    @Deprecated("Use EditorKeys.EDITOR_TEXT_SIZE (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.EDITOR_TEXT_SIZE.storedOrNull() ?: defValue", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun getEditorTextSize(defValue: Int): Int = EditorKeys.EDITOR_TEXT_SIZE.storedOrNull() ?: defValue

    @JvmStatic
    @Deprecated("Use EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(value)", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun setCodeHighlightThresholdChars(value: Int) = EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(value)

    @JvmStatic
    @Deprecated("Use EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES (docs/settings/ROADMAP.md P2.3)", ReplaceWith("EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(value)", "org.autojs.autojs.core.pref.keys.EditorKeys"))
    fun setLargeFileModeThresholdBytes(value: Int) = EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(value)

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
