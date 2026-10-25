package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.DoubleMinFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.autojs.autojs.core.automator.filter.ToleranceFilter
import org.autojs.autojs.core.automator.search.BFS
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.automator.selector.SelectorRegistry.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SelectorRegistry] applied to a bridge-less [UiSelector]: overload resolution, coercion, and the
 * filters each property builds, evaluated over [FakeUiNode]; screen-relative properties are
 * evaluated against a fixed [ScreenScale] installed as the default for the duration of the test.
 * zh-CN: 在无 bridge 的 [UiSelector] 上施加 [SelectorRegistry]: 重载解析, 参数转换, 以及各属性构建的过滤器在 [FakeUiNode]
 * 上的求值; 屏幕相关属性以测试期间安装为默认值的固定 [ScreenScale] 求值.
 */
class SelectorRegistryTest {

    private val login = FakeUiNode.root("login") {
        text = "Login"
        desc = "Sign in"
        id = "com.example:id/login"
        className = "android.widget.Button"
        packageName = "com.example"
        clickable = true
        actions = listOf("ACTION_CLICK")
        bounds(10, 20, 110, 70)
        child("icon")
    }

    private fun selector() = UiSelector(null as AccessibilityBridge?)

    private fun filterOf(name: String, vararg args: Any?, lenient: Boolean = false): Filter {
        val selector = selector()
        assertEquals(name, Outcome.APPLIED, SelectorRegistry.apply(selector, name, args.toList(), lenient))
        return selector.selector.filters.single()
    }

    private fun outcome(name: String, vararg args: Any?, lenient: Boolean = false) = SelectorRegistry.apply(selector(), name, args.toList(), lenient)

    @Test
    fun stringPropertiesBuildTheMatchingFilters() {
        assertTrue(filterOf("text", "Login").filter(login))
        assertFalse(filterOf("text", "login").filter(login))
        assertTrue(filterOf("textStartsWith", "Log").filter(login))
        assertTrue(filterOf("textEndsWith", "gin").filter(login))
        assertTrue(filterOf("textContains", "ogi").filter(login))
        // A plain string is a literal substring (D15), not a pattern.
        assertFalse(filterOf("textContains", "/OGI/i").filter(login))
        assertFalse(filterOf("textContains", "L.g").filter(login))
        assertTrue(filterOf("textMatches", "L.*n").filter(login))
        assertTrue(filterOf("textMatch", "^L").filter(login))
        assertTrue(filterOf("desc", "Sign in").filter(login))
        assertTrue(filterOf("content", "Login").filter(login))
        assertTrue(filterOf("id", "login").filter(login))
        assertTrue(filterOf("idStartsWith", "log").filter(login))
        assertTrue(filterOf("className", "Button").filter(login))
        assertTrue(filterOf("packageName", "com.example").filter(login))
        assertTrue(filterOf("packageNameStartsWith", "com.").filter(login))
        // A ConsString-like CharSequence is accepted as a string.
        assertTrue(filterOf("text", StringBuilder("Login")).filter(login))
    }

    @Test
    fun booleanPropertiesDefaultToTrue() {
        assertTrue(filterOf("clickable").filter(login))
        assertFalse(filterOf("clickable", false).filter(login))
        assertTrue(filterOf("hasChildren").filter(login))
        assertTrue(filterOf("hasChildren", false).filter(login.find("icon")))
        assertTrue(filterOf("enabled", true).filter(login))
        assertTrue(filterOf("checked", false).filter(login))
    }

    @Test
    fun intPropertiesRoundJsNumbers() {
        assertTrue(filterOf("depth", 1).filter(login.find("icon")))
        assertTrue(filterOf("depth", 1.0).filter(login.find("icon")))
        assertTrue(filterOf("depth", 0.7).filter(login.find("icon")))
        assertTrue(filterOf("childCount", 1).filter(login))
        assertTrue(filterOf("minChildCount", 1).filter(login))
        assertTrue(filterOf("maxChildCount", 0).filter(login.find("icon")))
        assertTrue(filterOf("indexInParent", 0).filter(login.find("icon")))
    }

    @Test
    fun metricsOverloadsAreChosenByArgumentCount() {
        assertEquals("boundsLeft(10)", filterOf("boundsLeft", 10.0).toString())
        assertEquals("boundsLeft(0, 20)", filterOf("boundsLeft", 0.0, 20.0).toString())
        assertEquals("boundsMinLeft(10)", filterOf("boundsMinLeft", 10.0).toString())
        assertEquals("boundsMaxLeft(10)", filterOf("boundsMaxLeft", 10.0).toString())
        assertEquals("left(10)", filterOf("left", 10).toString())
        assertEquals("centerY(0, 100)", filterOf("centerY", 0.0, 100.0).toString())
        assertEquals("minWidth(10)", filterOf("minWidth", 10.0).toString())
        assertEquals("maxHeight(10)", filterOf("maxHeight", 10.0).toString())
        assertEquals("bounds(10, 20, 110, 70)", filterOf("bounds", 10.0, 20.0, 110.0, 70.0).toString())
        assertEquals("boundsInside(0, 0, 200, 200)", filterOf("boundsInside", 0, 0, 200, 200).toString())
        assertTrue(filterOf("boundsLeft", 10.0) is MetricsFilter)
        assertTrue(filterOf("bounds", 10.0, 20.0, 110.0, 70.0) is BoundsFilter)
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("bounds", 10.0, 20.0))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("boundsLeft", 1.0, 2.0, 3.0))
    }

    @Test
    fun screenPropertiesAcceptEveryDocumentedShape() {
        assertEquals("screenCenterX(true, 0.1)", filterOf("screenCenterX", true, 0.1).toString())
        assertEquals("screenCenterX(false)", filterOf("screenCenterX", false).toString())
        assertEquals("screenCenterX(0.2)", filterOf("screenCenterX", 0.2).toString())
        assertEquals("screenCenterY()", filterOf("screenCenterY").toString())
        assertEquals("screenCoverage(0.5)", filterOf("screenCoverage", 0.5).toString())
        assertEquals("screenCoverage()", filterOf("screenCoverage").toString())
        assertTrue(filterOf("screenCenterX") is ToleranceFilter)
        assertTrue(filterOf("screenCoverage") is DoubleMinFilter)
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("screenCenterX", "left"))
    }

    @Test
    fun screenRelativePropertiesEvaluateAgainstTheDefaultScreenScale() {
        val previous = ScreenScale.Source.default
        ScreenScale.Source.default = ScreenScale.Source.fixed(1080, 1920)
        try {
            // login: bounds (10, 20, 110, 70); a second node fills the screen.
            val full = FakeUiNode.root { bounds(0, 0, 1080, 1920) }
            assertTrue(filterOf("boundsLeft", 10.0).filter(login))
            assertTrue(filterOf("left", 0.0, 0.5).filter(login))
            assertTrue(filterOf("boundsRight", -1.0).filter(full))
            assertTrue(filterOf("width", -1.0).filter(full))
            assertTrue(filterOf("minWidth", 0.5).filter(full))
            assertFalse(filterOf("minWidth", 0.5).filter(login))
            assertTrue(filterOf("maxHeight", 50.0).filter(login))
            assertTrue(filterOf("boundsInside", 0, 0, -1, -1).filter(login))
            assertTrue(filterOf("boundsContains", 20, 30, 40, 50).filter(login))
            assertTrue(filterOf("bounds", 10, 20, 110, 70).filter(login))
            assertTrue(filterOf("screenCenterX").filter(full))
            assertFalse(filterOf("screenCenterX").filter(login))
            assertTrue(filterOf("screenCenterY", false).filter(login))
            assertTrue(filterOf("screenCoverage").filter(full))
            assertFalse(filterOf("screenCoverage").filter(login))
            assertTrue(filterOf("screenCoverage", 0.0).filter(login))
        } finally {
            ScreenScale.Source.default = previous
        }
    }

    @Test
    fun actionAndFilterProperties() {
        assertTrue(filterOf("action", "CLICK") is ActionFilter)
        assertTrue(filterOf("action", "CLICK", 16).filter(login))
        assertFalse(filterOf("action", "LONG_CLICK").filter(login))
        assertTrue(filterOf("action").filter(login))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("action", "CLICK", null))
        val supplier = BooleanFilter.BooleanSupplier { node -> node.text() == "Login" }
        assertTrue(filterOf("filter", supplier).filter(login))
        assertFalse(filterOf("filter", supplier).filter(login.find("icon")))
        assertEquals("filter()", filterOf("filter", supplier).toString())
    }

    @Test
    fun algorithmSwitchesTheSearchAlgorithm() {
        val selector = selector()
        assertSame(DFS, selector.searchAlgorithm)
        assertEquals(Outcome.APPLIED, SelectorRegistry.apply(selector, "algorithm", listOf("bfs")))
        assertSame(BFS, selector.searchAlgorithm)
        assertEquals(Outcome.APPLIED, SelectorRegistry.apply(selector, "algorithm", listOf("DFS")))
        assertSame(DFS, selector.searchAlgorithm)
        assertTrue(selector.selector.filters.isEmpty())
    }

    @Test
    fun lenientCoercionParsesStringsOnlyWhenAsked() {
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("depth", "1"))
        assertTrue(filterOf("depth", "1", lenient = true).filter(login.find("icon")))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("clickable", "true"))
        assertTrue(filterOf("clickable", "true", lenient = true).filter(login))
        assertTrue(filterOf("clickable", "yes", lenient = true).filter(login.find("icon")))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("left", "10"))
        assertTrue(filterOf("left", "10", lenient = true) is MetricsFilter)
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("depth", "x", lenient = true))
    }

    @Test
    fun unknownPropertiesAndWrongKindsAreReported() {
        assertEquals(Outcome.NO_SUCH_PROPERTY, outcome("nope", 1))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("text", 1))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("text"))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("text", "a", "b"))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("clickable", 1))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("depth", null))
        assertTrue(selector().selector.filters.isEmpty())
    }

    @Test
    fun deprecatedPropertiesPointAtTheirReplacements() {
        for (prefix in listOf("id", "text", "desc", "content", "className", "packageName")) {
            val entry = SelectorRegistry.entry("${prefix}Matches")
            assertNotNull(entry)
            assertEquals("${prefix}Match", entry!!.deprecatedFor)
            assertNotNull(SelectorRegistry.entry("${prefix}Match"))
            assertNull(SelectorRegistry.entry("${prefix}Match")!!.deprecatedFor)
        }
        assertEquals(6, SelectorRegistry.entries.count { it.isDeprecated })
    }

    @Test
    fun gettersReadTheSameValuesTheFiltersCompare() {
        assertEquals("Login", SelectorRegistry.entry("text")!!.getter!!(login))
        assertEquals("Sign in", SelectorRegistry.entry("descContains")!!.getter!!(login))
        assertEquals("com.example:id/login", SelectorRegistry.entry("id")!!.getter!!(login))
        assertEquals(10, SelectorRegistry.entry("boundsLeft")!!.getter!!(login))
        assertEquals(60, SelectorRegistry.entry("centerX")!!.getter!!(login))
        assertEquals(true, SelectorRegistry.entry("clickable")!!.getter!!(login))
        assertEquals(1, SelectorRegistry.entry("childCount")!!.getter!!(login))
        assertEquals(listOf("ACTION_CLICK"), SelectorRegistry.entry("action")!!.getter!!(login))
        assertNull(SelectorRegistry.entry("bounds")!!.getter)
        assertNull(SelectorRegistry.entry("filter")!!.getter)
    }

    @Test
    fun theTableHasTheExpectedSize() {
        // normalize (3 overloads) and prefer (1) since docs/selector/ROADMAP.md P3.3 / P3.4. zh-CN: 自 P3.3 / P3.4 起含 normalize (3 个重载) 与 prefer (1 个).
        assertEquals(131, SelectorRegistry.entries.size)
        assertEquals(200, SelectorRegistry.entries.sumOf { it.overloads.size })
    }

    @Test
    fun integerBoundsFilterInclusively() {
        // icon: depth 1, indexInParent 0; login: depth 0, childCount 1 (layout inspector roadmap D19).
        val icon = login.find("icon")
        assertTrue(filterOf("minDepth", 1).filter(icon))
        assertFalse(filterOf("minDepth", 2).filter(icon))
        assertTrue(filterOf("maxDepth", 1).filter(icon))
        assertFalse(filterOf("maxDepth", 0).filter(icon))
        assertTrue(filterOf("minIndexInParent", 0).filter(icon))
        assertFalse(filterOf("minIndexInParent", 1).filter(icon))
        assertTrue(filterOf("maxIndexInParent", 0).filter(icon))
        assertTrue(filterOf("minChildCount", 1).filter(login))
        assertFalse(filterOf("maxChildCount", 0).filter(login))
        assertEquals("minDepth(3)", filterOf("minDepth", 3).toString())
        assertEquals("maxIndexInParent(2)", filterOf("maxIndexInParent", 2.0).toString())
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("minDepth", "x", lenient = true))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("maxDepth", null))
        assertEquals(Outcome.NO_MATCHING_OVERLOAD, outcome("minIndexInParent"))
        assertEquals(1, SelectorRegistry.entry("minDepth")!!.getter!!(icon))
        assertEquals(0, SelectorRegistry.entry("maxIndexInParent")!!.getter!!(icon))
    }

}
