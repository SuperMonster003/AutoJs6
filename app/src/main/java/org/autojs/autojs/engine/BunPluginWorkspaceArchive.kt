package org.autojs.autojs.engine

import android.system.Os
import android.system.OsConstants
import org.autojs.autojs.util.OsConstantsCompat
import android.system.StructStat
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.autojs.engine.encryption.ScriptEncryption
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packs a declared Bun project into the bounded ZIP snapshot that the Bun Runtime plugin expands
 * as a workspace archive (contract keys `workspaceArchiveVersion` and `workspaceEntryPoint`).
 *
 * A project is the nearest ancestor directory of the script that carries `project.json` or
 * `package.json`; a bare script keeps the single-source path. Only regular files and directories
 * are archived. A symbolic link or special file aborts the run instead of being skipped silently,
 * so a run never depends on a file the plugin will not receive. The entry file's bytes come from
 * the caller (already decrypted), so unsaved editor buffers and encrypted entries execute against
 * the on-disk project tree.
 */
internal class BunPluginWorkspaceArchive private constructor(
    val file: File,
    val entryPoint: String,
    val entryCount: Int,
    val totalBytes: Long,
) {
    companion object {
        private const val MAX_ENTRIES = BunRuntimeContract.MAX_WORKSPACE_ENTRIES
        private const val MAX_BYTES = BunRuntimeContract.MAX_WORKSPACE_BYTES
        private const val MAX_PATH_DEPTH = 32
        private const val MAX_ANCESTOR_LEVELS = 16
        private const val COPY_BUFFER_BYTES = 32 * 1024
        private val PROJECT_MARKERS = listOf("project.json", "package.json")

        /** The declared project root of [scriptFile], or null when the script is a bare file. */
        fun findProjectRoot(scriptFile: File): File? {
            var directory: File? = scriptFile.absoluteFile.parentFile
            var level = 0
            while (directory != null && level < MAX_ANCESTOR_LEVELS) {
                val current: File = directory
                if (PROJECT_MARKERS.any { marker -> File(current, marker).isFile }) {
                    return runCatching { current.canonicalFile }.getOrDefault(current)
                }
                directory = current.parentFile
                level++
            }
            return null
        }

        /**
         * Writes the archive for [root] into a new temporary file below [directory]. [entryBytes]
         * replace the on-disk entry file (or add it when the semantic path is not on disk).
         */
        fun create(root: File, entryFile: File, entryBytes: ByteArray, directory: File, decryptProjectScripts: Boolean = false): BunPluginWorkspaceArchive {
            val canonicalRoot = root.canonicalFile
            val entryPoint = relativePath(canonicalRoot, entryFile.canonicalFile)
                ?: throw IOException("Bun entry file is outside its project root: ${entryFile.path}")
            validateRelativePath(entryPoint)
            val archive = File.createTempFile("bun-project-", ".zip", directory)
            try {
                val (entryCount, totalBytes) = writeArchive(canonicalRoot, entryPoint, entryBytes, archive, decryptProjectScripts)
                return BunPluginWorkspaceArchive(archive, entryPoint, entryCount, totalBytes)
            } catch (error: Throwable) {
                archive.delete()
                throw error
            }
        }

        private fun writeArchive(root: File, entryPoint: String, entryBytes: ByteArray, archive: File, decryptProjectScripts: Boolean): Pair<Int, Long> {
            var entryCount = 0
            var totalBytes = 0L
            var entryWritten = false
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            fun countEntry() {
                entryCount += 1
                if (entryCount > MAX_ENTRIES) throw IOException("Bun project exceeds $MAX_ENTRIES entries: ${root.path}")
            }
            fun account(bytes: Long) {
                totalBytes += bytes
                if (totalBytes > MAX_BYTES) throw IOException("Bun project exceeds $MAX_BYTES bytes: ${root.path}")
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(archive)), Charsets.UTF_8).use { output ->
                val pending = ArrayDeque<File>().apply { add(root) }
                while (pending.isNotEmpty()) {
                    val directory = pending.removeFirst()
                    val children = directory.listFiles()
                        ?: throw IOException("Bun project directory is unreadable: ${directory.path}")
                    children.sortedBy(File::getName).forEach { child ->
                        val relative = relativePath(root, child)
                            ?: throw IOException("Bun project entry escapes its root: ${child.path}")
                        validateRelativePath(relative)
                        val stat = Os.lstat(child.path)
                        when {
                            OsConstants.S_ISLNK(stat.st_mode) ->
                                throw IOException("Bun project contains a symbolic link: $relative")
                            OsConstants.S_ISDIR(stat.st_mode) -> {
                                countEntry()
                                output.putNextEntry(ZipEntry("$relative/"))
                                output.closeEntry()
                                pending.addLast(child)
                            }
                            OsConstants.S_ISREG(stat.st_mode) -> {
                                countEntry()
                                output.putNextEntry(ZipEntry(relative))
                                if (relative == entryPoint) {
                                    account(entryBytes.size.toLong())
                                    output.write(entryBytes)
                                    entryWritten = true
                                } else {
                                    copyRegularFile(child, stat, output, buffer, ::account, decryptProjectScripts)
                                }
                                output.closeEntry()
                            }
                            else -> throw IOException("Bun project contains a special file: $relative")
                        }
                    }
                }
                if (!entryWritten) {
                    countEntry()
                    account(entryBytes.size.toLong())
                    output.putNextEntry(ZipEntry(entryPoint))
                    output.write(entryBytes)
                    output.closeEntry()
                }
            }
            if (archive.length() > MAX_BYTES) {
                throw IOException("Bun project archive exceeds $MAX_BYTES bytes: ${root.path}")
            }
            return entryCount to totalBytes
        }

        /** Opens without following links and re-checks the inode so a swapped path cannot leak another file. */
        private fun copyRegularFile(
            file: File,
            expected: StructStat,
            output: ZipOutputStream,
            buffer: ByteArray,
            account: (Long) -> Unit,
            decryptProjectScripts: Boolean,
        ) {
            val descriptor = Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstantsCompat.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
            try {
                FileInputStream(descriptor).use { input ->
                    val opened = Os.fstat(input.fd)
                    if (!OsConstants.S_ISREG(opened.st_mode) ||
                        opened.st_dev != expected.st_dev ||
                        opened.st_ino != expected.st_ino
                    ) {
                        throw IOException("Bun project file changed while it was being archived: ${file.path}")
                    }
                    if (decryptProjectScripts && file.extension.lowercase() in setOf("js", "mjs", "cjs", "jsx", "ts", "mts", "cts", "tsx")) {
                        val maximum = BunRuntimeContract.MAX_SOURCE_BYTES + EncryptedScriptFileHeader.BLOCK_SIZE + 16
                        val stored = ByteArrayOutputStream()
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(stored.size().toLong() + count <= maximum) { "Bun project script exceeds its stored size limit" }
                            stored.write(buffer, 0, count)
                        }
                        val bytes = stored.toByteArray()
                        var cleartext: ByteArray? = null
                        try {
                            cleartext = if (bytes.size >= EncryptedScriptFileHeader.BLOCK_SIZE && EncryptedScriptFileHeader.isValidFile(bytes))
                                ScriptEncryption.decrypt(bytes, EncryptedScriptFileHeader.BLOCK_SIZE) else bytes
                            require(cleartext.size.toLong() <= BunRuntimeContract.MAX_SOURCE_BYTES) { "Bun project script exceeds its source size limit" }
                            account(cleartext.size.toLong())
                            output.write(cleartext)
                        } finally { bytes.fill(0); cleartext?.fill(0) }
                        return
                    }
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        account(count.toLong())
                        output.write(buffer, 0, count)
                    }
                }
            } finally {
                // FileInputStream(FileDescriptor) never owns the descriptor on Android.
                runCatching { Os.close(descriptor) }
            }
        }

        private fun relativePath(root: File, file: File): String? {
            val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
            val path = file.path
            if (!path.startsWith(rootPath) || path.length <= rootPath.length) return null
            return path.substring(rootPath.length).replace(File.separatorChar, '/')
        }

        /** Mirrors the plugin's entry-path rules so a rejected project fails on the host with a readable path. */
        private fun validateRelativePath(relative: String) {
            if (relative.split('/').size > MAX_PATH_DEPTH) {
                throw IOException("Bun project path is too deep: $relative")
            }
            relative.forEach { character ->
                if (character.code < 0x20 || character.code == 0x7f || character == '\\' || character == ':') {
                    throw IOException("Bun project path contains an unsupported character: $relative")
                }
            }
        }
    }
}
