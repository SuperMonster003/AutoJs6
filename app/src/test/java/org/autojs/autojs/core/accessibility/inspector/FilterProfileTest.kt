package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap P3.1: every filter dimension of [FilterProfile] over a small tree, the AND composition,
 * and the selector rendering of the produced filters.
 * zh-CN: Roadmap P3.1: [FilterProfile] 的每个筛选维度在小树上的语义, AND 组合, 以及所产过滤器的选择器渲染.
 */
class FilterProfileTest {

    private val screen = ScreenScale.Source.fixed(1080, 1920)

    private val root = FakeUiNode.root("root") {
        className = "android.widget.FrameLayout"
        packageName = "com.a"
        bounds(0, 0, 1080, 1920)
        child("btn", clickable = true) {
            className = "android.widget.Button"
            packageName = "com.a"
            text = "OK"
            id = "com.a:id/ok"
            bounds(0, 0, 200, 100)
            actions = listOf("ACTION_CLICK")
        }
        child("txt") {
            className = "android.widget.TextView"
            packageName = "com.a"
            text = "Hello"
            desc = "greeting"
            enabled = false
            bounds(0, 100, 1080, 200)
        }
        child("off") {
            className = "android.widget.ImageView"
            packageName = "com.a"
            visibleToUser = false
            bounds(2000, 2000, 2100, 2100)
        }
        child("list") {
            className = "androidx.recyclerview.widget.RecyclerView"
            packageName = "com.b"
            scrollable = true
            bounds(0, 200, 1080, 1920)
            actions = listOf("ACTION_SCROLL_FORWARD")
            child("item") {
                className = "android.widget.LinearLayout"
                packageName = "com.b"
                bounds(0, 200, 1080, 300)
            }
        }
    }

    private fun matches(profile: FilterProfile): List<String> = TreeMatch.evaluate(root, profile.toFilters(screen)).matches.map { it.name }

    private fun profile(
        booleans: Map<BooleanProperty, Tri> = emptyMap(),
        requireId: Boolean = false,
        requireText: Boolean = false,
        requireDesc: Boolean = false,
        classPresets: Set<ClassPreset> = emptySet(),
        depth: IntRangeSpec = IntRangeSpec.ANY,
        childCount: IntRangeSpec = IntRangeSpec.ANY,
        indexInParent: IntRangeSpec = IntRangeSpec.ANY,
        minWidth: Int? = null,
        minHeight: Int? = null,
        onScreenOnly: Boolean = false,
        packages: Set<String> = emptySet(),
        actions: Set<String> = emptySet(),
    ) = FilterProfile("t", "Test", booleans, requireId, requireText, requireDesc, classPresets, depth, childCount, indexInParent, minWidth, minHeight, onScreenOnly, packages, actions)

    @Test
    fun defaultAndEmptyProfilesFilterNothing() {
        assertTrue(FilterProfile.DEFAULT.isDefault)
        assertTrue(FilterProfile.DEFAULT.isEmpty)
        assertEquals(0, FilterProfile.DEFAULT.conditionCount)
        assertTrue(profile(booleans = mapOf(BooleanProperty.CLICKABLE to Tri.ANY)).isEmpty)
        assertTrue(TreeMatch.evaluate(root, profile().toFilters(screen)).isUnfiltered)
    }

    @Test
    fun booleanTriState() {
        assertEquals(listOf("btn"), matches(profile(booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES))))
        assertEquals(listOf("txt"), matches(profile(booleans = mapOf(BooleanProperty.ENABLED to Tri.NO))))
        assertEquals(listOf("list"), matches(profile(booleans = mapOf(BooleanProperty.SCROLLABLE to Tri.YES, BooleanProperty.VISIBLE_TO_USER to Tri.YES))))
    }

    @Test
    fun hasValueShortcuts() {
        assertEquals(listOf("btn", "txt"), matches(profile(requireText = true)))
        assertEquals(listOf("btn"), matches(profile(requireId = true)))
        assertEquals(listOf("txt"), matches(profile(requireDesc = true)))
    }

    @Test
    fun classPresetsAreOrEd() {
        assertEquals(listOf("btn", "off"), matches(profile(classPresets = setOf(ClassPreset.BUTTON, ClassPreset.IMAGE))))
        assertEquals(listOf("root", "item"), matches(profile(classPresets = setOf(ClassPreset.CONTAINER))))
    }

    @Test
    fun integerRanges() {
        assertEquals(listOf("btn", "txt", "off", "list"), matches(profile(depth = IntRangeSpec(1, 1))))
        assertEquals(listOf("root", "list"), matches(profile(childCount = IntRangeSpec(min = 1))))
        assertEquals(listOf("list"), matches(profile(indexInParent = IntRangeSpec(min = 3))))
        assertEquals(listOf("item"), matches(profile(childCount = IntRangeSpec(max = 0), classPresets = setOf(ClassPreset.CONTAINER))))
    }

    @Test
    fun geometry() {
        assertEquals(listOf("root", "txt", "list", "item"), matches(profile(minWidth = 500)))
        assertEquals(listOf("root", "list"), matches(profile(minHeight = 1000)))
        assertEquals(listOf("root", "btn", "txt", "list", "item"), matches(profile(onScreenOnly = true)))
    }

    @Test
    fun packagesAndActions() {
        assertEquals(listOf("list", "item"), matches(profile(packages = setOf("com.b"))))
        assertEquals(listOf("root", "btn", "txt", "off", "list", "item"), matches(profile(packages = setOf("com.a", "com.b"))))
        assertEquals(listOf("btn"), matches(profile(actions = setOf("ACTION_CLICK"))))
        assertEquals(emptyList<String>(), matches(profile(actions = setOf("ACTION_CLICK", "ACTION_SCROLL_FORWARD"))))
    }

    @Test
    fun dimensionsCombineWithAnd() {
        val p = profile(booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES), classPresets = setOf(ClassPreset.BUTTON), requireText = true, onScreenOnly = true)
        assertEquals(listOf("btn"), matches(p))
        assertEquals(4, p.conditionCount)
        assertFalse(p.isEmpty)
    }

    @Test
    fun filtersRenderAsSelectorFragmentsOrComments() {
        val p = profile(
            booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES, BooleanProperty.ENABLED to Tri.NO),
            requireText = true,
            classPresets = setOf(ClassPreset.BUTTON),
            depth = IntRangeSpec(2, 5),
            minWidth = 3,
            onScreenOnly = true,
            packages = setOf("com.b"),
            actions = setOf("ACTION_CLICK"),
        )
        val rendered = p.toFilters(screen).map { it.toString() }
        assertEquals(
            listOf(
                "clickable(true)", "enabled(false)", "textMatch(\".\")",
                "classNameMatches(\"${ClassPreset.BUTTON.regex}\")",
                "minDepth(2)", "maxDepth(5)", "minWidth(3)", "/* onScreen */", "packageName(\"com.b\")",
                // ActionFilter renders names unquoted (script engine behavior); the P4.2 export must quote them.
                "action(ACTION_CLICK)",
            ),
            rendered,
        )
        val two = profile(packages = setOf("com.b", "com.a")).toFilters(screen).single().toString()
        assertEquals("/* packageName(\"com.a\") | packageName(\"com.b\") */", two)
    }

}
