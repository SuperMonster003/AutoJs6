package org.autojs.autojs.storage.history

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version history dialog controller.
 * zh-CN: 版本历史对话框控制器.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by SuperMonster003 as of Feb 6, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 7, 2026.
 */
class VersionHistoryController(private val context: Context) {

    /**
     * Show version history for editor (restore into editor only, not auto-save).
     * zh-CN: 为编辑器显示版本历史 (仅恢复到编辑器, 不自动保存).
     */
    fun showForEditor(
        uri: Uri,
        onRestoreToEditor: (restoredText: String) -> Unit,
        onRestoredUi: () -> Unit,
    ) {
        val logicalPath = HistoryUriUtils.toLogicalPathOrNull(uri)
        if (logicalPath == null) {
            showNoHistoryDialog()
            return
        }
        loadAndShow(
            canonicalPath = logicalPath,
            displayPath = runCatching { Uri.decode(logicalPath) }.getOrElse { logicalPath },
            mode = Mode.RESTORE_TO_EDITOR,
            showPreview = false,
            onRestoreToEditor = onRestoreToEditor,
            onRestoredUi = onRestoredUi,
        )
    }

    /**
     * Show version history for explorer (restore to disk, overwriting the file content).
     * zh-CN: 为资源管理器显示版本历史 (恢复并写回磁盘, 覆盖文件内容).
     */
    fun showForFilePath(path: String, displayPath: String) {
        val normalized = path.trimEnd('/')
        if (!normalized.startsWith(INTERNAL_STORAGE_ROOT)) {
            showNoHistoryDialog()
            return
        }
        loadAndShow(
            canonicalPath = normalized,
            displayPath = displayPath,
            mode = Mode.RESTORE_TO_DISK,
            showPreview = true,
            onRestoreToEditor = null,
            onRestoredUi = null,
        )
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

    private fun loadAndShow(
        canonicalPath: String,
        displayPath: String,
        mode: Mode,
        showPreview: Boolean = false,
        onRestoreToEditor: ((String) -> Unit)?,
        onRestoredUi: (() -> Unit)?,
    ) {
        Schedulers.io().scheduleDirect {
            runCatching {
                val appCtx = context.applicationContext
                val dao = HistoryDatabase.getInstance(appCtx).historyDao()

                val fileEntry = dao.findFileByPath(canonicalPath)
                val fileId = fileEntry?.fileId

                val revs = if (fileId != null) {
                    // Show latest first for selection.
                    // zh-CN: 列表按最新优先展示供选择.
                    dao.listRevisionsAsc(fileId).asReversed().take(HISTORY_DIALOG_MAX_ITEMS)
                } else {
                    emptyList()
                }

                AndroidSchedulers.mainThread().scheduleDirect {
                    if (revs.isEmpty()) {
                        showNoHistoryDialog()
                        return@scheduleDirect
                    }

                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val items = revs.map { rev ->
                        val t = fmt.format(Date(rev.createdAt))
                        val size = rev.sizeBytes
                        "$t  |  ${PFiles.formatSizeWithUnit(size)}"
                    }

                    val selectedIndex = intArrayOf(0)

                    val optionDeleteSelected = MaterialDialog.OptionMenuItemSpec(
                        context.getString(R.string.text_delete)
                    ) { dialog ->
                        val chosen = revs.getOrNull(selectedIndex[0]) ?: return@OptionMenuItemSpec
                        confirmAndDeleteSelectedRevision(
                            parentDialog = dialog,
                            rev = chosen,
                            canonicalPath = canonicalPath,
                            displayPath = displayPath,
                            mode = mode,
                            onRestoreToEditor = onRestoreToEditor,
                            onRestoredUi = onRestoredUi,
                        )
                    }

                    DialogUtils.buildAndShowAdaptive {
                        MaterialDialog.Builder(context)
                            .title(File(displayPath).name)
                            .options(listOf(optionDeleteSelected))
                            .items(items)
                            .itemsCallbackSingleChoice(0) { _, _, which, _ ->
                                selectedIndex[0] = which
                                true
                            }
                            .alwaysCallSingleChoiceCallback()
                            .choiceWidgetThemeColor()
                            .apply {
                                if (!showPreview) return@apply
                                neutralText(R.string.dialog_button_preview)
                                neutralColorRes(R.color.dialog_button_hint)
                                onNeutral { _, _ ->
                                    val chosen = revs.getOrNull(selectedIndex[0]) ?: return@onNeutral
                                    previewInReadOnlyEditor(chosen, displayPath)
                                }
                            }
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColorRes(R.color.dialog_button_default)
                            .onNegative { d, _ -> d.dismiss() }
                            .positiveText(R.string.dialog_button_restore)
                            .positiveColorRes(R.color.dialog_button_attraction)
                            .onPositive { d, _ ->
                                val chosen = revs.getOrNull(selectedIndex[0]) ?: return@onPositive
                                when (mode) {
                                    Mode.RESTORE_TO_EDITOR -> {
                                        showRestoreConfirmDialogForEditor {
                                            d.dismiss()
                                            restoreToEditor(chosen, onRestoreToEditor, onRestoredUi)
                                        }
                                    }
                                    Mode.RESTORE_TO_DISK -> {
                                        showRestoreConfirmDialogForDisk(canonicalPath) {
                                            d.dismiss()
                                            restoreToDisk(chosen, canonicalPath)
                                        }
                                    }
                                }
                            }
                            .cancelable(true)
                            .autoDismiss(false)
                            .build()
                            .apply {
                                titleView.setOnLongClickListener {
                                    when {
                                        File(displayPath).exists() -> {
                                            Scripts.edit(this@VersionHistoryController.context, displayPath)
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                    }
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
     * Preview selected revision in a read-only editor.
     * zh-CN: 在只读编辑器中预览所选 revision 内容.
     */
    private fun previewInReadOnlyEditor(rev: HistoryEntities.Revision, displayPath: String) {
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


    private fun restoreToEditor(
        rev: HistoryEntities.Revision,
        onRestoreToEditor: ((String) -> Unit)?,
        onRestoredUi: (() -> Unit)?,
    ) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val bytes = HistoryRepository(appCtx).readRevisionBytes(rev)
                val restored = decodeRevisionBytes(bytes, rev.encoding, rev.hadBom)
                AndroidSchedulers.mainThread().scheduleDirect {
                    onRestoreToEditor?.invoke(restored)
                    onRestoredUi?.invoke()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    private fun restoreToDisk(rev: HistoryEntities.Revision, targetPath: String) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val bytes = HistoryRepository(appCtx).readRevisionBytes(rev)
                writeBytesTransactional(File(targetPath), bytes)

                // Notify Explorer to refresh the parent directory after restoring to disk.
                // zh-CN: 写回磁盘恢复成功后通知 Explorer 刷新目标父目录.
                notifyExplorerParentDirChanged(targetPath)

                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, context.getString(R.string.text_done), true)
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
                key(R.string.key_version_history_restore_does_not_auto_save_to_disk),
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
        val charset = runCatching { Charset.forName(encodingName) }.getOrElse { DEFAULT_CHARSET }
        val effective = if (hadBom) {
            // Drop BOM before decoding because BOM presence is tracked by metadata.
            // zh-CN: BOM 是否存在由元数据记录, 解码前需丢弃 BOM.
            StringUtils.dropBom(bytes, charset)
        } else bytes
        return String(effective, charset)
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /**
     * Confirm deleting selected revision.
     * zh-CN: 确认删除所选 revision.
     */
    private fun confirmAndDeleteSelectedRevision(
        parentDialog: MaterialDialog,
        rev: HistoryEntities.Revision,
        canonicalPath: String,
        displayPath: String,
        mode: Mode,
        onRestoreToEditor: ((String) -> Unit)?,
        onRestoredUi: (() -> Unit)?,
    ) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.text_delete_revision_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    parentDialog.dismiss()
                    deleteRevisionAndReloadDialog(rev, canonicalPath, displayPath, mode, onRestoreToEditor, onRestoredUi)
                }
                .cancelable(true)
                .build()
        }
    }

    /**
     * Delete revision and reload dialog.
     * zh-CN: 删除 revision 并重新加载对话框.
     */
    private fun deleteRevisionAndReloadDialog(
        rev: HistoryEntities.Revision,
        canonicalPath: String,
        displayPath: String,
        mode: Mode,
        onRestoreToEditor: ((String) -> Unit)?,
        onRestoredUi: (() -> Unit)?,
    ) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                HistoryRepository(appCtx).deleteRevision(rev)
            }.onSuccess {
                AndroidSchedulers.mainThread().scheduleDirect {
                    loadAndShow(
                        canonicalPath = canonicalPath,
                        displayPath = displayPath,
                        mode = mode,
                        showPreview = true,
                        onRestoreToEditor = onRestoreToEditor,
                        onRestoredUi = onRestoredUi,
                    )
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, it.message, true)
                }
            }
        }
    }

    private enum class Mode {
        RESTORE_TO_EDITOR,
        RESTORE_TO_DISK,
    }

    companion object {
        private val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8
        private const val HISTORY_DIALOG_MAX_ITEMS: Int = 20
        private const val INTERNAL_STORAGE_ROOT: String = "/storage/emulated/0"

        // Keep Intent extras payload under a conservative threshold.
        // zh-CN: 将 Intent extras 的载荷控制在一个保守阈值以内.
        private const val PREVIEW_INTENT_MAX_BYTES: Int = 64 * 1024
    }
}
