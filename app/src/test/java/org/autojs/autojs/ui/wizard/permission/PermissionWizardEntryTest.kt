package org.autojs.autojs.ui.wizard.permission

import org.autojs.autojs.ui.wizard.WizardProfile
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionWizardEntryTest {

    @Test
    fun idsAreUniqueAndResolvable() {
        val ids = PermissionWizardEntry.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        PermissionWizardEntry.entries.forEach { assertEquals(it, PermissionWizardEntry.fromId(it.id)) }
        assertNull(PermissionWizardEntry.fromId("missing"))
    }

    @Test
    fun standardProfileHoldsFilesNotificationsBatteryAndOverlay() {
        assertEquals(
            setOf(
                PermissionWizardEntry.ALL_FILES_ACCESS,
                PermissionWizardEntry.POST_NOTIFICATIONS,
                PermissionWizardEntry.IGNORE_BATTERY_OPTIMIZATIONS,
                PermissionWizardEntry.DISPLAY_OVER_OTHER_APPS,
            ),
            resolve(WizardProfile.STANDARD),
        )
    }

    @Test
    fun minimalProfileHoldsOnlyAllFilesAccess() {
        assertEquals(setOf(PermissionWizardEntry.ALL_FILES_ACCESS), resolve(WizardProfile.MINIMAL))
    }

    @Test
    fun allProfileHoldsEverySelectablePermission() {
        assertEquals(PermissionWizardEntry.entries.toSet(), resolve(WizardProfile.ALL))
    }

    @Test
    fun grantedOrUnavailablePermissionsCannotBeTicked() {
        assertTrue(PermissionWizardItem(PermissionWizardEntry.SHIZUKU, isGranted = false).isSelectable)
        assertFalse(PermissionWizardItem(PermissionWizardEntry.SHIZUKU, isGranted = true).isSelectable)
        assertFalse(PermissionWizardItem(PermissionWizardEntry.SHIZUKU, isGranted = false, unavailableReason = "not installed").isSelectable)
        assertTrue(PermissionWizardItem(PermissionWizardEntry.ALL_FILES_ACCESS, isGranted = true).toCandidate().done)
    }

    private fun resolve(profile: WizardProfile): Set<PermissionWizardEntry> {
        val candidates = PermissionWizardEntry.entries.map { PermissionWizardItem(it, isGranted = false).toCandidate() }
        return WizardSelectionPolicy.resolveSelection(profile, candidates).mapNotNull(PermissionWizardEntry::fromId).toSet()
    }

}
