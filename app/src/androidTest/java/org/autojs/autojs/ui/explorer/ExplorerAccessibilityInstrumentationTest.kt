package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ExplorerAccessibilityInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @Test
    fun fiveSelectionActionsRemainUsableAcrossWidthsFontScaleLandscapeAndRtl() {
        val specifications = listOf(
            LayoutSpecification(320, 568, 1.5F, Locale.ENGLISH),
            LayoutSpecification(360, 640, 1.5F, Locale.SIMPLIFIED_CHINESE),
            LayoutSpecification(600, 960, 1.5F, Locale.ENGLISH),
            LayoutSpecification(568, 320, 1.5F, Locale.ENGLISH),
            LayoutSpecification(640, 360, 1.5F, Locale("ar")),
        )

        specifications.forEach { specification ->
            onMain {
                val context = configuredContext(specification)
                val explorer = ExplorerView(context)
                explorer.layoutDirection = if (isRtl(specification.locale)) {
                    View.LAYOUT_DIRECTION_RTL
                } else {
                    View.LAYOUT_DIRECTION_LTR
                }
                explorer.findViewById<View>(R.id.explorer_item_list).visibility = View.GONE
                val toolbar = explorer.findViewById<View>(R.id.selection_toolbar)
                val actionsContainer = explorer.findViewById<View>(R.id.selection_actions)
                val plugin = explorer.findViewById<View>(R.id.selection_plugin)
                val count = explorer.findViewById<TextView>(R.id.selection_count)
                plugin.visibility = View.VISIBLE
                plugin.contentDescription = "Compress..."
                explorer.findViewById<TextView>(R.id.selection_plugin_text).text = "Compress..."
                count.text = "128 selected"
                toolbar.visibility = View.VISIBLE
                measureAndLayout(explorer, specification.widthDp, specification.heightDp, context)

                val actions = selectionActions(explorer)
                assertEquals(5, actions.size)
                actions.forEach { action ->
                    assertTouchTarget(context, action)
                    assertTrue(action.contentDescription?.isNotBlank() == true)
                    val node = AccessibilityNodeInfoCompat.obtain()
                    action.onInitializeAccessibilityNodeInfo(node.unwrap())
                    assertEquals(Button::class.java.name, node.className)
                    node.recycle()
                }
                assertFalse(actions.zipWithNext().any { (first, second) -> first.right > second.left } && !isRtl(specification.locale))
                if (isRtl(specification.locale)) {
                    assertTrue(actions.zipWithNext().all { (first, second) -> first.left > second.left })
                } else {
                    assertTrue(actions.zipWithNext().all { (first, second) -> first.left < second.left })
                }
                assertEquals(
                    if (isRtl(specification.locale)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    ViewCompat.getLayoutDirection(actionsContainer),
                )

                selectionLabels(explorer).forEach { label ->
                    val textLayout = checkNotNull(label.layout)
                    assertTrue(textLayout.lineCount in 1..2)
                    assertTrue(textLayout.height <= label.height - label.paddingTop - label.paddingBottom)
                    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, label.importantForAccessibility)
                }
                assertTrue(ViewCompat.isAccessibilityHeading(count))
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, count.accessibilityLiveRegion)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, toolbar.importantForAccessibility)
            }
        }
    }

    @Test
    fun explorerPathRowsAndItemActionsExposeFortyEightDpTargetsAndLabels() = onMain {
        val specification = LayoutSpecification(320, 568, 1.5F, Locale.ENGLISH)
        val context = configuredContext(specification)

        val explorer = ExplorerView(context)
        explorer.findViewById<View>(R.id.explorer_item_list).visibility = View.GONE
        explorer.findViewById<View>(R.id.archive_filename_encoding).visibility = View.VISIBLE
        explorer.findViewById<View>(R.id.archive_extract_current).visibility = View.VISIBLE
        measureAndLayout(explorer, specification.widthDp, specification.heightDp, context)
        assertTargets(
            context,
            explorer,
            R.id.archive_filename_encoding,
            R.id.archive_extract_current,
        )

        val inflater = LayoutInflater.from(context)
        val file = inflater.inflate(R.layout.explorer_file, null, false)
        file.findViewById<View>(R.id.primary_viewer).visibility = View.VISIBLE
        file.findViewById<View>(R.id.edit).visibility = View.VISIBLE
        measureAndLayout(file, specification.widthDp, 160, context)
        assertTargets(context, file, R.id.item, R.id.primary_viewer, R.id.edit, R.id.more)

        val directory = inflater.inflate(R.layout.explorer_directory, null, false)
        measureAndLayout(directory, specification.widthDp, 96, context)
        assertTargets(context, directory, R.id.item, R.id.more)

        val category = inflater.inflate(R.layout.explorer_category, null, false)
        category.findViewById<View>(R.id.title_container).contentDescription = "Folders [2]"
        measureAndLayout(category, specification.widthDp, 48, context)
        assertTargets(
            context,
            category,
            R.id.title_container,
            R.id.go_up,
            R.id.sort_order,
            R.id.sort_type,
        )
        listOf(R.id.go_up, R.id.sort_order, R.id.sort_type).forEach { id ->
            assertTrue(category.findViewById<View>(id).contentDescription?.isNotBlank() == true)
        }
    }

    private fun selectionActions(root: View): List<View> = listOf(
        R.id.selection_close,
        R.id.selection_copy,
        R.id.selection_move,
        R.id.selection_plugin,
        R.id.selection_delete,
    ).map(root::findViewById)

    private fun selectionLabels(root: View): List<TextView> = listOf(
        R.id.selection_close_text,
        R.id.selection_copy_text,
        R.id.selection_move_text,
        R.id.selection_plugin_text,
        R.id.selection_delete_text,
    ).map(root::findViewById)

    private fun assertTargets(context: Context, root: View, vararg ids: Int) {
        ids.forEach { id ->
            val target = root.findViewById<View>(id)
            assertTouchTarget(context, target)
            if (target !== root.findViewById<View>(R.id.item)) {
                assertTrue("${resourceName(id)} needs an accessibility label", target.contentDescription?.isNotBlank() == true)
            }
        }
    }

    private fun assertTouchTarget(context: Context, target: View) {
        val minimum = dp(context, 48)
        assertTrue("${resourceName(target.id)} width was ${target.width}px", target.width >= minimum)
        assertTrue("${resourceName(target.id)} height was ${target.height}px", target.height >= minimum)
    }

    private fun configuredContext(specification: LayoutSpecification): Context {
        val configuration = Configuration(targetContext.resources.configuration).apply {
            fontScale = specification.fontScale
            screenWidthDp = specification.widthDp
            screenHeightDp = specification.heightDp
            smallestScreenWidthDp = minOf(specification.widthDp, specification.heightDp)
            orientation = if (specification.widthDp > specification.heightDp) {
                Configuration.ORIENTATION_LANDSCAPE
            } else {
                Configuration.ORIENTATION_PORTRAIT
            }
            setLocale(specification.locale)
            setLayoutDirection(specification.locale)
        }
        return ContextThemeWrapper(
            targetContext.createConfigurationContext(configuration),
            R.style.AppTheme,
        )
    }

    private fun measureAndLayout(
        view: View,
        widthDp: Int,
        heightDp: Int,
        context: Context,
    ) {
        val width = dp(context, widthDp)
        val height = dp(context, heightDp)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun resourceName(id: Int): String = runCatching {
        targetContext.resources.getResourceEntryName(id)
    }.getOrDefault(id.toString())

    private fun isRtl(locale: Locale): Boolean =
        TextUtilsCompat.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL

    private fun <T> onMain(block: () -> T): T {
        val outcome = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { outcome.set(runCatching(block)) }
        return requireNotNull(outcome.get()).getOrThrow()
    }

    private data class LayoutSpecification(
        val widthDp: Int,
        val heightDp: Int,
        val fontScale: Float,
        val locale: Locale,
    )
}
