package org.autojs.autojs.core.plugin.python

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectLaunchPolicy
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractException
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Immutable host-private payloads which stay alive until Binder admission has copied every PFD. */
internal class PythonPrivateExecutionSnapshot private constructor(
    private val source: PrivatePayload,
    private val workspace: PrivatePayload?,
    val workspaceEntryCount: Int?,
    val workspaceUncompressedBytes: Long?,
    private val stdin: PrivatePayload?,
    private val hostCapabilities: PrivatePayload?,
    val entryPoint: String,
    val entryMode: PythonEntryMode,
) : Closeable {

    init {
        require(
            (workspace == null) ==
                (workspaceEntryCount == null && workspaceUncompressedBytes == null),
        ) { "Workspace metrics must be present exactly when a workspace payload is present" }
        require(workspaceEntryCount == null || workspaceEntryCount >= 0) {
            "Workspace entry count must not be negative"
        }
        require(workspaceUncompressedBytes == null || workspaceUncompressedBytes >= 0L) {
            "Workspace uncompressed size must not be negative"
        }
    }

    val sourceLengthBytes: Long
        get() = source.lengthBytes

    val sourceSha256: PythonSha256
        get() = source.sha256

    val workspaceLengthBytes: Long?
        get() = workspace?.lengthBytes

    val workspaceSha256: PythonSha256?
        get() = workspace?.sha256

    val stdinLengthBytes: Long?
        get() = stdin?.lengthBytes

    val stdinSha256: PythonSha256?
        get() = stdin?.sha256

    val hostCapabilityLengthBytes: Long?
        get() = hostCapabilities?.lengthBytes

    val hostCapabilitySha256: PythonSha256?
        get() = hostCapabilities?.sha256

    val descriptorLayout = PythonRuntimeDescriptorLayout.create(
        hasWorkspace = workspace != null,
        hasStdin = stdin != null,
        hasHostCapabilities = hostCapabilities != null,
    )

    val descriptorCount: Int
        get() = descriptorLayout.descriptorCount

    val hasWorkspace: Boolean
        get() = workspace != null

    val hasStdin: Boolean
        get() = stdin != null

    fun openReadOnlyDescriptors(): Array<ParcelFileDescriptor> {
        val opened = ArrayList<ParcelFileDescriptor>(descriptorCount)
        return try {
            opened += source.openReadOnly()
            workspace?.let { opened += it.openReadOnly() }
            stdin?.let { opened += it.openReadOnly() }
            hostCapabilities?.let { opened += it.openReadOnly() }
            opened.toTypedArray()
        } catch (error: Throwable) {
            opened.forEach { descriptor -> runCatching { descriptor.close() } }
            throw error
        }
    }

    override fun close() {
        hostCapabilities?.close()
        stdin?.close()
        workspace?.close()
        source.close()
    }

    companion object {
        fun create(
            context: Context,
            source: PythonFileSource,
            requestId: org.autojs.plugin.python.runtime.api.PythonRequestId,
            entryMode: PythonEntryMode,
            stdinSnapshot: ByteArray?,
            cancelled: () -> Boolean,
        ): PythonPrivateExecutionSnapshot {
            val requestEntryPoint = resolveRequestEntryPoint(source, entryMode)
            val base = when (source) {
                is PythonProjectSource -> createProject(context, source, cancelled)
                else -> createSingleFile(context, source, cancelled)
            }
            val stdinPayload = try {
                stdinSnapshot?.let { bytes ->
                    val stdinFile = privateTempFile(
                        privateSnapshotDirectory(context),
                        "stdin-",
                        ".bin",
                    )
                    try {
                        snapshotStdinBytes(bytes, stdinFile, cancelled)
                    } catch (error: Throwable) {
                        stdinFile.delete()
                        throw error
                    }
                }
            } catch (error: Throwable) {
                base.close()
                throw mapStdinSnapshotFailure(error)
            }
            val capabilityFile = try {
                privateTempFile(
                    privateSnapshotDirectory(context),
                    "host-capabilities-",
                    ".json",
                )
            } catch (error: Throwable) {
                stdinPayload?.close()
                base.close()
                if (error is InterruptedException || error is PythonRuntimeHostException) throw error
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.HOST_CAPABILITY_SNAPSHOT_INVALID,
                    "Python host capability snapshot could not be created",
                    error,
                )
            }
            return try {
                val capabilityBytes = PythonHostCapabilitySnapshot.create(
                    context,
                    requestId,
                    source,
                    requestEntryPoint,
                )
                val capability = snapshotBytes(capabilityBytes, capabilityFile)
                PythonPrivateExecutionSnapshot(
                    source = base.source,
                    workspace = base.workspace,
                    workspaceEntryCount = base.workspaceEntryCount,
                    workspaceUncompressedBytes = base.workspaceUncompressedBytes,
                    stdin = stdinPayload,
                    hostCapabilities = capability,
                    entryPoint = requestEntryPoint,
                    entryMode = entryMode,
                )
            } catch (error: Throwable) {
                capabilityFile.delete()
                stdinPayload?.close()
                base.close()
                if (error is InterruptedException || error is PythonRuntimeHostException) throw error
                throw PythonRuntimeHostException(
                    PythonRuntimeHostError.HOST_CAPABILITY_SNAPSHOT_INVALID,
                    "Python host capability snapshot could not be created",
                    error,
                )
            }
        }

        private fun createSingleFile(
            context: Context,
            source: PythonFileSource,
            cancelled: () -> Boolean,
        ): PythonPrivateExecutionSnapshot {
            val canonicalSource = canonicalRegularPythonSource(source)
            val semanticSource = source.semanticFile.canonicalFile
            val directory = privateSnapshotDirectory(context)
            val snapshot = privateTempFile(directory, "source-", ".py")
            return try {
                val payload = snapshotRegularFile(
                    source = canonicalSource,
                    destination = snapshot,
                    maximumBytes = PythonRuntimeContract.MAX_SOURCE_BYTES,
                    cancelled = cancelled,
                )
                PythonPrivateExecutionSnapshot(
                    source = payload,
                    workspace = null,
                    workspaceEntryCount = null,
                    workspaceUncompressedBytes = null,
                    stdin = null,
                    hostCapabilities = null,
                    entryPoint = semanticSource.name,
                    entryMode = PythonEntryMode.FILE,
                )
            } catch (error: Throwable) {
                snapshot.delete()
                throw mapSnapshotFailure(error, workspace = false)
            }
        }

        private fun createProject(
            context: Context,
            source: PythonProjectSource,
            cancelled: () -> Boolean,
        ): PythonPrivateExecutionSnapshot {
            val plan = try {
                revalidateProjectDeclaration(source)
                PythonProjectWorkspacePolicy.plan(source)
            } catch (error: Throwable) {
                throw mapSnapshotFailure(error, workspace = true)
            }
            val directory = privateSnapshotDirectory(context)
            val sourceFile = privateTempFile(directory, "source-", ".py")
            val workspaceFile = privateTempFile(directory, "workspace-", ".zip")
            try {
                val sourceInput = PythonProjectWorkspacePolicy.planSource(source)
                val sourceDigest = MessageDigest.getInstance("SHA-256")
                var sourceLength = -1L
                val workspaceDigest = MessageDigest.getInstance("SHA-256")
                var expandedBytes = 0L
                var archiveEntries = 0

                FileOutputStream(sourceFile, false).use { sourceOutput ->
                    val boundedArchive = BoundedOutputStream(
                        FileOutputStream(workspaceFile, false),
                        PythonProjectWorkspacePolicy.MAX_ARCHIVE_BYTES,
                    )
                    DigestOutputStream(boundedArchive, workspaceDigest).use { digestedArchive ->
                        ZipOutputStream(BufferedOutputStream(digestedArchive)).use { zip ->
                            plan.files.forEach { planned ->
                                if (cancelled()) throw InterruptedException("Python project snapshot was cancelled")
                                if (planned.entryPoint) {
                                    check(sourceLength < 0L) { "Python project contains multiple entry-point files" }
                                    sourceLength = copyRegularFile(
                                        sourceInput,
                                        sourceOutput,
                                        sourceDigest,
                                        PythonRuntimeContract.MAX_SOURCE_BYTES,
                                        cancelled,
                                    )
                                } else {
                                    archiveEntries++
                                    check(archiveEntries <= PythonProjectWorkspacePolicy.MAX_ENTRIES) {
                                        "Python workspace exceeds the entry-count limit"
                                    }
                                    zip.putNextEntry(ZipEntry(planned.relativePath).apply { time = 0L })
                                    try {
                                        val remaining = PythonProjectWorkspacePolicy.MAX_UNCOMPRESSED_BYTES - expandedBytes
                                        val copied = copyRegularFile(
                                            planned,
                                            zip,
                                            digest = null,
                                            maximumBytes = remaining,
                                            cancelled = cancelled,
                                        )
                                        expandedBytes = Math.addExact(expandedBytes, copied)
                                    } finally {
                                        zip.closeEntry()
                                    }
                                }
                            }
                        }
                    }
                    check(sourceLength >= 0L) { "Python project entry point was not captured" }
                    sourceOutput.flush()
                    sourceOutput.fd.sync()
                }
                workspaceFile.setWritable(false, false)
                workspaceFile.setReadable(true, true)
                sourceFile.setWritable(false, false)
                sourceFile.setReadable(true, true)
                revalidateProjectDeclaration(source)
                return PythonPrivateExecutionSnapshot(
                    source = PrivatePayload(
                        sourceFile,
                        sourceLength,
                        PythonSha256.fromBytes(sourceDigest.digest()),
                    ),
                    workspace = PrivatePayload(
                        workspaceFile,
                        workspaceFile.length(),
                        PythonSha256.fromBytes(workspaceDigest.digest()),
                    ),
                    workspaceEntryCount = archiveEntries,
                    workspaceUncompressedBytes = expandedBytes,
                    stdin = null,
                    hostCapabilities = null,
                    entryPoint = source.entryPoint,
                    entryMode = PythonEntryMode.FILE,
                )
            } catch (error: Throwable) {
                sourceFile.delete()
                workspaceFile.delete()
                throw mapSnapshotFailure(error, workspace = true)
            }
        }

        private fun revalidateProjectDeclaration(source: PythonProjectSource) {
            val admission = PythonProjectLaunchPolicy.inspect(
                source.projectRoot,
                source.projectEntryFile,
            ) ?: error("Python project declaration disappeared before snapshot")
            check(
                admission.projectRoot == source.projectRoot &&
                    admission.entryFile == source.projectEntryFile &&
                    admission.timeoutMillis == source.timeoutMillis &&
                    admission.entryMode == source.entryMode &&
                    admission.stdin == source.stdin,
            ) { "Python project declaration changed before snapshot" }
        }

        private fun snapshotRegularFile(
            source: File,
            destination: File,
            maximumBytes: Long,
            cancelled: () -> Boolean,
        ): PrivatePayload {
            val digest = MessageDigest.getInstance("SHA-256")
            val length = FileOutputStream(destination, false).use { output ->
                val planned = PythonProjectWorkspacePolicy.PlannedFile(
                    file = source,
                    canonicalFile = source,
                    relativePath = source.name,
                    entryPoint = true,
                )
                val copied = copyRegularFile(planned, output, digest, maximumBytes, cancelled)
                output.flush()
                output.fd.sync()
                copied
            }
            destination.setWritable(false, false)
            destination.setReadable(true, true)
            return PrivatePayload(destination, length, PythonSha256.fromBytes(digest.digest()))
        }

        private fun snapshotBytes(value: ByteArray, destination: File): PrivatePayload {
            require(
                value.isNotEmpty() &&
                    value.size.toLong() <= PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES,
            ) {
                "Host capability snapshot exceeds the protocol limit"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(destination, false).use { output ->
                output.write(value)
                digest.update(value)
                output.flush()
                output.fd.sync()
            }
            destination.setWritable(false, false)
            destination.setReadable(true, true)
            return PrivatePayload(destination, value.size.toLong(), PythonSha256.fromBytes(digest.digest()))
        }

        private fun resolveRequestEntryPoint(source: PythonFileSource, entryMode: PythonEntryMode): String =
            when (entryMode) {
                PythonEntryMode.FILE -> when (source) {
                    is PythonProjectSource -> source.entryPoint
                    else -> source.semanticFile.name
                }
                PythonEntryMode.MODULE -> {
                    val project = source as? PythonProjectSource
                        ?: throw PythonRuntimeHostException(
                            PythonRuntimeHostError.ENTRY_MODE_INVALID,
                            "Python module entry requires an explicitly admitted project",
                        )
                    try {
                        PythonRuntimeValidation.moduleNameForFileEntryPoint(project.entryPoint)
                    } catch (error: PythonRuntimeContractException) {
                        throw PythonRuntimeHostException(
                            PythonRuntimeHostError.ENTRY_MODE_INVALID,
                            "Python project entry point cannot be represented as a module name",
                            error,
                        )
                    }
                }
            }

        private fun snapshotStdinBytes(
            value: ByteArray,
            destination: File,
            cancelled: () -> Boolean,
        ): PrivatePayload {
            require(value.size.toLong() <= PythonRuntimeContract.MAX_STDIN_BYTES) {
                "Python stdin snapshot exceeds the protocol limit"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(destination, false).use { output ->
                var offset = 0
                while (offset < value.size) {
                    if (cancelled()) throw InterruptedException("Python stdin snapshot was cancelled")
                    val count = minOf(COPY_BUFFER_BYTES, value.size - offset)
                    output.write(value, offset, count)
                    digest.update(value, offset, count)
                    offset += count
                }
                if (cancelled()) throw InterruptedException("Python stdin snapshot was cancelled")
                output.flush()
                output.fd.sync()
            }
            destination.setWritable(false, false)
            destination.setReadable(true, true)
            return PrivatePayload(destination, value.size.toLong(), PythonSha256.fromBytes(digest.digest()))
        }

        private fun copyRegularFile(
            planned: PythonProjectWorkspacePolicy.PlannedFile,
            output: OutputStream,
            digest: MessageDigest?,
            maximumBytes: Long,
            cancelled: () -> Boolean,
        ): Long {
            check(maximumBytes >= 0L) { "Python snapshot exceeds its byte limit" }
            PythonProjectWorkspacePolicy.revalidate(planned)
            val before = Os.lstat(planned.file.path)
            check(OsConstants.S_ISREG(before.st_mode) && !OsConstants.S_ISLNK(before.st_mode)) {
                "Python snapshot input is not a regular file"
            }
            val descriptor = Os.open(
                planned.file.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
            var total = 0L
            try {
                FileInputStream(descriptor).use { input ->
                    val opened = Os.fstat(input.fd)
                    check(
                        OsConstants.S_ISREG(opened.st_mode) &&
                            opened.st_dev == before.st_dev &&
                            opened.st_ino == before.st_ino,
                    ) { "Python snapshot input changed while it was opened" }
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        if (cancelled()) throw InterruptedException("Python snapshot was cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total = Math.addExact(total, count.toLong())
                        check(total <= maximumBytes) { "Python snapshot exceeds its byte limit" }
                        output.write(buffer, 0, count)
                        digest?.update(buffer, 0, count)
                    }
                }
            } catch (error: Throwable) {
                runCatching { Os.close(descriptor) }
                throw error
            }
            val after = Os.lstat(planned.file.path)
            check(
                OsConstants.S_ISREG(after.st_mode) &&
                    after.st_dev == before.st_dev &&
                    after.st_ino == before.st_ino,
            ) { "Python snapshot input changed during capture" }
            PythonProjectWorkspacePolicy.revalidate(planned)
            return total
        }

        private fun canonicalRegularPythonSource(source: PythonFileSource): File {
            val semantic = source.semanticFile.canonicalFile
            val canonical = source.file.canonicalFile
            check(semantic.isFile && semantic.name.endsWith(".py", ignoreCase = true)) {
                "Python preview requires one readable local .py semantic source"
            }
            check(canonical.isFile) {
                "Python preview requires one readable local .py file"
            }
            check(canonical.length() <= PythonRuntimeContract.MAX_SOURCE_BYTES) {
                "Python source exceeds the protocol limit"
            }
            return canonical
        }

        private fun privateSnapshotDirectory(context: Context): File {
            val directory = File(context.noBackupFilesDir, SNAPSHOT_DIRECTORY).canonicalFile
            check((directory.isDirectory || directory.mkdirs()) && directory.isDirectory) {
                "Python private snapshot directory is unavailable"
            }
            return directory
        }

        private fun privateTempFile(directory: File, prefix: String, suffix: String): File =
            File.createTempFile(prefix, suffix, directory).also { file ->
                file.setReadable(false, false)
                file.setWritable(false, false)
                file.setReadable(true, true)
                file.setWritable(true, true)
            }

        private fun mapSnapshotFailure(error: Throwable, workspace: Boolean): Throwable {
            if (error is InterruptedException || error is PythonRuntimeHostException) return error
            val hostError = if (workspace) {
                PythonRuntimeHostError.WORKSPACE_INVALID
            } else {
                PythonRuntimeHostError.SOURCE_INVALID
            }
            val label = if (workspace) "Python project workspace" else "Python source"
            return PythonRuntimeHostException(hostError, "$label could not be snapshotted", error)
        }

        private fun mapStdinSnapshotFailure(error: Throwable): Throwable {
            if (error is InterruptedException || error is PythonRuntimeHostException) return error
            return PythonRuntimeHostException(
                PythonRuntimeHostError.STDIN_INVALID,
                "Python stdin could not be snapshotted",
                error,
            )
        }

        private const val SNAPSHOT_DIRECTORY = "python-runtime-preview"
        private const val COPY_BUFFER_BYTES = 32 * 1024
    }

    private class PrivatePayload(
        private val file: File,
        val lengthBytes: Long,
        val sha256: PythonSha256,
    ) : Closeable {
        fun openReadOnly(): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun close() {
            if (file.exists() && !file.delete()) file.deleteOnExit()
        }
    }
}

/** Pure traversal policy; byte capture revalidates every planned file with lstat/open/fstat. */
internal object PythonProjectWorkspacePolicy {
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_ENTRIES = 8_192
    const val MAX_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L
    private const val MAX_PATH_BYTES = 4 * 1024

    data class PlannedFile(
        val file: File,
        val canonicalFile: File,
        val relativePath: String,
        val entryPoint: Boolean,
    )

    data class Plan(val files: List<PlannedFile>)

    fun plan(source: PythonProjectSource): Plan {
        val root = source.projectRoot.canonicalFile
        val entry = source.projectEntryFile.canonicalFile
        check(root == source.projectRoot && root.isDirectory) { "Python project root is not canonical" }
        check(entry == source.projectEntryFile && contains(root, entry)) {
            "Python project entry point is not canonical"
        }
        check(
            !source.entryPoint.substringBefore('/').equals(
                PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME,
                ignoreCase = true,
            ),
        ) { "Python project entry point uses the reserved result directory" }
        val planned = ArrayList<PlannedFile>()
        val paths = NodePathSet()
        var entrySeen = false
        var visitedDirectories = 0
        var plannedWorkspaceFiles = 0

        fun visit(directory: File, depth: Int) {
            check(depth <= MAX_DEPTH) { "Python workspace exceeds the directory-depth limit" }
            revalidateNode(root, directory, directory.canonicalFile, expectDirectory = true)
            val children = directory.listFiles()
                ?: error("Python project directory could not be enumerated")
            children.sortedBy { it.name }.forEach { child ->
                if (
                    depth == 0 &&
                    child.name.equals(PythonRuntimeResultPresenter.OUTPUT_DIRECTORY_NAME, ignoreCase = true)
                ) {
                    return@forEach
                }
                val canonical = child.canonicalFile
                check(child.absoluteFile.path == canonical.path) {
                    "Python project contains a symbolic-link or non-canonical path"
                }
                check(contains(root, canonical)) { "Python project path escapes the project root" }
                val relative = relativePath(root, canonical)
                validateRelativePath(relative)
                when {
                    canonical.isDirectory -> {
                        visitedDirectories++
                        check(visitedDirectories <= MAX_ENTRIES) {
                            "Python workspace exceeds the directory-count limit"
                        }
                        paths.add(relative, directory = true)
                        visit(canonical, depth + 1)
                    }
                    canonical.isFile -> {
                        paths.add(relative, directory = false)
                        val isEntry = canonical == entry
                        entrySeen = entrySeen || isEntry
                        if (!isEntry) {
                            check(plannedWorkspaceFiles < MAX_ENTRIES) {
                                "Python workspace exceeds the entry-count limit"
                            }
                            plannedWorkspaceFiles++
                        }
                        planned += PlannedFile(canonical, canonical, relative, isEntry)
                    }
                    else -> error("Python project contains a special filesystem entry")
                }
            }
        }

        visit(root, 0)
        check(entrySeen) { "Python project entry point disappeared during traversal" }
        check(planned.single { it.entryPoint }.relativePath == source.entryPoint) {
            "Python project entry point changed during traversal"
        }
        return Plan(planned.sortedBy(PlannedFile::relativePath))
    }

    fun planSource(source: PythonProjectSource): PlannedFile {
        val sourceFile = source.file.canonicalFile
        check(sourceFile == source.file && sourceFile.isFile) {
            "Python project source snapshot is not a canonical regular file"
        }
        return PlannedFile(
            file = sourceFile,
            canonicalFile = sourceFile,
            relativePath = source.entryPoint,
            entryPoint = true,
        )
    }

    fun validateArchivePaths(paths: Collection<String>) {
        val seen = NodePathSet()
        paths.forEach { path ->
            validateRelativePath(path)
            seen.add(path, directory = false)
        }
    }

    fun revalidate(planned: PlannedFile) {
        validateRelativePath(planned.relativePath)
        check(planned.file.absoluteFile.path == planned.canonicalFile.path) {
            "Python project path changed before snapshot"
        }
        check(planned.file.canonicalFile == planned.canonicalFile) {
            "Python project path escaped through a symbolic link"
        }
    }

    private fun revalidateNode(root: File, lexical: File, canonical: File, expectDirectory: Boolean) {
        check(lexical.absoluteFile.path == canonical.path && contains(root, canonical)) {
            "Python project path is non-canonical or escapes the root"
        }
        check(if (expectDirectory) canonical.isDirectory else canonical.isFile) {
            "Python project path has an unsupported filesystem type"
        }
    }

    private fun validateRelativePath(value: String) {
        check(value.isNotBlank() && value.toByteArray(StandardCharsets.UTF_8).size <= MAX_PATH_BYTES) {
            "Python workspace path is blank or too long"
        }
        check(
            !value.startsWith('/') &&
                !WINDOWS_DRIVE_PREFIX.matches(value) &&
                '\\' !in value &&
                value.none(Character::isISOControl),
        ) { "Python workspace path is not a normalized relative path" }
        check(Normalizer.normalize(value, Normalizer.Form.NFC) == value) {
            "Python workspace path is not Unicode NFC"
        }
        check(value.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Python workspace path contains an unsafe segment"
        }
    }

    private fun contains(root: File, candidate: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar)
        val candidatePath = candidate.path
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun relativePath(root: File, candidate: File): String = candidate.path
        .removePrefix(root.path.trimEnd(File.separatorChar))
        .trimStart(File.separatorChar)
        .replace(File.separatorChar, '/')

    private class NodePathSet {
        private val exact = LinkedHashMap<String, Boolean>()
        private val folded = LinkedHashMap<String, String>()

        fun add(path: String, directory: Boolean) {
            check(exact.putIfAbsent(path, directory) == null) { "Python workspace contains a duplicate path" }
            val foldedPath = path.uppercase(Locale.ROOT)
            val previousFolded = folded.putIfAbsent(foldedPath, path)
            check(previousFolded == null || previousFolded == path) {
                "Python workspace contains a case-folding path collision"
            }
            val segments = path.split('/')
            for (index in 1 until segments.size) {
                val parent = segments.take(index).joinToString("/")
                check(exact[parent] != false) { "Python workspace contains a file/directory prefix conflict" }
                val foldedParent = parent.uppercase(Locale.ROOT)
                val caseParent = folded[foldedParent]
                check(caseParent == null || exact[caseParent] != false) {
                    "Python workspace contains a case-folding prefix conflict"
                }
            }
            if (!directory) {
                val prefix = "$path/"
                val foldedPrefix = "$foldedPath/"
                check(exact.keys.none { it.startsWith(prefix) }) {
                    "Python workspace contains a file/directory prefix conflict"
                }
                check(folded.keys.none { it.startsWith(foldedPrefix) }) {
                    "Python workspace contains a case-folding prefix conflict"
                }
            }
        }
    }

    private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:.*")
    private const val MAX_DEPTH = 64
}

private class BoundedOutputStream(
    output: OutputStream,
    private val maximumBytes: Long,
) : FilterOutputStream(output) {
    private var count = 0L

    override fun write(value: Int) {
        reserve(1)
        out.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        reserve(length)
        out.write(buffer, offset, length)
    }

    private fun reserve(length: Int) {
        count = Math.addExact(count, length.toLong())
        check(count <= maximumBytes) { "Python workspace exceeds the compressed-size limit" }
    }
}
