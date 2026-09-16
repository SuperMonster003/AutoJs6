package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.HistoryTextCodec
import org.autojs.autojs.storage.history.LineDiff
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDiffBinding
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only line diff between two revisions of a tracked file, or between a revision and the
 * file on disk. Unchanged runs are folded with a few context lines and can be expanded by tapping;
 * the search box filters lines by text. Inputs over [LineDiff.exceedsLimits] show statistics only.
 *
 * zh-CN: 已纳管文件两条 revision 之间, 或 revision 与磁盘文件之间的只读行级 diff. 未变更段落折叠并保留少量
 * 上下文, 点击可展开; 搜索框按文本过滤行. 超过 [LineDiff.exceedsLimits] 阈值的输入仅显示统计.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class DiffActivity : BaseSearchSortListActivity<ActivityDiffBinding, DiffRow>() {

    private var canonicalPath: String = ""
    private var displayPath: String = ""
    private var leftRevId: String? = null
    private var rightRevId: String? = null

    private var leftLabel: String = ""
    private var rightLabel: String = ""

    // Populated by the background load; read on the main thread afterwards.
    // zh-CN: 由后台加载填充; 之后在主线程读取.
    @Volatile
    private var loadedStats: LineDiff.Result? = null

    @Volatile
    private var degradedMessage: String? = null

    private val expandedRuns = HashSet<Int>()

    private val adapter = DiffLineAdapter(onExpand = { collapsed ->
        expandedRuns.add(collapsed.fromIndex)
        applyFilterAndSort()
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        canonicalPath = intent?.getStringExtra(EXTRA_PATH).orEmpty()
        displayPath = intent?.getStringExtra(EXTRA_DISPLAY_PATH).orEmpty().ifEmpty { canonicalPath }
        leftRevId = intent?.getStringExtra(EXTRA_LEFT_REV_ID)?.ifEmpty { null }
        rightRevId = intent?.getStringExtra(EXTRA_RIGHT_REV_ID)?.ifEmpty { null }

        super.onCreate(savedInstanceState)

        if (canonicalPath.isEmpty() || (leftRevId == null && rightRevId == null)) {
            finish()
        }
    }

    override fun createBinding(): ActivityDiffBinding = ActivityDiffBinding.inflate(layoutInflater)

    override fun provideViews(binding: ActivityDiffBinding): ListPageViews = ListPageViews(
        toolbar = binding.toolbar,
        recyclerView = binding.recyclerView,
        swipeRefreshLayout = binding.swipeRefreshLayout,
        emptyHint = binding.emptyHint,
    )

    override fun getToolbarTitleText(): String = File(displayPath).name

    override fun menuResId(): Int = R.menu.menu_diff

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> = adapter

    override fun submitToAdapter(items: List<DiffRow>) = adapter.submit(items)

    override fun loadAllItemsInBackground(): List<DiffRow> {
        loadedStats = null
        degradedMessage = null

        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
        val repository = HistoryRepository(applicationContext)

        val leftRev = leftRevId?.let { dao.findRevisionById(it) ?: throw IllegalStateException("Revision not found: $it") }
        val rightRev = rightRevId?.let { dao.findRevisionById(it) ?: throw IllegalStateException("Revision not found: $it") }
        val encodingName = (leftRev ?: rightRev)?.encoding ?: "UTF-8"

        val left = loadSide(repository, leftRev, encodingName)
        val right = loadSide(repository, rightRev, encodingName)
        leftLabel = left.label
        rightLabel = right.label

        val leftLines = LineDiff.splitLines(left.text)
        val rightLines = LineDiff.splitLines(right.text)

        if (LineDiff.exceedsLimits(leftLines.size, rightLines.size, left.bytes, right.bytes)) {
            degradedMessage = buildString {
                append(getString(R.string.text_diff_too_large))
                append("\n\n")
                append(getString(R.string.text_property_colon_value, left.label, getString(R.string.text_lines_and_size, leftLines.size, PFiles.formatSizeWithUnit(left.bytes))))
                append('\n')
                append(getString(R.string.text_property_colon_value, right.label, getString(R.string.text_lines_and_size, rightLines.size, PFiles.formatSizeWithUnit(right.bytes))))
            }
            return emptyList()
        }

        val result = LineDiff.diff(leftLines, rightLines)
        loadedStats = result
        if (!result.hasChanges) return emptyList()
        return result.lines.map { DiffRow.Line(it) }
    }

    private class Side(val label: String, val text: String, val bytes: Long)

    private fun loadSide(repository: HistoryRepository, rev: HistoryEntities.Revision?, encodingName: String): Side {
        if (rev != null) {
            val bytes = repository.readRevisionBytes(rev)
            val label = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(rev.createdAt))
            return Side(label, HistoryTextCodec.decodeRevision(bytes, rev.encoding, rev.hadBom), bytes.size.toLong())
        }
        val file = File(canonicalPath)
        if (!file.isFile) throw FileNotFoundException(displayPath)
        val bytes = file.readBytes()
        return Side(getString(R.string.text_current_file), HistoryTextCodec.decodeFile(bytes, encodingName), bytes.size.toLong())
    }

    override fun onItemsLoaded(items: List<DiffRow>) {
        expandedRuns.clear()
        hint.text = degradedMessage ?: getString(R.string.text_no_differences)
    }

    override fun applyFilterAndSort() {
        val q = queryText.trim()
        val rows = if (q.isBlank()) {
            collapseUnchangedRuns(allItems)
        } else {
            val qLower = q.lowercase(Locale.getDefault())
            allItems.filter { matchesQuery(it, qLower) }
        }
        if (allItems.isNotEmpty()) {
            hint.text = getString(R.string.text_no_data)
        }
        updateHintVisibility(rows)
        swipeRefreshLayout.isRefreshing = false
        submitToAdapter(rows)
        updateStats(allItems = allItems, shownItems = rows)
    }

    /**
     * Fold runs of unchanged lines longer than the context window, unless the user expanded them.
     * zh-CN: 折叠长于上下文窗口的未变更段落, 除非用户已展开.
     */
    private fun collapseUnchangedRuns(all: List<DiffRow>): List<DiffRow> {
        val out = ArrayList<DiffRow>(all.size)
        var i = 0
        while (i < all.size) {
            val row = all[i]
            val isEqual = row is DiffRow.Line && row.line.kind == LineDiff.Kind.EQUAL
            if (!isEqual) {
                out.add(row)
                i++
                continue
            }
            var j = i
            while (j < all.size && (all[j] as? DiffRow.Line)?.line?.kind == LineDiff.Kind.EQUAL) j++
            val runLength = j - i
            val leading = if (i == 0) 0 else CONTEXT_LINES
            val trailing = if (j == all.size) 0 else CONTEXT_LINES
            val hiddenFrom = i + leading
            val hiddenCount = runLength - leading - trailing
            if (hiddenCount <= MIN_LINES_TO_FOLD || expandedRuns.contains(hiddenFrom)) {
                for (k in i until j) out.add(all[k])
            } else {
                for (k in i until hiddenFrom) out.add(all[k])
                out.add(DiffRow.Collapsed(hiddenFrom, hiddenCount))
                for (k in hiddenFrom + hiddenCount until j) out.add(all[k])
            }
            i = j
        }
        return out
    }

    override fun matchesQuery(item: DiffRow, qLower: String): Boolean {
        return item is DiffRow.Line && item.line.text.lowercase(Locale.getDefault()).contains(qLower)
    }

    override fun sortItems(items: List<DiffRow>, sortMode: CommonSortMode): List<DiffRow> = items

    override fun updateStats(allItems: List<DiffRow>, shownItems: List<DiffRow>) {
        val stats = loadedStats
        val sides = "$leftLabel → $rightLabel"
        toolbar.subtitle = when {
            leftLabel.isEmpty() && rightLabel.isEmpty() -> null
            stats == null -> sides
            else -> "${getString(R.string.text_diff_stats, stats.inserted, stats.deleted)}  |  $sides"
        }
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_expand_all -> true.also {
            allItems.indices.forEach { expandedRuns.add(it) }
            applyFilterAndSort()
        }
        else -> false
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_DISPLAY_PATH = "display_path"
        private const val EXTRA_LEFT_REV_ID = "left_rev_id"
        private const val EXTRA_RIGHT_REV_ID = "right_rev_id"

        private const val CONTEXT_LINES = 3

        // A run is only folded when folding hides more than this many lines.
        // zh-CN: 仅当折叠能隐藏超过此行数时才折叠.
        private const val MIN_LINES_TO_FOLD = 2

        /**
         * Open the diff page. A null revision id on either side means the file currently on disk.
         * zh-CN: 打开 diff 页. 任一侧 revision id 为 null 表示当前磁盘文件.
         */
        @JvmStatic
        fun launch(context: Context, canonicalPath: String, displayPath: String, leftRevId: String?, rightRevId: String?) {
            Intent(context, DiffActivity::class.java)
                .putExtra(EXTRA_PATH, canonicalPath)
                .putExtra(EXTRA_DISPLAY_PATH, displayPath)
                .putExtra(EXTRA_LEFT_REV_ID, leftRevId)
                .putExtra(EXTRA_RIGHT_REV_ID, rightRevId)
                .startSafely(context)
        }
    }
}
