package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.AiModelInfo
import org.autojs.plugin.ondeviceai.api.AiModelListRequest
import org.autojs.plugin.ondeviceai.api.AiModelPageTokenLedger
import org.autojs.plugin.ondeviceai.api.AiModelPaginationPolicy
import org.autojs.plugin.ondeviceai.api.AiModelTokenScope
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCodec
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import java.util.Collections

/** The default constructor is deliberately inert and produces no model-list dispatch. */
internal data class OnDeviceAiProviderModelListingOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..OnDeviceAiLimits.MAX_MODELS_PER_PAGE) {
            "Invalid on-device AI model-list page size"
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

/** A defensive copy of one request envelope; its opaque continuation token is never exposed. */
internal class OnDeviceAiProviderModelListingDispatch internal constructor(
    val sequence: Long,
    val isContinuation: Boolean,
    requestEnvelope: ByteArray,
) {
    private val requestEnvelope = requestEnvelope.copyOf()

    fun copyRequestEnvelope(): ByteArray = requestEnvelope.copyOf()

    override fun toString(): String =
        "OnDeviceAiProviderModelListingDispatch(sequence=$sequence, " +
            "isContinuation=$isContinuation, envelopeBytes=${requestEnvelope.size})"
}

internal enum class OnDeviceAiProviderModelListingFailure {
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
internal class OnDeviceAiProviderModelListingErrorSummary private constructor(
    val code: Int,
    val retryDisposition: Int,
    val providerCodePresent: Boolean,
) {
    override fun toString(): String =
        "OnDeviceAiProviderModelListingErrorSummary(code=$code, " +
            "retryDisposition=$retryDisposition, providerCodePresent=$providerCodePresent)"

    internal companion object {
        fun from(error: org.autojs.plugin.ai.common.api.AiError) =
            OnDeviceAiProviderModelListingErrorSummary(
                code = error.code,
                retryDisposition = error.retryDisposition,
                providerCodePresent = error.providerCode != null,
            )
    }
}

internal sealed class OnDeviceAiProviderExactModelPlanResult {
    data class Planned(val plan: OnDeviceAiHostSessionPlan) : OnDeviceAiProviderExactModelPlanResult()
    object ModelNotFound : OnDeviceAiProviderExactModelPlanResult()
    object ModelCapabilityMismatch : OnDeviceAiProviderExactModelPlanResult()
}

/** Immutable, provider-pinned result of one complete model-list transcript. */
internal class OnDeviceAiProviderModelCatalog internal constructor(
    val pinnedProvider: OnDeviceAiProviderPinnedIdentity,
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
        requirement: OnDeviceAiCapabilityRequirement = OnDeviceAiCapabilityRequirement(),
    ): OnDeviceAiProviderExactModelPlanResult {
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        val model = models.singleOrNull { it.modelId == modelId }
            ?: return OnDeviceAiProviderExactModelPlanResult.ModelNotFound
        return when (
            val planned = OnDeviceAiHostSessionPlanner.plan(
                selected = OnDeviceAiProviderSelectionResult.Selected(pinnedProvider),
                listingGeneration = listingGeneration,
                model = model,
                requirement = requirement,
            )
        ) {
            is OnDeviceAiSessionPlanningResult.Planned ->
                OnDeviceAiProviderExactModelPlanResult.Planned(planned.plan)
            OnDeviceAiSessionPlanningResult.ModelCapabilityMismatch ->
                OnDeviceAiProviderExactModelPlanResult.ModelCapabilityMismatch
        }
    }

    override fun toString(): String =
        "OnDeviceAiProviderModelCatalog(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "modelCount=${models.size})"
}

internal sealed class OnDeviceAiProviderModelListingOutcome {
    object Disabled : OnDeviceAiProviderModelListingOutcome()
    object Cancelled : OnDeviceAiProviderModelListingOutcome()
    object TimedOut : OnDeviceAiProviderModelListingOutcome()
    object BinderDied : OnDeviceAiProviderModelListingOutcome()

    data class Completed(
        val catalog: OnDeviceAiProviderModelCatalog,
    ) : OnDeviceAiProviderModelListingOutcome()

    data class Rejected(
        val failure: OnDeviceAiProviderModelListingFailure,
    ) : OnDeviceAiProviderModelListingOutcome()

    class ProviderFailed(
        val error: OnDeviceAiProviderModelListingErrorSummary,
    ) : OnDeviceAiProviderModelListingOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }
}

internal sealed class OnDeviceAiProviderModelListingDecision {
    data class Dispatch(
        val dispatch: OnDeviceAiProviderModelListingDispatch,
    ) : OnDeviceAiProviderModelListingDecision()

    data class Terminal(
        val outcome: OnDeviceAiProviderModelListingOutcome,
    ) : OnDeviceAiProviderModelListingDecision()

    object Ignored : OnDeviceAiProviderModelListingDecision()
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
internal class OnDeviceAiProviderModelListingPolicy(
    private val pinnedProvider: OnDeviceAiProviderPinnedIdentity,
    private val options: OnDeviceAiProviderModelListingOptions = OnDeviceAiProviderModelListingOptions(),
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
    fun start(): OnDeviceAiProviderModelListingDecision {
        if (started || terminal) return OnDeviceAiProviderModelListingDecision.Ignored
        started = true
        if (!options.enabled) {
            return terminate(OnDeviceAiProviderModelListingOutcome.Disabled)
        }
        return dispatch(pageToken = null)
    }

    @Synchronized
    fun acceptPage(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): OnDeviceAiProviderModelListingDecision {
        if (terminal) return OnDeviceAiProviderModelListingDecision.Ignored
        if (!OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(OnDeviceAiProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        val pending = claimPending(sequence) ?: return reject(OnDeviceAiProviderModelListingFailure.STALE_CALLBACK)
        val safeEnvelope = when {
            envelope == null -> return reject(OnDeviceAiProviderModelListingFailure.PAGE_MISSING)
            envelope.isEmpty() -> return reject(OnDeviceAiProviderModelListingFailure.PAGE_EMPTY)
            envelope.size > OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES ->
                return reject(OnDeviceAiProviderModelListingFailure.PAGE_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val page = runCatching { OnDeviceAiCodec.decodeModelPage(safeEnvelope) }.getOrNull()
            ?: return reject(OnDeviceAiProviderModelListingFailure.PAGE_INVALID)
        val negotiatedCapabilityIds = pinnedProvider.negotiatedCapabilities.supportedCapabilityIds()
        if (page.models.any { !negotiatedCapabilityIds.containsAll(it.capabilityIds) }) {
            return reject(OnDeviceAiProviderModelListingFailure.MODEL_CAPABILITY_MISMATCH)
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
        }.getOrNull() ?: return reject(OnDeviceAiProviderModelListingFailure.PAGINATION_INVALID)
        if (paginationPolicy == null) {
            paginationPolicy = pagination
            listingGeneration = page.listingGeneration
        }
        if (runCatching { pagination.acceptPage(pending.pageToken, page) }.isFailure) {
            return reject(OnDeviceAiProviderModelListingFailure.PAGINATION_INVALID)
        }
        acceptedPages += 1
        val nextPageToken = page.nextPageToken
        if (nextPageToken != null) {
            if (acceptedPages >= OnDeviceAiLimits.MAX_MODEL_PAGES) {
                return reject(OnDeviceAiProviderModelListingFailure.PAGINATION_INVALID)
            }
            val scope = requireNotNull(tokenScope)
            if (runCatching { tokenLedger.consume(nextPageToken, scope) }.isFailure) {
                return reject(OnDeviceAiProviderModelListingFailure.PAGINATION_INVALID)
            }
        }
        models += page.models
        return nextPageToken?.let(::dispatch) ?: terminate(
            OnDeviceAiProviderModelListingOutcome.Completed(
                OnDeviceAiProviderModelCatalog(
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
    ): OnDeviceAiProviderModelListingDecision {
        if (terminal) return OnDeviceAiProviderModelListingDecision.Ignored
        if (!OnDeviceAiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(OnDeviceAiProviderModelListingFailure.CALLBACK_UID_MISMATCH)
        }
        if (claimPending(sequence) == null) {
            return reject(OnDeviceAiProviderModelListingFailure.STALE_CALLBACK)
        }
        val safeEnvelope = when {
            envelope == null -> return reject(OnDeviceAiProviderModelListingFailure.ERROR_MISSING)
            envelope.isEmpty() -> return reject(OnDeviceAiProviderModelListingFailure.ERROR_EMPTY)
            envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                return reject(OnDeviceAiProviderModelListingFailure.ERROR_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val error = runCatching { AiCommonCodec.decodeError(safeEnvelope) }.getOrNull()
            ?: return reject(OnDeviceAiProviderModelListingFailure.ERROR_INVALID)
        return terminate(
            OnDeviceAiProviderModelListingOutcome.ProviderFailed(
                OnDeviceAiProviderModelListingErrorSummary.from(error),
            ),
        )
    }

    @Synchronized
    fun cancel(): OnDeviceAiProviderModelListingDecision =
        if (terminal) OnDeviceAiProviderModelListingDecision.Ignored
        else terminate(OnDeviceAiProviderModelListingOutcome.Cancelled)

    @Synchronized
    fun timeout(): OnDeviceAiProviderModelListingDecision =
        if (terminal) OnDeviceAiProviderModelListingDecision.Ignored
        else terminate(OnDeviceAiProviderModelListingOutcome.TimedOut)

    @Synchronized
    fun binderDied(): OnDeviceAiProviderModelListingDecision =
        if (terminal) OnDeviceAiProviderModelListingDecision.Ignored
        else terminate(OnDeviceAiProviderModelListingOutcome.BinderDied)

    private fun claimPending(sequence: Long): PendingDispatch? {
        val pending = pendingDispatch ?: return null
        if (pending.sequence != sequence) return null
        pendingDispatch = null
        return pending
    }

    private fun dispatch(pageToken: String?): OnDeviceAiProviderModelListingDecision.Dispatch {
        check(!terminal)
        val sequence = nextSequence++
        val request = AiModelListRequest(
            protocolVersion = pinnedProvider.protocolVersion,
            pageSize = options.pageSize,
            pageToken = pageToken,
        )
        pendingDispatch = PendingDispatch(sequence, pageToken)
        return OnDeviceAiProviderModelListingDecision.Dispatch(
            OnDeviceAiProviderModelListingDispatch(
                sequence = sequence,
                isContinuation = pageToken != null,
                requestEnvelope = OnDeviceAiCodec.encodeModelListRequest(request),
            ),
        )
    }

    private fun reject(
        failure: OnDeviceAiProviderModelListingFailure,
    ): OnDeviceAiProviderModelListingDecision.Terminal =
        terminate(OnDeviceAiProviderModelListingOutcome.Rejected(failure))

    private fun terminate(
        outcome: OnDeviceAiProviderModelListingOutcome,
    ): OnDeviceAiProviderModelListingDecision.Terminal {
        check(!terminal) { "on-device AI model listing already terminated" }
        terminal = true
        pendingDispatch = null
        return OnDeviceAiProviderModelListingDecision.Terminal(outcome)
    }
}
