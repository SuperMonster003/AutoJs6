package org.autojs.autojs.theme.intelligent

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentConfigJsonTest {

    private fun roundTrip(config: IntelligentConfig) = IntelligentConfigJson.decode(IntelligentConfigJson.encode(config))

    @Test
    fun fullConfigurationRoundTrips() {
        val config = IntelligentConfig(
            stateSource = StateSource.NightMode,
            rotation = listOf(
                RotationTrigger.StateChange,
                RotationTrigger.AppStart,
                RotationTrigger.Interval(2, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR),
            ),
            order = GroupOrder.Sequential(SortKey.HUE, referenceState = StateKeys.NIGHT, descending = true),
            overrides = listOf(
                DateOverride(DateCondition.DateRange(12, 24, 12, 26), groupId = 7),
                DateOverride(DateCondition.DateRange(12, 24, 1, 2, year = 2026), groupId = 8),
            ),
        )
        assertEquals(config, roundTrip(config))
        assertEquals(IntelligentConfig(order = GroupOrder.Random), roundTrip(IntelligentConfig(order = GroupOrder.Random)))
        assertEquals(IntelligentConfig(), roundTrip(IntelligentConfig()))
    }

    @Test
    fun encodedFormIsVersionedAndReadable() {
        val json = JsonParser.parseString(
            IntelligentConfigJson.encode(
                IntelligentConfig(StateSource.NightMode, listOf(RotationTrigger.Interval(1, IntervalUnit.MINUTE, IntervalAnchor.NEXT_MINUTE))),
            ),
        ).asJsonObject
        assertEquals(1, json["version"].asInt)
        assertEquals("nightMode", json["stateSource"].asJsonObject["type"].asString)
        val interval = json["rotation"].asJsonArray[0].asJsonObject
        assertEquals("interval", interval["type"].asString)
        assertEquals("minute", interval["unit"].asString)
        assertEquals("nextMinute", interval["anchor"].asString)
    }

    @Test
    fun blankOrMalformedInputGivesTheDefault() {
        for (input in listOf(null, "", "   ", "not json", "[]", "42", "{\"stateSource\": 3}")) {
            assertEquals("input: $input", IntelligentConfig(), IntelligentConfigJson.decode(input))
        }
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val config = IntelligentConfigJson.decode("""{"version":1,"stateSource":{"type":"nightMode","extra":1},"future":true}""")
        assertEquals(StateSource.NightMode, config.stateSource)
        assertFalse(config.hasUnsupportedItems)
    }

    @Test
    fun unknownTypesSurviveARoundTrip() {
        val source = """{"type":"weekday","mode":"weekend"}"""
        val trigger = """{"type":"daily","times":["08:00","20:00"]}"""
        val condition = """{"type":"holiday","id":"spring-festival"}"""
        val input = """{"version":2,"stateSource":$source,"rotation":[$trigger],"overrides":[{"when":$condition,"group":3}]}"""

        val config = IntelligentConfigJson.decode(input)
        assertTrue(config.hasUnsupportedItems)
        assertEquals("weekday", (config.stateSource as StateSource.Unsupported).type)
        assertEquals("daily", (config.rotation.single() as RotationTrigger.Unsupported).type)
        assertEquals(3L, config.overrides.single().groupId)

        val output = JsonParser.parseString(IntelligentConfigJson.encode(config)).asJsonObject
        assertEquals(JsonParser.parseString(source), output["stateSource"])
        assertEquals(JsonParser.parseString(trigger), output["rotation"].asJsonArray[0])
        assertEquals(JsonParser.parseString(condition), output["overrides"].asJsonArray[0].asJsonObject["when"])
    }

    @Test
    fun invalidValuesAreRepairedOrMarkedUnsupported() {
        val config = IntelligentConfigJson.decode(
            """
            {
              "rotation": [
                {"type": "interval", "every": -3, "unit": "minute", "anchor": "soon"},
                {"type": "interval", "every": 1, "unit": "fortnight"}
              ],
              "order": {"mode": "sequential", "key": "bogus"},
              "overrides": [
                {"when": {"type": "dateRange", "from": "13-01", "to": "12-31"}, "group": 1},
                {"when": {"type": "dateRange", "from": "02-29", "to": "03-01"}},
                {"when": {"type": "dateRange", "from": "02-29", "to": "03-01"}, "group": 2}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(RotationTrigger.Interval(1, IntervalUnit.MINUTE, IntervalAnchor.NOW), config.rotation[0])
        assertTrue(config.rotation[1] is RotationTrigger.Unsupported)
        assertEquals(GroupOrder.Sequential(SortKey.MANUAL), config.order)
        assertEquals(2, config.overrides.size)
        assertTrue(config.overrides[0].condition is DateCondition.Unsupported)
        assertEquals(DateCondition.DateRange(2, 29, 3, 1), config.overrides[1].condition)
    }

}
