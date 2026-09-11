package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginVersionSummaryFormatterTest {

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
