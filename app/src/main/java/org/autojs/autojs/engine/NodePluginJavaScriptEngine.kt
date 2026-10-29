package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.looper.LooperHelper
import org.autojs.autojs.core.plugin.typescript.CompiledTypeScriptSource
import org.autojs.autojs.core.plugin.typescript.TypeScriptExecutionCompiler
import org.autojs.autojs.core.plugin.typescript.TypeScriptNodeRuntimeProjectBuilder
import org.autojs.autojs.core.plugin.typescript.TypeScriptNodeStackTraceFormatter
import org.autojs.autojs.core.plugin.typescript.TypeScriptSourceMapBindings
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import java.io.File
import java.io.IOException
import java.util.UUID

/** JavaScript engine whose only Node.js execution backend is the external runtime plugin. */
class NodePluginJavaScriptEngine(
    private val androidContext: Context,
) : RhinoJavaScriptEngine(androidContext) {

    @Volatile
    private var activeExecutionId: String? = null

    @Volatile
    var lastPluginScriptResult: NodePluginScriptResult? = null
        private set

    @Volatile
    private var scriptThread: Thread? = null

    override fun doExecution(source: JavaScriptSource): Any? {
        val preparingAt = android.os.SystemClock.elapsedRealtime()
        val compiledSource = source as? CompiledTypeScriptSource
        val compiled = compiledSource?.executable?.takeIf { executable ->
            executable.targetProfile == TypeScriptCompilerContract.PROFILE_NODE
        }
        val executionId = newExecutionId()
        val baseRequest = buildRequest(source).copy(executionId = executionId)
        val onDemandTypeScriptProvider = compiled
            ?.takeIf { executable -> executable.isProjectCompilation }
            ?.let { executable ->
                NodeJsTypeScriptModuleSourceProvider(
                    context = androidContext,
                    rootDirectory = requireNotNull(executable.projectRoot),
                    entryFile = requireNotNull(
                        requireNotNull(compiledSource).originalSource as? JavaScriptFileSource,
                    ).file,
                    executionId = executionId,
                    timeoutMs = baseRequest.timeoutMs,
                    initialSourceCount = baseRequest.moduleSources.size,
                    initialSourceBytes = baseRequest.moduleSources.values.sumOf { moduleSource ->
                        moduleSource.toByteArray(Charsets.UTF_8).size.toLong()
                    },
                )
            }
        val request = if (onDemandTypeScriptProvider == null) {
            baseRequest
        } else {
            baseRequest.copy(
                moduleSourceProvider = onDemandTypeScriptProvider,
                moduleSourceProviderVersion = NodeJsRuntimeContract
                    .MODULE_SOURCE_PROVIDER_CONTRACT_VERSION,
            )
        }
        val sourceMapBindings = compiled?.let { executable ->
            val generatedNames = if (executable.isProjectCompilation) {
                val root = requireNotNull(executable.projectRoot).normalizedCanonicalFile()
                executable.modules.associate { module ->
                    module.javaScriptPath to File(
                        root,
                        executable.runtimeRelativeGeneratedPath(module),
                    ).absoluteFile.normalize().path
                }
            } else {
                mapOf(executable.entryModulePath to request.sourceName)
            }
            executable.sourceMapBindings(
                generatedSourceNames = generatedNames,
                entryGeneratedLineOffset = request.sourceLineOffset,
            )
        }
        compiled?.let { executable ->
            setTag(
                TypeScriptExecutionCompiler.SOURCE_MAP_ENGINE_TAG,
                requireNotNull(sourceMapBindings),
            )
            setTag(TypeScriptExecutionCompiler.GENERATED_SOURCE_ENGINE_TAG, request.sourceName)
            executable.diagnostics.forEach { diagnostic ->
                runtime.console.warn(
                    TypeScriptExecutionCompiler.formatDiagnostic(
                        executable.sourceName,
                        diagnostic,
                    ),
                )
            }
        }
        activeExecutionId = executionId
        val input = NodeRuntimeConsoleInput(runtime.console) { message ->
            NodeJsRuntimePluginBridge.postMessage(executionId, message)
        }
        val rawResult = try {
            NodeJsRuntimePluginBridge.runScript(
                context = androidContext,
                request = request,
                startupTimings = (getTag(org.autojs.autojs.execution.ExecutionConfig.tag) as? org.autojs.autojs.execution.ExecutionConfig)?.let { config ->
                    mapOf(
                        "launchStartedAtMs" to config.nodeLaunchStartedAtMs,
                        "timing.host.typescript_prepare.ms" to config.nodeTypeScriptPrepareMs,
                        "timing.host.engine_prepare.ms" to (preparingAt - config.nodeLaunchStartedAtMs - config.nodeTypeScriptPrepareMs).coerceAtLeast(0L),
                        "timing.host.request_prepare.ms" to (android.os.SystemClock.elapsedRealtime() - preparingAt),
                    )
                }.orEmpty(),
                stdinListener = input::onState,
                console = runtime.console,
                agentExecutionState = (getTag(org.autojs.autojs.execution.ExecutionConfig.tag) as? org.autojs.autojs.execution.ExecutionConfig)?.agentExecutionState,
                outputListener = { type, text ->
                    printScriptOutput(
                        if (type == NodeJsRuntimeContract.EVENT_STDERR) {
                            Log.ERROR
                        } else {
                            Log.DEBUG
                        },
                        text,
                    )
                },
                resources = runtime.resourceScope,
            )
        } finally {
            input.close()
            if (activeExecutionId == executionId) {
                activeExecutionId = null
            }
        }
        val effectiveSourceMapBindings = sourceMapBindings?.let { initial ->
            val dynamic = onDemandTypeScriptProvider?.sourceMapBindings().orEmpty()
            if (dynamic.isEmpty()) initial else TypeScriptSourceMapBindings.of(
                initial.bindings + dynamic,
            ).also { combined ->
                setTag(TypeScriptExecutionCompiler.SOURCE_MAP_ENGINE_TAG, combined)
            }
        }
        val result = if (compiled == null || rawResult.errorStack.isNullOrBlank()) {
            rawResult
        } else {
            val mappedStack = TypeScriptNodeStackTraceFormatter.format(
                bindings = requireNotNull(effectiveSourceMapBindings),
                stack = rawResult.errorStack,
            )
            if (mappedStack == null) rawResult else rawResult.copy(errorStack = mappedStack)
        }
        lastPluginScriptResult = result
        return finishExecution(request, result)
    }

    override fun forceStop() {
        activeExecutionId?.let { executionId ->
            NodeJsRuntimePluginBridge.cancelScript(androidContext, executionId)
        }
        super.forceStop()
    }

    override fun init() {
        scriptThread = Thread.currentThread()
        super.init()
    }

    @Synchronized
    override fun destroy() {
        super.destroy()
        // The runtime prologue prepared a looper on the script thread, and nothing loops it after the plugin returns:
        // quit it and recycle what is still queued, which would otherwise keep the thread reachable (docs/memory-phase2/ROADMAP.md C34).
        // zh-CN: 运行时序幕在脚本线程上准备了 Looper, 插件返回后不会有人再循环它: 退出它并回收队列中剩余的消息,
        // 否则这些消息会让该线程保持可达 (docs/memory-phase2/ROADMAP.md C34).
        scriptThread?.let(LooperHelper::discardForThread)
        scriptThread = null
    }

    override fun toString(): String {
        return "NodePluginJavaScriptEngine@" + Integer.toHexString(hashCode()) +
                "{id=" + id + ",source='" + getTag(TAG_SOURCE) + "',cwd='" + cwd() + "'}"
    }

    private fun buildRequest(source: JavaScriptSource): NodePluginScriptRequest {
        val compiled = source as? CompiledTypeScriptSource
        if (
            compiled?.executable?.targetProfile == TypeScriptCompilerContract.PROFILE_NODE
        ) {
            val original = compiled.originalSource
            val executable = compiled.executable
            if (executable.isProjectCompilation) {
                val projectRoot = requireNotNull(executable.projectRoot).normalizedCanonicalFile()
                val fileSource = requireNotNull(original as? JavaScriptFileSource)
                val sourceFile = fileSource.file.normalizedCanonicalFile()
                require(sourceFile.path.startsWith(projectRoot.path + File.separator)) {
                    "Compiled Node TypeScript entry escapes its project root."
                }
                val runtimeProject = TypeScriptNodeRuntimeProjectBuilder.build(executable)
                val launchSource = fileSource as? NodeProjectLaunchSource
                val projectRequest = if (launchSource == null) {
                    NodeProjectRunner.buildResolvedEntryRequest(
                        NodeProjectRunner.Request(
                            projectRoot = projectRoot,
                            entryFile = sourceFile,
                            workingDirectory = projectRoot,
                            entrySource = compiled.prefixedEntrySource(executable.javaScript),
                            moduleSources = runtimeProject.moduleSources,
                        ),
                    )
                } else {
                    val plan = NodeProjectRunner.resolveProjectLaunchPlanWithDescriptor(
                        NodeProjectRunner.buildProjectRequest(launchSource),
                    )
                    require(plan.entryFile.normalizedCanonicalFile().path == sourceFile.path) {
                        "Compiled Node TypeScript launch resolved a different project entry."
                    }
                    NodeProjectRunner.buildResolvedProjectLaunchRequest(
                        plan.copy(
                            entrySource = compiled.prefixedEntrySource(executable.javaScript),
                            moduleSources = plan.moduleSources + runtimeProject.moduleSources,
                        ),
                    )
                }
                // A project budget leaves room for the compilation; no budget (0) stays without a limit.
                val timeoutMs = if (launchSource?.nodeProjectTimeoutMs == null && projectRequest.timeoutMs > 0L) {
                    maxOf(projectRequest.timeoutMs, DEFAULT_TYPESCRIPT_PROJECT_TIMEOUT_MS)
                } else {
                    projectRequest.timeoutMs
                }
                return projectRequest.copy(
                    sourceLineOffset = Math.addExact(
                        projectRequest.sourceLineOffset,
                        compiled.entryGeneratedLineOffset,
                    ),
                    sourceName = runtimeProject.entryGeneratedSourceName,
                    timeoutMs = timeoutMs,
                    typeScriptPrecompiledSnapshot = true,
                    typeScriptPrecompiledSourceNames = runtimeProject.precompiledSourceNames,
                    workspaceModuleSources = runtimeProject.workspaceModuleSources,
                    spawnDepth = currentSpawnDepth(),
                )
            }
            val sourceFile = (original as? JavaScriptFileSource)
                ?.file
                ?.normalizedCanonicalFile()
            val workingDirectory = sourceFile?.parentFile?.normalizedCanonicalFile()
                ?: cwd()?.takeIf(String::isNotBlank)?.let(::File)?.normalizedCanonicalFile()
                ?: androidContext.cacheDir.normalizedCanonicalFile()
            return NodePluginScriptRequest(
                source = normalizeSource(
                    compiled.prefixedEntrySource(compiled.executable.javaScript),
                ),
                sourceName = compiled.executable.generatedSourceName,
                sourceLineOffset = compiled.entryGeneratedLineOffset,
                workingDirectory = workingDirectory.path,
                sandboxRoot = workingDirectory.path,
                typeScriptPrecompiledSnapshot = true,
                spawnDepth = currentSpawnDepth(),
            )
        }
        val launchSource = source as? NodeProjectLaunchSource
        if (launchSource != null) {
            val plan = NodeProjectRunner
                .resolveProjectLaunchPlanWithDescriptor(
                    NodeProjectRunner.buildProjectRequest(launchSource),
                )
                .let { resolved ->
                    resolved.copy(entrySource = readSource(resolved.entryFile))
                }
            return NodeProjectRunner
                .buildResolvedProjectLaunchRequest(plan)
                .copy(spawnDepth = currentSpawnDepth())
        }

        val fileSource = source as? JavaScriptFileSource
        val sourceFile = fileSource?.file?.normalizedCanonicalFile()
        val projectRoot = sourceFile?.let { fileSource.nodeProjectRoot?.normalizedCanonicalFile() }
        val workingDirectory = projectRoot
            ?: sourceFile?.parentFile?.normalizedCanonicalFile()
            ?: cwd()?.takeIf(String::isNotBlank)?.let(::File)?.normalizedCanonicalFile()
            ?: androidContext.cacheDir.normalizedCanonicalFile()
        return NodePluginScriptRequest(
            source = normalizeSource(sourceFile?.let(::readSource) ?: source.script),
            sourceName = sourceFile?.path
                ?: source.overriddenFullPath?.takeIf(String::isNotBlank)
                ?: source.fullPath.takeIf(String::isNotBlank)
                ?: DEFAULT_SOURCE_NAME,
            workingDirectory = workingDirectory.path,
            sandboxRoot = (projectRoot ?: workingDirectory).path,
            spawnDepth = currentSpawnDepth(),
        )
    }

    private fun finishExecution(
        request: NodePluginScriptRequest,
        result: NodePluginScriptResult,
    ): Any? {
        val streamedOutput = result.nativeValues[
            "embedded_script.runtime_plugin.streamed_output_events"
        ]?.toIntOrNull()?.let { it > 0 } == true
        if (!streamedOutput) {
            printScriptOutput(Log.DEBUG, result.stdout)
            printScriptOutput(Log.ERROR, result.stderr)
        }
        if (result.succeeded) {
            return result.exitCode ?: 0
        }
        result.errorStack?.takeIf(String::isNotBlank)?.let { printScriptOutput(Log.ERROR, it) }
        val message = result.errorMessage ?: result.errorName ?: "Node.js runtime plugin execution failed."
        val errorCode = result.errorCode?.let { " errorCode=$it" }.orEmpty()
        Log.e(
            TAG,
            "Node plugin execution failed sourceName=${request.sourceName} " +
                    "exitCode=${result.exitCode ?: 1}$errorCode message=$message",
        )
        throw NodePluginScriptExecutionException(
            "Node.js runtime plugin execution failed with exitCode=${result.exitCode ?: 1}$errorCode: $message",
        )
    }

    private fun printScriptOutput(level: Int, text: String) {
        if (text.isEmpty()) return
        val console = runCatching { runtime.console }.getOrNull()
        nodePluginConsoleOutputLines(text).forEach { line ->
            if (line.isEmpty()) {
                printScriptOutputChunk(level, line, console)
            } else {
                var start = 0
                while (start < line.length) {
                    val end = outputChunkEnd(line, start)
                    printScriptOutputChunk(level, line.substring(start, end), console)
                    start = end
                }
            }
        }
    }

    private fun printScriptOutputChunk(level: Int, chunk: String, console: ConsoleImpl?) {
        if (console == null) {
            Log.println(level, TAG, chunk)
        } else {
            console.println(level, chunk)
        }
    }

    private fun outputChunkEnd(line: String, start: Int): Int {
        val maxEnd = (start + SCRIPT_OUTPUT_LOG_LINE_MAX_CHARS).coerceAtMost(line.length)
        if (maxEnd >= line.length) return line.length
        for (index in maxEnd - 1 downTo start + 1) {
            if (line[index].isWhitespace()) return index + 1
        }
        return maxEnd
    }

    private fun readSource(file: File): String {
        val maximumStoredBytes = NodeRuntimeCapabilities.RUNTIME_SCRIPT_SOURCE_MAX_BYTES.toLong() +
                EncryptedScriptFileHeader.BLOCK_SIZE + ENCRYPTED_BLOCK_BYTES
        if (!file.isFile || file.length() > maximumStoredBytes) {
            throw IOException("Node.js plugin source is missing or exceeds its size limit: ${file.path}")
        }
        val bytes = file.readBytes()
        val cleartext = if (
            bytes.size >= EncryptedScriptFileHeader.BLOCK_SIZE &&
            EncryptedScriptFileHeader.isValidFile(bytes)
        ) {
            ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE)
        } else {
            bytes
        }
        if (cleartext.size > NodeRuntimeCapabilities.RUNTIME_SCRIPT_SOURCE_MAX_BYTES) {
            throw IOException("Node.js plugin source exceeds its cleartext size limit: ${file.path}")
        }
        return normalizeSource(cleartext.toString(Charsets.UTF_8))
    }

    private fun normalizeSource(source: String): String = when {
        source.startsWith(BOM_SHEBANG_PREFIX) -> BOM + SHEBANG_COMMENT_PREFIX + source.substring(BOM_SHEBANG_PREFIX.length)
        source.startsWith(SHEBANG_PREFIX) -> SHEBANG_COMMENT_PREFIX + source.substring(SHEBANG_PREFIX.length)
        else -> source
    }

    private fun currentSpawnDepth(): Int {
        val config = getTag(org.autojs.autojs.execution.ExecutionConfig.tag)
            as? org.autojs.autojs.execution.ExecutionConfig
            ?: return 0
        return (config.getArgument(NodeBridgeEngineInfo.SPAWN_DEPTH_ARGUMENT) as? Number)
            ?.toInt()
            ?.coerceIn(0, NodeBridgeEngineInfo.MAX_SPAWN_DEPTH)
            ?: 0
    }

    private fun newExecutionId(): String {
        val engineId = id.takeIf { it != org.autojs.autojs.execution.ScriptExecution.NO_ID }
            ?.toString()
            ?: "no-id"
        return "node-plugin-engine-$engineId-${UUID.randomUUID()}"
    }

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile }

    private companion object {
        private const val TAG = "NodePluginJavaScriptEngine"
        private const val DEFAULT_SOURCE_NAME = "<node-plugin-user-script.js>"
        private const val DEFAULT_TYPESCRIPT_PROJECT_TIMEOUT_MS = 30_000L
        private const val SCRIPT_OUTPUT_LOG_LINE_MAX_CHARS = 1800
        private const val ENCRYPTED_BLOCK_BYTES = 16L
        private const val BOM = "\uFEFF"
        private const val SHEBANG_PREFIX = "#!"
        private const val BOM_SHEBANG_PREFIX = "$BOM$SHEBANG_PREFIX"
        private const val SHEBANG_COMMENT_PREFIX = "//!"
    }
}

class NodePluginScriptExecutionException(message: String) : IllegalStateException(message)

internal fun nodePluginConsoleOutputLines(text: String): Sequence<String> {
    if (text.isEmpty()) return emptySequence()
    val output = when {
        text.endsWith("\r\n") -> text.dropLast(2)
        text.endsWith('\n') || text.endsWith('\r') -> text.dropLast(1)
        else -> text
    }
    if (output.isEmpty()) return sequenceOf("")
    return sequence {
        var start = 0
        var index = 0
        while (index < output.length) {
            val character = output[index]
            if (character == '\n' || character == '\r') {
                yield(output.substring(start, index))
                index += if (
                    character == '\r' && index + 1 < output.length && output[index + 1] == '\n'
                ) 2 else 1
                start = index
            } else {
                index += 1
            }
        }
        yield(output.substring(start))
    }
}
