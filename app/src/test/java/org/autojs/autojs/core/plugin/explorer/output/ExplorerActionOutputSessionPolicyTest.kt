package org.autojs.autojs.core.plugin.explorer.output

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerActionOutputSessionPolicyTest {

    @Test
    fun allowsOnlyTheExactWriteOnlyTransactionCapability() {
        assertEquals(ExplorerActionOutputOpenDecision.Allow, decision())
        assertEquals(
            ExplorerActionOutputOpenDecision.Allow,
            decision(transactionState = ExplorerActionOutputTransactionState.LAUNCHING),
        )
    }

    @Test
    fun rejectsReadSeekAppendAndSecondWriterModes() {
        listOf("r", "rw", "wa", "rwt", "wt").forEach { mode ->
            assertRejected(ExplorerActionOutputOpenRejection.MODE_NOT_WRITE_ONLY, decision(mode = mode))
        }
        assertRejected(
            ExplorerActionOutputOpenRejection.WRITER_ALREADY_OPENED,
            decision(writerAlreadyOpened = true),
        )
    }

    @Test
    fun rejectsWrongCallerExtraPathAndNonWritableState() {
        assertRejected(
            ExplorerActionOutputOpenRejection.CALLER_MISMATCH,
            decision(callingPackage = "com.example.attacker"),
        )
        assertRejected(
            ExplorerActionOutputOpenRejection.INVALID_PATH,
            decision(pathSegments = listOf(TRANSACTION_ID, "output")),
        )
        assertRejected(
            ExplorerActionOutputOpenRejection.QUERY_OR_FRAGMENT_NOT_ALLOWED,
            decision(hasQueryOrFragment = true),
        )
        assertRejected(
            ExplorerActionOutputOpenRejection.TRANSACTION_NOT_WRITABLE,
            decision(transactionState = ExplorerActionOutputTransactionState.RESULT_RECEIVED),
        )
    }

    private fun decision(
        mode: String = ExplorerActionOutputSessionPolicy.WRITE_MODE,
        callingPackage: String? = PLUGIN_PACKAGE,
        pathSegments: List<String> = listOf(TRANSACTION_ID),
        hasQueryOrFragment: Boolean = false,
        transactionState: ExplorerActionOutputTransactionState = ExplorerActionOutputTransactionState.LAUNCHED,
        writerAlreadyOpened: Boolean = false,
    ) = ExplorerActionOutputSessionPolicy.evaluate(
        scheme = "content",
        authority = AUTHORITY,
        expectedAuthority = AUTHORITY,
        pathSegments = pathSegments,
        hasQueryOrFragment = hasQueryOrFragment,
        mode = mode,
        callingPackage = callingPackage,
        expectedPackage = PLUGIN_PACKAGE,
        transactionState = transactionState,
        writerAlreadyOpened = writerAlreadyOpened,
    )

    private fun assertRejected(
        expected: ExplorerActionOutputOpenRejection,
        actual: ExplorerActionOutputOpenDecision,
    ) {
        assertEquals(expected, (actual as ExplorerActionOutputOpenDecision.Reject).reason)
    }

    private companion object {
        const val TRANSACTION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val AUTHORITY = "org.autojs.autojs6.explorer_action_output"
        const val PLUGIN_PACKAGE = "com.example.image"
    }
}
