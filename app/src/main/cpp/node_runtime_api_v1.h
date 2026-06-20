#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define AUTOJS_NODE_RUNTIME_API_ABI_VERSION 1u

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

typedef struct AutoJsNodeExecutionResult {
    uint32_t struct_size;
    int32_t exit_code;
    int32_t result_code;
    const char* stdout_text;
    const char* stderr_text;
    const char* result_json;
    const char* diagnostics_json;
} AutoJsNodeExecutionResult;

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
    const char* (*get_node_version)(void);
    const char* (*get_runtime_descriptor)(void);
    int32_t (*create)(const AutoJsNodeCreateOptions*, AutoJsNodeHandle**);
    int32_t (*start)(AutoJsNodeHandle*);
    int32_t (*execute)(AutoJsNodeHandle*, const AutoJsNodeExecutionRequest*, AutoJsNodeExecutionResult*);
    int32_t (*request_stop)(AutoJsNodeHandle*, int32_t);
    int32_t (*destroy)(AutoJsNodeHandle*);
    int32_t (*post_bridge_result)(AutoJsNodeHandle*, const char*, uint64_t);
    int32_t (*get_diagnostics)(AutoJsNodeHandle*, AutoJsNodeDiagnostics*);
} AutoJsNodeRuntimeApiV1;

const AutoJsNodeRuntimeApiV1* autojs_node_get_runtime_api_v1(void);

#ifdef __cplusplus
}
#endif
