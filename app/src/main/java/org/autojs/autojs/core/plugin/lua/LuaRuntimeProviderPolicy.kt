package org.autojs.autojs.core.plugin.lua

import org.autojs.plugin.lua.runtime.api.LuaPluginActions
import org.autojs.plugin.lua.runtime.api.LuaPluginIds
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeNegotiation
import org.autojs.plugin.lua.runtime.api.LuaRuntimeValidation
import java.util.Collections

/** Android-independent component identity used by discovery and explicit binding adapters. */
internal data class LuaRuntimeProviderComponent(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank()) { "Lua provider package name must not be blank" }
        require(className.isNotBlank()) { "Lua provider service class name must not be blank" }
        require(packageName.none(Character::isISOControl)) { "Lua provider package name contains control characters" }
        require(className.none(Character::isISOControl)) { "Lua provider service class name contains control characters" }
    }
}

/** Trust is decided by the Android package/signature adapter, never by provider-supplied metadata. */
internal enum class LuaRuntimeProviderTrustLevel {
    OFFICIAL,
    USER_APPROVED,
    DEBUG_ONLY,
    UNTRUSTED,
}

/**
 * Immutable discovery snapshot. The INFO and RUNTIME rows have already been joined by package.
 * Runtime information is deliberately absent until this descriptor passes structural trust checks.
 */
internal class LuaRuntimeProviderDescriptor(
    val component: LuaRuntimeProviderComponent,
    val infoAction: String,
    val runtimeAction: String,
    val pluginId: String,
    val engine: String,
    val variant: String,
    val infoVersionCode: Long,
    infoSupportedAbis: Collection<String>,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val trustLevel: LuaRuntimeProviderTrustLevel,
    val sameSignerAsHost: Boolean,
) {
    val infoSupportedAbis: List<String> = immutableSnapshot(infoSupportedAbis)
    val signerSha256: List<String> = immutableSnapshot(signerSha256)

    fun copy(
        component: LuaRuntimeProviderComponent = this.component,
        infoAction: String = this.infoAction,
        runtimeAction: String = this.runtimeAction,
        pluginId: String = this.pluginId,
        engine: String = this.engine,
        variant: String = this.variant,
        infoVersionCode: Long = this.infoVersionCode,
        infoSupportedAbis: Collection<String> = this.infoSupportedAbis,
        exported: Boolean = this.exported,
        applicationEnabled: Boolean = this.applicationEnabled,
        serviceEnabled: Boolean = this.serviceEnabled,
        requiredPermission: String? = this.requiredPermission,
        packageUid: Int = this.packageUid,
        versionCode: Long = this.versionCode,
        lastUpdateTime: Long = this.lastUpdateTime,
        signerSha256: Collection<String> = this.signerSha256,
        trustLevel: LuaRuntimeProviderTrustLevel = this.trustLevel,
        sameSignerAsHost: Boolean = this.sameSignerAsHost,
    ) = LuaRuntimeProviderDescriptor(
        component = component,
        infoAction = infoAction,
        runtimeAction = runtimeAction,
        pluginId = pluginId,
        engine = engine,
        variant = variant,
        infoVersionCode = infoVersionCode,
        infoSupportedAbis = infoSupportedAbis,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = requiredPermission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
    )
}

internal data class LuaRuntimeProviderCandidate(
    val descriptor: LuaRuntimeProviderDescriptor,
    val runtimeInfo: LuaRuntimeInfo,
)

internal class LuaRuntimeProviderEnvironment(
    val hostVersionCode: Long,
    supportedProcessAbis: Collection<String>,
    val releaseBuild: Boolean,
    val allowUserApprovedProviders: Boolean = false,
) {
    val supportedProcessAbis: List<String> = immutableSortedSnapshot(supportedProcessAbis)

    init {
        require(hostVersionCode >= 0L) { "Host version code must not be negative" }
        require(this.supportedProcessAbis.none(String::isBlank)) { "A supported process ABI is blank" }
        require(this.supportedProcessAbis.size == supportedProcessAbis.size) {
            "Supported process ABIs contain duplicates"
        }
    }
}

internal class LuaRuntimeProviderRequirement(
    hostCapabilities: Collection<String> = emptySet(),
    requiredCapabilities: Collection<String> = emptySet(),
) {
    val hostCapabilities: List<String> = immutableSortedSnapshot(hostCapabilities)
    val requiredCapabilities: List<String> = immutableSortedSnapshot(requiredCapabilities)

    init {
        require(this.hostCapabilities.size <= LuaRuntimeContract.MAX_CAPABILITIES) {
            "Host Lua capability count exceeds the protocol limit"
        }
        require(this.requiredCapabilities.size <= LuaRuntimeContract.MAX_CAPABILITIES) {
            "Required Lua capability count exceeds the protocol limit"
        }
        require(this.hostCapabilities.all(::isCapabilityName)) { "A host Lua capability name is invalid" }
        require(this.requiredCapabilities.all(::isCapabilityName)) { "A required Lua capability name is invalid" }
        require(this.hostCapabilities.containsAll(this.requiredCapabilities)) {
            "Required Lua capabilities must be enabled by the host"
        }
    }
}

internal enum class LuaRuntimeProviderRejection {
    WRONG_INFO_ACTION,
    WRONG_RUNTIME_ACTION,
    WRONG_PLUGIN_ID,
    WRONG_ENGINE,
    WRONG_VARIANT,
    INFO_PACKAGE_VERSION_MISMATCH,
    INFO_ABI_INVENTORY_INVALID,
    INFO_RUNTIME_VERSION_MISMATCH,
    INFO_RUNTIME_ABI_MISMATCH,
    INFO_ABI_UNSUPPORTED,
    NOT_EXPORTED,
    DISABLED,
    WRONG_PERMISSION,
    INVALID_PACKAGE_IDENTITY,
    TRUST_REQUIRED,
    USER_APPROVAL_REQUIRED,
    DEBUG_TRUST_FORBIDDEN,
    INVALID_RUNTIME_INFO,
    HOST_VERSION_UNSUPPORTED,
    PROTOCOL_UNSUPPORTED,
    RUNTIME_SLOT_UNSUPPORTED,
    ABI_UNSUPPORTED,
    CAPABILITY_MISMATCH,
    BIND_FAILED,
    BOUND_COMPONENT_MISMATCH,
}

internal data class LuaRuntimeProviderEvaluation(
    val component: LuaRuntimeProviderComponent,
    val providerId: String?,
    val rejection: LuaRuntimeProviderRejection,
)

/** Exact package, signer, runtime, ABI, protocol, and capability snapshot used for dispatch. */
internal class LuaRuntimeProviderPinnedIdentity(
    val component: LuaRuntimeProviderComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    runtimeInfo: LuaRuntimeInfo,
    val protocolVersion: LuaProtocolVersion,
    grantedCapabilities: Collection<String>,
) {
    val signerSha256: List<String> = immutableSortedSnapshot(signerSha256.map(::normalizeSha256))
    private val runtimeInfoWire: ByteArray = LuaRuntimeCodec.encodeRuntimeInfo(runtimeInfo)
    val runtimeInfo: LuaRuntimeInfo = LuaRuntimeCodec.decodeRuntimeInfo(runtimeInfoWire)
    val grantedCapabilities: List<String> = immutableSortedSnapshot(grantedCapabilities)

    val packageName: String
        get() = component.packageName

    val expectedCallbackUid: Int
        get() = packageUid

    val processAbi: String
        get() = runtimeInfo.processAbi

    val runtimeSlot: String
        get() = runtimeInfo.runtimeSlot

    val languageVersion: String
        get() = runtimeInfo.languageVersion

    init {
        require(packageUid >= 0) { "Pinned Lua provider UID must not be negative" }
        require(versionCode > 0L) { "Pinned Lua provider version must be positive" }
        require(lastUpdateTime >= 0L) { "Pinned Lua provider update time must not be negative" }
        require(this.signerSha256.isNotEmpty()) { "Pinned Lua provider signer set must not be empty" }
        require(protocolVersion in this.runtimeInfo.protocolMin..this.runtimeInfo.protocolMax) {
            "Pinned Lua protocol was not advertised by the provider"
        }
        require(this.runtimeInfo.runtimeSlot == LuaRuntimeContract.RUNTIME_SLOT_LUA54) {
            "Pinned Lua runtime slot is unsupported"
        }
        require(this.runtimeInfo.capabilities.containsAll(this.grantedCapabilities)) {
            "Pinned Lua grants exceed provider capabilities"
        }
    }

    fun matchesRuntimeInfo(value: LuaRuntimeInfo): Boolean = runCatching {
        runtimeInfoWire.contentEquals(LuaRuntimeCodec.encodeRuntimeInfo(value))
    }.getOrDefault(false)

    override fun equals(other: Any?): Boolean = this === other || other is LuaRuntimeProviderPinnedIdentity &&
        component == other.component &&
        packageUid == other.packageUid &&
        versionCode == other.versionCode &&
        lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256 &&
        runtimeInfoWire.contentEquals(other.runtimeInfoWire) &&
        protocolVersion == other.protocolVersion &&
        grantedCapabilities == other.grantedCapabilities

    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + packageUid
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + signerSha256.hashCode()
        result = 31 * result + runtimeInfoWire.contentHashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + grantedCapabilities.hashCode()
        return result
    }

    override fun toString(): String =
        "LuaRuntimeProviderPinnedIdentity(component=$component, packageUid=$packageUid, " +
            "versionCode=$versionCode, lastUpdateTime=$lastUpdateTime, signers=${signerSha256.size}, " +
            "protocolVersion=$protocolVersion, runtimeSlot=$runtimeSlot, processAbi=$processAbi, " +
            "capabilities=${grantedCapabilities.size})"
}

internal sealed class LuaRuntimeProviderSelectionResult {
    class NoMatch(evaluations: Collection<LuaRuntimeProviderEvaluation>) : LuaRuntimeProviderSelectionResult() {
        val evaluations: List<LuaRuntimeProviderEvaluation> = immutableSnapshot(evaluations)
    }

    class Ambiguous(candidates: Collection<LuaRuntimeProviderPinnedIdentity>) : LuaRuntimeProviderSelectionResult() {
        val candidates: List<LuaRuntimeProviderPinnedIdentity> = immutableSnapshot(candidates)
    }

    data class Selected(val pinned: LuaRuntimeProviderPinnedIdentity) : LuaRuntimeProviderSelectionResult()
}

internal object LuaRuntimeProviderSelectionPolicy {

    fun preflight(
        descriptor: LuaRuntimeProviderDescriptor,
        environment: LuaRuntimeProviderEnvironment,
    ): LuaRuntimeProviderRejection? = when {
        descriptor.infoAction != LuaPluginActions.INFO -> LuaRuntimeProviderRejection.WRONG_INFO_ACTION
        descriptor.runtimeAction != LuaRuntimeContract.SERVICE_ACTION -> LuaRuntimeProviderRejection.WRONG_RUNTIME_ACTION
        descriptor.pluginId != LuaPluginIds.ID -> LuaRuntimeProviderRejection.WRONG_PLUGIN_ID
        descriptor.engine != LuaPluginIds.ENGINE -> LuaRuntimeProviderRejection.WRONG_ENGINE
        descriptor.variant != LuaPluginIds.VARIANT_PUC_LUA_5_4 -> LuaRuntimeProviderRejection.WRONG_VARIANT
        !descriptor.exported -> LuaRuntimeProviderRejection.NOT_EXPORTED
        !descriptor.applicationEnabled || !descriptor.serviceEnabled -> LuaRuntimeProviderRejection.DISABLED
        descriptor.requiredPermission != LuaRuntimeContract.PLUGIN_PERMISSION -> LuaRuntimeProviderRejection.WRONG_PERMISSION
        !hasValidPackageIdentity(descriptor) -> LuaRuntimeProviderRejection.INVALID_PACKAGE_IDENTITY
        descriptor.infoVersionCode != descriptor.versionCode ->
            LuaRuntimeProviderRejection.INFO_PACKAGE_VERSION_MISMATCH
        !hasValidAbiInventory(descriptor.infoSupportedAbis) ->
            LuaRuntimeProviderRejection.INFO_ABI_INVENTORY_INVALID
        descriptor.sameSignerAsHost || descriptor.trustLevel == LuaRuntimeProviderTrustLevel.OFFICIAL -> null
        descriptor.trustLevel == LuaRuntimeProviderTrustLevel.USER_APPROVED &&
            !environment.allowUserApprovedProviders -> LuaRuntimeProviderRejection.USER_APPROVAL_REQUIRED
        descriptor.trustLevel == LuaRuntimeProviderTrustLevel.USER_APPROVED -> null
        descriptor.trustLevel == LuaRuntimeProviderTrustLevel.DEBUG_ONLY && environment.releaseBuild ->
            LuaRuntimeProviderRejection.DEBUG_TRUST_FORBIDDEN
        descriptor.trustLevel == LuaRuntimeProviderTrustLevel.DEBUG_ONLY -> null
        else -> LuaRuntimeProviderRejection.TRUST_REQUIRED
    }

    fun select(
        candidates: Collection<LuaRuntimeProviderCandidate>,
        environment: LuaRuntimeProviderEnvironment,
        requirement: LuaRuntimeProviderRequirement = LuaRuntimeProviderRequirement(),
        preferredComponent: LuaRuntimeProviderComponent? = null,
    ): LuaRuntimeProviderSelectionResult {
        val scoped = candidates.asSequence()
            .filter { preferredComponent == null || it.descriptor.component == preferredComponent }
            .sortedWith(CANDIDATE_ORDER)
            .toList()
        val rejected = ArrayList<LuaRuntimeProviderEvaluation>()
        val eligible = ArrayList<LuaRuntimeProviderPinnedIdentity>()

        scoped.forEach { candidate ->
            val result = evaluate(candidate, environment, requirement)
            if (result.rejection == null) {
                eligible += checkNotNull(result.pinned)
            } else {
                rejected += LuaRuntimeProviderEvaluation(
                    component = candidate.descriptor.component,
                    providerId = candidate.runtimeInfo.providerId,
                    rejection = result.rejection,
                )
            }
        }

        return when (eligible.size) {
            0 -> LuaRuntimeProviderSelectionResult.NoMatch(rejected)
            1 -> LuaRuntimeProviderSelectionResult.Selected(eligible.single())
            else -> LuaRuntimeProviderSelectionResult.Ambiguous(eligible)
        }
    }

    fun revalidate(
        pinned: LuaRuntimeProviderPinnedIdentity,
        current: LuaRuntimeProviderCandidate?,
        environment: LuaRuntimeProviderEnvironment,
        requirement: LuaRuntimeProviderRequirement = LuaRuntimeProviderRequirement(),
    ): Boolean {
        if (current == null || current.descriptor.component != pinned.component) return false
        val result = evaluate(current, environment, requirement)
        return result.rejection == null && result.pinned == pinned
    }

    private fun evaluate(
        candidate: LuaRuntimeProviderCandidate,
        environment: LuaRuntimeProviderEnvironment,
        requirement: LuaRuntimeProviderRequirement,
    ): EvaluationResult {
        preflight(candidate.descriptor, environment)?.let { return EvaluationResult(it, null) }
        val info = candidate.runtimeInfo
        runCatching { LuaRuntimeValidation.validateRuntimeInfo(info) }
            .onFailure { return EvaluationResult(LuaRuntimeProviderRejection.INVALID_RUNTIME_INFO, null) }

        if (info.providerVersionCode != candidate.descriptor.infoVersionCode) {
            return EvaluationResult(LuaRuntimeProviderRejection.INFO_RUNTIME_VERSION_MISMATCH, null)
        }
        // INFO reports the ABIs packed in the installed APK, runtime information every ABI of the build: a split APK, or
        // a universal APK whose foreign ABIs the installer stripped (ColorOS), packs only some (docs/memory-phase2/ROADMAP.md C40).
        // zh-CN: INFO 报告已安装 APK 中打包的 ABI, 运行时信息报告该构建的全部 ABI: 拆分包或被安装器剥离了其他 ABI 的通用包 (ColorOS)
        // 只含其中一部分 (docs/memory-phase2/ROADMAP.md C40).
        val infoAbis = candidate.descriptor.infoSupportedAbis
        if (!info.supportedAbis.containsAll(infoAbis)) {
            return EvaluationResult(LuaRuntimeProviderRejection.INFO_RUNTIME_ABI_MISMATCH, null)
        }
        if (infoAbis.none { it in environment.supportedProcessAbis }) {
            return EvaluationResult(LuaRuntimeProviderRejection.INFO_ABI_UNSUPPORTED, null)
        }
        if (info.minHostVersionCode?.let { environment.hostVersionCode < it } == true ||
            info.maxHostVersionCode?.let { environment.hostVersionCode > it } == true
        ) {
            return EvaluationResult(LuaRuntimeProviderRejection.HOST_VERSION_UNSUPPORTED, null)
        }
        if (info.runtimeSlot != LuaRuntimeContract.RUNTIME_SLOT_LUA54) {
            return EvaluationResult(LuaRuntimeProviderRejection.RUNTIME_SLOT_UNSUPPORTED, null)
        }
        if (info.processAbi !in environment.supportedProcessAbis) {
            return EvaluationResult(LuaRuntimeProviderRejection.ABI_UNSUPPORTED, null)
        }
        val protocol = LuaRuntimeNegotiation.negotiate(info)
            ?: return EvaluationResult(LuaRuntimeProviderRejection.PROTOCOL_UNSUPPORTED, null)
        val grantedCapabilities = info.capabilities.filter { it in requirement.hostCapabilities }.sorted()
        if (!grantedCapabilities.containsAll(requirement.requiredCapabilities)) {
            return EvaluationResult(LuaRuntimeProviderRejection.CAPABILITY_MISMATCH, null)
        }

        return EvaluationResult(
            rejection = null,
            pinned = LuaRuntimeProviderPinnedIdentity(
                component = candidate.descriptor.component,
                packageUid = candidate.descriptor.packageUid,
                versionCode = candidate.descriptor.versionCode,
                lastUpdateTime = candidate.descriptor.lastUpdateTime,
                signerSha256 = candidate.descriptor.signerSha256,
                runtimeInfo = info,
                protocolVersion = protocol,
                grantedCapabilities = grantedCapabilities,
            ),
        )
    }

    private fun hasValidPackageIdentity(descriptor: LuaRuntimeProviderDescriptor): Boolean {
        if (descriptor.packageUid < 0 || descriptor.versionCode <= 0L || descriptor.lastUpdateTime < 0L) return false
        if (descriptor.signerSha256.isEmpty()) return false
        val normalized = descriptor.signerSha256.map(String::lowercase)
        return normalized.distinct().size == normalized.size && normalized.all(::isSha256)
    }

    private fun hasValidAbiInventory(values: Collection<String>): Boolean =
        values.isNotEmpty() &&
            values.size <= LuaRuntimeContract.MAX_SUPPORTED_ABIS &&
            values.distinct().size == values.size &&
            values.all(::isAbi)

    private data class EvaluationResult(
        val rejection: LuaRuntimeProviderRejection?,
        val pinned: LuaRuntimeProviderPinnedIdentity?,
    )

    private val CANDIDATE_ORDER = compareBy<LuaRuntimeProviderCandidate>(
        { it.descriptor.component.packageName },
        { it.descriptor.component.className },
        { it.runtimeInfo.providerId },
        { it.descriptor.versionCode },
        { it.descriptor.lastUpdateTime },
    )
}

internal object LuaRuntimeProviderCallbackCallerPolicy {
    fun isExpectedUid(pinned: LuaRuntimeProviderPinnedIdentity, callingUid: Int): Boolean =
        callingUid >= 0 && callingUid == pinned.expectedCallbackUid
}

private fun normalizeSha256(value: String): String {
    require(isSha256(value)) { "Signer digest must be SHA-256" }
    return value.lowercase()
}

private fun isSha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun isAbi(value: String): Boolean =
    value.length <= LuaRuntimeContract.MAX_ABI_BYTES &&
        value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))

private fun isCapabilityName(value: String): Boolean =
    value.matches(Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*"))

private fun <T> immutableSnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun immutableSortedSnapshot(values: Collection<String>): List<String> =
    Collections.unmodifiableList(values.toSortedSet().toList())
