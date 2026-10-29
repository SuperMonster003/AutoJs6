package org.autojs.autojs.engine

import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64
import org.autojs.autojs.pio.PFiles

/**
 * Host-side equivalents of Node's public files facade. Android decides path access.
 *
 * The members of the Rhino files prototype (api-coverage P4.2b) follow the host's `Files` / `PFiles`: create / remove
 * results, byte payloads as base64 within the same budget, [assets] for readAssets, simplified paths by `PFiles`.
 */
internal class BunFilesBridge(
    private val directory: File,
    private val assets: (String) -> java.io.InputStream = { throw IOException("No application assets") },
    private val simplify: (String) -> String = { it },
) {
    fun invoke(method: String, args: List<Any?>): Any? {
        require(method in methods) { "Unsupported public files method: $method" }
        val expected = when (method) { "cwd" -> 0; "write", "append", "copy", "move", "writeBytes", "appendBytes", "rename", "renameWithoutExtension" -> 2; else -> 1 }
        require(args.size <= expected) { "Unexpected files.$method arguments" }
        fun text(index: Int): String? = if (args.getOrNull(index) == null) null else
            args[index] as? String ?: throw IllegalArgumentException("files.$method arguments must be strings")
        fun path(index: Int): File {
            val value = requireNotNull(text(index)) { "files.$method path must not be nullish" }
            require('\u0000' !in value) { "File path must not contain NUL" }
            val file = File(value)
            return (if (file.isAbsolute) file else File(directory, value)).toPath().toAbsolutePath().normalize().toFile()
        }
        fun bytes(index: Int) = Base64.getDecoder().decode(requireNotNull(text(index)) { "Encoded file contents are required" })
        fun encode(input: java.io.InputStream) = input.use {
            val bytes = it.readNBytes(MAX_READ_BYTES + 1)
            if (bytes.size > MAX_READ_BYTES) throw BunFilePayloadException()
            Base64.getEncoder().encodeToString(bytes)
        }
        if (method == "cwd") return directory.path
        if (text(0) == null) return when (method) {
            "path" -> null
            "read" -> ""
            "exists", "remove", "isFile", "isDir", "create", "createIfNotExists", "createWithDirs", "removeDir", "isEmptyDir" -> false
            "listDir" -> emptyList<String>()
            else -> throw IllegalArgumentException("files.$method path must not be nullish")
        }
        when (method) {
            "readAssets" -> return encode(assets(text(0)!!))
            "getSimplifiedPath" -> return simplify(text(0)!!)
        }
        val file = path(0)
        return when (method) {
            "create" -> if (text(0)!!.endsWith('/')) file.mkdir() else runCatching { file.createNewFile() }.isSuccess
            "createIfNotExists", "createWithDirs" -> {
                // PFiles.ensureDir creates the directories before the last separator, a trailing one included.
                (if (text(0)!!.endsWith('/')) file else file.parentFile)?.let { it.exists() || it.mkdirs() }
                !file.exists() && runCatching { file.createNewFile() }.getOrDefault(false)
            }
            "readBytes" -> encode(Files.newInputStream(file.toPath()))
            "writeBytes" -> { val data = bytes(1); java.io.FileOutputStream(file).use { it.write(data) }; null }
            "appendBytes" -> {
                val data = bytes(1)
                runCatching { file.createNewFile() }
                java.io.FileOutputStream(file, true).use { it.write(data) }; null
            }
            "rename", "renameWithoutExtension" -> {
                val name = requireNotNull(text(1)) { "files.$method newName must not be nullish" }
                file.renameTo(File(file.parentFile, if (method == "rename") name else name + "." + PFiles.getExtension(file.name)))
            }
            "removeDir" -> PFiles.deleteRecursively(file)
            "isEmptyDir" -> file.isDirectory && file.list()?.isEmpty() == true
            "path" -> file.path
            "read" -> encode(Files.newInputStream(file.toPath()))
            "write", "append" -> {
                val bytes = Base64.getDecoder().decode(requireNotNull(text(1)) { "Encoded file contents are required" })
                Files.write(file.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    if (method == "append") StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING)
                null
            }
            "exists" -> file.exists()
            "isFile" -> file.isFile
            "isDir" -> file.isDirectory
            "listDir" -> if (!file.exists()) emptyList<String>() else {
                require(file.isDirectory) { "Not a directory: $file" }
                file.list()?.toList() ?: throw IOException("Unable to list directory: $file")
            }
            "remove" -> if (!file.exists()) false else { Files.delete(file.toPath()); !file.exists() }
            "copy" -> {
                val target = path(1)
                if (!file.isFile) false else {
                    Files.createDirectories(target.parentFile!!.toPath())
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    true
                }
            }
            "move" -> {
                val target = path(1)
                if (!file.exists() || target.parentFile?.exists() != true) false else {
                    Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    true
                }
            }
            "ensureDir" -> {
                val raw = text(0)!!
                val target = if (raw.endsWith('/') || raw.endsWith('\\')) file else file.parentFile!!
                if (!target.exists()) Files.createDirectories(target.toPath())
                target.exists()
            }
            else -> error("Unreachable file method")
        }
    }

    companion object {
        val methods = setOf("path", "read", "write", "append", "exists", "listDir", "remove", "copy", "move", "ensureDir", "isFile", "isDir",
            "cwd", "create", "createIfNotExists", "createWithDirs", "readBytes", "writeBytes", "appendBytes", "rename", "renameWithoutExtension",
            "removeDir", "isEmptyDir", "readAssets", "getSimplifiedPath")
        // Leave room for the result envelope; encoded bytes use the existing JSON transport budget.
        const val MAX_READ_BYTES = (BunRuntimeContract.MAX_HOST_CALL_RESULT_BYTES - 128) / 4 * 3
    }
}

internal class BunFilePayloadException : IOException("File exceeds the Bun host result budget; use smaller files or Bun's native file APIs")
