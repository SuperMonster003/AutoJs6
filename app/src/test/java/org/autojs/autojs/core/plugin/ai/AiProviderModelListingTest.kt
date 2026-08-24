package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiError
import org.autojs.plugin.ai.common.api.AiErrorCode
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiModelInfo
import org.autojs.plugin.ai.provider.api.AiModelListRequest
import org.autojs.plugin.ai.provider.api.AiModelPage
import org.autojs.plugin.ai.provider.api.AiModelPageTokenLedger
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderBackendUnavailableReason
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
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

class AiProviderModelListingTest {

    @Test
    fun defaultOffTerminatesWithoutProducingADispatch() {
        val policy = policy(enabled = false)

        val decision = terminal(policy.start())

        assertSame(AiProviderModelListingOutcome.Disabled, decision.outcome)
        assertSame(AiProviderModelListingDecision.Ignored, policy.start())
        assertSame(AiProviderModelListingDecision.Ignored, policy.timeout())
    }

    @Test
    fun singlePageProducesAnImmutablePinnedCatalog() {
        val pinned = pinned()
        val policy = policy(pinned = pinned)
        val dispatch = dispatch(policy.start())
        val request = AiProviderCodec.decodeModelListRequest(dispatch.copyRequestEnvelope())
        val callerCopy = dispatch.copyRequestEnvelope()
        callerCopy.fill(0)

        assertEquals(pinned.protocolVersion, request.protocolVersion)
        assertEquals(DEFAULT_PAGE_SIZE, request.pageSize)
        assertEquals(null, request.pageToken)
        assertFalse(dispatch.isContinuation)
        assertEquals(
            request,
            AiProviderCodec.decodeModelListRequest(dispatch.copyRequestEnvelope()),
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
        val secondRequest: AiModelListRequest = AiProviderCodec.decodeModelListRequest(second.copyRequestEnvelope())

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
            AiProviderModelListingFailure.CALLBACK_UID_MISMATCH,
            pagePolicy.acceptPage(pageDispatch.sequence, PINNED_UID + 1, malformed),
        )

        val errorPolicy = policy()
        val errorDispatch = dispatch(errorPolicy.start())
        assertFailure(
            AiProviderModelListingFailure.CALLBACK_UID_MISMATCH,
            errorPolicy.acceptFailed(errorDispatch.sequence, PINNED_UID + 1, malformed),
        )
    }

    @Test
    fun nullEmptyOversizeAndMalformedPagesAreRejectedPrecisely() {
        val cases = listOf(
            null to AiProviderModelListingFailure.PAGE_MISSING,
            byteArrayOf() to AiProviderModelListingFailure.PAGE_EMPTY,
            ByteArray(AiProviderLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1) to
                AiProviderModelListingFailure.PAGE_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiProviderModelListingFailure.PAGE_INVALID,
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
            null to AiProviderModelListingFailure.ERROR_MISSING,
            byteArrayOf() to AiProviderModelListingFailure.ERROR_EMPTY,
            ByteArray(AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES + 1) to
                AiProviderModelListingFailure.ERROR_TOO_LARGE,
            byteArrayOf(1, 2, 3) to AiProviderModelListingFailure.ERROR_INVALID,
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
        val failed = decision.outcome as AiProviderModelListingOutcome.ProviderFailed

        assertEquals(error.code, failed.error.code)
        assertEquals(error.retryDisposition, failed.error.retryDisposition)
        assertTrue(failed.error.providerCodePresent)
        assertFalse(decision.toString().contains(SECRET_ERROR_MESSAGE))
        assertFalse(decision.toString().contains(SECRET_PROVIDER_CODE))
        assertFalse(failed.error.toString().contains(SECRET_PROVIDER_CODE))
        assertSame(
            AiProviderModelListingDecision.Ignored,
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
            AiProviderModelListingFailure.PAGINATION_INVALID,
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
            AiProviderModelListingFailure.PAGINATION_INVALID,
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
            AiProviderModelListingFailure.PAGINATION_INVALID,
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
            capabilities = AiProviderHostTestFixtures.capabilities(supportsTools = false),
        )
        val policy = policy(pinned = pinned)
        val dispatch = dispatch(policy.start())

        assertFailure(
            AiProviderModelListingFailure.MODEL_CAPABILITY_MISMATCH,
            policy.acceptPage(
                dispatch.sequence,
                PINNED_UID,
                page(
                    models = listOf(
                        model(
                            modelId = MODEL_ONE,
                            capabilities = listOf(AiProviderCapabilityId.TOOLS),
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
        repeat(AiProviderLimits.MAX_MODEL_PAGES - 1) { index ->
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
                page(models = listOf(model("fixture.model-${AiProviderLimits.MAX_MODEL_PAGES}"))),
            ),
        ).catalog
        assertEquals(AiProviderLimits.MAX_MODEL_PAGES, exactCatalog.models.size)

        val overflowing = policy(pageSize = 1)
        var overflowPending = dispatch(overflowing.start())
        repeat(AiProviderLimits.MAX_MODEL_PAGES - 1) { index ->
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
            AiProviderModelListingFailure.PAGINATION_INVALID,
            overflowing.acceptPage(
                overflowPending.sequence,
                PINNED_UID,
                page(
                    models = listOf(model("fixture.overflow-model-${AiProviderLimits.MAX_MODEL_PAGES}")),
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
            AiProviderModelListingFailure.PAGINATION_INVALID,
            exhausted.acceptPage(
                second.sequence,
                PINNED_UID,
                page(models = listOf(model(MODEL_TWO)), next = "ledger-token-2"),
            ),
        )
    }

    @Test
    fun sharedDispatchLedgerRejectsReissuedTokenAfterAnyFirstAttemptTerminal() {
        val terminalizers = listOf<(AiProviderModelListingPolicy, AiProviderModelListingDispatch) -> Unit>(
            { firstPolicy, _ ->
                assertSame(
                    AiProviderModelListingOutcome.TimedOut,
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
                assertTrue(failed is AiProviderModelListingOutcome.ProviderFailed)
            },
            { firstPolicy, continuation ->
                assertFailure(
                    AiProviderModelListingFailure.CALLBACK_UID_MISMATCH,
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
                AiProviderModelListingFailure.PAGINATION_INVALID,
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
            AiProviderModelListingFailure.STALE_CALLBACK,
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
            AiProviderModelListingFailure.STALE_CALLBACK,
            duplicatePolicy.acceptPage(first.sequence, PINNED_UID, page()),
        )
        assertSame(
            AiProviderModelListingDecision.Ignored,
            duplicatePolicy.acceptPage(second.sequence, PINNED_UID, page()),
        )

        val completedPolicy = policy()
        val completedDispatch = dispatch(completedPolicy.start())
        completed(completedPolicy.acceptPage(completedDispatch.sequence, PINNED_UID, page()))
        assertSame(
            AiProviderModelListingDecision.Ignored,
            completedPolicy.acceptPage(completedDispatch.sequence, PINNED_UID, page()),
        )
        assertSame(
            AiProviderModelListingDecision.Ignored,
            completedPolicy.acceptFailed(completedDispatch.sequence, PINNED_UID, byteArrayOf(1)),
        )
    }

    @Test
    fun cancelTimeoutAndDeathEachOwnTheTerminalExactlyOnce() {
        val cancelled = policy()
        val cancelledDispatch = dispatch(cancelled.start())
        assertSame(AiProviderModelListingOutcome.Cancelled, terminal(cancelled.cancel()).outcome)
        assertSame(AiProviderModelListingDecision.Ignored, cancelled.timeout())
        assertSame(AiProviderModelListingDecision.Ignored, cancelled.binderDied())
        assertSame(
            AiProviderModelListingDecision.Ignored,
            cancelled.acceptPage(cancelledDispatch.sequence, PINNED_UID, page()),
        )

        val timedOut = policy()
        dispatch(timedOut.start())
        assertSame(AiProviderModelListingOutcome.TimedOut, terminal(timedOut.timeout()).outcome)
        assertSame(AiProviderModelListingDecision.Ignored, timedOut.cancel())

        val died = policy()
        dispatch(died.start())
        assertSame(AiProviderModelListingOutcome.BinderDied, terminal(died.binderDied()).outcome)
        assertSame(AiProviderModelListingDecision.Ignored, died.cancel())
    }

    @Test
    fun concurrentCancelTimeoutAndDeathStillPublishOnlyOneTerminal() {
        repeat(20) {
            val policy = policy()
            dispatch(policy.start())
            val ready = CountDownLatch(3)
            val fire = CountDownLatch(1)
            val decisions = ConcurrentLinkedQueue<AiProviderModelListingDecision>()
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
                assertEquals(1, decisions.count { it is AiProviderModelListingDecision.Terminal })
                assertEquals(2, decisions.count { it === AiProviderModelListingDecision.Ignored })
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
            (AiProviderModelListingPolicy, AiProviderModelListingDispatch) ->
                AiProviderModelListingDecision
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
                val decisions = ConcurrentLinkedQueue<AiProviderModelListingDecision>()
                val executor = Executors.newFixedThreadPool(2)
                listOf<() -> AiProviderModelListingDecision>(
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
                    assertEquals(1, decisions.count { it is AiProviderModelListingDecision.Terminal })
                    assertEquals(1, decisions.count { it === AiProviderModelListingDecision.Ignored })
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
                            capabilities = listOf(AiProviderCapabilityId.STREAMING),
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
            requirement = AiProviderCapabilityRequirement(
                requiredCapabilityIds = setOf(AiProviderCapabilityId.STREAMING),
            ),
        ) as AiProviderExactModelPlanResult.Planned
        assertEquals(pinned, planned.plan.pinnedProvider)
        assertEquals(LISTING_GENERATION, planned.plan.listingGeneration)
        assertEquals(MODEL_ONE, planned.plan.modelId)
        assertSame(
            AiProviderExactModelPlanResult.ModelNotFound,
            catalog.planExact("fixture.missing"),
        )
        assertSame(
            AiProviderExactModelPlanResult.ModelCapabilityMismatch,
            catalog.planExact(
                modelId = MODEL_ONE,
                requirement = AiProviderCapabilityRequirement(
                    requiredCapabilityIds = setOf(AiProviderCapabilityId.TOOLS),
                ),
            ),
        )
        assertSame(
            AiProviderExactModelPlanResult.BackendUnavailable,
            catalog.planExact(MODEL_ONE, backendProfile = AiProviderBackendProfile.GPU),
        )
        assertFalse(catalog.toString().contains(SECRET_MODEL_DISPLAY))
        assertFalse(completed.toString().contains(SECRET_MODEL_DISPLAY))
    }

    private fun policy(
        pinned: AiProviderPinnedIdentity = pinned(),
        enabled: Boolean = true,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        tokenLedger: AiModelPageTokenLedger = AiModelPageTokenLedger(),
    ) = AiProviderModelListingPolicy(
        pinnedProvider = pinned,
        options = AiProviderModelListingOptions(enabled = enabled, pageSize = pageSize),
        tokenLedger = tokenLedger,
    )

    private fun pinned(
        capabilities: org.autojs.plugin.ai.provider.api.AiProviderCapabilities =
            AiProviderHostTestFixtures.capabilities(),
    ) = AiProviderPinnedIdentity(
        component = AiProviderComponent("example.ai.provider", "AiProviderService"),
        packageUid = PINNED_UID,
        versionCode = 7L,
        lastUpdateTime = 123L,
        signerSha256 = listOf(AiProviderHostTestFixtures.SIGNER_A, AiProviderHostTestFixtures.SIGNER_B),
        providerInfo = AiProviderHostTestFixtures.providerInfo(),
        protocolVersion = AiProviderProtocol.HOST_PROTOCOL_RANGE.maximum,
        negotiatedCapabilities = capabilities,
    )

    private fun page(
        generation: String = LISTING_GENERATION,
        models: List<AiModelInfo> = listOf(model(MODEL_ONE)),
        next: String? = null,
    ): ByteArray = AiProviderCodec.encodeModelPage(
        AiModelPage(
            listingGeneration = generation,
            models = models,
            nextPageToken = next,
        ),
    )

    private fun model(
        modelId: String,
        displayName: String = "Fixture model",
        capabilities: List<String> = listOf(AiProviderCapabilityId.STREAMING),
        backendProfiles: List<AiBackendProfileInfo> = listOf(
            AiBackendProfileInfo(
                AiProviderBackendProfile.CPU,
                AiProviderBackendAvailability.AVAILABLE,
            ),
            AiBackendProfileInfo(
                AiProviderBackendProfile.GPU,
                AiProviderBackendAvailability.UNAVAILABLE,
                AiProviderBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
            ),
        ),
    ) = AiModelInfo(
        modelId = modelId,
        displayName = displayName,
        capabilityIds = capabilities,
        maximumContextBytes = 64L * 1024L,
        maximumOutputBytes = 64L * 1024L,
        backendProfiles = backendProfiles,
    )

    private fun dispatch(
        decision: AiProviderModelListingDecision,
    ): AiProviderModelListingDispatch =
        (decision as AiProviderModelListingDecision.Dispatch).dispatch

    private fun terminal(
        decision: AiProviderModelListingDecision,
    ): AiProviderModelListingDecision.Terminal =
        decision as AiProviderModelListingDecision.Terminal

    private fun completed(
        decision: AiProviderModelListingDecision,
    ): AiProviderModelListingOutcome.Completed =
        terminal(decision).outcome as AiProviderModelListingOutcome.Completed

    private fun assertFailure(
        expected: AiProviderModelListingFailure,
        decision: AiProviderModelListingDecision,
    ) {
        val rejected = terminal(decision).outcome as AiProviderModelListingOutcome.Rejected
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
