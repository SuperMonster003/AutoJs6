package org.autojs.autojs.model.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedBackupRetentionTest {
    private fun version(id: Int, size: Long = 10, automatic: Boolean = true, restorable: Boolean = true, source: String = "/a") =
        PrivilegedBackupRetention.Version("$source:$id", source, id.toLong(), size, automatic, restorable)

    @Test fun keepsTheNewestVersionsForEachFile() {
        val versions = (1..8).map { version(it) } + (1..3).map { version(it, source = "/b") }
        assertEquals(setOf("/a:1", "/a:2", "/a:3"), PrivilegedBackupRetention.expired(versions, 5, 1000))
    }

    @Test fun spaceTargetPrunesOldestVersionsBeforeTheCountLimit() {
        assertEquals(setOf("/a:1", "/a:2"), PrivilegedBackupRetention.expired((1..4).map { version(it, 30) }, 5, 64))
    }

    @Test fun anOversizedNewestVersionIsStillRetained() {
        assertEquals(setOf("/a:1"), PrivilegedBackupRetention.expired(listOf(version(1), version(2, 1000)), 5, 64))
    }

    @Test fun changedBusyAndUnconfirmedFilesNeverBecomeAutomaticCandidates() {
        val versions = listOf(version(1, automatic = false), version(2, automatic = false, restorable = false), version(3), version(4))
        assertEquals(setOf("/a:3"), PrivilegedBackupRetention.expired(versions, 1, 1))
    }

    @Test fun aNewerFailedSaveDoesNotRemoveTheOnlyConfirmedBackup() {
        val versions = listOf(version(1), version(2, 900, automatic = false, restorable = false))
        assertTrue(PrivilegedBackupRetention.expired(versions, 1, 1).isEmpty())
    }

    @Test fun aNewerChangedBackupDoesNotRemoveTheLastUnchangedVersion() {
        val versions = listOf(version(1), version(2, 900, automatic = false))
        assertTrue(PrivilegedBackupRetention.expired(versions, 1, 1).isEmpty())
    }

    @Test fun orderingDoesNotDependOnTheCatalogReadOrder() {
        val versions = (1..8).map { version(it) }
        assertEquals(PrivilegedBackupRetention.expired(versions, 3, 100), PrivilegedBackupRetention.expired(versions.reversed(), 3, 100))
    }
}
