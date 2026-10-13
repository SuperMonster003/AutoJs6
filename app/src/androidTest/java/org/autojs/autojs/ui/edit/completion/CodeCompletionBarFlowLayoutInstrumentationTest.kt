package org.autojs.autojs.ui.edit.completion

import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.appcompat.R as AppCompatR

/**
 * The flow layout of the Ace symbol bar keeps only the items of its visible rows attached. Creating a view for each of
 * its 75 symbols took about 220 ms of the editor's first frame on a Helio G85 (manual test B18 of the Ace editor plugin).
 *
 * zh-CN: Ace 符号栏的流式布局只保留可见行中的项. 为 75 个符号逐一创建视图在 Helio G85 上约占编辑器首帧的 220 ms
 * (Ace 编辑器插件手测 B18).
 */
@RunWith(AndroidJUnit4::class)
class CodeCompletionBarFlowLayoutInstrumentationTest {

    @Test
    fun onlyTheVisibleRowsAreAttachedAndScrollingFillsTheRest() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, AppCompatR.style.Theme_AppCompat_Light)
            val metrics = context.resources.displayMetrics
            fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).toInt()

            val bar = CodeCompletionBar(context)
            bar.useFlowLayout()
            bar.setItemHeightDp(35)
            bar.setCodeCompletions(CodeCompletions.just(List(ITEM_COUNT) { "s$it" }))
            val width = dp(240f)
            val height = dp(70f)
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            bar.layout(0, 0, width, height)

            val first = attachedPositions(bar)
            assertTrue("only the visible rows are attached: ${first.size} of $ITEM_COUNT", first.size in 1 until ITEM_COUNT)
            assertEquals(0, first.minOrNull())
            assertTrue("the attached items lie in the bar", attachedChildrenInside(bar, height))

            bar.scrollBy(0, Int.MAX_VALUE / 2)
            val scrolled = attachedPositions(bar)
            assertTrue("scrolling to the end attaches the last item: $scrolled", ITEM_COUNT - 1 in scrolled)
            assertFalse("the first row was recycled: $scrolled", 0 in scrolled)
            assertTrue(scrolled.size < ITEM_COUNT)
            assertTrue("the attached items lie in the bar", attachedChildrenInside(bar, height))

            bar.scrollBy(0, -Int.MAX_VALUE / 2)
            assertEquals(first, attachedPositions(bar))
        }
    }

    private fun attachedPositions(bar: CodeCompletionBar): Set<Int> =
        (0 until bar.childCount).mapTo(sortedSetOf()) { bar.getChildAdapterPosition(bar.getChildAt(it)) }

    private fun attachedChildrenInside(bar: CodeCompletionBar, height: Int): Boolean =
        (0 until bar.childCount).map { bar.getChildAt(it) }.all { it.bottom > 0 && it.top < height }

    private companion object {
        const val ITEM_COUNT = 75
    }
}
