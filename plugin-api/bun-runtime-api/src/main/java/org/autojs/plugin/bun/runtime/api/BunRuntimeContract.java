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

    public static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    public static final String KEY_EXECUTION_ID = "executionId";
    public static final String KEY_SOURCE_NAME = "sourceName";
    public static final String KEY_ARGUMENTS = "arguments";
    public static final String KEY_ENVIRONMENT = "environment";
    public static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    public static final String KEY_OUTPUT_BYTE_LIMIT = "outputByteLimit";

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

    private BunRuntimeContract() {
    }
}
