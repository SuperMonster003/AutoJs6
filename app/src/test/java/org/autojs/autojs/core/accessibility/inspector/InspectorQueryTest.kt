package org.autojs.autojs.core.accessibility.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InspectorQuery.compile]: blank text filters nothing, keyword text yields one [KeywordFilter],
 * a bad pattern yields an error with the offending offset, the field mode parses the script string
 * syntax (roadmap P4.1) and reports its errors with position, term and candidates, the selector
 * mode is reported as unsupported until roadmap P5 wires it.
 * zh-CN: [InspectorQuery.compile]: 空白文本不筛选, 关键词文本给出一个 [KeywordFilter], 非法模式给出带偏移的错误, 字段模式解析脚本
 * 字符串语法 (Roadmap P4.1) 并以位置, 条件与候选报告错误, 选择器模式在 Roadmap P5 接入前报告为不支持.
 */
class InspectorQueryTest {

    @Test
    fun blankTextCompilesToNoFilters() {
        val compiled = InspectorQuery(text = "   ").compile() as InspectorQuery.Compiled.Filters
        assertTrue(compiled.isEmpty)
        assertTrue(InspectorQuery().isBlank)
    }

    @Test
    fun keywordTextIsTrimmedIntoOneKeywordFilter() {
        val compiled = InspectorQuery(text = "  login ").compile() as InspectorQuery.Compiled.Filters
        val filter = compiled.filters.single() as KeywordFilter
        assertEquals("login", filter.keyword)
        assertEquals(false, filter.isRegex)
    }

    @Test
    fun malformedRegexReportsInvalidRegexWithPosition() {
        val compiled = InspectorQuery(text = "ab(", regex = true).compile() as InspectorQuery.Compiled.Error
        assertEquals(InspectorQueryError.Kind.INVALID_REGEX, compiled.error.kind)
        assertTrue(compiled.error.position >= 0)
    }

    @Test
    fun selectorModeIsUnsupportedForNow() {
        val compiled = InspectorQuery(mode = InspectorQuery.Mode.SELECTOR, text = "clickable()").compile() as InspectorQuery.Compiled.Error
        assertEquals(InspectorQueryError.Kind.UNSUPPORTED_MODE, compiled.error.kind)
        assertEquals("SELECTOR", compiled.error.detail)
    }

    @Test
    fun fieldModeParsesTheScriptStringSyntax() {
        val compiled = InspectorQuery(mode = InspectorQuery.Mode.FIELD, text = " text=Login clickable !enabled id~=btn_.* minDepth=3 ").compile() as InspectorQuery.Compiled.Filters
        assertEquals(listOf("text(\"Login\")", "clickable()", "enabled(false)", "idMatch(\"btn_.*\")", "minDepth(3)"), compiled.filters.map { it.toString() })
    }

    @Test
    fun fieldModeReportsSyntaxErrorsWithPositionTermAndCandidates() {
        val unknown = InspectorQuery(mode = InspectorQuery.Mode.FIELD, text = "text=ok clikable").compile() as InspectorQuery.Compiled.Error
        assertEquals(InspectorQueryError.Kind.SYNTAX, unknown.error.kind)
        assertEquals(8, unknown.error.position)
        assertEquals("clikable", unknown.error.term)
        assertTrue(unknown.error.toString(), "clickable" in unknown.error.candidates)

        val badValue = InspectorQuery(mode = InspectorQuery.Mode.FIELD, text = "minDepth=x").compile() as InspectorQuery.Compiled.Error
        assertEquals(0, badValue.error.position)
        assertTrue(badValue.error.detail!!, "minDepth" in badValue.error.detail!!)
    }

    @Test
    fun errorRendersKindPositionTermDetailAndCandidates() {
        assertEquals("SYNTAX @3: boom", InspectorQueryError(InspectorQueryError.Kind.SYNTAX, "boom", 3).toString())
        assertEquals("SYNTAX @0 [foo=1]: no such property (font, foo2)", InspectorQueryError(InspectorQueryError.Kind.SYNTAX, "no such property", 0, "foo=1", listOf("font", "foo2")).toString())
        assertEquals("UNSUPPORTED_MODE", InspectorQueryError(InspectorQueryError.Kind.UNSUPPORTED_MODE).toString())
    }

}
