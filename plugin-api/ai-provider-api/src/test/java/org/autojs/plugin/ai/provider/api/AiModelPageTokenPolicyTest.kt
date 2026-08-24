package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiModelPageTokenPolicyTest {
    @Test
    fun issuedContinuationTokenCanBeConsumedExactlyOnce() {
        val ledger = AiModelPageTokenLedger()
        val policy = AiModelPaginationPolicy(scope(), ledger)
        policy.acceptPage(null, page(next = "token-2", modelId = "fake.first"))
        policy.acceptPage("token-2", page(next = null, modelId = "fake.second"))

        assertThrows(IllegalStateException::class.java) {
            policy.acceptPage("token-2", page(next = null, modelId = "fake.third"))
        }
    }

    @Test
    fun directAndLongerTokenCyclesAreRejected() {
        val direct = AiModelPaginationPolicy(scope(), AiModelPageTokenLedger())
        direct.acceptPage(null, page(next = "token-2", modelId = "fake.first"))
        assertThrows(IllegalStateException::class.java) {
            direct.acceptPage("token-2", page(next = "token-2", modelId = "fake.second"))
        }

        val longer = AiModelPaginationPolicy(scope(), AiModelPageTokenLedger())
        longer.acceptPage(null, page(next = "token-2", modelId = "fake.first"))
        longer.acceptPage("token-2", page(next = "token-3", modelId = "fake.second"))
        assertThrows(IllegalStateException::class.java) {
            longer.acceptPage("token-3", page(next = "token-2", modelId = "fake.third"))
        }
    }

    @Test
    fun tokenReplayAcrossSignerOrProviderScopeIsRejected() {
        val ledger = AiModelPageTokenLedger()
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
        val policy = AiModelPaginationPolicy(scope(), AiModelPageTokenLedger())
        assertThrows(IllegalArgumentException::class.java) {
            policy.acceptPage(null, page(next = "token", generation = "generation-2"))
        }

        val second = AiModelPaginationPolicy(scope(), AiModelPageTokenLedger())
        second.acceptPage(null, page(next = "token-2", modelId = "fake.first"))
        assertThrows(IllegalStateException::class.java) {
            second.acceptPage("not-issued", page(next = null, modelId = "fake.second"))
        }
    }

    @Test
    fun modelIdsCannotRepeatAcrossPages() {
        val policy = AiModelPaginationPolicy(scope(), AiModelPageTokenLedger())
        policy.acceptPage(null, page(next = "token-2", modelId = "fake.echo"))

        assertThrows(IllegalStateException::class.java) {
            policy.acceptPage("token-2", page(next = null, modelId = "fake.echo"))
        }
    }

    @Test
    fun tokenLedgerFailsClosedAtItsExplicitCapacity() {
        val ledger = AiModelPageTokenLedger(maximumConsumedTokens = 2)
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
            scope().copy(pageSize = AiProviderLimits.MAX_MODELS_PER_PAGE + 1)
        }

        val policy = AiModelPaginationPolicy(scope().copy(pageSize = 1), AiModelPageTokenLedger())
        assertThrows(IllegalArgumentException::class.java) {
            policy.acceptPage(
                null,
                AiModelPage(
                    listingGeneration = "generation-1",
                    models = listOf(model("fake.first"), model("fake.second")),
                ),
            )
        }
    }

    private fun scope() = AiModelTokenScope(
        packageName = "io.example.fake",
        signerSha256 = "a".repeat(64),
        providerId = "fake.local",
        protocolVersion = AiProtocolVersion(1, 0),
        listingGeneration = "generation-1",
        pageSize = 10,
    )

    private fun page(
        next: String?,
        generation: String = "generation-1",
        modelId: String = "fake.echo",
    ) = AiModelPage(
        listingGeneration = generation,
        models = listOf(model(modelId)),
        nextPageToken = next,
    )

    private fun model(modelId: String) = AiModelInfo(
        modelId = modelId,
        displayName = "Fake echo",
        capabilityIds = emptyList(),
        maximumContextBytes = 1024,
        maximumOutputBytes = 1024,
    )
}
