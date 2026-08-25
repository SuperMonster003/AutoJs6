package org.autojs.plugin.dexcompiler.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object DexCompilerContract {
    const val SERVICE_ACTION = "org.autojs.plugin.DEX_COMPILER"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val ENGINE_ID = "dex-compiler"

    const val PROTOCOL_MAJOR = 1
    /** Legacy raw-program default. Callers opt into 1.1 explicitly for classpath bundles. */
    const val PROTOCOL_MINOR = 0
    const val PROTOCOL_CLASSPATH_BUNDLE_MINOR = 1

    const val SCHEMA_MAJOR = 1
    const val SCHEMA_MINOR = 0

    const val SCHEMA_COMPILER_INFO = 0x0201
    const val SCHEMA_CAPABILITIES = 0x0202
    const val SCHEMA_RUNTIME_LIBRARY_IDENTITY = 0x0203
    const val SCHEMA_DIAGNOSTIC = 0x0204
    const val SCHEMA_INPUT_IDENTITY = 0x0205
    const val SCHEMA_DIAGNOSTIC_POSITION = 0x0206
    const val SCHEMA_COMPILE_REQUEST = 0x0210
    const val SCHEMA_STARTED = 0x0211
    const val SCHEMA_PROGRESS = 0x0212
    const val SCHEMA_RESULT = 0x0213
    const val SCHEMA_ERROR = 0x0214
    const val SCHEMA_CANCELLATION = 0x0215

    const val CANONICALIZATION_POLICY_VERSION = 1

    const val MAX_METADATA_BYTES = 256 * 1024
    const val MAX_COMPRESSED_PROGRAM_BYTES = 128L * 1024L * 1024L
    const val MAX_CLASSPATH_JARS = 32
    const val MAX_COMPRESSED_CLASSPATH_JAR_BYTES = 64L * 1024L * 1024L
    const val MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES = 128L * 1024L * 1024L
    const val MAX_COMPRESSED_INPUT_BUNDLE_BYTES = 256L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES = 20_000
    const val MAX_TOTAL_INPUT_ARCHIVE_ENTRIES = 60_000
    const val MAX_UNCOMPRESSED_PROGRAM_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_INPUT_BYTES = 512L * 1024L * 1024L
    const val MAX_TOTAL_CLASS_BYTES = 128L * 1024L * 1024L
    const val MAX_TOTAL_INPUT_CLASS_BYTES = 256L * 1024L * 1024L
    const val MAX_SINGLE_CLASS_BYTES = 8L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES = 256L * 1024L * 1024L
    const val MAX_DEX_ENTRIES = 64
    const val MAX_DIAGNOSTIC_BYTES = 64 * 1024
    const val MAX_ERROR_MESSAGE_CODE_POINTS = 512
    const val MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS = 4_096
    const val MAX_DIAGNOSTIC_ORIGIN_BYTES = 128
    const val MAX_DIAGNOSTIC_ENTRY_BYTES = 1_024
    const val MAX_RUNTIME_LIBRARY_IDENTITIES = 512
    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_VERSION_TEXT_BYTES = 128
    const val MAX_DIAGNOSTIC_CODE_BYTES = 128
    const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    const val MAX_TIMEOUT_MILLIS = 300_000L
    const val MAX_CONCURRENT_SESSIONS = 1

    val PROTOCOL_V1_0 = DexProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_MINOR)
    val PROTOCOL_V1_1 = DexProtocolVersion(PROTOCOL_MAJOR, PROTOCOL_CLASSPATH_BUNDLE_MINOR)
}

data class DexProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<DexProtocolVersion> {
    override fun compareTo(other: DexProtocolVersion): Int =
        compareValuesBy(this, other, DexProtocolVersion::major, DexProtocolVersion::minor)
}

enum class DexCompilerAbiMode(val wireCode: Int) {
    ANY(1),
}

enum class DexCompilerFamily(val wireCode: Int) {
    D8(1),
}

enum class DexCompilerInputFormat(val wireName: String) {
    JAR("jar"),
}

enum class DexCompilerInputLayout(val wireCode: Int) {
    PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1(1),
}

enum class DexCompilerInputRole(val wireCode: Int) {
    PROGRAM(1),
    CLASSPATH(2),
}

enum class DexCompilerOutputFormat(val wireName: String) {
    DEX_ZIP("dex-zip"),
}

enum class DexCompilerMode(val wireName: String) {
    DEBUG("debug"),
    RELEASE("release"),
}

enum class DexCompilerDeterminismClaim(val wireCode: Int) {
    NOT_CLAIMED(1),
}

enum class DexRuntimeLibraryModel(val wireCode: Int) {
    DEVICE_RUNTIME_BOOTCLASSPATH_V1(1),
}

enum class DexCompilerProgressStage(val wireCode: Int) {
    QUEUED(1),
    VALIDATING(2),
    COMPILING(3),
    PACKAGING(4),
    WRITING(5),
}

enum class DexCompilerDiagnosticSeverity(val wireCode: Int) {
    INFO(1),
    WARNING(2),
    ERROR(3),
}

enum class DexCompilerErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    UNSUPPORTED_PROTOCOL(2),
    UNSUPPORTED_CAPABILITY(3),
    INPUT_TOO_LARGE(4),
    INVALID_ARCHIVE(5),
    NO_PROGRAM_CLASSES(6),
    MIN_API_UNSUPPORTED(7),
    LIBRARY_MODEL_INCOMPATIBLE(8),
    BUSY(9),
    COMPILATION_FAILED(10),
    OUTPUT_TOO_LARGE(11),
    STORAGE_EXHAUSTED(12),
    INTERNAL(13),
}

enum class DexCompilerFailurePhase(val wireCode: Int) {
    NEGOTIATION(1),
    QUEUE(2),
    INPUT_VALIDATION(3),
    COMPILATION(4),
    OUTPUT_PACKAGING(5),
    OUTPUT_WRITE(6),
    CLEANUP(7),
}

enum class DexCompilerCancellationReason(val wireCode: Int) {
    REQUESTED(1),
    SESSION_CLOSED(2),
}

enum class DexCompilerContractViolation {
    INVALID_VALUE,
    UNKNOWN_ENUM,
    PROTOCOL_INCOMPATIBLE,
    CAPABILITY_INCOMPATIBLE,
}

class DexCompilerContractException(
    val violation: DexCompilerContractViolation,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class DexSha256 private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHexString(): String = buildString(value.size * 2) {
        value.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 0x0f])
            append(HEX[byte.toInt() and 0x0f])
        }
    }

    override fun equals(other: Any?): Boolean = other is DexSha256 && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHexString()

    companion object {
        const val BYTE_COUNT = 32
        private const val HEX = "0123456789abcdef"

        fun fromBytes(value: ByteArray): DexSha256 {
            if (value.size != BYTE_COUNT) {
                throw DexCompilerContractException(
                    DexCompilerContractViolation.INVALID_VALUE,
                    "SHA-256 value must contain exactly $BYTE_COUNT bytes",
                )
            }
            return DexSha256(value.copyOf())
        }

        fun digest(value: ByteArray): DexSha256 = fromBytes(MessageDigest.getInstance("SHA-256").digest(value))
    }
}

class DexRequestId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toUuid(): UUID {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    override fun equals(other: Any?): Boolean = other is DexRequestId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toUuid().toString()

    companion object {
        const val BYTE_COUNT = 16

        fun fromBytes(value: ByteArray): DexRequestId {
            if (value.size != BYTE_COUNT) {
                throw DexCompilerContractException(
                    DexCompilerContractViolation.INVALID_VALUE,
                    "Request ID must contain exactly $BYTE_COUNT bytes",
                )
            }
            return DexRequestId(value.copyOf())
        }

        fun fromUuid(value: UUID): DexRequestId = fromBytes(
            ByteBuffer.allocate(BYTE_COUNT)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array()
        )
    }
}

object DexRuntimeLibraryFingerprint {
    private val DOMAIN = "AutoJs6:DexRuntimeLibraryFingerprint:v1\u0000"
        .toByteArray(StandardCharsets.UTF_8)

    fun compute(identities: Collection<DexRuntimeLibraryIdentity>): DexSha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN)
        digest.update(intBytes(identities.size))
        identities.forEachIndexed { index, identity ->
            digest.update(intBytes(index))
            digest.update(longBytes(identity.sizeBytes))
            digest.update(identity.contentSha256.toByteArray())
        }
        return DexSha256.fromBytes(digest.digest())
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value)
        .array()
}

/** Domain-separated identity for the exact ordered program/classpath input set. */
object DexCompilerInputSetFingerprint {
    private val DOMAIN = "AutoJs6:DexCompilerInputSetFingerprint:v1\u0000"
        .toByteArray(StandardCharsets.UTF_8)

    fun compute(identities: Collection<DexCompilerInputIdentity>): DexSha256 {
        val ordered = identities.toList()
        DexCompilerInputIdentityValidation.requireCanonical(ordered)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN)
        digest.update(intBytes(ordered.size))
        ordered.forEach { identity ->
            digest.update(intBytes(identity.role.wireCode))
            digest.update(intBytes(identity.ordinal))
            digest.update(longBytes(identity.sizeBytes))
            digest.update(identity.contentSha256.toByteArray())
        }
        return DexSha256.fromBytes(digest.digest())
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array()

    private fun longBytes(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value)
        .array()
}
