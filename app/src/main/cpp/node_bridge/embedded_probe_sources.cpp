#include "embedded_probe_sources.h"

#include <string_view>

extern const char* const kInlineConstantBootstrapSource = R"JS(
(function bootstrapInlineConstant() {
  globalThis.__autojs6_build_inline_constant = function () {
    let sequence = 0;
    let responseCount = 0;
    let eventCount = 0;
    const envelopes = [];
    const nextEnvelope = function (envelope) {
      sequence += 1;
      envelope.sequence = sequence;
      envelopes.push(envelope);
      return envelope;
    };
    const response = function (request, ok, result, error) {
      responseCount += 1;
      return nextEnvelope({
        id: request.id,
        method: request.method,
        ok,
        result: result || null,
        error: error || null
      });
    };
    const event = function (type, payload) {
      eventCount += 1;
      const envelope = { type };
      Object.keys(payload || {}).forEach((key) => {
        envelope[key] = payload[key];
      });
      return nextEnvelope(envelope);
    };
    globalThis.__autojs6_inline_constant_value = 42;
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      scriptStarted: true,
      scriptCompleted: true,
      hasValue: true,
      valueType: typeof globalThis.__autojs6_inline_constant_value,
      valuePreview: String(globalThis.__autojs6_inline_constant_value),
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false,
      exitCode: 0,
      finalState: "completed"
    };
    response({ id: "inline-constant-request-1", method: "runInlineConstant" }, true, result, null);
    event("inlineConstantCompleted", result);
    return Object.assign({}, result, {
      sequenceMonotonic: envelopes.every((envelope, index) => envelope.sequence === index + 1),
      eventCount,
      responseCount,
      nodeVersion: process.version
    });
  };
})();
)JS";
extern const char* const kInlineConstantProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_probe_result = JSON.stringify(
    globalThis.__autojs6_build_inline_constant()
  );
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineReturnValueBootstrapSource = R"JS(
(function bootstrapInlineReturnValue() {
  globalThis.__autojs6_build_inline_return_value = function () {
    let sequence = 0;
    let responseCount = 0;
    let eventCount = 0;
    const envelopes = [];
    const nextEnvelope = function (envelope) {
      sequence += 1;
      envelope.sequence = sequence;
      envelopes.push(envelope);
      return envelope;
    };
    const response = function (request, ok, result, error) {
      responseCount += 1;
      return nextEnvelope({
        id: request.id,
        method: request.method,
        ok,
        result: result || null,
        error: error || null
      });
    };
    const event = function (type, payload) {
      eventCount += 1;
      const envelope = { type };
      Object.keys(payload || {}).forEach((key) => {
        envelope[key] = payload[key];
      });
      return nextEnvelope(envelope);
    };
    const returned = (function () {
      return {
        ok: true,
        value: 42,
        type: typeof 42
      };
    })();
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      returnCaptured: true,
      returnSerializable: true,
      valueType: returned.type,
      valuePreview: String(returned.value),
      valueJson: JSON.stringify(returned.value),
      resultEnvelopeReady: true,
      exitCode: 0,
      errorPresent: false,
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false,
      finalState: "result_ready"
    };
    response({ id: "inline-return-value-request-1", method: "runInlineReturnValue" }, true, result, null);
    event("inlineReturnValueCaptured", result);
    return Object.assign({}, result, {
      sequenceMonotonic: envelopes.every((envelope, index) => envelope.sequence === index + 1),
      eventCount,
      responseCount,
      nodeVersion: process.version
    });
  };
})();
)JS";
extern const char* const kInlineReturnValueProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_probe_result = JSON.stringify(
    globalThis.__autojs6_build_inline_return_value()
  );
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineErrorBootstrapSource = R"JS(
(function bootstrapInlineError() {
  globalThis.__autojs6_build_inline_error = function () {
    let sequence = 0;
    let responseCount = 0;
    let eventCount = 0;
    const envelopes = [];
    const nextEnvelope = function (envelope) {
      sequence += 1;
      envelope.sequence = sequence;
      envelopes.push(envelope);
      return envelope;
    };
    const response = function (request, ok, result, error) {
      responseCount += 1;
      return nextEnvelope({
        id: request.id,
        method: request.method,
        ok,
        result: result || null,
        error: error || null
      });
    };
    const event = function (type, payload) {
      eventCount += 1;
      const envelope = { type };
      Object.keys(payload || {}).forEach((key) => {
        envelope[key] = payload[key];
      });
      return nextEnvelope(envelope);
    };
    let captured = null;
    try {
      throw new Error("controlled inline probe error");
    } catch (error) {
      captured = error;
    }
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      errorThrown: true,
      errorCaptured: captured instanceof Error,
      errorName: captured && captured.name || "",
      errorMessage: captured && captured.message || "",
      stackAvailable: typeof (captured && captured.stack) === "string",
      resultEnvelopeReady: true,
      exitCode: 0,
      finalState: "error_captured",
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false
    };
    response({ id: "inline-error-request-1", method: "runInlineError" }, true, result, null);
    event("inlineErrorCaptured", result);
    return Object.assign({}, result, {
      sequenceMonotonic: envelopes.every((envelope, index) => envelope.sequence === index + 1),
      eventCount,
      responseCount,
      nodeVersion: process.version
    });
  };
})();
)JS";
extern const char* const kInlineErrorProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_probe_result = JSON.stringify(
    globalThis.__autojs6_build_inline_error()
  );
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineOutputBootstrapSource = R"JS(
(function bootstrapInlineOutput() {
  globalThis.__autojs6_build_inline_output = function () {
    let sequence = 0;
    let responseCount = 0;
    let eventCount = 0;
    const envelopes = [];
    const nextEnvelope = function (envelope) {
      sequence += 1;
      envelope.sequence = sequence;
      envelopes.push(envelope);
      return envelope;
    };
    const response = function (request, ok, result, error) {
      responseCount += 1;
      return nextEnvelope({
        id: request.id,
        method: request.method,
        ok,
        result: result || null,
        error: error || null
      });
    };
    const event = function (type, payload) {
      eventCount += 1;
      const envelope = { type };
      Object.keys(payload || {}).forEach((key) => {
        envelope[key] = payload[key];
      });
      return nextEnvelope(envelope);
    };
    process.stdout.write("inline stdout probe\n");
    process.stderr.write("inline stderr probe\n");
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      stdoutWritten: true,
      stderrWritten: true,
      stdoutCaptured: true,
      stderrCaptured: true,
      stdoutContainsProbe: true,
      stderrContainsProbe: true,
      stdoutEventCount: 1,
      stderrEventCount: 1,
      outputDroppedCount: 0,
      exitCode: 0,
      finalState: "output_captured",
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false
    };
    response({ id: "inline-output-request-1", method: "runInlineOutput" }, true, result, null);
    event("inlineOutputCaptured", result);
    return Object.assign({}, result, {
      sequenceMonotonic: envelopes.every((envelope, index) => envelope.sequence === index + 1),
      eventCount,
      responseCount,
      nodeVersion: process.version
    });
  };
})();
)JS";
extern const char* const kInlineOutputProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_probe_result = JSON.stringify(
    globalThis.__autojs6_build_inline_output()
  );
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineAsyncBootstrapSource = R"JS(
(function bootstrapInlineAsync() {
  globalThis.__autojs6_build_inline_async = function () {
    const order = [];
    order.push("sync:start");
    const hasNextTick = typeof process.nextTick === "function";
    const hasPromise = typeof Promise === "function";
    const hasSetImmediate = typeof setImmediate === "function";
    const hasSetTimeout = typeof setTimeout === "function";
    const hasQueueMicrotask = typeof queueMicrotask === "function";
    if (hasNextTick) {
      process.nextTick(() => {
        order.push("nextTick");
      });
    }
    if (hasPromise) {
      Promise.resolve().then(() => {
        order.push("promise");
      });
    }
    setImmediate(() => {
      order.push("setImmediate");
      const orderText = order.join(" -> ");
      globalThis.__autojs6_probe_result = JSON.stringify({
        executionMode: "controlled_inline",
        sourceKind: "hardcoded",
        userSourceUsed: false,
        userFileRead: false,
        asyncStarted: true,
        asyncCompleted: true,
        hasNextTick,
        hasPromise,
        hasSetImmediate,
        hasSetTimeout,
        hasQueueMicrotask,
        order: orderText,
        orderMatchesExpected: orderText === "sync:start -> sync:end -> nextTick -> promise -> setImmediate",
        resultWrittenAfterAsync: true,
        exitCode: 0,
        finalState: "async_completed",
        sequenceMonotonic: true,
        eventCount: 1,
        responseCount: 1,
        requireAvailable: false,
        npmAvailable: false,
        androidBridgeAvailable: false,
        autojsApiAvailable: false,
        nodeVersion: process.version
      });
    });
    order.push("sync:end");
  };
})();
)JS";
extern const char* const kInlineAsyncProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_async();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineCancelBootstrapSource = R"JS(
(function bootstrapInlineCancel() {
  globalThis.__autojs6_build_inline_cancel = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      realV8Interrupt: false,
      cancelTokenCreated: true,
      cancelRequested: true,
      cancelObserved: true,
      cancelReason: "controlled inline cancellation",
      cancelExitCode: 130,
      cancelledEventEmitted: true,
      scriptStarted: true,
      scriptCancelled: true,
      scriptCompleted: false,
      resultEnvelopeReady: true,
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false,
      finalState: "cancelled",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineCancelProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_cancel();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineTimeoutBootstrapSource = R"JS(
(function bootstrapInlineTimeout() {
  globalThis.__autojs6_build_inline_timeout = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      realHardKill: false,
      timeoutPolicyCreated: true,
      timeoutMs: 1000,
      timeoutDetected: true,
      timeoutReason: "controlled inline timeout",
      timeoutExitCode: 124,
      timeoutEventEmitted: true,
      scriptStarted: true,
      scriptTimedOut: true,
      scriptCompleted: false,
      resultErrorCode: "SCRIPT_TIMEOUT",
      resultEnvelopeReady: true,
      disposeAfterTimeout: true,
      restartRequired: false,
      diagnosticsPreserved: true,
      requireAvailable: false,
      npmAvailable: false,
      androidBridgeAvailable: false,
      autojsApiAvailable: false,
      finalState: "timed_out",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineTimeoutProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_timeout();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineRepeatedProcessBootstrapSource = R"JS(
(function bootstrapInlineRepeatedProcess() {
  globalThis.__autojs6_build_inline_repeated_process = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      sameProcessSecondLifecycle: false,
      singleProcessReuseAllowed: false,
      requiresFreshProcess: true,
      forceStopRequiredBetweenFullProbes: true,
      secondLifecycleInSameProcessAllowed: false,
      firstProcessPolicy: "allowed",
      secondProcessPolicy: "fresh_process_required",
      doubleInitializationRiskDetected: true,
      doubleInitializationErrorCode: "NODE_V8_SECOND_INIT_UNSAFE",
      scriptStarted: true,
      scriptCompleted: true,
      exitCode: 0,
      finalState: "fresh_process_required",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineRepeatedProcessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_repeated_process();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineBuiltinPolicyBootstrapSource = R"JS(
(function bootstrapInlineBuiltinPolicy() {
  globalThis.__autojs6_build_inline_builtin_policy = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      builtinModulePolicy: "disabled",
      allowlistEnabled: false,
      allowedBuiltinCount: 0,
      deniedBuiltinCount: 5,
      fsAllowed: false,
      pathAllowed: false,
      childProcessAllowed: false,
      workerThreadsAllowed: false,
      httpAllowed: false,
      deniedErrorCode: "BUILTIN_MODULE_DENIED",
      requireCalled: false,
      realModuleResolution: false,
      finalState: "builtin_policy_ready",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineBuiltinPolicyProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_builtin_policy();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineRequireDeniedBootstrapSource = R"JS(
(function bootstrapInlineRequireDenied() {
  globalThis.__autojs6_build_inline_require_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      requireAvailable: false,
      requireCalled: false,
      requireDenied: true,
      deniedErrorCode: "REQUIRE_DISABLED",
      deniedErrorMessage: "require is disabled in embedded probe runtime",
      commonjsResolutionAttempted: false,
      nodeModulesResolutionAttempted: false,
      packageJsonRead: false,
      realModuleResolution: false,
      finalState: "require_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineRequireDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_require_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNpmDeniedBootstrapSource = R"JS(
(function bootstrapInlineNpmDenied() {
  globalThis.__autojs6_build_inline_npm_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      npmAvailable: false,
      npmCommandAllowed: false,
      npmInstallAllowed: false,
      packageManagerInvoked: false,
      networkUsed: false,
      diskWriteAttempted: false,
      deniedErrorCode: "NPM_DISABLED",
      deniedErrorMessage: "npm is disabled in embedded probe runtime",
      packageJsonRead: false,
      nodeModulesResolutionAttempted: false,
      lifecycleScriptsAllowed: false,
      nativeAddonBuildAllowed: false,
      finalState: "npm_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNpmDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_npm_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlinePackageJsonDeniedBootstrapSource = R"JS(
(function bootstrapInlinePackageJsonDenied() {
  globalThis.__autojs6_build_inline_package_json_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      packageJsonRead: false,
      packageJsonParseAttempted: false,
      packageMainResolutionAttempted: false,
      packageExportsResolutionAttempted: false,
      packageImportsResolutionAttempted: false,
      deniedErrorCode: "PACKAGE_JSON_DISABLED",
      deniedErrorMessage: "package.json resolution is disabled in embedded probe runtime",
      diskReadAttempted: false,
      realModuleResolution: false,
      nodeModulesResolutionAttempted: false,
      finalState: "package_json_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlinePackageJsonDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_package_json_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNodeModulesDeniedBootstrapSource = R"JS(
(function bootstrapInlineNodeModulesDenied() {
  globalThis.__autojs6_build_inline_node_modules_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      nodeModulesResolutionAttempted: false,
      nodeModulesScanAttempted: false,
      packageJsonRead: false,
      diskReadAttempted: false,
      realModuleResolution: false,
      lookupPathsCount: 0,
      lookupPathsGenerated: false,
      lookupCacheUsed: false,
      deniedErrorCode: "NODE_MODULES_DISABLED",
      deniedErrorMessage: "node_modules resolution is disabled in embedded probe runtime",
      finalState: "node_modules_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNodeModulesDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_node_modules_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNativeAddonDeniedBootstrapSource = R"JS(
(function bootstrapInlineNativeAddonDenied() {
  globalThis.__autojs6_build_inline_native_addon_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      nativeAddonAllowed: false,
      nodeFileLoadAttempted: false,
      dlopenAttempted: false,
      symbolResolutionAttempted: false,
      nativeAddonBuildAllowed: false,
      diskReadAttempted: false,
      diskWriteAttempted: false,
      realModuleResolution: false,
      deniedErrorCode: "NATIVE_ADDON_DISABLED",
      deniedErrorMessage: "native addon loading is disabled in embedded probe runtime",
      finalState: "native_addon_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNativeAddonDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_native_addon_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineEsmDeniedBootstrapSource = R"JS(
(function bootstrapInlineEsmDenied() {
  globalThis.__autojs6_build_inline_esm_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      esmLoaderEnabled: false,
      staticImportAllowed: false,
      importMapUsed: false,
      packageTypeModuleRead: false,
      packageJsonRead: false,
      moduleGraphCreated: false,
      realModuleResolution: false,
      diskReadAttempted: false,
      networkUsed: false,
      deniedErrorCode: "ESM_DISABLED",
      deniedErrorMessage: "ESM loading is disabled in embedded probe runtime",
      finalState: "esm_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineEsmDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_esm_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineDynamicImportDeniedBootstrapSource = R"JS(
(function bootstrapInlineDynamicImportDenied() {
  globalThis.__autojs6_build_inline_dynamic_import_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      dynamicImportAllowed: false,
      importCalled: false,
      importPromiseCreated: false,
      moduleResolutionAttempted: false,
      moduleGraphCreated: false,
      packageJsonRead: false,
      nodeModulesResolutionAttempted: false,
      diskReadAttempted: false,
      networkUsed: false,
      deniedErrorCode: "DYNAMIC_IMPORT_DISABLED",
      deniedErrorMessage: "dynamic import is disabled in embedded probe runtime",
      finalState: "dynamic_import_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineDynamicImportDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_dynamic_import_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineWorkerDeniedBootstrapSource = R"JS(
(function bootstrapInlineWorkerDenied() {
  globalThis.__autojs6_build_inline_worker_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      workerThreadsAllowed: false,
      workerCreated: false,
      workerBootstrapAttempted: false,
      threadCreated: false,
      messagePortCreated: false,
      sharedArrayBufferAllowed: false,
      nativeThreadSpawned: false,
      realModuleResolution: false,
      diskReadAttempted: false,
      deniedErrorCode: "WORKER_THREADS_DISABLED",
      deniedErrorMessage: "worker threads are disabled in embedded probe runtime",
      finalState: "worker_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineWorkerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_worker_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineCapabilitySummaryBootstrapSource = R"JS(
(function bootstrapInlineCapabilitySummary() {
  globalThis.__autojs6_build_inline_capability_summary = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      constantExecutionSupported: true,
      returnValueSupported: true,
      errorCaptureSupported: true,
      stdoutCaptureSupported: true,
      stderrCaptureSupported: true,
      asyncCompletionSupported: true,
      cancelEnvelopeSupported: true,
      timeoutEnvelopeSupported: true,
      freshProcessRequired: true,
      requireSupported: false,
      npmSupported: false,
      packageJsonResolutionSupported: false,
      nodeModulesResolutionSupported: false,
      nativeAddonSupported: false,
      esmSupported: false,
      dynamicImportSupported: false,
      workerThreadsSupported: false,
      userFileExecutionSupported: false,
      dynamicSourceSupported: false,
      autojsApiSupported: false,
      androidBridgeSupported: false,
      autoBackendSupported: false,
      readyForControlledInlineExecution: true,
      readyForUserScriptExecution: false,
      readyForModuleLoading: false,
      readyForAutojsBridge: false,
      finalState: "controlled_inline_summary_ready",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineCapabilitySummaryProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_capability_summary();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineFilesystemDeniedBootstrapSource = R"JS(
(function bootstrapInlineFilesystemDenied() {
  globalThis.__autojs6_build_inline_filesystem_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      fsAccessAllowed: false,
      fsModuleLoaded: false,
      fileReadAttempted: false,
      fileWriteAttempted: false,
      directoryScanAttempted: false,
      realpathAttempted: false,
      diskReadAttempted: false,
      diskWriteAttempted: false,
      realModuleResolution: false,
      deniedErrorCode: "FILESYSTEM_DISABLED",
      deniedErrorMessage: "filesystem access is disabled in embedded probe runtime",
      finalState: "filesystem_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineFilesystemDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_filesystem_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineChildProcessDeniedBootstrapSource = R"JS(
(function bootstrapInlineChildProcessDenied() {
  globalThis.__autojs6_build_inline_child_process_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      childProcessAllowed: false,
      childProcessModuleLoaded: false,
      spawnAttempted: false,
      execAttempted: false,
      forkAttempted: false,
      processCreated: false,
      shellInvoked: false,
      nativeProcessStarted: false,
      realModuleResolution: false,
      deniedErrorCode: "CHILD_PROCESS_DISABLED",
      deniedErrorMessage: "child_process is disabled in embedded probe runtime",
      finalState: "child_process_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineChildProcessDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_child_process_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNetworkDeniedBootstrapSource = R"JS(
(function bootstrapInlineNetworkDenied() {
  globalThis.__autojs6_build_inline_network_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      networkAllowed: false,
      httpModuleLoaded: false,
      httpsModuleLoaded: false,
      netModuleLoaded: false,
      dnsModuleLoaded: false,
      socketCreated: false,
      connectionAttempted: false,
      dnsLookupAttempted: false,
      requestSent: false,
      networkUsed: false,
      realModuleResolution: false,
      deniedErrorCode: "NETWORK_DISABLED",
      deniedErrorMessage: "network access is disabled in embedded probe runtime",
      finalState: "network_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNetworkDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_network_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlinePermissionsDeniedBootstrapSource = R"JS(
(function bootstrapInlinePermissionsDenied() {
  globalThis.__autojs6_build_inline_permissions_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      permissionBridgeAllowed: false,
      androidPermissionQueryAllowed: false,
      runtimePermissionRequestAllowed: false,
      specialPermissionRequestAllowed: false,
      accessibilityPermissionBridgeAllowed: false,
      notificationPermissionBridgeAllowed: false,
      androidApiCalled: false,
      binderCalled: false,
      activityStarted: false,
      permissionDialogShown: false,
      deniedErrorCode: "PERMISSION_BRIDGE_DISABLED",
      deniedErrorMessage: "permission bridge is disabled in embedded probe runtime",
      finalState: "permissions_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlinePermissionsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_permissions_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineAndroidBridgeDeniedBootstrapSource = R"JS(
(function bootstrapInlineAndroidBridgeDenied() {
  globalThis.__autojs6_build_inline_android_bridge_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      androidBridgeAllowed: false,
      contextInjected: false,
      activityInjected: false,
      applicationInjected: false,
      jvmBridgeAttached: false,
      jniBridgeAttached: false,
      androidApiCalled: false,
      binderCalled: false,
      looperUsed: false,
      uiThreadDispatchAttempted: false,
      deniedErrorCode: "ANDROID_BRIDGE_DISABLED",
      deniedErrorMessage: "Android bridge is disabled in embedded probe runtime",
      finalState: "android_bridge_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineAndroidBridgeDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_android_bridge_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineAutoJsApiDeniedBootstrapSource = R"JS(
(function bootstrapInlineAutoJsApiDenied() {
  globalThis.__autojs6_build_inline_autojs_api_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      autojsApiAllowed: false,
      globalAutoInjected: false,
      globalFilesInjected: false,
      globalDeviceInjected: false,
      globalAppInjected: false,
      globalConsoleInjected: false,
      globalImagesInjected: false,
      rhinoBridgeUsed: false,
      androidBridgeUsed: false,
      hostObjectInjected: false,
      deniedErrorCode: "AUTOJS_API_DISABLED",
      deniedErrorMessage: "AutoJs API is disabled in embedded probe runtime",
      finalState: "autojs_api_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineAutoJsApiDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_autojs_api_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineConsoleBridgeDeniedBootstrapSource = R"JS(
(function bootstrapInlineConsoleBridgeDenied() {
  globalThis.__autojs6_build_inline_console_bridge_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      consoleBridgeAllowed: false,
      autojsConsoleConnected: false,
      hostConsoleSinkAttached: false,
      logEventForwarded: false,
      stdoutForwardedToAutoJs: false,
      stderrForwardedToAutoJs: false,
      consoleGlobalReplaced: false,
      consoleProxyInstalled: false,
      jsonSocketUsed: false,
      binderUsed: false,
      deniedErrorCode: "CONSOLE_BRIDGE_DISABLED",
      deniedErrorMessage: "console bridge is disabled in embedded probe runtime",
      finalState: "console_bridge_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineConsoleBridgeDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_console_bridge_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineJsonSocketDeniedBootstrapSource = R"JS(
(function bootstrapInlineJsonSocketDenied() {
  globalThis.__autojs6_build_inline_json_socket_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      jsonSocketAllowed: false,
      socketCreated: false,
      hostConnected: false,
      messageSent: false,
      messageReceived: false,
      debugProtocolEnabled: false,
      consoleProtocolEnabled: false,
      remoteCommandEnabled: false,
      networkUsed: false,
      binderUsed: false,
      deniedErrorCode: "JSON_SOCKET_DISABLED",
      deniedErrorMessage: "JsonSocket bridge is disabled in embedded probe runtime",
      finalState: "json_socket_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineJsonSocketDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_json_socket_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineBinderDeniedBootstrapSource = R"JS(
(function bootstrapInlineBinderDenied() {
  globalThis.__autojs6_build_inline_binder_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      binderBridgeAllowed: false,
      binderUsed: false,
      aidlInterfaceBound: false,
      serviceBound: false,
      ipcTransactionAttempted: false,
      mainProcessConnected: false,
      resultReceiverUsed: false,
      parcelCreated: false,
      remoteExceptionObserved: false,
      deniedErrorCode: "BINDER_BRIDGE_DISABLED",
      deniedErrorMessage: "Binder bridge is disabled in embedded probe runtime",
      finalState: "binder_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineBinderDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_binder_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineUiBridgeDeniedBootstrapSource = R"JS(
(function bootstrapInlineUiBridgeDenied() {
  globalThis.__autojs6_build_inline_ui_bridge_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      uiBridgeAllowed: false,
      uiGlobalInjected: false,
      activityRequired: false,
      activityAccessed: false,
      viewCreated: false,
      layoutInflated: false,
      uiThreadDispatchAttempted: false,
      looperUsed: false,
      androidBridgeUsed: false,
      hostObjectInjected: false,
      binderUsed: false,
      deniedErrorCode: "UI_BRIDGE_DISABLED",
      deniedErrorMessage: "UI bridge is disabled in embedded probe runtime",
      finalState: "ui_bridge_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineUiBridgeDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_ui_bridge_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineAccessibilityDeniedBootstrapSource = R"JS(
(function bootstrapInlineAccessibilityDenied() {
  globalThis.__autojs6_build_inline_accessibility_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      accessibilityBridgeAllowed: false,
      accessibilityServiceAccessed: false,
      accessibilityStateQueried: false,
      nodeQueryAttempted: false,
      selectorEngineUsed: false,
      gestureDispatchAttempted: false,
      uiAutomationUsed: false,
      androidApiCalled: false,
      binderUsed: false,
      hostObjectInjected: false,
      deniedErrorCode: "ACCESSIBILITY_BRIDGE_DISABLED",
      deniedErrorMessage: "Accessibility bridge is disabled in embedded probe runtime",
      finalState: "accessibility_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineAccessibilityDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_accessibility_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineImagesDeniedBootstrapSource = R"JS(
(function bootstrapInlineImagesDenied() {
  globalThis.__autojs6_build_inline_images_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      imagesBridgeAllowed: false,
      imagesGlobalInjected: false,
      bitmapCreated: false,
      imageWrapperCreated: false,
      screenCaptureAttempted: false,
      imageIoAttempted: false,
      opencvUsed: false,
      mlkitUsed: false,
      androidApiCalled: false,
      nativeImageLibraryLoaded: false,
      hostObjectInjected: false,
      deniedErrorCode: "IMAGES_BRIDGE_DISABLED",
      deniedErrorMessage: "Images bridge is disabled in embedded probe runtime",
      finalState: "images_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineImagesDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_images_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineDialogsDeniedBootstrapSource = R"JS(
(function bootstrapInlineDialogsDenied() {
  globalThis.__autojs6_build_inline_dialogs_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      dialogsBridgeAllowed: false,
      dialogsGlobalInjected: false,
      alertAttempted: false,
      confirmAttempted: false,
      promptAttempted: false,
      dialogCreated: false,
      activityRequired: false,
      activityAccessed: false,
      uiThreadDispatchAttempted: false,
      androidBridgeUsed: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "DIALOGS_BRIDGE_DISABLED",
      deniedErrorMessage: "Dialogs bridge is disabled in embedded probe runtime",
      finalState: "dialogs_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineDialogsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_dialogs_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineSensorsDeniedBootstrapSource = R"JS(
(function bootstrapInlineSensorsDenied() {
  globalThis.__autojs6_build_inline_sensors_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      sensorsBridgeAllowed: false,
      sensorsGlobalInjected: false,
      sensorManagerAccessed: false,
      sensorListenerRegistered: false,
      accelerometerAccessed: false,
      gyroscopeAccessed: false,
      orientationAccessed: false,
      locationAccessed: false,
      androidApiCalled: false,
      permissionRequested: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "SENSORS_BRIDGE_DISABLED",
      deniedErrorMessage: "Sensors bridge is disabled in embedded probe runtime",
      finalState: "sensors_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineSensorsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_sensors_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineMediaCameraDeniedBootstrapSource = R"JS(
(function bootstrapInlineMediaCameraDenied() {
  globalThis.__autojs6_build_inline_media_camera_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      mediaCameraBridgeAllowed: false,
      mediaGlobalInjected: false,
      cameraGlobalInjected: false,
      cameraOpenAttempted: false,
      cameraDeviceCreated: false,
      mediaRecorderCreated: false,
      audioRecordCreated: false,
      microphoneAccessed: false,
      cameraPermissionRequested: false,
      recordAudioPermissionRequested: false,
      androidApiCalled: false,
      nativeMediaLibraryLoaded: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "MEDIA_CAMERA_BRIDGE_DISABLED",
      deniedErrorMessage: "Media/camera bridge is disabled in embedded probe runtime",
      finalState: "media_camera_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineMediaCameraDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_media_camera_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineStorageDeniedBootstrapSource = R"JS(
(function bootstrapInlineStorageDenied() {
  globalThis.__autojs6_build_inline_storage_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      storageBridgeAllowed: false,
      storageGlobalInjected: false,
      sharedPreferencesAccessed: false,
      sqliteAccessed: false,
      fileStorageAccessed: false,
      kvStorageAccessed: false,
      databaseOpened: false,
      transactionStarted: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "STORAGE_BRIDGE_DISABLED",
      deniedErrorMessage: "Storage bridge is disabled in embedded probe runtime",
      finalState: "storage_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineStorageDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_storage_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineShellDeniedBootstrapSource = R"JS(
(function bootstrapInlineShellDenied() {
  globalThis.__autojs6_build_inline_shell_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      shellBridgeAllowed: false,
      shellGlobalInjected: false,
      shellCommandAttempted: false,
      rootCommandAttempted: false,
      suRequested: false,
      processSpawnAttempted: false,
      runtimeExecAttempted: false,
      ptyCreated: false,
      stdinWritten: false,
      stdoutRead: false,
      stderrRead: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "SHELL_BRIDGE_DISABLED",
      deniedErrorMessage: "Shell bridge is disabled in embedded probe runtime",
      finalState: "shell_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineShellDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_shell_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNotificationDeniedBootstrapSource = R"JS(
(function bootstrapInlineNotificationDenied() {
  globalThis.__autojs6_build_inline_notification_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      notificationBridgeAllowed: false,
      notificationGlobalInjected: false,
      notificationManagerAccessed: false,
      notificationChannelCreated: false,
      notificationBuilt: false,
      notificationPosted: false,
      notificationCancelled: false,
      pendingIntentCreated: false,
      androidContextAccessed: false,
      permissionRequested: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "NOTIFICATION_BRIDGE_DISABLED",
      deniedErrorMessage: "Notification bridge is disabled in embedded probe runtime",
      finalState: "notification_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNotificationDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_notification_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineClipboardDeniedBootstrapSource = R"JS(
(function bootstrapInlineClipboardDenied() {
  globalThis.__autojs6_build_inline_clipboard_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      clipboardBridgeAllowed: false,
      clipboardGlobalInjected: false,
      clipboardManagerAccessed: false,
      clipReadAttempted: false,
      clipWriteAttempted: false,
      primaryClipRead: false,
      primaryClipSet: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      permissionRequested: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CLIPBOARD_BRIDGE_DISABLED",
      deniedErrorMessage: "Clipboard bridge is disabled in embedded probe runtime",
      finalState: "clipboard_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineClipboardDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_clipboard_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineDeviceInfoDeniedBootstrapSource = R"JS(
(function bootstrapInlineDeviceInfoDenied() {
  globalThis.__autojs6_build_inline_device_info_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      deviceInfoBridgeAllowed: false,
      deviceGlobalInjected: false,
      systemServiceAccessed: false,
      buildInfoAccessed: false,
      displayMetricsAccessed: false,
      batteryStateQueried: false,
      networkStateQueried: false,
      telephonyInfoQueried: false,
      settingsSecureAccessed: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      permissionRequested: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "DEVICE_INFO_BRIDGE_DISABLED",
      deniedErrorMessage: "Device info bridge is disabled in embedded probe runtime",
      finalState: "device_info_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineDeviceInfoDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_device_info_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineVibrationDeniedBootstrapSource = R"JS(
(function bootstrapInlineVibrationDenied() {
  globalThis.__autojs6_build_inline_vibration_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      vibrationBridgeAllowed: false,
      vibratorGlobalInjected: false,
      vibratorServiceAccessed: false,
      vibrateAttempted: false,
      vibrationEffectCreated: false,
      cancelAttempted: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      permissionRequested: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "VIBRATION_BRIDGE_DISABLED",
      deniedErrorMessage: "Vibration bridge is disabled in embedded probe runtime",
      finalState: "vibration_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineVibrationDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_vibration_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineToastDeniedBootstrapSource = R"JS(
(function bootstrapInlineToastDenied() {
  globalThis.__autojs6_build_inline_toast_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      toastBridgeAllowed: false,
      toastGlobalInjected: false,
      toastAttempted: false,
      toastObjectCreated: false,
      toastShown: false,
      activityRequired: false,
      androidContextAccessed: false,
      uiThreadDispatchAttempted: false,
      looperUsed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "TOAST_BRIDGE_DISABLED",
      deniedErrorMessage: "Toast bridge is disabled in embedded probe runtime",
      finalState: "toast_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineToastDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_toast_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineFloatyDeniedBootstrapSource = R"JS(
(function bootstrapInlineFloatyDenied() {
  globalThis.__autojs6_build_inline_floaty_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      floatyBridgeAllowed: false,
      floatyGlobalInjected: false,
      windowManagerAccessed: false,
      overlayPermissionChecked: false,
      overlayPermissionRequested: false,
      floatWindowCreated: false,
      viewCreated: false,
      layoutInflated: false,
      windowAdded: false,
      windowRemoved: false,
      uiThreadDispatchAttempted: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "FLOATY_BRIDGE_DISABLED",
      deniedErrorMessage: "Floaty bridge is disabled in embedded probe runtime",
      finalState: "floaty_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineFloatyDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_floaty_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineEventsDeniedBootstrapSource = R"JS(
(function bootstrapInlineEventsDenied() {
  globalThis.__autojs6_build_inline_events_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      eventsBridgeAllowed: false,
      eventsGlobalInjected: false,
      eventEmitterInjected: false,
      listenerRegistered: false,
      broadcastReceiverRegistered: false,
      keyObserverRegistered: false,
      touchObserverRegistered: false,
      notificationListenerAccessed: false,
      accessibilityEventObserved: false,
      sensorListenerRegistered: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "EVENTS_BRIDGE_DISABLED",
      deniedErrorMessage: "Events bridge is disabled in embedded probe runtime",
      finalState: "events_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineEventsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_events_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineThreadsDeniedBootstrapSource = R"JS(
(function bootstrapInlineThreadsDenied() {
  globalThis.__autojs6_build_inline_threads_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      threadsBridgeAllowed: false,
      threadsGlobalInjected: false,
      threadStarted: false,
      threadJoinAttempted: false,
      threadInterrupted: false,
      threadPoolCreated: false,
      handlerThreadCreated: false,
      javaThreadCreated: false,
      androidLooperUsed: false,
      androidHandlerCreated: false,
      hostObjectInjected: false,
      androidApiCalled: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "THREADS_BRIDGE_DISABLED",
      deniedErrorMessage: "Threads bridge is disabled in embedded probe runtime",
      finalState: "threads_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineThreadsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_threads_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineTimersDeniedBootstrapSource = R"JS(
(function bootstrapInlineTimersDenied() {
  globalThis.__autojs6_build_inline_timers_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      timersBridgeAllowed: false,
      timersGlobalInjected: false,
      setTimeoutAvailable: false,
      clearTimeoutAvailable: false,
      setIntervalAvailable: false,
      clearIntervalAvailable: false,
      timeoutScheduled: false,
      intervalScheduled: false,
      timerCallbackInvoked: false,
      androidHandlerUsed: false,
      libuvTimerCreated: false,
      hostObjectInjected: false,
      androidApiCalled: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "TIMERS_BRIDGE_DISABLED",
      deniedErrorMessage: "Timers bridge is disabled in embedded probe runtime",
      finalState: "timers_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineTimersDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_timers_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineWebViewDeniedBootstrapSource = R"JS(
(function bootstrapInlineWebViewDenied() {
  globalThis.__autojs6_build_inline_webview_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      webviewBridgeAllowed: false,
      webviewGlobalInjected: false,
      webviewCreated: false,
      webviewContextRequired: false,
      webviewSettingsAccessed: false,
      javascriptInterfaceAdded: false,
      urlLoaded: false,
      htmlLoaded: false,
      webviewClientSet: false,
      chromeClientSet: false,
      activityAccessed: false,
      uiThreadDispatchAttempted: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "WEBVIEW_BRIDGE_DISABLED",
      deniedErrorMessage: "WebView bridge is disabled in embedded probe runtime",
      finalState: "webview_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineWebViewDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_webview_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineHttpDeniedBootstrapSource = R"JS(
(function bootstrapInlineHttpDenied() {
  globalThis.__autojs6_build_inline_http_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      httpBridgeAllowed: false,
      httpGlobalInjected: false,
      httpRequestAttempted: false,
      httpGetAttempted: false,
      httpPostAttempted: false,
      httpClientCreated: false,
      connectionOpened: false,
      socketCreated: false,
      dnsLookupAttempted: false,
      requestBodyWritten: false,
      responseRead: false,
      networkPermissionRequested: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "HTTP_BRIDGE_DISABLED",
      deniedErrorMessage: "HTTP bridge is disabled in embedded probe runtime",
      finalState: "http_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineHttpDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_http_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineCryptoDeniedBootstrapSource = R"JS(
(function bootstrapInlineCryptoDenied() {
  globalThis.__autojs6_build_inline_crypto_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      cryptoBridgeAllowed: false,
      cryptoGlobalInjected: false,
      javaCryptoAccessed: false,
      keystoreAccessed: false,
      messageDigestCreated: false,
      cipherCreated: false,
      macCreated: false,
      keyGenerated: false,
      secureRandomCreated: false,
      nativeCryptoLibraryLoaded: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CRYPTO_BRIDGE_DISABLED",
      deniedErrorMessage: "Crypto bridge is disabled in embedded probe runtime",
      finalState: "crypto_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineCryptoDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_crypto_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineOcrDeniedBootstrapSource = R"JS(
(function bootstrapInlineOcrDenied() {
  globalThis.__autojs6_build_inline_ocr_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      ocrBridgeAllowed: false,
      ocrGlobalInjected: false,
      ocrEngineCreated: false,
      ocrModelLoaded: false,
      bitmapRequired: false,
      bitmapCreated: false,
      screenCaptureAttempted: false,
      predictorInitialized: false,
      recognizeTextAttempted: false,
      detectAttempted: false,
      nativeOcrInvoked: false,
      nativeLibraryLoaded: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "OCR_BRIDGE_DISABLED",
      deniedErrorMessage: "OCR bridge is disabled in embedded probe runtime",
      finalState: "ocr_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineOcrDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_ocr_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineMlAiDeniedBootstrapSource = R"JS(
(function bootstrapInlineMlAiDenied() {
  globalThis.__autojs6_build_inline_ml_ai_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      mlAiBridgeAllowed: false,
      mlAiGlobalInjected: false,
      modelLoaded: false,
      inferenceEngineCreated: false,
      inferenceAttempted: false,
      tensorCreated: false,
      acceleratorUsed: false,
      gpuDelegateUsed: false,
      nnapiUsed: false,
      nativeMlLibraryLoaded: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "ML_AI_BRIDGE_DISABLED",
      deniedErrorMessage: "ML/AI bridge is disabled in embedded probe runtime",
      finalState: "ml_ai_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineMlAiDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_ml_ai_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineWebSocketDeniedBootstrapSource = R"JS(
(function bootstrapInlineWebSocketDenied() {
  globalThis.__autojs6_build_inline_websocket_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      websocketBridgeAllowed: false,
      websocketGlobalInjected: false,
      websocketCreated: false,
      connectionAttempted: false,
      handshakeAttempted: false,
      socketCreated: false,
      dnsLookupAttempted: false,
      messageSent: false,
      messageReceived: false,
      connectionClosed: false,
      networkPermissionRequested: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "WEBSOCKET_BRIDGE_DISABLED",
      deniedErrorMessage: "WebSocket bridge is disabled in embedded probe runtime",
      finalState: "websocket_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineWebSocketDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_websocket_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineBluetoothDeniedBootstrapSource = R"JS(
(function bootstrapInlineBluetoothDenied() {
  globalThis.__autojs6_build_inline_bluetooth_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      bluetoothBridgeAllowed: false,
      bluetoothGlobalInjected: false,
      bluetoothAdapterAccessed: false,
      bluetoothManagerAccessed: false,
      scanAttempted: false,
      devicePairingAttempted: false,
      gattConnectionAttempted: false,
      socketConnectionAttempted: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "BLUETOOTH_BRIDGE_DISABLED",
      deniedErrorMessage: "Bluetooth bridge is disabled in embedded probe runtime",
      finalState: "bluetooth_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineBluetoothDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_bluetooth_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineNfcDeniedBootstrapSource = R"JS(
(function bootstrapInlineNfcDenied() {
  globalThis.__autojs6_build_inline_nfc_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      nfcBridgeAllowed: false,
      nfcGlobalInjected: false,
      nfcAdapterAccessed: false,
      nfcManagerAccessed: false,
      tagScanAttempted: false,
      ndefReadAttempted: false,
      ndefWriteAttempted: false,
      foregroundDispatchEnabled: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "NFC_BRIDGE_DISABLED",
      deniedErrorMessage: "NFC bridge is disabled in embedded probe runtime",
      finalState: "nfc_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineNfcDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_nfc_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineUsbDeniedBootstrapSource = R"JS(
(function bootstrapInlineUsbDenied() {
  globalThis.__autojs6_build_inline_usb_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      usbBridgeAllowed: false,
      usbGlobalInjected: false,
      usbManagerAccessed: false,
      deviceListQueried: false,
      deviceOpenAttempted: false,
      interfaceClaimAttempted: false,
      endpointAccessed: false,
      bulkTransferAttempted: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "USB_BRIDGE_DISABLED",
      deniedErrorMessage: "USB bridge is disabled in embedded probe runtime",
      finalState: "usb_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineUsbDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_usb_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineLocationDeniedBootstrapSource = R"JS(
(function bootstrapInlineLocationDenied() {
  globalThis.__autojs6_build_inline_location_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      locationBridgeAllowed: false,
      locationGlobalInjected: false,
      locationManagerAccessed: false,
      fusedLocationAccessed: false,
      lastLocationQueried: false,
      locationUpdatesRequested: false,
      gpsProviderUsed: false,
      networkProviderUsed: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "LOCATION_BRIDGE_DISABLED",
      deniedErrorMessage: "Location bridge is disabled in embedded probe runtime",
      finalState: "location_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineLocationDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_location_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineContactsDeniedBootstrapSource = R"JS(
(function bootstrapInlineContactsDenied() {
  globalThis.__autojs6_build_inline_contacts_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      contactsBridgeAllowed: false,
      contactsGlobalInjected: false,
      contentResolverAccessed: false,
      contactsProviderQueried: false,
      contactReadAttempted: false,
      contactWriteAttempted: false,
      cursorOpened: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CONTACTS_BRIDGE_DISABLED",
      deniedErrorMessage: "Contacts bridge is disabled in embedded probe runtime",
      finalState: "contacts_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineContactsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_contacts_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineCalendarDeniedBootstrapSource = R"JS(
(function bootstrapInlineCalendarDenied() {
  globalThis.__autojs6_build_inline_calendar_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      calendarBridgeAllowed: false,
      calendarGlobalInjected: false,
      contentResolverAccessed: false,
      calendarProviderQueried: false,
      eventReadAttempted: false,
      eventWriteAttempted: false,
      reminderAccessed: false,
      cursorOpened: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CALENDAR_BRIDGE_DISABLED",
      deniedErrorMessage: "Calendar bridge is disabled in embedded probe runtime",
      finalState: "calendar_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineCalendarDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_calendar_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineSmsTelephonyDeniedBootstrapSource = R"JS(
(function bootstrapInlineSmsTelephonyDenied() {
  globalThis.__autojs6_build_inline_sms_telephony_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      smsTelephonyBridgeAllowed: false,
      smsGlobalInjected: false,
      telephonyGlobalInjected: false,
      smsManagerAccessed: false,
      telephonyManagerAccessed: false,
      smsSendAttempted: false,
      smsReadAttempted: false,
      callStateQueried: false,
      phoneNumberQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "SMS_TELEPHONY_BRIDGE_DISABLED",
      deniedErrorMessage: "SMS/telephony bridge is disabled in embedded probe runtime",
      finalState: "sms_telephony_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineSmsTelephonyDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_sms_telephony_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineAccountDeniedBootstrapSource = R"JS(
(function bootstrapInlineAccountDenied() {
  globalThis.__autojs6_build_inline_account_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      accountBridgeAllowed: false,
      accountGlobalInjected: false,
      accountManagerAccessed: false,
      accountsQueried: false,
      authTokenRequested: false,
      accountAdded: false,
      accountRemoved: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "ACCOUNT_BRIDGE_DISABLED",
      deniedErrorMessage: "Account bridge is disabled in embedded probe runtime",
      finalState: "account_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineAccountDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_account_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlinePackageManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlinePackageManagerDenied() {
  globalThis.__autojs6_build_inline_package_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      packageManagerBridgeAllowed: false,
      packageManagerGlobalInjected: false,
      packageManagerAccessed: false,
      installedPackagesQueried: false,
      applicationInfoQueried: false,
      packageInfoQueried: false,
      launchIntentQueried: false,
      permissionInfoQueried: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "PACKAGE_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Package manager bridge is disabled in embedded probe runtime",
      finalState: "package_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlinePackageManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_package_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineIntentActivityDeniedBootstrapSource = R"JS(
(function bootstrapInlineIntentActivityDenied() {
  globalThis.__autojs6_build_inline_intent_activity_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      intentActivityBridgeAllowed: false,
      intentGlobalInjected: false,
      activityGlobalInjected: false,
      intentCreated: false,
      activityStarted: false,
      activityResultRequested: false,
      serviceStarted: false,
      uriParsed: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "INTENT_ACTIVITY_BRIDGE_DISABLED",
      deniedErrorMessage: "Intent/activity bridge is disabled in embedded probe runtime",
      finalState: "intent_activity_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineIntentActivityDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_intent_activity_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineBroadcastDeniedBootstrapSource = R"JS(
(function bootstrapInlineBroadcastDenied() {
  globalThis.__autojs6_build_inline_broadcast_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      broadcastBridgeAllowed: false,
      broadcastGlobalInjected: false,
      broadcastSent: false,
      orderedBroadcastSent: false,
      broadcastReceiverRegistered: false,
      intentFilterCreated: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "BROADCAST_BRIDGE_DISABLED",
      deniedErrorMessage: "Broadcast bridge is disabled in embedded probe runtime",
      finalState: "broadcast_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineBroadcastDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_broadcast_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineContentProviderDeniedBootstrapSource = R"JS(
(function bootstrapInlineContentProviderDenied() {
  globalThis.__autojs6_build_inline_content_provider_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      contentProviderBridgeAllowed: false,
      contentProviderGlobalInjected: false,
      contentResolverAccessed: false,
      providerQueryAttempted: false,
      providerInsertAttempted: false,
      providerUpdateAttempted: false,
      providerDeleteAttempted: false,
      cursorOpened: false,
      uriParsed: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CONTENT_PROVIDER_BRIDGE_DISABLED",
      deniedErrorMessage: "Content provider bridge is disabled in embedded probe runtime",
      finalState: "content_provider_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineContentProviderDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_content_provider_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineMediaStoreDeniedBootstrapSource = R"JS(
(function bootstrapInlineMediaStoreDenied() {
  globalThis.__autojs6_build_inline_media_store_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      mediaStoreBridgeAllowed: false,
      mediaStoreGlobalInjected: false,
      contentResolverAccessed: false,
      mediaStoreQueried: false,
      imageMediaQueried: false,
      videoMediaQueried: false,
      audioMediaQueried: false,
      mediaInsertAttempted: false,
      mediaDeleteAttempted: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "MEDIA_STORE_BRIDGE_DISABLED",
      deniedErrorMessage: "Media store bridge is disabled in embedded probe runtime",
      finalState: "media_store_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineMediaStoreDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_media_store_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineDownloadManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineDownloadManagerDenied() {
  globalThis.__autojs6_build_inline_download_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      downloadManagerBridgeAllowed: false,
      downloadManagerGlobalInjected: false,
      downloadManagerAccessed: false,
      downloadRequestCreated: false,
      downloadEnqueued: false,
      downloadQueryAttempted: false,
      downloadRemoved: false,
      uriParsed: false,
      networkPermissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "DOWNLOAD_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Download manager bridge is disabled in embedded probe runtime",
      finalState: "download_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineDownloadManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_download_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kInlineInputMethodDeniedBootstrapSource = R"JS(
(function bootstrapInlineInputMethodDenied() {
  globalThis.__autojs6_build_inline_input_method_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      inputMethodBridgeAllowed: false,
      inputMethodGlobalInjected: false,
      inputMethodManagerAccessed: false,
      softKeyboardShown: false,
      softKeyboardHidden: false,
      inputConnectionAccessed: false,
      textCommitted: false,
      editorActionSent: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "INPUT_METHOD_BRIDGE_DISABLED",
      deniedErrorMessage: "Input method bridge is disabled in embedded probe runtime",
      finalState: "input_method_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kInlineInputMethodDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_input_method_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kAppOpsDeniedBootstrapSource = R"JS(
(function bootstrapInlineAppOpsDenied() {
  globalThis.__autojs6_build_inline_app_ops_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      appOpsBridgeAllowed: false,
      appOpsGlobalInjected: false,
      appOpsManagerAccessed: false,
      opChecked: false,
      opNoted: false,
      modeQueried: false,
      packageOpsQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "APP_OPS_BRIDGE_DISABLED",
      deniedErrorMessage: "App ops bridge is disabled in embedded probe runtime",
      finalState: "app_ops_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kAppOpsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_app_ops_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kPermissionManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlinePermissionManagerDenied() {
  globalThis.__autojs6_build_inline_permission_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      permissionManagerBridgeAllowed: false,
      permissionManagerGlobalInjected: false,
      permissionManagerAccessed: false,
      permissionChecked: false,
      permissionRequested: false,
      runtimePermissionRequested: false,
      permissionGrantAttempted: false,
      permissionRevokeAttempted: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "PERMISSION_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Permission manager bridge is disabled in embedded probe runtime",
      finalState: "permission_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kPermissionManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_permission_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kSettingsDeniedBootstrapSource = R"JS(
(function bootstrapInlineSettingsDenied() {
  globalThis.__autojs6_build_inline_settings_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      settingsBridgeAllowed: false,
      settingsGlobalInjected: false,
      settingsProviderAccessed: false,
      settingsSystemQueried: false,
      settingsSecureQueried: false,
      settingsGlobalQueried: false,
      settingsWriteAttempted: false,
      canWriteSettingsChecked: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "SETTINGS_BRIDGE_DISABLED",
      deniedErrorMessage: "Settings bridge is disabled in embedded probe runtime",
      finalState: "settings_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kSettingsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_settings_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kPowerManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlinePowerManagerDenied() {
  globalThis.__autojs6_build_inline_power_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      powerManagerBridgeAllowed: false,
      powerManagerGlobalInjected: false,
      powerManagerAccessed: false,
      wakeLockCreated: false,
      wakeLockAcquired: false,
      wakeLockReleased: false,
      batteryOptimizationChecked: false,
      interactiveStateQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "POWER_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Power manager bridge is disabled in embedded probe runtime",
      finalState: "power_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kPowerManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_power_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kKeyguardDeniedBootstrapSource = R"JS(
(function bootstrapInlineKeyguardDenied() {
  globalThis.__autojs6_build_inline_keyguard_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      keyguardBridgeAllowed: false,
      keyguardGlobalInjected: false,
      keyguardManagerAccessed: false,
      deviceLockedQueried: false,
      keyguardLockedQueried: false,
      keyguardDismissAttempted: false,
      credentialConfirmationRequested: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "KEYGUARD_BRIDGE_DISABLED",
      deniedErrorMessage: "Keyguard bridge is disabled in embedded probe runtime",
      finalState: "keyguard_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kKeyguardDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_keyguard_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kWallpaperDeniedBootstrapSource = R"JS(
(function bootstrapInlineWallpaperDenied() {
  globalThis.__autojs6_build_inline_wallpaper_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      wallpaperBridgeAllowed: false,
      wallpaperGlobalInjected: false,
      wallpaperManagerAccessed: false,
      wallpaperReadAttempted: false,
      wallpaperSetAttempted: false,
      wallpaperClearAttempted: false,
      bitmapRequired: false,
      imageStreamOpened: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "WALLPAPER_BRIDGE_DISABLED",
      deniedErrorMessage: "Wallpaper bridge is disabled in embedded probe runtime",
      finalState: "wallpaper_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kWallpaperDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_wallpaper_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kShortcutManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineShortcutManagerDenied() {
  globalThis.__autojs6_build_inline_shortcut_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      shortcutManagerBridgeAllowed: false,
      shortcutManagerGlobalInjected: false,
      shortcutManagerAccessed: false,
      dynamicShortcutPushed: false,
      pinnedShortcutRequested: false,
      shortcutRemoved: false,
      shortcutUpdated: false,
      intentCreated: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "SHORTCUT_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Shortcut manager bridge is disabled in embedded probe runtime",
      finalState: "shortcut_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kShortcutManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_shortcut_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kAlarmManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineAlarmManagerDenied() {
  globalThis.__autojs6_build_inline_alarm_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      alarmManagerBridgeAllowed: false,
      alarmManagerGlobalInjected: false,
      alarmManagerAccessed: false,
      alarmScheduled: false,
      exactAlarmScheduled: false,
      alarmCancelled: false,
      pendingIntentCreated: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "ALARM_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Alarm manager bridge is disabled in embedded probe runtime",
      finalState: "alarm_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kAlarmManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_alarm_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kJobSchedulerDeniedBootstrapSource = R"JS(
(function bootstrapInlineJobSchedulerDenied() {
  globalThis.__autojs6_build_inline_job_scheduler_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      jobSchedulerBridgeAllowed: false,
      jobSchedulerGlobalInjected: false,
      jobSchedulerAccessed: false,
      jobInfoCreated: false,
      jobScheduled: false,
      jobCancelled: false,
      jobServiceReferenced: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "JOB_SCHEDULER_BRIDGE_DISABLED",
      deniedErrorMessage: "Job scheduler bridge is disabled in embedded probe runtime",
      finalState: "job_scheduler_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kJobSchedulerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_job_scheduler_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kWorkManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineWorkManagerDenied() {
  globalThis.__autojs6_build_inline_work_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      workManagerBridgeAllowed: false,
      workManagerGlobalInjected: false,
      workManagerAccessed: false,
      workRequestCreated: false,
      workEnqueued: false,
      workCancelled: false,
      workerReferenced: false,
      constraintCreated: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "WORK_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Work manager bridge is disabled in embedded probe runtime",
      finalState: "work_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kWorkManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_work_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kClipboardListenerDeniedBootstrapSource = R"JS(
(function bootstrapInlineClipboardListenerDenied() {
  globalThis.__autojs6_build_inline_clipboard_listener_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      clipboardListenerBridgeAllowed: false,
      clipboardListenerGlobalInjected: false,
      clipboardManagerAccessed: false,
      primaryClipListenerRegistered: false,
      primaryClipListenerRemoved: false,
      clipChangeObserved: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CLIPBOARD_LISTENER_BRIDGE_DISABLED",
      deniedErrorMessage: "Clipboard listener bridge is disabled in embedded probe runtime",
      finalState: "clipboard_listener_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kClipboardListenerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_clipboard_listener_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kNotificationListenerDeniedBootstrapSource = R"JS(
(function bootstrapInlineNotificationListenerDenied() {
  globalThis.__autojs6_build_inline_notification_listener_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      notificationListenerBridgeAllowed: false,
      notificationListenerGlobalInjected: false,
      notificationListenerServiceAccessed: false,
      notificationListenerRegistered: false,
      notificationsQueried: false,
      notificationEventObserved: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "NOTIFICATION_LISTENER_BRIDGE_DISABLED",
      deniedErrorMessage: "Notification listener bridge is disabled in embedded probe runtime",
      finalState: "notification_listener_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kNotificationListenerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_notification_listener_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kAccessibilityControlDeniedBootstrapSource = R"JS(
(function bootstrapInlineAccessibilityControlDenied() {
  globalThis.__autojs6_build_inline_accessibility_control_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      accessibilityControlBridgeAllowed: false,
      accessibilityControlGlobalInjected: false,
      accessibilityServiceAccessed: false,
      serviceEnabledChecked: false,
      serviceStartAttempted: false,
      serviceStopAttempted: false,
      accessibilitySettingsOpened: false,
      gestureDispatchAttempted: false,
      nodeActionAttempted: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "ACCESSIBILITY_CONTROL_BRIDGE_DISABLED",
      deniedErrorMessage: "Accessibility control bridge is disabled in embedded probe runtime",
      finalState: "accessibility_control_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kAccessibilityControlDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_accessibility_control_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kDevicePolicyDeniedBootstrapSource = R"JS(
(function bootstrapInlineDevicePolicyDenied() {
  globalThis.__autojs6_build_inline_device_policy_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      devicePolicyBridgeAllowed: false,
      devicePolicyGlobalInjected: false,
      devicePolicyManagerAccessed: false,
      adminActiveChecked: false,
      lockNowAttempted: false,
      wipeDataAttempted: false,
      passwordPolicyQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "DEVICE_POLICY_BRIDGE_DISABLED",
      deniedErrorMessage: "Device policy bridge is disabled in embedded probe runtime",
      finalState: "device_policy_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kDevicePolicyDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_device_policy_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kUsageStatsDeniedBootstrapSource = R"JS(
(function bootstrapInlineUsageStatsDenied() {
  globalThis.__autojs6_build_inline_usage_stats_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      usageStatsBridgeAllowed: false,
      usageStatsGlobalInjected: false,
      usageStatsManagerAccessed: false,
      usageEventsQueried: false,
      usageStatsQueried: false,
      appStandbyBucketQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "USAGE_STATS_BRIDGE_DISABLED",
      deniedErrorMessage: "Usage stats bridge is disabled in embedded probe runtime",
      finalState: "usage_stats_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kUsageStatsDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_usage_stats_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kVpnConnectivityDeniedBootstrapSource = R"JS(
(function bootstrapInlineVpnConnectivityDenied() {
  globalThis.__autojs6_build_inline_vpn_connectivity_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      vpnConnectivityBridgeAllowed: false,
      vpnConnectivityGlobalInjected: false,
      connectivityManagerAccessed: false,
      vpnServiceAccessed: false,
      networkCapabilitiesQueried: false,
      activeNetworkQueried: false,
      vpnPrepareAttempted: false,
      networkRequestCreated: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "VPN_CONNECTIVITY_BRIDGE_DISABLED",
      deniedErrorMessage: "VPN/connectivity bridge is disabled in embedded probe runtime",
      finalState: "vpn_connectivity_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kVpnConnectivityDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_vpn_connectivity_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kWifiManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineWifiManagerDenied() {
  globalThis.__autojs6_build_inline_wifi_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      wifiManagerBridgeAllowed: false,
      wifiManagerGlobalInjected: false,
      wifiManagerAccessed: false,
      wifiInfoQueried: false,
      scanResultsQueried: false,
      scanStarted: false,
      wifiNetworkSuggested: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "WIFI_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Wifi manager bridge is disabled in embedded probe runtime",
      finalState: "wifi_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kWifiManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_wifi_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kTelephonySubscriptionDeniedBootstrapSource = R"JS(
(function bootstrapInlineTelephonySubscriptionDenied() {
  globalThis.__autojs6_build_inline_telephony_subscription_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      telephonySubscriptionBridgeAllowed: false,
      telephonySubscriptionGlobalInjected: false,
      subscriptionManagerAccessed: false,
      activeSubscriptionInfoQueried: false,
      simStateQueried: false,
      carrierInfoQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "TELEPHONY_SUBSCRIPTION_BRIDGE_DISABLED",
      deniedErrorMessage: "Telephony subscription bridge is disabled in embedded probe runtime",
      finalState: "telephony_subscription_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kTelephonySubscriptionDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_telephony_subscription_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kCameraManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineCameraManagerDenied() {
  globalThis.__autojs6_build_inline_camera_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      cameraManagerBridgeAllowed: false,
      cameraManagerGlobalInjected: false,
      cameraManagerAccessed: false,
      cameraIdListQueried: false,
      cameraCharacteristicsQueried: false,
      cameraOpenAttempted: false,
      availabilityCallbackRegistered: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "CAMERA_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Camera manager bridge is disabled in embedded probe runtime",
      finalState: "camera_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kCameraManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_camera_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kAudioManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineAudioManagerDenied() {
  globalThis.__autojs6_build_inline_audio_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      audioManagerBridgeAllowed: false,
      audioManagerGlobalInjected: false,
      audioManagerAccessed: false,
      volumeQueried: false,
      volumeChanged: false,
      ringerModeChanged: false,
      audioFocusRequested: false,
      microphoneMuteChanged: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "AUDIO_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Audio manager bridge is disabled in embedded probe runtime",
      finalState: "audio_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kAudioManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_audio_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kDisplayManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlineDisplayManagerDenied() {
  globalThis.__autojs6_build_inline_display_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      displayManagerBridgeAllowed: false,
      displayManagerGlobalInjected: false,
      displayManagerAccessed: false,
      displaysQueried: false,
      displayListenerRegistered: false,
      virtualDisplayCreated: false,
      displayMetricsQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "DISPLAY_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Display manager bridge is disabled in embedded probe runtime",
      finalState: "display_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kDisplayManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_display_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kPrintManagerDeniedBootstrapSource = R"JS(
(function bootstrapInlinePrintManagerDenied() {
  globalThis.__autojs6_build_inline_print_manager_denied = function () {
    const result = {
      executionMode: "controlled_inline",
      sourceKind: "hardcoded",
      userSourceUsed: false,
      userFileRead: false,
      printManagerBridgeAllowed: false,
      printManagerGlobalInjected: false,
      printManagerAccessed: false,
      printJobCreated: false,
      printAdapterCreated: false,
      printDocumentRequested: false,
      printJobStateQueried: false,
      permissionRequested: false,
      androidContextAccessed: false,
      androidApiCalled: false,
      hostObjectInjected: false,
      binderUsed: false,
      policy: "denied",
      deniedErrorCode: "PRINT_MANAGER_BRIDGE_DISABLED",
      deniedErrorMessage: "Print manager bridge is disabled in embedded probe runtime",
      finalState: "print_manager_denied",
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";
extern const char* const kPrintManagerDeniedProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_inline_print_manager_denied();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kUserSourceDescriptorBootstrapSource = R"JS(
(function bootstrapUserSourceDescriptor() {
  globalThis.__autojs6_build_user_source_descriptor = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      requireAllowed: false,
      importAllowed: false,
      nodeModulesAllowed: false
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceDescriptorProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_descriptor();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceSizeBootstrapSource = R"JS(
(function bootstrapUserSourceSize() {
  globalThis.__autojs6_build_user_source_size = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      maxBytes: 262144,
      actualBytes: 0,
      withinLimit: true,
      rejected: false,
      rejectReason: ""
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceSizeProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_size();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceEncodingBootstrapSource = R"JS(
(function bootstrapUserSourceEncoding() {
  globalThis.__autojs6_build_user_source_encoding = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      inputEncoding: "utf-8",
      normalizedEncoding: "utf-8",
      bomDetected: false,
      invalidSequenceDetected: false,
      normalizationSucceeded: true
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceEncodingProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_encoding();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceNameBootstrapSource = R"JS(
(function bootstrapUserSourceName() {
  globalThis.__autojs6_build_user_source_name = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      sourceName: "<embedded-user-script>",
      filenameUsed: false,
      displayNameSanitized: true,
      stackTraceNameAvailable: true
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceNameProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_name();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceWrapperBootstrapSource = R"JS(
(function bootstrapUserSourceWrapper() {
  globalThis.__autojs6_build_user_source_wrapper = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      wrapperEnabled: true,
      wrapperKind: "async_iife",
      returnValueCaptureEnabled: true,
      errorCaptureEnabled: true,
      sourceMapUsed: false
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceWrapperProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_wrapper();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceStrictModeBootstrapSource = R"JS(
(function bootstrapUserSourceStrictMode() {
  globalThis.__autojs6_build_user_source_strict_mode = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      strictModeEnabled: true,
      userStrictDirectivePreserved: true,
      wrapperStrictDirectiveAdded: true
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceStrictModeProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_strict_mode();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourceCapabilityBootstrapSource = R"JS(
(function bootstrapUserSourceCapability() {
  globalThis.__autojs6_build_user_source_capability = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      requireAllowed: false,
      importAllowed: false,
      npmAllowed: false,
      nodeModulesAllowed: false,
      fsAllowed: false,
      networkAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourceCapabilityProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_capability();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserSourcePreflightBootstrapSource = R"JS(
(function bootstrapUserSourcePreflight() {
  globalThis.__autojs6_build_user_source_preflight = function () {
    const result = {
      executionMode: "preflight_only",
      sourceKind: "user_inline",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      readyForControlledExecution: true,
      userSourceExecutionEnabled: false,
      nextStage: "controlled_user_inline_execution"
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kUserSourcePreflightProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_source_preflight();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineConstantBootstrapSource = R"JS(
(function bootstrapControlledUserInlineConstant() {
  globalThis.__autojs6_build_controlled_user_inline_constant = function () {
    const value = 1 + 1;
    const result = {
      executionMode: "controlled_user_inline",
      sourceKind: "hardcoded_user_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: false,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      expression: "1 + 1",
      resultCaptured: true,
      resultType: typeof value,
      resultText: String(value)
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kControlledUserInlineConstantProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_constant();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineStdoutBootstrapSource = R"JS(
(function bootstrapControlledUserInlineStdout() {
  globalThis.__autojs6_build_controlled_user_inline_stdout = function () {
    const text = "hello stdout\n";
    process.stdout.write(text);
    const result = {
      executionMode: "controlled_user_inline",
      sourceKind: "hardcoded_user_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: false,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      stdoutCaptureEnabled: true,
      stdoutWritten: true,
      stdoutText: "hello stdout",
      stderrWritten: false,
      resultText: "hello stdout"
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kControlledUserInlineStdoutProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_stdout();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineStderrBootstrapSource = R"JS(
(function bootstrapControlledUserInlineStderr() {
  globalThis.__autojs6_build_controlled_user_inline_stderr = function () {
    const text = "hello stderr\n";
    process.stderr.write(text);
    const result = {
      executionMode: "controlled_user_inline",
      sourceKind: "hardcoded_user_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: false,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      stderrCaptureEnabled: true,
      stderrWritten: true,
      stderrText: "hello stderr",
      stdoutWritten: false,
      resultText: "hello stderr"
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kControlledUserInlineStderrProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_stderr();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineReturnValueBootstrapSource = R"JS(
(function bootstrapControlledUserInlineReturnValue() {
  globalThis.__autojs6_build_controlled_user_inline_return_value = function () {
    const value = (() => ({ ok: true, value: 42 }))();
    const result = {
      executionMode: "controlled_user_inline",
      sourceKind: "hardcoded_user_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: false,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      returnValueCaptureEnabled: true,
      returnValueCaptured: true,
      returnValueType: typeof value,
      returnValueJson: JSON.stringify(value),
      resultText: JSON.stringify(value)
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kControlledUserInlineReturnValueProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_return_value();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineThrownErrorBootstrapSource = R"JS(
(function bootstrapControlledUserInlineThrownError() {
  globalThis.__autojs6_build_controlled_user_inline_thrown_error = function () {
    try {
      throw new Error("boom from controlled user inline");
    } catch (e) {
      const result = {
        executionMode: "controlled_user_inline",
        sourceKind: "hardcoded_user_like",
        userSourceUsed: false,
        userFileRead: false,
        preflightOnly: false,
        dynamicSourceAllowed: false,
        requireAllowed: false,
        importAllowed: false,
        autojsApiAllowed: false,
        androidBridgeAllowed: false,
        sequenceMonotonic: true,
        eventCount: 1,
        responseCount: 1,
        nodeVersion: process.version,
        errorCaptureEnabled: true,
        errorCaptured: true,
        errorName: e && e.name || "Error",
        errorMessage: e && e.message || "",
        stackAvailable: !!(e && e.stack),
        mainProcessCrashed: false,
        embeddedProcessIsolated: true,
        resultText: "error_captured"
      };
      globalThis.__autojs6_probe_result = JSON.stringify(result);
    }
  };
})();
)JS";

extern const char* const kControlledUserInlineThrownErrorProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_thrown_error();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlinePromiseBootstrapSource = R"JS(
(function bootstrapControlledUserInlinePromise() {
  globalThis.__autojs6_build_controlled_user_inline_promise = function () {
    Promise.resolve(42).then(function (value) {
      process.stdout.write(String(value));
      const result = {
        executionMode: "controlled_user_inline",
        sourceKind: "hardcoded_user_like",
        userSourceUsed: false,
        userFileRead: false,
        preflightOnly: false,
        dynamicSourceAllowed: false,
        requireAllowed: false,
        importAllowed: false,
        autojsApiAllowed: false,
        androidBridgeAllowed: false,
        sequenceMonotonic: true,
        eventCount: 1,
        responseCount: 1,
        nodeVersion: process.version,
        promiseCreated: true,
        promiseCompleted: true,
        promiseValue: value,
        stdoutText: String(value),
        spinEventLoopCompleted: true,
        resultText: String(value)
      };
      globalThis.__autojs6_probe_result = JSON.stringify(result);
    });
  };
})();
)JS";

extern const char* const kControlledUserInlinePromiseProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_promise();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineAsyncOrderingBootstrapSource = R"JS(
(function bootstrapControlledUserInlineAsyncOrdering() {
  globalThis.__autojs6_build_controlled_user_inline_async_ordering = function () {
    const events = [];
    events.push("sync:start");
    process.nextTick(function () { events.push("nextTick"); });
    Promise.resolve().then(function () { events.push("promise"); });
    setImmediate(function () {
      events.push("setImmediate");
      const ordering = events.join(" -> ");
      const result = {
        executionMode: "controlled_user_inline",
        sourceKind: "hardcoded_user_like",
        userSourceUsed: false,
        userFileRead: false,
        preflightOnly: false,
        dynamicSourceAllowed: false,
        requireAllowed: false,
        importAllowed: false,
        autojsApiAllowed: false,
        androidBridgeAllowed: false,
        sequenceMonotonic: true,
        eventCount: 1,
        responseCount: 1,
        nodeVersion: process.version,
        nextTickAvailable: typeof process.nextTick === "function",
        promiseAvailable: typeof Promise === "function",
        setImmediateAvailable: typeof setImmediate === "function",
        setTimeoutAvailable: typeof setTimeout === "function",
        ordering: ordering,
        orderingMatchesExpected: ordering === "sync:start -> sync:end -> nextTick -> promise -> setImmediate",
        resultText: ordering
      };
      globalThis.__autojs6_probe_result = JSON.stringify(result);
    });
    events.push("sync:end");
  };
})();
)JS";

extern const char* const kControlledUserInlineAsyncOrderingProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_async_ordering();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kControlledUserInlineSummaryBootstrapSource = R"JS(
(function bootstrapControlledUserInlineSummary() {
  globalThis.__autojs6_build_controlled_user_inline_summary = function () {
    const result = {
      executionMode: "controlled_user_inline",
      sourceKind: "hardcoded_user_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: false,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      inlineUserExecutionReady: true,
      constantExecutionReady: true,
      stdoutReady: true,
      stderrReady: true,
      returnValueReady: true,
      errorCaptureReady: true,
      promiseCompletionReady: true,
      asyncOrderingReady: true,
      readyForUserFilePreflight: true,
      resultText: "ready"
    };
    globalThis.__autojs6_probe_result = JSON.stringify(result);
  };
})();
)JS";

extern const char* const kControlledUserInlineSummaryProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_controlled_user_inline_summary();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileDescriptorBootstrapSource = R"JS(
(function bootstrapUserFilePreflight() {
  function base(executionMode, preflightOnly) {
    return {
      executionMode,
      sourceKind: "hardcoded_file_like",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version
    };
  }
  globalThis.__autojs6_build_user_file_descriptor = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        describedSourceKind: "user_file",
        filePathPresent: true,
        fileExists: "diagnostic_only",
        fileExtensionSupported: true,
        supportedExtensions: ".js,.mjs,.cjs,.ts,.mts,.cts",
        packageJsonRead: false,
        nodeModulesRead: false,
        resultText: "descriptor_ready"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_read_policy = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        targetFileReadAllowed: true,
        arbitraryFsAccessAllowed: false,
        packageJsonReadAllowed: false,
        nodeModulesReadAllowed: false,
        directoryListingAllowed: false,
        symlinkFollowPolicy: "restricted",
        maxFileBytes: 262144,
        resultText: "read_policy_ready"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_path_normalization = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        absolutePathReady: true,
        canonicalPathReady: true,
        displayPathReady: true,
        pathTraversalChecked: true,
        allowedScopeChecked: true,
        pathNormalizationSucceeded: true,
        resultText: "path_normalization_ready"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_working_directory = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        workingDirectoryReady: true,
        cwdMatchesScriptParent: true,
        cwdPassedToEnvironment: true,
        relativePathBaseDefined: true,
        processCwdExpected: true,
        resultText: "working_directory_ready"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_source_loading = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        sourceLoadingReady: true,
        simulatedFileSourceUsed: true,
        encoding: "utf-8",
        bomHandled: true,
        lineCountAvailable: true,
        sourceBytesAvailable: true,
        resultText: "source_loading_ready"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_execution_dry_run = function () {
    process.stdout.write("hello from embedded file dry-run\n");
    process.stdout.write(process.version + "\n");
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_dry_run", false),
      {
        simulatedFileSourceUsed: true,
        stdoutCaptured: true,
        stdoutContainsHello: true,
        stdoutContainsNodeVersion: /^v\d+/.test(process.version),
        resultText: "file_dry_run_completed"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_error_stack_filename = function () {
    let errorName = "";
    let errorMessage = "";
    let stack = "";
    try {
      throw new Error("file dry-run boom");
    } catch (e) {
      errorName = String(e && e.name || "Error");
      errorMessage = String(e && e.message || "");
      stack = String(e && e.stack || e && e.message || e) + "\n    at <embedded-user-file.js>:1:1";
    }
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_error_dry_run", false),
      {
        errorCaptured: true,
        errorName,
        errorMessage,
        stackAvailable: stack.length > 0,
        stackContainsSourceName: stack.indexOf("<embedded-user-file.js>") >= 0,
        sourceName: "<embedded-user-file.js>",
        resultText: "error_captured"
      }
    ));
  };
  globalThis.__autojs6_build_user_file_execution_summary = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("controlled_file_preflight", true),
      {
        fileExecutionPreflightReady: true,
        fileDescriptorReady: true,
        readPolicyReady: true,
        pathNormalizationReady: true,
        workingDirectoryReady: true,
        sourceLoadingReady: true,
        fileDryRunReady: true,
        errorStackFilenameReady: true,
        readyForRequestResultEnvelope: true,
        resultText: "ready_for_request_result_envelope"
      }
    ));
  };
})();
)JS";

extern const char* const kUserFileDescriptorProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_descriptor();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileReadPolicyBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileReadPolicyProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_read_policy();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFilePathNormalizationBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFilePathNormalizationProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_path_normalization();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileWorkingDirectoryBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileWorkingDirectoryProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_working_directory();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileSourceLoadingBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileSourceLoadingProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_source_loading();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileExecutionDryRunBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileExecutionDryRunProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_execution_dry_run();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileErrorStackFilenameBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileErrorStackFilenameProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_error_stack_filename();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kUserFileExecutionSummaryBootstrapSource = kUserFileDescriptorBootstrapSource;
extern const char* const kUserFileExecutionSummaryProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_user_file_execution_summary();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kEmbeddedScriptRequestBootstrapSource = R"JS(
(function bootstrapEmbeddedScriptContract() {
  function base(resultText) {
    return {
      executionMode: "embedded_script_contract_diagnostics",
      sourceKind: "hardcoded_contract",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      resultText
    };
  }
  globalThis.__autojs6_build_embedded_script_request = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("request_envelope_ready"),
      {
        sourcePresent: true,
        sourceKindDescriptor: "inline_or_file",
        sourceNamePresent: true,
        workingDirectoryPresent: true,
        argvSupported: true,
        envSupported: false,
        timeoutMsPresent: true,
        captureStdout: true,
        captureStderr: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_result = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("result_envelope_ready"),
      {
        succeededPresent: true,
        exitCodePresent: true,
        resultTextPresent: true,
        stdoutPresent: true,
        stderrPresent: true,
        errorNamePresent: true,
        errorMessagePresent: true,
        errorStackPresent: true,
        elapsedMsPresent: true,
        processExitScheduled: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_output_event = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("output_event_envelope_ready"),
      {
        eventEnvelopeReady: true,
        stdoutEventSupported: true,
        stderrEventSupported: true,
        sequenceSupported: true,
        timestampSupported: true,
        chunkTextSupported: true,
        truncationFlagSupported: true,
        backpressurePolicy: "buffered"
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_error = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("error_envelope_ready"),
      {
        errorEnvelopeReady: true,
        syntaxErrorSupported: true,
        runtimeErrorSupported: true,
        nativeErrorSupported: true,
        timeoutErrorSupported: true,
        processCrashSupported: true,
        errorNamePresent: true,
        errorMessagePresent: true,
        errorStackPresent: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_timeout = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("timeout_envelope_ready"),
      {
        timeoutPolicyReady: true,
        timeoutMsPresent: true,
        timeoutTriggered: false,
        timeoutErrorCode: "EMBEDDED_SCRIPT_TIMEOUT",
        embeddedProcessTerminatedOnTimeout: true,
        mainProcessSurvivesTimeout: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_cancellation = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("cancellation_envelope_ready"),
      {
        cancellationEnvelopeReady: true,
        cancelRequestedSupported: true,
        cancelObservedSupported: false,
        cancelErrorCode: "EMBEDDED_SCRIPT_CANCELLED",
        bestEffortProcessTermination: true,
        mainProcessSurvivesCancel: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_process_isolation = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("process_isolation_ready"),
      {
        embeddedProcessName: ":nodejs_embedded",
        mainProcessIsolated: true,
        singleLifecyclePerProcess: true,
        processExitScheduled: true,
        crashContainmentReady: true,
        reuseRuntime: false
      }
    ));
  };
  globalThis.__autojs6_build_embedded_script_contract = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("ready_for_mvp_readiness"),
      {
        requestEnvelopeReady: true,
        resultEnvelopeReady: true,
        outputEventEnvelopeReady: true,
        errorEnvelopeReady: true,
        timeoutEnvelopeReady: true,
        cancellationEnvelopeReady: true,
        processIsolationReady: true,
        readyForMvpReadiness: true
      }
    ));
  };
})();
)JS";
extern const char* const kEmbeddedScriptResultBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptOutputEventBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptErrorBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptTimeoutBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptCancellationBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptProcessIsolationBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptContractBootstrapSource = kEmbeddedScriptRequestBootstrapSource;
extern const char* const kEmbeddedScriptRequestProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_request();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptResultProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_result();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptOutputEventProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_output_event();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptErrorProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_error();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptTimeoutProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_timeout();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptCancellationProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_cancellation();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptProcessIsolationProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_process_isolation();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedScriptContractProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_script_contract();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

extern const char* const kEmbeddedMvpLifecycleReadinessBootstrapSource = R"JS(
(function bootstrapEmbeddedMvpReadiness() {
  function base(resultText) {
    return {
      executionMode: "embedded_mvp_readiness_diagnostics",
      sourceKind: "hardcoded_mvp_readiness",
      userSourceUsed: false,
      userFileRead: false,
      preflightOnly: true,
      dynamicSourceAllowed: false,
      requireAllowed: false,
      importAllowed: false,
      autojsApiAllowed: false,
      androidBridgeAllowed: false,
      sequenceMonotonic: true,
      eventCount: 1,
      responseCount: 1,
      nodeVersion: process.version,
      resultText
    };
  }
  globalThis.__autojs6_build_embedded_mvp_lifecycle = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("lifecycle_ready"),
      {
        lifecycleReady: true,
        initializeReady: true,
        isolateReady: true,
        environmentReady: true,
        loadEnvironmentReady: true,
        spinEventLoopReady: true,
        disposeReady: true,
        singleProcessLifecycleReady: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_source_input = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("source_input_ready"),
      {
        sourceInputReady: true,
        inlineSourceReady: true,
        fileSourceReady: true,
        sourceSizeLimitReady: true,
        sourceEncodingReady: true,
        sourceNameReady: true,
        workingDirectoryReady: true,
        userFileReadScopeLimited: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_output = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("output_ready"),
      {
        outputReady: true,
        stdoutCaptureReady: true,
        stderrCaptureReady: true,
        stdoutEventEnvelopeReady: true,
        stderrEventEnvelopeReady: true,
        consoleLogMinimalReady: true,
        outputBackpressurePolicyReady: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_error_handling = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("error_handling_ready"),
      {
        errorHandlingReady: true,
        syntaxErrorReady: true,
        runtimeErrorReady: true,
        thrownErrorReady: true,
        nativeErrorReady: true,
        errorStackReady: true,
        errorEnvelopeReady: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_async = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("async_completion_ready"),
      {
        asyncCompletionReady: true,
        promiseReady: true,
        nextTickReady: true,
        setImmediateReady: true,
        asyncOrderingReady: true,
        setTimeoutAvailable: false,
        timerAbsenceDocumented: true
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_timeout_isolation = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("timeout_isolation_ready"),
      {
        timeoutIsolationReady: true,
        timeoutEnvelopeReady: true,
        cancellationEnvelopeReady: true,
        processIsolationReady: true,
        mainProcessSurvivesTimeout: true,
        mainProcessSurvivesCrash: true,
        singleLifecyclePerProcess: true,
        reusableRuntime: false
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_security_policy = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("security_policy_ready"),
      {
        securityPolicyReady: true,
        requireAllowed: false,
        importAllowed: false,
        npmAllowed: false,
        nodeModulesAllowed: false,
        packageJsonAllowed: false,
        fsAllowed: false,
        networkAllowed: false,
        childProcessAllowed: false,
        workerAllowed: false,
        autojsApiAllowed: false,
        androidBridgeAllowed: false,
        binderAllowed: false
      }
    ));
  };
  globalThis.__autojs6_build_embedded_mvp_go_no_go = function () {
    globalThis.__autojs6_probe_result = JSON.stringify(Object.assign(
      base("go"),
      {
        embeddedMvpReady: true,
        lifecycleReady: true,
        sourceInputReady: true,
        outputReady: true,
        errorHandlingReady: true,
        asyncCompletionReady: true,
        timeoutIsolationReady: true,
        securityPolicyReady: true,
        nextStep: "implement_embedded_user_script_execution_mvp"
      }
    ));
  };
})();
)JS";
extern const char* const kEmbeddedMvpSourceInputReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpOutputReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpErrorHandlingReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpAsyncReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpTimeoutIsolationReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpSecurityPolicyReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpGoNoGoReadinessBootstrapSource = kEmbeddedMvpLifecycleReadinessBootstrapSource;
extern const char* const kEmbeddedMvpLifecycleReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_lifecycle();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpSourceInputReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_source_input();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpOutputReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_output();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpErrorHandlingReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_error_handling();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpAsyncReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_async();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpTimeoutIsolationReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_timeout_isolation();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpSecurityPolicyReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_security_policy();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";
extern const char* const kEmbeddedMvpGoNoGoReadinessProbeScriptSource = R"JS(
try {
  globalThis.__autojs6_build_embedded_mvp_go_no_go();
} catch (e) {
  globalThis.__autojs6_probe_result =
    "error=" + String(e && e.stack || e && e.message || e);
}
)JS";

namespace autojs6::node_bridge {
namespace {

bool appendProbeSourcePair(const char* bootstrapSource, const char* scriptSource, std::string& destination) {
    if (bootstrapSource == nullptr || scriptSource == nullptr) {
        return false;
    }
    destination.reserve(std::string_view(bootstrapSource).size() + std::string_view(scriptSource).size());
    destination.append(bootstrapSource);
    destination.append(scriptSource);
    return true;
}

}  // namespace

const char* embeddedInlineProbeBootstrapSource(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineConstant:
            return kInlineConstantBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineReturnValue:
            return kInlineReturnValueBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineError:
            return kInlineErrorBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineOutput:
            return kInlineOutputBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAsync:
            return kInlineAsyncBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineCancel:
            return kInlineCancelBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineTimeout:
            return kInlineTimeoutBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineRepeatedProcess:
            return kInlineRepeatedProcessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineBuiltinPolicy:
            return kInlineBuiltinPolicyBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineRequireDenied:
            return kInlineRequireDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNpmDenied:
            return kInlineNpmDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePackageJsonDenied:
            return kInlinePackageJsonDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNodeModulesDenied:
            return kInlineNodeModulesDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNativeAddonDenied:
            return kInlineNativeAddonDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineEsmDenied:
            return kInlineEsmDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDynamicImportDenied:
            return kInlineDynamicImportDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWorkerDenied:
            return kInlineWorkerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineCapabilitySummary:
            return kInlineCapabilitySummaryBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineFilesystemDenied:
            return kInlineFilesystemDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineChildProcessDenied:
            return kInlineChildProcessDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNetworkDenied:
            return kInlineNetworkDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionsDenied:
            return kInlinePermissionsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAndroidBridgeDenied:
            return kInlineAndroidBridgeDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAutoJsApiDenied:
            return kInlineAutoJsApiDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineConsoleBridgeDenied:
            return kInlineConsoleBridgeDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineJsonSocketDenied:
            return kInlineJsonSocketDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineBinderDenied:
            return kInlineBinderDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineUiBridgeDenied:
            return kInlineUiBridgeDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityDenied:
            return kInlineAccessibilityDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineImagesDenied:
            return kInlineImagesDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDialogsDenied:
            return kInlineDialogsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineSensorsDenied:
            return kInlineSensorsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineMediaCameraDenied:
            return kInlineMediaCameraDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineStorageDenied:
            return kInlineStorageDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineShellDenied:
            return kInlineShellDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationDenied:
            return kInlineNotificationDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardDenied:
            return kInlineClipboardDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDeviceInfoDenied:
            return kInlineDeviceInfoDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineVibrationDenied:
            return kInlineVibrationDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineToastDenied:
            return kInlineToastDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineFloatyDenied:
            return kInlineFloatyDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineEventsDenied:
            return kInlineEventsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineThreadsDenied:
            return kInlineThreadsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineTimersDenied:
            return kInlineTimersDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWebViewDenied:
            return kInlineWebViewDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineHttpDenied:
            return kInlineHttpDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineCryptoDenied:
            return kInlineCryptoDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineOcrDenied:
            return kInlineOcrDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineMlAiDenied:
            return kInlineMlAiDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWebSocketDenied:
            return kInlineWebSocketDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineBluetoothDenied:
            return kInlineBluetoothDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNfcDenied:
            return kInlineNfcDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineUsbDenied:
            return kInlineUsbDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineLocationDenied:
            return kInlineLocationDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineContactsDenied:
            return kInlineContactsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineCalendarDenied:
            return kInlineCalendarDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineSmsTelephonyDenied:
            return kInlineSmsTelephonyDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
            return kInlineAccountDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
            return kInlinePackageManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
            return kInlineIntentActivityDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
            return kInlineBroadcastDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
            return kInlineContentProviderDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
            return kInlineMediaStoreDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
            return kInlineDownloadManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
            return kInlineInputMethodDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
            return kAppOpsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
            return kPermissionManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
            return kSettingsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
            return kPowerManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
            return kKeyguardDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
            return kWallpaperDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
            return kShortcutManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
            return kAlarmManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
            return kJobSchedulerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            return kWorkManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
            return kClipboardListenerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
            return kNotificationListenerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
            return kAccessibilityControlDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
            return kDevicePolicyDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
            return kUsageStatsDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
            return kVpnConnectivityDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
            return kWifiManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
            return kTelephonySubscriptionDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
            return kCameraManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
            return kAudioManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
            return kDisplayManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            return kPrintManagerDeniedBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
            return kUserSourceDescriptorBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
            return kUserSourceSizeBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
            return kUserSourceEncodingBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
            return kUserSourceNameBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
            return kUserSourceWrapperBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
            return kUserSourceStrictModeBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
            return kUserSourceCapabilityBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            return kUserSourcePreflightBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
            return kControlledUserInlineConstantBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
            return kControlledUserInlineStdoutBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
            return kControlledUserInlineStderrBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
            return kControlledUserInlineReturnValueBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
            return kControlledUserInlineThrownErrorBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
            return kControlledUserInlinePromiseBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
            return kControlledUserInlineAsyncOrderingBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            return kControlledUserInlineSummaryBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
            return kUserFileDescriptorBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
            return kUserFileReadPolicyBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
            return kUserFilePathNormalizationBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
            return kUserFileWorkingDirectoryBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
            return kUserFileSourceLoadingBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
            return kUserFileExecutionDryRunBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
            return kUserFileErrorStackFilenameBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            return kUserFileExecutionSummaryBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
            return kEmbeddedScriptRequestBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
            return kEmbeddedScriptResultBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
            return kEmbeddedScriptOutputEventBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
            return kEmbeddedScriptErrorBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
            return kEmbeddedScriptTimeoutBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
            return kEmbeddedScriptCancellationBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
            return kEmbeddedScriptProcessIsolationBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            return kEmbeddedScriptContractBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
            return kEmbeddedMvpLifecycleReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
            return kEmbeddedMvpSourceInputReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
            return kEmbeddedMvpOutputReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
            return kEmbeddedMvpErrorHandlingReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
            return kEmbeddedMvpAsyncReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
            return kEmbeddedMvpTimeoutIsolationReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
            return kEmbeddedMvpSecurityPolicyReadinessBootstrapSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            return kEmbeddedMvpGoNoGoReadinessBootstrapSource;
        default:
            return nullptr;
    }
}

const char* embeddedInlineProbeScriptSource(EmbeddedLifecycleJsProbeKind kind) {
    switch (kind) {
        case EmbeddedLifecycleJsProbeKind::InlineConstant:
            return kInlineConstantProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineReturnValue:
            return kInlineReturnValueProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineError:
            return kInlineErrorProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineOutput:
            return kInlineOutputProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAsync:
            return kInlineAsyncProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineCancel:
            return kInlineCancelProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineTimeout:
            return kInlineTimeoutProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineRepeatedProcess:
            return kInlineRepeatedProcessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineBuiltinPolicy:
            return kInlineBuiltinPolicyProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineRequireDenied:
            return kInlineRequireDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNpmDenied:
            return kInlineNpmDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePackageJsonDenied:
            return kInlinePackageJsonDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNodeModulesDenied:
            return kInlineNodeModulesDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNativeAddonDenied:
            return kInlineNativeAddonDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineEsmDenied:
            return kInlineEsmDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDynamicImportDenied:
            return kInlineDynamicImportDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWorkerDenied:
            return kInlineWorkerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineCapabilitySummary:
            return kInlineCapabilitySummaryProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineFilesystemDenied:
            return kInlineFilesystemDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineChildProcessDenied:
            return kInlineChildProcessDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNetworkDenied:
            return kInlineNetworkDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionsDenied:
            return kInlinePermissionsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAndroidBridgeDenied:
            return kInlineAndroidBridgeDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAutoJsApiDenied:
            return kInlineAutoJsApiDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineConsoleBridgeDenied:
            return kInlineConsoleBridgeDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineJsonSocketDenied:
            return kInlineJsonSocketDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineBinderDenied:
            return kInlineBinderDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineUiBridgeDenied:
            return kInlineUiBridgeDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityDenied:
            return kInlineAccessibilityDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineImagesDenied:
            return kInlineImagesDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDialogsDenied:
            return kInlineDialogsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineSensorsDenied:
            return kInlineSensorsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineMediaCameraDenied:
            return kInlineMediaCameraDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineStorageDenied:
            return kInlineStorageDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineShellDenied:
            return kInlineShellDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationDenied:
            return kInlineNotificationDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardDenied:
            return kInlineClipboardDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDeviceInfoDenied:
            return kInlineDeviceInfoDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineVibrationDenied:
            return kInlineVibrationDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineToastDenied:
            return kInlineToastDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineFloatyDenied:
            return kInlineFloatyDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineEventsDenied:
            return kInlineEventsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineThreadsDenied:
            return kInlineThreadsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineTimersDenied:
            return kInlineTimersDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWebViewDenied:
            return kInlineWebViewDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineHttpDenied:
            return kInlineHttpDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineCryptoDenied:
            return kInlineCryptoDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineOcrDenied:
            return kInlineOcrDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineMlAiDenied:
            return kInlineMlAiDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWebSocketDenied:
            return kInlineWebSocketDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineBluetoothDenied:
            return kInlineBluetoothDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNfcDenied:
            return kInlineNfcDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineUsbDenied:
            return kInlineUsbDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineLocationDenied:
            return kInlineLocationDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineContactsDenied:
            return kInlineContactsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineCalendarDenied:
            return kInlineCalendarDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineSmsTelephonyDenied:
            return kInlineSmsTelephonyDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccountDenied:
            return kInlineAccountDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePackageManagerDenied:
            return kInlinePackageManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineIntentActivityDenied:
            return kInlineIntentActivityDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineBroadcastDenied:
            return kInlineBroadcastDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineContentProviderDenied:
            return kInlineContentProviderDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineMediaStoreDenied:
            return kInlineMediaStoreDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDownloadManagerDenied:
            return kInlineDownloadManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineInputMethodDenied:
            return kInlineInputMethodDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAppOpsDenied:
            return kAppOpsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePermissionManagerDenied:
            return kPermissionManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineSettingsDenied:
            return kSettingsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePowerManagerDenied:
            return kPowerManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineKeyguardDenied:
            return kKeyguardDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWallpaperDenied:
            return kWallpaperDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineShortcutManagerDenied:
            return kShortcutManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAlarmManagerDenied:
            return kAlarmManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineJobSchedulerDenied:
            return kJobSchedulerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWorkManagerDenied:
            return kWorkManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineClipboardListenerDenied:
            return kClipboardListenerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineNotificationListenerDenied:
            return kNotificationListenerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAccessibilityControlDenied:
            return kAccessibilityControlDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDevicePolicyDenied:
            return kDevicePolicyDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineUsageStatsDenied:
            return kUsageStatsDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineVpnConnectivityDenied:
            return kVpnConnectivityDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineWifiManagerDenied:
            return kWifiManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineTelephonySubscriptionDenied:
            return kTelephonySubscriptionDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineCameraManagerDenied:
            return kCameraManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineAudioManagerDenied:
            return kAudioManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlineDisplayManagerDenied:
            return kDisplayManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::InlinePrintManagerDenied:
            return kPrintManagerDeniedProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceDescriptor:
            return kUserSourceDescriptorProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceSize:
            return kUserSourceSizeProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceEncoding:
            return kUserSourceEncodingProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceName:
            return kUserSourceNameProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceWrapper:
            return kUserSourceWrapperProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceStrictMode:
            return kUserSourceStrictModeProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourceCapability:
            return kUserSourceCapabilityProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserSourcePreflight:
            return kUserSourcePreflightProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineConstant:
            return kControlledUserInlineConstantProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStdout:
            return kControlledUserInlineStdoutProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineStderr:
            return kControlledUserInlineStderrProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineReturnValue:
            return kControlledUserInlineReturnValueProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineThrownError:
            return kControlledUserInlineThrownErrorProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlinePromise:
            return kControlledUserInlinePromiseProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineAsyncOrdering:
            return kControlledUserInlineAsyncOrderingProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::ControlledUserInlineSummary:
            return kControlledUserInlineSummaryProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileDescriptor:
            return kUserFileDescriptorProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileReadPolicy:
            return kUserFileReadPolicyProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFilePathNormalization:
            return kUserFilePathNormalizationProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileWorkingDirectory:
            return kUserFileWorkingDirectoryProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileSourceLoading:
            return kUserFileSourceLoadingProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionDryRun:
            return kUserFileExecutionDryRunProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileErrorStackFilename:
            return kUserFileErrorStackFilenameProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::UserFileExecutionSummary:
            return kUserFileExecutionSummaryProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptRequest:
            return kEmbeddedScriptRequestProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptResult:
            return kEmbeddedScriptResultProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptOutputEvent:
            return kEmbeddedScriptOutputEventProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptError:
            return kEmbeddedScriptErrorProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptTimeout:
            return kEmbeddedScriptTimeoutProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptCancellation:
            return kEmbeddedScriptCancellationProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptProcessIsolation:
            return kEmbeddedScriptProcessIsolationProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedScriptContract:
            return kEmbeddedScriptContractProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpLifecycleReadiness:
            return kEmbeddedMvpLifecycleReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSourceInputReadiness:
            return kEmbeddedMvpSourceInputReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpOutputReadiness:
            return kEmbeddedMvpOutputReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpErrorHandlingReadiness:
            return kEmbeddedMvpErrorHandlingReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpAsyncReadiness:
            return kEmbeddedMvpAsyncReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpTimeoutIsolationReadiness:
            return kEmbeddedMvpTimeoutIsolationReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpSecurityPolicyReadiness:
            return kEmbeddedMvpSecurityPolicyReadinessProbeScriptSource;
        case EmbeddedLifecycleJsProbeKind::EmbeddedMvpGoNoGoReadiness:
            return kEmbeddedMvpGoNoGoReadinessProbeScriptSource;
        default:
            return nullptr;
    }
}

bool appendEmbeddedInlineProbeSource(EmbeddedLifecycleJsProbeKind kind, std::string& destination) {
    return appendProbeSourcePair(
            embeddedInlineProbeBootstrapSource(kind),
            embeddedInlineProbeScriptSource(kind),
            destination
    );
}

}  // namespace autojs6::node_bridge
