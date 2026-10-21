package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test

class RuntimeFlowGesturesTest {
    private val a = listOf(12, 34)
    private val b = listOf(56, 78)
    private fun parse(name: String, vararg args: Any?) = RuntimeFlowGestures.parse(name, args.toList(), 100)

    @Test fun pressFormsKeepThePlatformDefaultAndExplicitDuration() {
        val expected = RuntimeFlowGestures.Stroke(0, 100, listOf(12 to 34))
        assertEquals(listOf(expected), parse("press", a))
        assertEquals(listOf(expected), parse("press", 12, 34))
        for (args in listOf(listOf(a, 250), listOf(250, a), listOf(12, 34, 250))) {
            assertEquals(listOf(expected.copy(duration = 250)), RuntimeFlowGestures.parse("press", args, 100))
        }
    }
    @Test fun allSevenSwipeFormsProduceTheSamePath() {
        val flat = listOf(12, 34, 56, 78)
        val group = listOf(a, b)
        val forms = listOf(listOf(12, 34, 56, 78, 250), listOf(a, b, 250), listOf(group, 250), listOf(flat, 250), listOf(250, a, b), listOf(250, group), listOf(250, flat))
        for (args in forms) assertEquals(listOf(RuntimeFlowGestures.Stroke(0, 250, listOf(12 to 34, 56 to 78))), RuntimeFlowGestures.parse("swipe", args, 100))
    }
    @Test fun gestureFormsAndDelayedSimultaneousStrokes() {
        assertEquals(parse("gesture", 300, a, b), parse("gesture", 300, listOf(a, b)))
        assertEquals(parse("gesture", 300, a, b), parse("gesture", 300, listOf(12, 34, 56, 78)))
        assertEquals(listOf(RuntimeFlowGestures.Stroke(0, 300, listOf(12 to 34, 56 to 78)), RuntimeFlowGestures.Stroke(40, 200, listOf(56 to 78, 12 to 34))),
            parse("gestures", listOf(300, a, b), listOf(40, 200, b, a)))
    }
    @Test fun malformedPathsFailBeforeAnyDispatch() {
        val invalid = listOf("press" to listOf(10), "press" to listOf(a, 0), "press" to listOf(a, Double.NaN),
            "swipe" to listOf(listOf(1, 2, 3), 100), "swipe" to listOf(listOf(a, b, a), 100),
            "gesture" to listOf(200, emptyList<Int>()), "gesture" to listOf(200, listOf(Double.POSITIVE_INFINITY, 10)),
            "gestures" to listOf(listOf(-1, 100, a)), "gestures" to listOf(listOf(10, 100)))
        for ((name, args) in invalid) {
            try { RuntimeFlowGestures.parse(name, args, 100); fail("Accepted $name $args") } catch (_: IllegalArgumentException) { }
        }
    }
}
