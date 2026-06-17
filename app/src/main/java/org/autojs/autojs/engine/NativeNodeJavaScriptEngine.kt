package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.storage.file.TmpScriptFiles
import java.io.File
import java.util.Locale

class NativeNodeJavaScriptEngine(private val androidContext: Context) : RhinoJavaScriptEngine(androidContext) {

    override fun init() {
        NativeNodeProcessService.prewarm(androidContext)
        super.init()
    }

    override fun doExecution(source: JavaScriptSource): Any? {
        val target = resolveExecutionTarget(source)
        try {
            val file = target.file
            if (!file.isFile) {
                throw NativeNodeRuntimeUnavailableException(
                    "Native Node.js backend requires an existing script file: ${file.absolutePath}"
                )
            }

            val workingDirectory = cwd().takeUnless { it.isNullOrBlank() }
                ?: file.parent.orEmpty()
            val exitCode = NativeNodeProcessService.run(
                context = androidContext,
                argv = arrayOf("autojs6-node", file.absolutePath),
                workingDirectory = workingDirectory,
                outputSink = NativeNodeRuntimeBridge.consoleOutputSink(runtime.console),
            )
            if (exitCode != 0) {
                throw NativeNodeRuntimeUnavailableException(
                    "Native Node.js process exited with code $exitCode."
                )
            }
            return exitCode
        } finally {
            if (target.deleteAfterRun) {
                target.file.delete()
            }
        }
    }

    override fun toString(): String {
        return "NativeNodeJavaScriptEngine@" + Integer.toHexString(hashCode()) +
                "{id=" + id + ",source='" + getTag(TAG_SOURCE) + "',cwd='" + cwd() + "'}"
    }

    @Suppress("unused")
    private fun androidContext() = androidContext

    private fun resolveExecutionTarget(scriptSource: JavaScriptSource): ExecutionTarget {
        if (scriptSource is JavaScriptFileSource) {
            return ExecutionTarget(scriptSource.file, deleteAfterRun = false)
        }
        return ExecutionTarget(createTemporaryScriptFile(scriptSource), deleteAfterRun = true)
    }

    private fun createTemporaryScriptFile(scriptSource: JavaScriptSource): File {
        val dir = TmpScriptFiles.getNodeJsTmpDir(androidContext)
        if (!dir.exists() && !dir.mkdirs()) {
            throw NativeNodeRuntimeUnavailableException(
                "Native Node.js backend failed to create temporary script directory: ${dir.absolutePath}"
            )
        }
        val file = runCatching {
            File.createTempFile(buildTemporaryScriptFilePrefix(scriptSource), ".js", dir)
        }.getOrElse {
            throw NativeNodeRuntimeUnavailableException(
                "Native Node.js backend failed to create temporary script file in: ${dir.absolutePath}",
                it,
            )
        }
        return runCatching {
            PFiles.write(file, scriptSource.script)
            file
        }.getOrElse {
            file.delete()
            throw NativeNodeRuntimeUnavailableException(
                "Native Node.js backend failed to write temporary script file: ${file.absolutePath}",
                it,
            )
        }
    }

    private fun buildTemporaryScriptFilePrefix(scriptSource: JavaScriptSource): String {
        val niceName = scriptSource.name
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .ifBlank { "script" }
            .take(TEMP_SCRIPT_NAME_MAX_LENGTH)
        return "$TEMP_SCRIPT_PREFIX-$niceName-"
    }

    private data class ExecutionTarget(
        val file: File,
        val deleteAfterRun: Boolean,
    )

    private companion object {
        const val TEMP_SCRIPT_PREFIX = "autojs6-node"
        const val TEMP_SCRIPT_NAME_MAX_LENGTH = 48
    }

}

class NativeNodeRuntimeUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
