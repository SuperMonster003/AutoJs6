package org.autojs.plugin.tool.fake.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.tool.api.IPluginToolCallback
import org.autojs.plugin.tool.api.IPluginToolProvider
import org.autojs.plugin.tool.api.PluginToolAcceptKeys
import org.autojs.plugin.tool.api.PluginToolCancelKeys
import org.autojs.plugin.tool.api.PluginToolCapabilityKeys
import org.autojs.plugin.tool.api.PluginToolCatalogKeys
import org.autojs.plugin.tool.api.PluginToolContract
import org.autojs.plugin.tool.api.PluginToolErrorCodes
import org.autojs.plugin.tool.api.PluginToolErrorKeys
import org.autojs.plugin.tool.api.PluginToolInvokeKeys
import org.autojs.plugin.tool.api.PluginToolResultKeys
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Test-only provider of the plugin tool contract (docs/dev/plugin-tool-protocol-v1.md). Its catalog declares `faketool`
 * with `echo` (returns its text), `fail` (always fails), `slow` (waits until cancelled or `sleepMs` passed) and
 * `bad_schema` (an open schema the host must drop). Nothing here is reachable from production code.
 */
class FakePluginToolProviderService : Service() {

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private val binder = object : IPluginToolProvider.Stub() {

        override fun getInfo(): PluginInfo = PluginInfo(
            name = "AutoJs6 Plugin Tool Conformance",
            description = "Test-only plugin tool provider",
            instruction = null,
            author = "SuperMonster003",
            collaborators = null,
            versionName = "1.0.0",
            versionCode = 1L,
            versionDate = null,
            id = "plugin-tool-conformance",
            engine = "plugin-tool-conformance",
            variant = "default",
            supportedAbis = emptyArray(),
            capabilities = Bundle().apply { putInt(PluginToolCapabilityKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION) },
        )

        override fun getCatalog(request: Bundle?): Bundle = Bundle().apply {
            putInt(PluginToolCatalogKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
            putString(PluginToolCatalogKeys.CATALOG_JSON, CATALOG)
        }

        override fun invoke(request: Bundle?, inputs: Array<out ParcelFileDescriptor>?, callback: IPluginToolCallback?): Bundle {
            inputs?.forEach { runCatching { it.close() } }
            val invocationId = request?.getString(PluginToolInvokeKeys.INVOCATION_ID)
            if (request == null || callback == null || invocationId == null) return refuse(PluginToolErrorCodes.FAILED, "incomplete request")
            if (!PluginToolContract.supportsContractVersion(request.getInt(PluginToolInvokeKeys.CONTRACT_VERSION))) {
                return refuse(PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, "version ${request.getInt(PluginToolInvokeKeys.CONTRACT_VERSION)}")
            }
            val arguments = JSONObject(request.getString(PluginToolInvokeKeys.ARGUMENTS_JSON) ?: "{}")
            when (request.getString(PluginToolInvokeKeys.TOOL)) {
                "echo" -> thread(name = "fake-tool-echo") {
                    val text = arguments.optString("text")
                    callback.onResult(Bundle().apply {
                        putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                        putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("text", text).toString())
                        putString(PluginToolResultKeys.TEXT, text)
                        putString(PluginToolResultKeys.OUTPUTS_JSON, "[]")
                    }, emptyArray())
                }
                "fail" -> thread(name = "fake-tool-fail") { callback.onError(error(invocationId, PluginToolErrorCodes.FAILED, "the fake tool always fails")) }
                "slow" -> thread(name = "fake-tool-slow") {
                    val until = System.currentTimeMillis() + arguments.optLong("sleepMs", 60_000L)
                    while (System.currentTimeMillis() < until && invocationId !in cancelled) Thread.sleep(20)
                    if (cancelled.remove(invocationId)) {
                        callback.onError(error(invocationId, PluginToolErrorCodes.CANCELLED, "cancelled"))
                    } else {
                        callback.onResult(Bundle().apply {
                            putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                            putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("slept", true).toString())
                        }, emptyArray())
                    }
                }
                else -> return refuse(PluginToolErrorCodes.UNKNOWN_TOOL, "no tool ${request.getString(PluginToolInvokeKeys.TOOL)}")
            }
            return Bundle().apply { putBoolean(PluginToolAcceptKeys.ACCEPTED, true) }
        }

        override fun cancel(request: Bundle?) {
            request?.getString(PluginToolCancelKeys.INVOCATION_ID)?.let(cancelled::add)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun refuse(code: String, message: String) = Bundle().apply {
        putBoolean(PluginToolAcceptKeys.ACCEPTED, false)
        putString(PluginToolAcceptKeys.ERROR_CODE, code)
        putString(PluginToolAcceptKeys.ERROR_MESSAGE, message)
    }

    private fun error(invocationId: String, code: String, message: String) = Bundle().apply {
        putString(PluginToolErrorKeys.INVOCATION_ID, invocationId)
        putString(PluginToolErrorKeys.CODE, code)
        putString(PluginToolErrorKeys.MESSAGE, message)
    }

    companion object {

        const val CATALOG = """{
  "shortName": "faketool",
  "tools": [
    {
      "name": "echo",
      "title": "Echo text",
      "description": "Returns the text it receives. Test-only tool of the AutoJs6 plugin tool conformance provider.",
      "inputSchema": {"type": "object", "properties": {"text": {"type": "string", "maxLength": 1024}}, "required": ["text"], "additionalProperties": false},
      "outputSchema": {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"], "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 5000,
      "maxConcurrency": 2
    },
    {
      "name": "fail",
      "description": "Always fails with FAILED. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {}, "additionalProperties": false},
      "risk": "readonly"
    },
    {
      "name": "slow",
      "description": "Waits until it is cancelled or sleepMs passed. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"sleepMs": {"type": "integer", "minimum": 0, "maximum": 60000}}, "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 2000
    },
    {
      "name": "bad_schema",
      "description": "Declares an open schema, which the host must drop. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {}},
      "risk": "readonly"
    }
  ]
}"""
    }
}
