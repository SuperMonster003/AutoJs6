package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap P6.1 (D17): wall-clock figures for one evaluation over a synthetic 10,000-node tree,
 * one scenario per query mode plus a full filter profile and the candidate scan. The numbers are
 * printed (they land in the test report), never asserted: JVM timings only put the device
 * measurements in perspective, the budget itself (<= 50 ms on an API 33 mid-range phone) is
 * checked against the `InspectorSearchSession` evaluate log line on a device. The one assertion
 * is that every scenario still matches what it should, so the timing is of real work.
 *
 * zh-CN: Roadmap P6.1 (D17): 在 10,000 节点的合成树上记录单次求值的耗时, 每种查询模式一个场景, 外加完整筛选配置与候选扫描.
 * 数字只打印 (进入测试报告), 不断言: JVM 耗时只用于对照真机测量, 预算本身 (API 33 中端机 <= 50 ms) 以真机上
 * `InspectorSearchSession` 的 evaluate 日志行核对. 唯一的断言是各场景仍匹配到应有的节点, 以保证计时的是真实工作.
 */
class TreeMatchBenchmarkTest {

    private val screen = ScreenScale.Source.fixed(1080, 1920)

    /**
     * A 10,000-node tree shaped like a long scrolling page: a shallow chrome part and a list of
     * rows, each row a small subtree (container > text + button + image), with ids / texts /
     * clickable flags on a realistic share of the nodes and bounds laid out top to bottom.
     * zh-CN: 形似长滚动页面的 10,000 节点树: 浅层外壳加一个行列表, 每行是小子树 (容器 > 文本 + 按钮 + 图片), 节点按真实比例
     * 带 id / text / clickable, 边界自上而下排布.
     */
    private fun bigTree(target: Int = 10_000): FakeUiNode {
        var count = 0
        val root = FakeUiNode.root("root") {
            packageName = "com.example.bench"
            className = "android.widget.FrameLayout"
            bounds(0, 0, 1080, 1920)
            count++
        }
        var list: FakeUiNode? = null
        var chrome = root
        for (level in 1..4) {
            chrome = chrome.child("chrome$level") {
                packageName = "com.example.bench"
                className = if (level == 4) "androidx.recyclerview.widget.RecyclerView" else "android.widget.FrameLayout"
                id = "com.example.bench:id/chrome_$level"
                scrollable = level == 4
                bounds(0, 0, 1080, 1920)
                count++
            }
        }
        list = chrome
        var row = 0
        while (count < target) {
            val top = row * 120
            list.child("row$row") {
                packageName = "com.example.bench"
                className = "android.widget.LinearLayout"
                id = "com.example.bench:id/row"
                clickable = row % 2 == 0
                bounds(0, top, 1080, top + 120)
                count++
                child("title$row") {
                    packageName = "com.example.bench"
                    className = "android.widget.TextView"
                    id = "com.example.bench:id/title"
                    text = "Item $row"
                    bounds(16, top + 8, 800, top + 56)
                    count++
                }
                child("subtitle$row") {
                    packageName = "com.example.bench"
                    className = "android.widget.TextView"
                    id = "com.example.bench:id/subtitle"
                    text = if (row % 3 == 0) "Details of item $row" else ""
                    desc = if (row % 5 == 0) "item $row description" else null
                    bounds(16, top + 60, 800, top + 110)
                    count++
                }
                child("button$row", clickable = true) {
                    packageName = "com.example.bench"
                    className = if (row % 4 == 0) "android.widget.ImageButton" else "com.google.android.material.button.MaterialButton"
                    id = "com.example.bench:id/action"
                    text = if (row % 4 == 0) "" else "Open"
                    desc = if (row % 4 == 0) "More" else null
                    enabled = row % 7 != 0
                    actions = listOf("ACTION_CLICK", "ACTION_FOCUS")
                    bounds(820, top + 20, 1064, top + 100)
                    count++
                }
                child("icon$row") {
                    packageName = "com.example.bench"
                    className = "android.widget.ImageView"
                    bounds(0, top + 40, 16, top + 80)
                    count++
                }
            }
            row++
        }
        return root
    }

    private fun filtersOf(mode: InspectorQuery.Mode, text: String, regex: Boolean = false): List<Filter> =
        (InspectorQuery(mode, text, regex).compile() as InspectorQuery.Compiled.Filters).filters

    private fun <T> time(label: String, warmUp: Int = 3, runs: Int = 7, block: () -> T): T {
        repeat(warmUp) { block() }
        val samples = LongArray(runs)
        var result: T? = null
        for (i in 0 until runs) {
            val started = System.nanoTime()
            result = block()
            samples[i] = (System.nanoTime() - started) / 1_000
        }
        samples.sort()
        println(String.format("benchmark %-34s median %6.2f ms  min %6.2f ms  max %6.2f ms", label, samples[runs / 2] / 1000.0, samples[0] / 1000.0, samples[runs - 1] / 1000.0))
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun tenThousandNodeTreeTimings() {
        val root = bigTree()
        val total = root.descendantsAndSelf().size
        assertTrue("built $total nodes", total >= 10_000)
        println("benchmark tree: $total nodes, depth ${root.descendantsAndSelf().maxOf { it.depth() }}")

        val preOrder = time("preOrder") { InspectorNodes.preOrder(root) }
        assertEquals(total, preOrder.size)

        val keyword = time("keyword 'item 7'") { TreeMatch.evaluate(root, filtersOf(InspectorQuery.Mode.KEYWORD, "item 7")) }
        assertTrue(keyword.count > 0)

        val keywordRegex = time("keyword regex /item \\d*5$/i") { TreeMatch.evaluate(root, filtersOf(InspectorQuery.Mode.KEYWORD, "/item \\d*5$/i", regex = true)) }
        assertTrue(keywordRegex.count > 0)

        val field = time("field 'clickable minDepth=6 text~=Open'") { TreeMatch.evaluate(root, filtersOf(InspectorQuery.Mode.FIELD, "clickable minDepth=6 text~=Open")) }
        assertTrue(field.count > 0)

        val selector = time("selector textContains(\"Item\").clickable(false)") { TreeMatch.evaluate(root, filtersOf(InspectorQuery.Mode.SELECTOR, "textContains(\"Item\").clickable(false)")) }
        assertTrue(selector.count > 0)

        val profile = FilterProfile(
            "bench", "Bench",
            booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES, BooleanProperty.ENABLED to Tri.YES),
            requireText = true,
            classPresets = setOf(ClassPreset.BUTTON),
            depth = IntRangeSpec(3, 8),
            minWidth = 2, minHeight = 2,
            onScreenOnly = true,
            packages = setOf("com.example.bench"),
            actions = setOf("ACTION_CLICK"),
        ).toFilters(screen)
        val profiled = time("profile (${profile.size} filters)") { TreeMatch.evaluate(root, profile) }
        assertTrue(profiled.count > 0)

        val everything = time("keyword + profile") { TreeMatch.evaluate(root, filtersOf(InspectorQuery.Mode.KEYWORD, "open") + profile) }
        assertTrue(everything.count > 0)

        val candidates = time("TreeCandidates.collect") { TreeCandidates.collect(root) }
        assertEquals(total, candidates.nodeCount)
    }

}
