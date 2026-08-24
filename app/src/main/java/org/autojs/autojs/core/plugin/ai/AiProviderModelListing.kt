package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiModelInfo
import org.autojs.plugin.ai.provider.api.AiModelListRequest
import org.autojs.plugin.ai.provider.api.AiModelPageTokenLedger
import org.autojs.plugin.ai.provider.api.AiModelPaginationPolicy
import org.autojs.plugin.ai.provider.api.AiModelTokenScope
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import java.util.Collections

/** The default constructor is deliberately inert and produces no model-list dispatch. */
internal data class AiProviderModelListingOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..AiProviderLimits.MAX_MODELS_PER_PAGE) {
            "Invalid local AI model-list page size"
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

/** A defensive copy of one request envelope; its opaque continuation token is never exposed. */
internal class AiProviderModelListingDispatch internal constructor(
    val sequence: Long,
    val isContinuation: Boolean,
    requestEnvelope: ByteArray,
) {
    private val requestEnvelope = requestEnvelope.copyOf()

    fun copyRequestEnvelope(): ByteArray = requestEnvelope.copyOf()

    override fun toString(): String =
        "AiProviderModelListingDispatch(sequence=$sequence, " +
            "isContinuation=$isContinuation, envelopeBytes=${requestEnvelope.size})"
}

internal enum class AiProviderModelListingFailure {
    CALLBACK_UID_MISMATCH,
    STALE_CALLBACK,
    PAGE_MISSING,
    PAGE_EMPTY,
    PAGE_TOO_LARGE,
    PAGE_INVALID,
    ERROR_MISSING,
    ERROR_EMPTY,
    ERROR_TOO_LARGE,
    ERROR_INVALID,
    MODEL_CAPABILITY_MISMATCH,
    PAGINATION_INVALID,
}

/** Bounded provider-failure metadata retained after the untrusted envelope is discarded. */
internal class AiProviderModelListingErrorSummary private constructor(
    val code: Int,
    val retryDisposition: Int,
    val providerCodePresent: Boolean,
) {
    override fun toString(): String =
        "AiProviderModelListingErrorSummary(code=$code, " +
            "retryDisposition=$retryDisposition, providerCodePresent=$providerCodePresent)"

    internal companion object {
        fun from(error: org.autojs.plugin.ai.common.api.AiError) =
            AiProviderModelListingErrorSummary(
                code = error.code,
                retryDisposition = error.retryDisposition,
                providerCodePresent = error.providerCode != null,
            )
    }
}

internal sealed class AiProviderExactModelPlanResult {
    data class Planned(val plan: AiProviderHostSessionPlan) : AiProviderExactModelPlanResult()
    object ModelNotFound : AiProviderExactModelPlanResult()
    object ModelCapabilityMismatch : AiProviderExactModelPlanResult()
    object BackendUnavailable : AiProviderExactModelPlanResult()
}

/** Immutable, provider-pinned result of one complete model-list transcript. */
internal class AiProviderModelCatalog internal constructor(
    val pinnedProvider: AiProviderPinnedIdentity,
    val listingGeneration: String,
    models: Collection<AiModelInfo>,
) {
    val models: List<AiModelInfo> = Collections.unmodifiableList(ArrayList(models))

    init {
        AiValidation.requireUtf8(listingGeneration, 1, 256, "Model listing generation")
        require(this.models.map { it.modelId }.distinct().size == this.models.size) {
            "Duplicate model ID in completed catalog"
        }
    }

    fun planExact(
        modelId: String,
        requirement: AiProviderCapabilityRequirement = AiProviderCapabilityRequirement(),
        backendProfile: String = AiProviderBackendProfile.CPU,
    ): AiProviderExactModelPlanResult {
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        val model = models.singleOrNull { it.modelId == modelId }
            ?: return AiProviderExactModelPlanResult.ModelNotFound
        require(backendProfile in AiProviderBackendProfile.VALUES) { "Invalid backend profile" }
        if (model.backendProfiles.none { profile ->
                profile.profileId == backendProfile &&
                    profile.availability == AiProviderBackendAvailability.AVAILABLE
            }
        ) {
            return AiProviderExactModelPlanResult.BackendUnavailable
        }
        return when (
            val planned = AiProviderHostSessionPlanner.plan(
                selected = AiProviderSelectionResult.Selected(pinnedProvider),
                listingGeneration = listingGeneration,
                model = model,
                requirement = requirement,
            )
        ) {
            is AiProviderSessionPlanningResult.Planned ->
                AiProviderExactModelPlanResult.Planned(planned.plan)
            AiProviderSessionPlanningResult.ModelCapabilityMismatch ->
                AiProviderExactModelPlanResult.ModelCapabilityMismatch
        }
    }

    override fun toString(): String =
        "AiProviderModelCatalog(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "modelCount=${models.size})"
}

internal sealed class AiProviderModelListingOutcome {
    object Disabled : AiProviderModelListingOutcome()
    object Cancelled : AiProviderModelListingOutcome()
    object TimedOut : AiProviderModelListingOutcome()
    object BinderDied : AiProviderModelListingOutcome()

    data class Completed(
        val catalog: AiProviderModelCatalog,
    ) : AiProviderModelListingOutcome()

    data class Rejected(
        val failure: AiProviderModelListingFailure,
    ) : AiProviderModelListingOutcome()

    class ProviderFailed(
        val error: AiProviderModelListingErrorSummary,
    ) : AiProviderModelListingOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }
}

internal sealed class AiProviderModelListingDecision {
    data class Dispatch(
        val dispatch: AiProviderModelListingDispatch,
    ) : AiProviderModelListingDecision()

    data class Terminal(
        val outcome: AiProviderModelListingOutcome,
    ) : AiProviderModelListingDecision()

    object Ignored : AiProviderModelListingDecision()
}

/**
 * Transport-independent validator for an explicitly selected provider's model-list transcript.
 *
 * The caller owns binding, deadlines, and callback serialization. Decision production and
 * transport application must share one serialized lane: after applying a terminal decision, the
 * transport must not send an earlier returned but not-yet-applied dispatch. It must dispatch each
 * envelope at most once and pass the Binder calling UID captured at callback entry. The owner must
 * explicitly inject a token ledger shared for the appropriate provider/process lifecycle so token
 * reuse across transcripts is rejected before exposing another continuation dispatch. This policy
 * never binds, opens a session, transfers a descriptor, grants credit, or carries prompt data.
 */
internal class AiProviderModelListingPolicy(
    private val pinnedProvider: AiProviderPinnedIdentity,
    private val options: AiProviderModelListingOptions = AiProviderModelListingOptions(),
    private val tokenLedger: AiModelPageTokenLedger,
) {
    private data class PendingDispatch(
        val sequence: Long,
        val pageToken: String?,
    )

    private var started = false
    private var terminal = false
    private var nextSequence = 1L
    private var pendingDispatch: PendingDispatch? = null
    private var paginationPolicy: AiModelPaginationPolicy? = null
    private var tokenScope: AiModelTokenScope? = null
    private var listingGeneration: String? = null
    private var acceptedPages = 0
    private val models = ArrayList<AiModelInfo>()

    @Synchronized
    fun start(): AiProviderModelListingDecision {
        if (started || terminal) return AiProviderModelListingDecision.Ignored
        started = true
        if (!options.enabled) {
            return terminate(AiProviderModelListingOutcome.Disabled)
        }
        return dispatch(pageToken = null)
    }

    @Synchronized
    fun acceptPage(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): AiProviderModelListingDecision {
        if (terminal) return AiProviderModelListingDecision.Ignored
        if (!AiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        val pending = claimPending(sequence) ?: return reject(AiProviderModelListingFailure.STALE_CALLBACK)
        val safeEnvelope = when {
            envelope == null -> return reject(AiProviderModelListingFailure.PAGE_MISSING)
            envelope.isEmpty() -> return reject(AiProviderModelListingFailure.PAGE_EMPTY)
            envelope.size > AiProviderLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES ->
                return reject(AiProviderModelListingFailure.PAGE_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val page = runCatching { AiProviderCodec.decodeModelPage(safeEnvelope) }.getOrNull()
            ?: return reject(AiProviderModelListingFailure.PAGE_INVALID)
        val negotiatedCapabilityIds = pinnedProvider.negotiatedCapabilities.supportedCapabilityIds()
        if (page.models.any { !negotiatedCapabilityIds.containsAll(it.capabilityIds) }) {
            return reject(AiProviderModelListingFailure.MODEL_CAPABILITY_MISMATCH)
        }
        val pagination = paginationPolicy ?: runCatching {
            val scope = AiModelTokenScope(
                packageName = pinnedProvider.component.packageName,
                // Compatibility-only projection for the public V1 token-key shape; never trust
                // evidence. A future transport must reverify the complete signer set in
                // [pinnedProvider] before dispatching or accepting a callback.
                signerSha256 = pinnedProvider.signerSha256.first(),
                providerId = pinnedProvider.providerId,
                protocolVersion = pinnedProvider.protocolVersion,
                listingGeneration = page.listingGeneration,
                pageSize = options.pageSize,
            )
            tokenScope = scope
            AiModelPaginationPolicy(
                scope = scope,
                // The protocol policy consumes request tokens when their response arrives. Keep
                // that transcript-local accounting separate from the caller-supplied dispatch
                // ledger (shared across transcripts when injected), which claims a continuation
                // before it is exposed to the transport.
                ledger = AiModelPageTokenLedger(),
            )
        }.getOrNull() ?: return reject(AiProviderModelListingFailure.PAGINATION_INVALID)
        if (paginationPolicy == null) {
            paginationPolicy = pagination
            listingGeneration = page.listingGeneration
        }
        if (runCatching { pagination.acceptPage(pending.pageToken, page) }.isFailure) {
            return reject(AiProviderModelListingFailure.PAGINATION_INVALID)
        }
        acceptedPages += 1
        val nextPageToken = page.nextPageToken
        if (nextPageToken != null) {
            if (acceptedPages >= AiProviderLimits.MAX_MODEL_PAGES) {
                return reject(AiProviderModelListingFailure.PAGINATION_INVALID)
            }
            val scope = requireNotNull(tokenScope)
            if (runCatching { tokenLedger.consume(nextPageToken, scope) }.isFailure) {
                return reject(AiProviderModelListingFailure.PAGINATION_INVALID)
            }
        }
        models += page.models
        return nextPageToken?.let(::dispatch) ?: terminate(
            AiProviderModelListingOutcome.Completed(
                AiProviderModelCatalog(
                    pinnedProvider = pinnedProvider,
                    listingGeneration = requireNotNull(listingGeneration),
                    models = models,
                ),
            ),
        )
    }

    @Synchronized
    fun acceptFailed(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): AiProviderModelListingDecision {
        if (terminal) return AiProviderModelListingDecision.Ignored
        if (!AiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        if (claimPending(sequence) == null) {
            return reject(AiProviderModelListingFailure.STALE_CALLBACK)
        }
        val safeEnvelope = when {
            envelope == null -> return reject(AiProviderModelListingFailure.ERROR_MISSING)
            envelope.isEmpty() -> return reject(AiProviderModelListingFailure.ERROR_EMPTY)
            envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                return reject(AiProviderModelListingFailure.ERROR_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val error = runCatching { AiCommonCodec.decodeError(safeEnvelope) }.getOrNull()
            ?: return reject(AiProviderModelListingFailure.ERROR_INVALID)
        return terminate(
            AiProviderModelListingOutcome.ProviderFailed(
                AiProviderModelListingErrorSummary.from(error),
            ),
        )
    }

    @Synchronized
    fun cancel(): AiProviderModelListingDecision =
        if (terminal) AiProviderModelListingDecision.Ignored
        else terminate(AiProviderModelListingOutcome.Cancelled)

    @Synchronized
    fun timeout(): AiProviderModelListingDecision =
        if (terminal) AiProviderModelListingDecision.Ignored
        else terminate(AiProviderModelListingOutcome.TimedOut)

    @Synchronized
    fun binderDied(): AiProviderModelListingDecision =
        if (terminal) AiProviderModelListingDecision.Ignored
        else terminate(AiProviderModelListingOutcome.BinderDied)

    private fun claimPending(sequence: Long): PendingDispatch? {
        val pending = pendingDispatch ?: return null
        if (pending.sequence != sequence) return null
        pendingDispatch = null
        return pending
    }

    private fun dispatch(pageToken: String?): AiProviderModelListingDecision.Dispatch {
        check(!terminal)
        val sequence = nextSequence++
        val request = AiModelListRequest(
            protocolVersion = pinnedProvider.protocolVersion,
            pageSize = options.pageSize,
            pageToken = pageToken,
        )
        pendingDispatch = PendingDispatch(sequence, pageToken)
        return AiProviderModelListingDecision.Dispatch(
            AiProviderModelListingDispatch(
                sequence = sequence,
                isContinuation = pageToken != null,
                requestEnvelope = AiProviderCodec.encodeModelListRequest(request),
            ),
        )
    }

    private fun reject(
        failure: AiProviderModelListingFailure,
    ): AiProviderModelListingDecision.Terminal =
        terminate(AiProviderModelListingOutcome.Rejected(failure))

    private fun terminate(
        outcome: AiProviderModelListingOutcome,
    ): AiProviderModelListingDecision.Terminal {
        check(!terminal) { "local AI model listing already terminated" }
        terminal = true
        pendingDispatch = null
        return AiProviderModelListingDecision.Terminal(outcome)
    }
}
