package org.autojs.plugin.python.runtime.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

object PythonRuntimeContract {
    const val INFO_ACTION = "org.autojs.plugin.INFO"
    const val RUNTIME_ACTION = "org.autojs.plugin.python.RUNTIME"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val ENGINE_ID = "python"

    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 5
    const val SCHEMA_MAJOR = 1
    const val SCHEMA_MINOR = 0

    const val SCHEMA_RUNTIME_INFO = 0x5059_0001
    const val SCHEMA_CAPABILITIES = 0x5059_0002
    const val SCHEMA_RESOURCE_LIMITS = 0x5059_0003
    const val SCHEMA_PAYLOAD_REFERENCE = 0x5059_0004
    const val SCHEMA_EXECUTION_REQUEST = 0x5059_0010
    const val SCHEMA_SESSION_STARTED = 0x5059_0011
    const val SCHEMA_OUTPUT_CHUNK = 0x5059_0012
    const val SCHEMA_EXECUTION_RESULT = 0x5059_0013
    const val SCHEMA_TRACEBACK_FRAME = 0x5059_0014
    const val SCHEMA_EXECUTION_ERROR = 0x5059_0015
    const val SCHEMA_CANCELLATION = 0x5059_0016
    const val SCHEMA_INTERACTIVE_INPUT_POLICY = 0x5059_0017
    const val SCHEMA_INPUT_PROMPT = 0x5059_0018
    const val SCHEMA_INPUT_REPLY = 0x5059_0019
    const val SCHEMA_RESULT_POLICY = 0x5059_001a
    const val SCHEMA_OUTPUT_ARTIFACT = 0x5059_001b

    const val SOURCE_MIME_TYPE = "text/x-python"
    const val WORKSPACE_MIME_TYPE = "application/vnd.autojs6.python.workspace+zip"
    const val STDIN_MIME_TYPE = "application/octet-stream"

    const val MAX_METADATA_BYTES = 128 * 1024
    const val MAX_SOURCE_BYTES = 16L * 1024L * 1024L
    const val MAX_WORKSPACE_ARCHIVE_BYTES = 128L * 1024L * 1024L
    const val MAX_WORKSPACE_ENTRIES = 20_000
    const val MAX_WORKSPACE_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
    const val MAX_STDIN_BYTES = 16L * 1024L * 1024L
    const val MAX_HOST_CAPABILITY_SNAPSHOT_BYTES = 64L * 1024L
    const val MAX_OUTPUT_BYTES = 32L * 1024L * 1024L
    const val MAX_OUTPUT_CHUNK_BYTES = 48 * 1024
    const val MAX_OUTPUT_CHUNKS = 65_536L
    const val MAX_OUTSTANDING_OUTPUT_CREDITS = 8
    const val MAX_TIMEOUT_MILLIS = 24L * 60L * 60L * 1_000L
    const val DEFAULT_TIMEOUT_MILLIS = 5L * 60L * 1_000L
    const val MAX_INPUT_PROMPT_BYTES = 16 * 1024
    const val MAX_INPUT_REPLY_BYTES = 64 * 1024
    const val MAX_INPUT_PROMPTS = 1_024
    const val MAX_INPUT_WAIT_MILLIS = 30L * 60L * 1_000L
    const val MAX_STRUCTURED_JSON_BYTES = 64 * 1024
    const val MAX_OUTPUT_ARTIFACTS = 16
    const val MAX_OUTPUT_ARTIFACT_PATH_BYTES = 1024
    const val MAX_OUTPUT_ARTIFACT_BYTES = 4L * 1024L * 1024L
    const val MAX_TOTAL_OUTPUT_ARTIFACT_BYTES = 8L * 1024L * 1024L
    const val MAX_HOST_CAPABILITY_REQUEST_BYTES = 64 * 1024
    const val MAX_HOST_CAPABILITY_RESPONSE_BYTES = 64 * 1024
    const val MAX_HOST_CAPABILITY_CALLS = 1_024
    const val MAX_HOST_CAPABILITY_TEXT_BYTES = 32 * 1024
    const val HOST_CAPABILITY_CALL_TIMEOUT_MILLIS = 5_000L
    const val MAX_CONCURRENT_SESSIONS = 1
    const val MAX_ARGUMENTS = 128
    const val MAX_ARGUMENT_BYTES = 4 * 1024
    const val MAX_TOTAL_ARGUMENT_BYTES = 32 * 1024
    const val MAX_ENTRY_POINT_BYTES = 4 * 1024
    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_VERSION_TEXT_BYTES = 128
    const val MAX_ABIS = 8
    const val MAX_ERROR_MESSAGE_BYTES = 16 * 1024
    const val MAX_EXCEPTION_TEXT_BYTES = 16 * 1024
    const val MAX_TRACEBACK_FRAMES = 256
    const val MAX_TRACEBACK_TEXT_BYTES = 4 * 1024
}

data class PythonProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<PythonProtocolVersion> {
    override fun compareTo(other: PythonProtocolVersion): Int =
        compareValuesBy(this, other, PythonProtocolVersion::major, PythonProtocolVersion::minor)
}

enum class PythonImplementation(val wireCode: Int) {
    CPYTHON(1),
}

enum class PythonIsolationMode(val wireCode: Int) {
    DEDICATED_PLUGIN_PROCESS(1),
}

enum class PythonCancellationMode(val wireCode: Int) {
    PROCESS_RESTART_ONLY(1),
    COOPERATIVE_THEN_PROCESS_RESTART(2),
}

/** Selects whether [PythonExecutionRequest.entryPoint] is a file path or importable module name. */
enum class PythonEntryMode(val wireCode: Int) {
    FILE(1),
    MODULE(2),
}

enum class PythonPayloadKind(val wireCode: Int) {
    SOURCE(1),
    WORKSPACE_ARCHIVE(2),
    STDIN(3),
    HOST_CAPABILITY_SNAPSHOT(4),
}

enum class PythonOutputStream(val wireCode: Int) {
    STDOUT(1),
    STDERR(2),
}

enum class PythonInputEcho(val wireCode: Int) {
    VISIBLE(1),
    HIDDEN(2),
}

enum class PythonInputReplyStatus(val wireCode: Int) {
    VALUE(1),
    EOF(2),
    CANCELLED(3),
}

enum class PythonTracebackOrigin(val wireCode: Int) {
    PROJECT(1),
    STDLIB(2),
    PACKAGE(3),
    GENERATED(4),
}

enum class PythonErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    UNSUPPORTED_PROTOCOL(2),
    UNSUPPORTED_CAPABILITY(3),
    BUSY(4),
    SOURCE_REJECTED(5),
    WORKSPACE_REJECTED(6),
    PYTHON_EXCEPTION(7),
    TIMEOUT(8),
    OUTPUT_LIMIT_EXCEEDED(9),
    CANCELLATION_FAILED(10),
    RUNTIME_CRASHED(11),
    STORAGE_EXHAUSTED(12),
    INTERNAL(13),
    SESSION_OPEN_FAILED(14),
    STDIN_REJECTED(15),
    HOST_CAPABILITY_SNAPSHOT_REJECTED(16),
    INPUT_TIMEOUT(17),
    INPUT_LIMIT_EXCEEDED(18),
    OUTPUT_ARTIFACT_REJECTED(19),
}

enum class PythonFailurePhase(val wireCode: Int) {
    NEGOTIATION(1),
    SESSION_OPEN(2),
    INPUT_VALIDATION(3),
    RUNTIME_START(4),
    EXECUTION(5),
    OUTPUT(6),
    CANCELLATION(7),
    CLEANUP(8),
    INTERACTIVE_INPUT(9),
    RESULT(10),
}

enum class PythonCancellationReason(val wireCode: Int) {
    REQUESTED(1),
}

enum class PythonRuntimeContractViolation {
    INVALID_VALUE,
    UNKNOWN_ENUM,
    PROTOCOL_INCOMPATIBLE,
    CAPABILITY_INCOMPATIBLE,
    DESCRIPTOR_MISMATCH,
}

class PythonRuntimeContractException(
    val violation: PythonRuntimeContractViolation,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class PythonSha256 private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHexString(): String = buildString(value.size * 2) {
        value.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 0x0f])
            append(HEX[byte.toInt() and 0x0f])
        }
    }

    override fun equals(other: Any?): Boolean = other is PythonSha256 && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHexString()

    companion object {
        const val BYTE_COUNT = 32
        private const val HEX = "0123456789abcdef"

        fun fromBytes(value: ByteArray): PythonSha256 {
            if (value.size != BYTE_COUNT) {
                throw PythonRuntimeContractException(
                    PythonRuntimeContractViolation.INVALID_VALUE,
                    "SHA-256 value must contain exactly $BYTE_COUNT bytes",
                )
            }
            return PythonSha256(value.copyOf())
        }

        fun digest(value: ByteArray): PythonSha256 =
            fromBytes(MessageDigest.getInstance("SHA-256").digest(value))
    }
}

class PythonRequestId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toUuid(): UUID {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    override fun equals(other: Any?): Boolean = other is PythonRequestId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toUuid().toString()

    companion object {
        const val BYTE_COUNT = 16

        fun fromBytes(value: ByteArray): PythonRequestId {
            if (value.size != BYTE_COUNT) {
                throw PythonRuntimeContractException(
                    PythonRuntimeContractViolation.INVALID_VALUE,
                    "Request ID must contain exactly $BYTE_COUNT bytes",
                )
            }
            return PythonRequestId(value.copyOf())
        }

        fun fromUuid(value: UUID): PythonRequestId = fromBytes(
            ByteBuffer.allocate(BYTE_COUNT)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array(),
        )
    }
}

/** Monotonic, execution-scoped identifier for exactly one interactive input prompt. */
class PythonPromptId private constructor(val value: Long) {
    override fun equals(other: Any?): Boolean = other is PythonPromptId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()

    companion object {
        fun fromLong(value: Long): PythonPromptId {
            if (value <= 0L) {
                throw PythonRuntimeContractException(
                    PythonRuntimeContractViolation.INVALID_VALUE,
                    "Prompt ID must be positive",
                )
            }
            return PythonPromptId(value)
        }
    }
}
