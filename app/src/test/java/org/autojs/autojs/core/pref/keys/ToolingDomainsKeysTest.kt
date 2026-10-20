package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.console.ConsoleLogFormat
import org.autojs.autojs.core.console.ConsoleTimeSettings
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.pluginclient.DevPluginPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The domains of docs/settings/ROADMAP.md P2.5: [ConsoleKeys] (with `ConsoleTimeSettings`), [ConnectionKeys] (with the
 * deprecated `DevPluginPrefs`), [LayoutInspectorKeys] and [DeveloperKeys].
 *
 * zh-CN: ROADMAP P2.5 的领域: 日志与控制台 (含 `ConsoleTimeSettings`), 客户端与服务端模式 (含已弃用的 `DevPluginPrefs`), 布局分析器,
 * 开发者选项.
 */
class ToolingDomainsKeysTest : DomainKeysTestBase() {

    @Test
    fun consoleTimeSettingsReadTheirKeys() {
        assertEquals(12, ConsoleKeys.keys.size)
        // Defaults as before: visible on the log page and in the editor panel, hidden elsewhere.
        assertEquals(
            listOf(true, true, false, false, false),
            ConsoleTimeSettings.entries.map { it.isTimeVisible },
        )
        assertEquals(EditorKeys.EDITOR_LOG_PANEL_TIME_VISIBLE, ConsoleTimeSettings.EDITOR_PANEL.visibleKey)
        assertEquals(EditorKeys.EDITOR_LOG_PANEL_TIME_VISIBLE.keyRes, ConsoleTimeSettings.EDITOR_PANEL.visibleKeyRes)
        ConsoleTimeSettings.FLOATING_WINDOW.isTimeVisible = true
        assertTrue(store.getBoolean(key("key_floating_console_time_visible"), false))
        assertEquals(ConsoleLogFormat.DEFAULT_TIME_PATTERN, ConsoleTimeSettings.LOG_ACTIVITY.timeFormat)
        ConsoleTimeSettings.LOG_ACTIVITY.timeFormat = "HH:mm"
        assertEquals("HH:mm", store.getString(key("key_log_activity_time_format"), null))
        assertTrue(ConsoleKeys.LOG_ACTIVITY_LOG_COLORING.get())
        assertEquals(0, ConsoleKeys.LOG_ACTIVITY_TEXT_SIZE.get())
        assertFalse(ConsoleKeys.LOG_ACTIVITY_INPUT_BAR_VISIBLE.get())
        assertFalse(ConsoleKeys.LOG_ACTIVITY_TYPESCRIPT_CONSOLE_VISIBLE.get())
    }

    @Test
    @Suppress("DEPRECATION")
    fun connectionSwitchesForwardFromDevPluginPrefs() {
        assertEquals(7, ConnectionKeys.keys.size)
        assertTrue(DevPluginPrefs.isClientAutoReconnectEnabled)
        assertTrue(DevPluginPrefs.isClientReconnectOnNetworkChangeEnabled)
        assertTrue(DevPluginPrefs.isClientRestoreOnLaunchEnabled)
        assertTrue(DevPluginPrefs.isServerRestoreOnLaunchEnabled)
        assertTrue(DevPluginPrefs.isServerNotifyConnectionChangesEnabled)
        assertTrue(DevPluginPrefs.isServerLanDiscoveryEnabled)
        ConnectionKeys.SERVER_MODE_LAN_DISCOVERY.set(false)
        assertFalse(DevPluginPrefs.isServerLanDiscoveryEnabled)
        // The gateway address is a run-time default; a stored address is read as it is.
        assertEquals("if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS", ConnectionKeys.SERVER_ADDRESS.defaultText)
        ConnectionKeys.SERVER_ADDRESS.set("192.168.1.8")
        assertEquals("192.168.1.8", ConnectionKeys.SERVER_ADDRESS.get())
    }

    @Test
    fun layoutInspectorKeysHaveNoDefaults() {
        assertEquals(5, LayoutInspectorKeys.keys.size)
        LayoutInspectorKeys.keys.forEach { assertNull(it.name, it.getOrNull()) }
        LayoutInspectorKeys.LAYOUT_INSPECTOR_QUERY_MODE.set("SELECTOR")
        assertEquals("SELECTOR", store.getString(key("key_layout_inspector_query_mode"), null))
        LayoutInspectorKeys.LAYOUT_INSPECTOR_QUERY_MODE.set(null)
        assertFalse(LayoutInspectorKeys.LAYOUT_INSPECTOR_QUERY_MODE.isStored())
        assertTrue(LayoutInspectorKeys.keys.all { it.category == SettingCategory.LAYOUT_INSPECTOR })
    }

    @Test
    fun developerKeys() {
        assertFalse(DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.get())
        assertFalse(DeveloperKeys.SCRIPT_MODULE_DEBUG_PRINT.get())
        assertFalse(DeveloperKeys.NODEJS_INSPECTOR_ENABLED.get())
        assertTrue(DeveloperKeys.LEAKCANARY_ENABLED.get())
        assertEquals(emptySet<String>(), DeveloperKeys.scriptModuleDebugPrintDisabledModules)
        DeveloperKeys.scriptModuleDebugPrintDisabledModules = setOf("util", "colors")
        assertEquals(setOf("util", "colors"), store.getStringSet(key("key_script_module_debug_print_disabled_modules"), null))
        assertEquals(setOf("util", "colors"), DeveloperKeys.scriptModuleDebugPrintDisabledModules)
        assertFalse(SettingCategory.DEVELOPER.defaultSelected)
    }
}
