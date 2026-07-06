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
            NodeRuntimeBackend.PLUGIN -> NativeNodeEmbeddedJavaScriptEngine(context)
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
    AUTO,
    ;

    companion object {

        private const val PROPERTY_NAME = "autojs.nodejs.backend"

        fun current(): NodeRuntimeBackend {
            val raw = System.getProperty(PROPERTY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.NODEJS_RUNTIME_BACKEND
            return from(raw)
        }

        private fun from(value: String?): NodeRuntimeBackend = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "native", "v8", "node", "nodejs" -> NATIVE
            "embedded", "embed" -> EMBEDDED
            "plugin", "node-plugin", "nodejs-plugin" -> PLUGIN
            "auto" -> AUTO
            else -> RHINO
        }
    }
}
