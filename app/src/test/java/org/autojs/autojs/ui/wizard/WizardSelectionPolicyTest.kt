package org.autojs.autojs.ui.wizard

import org.autojs.autojs.ui.wizard.WizardSelectionPolicy.Candidate
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy.CheckBoxState
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy.NameValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardSelectionPolicyTest {

    private val candidates = listOf(
        Candidate("editor", inStandard = true, inMinimal = true, selectable = true),
        Candidate("docs", inStandard = true, inMinimal = true, selectable = false, done = true),
        Candidate("opencv", inStandard = true, inMinimal = false, selectable = true),
        Candidate("lua", inStandard = false, inMinimal = false, selectable = true),
        Candidate("broken", inStandard = false, inMinimal = false, selectable = false),
        Candidate("player", inStandard = false, inMinimal = false, selectable = false, done = true),
    )

    @Test
    fun builtInProfilesResolveFromMarkersAndSkipUnselectable() {
        assertEquals(listOf("editor", "opencv"), WizardSelectionPolicy.resolveSelection(WizardProfile.STANDARD, candidates).toList())
        assertEquals(listOf("editor"), WizardSelectionPolicy.resolveSelection(WizardProfile.MINIMAL, candidates).toList())
        assertEquals(listOf("editor", "opencv", "lua"), WizardSelectionPolicy.resolveSelection(WizardProfile.ALL, candidates).toList())
    }

    @Test
    fun customProfilesResolveFromTheirMemberSet() {
        val profile = WizardProfile.custom("mine", setOf("lua", "docs", "missing"), id = "custom-1")
        assertEquals(listOf("lua"), WizardSelectionPolicy.resolveSelection(profile, candidates).toList())
    }

    @Test
    fun membershipIgnoresSelectability() {
        val docs = candidates.first { it.id == "docs" }
        val player = candidates.first { it.id == "player" }
        assertTrue(WizardSelectionPolicy.isMember(WizardProfile.STANDARD, docs))
        assertFalse(WizardSelectionPolicy.isMember(WizardProfile.STANDARD, player))
        assertTrue(WizardSelectionPolicy.isMember(WizardProfile.ALL, player))
    }

    @Test
    fun doneEntriesShowADisabledTickInTheirProfileAndNoCheckBoxOtherwise() {
        assertEquals(CheckBoxState.DONE_IN_PROFILE, WizardSelectionPolicy.checkBoxState(isDone = true, isSelectable = false, isMemberOfProfile = true))
        assertEquals(CheckBoxState.HIDDEN, WizardSelectionPolicy.checkBoxState(isDone = true, isSelectable = false, isMemberOfProfile = false))
        assertEquals(CheckBoxState.SELECTABLE, WizardSelectionPolicy.checkBoxState(isDone = false, isSelectable = true, isMemberOfProfile = false))
        assertEquals(CheckBoxState.UNAVAILABLE, WizardSelectionPolicy.checkBoxState(isDone = false, isSelectable = false, isMemberOfProfile = true))
    }

    @Test
    fun doneRowsDisappearOnlyWhileTheyAreHidden() {
        assertTrue(WizardSelectionPolicy.isRowVisible(isDone = true, showDone = true))
        assertFalse(WizardSelectionPolicy.isRowVisible(isDone = true, showDone = false))
        assertTrue(WizardSelectionPolicy.isRowVisible(isDone = false, showDone = false))
    }

    @Test
    fun savedMembersKeepTickedAndDoneMembersInCandidateOrder() {
        val saved = WizardSelectionPolicy.membersToSave(WizardProfile.STANDARD, candidates, selectedIds = setOf("lua", "editor", "broken"))
        assertEquals(listOf("editor", "docs", "lua"), saved.toList())
    }

    @Test
    fun savedCustomMembersKeepEntriesUnknownToTheList() {
        val profile = WizardProfile.custom("mine", linkedSetOf("player", "gone.pkg"), id = "custom-1")
        val saved = WizardSelectionPolicy.membersToSave(profile, candidates, selectedIds = setOf("opencv"))
        assertEquals(listOf("opencv", "player", "gone.pkg"), saved.toList())
    }

    @Test
    fun customNamesMustBeNonBlankAndUnique() {
        val existing = listOf(WizardProfile.STANDARD, WizardProfile.custom("Work", setOf("a"), id = "custom-1"))
        assertEquals(NameValidation.BLANK, WizardSelectionPolicy.validateCustomName("   ", existing))
        assertEquals(NameValidation.DUPLICATE, WizardSelectionPolicy.validateCustomName(" work ", existing))
        assertEquals(NameValidation.OK, WizardSelectionPolicy.validateCustomName("work", existing, excludingId = "custom-1"))
        assertEquals(NameValidation.OK, WizardSelectionPolicy.validateCustomName("Home", existing))
    }

}
