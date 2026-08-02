package org.autojs.autojs.core.plugin.explorer.output

internal enum class ExplorerActionOutputOpenRejection {
    INVALID_SCHEME,
    INVALID_AUTHORITY,
    INVALID_PATH,
    QUERY_OR_FRAGMENT_NOT_ALLOWED,
    MODE_NOT_WRITE_ONLY,
    CALLER_MISMATCH,
    TRANSACTION_NOT_WRITABLE,
    WRITER_ALREADY_OPENED,
}

internal sealed interface ExplorerActionOutputOpenDecision {
    data object Allow : ExplorerActionOutputOpenDecision
    data class Reject(val reason: ExplorerActionOutputOpenRejection) : ExplorerActionOutputOpenDecision
}

internal object ExplorerActionOutputSessionPolicy {

    fun evaluate(
        scheme: String?,
        authority: String?,
        expectedAuthority: String,
        pathSegments: List<String>,
        hasQueryOrFragment: Boolean,
        mode: String,
        callingPackage: String?,
        expectedPackage: String,
        transactionState: ExplorerActionOutputTransactionState,
        writerAlreadyOpened: Boolean,
    ): ExplorerActionOutputOpenDecision {
        if (scheme != "content") return reject(ExplorerActionOutputOpenRejection.INVALID_SCHEME)
        if (authority != expectedAuthority) return reject(ExplorerActionOutputOpenRejection.INVALID_AUTHORITY)
        if (pathSegments.size != 1 || !isValidTransactionId(pathSegments.single())) {
            return reject(ExplorerActionOutputOpenRejection.INVALID_PATH)
        }
        if (hasQueryOrFragment) {
            return reject(ExplorerActionOutputOpenRejection.QUERY_OR_FRAGMENT_NOT_ALLOWED)
        }
        if (mode != WRITE_MODE) return reject(ExplorerActionOutputOpenRejection.MODE_NOT_WRITE_ONLY)
        if (callingPackage != expectedPackage) return reject(ExplorerActionOutputOpenRejection.CALLER_MISMATCH)
        if (
            transactionState != ExplorerActionOutputTransactionState.LAUNCHING &&
            transactionState != ExplorerActionOutputTransactionState.LAUNCHED
        ) {
            return reject(ExplorerActionOutputOpenRejection.TRANSACTION_NOT_WRITABLE)
        }
        if (writerAlreadyOpened) return reject(ExplorerActionOutputOpenRejection.WRITER_ALREADY_OPENED)
        return ExplorerActionOutputOpenDecision.Allow
    }

    fun isValidTransactionId(value: String): Boolean = TRANSACTION_ID_PATTERN.matches(value)

    private fun reject(reason: ExplorerActionOutputOpenRejection) =
        ExplorerActionOutputOpenDecision.Reject(reason)

    const val WRITE_MODE = "w"

    private val TRANSACTION_ID_PATTERN =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
}
