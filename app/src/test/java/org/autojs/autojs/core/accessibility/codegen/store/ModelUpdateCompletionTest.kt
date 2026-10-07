package org.autojs.autojs.core.accessibility.codegen.store

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelUpdateCompletionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun completionSwitchesCurrentAndKeepsBothVersionsUntilExplicitDeletion() {
        val f = ModelManagementTestFixtures(temporary.root)
        val old = f.installed(1)
        f.store.select(old.key)
        val next = f.installed(2)
        assertTrue(ModelUpdates.complete(f.store, f.update(old), next))
        assertEquals(next.key, f.store.current()?.key)
        assertEquals(2, f.store.listInstalled().size)
        assertTrue(f.store.delete(old.key))
        assertEquals(next.key, f.store.current()?.key)
        assertEquals(listOf(next), f.store.listInstalled())
    }

    @Test fun completionDoesNotOverwriteAnInterveningChoiceOrMissingTarget() {
        val f = ModelManagementTestFixtures(temporary.root)
        val old = f.installed(1)
        f.store.select(old.key)
        val next = f.installed(2)
        f.store.select(null)
        assertFalse(ModelUpdates.complete(f.store, f.update(old), next))
        assertNull(f.store.current())
        f.store.select(old.key)
        f.store.delete(next.key)
        assertFalse(ModelUpdates.complete(f.store, f.update(old), next))
        assertEquals(old.key, f.store.current()?.key)
    }

    @Test fun aMismatchedCompletionCannotSelectAnUnrequestedPackage() {
        val f = ModelManagementTestFixtures(temporary.root)
        val old = f.installed(1)
        val next = f.installed(2)
        f.store.select(old.key)
        assertThrows(IllegalArgumentException::class.java) { ModelUpdates.complete(f.store, f.update(old), next.copy(sha256 = "f".repeat(64))) }
        assertEquals(old.key, f.store.current()?.key)
    }
}
