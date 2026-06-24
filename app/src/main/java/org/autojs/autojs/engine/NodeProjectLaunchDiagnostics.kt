package org.autojs.autojs.engine

import org.autojs.autojs.script.NodeProjectLaunchKind
import org.autojs.autojs.script.NodeProjectLaunchSource
import java.io.File

object NodeProjectLaunchDiagnostics {

    const val LAUNCH_FAILURE_MARKER = "autojs6.node_project_launch.failure.v1"
    const val RUNNER_REQUEST_FAILURE_MARKER = "autojs6.node_project_runner.request_failure.v1"
    const val INRT_PROJECT_ENTRY_RESOLUTION_FAILURE_MARKER =
        "autojs6.inrt.node_project_entry_resolution.failure.v1"

    @JvmStatic
    fun launchFailureMarker(
        stage: String,
        projectRoot: File,
        entryFile: File?,
        launchSource: NodeProjectLaunchSource?,
        request: NativeNodeEmbeddedProjectRunner.ProjectRequest?,
        error: Throwable,
    ): String {
        val requestLaunchKind = request
            ?.let { NodeProjectLaunchKind.from(it.scriptName, it.binName).descriptorValue }
            .orEmpty()
        return buildString {
            append(LAUNCH_FAILURE_MARKER)
            append(" stage=").append(stage)
            append(" projectRoot=").append(projectRoot.path)
            append(" entry=").append(entryFile?.path.orEmpty())
            append(" requestReady=").append(request != null)
            append(" launchKind=").append(launchSource?.nodeProjectLaunchKind?.descriptorValue.orEmpty())
            append(" requestLaunchKind=").append(requestLaunchKind)
            append(" executionMode=").append(request?.executionMode ?: launchSource?.nodeProjectExecutionMode.orEmpty())
            append(" entryDirective=").append(request?.entryDirective ?: launchSource?.nodeProjectEntryDirective.orEmpty())
            append(" packageDir=").append(request?.packageDir?.path.orEmpty())
            append(" packageDirProjectRoot=").append(request?.packageDir?.canonicalPath == projectRoot.canonicalPath)
            append(" workingDirectory=").append(request?.workingDirectory?.path.orEmpty())
            append(" workingDirectoryProjectRoot=").append(request?.workingDirectory?.canonicalPath == projectRoot.canonicalPath)
            append(" argvCount=").append(request?.argv?.size ?: launchSource?.nodeProjectArgv?.size ?: 0)
            append(" script=").append(request?.scriptName ?: launchSource?.nodeProjectScriptName.orEmpty())
            append(" bin=").append(request?.binName ?: launchSource?.nodeProjectBinName.orEmpty())
            append(" envKeys=").append((request?.env?.keys ?: launchSource?.nodeProjectEnv?.keys.orEmpty()).sortedKeys())
            append(" errorClass=").append(error.javaClass.simpleName.ifBlank { error.javaClass.name })
        }
    }

    @JvmStatic
    fun runnerRequestFailureMarker(descriptor: NativeNodeEmbeddedProjectRunner.Descriptor): String {
        return buildString {
            append(RUNNER_REQUEST_FAILURE_MARKER)
            append(" kind=").append(descriptor.kind)
            append(" launchKind=").append(descriptor.launchKind.orEmpty())
            append(" requestReady=true")
            append(" executionMode=").append(descriptor.executionMode)
            append(" entryDirective=").append(descriptor.entryDirective.orEmpty())
            append(" packageDir=").append(descriptor.packageDir.orEmpty())
            append(" workingDirectory=").append(descriptor.workingDirectory)
            append(" argvCount=").append(descriptor.argv.size)
            append(" script=").append(descriptor.scriptName.orEmpty())
            append(" bin=").append(descriptor.binName.orEmpty())
            append(" envKeys=").append(descriptor.envKeys.sortedKeys())
        }
    }

    private fun Collection<String>.sortedKeys(): String {
        return sorted().joinToString(prefix = "[", postfix = "]")
    }
}
