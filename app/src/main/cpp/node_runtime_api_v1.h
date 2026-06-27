#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define AUTOJS_NODE_RUNTIME_API_ABI_VERSION 1u
#define AUTOJS_NODE_RUNTIME_FEATURE_SCRIPT (1ull << 0)
#define AUTOJS_NODE_RUNTIME_FEATURE_COMMONJS (1ull << 1)
#define AUTOJS_NODE_RUNTIME_FEATURE_ESM (1ull << 2)
#define AUTOJS_NODE_RUNTIME_FEATURE_MODULE_SOURCES (1ull << 3)
#define AUTOJS_NODE_RUNTIME_FEATURE_ENCRYPTED_MODULE_SOURCES (1ull << 4)
#define AUTOJS_NODE_RUNTIME_FEATURE_PACKAGED_DESCRIPTOR (1ull << 5)
#define AUTOJS_NODE_RUNTIME_FEATURE_POLL (1ull << 6)
#define AUTOJS_NODE_RUNTIME_FEATURE_STOP (1ull << 7)
#define AUTOJS_NODE_RUNTIME_FEATURE_DIAGNOSTICS (1ull << 8)

#define AUTOJS_NODE_EXECUTION_FLAG_ESM_EXPERIMENTAL (1u << 0)
#define AUTOJS_NODE_EXECUTION_FLAG_DYNAMIC_IMPORT_EXPERIMENTAL (1u << 1)
#define AUTOJS_NODE_EXECUTION_FLAG_WORKER_THREADS_EXPERIMENTAL (1u << 2)
#define AUTOJS_NODE_EXECUTION_FLAG_JAVA_INTEROP_EXPERIMENTAL (1u << 3)
#define AUTOJS_NODE_EXECUTION_FLAG_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL (1u << 4)
#define AUTOJS_NODE_EXECUTION_FLAG_CHILD_PROCESS_EXPERIMENTAL (1u << 5)

typedef struct AutoJsNodeHandle AutoJsNodeHandle;

typedef enum AutoJsNodeResultCode {
    AUTOJS_NODE_RESULT_OK = 0,
    AUTOJS_NODE_RESULT_INVALID_ARGUMENT = 1,
    AUTOJS_NODE_RESULT_UNAVAILABLE = 2,
    AUTOJS_NODE_RESULT_INTERRUPTED = 3,
    AUTOJS_NODE_RESULT_FAILED = 4
} AutoJsNodeResultCode;

typedef enum AutoJsNodeStopReason {
    AUTOJS_NODE_STOP_REASON_USER = 1,
    AUTOJS_NODE_STOP_REASON_TIMEOUT = 2,
    AUTOJS_NODE_STOP_REASON_HOST_DESTROY = 3,
    AUTOJS_NODE_STOP_REASON_CRASH_RECOVERY = 4
} AutoJsNodeStopReason;

typedef enum AutoJsNodeRuntimeStateCode {
    AUTOJS_NODE_RUNTIME_STATE_UNKNOWN = 0,
    AUTOJS_NODE_RUNTIME_STATE_CREATED = 1,
    AUTOJS_NODE_RUNTIME_STATE_INITIALIZING = 2,
    AUTOJS_NODE_RUNTIME_STATE_IDLE = 3,
    AUTOJS_NODE_RUNTIME_STATE_RUNNING = 4,
    AUTOJS_NODE_RUNTIME_STATE_DRAINING = 5,
    AUTOJS_NODE_RUNTIME_STATE_STOPPING = 6,
    AUTOJS_NODE_RUNTIME_STATE_STOPPED = 7,
    AUTOJS_NODE_RUNTIME_STATE_FAILED = 8,
    AUTOJS_NODE_RUNTIME_STATE_DESTROYED = 9
} AutoJsNodeRuntimeStateCode;

typedef struct AutoJsNodeBufferView {
    const char* data;
    uint64_t byte_length;
} AutoJsNodeBufferView;

typedef struct AutoJsNodeCreateOptions {
    uint32_t struct_size;
    uint32_t flags;
    const char* runtime_descriptor_json;
    const char* working_directory;
    const char* initial_environment_json;
    uint64_t timeout_ms;
} AutoJsNodeCreateOptions;

typedef struct AutoJsNodeExecutionRequest {
    uint32_t struct_size;
    uint32_t flags;
    const char* source_name;
    AutoJsNodeBufferView source;
    const char* working_directory;
    const char* module_sources_json;
    const char* environment_json;
    uint64_t timeout_ms;
    uint64_t execution_id;
} AutoJsNodeExecutionRequest;

typedef struct AutoJsNodePollOptions {
    uint32_t struct_size;
    uint32_t flags;
    uint64_t timeout_ms;
} AutoJsNodePollOptions;

typedef struct AutoJsNodeExecutionResult {
    uint32_t struct_size;
    int32_t exit_code;
    int32_t result_code;
    const char* stdout_text;
    const char* stderr_text;
    const char* result_json;
    const char* diagnostics_json;
} AutoJsNodeExecutionResult;

typedef struct AutoJsNodeRuntimeState {
    uint32_t struct_size;
    uint32_t state_code;
    uint64_t execution_id;
    const char* state_json;
} AutoJsNodeRuntimeState;

typedef struct AutoJsNodeDiagnostics {
    uint32_t struct_size;
    uint32_t abi_version;
    const char* runtime_descriptor_json;
    const char* state_json;
    const char* resource_json;
    const char* last_error_json;
} AutoJsNodeDiagnostics;

typedef void (*AutoJsNodeOutputCallback)(AutoJsNodeHandle*, const char*, uint64_t, void*);
typedef void (*AutoJsNodeBridgeRequestCallback)(AutoJsNodeHandle*, const char*, uint64_t, void*);

typedef struct AutoJsNodeRuntimeApiV1 {
    uint32_t struct_size;
    uint32_t abi_version;
    uint64_t feature_bits;
    const char* (*get_node_version)(void);
    const char* (*get_runtime_descriptor)(void);
    int32_t (*create_runtime)(const AutoJsNodeCreateOptions*, AutoJsNodeHandle**);
    int32_t (*execute_script)(AutoJsNodeHandle*, const AutoJsNodeExecutionRequest*, AutoJsNodeExecutionResult*);
    int32_t (*execute_commonjs)(AutoJsNodeHandle*, const AutoJsNodeExecutionRequest*, AutoJsNodeExecutionResult*);
    int32_t (*execute_module)(AutoJsNodeHandle*, const AutoJsNodeExecutionRequest*, AutoJsNodeExecutionResult*);
    int32_t (*poll_runtime)(AutoJsNodeHandle*, const AutoJsNodePollOptions*, AutoJsNodeExecutionResult*);
    int32_t (*request_stop)(AutoJsNodeHandle*, int32_t);
    int32_t (*force_stop)(AutoJsNodeHandle*, int32_t);
    int32_t (*get_state)(AutoJsNodeHandle*, AutoJsNodeRuntimeState*);
    int32_t (*get_diagnostics)(AutoJsNodeHandle*, AutoJsNodeDiagnostics*);
    int32_t (*destroy_runtime)(AutoJsNodeHandle*);
    int32_t (*post_bridge_result)(AutoJsNodeHandle*, const char*, uint64_t);
    int32_t (*legacy_create)(const AutoJsNodeCreateOptions*, AutoJsNodeHandle**);
    int32_t (*legacy_start)(AutoJsNodeHandle*);
    int32_t (*legacy_execute)(AutoJsNodeHandle*, const AutoJsNodeExecutionRequest*, AutoJsNodeExecutionResult*);
    int32_t (*legacy_request_stop)(AutoJsNodeHandle*, int32_t);
    int32_t (*legacy_destroy)(AutoJsNodeHandle*);
    int32_t (*legacy_post_bridge_result)(AutoJsNodeHandle*, const char*, uint64_t);
    int32_t (*legacy_get_diagnostics)(AutoJsNodeHandle*, AutoJsNodeDiagnostics*);
} AutoJsNodeRuntimeApiV1;

const AutoJsNodeRuntimeApiV1* autojs_node_get_runtime_api_v1(void);

#ifdef __cplusplus
}
#endif
