package org.autojs.autojs.core.plugin.explorer.output

internal sealed interface ExplorerActionOutputResultDecision {
    data object Accept : ExplorerActionOutputResultDecision
    data object Cancel : ExplorerActionOutputResultDecision
    data class Reject(val reason: String) : ExplorerActionOutputResultDecision
}

internal object ExplorerActionOutputResultPolicy {

    fun evaluate(
        expectedTransactionId: String,
        resultOk: Boolean,
        returnedTransactionId: String?,
    ): ExplorerActionOutputResultDecision {
        if (!resultOk) return ExplorerActionOutputResultDecision.Cancel
        if (returnedTransactionId.isNullOrBlank()) {
            return ExplorerActionOutputResultDecision.Reject("Explorer Action output result has no transaction ID")
        }
        if (returnedTransactionId != expectedTransactionId) {
            return ExplorerActionOutputResultDecision.Reject("Explorer Action output result transaction ID does not match")
        }
        return ExplorerActionOutputResultDecision.Accept
    }
}
