package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiTargetPageTokenPolicyTest {
    @Test
    fun issuedContinuationTokenCanBeConsumedExactlyOnce() {
        val ledger = AiTargetPageTokenLedger()
        val policy = AiTargetPaginationPolicy(scope(), ledger)
        policy.acceptPage(null, page(next = "token-2", targetId = "fake.local:first"))
        policy.acceptPage("token-2", page(next = null, targetId = "fake.local:second"))

        assertThrows(IllegalStateException::class.java) {
            policy.acceptPage("token-2", page(next = null, targetId = "fake.local:third"))
        }
    }

    @Test
    fun directAndLongerTokenCyclesAreRejected() {
        val direct = AiTargetPaginationPolicy(scope(), AiTargetPageTokenLedger())
        direct.acceptPage(null, page(next = "token-2", targetId = "fake.local:first"))
        assertThrows(IllegalStateException::class.java) {
            direct.acceptPage("token-2", page(next = "token-2", targetId = "fake.local:second"))
        }

        val longer = AiTargetPaginationPolicy(scope(), AiTargetPageTokenLedger())
        longer.acceptPage(null, page(next = "token-2", targetId = "fake.local:first"))
        longer.acceptPage("token-2", page(next = "token-3", targetId = "fake.local:second"))
        assertThrows(IllegalStateException::class.java) {
            longer.acceptPage("token-3", page(next = "token-2", targetId = "fake.local:third"))
        }
    }

    @Test
    fun tokenReplayAcrossSignerOrProviderScopeIsRejected() {
        val ledger = AiTargetPageTokenLedger()
        ledger.consume("opaque-token", scope())

        assertThrows(IllegalStateException::class.java) {
            ledger.consume(
                "opaque-token",
                scope().copy(
                    signerSha256 = "b".repeat(64),
                    providerId = "other.provider",
                ),
            )
        }
    }

    @Test
    fun wrongGenerationAndUnissuedRequestTokensAreRejected() {
        val policy = AiTargetPaginationPolicy(scope(), AiTargetPageTokenLedger())
        assertThrows(IllegalArgumentException::class.java) {
            policy.acceptPage(null, page(next = "token", generation = "generation-2"))
        }

        val second = AiTargetPaginationPolicy(scope(), AiTargetPageTokenLedger())
        second.acceptPage(null, page(next = "token-2", targetId = "fake.local:first"))
        assertThrows(IllegalStateException::class.java) {
            second.acceptPage("not-issued", page(next = null, targetId = "fake.local:second"))
        }
    }

    @Test
    fun targetIdsCannotRepeatAcrossPages() {
        val policy = AiTargetPaginationPolicy(scope(), AiTargetPageTokenLedger())
        policy.acceptPage(null, page(next = "token-2", targetId = "fake.local:echo"))

        assertThrows(IllegalStateException::class.java) {
            policy.acceptPage("token-2", page(next = null, targetId = "fake.local:echo"))
        }
    }

    @Test
    fun tokenLedgerFailsClosedAtItsExplicitCapacity() {
        val ledger = AiTargetPageTokenLedger(maximumConsumedTokens = 2)
        ledger.consume("token-1", scope())
        ledger.consume("token-2", scope())

        assertThrows(IllegalStateException::class.java) {
            ledger.consume("token-3", scope())
        }
        assertThrows(IllegalStateException::class.java) {
            ledger.consume("token-1", scope())
        }
    }

    @Test
    fun pageSizeIsValidatedAndIncludedInTokenScope() {
        assertNotEquals(scope(), scope().copy(pageSize = 2))
        assertThrows(IllegalArgumentException::class.java) { scope().copy(pageSize = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            scope().copy(pageSize = AiProviderLimits.MAX_TARGETS_PER_PAGE + 1)
        }

        val policy = AiTargetPaginationPolicy(scope().copy(pageSize = 1), AiTargetPageTokenLedger())
        assertThrows(IllegalArgumentException::class.java) {
            policy.acceptPage(
                null,
                AiTargetPage(
                    catalogGeneration = "generation-1",
                    targets = listOf(target("fake.local:first"), target("fake.local:second")),
                ),
            )
        }
    }

    private fun scope() = AiTargetTokenScope(
        packageName = "io.example.fake",
        signerSha256 = "a".repeat(64),
        providerId = "fake.local",
        protocolVersion = AiProtocolVersion(2, 0),
        catalogGeneration = "generation-1",
        pageSize = 10,
    )

    private fun page(
        next: String?,
        generation: String = "generation-1",
        targetId: String = "fake.local:echo",
    ) = AiTargetPage(
        catalogGeneration = generation,
        targets = listOf(target(targetId)),
        nextPageToken = next,
    )

    private fun target(targetId: String) = localTarget(targetId = targetId)
}
