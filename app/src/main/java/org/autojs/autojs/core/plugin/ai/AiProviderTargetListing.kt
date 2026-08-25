package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.autojs.plugin.ai.provider.api.AiTargetCatalogPolicy
import org.autojs.plugin.ai.provider.api.AiTargetListRequest
import org.autojs.plugin.ai.provider.api.AiTargetPageTokenLedger
import org.autojs.plugin.ai.provider.api.AiTargetPaginationPolicy
import org.autojs.plugin.ai.provider.api.AiTargetTokenScope
import org.autojs.plugin.ai.provider.api.AiProviderBackendAvailability
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCodec
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import java.util.Collections

/** The default constructor is deliberately inert and produces no target-list dispatch. */
internal data class AiProviderTargetListingOptions(
    val enabled: Boolean = false,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..AiProviderLimits.MAX_TARGETS_PER_PAGE) {
            "Invalid local AI target-list page size"
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

/** A defensive copy of one request envelope; its opaque continuation token is never exposed. */
internal class AiProviderTargetListingDispatch internal constructor(
    val sequence: Long,
    val isContinuation: Boolean,
    requestEnvelope: ByteArray,
) {
    private val requestEnvelope = requestEnvelope.copyOf()

    fun copyRequestEnvelope(): ByteArray = requestEnvelope.copyOf()

    override fun toString(): String =
        "AiProviderTargetListingDispatch(sequence=$sequence, " +
            "isContinuation=$isContinuation, envelopeBytes=${requestEnvelope.size})"
}

internal enum class AiProviderTargetListingFailure {
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
    TARGET_DESCRIPTOR_MISMATCH,
    PAGINATION_INVALID,
}

/** Bounded provider-failure metadata retained after the untrusted envelope is discarded. */
internal class AiProviderTargetListingErrorSummary private constructor(
    val code: Int,
    val retryDisposition: Int,
    val providerCodePresent: Boolean,
) {
    override fun toString(): String =
        "AiProviderTargetListingErrorSummary(code=$code, " +
            "retryDisposition=$retryDisposition, providerCodePresent=$providerCodePresent)"

    internal companion object {
        fun from(error: org.autojs.plugin.ai.common.api.AiError) =
            AiProviderTargetListingErrorSummary(
                code = error.code,
                retryDisposition = error.retryDisposition,
                providerCodePresent = error.providerCode != null,
            )
    }
}

internal sealed class AiProviderExactTargetPlanResult {
    data class Planned(val plan: AiProviderHostSessionPlan) : AiProviderExactTargetPlanResult()
    object TargetNotFound : AiProviderExactTargetPlanResult()
    object TargetNotConfigured : AiProviderExactTargetPlanResult()
    object TargetCapabilityMismatch : AiProviderExactTargetPlanResult()
    object TargetUnavailable : AiProviderExactTargetPlanResult()
    object BackendUnavailable : AiProviderExactTargetPlanResult()
}

/** Immutable, provider-pinned result of one complete target-list transcript. */
internal class AiProviderTargetCatalog internal constructor(
    val pinnedProvider: AiProviderPinnedIdentity,
    val catalogGeneration: String,
    targets: Collection<AiTargetInfo>,
) {
    val targets: List<AiTargetInfo> = Collections.unmodifiableList(ArrayList(targets))
    val defaultTarget: AiTargetInfo? = this.targets.singleOrNull { it.isDefault }

    init {
        AiValidation.requireUtf8(catalogGeneration, 1, 256, "Target catalog generation")
        require(this.targets.map { it.targetId }.distinct().size == this.targets.size) {
            "Duplicate target ID in completed catalog"
        }
        require(this.targets.count { it.isDefault } <= 1) {
            "Completed catalog declares multiple default targets"
        }
    }

    fun planExact(
        targetId: String,
        requirement: AiProviderCapabilityRequirement = AiProviderCapabilityRequirement(),
        backendProfile: String? = null,
    ): AiProviderExactTargetPlanResult {
        require(AiValidation.TARGET_ID.matches(targetId)) { "Invalid target ID" }
        val target = targets.singleOrNull { it.targetId == targetId }
            ?: return AiProviderExactTargetPlanResult.TargetNotFound
        if (!target.configured) return AiProviderExactTargetPlanResult.TargetNotConfigured
        if (!target.available) return AiProviderExactTargetPlanResult.TargetUnavailable
        backendProfile?.let { selectedProfile ->
            require(selectedProfile in AiProviderBackendProfile.VALUES) { "Invalid backend profile" }
            if (target.backendProfiles.none { profile ->
                    profile.profileId == selectedProfile &&
                        profile.availability == AiProviderBackendAvailability.AVAILABLE
                }
            ) {
                return AiProviderExactTargetPlanResult.BackendUnavailable
            }
        }
        return when (
            val planned = AiProviderHostSessionPlanner.plan(
                selected = AiProviderSelectionResult.Selected(pinnedProvider),
                catalogGeneration = catalogGeneration,
                target = target,
                requirement = requirement,
            )
        ) {
            is AiProviderSessionPlanningResult.Planned ->
                AiProviderExactTargetPlanResult.Planned(planned.plan)
            AiProviderSessionPlanningResult.TargetCapabilityMismatch ->
                AiProviderExactTargetPlanResult.TargetCapabilityMismatch
        }
    }

    override fun toString(): String =
            "AiProviderTargetCatalog(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "targetCount=${targets.size})"
}

internal sealed class AiProviderTargetListingOutcome {
    object Disabled : AiProviderTargetListingOutcome()
    object Cancelled : AiProviderTargetListingOutcome()
    object TimedOut : AiProviderTargetListingOutcome()
    object BinderDied : AiProviderTargetListingOutcome()

    data class Completed(
        val catalog: AiProviderTargetCatalog,
    ) : AiProviderTargetListingOutcome()

    data class Rejected(
        val failure: AiProviderTargetListingFailure,
    ) : AiProviderTargetListingOutcome()

    class ProviderFailed(
        val error: AiProviderTargetListingErrorSummary,
    ) : AiProviderTargetListingOutcome() {
        override fun toString(): String =
            "ProviderFailed(code=${error.code}, retryDisposition=${error.retryDisposition}, " +
                "providerCodePresent=${error.providerCodePresent})"
    }
}

internal sealed class AiProviderTargetListingDecision {
    data class Dispatch(
        val dispatch: AiProviderTargetListingDispatch,
    ) : AiProviderTargetListingDecision()

    data class Terminal(
        val outcome: AiProviderTargetListingOutcome,
    ) : AiProviderTargetListingDecision()

    object Ignored : AiProviderTargetListingDecision()
}

/**
 * Transport-independent validator for an explicitly selected provider's target-list transcript.
 *
 * The caller owns binding, deadlines, and callback serialization. Decision production and
 * transport application must share one serialized lane: after applying a terminal decision, the
 * transport must not send an earlier returned but not-yet-applied dispatch. It must dispatch each
 * envelope at most once and pass the Binder calling UID captured at callback entry. The owner must
 * explicitly inject a token ledger shared for the appropriate provider/process lifecycle so token
 * reuse across transcripts is rejected before exposing another continuation dispatch. This policy
 * never binds, opens a session, transfers a descriptor, grants credit, or carries prompt data.
 */
internal class AiProviderTargetListingPolicy(
    private val pinnedProvider: AiProviderPinnedIdentity,
    private val options: AiProviderTargetListingOptions = AiProviderTargetListingOptions(),
    private val tokenLedger: AiTargetPageTokenLedger,
) {
    private data class PendingDispatch(
        val sequence: Long,
        val pageToken: String?,
    )

    private var started = false
    private var terminal = false
    private var nextSequence = 1L
    private var pendingDispatch: PendingDispatch? = null
    private var paginationPolicy: AiTargetPaginationPolicy? = null
    private var tokenScope: AiTargetTokenScope? = null
    private var catalogGeneration: String? = null
    private var acceptedPages = 0
    private val targets = ArrayList<AiTargetInfo>()

    @Synchronized
    fun start(): AiProviderTargetListingDecision {
        if (started || terminal) return AiProviderTargetListingDecision.Ignored
        started = true
        if (!options.enabled) {
            return terminate(AiProviderTargetListingOutcome.Disabled)
        }
        return dispatch(pageToken = null)
    }

    @Synchronized
    fun acceptPage(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): AiProviderTargetListingDecision {
        if (terminal) return AiProviderTargetListingDecision.Ignored
        if (!AiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiProviderTargetListingFailure.CALLBACK_UID_MISMATCH)
        }
        val pending = claimPending(sequence) ?: return reject(AiProviderTargetListingFailure.STALE_CALLBACK)
        val safeEnvelope = when {
            envelope == null -> return reject(AiProviderTargetListingFailure.PAGE_MISSING)
            envelope.isEmpty() -> return reject(AiProviderTargetListingFailure.PAGE_EMPTY)
            envelope.size > AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES ->
                return reject(AiProviderTargetListingFailure.PAGE_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val page = runCatching { AiProviderCodec.decodeTargetPage(safeEnvelope) }.getOrNull()
            ?: return reject(AiProviderTargetListingFailure.PAGE_INVALID)
        if (page.targets.any { target ->
                runCatching {
                    AiTargetCatalogPolicy.requireCompatible(
                        pinnedProvider.providerInfo,
                        pinnedProvider.negotiatedCapabilities,
                        target,
                    )
                }.isFailure
            }
        ) {
            return reject(AiProviderTargetListingFailure.TARGET_DESCRIPTOR_MISMATCH)
        }
        val pagination = paginationPolicy ?: runCatching {
            val scope = AiTargetTokenScope(
                packageName = pinnedProvider.component.packageName,
                // The token scope keys one verified signer digest. The transport separately
                // reverifies the complete signer set in [pinnedProvider] before every dispatch.
                signerSha256 = pinnedProvider.signerSha256.first(),
                providerId = pinnedProvider.providerId,
                protocolVersion = pinnedProvider.protocolVersion,
                catalogGeneration = page.catalogGeneration,
                pageSize = options.pageSize,
            )
            tokenScope = scope
            AiTargetPaginationPolicy(
                scope = scope,
                // The protocol policy consumes request tokens when their response arrives. Keep
                // that transcript-local accounting separate from the caller-supplied dispatch
                // ledger (shared across transcripts when injected), which claims a continuation
                // before it is exposed to the transport.
                ledger = AiTargetPageTokenLedger(),
            )
        }.getOrNull() ?: return reject(AiProviderTargetListingFailure.PAGINATION_INVALID)
        if (paginationPolicy == null) {
            paginationPolicy = pagination
            catalogGeneration = page.catalogGeneration
        }
        if (runCatching { pagination.acceptPage(pending.pageToken, page) }.isFailure) {
            return reject(AiProviderTargetListingFailure.PAGINATION_INVALID)
        }
        acceptedPages += 1
        val nextPageToken = page.nextPageToken
        if (nextPageToken != null) {
            if (acceptedPages >= AiProviderLimits.MAX_TARGET_PAGES) {
                return reject(AiProviderTargetListingFailure.PAGINATION_INVALID)
            }
            val scope = requireNotNull(tokenScope)
            if (runCatching { tokenLedger.consume(nextPageToken, scope) }.isFailure) {
                return reject(AiProviderTargetListingFailure.PAGINATION_INVALID)
            }
        }
        targets += page.targets
        return nextPageToken?.let(::dispatch) ?: terminate(
            AiProviderTargetListingOutcome.Completed(
                AiProviderTargetCatalog(
                    pinnedProvider = pinnedProvider,
                    catalogGeneration = requireNotNull(catalogGeneration),
                    targets = targets,
                ),
            ),
        )
    }

    @Synchronized
    fun acceptFailed(
        sequence: Long,
        callingUid: Int,
        envelope: ByteArray?,
    ): AiProviderTargetListingDecision {
        if (terminal) return AiProviderTargetListingDecision.Ignored
        if (!AiProviderCallbackCallerPolicy.isExpectedUid(pinnedProvider, callingUid)) {
            return reject(AiProviderTargetListingFailure.CALLBACK_UID_MISMATCH)
        }
        if (claimPending(sequence) == null) {
            return reject(AiProviderTargetListingFailure.STALE_CALLBACK)
        }
        val safeEnvelope = when {
            envelope == null -> return reject(AiProviderTargetListingFailure.ERROR_MISSING)
            envelope.isEmpty() -> return reject(AiProviderTargetListingFailure.ERROR_EMPTY)
            envelope.size > AiCommonLimits.MAX_BINDER_ENVELOPE_BYTES ->
                return reject(AiProviderTargetListingFailure.ERROR_TOO_LARGE)
            else -> envelope.copyOf()
        }
        val error = runCatching { AiCommonCodec.decodeError(safeEnvelope) }.getOrNull()
            ?: return reject(AiProviderTargetListingFailure.ERROR_INVALID)
        return terminate(
            AiProviderTargetListingOutcome.ProviderFailed(
                AiProviderTargetListingErrorSummary.from(error),
            ),
        )
    }

    @Synchronized
    fun cancel(): AiProviderTargetListingDecision =
        if (terminal) AiProviderTargetListingDecision.Ignored
        else terminate(AiProviderTargetListingOutcome.Cancelled)

    @Synchronized
    fun timeout(): AiProviderTargetListingDecision =
        if (terminal) AiProviderTargetListingDecision.Ignored
        else terminate(AiProviderTargetListingOutcome.TimedOut)

    @Synchronized
    fun binderDied(): AiProviderTargetListingDecision =
        if (terminal) AiProviderTargetListingDecision.Ignored
        else terminate(AiProviderTargetListingOutcome.BinderDied)

    private fun claimPending(sequence: Long): PendingDispatch? {
        val pending = pendingDispatch ?: return null
        if (pending.sequence != sequence) return null
        pendingDispatch = null
        return pending
    }

    private fun dispatch(pageToken: String?): AiProviderTargetListingDecision.Dispatch {
        check(!terminal)
        val sequence = nextSequence++
        val request = AiTargetListRequest(
            protocolVersion = pinnedProvider.protocolVersion,
            pageSize = options.pageSize,
            pageToken = pageToken,
        )
        pendingDispatch = PendingDispatch(sequence, pageToken)
        return AiProviderTargetListingDecision.Dispatch(
            AiProviderTargetListingDispatch(
                sequence = sequence,
                isContinuation = pageToken != null,
                requestEnvelope = AiProviderCodec.encodeTargetListRequest(request),
            ),
        )
    }

    private fun reject(
        failure: AiProviderTargetListingFailure,
    ): AiProviderTargetListingDecision.Terminal =
        terminate(AiProviderTargetListingOutcome.Rejected(failure))

    private fun terminate(
        outcome: AiProviderTargetListingOutcome,
    ): AiProviderTargetListingDecision.Terminal {
        check(!terminal) { "local AI target catalog already terminated" }
        terminal = true
        pendingDispatch = null
        return AiProviderTargetListingDecision.Terminal(outcome)
    }
}
