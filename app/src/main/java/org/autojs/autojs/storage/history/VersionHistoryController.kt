package org.autojs.autojs.storage.history

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.core.pref.keys.DialogStateKeys
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.ui.storage.RevisionListActivity
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entry points into version history and the per-revision operations used by [RevisionListActivity].
 *
 * The list itself lives in [RevisionListActivity]; this controller decides whether there is
 * anything to show, launches the page in the right mode, and performs preview / restore / delete.
 *
 * zh-CN: 版本历史的入口, 以及 [RevisionListActivity] 使用的单条 revision 操作.
 * 列表本身在 [RevisionListActivity] 中; 本控制器负责判断有无内容, 以正确模式打开页面, 并执行预览 / 恢复 / 删除.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by SuperMonster003 as of Feb 6, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
class VersionHistoryController(private val context: Context) {

    enum class Mode {
        RESTORE_TO_EDITOR,
        RESTORE_TO_DISK,
    }

    /**
     * Show version history for editor (restore into editor only, not auto-save).
     * zh-CN: 为编辑器显示版本历史 (仅恢复到编辑器, 不自动保存).
     */
    fun showForEditor(uri: Uri, callbacks: VersionHistoryEditorBridge.Callbacks) {
        val logicalPath = HistoryUriUtils.toLogicalPathOrNull(uri)
        if (logicalPath == null) {
            showNoHistoryDialog()
            return
        }
        val displayPath = runCatching { Uri.decode(logicalPath) }.getOrElse { logicalPath }
        launchIfAnyRevision(logicalPath) {
            val token = VersionHistoryEditorBridge.register(callbacks)
            RevisionListActivity.launch(context, logicalPath, displayPath, Mode.RESTORE_TO_EDITOR, token)
        }
    }

    /**
     * Show version history for explorer (restore to disk, overwriting the file content).
     * zh-CN: 为资源管理器显示版本历史 (恢复并写回磁盘, 覆盖文件内容).
     */
    fun showForFilePath(path: String, displayPath: String) {
        val normalized = HistoryPaths.normalizePath(path)
        if (!HistoryPaths.isUnderInternalStorage(normalized)) {
            showNoHistoryDialog()
            return
        }
        launchIfAnyRevision(normalized) {
            RevisionListActivity.launch(context, normalized, displayPath, Mode.RESTORE_TO_DISK, null)
        }
    }

    private fun launchIfAnyRevision(canonicalPath: String, launch: () -> Unit) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            val hasRevision = runCatching {
                val dao = HistoryDatabase.getInstance(appCtx).historyDao()
                val fileId = dao.findFileByPath(canonicalPath)?.fileId
                fileId != null && dao.findLatestRevision(fileId) != null
            }.getOrElse {
                it.printStackTrace()
                false
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                if (hasRevision) launch() else showNoHistoryDialog()
            }
        }
    }

    private fun showNoHistoryDialog() {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_version_history)
                .content(R.string.text_no_version_history)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .cancelable(true)
                .build()
        }
    }

    /**
     * Preview a revision in a read-only editor.
     * zh-CN: 在只读编辑器中预览 revision 内容.
     */
    fun previewInReadOnlyEditor(rev: HistoryEntities.Revision, displayPath: String) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val bytes = HistoryRepository(appCtx).readRevisionBytes(rev)

                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timeText = fmt.format(Date(rev.createdAt))

                val name = "[ ${context.getString(R.string.text_preview)} ] ${File(displayPath).name} @ $timeText"

                AndroidSchedulers.mainThread().scheduleDirect {
                    val newTask = context !is Activity

                    // Avoid putting large content into Intent extras to prevent FAILED BINDER TRANSACTION.
                    // zh-CN: 避免将大文本放入 Intent extras, 防止 FAILED BINDER TRANSACTION.
                    if (bytes.size >= PREVIEW_INTENT_MAX_BYTES) {
                        val tmp = runCatching {
                            TmpScriptFiles.create(appCtx).also { f ->
                                f.outputStream().use { it.write(bytes) }
                            }
                        }.getOrElse {
                            it.printStackTrace()
                            ViewUtils.showToast(context, it.message, true)
                            return@scheduleDirect
                        }

                        EditActivity.viewPath(context, name, tmp.absolutePath, newTask)
                        return@scheduleDirect
                    }

                    val restored = decodeRevisionBytes(bytes, rev.encoding, rev.hadBom)
                    EditActivity.viewContent(context, name, restored, newTask)
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    /**
     * Confirm (with "do not ask again") and push the revision text into the editor.
     * zh-CN: 确认 (支持 "不再提示") 后把 revision 文本放入编辑器.
     */
    fun confirmAndRestoreToEditor(
        rev: HistoryEntities.Revision,
        callbacks: VersionHistoryEditorBridge.Callbacks,
        onDone: (() -> Unit)? = null,
    ) {
        showRestoreConfirmDialogForEditor {
            restoreToEditor(rev, callbacks, andSave = false, onDone = onDone)
        }
    }

    /**
     * Confirm (the file will be overwritten) and push the revision text into the editor, then save.
     * zh-CN: 确认 (文件将被覆盖) 后把 revision 文本放入编辑器并保存.
     */
    fun confirmAndRestoreToEditorAndSave(
        rev: HistoryEntities.Revision,
        callbacks: VersionHistoryEditorBridge.Callbacks,
        displayPath: String,
        onDone: (() -> Unit)? = null,
    ) {
        showRestoreConfirmDialogForDisk(displayPath) {
            restoreToEditor(rev, callbacks, andSave = true, onDone = onDone)
        }
    }

    /**
     * Confirm and write the revision back to [targetPath]; the previous content becomes a RESTORE_PRE revision.
     * zh-CN: 确认后把 revision 写回 [targetPath]; 原内容成为一条 RESTORE_PRE revision.
     */
    fun confirmAndRestoreToDisk(
        rev: HistoryEntities.Revision,
        targetPath: String,
        onDone: (() -> Unit)? = null,
    ) {
        showRestoreConfirmDialogForDisk(targetPath) {
            restoreToDisk(rev, targetPath, onDone)
        }
    }

    /**
     * Delete revisions (blobs included) off the main thread; [onDone] runs on the main thread afterwards.
     * zh-CN: 在非主线程删除 revision (含 blob); 完成后在主线程调用 [onDone].
     */
    fun deleteRevisions(revs: List<HistoryEntities.Revision>, onDone: (() -> Unit)? = null) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val repository = HistoryRepository(appCtx)
                revs.forEach { repository.deleteRevision(it) }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
            AndroidSchedulers.mainThread().scheduleDirect { onDone?.invoke() }
        }
    }

    /**
     * Export the raw bytes of a revision (original encoding and BOM preserved): pick a directory,
     * confirm a file name (suggested: original name with the revision time stamp), then write.
     * Overwriting a tracked file keeps its current content as an OVERWRITE_PRE revision (D4).
     *
     * zh-CN: 导出 revision 的原始字节 (保留原编码与 BOM): 选目录, 确认文件名 (建议名为原文件名加 revision 时间戳),
     * 然后写入. 覆盖已纳管文件时先把其当前内容记为 OVERWRITE_PRE revision (D4).
     */
    @SuppressLint("CheckResult")
    fun exportRevision(rev: HistoryEntities.Revision, displayPath: String) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(rev.createdAt))
        val suggested = HistoryExportNaming.suggestName(File(displayPath).name, stamp)
        FileChooserDialogBuilder(context)
            .title(R.string.text_save_to)
            .dir(HistoryPaths.internalStorageRoot)
            .chooseDir()
            .singleChoice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ dir ->
                promptExportFileName(rev, File(dir.path), suggested)
            }, { e ->
                e.printStackTrace()
                ViewUtils.showToast(context, e.message, true)
            })
    }

    private fun promptExportFileName(rev: HistoryEntities.Revision, dir: File, suggested: String) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_export)
                .content(context.getString(R.string.text_path_colon_value, dir.absolutePath))
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(context.getString(R.string.text_file_name), suggested, false) { _, input ->
                    val name = input.toString().trim()
                    if (!HistoryExportNaming.isValidFileName(name)) {
                        ViewUtils.showToast(context, context.getString(R.string.error_invalid_path, name), true)
                        return@input
                    }
                    val dest = File(dir, name)
                    if (dest.exists()) confirmOverwriteThenExport(rev, dest) else writeExport(rev, dest)
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .apply { widgetThemeColor() }
                .build()
        }
    }

    private fun confirmOverwriteThenExport(rev: HistoryEntities.Revision, dest: File) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_file_exists)
                .content(context.getString(R.string.text_path_colon_value, dest.absolutePath))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_overwrite)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> writeExport(rev, dest) }
                .cancelable(true)
                .build()
        }
    }

    private fun writeExport(rev: HistoryEntities.Revision, dest: File) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                if (dest.isDirectory) throw IOException("Destination is a directory: ${dest.absolutePath}")
                val repository = HistoryRepository(appCtx)
                val bytes = repository.readRevisionBytes(rev)
                if (dest.isFile && HistoryPaths.isUnderInternalStorage(HistoryPaths.normalizePath(dest.path))) {
                    repository.recordSnapshotBeforeOverwrite(
                        path = dest.path,
                        op = HistoryRepository.OP_OVERWRITE_PRE,
                        newContentSha256 = rev.sha256,
                    )
                }
                writeBytesTransactional(dest, bytes)
                notifyExplorerParentDirChanged(dest.path)
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, "${context.getString(R.string.text_exported)}: ${dest.absolutePath}", true)
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    fun setPinned(rev: HistoryEntities.Revision, pinned: Boolean, onDone: (() -> Unit)? = null) {
        updateRevision(onDone) { it.setRevisionPinned(rev, pinned) }
    }

    fun setLabel(rev: HistoryEntities.Revision, label: String?, onDone: (() -> Unit)? = null) {
        updateRevision(onDone) { it.setRevisionLabel(rev, label) }
    }

    private fun updateRevision(onDone: (() -> Unit)?, block: (HistoryRepository) -> Unit) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching { block(HistoryRepository(appCtx)) }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
            AndroidSchedulers.mainThread().scheduleDirect { onDone?.invoke() }
        }
    }

    private fun restoreToEditor(
        rev: HistoryEntities.Revision,
        callbacks: VersionHistoryEditorBridge.Callbacks,
        andSave: Boolean,
        onDone: (() -> Unit)?,
    ) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val bytes = HistoryRepository(appCtx).readRevisionBytes(rev)
                val restored = decodeRevisionBytes(bytes, rev.encoding, rev.hadBom)
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (andSave) callbacks.onRestoreToEditorAndSave(restored) else callbacks.onRestoreToEditor(restored)
                    callbacks.onRestoredUi()
                    onDone?.invoke()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    private fun restoreToDisk(rev: HistoryEntities.Revision, targetPath: String, onDone: (() -> Unit)?) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val repository = HistoryRepository(appCtx)

                // Read the revision first: the snapshot below runs retention cleanup, which could
                // otherwise evict the very revision being restored.
                // zh-CN: 先读取 revision: 下面的快照会触发保留策略清理, 可能淘汰正要恢复的这条 revision.
                val bytes = repository.readRevisionBytes(rev)

                // Keep the current on-disk content as a revision before overwriting it.
                // zh-CN: 覆盖前把当前磁盘内容记为一条 revision.
                repository.recordSnapshotBeforeOverwrite(
                    path = targetPath,
                    op = HistoryRepository.OP_RESTORE_PRE,
                    newContentSha256 = rev.sha256,
                )

                writeBytesTransactional(File(targetPath), bytes)

                // Notify Explorer to refresh the parent directory after restoring to disk.
                // zh-CN: 写回磁盘恢复成功后通知 Explorer 刷新目标父目录.
                notifyExplorerParentDirChanged(targetPath)

                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, context.getString(R.string.text_done), true)
                    onDone?.invoke()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    private fun notifyExplorerParentDirChanged(targetPath: String) {
        runCatching {
            val parent = File(targetPath).parentFile ?: return
            val page = ExplorerDirPage(ScriptFile(parent.path), null)
            Explorers.workspace().notifyChildrenChanged(page)
        }
    }

    private fun writeBytesTransactional(dest: File, bytes: ByteArray) {
        dest.parentFile?.mkdirs()

        val tmp = File(dest.parentFile, dest.name + ".restore.tmp")
        tmp.outputStream().use { it.write(bytes) }

        val newHash = sha256(bytes)
        val readBackHash = sha256(tmp.inputStream().use { it.readBytes() })
        if (!readBackHash.contentEquals(newHash)) {
            // Delete temp file when verification failed.
            // zh-CN: 校验失败时删除临时文件.
            // noinspection ResultOfMethodCallIgnored
            tmp.delete()
            throw IOException("Write verification failed (hash mismatch): ${dest.absolutePath}")
        }

        if (dest.exists() && !dest.delete()) {
            // noinspection ResultOfMethodCallIgnored
            tmp.delete()
            throw IOException("Failed to replace destination: ${dest.absolutePath}")
        }
        if (!tmp.renameTo(dest)) {
            // noinspection ResultOfMethodCallIgnored
            tmp.delete()
            throw IOException("Failed to commit restored file: ${dest.absolutePath}")
        }
    }

    private fun showRestoreConfirmDialogForEditor(onConfirm: () -> Unit) {
        // Use "Not ask again" for editor restoring.
        // zh-CN: 编辑器恢复使用 "不再提示" 的确认方式.
        DialogUtils.buildAndShowAdaptiveOrNull {
            NotAskAgainDialog.Builder(
                context,
                DialogStateKeys.VERSION_HISTORY_RESTORE_DOES_NOT_AUTO_SAVE_TO_DISK,
            ).apply {
                title(R.string.text_prompt)
                content(R.string.text_version_history_restore_does_not_auto_save_to_disk)
                widgetThemeColor()
                negativeText(R.string.dialog_button_cancel)
                negativeColorRes(R.color.dialog_button_default)
                positiveText(R.string.dialog_button_confirm)
                positiveColorRes(R.color.dialog_button_attraction)
                onPositive { _, _ -> onConfirm() }
                cancelable(false)
            }.build()
        } ?: onConfirm()
    }

    private fun showRestoreConfirmDialogForDisk(targetPath: String, onConfirm: () -> Unit) {
        // Restore to disk will overwrite file content.
        // zh-CN: 写回磁盘的恢复会覆盖文件内容.
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_version_history_restore_will_overwrite_file, targetPath))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> onConfirm() }
                .cancelable(true)
                .build()
        }
    }

    private fun decodeRevisionBytes(bytes: ByteArray, encodingName: String, hadBom: Boolean): String {
        return HistoryTextCodec.decodeRevision(bytes, encodingName, hadBom)
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    companion object {
        // Keep Intent extras payload under a conservative threshold.
        // zh-CN: 将 Intent extras 的载荷控制在一个保守阈值以内.
        private const val PREVIEW_INTENT_MAX_BYTES: Int = 64 * 1024

        /**
         * Localized label for a revision's op column.
         * zh-CN: revision 的 op 字段的本地化标签.
         */
        @JvmStatic
        fun opLabel(context: Context, op: String): String = when (op) {
            HistoryRepository.OP_SAVE_PRE -> context.getString(R.string.text_revision_op_save_pre)
            HistoryRepository.OP_RESTORE_PRE -> context.getString(R.string.text_revision_op_restore_pre)
            HistoryRepository.OP_OVERWRITE_PRE -> context.getString(R.string.text_revision_op_overwrite_pre)
            HistoryRepository.OP_MANUAL -> context.getString(R.string.text_revision_op_manual)
            else -> op
        }
    }
}
