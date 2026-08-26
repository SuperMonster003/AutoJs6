package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs6.BuildConfig

object NodeJavaScriptEngineFactory {

    fun create(context: Context): JavaScriptEngine {
        if (BuildConfig.isInrt) {
            return NodeDisabledJavaScriptEngine(
                message = buildPackagedAppDisabledMessage(),
            )
        }
        return NodePluginJavaScriptEngine(context)
    }

    private fun buildPackagedAppDisabledMessage(): String =
        "This packaged app does not support the external Node.js runtime plugin. " +
                "Run the project in AutoJs6 with the Node.js Runtime plugin installed. " +
                "code=${NodeRuntimeErrorCodes.NODE_DISABLED}"

}

class NodeDisabledJavaScriptEngine(
    private val message: String,
) : JavaScriptEngine() {

    override fun put(name: String, value: Any?) {
        // The disabled engine never evaluates script source, but JavaScriptEngine.setRuntime()
        // still installs standard host objects before execution starts.
    }

    override fun init() = Unit

    override fun forceStop() = Unit

    override fun doExecution(scriptSource: org.autojs.autojs.script.JavaScriptSource): Any? {
        val error = NodeRuntimeDisabledException(message)
        if (BuildConfig.isInrt) {
            runCatching {
                runtime.console.error(error)
            }
        }
        throw error
    }

    override fun toString(): String {
        return "NodeDisabledJavaScriptEngine@" + Integer.toHexString(hashCode()) +
                "{id=" + id + ",source='" + getTag(TAG_SOURCE) + "',cwd='" + cwd() + "'}"
    }
}

class NodeRuntimeDisabledException(
    message: String,
) : IllegalStateException(message)
