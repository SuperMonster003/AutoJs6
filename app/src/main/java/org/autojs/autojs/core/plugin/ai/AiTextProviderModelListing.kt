package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiModelListRequest
import org.autojs.plugin.ai.text.api.AiModelPageTokenLedger
import org.autojs.plugin.ai.text.api.AiModelPaginationPolicy
import org.autojs.plugin.ai.text.api.AiModelTokenScope
import org.autojs.plugin.ai.text.api.AiTextCodec
import org.autojs.plugin.ai.text.api.AiTextLimits
import java.util.Collections

/** The default constructor is deliberately inert and produces no model-list dispatch. */
internal data class AiTextProviderModelListingOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..AiTextLimits.MAX_MODELS_PER_PAGE) {
            "Invalid AI text model-list page size"
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

/** A defensive copy of one request envelope; its opaque continuation token is never exposed. */
internal class AiTextProviderModelListingDispatch internal constructor(
    val sequence: Long,
    val isContinuation: Boolean,
    requestEnvelope: ByteArray,
) {
    private val requestEnvelope = requestEnvelope.copyOf()

    fun copyRequestEnvelope(): ByteArray = requestEnvelope.copyOf()

    override fun toString(): String =
        "AiTextProviderModelListingDispatch(sequence=$sequence, " +
            "isContinuation=$isContinuation, envelopeBytes=${requestEnvelope.size})"
}

internal enum class AiTextProviderModelListingFailure {
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
internal class AiTextProviderModelListingErrorSummary private constructor(
    val code: Int,
    val retryDisposition: Int,
    val providerCodePresent: Boolean,
) {
    override fun toString(): String =
        "AiTextProviderModelListingErrorSummary(code=$code, " +
            "retryDisposition=$retryDisposition, providerCodePresent=$providerCodePresent)"

    internal companion object {
        fun from(error: org.autojs.plugin.ai.common.api.AiError) =
            AiTextProviderModelListingErrorSummary(
                code = error.code,
                retryDisposition = error.retryDisposition,
                providerCodePresent = error.providerCode != null,
            )
    }
}

internal sealed class AiTextProviderExactModelPlanResult {
    data class Planned(val plan: AiTextHostSessionPlan) : AiTextProviderExactModelPlanResult()
    object ModelNotFound : AiTextProviderExactModelPlanResult()
    object ModelCapabilityMismatch : AiTextProviderExactModelPlanResult()
}

/** Immutable, provider-pinned result of one complete model-list transcript. */
internal class AiTextProviderModelCatalog internal constructor(
    val pinnedProvider: AiTextProviderPinnedIdentity,
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
        requirement: AiTextCapabilityRequirement = AiTextCapabilityRequirement(),
    ): AiTextProviderExactModelPlanResult {
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        val model = models.singleOrNull { it.modelId == modelId }
            ?: return AiTextProviderExactModelPlanResult.ModelNotFound
        return when (
            val planned = AiTextHostSessionPlanner.plan(
                selected = AiTextProviderSelectionResult.Selected(pinnedProvider),
                listingGeneration = listingGeneration,
                model = model,
                requirement = requirement,
            )
        ) {
            is AiTextSessionPlanningResult.Planned ->
                AiTextProviderExactModelPlanResult.Planned(planned.plan)
            AiTextSessionPlanningResult.ModelCapabilityMismatch ->
                AiTextProviderExactModelPlanResult.ModelCapabilityMismatch
        }
    }

    override fun toString(): String =
        "AiTextProviderModelCatalog(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "modelCount=${models.size})"
}

internal sealed class AiTextProviderModelListingOutcome {
    object Disabled : AiTextProviderModelListingOutcome()
    object Cancelled : AiTextProviderModelListingOutcome()
    object TimedOut : AiTextProviderModelListingOutcome()
    object BinderDied : AiTextProviderModelListingOutcome()

    data class Completed(
        val catalog: AiTextProviderModelCatalog,
    ) : AiTextProviderModelListingOutcome()

    data class Rejected(
        val failure: AiTextProviderModelListingFailure,
    ) : AiTextProviderModelListingOutcome()

    class ProviderFailed(
        val error: AiTextProviderModelListingErrorSummary,
    ) : AiTextProviderModelListingOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }
}

internal sealed class AiTextProviderModelListingDecision {
    data class Dispatch(
        val dispatch: AiTextProviderModelListingDispatch,
    ) : AiTextProviderModelListingDecision()

    data class Terminal(
        val outcome: AiTextProviderModelListingOutcome,
    ) : AiTextProviderModelListingDecision()

    object Ignored : AiTextProviderModelListingDecision()
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
internal class AiTextProviderModelListingPolicy(
    private val pinnedProvider: AiTextProviderPinnedIdentity,
    private val options: AiTextProviderModelListingOptions = AiTextProviderModelListingOptions(),
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
    fun start(): AiTextProviderModelListingDecision {
        if (started || terminal) return AiTextProviderModelListingDecision.Ignored
        started = true
        if (!options.enabled) {
            return terminate(AiTextProviderModelListingOutcome.Disabled)
        }
        return dispatch(pageToken = null)
    }

    @Synchronized
    fun acceptPage(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): AiTextProviderModelListingDecision {
        if (terminal) return AiTextProviderModelListingDecision.Ignored
        if (!AiTextProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        val pending = claimPending(sequence) ?: return reject(AiTextProviderModelListingFailure.STALE_CALLBACK)
        val safeEnvelope = when {
            envelope == null -> return reject(AiTextProviderModelListingFailure.PAGE_MISSING)
            envelope.isEmpty() -> return reject(AiTextProviderModelListingFailure.PAGE_EMPTY)
            envelope.size > AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES ->
                return reject(AiTextProviderModelListingFailure.PAGE_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val page = runCatching { AiTextCodec.decodeModelPage(safeEnvelope) }.getOrNull()
            ?: return reject(AiTextProviderModelListingFailure.PAGE_INVALID)
        val negotiatedCapabilityIds = pinnedProvider.negotiatedCapabilities.supportedCapabilityIds()
        if (page.models.any { !negotiatedCapabilityIds.containsAll(it.capabilityIds) }) {
            return reject(AiTextProviderModelListingFailure.MODEL_CAPABILITY_MISMATCH)
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
        }.getOrNull() ?: return reject(AiTextProviderModelListingFailure.PAGINATION_INVALID)
        if (paginationPolicy == null) {
            paginationPolicy = pagination
            listingGeneration = page.listingGeneration
        }
        if (runCatching { pagination.acceptPage(pending.pageToken, page) }.isFailure) {
            return reject(AiTextProviderModelListingFailure.PAGINATION_INVALID)
        }
        acceptedPages += 1
        val nextPageToken = page.nextPageToken
        if (nextPageToken != null) {
            if (acceptedPages >= AiTextLimits.MAX_MODEL_PAGES) {
                return reject(AiTextProviderModelListingFailure.PAGINATION_INVALID)
            }
            val scope = requireNotNull(tokenScope)
            if (runCatching { tokenLedger.consume(nextPageToken, scope) }.isFailure) {
                return reject(AiTextProviderModelListingFailure.PAGINATION_INVALID)
            }
        }
        models += page.models
        return nextPageToken?.let(::dispatch) ?: terminate(
            AiTextProviderModelListingOutcome.Completed(
                AiTextProviderModelCatalog(
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
    ): AiTextProviderModelListingDecision {
        if (terminal) return AiTextProviderModelListingDecision.Ignored
        if (!AiTextProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiTextProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        if (claimPending(sequence) == null) {
            return reject(AiTextProviderModelListingFailure.STALE_CALLBACK)
        }
        val safeEnvelope = when {
            envelope == null -> return reject(AiTextProviderModelListingFailure.ERROR_MISSING)
            envelope.isEmpty() -> return reject(AiTextProviderModelListingFailure.ERROR_EMPTY)
            envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                return reject(AiTextProviderModelListingFailure.ERROR_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val error = runCatching { AiCommonCodec.decodeError(safeEnvelope) }.getOrNull()
            ?: return reject(AiTextProviderModelListingFailure.ERROR_INVALID)
        return terminate(
            AiTextProviderModelListingOutcome.ProviderFailed(
                AiTextProviderModelListingErrorSummary.from(error),
            ),
        )
    }

    @Synchronized
    fun cancel(): AiTextProviderModelListingDecision =
        if (terminal) AiTextProviderModelListingDecision.Ignored
        else terminate(AiTextProviderModelListingOutcome.Cancelled)

    @Synchronized
    fun timeout(): AiTextProviderModelListingDecision =
        if (terminal) AiTextProviderModelListingDecision.Ignored
        else terminate(AiTextProviderModelListingOutcome.TimedOut)

    @Synchronized
    fun binderDied(): AiTextProviderModelListingDecision =
        if (terminal) AiTextProviderModelListingDecision.Ignored
        else terminate(AiTextProviderModelListingOutcome.BinderDied)

    private fun claimPending(sequence: Long): PendingDispatch? {
        val pending = pendingDispatch ?: return null
        if (pending.sequence != sequence) return null
        pendingDispatch = null
        return pending
    }

    private fun dispatch(pageToken: String?): AiTextProviderModelListingDecision.Dispatch {
        check(!terminal)
        val sequence = nextSequence++
        val request = AiModelListRequest(
            protocolVersion = pinnedProvider.protocolVersion,
            pageSize = options.pageSize,
            pageToken = pageToken,
        )
        pendingDispatch = PendingDispatch(sequence, pageToken)
        return AiTextProviderModelListingDecision.Dispatch(
            AiTextProviderModelListingDispatch(
                sequence = sequence,
                isContinuation = pageToken != null,
                requestEnvelope = AiTextCodec.encodeModelListRequest(request),
            ),
        )
    }

    private fun reject(
        failure: AiTextProviderModelListingFailure,
    ): AiTextProviderModelListingDecision.Terminal =
        terminate(AiTextProviderModelListingOutcome.Rejected(failure))

    private fun terminate(
        outcome: AiTextProviderModelListingOutcome,
    ): AiTextProviderModelListingDecision.Terminal {
        check(!terminal) { "AI text model listing already terminated" }
        terminal = true
        pendingDispatch = null
        return AiTextProviderModelListingDecision.Terminal(outcome)
    }
}
