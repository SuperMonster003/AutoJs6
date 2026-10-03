package org.autojs.autojs.ui.floating.layoutinspector

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorUiDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun context(night: Boolean = false, fontScale: Float = 1f): ContextThemeWrapper {
        val base = instrumentation.targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            this.fontScale = fontScale
        }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.AppTheme)
    }

    @Test
    fun themeContrastsOnLightDarkAndTranslucentSurfaces() {
        val primaries = listOf(Color.WHITE, Color.BLACK, Color.YELLOW, Color.CYAN, Color.GREEN,
            Color.BLUE, Color.RED, Color.MAGENTA, Color.GRAY, 0xFFFFEB3B.toInt(), 0xFF263238.toInt())
        for (night in listOf(false, true)) {
            val context = context(night)
            for (surfaceRes in listOf(R.color.window_background, R.color.layout_inspector_search_surface, R.color.layout_hierarchy_window_background)) {
                val surface = context.getColor(surfaceRes)
                for (primary in primaries) {
                    val colors = InspectorTheme(context, surface, primary)
                    for (underlay in listOf(Color.WHITE, Color.BLACK)) {
                        val background = ColorUtils.compositeColors(surface, underlay)
                        for (text in listOf(colors.text, colors.secondaryText, colors.accent)) contrast(text, background, 4.5)
                        contrast(colors.control, background, 3.0)
                    }
                    contrast(colors.selectionText, colors.selectionBackground, 4.5)
                    contrast(colors.selectionAccent, colors.selectionBackground, 4.5)
                    contrast(colors.matchText, colors.matchBackground, 4.5)
                    contrast(colors.onAccent, colors.accent, 4.5)
                }
            }
        }
    }

    @Test
    fun selectionKeepsRowGeometryIncludingWrappedValues() {
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f)) {
                val context = context(fontScale = scale)
                val view = NodeInfoView(context)
                view.setNodeInfo(NodeInfoSnapshot(
                    packageName = "org.autojs.inspector.long.package.name.for.wrapping",
                    fullId = "org.autojs.inspector:id/a_long_resource_name",
                    text = "First line\nSecond line",
                ).toNodeInfo())
                val width = (280 * context.resources.displayMetrics.density).toInt()
                val height = (640 * context.resources.displayMetrics.density).toInt()
                fun layout() {
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                }
                fun geometry(): List<Pair<Int, Rect>> = (0 until view.childCount).map { i ->
                    val child = view.getChildAt(i)
                    view.getChildAdapterPosition(child) to Rect(child.left, child.top, child.right, child.bottom)
                }
                layout()
                val before = geometry()
                val row = requireNotNull(view.findViewHolderForAdapterPosition(1)).itemView
                row.performLongClick()
                layout()
                assertTrue(view.isSelectionMode)
                assertEquals(1, view.checkedRows.size)
                assertEquals(before, geometry())
                assertTrue(row.isSelected)
                val name = row.findViewById<TextView>(R.id.name)
                val background = (row.background as android.graphics.drawable.ColorDrawable).color
                contrast(name.currentTextColor, background, 4.5)
                val second = requireNotNull(view.findViewHolderForAdapterPosition(3)).itemView
                second.performClick()
                layout()
                assertEquals(2, view.checkedRows.size)
                assertEquals(before, geometry())
                row.performClick()
                layout()
                assertEquals(1, view.checkedRows.size)
                second.performClick()
                layout()
                assertTrue("Only Back leaves selection mode", view.isSelectionMode)
                assertTrue(view.checkedRows.isEmpty())
                assertEquals(before, geometry())
                view.deselectAll()
                layout()
                assertFalse(view.isSelectionMode)
                assertEquals(before, geometry())
            }
        }
    }

    @Test
    fun copyModeDefaultsToAskAndSurvivesReload() {
        val key = R.string.key_layout_inspector_node_copy_mode
        val original = Pref.getStringOrNull(key)
        try {
            Pref.putString(key, null)
            assertEquals(NodeInfoDialog.CopyMode.ASK, NodeInfoDialog.CopyMode.load())
            for (mode in NodeInfoDialog.CopyMode.entries) {
                mode.save()
                assertEquals(mode, NodeInfoDialog.CopyMode.load())
            }
            Pref.putString(key, "unknown-future-format")
            assertEquals(NodeInfoDialog.CopyMode.ASK, NodeInfoDialog.CopyMode.load())
        } finally {
            Pref.putString(key, original)
        }
    }

    private fun contrast(foreground: Int, background: Int, minimum: Double) {
        val actual = ColorUtils.calculateContrast(foreground, background)
        assertTrue("${Integer.toHexString(foreground)} on ${Integer.toHexString(background)}: $actual < $minimum", actual >= minimum)
    }
}
