package org.autojs.autojs.engine

import java.io.IOException
import java.io.InputStream
import java.util.Base64

/**
 * Read-only catalog of the host's built-in sample scripts (assets `sample/`) behind
 * `app.listSamples` / `app.readSample` (MCP roadmap P1.3, D20). Android-free: [lister] answers
 * like `AssetManager.list` (children of a directory, an empty array for files and missing paths)
 * and [opener] like `AssetManager.open` (null when the path is not a file), so the policy is
 * unit-testable. Paths follow [NodeBridgeFileScope.normalizeRelative] and stay inside the
 * sample root; dot-prefixed entries are hidden.
 *
 * zh-CN: 宿主内置示例脚本 (assets `sample/`) 的只读目录, 供 `app.listSamples` / `app.readSample` 使用 (MCP 路线图 P1.3, D20). Android-free:
 * [lister] 的行为同 `AssetManager.list` (目录返回子项, 文件与不存在的路径返回空数组), [opener] 同 `AssetManager.open` (非文件返回 null),
 * 因此策略可单元测试. 路径遵循 [NodeBridgeFileScope.normalizeRelative] 且限制在示例根内; 以点开头的条目被隐藏.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
class NodeBridgeSampleCatalog(
    private val lister: (assetPath: String) -> Array<String>?,
    private val opener: (assetPath: String) -> InputStream?,
) {

    class Entry(val path: String, val name: String, val isDirectory: Boolean)

    class ListResult(val path: String, val entries: List<Entry>, val truncated: Boolean, val available: Boolean)

    class ReadResult(val path: String, val content: String, val encoding: String, val bytes: Int, val truncated: Boolean)

    fun list(path: String = ROOT_PATH, recursive: Boolean = false, maxEntries: Int = MAX_LIST_ENTRIES): ListResult {
        val relative = NodeBridgeFileScope.normalizeRelative(path)
        val limit = maxEntries.coerceIn(1, MAX_LIST_ENTRIES)
        val assetPath = assetPathOf(relative)
        val children = children(assetPath)
        if (children.isEmpty()) {
            // The asset manager answers an empty array for files and missing paths alike; aapt
            // drops empty directories, so an empty listing never means an empty directory.
            if (relative == ROOT_PATH) return ListResult(ROOT_PATH, emptyList(), truncated = false, available = false)
            if (isFile(assetPath)) throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "not a sample directory: $path")
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "sample directory does not exist: $path")
        }
        val entries = ArrayList<Entry>()
        val complete = collect(relative, children, recursive, limit, entries)
        return ListResult(relative, entries, truncated = !complete, available = true)
    }

    fun read(path: String, encoding: String = NodeBridgeFileScope.ENCODING_UTF8, maxBytes: Int = MAX_READ_BYTES): ReadResult {
        val codec = NodeBridgeFileScope.requireEncoding(encoding)
        val limit = maxBytes.coerceIn(1, MAX_READ_BYTES)
        val relative = NodeBridgeFileScope.normalizeRelative(path)
        if (relative == ROOT_PATH) throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "the sample root is a directory")
        val assetPath = assetPathOf(relative)
        val stream = opener(assetPath) ?: run {
            if (children(assetPath).isNotEmpty()) {
                throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.WRONG_TYPE, "not a sample file: $path")
            }
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.NOT_FOUND, "sample file does not exist: $path")
        }
        val (bytes, truncated) = try {
            stream.use { readHead(it, limit) }
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(NodeBridgeFileScope.Kind.IO, "unable to read sample $path: ${error.message}")
        }
        val content = when (codec) {
            NodeBridgeFileScope.ENCODING_BASE64 -> Base64.getEncoder().encodeToString(bytes)
            else -> NodeBridgeFileScope.decodeUtf8(bytes, allowCutTail = truncated)
        }
        return ReadResult(relative, content, codec, bytes.size, truncated)
    }

    private fun collect(relative: String, children: Array<String>, recursive: Boolean, limit: Int, out: MutableList<Entry>): Boolean {
        val typed = children
            .filterNot { it.startsWith(".") }
            .map { name -> name to children(assetPathOf(join(relative, name))).isNotEmpty() }
            .sortedWith(compareBy({ !it.second }, { it.first }))
        for ((name, isDirectory) in typed) {
            if (out.size >= limit) return false
            val childPath = join(relative, name)
            out += Entry(childPath, name, isDirectory)
            if (recursive && isDirectory) {
                if (!collect(childPath, children(assetPathOf(childPath)), true, limit, out)) return false
            }
        }
        return true
    }

    private fun children(assetPath: String): Array<String> = runCatching { lister(assetPath) }.getOrNull() ?: emptyArray()

    private fun isFile(assetPath: String): Boolean = runCatching { opener(assetPath)?.use { true } }.getOrNull() ?: false

    private fun readHead(stream: InputStream, limit: Int): Pair<ByteArray, Boolean> {
        val buffer = ByteArray(limit)
        var offset = 0
        while (offset < limit) {
            val read = stream.read(buffer, offset, limit - offset)
            if (read < 0) break
            offset += read
        }
        val bytes = if (offset == limit) buffer else buffer.copyOf(offset)
        val truncated = offset == limit && stream.read() != -1
        return bytes to truncated
    }

    companion object {

        const val ASSET_ROOT = "sample"
        const val ROOT_PATH = NodeBridgeFileScope.ROOT_PATH
        const val MAX_LIST_ENTRIES = 2000
        const val MAX_READ_BYTES = 1024 * 1024

        fun assetPathOf(relative: String): String = if (relative == ROOT_PATH) ASSET_ROOT else "$ASSET_ROOT/$relative"

        private fun join(relative: String, name: String): String = if (relative == ROOT_PATH) name else "$relative/$name"
    }
}
