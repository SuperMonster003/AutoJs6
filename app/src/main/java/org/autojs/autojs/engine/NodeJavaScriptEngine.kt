package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Scriptable
import java.io.File

/**
 * Node.js-compatible JavaScript engine entrypoint.
 *
 * It reuses Rhino as the evaluator while installing a small Node-compatible
 * host layer and running file sources through the CommonJS module loader.
 */
class NodeJavaScriptEngine(context: Context) : LoopBasedJavaScriptEngine(context) {

    override fun init() {
        super.init()
        requireModule(NODEJS_MODULE)
    }

    override fun doExecution(source: JavaScriptSource): Any? {
        configureMainModule(source)
        return when (source) {
            is JavaScriptFileSource -> runMainModule(source.file)
            else -> super.doExecution(source)
        }
    }

    private fun runMainModule(file: File): Any? {
        val module = runtime.js_Module
        val runMain = module.prop("runMain") as BaseFunction
        return callFunction(runtime, runMain, scriptable, module as Scriptable, arrayOf(file.absolutePath))
    }

    private fun configureMainModule(source: JavaScriptSource) {
        val setter = scriptable.prop("__nodejs_set_main__") as? BaseFunction ?: return
        val file = (source as? JavaScriptFileSource)?.file
        val filename = file?.absolutePath ?: source.fullPath
        val dirname = file?.parent ?: cwd().orEmpty()
        val argv = context.newArray(scriptable, arrayOf<Any?>("autojs6", filename))
        callFunction(runtime, setter, scriptable, scriptable.globalThis, arrayOf(filename, dirname, argv, execArgv))
    }

    private fun requireModule(id: String): Any? {
        val require = scriptable.prop("require") as BaseFunction
        return callFunction(runtime, require, scriptable, scriptable.globalThis, arrayOf(id))
    }

    private companion object {
        const val NODEJS_MODULE = "nodejs"
    }

}
