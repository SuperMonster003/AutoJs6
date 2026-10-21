package org.autojs.autojs.core.pref.keys

import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginFilterStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginMountStore
import org.autojs.autojs.core.plugin.center.PluginRecentStore
import org.autojs.autojs.core.plugin.center.PluginUpdateCheckStore
import org.autojs.autojs.core.plugin.center.PluginVariantPriorityStore
import org.autojs.autojs.core.plugin.center.PluginVisibilityStore
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3.1: the plugin center stores on the declared layer ([PluginCenterKeys], [NamedStores]):
 * file and key names unchanged (R1), the categories of appendix A.1 (`plugin_center`, `plugin_enable_state`, states in
 * `other`), and the moved readers and writers reading old values and writing what they wrote before (R2).
 *
 * zh-CN: ROADMAP P3.1: 插件中心的存储迁移到声明层: 文件名与键名不变 (R1), 分类同附录 A.1, 迁移后的读写方能读旧值并写出与原先相同
 * 的值 (R2).
 */
class PluginCenterStoresTest : DomainKeysTestBase() {

    private val context: Context = ContextWrapper(null)

    @Test
    fun storesKeysAndCategories() {
        val stores = listOf(
            NamedStores.PLUGIN_CENTER_FILTER_STATE, NamedStores.PLUGIN_CENTER_SORT_STATE, NamedStores.PLUGIN_CENTER_VISIBILITY_STATE,
            NamedStores.PLUGIN_LAUNCHER_ICONS, NamedStores.PLUGIN_CENTER_ENABLE_STATE, NamedStores.PLUGIN_CENTER_RECENT,
            NamedStores.PLUGIN_CENTER_ATTENTION,
        )
        assertEquals(
            listOf("plugin_center_filter_state", "plugin_center_sort_state", "plugin_center_visibility_state", "plugin_launcher_icons",
                "plugin_center_enable_state", "plugin_center_recent", "plugin_center_attention"),
            stores.map(StoreId::name),
        )
        val rows = SettingsRegistry.describe()
        fun categoriesOf(id: StoreId) = rows.filter { it.store == id.name }.map { it.category }.toSet()
        assertEquals(setOf("plugin_center"), categoriesOf(NamedStores.PLUGIN_CENTER_SORT_STATE))
        assertEquals(setOf("plugin_enable_state"), categoriesOf(NamedStores.PLUGIN_CENTER_ENABLE_STATE))
        assertEquals(setOf("other"), categoriesOf(NamedStores.PLUGIN_CENTER_RECENT) + categoriesOf(NamedStores.PLUGIN_CENTER_ATTENTION))
        // The three sort stores share one file: 3 x (3 keys + 2 families).
        assertEquals(15, rows.count { it.store == "plugin_center_sort_state" })
        assertEquals(
            listOf("key_\$_plugin_center_sort_state", "key_\$_plugin_center_sort_state_official", "key_\$_plugin_center_sort_state_third_party"),
            listOf(PluginCenterKeys.SORT_ALL, PluginCenterKeys.SORT_OFFICIAL, PluginCenterKeys.SORT_THIRD_PARTY).map { it.sort.name },
        )
        assertEquals(SettingKind.STATE, PluginCenterKeys.LAST_INSTALL_TIME.kind)
        assertEquals(SettingCategory.PLUGIN_ENABLE_STATE, PluginCenterKeys.PLUGIN_ENABLED.category)
        assertTrue(PluginCenterKeys.families.none { it.template.store.isDefault && it.prefix.isEmpty() })
    }

    @Test
    fun filterStateReadsTheLegacyOrdinalUntilAModernFilterIsStored() {
        val filters = store(NamedStores.PLUGIN_CENTER_FILTER_STATE)
        assertEquals(PluginFilterStore.FilterState(), PluginFilterStore.getFilterState(context))
        filters.put("key_\$_plugin_center_filter_state", 5) // LEGACY_FILTER_UPDATABLE
        assertEquals(PluginFilterStore.FilterState(updatableOnly = true), PluginFilterStore.getFilterState(context))
        val state = PluginFilterStore.FilterState(
            installState = PluginFilterStore.InstallState.INSTALLED,
            sourceState = PluginFilterStore.SourceState.THIRD_PARTY,
        )
        val writes = filters.commitCount
        PluginFilterStore.setFilterState(context, state)
        assertEquals("one write", writes + 1, filters.commitCount)
        assertFalse(filters.contains("key_\$_plugin_center_filter_state"))
        assertEquals(1, filters.getInt("key_\$_plugin_center_filter_install_state", -1))
        assertEquals(state, PluginFilterStore.getFilterState(context))
        PluginFilterStore.clearAllFilterMemory(context)
        assertTrue(filters.all().isEmpty())
    }

    @Test
    fun hiddenPluginsKeepTheirTitlesAndSources() {
        val visibility = store(NamedStores.PLUGIN_CENTER_VISIBILITY_STATE)
        PluginVisibilityStore.hide(context, " org.example.b ", "  Beta ", PluginIndexSource.OFFICIAL)
        PluginVisibilityStore.hide(context, "org.example.a", null, null)
        assertEquals(setOf("org.example.a", "org.example.b"), visibility.getStringSet("key_\$_plugin_center_hidden_packages", null))
        assertEquals("Beta", visibility.getString("key_\$_plugin_center_hidden_title_org.example.b", null))
        assertEquals("OFFICIAL", visibility.getString("key_\$_plugin_center_hidden_source_org.example.b", null))
        assertFalse(visibility.contains("key_\$_plugin_center_hidden_title_org.example.a"))
        assertEquals(
            listOf(PluginVisibilityStore.HiddenPlugin("org.example.a", null, null), PluginVisibilityStore.HiddenPlugin("org.example.b", "Beta", PluginIndexSource.OFFICIAL)),
            PluginVisibilityStore.listHidden(context),
        )
        assertTrue(PluginVisibilityStore.isHidden(context, "org.example.b"))
        PluginVisibilityStore.show(context, "org.example.b")
        assertEquals(1, PluginVisibilityStore.hiddenCount(context))
        assertEquals(setOf("key_\$_plugin_center_hidden_packages"), visibility.all().keys)
    }

    @Test
    fun mountsAndUpdateChecksStayInTheDefaultPreferences() {
        PluginMountStore.setEnabled("org.example.mount", true)
        PluginMountStore.setObjectName("org.example.mount", "  helper ")
        PluginMountStore.setMountPoint("org.example.mount", "GLOBAL")
        assertEquals(setOf("org.example.mount"), store.getStringSet("key_\$_plugin_mount_packages", null))
        assertTrue(store.getBoolean("key_\$_plugin_mount_enabled_\$_org.example.mount", false))
        assertEquals("helper", store.getString("key_\$_plugin_mount_object_name_\$_org.example.mount", null))
        assertFalse("the global mount point is stored as missing", store.contains("key_\$_plugin_mount_point_\$_org.example.mount"))
        assertEquals(listOf(PluginMountStore.MountConfig("org.example.mount", true, "helper", null)), PluginMountStore.listEnabledConfigs())
        assertSame(PluginCenterKeys.MOUNT_ENABLED, SettingsRegistry.findFamily("key_\$_plugin_mount_enabled_\$_org.example.mount"))

        assertTrue(PluginUpdateCheckStore.isEnabled("org.example.mount"))
        assertFalse(PluginUpdateCheckStore.isEnabled("org.example.mount", defaultEnabled = false))
        PluginUpdateCheckStore.setEnabled("org.example.mount", false)
        assertFalse(store.getBoolean("key_\$_plugin_update_check_\$_org.example.mount", true))
        assertFalse(PluginUpdateCheckStore.isEnabled("org.example.mount"))
    }

    @Test
    fun variantPriorityKeepsItsJsonArrayText() {
        PluginVariantPriorityStore.setOrder(" OCR ", listOf(" a.b ", "c.d", "a.b", ""))
        assertEquals("[\"a.b\",\"c.d\"]", store.getString("key_\$_plugin_variant_priority_\$_ocr", null))
        assertEquals(listOf("a.b", "c.d"), PluginVariantPriorityStore.getOrder("ocr"))
        assertEquals(listOf("a.b", "c.d"), PluginVariantPriorityStore.getBestOrderForPackages(setOf("a.b", "c.d", "e.f")))
        // Unreadable text is no order, as before.
        store.put("key_\$_plugin_variant_priority_\$_ocr", "not json")
        assertEquals(emptyList<String>(), PluginVariantPriorityStore.getOrder("ocr"))
        PluginVariantPriorityStore.setOrder("ocr", emptyList())
        assertFalse(store.contains("key_\$_plugin_variant_priority_\$_ocr"))
    }

    @Test
    fun enableStateAndRecentTimes() {
        val enable = store(NamedStores.PLUGIN_CENTER_ENABLE_STATE)
        enable.put("key_\$_suspended_plugin_\$_org.example.p", true)
        assertTrue(PluginEnableStore.isSuspended(context, "org.example.p"))
        assertFalse(PluginEnableStore.isSuspended(context, "org.example.q"))
        assertEquals(listOf("key_\$_suspended_plugin_\$_org.example.p"), PluginCenterKeys.PLUGIN_SUSPENDED.storedNames())

        val recent = store(NamedStores.PLUGIN_CENTER_RECENT)
        assertNull(PluginRecentStore.getLastInstalled("org.example.p"))
        PluginRecentStore.setLastInstalled("org.example.p", 1234L)
        PluginRecentStore.setLastUninstalled("org.example.p", 0L)
        assertEquals(1234L, recent.getLong("key_\$_last_install_time_\$_org.example.p", 0L))
        assertEquals(1234L, PluginRecentStore.getLastInstalled("org.example.p"))
        assertNull("0 is unknown", PluginRecentStore.getLastUninstalled("org.example.p"))
    }
}
