package org.autojs.autojs.theme.intelligent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State keys are stored with group colors and in snapshots, so they are a persistence contract (roadmap 4.1);
 * this also covers the normalization helpers and the color fallback of groups (roadmap P7.1).
 * zh-CN: 状态 key 与组颜色及快照一同保存, 属于持久化约定 (Roadmap 4.1); 同时覆盖规范化函数与组的颜色回退 (Roadmap P7.1).
 */
class IntelligentConfigModelTest {

    @Test
    fun stateKeysAreStable() {
        assertEquals(listOf("default"), StateSource.None.stateKeys)
        assertEquals(listOf("day", "night"), StateSource.NightMode.stateKeys)
        assertEquals(listOf("period:0", "period:1", "period:2"), StateSource.TimePeriods(listOf(360, 720, 1080)).stateKeys)
        assertEquals(listOf("day", "night"), StateSource.SunriseSunset().stateKeys)
        assertEquals((1..7).map { "weekday:$it" }, StateSource.Weekday().stateKeys)
        assertEquals(listOf("workday", "weekend"), StateSource.Weekday(workdaysOnly = true).stateKeys)
        assertEquals(listOf("season:spring", "season:summer", "season:autumn", "season:winter"), StateSource.Seasons().stateKeys)
        assertEquals(listOf("battery:0", "battery:1", "battery:2"), StateSource.Battery(listOf(20, 60)).stateKeys)
        for (source in listOf(StateSource.Charging, StateSource.PowerSave, StateSource.AccessibilityService, StateSource.ScriptRunning)) {
            assertEquals(listOf("off", "on"), source.stateKeys)
        }
        assertEquals(listOf("script:0", "script:1"), StateSource.ScriptStates(listOf("idle", "busy")).stateKeys)
        assertEquals(listOf("default"), StateSource.Unsupported("future", "{}").stateKeys)
    }

    @Test
    fun normalizationSortsDeduplicatesAndRejectsTooFew() {
        assertEquals(StateSource.TimePeriods(listOf(0, 360, 1439)), StateSource.TimePeriods.normalized(listOf(1439, 360, 0, 360, -1, 1440)))
        assertNull(StateSource.TimePeriods.normalized(listOf(360, 360)))
        assertEquals((0 until 6).map { it * 60 }, StateSource.TimePeriods.normalized((0 until 8).map { it * 60 })!!.starts)

        assertEquals(StateSource.Battery(listOf(10, 50)), StateSource.Battery.normalized(listOf(50, 10, 0, 100, 50)))
        assertNull(StateSource.Battery.normalized(listOf(0, 100)))
        assertEquals(StateSource.Battery.MAX_THRESHOLDS, StateSource.Battery.normalized(listOf(10, 20, 30, 40, 50))!!.thresholds.size)

        assertEquals(RotationTrigger.Daily(listOf(480, 1200)), RotationTrigger.Daily.normalized(listOf(1200, 480, 1200, 1440)))
        assertNull(RotationTrigger.Daily.normalized(listOf(-5)))

        val long = "x".repeat(StateSource.ScriptStates.MAX_NAME_LENGTH + 1)
        assertEquals(StateSource.ScriptStates(listOf("idle", "busy")), StateSource.ScriptStates.normalized(listOf(" idle ", "busy", "idle", "", long)))
        assertNull(StateSource.ScriptStates.normalized(listOf("idle", " idle")))
        assertEquals(StateSource.ScriptStates.MAX_STATES, StateSource.ScriptStates.normalized((1..10).map { "s$it" })!!.names.size)
    }

    @Test
    fun groupColorsFallBackThroughTheStateOrderThenAnyColoredState() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val group = IntelligentGroup(1, states = mapOf(StateKeys.NIGHT to StateColors(blue), StateKeys.DAY to StateColors(null, accent = red)))
        val nightMode = StateSource.NightMode.stateKeys
        assertEquals(EffectiveColor(blue), group.resolve(StateKeys.DAY, nightMode))
        // After switching to a source whose keys the group lacks, any colored state still applies.
        // zh-CN: 切换到组中没有对应 key 的状态源后, 仍使用任一有颜色的状态.
        assertEquals(EffectiveColor(blue), group.resolve("battery:0", StateSource.Battery(listOf(20)).stateKeys))
        assertTrue(group.hasColor)
        val colorless = IntelligentGroup(2, states = mapOf(StateKeys.DAY to StateColors(null, dark = blue)))
        assertFalse(colorless.hasColor)
        assertNull(colorless.resolve(StateKeys.DAY, nightMode))
    }

    @Test
    fun unsupportedItemsAreReported() {
        assertFalse(IntelligentConfig().hasUnsupportedItems)
        assertTrue(IntelligentConfig(StateSource.Unsupported("future", "{}")).hasUnsupportedItems)
        assertTrue(IntelligentConfig(rotation = listOf(RotationTrigger.Unsupported("future", "{}"))).hasUnsupportedItems)
        assertTrue(IntelligentConfig(overrides = listOf(DateOverride(DateCondition.Unsupported("future", "{}"), 1))).hasUnsupportedItems)
    }

}
