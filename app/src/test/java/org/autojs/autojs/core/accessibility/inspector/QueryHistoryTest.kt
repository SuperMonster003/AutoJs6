package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.inspector.InspectorQuery.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap P2.2: ordering, de-duplication, cap, removal and the tolerant JSON codec of [QueryHistory].
 * zh-CN: Roadmap P2.2: [QueryHistory] 的排序, 去重, 上限, 删除与宽容的 JSON 编解码.
 */
class QueryHistoryTest {

    private fun q(text: String, regex: Boolean = false, mode: Mode = Mode.KEYWORD) = InspectorQuery(mode, text, regex)

    @Test
    fun recordsNewestFirstAndSkipsBlank() {
        val history = QueryHistory.empty()
        assertTrue(history.record(q("first")))
        assertTrue(history.record(q("second")))
        assertFalse(history.record(q("   ")))
        assertFalse(history.record(q("")))
        assertEquals(listOf(q("second"), q("first")), history.items)
    }

    @Test
    fun resubmissionMovesToFrontAndTakesTheNewerSwitch() {
        val history = QueryHistory.of(q("a"), q("b"), q("c"))
        assertEquals(listOf(q("a"), q("b"), q("c")), history.items)
        assertTrue(history.record(q("c", regex = true)))
        assertEquals(listOf(q("c", regex = true), q("a"), q("b")), history.items)
        assertFalse(history.record(q(" c ", regex = true)))
        assertEquals(3, history.size)
    }

    @Test
    fun sameTextInAnotherModeIsADistinctEntry() {
        val history = QueryHistory.of(q("x"), q("x", mode = Mode.FIELD))
        assertEquals(2, history.size)
        assertTrue(history.remove(q("x")))
        assertEquals(listOf(q("x", mode = Mode.FIELD)), history.items)
    }

    @Test
    fun capsAtMaxSizeDroppingTheOldest() {
        val history = QueryHistory.empty()
        for (i in 1..QueryHistory.MAX_SIZE + 5) history.record(q("q$i"))
        assertEquals(QueryHistory.MAX_SIZE, history.size)
        assertEquals(q("q${QueryHistory.MAX_SIZE + 5}"), history.items.first())
        assertEquals(q("q6"), history.items.last())
    }

    @Test
    fun removeAndClearReportChanges() {
        val history = QueryHistory.of(q("a"), q("b"))
        assertFalse(history.remove(q("zzz")))
        assertTrue(history.remove(q(" a ")))
        assertEquals(listOf(q("b")), history.items)
        assertTrue(history.clear())
        assertTrue(history.isEmpty)
        assertFalse(history.clear())
    }

    @Test
    fun jsonRoundTripKeepsOrderAndSwitches() {
        val history = QueryHistory.of(q("wlan", regex = true), q("id=btn", mode = Mode.FIELD), q("ok"))
        val decoded = QueryHistory.fromJson(history.toJson())
        assertEquals(history.items, decoded.items)
    }

    @Test
    fun decodingIsTolerant() {
        assertTrue(QueryHistory.fromJson(null).isEmpty)
        assertTrue(QueryHistory.fromJson("").isEmpty)
        assertTrue(QueryHistory.fromJson("not json").isEmpty)
        assertTrue(QueryHistory.fromJson("{\"a\":1}").isEmpty)
        val partial = """[{"mode":"KEYWORD","text":"ok"},{"mode":"NOPE","text":"x"},{"text":"no mode"},null,{"mode":"FIELD","regex":true},{"mode":"KEYWORD","text":" ok ","regex":true}]"""
        val decoded = QueryHistory.fromJson(partial)
        // The first element is the newest, so its switch wins over the older duplicate at the end.
        assertEquals(listOf(q("ok")), decoded.items)
    }
}
