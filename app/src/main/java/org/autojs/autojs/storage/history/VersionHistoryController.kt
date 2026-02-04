package org.autojs.autojs.storage.history

import android.content.Context
import android.net.Uri
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.MaterialDialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.MaterialDialogUtils.widgetThemeColor
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
            logicalPath = logicalPath,
            mode = Mode.RESTORE_TO_EDITOR,
            onRestoreToEditor = onRestoreToEditor,
            onRestoredUi = onRestoredUi,
        )
    }

    /**
     * Show version history for explorer (restore to disk, overwriting the file content).
     * zh-CN: 为资源管理器显示版本历史 (恢复并写回磁盘, 覆盖文件内容).
     */
    fun showForFilePath(path: String) {
        val normalized = path.trimEnd('/')
        if (!normalized.startsWith(INTERNAL_STORAGE_ROOT)) {
            showNoHistoryDialog()
            return
        }
        loadAndShow(
            logicalPath = normalized,
            mode = Mode.RESTORE_TO_DISK,
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
        logicalPath: String,
        mode: Mode,
        onRestoreToEditor: ((String) -> Unit)?,
        onRestoredUi: (() -> Unit)?,
    ) {
        Schedulers.io().scheduleDirect {
            runCatching {
                val appCtx = context.applicationContext
                val dao = HistoryDatabase.getInstance(appCtx).historyDao()

                val fileEntry = dao.findFileByPath(logicalPath)
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

                    DialogUtils.buildAndShowAdaptive {
                        MaterialDialog.Builder(context)
                            .title(R.string.text_version_history)
                            .items(items)
                            .itemsCallbackSingleChoice(0) { _, _, which, _ ->
                                selectedIndex[0] = which
                                true
                            }
                            .choiceWidgetThemeColor()
                            .negativeText(R.string.dialog_button_cancel)
                            .negativeColorRes(R.color.dialog_button_default)
                            .positiveText(R.string.dialog_button_retrieve)
                            .positiveColorRes(R.color.dialog_button_attraction)
                            .onPositive { _, _ ->
                                val chosen = revs.getOrNull(selectedIndex[0]) ?: return@onPositive
                                when (mode) {
                                    Mode.RESTORE_TO_EDITOR -> {
                                        showRestoreConfirmDialogForEditor {
                                            restoreToEditor(chosen, onRestoreToEditor, onRestoredUi)
                                        }
                                    }
                                    Mode.RESTORE_TO_DISK -> {
                                        showRestoreConfirmDialogForDisk(logicalPath) {
                                            restoreToDisk(chosen, logicalPath)
                                        }
                                    }
                                }
                            }
                            .cancelable(true)
                            .build()
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

    private enum class Mode {
        RESTORE_TO_EDITOR,
        RESTORE_TO_DISK,
    }

    companion object {
        private val DEFAULT_CHARSET: Charset = StandardCharsets.UTF_8
        private const val HISTORY_DIALOG_MAX_ITEMS: Int = 20
        private const val INTERNAL_STORAGE_ROOT: String = "/storage/emulated/0"
    }
}
