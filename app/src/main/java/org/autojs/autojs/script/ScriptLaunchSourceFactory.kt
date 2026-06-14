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
        if (JavaScriptSource.isJavaScriptFileName(file.name)) {
            return createPathLaunch(
                path = file.path,
                workingDirectory = workingDirectory,
                defaultWorkingDirectory = defaultWorkingDirectory,
            )
        }
        val normalizedFile = file.normalizedFile()
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
            is AutoFileSource -> file.parent ?: defaultWorkingDirectory
            else -> defaultWorkingDirectory
        }
    }

    private fun File.normalizedPath(): String {
        return runCatching { canonicalPath }.getOrElse { absolutePath }
    }

    private fun File.normalizedFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }
}
