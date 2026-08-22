package org.autojs.autojs.model.script

import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.autojs.util.FileUtils
import java.io.File

/** Keeps script pickers aligned with the engines admitted by the current host build. */
internal object ScriptFileFilterPolicy {

    fun accepts(file: File, pythonEnabled: Boolean): Boolean {
        return file.isDirectory ||
            JavaScriptSource.isJavaScriptFileName(file.name) ||
            JvmSourceKind.fromFileName(file.name) != null ||
            LuaScriptSource.isLuaFileName(file.name) ||
            file.name.endsWith(FileUtils.TYPE.AUTO.extension, ignoreCase = true) ||
            (pythonEnabled && file.name.endsWith(".${PythonScriptSource.EXTENSION}", ignoreCase = true))
    }
}
