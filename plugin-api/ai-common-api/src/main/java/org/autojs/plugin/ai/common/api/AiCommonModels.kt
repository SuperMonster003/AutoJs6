package org.autojs.plugin.ai.common.api

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

data class AiProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<AiProtocolVersion> {
    init {
        require(major > 0) { "Protocol major version must be positive" }
        require(minor >= 0) { "Protocol minor version must not be negative" }
    }

    override fun compareTo(other: AiProtocolVersion): Int =
        compareValuesBy(this, other, AiProtocolVersion::major, AiProtocolVersion::minor)
}

data class AiProtocolRange(
    val minimum: AiProtocolVersion,
    val maximum: AiProtocolVersion,
) {
    init {
        require(minimum <= maximum) { "Protocol minimum must not exceed maximum" }
    }

    operator fun contains(version: AiProtocolVersion): Boolean = version >= minimum && version <= maximum
}

class AiProviderInfo(
    val providerId: String,
    val displayName: String,
    val implementationVersionName: String,
    val implementationVersionCode: Long,
    val protocolRange: AiProtocolRange,
    val locality: Int,
    val credentialMode: Int,
    declaredHttpsOrigins: List<String> = emptyList(),
    supportedAbis: List<String> = emptyList(),
    val minimumHostVersionCode: Long = 0L,
    val maximumHostVersionCode: Long? = null,
) {
    val declaredHttpsOrigins: List<String> = immutableListSnapshot(declaredHttpsOrigins)
    val supportedAbis: List<String> = immutableListSnapshot(supportedAbis)

    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        AiValidation.requireUtf8(displayName, 1, AiCommonLimits.MAX_DISPLAY_NAME_BYTES, "Provider display name")
        AiValidation.requireUtf8(
            implementationVersionName,
            1,
            AiCommonLimits.MAX_VERSION_NAME_BYTES,
            "Provider implementation version",
        )
        require(implementationVersionCode >= 0L) { "Implementation version code must not be negative" }
        require(locality in AiDataLocality.VALUES) { "Unknown provider locality" }
        require(credentialMode in AiCredentialMode.VALUES) { "Unknown credential mode" }
        require(declaredHttpsOrigins.size <= AiCommonLimits.MAX_ORIGINS) { "Too many declared origins" }
        require(declaredHttpsOrigins.distinct().size == declaredHttpsOrigins.size) { "Duplicate declared origin" }
        declaredHttpsOrigins.forEach(AiValidation::requireHttpsOrigin)
        if (locality == AiDataLocality.ON_DEVICE) {
            require(declaredHttpsOrigins.isEmpty()) { "An on-device provider must not declare remote origins" }
            require(credentialMode == AiCredentialMode.NONE) { "An on-device provider must not require remote credentials" }
        } else {
            require(declaredHttpsOrigins.isNotEmpty()) { "A remote or hybrid provider must declare an HTTPS origin" }
            require(credentialMode == AiCredentialMode.PLUGIN_MANAGED) {
                "Remote credentials must remain plugin-managed"
            }
        }
        require(supportedAbis.size <= AiCommonLimits.MAX_SUPPORTED_ABIS) { "Too many supported ABIs" }
        require(supportedAbis.distinct().size == supportedAbis.size) { "Duplicate supported ABI" }
        supportedAbis.forEach { abi ->
            require(AiValidation.ABI.matches(abi)) { "Invalid supported ABI" }
        }
        require(minimumHostVersionCode >= 0L) { "Minimum host version must not be negative" }
        maximumHostVersionCode?.let { maximum ->
            require(maximum >= minimumHostVersionCode) { "Maximum host version is below the minimum" }
        }
    }

    operator fun component1() = providerId
    operator fun component2() = displayName
    operator fun component3() = implementationVersionName
    operator fun component4() = implementationVersionCode
    operator fun component5() = protocolRange
    operator fun component6() = locality
    operator fun component7() = credentialMode
    operator fun component8() = declaredHttpsOrigins
    operator fun component9() = supportedAbis
    operator fun component10() = minimumHostVersionCode
    operator fun component11() = maximumHostVersionCode

    fun copy(
        providerId: String = this.providerId,
        displayName: String = this.displayName,
        implementationVersionName: String = this.implementationVersionName,
        implementationVersionCode: Long = this.implementationVersionCode,
        protocolRange: AiProtocolRange = this.protocolRange,
        locality: Int = this.locality,
        credentialMode: Int = this.credentialMode,
        declaredHttpsOrigins: List<String> = this.declaredHttpsOrigins,
        supportedAbis: List<String> = this.supportedAbis,
        minimumHostVersionCode: Long = this.minimumHostVersionCode,
        maximumHostVersionCode: Long? = this.maximumHostVersionCode,
    ) = AiProviderInfo(
        providerId = providerId,
        displayName = displayName,
        implementationVersionName = implementationVersionName,
        implementationVersionCode = implementationVersionCode,
        protocolRange = protocolRange,
        locality = locality,
        credentialMode = credentialMode,
        declaredHttpsOrigins = declaredHttpsOrigins,
        supportedAbis = supportedAbis,
        minimumHostVersionCode = minimumHostVersionCode,
        maximumHostVersionCode = maximumHostVersionCode,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AiProviderInfo &&
            providerId == other.providerId &&
            displayName == other.displayName &&
            implementationVersionName == other.implementationVersionName &&
            implementationVersionCode == other.implementationVersionCode &&
            protocolRange == other.protocolRange &&
            locality == other.locality &&
            credentialMode == other.credentialMode &&
            declaredHttpsOrigins == other.declaredHttpsOrigins &&
            supportedAbis == other.supportedAbis &&
            minimumHostVersionCode == other.minimumHostVersionCode &&
            maximumHostVersionCode == other.maximumHostVersionCode

    override fun hashCode(): Int = dataClassHashCode(
        providerId,
        displayName,
        implementationVersionName,
        implementationVersionCode,
        protocolRange,
        locality,
        credentialMode,
        declaredHttpsOrigins,
        supportedAbis,
        minimumHostVersionCode,
        maximumHostVersionCode,
    )

    override fun toString(): String =
        "AiProviderInfo(providerId=$providerId, displayName=$displayName, " +
            "implementationVersionName=$implementationVersionName, " +
            "implementationVersionCode=$implementationVersionCode, protocolRange=$protocolRange, " +
            "locality=$locality, credentialMode=$credentialMode, " +
            "declaredHttpsOrigins=$declaredHttpsOrigins, supportedAbis=$supportedAbis, " +
            "minimumHostVersionCode=$minimumHostVersionCode, maximumHostVersionCode=$maximumHostVersionCode)"
}

data class AiError(
    val code: Int,
    val message: String? = null,
    val retryDisposition: Int = AiRetryDisposition.NEVER,
    val providerCode: String? = null,
) {
    init {
        require(code in AiErrorCode.VALUES) { "Unknown AI error code" }
        require(retryDisposition in AiRetryDisposition.VALUES) { "Unknown retry disposition" }
        message?.let {
            require(it.length <= AiCommonLimits.MAX_ERROR_MESSAGE_CHARS) { "AI error message is too long" }
            AiValidation.requireUtf8(it, 1, AiCommonLimits.MAX_ERROR_MESSAGE_BYTES, "AI error message")
            require(it.none { character -> Character.isISOControl(character) }) {
                "AI error message contains control characters"
            }
        }
        providerCode?.let {
            AiValidation.requireUtf8(it, 1, AiCommonLimits.MAX_PROVIDER_ERROR_CODE_BYTES, "Provider error code")
            require(it.none { character -> Character.isISOControl(character) }) {
                "Provider error code contains control characters"
            }
        }
    }
}

/**
 * Cumulative V1 usage snapshot. When all three values are present, [totalTokens] is at least
 * [inputTokens] plus [outputTokens]. Reasoning and cached-input counters are detail counters and
 * are not added again because a provider may already include them in output or input tokens.
 */
data class AiUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cachedInputTokens: Long? = null,
) {
    init {
        val counters = listOf(inputTokens, outputTokens, totalTokens, reasoningTokens, cachedInputTokens)
        require(counters.any { it != null }) { "At least one usage counter is required" }
        require(counters.filterNotNull().all { it >= 0L }) { "Usage counters must not be negative" }
        if (totalTokens != null && inputTokens != null && outputTokens != null) {
            require(inputTokens <= Long.MAX_VALUE - outputTokens) { "Input and output token sum overflow" }
            require(totalTokens >= inputTokens + outputTokens) {
                "Total tokens must not be less than input plus output tokens"
            }
        } else if (totalTokens != null && inputTokens != null) {
            require(totalTokens >= inputTokens) { "Total tokens must not be less than input tokens" }
        } else if (totalTokens != null && outputTokens != null) {
            require(totalTokens >= outputTokens) { "Total tokens must not be less than output tokens" }
        }
    }
}

class AiPayloadReference(
    val mimeType: String,
    val declaredLengthBytes: Long,
    val sha256: String? = null,
    inlineBytes: ByteArray? = null,
    val descriptorIndex: Int? = null,
    val charset: String? = null,
) {
    private val inlineStorage = inlineBytes?.copyOf()

    val inlineBytes: ByteArray?
        get() = inlineStorage?.copyOf()

    val isInline: Boolean
        get() = inlineStorage != null

    init {
        require(AiValidation.MIME_TYPE.matches(mimeType)) { "Invalid payload MIME type" }
        require(declaredLengthBytes in 0L..AiCommonLimits.MAX_PAYLOAD_BYTES) { "Invalid payload length" }
        require((inlineStorage == null) xor (descriptorIndex == null)) {
            "A payload must contain exactly one inline value or descriptor reference"
        }
        inlineStorage?.let { bytes ->
            require(bytes.size <= AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES) { "Inline payload is too large" }
            require(declaredLengthBytes == bytes.size.toLong()) { "Inline payload length does not match its declaration" }
        }
        descriptorIndex?.let { require(it >= 0) { "Descriptor index must not be negative" } }
        sha256?.let { require(AiValidation.SHA_256.matches(it)) { "Invalid payload SHA-256" } }
        charset?.let {
            require(AiValidation.CHARSET.matches(it)) { "Invalid payload charset" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiPayloadReference) return false
        return mimeType == other.mimeType &&
            declaredLengthBytes == other.declaredLengthBytes &&
            sha256 == other.sha256 &&
            descriptorIndex == other.descriptorIndex &&
            charset == other.charset &&
            inlineStorage.contentEqualsNullable(other.inlineStorage)
    }

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + declaredLengthBytes.hashCode()
        result = 31 * result + (sha256?.hashCode() ?: 0)
        result = 31 * result + (inlineStorage?.contentHashCode() ?: 0)
        result = 31 * result + (descriptorIndex ?: 0)
        result = 31 * result + (charset?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "AiPayloadReference(mimeType=$mimeType, declaredLengthBytes=$declaredLengthBytes, " +
            "sha256=$sha256, inline=${inlineStorage != null}, descriptorIndex=$descriptorIndex, charset=$charset)"
}

object AiProtocolNegotiator {
    fun negotiate(host: AiProtocolRange, provider: AiProtocolRange): AiProtocolVersion? {
        val minimum = maxOf(host.minimum, provider.minimum)
        val maximum = minOf(host.maximum, provider.maximum)
        return maximum.takeIf { minimum <= maximum }
    }

    fun requireNegotiated(host: AiProtocolRange, provider: AiProtocolRange): AiProtocolVersion =
        negotiate(host, provider) ?: throw IllegalArgumentException("AI protocol ranges do not overlap")
}

object AiUsagePolicy {
    fun isMonotonic(previous: AiUsage?, next: AiUsage): Boolean {
        if (previous == null) return true
        return monotonic(previous.inputTokens, next.inputTokens) &&
            monotonic(previous.outputTokens, next.outputTokens) &&
            monotonic(previous.totalTokens, next.totalTokens) &&
            monotonic(previous.reasoningTokens, next.reasoningTokens) &&
            monotonic(previous.cachedInputTokens, next.cachedInputTokens)
    }

    /**
     * Accepts a partial cumulative snapshot without forgetting counters that the snapshot omits.
     * Keeping the merged snapshot prevents a provider from hiding a counter for one event and then
     * reintroducing it at a lower value. Reported totals are never derived or corrected: the merged
     * snapshot must still satisfy [AiUsage]'s additive invariant or the provider is rejected.
     */
    fun mergeMonotonic(previous: AiUsage?, next: AiUsage): AiUsage {
        require(isMonotonic(previous, next)) { "Usage counters moved backwards" }
        if (previous == null) return next

        val inputTokens = next.inputTokens ?: previous.inputTokens
        val outputTokens = next.outputTokens ?: previous.outputTokens
        return AiUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = next.totalTokens ?: previous.totalTokens,
            reasoningTokens = next.reasoningTokens ?: previous.reasoningTokens,
            cachedInputTokens = next.cachedInputTokens ?: previous.cachedInputTokens,
        )
    }

    private fun monotonic(previous: Long?, next: Long?): Boolean =
        previous == null || next == null || next >= previous
}

object AiDescriptorReferencePolicy {
    fun validateExactReferences(
        descriptorIndices: Collection<Int>,
        descriptorCount: Int,
        maximumDescriptors: Int,
    ) {
        require(descriptorCount in 0..maximumDescriptors) { "Descriptor count exceeds the negotiated limit" }
        require(descriptorIndices.size == descriptorCount) { "Every descriptor must be referenced exactly once" }
        require(descriptorIndices.distinct().size == descriptorIndices.size) { "A descriptor is referenced more than once" }
        require(descriptorIndices.all { it in 0 until descriptorCount }) { "Descriptor reference is out of range" }
    }
}

object AiValidation {
    val STABLE_ID = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    val ABI = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    val MIME_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}$")
    val SHA_256 = Regex("^[0-9a-f]{64}$")
    val CHARSET = Regex("^[a-z0-9][a-z0-9._-]{0,31}$")

    fun utf8Size(value: String): Int = try {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
        encoded.remaining()
    } catch (error: Exception) {
        throw IllegalArgumentException("Text is not valid UTF-8", error)
    }

    fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        throw IllegalArgumentException("Payload is not valid UTF-8", error)
    }

    fun requireUtf8(value: String, minimumBytes: Int, maximumBytes: Int, label: String) {
        val bytes = utf8Size(value)
        require(bytes in minimumBytes..maximumBytes) { "$label has an invalid UTF-8 length" }
    }

    fun requireHttpsOrigin(origin: String) {
        requireUtf8(origin, 1, AiCommonLimits.MAX_ORIGIN_BYTES, "Declared origin")
        val uri = runCatching { URI(origin) }.getOrElse { throw IllegalArgumentException("Invalid declared origin", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Declared remote origin must use HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Declared remote origin must include a host" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "Declared remote origin contains forbidden components" }
        require(uri.path.isNullOrEmpty() || uri.path == "/") { "Declared remote origin must not contain a path" }
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}

private fun <T> immutableListSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun dataClassHashCode(first: Any?, vararg remaining: Any?): Int {
    var result = first?.hashCode() ?: 0
    remaining.forEach { value -> result = 31 * result + (value?.hashCode() ?: 0) }
    return result
}
