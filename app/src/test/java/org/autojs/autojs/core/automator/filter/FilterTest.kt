package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selector filters evaluated over [FakeUiNode]: what each one reads from a [UiNode] and how
 * the string forms are matched. Screen-relative filters (bounds, metrics, screen center, coverage)
 * depend on the device metrics and stay device-tested until roadmap P2.3.
 * zh-CN: 在 [FakeUiNode] 上求值的选择器过滤器: 各自读取 [UiNode] 的哪个属性, 字符串形式如何匹配. 依赖设备尺寸的过滤器
 * (bounds, metrics, screenCenter, coverage) 在路线图 P2.3 之前仍只在设备上测试.
 */
class FilterTest {

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
        child("icon") {
            className = "android.widget.ImageView"
            packageName = "com.example"
        }
    }

    private val icon = login.find("icon")

    private fun Filter.accepts(node: UiNode<*>) = filter(node)

    @Test
    fun textFiltersReadTheNodeText() {
        assertTrue(TextFilter.equals("Login").accepts(login))
        assertFalse(TextFilter.equals("login").accepts(login))
        assertTrue(TextFilter.contains("ogi").accepts(login))
        assertTrue(TextFilter.startsWith("Log").accepts(login))
        assertTrue(TextFilter.endsWith("gin").accepts(login))
        assertTrue(TextFilter.match("^L").accepts(login))
        assertFalse(TextFilter.matches("^L").accepts(login))
        assertTrue(TextFilter.matches("L.*n").accepts(login))
        // A node without text has the empty string as text, which only the empty string equals.
        assertFalse(TextFilter.equals("Login").accepts(icon))
        assertTrue(TextFilter.equals("").accepts(icon))
    }

    @Test
    fun jsRegexLiteralsAreHonouredInStringFormExceptByEquals() {
        // `equals` compares the plain string; a regex only reaches it through the NativeRegExp overload.
        assertFalse(TextFilter.equals("/login/i").accepts(login))
        assertTrue(TextFilter.contains("/OG/i").accepts(login))
        assertTrue(TextFilter.match("/^l/i").accepts(login))
        assertFalse(TextFilter.match("/^l/").accepts(login))
    }

    @Test
    fun descAndContentFilters() {
        assertTrue(DescFilter.equals("Sign in button").accepts(login))
        assertTrue(DescFilter.startsWith("Sign").accepts(login))
        assertFalse(DescFilter.equals("").accepts(icon))
        // Content is the description or the text, either match counts.
        assertTrue(ContentFilter.equals("Sign in button").accepts(login))
        assertTrue(ContentFilter.equals("Login").accepts(login))
        assertTrue(ContentFilter.contains("in").accepts(login))
        assertTrue(ContentFilter.match("^Log").accepts(login))
        assertFalse(ContentFilter.equals("Login").accepts(icon))
    }

    @Test
    fun idFiltersAcceptEntryNamesAndFullResourceNames() {
        assertTrue(IdFilter.equals("login_button").accepts(login))
        assertTrue(IdFilter.equals("com.example:id/login_button").accepts(login))
        assertFalse(IdFilter.equals("com.other:id/login_button").accepts(login))
        assertTrue(IdFilter.startsWith("login").accepts(login))
        assertTrue(IdFilter.startsWith("com.example:id/log").accepts(login))
        assertTrue(IdFilter.endsWith("_button").accepts(login))
        assertTrue(IdFilter.contains(":id/").accepts(login))
        assertTrue(IdFilter.match("login_\\w+$").accepts(login))
        assertFalse(IdFilter.equals("login_button").accepts(icon))
        assertTrue(IdHexFilter.equals("0x7f0a0001").accepts(login))
    }

    @Test
    fun classNameFiltersExpandBareWidgetNames() {
        assertTrue(ClassNameFilter.equals("Button").accepts(login))
        assertTrue(ClassNameFilter.equals("android.widget.Button").accepts(login))
        assertTrue(ClassNameFilter.startsWith("Butt").accepts(login))
        assertTrue(ClassNameFilter.endsWith("Button").accepts(login))
        assertFalse(ClassNameFilter.equals("Button").accepts(icon))
        assertTrue(ClassNameFilter.equals("ImageView").accepts(icon))
        assertTrue(ClassNameFilter.equals("RecyclerView").accepts(FakeUiNode.root { className = "androidx.recyclerview.widget.RecyclerView" }))
    }

    @Test
    fun packageNameFilter() {
        assertTrue(PackageNameFilter.equals("com.example").accepts(login))
        assertTrue(PackageNameFilter.startsWith("com.").accepts(icon))
        assertFalse(PackageNameFilter.equals("com.example").accepts(FakeUiNode.root()))
    }

    @Test
    fun booleanFiltersDefaultToTrueAndAreCachedPerSupplierAndValue() {
        assertTrue(BooleanFilter(BooleanFilter.CLICKABLE).accepts(login))
        assertFalse(BooleanFilter(BooleanFilter.CLICKABLE).accepts(icon))
        assertTrue(BooleanFilter(BooleanFilter.CLICKABLE, false).accepts(icon))
        assertTrue(BooleanFilter(BooleanFilter.ENABLED).accepts(login))
        assertTrue(BooleanFilter(BooleanFilter.HAS_CHILDREN).accepts(login))
        assertFalse(BooleanFilter(BooleanFilter.HAS_CHILDREN).accepts(icon))
        assertSame(BooleanFilter[BooleanFilter.CHECKED, true], BooleanFilter[BooleanFilter.CHECKED, true])
        assertFalse(BooleanFilter[BooleanFilter.CHECKED, true].accepts(login))
        assertTrue(BooleanFilter[BooleanFilter.CHECKED, false].accepts(login))
        assertEquals("contextClickable(false)", BooleanFilter(BooleanFilter.CONTEXT_CLICKABLE, false).toString())
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
        assertTrue(IntFilter(IntFilter.DEPTH, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.INDEX_IN_PARENT, 0).accepts(cell))
        assertTrue(IntFilter(IntFilter.INDEX_IN_PARENT, -1).accepts(grid))
        assertTrue(IntFilter(IntFilter.CHILD_COUNT, 1).accepts(grid))
        assertTrue(IntFilter(IntFilter.ROW_COUNT, 3).accepts(grid))
        assertTrue(IntFilter(IntFilter.COLUMN_COUNT, 2).accepts(grid))
        assertTrue(IntFilter(IntFilter.DRAWING_ORDER, 5).accepts(grid))
        assertTrue(IntFilter(IntFilter.ROW, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.COLUMN, 0).accepts(cell))
        assertTrue(IntFilter(IntFilter.ROW_SPAN, 1).accepts(cell))
        assertTrue(IntFilter(IntFilter.COLUMN_SPAN, 2).accepts(cell))
        assertTrue(IntFilter(IntFilter.ROW, -1).accepts(grid))
        assertTrue(MinIntFilter(MinIntFilter.CHILD_COUNT, 1).accepts(grid))
        assertFalse(MinIntFilter(MinIntFilter.CHILD_COUNT, 2).accepts(grid))
        assertTrue(MaxIntFilter(MaxIntFilter.CHILD_COUNT, 0).accepts(cell))
        assertFalse(MaxIntFilter(MaxIntFilter.CHILD_COUNT, 0).accepts(grid))
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
    fun selectorRequiresEveryFilter() {
        val selector = Selector()
        assertTrue(selector.accepts(icon))
        selector.add(BooleanFilter(BooleanFilter.CLICKABLE))
        selector.add(IntFilter(IntFilter.DEPTH, 0))
        assertTrue(selector.accepts(login))
        assertFalse(selector.accepts(icon))
        assertEquals("clickable().depth(0)", selector.toString())
        assertEquals("selector()", Selector().toString())
    }

}
