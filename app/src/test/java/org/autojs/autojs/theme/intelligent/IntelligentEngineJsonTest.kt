package org.autojs.autojs.theme.intelligent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntelligentEngineJsonTest {

    private val snapshot = IntelligentSnapshot(
        libraryId = 0x0200_0007,
        name = "Warm and cool",
        config = IntelligentConfig(
            stateSource = StateSource.NightMode,
            rotation = listOf(RotationTrigger.StateChange, RotationTrigger.Interval(1, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR)),
            order = GroupOrder.Sequential(SortKey.HUE, StateKeys.DAY),
        ),
        groups = listOf(
            IntelligentGroup(
                id = 11, name = "Orange", position = 0, createdTime = 100, modifiedTime = 200,
                states = mapOf(StateKeys.DAY to StateColors(0xFFFFB74D.toInt()), StateKeys.NIGHT to StateColors(0xFFE65100.toInt(), accent = 0xFFFFD54F.toInt())),
            ),
            IntelligentGroup(id = 12, enabled = false, position = 1, states = mapOf(StateKeys.NIGHT to StateColors(null, dark = 0xFF1B5E20.toInt()))),
        ),
    )

    @Test
    fun snapshotRoundTrips() {
        assertEquals(snapshot, IntelligentEngineJson.decodeSnapshot(IntelligentEngineJson.encodeSnapshot(snapshot)))
    }

    @Test
    fun runtimeStateRoundTrips() {
        val state = IntelligentRuntimeState(
            currentGroupId = 11,
            lastStateKey = StateKeys.NIGHT,
            overrideGroupId = 12,
            intervalAnchorAt = 1_790_000_000_000,
            nextDueAt = 1_790_003_600_000,
            lastRotatedAt = 1_790_000_100_000,
        )
        assertEquals(state, IntelligentEngineJson.decodeRuntime(IntelligentEngineJson.encodeRuntime(state)))
        assertEquals(IntelligentRuntimeState(), IntelligentEngineJson.decodeRuntime(IntelligentEngineJson.encodeRuntime(IntelligentRuntimeState())))
    }

    @Test
    fun malformedInputFallsBack() {
        for (input in listOf(null, "", "nope", "[]", "{\"name\":\"no id\"}")) {
            assertNull("input: $input", IntelligentEngineJson.decodeSnapshot(input))
            assertEquals(IntelligentRuntimeState(), IntelligentEngineJson.decodeRuntime(input))
        }
    }

    @Test
    fun groupsWithoutAnIdAreDroppedAndMissingFieldsDefault() {
        val decoded = IntelligentEngineJson.decodeSnapshot(
            """{"libraryId":256,"groups":[{"name":"no id"},{"id":3,"states":{"default":{"primary":-16711936}}}],"future":1}""",
        )!!
        assertEquals("", decoded.name)
        assertEquals(IntelligentConfig(), decoded.config)
        assertEquals(listOf(IntelligentGroup(id = 3, states = mapOf(StateKeys.DEFAULT to StateColors(0xFF00FF00.toInt())))), decoded.groups)
    }

    @Test
    fun malformedPartsAreSkippedAndGroupsAreFoundById() {
        val decoded = IntelligentEngineJson.decodeSnapshot(
            """{"libraryId":5,"config":"broken","groups":[1,{"id":2,"states":{"day":{"primary":1},"night":"x"}}]}""",
        )!!
        assertEquals(IntelligentConfig(), decoded.config)
        assertEquals(IntelligentGroup(id = 2, states = mapOf(StateKeys.DAY to StateColors(1))), decoded.groups.single())
        assertEquals(decoded.groups.single(), decoded.group(2))
        assertNull(decoded.group(null))
        assertNull(decoded.group(3))
        assertEquals(emptyList<IntelligentGroup>(), IntelligentEngineJson.decodeSnapshot("""{"libraryId":5,"groups":{}}""")!!.groups)
    }

    @Test
    fun dailyDueTimeRoundTripsAndMistypedFieldsAreIgnored() {
        val daily = IntelligentRuntimeState(currentGroupId = 1, dailyDueAt = 1_790_010_000_000)
        assertEquals(daily, IntelligentEngineJson.decodeRuntime(IntelligentEngineJson.encodeRuntime(daily)))
        assertEquals(
            IntelligentRuntimeState(lastStateKey = StateKeys.DAY),
            IntelligentEngineJson.decodeRuntime("""{"currentGroupId":"x","lastStateKey":"day","dailyDueAt":true,"nextDueAt":{}}"""),
        )
    }

}
