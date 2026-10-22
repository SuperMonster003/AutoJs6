package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.plugin.center.PluginOnlineSettingsStore
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs.engine.RootAutomatorEngine
import org.autojs.autojs.theme.app.ColorSettingRecyclerView
import org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.explorer.ExplorerStoredPageState
import org.autojs.autojs.ui.explorer.ExplorerViewStateStore
import org.autojs.autojs.ui.floating.CircularMenuWindow
import org.autojs.autojs.ui.floating.controller.FloatingControllerWindow
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P2.6: [MiscStateKeys] (the states and the cache of the former direct readers of the default
 * preferences, names unchanged by R1) and [PluginCenterKeys] (the last default preference settings); P4.2: the other
 * remembered states, the Q18 literal keys and the file manager states (Q24).
 *
 * zh-CN: ROADMAP P2.6: [MiscStateKeys] (原先直接读取默认偏好的读取方的状态与缓存, 名称按 R1 不变) 与 [PluginCenterKeys] (默认偏好中
 * 最后的几个设置); P4.2: 其余记忆型状态, Q18 的字面量键与文件管理器状态 (Q24).
 */
class MiscStateKeysTest : DomainKeysTestBase() {

    @Test
    fun namesAreThoseTheReadersUsed() {
        assertEquals("...", MiscStateKeys.FIND_OR_REPLACE_KEYWORDS.name)
        assertEquals("find_or_replace_using_regex", MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name)
        // The two readers built these names from their class names.
        assertEquals(CodeEvaluateDialogBuilder::class.java.name + ".code", MiscStateKeys.CODE_EVALUATE_CODE.name)
        assertEquals(RootAutomatorEngine::class.java.name + ".touch_device", MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name)
    }

    @Test
    fun statesAndTheCache() {
        listOf(MiscStateKeys.FIND_OR_REPLACE_KEYWORDS, MiscStateKeys.FIND_OR_REPLACE_USING_REGEX, MiscStateKeys.CODE_EVALUATE_CODE).forEach {
            assertEquals(SettingKind.STATE, it.kind)
            assertEquals(SettingCategory.OTHER, it.category)
        }
        assertEquals(SettingKind.CACHE, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.kind)
        assertNull(MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.category)
        assertEquals("", MiscStateKeys.FIND_OR_REPLACE_KEYWORDS.get())
        assertFalse(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.get())
        assertEquals(-1, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get())
        // FindOrReplaceDialogBuilder.storeState writes both in one edit.
        val commits = store.commitCount
        SettingsStores.default.edit {
            this[MiscStateKeys.FIND_OR_REPLACE_KEYWORDS] = "auto.waitFor"
            this[MiscStateKeys.FIND_OR_REPLACE_USING_REGEX] = true
        }
        assertEquals(commits + 1, store.commitCount)
        assertEquals("auto.waitFor", store.getString("...", null))
        assertTrue(store.getBoolean("find_or_replace_using_regex", false))
        MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.set(4)
        assertEquals(4, store.getInt("org.autojs.autojs.engine.RootAutomatorEngine.touch_device", -1))
    }

    @Test
    fun p42NamesAreThoseTheReadersUsed() {
        // Literal names built from class names by the former readers (R1).
        val colorSetting = "org.autojs.autojs.theme.app.ColorSettingRecyclerView"
        assertEquals(colorSetting, ColorSettingRecyclerView::class.java.name)
        assertEquals("$colorSetting.COLOR_SETTING_CUSTOM_COLOR", MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR.name)
        assertEquals("$colorSetting.SELECTED_COLOR_INDEX", MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX.name)
        assertEquals(CircularMenuWindow::class.java.name + ".position.x_percent", FloatingKeys.CIRCULAR_MENU_POSITION_X_PERCENT.name)
        assertEquals(CircularMenuWindow::class.java.name + ".position.y_percent", FloatingKeys.CIRCULAR_MENU_POSITION_Y_PERCENT.name)
        assertEquals(FloatingControllerWindow::class.java.name + ".position.x_percent", FloatingKeys.FLOATING_CONTROLLER_POSITION_X_PERCENT.name)
        assertEquals(FloatingControllerWindow::class.java.name + ".position.y_percent", FloatingKeys.FLOATING_CONTROLLER_POSITION_Y_PERCENT.name)
        assertEquals(ExplorerViewStateStore.prefKey("page_state"), MiscStateKeys.EXPLORER_VIEW_PAGE_STATE.name)
        assertEquals(ExplorerViewStateStore.prefKey("explorer_current"), MiscStateKeys.EXPLORER_VIEW_CURRENT.name)
        assertEquals(ExplorerViewStateStore.prefKey("explorer_root"), MiscStateKeys.EXPLORER_VIEW_ROOT.name)
        assertEquals(ExplorerViewStateStore.prefKey("explorer_history"), MiscStateKeys.EXPLORER_VIEW_HISTORY.name)
        val sortConfig = ExplorerItemManager.SortConfig::class.java.name
        assertEquals("$sortConfig.file_sort_type", ExplorerKeys.EXPLORER_FILE_SORT_TYPE.name)
        assertEquals("$sortConfig.dir_sort_type", ExplorerKeys.EXPLORER_DIR_SORT_TYPE.name)
        assertEquals("$sortConfig.file_ascending", ExplorerKeys.EXPLORER_FILE_ASCENDING.name)
        assertEquals("$sortConfig.dir_ascending", ExplorerKeys.EXPLORER_DIR_ASCENDING.name)
        assertEquals("key_\$_last_orientation", MiscStateKeys.CIRCULAR_MENU_LAST_ORIENTATION.name)
        assertEquals("key_\$_ace_lsp_file_types_revision", MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.name)
        assertEquals(AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES_REVISION, MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.name)
        // Resource keys keep their key strings.
        assertEquals(key("key_last_updates_checked"), MiscStateKeys.LAST_UPDATES_CHECKED.name)
        assertEquals(key("key_pc_server_address_history"), MiscStateKeys.PC_SERVER_ADDRESS_HISTORY.name)
        assertEquals(key("key_keep_screen_on_when_in_foreground_last_enabled"), MiscStateKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_LAST_ENABLED.name)
    }

    @Test
    fun p42EveryStateIsOtherAndRegistered() {
        MiscStateKeys.keys.filter { it !== MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE }.forEach {
            assertTrue(it.name, it is StateKey<*>)
            assertEquals(it.name, SettingKind.STATE, it.kind)
            assertEquals(it.name, SettingCategory.OTHER, it.category)
            assertSame(it.name, it, SettingsRegistry.find(it.name))
        }
        // The Q18 settings are user choices, not states.
        assertEquals(SettingKind.SETTING, AppearanceKeys.COLOR_FAVORITE_SORT.kind)
        assertEquals(SettingCategory.APPEARANCE, AppearanceKeys.COLOR_FAVORITE_SORT.category)
        listOf(ExplorerKeys.EXPLORER_FILE_SORT_TYPE, ExplorerKeys.EXPLORER_DIR_SORT_TYPE, ExplorerKeys.EXPLORER_FILE_ASCENDING, ExplorerKeys.EXPLORER_DIR_ASCENDING).forEach {
            assertEquals(it.name, SettingKind.SETTING, it.kind)
            assertEquals(it.name, SettingCategory.EXPLORER, it.category)
            assertSame(it.name, it, SettingsRegistry.find(it.name))
        }
    }

    @Test
    fun p42DefaultsAreThoseOfTheReaders() {
        listOf(MiscStateKeys.LAST_UPDATES_CHECKED, MiscStateKeys.LAST_NO_NEWER_UPDATES, MiscStateKeys.LAST_UPDATES_POSTPONED, MiscStateKeys.LAST_UPDATES_AUTO_CHECKED).forEach {
            assertEquals(it.name, -1L, it.get())
        }
        assertEquals(0L, MiscStateKeys.PLUGIN_CENTER_LAST_ONLINE_CHECK_TS.get())
        listOf(MiscStateKeys.CLIENT_SOCKET_NORMALLY_CLOSED, MiscStateKeys.SERVER_SOCKET_NORMALLY_CLOSED, MiscStateKeys.MCP_SERVER_NORMALLY_CLOSED).forEach {
            assertTrue(it.name, it.get())
        }
        listOf(
            MiscStateKeys.A11Y_SERVICE, MiscStateKeys.FLOATING_MENU_SHOWN, MiscStateKeys.POST_NOTIFICATIONS_PERMISSION_REQUEST_ATTEMPTED,
            MiscStateKeys.SETUP_WIZARD_WELCOME_DONE, MiscStateKeys.WORKING_DIRECTORY_INITIALIZED, MiscStateKeys.COLOR_SELECT_ACTIVITY_LEGACY_LAYOUT,
            MiscStateKeys.A11Y_SERVICE_MANAGER_SETTINGS_COLLAPSED, MiscStateKeys.CLIENT_MODE_MANAGER_SETTINGS_COLLAPSED,
            MiscStateKeys.SERVER_MODE_MANAGER_SETTINGS_COLLAPSED, MiscStateKeys.LAYOUT_INSPECTOR_FIELD_MODE_INTRODUCED,
            MiscStateKeys.LAYOUT_INSPECTOR_SELECTOR_MODE_INTRODUCED, MiscStateKeys.ENABLE_OBSERVE_KEY,
            MiscStateKeys.SEEN_EDITOR_SWITCH_UNDO_NOTICE, MiscStateKeys.SEEN_ACE_FALLBACK_NOTICE, MiscStateKeys.SEEN_ACE_LARGE_FILE_NOTICE,
        ).forEach { assertFalse(it.name, it.get()) }
        assertEquals(1, MiscStateKeys.A11Y_SERVICE_MANAGER_STATUS_LEVEL.get())
        assertEquals(1, MiscStateKeys.CLIENT_MODE_MANAGER_CONTROL_LEVEL.get())
        assertEquals(2, MiscStateKeys.CLIENT_MODE_MANAGER_CLIENTS_LEVEL.get())
        assertEquals(2, MiscStateKeys.SERVER_MODE_MANAGER_CLIENTS_LEVEL.get())
        assertEquals(1, MiscStateKeys.ACE_LSP_FILE_TYPES_REVISION.get())
        assertEquals(-1, MiscStateKeys.COLOR_SELECT_LIBRARY_ID.get())
        assertEquals(-1, MiscStateKeys.COLOR_SELECT_ITEM_ID.get())
        assertEquals(-1, MiscStateKeys.EXPLORER_VIEW_PAGE_STATE.get())
        assertEquals(0f, FloatingKeys.CIRCULAR_MENU_POSITION_X_PERCENT.get())
        assertEquals(1 - 0.618f, FloatingKeys.CIRCULAR_MENU_POSITION_Y_PERCENT.get())
        assertEquals(-1f, FloatingKeys.FLOATING_CONTROLLER_POSITION_X_PERCENT.get())
        assertEquals(key("key_keep_screen_on_when_in_foreground_homepage_only"), MiscStateKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_LAST_ENABLED.get())
        assertEquals(emptyList<String>(), MiscStateKeys.PC_SERVER_ADDRESS_HISTORY.get())
        // Readers with a fallback of their own see no value.
        listOf(
            MiscStateKeys.EDITOR_PINCH_TO_ZOOM_STRATEGY, MiscStateKeys.COLOR_SELECT_CUSTOM_COLOR, MiscStateKeys.COLOR_SELECT_LEGACY_SELECTED_INDEX,
            MiscStateKeys.COLOR_SELECT_LIBRARY_NAME, MiscStateKeys.THEME_COLOR_SCRIPT_UNDO, MiscStateKeys.INTELLIGENT_COLOR_SCRIPT_STATE,
            MiscStateKeys.LAYOUT_INSPECTOR_QUERY_HISTORY, MiscStateKeys.EXPLORER_VIEW_CURRENT, AppearanceKeys.COLOR_FAVORITE_SORT,
        ).forEach { assertNull(it.name, it.getOrNull()) }
        val sortConfig = ExplorerItemManager.SortConfig.load()
        assertEquals(ExplorerItemManager.SORT_TYPE_NAME, sortConfig.fileSortType)
        assertEquals(ExplorerItemManager.SORT_TYPE_NAME, sortConfig.dirSortType)
        assertTrue(sortConfig.isFileSortedAscending && sortConfig.isDirSortedAscending)
    }

    @Test
    fun p42TimestampsAsPrefDidThem() {
        val before = System.currentTimeMillis()
        MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_CHECKED)
        MiscStateKeys.markNow(MiscStateKeys.LAST_UPDATES_POSTPONED)
        assertTrue(store.getLong(key("key_last_updates_checked"), -1) >= before)
        // The four go in one edit, as the former Pref.clearUpdatesCheckedStates did.
        val commits = store.commitCount
        MiscStateKeys.clearUpdatesCheckedStates()
        assertEquals(commits + 1, store.commitCount)
        assertFalse(store.contains(key("key_last_updates_checked")))
        assertFalse(store.contains(key("key_last_updates_postponed")))
        assertEquals(-1L, MiscStateKeys.LAST_UPDATES_CHECKED.get())
    }

    @Test
    fun p42StringListsKeepTheGsonText() {
        assertEquals(LinkedHashSet<String>(), MiscStateKeys.linkedSet(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY))
        MiscStateKeys.setLinkedSet(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY, linkedSetOf("192.168.1.2", "host<1>"))
        // Gson escaped HTML characters (Pref.putLinkedHashSet).
        assertEquals("[\"192.168.1.2\",\"host\\u003c1\\u003e\"]", store.getString(key("key_pc_server_address_history"), null))
        assertEquals(linkedSetOf("192.168.1.2", "host<1>"), MiscStateKeys.linkedSet(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY))
        // Malformed text throws as Pref.getLinkedHashSet did (R2).
        store.edit { put(key("key_working_directory_history"), "not json") }
        assertThrows(SettingsJson.SettingsJsonException::class.java) { MiscStateKeys.linkedSet(MiscStateKeys.WORKING_DIRECTORY_HISTORY) }
        // A list keeps duplicates (Pref.getLinkedList).
        store.edit { put(key("key_pc_server_address_history"), "[\"192.168.1.2\",\"192.168.1.2\"]") }
        assertEquals(listOf("192.168.1.2", "192.168.1.2"), MiscStateKeys.stringList(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY))
    }

    @Test
    fun p42JsonTextsAreStoredAsTheReadersWroteThem() {
        val undo = "{ \"selected\" : 3, \"name\": \"a<b\" }"
        MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.setStoredText(undo)
        assertEquals(undo, store.getString("THEME_COLOR_SCRIPT_UNDO", null))
        assertEquals(undo, MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.storedText())
        val history = "[{\"query\":\"text(\\\"OK\\\")\",\"mode\":\"selector\"}]"
        MiscStateKeys.LAYOUT_INSPECTOR_QUERY_HISTORY.setStoredText(history)
        assertEquals(history, store.getString(key("key_layout_inspector_query_history"), null))
        MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.remove()
        assertNull(MiscStateKeys.THEME_COLOR_SCRIPT_UNDO.storedText())
    }

    @Test
    fun p42ExplorerStatesAndSortConfig() {
        val commits = store.commitCount
        val page = ExplorerStoredPageState("/sdcard/Scripts,a", 12, directoriesCollapsed = true, filesCollapsed = false)
        ExplorerViewStateStore.saveExplorer("/sdcard/Scripts", "/sdcard", listOf(page))
        assertEquals(commits + 1, store.commitCount)
        assertEquals("/sdcard/Scripts", store.getString("ExplorerView.explorer_current", null))
        // Page objects since appendix C Q40.
        assertEquals(
            "[{\"path\":\"/sdcard/Scripts,a\",\"scrollPosition\":12,\"directoriesCollapsed\":true,\"filesCollapsed\":false}]",
            store.getString("ExplorerView.explorer_history", null),
        )
        val restored = ExplorerViewStateStore.restoreExplorer()
        assertEquals("/sdcard", restored.rootPath)
        assertEquals(listOf(page), restored.history)
        // The text of older versions (Pref.putLinkedList of "path,scroll,directories collapsed,files collapsed") is still read.
        store.edit { put("ExplorerView.explorer_history", "[\"/sdcard/Scripts,a,12,true,false\",\"/sdcard,0,false,false\",\"broken\"]") }
        assertEquals(
            listOf(page, ExplorerStoredPageState("/sdcard", 0, directoriesCollapsed = false, filesCollapsed = false)),
            ExplorerViewStateStore.restoreExplorer().history,
        )
        assertNull(ExplorerViewStateStore.restorePageScroll())
        ExplorerViewStateStore.savePageScroll(40)
        assertEquals(40, ExplorerViewStateStore.restorePageScroll())
        ExplorerViewStateStore.clear()
        assertTrue(store.all().keys.none { it.startsWith("ExplorerView.") })
        // ExplorerItemManager.saveSortConfig writes the four in one edit.
        val sortConfig = ExplorerItemManager.SortConfig().apply {
            fileSortType = ExplorerItemManager.SORT_TYPE_DATE
            isDirSortedAscending = false
        }
        val sortCommits = store.commitCount
        sortConfig.save()
        assertEquals(sortCommits + 1, store.commitCount)
        assertEquals(ExplorerItemManager.SORT_TYPE_DATE, store.getInt(ExplorerKeys.EXPLORER_FILE_SORT_TYPE.name, 0))
        assertFalse(ExplorerItemManager.SortConfig.load().isDirSortedAscending)
    }

    @Test
    fun pluginCenterSettingsPage() {
        // The settings page keys; the named stores of the plugin center follow (P3.1, PluginCenterStoresTest).
        assertEquals(PluginCenterKeys.keys.take(3), listOf(PluginCenterKeys.PLUGIN_CENTER_SHOW_OFFICIAL_PLUGINS, PluginCenterKeys.PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS, PluginCenterKeys.PLUGIN_CENTER_AUTO_CHECK_UPDATES))
        assertTrue(PluginOnlineSettingsStore.isOfficialEnabled())
        assertTrue(PluginOnlineSettingsStore.isThirdPartyEnabled())
        assertTrue(PluginOnlineSettingsStore.isAutoCheckUpdatesEnabled())
        PluginCenterKeys.PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS.set(false)
        assertFalse(PluginOnlineSettingsStore.isThirdPartyEnabled())
        assertFalse(store.getBoolean(key("key_plugin_center_show_third_party_plugins"), true))
    }
}
