package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassStep
import org.autojs.autojs.core.automator.filter.Preference.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `prefer(...)` (docs/selector/ROADMAP.md D4, P3.4, appendix C Q3): each key alone and in combination over three candidates
 * found in the order a, b, c, ties keeping that order, the selector setting and its refusals, and the compass step `k`
 * passing over the invisible clickable ancestors under `visible`; the screen is 1080 x 1920.
 * zh-CN: `prefer(...)` (D4, P3.4, 附录 C Q3): 三个按 a, b, c 顺序找到的候选上每个键单独与组合的效果, 相同时保持该顺序, 选择器设置及其拒绝,
 * 以及 `visible` 下罗盘 `k` 步骤越过不可见的可点击祖先; 屏幕为 1080 x 1920.
 */
class PreferenceTest {

    private val screen = ScreenScale.Source.fixed(1080, 1920)

    private val root = FakeUiNode.root("root") {
        // Off the screen, clickable, at the top. zh-CN: 屏幕之外, 可点击, 最靠上.
        child("a", clickable = true) {
            text = "OK"
            visibleToUser = false
            bounds(0, 0, 100, 50)
        }
        // Near the centre, not clickable. zh-CN: 靠近中心, 不可点击.
        child("b") {
            text = "OK"
            bounds(490, 935, 590, 985)
        }
        // The largest, clickable but not enabled. zh-CN: 面积最大, 可点击但未启用.
        child("c", clickable = true) {
            text = "OK"
            enabled = false
            bounds(0, 1700, 1080, 1900)
        }
    }

    private val candidates = listOf(root.find("a"), root.find("b"), root.find("c"))

    private fun best(vararg keys: Key) = Preference(keys.toList(), screen).best(candidates)?.name

    @Test
    fun eachKeyAlone() {
        assertEquals("b", best(Key.VISIBLE))
        assertEquals("a", best(Key.CLICKABLE))
        assertEquals("a", best(Key.ENABLED))
        assertEquals("b", best(Key.CENTER))
        assertEquals("c", best(Key.AREA))
        assertEquals("a", best(Key.TOP))
    }

    @Test
    fun keysCompareOneAfterAnother() {
        assertEquals("c", best(Key.VISIBLE, Key.CLICKABLE))
        assertEquals("c", best(Key.CLICKABLE, Key.VISIBLE))
        assertEquals("b", best(Key.VISIBLE, Key.ENABLED))
        // a and b are both enabled and as large: the first found. zh-CN: a 与 b 都已启用且面积相同: 取先找到的.
        assertEquals("a", best(Key.ENABLED, Key.AREA))
        assertEquals("b", best(Key.ENABLED, Key.AREA, Key.VISIBLE))
        assertEquals("b", best(Key.VISIBLE, Key.TOP))
        assertNull(Preference(listOf(Key.VISIBLE), screen).best(emptyList<FakeUiNode?>()))
        assertSame(candidates[2], Preference(listOf(Key.TOP), screen).best(listOf(null, candidates[2])))
    }

    @Test
    fun theSelectorSetting() {
        val selector = UiSelector(null as AccessibilityBridge?).text("OK").prefer("visible", "center")
        val preference = selector.selector.preference!!
        assertEquals(listOf(Key.VISIBLE, Key.CENTER), preference.keys)
        assertTrue(preference.visibleOnly)
        assertEquals("text(\"OK\").prefer(\"visible\", \"center\")", selector.toString())
        // The setting passes every node; the last one applies. zh-CN: 该设置接受每个节点; 最后一个生效.
        assertEquals(listOf("a", "b", "c"), selector.searchIn(root).map { it.name })
        assertEquals(listOf(Key.AREA), UiSelector(null as AccessibilityBridge?).prefer("top").prefer("area").selector.preference!!.keys)
        assertNull(UiSelector(null as AccessibilityBridge?).text("OK").selector.preference)
        val none = assertThrows(IllegalArgumentException::class.java) { UiSelector(null as AccessibilityBridge?).prefer() }
        assertEquals("prefer needs at least one key (keys: \"visible\", \"clickable\", \"enabled\", \"center\", \"area\", \"top\")", none.message)
        val unknown = assertThrows(IllegalArgumentException::class.java) { UiSelector(null as AccessibilityBridge?).prefer("visible", "left") }
        assertEquals("Unknown key of prefer: \"left\" (keys: \"visible\", \"clickable\", \"enabled\", \"center\", \"area\", \"top\")", unknown.message)
    }

    @Test
    fun theStepKPassesOverInvisibleClickableAncestorsUnderVisible() {
        val tree = FakeUiNode.root("outer", clickable = true) {
            bounds(0, 0, 1080, 1920)
            child("inner", clickable = true) {
                visibleToUser = false
                child("leaf")
            }
        }
        val leaf = tree.find("leaf")
        assertEquals("inner", Compass.navigate(leaf, listOf(CompassStep.Clickable(2)))?.name)
        assertEquals("outer", Compass.navigate(leaf, listOf(CompassStep.Clickable(2)), visibleOnly = true)?.name)
        assertNull(Compass.navigate(leaf, listOf(CompassStep.Clickable(1)), visibleOnly = true))
    }

}
