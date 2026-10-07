package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.junit.Assert.*
import org.junit.Test

class ComposeV2CallbacksTest {
    @Test fun optionalDateBecomesNullAndNumbersKeepTheirExactPositionalOrder() {
        assertArrayEquals(arrayOf<Any?>(null), ComposeV2Callbacks.arguments("dateChange", emptyMap()))
        assertArrayEquals(arrayOf<Any?>(0L), ComposeV2Callbacks.arguments("dateChange", mapOf("selectedDateMillis" to 0L)))
        assertArrayEquals(arrayOf<Any?>(23, 59), ComposeV2Callbacks.arguments("timeChange", mapOf("minute" to 59, "hour" to 23)))
        assertArrayEquals(arrayOf<Any?>(4), ComposeV2Callbacks.arguments("pageChange", mapOf("page" to 4)))
    }
    @Test fun controlledBooleansModesAndSearchTextAreNotCoerced() {
        for ((event, key) in listOf("openChange" to "open", "selectedChange" to "selected", "expandedChange" to "expanded")) {
            assertArrayEquals(arrayOf<Any?>(false), ComposeV2Callbacks.arguments(event, mapOf(key to false)))
            assertThrows(ComposeUiContractException::class.java) { ComposeV2Callbacks.arguments(event, mapOf(key to 0)) }
        }
        assertArrayEquals(arrayOf<Any?>("input"), ComposeV2Callbacks.arguments("displayModeChange", mapOf("displayMode" to "input")))
        assertArrayEquals(arrayOf<Any?>("needle"), ComposeV2Callbacks.arguments("queryChange", mapOf("text" to "needle")))
        assertArrayEquals(arrayOf<Any?>("needle"), ComposeV2Callbacks.arguments("search", mapOf("text" to "needle")))
        assertArrayEquals(emptyArray<Any?>(), ComposeV2Callbacks.arguments("refresh", emptyMap()))
        assertNull(ComposeV2Callbacks.arguments("click", emptyMap()))
    }
    @Test fun invalidDatesTimesPagesAndModesNeverReachUserCallbacks() {
        for ((event, payload) in listOf(
            "dateChange" to mapOf("selectedDateMillis" to null),
            "dateChange" to mapOf("selectedDateMillis" to 1L),
            "timeChange" to mapOf("hour" to 24, "minute" to 0),
            "timeChange" to mapOf("hour" to 0, "minute" to 60),
            "timeChange" to mapOf("hour" to 0),
            "pageChange" to mapOf("page" to -1),
            "pageChange" to mapOf("page" to Double.NaN),
            "displayModeChange" to mapOf("displayMode" to "other"),
            "queryChange" to mapOf("text" to 2),
        )) assertThrows(ComposeUiContractException::class.java) { ComposeV2Callbacks.arguments(event, payload) }
    }
}
