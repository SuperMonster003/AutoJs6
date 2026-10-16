package org.autojs.autojs.ui.explorer

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.TouchDelegate
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionPolicy
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.ui.storage.BatchRenameHistoryActivity
import org.autojs.autojs6.R

/**
 * One entry of the "More" menu in the title of the Explorer rename dialog (Batch Rename plugin roadmap D10c / D29).
 * The type parameter is the plugin action, so JVM tests can use plain values.
 *
 * zh-CN: 文件管理器重命名对话框标题栏 "更多" 菜单中的一项 (Batch Rename 插件路线图 D10c / D29). 类型参数为插件动作,
 * 使 JVM 测试可使用普通值.
 */
internal sealed interface ExplorerRenameDialogMenuEntry<out A> {

    /** A plugin action placed in the rename dialog for the renamed item. zh-CN: 放置在重命名对话框中并匹配被重命名项的插件动作. */
    data class PluginAction<out A>(val action: A) : ExplorerRenameDialogMenuEntry<A>

    /**
     * "Batch rename..." when no installed plugin offers an action, so the entry point stays discoverable; choosing it
     * explains how to install or enable the Batch Rename plugin.
     * zh-CN: 没有已安装插件提供动作时显示的 "批量重命名...", 使入口仍可被发现; 选择后说明如何安装或启用 Batch Rename 插件.
     */
    data object BatchRenameGuidance : ExplorerRenameDialogMenuEntry<Nothing>

    /**
     * "Rename history", always the last entry: opens the rename history page of the host (Batch Rename plugin roadmap
     * D51), which lists script batches as well, so it does not depend on the plugin.
     * zh-CN: 始终位于末尾的 "重命名历史": 打开宿主的重命名历史页面 (Batch Rename 插件路线图 D51). 该页面也列出脚本产生的批次, 因此不依赖插件.
     */
    data object RenameHistory : ExplorerRenameDialogMenuEntry<Nothing>
}

internal object ExplorerRenameDialogMenu {

    private const val LOG_TAG = "ExplorerRenameDialogMenu"

    /** material-dialogs shows at most ten title options. zh-CN: material-dialogs 的标题选项最多十项. */
    const val MAX_ENTRIES = 10

    private const val MIN_TOUCH_TARGET_DP = 48

    /**
     * The menu entries for the rename dialog actions of one item, followed by "Rename history", so plugin actions get
     * one place less than [MAX_ENTRIES]. Explorers without plugin entry points (all but the main file manager) get none
     * and keep the plain dialog.
     * zh-CN: 某一项的重命名对话框动作对应的菜单条目, 末尾为 "重命名历史", 因此插件动作最多占 [MAX_ENTRIES] 减一项. 没有插件入口的文件管理器
     * (主文件管理器以外) 不显示菜单, 保持原有对话框.
     */
    fun <A> entries(surface: ExplorerSurface, actions: List<A>): List<ExplorerRenameDialogMenuEntry<A>> = when {
        !ExplorerActionPolicy.allowsThirdPartyActions(surface) -> emptyList()
        actions.isEmpty() -> listOf(ExplorerRenameDialogMenuEntry.BatchRenameGuidance, ExplorerRenameDialogMenuEntry.RenameHistory)
        else -> actions.take(MAX_ENTRIES - 1).map { ExplorerRenameDialogMenuEntry.PluginAction(it) } + ExplorerRenameDialogMenuEntry.RenameHistory
    }

    /**
     * The [ExplorerActionEntryPoint.RENAME_DIALOG] actions for [item]; a missing or failing provider yields none.
     * zh-CN: [item] 的 [ExplorerActionEntryPoint.RENAME_DIALOG] 动作; 提供者缺失或出错时返回空列表.
     */
    fun resolveActions(
        provider: ExplorerPluginActionProvider?,
        surface: ExplorerSurface,
        item: ExplorerItem,
    ): List<ResolvedExplorerAction> {
        provider ?: return emptyList()
        return runCatching { provider.actionsFor(surface, ExplorerActionEntryPoint.RENAME_DIALOG, item) }
            .onFailure { error -> Log.w(LOG_TAG, "Failed to resolve rename dialog plugin actions", error) }
            .getOrDefault(emptyList())
    }

    /**
     * Title options for [entries]. Choosing a plugin action closes the rename dialog before [launch] runs, and so does
     * the rename history, whose undo may change the name being edited; the guidance shows its explanation above the
     * dialog, which stays open.
     * zh-CN: [entries] 对应的标题选项. 选择插件动作时先关闭重命名对话框再执行 [launch]; 重命名历史同样先关闭对话框 (其中的撤销可能改变正在
     * 编辑的名称); 引导说明显示在对话框之上, 对话框保持打开.
     */
    fun options(
        context: Context,
        entries: List<ExplorerRenameDialogMenuEntry<ResolvedExplorerAction>>,
        launch: (ResolvedExplorerAction) -> Unit,
    ): List<MaterialDialog.OptionMenuItemSpec> = entries.map { entry ->
        when (entry) {
            is ExplorerRenameDialogMenuEntry.PluginAction -> MaterialDialog.OptionMenuItemSpec(entry.action.menuTitle) { dialog ->
                dialog.dismiss()
                launch(entry.action)
            }
            ExplorerRenameDialogMenuEntry.BatchRenameGuidance -> MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_batch_rename)) { _ ->
                ExplorerBatchRenamePluginUi.showUnavailableDialog(context)
            }
            ExplorerRenameDialogMenuEntry.RenameHistory -> MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_rename_history)) { dialog ->
                dialog.dismiss()
                BatchRenameHistoryActivity.launch(context)
            }
        }
    }

    /**
     * Labels the 27 dp title options button for accessibility and widens its touch target to 48 dp, as the Ace editor
     * font dialog does.
     * zh-CN: 为 27 dp 宽的标题选项按钮设置无障碍标签, 并把触控区域扩展到 48 dp (与 Ace 编辑器字体对话框一致).
     */
    @JvmStatic
    fun decorateOptionsButton(dialog: MaterialDialog) {
        val options = dialog.optionsView ?: return
        options.contentDescription = dialog.context.getString(R.string.text_more)
        options.post {
            val parent = options.parent as? View ?: return@post
            val targetSize = (MIN_TOUCH_TARGET_DP * options.resources.displayMetrics.density).toInt()
            val bounds = Rect().also(options::getHitRect)
            val horizontalExtra = ((targetSize - bounds.width()).coerceAtLeast(0) + 1) / 2
            val verticalExtra = ((targetSize - bounds.height()).coerceAtLeast(0) + 1) / 2
            bounds.inset(-horizontalExtra, -verticalExtra)
            parent.touchDelegate = TouchDelegate(bounds, options)
        }
    }
}
