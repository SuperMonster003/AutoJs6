package org.autojs.plugin.lua.runtime.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

object LuaRuntimeContract {
    const val SERVICE_ACTION = LuaPluginActions.RUNTIME
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val RUNTIME_SLOT_LUA54 = "lua54"

    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 0
    const val SCHEMA_MAJOR = 1
    const val SCHEMA_MINOR = 0

    const val SCHEMA_VALUE = 0x4C55_0001
    const val SCHEMA_MAP_ENTRY = 0x4C55_0002
    const val SCHEMA_RUNTIME_INFO = 0x4C55_0010
    const val SCHEMA_EXECUTION_REQUEST = 0x4C55_0011
    const val SCHEMA_EXECUTION_STARTED = 0x4C55_0012
    const val SCHEMA_OUTPUT = 0x4C55_0013
    const val SCHEMA_RESULT = 0x4C55_0014
    const val SCHEMA_ERROR = 0x4C55_0015
    const val SCHEMA_CANCELLATION = 0x4C55_0016
    const val SCHEMA_HOST_REQUEST = 0x4C55_0020
    const val SCHEMA_HOST_RESULT = 0x4C55_0021
    const val SCHEMA_HOST_ERROR = 0x4C55_0022

    const val MAX_METADATA_BYTES = 256 * 1024
    const val MAX_SOURCE_BYTES = 16L * 1024L * 1024L
    const val MAX_MEMORY_BYTES = 256L * 1024L * 1024L
    const val DEFAULT_MEMORY_BYTES = 32L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES = 8L * 1024L * 1024L
    const val DEFAULT_OUTPUT_BYTES = 1024L * 1024L
    const val MAX_OUTPUT_CHUNK_BYTES = 32 * 1024
    const val MAX_OUTSTANDING_OUTPUT_CREDITS = 128
    const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1000L
    const val MAX_CONCURRENT_EXECUTIONS = 1

    const val MAX_VALUE_DEPTH = 32
    const val MAX_VALUE_NODES = 4_096
    const val MAX_VALUE_DATA_BYTES = 256 * 1024
    const val MAX_VALUE_CONTAINER_ENTRIES = 1_023
    const val MAX_VALUE_STRING_OR_BYTES = 64 * 1024
    const val MAX_MAP_KEY_BYTES = 1_024

    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_VERSION_TEXT_BYTES = 128
    const val MAX_RUNTIME_SLOT_BYTES = 64
    const val MAX_ABI_BYTES = 64
    const val MAX_SUPPORTED_ABIS = 16
    const val MAX_CAPABILITIES = 64
    const val MAX_CAPABILITY_NAME_BYTES = 128
    const val MAX_SOURCE_NAME_BYTES = 1_024
    const val MAX_ERROR_MESSAGE_CODE_POINTS = 2_048
    const val MAX_CALL_ID_BYTES = 128
    const val MAX_CONCURRENT_HOST_CALLS = 32
    const val MAX_HOST_CALLS_PER_EXECUTION = 1_024

    /** Generated AIDL stubs currently do not prove strict trailing Parcel rejection. */
    const val GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA = false
}

data class LuaProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<LuaProtocolVersion> {
    override fun compareTo(other: LuaProtocolVersion): Int =
        compareValuesBy(this, other, LuaProtocolVersion::major, LuaProtocolVersion::minor)
}

enum class LuaRuntimeFamily(val wireCode: Int) {
    PUC_LUA(1),
}

enum class LuaOutputStream(val wireCode: Int) {
    STDOUT(1),
    STDERR(2),
}

enum class LuaExecutionErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    UNSUPPORTED_PROTOCOL(2),
    UNSUPPORTED_CAPABILITY(3),
    SOURCE_TOO_LARGE(4),
    SOURCE_DIGEST_MISMATCH(5),
    BUSY(6),
    SYNTAX_ERROR(7),
    RUNTIME_ERROR(8),
    MEMORY_LIMIT(9),
    TIMEOUT(10),
    HOST_CAPABILITY_FAILED(11),
    INTERNAL(12),
    OUTPUT_LIMIT(13),
    RESULT_LIMIT(14),
    PROTOCOL_VIOLATION(15),
    DESCRIPTOR_FAILED(16),
}

/** Advisory only. The host never redispatches an execution automatically. */
enum class LuaRetryDisposition(val wireCode: Int) {
    DO_NOT_RETRY(1),
    NEW_REQUEST_MAY_SUCCEED(2),
}

enum class LuaExecutionFailurePhase(val wireCode: Int) {
    NEGOTIATION(1),
    QUEUE(2),
    SOURCE_VALIDATION(3),
    LOAD(4),
    EXECUTION(5),
    HOST_CALL(6),
    CLEANUP(7),
}

enum class LuaCancellationReason(val wireCode: Int) {
    REQUESTED(1),
    SESSION_CLOSED(2),
    HOST_SHUTDOWN(3),
}

enum class LuaHostErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    CAPABILITY_DENIED(2),
    CAPABILITY_UNAVAILABLE(3),
    QUOTA_EXCEEDED(4),
    CANCELLED(5),
    INTERNAL(6),
}

enum class LuaContractViolation {
    INVALID_VALUE,
    UNKNOWN_ENUM,
    PROTOCOL_INCOMPATIBLE,
    CAPABILITY_INCOMPATIBLE,
}

class LuaContractException(
    val violation: LuaContractViolation,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class LuaSha256 private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHexString(): String = buildString(value.size * 2) {
        value.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 0x0f])
            append(HEX[byte.toInt() and 0x0f])
        }
    }

    override fun equals(other: Any?): Boolean = other is LuaSha256 && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHexString()

    companion object {
        const val BYTE_COUNT = 32
        private const val HEX = "0123456789abcdef"

        fun fromBytes(value: ByteArray): LuaSha256 {
            if (value.size != BYTE_COUNT) {
                throw LuaContractException(
                    LuaContractViolation.INVALID_VALUE,
                    "SHA-256 value must contain exactly $BYTE_COUNT bytes",
                )
            }
            return LuaSha256(value.copyOf())
        }

        fun digest(value: ByteArray): LuaSha256 =
            fromBytes(MessageDigest.getInstance("SHA-256").digest(value))
    }
}

class LuaRequestId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toUuid(): UUID {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    override fun equals(other: Any?): Boolean = other is LuaRequestId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toUuid().toString()

    companion object {
        const val BYTE_COUNT = 16

        fun fromBytes(value: ByteArray): LuaRequestId {
            if (value.size != BYTE_COUNT) {
                throw LuaContractException(
                    LuaContractViolation.INVALID_VALUE,
                    "Request ID must contain exactly $BYTE_COUNT bytes",
                )
            }
            return LuaRequestId(value.copyOf())
        }

        fun fromUuid(value: UUID): LuaRequestId = fromBytes(
            ByteBuffer.allocate(BYTE_COUNT)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array(),
        )
    }
}
