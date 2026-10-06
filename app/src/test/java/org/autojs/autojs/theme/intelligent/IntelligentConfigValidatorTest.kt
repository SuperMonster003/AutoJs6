package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.intelligent.IntelligentConfigValidator.Issue
import org.autojs.autojs.theme.intelligent.StateKeys.DAY
import org.autojs.autojs.theme.intelligent.StateKeys.DEFAULT
import org.autojs.autojs.theme.intelligent.StateKeys.NIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentConfigValidatorTest {

    private fun single(id: Long, enabled: Boolean = true) =
        IntelligentGroup(id, enabled = enabled, states = mapOf(DEFAULT to StateColors(0xFF000000.toInt() + id.toInt())))

    private fun validate(config: IntelligentConfig, groups: List<IntelligentGroup>) = IntelligentConfigValidator.validate(config, groups)

    @Test
    fun noAvailableGroupsBlocksActivation() {
        val empty = validate(IntelligentConfig(), emptyList())
        assertEquals(setOf(Issue.NO_AVAILABLE_GROUPS), empty.issues)
        assertFalse(empty.canActivate)

        val allDisabled = validate(IntelligentConfig(), listOf(single(1, enabled = false)))
        assertFalse(allDisabled.canActivate)
    }

    @Test
    fun aValidOverrideAloneAllowsActivation() {
        val config = IntelligentConfig(overrides = listOf(DateOverride(DateCondition.DateRange(12, 24, 12, 26), groupId = 1)))
        val result = validate(config, listOf(single(1, enabled = false)))
        assertTrue(result.canActivate)
        assertFalse(Issue.NOT_INTELLIGENT in result.issues)
    }

    @Test
    fun oneStateAndOneGroupIsNotIntelligent() {
        val result = validate(IntelligentConfig(rotation = listOf(RotationTrigger.AppStart)), listOf(single(1), single(2, enabled = false)))
        assertEquals(setOf(Issue.NOT_INTELLIGENT, Issue.ROTATION_WITHOUT_EFFECT), result.issues)
        assertTrue(result.canActivate)
    }

    @Test
    fun twoStatesWithOneGroupIsIntelligentButMayMissColors() {
        val group = IntelligentGroup(1, states = mapOf(DAY to StateColors(0xFFE0F2F1.toInt())))
        val result = validate(IntelligentConfig(StateSource.NightMode), listOf(group))
        assertEquals(setOf(Issue.MISSING_STATE_COLORS), result.issues)
        assertEquals(mapOf(1L to listOf(NIGHT)), result.groupsMissingStates)
    }

    @Test
    fun stateChangeWithASingleStateHasNoEffect() {
        val result = validate(IntelligentConfig(rotation = listOf(RotationTrigger.StateChange)), listOf(single(1), single(2), single(3)))
        assertEquals(setOf(Issue.ROTATION_WITHOUT_EFFECT), result.issues)
    }

    @Test
    fun enabledGroupsWithoutColorAreReported() {
        val result = validate(IntelligentConfig(), listOf(single(1), single(2), IntelligentGroup(3), IntelligentGroup(4, enabled = false)))
        assertEquals(setOf(Issue.GROUPS_WITHOUT_COLOR), result.issues)
        assertEquals(listOf(3L), result.groupsWithoutColor)
    }

    @Test
    fun intervalsShorterThanAMinuteAreFlagged() {
        val groups = listOf(single(1), single(2))
        val short = IntelligentConfig(rotation = listOf(RotationTrigger.Interval(30, IntervalUnit.SECOND)))
        assertEquals(setOf(Issue.SHORT_INTERVAL), validate(short, groups).issues)
        val minute = IntelligentConfig(rotation = listOf(RotationTrigger.Interval(1, IntervalUnit.MINUTE)))
        assertTrue(validate(minute, groups).issues.isEmpty())
    }

    @Test
    fun unsupportedItemsAndMissingOverrideTargetsAreReported() {
        val config = IntelligentConfig(
            rotation = listOf(RotationTrigger.Unsupported("daily", "{}")),
            overrides = listOf(DateOverride(DateCondition.DateRange(1, 1, 1, 1), groupId = 42)),
        )
        val result = validate(config, listOf(single(1), single(2)))
        assertEquals(setOf(Issue.UNSUPPORTED_ITEMS, Issue.MISSING_OVERRIDE_TARGET), result.issues)
    }

}
