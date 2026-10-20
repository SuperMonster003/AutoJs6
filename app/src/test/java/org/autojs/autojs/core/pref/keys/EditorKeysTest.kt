package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs.ui.edit.editor.EditorDisplayPreferences
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EditorKeys] (docs/settings/ROADMAP.md P2.3): the eight editor categories, the text size default of appendix C Q10,
 * the threshold readers of the former `Pref` and the page-level wrappers (`EditorDisplayPreferences`,
 * `AceEditorLspPreferences`) reading through the keys.
 *
 * zh-CN: [EditorKeys] (ROADMAP P2.3): 编辑器的八个分类, 附录 C Q10 的字号默认值, 原 `Pref` 的阈值读取, 以及经键读写的页面级封装
 * (`EditorDisplayPreferences`, `AceEditorLspPreferences`).
 */
class EditorKeysTest : DomainKeysTestBase() {

    @Test
    fun categoriesHoldTheCountsOfAppendixA() {
        val counts = EditorKeys.keys.groupingBy { it.category!!.id }.eachCount()
        assertEquals(
            mapOf(
                "editor_general" to 2, "editor_appearance" to 17, "editor_log" to 6, "editor_assistance" to 8,
                "editor_smart_hint" to 7, "editor_gestures" to 2, "editor_gutter" to 5, "editor_performance" to 2,
            ),
            counts,
        )
        assertEquals(DeploymentKind.DOWNLOAD_EDITOR_FONT, EditorKeys.ACE_EDITOR_FONT.deployment)
        assertEquals("AceEditorFont.IOSEVKA.preferenceValue", EditorKeys.ACE_EDITOR_FONT.defaultText)
        assertEquals(SettingCategory.EDITOR_GENERAL, EditorKeys.EDITOR_ENGINE_SELECTION_MODE.category)
    }

    @Test
    fun textSizeDefaultsToTheSettingsPageValue() {
        // Appendix C Q10 (1): both editors start with R.integer.editor_text_size_default_value.
        assertEquals(14, EditorKeys.EDITOR_TEXT_SIZE.get())
        assertNull(EditorKeys.EDITOR_TEXT_SIZE.storedOrNull())
        EditorKeys.EDITOR_TEXT_SIZE.set(18)
        assertEquals(18, store.getInt(key("key_editor_text_size"), 0))
    }

    @Test
    fun thresholdsAreNormalizedAsBefore() {
        // Defaults (R.integer, 512 KiB) are used for values below -1; -1 means no threshold, 0 always.
        val default = 524288
        assertEquals(default, EditorKeys.codeHighlightThresholdChars)
        EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(-7)
        assertEquals(default, EditorKeys.codeHighlightThresholdChars)
        EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(EditorKeys.NO_THRESHOLD)
        assertTrue(EditorKeys.isCodeHighlightEnabledForLength(Int.MAX_VALUE))
        EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(0)
        assertFalse(EditorKeys.isCodeHighlightEnabledForLength(1))
        EditorKeys.EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES.set(100)
        assertTrue(EditorKeys.isCodeHighlightEnabledForLength(100))
        assertFalse(EditorKeys.isCodeHighlightEnabledForLength(101))

        assertEquals(default, EditorKeys.largeFileModeThresholdBytes)
        assertTrue(EditorKeys.shouldEnableLargeFileModeForSize(default + 1L))
        assertFalse(EditorKeys.shouldEnableLargeFileModeForSize(default.toLong()))
        EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(0)
        assertTrue(EditorKeys.shouldEnableLargeFileModeForSize(0))
        EditorKeys.EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES.set(EditorKeys.NO_THRESHOLD)
        assertFalse(EditorKeys.shouldEnableLargeFileModeForSize(Long.MAX_VALUE))
    }

    @Test
    fun displayPreferencesReadAndNormalizeThroughTheKeys() {
        assertFalse(EditorDisplayPreferences.isWordWrapEnabled())
        EditorDisplayPreferences.setWordWrapEnabled(true)
        assertTrue(store.getBoolean(key("key_ace_word_wrap_enabled"), false))
        assertEquals("continuation", EditorDisplayPreferences.wordWrapIndentStyle())
        store.put(key("key_editor_word_wrap_indent_style"), "removed")
        assertEquals("continuation", EditorDisplayPreferences.wordWrapIndentStyle())
        EditorDisplayPreferences.setWordWrapIndentStyle("classic")
        assertEquals("classic", EditorDisplayPreferences.wordWrapIndentStyle())
        assertEquals("dynamic", EditorDisplayPreferences.gutterWidthMode())
        store.put(key("key_editor_gutter_width_mode"), "removed")
        assertEquals("dynamic", EditorDisplayPreferences.gutterWidthMode())
        assertTrue(EditorDisplayPreferences.isLineNumbersEnabled())
        assertFalse(EditorDisplayPreferences.isPrintMarginEnabled())
        assertTrue(EditorDisplayPreferences.isBreakpointMarkersEnabled())
        assertTrue(EditorDisplayPreferences.isFoldMarkersEnabled())
        assertTrue(EditorDisplayPreferences.isFontLigaturesEnabled())
        assertTrue(EditorDisplayPreferences.isFontStylesEnabled())
    }

    @Test
    fun lspPreferencesUseTheKeysAndTheirFallbacks() {
        assertTrue(AceEditorLspPreferences.isEnabled())
        assertEquals(AceEditorLspPreferences.DEFAULT_FILE_TYPES, AceEditorLspPreferences.getFileTypes())
        assertEquals(AceEditorLspPreferences.DEFAULT_FILE_TYPES.joinToString(","), EditorKeys.ACE_LSP_FILE_TYPES.get())
        assertEquals(emptyList<String>(), AceEditorLspPreferences.getDeclarationGroups())
        AceEditorLspPreferences.setEnabled(false)
        assertFalse(store.getBoolean(key("key_ace_lsp_enabled"), true))
        AceEditorLspPreferences.resetEnabled()
        assertFalse(EditorKeys.ACE_LSP_ENABLED.isStored())
        assertEquals(false, EditorKeys.ACE_SEMANTIC_KOTLIN_ENABLED.get())
        assertEquals(true, EditorKeys.ACE_SEMANTIC_JAVA_ENABLED.get())
        assertEquals("key_\$_ace_semantic_kotlin_enabled", EditorKeys.ACE_SEMANTIC_KOTLIN_ENABLED.name)
    }

    @Test
    fun engineAndSelectionModeAreWrittenInOneEdit() {
        assertEquals(key("key_editor_engine_native"), EditorKeys.EDITOR_ENGINE.storedKeyOrDefault())
        assertNull(EditorKeys.EDITOR_ENGINE_SELECTION_MODE.storedOrNull())
        val commits = store.commitCount
        SettingsStores.default.edit {
            this[EditorKeys.EDITOR_ENGINE_SELECTION_MODE] = "auto_ace"
            this[EditorKeys.EDITOR_ENGINE] = key("key_editor_engine_ace")
        }
        assertEquals(commits + 1, store.commitCount)
        assertEquals("auto_ace", store.getString("key_\$_editor_engine_selection_mode", null))
        assertEquals("ace", EditorKeys.EDITOR_ENGINE.get())
        assertEquals(key("key_editor_log_button_behavior_bottom_sheet"), EditorKeys.EDITOR_LOG_BUTTON_BEHAVIOR.get())
        assertEquals("separate_light_dark", EditorKeys.EDITOR_THEME_MODE.get())
    }
}
