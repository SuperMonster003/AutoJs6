package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.ScrollSearch.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrolling search (roadmap P4.1): container detection and selection, both scroll methods and
 * all four directions, end detection by refusal and by unchanged fingerprints, the step limit,
 * and the container vanishing.
 * zh-CN: 滚动查找 (Roadmap P4.1): 容器的探测与选择, 两种滚动方式与四个方向, 以拒绝与指纹无变化判定的末尾, 次数上限, 以及容器消失.
 */
class ScrollSearchTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    //          root
    //      /    |     \
    //  header  list   side (small scrollable)
    //          /  \
    //      item1  item2
    private val root = FakeUiNode.root {
        bounds(0, 0, 1080, 1920)
        child("header") { bounds(0, 0, 1080, 200) }
        child("list") {
            scrollable = true
            bounds(0, 200, 1080, 1920)
            child("item1") {
                text = "1"
                bounds(0, 200, 1080, 400)
            }
            child("item2") {
                text = "2"
                bounds(0, 400, 1080, 600)
            }
        }
        child("side") {
            scrollable = true
            bounds(0, 0, 100, 100)
        }
    }

    private val list = root.find("list")

    private val target = FakeUiNode.root("target")

    init {
        b.root = root
    }

    /** Every scroll moves the list content; the target shows up after [showAfter] scrolls; [canScroll] decides the action's result. zh-CN: 每次滚动都移动列表内容; 目标在 [showAfter] 次滚动后出现; [canScroll] 决定动作的结果. */
    private fun moving(showAfter: Int = Int.MAX_VALUE, canScroll: (Int) -> Boolean = { true }) {
        var scrolls = 0
        actions.scrollResult = { _, _ ->
            scrolls += 1
            list.descendantsAndSelf().drop(1).forEach {
                it.top -= 100
                it.bottom -= 100
            }
            if (scrolls >= showAfter) b.show("target", target)
            canScroll(scrolls)
        }
    }

    private fun scrollUntil(options: Options = Options(), root: FakeUiNode? = null) = kit.scrollSearch.scrollUntil("target", options, root)

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun presentTargetNeedsNoScroll() {
        b.show("target", target)
        val outcome = scrollUntil()
        assertSame(target, outcome.node)
        assertEquals(0, outcome.steps)
        assertSame(list, outcome.container)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun scrollsTheLargestScrollableUntilTheTargetAppears() {
        moving(showAfter = 2)
        val outcome = scrollUntil()
        assertSame(target, outcome.node)
        assertEquals(2, outcome.steps)
        assertSame(list, outcome.container)
        assertEquals(listOf("scroll(list, DOWN)", "scroll(list, DOWN)"), actions.calls)
        assertEquals(listOf(300L, 300L), rig.sleeps)
    }

    @Test
    fun usesTheGivenContainerAndDirection() {
        val other = FakeUiNode.root("other") { scrollable = true }
        b.show("other", other)
        actions.scrollResult = { _, _ ->
            b.show("target", target)
            true
        }
        val outcome = scrollUntil(Options(container = "other", direction = ScrollDirection.UP, settleMillis = 0))
        assertSame(other, outcome.container)
        assertEquals(listOf("scroll(other, UP)"), actions.calls)
        assertTrue(rig.sleeps.isEmpty())
    }

    @Test
    fun givenContainerIsWaitedForPerLocate() {
        val other = FakeUiNode.root("other") { scrollable = true }
        rig.appearing("other", other, from = 2)
        b.show("target", target)
        val outcome = scrollUntil(Options(container = "other", locate = Toolkit.Locate(timeout = 1000)))
        assertSame(other, outcome.container)
        assertEquals(listOf(50L), rig.sleeps)
    }

    @Test
    fun missingContainerIsInvalidTarget() {
        val e = error { scrollUntil(Options(container = "nope")) }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Toolkit.REASON_CONTAINER, e.reason)
        assertEquals("text(\"nope\")", e.selector)
        assertEquals(ScrollSearch.STEP, e.step)
    }

    @Test
    fun noScrollableForTheActionMethodIsInvalidTarget() {
        b.root = FakeUiNode.root("empty")
        val e = error { scrollUntil() }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Toolkit.REASON_NO_SCROLLABLE, e.reason)
    }

    @Test
    fun detectionAndTheSearchAreScopedToTheGivenRoot() {
        b.root = null
        // The target is the list's second item, but it is only "shown" after one scroll.
        actions.scrollResult = { _, _ ->
            b.show("target", list.find("item2"))
            true
        }
        val outcome = scrollUntil(root = root)
        assertSame(list, outcome.container)
        assertSame(list.find("item2"), outcome.node)
        assertEquals(1, outcome.steps)
        assertTrue(b.findLog.all { (_, scope) -> scope === root })

        val e = error { scrollUntil(root = root.find("header")) }
        assertEquals(Toolkit.REASON_NO_SCROLLABLE, e.reason)
    }

    @Test
    fun gestureScrollsSwipeAcrossTheContainer() {
        actions.swipeResult = {
            b.show("target", target)
            true
        }
        val outcome = scrollUntil(Options(method = ScrollMethod.GESTURE))
        assertEquals(1, outcome.steps)
        // The list spans y 200..1920: from 80 % (1576) to 20 % (544) of its height, at its centre x.
        assertEquals(listOf(listOf(540, 1576, 540, 544, 400)), actions.swipes)
        assertTrue(actions.calls.none { it.startsWith("scroll(") })
    }

    @Test
    fun gestureDirectionsMapToSwipes() {
        actions.swipeResult = {
            b.show("target", target)
            true
        }
        val swipes = ScrollDirection.entries.map { direction ->
            b.show("target", null)
            scrollUntil(Options(method = ScrollMethod.GESTURE, direction = direction, gestureDuration = 250))
            actions.swipes.last()
        }
        assertEquals(
            listOf(
                listOf(540, 544, 540, 1576, 250),
                listOf(540, 1576, 540, 544, 250),
                listOf(216, 1060, 864, 1060, 250),
                listOf(864, 1060, 216, 1060, 250),
            ),
            swipes,
        )
    }

    @Test
    fun gestureWithoutAnyAreaSwipesTheScreen() {
        b.root = null
        actions.swipeResult = {
            b.show("target", target)
            true
        }
        scrollUntil(Options(method = ScrollMethod.GESTURE))
        assertEquals(listOf(listOf(540, 1536, 540, 384, 400)), actions.swipes)
    }

    @Test
    fun gestureRefusedIsActionFailed() {
        actions.swipeResult = { false }
        val e = error { scrollUntil(Options(method = ScrollMethod.GESTURE)) }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(Scroller.REASON_SWIPE, e.reason)
    }

    @Test
    fun maxStepsTimesOut() {
        moving()
        val e = error { scrollUntil(Options(maxSteps = 3)) }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(ScrollSearch.REASON_MAX_STEPS, e.reason)
        assertEquals("text(\"target\")", e.selector)
        assertEquals(4, e.attempts)
        assertEquals(900, e.elapsed)
        assertEquals(3, actions.calls("scroll("))
    }

    @Test
    fun twoUnchangedScrollsMeanTheEnd() {
        val e = error { scrollUntil() }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(ScrollSearch.REASON_END, e.reason)
        assertEquals(2, actions.calls("scroll("))
        // Looked before each scroll and once more after the last.
        assertEquals(3, e.attempts)
        assertEquals(600, e.elapsed)
    }

    @Test
    fun oneUnchangedScrollIsNotYetTheEnd() {
        var scrolls = 0
        actions.scrollResult = { _, _ ->
            scrolls += 1
            if (scrolls != 1) {
                list.find("item1").top -= 100
            }
            if (scrolls == 3) b.show("target", target)
            true
        }
        val outcome = scrollUntil()
        assertEquals(3, outcome.steps)
    }

    @Test
    fun aRefusedScrollActionMeansTheEnd() {
        actions.scrollResult = { _, _ -> false }
        val e = error { scrollUntil() }
        assertEquals(ScrollSearch.REASON_END, e.reason)
        assertEquals(1, actions.calls("scroll("))
        assertEquals(2, e.attempts)
    }

    @Test
    fun aTargetAppearingWithTheLastScrollIsStillFound() {
        actions.scrollResult = { _, _ ->
            b.show("target", target)
            false
        }
        val outcome = scrollUntil()
        assertSame(target, outcome.node)
        assertEquals(1, outcome.steps)
    }

    @Test
    fun stopAtEndOffKeepsScrollingUntilMaxSteps() {
        actions.scrollResult = { _, _ -> false }
        val e = error { scrollUntil(Options(stopAtEnd = false, maxSteps = 3)) }
        assertEquals(ScrollSearch.REASON_MAX_STEPS, e.reason)
        assertEquals(3, actions.calls("scroll("))
    }

    @Test
    fun aVanishedContainerIsInvalidTarget() {
        actions.scrollResult = { _, _ ->
            b.gone += list
            true
        }
        val e = error { scrollUntil() }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(Scroller.REASON_CONTAINER_GONE, e.reason)
        assertEquals(1, e.attempts)
    }

    @Test
    fun humanizedScrollsPauseBeforeEachScroll() {
        moving(showAfter = 2)
        scrollUntil(Options(humanize = Humanize(minDelay = 20, maxDelay = 20)))
        assertEquals(listOf(20L, 300L, 20L, 300L), rig.sleeps)
    }

    @Test
    fun optionsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { Options(maxSteps = -1) }
        assertThrows(IllegalArgumentException::class.java) { Options(settleMillis = -1) }
        assertThrows(IllegalArgumentException::class.java) { Options(gestureDuration = 0) }
    }

}
