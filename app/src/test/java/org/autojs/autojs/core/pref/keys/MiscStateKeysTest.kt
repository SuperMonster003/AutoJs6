package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.plugin.center.PluginOnlineSettingsStore
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs.engine.RootAutomatorEngine
import org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P2.6: [MiscStateKeys] (the states and the cache of the former direct readers of the default
 * preferences, names unchanged by R1) and [PluginCenterKeys] (the last default preference settings).
 *
 * zh-CN: ROADMAP P2.6: [MiscStateKeys] (原先直接读取默认偏好的读取方的状态与缓存, 名称按 R1 不变) 与 [PluginCenterKeys] (默认偏好中
 * 最后的几个设置).
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
    fun pluginCenterSettingsPage() {
        assertEquals(3, PluginCenterKeys.keys.size)
        assertTrue(PluginOnlineSettingsStore.isOfficialEnabled())
        assertTrue(PluginOnlineSettingsStore.isThirdPartyEnabled())
        assertTrue(PluginOnlineSettingsStore.isAutoCheckUpdatesEnabled())
        PluginCenterKeys.PLUGIN_CENTER_SHOW_THIRD_PARTY_PLUGINS.set(false)
        assertFalse(PluginOnlineSettingsStore.isThirdPartyEnabled())
        assertFalse(store.getBoolean(key("key_plugin_center_show_third_party_plugins"), true))
    }
}
