package org.autojs.autojs.core.plugin.explorer.archive

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerArchiveVolumeNamePolicyTest {

    @Test
    fun standardZipCandidatesAreBoundedAndKeepTheSelectedStem() {
        val candidates = ExplorerArchiveVolumeNamePolicy.candidateNames("backup.zip")

        assertEquals(ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES - 1, candidates.size)
        assertEquals("backup.z01", candidates.first())
        assertEquals("backup.z99", candidates[98])
        assertEquals("backup.z100", candidates[99])
        assertEquals("backup.z127", candidates.last())
    }

    @Test
    fun modernRarCandidatesPreserveWidthAndExcludeTheSelectedPart() {
        val candidates = ExplorerArchiveVolumeNamePolicy.candidateNames("backup.part01.rar")

        assertEquals(ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES - 1, candidates.size)
        assertFalse("backup.part01.rar" in candidates)
        assertEquals("backup.part02.rar", candidates.first())
        assertTrue("backup.part99.rar" in candidates)
        assertTrue("backup.part128.rar" in candidates)
    }

    @Test
    fun modernRarPartOutsideTheProtocolEnvelopeIsNotAuthorized() {
        assertTrue(
            ExplorerArchiveVolumeNamePolicy
                .candidateNames("backup.part129.rar")
                .isEmpty(),
        )
    }

    @Test
    fun legacyRarCandidatesFollowTheRarExtensionSequence() {
        val candidates = ExplorerArchiveVolumeNamePolicy.candidateNames("backup.rar")

        assertEquals(ExplorerActionProtocol.MAX_ARCHIVE_VOLUMES - 1, candidates.size)
        assertEquals("backup.r00", candidates.first())
        assertEquals("backup.r99", candidates[99])
        assertEquals("backup.s00", candidates[100])
        assertEquals("backup.s26", candidates.last())
    }

    @Test
    fun unrelatedArchiveNamesDoNotGrantSiblingAccess() {
        assertTrue(ExplorerArchiveVolumeNamePolicy.candidateNames("backup.7z").isEmpty())
        assertTrue(ExplorerArchiveVolumeNamePolicy.candidateNames("backup.tar.gz").isEmpty())
        assertTrue(ExplorerArchiveVolumeNamePolicy.candidateNames("backup.r00").isEmpty())
    }
}
