package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginVersionSummaryFormatterTest {

    @Test
    fun `September aliases from Android and desktop JDKs share an ISO date`() {
        listOf(
            "Sep 13, 2026", "Sept 13, 2026", "September 13, 2026",
            "SEPT 13, 2026", "Sept 3, 2026", "13 Sept 2026", "Sept 13 2026",
        ).forEach { input ->
            val expectedDate = if (input == "Sept 3, 2026") "2026-09-03" else "2026-09-13"
            assertEquals(input, "1.0.0 (27) | $expectedDate", PluginVersionSummaryFormatter.format("1.0.0", 27L, input))
        }
        assertEquals("1.0.0 | Sept someday", PluginVersionSummaryFormatter.format("1.0.0", null, "Sept someday"))
        assertEquals("1.0.0 | Sept 31, 2026", PluginVersionSummaryFormatter.format("1.0.0", null, "Sept 31, 2026"))
    }

    @Test
    fun `supported dates are normalized without changing the existing precedence`() {
        val cases = linkedMapOf(
            "Nov 21, 2025" to "2025-11-21",
            "2025-11-21" to "2025-11-21",
            "2025/11/21" to "2025-11-21",
            "21/11/2025" to "2025-11-21",
            "11/21/2025" to "2025-11-21",
            "2025.11.21" to "2025-11-21",
            "21.11.2025" to "2025-11-21",
            "20251121" to "2025-11-21",
            "2025-11-21 08:09:10" to "2025-11-21",
            "2025/11/21 08:09:10" to "2025-11-21",
            "21 Nov 2025" to "2025-11-21",
            "Nov 21 2025" to "2025-11-21",
        )

        cases.forEach { (input, expectedDate) ->
            assertEquals(
                "v1.2.3 (17) | $expectedDate",
                PluginVersionSummaryFormatter.format("v1.2.3", 17L, input),
            )
        }
    }

    @Test
    fun `ISO 8601 date-times keep the calendar date of their own offset`() {
        val cases = linkedMapOf(
            "2025-11-21T08:09:10Z" to "2025-11-21",
            "2025-11-21T23:30:00-05:00" to "2025-11-21",
            "2025-11-21T00:30:00+08:00" to "2025-11-21",
            "2025-11-21T08:09:10.123+08:00" to "2025-11-21",
            "2025-11-21T08:09" to "2025-11-21",
            "2025-11-21 08:09" to "2025-11-21",
            "2025/11/21 08:09" to "2025-11-21",
        )

        cases.forEach { (input, expectedDate) ->
            assertEquals(
                input,
                "v1.2.3 (17) | $expectedDate",
                PluginVersionSummaryFormatter.format("v1.2.3", 17L, input),
            )
        }
    }

    @Test
    fun `unknown date remains visible verbatim`() {
        assertEquals(
            "v1.2.3 (17) | someday",
            PluginVersionSummaryFormatter.format("v1.2.3", 17L, "  someday  "),
        )
    }

    @Test
    fun `missing date and nonpositive code are omitted`() {
        assertEquals("v1.2.3", PluginVersionSummaryFormatter.format("v1.2.3", 0L, "  "))
        assertEquals("v1.2.3", PluginVersionSummaryFormatter.format("v1.2.3", null, null))
    }
}
