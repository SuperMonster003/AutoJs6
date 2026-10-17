package org.autojs.autojs.ui.explorer

import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.explorer.PrivilegedExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs.model.explorer.PrivilegedFileOperationJournal
import org.autojs.autojs.model.explorer.PrivilegedFileOperations
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.json.JSONObject
import java.io.File

/** Privileged paths never fall through to ScriptOperations' unprivileged java.io.File methods. */
internal class PrivilegedExplorerOperationsController(private val explorer: ExplorerView) {
    private val context get() = explorer.context
    private var progress: MaterialDialog? = null
    private var recoveryCheckStarted = false
    var isBusy = false
        private set

    fun showMenu(anchor: View, item: ExplorerItem) {
        if (isBusy) return
        PopupMenu(context, anchor).apply {
            if (item !is ExplorerPage) menu.add(0, R.id.explorer_privileged_open, 0, R.string.text_open)
            menu.add(0, R.id.action_copy_to, 1, R.string.text_copy_to)
            menu.add(0, R.id.action_move_to, 2, R.string.text_move_to)
            menu.add(0, R.id.action_rename, 3, R.string.text_rename)
            menu.add(0, R.id.action_delete, 4, R.string.text_delete)
            menu.add(0, R.id.action_copy_path, 5, R.string.text_copy_path)
            menu.add(0, R.id.action_info, 6, R.string.text_info)
            if (item !is ExplorerPage) menu.add(0, R.id.explorer_privileged_backups, 7, R.string.text_privileged_backups)
            setOnMenuItemClickListener { action -> handleMenu(action, item) }
        }.show()
    }

    fun handleMenu(action: MenuItem, item: ExplorerItem): Boolean {
        when (action.itemId) {
            R.id.explorer_privileged_open -> open(item)
            R.id.action_copy_to -> transfer(listOf(item), move = false)
            R.id.action_move_to -> transfer(listOf(item), move = true)
            R.id.action_rename -> rename(item)
            R.id.action_delete -> delete(listOf(item))
            R.id.action_copy_path -> ExplorerViewHelper.copyPaths(context, listOf(item))
            R.id.action_info -> ExplorerDirectoryAccessUi.showFileInfo(explorer, item)
            R.id.explorer_privileged_backups -> PrivilegedBackupUi.show(explorer, item.path, File(item.path).parent, modeOf(item))
            else -> return false
        }
        return true
    }

    fun open(item: ExplorerItem) {
        val mode = modeOf(item) ?: return
        if (item.type.isTextEditable()) {
            run(R.string.text_open, { PrivilegedEditSessions.create(context.applicationContext, mode, item.path) }, abandoned = { uri ->
                PrivilegedEditSessions.detach(context, uri)
                Schedulers.io().scheduleDirect { runCatching { PrivilegedEditSessions.markCleanlyClosed(context, uri) } }
            }) { uri ->
                try {
                    if (!EditActivity.editPrivilegedDocument(context, uri, item.name)) {
                        PrivilegedEditSessions.detach(context, uri)
                    }
                } catch (error: Exception) {
                    PrivilegedEditSessions.detach(context, uri)
                    throw error
                }
            }
        } else {
            run(R.string.text_open, { PrivilegedFileOperations.materialize(context.applicationContext, mode, item.path) }) { file ->
                IntentUtils.viewFile(context, file.path, exceptionHolder = IntentUtils.ToastExceptionHolder(context))
            }
        }
    }

    fun transfer(items: List<ExplorerItem>, move: Boolean) {
        if (isBusy || items.isEmpty()) return
        val mode = modeOf(items.first()) ?: return
        val title = if (move) R.string.text_move_to else R.string.text_copy_to
        prepare(items, title, mode) { snapshots -> chooseDestination(snapshots, mode, move, title) }
    }

    private fun chooseDestination(snapshots: List<Snapshot>, mode: PrivilegedAccessMode, move: Boolean, title: Int) {
        val chooser = FileChooserDialogBuilder(context)
            .title(title)
            .dir(EnvironmentUtils.externalStoragePath, File(snapshots.first().item.path).parent ?: EnvironmentUtils.externalStoragePath)
            .chooseDir()
            .privilegedAccess(mode)
        chooser.content(R.string.text_explorer_privileged_destination_hint)
        chooser.singleChoice { directory ->
            val selectedMode = chooser.privilegedAccessMode ?: mode
            perform(snapshots, if (move) "move" else "copy", title, selectedMode) { File(directory.path, it.name).path }
        }.show()
    }

    private fun rename(item: ExplorerItem) {
        if (isBusy) return
        val mode = modeOf(item) ?: return
        prepare(listOf(item), R.string.text_rename, mode) { snapshots -> showRename(item, mode, snapshots) }
    }

    private fun showRename(item: ExplorerItem, mode: PrivilegedAccessMode, snapshots: List<Snapshot>) {
        MaterialDialog.Builder(context)
            .title(R.string.text_rename)
            .input(context.getString(R.string.text_name), item.name, false) { _, value ->
                val name = value.toString()
                if (name != item.name) {
                    if (name.isBlank() || name == "." || name == ".." || '/' in name || '\u0000' in name || name.toByteArray().size > 255) {
                        showFailure(R.string.text_rename, PrivilegedFileOperationException("INVALID_NAME", context.getString(R.string.text_name)))
                    } else perform(snapshots, "rename", R.string.text_rename, mode) {
                        File(requireNotNull(File(it.path).parent), name).path
                    }
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .showAdaptive()
    }

    fun delete(items: List<ExplorerItem>) {
        if (isBusy || items.isEmpty()) return
        val mode = modeOf(items.first()) ?: return
        prepare(items, R.string.text_delete, mode) { snapshots -> confirmDelete(items, mode, snapshots) }
    }

    private fun confirmDelete(items: List<ExplorerItem>, mode: PrivilegedAccessMode, snapshots: List<Snapshot>) {
        MaterialDialog.Builder(context)
            .title(R.string.text_delete)
            .content(context.getString(R.string.text_explorer_privileged_delete_confirmation, items.joinToString("\n") {
                "\"" + it.path.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\"", "\\\"") + "\""
            }))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ -> perform(snapshots, "delete", R.string.text_delete, mode) { null } }
            .showAdaptive()
    }

    private fun prepare(items: List<ExplorerItem>, title: Int, mode: PrivilegedAccessMode, success: (List<Snapshot>) -> Unit) {
        val selected = items.distinctBy { it.path }.toList()
        run(title, {
            selected.map { item ->
                val metadata = PrivilegedFileOperations.stat(context.applicationContext, mode, item.path)
                val kind = metadata.getString("kind")
                if ((kind == "directory" && item !is ExplorerPage) || (kind == "file" && item is ExplorerPage) ||
                    (kind == "directory" && item.lastModified() != metadata.getLong("modified"))) {
                    throw PrivilegedFileOperationException("SOURCE_CHANGED", item.path)
                }
                if (metadata.optString("kind") == "file" && (item.size != metadata.getLong("size") || item.lastModified() != metadata.getLong("modified"))) {
                    throw PrivilegedFileOperationException("SOURCE_CHANGED", item.path)
                }
                Snapshot(item, metadata)
            }
        }, success = success)
    }

    private fun perform(snapshots: List<Snapshot>, operation: String, title: Int, mode: PrivilegedAccessMode, destination: (ExplorerItem) -> String?) {
        run(title, {
            var completed = 0
            val preserved = mutableListOf<String>()
            val receipts = mutableListOf<File>()
            try {
                snapshots.forEach { (item, snapshot) ->
                    val result = PrivilegedFileOperations.execute(context.applicationContext, mode, operation, item.path, destination(item), snapshot.getJSONObject("identity"))
                    completed++
                    result.optJSONArray("recoveryPaths")?.let { paths -> (0 until paths.length()).forEach { preserved += paths.getString(it) } }
                    result.optString("_receipt").takeIf { it.isNotEmpty() }?.let { receipts += File(it) }
                }
                OperationResult(completed, preserved, receipts)
            } catch (error: Exception) {
                throw BatchFailure(completed, preserved, receipts, error)
            }
        }, refresh = true) { result ->
            explorer.clearPrivilegedSelection()
            if (result.preserved.isEmpty()) ViewUtils.showToast(context, R.string.text_operation_is_completed) else {
                MaterialDialog.Builder(context)
                    .title(R.string.text_operation_is_completed)
                    .content(context.getString(R.string.text_explorer_privileged_recovery_paths, result.preserved.joinToString("\n")))
                    .positiveText(R.string.dialog_button_dismiss).showAdaptive()
            }
            result.receipts.forEach(PrivilegedFileOperationJournal::finish)
        }
    }

    private fun <T : Any> run(title: Int, work: () -> T, refresh: Boolean = false, abandoned: (T) -> Unit = {}, success: (T) -> Unit) {
        if (isBusy) return
        isBusy = true
        progress = MaterialDialog.Builder(context)
            .title(title).content(R.string.text_preparing).progress(true, 0)
            .cancelable(false).showAdaptive()
        // Mutations finish or return their own bounded failure even if the view is detached.
        // Disposing a Binder call cannot undo a rename or deletion in the authorized process.
        Single.fromCallable(work)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                finish(refresh)
                if (explorer.isAttachedToWindow) success(result) else abandoned(result)
            }, { error ->
                finish(refresh)
                if (explorer.isAttachedToWindow) showFailure(title, error)
            })
    }

    private fun finish(refresh: Boolean) {
        progress?.dismiss()
        progress = null
        isBusy = false
        if (refresh) {
            Explorers.external().invalidateCache()
            Explorers.workspace().refreshAll()
        }
    }

    private fun showFailure(title: Int, error: Throwable) {
        val completed = (error as? BatchFailure)?.completed ?: 0
        val cause = if (error is BatchFailure) error.cause ?: error else error
        val failure = cause as? PrivilegedFileOperationException
        val detail = when (failure?.code) {
            "PERMISSION_DENIED", "ACCESS_UNAVAILABLE" -> context.getString(R.string.text_explorer_privileged_permission_lost)
            "READ_ONLY" -> context.getString(R.string.text_explorer_privileged_read_only_storage)
            "UNSUPPORTED_MOVE" -> context.getString(R.string.text_explorer_privileged_move_scope)
            "OUTCOME_UNKNOWN" -> context.getString(R.string.text_explorer_privileged_outcome_unknown)
            "DESTINATION_EXISTS" -> context.getString(R.string.text_explorer_privileged_conflict)
            "SOURCE_CHANGED" -> context.getString(R.string.text_explorer_privileged_source_changed)
            "OPEN_LIMIT" -> context.getString(R.string.text_explorer_privileged_open_limit)
            "NO_SPACE" -> context.getString(R.string.text_explorer_privileged_no_space)
            "INVALID_NAME" -> context.getString(R.string.text_explorer_privileged_invalid_name)
            "UNSUPPORTED_TYPE" -> context.getString(R.string.text_explorer_privileged_unsupported)
            else -> cause.message ?: cause.toString()
        }
        val details = buildString {
            append(detail)
            if (completed > 0) append("\n\n").append(context.getString(R.string.text_explorer_privileged_partial, completed))
            val preserved = (error as? BatchFailure)?.preserved.orEmpty() + failure?.recoveryPaths.orEmpty()
            if (preserved.isNotEmpty()) {
                append("\n\n").append(context.getString(R.string.text_explorer_privileged_recovery_paths, preserved.distinct().joinToString("\n")))
            }
        }
        MaterialDialog.Builder(context)
            .title(title)
            .content(context.getString(R.string.text_explorer_privileged_operation_failed, context.getString(title), details))
            .neutralText(R.string.text_explorer_access_options)
            .onNeutral { _, _ -> explorer.showDirectoryAccessOptions() }
            .positiveText(R.string.dialog_button_dismiss)
            .showAdaptive()
        failure?.receipt?.let(PrivilegedFileOperationJournal::finish)
        (error as? BatchFailure)?.receipts?.forEach(PrivilegedFileOperationJournal::finish)
    }

    fun showInterruptedOperations() {
        if (recoveryCheckStarted) return
        recoveryCheckStarted = true
        Single.fromCallable { PrivilegedFileOperationJournal.interrupted(context.applicationContext) }
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ records ->
                if (records.isNotEmpty() && explorer.isAttachedToWindow) {
                    val details = records.joinToString("\n\n") { (_, record) ->
                        val message = if (record.optString("code") in listOf("", "OUTCOME_UNKNOWN")) context.getString(R.string.text_explorer_privileged_outcome_unknown)
                            else record.optString("message")
                        val paths = listOf(record.optString("source"), record.optString("destination")).filter(String::isNotEmpty).toMutableList()
                        record.optJSONArray("recoveryPaths")?.let { retained -> (0 until retained.length()).forEach { paths += retained.getString(it) } }
                        message + "\n" + paths.joinToString("\n")
                    }
                    MaterialDialog.Builder(context).title(R.string.text_explorer_privileged_access)
                        .content(details)
                        .positiveText(R.string.dialog_button_dismiss).showAdaptive()
                    records.forEach { (file, _) -> PrivilegedFileOperationJournal.finish(file) }
                } else if (!explorer.isAttachedToWindow) {
                    recoveryCheckStarted = false
                }
            }, { recoveryCheckStarted = false /* Diagnostics must not prevent opening the explorer. */ })
    }

    fun onWindowFocusGained() {
        if (isBusy) return
        recoveryCheckStarted = false
        showInterruptedOperations()
    }

    fun detach() {
        progress?.dismiss()
        progress = null
    }

    private fun modeOf(item: ExplorerItem) = (item as? PrivilegedExplorerItem)?.accessMode ?: explorer.privilegedAccessMode
    private data class OperationResult(val completed: Int, val preserved: List<String>, val receipts: List<File>)
    private data class Snapshot(val item: ExplorerItem, val metadata: JSONObject)
    private class BatchFailure(val completed: Int, val preserved: List<String>, val receipts: List<File>, cause: Throwable) : Exception(cause)
}
