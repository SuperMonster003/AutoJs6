package org.autojs.autojs.core.pref.keys

import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontSortField
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontSortMode
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontUiPreferences
import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.ui.edit.keyboard.SymbolsConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3.2: the named stores of the editor keyboards and of the font dialog ([EditorKeyboardKeys]):
 * file and key names unchanged (R1), and the moved readers and writers reading and writing what they did (R2).
 *
 * zh-CN: ROADMAP P3.2: 编辑器键盘与字体对话框的命名存储: 文件名与键名不变 (R1), 迁移后的读写方读写的值与原先一致 (R2).
 */
class EditorKeyboardKeysTest : DomainKeysTestBase() {

    private val context: Context = ContextWrapper(null)

    @Test
    fun storesKindsAndCategories() {
        assertEquals(
            listOf("editor_symbols_config", "FunctionsKeyboardHelper", "ace_editor_font_ui_preferences", "ace_action_history"),
            listOf(NamedStores.EDITOR_SYMBOLS_CONFIG, NamedStores.FUNCTIONS_KEYBOARD_HELPER, NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES, NamedStores.ACE_ACTION_HISTORY).map { it.name },
        )
        val rows = SettingsRegistry.describe()
        assertEquals(setOf("editor_keyboard"), rows.filter { it.store == "editor_symbols_config" || it.store == "ace_editor_font_ui_preferences" }.map { it.category }.toSet())
        assertEquals(SettingKind.CACHE, EditorKeyboardKeys.SOFT_INPUT_HEIGHT.kind)
        assertNull(EditorKeyboardKeys.SOFT_INPUT_HEIGHT.category)
        assertEquals(400, EditorKeyboardKeys.SOFT_INPUT_HEIGHT.get())
        assertEquals(SettingCategory.OTHER, EditorKeyboardKeys.ACE_ACTION_HISTORY.category)
        assertTrue(EditorKeyboardKeys in SettingsRegistry.DOMAINS)
    }

    @Test
    fun symbolProfilesKeepTheirKeys() {
        val symbols = store(NamedStores.EDITOR_SYMBOLS_CONFIG)
        assertEquals(SymbolsConfigStore.PROFILE_DEFAULT_ID, SymbolsConfigStore.getActiveProfileName(context))
        SymbolsConfigStore.setActiveProfileName(context, "config-2")
        assertEquals("config-2", symbols.getString("active_profile", null))
        assertEquals("config-2", SymbolsConfigStore.getActiveProfileName(context))
        symbols.put("profiles_json", "{\"profiles\":{}}")
        assertEquals("{\"profiles\":{}}", EditorKeyboardKeys.SYMBOLS_PROFILES.storedText())
        assertEquals(emptyMap<String, Any?>(), EditorKeyboardKeys.SYMBOLS_PROFILES.get()["profiles"])
    }

    @Test
    fun fontDialogPreferences() {
        val fonts = store(NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES)
        assertEquals(AceEditorFontSortMode.DEFAULT, AceEditorFontUiPreferences.getSortMode(context))
        val other = AceEditorFontSortMode.entries.first { it != AceEditorFontSortMode.DEFAULT }
        AceEditorFontUiPreferences.setSortMode(context, other)
        assertEquals(other.name, fonts.getString("key_\$_ace_editor_font_sort_mode", null))
        AceEditorFontUiPreferences.setSortMode(context, AceEditorFontSortMode.DEFAULT)
        assertFalse("the default mode is stored as missing", fonts.contains("key_\$_ace_editor_font_sort_mode"))

        assertTrue(AceEditorFontUiPreferences.getTypeLocked(context, AceEditorFontSortField.NAME))
        assertFalse(AceEditorFontUiPreferences.getTypeLocked(context, AceEditorFontSortField.SIZE))
        AceEditorFontUiPreferences.setTypeLocked(context, AceEditorFontSortField.SIZE, true)
        assertTrue(fonts.getBoolean("key_\$_ace_editor_font_size_sort_type_locked", false))
        AceEditorFontUiPreferences.setTypeLocked(context, AceEditorFontSortField.SIZE, false)
        assertFalse(fonts.contains("key_\$_ace_editor_font_size_sort_type_locked"))

        val feature = FontFeature.entries.first()
        AceEditorFontUiPreferences.setExcludedFeatures(context, setOf(feature), excludeOther = true)
        assertEquals(setOf(feature.catalogValue, "other"), fonts.getStringSet("key_\$_ace_editor_font_preview_excluded_features", null))
        val filter = AceEditorFontUiPreferences.getPreviewFilter(context)
        assertEquals(setOf(feature), filter.excludedFeatures)
        assertTrue(filter.excludeOther)
        AceEditorFontUiPreferences.resetPreviewFilter(context)
        assertFalse(fonts.contains("key_\$_ace_editor_font_preview_excluded_features"))

        AceEditorFontUiPreferences.setSelectedVariantId(context, "iosevka", "iosevka_slab")
        assertEquals("iosevka_slab", fonts.getString("key_\$_ace_editor_font_selected_variant_iosevka", null))
        assertEquals("iosevka_slab", AceEditorFontUiPreferences.getSelectedVariantId(context, "iosevka"))
        assertEquals(listOf("key_\$_ace_editor_font_selected_variant_iosevka"), EditorKeyboardKeys.FONT_SELECTED_VARIANT.storedNames())
    }

    @Test
    fun aceActionHistoryIsOneJsonState() {
        val history = store(NamedStores.ACE_ACTION_HISTORY)
        AceActionHistoryStore.record(context, AceActionHistoryStore.Action("editor:undo", "Undo", mutatesText = true), now = 1000L)
        val text = history.getString("state_json", null)!!
        assertTrue(text.contains("\"editor:undo\""))
        assertEquals(1, (EditorKeyboardKeys.ACE_ACTION_HISTORY.get()["entries"] as List<*>).size)
        AceActionHistoryStore.clear(context)
        assertFalse(history.contains("state_json"))
    }
}
