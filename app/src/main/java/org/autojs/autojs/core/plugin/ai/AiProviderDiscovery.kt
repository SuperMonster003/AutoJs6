package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import java.util.Collections

/**
 * Transport-independent package facts supplied by an Android discovery adapter.
 *
 * The complete signer collection is copied at this boundary. Official-signer, same-signer, and
 * debuggable values remain evidence rather than a final trust decision. This type contains no
 * remote provider metadata and cannot by itself become an eligible provider or dispatch a request.
 */
internal class AiProviderDiscoveryInput(
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
internal data class AiProviderDiscoveryReference(
    val packageName: String?,
    val className: String?,
)

internal enum class AiProviderDiscoverySourceFailure {
    QUERY_FAILED,
    SERVICE_READ_FAILED,
}

internal sealed class AiProviderDiscoverySourceEntry {
    class Read(
        val input: AiProviderDiscoveryInput,
    ) : AiProviderDiscoverySourceEntry()

    data class ReadFailed(
        val reference: AiProviderDiscoveryReference?,
        val failure: AiProviderDiscoverySourceFailure =
            AiProviderDiscoverySourceFailure.SERVICE_READ_FAILED,
    ) : AiProviderDiscoverySourceEntry() {
        init {
            require(failure == AiProviderDiscoverySourceFailure.SERVICE_READ_FAILED) {
                "A discovery row failure must use SERVICE_READ_FAILED"
            }
        }
    }
}

internal sealed class AiProviderDiscoverySourceResult {
    object WrongAction : AiProviderDiscoverySourceResult()

    class Completed(entries: Collection<AiProviderDiscoverySourceEntry>) :
        AiProviderDiscoverySourceResult() {
        val entries: List<AiProviderDiscoverySourceEntry> =
            immutableDiscoverySnapshot(entries)
    }

    data class QueryFailed(
        val failure: AiProviderDiscoverySourceFailure =
            AiProviderDiscoverySourceFailure.QUERY_FAILED,
    ) : AiProviderDiscoverySourceResult() {
        init {
            require(failure == AiProviderDiscoverySourceFailure.QUERY_FAILED) {
                "A discovery query failure must use QUERY_FAILED"
            }
        }
    }
}

/**
 * Read-only source contract for an Android PackageManager adapter.
 *
 * Each resolved service must produce exactly one [AiProviderDiscoverySourceEntry]. A whole
 * query failure is [AiProviderDiscoverySourceResult.QueryFailed], while a failure reading one
 * resolved service is [AiProviderDiscoverySourceEntry.ReadFailed]. Implementations must not
 * use `mapNotNull` to drop failed rows. [inspectExact] represents absence with an empty completed
 * list and preserves every duplicate match instead of using `firstOrNull`.
 */
internal interface AiProviderDiscoverySource {
    fun discoverExactAction(action: String): AiProviderDiscoverySourceResult

    fun inspectExact(component: AiProviderComponent): AiProviderDiscoverySourceResult
}

/**
 * Validated local identity snapshot. Trust evidence is retained without granting authorization;
 * remote provider info and capabilities are intentionally absent.
 */
internal class AiProviderPackageSnapshot private constructor(
    val component: AiProviderComponent,
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

    /** Exact local identity comparison used before and after a metadata-only Binder handshake. */
    fun samePackageIdentityAs(other: AiProviderPackageSnapshot): Boolean =
        component == other.component &&
            declaredAction == other.declaredAction &&
            exported == other.exported &&
            applicationEnabled == other.applicationEnabled &&
            serviceEnabled == other.serviceEnabled &&
            requiredPermission == other.requiredPermission &&
            packageUid == other.packageUid &&
            versionCode == other.versionCode &&
            lastUpdateTime == other.lastUpdateTime &&
            signerSha256 == other.signerSha256 &&
            officialSignerMatch == other.officialSignerMatch &&
            sameSignerAsHost == other.sameSignerAsHost &&
            debuggable == other.debuggable

    companion object {
        fun from(input: AiProviderDiscoveryInput): AiProviderPackageSnapshot? {
            val component = runCatching {
                AiProviderComponent(input.packageName, input.className)
            }.getOrNull() ?: return null
            if (!hasValidDiscoveryPackageIdentity(input)) return null
            return AiProviderPackageSnapshot(
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

internal enum class AiProviderDiscoveryRejection {
    WRONG_ACTION,
    INVALID_PACKAGE_IDENTITY,
}

internal sealed class AiProviderDiscoveryEvaluation {
    class Discovered(
        val input: AiProviderDiscoveryInput,
        val snapshot: AiProviderPackageSnapshot,
    ) : AiProviderDiscoveryEvaluation()

    data class Rejected(
        val input: AiProviderDiscoveryInput,
        val rejection: AiProviderDiscoveryRejection,
    ) : AiProviderDiscoveryEvaluation()

    data class SourceFailure(
        val reference: AiProviderDiscoveryReference?,
        val failure: AiProviderDiscoverySourceFailure,
    ) : AiProviderDiscoveryEvaluation()
}

/** The default constructor keeps package discovery inert until an explicit host integration exists. */
internal data class AiProviderDiscoveryOptions(
    val enabled: Boolean = false,
)

internal sealed class AiProviderDiscoveryResult {
    object Disabled : AiProviderDiscoveryResult()
    object WrongAction : AiProviderDiscoveryResult()

    data class QueryFailed(
        val failure: AiProviderDiscoverySourceFailure,
    ) : AiProviderDiscoveryResult()

    class Completed(evaluations: Collection<AiProviderDiscoveryEvaluation>) :
        AiProviderDiscoveryResult() {
        val evaluations: List<AiProviderDiscoveryEvaluation> =
            immutableDiscoverySnapshot(evaluations)
    }
}

/**
 * Pure discovery admission policy.
 *
 * It validates only the exact discovery action and local package identity. Export, enable,
 * permission, trust, protocol, and capability decisions remain in [AiProviderSelectionPolicy].
 */
internal object AiProviderDiscoveryPolicy {

    fun discoverExactAction(
        source: AiProviderDiscoverySource,
        action: String,
        options: AiProviderDiscoveryOptions = AiProviderDiscoveryOptions(),
    ): AiProviderDiscoveryResult {
        if (!options.enabled) return AiProviderDiscoveryResult.Disabled
        if (action != AiProviderProtocol.ACTION_PROVIDER) return AiProviderDiscoveryResult.WrongAction
        return evaluateEnabled(source.discoverExactAction(action))
    }

    fun inspectExact(
        source: AiProviderDiscoverySource,
        component: AiProviderComponent,
        options: AiProviderDiscoveryOptions = AiProviderDiscoveryOptions(),
    ): AiProviderDiscoveryResult {
        if (!options.enabled) return AiProviderDiscoveryResult.Disabled
        return evaluateEnabled(source.inspectExact(component))
    }

    fun evaluate(
        inputs: Collection<AiProviderDiscoveryInput>,
        options: AiProviderDiscoveryOptions = AiProviderDiscoveryOptions(),
    ): AiProviderDiscoveryResult = evaluate(
        source = AiProviderDiscoverySourceResult.Completed(
            inputs.map { input -> AiProviderDiscoverySourceEntry.Read(input) },
        ),
        options = options,
    )

    fun evaluate(
        source: AiProviderDiscoverySourceResult,
        options: AiProviderDiscoveryOptions = AiProviderDiscoveryOptions(),
    ): AiProviderDiscoveryResult {
        if (!options.enabled) return AiProviderDiscoveryResult.Disabled

        return evaluateEnabled(source)
    }

    private fun evaluateEnabled(
        source: AiProviderDiscoverySourceResult,
    ): AiProviderDiscoveryResult = when (source) {
        AiProviderDiscoverySourceResult.WrongAction -> AiProviderDiscoveryResult.WrongAction
        is AiProviderDiscoverySourceResult.QueryFailed ->
            AiProviderDiscoveryResult.QueryFailed(source.failure)
        is AiProviderDiscoverySourceResult.Completed -> {
            val evaluations = source.entries.sortedWith(SOURCE_ENTRY_ORDER).map { entry ->
                when (entry) {
                    is AiProviderDiscoverySourceEntry.Read -> evaluate(entry.input)
                    is AiProviderDiscoverySourceEntry.ReadFailed ->
                        AiProviderDiscoveryEvaluation.SourceFailure(
                            reference = entry.reference,
                            failure = entry.failure,
                        )
                }
            }
            AiProviderDiscoveryResult.Completed(evaluations)
        }
    }

    private fun evaluate(input: AiProviderDiscoveryInput): AiProviderDiscoveryEvaluation {
        if (input.declaredAction != AiProviderProtocol.ACTION_PROVIDER) {
            return AiProviderDiscoveryEvaluation.Rejected(
                input,
                AiProviderDiscoveryRejection.WRONG_ACTION,
            )
        }
        val snapshot = AiProviderPackageSnapshot.from(input)
        if (snapshot == null) {
            return AiProviderDiscoveryEvaluation.Rejected(
                input,
                AiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            )
        }
        return AiProviderDiscoveryEvaluation.Discovered(
            input = input,
            snapshot = snapshot,
        )
    }

    private val INPUT_ORDER = compareBy<AiProviderDiscoveryInput>(
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

    private val SOURCE_ENTRY_ORDER = Comparator<AiProviderDiscoverySourceEntry> { left, right ->
        val packageOrder = compareValues(sourcePackageName(left), sourcePackageName(right))
        if (packageOrder != 0) return@Comparator packageOrder
        val classOrder = compareValues(sourceClassName(left), sourceClassName(right))
        if (classOrder != 0) return@Comparator classOrder
        when {
            left is AiProviderDiscoverySourceEntry.Read &&
                right is AiProviderDiscoverySourceEntry.Read -> INPUT_ORDER.compare(left.input, right.input)
            left is AiProviderDiscoverySourceEntry.ReadFailed &&
                right is AiProviderDiscoverySourceEntry.ReadFailed -> {
                left.failure.name.compareTo(right.failure.name)
            }
            left is AiProviderDiscoverySourceEntry.Read -> -1
            else -> 1
        }
    }

    private fun sourcePackageName(entry: AiProviderDiscoverySourceEntry): String? = when (entry) {
        is AiProviderDiscoverySourceEntry.Read -> entry.input.packageName
        is AiProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.packageName
    }

    private fun sourceClassName(entry: AiProviderDiscoverySourceEntry): String? = when (entry) {
        is AiProviderDiscoverySourceEntry.Read -> entry.input.className
        is AiProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.className
    }
}

private fun hasValidDiscoveryPackageIdentity(input: AiProviderDiscoveryInput): Boolean {
    if (input.packageUid < 0 || input.versionCode <= 0L || input.lastUpdateTime < 0L) return false
    if (input.signerSha256.isEmpty()) return false
    val normalized = input.signerSha256.map { value -> value.lowercase() }
    return normalized.distinct().size == normalized.size && normalized.all(::isDiscoverySha256)
}

private fun isDiscoverySha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun <T> immutableDiscoverySnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
