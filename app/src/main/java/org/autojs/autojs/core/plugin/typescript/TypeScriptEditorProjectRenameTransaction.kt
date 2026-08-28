package org.autojs.autojs.core.plugin.typescript

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import org.autojs.plugin.editor.api.EditorPluginProjectRenameContract
import org.autojs.plugin.editor.api.EditorPluginProjectRenameFile
import org.autojs.plugin.editor.api.EditorPluginProjectRenameRequest
import org.autojs.plugin.editor.api.EditorPluginProjectSnapshotContract
import org.autojs.plugin.editor.api.EditorPluginTextEdit

/** Validates, stages and atomically publishes one contract-5 project rename transaction. */
internal class TypeScriptEditorProjectRenameTransaction(
    private val fileOperations: FileOperations = AndroidFileOperations,
) {

    class Prepared internal constructor(
        val request: EditorPluginProjectRenameRequest,
        val documentPath: String,
        val activeDocumentResultText: String,
        val fileCount: Int,
        val editCount: Int,
        internal val files: List<FilePlan>,
    )

    class Publication internal constructor(
        internal val entries: List<PublicationEntry>,
    ) {
        internal var terminal = false
    }

    internal data class FilePlan(
        val relativePath: String,
        val target: File,
        val baseBytes: ByteArray,
        val resultBytes: ByteArray,
        val baseSha256: String,
        val resultSha256: String,
        val resultText: String,
    )

    internal data class FileMetadata(
        val identity: String,
        val mode: Int,
        val linkCount: Long,
        val regularFile: Boolean,
    )

    internal interface FileOperations {
        fun metadata(file: File): FileMetadata
        fun sha256(file: File): String
        fun createTemporaryFile(parent: File, prefix: String): File
        fun writeAndSync(file: File, bytes: ByteArray, mode: Int)
        fun rename(source: File, target: File)
        fun delete(file: File)
        fun syncDirectory(directory: File)
    }

    @Throws(TypeScriptEditorProjectRenameException::class)
    fun prepare(
        documentPath: String,
        currentDocumentText: String,
        request: EditorPluginProjectRenameRequest,
    ): Prepared {
        if (!EditorPluginProjectRenameContract.isSupported(request)) {
            throw failure(
                TypeScriptEditorProjectRenameFailureReason.REJECTED,
                "Project rename request violates editor contract 5.",
            )
        }
        val snapshot = TypeScriptEditorProjectSnapshotProvider.captureRequired(documentPath)
            ?: throw stale("The TypeScript project snapshot is no longer available.")
        if (
            request.projectRootPath != snapshot.projectRootPath ||
            request.projectSourceInventoryFingerprint != snapshot.sourceInventoryFingerprint ||
            request.documentRelativePath != snapshot.documentRelativePath
        ) {
            throw stale("The TypeScript project source inventory changed before rename.")
        }

        val canonicalDocument = File(snapshot.documentPath).normalizedCanonicalFile()
        if (canonicalDocument.path != snapshot.documentPath || canonicalDocument.path != documentPath) {
            throw stale("The active TypeScript document identity changed before rename.")
        }
        val activeSource = snapshot.sourceFiles.firstOrNull { source ->
            source.relativePath == snapshot.documentRelativePath
        } ?: throw stale("The active TypeScript document left the source inventory.")
        val activeSourceHasBom = activeSource.text.startsWith(UTF8_BOM_CHARACTER) &&
            activeSource.text.drop(1) == currentDocumentText
        if (activeSource.text != currentDocumentText && !activeSourceHasBom) {
            throw failure(
                TypeScriptEditorProjectRenameFailureReason.UNSAVED_DOCUMENT,
                "The active document must be saved before a project rename.",
            )
        }

        val root = File(snapshot.projectRootPath).normalizedCanonicalFile()
        if (!root.isDirectory || root.path != snapshot.projectRootPath) {
            throw stale("The TypeScript project root identity changed before rename.")
        }
        val sourceByPath = snapshot.sourceFiles.associateBy { source -> source.relativePath }
        var resultingProjectBytes = snapshot.sourceByteLength
        val plans = request.files.map { file ->
            val source = sourceByPath[file.relativePath]
                ?: throw stale("A rename target left the TypeScript source inventory.")
            if (file.baseContentSha256 != source.sha256) {
                throw stale("A rename target changed before replay.")
            }
            val requestedTarget = File(root, file.relativePath.replace('/', File.separatorChar))
            val target = requestedTarget.normalizedCanonicalFile()
            if (
                !target.isFile ||
                !target.isInside(root) ||
                requestedTarget.absoluteFile.normalize().path != target.path
            ) {
                throw stale("A rename target is not a canonical project source file.")
            }
            val replayBaseText = if (source.relativePath == snapshot.documentRelativePath) {
                currentDocumentText
            } else {
                source.text
            }
            val resultText = replay(replayBaseText, request, file)
            val baseBytes = TypeScriptUtf8.encode(source.text, "Project rename base source")
            val resultBytes = TypeScriptUtf8.encode(
                if (source.relativePath == snapshot.documentRelativePath && activeSourceHasBom) {
                    UTF8_BOM_CHARACTER + resultText
                } else {
                    resultText
                },
                "Project rename result source",
            )
            if (
                resultBytes.size.toLong() >
                EditorPluginProjectSnapshotContract.MAX_SINGLE_SOURCE_BYTES
            ) {
                throw failure(
                    TypeScriptEditorProjectRenameFailureReason.REJECTED,
                    "A project rename result exceeds the source-file budget.",
                )
            }
            val resultHash = sha256(resultBytes)
            if (resultHash != file.resultContentSha256) {
                throw failure(
                    TypeScriptEditorProjectRenameFailureReason.REJECTED,
                    "A project rename result hash does not match replayed edits.",
                )
            }
            resultingProjectBytes = Math.addExact(
                Math.subtractExact(resultingProjectBytes, source.utf8ByteLength),
                resultBytes.size.toLong(),
            )
            FilePlan(
                relativePath = file.relativePath,
                target = target,
                baseBytes = baseBytes,
                resultBytes = resultBytes,
                baseSha256 = source.sha256,
                resultSha256 = resultHash,
                resultText = resultText,
            )
        }
        if (resultingProjectBytes !in 0..EditorPluginProjectSnapshotContract.MAX_SOURCE_BYTES) {
            throw failure(
                TypeScriptEditorProjectRenameFailureReason.REJECTED,
                "The project rename result exceeds the source-inventory budget.",
            )
        }
        val activePlan = plans.singleOrNull { plan ->
            plan.relativePath == snapshot.documentRelativePath
        } ?: throw failure(
            TypeScriptEditorProjectRenameFailureReason.REJECTED,
            "The project rename does not contain exactly one active document.",
        )
        return Prepared(
            request = request,
            documentPath = canonicalDocument.path,
            activeDocumentResultText = activePlan.resultText,
            fileCount = plans.size,
            editCount = request.files.sumOf { file -> file.edits.size },
            files = plans,
        )
    }

    @Throws(TypeScriptEditorProjectRenameException::class)
    fun publish(prepared: Prepared): Publication {
        val freshSnapshot = TypeScriptEditorProjectSnapshotProvider.captureRequired(
            prepared.documentPath,
        ) ?: throw stale("The TypeScript project snapshot disappeared before publication.")
        if (
            freshSnapshot.projectRootPath != prepared.request.projectRootPath ||
            freshSnapshot.sourceInventoryFingerprint !=
            prepared.request.projectSourceInventoryFingerprint ||
            freshSnapshot.documentRelativePath != prepared.request.documentRelativePath
        ) {
            throw stale("The TypeScript project source inventory changed before publication.")
        }

        val entries = ArrayList<PublicationEntry>(prepared.files.size)
        try {
            prepared.files.forEach { plan ->
                val metadata = fileOperations.metadata(plan.target)
                if (!metadata.regularFile || metadata.linkCount > 1L) {
                    throw IOException("Project rename target is not an independent regular file: ${plan.target}")
                }
                if (fileOperations.sha256(plan.target) != plan.baseSha256) {
                    throw stale("A project rename target changed before staging.")
                }
                val parent = plan.target.parentFile
                    ?: throw IOException("Project rename target has no parent: ${plan.target}")
                val backup = fileOperations.createTemporaryFile(parent, BACKUP_PREFIX)
                val staged = fileOperations.createTemporaryFile(parent, STAGED_PREFIX)
                val entry = PublicationEntry(plan, metadata, backup, staged)
                entries += entry
                fileOperations.writeAndSync(backup, plan.baseBytes, metadata.mode)
                fileOperations.writeAndSync(staged, plan.resultBytes, metadata.mode)
                if (
                    fileOperations.sha256(backup) != plan.baseSha256 ||
                    fileOperations.sha256(staged) != plan.resultSha256
                ) {
                    throw IOException("Project rename staging verification failed: ${plan.target}")
                }
            }

            entries.forEach { entry ->
                verifyUnchanged(entry)
                try {
                    fileOperations.rename(entry.staged, entry.plan.target)
                } catch (error: Throwable) {
                    entry.published = runCatching {
                        fileOperations.sha256(entry.plan.target) == entry.plan.resultSha256
                    }.getOrDefault(false)
                    throw error
                }
                entry.published = true
                if (fileOperations.sha256(entry.plan.target) != entry.plan.resultSha256) {
                    throw IOException("Project rename publication verification failed: ${entry.plan.target}")
                }
                fileOperations.syncDirectory(requireNotNull(entry.plan.target.parentFile))
            }
            return Publication(entries)
        } catch (error: Throwable) {
            val rollbackError = rollbackEntries(entries)
            if (rollbackError == null) {
                cleanup(entries)
                throw asTransactionFailure(error)
            }
            val recoveryFiles = entries.mapNotNull { entry ->
                entry.backup.takeIf(File::exists)
            }
            throw TypeScriptEditorProjectRenameException(
                reason = TypeScriptEditorProjectRenameFailureReason.ROLLBACK_FAILED,
                message = "Project rename failed and one or more source files could not be restored.",
                recoveryFiles = recoveryFiles,
                cause = rollbackError.apply { addSuppressed(error) },
            )
        }
    }

    @Throws(TypeScriptEditorProjectRenameException::class)
    fun rollback(publication: Publication) {
        synchronized(publication) {
            check(!publication.terminal) { "Project rename publication is already terminal." }
            val rollbackError = rollbackEntries(publication.entries)
            if (rollbackError != null) {
                val recoveryFiles = publication.entries.mapNotNull { entry ->
                    entry.backup.takeIf(File::exists)
                }
                throw TypeScriptEditorProjectRenameException(
                    reason = TypeScriptEditorProjectRenameFailureReason.ROLLBACK_FAILED,
                    message = "Project rename could not restore every published source file.",
                    recoveryFiles = recoveryFiles,
                    cause = rollbackError,
                )
            }
            cleanup(publication.entries)
            publication.terminal = true
        }
    }

    fun complete(publication: Publication) {
        synchronized(publication) {
            check(!publication.terminal) { "Project rename publication is already terminal." }
            cleanup(publication.entries)
            publication.terminal = true
        }
    }

    private fun replay(
        baseText: String,
        request: EditorPluginProjectRenameRequest,
        file: EditorPluginProjectRenameFile,
    ): String {
        if (baseText.length > EditorPluginProjectRenameContract.MAX_DOCUMENT_UTF16_LENGTH) {
            throw failure(
                TypeScriptEditorProjectRenameFailureReason.REJECTED,
                "A project rename base source exceeds the UTF-16 budget.",
            )
        }
        file.edits.forEach { edit ->
            if (
                edit.endOffset > baseText.length ||
                !isUtf16Boundary(baseText, edit.startOffset) ||
                !isUtf16Boundary(baseText, edit.endOffset) ||
                !baseText.regionMatches(
                    thisOffset = edit.startOffset,
                    other = request.symbolName,
                    otherOffset = 0,
                    length = request.symbolName.length,
                )
            ) {
                throw failure(
                    TypeScriptEditorProjectRenameFailureReason.REJECTED,
                    "A project rename edit does not match the authorized symbol.",
                )
            }
        }
        return StringBuilder(baseText).apply {
            file.edits.asReversed().forEach { edit ->
                replace(edit.startOffset, edit.endOffset, request.newName)
            }
        }.toString()
    }

    private fun verifyUnchanged(entry: PublicationEntry) {
        if (fileOperations.sha256(entry.plan.target) != entry.plan.baseSha256) {
            throw stale("A project rename target changed before atomic publication.")
        }
        val current = fileOperations.metadata(entry.plan.target)
        if (
            current.identity != entry.metadata.identity ||
            current.mode != entry.metadata.mode ||
            current.linkCount != entry.metadata.linkCount ||
            !current.regularFile
        ) {
            throw stale("Project rename target metadata changed before atomic publication.")
        }
    }

    private fun rollbackEntries(entries: List<PublicationEntry>): Throwable? {
        var firstFailure: Throwable? = null
        entries.asReversed().forEach { entry ->
            if (!entry.published) return@forEach
            runCatching {
                if (fileOperations.sha256(entry.plan.target) != entry.plan.resultSha256) {
                    throw IOException(
                        "Published rename target changed before rollback: ${entry.plan.target}",
                    )
                }
                fileOperations.rename(entry.backup, entry.plan.target)
                if (fileOperations.sha256(entry.plan.target) != entry.plan.baseSha256) {
                    throw IOException("Project rename rollback verification failed: ${entry.plan.target}")
                }
                entry.published = false
                fileOperations.syncDirectory(requireNotNull(entry.plan.target.parentFile))
            }.onFailure { error ->
                firstFailure = firstFailure?.apply { addSuppressed(error) } ?: error
            }
        }
        return firstFailure
    }

    private fun cleanup(entries: List<PublicationEntry>) {
        entries.forEach { entry ->
            runCatching { fileOperations.delete(entry.staged) }
            runCatching { fileOperations.delete(entry.backup) }
            entry.plan.target.parentFile?.let { parent ->
                runCatching { fileOperations.syncDirectory(parent) }
            }
        }
    }

    private fun asTransactionFailure(error: Throwable): TypeScriptEditorProjectRenameException =
        error as? TypeScriptEditorProjectRenameException
            ?: TypeScriptEditorProjectRenameException(
                reason = TypeScriptEditorProjectRenameFailureReason.FAILED,
                message = "Project rename transaction failed.",
                cause = error,
            )

    private fun stale(message: String) = failure(
        TypeScriptEditorProjectRenameFailureReason.STALE,
        message,
    )

    private fun failure(
        reason: TypeScriptEditorProjectRenameFailureReason,
        message: String,
    ) = TypeScriptEditorProjectRenameException(reason, message)

    private fun isUtf16Boundary(text: String, offset: Int): Boolean =
        offset in 0..text.length &&
            !(offset > 0 && offset < text.length &&
                Character.isHighSurrogate(text[offset - 1]) &&
                Character.isLowSurrogate(text[offset]))

    private fun File.isInside(root: File): Boolean = toPath().startsWith(root.toPath())

    private fun File.normalizedCanonicalFile(): File =
        runCatching { canonicalFile }.getOrElse { absoluteFile.normalize() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
    }

    internal data class PublicationEntry(
        val plan: FilePlan,
        val metadata: FileMetadata,
        val backup: File,
        val staged: File,
        var published: Boolean = false,
    )

    private object AndroidFileOperations : FileOperations {
        override fun metadata(file: File): FileMetadata {
            val stat = Os.stat(file.absolutePath)
            return FileMetadata(
                identity = "${stat.st_dev}:${stat.st_ino}",
                mode = stat.st_mode,
                linkCount = stat.st_nlink,
                regularFile = OsConstants.S_ISREG(stat.st_mode),
            )
        }

        override fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            buffer.fill(0)
            return digest.digest().toLowerHex()
        }

        override fun createTemporaryFile(parent: File, prefix: String): File =
            File.createTempFile(prefix, ".tmp", parent)

        override fun writeAndSync(file: File, bytes: ByteArray, mode: Int) {
            FileOutputStream(file).use { output ->
                output.write(bytes)
                output.flush()
                Os.fchmod(output.fd, mode and 0xFFF)
                output.fd.sync()
            }
        }

        override fun rename(source: File, target: File) {
            Os.rename(source.absolutePath, target.absolutePath)
        }

        override fun delete(file: File) {
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot delete project rename temporary file: $file")
            }
        }

        override fun syncDirectory(directory: File) {
            val descriptor = runCatching {
                Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
            }.getOrNull() ?: return
            try {
                runCatching { Os.fsync(descriptor) }
            } finally {
                runCatching { Os.close(descriptor) }
            }
        }
    }

    private companion object {
        const val BACKUP_PREFIX = ".autojs6-rename-backup-"
        const val STAGED_PREFIX = ".autojs6-rename-stage-"
        const val UTF8_BOM_CHARACTER = '\uFEFF'
    }
}

internal enum class TypeScriptEditorProjectRenameFailureReason {
    REJECTED,
    STALE,
    UNSAVED_DOCUMENT,
    FAILED,
    ROLLBACK_FAILED,
}

internal class TypeScriptEditorProjectRenameException(
    val reason: TypeScriptEditorProjectRenameFailureReason,
    message: String,
    val recoveryFiles: List<File> = emptyList(),
    cause: Throwable? = null,
) : IOException(message, cause)
