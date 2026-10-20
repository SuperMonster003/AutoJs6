package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.view.View
import androidx.annotation.RequiresApi
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.storage.file.EmergencyDraftDirectoryGuard
import org.autojs.autojs.storage.file.StableDraftFileHelper
import org.autojs.autojs.storage.history.HistoryPaths
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingCodec
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingProbe
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadMetadata
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadPipeline
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadResult
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.explorer.PrivilegedBackupUi
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(Locale.US, it) }

private fun sha256(file: File): ByteArray = file.inputStream().use(::sha256)

private fun sha256(input: InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) {
            val oneByte = input.read()
            if (oneByte < 0) break
            digest.update(oneByte.toByte())
        } else {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest()
}

class EditorFileRollbackFailedException(
    val originalRecoveryFile: File?,
    cause: Throwable,
) : IOException("The file could not be restored after a failed write", cause)

class EditorFileService(
    private val context: Context,
    private val hostView: View,
    private val versionHistoryService: EditorVersionHistoryService,
    private val editorSessionIdProvider: () -> String,
    private val onSaveAsSucceeded: (EditorPendingSaveAsResult) -> Boolean,
    private val onSaveAsInProgressChanged: (Boolean) -> Unit,
) {

    private val fileReadPipeline = EditorFileReadPipeline(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val destroyed = AtomicBoolean(false)
    private val saveAsInProgress = AtomicBoolean(false)
    private val recoveryLeaseLock = Any()
    private val leasedRecoveryFiles = mutableSetOf<File>()
    @Volatile
    private var saveAsWriteStarted = false
    @Volatile
    private var saveAsOperationToken: EditorSaveAsOperationToken? = null
    @Volatile
    private var saveAsChooserDisposable: Disposable? = null

    fun queryContentLengthOrNull(uri: Uri): Long? {
        return fileReadPipeline.queryContentLengthOrNull(uri)
    }

    fun queryDisplayNameOrNull(uri: Uri): String? {
        return fileReadPipeline.queryDisplayNameOrNull(uri)
    }

    fun readText(uri: Uri, charsetOverride: Charset? = null): EditorFileReadResult {
        return fileReadPipeline.readText(uri, effectiveReadCharset(uri, charsetOverride))
    }

    fun streamTextChunks(
        uri: Uri,
        cancel: AtomicBoolean,
        streamRef: AtomicReference<InputStream?>,
        onMetadata: (EditorFileReadMetadata) -> Unit,
        charsetOverride: Charset? = null,
    ): Observable<String> {
        return fileReadPipeline.streamTextChunks(
            uri = uri,
            cancel = cancel,
            streamRef = streamRef,
            onMetadata = onMetadata,
            charsetOverride = effectiveReadCharset(uri, charsetOverride),
        )
    }

    private fun effectiveReadCharset(uri: Uri, charsetOverride: Charset?): Charset? =
        charsetOverride ?: if (PrivilegedEditSessions.isSessionUri(context, uri)) {
            PrivilegedEditSessions.pendingCharset(context, uri)
        } else null

    fun readEncodingProbe(uri: Uri, limitBytes: Int = ENCODING_PROBE_LIMIT_BYTES): EditorEncodingProbe {
        require(limitBytes > 0)
        return EditorFileIoCoordinator.withReadAccess(uri) {
            val buffer = ByteArray(limitBytes + 1)
            var read = 0
            val raw = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream for $uri")
            raw.use { input ->
                while (read < buffer.size) {
                    val count = input.read(buffer, read, buffer.size - read)
                    if (count < 0) break
                    if (count == 0) {
                        val oneByte = input.read()
                        if (oneByte < 0) break
                        buffer[read++] = oneByte.toByte()
                        continue
                    }
                    read += count
                }
            }
            val truncated = read > limitBytes
            val size = read.coerceAtMost(limitBytes)
            EditorEncodingProbe(buffer.copyOf(size), truncated)
        }
    }

    fun discardStableDraft(uri: Uri) {
        StableDraftFileHelper(context, uri.editorDocumentIdentity()).deleteDraft()
    }

    fun saveTextTransactional(
        uri: Uri,
        text: String,
        encoding: EditorFileSaveEncoding,
        displayName: String,
        previousEncoding: EditorFileSaveEncoding,
        discardStableDraftOnSuccess: Boolean = true,
    ) {
        EditorFileIoCoordinator.withWriteAccess(uri) {
            writeTextWithCharsetTransactional(
                uri = uri,
                text = text,
                encoding = encoding,
                displayName = displayName,
                previousEncoding = previousEncoding,
            )
            if (discardStableDraftOnSuccess) {
                StableDraftFileHelper(context, uri.editorDocumentIdentity()).deleteDraft()
            }
        }
    }

    private fun writeTextWithCharsetTransactional(
        uri: Uri,
        text: String,
        encoding: EditorFileSaveEncoding,
        displayName: String,
        previousEncoding: EditorFileSaveEncoding,
    ) {
        val resolver = context.contentResolver

        val targetCharset = encoding.charset
        val needBom = encoding.hadBom

        val newBytes = buildBytesToWrite(text, targetCharset, needBom)
        if (PrivilegedEditSessions.isSessionUri(context, uri)) {
            // This private provider exposes a durable editor snapshot, not a writable copy of
            // the source. Only the session can verify and publish bytes to the original file.
            PrivilegedEditSessions.save(context, uri, newBytes, targetCharset.name(), needBom)
            // App-UID file observers cannot watch restricted source directories. Refresh their
            // privileged row metadata after a confirmed save, including the next rename/delete.
            mainHandler.post {
                Explorers.external().invalidateCache()
                Explorers.workspace().refreshAll()
            }
            runCatching { PrivilegedEditSessions.consumeSaveNotice(context, uri) }.getOrNull()?.let { notice ->
                hostView.post {
                    if (!destroyed.get() && hostView.isAttachedToWindow) {
                        Snackbar.make(hostView, R.string.text_done, Snackbar.LENGTH_LONG)
                            .setAction(R.string.dialog_button_details) {
                                DialogUtils.buildAndShowAdaptive {
                                    MaterialDialog.Builder(context)
                                        .title(R.string.text_done)
                                        .content(notice)
                                        .positiveText(R.string.dialog_button_dismiss)
                                        .negativeText(R.string.text_privileged_backups)
                                        .onNegative { _, _ ->
                                            val source = PrivilegedEditSessions.originalPath(context, uri)
                                            PrivilegedBackupUi.show(hostView, source, source?.let { File(it).parent }, PrivilegedEditSessions.accessMode(context, uri))
                                        }
                                        .neutralText(R.string.text_copy_all)
                                        .onNeutral { _, _ -> ClipboardUtils.setClip(context, notice) }
                                        .build()
                                        .apply { makeTextCopyable(contentView) }
                                }
                            }
                            .show()
                    }
                }
            }
            return
        }
        val oldBytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot capture original bytes before writing: $uri")
        } catch (error: Throwable) {
            throw IOException("Cannot save safely because the original file could not be read: $uri", error)
        }

        versionHistoryService.recordSavePreIfNeeded(
            uri = uri,
            oldBytes = oldBytes,
            newBytes = newBytes,
            encodingName = previousEncoding.charset.name(),
            hadBom = previousEncoding.hadBom,
        )

        val oldHash = sha256(oldBytes)
        val newHash = sha256(newBytes)

        if (uri.scheme.equals("file", ignoreCase = true)) {
            try {
                writeFileUriAtomically(uri, newBytes, oldHash, newHash)
                return
            } catch (e: SecurityException) {
                handleSaveFailure(
                    uri,
                    displayName,
                    oldBytes,
                    newBytes,
                    text,
                    encoding,
                    IOException("Permission denied for uri: $uri", e),
                    rollbackRequired = false,
                )
            } catch (e: Throwable) {
                handleSaveFailure(
                    uri,
                    displayName,
                    oldBytes,
                    newBytes,
                    text,
                    encoding,
                    e,
                    rollbackRequired = false,
                )
            }
        }

        // A generic content provider cannot offer a provider-independent atomic replace. Persist
        // and fsync the preimage before the first truncating write so a process kill or power loss
        // still leaves a recoverable original in app-private storage.
        // zh-CN: 通用 content provider 无法提供与实现无关的原子替换. 因此在首次截断写入前
        // 持久化并 fsync 原始内容, 使进程被杀或掉电后仍保留可恢复的原文件副本.
        val preparedOriginalRecovery = EmergencyDraftStore(context).saveDraft(
            displayName = "original.pending.${displayName.ifBlank { "untitled" }}",
            bytes = oldBytes,
        )
        val preWriteHash = runCatching {
            resolver.openInputStream(uri)?.use(::sha256)
                ?: throw IOException("Cannot verify source immediately before writing: $uri")
        }.getOrElse { verificationError ->
            handleSaveFailure(
                uri,
                displayName,
                oldBytes,
                newBytes,
                text,
                encoding,
                verificationError,
                rollbackRequired = false,
                preparedOriginalRecoveryFile = preparedOriginalRecovery,
            )
        }
        if (!preWriteHash.contentEquals(oldHash)) {
            handleSaveFailure(
                uri,
                displayName,
                oldBytes,
                newBytes,
                text,
                encoding,
                IOException("The file changed outside the editor before it could be saved: $uri"),
                rollbackRequired = false,
                preparedOriginalRecoveryFile = preparedOriginalRecovery,
            )
        }
        try {
            resolver.openOutputStream(uri, "rwt")?.use { out ->
                out.write(newBytes)
                out.flush()
            } ?: throw IOException("Cannot open output stream for $uri")

            val readBackHash = runCatching {
                resolver.openInputStream(uri)?.use(::sha256)
                    ?: throw IOException("Cannot open input stream for write verification: $uri")
            }.getOrElse { e ->
                throw IOException("Read-back failed after write: $uri", e)
            }

            if (!readBackHash.contentEquals(newHash)) {
                throw IOException("Write verification failed (hash mismatch) for uri: $uri")
            }
            deleteEmergencyRecovery(preparedOriginalRecovery)
        } catch (e: Throwable) {
            val reportedError = if (e is SecurityException) {
                IOException("Permission denied for uri: $uri", e)
            } else {
                e
            }
            val currentHash = runCatching {
                resolver.openInputStream(uri)?.use(::sha256)
            }.getOrNull()
            if (currentHash?.contentEquals(newHash) == true) {
                deleteEmergencyRecovery(preparedOriginalRecovery)
                return
            }
            val targetStillHasOriginal = currentHash?.contentEquals(oldHash) == true
            handleSaveFailure(
                uri,
                displayName,
                oldBytes,
                newBytes,
                text,
                encoding,
                reportedError,
                rollbackRequired = false,
                stateUnknownWithoutRollback = !targetStillHasOriginal,
                preparedOriginalRecoveryFile = preparedOriginalRecovery,
            )
        }
    }

    private fun handleSaveFailure(
        uri: Uri,
        displayName: String,
        oldBytes: ByteArray,
        newBytes: ByteArray,
        newText: String,
        newEncoding: EditorFileSaveEncoding,
        error: Throwable,
        rollbackRequired: Boolean = true,
        stateUnknownWithoutRollback: Boolean = false,
        preparedOriginalRecoveryFile: File? = null,
    ): Nothing {
        val resolver = context.contentResolver

        val rollbackFailure = if (stateUnknownWithoutRollback) {
            IOException("The target no longer matches either the original or the completed write")
        } else if (rollbackRequired) {
            runCatching {
                resolver.openOutputStream(uri, "rwt")?.use { out ->
                    out.write(oldBytes)
                    out.flush()
                } ?: throw IOException("Cannot open output stream while restoring $uri")
                val restoredHash = resolver.openInputStream(uri)?.use(::sha256)
                    ?: throw IOException("Cannot verify restored bytes for $uri")
                if (!restoredHash.contentEquals(sha256(oldBytes))) {
                    throw IOException("Restored-byte verification failed for $uri")
                }
            }.exceptionOrNull()
        } else {
            null
        }

        val emergencyStore = EmergencyDraftStore(context)
        var originalRecoveryFailure: Throwable? = null
        val originalRecoveryFile = if (rollbackFailure != null) {
            preparedOriginalRecoveryFile ?: runCatching {
                emergencyStore.saveDraft(
                    displayName = "${displayName.ifBlank { "untitled" }}.original",
                    bytes = oldBytes,
                )
            }.onFailure { originalRecoveryFailure = it }.getOrNull()
        } else {
            preparedOriginalRecoveryFile?.let(::deleteEmergencyRecovery)
            null
        }
        // The editor still owns newText, while oldBytes may be the only remaining copy after a
        // failed rollback. Preserve the original first, then save the new snapshot best-effort.
        // zh-CN: 编辑器仍持有 newText, 而回滚失败后 oldBytes 可能是唯一旧副本.
        // 因此先保护原始内容, 再尽力保存新快照.
        val draftFile = runCatching {
            emergencyStore.saveDraft(
                displayName = displayName.ifBlank { "untitled" },
                bytes = newBytes,
            )
        }.getOrNull()
        val recoveryFiles = listOfNotNull(draftFile, originalRecoveryFile)
        trackRecoveryFiles(recoveryFiles)
        val reportedError = rollbackFailure?.let {
            EditorFileRollbackFailedException(originalRecoveryFile, it).apply {
                addSuppressed(error)
                originalRecoveryFailure?.let(::addSuppressed)
            }
        } ?: error

        val dialogFailure = runCatching {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.error_save_failed)
                    .content(
                        buildString {
                            append(error.message ?: error.toString())
                            if (rollbackFailure != null) {
                                append("\n\n")
                                append(context.getString(R.string.error_file_state_unknown_reload_required))
                            }
                            if (draftFile != null) {
                                append("\n\n")
                                append(context.getString(R.string.text_draft_file_path))
                                append(":\n")
                                append(draftFile.absolutePath)
                            }
                            if (originalRecoveryFile != null) {
                                append("\n\n")
                                append(context.getString(R.string.text_original_file_recovery_path))
                                append(":\n")
                                append(originalRecoveryFile.absolutePath)
                            }
                        }
                    )
                    .also { builder ->
                        val pathToCopy = originalRecoveryFile ?: draftFile
                        if (pathToCopy != null) {
                            builder.neutralText(R.string.dialog_button_copy_path)
                            builder.neutralColorRes(R.color.dialog_button_hint)
                            builder.onNeutral { d, _ ->
                                ClipboardUtils.setClip(context, pathToCopy.absolutePath)
                                showSnack(d.view, R.string.text_already_copied_to_clip, false)
                            }
                        }
                    }
                    .negativeText(R.string.dialog_button_dismiss)
                    .negativeColorRes(R.color.dialog_button_default)
                    .onNegative { d, _ ->
                        releaseRecoveryFiles(recoveryFiles)
                        d.dismiss()
                    }
                    .positiveText(R.string.dialog_button_save_as)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .onPositive { d, _ ->
                        showSaveAsDialog(
                            failureDialog = d,
                            uri = uri,
                            displayName = displayName,
                            newBytes = newBytes,
                            newText = newText,
                            newEncoding = newEncoding,
                            recoveryFiles = recoveryFiles,
                        )
                    }
                    .autoDismiss(false)
                    .cancelable(false)
                    .build()
            }
        }.exceptionOrNull()
        if (dialogFailure != null) {
            releaseRecoveryFiles(recoveryFiles)
            reportedError.addSuppressed(dialogFailure)
        }

        if (reportedError is IOException) {
            throw reportedError
        }
        throw IOException(reportedError)
    }

    /** Stages a complete sibling file and publishes it with one same-filesystem rename. */
    private fun writeFileUriAtomically(
        uri: Uri,
        bytes: ByteArray,
        expectedOldHash: ByteArray,
        expectedNewHash: ByteArray,
    ) {
        val rawPath = uri.path ?: throw IOException("File URI has no path: $uri")
        val target = File(rawPath).canonicalFile
        val parent = target.parentFile ?: throw IOException("File has no parent directory: $target")
        if (!parent.isDirectory) {
            throw IOException("File parent directory does not exist: $parent")
        }

        val originalStat = Os.stat(target.absolutePath)
        if (originalStat.st_nlink > 1) {
            throw IOException("Atomic save would break hard-link identity: $target")
        }
        val originalMode = originalStat.st_mode
        val originalIdentity = originalStat.toFileIdentity()
        val staged = File.createTempFile(".autojs6-save-", ".tmp", parent)
        try {
            FileOutputStream(staged).use { output ->
                output.write(bytes)
                output.flush()
                Os.fchmod(output.fd, originalMode and 0xFFF)
                output.fd.sync()
            }
            if (!sha256(staged).contentEquals(expectedNewHash)) {
                throw IOException("Atomic-save staging verification failed: $staged")
            }

            if (!sha256(target).contentEquals(expectedOldHash)) {
                throw IOException("The file changed outside the editor before atomic publication: $target")
            }
            val currentStat = Os.stat(target.absolutePath)
            if (currentStat.toFileIdentity() != originalIdentity || currentStat.st_mode != originalMode) {
                throw IOException("File metadata changed outside the editor before atomic publication: $target")
            }

            Os.rename(staged.absolutePath, target.absolutePath)
            syncDirectoryBestEffort(parent)
        } finally {
            runCatching { staged.delete() }
            syncDirectoryBestEffort(parent)
        }
    }

    private fun syncDirectoryBestEffort(directory: File) {
        val descriptor = runCatching {
            Os.open(directory.absolutePath, android.system.OsConstants.O_RDONLY, 0)
        }.getOrNull() ?: return
        try {
            runCatching { Os.fsync(descriptor) }
        } finally {
            runCatching { Os.close(descriptor) }
        }
    }

    private fun deleteEmergencyRecovery(file: File) {
        EmergencyDraftDirectoryGuard.releaseActive(listOf(file))
        runCatching { file.delete() }
    }

    /** Keeps the current editor snapshot available when the original cannot be written safely. */
    fun showPrivilegedSaveFailure(
        uri: Uri,
        displayName: String,
        text: String,
        encoding: EditorFileSaveEncoding,
        error: Throwable,
        configureAccess: () -> Unit,
    ) {
        if (destroyed.get()) return
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.error_failed_to_save)
                .content(error.message ?: error.toString())
                .negativeText(R.string.text_privileged_edit_keep_editing)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { dialog, _ -> dialog.dismiss() }
                .positiveText(R.string.dialog_button_save_as)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { dialog, _ ->
                    saveCopyFromDialog(dialog, uri, displayName, text, encoding)
                }
                .neutralText(R.string.text_explorer_access_mode)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { dialog, _ ->
                    dialog.dismiss()
                    configureAccess()
                }
                .autoDismiss(false)
                .build()
                .apply { makeTextCopyable(contentView) }
        }
    }

    fun saveCopyFromDialog(
        dialog: MaterialDialog,
        uri: Uri,
        displayName: String,
        text: String,
        encoding: EditorFileSaveEncoding,
    ) {
        showSaveAsDialog(
            failureDialog = dialog,
            uri = uri,
            displayName = displayName,
            newBytes = null,
            newText = text,
            newEncoding = encoding,
            recoveryFiles = emptyList(),
        )
    }

    private fun showSaveAsDialog(
        failureDialog: MaterialDialog,
        uri: Uri,
        displayName: String,
        newBytes: ByteArray?,
        newText: String,
        newEncoding: EditorFileSaveEncoding,
        recoveryFiles: List<File>,
    ) {
        if (!saveAsInProgress.compareAndSet(false, true)) return
        val operationToken = runCatching {
            EditorPendingSaveAsStore.begin(editorSessionIdProvider(), uri)
        }.getOrElse {
            saveAsInProgress.set(false)
            throw it
        }
        saveAsOperationToken = operationToken
        saveAsWriteStarted = false
        setSaveAsDialogBusy(failureDialog, true)
        onSaveAsInProgressChanged(true)
        var directorySelected = false
        runCatching {
            saveAsChooserDisposable = FileChooserDialogBuilder(context)
                .title(R.string.text_save_to)
                .dir(HistoryPaths.internalStorageRoot)
                .chooseDir()
                .singleChoice()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ dir ->
                    if (directorySelected) return@subscribe
                    directorySelected = true
                    saveAsWriteStarted = true

                    Schedulers.io().scheduleDirect {
                        runCatching {
                            val bytes = newBytes ?: buildBytesToWrite(newText, newEncoding.charset, newEncoding.hadBom)
                            val sourcePath = if (PrivilegedEditSessions.isSessionUri(context, uri)) {
                                PrivilegedEditSessions.originalPath(context, uri)
                            } else uri.path
                            val ext = sourcePath?.let(::targetExtFromPath).orEmpty()
                            val dest = writeSaveAsFileSafely(
                                directory = File(dir.path),
                                baseName = displayName,
                                ext = ext,
                                bytes = bytes,
                            )

                            releaseRecoveryFiles(recoveryFiles)
                            val record = EditorPendingSaveAsStore.complete(
                                token = operationToken,
                                destination = dest,
                                encoding = newEncoding,
                                savedByteCount = bytes.size.toLong(),
                                savedSha256 = sha256(bytes).toHexString(),
                            ) ?: throw IOException("Save As operation is no longer active")
                            val result = EditorPendingSaveAsResult(record, newText)
                            mainHandler.post {
                                runCatching { failureDialog.dismiss() }
                                val applied = runCatching { onSaveAsSucceeded(result) }
                                    .getOrDefault(false)
                                if (applied) {
                                    EditorPendingSaveAsStore.removeIfSame(record)
                                }
                                finishSaveAsOperation(failureDialog, successful = true)
                            }
                        }.onFailure {
                            if (destroyed.get()) {
                                releaseRecoveryFiles(recoveryFiles)
                            }
                            mainHandler.post {
                                showSnack(failureDialog.view, R.string.error_save_failed, false)
                                showToast(context, it.message, true)
                                finishSaveAsOperation(failureDialog, successful = false)
                            }
                        }
                    }
                }, { e ->
                    e.printStackTrace()
                    finishSaveAsOperation(failureDialog, successful = false)
                }, {
                    if (!directorySelected) {
                        finishSaveAsOperation(failureDialog, successful = false)
                    }
                })
        }.onFailure {
            it.printStackTrace()
            finishSaveAsOperation(failureDialog, successful = false)
        }
    }

    private fun setSaveAsDialogBusy(dialog: MaterialDialog, busy: Boolean) {
        runCatching { dialog.getActionButton(DialogAction.NEUTRAL).isEnabled = !busy }
        runCatching { dialog.getActionButton(DialogAction.NEGATIVE).isEnabled = !busy }
        runCatching { dialog.getActionButton(DialogAction.POSITIVE).isEnabled = !busy }
    }

    private fun finishSaveAsOperation(dialog: MaterialDialog, successful: Boolean) {
        if (!saveAsInProgress.compareAndSet(true, false)) return
        val operationToken = saveAsOperationToken
        saveAsOperationToken = null
        saveAsWriteStarted = false
        saveAsChooserDisposable?.dispose()
        saveAsChooserDisposable = null
        if (!successful && operationToken != null) {
            EditorPendingSaveAsStore.cancel(operationToken)
        }
        setSaveAsDialogBusy(dialog, false)
        onSaveAsInProgressChanged(false)
        if (destroyed.get()) {
            releaseAllRecoveryFiles()
        }
    }

    /** Cancels a chooser-only operation; an already-started file write must run to completion. */
    fun destroy() {
        destroyed.set(true)
        if (!saveAsInProgress.get()) {
            releaseAllRecoveryFiles()
            return
        }
        if (saveAsWriteStarted) return
        saveAsChooserDisposable?.dispose()
        saveAsChooserDisposable = null
        saveAsOperationToken?.let(EditorPendingSaveAsStore::cancel)
        saveAsOperationToken = null
        if (saveAsInProgress.compareAndSet(true, false)) {
            onSaveAsInProgressChanged(false)
        }
        releaseAllRecoveryFiles()
    }

    private fun trackRecoveryFiles(files: Collection<File>) {
        val filesToRelease = synchronized(recoveryLeaseLock) {
            if (destroyed.get()) {
                files.toList()
            } else {
                leasedRecoveryFiles += files
                emptyList()
            }
        }
        if (filesToRelease.isNotEmpty()) {
            EmergencyDraftDirectoryGuard.releaseActive(filesToRelease)
        }
    }

    private fun releaseRecoveryFiles(files: Collection<File>) {
        val filesToRelease = synchronized(recoveryLeaseLock) {
            files.filter(leasedRecoveryFiles::remove)
        }
        if (filesToRelease.isNotEmpty()) {
            EmergencyDraftDirectoryGuard.releaseActive(filesToRelease)
        }
    }

    private fun releaseAllRecoveryFiles() {
        val filesToRelease = synchronized(recoveryLeaseLock) {
            leasedRecoveryFiles.toList().also { leasedRecoveryFiles.clear() }
        }
        if (filesToRelease.isNotEmpty()) {
            EmergencyDraftDirectoryGuard.releaseActive(filesToRelease)
        }
    }

    private fun buildBytesToWrite(text: String, charset: Charset, needBom: Boolean): ByteArray {
        val body = EditorEncodingCodec.encodeStrict(text, charset)
        if (!needBom) return body

        val bom = StringUtils.bomBytes(charset)
        return ByteArray(bom.size + body.size).also {
            System.arraycopy(bom, 0, it, 0, bom.size)
            System.arraycopy(body, 0, it, bom.size, body.size)
        }
    }

    private fun targetExtFromPath(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return ""
        return name.substring(dot + 1)
    }

    private fun buildSafeFileNameForSaveAs(baseName: String, ext: String): String {
        val raw = baseName.ifBlank { "untitled" }
        val sanitized = raw.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val niceExt = ext.trim().trimStart('.')
        val candidate = if (niceExt.isBlank()) sanitized else "$sanitized.$niceExt"
        if (candidate.toByteArray(StandardCharsets.UTF_8).size <= MAX_SAVE_AS_FILENAME_UTF8_BYTES) {
            return candidate
        }

        val shortHash = sha256(candidate.toByteArray(StandardCharsets.UTF_8))
            .copyOf(SHORT_RECOVERY_NAME_HASH_BYTES)
            .toHexString()
        val boundedExtension = truncateToUtf8Bytes(niceExt, MAX_SAVE_AS_EXTENSION_UTF8_BYTES)
        val suffix = "~$shortHash" + if (boundedExtension.isBlank()) "" else ".$boundedExtension"
        val stemBudget = (
            MAX_SAVE_AS_FILENAME_UTF8_BYTES - suffix.toByteArray(StandardCharsets.UTF_8).size
            ).coerceAtLeast(0)
        return truncateToUtf8Bytes(sanitized, stemBudget) + suffix
    }

    private fun truncateToUtf8Bytes(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        val result = StringBuilder(value.length.coerceAtMost(maxBytes))
        var index = 0
        var usedBytes = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val chars = Character.toChars(codePoint)
            val encodedSize = String(chars).toByteArray(StandardCharsets.UTF_8).size
            if (usedBytes + encodedSize > maxBytes) break
            result.append(chars)
            usedBytes += encodedSize
            index += chars.size
        }
        return result.toString()
    }

    /**
     * Creates a new, verified Save As target without ever truncating an existing file.
     * A fully synced temporary file is hard-linked into place when the filesystem supports it;
     * the fallback moves that already-complete file instead of exposing a partially copied target.
     */
    private fun writeSaveAsFileSafely(
        directory: File,
        baseName: String,
        ext: String,
        bytes: ByteArray,
    ): File {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create Save As directory: $directory")
        }
        if (!directory.isDirectory) {
            throw IOException("Save As destination is not a directory: $directory")
        }

        val expectedHash = sha256(bytes)
        val temp = File.createTempFile(".autojs6-save-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!sha256(temp).contentEquals(expectedHash)) {
                throw IOException("Save As temporary-file verification failed: $temp")
            }

            repeat(MAX_SAVE_AS_NAME_ATTEMPTS) { suffix ->
                val target = File(
                    directory,
                    buildSafeFileNameForSaveAs(
                        baseName = if (suffix == 0) baseName else "$baseName ($suffix)",
                        ext = ext,
                    ),
                )
                val created = EditorFileIoCoordinator.withWriteAccess(Uri.fromFile(target)) {
                    materializeNewSaveAsTarget(temp, target, expectedHash)
                }
                if (created) return target
            }
            throw IOException("Cannot allocate a unique Save As filename in $directory")
        } finally {
            runCatching { temp.delete() }
            syncDirectoryBestEffort(directory)
        }
    }

    private fun materializeNewSaveAsTarget(
        verifiedTemp: File,
        target: File,
        expectedHash: ByteArray,
    ): Boolean {
        if (target.exists()) return false

        val expectedIdentity = regularFileIdentityOrNull(verifiedTemp)
            ?: throw IOException("Save As temporary path is not a regular file: $verifiedTemp")
        try {
            Os.link(verifiedTemp.absolutePath, target.absolutePath)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EEXIST) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // No safe no-replacement move API exists here if the filesystem rejects hard links.
                // Preserve the original instead of exposing a partial copy under the final name.
                throw IOException("This filesystem cannot safely publish a new Save As file on Android 7: $target", error)
            }
            if (!moveNewSaveAsTarget(verifiedTemp, target)) return false
        }

        val beforeVerification = regularFileIdentityOrNull(target)
        if (beforeVerification != expectedIdentity) {
            throw IOException("Save As target was replaced before verification: $target")
        }
        val verifiedHash = runCatching { sha256(target) }.getOrNull()
        val afterVerification = regularFileIdentityOrNull(target)
        if (afterVerification != expectedIdentity) {
            throw IOException("Save As target was replaced during verification: $target")
        }
        if (verifiedHash == null || !verifiedHash.contentEquals(expectedHash)) {
            throw IOException("Save As verification failed: $target")
        }
        target.parentFile?.let(::syncDirectoryBestEffort)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun moveNewSaveAsTarget(verifiedTemp: File, target: File): Boolean {
        try {
            // Publish the already-fsynced complete inode without requesting replacement.
            Files.move(verifiedTemp.toPath(), target.toPath())
        } catch (_: FileAlreadyExistsException) {
            return false
        } catch (error: Exception) {
            throw IOException("Cannot publish Save As target: $target", error)
        }
        return true
    }

    private fun StructStat.toFileIdentity(): FileIdentity = FileIdentity(st_dev, st_ino)

    private fun regularFileIdentityOrNull(file: File): FileIdentity? = runCatching {
        Os.lstat(file.absolutePath).takeIf { OsConstants.S_ISREG(it.st_mode) }?.toFileIdentity()
    }.getOrNull()

    private data class FileIdentity(
        val device: Long,
        val inode: Long,
    )

    private class EmergencyDraftStore(private val context: Context) {

        private val draftsDir: File by lazy { File(context.filesDir, "drafts") }

        fun saveDraft(displayName: String, bytes: ByteArray): File =
            EmergencyDraftDirectoryGuard.withLock(draftsDir) {
                if (!draftsDir.exists() && !draftsDir.mkdirs()) {
                    throw IOException("Cannot create emergency-draft directory: $draftsDir")
                }
                // A failed source write is often caused by low storage. Reclaim eligible old
                // recovery files before allocating the new temporary copy, while active files
                // remain protected by the shared guard.
                // zh-CN: 源文件写入失败常由存储空间不足引起. 分配新临时副本前先回收
                // 可清理的旧恢复文件, 同时由共享 guard 继续保护活动文件.
                runCatching { cleanupLocked() }

                val safeName = displayName
                    .ifBlank { "untitled" }
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .lowercase(Locale.getDefault())
                val part = File.createTempFile(".recovery-", ".part", draftsDir)
                val targetPrefix = "${part.name.removeSuffix(".part")}_"
                val nameHash = sha256(safeName.toByteArray(StandardCharsets.UTF_8))
                    .take(SHORT_RECOVERY_NAME_HASH_BYTES)
                    .joinToString("") { "%02x".format(Locale.US, it) }
                val targetSuffix = "_$nameHash.bin"
                val safeNameByteBudget = (
                    MAX_RECOVERY_FILENAME_UTF8_BYTES -
                        targetPrefix.toByteArray(StandardCharsets.UTF_8).size -
                        targetSuffix.toByteArray(StandardCharsets.UTF_8).size
                    ).coerceAtLeast(0)
                val target = File(
                    draftsDir,
                    targetPrefix + truncateToUtf8Bytes(safeName, safeNameByteBudget) + targetSuffix,
                )
                val expectedHash = MessageDigest.getInstance("SHA-256").digest(bytes)

                try {
                    FileOutputStream(part).use { output ->
                        output.write(bytes)
                        output.flush()
                        output.fd.sync()
                    }
                    if (!sha256(part).contentEquals(expectedHash)) {
                        throw IOException("Emergency-draft temporary-file verification failed: $part")
                    }

                    if (!part.renameTo(target)) {
                        if (!target.createNewFile()) {
                            throw IOException("Cannot reserve emergency-draft path: $target")
                        }
                        try {
                            part.inputStream().use { input ->
                                FileOutputStream(target, false).use { output ->
                                    input.copyTo(output)
                                    output.flush()
                                    output.fd.sync()
                                }
                            }
                        } catch (error: Throwable) {
                            runCatching { target.delete() }
                            throw error
                        }
                    }

                    syncDraftDirectoryBestEffort()

                    EmergencyDraftDirectoryGuard.registerActive(target)
                    runCatching { cleanupLocked() }
                    val actualHash = runCatching { sha256(target) }.getOrNull()
                    if (!target.isFile || target.length() != bytes.size.toLong() ||
                        actualHash == null || !actualHash.contentEquals(expectedHash)
                    ) {
                        EmergencyDraftDirectoryGuard.releaseActive(listOf(target))
                        runCatching { target.delete() }
                        throw IOException("Emergency-draft publication verification failed: $target")
                    }
                    target
                } finally {
                    runCatching { part.delete() }
                }
            }

        private fun syncDraftDirectoryBestEffort() {
            val descriptor = runCatching {
                Os.open(draftsDir.absolutePath, android.system.OsConstants.O_RDONLY, 0)
            }.getOrNull() ?: return
            try {
                runCatching { Os.fsync(descriptor) }
            } finally {
                runCatching { Os.close(descriptor) }
            }
        }

        private fun cleanupLocked() {
            val files = draftsDir.listFiles()?.toList() ?: return
            if (files.isEmpty()) return

            val now = System.currentTimeMillis()
            val maxDraftLifetime = HistoryKeys.draftsMaxDays().coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
            val expiredBefore = now - maxDraftLifetime
            files.forEach { f ->
                if (!EmergencyDraftDirectoryGuard.isProtectedFromDeletion(f, now) &&
                    f.lastModified() < expiredBefore
                ) {
                    f.delete()
                }
            }

            val remained = draftsDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: return
            var total = remained.sumOf { it.length().coerceAtLeast(0L) }
            val draftLimit = HistoryKeys.draftsMaxTotalBytes().coerceAtLeast(0L)

            if (total <= draftLimit) return

            for (f in remained.asReversed()) {
                if (total <= draftLimit) break
                if (EmergencyDraftDirectoryGuard.isProtectedFromDeletion(f, now)) continue
                val len = f.length().coerceAtLeast(0L)
                if (f.delete()) {
                    total -= len
                }
            }
        }

        private fun truncateToUtf8Bytes(value: String, maxBytes: Int): String {
            if (maxBytes <= 0) return ""
            val result = StringBuilder(value.length.coerceAtMost(maxBytes))
            var index = 0
            var usedBytes = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val chars = Character.toChars(codePoint)
                val encodedSize = String(chars).toByteArray(StandardCharsets.UTF_8).size
                if (usedBytes + encodedSize > maxBytes) break
                result.append(chars)
                usedBytes += encodedSize
                index += chars.size
            }
            return result.toString()
        }

    }

    companion object {
        val DEFAULT_CHARSET_TO_WRITE_FILE: Charset = StandardCharsets.UTF_8

        private const val ENCODING_PROBE_LIMIT_BYTES = 256 * 1024
        private const val MAX_SAVE_AS_NAME_ATTEMPTS = 10_000
        private const val MAX_SAVE_AS_FILENAME_UTF8_BYTES = 240
        private const val MAX_SAVE_AS_EXTENSION_UTF8_BYTES = 32
        private const val MAX_RECOVERY_FILENAME_UTF8_BYTES = 240
        private const val SHORT_RECOVERY_NAME_HASH_BYTES = 4
    }
}

data class EditorFileSaveEncoding(
    val charset: Charset,
    val hadBom: Boolean,
)
