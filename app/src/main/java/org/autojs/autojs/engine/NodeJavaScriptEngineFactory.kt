package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs6.BuildConfig
import java.util.Locale

object NodeJavaScriptEngineFactory {

    fun create(context: Context): JavaScriptEngine {
        val backend = NodeRuntimeBackend.current()
        if (BuildConfig.NODEJS_RELEASE_FLAVOR == RHINO_ONLY_RELEASE_FLAVOR && backend != NodeRuntimeBackend.EMBEDDED) {
            return NodeDisabledJavaScriptEngine(
                message = buildRhinoOnlyDisabledMessage(),
            )
        }
        return when (backend) {
            NodeRuntimeBackend.RHINO -> NodeJavaScriptEngine(context)
            NodeRuntimeBackend.NATIVE -> NativeNodeJavaScriptEngine(context)
            NodeRuntimeBackend.EMBEDDED -> NativeNodeEmbeddedJavaScriptEngine(context)
            NodeRuntimeBackend.PLUGIN,
            NodeRuntimeBackend.PLUGIN_OPTIONAL -> NativeNodeEmbeddedJavaScriptEngine(context)
            NodeRuntimeBackend.AUTO -> when {
                NativeNodeRuntimeBridge.isAvailable -> NativeNodeJavaScriptEngine(context)
                else -> {
                    Log.i(TAG, "Native Node.js backend is unavailable; using Rhino compatibility backend.")
                    NodeJavaScriptEngine(context)
                }
            }
        }
    }

    private const val TAG = "NodeJavaScriptEngineFactory"
    private const val RHINO_ONLY_RELEASE_FLAVOR = "rhinoOnly"

    private fun buildRhinoOnlyDisabledMessage(): String {
        if (BuildConfig.isInrt) {
            return "This packaged app does not include the Embedded Node.js runtime. " +
                    "Select Embedded Node.js when packaging the app to run \"nodejs\" scripts. " +
                    "code=${NodeRuntimeErrorCodes.NODE_DISABLED}"
        }
        return "Node.js scripts are disabled for the rhinoOnly release flavor. " +
                "Use nodeFull/nodeMini for Node Profile v1 scripts, or explicitly run a legacy " +
                "Rhino-backed Node compatibility diagnostic outside rhinoOnly. " +
                "code=${NodeRuntimeErrorCodes.NODE_DISABLED}"
    }

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
    NATIVE,
    EMBEDDED,
    PLUGIN,
    PLUGIN_OPTIONAL,
    AUTO,
    ;

    companion object {

        private const val PROPERTY_NAME = "autojs.nodejs.backend"

        fun current(): NodeRuntimeBackend {
            val raw = System.getProperty(PROPERTY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NODEJS_RUNTIME_BACKEND
            return from(raw).also { backend ->
                require(
                    backend != PLUGIN_OPTIONAL ||
                            BuildConfig.NODEJS_RELEASE_FLAVOR == "nodeFull" ||
                            BuildConfig.NODEJS_RELEASE_FLAVOR == "nodeMini"
                ) {
                    "Node.js runtime backend pluginOptional requires release flavor nodeFull or nodeMini; " +
                            "current release flavor is ${BuildConfig.NODEJS_RELEASE_FLAVOR}."
                }
            }
        }

        internal fun from(value: String?): NodeRuntimeBackend = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "rhino" -> RHINO
            "native", "v8", "node", "nodejs" -> NATIVE
            "embedded", "embed" -> EMBEDDED
            "plugin", "node-plugin", "nodejs-plugin" -> PLUGIN
            "pluginoptional", "plugin-optional", "plugin_optional",
            "optional-plugin", "node-plugin-optional", "nodejs-plugin-optional" -> PLUGIN_OPTIONAL
            "auto" -> AUTO
            else -> throw IllegalArgumentException(
                "Unsupported Node.js runtime backend '${value.orEmpty()}'. " +
                        "Use rhino, native, embedded, plugin, pluginOptional, or auto."
            )
        }
    }
}
