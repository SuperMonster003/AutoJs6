package org.autojs.autojs.script

import java.io.File
import java.util.Collections

enum class NodeProjectLaunchKind(val descriptorValue: String) {
    PROJECT_ENTRY("project-entry"),
    PACKAGE_SCRIPT("package-script"),
    PACKAGE_BIN("package-bin"),
    AMBIGUOUS("ambiguous");

    companion object {
        fun from(scriptName: String?, binName: String?): NodeProjectLaunchKind {
            return when {
                scriptName != null && binName != null -> AMBIGUOUS
                scriptName != null -> PACKAGE_SCRIPT
                binName != null -> PACKAGE_BIN
                else -> PROJECT_ENTRY
            }
        }

        fun fromDescriptorValue(value: String): NodeProjectLaunchKind {
            return values().firstOrNull { kind -> kind.descriptorValue == value }
                ?: throw IllegalArgumentException("Unsupported Node project launch kind: $value")
        }
    }
}

interface NodeProjectLaunchSource {
    val nodeProjectRootFile: File
    val nodeProjectLaunchKind: NodeProjectLaunchKind
        get() = NodeProjectLaunchKind.from(nodeProjectScriptName, nodeProjectBinName)
    val nodeProjectPackageDirFile: File?
        get() = null
    val nodeProjectWorkingDirectoryFile: File?
        get() = null
    val nodeProjectScriptName: String?
        get() = null
    val nodeProjectBinName: String?
        get() = null
    val nodeProjectArgv: List<String>
        get() = emptyList()
    val nodeProjectEnv: Map<String, String>
        get() = emptyMap()
    val nodeProjectTimeoutMs: Long?
        get() = null
    val nodeProjectExecutionMode: String?
        get() = null
    val nodeProjectEntryDirective: String?
        get() = null
    val nodeProjectProcessPoolSlot: String?
        get() = null
    val nodeProjectProcessPoolSlotSource: String?
        get() = null
}

data class NodeProjectLaunchOptions @JvmOverloads constructor(
    val packageDir: File? = null,
    val workingDirectory: File? = null,
    val scriptName: String? = null,
    val binName: String? = null,
    val argv: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val timeoutMs: Long? = null,
    val executionMode: String? = null,
    val entryDirective: String? = null,
    val processPoolSlot: String? = null,
    val processPoolSlotSource: String? = null,
) {
    val launchKind: NodeProjectLaunchKind
        get() = NodeProjectLaunchKind.from(scriptName, binName)
}

class NodeProjectJavaScriptFileSource @JvmOverloads constructor(
    projectRoot: File,
    file: File,
    name: String = file.nameWithoutExtension,
    launchOptions: NodeProjectLaunchOptions = NodeProjectLaunchOptions(),
) : JavaScriptFileSource(name, file), NodeProjectLaunchSource {

    override val nodeProjectRootFile: File = projectRoot.normalizedCanonicalFile()
    private val nodeProjectLaunchOptions = launchOptions.normalized()

    override val nodeProjectLaunchKind: NodeProjectLaunchKind = nodeProjectLaunchOptions.launchKind

    override val nodeProjectPackageDirFile: File?
        get() = nodeProjectLaunchOptions.packageDir

    override val nodeProjectWorkingDirectoryFile: File?
        get() = nodeProjectLaunchOptions.workingDirectory

    override val nodeProjectScriptName: String?
        get() = nodeProjectLaunchOptions.scriptName

    override val nodeProjectBinName: String?
        get() = nodeProjectLaunchOptions.binName

    override val nodeProjectArgv: List<String>
        get() = nodeProjectLaunchOptions.argv

    override val nodeProjectEnv: Map<String, String>
        get() = nodeProjectLaunchOptions.env

    override val nodeProjectTimeoutMs: Long?
        get() = nodeProjectLaunchOptions.timeoutMs

    override val nodeProjectExecutionMode: String?
        get() = nodeProjectLaunchOptions.executionMode

    override val nodeProjectEntryDirective: String?
        get() = nodeProjectLaunchOptions.entryDirective
            ?: NodeEntryDirective.fromExecutionMode(executionMode)

    override val nodeProjectProcessPoolSlot: String?
        get() = nodeProjectLaunchOptions.processPoolSlot

    override val nodeProjectProcessPoolSlotSource: String?
        get() = nodeProjectLaunchOptions.processPoolSlotSource

    override val engineName: String
        get() {
            require(super.engineName != JavaScriptSource.ENGINE_QUICKJS) {
                "A QuickJS entry cannot be launched through the Node project runtime"
            }
            require(super.engineName != JavaScriptSource.ENGINE_BUN) {
                "A Bun entry cannot be launched through the Node project runtime"
            }
            return JavaScriptSource.ENGINE_NODEJS
        }

    override fun getNodeProjectRoot(): File = nodeProjectRootFile

    override fun toString(): String = originPath ?: originUri
        ?: if (nodeProjectLaunchKind == NodeProjectLaunchKind.PROJECT_ENTRY) file.absolutePath else super.toString()

    override val elegantPath: String
        get() = if (nodeProjectLaunchKind == NodeProjectLaunchKind.PROJECT_ENTRY) {
            overriddenFullPath?.let { "$it [cache]" } ?: fullPath
        } else super.elegantPath

    private fun NodeProjectLaunchOptions.normalized(): NodeProjectLaunchOptions {
        return copy(
            packageDir = packageDir?.normalizedCanonicalFile(),
            workingDirectory = workingDirectory?.normalizedCanonicalFile(),
            argv = Collections.unmodifiableList(argv.toList()),
            env = Collections.unmodifiableMap(env.toMap()),
            executionMode = executionMode?.trim()?.takeIf { it.isNotBlank() },
            entryDirective = NodeEntryDirective.normalize(entryDirective),
            processPoolSlot = processPoolSlot?.trim()?.takeIf { it.isNotBlank() },
            processPoolSlotSource = processPoolSlotSource?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun File.normalizedCanonicalFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }
}
