package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.intelligent.IntelligentEvent.ACTIVATE
import org.autojs.autojs.theme.intelligent.IntelligentEvent.APP_START
import org.autojs.autojs.theme.intelligent.IntelligentEvent.EVALUATE
import org.autojs.autojs.theme.intelligent.IntelligentEvent.SCRIPT_NEXT
import org.autojs.autojs.theme.intelligent.StateKeys.DAY
import org.autojs.autojs.theme.intelligent.StateKeys.DEFAULT
import org.autojs.autojs.theme.intelligent.StateKeys.NIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class IntelligentColorPlannerTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val minute = 60_000L

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 10, 6, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    private val t0 = at(15, 51, 20)

    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun dayNight(id: Long, day: Int?, night: Int?, enabled: Boolean = true, position: Int = id.toInt()) = IntelligentGroup(
        id = id,
        enabled = enabled,
        position = position,
        states = buildMap {
            day?.let { put(DAY, StateColors(it)) }
            night?.let { put(NIGHT, StateColors(it)) }
        },
    )

    private fun single(id: Long, color: Int, enabled: Boolean = true, position: Int = id.toInt(), name: String? = null, created: Long = id, modified: Long = id) =
        IntelligentGroup(id, name, enabled, position, created, modified, mapOf(DEFAULT to StateColors(color)))

    private val planner = IntelligentColorPlanner(Random(42))

    private fun plan(
        config: IntelligentConfig,
        groups: List<IntelligentGroup>,
        event: IntelligentEvent,
        runtime: IntelligentRuntimeState = IntelligentRuntimeState(),
        state: String = DEFAULT,
        now: Long = t0,
        override: Long? = null,
        start: Long? = null,
    ) = planner.plan(PlannerInput(config, groups, state, now, event, runtime, override, start, zone))

    @Test
    fun activationUsesTheChosenGroupOrTheFirstAvailable() {
        val config = IntelligentConfig()
        val groups = listOf(single(1, red, enabled = false), single(2, green), single(3, blue))
        assertEquals(3L, plan(config, groups, ACTIVATE, start = 3).groupId)
        assertEquals(2L, plan(config, groups, ACTIVATE, start = 1).groupId)
        assertEquals(2L, plan(config, groups, ACTIVATE).groupId)
        assertFalse(plan(config, groups, ACTIVATE).rotated)
    }

    @Test
    fun stateChangeRotatesFirstThenUsesTheNewState() {
        val config = IntelligentConfig(StateSource.NightMode, listOf(RotationTrigger.StateChange))
        val groups = listOf(dayNight(1, 0xFF111111.toInt(), 0xFF222222.toInt()), dayNight(2, 0xFF333333.toInt(), 0xFF444444.toInt()))

        val activated = plan(config, groups, ACTIVATE, state = DAY)
        assertEquals(1L, activated.groupId)
        assertEquals(0xFF111111.toInt(), activated.color?.primary)

        val toNight = plan(config, groups, EVALUATE, activated.runtime, state = NIGHT)
        assertTrue(toNight.rotated)
        assertEquals(2L, toNight.groupId)
        assertEquals(0xFF444444.toInt(), toNight.color?.primary)

        val stillNight = plan(config, groups, EVALUATE, toNight.runtime, state = NIGHT)
        assertFalse(stillNight.rotated)
        assertEquals(2L, stillNight.groupId)
    }

    @Test
    fun withoutStateChangeTriggerOnlyTheColorFollowsTheState() {
        val config = IntelligentConfig(StateSource.NightMode)
        val groups = listOf(dayNight(1, red, blue), dayNight(2, green, green))
        val activated = plan(config, groups, ACTIVATE, state = DAY)
        val night = plan(config, groups, EVALUATE, activated.runtime, state = NIGHT)
        assertFalse(night.rotated)
        assertEquals(1L, night.groupId)
        assertEquals(blue, night.color?.primary)
    }

    @Test
    fun sequentialOrderFollowsTheSortKeyAndWraps() {
        // Positions deliberately disagree with hue order. zh-CN: 位置与色相顺序故意不一致.
        val groups = listOf(single(1, blue, position = 0), single(2, red, position = 1), single(3, green, position = 2))
        fun cycle(descending: Boolean): List<Long?> {
            val config = IntelligentConfig(rotation = listOf(RotationTrigger.AppStart), order = GroupOrder.Sequential(SortKey.HUE, descending = descending))
            var result = plan(config, groups, ACTIVATE)
            val seen = mutableListOf(result.groupId)
            repeat(3) {
                result = plan(config, groups, APP_START, result.runtime)
                seen += result.groupId
            }
            return seen
        }
        assertEquals(listOf<Long?>(2, 3, 1, 2), cycle(descending = false))
        assertEquals(listOf<Long?>(1, 3, 2, 1), cycle(descending = true))
    }

    @Test
    fun sortKeysOrderGroups() {
        val groups = listOf(
            single(1, 0xFF202020.toInt(), name = "beta", created = 30, modified = 10),
            single(2, 0xFFE0E0E0.toInt(), name = null, created = 10, modified = 30),
            single(3, 0xFF2196F3.toInt(), name = "alpha", created = 20, modified = 20),
        )
        fun order(key: SortKey) = planner.orderedGroups(IntelligentConfig(order = GroupOrder.Sequential(key)), groups).map { it.id }
        assertEquals(listOf(1L, 2L, 3L), order(SortKey.MANUAL))
        assertEquals(listOf(2L, 3L, 1L), order(SortKey.CREATED))
        assertEquals(listOf(1L, 3L, 2L), order(SortKey.MODIFIED))
        // A nameless group sorts by its hex value ("#E0E0E0"), which precedes letters. zh-CN: 无名组按色值 ("#E0E0E0") 排序, 排在字母之前.
        assertEquals(listOf(2L, 3L, 1L), order(SortKey.NAME))
        assertEquals(listOf(1L, 3L, 2L), order(SortKey.LIGHTNESS))
        assertEquals(3L, order(SortKey.CHROMA).last())
    }

    @Test
    fun randomOrderNeverRepeatsTheCurrentGroup() {
        val config = IntelligentConfig(order = GroupOrder.Random)
        val groups = (1L..5L).map { single(it, (0xFF000000 + it * 0x101010).toInt()) }
        var result = plan(config, groups, ACTIVATE)
        val visited = mutableSetOf(result.groupId)
        repeat(200) {
            val next = plan(config, groups, SCRIPT_NEXT, result.runtime)
            assertNotEquals(result.groupId, next.groupId)
            visited += next.groupId
            result = next
        }
        assertEquals(5, visited.size)
    }

    @Test
    fun disabledAndDeletedGroupsAreSkipped() {
        val config = IntelligentConfig(rotation = listOf(RotationTrigger.AppStart))
        val groups = listOf(single(1, red), single(2, green, enabled = false), single(3, blue))
        val activated = plan(config, groups, ACTIVATE)
        assertEquals(3L, plan(config, groups, APP_START, activated.runtime).groupId)

        val deleted = plan(config, groups, EVALUATE, IntelligentRuntimeState(currentGroupId = 99, lastStateKey = DEFAULT))
        assertEquals(1L, deleted.groupId)
        assertFalse(deleted.rotated)

        val disabled = plan(config, groups, EVALUATE, IntelligentRuntimeState(currentGroupId = 2, lastStateKey = DEFAULT))
        assertEquals(3L, disabled.groupId)
    }

    @Test
    fun intervalFiresOnTheGridAndCatchesUpOnlyOnce() {
        val config = IntelligentConfig(rotation = listOf(RotationTrigger.Interval(1, IntervalUnit.MINUTE, IntervalAnchor.NEXT_MINUTE)))
        val groups = listOf(single(1, red), single(2, green), single(3, blue))

        val activated = plan(config, groups, ACTIVATE)
        assertEquals(at(15, 52), activated.runtime.nextDueAt)

        val early = plan(config, groups, EVALUATE, activated.runtime, now = at(15, 51, 59))
        assertFalse(early.rotated)

        val due = plan(config, groups, EVALUATE, early.runtime, now = at(15, 52))
        assertTrue(due.rotated)
        assertEquals(2L, due.groupId)
        assertEquals(at(15, 53), due.runtime.nextDueAt)

        val late = plan(config, groups, EVALUATE, due.runtime, now = at(16, 10, 30))
        assertTrue(late.rotated)
        assertEquals(3L, late.groupId)
        assertEquals(at(16, 11), late.runtime.nextDueAt)
        assertEquals(at(16, 10, 30), late.runtime.lastRotatedAt)
    }

    @Test
    fun appStartRotatesOnlyWhenConfigured() {
        val groups = listOf(single(1, red), single(2, green))
        val plain = IntelligentConfig()
        assertFalse(plan(plain, groups, APP_START, plan(plain, groups, ACTIVATE).runtime).rotated)
        val withAppStart = IntelligentConfig(rotation = listOf(RotationTrigger.AppStart))
        assertTrue(plan(withAppStart, groups, APP_START, plan(withAppStart, groups, ACTIVATE).runtime).rotated)
    }

    @Test
    fun scriptNextAlwaysRotates() {
        val config = IntelligentConfig()
        val groups = listOf(single(1, red), single(2, green))
        val next = plan(config, groups, SCRIPT_NEXT, plan(config, groups, ACTIVATE).runtime)
        assertTrue(next.rotated)
        assertEquals(2L, next.groupId)
    }

    @Test
    fun dateOverrideSuspendsRotationAndRestoresTheGroupAfterwards() {
        val config = IntelligentConfig(
            rotation = listOf(RotationTrigger.Interval(1, IntervalUnit.MINUTE, IntervalAnchor.NEXT_MINUTE)),
            overrides = listOf(DateOverride(DateCondition.DateRange(12, 24, 12, 26), groupId = 4)),
        )
        val groups = listOf(single(1, red), single(2, green), single(3, blue), single(4, 0xFFC62828.toInt(), enabled = false))

        val activated = plan(config, groups, ACTIVATE)
        val entering = plan(config, groups, EVALUATE, activated.runtime, now = at(15, 52), override = 4)
        assertEquals(4L, entering.groupId)
        assertFalse(entering.rotated)
        assertEquals(1L, entering.runtime.currentGroupId)
        assertEquals(at(15, 53), entering.runtime.nextDueAt)

        val during = plan(config, groups, EVALUATE, entering.runtime, now = at(15, 53), override = 4)
        assertEquals(4L, during.groupId)
        assertEquals(1L, during.runtime.currentGroupId)

        val leaving = plan(config, groups, EVALUATE, during.runtime, now = at(15, 54))
        assertEquals(1L, leaving.groupId)
        assertFalse(leaving.rotated)
        assertNull(leaving.runtime.overrideGroupId)

        assertEquals(2L, plan(config, groups, EVALUATE, leaving.runtime, now = at(15, 55)).groupId)
    }

    @Test
    fun missingStateColorsFallBackToTheFirstColoredState() {
        val config = IntelligentConfig(StateSource.NightMode)
        val groups = listOf(dayNight(1, day = null, night = blue))
        assertEquals(blue, plan(config, groups, ACTIVATE, state = DAY).color?.primary)
    }

    @Test
    fun unknownStateKeyFallsBackToTheFirstState() {
        val config = IntelligentConfig(StateSource.NightMode)
        val result = plan(config, listOf(dayNight(1, red, blue)), ACTIVATE, state = "evening")
        assertEquals(DAY, result.stateKey)
        assertEquals(red, result.color?.primary)
    }

    @Test
    fun accentAndDarkArePassedThrough() {
        val group = IntelligentGroup(1, states = mapOf(DEFAULT to StateColors(red, accent = green, dark = blue)))
        assertEquals(EffectiveColor(red, green, blue), plan(IntelligentConfig(), listOf(group), ACTIVATE).color)
    }

    @Test
    fun noAvailableGroupsYieldNoColor() {
        val result = plan(IntelligentConfig(), listOf(single(1, red, enabled = false), IntelligentGroup(2)), ACTIVATE)
        assertNull(result.groupId)
        assertNull(result.color)
    }

    @Test
    fun dailyTimesRotateOnceWhenOverdue() {
        val config = IntelligentConfig(rotation = listOf(RotationTrigger.Daily(listOf(8 * 60, 20 * 60))))
        val groups = listOf(single(1, red), single(2, green), single(3, blue))
        // Activated at 15:51, the next daily time is 20:00 the same day.
        val activated = plan(config, groups, ACTIVATE, start = 1L)
        assertEquals(at(20, 0), activated.runtime.dailyDueAt)
        assertEquals(at(20, 0), activated.runtime.earliestDueAt)
        // Before the time nothing happens.
        val early = plan(config, groups, EVALUATE, activated.runtime, now = at(19, 59))
        assertEquals(1L, early.groupId)
        assertFalse(early.rotated)
        // Long overdue (the process was dead over 20:00 and the next 08:00): only one rotation, re-armed after now.
        val late = plan(config, groups, EVALUATE, activated.runtime, now = at(20, 0) + 13 * 60 * minute)
        assertEquals(2L, late.groupId)
        assertTrue(late.rotated)
        assertEquals(ZonedDateTime.of(2026, 10, 7, 20, 0, 0, 0, zone).toInstant().toEpochMilli(), late.runtime.dailyDueAt)
    }

    @Test
    fun dailyAndIntervalDueTogetherRotateOnce() {
        val config = IntelligentConfig(
            rotation = listOf(RotationTrigger.Interval(1, IntervalUnit.HOUR, IntervalAnchor.NEXT_HOUR), RotationTrigger.Daily(listOf(16 * 60))),
        )
        val groups = listOf(single(1, red), single(2, green), single(3, blue))
        val activated = plan(config, groups, ACTIVATE, start = 1L)
        assertEquals(at(16, 0), activated.runtime.nextDueAt)
        assertEquals(at(16, 0), activated.runtime.dailyDueAt)
        val result = plan(config, groups, EVALUATE, activated.runtime, now = at(16, 0, 1))
        assertEquals(2L, result.groupId)
        assertTrue(result.runtime.earliestDueAt!! > at(16, 0, 1))
    }

}
