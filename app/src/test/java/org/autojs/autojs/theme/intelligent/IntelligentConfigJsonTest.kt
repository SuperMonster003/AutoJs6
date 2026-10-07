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
        // Known types with values this version cannot read count as unsupported too.
        val source = """{"type":"weekday","mode":"weekend"}"""
        val trigger = """{"type":"onUnlock","delay":5}"""
        val condition = """{"type":"holiday","id":"spring-festival"}"""
        val input = """{"version":2,"stateSource":$source,"rotation":[$trigger],"overrides":[{"when":$condition,"group":3}]}"""

        val config = IntelligentConfigJson.decode(input)
        assertTrue(config.hasUnsupportedItems)
        assertEquals("weekday", (config.stateSource as StateSource.Unsupported).type)
        assertEquals("onUnlock", (config.rotation.single() as RotationTrigger.Unsupported).type)
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

    @Test
    fun intervalCountsAreClampedToTheEditorRange() {
        val config = IntelligentConfigJson.decode(
            """{"version":1,"rotation":[
                {"type":"interval","every":1,"unit":"second"},
                {"type":"interval","every":0,"unit":"minute"},
                {"type":"interval","every":100000,"unit":"hour"}]}"""
        )
        val intervals = config.rotation.filterIsInstance<RotationTrigger.Interval>()
        // 5 seconds is the lower bound (D19); minutes and hours start at 1; 9999 is the upper bound.
        assertEquals(listOf(5, 1, 9999), intervals.map { it.every })
        assertEquals(listOf(5, 1, 1), IntervalUnit.entries.map { it.minimumEvery })
    }

    @Test
    fun conditionTypesRoundTrip() {
        val sources = listOf(
            StateSource.TimePeriods(listOf(6 * 60, 12 * 60 + 30, 18 * 60)),
            StateSource.SunriseSunset(),
            StateSource.SunriseSunset(GeoLocation(39.9, 116.4)),
            StateSource.Weekday(workdaysOnly = false),
            StateSource.Weekday(workdaysOnly = true),
            StateSource.Seasons(),
            StateSource.Seasons(Hemisphere.SOUTH),
            StateSource.Battery(listOf(20, 80)),
            StateSource.Charging,
            StateSource.PowerSave,
            StateSource.AccessibilityService,
            StateSource.ScriptRunning,
            StateSource.ScriptStates(listOf("idle", "busy", "error")),
        )
        sources.forEach { source ->
            val config = IntelligentConfig(
                stateSource = source,
                rotation = listOf(RotationTrigger.StateChange, RotationTrigger.ScriptStart, RotationTrigger.Daily(listOf(8 * 60, 20 * 60))),
                overrides = listOf(
                    DateOverride(DateCondition.Holiday("christmas", daysBefore = 1, daysAfter = 1), 3L),
                    DateOverride(DateCondition.Holiday("springFestival"), 2L),
                ),
            )
            assertEquals(config, roundTrip(config))
        }
        val encoded = JsonParser.parseString(IntelligentConfigJson.encode(IntelligentConfig(StateSource.TimePeriods(listOf(6 * 60, 18 * 60))))).asJsonObject
        assertEquals("""{"type":"timePeriods","starts":["06:00","18:00"]}""", encoded["stateSource"].toString())
    }

    @Test
    fun scriptStatesAreNormalized() {
        val repaired = IntelligentConfigJson.decode("""{"version":1,"stateSource":{"type":"scriptStates","names":[" a ","b","a","",3,"c"]}}""")
        assertEquals(StateSource.ScriptStates(listOf("a", "b", "c")), repaired.stateSource)
        assertEquals(listOf("script:0", "script:1", "script:2"), repaired.stateSource.stateKeys)
        // Fewer than two names keep the raw data. zh-CN: 不足两个名称时保留原始数据.
        val single = IntelligentConfigJson.decode("""{"version":1,"stateSource":{"type":"scriptStates","names":["only"]}}""")
        assertTrue(single.stateSource is StateSource.Unsupported)
        assertEquals(8, StateSource.ScriptStates.normalized((1..10).map { "s$it" })!!.names.size)
    }

    @Test
    fun invalidConditionValuesAreRepairedOrUnsupported() {
        val config = IntelligentConfigJson.decode(
            """{"version":1,
                "stateSource":{"type":"timePeriods","starts":["18:00","06:00","06:00","25:00","x"]},
                "rotation":[{"type":"daily","times":["20:00","08:00","bad"]},{"type":"daily","times":[]}],
                "overrides":[
                  {"when":{"type":"holiday","id":"christmas","before":-3,"after":99},"group":1},
                  {"when":{"type":"holiday","id":"unknownDay"},"group":2}]}"""
        )
        assertEquals(StateSource.TimePeriods(listOf(6 * 60, 18 * 60)), config.stateSource)
        assertEquals(RotationTrigger.Daily(listOf(8 * 60, 20 * 60)), config.rotation[0])
        assertTrue(config.rotation[1] is RotationTrigger.Unsupported)
        assertEquals(DateCondition.Holiday("christmas", 0, DateCondition.Holiday.MAX_EXTRA_DAYS), config.overrides[0].condition)
        assertTrue(config.overrides[1].condition is DateCondition.Unsupported)

        fun source(json: String) = IntelligentConfigJson.decode("""{"version":1,"stateSource":$json}""").stateSource
        assertTrue(source("""{"type":"timePeriods","starts":["06:00"]}""") is StateSource.Unsupported)
        assertEquals(StateSource.Battery(listOf(20, 80)), source("""{"type":"battery","thresholds":[80,0,20,150,20]}"""))
        assertTrue(source("""{"type":"battery","thresholds":[0,100]}""") is StateSource.Unsupported)
        assertTrue(source("""{"type":"sunriseSunset","latitude":120,"longitude":0}""") is StateSource.Unsupported)
        assertTrue(source("""{"type":"weekday","mode":"fortnight"}""") is StateSource.Unsupported)
        assertEquals(StateSource.Seasons(null), source("""{"type":"season","hemisphere":"east"}"""))
    }

}
