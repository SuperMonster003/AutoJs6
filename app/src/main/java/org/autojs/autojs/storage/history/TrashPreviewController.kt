package org.autojs.autojs.storage.history

import android.app.Activity
import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Preview and details of a trash item: text files open in the read-only editor from a temporary
 * copy, directories show their archive listing in a dialog, binary files are refused; the details
 * dialog lists path / time / size / hash / expiry as selectable, copyable text.
 *
 * zh-CN: 回收站条目的预览与详情: 文本文件复制到临时文件后用只读编辑器打开, 目录以对话框显示归档条目列表,
 * 二进制文件拒绝预览; 详情对话框以可选中, 可复制的文本列出路径 / 时间 / 大小 / 哈希 / 到期时间.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class TrashPreviewController(private val context: Context) {

    fun showDetails(item: TrashEntities.TrashItem) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = System.currentTimeMillis()
        val expiresAt = TrashExpiry.expiresAtOrNull(item.trashedAt, HistoryKeys.trashMaxDays())
        fun line(label: String, value: String) = context.getString(R.string.text_property_colon_value, label, value)
        val text = buildList {
            add(line(context.getString(R.string.text_name), File(item.originalPath).name))
            add(line(context.getString(R.string.text_type), context.getString(if (item.isDirectory) R.string.text_folder else R.string.text_file)))
            add(context.getString(R.string.text_path_colon_value, item.originalPath))
            add(line(context.getString(R.string.text_moved_to_trash), fmt.format(Date(item.trashedAt))))
            add(line(context.getString(R.string.text_size), PFiles.formatSizeWithUnit(item.sizeBytes)))
            if (!item.isDirectory) add(line("SHA-256", item.sha256 ?: "-"))
            add(
                line(
                    context.getString(R.string.text_expires_at),
                    if (expiresAt == null) context.getString(R.string.text_unlimited)
                    else "${fmt.format(Date(expiresAt))} (${formatRemaining(context, TrashExpiry.remaining(expiresAt, now))})",
                )
            )
        }.joinToString("\n")
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_details)
                .content(text)
                .neutralText(R.string.text_copy_to_clip)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { _, _ ->
                    ClipboardUtils.setClip(context, text)
                    ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
                }
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .cancelable(true)
                .build()
                .apply { contentView?.setTextIsSelectable(true) }
        }
    }

    fun preview(item: TrashEntities.TrashItem) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val blob = File(appCtx.filesDir, item.blobRelPath)
                if (!blob.isFile) throw TrashBlobMissingException(item)
                if (item.isDirectory) {
                    val listing = TrashArchiveListing.read(blob)
                    AndroidSchedulers.mainThread().scheduleDirect { showArchiveListing(item, listing) }
                } else {
                    val head = ByteArray(TrashArchiveListing.BINARY_SNIFF_BYTES)
                    val read = blob.inputStream().use { it.read(head) }.coerceAtLeast(0)
                    if (TrashArchiveListing.looksBinary(head.copyOf(read))) {
                        AndroidSchedulers.mainThread().scheduleDirect {
                            ViewUtils.showToast(context, R.string.text_binary_content_cannot_be_previewed)
                        }
                        return@runCatching
                    }
                    val name = File(item.originalPath).name
                    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
                    val tmp = TmpScriptFiles.create(appCtx, ext)
                    blob.copyTo(tmp, overwrite = true)
                    val title = "[ ${context.getString(R.string.text_preview)} ] $name"
                    AndroidSchedulers.mainThread().scheduleDirect {
                        EditActivity.viewPath(context, title, tmp.absolutePath, context !is Activity)
                    }
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, TrashFailureText.describe(context, it), true)
                }
            }
        }
    }

    private fun showArchiveListing(item: TrashEntities.TrashItem, listing: TrashArchiveListing.Listing) {
        val text = buildString {
            append(context.getString(R.string.text_n_files_n_folders, listing.fileCount, listing.dirCount))
            append("  |  ")
            append(PFiles.formatSizeWithUnit(listing.totalBytes))
            if (listing.entries.isNotEmpty()) append("\n")
            listing.entries.forEach { entry ->
                append('\n')
                append(entry.relativePath)
                if (!entry.isDirectory) {
                    append("  (")
                    append(PFiles.formatSizeWithUnit(entry.sizeBytes))
                    append(')')
                }
            }
            if (listing.truncated) {
                append("\n\n")
                append(context.getString(R.string.text_and_n_more, listing.fileCount + listing.dirCount - listing.entries.size))
            }
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(File(item.originalPath).name)
                .content(text)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .cancelable(true)
                .build()
                .apply { contentView?.setTextIsSelectable(true) }
        }
    }

    companion object {
        /**
         * "N days M hours left" / "M hours left" / "Expired".
         * zh-CN: "剩余 N 天 M 小时" / "剩余 M 小时" / "已到期".
         */
        @JvmStatic
        fun formatRemaining(context: Context, remaining: TrashExpiry.Remaining): String = when {
            remaining.expired -> context.getString(R.string.text_expired)
            remaining.days <= 0L -> context.getString(R.string.text_n_hours_left, remaining.hours)
            else -> context.getString(R.string.text_n_days_n_hours_left, remaining.days, remaining.hours)
        }
    }
}
