package org.autojs.autojs.core.plugin.explorer.output

internal enum class ExplorerActionOutputRecoveryAction {
    KEEP,
    RESUME,
    ROLLBACK,
    COMPLETE_COMMIT,
    CLEANUP,
}

internal object ExplorerActionOutputRecoveryPolicy {

    fun decide(
        transaction: ExplorerActionOutputTransaction,
        nowMillis: Long,
        stagingExists: Boolean,
        snapshotExists: Boolean,
        committedFileVerified: Boolean,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    ): ExplorerActionOutputRecoveryAction {
        require(staleAfterMillis > 0L) { "Explorer Action output stale interval must be positive" }
        if (transaction.state == ExplorerActionOutputTransactionState.COMMITTED ||
            transaction.state == ExplorerActionOutputTransactionState.ROLLED_BACK
        ) {
            return ExplorerActionOutputRecoveryAction.CLEANUP
        }
        if (transaction.state == ExplorerActionOutputTransactionState.COMMITTING && committedFileVerified) {
            return ExplorerActionOutputRecoveryAction.COMPLETE_COMMIT
        }
        return when (transaction.state) {
            ExplorerActionOutputTransactionState.LAUNCHING -> ExplorerActionOutputRecoveryAction.ROLLBACK
            ExplorerActionOutputTransactionState.PREPARED,
            ExplorerActionOutputTransactionState.LAUNCHED,
            -> {
                val stale = nowMillis < transaction.updatedAtMillis ||
                    nowMillis - transaction.updatedAtMillis >= staleAfterMillis
                if (stagingExists && !stale) {
                    ExplorerActionOutputRecoveryAction.KEEP
                } else {
                    ExplorerActionOutputRecoveryAction.ROLLBACK
                }
            }
            ExplorerActionOutputTransactionState.RESULT_RECEIVED -> if (stagingExists) {
                ExplorerActionOutputRecoveryAction.RESUME
            } else {
                ExplorerActionOutputRecoveryAction.ROLLBACK
            }
            ExplorerActionOutputTransactionState.SNAPSHOTTED,
            ExplorerActionOutputTransactionState.VALIDATED,
            ExplorerActionOutputTransactionState.COMMITTING,
            -> if (snapshotExists) {
                ExplorerActionOutputRecoveryAction.RESUME
            } else {
                ExplorerActionOutputRecoveryAction.ROLLBACK
            }
            ExplorerActionOutputTransactionState.COMMITTED,
            ExplorerActionOutputTransactionState.ROLLED_BACK,
            -> ExplorerActionOutputRecoveryAction.CLEANUP
        }
    }

    const val DEFAULT_STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
}
