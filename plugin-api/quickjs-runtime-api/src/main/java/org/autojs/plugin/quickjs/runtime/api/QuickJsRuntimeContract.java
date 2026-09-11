package org.autojs.plugin.quickjs.runtime.api;

public final class QuickJsRuntimeContract {
    public static final int PROTOCOL_VERSION = 1;
    public static final String SERVICE_ACTION = QuickJsPluginActions.RUNTIME;
    public static final String SERVICE_CATEGORY = QuickJsPluginActions.CATEGORY;
    public static final String PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN";
    public static final String BINDER_DESCRIPTOR =
            "org.autojs.plugin.quickjs.runtime.api.IQuickJsRuntimePlugin";

    public static final String RUNTIME_VERSION = "2026-06-04";
    public static final String RUNTIME_REVISION =
            "2026-06-04+3d5e064e9dd67c70f7962836505a7fa067bf0a4e";
    public static final long REQUIRES_HOST_VERSION = 5279L;

    public static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_OUTPUT_BYTES = 4L * 1024L * 1024L;
    public static final int MAX_CALLBACK_CHUNK_BYTES = 64 * 1024;
    public static final int MAX_TERMINAL_MESSAGE_BYTES = 16 * 1024;
    public static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;
    public static final long MAX_TIMEOUT_MILLIS = 10L * 60L * 1000L;
    public static final long DEFAULT_MEMORY_LIMIT_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_MEMORY_LIMIT_BYTES = 256L * 1024L * 1024L;
    public static final long DEFAULT_STACK_LIMIT_BYTES = 1L * 1024L * 1024L;
    public static final long MAX_STACK_LIMIT_BYTES = 8L * 1024L * 1024L;
    public static final int MAX_SOURCE_NAME_BYTES = 1_024;

    public static final String SOURCE_MODE_SCRIPT = "script";
    public static final String SOURCE_MODE_MODULE = "module";

    public static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    public static final String KEY_EXECUTION_ID = "executionId";
    public static final String KEY_SOURCE_NAME = "sourceName";
    public static final String KEY_SOURCE_MODE = "sourceMode";
    public static final String KEY_TIMEOUT_MILLIS = "timeoutMillis";
    public static final String KEY_MEMORY_LIMIT_BYTES = "memoryLimitBytes";
    public static final String KEY_STACK_LIMIT_BYTES = "stackLimitBytes";
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
    public static final String KEY_NATIVE_LIBRARY = "nativeLibrary";
    public static final String KEY_PROCESS_NAME = "processName";
    public static final String KEY_PROCESS_ABI = "processAbi";
    public static final String KEY_SUPPORTED_ABIS = "supportedAbis";
    public static final String KEY_MAX_SOURCE_BYTES = "maxSourceBytes";
    public static final String KEY_MAX_OUTPUT_BYTES = "maxOutputBytes";
    public static final String KEY_MAX_MEMORY_BYTES = "maxMemoryBytes";
    public static final String KEY_MAX_STACK_BYTES = "maxStackBytes";

    public static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String ERROR_SOURCE_TOO_LARGE = "SOURCE_TOO_LARGE";
    public static final String ERROR_BUSY = "BUSY";
    public static final String ERROR_RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";
    public static final String ERROR_CANCELLED = "CANCELLED";
    public static final String ERROR_TIMEOUT = "TIMEOUT";
    public static final String ERROR_OUTPUT_LIMIT = "OUTPUT_LIMIT";
    public static final String ERROR_EVALUATION = "EVALUATION_ERROR";
    public static final String ERROR_OUT_OF_MEMORY = "OUT_OF_MEMORY";
    public static final String ERROR_INTERNAL = "INTERNAL";

    private QuickJsRuntimeContract() {
    }
}
