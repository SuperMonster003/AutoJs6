package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.ui.settings.transfer.SettingsCategoryChoice
import org.autojs.autojs.ui.settings.transfer.SettingsCategoryChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P8.1: "Restore default settings..." reuses the import planner (an empty category file removes
 * every stored name of the category, D8), clears a named store owned by the selected categories as a whole (D11) and
 * never touches `other`, the data categories or the categories that are not selected.
 *
 * zh-CN: ROADMAP P8.1: "恢复默认设置..." 复用导入计划器 (空分类文件删除该分类全部已存储名称, D8), 整份清除属于所选分类的命名存储 (D11),
 * 不处理 `other`, 数据分类与未勾选的分类.
 */
class SettingsResetPlannerTest : DomainKeysTestBase() {

    @Test
    fun aResetRemovesEveryStoredNameOfTheSelectedCategories() {
        store.edit {
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 5)
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(EditorKeys.EDITOR_TEXT_SIZE.name, 19)
        }
        MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.set(listOf("appearance"))
        CredentialKeys.GITHUB_TOKEN.set("token")
        val enableState = store(NamedStores.PLUGIN_CENTER_ENABLE_STATE)
        enableState.edit {
            put(PluginCenterKeys.PLUGIN_ENABLED.nameOf("org.example.plugin"), false)
            put("legacy_entry_of_an_older_version", 1)
        }

        val plan = SettingsResetPlanner.plan(listOf("appearance", "editor_appearance", "plugin_enable_state", "other", "data_shortcuts"))
        assertEquals(listOf("appearance", "editor_appearance", "plugin_enable_state"), plan.categories)
        assertTrue(plan.puts.isEmpty() && plan.skips.isEmpty() && plan.deployments.isEmpty())
        assertEquals(
            setOf(
                AppearanceKeys.THEME_COLOR_PRIMARY.name, AppearanceKeys.NIGHT_MODE_ENABLED.name, EditorKeys.EDITOR_TEXT_SIZE.name,
                PluginCenterKeys.PLUGIN_ENABLED.nameOf("org.example.plugin"), "legacy_entry_of_an_older_version",
            ),
            plan.removes.map { it.name }.toSet(),
        )
        // The declared keys are the import planner's removes of empty category files; the leftover is the named store's.
        val empty = plan.categories.map { SettingsSnapshotCodec.Decoded(SettingsSnapshot.Category(it, emptyMap()), emptyList()) }
        val imported = SettingsImportPlanner.plan(empty, plan.categories, ImportEnvironment(), withDeployments = false)
        assertEquals(imported.removes, plan.removes.take(imported.removes.size))
        assertEquals(listOf("legacy_entry_of_an_older_version"), plan.removes.drop(imported.removes.size).map { it.name })

        SettingsImportApplier.write(plan)
        assertTrue(store.all().keys.none { it == AppearanceKeys.THEME_COLOR_PRIMARY.name || it == EditorKeys.EDITOR_TEXT_SIZE.name })
        assertTrue(enableState.all().isEmpty())
        // `other`, credentials and the categories not selected keep their values.
        assertEquals(listOf("appearance"), MiscStateKeys.SETTINGS_EXPORT_CATEGORIES.storedOrNull())
        assertEquals("token", CredentialKeys.GITHUB_TOKEN.get())
    }

    @Test
    fun otherAndTheDataCategoriesCannotBeReset() {
        val resettable = SettingsResetPlanner.resettable()
        assertEquals(SettingCategory.ALL.filterNot { it.isData || it == SettingCategory.OTHER }, resettable)
        assertTrue(SettingCategory.CREDENTIALS in resettable)
        val rows = SettingsCategoryChoices.reset(mapOf("appearance" to 3, "other" to 7))
        assertEquals(SettingCategory.ALL, rows.map { it.category })
        assertEquals(resettable, SettingsCategoryChoices.selectable(rows))
        val other = rows.single { it.category == SettingCategory.OTHER }
        assertEquals(SettingsCategoryChoice.State.NOT_RESETTABLE, other.state)
        assertEquals(7, other.entries)
        assertEquals(3, rows.first().entries)
        assertTrue(rows.filter { it.category.isData }.all { it.state == SettingsCategoryChoice.State.NOT_RESETTABLE && it.entries == null })
        // Nothing selected, or only what cannot be reset: an empty plan.
        assertTrue(SettingsResetPlanner.plan(listOf("other")).let { it.categories.isEmpty() && it.removes.isEmpty() })
    }
}
