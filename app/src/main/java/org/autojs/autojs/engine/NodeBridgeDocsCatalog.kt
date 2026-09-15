package org.autojs.autojs.engine

import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream

/**
 * Read-only catalog of the offline documentation shipped by the `AutoJs6-Plugin-Offline-Docs`
 * plugin (assets `docs/`) behind `app.listDocs` / `app.readDoc` (MCP roadmap P3.5, D20).
 * Android-free: [source] answers with a snapshot of the plugin the host currently trusts (its
 * verified inventory and an asset opener bound to the same package), or null when no eligible
 * plugin is installed, so the policy is unit-testable. Only paths of the verified inventory can
 * be read; directories are derived from those paths. Paths follow
 * [NodeBridgeFileScope.normalizeRelative] and stay inside the docs root.
 *
 * zh-CN: `AutoJs6-Plugin-Offline-Docs` 插件随附离线文档 (assets `docs/`) 的只读目录, 供 `app.listDocs` / `app.readDoc` 使用
 * (MCP 路线图 P3.5, D20). Android-free: [source] 返回宿主当前信任的插件快照 (已校验清单与绑定同一包的资产打开器), 无可用插件时返回 null,
 * 因此策略可单元测试. 仅可读取已校验清单中的路径; 目录由这些路径推导. 路径遵循 [NodeBridgeFileScope.normalizeRelative] 且限制在文档根内.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class NodeBridgeDocsCatalog(private val source: () -> Source?) {

    /** One trusted plugin at one moment: identity, verified inventory and an opener for its assets. */
    interface Source {
        val packageName: String
        val contentVersion: String
        val contractVersion: Int
        val entryPoint: String
        val fileCount: Long
        val totalBytes: Long

        /** Verified asset paths relative to the docs root, such as `index.html` or `assets/style.css`. */
        val assetPaths: Set<String>

        /** Opens an inventory path relative to the docs root; null when the asset cannot be opened. */
        fun open(relativePath: String): InputStream?
    }

    class Entry(val path: String, val name: String, val isDirectory: Boolean)

    class ListResult(val path: String, val entries: List<Entry>, val truncated: Boolean, val source: Source?) {
        val available: Boolean
            get() = source != null
    }

    class ReadResult(val path: String, val content: String, val encoding: String, val bytes: Int, val truncated: Boolean)

    fun list(path: String = ROOT_PATH, recursive: Boolean = false, maxEntries: Int = MAX_LIST_ENTRIES): ListResult {
        val relative = NodeBridgeFileScope.normalizeRelative(path)
        val limit = maxEntries.coerceIn(1, MAX_LIST_ENTRIES)
        val snapshot = source()
        if (snapshot == null) {
            if (relative == ROOT_PATH) return ListResult(ROOT_PATH, emptyList(), truncated = false, source = null)
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "offline documentation is not available: $path")
        }
        val tree = Tree(snapshot.assetPaths)
        if (relative != ROOT_PATH && !tree.isDirectory(relative)) {
            if (relative in snapshot.assetPaths) {
                throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "not a documentation directory: $path")
            }
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "documentation directory does not exist: $path")
        }
        val entries = ArrayList<Entry>()
        val complete = collect(tree, relative, recursive, limit, entries)
        return ListResult(relative, entries, truncated = !complete, source = snapshot)
    }

    fun read(path: String, encoding: String = NodeBridgeFileScope.ENCODING_UTF8, maxBytes: Int = MAX_READ_BYTES): ReadResult {
        val codec = NodeBridgeFileScope.requireEncoding(encoding)
        val limit = maxBytes.coerceIn(1, MAX_READ_BYTES)
        val relative = NodeBridgeFileScope.normalizeRelative(path)
        if (relative == ROOT_PATH) throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "the documentation root is a directory")
        val snapshot = source()
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "offline documentation is not available: $path")
        if (relative !in snapshot.assetPaths) {
            if (Tree(snapshot.assetPaths).isDirectory(relative)) {
                throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "not a documentation file: $path")
            }
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "documentation file does not exist: $path")
        }
        val stream = snapshot.open(relative)
            ?: throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.IO, "unable to open documentation $path")
        val (bytes, truncated) = try {
            stream.use { NodeBridgeSampleCatalog.readHead(it, limit) }
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.IO, "unable to read documentation $path: ${error.message}")
        }
        val content = when (codec) {
            NodeBridgeFileScope.ENCODING_BASE64 -> bytes.toByteString().base64()
            else -> NodeBridgeFileScope.decodeUtf8(bytes, allowCutTail = truncated)
        }
        return ReadResult(relative, content, codec, bytes.size, truncated)
    }

    private fun collect(tree: Tree, relative: String, recursive: Boolean, limit: Int, out: MutableList<Entry>): Boolean {
        for (entry in tree.childrenOf(relative)) {
            if (out.size >= limit) return false
            out += entry
            if (recursive && entry.isDirectory) {
                if (!collect(tree, entry.path, true, limit, out)) return false
            }
        }
        return true
    }

    /** Directories implied by the inventory paths; every path is validated the way callers' paths are. */
    private class Tree(paths: Set<String>) {

        private val children = HashMap<String, LinkedHashMap<String, Boolean>>()

        init {
            for (path in paths) {
                val relative = runCatching { NodeBridgeFileScope.normalizeRelative(path) }.getOrNull() ?: continue
                if (relative == ROOT_PATH) continue
                val segments = relative.split('/')
                var parent = ROOT_PATH
                segments.forEachIndexed { index, name ->
                    val siblings = children.getOrPut(parent) { LinkedHashMap() }
                    val isDirectory = index < segments.lastIndex
                    siblings[name] = (siblings[name] ?: false) || isDirectory
                    parent = join(parent, name)
                }
            }
        }

        fun isDirectory(path: String): Boolean = children.containsKey(path)

        fun childrenOf(path: String): List<Entry> = children[path].orEmpty()
            .map { (name, isDirectory) -> Entry(join(path, name), name, isDirectory) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    companion object {

        const val ASSET_ROOT = "docs"
        const val ROOT_PATH = NodeBridgeFileScope.ROOT_PATH
        const val MAX_LIST_ENTRIES = 2000
        const val MAX_READ_BYTES = 1024 * 1024

        private fun join(relative: String, name: String): String = if (relative == ROOT_PATH) name else "$relative/$name"
    }
}
