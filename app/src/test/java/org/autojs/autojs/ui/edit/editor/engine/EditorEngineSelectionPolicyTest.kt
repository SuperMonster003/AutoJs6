package org.autojs.autojs.ui.edit.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEngineSelectionPolicyTest {

    @Test
    fun legacyPreferenceMigrationPreservesExplicitNativeSelection() {
        assertEquals(
            EditorEngineSelectionPolicy.Mode.AUTO_ACE,
            EditorEngineSelectionPolicy.migrateMode(null),
        )
        assertEquals(
            EditorEngineSelectionPolicy.Mode.AUTO_ACE,
            EditorEngineSelectionPolicy.migrateMode(EditorEngineType.ACE.preferenceValue),
        )
        assertEquals(
            EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT,
            EditorEngineSelectionPolicy.migrateMode(EditorEngineType.NATIVE.preferenceValue),
        )
        assertEquals(
            EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT,
            EditorEngineSelectionPolicy.migrateMode("invalid"),
        )
    }

    @Test
    fun autoModeTracksAceAvailability() {
        val missing = EditorEngineSelectionPolicy.reconcile(
            EditorEngineSelectionPolicy.Mode.AUTO_ACE,
            aceAvailable = false,
        )
        val installed = EditorEngineSelectionPolicy.reconcile(
            missing.mode,
            aceAvailable = true,
        )
        val removed = EditorEngineSelectionPolicy.reconcile(
            installed.mode,
            aceAvailable = false,
        )
        val reinstalled = EditorEngineSelectionPolicy.reconcile(
            removed.mode,
            aceAvailable = true,
        )

        assertEquals(EditorEngineType.NATIVE, missing.effectiveType)
        assertEquals(EditorEngineType.ACE, installed.effectiveType)
        assertEquals(EditorEngineType.NATIVE, removed.effectiveType)
        assertEquals(EditorEngineType.ACE, reinstalled.effectiveType)
    }

    @Test
    fun explicitNativeSuppressesAutomaticAceUntilAvailableAceIsSelected() {
        val explicitNative = EditorEngineSelectionPolicy.selectByUser(
            currentMode = EditorEngineSelectionPolicy.Mode.AUTO_ACE,
            selectedType = EditorEngineType.NATIVE,
            aceAvailable = true,
        )
        val afterReinstall = EditorEngineSelectionPolicy.reconcile(
            explicitNative.mode,
            aceAvailable = true,
        )
        val explicitAce = EditorEngineSelectionPolicy.selectByUser(
            currentMode = afterReinstall.mode,
            selectedType = EditorEngineType.ACE,
            aceAvailable = true,
        )

        assertEquals(EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT, explicitNative.mode)
        assertEquals(EditorEngineType.NATIVE, afterReinstall.effectiveType)
        assertEquals(EditorEngineSelectionPolicy.Mode.AUTO_ACE, explicitAce.mode)
        assertEquals(EditorEngineType.ACE, explicitAce.effectiveType)
        assertFalse(explicitAce.needsAcePlugin)
    }

    @Test
    fun unavailableAceSelectionIsRejectedWithoutChangingExplicitMode() {
        val decision = EditorEngineSelectionPolicy.selectByUser(
            currentMode = EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT,
            selectedType = EditorEngineType.ACE,
            aceAvailable = false,
        )

        assertEquals(EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT, decision.mode)
        assertEquals(EditorEngineType.NATIVE, decision.effectiveType)
        assertTrue(decision.needsAcePlugin)
    }

    @Test
    fun nativeSelectionWhileAceUnavailableDoesNotDisableFutureAutoSelection() {
        val decision = EditorEngineSelectionPolicy.selectByUser(
            currentMode = EditorEngineSelectionPolicy.Mode.AUTO_ACE,
            selectedType = EditorEngineType.NATIVE,
            aceAvailable = false,
        )

        assertEquals(EditorEngineSelectionPolicy.Mode.AUTO_ACE, decision.mode)
        assertEquals(EditorEngineType.NATIVE, decision.effectiveType)
    }
}
