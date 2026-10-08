package org.autojs.autojs.engine

import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider

/** Host-side request model for the external Node.js runtime plugin. */
data class NodePluginScriptRequest(
    val source: String,
    val sourceName: String,
    /** Host-only prologue lines prepended before [source]'s generated TypeScript entry. */
    val sourceLineOffset: Int = 0,
    val workingDirectory: String? = null,
    val sandboxRoot: String? = null,
    val executionId: String? = null,
    val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val env: Map<String, String> = emptyMap(),
    val moduleSources: Map<String, String> = emptyMap(),
    /** Host-only immutable files published through the private workspace, never through Binder. */
    val workspaceModuleSources: Map<String, String> = emptyMap(),
    val runtimeModuleSources: Map<String, String> = emptyMap(),
    val typeScriptPrecompiledSnapshot: Boolean = false,
    val typeScriptPrecompiledSourceNames: Set<String> = emptySet(),
    /** Optional compatibility override; absent values are owned and defaulted by the plugin. */
    val esmEnabled: Boolean? = null,
    val dynamicImportEnabled: Boolean? = null,
    val rawNodeNetworkModulesEnabled: Boolean? = null,
    /** Null follows the host developer preference; true/false explicitly overrides it. */
    val inspectorEnabled: Boolean? = null,
    val workerThreadsEnabled: Boolean? = null,
    val childProcessEnabled: Boolean? = null,
    val javaInteropEnabled: Boolean? = null,
    val spawnDepth: Int = 0,
    val moduleSourceProvider: INodeJsModuleSourceProvider? = null,
    val moduleSourceProviderVersion: Int? = null,
    /** Optional process idle exit after this request completes; 0 keeps it resident. */
    val idleExitMs: Long = 0L,
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
