package org.autojs.autojs.engine

import android.content.Context
import android.content.res.AssetManager
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginHost
import org.autojs.autojs.core.plugin.docs.OfflineDocsPluginStateMonitor
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

/**
 * The JSON layer of `app.listSamples(path?, options?)` / `app.readSample(path, options?)` over
 * [NodeBridgeSampleCatalog] backed by the application's asset manager (MCP roadmap P1.3, D20),
 * and of `app.listDocs(path?, options?)` / `app.readDoc(path, options?)` over
 * [NodeBridgeDocsCatalog] backed by the verified assets of the installed Offline Documentation
 * plugin (MCP roadmap P3.5, D20). Read-only; the `inrt` flavor ships no samples and a host
 * without an eligible documentation plugin has no docs, so the root listings report
 * `available: false`.
 *
 * zh-CN: `app.listSamples(path?, options?)` / `app.readSample(path, options?)` 的 JSON 层, 基于以应用 asset manager 为后端的
 * [NodeBridgeSampleCatalog] (MCP 路线图 P1.3, D20); 以及 `app.listDocs(path?, options?)` / `app.readDoc(path, options?)` 的 JSON 层,
 * 基于以已安装离线文档插件的已校验资产为后端的 [NodeBridgeDocsCatalog] (MCP 路线图 P3.5, D20). 只读; `inrt` 风味不含示例,
 * 无可用文档插件的宿主没有文档, 根列表均报告 `available: false`.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeSamplesBridge(context: Context) {

    private val appContext = context.applicationContext

    private val assets = appContext.assets

    private val catalog = NodeBridgeSampleCatalog(
        lister = { path -> runCatching { assets.list(path) }.getOrNull() },
        opener = { path -> runCatching { assets.open(path) }.getOrNull() },
    )

    private val docsCatalog = NodeBridgeDocsCatalog(source = ::docsSource)

    fun dispatch(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
        return try {
            when (request.method) {
                "listSamples" -> listSamples(request, args)
                "readSample" -> readSample(request, args)
                "listDocs" -> listDocs(request, args)
                "readDoc" -> readDoc(request, args)
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
            entries.put(entryJson(entry.path, entry.name, entry.isDirectory))
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

    private fun listDocs(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val path = pathArg(args, 0, NodeBridgeDocsCatalog.ROOT_PATH)
        val options = optionsArg(request, args, 1, LIST_KEYS)
        val recursive = booleanOf(options, "recursive", false)
        val result = docsCatalog.list(
            path = path,
            recursive = recursive,
            maxEntries = intOf(options, "maxEntries", NodeBridgeDocsCatalog.MAX_LIST_ENTRIES),
        )
        val entries = JSONArray()
        result.entries.forEach { entry ->
            entries.put(entryJson(entry.path, entry.name, entry.isDirectory))
        }
        val json = JSONObject()
            .put("schema", SCHEMA_LIST_DOCS)
            .put("root", NodeBridgeDocsCatalog.ASSET_ROOT)
            .put("path", result.path)
            .put("recursive", recursive)
            .put("available", result.available)
        val source = result.source
        if (source != null) {
            json.put("status", STATUS_AVAILABLE)
                .put("packageName", source.packageName)
                .put("contentVersion", source.contentVersion)
                .put("contractVersion", source.contractVersion)
                .put("entryPoint", source.entryPoint)
                .put("fileCount", source.fileCount)
                .put("totalBytes", source.totalBytes)
        } else {
            val state = OfflineDocsPluginStateMonitor.current(appContext)
            json.put("status", state.status.name.lowercase(Locale.ROOT))
            state.errorCode?.let { json.put("errorCode", it.name.lowercase(Locale.ROOT)) }
        }
        return success(
            request,
            json.put("count", result.entries.size)
                .put("truncated", result.truncated)
                .put("entries", entries),
        )
    }

    private fun readDoc(request: NodeBridgeRequest, args: JSONArray): NodeBridgeResponse {
        val options = optionsArg(request, args, 1, READ_KEYS)
        val result = docsCatalog.read(
            path = pathArg(args, 0),
            encoding = options.optString("encoding", NodeBridgeFileScope.ENCODING_UTF8),
            maxBytes = intOf(options, "maxBytes", NodeBridgeDocsCatalog.MAX_READ_BYTES),
        )
        return success(
            request,
            JSONObject()
                .put("schema", SCHEMA_READ_DOC)
                .put("root", NodeBridgeDocsCatalog.ASSET_ROOT)
                .put("path", result.path)
                .put("encoding", result.encoding)
                .put("bytes", result.bytes)
                .put("truncated", result.truncated)
                .put("content", result.content),
        )
    }

    /**
     * The plugin the host trusts right now, as seen by the same monitor the documentation UI uses.
     * While the monitor's first inspection is still running, the plugin is loaded (and verified)
     * synchronously on the bridge thread instead of answering `available: false` too early.
     */
    private fun docsSource(): NodeBridgeDocsCatalog.Source? {
        val plugin = OfflineDocsPluginStateMonitor.loadedPlugin(appContext)
            ?: OfflineDocsPluginStateMonitor.current(appContext)
                .takeIf { it.status == OfflineDocsPluginStateMonitor.Status.LOADING }
                ?.let { runCatching { OfflineDocsPluginHost.load(appContext) }.getOrNull() }
            ?: return null
        return DocsSource(plugin)
    }

    private class DocsSource(private val plugin: OfflineDocsPluginHost.LoadedPlugin) : NodeBridgeDocsCatalog.Source {

        override val packageName: String
            get() = plugin.candidate.serviceInfo.packageName

        override val contentVersion: String
            get() = plugin.candidate.contentVersion

        override val contractVersion: Int
            get() = plugin.candidate.contractVersion

        override val entryPoint: String
            get() = plugin.candidate.entryPoint

        override val fileCount: Long
            get() = plugin.candidate.fileCount

        override val totalBytes: Long
            get() = plugin.candidate.totalBytes

        override val assetPaths: Set<String>
            get() = plugin.verifiedAssetPaths

        override fun open(relativePath: String): InputStream? = runCatching {
            plugin.assetManager.open("${plugin.candidate.assetRoot}/$relativePath", AssetManager.ACCESS_STREAMING)
        }.getOrNull()
    }

    private fun entryJson(path: String, name: String, isDirectory: Boolean): JSONObject {
        return JSONObject()
            .put("path", path)
            .put("name", name)
            .put("type", if (isDirectory) TYPE_DIRECTORY else TYPE_FILE)
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
        const val SCHEMA_LIST_DOCS = "autojs6-bridge-app-list-docs-v1"
        const val SCHEMA_READ_DOC = "autojs6-bridge-app-read-doc-v1"
        const val TYPE_FILE = "file"
        const val TYPE_DIRECTORY = "directory"
        const val STATUS_AVAILABLE = "available"
        val DOCS_METHODS = setOf("listDocs", "readDoc")
        val METHODS = setOf("listSamples", "readSample") + DOCS_METHODS
        private val LIST_KEYS = setOf("recursive", "maxEntries")
        private val READ_KEYS = setOf("encoding", "maxBytes")
    }
}
