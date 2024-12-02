package org.autojs.autojs.runtime.api.augment.plugins

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_require
import org.mozilla.javascript.BaseFunction
import kotlin.text.RegexOption.IGNORE_CASE

@Suppress("unused", "UNUSED_PARAMETER")
class Plugins(scriptRuntime: ScriptRuntime) : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        ::load.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun load(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) { o ->
            // require(o is String) { "Argument name for plugins.load must be a string" }
            val name = coerceString(o)
            val asPackageName = name.contains('.') && !name.matches(Regex(".+\\.js", IGNORE_CASE))
            when {
                asPackageName -> {
                    val plugin = scriptRuntime.plugins.load(name)
                    val moduleExportedFunc = js_require(scriptRuntime, plugin.mainScriptPath) as BaseFunction
                    val global = scriptRuntime.topLevelScope
                    val scope = moduleExportedFunc.parentScope ?: global
                    callFunction(scriptRuntime, moduleExportedFunc, scope, global, arrayOf(plugin.unwrap()))
                }
                scriptRuntime.files.isDir("./plugins") -> {
                    /* As project-level plugins name. */
                    js_require(scriptRuntime, "./plugins/$name")
                }
                else -> listOf(
                    "A directory named \"plugins\" containing module \"$name\"",
                    "must be found in the root directory of current project",
                ).joinToString(" ").let { s -> throw WrappedIllegalArgumentException(s) }
            }
        }

    }

}
