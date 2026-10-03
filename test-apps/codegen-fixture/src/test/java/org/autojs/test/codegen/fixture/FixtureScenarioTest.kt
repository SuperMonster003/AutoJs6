package org.autojs.test.codegen.fixture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureScenarioTest {
    @Test fun allFamiliesRetainSixTruthTargetsAcrossEveryMutation() {
        FixtureScenario.SCREENS.forEach { screen ->
            repeat(48) { seed ->
                val original = FixtureScenario.create(screen, seed.toLong(), emptyList())
                assertEquals(6, original.targetKeys.size)
                FixtureScenario.MUTATIONS.forEach { mutation ->
                    val changed = FixtureScenario.create(screen, seed.toLong(), listOf(mutation))
                    assertEquals(original.targetKeys, changed.targetKeys)
                    assertEquals(changed, FixtureScenario.create(screen, seed.toLong(), listOf(mutation)))
                    assertEquals(changed.defaultStateId, FixtureScenario.create(screen, seed.toLong(), listOf(mutation)).defaultStateId)
                }
            }
        }
    }

    @Test fun orderedMutationSequencesAndRepeatedDeletesRemainDeterministic() {
        val mutations = listOf("shuffle", "insert", "delete", "wrap", "locale", "retext", "sibling", "rename-ids")
        FixtureScenario.SCREENS.forEach { screen ->
            val original = FixtureScenario.create(screen, Long.MIN_VALUE, emptyList())
            val changed = FixtureScenario.create(screen, Long.MIN_VALUE, mutations)
            assertEquals(original.targetKeys, changed.targetKeys)
            assertEquals(changed, FixtureScenario.create(screen, Long.MIN_VALUE, mutations))
            assertNotEquals(changed.defaultStateId, original.defaultStateId)
            val exhausted = FixtureScenario.create(screen, 0, listOf("delete", "delete", "delete"))
            assertEquals(listOf("delete"), exhausted.skippedMutations)
            assertEquals(6, exhausted.rows.size)
        }
    }

    @Test fun noOpIdMutationsAreRecordedWithoutClaimingAppliedChanges() {
        val scenario = FixtureScenario.create("no-id", 12, listOf("strip-ids", "rename-ids"))
        assertTrue(scenario.stripIds)
        assertFalse(scenario.renameIds)
        assertTrue(scenario.appliedMutations.isEmpty())
        assertEquals(listOf("strip-ids", "rename-ids"), scenario.skippedMutations)
    }

    @Test fun shuffleAlwaysChangesOrderAndDynamicTextAlwaysChanges() {
        repeat(100) { seed ->
            val original = FixtureScenario.create("list", seed.toLong(), emptyList())
            val shuffled = FixtureScenario.create("list", seed.toLong(), listOf("shuffle"))
            assertNotEquals(original.rows, shuffled.rows)
            val retext = FixtureScenario.create("list", seed.toLong(), listOf("retext"))
            repeat(6) { assertNotEquals(original.dynamicNumber(it), retext.dynamicNumber(it)) }
        }
    }

    @Test fun invalidLaunchInputsAreRejected() {
        listOf("../escape", "", "/absolute", "x/y", "x\\y", "a".repeat(97)).forEach { value ->
            rejected { FixtureScenario.safeStateId(value) }
        }
        rejected { FixtureScenario.create("unknown", 0, emptyList()) }
        rejected { FixtureScenario.create("login", 0, listOf("unknown")) }
        rejected { FixtureScenario.create("login", 0, List(33) { "retext" }) }
    }

    private fun rejected(block: () -> Unit) {
        try { block(); throw AssertionError("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
}
