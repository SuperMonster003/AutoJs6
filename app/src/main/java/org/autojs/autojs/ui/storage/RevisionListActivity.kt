package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.HistoryKeys
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryRetentionPolicy
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.storage.history.VersionHistoryEditorBridge
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityRevisionListBinding
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Revisions of one tracked file: time, size, operation, encoding and a "current" marker for the
 * revision whose content equals the file on disk. Tap for preview / restore / delete, long press
 * for multi-selection delete. Restore goes to disk (explorer entry) or into the editor (editor entry).
 *
 * zh-CN: 单个已纳管文件的全部 revision: 时间, 大小, 操作, 编码, 以及内容与当前磁盘文件一致的 "当前" 标记.
 * 点击预览 / 恢复 / 删除, 长按进入多选删除. 恢复目标为磁盘 (资源管理器入口) 或编辑器 (编辑器入口).
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class RevisionListActivity : BaseSearchSortListActivity<ActivityRevisionListBinding, RevisionRow>() {

    private var canonicalPath: String = ""
    private var displayPath: String = ""
    private var mode: VersionHistoryController.Mode = VersionHistoryController.Mode.RESTORE_TO_DISK
    private var callbackToken: String? = null

    private val controller by lazy { VersionHistoryController(this) }

    private val adapter: RevisionAdapter = RevisionAdapter(
        onClick = { row -> if (adapter.selectionMode) adapter.toggle(row) else showRevisionDialog(row) },
        onLongClick = { row -> if (adapter.selectionMode) adapter.toggle(row) else adapter.enterSelection(row) },
        onSelectionChanged = { count, selectionMode -> onSelectionChanged(count, selectionMode) },
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            adapter.clearSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        canonicalPath = intent?.getStringExtra(EXTRA_PATH).orEmpty()
        displayPath = intent?.getStringExtra(EXTRA_DISPLAY_PATH).orEmpty().ifEmpty { canonicalPath }
        mode = runCatching { VersionHistoryController.Mode.valueOf(intent?.getStringExtra(EXTRA_MODE).orEmpty()) }
            .getOrDefault(VersionHistoryController.Mode.RESTORE_TO_DISK)
        callbackToken = intent?.getStringExtra(EXTRA_CALLBACK_TOKEN)

        super.onCreate(savedInstanceState)

        if (canonicalPath.isEmpty()) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onDestroy() {
        VersionHistoryEditorBridge.unregister(callbackToken)
        super.onDestroy()
    }

    override fun createBinding(): ActivityRevisionListBinding {
        return ActivityRevisionListBinding.inflate(layoutInflater)
    }

    override fun provideViews(binding: ActivityRevisionListBinding): ListPageViews {
        return ListPageViews(
            toolbar = binding.toolbar,
            recyclerView = binding.recyclerView,
            swipeRefreshLayout = binding.swipeRefreshLayout,
            emptyHint = binding.emptyHint,
        )
    }

    override fun getToolbarTitleText(): String = File(displayPath).name

    override fun menuResId(): Int = R.menu.menu_revision_list

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> = adapter

    override fun submitToAdapter(items: List<RevisionRow>) = adapter.submit(items)

    override fun loadAllItemsInBackground(): List<RevisionRow> {
        if (canonicalPath.isEmpty()) return emptyList()
        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
        val entry = dao.findFileByPath(canonicalPath) ?: return emptyList()
        val revisions = dao.listRevisionsAsc(entry.fileId)
        val currentSha = currentFileSha256OrNull()
        return revisions.map { rev ->
            RevisionRow(rev, currentSha != null && rev.sha256.equals(currentSha, ignoreCase = true))
        }
    }

    /**
     * SHA-256 of the file on disk, or null when it is missing or larger than the tracked size limit
     * (a file that large never has a revision to match anyway).
     * zh-CN: 当前磁盘文件的 SHA-256; 文件不存在或超过纳管大小上限时返回 null (那么大的文件本来也不会有可匹配的 revision).
     */
    private fun currentFileSha256OrNull(): String? = runCatching {
        val file = File(canonicalPath)
        if (!file.isFile) return@runCatching null
        val limit = HistoryKeys.maxFileSizeToTrackBytes()
        if (!HistoryRetentionPolicy.isUnlimited(limit) && file.length() > limit) return@runCatching null
        if (file.length() > CURRENT_SHA_MAX_BYTES) return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }.getOrNull()

    override fun matchesQuery(item: RevisionRow, qLower: String): Boolean = true

    override fun sortItems(items: List<RevisionRow>, sortMode: CommonSortMode): List<RevisionRow> = when (sortMode) {
        CommonSortMode.TIME_ASC -> items.sortedBy { it.rev.createdAt }
        CommonSortMode.SIZE_DESC -> items.sortedByDescending { it.rev.sizeBytes }
        CommonSortMode.SIZE_ASC -> items.sortedBy { it.rev.sizeBytes }
        else -> items.sortedByDescending { it.rev.createdAt }
    }

    override fun updateStats(allItems: List<RevisionRow>, shownItems: List<RevisionRow>) {
        if (adapter.selectionMode) return
        val totalBytes = allItems.sumOf { it.rev.sizeBytes.coerceAtLeast(0L) }
        toolbar.subtitle = "${getString(R.string.text_n_versions, allItems.size.toLong())}  |  ${PFiles.formatSizeWithUnit(totalBytes)}"
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val selecting = adapter.selectionMode
        menu.findItem(R.id.action_delete_selected)?.isVisible = selecting
        menu.findItem(R.id.action_compare_selected)?.isVisible = selecting && adapter.selectedCount == 2
        menu.findItem(R.id.action_select_all)?.isVisible = selecting
        menu.findItem(R.id.action_deselect_all)?.isVisible = selecting
        menu.findItem(R.id.action_sort)?.isVisible = !selecting
        menu.findItem(R.id.action_open_file)?.isVisible = !selecting && File(canonicalPath).isFile
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_select_all -> true.also { adapter.selectAll() }
        R.id.action_deselect_all -> true.also { adapter.clearSelection() }
        R.id.action_delete_selected -> true.also { confirmAndDelete(adapter.selectedRows()) }
        R.id.action_compare_selected -> true.also { compareSelected(adapter.selectedRows()) }
        R.id.action_open_file -> true.also { Scripts.edit(this, canonicalPath) }
        else -> false
    }

    private fun onSelectionChanged(count: Int, selectionMode: Boolean) {
        backCallback.isEnabled = selectionMode
        if (selectionMode) {
            toolbar.title = getString(R.string.text_selected_items, count)
            toolbar.subtitle = null
        } else {
            toolbar.title = getToolbarTitleText()
            updateStats(allItems = allItems, shownItems = allItems)
        }
        invalidateOptionsMenu()
    }

    private fun showRevisionDialog(row: RevisionRow) {
        val rev = row.rev
        val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(rev.createdAt))
        val details = buildString {
            append(getString(R.string.text_property_colon_value, getString(R.string.text_operation), VersionHistoryController.opLabel(this@RevisionListActivity, rev.op)))
            append('\n')
            append(getString(R.string.text_property_colon_value, getString(R.string.text_size), PFiles.formatSizeWithUnit(rev.sizeBytes)))
            append('\n')
            append(getString(R.string.text_property_colon_value, getString(R.string.text_file_encoding), rev.encoding + if (rev.hadBom) " (BOM)" else ""))
            append('\n')
            append(getString(R.string.text_property_colon_value, "SHA-256", rev.sha256.take(SHA_PREVIEW_CHARS)))
            rev.label?.takeIf { it.isNotBlank() }?.let {
                append('\n')
                append(getString(R.string.text_property_colon_value, getString(R.string.text_label), it))
            }
            if (rev.pinned) {
                append('\n')
                append(getString(R.string.text_pinned_exempt_from_per_file_cleanup))
            }
            if (row.isCurrent) {
                append('\n')
                append(getString(R.string.text_same_as_current_file))
            }
        }
        val options = ArrayList<MaterialDialog.OptionMenuItemSpec>()
        if (File(canonicalPath).isFile) {
            options += MaterialDialog.OptionMenuItemSpec(getString(R.string.text_compare_with_current_file)) { dialog ->
                dialog.dismiss()
                DiffActivity.launch(this, canonicalPath, displayPath, leftRevId = rev.revId, rightRevId = null)
            }
        }
        if (mode == VersionHistoryController.Mode.RESTORE_TO_EDITOR) {
            options += MaterialDialog.OptionMenuItemSpec(getString(R.string.text_restore_and_save)) { dialog ->
                dialog.dismiss()
                restoreToEditorAndSave(row)
            }
        }
        options += MaterialDialog.OptionMenuItemSpec(getString(if (rev.pinned) R.string.text_unpin else R.string.text_pin)) { dialog ->
            dialog.dismiss()
            controller.setPinned(rev, !rev.pinned) { load() }
        }
        options += MaterialDialog.OptionMenuItemSpec(getString(R.string.text_edit_label)) { dialog ->
            dialog.dismiss()
            showEditLabelDialog(row)
        }
        options += MaterialDialog.OptionMenuItemSpec(getString(R.string.text_export)) { dialog ->
            dialog.dismiss()
            controller.exportRevision(rev, displayPath)
        }
        options += MaterialDialog.OptionMenuItemSpec(getString(R.string.text_delete)) { dialog ->
            dialog.dismiss()
            confirmAndDelete(listOf(row))
        }
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(timeText)
                .content(details)
                .options(options)
                .neutralText(R.string.dialog_button_preview)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { _, _ -> controller.previewInReadOnlyEditor(rev, displayPath) }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_restore)
                .positiveColorRes(
                    when (mode) {
                        VersionHistoryController.Mode.RESTORE_TO_EDITOR -> R.color.dialog_button_attraction
                        VersionHistoryController.Mode.RESTORE_TO_DISK -> R.color.dialog_button_caution
                    }
                )
                .onPositive { _, _ -> restore(row) }
                .cancelable(true)
                .build()
        }
    }

    private fun restore(row: RevisionRow) {
        when (mode) {
            VersionHistoryController.Mode.RESTORE_TO_EDITOR -> {
                val callbacks = VersionHistoryEditorBridge.get(callbackToken)
                if (callbacks == null) {
                    ViewUtils.showToast(this, R.string.text_editor_is_no_longer_available)
                    return
                }
                controller.confirmAndRestoreToEditor(row.rev, callbacks) { finish() }
            }
            VersionHistoryController.Mode.RESTORE_TO_DISK -> {
                controller.confirmAndRestoreToDisk(row.rev, canonicalPath) { load() }
            }
        }
    }

    private fun showEditLabelDialog(row: RevisionRow) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_edit_label)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .input(getString(R.string.text_snapshot_label_optional), row.rev.label.orEmpty(), true) { _, input ->
                    controller.setLabel(row.rev, input.toString()) { load() }
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .apply { widgetThemeColor() }
                .build()
        }
    }

    /**
     * Diff two selected revisions, older on the left.
     * zh-CN: 比较选中的两条 revision, 较旧的在左侧.
     */
    private fun compareSelected(rows: List<RevisionRow>) {
        if (rows.size != 2) return
        val sorted = rows.sortedBy { it.rev.createdAt }
        DiffActivity.launch(this, canonicalPath, displayPath, leftRevId = sorted[0].rev.revId, rightRevId = sorted[1].rev.revId)
    }

    private fun restoreToEditorAndSave(row: RevisionRow) {
        val callbacks = VersionHistoryEditorBridge.get(callbackToken)
        if (callbacks == null) {
            ViewUtils.showToast(this, R.string.text_editor_is_no_longer_available)
            return
        }
        controller.confirmAndRestoreToEditorAndSave(row.rev, callbacks, displayPath) { finish() }
    }

    private fun confirmAndDelete(rows: List<RevisionRow>) {
        if (rows.isEmpty()) return
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(
                    if (rows.size == 1) getString(R.string.text_delete_revision_confirm)
                    else getString(R.string.text_delete_selected_revisions_confirm, rows.size)
                )
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    controller.deleteRevisions(rows.map { it.rev }) {
                        adapter.clearSelection()
                        load()
                    }
                }
                .cancelable(true)
                .build()
        }
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_DISPLAY_PATH = "display_path"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_CALLBACK_TOKEN = "callback_token"

        private const val SHA_PREVIEW_CHARS = 16

        // Hashing the on-disk file for the "current" marker is skipped above this size.
        // zh-CN: 为 "当前" 标记而对磁盘文件求哈希的大小上限, 超过则跳过.
        private const val CURRENT_SHA_MAX_BYTES: Long = 64L * 1024L * 1024L

        @JvmStatic
        fun launch(
            context: Context,
            canonicalPath: String,
            displayPath: String,
            mode: VersionHistoryController.Mode,
            callbackToken: String?,
        ) {
            Intent(context, RevisionListActivity::class.java)
                .putExtra(EXTRA_PATH, canonicalPath)
                .putExtra(EXTRA_DISPLAY_PATH, displayPath)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_CALLBACK_TOKEN, callbackToken)
                .startSafely(context)
        }
    }
}
