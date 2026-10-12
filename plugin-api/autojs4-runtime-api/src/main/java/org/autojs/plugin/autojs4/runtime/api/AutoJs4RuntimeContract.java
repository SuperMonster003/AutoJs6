package org.autojs.plugin.autojs4.runtime.api;

/**
 * Shared constants of the AutoJs4 Runtime plugin protocol V1.
 * <p>
 * See {@code docs/dev/autojs4-runtime-protocol-v1.md} in the AutoJs6 repository.
 * <p>
 * zh-CN: AutoJs4 Runtime 插件协议 V1 的共享常量, 契约文档见 AutoJs6 仓库的
 * {@code docs/dev/autojs4-runtime-protocol-v1.md}.
 */
public final class AutoJs4RuntimeContract {
    public static final int PROTOCOL_VERSION = 1;
    public static final String SERVICE_ACTION = AutoJs4PluginActions.RUNTIME;
    public static final String SERVICE_CATEGORY = AutoJs4PluginActions.CATEGORY;
    public static final String PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN";
    public static final String BINDER_DESCRIPTOR =
            "org.autojs.plugin.autojs4.runtime.api.IAutoJs4RuntimePlugin";

    /** The first AutoJs6 versionCode which registers the {@code autojs4} engine channel. */
    public static final long REQUIRES_HOST_VERSION = 5334L;

    public static final String RHINO_VERSION = "1.7.7.2";
    public static final String SOURCE_TRANSPORT_FILE_PATH = "file-path";
    public static final String EXECUTION_MODEL_ISOLATED_PROCESS_CONCURRENT = "isolated-process-concurrent";

    public static final int MAX_PATH_BYTES = 4_096;
    public static final int MAX_ARGUMENTS = 64;
    public static final int MAX_ARGUMENT_KEY_BYTES = 256;
    public static final int MAX_ARGUMENT_VALUE_BYTES = 64 * 1024;
    public static final int MAX_LOG_CHUNK_BYTES = 64 * 1024;
    public static final int MAX_TERMINAL_MESSAGE_BYTES = 16 * 1024;
    public static final int MAX_STACK_TRACE_BYTES = 64 * 1024;
    public static final int MAX_CONCURRENT_EXECUTIONS = 16;
    public static final int MAX_EXECUTION_ID_LENGTH = 128;
    public static final long ACCEPT_TIMEOUT_MILLIS = 15_000L;
    public static final long CANCEL_CONFIRM_TIMEOUT_MILLIS = 10_000L;
    public static final long PENDING_CANCELLATION_TTL_MILLIS = 60_000L;
    public static final int DEFAULT_LOOP_TIMES = 1;

    public static final String SOURCE_TYPE_JAVASCRIPT = "javascript";
    public static final String SOURCE_TYPE_AUTO = "auto";

    public static final String KEY_PROTOCOL_VERSION = "protocolVersion";
    public static final String KEY_EXECUTION_ID = "executionId";
    public static final String KEY_SOURCE_TYPE = "sourceType";
    public static final String KEY_SCRIPT_PATH = "scriptPath";
    public static final String KEY_WORKING_DIRECTORY = "workingDirectory";
    public static final String KEY_PROJECT_DIRECTORY = "projectDirectory";
    public static final String KEY_ARGUMENTS = "arguments";
    public static final String KEY_DELAY = "delay";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_LOOP_TIMES = "loopTimes";

    public static final String KEY_ACCEPTED = "accepted";
    public static final String KEY_UI_MODE = "uiMode";
    public static final String KEY_ERROR_CODE = "errorCode";
    public static final String KEY_ERROR_MESSAGE = "errorMessage";
    public static final String KEY_ERROR_STACK_TRACE = "errorStackTrace";
    public static final String KEY_PERMISSION = "permission";

    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_SEQUENCE = "sequence";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_LEVEL = "level";
    public static final String KEY_TEXT = "text";
    public static final String KEY_SUCCEEDED = "succeeded";
    public static final String KEY_CANCELLED = "cancelled";
    public static final String KEY_DURATION_MILLIS = "durationMillis";
    public static final String KEY_RESULT = "result";

    public static final String EVENT_STARTED = "started";
    public static final String EVENT_LOG = "log";
    public static final String EVENT_FINISHED = "finished";

    public static final String KEY_RUNTIME_READY = "runtimeReady";
    public static final String KEY_PROCESS_NAME = "processName";
    public static final String KEY_PROCESS_ABI = "processAbi";
    public static final String KEY_SUPPORTED_ABIS = "supportedAbis";
    public static final String KEY_AUTOJS4_VERSION_NAME = "autojs4VersionName";
    public static final String KEY_AUTOJS4_VERSION_CODE = "autojs4VersionCode";
    public static final String KEY_AUTOJS4_COMMIT = "autojs4Commit";
    public static final String KEY_RHINO_VERSION = "rhinoVersion";
    public static final String KEY_STORAGE_PERMISSION_GRANTED = "storagePermissionGranted";
    public static final String KEY_OVERLAY_PERMISSION_GRANTED = "overlayPermissionGranted";
    public static final String KEY_ACCESSIBILITY_SERVICE_ENABLED = "accessibilityServiceEnabled";
    public static final String KEY_ACTIVE_EXECUTIONS = "activeExecutions";
    public static final String KEY_MAX_CONCURRENT_EXECUTIONS = "maxConcurrentExecutions";

    public static final String PERMISSION_STORAGE = "storage";
    public static final String PERMISSION_OVERLAY = "overlay";
    public static final String PERMISSION_ACCESSIBILITY = "accessibility";

    public static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ERROR_FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String ERROR_PROJECT_INVALID = "PROJECT_INVALID";
    public static final String ERROR_BUSY = "BUSY";
    public static final String ERROR_RUNTIME_UNAVAILABLE = "RUNTIME_UNAVAILABLE";
    public static final String ERROR_CANCELLED = "CANCELLED";
    public static final String ERROR_SCRIPT_EXCEPTION = "SCRIPT_EXCEPTION";
    public static final String ERROR_INTERNAL = "INTERNAL";

    private AutoJs4RuntimeContract() {
    }
}
