package org.autojs.autojs.engine

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.json.JSONArray
import org.json.JSONObject
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs6.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID

internal fun nodeEmbeddedConsoleOutputLines(text: String): Sequence<String> {
    if (text.isEmpty()) {
        return emptySequence()
    }
    val output = text.stripSingleTrailingLineTerminator()
    if (output.isEmpty()) {
        return sequenceOf("")
    }
    return sequence {
        var start = 0
        var index = 0
        while (index < output.length) {
            val ch = output[index]
            if (ch == '\n' || ch == '\r') {
                yield(output.substring(start, index))
                index += if (ch == '\r' && index + 1 < output.length && output[index + 1] == '\n') 2 else 1
                start = index
                continue
            }
            index += 1
        }
        yield(output.substring(start))
    }
}

private fun String.stripSingleTrailingLineTerminator(): String {
    return when {
        endsWith("\r\n") -> dropLast(2)
        endsWith('\n') || endsWith('\r') -> dropLast(1)
        else -> this
    }
}

class NativeNodeEmbeddedJavaScriptEngine(private val androidContext: Context) : RhinoJavaScriptEngine(androidContext) {

    @Volatile
    var lastCommonJsPreloadDiagnostics: CommonJsPreloadDiagnostics = CommonJsPreloadDiagnostics.empty()
        private set

    @Volatile
    var lastRequestPreparationDiagnostics: RequestPreparationDiagnostics = RequestPreparationDiagnostics.empty()
        private set

    @Volatile
    var lastNodeProjectRunnerDescriptor: NativeNodeEmbeddedProjectRunner.Descriptor? = null
        private set

    @Volatile
    var lastEmbeddedScriptResultNativeValues: Map<String, String> = emptyMap()
        private set

    @Volatile
    private var activeEmbeddedExecutionId: String? = null

    @Volatile
    internal var encryptedRuntimeScanBeforeFinalRootIdentityCheckForTest: (() -> Unit)? = null

    override fun doExecution(source: JavaScriptSource): Any? {
        lastNodeProjectRunnerDescriptor = null
        lastEmbeddedScriptResultNativeValues = emptyMap()
        lastRequestPreparationDiagnostics = RequestPreparationDiagnostics.empty()
        failFastIfEmbeddedRuntimeUnavailable(source)?.let { return it }
        tryRunNodeProjectLaunch(source)?.let { return it }
        tryRunNodeProjectFile(source)?.let { return it }

        val executionId = newEmbeddedExecutionId()
        val request = buildEmbeddedScriptRequest(source)
            .copy(executionId = executionId)
        val result = withActiveEmbeddedExecutionId(executionId) {
            runEmbeddedScriptRequest(request)
        }
        return finishEmbeddedScriptExecution(request, result)
    }

    private fun failFastIfEmbeddedRuntimeUnavailable(source: JavaScriptSource): Any? {
        if (
            BuildConfig.NODEJS_RELEASE_FLAVOR != NODE_PLUGIN_RELEASE_FLAVOR ||
            NodeJsRuntimePluginBridge.isRequested()
        ) {
            return null
        }
        val request = buildRuntimePreflightRequest(source)
        val failure = NativeNodeEmbeddedRuntimeBridge.validateEmbeddedScriptRequestBeforeServiceStart(
            request = request,
            context = androidContext,
        ) ?: return null
        return finishEmbeddedScriptExecution(request, failure)
    }

    override fun forceStop() {
        activeEmbeddedExecutionId?.let { executionId ->
            if (NodeJsRuntimePluginBridge.isRequested()) {
                NodeJsRuntimePluginBridge.cancelScript(androidContext, executionId)
            } else {
                NativeNodeEmbeddedScriptService.cancelRunningScript(androidContext, executionId)
            }
        }
        super.forceStop()
    }

    private inline fun <T> withActiveEmbeddedExecutionId(executionId: String, block: () -> T): T {
        activeEmbeddedExecutionId = executionId
        try {
            return block()
        } finally {
            if (activeEmbeddedExecutionId == executionId) {
                activeEmbeddedExecutionId = null
            }
        }
    }

    fun prepareNodeProjectLaunchRequest(
        source: JavaScriptSource,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest? {
        val launchSource = source as? NodeProjectLaunchSource ?: return null
        val fileSource = source as? JavaScriptFileSource ?: return null
        val projectRoot = launchSource.nodeProjectRootFile.normalizedCanonicalFile()
        val sourceLaunchKind = launchSource.nodeProjectLaunchKind
        val baseRequest = NativeNodeEmbeddedProjectRunner.buildProjectRequest(launchSource, projectRoot)
        val basePlan = NativeNodeEmbeddedProjectRunner.resolveProjectLaunchPlanWithDescriptor(baseRequest)
        val preparation = prepareNodeProjectRun(fileSource, projectRoot, basePlan.entryFile)
        val plan = basePlan.copy(
            moduleSources = preparation.preload.moduleSources,
            runtimeModuleSources = preparation.runtimeModuleSources,
            entrySource = preparation.entrySource,
        )
        Log.i(
            TAG,
            "embedded node project launch request prepared for external service " +
                    "projectRoot=${preparation.workingDirectory} " +
                    "sourceName=${preparation.sourceName} " +
                    "sourceLaunchKind=${sourceLaunchKind.descriptorValue} " +
                    "launchKind=${plan.kind} " +
                    "executionMode=${plan.executionMode} " +
                    "moduleSources=${preparation.preload.moduleSources.size} " +
                    "runtimeModuleSources=${preparation.runtimeModuleSources.size} " +
                    "runtimeFirst=${preparation.runtimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        lastCommonJsPreloadDiagnostics = preparation.preload.diagnostics
        lastNodeProjectRunnerDescriptor = NativeNodeEmbeddedProjectRunner.describe(plan)
        return NativeNodeEmbeddedProjectRunner
            .buildResolvedProjectLaunchRequest(plan)
            .copy(spawnDepth = currentSpawnDepth())
    }

    private fun tryRunNodeProjectLaunch(source: JavaScriptSource): Any? {
        val launchSource = source as? NodeProjectLaunchSource ?: return null
        val fileSource = source as? JavaScriptFileSource ?: return null
        val projectRoot = launchSource.nodeProjectRootFile.normalizedCanonicalFile()
        val sourceLaunchKind = launchSource.nodeProjectLaunchKind
        val baseRequest = NativeNodeEmbeddedProjectRunner.buildProjectRequest(launchSource, projectRoot)
        val basePlan = NativeNodeEmbeddedProjectRunner.resolveProjectLaunchPlanWithDescriptor(baseRequest)
        val preparation = prepareNodeProjectRun(fileSource, projectRoot, basePlan.entryFile)
        val plan = basePlan.copy(
            moduleSources = preparation.preload.moduleSources,
            runtimeModuleSources = preparation.runtimeModuleSources,
            entrySource = preparation.entrySource,
        )

        Log.i(
            TAG,
            "embedded node project launch request prepared projectRoot=${preparation.workingDirectory} " +
                    "sourceName=${preparation.sourceName} " +
                    "sourceLaunchKind=${sourceLaunchKind.descriptorValue} " +
                    "launchKind=${plan.kind} " +
                    "moduleSources=${preparation.preload.moduleSources.size} " +
                    "runtimeModuleSources=${preparation.runtimeModuleSources.size} " +
                    "runtimeFirst=${preparation.runtimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        lastCommonJsPreloadDiagnostics = preparation.preload.diagnostics
        val descriptor = NativeNodeEmbeddedProjectRunner.describe(plan)
        lastNodeProjectRunnerDescriptor = descriptor
        val executionId = newEmbeddedExecutionId()
        val result = withActiveEmbeddedExecutionId(executionId) {
            if (NodeJsRuntimePluginBridge.isRequested()) {
                runEmbeddedScriptRequest(
                    NativeNodeEmbeddedProjectRunner
                        .buildResolvedProjectLaunchRequest(plan)
                        .copy(executionId = executionId, spawnDepth = currentSpawnDepth()),
                    descriptorNativeValues = descriptor.toNativeValues(),
                )
            } else {
                NativeNodeEmbeddedProjectRunner.runResolvedProjectLaunchPlan(androidContext, plan, executionId)
            }
        }
        return finishEmbeddedScriptExecution(
            preparation.toBridgeRequest(plan.timeoutMs, currentSpawnDepth(), plan.executionMode)
                .copy(executionId = executionId),
            result,
        )
    }

    private fun runEmbeddedScriptRequest(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        descriptorNativeValues: Map<String, String> = emptyMap(),
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val result = NodeJsRuntimePluginBridge.runScriptWithEmbeddedFallback(androidContext, request)
        return if (descriptorNativeValues.isEmpty()) {
            result
        } else {
            result.copy(nativeValues = result.nativeValues + descriptorNativeValues)
        }
    }

    private fun tryRunNodeProjectFile(source: JavaScriptSource): Any? {
        val fileSource = source as? JavaScriptFileSource ?: return null
        val projectRoot = fileSource.nodeProjectRoot?.normalizedCanonicalFile() ?: return null
        val entryFile = fileSource.file.normalizedCanonicalFile()
        if (!entryFile.isFile || !entryFile.isInsideOrSame(projectRoot)) {
            return null
        }

        val preparation = prepareNodeProjectRun(fileSource, projectRoot, entryFile)
        Log.i(
            TAG,
            "embedded node project file request prepared projectRoot=${preparation.workingDirectory} " +
                    "sourceName=${preparation.sourceName} " +
                    "moduleSources=${preparation.preload.moduleSources.size} " +
                    "runtimeModuleSources=${preparation.runtimeModuleSources.size} " +
                    "runtimeFirst=${preparation.runtimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        lastCommonJsPreloadDiagnostics = preparation.preload.diagnostics

        val request = NativeNodeEmbeddedProjectRunner.Request(
            projectRoot = projectRoot,
            entryFile = entryFile,
            workingDirectory = projectRoot,
            moduleSources = preparation.preload.moduleSources,
            runtimeModuleSources = preparation.runtimeModuleSources,
            entrySource = preparation.entrySource,
        )
        val descriptor = NativeNodeEmbeddedProjectRunner.describeResolvedEntryRequest(request)
        lastNodeProjectRunnerDescriptor = descriptor
        val executionId = newEmbeddedExecutionId()
        val result = withActiveEmbeddedExecutionId(executionId) {
            if (NodeJsRuntimePluginBridge.isRequested()) {
                runEmbeddedScriptRequest(
                    NativeNodeEmbeddedProjectRunner
                        .buildResolvedEntryRequest(request)
                        .copy(executionId = executionId, spawnDepth = currentSpawnDepth()),
                    descriptorNativeValues = descriptor.toNativeValues(),
                )
            } else {
                NativeNodeEmbeddedProjectRunner.run(androidContext, request, executionId)
            }
        }
        return finishEmbeddedScriptExecution(
            preparation.toBridgeRequest(request.timeoutMs, currentSpawnDepth())
                .copy(executionId = executionId),
            result,
        )
    }

    private fun prepareNodeProjectRun(
        fileSource: JavaScriptFileSource,
        projectRoot: File,
        entryFile: File,
    ): NodeProjectRunPreparation {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val normalizedProjectRoot = projectRoot.normalizedCanonicalFile()
        val normalizedEntryFile = entryFile.normalizedCanonicalFile()
        val entrySource = normalizeEmbeddedSource(sourceText(normalizedEntryFile))
        val sourceName = normalizedEntryFile.normalizedAbsolutePath()
        val workingDirectory = normalizedProjectRoot.normalizedAbsolutePath()
        val rootLoaderAnalysis = analyzeCommonJsSourceLoaders(entrySource)
        val isEsmEntry = isEsmSourceName(sourceName, workingDirectory) ||
                rootLoaderAnalysis.sourceLevelEsmSyntaxCount > 0
        val preload = collectCommonJsModuleSources(
            source = entrySource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            isEsmEntry = isEsmEntry,
            rootLoaderAnalysis = rootLoaderAnalysis,
        )
        val encryptedScan = collectEncryptedRuntimeModuleSources(
            source = fileSource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            moduleSources = preload.moduleSources,
            sourceFileOverride = normalizedEntryFile,
            decision = encryptedRuntimeModuleScanDecision(isEsmEntry, preload.diagnostics),
        )
        val diagnostics = RequestPreparationDiagnostics(
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            isEsmEntry = isEsmEntry,
            elapsedMs = elapsedSince(startedAt),
            commonJs = preload.diagnostics,
            encryptedRuntimeModuleScan = encryptedScan.diagnostics,
        )
        lastRequestPreparationDiagnostics = diagnostics
        return NodeProjectRunPreparation(
            entrySource = entrySource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            preload = preload,
            runtimeModuleSources = encryptedScan.moduleSources,
            diagnostics = diagnostics,
        )
    }

    private fun finishEmbeddedScriptExecution(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): Any? {
        Log.i(
            TAG,
            "embedded script finished backend=${BuildConfig.NODEJS_RUNTIME_BACKEND} " +
                    "scriptExecution=${BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED} " +
                    "sourceName=${request.sourceName} exitCode=${result.exitCode ?: 1} " +
                    "succeeded=${result.succeeded} elapsed=${result.elapsedMs}ms",
        )
        val resultNativeValues = result.nativeValues + lastRequestPreparationDiagnostics.toNativeValues()
        lastEmbeddedScriptResultNativeValues = resultNativeValues
        if (!printStructuredConsoleOutput(resultNativeValues)) {
            printScriptOutput(Log.DEBUG, result.stdout)
            printScriptOutput(Log.ERROR, result.stderr)
        }
        if (!result.succeeded) {
            result.errorStack?.takeIf { it.isNotBlank() }?.let { printScriptOutput(Log.ERROR, it) }
            val failureEnvelope = buildFailureEnvelope(request, result)
            Log.e(TAG, failureEnvelope)
            printScriptOutput(Log.ERROR, failureEnvelope)
            val message = result.errorMessage
                ?: result.errorName
                ?: "Native Node embedded script failed."
            val errorCode = result.errorCode?.let { " errorCode=$it" }.orEmpty()
            val diagnosticHint = dynamicRequireDiagnosticHint(result)?.let { "\n$it" }.orEmpty()
            throw NativeNodeRuntimeUnavailableException(
                "Native Node embedded script failed with exitCode=${result.exitCode ?: 1}$errorCode: $message$diagnosticHint"
            )
        }
        return result.exitCode ?: 0
    }

    private fun buildFailureEnvelope(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): String {
        return buildString {
            append("Embedded Node failure envelope:")
            append(" errorCode=").append(result.errorCode.orEmpty())
            append(" errorName=").append(result.errorName.orEmpty())
            append(" exitCode=").append(result.exitCode ?: 1)
            append(" timedOut=").append(result.timedOut)
            result.timeoutMs?.let { append(" timeoutMs=").append(it) }
            append(" elapsedMs=").append(result.elapsedMs)
            append(" sourceName=").append(result.sourceName.ifBlank { request.sourceName })
            append(" workingDirectory=").append(result.workingDirectory ?: request.workingDirectory.orEmpty())
            append(" moduleSources=").append(request.moduleSources.size)
            append(" runtimeModuleSources=").append(request.runtimeModuleSources.size)
            result.nativeValues["embedded_script.sandbox_root"]?.let { append(" sandboxRoot=").append(it) }
            result.nativeValues["embedded_script.current_working_directory"]?.let {
                append(" currentWorkingDirectory=").append(it)
            }
            result.nativeValues["embedded_project_runner.kind"]?.let { append(" runnerKind=").append(it) }
            result.nativeValues["embedded_project_runner.project_root"]?.let { append(" runnerProjectRoot=").append(it) }
            result.nativeValues["embedded_project_runner.entry_file"]?.let { append(" runnerEntryFile=").append(it) }
            result.nativeValues["embedded_project_runner.package_dir"]?.takeIf { it.isNotBlank() }?.let {
                append(" runnerPackageDir=").append(it)
            }
            result.nativeValues["embedded_project_runner.bin_name"]?.takeIf { it.isNotBlank() }?.let {
                append(" runnerBinName=").append(it)
            }
            result.pendingTimers?.let { append(" pendingTimers=").append(it) }
            result.pendingTimerCount?.let { append(" pendingTimerCount=").append(it) }
            result.pendingScopedFsCallbacks?.let { append(" pendingScopedFsCallbacks=").append(it) }
            result.pendingCryptoCallbacks?.let { append(" pendingCryptoCallbacks=").append(it) }
            result.pendingZlibCallbacks?.let { append(" pendingZlibCallbacks=").append(it) }
            result.pendingZlibStreamCount?.let { append(" pendingZlibStreamCount=").append(it) }
            result.pendingAsyncCallbacks?.let { append(" pendingAsyncCallbacks=").append(it) }
            result.pendingCallbacks?.let { append(" pendingCallbacks=").append(it) }
        }
    }

    override fun toString(): String {
        return "NativeNodeEmbeddedJavaScriptEngine@" + Integer.toHexString(hashCode()) +
                "{id=" + id + ",source='" + getTag(TAG_SOURCE) + "',cwd='" + cwd() + "'}"
    }

    private fun printScriptOutput(level: Int, text: String) {
        if (text.isEmpty()) {
            return
        }
        val console = runCatching { runtime.console }.getOrNull()
        nodeEmbeddedConsoleOutputLines(text).forEach { line ->
            if (line.isEmpty()) {
                printScriptOutputChunk(level, line, console)
                return@forEach
            }
            var start = 0
            while (start < line.length) {
                val end = outputChunkEnd(line, start)
                printScriptOutputChunk(level, line.substring(start, end), console)
                start = end
            }
        }
    }

    private fun printStructuredConsoleOutput(nativeValues: Map<String, String>): Boolean {
        val eventsJson = embeddedConsoleEventsJson(nativeValues)
            ?: return false
        val events = runCatching { JSONArray(eventsJson) }.getOrNull() ?: return false
        if (events.length() == 0) {
            return false
        }
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            printScriptOutput(
                nodeConsoleLevel(event.optString("level")),
                event.optString("text"),
            )
        }
        return true
    }

    private fun embeddedConsoleEventsJson(nativeValues: Map<String, String>): String? {
        nativeValues["embedded_script.console_events_json"].takeUnless { it.isNullOrBlank() }?.let { return it }
        val resultJson = nativeValues["embedded_script.result_json"].takeUnless { it.isNullOrBlank() } ?: return null
        return runCatching { JSONObject(resultJson).optString("consoleEventsJson") }
            .getOrNull()
            ?.takeUnless { it.isBlank() }
    }

    private fun nodeConsoleLevel(level: String): Int {
        return when (level.uppercase()) {
            "VERBOSE" -> Log.VERBOSE
            "INFO" -> Log.INFO
            "WARN" -> Log.WARN
            "ERROR" -> Log.ERROR
            "ASSERT" -> Log.ASSERT
            else -> Log.DEBUG
        }
    }

    private fun printScriptOutputChunk(level: Int, chunk: String, console: GlobalConsole?) {
        if (console != null) {
            console.println(level, chunk)
        } else {
            Log.println(level, TAG, chunk)
        }
    }

    private fun outputChunkEnd(line: String, start: Int): Int {
        val maxEnd = (start + SCRIPT_OUTPUT_LOG_LINE_MAX_CHARS).coerceAtMost(line.length)
        if (maxEnd >= line.length) {
            return line.length
        }
        for (index in maxEnd - 1 downTo start + 1) {
            if (line[index].isWhitespace()) {
                return index + 1
            }
        }
        return maxEnd
    }

    private fun buildEmbeddedScriptRequest(source: JavaScriptSource): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val requestSource = normalizeEmbeddedSource(sourceText(source))
        val requestSourceName = sourceName(source)
        val requestWorkingDirectory = workingDirectory(source)
        val requestSandboxRoot = sandboxRoot(requestWorkingDirectory)
        val rootLoaderAnalysis = analyzeCommonJsSourceLoaders(requestSource)
        val isEsmEntry = isEsmSourceName(requestSourceName, requestWorkingDirectory) ||
                rootLoaderAnalysis.sourceLevelEsmSyntaxCount > 0
        val preload = collectCommonJsModuleSources(
            source = requestSource,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            isEsmEntry = isEsmEntry,
            rootLoaderAnalysis = rootLoaderAnalysis,
        )
        val encryptedScan = collectEncryptedRuntimeModuleSources(
            source = source,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            moduleSources = preload.moduleSources,
            decision = encryptedRuntimeModuleScanDecision(isEsmEntry, preload.diagnostics),
        )
        val diagnostics = RequestPreparationDiagnostics(
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            isEsmEntry = isEsmEntry,
            elapsedMs = elapsedSince(startedAt),
            commonJs = preload.diagnostics,
            encryptedRuntimeModuleScan = encryptedScan.diagnostics,
        )
        lastRequestPreparationDiagnostics = diagnostics
        Log.i(
            TAG,
            "embedded request prepared sourceName=$requestSourceName " +
                    "moduleSources=${preload.moduleSources.size} " +
                    "runtimeModuleSources=${encryptedScan.moduleSources.size} " +
                    "runtimeFirst=${encryptedScan.moduleSources.keys.firstOrNull().orEmpty()} " +
                    "requestPreparationMs=${diagnostics.elapsedMs} " +
                    "commonJsMs=${preload.diagnostics.elapsedMs} " +
                    "encryptedScan=${encryptedScan.diagnostics.decision} " +
                    "encryptedScanMs=${encryptedScan.diagnostics.elapsedMs} " +
                    "encryptedCandidates=${encryptedScan.diagnostics.candidateFileCount}",
        )
        lastCommonJsPreloadDiagnostics = preload.diagnostics
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
            source = requestSource,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            sandboxRoot = requestSandboxRoot,
            moduleSources = preload.moduleSources,
            runtimeModuleSources = encryptedScan.moduleSources,
            spawnDepth = currentSpawnDepth(),
        )
    }

    private fun buildRuntimePreflightRequest(source: JavaScriptSource): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
        val requestWorkingDirectory = workingDirectory(source)
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
            source = "",
            sourceName = sourceName(source),
            workingDirectory = requestWorkingDirectory,
            sandboxRoot = sandboxRoot(requestWorkingDirectory),
            spawnDepth = currentSpawnDepth(),
        )
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

    private fun dynamicRequireDiagnosticHint(
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): String? {
        if (lastCommonJsPreloadDiagnostics.dynamicRequireIgnoredCount <= 0) {
            return null
        }
        val message = result.errorMessage.orEmpty()
        val looksLikeMissingModule = message.contains("Cannot find Embedded Node") ||
                message.contains("Cannot find module")
        if (!looksLikeMissingModule) {
            return null
        }
        return "Dynamic require is not preloaded by Embedded Node.js file engine; " +
                "use a static string literal require(...) or provide explicit moduleSources."
    }

    private fun sourceText(source: JavaScriptSource): String {
        val file = (source as? JavaScriptFileSource)?.file ?: return source.script
        return sourceText(file)
    }

    private fun sourceText(file: File): String {
        return sourceTextOrNull(file, RUNTIME_MODULE_SOURCE_MAX_BYTES)
            ?: throw IOException("Unable to read JavaScript source file: ${file.absolutePath}")
    }

    private fun sourceTextOrNull(file: File, maximumCleartextBytes: Int): String? {
        return when (val result = readSourceTextBounded(file, maximumCleartextBytes)) {
            is BoundedSourceReadResult.Success -> result.source
            BoundedSourceReadResult.SizeLimitExceeded,
            BoundedSourceReadResult.Failure,
            -> null
        }
    }

    private fun readSourceTextBounded(file: File, maximumCleartextBytes: Int): BoundedSourceReadResult {
        val maximumStoredBytes = maximumCleartextBytes.toLong() +
                EncryptedScriptFileHeader.BLOCK_SIZE +
                ENCRYPTED_SOURCE_CIPHER_BLOCK_SIZE_BYTES
        val canonicalFile = file.strictCanonicalFileOrNull()
            ?: return BoundedSourceReadResult.Failure
        val canonicalPath = canonicalFile.absolutePath
        val identity = canonicalFile.fileIdentityOrNull(noFollow = true)
            ?: return BoundedSourceReadResult.Failure
        val bytes = when (val result = readFileBytesBounded(
            file = canonicalFile,
            expectedCanonicalPath = canonicalPath,
            expectedIdentity = identity,
            maximumBytes = maximumStoredBytes,
        )) {
            is BoundedFileReadResult.Success -> result.bytes
            BoundedFileReadResult.SizeLimitExceeded -> return BoundedSourceReadResult.SizeLimitExceeded
            BoundedFileReadResult.Failure -> return BoundedSourceReadResult.Failure
        }
        if (bytes.size < EncryptedScriptFileHeader.BLOCK_SIZE || !EncryptedScriptFileHeader.isValidFile(bytes)) {
            return if (bytes.size <= maximumCleartextBytes) {
                BoundedSourceReadResult.Success(bytes.toString(Charsets.UTF_8))
            } else {
                BoundedSourceReadResult.SizeLimitExceeded
            }
        }
        val cleartext = runCatching {
            ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE)
        }.getOrNull() ?: return BoundedSourceReadResult.Failure
        return if (cleartext.size <= maximumCleartextBytes) {
            BoundedSourceReadResult.Success(cleartext.toString(Charsets.UTF_8))
        } else {
            BoundedSourceReadResult.SizeLimitExceeded
        }
    }

    private fun readFileBytesBounded(
        file: File,
        expectedCanonicalPath: String,
        expectedIdentity: FileIdentity,
        maximumBytes: Long,
    ): BoundedFileReadResult {
        if (maximumBytes < 0L || maximumBytes > Int.MAX_VALUE) {
            return BoundedFileReadResult.Failure
        }
        val currentCanonicalFile = file.strictCanonicalFileOrNull() ?: return BoundedFileReadResult.Failure
        if (currentCanonicalFile.absolutePath != expectedCanonicalPath) {
            return BoundedFileReadResult.Failure
        }
        return try {
            val descriptor = Os.open(
                expectedCanonicalPath,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
            val openedStat = try {
                Os.fstat(descriptor)
            } catch (error: Exception) {
                runCatching { Os.close(descriptor) }
                throw error
            }
            if (!expectedIdentity.matches(openedStat) || !OsConstants.S_ISREG(openedStat.st_mode)) {
                runCatching { Os.close(descriptor) }
                return BoundedFileReadResult.Failure
            }
            if (openedStat.st_size > maximumBytes) {
                runCatching { Os.close(descriptor) }
                return BoundedFileReadResult.SizeLimitExceeded
            }
            val inputStream = try {
                FileInputStream(descriptor)
            } catch (error: Exception) {
                runCatching { Os.close(descriptor) }
                throw error
            }
            inputStream.buffered().use { input ->
                val output = ByteArrayOutputStream(
                    openedStat.st_size.coerceAtMost(ENCRYPTED_SOURCE_READ_BUFFER_SIZE.toLong()).toInt(),
                )
                val buffer = ByteArray(ENCRYPTED_SOURCE_READ_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    if (count == 0) {
                        continue
                    }
                    total += count
                    if (total > maximumBytes) {
                        return BoundedFileReadResult.SizeLimitExceeded
                    }
                    output.write(buffer, 0, count)
                }
                BoundedFileReadResult.Success(output.toByteArray())
            }
        } catch (_: Exception) {
            BoundedFileReadResult.Failure
        }
    }

    private fun EncryptedRuntimeModuleCandidate.encryptedScriptSourceText(
        scanRoot: EncryptedRuntimeScanRoot,
        stats: EncryptedRuntimeModuleScanStats,
        maximumCleartextBytes: Int,
    ): String? {
        val canonicalFile = file.strictCanonicalFileOrNull()
        if (
            canonicalFile == null ||
            canonicalFile.absolutePath != moduleKey ||
            !scanRoot.containsCanonicalPath(canonicalFile.absolutePath)
        ) {
            stats.identityMismatchCount += 1
            return null
        }
        stats.headerProbeCount += 1
        val header = ByteArray(EncryptedScriptFileHeader.BLOCK_SIZE)
        var headerBytesRead = 0
        val encryptedPayload = try {
            val descriptor = Os.open(
                moduleKey,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
            val openedStat = try {
                Os.fstat(descriptor)
            } catch (error: Exception) {
                runCatching { Os.close(descriptor) }
                throw error
            }
            if (!identity.matches(openedStat)) {
                stats.identityMismatchCount += 1
                runCatching { Os.close(descriptor) }
                return null
            }
            val inputStream = try {
                FileInputStream(descriptor)
            } catch (error: Exception) {
                runCatching { Os.close(descriptor) }
                throw error
            }
            inputStream.buffered().use { input ->
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count <= 0) {
                        break
                    }
                    offset += count
                }
                headerBytesRead = offset
                stats.headerBytesRead += offset.toLong()
                if (
                    offset < EncryptedScriptFileHeader.BLOCK_SIZE ||
                    !EncryptedScriptFileHeader.isValidFile(header)
                ) {
                    return null
                }

                val maximumEncryptedPayloadBytes = maximumCleartextBytes.toLong() +
                        ENCRYPTED_SOURCE_CIPHER_BLOCK_SIZE_BYTES
                val knownPayloadBytes = (openedStat.st_size - EncryptedScriptFileHeader.BLOCK_SIZE)
                    .coerceAtLeast(0L)
                if (knownPayloadBytes > maximumEncryptedPayloadBytes) {
                    stats.oversizedCiphertextCount += 1
                    stats.encryptedModuleBudgetRejectedCount += 1
                    if (maximumCleartextBytes < RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                        stats.outputBytesBudgetExhausted = true
                    }
                    return null
                }
                val initialCapacity = knownPayloadBytes
                    .coerceAtMost(ENCRYPTED_SOURCE_READ_BUFFER_SIZE.toLong())
                    .toInt()
                val output = ByteArrayOutputStream(initialCapacity)
                val buffer = ByteArray(ENCRYPTED_SOURCE_READ_BUFFER_SIZE)
                var payloadBytesRead = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    if (count == 0) {
                        continue
                    }
                    payloadBytesRead += count
                    if (payloadBytesRead > maximumEncryptedPayloadBytes) {
                        stats.oversizedCiphertextCount += 1
                        stats.encryptedModuleBudgetRejectedCount += 1
                        if (maximumCleartextBytes < RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                            stats.outputBytesBudgetExhausted = true
                        }
                        return null
                    }
                    output.write(buffer, 0, count)
                }
                stats.fullFileReadCount += 1
                stats.fullFileBytesRead += header.size.toLong() + payloadBytesRead
                output.toByteArray()
            }
        } catch (_: Exception) {
            if (headerBytesRead < EncryptedScriptFileHeader.BLOCK_SIZE) {
                stats.headerReadFailureCount += 1
            } else {
                stats.fullFileReadFailureCount += 1
            }
            return null
        }
        return try {
            val cleartext = ScriptEncryption.decrypt(encryptedPayload)
            if (encryptedRuntimeModuleCleartextBudgetStatus(cleartext.size, maximumCleartextBytes) != null) {
                stats.cleartextSizeLimitRejectedCount += 1
                stats.encryptedModuleBudgetRejectedCount += 1
                if (maximumCleartextBytes < RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                    stats.outputBytesBudgetExhausted = true
                }
                return null
            }
            String(cleartext, Charsets.UTF_8)
        } catch (_: Exception) {
            stats.decryptFailureCount += 1
            null
        }
    }

    internal fun encryptedRuntimeModuleCleartextBudgetStatus(
        cleartextBytes: Int,
        maximumCleartextBytes: Int,
    ): String? = if (cleartextBytes > maximumCleartextBytes) {
        ENCRYPTED_SCAN_STATUS_INCOMPLETE_MODULE_SIZE_BUDGET
    } else {
        null
    }

    private fun collectCommonJsModuleSources(
        source: String,
        sourceName: String,
        workingDirectory: String?,
        isEsmEntry: Boolean = isEsmSourceName(sourceName, workingDirectory),
        rootLoaderAnalysis: CommonJsSourceLoaderAnalysis = analyzeCommonJsSourceLoaders(source),
    ): CommonJsPreloadResult {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val stats = CommonJsPreloadStats(source.toByteArray(Charsets.UTF_8).size)
        stats.trivialModuleFreeEntry = rootLoaderAnalysis.trivialModuleFreeEntry
        stats.recordLoaderAnalysis(rootLoaderAnalysis)
        if (isEsmEntry) {
            return CommonJsPreloadResult(
                moduleSources = emptyMap(),
                diagnostics = stats.toDiagnostics(sourceName, workingDirectory, emptyList(), startedAt),
            )
        }
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).strictCanonicalFileOrNull() }
            ?.takeIf { it.isDirectory }
            ?: return CommonJsPreloadResult(
                moduleSources = emptyMap(),
                diagnostics = stats.toDiagnostics(sourceName, workingDirectory, emptyList(), startedAt),
            )
        val preloadRoot = EncryptedRuntimeScanRoot.capture(root)
        if (preloadRoot == null || !preloadRoot.validateCurrentIdentity(stats)) {
            stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_ROOT_IDENTITY_FAILURE)
            return CommonJsPreloadResult(
                moduleSources = emptyMap(),
                diagnostics = stats.toDiagnostics(sourceName, workingDirectory, emptyList(), startedAt),
            )
        }
        val parentDir = File(sourceName)
            .takeIf { it.isAbsolute }
            ?.normalizedCanonicalFile()
            ?.parentFile
            ?: root
        val moduleSources = linkedMapOf<String, String>()
        collectCommonJsModuleSources(
            source = source,
            parentDirectory = parentDir,
            rootDirectory = root,
            preloadRoot = preloadRoot,
            moduleSources = moduleSources,
            visited = linkedSetOf(),
            stats = stats,
            depth = 1,
            loaderAnalysis = rootLoaderAnalysis,
        )
        if (!preloadRoot.validateCurrentIdentity(stats)) {
            moduleSources.clear()
            stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_ROOT_IDENTITY_FAILURE)
        }
        return CommonJsPreloadResult(
            moduleSources = moduleSources,
            diagnostics = stats.toDiagnostics(
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                moduleSourceNames = moduleSources.keys.toList(),
                startedAt = startedAt,
            ),
        )
    }

    private fun encryptedRuntimeModuleScanDecision(
        isEsmEntry: Boolean,
        commonJsDiagnostics: CommonJsPreloadDiagnostics,
    ): EncryptedRuntimeModuleScanDecision {
        return when {
            isEsmEntry -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_ESM_ENTRY,
            )
            commonJsDiagnostics.sourceLevelEsmSyntaxCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_SOURCE_LEVEL_ESM_GRAPH,
            )
            commonJsDiagnostics.dynamicImportCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_DYNAMIC_IMPORT,
            )
            commonJsDiagnostics.createRequireReferenceCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_CREATE_REQUIRE,
            )
            commonJsDiagnostics.unclassifiedLoaderReferenceCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_UNCLASSIFIED_LOADER,
            )
            commonJsDiagnostics.dynamicRequireIgnoredCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_DYNAMIC_REQUIRE,
            )
            commonJsDiagnostics.dynamicCodeReferenceCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_DYNAMIC_CODE,
            )
            commonJsDiagnostics.unresolvedStaticRequireCount > 0 -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_UNRESOLVED_STATIC_REQUIRE,
            )
            !commonJsDiagnostics.complete -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_COMMON_JS_PRELOAD_INCOMPLETE,
            )
            commonJsDiagnostics.trivialModuleFreeEntry -> EncryptedRuntimeModuleScanDecision(
                required = false,
                reason = ENCRYPTED_SCAN_REASON_TRIVIAL_MODULE_FREE,
            )
            else -> EncryptedRuntimeModuleScanDecision(
                required = true,
                reason = ENCRYPTED_SCAN_REASON_CONSERVATIVE_SOURCE,
            )
        }
    }

    private fun collectEncryptedRuntimeModuleSources(
        source: JavaScriptSource,
        sourceName: String,
        workingDirectory: String?,
        moduleSources: Map<String, String>,
        sourceFileOverride: File? = null,
        decision: EncryptedRuntimeModuleScanDecision,
    ): EncryptedRuntimeModuleSourcesResult {
        val stats = EncryptedRuntimeModuleScanStats(decision)
        if (!decision.required) {
            return stats.result(emptyMap(), ENCRYPTED_SCAN_STATUS_SKIPPED)
        }
        val rootFile = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).strictCanonicalFileOrNull() }
            ?.takeIf { it.isDirectory }
            ?: return stats.result(emptyMap(), ENCRYPTED_SCAN_STATUS_WORKING_DIRECTORY_UNAVAILABLE)
        val scanRoot = EncryptedRuntimeScanRoot.capture(rootFile)
            ?: return stats.apply { identityReadFailureCount += 1 }
                .result(emptyMap(), ENCRYPTED_SCAN_STATUS_INCOMPLETE_IDENTITY_FAILURE)
        if (!scanRoot.validateCurrentIdentity(stats)) {
            return stats.result(emptyMap(), ENCRYPTED_SCAN_STATUS_INCOMPLETE_IDENTITY_FAILURE)
        }
        val sourceFile = sourceFileOverride
            ?.strictCanonicalFileOrNull()
            ?.takeIf { it.isFile && scanRoot.containsCanonicalPath(it.absolutePath) }
            ?: (source as? JavaScriptFileSource)?.file
                ?.strictCanonicalFileOrNull()
                ?.takeIf { it.isFile && scanRoot.containsCanonicalPath(it.absolutePath) }
            ?: File(sourceName)
                .takeIf { it.isAbsolute }
                ?.strictCanonicalFileOrNull()
                ?.takeIf { it.isFile && scanRoot.containsCanonicalPath(it.absolutePath) }
            ?: return stats.result(emptyMap(), ENCRYPTED_SCAN_STATUS_SOURCE_FILE_UNAVAILABLE)
        val excluded = moduleSources.keys.toMutableSet()
        excluded += sourceFile.absolutePath
        val existingModuleSourceBytes = moduleSources.values.sumOf {
            it.toByteArray(Charsets.UTF_8).size.toLong()
        }
        encryptedRuntimeModuleOutputBudgetStatus(moduleSources.size, existingModuleSourceBytes)?.let { status ->
            if (status == ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_COUNT_BUDGET) {
                stats.outputCountBudgetExhausted = true
            } else {
                stats.outputBytesBudgetExhausted = true
            }
            return stats.result(emptyMap(), status)
        }
        var remainingCount = (RUNTIME_MODULE_SOURCE_MAX_COUNT - moduleSources.size).coerceAtLeast(0)
        var remainingBytes = (RUNTIME_MODULE_SOURCES_MAX_BYTES.toLong() - existingModuleSourceBytes).toInt()
        val runtimeSources = linkedMapOf<String, String>()
        stats.performed = true
        val candidates = collectEncryptedRuntimeModuleCandidates(scanRoot, stats)
        for (candidate in candidates) {
            if (remainingCount <= 0 || remainingBytes <= 0) {
                if (remainingCount <= 0) {
                    stats.outputCountBudgetExhausted = true
                }
                if (remainingBytes <= 0) {
                    stats.outputBytesBudgetExhausted = true
                }
                break
            }
            if (candidate.moduleKey in excluded) {
                continue
            }
            val maximumCleartextBytes = minOf(RUNTIME_MODULE_SOURCE_MAX_BYTES, remainingBytes)
            val clearSource = candidate.encryptedScriptSourceText(
                scanRoot = scanRoot,
                stats = stats,
                maximumCleartextBytes = maximumCleartextBytes,
            ) ?: continue
            val moduleSource = normalizeEmbeddedSource(clearSource)
            val moduleBytes = moduleSource.toByteArray(Charsets.UTF_8).size
            if (moduleBytes > RUNTIME_MODULE_SOURCE_MAX_BYTES || moduleBytes > remainingBytes) {
                stats.encryptedModuleBudgetRejectedCount += 1
                if (moduleBytes > RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                    stats.cleartextSizeLimitRejectedCount += 1
                }
                if (moduleBytes > remainingBytes) {
                    stats.outputBytesBudgetExhausted = true
                }
                continue
            }
            runtimeSources[candidate.moduleKey] = moduleSource
            stats.encryptedModuleCount += 1
            stats.encryptedModuleSourceBytes += moduleBytes.toLong()
            remainingCount -= 1
            remainingBytes -= moduleBytes
        }
        val hookSucceeded = runCatching { encryptedRuntimeScanBeforeFinalRootIdentityCheckForTest?.invoke() }
            .onFailure { stats.identityReadFailureCount += 1 }
            .isSuccess
        if (!hookSucceeded || !scanRoot.validateCurrentIdentity(stats)) {
            runtimeSources.clear()
        }
        return stats.result(runtimeSources, stats.completionStatus())
    }

    internal fun encryptedRuntimeModuleOutputBudgetStatus(
        existingModuleSourceCount: Int,
        existingModuleSourceBytes: Long,
    ): String? {
        return when {
            existingModuleSourceCount >= RUNTIME_MODULE_SOURCE_MAX_COUNT ->
                ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_COUNT_BUDGET
            existingModuleSourceBytes >= RUNTIME_MODULE_SOURCES_MAX_BYTES.toLong() ->
                ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_BYTES_BUDGET
            else -> null
        }
    }

    private fun collectEncryptedRuntimeModuleCandidates(
        scanRoot: EncryptedRuntimeScanRoot,
        stats: EncryptedRuntimeModuleScanStats,
    ): List<EncryptedRuntimeModuleCandidate> {
        val candidates = mutableListOf<EncryptedRuntimeModuleCandidate>()
        val visitedCanonicalDirectories = linkedSetOf<String>()
        val visitedCanonicalFiles = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            collectEncryptedRuntimeModuleCandidatesStreaming(
                scanRoot = scanRoot,
                stats = stats,
                candidates = candidates,
                visitedCanonicalDirectories = visitedCanonicalDirectories,
                visitedCanonicalFiles = visitedCanonicalFiles,
            )
        } else {
            collectEncryptedRuntimeModuleCandidatesLegacy(
                scanRoot = scanRoot,
                stats = stats,
                candidates = candidates,
                visitedCanonicalDirectories = visitedCanonicalDirectories,
                visitedCanonicalFiles = visitedCanonicalFiles,
            )
        }
        candidates.sortWith(
            compareBy<EncryptedRuntimeModuleCandidate>(
                { candidate -> candidate.relativePath.split('/').contains(NODE_MODULES) },
                { candidate -> candidate.relativePath.count { it == '/' } },
                { candidate -> candidate.relativePath },
            )
        )
        return candidates
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun collectEncryptedRuntimeModuleCandidatesStreaming(
        scanRoot: EncryptedRuntimeScanRoot,
        stats: EncryptedRuntimeModuleScanStats,
        candidates: MutableList<EncryptedRuntimeModuleCandidate>,
        visitedCanonicalDirectories: MutableSet<String>,
        visitedCanonicalFiles: MutableSet<String>,
    ) {
        val pendingDirectories = java.util.ArrayDeque<File>()
        pendingDirectories.add(scanRoot.canonicalFile)
        var stop = false
        while (pendingDirectories.isNotEmpty() && !stop) {
            val directory = pendingDirectories.removeLast()
            val canonicalDirectory = directory.strictCanonicalFileOrNull()
            val directoryKey = canonicalDirectory?.absolutePath
            if (
                canonicalDirectory == null ||
                !canonicalDirectory.isDirectory ||
                !scanRoot.containsCanonicalPath(canonicalDirectory.absolutePath) ||
                directoryKey == null ||
                !visitedCanonicalDirectories.add(directoryKey)
            ) {
                stats.prunedDirectoryCount += 1
                if (canonicalDirectory == null || !canonicalDirectory.isDirectory) {
                    stats.walkFailureCount += 1
                }
                continue
            }
            try {
                Files.newDirectoryStream(canonicalDirectory.toPath()).use { entries ->
                    for (path in entries) {
                        if (stats.examinedPathCount >= ENCRYPTED_SCAN_MAX_EXAMINED_PATHS) {
                            stats.traversalLimitReached = true
                            stop = true
                            break
                        }
                        stats.examinedPathCount += 1
                        val walkedFile = path.toFile()
                        if (Files.isSymbolicLink(path)) {
                            val symbolicTarget = walkedFile.strictCanonicalFileOrNull()
                            if (symbolicTarget == null) {
                                stats.walkFailureCount += 1
                            } else if (symbolicTarget.isDirectory) {
                                stats.prunedDirectoryCount += 1
                            } else {
                                stats.outsidePathRejectedCount += 1
                            }
                            continue
                        }
                        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                            pendingDirectories.add(walkedFile)
                            continue
                        }
                        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            continue
                        }
                        if (!recordEncryptedRuntimeModuleCandidate(
                                walkedFile,
                                scanRoot,
                                stats,
                                candidates,
                                visitedCanonicalFiles,
                            )) {
                            stop = true
                            break
                        }
                    }
                }
            } catch (_: IOException) {
                stats.walkFailureCount += 1
            } catch (_: DirectoryIteratorException) {
                stats.walkFailureCount += 1
            } catch (_: SecurityException) {
                stats.walkFailureCount += 1
            }
        }
    }

    private fun collectEncryptedRuntimeModuleCandidatesLegacy(
        scanRoot: EncryptedRuntimeScanRoot,
        stats: EncryptedRuntimeModuleScanStats,
        candidates: MutableList<EncryptedRuntimeModuleCandidate>,
        visitedCanonicalDirectories: MutableSet<String>,
        visitedCanonicalFiles: MutableSet<String>,
    ) {
        try {
            scanRoot.canonicalFile.walkTopDown()
                .onEnter { directory ->
                    if (stats.examinedPathCount >= ENCRYPTED_SCAN_MAX_EXAMINED_PATHS) {
                        stats.traversalLimitReached = true
                        throw EncryptedScanTraversalStoppedException()
                    }
                    stats.examinedPathCount += 1
                    val canonicalDirectory = directory.strictCanonicalFileOrNull()
                    val accepted = canonicalDirectory != null &&
                            canonicalDirectory.isDirectory &&
                            scanRoot.containsCanonicalPath(canonicalDirectory.absolutePath) &&
                            visitedCanonicalDirectories.add(canonicalDirectory.absolutePath)
                    if (!accepted) {
                        stats.prunedDirectoryCount += 1
                        if (canonicalDirectory == null || !canonicalDirectory.isDirectory) {
                            stats.walkFailureCount += 1
                        }
                    }
                    accepted
                }
                .onFail { _, _ -> stats.walkFailureCount += 1 }
                .forEach { walkedFile ->
                    if (walkedFile.isDirectory) {
                        return@forEach
                    }
                    if (stats.examinedPathCount >= ENCRYPTED_SCAN_MAX_EXAMINED_PATHS) {
                        stats.traversalLimitReached = true
                        throw EncryptedScanTraversalStoppedException()
                    }
                    stats.examinedPathCount += 1
                    if (!recordEncryptedRuntimeModuleCandidate(
                            walkedFile,
                            scanRoot,
                            stats,
                            candidates,
                            visitedCanonicalFiles,
                        )) {
                        throw EncryptedScanTraversalStoppedException()
                    }
                }
        } catch (_: EncryptedScanTraversalStoppedException) {
            // The explicit limit status is reported by the caller.
        }
    }

    private fun recordEncryptedRuntimeModuleCandidate(
        walkedFile: File,
        scanRoot: EncryptedRuntimeScanRoot,
        stats: EncryptedRuntimeModuleScanStats,
        candidates: MutableList<EncryptedRuntimeModuleCandidate>,
        visitedCanonicalFiles: MutableSet<String>,
    ): Boolean {
        val file = walkedFile.strictCanonicalFileOrNull()
        if (file == null || !file.isFile) {
            stats.identityReadFailureCount += 1
            return true
        }
        if (!scanRoot.containsCanonicalPath(file.absolutePath)) {
            stats.outsidePathRejectedCount += 1
            return true
        }
        stats.walkedFileCount += 1
        if (
            file.extension.lowercase() !in RUNTIME_MODULE_SOURCE_EXTENSIONS ||
            NodeTypeScriptStripper.isTypeScriptDeclarationSourceName(file.name)
        ) {
            return true
        }
        val moduleKey = file.absolutePath
        if (!visitedCanonicalFiles.add(moduleKey)) {
            stats.duplicateCanonicalFileCount += 1
            return true
        }
        if (candidates.size >= ENCRYPTED_SCAN_MAX_CANDIDATES) {
            stats.candidateLimitReached = true
            return false
        }
        val identity = file.fileIdentityOrNull(noFollow = true)
        if (identity == null) {
            stats.identityReadFailureCount += 1
            return true
        }
        candidates += EncryptedRuntimeModuleCandidate(
            file = file,
            moduleKey = moduleKey,
            relativePath = scanRoot.relativePath(file.absolutePath),
            identity = identity,
        )
        stats.candidateFileCount += 1
        return true
    }

    private fun isEsmSourceName(sourceName: String, workingDirectory: String?): Boolean {
        if (NodeTypeScriptStripper.isTypeScriptDeclarationSourceName(sourceName)) {
            return false
        }
        val extension = File(sourceName).extension.lowercase()
        if (extension == MJS_EXTENSION || extension == MTS_EXTENSION) {
            return true
        }
        if (extension != JS_EXTENSION && extension != TS_EXTENSION) {
            return false
        }
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).normalizedCanonicalFile() }
            ?: return false
        val file = File(sourceName).takeIf { it.isAbsolute }?.normalizedCanonicalFile() ?: return false
        return packageTypeModuleForFile(file, root)
    }

    private fun packageTypeModuleForFile(file: File, root: File): Boolean {
        var current = file.parentFile?.normalizedCanonicalFile() ?: root
        while (current.isInsideOrSame(root)) {
            val packageJson = File(current, PACKAGE_JSON).normalizedCanonicalFile()
            if (packageJson.isFile && packageJson.isInsideOrSame(root) && packageJson.isInsideOrSame(current)) {
                return runCatching {
                    JSONObject(sourceText(packageJson)).optString("type") == "module"
                }.getOrDefault(false)
            }
            if (current == root) {
                break
            }
            current = current.parentFile?.normalizedCanonicalFile() ?: break
        }
        return false
    }

    private fun collectCommonJsModuleSources(
        source: String,
        parentDirectory: File,
        rootDirectory: File,
        preloadRoot: EncryptedRuntimeScanRoot,
        moduleSources: MutableMap<String, String>,
        visited: MutableSet<String>,
        stats: CommonJsPreloadStats,
        depth: Int,
        loaderAnalysis: CommonJsSourceLoaderAnalysis = analyzeCommonJsSourceLoaders(source),
    ) {
        val pending = java.util.ArrayDeque<CommonJsPreloadFrame>()
        pending.add(
            CommonJsPreloadFrame(
                parentDirectory = parentDirectory,
                depth = depth,
                loaderAnalysis = loaderAnalysis,
                recordLoaderAnalysis = depth > 1,
            )
        )
        while (pending.isNotEmpty() && stats.complete) {
            val frame = pending.peekLast() ?: break
            if (!frame.started) {
                frame.started = true
                if (frame.depth > COMMON_JS_PRELOAD_MAX_DEPTH) {
                    stats.depthLimitRejectedCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_DEPTH_LIMIT)
                    break
                }
                if (frame.recordLoaderAnalysis) {
                    stats.recordLoaderAnalysis(frame.loaderAnalysis)
                }
            }

            if (frame.moduleFileIndex < frame.moduleFiles.size) {
                val unresolvedModuleFile = frame.moduleFiles[frame.moduleFileIndex++]
                val moduleFile = unresolvedModuleFile.strictCanonicalFileOrNull()
                if (moduleFile == null || !preloadRoot.containsCanonicalPath(moduleFile.absolutePath)) {
                    stats.sourceReadFailureCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_READ_FAILURE)
                    break
                }
                val moduleKey = moduleFile.absolutePath
                if (!visited.add(moduleKey)) {
                    stats.duplicateSkippedCount += 1
                    continue
                }
                if (moduleSources.size >= COMMON_JS_PRELOAD_HARD_MODULE_COUNT) {
                    stats.moduleCountLimitRejectedCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_MODULE_COUNT_LIMIT)
                    break
                }
                val rawModuleSource = when (
                    val readResult = readSourceTextBounded(moduleFile, RUNTIME_MODULE_SOURCE_MAX_BYTES)
                ) {
                    is BoundedSourceReadResult.Success -> readResult.source
                    BoundedSourceReadResult.SizeLimitExceeded -> {
                        stats.sourceSizeLimitRejectedCount += 1
                        stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_SIZE_LIMIT)
                        break
                    }
                    BoundedSourceReadResult.Failure -> {
                        stats.sourceReadFailureCount += 1
                        stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_READ_FAILURE)
                        break
                    }
                }
                val moduleSource = normalizeEmbeddedSource(rawModuleSource)
                val moduleBytes = moduleSource.toByteArray(Charsets.UTF_8).size
                if (moduleBytes > RUNTIME_MODULE_SOURCE_MAX_BYTES) {
                    stats.sourceSizeLimitRejectedCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_SIZE_LIMIT)
                    break
                }
                if (stats.totalSourceBytes.toLong() + moduleBytes > RUNTIME_MODULE_SOURCES_MAX_BYTES) {
                    stats.aggregateSizeLimitRejectedCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_AGGREGATE_SIZE_LIMIT)
                    break
                }
                moduleSources[moduleKey] = moduleSource
                stats.recordPreloadedModule(moduleFile, moduleSource, frame.depth)
                if (moduleSources.size > RUNTIME_MODULE_SOURCE_MAX_COUNT) {
                    stats.moduleCountLimitRejectedCount += 1
                    stats.markIncomplete(COMMON_JS_PRELOAD_INCOMPLETE_MODULE_COUNT_LIMIT)
                    break
                }
                if (
                    moduleFile.extension.lowercase() in COMMON_JS_RECURSIVE_SOURCE_EXTENSIONS &&
                    !NodeTypeScriptStripper.isTypeScriptDeclarationSourceName(moduleFile.name)
                ) {
                    pending.add(
                        CommonJsPreloadFrame(
                            parentDirectory = moduleFile.parentFile ?: rootDirectory,
                            depth = frame.depth + 1,
                            loaderAnalysis = analyzeCommonJsSourceLoaders(moduleSource),
                            recordLoaderAnalysis = true,
                        )
                    )
                }
                continue
            }

            if (frame.referenceIndex >= frame.loaderAnalysis.requireReferences.size) {
                pending.removeLast()
                continue
            }
            val reference = frame.loaderAnalysis.requireReferences[frame.referenceIndex++]
            val moduleName = reference.moduleName ?: continue
            if (moduleName.isRuntimeProvidedCommonJsSpecifier()) {
                continue
            }
            frame.moduleFiles = resolveCommonJsModuleFiles(frame.parentDirectory, rootDirectory, moduleName)
            frame.moduleFileIndex = 0
            if (frame.moduleFiles.isEmpty()) {
                stats.unresolvedStaticRequireCount += 1
            }
        }
    }

    private fun resolveCommonJsModuleFiles(parentDirectory: File, rootDirectory: File, moduleName: String): List<File> {
        if (moduleName.isRelativeCommonJsModuleName()) {
            return resolveCommonJsLocalModuleFiles(parentDirectory, rootDirectory, moduleName)
        }
        if (moduleName.isCommonJsPackageImportSpecifier()) {
            return resolveCommonJsPackageImportFiles(parentDirectory, rootDirectory, moduleName)
        }
        val packageSpecifier = moduleName.toCommonJsPackageSpecifier() ?: return emptyList()
        resolveCommonJsPackageSelfReferenceFiles(parentDirectory, rootDirectory, packageSpecifier)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        return resolveCommonJsNodeModulesFiles(parentDirectory, rootDirectory, packageSpecifier)
    }

    private fun resolveCommonJsLocalModuleFiles(parentDirectory: File, rootDirectory: File, moduleName: String): List<File> {
        val base = File(parentDirectory, moduleName).normalizedCanonicalFile()
        if (!base.isInsideOrSame(rootDirectory)) {
            return emptyList()
        }
        return resolveCommonJsFileOrDirectoryFiles(base, rootDirectory)
    }

    private fun resolveCommonJsFileOrDirectoryFiles(base: File, rootDirectory: File): List<File> {
        val extension = base.extension.lowercase()
        if (extension in COMMON_JS_MODULE_EXTENSIONS) {
            return resolveFirstCommonJsModuleFile(listOf(base), rootDirectory)?.let { listOf(it) } ?: emptyList()
        }
        if (extension.isNotBlank()) {
            return emptyList()
        }

        resolveFirstCommonJsModuleFile(
            listOf(base, File(base.path + ".$JS_EXTENSION"), File(base.path + ".$JSON_EXTENSION")),
            rootDirectory,
        )?.let { return listOf(it) }

        return resolveCommonJsPackageDirectoryFiles(base, rootDirectory)
    }

    private fun resolveCommonJsPackageDirectoryFiles(packageDirectory: File, rootDirectory: File): List<File> {
        val packageJson = File(packageDirectory, PACKAGE_JSON).normalizedCanonicalFile()
        if (packageJson.isFile && packageJson.isInsideOrSame(rootDirectory) && packageJson.isInsideOrSame(packageDirectory)) {
            val packageFiles = mutableListOf(packageJson)
            val packageExports = packageJsonExportsTarget(packageJson, subpath = "")
            if (packageExports.present) {
                packageExports.value
                    ?.let { resolveCommonJsPackageExportsFile(packageDirectory, rootDirectory, it) }
                    ?.let { packageFiles += it }
                return packageFiles
            }
            val packageMain = packageJsonMain(packageJson)
            if (packageMain.present) {
                resolveCommonJsPackageMainFile(packageDirectory, rootDirectory, packageMain.value)
                    ?.let {
                        packageFiles += it
                        return packageFiles
                    }
            }
        }

        return resolveFirstCommonJsModuleFile(
            listOf(File(packageDirectory, "index.$JS_EXTENSION"), File(packageDirectory, "index.$JSON_EXTENSION")),
            rootDirectory,
        )?.let { listOf(it) } ?: emptyList()
    }

    private fun resolveCommonJsNodeModulesFiles(
        parentDirectory: File,
        rootDirectory: File,
        packageSpecifier: CommonJsPackageSpecifier,
    ): List<File> {
        commonJsNodeModulesDirectories(parentDirectory, rootDirectory).forEach { nodeModulesDirectory ->
            val nodeModulesRoot = nodeModulesDirectory.normalizedCanonicalFile()
            if (!nodeModulesRoot.isInsideOrSame(rootDirectory)) {
                return@forEach
            }
            val packageDirectory = File(nodeModulesRoot, packageSpecifier.packageName).normalizedCanonicalFile()
            if (!packageDirectory.isInsideOrSame(rootDirectory) || !packageDirectory.isInsideOrSame(nodeModulesRoot)) {
                return@forEach
            }
            val files = if (packageSpecifier.subpath.isBlank()) {
                resolveCommonJsPackageDirectoryFiles(packageDirectory, rootDirectory)
            } else {
                resolveCommonJsPackageSubpathFiles(packageDirectory, rootDirectory, packageSpecifier.subpath)
            }
            if (files.isNotEmpty()) {
                return files
            }
        }
        return emptyList()
    }

    private fun commonJsNodeModulesDirectories(parentDirectory: File, rootDirectory: File): List<File> {
        val rootPath = rootDirectory.normalizedAbsolutePath()
        var current = parentDirectory.normalizedCanonicalFile()
        if (!current.isInsideOrSame(rootDirectory)) {
            current = rootDirectory
        }
        val directories = mutableListOf<File>()
        while (current.isInsideOrSame(rootDirectory)) {
            if (current.name != NODE_MODULES) {
                directories += File(current, NODE_MODULES)
            }
            if (current.normalizedAbsolutePath() == rootPath) {
                break
            }
            val parent = current.parentFile ?: break
            current = if (parent.isInsideOrSame(rootDirectory)) parent else rootDirectory
        }
        return directories
    }

    private fun resolveCommonJsPackageSubpathFiles(
        packageDirectory: File,
        rootDirectory: File,
        subpath: String,
    ): List<File> {
        if (subpath.indexOf('\u0000') >= 0 || File(subpath).isAbsolute) {
            return emptyList()
        }
        val packageJson = File(packageDirectory, PACKAGE_JSON).normalizedCanonicalFile()
        if (packageJson.isFile && packageJson.isInsideOrSame(rootDirectory) && packageJson.isInsideOrSame(packageDirectory)) {
            val packageExports = packageJsonExportsTarget(packageJson, subpath)
            if (packageExports.present) {
                val packageFiles = mutableListOf(packageJson)
                packageExports.value
                    ?.let { resolveCommonJsPackageExportsFile(packageDirectory, rootDirectory, it) }
                    ?.let { packageFiles += it }
                return packageFiles
            }
        }
        val base = File(packageDirectory, subpath).normalizedCanonicalFile()
        if (!base.isInsideOrSame(packageDirectory)) {
            return emptyList()
        }
        return resolveCommonJsFileOrDirectoryFiles(base, rootDirectory)
            .filter { it.isInsideOrSame(packageDirectory) }
    }

    private fun resolveCommonJsPackageImportFiles(
        parentDirectory: File,
        rootDirectory: File,
        specifier: String,
    ): List<File> {
        val scope = commonJsPackageScope(parentDirectory, rootDirectory) ?: return emptyList()
        val packageFiles = mutableListOf(scope.packageJson)
        val packageImport = packageJsonImportsTarget(scope.packageJson, specifier)
        if (packageImport.present) {
            packageImport.value
                ?.let { resolveCommonJsPackageImportsFile(scope.packageDirectory, rootDirectory, it) }
                ?.let { packageFiles += it }
        }
        return packageFiles
    }

    private fun resolveCommonJsPackageSelfReferenceFiles(
        parentDirectory: File,
        rootDirectory: File,
        packageSpecifier: CommonJsPackageSpecifier,
    ): List<File> {
        val scope = commonJsPackageScope(parentDirectory, rootDirectory) ?: return emptyList()
        val packageName = packageJsonName(scope.packageJson) ?: return emptyList()
        if (packageName != packageSpecifier.packageName) {
            return emptyList()
        }
        val packageExport = packageJsonExportsTarget(scope.packageJson, packageSpecifier.subpath)
        if (!packageExport.present) {
            return emptyList()
        }
        val packageFiles = mutableListOf(scope.packageJson)
        packageExport.value
            ?.let { resolveCommonJsPackageExportsFile(scope.packageDirectory, rootDirectory, it) }
            ?.let { packageFiles += it }
        return packageFiles
    }

    private fun commonJsPackageScope(parentDirectory: File, rootDirectory: File): CommonJsPackageScope? {
        val rootPath = rootDirectory.normalizedAbsolutePath()
        var current = parentDirectory.normalizedCanonicalFile()
        if (!current.isInsideOrSame(rootDirectory)) {
            current = rootDirectory
        }
        while (current.isInsideOrSame(rootDirectory)) {
            val packageJson = File(current, PACKAGE_JSON).normalizedCanonicalFile()
            if (packageJson.isFile && packageJson.isInsideOrSame(rootDirectory) && packageJson.isInsideOrSame(current)) {
                return CommonJsPackageScope(packageDirectory = current, packageJson = packageJson)
            }
            if (current.normalizedAbsolutePath() != rootPath && current.isCommonJsNodeModulesPackageDirectory()) {
                return null
            }
            if (current.normalizedAbsolutePath() == rootPath) {
                break
            }
            val parent = current.parentFile ?: break
            current = if (parent.isInsideOrSame(rootDirectory)) parent else rootDirectory
        }
        return null
    }

    private fun File.isCommonJsNodeModulesPackageDirectory(): Boolean {
        val parent = parentFile ?: return false
        if (parent.name == NODE_MODULES) {
            return true
        }
        val grandParent = parent.parentFile ?: return false
        return parent.name.startsWith("@") && grandParent.name == NODE_MODULES
    }

    private fun resolveCommonJsPackageMainFile(
        packageDirectory: File,
        rootDirectory: File,
        packageMain: String?,
    ): File? {
        val main = packageMain?.takeIf { it.isNotBlank() } ?: return null
        if (main.indexOf('\u0000') >= 0 || File(main).isAbsolute) {
            return null
        }
        val base = File(packageDirectory, main).normalizedCanonicalFile()
        if (!base.isInsideOrSame(packageDirectory)) {
            return null
        }
        val extension = base.extension.lowercase()
        val candidates = when {
            extension in COMMON_JS_MODULE_EXTENSIONS -> listOf(base)
            extension.isBlank() -> listOf(base, File(base.path + ".$JS_EXTENSION"), File(base.path + ".$JSON_EXTENSION"))
            else -> return null
        }
        return resolveFirstCommonJsModuleFile(candidates, rootDirectory)
            ?.takeIf { it.isInsideOrSame(packageDirectory) }
    }

    private fun resolveCommonJsPackageExportsFile(
        packageDirectory: File,
        rootDirectory: File,
        packageExport: String?,
    ): File? {
        val target = packageExport?.takeIf { it.isNotBlank() } ?: return null
        if (!target.startsWith("./") || target.indexOf('\u0000') >= 0 || File(target).isAbsolute) {
            return null
        }
        val base = File(packageDirectory, target).normalizedCanonicalFile()
        if (!base.isInsideOrSame(packageDirectory)) {
            return null
        }
        val extension = base.extension.lowercase()
        val candidates = when {
            extension in COMMON_JS_MODULE_EXTENSIONS -> listOf(base)
            extension.isBlank() -> listOf(base, File(base.path + ".$JS_EXTENSION"), File(base.path + ".$JSON_EXTENSION"))
            else -> return null
        }
        return resolveFirstCommonJsModuleFile(candidates, rootDirectory)
            ?.takeIf { it.isInsideOrSame(packageDirectory) }
    }

    private fun resolveCommonJsPackageImportsFile(
        packageDirectory: File,
        rootDirectory: File,
        packageImport: String?,
    ): File? {
        return resolveCommonJsPackageExportsFile(packageDirectory, rootDirectory, packageImport)
    }

    private fun resolveFirstCommonJsModuleFile(candidates: List<File>, rootDirectory: File): File? {
        return candidates
            .map { it.normalizedCanonicalFile() }
            .firstOrNull { candidate ->
                candidate.isFile &&
                        candidate.isInsideOrSame(rootDirectory) &&
                        candidate.extension.lowercase() in COMMON_JS_MODULE_EXTENSIONS &&
                        !NodeTypeScriptStripper.isTypeScriptDeclarationSourceName(candidate.name)
            }
    }

    private fun packageJsonMain(packageJson: File): PackageMain {
        return runCatching {
            val packageData = JSONObject(sourceText(packageJson))
            if (!packageData.has(PACKAGE_JSON_MAIN)) {
                PackageMain(present = false, value = null)
            } else {
                PackageMain(present = true, value = packageData.opt(PACKAGE_JSON_MAIN) as? String)
            }
        }.getOrElse {
            PackageMain(present = true, value = null)
        }
    }

    private fun packageJsonName(packageJson: File): String? {
        return runCatching {
            (JSONObject(sourceText(packageJson)).opt(PACKAGE_JSON_NAME) as? String)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun packageJsonExportsTarget(packageJson: File, subpath: String): PackageExport {
        return runCatching {
            val packageData = JSONObject(sourceText(packageJson))
            if (!packageData.has(PACKAGE_JSON_EXPORTS)) {
                PackageExport(present = false, value = null)
            } else {
                PackageExport(
                    present = true,
                    value = selectPackageExportsTarget(packageData.opt(PACKAGE_JSON_EXPORTS), subpath),
                )
            }
        }.getOrElse {
            PackageExport(present = true, value = null)
        }
    }

    private fun packageJsonImportsTarget(packageJson: File, specifier: String): PackageImport {
        return runCatching {
            val packageData = JSONObject(sourceText(packageJson))
            if (!packageData.has(PACKAGE_JSON_IMPORTS)) {
                PackageImport(present = false, value = null)
            } else {
                PackageImport(
                    present = true,
                    value = selectPackageImportsTarget(packageData.opt(PACKAGE_JSON_IMPORTS), specifier),
                )
            }
        }.getOrElse {
            PackageImport(present = true, value = null)
        }
    }

    private fun selectPackageExportsTarget(exportsValue: Any?, subpath: String): String? {
        val key = if (subpath.isBlank()) "." else "./$subpath"
        if (exportsValue is String) {
            return exportsValue.takeIf { subpath.isBlank() }
        }
        val exportsObject = exportsValue as? JSONObject ?: return null
        val exactEntry = when {
            exportsObject.has(key) -> exportsObject.opt(key) to null
            subpath.isBlank() &&
                    !exportsObject.has(".") &&
                    PACKAGE_JSON_EXPORT_CONDITIONS.any { exportsObject.has(it) } -> exportsObject to null
            else -> null
        }
        val entry = exactEntry ?: selectPackagePatternEntry(exportsObject, key) ?: return null
        val target = selectPackageExportConditionTarget(entry.first) ?: return null
        return applyPackagePatternTarget(target, entry.second)
    }

    private fun selectPackageExportConditionTarget(entry: Any?): String? {
        if (entry is String) {
            return entry
        }
        val conditionObject = entry as? JSONObject ?: return null
        PACKAGE_JSON_EXPORT_CONDITIONS.forEach { condition ->
            if (conditionObject.has(condition)) {
                selectPackageExportConditionTarget(conditionObject.opt(condition))?.let { return it }
            }
        }
        return null
    }

    private fun selectPackageImportsTarget(importsValue: Any?, specifier: String): String? {
        val importsObject = importsValue as? JSONObject ?: return null
        val entry = if (importsObject.has(specifier)) {
            importsObject.opt(specifier) to null
        } else {
            selectPackagePatternEntry(importsObject, specifier)
        } ?: return null
        val target = selectPackageExportConditionTarget(entry.first) ?: return null
        return applyPackagePatternTarget(target, entry.second)
    }

    private fun selectPackagePatternEntry(packageObject: JSONObject, key: String): Pair<Any?, String?>? {
        val keys = packageObject.keys()
        while (keys.hasNext()) {
            val patternKey = keys.next()
            val replacement = packagePatternReplacement(patternKey, key) ?: continue
            return packageObject.opt(patternKey) to replacement
        }
        return null
    }

    private fun packagePatternReplacement(patternKey: String, key: String): String? {
        val wildcardCount = patternKey.count { it == '*' }
        if (wildcardCount == 0) {
            return null
        }
        val starIndex = patternKey.indexOf('*')
        val prefix = patternKey.substring(0, starIndex)
        if (!key.startsWith(prefix)) {
            return null
        }
        if (wildcardCount != 1) {
            return null
        }
        val suffix = patternKey.substring(starIndex + 1)
        if (suffix.isNotBlank() && !key.endsWith(suffix)) {
            return null
        }
        if (key.length < prefix.length + suffix.length) {
            return null
        }
        return key.substring(prefix.length, key.length - suffix.length).takeIf { replacement ->
            replacement.isNotBlank() &&
                    replacement.indexOf('\u0000') < 0 &&
                    !File(replacement).isAbsolute &&
                    replacement.split("/").none { it.isBlank() || it == "." || it == ".." }
        }
    }

    private fun applyPackagePatternTarget(target: String, replacement: String?): String? {
        if (replacement == null) {
            return target
        }
        if (target.count { it == '*' } != 1) {
            return null
        }
        return target.replace("*", replacement)
    }

    private fun normalizeEmbeddedSource(script: String): String {
        if (script.startsWith(SHEBANG_PREFIX)) {
            return SHEBANG_COMMENT_PREFIX + script.substring(SHEBANG_PREFIX.length)
        }
        if (script.startsWith(BOM_SHEBANG_PREFIX)) {
            return BOM + SHEBANG_COMMENT_PREFIX + script.substring(BOM_SHEBANG_PREFIX.length)
        }
        return script
    }

    private fun sourceName(source: JavaScriptSource): String {
        source.semanticFileFromOverriddenFullPath()?.normalizedAbsolutePath()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        (source as? JavaScriptFileSource)?.file?.normalizedAbsolutePath()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return source.fullPath.takeIf { it.isNotBlank() }
            ?: source.name.takeIf { it.isNotBlank() }
            ?: DEFAULT_SOURCE_NAME
    }

    private fun workingDirectory(source: JavaScriptSource): String? {
        source.semanticFileFromOverriddenFullPath()
            ?.parentFile
            ?.normalizedAbsolutePath()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        cwd().takeUnless { it.isNullOrBlank() }?.let { return it }
        val file = (source as? JavaScriptFileSource)?.file
        file?.parentFile?.normalizedAbsolutePath()?.takeIf { it.isNotBlank() }?.let { return it }
        return source.fullPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).parentFile?.absolutePath }.getOrNull() }
    }

    private fun sandboxRoot(workingDirectory: String?): String? {
        val cwd = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).normalizedCanonicalFile() }
            ?: return null
        val defaultRoot = WorkingDirectoryUtils.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it).normalizedCanonicalFile() }
        return if (defaultRoot != null && cwd.isInsideOrSame(defaultRoot)) {
            defaultRoot.normalizedAbsolutePath()
        } else {
            cwd.normalizedAbsolutePath()
        }
    }

    private fun JavaScriptSource.semanticFileFromOverriddenFullPath(): File? {
        return overriddenFullPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isAbsolute }
    }

    private fun newEmbeddedExecutionId(): String {
        val engineId = id.takeIf { it != org.autojs.autojs.execution.ScriptExecution.NO_ID }
            ?.toString()
            ?: "no-id"
        return "engine-$engineId-${UUID.randomUUID()}"
    }

    private fun elapsedSince(startedAt: Long): Long {
        return (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
    }

    private fun File.normalizedAbsolutePath(): String {
        return runCatching { canonicalPath }.getOrElse { absolutePath }
    }

    private fun File.normalizedCanonicalFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }

    private fun File.strictCanonicalFileOrNull(): File? {
        return try {
            canonicalFile
        } catch (_: Exception) {
            null
        }
    }

    private fun File.fileIdentityOrNull(noFollow: Boolean = false): FileIdentity? {
        return try {
            val stat = if (noFollow) Os.lstat(path) else Os.stat(path)
            if (!OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISDIR(stat.st_mode)) {
                return null
            }
            FileIdentity(device = stat.st_dev, inode = stat.st_ino)
        } catch (_: Exception) {
            null
        }
    }

    private fun File.isInsideOrSame(root: File): Boolean {
        val rootPath = root.normalizedAbsolutePath().trimEnd(File.separatorChar)
        val path = normalizedAbsolutePath()
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    private fun File.relativePathInside(root: File): String {
        val rootPath = root.normalizedAbsolutePath().trimEnd(File.separatorChar)
        val path = normalizedAbsolutePath()
        return when {
            path == rootPath -> ""
            path.startsWith(rootPath + File.separator) -> path.substring(rootPath.length + 1)
            else -> name
        }.replace(File.separatorChar, '/')
    }

    private fun String.isRelativeCommonJsModuleName(): Boolean {
        return this == "." ||
                this == ".." ||
                startsWith("./") ||
                startsWith("../")
    }

    private fun String.isCommonJsPackageImportSpecifier(): Boolean {
        return startsWith("#") && this != "#" && !startsWith("#/") && indexOf('\u0000') < 0
    }

    private fun String.isRuntimeProvidedCommonJsSpecifier(): Boolean {
        return this in COMMON_JS_BUILTIN_MODULE_NAMES ||
                startsWith("node:") ||
                COMMON_JS_PROTOCOL_SPECIFIER_PATTERN.containsMatchIn(this)
    }

    private fun String.toCommonJsPackageSpecifier(): CommonJsPackageSpecifier? {
        if (isBlank() ||
            this in COMMON_JS_BUILTIN_MODULE_NAMES ||
            startsWith("node:") ||
            startsWith("#") ||
            COMMON_JS_PROTOCOL_SPECIFIER_PATTERN.containsMatchIn(this) ||
            indexOf('\u0000') >= 0 ||
            File(this).isAbsolute
        ) {
            return null
        }
        val parts = split("/")
        if (parts.any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }
        return if (parts.first().startsWith("@")) {
            if (parts.size < 2 || parts[0].length <= 1 || parts[1].startsWith("@")) {
                null
            } else {
                CommonJsPackageSpecifier(
                    packageName = parts[0] + "/" + parts[1],
                    subpath = parts.drop(2).joinToString("/"),
                )
            }
        } else {
            if (parts.first().startsWith(".")) {
                null
            } else {
                CommonJsPackageSpecifier(
                    packageName = parts.first(),
                    subpath = parts.drop(1).joinToString("/"),
                )
            }
        }
    }

    private companion object {
        private const val TAG = "NativeNodeEmbeddedJavaScriptEngine"
        private const val SCRIPT_OUTPUT_LOG_LINE_MAX_CHARS = 1800
        const val DEFAULT_SOURCE_NAME = "<embedded-user-script.js>"
        const val BOM = "\uFEFF"
        const val SHEBANG_PREFIX = "#!"
        const val BOM_SHEBANG_PREFIX = "$BOM$SHEBANG_PREFIX"
        const val SHEBANG_COMMENT_PREFIX = "//!"
        const val JS_EXTENSION = "js"
        const val CJS_EXTENSION = "cjs"
        const val MJS_EXTENSION = "mjs"
        const val TS_EXTENSION = "ts"
        const val CTS_EXTENSION = "cts"
        const val MTS_EXTENSION = "mts"
        const val JSON_EXTENSION = "json"
        const val NODE_PLUGIN_RELEASE_FLAVOR = "nodePlugin"
        const val PACKAGE_JSON = "package.json"
        const val PACKAGE_JSON_NAME = "name"
        const val PACKAGE_JSON_MAIN = "main"
        const val PACKAGE_JSON_EXPORTS = "exports"
        const val PACKAGE_JSON_IMPORTS = "imports"
        const val NODE_MODULES = "node_modules"
        const val ENCRYPTED_SCAN_REASON_ESM_ENTRY = "esm_entry"
        const val ENCRYPTED_SCAN_REASON_SOURCE_LEVEL_ESM_GRAPH = "source_level_esm_graph"
        const val ENCRYPTED_SCAN_REASON_DYNAMIC_IMPORT = "dynamic_import"
        const val ENCRYPTED_SCAN_REASON_CREATE_REQUIRE = "create_require"
        const val ENCRYPTED_SCAN_REASON_UNCLASSIFIED_LOADER = "unclassified_loader"
        const val ENCRYPTED_SCAN_REASON_DYNAMIC_REQUIRE = "dynamic_require"
        const val ENCRYPTED_SCAN_REASON_DYNAMIC_CODE = "dynamic_code"
        const val ENCRYPTED_SCAN_REASON_UNRESOLVED_STATIC_REQUIRE = "unresolved_static_require"
        const val ENCRYPTED_SCAN_REASON_COMMON_JS_PRELOAD_INCOMPLETE = "commonjs_preload_incomplete"
        const val ENCRYPTED_SCAN_REASON_CONSERVATIVE_SOURCE = "conservative_source"
        const val ENCRYPTED_SCAN_REASON_TRIVIAL_MODULE_FREE = "trivial_module_free"
        const val ENCRYPTED_SCAN_STATUS_SKIPPED = "skipped"
        const val ENCRYPTED_SCAN_STATUS_COMPLETED = "completed"
        const val ENCRYPTED_SCAN_STATUS_WORKING_DIRECTORY_UNAVAILABLE = "working_directory_unavailable"
        const val ENCRYPTED_SCAN_STATUS_SOURCE_FILE_UNAVAILABLE = "source_file_unavailable"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_WALK_FAILURE = "incomplete_walk_failure"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_TRAVERSAL_LIMIT = "incomplete_traversal_limit"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_CANDIDATE_LIMIT = "incomplete_candidate_limit"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_IDENTITY_FAILURE = "incomplete_identity_failure"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_READ_FAILURE = "incomplete_read_failure"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_DECRYPT_FAILURE = "incomplete_decrypt_failure"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_MODULE_SIZE_BUDGET = "incomplete_module_size_budget"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_COUNT_BUDGET = "incomplete_output_count_budget"
        const val ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_BYTES_BUDGET = "incomplete_output_bytes_budget"
        const val RUNTIME_MODULE_SOURCE_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES
        const val RUNTIME_MODULE_SOURCES_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES
        const val RUNTIME_MODULE_SOURCE_MAX_COUNT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT
        const val COMMON_JS_PRELOAD_MAX_DEPTH = RUNTIME_MODULE_SOURCE_MAX_COUNT + 1
        const val COMMON_JS_PRELOAD_HARD_MODULE_COUNT = RUNTIME_MODULE_SOURCE_MAX_COUNT + 1
        const val COMMON_JS_PRELOAD_INCOMPLETE_DEPTH_LIMIT = "depth_limit"
        const val COMMON_JS_PRELOAD_INCOMPLETE_MODULE_COUNT_LIMIT = "module_count_limit"
        const val COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_SIZE_LIMIT = "source_size_limit"
        const val COMMON_JS_PRELOAD_INCOMPLETE_AGGREGATE_SIZE_LIMIT = "aggregate_size_limit"
        const val COMMON_JS_PRELOAD_INCOMPLETE_SOURCE_READ_FAILURE = "source_read_failure"
        const val COMMON_JS_PRELOAD_INCOMPLETE_ROOT_IDENTITY_FAILURE = "root_identity_failure"
        const val ENCRYPTED_SCAN_MAX_EXAMINED_PATHS = 16_384
        const val ENCRYPTED_SCAN_MAX_CANDIDATES = 8_192
        const val TRIVIAL_MODULE_FREE_ENTRY_MAX_CHARS = 4_096
        const val ENCRYPTED_SOURCE_CIPHER_BLOCK_SIZE_BYTES = 16L
        const val ENCRYPTED_SOURCE_READ_BUFFER_SIZE = 8 * 1024
        val COMMON_JS_MODULE_EXTENSIONS = NodeRuntimeCapabilities.commonJsModuleExtensions
        val RUNTIME_MODULE_SOURCE_EXTENSIONS = COMMON_JS_MODULE_EXTENSIONS + MJS_EXTENSION + MTS_EXTENSION
        val COMMON_JS_RECURSIVE_SOURCE_EXTENSIONS = NodeRuntimeCapabilities.commonJsRecursiveSourceExtensions
        val PACKAGE_JSON_EXPORT_CONDITIONS = NodeRuntimeCapabilities.packageJsonExportConditions
        val COMMON_JS_PROTOCOL_SPECIFIER_PATTERN = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
        val LOADER_AUTHORITY_MODULE_NAMES = setOf(
            "module", "node:module", "vm", "node:vm", "process", "node:process",
        )
        val TRIVIAL_MODULE_FREE_ENTRY_PATTERN = Regex(
            """^\s*(?:(?:[\"']nodejs[\"'])\s*;\s*)?console\s*\.\s*(?:log|debug|info|warn|error)\s*\(\s*(?:(?:-?(?:\d+(?:\.\d+)?|\.\d+)|true|false|null|undefined|[\"'](?:\\.|[^\"'\\\r\n])*[\"'])(?:\s*,\s*(?:-?(?:\d+(?:\.\d+)?|\.\d+)|true|false|null|undefined|[\"'](?:\\.|[^\"'\\\r\n])*[\"']))*)?\s*\)\s*;?\s*$""",
        )
        val COMMON_JS_BUILTIN_MODULE_NAMES = buildSet {
            addAll(NodeRuntimeCapabilities.commonJsBuiltinModuleNames)
            addAll(NodeRuntimeCapabilities.limitedBuiltinModuleNames)
            addAll(NodeRuntimeCapabilities.disabledBuiltinModuleNames)
            addAll(NodeRuntimeCapabilities.networkBuiltinModuleNames)
        }
        val SAFE_COMMON_JS_MODULE_PROPERTIES = setOf(
            "exports",
            "filename",
            "id",
            "loaded",
        )

        private fun analyzeCommonJsSourceLoaders(source: String): CommonJsSourceLoaderAnalysis {
            if (source.isEmpty()) {
                return CommonJsSourceLoaderAnalysis.empty()
            }
            val references = mutableListOf<CommonJsRequireReference>()
            var sourceLevelEsmSyntaxCount = 0
            var dynamicImportCount = 0
            var createRequireReferenceCount = 0
            var unclassifiedLoaderReferenceCount = 0
            var dynamicCodeReferenceCount = 0
            var index = 0
            while (index < source.length) {
                val ch = source[index]
                index = when {
                    ch == '"' || ch == '\'' -> {
                        val literal = parseJavaScriptStringLiteral(source, index)
                        when (literal?.value) {
                            "constructor" -> dynamicCodeReferenceCount += 1
                            "require", "createRequire", "eval", "Function", "mainModule", "getBuiltinModule" ->
                                unclassifiedLoaderReferenceCount += 1
                        }
                        literal?.endIndex ?: skipQuotedJavaScriptString(source, index)
                    }
                    ch == '`' -> {
                        val endIndex = skipJavaScriptTemplateLiteral(source, index)
                        unclassifiedLoaderReferenceCount += 1
                        endIndex
                    }
                    ch == '/' && index + 1 < source.length && source[index + 1] == '/' -> skipJavaScriptLineComment(source, index)
                    ch == '/' && index + 1 < source.length && source[index + 1] == '*' -> skipJavaScriptBlockComment(source, index)
                    ch == '/' && looksLikeJavaScriptRegexLiteral(source, index) -> {
                        unclassifiedLoaderReferenceCount += 1
                        skipJavaScriptRegexLiteral(source, index)
                    }
                    ch.isJavaScriptIdentifierStart() -> {
                        val identifier = readJavaScriptIdentifier(source, index)
                        val hasBoundary = isJavaScriptRequireBoundaryBefore(source, index)
                        when (identifier.name) {
                            "require" -> {
                                val parsed = if (hasBoundary) {
                                    parseCommonJsRequireCall(source, identifier.endIndex)
                                } else {
                                    null
                                }
                                if (parsed != null) {
                                    references += CommonJsRequireReference(parsed.moduleName)
                                    if (parsed.moduleName in LOADER_AUTHORITY_MODULE_NAMES) {
                                        unclassifiedLoaderReferenceCount += 1
                                    }
                                    parsed.endIndex.coerceAtLeast(identifier.endIndex)
                                } else {
                                    unclassifiedLoaderReferenceCount += 1
                                    identifier.endIndex
                                }
                            }
                            "module" -> {
                                val parsed = if (hasBoundary) {
                                    parseCommonJsModuleRequireCall(source, identifier.endIndex)
                                } else {
                                    null
                                }
                                if (parsed != null) {
                                    references += CommonJsRequireReference(parsed.moduleName)
                                    if (parsed.moduleName in LOADER_AUTHORITY_MODULE_NAMES) {
                                        unclassifiedLoaderReferenceCount += 1
                                    }
                                    parsed.endIndex.coerceAtLeast(identifier.endIndex)
                                } else {
                                    if (hasBoundary && hasPotentialModuleLoaderAccess(source, identifier.endIndex)) {
                                        unclassifiedLoaderReferenceCount += 1
                                    }
                                    identifier.endIndex
                                }
                            }
                            "createRequire" -> {
                                createRequireReferenceCount += 1
                                identifier.endIndex
                            }
                            "import" -> {
                                if (hasBoundary) {
                                    val nextIndex = skipJavaScriptWhitespaceAndComments(source, identifier.endIndex)
                                    when {
                                        nextIndex < source.length && source[nextIndex] == '(' -> dynamicImportCount += 1
                                        isSourceLevelEsmImport(source, nextIndex) -> sourceLevelEsmSyntaxCount += 1
                                    }
                                }
                                identifier.endIndex
                            }
                            "export" -> {
                                if (hasBoundary) {
                                    val nextIndex = skipJavaScriptWhitespaceAndComments(source, identifier.endIndex)
                                    if (isSourceLevelEsmExport(source, nextIndex)) {
                                        sourceLevelEsmSyntaxCount += 1
                                    }
                                }
                                identifier.endIndex
                            }
                            "eval", "Function" -> {
                                dynamicCodeReferenceCount += 1
                                identifier.endIndex
                            }
                            "constructor" -> {
                                dynamicCodeReferenceCount += 1
                                identifier.endIndex
                            }
                            "globalThis", "global", "Reflect", "this", "process", "mainModule", "getBuiltinModule" -> {
                                unclassifiedLoaderReferenceCount += 1
                                identifier.endIndex
                            }
                            "arguments" -> {
                                if (hasBoundary) {
                                    unclassifiedLoaderReferenceCount += 1
                                }
                                identifier.endIndex
                            }
                            else -> identifier.endIndex
                        }
                    }
                    else -> index + 1
                }
            }
            return CommonJsSourceLoaderAnalysis(
                requireReferences = references,
                sourceLevelEsmSyntaxCount = sourceLevelEsmSyntaxCount,
                dynamicImportCount = dynamicImportCount,
                createRequireReferenceCount = createRequireReferenceCount,
                unclassifiedLoaderReferenceCount = unclassifiedLoaderReferenceCount,
                dynamicCodeReferenceCount = dynamicCodeReferenceCount,
                trivialModuleFreeEntry = source.length <= TRIVIAL_MODULE_FREE_ENTRY_MAX_CHARS &&
                        TRIVIAL_MODULE_FREE_ENTRY_PATTERN.matches(source),
            )
        }

        private fun hasPotentialModuleLoaderAccess(source: String, moduleIdentifierEnd: Int): Boolean {
            var index = skipJavaScriptWhitespaceAndComments(source, moduleIdentifierEnd)
            if (index >= source.length) {
                return true
            }
            if (source[index] == '.') {
                index = skipJavaScriptWhitespaceAndComments(source, index + 1)
                if (index >= source.length || !source[index].isJavaScriptIdentifierStart()) {
                    return true
                }
                return readJavaScriptIdentifier(source, index).name !in SAFE_COMMON_JS_MODULE_PROPERTIES
            }
            return source[index] != ':'
        }

        private fun isSourceLevelEsmImport(source: String, nextIndex: Int): Boolean {
            if (nextIndex >= source.length) {
                return false
            }
            val next = source[nextIndex]
            if (next == '"' || next == '\'' || next == '{' || next == '*') {
                return true
            }
            if (next == '.') {
                val afterDot = skipJavaScriptWhitespaceAndComments(source, nextIndex + 1)
                return afterDot < source.length &&
                        source.startsWith("meta", afterDot) &&
                        (afterDot + 4 >= source.length || !source[afterDot + 4].isJavaScriptIdentifierPart())
            }
            return next.isJavaScriptIdentifierStart()
        }

        private fun isSourceLevelEsmExport(source: String, nextIndex: Int): Boolean {
            if (nextIndex >= source.length) {
                return false
            }
            val next = source[nextIndex]
            return next == '{' || next == '*' || next.isJavaScriptIdentifierStart()
        }

        private fun parseCommonJsModuleRequireCall(source: String, moduleIdentifierEnd: Int): CommonJsRequireParseResult? {
            var index = skipJavaScriptWhitespaceAndComments(source, moduleIdentifierEnd)
            if (index >= source.length || source[index] != '.') {
                return null
            }
            index = skipJavaScriptWhitespaceAndComments(source, index + 1)
            if (index >= source.length || !source[index].isJavaScriptIdentifierStart()) {
                return null
            }
            val requireIdentifier = readJavaScriptIdentifier(source, index)
            if (requireIdentifier.name != "require") {
                return null
            }
            return parseCommonJsRequireCall(source, requireIdentifier.endIndex)
        }

        private fun parseCommonJsRequireCall(source: String, requireIdentifierEnd: Int): CommonJsRequireParseResult? {
            var index = skipJavaScriptWhitespaceAndComments(source, requireIdentifierEnd)
            if (index >= source.length || source[index] != '(') {
                return null
            }
            index = skipJavaScriptWhitespaceAndComments(source, index + 1)
            val literal = parseJavaScriptStringLiteral(source, index) ?: return CommonJsRequireParseResult(null, index)
            index = skipJavaScriptWhitespaceAndComments(source, literal.endIndex)
            if (index >= source.length || source[index] != ')') {
                return CommonJsRequireParseResult(null, literal.endIndex)
            }
            return CommonJsRequireParseResult(literal.value, index + 1)
        }

        private fun skipJavaScriptWhitespaceAndComments(source: String, startIndex: Int): Int {
            var index = startIndex
            while (index < source.length) {
                val ch = source[index]
                index = when {
                    ch.isWhitespace() -> index + 1
                    ch == '/' && index + 1 < source.length && source[index + 1] == '/' -> skipJavaScriptLineComment(source, index)
                    ch == '/' && index + 1 < source.length && source[index + 1] == '*' -> skipJavaScriptBlockComment(source, index)
                    else -> return index
                }
            }
            return index
        }

        private fun skipQuotedJavaScriptString(source: String, startIndex: Int): Int {
            val quote = source[startIndex]
            var index = startIndex + 1
            while (index < source.length) {
                val ch = source[index]
                when {
                    ch == '\\' -> index = (index + 2).coerceAtMost(source.length)
                    ch == quote -> return index + 1
                    else -> index += 1
                }
            }
            return source.length
        }

        private fun parseJavaScriptStringLiteral(source: String, startIndex: Int): JavaScriptStringLiteral? {
            if (startIndex >= source.length) {
                return null
            }
            val quote = source[startIndex]
            if (quote != '"' && quote != '\'') {
                return null
            }
            val value = StringBuilder()
            var index = startIndex + 1
            while (index < source.length) {
                val ch = source[index]
                when {
                    ch == '\\' -> {
                        if (index + 1 >= source.length) {
                            return null
                        }
                        value.append(source[index + 1])
                        index += 2
                    }
                    ch == quote -> return JavaScriptStringLiteral(value.toString(), index + 1)
                    else -> {
                        value.append(ch)
                        index += 1
                    }
                }
            }
            return null
        }

        private fun skipJavaScriptTemplateLiteral(source: String, startIndex: Int): Int {
            var index = startIndex + 1
            while (index < source.length) {
                val ch = source[index]
                index = when {
                    ch == '\\' -> (index + 2).coerceAtMost(source.length)
                    ch == '`' -> return index + 1
                    else -> index + 1
                }
            }
            return source.length
        }

        private fun skipJavaScriptLineComment(source: String, startIndex: Int): Int {
            var index = startIndex + 2
            while (index < source.length && source[index] != '\n' && source[index] != '\r') {
                index += 1
            }
            return index
        }

        private fun skipJavaScriptBlockComment(source: String, startIndex: Int): Int {
            var index = startIndex + 2
            while (index + 1 < source.length) {
                if (source[index] == '*' && source[index + 1] == '/') {
                    return index + 2
                }
                index += 1
            }
            return source.length
        }

        private fun skipJavaScriptRegexLiteral(source: String, startIndex: Int): Int {
            var index = startIndex + 1
            var inCharacterClass = false
            while (index < source.length) {
                val ch = source[index]
                when {
                    ch == '\\' -> index = (index + 2).coerceAtMost(source.length)
                    ch == '[' -> {
                        inCharacterClass = true
                        index += 1
                    }
                    ch == ']' -> {
                        inCharacterClass = false
                        index += 1
                    }
                    ch == '/' && !inCharacterClass -> {
                        index += 1
                        while (index < source.length && source[index].isJavaScriptIdentifierPart()) {
                            index += 1
                        }
                        return index
                    }
                    ch == '\n' || ch == '\r' -> return index
                    else -> index += 1
                }
            }
            return source.length
        }

        private fun looksLikeJavaScriptRegexLiteral(source: String, slashIndex: Int): Boolean {
            val previous = previousJavaScriptSignificantToken(source, slashIndex) ?: return true
            if (previous.text in JAVASCRIPT_REGEX_PREFIX_KEYWORDS) {
                return true
            }
            return previous.text.length == 1 && previous.text[0] in JAVASCRIPT_REGEX_PREFIX_CHARS
        }

        private fun previousJavaScriptSignificantToken(source: String, beforeIndex: Int): JavaScriptToken? {
            var index = beforeIndex - 1
            while (index >= 0 && source[index].isWhitespace()) {
                index -= 1
            }
            if (index < 0) {
                return null
            }
            val ch = source[index]
            if (ch.isJavaScriptIdentifierPart()) {
                val end = index + 1
                while (index >= 0 && source[index].isJavaScriptIdentifierPart()) {
                    index -= 1
                }
                return JavaScriptToken(source.substring(index + 1, end))
            }
            return JavaScriptToken(ch.toString())
        }

        private fun readJavaScriptIdentifier(source: String, startIndex: Int): JavaScriptIdentifier {
            var index = startIndex + 1
            while (index < source.length && source[index].isJavaScriptIdentifierPart()) {
                index += 1
            }
            return JavaScriptIdentifier(source.substring(startIndex, index), index)
        }

        private fun isJavaScriptRequireBoundaryBefore(source: String, index: Int): Boolean {
            if (index <= 0) {
                return true
            }
            val previous = source[index - 1]
            return !previous.isJavaScriptIdentifierPart() && previous != '.'
        }

        private fun Char.isJavaScriptIdentifierStart(): Boolean {
            return this == '_' || this == '$' || isLetter()
        }

        private fun Char.isJavaScriptIdentifierPart(): Boolean {
            return isJavaScriptIdentifierStart() || isDigit()
        }

        private val JAVASCRIPT_REGEX_PREFIX_CHARS = setOf(
            '(', '{', '[', '=', ':', ',', ';', '!', '?', '&', '|', '+', '-', '*', '~', '^', '<', '>',
        )
        private val JAVASCRIPT_REGEX_PREFIX_KEYWORDS = setOf(
            "return", "throw", "case", "delete", "void", "typeof", "yield", "await", "else", "do", "in", "of",
        )
    }

    data class CommonJsPreloadDiagnostics(
        val sourceName: String,
        val workingDirectory: String?,
        val rootSourceBytes: Int,
        val moduleCount: Int,
        val packageJsonCount: Int,
        val jsonModuleCount: Int,
        val totalSourceBytes: Int,
        val maxDepth: Int,
        val duplicateSkippedCount: Int,
        val staticRequireCount: Int,
        val dynamicRequireIgnoredCount: Int,
        val unresolvedStaticRequireCount: Int,
        val sourceLevelEsmSyntaxCount: Int,
        val dynamicImportCount: Int,
        val createRequireReferenceCount: Int,
        val unclassifiedLoaderReferenceCount: Int,
        val dynamicCodeReferenceCount: Int,
        val trivialModuleFreeEntry: Boolean,
        val complete: Boolean,
        val incompleteReason: String?,
        val sourceReadFailureCount: Int,
        val sourceSizeLimitRejectedCount: Int,
        val aggregateSizeLimitRejectedCount: Int,
        val moduleCountLimitRejectedCount: Int,
        val depthLimitRejectedCount: Int,
        val moduleCountLimit: Int,
        val sourceBytesLimit: Int,
        val aggregateSourceBytesLimit: Int,
        val depthLimit: Int,
        val elapsedMs: Long,
        val moduleSourceNames: List<String>,
    ) {

        companion object {
            fun empty(): CommonJsPreloadDiagnostics = CommonJsPreloadDiagnostics(
                sourceName = DEFAULT_SOURCE_NAME,
                workingDirectory = null,
                rootSourceBytes = 0,
                moduleCount = 0,
                packageJsonCount = 0,
                jsonModuleCount = 0,
                totalSourceBytes = 0,
                maxDepth = 0,
                duplicateSkippedCount = 0,
                staticRequireCount = 0,
                dynamicRequireIgnoredCount = 0,
                unresolvedStaticRequireCount = 0,
                sourceLevelEsmSyntaxCount = 0,
                dynamicImportCount = 0,
                createRequireReferenceCount = 0,
                unclassifiedLoaderReferenceCount = 0,
                dynamicCodeReferenceCount = 0,
                trivialModuleFreeEntry = false,
                complete = true,
                incompleteReason = null,
                sourceReadFailureCount = 0,
                sourceSizeLimitRejectedCount = 0,
                aggregateSizeLimitRejectedCount = 0,
                moduleCountLimitRejectedCount = 0,
                depthLimitRejectedCount = 0,
                moduleCountLimit = RUNTIME_MODULE_SOURCE_MAX_COUNT,
                sourceBytesLimit = RUNTIME_MODULE_SOURCE_MAX_BYTES,
                aggregateSourceBytesLimit = RUNTIME_MODULE_SOURCES_MAX_BYTES,
                depthLimit = COMMON_JS_PRELOAD_MAX_DEPTH,
                elapsedMs = 0L,
                moduleSourceNames = emptyList(),
            )
        }
    }

    data class RequestPreparationDiagnostics(
        val prepared: Boolean = true,
        val sourceName: String,
        val workingDirectory: String?,
        val isEsmEntry: Boolean,
        val elapsedMs: Long,
        val commonJs: CommonJsPreloadDiagnostics,
        val encryptedRuntimeModuleScan: EncryptedRuntimeModuleScanDiagnostics,
    ) {

        internal fun toNativeValues(): Map<String, String> = linkedMapOf(
            "embedded_script.engine_request_preparation.prepared" to prepared.toString(),
            "embedded_script.engine_request_preparation.source_name" to sourceName,
            "embedded_script.engine_request_preparation.working_directory" to workingDirectory.orEmpty(),
            "embedded_script.engine_request_preparation.esm_entry" to isEsmEntry.toString(),
            "timing.engine_request_preparation.ms" to elapsedMs.toString(),
            "timing.engine_commonjs_preload.ms" to commonJs.elapsedMs.toString(),
            "embedded_script.engine_commonjs_preload.module_count" to commonJs.moduleCount.toString(),
            "embedded_script.engine_commonjs_preload.static_require_count" to commonJs.staticRequireCount.toString(),
            "embedded_script.engine_commonjs_preload.dynamic_require_count" to commonJs.dynamicRequireIgnoredCount.toString(),
            "embedded_script.engine_commonjs_preload.unresolved_static_require_count" to
                    commonJs.unresolvedStaticRequireCount.toString(),
            "embedded_script.engine_commonjs_preload.source_level_esm_count" to
                    commonJs.sourceLevelEsmSyntaxCount.toString(),
            "embedded_script.engine_commonjs_preload.dynamic_import_count" to commonJs.dynamicImportCount.toString(),
            "embedded_script.engine_commonjs_preload.create_require_count" to
                    commonJs.createRequireReferenceCount.toString(),
            "embedded_script.engine_commonjs_preload.unclassified_loader_count" to
                    commonJs.unclassifiedLoaderReferenceCount.toString(),
            "embedded_script.engine_commonjs_preload.dynamic_code_count" to
                    commonJs.dynamicCodeReferenceCount.toString(),
            "embedded_script.engine_commonjs_preload.trivial_module_free_entry" to
                    commonJs.trivialModuleFreeEntry.toString(),
            "embedded_script.engine_commonjs_preload.complete" to commonJs.complete.toString(),
            "embedded_script.engine_commonjs_preload.incomplete_reason" to commonJs.incompleteReason.orEmpty(),
            "embedded_script.engine_commonjs_preload.source_read_failure_count" to
                    commonJs.sourceReadFailureCount.toString(),
            "embedded_script.engine_commonjs_preload.source_size_limit_rejected_count" to
                    commonJs.sourceSizeLimitRejectedCount.toString(),
            "embedded_script.engine_commonjs_preload.aggregate_size_limit_rejected_count" to
                    commonJs.aggregateSizeLimitRejectedCount.toString(),
            "embedded_script.engine_commonjs_preload.module_count_limit_rejected_count" to
                    commonJs.moduleCountLimitRejectedCount.toString(),
            "embedded_script.engine_commonjs_preload.depth_limit_rejected_count" to
                    commonJs.depthLimitRejectedCount.toString(),
            "embedded_script.engine_commonjs_preload.module_count_limit" to commonJs.moduleCountLimit.toString(),
            "embedded_script.engine_commonjs_preload.source_bytes_limit" to commonJs.sourceBytesLimit.toString(),
            "embedded_script.engine_commonjs_preload.aggregate_source_bytes_limit" to
                    commonJs.aggregateSourceBytesLimit.toString(),
            "embedded_script.engine_commonjs_preload.depth_limit" to commonJs.depthLimit.toString(),
        ) + encryptedRuntimeModuleScan.toNativeValues()

        companion object {
            fun empty(): RequestPreparationDiagnostics = RequestPreparationDiagnostics(
                prepared = false,
                sourceName = DEFAULT_SOURCE_NAME,
                workingDirectory = null,
                isEsmEntry = false,
                elapsedMs = 0L,
                commonJs = CommonJsPreloadDiagnostics.empty(),
                encryptedRuntimeModuleScan = EncryptedRuntimeModuleScanDiagnostics.empty(),
            )
        }
    }

    data class EncryptedRuntimeModuleScanDiagnostics(
        val required: Boolean,
        val performed: Boolean,
        val decision: String,
        val status: String,
        val elapsedMs: Long,
        val examinedPathLimit: Int,
        val candidateFileLimit: Int,
        val examinedPathCount: Int,
        val walkedFileCount: Int,
        val candidateFileCount: Int,
        val headerProbeCount: Int,
        val headerBytesRead: Long,
        val headerReadFailureCount: Int,
        val fullFileReadCount: Int,
        val fullFileBytesRead: Long,
        val fullFileReadFailureCount: Int,
        val prunedDirectoryCount: Int,
        val outsidePathRejectedCount: Int,
        val duplicateCanonicalFileCount: Int,
        val walkFailureCount: Int,
        val oversizedCiphertextCount: Int,
        val cleartextSizeLimitRejectedCount: Int,
        val decryptFailureCount: Int,
        val encryptedModuleCount: Int,
        val retainedEncryptedModuleCount: Int,
        val encryptedModuleSourceBytes: Long,
        val encryptedModuleBudgetRejectedCount: Int,
        val traversalLimitReached: Boolean,
        val candidateLimitReached: Boolean,
        val identityReadFailureCount: Int,
        val identityMismatchCount: Int,
        val outputCountBudgetExhausted: Boolean,
        val outputBytesBudgetExhausted: Boolean,
    ) {

        internal fun toNativeValues(): Map<String, String> = linkedMapOf(
            "embedded_script.engine_encrypted_runtime_scan.required" to required.toString(),
            "embedded_script.engine_encrypted_runtime_scan.performed" to performed.toString(),
            "embedded_script.engine_encrypted_runtime_scan.decision" to decision,
            "embedded_script.engine_encrypted_runtime_scan.status" to status,
            "timing.engine_encrypted_runtime_scan.ms" to elapsedMs.toString(),
            "embedded_script.engine_encrypted_runtime_scan.examined_path_limit" to examinedPathLimit.toString(),
            "embedded_script.engine_encrypted_runtime_scan.candidate_file_limit" to candidateFileLimit.toString(),
            "embedded_script.engine_encrypted_runtime_scan.examined_path_count" to examinedPathCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.walked_file_count" to walkedFileCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.candidate_file_count" to candidateFileCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.header_probe_count" to headerProbeCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.header_bytes_read" to headerBytesRead.toString(),
            "embedded_script.engine_encrypted_runtime_scan.header_read_failure_count" to
                    headerReadFailureCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.full_file_read_count" to fullFileReadCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.full_file_bytes_read" to fullFileBytesRead.toString(),
            "embedded_script.engine_encrypted_runtime_scan.full_file_read_failure_count" to
                    fullFileReadFailureCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.pruned_directory_count" to prunedDirectoryCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.outside_path_rejected_count" to
                    outsidePathRejectedCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.duplicate_canonical_file_count" to
                    duplicateCanonicalFileCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.walk_failure_count" to walkFailureCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.oversized_ciphertext_count" to
                    oversizedCiphertextCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.cleartext_size_limit_rejected_count" to
                    cleartextSizeLimitRejectedCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.decrypt_failure_count" to decryptFailureCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.encrypted_module_count" to encryptedModuleCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.retained_encrypted_module_count" to
                    retainedEncryptedModuleCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.encrypted_module_source_bytes" to
                    encryptedModuleSourceBytes.toString(),
            "embedded_script.engine_encrypted_runtime_scan.budget_rejected_count" to
                    encryptedModuleBudgetRejectedCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.traversal_limit_reached" to
                    traversalLimitReached.toString(),
            "embedded_script.engine_encrypted_runtime_scan.candidate_limit_reached" to
                    candidateLimitReached.toString(),
            "embedded_script.engine_encrypted_runtime_scan.identity_read_failure_count" to
                    identityReadFailureCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.identity_mismatch_count" to
                    identityMismatchCount.toString(),
            "embedded_script.engine_encrypted_runtime_scan.output_count_budget_exhausted" to
                    outputCountBudgetExhausted.toString(),
            "embedded_script.engine_encrypted_runtime_scan.output_bytes_budget_exhausted" to
                    outputBytesBudgetExhausted.toString(),
        )

        companion object {
            fun empty(): EncryptedRuntimeModuleScanDiagnostics = EncryptedRuntimeModuleScanDiagnostics(
                required = false,
                performed = false,
                decision = ENCRYPTED_SCAN_REASON_TRIVIAL_MODULE_FREE,
                status = ENCRYPTED_SCAN_STATUS_SKIPPED,
                elapsedMs = 0L,
                examinedPathLimit = ENCRYPTED_SCAN_MAX_EXAMINED_PATHS,
                candidateFileLimit = ENCRYPTED_SCAN_MAX_CANDIDATES,
                examinedPathCount = 0,
                walkedFileCount = 0,
                candidateFileCount = 0,
                headerProbeCount = 0,
                headerBytesRead = 0L,
                headerReadFailureCount = 0,
                fullFileReadCount = 0,
                fullFileBytesRead = 0L,
                fullFileReadFailureCount = 0,
                prunedDirectoryCount = 0,
                outsidePathRejectedCount = 0,
                duplicateCanonicalFileCount = 0,
                walkFailureCount = 0,
                oversizedCiphertextCount = 0,
                cleartextSizeLimitRejectedCount = 0,
                decryptFailureCount = 0,
                encryptedModuleCount = 0,
                retainedEncryptedModuleCount = 0,
                encryptedModuleSourceBytes = 0L,
                encryptedModuleBudgetRejectedCount = 0,
                traversalLimitReached = false,
                candidateLimitReached = false,
                identityReadFailureCount = 0,
                identityMismatchCount = 0,
                outputCountBudgetExhausted = false,
                outputBytesBudgetExhausted = false,
            )
        }
    }

    private data class CommonJsRequireReference(
        val moduleName: String?,
    )

    private class CommonJsPreloadFrame(
        val parentDirectory: File,
        val depth: Int,
        val loaderAnalysis: CommonJsSourceLoaderAnalysis,
        val recordLoaderAnalysis: Boolean,
        var started: Boolean = false,
        var referenceIndex: Int = 0,
        var moduleFiles: List<File> = emptyList(),
        var moduleFileIndex: Int = 0,
    )

    private data class CommonJsSourceLoaderAnalysis(
        val requireReferences: List<CommonJsRequireReference>,
        val sourceLevelEsmSyntaxCount: Int,
        val dynamicImportCount: Int,
        val createRequireReferenceCount: Int,
        val unclassifiedLoaderReferenceCount: Int,
        val dynamicCodeReferenceCount: Int,
        val trivialModuleFreeEntry: Boolean,
    ) {
        companion object {
            fun empty(): CommonJsSourceLoaderAnalysis = CommonJsSourceLoaderAnalysis(
                requireReferences = emptyList(),
                sourceLevelEsmSyntaxCount = 0,
                dynamicImportCount = 0,
                createRequireReferenceCount = 0,
                unclassifiedLoaderReferenceCount = 0,
                dynamicCodeReferenceCount = 0,
                trivialModuleFreeEntry = false,
            )
        }
    }

    private data class CommonJsRequireParseResult(
        val moduleName: String?,
        val endIndex: Int,
    )

    private data class JavaScriptIdentifier(
        val name: String,
        val endIndex: Int,
    )

    private data class JavaScriptStringLiteral(
        val value: String,
        val endIndex: Int,
    )

    private data class JavaScriptToken(
        val text: String,
    )

    private data class CommonJsPreloadResult(
        val moduleSources: Map<String, String>,
        val diagnostics: CommonJsPreloadDiagnostics,
    )

    private data class EncryptedRuntimeModuleScanDecision(
        val required: Boolean,
        val reason: String,
    )

    private data class EncryptedRuntimeModuleSourcesResult(
        val moduleSources: Map<String, String>,
        val diagnostics: EncryptedRuntimeModuleScanDiagnostics,
    )

    private data class EncryptedRuntimeModuleCandidate(
        val file: File,
        val moduleKey: String,
        val relativePath: String,
        val identity: FileIdentity,
    )

    private data class EncryptedRuntimeScanRoot(
        val canonicalFile: File,
        val canonicalPath: String,
        val identity: FileIdentity,
    ) {
        fun containsCanonicalPath(path: String): Boolean {
            return path == canonicalPath || path.startsWith(canonicalPath + File.separator)
        }

        fun relativePath(path: String): String {
            return when {
                path == canonicalPath -> ""
                path.startsWith(canonicalPath + File.separator) -> path.substring(canonicalPath.length + 1)
                else -> File(path).name
            }.replace(File.separatorChar, '/')
        }

        fun validateCurrentIdentity(stats: EncryptedRuntimeModuleScanStats): Boolean {
            return when (currentIdentityStatus()) {
                DirectoryIdentityStatus.MATCH -> true
                DirectoryIdentityStatus.READ_FAILURE -> {
                    stats.identityReadFailureCount += 1
                    false
                }
                DirectoryIdentityStatus.MISMATCH -> {
                    stats.identityMismatchCount += 1
                    false
                }
            }
        }

        fun validateCurrentIdentity(stats: CommonJsPreloadStats): Boolean {
            val matches = currentIdentityStatus() == DirectoryIdentityStatus.MATCH
            if (!matches) {
                stats.sourceReadFailureCount += 1
            }
            return matches
        }

        private fun currentIdentityStatus(): DirectoryIdentityStatus {
            val current = try {
                Os.lstat(canonicalPath)
            } catch (_: Exception) {
                return DirectoryIdentityStatus.READ_FAILURE
            }
            return if (OsConstants.S_ISDIR(current.st_mode) && identity.matches(current)) {
                DirectoryIdentityStatus.MATCH
            } else {
                DirectoryIdentityStatus.MISMATCH
            }
        }

        companion object {
            fun capture(root: File): EncryptedRuntimeScanRoot? {
                val canonicalFile = try {
                    root.canonicalFile
                } catch (_: Exception) {
                    return null
                }
                val canonicalPath = canonicalFile.absolutePath
                val stat = try {
                    Os.lstat(canonicalPath)
                } catch (_: Exception) {
                    return null
                }
                if (!OsConstants.S_ISDIR(stat.st_mode)) {
                    return null
                }
                return EncryptedRuntimeScanRoot(
                    canonicalFile = canonicalFile,
                    canonicalPath = canonicalPath,
                    identity = FileIdentity(stat.st_dev, stat.st_ino),
                )
            }
        }
    }

    private enum class DirectoryIdentityStatus {
        MATCH,
        READ_FAILURE,
        MISMATCH,
    }

    private data class FileIdentity(
        val device: Long,
        val inode: Long,
    ) {
        fun matches(stat: StructStat): Boolean = device == stat.st_dev && inode == stat.st_ino
    }

    private sealed interface BoundedFileReadResult {
        data class Success(val bytes: ByteArray) : BoundedFileReadResult
        data object SizeLimitExceeded : BoundedFileReadResult
        data object Failure : BoundedFileReadResult
    }

    private sealed interface BoundedSourceReadResult {
        data class Success(val source: String) : BoundedSourceReadResult
        data object SizeLimitExceeded : BoundedSourceReadResult
        data object Failure : BoundedSourceReadResult
    }

    private class EncryptedScanTraversalStoppedException : RuntimeException()

    private data class NodeProjectRunPreparation(
        val entrySource: String,
        val sourceName: String,
        val workingDirectory: String,
        val preload: CommonJsPreloadResult,
        val runtimeModuleSources: Map<String, String>,
        val diagnostics: RequestPreparationDiagnostics,
    ) {
        fun toBridgeRequest(
            timeoutMs: Long,
            spawnDepth: Int = 0,
            executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = entrySource,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                sandboxRoot = workingDirectory,
                executionMode = NodeExecutionModePolicy.normalize(executionMode),
                timeoutMs = timeoutMs,
                moduleSources = preload.moduleSources,
                runtimeModuleSources = runtimeModuleSources,
                spawnDepth = spawnDepth,
            )
        }
    }

    private class CommonJsPreloadStats(private val rootSourceBytes: Int) {

        var packageJsonCount = 0
        var jsonModuleCount = 0
        var totalSourceBytes = 0
        var maxDepth = 0
        var duplicateSkippedCount = 0
        var staticRequireCount = 0
        var dynamicRequireIgnoredCount = 0
        var unresolvedStaticRequireCount = 0
        var sourceLevelEsmSyntaxCount = 0
        var dynamicImportCount = 0
        var createRequireReferenceCount = 0
        var unclassifiedLoaderReferenceCount = 0
        var dynamicCodeReferenceCount = 0
        var trivialModuleFreeEntry = false
        var complete = true
        var incompleteReason: String? = null
        var sourceReadFailureCount = 0
        var sourceSizeLimitRejectedCount = 0
        var aggregateSizeLimitRejectedCount = 0
        var moduleCountLimitRejectedCount = 0
        var depthLimitRejectedCount = 0

        fun markIncomplete(reason: String) {
            complete = false
            if (incompleteReason == null) {
                incompleteReason = reason
            }
        }

        fun recordLoaderAnalysis(analysis: CommonJsSourceLoaderAnalysis) {
            val staticCount = analysis.requireReferences.count { it.moduleName != null }
            staticRequireCount += staticCount
            dynamicRequireIgnoredCount += (analysis.requireReferences.size - staticCount).coerceAtLeast(0)
            sourceLevelEsmSyntaxCount += analysis.sourceLevelEsmSyntaxCount
            dynamicImportCount += analysis.dynamicImportCount
            createRequireReferenceCount += analysis.createRequireReferenceCount
            unclassifiedLoaderReferenceCount += analysis.unclassifiedLoaderReferenceCount
            dynamicCodeReferenceCount += analysis.dynamicCodeReferenceCount
        }

        fun recordPreloadedModule(moduleFile: File, moduleSource: String, depth: Int) {
            totalSourceBytes += moduleSource.toByteArray(Charsets.UTF_8).size
            maxDepth = maxDepth.coerceAtLeast(depth)
            if (moduleFile.name == PACKAGE_JSON) {
                packageJsonCount += 1
            } else if (moduleFile.extension.equals(JSON_EXTENSION, ignoreCase = true)) {
                jsonModuleCount += 1
            }
        }

        fun toDiagnostics(
            sourceName: String,
            workingDirectory: String?,
            moduleSourceNames: List<String>,
            startedAt: Long,
        ): CommonJsPreloadDiagnostics {
            return CommonJsPreloadDiagnostics(
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                rootSourceBytes = rootSourceBytes,
                moduleCount = moduleSourceNames.size,
                packageJsonCount = packageJsonCount,
                jsonModuleCount = jsonModuleCount,
                totalSourceBytes = totalSourceBytes,
                maxDepth = maxDepth,
                duplicateSkippedCount = duplicateSkippedCount,
                staticRequireCount = staticRequireCount,
                dynamicRequireIgnoredCount = dynamicRequireIgnoredCount,
                unresolvedStaticRequireCount = unresolvedStaticRequireCount,
                sourceLevelEsmSyntaxCount = sourceLevelEsmSyntaxCount,
                dynamicImportCount = dynamicImportCount,
                createRequireReferenceCount = createRequireReferenceCount,
                unclassifiedLoaderReferenceCount = unclassifiedLoaderReferenceCount,
                dynamicCodeReferenceCount = dynamicCodeReferenceCount,
                trivialModuleFreeEntry = trivialModuleFreeEntry,
                complete = complete,
                incompleteReason = incompleteReason,
                sourceReadFailureCount = sourceReadFailureCount,
                sourceSizeLimitRejectedCount = sourceSizeLimitRejectedCount,
                aggregateSizeLimitRejectedCount = aggregateSizeLimitRejectedCount,
                moduleCountLimitRejectedCount = moduleCountLimitRejectedCount,
                depthLimitRejectedCount = depthLimitRejectedCount,
                moduleCountLimit = RUNTIME_MODULE_SOURCE_MAX_COUNT,
                sourceBytesLimit = RUNTIME_MODULE_SOURCE_MAX_BYTES,
                aggregateSourceBytesLimit = RUNTIME_MODULE_SOURCES_MAX_BYTES,
                depthLimit = COMMON_JS_PRELOAD_MAX_DEPTH,
                elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                moduleSourceNames = moduleSourceNames,
            )
        }
    }

    private class EncryptedRuntimeModuleScanStats(
        private val decision: EncryptedRuntimeModuleScanDecision,
    ) {

        private val startedAt = android.os.SystemClock.elapsedRealtime()

        var performed = false
        var examinedPathCount = 0
        var walkedFileCount = 0
        var candidateFileCount = 0
        var headerProbeCount = 0
        var headerBytesRead = 0L
        var headerReadFailureCount = 0
        var fullFileReadCount = 0
        var fullFileBytesRead = 0L
        var fullFileReadFailureCount = 0
        var prunedDirectoryCount = 0
        var outsidePathRejectedCount = 0
        var duplicateCanonicalFileCount = 0
        var walkFailureCount = 0
        var oversizedCiphertextCount = 0
        var cleartextSizeLimitRejectedCount = 0
        var decryptFailureCount = 0
        var encryptedModuleCount = 0
        var encryptedModuleSourceBytes = 0L
        var encryptedModuleBudgetRejectedCount = 0
        var traversalLimitReached = false
        var candidateLimitReached = false
        var identityReadFailureCount = 0
        var identityMismatchCount = 0
        var outputCountBudgetExhausted = false
        var outputBytesBudgetExhausted = false

        fun completionStatus(): String {
            return when {
                identityReadFailureCount > 0 || identityMismatchCount > 0 ->
                    ENCRYPTED_SCAN_STATUS_INCOMPLETE_IDENTITY_FAILURE
                traversalLimitReached -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_TRAVERSAL_LIMIT
                candidateLimitReached -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_CANDIDATE_LIMIT
                walkFailureCount > 0 -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_WALK_FAILURE
                headerReadFailureCount > 0 || fullFileReadFailureCount > 0 ->
                    ENCRYPTED_SCAN_STATUS_INCOMPLETE_READ_FAILURE
                decryptFailureCount > 0 -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_DECRYPT_FAILURE
                outputCountBudgetExhausted -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_COUNT_BUDGET
                outputBytesBudgetExhausted -> ENCRYPTED_SCAN_STATUS_INCOMPLETE_OUTPUT_BYTES_BUDGET
                oversizedCiphertextCount > 0 || cleartextSizeLimitRejectedCount > 0 ->
                    ENCRYPTED_SCAN_STATUS_INCOMPLETE_MODULE_SIZE_BUDGET
                else -> ENCRYPTED_SCAN_STATUS_COMPLETED
            }
        }

        fun result(
            moduleSources: Map<String, String>,
            status: String,
        ): EncryptedRuntimeModuleSourcesResult {
            return EncryptedRuntimeModuleSourcesResult(
                moduleSources = moduleSources,
                diagnostics = EncryptedRuntimeModuleScanDiagnostics(
                    required = decision.required,
                    performed = performed,
                    decision = decision.reason,
                    status = status,
                    elapsedMs = (
                            android.os.SystemClock.elapsedRealtime() - startedAt
                            ).coerceAtLeast(0L),
                    examinedPathLimit = ENCRYPTED_SCAN_MAX_EXAMINED_PATHS,
                    candidateFileLimit = ENCRYPTED_SCAN_MAX_CANDIDATES,
                    examinedPathCount = examinedPathCount,
                    walkedFileCount = walkedFileCount,
                    candidateFileCount = candidateFileCount,
                    headerProbeCount = headerProbeCount,
                    headerBytesRead = headerBytesRead,
                    headerReadFailureCount = headerReadFailureCount,
                    fullFileReadCount = fullFileReadCount,
                    fullFileBytesRead = fullFileBytesRead,
                    fullFileReadFailureCount = fullFileReadFailureCount,
                    prunedDirectoryCount = prunedDirectoryCount,
                    outsidePathRejectedCount = outsidePathRejectedCount,
                    duplicateCanonicalFileCount = duplicateCanonicalFileCount,
                    walkFailureCount = walkFailureCount,
                    oversizedCiphertextCount = oversizedCiphertextCount,
                    cleartextSizeLimitRejectedCount = cleartextSizeLimitRejectedCount,
                    decryptFailureCount = decryptFailureCount,
                    encryptedModuleCount = encryptedModuleCount,
                    retainedEncryptedModuleCount = moduleSources.size,
                    encryptedModuleSourceBytes = encryptedModuleSourceBytes,
                    encryptedModuleBudgetRejectedCount = encryptedModuleBudgetRejectedCount,
                    traversalLimitReached = traversalLimitReached,
                    candidateLimitReached = candidateLimitReached,
                    identityReadFailureCount = identityReadFailureCount,
                    identityMismatchCount = identityMismatchCount,
                    outputCountBudgetExhausted = outputCountBudgetExhausted,
                    outputBytesBudgetExhausted = outputBytesBudgetExhausted,
                ),
            )
        }
    }

    private data class PackageMain(
        val present: Boolean,
        val value: String?,
    )

    private data class PackageExport(
        val present: Boolean,
        val value: String?,
    )

    private data class PackageImport(
        val present: Boolean,
        val value: String?,
    )

    private data class CommonJsPackageScope(
        val packageDirectory: File,
        val packageJson: File,
    )

    private data class CommonJsPackageSpecifier(
        val packageName: String,
        val subpath: String,
    )
}
