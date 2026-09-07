package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeSelectorFiltersTest {

    private val login = FakeUiNode.root("root") {
        child("a") {
            text = "Login"
            clickable = true
            bounds(0, 0, 540, 100)
        }
    }.find("a")

    @Test
    fun emptySelectorHasNoFiltersAndMatchesNothing() {
        val selector = BridgeSelector()
        assertTrue(selector.toFilters().isEmpty())
        assertSame(BridgeSelector.NOTHING, selector.toFilter())
        assertFalse(selector.toFilter().filter(login))
    }

    @Test
    fun oneFilterPerConditionInProtocolOrder() {
        val selector = BridgeSelector(
            text = "Login",
            clickable = true,
            classNameMatches = Regex("android\\..*"),
            boundsInside = BridgeBounds(0, 0, 1080, 1920),
            depth = 1,
        )
        val filters = selector.toFilters()
        assertEquals(5, filters.size)
        assertTrue(filters[1].toString().startsWith("classNameMatches("))
        assertTrue(filters[3].toString().startsWith("depth") || filters[3].toString().contains("1"))
        assertTrue(filters[4].toString().startsWith("boundsInside("))
    }

    @Test
    fun combinedFilterAgreesWithTheConjunctionOfTheParts() {
        val matching = BridgeSelector(text = "Login", clickable = true, boundsInside = BridgeBounds(0, 0, 1080, 1920))
        assertTrue(matching.toFilter().filter(login))
        assertTrue(matching.toFilters().all { it.filter(login) })

        val failing = BridgeSelector(text = "Login", clickable = false)
        assertFalse(failing.toFilter().filter(login))
        assertEquals(listOf(true, false), failing.toFilters().map { it.filter(login) })
    }
}
