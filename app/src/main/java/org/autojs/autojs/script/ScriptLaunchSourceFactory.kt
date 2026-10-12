package org.autojs.autojs.script

import org.autojs.autojs.core.plugin.python.PythonExecutionInputPolicy
import org.autojs.autojs.engine.NodeProjectRunner
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import java.io.File

object ScriptLaunchSourceFactory {

    data class Launch(
        val source: ScriptSource,
        val workingDirectory: String,
        val projectFeatures: List<String>? = null,
    ) {
        val engineName: String
            get() = source.engineName.orEmpty()

        val nodeProjectLaunchSource: NodeProjectLaunchSource?
            get() = source as? NodeProjectLaunchSource

        val pythonProjectSource: PythonProjectSource?
            get() = source as? PythonProjectSource

        val nodeProjectLaunchKind: NodeProjectLaunchKind?
            get() = nodeProjectLaunchSource?.nodeProjectLaunchKind

        fun buildNodeProjectRequestOrNull(): NodeProjectRunner.ProjectRequest? {
            val launchSource = nodeProjectLaunchSource ?: return null
            return NodeProjectRunner.buildProjectRequest(launchSource)
        }

        @JvmOverloads
        fun createExecutionConfig(
            base: ExecutionConfig = ExecutionConfig(),
            overrideWorkingDirectory: Boolean = true,
        ): ExecutionConfig {
            val projectSource = pythonProjectSource
            val stdinSnapshot = projectSource?.stdin?.let { stdin ->
                PythonProjectLaunchPolicy.snapshotStdin(projectSource.projectRoot, stdin)
            }
            return base.apply {
                if (overrideWorkingDirectory || workingDirectory.isBlank()) {
                    workingDirectory = this@Launch.workingDirectory
                }
                projectFeatures?.let { features ->
                    scriptConfig.features = features
                }
                projectSource?.let { source ->
                    setArgument(
                        PythonExecutionInputPolicy.ENTRY_MODE_ARGUMENT,
                        source.entryMode.executionConfigValue(),
                    )
                    if (stdinSnapshot != null) {
                        setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, stdinSnapshot)
                    }
                }
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun createScriptLaunch(
        script: String,
        workingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch {
        return Launch(
            source = StringScriptSource(script),
            workingDirectory = workingDirectory,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createSourceLaunch(
        source: ScriptSource,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch {
        enforcePythonLaunchBoundary(*source.pythonLaunchLocations().toTypedArray())
        return Launch(
            source = source,
            workingDirectory = workingDirectory ?: source.launchWorkingDirectory(defaultWorkingDirectory),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createProjectEntryLaunch(
        projectRoot: File,
        entryFile: File,
        name: String = entryFile.nameWithoutExtension,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = projectRoot.normalizedPath(),
    ): Launch {
        PythonLaunchBoundaryPolicy.enforceDefaultOff(entryFile.path)
        val fileSource = NodeProjectScriptSourceFactory.createProjectEntry(projectRoot, entryFile, name)
        PythonLaunchBoundaryPolicy.enforceDefaultOff(fileSource.file.path)
        return Launch(
            source = fileSource,
            workingDirectory = workingDirectory ?: fileSource.launchWorkingDirectory(entryFile.path, defaultWorkingDirectory),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createNodeProjectLaunch(
        projectRoot: File,
        entryFile: File,
        name: String = entryFile.nameWithoutExtension,
        launchOptions: NodeProjectLaunchOptions = NodeProjectLaunchOptions(),
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = projectRoot.normalizedPath(),
    ): Launch {
        PythonLaunchBoundaryPolicy.enforceDefaultOff(entryFile.path)
        val fileSource = NodeProjectScriptSourceFactory.createProjectLaunch(
            projectRoot = projectRoot,
            entryFile = entryFile,
            name = name,
            launchOptions = launchOptions,
        )
        PythonLaunchBoundaryPolicy.enforceDefaultOff(fileSource.file.path)
        return Launch(
            source = fileSource,
            workingDirectory = workingDirectory ?: fileSource.launchWorkingDirectory(entryFile.path, defaultWorkingDirectory),
        )
    }

    /** Resolves a project directory for reusable foreground or background execution. */
    internal fun createAutoJsProjectLaunchForPythonPreview(
        projectRoot: File,
        workingDirectory: String?,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        val root = projectRoot.normalizedFile()
        require(root.isDirectory && ProjectConfig.isProject(root)) {
            "AUTOJS_PROJECT_CONTEXT_INVALID: Not an AutoJs6 project directory: ${root.path}"
        }

        val projectConfig = ProjectConfig.fromProjectDirForExecution(root.path)
        val legacyEntry = File(
            root,
            projectConfig?.mainScriptFileName ?: ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME,
        )
        val pythonAdmission = PythonProjectLaunchPolicy.inspect(root, legacyEntry)
        val launch = when {
            pythonAdmission != null -> createPythonProjectLaunchForPreview(
                admission = pythonAdmission,
                workingDirectory = workingDirectory,
                pythonPreviewEnabled = pythonPreviewEnabled,
            )
            else -> {
                val nodeEntry = NodeProjectRunner.resolveProjectEntryFileIfNodeProject(root)
                if (nodeEntry == null) {
                    require(legacyEntry.exists()) {
                        "AutoJs6 project main script does not exist: ${legacyEntry.absolutePath}"
                    }
                    createPathLaunchForPythonPreview(
                        path = legacyEntry.path,
                        preExecuteScript = null,
                        workingDirectory = workingDirectory ?: root.path,
                        defaultWorkingDirectory = root.path,
                        pythonPreviewEnabled = pythonPreviewEnabled,
                    )
                } else {
                    createNodeProjectLaunch(
                        projectRoot = root,
                        entryFile = nodeEntry,
                        launchOptions = NodeProjectLaunchDescriptor.readOptionsOrDefault(root),
                        workingDirectory = workingDirectory,
                        defaultWorkingDirectory = root.path,
                    )
                }
            }
        }
        return launch.copy(projectFeatures = projectConfig?.features?.toList())
    }

    @JvmStatic
    @JvmOverloads
    fun createNodeProjectRequestOrNull(
        path: String,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): NodeProjectRunner.ProjectRequest? {
        return createNodeProjectRequestOrNull(File(path), workingDirectory, defaultWorkingDirectory)
    }

    @JvmStatic
    @JvmOverloads
    fun createNodeProjectRequestOrNull(
        file: File,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): NodeProjectRunner.ProjectRequest? {
        return createFileLaunch(file, workingDirectory, defaultWorkingDirectory).buildNodeProjectRequestOrNull()
    }

    @JvmStatic
    fun createFileSource(file: File): ScriptSource {
        return createFileLaunch(file).source
    }

    @JvmStatic
    @JvmOverloads
    fun createPythonProjectLaunch(
        admission: PythonProjectLaunchAdmission,
        workingDirectory: String? = null,
    ): Launch = createPythonProjectLaunchForPreview(
        admission = admission,
        workingDirectory = workingDirectory,
        pythonPreviewEnabled = PythonRuntimePreviewFeature.isEnabled,
    )

    internal fun createPythonProjectLaunchForPreview(
        admission: PythonProjectLaunchAdmission,
        workingDirectory: String? = null,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        if (!pythonPreviewEnabled) {
            PythonProjectLaunchPolicy.rejectExecutionUntilWorkspaceSupport(admission)
        }
        val source = PythonProjectSource.create(
            projectRoot = admission.projectRoot,
            entryFile = admission.entryFile,
            timeoutMillis = admission.timeoutMillis,
            entryMode = admission.entryMode,
            stdin = admission.stdin,
            executionMode = admission.executionMode,
        )
        return Launch(
            source = source,
            workingDirectory = workingDirectory ?: source.projectRoot.normalizedPath(),
        )
    }

    /**
     * Default-off R3 boundary for callers whose semantic origin differs from
     * their temporary or cached file. This is a rejection guard, not an engine route.
     */
    @JvmStatic
    fun enforcePythonLaunchBoundary(vararg locations: String?) {
        locations.filterNotNull().forEach(PythonLaunchBoundaryPolicy::enforceDefaultOff)
    }

    /** Rejects Python content-provider launches even when their URI has no suffix. */
    @JvmStatic
    fun enforcePythonMimeBoundary(mimeType: String?) {
        PythonLaunchBoundaryPolicy.enforceDefaultOffMimeType(mimeType)
    }

    /** Validates an external URI without treating a local file URI as an unsupported provider. */
    @JvmStatic
    fun enforcePythonExternalUriBoundary(
        scheme: String?,
        uriPath: String?,
        uriText: String?,
        displayName: String?,
        explicitMimeType: String?,
        providerMimeType: String?,
    ) {
        val pathClassification = uriPath?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val displayClassification = displayName?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val uriClassification = uriText?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val classifications = listOf(pathClassification, displayClassification, uriClassification)
        if (PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED in classifications) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED)
        }
        val pythonMime = PythonLaunchBoundaryPolicy.isPythonMimeType(explicitMimeType) ||
            PythonLaunchBoundaryPolicy.isPythonMimeType(providerMimeType)
        val pythonHint = classifications.any { it != PythonLaunchClassification.NOT_PYTHON } || pythonMime
        if (!pythonHint) return

        val normalizedScheme = scheme?.trim()?.lowercase().orEmpty()
        val localFileUri = normalizedScheme.isEmpty() || normalizedScheme == "file"
        if (!localFileUri) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
        if (pathClassification != PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
        if (!PythonRuntimePreviewFeature.isEnabled) {
            PythonLaunchBoundaryPolicy.enforceDefaultOff(checkNotNull(uriPath))
        }
    }

    /**
     * Returns `py` only for a single imported Python source. Project trees and compiled Python
     * remain unsupported, while non-Python imports keep their legacy extension handling.
     */
    @JvmStatic
    fun resolvePythonImportExtension(
        displayName: String?,
        uriPath: String?,
        explicitMimeType: String?,
        providerMimeType: String?,
    ): String? {
        if (listOfNotNull(displayName, uriPath).any(::isProjectDescriptorName)) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.PROJECT_IMPORT_UNSUPPORTED)
        }
        val classifications = listOfNotNull(displayName, uriPath)
            .map(PythonLaunchBoundaryPolicy::classify)
        if (PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED in classifications) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED)
        }
        val pythonName = PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME in classifications
        val pythonMime = PythonLaunchBoundaryPolicy.isPythonMimeType(explicitMimeType) ||
            PythonLaunchBoundaryPolicy.isPythonMimeType(providerMimeType)
        if (!pythonName && !pythonMime) return null
        val displayClassification = displayName?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        if (
            displayName?.substringAfterLast('.', missingDelimiterValue = "")?.isNotEmpty() == true &&
            displayClassification != PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
        ) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
        return PythonScriptSource.EXTENSION
    }

    @JvmStatic
    @JvmOverloads
    fun createFileLaunch(
        file: File,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch {
        return createFileLaunchForPythonPreview(
            file = file,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
            pythonPreviewEnabled = PythonRuntimePreviewFeature.isEnabled,
        )
    }

    /**
     * Creates an editor/cache launch whose bytes live in [sourceFile] while [semanticPath]
     * identifies the user-visible local source. Non-file Python URIs remain unsupported.
     */
    @JvmStatic
    @JvmOverloads
    fun createSemanticFileLaunch(
        sourceFile: File,
        semanticPath: String?,
        semanticUri: String? = null,
        semanticDisplayName: String? = null,
        semanticMimeType: String? = null,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch = createSemanticFileLaunchForPythonPreview(
        sourceFile = sourceFile,
        semanticPath = semanticPath,
        semanticUri = semanticUri,
        semanticDisplayName = semanticDisplayName,
        semanticMimeType = semanticMimeType,
        workingDirectory = workingDirectory,
        defaultWorkingDirectory = defaultWorkingDirectory,
        pythonPreviewEnabled = PythonRuntimePreviewFeature.isEnabled,
    )

    internal fun createSemanticFileLaunchForPythonPreview(
        sourceFile: File,
        semanticPath: String?,
        semanticUri: String?,
        semanticDisplayName: String?,
        semanticMimeType: String?,
        workingDirectory: String?,
        defaultWorkingDirectory: String,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        val pathClassification = semanticPath?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val displayClassification = semanticDisplayName?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val uriClassification = semanticUri?.let(PythonLaunchBoundaryPolicy::classify)
            ?: PythonLaunchClassification.NOT_PYTHON
        val pythonMime = PythonLaunchBoundaryPolicy.isPythonMimeType(semanticMimeType)

        listOf(pathClassification, displayClassification, uriClassification).forEach { classification ->
            if (classification == PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED) {
                throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED)
            }
        }
        val pythonHint = pathClassification == PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME ||
            displayClassification == PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME ||
            uriClassification != PythonLaunchClassification.NOT_PYTHON ||
            pythonMime
        val uriScheme = semanticUri
            ?.substringBefore(':', missingDelimiterValue = "")
            ?.lowercase()
        if (pythonHint && !uriScheme.isNullOrEmpty() && uriScheme != "file") {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
        if (!pythonHint) {
            val launch = createFileLaunchForPythonPreview(
                file = sourceFile,
                workingDirectory = workingDirectory,
                defaultWorkingDirectory = defaultWorkingDirectory,
                pythonPreviewEnabled = pythonPreviewEnabled,
            )
            (launch.source as? TypeScriptFileSource)?.semanticFileName = sequenceOf(
                semanticDisplayName,
                semanticPath?.let(::File)?.name,
                semanticUri
                    ?.substringBefore('?')
                    ?.substringBefore('#')
                    ?.replace('\\', '/')
                    ?.substringAfterLast('/'),
            ).filterNotNull()
                .firstOrNull(JavaScriptSource::isRhinoTypeScriptFileName)
            return launch
        }
        if (pathClassification != PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
        if (!pythonPreviewEnabled) {
            PythonLaunchBoundaryPolicy.enforceDefaultOff(checkNotNull(semanticPath))
        }

        val semanticFile = File(checkNotNull(semanticPath)).canonicalFile
        val payloadFile = sourceFile.canonicalFile
        check(semanticFile.isFile && payloadFile.isFile) {
            "Python editor launch requires readable local source files"
        }
        val admission = PythonProjectLaunchPolicy.inspectContainingEntry(semanticFile)
        val source = if (admission != null) {
            PythonProjectSource.createWithSource(
                projectRoot = admission.projectRoot,
                entryFile = admission.entryFile,
                sourceFile = payloadFile,
                timeoutMillis = admission.timeoutMillis,
                entryMode = admission.entryMode,
                stdin = admission.stdin,
                executionMode = admission.executionMode,
            )
        } else {
            PythonFileSource(payloadFile, semanticFile).apply {
                overriddenFullPath = semanticFile.path
            }
        }
        source.originUri = semanticUri
        return Launch(
            source = source,
            workingDirectory = admission?.projectRoot?.normalizedPath()
                ?: workingDirectory
                ?: semanticFile.parent
                ?: defaultWorkingDirectory,
        )
    }

    internal fun createFileLaunchForPythonPreview(
        file: File,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        val pythonLaunch = createLocalPythonLaunchOrNull(
            file = file,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
            pythonPreviewEnabled = pythonPreviewEnabled,
        )
        if (pythonLaunch != null) {
            return pythonLaunch
        }
        // Preserve Python's stable boundary errors before interpreting a URI-like string as a
        // platform File. On Windows in particular, canonicalizing `content://...` can fail first.
        PythonLaunchBoundaryPolicy.enforceDefaultOff(file.path)
        file.requireNoCanonicalJvmRoutingMismatch()
        createJvmFileLaunchOrNull(
            file = file,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
        )?.let { return it }
        rejectImplicitPythonProject(file)
        if (LuaScriptSource.isLuaFileName(file.name) || JavaScriptSource.isJavaScriptFileName(file.name)) {
            return createPathLaunchForPythonPreview(
                path = file.path,
                workingDirectory = workingDirectory,
                defaultWorkingDirectory = defaultWorkingDirectory,
                pythonPreviewEnabled = pythonPreviewEnabled,
            )
        }
        val normalizedFile = file.normalizedFile()
        PythonLaunchBoundaryPolicy.enforceDefaultOff(normalizedFile.path)
        return Launch(
            source = AutoFileSource(normalizedFile),
            workingDirectory = workingDirectory
                ?: normalizedFile.parent
                ?: defaultWorkingDirectory,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createPathLaunch(
        path: String,
        preExecuteScript: String? = null,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch {
        return createPathLaunchForPythonPreview(
            path = path,
            preExecuteScript = preExecuteScript,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
            pythonPreviewEnabled = PythonRuntimePreviewFeature.isEnabled,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createPersistedPathLaunch(
        path: String,
        sourceKind: String? = null,
        projectRoot: String? = null,
        preExecuteScript: String? = null,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch = createPersistedPathLaunchForPythonPreview(
        path = path,
        sourceKind = sourceKind,
        projectRoot = projectRoot,
        preExecuteScript = preExecuteScript,
        workingDirectory = workingDirectory,
        defaultWorkingDirectory = defaultWorkingDirectory,
        pythonPreviewEnabled = PythonRuntimePreviewFeature.isEnabled,
    )

    internal fun createPersistedPathLaunchForPythonPreview(
        path: String,
        sourceKind: String?,
        projectRoot: String?,
        preExecuteScript: String?,
        workingDirectory: String?,
        defaultWorkingDirectory: String,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        if (sourceKind == null) {
            if (projectRoot != null) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
            }
            return createPathLaunchForPythonPreview(
                path,
                preExecuteScript,
                workingDirectory,
                defaultWorkingDirectory,
                pythonPreviewEnabled,
            )
        }
        return when (sourceKind) {
            PersistedScriptLaunchPolicy.AUTOJS_PROJECT_V1 -> {
                val declaredRoot = projectRoot?.takeIf(String::isNotBlank)?.let(::File)
                    ?: throw autoJsProjectContextInvalid()
                val requestedRoot = File(path).canonicalFile
                val normalizedDeclaredRoot = declaredRoot.canonicalFile
                if (
                    preExecuteScript != null ||
                    requestedRoot != normalizedDeclaredRoot ||
                    !normalizedDeclaredRoot.isDirectory ||
                    !ProjectConfig.isProject(normalizedDeclaredRoot)
                ) {
                    throw autoJsProjectContextInvalid()
                }
                createAutoJsProjectLaunchForPythonPreview(
                    projectRoot = normalizedDeclaredRoot,
                    workingDirectory = workingDirectory,
                    pythonPreviewEnabled = pythonPreviewEnabled,
                )
            }
            PersistedScriptLaunchPolicy.LUA_FILE_V1 -> {
                if (projectRoot != null ||
                    preExecuteScript != null ||
                    !LuaScriptSource.isLuaFileName(File(path).name)
                ) {
                    throw IllegalArgumentException(
                        "LUA_PERSISTED_CONTEXT_INVALID: Persisted Lua launch context no longer matches its path",
                    )
                }
                createPathLaunchForPythonPreview(
                    path,
                    preExecuteScript,
                    workingDirectory,
                    defaultWorkingDirectory,
                    pythonPreviewEnabled,
                )
            }
            PersistedScriptLaunchPolicy.PYTHON_FILE_V1 -> {
                if (projectRoot != null ||
                    PythonLaunchBoundaryPolicy.classify(path) !=
                    PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
                ) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
                }
                createPathLaunchForPythonPreview(
                    path,
                    preExecuteScript,
                    workingDirectory,
                    defaultWorkingDirectory,
                    pythonPreviewEnabled,
                )
            }
            PersistedScriptLaunchPolicy.PYTHON_PROJECT_V1 -> {
                if (projectRoot.isNullOrBlank()) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
                }
                if (preExecuteScript != null) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.PRE_EXECUTE_UNSUPPORTED)
                }
                val entry = File(path).canonicalFile
                val admission = PythonProjectLaunchPolicy.inspect(File(projectRoot), entry)
                    ?: throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
                if (admission.entryFile != entry) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
                }
                createPythonProjectLaunchForPreview(admission, workingDirectory, pythonPreviewEnabled)
            }
            else -> throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
        }
    }

    private fun autoJsProjectContextInvalid(): IllegalArgumentException = IllegalArgumentException(
        "AUTOJS_PROJECT_CONTEXT_INVALID: Persisted project launch context no longer matches its directory",
    )

    @JvmStatic
    fun capturePersistedLaunchContext(path: String): PersistedScriptLaunchContext =
        PersistedScriptLaunchPolicy.capture(path)

    internal fun createPathLaunchForPythonPreview(
        path: String,
        preExecuteScript: String? = null,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
        pythonPreviewEnabled: Boolean,
    ): Launch {
        val pathClassification = PythonLaunchBoundaryPolicy.classify(path)
        if (
            preExecuteScript != null &&
            pythonPreviewEnabled &&
            pathClassification == PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
        ) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.PRE_EXECUTE_UNSUPPORTED)
        }
        if (preExecuteScript == null) {
            val pythonLaunch = createLocalPythonLaunchOrNull(
                file = File(path),
                workingDirectory = workingDirectory,
                defaultWorkingDirectory = defaultWorkingDirectory,
                pythonPreviewEnabled = pythonPreviewEnabled,
            )
            if (pythonLaunch != null) {
                return pythonLaunch
            }
        }
        // Python routes own their rejection semantics; JVM canonical-path hardening applies only
        // after this guard has established that the request is not a Python source or URI.
        PythonLaunchBoundaryPolicy.enforceDefaultOff(path)
        val requestedFile = File(path)
        requestedFile.requireNoCanonicalJvmRoutingMismatch()
        createJvmFileLaunchOrNull(
            file = requestedFile,
            preExecuteScript = preExecuteScript,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
        )?.let { return it }
        rejectImplicitPythonProject(File(path))
        if (LuaScriptSource.isLuaFileName(File(path).name)) {
            require(preExecuteScript == null) {
                "Lua launch source does not support JavaScript preExecuteScript"
            }
            val fileSource = LuaFileSource(File(path).normalizedFile())
            return Launch(
                source = fileSource,
                workingDirectory = workingDirectory
                    ?: fileSource.file.parent
                    ?: defaultWorkingDirectory,
            )
        }
        if (AutoFileSource.isAutoFileName(File(path).name)) {
            // Auto files are binary input-event recordings replayed by RootAutomatorEngine.
            // They must never be wrapped as JavaScript sources.
            // zh-CN: .auto 文件是由 RootAutomatorEngine 回放的二进制输入事件录制, 不能被包装为 JavaScript 源.
            require(preExecuteScript == null) {
                "Auto file launch source does not support JavaScript preExecuteScript"
            }
            val autoFile = File(path).normalizedFile()
            PythonLaunchBoundaryPolicy.enforceDefaultOff(autoFile.path)
            return Launch(
                source = AutoFileSource(autoFile),
                workingDirectory = workingDirectory
                    ?: autoFile.parent
                    ?: defaultWorkingDirectory,
            )
        }
        val fileSource = NodeProjectScriptSourceFactory.create(path)
        PythonLaunchBoundaryPolicy.enforceDefaultOff(fileSource.file.path)
        val source = if (preExecuteScript == null) {
            fileSource
        } else {
            require(fileSource.engineName != JavaScriptSource.ENGINE_QUICKJS) {
                "QuickJS launch source does not support AutoJs6 preExecuteScript"
            }
            require(fileSource.engineName != JavaScriptSource.ENGINE_BUN) {
                "Bun launch source does not support AutoJs6 preExecuteScript"
            }
            require(fileSource !is NodeProjectLaunchSource) {
                "Node project launch source does not support preExecuteScript; " +
                        "run the project entry without preExecuteScript or run a concrete project file."
            }
            SequenceScriptSource(fileSource.name, StringScriptSource(preExecuteScript), fileSource)
        }
        return Launch(
            source = source,
            workingDirectory = workingDirectory ?: fileSource.launchWorkingDirectory(path, defaultWorkingDirectory),
        )
    }

    /**
     * Returns a Python launch only when both the caller-visible and canonical local paths are
     * Python source names. All mismatches continue through the unconditional fail-closed guard.
     */
    private fun createLocalPythonLaunchOrNull(
        file: File,
        workingDirectory: String?,
        defaultWorkingDirectory: String,
        pythonPreviewEnabled: Boolean,
    ): Launch? {
        if (PythonLaunchBoundaryPolicy.classify(file.path) !=
            PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
        ) {
            return null
        }
        if (!pythonPreviewEnabled) return null
        val normalizedFile = file.normalizedFile()
        if (PythonLaunchBoundaryPolicy.classify(normalizedFile.path) !=
            PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
        ) {
            return null
        }
        return Launch(
            source = PythonFileSource(normalizedFile),
            workingDirectory = workingDirectory
                ?: normalizedFile.parent
                ?: defaultWorkingDirectory,
        )
    }

    private fun rejectImplicitPythonProject(file: File) {
        val projectRoot = when {
            file.isDirectory -> file
            file.name.equals("project.json", ignoreCase = true) -> file.parentFile ?: return
            else -> return
        }
        val admission = PythonProjectLaunchPolicy.inspect(projectRoot, legacyMain = null) ?: return
        require(admission.projectRoot.isDirectory) { "Python project admission is not normalized" }
        throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_REQUIRED)
    }

    private fun isProjectDescriptorName(location: String): Boolean {
        val fileName = location
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('\\')
        return fileName.equals("project.json", ignoreCase = true)
    }

    private fun JavaScriptFileSource.launchWorkingDirectory(
        path: String,
        defaultWorkingDirectory: String,
    ): String {
        val launchSource = this as? NodeProjectLaunchSource
        return launchSource
            ?.nodeProjectWorkingDirectoryFile
            ?.normalizedPath()
            ?: launchSource
                ?.nodeProjectRootFile
                ?.normalizedPath()
            ?: File(path).parent
            ?: defaultWorkingDirectory
    }

    private fun ScriptSource.launchWorkingDirectory(
        defaultWorkingDirectory: String,
    ): String {
        val launchSource = this as? NodeProjectLaunchSource
        if (launchSource != null) {
            return launchSource.nodeProjectWorkingDirectoryFile?.normalizedPath()
                ?: launchSource.nodeProjectRootFile.normalizedPath()
        }
        return when (this) {
            is JavaScriptFileSource -> file.parent ?: defaultWorkingDirectory
            is JvmFileSource -> file.parent ?: defaultWorkingDirectory
            is LuaFileSource -> file.parent ?: defaultWorkingDirectory
            is PythonProjectSource -> projectRoot.normalizedPath()
            is PythonFileSource -> file.parent ?: defaultWorkingDirectory
            is AutoFileSource -> file.parent ?: defaultWorkingDirectory
            else -> defaultWorkingDirectory
        }
    }

    private fun ScriptSource.pythonLaunchLocations(): List<String?> {
        val locations = mutableListOf<String?>(originPath, originUri)
        when (this) {
            is JavaScriptFileSource -> {
                locations += file.path
                locations += file.normalizedPath()
            }
            is AutoFileSource -> {
                locations += file.path
                locations += file.normalizedPath()
            }
            is JvmFileSource -> {
                locations += file.path
                locations += file.normalizedPath()
            }
            is LuaFileSource -> {
                locations += file.path
                locations += file.normalizedPath()
            }
            is PythonProjectSource -> {
                locations += projectRoot.path
                locations += entryPoint
                locations += file.path
                locations += file.normalizedPath()
            }
            is PythonFileSource -> {
                locations += file.path
                locations += file.normalizedPath()
            }
        }
        return locations.distinct()
    }

    private fun createJvmFileLaunchOrNull(
        file: File,
        preExecuteScript: String? = null,
        workingDirectory: String?,
        defaultWorkingDirectory: String,
    ): Launch? {
        if (JvmSourceKind.fromFileName(file.name) == null) {
            if (JvmSourceKind.isUnsupportedJvmFileName(file.name)) {
                throw UnsupportedJvmSourceException(file.path)
            }
            return null
        }
        require(preExecuteScript == null) {
            "JVM source launch does not support preExecuteScript; run the source file without a JavaScript prefix."
        }
        return Launch(
            source = JvmFileSource(file),
            workingDirectory = workingDirectory
                ?: file.parent
                ?: defaultWorkingDirectory,
        )
    }

    private fun File.normalizedPath(): String {
        return runCatching { canonicalPath }.getOrElse { absolutePath }
    }

    private fun File.requireNoCanonicalJvmRoutingMismatch() {
        val canonical = runCatching { canonicalFile }.getOrElse {
            throw UnsafeJvmSourcePathException(
                path,
                "the canonical routing target cannot be resolved",
            )
        }
        // Resolve common aliased prefixes such as /sdcard. Refuse only a routing-class change.
        if (JvmSourceKind.hasCanonicalJvmRoutingMismatch(name, canonical.name)) {
            throw UnsafeJvmSourcePathException(
                path,
                "requested file type differs from canonical JVM source or artifact '${canonical.name}'",
            )
        }
    }

    private fun File.normalizedFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }

    private fun PythonEntryMode.executionConfigValue(): String = when (this) {
        PythonEntryMode.FILE -> "file"
        PythonEntryMode.MODULE -> "module"
    }
}
