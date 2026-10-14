package org.autojs.autojs.ui.explorer

import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.PopupMenu
import androidx.core.widget.ImageViewCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.core.plugin.autojs4.AutoJs4Comparison
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerSamplePage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.WorkspaceFileProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.ui.autojs4.AutoJs4RuntimePluginUi
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import org.autojs.autojs6.databinding.ExplorerDirectoryBinding

private const val LOG_TAG = "ExplorerPageView"
private const val PLUGIN_ACTION_MENU_ORDER_START = 1_000

internal interface ExplorerPageViewHost {
    val context: Context
    val explorerView: ExplorerView
    val actionIconTint: ColorStateList
    val isInSelectionMode: Boolean
    val isProjectRecognitionEnabled: Boolean
    val pluginActionProvider: ExplorerPluginActionProvider?
    val pluginActionSurface: ExplorerSurface
    val currentPage: ExplorerPage

    fun selectItem(item: ExplorerItem)
    fun enterSelectionMode(item: ExplorerItem): Boolean
    fun blockListScrollUntilTouchRelease()
    fun isItemSelected(item: ExplorerItem): Boolean
    fun toggleItemSelection(item: ExplorerItem)
    fun enterDirectChildPage(page: ExplorerPage)
    fun dispatchPluginAction(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit>
    fun notifyItemOperated(item: ExplorerItem)
    fun hideHostDialog()
    fun showErrorDialog(error: Throwable, titleRes: Int)
    fun resetSucceeded()
    fun resetFailed()
    fun onBuiltInMenuItem(item: MenuItem): Boolean
}
internal class ExplorerPageViewHolder(
    itemView: View,
    private val host: ExplorerPageViewHost,
) : BindableViewHolder<Any>(itemView) {

    private val binding = ExplorerDirectoryBinding.bind(itemView).also {
        it.item.setOnClickListener { withItemSelected { onItemClick() } }
        it.item.setOnLongClickListener {
            val enteredSelectionMode = withItemSelected {
                mExplorerPage?.let { page -> host.enterSelectionMode(page) }
            } == true
            if (enteredSelectionMode) {
                host.blockListScrollUntilTouchRelease()
            }
            true
        }
    }

    private var mName = binding.name
    private var mDirDate = binding.scriptDirDate
    private var mIcon = binding.icon
    private var mOptions = binding.more.also {
        it.setOnClickListener { withItemSelected { showOptionsMenu() } }
    }

    private var mExplorerPage: ExplorerPage? = null

    init {
        listOf(mOptions).forEach { view ->
            ImageViewCompat.setImageTintList(view, host.actionIconTint)
        }
    }

    override fun bind(data: Any, position: Int) {
        if (data !is ExplorerPage) return
        mName.text = ExplorerViewHelper.getDisplayName(host.context, data)
        mDirDate.text = PFile.getFullDateString(data.lastModified())
        mIcon.setImageResource(ExplorerViewHelper.getIconRes(data))
        val selected = host.isItemSelected(data)
        binding.item.isSelected = selected
        binding.item.setCardBackgroundColor(host.context.getColor(if (selected) R.color.window_background_selected else R.color.item_background))
        mOptions.visibility = when {
            data is ExplorerSamplePage -> GONE
            data is ArchiveExplorerPage && !data.canRename() && !data.canDelete() -> GONE
            host.isInSelectionMode -> INVISIBLE
            else -> VISIBLE
        }
        mExplorerPage = data
    }

    private fun onItemClick() {
        mExplorerPage?.let {
            if (host.isInSelectionMode) {
                host.toggleItemSelection(it)
                return
            }
        }
        mExplorerPage?.let { host.enterDirectChildPage(it) }
    }

    private fun showOptionsMenu() {
        val explorerPage = mExplorerPage ?: return
        val popupMenu = PopupMenu(host.context, mOptions)
        val menu = popupMenu.menu
        popupMenu.inflate(R.menu.menu_dir_options)
        if (!explorerPage.canMove()) {
            menu.removeItem(R.id.action_move_to)
        }
        if (!explorerPage.canCopy()) {
            menu.removeItem(R.id.action_copy_to)
        }
        // Archive entries only have synthetic paths, which are of no use outside the archive view.
        // zh-CN: 压缩包条目只有合成路径, 离开压缩包视图后没有意义.
        if (explorerPage is ArchiveExplorerPage) {
            menu.removeItem(R.id.action_copy_path)
        }
        if (!explorerPage.canRename()) {
            menu.removeItem(R.id.action_rename)
        }
        if (!explorerPage.canDelete()) {
            menu.removeItem(R.id.action_delete)
        }
        if (!host.isProjectRecognitionEnabled || !ProjectConfig.isProject(explorerPage)) {
            menu.removeItem(R.id.action_timed_task)
            menu.removeItem(R.id.action_run_with_autojs4)
            menu.removeItem(R.id.action_compare_with_autojs4)
            menu.removeItem(R.id.action_install_autojs4_runtime)
        } else if (AutoJs4RuntimePluginUi.isInstalled(host.context)) {
            menu.removeItem(R.id.action_install_autojs4_runtime)
        } else {
            menu.removeItem(R.id.action_run_with_autojs4)
            menu.removeItem(R.id.action_compare_with_autojs4)
        }
        if (!explorerPage.canSetAsWorkingDir()) {
            menu.removeItem(R.id.action_set_as_working_dir)
        }
        if (!explorerPage.canBuildApk()) {
            menu.removeItem(R.id.action_build_apk)
        }
        val samplePath = PFile(host.context.filesDir, WorkspaceFileProvider.SAMPLE_PATH).path
        if (!explorerPage.path.startsWith(samplePath)) {
            menu.removeItem(R.id.action_reset)
        }
        val pluginMenuItems = mutableMapOf<Int, ResolvedExplorerAction>()
        runCatching {
            host.pluginActionProvider?.actionsFor(
                host.pluginActionSurface,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                explorerPage,
            ).orEmpty()
        }
            .getOrElse { error ->
                Log.w(LOG_TAG, "Failed to resolve Explorer directory plugin actions", error)
                emptyList()
            }
            .forEachIndexed { index, action ->
                val menuId = View.generateViewId()
                menu.add(
                    Menu.NONE,
                    menuId,
                    PLUGIN_ACTION_MENU_ORDER_START + index,
                    action.menuTitle,
                )
                pluginMenuItems[menuId] = action
            }
        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            pluginMenuItems[item.itemId]?.let { pluginAction ->
                host.dispatchPluginAction(
                    host.context,
                    host.pluginActionSurface,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                    listOf(explorerPage),
                    pluginAction,
                )
                    .onSuccess { host.notifyItemOperated(explorerPage) }
                    .onFailure { error ->
                        ViewUtils.showToast(host.context, error.message ?: error.toString(), true)
                    }
                return@setOnMenuItemClickListener true
            }
            val selectedItem = explorerPage
            if (selectedItem is ArchiveExplorerPage) {
                return@setOnMenuItemClickListener host.onBuiltInMenuItem(item)
            }
            val explorerView = host.explorerView
            when (item.itemId) {
                R.id.action_copy_to ->
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .copy(selectedItem.toScriptFile())
                        .ignoreElements()
                        .subscribe({}, { host.showErrorDialog(it, R.string.error_failed_to_copy) })
                R.id.action_copy_path ->
                    ExplorerViewHelper.copyPaths(host.context, listOf(selectedItem))
                R.id.action_move_to ->
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .move(selectedItem.toScriptFile())
                        .ignoreElements()
                        .subscribe({}, { host.showErrorDialog(it, R.string.error_failed_to_move) })
                R.id.action_rename ->
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .rename(selectedItem as? ExplorerFileItem)
                        .ignoreElements()
                        .subscribe({}, { host.showErrorDialog(it, R.string.error_failed_to_rename) })
                R.id.action_delete ->
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .delete(selectedItem.toScriptFile())
                R.id.action_timed_task -> {
                    host.hideHostDialog()
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .timedTask(selectedItem.toScriptFile())
                    host.notifyItemOperated(explorerPage)
                }
                R.id.action_set_as_working_dir ->
                    ScriptOperations(host.context, explorerView, host.currentPage)
                        .setAsWorkingDir(selectedItem.toScriptFile())
                R.id.action_run_with_autojs4 -> {
                    host.hideHostDialog()
                    Scripts.runWithAutoJs4(host.context, File(selectedItem.path))
                    host.notifyItemOperated(explorerPage)
                }
                R.id.action_compare_with_autojs4 -> {
                    host.hideHostDialog()
                    AutoJs4Comparison.start(host.context, File(selectedItem.path))
                    host.notifyItemOperated(explorerPage)
                }
                R.id.action_install_autojs4_runtime -> {
                    host.hideHostDialog()
                    AutoJs4RuntimePluginUi.openPluginCenter(host.context)
                }
                R.id.action_build_apk -> {
                    host.hideHostDialog()
                    BuildActivity.launch(host.context, selectedItem.path)
                }
                R.id.action_reset -> {
                    val o = Explorers.Providers.workspace()
                        .resetSample(selectedItem.toScriptFile())
                    if (o == null) {
                        host.resetFailed()
                    } else {
                        o.observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ file: ScriptFile ->
                                if (file.exists()) {
                                    host.resetSucceeded()
                                } else {
                                    host.resetFailed()
                                }
                            }, Observers.toastMessage())
                    }
                }
                else -> null
            } != null
        }
        popupMenu.show()
    }

    private fun <R> withItemSelected(func: () -> R): R {
        mExplorerPage?.let(host::selectItem)
        return func.invoke()
    }
}
