package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.inspector.FilterProfile.BooleanProperty
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.IntRangeSpec
import org.autojs.autojs.core.accessibility.inspector.FilterProfile.Tri
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.JsLiteral
import org.autojs.autojs.core.automator.filter.ScreenScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SelectorExport]: the keyword query maps to the hit property's `Contains` / `Match` call, field
 * queries print their filters, profile and region filters follow, inexpressible filters become
 * trailing comments, action names are quoted and string literals are escaped (roadmap P4.2, D8).
 * zh-CN: [SelectorExport]: 关键词查询映射为命中属性的 `Contains` / `Match` 调用, 字段查询打印其过滤器, 其后为配置与区域过滤器,
 * 不可表达的过滤器成为尾随注释, 动作名加引号, 字符串字面量转义 (Roadmap P4.2, D8).
 */
class SelectorExportTest {

    private val screen = ScreenScale.Source.fixed(1080, 1920)

    private fun keyword(text: String, regex: Boolean = false) = InspectorQuery(InspectorQuery.Mode.KEYWORD, text, regex)

    private fun field(text: String) = InspectorQuery(InspectorQuery.Mode.FIELD, text)

    private fun filtersOf(query: InspectorQuery): List<Filter> = (query.compile() as InspectorQuery.Compiled.Filters).filters

    private fun profile(
        booleans: Map<BooleanProperty, Tri> = emptyMap(),
        depth: IntRangeSpec = IntRangeSpec.ANY,
        onScreenOnly: Boolean = false,
        packages: Set<String> = emptySet(),
        actions: Set<String> = emptySet(),
    ) = FilterProfile("p", "P", booleans = booleans, depth = depth, onScreenOnly = onScreenOnly, packages = packages, actions = actions).toFilters(screen)

    @Test
    fun keywordUsesTheHitPropertyAndFallsBackToText() {
        assertEquals("textContains(\"OK\")", SelectorExport.render(keyword("OK"), emptyList(), KeywordProperty.TEXT, emptyList()).text)
        assertEquals("descContains(\"OK\")", SelectorExport.render(keyword("OK"), emptyList(), KeywordProperty.DESC, emptyList()).text)
        assertEquals("idContains(\"ok\")", SelectorExport.render(keyword(" ok "), emptyList(), KeywordProperty.ID, emptyList()).text)
        assertEquals("classNameContains(\"Button\")", SelectorExport.render(keyword("Button"), emptyList(), KeywordProperty.CLASS_NAME, emptyList()).text)
        assertEquals("textContains(\"OK\")", SelectorExport.render(keyword("OK"), emptyList(), null, emptyList()).text)
    }

    @Test
    fun regexKeywordBecomesAMatchCallWithAJsLiteral() {
        assertEquals("textMatch(/OK|Cancel/i)", SelectorExport.render(keyword("/OK|Cancel/i", regex = true), emptyList(), KeywordProperty.TEXT, emptyList()).text)
        assertEquals("descMatch(/^btn_/)", SelectorExport.render(keyword("^btn_", regex = true), emptyList(), KeywordProperty.DESC, emptyList()).text)
    }

    @Test
    fun profileAndRegionFiltersAreChainedAfterTheQuery() {
        val extra = profile(booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES), depth = IntRangeSpec(3, 5)) +
            BoundsFilter(0.0, 120.0, 720.0, 900.0, BoundsFilter.TYPE_INSIDE)
        assertEquals(
            "textContains(\"OK\").clickable(true).minDepth(3).maxDepth(5).boundsInside(0, 120, 720, 900)",
            SelectorExport.render(keyword("OK"), emptyList(), KeywordProperty.TEXT, extra).text,
        )
    }

    @Test
    fun fieldQueryPrintsItsOwnFilters() {
        val query = field("text=OK clickable minDepth=3")
        assertEquals("text(\"OK\").clickable().minDepth(3)", SelectorExport.render(query, filtersOf(query), null, emptyList()).text)
    }

    @Test
    fun inexpressibleFiltersTrailAsComments() {
        val extra = profile(onScreenOnly = true, packages = setOf("com.b", "com.a"))
        val rendered = SelectorExport.render(keyword(""), emptyList(), null, extra)
        assertEquals(emptyList<String>(), rendered.calls)
        assertEquals(listOf("/* onScreen */", "/* packageName(\"com.a\") | packageName(\"com.b\") */"), rendered.comments)
        assertEquals("selector() /* onScreen */ /* packageName(\"com.a\") | packageName(\"com.b\") */", rendered.text)
    }

    @Test
    fun actionNamesAreQuotedAndSinglePackageIsACall() {
        val extra = profile(packages = setOf("com.a"), actions = setOf("ACTION_FOCUS", "ACTION_CLICK"))
        assertEquals("packageName(\"com.a\").action(\"ACTION_CLICK\", \"ACTION_FOCUS\")", SelectorExport.render(keyword(""), emptyList(), null, extra).text)
    }

    @Test
    fun emptyInputRendersTheEmptySelector() {
        val rendered = SelectorExport.render(keyword("   "), emptyList(), null, emptyList())
        assertTrue(rendered.isEmpty)
        assertEquals("selector()", rendered.text)
    }

    @Test
    fun thePickupFormPutsTheCallsInAnObjectSelector() {
        val extra = profile(booleans = mapOf(BooleanProperty.CLICKABLE to Tri.YES), onScreenOnly = true) +
            BoundsFilter(0.0, 120.0, 720.0, 900.0, BoundsFilter.TYPE_INSIDE)
        val rendered = SelectorExport.render(keyword("/a,b/i", regex = true), emptyList(), KeywordProperty.TEXT, extra)
        assertEquals(
            """pickup({textMatch: /a,b/i, clickable: true, boundsInside: [0, 120, 720, 900]}) /* onScreen */""",
            rendered.text(SelectorExport.Form.PICKUP),
        )
        val query = field("text=OK clickable minDepth=3")
        assertEquals(
            """pickup({text: "OK", clickable: null, minDepth: 3})""",
            SelectorExport.render(query, filtersOf(query), null, emptyList()).text(SelectorExport.Form.PICKUP),
        )
        val actions = profile(actions = setOf("ACTION_FOCUS", "ACTION_CLICK"))
        assertEquals(
            """pickup({action: ["ACTION_CLICK", "ACTION_FOCUS"]})""",
            SelectorExport.render(keyword(""), emptyList(), null, actions).text(SelectorExport.Form.PICKUP),
        )
    }

    @Test
    fun thePickupFormKeepsTheChainForARepeatedProperty() {
        val rendered = SelectorExport.Rendered(listOf("""text("a")""", """text("b")"""), listOf("/* onScreen */"))
        assertEquals("""pickup(text("a").text("b")) /* onScreen */""", rendered.text(SelectorExport.Form.PICKUP))
        assertEquals("""text("a").text("b") /* onScreen */""", rendered.text)
    }

    @Test
    fun theArgumentsSplitAtTheTopLevelOnly() {
        val calls = listOf(
            """textContains("x, \"y\", z")""",
            """normalize({trim: true, nfkc: true})""",
            """prefer("visible", "center")""",
            """descMatch(/[/,]x\/,/g)""",
            """idEndsWith('a,b')""",
            """grid([1, 2])""",
            """hint(null)""",
        )
        assertEquals(
            """pickup({textContains: "x, \"y\", z", normalize: {trim: true, nfkc: true}, prefer: ["visible", "center"], """ +
                """descMatch: /[/,]x\/,/g, idEndsWith: 'a,b', grid: [[1, 2]], hint: [null]})""",
            SelectorExport.Rendered(calls, emptyList()).text(SelectorExport.Form.PICKUP),
        )
        assertEquals(listOf<String>(), CallSyntax.split("  "))
        assertEquals(null, CallSyntax.split("\"a"))
        assertEquals(null, CallSyntax.split("(a"))
        assertEquals(null, CallSyntax.split("a,"))
        assertEquals(null, CallSyntax.parse("text"))
        assertEquals(null, CallSyntax.parse("a.b(1)"))
    }

    @Test
    fun stringLiteralsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\n\"", JsLiteral.quote("a\"b\\c\n"))
        assertEquals("textContains(\"say \\\"hi\\\"\")", SelectorExport.render(keyword("say \"hi\""), emptyList(), KeywordProperty.TEXT, emptyList()).text)
    }

}
