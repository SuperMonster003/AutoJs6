package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.autojs.plugin.ai.text.api.AiModelPage
import org.autojs.plugin.ai.text.api.AiModelPageTokenLedger
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextLimits
import org.autojs.plugin.ai.text.api.AiTextProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiTextProviderModelListingTest {

    @Test
    fun defaultOffTerminatesWithoutProducingADispatch() {
        val policy = policy(enabled = false)

        val decision = terminal(policy.start())

        assertSame(AiTextProviderModelListingOutcome.Disabled, decision.outcome)
        assertSame(AiTextProviderModelListingDecision.Ignored, policy.start())
        assertSame(AiTextProviderModelListingDecision.Ignored, policy.timeout())
    }

    @Test
    fun singlePageProducesAnImmutablePinnedCatalog() {
        val pinned = pinned()
        val policy = policy(pinned = pinned)
        val dispatch = dispatch(policy.start())
        val request = AiTextCodec.decodeModelListRequest(dispatch.copyRequestEnvelope())
        val callerCopy = dispatch.copyRequestEnvelope()
        callerCopy.fill(0)

        assertEquals(pinned.protocolVersion, request.protocolVersion)
        assertEquals(DEFAULT_PAGE_SIZE, request.pageSize)
        assertEquals(null, request.pageToken)
        assertFalse(dispatch.isContinuation)
        assertEquals(
            request,
            AiTextCodec.decodeModelListRequest(dispatch.copyRequestEnvelope()),
        )

        val completed = completed(
            policy.acceptPage(
                sequence = dispatch.sequence,
                callingUid = PINNED_UID,
                envelope = page(models = listOf(model(MODEL_ONE))),
            ),
        )

        assertEquals(pinned, completed.catalog.pinnedProvider)
        assertEquals(LISTING_GENERATION, completed.catalog.listingGeneration)
        assertEquals(listOf(MODEL_ONE), completed.catalog.models.map(AiModelInfo::modelId))
    }

    @Test
    fun multipageListingKeepsTheTokenOpaqueAndAggregatesInOrder() {
        val policy = policy(pageSize = 1)
        val first = dispatch(policy.start())

        val secondDecision = policy.acceptPage(
            first.sequence,
            PINNED_UID,
            page(models = listOf(model(MODEL_ONE)), next = SECRET_TOKEN),
        )
        val second = dispatch(secondDecision)
        val secondRequest: AiModelListRequest = AiTextCodec.decodeModelListRequest(second.copyRequestEnvelope())

        assertTrue(second.isContinuation)
        assertEquals(SECRET_TOKEN, secondRequest.pageToken)
        assertFalse(second.toString().contains(SECRET_TOKEN))
        assertFalse(secondDecision.toString().contains(SECRET_TOKEN))

        val completed = completed(
            policy.acceptPage(
                second.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_TWO))),
            ),
        )
        assertEquals(listOf(MODEL_ONE, MODEL_TWO), completed.catalog.models.map(AiModelInfo::modelId))
    }

    @Test
    fun callbackUidIsCheckedBeforePageAndErrorDecoding() {
        val malformed = byteArrayOf(1, 2, 3)
        val pagePolicy = policy()
        val pageDispatch = dispatch(pagePolicy.start())
        assertFailure(
            AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH,
            pagePolicy.acceptPage(pageDispatch.sequence, PINNED_UID + 1, malformed),
        )

        val errorPolicy = policy()
        val errorDispatch = dispatch(errorPolicy.start())
        assertFailure(
            AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH,
            errorPolicy.acceptFailed(errorDispatch.sequence, PINNED_UID + 1, malformed),
        )
    }

    @Test
    fun nullEmptyOversizeAndMalformedPagesAreRejectedPrecisely() {
        val cases = listOf(
            null to AiTextProviderModelListingFailure.PAGE_MISSING,
            byteArrayOf() to AiTextProviderModelListingFailure.PAGE_EMPTY,
            ByteArray(AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1) to
                AiTextProviderModelListingFailure.PAGE_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiTextProviderModelListingFailure.PAGE_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val policy = policy()
            val dispatch = dispatch(policy.start())
            assertFailure(expected, policy.acceptPage(dispatch.sequence, PINNED_UID, envelope))
        }
    }

    @Test
    fun nullEmptyOversizeAndMalformedErrorsAreRejectedPrecisely() {
        val cases = listOf(
            null to AiTextProviderModelListingFailure.ERROR_MISSING,
            byteArrayOf() to AiTextProviderModelListingFailure.ERROR_EMPTY,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                AiTextProviderModelListingFailure.ERROR_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiTextProviderModelListingFailure.ERROR_INVALID,
        )

        cases.forEach { (envelope, expected) ->
            val policy = policy()
            val dispatch = dispatch(policy.start())
            assertFailure(expected, policy.acceptFailed(dispatch.sequence, PINNED_UID, envelope))
        }
    }

    @Test
    fun providerFailureIsTerminalAndItsToStringIsPayloadFree() {
        val policy = policy()
        val dispatch = dispatch(policy.start())
        val error = AiError(
            code = AiErrorCode.INVALID_REQUEST,
            message = SECRET_ERROR_MESSAGE,
            providerCode = SECRET_PROVIDER_CODE,
        )

        val decision = terminal(
            policy.acceptFailed(
                dispatch.sequence,
                PINNED_UID,
                AiCommonCodec.encodeError(error),
            ),
        )
        val failed = decision.outcome as AiTextProviderModelListingOutcome.ProviderFailed

        assertEquals(error.code, failed.error.code)
        assertEquals(error.retryDisposition, failed.error.retryDisposition)
        assertTrue(failed.error.providerCodePresent)
        assertFalse(decision.toString().contains(SECRET_ERROR_MESSAGE))
        assertFalse(decision.toString().contains(SECRET_PROVIDER_CODE))
        assertFalse(failed.error.toString().contains(SECRET_PROVIDER_CODE))
        assertSame(
            AiTextProviderModelListingDecision.Ignored,
            policy.acceptPage(dispatch.sequence, PINNED_UID, page()),
        )
    }

    @Test
    fun generationDriftTokenCycleAndRepeatedModelAreRejected() {
        val generationPolicy = policy(pageSize = 1)
        val generationFirst = dispatch(generationPolicy.start())
        val generationSecond = dispatch(
            generationPolicy.acceptPage(
                generationFirst.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE)), next = "generation-token"),
            ),
        )
        assertFailure(
            AiTextProviderModelListingFailure.PAGINATION_INVALID,
            generationPolicy.acceptPage(
                generationSecond.sequence,
                PINNED_UID,
                page(
                    generation = "changed-generation",
                    models = listOf(model(MODEL_TWO)),
                ),
            ),
        )

        val cyclePolicy = policy(pageSize = 1)
        val cycleFirst = dispatch(cyclePolicy.start())
        val cycleSecond = dispatch(
            cyclePolicy.acceptPage(
                cycleFirst.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE)), next = "cycle-token"),
            ),
        )
        assertFailure(
            AiTextProviderModelListingFailure.PAGINATION_INVALID,
            cyclePolicy.acceptPage(
                cycleSecond.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_TWO)), next = "cycle-token"),
            ),
        )

        val duplicatePolicy = policy(pageSize = 1)
        val duplicateFirst = dispatch(duplicatePolicy.start())
        val duplicateSecond = dispatch(
            duplicatePolicy.acceptPage(
                duplicateFirst.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE)), next = "duplicate-token"),
            ),
        )
        assertFailure(
            AiTextProviderModelListingFailure.PAGINATION_INVALID,
            duplicatePolicy.acceptPage(
                duplicateSecond.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE))),
            ),
        )
    }

    @Test
    fun modelCapabilitiesCannotExceedThePinnedNegotiation() {
        val pinned = pinned(
            capabilities = AiTextHostTestFixtures.capabilities(supportsTools = false),
        )
        val policy = policy(pinned = pinned)
        val dispatch = dispatch(policy.start())

        assertFailure(
            AiTextProviderModelListingFailure.MODEL_CAPABILITY_MISMATCH,
            policy.acceptPage(
                dispatch.sequence,
                PINNED_UID,
                page(
                    models = listOf(
                        model(
                            modelId = MODEL_ONE,
                            capabilities = listOf(AiTextCapabilityId.TOOLS),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun maximumPageCountAndTokenLedgerCapacityAreFailClosed() {
        val exactlyBounded = policy(pageSize = 1)
        var exactPending = dispatch(exactlyBounded.start())
        repeat(AiTextLimits.MAX_MODEL_PAGES - 1) { index ->
            exactPending = dispatch(
                exactlyBounded.acceptPage(
                    exactPending.sequence,
                    PINNED_UID,
                    page(
                        models = listOf(model("fixture.model-${index + 1}")),
                        next = "page-token-${index + 2}",
                    ),
                ),
            )
        }
        val exactCatalog = completed(
            exactlyBounded.acceptPage(
                exactPending.sequence,
                PINNED_UID,
                page(models = listOf(model("fixture.model-${AiTextLimits.MAX_MODEL_PAGES}"))),
            ),
        ).catalog
        assertEquals(AiTextLimits.MAX_MODEL_PAGES, exactCatalog.models.size)

        val overflowing = policy(pageSize = 1)
        var overflowPending = dispatch(overflowing.start())
        repeat(AiTextLimits.MAX_MODEL_PAGES - 1) { index ->
            overflowPending = dispatch(
                overflowing.acceptPage(
                    overflowPending.sequence,
                    PINNED_UID,
                    page(
                        models = listOf(model("fixture.overflow-model-${index + 1}")),
                        next = "overflow-token-${index + 2}",
                    ),
                ),
            )
        }
        assertFailure(
            AiTextProviderModelListingFailure.PAGINATION_INVALID,
            overflowing.acceptPage(
                overflowPending.sequence,
                PINNED_UID,
                page(
                    models = listOf(model("fixture.overflow-model-${AiTextLimits.MAX_MODEL_PAGES}")),
                    next = "must-not-be-dispatched",
                ),
            ),
        )

        val exhausted = policy(
            pageSize = 1,
            tokenLedger = AiModelPageTokenLedger(maximumConsumedTokens = 1),
        )
        val first = dispatch(exhausted.start())
        val second = dispatch(
            exhausted.acceptPage(
                first.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE)), next = "ledger-token-1"),
            ),
        )
        assertFailure(
            AiTextProviderModelListingFailure.PAGINATION_INVALID,
            exhausted.acceptPage(
                second.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_TWO)), next = "ledger-token-2"),
            ),
        )
    }

    @Test
    fun sharedDispatchLedgerRejectsReissuedTokenAfterAnyFirstAttemptTerminal() {
        val terminalizers = listOf<(AiTextProviderModelListingPolicy, AiTextProviderModelListingDispatch) -> Unit>(
            { firstPolicy, _ ->
                assertSame(
                    AiTextProviderModelListingOutcome.TimedOut,
                    terminal(firstPolicy.timeout()).outcome,
                )
            },
            { firstPolicy, continuation ->
                val failed = terminal(
                    firstPolicy.acceptFailed(
                        continuation.sequence,
                        PINNED_UID,
                        AiCommonCodec.encodeError(AiError(code = AiErrorCode.INVALID_REQUEST)),
                    ),
                ).outcome
                assertTrue(failed is AiTextProviderModelListingOutcome.ProviderFailed)
            },
            { firstPolicy, continuation ->
                assertFailure(
                    AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH,
                    firstPolicy.acceptPage(
                        continuation.sequence,
                        PINNED_UID + 1,
                        byteArrayOf(1, 2, 3),
                    ),
                )
            },
        )

        terminalizers.forEach { terminateFirstAttempt ->
            val sharedLedger = AiModelPageTokenLedger()
            val firstPolicy = policy(pageSize = 1, tokenLedger = sharedLedger)
            val firstDispatch = dispatch(firstPolicy.start())
            val continuation = dispatch(
                firstPolicy.acceptPage(
                    firstDispatch.sequence,
                    PINNED_UID,
                    page(models = listOf(model(MODEL_ONE)), next = SHARED_TOKEN),
                ),
            )
            terminateFirstAttempt(firstPolicy, continuation)

            val secondPolicy = policy(pageSize = 1, tokenLedger = sharedLedger)
            val secondDispatch = dispatch(secondPolicy.start())
            assertFailure(
                AiTextProviderModelListingFailure.PAGINATION_INVALID,
                secondPolicy.acceptPage(
                    secondDispatch.sequence,
                    PINNED_UID,
                    page(models = listOf(model(MODEL_ONE)), next = SHARED_TOKEN),
                ),
            )
        }
    }

    @Test
    fun staleDuplicateAndLateCallbacksCannotPublishAnotherTerminal() {
        val stalePolicy = policy()
        val staleDispatch = dispatch(stalePolicy.start())
        assertFailure(
            AiTextProviderModelListingFailure.STALE_CALLBACK,
            stalePolicy.acceptPage(staleDispatch.sequence + 1L, PINNED_UID, byteArrayOf(1, 2, 3)),
        )

        val duplicatePolicy = policy(pageSize = 1)
        val first = dispatch(duplicatePolicy.start())
        val second = dispatch(
            duplicatePolicy.acceptPage(
                first.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_ONE)), next = "next-token"),
            ),
        )
        assertFailure(
            AiTextProviderModelListingFailure.STALE_CALLBACK,
            duplicatePolicy.acceptPage(first.sequence, PINNED_UID, page()),
        )
        assertSame(
            AiTextProviderModelListingDecision.Ignored,
            duplicatePolicy.acceptPage(second.sequence, PINNED_UID, page()),
        )

        val completedPolicy = policy()
        val completedDispatch = dispatch(completedPolicy.start())
        completed(completedPolicy.acceptPage(completedDispatch.sequence, PINNED_UID, page()))
        assertSame(
            AiTextProviderModelListingDecision.Ignored,
            completedPolicy.acceptPage(completedDispatch.sequence, PINNED_UID, page()),
        )
        assertSame(
            AiTextProviderModelListingDecision.Ignored,
            completedPolicy.acceptFailed(completedDispatch.sequence, PINNED_UID, byteArrayOf(1)),
        )
    }

    @Test
    fun cancelTimeoutAndDeathEachOwnTheTerminalExactlyOnce() {
        val cancelled = policy()
        val cancelledDispatch = dispatch(cancelled.start())
        assertSame(AiTextProviderModelListingOutcome.Cancelled, terminal(cancelled.cancel()).outcome)
        assertSame(AiTextProviderModelListingDecision.Ignored, cancelled.timeout())
        assertSame(AiTextProviderModelListingDecision.Ignored, cancelled.binderDied())
        assertSame(
            AiTextProviderModelListingDecision.Ignored,
            cancelled.acceptPage(cancelledDispatch.sequence, PINNED_UID, page()),
        )

        val timedOut = policy()
        dispatch(timedOut.start())
        assertSame(AiTextProviderModelListingOutcome.TimedOut, terminal(timedOut.timeout()).outcome)
        assertSame(AiTextProviderModelListingDecision.Ignored, timedOut.cancel())

        val died = policy()
        dispatch(died.start())
        assertSame(AiTextProviderModelListingOutcome.BinderDied, terminal(died.binderDied()).outcome)
        assertSame(AiTextProviderModelListingDecision.Ignored, died.cancel())
    }

    @Test
    fun concurrentCancelTimeoutAndDeathStillPublishOnlyOneTerminal() {
        repeat(20) {
            val policy = policy()
            dispatch(policy.start())
            val ready = CountDownLatch(3)
            val fire = CountDownLatch(1)
            val decisions = ConcurrentLinkedQueue<AiTextProviderModelListingDecision>()
            val executor = Executors.newFixedThreadPool(3)
            listOf(policy::cancel, policy::timeout, policy::binderDied).forEach { action ->
                executor.execute {
                    ready.countDown()
                    fire.await(2, TimeUnit.SECONDS)
                    decisions += action()
                }
            }
            try {
                assertTrue(ready.await(2, TimeUnit.SECONDS))
                fire.countDown()
                executor.shutdown()
                assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
                assertEquals(1, decisions.count { it is AiTextProviderModelListingDecision.Terminal })
                assertEquals(2, decisions.count { it === AiTextProviderModelListingDecision.Ignored })
            } finally {
                fire.countDown()
                executor.shutdown()
                executor.awaitTermination(2, TimeUnit.SECONDS)
            }
        }
    }

    @Test
    fun finalPageAndProviderFailureEachRaceCancelToOneTerminal() {
        val callbackActions = listOf<
            (AiTextProviderModelListingPolicy, AiTextProviderModelListingDispatch) ->
                AiTextProviderModelListingDecision
            >(
            { policy, pending ->
                policy.acceptPage(pending.sequence, PINNED_UID, page())
            },
            { policy, pending ->
                policy.acceptFailed(
                    pending.sequence,
                    PINNED_UID,
                    AiCommonCodec.encodeError(AiError(code = AiErrorCode.INVALID_REQUEST)),
                )
            },
        )

        callbackActions.forEach { callback ->
            repeat(20) {
                val policy = policy()
                val pending = dispatch(policy.start())
                val ready = CountDownLatch(2)
                val fire = CountDownLatch(1)
                val decisions = ConcurrentLinkedQueue<AiTextProviderModelListingDecision>()
                val executor = Executors.newFixedThreadPool(2)
                listOf<() -> AiTextProviderModelListingDecision>(
                    { callback(policy, pending) },
                    policy::cancel,
                ).forEach { action ->
                    executor.execute {
                        ready.countDown()
                        fire.await(2, TimeUnit.SECONDS)
                        decisions += action()
                    }
                }
                try {
                    assertTrue(ready.await(2, TimeUnit.SECONDS))
                    fire.countDown()
                    executor.shutdown()
                    assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
                    assertEquals(1, decisions.count { it is AiTextProviderModelListingDecision.Terminal })
                    assertEquals(1, decisions.count { it === AiTextProviderModelListingDecision.Ignored })
                } finally {
                    fire.countDown()
                    executor.shutdown()
                    executor.awaitTermination(2, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Test
    fun completedCatalogIsImmutableAndPlansOnlyTheExactCompatibleModel() {
        val pinned = pinned()
        val policy = policy(pinned = pinned)
        val dispatch = dispatch(policy.start())
        val completed = completed(
            policy.acceptPage(
                dispatch.sequence,
                PINNED_UID,
                page(
                    models = listOf(
                        model(
                            modelId = MODEL_ONE,
                            displayName = SECRET_MODEL_DISPLAY,
                            capabilities = listOf(AiTextCapabilityId.STREAMING),
                        ),
                    ),
                ),
            ),
        )
        val catalog = completed.catalog

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (catalog.models as MutableList<AiModelInfo>).add(model(MODEL_TWO))
        }
        val planned = catalog.planExact(
            modelId = MODEL_ONE,
            requirement = AiTextCapabilityRequirement(
                requiredCapabilityIds = setOf(AiTextCapabilityId.STREAMING),
            ),
        ) as AiTextProviderExactModelPlanResult.Planned
        assertEquals(pinned, planned.plan.pinnedProvider)
        assertEquals(LISTING_GENERATION, planned.plan.listingGeneration)
        assertEquals(MODEL_ONE, planned.plan.modelId)
        assertSame(
            AiTextProviderExactModelPlanResult.ModelNotFound,
            catalog.planExact("fixture.missing"),
        )
        assertSame(
            AiTextProviderExactModelPlanResult.ModelCapabilityMismatch,
            catalog.planExact(
                modelId = MODEL_ONE,
                requirement = AiTextCapabilityRequirement(
                    requiredCapabilityIds = setOf(AiTextCapabilityId.TOOLS),
                ),
            ),
        )
        assertFalse(catalog.toString().contains(SECRET_MODEL_DISPLAY))
        assertFalse(completed.toString().contains(SECRET_MODEL_DISPLAY))
    }

    private fun policy(
        pinned: AiTextProviderPinnedIdentity = pinned(),
        enabled: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        tokenLedger: AiModelPageTokenLedger = AiModelPageTokenLedger(),
    ) = AiTextProviderModelListingPolicy(
        pinnedProvider = pinned,
        options = AiTextProviderModelListingOptions(enabled = enabled, pageSize = pageSize),
        tokenLedger = tokenLedger,
    )

    private fun pinned(
        capabilities: org.autojs.plugin.ai.text.api.AiTextCapabilities =
            AiTextHostTestFixtures.capabilities(),
    ) = AiTextProviderPinnedIdentity(
        component = AiTextProviderComponent("example.ai.provider", "AiTextService"),
        packageUid = PINNED_UID,
        versionCode = 7L,
        lastUpdateTime = 123L,
        signerSha256 = listOf(AiTextHostTestFixtures.SIGNER_A, AiTextHostTestFixtures.SIGNER_B),
        providerInfo = AiTextHostTestFixtures.providerInfo(),
        protocolVersion = AiTextProtocol.HOST_PROTOCOL_RANGE.maximum,
        negotiatedCapabilities = capabilities,
    )

    private fun page(
        generation: String = LISTING_GENERATION,
        models: List<AiModelInfo> = listOf(model(MODEL_ONE)),
        next: String? = null,
    ): ByteArray = AiTextCodec.encodeModelPage(
        AiModelPage(
            listingGeneration = generation,
            models = models,
            nextPageToken = next,
        ),
    )

    private fun model(
        modelId: String,
        displayName: String = "Fixture model",
        capabilities: List<String> = listOf(AiTextCapabilityId.STREAMING),
    ) = AiModelInfo(
        modelId = modelId,
        displayName = displayName,
        capabilityIds = capabilities,
        maximumContextBytes = 64L * 1024L,
        maximumOutputBytes = 64L * 1024L,
    )

    private fun dispatch(
        decision: AiTextProviderModelListingDecision,
    ): AiTextProviderModelListingDispatch =
        (decision as AiTextProviderModelListingDecision.Dispatch).dispatch

    private fun terminal(
        decision: AiTextProviderModelListingDecision,
    ): AiTextProviderModelListingDecision.Terminal =
        decision as AiTextProviderModelListingDecision.Terminal

    private fun completed(
        decision: AiTextProviderModelListingDecision,
    ): AiTextProviderModelListingOutcome.Completed =
        terminal(decision).outcome as AiTextProviderModelListingOutcome.Completed

    private fun assertFailure(
        expected: AiTextProviderModelListingFailure,
        decision: AiTextProviderModelListingDecision,
    ) {
        val rejected = terminal(decision).outcome as AiTextProviderModelListingOutcome.Rejected
        assertEquals(expected, rejected.failure)
    }

    private companion object {
        const val PINNED_UID = 10_001
        const val DEFAULT_PAGE_SIZE = 50
        const val LISTING_GENERATION = "fixture-generation"
        const val MODEL_ONE = "fixture.model-one"
        const val MODEL_TWO = "fixture.model-two"
        const val SECRET_TOKEN = "opaque-secret-page-token"
        const val SHARED_TOKEN = "shared-replay-token"
        const val SECRET_ERROR_MESSAGE = "private provider failure detail"
        const val SECRET_PROVIDER_CODE = "private.provider.code"
        const val SECRET_MODEL_DISPLAY = "Private model display name"
    }
}
