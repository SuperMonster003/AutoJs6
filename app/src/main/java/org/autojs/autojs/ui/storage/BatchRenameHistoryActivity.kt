package org.autojs.autojs.ui.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.plugin.batchrename.BatchRenameBatchState
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHistoryPrefs
import org.autojs.autojs.core.plugin.batchrename.BatchRenameHostServices
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalRecord
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalStore
import org.autojs.autojs.core.plugin.batchrename.BatchRenameSource
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTrashBinding
import java.io.File
import java.util.Locale

/**
 * Rename history page (Batch Rename plugin roadmap D38 / D39 / D51): the batches of the rename journal shared by
 * `files.batchRename` and the file manager, searchable by old or new name and filtered by source and state. A batch
 * shows its entries and can be undone or deleted; the menu clears the history and sets the batches kept per source.
 * Reached from the "More" menu of the rename dialog, and from the plugin through
 * [org.autojs.plugin.batchrename.api.BatchRenamePluginActions.RENAME_HISTORY].
 *
 * zh-CN: 重命名历史页面 (Batch Rename 插件路线图 D38 / D39 / D51): `files.batchRename` 与文件管理器共用的改名日志中的批次, 可按旧名或
 * 新名搜索, 按来源与状态筛选. 批次可查看条目, 撤销或删除; 菜单可清空历史并设置每个来源保留的批数. 入口为重命名对话框的 "更多" 菜单,
 * 插件经 [org.autojs.plugin.batchrename.api.BatchRenamePluginActions.RENAME_HISTORY] 打开.
 */
class BatchRenameHistoryActivity : BaseSearchSortListActivity<ActivityTrashBinding, BatchRenameJournalRecord>() {

    private val journal: BatchRenameJournalStore by lazy { BatchRenameHostServices.journal(applicationContext) }

    private val adapter = BatchRenameHistoryAdapter { record -> showDetails(record) }

    private var sourceFilter: BatchRenameSource? = null
    private var stateFilter: BatchRenameBatchState? = null

    override fun createBinding(): ActivityTrashBinding = ActivityTrashBinding.inflate(layoutInflater)

    override fun provideViews(binding: ActivityTrashBinding) = ListPageViews(
        toolbar = binding.toolbar,
        recyclerView = binding.recyclerView,
        swipeRefreshLayout = binding.swipeRefreshLayout,
        emptyHint = binding.emptyHint,
    )

    override fun getToolbarTitleText(): String = getString(R.string.text_rename_history)

    override fun menuResId(): Int = R.menu.menu_batch_rename_history

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> = adapter

    override fun submitToAdapter(items: List<BatchRenameJournalRecord>) = adapter.submitList(items)

    override fun loadAllItemsInBackground(): List<BatchRenameJournalRecord> = journal.list()

    override fun matchesFilters(item: BatchRenameJournalRecord): Boolean =
        (sourceFilter == null || item.source == sourceFilter) && (stateFilter == null || item.state == stateFilter)

    override fun matchesQuery(item: BatchRenameJournalRecord, qLower: String): Boolean {
        val locale = Locale.getDefault()
        return item.entries.any { entry ->
            File(entry.oldPath).name.lowercase(locale).contains(qLower) || File(entry.newPath).name.lowercase(locale).contains(qLower)
        }
    }

    override fun sortItems(items: List<BatchRenameJournalRecord>, sortMode: CommonSortMode): List<BatchRenameJournalRecord> = when (sortMode) {
        CommonSortMode.TIME_ASC -> items.sortedWith(compareBy<BatchRenameJournalRecord> { it.createdAt }.thenBy { it.batchId })
        else -> items.sortedWith(compareByDescending<BatchRenameJournalRecord> { it.createdAt }.thenByDescending { it.batchId })
    }

    /** "12 batches", or "3 / 12 batches" while a filter or the search hides some. zh-CN: 有批次被筛选或搜索隐藏时显示 "3 / 12 批". */
    override fun updateStats(allItems: List<BatchRenameJournalRecord>, shownItems: List<BatchRenameJournalRecord>) {
        val total = resources.getQuantityString(R.plurals.text_rename_history_batches, allItems.size, allItems.size)
        toolbar.subtitle = if (shownItems.size == allItems.size) total else "${shownItems.size} / $total"
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        SOURCE_FILTERS.forEach { (id, source) -> menu.findItem(id)?.isChecked = source == sourceFilter }
        STATE_FILTERS.forEach { (id, state) -> menu.findItem(id)?.isChecked = state == stateFilter }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when {
            id in SOURCE_FILTERS -> sourceFilter = SOURCE_FILTERS[id]
            id in STATE_FILTERS -> stateFilter = STATE_FILTERS[id]
            id == R.id.action_rename_history_retention -> return true.also { chooseRetention() }
            id == R.id.action_rename_history_clear -> return true.also { confirmAndClear() }
            else -> return false
        }
        item.isChecked = true
        applyFilterAndSort()
        return true
    }

    private fun showDetails(record: BatchRenameJournalRecord) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(BatchRenameHistoryTexts.time(record.createdAt))
                .content(BatchRenameHistoryTexts.details(this, record))
                .apply {
                    if (record.undoable) {
                        positiveText(R.string.text_rename_history_undo)
                        positiveColorRes(R.color.dialog_button_attraction)
                        onPositive { d, _ ->
                            d.dismiss()
                            undo(record)
                        }
                    }
                }
                .neutralText(R.string.text_delete)
                .neutralColorRes(R.color.dialog_button_caution)
                .onNeutral { d, _ -> confirmAndDelete(record, d) }
                .negativeText(R.string.dialog_button_close)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .autoDismiss(false)
                .cancelable(true)
                .build()
        }
    }

    /**
     * Undoes [record] off the main thread; entries that cannot be restored are shown in the details again.
     * zh-CN: 在主线程以外撤销 [record]; 有条目未能恢复时再次显示明细.
     */
    private fun undo(record: BatchRenameJournalRecord) {
        val progress = DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .content(R.string.text_rename_history_undoing)
                .progress(true, 0)
                .progressIndeterminateStyle(true)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .build()
        }
        Schedulers.io().scheduleDirect {
            val result = runCatching { BatchRenameHostServices.executor(applicationContext).undo(record.batchId) }
            AndroidSchedulers.mainThread().scheduleDirect {
                progress.dismiss()
                if (isFinishing || isDestroyed) return@scheduleDirect
                result.onSuccess { updated ->
                    when {
                        updated == null -> ViewUtils.showToast(this, R.string.text_rename_history_undo_unavailable, true)
                        updated.state == BatchRenameBatchState.RESTORED -> ViewUtils.showToast(this, R.string.text_rename_history_state_restored)
                        else -> {
                            ViewUtils.showToast(this, R.string.text_rename_history_undo_incomplete, true)
                            showDetails(updated)
                        }
                    }
                }.onFailure { error ->
                    error.printStackTrace()
                    ViewUtils.showToast(this, error.message ?: error.toString(), true)
                }
                load()
            }
        }
    }

    private fun confirmAndDelete(record: BatchRenameJournalRecord, parentDialog: MaterialDialog) {
        confirm(R.string.text_rename_history_delete_confirm) {
            parentDialog.dismiss()
            inBackground({ journal.delete(record.batchId) }) { ViewUtils.showToast(this, R.string.text_already_deleted) }
        }
    }

    private fun confirmAndClear() {
        confirm(R.string.text_rename_history_clear_confirm) {
            inBackground({ journal.clear() }) { ViewUtils.showToast(this, R.string.text_done) }
        }
    }

    /**
     * The batches kept per source (plugin roadmap D39); a lower choice deletes the older batches at once.
     * zh-CN: 每个来源保留的批数 (插件路线图 D39); 调低时立即删除更早的批次.
     */
    private fun chooseRetention() {
        val choices = BatchRenameJournalStore.RETENTION_CHOICES
        val current = BatchRenameHistoryPrefs.retention()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_rename_history_retention)
                .content(R.string.text_rename_history_retention_hint)
                .items(choices.map(Int::toString))
                .itemsCallbackSingleChoice(choices.indexOf(current)) { _, _, which, _ ->
                    val chosen = choices[which]
                    if (chosen != current) {
                        BatchRenameHistoryPrefs.setRetention(chosen)
                        inBackground({ journal.trim() }) { ViewUtils.showToast(this, R.string.text_done) }
                    }
                    true
                }
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .cancelable(true)
                .build()
        }
    }

    private fun confirm(content: Int, onConfirm: () -> Unit) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> onConfirm() }
                .cancelable(true)
                .build()
        }
    }

    /** Runs [work] off the main thread, then [done] and a reload. zh-CN: 在主线程以外执行 [work], 之后执行 [done] 并重新加载. */
    private fun inBackground(work: () -> Unit, done: () -> Unit) {
        Schedulers.io().scheduleDirect {
            val result = runCatching(work)
            AndroidSchedulers.mainThread().scheduleDirect {
                if (isFinishing || isDestroyed) return@scheduleDirect
                result.onSuccess { done() }.onFailure { error ->
                    error.printStackTrace()
                    ViewUtils.showToast(this, error.message ?: error.toString(), true)
                }
                load()
            }
        }
    }

    companion object {

        private val SOURCE_FILTERS: Map<Int, BatchRenameSource?> = mapOf(
            R.id.action_rename_history_source_all to null,
            R.id.action_rename_history_source_script to BatchRenameSource.SCRIPT,
            R.id.action_rename_history_source_explorer to BatchRenameSource.EXPLORER,
        )

        private val STATE_FILTERS: Map<Int, BatchRenameBatchState?> = mapOf(
            R.id.action_rename_history_state_all to null,
            R.id.action_rename_history_state_committed to BatchRenameBatchState.COMMITTED,
            R.id.action_rename_history_state_partial to BatchRenameBatchState.PARTIAL,
            R.id.action_rename_history_state_rejected to BatchRenameBatchState.REJECTED,
            R.id.action_rename_history_state_restored to BatchRenameBatchState.RESTORED,
            R.id.action_rename_history_state_recovery_required to BatchRenameBatchState.RECOVERY_REQUIRED,
        )

        fun intent(context: Context): Intent = Intent(context, BatchRenameHistoryActivity::class.java)

        fun launch(context: Context) {
            context.startActivity(intent(context).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
