package org.autojs.autojs.engine

import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.NodeEntryDirective
import org.autojs.autojs.script.NodeProjectLaunchKind
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/** Resolves host project descriptors into requests for the external Node.js runtime plugin. */
object NodeProjectRunner {

    data class Request(
        val projectRoot: File,
        val entryFile: File,
        val workingDirectory: File = projectRoot,
        val argv: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val timeoutMs: Long = NodePluginScriptRequest.DEFAULT_TIMEOUT_MS,
        val moduleSources: Map<String, String> = emptyMap(),
        val runtimeModuleSources: Map<String, String> = emptyMap(),
        val entrySource: String? = null,
        val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
    )

    data class PackageBinRequest(
        val projectRoot: File,
        val packageDir: File,
        val binName: String,
        val workingDirectory: File = projectRoot,
        val argv: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val timeoutMs: Long = NodePluginScriptRequest.DEFAULT_TIMEOUT_MS,
        val moduleSources: Map<String, String> = emptyMap(),
        val runtimeModuleSources: Map<String, String> = emptyMap(),
        val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
    )

    data class PackageScriptRequest(
        val projectRoot: File,
        val packageDir: File,
        val scriptName: String,
        val workingDirectory: File = packageDir,
        val argv: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val timeoutMs: Long = NodePluginScriptRequest.DEFAULT_TIMEOUT_MS,
        val moduleSources: Map<String, String> = emptyMap(),
        val runtimeModuleSources: Map<String, String> = emptyMap(),
        val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
    )

    data class ProjectRequest(
        val projectRoot: File,
        val packageDir: File? = null,
        val workingDirectory: File = projectRoot,
        val scriptName: String? = null,
        val binName: String? = null,
        val argv: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val timeoutMs: Long = NodePluginScriptRequest.DEFAULT_TIMEOUT_MS,
        val moduleSources: Map<String, String> = emptyMap(),
        val runtimeModuleSources: Map<String, String> = emptyMap(),
        val entrySource: String? = null,
        val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
    )

    data class ProjectLaunchPlan(
        val kind: String,
        val projectRoot: File,
        val entryFile: File,
        val workingDirectory: File,
        val argv: List<String>,
        val env: Map<String, String>,
        val timeoutMs: Long,
        val moduleSources: Map<String, String>,
        val runtimeModuleSources: Map<String, String>,
        val entrySource: String?,
        val executionMode: String,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
        val diagnosticArgv: List<String>? = null,
        val diagnosticEnvKeys: List<String>? = null,
        val packageDir: File? = null,
        val binName: String? = null,
        val scriptName: String? = null,
        val scriptCommand: String? = null,
        val resolvedCommand: String? = null,
    )

    data class Descriptor(
        val kind: String,
        val launchKind: String? = null,
        val projectRoot: String,
        val entryFile: String,
        val workingDirectory: String,
        val argv: List<String>,
        val envKeys: List<String>,
        val timeoutMs: Long,
        val moduleSourceCount: Int,
        val moduleSourceBytes: Int,
        val runtimeModuleSourceCount: Int,
        val runtimeModuleSourceBytes: Int,
        val entrySourceBytes: Int,
        val executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        val entryDirective: String? = null,
        val processPoolSlot: String? = null,
        val processPoolSlotSource: String? = null,
        val packageDir: String? = null,
        val binName: String? = null,
        val scriptName: String? = null,
        val scriptCommand: String? = null,
        val resolvedCommand: String? = null,
    ) {
        fun toDebugString(): String {
            return buildString {
                appendLine("kind=$kind")
                appendLine("launchKind=${launchKind.orEmpty()}")
                appendLine("projectRoot=$projectRoot")
                appendLine("entryFile=$entryFile")
                appendLine("workingDirectory=$workingDirectory")
                appendLine("argv=${argv.joinToString(prefix = "[", postfix = "]")}")
                appendLine("envKeys=${envKeys.joinToString(prefix = "[", postfix = "]")}")
                appendLine("timeoutMs=$timeoutMs")
                appendLine("moduleSourceCount=$moduleSourceCount")
                appendLine("moduleSourceBytes=$moduleSourceBytes")
                appendLine("runtimeModuleSourceCount=$runtimeModuleSourceCount")
                appendLine("runtimeModuleSourceBytes=$runtimeModuleSourceBytes")
                appendLine("entrySourceBytes=$entrySourceBytes")
                appendLine("executionMode=$executionMode")
                appendLine("entryDirective=${entryDirective.orEmpty()}")
                appendLine("processPoolSlot=${processPoolSlot.orEmpty()}")
                appendLine("processPoolSlotSource=${processPoolSlotSource.orEmpty()}")
                appendLine("packageDir=${packageDir.orEmpty()}")
                appendLine("binName=${binName.orEmpty()}")
                appendLine("scriptName=${scriptName.orEmpty()}")
                appendLine("scriptCommand=${scriptCommand.orEmpty()}")
                appendLine("resolvedCommand=${resolvedCommand.orEmpty()}")
            }
        }

        fun toNativeValues(): Map<String, String> {
            return mapOf(
                "embedded_project_runner.kind" to kind,
                "embedded_project_runner.launch_kind" to launchKind.orEmpty(),
                "embedded_project_runner.project_root" to projectRoot,
                "embedded_project_runner.entry_file" to entryFile,
                "embedded_project_runner.working_directory" to workingDirectory,
                "embedded_project_runner.argv" to argv.joinToString(prefix = "[", postfix = "]"),
                "embedded_project_runner.env_keys" to envKeys.joinToString(prefix = "[", postfix = "]"),
                "embedded_project_runner.timeout_ms" to timeoutMs.toString(),
                "embedded_project_runner.module_source_count" to moduleSourceCount.toString(),
                "embedded_project_runner.module_source_bytes" to moduleSourceBytes.toString(),
                "embedded_project_runner.runtime_module_source_count" to runtimeModuleSourceCount.toString(),
                "embedded_project_runner.runtime_module_source_bytes" to runtimeModuleSourceBytes.toString(),
                "embedded_project_runner.entry_source_bytes" to entrySourceBytes.toString(),
                "embedded_project_runner.execution_mode" to executionMode,
                "embedded_project_runner.entry_directive" to entryDirective.orEmpty(),
                "embedded_project_runner.process_pool_slot" to processPoolSlot.orEmpty(),
                "embedded_project_runner.process_pool_slot_source" to processPoolSlotSource.orEmpty(),
                "embedded_project_runner.entry_directive_ui_activity" to NodeEntryDirective
                    .requiresUiActivity(entryDirective)
                    .toString(),
                "embedded_project_runner.entry_directive_ui_thread" to NodeEntryDirective
                    .requiresUiThread(entryDirective)
                    .toString(),
                "embedded_project_runner.package_dir" to packageDir.orEmpty(),
                "embedded_project_runner.bin_name" to binName.orEmpty(),
                "embedded_project_runner.script_name" to scriptName.orEmpty(),
                "embedded_project_runner.script_command" to scriptCommand.orEmpty(),
                "embedded_project_runner.resolved_command" to resolvedCommand.orEmpty(),
            )
        }
    }

    fun buildResolvedProjectLaunchRequest(
        plan: ProjectLaunchPlan,
    ): NodePluginScriptRequest {
        return buildPluginScriptRequest(plan.toEntryRequest(), describe(plan))
    }

    fun resolveProjectLaunchPlanWithDescriptor(request: ProjectRequest): ProjectLaunchPlan {
        val baseDescriptor = describe(request)
        return runCatching {
            resolveProjectLaunchPlan(request)
        }.getOrElse { error ->
            throw illegalRequest(error.message ?: error.javaClass.name, baseDescriptor)
        }
    }

    @JvmStatic
    fun isNodeProjectRoot(projectRoot: File): Boolean {
        return readNodeProjectConfig(projectRoot.canonicalFile) != null
    }

    @JvmStatic
    fun resolveProjectEntryFileIfNodeProject(projectRoot: File): File? {
        return if (isNodeProjectRoot(projectRoot)) {
            resolveProjectEntryFile(projectRoot)
        } else {
            null
        }
    }

    fun resolveProjectLaunchPlan(projectRoot: File): ProjectLaunchPlan {
        return resolveProjectLaunchPlan(ProjectRequest(projectRoot = projectRoot))
    }

    @JvmStatic
    fun buildProjectRequest(launchSource: NodeProjectLaunchSource): ProjectRequest {
        return buildProjectRequest(launchSource, launchSource.nodeProjectRootFile)
    }

    fun buildProjectRequest(
        launchSource: NodeProjectLaunchSource,
        projectRoot: File,
    ): ProjectRequest {
        val normalizedProjectRoot = projectRoot.canonicalFile
        val sourceLaunchKind = launchSource.nodeProjectLaunchKind
        val defaultRequest = ProjectRequest(projectRoot = normalizedProjectRoot)
        val projectTimeoutMs = readNodeProjectConfig(normalizedProjectRoot)
            ?.second?.optJSONObject("node")?.optLong("timeoutMs")?.takeIf { it > 0L }
        val processPoolSlot = normalizeDescriptorProcessPoolSlot(launchSource.nodeProjectProcessPoolSlot)
        val request = defaultRequest.copy(
            packageDir = launchSource.nodeProjectPackageDirFile?.canonicalFile ?: normalizedProjectRoot,
            workingDirectory = launchSource.nodeProjectWorkingDirectoryFile?.canonicalFile ?: normalizedProjectRoot,
            scriptName = launchSource.nodeProjectScriptName,
            binName = launchSource.nodeProjectBinName,
            argv = launchSource.nodeProjectArgv.toList(),
            env = launchSource.nodeProjectEnv.toMap(),
            timeoutMs = launchSource.nodeProjectTimeoutMs ?: projectTimeoutMs ?: defaultRequest.timeoutMs,
            executionMode = launchSource.nodeProjectExecutionMode ?: defaultRequest.executionMode,
            entryDirective = NodeEntryDirective.normalize(launchSource.nodeProjectEntryDirective)
                ?: (launchSource as? JavaScriptSource)
                    ?.executionMode
                    ?.let { mode -> NodeEntryDirective.fromExecutionMode(mode) },
            processPoolSlot = processPoolSlot,
            processPoolSlotSource = normalizeDescriptorProcessPoolSlotSource(
                launchSource.nodeProjectProcessPoolSlotSource,
                processPoolSlot,
            ),
        )
        val requestLaunchKind = NodeProjectLaunchKind.from(request.scriptName, request.binName)
        require(requestLaunchKind == sourceLaunchKind) {
            "Node project launch source kind mismatch: source=${sourceLaunchKind.descriptorValue} " +
                    "request=${requestLaunchKind.descriptorValue}"
        }
        return request
    }

    fun resolveProjectLaunchPlan(request: ProjectRequest): ProjectLaunchPlan {
        val normalizedProjectRoot = request.projectRoot.canonicalFile
        val normalizedWorkingDirectory = request.workingDirectory.canonicalFile
        val normalizedPackageDir = (request.packageDir ?: normalizedProjectRoot).canonicalFile
        val processPoolSlot = normalizeDescriptorProcessPoolSlot(request.processPoolSlot)
        val processPoolSlotSource = normalizeDescriptorProcessPoolSlotSource(request.processPoolSlotSource, processPoolSlot)
        readNodeProjectConfig(normalizedProjectRoot)
            ?: throw IllegalArgumentException(
                "Node project config type must be node: ${File(normalizedProjectRoot, PROJECT_CONFIG_NAME).absolutePath}",
            )
        auditPackageFeatureFlagMetadata(File(normalizedProjectRoot, "package.json"))
        require(request.scriptName == null || request.binName == null) {
            "Node project launch request cannot specify both scriptName and binName"
        }
        request.scriptName?.let { scriptName ->
            require(request.entrySource == null) {
                "Node project entrySource is only supported for project entry launch"
            }
            return resolvePackageScriptLaunchPlan(
                PackageScriptRequest(
                    projectRoot = normalizedProjectRoot,
                    packageDir = normalizedPackageDir,
                    scriptName = scriptName,
                    workingDirectory = normalizedWorkingDirectory,
                    argv = request.argv,
                    env = request.env,
                    timeoutMs = request.timeoutMs,
                    moduleSources = request.moduleSources,
                    runtimeModuleSources = request.runtimeModuleSources,
                    executionMode = request.executionMode,
                    entryDirective = request.entryDirective,
                    processPoolSlot = processPoolSlot,
                    processPoolSlotSource = processPoolSlotSource,
                ),
            )
        }
        request.binName?.let { binName ->
            require(request.entrySource == null) {
                "Node project entrySource is only supported for project entry launch"
            }
            return resolvePackageBinLaunchPlan(
                PackageBinRequest(
                    projectRoot = normalizedProjectRoot,
                    packageDir = normalizedPackageDir,
                    binName = binName,
                    workingDirectory = normalizedWorkingDirectory,
                    argv = request.argv,
                    env = request.env,
                    timeoutMs = request.timeoutMs,
                    moduleSources = request.moduleSources,
                    runtimeModuleSources = request.runtimeModuleSources,
                    executionMode = request.executionMode,
                    entryDirective = request.entryDirective,
                    processPoolSlot = processPoolSlot,
                    processPoolSlotSource = processPoolSlotSource,
                ),
            )
        }
        return ProjectLaunchPlan(
            kind = PROJECT_LAUNCH_KIND_ENTRY,
            projectRoot = normalizedProjectRoot,
            entryFile = resolveProjectEntryFile(normalizedProjectRoot),
            workingDirectory = normalizedWorkingDirectory,
            argv = request.argv,
            env = request.env,
            timeoutMs = request.timeoutMs,
            moduleSources = request.moduleSources,
            runtimeModuleSources = request.runtimeModuleSources,
            entrySource = request.entrySource,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = processPoolSlot,
            processPoolSlotSource = processPoolSlotSource,
        )
    }

    fun buildResolvedEntryRequest(
        request: Request,
    ): NodePluginScriptRequest {
        val normalizedRequest = normalizeRequest(request)
        return buildPluginScriptRequest(normalizedRequest, describe(normalizedRequest))
    }

    fun describeResolvedEntryRequest(request: Request): Descriptor {
        return describe(normalizeRequest(request))
    }

    private fun buildPluginScriptRequest(
        request: Request,
        descriptor: Descriptor,
    ): NodePluginScriptRequest {
        val projectRoot = request.projectRoot.canonicalFile
        val entryFile = request.entryFile.canonicalFile
        val workingDirectory = request.workingDirectory.canonicalFile

        requirePathInside(projectRoot, entryFile, "Node project entry", descriptor)
        requirePathInside(projectRoot, workingDirectory, "Node project workingDirectory", descriptor)
        if (!entryFile.isFile) {
            throw illegalRequest("Node project entry missing: ${entryFile.absolutePath}", descriptor)
        }
        if (!workingDirectory.isDirectory) {
            throw illegalRequest(
                "Node project workingDirectory is not a directory: ${workingDirectory.absolutePath}",
                descriptor,
            )
        }
        when (entryFile.extension.lowercase()) {
            "js", "cjs", "mjs", "ts", "cts", "mts" -> Unit
            "tsx" -> throw illegalRequest(
                "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION: Unsupported TypeScript TSX project entry: ${entryFile.absolutePath}",
                descriptor,
            )
            "node" -> throw illegalRequest(
                "Unsupported Node project native addon entry: ${entryFile.absolutePath}",
                descriptor,
            )
            else -> throw illegalRequest(
                "Unsupported Node project entry extension: ${entryFile.absolutePath}",
                descriptor,
            )
        }

        val bootstrap = buildBootstrap(request, projectRoot, entryFile, workingDirectory)
        val source = buildString {
            append(bootstrap)
            append(stripShebang(request.entrySource ?: entryFile.readText(StandardCharsets.UTF_8)))
            if (!endsWith("\n")) {
                append('\n')
            }
        }
        return NodePluginScriptRequest(
            source = source,
            sourceName = entryFile.canonicalPath,
            sourceLineOffset = if (request.entrySource == null) 0 else bootstrap.count { it == '\n' },
            workingDirectory = projectRoot.canonicalPath,
            sandboxRoot = projectRoot.canonicalPath,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            timeoutMs = request.timeoutMs,
            moduleSources = request.moduleSources,
            runtimeModuleSources = request.runtimeModuleSources,
        )
    }

    fun resolvePackageBinLaunchPlan(request: PackageBinRequest): ProjectLaunchPlan {
        val normalizedRequest = normalizePackageBinRequest(request)
        require(isPathInsideOrSame(normalizedRequest.projectRoot, normalizedRequest.packageDir)) {
            "Node package bin packageDir escapes Node project root: ${normalizedRequest.packageDir.path}"
        }
        require(isPathInsideOrSame(normalizedRequest.projectRoot, normalizedRequest.workingDirectory)) {
            "Node package bin workingDirectory escapes Node project root: ${normalizedRequest.workingDirectory.path}"
        }
        require(normalizedRequest.packageDir.isDirectory) {
            "Node package bin packageDir is not a directory: ${normalizedRequest.packageDir.absolutePath}"
        }
        require(normalizedRequest.workingDirectory.isDirectory) {
            "Node package bin workingDirectory is not a directory: ${normalizedRequest.workingDirectory.absolutePath}"
        }

        return ProjectLaunchPlan(
            kind = PROJECT_LAUNCH_KIND_PACKAGE_BIN,
            projectRoot = normalizedRequest.projectRoot,
            entryFile = resolvePackageBinTarget(normalizedRequest.packageDir, normalizedRequest.binName),
            workingDirectory = normalizedRequest.workingDirectory,
            argv = normalizedRequest.argv,
            env = normalizedRequest.env,
            timeoutMs = normalizedRequest.timeoutMs,
            moduleSources = normalizedRequest.moduleSources,
            runtimeModuleSources = normalizedRequest.runtimeModuleSources,
            entrySource = null,
            executionMode = NodeExecutionModePolicy.normalize(normalizedRequest.executionMode),
            entryDirective = NodeEntryDirective.normalize(normalizedRequest.entryDirective),
            processPoolSlot = normalizedRequest.processPoolSlot,
            processPoolSlotSource = normalizedRequest.processPoolSlotSource,
            packageDir = normalizedRequest.packageDir,
            binName = normalizedRequest.binName,
        )
    }

    fun resolvePackageScriptLaunchPlan(request: PackageScriptRequest): ProjectLaunchPlan {
        val normalizedRequest = normalizePackageScriptRequest(request)
        require(isPathInsideOrSame(normalizedRequest.projectRoot, normalizedRequest.packageDir)) {
            "Node package script packageDir escapes Node project root: ${normalizedRequest.packageDir.path}"
        }
        require(isPathInsideOrSame(normalizedRequest.projectRoot, normalizedRequest.workingDirectory)) {
            "Node package script workingDirectory escapes Node project root: ${normalizedRequest.workingDirectory.path}"
        }
        require(normalizedRequest.packageDir.isDirectory) {
            "Node package script packageDir is not a directory: ${normalizedRequest.packageDir.absolutePath}"
        }
        require(normalizedRequest.workingDirectory.isDirectory) {
            "Node package script workingDirectory is not a directory: ${normalizedRequest.workingDirectory.absolutePath}"
        }

        val command = resolvePackageScriptCommand(normalizedRequest)
        return ProjectLaunchPlan(
            kind = PROJECT_LAUNCH_KIND_PACKAGE_SCRIPT,
            projectRoot = normalizedRequest.projectRoot,
            entryFile = command.entryFile,
            workingDirectory = normalizedRequest.workingDirectory,
            argv = command.argv + normalizedRequest.argv,
            env = normalizedRequest.env + buildPackageScriptLifecycleEnv(normalizedRequest),
            timeoutMs = normalizedRequest.timeoutMs,
            moduleSources = normalizedRequest.moduleSources,
            runtimeModuleSources = normalizedRequest.runtimeModuleSources,
            entrySource = null,
            executionMode = NodeExecutionModePolicy.normalize(normalizedRequest.executionMode),
            entryDirective = NodeEntryDirective.normalize(normalizedRequest.entryDirective),
            processPoolSlot = normalizedRequest.processPoolSlot,
            processPoolSlotSource = normalizedRequest.processPoolSlotSource,
            diagnosticArgv = normalizedRequest.argv,
            diagnosticEnvKeys = normalizedRequest.env.keys.sorted(),
            packageDir = normalizedRequest.packageDir,
            binName = command.binName,
            scriptName = normalizedRequest.scriptName,
            scriptCommand = command.scriptCommand,
            resolvedCommand = command.resolvedCommand,
        )
    }

    fun resolveProjectEntryFile(projectRoot: File): File {
        val root = projectRoot.canonicalFile
        val (projectJson, json) = readNodeProjectConfig(root)
            ?: throw IllegalArgumentException("Node project config type must be node: ${File(root, PROJECT_CONFIG_NAME).absolutePath}")

        val target = PROJECT_MAIN_KEYS
            .asSequence()
            .mapNotNull { key -> json.opt(key)?.takeIf { it !is JSONObject && it != JSONObject.NULL } }
            .firstOrNull()
            ?.also { value ->
                require(value is String) {
                    "Unsupported Node project main entry in ${projectJson.absolutePath}"
                }
            } as? String
            ?: DEFAULT_PROJECT_MAIN

        return resolveSandboxedScriptTarget(
            root = root,
            target = target,
            missingMessage = "Node project entry missing",
            nulMessage = "Node project entry target contains NUL",
            emptyMessage = "Node project entry target is empty",
            escapeMessage = "Node project entry escapes project sandbox",
            absoluteMessage = "Node project entry target must be relative",
            nativeAddonMessage = "Unsupported Node project native addon entry",
            extensionMessage = "Unsupported Node project entry extension",
        )
    }

    private fun readNodeProjectConfig(projectRoot: File): Pair<File, JSONObject>? {
        val projectJson = File(projectRoot, PROJECT_CONFIG_NAME)
        if (!projectJson.isFile) {
            return null
        }
        val json = runCatching {
            JSONObject(projectJson.readText(StandardCharsets.UTF_8))
        }.getOrNull() ?: return null
        val type = (json.optString("type").ifBlank { json.optString("projectType") }).trim()
        if (!type.equals("node", ignoreCase = true)) {
            return null
        }
        NodeRuntimeCapabilities.requireNoRejectedFeatureFlagOverrides(
            NodeRuntimeCapabilities.auditProjectJsonFeatureFlags(json, projectJson.absolutePath),
        )
        return projectJson to json
    }

    fun resolvePackageBinTarget(packageDir: File, binName: String): File {
        val packageRoot = packageDir.canonicalFile
        val packageJson = File(packageRoot, "package.json")
        require(packageJson.isFile) { "Missing package.json for package bin: ${packageJson.absolutePath}" }

        val json = JSONObject(packageJson.readText(StandardCharsets.UTF_8))
        auditPackageFeatureFlagMetadata(packageJson, json)
        val binValue: Any? = json.opt("bin")
        val target = when (binValue) {
            null, JSONObject.NULL -> throw IllegalArgumentException("Missing package bin in ${packageJson.absolutePath}")
            is String -> binValue
            is JSONObject -> {
                if (!binValue.has(binName)) {
                    throw IllegalArgumentException("Missing package bin entry '$binName' in ${packageJson.absolutePath}")
                }
                val entry = binValue.opt(binName)
                if (entry !is String) {
                    throw IllegalArgumentException("Unsupported package bin entry '$binName' in ${packageJson.absolutePath}")
                }
                entry
            }
            else -> throw IllegalArgumentException("Unsupported package bin shape in ${packageJson.absolutePath}")
        }

        return resolveSandboxedScriptTarget(
            root = packageRoot,
            target = target,
            missingMessage = "Package bin target missing",
            nulMessage = "Package bin target contains NUL",
            emptyMessage = "Package bin target is empty",
            escapeMessage = "Package bin target escapes package sandbox",
            absoluteMessage = "Package bin target must be relative",
            nativeAddonMessage = "Unsupported package bin native addon target",
            extensionMessage = "Unsupported package bin target extension",
        )
    }

    fun resolvePackageScriptCommand(request: PackageScriptRequest): ResolvedPackageScriptCommand {
        val packageJson = File(request.packageDir, "package.json")
        require(packageJson.isFile) {
            "Missing package.json for package script: ${packageJson.absolutePath}"
        }
        val json = JSONObject(packageJson.readText(StandardCharsets.UTF_8))
        auditPackageFeatureFlagMetadata(packageJson, json)
        val scripts = json.opt("scripts")
        require(scripts is JSONObject) {
            "Missing package scripts in ${packageJson.absolutePath}"
        }
        require(scripts.has(request.scriptName)) {
            "Missing package script '${request.scriptName}' in ${packageJson.absolutePath}"
        }
        val command = scripts.opt(request.scriptName)
        require(command is String) {
            "Unsupported package script '${request.scriptName}' in ${packageJson.absolutePath}"
        }

        val tokens = parsePackageScriptCommand(command)
        return if (tokens.first() == "node") {
            resolveNodePackageScriptCommand(request, command, tokens)
        } else {
            resolveBinPackageScriptCommand(request, command, tokens)
        }
    }

    fun parsePackageScriptCommand(command: String): List<String> {
        require(!command.contains('\u0000')) { "Package script command contains NUL" }
        require(!command.contains('`')) { "Package script command substitution is unsupported" }
        require(!command.contains("\$(")) { "Package script command substitution is unsupported" }

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var index = 0

        fun flushToken() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.setLength(0)
            }
        }

        while (index < command.length) {
            val char = command[index]
            if (quote == null) {
                when {
                    char.isWhitespace() -> flushToken()
                    char == '"' || char == '\'' -> quote = char
                    char in SHELL_OPERATOR_CHARS -> throw IllegalArgumentException(
                        "Package script shell operators are unsupported: $char",
                    )
                    else -> current.append(char)
                }
            } else {
                when {
                    char == quote -> quote = null
                    char == '\\' && quote == '"' && index + 1 < command.length -> {
                        index += 1
                        current.append(command[index])
                    }
                    else -> current.append(char)
                }
            }
            index += 1
        }

        require(quote == null) { "Package script command has unterminated quote" }
        flushToken()
        require(tokens.isNotEmpty()) { "Package script command is empty" }
        validatePackageScriptCommandHead(tokens.first())
        return tokens
    }

    fun resolvePackageScriptBinPackageDir(
        projectRoot: File,
        packageDir: File,
        binName: String,
    ): File {
        val normalizedProjectRoot = projectRoot.canonicalFile
        val normalizedPackageDir = packageDir.canonicalFile
        require(isPathInsideOrSame(normalizedProjectRoot, normalizedPackageDir)) {
            "Package script packageDir escapes Node project root: ${normalizedPackageDir.path}"
        }
        val normalizedBinName = normalizePackageScriptCommandHead(binName)
        return packageScriptBinPackageCandidates(normalizedProjectRoot, normalizedPackageDir, normalizedBinName)
            .firstOrNull { candidate -> packageProvidesBin(candidate, normalizedBinName) }
            ?: throw IllegalArgumentException("Missing package script bin '$normalizedBinName' from ${normalizedPackageDir.absolutePath}")
    }

    fun describe(
        request: ProjectRequest,
        entryFile: File? = null,
    ): Descriptor {
        return Descriptor(
            kind = "project",
            launchKind = projectRequestLaunchKind(request),
            projectRoot = request.projectRoot.absolutePath,
            entryFile = entryFile?.absolutePath.orEmpty(),
            workingDirectory = request.workingDirectory.absolutePath,
            argv = request.argv,
            envKeys = request.env.keys.sorted(),
            timeoutMs = request.timeoutMs,
            moduleSourceCount = request.moduleSources.size,
            moduleSourceBytes = request.moduleSources.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size },
            runtimeModuleSourceCount = request.runtimeModuleSources.size,
            runtimeModuleSourceBytes = request.runtimeModuleSources.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size
            },
            entrySourceBytes = request.entrySource?.toByteArray(StandardCharsets.UTF_8)?.size ?: -1,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = request.processPoolSlot,
            processPoolSlotSource = request.processPoolSlotSource,
            packageDir = request.packageDir?.absolutePath,
            binName = request.binName,
            scriptName = request.scriptName,
        )
    }

    fun describe(plan: ProjectLaunchPlan): Descriptor {
        return Descriptor(
            kind = when (plan.kind) {
                PROJECT_LAUNCH_KIND_ENTRY -> "project"
                else -> plan.kind
            },
            launchKind = plan.kind,
            projectRoot = plan.projectRoot.absolutePath,
            entryFile = plan.entryFile.absolutePath,
            workingDirectory = plan.workingDirectory.absolutePath,
            argv = plan.diagnosticArgv ?: plan.argv,
            envKeys = plan.diagnosticEnvKeys ?: plan.env.keys.sorted(),
            timeoutMs = plan.timeoutMs,
            moduleSourceCount = plan.moduleSources.size,
            moduleSourceBytes = plan.moduleSources.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size },
            runtimeModuleSourceCount = plan.runtimeModuleSources.size,
            runtimeModuleSourceBytes = plan.runtimeModuleSources.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size
            },
            entrySourceBytes = plan.entrySource?.toByteArray(StandardCharsets.UTF_8)?.size ?: -1,
            executionMode = NodeExecutionModePolicy.normalize(plan.executionMode),
            entryDirective = NodeEntryDirective.normalize(plan.entryDirective),
            processPoolSlot = plan.processPoolSlot,
            processPoolSlotSource = plan.processPoolSlotSource,
            packageDir = plan.packageDir?.absolutePath,
            binName = plan.binName,
            scriptName = plan.scriptName,
            scriptCommand = plan.scriptCommand,
            resolvedCommand = plan.resolvedCommand,
        )
    }

    fun describe(request: Request): Descriptor {
        return Descriptor(
            kind = "entry",
            launchKind = PROJECT_LAUNCH_KIND_ENTRY_DIRECT,
            projectRoot = request.projectRoot.absolutePath,
            entryFile = request.entryFile.absolutePath,
            workingDirectory = request.workingDirectory.absolutePath,
            argv = request.argv,
            envKeys = request.env.keys.sorted(),
            timeoutMs = request.timeoutMs,
            moduleSourceCount = request.moduleSources.size,
            moduleSourceBytes = request.moduleSources.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size },
            runtimeModuleSourceCount = request.runtimeModuleSources.size,
            runtimeModuleSourceBytes = request.runtimeModuleSources.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size
            },
            entrySourceBytes = request.entrySource?.toByteArray(StandardCharsets.UTF_8)?.size ?: -1,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = request.processPoolSlot,
            processPoolSlotSource = request.processPoolSlotSource,
        )
    }

    fun describe(
        request: PackageBinRequest,
        entryFile: File? = null,
    ): Descriptor {
        return Descriptor(
            kind = "package-bin",
            launchKind = PROJECT_LAUNCH_KIND_PACKAGE_BIN,
            projectRoot = request.projectRoot.absolutePath,
            entryFile = entryFile?.absolutePath.orEmpty(),
            workingDirectory = request.workingDirectory.absolutePath,
            argv = request.argv,
            envKeys = request.env.keys.sorted(),
            timeoutMs = request.timeoutMs,
            moduleSourceCount = request.moduleSources.size,
            moduleSourceBytes = request.moduleSources.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size },
            runtimeModuleSourceCount = request.runtimeModuleSources.size,
            runtimeModuleSourceBytes = request.runtimeModuleSources.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size
            },
            entrySourceBytes = -1,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = request.processPoolSlot,
            processPoolSlotSource = request.processPoolSlotSource,
            packageDir = request.packageDir.absolutePath,
            binName = request.binName,
        )
    }

    fun describe(
        request: PackageScriptRequest,
        entryFile: File? = null,
        binName: String? = null,
        scriptCommand: String? = null,
        resolvedCommand: String? = null,
    ): Descriptor {
        return Descriptor(
            kind = "package-script",
            launchKind = PROJECT_LAUNCH_KIND_PACKAGE_SCRIPT,
            projectRoot = request.projectRoot.absolutePath,
            entryFile = entryFile?.absolutePath.orEmpty(),
            workingDirectory = request.workingDirectory.absolutePath,
            argv = request.argv,
            envKeys = request.env.keys.sorted(),
            timeoutMs = request.timeoutMs,
            moduleSourceCount = request.moduleSources.size,
            moduleSourceBytes = request.moduleSources.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size },
            runtimeModuleSourceCount = request.runtimeModuleSources.size,
            runtimeModuleSourceBytes = request.runtimeModuleSources.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size
            },
            entrySourceBytes = -1,
            executionMode = NodeExecutionModePolicy.normalize(request.executionMode),
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = request.processPoolSlot,
            processPoolSlotSource = request.processPoolSlotSource,
            packageDir = request.packageDir.absolutePath,
            binName = binName,
            scriptName = request.scriptName,
            scriptCommand = scriptCommand,
            resolvedCommand = resolvedCommand,
        )
    }

    fun describe(
        request: Request,
        result: NodePluginScriptResult,
    ): String {
        return buildString {
            append(describe(request).toDebugString())
            appendLine("succeeded=${result.succeeded}")
            appendLine("errorCode=${result.errorCode}")
            appendLine("sourceName=${result.sourceName}")
            appendLine("errorMessage=${result.errorMessage}")
            appendLine("nativeValues=${result.nativeValues}")
            appendLine("stdout=${result.stdout}")
            appendLine("stderr=${result.stderr}")
        }
    }

    fun describe(
        request: ProjectRequest,
        result: NodePluginScriptResult,
        entryFile: File? = null,
    ): String {
        return buildString {
            append(describe(request, entryFile).toDebugString())
            appendLine("succeeded=${result.succeeded}")
            appendLine("errorCode=${result.errorCode}")
            appendLine("sourceName=${result.sourceName}")
            appendLine("errorMessage=${result.errorMessage}")
            appendLine("nativeValues=${result.nativeValues}")
            appendLine("stdout=${result.stdout}")
            appendLine("stderr=${result.stderr}")
        }
    }

    fun describe(
        request: PackageBinRequest,
        result: NodePluginScriptResult,
        entryFile: File? = null,
    ): String {
        return buildString {
            append(describe(request, entryFile).toDebugString())
            appendLine("succeeded=${result.succeeded}")
            appendLine("errorCode=${result.errorCode}")
            appendLine("sourceName=${result.sourceName}")
            appendLine("errorMessage=${result.errorMessage}")
            appendLine("nativeValues=${result.nativeValues}")
            appendLine("stdout=${result.stdout}")
            appendLine("stderr=${result.stderr}")
        }
    }

    fun describe(
        request: PackageScriptRequest,
        result: NodePluginScriptResult,
        entryFile: File? = null,
        binName: String? = null,
        scriptCommand: String? = null,
        resolvedCommand: String? = null,
    ): String {
        return buildString {
            append(describe(request, entryFile, binName, scriptCommand, resolvedCommand).toDebugString())
            appendLine("succeeded=${result.succeeded}")
            appendLine("errorCode=${result.errorCode}")
            appendLine("sourceName=${result.sourceName}")
            appendLine("errorMessage=${result.errorMessage}")
            appendLine("nativeValues=${result.nativeValues}")
            appendLine("stdout=${result.stdout}")
            appendLine("stderr=${result.stderr}")
        }
    }

    private fun buildBootstrap(
        request: Request,
        projectRoot: File,
        entryFile: File,
        workingDirectory: File,
    ): String {
        val argvLiteral = (listOf("autojs6-node", entryFile.canonicalPath) + request.argv)
            .joinToString(prefix = "[", postfix = "]") { value -> jsStringLiteral(value) }
        val relativeWorkingDirectory = relativePathInside(projectRoot, workingDirectory)
        val entryDirective = NodeEntryDirective.normalize(request.entryDirective)
            ?: NodeEntryDirective.DIRECTIVE_NODEJS
        return buildString {
            append(jsStringLiteral(entryDirective))
            append(";\n")
            append("process.argv.splice(0, process.argv.length, ...")
            append(argvLiteral)
            append(");\n")
            request.env.toSortedMap().forEach { (key, value) ->
                append("process.env[")
                append(jsStringLiteral(key))
                append("] = ")
                append(jsStringLiteral(value))
                append(";\n")
            }
            if (relativeWorkingDirectory != ".") {
                append("process.chdir(")
                append(jsStringLiteral(relativeWorkingDirectory))
                append(");\n")
            }
        }
    }

    private fun relativePathInside(root: File, target: File): String {
        val rootPath = root.path
        val targetPath = target.path
        if (targetPath == rootPath) {
            return "."
        }
        val prefix = rootPath + File.separator
        require(targetPath.startsWith(prefix)) { "Node project path escapes projectRoot: $targetPath" }
        return targetPath.removePrefix(prefix).replace(File.separatorChar, '/')
    }

    private fun resolveSandboxedScriptTarget(
        root: File,
        target: String,
        missingMessage: String,
        nulMessage: String,
        emptyMessage: String,
        escapeMessage: String,
        absoluteMessage: String,
        nativeAddonMessage: String,
        extensionMessage: String,
    ): File {
        require(!target.contains('\u0000')) { "$nulMessage: $target" }
        val normalized = target.replace('\\', '/')
        require(!normalized.startsWith("/") && !PROTOCOL_OR_WINDOWS_DRIVE_PATTERN.containsMatchIn(normalized)) {
            "$absoluteMessage: $target"
        }

        val parts = normalized.split('/')
            .filter { part -> part.isNotEmpty() && part != "." }
        require(parts.isNotEmpty()) { "$emptyMessage: $target" }
        require(parts.none { part -> part == ".." }) { "$escapeMessage: $target" }

        val entry = parts.fold(root) { current, part -> File(current, part) }.canonicalFile
        val rootPath = root.path
        require(entry.path == rootPath || entry.path.startsWith(rootPath + File.separator)) {
            "$escapeMessage: $target"
        }

        when (entry.extension.lowercase()) {
            "js", "cjs", "mjs", "ts", "cts", "mts" -> Unit
            "tsx" -> throw IllegalArgumentException("ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION: $extensionMessage: $target")
            "node" -> throw IllegalArgumentException("$nativeAddonMessage: $target")
            else -> throw IllegalArgumentException("$extensionMessage: $target")
        }
        require(entry.isFile) { "$missingMessage: $target" }
        return entry
    }

    private fun resolveNodePackageScriptCommand(
        request: PackageScriptRequest,
        scriptCommand: String,
        tokens: List<String>,
    ): ResolvedPackageScriptCommand {
        require(tokens.size >= 2) { "Package script node command requires a relative entry file" }
        val entryTarget = tokens[1]
        if (entryTarget.startsWith("-")) {
            throw IllegalArgumentException("Unsupported package script node option: $entryTarget")
        }
        val entryFile = resolveSandboxedScriptTarget(
            root = request.packageDir,
            target = entryTarget,
            missingMessage = "Package script node entry missing",
            nulMessage = "Package script node entry target contains NUL",
            emptyMessage = "Package script node entry target is empty",
            escapeMessage = "Package script node entry escapes package sandbox",
            absoluteMessage = "Package script node entry target must be relative",
            nativeAddonMessage = "Unsupported package script node native addon entry",
            extensionMessage = "Unsupported package script node entry extension",
        )
        return ResolvedPackageScriptCommand(
            entryFile = entryFile,
            argv = tokens.drop(2),
            binName = null,
            scriptCommand = scriptCommand,
            resolvedCommand = "node $entryTarget",
        )
    }

    private fun resolveBinPackageScriptCommand(
        request: PackageScriptRequest,
        scriptCommand: String,
        tokens: List<String>,
    ): ResolvedPackageScriptCommand {
        val binName = normalizePackageScriptCommandHead(tokens.first())
        val binPackageDir = resolvePackageScriptBinPackageDir(request.projectRoot, request.packageDir, binName)
        val entryFile = resolvePackageBinTarget(binPackageDir, binName)
        return ResolvedPackageScriptCommand(
            entryFile = entryFile,
            argv = tokens.drop(1),
            binName = binName,
            scriptCommand = scriptCommand,
            resolvedCommand = "$binName -> ${entryFile.absolutePath}",
        )
    }

    private fun buildPackageScriptLifecycleEnv(request: PackageScriptRequest): Map<String, String> {
        val packageJson = File(request.packageDir, "package.json")
        val json = JSONObject(packageJson.readText(StandardCharsets.UTF_8))
        return mapOf(
            "npm_lifecycle_event" to request.scriptName,
            "npm_package_json" to packageJson.canonicalPath,
            "npm_package_name" to json.optString("name"),
            "npm_package_version" to json.optString("version"),
            "INIT_CWD" to request.workingDirectory.canonicalPath,
        )
    }

    private fun auditPackageFeatureFlagMetadata(packageJson: File) {
        if (!packageJson.isFile) {
            return
        }
        val json = runCatching { JSONObject(packageJson.readText(StandardCharsets.UTF_8)) }.getOrNull() ?: return
        auditPackageFeatureFlagMetadata(packageJson, json)
    }

    private fun auditPackageFeatureFlagMetadata(packageJson: File, json: JSONObject) {
        NodeRuntimeCapabilities.requireNoRejectedFeatureFlagOverrides(
            NodeRuntimeCapabilities.auditPackageJsonFeatureFlags(json, packageJson.absolutePath),
        )
    }

    private fun packageScriptBinPackageCandidates(
        projectRoot: File,
        packageDir: File,
        binName: String,
    ): Sequence<File> {
        val nodeModulesDirs = nodeModulesDirectories(projectRoot, packageDir)
        return sequence {
            nodeModulesDirs.forEach { nodeModules ->
                directPackageBinCandidate(nodeModules, binName)?.let { yield(it) }
            }
            nodeModulesDirs.forEach { nodeModules ->
                if (!nodeModules.isDirectory) {
                    return@forEach
                }
                nodeModules.listFiles().orEmpty()
                    .filter { child -> child.isDirectory }
                    .forEach { child ->
                        if (child.name.startsWith("@")) {
                            child.listFiles().orEmpty()
                                .filter { scopedChild -> scopedChild.isDirectory }
                                .forEach { scopedChild -> yield(scopedChild.canonicalFile) }
                        } else {
                            yield(child.canonicalFile)
                        }
                    }
            }
        }.distinctBy { candidate -> candidate.canonicalPath }
    }

    private fun nodeModulesDirectories(projectRoot: File, packageDir: File): List<File> {
        val result = mutableListOf<File>()
        val root = projectRoot.canonicalFile
        var current: File? = packageDir.canonicalFile
        while (current != null) {
            result += File(current, "node_modules").canonicalFile
            if (current == root) {
                break
            }
            current = current.parentFile?.canonicalFile
            if (current == null || !isPathInsideOrSame(root, current)) {
                break
            }
        }
        return result.distinctBy { it.canonicalPath }
    }

    private fun directPackageBinCandidate(nodeModules: File, binName: String): File? {
        val candidate = if (isScopedPackageName(binName)) {
            val parts = binName.split('/', limit = 2)
            File(File(nodeModules, parts[0]), parts[1])
        } else {
            File(nodeModules, binName)
        }
        return candidate.takeIf { it.isDirectory }?.canonicalFile
    }

    private fun packageProvidesBin(packageDir: File, binName: String): Boolean {
        val packageJson = File(packageDir, "package.json")
        if (!packageJson.isFile) {
            return false
        }
        val json = runCatching { JSONObject(packageJson.readText(StandardCharsets.UTF_8)) }.getOrNull() ?: return false
        val binValue: Any? = json.opt("bin")
        return when (binValue) {
            is JSONObject -> binValue.has(binName)
            is String -> {
                val packageName = json.optString("name")
                binName == packageName || binName == packageName.substringAfterLast('/')
            }
            else -> false
        }
    }

    private fun validatePackageScriptCommandHead(command: String) {
        normalizePackageScriptCommandHead(command)
    }

    private fun normalizePackageScriptCommandHead(command: String): String {
        require(command.isNotBlank()) { "Package script command is empty" }
        require(!command.contains('\u0000')) { "Package script command contains NUL" }
        require(!PACKAGE_SCRIPT_ENV_ASSIGNMENT_PATTERN.matches(command)) {
            "Package script env assignment prefix is unsupported: $command"
        }
        require(!PROTOCOL_OR_WINDOWS_DRIVE_PATTERN.containsMatchIn(command)) {
            "Package script command must not be absolute or protocol based: $command"
        }
        require(!command.startsWith("/") && !command.startsWith("\\") && command != "." && command != "..") {
            "Package script command must be a package bin or node: $command"
        }
        require(!command.contains('\\')) {
            "Package script command must be a package bin or node: $command"
        }
        val normalizedCommand = normalizeNodeModulesBinCommand(command)
        require(command == "node" || isPackageBinCommandName(normalizedCommand)) {
            "Package script command must be a package bin or node: $command"
        }
        require(normalizedCommand !in PACKAGE_SCRIPT_DENIED_COMMANDS) {
            "Unsupported package script command: $command"
        }
        return normalizedCommand
    }

    private fun normalizeNodeModulesBinCommand(command: String): String {
        val relativeCommand = command.removePrefix("./")
        return if (relativeCommand.startsWith(PACKAGE_SCRIPT_NODE_MODULES_BIN_PREFIX)) {
            relativeCommand.removePrefix(PACKAGE_SCRIPT_NODE_MODULES_BIN_PREFIX)
        } else {
            command
        }
    }

    private fun isPackageBinCommandName(command: String): Boolean {
        return PACKAGE_BIN_COMMAND_PATTERN.matches(command) || isScopedPackageName(command)
    }

    private fun isScopedPackageName(command: String): Boolean {
        return SCOPED_PACKAGE_NAME_PATTERN.matches(command)
    }

    private fun normalizeRequest(request: Request): Request {
        val processPoolSlot = normalizeDescriptorProcessPoolSlot(request.processPoolSlot)
        return request.copy(
            projectRoot = request.projectRoot.canonicalFile,
            entryFile = request.entryFile.canonicalFile,
            workingDirectory = request.workingDirectory.canonicalFile,
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = processPoolSlot,
            processPoolSlotSource = normalizeDescriptorProcessPoolSlotSource(
                request.processPoolSlotSource,
                processPoolSlot,
            ),
        )
    }

    private fun normalizePackageScriptRequest(request: PackageScriptRequest): PackageScriptRequest {
        val processPoolSlot = normalizeDescriptorProcessPoolSlot(request.processPoolSlot)
        return request.copy(
            projectRoot = request.projectRoot.canonicalFile,
            packageDir = request.packageDir.canonicalFile,
            workingDirectory = request.workingDirectory.canonicalFile,
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = processPoolSlot,
            processPoolSlotSource = normalizeDescriptorProcessPoolSlotSource(
                request.processPoolSlotSource,
                processPoolSlot,
            ),
        )
    }

    private fun normalizePackageBinRequest(request: PackageBinRequest): PackageBinRequest {
        val processPoolSlot = normalizeDescriptorProcessPoolSlot(request.processPoolSlot)
        return request.copy(
            projectRoot = request.projectRoot.canonicalFile,
            packageDir = request.packageDir.canonicalFile,
            workingDirectory = request.workingDirectory.canonicalFile,
            entryDirective = NodeEntryDirective.normalize(request.entryDirective),
            processPoolSlot = processPoolSlot,
            processPoolSlotSource = normalizeDescriptorProcessPoolSlotSource(
                request.processPoolSlotSource,
                processPoolSlot,
            ),
        )
    }

    private fun normalizeDescriptorProcessPoolSlot(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeDescriptorProcessPoolSlotSource(
        value: String?,
        processPoolSlot: String?,
    ): String? {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (processPoolSlot == null) {
            require(normalized == null) {
                "Node project processPoolSlotSource requires processPoolSlot: $normalized"
            }
            return null
        }
        return normalized ?: "legacy_descriptor"
    }

    private fun projectRequestLaunchKind(request: ProjectRequest): String {
        return when {
            request.scriptName != null && request.binName != null -> PROJECT_LAUNCH_KIND_AMBIGUOUS
            request.scriptName != null -> PROJECT_LAUNCH_KIND_PACKAGE_SCRIPT
            request.binName != null -> PROJECT_LAUNCH_KIND_PACKAGE_BIN
            else -> PROJECT_LAUNCH_KIND_ENTRY
        }
    }

    private fun ProjectLaunchPlan.toEntryRequest(): Request {
        return normalizeRequest(
            Request(
                projectRoot = projectRoot,
                entryFile = entryFile,
                workingDirectory = workingDirectory,
                argv = argv,
                env = env,
                timeoutMs = timeoutMs,
                moduleSources = moduleSources,
                runtimeModuleSources = runtimeModuleSources,
                entrySource = entrySource,
                executionMode = executionMode,
                entryDirective = entryDirective,
                processPoolSlot = processPoolSlot,
                processPoolSlotSource = processPoolSlotSource,
            ),
        )
    }

    private fun illegalRequest(
        message: String,
        descriptor: Descriptor,
    ): IllegalArgumentException {
        return IllegalArgumentException(
            NodeProjectLaunchDiagnostics.runnerRequestFailureMarker(descriptor) +
                    "\n$message\nNode project runner request:\n${descriptor.toDebugString()}",
        )
    }

    private fun requirePathInside(
        root: File,
        target: File,
        label: String,
        descriptor: Descriptor,
    ) {
        val rootPath = root.path
        val targetPath = target.path
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw illegalRequest("$label escapes Node project root: $targetPath", descriptor)
        }
    }

    private fun isPathInsideOrSame(root: File, target: File): Boolean {
        val rootPath = root.path
        val targetPath = target.path
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }

    private fun stripShebang(source: String): String {
        if (!source.startsWith("#!")) {
            return source
        }
        val lineFeed = source.indexOf('\n')
        return if (lineFeed < 0) {
            ""
        } else {
            source.substring(lineFeed + 1)
        }
    }

    private fun jsStringLiteral(value: String): String {
        val escaped = StringBuilder(value.length + 2)
        escaped.append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\b' -> escaped.append("\\b")
                '\u000C' -> escaped.append("\\f")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        escaped.append("\\u")
                        escaped.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        escaped.append(char)
                    }
                }
            }
        }
        escaped.append('"')
        return escaped.toString()
    }

    private const val PROJECT_CONFIG_NAME = "project.json"
    private const val DEFAULT_PROJECT_MAIN = "main.js"
    private const val PROJECT_LAUNCH_KIND_AMBIGUOUS = "ambiguous"
    private const val PROJECT_LAUNCH_KIND_ENTRY = "project-entry"
    private const val PROJECT_LAUNCH_KIND_ENTRY_DIRECT = "entry"
    private const val PROJECT_LAUNCH_KIND_PACKAGE_BIN = "package-bin"
    private const val PROJECT_LAUNCH_KIND_PACKAGE_SCRIPT = "package-script"
    private val SHELL_OPERATOR_CHARS = setOf('&', '|', ';', '>', '<')
    private val PACKAGE_SCRIPT_DENIED_COMMANDS = setOf("npm", "npx", "pnpm", "yarn", "corepack")
    private const val PACKAGE_SCRIPT_NODE_MODULES_BIN_PREFIX = "node_modules/.bin/"
    private val PROJECT_MAIN_KEYS = listOf(
        "main",
        "mainName",
        "mainScript",
        "mainScriptName",
        "mainScriptFile",
        "mainScriptFileName",
        "mainFile",
        "mainFileName",
    )
    private val PROTOCOL_OR_WINDOWS_DRIVE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val PACKAGE_SCRIPT_ENV_ASSIGNMENT_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*=.*")
    private val PACKAGE_BIN_COMMAND_PATTERN = Regex("[A-Za-z0-9._~-]+")
    private val SCOPED_PACKAGE_NAME_PATTERN = Regex("@[A-Za-z0-9._~-]+/[A-Za-z0-9._~-]+")

    data class ResolvedPackageScriptCommand(
        val entryFile: File,
        val argv: List<String>,
        val binName: String?,
        val scriptCommand: String,
        val resolvedCommand: String,
    )
}
