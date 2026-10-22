package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P6.3: the selection rules of the category step (T10, D2, appendix C Q28).
 * zh-CN: ROADMAP P6.3: 分类步骤的勾选规则 (T10, D2, 附录 C Q28).
 */
class SettingsCategorySelectionTest {

    private val credentials = SettingCategory.CREDENTIALS.id

    @Test
    fun everyCategoryIsSelectableWithTheDataCategories() {
        // The data categories can be ticked since their detail pages (P10.2).
        val selectable = SettingsCategorySelection.selectable()
        assertEquals(SettingCategory.ALL, selectable)
        assertTrue(SettingCategory.DATA_SHORTCUTS in selectable && SettingCategory.DATA_COLOR_LIBRARY in selectable)
    }

    @Test
    fun startsWithTheDefaultsWhenNothingIsRemembered() {
        val initial = SettingsCategorySelection.initial(null)
        assertEquals(SettingCategory.ALL.filter { it.defaultSelected }.map { it.id }, initial)
        assertTrue("data_shortcuts" in initial && "data_color_library" in initial)
        assertFalse("developer" in initial)
        assertFalse(credentials in initial)
        assertFalse("other" in initial)
    }

    @Test
    fun startsWithTheRememberedSelectionInPageOrderWithoutCredentials() {
        val remembered = listOf("other", credentials, "appearance", "data_shortcuts", "no_such_category", "appearance", "developer")
        assertEquals(listOf("appearance", "developer", "data_shortcuts", "other"), SettingsCategorySelection.initial(remembered))
        assertEquals(emptyList<String>(), SettingsCategorySelection.initial(emptyList()))
    }

    @Test
    fun bulkActionsCoverTheSelectableCategories() {
        val all = SettingsCategorySelection.all()
        assertEquals(SettingCategory.ALL.map { it.id }, all)
        assertTrue(credentials in all)
        val inverted = SettingsCategorySelection.inverted(listOf("appearance", "other", "data_shortcuts"))
        assertEquals(all.size - 3, inverted.size)
        assertFalse("appearance" in inverted || "other" in inverted || "data_shortcuts" in inverted)
        assertEquals(all, SettingsCategorySelection.inverted(emptyList()))
        assertEquals(emptyList<String>(), SettingsCategorySelection.inverted(all))
    }

    @Test
    fun togglingKeepsPageOrder() {
        val selected = SettingsCategorySelection.toggled(listOf("explorer", "other"), "appearance")
        assertEquals(listOf("appearance", "explorer", "other"), selected)
        assertEquals(listOf("appearance", "other"), SettingsCategorySelection.toggled(selected, "explorer"))
        assertEquals(listOf("data_color_library", "other"), SettingsCategorySelection.toggled(listOf("other"), "data_color_library"))
        assertEquals(listOf("other"), SettingsCategorySelection.toggled(listOf("other"), "no_such_category"))
    }

    @Test
    fun credentialsAreWarnedAboutAndNeverRemembered() {
        assertTrue(SettingsCategorySelection.addsCredentials(listOf("appearance"), SettingsCategorySelection.all()))
        assertFalse(SettingsCategorySelection.addsCredentials(listOf(credentials), listOf(credentials, "appearance")))
        assertFalse(SettingsCategorySelection.addsCredentials(listOf(credentials), emptyList()))
        assertEquals(listOf("appearance", "other"), SettingsCategorySelection.toRemember(listOf("appearance", credentials, "other")))
    }
}
