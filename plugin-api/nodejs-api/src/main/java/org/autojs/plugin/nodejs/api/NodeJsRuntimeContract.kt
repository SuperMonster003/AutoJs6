package org.autojs.plugin.nodejs.api

object NodeJsRuntimeContract {

    const val CONTRACT_VERSION = 2
    const val MIN_CONTRACT_VERSION = 2
    // Preserve the v2 handshake: published hosts require exact equality.
    const val MAX_CONTRACT_VERSION = 3
    const val POST_MESSAGE_CONTRACT_VERSION = 3
    const val KEY_MAX_CONTRACT_VERSION = "maxContractVersion"
    const val KEY_MESSAGE_KIND = "kind"
    // stdin: literal UTF-8 text; message: a JSON value encoded as a string.
    const val KEY_MESSAGE_DATA = "data"
    const val KEY_MESSAGE_EOF = "eof"
    const val MESSAGE_STDIN = "stdin"
    const val MESSAGE_HOST = "message"
    const val MAX_MESSAGE_BYTES = 64 * 1024
    const val HOST_CAPABILITY_BROKER_CONTRACT_VERSION = 1
    const val MODULE_SOURCE_PROVIDER_CONTRACT_VERSION = 3
    // Module-source transport versioning remains independent from the runtime
    // request contract so both published transport revisions stay readable.
    const val MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION = 1
    const val MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION = 3
    const val WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION = 2

    @JvmStatic
    fun supportsContractVersion(version: Int): Boolean =
        version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    @JvmStatic
    fun supportsModuleSourceProviderContractVersion(version: Int): Boolean =
        version in MODULE_SOURCE_PROVIDER_MIN_CONTRACT_VERSION..
            MODULE_SOURCE_PROVIDER_MAX_CONTRACT_VERSION

    const val KEY_CONTRACT_VERSION = "contractVersion"
    const val KEY_RUNTIME_SLOT = "runtimeSlot"
    const val KEY_NODE_VERSION = "nodeVersion"
    const val KEY_NATIVE_LIBRARY_NAME = "nativeLibraryName"
    const val KEY_BRIDGE_LIBRARY_NAME = "bridgeLibraryName"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_ACTIVE_EXECUTION_ID = "activeExecutionId"
    const val KEY_ACTIVE_EXECUTION_FOR_MS = "activeExecutionForMs"
    const val KEY_ACTIVE_EXECUTION_CANCELLATION_REQUESTED = "activeExecutionCancellationRequested"

    const val KEY_EXECUTION_ID = "executionId"
    /** Additive worker index; -1 means the request has not been assigned a process. */
    const val KEY_SLOT_ID = "slotId"
    const val KEY_SOURCE = "source"
    const val KEY_SOURCE_NAME = "sourceName"
    const val KEY_WORKING_DIRECTORY = "workingDirectory"
    const val KEY_SANDBOX_ROOT = "sandboxRoot"
    const val KEY_TIMEOUT_MS = "timeoutMs"
    /** Exit the dedicated process after this much idle time; absent/nonpositive keeps it resident. */
    const val KEY_IDLE_EXIT_MS = "idleExitMs"
    const val KEY_IDLE_FOR_MS = "idleForMs"
    const val KEY_EXECUTION_MODE = "executionMode"

    /**
     * Legacy no-op request metadata. Runtime selection is discovered from the
     * bound plugin service and cannot be overridden per execution.
     */
    @Deprecated("Ignored since runtime contract v2; select the installed runtime service instead")
    const val KEY_RUNTIME_ADAPTER = "runtimeAdapter"

    /**
     * Request-scoped, seekable archive transport for a sandbox that lives in
     * the host app's private storage. The host supplies an input snapshot and
     * an empty output file descriptor. The plugin executes in its own private
     * mirror and writes the final snapshot to the output descriptor. Contract
     * v2 adds an explicit-input manifest plus a final tombstone manifest so
     * only named input files can have deletions synchronized back.
     */
    const val KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION = "workspaceArchiveTransportVersion"
    const val KEY_WORKSPACE_ARCHIVE_INPUT_FD = "workspaceArchiveInputFd"
    const val KEY_WORKSPACE_ARCHIVE_OUTPUT_FD = "workspaceArchiveOutputFd"
    const val KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY = "workspaceRelativeWorkingDirectory"
    const val KEY_WORKSPACE_ARCHIVE_MAX_FILES = "workspaceArchiveMaxFiles"
    const val KEY_WORKSPACE_ARCHIVE_MAX_BYTES = "workspaceArchiveMaxBytes"

    const val KEY_ENV_NAMES = "envNames"
    const val KEY_ENV_VALUES = "envValues"
    const val KEY_MODULE_SOURCE_NAMES = "moduleSourceNames"
    const val KEY_MODULE_SOURCES = "moduleSources"
    const val KEY_RUNTIME_MODULE_SOURCE_NAMES = "runtimeModuleSourceNames"
    const val KEY_RUNTIME_MODULE_SOURCES = "runtimeModuleSources"

    /**
     * Marks a request whose TypeScript graph was compiled from a closed host
     * snapshot. In this mode the runtime maps .ts/.mts/.cts specifiers only to
     * matching JavaScript records already present in moduleSources.
     */
    const val KEY_TYPESCRIPT_PRECOMPILED_SNAPSHOT = "typeScriptPrecompiledSnapshot"
    const val KEY_TYPESCRIPT_PRECOMPILED_SOURCE_NAMES = "typeScriptPrecompiledSourceNames"

    const val KEY_ESM_ENABLED = "esmEnabled"
    const val KEY_DYNAMIC_IMPORT_ENABLED = "dynamicImportEnabled"
    const val KEY_RAW_NODE_NETWORK_MODULES_ENABLED = "rawNodeNetworkModulesEnabled"
    /** Debug builds only. Enables the localhost-only Node inspector facade for this execution. */
    const val KEY_INSPECTOR_ENABLED = "inspectorEnabled"
    const val KEY_WORKER_THREADS_ENABLED = "workerThreadsEnabled"
    const val KEY_CHILD_PROCESS_ENABLED = "childProcessEnabled"
    const val KEY_JAVA_INTEROP_ENABLED = "javaInteropEnabled"

    const val KEY_SUCCEEDED = "succeeded"
    const val KEY_EXIT_CODE = "exitCode"
    const val KEY_RESULT_TEXT = "resultText"
    const val KEY_STDOUT = "stdout"
    const val KEY_STDERR = "stderr"
    const val KEY_ERROR_NAME = "errorName"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_ERROR_STACK = "errorStack"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_ELAPSED_MS = "elapsedMs"
    const val KEY_PROCESS_NAME = "processName"
    const val KEY_PID = "pid"
    const val KEY_TIMED_OUT = "timedOut"
    const val KEY_NATIVE_PAYLOAD = "nativePayload"
    const val KEY_HOST_CAPABILITY_BROKER = "hostCapabilityBroker"
    const val KEY_HOST_CAPABILITY_BROKER_ID = "hostCapabilityBrokerId"
    const val KEY_HOST_CAPABILITY_BROKER_VERSION = "hostCapabilityBrokerVersion"
    const val KEY_HOST_CAPABILITY_MODULES = "hostCapabilityModules"
    const val KEY_BRIDGE_ENGINE_INFO = "bridgeEngineInfo"
    const val KEY_BRIDGE_REQUEST_JSON = "bridgeRequestJson"
    const val KEY_BRIDGE_RESPONSE_JSON = "bridgeResponseJson"
    const val KEY_BRIDGE_RESPONSE_OK = "bridgeResponseOk"
    const val KEY_BRIDGE_BINARY_PFD = "bridgeBinaryPfd"
    const val KEY_BRIDGE_BINARY_BYTE_COUNT = "bridgeBinaryByteCount"
    const val KEY_BRIDGE_ERROR_MESSAGE = "bridgeErrorMessage"

    const val KEY_MODULE_SOURCE_PROVIDER = "moduleSourceProvider"
    const val KEY_MODULE_SOURCE_PROVIDER_VERSION = "moduleSourceProviderVersion"
    const val KEY_MODULE_SOURCE_PROVIDER_OPERATION = "moduleSourceProviderOperation"
    const val KEY_MODULE_SOURCE_PROVIDER_DEADLINE_ELAPSED_REALTIME_MS =
        "moduleSourceProviderDeadlineElapsedRealtimeMs"
    const val KEY_MODULE_SOURCE_PROVIDER_ID = "moduleSourceProviderId"
    const val KEY_MODULE_SOURCE_PROVIDER_PATH = "moduleSourceProviderPath"
    const val KEY_MODULE_SOURCE_PROVIDER_REQUEST_ID = "moduleSourceProviderRequestId"
    const val KEY_MODULE_SOURCE_PROVIDER_STATUS = "moduleSourceProviderStatus"
    const val KEY_MODULE_SOURCE_PROVIDER_RESOLVED_PATH = "moduleSourceProviderResolvedPath"
    const val KEY_MODULE_SOURCE_PROVIDER_SOURCE_FD = "moduleSourceProviderSourceFd"
    const val KEY_MODULE_SOURCE_PROVIDER_SOURCE_BYTES = "moduleSourceProviderSourceBytes"
    const val KEY_MODULE_SOURCE_PROVIDER_INPUT_FD = "moduleSourceProviderInputFd"
    const val KEY_MODULE_SOURCE_PROVIDER_INPUT_BYTES = "moduleSourceProviderInputBytes"
    const val KEY_MODULE_SOURCE_PROVIDER_INPUT_SHA256 = "moduleSourceProviderInputSha256"
    const val KEY_MODULE_SOURCE_PROVIDER_ELAPSED_MS = "moduleSourceProviderElapsedMs"
    const val KEY_MODULE_SOURCE_PROVIDER_ROOT = "moduleSourceProviderRoot"
    const val KEY_MODULE_SOURCE_PROVIDER_DENIAL_REASON = "moduleSourceProviderDenialReason"
    const val KEY_MODULE_SOURCE_PROVIDER_SOURCE_MAX_BYTES = "moduleSourceProviderSourceMaxBytes"
    const val KEY_MODULE_SOURCE_PROVIDER_TOTAL_MAX_BYTES = "moduleSourceProviderTotalMaxBytes"
    const val KEY_MODULE_SOURCE_PROVIDER_MAX_COUNT = "moduleSourceProviderMaxCount"
    const val KEY_MODULE_SOURCE_PROVIDER_REQUEST_MAX_COUNT = "moduleSourceProviderRequestMaxCount"

    const val MODULE_SOURCE_PROVIDER_STATUS_DECRYPTED = "decrypted"
    const val MODULE_SOURCE_PROVIDER_STATUS_PLAINTEXT = "plaintext"
    const val MODULE_SOURCE_PROVIDER_STATUS_COMPILED_TYPESCRIPT = "compiled_typescript"
    const val MODULE_SOURCE_PROVIDER_STATUS_NOT_ENCRYPTED = "not_encrypted"
    const val MODULE_SOURCE_PROVIDER_STATUS_NOT_FOUND = "not_found"
    const val MODULE_SOURCE_PROVIDER_STATUS_DENIED = "denied"
    const val MODULE_SOURCE_PROVIDER_STATUS_CANCELLED = "cancelled"
    const val MODULE_SOURCE_PROVIDER_STATUS_TIMED_OUT = "timed_out"
    const val MODULE_SOURCE_PROVIDER_STATUS_FAILED = "failed"

    const val MODULE_SOURCE_PROVIDER_OPERATION_RESOLVE_EXISTING = "resolve_existing"
    const val MODULE_SOURCE_PROVIDER_OPERATION_MATERIALIZE_MISSING_PLAINTEXT =
        "materialize_missing_plaintext"
    const val MODULE_SOURCE_PROVIDER_OPERATION_COMPILE_MISSING_TYPESCRIPT =
        "compile_missing_typescript"

    const val ERROR_MODULE_SOURCE_PROVIDER_INVALID_REQUEST =
        "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_INVALID_REQUEST"
    const val ERROR_MODULE_SOURCE_PROVIDER_NOT_FOUND = "ERR_AUTOJS6_MODULE_NOT_FOUND"
    const val ERROR_MODULE_SOURCE_PROVIDER_DENIED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_DENIED"
    const val ERROR_MODULE_SOURCE_PROVIDER_CANCELLED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_CANCELLED"
    const val ERROR_MODULE_SOURCE_PROVIDER_TIMED_OUT = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_TIMEOUT"
    const val ERROR_MODULE_SOURCE_PROVIDER_BUDGET_EXCEEDED =
        "ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED"
    const val ERROR_MODULE_SOURCE_PROVIDER_FAILED = "ERR_AUTOJS6_MODULE_SOURCE_PROVIDER_FAILED"
    const val ERROR_TYPESCRIPT_COMPILER_REQUIRED = "ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED"
    const val ERROR_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND =
        "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND"
    const val ERROR_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS =
        "ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS"
    const val ERROR_TYPESCRIPT_COMPILATION_FAILED =
        "ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED"

    const val RUNTIME_MODULE_SOURCE_PROVIDER_NAME = "autojs6:module-source-provider"

    const val KEY_EVENT_TYPE = "eventType"
    const val KEY_EVENT_LEVEL = "level"
    const val KEY_EVENT_TEXT = "text"

    const val EVENT_STARTED = "started"
    const val EVENT_STDOUT = "stdout"
    const val EVENT_STDERR = "stderr"
    // text is "true" while stdin can accept another chunk, otherwise "false".
    const val EVENT_STDIN_STATE = "stdin_state"
    const val EVENT_FINISHED = "finished"

    const val CAPABILITY_SYNC_SCRIPT_EXECUTION = "syncScriptExecution"
    const val CAPABILITY_BUNDLE_TRANSPORT = "bundleTransport"
    const val CAPABILITY_NATIVE_EMBEDDED_RUNTIME = "nativeEmbeddedRuntime"
    const val CAPABILITY_HOST_CAPABILITY_BROKER = "hostCapabilityBroker"
    const val CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE = "hostCapabilityLiveBridge"
    const val CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER = "onDemandModuleSourceProvider"
    const val CAPABILITY_HOST_PLAINTEXT_MODULE_SOURCE_MATERIALIZATION =
        "hostPlaintextModuleSourceMaterialization"
    const val CAPABILITY_HOST_TYPESCRIPT_ON_DEMAND_COMPILATION =
        "hostTypeScriptOnDemandCompilation"
    const val CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT = "scopedWorkspaceArchiveTransport"
}
