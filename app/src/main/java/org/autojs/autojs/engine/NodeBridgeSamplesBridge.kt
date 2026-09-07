package org.autojs.autojs.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The JSON layer of `app.listSamples(path?, options?)` / `app.readSample(path, options?)` over
 * [NodeBridgeSampleCatalog] backed by the application's asset manager (MCP roadmap P1.3, D20).
 * Read-only; the `inrt` flavor ships no samples, so the root listing reports `available: false`.
 *
 * zh-CN: `app.listSamples(path?, options?)` / `app.readSample(path, options?)` 的 JSON 层, 基于以应用 asset manager 为后端的
 * [NodeBridgeSampleCatalog] (MCP 路线图 P1.3, D20). 只读; `inrt` 风味不含示例, 根列表报告 `available: false`.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeSamplesBridge(context: Context) {

    private val assets = context.applicationContext.assets

    private val catalog = NodeBridgeSampleCatalog(
        lister = { path -> runCatching { assets.list(path) }.getOrNull() },
        opener = { path -> runCatching { assets.open(path) }.getOrNull() },
    )

    fun dispatch(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
        return try {
            when (request.method) {
                "listSamples" -> listSamples(request, args)
                "readSample" -> readSample(request, args)
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        } catch (error: NodeBridgeFileScopeException) {
            failure(request, error)
        } catch (error: Throwable) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.providerFailed(
                    message = error.message ?: error.javaClass.name,
                    module = request.module,
                    method = request.method,
                    stack = error.stackTraceToString(),
                ),
            )
        }
    }

    private fun listSamples(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val path = pathArg(args, 0, NodeBridgeSampleCatalog.ROOT_PATH)
        val options = optionsArg(request, args, 1, LIST_KEYS)
        val recursive = booleanOf(options, "recursive", false)
        val result = catalog.list(
            path = path,
            recursive = recursive,
            maxEntries = intOf(options, "maxEntries", NodeBridgeSampleCatalog.MAX_LIST_ENTRIES),
        )
        val entries = JSONArray()
        result.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("name", entry.name)
                    .put("type", if (entry.isDirectory) TYPE_DIRECTORY else TYPE_FILE),
            )
        }
        return success(
            request,
            JSONObject()
                .put("schema", SCHEMA_LIST)
                .put("root", NodeBridgeSampleCatalog.ASSET_ROOT)
                .put("path", result.path)
                .put("recursive", recursive)
                .put("available", result.available)
                .put("count", result.entries.size)
                .put("truncated", result.truncated)
                .put("entries", entries),
        )
    }

    private fun readSample(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val options = optionsArg(request, args, 1, READ_KEYS)
        val result = catalog.read(
            path = pathArg(args, 0),
            encoding = options.optString("encoding", NodeBridgeFileScope.ENCODING_UTF8),
            maxBytes = intOf(options, "maxBytes", NodeBridgeSampleCatalog.MAX_READ_BYTES),
        )
        return success(
            request,
            JSONObject()
                .put("schema", SCHEMA_READ)
                .put("root", NodeBridgeSampleCatalog.ASSET_ROOT)
                .put("path", result.path)
                .put("encoding", result.encoding)
                .put("bytes", result.bytes)
                .put("truncated", result.truncated)
                .put("content", result.content),
        )
    }

    private fun pathArg(args: JSONArray, index: Int, default: String? = null): String {
        val value = args.opt(index)
        if (value == null || value == JSONObject.NULL) {
            return default ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "path argument ${index + 1} is missing")
        }
        return value as? String
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "path argument ${index + 1} must be a string")
    }

    private fun optionsArg(request: NodeBridgeRequest, args: JSONArray, index: Int, keys: Set<String>): JSONObject {
        val value = args.opt(index)
        if (value == null || value == JSONObject.NULL) return JSONObject()
        val options = value as? JSONObject
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "app.${request.method} options must be an object")
        val iterator = options.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in keys) {
                throw NodeBridgeFileScopeException(
                    NodeBridgeFileScope.Kind.INVALID_PATH,
                    "app.${request.method} does not accept option \"$key\"; expected one of ${keys.joinToString(", ")}",
                )
            }
        }
        return options
    }

    private fun booleanOf(options: JSONObject, key: String, default: Boolean): Boolean {
        if (!options.has(key) || options.isNull(key)) return default
        return options.opt(key) as? Boolean
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "$key must be a boolean")
    }

    private fun intOf(options: JSONObject, key: String, default: Int): Int {
        if (!options.has(key) || options.isNull(key)) return default
        return (options.opt(key) as? Number)?.toInt()
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "$key must be a number")
    }

    private fun success(request: NodeBridgeRequest, json: JSONObject): NodeBridgeResponse {
        return NodeBridgeResponse.success(request.id, json.toString())
    }

    private fun failure(request: NodeBridgeRequest, error: NodeBridgeFileScopeException): NodeBridgeResponse {
        val message = "app.${request.method}: ${error.message}"
        val bridgeError = when (error.kind) {
            NodeBridgeFileScope.Kind.ESCAPE -> NodeBridgeError.permissionDenied(message, request.module, request.method)
            NodeBridgeFileScope.Kind.LIMIT -> NodeBridgeError.resourceLimit(message, request.module, request.method)
            NodeBridgeFileScope.Kind.IO -> NodeBridgeError.providerFailed(message, request.module, request.method)
            NodeBridgeFileScope.Kind.INVALID_PATH,
            NodeBridgeFileScope.Kind.NOT_FOUND,
            NodeBridgeFileScope.Kind.WRONG_TYPE,
            NodeBridgeFileScope.Kind.EXISTS,
            -> NodeBridgeError.invalidRequest(message, request.module, request.method)
        }
        return NodeBridgeResponse.failure(request.id, bridgeError)
    }

    companion object {
        const val SCHEMA_LIST = "autojs6-bridge-app-list-samples-v1"
        const val SCHEMA_READ = "autojs6-bridge-app-read-sample-v1"
        const val TYPE_FILE = "file"
        const val TYPE_DIRECTORY = "directory"
        private val LIST_KEYS = setOf("recursive", "maxEntries")
        private val READ_KEYS = setOf("encoding", "maxBytes")
    }
}
