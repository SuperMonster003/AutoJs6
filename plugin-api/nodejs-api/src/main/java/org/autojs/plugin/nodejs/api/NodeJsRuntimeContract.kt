package org.autojs.plugin.nodejs.api

object NodeJsRuntimeContract {

    const val CONTRACT_VERSION = 1
    const val HOST_CAPABILITY_BROKER_CONTRACT_VERSION = 1

    const val KEY_CONTRACT_VERSION = "contractVersion"
    const val KEY_RUNTIME_SLOT = "runtimeSlot"
    const val KEY_NODE_VERSION = "nodeVersion"
    const val KEY_NATIVE_LIBRARY_NAME = "nativeLibraryName"
    const val KEY_BRIDGE_LIBRARY_NAME = "bridgeLibraryName"
    const val KEY_CAPABILITIES = "capabilities"

    const val KEY_EXECUTION_ID = "executionId"
    const val KEY_SOURCE = "source"
    const val KEY_SOURCE_NAME = "sourceName"
    const val KEY_WORKING_DIRECTORY = "workingDirectory"
    const val KEY_SANDBOX_ROOT = "sandboxRoot"
    const val KEY_TIMEOUT_MS = "timeoutMs"
    const val KEY_EXECUTION_MODE = "executionMode"
    const val KEY_RUNTIME_ADAPTER = "runtimeAdapter"

    const val KEY_ENV_NAMES = "envNames"
    const val KEY_ENV_VALUES = "envValues"
    const val KEY_MODULE_SOURCE_NAMES = "moduleSourceNames"
    const val KEY_MODULE_SOURCES = "moduleSources"
    const val KEY_RUNTIME_MODULE_SOURCE_NAMES = "runtimeModuleSourceNames"
    const val KEY_RUNTIME_MODULE_SOURCES = "runtimeModuleSources"

    const val KEY_ESM_EXPERIMENTAL_ENABLED = "esmExperimentalEnabled"
    const val KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED = "dynamicImportExperimentalEnabled"
    const val KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED = "rawNodeNetworkModulesExperimentalEnabled"
    const val KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED = "workerThreadsExperimentalEnabled"
    const val KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED = "childProcessExperimentalEnabled"
    const val KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED = "javaInteropExperimentalEnabled"

    const val KEY_SUCCEEDED = "succeeded"
    const val KEY_EXIT_CODE = "exitCode"
    const val KEY_RESULT_TEXT = "resultText"
    const val KEY_STDOUT = "stdout"
    const val KEY_STDERR = "stderr"
    const val KEY_ERROR_NAME = "errorName"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_ERROR_STACK = "errorStack"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_ELAPSED_MS = "elapsedMs"
    const val KEY_PROCESS_NAME = "processName"
    const val KEY_PID = "pid"
    const val KEY_TIMED_OUT = "timedOut"
    const val KEY_NATIVE_PAYLOAD = "nativePayload"
    const val KEY_HOST_CAPABILITY_BROKER = "hostCapabilityBroker"
    const val KEY_HOST_CAPABILITY_BROKER_ID = "hostCapabilityBrokerId"
    const val KEY_HOST_CAPABILITY_BROKER_VERSION = "hostCapabilityBrokerVersion"
    const val KEY_HOST_CAPABILITY_MODULES = "hostCapabilityModules"
    const val KEY_BRIDGE_ENGINE_INFO = "bridgeEngineInfo"
    const val KEY_BRIDGE_REQUEST_JSON = "bridgeRequestJson"
    const val KEY_BRIDGE_RESPONSE_JSON = "bridgeResponseJson"
    const val KEY_BRIDGE_RESPONSE_OK = "bridgeResponseOk"
    const val KEY_BRIDGE_ERROR_MESSAGE = "bridgeErrorMessage"

    const val KEY_EVENT_TYPE = "eventType"
    const val KEY_EVENT_LEVEL = "level"
    const val KEY_EVENT_TEXT = "text"

    const val EVENT_STARTED = "started"
    const val EVENT_STDOUT = "stdout"
    const val EVENT_STDERR = "stderr"
    const val EVENT_FINISHED = "finished"

    const val CAPABILITY_SYNC_SCRIPT_EXECUTION = "syncScriptExecution"
    const val CAPABILITY_BUNDLE_TRANSPORT = "bundleTransport"
    const val CAPABILITY_NATIVE_EMBEDDED_RUNTIME = "nativeEmbeddedRuntime"
    const val CAPABILITY_HOST_CAPABILITY_BROKER = "hostCapabilityBroker"
}
