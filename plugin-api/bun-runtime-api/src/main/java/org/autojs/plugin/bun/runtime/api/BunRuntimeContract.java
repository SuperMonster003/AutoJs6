package org.autojs.plugin.bun.runtime.api;

public final class BunRuntimeContract {
    public static final int PROTOCOL_VERSION = 1;
    public static final String SERVICE_ACTION = BunPluginActions.RUNTIME;
    public static final String SERVICE_CATEGORY = BunPluginActions.CATEGORY;
    public static final String PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN";
    public static final String BINDER_DESCRIPTOR = "org.autojs.plugin.bun.runtime.api.IBunRuntimePlugin";

    public static final String RUNTIME_VERSION = "1.4.0";
    public static final String RUNTIME_REVISION = "1.4.0+34cbb9a40";
    public static final long REQUIRES_HOST_VERSION = 5278L;

    public static final long MAX_SOURCE_BYTES = 16L * 1024L * 1024L;
    public static final long MAX_OUTPUT_BYTES = 8L * 1024L * 1024L;
    public static final int MAX_CALLBACK_CHUNK_BYTES = 64 * 1024;
    public static final int MAX_TERMINAL_MESSAGE_BYTES = 16 * 1024;
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;
    public static final long MAX_TIMEOUT_MILLIS = 10L * 60L * 1000L;
    public static final int MAX_SOURCE_NAME_BYTES = 1_024;
    public static final int MAX_ARGUMENT_COUNT = 256;
    public static final int MAX_ARGUMENT_BYTES = 64 * 1024;
    public static final int MAX_ENVIRONMENT_COUNT = 256;
    public static final int MAX_ENVIRONMENT_BYTES = 128 * 1024;

    /** Format version of the optional multi-file workspace archive; absent request key means a single source. */
    public static final int WORKSPACE_ARCHIVE_VERSION = 1;
    public static final int MAX_WORKSPACE_ENTRIES = 16_384;
    /** Uncompressed byte limit of one workspace archive; each file still respects MAX_SOURCE_BYTES. */
    public static final long MAX_WORKSPACE_BYTES = 64L * 1024L * 1024L;

    /**
     * Format version of the optional read-only host info snapshot (M7 capability bridge, first capability).
     * Absent request key means no snapshot; present means the plugin verifies the host package against the
     * Binder caller, resolves the host version itself, writes a bounded JSON file into the private run directory
     * and exposes its path to the script through {@link #HOST_INFO_ENVIRONMENT_VARIABLE}.
     */
    public static final int HOST_INFO_VERSION = 1;
    /** Upper bound of the rendered host info JSON file. */
    public static final int MAX_HOST_INFO_BYTES = 16 * 1024;
    /** Upper bound of each advisory host info string value. */
    public static final int MAX_HOST_INFO_VALUE_BYTES = 256;
    /** Environment variable that carries the absolute path of the host info JSON file inside the Bun process. */
    public static final String HOST_INFO_ENVIRONMENT_VARIABLE = "AUTOJS6_HOST_INFO_FILE";
    /** Environment variable names with this prefix are owned by the plugin; host requests carrying them are invalid. */
    public static final String RESERVED_ENVIRONMENT_PREFIX = "AUTOJS6_";

    /**
     * Version of the optional runtime capability bridge (M7 capability bridge, dynamic calls). Absent request
     * key means no bridge; present means the host hands over an {@code IBunHostCapabilityBroker} Binder and the
     * granted capability IDs, and the plugin serves them to the script over a private unix socket (HTTP/1.1)
     * whose path is exposed through {@link #HOST_BRIDGE_ENVIRONMENT_VARIABLE}.
     */
    public static final int HOST_CAPABILITY_BRIDGE_VERSION = 1;
    /** Environment variable that carries the absolute path of the bridge unix socket inside the Bun process. */
    public static final String HOST_BRIDGE_ENVIRONMENT_VARIABLE = "AUTOJS6_HOST_BRIDGE_SOCKET";
    /**
     * Upper bound of granted capability IDs in one request a plugin built with this contract accepts. The plugin
     * advertises it as {@link BunPluginCapabilityKeys#MAX_HOST_CAPABILITY_COUNT}, and the host offers no more than the
     * advertised bound (api-coverage Q170 (a)).
     */
    public static final int MAX_HOST_CAPABILITY_COUNT = 128;
    /** The bound of a plugin that advertises none: built with an earlier contract, its request parser rejects a longer list. */
    public static final int LEGACY_MAX_HOST_CAPABILITY_COUNT = 64;
    /** Upper bound of one capability ID; IDs match {@code [a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+} (at least one dot, so no ID collides with the bridge info path). */
    public static final int MAX_HOST_CAPABILITY_ID_BYTES = 64;
    /** Upper bound of one call request JSON document (HTTP body and broker request alike). */
    public static final int MAX_HOST_CALL_REQUEST_BYTES = 64 * 1024;
    /** Upper bound of one call result JSON document. */
    public static final int MAX_HOST_CALL_RESULT_BYTES = 256 * 1024;
    /** Upper bound of calls (including failed ones) relayed during one execution. */
    public static final int MAX_HOST_CALLS_PER_EXECUTION = 1024;
    /** Upper bound of calls in flight at the same time; further calls fail fast. */
    public static final int MAX_CONCURRENT_HOST_CALLS = 4;
    /** Time the plugin waits for the broker callback of one call. */
    public static final long HOST_CALL_TIMEOUT_MILLIS = 10_000L;

    public static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    public static final String KEY_EXECUTION_ID = "executionId";
    public static final String KEY_SOURCE_NAME = "sourceName";
    public static final String KEY_ARGUMENTS = "arguments";
    public static final String KEY_ENVIRONMENT = "environment";
    public static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    public static final String KEY_OUTPUT_BYTE_LIMIT = "outputByteLimit";
    public static final String KEY_WORKSPACE_ARCHIVE_VERSION = "workspaceArchiveVersion";
    public static final String KEY_WORKSPACE_ENTRY_POINT = "workspaceEntryPoint";
    public static final String KEY_WORKSPACE_MAX_ENTRIES = "workspaceMaxEntries";
    public static final String KEY_WORKSPACE_MAX_BYTES = "workspaceMaxBytes";
    public static final String KEY_HOST_INFO_VERSION = "hostInfoVersion";
    public static final String KEY_HOST_INFO = "hostInfo";
    public static final String KEY_HOST_CAPABILITY_BRIDGE_VERSION = "hostCapabilityBridgeVersion";
    /** IBinder request value implementing {@code IBunHostCapabilityBroker}; scoped to this execution. */
    public static final String KEY_HOST_CAPABILITY_BROKER = "hostCapabilityBroker";
    /** String list request value: the capability IDs granted for this execution (1..MAX_HOST_CAPABILITY_COUNT). */
    public static final String KEY_HOST_CAPABILITIES = "hostCapabilities";

    /** Keys inside the {@link #KEY_HOST_INFO} Bundle; the package name is mandatory and verified, the rest is advisory. */
    public static final String HOST_INFO_KEY_PACKAGE_NAME = "packageName";
    public static final String HOST_INFO_KEY_VERSION_DATE = "versionDate";
    public static final String HOST_INFO_KEY_LANGUAGE_TAG = "languageTag";

    /** Keys of the broker call request and result Bundles; results also carry executionId, succeeded, errorCode and errorMessage. */
    public static final String KEY_HOST_CALL_ID = "hostCallId";
    public static final String KEY_HOST_CAPABILITY = "hostCapability";
    public static final String KEY_HOST_CALL_REQUEST_JSON = "hostCallRequestJson";
    public static final String KEY_HOST_CALL_RESULT_JSON = "hostCallResultJson";

    /** Capability IDs of the first dynamic host capabilities. */
    public static final String HOST_CAPABILITY_UI_TOAST = "ui.toast";
    public static final String HOST_CAPABILITY_DEVICE_INFO = "device.info";

    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_TEXT = "text";
    public static final String KEY_SEQUENCE = "sequence";
    public static final String EVENT_STARTED = "started";
    public static final String EVENT_STDOUT = "stdout";
    public static final String EVENT_STDERR = "stderr";
    public static final String EVENT_FINISHED = "finished";

    public static final String KEY_SUCCEEDED = "succeeded";
    public static final String KEY_EXIT_CODE = "exitCode";
    public static final String KEY_STDOUT = "stdout";
    public static final String KEY_STDERR = "stderr";
    public static final String KEY_ERROR_CODE = "errorCode";
    public static final String KEY_ERROR_MESSAGE = "errorMessage";
    public static final String KEY_DURATION_MILLIS = "durationMillis";
    public static final String KEY_CANCELLED = "cancelled";
    public static final String KEY_TIMED_OUT = "timedOut";
    public static final String KEY_WORKSPACE_FILE_COUNT = "workspaceFileCount";
    public static final String KEY_WORKSPACE_BYTES = "workspaceBytes";
    public static final String KEY_HOST_INFO_DELIVERED = "hostInfoDelivered";
    public static final String KEY_HOST_BRIDGE_DELIVERED = "hostBridgeDelivered";
    public static final String KEY_HOST_CALLS = "hostCalls";

    public static final String KEY_RUNTIME_READY = "runtimeReady";
    public static final String KEY_RUNTIME_VERSION = "runtimeVersion";
    public static final String KEY_RUNTIME_REVISION = "runtimeRevision";
    public static final String KEY_RUNTIME_PATH = "runtimePath";
    public static final String KEY_PROCESS_NAME = "processName";
    public static final String KEY_PROCESS_ABI = "processAbi";
    public static final String KEY_SUPPORTED_ABIS = "supportedAbis";
    public static final String KEY_MAX_SOURCE_BYTES = "maxSourceBytes";
    public static final String KEY_MAX_OUTPUT_BYTES = "maxOutputBytes";

    public static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String ERROR_SOURCE_TOO_LARGE = "SOURCE_TOO_LARGE";
    public static final String ERROR_BUSY = "BUSY";
    public static final String ERROR_RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";
    public static final String ERROR_SPAWN_FAILED = "SPAWN_FAILED";
    public static final String ERROR_CANCELLED = "CANCELLED";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_OUTPUT_LIMIT = "OUTPUT_LIMIT";
    public static final String ERROR_NON_ZERO_EXIT = "NON_ZERO_EXIT";
    public static final String ERROR_INTERNAL = "INTERNAL";

    /** Bridge-level call error codes shared by the HTTP reply and the broker result; not runScript error codes. */
    public static final String HOST_CALL_ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String HOST_CALL_ERROR_NOT_GRANTED = "NOT_GRANTED";
    public static final String HOST_CALL_ERROR_UNKNOWN_CAPABILITY = "UNKNOWN_CAPABILITY";
    public static final String HOST_CALL_ERROR_PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String HOST_CALL_ERROR_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String HOST_CALL_ERROR_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String HOST_CALL_ERROR_TIMEOUT = "TIMEOUT";
    public static final String HOST_CALL_ERROR_HOST_UNAVAILABLE = "HOST_UNAVAILABLE";
    public static final String HOST_CALL_ERROR_INTERNAL = "INTERNAL";

    private BunRuntimeContract() {
    }
}
