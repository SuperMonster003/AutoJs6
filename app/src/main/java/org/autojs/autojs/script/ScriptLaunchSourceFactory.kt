package org.autojs.autojs.script

import org.autojs.autojs.engine.NativeNodeEmbeddedProjectRunner
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.util.WorkingDirectoryUtils
import java.io.File

object ScriptLaunchSourceFactory {

    data class Launch(
        val source: ScriptSource,
        val workingDirectory: String,
    ) {
        val engineName: String
            get() = source.engineName.orEmpty()

        val nodeProjectLaunchSource: NodeProjectLaunchSource?
            get() = source as? NodeProjectLaunchSource

        val nodeProjectLaunchKind: NodeProjectLaunchKind?
            get() = nodeProjectLaunchSource?.nodeProjectLaunchKind

        fun buildNodeProjectRequestOrNull(): NativeNodeEmbeddedProjectRunner.ProjectRequest? {
            val launchSource = nodeProjectLaunchSource ?: return null
            return NativeNodeEmbeddedProjectRunner.buildProjectRequest(launchSource)
        }

        @JvmOverloads
        fun createExecutionConfig(
            base: ExecutionConfig = ExecutionConfig(),
            overrideWorkingDirectory: Boolean = true,
        ): ExecutionConfig {
            return base.apply {
                if (overrideWorkingDirectory || workingDirectory.isBlank()) {
                    workingDirectory = this@Launch.workingDirectory
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
        val fileSource = NodeProjectScriptSourceFactory.createProjectEntry(projectRoot, entryFile, name)
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
        val fileSource = NodeProjectScriptSourceFactory.createProjectLaunch(
            projectRoot = projectRoot,
            entryFile = entryFile,
            name = name,
            launchOptions = launchOptions,
        )
        return Launch(
            source = fileSource,
            workingDirectory = workingDirectory ?: fileSource.launchWorkingDirectory(entryFile.path, defaultWorkingDirectory),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun createNodeProjectRequestOrNull(
        path: String,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): NativeNodeEmbeddedProjectRunner.ProjectRequest? {
        return createNodeProjectRequestOrNull(File(path), workingDirectory, defaultWorkingDirectory)
    }

    @JvmStatic
    @JvmOverloads
    fun createNodeProjectRequestOrNull(
        file: File,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): NativeNodeEmbeddedProjectRunner.ProjectRequest? {
        return createFileLaunch(file, workingDirectory, defaultWorkingDirectory).buildNodeProjectRequestOrNull()
    }

    @JvmStatic
    fun createFileSource(file: File): ScriptSource {
        return createFileLaunch(file).source
    }

    @JvmStatic
    @JvmOverloads
    fun createFileLaunch(
        file: File,
        workingDirectory: String? = null,
        defaultWorkingDirectory: String = WorkingDirectoryUtils.path,
    ): Launch {
        file.requireNoCanonicalJvmRoutingMismatch()
        createJvmFileLaunchOrNull(
            file = file,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
        )?.let { return it }
        val normalizedFile = file.normalizedFile()
        if (JavaScriptSource.isJavaScriptFileName(file.name)) {
            return createPathLaunch(
                path = file.path,
                workingDirectory = workingDirectory,
                defaultWorkingDirectory = defaultWorkingDirectory,
            )
        }
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
        val requestedFile = File(path)
        requestedFile.requireNoCanonicalJvmRoutingMismatch()
        createJvmFileLaunchOrNull(
            file = requestedFile,
            preExecuteScript = preExecuteScript,
            workingDirectory = workingDirectory,
            defaultWorkingDirectory = defaultWorkingDirectory,
        )?.let { return it }
        val fileSource = NodeProjectScriptSourceFactory.create(path)
        val source = if (preExecuteScript == null) {
            fileSource
        } else {
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
            is AutoFileSource -> file.parent ?: defaultWorkingDirectory
            else -> defaultWorkingDirectory
        }
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
        // Symlinked prefixes (/sdcard, /data/user/0, ...) are resolved rather than rejected;
        // only a routing-class change after canonicalization is refused (Roadmap v2 M1).
        // zh-CN: 符号链接前缀 (/sdcard 等) 做解析而非拒绝; 仅拦截规范化后路由类型变化的请求.
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
}
