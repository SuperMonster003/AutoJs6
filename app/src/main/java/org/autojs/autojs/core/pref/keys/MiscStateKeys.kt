package org.autojs.autojs.core.pref.keys

import android.content.res.Configuration
import org.autojs.autojs.core.pref.CacheKey
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKeyNames
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs6.R

/**
 * Remembered states and caches of the default preferences (docs/settings/ROADMAP.md P2.6 / P4.2, T2): what the app
 * remembers about itself rather than what the user chose, always in the `other` category (not selected by default,
 * never reset to defaults). The names are those of the former readers (R1; some are class names with a suffix, checked
 * by MiscStateKeysTest). The "do not show again" states of `NotAskAgainDialog` are in [DialogStateKeys] (P4.1).
 *
 * Readers that had a fallback of their own keep it (`storedOrNull() ?: fallback`), and the declared default is the
 * value they used when they all agreed; texts that readers parse themselves are `Json` keys read and written as stored
 * text (`storedText` / `setStoredText`), so the text stays as it was (R1 / R2, as in P3).
 *
 * zh-CN: 默认偏好中的记忆型状态与缓存 (ROADMAP P2.6 / P4.2, T2): 应用对自身的记忆, 而非用户的选择, 固定归 `other` 分类 (默认不勾选,
 * 不参与恢复默认). 名称沿用原读取方 (R1; 其中一些是类名加后缀, 由 MiscStateKeysTest 核对). `NotAskAgainDialog` 的 "不再提示"
 * 状态在 [DialogStateKeys] (P4.1). 自带回退值的读取方保留回退值 (`storedOrNull() ?: 回退值`), 各读取方一致时声明的默认值即该值;
 * 由读取方自行解析的文本声明为 `Json` 键并按存储的文本读写, 文本保持原样 (R1 / R2, 同 P3).
 */
object MiscStateKeys : SettingsDomain {

    // ---------------------------------------------------------------------- dialogs and the code editor (P2.6)

    /** The search text of the editor's find / replace dialog (the name is three dots, as `FindOrReplaceDialogBuilder` wrote it). */
    @JvmField
    val FIND_OR_REPLACE_KEYWORDS = StateKey.str(name = "...", default = SettingDefault.of(""))

    @JvmField
    val FIND_OR_REPLACE_USING_REGEX = StateKey.bool(name = "find_or_replace_using_regex", default = SettingDefault.of(false))

    /** The last code of the code evaluation dialog (`CodeEvaluateDialogBuilder`). */
    @JvmField
    val CODE_EVALUATE_CODE = StateKey.str(name = "org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder.code", default = SettingDefault.of(""))

    /** The input device id the root automator detected (`RootAutomatorEngine`); -1 until detected. */
    @JvmField
    val ROOT_AUTOMATOR_TOUCH_DEVICE = CacheKey.int(name = "org.autojs.autojs.engine.RootAutomatorEngine.touch_device", default = SettingDefault.of(-1))

    /** The legacy pinch to zoom strategy, read once to migrate it (`EditorPinchToZoomPreferences`); the old page wrote it. */
    @JvmField
    val EDITOR_PINCH_TO_ZOOM_STRATEGY = StateKey.str(R.string.key_editor_pinch_to_zoom_strategy, default = SettingDefault.None)

    /** The format revision of the stored Ace LSP file types (`AceEditorLspPreferences`); 1 when absent. */
    @JvmField
    val ACE_LSP_FILE_TYPES_REVISION = StateKey.int(name = "key_\$_ace_lsp_file_types_revision", default = SettingDefault.of(1))

    /** One-time notices of the editor engines that were shown (`EditorEnginePreferences.consumeOnce`). */
    @JvmField
    val SEEN_EDITOR_SWITCH_UNDO_NOTICE = StateKey.bool(name = "key_\$_seen_editor_switch_undo_notice", default = SettingDefault.of(false))

    @JvmField
    val SEEN_ACE_FALLBACK_NOTICE = StateKey.bool(name = "key_\$_seen_ace_fallback_notice", default = SettingDefault.of(false))

    @JvmField
    val SEEN_ACE_LARGE_FILE_NOTICE = StateKey.bool(name = "key_\$_seen_ace_large_file_notice", default = SettingDefault.of(false))

    // ---------------------------------------------------------------------- drawer switches (P4.2)

    /** Mirror of the drawer switch "accessibility service", written back from the real state (`DrawerMenuToggleableItem.sync`). */
    @JvmField
    val A11Y_SERVICE = StateKey.bool(R.string.key_a11y_service, default = SettingDefault.of(false))

    /** Mirror of the drawer switch "floating button", written when the menu closes (`CircularMenu.closeAndSaveState`). */
    @JvmField
    val FLOATING_MENU_SHOWN = StateKey.bool(R.string.key_floating_menu_shown, default = SettingDefault.of(false))

    // ---------------------------------------------------------------------- manager dialogs (P4.2)
    // Levels are the ordinals of the level enums of the dialogs (1 = BASIC / START_STOP, 2 = ALL); a collapsed state
    // hides the settings section (`ManagerSettingsSection`).
    // zh-CN: 等级为对话框中等级枚举的序号 (1 = BASIC / START_STOP, 2 = ALL); 折叠状态隐藏设置区.

    @JvmField
    val A11Y_SERVICE_MANAGER_STATUS_LEVEL = StateKey.int(R.string.key_a11y_service_manager_status_level, default = SettingDefault.of(1))

    @JvmField
    val A11Y_SERVICE_MANAGER_CONTROL_LEVEL = StateKey.int(R.string.key_a11y_service_manager_control_level, default = SettingDefault.of(1))

    @JvmField
    val A11Y_SERVICE_MANAGER_SETTINGS_COLLAPSED = StateKey.bool(R.string.key_a11y_service_manager_settings_collapsed, default = SettingDefault.of(false))

    @JvmField
    val CLIENT_MODE_MANAGER_STATUS_LEVEL = StateKey.int(R.string.key_client_mode_manager_status_level, default = SettingDefault.of(1))

    @JvmField
    val CLIENT_MODE_MANAGER_CONTROL_LEVEL = StateKey.int(R.string.key_client_mode_manager_control_level, default = SettingDefault.of(1))

    @JvmField
    val CLIENT_MODE_MANAGER_SETTINGS_COLLAPSED = StateKey.bool(R.string.key_client_mode_manager_settings_collapsed, default = SettingDefault.of(false))

    @JvmField
    val CLIENT_MODE_MANAGER_CLIENTS_LEVEL = StateKey.int(R.string.key_client_mode_manager_clients_level, default = SettingDefault.of(2))

    @JvmField
    val SERVER_MODE_MANAGER_STATUS_LEVEL = StateKey.int(R.string.key_server_mode_manager_status_level, default = SettingDefault.of(1))

    @JvmField
    val SERVER_MODE_MANAGER_CONTROL_LEVEL = StateKey.int(R.string.key_server_mode_manager_control_level, default = SettingDefault.of(1))

    @JvmField
    val SERVER_MODE_MANAGER_SETTINGS_COLLAPSED = StateKey.bool(R.string.key_server_mode_manager_settings_collapsed, default = SettingDefault.of(false))

    @JvmField
    val SERVER_MODE_MANAGER_CLIENTS_LEVEL = StateKey.int(R.string.key_server_mode_manager_clients_level, default = SettingDefault.of(2))

    // ---------------------------------------------------------------------- connections (P4.2)

    /** The last client connection ended by an explicit user action; true so that a fresh install never connects on its own. */
    @JvmField
    val CLIENT_SOCKET_NORMALLY_CLOSED = StateKey.bool(R.string.key_client_socket_normally_closed, default = SettingDefault.of(true))

    /** The server mode was stopped by the user; true so that a fresh install never starts it on its own. */
    @JvmField
    val SERVER_SOCKET_NORMALLY_CLOSED = StateKey.bool(R.string.key_server_socket_normally_closed, default = SettingDefault.of(true))

    /** The MCP Server was stopped by the user (`McpServerTool`); true so that a fresh install never starts it on its own. */
    @JvmField
    val MCP_SERVER_NORMALLY_CLOSED = StateKey.bool(R.string.key_mcp_server_normally_closed, default = SettingDefault.of(true))

    /** Server addresses entered for the client mode, in insertion order (a JSON list as Gson wrote it, `JsonSocketClient`). */
    @JvmField
    val PC_SERVER_ADDRESS_HISTORY = StateKey.json(
        R.string.key_pc_server_address_history, SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true), SettingDefault.of(emptyList()),
    )

    // ---------------------------------------------------------------------- updates (P4.2)
    // Times in milliseconds; -1 when never (`Pref.getTimestamp` read them with that default).
    // zh-CN: 毫秒时间; 从未发生时为 -1 (`Pref.getTimestamp` 以此为默认值).

    @JvmField
    val LAST_UPDATES_CHECKED = StateKey.long(R.string.key_last_updates_checked, default = SettingDefault.of(-1L))

    @JvmField
    val LAST_NO_NEWER_UPDATES = StateKey.long(R.string.key_last_no_newer_updates, default = SettingDefault.of(-1L))

    @JvmField
    val LAST_UPDATES_POSTPONED = StateKey.long(R.string.key_last_updates_postponed, default = SettingDefault.of(-1L))

    @JvmField
    val LAST_UPDATES_AUTO_CHECKED = StateKey.long(R.string.key_last_updates_auto_checked, default = SettingDefault.of(-1L))

    /** The last automatic check of the plugin indexes (`PluginOnlineSettingsStore`); 0 when never. */
    @JvmField
    val PLUGIN_CENTER_LAST_ONLINE_CHECK_TS = StateKey.long(R.string.key_plugin_center_last_online_check_ts, default = SettingDefault.of(0L))

    // ---------------------------------------------------------------------- permissions, wizards and the working directory (P4.2)

    /** The notification permission was requested once (`PostNotificationsPermission`, `PermissionWizardRunner`). */
    @JvmField
    val POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED = StateKey.bool(R.string.key_post_notifications_permission_request_attempted, default = SettingDefault.of(false))

    /** The welcome of the setup wizards was shown (`SetupWizards`); the settings page can reset it. */
    @JvmField
    val SETUP_WIZARD_WELCOME_DONE = StateKey.bool(R.string.key_setup_wizard_welcome_done, default = SettingDefault.of(false))

    /** The working directory was determined once (`WorkingDirectoryUtils.determineIfNeeded` checks its presence and writes true). */
    @JvmField
    val WORKING_DIRECTORY_INITIALIZED = StateKey.bool(R.string.key_working_directory_initialized, default = SettingDefault.of(false))

    /** Working directories chosen before, in insertion order (a JSON list as Gson wrote it, `WorkingDirectoryUtils`). */
    @JvmField
    val WORKING_DIRECTORY_HISTORY = StateKey.json(
        R.string.key_working_directory_history, SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true), SettingDefault.of(emptyList()),
    )

    // ---------------------------------------------------------------------- appearance, colors and floating windows (P4.2)

    /** The "keep screen on" choice before it was turned off, restored when it is turned on again (`ViewUtils`). */
    @JvmField
    val KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_LAST_ENABLED = StateKey.str(
        R.string.key_keep_screen_on_when_in_foreground_last_enabled,
        // As `keyResource`, written out so that the candidate name appears only as a resource reference (the inventory
        // takes a "key_" string literal for a stored key name).
        default = SettingDefault.computed("R.string.key_keep_screen_on_when_in_foreground_homepage_only") {
            SettingKeyNames.resolve(R.string.key_keep_screen_on_when_in_foreground_homepage_only)
        },
    )

    /** The layout of the color pickers, written before they open (`ColorSelectBaseActivity.isLegacyLayout`, appendix C Q13 (b)). */
    @JvmField
    val COLOR_SELECT_ACTIVITY_LEGACY_LAYOUT = StateKey.bool(R.string.key_color_select_activity_legacy_layout, default = SettingDefault.of(false))

    /** The custom color of the color picker (`ColorSelectBaseActivity`, appendix C Q15); readers fall back to a resource color. */
    @JvmField
    val COLOR_SELECT_CUSTOM_COLOR = StateKey.int(name = "org.autojs.autojs.theme.app.ColorSettingRecyclerView.COLOR_SETTING_CUSTOM_COLOR", default = SettingDefault.None)

    /** The selected position of the legacy color list (`ColorSelectBaseActivity`, Q15); readers fall back to "none" or the default position. */
    @JvmField
    val COLOR_SELECT_LEGACY_SELECTED_INDEX = StateKey.int(name = "org.autojs.autojs.theme.app.ColorSettingRecyclerView.SELECTED_COLOR_INDEX", default = SettingDefault.None)

    /** The selected color library and item (`ColorSelectBaseActivity`, Q15); -1 (`ColorLibraryIds.SELECT_NONE`) when none. */
    @JvmField
    val COLOR_SELECT_LIBRARY_ID = StateKey.int(name = "SELECTED_COLOR_LIBRARY_ID", default = SettingDefault.of(-1))

    @JvmField
    val COLOR_SELECT_ITEM_ID = StateKey.int(name = "SELECTED_COLOR_LIBRARY_ITEM_ID", default = SettingDefault.of(-1))

    /** Names of the selected library and item kept for summaries (`ColorSelectBaseActivity`, Q15). */
    @JvmField
    val COLOR_SELECT_LIBRARY_NAME = StateKey.str(name = "SELECTED_COLOR_LIBRARY_NAME", default = SettingDefault.None)

    @JvmField
    val COLOR_SELECT_ITEM_NAME = StateKey.str(name = "SELECTED_COLOR_LIBRARY_ITEM_NAME", default = SettingDefault.None)

    /** The selection before a script changed the theme color, for undo (`ScriptThemeChanges`, appendix C Q18). */
    @JvmField
    val THEME_COLOR_SCRIPT_UNDO = StateKey.json(name = "THEME_COLOR_SCRIPT_UNDO", type = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None)

    /** The active snapshot, the run-time state and the script state of the intelligent colors (`IntelligentColorEngine`, Q18). */
    @JvmField
    val INTELLIGENT_COLOR_ACTIVE_SNAPSHOT = StateKey.json(name = "INTELLIGENT_COLOR_ACTIVE_SNAPSHOT", type = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None)

    @JvmField
    val INTELLIGENT_COLOR_RUNTIME_STATE = StateKey.json(name = "INTELLIGENT_COLOR_RUNTIME_STATE", type = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None)

    @JvmField
    val INTELLIGENT_COLOR_SCRIPT_STATE = StateKey.str(name = "INTELLIGENT_COLOR_SCRIPT_STATE", default = SettingDefault.None)

    /** Position of the floating button as fractions of the screen (`CircularMenuWindow`, Q18). */
    @JvmField
    val CIRCULAR_MENU_POSITION_X_PERCENT = StateKey.float(name = "org.autojs.autojs.ui.floating.CircularMenuWindow.position.x_percent", default = SettingDefault.of(0f))

    @JvmField
    val CIRCULAR_MENU_POSITION_Y_PERCENT = StateKey.float(name = "org.autojs.autojs.ui.floating.CircularMenuWindow.position.y_percent", default = SettingDefault.of(1 - 0.618f))

    /** The screen orientation the floating button was last placed in (`CircularMenuWindow`). */
    @JvmField
    val CIRCULAR_MENU_LAST_ORIENTATION = StateKey.int(name = "key_\$_last_orientation", default = SettingDefault.of(Configuration.ORIENTATION_PORTRAIT))

    /** Position of the floating controller as fractions of the screen (`FloatingControllerWindow`, Q18); -1 when not placed yet. */
    @JvmField
    val FLOATING_CONTROLLER_POSITION_X_PERCENT = StateKey.float(name = "org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.x_percent", default = SettingDefault.of(-1f))

    @JvmField
    val FLOATING_CONTROLLER_POSITION_Y_PERCENT = StateKey.float(name = "org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.y_percent", default = SettingDefault.of(-1f))

    // ---------------------------------------------------------------------- the file manager (P4.2)
    // The names are the simple name of `ExplorerView` with a suffix (`ExplorerViewStateStore.prefKey`, appendix C Q24).
    // zh-CN: 名称为 `ExplorerView` 的简单类名加后缀 (`ExplorerViewStateStore.prefKey`, 附录 C Q24).

    /** The scroll position of the file manager page; readers take values <= 0 (and the missing -1) as none. */
    @JvmField
    val EXPLORER_VIEW_PAGE_STATE = StateKey.int(name = "ExplorerView.page_state", default = SettingDefault.of(-1))

    /** The current and the root directory of the file manager. */
    @JvmField
    val EXPLORER_VIEW_CURRENT = StateKey.str(name = "ExplorerView.explorer_current", default = SettingDefault.None)

    @JvmField
    val EXPLORER_VIEW_ROOT = StateKey.str(name = "ExplorerView.explorer_root", default = SettingDefault.None)

    /** Pages left behind ("path,scroll,directories collapsed,files collapsed"), a JSON list as `Pref.putLinkedList` wrote it. */
    @JvmField
    val EXPLORER_VIEW_HISTORY = StateKey.json(
        name = "ExplorerView.explorer_history", type = SettingType.Json(SettingsJson.STRING_LIST, htmlSafe = true), default = SettingDefault.of(emptyList()),
    )

    // ---------------------------------------------------------------------- layout inspector and accessibility (P4.2)

    /** One-time introductions of the field and selector query modes (`QueryModeStore`). */
    @JvmField
    val LAYOUT_INSPECTOR_FIELD_MODE_INTRODUCED = StateKey.bool(R.string.key_layout_inspector_field_mode_introduced, default = SettingDefault.of(false))

    @JvmField
    val LAYOUT_INSPECTOR_SELECTOR_MODE_INTRODUCED = StateKey.bool(R.string.key_layout_inspector_selector_mode_introduced, default = SettingDefault.of(false))

    /** The query history of the layout inspector, a JSON array that `QueryHistory` reads and writes (`QueryHistoryStore`). */
    @JvmField
    val LAYOUT_INSPECTOR_QUERY_HISTORY = StateKey.json(
        R.string.key_layout_inspector_query_history, SettingType.Json(SettingsJson.ARRAY), SettingDefault.None,
    )

    /** "Key observing" of the accessibility page: no reader or writer is left (appendix C Q12), registered only. */
    @JvmField
    val ENABLE_OBSERVE_KEY = StateKey.bool(R.string.key_enable_observe_key, default = SettingDefault.of(false))

    override val keys: List<PrefKey<*>> = listOf(
        FIND_OR_REPLACE_KEYWORDS, FIND_OR_REPLACE_USING_REGEX, CODE_EVALUATE_CODE, ROOT_AUTOMATOR_TOUCH_DEVICE,
        EDITOR_PINCH_TO_ZOOM_STRATEGY, ACE_LSP_FILE_TYPES_REVISION,
        SEEN_EDITOR_SWITCH_UNDO_NOTICE, SEEN_ACE_FALLBACK_NOTICE, SEEN_ACE_LARGE_FILE_NOTICE,
        A11Y_SERVICE, FLOATING_MENU_SHOWN,
        A11Y_SERVICE_MANAGER_STATUS_LEVEL, A11Y_SERVICE_MANAGER_CONTROL_LEVEL, A11Y_SERVICE_MANAGER_SETTINGS_COLLAPSED,
        CLIENT_MODE_MANAGER_STATUS_LEVEL, CLIENT_MODE_MANAGER_CONTROL_LEVEL, CLIENT_MODE_MANAGER_SETTINGS_COLLAPSED, CLIENT_MODE_MANAGER_CLIENTS_LEVEL,
        SERVER_MODE_MANAGER_STATUS_LEVEL, SERVER_MODE_MANAGER_CONTROL_LEVEL, SERVER_MODE_MANAGER_SETTINGS_COLLAPSED, SERVER_MODE_MANAGER_CLIENTS_LEVEL,
        CLIENT_SOCKET_NORMALLY_CLOSED, SERVER_SOCKET_NORMALLY_CLOSED, MCP_SERVER_NORMALLY_CLOSED, PC_SERVER_ADDRESS_HISTORY,
        LAST_UPDATES_CHECKED, LAST_NO_NEWER_UPDATES, LAST_UPDATES_POSTPONED, LAST_UPDATES_AUTO_CHECKED, PLUGIN_CENTER_LAST_ONLINE_CHECK_TS,
        POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED, SETUP_WIZARD_WELCOME_DONE, WORKING_DIRECTORY_INITIALIZED, WORKING_DIRECTORY_HISTORY,
        KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_LAST_ENABLED, COLOR_SELECT_ACTIVITY_LEGACY_LAYOUT,
        COLOR_SELECT_CUSTOM_COLOR, COLOR_SELECT_LEGACY_SELECTED_INDEX, COLOR_SELECT_LIBRARY_ID, COLOR_SELECT_ITEM_ID,
        COLOR_SELECT_LIBRARY_NAME, COLOR_SELECT_ITEM_NAME,
        THEME_COLOR_SCRIPT_UNDO, INTELLIGENT_COLOR_ACTIVE_SNAPSHOT, INTELLIGENT_COLOR_RUNTIME_STATE, INTELLIGENT_COLOR_SCRIPT_STATE,
        CIRCULAR_MENU_POSITION_X_PERCENT, CIRCULAR_MENU_POSITION_Y_PERCENT, CIRCULAR_MENU_LAST_ORIENTATION,
        FLOATING_CONTROLLER_POSITION_X_PERCENT, FLOATING_CONTROLLER_POSITION_Y_PERCENT,
        EXPLORER_VIEW_PAGE_STATE, EXPLORER_VIEW_CURRENT, EXPLORER_VIEW_ROOT, EXPLORER_VIEW_HISTORY,
        LAYOUT_INSPECTOR_FIELD_MODE_INTRODUCED, LAYOUT_INSPECTOR_SELECTOR_MODE_INTRODUCED, LAYOUT_INSPECTOR_QUERY_HISTORY,
        ENABLE_OBSERVE_KEY,
    )

    /**
     * Writes the current time into one of the time stamps (`Pref.refreshLast...Timestamp`, apply).
     * zh-CN: 把当前时间写入某个时间戳 (同 `Pref.refreshLast...Timestamp`, apply).
     */
    @JvmStatic
    fun markNow(key: StateKey<Long>) = key.set(System.currentTimeMillis())

    /**
     * Removes the four update check time stamps in one write (`Pref.clearUpdatesCheckedStates`).
     * zh-CN: 一次写入删除四个更新检查时间戳 (同 `Pref.clearUpdatesCheckedStates`).
     */
    @JvmStatic
    fun clearUpdatesCheckedStates() {
        SettingsStores.default.edit {
            remove(LAST_NO_NEWER_UPDATES.name)
            remove(LAST_UPDATES_POSTPONED.name)
            remove(LAST_UPDATES_CHECKED.name)
            remove(LAST_UPDATES_AUTO_CHECKED.name)
        }
    }

    /**
     * A string list key read as `Pref.getLinkedHashSet` did: empty when the key is missing, otherwise the stored JSON
     * text decoded strictly (malformed text throws, as before, R2).
     * zh-CN: 读法同 `Pref.getLinkedHashSet`: 键不存在时为空, 否则严格解码存储的 JSON 文本 (格式错误时与原先一样抛出, R2).
     */
    @JvmStatic
    fun linkedSet(key: StateKey<List<String>>): LinkedHashSet<String> = LinkedHashSet(stringList(key))

    /** Writes a string list key as `Pref.putLinkedHashSet` did (the JSON text Gson used to write, R1). */
    @JvmStatic
    fun setLinkedSet(key: StateKey<List<String>>, values: Collection<String>) = key.set(values.toList())

    /**
     * A string list key read as `Pref.getLinkedList` did: like [linkedSet], but duplicates are kept.
     * zh-CN: 读法同 `Pref.getLinkedList`: 与 [linkedSet] 相同, 但保留重复项.
     */
    @JvmStatic
    fun stringList(key: StateKey<List<String>>): List<String> {
        val text = key.settingsStore.getString(key.name, null) ?: return emptyList()
        return SettingsJson.decodeStringList(text)
    }
}
