package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.core.pref.PrefRx
import org.autojs.autojs.storage.history.HistoryCleanupUi
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashExpiry
import org.autojs.autojs.storage.history.TrashPreviewController
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.storage.history.TrashRestoreController
import org.autojs.autojs.ui.settings.TrashAndVersionHistorySettingsActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTrashBinding
import java.io.File
import java.util.Locale

/**
 * Trash page.
 * zh-CN: 回收站页面.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 5, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
class TrashActivity : BaseSearchSortListActivity<ActivityTrashBinding, TrashEntities.TrashItem>() {

    @Volatile
    private var missingBlobIds: Set<String> = emptySet()

    private val adapter: TrashAdapter = TrashAdapter(
        onClick = { item ->
            if (adapter.selectionMode) {
                adapter.toggle(item)
            } else if (item.trashId in missingBlobIds) {
                showBrokenItemDialog(item)
            } else {
                val optionMenuItemPreview = MaterialDialog.OptionMenuItemSpec(getString(R.string.text_preview)) { d ->
                    d.dismiss()
                    TrashPreviewController(this).preview(item)
                }
                val optionMenuItemDetails = MaterialDialog.OptionMenuItemSpec(getString(R.string.text_details)) { d ->
                    d.dismiss()
                    TrashPreviewController(this).showDetails(item)
                }
                val optionMenuItemRemove = MaterialDialog.OptionMenuItemSpec(getString(R.string.text_remove_from_trash)) { d ->
                    confirmAndRemoveFromTrash(item, d)
                }
                TrashRestoreController(this).startRestoreFlow(item, listOf(optionMenuItemPreview, optionMenuItemDetails, optionMenuItemRemove)) {
                    load()
                }
            }
        },
        onLongClick = { item ->
            if (adapter.selectionMode) adapter.toggle(item) else adapter.enterSelection(item)
        },
        onSelectionChanged = { count, selectionMode -> onSelectionChanged(count, selectionMode) },
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            adapter.clearSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    /**
     * Disposables for header observing.
     * zh-CN: header 监听订阅管理器.
     */
    private val headerDisposables: CompositeDisposable = CompositeDisposable()

    /**
     * Latest computed subtitle from observers.
     * zh-CN: 来自监听器的最新 subtitle 文本.
     */
    private var observedSubtitle: String? = null

    override fun onStart() {
        super.onStart()
        startObserveHeaderSubtitleIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        // Avoid leaking Activity via subscriptions.
        // zh-CN: 避免订阅持有 Activity 导致泄露.
        headerDisposables.clear()
    }

    private fun startObserveHeaderSubtitleIfNeeded() {
        headerDisposables.clear()

        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()

        val limitBytesFlow = PrefRx.observeLong(HistoryKeys.TRASH_MAX_TOTAL_BYTES)

        val disposable = Observable
            .combineLatest(
                dao.observeTrashStats().toObservable(),
                limitBytesFlow.toObservable(),
            ) { stats, limitBytes ->
                StorageUsageSummaryFormatter.formatHeader(
                    context = this,
                    count = stats.count,
                    totalBytes = (stats.totalBytes ?: 0L),
                    limitBytes = limitBytes,
                )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                observedSubtitle = subtitle
                toolbar.subtitle = subtitle
            }

        headerDisposables.add(disposable)
    }

    override fun createBinding(): ActivityTrashBinding {
        return ActivityTrashBinding.inflate(layoutInflater)
    }

    override fun provideViews(binding: ActivityTrashBinding): ListPageViews {
        return ListPageViews(
            toolbar = binding.toolbar,
            recyclerView = binding.recyclerView,
            swipeRefreshLayout = binding.swipeRefreshLayout,
            emptyHint = binding.emptyHint,
        )
    }

    override fun getToolbarTitleText(): String {
        return getString(R.string.text_trash)
    }

    override fun menuResId(): Int {
        return R.menu.menu_trash
    }

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> {
        return adapter
    }

    override fun submitToAdapter(items: List<TrashEntities.TrashItem>) {
        // Derived state travels with the row so DiffUtil sees countdown / broken-state changes.
        // zh-CN: 派生状态随行一起提交, 使 DiffUtil 能感知倒计时 / 失效状态的变化.
        val maxDays = HistoryKeys.trashMaxDays()
        val now = System.currentTimeMillis()
        val missing = missingBlobIds
        adapter.submit(items.map { item ->
            TrashRow(
                item = item,
                missing = item.trashId in missing,
                remaining = TrashExpiry.expiresAtOrNull(item.trashedAt, maxDays)?.let { TrashExpiry.remaining(it, now) },
            )
        })
    }

    override fun loadAllItemsInBackground(): List<TrashEntities.TrashItem> {
        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
        val repository = TrashRepository(applicationContext)
        val items = dao.listTrashItemsDesc()
        // Broken items (blob missing) are detected on every load and only marked, never removed here.
        // zh-CN: 每次加载都检测失效条目 (blob 缺失), 仅标记, 不在此删除.
        missingBlobIds = items.filter { repository.isBlobMissing(it) }.mapTo(HashSet()) { it.trashId }
        return items
    }

    override fun matchesQuery(item: TrashEntities.TrashItem, qLower: String): Boolean {
        val path = item.originalPath
        val name = File(path).name
        val locale = Locale.getDefault()
        return path.lowercase(locale).contains(qLower) || name.lowercase(locale).contains(qLower)
    }

    override fun sortItems(
        items: List<TrashEntities.TrashItem>,
        sortMode: CommonSortMode,
    ): List<TrashEntities.TrashItem> {
        val locale = Locale.getDefault()
        return when (sortMode) {
            CommonSortMode.TIME_DESC -> items.sortedByDescending { it.trashedAt }
            CommonSortMode.TIME_ASC -> items.sortedBy { it.trashedAt }

            CommonSortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            CommonSortMode.SIZE_ASC -> items.sortedBy { it.sizeBytes }

            CommonSortMode.NAME_ASC -> items.sortedBy { File(it.originalPath).name.lowercase(locale) }
            CommonSortMode.PATH_ASC -> items.sortedBy { it.originalPath.lowercase(locale) }
        }
    }

    override fun updateStats(
        allItems: List<TrashEntities.TrashItem>,
        shownItems: List<TrashEntities.TrashItem>,
    ) {
        // Header subtitle is driven by Room observe + Pref observe.
        // zh-CN: header subtitle 由 Room 监听 + Pref 监听驱动.
        if (adapter.selectionMode) return
        observedSubtitle?.let { toolbar.subtitle = it }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val selecting = adapter.selectionMode
        menu.findItem(R.id.action_remove_selected)?.isVisible = selecting
        menu.findItem(R.id.action_restore_selected)?.isVisible = selecting
        menu.findItem(R.id.action_select_all)?.isVisible = selecting
        menu.findItem(R.id.action_deselect_all)?.isVisible = selecting
        menu.findItem(R.id.action_search)?.isVisible = !selecting
        menu.findItem(R.id.action_sort)?.isVisible = !selecting
        menu.findItem(R.id.action_clear_trash)?.isVisible = !selecting
        menu.findItem(R.id.action_remove_broken_items)?.isVisible = !selecting
        menu.findItem(R.id.action_clean_up_now)?.isVisible = !selecting
        menu.findItem(R.id.action_trash_and_version_history_settings)?.isVisible = !selecting
        return super.onPrepareOptionsMenu(menu)
    }

    private fun onSelectionChanged(count: Int, selectionMode: Boolean) {
        backCallback.isEnabled = selectionMode
        if (selectionMode) {
            toolbar.title = getString(R.string.text_selected_items, count)
            toolbar.subtitle = null
        } else {
            toolbar.title = getToolbarTitleText()
            observedSubtitle?.let { toolbar.subtitle = it }
        }
        invalidateOptionsMenu()
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_all -> true.also { adapter.selectAll() }
            R.id.action_deselect_all -> true.also { adapter.clearSelection() }
            R.id.action_restore_selected -> true.also {
                TrashRestoreController(this).startBatchRestoreFlow(adapter.selectedItems()) {
                    adapter.clearSelection()
                    load()
                }
            }
            R.id.action_remove_selected -> true.also { confirmAndRemoveSelectedFromTrash(adapter.selectedItems()) }
            R.id.action_remove_broken_items -> true.also { confirmAndRemoveBrokenItems() }
            R.id.action_clear_trash -> true.also { confirmAndClearTrash() }
            R.id.action_clean_up_now -> true.also { HistoryCleanupUi.runNow(this) { load() } }
            R.id.action_trash_and_version_history_settings -> true.also { TrashAndVersionHistorySettingsActivity.launch(this) }
            else -> false
        }
    }

    private fun confirmAndRemoveFromTrash(item: TrashEntities.TrashItem, parentDialog: MaterialDialog? = null) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_remove_from_trash_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    parentDialog?.dismiss()
                    removeFromTrash(item)
                }
                .cancelable(true)
                .build()
        }
    }

    private fun removeFromTrash(item: TrashEntities.TrashItem) {
        Schedulers.io().scheduleDirect {
            runCatching {
                TrashRepository(applicationContext).deleteTrashItem(item)
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, getString(R.string.text_already_deleted))
                    load()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, it.message, true)
                }
            }
        }
    }

    private fun confirmAndRemoveSelectedFromTrash(items: List<TrashEntities.TrashItem>) {
        if (items.isEmpty()) return
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(getString(R.string.text_remove_selected_from_trash_confirm, items.size))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> removeSelectedFromTrash(items) }
                .cancelable(true)
                .build()
        }
    }

    private fun removeSelectedFromTrash(items: List<TrashEntities.TrashItem>) {
        Schedulers.io().scheduleDirect {
            val repository = TrashRepository(applicationContext)
            var failed = 0
            items.forEach { item ->
                runCatching { repository.deleteTrashItem(item) }.onFailure {
                    it.printStackTrace()
                    failed++
                }
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                if (failed == 0) {
                    ViewUtils.showToast(this, getString(R.string.text_already_deleted))
                } else {
                    ViewUtils.showToast(this, "${getString(R.string.text_failed)}: $failed", true)
                }
                adapter.clearSelection()
                load()
            }
        }
    }

    /**
     * A broken item cannot be previewed or restored; only details and removal are offered.
     * zh-CN: 失效条目不能预览或恢复; 仅提供详情与移除.
     */
    private fun showBrokenItemDialog(item: TrashEntities.TrashItem) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(File(item.originalPath).name)
                .content(R.string.error_trash_data_missing)
                .neutralText(R.string.text_details)
                .neutralColorRes(R.color.dialog_button_default)
                .onNeutral { d, _ ->
                    d.dismiss()
                    TrashPreviewController(this).showDetails(item)
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_remove_from_trash)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { d, _ -> confirmAndRemoveFromTrash(item, d) }
                .autoDismiss(false)
                .cancelable(true)
                .build()
        }
    }

    private fun confirmAndRemoveBrokenItems() {
        Schedulers.io().scheduleDirect {
            val broken = runCatching { TrashRepository(applicationContext).listTrashItemsWithMissingBlobs() }
                .onFailure { it.printStackTrace() }
                .getOrDefault(emptyList())
            AndroidSchedulers.mainThread().scheduleDirect {
                if (isFinishing || isDestroyed) return@scheduleDirect
                if (broken.isEmpty()) {
                    ViewUtils.showToast(this, R.string.text_no_broken_items)
                    return@scheduleDirect
                }
                DialogUtils.buildAndShowAdaptive {
                    MaterialDialog.Builder(this)
                        .title(R.string.text_prompt)
                        .content(getString(R.string.text_remove_broken_items_confirm, broken.size))
                        .negativeText(R.string.dialog_button_cancel)
                        .negativeColorRes(R.color.dialog_button_default)
                        .positiveText(R.string.dialog_button_confirm)
                        .positiveColorRes(R.color.dialog_button_caution)
                        .onPositive { _, _ -> removeSelectedFromTrash(broken) }
                        .cancelable(true)
                        .build()
                }
            }
        }
    }

    private fun confirmAndClearTrash() {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_clear_trash_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> clearTrash() }
                .cancelable(true)
                .build()
        }
    }

    private fun clearTrash() {
        Schedulers.io().scheduleDirect {
            runCatching {
                TrashRepository(applicationContext).clearTrash()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showSnack(recyclerView, getString(R.string.text_done), true)
                    load()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showSnack(recyclerView, it.message, true)
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, TrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
