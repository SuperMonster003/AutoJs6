package org.autojs.autojs.engine

import android.annotation.SuppressLint
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * The path-restricted file surface behind the Node Bridge's `files` module (MCP roadmap P1.3,
 * modelled on `PythonHostFileScope`): every path is relative to [root], must be a normalized
 * forward-slash path without `.` / `..` segments, and must canonicalize inside the root, so a
 * symbolic link cannot lead out. Android-free; the JSON layer lives in `NodeBridgeFilesBridge`.
 * Failures are [NodeBridgeFileScopeException]s whose [NodeBridgeFileScopeException.kind] the
 * bridge maps to error categories.
 *
 * zh-CN: Node Bridge `files` 模块背后的受限文件表面 (MCP 路线图 P1.3, 仿 `PythonHostFileScope`): 每个路径相对于 [root], 须为不含
 * `.` / `..` 段的规范正斜杠路径, 且规范化后必须位于根目录内, 因此符号链接无法逃逸. Android-free; JSON 层位于 `NodeBridgeFilesBridge`.
 * 失败以 [NodeBridgeFileScopeException] 表示, bridge 按其 [NodeBridgeFileScopeException.kind] 映射错误分类.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
class NodeBridgeFileScope(root: File, private val legacyFileApi: Boolean = false) {

    val rootDirectory: File = root.canonicalFile.also { candidate ->
        if (!candidate.isDirectory) {
            throw NodeBridgeFileScopeException(Kind.IO, "working directory is not available: ${root.path}")
        }
    }

    private val rootPrefix = rootDirectory.path.trimEnd(File.separatorChar) + File.separator
    private val ignoreCase = File.separatorChar == '\\'

    enum class Kind { INVALID_PATH, ESCAPE, NOT_FOUND, WRONG_TYPE, EXISTS, LIMIT, IO }

    class Entry(val path: String, val name: String, val isDirectory: Boolean, val size: Long, val lastModified: Long)

    class ListResult(val path: String, val entries: List<Entry>, val truncated: Boolean)

    class Stat(val path: String, val name: String, val exists: Boolean, val isDirectory: Boolean, val isFile: Boolean, val size: Long, val lastModified: Long)

    class ReadResult(val path: String, val content: String, val encoding: String, val bytes: Int, val totalBytes: Long, val truncated: Boolean)

    class WriteResult(val path: String, val bytes: Int, val created: Boolean, val appended: Boolean)

    class MkdirResult(val path: String, val created: Boolean)

    class RenameResult(val from: String, val to: String, val overwritten: Boolean)

    class DeleteResult(val path: String, val deleted: Int)

    /** [hidden] entries are neither listed nor descended into (the shared-storage scope hides `Android/data` and `Android/obb`). */
    fun list(path: String = ROOT_PATH, recursive: Boolean = false, maxEntries: Int = MAX_LIST_ENTRIES, hidden: (File) -> Boolean = { false }): ListResult {
        val limit = maxEntries.coerceIn(1, MAX_LIST_ENTRIES)
        val directory = resolve(path)
        if (!directory.exists()) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "directory does not exist: $path")
        if (!directory.isDirectory) throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "not a directory: $path")
        val entries = ArrayList<Entry>()
        val truncated = !collect(directory, recursive, limit, entries, hidden)
        return ListResult(relativePathOf(directory), entries, truncated)
    }

    fun stat(path: String): Stat {
        val file = resolve(path)
        return Stat(
            path = relativePathOf(file),
            name = if (file == rootDirectory) "" else file.name,
            exists = file.exists(),
            isDirectory = file.isDirectory,
            isFile = file.isFile,
            size = if (file.isFile) file.length() else 0L,
            lastModified = if (file.exists()) file.lastModified() else 0L,
        )
    }

    fun read(path: String, encoding: String = ENCODING_UTF8, maxBytes: Int = MAX_READ_BYTES): ReadResult {
        val codec = requireEncoding(encoding)
        val limit = maxBytes.coerceIn(1, MAX_READ_BYTES)
        val file = requireRegularFile(path)
        val total = file.length()
        val bytes = readHead(file, limit)
        val truncated = total > bytes.size
        val content = when (codec) {
            ENCODING_BASE64 -> bytes.toByteString().base64()
            else -> decodeUtf8(bytes, allowCutTail = truncated)
        }
        return ReadResult(relativePathOf(file), content, codec, bytes.size, total, truncated)
    }

    fun write(
        path: String,
        content: String,
        encoding: String = ENCODING_UTF8,
        createDirs: Boolean = true,
        overwrite: Boolean = true,
        append: Boolean = false,
    ): WriteResult {
        val codec = requireEncoding(encoding)
        val bytes = when (codec) {
            ENCODING_BASE64 -> runCatching {
                require(content.none { it.isWhitespace() || it == '-' || it == '_' }) { "invalid base64" }
                content.decodeBase64()?.toByteArray() ?: throw IllegalArgumentException("invalid base64")
            }
                .getOrElse { throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "content is not valid base64") }
            else -> encodeUtf8(content)
        }
        if (bytes.size > MAX_WRITE_BYTES) {
            throw NodeBridgeFileScopeException(Kind.LIMIT, "content is ${bytes.size} bytes, above the limit of $MAX_WRITE_BYTES")
        }
        val file = resolveWritable(path)
        val existed = file.exists()
        if (existed && file.isDirectory) throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "target is a directory: $path")
        if (existed && !overwrite && !append) throw NodeBridgeFileScopeException(Kind.EXISTS, "file already exists: $path")
        val parent = file.parentFile ?: throw NodeBridgeFileScopeException(Kind.IO, "target has no parent directory: $path")
        if (!parent.isDirectory) {
            if (!createDirs) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "parent directory does not exist: $path")
            if (!parent.mkdirs() && !parent.isDirectory) throw NodeBridgeFileScopeException(Kind.IO, "unable to create parent directory: $path")
            guardInside(parent, path)
        }
        try {
            if (append && existed) file.appendBytes(bytes) else file.writeBytes(bytes)
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(Kind.IO, "unable to write $path: ${error.message}")
        }
        return WriteResult(relativePathOf(file), bytes.size, created = !existed, appended = append && existed)
    }

    fun mkdir(path: String): MkdirResult {
        val directory = resolveWritable(path)
        if (directory.isDirectory) return MkdirResult(relativePathOf(directory), created = false)
        if (directory.exists()) throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "a file occupies the path: $path")
        if (!directory.mkdirs() && !directory.isDirectory) throw NodeBridgeFileScopeException(Kind.IO, "unable to create directory: $path")
        guardInside(directory, path)
        return MkdirResult(relativePathOf(directory), created = true)
    }

    // NodeBridgeFilesBridge supplies legacyFileApi from SDK_INT < 26; desktop tests use NIO.
    // Keep this platform-independent branch injectable so both implementations can be tested.
    @SuppressLint("NewApi")
    fun rename(from: String, to: String, overwrite: Boolean = false): RenameResult {
        val source = resolveWritable(from)
        val target = resolveWritable(to)
        if (!source.exists()) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "source does not exist: $from")
        if (source == target) return RenameResult(relativePathOf(source), relativePathOf(target), overwritten = false)
        if (source.isDirectory && target.path.startsWith(source.path + File.separator, ignoreCase)) {
            throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "cannot move a directory into itself: $to")
        }
        val targetExisted = target.exists()
        if (targetExisted) {
            if (!overwrite) throw NodeBridgeFileScopeException(Kind.EXISTS, "target already exists: $to")
            if (target.isDirectory) throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "target is a directory: $to")
        }
        val parent = target.parentFile ?: throw NodeBridgeFileScopeException(Kind.IO, "target has no parent directory: $to")
        if (!parent.isDirectory) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "target parent directory does not exist: $to")
        try {
            if (legacyFileApi) {
                // Android 24 / 25 have neither File.toPath nor java.nio.file. Never pre-delete the destination.
                if (!source.renameTo(target)) throw IOException("filesystem refused rename")
            } else if (targetExisted) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.move(source.toPath(), target.toPath())
            }
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(Kind.IO, "unable to rename $from to $to: ${error.message}")
        }
        return RenameResult(relativePathOf(source), relativePathOf(target), overwritten = targetExisted)
    }

    fun delete(path: String, recursive: Boolean = false): DeleteResult {
        val file = resolveWritable(path)
        if (!file.exists()) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "target does not exist: $path")
        if (file.isDirectory) {
            val children = file.list()?.size ?: 0
            if (children > 0 && !recursive) {
                throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "directory is not empty (pass recursive to delete it): $path")
            }
            return DeleteResult(relativePathOf(file), deleteTree(file))
        }
        if (!file.delete()) throw NodeBridgeFileScopeException(Kind.IO, "unable to delete file: $path")
        return DeleteResult(relativePathOf(file), 1)
    }

    /** The scope-relative forward-slash path of [file], `.` for the root. zh-CN: [file] 相对于根的正斜杠路径, 根为 `.`. */
    fun relativePathOf(file: File): String {
        val path = file.path
        if (path == rootDirectory.path) return ROOT_PATH
        require(path.startsWith(rootPrefix, ignoreCase)) { "file is outside the scope: $path" }
        return path.substring(rootPrefix.length).replace(File.separatorChar, '/')
    }

    private fun collect(directory: File, recursive: Boolean, limit: Int, entries: MutableList<Entry>, hidden: (File) -> Boolean): Boolean {
        val children = directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return true
        for (child in children) {
            if (hidden(child)) continue
            if (entries.size >= limit) return false
            val isDirectory = child.isDirectory
            entries += Entry(
                path = relativePathOf(child),
                name = child.name,
                isDirectory = isDirectory,
                size = if (child.isFile) child.length() else 0L,
                lastModified = child.lastModified(),
            )
            if (recursive && isDirectory && !isSymbolicLink(child)) {
                if (!collect(child, true, limit, entries, hidden)) return false
            }
        }
        return true
    }

    private fun deleteTree(file: File): Int {
        val descendants = if (!isSymbolicLink(file) && file.isDirectory) {
            val children = file.listFiles() ?: throw NodeBridgeFileScopeException(Kind.IO, "unable to list a directory for deletion")
            children.sumOf { deleteTree(it) }
        } else 0
        if (!file.delete()) throw NodeBridgeFileScopeException(Kind.IO, "unable to delete a workspace entry")
        return descendants + 1
    }

    // The same SDK-selected branch as rename(), including the pre-26 implementation.
    @SuppressLint("NewApi")
    private fun isSymbolicLink(file: File): Boolean = if (legacyFileApi) {
        val entry = file.parentFile?.let { File(it.canonicalFile, file.name) } ?: file
        entry.canonicalFile != entry.absoluteFile
    } else Files.isSymbolicLink(file.toPath())

    private fun requireRegularFile(path: String): File {
        val file = resolve(path)
        if (!file.exists()) throw NodeBridgeFileScopeException(Kind.NOT_FOUND, "file does not exist: $path")
        if (!file.isFile) throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "not a regular file: $path")
        return file
    }

    private fun resolveWritable(path: String): File {
        val file = resolve(path)
        if (file == rootDirectory) throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "the working directory itself cannot be the target")
        return file
    }

    /**
     * Resolves a normalized relative path inside the root. A missing file is resolved through its
     * nearest existing ancestor so a symbolic link in the middle still cannot escape.
     * zh-CN: 把规范化的相对路径解析到根目录内. 不存在的文件经最近的已存在祖先解析, 使中途的符号链接同样无法逃逸.
     */
    private fun resolve(path: String): File {
        val relative = normalizeRelative(path)
        if (relative == ROOT_PATH) return rootDirectory
        val plain = File(rootDirectory, relative.replace('/', File.separatorChar))
        val canonical = try {
            plain.canonicalFile
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(Kind.IO, "unable to resolve $path: ${error.message}")
        }
        guardInside(canonical, path)
        return canonical
    }

    private fun guardInside(file: File, path: String) {
        val candidate = file.path
        if (candidate != rootDirectory.path && !candidate.startsWith(rootPrefix, ignoreCase)) {
            throw NodeBridgeFileScopeException(Kind.ESCAPE, "path escapes the working directory: $path")
        }
    }

    private fun readHead(file: File, limit: Int): ByteArray {
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(limit)
                var offset = 0
                while (offset < limit) {
                    val read = input.read(buffer, offset, limit - offset)
                    if (read < 0) break
                    offset += read
                }
                return if (offset == limit) buffer else buffer.copyOf(offset)
            }
        } catch (error: IOException) {
            throw NodeBridgeFileScopeException(Kind.IO, "unable to read ${file.name}: ${error.message}")
        }
    }

    private fun encodeUtf8(content: String): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val buffer = encoder.encode(java.nio.CharBuffer.wrap(content))
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } catch (_: CharacterCodingException) {
            throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "content contains unpaired surrogates and is not valid UTF-8")
        }
    }

    companion object {

        const val ROOT_PATH = "."
        const val ENCODING_UTF8 = "utf-8"
        const val ENCODING_BASE64 = "base64"
        const val MAX_PATH_BYTES = 4 * 1024
        const val MAX_LIST_ENTRIES = 2000
        const val MAX_READ_BYTES = 1024 * 1024
        const val MAX_WRITE_BYTES = 1024 * 1024

        private val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:.*")

        /**
         * Strict UTF-8 decoding; with [allowCutTail] an incomplete trailing sequence (a read cut at
         * a byte limit) is dropped instead of failing. Shared with `NodeBridgeSampleCatalog`.
         * zh-CN: 严格的 UTF-8 解码; [allowCutTail] 时丢弃末尾不完整的序列 (按字节上限截断的读取) 而非失败. 与 `NodeBridgeSampleCatalog` 共用.
         */
        internal fun decodeUtf8(bytes: ByteArray, allowCutTail: Boolean): String {
            var end = bytes.size
            if (allowCutTail && end > 0) {
                var start = end - 1
                var back = 0
                while (start > 0 && back < 3 && (bytes[start].toInt() and 0xC0) == 0x80) {
                    start--
                    back++
                }
                val lead = bytes[start].toInt() and 0xFF
                val expected = when {
                    lead >= 0xF0 -> 4
                    lead >= 0xE0 -> 3
                    lead >= 0xC0 -> 2
                    else -> 1
                }
                if (expected > end - start) end = start
            }
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(ByteBuffer.wrap(bytes, 0, end)).toString()
            } catch (_: CharacterCodingException) {
                throw NodeBridgeFileScopeException(Kind.WRONG_TYPE, "file is not valid UTF-8; read it with encoding \"$ENCODING_BASE64\"")
            }
        }

        fun requireEncoding(value: String): String {
            val encoding = value.trim().lowercase()
            return when (encoding) {
                "", ENCODING_UTF8, "utf8" -> ENCODING_UTF8
                ENCODING_BASE64 -> ENCODING_BASE64
                else -> throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "encoding must be \"$ENCODING_UTF8\" or \"$ENCODING_BASE64\", got \"$value\"")
            }
        }

        /**
         * `.` or a forward-slash relative path without empty, `.` or `..` segments, drive
         * prefixes, backslashes or control characters; a trailing slash is dropped.
         * zh-CN: `.` 或不含空段, `.`, `..` 段, 盘符前缀, 反斜杠与控制字符的正斜杠相对路径; 末尾斜杠会被去掉.
         */
        fun normalizeRelative(value: String): String {
            if (value.isEmpty()) throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "path is empty")
            if (value.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_BYTES) {
                throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "path exceeds $MAX_PATH_BYTES bytes")
            }
            if (value == ROOT_PATH || value == "./" || value == "/") {
                if (value == "/") throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "path must be relative to the working directory")
                return ROOT_PATH
            }
            val trimmed = value.removePrefix("./").removeSuffix("/")
            val invalid = when {
                trimmed.startsWith('/') -> "path must be relative to the working directory"
                WINDOWS_DRIVE_PATH.matches(trimmed) -> "path must not carry a drive prefix"
                '\\' in trimmed -> "path must use forward slashes"
                trimmed.any { it == '\u0000' || Character.isISOControl(it) } -> "path must not contain control characters"
                trimmed.split('/').any { it.isEmpty() || it == "." || it == ".." } -> "path must not contain empty, \".\" or \"..\" segments"
                else -> null
            }
            if (invalid != null) throw NodeBridgeFileScopeException(Kind.INVALID_PATH, "$invalid: $value")
            return trimmed
        }

    }

}

class NodeBridgeFileScopeException(val kind: NodeBridgeFileScope.Kind, message: String) : RuntimeException(message)
