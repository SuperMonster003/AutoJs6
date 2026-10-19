package org.autojs.plugin.three.stove.agent.api

import org.autojs.plugin.host.capability.api.HostCapabilityContract

/**
 * 3-Stove Agent control and model data plane, version 2 (the renamed contract line;
 * version 1 belonged to the retired AI Agent identifiers).
 *
 * Every Bundle includes KEY_CONTRACT_VERSION and is a non-null immutable snapshot. JSON strings
 * use UTF-8 byte ceilings. Missing optional JSON fields take their documented defaults; unknown
 * Bundle keys are ignored. Bridge, grant and reason keys belong to HostCapabilityContract.
 *
 * Binder calls may arrive concurrently. Non-oneway methods only validate/enqueue and return
 * within 200 ms; expensive work completes through oneway callbacks. Sequence numbers increase
 * per run/model request, duplicate/out-of-order events are ignored, and terminal events occur once.
 *
 * For a large control/model JSON value, omit its inline key and supply KEY_PAYLOAD_FD instead.
 * A model generation request sent by descriptor also includes KEY_MODEL_REF_JSON with requestId,
 * so cancellation can identify it before reading finishes. The descriptor body's requestId must
 * match that header; mismatches are INVALID_REQUEST and never reach a Provider.
 * The receiving operation determines the JSON shape. Senders own/close their local descriptor;
 * receivers own/close their transferred descriptor, including rejected or unread descriptors.
 * Descriptors cannot bypass an operation-specific byte ceiling. Callbacks must enqueue accepted
 * reads promptly and close rejected descriptors before returning.
 */
object ThreeStoveAgentContract {
    const val CONTRACT_VERSION = 2
    const val MIN_SUPPORTED_CONTRACT_VERSION = 2
    const val MAX_SUPPORTED_CONTRACT_VERSION = CONTRACT_VERSION
    const val MIN_CONTRACT_VERSION = MIN_SUPPORTED_CONTRACT_VERSION
    const val MAX_CONTRACT_VERSION = MAX_SUPPORTED_CONTRACT_VERSION

    @JvmStatic
    fun supportsContractVersion(version: Int): Boolean =
        version in MIN_SUPPORTED_CONTRACT_VERSION..MAX_SUPPORTED_CONTRACT_VERSION

    /** Required Int: negotiated control-plane version on every Bundle. */
    const val KEY_CONTRACT_VERSION = "contractVersion"
    /** String: attach/updateConfig JSON {hostLabel?, locale, scriptRoots, grantSummary}. */
    const val KEY_LINK_CONFIG_JSON = "linkConfigJson"
    /** String: link snapshot {state, attachedAt, runningRunId?, queuedCount, pluginVersion, lastErrorCode?, lastError?}. */
    const val KEY_STATUS_JSON = "statusJson"
    /** String: startRun goal/options/origin or a listRuns/listPresets query. */
    const val KEY_RUN_REQUEST_JSON = "runRequestJson"
    /** String: bounded run/link event. Run events include runId, type and sequence. */
    const val KEY_RUN_EVENT_JSON = "runEventJson"
    /** String: interaction reply {runId, requestId, value|allowed, scope}, acknowledgement or query result. */
    const val KEY_RUN_RESPONSE_JSON = "runResponseJson"
    /** String: reference {runId, reason?} for lookup/cancel. */
    const val KEY_RUN_REF_JSON = "runRefJson"
    /** String: requestId, targetId, messages, structuredJson, schema/output/temperature controls, stream and timeoutMs. */
    const val KEY_MODEL_REQUEST_JSON = "modelRequestJson"
    /** String: model event {requestId, type, sequence, ...}; terminal event exactly once. */
    const val KEY_MODEL_EVENT_JSON = "modelEventJson"
    /**
     * String: reference {requestId, reason?} for cancellation. A descriptor model request must
     * also carry this key as a correlation header (at most 512 UTF-8 bytes) whose requestId
     * equals the body requestId. Request ids are nonempty ASCII letters/digits plus ._:-,
     * begin with a letter/digit, and contain at most 128 characters.
     */
    const val KEY_MODEL_REF_JSON = "modelRefJson"
    /** String: broker identity/capabilities/limits; model credentials are never included. */
    const val KEY_MODEL_BROKER_INFO_JSON = "modelBrokerInfoJson"
    /** Optional ParcelFileDescriptor: exclusive alternative to the operation's inline JSON key. */
    const val KEY_PAYLOAD_FD = "payloadFd"
    /** Separately negotiated image input descriptors, indexed by model imageRefs. */
    const val KEY_MODEL_IMAGE_FDS = "modelImageFds"
    /** Optional String: one of ERROR_CODES for a synchronous failure. */
    const val KEY_ERROR_CODE = "errorCode"
    /** Optional String: human-readable failure detail, at most MAX_ERROR_MESSAGE_BYTES. */
    const val KEY_ERROR_MESSAGE = "errorMessage"

    const val LINK_STATE_DETACHED = "detached"
    const val LINK_STATE_ATTACHING = "attaching"
    const val LINK_STATE_ATTACHED = "attached"
    const val LINK_STATE_HOST_UNAVAILABLE = "host_unavailable"
    const val LINK_STATE_FAILED = "failed"

    @JvmField
    val LINK_STATES: Set<String> = linkedSetOf(LINK_STATE_DETACHED, LINK_STATE_ATTACHING, LINK_STATE_ATTACHED, LINK_STATE_HOST_UNAVAILABLE, LINK_STATE_FAILED)

    const val RUN_STATE_QUEUED = "queued"
    const val RUN_STATE_RUNNING = "running"
    const val RUN_STATE_WAITING_INPUT = "waiting_input"
    const val RUN_STATE_WAITING_CONFIRMATION = "waiting_confirmation"
    const val RUN_STATE_CANCELLING = "cancelling"
    const val RUN_STATE_COMPLETED = "completed"
    const val RUN_STATE_PARTIAL = "partial"
    const val RUN_STATE_FAILED = "failed"
    const val RUN_STATE_BLOCKED = "blocked"
    const val RUN_STATE_CANCELLED = "cancelled"

    @JvmField
    val RUN_STATES: Set<String> = linkedSetOf(
        RUN_STATE_QUEUED,
        RUN_STATE_RUNNING,
        RUN_STATE_WAITING_INPUT,
        RUN_STATE_WAITING_CONFIRMATION,
        RUN_STATE_CANCELLING,
        RUN_STATE_COMPLETED,
        RUN_STATE_PARTIAL,
        RUN_STATE_FAILED,
        RUN_STATE_BLOCKED,
        RUN_STATE_CANCELLED,
    )
    @JvmField
    val TERMINAL_RUN_STATES: Set<String> = linkedSetOf(
        RUN_STATE_COMPLETED, RUN_STATE_PARTIAL, RUN_STATE_FAILED, RUN_STATE_BLOCKED, RUN_STATE_CANCELLED,
    )

    @JvmStatic
    fun isTerminalRunState(state: String?): Boolean = state != null && state in TERMINAL_RUN_STATES

    const val RUN_EVENT_STATE = "state"
    const val RUN_EVENT_PROGRESS = "progress"
    const val RUN_EVENT_STEP = "step"
    const val RUN_EVENT_INPUT = "input"
    const val RUN_EVENT_CONFIRMATION = "confirmation"
    const val RUN_EVENT_DONE = "done"
    const val RUN_EVENT_ERROR = "error"
    const val LINK_EVENT_QUEUE_CHANGED = "queue_changed"
    const val LINK_EVENT_PRESET_CHANGED = "preset_changed"

    const val MODEL_EVENT_STARTED = "started"
    const val MODEL_EVENT_CHUNK = "chunk"
    const val MODEL_EVENT_USAGE = "usage"
    const val MODEL_EVENT_TOOL_CALLS = "tool_calls"
    /** Optional broker extension; contract V2 and the original five transactions stay compatible. */
    const val MODEL_VISION_VERSION = 1
    const val MODEL_INFO_VISION_VERSION = "visionVersion"
    const val MAX_MODEL_IMAGES = 4
    const val MAX_MODEL_IMAGE_BYTES = 4L * 1024 * 1024
    const val MAX_MODEL_TOTAL_IMAGE_BYTES = 8L * 1024 * 1024
    const val MODEL_TOOL_CALLING_VERSION = 1
    const val MODEL_INFO_TOOL_CALLING_VERSION = "toolCallingVersion"
    const val MAX_MODEL_TOOL_ROUNDS = 16
    const val MAX_MODEL_TOOL_RESULT_BYTES = 64 * 1024
    const val MAX_MODEL_TOOL_RESULTS_BYTES = 128 * 1024
    const val MODEL_EVENT_COMPLETED = "completed"
    const val MODEL_EVENT_FAILED = "failed"
    const val MODEL_EVENT_CANCELLED = "cancelled"

    @JvmField
    val MODEL_TERMINAL_EVENTS: Set<String> = linkedSetOf(
        MODEL_EVENT_COMPLETED, MODEL_EVENT_FAILED, MODEL_EVENT_CANCELLED,
    )

    const val ERROR_PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val ERROR_LINK_DETACHED = "LINK_DETACHED"
    const val ERROR_HOST_UNAVAILABLE = "HOST_UNAVAILABLE"
    const val ERROR_QUEUE_FULL = "QUEUE_FULL"
    const val ERROR_RUN_NOT_FOUND = "RUN_NOT_FOUND"
    const val ERROR_RUN_NOT_INTERACTIVE = "RUN_NOT_INTERACTIVE"
    const val ERROR_TOOL_UNKNOWN = "TOOL_UNKNOWN"
    const val ERROR_TOOL_DISABLED = "TOOL_DISABLED"
    const val ERROR_TOOL_ARGUMENTS_INVALID = "TOOL_ARGUMENTS_INVALID"
    const val ERROR_TOOL_FAILED = "TOOL_FAILED"
    const val ERROR_CAPABILITY_DENIED = "CAPABILITY_DENIED"
    const val ERROR_QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
    const val ERROR_RATE_LIMITED = "RATE_LIMITED"
    const val ERROR_LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
    const val ERROR_TARGET_UNSUPPORTED = "TARGET_UNSUPPORTED"
    const val ERROR_TARGET_UNAVAILABLE = "TARGET_UNAVAILABLE"
    const val ERROR_MODEL_FAILED = "MODEL_FAILED"
    const val ERROR_MODEL_TIMEOUT = "MODEL_TIMEOUT"
    const val ERROR_DECISION_UNPARSABLE = "DECISION_UNPARSABLE"
    const val ERROR_A11Y_SERVICE_NOT_RUNNING = "A11Y_SERVICE_NOT_RUNNING"
    const val ERROR_NODE_REF_STALE = "NODE_REF_STALE"
    const val ERROR_NODE_NOT_FOUND = "NODE_NOT_FOUND"
    const val ERROR_SCREEN_LOCKED = "SCREEN_LOCKED"
    const val ERROR_SCRIPT_NOT_REGISTERED = "SCRIPT_NOT_REGISTERED"
    const val ERROR_SCRIPT_TIMEOUT = "SCRIPT_TIMEOUT"
    const val ERROR_SCRIPT_FAILED = "SCRIPT_FAILED"
    const val ERROR_OCR_PLUGIN_REQUIRED = "OCR_PLUGIN_REQUIRED"
    const val ERROR_USER_DENIED = "USER_DENIED"
    const val ERROR_USER_TIMEOUT = "USER_TIMEOUT"
    const val ERROR_BUDGET_EXCEEDED = "BUDGET_EXCEEDED"
    const val ERROR_CANCELLED = "CANCELLED"
    const val ERROR_INVALID_REQUEST = "INVALID_REQUEST"
    const val ERROR_UNSUPPORTED_CONTRACT = "UNSUPPORTED_CONTRACT"

    @JvmField
    val ERROR_CODES: Set<String> = linkedSetOf(
        ERROR_PLUGIN_UNAVAILABLE,
        ERROR_LINK_DETACHED,
        ERROR_HOST_UNAVAILABLE,
        ERROR_QUEUE_FULL,
        ERROR_RUN_NOT_FOUND,
        ERROR_RUN_NOT_INTERACTIVE,
        ERROR_TOOL_UNKNOWN,
        ERROR_TOOL_DISABLED,
        ERROR_TOOL_ARGUMENTS_INVALID,
        ERROR_TOOL_FAILED,
        ERROR_CAPABILITY_DENIED,
        ERROR_QUOTA_EXCEEDED,
        ERROR_RATE_LIMITED,
        ERROR_LIMIT_EXCEEDED,
        ERROR_TARGET_UNSUPPORTED,
        ERROR_TARGET_UNAVAILABLE,
        ERROR_MODEL_FAILED,
        ERROR_MODEL_TIMEOUT,
        ERROR_DECISION_UNPARSABLE,
        ERROR_A11Y_SERVICE_NOT_RUNNING,
        ERROR_NODE_REF_STALE,
        ERROR_NODE_NOT_FOUND,
        ERROR_SCREEN_LOCKED,
        ERROR_SCRIPT_NOT_REGISTERED,
        ERROR_SCRIPT_TIMEOUT,
        ERROR_SCRIPT_FAILED,
        ERROR_OCR_PLUGIN_REQUIRED,
        ERROR_USER_DENIED,
        ERROR_USER_TIMEOUT,
        ERROR_BUDGET_EXCEEDED,
        ERROR_CANCELLED,
        ERROR_INVALID_REQUEST,
        ERROR_UNSUPPORTED_CONTRACT,
    )

    /** Observation code after explicit reattachment; never resumes a blocked task automatically. */
    const val REASON_HOST_REATTACHED = "HOST_REATTACHED"
    const val REASON_SCRIPT_STOPPED = "script-stopped"

    const val MAX_GOAL_BYTES = 4 * 1024
    const val MAX_CONTEXT_BYTES = 8 * 1024
    const val MAX_EVENT_JSON_BYTES = 32 * 1024
    const val MAX_RUN_QUEUE = 8
    const val MAX_CONCURRENT_RUNS_PER_LINK = 1
    const val MAX_STEPS = 200
    const val MAX_MODEL_CALLS = 300
    const val MAX_DURATION_MS = 30 * 60_000L
    const val MAX_DETACHED_DURATION_MS = 60 * 60_000L
    const val MAX_MODEL_REQUEST_INLINE_BYTES = 128 * 1024
    const val MAX_MODEL_REQUEST_PAYLOAD_BYTES = 2 * 1024 * 1024
    const val MAX_MODEL_OUTPUT_BYTES = 64 * 1024
    const val MAX_RESPONSE_SCHEMA_BYTES = 16 * 1024
    const val DEFAULT_MODEL_CALLS_PER_MINUTE = 30
    const val DEFAULT_MAX_TOTAL_TOKENS_PER_LINK = 1_000_000L
    const val MAX_BRIDGE_INLINE_JSON_BYTES = HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES
    const val MAX_BRIDGE_PAYLOAD_BYTES = HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES
    const val MAX_CONCURRENT_TOOL_CALLS = 2
    const val DEFAULT_TOOL_TIMEOUT_MS = HostCapabilityContract.DEFAULT_TOOL_TIMEOUT_MS
    const val MAX_TOOL_TIMEOUT_MS = HostCapabilityContract.MAX_TOOL_TIMEOUT_MS
    const val MAX_DUMP_NODES = HostCapabilityContract.MAX_DUMP_NODES
    const val MAX_DUMP_DEPTH = HostCapabilityContract.MAX_DUMP_DEPTH
    const val MAX_DUMP_TEXT_BYTES = HostCapabilityContract.MAX_DUMP_TEXT_BYTES
    const val MAX_SNAPSHOTS_PER_LINK = 8
    const val MAX_SCREEN_TEXT_ITEMS = 400
    const val MAX_SCREEN_TEXT_BYTES = 64 * 1024
    const val MAX_SCRIPT_CATALOG_ENTRIES = 500
    const val MAX_SCRIPT_CATALOG_BYTES = 256 * 1024
    const val MAX_SCRIPT_SCAN_DEPTH = 4
    const val MAX_MANIFEST_HEADER_BYTES = 8 * 1024
    const val MAX_SCRIPT_RESULT_BYTES = 64 * 1024
    const val MAX_SCRIPT_ARGUMENTS_BYTES = 16 * 1024
    const val MAX_RUN_STEPS_RECORDED = 200
    const val MAX_RUN_JOURNAL_BYTES = 1024 * 1024
    const val MAX_ERROR_MESSAGE_BYTES = HostCapabilityContract.MAX_ERROR_MESSAGE_BYTES
}
