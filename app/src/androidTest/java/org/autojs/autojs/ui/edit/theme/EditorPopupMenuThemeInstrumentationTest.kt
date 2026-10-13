package org.autojs.autojs.ui.edit.theme

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.appcompat.R as AppCompatR

/**
 * The options menus of editor dialogs are androidx PopupMenus created with EditorPopupMenuTheme
 * (EditorMaterialDialogThemeDelegate). PopupMenu reads popupMenuStyle, so the theme must point it to
 * the editor popup background; otherwise the menu kept the app background while its titles took the
 * editor foreground, and light text on a light menu was unreadable.
 *
 * zh-CN: 编辑器对话框的选项菜单是以 EditorPopupMenuTheme 创建的 androidx PopupMenu
 * (EditorMaterialDialogThemeDelegate). PopupMenu 读取 popupMenuStyle, 因此该主题必须让它使用编辑器弹窗背景;
 * 否则菜单沿用应用背景而标题使用编辑器前景色, 浅色文字落在浅色菜单上无法辨认.
 */
@RunWith(AndroidJUnit4::class)
class EditorPopupMenuThemeInstrumentationTest {

    @Test
    fun popupMenusOfTheEditorPopupThemeUseTheEditorPopupBackground() {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.EditorPopupMenuTheme,
        )
        val style = TypedValue()
        assertTrue(
            "EditorPopupMenuTheme does not define popupMenuStyle",
            context.theme.resolveAttribute(AppCompatR.attr.popupMenuStyle, style, true),
        )
        assertEquals(R.style.EditorPopupMenu, style.resourceId)

        val attributes = context.obtainStyledAttributes(style.resourceId, intArrayOf(android.R.attr.popupBackground))
        try {
            assertEquals(R.drawable.editor_popup_background, attributes.getResourceId(0, 0))
        } finally {
            attributes.recycle()
        }
    }
}
