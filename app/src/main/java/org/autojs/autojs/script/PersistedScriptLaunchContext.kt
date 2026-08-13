package org.autojs.autojs.script

import java.io.File

/** Versioned local launch context stored by shortcuts and scheduled tasks. */
data class PersistedScriptLaunchContext(
    val sourceKind: String?,
    val projectRoot: String?,
)

internal object PersistedScriptLaunchPolicy {

    const val LUA_FILE_V1 = "lua_file_v1"
    const val PYTHON_FILE_V1 = "python_file_v1"
    const val PYTHON_PROJECT_V1 = "python_project_v1"

    fun capture(path: String): PersistedScriptLaunchContext {
        val file = File(path).canonicalFile
        if (LuaScriptSource.isLuaFileName(file.name)) {
            return PersistedScriptLaunchContext(LUA_FILE_V1, null)
        }
        return when (PythonLaunchBoundaryPolicy.classify(file.path)) {
            PythonLaunchClassification.NOT_PYTHON -> PersistedScriptLaunchContext(null, null)
            PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME -> {
                val admission = PythonProjectLaunchPolicy.inspectContainingEntry(file)
                if (admission == null) {
                    PersistedScriptLaunchContext(PYTHON_FILE_V1, null)
                } else {
                    PersistedScriptLaunchContext(
                        sourceKind = PYTHON_PROJECT_V1,
                        projectRoot = admission.projectRoot.path,
                    )
                }
            }
            PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED ->
                throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED)
            PythonLaunchClassification.PYTHON_URI_UNSUPPORTED ->
                throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
    }
}
