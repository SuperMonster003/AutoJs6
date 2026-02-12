package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.core.pref.PrefRx
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityVersionHistoryBinding
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Version history page (all files).
 * zh-CN: 版本历史页面 (全部文件).
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 */
class VersionHistoryActivity : BaseSearchSortListActivity<ActivityVersionHistoryBinding, HistoryEntities.FileEntry>() {

    /**
     * Cached aggregated bytes for fileId.
     * zh-CN: fileId 对应的聚合字节数缓存.
     */
    private val sizeCache: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Cached revision count for fileId.
     * zh-CN: fileId 对应的 revision 数量缓存.
     */
    private val countCache: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    /**
     * Whether caches have been populated once.
     * zh-CN: 缓存是否已至少完成一次填充.
     */
    private val sizeReady: AtomicBoolean = AtomicBoolean(false)

    /**
     * Prevent duplicated async loading.
     * zh-CN: 防止重复触发异步加载.
     */
    private val sizeLoading: AtomicBoolean = AtomicBoolean(false)

    private val adapter = VersionHistoryFileAdapter(
        onClick = { entry ->
            VersionHistoryController(this).showForFilePath(entry.canonicalPath, entry.displayPath)
        },
        onLongClick = { entry ->
            confirmAndClearHistoryForEntry(entry)
        },
        getSizeBytesOrNull = { fileId ->
            sizeCache[fileId]
        },
        getRevisionCountOrNull = { fileId ->
            countCache[fileId]
        },
    )

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

        val limitBytesFlow = PrefRx.observeLong(
            keyRes = R.string.key_history_max_total_bytes,
            defaultValue = HistoryPrefs.DEFAULT_HISTORY_MAX_TOTAL_BYTES,
        )

        val disposable = Observable
            .combineLatest(
                dao.observeVersionHistoryStats().toObservable(),
                limitBytesFlow.toObservable(),
            ) { stats, limitBytes ->
                StorageUsageSummaryFormatter.formatHeader(
                    context = this,
                    count = stats.fileCount,
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

    override fun createBinding(): ActivityVersionHistoryBinding {
        return ActivityVersionHistoryBinding.inflate(layoutInflater)
    }

    override fun provideViews(binding: ActivityVersionHistoryBinding): ListPageViews {
        return ListPageViews(
            toolbar = binding.toolbar,
            recyclerView = binding.recyclerView,
            swipeRefreshLayout = binding.swipeRefreshLayout,
            emptyHint = binding.emptyHint,
        )
    }

    override fun getToolbarTitleText(): String {
        return getString(R.string.text_version_history)
    }

    override fun menuResId(): Int {
        return R.menu.menu_version_history
    }

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> {
        return adapter
    }

    override fun submitToAdapter(items: List<HistoryEntities.FileEntry>) {
        adapter.submit(items)
    }

    override fun loadAllItemsInBackground(): List<HistoryEntities.FileEntry> {
        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()

        // Default sorting: latest seen first.
        // zh-CN: 默认排序: 最近访问优先.
        return dao.listAllFiles().sortedByDescending { it.lastSeenAt }
    }

    override fun onItemsLoaded(items: List<HistoryEntities.FileEntry>) {
        // Recompute stats on each load (first enter / swipe refresh).
        // zh-CN: 每次 load 后都重新计算聚合统计 (首次进入/下拉刷新).
        sizeCache.clear()
        countCache.clear()
        sizeReady.set(false)
        sizeLoading.set(false)

        // Refresh per-row display immediately (show "Calculating...").
        // zh-CN: 立即刷新每行显示 (展示 "Calculating...").
        adapter.notifyDataSetChanged()

        ensureStatsLoadedAsync(forceRecompute = true)
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_version_history -> true.also { confirmAndClearAllHistory() }
            else -> false
        }
    }

    override fun setSortMode(mode: CommonSortMode) {
        internalSortMode = mode

        val needSize = mode == CommonSortMode.SIZE_DESC || mode == CommonSortMode.SIZE_ASC
        if (needSize && !sizeReady.get()) {
            // Strict mode: wait for real size before sorting by size.
            // zh-CN: 严格模式: size 未就绪时, 切到按大小排序需等待真实 size 后再排序.
            swipeRefreshLayout.isRefreshing = true
            ensureStatsLoadedAsync(forceRecompute = false)
            updateStats(allItems = allItems, shownItems = emptyList())
            return
        }

        applyFilterAndSort()
    }

    /**
     * Ensure aggregated stats (size + count) loaded.
     * zh-CN: 确保聚合统计 (size + count) 已加载.
     */
    private fun ensureStatsLoadedAsync(forceRecompute: Boolean) {
        if (!forceRecompute && sizeReady.get()) return
        if (!sizeLoading.compareAndSet(false, true)) return

        Schedulers.io().scheduleDirect {
            runCatching {
                val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
                val fileIds = allItems.map { it.fileId }
                val rows = if (fileIds.isEmpty()) emptyList() else dao.aggStatsByFileIds(fileIds)

                val sizeMap = HashMap<String, Long>(rows.size)
                val countMap = HashMap<String, Long>(rows.size)

                for (row in rows) {
                    sizeMap[row.fileId] = (row.totalBytes ?: 0L).coerceAtLeast(0L)
                    countMap[row.fileId] = row.revisionCount.coerceAtLeast(0L)
                }

                Pair(sizeMap, countMap)
            }.onSuccess { (sizeMap, countMap) ->
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (isFinishing || isDestroyed) return@scheduleDirect

                    // Replace caches with newly computed values.
                    // zh-CN: 使用新计算结果替换缓存.
                    sizeCache.clear()
                    sizeCache.putAll(sizeMap)

                    countCache.clear()
                    countCache.putAll(countMap)

                    sizeReady.set(true)
                    sizeLoading.set(false)

                    // Refresh per-row display.
                    // zh-CN: 刷新每行显示.
                    adapter.notifyDataSetChanged()

                    // If current mode is size sort, now apply strict sorting.
                    // zh-CN: 若当前为按大小排序, 则此时才允许应用严格排序.
                    val needSize = internalSortMode == CommonSortMode.SIZE_DESC || internalSortMode == CommonSortMode.SIZE_ASC
                    if (needSize) {
                        applyFilterAndSort()
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                        updateStats(allItems = allItems, shownItems = emptyList())
                    }
                }
            }.onFailure { t ->
                t.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    sizeLoading.set(false)
                    swipeRefreshLayout.isRefreshing = false

                    // Keep UI usable even if stats calculation failed.
                    // zh-CN: 统计计算失败时仍保持 UI 可用.
                    applyFilterAndSort()
                }
            }
        }
    }

    override fun matchesQuery(item: HistoryEntities.FileEntry, qLower: String): Boolean {
        val path = item.displayPath
        val name = File(path).name
        val locale = Locale.getDefault()
        return path.lowercase(locale).contains(qLower) || name.lowercase(locale).contains(qLower)
    }

    override fun sortItems(
        items: List<HistoryEntities.FileEntry>,
        sortMode: CommonSortMode,
    ): List<HistoryEntities.FileEntry> {
        val locale = Locale.getDefault()
        return when (sortMode) {
            CommonSortMode.TIME_DESC -> items.sortedByDescending { it.lastSeenAt }
            CommonSortMode.TIME_ASC -> items.sortedBy { it.lastSeenAt }

            CommonSortMode.SIZE_DESC -> items.sortedWith(
                compareByDescending<HistoryEntities.FileEntry> { sizeCache[it.fileId] ?: 0L }
                    .thenBy { it.canonicalPath.lowercase(locale) }
            )

            CommonSortMode.SIZE_ASC -> items.sortedWith(
                compareBy<HistoryEntities.FileEntry> { sizeCache[it.fileId] ?: 0L }
                    .thenBy { it.canonicalPath.lowercase(locale) }
            )

            CommonSortMode.NAME_ASC -> items.sortedBy { File(it.canonicalPath).name.lowercase(locale) }
            CommonSortMode.PATH_ASC -> items.sortedBy { it.canonicalPath.lowercase(locale) }
        }
    }

    override fun updateStats(
        allItems: List<HistoryEntities.FileEntry>,
        shownItems: List<HistoryEntities.FileEntry>,
    ) {
        // Header subtitle is driven by Room observe + Pref observe.
        // zh-CN: header subtitle 由 Room 监听 + Pref 监听驱动.
        observedSubtitle?.let { toolbar.subtitle = it }
    }

    /**
     * Confirm clearing all history.
     * zh-CN: 确认清空全部历史记录.
     */
    private fun confirmAndClearAllHistory() {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_clear_version_history_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> clearAllHistory() }
                .cancelable(true)
                .build()
        }
    }

    /**
     * Clear all history in background.
     * zh-CN: 在后台线程清空全部历史记录.
     */
    private fun clearAllHistory() {
        Schedulers.io().scheduleDirect {
            runCatching {
                HistoryRepository(applicationContext).clearAllHistory()
            }.onSuccess {
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, getString(R.string.text_history_has_been_cleared), true)
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

    /**
     * Confirm clearing history for one file entry (all revisions).
     * zh-CN: 确认清除单个条目对应的历史记录 (全部 revisions).
     */
    private fun confirmAndClearHistoryForEntry(entry: HistoryEntities.FileEntry) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(getString(R.string.text_confirm_to_clear_the_history_of, entry.displayPath))
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> clearHistoryForEntry(entry) }
                .cancelable(true)
                .build()
        }
    }

    /**
     * Clear history for one entry in background.
     * zh-CN: 在后台线程清除单个条目的历史记录.
     */
    private fun clearHistoryForEntry(entry: HistoryEntities.FileEntry) {
        Schedulers.io().scheduleDirect {
            runCatching {
                HistoryRepository(applicationContext).clearHistoryForPath(entry.canonicalPath)
            }.onSuccess {
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, getString(R.string.text_history_has_been_cleared), true)
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

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(
                Intent(context, VersionHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
