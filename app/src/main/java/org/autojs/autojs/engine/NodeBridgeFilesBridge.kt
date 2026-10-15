package org.autojs.autojs.engine

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The JSON layer of the Node Bridge's `files` module (MCP roadmap P1.3) over
 * [NodeBridgeFileScope], rooted at the working directory: `list(path?, options?)`,
 * `stat(path)`, `read(path, options?)`, `write(path, content, options?)`, `mkdir(path)`,
 * `rename(from, to, options?)`, `delete(path, options?)`. Mutations refresh the workspace
 * explorer on the main thread.
 *
 * zh-CN: Node Bridge `files` 模块 (MCP 路线图 P1.3) 的 JSON 层, 基于以工作目录为根的 [NodeBridgeFileScope]: `list(path?, options?)`,
 * `stat(path)`, `read(path, options?)`, `write(path, content, options?)`, `mkdir(path)`, `rename(from, to, options?)`,
 * `delete(path, options?)`. 变更后在主线程刷新工作区文件管理器.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeFilesBridge(
    context: Context,
    private val rootProvider: () -> File = { File(WorkingDirectoryUtils.path) },
    /** Present for model-facing plugin sessions: lets `list` / `stat` read shared storage (MCP roadmap P11 D26). */
    private val modelPathPolicy: NodeBridgeModelPathPolicy? = null,
) {

    @Suppress("unused")
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun dispatch(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
        return try {
            val legacyFileApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            val shared = sharedStorageTarget(request, args)
            if (shared != null) {
                val scope = NodeBridgeFileScope(shared.root, legacyFileApi)
                return when (request.method) {
                    "list" -> list(request, scope, args, path = shared.relativePath, hidden = shared.hidden)
                    else -> stat(request, scope, args, path = shared.relativePath)
                }
            }
            val scope = NodeBridgeFileScope(rootProvider(), legacyFileApi)
            when (request.method) {
                "list" -> list(request, scope, args)
                "stat" -> stat(request, scope, args)
                "read" -> read(request, scope, args)
                "write" -> write(request, scope, args)
                "mkdir" -> mkdir(request, scope, args)
                "rename" -> rename(request, scope, args)
                "delete" -> delete(request, scope, args)
                else -> NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method))
            }
        } catch (error: NodeBridgeFileScopeException) {
            failure(request, error)
        } catch (error: SecurityException) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.permissionDenied(error.message ?: "files.${request.method} path is not allowed", request.module, request.method),
            )
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

    private class SharedStorageTarget(val root: File, val relativePath: String, val hidden: (File) -> Boolean)

    /**
     * An absolute `list` / `stat` path from a model-facing session resolves through the policy: inside the
     * working directory it stays on the working-directory scope (as a relative path); inside shared storage it
     * moves to a read-only scope rooted at the shared storage root, with `Android/data` and `Android/obb` hidden.
     */
    private fun sharedStorageTarget(request: NodeBridgeRequest, args: JSONArray): SharedStorageTarget? {
        val policy = modelPathPolicy ?: return null
        if (request.method != "list" && request.method != "stat") return null
        val rawPath = args.opt(0) as? String ?: return null
        if (!File(rawPath).isAbsolute) return null
        val resolved = policy.resolveRead(rawPath, request.permissions, "files.${request.method}")
        return when (resolved.location) {
            NodeBridgeModelPathPolicy.Location.WORKING_DIRECTORY -> {
                val root = policy.workingRoot
                SharedStorageTarget(root, NodeBridgeFileScope(root).relativePathOf(resolved.file)) { false }
            }
            NodeBridgeModelPathPolicy.Location.SHARED_STORAGE -> SharedStorageTarget(
                checkNotNull(policy.sharedRoot),
                policy.sharedRelativePath(resolved.file),
                policy::isHiddenInShared,
            )
        }
    }

    private fun list(
        request: NodeBridgeRequest,
        scope: NodeBridgeFileScope,
        args: JSONArray,
        path: String = pathArg(args, 0, NodeBridgeFileScope.ROOT_PATH),
        hidden: (File) -> Boolean = { false },
    ): NodeBridgeResponse {
        val options = optionsArg(request, args, 1, LIST_KEYS)
        val result = scope.list(
            path = path,
            recursive = booleanOf(options, "recursive", false),
            maxEntries = intOf(options, "maxEntries", NodeBridgeFileScope.MAX_LIST_ENTRIES),
            hidden = hidden,
        )
        val entries = JSONArray()
        result.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("path", entry.path)
                    .put("name", entry.name)
                    .put("type", if (entry.isDirectory) TYPE_DIRECTORY else TYPE_FILE)
                    .put("size", entry.size)
                    .put("lastModified", entry.lastModified),
            )
        }
        return success(
            request,
            JSONObject()
                .put("schema", schema("list"))
                .put("root", scope.rootDirectory.absolutePath)
                .put("path", result.path)
                .put("recursive", booleanOf(options, "recursive", false))
                .put("count", result.entries.size)
                .put("truncated", result.truncated)
                .put("entries", entries),
        )
    }

    private fun stat(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray, path: String = pathArg(args, 0)): NodeBridgeResponse {
        val stat = scope.stat(path)
        return success(
            request,
            JSONObject()
                .put("schema", schema("stat"))
                .put("root", scope.rootDirectory.absolutePath)
                .put("path", stat.path)
                .put("name", stat.name)
                .put("exists", stat.exists)
                .put("type", if (!stat.exists) JSONObject.NULL else if (stat.isDirectory) TYPE_DIRECTORY else TYPE_FILE)
                .put("size", stat.size)
                .put("lastModified", stat.lastModified),
        )
    }

    private fun read(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray): NodeBridgeResponse {
        val options = optionsArg(request, args, 1, READ_KEYS)
        val result = scope.read(
            path = pathArg(args, 0),
            encoding = options.optString("encoding", NodeBridgeFileScope.ENCODING_UTF8),
            maxBytes = intOf(options, "maxBytes", NodeBridgeFileScope.MAX_READ_BYTES),
        )
        return success(
            request,
            JSONObject()
                .put("schema", schema("read"))
                .put("path", result.path)
                .put("encoding", result.encoding)
                .put("bytes", result.bytes)
                .put("totalBytes", result.totalBytes)
                .put("truncated", result.truncated)
                .put("content", result.content),
        )
    }

    private fun write(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray): NodeBridgeResponse {
        val content = args.opt(1) as? String
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "files.write requires string content as the second argument")
        val options = optionsArg(request, args, 2, WRITE_KEYS)
        val result = scope.write(
            path = pathArg(args, 0),
            content = content,
            encoding = options.optString("encoding", NodeBridgeFileScope.ENCODING_UTF8),
            createDirs = booleanOf(options, "createDirs", true),
            overwrite = booleanOf(options, "overwrite", true),
            append = booleanOf(options, "append", false),
        )
        notifyWorkspaceChanged()
        return success(
            request,
            JSONObject()
                .put("schema", schema("write"))
                .put("path", result.path)
                .put("bytes", result.bytes)
                .put("created", result.created)
                .put("appended", result.appended),
        )
    }

    private fun mkdir(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray): NodeBridgeResponse {
        val result = scope.mkdir(pathArg(args, 0))
        if (result.created) notifyWorkspaceChanged()
        return success(
            request,
            JSONObject()
                .put("schema", schema("mkdir"))
                .put("path", result.path)
                .put("created", result.created),
        )
    }

    private fun rename(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray): NodeBridgeResponse {
        val options = optionsArg(request, args, 2, RENAME_KEYS)
        val result = scope.rename(
            from = pathArg(args, 0),
            to = pathArg(args, 1),
            overwrite = booleanOf(options, "overwrite", false),
        )
        notifyWorkspaceChanged()
        return success(
            request,
            JSONObject()
                .put("schema", schema("rename"))
                .put("from", result.from)
                .put("to", result.to)
                .put("overwritten", result.overwritten),
        )
    }

    private fun delete(request: NodeBridgeRequest, scope: NodeBridgeFileScope, args: JSONArray): NodeBridgeResponse {
        val options = optionsArg(request, args, 1, DELETE_KEYS)
        val result = scope.delete(
            path = pathArg(args, 0),
            recursive = booleanOf(options, "recursive", false),
        )
        notifyWorkspaceChanged()
        return success(
            request,
            JSONObject()
                .put("schema", schema("delete"))
                .put("path", result.path)
                .put("deleted", result.deleted),
        )
    }

    private fun notifyWorkspaceChanged() {
        val notify = Runnable { runCatching { Explorers.workspace().refreshAll() } }
        if (Looper.myLooper() == Looper.getMainLooper()) notify.run() else mainHandler.post(notify)
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
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.INVALID_PATH, "files.${request.method} options must be an object")
        val iterator = options.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in keys) {
                throw NodeBridgeFileScopeException(
                    NodeBridgeFileScope.Kind.INVALID_PATH,
                    "files.${request.method} does not accept option \"$key\"; expected one of ${keys.joinToString(", ")}",
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
        val message = "files.${request.method}: ${error.message}"
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

    private fun schema(method: String): String = "$SCHEMA_PREFIX$method-v1"

    companion object {
        const val SCHEMA_PREFIX = "autojs6-bridge-files-"
        const val TYPE_FILE = "file"
        const val TYPE_DIRECTORY = "directory"
        private val LIST_KEYS = setOf("recursive", "maxEntries")
        private val READ_KEYS = setOf("encoding", "maxBytes")
        private val WRITE_KEYS = setOf("encoding", "createDirs", "overwrite", "append")
        private val RENAME_KEYS = setOf("overwrite")
        private val DELETE_KEYS = setOf("recursive")
    }
}
