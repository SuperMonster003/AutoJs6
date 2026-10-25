package org.autojs.autojs.runtime.api.modules.plugins

import android.graphics.Bitmap
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeModules
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgePluginToolsBridge
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.engine.NodeBridgeScreenCapture
import org.autojs.autojs.engine.PluginToolPaths
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolValues
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `plugins.tools` (MCP Server roadmap P12 D35, AutoJs6 docs > Plugins): the tools AutoJs6 plugins describe themselves
 * (docs/dev/plugin-tool-protocol-v1.md), through the same host path as MCP Server (`plugin_tools` bridge module).
 *
 * - `list()`: the tools, each with `name` (`<shortName>_<name>`), `description`, `inputSchema`, `risk`, `outputs` and the
 *   rest of the catalog entry; `providers()`: every provider with its state and diagnostics.
 * - `call(name, args?, options?)`: runs one tool and returns `{ result, text, outputs, tool, provider, invocationId,
 *   durationMs }`; `callAsync` returns a Promise of the same. `options`: `timeoutMs` (only shortens the tool's limit),
 *   `outputs` (output name to path) and `overwrite`.
 *
 * Scripts are the user's own code, so they may call the tools of every authorized plugin, USER_GRANTED ones included
 * (D34). Image arguments take `"screen"` (the accessibility screenshot on Android 11+, otherwise the script's screen
 * capture, which needs `images.requestScreenCapture()`), a path, or an `ImageWrapper`; file arguments and outputs take
 * paths, resolved like `files.path`.
 *
 * zh-CN: `plugins.tools`: AutoJs6 插件自描述的工具, 与 MCP Server 经同一宿主路径调用. 脚本是用户自己的代码, 可调用所有已授权插件的工具
 * (含 USER_GRANTED). 图片参数接受 `"screen"`, 路径或 `ImageWrapper`; 文件参数与输出接受路径, 按 `files.path` 解析.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class PluginsTools(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    @RhinoRuntimeFunctionInterface
    fun list(runtime: ScriptRuntime, args: Array<out Any?>): Any? {
        val answer = runBridge("list", JsonArray(), LIST_TIMEOUT_MS)
        return js_json_parse(JsonParser.parseString(answer).asJsonObject.get("tools").toString())
    }

    @RhinoRuntimeFunctionInterface
    fun providers(runtime: ScriptRuntime, args: Array<out Any?>): Any? {
        val answer = runBridge("list", JsonArray(), LIST_TIMEOUT_MS)
        return js_json_parse(JsonParser.parseString(answer).asJsonObject.get("providers").toString())
    }

    @RhinoRuntimeFunctionInterface
    fun call(runtime: ScriptRuntime, args: Array<out Any?>): Any? {
        val prepared = prepare(args)
        try {
            return js_json_parse(runBridge("invoke", prepared.arguments, INVOKE_TIMEOUT_MS))
        } finally {
            prepared.cleanup()
        }
    }

    @RhinoRuntimeFunctionInterface
    fun callAsync(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = ScriptAsyncDispatcher(scriptRuntime)
        val prepared = try {
            prepare(args)
        } catch (t: Throwable) {
            dispatcher.terminalValues(emptyArray()) { adapter.reject(jsError(t.message ?: t.toString())) }
            return adapter.toJsPromise(scriptRuntime)
        }
        scriptRuntime.coroutineScope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { runAsync(prepared.arguments) } }
            prepared.cleanup()
            try {
                dispatcher.terminalValues(emptyArray()) {
                    outcome.fold(
                        onSuccess = { adapter.resolve(js_json_parse(it)) },
                        onFailure = { adapter.reject(jsError(it.message ?: it.toString())) },
                    )
                }
            } catch (_: Throwable) {
                // The script is gone: nothing is left to settle. zh-CN: 脚本已不在: 没有可落定的对象了.
                runCatching { dispatcher.cancelSilently() }
            }
        }
        return adapter.toJsPromise(scriptRuntime)
    }

    /** The bridge arguments of one call; [cleanup] removes the PNG files written for `ImageWrapper` arguments. */
    private class Prepared(val arguments: JsonArray, private val temporaryFiles: List<File>) {
        fun cleanup() = temporaryFiles.forEach { it.delete() }
    }

    private fun prepare(args: Array<out Any?>): Prepared {
        require(args.size in 1..3) { "plugins.tools.call takes 1 to 3 arguments (name, args, options), got ${args.size}" }
        val name = (args[0] as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("plugins.tools.call needs the tool name as a string, got ${args[0].jsBrief()}")
        val temporaryFiles = mutableListOf<File>()
        try {
            val toolArguments = when (val value = args.getOrNull(1)) {
                null, is Undefined -> JsonObject()
                is NativeObject -> argumentsOf(value, temporaryFiles)
                else -> throw IllegalArgumentException("plugins.tools.call needs the tool arguments as an object, got ${value.jsBrief()}")
            }
            val options = when (val value = args.getOrNull(2)) {
                null, is Undefined -> JsonObject()
                is Scriptable -> json(value) as? JsonObject ?: throw IllegalArgumentException("options must be an object")
                else -> throw IllegalArgumentException("options must be an object, got ${value.jsBrief()}")
            }
            options.get("outputs")?.takeIf { it.isJsonObject }?.asJsonObject?.let { outputs ->
                // Output paths resolve like files.path, against the script's working directory.
                outputs.entrySet().forEach { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.let { outputs.addProperty(key, scriptRuntime.files.nonNullPath(it.asString)) }
                }
            }
            return Prepared(JsonArray().apply { add(name); add(toolArguments); add(options) }, temporaryFiles)
        } catch (t: Throwable) {
            temporaryFiles.forEach { it.delete() }
            throw t
        }
    }

    /** The argument object as JSON; a top-level `ImageWrapper` becomes the path of a PNG copy of it. */
    private fun argumentsOf(value: NativeObject, temporaryFiles: MutableList<File>): JsonObject {
        val result = JsonObject()
        for (id in value.ids) {
            val key = id.toString()
            val item = ScriptableObject.getProperty(value, key).let { if (it is Wrapper) it.unwrap() else it }
            when {
                item is ImageWrapper -> {
                    val file = File(File(ScriptRuntime.applicationContext.cacheDir, SCRIPT_IMAGE_DIRECTORY).apply { mkdirs() }, "${UUID.randomUUID()}.png")
                    file.outputStream().use { out -> check(item.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "the image could not be encoded" } }
                    temporaryFiles += file
                    result.addProperty(key, file.absolutePath)
                }
                item.isJsNullish() -> Unit
                else -> json(item)?.let { result.add(key, it) }
            }
        }
        return result
    }

    private fun json(value: Any?): JsonElement? = when (value) {
        null, is Undefined -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Scriptable -> {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) throw IllegalArgumentException("argument must be JSON-compatible, got ${value.jsBrief()}")
            JsonParser.parseString(Context.toString(serialized))
        }
        else -> throw IllegalArgumentException("unsupported argument ${value.jsBrief()}")
    }

    /** Runs one bridge call on the script thread and returns its result JSON; stopping the script cancels the call. */
    private fun runBridge(method: String, arguments: JsonArray, timeoutMs: Long): String {
        val bridge = bridge()
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        bridge.dispatch(request(method, arguments, timeoutMs)) { response = it; latch.countDown() }
        try {
            if (!latch.await(timeoutMs + 5_000L, TimeUnit.MILLISECONDS)) throw IllegalStateException("plugins.tools.$method did not answer")
        } catch (e: InterruptedException) {
            bridge.destroy()
            throw ScriptInterruptedException(e)
        } finally {
            bridge.destroy()
        }
        return resultOf(requireNotNull(response))
    }

    private suspend fun runAsync(arguments: JsonArray): String {
        val bridge = bridge()
        try {
            val answer = kotlinx.coroutines.suspendCancellableCoroutine<NodeBridgeResponse> { continuation ->
                continuation.invokeOnCancellation { bridge.destroy() }
                bridge.dispatch(request("invoke", arguments, INVOKE_TIMEOUT_MS)) { response -> if (continuation.isActive) continuation.resumeWith(Result.success(response)) }
            }
            return resultOf(answer)
        } finally {
            bridge.destroy()
        }
    }

    private fun resultOf(response: NodeBridgeResponse): String {
        if (response.ok) return response.resultJson ?: "null"
        val error: NodeBridgeError? = response.error
        throw IllegalStateException(error?.message ?: "the plugin tool call failed")
    }

    private fun bridge() = NodeBridgePluginToolsBridge(
        context = ScriptRuntime.applicationContext,
        paths = scriptPaths,
        consumer = PluginToolValues.CONSUMER_SCRIPT,
        captureScreen = { timeoutMs ->
            NodeBridgeScreenCapture.capture(timeoutMs) {
                val image = scriptRuntime.images.captureScreen()
                    ?: throw SecurityException("capturing the screen needs images.requestScreenCapture() first, or the AutoJs6 accessibility service on Android 11+")
                image.bitmap.let { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }
            }.bitmap
        },
    )

    /** Scripts read and write where `files.path` points; the tool contract still bounds the sizes. */
    private val scriptPaths = object : PluginToolPaths {
        override fun read(path: String, permissions: Collection<String>, label: String) = File(scriptRuntime.files.nonNullPath(path)).canonicalFile
        override fun write(path: String, label: String) = File(scriptRuntime.files.nonNullPath(path)).canonicalFile
    }

    private fun request(method: String, arguments: JsonArray, timeoutMs: Long) = NodeBridgeRequest(
        id = UUID.randomUUID().toString(),
        module = NodeBridgeModules.PLUGIN_TOOLS,
        method = method,
        argsJson = arguments.toString(),
        timeoutMs = timeoutMs,
        permissions = SCRIPT_TOKENS,
    )

    private fun jsError(message: String): Any = RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
        cx.newObject(scriptRuntime.topLevelScope, "Error", arrayOf(message))
    }

    private companion object {
        /** Scripts are the user's own code: every token the module knows (MCP roadmap D34). */
        val SCRIPT_TOKENS = listOf(
            NodeBridgePermissionManifest.PLUGIN_TOOLS,
            NodeBridgePermissionManifest.PLUGIN_TOOLS_USER_GRANTED,
            NodeBridgePermissionManifest.SCREEN_CAPTURE,
            NodeBridgePermissionManifest.FILES,
            NodeBridgePermissionManifest.FILES_WRITE,
        )
        const val LIST_TIMEOUT_MS = 30_000L
        /** Above the longest tool limit, so the tool's own limit decides. */
        const val INVOKE_TIMEOUT_MS = PluginToolLimits.MAX_TIMEOUT_MS + 2_000L
        const val SCRIPT_IMAGE_DIRECTORY = "plugin-tool-script-images"
    }
}
