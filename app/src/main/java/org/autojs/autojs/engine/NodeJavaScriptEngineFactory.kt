package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs6.BuildConfig
import java.util.Locale

object NodeJavaScriptEngineFactory {

    fun create(context: Context): JavaScriptEngine {
        val backend = NodeRuntimeBackend.current()
        if (BuildConfig.NODEJS_RELEASE_FLAVOR == RHINO_ONLY_RELEASE_FLAVOR) {
            return NodeDisabledJavaScriptEngine(
                message = buildRhinoOnlyDisabledMessage(),
            )
        }
        return when (backend) {
            NodeRuntimeBackend.RHINO -> NodeJavaScriptEngine(context)
            NodeRuntimeBackend.PLUGIN -> NodePluginJavaScriptEngine(context)
            NodeRuntimeBackend.DISABLED -> NodeDisabledJavaScriptEngine(buildDisabledMessage())
        }
    }

    private const val RHINO_ONLY_RELEASE_FLAVOR = "rhinoOnly"

    private fun buildRhinoOnlyDisabledMessage(): String {
        if (BuildConfig.isInrt) {
            return "This packaged app does not enable the Node.js runtime plugin. " +
                    "Package the app with Node.js plugin support to run \"nodejs\" scripts. " +
                    "code=${NodeRuntimeErrorCodes.NODE_DISABLED}"
        }
        return "Node.js scripts are disabled for the rhinoOnly release flavor. " +
                "Use a plugin-enabled flavor for Node.js scripts, or explicitly run a legacy " +
                "Rhino-backed Node compatibility diagnostic outside rhinoOnly. " +
                "code=${NodeRuntimeErrorCodes.NODE_DISABLED}"
    }

    private fun buildDisabledMessage(): String =
        "Node.js scripts are disabled by the selected runtime backend. " +
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

enum class NodeRuntimeBackend {

    RHINO,
    PLUGIN,
    DISABLED,
    ;

    companion object {

        private const val PROPERTY_NAME = "autojs.nodejs.backend"

        fun current(): NodeRuntimeBackend {
            val raw = System.getProperty(PROPERTY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NODEJS_RUNTIME_BACKEND
            return from(raw)
        }

        internal fun from(value: String?): NodeRuntimeBackend = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "rhino" -> RHINO
            "plugin", "node-plugin", "nodejs-plugin" -> PLUGIN
            // Historical host-runtime spellings are accepted as migration aliases. They now
            // resolve to the only real Node.js execution owner: the external runtime plugin.
            "native", "v8", "node", "nodejs",
            "embedded", "embed", "auto",
            "pluginoptional", "plugin-optional", "plugin_optional",
            "optional-plugin", "node-plugin-optional", "nodejs-plugin-optional" -> PLUGIN
            "disabled", "disable", "off", "none" -> DISABLED
            else -> throw IllegalArgumentException(
                "Unsupported Node.js runtime backend '${value.orEmpty()}'. " +
                        "Use plugin, rhino, or disabled."
            )
        }
    }
}
