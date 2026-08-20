package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import java.util.Collections

/**
 * Transport-independent package facts supplied by an Android discovery adapter.
 *
 * The complete signer collection is copied at this boundary. Official-signer, same-signer, and
 * debuggable values remain evidence rather than a final trust decision. This type contains no
 * remote provider metadata and cannot by itself become an eligible provider or dispatch a request.
 */
internal class OnDeviceAiProviderDiscoveryInput(
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
internal data class OnDeviceAiProviderDiscoveryReference(
    val packageName: String?,
    val className: String?,
)

internal enum class OnDeviceAiProviderDiscoverySourceFailure {
    QUERY_FAILED,
    SERVICE_READ_FAILED,
}

internal sealed class OnDeviceAiProviderDiscoverySourceEntry {
    class Read(
        val input: OnDeviceAiProviderDiscoveryInput,
    ) : OnDeviceAiProviderDiscoverySourceEntry()

    data class ReadFailed(
        val reference: OnDeviceAiProviderDiscoveryReference?,
        val failure: OnDeviceAiProviderDiscoverySourceFailure =
            OnDeviceAiProviderDiscoverySourceFailure.SERVICE_READ_FAILED,
    ) : OnDeviceAiProviderDiscoverySourceEntry() {
        init {
            require(failure == OnDeviceAiProviderDiscoverySourceFailure.SERVICE_READ_FAILED) {
                "A discovery row failure must use SERVICE_READ_FAILED"
            }
        }
    }
}

internal sealed class OnDeviceAiProviderDiscoverySourceResult {
    object WrongAction : OnDeviceAiProviderDiscoverySourceResult()

    class Completed(entries: Collection<OnDeviceAiProviderDiscoverySourceEntry>) :
        OnDeviceAiProviderDiscoverySourceResult() {
        val entries: List<OnDeviceAiProviderDiscoverySourceEntry> =
            immutableDiscoverySnapshot(entries)
    }

    data class QueryFailed(
        val failure: OnDeviceAiProviderDiscoverySourceFailure =
            OnDeviceAiProviderDiscoverySourceFailure.QUERY_FAILED,
    ) : OnDeviceAiProviderDiscoverySourceResult() {
        init {
            require(failure == OnDeviceAiProviderDiscoverySourceFailure.QUERY_FAILED) {
                "A discovery query failure must use QUERY_FAILED"
            }
        }
    }
}

/**
 * Read-only source contract for an Android PackageManager adapter.
 *
 * Each resolved service must produce exactly one [OnDeviceAiProviderDiscoverySourceEntry]. A whole
 * query failure is [OnDeviceAiProviderDiscoverySourceResult.QueryFailed], while a failure reading one
 * resolved service is [OnDeviceAiProviderDiscoverySourceEntry.ReadFailed]. Implementations must not
 * use `mapNotNull` to drop failed rows. [inspectExact] represents absence with an empty completed
 * list and preserves every duplicate match instead of using `firstOrNull`.
 */
internal interface OnDeviceAiProviderDiscoverySource {
    fun discoverExactAction(action: String): OnDeviceAiProviderDiscoverySourceResult

    fun inspectExact(component: OnDeviceAiProviderComponent): OnDeviceAiProviderDiscoverySourceResult
}

/**
 * Validated local identity snapshot. Trust evidence is retained without granting authorization;
 * remote provider info and capabilities are intentionally absent.
 */
internal class OnDeviceAiProviderPackageSnapshot private constructor(
    val component: OnDeviceAiProviderComponent,
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
    fun samePackageIdentityAs(other: OnDeviceAiProviderPackageSnapshot): Boolean =
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
        fun from(input: OnDeviceAiProviderDiscoveryInput): OnDeviceAiProviderPackageSnapshot? {
            val component = runCatching {
                OnDeviceAiProviderComponent(input.packageName, input.className)
            }.getOrNull() ?: return null
            if (!hasValidDiscoveryPackageIdentity(input)) return null
            return OnDeviceAiProviderPackageSnapshot(
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

internal enum class OnDeviceAiProviderDiscoveryRejection {
    WRONG_ACTION,
    INVALID_PACKAGE_IDENTITY,
}

internal sealed class OnDeviceAiProviderDiscoveryEvaluation {
    class Discovered(
        val input: OnDeviceAiProviderDiscoveryInput,
        val snapshot: OnDeviceAiProviderPackageSnapshot,
    ) : OnDeviceAiProviderDiscoveryEvaluation()

    data class Rejected(
        val input: OnDeviceAiProviderDiscoveryInput,
        val rejection: OnDeviceAiProviderDiscoveryRejection,
    ) : OnDeviceAiProviderDiscoveryEvaluation()

    data class SourceFailure(
        val reference: OnDeviceAiProviderDiscoveryReference?,
        val failure: OnDeviceAiProviderDiscoverySourceFailure,
    ) : OnDeviceAiProviderDiscoveryEvaluation()
}

/** The default constructor keeps package discovery inert until an explicit host integration exists. */
internal data class OnDeviceAiProviderDiscoveryOptions(
    val enabled: Boolean = false,
)

internal sealed class OnDeviceAiProviderDiscoveryResult {
    object Disabled : OnDeviceAiProviderDiscoveryResult()
    object WrongAction : OnDeviceAiProviderDiscoveryResult()

    data class QueryFailed(
        val failure: OnDeviceAiProviderDiscoverySourceFailure,
    ) : OnDeviceAiProviderDiscoveryResult()

    class Completed(evaluations: Collection<OnDeviceAiProviderDiscoveryEvaluation>) :
        OnDeviceAiProviderDiscoveryResult() {
        val evaluations: List<OnDeviceAiProviderDiscoveryEvaluation> =
            immutableDiscoverySnapshot(evaluations)
    }
}

/**
 * Pure discovery admission policy.
 *
 * It validates only the exact discovery action and local package identity. Export, enable,
 * permission, trust, protocol, and capability decisions remain in [OnDeviceAiProviderSelectionPolicy].
 */
internal object OnDeviceAiProviderDiscoveryPolicy {

    fun discoverExactAction(
        source: OnDeviceAiProviderDiscoverySource,
        action: String,
        options: OnDeviceAiProviderDiscoveryOptions = OnDeviceAiProviderDiscoveryOptions(),
    ): OnDeviceAiProviderDiscoveryResult {
        if (!options.enabled) return OnDeviceAiProviderDiscoveryResult.Disabled
        if (action != OnDeviceAiProtocol.ACTION_PROVIDER) return OnDeviceAiProviderDiscoveryResult.WrongAction
        return evaluateEnabled(source.discoverExactAction(action))
    }

    fun inspectExact(
        source: OnDeviceAiProviderDiscoverySource,
        component: OnDeviceAiProviderComponent,
        options: OnDeviceAiProviderDiscoveryOptions = OnDeviceAiProviderDiscoveryOptions(),
    ): OnDeviceAiProviderDiscoveryResult {
        if (!options.enabled) return OnDeviceAiProviderDiscoveryResult.Disabled
        return evaluateEnabled(source.inspectExact(component))
    }

    fun evaluate(
        inputs: Collection<OnDeviceAiProviderDiscoveryInput>,
        options: OnDeviceAiProviderDiscoveryOptions = OnDeviceAiProviderDiscoveryOptions(),
    ): OnDeviceAiProviderDiscoveryResult = evaluate(
        source = OnDeviceAiProviderDiscoverySourceResult.Completed(
            inputs.map { input -> OnDeviceAiProviderDiscoverySourceEntry.Read(input) },
        ),
        options = options,
    )

    fun evaluate(
        source: OnDeviceAiProviderDiscoverySourceResult,
        options: OnDeviceAiProviderDiscoveryOptions = OnDeviceAiProviderDiscoveryOptions(),
    ): OnDeviceAiProviderDiscoveryResult {
        if (!options.enabled) return OnDeviceAiProviderDiscoveryResult.Disabled

        return evaluateEnabled(source)
    }

    private fun evaluateEnabled(
        source: OnDeviceAiProviderDiscoverySourceResult,
    ): OnDeviceAiProviderDiscoveryResult = when (source) {
        OnDeviceAiProviderDiscoverySourceResult.WrongAction -> OnDeviceAiProviderDiscoveryResult.WrongAction
        is OnDeviceAiProviderDiscoverySourceResult.QueryFailed ->
            OnDeviceAiProviderDiscoveryResult.QueryFailed(source.failure)
        is OnDeviceAiProviderDiscoverySourceResult.Completed -> {
            val evaluations = source.entries.sortedWith(SOURCE_ENTRY_ORDER).map { entry ->
                when (entry) {
                    is OnDeviceAiProviderDiscoverySourceEntry.Read -> evaluate(entry.input)
                    is OnDeviceAiProviderDiscoverySourceEntry.ReadFailed ->
                        OnDeviceAiProviderDiscoveryEvaluation.SourceFailure(
                            reference = entry.reference,
                            failure = entry.failure,
                        )
                }
            }
            OnDeviceAiProviderDiscoveryResult.Completed(evaluations)
        }
    }

    private fun evaluate(input: OnDeviceAiProviderDiscoveryInput): OnDeviceAiProviderDiscoveryEvaluation {
        if (input.declaredAction != OnDeviceAiProtocol.ACTION_PROVIDER) {
            return OnDeviceAiProviderDiscoveryEvaluation.Rejected(
                input,
                OnDeviceAiProviderDiscoveryRejection.WRONG_ACTION,
            )
        }
        val snapshot = OnDeviceAiProviderPackageSnapshot.from(input)
        if (snapshot == null) {
            return OnDeviceAiProviderDiscoveryEvaluation.Rejected(
                input,
                OnDeviceAiProviderDiscoveryRejection.INVALID_PACKAGE_IDENTITY,
            )
        }
        return OnDeviceAiProviderDiscoveryEvaluation.Discovered(
            input = input,
            snapshot = snapshot,
        )
    }

    private val INPUT_ORDER = compareBy<OnDeviceAiProviderDiscoveryInput>(
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

    private val SOURCE_ENTRY_ORDER = Comparator<OnDeviceAiProviderDiscoverySourceEntry> { left, right ->
        val packageOrder = compareValues(sourcePackageName(left), sourcePackageName(right))
        if (packageOrder != 0) return@Comparator packageOrder
        val classOrder = compareValues(sourceClassName(left), sourceClassName(right))
        if (classOrder != 0) return@Comparator classOrder
        when {
            left is OnDeviceAiProviderDiscoverySourceEntry.Read &&
                right is OnDeviceAiProviderDiscoverySourceEntry.Read -> INPUT_ORDER.compare(left.input, right.input)
            left is OnDeviceAiProviderDiscoverySourceEntry.ReadFailed &&
                right is OnDeviceAiProviderDiscoverySourceEntry.ReadFailed -> {
                left.failure.name.compareTo(right.failure.name)
            }
            left is OnDeviceAiProviderDiscoverySourceEntry.Read -> -1
            else -> 1
        }
    }

    private fun sourcePackageName(entry: OnDeviceAiProviderDiscoverySourceEntry): String? = when (entry) {
        is OnDeviceAiProviderDiscoverySourceEntry.Read -> entry.input.packageName
        is OnDeviceAiProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.packageName
    }

    private fun sourceClassName(entry: OnDeviceAiProviderDiscoverySourceEntry): String? = when (entry) {
        is OnDeviceAiProviderDiscoverySourceEntry.Read -> entry.input.className
        is OnDeviceAiProviderDiscoverySourceEntry.ReadFailed -> entry.reference?.className
    }
}

private fun hasValidDiscoveryPackageIdentity(input: OnDeviceAiProviderDiscoveryInput): Boolean {
    if (input.packageUid < 0 || input.versionCode <= 0L || input.lastUpdateTime < 0L) return false
    if (input.signerSha256.isEmpty()) return false
    val normalized = input.signerSha256.map { value -> value.lowercase() }
    return normalized.distinct().size == normalized.size && normalized.all(::isDiscoverySha256)
}

private fun isDiscoverySha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun <T> immutableDiscoverySnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
