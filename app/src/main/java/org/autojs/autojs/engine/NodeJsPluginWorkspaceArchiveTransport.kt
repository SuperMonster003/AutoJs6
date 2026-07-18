package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Transfers one host-private Node sandbox to an external runtime plugin without
 * granting either app broad storage access.
 *
 * Binder transfers already-open descriptors, not private paths. The plugin
 * expands the input archive below its own cache directory and writes a final
 * snapshot to the output descriptor. On return we validate every entry again
 * before copying it below the original host sandbox. Symlinks and special files
 * are intentionally rejected on both sides of the boundary.
 */
internal class NodeJsPluginWorkspaceArchiveTransport private constructor(
    private val sandboxRoot: File,
    private val workingDirectory: File,
    private val sandboxIdentity: DirectoryIdentity,
    private val transportDirectory: File,
    private val outputArchive: File,
    private val deleteEligibleInputFiles: Set<String>,
    private var inputDescriptor: ParcelFileDescriptor?,
    private var outputDescriptor: ParcelFileDescriptor?,
    private val relativeWorkingDirectory: String,
) : Closeable {

    private val synchronized = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun attachTo(request: Bundle) {
        check(!closed.get()) { "Node.js plugin workspace transport is already closed." }
        // [create] has already canonicalized and containment-checked these paths. Always send
        // those exact values across Binder so Android's /data/user/0 and /data/data aliases do
        // not look like different trees to the plugin's lexical sandbox validation.
        request.putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, sandboxRoot.absolutePath)
        request.putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, workingDirectory.absolutePath)
        request.putInt(
            NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_TRANSPORT_VERSION,
            NodeJsRuntimeContract.WORKSPACE_ARCHIVE_TRANSPORT_CONTRACT_VERSION,
        )
        request.putParcelable(
            NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_INPUT_FD,
            checkNotNull(inputDescriptor),
        )
        request.putParcelable(
            NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_OUTPUT_FD,
            checkNotNull(outputDescriptor),
        )
        request.putString(
            NodeJsRuntimeContract.KEY_WORKSPACE_RELATIVE_WORKING_DIRECTORY,
            relativeWorkingDirectory,
        )
        request.putInt(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_FILES, MAX_FILES)
        request.putLong(NodeJsRuntimeContract.KEY_WORKSPACE_ARCHIVE_MAX_BYTES, MAX_BYTES)
    }

    /** Idempotent so normal completion and Binder-death cleanup can share it. */
    fun synchronizeBack() {
        if (!synchronized.compareAndSet(false, true)) return
        closeDescriptors()
        if (!outputArchive.isFile || outputArchive.length() == 0L) return
        if (outputArchive.length() > MAX_ARCHIVE_BYTES) {
            throw IOException("Node.js plugin workspace output archive exceeds the compressed-size limit.")
        }
        if (!sandboxIdentity.matches(sandboxRoot)) {
            throw IOException("Node.js plugin workspace sandbox identity changed during execution.")
        }
        extractArchive(outputArchive, sandboxRoot, deleteEligibleInputFiles)
        if (!sandboxIdentity.matches(sandboxRoot)) {
            throw IOException("Node.js plugin workspace sandbox identity changed during synchronization.")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeDescriptors()
        deleteTransportDirectory()
    }

    private fun closeDescriptors() {
        runCatching { inputDescriptor?.close() }
        runCatching { outputDescriptor?.close() }
        inputDescriptor = null
        outputDescriptor = null
    }

    private fun deleteTransportDirectory() {
        val expectedParent = transportDirectory.parentFile?.canonicalFile ?: return
        val actual = runCatching { transportDirectory.canonicalFile }.getOrNull() ?: return
        if (actual.parentFile != expectedParent || actual.name.startsWith(TRANSPORT_DIRECTORY_PREFIX).not()) {
            return
        }
        actual.walkBottomUp().forEach { entry -> runCatching { entry.delete() } }
    }

    companion object {

        private const val TRANSPORT_DIRECTORY_PREFIX = "request-"
        private const val INPUT_ARCHIVE_NAME = "workspace-input.zip"
        private const val OUTPUT_ARCHIVE_NAME = "workspace-output.zip"
        private const val TRANSPORT_PROTOCOL_DIRECTORY = ".autojs6-workspace-transport"
        private const val INPUT_MANIFEST_PATH =
            "$TRANSPORT_PROTOCOL_DIRECTORY/input-manifest-v1.json"
        private const val OUTPUT_TOMBSTONE_PATH =
            "$TRANSPORT_PROTOCOL_DIRECTORY/deletions-v1.json"
        private const val MANIFEST_VERSION = 1
        private const val MANIFEST_MAX_BYTES = 1024 * 1024
        private const val COPY_BUFFER_BYTES = 32 * 1024
        internal const val MAX_FILES = 4_096
        internal const val MAX_BYTES = 64L * 1024L * 1024L
        private const val MAX_ARCHIVE_BYTES = MAX_BYTES + 8L * 1024L * 1024L

        fun create(
            context: Context,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        ): NodeJsPluginWorkspaceArchiveTransport {
            val appContext = context.applicationContext
            val fallbackRoot = File(
                appContext.cacheDir,
                "node-plugin-default-workspaces/${safeName(request.executionId.orEmpty())}",
            )
            val requestedWorkingDirectory = request.workingDirectory?.takeIf(String::isNotBlank)
            val requestedSandboxRoot = request.sandboxRoot?.takeIf(String::isNotBlank)
            val sandboxRoot = File(requestedSandboxRoot ?: requestedWorkingDirectory ?: fallbackRoot.path)
                .canonicalFile
            val workingDirectory = File(requestedWorkingDirectory ?: sandboxRoot.path).canonicalFile
            ensureDirectory(sandboxRoot, "sandboxRoot")
            ensureDirectory(workingDirectory, "workingDirectory")
            if (!contains(sandboxRoot, workingDirectory)) {
                throw IOException("Node.js plugin workingDirectory escapes sandboxRoot.")
            }
            val sandboxIdentity = DirectoryIdentity.capture(sandboxRoot)
                ?: throw IOException("Node.js plugin sandboxRoot identity is unavailable.")
            val relativeWorkingDirectory = relativePath(sandboxRoot, workingDirectory)

            val transportParent = File(appContext.cacheDir, "node-plugin-workspace-transport")
            ensureDirectory(transportParent, "workspace transport root")
            val transportDirectory = File(
                transportParent,
                "$TRANSPORT_DIRECTORY_PREFIX${safeName(request.executionId.orEmpty())}-${UUID.randomUUID()}",
            )
            if (!transportDirectory.mkdir()) {
                throw IOException("Unable to create the Node.js plugin workspace transport directory.")
            }
            val inputArchive = File(transportDirectory, INPUT_ARCHIVE_NAME)
            val outputArchive = File(transportDirectory, OUTPUT_ARCHIVE_NAME)
            try {
                val deleteEligibleInputFiles = writeExplicitInputArchive(
                    root = sandboxRoot,
                    workingDirectory = workingDirectory,
                    request = request,
                    archive = inputArchive,
                )
                if (!sandboxIdentity.matches(sandboxRoot)) {
                    throw IOException("Node.js plugin sandboxRoot changed while creating its snapshot.")
                }
                if (!outputArchive.createNewFile()) {
                    throw IOException("Unable to create the Node.js plugin workspace output archive.")
                }
                val inputDescriptor = ParcelFileDescriptor.open(
                    inputArchive,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                val outputDescriptor = try {
                    ParcelFileDescriptor.open(
                        outputArchive,
                        ParcelFileDescriptor.MODE_READ_WRITE or
                                ParcelFileDescriptor.MODE_TRUNCATE,
                    )
                } catch (error: Throwable) {
                    runCatching { inputDescriptor.close() }
                    throw error
                }
                return NodeJsPluginWorkspaceArchiveTransport(
                    sandboxRoot = sandboxRoot,
                    workingDirectory = workingDirectory,
                    sandboxIdentity = sandboxIdentity,
                    transportDirectory = transportDirectory,
                    outputArchive = outputArchive,
                    deleteEligibleInputFiles = deleteEligibleInputFiles,
                    inputDescriptor = inputDescriptor,
                    outputDescriptor = outputDescriptor,
                    relativeWorkingDirectory = relativeWorkingDirectory,
                )
            } catch (error: Throwable) {
                transportDirectory.walkBottomUp().forEach { entry -> runCatching { entry.delete() } }
                throw error
            }
        }

        /**
         * Deliberately does not enumerate [root]. R4 requires the default
         * execution path to remain free of legacy working-directory scans.
         * Only paths already named by the request are eligible input entries.
         */
        private fun isAndroidCredentialDataAlias(lexical: File, canonical: File): Boolean {
            fun normalized(path: String): String = path
                .replace(Regex("^/data/user/0/([^/]+)(?=/|$)"), "/data/data/$1")
                .trimEnd('/')
            return normalized(lexical.absolutePath) == normalized(canonical.absolutePath)
        }

        private fun writeExplicitInputArchive(
            root: File,
            workingDirectory: File,
            request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
            archive: File,
        ): Set<String> {
            var fileCount = 0
            var totalBytes = 0L
            val entries = LinkedHashMap<String, ExplicitInput>()

            fun relativeCandidate(value: String): Pair<String, File>? {
                if (value.isBlank() || value.startsWith("<") || '\u0000' in value) return null
                val requested = File(value).let { if (it.isAbsolute) it else File(workingDirectory, value) }
                val lexical = requested.absoluteFile.normalize()
                val canonical = runCatching { requested.canonicalFile }.getOrNull() ?: return null
                val acceptedAndroidAlias = isAndroidCredentialDataAlias(lexical, canonical)
                if (!contains(root, lexical) && !acceptedAndroidAlias) return null
                if (!contains(root, canonical)) {
                    throw IOException("Node.js plugin explicit workspace input escapes through a symbolic link.")
                }
                if (lexical.absolutePath != canonical.absolutePath && !acceptedAndroidAlias) {
                    throw IOException("Node.js plugin explicit workspace inputs may not traverse symbolic links.")
                }
                validateRealPathComponents(root, canonical, allowMissingLeaf = true)
                val relative = relativePath(root, canonical)
                if (relative.isBlank()) return null
                if (isTransportProtocolPath(relative)) {
                    throw IOException("Node.js plugin explicit workspace input uses the reserved transport namespace.")
                }
                return relative to canonical
            }

            fun addExistingExplicitPath(value: String) {
                val (relative, requested) = relativeCandidate(value) ?: return
                if (!requested.exists()) return
                val stat = Os.lstat(requested.path)
                if (OsConstants.S_ISLNK(stat.st_mode)) {
                    throw IOException("Node.js plugin explicit workspace input is a symbolic link: ${requested.path}")
                }
                when {
                    OsConstants.S_ISDIR(stat.st_mode) -> entries.putIfAbsent(relative, ExplicitInput.Directory)
                    OsConstants.S_ISREG(stat.st_mode) -> entries.putIfAbsent(relative, ExplicitInput.FileEntry(requested))
                    else -> throw IOException("Node.js plugin explicit workspace input is a special file: ${requested.path}")
                }
            }

            request.sourceName.takeIf(String::isNotBlank)?.let(::addExistingExplicitPath)
            request.env.values.forEach(::addExistingExplicitPath)
            request.moduleSources.forEach { (name, source) ->
                val (relative, _) = relativeCandidate(name) ?: return@forEach
                entries[relative] = ExplicitInput.Bytes(source.toByteArray(StandardCharsets.UTF_8))
            }

            val deleteEligibleInputFiles = entries
                .filterValues { it is ExplicitInput.FileEntry }
                .keys
                .toSortedSet()
            val manifestBytes = inputManifest(deleteEligibleInputFiles)
                .toByteArray(StandardCharsets.UTF_8)
            if (manifestBytes.size > MANIFEST_MAX_BYTES) {
                throw IOException("Node.js plugin workspace input manifest exceeds its byte limit.")
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { output ->
                output.putNextEntry(ZipEntry(INPUT_MANIFEST_PATH))
                output.write(manifestBytes)
                output.closeEntry()
                entries.toSortedMap().forEach { (relative, explicit) ->
                    fileCount = checkedFileCount(fileCount)
                    when (explicit) {
                        ExplicitInput.Directory -> {
                            output.putNextEntry(ZipEntry("$relative/"))
                            output.closeEntry()
                        }
                        is ExplicitInput.Bytes -> {
                            totalBytes += explicit.value.size
                            if (totalBytes > MAX_BYTES) {
                                throw IOException("Node.js plugin explicit workspace inputs exceed the byte limit.")
                            }
                            output.putNextEntry(ZipEntry(relative))
                            output.write(explicit.value)
                            output.closeEntry()
                        }
                        is ExplicitInput.FileEntry -> {
                            validateRealPathComponents(root, explicit.file, allowMissingLeaf = false)
                            val stat = Os.lstat(explicit.file.path)
                            if (!OsConstants.S_ISREG(stat.st_mode) || OsConstants.S_ISLNK(stat.st_mode)) {
                                throw IOException("Node.js plugin explicit workspace input changed before open.")
                            }
                            output.putNextEntry(ZipEntry(relative).apply { time = explicit.file.lastModified() })
                            val descriptor = Os.open(
                                explicit.file.path,
                                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                                0,
                            )
                            FileInputStream(descriptor).use { input ->
                                val openedStat = Os.fstat(input.fd)
                                if (
                                    !OsConstants.S_ISREG(openedStat.st_mode) ||
                                    openedStat.st_dev != stat.st_dev ||
                                    openedStat.st_ino != stat.st_ino ||
                                    openedDescriptorPath(input.fd) != explicit.file.absolutePath
                                ) {
                                    throw IOException("Node.js plugin explicit workspace input changed during open.")
                                }
                                val buffer = ByteArray(COPY_BUFFER_BYTES)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    totalBytes += count
                                    if (totalBytes > MAX_BYTES) {
                                        throw IOException("Node.js plugin workspace exceeds the byte limit.")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            output.closeEntry()
                        }
                    }
                }
            }
            if (archive.length() > MAX_ARCHIVE_BYTES) {
                throw IOException("Node.js plugin workspace input archive exceeds the compressed-size limit.")
            }
            return deleteEligibleInputFiles
        }

        private fun inputManifest(deleteEligibleInputFiles: Set<String>): String = JSONObject()
            .put("version", MANIFEST_VERSION)
            .put(
                "deleteEligibleFiles",
                JSONArray().apply { deleteEligibleInputFiles.forEach(::put) },
            )
            .toString()

        private fun extractArchive(
            archive: File,
            root: File,
            deleteEligibleInputFiles: Set<String>,
        ) {
            val plan = inspectOutputArchive(archive, deleteEligibleInputFiles)
            var fileCount = 0
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name == OUTPUT_TOMBSTONE_PATH) {
                        drainManifestEntry(input)
                        input.closeEntry()
                        continue
                    }
                    fileCount = checkedFileCount(fileCount)
                    val relative = validatedArchivePath(entry.name)
                    val target = File(root, relative)
                    ensureContainedTarget(root, target)
                    if (entry.isDirectory) {
                        ensureSafeDirectories(root, target)
                    } else {
                        val parent = target.parentFile
                            ?: throw IOException("Node.js plugin workspace output has no parent.")
                        ensureSafeDirectories(root, parent)
                        rejectSymbolicLink(target)
                        val temporary = File.createTempFile(".node-plugin-", ".tmp", parent)
                        try {
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(COPY_BUFFER_BYTES)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    totalBytes += count
                                    if (totalBytes > MAX_BYTES) {
                                        throw IOException("Node.js plugin workspace output exceeds the byte limit.")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            rejectSymbolicLink(target)
                            Os.rename(temporary.path, target.path)
                            if (entry.time > 0L) target.setLastModified(entry.time)
                        } finally {
                            runCatching { temporary.delete() }
                        }
                    }
                    input.closeEntry()
                }
            }
            plan.deletedFiles.forEach { relative -> deleteExplicitInputFile(root, relative) }
        }

        /**
         * Validates the entire plugin-private snapshot before mutating the host
         * sandbox. The final tombstone manifest doubles as a completion marker,
         * so a process death cannot turn a partial archive into deletions.
         */
        private fun inspectOutputArchive(
            archive: File,
            deleteEligibleInputFiles: Set<String>,
        ): OutputArchivePlan {
            var fileCount = 0
            var totalBytes = 0L
            var tombstoneManifestSeen = false
            var deletedFiles: Set<String>? = null
            val outputPaths = LinkedHashSet<String>()
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (tombstoneManifestSeen) {
                        throw IOException("Node.js plugin workspace tombstone manifest must be the final archive entry.")
                    }
                    if (entry.name == OUTPUT_TOMBSTONE_PATH) {
                        if (entry.isDirectory) {
                            throw IOException("Node.js plugin workspace tombstone manifest is a directory.")
                        }
                        deletedFiles = parseTombstoneManifest(readManifestEntry(input))
                        tombstoneManifestSeen = true
                        input.closeEntry()
                        continue
                    }
                    val relative = validatedArchivePath(entry.name)
                    if (isTransportProtocolPath(relative)) {
                        throw IOException("Node.js plugin workspace output uses the reserved transport namespace.")
                    }
                    if (!outputPaths.add(relative)) {
                        throw IOException("Node.js plugin workspace output contains a duplicate path.")
                    }
                    fileCount = checkedFileCount(fileCount)
                    if (!entry.isDirectory) {
                        totalBytes = drainArchiveEntry(input, totalBytes)
                    }
                    input.closeEntry()
                }
            }
            val deletions = deletedFiles
                ?: throw IOException("Node.js plugin workspace output is incomplete: tombstone manifest is missing.")
            deletions.forEach { relative ->
                if (relative !in deleteEligibleInputFiles) {
                    throw IOException("Node.js plugin workspace requested deletion of an undeclared input file.")
                }
                if (outputPaths.any { it == relative || it.startsWith("$relative/") }) {
                    throw IOException("Node.js plugin workspace deletion conflicts with an output path.")
                }
            }
            return OutputArchivePlan(deletions.toSortedSet())
        }

        private fun parseTombstoneManifest(text: String): Set<String> {
            val json = try {
                JSONObject(text)
            } catch (error: Throwable) {
                throw IOException("Node.js plugin workspace tombstone manifest is invalid.", error)
            }
            if (json.optInt("version", 0) != MANIFEST_VERSION) {
                throw IOException("Unsupported Node.js plugin workspace tombstone manifest version.")
            }
            val files = json.optJSONArray("deletedFiles")
                ?: throw IOException("Node.js plugin workspace tombstone manifest has no deletedFiles array.")
            val result = LinkedHashSet<String>()
            for (index in 0 until files.length()) {
                val raw = files.opt(index)
                if (raw !is String) {
                    throw IOException("Node.js plugin workspace tombstone path is not a string.")
                }
                val relative = validatedArchivePath(raw)
                if (isTransportProtocolPath(relative) || !result.add(relative)) {
                    throw IOException("Node.js plugin workspace tombstone manifest contains an invalid path.")
                }
            }
            return result
        }

        private fun readManifestEntry(input: ZipInputStream): String {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(4 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > MANIFEST_MAX_BYTES) {
                    throw IOException("Node.js plugin workspace manifest exceeds its byte limit.")
                }
                bytes.write(buffer, 0, count)
            }
            return bytes.toString(StandardCharsets.UTF_8.name())
        }

        private fun drainManifestEntry(input: ZipInputStream) {
            var total = 0
            val buffer = ByteArray(4 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return
                if (count == 0) continue
                total += count
                if (total > MANIFEST_MAX_BYTES) {
                    throw IOException("Node.js plugin workspace manifest exceeds its byte limit.")
                }
            }
        }

        private fun drainArchiveEntry(input: ZipInputStream, previousBytes: Long): Long {
            var totalBytes = previousBytes
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return totalBytes
                if (count == 0) continue
                totalBytes += count
                if (totalBytes > MAX_BYTES) {
                    throw IOException("Node.js plugin workspace output exceeds the byte limit.")
                }
            }
        }

        private fun deleteExplicitInputFile(root: File, relative: String) {
            val target = File(root, validatedArchivePath(relative))
            ensureContainedTarget(root, target)
            var current = root
            relative.split('/').forEachIndexed { index, segment ->
                current = File(current, segment)
                val stat = try {
                    Os.lstat(current.path)
                } catch (error: ErrnoException) {
                    if (error.errno == OsConstants.ENOENT) return
                    throw IOException("Unable to inspect Node.js plugin workspace deletion path.", error)
                }
                if (OsConstants.S_ISLNK(stat.st_mode)) {
                    throw IOException("Node.js plugin workspace deletion path traverses a symbolic link.")
                }
                if (index < relative.count { it == '/' } && !OsConstants.S_ISDIR(stat.st_mode)) {
                    throw IOException("Node.js plugin workspace deletion parent is not a directory.")
                }
                if (index == relative.count { it == '/' } && !OsConstants.S_ISREG(stat.st_mode)) {
                    throw IOException("Node.js plugin workspace tombstone targets a non-regular file.")
                }
            }
            val before = Os.lstat(target.path)
            val descriptor = Os.open(
                target.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
            FileInputStream(descriptor).use { input ->
                val opened = Os.fstat(input.fd)
                if (
                    !OsConstants.S_ISREG(opened.st_mode) ||
                    opened.st_dev != before.st_dev ||
                    opened.st_ino != before.st_ino ||
                    openedDescriptorPath(input.fd) != target.absolutePath
                ) {
                    throw IOException("Node.js plugin workspace deletion target changed during open.")
                }
            }
            val after = Os.lstat(target.path)
            if (after.st_dev != before.st_dev || after.st_ino != before.st_ino) {
                throw IOException("Node.js plugin workspace deletion target changed before unlink.")
            }
            if (!target.delete()) {
                throw IOException("Unable to delete Node.js plugin workspace input file.")
            }
        }

        private fun ensureSafeDirectories(root: File, directory: File) {
            ensureContainedTarget(root, directory)
            val relative = relativePath(root, directory)
            var current = root
            if (relative.isBlank()) return
            relative.split('/').forEach { segment ->
                current = File(current, segment)
                rejectSymbolicLink(current)
                if (current.exists()) {
                    if (!current.isDirectory) {
                        throw IOException("Node.js plugin workspace output collides with a non-directory.")
                    }
                } else if (!current.mkdir()) {
                    throw IOException("Unable to create Node.js plugin workspace output directory.")
                }
            }
        }

        private fun ensureContainedTarget(root: File, target: File) {
            val normalized = target.absoluteFile.normalize()
            if (!contains(root, normalized)) {
                throw IOException("Node.js plugin workspace archive path escapes sandboxRoot.")
            }
        }

        private fun rejectSymbolicLink(file: File) {
            try {
                val stat = Os.lstat(file.path)
                if (OsConstants.S_ISLNK(stat.st_mode)) {
                    throw IOException("Node.js plugin workspace output targets a symbolic link.")
                }
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.ENOENT) throw error
            }
        }

        private fun validatedArchivePath(name: String): String {
            if (name.isBlank() || name.startsWith('/') || '\\' in name || '\u0000' in name) {
                throw IOException("Node.js plugin workspace archive contains an invalid path.")
            }
            val segments = name.trimEnd('/').split('/')
            if (segments.isEmpty() || segments.any { it.isBlank() || it == "." || it == ".." }) {
                throw IOException("Node.js plugin workspace archive contains a path traversal.")
            }
            return segments.joinToString("/")
        }

        private fun isTransportProtocolPath(relative: String): Boolean =
            relative == TRANSPORT_PROTOCOL_DIRECTORY ||
                relative.startsWith("$TRANSPORT_PROTOCOL_DIRECTORY/")

        private fun checkedFileCount(previous: Int): Int {
            if (previous >= MAX_FILES) {
                throw IOException("Node.js plugin workspace exceeds the entry-count limit.")
            }
            return previous + 1
        }

        private fun ensureDirectory(directory: File, label: String) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Unable to create Node.js plugin $label: ${directory.path}")
            }
            val stat = Os.lstat(directory.path)
            if (!OsConstants.S_ISDIR(stat.st_mode) || OsConstants.S_ISLNK(stat.st_mode)) {
                throw IOException("Node.js plugin $label is not a real directory: ${directory.path}")
            }
        }

        private fun validateRealPathComponents(
            root: File,
            candidate: File,
            allowMissingLeaf: Boolean,
        ) {
            val relative = relativePath(root, candidate)
            var current = root
            relative.split('/').filter(String::isNotBlank).forEach { segment ->
                current = File(current, segment)
                try {
                    val stat = Os.lstat(current.path)
                    if (OsConstants.S_ISLNK(stat.st_mode)) {
                        throw IOException("Node.js plugin explicit workspace input traverses a symbolic link.")
                    }
                } catch (error: ErrnoException) {
                    if (error.errno == OsConstants.ENOENT && allowMissingLeaf) return
                    throw error
                }
            }
        }

        private fun openedDescriptorPath(descriptor: java.io.FileDescriptor): String? = try {
            ParcelFileDescriptor.dup(descriptor).use { duplicate ->
                Os.readlink("/proc/self/fd/${duplicate.fd}")
                    .takeUnless { it.endsWith(" (deleted)") }
                    ?.let(::File)
                    ?.absolutePath
            }
        } catch (_: Exception) {
            null
        }

        private fun contains(root: File, candidate: File): Boolean {
            val rootPath = root.absolutePath.trimEnd(File.separatorChar)
            val candidatePath = candidate.absolutePath
            return candidatePath == rootPath || candidatePath.startsWith("$rootPath${File.separator}")
        }

        private fun relativePath(root: File, candidate: File): String {
            if (!contains(root, candidate)) {
                throw IOException("Node.js plugin workspace relative path escapes sandboxRoot.")
            }
            return candidate.absolutePath
                .removePrefix(root.absolutePath.trimEnd(File.separatorChar))
                .trimStart(File.separatorChar)
                .replace(File.separatorChar, '/')
        }

        private fun safeName(value: String): String = value
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(48)
            .ifBlank { "request" }

        private sealed interface ExplicitInput {
            data object Directory : ExplicitInput
            data class FileEntry(val file: File) : ExplicitInput
            data class Bytes(val value: ByteArray) : ExplicitInput
        }

        private data class OutputArchivePlan(val deletedFiles: Set<String>)
    }

    private data class DirectoryIdentity(val device: Long, val inode: Long) {

        fun matches(directory: File): Boolean = runCatching {
            val stat = Os.lstat(directory.path)
            OsConstants.S_ISDIR(stat.st_mode) && stat.st_dev == device && stat.st_ino == inode
        }.getOrDefault(false)

        companion object {
            fun capture(directory: File): DirectoryIdentity? = runCatching {
                val stat = Os.lstat(directory.path)
                if (!OsConstants.S_ISDIR(stat.st_mode)) null else DirectoryIdentity(stat.st_dev, stat.st_ino)
            }.getOrNull()
        }
    }
}
