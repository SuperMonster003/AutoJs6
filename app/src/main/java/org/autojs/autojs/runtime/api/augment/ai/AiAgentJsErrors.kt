package org.autojs.autojs.runtime.api.augment.ai

import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentControlException
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract as C
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.RhinoException
import java.util.concurrent.TimeoutException

/** Native Rhino lambdas must explicitly turn Java failures into catchable script errors. */
internal object AiAgentJsErrors {
    inline fun <T> guard(action: () -> T): T = try { action() } catch (error: Exception) {
        when (error) {
            is RhinoException, is InterruptedException -> throw error
            else -> raise(error)
        }
    }
    fun raise(error: Exception): Nothing {
        val code = when (error) {
            is ThreeStoveAgentControlException -> error.code
            is TimeoutException -> "JOIN_TIMEOUT"
            else -> error.message?.takeIf { it in C.ERROR_CODES } ?: C.ERROR_INVALID_REQUEST
        }
        val json = JsonObject().apply {
            addProperty("code", code); addProperty("message", error.message ?: code)
            (error as? ThreeStoveAgentControlException)?.hint?.let { addProperty("hint", it) }
        }
        throw JavaScriptException(js_json_parse(json.toString()), "ai.agent", 0)
    }
}
