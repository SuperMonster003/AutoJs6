#include "node_bridge_internal.h"

namespace autojs6::node_bridge::internal {

using namespace autojs6::node_bridge;

void putEmbeddedScriptExecutionFields(std::vector<std::string>& payload, const std::string& text) {
    putPayload(payload, "embedded_script.result_json", text);
    putPayload(payload, "embedded_script.succeeded", jsonBooleanField(text, "succeeded"));
    putPayload(payload, "embedded_script.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "embedded_script.result_text", jsonStringField(text, "resultText"));
    putPayload(payload, "embedded_script.error_name", jsonStringField(text, "errorName"));
    putPayload(payload, "embedded_script.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "embedded_script.error_stack", jsonStringField(text, "errorStack"));
    putPayload(payload, "embedded_script.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "embedded_script.source_name", jsonStringField(text, "sourceName"));
    putPayload(payload, "embedded_script.working_directory", jsonStringField(text, "workingDirectory"));
    putPayload(payload, "embedded_script.sandbox_root", jsonStringField(text, "sandboxRoot"));
    putPayload(payload, "embedded_script.current_working_directory", jsonStringField(text, "currentWorkingDirectory"));
    putPayloadRaw(payload, "embedded_script.stdout", jsonStringField(text, "stdout"));
    putPayloadRaw(payload, "embedded_script.stderr", jsonStringField(text, "stderr"));
    putPayload(payload, "embedded_script.stdout.bytes", jsonNumberField(text, "stdoutBytes"));
    putPayload(payload, "embedded_script.stderr.bytes", jsonNumberField(text, "stderrBytes"));
    putPayload(payload, "embedded_script.stdout.truncated", jsonBooleanField(text, "stdoutTruncated"));
    putPayload(payload, "embedded_script.stderr.truncated", jsonBooleanField(text, "stderrTruncated"));
    putPayload(payload, "embedded_script.console_events_json", jsonStringField(text, "consoleEventsJson"));
    putPayload(payload, "embedded_script.console_event_count", jsonNumberField(text, "consoleEventCount"));
    putPayload(payload, "embedded_script.console_events_truncated", jsonBooleanField(text, "consoleEventsTruncated"));
    putPayload(payload, "embedded_script.chdir_failure_reason", jsonStringField(text, "chdirFailureReason"));
    putPayload(payload, "embedded_script.create_require_failure_reason", jsonStringField(text, "createRequireFailureReason"));
    putPayload(payload, "embedded_script.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "embedded_script.elapsed_ms", jsonNumberField(text, "elapsedMs"));
    putPayload(payload, "embedded_script.timed_out", jsonBooleanField(text, "timedOut"));
    putPayload(payload, "embedded_script.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "embedded_script.pending_timers", jsonBooleanField(text, "pendingTimers"));
    putPayload(payload, "embedded_script.pending_timer_count", jsonNumberField(text, "pendingTimerCount"));
    putPayload(payload, "embedded_script.pending_timeout_count", jsonNumberField(text, "pendingTimeoutCount"));
    putPayload(payload, "embedded_script.pending_interval_count", jsonNumberField(text, "pendingIntervalCount"));
    putPayload(payload, "embedded_script.pending_immediate_count", jsonNumberField(text, "pendingImmediateCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_callbacks", jsonNumberField(text, "pendingScopedFsCallbacks"));
    putPayload(payload, "embedded_script.pending_scoped_fs_streams", jsonBooleanField(text, "pendingScopedFsStreams"));
    putPayload(payload, "embedded_script.pending_scoped_fs_stream_count", jsonNumberField(text, "pendingScopedFsStreamCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_watch_count", jsonNumberField(text, "pendingScopedFsWatchCount"));
    putPayload(payload, "embedded_script.pending_scoped_fs_watch_file_count", jsonNumberField(text, "pendingScopedFsWatchFileCount"));
    putPayload(payload, "embedded_script.pending_workers", jsonBooleanField(text, "pendingWorkers"));
    putPayload(payload, "embedded_script.pending_worker_count", jsonNumberField(text, "pendingWorkerCount"));
    putPayload(payload, "embedded_script.pending_crypto_callbacks", jsonNumberField(text, "pendingCryptoCallbacks"));
    putPayload(payload, "embedded_script.pending_dns_callbacks", jsonNumberField(text, "pendingDnsCallbacks"));
    putPayload(payload, "embedded_script.pending_zlib_callbacks", jsonNumberField(text, "pendingZlibCallbacks"));
    putPayload(payload, "embedded_script.pending_node_test_callbacks", jsonNumberField(text, "pendingNodeTestCallbacks"));
    putPayload(payload, "embedded_script.pending_zlib_streams", jsonBooleanField(text, "pendingZlibStreams"));
    putPayload(payload, "embedded_script.pending_zlib_stream_count", jsonNumberField(text, "pendingZlibStreamCount"));
    putPayload(payload, "embedded_script.pending_async_callbacks", jsonNumberField(text, "pendingAsyncCallbacks"));
    putPayload(payload, "embedded_script.pending_callbacks", jsonBooleanField(text, "pendingCallbacks"));
    putPayload(payload, "embedded_script.bridge_requests_json", jsonStringField(text, "bridgeRequestsJson"));
    putPayload(payload, "embedded_script.bridge_request_count", jsonNumberField(text, "bridgeRequestCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_loaded_count", jsonNumberField(text, "dynamicRequireFallbackLoadedCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_source_bytes", jsonNumberField(text, "dynamicRequireFallbackSourceBytes"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_denied_count", jsonNumberField(text, "dynamicRequireFallbackDeniedCount"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_request", jsonStringField(text, "dynamicRequireFallbackLastRequest"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_referrer", jsonStringField(text, "dynamicRequireFallbackLastReferrer"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_candidate", jsonStringField(text, "dynamicRequireFallbackLastCandidate"));
    putPayload(payload, "embedded_script.dynamic_require_fallback_last_denied_reason", jsonStringField(text, "dynamicRequireFallbackLastDeniedReason"));
    putPayload(payload, "embedded_script.package_resolution_package", jsonStringField(text, "packageResolutionPackage"));
    putPayload(payload, "embedded_script.package_resolution_package_json", jsonStringField(text, "packageResolutionPackageJson"));
    putPayload(payload, "embedded_script.package_resolution_field", jsonStringField(text, "packageResolutionField"));
    putPayload(payload, "embedded_script.package_resolution_request", jsonStringField(text, "packageResolutionRequest"));
    putPayload(payload, "embedded_script.package_resolution_target", jsonStringField(text, "packageResolutionTarget"));
    putPayload(payload, "embedded_script.package_resolution_condition_keys", jsonStringField(text, "packageResolutionConditionKeys"));
    putPayload(payload, "embedded_script.package_resolution_selected_condition", jsonStringField(text, "packageResolutionSelectedCondition"));
    putPayload(payload, "embedded_script.package_resolution_ignored_fields", jsonStringField(text, "packageResolutionIgnoredFields"));
    putPayload(payload, "embedded_script.package_resolution_ignored_conditions", jsonStringField(text, "packageResolutionIgnoredConditions"));
    putPayload(payload, "embedded_script.package_resolution_reason", jsonStringField(text, "packageResolutionReason"));
    putPayload(payload, "embedded_script.package_type_module_policy", jsonStringField(text, "packageTypeModulePolicy"));
    putPayload(payload, "embedded_script.package_type_module_package", jsonStringField(text, "packageTypeModulePackage"));
    putPayload(payload, "embedded_script.package_type_module_package_json", jsonStringField(text, "packageTypeModulePackageJson"));
    putPayload(payload, "embedded_script.package_type_module_filename", jsonStringField(text, "packageTypeModuleFilename"));
    putPayload(payload, "embedded_script.esm_enabled", jsonBooleanField(text, "esmEnabled"));
    putPayload(payload, "embedded_script.esm_entry", jsonBooleanField(text, "esmEntry"));
    putPayload(payload, "embedded_script.esm_module_graph_root", jsonStringField(text, "esmModuleGraphRoot"));
    putPayload(payload, "embedded_script.esm_module_graph_size", jsonNumberField(text, "esmModuleGraphSize"));
    putPayload(payload, "embedded_script.esm_module_graph_modules", jsonStringField(text, "esmModuleGraphModules"));
    putPayload(payload, "embedded_script.esm_denied_count", jsonNumberField(text, "esmDeniedCount"));
    putPayload(payload, "embedded_script.esm_last_denied_reason", jsonStringField(text, "esmLastDeniedReason"));
    putPayload(payload, "embedded_script.dynamic_import_enabled", jsonBooleanField(text, "dynamicImportEnabled"));
    putPayload(payload, "embedded_script.worker_threads_enabled", jsonBooleanField(text, "workerThreadsEnabled"));
    putPayload(payload, "embedded_script.worker_active_count", jsonNumberField(text, "workerActiveCount"));
    putPayload(payload, "embedded_script.worker_max_active_count", jsonNumberField(text, "workerMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_created_count", jsonNumberField(text, "workerCreatedCount"));
    putPayload(payload, "embedded_script.worker_terminated_count", jsonNumberField(text, "workerTerminatedCount"));
    putPayload(payload, "embedded_script.worker_startup_timeout_count", jsonNumberField(text, "workerStartupTimeoutCount"));
    putPayload(payload, "embedded_script.worker_rejected_by_policy_count", jsonNumberField(text, "workerRejectedByPolicyCount"));
    putPayload(payload, "embedded_script.worker_cleanup_requested_count", jsonNumberField(text, "workerCleanupRequestedCount"));
    putPayload(payload, "embedded_script.worker_cleanup_terminate_requested_count", jsonNumberField(text, "workerCleanupTerminateRequestedCount"));
    putPayload(payload, "embedded_script.worker_cleanup_error_count", jsonNumberField(text, "workerCleanupErrorCount"));
    putPayload(payload, "embedded_script.worker_cleanup_pending_message_rejected_count", jsonNumberField(text, "workerCleanupPendingMessageRejectedCount"));
    putPayload(payload, "embedded_script.worker_message_port_active_count", jsonNumberField(text, "workerMessagePortActiveCount"));
    putPayload(payload, "embedded_script.worker_message_port_max_active_count", jsonNumberField(text, "workerMessagePortMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_message_port_created_count", jsonNumberField(text, "workerMessagePortCreatedCount"));
    putPayload(payload, "embedded_script.worker_message_port_closed_count", jsonNumberField(text, "workerMessagePortClosedCount"));
    putPayload(payload, "embedded_script.worker_message_rejected_count", jsonNumberField(text, "workerMessageRejectedCount"));
    putPayload(payload, "embedded_script.worker_last_message_rejection", jsonStringField(text, "workerLastMessageRejection"));
    putPayload(payload, "embedded_script.worker_pool_active_count", jsonNumberField(text, "workerPoolActiveCount"));
    putPayload(payload, "embedded_script.worker_pool_max_active_count", jsonNumberField(text, "workerPoolMaxActiveCount"));
    putPayload(payload, "embedded_script.worker_pool_created_count", jsonNumberField(text, "workerPoolCreatedCount"));
    putPayload(payload, "embedded_script.worker_pool_closed_count", jsonNumberField(text, "workerPoolClosedCount"));
    putPayload(payload, "embedded_script.worker_pool_task_completed_count", jsonNumberField(text, "workerPoolTaskCompletedCount"));
    putPayload(payload, "embedded_script.worker_pool_task_cancelled_count", jsonNumberField(text, "workerPoolTaskCancelledCount"));
    putPayload(payload, "embedded_script.worker_pool_task_timeout_count", jsonNumberField(text, "workerPoolTaskTimeoutCount"));
    putPayload(payload, "embedded_script.worker_pool_queue_rejected_count", jsonNumberField(text, "workerPoolQueueRejectedCount"));
    putPayload(payload, "embedded_script.worker_last_policy_rejection", jsonStringField(text, "workerLastPolicyRejection"));
    putPayload(payload, "embedded_script.source_map_enabled", jsonBooleanField(text, "sourceMapEnabled"));
    putPayload(payload, "embedded_script.source_map_loaded_count", jsonNumberField(text, "sourceMapLoadedCount"));
    putPayload(payload, "embedded_script.source_map_mapped_frame_count", jsonNumberField(text, "sourceMapMappedFrameCount"));
    putPayload(payload, "embedded_script.source_map_denied_count", jsonNumberField(text, "sourceMapDeniedCount"));
    putPayload(payload, "embedded_script.source_map_last_source", jsonStringField(text, "sourceMapLastSource"));
    putPayload(payload, "embedded_script.source_map_last_map", jsonStringField(text, "sourceMapLastMap"));
    putPayload(payload, "embedded_script.source_map_last_error", jsonStringField(text, "sourceMapLastError"));
    putPayload(payload, "embedded_script.generated_stack_mapped_frame_count", jsonNumberField(text, "generatedStackMappedFrameCount"));
    putPayload(payload, "embedded_script.generated_stack_last_source", jsonStringField(text, "generatedStackLastSource"));
    putPayload(payload, "embedded_script.generated_stack_last_generated_line", jsonNumberField(text, "generatedStackLastGeneratedLine"));
    putPayload(payload, "embedded_script.generated_stack_last_original_line", jsonNumberField(text, "generatedStackLastOriginalLine"));
    putPayload(payload, "embedded_script.compile_cache.requested", jsonBooleanField(text, "compileCacheRequested"));
    putPayload(payload, "embedded_script.compile_cache.enabled", jsonBooleanField(text, "compileCacheEnabled"));
    putPayload(payload, "embedded_script.compile_cache.status", jsonStringField(text, "compileCacheStatus"));
    putPayload(payload, "embedded_script.compile_cache.status_code", jsonStringField(text, "compileCacheStatusCode"));
    putPayload(payload, "embedded_script.compile_cache.directory", jsonStringField(text, "compileCacheDirectory"));
    putPayload(payload, "embedded_script.compile_cache.key", jsonStringField(text, "compileCacheKey"));
    putPayload(payload, "embedded_script.compile_cache.runtime_key", jsonStringField(text, "compileCacheRuntimeKey"));
    putPayload(payload, "embedded_script.compile_cache.project_key", jsonStringField(text, "compileCacheProjectKey"));
    putPayload(payload, "embedded_script.compile_cache.loader", jsonStringField(text, "compileCacheLoader"));
    putPayload(payload, "embedded_script.compile_cache.schema", jsonStringField(text, "compileCacheSchema"));
    putPayload(payload, "embedded_script.compile_cache.message", jsonStringField(text, "compileCacheMessage"));
    putPayload(payload, "embedded_script.compile_cache.v8_version", jsonStringField(text, "compileCacheV8Version"));
    putPayload(payload, "embedded_script.compile_cache.flush_supported", jsonBooleanField(text, "compileCacheFlushSupported"));
    putPayload(payload, "embedded_script.compile_cache.flush_attempted", jsonBooleanField(text, "compileCacheFlushAttempted"));
    putPayload(payload, "embedded_script.compile_cache.flushed", jsonBooleanField(text, "compileCacheFlushed"));
    putPayload(payload, "embedded_script.compile_cache.custom_loader", jsonBooleanField(text, "compileCacheCustomLoader"));
    putPayload(payload, "embedded_script.compile_cache.hit_count", jsonNumberField(text, "compileCacheHitCount"));
    putPayload(payload, "embedded_script.compile_cache.miss_count", jsonNumberField(text, "compileCacheMissCount"));
    putPayload(payload, "embedded_script.compile_cache.hit_miss_status", jsonStringField(text, "compileCacheHitMissStatus"));
}

void putLifecycleSkippedPayload(
        std::vector<std::string>& payload,
        const char* initializeDetail,
        const char* teardownDetail
) {
    putPayload(payload, "initialize.status", "skipped");
    putPayload(payload, "initialize.detail", initializeDetail);
    putPayload(payload, "teardown.status", "skipped");
    putPayload(payload, "teardown.detail", teardownDetail);
    putPayload(payload, "timing.initialize.ms", static_cast<long long>(0));
    putPayload(payload, "timing.teardown.ms", static_cast<long long>(0));
}

void putIsolateSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "platform.create.status", "skipped");
    putPayload(payload, "platform.create.detail", detail);
    putPayload(payload, "allocator.create.status", "skipped");
    putPayload(payload, "allocator.create.detail", detail);
    putPayload(payload, "isolate.create.status", "skipped");
    putPayload(payload, "isolate.create.detail", detail);
    putPayload(payload, "isolate.dispose.status", "skipped");
    putPayload(payload, "isolate.dispose.detail", detail);
    putPayload(payload, "timing.platform_create.ms", static_cast<long long>(0));
    putPayload(payload, "timing.allocator_create.ms", static_cast<long long>(0));
    putPayload(payload, "timing.isolate_create.ms", static_cast<long long>(0));
    putPayload(payload, "timing.isolate_dispose.ms", static_cast<long long>(0));
}

void putIsolateDataSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "isolate_data.create.status", "skipped");
    putPayload(payload, "isolate_data.create.detail", detail);
    putPayload(payload, "isolate_data.free.status", "skipped");
    putPayload(payload, "isolate_data.free.detail", detail);
    putPayload(payload, "isolate_data.pointer", "null");
    putPayload(payload, "timing.isolate_data_create.ms", static_cast<long long>(0));
    putPayload(payload, "timing.isolate_data_free.ms", static_cast<long long>(0));
}

void putEnvironmentSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "environment.create.status", "skipped");
    putPayload(payload, "environment.create.detail", detail);
    putPayload(payload, "environment.free.status", "skipped");
    putPayload(payload, "environment.free.detail", detail);
    putPayload(payload, "environment.pointer", "null");
    putPayload(payload, "timing.environment_create.ms", static_cast<long long>(0));
    putPayload(payload, "timing.environment_free.ms", static_cast<long long>(0));
}

void putLoadEnvironmentSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "load_environment.status", "skipped");
    putPayload(payload, "load_environment.detail", detail);
    putPayload(payload, "load_environment.result", "skipped");
    putPayload(payload, "timing.load_environment.ms", static_cast<long long>(0));
}

void putSpinEventLoopSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "spin_event_loop.status", "skipped");
    putPayload(payload, "spin_event_loop.detail", detail);
    putPayload(payload, "spin_event_loop.result", "skipped");
    putPayload(payload, "timing.spin_event_loop.ms", static_cast<long long>(0));
}

void putInlineJsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "inline_js.status", "skipped");
    putPayload(payload, "inline_js.detail", detail);
    putPayload(payload, "inline_js.source.length", static_cast<long long>(0));
    putPayload(payload, "inline_js.load_environment.result", "skipped");
    putPayload(payload, "inline_js.spin_event_loop.result", "skipped");
}

void putProcessJsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "process_js.status", "skipped");
    putPayload(payload, "process_js.detail", detail);
    putPayload(payload, "process_js.source.length", static_cast<long long>(0));
    putPayload(payload, "process_js.load_environment.result", "skipped");
    putPayload(payload, "process_js.spin_event_loop.result", "skipped");
}

void putConsoleJsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_js.status", "skipped");
    putPayload(payload, "console_js.detail", detail);
    putPayload(payload, "console_js.source.length", static_cast<long long>(0));
    putPayload(payload, "console_js.load_environment.result", "skipped");
    putPayload(payload, "console_js.spin_event_loop.result", "skipped");
}

void putStdoutCaptureSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "stdout_capture.status", "skipped");
    putPayload(payload, "stdout_capture.detail", detail);
    putPayload(payload, "stdout_capture.bytes", static_cast<long long>(0));
    putPayload(payload, "stdout_capture.text", "");
    putPayload(payload, "stdout_capture.text.max", static_cast<long long>(kStdoutCaptureTextMax));
    putPayload(payload, "stdout_capture.truncated", false);
    putPayload(payload, "stdout_capture.contains_expected", false);
    putPayload(payload, "stderr_capture.bytes", static_cast<long long>(0));
    putPayload(payload, "stderr_capture.text", "");
    putPayload(payload, "stderr_capture.text.max", static_cast<long long>(kStdoutCaptureTextMax));
    putPayload(payload, "stderr_capture.truncated", false);
    putPayload(payload, "timing.stdout_capture.ms", static_cast<long long>(0));
}

void putJsResultSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "js_result.status", "skipped");
    putPayload(payload, "js_result.detail", detail);
    putPayload(payload, "js_result.text", "");
    putPayload(payload, "js_result.contains_version", false);
    putPayload(payload, "js_result.stdout_type", "");
    putPayload(payload, "js_result.stdout_write_type", "");
    putPayload(payload, "js_result.stdout_fd", "");
    putPayload(payload, "js_result.stderr_fd", "");
    putPayload(payload, "js_result.source.length", static_cast<long long>(0));
    putPayload(payload, "js_result.load_environment.result", "skipped");
    putPayload(payload, "js_result.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.js_result.ms", static_cast<long long>(0));
}

void putStdoutWriteSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "stdout_write.status", "skipped");
    putPayload(payload, "stdout_write.detail", detail);
    putPayload(payload, "stdout_write.result_text", "");
    putPayload(payload, "stdout_write.ok", "unknown");
    putPayload(payload, "stdout_write.fd", "");
    putPayload(payload, "stdout_write.write_type", "");
    putPayload(payload, "stdout_write.source.length", static_cast<long long>(0));
    putPayload(payload, "stdout_write.load_environment.result", "skipped");
    putPayload(payload, "stdout_write.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.stdout_write.ms", static_cast<long long>(0));
}

void putConsoleDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_diagnostics.status", "skipped");
    putPayload(payload, "console_diagnostics.detail", detail);
    putPayload(payload, "console_diagnostics.result_text", "");
    putPayload(payload, "console_diagnostics.console_type", "");
    putPayload(payload, "console_diagnostics.console_log_type", "");
    putPayload(payload, "console_diagnostics.stdout_write_type", "");
    putPayload(payload, "console_diagnostics.captured", "");
    putPayload(payload, "console_diagnostics.captured_contains_version", false);
    putPayload(payload, "console_diagnostics.fd", "");
    putPayload(payload, "console_diagnostics.source.length", static_cast<long long>(0));
    putPayload(payload, "console_diagnostics.load_environment.result", "skipped");
    putPayload(payload, "console_diagnostics.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_diagnostics.ms", static_cast<long long>(0));
}

void putConsoleStreamSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_stream.status", "skipped");
    putPayload(payload, "console_stream.detail", detail);
    putPayload(payload, "console_stream.result_text", "");
    putPayload(payload, "console_stream.console_type", "");
    putPayload(payload, "console_stream.console_log_type", "");
    putPayload(payload, "console_stream.console_ctor", "");
    putPayload(payload, "console_stream.has_console_stdout", "");
    putPayload(payload, "console_stream.console_stdout_type", "");
    putPayload(payload, "console_stream.console_stdout_ctor", "");
    putPayload(payload, "console_stream.process_stdout_ctor", "");
    putPayload(payload, "console_stream.before_same_stdout", "");
    putPayload(payload, "console_stream.before_same_write", "");
    putPayload(payload, "console_stream.after_process_patch_same_write", "");
    putPayload(payload, "console_stream.after_console_patch_same_write", "");
    putPayload(payload, "console_stream.process_captured", "");
    putPayload(payload, "console_stream.console_stdout_captured", "");
    putPayload(payload, "console_stream.process_captured_contains_version", false);
    putPayload(payload, "console_stream.console_stdout_captured_contains_version", false);
    putPayload(payload, "console_stream.fd", "");
    putPayload(payload, "console_stream.console_stdout_fd", "");
    putPayload(payload, "console_stream.source.length", static_cast<long long>(0));
    putPayload(payload, "console_stream.load_environment.result", "skipped");
    putPayload(payload, "console_stream.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_stream.ms", static_cast<long long>(0));
}

void putConsoleShapeSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_shape.status", "skipped");
    putPayload(payload, "console_shape.detail", detail);
    putPayload(payload, "console_shape.result_text", "");
    putPayload(payload, "console_shape.console_type", "");
    putPayload(payload, "console_shape.console_ctor", "");
    putPayload(payload, "console_shape.proto_type", "");
    putPayload(payload, "console_shape.proto_ctor", "");
    putPayload(payload, "console_shape.has_own_log", "");
    putPayload(payload, "console_shape.log_type", "");
    putPayload(payload, "console_shape.log_name", "");
    putPayload(payload, "console_shape.log_length", "");
    putPayload(payload, "console_shape.log_equals_proto_log", "");
    putPayload(payload, "console_shape.log_desc", "");
    putPayload(payload, "console_shape.proto_log_desc", "");
    putPayload(payload, "console_shape.own_names", "");
    putPayload(payload, "console_shape.proto_names", "");
    putPayload(payload, "console_shape.own_symbols", "");
    putPayload(payload, "console_shape.proto_symbols", "");
    putPayload(payload, "console_shape.log_string", "");
    putPayload(payload, "console_shape.source.length", static_cast<long long>(0));
    putPayload(payload, "console_shape.load_environment.result", "skipped");
    putPayload(payload, "console_shape.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_shape.ms", static_cast<long long>(0));
}

void putConsoleReplaceSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_replace.status", "skipped");
    putPayload(payload, "console_replace.detail", detail);
    putPayload(payload, "console_replace.result_text", "");
    putPayload(payload, "console_replace.original_log_type", "");
    putPayload(payload, "console_replace.original_log_string", "");
    putPayload(payload, "console_replace.replacement_log_type", "");
    putPayload(payload, "console_replace.replacement_log_string", "");
    putPayload(payload, "console_replace.replacement_name", "");
    putPayload(payload, "console_replace.replacement_length", "");
    putPayload(payload, "console_replace.replacement_writable", "");
    putPayload(payload, "console_replace.replacement_configurable", "");
    putPayload(payload, "console_replace.result", "");
    putPayload(payload, "console_replace.captured", "");
    putPayload(payload, "console_replace.captured_contains_version", false);
    putPayload(payload, "console_replace.fd", "");
    putPayload(payload, "console_replace.write_type", "");
    putPayload(payload, "console_replace.source.length", static_cast<long long>(0));
    putPayload(payload, "console_replace.load_environment.result", "skipped");
    putPayload(payload, "console_replace.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_replace.ms", static_cast<long long>(0));
}

void putConsoleFamilySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_family.status", "skipped");
    putPayload(payload, "console_family.detail", detail);
    putPayload(payload, "console_family.result_text", "");
    putPayload(payload, "console_family.stdout_captured", "");
    putPayload(payload, "console_family.stderr_captured", "");
    putPayload(payload, "console_family.stdout_contains_version", false);
    putPayload(payload, "console_family.stderr_contains_version", false);
    putPayload(payload, "console_family.stdout_fd", "");
    putPayload(payload, "console_family.stderr_fd", "");
    putPayload(payload, "console_family.stdout_write_type", "");
    putPayload(payload, "console_family.stderr_write_type", "");
    putPayload(payload, "console_family.source.length", static_cast<long long>(0));
    putPayload(payload, "console_family.load_environment.result", "skipped");
    putPayload(payload, "console_family.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_family.ms", static_cast<long long>(0));
}

void putConsoleFormatSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_format.status", "skipped");
    putPayload(payload, "console_format.detail", detail);
    putPayload(payload, "console_format.result_text", "");
    putPayload(payload, "console_format.stdout_captured", "");
    putPayload(payload, "console_format.stderr_captured", "");
    putPayload(payload, "console_format.stdout_contains_primitive", false);
    putPayload(payload, "console_format.stdout_contains_object", false);
    putPayload(payload, "console_format.stderr_contains_error", false);
    putPayload(payload, "console_format.stdout_fd", "");
    putPayload(payload, "console_format.stderr_fd", "");
    putPayload(payload, "console_format.source.length", static_cast<long long>(0));
    putPayload(payload, "console_format.load_environment.result", "skipped");
    putPayload(payload, "console_format.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_format.ms", static_cast<long long>(0));
}

void putConsoleRejectionSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_rejection.status", "skipped");
    putPayload(payload, "console_rejection.detail", detail);
    putPayload(payload, "console_rejection.result_text", "");
    putPayload(payload, "console_rejection.stderr_captured", "");
    putPayload(payload, "console_rejection.stderr_contains_caught", false);
    putPayload(payload, "console_rejection.stderr_contains_rejection", false);
    putPayload(payload, "console_rejection.rejection_seen", false);
    putPayload(payload, "console_rejection.stderr_fd", "");
    putPayload(payload, "console_rejection.stderr_write_type", "");
    putPayload(payload, "console_rejection.source.length", static_cast<long long>(0));
    putPayload(payload, "console_rejection.load_environment.result", "skipped");
    putPayload(payload, "console_rejection.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_rejection.ms", static_cast<long long>(0));
}

void putConsoleUncaughtSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "console_uncaught.status", "skipped");
    putPayload(payload, "console_uncaught.detail", detail);
    putPayload(payload, "console_uncaught.result_text", "");
    putPayload(payload, "console_uncaught.stderr_captured", "");
    putPayload(payload, "console_uncaught.stderr_contains_uncaught", false);
    putPayload(payload, "console_uncaught.uncaught_seen", false);
    putPayload(payload, "console_uncaught.stderr_fd", "");
    putPayload(payload, "console_uncaught.stderr_write_type", "");
    putPayload(payload, "console_uncaught.source.length", static_cast<long long>(0));
    putPayload(payload, "console_uncaught.load_environment.result", "skipped");
    putPayload(payload, "console_uncaught.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.console_uncaught.ms", static_cast<long long>(0));
}

void putSchedulingSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "scheduling.status", "skipped");
    putPayload(payload, "scheduling.detail", detail);
    putPayload(payload, "scheduling.result_text", "");
    putPayload(payload, "scheduling.contains_version", false);
    putPayload(payload, "scheduling.set_timeout_type", "");
    putPayload(payload, "scheduling.clear_timeout_type", "");
    putPayload(payload, "scheduling.set_interval_type", "");
    putPayload(payload, "scheduling.clear_interval_type", "");
    putPayload(payload, "scheduling.set_immediate_type", "");
    putPayload(payload, "scheduling.clear_immediate_type", "");
    putPayload(payload, "scheduling.queue_microtask_type", "");
    putPayload(payload, "scheduling.process_next_tick_type", "");
    putPayload(payload, "scheduling.promise_type", "");
    putPayload(payload, "scheduling.stdout_write_type", "");
    putPayload(payload, "scheduling.stderr_write_type", "");
    putPayload(payload, "scheduling.source.length", static_cast<long long>(0));
    putPayload(payload, "scheduling.load_environment.result", "skipped");
    putPayload(payload, "scheduling.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.scheduling.ms", static_cast<long long>(0));
}

void putSchedulingOrderSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "scheduling_order.status", "skipped");
    putPayload(payload, "scheduling_order.detail", detail);
    putPayload(payload, "scheduling_order.result_text", "");
    putPayload(payload, "scheduling_order.events", "");
    putPayload(payload, "scheduling_order.has_next_tick", false);
    putPayload(payload, "scheduling_order.has_promise", false);
    putPayload(payload, "scheduling_order.has_set_immediate", false);
    putPayload(payload, "scheduling_order.contains_version", false);
    putPayload(payload, "scheduling_order.contains_next_tick", false);
    putPayload(payload, "scheduling_order.contains_promise", false);
    putPayload(payload, "scheduling_order.contains_set_immediate", false);
    putPayload(payload, "scheduling_order.source.length", static_cast<long long>(0));
    putPayload(payload, "scheduling_order.load_environment.result", "skipped");
    putPayload(payload, "scheduling_order.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.scheduling_order.ms", static_cast<long long>(0));
}

void putAsyncConsoleSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "async_console.status", "skipped");
    putPayload(payload, "async_console.detail", detail);
    putPayload(payload, "async_console.result_text", "");
    putPayload(payload, "async_console.events", "");
    putPayload(payload, "async_console.stdout_captured", "");
    putPayload(payload, "async_console.stderr_captured", "");
    putPayload(payload, "async_console.stdout_contains_sync", false);
    putPayload(payload, "async_console.stdout_contains_async", false);
    putPayload(payload, "async_console.stderr_contains_sync", false);
    putPayload(payload, "async_console.stderr_contains_async", false);
    putPayload(payload, "async_console.stdout_fd", "");
    putPayload(payload, "async_console.stderr_fd", "");
    putPayload(payload, "async_console.stdout_write_type", "");
    putPayload(payload, "async_console.stderr_write_type", "");
    putPayload(payload, "async_console.contains_version", false);
    putPayload(payload, "async_console.source.length", static_cast<long long>(0));
    putPayload(payload, "async_console.load_environment.result", "skipped");
    putPayload(payload, "async_console.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.async_console.ms", static_cast<long long>(0));
}

void putAsyncErrorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "async_error.status", "skipped");
    putPayload(payload, "async_error.detail", detail);
    putPayload(payload, "async_error.result_text", "");
    putPayload(payload, "async_error.events", "");
    putPayload(payload, "async_error.stderr_captured", "");
    putPayload(payload, "async_error.stderr_contains_uncaught", false);
    putPayload(payload, "async_error.stderr_contains_rejection", false);
    putPayload(payload, "async_error.uncaught_seen", false);
    putPayload(payload, "async_error.rejection_seen", false);
    putPayload(payload, "async_error.stderr_fd", "");
    putPayload(payload, "async_error.stderr_write_type", "");
    putPayload(payload, "async_error.contains_version", false);
    putPayload(payload, "async_error.source.length", static_cast<long long>(0));
    putPayload(payload, "async_error.load_environment.result", "skipped");
    putPayload(payload, "async_error.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.async_error.ms", static_cast<long long>(0));
}

void putBootstrapSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "bootstrap.status", "skipped");
    putPayload(payload, "bootstrap.detail", detail);
    putPayload(payload, "bootstrap.result_text", "");
    putPayload(payload, "bootstrap.events", "");
    putPayload(payload, "bootstrap.installed", false);
    putPayload(payload, "bootstrap.console_installed", false);
    putPayload(payload, "bootstrap.error_handlers_installed", false);
    putPayload(payload, "bootstrap.stdout_captured", "");
    putPayload(payload, "bootstrap.stderr_captured", "");
    putPayload(payload, "bootstrap.stdout_contains_sync", false);
    putPayload(payload, "bootstrap.stdout_contains_async", false);
    putPayload(payload, "bootstrap.stderr_contains_sync", false);
    putPayload(payload, "bootstrap.stderr_contains_async", false);
    putPayload(payload, "bootstrap.stderr_contains_rejection", false);
    putPayload(payload, "bootstrap.stderr_contains_uncaught", false);
    putPayload(payload, "bootstrap.rejection_seen", false);
    putPayload(payload, "bootstrap.uncaught_seen", false);
    putPayload(payload, "bootstrap.final_result_writer_seen", false);
    putPayload(payload, "bootstrap.stdout_fd", "");
    putPayload(payload, "bootstrap.stderr_fd", "");
    putPayload(payload, "bootstrap.stdout_write_type", "");
    putPayload(payload, "bootstrap.stderr_write_type", "");
    putPayload(payload, "bootstrap.contains_version", false);
    putPayload(payload, "bootstrap.source.length", static_cast<long long>(0));
    putPayload(payload, "bootstrap.load_environment.result", "skipped");
    putPayload(payload, "bootstrap.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.bootstrap.ms", static_cast<long long>(0));
}

void putBootstrapScriptSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "bootstrap_script.status", "skipped");
    putPayload(payload, "bootstrap_script.detail", detail);
    putPayload(payload, "bootstrap_script.result_text", "");
    putPayload(payload, "bootstrap_script.events", "");
    putPayload(payload, "bootstrap_script.bootstrap_installed", false);
    putPayload(payload, "bootstrap_script.script_started", false);
    putPayload(payload, "bootstrap_script.script_completed", false);
    putPayload(payload, "bootstrap_script.stdout_captured", "");
    putPayload(payload, "bootstrap_script.stderr_captured", "");
    putPayload(payload, "bootstrap_script.stdout_contains_script_sync", false);
    putPayload(payload, "bootstrap_script.stdout_contains_script_async", false);
    putPayload(payload, "bootstrap_script.stderr_contains_script_sync", false);
    putPayload(payload, "bootstrap_script.stderr_contains_script_async", false);
    putPayload(payload, "bootstrap_script.stderr_contains_rejection", false);
    putPayload(payload, "bootstrap_script.stderr_contains_uncaught", false);
    putPayload(payload, "bootstrap_script.rejection_seen", false);
    putPayload(payload, "bootstrap_script.uncaught_seen", false);
    putPayload(payload, "bootstrap_script.final_result_writer_seen", false);
    putPayload(payload, "bootstrap_script.contains_version", false);
    putPayload(payload, "bootstrap_script.source.length", static_cast<long long>(0));
    putPayload(payload, "bootstrap_script.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "bootstrap_script.script_source.length", static_cast<long long>(0));
    putPayload(payload, "bootstrap_script.load_environment.result", "skipped");
    putPayload(payload, "bootstrap_script.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.bootstrap_script.ms", static_cast<long long>(0));
}

void putScriptCompletionSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_completion.status", "skipped");
    putPayload(payload, "script_completion.detail", detail);
    putPayload(payload, "script_completion.result_text", "");
    putPayload(payload, "script_completion.started", false);
    putPayload(payload, "script_completion.completed", false);
    putPayload(payload, "script_completion.failed", false);
    putPayload(payload, "script_completion.exit_code", "");
    putPayload(payload, "script_completion.value", "");
    putPayload(payload, "script_completion.events", "");
    putPayload(payload, "script_completion.stdout_captured", "");
    putPayload(payload, "script_completion.stderr_captured", "");
    putPayload(payload, "script_completion.stdout_contains_sync", false);
    putPayload(payload, "script_completion.stdout_contains_async", false);
    putPayload(payload, "script_completion.stderr_contains_error", false);
    putPayload(payload, "script_completion.contains_version", false);
    putPayload(payload, "script_completion.source.length", static_cast<long long>(0));
    putPayload(payload, "script_completion.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_completion.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_completion.load_environment.result", "skipped");
    putPayload(payload, "script_completion.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_completion.ms", static_cast<long long>(0));
}

void putScriptFailureSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_failure.status", "skipped");
    putPayload(payload, "script_failure.detail", detail);
    putPayload(payload, "script_failure.result_text", "");
    putPayload(payload, "script_failure.started", false);
    putPayload(payload, "script_failure.completed", false);
    putPayload(payload, "script_failure.failed", false);
    putPayload(payload, "script_failure.exit_code", "");
    putPayload(payload, "script_failure.value", "");
    putPayload(payload, "script_failure.error_message", "");
    putPayload(payload, "script_failure.error_stack", "");
    putPayload(payload, "script_failure.events", "");
    putPayload(payload, "script_failure.stdout_captured", "");
    putPayload(payload, "script_failure.stderr_captured", "");
    putPayload(payload, "script_failure.stdout_contains_sync", false);
    putPayload(payload, "script_failure.stderr_contains_error", false);
    putPayload(payload, "script_failure.contains_version", false);
    putPayload(payload, "script_failure.source.length", static_cast<long long>(0));
    putPayload(payload, "script_failure.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_failure.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_failure.load_environment.result", "skipped");
    putPayload(payload, "script_failure.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_failure.ms", static_cast<long long>(0));
}

void putScriptCancelSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_cancel.status", "skipped");
    putPayload(payload, "script_cancel.detail", detail);
    putPayload(payload, "script_cancel.result_text", "");
    putPayload(payload, "script_cancel.started", false);
    putPayload(payload, "script_cancel.completed", false);
    putPayload(payload, "script_cancel.failed", false);
    putPayload(payload, "script_cancel.cancelled", false);
    putPayload(payload, "script_cancel.exit_code", "");
    putPayload(payload, "script_cancel.cancel_reason", "");
    putPayload(payload, "script_cancel.after_cancel_seen", false);
    putPayload(payload, "script_cancel.cancel_observed", false);
    putPayload(payload, "script_cancel.events", "");
    putPayload(payload, "script_cancel.stdout_captured", "");
    putPayload(payload, "script_cancel.stderr_captured", "");
    putPayload(payload, "script_cancel.stdout_contains_sync", false);
    putPayload(payload, "script_cancel.stderr_contains_cancel", false);
    putPayload(payload, "script_cancel.contains_version", false);
    putPayload(payload, "script_cancel.source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancel.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancel.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancel.load_environment.result", "skipped");
    putPayload(payload, "script_cancel.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_cancel.ms", static_cast<long long>(0));
}

void putCapabilityDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "capability_descriptor.status", "skipped");
    putPayload(payload, "capability_descriptor.detail", detail);
    putPayload(payload, "capability_descriptor.result_text", "");
    putPayload(payload, "capability_descriptor.node_version", "");
    putPayload(payload, "capability_descriptor.embedded", false);
    putPayload(payload, "capability_descriptor.one_shot", false);
    putPayload(payload, "capability_descriptor.abi", "");
    putPayload(payload, "capability_descriptor.has_process_version", false);
    putPayload(payload, "capability_descriptor.has_stdout_write", false);
    putPayload(payload, "capability_descriptor.has_stderr_write", false);
    putPayload(payload, "capability_descriptor.has_console_shim", false);
    putPayload(payload, "capability_descriptor.has_set_immediate", false);
    putPayload(payload, "capability_descriptor.has_promise", false);
    putPayload(payload, "capability_descriptor.has_next_tick", false);
    putPayload(payload, "capability_descriptor.has_set_timeout", false);
    putPayload(payload, "capability_descriptor.has_set_interval", false);
    putPayload(payload, "capability_descriptor.has_queue_microtask", false);
    putPayload(payload, "capability_descriptor.supports_output_events", false);
    putPayload(payload, "capability_descriptor.supports_output_envelope", false);
    putPayload(payload, "capability_descriptor.supports_script_completion", false);
    putPayload(payload, "capability_descriptor.supports_script_failure", false);
    putPayload(payload, "capability_descriptor.supports_script_cancellation", false);
    putPayload(payload, "capability_descriptor.supports_post_completion_error", false);
    putPayload(payload, "capability_descriptor.supports_require", false);
    putPayload(payload, "capability_descriptor.supports_npm", false);
    putPayload(payload, "capability_descriptor.supports_user_script", false);
    putPayload(payload, "capability_descriptor.supports_autojs_api", false);
    putPayload(payload, "capability_descriptor.supports_android_bridge", false);
    putPayload(payload, "capability_descriptor.source.length", static_cast<long long>(0));
    putPayload(payload, "capability_descriptor.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "capability_descriptor.script_source.length", static_cast<long long>(0));
    putPayload(payload, "capability_descriptor.load_environment.result", "skipped");
    putPayload(payload, "capability_descriptor.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.capability_descriptor.ms", static_cast<long long>(0));
}

void putPluginContractSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_contract.status", "skipped");
    putPayload(payload, "plugin_contract.detail", detail);
    putPayload(payload, "plugin_contract.result_text", "");
    putPayload(payload, "plugin_contract.protocol_version", "");
    putPayload(payload, "plugin_contract.runtime_id", "");
    putPayload(payload, "plugin_contract.runtime_kind", "");
    putPayload(payload, "plugin_contract.runtime_version", "");
    putPayload(payload, "plugin_contract.embedded", false);
    putPayload(payload, "plugin_contract.plugin_ready", false);
    putPayload(payload, "plugin_contract.supports_probe", false);
    putPayload(payload, "plugin_contract.supports_run_script", false);
    putPayload(payload, "plugin_contract.supports_cancel", false);
    putPayload(payload, "plugin_contract.supports_dispose", false);
    putPayload(payload, "plugin_contract.event_stdout", false);
    putPayload(payload, "plugin_contract.event_stderr", false);
    putPayload(payload, "plugin_contract.event_output_envelope", false);
    putPayload(payload, "plugin_contract.event_completed", false);
    putPayload(payload, "plugin_contract.event_failed", false);
    putPayload(payload, "plugin_contract.event_cancelled", false);
    putPayload(payload, "plugin_contract.event_post_completion_error", false);
    putPayload(payload, "plugin_contract.policy_isolated_process", false);
    putPayload(payload, "plugin_contract.policy_drain_after_complete", false);
    putPayload(payload, "plugin_contract.policy_post_completion_error_does_not_fail", false);
    putPayload(payload, "plugin_contract.policy_default_cancellation_exit_code", "");
    putPayload(payload, "plugin_contract.unsupported_require", false);
    putPayload(payload, "plugin_contract.unsupported_npm", false);
    putPayload(payload, "plugin_contract.unsupported_user_file", false);
    putPayload(payload, "plugin_contract.unsupported_autojs_api", false);
    putPayload(payload, "plugin_contract.unsupported_android_bridge", false);
    putPayload(payload, "plugin_contract.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_contract.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_contract.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_contract.load_environment.result", "skipped");
    putPayload(payload, "plugin_contract.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_contract.ms", static_cast<long long>(0));
}

void putPluginRequestSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_request.status", "skipped");
    putPayload(payload, "plugin_request.detail", detail);
    putPayload(payload, "plugin_request.result_text", "");
    putPayload(payload, "plugin_request.request_id", "");
    putPayload(payload, "plugin_request.method", "");
    putPayload(payload, "plugin_request.response_id", "");
    putPayload(payload, "plugin_request.response_ok", false);
    putPayload(payload, "plugin_request.runtime", "");
    putPayload(payload, "plugin_request.node_version", "");
    putPayload(payload, "plugin_request.cancel_request_id", "");
    putPayload(payload, "plugin_request.cancel_response_ok", false);
    putPayload(payload, "plugin_request.cancelled", false);
    putPayload(payload, "plugin_request.cancel_exit_code", "");
    putPayload(payload, "plugin_request.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_request.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_request.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_request.load_environment.result", "skipped");
    putPayload(payload, "plugin_request.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_request.ms", static_cast<long long>(0));
}

void putPluginEventsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_events.status", "skipped");
    putPayload(payload, "plugin_events.detail", detail);
    putPayload(payload, "plugin_events.result_text", "");
    putPayload(payload, "plugin_events.request_id", "");
    putPayload(payload, "plugin_events.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_events.sequence_monotonic", false);
    putPayload(payload, "plugin_events.has_stdout_event", false);
    putPayload(payload, "plugin_events.has_stderr_event", false);
    putPayload(payload, "plugin_events.has_completed_event", false);
    putPayload(payload, "plugin_events.has_cancelled_event", false);
    putPayload(payload, "plugin_events.has_diagnostic_event", false);
    putPayload(payload, "plugin_events.stdout_event_text", "");
    putPayload(payload, "plugin_events.stderr_event_text", "");
    putPayload(payload, "plugin_events.completed_exit_code", "");
    putPayload(payload, "plugin_events.cancelled_exit_code", "");
    putPayload(payload, "plugin_events.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_events.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_events.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_events.load_environment.result", "skipped");
    putPayload(payload, "plugin_events.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_events.ms", static_cast<long long>(0));
}

void putPluginFailureSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_failure.status", "skipped");
    putPayload(payload, "plugin_failure.detail", detail);
    putPayload(payload, "plugin_failure.result_text", "");
    putPayload(payload, "plugin_failure.request_id", "");
    putPayload(payload, "plugin_failure.method", "");
    putPayload(payload, "plugin_failure.response_id", "");
    putPayload(payload, "plugin_failure.response_ok", false);
    putPayload(payload, "plugin_failure.error_code", "");
    putPayload(payload, "plugin_failure.error_message", "");
    putPayload(payload, "plugin_failure.error_stack", "");
    putPayload(payload, "plugin_failure.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_failure.has_failed_event", false);
    putPayload(payload, "plugin_failure.has_stderr_event", false);
    putPayload(payload, "plugin_failure.failed_exit_code", "");
    putPayload(payload, "plugin_failure.stderr_event_text", "");
    putPayload(payload, "plugin_failure.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_failure.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_failure.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_failure.load_environment.result", "skipped");
    putPayload(payload, "plugin_failure.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_failure.ms", static_cast<long long>(0));
}

void putPluginCancelSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_cancel.status", "skipped");
    putPayload(payload, "plugin_cancel.detail", detail);
    putPayload(payload, "plugin_cancel.result_text", "");
    putPayload(payload, "plugin_cancel.request_id", "");
    putPayload(payload, "plugin_cancel.method", "");
    putPayload(payload, "plugin_cancel.reason", "");
    putPayload(payload, "plugin_cancel.response_id", "");
    putPayload(payload, "plugin_cancel.response_ok", false);
    putPayload(payload, "plugin_cancel.cancelled", false);
    putPayload(payload, "plugin_cancel.exit_code", "");
    putPayload(payload, "plugin_cancel.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_cancel.has_diagnostic_event", false);
    putPayload(payload, "plugin_cancel.has_cancelled_event", false);
    putPayload(payload, "plugin_cancel.cancelled_event_exit_code", "");
    putPayload(payload, "plugin_cancel.cancelled_event_reason", "");
    putPayload(payload, "plugin_cancel.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_cancel.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_cancel.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_cancel.load_environment.result", "skipped");
    putPayload(payload, "plugin_cancel.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_cancel.ms", static_cast<long long>(0));
}

void putPluginDisposeSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_dispose.status", "skipped");
    putPayload(payload, "plugin_dispose.detail", detail);
    putPayload(payload, "plugin_dispose.result_text", "");
    putPayload(payload, "plugin_dispose.request_id", "");
    putPayload(payload, "plugin_dispose.method", "");
    putPayload(payload, "plugin_dispose.response_id", "");
    putPayload(payload, "plugin_dispose.response_ok", false);
    putPayload(payload, "plugin_dispose.disposed", false);
    putPayload(payload, "plugin_dispose.runtime", "");
    putPayload(payload, "plugin_dispose.node_version", "");
    putPayload(payload, "plugin_dispose.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_dispose.sequence_monotonic", false);
    putPayload(payload, "plugin_dispose.has_diagnostic_event", false);
    putPayload(payload, "plugin_dispose.has_disposed_event", false);
    putPayload(payload, "plugin_dispose.disposed_event_runtime", "");
    putPayload(payload, "plugin_dispose.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_dispose.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_dispose.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_dispose.load_environment.result", "skipped");
    putPayload(payload, "plugin_dispose.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_dispose.ms", static_cast<long long>(0));
}

void putPluginLifecycleSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_lifecycle.status", "skipped");
    putPayload(payload, "plugin_lifecycle.detail", detail);
    putPayload(payload, "plugin_lifecycle.result_text", "");
    putPayload(payload, "plugin_lifecycle.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_lifecycle.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_lifecycle.has_probe_response", false);
    putPayload(payload, "plugin_lifecycle.has_run_completed_event", false);
    putPayload(payload, "plugin_lifecycle.has_cancel_response", false);
    putPayload(payload, "plugin_lifecycle.has_cancelled_event", false);
    putPayload(payload, "plugin_lifecycle.has_dispose_response", false);
    putPayload(payload, "plugin_lifecycle.has_disposed_event", false);
    putPayload(payload, "plugin_lifecycle.sequence_monotonic", false);
    putPayload(payload, "plugin_lifecycle.final_disposed", false);
    putPayload(payload, "plugin_lifecycle.node_version", "");
    putPayload(payload, "plugin_lifecycle.probe_response_runtime", "");
    putPayload(payload, "plugin_lifecycle.completed_exit_code", "");
    putPayload(payload, "plugin_lifecycle.cancelled_exit_code", "");
    putPayload(payload, "plugin_lifecycle.disposed_event_runtime", "");
    putPayload(payload, "plugin_lifecycle.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_lifecycle.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_lifecycle.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_lifecycle.load_environment.result", "skipped");
    putPayload(payload, "plugin_lifecycle.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_lifecycle.ms", static_cast<long long>(0));
}

void putPluginInvalidRequestSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_invalid_request.status", "skipped");
    putPayload(payload, "plugin_invalid_request.detail", detail);
    putPayload(payload, "plugin_invalid_request.result_text", "");
    putPayload(payload, "plugin_invalid_request.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_invalid_request.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_invalid_request.has_unknown_method_error", false);
    putPayload(payload, "plugin_invalid_request.unknown_method_error_code", "");
    putPayload(payload, "plugin_invalid_request.has_missing_id_error", false);
    putPayload(payload, "plugin_invalid_request.missing_id_error_code", "");
    putPayload(payload, "plugin_invalid_request.has_malformed_params_error", false);
    putPayload(payload, "plugin_invalid_request.malformed_params_error_code", "");
    putPayload(payload, "plugin_invalid_request.has_after_dispose_error", false);
    putPayload(payload, "plugin_invalid_request.after_dispose_error_code", "");
    putPayload(payload, "plugin_invalid_request.all_errors_have_message", false);
    putPayload(payload, "plugin_invalid_request.all_errors_have_code", false);
    putPayload(payload, "plugin_invalid_request.sequence_monotonic", false);
    putPayload(payload, "plugin_invalid_request.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_invalid_request.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_invalid_request.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_invalid_request.load_environment.result", "skipped");
    putPayload(payload, "plugin_invalid_request.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_invalid_request.ms", static_cast<long long>(0));
}

void putPluginStateMachineSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_state_machine.status", "skipped");
    putPayload(payload, "plugin_state_machine.detail", detail);
    putPayload(payload, "plugin_state_machine.result_text", "");
    putPayload(payload, "plugin_state_machine.state_count", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.transition_count", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.created_probe_allowed", false);
    putPayload(payload, "plugin_state_machine.created_cancel_rejected", false);
    putPayload(payload, "plugin_state_machine.created_dispose_allowed", false);
    putPayload(payload, "plugin_state_machine.ready_run_allowed", false);
    putPayload(payload, "plugin_state_machine.running_run_rejected", false);
    putPayload(payload, "plugin_state_machine.running_cancel_allowed", false);
    putPayload(payload, "plugin_state_machine.completed_cancel_rejected", false);
    putPayload(payload, "plugin_state_machine.completed_dispose_allowed", false);
    putPayload(payload, "plugin_state_machine.cancelled_dispose_allowed", false);
    putPayload(payload, "plugin_state_machine.disposed_probe_rejected", false);
    putPayload(payload, "plugin_state_machine.disposed_run_rejected", false);
    putPayload(payload, "plugin_state_machine.disposed_cancel_rejected", false);
    putPayload(payload, "plugin_state_machine.disposed_dispose_idempotent", false);
    putPayload(payload, "plugin_state_machine.has_invalid_state_error", false);
    putPayload(payload, "plugin_state_machine.invalid_state_error_code", "");
    putPayload(payload, "plugin_state_machine.sequence_monotonic", false);
    putPayload(payload, "plugin_state_machine.final_state", "");
    putPayload(payload, "plugin_state_machine.node_version", "");
    putPayload(payload, "plugin_state_machine.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_state_machine.load_environment.result", "skipped");
    putPayload(payload, "plugin_state_machine.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_state_machine.ms", static_cast<long long>(0));
}

void putPluginTimeoutSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_timeout.status", "skipped");
    putPayload(payload, "plugin_timeout.detail", detail);
    putPayload(payload, "plugin_timeout.result_text", "");
    putPayload(payload, "plugin_timeout.request_id", "");
    putPayload(payload, "plugin_timeout.method", "");
    putPayload(payload, "plugin_timeout.timeout_ms", "");
    putPayload(payload, "plugin_timeout.response_ok", false);
    putPayload(payload, "plugin_timeout.error_code", "");
    putPayload(payload, "plugin_timeout.error_message_contains_timeout", false);
    putPayload(payload, "plugin_timeout.has_timeout_event", false);
    putPayload(payload, "plugin_timeout.timeout_event_exit_code", "");
    putPayload(payload, "plugin_timeout.timeout_event_reason", "");
    putPayload(payload, "plugin_timeout.has_dispose_after_timeout", false);
    putPayload(payload, "plugin_timeout.dispose_after_timeout_ok", false);
    putPayload(payload, "plugin_timeout.state_before_timeout", "");
    putPayload(payload, "plugin_timeout.state_after_timeout", "");
    putPayload(payload, "plugin_timeout.final_state", "");
    putPayload(payload, "plugin_timeout.sequence_monotonic", false);
    putPayload(payload, "plugin_timeout.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.node_version", "");
    putPayload(payload, "plugin_timeout.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_timeout.load_environment.result", "skipped");
    putPayload(payload, "plugin_timeout.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_timeout.ms", static_cast<long long>(0));
}

void putPluginCrashSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_crash.status", "skipped");
    putPayload(payload, "plugin_crash.detail", detail);
    putPayload(payload, "plugin_crash.result_text", "");
    putPayload(payload, "plugin_crash.request_id", "");
    putPayload(payload, "plugin_crash.method", "");
    putPayload(payload, "plugin_crash.state_before_crash", "");
    putPayload(payload, "plugin_crash.state_after_crash", "");
    putPayload(payload, "plugin_crash.final_state", "");
    putPayload(payload, "plugin_crash.response_ok", false);
    putPayload(payload, "plugin_crash.error_code", "");
    putPayload(payload, "plugin_crash.error_message_contains_crash", false);
    putPayload(payload, "plugin_crash.has_crash_event", false);
    putPayload(payload, "plugin_crash.crash_event_reason", "");
    putPayload(payload, "plugin_crash.crash_event_exit_code", "");
    putPayload(payload, "plugin_crash.has_pending_request_failure", false);
    putPayload(payload, "plugin_crash.pending_request_error_code", "");
    putPayload(payload, "plugin_crash.dispose_after_crash_ok", false);
    putPayload(payload, "plugin_crash.dispose_after_crash_error_code", "");
    putPayload(payload, "plugin_crash.sequence_monotonic", false);
    putPayload(payload, "plugin_crash.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.node_version", "");
    putPayload(payload, "plugin_crash.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_crash.load_environment.result", "skipped");
    putPayload(payload, "plugin_crash.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_crash.ms", static_cast<long long>(0));
}

void putPluginBackpressureSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_backpressure.status", "skipped");
    putPayload(payload, "plugin_backpressure.detail", detail);
    putPayload(payload, "plugin_backpressure.result_text", "");
    putPayload(payload, "plugin_backpressure.request_id", "");
    putPayload(payload, "plugin_backpressure.method", "");
    putPayload(payload, "plugin_backpressure.output_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.stdout_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.stderr_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.queue_soft_limit", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.backpressure_triggered", false);
    putPayload(payload, "plugin_backpressure.has_backpressure_event", false);
    putPayload(payload, "plugin_backpressure.backpressure_event_level", "");
    putPayload(payload, "plugin_backpressure.dropped_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.summary_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.has_summary_event", false);
    putPayload(payload, "plugin_backpressure.summary_contains_dropped_count", false);
    putPayload(payload, "plugin_backpressure.has_completed_event", false);
    putPayload(payload, "plugin_backpressure.completed_exit_code", "");
    putPayload(payload, "plugin_backpressure.final_state", "");
    putPayload(payload, "plugin_backpressure.sequence_monotonic", false);
    putPayload(payload, "plugin_backpressure.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.node_version", "");
    putPayload(payload, "plugin_backpressure.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_backpressure.load_environment.result", "skipped");
    putPayload(payload, "plugin_backpressure.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_backpressure.ms", static_cast<long long>(0));
}

void putPluginNegotiationSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_negotiation.status", "skipped");
    putPayload(payload, "plugin_negotiation.detail", detail);
    putPayload(payload, "plugin_negotiation.result_text", "");
    putPayload(payload, "plugin_negotiation.request_id", "");
    putPayload(payload, "plugin_negotiation.method", "");
    putPayload(payload, "plugin_negotiation.host_versions", "");
    putPayload(payload, "plugin_negotiation.plugin_versions", "");
    putPayload(payload, "plugin_negotiation.selected_version", "");
    putPayload(payload, "plugin_negotiation.compatible", false);
    putPayload(payload, "plugin_negotiation.host_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.plugin_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.negotiated_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.has_run_script", false);
    putPayload(payload, "plugin_negotiation.has_cancel", false);
    putPayload(payload, "plugin_negotiation.has_dispose", false);
    putPayload(payload, "plugin_negotiation.has_output_events", false);
    putPayload(payload, "plugin_negotiation.has_backpressure", false);
    putPayload(payload, "plugin_negotiation.has_crash_events", false);
    putPayload(payload, "plugin_negotiation.rejected_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.has_rejected_require", false);
    putPayload(payload, "plugin_negotiation.has_rejected_npm", false);
    putPayload(payload, "plugin_negotiation.has_rejected_android_bridge", false);
    putPayload(payload, "plugin_negotiation.has_rejected_autojs_api", false);
    putPayload(payload, "plugin_negotiation.final_state", "");
    putPayload(payload, "plugin_negotiation.sequence_monotonic", false);
    putPayload(payload, "plugin_negotiation.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.node_version", "");
    putPayload(payload, "plugin_negotiation.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_negotiation.load_environment.result", "skipped");
    putPayload(payload, "plugin_negotiation.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_negotiation.ms", static_cast<long long>(0));
}

void putPluginPermissionSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_permission.status", "skipped");
    putPayload(payload, "plugin_permission.detail", detail);
    putPayload(payload, "plugin_permission.result_text", "");
    putPayload(payload, "plugin_permission.requested_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.granted_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.denied_capability_count", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.has_granted_run_script", false);
    putPayload(payload, "plugin_permission.has_granted_output_events", false);
    putPayload(payload, "plugin_permission.has_granted_cancel", false);
    putPayload(payload, "plugin_permission.has_granted_dispose", false);
    putPayload(payload, "plugin_permission.has_denied_require", false);
    putPayload(payload, "plugin_permission.has_denied_npm", false);
    putPayload(payload, "plugin_permission.has_denied_android_bridge", false);
    putPayload(payload, "plugin_permission.has_denied_autojs_api", false);
    putPayload(payload, "plugin_permission.denied_error_code", "");
    putPayload(payload, "plugin_permission.final_state", "");
    putPayload(payload, "plugin_permission.sequence_monotonic", false);
    putPayload(payload, "plugin_permission.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.node_version", "");
    putPayload(payload, "plugin_permission.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_permission.load_environment.result", "skipped");
    putPayload(payload, "plugin_permission.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_permission.ms", static_cast<long long>(0));
}

void putPluginManifestSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_manifest.status", "skipped");
    putPayload(payload, "plugin_manifest.detail", detail);
    putPayload(payload, "plugin_manifest.result_text", "");
    putPayload(payload, "plugin_manifest.valid_manifest_ok", false);
    putPayload(payload, "plugin_manifest.checked_manifest_count", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.accepted_count", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.rejected_count", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.has_missing_runtime_id_error", false);
    putPayload(payload, "plugin_manifest.missing_runtime_id_error_code", "");
    putPayload(payload, "plugin_manifest.has_unsupported_protocol_error", false);
    putPayload(payload, "plugin_manifest.unsupported_protocol_error_code", "");
    putPayload(payload, "plugin_manifest.has_invalid_capability_error", false);
    putPayload(payload, "plugin_manifest.invalid_capability_error_code", "");
    putPayload(payload, "plugin_manifest.has_invalid_entrypoint_error", false);
    putPayload(payload, "plugin_manifest.invalid_entrypoint_error_code", "");
    putPayload(payload, "plugin_manifest.all_errors_have_code", false);
    putPayload(payload, "plugin_manifest.all_errors_have_message", false);
    putPayload(payload, "plugin_manifest.final_state", "");
    putPayload(payload, "plugin_manifest.sequence_monotonic", false);
    putPayload(payload, "plugin_manifest.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.node_version", "");
    putPayload(payload, "plugin_manifest.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_manifest.load_environment.result", "skipped");
    putPayload(payload, "plugin_manifest.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_manifest.ms", static_cast<long long>(0));
}

void putPluginAbiSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_abi.status", "skipped");
    putPayload(payload, "plugin_abi.detail", detail);
    putPayload(payload, "plugin_abi.result_text", "");
    putPayload(payload, "plugin_abi.host_abis", "");
    putPayload(payload, "plugin_abi.plugin_abis", "");
    putPayload(payload, "plugin_abi.intersection", "");
    putPayload(payload, "plugin_abi.selected_abi", "");
    putPayload(payload, "plugin_abi.compatible", false);
    putPayload(payload, "plugin_abi.unsupported_abi_count", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.has_unsupported_x86", false);
    putPayload(payload, "plugin_abi.no_intersection_case_checked", false);
    putPayload(payload, "plugin_abi.no_intersection_error_code", "");
    putPayload(payload, "plugin_abi.final_state", "");
    putPayload(payload, "plugin_abi.sequence_monotonic", false);
    putPayload(payload, "plugin_abi.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.node_version", "");
    putPayload(payload, "plugin_abi.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_abi.load_environment.result", "skipped");
    putPayload(payload, "plugin_abi.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_abi.ms", static_cast<long long>(0));
}

void putPluginLibrarySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_library.status", "skipped");
    putPayload(payload, "plugin_library.detail", detail);
    putPayload(payload, "plugin_library.result_text", "");
    putPayload(payload, "plugin_library.required_library_count", static_cast<long long>(0));
    putPayload(payload, "plugin_library.resolved_library_count", static_cast<long long>(0));
    putPayload(payload, "plugin_library.has_libnode", false);
    putPayload(payload, "plugin_library.libnode_name", "");
    putPayload(payload, "plugin_library.libnode_version", "");
    putPayload(payload, "plugin_library.has_bridge", false);
    putPayload(payload, "plugin_library.bridge_name", "");
    putPayload(payload, "plugin_library.missing_required_case_checked", false);
    putPayload(payload, "plugin_library.missing_required_error_code", "");
    putPayload(payload, "plugin_library.version_mismatch_case_checked", false);
    putPayload(payload, "plugin_library.version_mismatch_error_code", "");
    putPayload(payload, "plugin_library.resolution_ok", false);
    putPayload(payload, "plugin_library.final_state", "");
    putPayload(payload, "plugin_library.sequence_monotonic", false);
    putPayload(payload, "plugin_library.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_library.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_library.node_version", "");
    putPayload(payload, "plugin_library.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_library.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_library.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_library.load_environment.result", "skipped");
    putPayload(payload, "plugin_library.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_library.ms", static_cast<long long>(0));
}

void putPluginInstanceSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_instance.status", "skipped");
    putPayload(payload, "plugin_instance.detail", detail);
    putPayload(payload, "plugin_instance.result_text", "");
    putPayload(payload, "plugin_instance.runtime_id", "");
    putPayload(payload, "plugin_instance.session_id", "");
    putPayload(payload, "plugin_instance.instance_created", false);
    putPayload(payload, "plugin_instance.instance_state", "");
    putPayload(payload, "plugin_instance.has_manifest", false);
    putPayload(payload, "plugin_instance.has_selected_abi", false);
    putPayload(payload, "plugin_instance.has_resolved_libraries", false);
    putPayload(payload, "plugin_instance.has_negotiated_protocol", false);
    putPayload(payload, "plugin_instance.has_permission_policy", false);
    putPayload(payload, "plugin_instance.create_response_ok", false);
    putPayload(payload, "plugin_instance.final_state", "");
    putPayload(payload, "plugin_instance.sequence_monotonic", false);
    putPayload(payload, "plugin_instance.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_instance.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_instance.node_version", "");
    putPayload(payload, "plugin_instance.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_instance.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_instance.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_instance.load_environment.result", "skipped");
    putPayload(payload, "plugin_instance.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_instance.ms", static_cast<long long>(0));
}

void putPluginSessionSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_session.status", "skipped");
    putPayload(payload, "plugin_session.detail", detail);
    putPayload(payload, "plugin_session.result_text", "");
    putPayload(payload, "plugin_session.session_id", "");
    putPayload(payload, "plugin_session.registered", false);
    putPayload(payload, "plugin_session.lookup_ok", false);
    putPayload(payload, "plugin_session.duplicate_rejected", false);
    putPayload(payload, "plugin_session.duplicate_error_code", "");
    putPayload(payload, "plugin_session.disposed", false);
    putPayload(payload, "plugin_session.lookup_after_dispose_rejected", false);
    putPayload(payload, "plugin_session.lookup_after_dispose_error_code", "");
    putPayload(payload, "plugin_session.active_session_count_after_register", static_cast<long long>(0));
    putPayload(payload, "plugin_session.active_session_count_after_dispose", static_cast<long long>(0));
    putPayload(payload, "plugin_session.final_state", "");
    putPayload(payload, "plugin_session.sequence_monotonic", false);
    putPayload(payload, "plugin_session.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_session.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_session.node_version", "");
    putPayload(payload, "plugin_session.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_session.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_session.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_session.load_environment.result", "skipped");
    putPayload(payload, "plugin_session.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_session.ms", static_cast<long long>(0));
}

void putPluginQueueSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_queue.status", "skipped");
    putPayload(payload, "plugin_queue.detail", detail);
    putPayload(payload, "plugin_queue.result_text", "");
    putPayload(payload, "plugin_queue.session_id", "");
    putPayload(payload, "plugin_queue.request_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.accepted_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.queued_count_initial", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.started_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.completed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.queue_drained", false);
    putPayload(payload, "plugin_queue.max_queue_size", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.final_queue_size", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.final_state", "");
    putPayload(payload, "plugin_queue.sequence_monotonic", false);
    putPayload(payload, "plugin_queue.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.node_version", "");
    putPayload(payload, "plugin_queue.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_queue.load_environment.result", "skipped");
    putPayload(payload, "plugin_queue.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_queue.ms", static_cast<long long>(0));
}

void putPluginConcurrencySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_concurrency.status", "skipped");
    putPayload(payload, "plugin_concurrency.detail", detail);
    putPayload(payload, "plugin_concurrency.result_text", "");
    putPayload(payload, "plugin_concurrency.session_id", "");
    putPayload(payload, "plugin_concurrency.first_run_accepted", false);
    putPayload(payload, "plugin_concurrency.second_run_rejected", false);
    putPayload(payload, "plugin_concurrency.second_run_error_code", "");
    putPayload(payload, "plugin_concurrency.cancel_while_running_accepted", false);
    putPayload(payload, "plugin_concurrency.run_after_cancel_accepted", false);
    putPayload(payload, "plugin_concurrency.run_after_completion_accepted", false);
    putPayload(payload, "plugin_concurrency.max_concurrent_runs", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.final_running_count", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.final_state", "");
    putPayload(payload, "plugin_concurrency.sequence_monotonic", false);
    putPayload(payload, "plugin_concurrency.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.node_version", "");
    putPayload(payload, "plugin_concurrency.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_concurrency.load_environment.result", "skipped");
    putPayload(payload, "plugin_concurrency.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_concurrency.ms", static_cast<long long>(0));
}

void putPluginRaceSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_race.status", "skipped");
    putPayload(payload, "plugin_race.detail", detail);
    putPayload(payload, "plugin_race.result_text", "");
    putPayload(payload, "plugin_race.session_id", "");
    putPayload(payload, "plugin_race.request_id", "");
    putPayload(payload, "plugin_race.run_accepted", false);
    putPayload(payload, "plugin_race.cancel_requested", false);
    putPayload(payload, "plugin_race.completion_requested", false);
    putPayload(payload, "plugin_race.terminal_policy", "");
    putPayload(payload, "plugin_race.completed_emitted", false);
    putPayload(payload, "plugin_race.cancelled_emitted", false);
    putPayload(payload, "plugin_race.terminal_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_race.no_double_finalization", false);
    putPayload(payload, "plugin_race.cancel_after_terminal_rejected", false);
    putPayload(payload, "plugin_race.cancel_after_terminal_error_code", "");
    putPayload(payload, "plugin_race.final_state", "");
    putPayload(payload, "plugin_race.sequence_monotonic", false);
    putPayload(payload, "plugin_race.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_race.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_race.node_version", "");
    putPayload(payload, "plugin_race.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_race.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_race.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_race.load_environment.result", "skipped");
    putPayload(payload, "plugin_race.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_race.ms", static_cast<long long>(0));
}

void putPluginShutdownSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_shutdown.status", "skipped");
    putPayload(payload, "plugin_shutdown.detail", detail);
    putPayload(payload, "plugin_shutdown.result_text", "");
    putPayload(payload, "plugin_shutdown.session_id", "");
    putPayload(payload, "plugin_shutdown.running_before_shutdown", false);
    putPayload(payload, "plugin_shutdown.shutdown_requested", false);
    putPayload(payload, "plugin_shutdown.new_request_rejected", false);
    putPayload(payload, "plugin_shutdown.new_request_error_code", "");
    putPayload(payload, "plugin_shutdown.running_script_cancelled", false);
    putPayload(payload, "plugin_shutdown.cancel_exit_code", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.dispose_emitted", false);
    putPayload(payload, "plugin_shutdown.shutdown_completed", false);
    putPayload(payload, "plugin_shutdown.final_state", "");
    putPayload(payload, "plugin_shutdown.sequence_monotonic", false);
    putPayload(payload, "plugin_shutdown.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.node_version", "");
    putPayload(payload, "plugin_shutdown.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_shutdown.load_environment.result", "skipped");
    putPayload(payload, "plugin_shutdown.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_shutdown.ms", static_cast<long long>(0));
}

void putPluginHeartbeatSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_heartbeat.status", "skipped");
    putPayload(payload, "plugin_heartbeat.detail", detail);
    putPayload(payload, "plugin_heartbeat.result_text", "");
    putPayload(payload, "plugin_heartbeat.session_id", "");
    putPayload(payload, "plugin_heartbeat.ping_count", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.pong_count", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.missed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.timeout_ms", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.has_heartbeat_event", false);
    putPayload(payload, "plugin_heartbeat.has_missed_heartbeat_event", false);
    putPayload(payload, "plugin_heartbeat.degraded", false);
    putPayload(payload, "plugin_heartbeat.final_state", "");
    putPayload(payload, "plugin_heartbeat.sequence_monotonic", false);
    putPayload(payload, "plugin_heartbeat.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.node_version", "");
    putPayload(payload, "plugin_heartbeat.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_heartbeat.load_environment.result", "skipped");
    putPayload(payload, "plugin_heartbeat.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_heartbeat.ms", static_cast<long long>(0));
}

void putPluginHealthSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_health.status", "skipped");
    putPayload(payload, "plugin_health.detail", detail);
    putPayload(payload, "plugin_health.result_text", "");
    putPayload(payload, "plugin_health.runtime_id", "");
    putPayload(payload, "plugin_health.session_count", static_cast<long long>(0));
    putPayload(payload, "plugin_health.active_session_count", static_cast<long long>(0));
    putPayload(payload, "plugin_health.queue_size", static_cast<long long>(0));
    putPayload(payload, "plugin_health.running_count", static_cast<long long>(0));
    putPayload(payload, "plugin_health.state", "");
    putPayload(payload, "plugin_health.degraded_reason", "");
    putPayload(payload, "plugin_health.last_error_code", "");
    putPayload(payload, "plugin_health.has_health_event", false);
    putPayload(payload, "plugin_health.summary_ok", false);
    putPayload(payload, "plugin_health.final_state", "");
    putPayload(payload, "plugin_health.sequence_monotonic", false);
    putPayload(payload, "plugin_health.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_health.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_health.node_version", "");
    putPayload(payload, "plugin_health.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_health.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_health.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_health.load_environment.result", "skipped");
    putPayload(payload, "plugin_health.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_health.ms", static_cast<long long>(0));
}

void putPluginTelemetrySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_telemetry.status", "skipped");
    putPayload(payload, "plugin_telemetry.detail", detail);
    putPayload(payload, "plugin_telemetry.result_text", "");
    putPayload(payload, "plugin_telemetry.runtime_id", "");
    putPayload(payload, "plugin_telemetry.session_id", "");
    putPayload(payload, "plugin_telemetry.state", "");
    putPayload(payload, "plugin_telemetry.protocol_version", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.session_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.active_session_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.disposed_session_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.queue_size", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.running_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.completed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.cancelled_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.failed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.timed_out_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.crashed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.output_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.stdout_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.stderr_event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.dropped_output_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.backpressure_detected", false);
    putPayload(payload, "plugin_telemetry.heartbeat_ping_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.heartbeat_pong_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.heartbeat_missed_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.degraded", false);
    putPayload(payload, "plugin_telemetry.degraded_reason", "");
    putPayload(payload, "plugin_telemetry.last_error_code", "");
    putPayload(payload, "plugin_telemetry.snapshot_ok", false);
    putPayload(payload, "plugin_telemetry.final_state", "");
    putPayload(payload, "plugin_telemetry.sequence_monotonic", false);
    putPayload(payload, "plugin_telemetry.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.node_version", "");
    putPayload(payload, "plugin_telemetry.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_telemetry.load_environment.result", "skipped");
    putPayload(payload, "plugin_telemetry.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_telemetry.ms", static_cast<long long>(0));
}

void putPluginDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_diagnostics.status", "skipped");
    putPayload(payload, "plugin_diagnostics.detail", detail);
    putPayload(payload, "plugin_diagnostics.result_text", "");
    putPayload(payload, "plugin_diagnostics.bundle_id", "");
    putPayload(payload, "plugin_diagnostics.runtime_id", "");
    putPayload(payload, "plugin_diagnostics.session_id", "");
    putPayload(payload, "plugin_diagnostics.includes_manifest", false);
    putPayload(payload, "plugin_diagnostics.includes_protocol", false);
    putPayload(payload, "plugin_diagnostics.includes_permissions", false);
    putPayload(payload, "plugin_diagnostics.includes_abi", false);
    putPayload(payload, "plugin_diagnostics.includes_libraries", false);
    putPayload(payload, "plugin_diagnostics.includes_instance", false);
    putPayload(payload, "plugin_diagnostics.includes_session", false);
    putPayload(payload, "plugin_diagnostics.includes_queue", false);
    putPayload(payload, "plugin_diagnostics.includes_health", false);
    putPayload(payload, "plugin_diagnostics.includes_telemetry", false);
    putPayload(payload, "plugin_diagnostics.section_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.error_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.warning_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.info_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.has_last_error", false);
    putPayload(payload, "plugin_diagnostics.last_error_code", "");
    putPayload(payload, "plugin_diagnostics.has_degraded_reason", false);
    putPayload(payload, "plugin_diagnostics.degraded_reason", "");
    putPayload(payload, "plugin_diagnostics.redacted_sensitive_fields", false);
    putPayload(payload, "plugin_diagnostics.bundle_serializable", false);
    putPayload(payload, "plugin_diagnostics.bundle_size_bytes", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.summary_ok", false);
    putPayload(payload, "plugin_diagnostics.final_state", "");
    putPayload(payload, "plugin_diagnostics.sequence_monotonic", false);
    putPayload(payload, "plugin_diagnostics.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.node_version", "");
    putPayload(payload, "plugin_diagnostics.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_diagnostics.load_environment.result", "skipped");
    putPayload(payload, "plugin_diagnostics.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_diagnostics.ms", static_cast<long long>(0));
}

void putPluginRecoverySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_recovery.status", "skipped");
    putPayload(payload, "plugin_recovery.detail", detail);
    putPayload(payload, "plugin_recovery.result_text", "");
    putPayload(payload, "plugin_recovery.runtime_id", "");
    putPayload(payload, "plugin_recovery.session_id", "");
    putPayload(payload, "plugin_recovery.input_state", "");
    putPayload(payload, "plugin_recovery.input_error_code", "");
    putPayload(payload, "plugin_recovery.input_degraded_reason", "");
    putPayload(payload, "plugin_recovery.plan_id", "");
    putPayload(payload, "plugin_recovery.plan_generated", false);
    putPayload(payload, "plugin_recovery.action_count", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.has_cancel_action", false);
    putPayload(payload, "plugin_recovery.has_drain_queue_action", false);
    putPayload(payload, "plugin_recovery.has_dispose_action", false);
    putPayload(payload, "plugin_recovery.has_restart_action", false);
    putPayload(payload, "plugin_recovery.cancel_action_order", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.drain_queue_action_order", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.dispose_action_order", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.restart_action_order", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.retryable", false);
    putPayload(payload, "plugin_recovery.requires_new_process", false);
    putPayload(payload, "plugin_recovery.preserve_session", false);
    putPayload(payload, "plugin_recovery.preserve_diagnostics", false);
    putPayload(payload, "plugin_recovery.recovery_reason", "");
    putPayload(payload, "plugin_recovery.recommended_policy", "");
    putPayload(payload, "plugin_recovery.final_state", "");
    putPayload(payload, "plugin_recovery.sequence_monotonic", false);
    putPayload(payload, "plugin_recovery.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.node_version", "");
    putPayload(payload, "plugin_recovery.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_recovery.load_environment.result", "skipped");
    putPayload(payload, "plugin_recovery.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_recovery.ms", static_cast<long long>(0));
}

void putPluginRestartSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_restart.status", "skipped");
    putPayload(payload, "plugin_restart.detail", detail);
    putPayload(payload, "plugin_restart.result_text", "");
    putPayload(payload, "plugin_restart.runtime_id", "");
    putPayload(payload, "plugin_restart.previous_session_id", "");
    putPayload(payload, "plugin_restart.next_session_id", "");
    putPayload(payload, "plugin_restart.policy", "");
    putPayload(payload, "plugin_restart.reason", "");
    putPayload(payload, "plugin_restart.previous_state", "");
    putPayload(payload, "plugin_restart.next_state", "");
    putPayload(payload, "plugin_restart.max_attempts", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.attempt_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.backoff_ms", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.backoff_policy", "");
    putPayload(payload, "plugin_restart.jitter_enabled", false);
    putPayload(payload, "plugin_restart.previous_runtime_disposed", false);
    putPayload(payload, "plugin_restart.new_runtime_created", false);
    putPayload(payload, "plugin_restart.session_recreated", false);
    putPayload(payload, "plugin_restart.queue_reset", false);
    putPayload(payload, "plugin_restart.diagnostics_preserved", false);
    putPayload(payload, "plugin_restart.restart_allowed", false);
    putPayload(payload, "plugin_restart.restart_scheduled", false);
    putPayload(payload, "plugin_restart.restart_executed", false);
    putPayload(payload, "plugin_restart.execution_mode", "");
    putPayload(payload, "plugin_restart.final_state", "");
    putPayload(payload, "plugin_restart.sequence_monotonic", false);
    putPayload(payload, "plugin_restart.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.node_version", "");
    putPayload(payload, "plugin_restart.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart.load_environment.result", "skipped");
    putPayload(payload, "plugin_restart.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_restart.ms", static_cast<long long>(0));
}

void putPluginRestartBudgetSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_restart_budget.status", "skipped");
    putPayload(payload, "plugin_restart_budget.detail", detail);
    putPayload(payload, "plugin_restart_budget.result_text", "");
    putPayload(payload, "plugin_restart_budget.runtime_id", "");
    putPayload(payload, "plugin_restart_budget.session_id", "");
    putPayload(payload, "plugin_restart_budget.policy", "");
    putPayload(payload, "plugin_restart_budget.max_attempts", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.attempt_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.remaining_attempts", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.window_ms", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.first_failure_reason", "");
    putPayload(payload, "plugin_restart_budget.last_failure_reason", "");
    putPayload(payload, "plugin_restart_budget.last_error_code", "");
    putPayload(payload, "plugin_restart_budget.restart_allowed", false);
    putPayload(payload, "plugin_restart_budget.circuit_open", false);
    putPayload(payload, "plugin_restart_budget.circuit_reason", "");
    putPayload(payload, "plugin_restart_budget.next_retry_after_ms", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.diagnostics_preserved", false);
    putPayload(payload, "plugin_restart_budget.requires_manual_intervention", false);
    putPayload(payload, "plugin_restart_budget.final_state", "");
    putPayload(payload, "plugin_restart_budget.sequence_monotonic", false);
    putPayload(payload, "plugin_restart_budget.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.node_version", "");
    putPayload(payload, "plugin_restart_budget.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_restart_budget.load_environment.result", "skipped");
    putPayload(payload, "plugin_restart_budget.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_restart_budget.ms", static_cast<long long>(0));
}

void putPluginQuarantineSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "plugin_quarantine.status", "skipped");
    putPayload(payload, "plugin_quarantine.detail", detail);
    putPayload(payload, "plugin_quarantine.result_text", "");
    putPayload(payload, "plugin_quarantine.runtime_id", "");
    putPayload(payload, "plugin_quarantine.session_id", "");
    putPayload(payload, "plugin_quarantine.input_state", "");
    putPayload(payload, "plugin_quarantine.input_error_code", "");
    putPayload(payload, "plugin_quarantine.input_exit_code", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.quarantine_id", "");
    putPayload(payload, "plugin_quarantine.quarantined", false);
    putPayload(payload, "plugin_quarantine.reason", "");
    putPayload(payload, "plugin_quarantine.scope", "");
    putPayload(payload, "plugin_quarantine.restart_allowed", false);
    putPayload(payload, "plugin_quarantine.new_requests_allowed", false);
    putPayload(payload, "plugin_quarantine.active_sessions_terminated", false);
    putPayload(payload, "plugin_quarantine.pending_requests_failed", false);
    putPayload(payload, "plugin_quarantine.queue_drained", false);
    putPayload(payload, "plugin_quarantine.diagnostics_preserved", false);
    putPayload(payload, "plugin_quarantine.crash_report_attached", false);
    putPayload(payload, "plugin_quarantine.manual_clear_required", false);
    putPayload(payload, "plugin_quarantine.final_state", "");
    putPayload(payload, "plugin_quarantine.sequence_monotonic", false);
    putPayload(payload, "plugin_quarantine.event_count", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.response_count", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.node_version", "");
    putPayload(payload, "plugin_quarantine.source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.script_source.length", static_cast<long long>(0));
    putPayload(payload, "plugin_quarantine.load_environment.result", "skipped");
    putPayload(payload, "plugin_quarantine.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.plugin_quarantine.ms", static_cast<long long>(0));
}

void putScriptDescriptorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_descriptor.status", "skipped");
    putPayload(payload, "script_descriptor.detail", detail);
    putPayload(payload, "script_descriptor.result_text", "");
    putPayload(payload, "script_descriptor.descriptor_id", "");
    putPayload(payload, "script_descriptor.source_type", "");
    putPayload(payload, "script_descriptor.path", "");
    putPayload(payload, "script_descriptor.working_directory", "");
    putPayload(payload, "script_descriptor.file_name", "");
    putPayload(payload, "script_descriptor.extension", "");
    putPayload(payload, "script_descriptor.has_valid_path", false);
    putPayload(payload, "script_descriptor.has_valid_extension", false);
    putPayload(payload, "script_descriptor.has_working_directory", false);
    putPayload(payload, "script_descriptor.has_entrypoint", false);
    putPayload(payload, "script_descriptor.argv_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.env_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.encoding", "");
    putPayload(payload, "script_descriptor.accepted_descriptor_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.rejected_descriptor_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.reject_missing_path_error_code", "");
    putPayload(payload, "script_descriptor.reject_invalid_extension_error_code", "");
    putPayload(payload, "script_descriptor.reject_missing_working_directory_error_code", "");
    putPayload(payload, "script_descriptor.reject_invalid_encoding_error_code", "");
    putPayload(payload, "script_descriptor.validation_ok", false);
    putPayload(payload, "script_descriptor.final_state", "");
    putPayload(payload, "script_descriptor.sequence_monotonic", false);
    putPayload(payload, "script_descriptor.event_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.response_count", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.node_version", "");
    putPayload(payload, "script_descriptor.source.length", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_descriptor.load_environment.result", "skipped");
    putPayload(payload, "script_descriptor.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_descriptor.ms", static_cast<long long>(0));
}

void putScriptNormalizationSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_normalization.status", "skipped");
    putPayload(payload, "script_normalization.detail", detail);
    putPayload(payload, "script_normalization.result_text", "");
    putPayload(payload, "script_normalization.descriptor_id", "");
    putPayload(payload, "script_normalization.input_source_type", "");
    putPayload(payload, "script_normalization.normalized_source_type", "");
    putPayload(payload, "script_normalization.normalized_file_name", "");
    putPayload(payload, "script_normalization.normalized_url", "");
    putPayload(payload, "script_normalization.normalized_cwd", "");
    putPayload(payload, "script_normalization.normalized_encoding", "");
    putPayload(payload, "script_normalization.has_shebang", false);
    putPayload(payload, "script_normalization.shebang_stripped", false);
    putPayload(payload, "script_normalization.has_bom", false);
    putPayload(payload, "script_normalization.bom_stripped", false);
    putPayload(payload, "script_normalization.line_ending_input", "");
    putPayload(payload, "script_normalization.line_ending_normalized", "");
    putPayload(payload, "script_normalization.source_length_before", static_cast<long long>(0));
    putPayload(payload, "script_normalization.source_length_after", static_cast<long long>(0));
    putPayload(payload, "script_normalization.source_hash", "");
    putPayload(payload, "script_normalization.source_map_url_preserved", false);
    putPayload(payload, "script_normalization.stack_trace_url_ready", false);
    putPayload(payload, "script_normalization.normalization_ok", false);
    putPayload(payload, "script_normalization.final_state", "");
    putPayload(payload, "script_normalization.sequence_monotonic", false);
    putPayload(payload, "script_normalization.event_count", static_cast<long long>(0));
    putPayload(payload, "script_normalization.response_count", static_cast<long long>(0));
    putPayload(payload, "script_normalization.node_version", "");
    putPayload(payload, "script_normalization.source.length", static_cast<long long>(0));
    putPayload(payload, "script_normalization.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_normalization.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_normalization.load_environment.result", "skipped");
    putPayload(payload, "script_normalization.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_normalization.ms", static_cast<long long>(0));
}

void putScriptContextSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_context.status", "skipped");
    putPayload(payload, "script_context.detail", detail);
    putPayload(payload, "script_context.result_text", "");
    putPayload(payload, "script_context.context_id", "");
    putPayload(payload, "script_context.descriptor_id", "");
    putPayload(payload, "script_context.runtime_id", "");
    putPayload(payload, "script_context.session_id", "");
    putPayload(payload, "script_context.cwd", "");
    putPayload(payload, "script_context.argv_count", static_cast<long long>(0));
    putPayload(payload, "script_context.env_count", static_cast<long long>(0));
    putPayload(payload, "script_context.has_process_argv", false);
    putPayload(payload, "script_context.has_process_env", false);
    putPayload(payload, "script_context.has_process_cwd", false);
    putPayload(payload, "script_context.timeout_ms", static_cast<long long>(0));
    putPayload(payload, "script_context.memory_limit_mb", static_cast<long long>(0));
    putPayload(payload, "script_context.cancellation_token_id", "");
    putPayload(payload, "script_context.stdout_mode", "");
    putPayload(payload, "script_context.stderr_mode", "");
    putPayload(payload, "script_context.output_backpressure_policy", "");
    putPayload(payload, "script_context.require_policy", "");
    putPayload(payload, "script_context.npm_policy", "");
    putPayload(payload, "script_context.android_bridge_policy", "");
    putPayload(payload, "script_context.autojs_api_policy", "");
    putPayload(payload, "script_context.context_created", false);
    putPayload(payload, "script_context.context_valid", false);
    putPayload(payload, "script_context.final_state", "");
    putPayload(payload, "script_context.sequence_monotonic", false);
    putPayload(payload, "script_context.event_count", static_cast<long long>(0));
    putPayload(payload, "script_context.response_count", static_cast<long long>(0));
    putPayload(payload, "script_context.node_version", "");
    putPayload(payload, "script_context.source.length", static_cast<long long>(0));
    putPayload(payload, "script_context.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_context.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_context.load_environment.result", "skipped");
    putPayload(payload, "script_context.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_context.ms", static_cast<long long>(0));
}

void putScriptResultV2SkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_result_v2.status", "skipped");
    putPayload(payload, "script_result_v2.detail", detail);
    putPayload(payload, "script_result_v2.result_text", "");
    putPayload(payload, "script_result_v2.result_id", "");
    putPayload(payload, "script_result_v2.request_id", "");
    putPayload(payload, "script_result_v2.context_id", "");
    putPayload(payload, "script_result_v2.exit_code", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.signal", "");
    putPayload(payload, "script_result_v2.reason", "");
    putPayload(payload, "script_result_v2.has_value", false);
    putPayload(payload, "script_result_v2.value_type", "");
    putPayload(payload, "script_result_v2.value_preview", "");
    putPayload(payload, "script_result_v2.has_error", false);
    putPayload(payload, "script_result_v2.error_code", "");
    putPayload(payload, "script_result_v2.error_name", "");
    putPayload(payload, "script_result_v2.error_message", "");
    putPayload(payload, "script_result_v2.stack_available", false);
    putPayload(payload, "script_result_v2.stdout_event_count", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.stderr_event_count", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.output_dropped_count", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.started_at_ms", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.completed_at_ms", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.duration_ms", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.completed_event_emitted", false);
    putPayload(payload, "script_result_v2.result_serializable", false);
    putPayload(payload, "script_result_v2.final_state", "");
    putPayload(payload, "script_result_v2.sequence_monotonic", false);
    putPayload(payload, "script_result_v2.event_count", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.response_count", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.node_version", "");
    putPayload(payload, "script_result_v2.source.length", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_result_v2.load_environment.result", "skipped");
    putPayload(payload, "script_result_v2.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_result_v2.ms", static_cast<long long>(0));
}

void putScriptCancellationTokenSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_cancellation_token.status", "skipped");
    putPayload(payload, "script_cancellation_token.detail", detail);
    putPayload(payload, "script_cancellation_token.result_text", "");
    putPayload(payload, "script_cancellation_token.token_id", "");
    putPayload(payload, "script_cancellation_token.request_id", "");
    putPayload(payload, "script_cancellation_token.context_id", "");
    putPayload(payload, "script_cancellation_token.created", false);
    putPayload(payload, "script_cancellation_token.initial_state", "");
    putPayload(payload, "script_cancellation_token.cancel_requested", false);
    putPayload(payload, "script_cancellation_token.cancel_reason", "");
    putPayload(payload, "script_cancellation_token.cancel_signal", "");
    putPayload(payload, "script_cancellation_token.cancel_exit_code", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.observed_by_runtime", false);
    putPayload(payload, "script_cancellation_token.observed_by_script", false);
    putPayload(payload, "script_cancellation_token.terminal_event_emitted", false);
    putPayload(payload, "script_cancellation_token.cancelled_event_emitted", false);
    putPayload(payload, "script_cancellation_token.request_after_cancel_allowed", false);
    putPayload(payload, "script_cancellation_token.request_after_cancel_error_code", "");
    putPayload(payload, "script_cancellation_token.idempotent_cancel", false);
    putPayload(payload, "script_cancellation_token.second_cancel_ignored", false);
    putPayload(payload, "script_cancellation_token.final_state", "");
    putPayload(payload, "script_cancellation_token.sequence_monotonic", false);
    putPayload(payload, "script_cancellation_token.event_count", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.response_count", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.node_version", "");
    putPayload(payload, "script_cancellation_token.source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_cancellation_token.load_environment.result", "skipped");
    putPayload(payload, "script_cancellation_token.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_cancellation_token.ms", static_cast<long long>(0));
}

void putScriptTimeoutPolicySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_timeout_policy.status", "skipped");
    putPayload(payload, "script_timeout_policy.detail", detail);
    putPayload(payload, "script_timeout_policy.result_text", "");
    putPayload(payload, "script_timeout_policy.policy_id", "");
    putPayload(payload, "script_timeout_policy.request_id", "");
    putPayload(payload, "script_timeout_policy.context_id", "");
    putPayload(payload, "script_timeout_policy.timeout_ms", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.grace_period_ms", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.hard_kill_after_ms", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.timer_created", false);
    putPayload(payload, "script_timeout_policy.timer_started", false);
    putPayload(payload, "script_timeout_policy.timeout_detected", false);
    putPayload(payload, "script_timeout_policy.timeout_reason", "");
    putPayload(payload, "script_timeout_policy.cancel_requested", false);
    putPayload(payload, "script_timeout_policy.cancel_exit_code", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.timeout_event_emitted", false);
    putPayload(payload, "script_timeout_policy.result_error_code", "");
    putPayload(payload, "script_timeout_policy.dispose_after_timeout", false);
    putPayload(payload, "script_timeout_policy.restart_required", false);
    putPayload(payload, "script_timeout_policy.diagnostics_preserved", false);
    putPayload(payload, "script_timeout_policy.final_state", "");
    putPayload(payload, "script_timeout_policy.sequence_monotonic", false);
    putPayload(payload, "script_timeout_policy.event_count", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.response_count", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.node_version", "");
    putPayload(payload, "script_timeout_policy.source.length", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_timeout_policy.load_environment.result", "skipped");
    putPayload(payload, "script_timeout_policy.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_timeout_policy.ms", static_cast<long long>(0));
}

void putScriptMemoryPolicySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_memory_policy.status", "skipped");
    putPayload(payload, "script_memory_policy.detail", detail);
    putPayload(payload, "script_memory_policy.result_text", "");
    putPayload(payload, "script_memory_policy.policy_id", "");
    putPayload(payload, "script_memory_policy.request_id", "");
    putPayload(payload, "script_memory_policy.context_id", "");
    putPayload(payload, "script_memory_policy.max_old_space_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.max_heap_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.soft_limit_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.hard_limit_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.memory_snapshot_enabled", false);
    putPayload(payload, "script_memory_policy.initial_heap_used_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.peak_heap_used_mb", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.limit_exceeded", false);
    putPayload(payload, "script_memory_policy.oom_error_code", "");
    putPayload(payload, "script_memory_policy.oom_exit_code", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.dispose_on_oom", false);
    putPayload(payload, "script_memory_policy.restart_required_on_oom", false);
    putPayload(payload, "script_memory_policy.diagnostics_preserved", false);
    putPayload(payload, "script_memory_policy.policy_valid", false);
    putPayload(payload, "script_memory_policy.final_state", "");
    putPayload(payload, "script_memory_policy.sequence_monotonic", false);
    putPayload(payload, "script_memory_policy.event_count", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.response_count", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.node_version", "");
    putPayload(payload, "script_memory_policy.source.length", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_memory_policy.load_environment.result", "skipped");
    putPayload(payload, "script_memory_policy.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_memory_policy.ms", static_cast<long long>(0));
}

void putScriptPreflightSummarySkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "script_preflight_summary.status", "skipped");
    putPayload(payload, "script_preflight_summary.detail", detail);
    putPayload(payload, "script_preflight_summary.result_text", "");
    putPayload(payload, "script_preflight_summary.summary_id", "");
    putPayload(payload, "script_preflight_summary.request_id", "");
    putPayload(payload, "script_preflight_summary.has_descriptor", false);
    putPayload(payload, "script_preflight_summary.has_normalized_source", false);
    putPayload(payload, "script_preflight_summary.has_context", false);
    putPayload(payload, "script_preflight_summary.has_result_envelope", false);
    putPayload(payload, "script_preflight_summary.has_cancellation_token", false);
    putPayload(payload, "script_preflight_summary.has_timeout_policy", false);
    putPayload(payload, "script_preflight_summary.has_memory_policy", false);
    putPayload(payload, "script_preflight_summary.descriptor_valid", false);
    putPayload(payload, "script_preflight_summary.source_normalized", false);
    putPayload(payload, "script_preflight_summary.context_valid", false);
    putPayload(payload, "script_preflight_summary.result_envelope_ready", false);
    putPayload(payload, "script_preflight_summary.cancellation_ready", false);
    putPayload(payload, "script_preflight_summary.timeout_policy_ready", false);
    putPayload(payload, "script_preflight_summary.memory_policy_ready", false);
    putPayload(payload, "script_preflight_summary.require_policy", "");
    putPayload(payload, "script_preflight_summary.npm_policy", "");
    putPayload(payload, "script_preflight_summary.android_bridge_policy", "");
    putPayload(payload, "script_preflight_summary.autojs_api_policy", "");
    putPayload(payload, "script_preflight_summary.ready_for_controlled_inline_execution", false);
    putPayload(payload, "script_preflight_summary.ready_for_user_file_execution", false);
    putPayload(payload, "script_preflight_summary.ready_for_require", false);
    putPayload(payload, "script_preflight_summary.ready_for_autojs_api", false);
    putPayload(payload, "script_preflight_summary.final_state", "");
    putPayload(payload, "script_preflight_summary.sequence_monotonic", false);
    putPayload(payload, "script_preflight_summary.event_count", static_cast<long long>(0));
    putPayload(payload, "script_preflight_summary.response_count", static_cast<long long>(0));
    putPayload(payload, "script_preflight_summary.node_version", "");
    putPayload(payload, "script_preflight_summary.source.length", static_cast<long long>(0));
    putPayload(payload, "script_preflight_summary.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "script_preflight_summary.script_source.length", static_cast<long long>(0));
    putPayload(payload, "script_preflight_summary.load_environment.result", "skipped");
    putPayload(payload, "script_preflight_summary.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.script_preflight_summary.ms", static_cast<long long>(0));
}

void putCompletionDrainSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "completion_drain.status", "skipped");
    putPayload(payload, "completion_drain.detail", detail);
    putPayload(payload, "completion_drain.result_text", "");
    putPayload(payload, "completion_drain.started", false);
    putPayload(payload, "completion_drain.completed", false);
    putPayload(payload, "completion_drain.failed", false);
    putPayload(payload, "completion_drain.exit_code", "");
    putPayload(payload, "completion_drain.value", "");
    putPayload(payload, "completion_drain.events", "");
    putPayload(payload, "completion_drain.late_immediate_seen", false);
    putPayload(payload, "completion_drain.late_stdout_seen", false);
    putPayload(payload, "completion_drain.late_stderr_seen", false);
    putPayload(payload, "completion_drain.stdout_captured", "");
    putPayload(payload, "completion_drain.stderr_captured", "");
    putPayload(payload, "completion_drain.stdout_contains_sync", false);
    putPayload(payload, "completion_drain.stdout_contains_first_immediate", false);
    putPayload(payload, "completion_drain.stdout_contains_late", false);
    putPayload(payload, "completion_drain.stderr_contains_late", false);
    putPayload(payload, "completion_drain.contains_version", false);
    putPayload(payload, "completion_drain.source.length", static_cast<long long>(0));
    putPayload(payload, "completion_drain.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "completion_drain.script_source.length", static_cast<long long>(0));
    putPayload(payload, "completion_drain.load_environment.result", "skipped");
    putPayload(payload, "completion_drain.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.completion_drain.ms", static_cast<long long>(0));
}

void putPostCompletionErrorSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "post_completion_error.status", "skipped");
    putPayload(payload, "post_completion_error.detail", detail);
    putPayload(payload, "post_completion_error.result_text", "");
    putPayload(payload, "post_completion_error.started", false);
    putPayload(payload, "post_completion_error.completed", false);
    putPayload(payload, "post_completion_error.failed", false);
    putPayload(payload, "post_completion_error.exit_code", "");
    putPayload(payload, "post_completion_error.value", "");
    putPayload(payload, "post_completion_error.events", "");
    putPayload(payload, "post_completion_error.post_completion_error_seen", false);
    putPayload(payload, "post_completion_error.post_completion_error_message", "");
    putPayload(payload, "post_completion_error.stderr_contains_post_completion_error", false);
    putPayload(payload, "post_completion_error.stdout_captured", "");
    putPayload(payload, "post_completion_error.stderr_captured", "");
    putPayload(payload, "post_completion_error.contains_version", false);
    putPayload(payload, "post_completion_error.source.length", static_cast<long long>(0));
    putPayload(payload, "post_completion_error.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "post_completion_error.script_source.length", static_cast<long long>(0));
    putPayload(payload, "post_completion_error.load_environment.result", "skipped");
    putPayload(payload, "post_completion_error.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.post_completion_error.ms", static_cast<long long>(0));
}

void putOutputEventsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "output_events.status", "skipped");
    putPayload(payload, "output_events.detail", detail);
    putPayload(payload, "output_events.result_text", "");
    putPayload(payload, "output_events.events", "");
    putPayload(payload, "output_events.output_events", "");
    putPayload(payload, "output_events.output_event_count", static_cast<long long>(0));
    putPayload(payload, "output_events.stdout_event_count", static_cast<long long>(0));
    putPayload(payload, "output_events.stderr_event_count", static_cast<long long>(0));
    putPayload(payload, "output_events.before_complete_count", static_cast<long long>(0));
    putPayload(payload, "output_events.after_complete_count", static_cast<long long>(0));
    putPayload(payload, "output_events.contains_sync_stdout", false);
    putPayload(payload, "output_events.contains_sync_stderr", false);
    putPayload(payload, "output_events.contains_late_stdout", false);
    putPayload(payload, "output_events.contains_late_stderr", false);
    putPayload(payload, "output_events.contains_post_completion_error", false);
    putPayload(payload, "output_events.fd_capture_stdout_matches", false);
    putPayload(payload, "output_events.fd_capture_stderr_matches", false);
    putPayload(payload, "output_events.source.length", static_cast<long long>(0));
    putPayload(payload, "output_events.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "output_events.script_source.length", static_cast<long long>(0));
    putPayload(payload, "output_events.load_environment.result", "skipped");
    putPayload(payload, "output_events.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.output_events.ms", static_cast<long long>(0));
}

void putOutputEnvelopeSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "output_envelope.status", "skipped");
    putPayload(payload, "output_envelope.detail", detail);
    putPayload(payload, "output_envelope.result_text", "");
    putPayload(payload, "output_envelope.events", "");
    putPayload(payload, "output_envelope.envelopes", "");
    putPayload(payload, "output_envelope.envelope_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.sequence_monotonic", false);
    putPayload(payload, "output_envelope.elapsed_monotonic", false);
    putPayload(payload, "output_envelope.stdout_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.stderr_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.info_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.error_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.stdout_info_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.stderr_error_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.console_source_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.stdout_source_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.stderr_source_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.error_handler_source_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.before_complete_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.after_complete_count", static_cast<long long>(0));
    putPayload(payload, "output_envelope.contains_sync_stdout", false);
    putPayload(payload, "output_envelope.contains_sync_stderr", false);
    putPayload(payload, "output_envelope.contains_late_stdout", false);
    putPayload(payload, "output_envelope.contains_late_stderr", false);
    putPayload(payload, "output_envelope.contains_post_completion_error", false);
    putPayload(payload, "output_envelope.source.length", static_cast<long long>(0));
    putPayload(payload, "output_envelope.bootstrap_source.length", static_cast<long long>(0));
    putPayload(payload, "output_envelope.script_source.length", static_cast<long long>(0));
    putPayload(payload, "output_envelope.load_environment.result", "skipped");
    putPayload(payload, "output_envelope.spin_event_loop.result", "skipped");
    putPayload(payload, "timing.output_envelope.ms", static_cast<long long>(0));
}

std::vector<std::string> splitPipeFields(const std::string& text) {
    std::vector<std::string> fields;
    size_t start = 0;
    while (start <= text.size()) {
        const size_t separator = text.find('|', start);
        if (separator == std::string::npos) {
            fields.emplace_back(text.substr(start));
            break;
        }
        fields.emplace_back(text.substr(start, separator - start));
        start = separator + 1;
    }
    return fields;
}

void putJsResultFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    putPayload(payload, "js_result.text", text);
    putPayload(payload, "js_result.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "js_result.stdout_type", fields.size() > 1 ? fields[1] : "");
    putPayload(payload, "js_result.stdout_write_type", fields.size() > 2 ? fields[2] : "");
    putPayload(payload, "js_result.stdout_fd", fields.size() > 3 ? fields[3] : "");
    putPayload(payload, "js_result.stderr_fd", fields.size() > 4 ? fields[4] : "");
}

void putStdoutWriteFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string ok = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "ok=");
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    putPayload(payload, "stdout_write.result_text", text);
    putPayload(payload, "stdout_write.ok", ok.empty() ? "unknown" : ok);
    putPayload(payload, "stdout_write.fd", fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "fd=") : "");
    putPayload(payload, "stdout_write.write_type", fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "type=") : "");
    if (!error.empty()) {
        putPayload(payload, "stdout_write.detail", error);
    }
}

void putConsoleDiagnosticsFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string captured = fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "captured=") : "";
    putPayload(payload, "console_diagnostics.result_text", text);
    putPayload(payload, "console_diagnostics.console_type", fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "consoleType="));
    putPayload(payload, "console_diagnostics.console_log_type", fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "consoleLogType=") : "");
    putPayload(payload, "console_diagnostics.stdout_write_type", fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "stdoutWriteType=") : "");
    putPayload(payload, "console_diagnostics.captured", captured);
    putPayload(payload, "console_diagnostics.captured_contains_version", captured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_diagnostics.fd", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "fd=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_diagnostics.detail", error);
    }
}

void putConsoleStreamFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string processCaptured = fields.size() > 11 ? fieldValueAfterPrefix(fields[11], "processCaptured=") : "";
    const std::string consoleStdoutCaptured = fields.size() > 12 ? fieldValueAfterPrefix(fields[12], "consoleStdoutCaptured=") : "";
    putPayload(payload, "console_stream.result_text", text);
    putPayload(payload, "console_stream.console_type", fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "consoleType="));
    putPayload(payload, "console_stream.console_log_type", fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "consoleLogType=") : "");
    putPayload(payload, "console_stream.console_ctor", fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "consoleCtor=") : "");
    putPayload(payload, "console_stream.has_console_stdout", fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "hasConsoleStdout=") : "");
    putPayload(payload, "console_stream.console_stdout_type", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "consoleStdoutType=") : "");
    putPayload(payload, "console_stream.console_stdout_ctor", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "consoleStdoutCtor=") : "");
    putPayload(payload, "console_stream.process_stdout_ctor", fields.size() > 6 ? fieldValueAfterPrefix(fields[6], "processStdoutCtor=") : "");
    putPayload(payload, "console_stream.before_same_stdout", fields.size() > 7 ? fieldValueAfterPrefix(fields[7], "beforeSameStdout=") : "");
    putPayload(payload, "console_stream.before_same_write", fields.size() > 8 ? fieldValueAfterPrefix(fields[8], "beforeSameWrite=") : "");
    putPayload(payload, "console_stream.after_process_patch_same_write", fields.size() > 9 ? fieldValueAfterPrefix(fields[9], "afterProcessPatchSameWrite=") : "");
    putPayload(payload, "console_stream.after_console_patch_same_write", fields.size() > 10 ? fieldValueAfterPrefix(fields[10], "afterConsolePatchSameWrite=") : "");
    putPayload(payload, "console_stream.process_captured", processCaptured);
    putPayload(payload, "console_stream.console_stdout_captured", consoleStdoutCaptured);
    putPayload(payload, "console_stream.process_captured_contains_version", processCaptured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_stream.console_stdout_captured_contains_version", consoleStdoutCaptured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_stream.fd", fields.size() > 13 ? fieldValueAfterPrefix(fields[13], "fd=") : "");
    putPayload(payload, "console_stream.console_stdout_fd", fields.size() > 14 ? fieldValueAfterPrefix(fields[14], "consoleStdoutFd=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_stream.detail", error);
    }
}

void putConsoleShapeFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    putPayload(payload, "console_shape.result_text", text);
    putPayload(payload, "console_shape.console_type", fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "consoleType="));
    putPayload(payload, "console_shape.console_ctor", fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "consoleCtor=") : "");
    putPayload(payload, "console_shape.proto_type", fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "protoType=") : "");
    putPayload(payload, "console_shape.proto_ctor", fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "protoCtor=") : "");
    putPayload(payload, "console_shape.has_own_log", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "hasOwnLog=") : "");
    putPayload(payload, "console_shape.log_type", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "logType=") : "");
    putPayload(payload, "console_shape.log_name", fields.size() > 6 ? fieldValueAfterPrefix(fields[6], "logName=") : "");
    putPayload(payload, "console_shape.log_length", fields.size() > 7 ? fieldValueAfterPrefix(fields[7], "logLength=") : "");
    putPayload(payload, "console_shape.log_equals_proto_log", fields.size() > 8 ? fieldValueAfterPrefix(fields[8], "logEqualsProtoLog=") : "");
    putPayload(payload, "console_shape.log_desc", fields.size() > 9 ? fieldValueAfterPrefix(fields[9], "logDesc=") : "");
    putPayload(payload, "console_shape.proto_log_desc", fields.size() > 10 ? fieldValueAfterPrefix(fields[10], "protoLogDesc=") : "");
    putPayload(payload, "console_shape.own_names", fields.size() > 11 ? fieldValueAfterPrefix(fields[11], "ownNames=") : "");
    putPayload(payload, "console_shape.proto_names", fields.size() > 12 ? fieldValueAfterPrefix(fields[12], "protoNames=") : "");
    putPayload(payload, "console_shape.own_symbols", fields.size() > 13 ? fieldValueAfterPrefix(fields[13], "ownSymbols=") : "");
    putPayload(payload, "console_shape.proto_symbols", fields.size() > 14 ? fieldValueAfterPrefix(fields[14], "protoSymbols=") : "");
    putPayload(payload, "console_shape.log_string", fields.size() > 15 ? fieldValueAfterPrefix(fields[15], "logString=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_shape.detail", error);
    }
}

void putConsoleReplaceFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string captured = fields.size() > 9 ? fieldValueAfterPrefix(fields[9], "captured=") : "";
    putPayload(payload, "console_replace.result_text", text);
    putPayload(payload, "console_replace.original_log_type", fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "originalLogType="));
    putPayload(payload, "console_replace.original_log_string", fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "originalLogString=") : "");
    putPayload(payload, "console_replace.replacement_log_type", fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "replacementLogType=") : "");
    putPayload(payload, "console_replace.replacement_log_string", fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "replacementLogString=") : "");
    putPayload(payload, "console_replace.replacement_name", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "replacementName=") : "");
    putPayload(payload, "console_replace.replacement_length", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "replacementLength=") : "");
    putPayload(payload, "console_replace.replacement_writable", fields.size() > 6 ? fieldValueAfterPrefix(fields[6], "replacementWritable=") : "");
    putPayload(payload, "console_replace.replacement_configurable", fields.size() > 7 ? fieldValueAfterPrefix(fields[7], "replacementConfigurable=") : "");
    putPayload(payload, "console_replace.result", fields.size() > 8 ? fieldValueAfterPrefix(fields[8], "result=") : "");
    putPayload(payload, "console_replace.captured", captured);
    putPayload(payload, "console_replace.captured_contains_version", captured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_replace.fd", fields.size() > 11 ? fieldValueAfterPrefix(fields[11], "fd=") : "");
    putPayload(payload, "console_replace.write_type", fields.size() > 12 ? fieldValueAfterPrefix(fields[12], "writeType=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_replace.detail", error);
    }
}

void putConsoleFamilyFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string stdoutCaptured = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "stdoutCaptured=");
    const std::string stderrCaptured = fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "stderrCaptured=") : "";
    putPayload(payload, "console_family.result_text", text);
    putPayload(payload, "console_family.stdout_captured", stdoutCaptured);
    putPayload(payload, "console_family.stderr_captured", stderrCaptured);
    putPayload(payload, "console_family.stdout_contains_version", stdoutCaptured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_family.stderr_contains_version", stderrCaptured.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "console_family.stdout_fd", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "stdoutFd=") : "");
    putPayload(payload, "console_family.stderr_fd", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "stderrFd=") : "");
    putPayload(payload, "console_family.stdout_write_type", fields.size() > 6 ? fieldValueAfterPrefix(fields[6], "stdoutWriteType=") : "");
    putPayload(payload, "console_family.stderr_write_type", fields.size() > 7 ? fieldValueAfterPrefix(fields[7], "stderrWriteType=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_family.detail", error);
    }
}

void putConsoleFormatFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string stdoutCaptured = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "stdoutCaptured=");
    const std::string stderrCaptured = fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "stderrCaptured=") : "";
    const std::string stdoutContainsPrimitive = fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "stdoutContainsPrimitive=") : "";
    const std::string stdoutContainsObject = fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "stdoutContainsObject=") : "";
    const std::string stderrContainsError = fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "stderrContainsError=") : "";
    putPayload(payload, "console_format.result_text", text);
    putPayload(payload, "console_format.stdout_captured", stdoutCaptured);
    putPayload(payload, "console_format.stderr_captured", stderrCaptured);
    putPayload(payload, "console_format.stdout_contains_primitive", stdoutContainsPrimitive == "true");
    putPayload(payload, "console_format.stdout_contains_object", stdoutContainsObject == "true");
    putPayload(payload, "console_format.stderr_contains_error", stderrContainsError == "true");
    putPayload(payload, "console_format.stdout_fd", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "stdoutFd=") : "");
    putPayload(payload, "console_format.stderr_fd", fields.size() > 6 ? fieldValueAfterPrefix(fields[6], "stderrFd=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_format.detail", error);
    }
}

void putConsoleRejectionFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string stderrCaptured = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "stderrCaptured=");
    const std::string stderrContainsCaught = fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "stderrContainsCaught=") : "";
    const std::string stderrContainsRejection = fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "stderrContainsRejection=") : "";
    const std::string rejectionSeen = fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "rejectionSeen=") : "";
    putPayload(payload, "console_rejection.result_text", text);
    putPayload(payload, "console_rejection.stderr_captured", stderrCaptured);
    putPayload(payload, "console_rejection.stderr_contains_caught", stderrContainsCaught == "true");
    putPayload(payload, "console_rejection.stderr_contains_rejection", stderrContainsRejection == "true");
    putPayload(payload, "console_rejection.rejection_seen", rejectionSeen == "true");
    putPayload(payload, "console_rejection.stderr_fd", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "stderrFd=") : "");
    putPayload(payload, "console_rejection.stderr_write_type", fields.size() > 5 ? fieldValueAfterPrefix(fields[5], "stderrWriteType=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_rejection.detail", error);
    }
}

void putConsoleUncaughtFields(std::vector<std::string>& payload, const std::string& text) {
    const std::vector<std::string> fields = splitPipeFields(text);
    const std::string error = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "error=");
    const std::string stderrCaptured = fields.empty() ? "" : fieldValueAfterPrefix(fields[0], "stderrCaptured=");
    const std::string stderrContainsUncaught = fields.size() > 1 ? fieldValueAfterPrefix(fields[1], "stderrContainsUncaught=") : "";
    const std::string uncaughtSeen = fields.size() > 2 ? fieldValueAfterPrefix(fields[2], "uncaughtSeen=") : "";
    putPayload(payload, "console_uncaught.result_text", text);
    putPayload(payload, "console_uncaught.stderr_captured", stderrCaptured);
    putPayload(payload, "console_uncaught.stderr_contains_uncaught", stderrContainsUncaught == "true");
    putPayload(payload, "console_uncaught.uncaught_seen", uncaughtSeen == "true");
    putPayload(payload, "console_uncaught.stderr_fd", fields.size() > 3 ? fieldValueAfterPrefix(fields[3], "stderrFd=") : "");
    putPayload(payload, "console_uncaught.stderr_write_type", fields.size() > 4 ? fieldValueAfterPrefix(fields[4], "stderrWriteType=") : "");
    if (!error.empty()) {
        putPayload(payload, "console_uncaught.detail", error);
    }
}

void putSchedulingFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "scheduling.result_text", text);
    putPayload(payload, "scheduling.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "scheduling.set_timeout_type", jsonStringField(text, "setTimeout"));
    putPayload(payload, "scheduling.clear_timeout_type", jsonStringField(text, "clearTimeout"));
    putPayload(payload, "scheduling.set_interval_type", jsonStringField(text, "setInterval"));
    putPayload(payload, "scheduling.clear_interval_type", jsonStringField(text, "clearInterval"));
    putPayload(payload, "scheduling.set_immediate_type", jsonStringField(text, "setImmediate"));
    putPayload(payload, "scheduling.clear_immediate_type", jsonStringField(text, "clearImmediate"));
    putPayload(payload, "scheduling.queue_microtask_type", jsonStringField(text, "queueMicrotask"));
    putPayload(payload, "scheduling.process_next_tick_type", jsonStringField(text, "processNextTick"));
    putPayload(payload, "scheduling.promise_type", jsonStringField(text, "promise"));
    putPayload(payload, "scheduling.stdout_write_type", jsonStringField(text, "stdoutWrite"));
    putPayload(payload, "scheduling.stderr_write_type", jsonStringField(text, "stderrWrite"));
    if (!error.empty()) {
        putPayload(payload, "scheduling.detail", error);
    }
}

void putSchedulingOrderFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "scheduling_order.result_text", text);
    putPayload(payload, "scheduling_order.events", events);
    putPayload(payload, "scheduling_order.has_next_tick", jsonBooleanField(text, "hasNextTick"));
    putPayload(payload, "scheduling_order.has_promise", jsonBooleanField(text, "hasPromise"));
    putPayload(payload, "scheduling_order.has_set_immediate", jsonBooleanField(text, "hasSetImmediate"));
    putPayload(payload, "scheduling_order.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    putPayload(payload, "scheduling_order.contains_next_tick", events.find("\"nextTick\"") != std::string::npos);
    putPayload(payload, "scheduling_order.contains_promise", events.find("\"promise\"") != std::string::npos);
    putPayload(payload, "scheduling_order.contains_set_immediate", events.find("\"setImmediate\"") != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "scheduling_order.detail", error);
    }
}

void putAsyncConsoleFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "async_console.result_text", text);
    putPayload(payload, "async_console.events", events);
    putPayload(payload, "async_console.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "async_console.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "async_console.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "async_console.stdout_contains_async", jsonBooleanField(text, "stdoutContainsAsync"));
    putPayload(payload, "async_console.stderr_contains_sync", jsonBooleanField(text, "stderrContainsSync"));
    putPayload(payload, "async_console.stderr_contains_async", jsonBooleanField(text, "stderrContainsAsync"));
    putPayload(payload, "async_console.stdout_fd", jsonStringField(text, "stdoutFd"));
    putPayload(payload, "async_console.stderr_fd", jsonStringField(text, "stderrFd"));
    putPayload(payload, "async_console.stdout_write_type", jsonStringField(text, "stdoutWriteType"));
    putPayload(payload, "async_console.stderr_write_type", jsonStringField(text, "stderrWriteType"));
    putPayload(payload, "async_console.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "async_console.detail", error);
    }
}

void putAsyncErrorFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "async_error.result_text", text);
    putPayload(payload, "async_error.events", events);
    putPayload(payload, "async_error.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "async_error.stderr_contains_uncaught", jsonBooleanField(text, "stderrContainsUncaught"));
    putPayload(payload, "async_error.stderr_contains_rejection", jsonBooleanField(text, "stderrContainsRejection"));
    putPayload(payload, "async_error.uncaught_seen", jsonBooleanField(text, "uncaughtSeen"));
    putPayload(payload, "async_error.rejection_seen", jsonBooleanField(text, "rejectionSeen"));
    putPayload(payload, "async_error.stderr_fd", jsonStringField(text, "stderrFd"));
    putPayload(payload, "async_error.stderr_write_type", jsonStringField(text, "stderrWriteType"));
    putPayload(payload, "async_error.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "async_error.detail", error);
    }
}

void putBootstrapFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "bootstrap.result_text", text);
    putPayload(payload, "bootstrap.events", events);
    putPayload(payload, "bootstrap.installed", jsonBooleanField(text, "bootstrapInstalled"));
    putPayload(payload, "bootstrap.console_installed", jsonBooleanField(text, "consoleInstalled"));
    putPayload(payload, "bootstrap.error_handlers_installed", jsonBooleanField(text, "errorHandlersInstalled"));
    putPayload(payload, "bootstrap.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "bootstrap.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "bootstrap.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "bootstrap.stdout_contains_async", jsonBooleanField(text, "stdoutContainsAsync"));
    putPayload(payload, "bootstrap.stderr_contains_sync", jsonBooleanField(text, "stderrContainsSync"));
    putPayload(payload, "bootstrap.stderr_contains_async", jsonBooleanField(text, "stderrContainsAsync"));
    putPayload(payload, "bootstrap.stderr_contains_rejection", jsonBooleanField(text, "stderrContainsRejection"));
    putPayload(payload, "bootstrap.stderr_contains_uncaught", jsonBooleanField(text, "stderrContainsUncaught"));
    putPayload(payload, "bootstrap.rejection_seen", jsonBooleanField(text, "rejectionSeen"));
    putPayload(payload, "bootstrap.uncaught_seen", jsonBooleanField(text, "uncaughtSeen"));
    putPayload(payload, "bootstrap.final_result_writer_seen", jsonBooleanField(text, "finalResultWriterSeen"));
    putPayload(payload, "bootstrap.stdout_fd", jsonStringField(text, "stdoutFd"));
    putPayload(payload, "bootstrap.stderr_fd", jsonStringField(text, "stderrFd"));
    putPayload(payload, "bootstrap.stdout_write_type", jsonStringField(text, "stdoutWriteType"));
    putPayload(payload, "bootstrap.stderr_write_type", jsonStringField(text, "stderrWriteType"));
    putPayload(payload, "bootstrap.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "bootstrap.detail", error);
    }
}

void putBootstrapScriptFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "bootstrap_script.result_text", text);
    putPayload(payload, "bootstrap_script.events", events);
    putPayload(payload, "bootstrap_script.bootstrap_installed", jsonBooleanField(text, "bootstrapInstalled"));
    putPayload(payload, "bootstrap_script.script_started", jsonBooleanField(text, "scriptStarted"));
    putPayload(payload, "bootstrap_script.script_completed", jsonBooleanField(text, "scriptCompleted"));
    putPayload(payload, "bootstrap_script.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "bootstrap_script.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "bootstrap_script.stdout_contains_script_sync", jsonBooleanField(text, "stdoutContainsScriptSync"));
    putPayload(payload, "bootstrap_script.stdout_contains_script_async", jsonBooleanField(text, "stdoutContainsScriptAsync"));
    putPayload(payload, "bootstrap_script.stderr_contains_script_sync", jsonBooleanField(text, "stderrContainsScriptSync"));
    putPayload(payload, "bootstrap_script.stderr_contains_script_async", jsonBooleanField(text, "stderrContainsScriptAsync"));
    putPayload(payload, "bootstrap_script.stderr_contains_rejection", jsonBooleanField(text, "stderrContainsRejection"));
    putPayload(payload, "bootstrap_script.stderr_contains_uncaught", jsonBooleanField(text, "stderrContainsUncaught"));
    putPayload(payload, "bootstrap_script.rejection_seen", jsonBooleanField(text, "rejectionSeen"));
    putPayload(payload, "bootstrap_script.uncaught_seen", jsonBooleanField(text, "uncaughtSeen"));
    putPayload(payload, "bootstrap_script.final_result_writer_seen", jsonBooleanField(text, "finalResultWriterSeen"));
    putPayload(payload, "bootstrap_script.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "bootstrap_script.detail", error);
    }
}

void putScriptCompletionFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "script_completion.result_text", text);
    putPayload(payload, "script_completion.started", jsonBooleanField(text, "started"));
    putPayload(payload, "script_completion.completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "script_completion.failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "script_completion.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "script_completion.value", jsonStringField(text, "value"));
    putPayload(payload, "script_completion.events", events);
    putPayload(payload, "script_completion.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "script_completion.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "script_completion.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "script_completion.stdout_contains_async", jsonBooleanField(text, "stdoutContainsAsync"));
    putPayload(payload, "script_completion.stderr_contains_error", jsonBooleanField(text, "stderrContainsError"));
    putPayload(payload, "script_completion.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "script_completion.detail", error);
    }
}

void putScriptFailureFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "script_failure.result_text", text);
    putPayload(payload, "script_failure.started", jsonBooleanField(text, "started"));
    putPayload(payload, "script_failure.completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "script_failure.failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "script_failure.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "script_failure.value", jsonStringField(text, "value"));
    putPayload(payload, "script_failure.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "script_failure.error_stack", jsonStringField(text, "errorStack"));
    putPayload(payload, "script_failure.events", events);
    putPayload(payload, "script_failure.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "script_failure.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "script_failure.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "script_failure.stderr_contains_error", jsonBooleanField(text, "stderrContainsError"));
    putPayload(payload, "script_failure.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "script_failure.detail", error);
    }
}

void putScriptCancelFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = jsonStringField(text, "error");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "script_cancel.result_text", text);
    putPayload(payload, "script_cancel.started", jsonBooleanField(text, "started"));
    putPayload(payload, "script_cancel.completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "script_cancel.failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "script_cancel.cancelled", jsonBooleanField(text, "cancelled"));
    putPayload(payload, "script_cancel.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "script_cancel.cancel_reason", jsonStringField(text, "cancelReason"));
    putPayload(payload, "script_cancel.after_cancel_seen", jsonBooleanField(text, "afterCancelSeen"));
    putPayload(payload, "script_cancel.cancel_observed", jsonBooleanField(text, "cancelObserved"));
    putPayload(payload, "script_cancel.events", events);
    putPayload(payload, "script_cancel.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "script_cancel.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "script_cancel.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "script_cancel.stderr_contains_cancel", jsonBooleanField(text, "stderrContainsCancel"));
    putPayload(payload, "script_cancel.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "script_cancel.detail", error);
    }
}

void putCapabilityDescriptorFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "capability_descriptor.result_text", text);
    putPayload(payload, "capability_descriptor.runtime", jsonStringField(text, "runtime"));
    putPayload(payload, "capability_descriptor.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "capability_descriptor.embedded", jsonBooleanField(text, "embedded"));
    putPayload(payload, "capability_descriptor.one_shot", jsonBooleanField(text, "oneShot"));
    putPayload(payload, "capability_descriptor.abi", jsonStringField(text, "abi"));
    putPayload(payload, "capability_descriptor.has_process_version", jsonBooleanField(text, "processVersion"));
    putPayload(payload, "capability_descriptor.has_stdout_write", jsonBooleanField(text, "stdoutWrite"));
    putPayload(payload, "capability_descriptor.has_stderr_write", jsonBooleanField(text, "stderrWrite"));
    putPayload(payload, "capability_descriptor.has_console_shim", jsonBooleanField(text, "consoleShim"));
    putPayload(payload, "capability_descriptor.has_set_immediate", jsonBooleanField(text, "setImmediate"));
    putPayload(payload, "capability_descriptor.has_promise", jsonBooleanField(text, "promise"));
    putPayload(payload, "capability_descriptor.has_next_tick", jsonBooleanField(text, "nextTick"));
    putPayload(payload, "capability_descriptor.has_set_timeout", jsonBooleanField(text, "setTimeout"));
    putPayload(payload, "capability_descriptor.has_set_interval", jsonBooleanField(text, "setInterval"));
    putPayload(payload, "capability_descriptor.has_queue_microtask", jsonBooleanField(text, "queueMicrotask"));
    putPayload(payload, "capability_descriptor.supports_output_events", jsonBooleanField(text, "outputEvents"));
    putPayload(payload, "capability_descriptor.supports_output_envelope", jsonBooleanField(text, "outputEnvelope"));
    putPayload(payload, "capability_descriptor.supports_script_completion", jsonBooleanField(text, "scriptCompletion"));
    putPayload(payload, "capability_descriptor.supports_script_failure", jsonBooleanField(text, "scriptFailure"));
    putPayload(payload, "capability_descriptor.supports_script_cancellation", jsonBooleanField(text, "scriptCancellation"));
    putPayload(payload, "capability_descriptor.supports_post_completion_error", jsonBooleanField(text, "postCompletionError"));
    putPayload(payload, "capability_descriptor.supports_require", jsonBooleanField(text, "require"));
    putPayload(payload, "capability_descriptor.supports_npm", jsonBooleanField(text, "npm"));
    putPayload(payload, "capability_descriptor.supports_user_script", jsonBooleanField(text, "userScript"));
    putPayload(payload, "capability_descriptor.supports_autojs_api", jsonBooleanField(text, "autojsApi"));
    putPayload(payload, "capability_descriptor.supports_android_bridge", jsonBooleanField(text, "androidBridge"));
    if (!error.empty()) {
        putPayload(payload, "capability_descriptor.detail", error);
    }
}

void putPluginContractFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_contract.result_text", text);
    putPayload(payload, "plugin_contract.protocol_version", jsonNumberField(text, "protocolVersion"));
    putPayload(payload, "plugin_contract.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_contract.runtime_kind", jsonStringField(text, "runtimeKind"));
    putPayload(payload, "plugin_contract.runtime_version", jsonStringField(text, "runtimeVersion"));
    putPayload(payload, "plugin_contract.embedded", jsonBooleanField(text, "embedded"));
    putPayload(payload, "plugin_contract.plugin_ready", jsonBooleanField(text, "pluginReady"));
    putPayload(payload, "plugin_contract.supports_probe", jsonBooleanField(text, "probe"));
    putPayload(payload, "plugin_contract.supports_run_script", jsonBooleanField(text, "runScript"));
    putPayload(payload, "plugin_contract.supports_cancel", jsonBooleanField(text, "cancel"));
    putPayload(payload, "plugin_contract.supports_dispose", jsonBooleanField(text, "dispose"));
    putPayload(payload, "plugin_contract.event_stdout", jsonBooleanField(text, "stdout"));
    putPayload(payload, "plugin_contract.event_stderr", jsonBooleanField(text, "stderr"));
    putPayload(payload, "plugin_contract.event_output_envelope", jsonBooleanField(text, "outputEnvelope"));
    putPayload(payload, "plugin_contract.event_completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "plugin_contract.event_failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "plugin_contract.event_cancelled", jsonBooleanField(text, "cancelled"));
    putPayload(payload, "plugin_contract.event_post_completion_error", jsonBooleanField(text, "postCompletionError"));
    putPayload(payload, "plugin_contract.policy_isolated_process", jsonBooleanField(text, "isolatedProcess"));
    putPayload(payload, "plugin_contract.policy_drain_after_complete", jsonBooleanField(text, "drainAfterComplete"));
    putPayload(payload, "plugin_contract.policy_post_completion_error_does_not_fail", jsonBooleanField(text, "postCompletionErrorDoesNotFailScript"));
    putPayload(payload, "plugin_contract.policy_default_cancellation_exit_code", jsonNumberField(text, "defaultCancellationExitCode"));
    putPayload(payload, "plugin_contract.unsupported_require", jsonBooleanField(text, "require"));
    putPayload(payload, "plugin_contract.unsupported_npm", jsonBooleanField(text, "npm"));
    putPayload(payload, "plugin_contract.unsupported_user_file", jsonBooleanField(text, "userFile"));
    putPayload(payload, "plugin_contract.unsupported_autojs_api", jsonBooleanField(text, "autojsApi"));
    putPayload(payload, "plugin_contract.unsupported_android_bridge", jsonBooleanField(text, "androidBridge"));
    if (!error.empty()) {
        putPayload(payload, "plugin_contract.detail", error);
    }
}

void putPluginRequestFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_request.result_text", text);
    putPayload(payload, "plugin_request.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_request.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_request.response_id", jsonStringField(text, "responseId"));
    putPayload(payload, "plugin_request.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_request.runtime", jsonStringField(text, "runtime"));
    putPayload(payload, "plugin_request.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "plugin_request.cancel_request_id", jsonStringField(text, "cancelRequestId"));
    putPayload(payload, "plugin_request.cancel_response_ok", jsonBooleanField(text, "cancelResponseOk"));
    putPayload(payload, "plugin_request.cancelled", jsonBooleanField(text, "cancelled"));
    putPayload(payload, "plugin_request.cancel_exit_code", jsonNumberField(text, "cancelExitCode"));
    if (!error.empty()) {
        putPayload(payload, "plugin_request.detail", error);
    }
}

void putPluginEventsFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_events.result_text", text);
    putPayload(payload, "plugin_events.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_events.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_events.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_events.has_stdout_event", jsonBooleanField(text, "hasStdoutEvent"));
    putPayload(payload, "plugin_events.has_stderr_event", jsonBooleanField(text, "hasStderrEvent"));
    putPayload(payload, "plugin_events.has_completed_event", jsonBooleanField(text, "hasCompletedEvent"));
    putPayload(payload, "plugin_events.has_cancelled_event", jsonBooleanField(text, "hasCancelledEvent"));
    putPayload(payload, "plugin_events.has_diagnostic_event", jsonBooleanField(text, "hasDiagnosticEvent"));
    putPayload(payload, "plugin_events.stdout_event_text", jsonStringField(text, "stdoutEventText"));
    putPayload(payload, "plugin_events.stderr_event_text", jsonStringField(text, "stderrEventText"));
    putPayload(payload, "plugin_events.completed_exit_code", jsonNumberField(text, "completedExitCode"));
    putPayload(payload, "plugin_events.cancelled_exit_code", jsonNumberField(text, "cancelledExitCode"));
    if (!error.empty()) {
        putPayload(payload, "plugin_events.detail", error);
    }
}

void putPluginFailureFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_failure.result_text", text);
    putPayload(payload, "plugin_failure.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_failure.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_failure.response_id", jsonStringField(text, "responseId"));
    putPayload(payload, "plugin_failure.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_failure.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "plugin_failure.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "plugin_failure.error_stack", jsonStringField(text, "errorStack"));
    putPayload(payload, "plugin_failure.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_failure.has_failed_event", jsonBooleanField(text, "hasFailedEvent"));
    putPayload(payload, "plugin_failure.has_stderr_event", jsonBooleanField(text, "hasStderrEvent"));
    putPayload(payload, "plugin_failure.failed_exit_code", jsonNumberField(text, "failedExitCode"));
    putPayload(payload, "plugin_failure.stderr_event_text", jsonStringField(text, "stderrEventText"));
    if (!error.empty()) {
        putPayload(payload, "plugin_failure.detail", error);
    }
}

void putPluginCancelFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_cancel.result_text", text);
    putPayload(payload, "plugin_cancel.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_cancel.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_cancel.reason", jsonStringField(text, "reason"));
    putPayload(payload, "plugin_cancel.response_id", jsonStringField(text, "responseId"));
    putPayload(payload, "plugin_cancel.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_cancel.cancelled", jsonBooleanField(text, "cancelled"));
    putPayload(payload, "plugin_cancel.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "plugin_cancel.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_cancel.has_diagnostic_event", jsonBooleanField(text, "hasDiagnosticEvent"));
    putPayload(payload, "plugin_cancel.has_cancelled_event", jsonBooleanField(text, "hasCancelledEvent"));
    putPayload(payload, "plugin_cancel.cancelled_event_exit_code", jsonNumberField(text, "cancelledEventExitCode"));
    putPayload(payload, "plugin_cancel.cancelled_event_reason", jsonStringField(text, "cancelledEventReason"));
    if (!error.empty()) {
        putPayload(payload, "plugin_cancel.detail", error);
    }
}

void putPluginDisposeFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_dispose.result_text", text);
    putPayload(payload, "plugin_dispose.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_dispose.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_dispose.response_id", jsonStringField(text, "responseId"));
    putPayload(payload, "plugin_dispose.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_dispose.disposed", jsonBooleanField(text, "disposed"));
    putPayload(payload, "plugin_dispose.runtime", jsonStringField(text, "runtime"));
    putPayload(payload, "plugin_dispose.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "plugin_dispose.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_dispose.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_dispose.has_diagnostic_event", jsonBooleanField(text, "hasDiagnosticEvent"));
    putPayload(payload, "plugin_dispose.has_disposed_event", jsonBooleanField(text, "hasDisposedEvent"));
    putPayload(payload, "plugin_dispose.disposed_event_runtime", jsonStringField(text, "disposedEventRuntime"));
    if (!error.empty()) {
        putPayload(payload, "plugin_dispose.detail", error);
    }
}

void putPluginLifecycleFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_lifecycle.result_text", text);
    putPayload(payload, "plugin_lifecycle.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_lifecycle.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_lifecycle.has_probe_response", jsonBooleanField(text, "hasProbeResponse"));
    putPayload(payload, "plugin_lifecycle.has_run_completed_event", jsonBooleanField(text, "hasRunCompletedEvent"));
    putPayload(payload, "plugin_lifecycle.has_cancel_response", jsonBooleanField(text, "hasCancelResponse"));
    putPayload(payload, "plugin_lifecycle.has_cancelled_event", jsonBooleanField(text, "hasCancelledEvent"));
    putPayload(payload, "plugin_lifecycle.has_dispose_response", jsonBooleanField(text, "hasDisposeResponse"));
    putPayload(payload, "plugin_lifecycle.has_disposed_event", jsonBooleanField(text, "hasDisposedEvent"));
    putPayload(payload, "plugin_lifecycle.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_lifecycle.final_disposed", jsonBooleanField(text, "finalDisposed"));
    putPayload(payload, "plugin_lifecycle.node_version", jsonStringField(text, "nodeVersion"));
    putPayload(payload, "plugin_lifecycle.probe_response_runtime", jsonStringField(text, "probeResponseRuntime"));
    putPayload(payload, "plugin_lifecycle.completed_exit_code", jsonNumberField(text, "completedExitCode"));
    putPayload(payload, "plugin_lifecycle.cancelled_exit_code", jsonNumberField(text, "cancelledExitCode"));
    putPayload(payload, "plugin_lifecycle.disposed_event_runtime", jsonStringField(text, "disposedEventRuntime"));
    if (!error.empty()) {
        putPayload(payload, "plugin_lifecycle.detail", error);
    }
}

void putPluginInvalidRequestFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_invalid_request.result_text", text);
    putPayload(payload, "plugin_invalid_request.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_invalid_request.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_invalid_request.has_unknown_method_error", jsonBooleanField(text, "hasUnknownMethodError"));
    putPayload(payload, "plugin_invalid_request.unknown_method_error_code", jsonStringField(text, "unknownMethodErrorCode"));
    putPayload(payload, "plugin_invalid_request.has_missing_id_error", jsonBooleanField(text, "hasMissingIdError"));
    putPayload(payload, "plugin_invalid_request.missing_id_error_code", jsonStringField(text, "missingIdErrorCode"));
    putPayload(payload, "plugin_invalid_request.has_malformed_params_error", jsonBooleanField(text, "hasMalformedParamsError"));
    putPayload(payload, "plugin_invalid_request.malformed_params_error_code", jsonStringField(text, "malformedParamsErrorCode"));
    putPayload(payload, "plugin_invalid_request.has_after_dispose_error", jsonBooleanField(text, "hasAfterDisposeError"));
    putPayload(payload, "plugin_invalid_request.after_dispose_error_code", jsonStringField(text, "afterDisposeErrorCode"));
    putPayload(payload, "plugin_invalid_request.all_errors_have_message", jsonBooleanField(text, "allErrorsHaveMessage"));
    putPayload(payload, "plugin_invalid_request.all_errors_have_code", jsonBooleanField(text, "allErrorsHaveCode"));
    putPayload(payload, "plugin_invalid_request.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    if (!error.empty()) {
        putPayload(payload, "plugin_invalid_request.detail", error);
    }
}

void putPluginStateMachineFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_state_machine.result_text", text);
    putPayload(payload, "plugin_state_machine.state_count", jsonNumberField(text, "stateCount"));
    putPayload(payload, "plugin_state_machine.transition_count", jsonNumberField(text, "transitionCount"));
    putPayload(payload, "plugin_state_machine.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_state_machine.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_state_machine.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_state_machine.created_probe_allowed", jsonBooleanField(text, "createdProbeAllowed"));
    putPayload(payload, "plugin_state_machine.created_cancel_rejected", jsonBooleanField(text, "createdCancelRejected"));
    putPayload(payload, "plugin_state_machine.created_dispose_allowed", jsonBooleanField(text, "createdDisposeAllowed"));
    putPayload(payload, "plugin_state_machine.ready_run_allowed", jsonBooleanField(text, "readyRunAllowed"));
    putPayload(payload, "plugin_state_machine.running_run_rejected", jsonBooleanField(text, "runningRunRejected"));
    putPayload(payload, "plugin_state_machine.running_cancel_allowed", jsonBooleanField(text, "runningCancelAllowed"));
    putPayload(payload, "plugin_state_machine.completed_cancel_rejected", jsonBooleanField(text, "completedCancelRejected"));
    putPayload(payload, "plugin_state_machine.completed_dispose_allowed", jsonBooleanField(text, "completedDisposeAllowed"));
    putPayload(payload, "plugin_state_machine.cancelled_dispose_allowed", jsonBooleanField(text, "cancelledDisposeAllowed"));
    putPayload(payload, "plugin_state_machine.disposed_probe_rejected", jsonBooleanField(text, "disposedProbeRejected"));
    putPayload(payload, "plugin_state_machine.disposed_run_rejected", jsonBooleanField(text, "disposedRunRejected"));
    putPayload(payload, "plugin_state_machine.disposed_cancel_rejected", jsonBooleanField(text, "disposedCancelRejected"));
    putPayload(payload, "plugin_state_machine.disposed_dispose_idempotent", jsonBooleanField(text, "disposedDisposeIdempotent"));
    putPayload(payload, "plugin_state_machine.has_invalid_state_error", jsonBooleanField(text, "hasInvalidStateError"));
    putPayload(payload, "plugin_state_machine.invalid_state_error_code", jsonStringField(text, "invalidStateErrorCode"));
    putPayload(payload, "plugin_state_machine.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_state_machine.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_state_machine.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_state_machine.detail", error);
    }
}

void putPluginTimeoutFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_timeout.result_text", text);
    putPayload(payload, "plugin_timeout.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_timeout.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_timeout.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "plugin_timeout.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_timeout.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "plugin_timeout.error_message_contains_timeout", jsonBooleanField(text, "errorMessageContainsTimeout"));
    putPayload(payload, "plugin_timeout.has_timeout_event", jsonBooleanField(text, "hasTimeoutEvent"));
    putPayload(payload, "plugin_timeout.timeout_event_exit_code", jsonNumberField(text, "timeoutEventExitCode"));
    putPayload(payload, "plugin_timeout.timeout_event_reason", jsonStringField(text, "timeoutEventReason"));
    putPayload(payload, "plugin_timeout.has_dispose_after_timeout", jsonBooleanField(text, "hasDisposeAfterTimeout"));
    putPayload(payload, "plugin_timeout.dispose_after_timeout_ok", jsonBooleanField(text, "disposeAfterTimeoutOk"));
    putPayload(payload, "plugin_timeout.state_before_timeout", jsonStringField(text, "stateBeforeTimeout"));
    putPayload(payload, "plugin_timeout.state_after_timeout", jsonStringField(text, "stateAfterTimeout"));
    putPayload(payload, "plugin_timeout.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_timeout.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_timeout.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_timeout.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_timeout.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_timeout.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_timeout.detail", error);
    }
}

void putPluginCrashFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_crash.result_text", text);
    putPayload(payload, "plugin_crash.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_crash.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_crash.state_before_crash", jsonStringField(text, "stateBeforeCrash"));
    putPayload(payload, "plugin_crash.state_after_crash", jsonStringField(text, "stateAfterCrash"));
    putPayload(payload, "plugin_crash.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_crash.response_ok", jsonBooleanField(text, "responseOk"));
    putPayload(payload, "plugin_crash.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "plugin_crash.error_message_contains_crash", jsonBooleanField(text, "errorMessageContainsCrash"));
    putPayload(payload, "plugin_crash.has_crash_event", jsonBooleanField(text, "hasCrashEvent"));
    putPayload(payload, "plugin_crash.crash_event_reason", jsonStringField(text, "crashEventReason"));
    putPayload(payload, "plugin_crash.crash_event_exit_code", jsonNumberField(text, "crashEventExitCode"));
    putPayload(payload, "plugin_crash.has_pending_request_failure", jsonBooleanField(text, "hasPendingRequestFailure"));
    putPayload(payload, "plugin_crash.pending_request_error_code", jsonStringField(text, "pendingRequestErrorCode"));
    putPayload(payload, "plugin_crash.dispose_after_crash_ok", jsonBooleanField(text, "disposeAfterCrashOk"));
    putPayload(payload, "plugin_crash.dispose_after_crash_error_code", jsonStringField(text, "disposeAfterCrashErrorCode"));
    putPayload(payload, "plugin_crash.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_crash.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_crash.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_crash.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_crash.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_crash.detail", error);
    }
}

void putPluginBackpressureFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_backpressure.result_text", text);
    putPayload(payload, "plugin_backpressure.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_backpressure.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_backpressure.output_event_count", jsonNumberField(text, "outputEventCount"));
    putPayload(payload, "plugin_backpressure.stdout_event_count", jsonNumberField(text, "stdoutEventCount"));
    putPayload(payload, "plugin_backpressure.stderr_event_count", jsonNumberField(text, "stderrEventCount"));
    putPayload(payload, "plugin_backpressure.queue_soft_limit", jsonNumberField(text, "queueSoftLimit"));
    putPayload(payload, "plugin_backpressure.backpressure_triggered", jsonBooleanField(text, "backpressureTriggered"));
    putPayload(payload, "plugin_backpressure.has_backpressure_event", jsonBooleanField(text, "hasBackpressureEvent"));
    putPayload(payload, "plugin_backpressure.backpressure_event_level", jsonStringField(text, "backpressureEventLevel"));
    putPayload(payload, "plugin_backpressure.dropped_event_count", jsonNumberField(text, "droppedEventCount"));
    putPayload(payload, "plugin_backpressure.summary_event_count", jsonNumberField(text, "summaryEventCount"));
    putPayload(payload, "plugin_backpressure.has_summary_event", jsonBooleanField(text, "hasSummaryEvent"));
    putPayload(payload, "plugin_backpressure.summary_contains_dropped_count", jsonBooleanField(text, "summaryContainsDroppedCount"));
    putPayload(payload, "plugin_backpressure.has_completed_event", jsonBooleanField(text, "hasCompletedEvent"));
    putPayload(payload, "plugin_backpressure.completed_exit_code", jsonNumberField(text, "completedExitCode"));
    putPayload(payload, "plugin_backpressure.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_backpressure.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_backpressure.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_backpressure.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_backpressure.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_backpressure.detail", error);
    }
}

void putPluginNegotiationFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_negotiation.result_text", text);
    putPayload(payload, "plugin_negotiation.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_negotiation.method", jsonStringField(text, "method"));
    putPayload(payload, "plugin_negotiation.host_versions", jsonStringField(text, "hostVersions"));
    putPayload(payload, "plugin_negotiation.plugin_versions", jsonStringField(text, "pluginVersions"));
    putPayload(payload, "plugin_negotiation.selected_version", jsonNumberField(text, "selectedVersion"));
    putPayload(payload, "plugin_negotiation.compatible", jsonBooleanField(text, "compatible"));
    putPayload(payload, "plugin_negotiation.host_capability_count", jsonNumberField(text, "hostCapabilityCount"));
    putPayload(payload, "plugin_negotiation.plugin_capability_count", jsonNumberField(text, "pluginCapabilityCount"));
    putPayload(payload, "plugin_negotiation.negotiated_capability_count", jsonNumberField(text, "negotiatedCapabilityCount"));
    putPayload(payload, "plugin_negotiation.has_run_script", jsonBooleanField(text, "hasRunScript"));
    putPayload(payload, "plugin_negotiation.has_cancel", jsonBooleanField(text, "hasCancel"));
    putPayload(payload, "plugin_negotiation.has_dispose", jsonBooleanField(text, "hasDispose"));
    putPayload(payload, "plugin_negotiation.has_output_events", jsonBooleanField(text, "hasOutputEvents"));
    putPayload(payload, "plugin_negotiation.has_backpressure", jsonBooleanField(text, "hasBackpressure"));
    putPayload(payload, "plugin_negotiation.has_crash_events", jsonBooleanField(text, "hasCrashEvents"));
    putPayload(payload, "plugin_negotiation.rejected_capability_count", jsonNumberField(text, "rejectedCapabilityCount"));
    putPayload(payload, "plugin_negotiation.has_rejected_require", jsonBooleanField(text, "hasRejectedRequire"));
    putPayload(payload, "plugin_negotiation.has_rejected_npm", jsonBooleanField(text, "hasRejectedNpm"));
    putPayload(payload, "plugin_negotiation.has_rejected_android_bridge", jsonBooleanField(text, "hasRejectedAndroidBridge"));
    putPayload(payload, "plugin_negotiation.has_rejected_autojs_api", jsonBooleanField(text, "hasRejectedAutojsApi"));
    putPayload(payload, "plugin_negotiation.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_negotiation.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_negotiation.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_negotiation.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_negotiation.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_negotiation.detail", error);
    }
}

void putPluginPermissionFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_permission.result_text", text);
    putPayload(payload, "plugin_permission.requested_capability_count", jsonNumberField(text, "requestedCapabilityCount"));
    putPayload(payload, "plugin_permission.granted_capability_count", jsonNumberField(text, "grantedCapabilityCount"));
    putPayload(payload, "plugin_permission.denied_capability_count", jsonNumberField(text, "deniedCapabilityCount"));
    putPayload(payload, "plugin_permission.has_granted_run_script", jsonBooleanField(text, "hasGrantedRunScript"));
    putPayload(payload, "plugin_permission.has_granted_output_events", jsonBooleanField(text, "hasGrantedOutputEvents"));
    putPayload(payload, "plugin_permission.has_granted_cancel", jsonBooleanField(text, "hasGrantedCancel"));
    putPayload(payload, "plugin_permission.has_granted_dispose", jsonBooleanField(text, "hasGrantedDispose"));
    putPayload(payload, "plugin_permission.has_denied_require", jsonBooleanField(text, "hasDeniedRequire"));
    putPayload(payload, "plugin_permission.has_denied_npm", jsonBooleanField(text, "hasDeniedNpm"));
    putPayload(payload, "plugin_permission.has_denied_android_bridge", jsonBooleanField(text, "hasDeniedAndroidBridge"));
    putPayload(payload, "plugin_permission.has_denied_autojs_api", jsonBooleanField(text, "hasDeniedAutojsApi"));
    putPayload(payload, "plugin_permission.denied_error_code", jsonStringField(text, "deniedErrorCode"));
    putPayload(payload, "plugin_permission.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_permission.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_permission.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_permission.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_permission.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_permission.detail", error);
    }
}

void putPluginManifestFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_manifest.result_text", text);
    putPayload(payload, "plugin_manifest.valid_manifest_ok", jsonBooleanField(text, "validManifestOk"));
    putPayload(payload, "plugin_manifest.checked_manifest_count", jsonNumberField(text, "checkedManifestCount"));
    putPayload(payload, "plugin_manifest.accepted_count", jsonNumberField(text, "acceptedCount"));
    putPayload(payload, "plugin_manifest.rejected_count", jsonNumberField(text, "rejectedCount"));
    putPayload(payload, "plugin_manifest.has_missing_runtime_id_error", jsonBooleanField(text, "hasMissingRuntimeIdError"));
    putPayload(payload, "plugin_manifest.missing_runtime_id_error_code", jsonStringField(text, "missingRuntimeIdErrorCode"));
    putPayload(payload, "plugin_manifest.has_unsupported_protocol_error", jsonBooleanField(text, "hasUnsupportedProtocolError"));
    putPayload(payload, "plugin_manifest.unsupported_protocol_error_code", jsonStringField(text, "unsupportedProtocolErrorCode"));
    putPayload(payload, "plugin_manifest.has_invalid_capability_error", jsonBooleanField(text, "hasInvalidCapabilityError"));
    putPayload(payload, "plugin_manifest.invalid_capability_error_code", jsonStringField(text, "invalidCapabilityErrorCode"));
    putPayload(payload, "plugin_manifest.has_invalid_entrypoint_error", jsonBooleanField(text, "hasInvalidEntrypointError"));
    putPayload(payload, "plugin_manifest.invalid_entrypoint_error_code", jsonStringField(text, "invalidEntrypointErrorCode"));
    putPayload(payload, "plugin_manifest.all_errors_have_code", jsonBooleanField(text, "allErrorsHaveCode"));
    putPayload(payload, "plugin_manifest.all_errors_have_message", jsonBooleanField(text, "allErrorsHaveMessage"));
    putPayload(payload, "plugin_manifest.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_manifest.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_manifest.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_manifest.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_manifest.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_manifest.detail", error);
    }
}

void putPluginAbiFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_abi.result_text", text);
    putPayload(payload, "plugin_abi.host_abis", jsonStringField(text, "hostAbis"));
    putPayload(payload, "plugin_abi.plugin_abis", jsonStringField(text, "pluginAbis"));
    putPayload(payload, "plugin_abi.intersection", jsonStringField(text, "intersection"));
    putPayload(payload, "plugin_abi.selected_abi", jsonStringField(text, "selectedAbi"));
    putPayload(payload, "plugin_abi.compatible", jsonBooleanField(text, "compatible"));
    putPayload(payload, "plugin_abi.unsupported_abi_count", jsonNumberField(text, "unsupportedAbiCount"));
    putPayload(payload, "plugin_abi.has_unsupported_x86", jsonBooleanField(text, "hasUnsupportedX86"));
    putPayload(payload, "plugin_abi.no_intersection_case_checked", jsonBooleanField(text, "noIntersectionCaseChecked"));
    putPayload(payload, "plugin_abi.no_intersection_error_code", jsonStringField(text, "noIntersectionErrorCode"));
    putPayload(payload, "plugin_abi.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_abi.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_abi.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_abi.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_abi.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_abi.detail", error);
    }
}

void putPluginLibraryFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_library.result_text", text);
    putPayload(payload, "plugin_library.required_library_count", jsonNumberField(text, "requiredLibraryCount"));
    putPayload(payload, "plugin_library.resolved_library_count", jsonNumberField(text, "resolvedLibraryCount"));
    putPayload(payload, "plugin_library.has_libnode", jsonBooleanField(text, "hasLibnode"));
    putPayload(payload, "plugin_library.libnode_name", jsonStringField(text, "libnodeName"));
    putPayload(payload, "plugin_library.libnode_version", jsonStringField(text, "libnodeVersion"));
    putPayload(payload, "plugin_library.has_bridge", jsonBooleanField(text, "hasBridge"));
    putPayload(payload, "plugin_library.bridge_name", jsonStringField(text, "bridgeName"));
    putPayload(payload, "plugin_library.missing_required_case_checked", jsonBooleanField(text, "missingRequiredCaseChecked"));
    putPayload(payload, "plugin_library.missing_required_error_code", jsonStringField(text, "missingRequiredErrorCode"));
    putPayload(payload, "plugin_library.version_mismatch_case_checked", jsonBooleanField(text, "versionMismatchCaseChecked"));
    putPayload(payload, "plugin_library.version_mismatch_error_code", jsonStringField(text, "versionMismatchErrorCode"));
    putPayload(payload, "plugin_library.resolution_ok", jsonBooleanField(text, "resolutionOk"));
    putPayload(payload, "plugin_library.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_library.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_library.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_library.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_library.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_library.detail", error);
    }
}

void putPluginInstanceFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_instance.result_text", text);
    putPayload(payload, "plugin_instance.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_instance.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_instance.instance_created", jsonBooleanField(text, "instanceCreated"));
    putPayload(payload, "plugin_instance.instance_state", jsonStringField(text, "instanceState"));
    putPayload(payload, "plugin_instance.has_manifest", jsonBooleanField(text, "hasManifest"));
    putPayload(payload, "plugin_instance.has_selected_abi", jsonBooleanField(text, "hasSelectedAbi"));
    putPayload(payload, "plugin_instance.has_resolved_libraries", jsonBooleanField(text, "hasResolvedLibraries"));
    putPayload(payload, "plugin_instance.has_negotiated_protocol", jsonBooleanField(text, "hasNegotiatedProtocol"));
    putPayload(payload, "plugin_instance.has_permission_policy", jsonBooleanField(text, "hasPermissionPolicy"));
    putPayload(payload, "plugin_instance.create_response_ok", jsonBooleanField(text, "createResponseOk"));
    putPayload(payload, "plugin_instance.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_instance.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_instance.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_instance.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_instance.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_instance.detail", error);
    }
}

void putPluginSessionFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_session.result_text", text);
    putPayload(payload, "plugin_session.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_session.registered", jsonBooleanField(text, "registered"));
    putPayload(payload, "plugin_session.lookup_ok", jsonBooleanField(text, "lookupOk"));
    putPayload(payload, "plugin_session.duplicate_rejected", jsonBooleanField(text, "duplicateRejected"));
    putPayload(payload, "plugin_session.duplicate_error_code", jsonStringField(text, "duplicateErrorCode"));
    putPayload(payload, "plugin_session.disposed", jsonBooleanField(text, "disposed"));
    putPayload(payload, "plugin_session.lookup_after_dispose_rejected", jsonBooleanField(text, "lookupAfterDisposeRejected"));
    putPayload(payload, "plugin_session.lookup_after_dispose_error_code", jsonStringField(text, "lookupAfterDisposeErrorCode"));
    putPayload(payload, "plugin_session.active_session_count_after_register", jsonNumberField(text, "activeSessionCountAfterRegister"));
    putPayload(payload, "plugin_session.active_session_count_after_dispose", jsonNumberField(text, "activeSessionCountAfterDispose"));
    putPayload(payload, "plugin_session.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_session.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_session.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_session.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_session.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_session.detail", error);
    }
}

void putPluginQueueFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_queue.result_text", text);
    putPayload(payload, "plugin_queue.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_queue.request_count", jsonNumberField(text, "requestCount"));
    putPayload(payload, "plugin_queue.accepted_count", jsonNumberField(text, "acceptedCount"));
    putPayload(payload, "plugin_queue.queued_count_initial", jsonNumberField(text, "queuedCountInitial"));
    putPayload(payload, "plugin_queue.started_count", jsonNumberField(text, "startedCount"));
    putPayload(payload, "plugin_queue.completed_count", jsonNumberField(text, "completedCount"));
    putPayload(payload, "plugin_queue.queue_drained", jsonBooleanField(text, "queueDrained"));
    putPayload(payload, "plugin_queue.max_queue_size", jsonNumberField(text, "maxQueueSize"));
    putPayload(payload, "plugin_queue.final_queue_size", jsonNumberField(text, "finalQueueSize"));
    putPayload(payload, "plugin_queue.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_queue.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_queue.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_queue.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_queue.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_queue.detail", error);
    }
}

void putPluginConcurrencyFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_concurrency.result_text", text);
    putPayload(payload, "plugin_concurrency.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_concurrency.first_run_accepted", jsonBooleanField(text, "firstRunAccepted"));
    putPayload(payload, "plugin_concurrency.second_run_rejected", jsonBooleanField(text, "secondRunRejected"));
    putPayload(payload, "plugin_concurrency.second_run_error_code", jsonStringField(text, "secondRunErrorCode"));
    putPayload(payload, "plugin_concurrency.cancel_while_running_accepted", jsonBooleanField(text, "cancelWhileRunningAccepted"));
    putPayload(payload, "plugin_concurrency.run_after_cancel_accepted", jsonBooleanField(text, "runAfterCancelAccepted"));
    putPayload(payload, "plugin_concurrency.run_after_completion_accepted", jsonBooleanField(text, "runAfterCompletionAccepted"));
    putPayload(payload, "plugin_concurrency.max_concurrent_runs", jsonNumberField(text, "maxConcurrentRuns"));
    putPayload(payload, "plugin_concurrency.final_running_count", jsonNumberField(text, "finalRunningCount"));
    putPayload(payload, "plugin_concurrency.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_concurrency.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_concurrency.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_concurrency.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_concurrency.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_concurrency.detail", error);
    }
}

void putPluginRaceFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_race.result_text", text);
    putPayload(payload, "plugin_race.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_race.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "plugin_race.run_accepted", jsonBooleanField(text, "runAccepted"));
    putPayload(payload, "plugin_race.cancel_requested", jsonBooleanField(text, "cancelRequested"));
    putPayload(payload, "plugin_race.completion_requested", jsonBooleanField(text, "completionRequested"));
    putPayload(payload, "plugin_race.terminal_policy", jsonStringField(text, "terminalPolicy"));
    putPayload(payload, "plugin_race.completed_emitted", jsonBooleanField(text, "completedEmitted"));
    putPayload(payload, "plugin_race.cancelled_emitted", jsonBooleanField(text, "cancelledEmitted"));
    putPayload(payload, "plugin_race.terminal_event_count", jsonNumberField(text, "terminalEventCount"));
    putPayload(payload, "plugin_race.no_double_finalization", jsonBooleanField(text, "noDoubleFinalization"));
    putPayload(payload, "plugin_race.cancel_after_terminal_rejected", jsonBooleanField(text, "cancelAfterTerminalRejected"));
    putPayload(payload, "plugin_race.cancel_after_terminal_error_code", jsonStringField(text, "cancelAfterTerminalErrorCode"));
    putPayload(payload, "plugin_race.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_race.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_race.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_race.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_race.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_race.detail", error);
    }
}

void putPluginShutdownFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_shutdown.result_text", text);
    putPayload(payload, "plugin_shutdown.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_shutdown.running_before_shutdown", jsonBooleanField(text, "runningBeforeShutdown"));
    putPayload(payload, "plugin_shutdown.shutdown_requested", jsonBooleanField(text, "shutdownRequested"));
    putPayload(payload, "plugin_shutdown.new_request_rejected", jsonBooleanField(text, "newRequestRejected"));
    putPayload(payload, "plugin_shutdown.new_request_error_code", jsonStringField(text, "newRequestErrorCode"));
    putPayload(payload, "plugin_shutdown.running_script_cancelled", jsonBooleanField(text, "runningScriptCancelled"));
    putPayload(payload, "plugin_shutdown.cancel_exit_code", jsonNumberField(text, "cancelExitCode"));
    putPayload(payload, "plugin_shutdown.dispose_emitted", jsonBooleanField(text, "disposeEmitted"));
    putPayload(payload, "plugin_shutdown.shutdown_completed", jsonBooleanField(text, "shutdownCompleted"));
    putPayload(payload, "plugin_shutdown.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_shutdown.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_shutdown.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_shutdown.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_shutdown.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_shutdown.detail", error);
    }
}

void putPluginHeartbeatFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_heartbeat.result_text", text);
    putPayload(payload, "plugin_heartbeat.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_heartbeat.ping_count", jsonNumberField(text, "pingCount"));
    putPayload(payload, "plugin_heartbeat.pong_count", jsonNumberField(text, "pongCount"));
    putPayload(payload, "plugin_heartbeat.missed_count", jsonNumberField(text, "missedCount"));
    putPayload(payload, "plugin_heartbeat.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "plugin_heartbeat.has_heartbeat_event", jsonBooleanField(text, "hasHeartbeatEvent"));
    putPayload(payload, "plugin_heartbeat.has_missed_heartbeat_event", jsonBooleanField(text, "hasMissedHeartbeatEvent"));
    putPayload(payload, "plugin_heartbeat.degraded", jsonBooleanField(text, "degraded"));
    putPayload(payload, "plugin_heartbeat.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_heartbeat.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_heartbeat.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_heartbeat.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_heartbeat.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_heartbeat.detail", error);
    }
}

void putPluginHealthFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_health.result_text", text);
    putPayload(payload, "plugin_health.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_health.session_count", jsonNumberField(text, "sessionCount"));
    putPayload(payload, "plugin_health.active_session_count", jsonNumberField(text, "activeSessionCount"));
    putPayload(payload, "plugin_health.queue_size", jsonNumberField(text, "queueSize"));
    putPayload(payload, "plugin_health.running_count", jsonNumberField(text, "runningCount"));
    putPayload(payload, "plugin_health.state", jsonStringField(text, "state"));
    putPayload(payload, "plugin_health.degraded_reason", jsonStringField(text, "degradedReason"));
    putPayload(payload, "plugin_health.last_error_code", jsonStringField(text, "lastErrorCode"));
    putPayload(payload, "plugin_health.has_health_event", jsonBooleanField(text, "hasHealthEvent"));
    putPayload(payload, "plugin_health.summary_ok", jsonBooleanField(text, "summaryOk"));
    putPayload(payload, "plugin_health.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_health.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_health.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_health.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_health.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_health.detail", error);
    }
}

void putPluginTelemetryFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_telemetry.result_text", text);
    putPayload(payload, "plugin_telemetry.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_telemetry.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_telemetry.state", jsonStringField(text, "state"));
    putPayload(payload, "plugin_telemetry.protocol_version", jsonNumberField(text, "protocolVersion"));
    putPayload(payload, "plugin_telemetry.session_count", jsonNumberField(text, "sessionCount"));
    putPayload(payload, "plugin_telemetry.active_session_count", jsonNumberField(text, "activeSessionCount"));
    putPayload(payload, "plugin_telemetry.disposed_session_count", jsonNumberField(text, "disposedSessionCount"));
    putPayload(payload, "plugin_telemetry.queue_size", jsonNumberField(text, "queueSize"));
    putPayload(payload, "plugin_telemetry.running_count", jsonNumberField(text, "runningCount"));
    putPayload(payload, "plugin_telemetry.completed_count", jsonNumberField(text, "completedCount"));
    putPayload(payload, "plugin_telemetry.cancelled_count", jsonNumberField(text, "cancelledCount"));
    putPayload(payload, "plugin_telemetry.failed_count", jsonNumberField(text, "failedCount"));
    putPayload(payload, "plugin_telemetry.timed_out_count", jsonNumberField(text, "timedOutCount"));
    putPayload(payload, "plugin_telemetry.crashed_count", jsonNumberField(text, "crashedCount"));
    putPayload(payload, "plugin_telemetry.output_event_count", jsonNumberField(text, "outputEventCount"));
    putPayload(payload, "plugin_telemetry.stdout_event_count", jsonNumberField(text, "stdoutEventCount"));
    putPayload(payload, "plugin_telemetry.stderr_event_count", jsonNumberField(text, "stderrEventCount"));
    putPayload(payload, "plugin_telemetry.dropped_output_count", jsonNumberField(text, "droppedOutputCount"));
    putPayload(payload, "plugin_telemetry.backpressure_detected", jsonBooleanField(text, "backpressureDetected"));
    putPayload(payload, "plugin_telemetry.heartbeat_ping_count", jsonNumberField(text, "heartbeatPingCount"));
    putPayload(payload, "plugin_telemetry.heartbeat_pong_count", jsonNumberField(text, "heartbeatPongCount"));
    putPayload(payload, "plugin_telemetry.heartbeat_missed_count", jsonNumberField(text, "heartbeatMissedCount"));
    putPayload(payload, "plugin_telemetry.degraded", jsonBooleanField(text, "degraded"));
    putPayload(payload, "plugin_telemetry.degraded_reason", jsonStringField(text, "degradedReason"));
    putPayload(payload, "plugin_telemetry.last_error_code", jsonStringField(text, "lastErrorCode"));
    putPayload(payload, "plugin_telemetry.snapshot_ok", jsonBooleanField(text, "snapshotOk"));
    putPayload(payload, "plugin_telemetry.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_telemetry.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_telemetry.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_telemetry.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_telemetry.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_telemetry.detail", error);
    }
}

void putPluginDiagnosticsFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_diagnostics.result_text", text);
    putPayload(payload, "plugin_diagnostics.bundle_id", jsonStringField(text, "bundleId"));
    putPayload(payload, "plugin_diagnostics.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_diagnostics.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_diagnostics.includes_manifest", jsonBooleanField(text, "includesManifest"));
    putPayload(payload, "plugin_diagnostics.includes_protocol", jsonBooleanField(text, "includesProtocol"));
    putPayload(payload, "plugin_diagnostics.includes_permissions", jsonBooleanField(text, "includesPermissions"));
    putPayload(payload, "plugin_diagnostics.includes_abi", jsonBooleanField(text, "includesAbi"));
    putPayload(payload, "plugin_diagnostics.includes_libraries", jsonBooleanField(text, "includesLibraries"));
    putPayload(payload, "plugin_diagnostics.includes_instance", jsonBooleanField(text, "includesInstance"));
    putPayload(payload, "plugin_diagnostics.includes_session", jsonBooleanField(text, "includesSession"));
    putPayload(payload, "plugin_diagnostics.includes_queue", jsonBooleanField(text, "includesQueue"));
    putPayload(payload, "plugin_diagnostics.includes_health", jsonBooleanField(text, "includesHealth"));
    putPayload(payload, "plugin_diagnostics.includes_telemetry", jsonBooleanField(text, "includesTelemetry"));
    putPayload(payload, "plugin_diagnostics.section_count", jsonNumberField(text, "sectionCount"));
    putPayload(payload, "plugin_diagnostics.error_count", jsonNumberField(text, "errorCount"));
    putPayload(payload, "plugin_diagnostics.warning_count", jsonNumberField(text, "warningCount"));
    putPayload(payload, "plugin_diagnostics.info_count", jsonNumberField(text, "infoCount"));
    putPayload(payload, "plugin_diagnostics.has_last_error", jsonBooleanField(text, "hasLastError"));
    putPayload(payload, "plugin_diagnostics.last_error_code", jsonStringField(text, "lastErrorCode"));
    putPayload(payload, "plugin_diagnostics.has_degraded_reason", jsonBooleanField(text, "hasDegradedReason"));
    putPayload(payload, "plugin_diagnostics.degraded_reason", jsonStringField(text, "degradedReason"));
    putPayload(payload, "plugin_diagnostics.redacted_sensitive_fields", jsonBooleanField(text, "redactedSensitiveFields"));
    putPayload(payload, "plugin_diagnostics.bundle_serializable", jsonBooleanField(text, "bundleSerializable"));
    putPayload(payload, "plugin_diagnostics.bundle_size_bytes", jsonNumberField(text, "bundleSizeBytes"));
    putPayload(payload, "plugin_diagnostics.summary_ok", jsonBooleanField(text, "summaryOk"));
    putPayload(payload, "plugin_diagnostics.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_diagnostics.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_diagnostics.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_diagnostics.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_diagnostics.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_diagnostics.detail", error);
    }
}

void putPluginRecoveryFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_recovery.result_text", text);
    putPayload(payload, "plugin_recovery.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_recovery.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_recovery.input_state", jsonStringField(text, "inputState"));
    putPayload(payload, "plugin_recovery.input_error_code", jsonStringField(text, "inputErrorCode"));
    putPayload(payload, "plugin_recovery.input_degraded_reason", jsonStringField(text, "inputDegradedReason"));
    putPayload(payload, "plugin_recovery.plan_id", jsonStringField(text, "planId"));
    putPayload(payload, "plugin_recovery.plan_generated", jsonBooleanField(text, "planGenerated"));
    putPayload(payload, "plugin_recovery.action_count", jsonNumberField(text, "actionCount"));
    putPayload(payload, "plugin_recovery.has_cancel_action", jsonBooleanField(text, "hasCancelAction"));
    putPayload(payload, "plugin_recovery.has_drain_queue_action", jsonBooleanField(text, "hasDrainQueueAction"));
    putPayload(payload, "plugin_recovery.has_dispose_action", jsonBooleanField(text, "hasDisposeAction"));
    putPayload(payload, "plugin_recovery.has_restart_action", jsonBooleanField(text, "hasRestartAction"));
    putPayload(payload, "plugin_recovery.cancel_action_order", jsonNumberField(text, "cancelActionOrder"));
    putPayload(payload, "plugin_recovery.drain_queue_action_order", jsonNumberField(text, "drainQueueActionOrder"));
    putPayload(payload, "plugin_recovery.dispose_action_order", jsonNumberField(text, "disposeActionOrder"));
    putPayload(payload, "plugin_recovery.restart_action_order", jsonNumberField(text, "restartActionOrder"));
    putPayload(payload, "plugin_recovery.retryable", jsonBooleanField(text, "retryable"));
    putPayload(payload, "plugin_recovery.requires_new_process", jsonBooleanField(text, "requiresNewProcess"));
    putPayload(payload, "plugin_recovery.preserve_session", jsonBooleanField(text, "preserveSession"));
    putPayload(payload, "plugin_recovery.preserve_diagnostics", jsonBooleanField(text, "preserveDiagnostics"));
    putPayload(payload, "plugin_recovery.recovery_reason", jsonStringField(text, "recoveryReason"));
    putPayload(payload, "plugin_recovery.recommended_policy", jsonStringField(text, "recommendedPolicy"));
    putPayload(payload, "plugin_recovery.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_recovery.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_recovery.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_recovery.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_recovery.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_recovery.detail", error);
    }
}

void putPluginRestartFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_restart.result_text", text);
    putPayload(payload, "plugin_restart.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_restart.previous_session_id", jsonStringField(text, "previousSessionId"));
    putPayload(payload, "plugin_restart.next_session_id", jsonStringField(text, "nextSessionId"));
    putPayload(payload, "plugin_restart.policy", jsonStringField(text, "policy"));
    putPayload(payload, "plugin_restart.reason", jsonStringField(text, "reason"));
    putPayload(payload, "plugin_restart.previous_state", jsonStringField(text, "previousState"));
    putPayload(payload, "plugin_restart.next_state", jsonStringField(text, "nextState"));
    putPayload(payload, "plugin_restart.max_attempts", jsonNumberField(text, "maxAttempts"));
    putPayload(payload, "plugin_restart.attempt_count", jsonNumberField(text, "attemptCount"));
    putPayload(payload, "plugin_restart.backoff_ms", jsonNumberField(text, "backoffMs"));
    putPayload(payload, "plugin_restart.backoff_policy", jsonStringField(text, "backoffPolicy"));
    putPayload(payload, "plugin_restart.jitter_enabled", jsonBooleanField(text, "jitterEnabled"));
    putPayload(payload, "plugin_restart.previous_runtime_disposed", jsonBooleanField(text, "previousRuntimeDisposed"));
    putPayload(payload, "plugin_restart.new_runtime_created", jsonBooleanField(text, "newRuntimeCreated"));
    putPayload(payload, "plugin_restart.session_recreated", jsonBooleanField(text, "sessionRecreated"));
    putPayload(payload, "plugin_restart.queue_reset", jsonBooleanField(text, "queueReset"));
    putPayload(payload, "plugin_restart.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "plugin_restart.restart_allowed", jsonBooleanField(text, "restartAllowed"));
    putPayload(payload, "plugin_restart.restart_scheduled", jsonBooleanField(text, "restartScheduled"));
    putPayload(payload, "plugin_restart.restart_executed", jsonBooleanField(text, "restartExecuted"));
    putPayload(payload, "plugin_restart.execution_mode", jsonStringField(text, "executionMode"));
    putPayload(payload, "plugin_restart.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_restart.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_restart.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_restart.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_restart.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_restart.detail", error);
    }
}

void putPluginRestartBudgetFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_restart_budget.result_text", text);
    putPayload(payload, "plugin_restart_budget.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_restart_budget.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_restart_budget.policy", jsonStringField(text, "policy"));
    putPayload(payload, "plugin_restart_budget.max_attempts", jsonNumberField(text, "maxAttempts"));
    putPayload(payload, "plugin_restart_budget.attempt_count", jsonNumberField(text, "attemptCount"));
    putPayload(payload, "plugin_restart_budget.remaining_attempts", jsonNumberField(text, "remainingAttempts"));
    putPayload(payload, "plugin_restart_budget.window_ms", jsonNumberField(text, "windowMs"));
    putPayload(payload, "plugin_restart_budget.first_failure_reason", jsonStringField(text, "firstFailureReason"));
    putPayload(payload, "plugin_restart_budget.last_failure_reason", jsonStringField(text, "lastFailureReason"));
    putPayload(payload, "plugin_restart_budget.last_error_code", jsonStringField(text, "lastErrorCode"));
    putPayload(payload, "plugin_restart_budget.restart_allowed", jsonBooleanField(text, "restartAllowed"));
    putPayload(payload, "plugin_restart_budget.circuit_open", jsonBooleanField(text, "circuitOpen"));
    putPayload(payload, "plugin_restart_budget.circuit_reason", jsonStringField(text, "circuitReason"));
    putPayload(payload, "plugin_restart_budget.next_retry_after_ms", jsonNumberField(text, "nextRetryAfterMs"));
    putPayload(payload, "plugin_restart_budget.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "plugin_restart_budget.requires_manual_intervention", jsonBooleanField(text, "requiresManualIntervention"));
    putPayload(payload, "plugin_restart_budget.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_restart_budget.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_restart_budget.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_restart_budget.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_restart_budget.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_restart_budget.detail", error);
    }
}

void putPluginQuarantineFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "plugin_quarantine.result_text", text);
    putPayload(payload, "plugin_quarantine.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "plugin_quarantine.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "plugin_quarantine.input_state", jsonStringField(text, "inputState"));
    putPayload(payload, "plugin_quarantine.input_error_code", jsonStringField(text, "inputErrorCode"));
    putPayload(payload, "plugin_quarantine.input_exit_code", jsonNumberField(text, "inputExitCode"));
    putPayload(payload, "plugin_quarantine.quarantine_id", jsonStringField(text, "quarantineId"));
    putPayload(payload, "plugin_quarantine.quarantined", jsonBooleanField(text, "quarantined"));
    putPayload(payload, "plugin_quarantine.reason", jsonStringField(text, "reason"));
    putPayload(payload, "plugin_quarantine.scope", jsonStringField(text, "scope"));
    putPayload(payload, "plugin_quarantine.restart_allowed", jsonBooleanField(text, "restartAllowed"));
    putPayload(payload, "plugin_quarantine.new_requests_allowed", jsonBooleanField(text, "newRequestsAllowed"));
    putPayload(payload, "plugin_quarantine.active_sessions_terminated", jsonBooleanField(text, "activeSessionsTerminated"));
    putPayload(payload, "plugin_quarantine.pending_requests_failed", jsonBooleanField(text, "pendingRequestsFailed"));
    putPayload(payload, "plugin_quarantine.queue_drained", jsonBooleanField(text, "queueDrained"));
    putPayload(payload, "plugin_quarantine.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "plugin_quarantine.crash_report_attached", jsonBooleanField(text, "crashReportAttached"));
    putPayload(payload, "plugin_quarantine.manual_clear_required", jsonBooleanField(text, "manualClearRequired"));
    putPayload(payload, "plugin_quarantine.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "plugin_quarantine.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "plugin_quarantine.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "plugin_quarantine.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "plugin_quarantine.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "plugin_quarantine.detail", error);
    }
}

void putScriptDescriptorFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_descriptor.result_text", text);
    putPayload(payload, "script_descriptor.descriptor_id", jsonStringField(text, "descriptorId"));
    putPayload(payload, "script_descriptor.source_type", jsonStringField(text, "sourceType"));
    putPayload(payload, "script_descriptor.path", jsonStringField(text, "path"));
    putPayload(payload, "script_descriptor.working_directory", jsonStringField(text, "workingDirectory"));
    putPayload(payload, "script_descriptor.file_name", jsonStringField(text, "fileName"));
    putPayload(payload, "script_descriptor.extension", jsonStringField(text, "extension"));
    putPayload(payload, "script_descriptor.has_valid_path", jsonBooleanField(text, "hasValidPath"));
    putPayload(payload, "script_descriptor.has_valid_extension", jsonBooleanField(text, "hasValidExtension"));
    putPayload(payload, "script_descriptor.has_working_directory", jsonBooleanField(text, "hasWorkingDirectory"));
    putPayload(payload, "script_descriptor.has_entrypoint", jsonBooleanField(text, "hasEntrypoint"));
    putPayload(payload, "script_descriptor.argv_count", jsonNumberField(text, "argvCount"));
    putPayload(payload, "script_descriptor.env_count", jsonNumberField(text, "envCount"));
    putPayload(payload, "script_descriptor.encoding", jsonStringField(text, "encoding"));
    putPayload(payload, "script_descriptor.accepted_descriptor_count", jsonNumberField(text, "acceptedDescriptorCount"));
    putPayload(payload, "script_descriptor.rejected_descriptor_count", jsonNumberField(text, "rejectedDescriptorCount"));
    putPayload(payload, "script_descriptor.reject_missing_path_error_code", jsonStringField(text, "rejectMissingPathErrorCode"));
    putPayload(payload, "script_descriptor.reject_invalid_extension_error_code", jsonStringField(text, "rejectInvalidExtensionErrorCode"));
    putPayload(payload, "script_descriptor.reject_missing_working_directory_error_code", jsonStringField(text, "rejectMissingWorkingDirectoryErrorCode"));
    putPayload(payload, "script_descriptor.reject_invalid_encoding_error_code", jsonStringField(text, "rejectInvalidEncodingErrorCode"));
    putPayload(payload, "script_descriptor.validation_ok", jsonBooleanField(text, "validationOk"));
    putPayload(payload, "script_descriptor.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_descriptor.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_descriptor.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_descriptor.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_descriptor.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_descriptor.detail", error);
    }
}

void putScriptNormalizationFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_normalization.result_text", text);
    putPayload(payload, "script_normalization.descriptor_id", jsonStringField(text, "descriptorId"));
    putPayload(payload, "script_normalization.input_source_type", jsonStringField(text, "inputSourceType"));
    putPayload(payload, "script_normalization.normalized_source_type", jsonStringField(text, "normalizedSourceType"));
    putPayload(payload, "script_normalization.normalized_file_name", jsonStringField(text, "normalizedFileName"));
    putPayload(payload, "script_normalization.normalized_url", jsonStringField(text, "normalizedUrl"));
    putPayload(payload, "script_normalization.normalized_cwd", jsonStringField(text, "normalizedCwd"));
    putPayload(payload, "script_normalization.normalized_encoding", jsonStringField(text, "normalizedEncoding"));
    putPayload(payload, "script_normalization.has_shebang", jsonBooleanField(text, "hasShebang"));
    putPayload(payload, "script_normalization.shebang_stripped", jsonBooleanField(text, "shebangStripped"));
    putPayload(payload, "script_normalization.has_bom", jsonBooleanField(text, "hasBom"));
    putPayload(payload, "script_normalization.bom_stripped", jsonBooleanField(text, "bomStripped"));
    putPayload(payload, "script_normalization.line_ending_input", jsonStringField(text, "lineEndingInput"));
    putPayload(payload, "script_normalization.line_ending_normalized", jsonStringField(text, "lineEndingNormalized"));
    putPayload(payload, "script_normalization.source_length_before", jsonNumberField(text, "sourceLengthBefore"));
    putPayload(payload, "script_normalization.source_length_after", jsonNumberField(text, "sourceLengthAfter"));
    putPayload(payload, "script_normalization.source_hash", jsonStringField(text, "sourceHash"));
    putPayload(payload, "script_normalization.source_map_url_preserved", jsonBooleanField(text, "sourceMapUrlPreserved"));
    putPayload(payload, "script_normalization.stack_trace_url_ready", jsonBooleanField(text, "stackTraceUrlReady"));
    putPayload(payload, "script_normalization.normalization_ok", jsonBooleanField(text, "normalizationOk"));
    putPayload(payload, "script_normalization.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_normalization.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_normalization.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_normalization.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_normalization.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_normalization.detail", error);
    }
}

void putScriptContextFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_context.result_text", text);
    putPayload(payload, "script_context.context_id", jsonStringField(text, "contextId"));
    putPayload(payload, "script_context.descriptor_id", jsonStringField(text, "descriptorId"));
    putPayload(payload, "script_context.runtime_id", jsonStringField(text, "runtimeId"));
    putPayload(payload, "script_context.session_id", jsonStringField(text, "sessionId"));
    putPayload(payload, "script_context.cwd", jsonStringField(text, "cwd"));
    putPayload(payload, "script_context.argv_count", jsonNumberField(text, "argvCount"));
    putPayload(payload, "script_context.env_count", jsonNumberField(text, "envCount"));
    putPayload(payload, "script_context.has_process_argv", jsonBooleanField(text, "hasProcessArgv"));
    putPayload(payload, "script_context.has_process_env", jsonBooleanField(text, "hasProcessEnv"));
    putPayload(payload, "script_context.has_process_cwd", jsonBooleanField(text, "hasProcessCwd"));
    putPayload(payload, "script_context.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "script_context.memory_limit_mb", jsonNumberField(text, "memoryLimitMb"));
    putPayload(payload, "script_context.cancellation_token_id", jsonStringField(text, "cancellationTokenId"));
    putPayload(payload, "script_context.stdout_mode", jsonStringField(text, "stdoutMode"));
    putPayload(payload, "script_context.stderr_mode", jsonStringField(text, "stderrMode"));
    putPayload(payload, "script_context.output_backpressure_policy", jsonStringField(text, "outputBackpressurePolicy"));
    putPayload(payload, "script_context.require_policy", jsonStringField(text, "requirePolicy"));
    putPayload(payload, "script_context.npm_policy", jsonStringField(text, "npmPolicy"));
    putPayload(payload, "script_context.android_bridge_policy", jsonStringField(text, "androidBridgePolicy"));
    putPayload(payload, "script_context.autojs_api_policy", jsonStringField(text, "autojsApiPolicy"));
    putPayload(payload, "script_context.context_created", jsonBooleanField(text, "contextCreated"));
    putPayload(payload, "script_context.context_valid", jsonBooleanField(text, "contextValid"));
    putPayload(payload, "script_context.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_context.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_context.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_context.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_context.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_context.detail", error);
    }
}

void putScriptResultV2Fields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_result_v2.result_text", text);
    putPayload(payload, "script_result_v2.result_id", jsonStringField(text, "resultId"));
    putPayload(payload, "script_result_v2.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "script_result_v2.context_id", jsonStringField(text, "contextId"));
    putPayload(payload, "script_result_v2.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "script_result_v2.signal", jsonStringField(text, "signal"));
    putPayload(payload, "script_result_v2.reason", jsonStringField(text, "reason"));
    putPayload(payload, "script_result_v2.has_value", jsonBooleanField(text, "hasValue"));
    putPayload(payload, "script_result_v2.value_type", jsonStringField(text, "valueType"));
    putPayload(payload, "script_result_v2.value_preview", jsonStringField(text, "valuePreview"));
    putPayload(payload, "script_result_v2.has_error", jsonBooleanField(text, "hasError"));
    putPayload(payload, "script_result_v2.error_code", jsonStringField(text, "errorCode"));
    putPayload(payload, "script_result_v2.error_name", jsonStringField(text, "errorName"));
    putPayload(payload, "script_result_v2.error_message", jsonStringField(text, "errorMessage"));
    putPayload(payload, "script_result_v2.stack_available", jsonBooleanField(text, "stackAvailable"));
    putPayload(payload, "script_result_v2.stdout_event_count", jsonNumberField(text, "stdoutEventCount"));
    putPayload(payload, "script_result_v2.stderr_event_count", jsonNumberField(text, "stderrEventCount"));
    putPayload(payload, "script_result_v2.output_dropped_count", jsonNumberField(text, "outputDroppedCount"));
    putPayload(payload, "script_result_v2.started_at_ms", jsonNumberField(text, "startedAtMs"));
    putPayload(payload, "script_result_v2.completed_at_ms", jsonNumberField(text, "completedAtMs"));
    putPayload(payload, "script_result_v2.duration_ms", jsonNumberField(text, "durationMs"));
    putPayload(payload, "script_result_v2.completed_event_emitted", jsonBooleanField(text, "completedEventEmitted"));
    putPayload(payload, "script_result_v2.result_serializable", jsonBooleanField(text, "resultSerializable"));
    putPayload(payload, "script_result_v2.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_result_v2.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_result_v2.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_result_v2.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_result_v2.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_result_v2.detail", error);
    }
}

void putScriptCancellationTokenFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_cancellation_token.result_text", text);
    putPayload(payload, "script_cancellation_token.token_id", jsonStringField(text, "tokenId"));
    putPayload(payload, "script_cancellation_token.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "script_cancellation_token.context_id", jsonStringField(text, "contextId"));
    putPayload(payload, "script_cancellation_token.created", jsonBooleanField(text, "created"));
    putPayload(payload, "script_cancellation_token.initial_state", jsonStringField(text, "initialState"));
    putPayload(payload, "script_cancellation_token.cancel_requested", jsonBooleanField(text, "cancelRequested"));
    putPayload(payload, "script_cancellation_token.cancel_reason", jsonStringField(text, "cancelReason"));
    putPayload(payload, "script_cancellation_token.cancel_signal", jsonStringField(text, "cancelSignal"));
    putPayload(payload, "script_cancellation_token.cancel_exit_code", jsonNumberField(text, "cancelExitCode"));
    putPayload(payload, "script_cancellation_token.observed_by_runtime", jsonBooleanField(text, "observedByRuntime"));
    putPayload(payload, "script_cancellation_token.observed_by_script", jsonBooleanField(text, "observedByScript"));
    putPayload(payload, "script_cancellation_token.terminal_event_emitted", jsonBooleanField(text, "terminalEventEmitted"));
    putPayload(payload, "script_cancellation_token.cancelled_event_emitted", jsonBooleanField(text, "cancelledEventEmitted"));
    putPayload(payload, "script_cancellation_token.request_after_cancel_allowed", jsonBooleanField(text, "requestAfterCancelAllowed"));
    putPayload(payload, "script_cancellation_token.request_after_cancel_error_code", jsonStringField(text, "requestAfterCancelErrorCode"));
    putPayload(payload, "script_cancellation_token.idempotent_cancel", jsonBooleanField(text, "idempotentCancel"));
    putPayload(payload, "script_cancellation_token.second_cancel_ignored", jsonBooleanField(text, "secondCancelIgnored"));
    putPayload(payload, "script_cancellation_token.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_cancellation_token.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_cancellation_token.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_cancellation_token.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_cancellation_token.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_cancellation_token.detail", error);
    }
}

void putScriptTimeoutPolicyFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_timeout_policy.result_text", text);
    putPayload(payload, "script_timeout_policy.policy_id", jsonStringField(text, "policyId"));
    putPayload(payload, "script_timeout_policy.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "script_timeout_policy.context_id", jsonStringField(text, "contextId"));
    putPayload(payload, "script_timeout_policy.timeout_ms", jsonNumberField(text, "timeoutMs"));
    putPayload(payload, "script_timeout_policy.grace_period_ms", jsonNumberField(text, "gracePeriodMs"));
    putPayload(payload, "script_timeout_policy.hard_kill_after_ms", jsonNumberField(text, "hardKillAfterMs"));
    putPayload(payload, "script_timeout_policy.timer_created", jsonBooleanField(text, "timerCreated"));
    putPayload(payload, "script_timeout_policy.timer_started", jsonBooleanField(text, "timerStarted"));
    putPayload(payload, "script_timeout_policy.timeout_detected", jsonBooleanField(text, "timeoutDetected"));
    putPayload(payload, "script_timeout_policy.timeout_reason", jsonStringField(text, "timeoutReason"));
    putPayload(payload, "script_timeout_policy.cancel_requested", jsonBooleanField(text, "cancelRequested"));
    putPayload(payload, "script_timeout_policy.cancel_exit_code", jsonNumberField(text, "cancelExitCode"));
    putPayload(payload, "script_timeout_policy.timeout_event_emitted", jsonBooleanField(text, "timeoutEventEmitted"));
    putPayload(payload, "script_timeout_policy.result_error_code", jsonStringField(text, "resultErrorCode"));
    putPayload(payload, "script_timeout_policy.dispose_after_timeout", jsonBooleanField(text, "disposeAfterTimeout"));
    putPayload(payload, "script_timeout_policy.restart_required", jsonBooleanField(text, "restartRequired"));
    putPayload(payload, "script_timeout_policy.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "script_timeout_policy.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_timeout_policy.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_timeout_policy.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_timeout_policy.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_timeout_policy.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_timeout_policy.detail", error);
    }
}

void putScriptMemoryPolicyFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_memory_policy.result_text", text);
    putPayload(payload, "script_memory_policy.policy_id", jsonStringField(text, "policyId"));
    putPayload(payload, "script_memory_policy.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "script_memory_policy.context_id", jsonStringField(text, "contextId"));
    putPayload(payload, "script_memory_policy.max_old_space_mb", jsonNumberField(text, "maxOldSpaceMb"));
    putPayload(payload, "script_memory_policy.max_heap_mb", jsonNumberField(text, "maxHeapMb"));
    putPayload(payload, "script_memory_policy.soft_limit_mb", jsonNumberField(text, "softLimitMb"));
    putPayload(payload, "script_memory_policy.hard_limit_mb", jsonNumberField(text, "hardLimitMb"));
    putPayload(payload, "script_memory_policy.memory_snapshot_enabled", jsonBooleanField(text, "memorySnapshotEnabled"));
    putPayload(payload, "script_memory_policy.initial_heap_used_mb", jsonNumberField(text, "initialHeapUsedMb"));
    putPayload(payload, "script_memory_policy.peak_heap_used_mb", jsonNumberField(text, "peakHeapUsedMb"));
    putPayload(payload, "script_memory_policy.limit_exceeded", jsonBooleanField(text, "limitExceeded"));
    putPayload(payload, "script_memory_policy.oom_error_code", jsonStringField(text, "oomErrorCode"));
    putPayload(payload, "script_memory_policy.oom_exit_code", jsonNumberField(text, "oomExitCode"));
    putPayload(payload, "script_memory_policy.dispose_on_oom", jsonBooleanField(text, "disposeOnOom"));
    putPayload(payload, "script_memory_policy.restart_required_on_oom", jsonBooleanField(text, "restartRequiredOnOom"));
    putPayload(payload, "script_memory_policy.diagnostics_preserved", jsonBooleanField(text, "diagnosticsPreserved"));
    putPayload(payload, "script_memory_policy.policy_valid", jsonBooleanField(text, "policyValid"));
    putPayload(payload, "script_memory_policy.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_memory_policy.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_memory_policy.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_memory_policy.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_memory_policy.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_memory_policy.detail", error);
    }
}

void putScriptPreflightSummaryFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    putPayload(payload, "script_preflight_summary.result_text", text);
    putPayload(payload, "script_preflight_summary.summary_id", jsonStringField(text, "summaryId"));
    putPayload(payload, "script_preflight_summary.request_id", jsonStringField(text, "requestId"));
    putPayload(payload, "script_preflight_summary.has_descriptor", jsonBooleanField(text, "hasDescriptor"));
    putPayload(payload, "script_preflight_summary.has_normalized_source", jsonBooleanField(text, "hasNormalizedSource"));
    putPayload(payload, "script_preflight_summary.has_context", jsonBooleanField(text, "hasContext"));
    putPayload(payload, "script_preflight_summary.has_result_envelope", jsonBooleanField(text, "hasResultEnvelope"));
    putPayload(payload, "script_preflight_summary.has_cancellation_token", jsonBooleanField(text, "hasCancellationToken"));
    putPayload(payload, "script_preflight_summary.has_timeout_policy", jsonBooleanField(text, "hasTimeoutPolicy"));
    putPayload(payload, "script_preflight_summary.has_memory_policy", jsonBooleanField(text, "hasMemoryPolicy"));
    putPayload(payload, "script_preflight_summary.descriptor_valid", jsonBooleanField(text, "descriptorValid"));
    putPayload(payload, "script_preflight_summary.source_normalized", jsonBooleanField(text, "sourceNormalized"));
    putPayload(payload, "script_preflight_summary.context_valid", jsonBooleanField(text, "contextValid"));
    putPayload(payload, "script_preflight_summary.result_envelope_ready", jsonBooleanField(text, "resultEnvelopeReady"));
    putPayload(payload, "script_preflight_summary.cancellation_ready", jsonBooleanField(text, "cancellationReady"));
    putPayload(payload, "script_preflight_summary.timeout_policy_ready", jsonBooleanField(text, "timeoutPolicyReady"));
    putPayload(payload, "script_preflight_summary.memory_policy_ready", jsonBooleanField(text, "memoryPolicyReady"));
    putPayload(payload, "script_preflight_summary.require_policy", jsonStringField(text, "requirePolicy"));
    putPayload(payload, "script_preflight_summary.npm_policy", jsonStringField(text, "npmPolicy"));
    putPayload(payload, "script_preflight_summary.android_bridge_policy", jsonStringField(text, "androidBridgePolicy"));
    putPayload(payload, "script_preflight_summary.autojs_api_policy", jsonStringField(text, "autojsApiPolicy"));
    putPayload(payload, "script_preflight_summary.ready_for_controlled_inline_execution", jsonBooleanField(text, "readyForControlledInlineExecution"));
    putPayload(payload, "script_preflight_summary.ready_for_user_file_execution", jsonBooleanField(text, "readyForUserFileExecution"));
    putPayload(payload, "script_preflight_summary.ready_for_require", jsonBooleanField(text, "readyForRequire"));
    putPayload(payload, "script_preflight_summary.ready_for_autojs_api", jsonBooleanField(text, "readyForAutojsApi"));
    putPayload(payload, "script_preflight_summary.final_state", jsonStringField(text, "finalState"));
    putPayload(payload, "script_preflight_summary.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "script_preflight_summary.event_count", jsonNumberField(text, "eventCount"));
    putPayload(payload, "script_preflight_summary.response_count", jsonNumberField(text, "responseCount"));
    putPayload(payload, "script_preflight_summary.node_version", jsonStringField(text, "nodeVersion"));
    if (!error.empty()) {
        putPayload(payload, "script_preflight_summary.detail", error);
    }
}

void putCompletionDrainFields(std::vector<std::string>& payload, const std::string& text) {
    const std::string error = fieldValueAfterPrefix(text, "error=");
    const std::string events = jsonArrayField(text, "events");
    putPayload(payload, "completion_drain.result_text", text);
    putPayload(payload, "completion_drain.started", jsonBooleanField(text, "started"));
    putPayload(payload, "completion_drain.completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "completion_drain.failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "completion_drain.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "completion_drain.value", jsonStringField(text, "value"));
    putPayload(payload, "completion_drain.events", events);
    putPayload(payload, "completion_drain.late_immediate_seen", jsonBooleanField(text, "lateImmediateSeen"));
    putPayload(payload, "completion_drain.late_stdout_seen", jsonBooleanField(text, "lateStdoutSeen"));
    putPayload(payload, "completion_drain.late_stderr_seen", jsonBooleanField(text, "lateStderrSeen"));
    putPayload(payload, "completion_drain.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "completion_drain.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "completion_drain.stdout_contains_sync", jsonBooleanField(text, "stdoutContainsSync"));
    putPayload(payload, "completion_drain.stdout_contains_first_immediate", jsonBooleanField(text, "stdoutContainsFirstImmediate"));
    putPayload(payload, "completion_drain.stdout_contains_late", jsonBooleanField(text, "stdoutContainsLate"));
    putPayload(payload, "completion_drain.stderr_contains_late", jsonBooleanField(text, "stderrContainsLate"));
    putPayload(payload, "completion_drain.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "completion_drain.detail", error);
    }
}

void putPostCompletionErrorFields(std::vector<std::string>& payload, const std::string& text) {
    const auto events = jsonArrayField(text, "events");
    const auto error = jsonStringField(text, "error");
    putPayload(payload, "post_completion_error.result_text", text);
    putPayload(payload, "post_completion_error.started", jsonBooleanField(text, "started"));
    putPayload(payload, "post_completion_error.completed", jsonBooleanField(text, "completed"));
    putPayload(payload, "post_completion_error.failed", jsonBooleanField(text, "failed"));
    putPayload(payload, "post_completion_error.exit_code", jsonNumberField(text, "exitCode"));
    putPayload(payload, "post_completion_error.value", jsonStringField(text, "value"));
    putPayload(payload, "post_completion_error.events", events);
    putPayload(payload, "post_completion_error.post_completion_error_seen", jsonBooleanField(text, "postCompletionErrorSeen"));
    putPayload(payload, "post_completion_error.post_completion_error_message", jsonStringField(text, "postCompletionErrorMessage"));
    putPayload(payload, "post_completion_error.stderr_contains_post_completion_error", jsonBooleanField(text, "stderrContainsPostCompletionError"));
    putPayload(payload, "post_completion_error.stdout_captured", jsonStringField(text, "stdoutCaptured"));
    putPayload(payload, "post_completion_error.stderr_captured", jsonStringField(text, "stderrCaptured"));
    putPayload(payload, "post_completion_error.contains_version", text.find(kJsResultExpectedText) != std::string::npos);
    if (!error.empty()) {
        putPayload(payload, "post_completion_error.detail", error);
    }
}

void putOutputEventsFields(std::vector<std::string>& payload, const std::string& text) {
    const auto events = jsonArrayField(text, "events");
    const auto outputEvents = jsonArrayField(text, "outputEvents");
    const auto error = jsonStringField(text, "error");
    putPayload(payload, "output_events.result_text", text);
    putPayload(payload, "output_events.events", events);
    putPayload(payload, "output_events.output_events", outputEvents);
    putPayload(payload, "output_events.output_event_count", jsonNumberField(text, "outputEventCount"));
    putPayload(payload, "output_events.stdout_event_count", jsonNumberField(text, "stdoutEventCount"));
    putPayload(payload, "output_events.stderr_event_count", jsonNumberField(text, "stderrEventCount"));
    putPayload(payload, "output_events.before_complete_count", jsonNumberField(text, "beforeCompleteCount"));
    putPayload(payload, "output_events.after_complete_count", jsonNumberField(text, "afterCompleteCount"));
    putPayload(payload, "output_events.contains_sync_stdout", jsonBooleanField(text, "containsSyncStdout"));
    putPayload(payload, "output_events.contains_sync_stderr", jsonBooleanField(text, "containsSyncStderr"));
    putPayload(payload, "output_events.contains_late_stdout", jsonBooleanField(text, "containsLateStdout"));
    putPayload(payload, "output_events.contains_late_stderr", jsonBooleanField(text, "containsLateStderr"));
    putPayload(payload, "output_events.contains_post_completion_error", jsonBooleanField(text, "containsPostCompletionError"));
    putPayload(payload, "output_events.fd_capture_stdout_matches", false);
    putPayload(payload, "output_events.fd_capture_stderr_matches", false);
    if (!error.empty()) {
        putPayload(payload, "output_events.detail", error);
    }
}

void putOutputEnvelopeFields(std::vector<std::string>& payload, const std::string& text) {
    const auto events = jsonArrayField(text, "events");
    const auto envelopes = jsonArrayField(text, "envelopes");
    const auto error = jsonStringField(text, "error");
    putPayload(payload, "output_envelope.result_text", text);
    putPayload(payload, "output_envelope.events", events);
    putPayload(payload, "output_envelope.envelopes", envelopes);
    putPayload(payload, "output_envelope.envelope_count", jsonNumberField(text, "envelopeCount"));
    putPayload(payload, "output_envelope.count", jsonNumberField(text, "count"));
    putPayload(payload, "output_envelope.sequence_monotonic", jsonBooleanField(text, "sequenceMonotonic"));
    putPayload(payload, "output_envelope.elapsed_monotonic", jsonBooleanField(text, "elapsedMonotonic"));
    putPayload(payload, "output_envelope.stdout_count", jsonNumberField(text, "stdoutCount"));
    putPayload(payload, "output_envelope.stderr_count", jsonNumberField(text, "stderrCount"));
    putPayload(payload, "output_envelope.info_count", jsonNumberField(text, "infoCount"));
    putPayload(payload, "output_envelope.error_count", jsonNumberField(text, "errorCount"));
    putPayload(payload, "output_envelope.stdout_info_count", jsonNumberField(text, "stdoutInfoCount"));
    putPayload(payload, "output_envelope.stderr_error_count", jsonNumberField(text, "stderrErrorCount"));
    putPayload(payload, "output_envelope.console_source_count", jsonNumberField(text, "consoleSourceCount"));
    putPayload(payload, "output_envelope.stdout_source_count", jsonNumberField(text, "stdoutSourceCount"));
    putPayload(payload, "output_envelope.stderr_source_count", jsonNumberField(text, "stderrSourceCount"));
    putPayload(payload, "output_envelope.error_handler_source_count", jsonNumberField(text, "errorHandlerSourceCount"));
    putPayload(payload, "output_envelope.before_complete_count", jsonNumberField(text, "beforeCompleteCount"));
    putPayload(payload, "output_envelope.after_complete_count", jsonNumberField(text, "afterCompleteCount"));
    putPayload(payload, "output_envelope.contains_sync_stdout", jsonBooleanField(text, "containsSyncStdout"));
    putPayload(payload, "output_envelope.contains_sync_stderr", jsonBooleanField(text, "containsSyncStderr"));
    putPayload(payload, "output_envelope.contains_late_stdout", jsonBooleanField(text, "containsLateStdout"));
    putPayload(payload, "output_envelope.contains_late_stderr", jsonBooleanField(text, "containsLateStderr"));
    putPayload(payload, "output_envelope.contains_post_completion_error", jsonBooleanField(text, "containsPostCompletionError"));
    if (!error.empty()) {
        putPayload(payload, "output_envelope.detail", error);
    }
}

bool readJsResultGlobal(
        v8::Isolate* isolate,
        v8::Local<v8::Context> context,
        V8ContextGlobal contextGlobal,
        V8StringNewFromUtf8 stringNewFromUtf8,
        V8ObjectGet objectGet,
        V8ValueToString valueToString,
        V8StringUtf8ValueConstructor utf8ValueConstructor,
        V8StringUtf8ValueDestructor utf8ValueDestructor,
        std::string& resultText,
        std::string& error
) {
    if (isolate == nullptr || context.IsEmpty()) {
        error = "isolate or context is unavailable";
        return false;
    }

    v8::Context* contextRaw = context.operator->();
    v8::Local<v8::Object> global = contextGlobal(contextRaw);
    if (global.IsEmpty()) {
        error = "Context::Global returned empty";
        return false;
    }

    v8::Local<v8::String> key;
    if (!stringNewFromUtf8(
            isolate,
            kJsResultPropertyName,
            v8::NewStringType::kNormal,
            -1
    ).ToLocal(&key)) {
        error = "String::NewFromUtf8 returned empty";
        return false;
    }

    v8::Local<v8::Value> value;
    if (!objectGet(global.operator->(), context, key.As<v8::Value>()).ToLocal(&value)) {
        error = "Object::Get returned empty";
        return false;
    }

    v8::Local<v8::String> stringValue;
    if (!valueToString(value.operator->(), context).ToLocal(&stringValue)) {
        error = "Value::ToString returned empty";
        return false;
    }

    alignas(v8::String::Utf8Value) unsigned char utf8Storage[sizeof(v8::String::Utf8Value)] = {};
    auto* utf8Value = reinterpret_cast<v8::String::Utf8Value*>(utf8Storage);
    utf8ValueConstructor(
            utf8Value,
            isolate,
            stringValue.As<v8::Value>(),
            v8::String::REPLACE_INVALID_UTF8
    );
    const char* chars = **utf8Value;
    if (chars == nullptr) {
        utf8ValueDestructor(utf8Value);
        error = "String::Utf8Value returned null";
        return false;
    }
    resultText.assign(chars, utf8Value->length());
    utf8ValueDestructor(utf8Value);
    return true;
}

void putUvLoopSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "uv.loop.init.status", "skipped");
    putPayload(payload, "uv.loop.init.detail", detail);
    putPayload(payload, "uv.loop.close.status", "skipped");
    putPayload(payload, "uv.loop.close.detail", detail);
    putPayload(payload, "timing.uv_loop_init.ms", static_cast<long long>(0));
    putPayload(payload, "timing.uv_loop_close.ms", static_cast<long long>(0));
}

void putV8SkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "v8.initialize_platform.status", "skipped");
    putPayload(payload, "v8.initialize_platform.detail", detail);
    putPayload(payload, "v8.initialize.status", "skipped");
    putPayload(payload, "v8.initialize.detail", detail);
    putPayload(payload, "timing.v8_initialize_platform.ms", static_cast<long long>(0));
    putPayload(payload, "timing.v8_initialize.ms", static_cast<long long>(0));
}

void putUvDiagnosticsSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "uv.diagnostics.status", "skipped");
    putPayload(payload, "uv.diagnostics.detail", detail);
    putPayload(payload, "uv.diagnostics.before_close.status", "skipped");
    putPayload(payload, "uv.diagnostics.before_close.detail", detail);
    putPayload(payload, "uv.diagnostics.after_close.status", "skipped");
    putPayload(payload, "uv.diagnostics.after_close.detail", detail);
    putPayload(payload, "uv.diagnostics.after_uv_close.status", "skipped");
    putPayload(payload, "uv.diagnostics.after_uv_close.detail", detail);
    putPayload(payload, "uv.diagnostics.after_loop_close.status", "skipped");
    putPayload(payload, "uv.diagnostics.after_loop_close.detail", detail);
    putPayload(payload, "timing.uv_diagnostics_before_close.ms", static_cast<long long>(0));
    putPayload(payload, "timing.uv_diagnostics_after_close.ms", static_cast<long long>(0));
    putPayload(payload, "timing.uv_diagnostics_after_uv_close.ms", static_cast<long long>(0));
    putPayload(payload, "timing.uv_diagnostics_after_loop_close.ms", static_cast<long long>(0));
}

void putUvCloseSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "uv.close.status", "skipped");
    putPayload(payload, "uv.close.detail", detail);
    putPayload(payload, "uv.close.attempted", static_cast<long long>(0));
    putPayload(payload, "uv.close.succeeded", static_cast<long long>(0));
    putPayload(payload, "uv.close.skipped", static_cast<long long>(0));
    putPayload(payload, "uv.close.failed", static_cast<long long>(0));
    putPayload(payload, "timing.uv_close.ms", static_cast<long long>(0));
}

void putUvRunSkippedPayload(std::vector<std::string>& payload, const char* detail) {
    putPayload(payload, "uv.run.status", "skipped");
    putPayload(payload, "uv.run.detail", detail);
    putPayload(payload, "uv.run.mode", "UV_RUN_NOWAIT");
    putPayload(payload, "uv.run.max_iterations", static_cast<long long>(kUvRunCleanupMaxIterations));
    putPayload(payload, "uv.run.completed_iterations", static_cast<long long>(0));
    putPayload(payload, "timing.uv_run.ms", static_cast<long long>(0));
}

const char* uvHandleTypeNameForPayload(uv_handle_type type) {
    switch (type) {
        case UV_ASYNC:
            return "async";
        case UV_CHECK:
            return "check";
        case UV_FS_EVENT:
            return "fs_event";
        case UV_FS_POLL:
            return "fs_poll";
        case UV_HANDLE:
            return "handle";
        case UV_IDLE:
            return "idle";
        case UV_NAMED_PIPE:
            return "pipe";
        case UV_POLL:
            return "poll";
        case UV_PREPARE:
            return "prepare";
        case UV_PROCESS:
            return "process";
        case UV_STREAM:
            return "stream";
        case UV_TCP:
            return "tcp";
        case UV_TIMER:
            return "timer";
        case UV_TTY:
            return "tty";
        case UV_UDP:
            return "udp";
        case UV_SIGNAL:
            return "signal";
        case UV_FILE:
            return "file";
        case UV_UNKNOWN_HANDLE:
        default:
            return "unknown";
    }
}

std::string pointerToHex(const void* pointer) {
    char buffer[32];
    std::snprintf(
            buffer,
            sizeof(buffer),
            "0x%" PRIxPTR,
            static_cast<uintptr_t>(reinterpret_cast<uintptr_t>(pointer))
    );
    return std::string(buffer);
}


EmbeddedLifecycleJsProbeKind resolveEmbeddedLifecycleJsProbeKind(
        bool jsResult,
        bool stdoutWriteRequested,
        bool consoleDiagnostics,
        bool consoleStream,
        bool consoleShape,
        bool consoleReplaceRequested,
        bool consoleFamilyRequested,
        bool consoleFormatRequested,
        bool consoleRejectionRequested,
        bool consoleUncaughtRequested,
        bool schedulingRequested,
        bool schedulingOrderRequested
) {
    // Keep the reserved flag combinations in one place until the public/native ABI can grow.
    const auto inlineProbeKind = embeddedInlineProbeKindFromSelectorFlags({
            jsResult,
            stdoutWriteRequested,
            consoleDiagnostics,
            consoleStream,
            consoleShape,
            consoleReplaceRequested,
            consoleFamilyRequested,
            consoleFormatRequested,
            consoleRejectionRequested,
            consoleUncaughtRequested,
            schedulingRequested,
            schedulingOrderRequested,
    });
    if (inlineProbeKind != EmbeddedLifecycleJsProbeKind::None) {
        return inlineProbeKind;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptCancellationToken;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptContext;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptResultV2;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptDescriptor;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptNormalization;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginRestartBudget;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginQuarantine;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginRecovery;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginRestart;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginTelemetry;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginDiagnostics;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginHeartbeat;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginHealth;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginShutdown;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginRace;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginConcurrency;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginQueue;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginSession;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginInstance;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginLibrary;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginAbi;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginManifest;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginPermission;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && !consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginNegotiation;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginTimeout;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginCrash;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginBackpressure;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginStateMachine;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginInvalidRequest;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginLifecycle;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && !consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginDispose;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && !consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginCancel;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginFailure;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginEvents;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && !consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginRequest;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleShape &&
        consoleUncaughtRequested && schedulingOrderRequested && schedulingRequested &&
        consoleRejectionRequested && consoleFormatRequested && consoleFamilyRequested &&
        consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PluginContract;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleStream && consoleUncaughtRequested &&
        schedulingOrderRequested && schedulingRequested && consoleRejectionRequested &&
        consoleFormatRequested && consoleFamilyRequested && consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::CapabilityDescriptor;
    }
    if (jsResult && stdoutWriteRequested && consoleDiagnostics && consoleUncaughtRequested &&
        schedulingOrderRequested && schedulingRequested && consoleRejectionRequested &&
        consoleFormatRequested && consoleFamilyRequested && !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptCancel;
    }
    if (jsResult && stdoutWriteRequested && !consoleDiagnostics && consoleUncaughtRequested &&
        schedulingOrderRequested && schedulingRequested && consoleRejectionRequested &&
        consoleFormatRequested && consoleFamilyRequested && !consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::OutputEnvelope;
    }
    if (jsResult && stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && consoleRejectionRequested && consoleFormatRequested &&
        consoleFamilyRequested && consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::OutputEvents;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && consoleRejectionRequested && consoleFormatRequested &&
        consoleFamilyRequested && consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::PostCompletionError;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && consoleRejectionRequested && consoleFormatRequested &&
        consoleFamilyRequested) {
        return EmbeddedLifecycleJsProbeKind::CompletionDrain;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && consoleRejectionRequested && consoleFormatRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptFailure;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && consoleRejectionRequested && !consoleFormatRequested) {
        return EmbeddedLifecycleJsProbeKind::ScriptCompletion;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        schedulingRequested && !consoleRejectionRequested) {
        return EmbeddedLifecycleJsProbeKind::BootstrapScript;
    }
    if (stdoutWriteRequested && consoleUncaughtRequested && schedulingOrderRequested &&
        !schedulingRequested) {
        return EmbeddedLifecycleJsProbeKind::Bootstrap;
    }
    if (stdoutWriteRequested && schedulingOrderRequested && !consoleUncaughtRequested) {
        return EmbeddedLifecycleJsProbeKind::AsyncConsole;
    }
    if (consoleUncaughtRequested && schedulingOrderRequested && !stdoutWriteRequested) {
        return EmbeddedLifecycleJsProbeKind::AsyncError;
    }
    if (schedulingOrderRequested) {
        return EmbeddedLifecycleJsProbeKind::SchedulingOrder;
    }
    if (schedulingRequested) {
        return EmbeddedLifecycleJsProbeKind::Scheduling;
    }
    if (consoleUncaughtRequested) {
        return EmbeddedLifecycleJsProbeKind::ConsoleUncaught;
    }
    if (consoleRejectionRequested) {
        return EmbeddedLifecycleJsProbeKind::ConsoleRejection;
    }
    if (consoleFormatRequested) {
        return EmbeddedLifecycleJsProbeKind::ConsoleFormat;
    }
    if (consoleFamilyRequested) {
        return EmbeddedLifecycleJsProbeKind::ConsoleFamily;
    }
    if (consoleReplaceRequested) {
        return EmbeddedLifecycleJsProbeKind::ConsoleReplace;
    }
    if (consoleShape) {
        return EmbeddedLifecycleJsProbeKind::ConsoleShape;
    }
    if (consoleStream) {
        return EmbeddedLifecycleJsProbeKind::ConsoleStream;
    }
    if (consoleDiagnostics) {
        return EmbeddedLifecycleJsProbeKind::ConsoleDiagnostics;
    }
    if (stdoutWriteRequested) {
        return EmbeddedLifecycleJsProbeKind::StdoutWrite;
    }
    if (jsResult) {
        return EmbeddedLifecycleJsProbeKind::JsResult;
    }
    return EmbeddedLifecycleJsProbeKind::None;
}

const char* embeddedLifecycleJsProbePayloadPrefix(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlinePrefix = embeddedInlineProbePayloadPrefix(kind)) {
        return inlinePrefix;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "plugin_shutdown";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "plugin_heartbeat";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "plugin_health";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "plugin_telemetry";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "plugin_diagnostics";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "plugin_recovery";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "plugin_restart";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "plugin_restart_budget";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "plugin_quarantine";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "script_descriptor";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "script_normalization";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "script_context";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "script_result_v2";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "script_cancellation_token";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "script_timeout_policy";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "script_memory_policy";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "script_preflight_summary";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "plugin_race";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "plugin_concurrency";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "plugin_queue";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "plugin_session";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "plugin_instance";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "plugin_library";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "plugin_abi";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "plugin_manifest";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "plugin_permission";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "plugin_negotiation";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "plugin_backpressure";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "plugin_crash";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "plugin_timeout";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "plugin_state_machine";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "plugin_invalid_request";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "plugin_lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "plugin_dispose";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "plugin_cancel";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "plugin_failure";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "plugin_events";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "plugin_request";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "plugin_contract";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "capability_descriptor";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "script_cancel";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "output_envelope";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "output_events";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "post_completion_error";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "completion_drain";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "script_failure";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "script_completion";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "bootstrap_script";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "bootstrap";
        case EmbeddedLifecycleJsProbeKind::AsyncError:
            return "async_error";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "async_console";
        case EmbeddedLifecycleJsProbeKind::SchedulingOrder:
            return "scheduling_order";
        case EmbeddedLifecycleJsProbeKind::Scheduling:
            return "scheduling";
        case EmbeddedLifecycleJsProbeKind::ConsoleUncaught:
            return "console_uncaught";
        case EmbeddedLifecycleJsProbeKind::ConsoleRejection:
            return "console_rejection";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "console_format";
        case EmbeddedLifecycleJsProbeKind::ConsoleFamily:
            return "console_family";
        case EmbeddedLifecycleJsProbeKind::ConsoleReplace:
            return "console_replace";
        case EmbeddedLifecycleJsProbeKind::ConsoleShape:
            return "console_shape";
        case EmbeddedLifecycleJsProbeKind::ConsoleStream:
            return "console_stream";
        case EmbeddedLifecycleJsProbeKind::ConsoleDiagnostics:
            return "console_diagnostics";
        case EmbeddedLifecycleJsProbeKind::StdoutWrite:
            return "stdout_write";
        case EmbeddedLifecycleJsProbeKind::JsResult:
            return "js_result";
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return nullptr;
    }
}

const char* embeddedLifecycleJsProbeLogName(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlineLogName = embeddedInlineProbeLogName(kind)) {
        return inlineLogName;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "v8.uv.plugin_shutdown.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "v8.uv.plugin_heartbeat.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "v8.uv.plugin_health.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "v8.uv.plugin_telemetry.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "v8.uv.plugin_diagnostics.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "v8.uv.plugin_recovery.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "v8.uv.plugin_restart.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "v8.uv.plugin_restart_budget.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "v8.uv.plugin_quarantine.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "v8.uv.script_descriptor.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "v8.uv.script_normalization.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "v8.uv.script_context.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "v8.uv.script_result_v2.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "v8.uv.script_cancellation_token.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "v8.uv.script_timeout_policy.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "v8.uv.script_memory_policy.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "v8.uv.script_preflight_summary.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "v8.uv.plugin_race.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "v8.uv.plugin_concurrency.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "v8.uv.plugin_queue.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "v8.uv.plugin_session.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "v8.uv.plugin_instance.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "v8.uv.plugin_library.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "v8.uv.plugin_abi.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "v8.uv.plugin_manifest.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "v8.uv.plugin_permission.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "v8.uv.plugin_negotiation.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "v8.uv.plugin_backpressure.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "v8.uv.plugin_crash.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "v8.uv.plugin_timeout.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "v8.uv.plugin_state_machine.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "v8.uv.plugin_invalid_request.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "v8.uv.plugin_lifecycle.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "v8.uv.plugin_dispose.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "v8.uv.plugin_cancel.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "v8.uv.plugin_failure.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "v8.uv.plugin_events.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "v8.uv.plugin_request.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "v8.uv.plugin_contract.lifecycle";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "v8.uv.capability_descriptor.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "v8.uv.script_cancel.lifecycle";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "v8.uv.output_envelope.lifecycle";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "v8.uv.output_events.lifecycle";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "v8.uv.post_completion_error.lifecycle";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "v8.uv.completion_drain.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "v8.uv.script_failure.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "v8.uv.script_completion.lifecycle";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "v8.uv.bootstrap_script.lifecycle";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "v8.uv.bootstrap.lifecycle";
        case EmbeddedLifecycleJsProbeKind::AsyncError:
            return "v8.uv.async_error.lifecycle";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "v8.uv.async_console.lifecycle";
        case EmbeddedLifecycleJsProbeKind::SchedulingOrder:
            return "v8.uv.scheduling_order.lifecycle";
        case EmbeddedLifecycleJsProbeKind::Scheduling:
            return "v8.uv.scheduling.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleUncaught:
            return "v8.uv.console_uncaught.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleRejection:
            return "v8.uv.console_rejection.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "v8.uv.console_format.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleFamily:
            return "v8.uv.console_family.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleReplace:
            return "v8.uv.console_replace.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleShape:
            return "v8.uv.console_shape.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleStream:
            return "v8.uv.console_stream.lifecycle";
        case EmbeddedLifecycleJsProbeKind::ConsoleDiagnostics:
            return "v8.uv.console_diagnostics.lifecycle";
        case EmbeddedLifecycleJsProbeKind::StdoutWrite:
            return "v8.uv.stdout_write.lifecycle";
        case EmbeddedLifecycleJsProbeKind::JsResult:
            return "v8.uv.js_result.lifecycle";
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return nullptr;
    }
}

const char* embeddedLifecycleJsProbeNotStartedDetail(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlineDetail = embeddedInlineProbeNotStartedDetail(kind)) {
        return inlineDetail;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "v8 uv plugin shutdown lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "v8 uv plugin heartbeat lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "v8 uv plugin health lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "v8 uv plugin telemetry lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "v8 uv plugin diagnostics lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "v8 uv plugin recovery lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "v8 uv plugin restart lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "v8 uv plugin restart budget lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "v8 uv plugin quarantine lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "v8 uv script descriptor lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "v8 uv script normalization lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "v8 uv script context lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "v8 uv script result v2 lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "v8 uv script cancellation token lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "v8 uv script timeout policy lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "v8 uv script memory policy lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "v8 uv script preflight summary lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "v8 uv plugin race lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "v8 uv plugin concurrency lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "v8 uv plugin queue lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "v8 uv plugin session lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "v8 uv plugin instance lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "v8 uv plugin library lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "v8 uv plugin ABI lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "v8 uv plugin manifest lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "v8 uv plugin permission lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "v8 uv plugin negotiation lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "v8 uv plugin backpressure lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "v8 uv plugin crash lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "v8 uv plugin timeout lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "v8 uv plugin state machine lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "v8 uv plugin invalid request lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "v8 uv plugin lifecycle sequence lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "v8 uv plugin dispose lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "v8 uv plugin cancel lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "v8 uv plugin failure lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "v8 uv plugin events lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "v8 uv plugin request lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "v8 uv plugin contract lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "v8 uv capability descriptor lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "v8 uv script cancellation lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "v8 uv output envelope lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "v8 uv output events lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "v8 uv post-completion error lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "v8 uv completion drain lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "v8 uv script failure lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "v8 uv script completion lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "v8 uv bootstrap script lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "v8 uv bootstrap lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::AsyncError:
            return "v8 uv async error lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "v8 uv async console lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::SchedulingOrder:
            return "v8 uv scheduling order lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::Scheduling:
            return "v8 uv scheduling globals lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleUncaught:
            return "v8 uv console uncaught lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleRejection:
            return "v8 uv console rejection lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "v8 uv console format lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleFamily:
            return "v8 uv console family lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleReplace:
            return "v8 uv console replacement lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleShape:
            return "v8 uv console shape lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleStream:
            return "v8 uv console stream lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::ConsoleDiagnostics:
            return "v8 uv console diagnostics lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::StdoutWrite:
            return "v8 uv stdout write lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::JsResult:
            return "v8 uv JS result lifecycle did not start";
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return nullptr;
    }
}

const char* embeddedLifecycleJsProbeTimingKey(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlineTimingKey = embeddedInlineProbeTimingKey(kind)) {
        return inlineTimingKey;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "timing.plugin_shutdown.ms";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "timing.plugin_heartbeat.ms";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "timing.plugin_health.ms";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "timing.plugin_telemetry.ms";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "timing.plugin_diagnostics.ms";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "timing.plugin_recovery.ms";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "timing.plugin_restart.ms";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "timing.plugin_restart_budget.ms";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "timing.plugin_quarantine.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "timing.script_descriptor.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "timing.script_normalization.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "timing.script_context.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "timing.script_result_v2.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "timing.script_cancellation_token.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "timing.script_timeout_policy.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "timing.script_memory_policy.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "timing.script_preflight_summary.ms";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "timing.plugin_race.ms";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "timing.plugin_concurrency.ms";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "timing.plugin_queue.ms";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "timing.plugin_session.ms";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "timing.plugin_instance.ms";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "timing.plugin_library.ms";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "timing.plugin_abi.ms";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "timing.plugin_manifest.ms";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "timing.plugin_permission.ms";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "timing.plugin_negotiation.ms";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "timing.plugin_backpressure.ms";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "timing.plugin_crash.ms";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "timing.plugin_timeout.ms";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "timing.plugin_state_machine.ms";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "timing.plugin_invalid_request.ms";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "timing.plugin_lifecycle.ms";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "timing.plugin_dispose.ms";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "timing.plugin_cancel.ms";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "timing.plugin_failure.ms";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "timing.plugin_events.ms";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "timing.plugin_request.ms";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "timing.plugin_contract.ms";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "timing.capability_descriptor.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "timing.script_cancel.ms";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "timing.output_envelope.ms";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "timing.output_events.ms";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "timing.post_completion_error.ms";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "timing.completion_drain.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "timing.script_failure.ms";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "timing.script_completion.ms";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "timing.bootstrap_script.ms";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "timing.bootstrap.ms";
        case EmbeddedLifecycleJsProbeKind::AsyncError:
            return "timing.async_error.ms";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "timing.async_console.ms";
        case EmbeddedLifecycleJsProbeKind::SchedulingOrder:
            return "timing.scheduling_order.ms";
        case EmbeddedLifecycleJsProbeKind::Scheduling:
            return "timing.scheduling.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleUncaught:
            return "timing.console_uncaught.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleRejection:
            return "timing.console_rejection.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "timing.console_format.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleFamily:
            return "timing.console_family.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleReplace:
            return "timing.console_replace.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleShape:
            return "timing.console_shape.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleStream:
            return "timing.console_stream.ms";
        case EmbeddedLifecycleJsProbeKind::ConsoleDiagnostics:
            return "timing.console_diagnostics.ms";
        case EmbeddedLifecycleJsProbeKind::StdoutWrite:
            return "timing.stdout_write.ms";
        case EmbeddedLifecycleJsProbeKind::JsResult:
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return "timing.js_result.ms";
    }
}

const char* embeddedLifecycleJsProbeExpectedStdout(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlineExpectedStdout = embeddedInlineProbeExpectedStdout(kind)) {
        return inlineExpectedStdout;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "script cancel sync stdout";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "output envelope late stdout";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "output events late stdout";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "post completion sync stdout";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "completion drain late stdout";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "script failure sync stdout";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "script completion async";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "script async stdout";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "bootstrap async stdout";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "async stdout";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "primitive 1 true null undefined";
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return kStdoutCaptureExpectedText;
    }
}

const char* embeddedLifecycleJsProbeExpectedStderr(EmbeddedLifecycleJsProbeKind kind) {
    if (const char* inlineExpectedStderr = embeddedInlineProbeExpectedStderr(kind)) {
        return inlineExpectedStderr;
    }
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::PluginShutdown:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginHeartbeat:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginHealth:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginTelemetry:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginDiagnostics:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRecovery:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRestart:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRestartBudget:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginQuarantine:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptDescriptor:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptNormalization:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptContext:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptResultV2:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptCancellationToken:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptTimeoutPolicy:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptMemoryPolicy:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptPreflightSummary:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRace:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginConcurrency:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginQueue:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginSession:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginInstance:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginLibrary:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginAbi:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginManifest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginPermission:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginNegotiation:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginBackpressure:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginCrash:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginTimeout:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginStateMachine:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginInvalidRequest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginLifecycle:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginDispose:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginCancel:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginFailure:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginEvents:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginRequest:
            return "";
        case EmbeddedLifecycleJsProbeKind::PluginContract:
            return "";
        case EmbeddedLifecycleJsProbeKind::CapabilityDescriptor:
            return "";
        case EmbeddedLifecycleJsProbeKind::ScriptCancel:
            return "script cancel observed";
        case EmbeddedLifecycleJsProbeKind::OutputEnvelope:
            return "output envelope post completion boom";
        case EmbeddedLifecycleJsProbeKind::OutputEvents:
            return "output events post completion boom";
        case EmbeddedLifecycleJsProbeKind::PostCompletionError:
            return "post completion boom";
        case EmbeddedLifecycleJsProbeKind::CompletionDrain:
            return "completion drain late stderr";
        case EmbeddedLifecycleJsProbeKind::ScriptFailure:
            return "script failure boom";
        case EmbeddedLifecycleJsProbeKind::ScriptCompletion:
            return "";
        case EmbeddedLifecycleJsProbeKind::BootstrapScript:
            return "script uncaught boom";
        case EmbeddedLifecycleJsProbeKind::Bootstrap:
            return "bootstrap uncaught boom";
        case EmbeddedLifecycleJsProbeKind::AsyncError:
            return "async rejection boom";
        case EmbeddedLifecycleJsProbeKind::AsyncConsole:
            return "async stderr";
        case EmbeddedLifecycleJsProbeKind::ConsoleUncaught:
            return "uncaught boom";
        case EmbeddedLifecycleJsProbeKind::ConsoleRejection:
            return "rejection boom";
        case EmbeddedLifecycleJsProbeKind::ConsoleFormat:
            return "Error: boom";
        case EmbeddedLifecycleJsProbeKind::None:
        default:
            return kStdoutCaptureExpectedText;
    }
}

void collectUvHandleDiagnostic(uv_handle_t* handle, void* arg) {
    auto* context = static_cast<UvDiagnosticsContext*>(arg);
    if (context == nullptr || handle == nullptr) {
        return;
    }
    const uv_handle_type type = context->handleGetType(handle);
    const int active = context->isActive(handle);
    const int closing = context->isClosing(handle);
    context->count += 1;
    if (active != 0) {
        context->active += 1;
    }
    if (closing != 0) {
        context->closing += 1;
    }
    const int typeIndex = static_cast<int>(type);
    if (typeIndex >= 0 && typeIndex < static_cast<int>(context->typeCounts.size())) {
        context->typeCounts[static_cast<size_t>(typeIndex)] += 1;
    } else {
        context->typeCounts[static_cast<size_t>(UV_UNKNOWN_HANDLE)] += 1;
    }
    if (context->samples.size() < 16) {
        context->samples.push_back(
                {
                        pointerToHex(handle),
                        uvHandleTypeNameForPayload(type),
                        active != 0,
                        closing != 0,
                }
        );
    }
}

void collectUvCloseTarget(uv_handle_t* handle, void* arg) {
    auto* context = static_cast<UvCloseContext*>(arg);
    if (context == nullptr || handle == nullptr) {
        return;
    }
    const uv_handle_type type = context->handleGetType(handle);
    const int active = context->isActive(handle);
    const int closing = context->isClosing(handle);
    context->seen += 1;
    if (closing != 0) {
        context->skipped += 1;
        return;
    }
    context->targets.push_back(
            {
                    handle,
                    pointerToHex(handle),
                    uvHandleTypeNameForPayload(type),
                    active != 0,
                    false,
            }
    );
}

void noopUvCloseCallback(uv_handle_t* /* handle */) {}

UvDiagnosticsSummary appendUvDiagnosticsPayload(
        std::vector<std::string>& payload,
        const std::string& phase,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
) {
    const std::string prefix = "uv.diagnostics." + phase;
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.diagnostics.start phase=%s", phase.c_str());
    const auto startedAt = Clock::now();
    UvDiagnosticsSummary summary;
    if (eventLoop == nullptr) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_diagnostics_" + phase + ".ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed reason=missing_loop", phase.c_str());
        return summary;
    }

    try {
        UvDiagnosticsContext context(uvHandleGetType, uvIsActive, uvIsClosing);
        uvWalk(eventLoop, collectUvHandleDiagnostic, &context);
        summary.valid = true;
        summary.count = context.count;
        summary.active = context.active;
        summary.closing = context.closing;
        putPayload(payload, prefix + ".status", "done");
        putPayload(payload, prefix + ".detail", "uv_walk returned");
        putPayload(payload, prefix + ".count", static_cast<long long>(context.count));
        putPayload(payload, prefix + ".active", static_cast<long long>(context.active));
        putPayload(payload, prefix + ".closing", static_cast<long long>(context.closing));
        for (size_t i = 0; i < context.typeCounts.size(); ++i) {
            const int count = context.typeCounts[i];
            if (count <= 0) {
                continue;
            }
            const std::string key = prefix + ".type." +
                    uvHandleTypeNameForPayload(static_cast<uv_handle_type>(i));
            putPayload(payload, key, static_cast<long long>(count));
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.diagnostics.%s.%s=%d",
                    phase.c_str(),
                    key.substr(prefix.size() + 1).c_str(),
                    count
            );
        }
        putPayload(payload, prefix + ".handle.sample.count", static_cast<long long>(context.samples.size()));
        for (size_t i = 0; i < context.samples.size(); ++i) {
            const UvHandleDiagnosticSample& sample = context.samples[i];
            const std::string handlePrefix = prefix + ".handle." + std::to_string(i);
            putPayload(payload, handlePrefix + ".address", sample.address);
            putPayload(payload, handlePrefix + ".type", sample.type);
            putPayload(payload, handlePrefix + ".active", sample.active);
            putPayload(payload, handlePrefix + ".closing", sample.closing);
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.diagnostics.handle.%zu phase=%s type=%s active=%s closing=%s address=%s",
                    i,
                    phase.c_str(),
                    sample.type.c_str(),
                    sample.active ? "true" : "false",
                    sample.closing ? "true" : "false",
                    sample.address.c_str()
            );
        }
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.diagnostics.%s.count=%d active=%d closing=%d",
                phase.c_str(),
                context.count,
                context.active,
                context.closing
        );
    } catch (const std::exception& e) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed error=%s", phase.c_str(), e.what());
    } catch (...) {
        putPayload(payload, prefix + ".status", "failed");
        putPayload(payload, prefix + ".detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.diagnostics.%s.failed error=unknown", phase.c_str());
    }
    putPayload(payload, "timing.uv_diagnostics_" + phase + ".ms", elapsedMs(startedAt));
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.diagnostics.done phase=%s elapsed=%lldms", phase.c_str(), elapsedMs(startedAt));
    return summary;
}

void appendUvClosePayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvWalk uvWalk,
        UvClose uvClose,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing
) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.close.start");
    const auto startedAt = Clock::now();
    if (eventLoop == nullptr) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_close.ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed reason=missing_loop");
        return;
    }

    try {
        UvCloseContext context(uvHandleGetType, uvIsActive, uvIsClosing);
        uvWalk(eventLoop, collectUvCloseTarget, &context);
        int succeeded = 0;
        int failed = 0;
        for (size_t i = 0; i < context.targets.size(); ++i) {
            const UvCloseTarget& target = context.targets[i];
            const std::string handlePrefix = "uv.close.handle." + std::to_string(i);
            putPayload(payload, handlePrefix + ".address", target.address);
            putPayload(payload, handlePrefix + ".type", target.type);
            putPayload(payload, handlePrefix + ".active.before", target.activeBefore);
            putPayload(payload, handlePrefix + ".closing.before", target.closingBefore);
            uvClose(target.handle, noopUvCloseCallback);
            const bool closingAfter = uvIsClosing(target.handle) != 0;
            putPayload(payload, handlePrefix + ".closing.after", closingAfter);
            putPayload(payload, handlePrefix + ".status", closingAfter ? "closing" : "not_closing");
            if (closingAfter) {
                succeeded += 1;
            } else {
                failed += 1;
            }
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.close.handle.%zu type=%s active_before=%s closing_after=%s address=%s",
                    i,
                    target.type.c_str(),
                    target.activeBefore ? "true" : "false",
                    closingAfter ? "true" : "false",
                    target.address.c_str()
            );
        }
        putPayload(payload, "uv.close.status", failed == 0 ? "done" : "failed");
        putPayload(payload, "uv.close.detail", failed == 0 ? "uv_close returned for collected handles" : "uv_close did not mark every collected handle as closing");
        putPayload(payload, "uv.close.seen", static_cast<long long>(context.seen));
        putPayload(payload, "uv.close.attempted", static_cast<long long>(context.targets.size()));
        putPayload(payload, "uv.close.succeeded", static_cast<long long>(succeeded));
        putPayload(payload, "uv.close.skipped", static_cast<long long>(context.skipped));
        putPayload(payload, "uv.close.failed", static_cast<long long>(failed));
        __android_log_print(
                failed == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                kLogTag,
                "uv.close.done seen=%d attempted=%zu succeeded=%d skipped=%d failed=%d elapsed=%lldms",
                context.seen,
                context.targets.size(),
                succeeded,
                context.skipped,
                failed,
                elapsedMs(startedAt)
        );
    } catch (const std::exception& e) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed error=%s", e.what());
    } catch (...) {
        putPayload(payload, "uv.close.status", "failed");
        putPayload(payload, "uv.close.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.close.failed error=unknown");
    }
    putPayload(payload, "timing.uv_close.ms", elapsedMs(startedAt));
}

bool appendUvRunCleanupPayload(
        std::vector<std::string>& payload,
        uv_loop_t* eventLoop,
        UvRun uvRun,
        UvWalk uvWalk,
        UvHandleGetType uvHandleGetType,
        UvIsActive uvIsActive,
        UvIsClosing uvIsClosing,
        bool collectDiagnostics
) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.run.start mode=UV_RUN_NOWAIT max_iterations=%d", kUvRunCleanupMaxIterations);
    const auto startedAt = Clock::now();
    putPayload(payload, "uv.run.mode", "UV_RUN_NOWAIT");
    putPayload(payload, "uv.run.diagnostics", collectDiagnostics ? "full" : "lightweight");
    putPayload(payload, "uv.run.max_iterations", static_cast<long long>(kUvRunCleanupMaxIterations));
    putPayload(payload, "uv.run.completed_iterations", static_cast<long long>(0));
    if (eventLoop == nullptr) {
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", "uv_loop_t was unavailable");
        putPayload(payload, "timing.uv_run.ms", elapsedMs(startedAt));
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed reason=missing_loop");
        return false;
    }

    bool failed = false;
    bool handleCountZero = false;
    int completedIterations = 0;
    try {
        for (int iteration = 0; iteration < kUvRunCleanupMaxIterations; ++iteration) {
            const auto iterationStartedAt = Clock::now();
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "uv.run.iteration.%d.start mode=UV_RUN_NOWAIT", iteration);
            const int result = uvRun(eventLoop, UV_RUN_NOWAIT);
            completedIterations = iteration + 1;
            const std::string iterationPrefix = "uv.run.iteration." + std::to_string(iteration);
            putPayload(payload, iterationPrefix + ".rc", static_cast<long long>(result));
            putPayload(payload, "timing.uv_run_iteration_" + std::to_string(iteration) + ".ms", elapsedMs(iterationStartedAt));
            int handleCount = -1;
            int activeCount = -1;
            int closingCount = -1;
            if (collectDiagnostics) {
                const UvDiagnosticsSummary summary = appendUvDiagnosticsPayload(
                        payload,
                        "after_run_" + std::to_string(iteration),
                        eventLoop,
                        uvWalk,
                        uvHandleGetType,
                        uvIsActive,
                        uvIsClosing
                );
                if (summary.valid) {
                    handleCount = summary.count;
                    activeCount = summary.active;
                    closingCount = summary.closing;
                    putPayload(payload, iterationPrefix + ".handle.count", static_cast<long long>(summary.count));
                    putPayload(payload, iterationPrefix + ".handle.active", static_cast<long long>(summary.active));
                    putPayload(payload, iterationPrefix + ".handle.closing", static_cast<long long>(summary.closing));
                    handleCountZero = summary.count == 0;
                }
            } else {
                handleCountZero = result == 0;
            }
            putPayload(payload, "uv.run.completed_iterations", static_cast<long long>(completedIterations));
            __android_log_print(
                    ANDROID_LOG_INFO,
                    kLogTag,
                    "uv.run.iteration.%d.done rc=%d handle_count=%d active=%d closing=%d elapsed=%lldms",
                    iteration,
                    result,
                    handleCount,
                    activeCount,
                    closingCount,
                    elapsedMs(iterationStartedAt)
            );
            if (handleCountZero) {
                break;
            }
        }
    } catch (const std::exception& e) {
        failed = true;
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", e.what());
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed error=%s", e.what());
    } catch (...) {
        failed = true;
        putPayload(payload, "uv.run.status", "failed");
        putPayload(payload, "uv.run.detail", "unknown native exception");
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "uv.run.failed error=unknown");
    }

    if (!failed) {
        putPayload(payload, "uv.run.status", "done");
        putPayload(
                payload,
                "uv.run.detail",
                handleCountZero
                        ? "bounded UV_RUN_NOWAIT cleanup released all handles"
                        : "bounded UV_RUN_NOWAIT cleanup completed with handles still present"
        );
        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "uv.run.done completed_iterations=%d handle_count_zero=%s elapsed=%lldms",
                completedIterations,
                handleCountZero ? "true" : "false",
                elapsedMs(startedAt)
        );
    }
    putPayload(payload, "timing.uv_run.ms", elapsedMs(startedAt));
    return !failed;
}

int v8BuildConfiguration() {
    int buildConfiguration = 0;
    if (v8::internal::PointerCompressionIsEnabled()) {
        buildConfiguration |= 1 << 0;
    }
    if (v8::internal::SmiValuesAre31Bits()) {
        buildConfiguration |= 1 << 1;
    }
    if (v8::internal::SandboxIsEnabled()) {
        buildConfiguration |= 1 << 2;
    }
#ifdef V8_TARGET_OS_ANDROID
    buildConfiguration |= 1 << 3;
#endif
#ifdef V8_ENABLE_CHECKS
    buildConfiguration |= 1 << 4;
#endif
    return buildConfiguration;
}

std::string uvLoopResultDetail(const char* operation, int result) {
    std::ostringstream stream;
    stream << operation << " returned " << result;
    if (result == UV_EBUSY) {
        stream << " (UV_EBUSY: uv_loop_t still has active handles or requests; handles were not force-closed)";
    }
    return stream.str();
}

}  // namespace autojs6::node_bridge::internal
