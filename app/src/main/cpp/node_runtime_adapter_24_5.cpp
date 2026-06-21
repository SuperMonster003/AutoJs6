#include "node_runtime_api_v1.h"

#include "node_version.h"

#include <stddef.h>

namespace {

const char kRuntimeDescriptor[] =
        "{\"runtimeSlot\":\"node24_5\","
        "\"nodeVersion\":\"" NODE_VERSION "\","
        "\"adapterAbiVersion\":1,"
        "\"status\":\"surface_only\","
        "\"execute\":\"legacy_jni_path\"}";

const char kUnavailableDiagnostics[] =
        "{\"code\":\"ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED\","
        "\"message\":\"Node 24.5 execution still uses the legacy JNI path.\"}";

const char* getNodeVersion() {
    return NODE_VERSION;
}

const char* getRuntimeDescriptor() {
    return kRuntimeDescriptor;
}

int32_t createRuntime(const AutoJsNodeCreateOptions*, AutoJsNodeHandle** handle) {
    if (handle != nullptr) {
        *handle = nullptr;
    }
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t startRuntime(AutoJsNodeHandle*) {
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t fillUnavailableExecutionResult(AutoJsNodeExecutionResult* result) {
    if (result != nullptr && result->struct_size >= sizeof(AutoJsNodeExecutionResult)) {
        result->exit_code = -1;
        result->result_code = AUTOJS_NODE_RESULT_UNAVAILABLE;
        result->stdout_text = "";
        result->stderr_text = "";
        result->result_json = "{}";
        result->diagnostics_json = kUnavailableDiagnostics;
    }
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t executeScript(
        AutoJsNodeHandle*,
        const AutoJsNodeExecutionRequest*,
        AutoJsNodeExecutionResult* result
) {
    return fillUnavailableExecutionResult(result);
}

int32_t executeCommonjs(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result
) {
    return executeScript(handle, request, result);
}

int32_t executeModule(
        AutoJsNodeHandle* handle,
        const AutoJsNodeExecutionRequest* request,
        AutoJsNodeExecutionResult* result
) {
    return executeScript(handle, request, result);
}

int32_t pollRuntime(
        AutoJsNodeHandle*,
        const AutoJsNodePollOptions*,
        AutoJsNodeExecutionResult* result
) {
    return fillUnavailableExecutionResult(result);
}

int32_t requestStop(AutoJsNodeHandle*, int32_t) {
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t forceStop(AutoJsNodeHandle*, int32_t) {
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t destroyRuntime(AutoJsNodeHandle*) {
    return AUTOJS_NODE_RESULT_OK;
}

int32_t postBridgeResult(AutoJsNodeHandle*, const char*, uint64_t) {
    return AUTOJS_NODE_RESULT_UNAVAILABLE;
}

int32_t getState(AutoJsNodeHandle*, AutoJsNodeRuntimeState* state) {
    if (state != nullptr && state->struct_size >= sizeof(AutoJsNodeRuntimeState)) {
        state->state_code = AUTOJS_NODE_RUNTIME_STATE_UNKNOWN;
        state->execution_id = 0;
        state->state_json = "{\"state\":\"surface_only\"}";
    }
    return AUTOJS_NODE_RESULT_OK;
}

int32_t getDiagnostics(AutoJsNodeHandle*, AutoJsNodeDiagnostics* diagnostics) {
    if (diagnostics != nullptr && diagnostics->struct_size >= sizeof(AutoJsNodeDiagnostics)) {
        diagnostics->abi_version = AUTOJS_NODE_RUNTIME_API_ABI_VERSION;
        diagnostics->runtime_descriptor_json = kRuntimeDescriptor;
        diagnostics->state_json = "{\"state\":\"surface_only\"}";
        diagnostics->resource_json = "{}";
        diagnostics->last_error_json = kUnavailableDiagnostics;
    }
    return AUTOJS_NODE_RESULT_OK;
}

const AutoJsNodeRuntimeApiV1 kRuntimeApi = {
        sizeof(AutoJsNodeRuntimeApiV1),
        AUTOJS_NODE_RUNTIME_API_ABI_VERSION,
        0,
        getNodeVersion,
        getRuntimeDescriptor,
        createRuntime,
        executeScript,
        executeCommonjs,
        executeModule,
        pollRuntime,
        requestStop,
        forceStop,
        getState,
        getDiagnostics,
        destroyRuntime,
        postBridgeResult,
        createRuntime,
        startRuntime,
        executeScript,
        requestStop,
        destroyRuntime,
        postBridgeResult,
        getDiagnostics,
};

}  // namespace

extern "C" const AutoJsNodeRuntimeApiV1* autojs_node_get_runtime_api_v1(void) {
    return &kRuntimeApi;
}
