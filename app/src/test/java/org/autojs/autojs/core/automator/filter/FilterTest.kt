package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.regexp.NativeRegExp
import java.util.regex.PatternSyntaxException

/**
 * The selector filters evaluated over [FakeUiNode]: the truth table of every [StringMatcher] and
 * every numeric comparison, the argument rules of `id` / `className`, and the screen-relative
 * filters against an injected [ScreenScale] (1080 x 1920).
 * zh-CN: 在 [FakeUiNode] 上求值的选择器过滤器: 每种 [StringMatcher] 与每种数值比较的真值表, `id` / `className` 的参数规则,
 * 以及以注入的 [ScreenScale] (1080 x 1920) 求值的屏幕相关过滤器.
 */
class FilterTest {

    private lateinit var context: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    private fun regex(source: String) = context.evaluateString(scope, source, "regex", 1, null) as NativeRegExp

    private val screen = ScreenScale.Source.fixed(1080, 1920)

    private val login = FakeUiNode.root("login") {
        text = "Login"
        desc = "Sign in button"
        id = "com.example:id/login_button"
        idHex = "0x7f0a0001"
        className = "android.widget.Button"
        packageName = "com.example"
        clickable = true
        enabled = true
        actions = listOf("ACTION_CLICK", "ACTION_FOCUS", "Custom label")
        bounds(100, 200, 500, 400)
        child("icon") {
            className = "android.widget.ImageView"
            packageName = "com.example"
            bounds(120, 220, 180, 280)
        }
    }

    private val icon = login.find("icon")

    private fun Filter.accepts(node: UiNode<*>) = filter(node)

    private fun text(matcher: StringMatcher) = StringPropertyFilter(StringProperty.TEXT, matcher)

    @Test
    fun plainStringMatchers() {
        assertTrue(text(StringMatcher.equals("Login")).accepts(login))
        assertFalse(text(StringMatcher.equals("login")).accepts(login))
        assertFalse(text(StringMatcher.equals("Log")).accepts(login))
        assertTrue(text(StringMatcher.startsWith("Log")).accepts(login))
        assertFalse(text(StringMatcher.startsWith("ogin")).accepts(login))
        assertTrue(text(StringMatcher.endsWith("gin")).accepts(login))
        assertFalse(text(StringMatcher.endsWith("Logi")).accepts(login))
        assertTrue(text(StringMatcher.contains("ogi")).accepts(login))
        assertFalse(text(StringMatcher.contains("ogx")).accepts(login))
        assertTrue(text(StringMatcher.matches("L.*n")).accepts(login))
        assertFalse(text(StringMatcher.matches("^L")).accepts(login))
        assertTrue(text(StringMatcher.match("^L")).accepts(login))
        assertFalse(text(StringMatcher.match("^l")).accepts(login))
        // A node without text has the empty string as text, which only the empty string equals.
        assertFalse(text(StringMatcher.equals("Login")).accepts(icon))
        assertTrue(text(StringMatcher.equals("")).accepts(icon))
        assertTrue(text(StringMatcher.startsWith("")).accepts(icon))
    }

    @Test
    fun patternsCompileFromPlainStringsAndJsLiteralsOnlyInMatchesAndMatch() {
        // `matches` / `match` take the string as a pattern; a JS literal keeps its flags.
        assertTrue(text(StringMatcher.matches("/login/i")).accepts(login))
        assertTrue(text(StringMatcher.match("/^l/i")).accepts(login))
        assertFalse(text(StringMatcher.match("/^l/")).accepts(login))
        // `equals`, `startsWith`, `endsWith` and `contains` never do (D15: `contains` is a literal
        // substring again, as before 6.7.0 Alpha13).
        assertFalse(text(StringMatcher.equals("/login/i")).accepts(login))
        assertFalse(text(StringMatcher.startsWith("/L/")).accepts(login))
        assertFalse(text(StringMatcher.endsWith("/n/")).accepts(login))
        assertFalse(text(StringMatcher.contains("/OG/i")).accepts(login))
        assertFalse(text(StringMatcher.contains("L.g")).accepts(login))
        assertFalse(text(StringMatcher.contains(".")).accepts(login))
        assertTrue(text(StringMatcher.contains(".")).accepts(FakeUiNode.root { text = "a.b" }))
        assertTrue(text(StringMatcher.contains("(")).accepts(FakeUiNode.root { text = "f(x)" }))
        assertTrue(text(StringMatcher.contains("1+1")).accepts(FakeUiNode.root { text = "1+1=2" }))
        assertFalse(text(StringMatcher.contains("1+1")).accepts(FakeUiNode.root { text = "11" }))
        // A malformed pattern fails when the matcher is built.
        assertThrows(PatternSyntaxException::class.java) { StringMatcher.match("(") }
        assertThrows(PatternSyntaxException::class.java) { StringMatcher.matches("(") }
    }

    @Test
    fun regexMatchersMatchWholeValuesInEqualsAndMatchesAndAnywhereOtherwise() {
        assertTrue(text(StringMatcher.equals(regex("/login/i"))).accepts(login))
        assertFalse(text(StringMatcher.equals(regex("/log/i"))).accepts(login))
        assertTrue(text(StringMatcher.matches(regex("/l.*n/i"))).accepts(login))
        assertFalse(text(StringMatcher.matches(regex("/l/i"))).accepts(login))
        assertTrue(text(StringMatcher.contains(regex("/og/"))).accepts(login))
        assertFalse(text(StringMatcher.contains(regex("/OG/"))).accepts(login))
        assertTrue(text(StringMatcher.match(regex("/^L/"))).accepts(login))
        assertFalse(text(StringMatcher.match(regex("/^l/"))).accepts(login))
    }

    @Test
    fun matchersRenderTheBuilderTheyCameFrom() {
        assertEquals("text(\"a\")", text(StringMatcher.equals("a")).toString())
        assertEquals("text(/a/i)", text(StringMatcher.equals(regex("/a/i"))).toString())
        assertEquals("textStartsWith(\"a\")", text(StringMatcher.startsWith("a")).toString())
        assertEquals("textEndsWith(\"a\")", text(StringMatcher.endsWith("a")).toString())
        assertEquals("textContains(\"a.b\")", text(StringMatcher.contains("a.b")).toString())
        assertEquals("textContains(/a.b/)", text(StringMatcher.contains(regex("/a.b/"))).toString())
        assertEquals("textMatches(\"^a\")", text(StringMatcher.matches("^a")).toString())
        assertEquals("textMatch(/^a/m)", text(StringMatcher.match(regex("/^a/m"))).toString())
        assertEquals("desc(\"a\")", StringPropertyFilter(StringProperty.DESC, StringMatcher.equals("a")).toString())
        assertEquals("idHex(\"a\")", StringPropertyFilter(StringProperty.ID_HEX, StringMatcher.equals("a")).toString())
        assertEquals("id(\"a\")", StringPropertyFilter(StringProperty.ID_ENTRY, StringMatcher.equals("a")).toString())
    }

    @Test
    fun propertiesFeedTheirOwnValuesAndNullNeverMatches() {
        val equalsLogin = StringMatcher.equals("Login")
        assertTrue(StringProperty.TEXT.test(login, equalsLogin))
        assertFalse(StringProperty.DESC.test(login, equalsLogin))
        assertTrue(StringProperty.DESC.test(login, StringMatcher.equals("Sign in button")))
        // Content is the description or the text, either match counts.
        assertTrue(StringProperty.CONTENT.test(login, equalsLogin))
        assertTrue(StringProperty.CONTENT.test(login, StringMatcher.equals("Sign in button")))
        assertTrue(StringProperty.CONTENT.test(login, StringMatcher.contains("in")))
        assertTrue(StringProperty.CONTENT.test(login, StringMatcher.match("^Log")))
        assertFalse(StringProperty.CONTENT.test(icon, equalsLogin))
        assertTrue(StringProperty.ID.test(login, StringMatcher.equals("com.example:id/login_button")))
        assertTrue(StringProperty.ID_ENTRY.test(login, StringMatcher.equals("login_button")))
        assertTrue(StringProperty.ID_ENTRY.test(FakeUiNode.root { id = "plain" }, StringMatcher.equals("plain")))
        assertTrue(StringProperty.ID_HEX.test(login, StringMatcher.equals("0x7f0a0001")))
        assertTrue(StringProperty.CLASS_NAME.test(login, StringMatcher.endsWith("Button")))
        assertTrue(StringProperty.PACKAGE_NAME.test(login, StringMatcher.startsWith("com.")))
        val blank = FakeUiNode.root()
        for (property in StringProperty.entries) {
            if (property == StringProperty.TEXT || property == StringProperty.CONTENT) continue
            assertFalse(property.toString(), property.test(blank, StringMatcher.equals("")))
            assertFalse(property.toString(), property.test(blank, StringMatcher.match("")))
        }
        // The text is never null, so it (and hence the content) is the empty string when absent.
        assertTrue(StringProperty.TEXT.test(blank, StringMatcher.equals("")))
        assertTrue(StringProperty.CONTENT.test(blank, StringMatcher.equals("")))
        assertFalse(StringProperty.DESC.test(icon, StringMatcher.equals("")))
    }

    @Test
    fun idFiltersAcceptEntryNamesAndFullResourceNames() {
        assertTrue(IdFilter.equals("login_button").accepts(login))
        assertTrue(IdFilter.equals("com.example:id/login_button").accepts(login))
        assertFalse(IdFilter.equals("com.other:id/login_button").accepts(login))
        assertTrue(IdFilter.startsWith("login").accepts(login))
        assertTrue(IdFilter.startsWith("com.example:id/log").accepts(login))
        assertFalse(IdFilter.startsWith("com.other").accepts(login))
        assertFalse(IdFilter.equals("login_button").accepts(icon))
        assertEquals("id(\"login_button\")", IdFilter.equals("login_button").toString())
        assertEquals("idStartsWith(\"com.example:id/log\")", IdFilter.startsWith("com.example:id/log").toString())
    }

    @Test
    fun composeTestTagsRemainRawIdsForExactSubstringAndWholePatternMatching() {
        val button = FakeUiNode.root { id = "start_button" }
        assertTrue(IdFilter.equals("start_button").accepts(button))
        assertTrue(IdFilter.startsWith("start_").accepts(button))
        assertFalse(IdFilter.equals("com.example:id/start_button").accepts(button))
        assertTrue(StringPropertyFilter(StringProperty.ID, StringMatcher.contains("start_")).accepts(button))
        assertFalse(StringPropertyFilter(StringProperty.ID, StringMatcher.contains(":id/start_button")).accepts(button))
        assertTrue(StringPropertyFilter(StringProperty.ID, StringMatcher.matches("start_button")).accepts(button))
        assertTrue(StringPropertyFilter(StringProperty.ID, StringMatcher.matches("^start_button$")).accepts(button))
        assertFalse(StringPropertyFilter(StringProperty.ID, StringMatcher.matches("start")).accepts(button))
    }

    @Test
    fun classNameFiltersExpandBareWidgetNames() {
        assertTrue(ClassNameFilter.equals("Button").accepts(login))
        assertTrue(ClassNameFilter.equals("android.widget.Button").accepts(login))
        assertTrue(ClassNameFilter.startsWith("Butt").accepts(login))
        assertFalse(ClassNameFilter.equals("Button").accepts(icon))
        assertTrue(ClassNameFilter.equals("ImageView").accepts(icon))
        assertTrue(ClassNameFilter.equals("RecyclerView").accepts(FakeUiNode.root { className = "androidx.recyclerview.widget.RecyclerView" }))
        assertEquals("android.widget.Button", ClassNameFilter.expand("Button"))
        assertEquals("a.b.C", ClassNameFilter.expand("a.b.C"))
        assertEquals("className(\"android.widget.Button\")", ClassNameFilter.equals("Button").toString())
    }

    @Test
    fun booleanFiltersDefaultToTrue() {
        assertTrue(BooleanFilter(BooleanFilter.CLICKABLE).accepts(login))
        assertFalse(BooleanFilter(BooleanFilter.CLICKABLE).accepts(icon))
        assertTrue(BooleanFilter(BooleanFilter.CLICKABLE, false).accepts(icon))
        assertTrue(BooleanFilter(BooleanFilter.ENABLED).accepts(login))
        assertTrue(BooleanFilter(BooleanFilter.HAS_CHILDREN).accepts(login))
        assertFalse(BooleanFilter(BooleanFilter.HAS_CHILDREN).accepts(icon))
        assertFalse(BooleanFilter(BooleanFilter.CHECKED, true).accepts(login))
        assertTrue(BooleanFilter(BooleanFilter.CHECKED, false).accepts(login))
        assertEquals("contextClickable(false)", BooleanFilter(BooleanFilter.CONTEXT_CLICKABLE, false).toString())
        assertEquals("clickable()", BooleanFilter(BooleanFilter.CLICKABLE).toString())
    }

    @Test
    fun everyBooleanSupplierReadsItsOwnProperty() {
        val node = FakeUiNode.root {
            checkable = true; checked = true; focusable = true; focused = true; visibleToUser = true
            accessibilityFocused = true; selected = true; clickable = true; longClickable = true; enabled = true
            password = true; scrollable = true; editable = true; contentInvalid = true; contextClickable = true
            multiLine = true; dismissable = true
        }
        val suppliers = listOf(
            BooleanFilter.CHECKABLE, BooleanFilter.CHECKED, BooleanFilter.FOCUSABLE, BooleanFilter.FOCUSED,
            BooleanFilter.VISIBLE_TO_USER, BooleanFilter.ACCESSIBILITY_FOCUSED, BooleanFilter.SELECTED,
            BooleanFilter.CLICKABLE, BooleanFilter.LONG_CLICKABLE, BooleanFilter.ENABLED, BooleanFilter.PASSWORD,
            BooleanFilter.SCROLLABLE, BooleanFilter.EDITABLE, BooleanFilter.CONTENT_INVALID,
            BooleanFilter.CONTEXT_CLICKABLE, BooleanFilter.MULTI_LINE, BooleanFilter.DISMISSABLE,
        )
        suppliers.forEach { assertTrue(it.toString(), it[node]) }
        val blank = FakeUiNode.root { visibleToUser = false; enabled = false }
        suppliers.forEach { assertFalse(it.toString(), it[blank]) }
        assertEquals(suppliers.size, suppliers.map { it.toString() }.toSet().size)
    }

    @Test
    fun intFiltersCompareTreeAndCollectionProperties() {
        val grid = FakeUiNode.root { rowCount = 3; columnCount = 2; drawingOrder = 5 }
        val cell = grid.child("cell") { row = 1; column = 0; rowSpan = 1; columnSpan = 2 }
        assertTrue(IntFilter(IntFilter.Property.DEPTH, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.INDEX_IN_PARENT, 0).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.INDEX_IN_PARENT, -1).accepts(grid))
        assertTrue(IntFilter(IntFilter.Property.CHILD_COUNT, 1).accepts(grid))
        assertTrue(IntFilter(IntFilter.Property.ROW_COUNT, 3).accepts(grid))
        assertTrue(IntFilter(IntFilter.Property.COLUMN_COUNT, 2).accepts(grid))
        assertTrue(IntFilter(IntFilter.Property.DRAWING_ORDER, 5).accepts(grid))
        assertTrue(IntFilter(IntFilter.Property.ROW, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.COLUMN, 0).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.ROW_SPAN, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.COLUMN_SPAN, 2).accepts(cell))
        assertTrue(IntFilter(IntFilter.Property.ROW, -1).accepts(grid))
        assertEquals(IntFilter.Property.entries.size, IntFilter.Property.entries.map { it.toString() }.toSet().size)
    }

    @Test
    fun intComparisonsAndTheirNames() {
        val two = FakeUiNode.root { child("a"); child("b") }
        val equals = IntFilter(IntFilter.Property.CHILD_COUNT, IntFilter.Comparison.Equals(2))
        val min = IntFilter(IntFilter.Property.CHILD_COUNT, IntFilter.Comparison.Min(2))
        val max = IntFilter(IntFilter.Property.CHILD_COUNT, IntFilter.Comparison.Max(2))
        for ((count, node) in listOf(1 to two.find("a"), 2 to two, 3 to FakeUiNode.root { child("a"); child("b"); child("c") })) {
            assertEquals("equals $count", count == 2, equals.accepts(node))
            assertEquals("min $count", count >= 2, min.accepts(node))
            assertEquals("max $count", count <= 2, max.accepts(node))
        }
        assertEquals("childCount(2)", equals.toString())
        assertEquals("minChildCount(2)", min.toString())
        assertEquals("maxChildCount(2)", max.toString())
        assertEquals("depth(0)", IntFilter(IntFilter.Property.DEPTH, 0).toString())
    }

    @Test
    fun screenScaleResolvesFractionsWholeSidesAndPixels() {
        val scale = screen.scale()
        assertEquals(540, scale.round(ScreenScale.Axis.X, 0.5))
        assertEquals(960, scale.round(ScreenScale.Axis.Y, 0.5))
        assertEquals(1080, scale.round(ScreenScale.Axis.X, -1.0))
        assertEquals(1920, scale.round(ScreenScale.Axis.Y, -1.0))
        assertEquals(100, scale.round(ScreenScale.Axis.X, 100.0))
        assertEquals(101, scale.round(ScreenScale.Axis.X, 100.5))
        assertEquals(-5, scale.round(ScreenScale.Axis.X, -5.0))
        assertEquals(0, scale.round(ScreenScale.Axis.X, 0.0))
        assertEquals(1, scale.round(ScreenScale.Axis.X, 1.0))
        assertEquals(-540, scale.round(ScreenScale.Axis.X, -0.5))
        assertEquals(359, scale.floor(ScreenScale.Axis.X, 0.333))
        assertEquals(360, scale.ceil(ScreenScale.Axis.X, 0.333))
        assertEquals(100, scale.floor(ScreenScale.Axis.Y, 100.9))
        assertEquals(101, scale.ceil(ScreenScale.Axis.Y, 100.1))
    }

    @Test
    fun metricsComparisonsResolveAgainstTheInjectedScreen() {
        fun metric(property: MetricsFilter.Property, comparison: MetricsFilter.Comparison, spelling: MetricsFilter.Spelling = MetricsFilter.Spelling.PLAIN) =
            MetricsFilter(property, comparison, spelling, screen)
        // login: left 100, top 200, right 500, bottom 400, width 400, height 200, centre (300, 300)
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Equals(100.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Equals(100.4)).accepts(login))
        assertFalse(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Equals(100.5)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.TOP, MetricsFilter.Comparison.Equals(200.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.RIGHT, MetricsFilter.Comparison.Equals(500.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.BOTTOM, MetricsFilter.Comparison.Equals(400.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.WIDTH, MetricsFilter.Comparison.Equals(400.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.HEIGHT, MetricsFilter.Comparison.Equals(200.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.CENTER_X, MetricsFilter.Comparison.Equals(300.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.CENTER_Y, MetricsFilter.Comparison.Equals(300.0)).accepts(login))
        // Fractions of the screen side, on the axis of the metric: 0.5 is 540 on X and 960 on Y.
        val half = FakeUiNode.root { bounds(0, 0, 540, 960) }
        assertTrue(metric(MetricsFilter.Property.RIGHT, MetricsFilter.Comparison.Equals(0.5)).accepts(half))
        assertTrue(metric(MetricsFilter.Property.BOTTOM, MetricsFilter.Comparison.Equals(0.5)).accepts(half))
        assertFalse(metric(MetricsFilter.Property.RIGHT, MetricsFilter.Comparison.Equals(0.25)).accepts(half))
        assertTrue(metric(MetricsFilter.Property.WIDTH, MetricsFilter.Comparison.Equals(-1.0)).accepts(FakeUiNode.root { bounds(0, 0, 1080, 10) }))
        // Range, min and max are inclusive on both ends.
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Range(100.0, 100.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Range(0.0, 0.5)).accepts(login))
        assertFalse(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Range(101.0, 200.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Min(100.0)).accepts(login))
        assertFalse(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Min(101.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Max(100.0)).accepts(login))
        assertFalse(metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Max(99.0)).accepts(login))
        assertTrue(metric(MetricsFilter.Property.CENTER_Y, MetricsFilter.Comparison.Max(0.5)).accepts(login))
        // Names, in both spellings.
        assertEquals("left(100)", metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Equals(100.0)).toString())
        assertEquals("boundsLeft(0.5)", metric(MetricsFilter.Property.LEFT, MetricsFilter.Comparison.Equals(0.5), MetricsFilter.Spelling.BOUNDS).toString())
        assertEquals("centerX(0, 0.333)", metric(MetricsFilter.Property.CENTER_X, MetricsFilter.Comparison.Range(0.0, 0.333)).toString())
        assertEquals("boundsCenterY(1, 2)", metric(MetricsFilter.Property.CENTER_Y, MetricsFilter.Comparison.Range(1.0, 2.0), MetricsFilter.Spelling.BOUNDS).toString())
        assertEquals("minWidth(10)", metric(MetricsFilter.Property.WIDTH, MetricsFilter.Comparison.Min(10.0)).toString())
        assertEquals("boundsMinWidth(10)", metric(MetricsFilter.Property.WIDTH, MetricsFilter.Comparison.Min(10.0), MetricsFilter.Spelling.BOUNDS).toString())
        assertEquals("maxHeight(0.016)", metric(MetricsFilter.Property.HEIGHT, MetricsFilter.Comparison.Max(0.016)).toString())
        assertEquals("boundsMaxHeight(-1)", metric(MetricsFilter.Property.HEIGHT, MetricsFilter.Comparison.Max(-1.0), MetricsFilter.Spelling.BOUNDS).toString())
    }

    @Test
    fun boundsFiltersCompareNormalisedRectangles() {
        fun bounds(l: Double, t: Double, r: Double, b: Double, type: Int) = BoundsFilter(l, t, r, b, type, screen)
        assertTrue(bounds(100.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_EQUALS).accepts(login))
        assertTrue(bounds(99.5, 199.5, 500.5, 400.5, BoundsFilter.TYPE_EQUALS).accepts(login))
        assertFalse(bounds(101.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_EQUALS).accepts(login))
        // A node with inverted bounds (a narrow empty rectangle, issue #49) is normalised before comparing.
        val inverted = FakeUiNode.root { bounds(500, 400, 100, 200) }
        assertTrue(bounds(100.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_EQUALS).accepts(inverted))
        assertTrue(bounds(0.0, 0.0, 600.0, 500.0, BoundsFilter.TYPE_INSIDE).accepts(inverted))
        // So is an inverted argument rectangle of `boundsInside` / `boundsContains`.
        assertTrue(bounds(600.0, 500.0, 0.0, 0.0, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertTrue(bounds(0.0, 0.0, 600.0, 500.0, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertTrue(bounds(100.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertFalse(bounds(101.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertTrue(bounds(100.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_INSIDE).accepts(icon))
        assertTrue(bounds(120.0, 220.0, 180.0, 280.0, BoundsFilter.TYPE_CONTAINS).accepts(login))
        assertTrue(bounds(150.0, 250.0, 160.0, 260.0, BoundsFilter.TYPE_CONTAINS).accepts(login))
        assertFalse(bounds(90.0, 250.0, 160.0, 260.0, BoundsFilter.TYPE_CONTAINS).accepts(login))
        assertFalse(bounds(100.0, 200.0, 500.0, 400.0, BoundsFilter.TYPE_CONTAINS).accepts(icon))
        // Screen-relative arguments: the whole screen contains everything on it.
        assertTrue(bounds(0.0, 0.0, -1.0, -1.0, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertTrue(bounds(0.0, 0.0, 0.5, 0.25, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertFalse(bounds(0.0, 0.0, 0.25, 0.25, BoundsFilter.TYPE_INSIDE).accepts(login))
        assertEquals("bounds(0, 0, 0.5, 0.25)", bounds(0.0, 0.0, 0.5, 0.25, BoundsFilter.TYPE_EQUALS).toString())
        assertEquals("boundsInside(0, 0, -1, -1)", bounds(0.0, 0.0, -1.0, -1.0, BoundsFilter.TYPE_INSIDE).toString())
        assertEquals("boundsContains(1, 2, 3, 4)", bounds(1.0, 2.0, 3.0, 4.0, BoundsFilter.TYPE_CONTAINS).toString())
    }

    @Test
    fun screenCentreAndCoverageUseTheInjectedScreen() {
        val centred = FakeUiNode.root { bounds(500, 900, 580, 1020) } // centre (540, 960)
        val nearlyCentred = FakeUiNode.root { bounds(520, 900, 580, 1020) } // centre x 550, off by 10 / 1080 = 0.93 %
        assertTrue(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, scale = screen).accepts(centred))
        assertTrue(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, scale = screen).accepts(centred))
        assertTrue(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, scale = screen).accepts(nearlyCentred))
        assertFalse(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, null, 0.005, screen).accepts(nearlyCentred))
        assertTrue(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, false, 0.005, screen).accepts(nearlyCentred))
        assertFalse(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, scale = screen).accepts(login))
        assertTrue(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, false, scale = screen).accepts(login))
        assertEquals("screenCenterX()", ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X).toString())
        assertEquals("screenCenterY(0.1)", ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, null, 0.1).toString())
        assertEquals("screenCenterX(false, 0.1)", ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, false, 0.1).toString())
        assertEquals("screenCenterX(true)", ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, true).toString())

        val fullScreen = FakeUiNode.root { bounds(0, 0, 1080, 1920) }
        val overflowing = FakeUiNode.root { bounds(-100, -100, 1200, 2000) }
        val quarter = FakeUiNode.root { bounds(0, 0, 540, 960) }
        assertTrue(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, scale = screen).accepts(fullScreen))
        assertTrue(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, scale = screen).accepts(overflowing))
        assertFalse(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, scale = screen).accepts(quarter))
        assertTrue(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, 0.25, screen).accepts(quarter))
        assertFalse(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, 0.26, screen).accepts(quarter))
        assertEquals(0.25, DoubleMinFilter.SCREEN_COVERAGE[quarter, screen.scale()], 1e-9)
        assertEquals("screenCoverage()", DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE).toString())
        assertEquals("screenCoverage(0.5)", DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, 0.5).toString())
    }

    @Test
    fun numbersRenderWithUpToThreeDecimalsAndADot() {
        assertEquals("0", FilterFormat.number(0.0))
        assertEquals("100", FilterFormat.number(100.0))
        assertEquals("-1", FilterFormat.number(-1.0))
        assertEquals("0.5", FilterFormat.number(0.5))
        assertEquals("0.25", FilterFormat.number(0.25))
        assertEquals("0.016", FilterFormat.number(0.016))
        assertEquals("0.333", FilterFormat.number(0.3333))
        assertEquals("1.5", FilterFormat.number(1.5))
    }

    @Test
    fun actionFilterMatchesIdsAndNamesWithOrWithoutPrefix() {
        assertTrue(ActionFilter(arrayOf("CLICK")).accepts(login))
        assertTrue(ActionFilter(arrayOf("ACTION_CLICK", "FOCUS")).accepts(login))
        assertTrue(ActionFilter(arrayOf(16 /* ACTION_CLICK */)).accepts(login))
        assertTrue(ActionFilter(arrayOf<Any>(16.0, "FOCUS")).accepts(login))
        assertFalse(ActionFilter(arrayOf("LONG_CLICK")).accepts(login))
        assertFalse(ActionFilter(arrayOf(32 /* ACTION_LONG_CLICK */)).accepts(login))
        assertFalse(ActionFilter(arrayOf(Any())).accepts(login))
        assertTrue(ActionFilter(emptyArray()).accepts(icon))
        assertEquals("action(CLICK)", ActionFilter(arrayOf("CLICK")).toString())
        assertEquals("action([CLICK,FOCUS])", ActionFilter(arrayOf("CLICK", "FOCUS")).toString())
    }

    @Test
    fun selectorRequiresEveryFilterAndEvaluatesASnapshot() {
        val selector = Selector()
        assertTrue(selector.accepts(icon))
        selector.add(BooleanFilter(BooleanFilter.CLICKABLE))
        selector.add(IntFilter(IntFilter.Property.DEPTH, 0))
        assertTrue(selector.accepts(login))
        assertFalse(selector.accepts(icon))
        assertEquals("clickable().depth(0)", selector.toString())
        assertEquals("selector()", Selector().toString())
        // Appending while evaluating does not disturb the evaluation in progress.
        val growing = Selector()
        growing.add(object : Filter {
            override fun filter(node: UiNode<*>): Boolean {
                growing.add(BooleanFilter(BooleanFilter.CHECKED))
                return true
            }
        })
        assertTrue(growing.accepts(login))
        assertEquals(2, growing.filters.size)
        assertFalse(growing.accepts(login))
    }

}
