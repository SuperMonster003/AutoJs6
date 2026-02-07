package org.autojs.autojs.storage.history

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.OperationAbortedException
import org.autojs.autojs.util.DialogUtils.OperationController
import org.autojs.autojs.util.DialogUtils.ProgressDialogSession
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.setActionButtonText
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.regex.Pattern

/**
 * Restore flow controller for trash items.
 * zh-CN: 回收站条目的恢复流程控制器.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
@SuppressLint("CheckResult")
class TrashRestoreController(private val context: Context) {

    fun startRestoreFlow(
        item: TrashEntities.TrashItem,
        optionMenuItems: List<MaterialDialog.OptionMenuItemSpec> = emptyList(),
        onFinished: (() -> Unit)? = null,
    ) {
        val originalPath = item.originalPath
        val originalName = File(originalPath).name

        val selected = intArrayOf(0)

        fun generateContent(context: Context, isUserChoice: Boolean): String {
            val pathContent = if (isUserChoice) {
                "[ ${context.getString(R.string.text_to_be_chosen)} ]"
            } else originalPath.removeSuffix(originalName)

            return listOf(
                if (item.isDirectory) {
                    context.getString(R.string.text_folder_colon_value, originalName)
                } else {
                    context.getString(R.string.text_file_colon_value, originalName)
                },
                context.getString(R.string.text_path_colon_value, pathContent),
            ).joinToString("\n")
        }

        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_choose_restore_path)
                .options(optionMenuItems)
                .content(generateContent(context, false))
                .items(
                    context.getString(R.string.text_restore_to_original_path),
                    context.getString(R.string.text_restore_to_specified_path),
                )
                .itemsCallbackSingleChoice(0) { dialog, _, which, _ ->
                    selected[0] = which
                    when (which) {
                        0 -> {
                            dialog.setContent(generateContent(context, isUserChoice = false))
                            dialog.setActionButtonText(DialogAction.POSITIVE, context.getString(R.string.dialog_button_confirm))
                        }
                        else -> {
                            dialog.setContent(generateContent(context, isUserChoice = true))
                            dialog.setActionButtonText(DialogAction.POSITIVE, context.getString(R.string.dialog_button_choose_path))
                        }
                    }
                    true
                }
                .alwaysCallSingleChoiceCallback()
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { d, _ ->
                    when (selected[0]) {
                        0 -> restoreToOriginalPathWithConflictHandling(
                            item = item,
                            onRestore = { d.dismiss() },
                            onFinished = onFinished,
                        )
                        else -> chooseDirectoryThenRestore(
                            item = item,
                            fileName = originalName,
                            onRestore = { d.dismiss() },
                            onFinished = onFinished,
                        )
                    }
                }
                .cancelable(true)
                .autoDismiss(false)
                .build()
                .apply {
                    contentView?.apply {
                        setLineSpacing(0f, 1.2f)
                        setLines(3)
                        minLines = 3
                        maxLines = 9
                        ellipsize = TextUtils.TruncateAt.MIDDLE
                        gravity = Gravity.CENTER_VERTICAL
                    }
                }
        }
    }

    private fun restoreToOriginalPathWithConflictHandling(item: TrashEntities.TrashItem, onRestore: (() -> Unit)? = null, onFinished: (() -> Unit)? = null) {
        val dest = File(item.originalPath)
        val parent = dest.parentFile ?: run {
            ViewUtils.showToast(context, context.getString(R.string.error_invalid_path, item.originalPath), true)
            return
        }
        restoreWithConflictHandling(item, parent, dest.name, onRestore, onFinished)
    }

    @SuppressLint("CheckResult")
    private fun chooseDirectoryThenRestore(item: TrashEntities.TrashItem, fileName: String, onRestore: (() -> Unit)? = null, onFinished: (() -> Unit)? = null) {
        FileChooserDialogBuilder(context)
            .title(R.string.dialog_button_choose_path)
            .dir(INTERNAL_STORAGE_ROOT)
            .chooseDir()
            .singleChoice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ dir ->
                restoreWithConflictHandling(item, File(dir.path), fileName, onRestore, onFinished)
            }, { e ->
                e.printStackTrace()
                ViewUtils.showToast(context, e.message, true)
            })
    }

    private fun restoreWithConflictHandling(item: TrashEntities.TrashItem, destDir: File, fileName: String, onRestore: (() -> Unit)? = null, onFinished: (() -> Unit)? = null) {
        val dest = File(destDir, fileName)

        if (!dest.exists()) {
            restoreNow(item, dest, onRestore, onFinished)
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
                    restoreNow(item, File(destDir, newName), onRestore, onFinished)
                }
                .positiveText(R.string.text_overwrite)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    restoreNow(item, dest, onRestore, onFinished)
                }
                .cancelable(true)
                .build()
        }
    }

    private fun restoreNow(item: TrashEntities.TrashItem, dest: File, onRestore: (() -> Unit)? = null, onFinished: (() -> Unit)? = null) {
        val destExistsBeforeRestore = dest.exists()
        Schedulers.io().scheduleDirect {
            runCatching {
                AndroidSchedulers.mainThread().scheduleDirect {
                    onRestore?.invoke()
                }

                withControllableProgressDialog { operationController ->
                    TrashRepository(context.applicationContext).restoreTrashItemToPath(item, dest, operationController)

                    // Notify Explorer to refresh the parent directory after restore.
                    // zh-CN: 恢复成功后通知 Explorer 刷新目标父目录.
                    notifyExplorerParentDirChanged(dest)
                }

                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(context, context.getString(R.string.text_done), true)
                    onFinished?.invoke()
                }
            }.onFailure { e ->
                when (e) {
                    is OperationAbortedException -> {
                        AndroidSchedulers.mainThread().scheduleDirect {
                            ViewUtils.showToast(context, context.getString(R.string.text_operation_aborted), false)
                        }
                        if (!destExistsBeforeRestore && dest.exists()) {
                            dest.deleteRecursively()
                        }
                    }
                    else -> {
                        e.printStackTrace()
                        AndroidSchedulers.mainThread().scheduleDirect {
                            ViewUtils.showToast(context, e.message, true)
                        }
                    }
                }
            }
        }
    }

    @Throws(OperationAbortedException::class)
    private fun withControllableProgressDialog(operation: (OperationController) -> Unit) {
        val controller = OperationController()
        val session = ProgressDialogSession(controller)
        try {
            val builder = MaterialDialog.Builder(context)
                .title(R.string.text_restore)
                .content(R.string.text_restoring)
                .negativeText(R.string.dialog_button_abort)
                .negativeColorRes(R.color.dialog_button_caution)
                .onNegative { d, _ ->
                    controller.cancel()
                }
                .progress(true, 0)
                .progressIndeterminateStyle(true)
                .cancelable(false)
                .canceledOnTouchOutside(false)

            session.scheduleShow(builder)

            controller.throwIfCancelled()
            operation(controller)
            controller.throwIfCancelled()
        } catch (e: OperationAbortedException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            session.dismissSafely()
        }
    }

    private fun notifyExplorerParentDirChanged(dest: File) {
        runCatching {
            val parent = dest.parentFile ?: return
            val page = ExplorerDirPage(ScriptFile(parent.path), null)
            Explorers.workspace().notifyChildrenChanged(page)
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