package org.autojs.autojs.storage.history

import android.app.Activity
import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.file.TmpScriptFiles
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.io.FileNotFoundException

/**
 * Preview of a trash item: text files open in the read-only editor from a temporary copy,
 * directories show their archive listing in a dialog, binary files are refused.
 *
 * zh-CN: 回收站条目预览: 文本文件复制到临时文件后用只读编辑器打开, 目录以对话框显示归档条目列表, 二进制文件拒绝预览.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class TrashPreviewController(private val context: Context) {

    fun preview(item: TrashEntities.TrashItem) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            runCatching {
                val blob = File(appCtx.filesDir, item.blobRelPath)
                if (!blob.isFile) throw FileNotFoundException(item.blobRelPath)
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
                    ViewUtils.showToast(context, it.message, true)
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
}
