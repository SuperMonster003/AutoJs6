package org.autojs.autojs.ui.explorer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.optArray
import org.autojs.autojs.core.pref.optString
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles
import org.autojs.autojs.model.explorer.PrivilegedExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs.model.explorer.PrivilegedFileOperationJournal
import org.autojs.autojs.model.explorer.PrivilegedFileOperations
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.filechooser.FileChooserDialogBuilder
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Privileged paths never fall through to ScriptOperations' unprivileged java.io.File methods. */
internal class PrivilegedExplorerOperationsController(
    private val explorer: ExplorerView,
    private val externalFiles: PrivilegedExternalOpenAccess = PrivilegedExternalOpenAccess.forContext(explorer.context),
) {
    private val context get() = explorer.context
    private var progress: MaterialDialog? = null
    private var recoveryCheckStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var compatibilityCopy: CopyOperation? = null
    private var checkingExternalFailure = false
    private var lastExternalOpen: Pair<ExplorerItem, PrivilegedAccessMode>? = null
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
            if (item !is ExplorerPage && !item.type.isTextEditable()) {
                menu.add(0, R.id.explorer_privileged_open_compatibility, 8, R.string.text_explorer_stream_compatibility_open)
            }
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
            R.id.explorer_privileged_open_compatibility -> modeOf(item)?.let { confirmCompatibilityOpen(item, it) }
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
            openExternally(item, mode)
        }
    }

    private fun openExternally(item: ExplorerItem, mode: PrivilegedAccessMode) {
        run(R.string.text_open, { externalFiles.prepare(mode, item.path) },
            abandoned = ::releasePrepared,
            failure = { error ->
                if ((error as? PrivilegedFileOperationException)?.code == "STREAM_UNSUPPORTED") {
                    confirmCompatibilityOpen(item, mode)
                } else showFailure(R.string.text_open, error)
            },
        ) { prepared ->
            try {
                launchExternalUri(prepared.uri, prepared.mimeType)
                lastExternalOpen = item to mode
            } catch (error: Exception) {
                releasePrepared(prepared)
                showFailure(R.string.text_open, error)
            }
        }
    }

    private fun releasePrepared(prepared: PrivilegedExternalFiles.PreparedOpen) {
        Schedulers.io().scheduleDirect { runCatching { externalFiles.release(prepared) } }
    }

    /** Grant only this read-only document, including ClipData for receiving-app compatibility. */
    private fun launchExternalUri(uri: Uri, mimeType: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(context.getString(R.string.text_open), uri)
                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (error: ActivityNotFoundException) {
            throw PrivilegedFileOperationException("NO_APP", context.getString(R.string.error_no_applications_available_for_viewing_this_file), cause = error)
        } catch (error: SecurityException) {
            throw PrivilegedFileOperationException("ACCESS_UNAVAILABLE", error.message ?: context.getString(R.string.text_explorer_stream_access_lost), cause = error)
        }
    }

    private fun confirmCompatibilityOpen(item: ExplorerItem, mode: PrivilegedAccessMode) {
        run(R.string.text_explorer_stream_compatibility_open, { externalFiles.describe(mode, item.path) }) { description ->
            MaterialDialog.Builder(context)
                .title(R.string.text_explorer_stream_compatibility_open)
                .content(context.getString(R.string.text_explorer_stream_copy_confirm,
                    Formatter.formatFileSize(context, description.size), item.path,
                    Formatter.formatFileSize(context, description.availableBytes)))
                .negativeText(R.string.dialog_button_cancel)
                .positiveText(R.string.text_open)
                .positiveColorRes(R.color.dialog_button_warn)
                .onPositive { _, _ -> copyForCompatibility(item, mode, description) }
                .showAdaptive()
        }
    }

    private fun copyForCompatibility(item: ExplorerItem, mode: PrivilegedAccessMode, description: PrivilegedExternalFiles.FileDescription) {
        if (isBusy || !explorer.isAttachedToWindow) return
        val operation = CopyOperation()
        compatibilityCopy = operation
        isBusy = true
        progress = MaterialDialog.Builder(context)
            .title(R.string.text_explorer_stream_copy_title)
            .content(copyProgressText(0, description.size))
            .progress(false, 100, true)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { dialog, _ ->
                operation.cancelled.set(true)
                dialog.getActionButton(DialogAction.NEGATIVE).isEnabled = false
                dialog.setContent(R.string.text_aborting)
            }
            .cancelable(false)
            .canceledOnTouchOutside(false)
            .autoDismiss(false)
            .showAdaptive()
        Single.fromCallable {
            externalFiles.copy(mode, item.path,
                onProgress = { copied, total ->
                    val now = SystemClock.elapsedRealtime()
                    val last = operation.updatedAt.get()
                    if ((copied >= total || now - last >= 100) && operation.updatedAt.compareAndSet(last, now)) {
                        mainHandler.post {
                            if (compatibilityCopy === operation && !operation.cancelled.get() && explorer.isAttachedToWindow) {
                                val percent = if (total <= 0) 100 else (copied.toDouble() / total.toDouble() * 100).toInt().coerceIn(0, 100)
                                progress?.setProgress(percent)
                                progress?.setContent(copyProgressText(copied, total))
                            }
                        }
                    }
                }, cancelled = operation.cancelled::get, confirmed = description)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ file ->
            compatibilityCopy = null
            finish(false)
            if (operation.cancelled.get() || !explorer.isAttachedToWindow) {
                discardCompatibilityCopy(file)
            } else {
                try {
                    launchExternalUri(IntentUtils.getUriOfFile(context, file.path), Mime.fromFileOrWildcard(item.path))
                    lastExternalOpen = item to mode
                } catch (error: Exception) {
                    discardCompatibilityCopy(file)
                    showFailure(R.string.text_open, error)
                }
            }
        }, { error ->
            compatibilityCopy = null
            finish(false)
            if (!operation.cancelled.get() && (error as? PrivilegedFileOperationException)?.code != "CANCELLED" && explorer.isAttachedToWindow) {
                showFailure(R.string.text_explorer_stream_copy_title, error)
            }
        })
    }

    private fun copyProgressText(copied: Long, total: Long) = context.getString(
        R.string.text_explorer_stream_copy_progress,
        Formatter.formatFileSize(context, copied.coerceAtLeast(0L)),
        Formatter.formatFileSize(context, total.coerceAtLeast(0L)),
    )

    private fun discardCompatibilityCopy(file: File) {
        Schedulers.io().scheduleDirect { runCatching { file.delete() } }
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

    private fun <T : Any> run(title: Int, work: () -> T, refresh: Boolean = false, abandoned: (T) -> Unit = {}, failure: ((Throwable) -> Unit)? = null, success: (T) -> Unit) {
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
                if (explorer.isAttachedToWindow) {
                    if (failure != null) failure(error) else showFailure(title, error)
                }
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
            "NO_APP" -> context.getString(R.string.error_no_applications_available_for_viewing_this_file)
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
            .apply {
                if (failure?.code != "NO_APP") {
                    neutralText(R.string.text_explorer_access_options)
                    onNeutral { _, _ -> explorer.showDirectoryAccessOptions() }
                }
            }
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
                        record.optArray("recoveryPaths")?.forEach { retained -> if (retained is String) paths += retained }
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
        showExternalReadFailure()
    }

    private fun showExternalReadFailure() {
        if (checkingExternalFailure || !explorer.isAttachedToWindow) return
        checkingExternalFailure = true
        Single.fromCallable { externalFiles.consumeFailure().orEmpty() }
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ message ->
                checkingExternalFailure = false
                if (message.isEmpty() || !explorer.isAttachedToWindow) return@subscribe
                val previous = lastExternalOpen
                MaterialDialog.Builder(context)
                    .title(R.string.text_open)
                    .content(message)
                    .negativeText(R.string.dialog_button_dismiss)
                    .neutralText(R.string.text_explorer_access_options)
                    .onNeutral { _, _ -> explorer.showDirectoryAccessOptions() }
                    .positiveText(if (previous == null) R.string.text_refresh else R.string.text_open)
                    .onPositive { _, _ ->
                        if (previous == null) explorer.refreshFromPathMenu()
                        else openExternally(previous.first, explorer.privilegedAccessMode ?: previous.second)
                    }
                    .showAdaptive()
            }, { checkingExternalFailure = false })
    }

    fun detach() {
        compatibilityCopy?.cancelled?.set(true)
        progress?.dismiss()
        progress = null
    }

    private fun modeOf(item: ExplorerItem) = (item as? PrivilegedExplorerItem)?.accessMode ?: explorer.privilegedAccessMode
    private data class OperationResult(val completed: Int, val preserved: List<String>, val receipts: List<File>)
    private data class Snapshot(val item: ExplorerItem, val metadata: JSONObject)
    private class CopyOperation {
        val cancelled = AtomicBoolean(false)
        val updatedAt = AtomicLong(0)
    }
    private class BatchFailure(val completed: Int, val preserved: List<String>, val receipts: List<File>, cause: Throwable) : Exception(cause)
}
