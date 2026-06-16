package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.console.GlobalConsole
import org.json.JSONObject
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs6.BuildConfig
import java.io.File

class NativeNodeEmbeddedJavaScriptEngine(private val androidContext: Context) : RhinoJavaScriptEngine(androidContext) {

    @Volatile
    var lastCommonJsPreloadDiagnostics: CommonJsPreloadDiagnostics = CommonJsPreloadDiagnostics.empty()
        private set

    @Volatile
    var lastNodeProjectRunnerDescriptor: NativeNodeEmbeddedProjectRunner.Descriptor? = null
        private set

    @Volatile
    var lastEmbeddedScriptResultNativeValues: Map<String, String> = emptyMap()
        private set

    override fun doExecution(source: JavaScriptSource): Any? {
        lastNodeProjectRunnerDescriptor = null
        lastEmbeddedScriptResultNativeValues = emptyMap()
        tryRunNodeProjectLaunch(source)?.let { return it }
        tryRunNodeProjectFile(source)?.let { return it }

        val request = buildEmbeddedScriptRequest(source)
        val result = NativeNodeEmbeddedScriptService.runScript(androidContext, request)
        return finishEmbeddedScriptExecution(request, result)
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
        lastNodeProjectRunnerDescriptor = NativeNodeEmbeddedProjectRunner.describe(plan)
        val result = NativeNodeEmbeddedProjectRunner.runResolvedProjectLaunchPlan(androidContext, plan)
        return finishEmbeddedScriptExecution(preparation.toBridgeRequest(plan.timeoutMs, currentSpawnDepth()), result)
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
        lastNodeProjectRunnerDescriptor = NativeNodeEmbeddedProjectRunner.describe(request)
        val result = NativeNodeEmbeddedProjectRunner.run(androidContext, request)
        return finishEmbeddedScriptExecution(preparation.toBridgeRequest(request.timeoutMs, currentSpawnDepth()), result)
    }

    private fun prepareNodeProjectRun(
        fileSource: JavaScriptFileSource,
        projectRoot: File,
        entryFile: File,
    ): NodeProjectRunPreparation {
        val normalizedProjectRoot = projectRoot.normalizedCanonicalFile()
        val normalizedEntryFile = entryFile.normalizedCanonicalFile()
        val entrySource = normalizeEmbeddedSource(sourceText(normalizedEntryFile))
        val sourceName = normalizedEntryFile.normalizedAbsolutePath()
        val workingDirectory = normalizedProjectRoot.normalizedAbsolutePath()
        val preload = collectCommonJsModuleSources(
            source = entrySource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
        )
        val runtimeModuleSources = collectEncryptedRuntimeModuleSources(
            source = fileSource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            moduleSources = preload.moduleSources,
            sourceFileOverride = normalizedEntryFile,
        )
        return NodeProjectRunPreparation(
            entrySource = entrySource,
            sourceName = sourceName,
            workingDirectory = workingDirectory,
            preload = preload,
            runtimeModuleSources = runtimeModuleSources,
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
        lastEmbeddedScriptResultNativeValues = result.nativeValues
        printScriptOutput(Log.INFO, result.stdout)
        printScriptOutput(Log.ERROR, result.stderr)
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
        text.lineSequence().forEach { line ->
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
        val requestSource = normalizeEmbeddedSource(sourceText(source))
        val requestSourceName = sourceName(source)
        val requestWorkingDirectory = workingDirectory(source)
        val preload = collectCommonJsModuleSources(
            source = requestSource,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
        )
        val runtimeModuleSources = collectEncryptedRuntimeModuleSources(
            source = source,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            moduleSources = preload.moduleSources,
        )
        Log.i(
            TAG,
            "embedded request prepared sourceName=$requestSourceName " +
                    "moduleSources=${preload.moduleSources.size} " +
                    "runtimeModuleSources=${runtimeModuleSources.size} " +
                    "runtimeFirst=${runtimeModuleSources.keys.firstOrNull().orEmpty()}",
        )
        lastCommonJsPreloadDiagnostics = preload.diagnostics
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
            source = requestSource,
            sourceName = requestSourceName,
            workingDirectory = requestWorkingDirectory,
            moduleSources = preload.moduleSources,
            runtimeModuleSources = runtimeModuleSources,
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
        return sourceText(file).ifEmpty { source.script }
    }

    private fun sourceText(file: File): String {
        val bytes = runCatching { PFiles.readBytes(file.path) }.getOrNull() ?: return ""
        if (bytes.size < EncryptedScriptFileHeader.BLOCK_SIZE || !EncryptedScriptFileHeader.isValidFile(bytes)) {
            return bytes.toString(Charsets.UTF_8)
        }
        return String(ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE), Charsets.UTF_8)
    }

    private fun File.encryptedScriptSourceText(): String? {
        val bytes = runCatching { PFiles.readBytes(path) }.getOrNull() ?: return null
        if (bytes.size < EncryptedScriptFileHeader.BLOCK_SIZE || !EncryptedScriptFileHeader.isValidFile(bytes)) {
            return null
        }
        return String(ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE), Charsets.UTF_8)
    }

    private fun collectCommonJsModuleSources(
        source: String,
        sourceName: String,
        workingDirectory: String?,
    ): CommonJsPreloadResult {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val stats = CommonJsPreloadStats(source.toByteArray(Charsets.UTF_8).size)
        if (File(sourceName).extension.equals(MJS_EXTENSION, ignoreCase = true)) {
            return CommonJsPreloadResult(
                moduleSources = emptyMap(),
                diagnostics = stats.toDiagnostics(sourceName, workingDirectory, emptyList(), startedAt),
            )
        }
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).normalizedCanonicalFile() }
            ?: return CommonJsPreloadResult(
                moduleSources = emptyMap(),
                diagnostics = stats.toDiagnostics(sourceName, workingDirectory, emptyList(), startedAt),
            )
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
            moduleSources = moduleSources,
            visited = linkedSetOf(),
            stats = stats,
            depth = 1,
        )
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

    private fun collectEncryptedRuntimeModuleSources(
        source: JavaScriptSource,
        sourceName: String,
        workingDirectory: String?,
        moduleSources: Map<String, String>,
        sourceFileOverride: File? = null,
    ): Map<String, String> {
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).normalizedCanonicalFile() }
            ?: return emptyMap()
        val sourceFile = sourceFileOverride
            ?.normalizedCanonicalFile()
            ?.takeIf { it.isFile && it.isInsideOrSame(root) }
            ?: (source as? JavaScriptFileSource)?.file
            ?: File(sourceName).takeIf { it.isAbsolute && it.isFile && it.isInsideOrSame(root) }
            ?: return emptyMap()
        val excluded = moduleSources.keys.toMutableSet()
        excluded += sourceFile.normalizedAbsolutePath()
        var remainingCount = (RUNTIME_MODULE_SOURCE_MAX_COUNT - moduleSources.size).coerceAtLeast(0)
        if (remainingCount <= 0) {
            return emptyMap()
        }
        var remainingBytes = RUNTIME_MODULE_SOURCES_MAX_BYTES - moduleSources.values.sumOf {
            it.toByteArray(Charsets.UTF_8).size
        }
        if (remainingBytes <= 0) {
            return emptyMap()
        }
        val runtimeSources = linkedMapOf<String, String>()
        root.walkTopDown()
            .filter { file -> file.isFile && file.extension.lowercase() in COMMON_JS_MODULE_EXTENSIONS }
            .map { file -> file to file.relativePathInside(root) }
            .sortedWith(
                compareBy<Pair<File, String>>(
                    { (_, relativePath) -> relativePath.split('/').contains(NODE_MODULES) },
                    { (_, relativePath) -> relativePath.count { it == '/' } },
                    { (_, relativePath) -> relativePath },
                )
            )
            .forEach { (file, _) ->
            if (remainingCount <= 0 || remainingBytes <= 0) {
                return@forEach
            }
            val moduleKey = file.normalizedAbsolutePath()
            if (moduleKey in excluded || !file.isInsideOrSame(root)) {
                return@forEach
            }
            val clearSource = file.encryptedScriptSourceText() ?: return@forEach
            val moduleSource = normalizeEmbeddedSource(clearSource)
            val moduleBytes = moduleSource.toByteArray(Charsets.UTF_8).size
            if (moduleBytes > RUNTIME_MODULE_SOURCE_MAX_BYTES || moduleBytes > remainingBytes) {
                return@forEach
            }
            runtimeSources[moduleKey] = moduleSource
            remainingCount -= 1
            remainingBytes -= moduleBytes
        }
        return runtimeSources
    }

    private fun collectCommonJsModuleSources(
        source: String,
        parentDirectory: File,
        rootDirectory: File,
        moduleSources: MutableMap<String, String>,
        visited: MutableSet<String>,
        stats: CommonJsPreloadStats,
        depth: Int,
    ) {
        val requireMatches = COMMON_JS_REQUIRE_PATTERN.findAll(source).toList()
        stats.recordRequireScan(source, requireMatches.size)
        requireMatches
            .map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
            .forEach { moduleName ->
                val moduleFiles = resolveCommonJsModuleFiles(parentDirectory, rootDirectory, moduleName)
                if (moduleFiles.isEmpty()) {
                    stats.unresolvedStaticRequireCount += 1
                    return@forEach
                }
                moduleFiles.forEach { moduleFile ->
                    val moduleKey = moduleFile.normalizedAbsolutePath()
                    if (!visited.add(moduleKey)) {
                        stats.duplicateSkippedCount += 1
                        return@forEach
                    }
                    val moduleSource = normalizeEmbeddedSource(sourceText(moduleFile))
                    moduleSources[moduleKey] = moduleSource
                    stats.recordPreloadedModule(moduleFile, moduleSource, depth)
                    if (moduleFile.extension.lowercase() in COMMON_JS_RECURSIVE_SOURCE_EXTENSIONS) {
                        collectCommonJsModuleSources(
                            source = moduleSource,
                            parentDirectory = moduleFile.parentFile ?: rootDirectory,
                            rootDirectory = rootDirectory,
                            moduleSources = moduleSources,
                            visited = visited,
                            stats = stats,
                            depth = depth + 1,
                        )
                    }
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
                        candidate.extension.lowercase() in COMMON_JS_MODULE_EXTENSIONS
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
        (source as? JavaScriptFileSource)?.file?.normalizedAbsolutePath()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return source.fullPath.takeIf { it.isNotBlank() }
            ?: source.name.takeIf { it.isNotBlank() }
            ?: DEFAULT_SOURCE_NAME
    }

    private fun workingDirectory(source: JavaScriptSource): String? {
        val file = (source as? JavaScriptFileSource)?.file
        file?.parentFile?.normalizedAbsolutePath()?.takeIf { it.isNotBlank() }?.let { return it }
        cwd().takeUnless { it.isNullOrBlank() }?.let { return it }
        return source.fullPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).parentFile?.absolutePath }.getOrNull() }
    }

    private fun File.normalizedAbsolutePath(): String {
        return runCatching { canonicalPath }.getOrElse { absolutePath }
    }

    private fun File.normalizedCanonicalFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
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
        const val JSON_EXTENSION = "json"
        const val PACKAGE_JSON = "package.json"
        const val PACKAGE_JSON_NAME = "name"
        const val PACKAGE_JSON_MAIN = "main"
        const val PACKAGE_JSON_EXPORTS = "exports"
        const val PACKAGE_JSON_IMPORTS = "imports"
        const val NODE_MODULES = "node_modules"
        const val RUNTIME_MODULE_SOURCE_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES
        const val RUNTIME_MODULE_SOURCES_MAX_BYTES = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES
        const val RUNTIME_MODULE_SOURCE_MAX_COUNT = NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT
        val COMMON_JS_MODULE_EXTENSIONS = NodeRuntimeCapabilities.commonJsModuleExtensions
        val COMMON_JS_RECURSIVE_SOURCE_EXTENSIONS = NodeRuntimeCapabilities.commonJsRecursiveSourceExtensions
        val PACKAGE_JSON_EXPORT_CONDITIONS = NodeRuntimeCapabilities.packageJsonExportConditions
        const val COMMON_JS_REQUIRE_SEPARATOR = """(?:\s|/\*[\s\S]*?\*/|//[^\r\n]*(?:\r?\n|$))*"""
        val COMMON_JS_REQUIRE_CALL_PATTERN = Regex(
            """(?<![\w$.])(?:require|module\s*\.\s*require)$COMMON_JS_REQUIRE_SEPARATOR\(""",
        )
        val COMMON_JS_REQUIRE_PATTERN = Regex(
            """(?<![\w$.])(?:require|module\s*\.\s*require)$COMMON_JS_REQUIRE_SEPARATOR\($COMMON_JS_REQUIRE_SEPARATOR(?:"([^"]+)"|'([^']+)')$COMMON_JS_REQUIRE_SEPARATOR\)""",
        )
        val COMMON_JS_PROTOCOL_SPECIFIER_PATTERN = Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""")
        val COMMON_JS_BUILTIN_MODULE_NAMES = NodeRuntimeCapabilities.commonJsBuiltinModuleNames
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
                elapsedMs = 0L,
                moduleSourceNames = emptyList(),
            )
        }
    }

    private data class CommonJsPreloadResult(
        val moduleSources: Map<String, String>,
        val diagnostics: CommonJsPreloadDiagnostics,
    )

    private data class NodeProjectRunPreparation(
        val entrySource: String,
        val sourceName: String,
        val workingDirectory: String,
        val preload: CommonJsPreloadResult,
        val runtimeModuleSources: Map<String, String>,
    ) {
        fun toBridgeRequest(timeoutMs: Long, spawnDepth: Int = 0): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest {
            return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = entrySource,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
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

        fun recordRequireScan(source: String, staticCount: Int) {
            staticRequireCount += staticCount
            val requireCallCount = COMMON_JS_REQUIRE_CALL_PATTERN.findAll(source).count()
            dynamicRequireIgnoredCount += (requireCallCount - staticCount).coerceAtLeast(0)
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
                elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                moduleSourceNames = moduleSourceNames,
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
