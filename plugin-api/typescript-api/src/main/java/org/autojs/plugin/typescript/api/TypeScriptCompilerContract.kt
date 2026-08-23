package org.autojs.plugin.typescript.api

object TypeScriptCompilerContract {

    const val VERSION = 1
    const val HASH_INPUT =
        "autojs6.typescript.compiler.bundle-envelope.v1|" +
            "getContractVersion()->int|getContractHash()->String|" +
            "getCompilerInfo()->Bundle|prewarm(Bundle)->Bundle|" +
            "startCompilation(Bundle,ParcelFileDescriptor,ParcelFileDescriptor," +
            "ITypeScriptCompilationCallback)->Bundle|" +
            "cancelCompilation(Bundle)->boolean|" +
            "callback:onProgress(Bundle),onCompleted(Bundle)|" +
            "bundle:primitive-only|fd:input,output"
    const val HASH = "e35e8c6f71b8e0799678974e95aca9233225b3371c4a484437c7740defe9f86b"
    const val INPUT_ARCHIVE_TRANSPORT_VERSION = 1
    const val OUTPUT_ARCHIVE_TRANSPORT_VERSION = 1
    const val PROFILE_SCHEMA_REVISION = 1
    const val RUNTIME_CAPABILITY_SCHEMA_REVISION = 1
    const val ARCHIVE_FORMAT = "zip"
    const val ARCHIVE_PATH_ENCODING = "utf-8"

    const val PROFILE_RHINO = "rhino"
    const val PROFILE_NODE = "node"

    const val MODE_DEVELOPMENT = "development"
    const val MODE_RELEASE = "release"

    const val BACKEND_UNINITIALIZED = "uninitialized"
    const val BACKEND_WEBVIEW_WORKER = "webview_worker"

    const val STATUS_BOOTSTRAP = "bootstrap"
    const val STATUS_STARTING = "starting"
    const val STATUS_READY = "ready"
    const val STATUS_DEGRADED = "degraded"
    const val STATUS_FAILED = "failed"

    const val OUTCOME_EMITTED = "emitted"
    const val OUTCOME_DIAGNOSTICS = "diagnostics"
    const val OUTCOME_CANCELLED = "cancelled"
    const val OUTCOME_TIMED_OUT = "timed_out"
    const val OUTCOME_FAILED = "failed"

    const val STAGE_ADMISSION = "admission"
    const val STAGE_SNAPSHOT = "snapshot"
    const val STAGE_CONFIG = "config"
    const val STAGE_TYPE_CHECK = "type_check"
    const val STAGE_EMIT = "emit"
    const val STAGE_PACKAGE = "package"

    const val ERROR_R0_NOT_AVAILABLE = "ERR_AUTOJS6_TYPESCRIPT_R0_NOT_AVAILABLE"
    const val ERROR_INVALID_REQUEST = "ERR_AUTOJS6_TYPESCRIPT_INVALID_REQUEST"
    const val ERROR_CONTRACT_MISMATCH = "ERR_AUTOJS6_TYPESCRIPT_CONTRACT_MISMATCH"
    const val ERROR_BUSY = "ERR_AUTOJS6_TYPESCRIPT_COMPILER_BUSY"
    const val ERROR_CANCELLED = "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_CANCELLED"
    const val ERROR_COMPILATION_DIAGNOSTICS =
        "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_DIAGNOSTICS"
    const val ERROR_TIMEOUT = "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_TIMEOUT"
    const val ERROR_COMPILER_UNAVAILABLE = "ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE"
    const val ERROR_OUTPUT_TRANSPORT = "ERR_AUTOJS6_TYPESCRIPT_OUTPUT_TRANSPORT"
    const val ERROR_ARCHIVE_FORMAT = "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_FORMAT"
    const val ERROR_ARCHIVE_INTEGRITY = "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_INTEGRITY"
    const val ERROR_ARCHIVE_PATH = "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_PATH"
    const val ERROR_ARCHIVE_QUOTA = "ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA"

    const val KEY_ACCEPTED = "accepted"
    const val KEY_BACKEND = "backend"
    const val KEY_COMPILATION_MODE = "compilationMode"
    const val KEY_COMPILER_OPTIONS_JSON = "compilerOptionsJson"
    const val KEY_COMPILER_DISTRIBUTION_FINGERPRINT =
        "compilerDistributionFingerprint"
    const val KEY_CONTRACT_HASH = "contractHash"
    const val KEY_CONTRACT_VERSION = "contractVersion"
    const val KEY_DECLARATIONS_FINGERPRINT = "declarationsFingerprint"
    const val KEY_DIAGNOSTIC_COUNT = "diagnosticCount"
    const val KEY_ELAPSED_MS = "elapsedMs"
    const val KEY_EMITTED_MODULE_COUNT = "emittedModuleCount"
    const val KEY_ENTRY_SOURCE_URI = "entrySourceUri"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_INPUT_ARCHIVE_TRANSPORT_VERSION = "inputArchiveTransportVersion"
    const val KEY_JOB_TOKEN = "jobToken"
    const val KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION = "outputArchiveTransportVersion"
    const val KEY_OUTPUT_MANIFEST_SHA256 = "outputManifestSha256"
    const val KEY_OUTCOME = "outcome"
    const val KEY_PROJECT_ID = "projectId"
    const val KEY_PROFILE_SCHEMA_REVISION = "profileSchemaRevision"
    const val KEY_REQUEST_ID = "requestId"
    const val KEY_RUNTIME_CAPABILITIES_FINGERPRINT =
        "runtimeCapabilitiesFingerprint"
    const val KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION =
        "runtimeCapabilitySchemaRevision"
    const val KEY_STAGE = "stage"
    const val KEY_SUCCEEDED = "succeeded"
    const val KEY_TARGET_PROFILE = "targetProfile"
    const val KEY_TIMEOUT_MS = "timeoutMs"

    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_TIMEOUT_MS = 120_000L
    const val MAX_REQUEST_ID_CHARS = 128
    const val MAX_JOB_TOKEN_CHARS = 256
    const val MAX_PROJECT_ID_CHARS = 256
    const val MAX_SOURCE_URI_CHARS = 4_096
    const val MAX_ERROR_MESSAGE_CHARS = 4_096
    const val MAX_METADATA_BYTES = 64 * 1024
    const val MAX_METADATA_KEYS = 64
    const val MAX_METADATA_ARRAY_ELEMENTS = 256
    const val MAX_COMPILER_OPTIONS_BYTES = 48 * 1024
    const val MAX_INPUT_FILES = 4_096
    const val MAX_INPUT_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_INPUT_BYTES = 64L * 1024L * 1024L
    const val MAX_SINGLE_FILE_BYTES = 8L * 1024L * 1024L
    const val MAX_OUTPUT_FILES = 8_192
    const val MAX_OUTPUT_ARCHIVE_BYTES = 128L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES = 256L * 1024L * 1024L
    const val MAX_SINGLE_JAVASCRIPT_BYTES = 16L * 1024L * 1024L
    const val MAX_SINGLE_SOURCE_MAP_BYTES = 32L * 1024L * 1024L
    const val MAX_DIAGNOSTICS = 10_000
    const val MAX_DIAGNOSTICS_BYTES = 8L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 4L * 1024L * 1024L
    const val MAX_JSON_DEPTH = 64
    const val MAX_ARCHIVE_ENTRY_NAME_BYTES = 1_024
    const val MAX_ARCHIVE_PATH_DEPTH = 64
    const val MAX_ZIP_METADATA_BYTES = 8L * 1024L * 1024L
    const val MAX_PRIVATE_STAGING_BYTES = 384L * 1024L * 1024L
    const val MAX_ARCHIVE_COMPRESSION_RATIO = 100

    val SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts", ".mts", ".cts")

    val RHINO_SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts")

    val NODE_SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts", ".mts", ".cts")

    val TARGET_PROFILES
        get() = arrayOf(PROFILE_RHINO, PROFILE_NODE)

    val COMPILATION_MODES
        get() = arrayOf(MODE_DEVELOPMENT, MODE_RELEASE)

    val TERMINAL_OUTCOMES
        get() = arrayOf(
            OUTCOME_EMITTED,
            OUTCOME_DIAGNOSTICS,
            OUTCOME_CANCELLED,
            OUTCOME_TIMED_OUT,
            OUTCOME_FAILED,
        )

    fun supportedExtensions(targetProfile: String): Array<String> = when (targetProfile) {
        PROFILE_RHINO -> RHINO_SUPPORTED_EXTENSIONS
        PROFILE_NODE -> NODE_SUPPORTED_EXTENSIONS
        else -> emptyArray()
    }
}
