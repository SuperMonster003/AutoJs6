#include "node_bridge_internal.h"
#include "node_runtime_api_v1.h"

#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

using namespace autojs6::node_bridge;
using namespace autojs6::node_bridge::internal;

#ifndef O_CLOEXEC
#define O_CLOEXEC 0
#endif

extern "C" JNIEXPORT jint JNICALL
Java_org_autojs_autojs_engine_NativeNodeRuntimeBridge_nativeRunMain(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray argv,
        jstring workingDirectory,
        jobject outputSink
) {
    const auto startedAt = Clock::now();
    std::vector<std::string> args = toStringVector(env, argv, "argv");
    if (env->ExceptionCheck()) {
        return -1;
    }
    if (args.empty()) {
        throwJava(env, "java/lang/IllegalArgumentException", "argv must contain at least argv[0]");
        return -1;
    }

    std::lock_guard<std::mutex> lock(nodeStartMutex());

    ScopedWorkingDirectory scopedWorkingDirectory;
    if (!scopedWorkingDirectory.enter(env, toStdString(env, workingDirectory))) {
        return -1;
    }

    NodeStart nodeStart = resolveNodeStart(env);
    if (env->ExceptionCheck() || nodeStart == nullptr) {
        return -1;
    }

    std::vector<char*> rawArgs;
    rawArgs.reserve(args.size());
    for (std::string& arg : args) {
        rawArgs.push_back(arg.data());
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Starting Node.js with argc=%zu, script=%s",
            args.size(),
            args.size() > 1 ? args[1].c_str() : ""
    );

    ScopedOutputRedirection outputRedirection;
    if (!outputRedirection.start(env, outputSink)) {
        return -1;
    }

    const auto beforeNodeStart = Clock::now();
    int exitCode = nodeStart(static_cast<int>(rawArgs.size()), rawArgs.data());
    const auto afterNodeStart = Clock::now();
    outputRedirection.stop(env);
    const auto stoppedAt = Clock::now();
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Node.js bridge timings: prepare=%lldms, nodeStart=%lldms, outputStop=%lldms, total=%lldms, exitCode=%d",
            elapsedMs(startedAt, beforeNodeStart),
            elapsedMs(beforeNodeStart, afterNodeStart),
            elapsedMs(afterNodeStart, stoppedAt),
            elapsedMs(startedAt, stoppedAt),
            exitCode
    );
    return exitCode;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeRuntimeBridge_nativeMissingEmbedderSymbols(
        JNIEnv* env,
        jobject /* thiz */
) {
    void* handle = dlopen(kNodeLibraryName, RTLD_NOW | RTLD_GLOBAL);
    std::vector<std::string> missing;
    for (size_t i = 0; i < kEmbedderSymbolCount; ++i) {
        const SymbolRequirement& requirement = kEmbedderSymbols[i];
        if (!hasAnySymbol(handle, requirement)) {
            missing.emplace_back(requirement.description);
        }
    }
    return toJavaStringArray(env, missing);
}

static jobjectArray runEmbeddedScriptLifecycleNative(
        JNIEnv* env,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& envPairs,
        bool esmExperimentalEnabled,
        bool dynamicImportExperimentalEnabled,
        bool rawNodeNetworkModulesExperimentalEnabled,
        bool workerThreadsExperimentalEnabled,
        bool childProcessExperimentalEnabled,
        bool javaInteropExperimentalEnabled
) {
    EmbeddedScriptExecutionRequest request;
    request.source = toStdString(env, source);
    request.sourceName = toStdString(env, sourceName);
    request.workingDirectory = toStdString(env, workingDirectory);
    request.sandboxRoot = toStdString(env, sandboxRoot);
    request.moduleSources = moduleSourcePairs;
    request.runtimeModuleSources = runtimeModuleSourcePairs;
    request.env = envPairs;
    request.esmExperimentalEnabled = esmExperimentalEnabled;
    request.dynamicImportExperimentalEnabled = dynamicImportExperimentalEnabled;
    request.rawNodeNetworkModulesExperimentalEnabled = rawNodeNetworkModulesExperimentalEnabled;
    request.workerThreadsExperimentalEnabled = workerThreadsExperimentalEnabled;
    request.childProcessExperimentalEnabled = childProcessExperimentalEnabled;
    request.javaInteropExperimentalEnabled = javaInteropExperimentalEnabled;
    std::vector<std::string> payload = runEmbeddedScriptExecution(request);
    return toJavaStringArray(env, payload);
}

static const char* adapterResultCodeName(int32_t code) {
    switch (code) {
        case AUTOJS_NODE_RESULT_OK:
            return "ok";
        case AUTOJS_NODE_RESULT_INVALID_ARGUMENT:
            return "invalid_argument";
        case AUTOJS_NODE_RESULT_UNAVAILABLE:
            return "unavailable";
        case AUTOJS_NODE_RESULT_INTERRUPTED:
            return "interrupted";
        case AUTOJS_NODE_RESULT_FAILED:
            return "failed";
        default:
            return "unknown";
    }
}

namespace {

constexpr size_t kSignalSafeMarkerRecordBytes = 256;
constexpr const char* kSignalSafeMarkerSchema = "autojs6-node-phase13-signal-safe-crash-marker-v1";
constexpr const char* kSignalSafeMarkerWriter = "native_fixed_size_signal_safe_writer";

struct SignalSafeMarkerWriteResult {
    bool succeeded = false;
    ssize_t bytesWritten = 0;
    int errorNumber = 0;
};

void appendSignalSafeLiteral(char* buffer, size_t* offset, const char* value) {
    if (value == nullptr) {
        return;
    }
    while (*value != '\0' && *offset < kSignalSafeMarkerRecordBytes - 1) {
        buffer[*offset] = *value;
        ++(*offset);
        ++value;
    }
}

void appendSignalSafeTruncated(char* buffer, size_t* offset, const char* value, size_t maxBytes) {
    if (value == nullptr) {
        return;
    }
    size_t copied = 0;
    while (*value != '\0' && copied < maxBytes && *offset < kSignalSafeMarkerRecordBytes - 1) {
        const char next = *value;
        buffer[*offset] = next == '\n' || next == '\r' ? '_' : next;
        ++(*offset);
        ++value;
        ++copied;
    }
}

void appendSignalSafeUnsigned(char* buffer, size_t* offset, unsigned long long value) {
    char digits[32];
    size_t count = 0;
    do {
        digits[count++] = static_cast<char>('0' + (value % 10));
        value /= 10;
    } while (value > 0 && count < sizeof(digits));
    while (count > 0 && *offset < kSignalSafeMarkerRecordBytes - 1) {
        buffer[*offset] = digits[--count];
        ++(*offset);
    }
}

void appendSignalSafeSigned(char* buffer, size_t* offset, long long value) {
    if (value < 0) {
        appendSignalSafeLiteral(buffer, offset, "-");
        appendSignalSafeUnsigned(buffer, offset, static_cast<unsigned long long>(-(value + 1)) + 1ULL);
    } else {
        appendSignalSafeUnsigned(buffer, offset, static_cast<unsigned long long>(value));
    }
}

SignalSafeMarkerWriteResult writeSignalSafeCrashMarkerRecord(
        const char* path,
        const char* markerId,
        const char* executionId,
        const char* scenario,
        const char* reason,
        int pid,
        long long epochMs
) {
    char record[kSignalSafeMarkerRecordBytes];
    for (size_t index = 0; index < sizeof(record); ++index) {
        record[index] = ' ';
    }
    size_t offset = 0;
    appendSignalSafeLiteral(record, &offset, "AJ6SIG13 ");
    appendSignalSafeLiteral(record, &offset, "schema=");
    appendSignalSafeLiteral(record, &offset, kSignalSafeMarkerSchema);
    appendSignalSafeLiteral(record, &offset, " writer=");
    appendSignalSafeLiteral(record, &offset, kSignalSafeMarkerWriter);
    appendSignalSafeLiteral(record, &offset, " marker=");
    appendSignalSafeTruncated(record, &offset, markerId, 40);
    appendSignalSafeLiteral(record, &offset, " exec=");
    appendSignalSafeTruncated(record, &offset, executionId, 40);
    appendSignalSafeLiteral(record, &offset, " scenario=");
    appendSignalSafeTruncated(record, &offset, scenario, 36);
    appendSignalSafeLiteral(record, &offset, " reason=");
    appendSignalSafeTruncated(record, &offset, reason, 28);
    appendSignalSafeLiteral(record, &offset, " pid=");
    appendSignalSafeSigned(record, &offset, static_cast<long long>(pid));
    appendSignalSafeLiteral(record, &offset, " epochMs=");
    appendSignalSafeSigned(record, &offset, epochMs);
    record[kSignalSafeMarkerRecordBytes - 1] = '\n';

    const int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, S_IRUSR | S_IWUSR);
    if (fd < 0) {
        return {false, 0, errno};
    }

    size_t total = 0;
    while (total < sizeof(record)) {
        const ssize_t written = write(fd, record + total, sizeof(record) - total);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            const int errorNumber = errno;
            close(fd);
            return {false, static_cast<ssize_t>(total), errorNumber};
        }
        if (written == 0) {
            close(fd);
            return {false, static_cast<ssize_t>(total), EIO};
        }
        total += static_cast<size_t>(written);
    }

    if (close(fd) != 0) {
        return {false, static_cast<ssize_t>(total), errno};
    }
    return {true, static_cast<ssize_t>(total), 0};
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeWriteSignalSafeCrashMarker(
        JNIEnv* env,
        jobject /* thiz */,
        jstring markerPath,
        jstring markerId,
        jstring executionId,
        jstring scenario,
        jstring reason,
        jint pid,
        jlong epochMs
) {
    const auto startedAt = Clock::now();
    const std::string path = toStdString(env, markerPath);
    const std::string markerIdText = toStdString(env, markerId);
    const std::string executionIdText = toStdString(env, executionId);
    const std::string scenarioText = toStdString(env, scenario);
    const std::string reasonText = toStdString(env, reason);
    std::vector<std::string> payload;
    putPayload(payload, "signal_safe_marker.schema", kSignalSafeMarkerSchema);
    putPayload(payload, "signal_safe_marker.writer", kSignalSafeMarkerWriter);
    putPayload(payload, "signal_safe_marker.record_bytes", static_cast<long long>(kSignalSafeMarkerRecordBytes));
    putPayload(payload, "signal_safe_marker.path", path);
    putPayload(payload, "signal_safe_marker.marker_id", markerIdText);
    putPayload(payload, "signal_safe_marker.execution_id", executionIdText);
    putPayload(payload, "signal_safe_marker.scenario", scenarioText);
    putPayload(payload, "signal_safe_marker.reason", reasonText);
    putPayload(payload, "signal_safe_marker.pid", static_cast<long long>(pid));
    putPayload(payload, "signal_safe_marker.epoch_ms", static_cast<long long>(epochMs));
    putPayload(payload, "signal_safe_marker.append_only", true);
    putPayload(payload, "signal_safe_marker.native_core", "open_write_close_fixed_buffer");
    putPayload(payload, "signal_safe_marker.signal_safe_syscalls", "open|write|close");

    if (path.empty()) {
        putPayload(payload, "signal_safe_marker.status", "failed");
        putPayload(payload, "signal_safe_marker.error", "marker path is empty");
        putPayload(payload, "signal_safe_marker.bytes_written", static_cast<long long>(0));
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        return toJavaStringArray(env, payload);
    }

    const SignalSafeMarkerWriteResult result = writeSignalSafeCrashMarkerRecord(
            path.c_str(),
            markerIdText.c_str(),
            executionIdText.c_str(),
            scenarioText.c_str(),
            reasonText.c_str(),
            static_cast<int>(pid),
            static_cast<long long>(epochMs)
    );
    putPayload(payload, "signal_safe_marker.status", result.succeeded ? "written" : "failed");
    putPayload(payload, "signal_safe_marker.bytes_written", static_cast<long long>(result.bytesWritten));
    putPayload(payload, "signal_safe_marker.errno", static_cast<long long>(result.errorNumber));
    if (!result.succeeded) {
        putPayload(payload, "signal_safe_marker.error", std::strerror(result.errorNumber));
    }
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    return toJavaStringArray(env, payload);
}

static std::string orderedStringPairsJson(const std::vector<std::pair<std::string, std::string>>& pairs) {
    std::string json = "[";
    for (size_t index = 0; index < pairs.size(); ++index) {
        if (index > 0) {
            json += ",";
        }
        json += "[";
        json += jsonStringLiteral(pairs[index].first);
        json += ",";
        json += jsonStringLiteral(pairs[index].second);
        json += "]";
    }
    json += "]";
    return json;
}

static std::string adapterModuleSourcesJson(
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::string& sandboxRoot
) {
    return std::string("{\"sandboxRoot\":") + jsonStringLiteral(sandboxRoot) +
            ",\"moduleSources\":" + orderedStringPairsJson(moduleSourcePairs) +
            ",\"runtimeModuleSources\":" + orderedStringPairsJson(runtimeModuleSourcePairs) + "}";
}

static uint32_t adapterExecutionFlags(
        bool esmExperimentalEnabled,
        bool dynamicImportExperimentalEnabled,
        bool rawNodeNetworkModulesExperimentalEnabled,
        bool workerThreadsExperimentalEnabled,
        bool childProcessExperimentalEnabled,
        bool javaInteropExperimentalEnabled
) {
    uint32_t flags = 0;
    if (esmExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_ESM_EXPERIMENTAL;
    }
    if (dynamicImportExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_DYNAMIC_IMPORT_EXPERIMENTAL;
    }
    if (rawNodeNetworkModulesExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL;
    }
    if (workerThreadsExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_WORKER_THREADS_EXPERIMENTAL;
    }
    if (childProcessExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_CHILD_PROCESS_EXPERIMENTAL;
    }
    if (javaInteropExperimentalEnabled) {
        flags |= AUTOJS_NODE_EXECUTION_FLAG_JAVA_INTEROP_EXPERIMENTAL;
    }
    return flags;
}

static jobjectArray runEmbeddedScriptAdapterV1DiagnosticsNative(
        JNIEnv* env,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jint moduleSourceCount,
        jint runtimeModuleSourceCount,
        jint envCount
) {
    const auto startedAt = Clock::now();
    const std::string sourceText = toStdString(env, source);
    const std::string sourceNameInput = toStdString(env, sourceName);
    const std::string sourceNameText = sourceNameInput.empty()
            ? "<embedded-user-script.js>"
            : sourceNameInput;
    const std::string workingDirectoryText = toStdString(env, workingDirectory);
    const char* workingDirectoryPtr = workingDirectoryText.empty() ? nullptr : workingDirectoryText.c_str();
    std::vector<std::string> payload;
    putPayload(payload, "embedded_script.request.source_name", sourceNameText);
    putPayload(payload, "embedded_script.request.source.length", static_cast<long long>(sourceText.size()));
    putPayload(payload, "embedded_script.request.working_directory", workingDirectoryText);
    putPayload(payload, "embedded_script.request.env.count", static_cast<long long>(envCount));
    putPayload(payload, "embedded_script.request.module_sources.count", static_cast<long long>(moduleSourceCount));
    putPayload(
            payload,
            "embedded_script.request.runtime_module_sources.count",
            static_cast<long long>(runtimeModuleSourceCount)
    );
    putPayload(payload, "embedded_script.request.source_kind", "kotlin_provided_source");
    putPayload(payload, "embedded_script.request.user_file_read_by_native", false);
    putPayload(payload, "embedded_script.runtime_adapter.path", "adapter_v1");
    putPayload(payload, "embedded_script.runtime_adapter.fallback_path", "legacy_jni");
    putPayload(payload, "embedded_script.runtime_adapter.mode", "diagnostics");

    const AutoJsNodeRuntimeApiV1* api = autojs_node_get_runtime_api_v1();
    if (api == nullptr) {
        putPayload(payload, "embedded_script.runtime_adapter.status", "api_unavailable");
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "autojs_node_get_runtime_api_v1 returned null");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_API_UNAVAILABLE");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        return toJavaStringArray(env, payload);
    }

    putPayload(payload, "embedded_script.runtime_adapter.abi_version", static_cast<long long>(api->abi_version));
    putPayload(payload, "embedded_script.runtime_adapter.struct_size", static_cast<long long>(api->struct_size));
    putPayload(payload, "embedded_script.runtime_adapter.feature_bits", static_cast<long long>(api->feature_bits));
    putPayload(payload, "embedded_script.runtime_adapter.node_version", api->get_node_version == nullptr ? "" : api->get_node_version());
    putPayload(
            payload,
            "embedded_script.runtime_adapter.runtime_descriptor_json",
            api->get_runtime_descriptor == nullptr ? "" : api->get_runtime_descriptor()
    );

    AutoJsNodeCreateOptions createOptions{};
    createOptions.struct_size = sizeof(AutoJsNodeCreateOptions);
    createOptions.runtime_descriptor_json = "{\"requestedPath\":\"adapter_v1\",\"host\":\"autojs6\"}";
    createOptions.working_directory = workingDirectoryPtr;
    createOptions.initial_environment_json = "{}";
    createOptions.timeout_ms = 0;
    AutoJsNodeHandle* handle = nullptr;
    const int32_t createCode = api->create_runtime == nullptr
            ? AUTOJS_NODE_RESULT_UNAVAILABLE
            : api->create_runtime(&createOptions, &handle);
    putPayload(payload, "embedded_script.runtime_adapter.create_result_code", static_cast<long long>(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.create_result", adapterResultCodeName(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.handle_created", handle != nullptr);

    AutoJsNodeDiagnostics diagnostics{};
    diagnostics.struct_size = sizeof(AutoJsNodeDiagnostics);
    const int32_t diagnosticsCode = api->get_diagnostics == nullptr
            ? AUTOJS_NODE_RESULT_UNAVAILABLE
            : api->get_diagnostics(handle, &diagnostics);
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_result_code", static_cast<long long>(diagnosticsCode));
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_result", adapterResultCodeName(diagnosticsCode));
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_descriptor_json", diagnostics.runtime_descriptor_json);
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_state_json", diagnostics.state_json);
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_resource_json", diagnostics.resource_json);
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_last_error_json", diagnostics.last_error_json);

    if (handle != nullptr && api->destroy_runtime != nullptr) {
        const int32_t destroyCode = api->destroy_runtime(handle);
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result_code", static_cast<long long>(destroyCode));
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result", adapterResultCodeName(destroyCode));
    }

    putPayload(payload, "embedded_script.runtime_adapter.status", "diagnostics_ready");
    putPayload(payload, "embedded_script.status", "done");
    putPayload(payload, "embedded_script.detail", "adapter_v1 diagnostics completed");
    putPayload(payload, "embedded_script.succeeded", true);
    putPayload(payload, "embedded_script.exit_code", static_cast<long long>(0));
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    return toJavaStringArray(env, payload);
}

static jobjectArray runEmbeddedScriptAdapterV1ExecuteNative(
        JNIEnv* env,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        const std::vector<std::pair<std::string, std::string>>& moduleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& runtimeModuleSourcePairs,
        const std::vector<std::pair<std::string, std::string>>& envPairs,
        bool esmExperimentalEnabled,
        bool dynamicImportExperimentalEnabled,
        bool rawNodeNetworkModulesExperimentalEnabled,
        bool workerThreadsExperimentalEnabled,
        bool childProcessExperimentalEnabled,
        bool javaInteropExperimentalEnabled
) {
    const auto startedAt = Clock::now();
    const std::string sourceText = toStdString(env, source);
    const std::string sourceNameInput = toStdString(env, sourceName);
    const std::string sourceNameText = sourceNameInput.empty()
            ? "<embedded-user-script.js>"
            : sourceNameInput;
    const std::string workingDirectoryText = toStdString(env, workingDirectory);
    const std::string sandboxRootText = toStdString(env, sandboxRoot);
    const std::string moduleSourcesJson = adapterModuleSourcesJson(moduleSourcePairs, runtimeModuleSourcePairs, sandboxRootText);
    const std::string environmentJson = orderedStringPairsJson(envPairs);
    std::vector<std::string> payload;
    putPayload(payload, "embedded_script.request.source_name", sourceNameText);
    putPayload(payload, "embedded_script.request.source.length", static_cast<long long>(sourceText.size()));
    putPayload(payload, "embedded_script.request.working_directory", workingDirectoryText);
    putPayload(payload, "embedded_script.request.sandbox_root", sandboxRootText);
    putPayload(payload, "embedded_script.request.env.count", static_cast<long long>(envPairs.size()));
    putPayload(payload, "embedded_script.request.module_sources.count", static_cast<long long>(moduleSourcePairs.size()));
    putPayload(
            payload,
            "embedded_script.request.runtime_module_sources.count",
            static_cast<long long>(runtimeModuleSourcePairs.size())
    );
    putPayload(payload, "embedded_script.request.source_kind", "kotlin_provided_source");
    putPayload(payload, "embedded_script.request.user_file_read_by_native", false);
    putPayload(payload, "embedded_script.runtime_adapter.path", "adapter_v1");
    putPayload(payload, "embedded_script.runtime_adapter.fallback_path", "legacy_jni");
    putPayload(payload, "embedded_script.runtime_adapter.mode", "execution_path");

    const AutoJsNodeRuntimeApiV1* api = autojs_node_get_runtime_api_v1();
    if (api == nullptr) {
        putPayload(payload, "embedded_script.runtime_adapter.status", "api_unavailable");
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "autojs_node_get_runtime_api_v1 returned null");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_API_UNAVAILABLE");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        return toJavaStringArray(env, payload);
    }

    putPayload(payload, "embedded_script.runtime_adapter.abi_version", static_cast<long long>(api->abi_version));
    putPayload(payload, "embedded_script.runtime_adapter.struct_size", static_cast<long long>(api->struct_size));
    putPayload(payload, "embedded_script.runtime_adapter.feature_bits", static_cast<long long>(api->feature_bits));
    putPayload(payload, "embedded_script.runtime_adapter.node_version", api->get_node_version == nullptr ? "" : api->get_node_version());
    putPayload(
            payload,
            "embedded_script.runtime_adapter.runtime_descriptor_json",
            api->get_runtime_descriptor == nullptr ? "" : api->get_runtime_descriptor()
    );

    AutoJsNodeCreateOptions createOptions{};
    createOptions.struct_size = sizeof(AutoJsNodeCreateOptions);
    createOptions.runtime_descriptor_json = "{\"requestedPath\":\"adapter_v1\",\"host\":\"autojs6\"}";
    createOptions.working_directory = workingDirectoryText.empty() ? nullptr : workingDirectoryText.c_str();
    createOptions.initial_environment_json = environmentJson.c_str();
    createOptions.timeout_ms = 0;
    AutoJsNodeHandle* handle = nullptr;
    const int32_t createCode = api->create_runtime == nullptr
            ? AUTOJS_NODE_RESULT_FAILED
            : api->create_runtime(&createOptions, &handle);
    putPayload(payload, "embedded_script.runtime_adapter.create_result_code", static_cast<long long>(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.create_result", adapterResultCodeName(createCode));
    putPayload(payload, "embedded_script.runtime_adapter.handle_created", handle != nullptr);
    if (createCode != AUTOJS_NODE_RESULT_OK || handle == nullptr) {
        putPayload(payload, "embedded_script.runtime_adapter.status", "create_failed");
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "adapter_v1 create_runtime failed");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_CREATE_FAILED");
        putCommonEmbeddedProbePayload(payload, 0, startedAt);
        return toJavaStringArray(env, payload);
    }

    AutoJsNodeExecutionRequest executionRequest{};
    executionRequest.struct_size = sizeof(AutoJsNodeExecutionRequest);
    executionRequest.flags = adapterExecutionFlags(
            esmExperimentalEnabled,
            dynamicImportExperimentalEnabled,
            rawNodeNetworkModulesExperimentalEnabled,
            workerThreadsExperimentalEnabled,
            childProcessExperimentalEnabled,
            javaInteropExperimentalEnabled
    );
    executionRequest.source_name = sourceNameText.c_str();
    executionRequest.source = {
            sourceText.data(),
            static_cast<uint64_t>(sourceText.size())
    };
    executionRequest.working_directory = workingDirectoryText.empty() ? nullptr : workingDirectoryText.c_str();
    executionRequest.module_sources_json = moduleSourcesJson.c_str();
    executionRequest.environment_json = environmentJson.c_str();
    executionRequest.timeout_ms = 0;
    executionRequest.execution_id = 0;
    AutoJsNodeExecutionResult executionResult{};
    executionResult.struct_size = sizeof(AutoJsNodeExecutionResult);
    const int32_t executeCode = api->execute_script == nullptr
            ? AUTOJS_NODE_RESULT_FAILED
            : api->execute_script(handle, &executionRequest, &executionResult);
    putPayload(payload, "embedded_script.runtime_adapter.execute_result_code", static_cast<long long>(executeCode));
    putPayload(payload, "embedded_script.runtime_adapter.execute_result", adapterResultCodeName(executeCode));
    putPayload(payload, "embedded_script.runtime_adapter.execution_result_code", static_cast<long long>(executionResult.result_code));
    putPayload(payload, "embedded_script.runtime_adapter.diagnostics_json", executionResult.diagnostics_json);
    putPayload(payload, "embedded_script.runtime_adapter.status", executeCode == AUTOJS_NODE_RESULT_OK ? "executed" : "execute_failed");
    if (executionResult.result_json != nullptr && std::strlen(executionResult.result_json) > 0) {
        putPayload(payload, "embedded_script.status", executeCode == AUTOJS_NODE_RESULT_OK ? "done" : "failed");
        putEmbeddedScriptExecutionFields(payload, executionResult.result_json);
        putPayloadRaw(payload, "embedded_script.stdout", executionResult.stdout_text == nullptr ? "" : executionResult.stdout_text);
        putPayloadRaw(payload, "embedded_script.stderr", executionResult.stderr_text == nullptr ? "" : executionResult.stderr_text);
    } else {
        putPayload(payload, "embedded_script.status", "failed");
        putPayload(payload, "embedded_script.detail", "adapter_v1 did not return a result envelope");
        putPayload(payload, "embedded_script.succeeded", false);
        putPayload(payload, "embedded_script.exit_code", static_cast<long long>(1));
        putPayload(payload, "embedded_script.error_code", "ERR_AUTOJS6_NODE_ADAPTER_RESULT_MISSING");
    }

    if (api->destroy_runtime != nullptr) {
        const int32_t destroyCode = api->destroy_runtime(handle);
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result_code", static_cast<long long>(destroyCode));
        putPayload(payload, "embedded_script.runtime_adapter.destroy_result", adapterResultCodeName(destroyCode));
    }
    putCommonEmbeddedProbePayload(payload, 0, startedAt);
    return toJavaStringArray(env, payload);
}

// Keep the unversioned JNI symbols compatible with already-packaged INRT
// templates. Current app builds call the V2 symbols below so new arguments can
// be added without breaking older packaged APK smoke artifacts.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycle(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory
) {
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            workingDirectory,
            {},
            {},
            {},
            false,
            false,
            false,
            false,
            false,
            false
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptAdapterV1Diagnostics(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jint moduleSourceCount,
        jint runtimeModuleSourceCount,
        jint envCount
) {
    return runEmbeddedScriptAdapterV1DiagnosticsNative(
            env,
            source,
            sourceName,
            workingDirectory,
            moduleSourceCount,
            runtimeModuleSourceCount,
            envCount
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptAdapterV1Execute(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray runtimeModuleSourceNames,
        jobjectArray runtimeModuleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmExperimentalEnabled,
        jboolean dynamicImportExperimentalEnabled,
        jboolean rawNodeNetworkModulesExperimentalEnabled,
        jboolean workerThreadsExperimentalEnabled,
        jboolean childProcessExperimentalEnabled,
        jboolean javaInteropExperimentalEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceNameValues = toStringVectorOrEmpty(env, runtimeModuleSourceNames, "runtimeModuleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceValues = toStringVectorOrEmpty(env, runtimeModuleSources, "runtimeModuleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (runtimeModuleSourceNameValues.size() != runtimeModuleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "runtimeModuleSourceNames.size != runtimeModuleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> runtimeModuleSourcePairs;
    runtimeModuleSourcePairs.reserve(runtimeModuleSourceNameValues.size());
    for (size_t index = 0; index < runtimeModuleSourceNameValues.size(); ++index) {
        runtimeModuleSourcePairs.emplace_back(runtimeModuleSourceNameValues[index], runtimeModuleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptAdapterV1ExecuteNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            runtimeModuleSourcePairs,
            envPairs,
            esmExperimentalEnabled == JNI_TRUE,
            dynamicImportExperimentalEnabled == JNI_TRUE,
            rawNodeNetworkModulesExperimentalEnabled == JNI_TRUE,
            workerThreadsExperimentalEnabled == JNI_TRUE,
            childProcessExperimentalEnabled == JNI_TRUE,
            javaInteropExperimentalEnabled == JNI_TRUE
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleV2(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmExperimentalEnabled,
        jboolean dynamicImportExperimentalEnabled,
        jboolean rawNodeNetworkModulesExperimentalEnabled,
        jboolean workerThreadsExperimentalEnabled,
        jboolean childProcessExperimentalEnabled,
        jboolean javaInteropExperimentalEnabled
) {
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            {},
            {},
            envPairs,
            esmExperimentalEnabled == JNI_TRUE,
            dynamicImportExperimentalEnabled == JNI_TRUE,
            rawNodeNetworkModulesExperimentalEnabled == JNI_TRUE,
            workerThreadsExperimentalEnabled == JNI_TRUE,
            childProcessExperimentalEnabled == JNI_TRUE,
            javaInteropExperimentalEnabled == JNI_TRUE
    );
}

// Legacy packaged templates pass only module source arrays here. They do not
// know about request environment or experimental feature flags.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithModules(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            workingDirectory,
            moduleSourcePairs,
            {},
            {},
            false,
            false,
            false,
            false,
            false,
            false
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithModulesV2(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmExperimentalEnabled,
        jboolean dynamicImportExperimentalEnabled,
        jboolean rawNodeNetworkModulesExperimentalEnabled,
        jboolean workerThreadsExperimentalEnabled,
        jboolean childProcessExperimentalEnabled,
        jboolean javaInteropExperimentalEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            {},
            envPairs,
            esmExperimentalEnabled == JNI_TRUE,
            dynamicImportExperimentalEnabled == JNI_TRUE,
            rawNodeNetworkModulesExperimentalEnabled == JNI_TRUE,
            workerThreadsExperimentalEnabled == JNI_TRUE,
            childProcessExperimentalEnabled == JNI_TRUE,
            javaInteropExperimentalEnabled == JNI_TRUE
    );
}

// Legacy packaged templates with runtime module sources pass seven JNI
// arguments. Keep this entry point separate from the current V2 signature.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithRuntimeModules(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray runtimeModuleSourceNames,
        jobjectArray runtimeModuleSources
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceNameValues = toStringVectorOrEmpty(env, runtimeModuleSourceNames, "runtimeModuleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceValues = toStringVectorOrEmpty(env, runtimeModuleSources, "runtimeModuleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (runtimeModuleSourceNameValues.size() != runtimeModuleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "runtimeModuleSourceNames.size != runtimeModuleSources.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> runtimeModuleSourcePairs;
    runtimeModuleSourcePairs.reserve(runtimeModuleSourceNameValues.size());
    for (size_t index = 0; index < runtimeModuleSourceNameValues.size(); ++index) {
        runtimeModuleSourcePairs.emplace_back(runtimeModuleSourceNameValues[index], runtimeModuleSourceValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            workingDirectory,
            moduleSourcePairs,
            runtimeModuleSourcePairs,
            {},
            false,
            false,
            false,
            false,
            false,
            false
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithRuntimeModulesV2(
        JNIEnv* env,
        jobject /* thiz */,
        jstring source,
        jstring sourceName,
        jstring workingDirectory,
        jstring sandboxRoot,
        jobjectArray moduleSourceNames,
        jobjectArray moduleSources,
        jobjectArray runtimeModuleSourceNames,
        jobjectArray runtimeModuleSources,
        jobjectArray envNames,
        jobjectArray envValues,
        jboolean esmExperimentalEnabled,
        jboolean dynamicImportExperimentalEnabled,
        jboolean rawNodeNetworkModulesExperimentalEnabled,
        jboolean workerThreadsExperimentalEnabled,
        jboolean childProcessExperimentalEnabled,
        jboolean javaInteropExperimentalEnabled
) {
    const std::vector<std::string> moduleSourceNameValues = toStringVectorOrEmpty(env, moduleSourceNames, "moduleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> moduleSourceValues = toStringVectorOrEmpty(env, moduleSources, "moduleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceNameValues = toStringVectorOrEmpty(env, runtimeModuleSourceNames, "runtimeModuleSourceNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> runtimeModuleSourceValues = toStringVectorOrEmpty(env, runtimeModuleSources, "runtimeModuleSources");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envNameValues = toStringVectorOrEmpty(env, envNames, "envNames");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    const std::vector<std::string> envValueValues = toStringVectorOrEmpty(env, envValues, "envValues");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    if (moduleSourceNameValues.size() != moduleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "moduleSourceNames.size != moduleSources.size");
        return nullptr;
    }
    if (runtimeModuleSourceNameValues.size() != runtimeModuleSourceValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "runtimeModuleSourceNames.size != runtimeModuleSources.size");
        return nullptr;
    }
    if (envNameValues.size() != envValueValues.size()) {
        throwJava(env, "java/lang/IllegalArgumentException", "envNames.size != envValues.size");
        return nullptr;
    }
    std::vector<std::pair<std::string, std::string>> moduleSourcePairs;
    moduleSourcePairs.reserve(moduleSourceNameValues.size());
    for (size_t index = 0; index < moduleSourceNameValues.size(); ++index) {
        moduleSourcePairs.emplace_back(moduleSourceNameValues[index], moduleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> runtimeModuleSourcePairs;
    runtimeModuleSourcePairs.reserve(runtimeModuleSourceNameValues.size());
    for (size_t index = 0; index < runtimeModuleSourceNameValues.size(); ++index) {
        runtimeModuleSourcePairs.emplace_back(runtimeModuleSourceNameValues[index], runtimeModuleSourceValues[index]);
    }
    std::vector<std::pair<std::string, std::string>> envPairs;
    envPairs.reserve(envNameValues.size());
    for (size_t index = 0; index < envNameValues.size(); ++index) {
        envPairs.emplace_back(envNameValues[index], envValueValues[index]);
    }
    return runEmbeddedScriptLifecycleNative(
            env,
            source,
            sourceName,
            workingDirectory,
            sandboxRoot,
            moduleSourcePairs,
            runtimeModuleSourcePairs,
            envPairs,
            esmExperimentalEnabled == JNI_TRUE,
            dynamicImportExperimentalEnabled == JNI_TRUE,
            rawNodeNetworkModulesExperimentalEnabled == JNI_TRUE,
            workerThreadsExperimentalEnabled == JNI_TRUE,
            childProcessExperimentalEnabled == JNI_TRUE,
            javaInteropExperimentalEnabled == JNI_TRUE
    );
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbePing(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 1;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.ping.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    putPayload(payload, "native.ping", "ok");
    putPayload(payload, "bridge.links.libnode", AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE != 0);
    putPayload(
            payload,
            "bridge.implicit_libnode_load",
            AUTOJS6_NODE_BRIDGE_LINKS_LIBNODE != 0 ? "possible" : "not_expected"
    );
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    putPayload(payload, "timing.load.ms", static_cast<long long>(0));
    putPayload(payload, "timing.symbols.ms", static_cast<long long>(0));
    putPayload(payload, "initialize.status", "skipped");
    putPayload(payload, "initialize.detail", "level 1 bridge ping does not initialize Node");
    putPayload(payload, "teardown.status", "skipped");
    putPayload(payload, "teardown.detail", "InitializeOncePerProcess was not called");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.ping.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeLibnodeHandle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 3;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.libnode_handle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    putPayload(payload, "symbol.count", static_cast<long long>(0));
    putPayload(payload, "initialize.status", "skipped");
    putPayload(payload, "initialize.detail", "level 3 handle probe does not initialize Node");
    putPayload(payload, "teardown.status", "skipped");
    putPayload(payload, "teardown.detail", "InitializeOncePerProcess was not called");
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "probe.libnode_handle.exit level=%d available=%s",
            probeLevel,
            handle != nullptr ? "true" : "false"
    );
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeDlsym(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 4;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.dlsym.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedSymbolProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    putPayload(payload, "initialize.status", "skipped");
    putPayload(payload, "initialize.detail", "level 4 dlsym probe does not initialize Node");
    putPayload(payload, "teardown.status", "skipped");
    putPayload(payload, "teardown.detail", "InitializeOncePerProcess was not called");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.dlsym.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 5;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedLifecycleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeIsolateLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 6;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.isolate_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedIsolateLifecycleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.isolate_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeUvIsolateLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 7;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.uv_isolate_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedUvIsolateLifecycleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.uv_isolate_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8Lifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 8;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8InitOnlyLifecycleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvIsolateLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 8;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_isolate_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvIsolateProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_isolate_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvDiagnosticsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 9;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_diagnostics_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvDiagnosticsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_diagnostics_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvCloseLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 10;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_close_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvCloseProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_close_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvRunLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 11;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_run_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvRunProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_run_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvIsolateDataLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 12;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_isolate_data_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvIsolateDataProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_isolate_data_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEnvironmentLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 13;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_environment_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvEnvironmentProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_environment_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvLoadEnvironmentLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 14;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_load_environment_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvLoadEnvironmentProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_load_environment_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvSpinEventLoopLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 15;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_spin_event_loop_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvSpinEventLoopProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_spin_event_loop_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineJsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 16;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_inline_js_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvInlineJsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_inline_js_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvProcessJsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 17;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_process_js_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvProcessJsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_process_js_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleJsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 18;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_js_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleJsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_js_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvStdoutCaptureLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 19;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_stdout_capture_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvStdoutCaptureProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_stdout_capture_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvJsResultLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 20;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_js_result_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvJsResultProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_js_result_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvStdoutWriteLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 21;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_stdout_write_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvStdoutWriteProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_stdout_write_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleDiagnosticsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 22;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_diagnostics_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleDiagnosticsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_diagnostics_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleStreamLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 23;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_stream_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleStreamProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_stream_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleShapeLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 24;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_shape_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleShapeProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_shape_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleReplaceLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 25;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_replace_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleReplaceProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_replace_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleFamilyLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 26;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_family_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleFamilyProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_family_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleFormatLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 27;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_format_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleFormatProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_format_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleRejectionLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 28;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_rejection_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleRejectionProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_rejection_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvConsoleUncaughtLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 29;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_uncaught_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvConsoleUncaughtProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_console_uncaught_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvSchedulingLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 30;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_scheduling_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvSchedulingProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_scheduling_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvSchedulingOrderLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 31;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_scheduling_order_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvSchedulingOrderProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_scheduling_order_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvAsyncConsoleLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 32;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_async_console_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvAsyncConsoleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_async_console_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvAsyncErrorLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 33;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_async_error_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvAsyncErrorProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_async_error_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvBootstrapLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 34;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_bootstrap_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvBootstrapProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_bootstrap_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvBootstrapScriptLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 35;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_bootstrap_script_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvBootstrapScriptProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_bootstrap_script_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptCompletionLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 36;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_completion_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptCompletionProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_completion_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptFailureLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 37;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_failure_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptFailureProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_failure_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvCompletionDrainLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 38;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_completion_drain_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvCompletionDrainProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_completion_drain_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPostCompletionErrorLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 39;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_post_completion_error_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPostCompletionErrorProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_post_completion_error_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvOutputEventsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 40;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_output_events_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvOutputEventsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_output_events_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvOutputEnvelopeLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 41;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_output_envelope_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvOutputEnvelopeProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_output_envelope_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptCancelLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 42;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_cancel_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptCancelProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_cancel_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvCapabilityDescriptorLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 43;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_capability_descriptor_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvCapabilityDescriptorProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_capability_descriptor_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginContractLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 44;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_contract_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginContractProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_contract_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginRequestLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 45;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_request_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginRequestProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_request_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginEventsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 46;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_events_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginEventsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_events_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginFailureLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 47;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_failure_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginFailureProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_failure_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginCancelLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 48;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_cancel_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginCancelProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_cancel_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginDisposeLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 49;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_dispose_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginDisposeProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_dispose_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginLifecycleLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 50;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_lifecycle_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginLifecycleProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_lifecycle_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginInvalidRequestLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 51;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_invalid_request_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginInvalidRequestProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_invalid_request_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginStateMachineLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 52;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_state_machine_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginStateMachineProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_state_machine_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginTimeoutLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 53;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_timeout_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginTimeoutProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_timeout_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginCrashLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 54;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_crash_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginCrashProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_crash_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginBackpressureLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 55;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_backpressure_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginBackpressureProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_backpressure_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginNegotiationLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 56;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_negotiation_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginNegotiationProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_negotiation_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginPermissionLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 57;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_permission_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginPermissionProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_permission_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginManifestLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 58;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_manifest_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginManifestProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_manifest_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginAbiLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 59;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_abi_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginAbiProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_abi_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginLibraryLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 60;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_library_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginLibraryProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_library_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginInstanceLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 61;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_instance_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginInstanceProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_instance_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginSessionLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 62;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_session_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginSessionProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_session_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginQueueLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 63;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_queue_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginQueueProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_queue_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginConcurrencyLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 64;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_concurrency_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginConcurrencyProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_concurrency_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginRaceLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 65;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_race_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginRaceProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_race_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginShutdownLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 66;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_shutdown_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginShutdownProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_shutdown_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginHeartbeatLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 67;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_heartbeat_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginHeartbeatProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_heartbeat_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginHealthLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 68;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_health_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginHealthProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_health_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginTelemetryLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 69;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_telemetry_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginTelemetryProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_telemetry_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginDiagnosticsLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 70;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_diagnostics_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginDiagnosticsProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_diagnostics_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginRecoveryLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 71;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_recovery_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginRecoveryProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_recovery_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginRestartLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 72;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_restart_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginRestartProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_restart_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginRestartBudgetLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 73;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_restart_budget_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginRestartBudgetProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_restart_budget_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvPluginQuarantineLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 74;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_quarantine_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvPluginQuarantineProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_plugin_quarantine_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptDescriptorLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 75;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_descriptor_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptDescriptorProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_descriptor_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptNormalizationLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 76;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_normalization_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptNormalizationProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_normalization_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptContextLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 77;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_context_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptContextProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_context_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptResultV2Lifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 78;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_result_v2_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptResultV2ProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_result_v2_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptCancellationTokenLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 79;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_cancellation_token_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptCancellationTokenProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_cancellation_token_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptTimeoutPolicyLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 80;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_timeout_policy_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptTimeoutPolicyProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_timeout_policy_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptMemoryPolicyLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 81;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_memory_policy_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptMemoryPolicyProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_memory_policy_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvScriptPreflightSummaryLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    const auto startedAt = Clock::now();
    constexpr int probeLevel = 82;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_preflight_summary_lifecycle.enter level=%d", probeLevel);
    std::vector<std::string> payload;
    void* handle = probeLoadedLibnodeHandle(payload);
    appendEmbeddedV8UvScriptPreflightSummaryProbePayload(payload, handle);
    putCommonEmbeddedProbePayload(payload, probeLevel, startedAt);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "probe.v8_uv_script_preflight_summary_lifecycle.exit level=%d", probeLevel);
    return toJavaStringArray(env, payload);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineConstantLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineConstant, 83);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineReturnValueLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineReturnValue, 84);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineErrorLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineError, 85);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineOutputLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineOutput, 86);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAsyncLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAsync, 87);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineCancelLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineCancel, 88);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineTimeoutLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineTimeout, 89);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineRepeatedProcessLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineRepeatedProcess, 90);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineBuiltinPolicyLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineBuiltinPolicy, 91);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineRequireDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineRequireDenied, 92);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNpmDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNpmDenied, 93);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePackageJsonDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePackageJsonDenied, 94);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNodeModulesDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNodeModulesDenied, 95);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNativeAddonDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNativeAddonDenied, 96);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineEsmDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineEsmDenied, 97);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDynamicImportDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDynamicImportDenied, 98);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWorkerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWorkerDenied, 99);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineCapabilitySummaryLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineCapabilitySummary, 100);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineFilesystemDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineFilesystemDenied, 101);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineChildProcessDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineChildProcessDenied, 102);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNetworkDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNetworkDenied, 103);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePermissionsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePermissionsDenied, 104);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAndroidBridgeDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAndroidBridgeDenied, 105);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAutoJsApiDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAutoJsApiDenied, 106);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineConsoleBridgeDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineConsoleBridgeDenied, 107);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineJsonSocketDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineJsonSocketDenied, 108);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineBinderDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineBinderDenied, 109);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineUiBridgeDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineUiBridgeDenied, 110);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAccessibilityDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAccessibilityDenied, 111);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineImagesDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineImagesDenied, 112);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDialogsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDialogsDenied, 113);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineSensorsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineSensorsDenied, 114);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineMediaCameraDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineMediaCameraDenied, 115);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineStorageDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineStorageDenied, 116);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineShellDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineShellDenied, 117);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNotificationDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNotificationDenied, 118);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineClipboardDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineClipboardDenied, 119);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDeviceInfoDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDeviceInfoDenied, 120);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineVibrationDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineVibrationDenied, 121);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineToastDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineToastDenied, 122);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineFloatyDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineFloatyDenied, 123);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineEventsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineEventsDenied, 124);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineThreadsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineThreadsDenied, 125);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineTimersDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineTimersDenied, 126);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWebViewDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWebViewDenied, 127);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineHttpDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineHttpDenied, 128);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineCryptoDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineCryptoDenied, 129);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineOcrDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineOcrDenied, 130);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineMlAiDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineMlAiDenied, 131);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWebSocketDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWebSocketDenied, 132);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineBluetoothDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineBluetoothDenied, 133);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNfcDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNfcDenied, 134);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineUsbDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineUsbDenied, 135);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineLocationDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineLocationDenied, 136);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineContactsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineContactsDenied, 137);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineCalendarDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineCalendarDenied, 138);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineSmsTelephonyDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineSmsTelephonyDenied, 139);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAccountDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAccountDenied, 140);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePackageManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied, 141);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineIntentActivityDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied, 142);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineBroadcastDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied, 143);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineContentProviderDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied, 144);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineMediaStoreDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied, 145);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDownloadManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied, 146);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineInputMethodDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied, 147);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAppOpsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied, 148);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePermissionManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied, 149);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineSettingsDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineSettingsDenied, 150);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePowerManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied, 151);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineKeyguardDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied, 152);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWallpaperDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied, 153);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineShortcutManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied, 154);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAlarmManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied, 155);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineJobSchedulerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied, 156);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWorkManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /* thiz */
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied, 157);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineClipboardListenerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied, 158);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineNotificationListenerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied, 159);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAccessibilityControlDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied, 160);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDevicePolicyDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied, 161);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineUsageStatsDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied, 162);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineVpnConnectivityDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied, 163);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineWifiManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied, 164);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineTelephonySubscriptionDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied, 165);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineCameraManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied, 166);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineAudioManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied, 167);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlineDisplayManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied, 168);
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvInlinePrintManagerDeniedLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied, 169);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceDescriptorLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceDescriptor, 170);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceSizeLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceSize, 171);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceEncodingLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceEncoding, 172);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceNameLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceName, 173);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceWrapperLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceWrapper, 174);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceStrictModeLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceStrictMode, 175);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourceCapabilityLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourceCapability, 176);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserSourcePreflightLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserSourcePreflight, 177);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineConstantLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant, 178);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineStdoutLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout, 179);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineStderrLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr, 180);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineReturnValueLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue, 181);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineThrownErrorLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError, 182);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlinePromiseLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise, 183);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineAsyncOrderingLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering, 184);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvControlledUserInlineSummaryLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary, 185);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileDescriptorLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileDescriptor, 186);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileReadPolicyLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileReadPolicy, 187);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFilePathNormalizationLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFilePathNormalization, 188);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileWorkingDirectoryLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory, 189);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileSourceLoadingLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileSourceLoading, 190);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileExecutionDryRunLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun, 191);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileErrorStackFilenameLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename, 192);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvUserFileExecutionSummaryLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary, 193);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptRequestLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest, 194);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptResultLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult, 195);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptOutputEventLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent, 196);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptErrorLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptError, 197);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptTimeoutLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout, 198);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptCancellationLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation, 199);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptProcessIsolationLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation, 200);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedScriptContractLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract, 201);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpLifecycleReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness, 202);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpSourceInputReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness, 203);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpOutputReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness, 204);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpErrorHandlingReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness, 205);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpAsyncReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness, 206);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpTimeoutIsolationReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness, 207);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpSecurityPolicyReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness, 208);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeEmbeddedProbeV8UvEmbeddedMvpGoNoGoReadinessLifecycle(
        JNIEnv* env,
        jobject /*thiz*/
) {
    return nativeEmbeddedProbeInlineKind(env, EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness, 209);
}
