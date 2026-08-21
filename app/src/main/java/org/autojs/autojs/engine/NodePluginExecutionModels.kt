package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider

/** Host-side request model for the external Node.js runtime plugin. */
data class NodePluginScriptRequest(
    val source: String,
    val sourceName: String,
    val workingDirectory: String? = null,
    val sandboxRoot: String? = null,
    val executionId: String? = null,
    val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val env: Map<String, String> = emptyMap(),
    val moduleSources: Map<String, String> = emptyMap(),
    val runtimeModuleSources: Map<String, String> = emptyMap(),
    val esmEnabled: Boolean = BuildConfig.NODEJS_ESM_ENABLED,
    val dynamicImportEnabled: Boolean = BuildConfig.NODEJS_DYNAMIC_IMPORT_ENABLED,
    val rawNodeNetworkModulesEnabled: Boolean = BuildConfig.NODEJS_RAW_NETWORK_MODULES_ENABLED,
    val workerThreadsEnabled: Boolean = BuildConfig.NODEJS_WORKER_THREADS_ENABLED,
    val childProcessEnabled: Boolean = BuildConfig.NODEJS_CHILD_PROCESS_ENABLED,
    val javaInteropEnabled: Boolean = BuildConfig.NODEJS_JAVA_INTEROP_ENABLED,
    val spawnDepth: Int = 0,
    val runtimeAdapter: String? = null,
    val moduleSourceProvider: INodeJsModuleSourceProvider? = null,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 300_000L
    }
}

/** Host-side terminal result returned by the external Node.js runtime plugin. */
data class NodePluginScriptResult(
    val succeeded: Boolean,
    val exitCode: Int?,
    val resultText: String?,
    val stdout: String,
    val stderr: String,
    val errorName: String?,
    val errorMessage: String?,
    val errorStack: String?,
    val elapsedMs: Long,
    val processName: String,
    val pid: Int,
    val sourceName: String,
    val nativeValues: Map<String, String>,
    val errorCode: String? = null,
    val timedOut: Boolean = false,
    val timeoutMs: Long? = null,
    val workingDirectory: String? = null,
)
