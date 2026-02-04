package org.autojs.autojs.storage.history

import android.annotation.SuppressLint
import android.content.Context
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.app.DialogUtils.setActionButtonText
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.MaterialDialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.regex.Pattern

/**
 * Restore flow controller for trash items.
 * zh-CN: 回收站条目的恢复流程控制器.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
class TrashRestoreController(private val context: Context) {

    fun startRestoreFlow(item: TrashEntities.TrashItem) {
        val originalPath = item.originalPath
        val originalName = File(originalPath).name

        val selected = intArrayOf(0)

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_choose_restore_path)
                .content(context.getString(R.string.text_path_colon_value, originalPath))
                .items(
                    context.getString(R.string.text_restore_to_original_path),
                    context.getString(R.string.text_restore_to_specified_path),
                )
                .itemsCallbackSingleChoice(0) { dialog, _, which, _ ->
                    selected[0] = which

                    // Update content and positive button text dynamically.
                    // zh-CN: 动态更新内容与确认按钮文本.
                    when (which) {
                        0 -> {
                            dialog.setContent(context.getString(R.string.text_path_colon_value, originalPath))
                            dialog.setActionButtonText(DialogAction.POSITIVE, context.getString(R.string.dialog_button_confirm))
                        }
                        else -> {
                            dialog.setContent(context.getString(R.string.text_file_name_colon_value, originalName))
                            dialog.setActionButtonText(DialogAction.POSITIVE, context.getString(R.string.dialog_button_choose_path))
                        }
                    }
                    true
                }
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ ->
                    when (selected[0]) {
                        0 -> restoreToOriginalPathWithConflictHandling(item)
                        else -> chooseDirectoryThenRestore(item, originalName)
                    }
                }
                .cancelable(true)
                .build()
        }
    }

    private fun restoreToOriginalPathWithConflictHandling(item: TrashEntities.TrashItem) {
        val dest = File(item.originalPath)
        val parent = dest.parentFile ?: run {
            ViewUtils.showToast(context, context.getString(R.string.error_invalid_path, item.originalPath), true)
            return
        }
        restoreWithConflictHandling(item, parent, dest.name)
    }

    @SuppressLint("CheckResult")
    private fun chooseDirectoryThenRestore(item: TrashEntities.TrashItem, fileName: String) {
        FileChooserDialogBuilder(context)
            .title(R.string.dialog_button_choose_path)
            .dir(INTERNAL_STORAGE_ROOT)
            .chooseDir()
            .singleChoice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ dir ->
                restoreWithConflictHandling(item, File(dir.path), fileName)
            }, { e ->
                e.printStackTrace()
                ViewUtils.showToast(context, e.message, true)
            })
    }

    private fun restoreWithConflictHandling(item: TrashEntities.TrashItem, destDir: File, fileName: String) {
        val dest = File(destDir, fileName)

        if (!dest.exists()) {
            restoreNow(item, dest)
            return
        }

        // Ask overwrite or auto-suffix when name conflict.
        // zh-CN: 同名冲突时询问覆盖或自动后缀.
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(context.getString(R.string.text_path_colon_value, dest.absolutePath))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.text_auto_rename)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { _, _ ->
                    val newName = generateNextIndexedName(destDir, fileName)
                    restoreNow(item, File(destDir, newName))
                }
                .positiveText(R.string.text_overwrite)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    restoreNow(item, dest)
                }
                .cancelable(true)
                .build()
        }
    }

    private fun restoreNow(item: TrashEntities.TrashItem, dest: File) {
        Schedulers.io().scheduleDirect {
            runCatching {
                TrashRepository(context.applicationContext).restoreTrashItemToPath(item, dest)
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

    /**
     * Generate next indexed name by scanning existing "-n" siblings and taking the first missing n from 1.
     * zh-CN: 通过扫描同级目录中已有的 "-n" 名称, 从 1 开始取第一个缺失的 n 作为新后缀.
     */
    private fun generateNextIndexedName(parentDir: File, originalName: String): String {
        val isDirectory = !originalName.contains('.')
        val base: String
        val extWithDot: String

        if (isDirectory) {
            base = originalName
            extWithDot = ""
        } else {
            val dot = originalName.lastIndexOf('.')
            base = if (dot > 0) originalName.substring(0, dot) else originalName
            extWithDot = if (dot > 0) originalName.substring(dot) else ""
        }

        val files = parentDir.listFiles()?.map { it.name } ?: emptyList()

        val existingIndexes = HashSet<Int>()
        val pattern = if (isDirectory) {
            Regex("^" + Pattern.quote(base) + "-(\\d+)$")
        } else {
            Regex("^" + Pattern.quote(base) + "-(\\d+)" + Pattern.quote(extWithDot) + "$")
        }

        for (name in files) {
            val m = pattern.matchEntire(name) ?: continue
            val idx = m.groups[1]?.value?.toIntOrNull() ?: continue
            existingIndexes.add(idx)
        }

        var n = 1
        while (existingIndexes.contains(n)) n++

        return if (isDirectory) "$base-$n" else "$base-$n$extWithDot"
    }

    companion object {
        private const val INTERNAL_STORAGE_ROOT: String = "/storage/emulated/0"
    }
}