package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerViewBinding
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
    fun sixSelectionActionsRemainUsableAcrossWidthsFontScaleLandscapeAndRtl() {
        val specifications = listOf(
            LayoutSpecification(320, 568, 1.5F, Locale.ENGLISH),
            LayoutSpecification(360, 640, 1.5F, Locale.SIMPLIFIED_CHINESE),
            LayoutSpecification(600, 960, 1.5F, Locale.ENGLISH),
            LayoutSpecification(568, 320, 1.5F, Locale.ENGLISH),
            LayoutSpecification(640, 360, 1.5F, Locale.forLanguageTag("ar")),
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
                val count = explorer.findViewById<TextView>(R.id.selection_count)
                val controller = renderSelectionToolbar(context, explorer)
                measureAndLayout(explorer, specification.widthDp, specification.heightDp, context)

                // "Exit", four primary buttons and "More" for the secondary "Copy path".
                // zh-CN: "退出", 四个主要操作按钮, 以及承载次要操作 "复制路径" 的 "更多".
                val actions = selectionActions(explorer, controller)
                assertEquals(6, actions.size)
                assertHeight(context, count, 32F)
                assertHeight(context, actionsContainer, 64F)
                actions.forEach { action ->
                    assertTouchTarget(context, action)
                    assertTrue(action.contentDescription?.isNotBlank() == true)
                    val node = AccessibilityNodeInfoCompat.obtain()
                    action.onInitializeAccessibilityNodeInfo(node.unwrap())
                    assertEquals(Button::class.java.name, node.className)
                    NodeLifecycle.default.release(node.unwrap())
                }
                assertFalse(actions.zipWithNext().any { (first, second) -> first.right > second.left } && !isRtl(specification.locale))
                if (isRtl(specification.locale)) {
                    assertTrue(actions.zipWithNext().all { (first, second) -> first.left > second.left })
                } else {
                    assertTrue(actions.zipWithNext().all { (first, second) -> first.left < second.left })
                }
                assertEquals(
                    if (isRtl(specification.locale)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    actionsContainer.layoutDirection,
                )

                selectionLabels(actions).forEach { label ->
                    val textLayout = checkNotNull(label.layout)
                    assertEquals(1, textLayout.lineCount)
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
    fun compactExplorerRowsRetainActionLabels() = onMain {
        val specification = LayoutSpecification(320, 568, 1F, Locale.ENGLISH)
        val context = configuredContext(specification)

        val explorer = ExplorerView(context)
        explorer.findViewById<View>(R.id.explorer_item_list).visibility = View.GONE
        assertEquals(
            View.GONE,
            explorer.findViewById<View>(R.id.archive_create_directory).visibility,
        )
        assertEquals(
            View.GONE,
            explorer.findViewById<View>(R.id.archive_add_entries).visibility,
        )
        explorer.findViewById<View>(R.id.archive_filename_encoding).visibility = View.VISIBLE
        explorer.findViewById<View>(R.id.archive_create_directory).visibility = View.VISIBLE
        explorer.findViewById<View>(R.id.archive_add_entries).visibility = View.VISIBLE
        explorer.findViewById<View>(R.id.archive_extract_current).visibility = View.VISIBLE
        measureAndLayout(explorer, specification.widthDp, specification.heightDp, context)
        assertHeight(context, explorer.findViewById(R.id.explorer_path_row), 33.5F)
        assertSizes(
            context,
            explorer,
            36F,
            33.5F,
            R.id.archive_filename_encoding,
            R.id.archive_create_directory,
            R.id.archive_add_entries,
            R.id.archive_extract_current,
        )

        val inflater = LayoutInflater.from(context)
        val file = inflater.inflate(R.layout.explorer_file, null, false)
        file.findViewById<View>(R.id.primary_viewer).visibility = View.VISIBLE
        file.findViewById<View>(R.id.edit).visibility = View.VISIBLE
        measureAndLayoutWrapHeight(file, specification.widthDp, 160, context)
        assertHeight(context, file.findViewById(R.id.action_icon_container), 36F)
        assertSizes(context, file, 36F, 36F, R.id.primary_viewer, R.id.edit, R.id.more)

        val directory = inflater.inflate(R.layout.explorer_directory, null, false)
        measureAndLayoutWrapHeight(directory, specification.widthDp, 96, context)
        assertSizes(context, directory, 52F, 20F, R.id.more)

        val category = inflater.inflate(R.layout.explorer_category, null, false)
        category.findViewById<View>(R.id.title_container).contentDescription = "Folders [2]"
        measureAndLayoutWrapHeight(category, specification.widthDp, 48, context)
        assertTrue(category.height < dp(context, 48F))
        assertWidths(
            context,
            category,
            32F,
            R.id.go_up,
            R.id.sort_order,
            R.id.sort_type,
        )
        assertTrue(category.findViewById<View>(R.id.title_container).contentDescription?.isNotBlank() == true)
        listOf(R.id.go_up, R.id.sort_order, R.id.sort_type).forEach { id ->
            assertTrue(category.findViewById<View>(id).contentDescription?.isNotBlank() == true)
        }
    }

    /** Renders the actions of a typical file selection with one plugin action through a toolbar controller. */
    private fun renderSelectionToolbar(context: Context, explorer: ExplorerView): ExplorerSelectionToolbarController {
        val root = explorer.findViewById<View>(R.id.selection_toolbar).parent as View
        val binding = ExplorerViewBinding.bind(root)
        val controller = ExplorerSelectionToolbarController(
            context = context,
            binding = binding,
            recyclerView = binding.explorerItemList,
            isSelectionActive = { true },
            setRefreshEnabled = {},
            onClose = {},
            onShownChanged = {},
        )
        fun primary(key: String, label: CharSequence, iconRes: Int) = ExplorerSelectionAction(
            key = key,
            label = label,
            iconRes = iconRes,
            level = ExplorerSelectionActionLevel.PRIMARY,
        ) {}
        controller.render(
            ExplorerSelectionToolbarState(
                selectedCount = 128,
                actions = listOf(
                    primary("copy", context.getString(R.string.text_copy_to), R.drawable.ic_content_copy_black_24dp),
                    primary("move", context.getString(R.string.text_move_to), R.drawable.ic_move_to),
                    primary("plugin", "Compress...", R.drawable.ic_archive_black_48dp),
                    primary("delete", context.getString(R.string.text_delete), R.drawable.ic_delete_black_24dp),
                    ExplorerSelectionAction(
                        key = "copy-path",
                        label = context.getString(R.string.text_copy_path),
                        iconRes = null,
                        level = ExplorerSelectionActionLevel.SECONDARY,
                    ) {},
                ),
            ),
        )
        return controller
    }

    private fun selectionActions(root: View, controller: ExplorerSelectionToolbarController): List<View> =
        listOf(root.findViewById<View>(R.id.selection_close)) +
            controller.actionButtonViews +
            root.findViewById<View>(R.id.selection_more)

    private fun selectionLabels(actions: List<View>): List<TextView> = actions.map { action ->
        (action as ViewGroup).children.filterIsInstance<TextView>().single()
    }

    private fun assertSizes(
        context: Context,
        root: View,
        widthDp: Float,
        heightDp: Float,
        vararg ids: Int,
    ) {
        ids.forEach { id ->
            val target = root.findViewById<View>(id)
            assertEquals("${resourceName(id)} width", dp(context, widthDp), target.width)
            assertEquals("${resourceName(id)} height", dp(context, heightDp), target.height)
            assertTrue("${resourceName(id)} needs an accessibility label", target.contentDescription?.isNotBlank() == true)
        }
    }

    private fun assertWidths(context: Context, root: View, widthDp: Float, vararg ids: Int) {
        ids.forEach { id ->
            val target = root.findViewById<View>(id)
            assertEquals("${resourceName(id)} width", dp(context, widthDp), target.width)
            assertTrue("${resourceName(id)} needs an accessibility label", target.contentDescription?.isNotBlank() == true)
        }
    }

    private fun assertHeight(context: Context, target: View, heightDp: Float) {
        assertEquals("${resourceName(target.id)} height", dp(context, heightDp), target.height)
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

    private fun measureAndLayoutWrapHeight(
        view: View,
        widthDp: Int,
        maximumHeightDp: Int,
        context: Context,
    ) {
        val width = dp(context, widthDp.toFloat())
        val maximumHeight = dp(context, maximumHeightDp.toFloat())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun dp(context: Context, value: Int): Int =
        dp(context, value.toFloat())

    private fun dp(context: Context, value: Float): Int =
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
