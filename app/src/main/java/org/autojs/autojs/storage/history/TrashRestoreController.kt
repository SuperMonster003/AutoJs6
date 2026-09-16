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

/**
 * Restore flow controller for trash items.
 * zh-CN: 回收站条目的恢复流程控制器.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
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

    /**
     * Restore several items in one go: destination (original parents or one chosen directory),
     * then a single conflict policy for the batch (skip / overwrite / auto-rename), then an
     * abortable progress dialog. Items sharing a name inside the batch are always auto-renamed.
     *
     * zh-CN: 批量恢复: 先选目标 (各自原父目录或统一目录), 有冲突时为整批选择一种策略 (跳过 / 覆盖 / 自动改名),
     * 然后在可中止的进度对话框中执行. 批次内同名条目一律自动改名.
     */
    fun startBatchRestoreFlow(items: List<TrashEntities.TrashItem>, onFinished: (() -> Unit)? = null) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            startRestoreFlow(items[0], emptyList(), onFinished)
            return
        }
        val selected = intArrayOf(0)
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_choose_restore_path)
                .content(context.getString(R.string.text_n_items, items.size))
                .items(
                    context.getString(R.string.text_restore_to_original_path),
                    context.getString(R.string.text_restore_to_specified_path),
                )
                .itemsCallbackSingleChoice(0) { _, _, which, _ ->
                    selected[0] = which
                    true
                }
                .alwaysCallSingleChoiceCallback()
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive { _, _ ->
                    when (selected[0]) {
                        0 -> planBatchThenAskConflicts(items, null, onFinished)
                        else -> chooseDirectoryThenBatchRestore(items, onFinished)
                    }
                }
                .cancelable(true)
                .build()
        }
    }

    private fun chooseDirectoryThenBatchRestore(items: List<TrashEntities.TrashItem>, onFinished: (() -> Unit)?) {
        FileChooserDialogBuilder(context)
            .title(R.string.dialog_button_choose_path)
            .dir(HistoryPaths.internalStorageRoot)
            .chooseDir()
            .singleChoice()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ dir ->
                planBatchThenAskConflicts(items, dir.path, onFinished)
            }, { e ->
                e.printStackTrace()
                ViewUtils.showToast(context, e.message, true)
            })
    }

    private fun planBatch(
        items: List<TrashEntities.TrashItem>,
        targetDir: String?,
        policy: TrashBatchPlanner.ConflictPolicy,
    ): List<TrashBatchPlanner.Step> = TrashBatchPlanner.plan(
        entries = items.map { TrashBatchPlanner.Entry(it.trashId, it.originalPath, it.isDirectory) },
        targetDir = targetDir,
        policy = policy,
        exists = { File(it).exists() },
        siblingNames = { dir -> File(dir).list()?.toList() ?: emptyList() },
    )

    private fun planBatchThenAskConflicts(items: List<TrashEntities.TrashItem>, targetDir: String?, onFinished: (() -> Unit)?) {
        Schedulers.io().scheduleDirect {
            val conflicts = runCatching {
                planBatch(items, targetDir, TrashBatchPlanner.ConflictPolicy.SKIP).count { it.conflict }
            }.getOrElse {
                it.printStackTrace()
                0
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                if (conflicts == 0) {
                    executeBatchRestore(items, targetDir, TrashBatchPlanner.ConflictPolicy.SKIP, onFinished)
                    return@scheduleDirect
                }
                DialogUtils.buildAndShowAdaptive {
                    MaterialDialog.Builder(context)
                        .title(R.string.text_prompt)
                        .content(context.getString(R.string.text_n_items_conflict, conflicts, items.size))
                        .options(listOf(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_cancel)) { d -> d.dismiss() }))
                        .negativeText(R.string.text_skip_conflicting)
                        .negativeColorRes(R.color.dialog_button_default)
                        .onNegative { _, _ -> executeBatchRestore(items, targetDir, TrashBatchPlanner.ConflictPolicy.SKIP, onFinished) }
                        .neutralText(R.string.text_overwrite)
                        .neutralColorRes(R.color.dialog_button_caution)
                        .onNeutral { _, _ -> executeBatchRestore(items, targetDir, TrashBatchPlanner.ConflictPolicy.OVERWRITE, onFinished) }
                        .positiveText(R.string.text_auto_rename)
                        .positiveColorRes(R.color.dialog_button_attraction)
                        .onPositive { _, _ -> executeBatchRestore(items, targetDir, TrashBatchPlanner.ConflictPolicy.AUTO_RENAME, onFinished) }
                        .cancelable(true)
                        .build()
                }
            }
        }
    }

    private fun executeBatchRestore(
        items: List<TrashEntities.TrashItem>,
        targetDir: String?,
        policy: TrashBatchPlanner.ConflictPolicy,
        onFinished: (() -> Unit)?,
    ) {
        val appCtx = context.applicationContext
        Schedulers.io().scheduleDirect {
            var restored = 0
            var skipped = 0
            var failed = 0
            val aborted = runCatching {
                val steps = planBatch(items, targetDir, policy)
                skipped = steps.count { it.skipped }
                val work = steps.filter { !it.skipped }
                val byId = items.associateBy { it.trashId }
                withBatchProgressDialog(work.size) { operationController, session ->
                    val repository = TrashRepository(appCtx)
                    work.forEachIndexed { index, step ->
                        operationController.throwIfCancelled()
                        val item = byId.getValue(step.entry.id)
                        val dest = File(step.dest)
                        val destExistedBefore = dest.exists()
                        session.setContentThrottled(mutableListOf("${index + 1} / ${work.size}", dest.name))
                        session.setProgressThrottled(index.toLong(), work.size.toLong())
                        try {
                            if (destExistedBefore && !item.isDirectory && dest.isFile) {
                                HistoryRepository(appCtx).recordSnapshotBeforeOverwrite(
                                    path = dest.path,
                                    op = HistoryRepository.OP_RESTORE_PRE,
                                    newContentSha256 = item.sha256,
                                )
                            }
                            repository.restoreTrashItemToPath(item, dest, operationController)
                            notifyExplorerParentDirChanged(dest)
                            restored++
                        } catch (e: OperationAbortedException) {
                            if (!destExistedBefore && dest.exists()) dest.deleteRecursively()
                            throw e
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            failed++
                        }
                    }
                    session.setProgressThrottled(work.size.toLong(), work.size.toLong())
                }
                false
            }.getOrElse { e ->
                if (e is OperationAbortedException) {
                    true
                } else {
                    e.printStackTrace()
                    failed++
                    false
                }
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                if (aborted) {
                    ViewUtils.showToast(context, context.getString(R.string.text_operation_aborted), false)
                }
                ViewUtils.showToast(context, context.getString(R.string.text_batch_restore_summary, restored, skipped, failed), true)
                onFinished?.invoke()
            }
        }
    }

    /**
     * Determinate, abortable progress dialog for a batch; failures inside [operation] propagate.
     * zh-CN: 批量操作用的可中止确定进度对话框; [operation] 内的失败会向外抛出.
     */
    @Throws(OperationAbortedException::class)
    private fun withBatchProgressDialog(total: Int, operation: (OperationController, ProgressDialogSession) -> Unit) {
        val controller = OperationController()
        val session = ProgressDialogSession(controller)
        try {
            val builder = MaterialDialog.Builder(context)
                .title(R.string.text_restore)
                .content(R.string.text_restoring)
                .negativeText(R.string.dialog_button_abort)
                .negativeColorRes(R.color.dialog_button_caution)
                .onNegative { _, _ -> controller.cancel() }
                .progress(false, total.coerceAtLeast(1))
                .cancelable(false)
                .canceledOnTouchOutside(false)
            session.scheduleShow(builder)
            controller.throwIfCancelled()
            operation(controller, session)
        } finally {
            session.dismissSafely()
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
            .dir(HistoryPaths.internalStorageRoot)
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
                    val siblings = destDir.list()?.toList() ?: emptyList()
                    val newName = TrashRestoreNaming.nextIndexedName(siblings, fileName, item.isDirectory)
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
                    val appCtx = context.applicationContext

                    // Overwriting an existing file: keep its current content as a history revision first.
                    // zh-CN: 覆盖已有文件前, 先把其当前内容记为一条历史 revision.
                    if (destExistsBeforeRestore && !item.isDirectory && dest.isFile) {
                        HistoryRepository(appCtx).recordSnapshotBeforeOverwrite(
                            path = dest.path,
                            op = HistoryRepository.OP_RESTORE_PRE,
                            newContentSha256 = item.sha256,
                        )
                    }

                    TrashRepository(appCtx).restoreTrashItemToPath(item, dest, operationController)

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
                            ViewUtils.showToast(context, TrashFailureText.describe(context, e), true)
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
            // Propagate so the caller reports the failure instead of a misleading "done".
            // zh-CN: 向外抛出, 让调用方报告失败而不是误报 "完成".
            e.printStackTrace()
            throw e
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

}