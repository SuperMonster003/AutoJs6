package org.autojs.autojs.external.shortcut

import org.autojs.autojs.external.shortcut.ShortcutBackupEntry.Status
import org.junit.Assert.*
import org.junit.Test

class ShortcutBackupPolicyTest {
    private fun entry(id: String, status: Status = Status.PINNED) = ShortcutBackupEntry(
        id, "Same script", "Same script", "script", "/sdcard/scripts/test.js", icon = byteArrayOf(1), status = status,
    )

    private fun state(vararg entries: ShortcutBackupEntry) = ShortcutBackupState(entries.toList(), "generation", "home", "activity")

    @Test fun removingOneOfSeveralCopiesOnlyRetiresThatInstance() {
        val result = ShortcutBackupPolicy.reconcile(state(entry("one"), entry("two")), setOf("two"), true, true)
        assertEquals(listOf(Status.REMOVED, Status.PINNED), result.entries.map { it.status })
        assertEquals(listOf("one", "two"), result.entries.map { it.id })
    }

    @Test fun removingAllIconsDoesNotLookLikeFreezeWhenMarkerSurvives() {
        val result = ShortcutBackupPolicy.reconcile(state(entry("one"), entry("two")), emptySet(), true, true)
        assertTrue(result.entries.all { it.status == Status.REMOVED })
    }

    @Test fun packageResetOffersEachConfirmedInstanceButNeverPreviouslyRemovedOnes() {
        val before = state(entry("one"), entry("two"), entry("removed", Status.REMOVED), entry("cancelled", Status.PENDING))
        val result = ShortcutBackupPolicy.reconcile(before, emptySet(), false, true)
        assertEquals(listOf(Status.MISSING, Status.MISSING, Status.REMOVED, Status.PENDING), result.entries.map { it.status })
    }

    @Test fun unknownLauncherOrUnarmedMarkerDoesNotInferReset() {
        val before = state(entry("one"))
        assertEquals(Status.SAVED, ShortcutBackupPolicy.reconcile(before, emptySet(), false, false).entries.single().status)
        val unarmed = before.copy(markerToken = null)
        assertEquals(Status.SAVED, ShortcutBackupPolicy.reconcile(unarmed, emptySet(), false, true).entries.single().status)
    }

    @Test fun resetCandidatesSurviveSubsequentChecksWithNewMarker() {
        val before = state(entry("one", Status.MISSING).copy(notified = true))
        assertEquals(before, ShortcutBackupPolicy.reconcile(before, emptySet(), true, true))
    }

    @Test fun queryCanConfirmPinEvenWhenSuccessBroadcastWasMissed() {
        val before = state(entry("one", Status.PENDING).copy(requestToken = "request"))
        val result = ShortcutBackupPolicy.reconcile(before, setOf("one"), true, true).entries.single()
        assertEquals(Status.PINNED, result.status)
        assertNull(result.requestToken)
    }

    @Test fun declinedRequestsAndImportedDefinitionsNeverBecomeAutomaticRecoveryPrompts() {
        val before = state(entry("one", Status.PENDING), entry("two", Status.SAVED))
        assertEquals(before, ShortcutBackupPolicy.reconcile(before, emptySet(), false, true))
    }

    @Test fun importPreservesCopiesAndDoesNotPretendTheyAreOnDesktop() {
        val result = ShortcutBackupPolicy.merge(ShortcutBackupState(), listOf(entry("one"), entry("two")))
        assertEquals(listOf("one", "two"), result.entries.map { it.id })
        assertTrue(result.entries.all { it.status == Status.SAVED })
        assertNull(result.markerToken)
    }

    @Test fun reimportIsIdempotentAndPreservesLocalDeletion() {
        val local = state(entry("one", Status.REMOVED), entry("two"))
        val result = ShortcutBackupPolicy.merge(local, listOf(entry("one"), entry("two")))
        assertEquals(local, result)
    }

    @Test fun importedRemovedEntriesStayUnselectedAndDiscardDeviceRequestTokens() {
        val result = ShortcutBackupPolicy.merge(ShortcutBackupState(), listOf(entry("one", Status.REMOVED).copy(requestToken = "old")))
        assertEquals(Status.REMOVED, result.entries.single().status)
        assertNull(result.entries.single().requestToken)
    }

    @Test fun conflictingIdDoesNotOverwriteAnExistingDefinition() {
        val local = state(entry("one"))
        assertThrows(IllegalArgumentException::class.java) {
            ShortcutBackupPolicy.merge(local, listOf(entry("one").copy(path = "/different.js")))
        }
        assertEquals("/sdcard/scripts/test.js", local.entries.single().path)
    }
}
