package org.autojs.plugin.ai.common.api

object AiCommonProtocol {
    const val MAJOR_VERSION = 1
    const val MINOR_VERSION = 0

    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    const val SCHEMA_PROTOCOL_RANGE = 0x4149_0001
    const val SCHEMA_PROVIDER_INFO = 0x4149_0002
    const val SCHEMA_ERROR = 0x4149_0003
    const val SCHEMA_USAGE = 0x4149_0004
    const val SCHEMA_PAYLOAD_REFERENCE = 0x4149_0005

    /**
     * Generated AIDL stubs in the current toolchain do not enforce trailing Parcel data.
     * Protocol implementations must treat the declared byte arrays and descriptors as the
     * complete capability surface and must never claim strict trailing-data rejection unless
     * they install a separately audited Binder preflight stub.
     */
    const val GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA = false
}

object AiDataLocality {
    const val ON_DEVICE = 1
    const val REMOTE = 2
    const val HYBRID = 3

    val VALUES = setOf(ON_DEVICE, REMOTE, HYBRID)
}

object AiCredentialMode {
    const val NONE = 1
    const val PLUGIN_MANAGED = 2

    val VALUES = setOf(NONE, PLUGIN_MANAGED)
}

object AiRetryDisposition {
    const val NEVER = 1
    const val EXPLICIT_NEW_REQUEST_ONLY = 2

    val VALUES = setOf(NEVER, EXPLICIT_NEW_REQUEST_ONLY)
}

object AiErrorCode {
    const val INVALID_REQUEST = 1
    const val UNSUPPORTED_PROTOCOL = 2
    const val UNSUPPORTED_CAPABILITY = 3
    const val PROVIDER_UNAVAILABLE = 4
    const val MODEL_UNAVAILABLE = 5
    const val PERMISSION_DENIED = 6
    const val QUOTA_EXCEEDED = 7
    const val BACKPRESSURE = 8
    const val TIMEOUT = 9
    const val CANCELLED = 10
    const val PROTOCOL_VIOLATION = 11
    const val PROVIDER_FAILED = 12
    const val DESCRIPTOR_FAILED = 13

    val VALUES = setOf(
        INVALID_REQUEST,
        UNSUPPORTED_PROTOCOL,
        UNSUPPORTED_CAPABILITY,
        PROVIDER_UNAVAILABLE,
        MODEL_UNAVAILABLE,
        PERMISSION_DENIED,
        QUOTA_EXCEEDED,
        BACKPRESSURE,
        TIMEOUT,
        CANCELLED,
        PROTOCOL_VIOLATION,
        PROVIDER_FAILED,
        DESCRIPTOR_FAILED,
    )
}

object AiCommonLimits {
    const val MAX_BINDER_ENVELOPE_BYTES = 256 * 1024
    const val MAX_INLINE_PAYLOAD_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 32L * 1024L * 1024L
    const val MAX_PROVIDER_ID_BYTES = 128
    const val MAX_DISPLAY_NAME_BYTES = 256
    const val MAX_VERSION_NAME_BYTES = 128
    const val MAX_ORIGINS = 32
    const val MAX_ORIGIN_BYTES = 2 * 1024
    const val MAX_SUPPORTED_ABIS = 16
    const val MAX_ABI_BYTES = 64
    const val MAX_MIME_TYPE_BYTES = 128
    const val MAX_ERROR_MESSAGE_BYTES = 2 * 1024
    const val MAX_ERROR_MESSAGE_CHARS = 512
    const val MAX_PROVIDER_ERROR_CODE_BYTES = 256
    /** Maximum descriptors concurrently carried by one request-opening Binder event. */
    const val MAX_DESCRIPTORS_PER_REQUEST = 16

    /**
     * Maximum descriptors concurrently carried by one session method or callback event. This is
     * not a cumulative count across the session lifetime; closed descriptors release the capacity.
     */
    const val MAX_DESCRIPTORS_PER_SESSION = 32
}
