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
import org.autojs.plugin.tool.api.IPluginToolOutputTree
import org.autojs.plugin.tool.api.PluginToolInvokeKeys
import org.autojs.plugin.tool.api.PluginToolOutputTreeKeys
import org.autojs.plugin.tool.api.PluginToolResultKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Test-only provider of the plugin tool contract (docs/dev/plugin-tool-protocol-v1.md). Its catalog declares `faketool`
 * with `echo` (returns its text), `fail` (always fails), `slow` (waits until cancelled or `sleepMs` passed), `inspect`
 * (reports the bytes behind its image and file descriptors), `emit` (returns its text as the binary output `data`) and
 * `last_cancel` (returns the invocation id and reason of the last cancel it received), `inspect_dir` (reports the files of
 * its directory inputs), `emit_tree` (writes its files into the directory output `tree` and reports each answer of the
 * host), `die` (kills the provider process during the call) and `bad_schema` (an open schema the host must drop).
 * Nothing here is reachable from production code.
 */
class FakePluginToolProviderService : Service() {

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private val binder = object : IPluginToolProvider.Stub() {

        override fun getInfo(): PluginInfo = pluginInfo()

        override fun getCatalog(request: Bundle?): Bundle = Bundle().apply {
            putInt(PluginToolCatalogKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
            putString(PluginToolCatalogKeys.CATALOG_JSON, CATALOG)
        }

        override fun invoke(request: Bundle?, inputs: Array<out ParcelFileDescriptor>?, callback: IPluginToolCallback?): Bundle {
            val descriptors = inputs?.toList().orEmpty()
            if (request?.getString(PluginToolInvokeKeys.TOOL) !in INSPECTING) descriptors.forEach { runCatching { it.close() } }
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
                "emit_tree" -> thread(name = "fake-tool-emit-tree") {
                    val tree = request.getBundle(PluginToolInvokeKeys.OUTPUT_TREES)?.getBinder("tree")?.let(IPluginToolOutputTree.Stub::asInterface)
                    val answers = JSONObject()
                    if (tree != null) {
                        arguments.optJSONArray("directories")?.let { list ->
                            for (index in 0 until list.length()) {
                                val path = list.getString(index)
                                val answer = tree.createDirectory(path)
                                answers.put(path, if (answer.getBoolean(PluginToolOutputTreeKeys.OK)) "OK" else answer.getString(PluginToolOutputTreeKeys.ERROR_CODE))
                            }
                        }
                        val files = arguments.optJSONArray("files") ?: JSONArray()
                        for (index in 0 until files.length()) {
                            val path = files.getString(index)
                            val answer = Bundle()
                            val descriptor = tree.openFile(path, -1L, answer)
                            if (descriptor != null) ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { it.write("content of $path".toByteArray()) }
                            answers.put(path, if (descriptor != null) "OK" else answer.getString(PluginToolOutputTreeKeys.ERROR_CODE))
                        }
                    }
                    if (arguments.optBoolean("failAfter")) {
                        callback.onError(error(invocationId, PluginToolErrorCodes.FAILED, "failed after writing ${answers.length()} entries"))
                    } else {
                        callback.onResult(Bundle().apply {
                            putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                            putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("tree", tree != null).put("answers", answers).toString())
                            putString(PluginToolResultKeys.OUTPUTS_JSON, "[]")
                        }, emptyArray())
                    }
                }
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
                "inspect", "inspect_dir" -> thread(name = "fake-tool-inspect") {
                    val meta = JSONArray(request.getString(PluginToolInvokeKeys.INPUTS_JSON) ?: "[]")
                    val report = JSONArray()
                    descriptors.forEachIndexed { index, descriptor ->
                        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                        val entry = meta.optJSONObject(index) ?: JSONObject()
                        report.put(JSONObject().put("name", entry.optString("name")).put("source", entry.optString("source")).put("displayName", entry.optString("displayName"))
                            .put("mimeType", entry.optString("mimeType")).put("bytes", bytes.size).put("declaredBytes", entry.optLong("byteCount", -1)))
                    }
                    callback.onResult(Bundle().apply {
                        putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                        putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("inputs", report).toString())
                    }, emptyArray())
                }
                "emit" -> thread(name = "fake-tool-emit") {
                    val file = File(cacheDir, "emit-$invocationId.txt").apply { writeText(arguments.optString("text")) }
                    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    file.delete()
                    val wanted = JSONArray(request.getString(PluginToolInvokeKeys.OUTPUTS_JSON) ?: "[]")
                    val outputs = if ((0 until wanted.length()).any { wanted.optString(it) == "data" }) arrayOf(descriptor) else emptyArray<ParcelFileDescriptor>().also { descriptor.close() }
                    callback.onResult(Bundle().apply {
                        putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                        putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("length", arguments.optString("text").length).toString())
                        putString(PluginToolResultKeys.OUTPUTS_JSON, JSONArray().apply { if (outputs.isNotEmpty()) put(JSONObject().put("name", "data").put("mimeType", "text/plain")) }.toString())
                    }, outputs)
                    outputs.forEach { runCatching { it.close() } }
                }
                "die" -> thread(name = "fake-tool-die") {
                    Thread.sleep(arguments.optLong("afterMs", 300L))
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                "last_cancel" -> thread(name = "fake-tool-last-cancel") {
                    callback.onResult(Bundle().apply {
                        putString(PluginToolResultKeys.INVOCATION_ID, invocationId)
                        putString(PluginToolResultKeys.RESULT_JSON, JSONObject().put("lastCancel", lastCancel).toString())
                    }, emptyArray())
                }
                else -> return refuse(PluginToolErrorCodes.UNKNOWN_TOOL, "no tool ${request.getString(PluginToolInvokeKeys.TOOL)}")
            }
            return Bundle().apply { putBoolean(PluginToolAcceptKeys.ACCEPTED, true) }
        }

        override fun cancel(request: Bundle?) {
            lastCancel = "${request?.getString(PluginToolCancelKeys.INVOCATION_ID)}:${request?.getString(PluginToolCancelKeys.REASON)}"
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

        /** What both the provider and the `org.autojs.plugin.INFO` service ([FakePluginInfoService]) report. */
        fun pluginInfo(): PluginInfo = PluginInfo(
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

        /** The tools that read their input descriptors; every other tool closes them at once. */
        private val INSPECTING = setOf("inspect", "inspect_dir")

        /** `<invocationId>:<reason>` of the last cancel this provider process received; the `last_cancel` tool returns it. */
        @Volatile
        var lastCancel: String = ""

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
      "timeoutMs": 15000
    },
    {
      "name": "inspect",
      "description": "Reports the bytes behind its image and file inputs. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"image": {"type": "string", "x-autojs6-type": "image"}, "file": {"type": "string", "x-autojs6-type": "file"}}, "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 10000
    },
    {
      "name": "emit",
      "description": "Returns its text as the binary output data. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"text": {"type": "string", "maxLength": 4096}}, "required": ["text"], "additionalProperties": false},
      "risk": "write",
      "outputs": [{"name": "data", "mimeType": "text/plain", "description": "the text as UTF-8"}]
    },
    {
      "name": "last_cancel",
      "description": "Returns the invocation id and reason of the last cancel the provider received. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {}, "additionalProperties": false},
      "risk": "readonly"
    },
    {
      "name": "inspect_dir",
      "description": "Reports the files behind its directory inputs: dir hands over a.txt and b.bin, all hands over every file. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"dir": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["a.txt", "b.bin"]}, "all": {"type": "string", "x-autojs6-type": "directory"}}, "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 10000
    },
    {
      "name": "emit_tree",
      "description": "Writes its files, each holding 'content of <path>', into the directory output tree and reports the answer for every path; failAfter reports an error afterwards. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"files": {"type": "array", "items": {"type": "string", "maxLength": 300}, "maxItems": 16}, "directories": {"type": "array", "items": {"type": "string", "maxLength": 300}, "maxItems": 8}, "failAfter": {"type": "boolean"}}, "required": ["files"], "additionalProperties": false},
      "risk": "write",
      "timeoutMs": 10000,
      "outputs": [{"name": "tree", "kind": "directory", "description": "the files"}]
    },
    {
      "name": "die",
      "description": "Kills the provider process during the call, after afterMs. Test-only tool.",
      "inputSchema": {"type": "object", "properties": {"afterMs": {"type": "integer", "minimum": 0, "maximum": 5000}}, "additionalProperties": false},
      "risk": "readonly",
      "timeoutMs": 15000
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
