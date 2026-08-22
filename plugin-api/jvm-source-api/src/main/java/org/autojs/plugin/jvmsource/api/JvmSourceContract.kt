package org.autojs.plugin.jvmsource.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashSet
import java.util.UUID

object JvmSourceContract {
    const val SERVICE_ACTION = "org.autojs.plugin.JVM_SOURCE"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val ENGINE_ID = "jvm-source"

    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 0
    const val ENTRY_API_VERSION = 1

    const val SCHEMA_MAJOR = 1
    const val SCHEMA_MINOR = 0
    const val SCHEMA_PROVIDER_INFO = 0x0301
    const val SCHEMA_CAPABILITIES = 0x0302
    const val SCHEMA_STARTED = 0x0310
    const val SCHEMA_DIAGNOSTIC = 0x0311
    const val SCHEMA_REQUEST = 0x0312
    const val SCHEMA_RESULT = 0x0313
    const val SCHEMA_ERROR = 0x0314
    const val SCHEMA_CANCELLATION = 0x0315
    const val SCHEMA_HOST_CALL = 0x0320
    const val SCHEMA_HOST_RESPONSE = 0x0321

    const val SOURCE_FD_INDEX = 0
    const val STDOUT_FD_INDEX = 1
    const val STDERR_FD_INDEX = 2

    const val MIN_ANDROID_API = 24
    const val MAX_METADATA_BYTES = 128 * 1024
    const val MAX_SOURCE_BYTES = 4L * 1024L * 1024L
    const val MAX_STDOUT_BYTES = 1024L * 1024L
    const val MAX_STDERR_BYTES = 1024L * 1024L
    const val MAX_DIAGNOSTIC_BYTES = 64 * 1024
    const val MAX_RESULT_JSON_BYTES = 64 * 1024
    const val MAX_HOST_PAYLOAD_BYTES = 64 * 1024
    const val MAX_CLASS_ARTIFACT_BYTES = 20L * 1024L * 1024L
    const val MAX_DEX_ARTIFACT_BYTES = 32L * 1024L * 1024L
    const val MAX_SOURCE_FILE_NAME_BYTES = 255
    const val MAX_ENTRY_CLASS_NAME_BYTES = 512
    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_VERSION_TEXT_BYTES = 128
    const val MAX_ERROR_CODE_BYTES = 128
    const val MAX_ERROR_MESSAGE_CODE_POINTS = 2_048
    const val MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS = 4_096
    const val MAX_HOST_METHOD_BYTES = 128
    const val MAX_PROCESS_NAME_BYTES = 256
    const val MAX_ALLOWED_HOST_CALLS = 32
    const val MAX_JSON_DEPTH = 64
    const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    const val MAX_TIMEOUT_MILLIS = 120_000L
    const val MAX_CONCURRENT_SESSIONS = 1
}

data class JvmProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<JvmProtocolVersion> {
    override fun compareTo(other: JvmProtocolVersion): Int =
        compareValuesBy(this, other, JvmProtocolVersion::major, JvmProtocolVersion::minor)
}

enum class JvmSourceLanguage(
    val wireName: String,
    val fileExtension: String,
) {
    JAVA("java", "java"),
    KOTLIN("kotlin", "kt"),
    ;

    companion object {
        @JvmStatic
        fun fromExtension(extension: String): JvmSourceLanguage? = entries.firstOrNull {
            it.fileExtension.equals(extension.removePrefix("."), ignoreCase = true)
        }

        @JvmStatic
        fun fromFileName(fileName: String): JvmSourceLanguage? =
            fromExtension(fileName.substringAfterLast('.', missingDelimiterValue = ""))
    }
}

enum class JvmSourceCompilerFamily(
    val wireName: String,
    val language: JvmSourceLanguage,
) {
    ECJ("ecj", JvmSourceLanguage.JAVA),
    KOTLIN_JVM("kotlin-jvm", JvmSourceLanguage.KOTLIN),
}

enum class JvmIsolationCapability(val wireName: String) {
    COMPILER_AND_WORKER_SEPARATED("compiler-and-worker-separated"),
    FRESH_WORKER_PER_EXECUTION("fresh-worker-per-execution"),
    HARD_KILL_AFTER_GRACE("hard-kill-after-grace"),
}

/** Stable execution-loader identity carried by every successful Protocol V1 result. */
enum class JvmDexLoaderKind(
    val wireCode: Int,
    val wireName: String,
) {
    PRIVATE_DEX_CLASS_LOADER(1, "PRIVATE_DEX_CLASS_LOADER"),
    IN_MEMORY_DEX_CLASS_LOADER(2, "IN_MEMORY_DEX_CLASS_LOADER"),
}

/** Conservative DEX/container profile shared by provider and host validation. */
object JvmDexRuntimeProfile {
    @JvmStatic
    fun loaderKindForApi(runtimeApi: Int): JvmDexLoaderKind? = when {
        runtimeApi < JvmSourceContract.MIN_ANDROID_API -> null
        runtimeApi < 26 -> JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER
        else -> JvmDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER
    }

    @JvmStatic
    fun admittedVersions(api: Int): Set<String> = when {
        api >= 29 -> VERSION_040_PROFILE
        api >= 28 -> VERSION_039_PROFILE
        api >= 26 -> VERSION_038_PROFILE
        api >= JvmSourceContract.MIN_ANDROID_API -> VERSION_037_PROFILE
        else -> emptySet()
    }

    @JvmStatic
    fun isKnownVersion(dexVersion: String): Boolean = dexVersion in VERSION_040_PROFILE

    // DEX 041 remains rejected: Protocol V1 validates the classic single-DEX container only.
    private val VERSION_037_PROFILE = frozenSetOf("035", "037")
    private val VERSION_038_PROFILE = frozenSetOf("035", "037", "038")
    private val VERSION_039_PROFILE = frozenSetOf("035", "037", "038", "039")
    private val VERSION_040_PROFILE = frozenSetOf("035", "037", "038", "039", "040")

    private fun frozenSetOf(vararg values: String): Set<String> =
        Collections.unmodifiableSet(LinkedHashSet(values.asList()))
}

enum class JvmSourceFailurePhase(val wireCode: Int) {
    NEGOTIATION(1),
    INPUT(2),
    COMPILATION(3),
    DEXING(4),
    WORKER_START(5),
    EXECUTION(6),
    HOST_BRIDGE(7),
    CLEANUP(8),
}

enum class JvmSourceErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    UNSUPPORTED_PROTOCOL(2),
    UNSUPPORTED_LANGUAGE(3),
    SOURCE_TOO_LARGE(4),
    COMPILATION_FAILED(5),
    DEXING_FAILED(6),
    EXECUTION_FAILED(7),
    OUTPUT_LIMIT_EXCEEDED(8),
    TIMEOUT(9),
    WORKER_DIED(10),
    BUSY(11),
    HOST_CALL_REJECTED(12),
    INTERNAL(13),
    ARTIFACT_INVALID(14),
    ENTRY_POINT_MISSING(15),
    ENTRY_POINT_AMBIGUOUS(16),
    ENTRY_POINT_ABI_INCOMPATIBLE(17),
    CLASS_LOADING_FAILED(18),
}

enum class JvmCancellationReason(val wireCode: Int) {
    REQUESTED(1),
    TIMEOUT(2),
    SESSION_CLOSED(3),
    HOST_DIED(4),
}

enum class JvmDiagnosticSeverity(val wireCode: Int) {
    INFO(1),
    WARNING(2),
    ERROR(3),
}

enum class JvmSourceContractViolation {
    INVALID_VALUE,
    UNKNOWN_ENUM,
    PROTOCOL_INCOMPATIBLE,
    CAPABILITY_INCOMPATIBLE,
}

class JvmSourceContractException(
    val violation: JvmSourceContractViolation,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class JvmSha256 private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHexString(): String = value.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    override fun equals(other: Any?): Boolean = other is JvmSha256 && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHexString()

    companion object {
        const val BYTE_COUNT = 32

        @JvmStatic
        fun fromBytes(value: ByteArray): JvmSha256 {
            if (value.size != BYTE_COUNT) {
                throw JvmSourceContractException(
                    JvmSourceContractViolation.INVALID_VALUE,
                    "SHA-256 value must contain exactly $BYTE_COUNT bytes",
                )
            }
            return JvmSha256(value.copyOf())
        }

        @JvmStatic
        fun digest(value: ByteArray): JvmSha256 =
            fromBytes(MessageDigest.getInstance("SHA-256").digest(value))
    }
}

/**
 * Computes the protocol-v1 toolchain identity. Every component is length-prefixed
 * before hashing, and the domain includes the protocol and entry-API versions so
 * that the same tool versions cannot be confused with a future contract.
 */
object JvmToolchainFingerprint {
    private const val DOMAIN = "org.autojs.jvm-source.toolchain"

    @JvmStatic
    fun compute(
        language: JvmSourceLanguage,
        sourceCompilerFamily: JvmSourceCompilerFamily,
        sourceCompilerVersion: String,
        d8Version: String,
        runtimeLibraryFingerprint: JvmSha256,
    ): JvmSha256 {
        val digest = MessageDigest.getInstance("SHA-256")

        fun add(value: ByteArray) {
            digest.update(
                ByteBuffer.allocate(Int.SIZE_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(value.size)
                    .array(),
            )
            digest.update(value)
        }

        fun add(value: String) = add(value.toByteArray(Charsets.UTF_8))

        add(DOMAIN)
        add(JvmSourceContract.PROTOCOL_MAJOR.toString())
        add(JvmSourceContract.ENTRY_API_VERSION.toString())
        add(language.wireName)
        add(sourceCompilerFamily.wireName)
        add(sourceCompilerVersion)
        add(d8Version)
        add(runtimeLibraryFingerprint.toByteArray())
        return JvmSha256.fromBytes(digest.digest())
    }
}

class JvmRequestId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toUuid(): UUID {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    override fun equals(other: Any?): Boolean = other is JvmRequestId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toUuid().toString()

    companion object {
        const val BYTE_COUNT = 16

        @JvmStatic
        fun fromBytes(value: ByteArray): JvmRequestId {
            if (value.size != BYTE_COUNT) {
                throw JvmSourceContractException(
                    JvmSourceContractViolation.INVALID_VALUE,
                    "Request ID must contain exactly $BYTE_COUNT bytes",
                )
            }
            return JvmRequestId(value.copyOf())
        }

        @JvmStatic
        fun fromUuid(value: UUID): JvmRequestId = fromBytes(
            ByteBuffer.allocate(BYTE_COUNT)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array(),
        )
    }
}
