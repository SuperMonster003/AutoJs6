package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.text.api.AiTextProtocol
import java.util.Collections

/**
 * Transport-independent package facts supplied by an Android discovery adapter.
 *
 * The complete signer collection is copied at this boundary. Official-signer, same-signer, and
 * debuggable values remain evidence rather than a final trust decision. This type contains no
 * remote provider metadata and cannot by itself become an eligible provider or dispatch a request.
 */
internal class AiTextProviderDiscoveryInput(
    val packageName: String,
    val className: String,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val officialSignerMatch: Boolean,
    val sameSignerAsHost: Boolean,
    val debuggable: Boolean,
) {
    val signerSha256: List<String> = immutableDiscoverySnapshot(signerSha256)
}

/** Best-effort identity for a row whose complete package facts could not be read. */
internal data class AiTextProviderDiscoveryReference(
    val packageName: String?,
    val className: String?,
)

internal enum class AiTextProviderDiscoverySourceFailure {
    QUERY_FAILED,
    SERVICE_READ_FAILED,
}

internal sealed class AiTextProviderDiscoverySourceEntry {
    class Read(
        val input: AiTextProviderDiscoveryInput,
    ) : AiTextProviderDiscoverySourceEntry()

    data class ReadFailed(
        val reference: AiTextProviderDiscoveryReference?,
        val failure: AiTextProviderDiscoverySourceFailure =
            AiTextProviderDiscoverySourceFailure.SERVICE_READ_FAILED,
    ) : AiTextProviderDiscoverySourceEntry() {
        init {
            require(failure == AiTextProviderDiscoverySourceFailure.SERVICE_READ_FAILED) {
                "A discovery row failure must use SERVICE_READ_FAILED"
            }
        }
    }
}

internal sealed class AiTextProviderDiscoverySourceResult {
    object WrongAction : AiTextProviderDiscoverySourceResult()

    class Completed(entries: Collection<AiTextProviderDiscoverySourceEntry>) :
        AiTextProviderDiscoverySourceResult() {
        val entries: List<AiTextProviderDiscoverySourceEntry> =
            immutableDiscoverySnapshot(entries)
    }

    data class QueryFailed(
        val failure: AiTextProviderDiscoverySourceFailure =
            AiTextProviderDiscoverySourceFailure.QUERY_FAILED,
    ) : AiTextProviderDiscoverySourceResult() {
        init {
            require(failure == AiTextProviderDiscoverySourceFailure.QUERY_FAILED) {
                "A discovery query failure must use QUERY_FAILED"
            }
        }
    }
}

/**
 * Read-only source contract for an Android PackageManager adapter.
 *
 * Each resolved service must produce exactly one [AiTextProviderDiscoverySourceEntry]. A whole
 * query failure is [AiTextProviderDiscoverySourceResult.QueryFailed], while a failure reading one
 * resolved service is [AiTextProviderDiscoverySourceEntry.ReadFailed]. Implementations must not
 * use `mapNotNull` to drop failed rows. [inspectExact] represents absence with an empty completed
 * list and preserves every duplicate match instead of using `firstOrNull`.
 */
internal interface AiTextProviderDiscoverySource {
    fun discoverExactAction(action: String): AiTextProviderDiscoverySourceResult

    fun inspectExact(component: AiTextProviderComponent): AiTextProviderDiscoverySourceResult
}

/**
 * Validated local identity snapshot. Trust evidence is retained without granting authorization;
 * remote provider info and capabilities are intentionally absent.
 */
internal class AiTextProviderPackageSnapshot private constructor(
    val component: AiTextProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val officialSignerMatch: Boolean,
    val sameSignerAsHost: Boolean,
    val debuggable: Boolean,
) {
    val signerSha256: List<String> = immutableDiscoverySnapshot(
        signerSha256.map { value -> value.lowercase() }.sorted(),
    )

    companion object {
        fun from(input: AiTextProviderDiscoveryInput): AiTextProviderPackageSnapshot? {
            val component = runCatching {
                AiTextProviderComponent(input.packageName, input.className)
            }.getOrNull() ?: return null
            if (!hasValidDiscoveryPackageIdentity(input)) return null
            return AiTextProviderPackageSnapshot(
                component = component,
                declaredAction = input.declaredAction,
                exported = input.exported,
                applicationEnabled = input.applicationEnabled,
                serviceEnabled = input.serviceEnabled,
                requiredPermission = input.requiredPermission,
                packageUid = input.packageUid,
                versionCode = input.versionCode,
                lastUpdateTime = input.lastUpdateTime,
                signerSha256 = input.signerSha256,
                officialSignerMatch = input.officialSignerMatch,
                sameSignerAsHost = input.sameSignerAsHost,
                debuggable = input.debuggable,
            )
        }
    }
}

internal enum class AiTextProviderDiscoveryRejection {
    WRONG_ACTION,
    INVALID_PACKAGE_IDENTITY,
}

internal sealed class AiTextProviderDiscoveryEvaluation {
    class Discovered(
        val input: AiTextProviderDiscoveryInput,
        val snapshot: AiTextProviderPackageSnapshot,
    ) : AiTextProviderDiscoveryEvaluation()

    data class Rejected(
        val input: AiTextProviderDiscoveryInput,
        val rejection: AiTextProviderDiscoveryRejection,
    ) : AiTextProviderDiscoveryEvaluation()

    data class SourceFailure(
        val reference: AiTextProviderDiscoveryReference?,
        val failure: AiTextProviderDiscoverySourceFailure,
    ) : AiTextProviderDiscoveryEvaluation()
}

/** The default constructor keeps package discovery inert until an explicit host integration exists. */
internal data class AiTextProviderDiscoveryOptions(
    val enabled: Boolean = false,
)

internal sealed class AiTextProviderDiscoveryResult {
    object Disabled : AiTextProviderDiscoveryResult()
    object WrongAction : AiTextProviderDiscoveryResult()

    data class QueryFailed(
        val failure: AiTextProviderDiscoverySourceFailure,
    ) : AiTextProviderDiscoveryResult()

    class Completed(evaluations: Collection<AiTextProviderDiscoveryEvaluation>) :
        AiTextProviderDiscoveryResult() {
        val evaluations: List<AiTextProviderDiscoveryEvaluation> =
            immutableDiscoverySnapshot(evaluations)
    }
}

/**
 * Pure discovery admission policy.
 *
 * It validates only the exact discovery action and local package identity. Export, enable,
 * permission, trust, protocol, and capability decisions remain in [AiTextProviderSelectionPolicy].
 */
internal object AiTextProviderDiscoveryPolicy {

    fun discoverExactAction(
        source: AiTextProviderDiscoverySource,
        action: String,
        options: AiTextProviderDiscoveryOptions = AiTextProviderDiscoveryOptions(),
    ): AiTextProviderDiscoveryResult {
        if (!options.enabled) return AiTextProviderDiscoveryResult.Disabled
        if (action != AiTextProtocol.ACTION_TEXT_PROVIDER) return AiTextProviderDiscoveryResult.WrongAction
        return evaluateEnabled(source.discoverExactAction(action))
    }

    fun inspectExact(
        source: AiTextProviderDiscoverySource,
        component: AiTextProviderComponent,
        options: AiTextProviderDiscoveryOptions = AiTextProviderDiscoveryOptions(),
    ): AiTextProviderDiscoveryResult {
        if (!options.enabled) return AiTextProviderDiscoveryResult.Disabled
        return evaluateEnabled(source.inspectExact(component))
    }

    fun evaluate(
        inputs: Collection<AiTextProviderDiscoveryInput>,
        options: AiTextProviderDiscoveryOptions = AiTextProviderDiscoveryOptions(),
    ): AiTextProviderDiscoveryResult = evaluate(
        source = AiTextProviderDiscoverySourceResult.Completed(
            inputs.map { input -> AiTextProviderDiscoverySourceEntry.Read(input) },
        ),
        options = options,
    )

    fun evaluate(
        source: AiTextProviderDiscoverySourceResult,
        options: AiTextProviderDiscoveryOptions = AiTextProviderDiscoveryOptions(),
    ): AiTextProviderDiscoveryResult {
        if (!options.enabled) return AiTextProviderDiscoveryResult.Disabled

        return evaluateEnabled(source)
    }

    private fun evaluateEnabled(
        source: AiTextProviderDiscoverySourceResult,
    ): AiTextProviderDiscoveryResult = when (source) {
        AiTextProviderDiscoverySourceResult.WrongAction -> AiTextProviderDiscoveryResult.WrongAction
        is AiTextProviderDiscoverySourceResult.QueryFailed ->
            AiTextProviderDiscoveryResult.QueryFailed(source.failure)
        is AiTextProviderDiscoverySourceResult.Completed -> {
            val evaluations = source.entries.sortedWith(SOURCE_ENTRY_ORDER).map { entry ->
                when (entry) {
                    is AiTextProviderDiscoverySourceEntry.Read -> evaluate(entry.input)
                    is AiTextProviderDiscoverySourceEntry.ReadFailed ->
                        AiTextProviderDiscoveryEvaluation.SourceFailure(
                            reference = entry.reference,
                            failure = entry.failure,
                        )
                }
            }
            AiTextProviderDiscoveryResult.Completed(evaluations)
        }
    }

    private fun evaluate(input: AiTextProviderDiscoveryInput): AiTextProviderDiscoveryEvaluation {
        if (input.declaredAction != AiTextProtocol.ACTION_TEXT_PROVIDER) {
            return AiTextProviderDiscoveryEvaluation.Rejected(
                input,
                AiTextProviderDiscoveryRejection.WRONG_ACTION,
            )
        }
        val snapshot = AiTextProviderPackageSnapshot.from(input)
        if (snapshot == null) {
            return AiTextProviderDiscoveryEvaluation.Rejected(
                input,
                AiTextProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            )
        }
        return AiTextProviderDiscoveryEvaluation.Discovered(
            input = input,
            snapshot = snapshot,
        )
    }

    private val INPUT_ORDER = compareBy<AiTextProviderDiscoveryInput>(
        { it.packageName },
        { it.className },
        { it.declaredAction },
    ).thenByDescending { it.versionCode }
        .thenByDescending { it.lastUpdateTime }
        .thenBy { it.packageUid }
        .thenBy { it.signerSha256.map { value -> value.lowercase() }.sorted().joinToString(separator = ",") }
        .thenBy { it.requiredPermission.orEmpty() }
        .thenBy { it.exported }
        .thenBy { it.applicationEnabled }
        .thenBy { it.serviceEnabled }
        .thenBy { it.officialSignerMatch }
        .thenBy { it.sameSignerAsHost }
        .thenBy { it.debuggable }

    private val SOURCE_ENTRY_ORDER = Comparator<AiTextProviderDiscoverySourceEntry> { left, right ->
        val packageOrder = compareValues(sourcePackageName(left), sourcePackageName(right))
        if (packageOrder != 0) return@Comparator packageOrder
        val classOrder = compareValues(sourceClassName(left), sourceClassName(right))
        if (classOrder != 0) return@Comparator classOrder
        when {
            left is AiTextProviderDiscoverySourceEntry.Read &&
                right is AiTextProviderDiscoverySourceEntry.Read -> INPUT_ORDER.compare(left.input, right.input)
            left is AiTextProviderDiscoverySourceEntry.ReadFailed &&
                right is AiTextProviderDiscoverySourceEntry.ReadFailed -> {
                left.failure.name.compareTo(right.failure.name)
            }
            left is AiTextProviderDiscoverySourceEntry.Read -> -1
            else -> 1
        }
    }

    private fun sourcePackageName(entry: AiTextProviderDiscoverySourceEntry): String? = when (entry) {
        is AiTextProviderDiscoverySourceEntry.Read -> entry.input.packageName
        is AiTextProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.packageName
    }

    private fun sourceClassName(entry: AiTextProviderDiscoverySourceEntry): String? = when (entry) {
        is AiTextProviderDiscoverySourceEntry.Read -> entry.input.className
        is AiTextProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.className
    }
}

private fun hasValidDiscoveryPackageIdentity(input: AiTextProviderDiscoveryInput): Boolean {
    if (input.packageUid < 0 || input.versionCode <= 0L || input.lastUpdateTime < 0L) return false
    if (input.signerSha256.isEmpty()) return false
    val normalized = input.signerSha256.map { value -> value.lowercase() }
    return normalized.distinct().size == normalized.size && normalized.all(::isDiscoverySha256)
}

private fun isDiscoverySha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun <T> immutableDiscoverySnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
