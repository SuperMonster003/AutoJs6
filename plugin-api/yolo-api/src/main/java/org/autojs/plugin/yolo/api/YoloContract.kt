package org.autojs.plugin.yolo.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

object YoloContract {
    const val INFO_ACTION = "org.autojs.plugin.INFO"
    const val SERVICE_ACTION = "org.autojs.plugin.YOLO"
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val ENGINE_ID = "yolo"

    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 0
    const val SCHEMA_MAJOR = 1
    const val SCHEMA_MINOR = 0

    const val SCHEMA_PROVIDER_INFO = 0x594F_0001
    const val SCHEMA_CAPABILITIES = 0x594F_0002
    const val SCHEMA_RESOURCE_LIMITS = 0x594F_0003
    const val SCHEMA_MODEL_ARTIFACT = 0x594F_0004
    const val SCHEMA_OPEN_SESSION_REQUEST = 0x594F_0010
    const val SCHEMA_IMAGE_REFERENCE = 0x594F_0011
    const val SCHEMA_DETECT_REQUEST = 0x594F_0012
    const val SCHEMA_BOUNDS = 0x594F_0013
    const val SCHEMA_DETECTION = 0x594F_0014
    const val SCHEMA_DETECT_RESULT = 0x594F_0015
    const val SCHEMA_ERROR = 0x594F_0016
    const val SCHEMA_CANCEL_REQUEST = 0x594F_0017
    const val SCHEMA_CANCELLATION = 0x594F_0018

    const val MAX_METADATA_BYTES = 256 * 1024
    const val MAX_FIELD_BYTES = 64 * 1024
    const val MAX_WIRE_FIELDS = 2_048
    const val MAX_MODEL_DESCRIPTORS = 16
    const val MAX_MANIFEST_BYTES = 256L * 1024L
    const val MAX_MODEL_ARTIFACT_BYTES = 512L * 1024L * 1024L
    const val MAX_TOTAL_MODEL_BYTES = 1024L * 1024L * 1024L
    const val MAX_IMAGE_DIMENSION = 16_384
    const val MAX_IMAGE_BYTES = 256L * 1024L * 1024L
    const val MAX_DETECTIONS = 400
    const val MAX_CPU_THREADS = 64
    const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1_000L
    const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    const val MAX_CONCURRENT_SESSIONS = 8
    const val MAX_QUEUED_DETECTIONS = 0
    const val MAX_ROLE_BYTES = 64
    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_DISPLAY_NAME_BYTES = 256
    const val MAX_AUTHOR_BYTES = 256
    const val MAX_SUPPORTED_ABIS = 8
    const val MAX_ABI_NAME_BYTES = 64
    const val MAX_LOGICAL_NAME_BYTES = 256
    const val MAX_DECODER_ID_BYTES = 128
    const val MAX_VERSION_TEXT_BYTES = 128
    const val MAX_LABEL_BYTES = 256
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024

    const val REQUIRED_MANIFEST_ROLE = "manifest"
}

data class YoloProtocolVersion(
    val major: Int,
    val minor: Int,
) : Comparable<YoloProtocolVersion> {
    override fun compareTo(other: YoloProtocolVersion): Int =
        compareValuesBy(this, other, YoloProtocolVersion::major, YoloProtocolVersion::minor)
}

enum class YoloBackend(val wireCode: Int) {
    NCNN(1),
}

enum class YoloTask(val wireCode: Int) {
    DETECT(1),
}

enum class YoloModelFormat(val wireCode: Int) {
    NCNN(1),
}

enum class YoloDevice(val wireCode: Int) {
    CPU(1),
    VULKAN(2),
}

enum class YoloPixelFormat(val wireCode: Int) {
    RGBA_8888(1),
}

enum class YoloErrorCode(val wireCode: Int) {
    INVALID_REQUEST(1),
    UNSUPPORTED_PROTOCOL(2),
    UNSUPPORTED_CAPABILITY(3),
    BUSY(4),
    MODEL_REJECTED(5),
    IMAGE_REJECTED(6),
    INFERENCE_FAILED(7),
    TIMEOUT(8),
    SESSION_CLOSED(9),
    RESOURCE_EXHAUSTED(10),
    INTERNAL(11),
}

enum class YoloFailurePhase(val wireCode: Int) {
    NEGOTIATION(1),
    SESSION_OPEN(2),
    INPUT_VALIDATION(3),
    ADMISSION(4),
    INFERENCE(5),
    DECODE(6),
    CANCELLATION(7),
    CLEANUP(8),
}

enum class YoloCancellationReason(val wireCode: Int) {
    REQUESTED(1),
    TIMEOUT(2),
    SESSION_CLOSED(3),
    PROVIDER_SHUTDOWN(4),
}

enum class YoloContractViolation {
    INVALID_VALUE,
    UNKNOWN_ENUM,
    PROTOCOL_INCOMPATIBLE,
    CAPABILITY_INCOMPATIBLE,
    DESCRIPTOR_MISMATCH,
}

class YoloContractException(
    val violation: YoloContractViolation,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class YoloSha256 private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHexString(): String = buildString(value.size * 2) {
        value.forEach { byte ->
            append(HEX[(byte.toInt() ushr 4) and 0x0f])
            append(HEX[byte.toInt() and 0x0f])
        }
    }

    override fun equals(other: Any?): Boolean = other is YoloSha256 && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHexString()

    companion object {
        const val BYTE_COUNT = 32
        private const val HEX = "0123456789abcdef"

        fun fromBytes(value: ByteArray): YoloSha256 {
            if (value.size != BYTE_COUNT) {
                throw YoloContractException(
                    YoloContractViolation.INVALID_VALUE,
                    "SHA-256 value must contain exactly $BYTE_COUNT bytes",
                )
            }
            return YoloSha256(value.copyOf())
        }

        fun digest(value: ByteArray): YoloSha256 =
            fromBytes(MessageDigest.getInstance("SHA-256").digest(value))
    }
}

class YoloRequestId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toUuid(): UUID {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long)
    }

    override fun equals(other: Any?): Boolean = other is YoloRequestId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toUuid().toString()

    companion object {
        const val BYTE_COUNT = 16

        fun fromBytes(value: ByteArray): YoloRequestId {
            if (value.size != BYTE_COUNT) {
                throw YoloContractException(
                    YoloContractViolation.INVALID_VALUE,
                    "Request ID must contain exactly $BYTE_COUNT bytes",
                )
            }
            return YoloRequestId(value.copyOf())
        }

        fun fromUuid(value: UUID): YoloRequestId = fromBytes(
            ByteBuffer.allocate(BYTE_COUNT)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value.mostSignificantBits)
                .putLong(value.leastSignificantBits)
                .array(),
        )
    }
}
