package org.autojs.autojs.ui.explorer

import android.content.Context
import android.view.MenuItem
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.ui.common.ScriptLoopDialog
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.floating.controller.FloatingControllerManager
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.autojs4.AutoJs4RuntimePluginUi
import org.autojs.autojs.util.Observers
import org.autojs.autojs6.R
import java.io.File

internal interface ExplorerBuiltInActionHost {
    val context: Context
    val explorerView: ExplorerView
    val currentPage: ExplorerPage
    val selectedItem: ExplorerItem?

    fun showError(error: Throwable, titleRes: Int)
    fun notifyItemOperated()
    fun hideHostDialog()
    fun resetSucceeded()
    fun resetFailed()
}

/** Dispatches the built-in overflow actions for one selected Explorer row. */
internal class ExplorerBuiltInActionHandler(
    private val host: ExplorerBuiltInActionHost,
) {

    fun handle(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_move_to -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .move(host.selectedItem!!.toScriptFile())
                    .ignoreElements()
                    .subscribe({}, { host.showError(it, R.string.error_failed_to_move) })
            }
            R.id.action_copy_to -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .copy(host.selectedItem!!.toScriptFile())
                    .ignoreElements()
                    .subscribe({}, { host.showError(it, R.string.error_failed_to_copy) })
            }
            R.id.action_rename -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .rename(host.selectedItem as ExplorerFileItem?)
                    .ignoreElements()
                    .subscribe({}, { host.showError(it, R.string.error_failed_to_rename) })
            }
            R.id.action_delete -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .delete(host.selectedItem!!.toScriptFile())
            }
            R.id.action_version_history -> {
                val selected = host.selectedItem ?: return false
                VersionHistoryController(host.context).showForFilePath(selected.path, selected.path)
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_run_repeatedly -> {
                ScriptLoopDialog(host.context, host.selectedItem!!.toScriptFile()).show()
                host.notifyItemOperated()
            }
            R.id.action_floating_run -> {
                FloatingControllerManager.start(host.context, host.selectedItem!!.toScriptFile())
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_create_shortcut -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .createShortcut(host.selectedItem!!.toScriptFile())
                host.hideHostDialog()
            }
            R.id.action_edit_by_other_apps -> {
                host.selectedItem?.edit(host.explorerView)
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_open_by_other_apps -> {
                Scripts.openByOtherApps(host.selectedItem!!.toScriptFile())
                host.notifyItemOperated()
            }
            R.id.action_send -> {
                Scripts.send(host.context, host.selectedItem!!.toScriptFile())
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_timed_task -> {
                ScriptOperations(host.context, host.explorerView, host.currentPage)
                    .timedTask(host.selectedItem!!.toScriptFile())
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_build_apk -> {
                BuildActivity.launch(host.context, host.selectedItem!!.path)
                host.notifyItemOperated()
                host.hideHostDialog()
            }
            R.id.action_run_with_autojs4 -> {
                val selected = host.selectedItem ?: return false
                host.hideHostDialog()
                Scripts.runWithAutoJs4(host.context, File(selected.path))
                host.notifyItemOperated()
            }
            R.id.action_install_autojs4_runtime -> {
                host.hideHostDialog()
                AutoJs4RuntimePluginUi.openPluginCenter(host.context)
            }
            R.id.action_reset -> {
                val reset = Explorers.Providers.workspace().resetSample(host.selectedItem!!.toScriptFile())
                if (reset == null) {
                    host.resetFailed()
                } else {
                    reset.observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ file: ScriptFile ->
                            if (file.exists()) host.resetSucceeded() else host.resetFailed()
                        }, Observers.toastMessage())
                }
            }
            else -> return false
        }
        return true
    }
}
