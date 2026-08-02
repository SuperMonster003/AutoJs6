package org.autojs.autojs.core.plugin.explorer.output

internal enum class ExplorerActionOutputTransactionState {
    PREPARED,
    LAUNCHING,
    LAUNCHED,
    RESULT_RECEIVED,
    SNAPSHOTTED,
    VALIDATED,
    COMMITTING,
    COMMITTED,
    ROLLED_BACK,
}

internal data class ExplorerActionOutputPrepareRequest(
    val pluginPackageName: String,
    val pluginActivityClassName: String,
    val pluginUid: Int,
    val pluginVersionCode: Long,
    val pluginLastUpdateTime: Long,
    val pluginSigningDigests: Set<String>,
    val actionId: String,
    val protocolVersion: Int,
    val sourcePath: String,
    val sourceParentDevice: Long,
    val sourceParentInode: Long,
    val sourceDisplayName: String,
    val sourceMimeType: String,
    val sourceSurface: String,
    val allowedOutputMimeTypes: Set<String>,
    val outputNameSuffix: String,
    val maxOutputBytes: Long,
)

internal data class ExplorerActionOutputTransaction(
    val id: String,
    val state: ExplorerActionOutputTransactionState,
    val pluginPackageName: String,
    val pluginActivityClassName: String,
    val pluginUid: Int,
    val pluginVersionCode: Long,
    val pluginLastUpdateTime: Long,
    val pluginSigningDigests: Set<String>,
    val actionId: String,
    val protocolVersion: Int,
    val sourcePath: String,
    val sourceParentPath: String,
    val sourceParentDevice: Long,
    val sourceParentInode: Long,
    val sourceDisplayName: String,
    val sourceMimeType: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val sourceSurface: String,
    val allowedOutputMimeTypes: Set<String>,
    val outputNameSuffix: String,
    val maxOutputBytes: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val detectedOutputMimeType: String? = null,
    val snapshotSha256: String? = null,
    val finalPath: String? = null,
    val publishTemporaryPath: String? = null,
    val finalSha256: String? = null,
) {

    fun transitionTo(
        next: ExplorerActionOutputTransactionState,
        nowMillis: Long,
    ): ExplorerActionOutputTransaction {
        require(next in allowedTransitions.getValue(state)) {
            "Invalid Explorer Action output transaction transition: $state -> $next"
        }
        return copy(state = next, updatedAtMillis = nowMillis)
    }

    companion object {
        private val allowedTransitions = mapOf(
            ExplorerActionOutputTransactionState.PREPARED to setOf(
                ExplorerActionOutputTransactionState.LAUNCHING,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.LAUNCHING to setOf(
                ExplorerActionOutputTransactionState.LAUNCHED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.LAUNCHED to setOf(
                ExplorerActionOutputTransactionState.RESULT_RECEIVED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.RESULT_RECEIVED to setOf(
                ExplorerActionOutputTransactionState.SNAPSHOTTED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.SNAPSHOTTED to setOf(
                ExplorerActionOutputTransactionState.VALIDATED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.VALIDATED to setOf(
                ExplorerActionOutputTransactionState.COMMITTING,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.COMMITTING to setOf(
                ExplorerActionOutputTransactionState.COMMITTED,
                ExplorerActionOutputTransactionState.ROLLED_BACK,
            ),
            ExplorerActionOutputTransactionState.COMMITTED to emptySet(),
            ExplorerActionOutputTransactionState.ROLLED_BACK to emptySet(),
        )
    }
}
