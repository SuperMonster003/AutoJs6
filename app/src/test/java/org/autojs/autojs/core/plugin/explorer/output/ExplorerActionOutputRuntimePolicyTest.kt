package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerActionOutputRuntimePolicyTest {

    @Test
    fun grantPlanNeverGivesSourceWriteOrPrefixAccess() {
        val plan = ExplorerActionOutputGrantPolicy.exactGrantPlan()
        val source = plan.single { it.role == ExplorerActionOutputUriRole.SOURCE }
        val output = plan.single { it.role == ExplorerActionOutputUriRole.OUTPUT }

        assertTrue(ExplorerActionOutputGrantPolicy.isSafe(plan))
        assertTrue(source.read)
        assertFalse(source.write)
        assertFalse(source.prefix)
        assertFalse(output.read)
        assertTrue(output.write)
        assertFalse(output.prefix)
    }

    @Test
    fun resultMustReturnTheExpectedTransactionId() {
        assertEquals(
            ExplorerActionOutputResultDecision.Accept,
            ExplorerActionOutputResultPolicy.evaluate("tx", true, "tx"),
        )
        assertEquals(
            ExplorerActionOutputResultDecision.Cancel,
            ExplorerActionOutputResultPolicy.evaluate("tx", false, null),
        )
        assertTrue(
            ExplorerActionOutputResultPolicy.evaluate("tx", true, "other") is
                ExplorerActionOutputResultDecision.Reject,
        )
    }

    @Test
    fun recoveryCompletesVerifiedCommitAndRollsBackStaleWork() {
        val committing = transaction(ExplorerActionOutputTransactionState.COMMITTING, updatedAtMillis = 1_000L)
        assertEquals(
            ExplorerActionOutputRecoveryAction.COMPLETE_COMMIT,
            ExplorerActionOutputRecoveryPolicy.decide(
                committing,
                nowMillis = 2_000L,
                stagingExists = false,
                snapshotExists = true,
                committedFileVerified = true,
            ),
        )
        assertEquals(
            ExplorerActionOutputRecoveryAction.ROLLBACK,
            ExplorerActionOutputRecoveryPolicy.decide(
                transaction(ExplorerActionOutputTransactionState.LAUNCHED, updatedAtMillis = 1_000L),
                nowMillis = 1_000L + ExplorerActionOutputRecoveryPolicy.DEFAULT_STALE_AFTER_MILLIS,
                stagingExists = true,
                snapshotExists = false,
                committedFileVerified = false,
            ),
        )
        assertEquals(
            ExplorerActionOutputRecoveryAction.RESUME,
            ExplorerActionOutputRecoveryPolicy.decide(
                transaction(ExplorerActionOutputTransactionState.VALIDATED, updatedAtMillis = 1_000L),
                nowMillis = 1_000L + ExplorerActionOutputRecoveryPolicy.DEFAULT_STALE_AFTER_MILLIS,
                stagingExists = false,
                snapshotExists = true,
                committedFileVerified = false,
            ),
        )
        assertEquals(
            ExplorerActionOutputRecoveryAction.ROLLBACK,
            ExplorerActionOutputRecoveryPolicy.decide(
                transaction(ExplorerActionOutputTransactionState.LAUNCHING, updatedAtMillis = 1_000L),
                nowMillis = 1_001L,
                stagingExists = true,
                snapshotExists = false,
                committedFileVerified = false,
            ),
        )
    }

    private fun transaction(
        state: ExplorerActionOutputTransactionState,
        updatedAtMillis: Long,
    ) = ExplorerActionOutputTransaction(
        id = "123e4567-e89b-42d3-a456-426614174000",
        state = state,
        pluginPackageName = "com.example.image",
        pluginActivityClassName = "com.example.image.OutputActivity",
        pluginUid = 12_345,
        pluginVersionCode = 7L,
        pluginLastUpdateTime = 999L,
        pluginSigningDigests = setOf("a".repeat(64)),
        actionId = "edit-image",
        protocolVersion = 3,
        sourcePath = "/source/photo.png",
        sourceParentPath = "/source",
        sourceParentDevice = 1L,
        sourceParentInode = 2L,
        sourceDisplayName = "photo.png",
        sourceMimeType = "image/png",
        sourceSize = 10L,
        sourceLastModified = 1L,
        sourceSurface = "main",
        allowedOutputMimeTypes = setOf("image/png"),
        outputNameSuffix = "edited",
        maxOutputBytes = 1024L,
        createdAtMillis = 1L,
        updatedAtMillis = updatedAtMillis,
        snapshotSha256 = if (state == ExplorerActionOutputTransactionState.COMMITTING) "a".repeat(64) else null,
        finalPath = if (state == ExplorerActionOutputTransactionState.COMMITTING) "/source/output.png" else null,
    )
}
