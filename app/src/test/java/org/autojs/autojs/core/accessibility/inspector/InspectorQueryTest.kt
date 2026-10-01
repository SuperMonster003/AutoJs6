package org.autojs.autojs.core.accessibility.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InspectorQuery.compile]: blank text filters nothing, keyword text yields one [KeywordFilter],
 * a bad pattern yields an error with the offending offset, the later modes are reported as
 * unsupported until roadmap P4 / P5 wire them.
 * zh-CN: [InspectorQuery.compile]: 空白文本不筛选, 关键词文本给出一个 [KeywordFilter], 非法模式给出带偏移的错误,
 * 后续模式在 Roadmap P4 / P5 接入前报告为不支持.
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
    fun fieldAndSelectorModesAreUnsupportedForNow() {
        for (mode in listOf(InspectorQuery.Mode.FIELD, InspectorQuery.Mode.SELECTOR)) {
            val compiled = InspectorQuery(mode = mode, text = "clickable").compile() as InspectorQuery.Compiled.Error
            assertEquals(InspectorQueryError.Kind.UNSUPPORTED_MODE, compiled.error.kind)
            assertEquals(mode.name, compiled.error.detail)
        }
    }

    @Test
    fun errorRendersKindPositionAndDetail() {
        assertEquals("SYNTAX @3: boom", InspectorQueryError(InspectorQueryError.Kind.SYNTAX, "boom", 3).toString())
        assertEquals("UNSUPPORTED_MODE", InspectorQueryError(InspectorQueryError.Kind.UNSUPPORTED_MODE).toString())
    }

}
