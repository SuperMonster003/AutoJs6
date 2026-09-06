package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.ListCollector.Options
import org.autojs.autojs.core.automator.toolkit.ListCollector.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * List collection (roadmap P4.1) over a fake list of six items showing three at a time: the
 * harvest across scrolls, deduplication, the stop conditions and the rendering of items.
 * zh-CN: 列表采集 (Roadmap P4.1), 基于一次显示三项的六项假列表: 跨滚动的采集, 去重, 停止条件与条目的呈现.
 */
class ListCollectorTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    private val root = FakeUiNode.root {
        bounds(0, 0, 1080, 1920)
        child("list") {
            scrollable = true
            bounds(0, 0, 1080, 1920)
            for (i in 1..6) {
                child("item$i") {
                    text = "$i"
                    bounds(0, (i - 1) * 300, 1080, i * 300)
                }
            }
        }
    }

    private val list = root.find("list")

    private val items = (1..6).map { root.find("item$it") }

    private var offset = 0

    private val window = 3

    init {
        b.root = root
        b.show("item") { items.subList(offset, minOf(offset + window, items.size)) }
        actions.scrollResult = { _, _ -> scrollBy(2) }
    }

    /** Scrolls the fake list by [n] items; `false` (nothing moves) once the last item is in view. zh-CN: 把假列表滚动 [n] 项; 最后一项已在视野内时为 `false` (没有移动). */
    private fun scrollBy(n: Int): Boolean {
        if (offset + window >= items.size) return false
        val moved = minOf(n, items.size - window - offset)
        offset += moved
        items.forEach {
            it.top -= moved * 300
            it.bottom -= moved * 300
        }
        return true
    }

    private fun collect(options: Options = Options(), container: Any? = null) = kit.listCollector.collect(container, "item", options)

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun collectsAcrossScrollsUntilTheEnd() {
        val outcome = collect()
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), outcome.items)
        assertEquals(3, outcome.steps)
        assertEquals(Stop.END, outcome.stop)
        assertTrue(outcome.reachedEnd)
        assertEquals(3, actions.calls("scroll(list, DOWN)"))
        assertEquals(listOf(300L, 300L, 300L), rig.sleeps)
    }

    @Test
    fun dedupeOffKeepsRepeatedItems() {
        val outcome = collect(Options(dedupe = false))
        assertEquals(listOf("1", "2", "3", "3", "4", "5", "4", "5", "6"), outcome.items)
    }

    @Test
    fun dedupeIsByContentNotPosition() {
        val twins = FakeUiNode.root("twins") {
            scrollable = true
            bounds(0, 0, 100, 100)
            child("x") {
                text = "dup"
                bounds(0, 0, 100, 50)
            }
            child("y") {
                text = "dup"
                bounds(0, 50, 100, 100)
            }
        }
        b.show("twins", twins)
        b.show("twin") { listOf(twins.find("x"), twins.find("y")) }
        actions.scrollResult = { _, _ -> false }
        assertEquals(listOf("dup"), kit.listCollector.collect("twins", "twin").items)
        assertEquals(listOf("dup", "dup"), kit.listCollector.collect("twins", "twin", Options(dedupe = false)).items)
    }

    @Test
    fun untilStopsAtTheAcceptedItem() {
        val outcome = collect(Options(until = { it == "4" }))
        assertEquals(listOf("1", "2", "3", "4"), outcome.items)
        assertEquals(Stop.UNTIL, outcome.stop)
        assertEquals(1, outcome.steps)
    }

    @Test
    fun maxItemsStops() {
        val outcome = collect(Options(maxItems = 2))
        assertEquals(listOf("1", "2"), outcome.items)
        assertEquals(Stop.MAX_ITEMS, outcome.stop)
        assertEquals(0, outcome.steps)
    }

    @Test
    fun maxStepsStops() {
        val outcome = collect(Options(maxSteps = 1))
        assertEquals(listOf("1", "2", "3", "4", "5"), outcome.items)
        assertEquals(Stop.MAX_STEPS, outcome.stop)
        assertEquals(1, outcome.steps)
    }

    @Test
    fun resultTypeHashYieldsTheNodes() {
        assertEquals(items, collect(Options(resultType = "#")).items)
    }

    @Test
    fun resultTypeIsRenderedByTheBindings() {
        assertEquals((1..6).map { "bounds(item$it)" }, collect(Options(resultType = "bounds")).items)
    }

    @Test
    fun unchangedContentIsTheEnd() {
        actions.scrollResult = { _, _ -> true }
        val outcome = collect()
        assertEquals(listOf("1", "2", "3"), outcome.items)
        assertEquals(2, outcome.steps)
        assertEquals(Stop.END, outcome.stop)
    }

    @Test
    fun itemsAreSearchedInsideTheContainer() {
        collect()
        assertTrue(b.findAllLog.isNotEmpty())
        assertTrue(b.findAllLog.all { (target, root) -> target == "item" && root === list })
    }

    @Test
    fun givenContainerIsUsed() {
        val other = FakeUiNode.root("other") { scrollable = true }
        b.show("other", other)
        actions.scrollResult = { _, _ -> false }
        val outcome = collect(container = "other")
        assertTrue(outcome.items.isEmpty())
        assertEquals(1, outcome.steps)
        assertEquals(listOf("scroll(other, DOWN)"), actions.calls)
        assertSame(other, b.findAllLog.single().second)
    }

    @Test
    fun missingContainerIsInvalidTarget() {
        val e = error { collect(container = "nope") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Toolkit.REASON_CONTAINER, e.reason)
        assertEquals(ListCollector.STEP, e.step)
    }

    @Test
    fun noScrollableIsInvalidTarget() {
        b.root = FakeUiNode.root("empty")
        val e = error { collect() }
        assertEquals(Toolkit.REASON_NO_SCROLLABLE, e.reason)
    }

    @Test
    fun gestureMethodSwipesTheContainer() {
        // A swipe is always dispatched; whether the content moved is only visible in the fingerprints.
        actions.swipeResult = {
            scrollBy(2)
            true
        }
        val outcome = collect(Options(method = ScrollMethod.GESTURE))
        assertEquals(listOf("1", "2", "3", "4", "5", "6"), outcome.items)
        // The third swipe moves nothing, the fourth confirms it: two unchanged swipes are the end.
        assertEquals(4, actions.swipes.size)
        assertEquals(Stop.END, outcome.stop)
    }

    @Test
    fun optionsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { Options(maxItems = 0) }
        assertThrows(IllegalArgumentException::class.java) { Options(maxSteps = -1) }
    }

}
