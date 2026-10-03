package org.autojs.autojs.ui.floating

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.LayoutSnapshotFiles
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.WindowInfo
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataSummary
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoOrderDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoRootNodeDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.parseWindowType
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.codegen.CodeGenerateDialog
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsView
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.NodeInfoDialog
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchBar
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchSession
import org.autojs.autojs.ui.widget.BubblePopupMenu
import org.autojs.autojs.util.ContextUtils.withHostConfiguration
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import kotlin.concurrent.thread
import kotlin.reflect.KFunction0

abstract class LayoutFloatyWindow(
    private val capture: Capture,
    private val context: Context,
    private val isServiceRelied: Boolean,
) : FullScreenFloatyWindow() {

    protected abstract val popMenuActions: List<Pair<Int, KFunction0<Unit>>?>

    private lateinit var mServiceContext: Context

    private var mLayoutSelectedNode: NodeInfo? = null

    /** True for a layout loaded from a file: there are no live windows to switch to or to save. zh-CN: 从文件读入的布局为 true: 没有可切换或可保存的实时窗口. */
    var isOffline = false

    /**
     * Search state shared by the inspector windows of one inspection (roadmap D5): created by the
     * first window, handed to the next one on every bounds / hierarchy switch.
     * zh-CN: 同一次布局分析的各窗口共享的搜索状态 (Roadmap D5): 由首个窗口创建, 范围 / 层次互切时交给下一个窗口.
     */
    var searchSession: InspectorSearchSession? = null

    protected val serviceContext: Context get() = mServiceContext

    private val mMainHandler = Handler(Looper.getMainLooper())

    private val mNodeInfoDialog by lazy { NodeInfoDialog(mServiceContext) }

    private var mCodeGenerateDialog: CodeGenerateDialog? = null

    fun onCreate(floatyService: FloatyService) {
        // The service context follows the system's language and night mode; the host app's settings are wanted here.
        // zh-CN: service context 跟随系统的语言与夜间模式, 此处需要的是宿主应用的设置.
        mServiceContext = if (isServiceRelied) ContextThemeWrapper(floatyService.withHostConfiguration(), R.style.AppTheme) else context
        if (searchSession == null) searchSession = InspectorSearchSession(capture.root)
    }

    protected fun requireSearchSession(): InspectorSearchSession = searchSession ?: InspectorSearchSession(capture.root).also { searchSession = it }

    /**
     * A search bar bound to the shared session; [defaultExpanded] is the initial state of this
     * window kind (roadmap D9) and decides whether the back key collapses an empty bar.
     * zh-CN: 绑定到共享会话的搜索栏; [defaultExpanded] 为该类窗口的初始状态 (Roadmap D9), 并决定返回键是否收起空的搜索栏.
     */
    protected fun createSearchBar(defaultExpanded: Boolean, expanded: Boolean, host: InspectorSearchBar.Host): InspectorSearchBar {
        return InspectorSearchBar(ContextThemeWrapper(mServiceContext, R.style.AppTheme)).apply {
            this.host = host
            collapsesOnBack = !defaultExpanded
            bind(requireSearchSession(), expanded)
        }
    }

    fun setLayoutSelectedNode(selectedNode: NodeInfo?) {
        mLayoutSelectedNode = selectedNode
    }

    protected fun getLayoutSelectedNode() = mLayoutSelectedNode

    // /**
    //  * Shows the "press volume down to exit" tip once per process, unless the user has ticked
    //  * "do not show again". Shown after attaching so the dialog can sit above the inspector window.
    //  * zh-CN: 每个进程仅提示一次 "按音量减键可退出", 用户勾选 "不再提示" 后不再显示; 附加到窗口之后再弹出, 以便对话框位于分析窗口之上.
    //  */
    // override fun onAttachToWindow(view: View?, manager: WindowManager?) {
    //     super.onAttachToWindow(view, manager)
    //     if (sExitTipsShownInProcess) return
    //     sExitTipsShownInProcess = true
    //     NotAskAgainDialog.Builder(mServiceContext, key(R.string.key_dialog_layout_inspector_exit_tips))
    //         .title(R.string.text_prompt)
    //         .content(R.string.text_volume_down_to_exit_layout_inspector)
    //         .positiveText(R.string.dialog_button_confirm)
    //         .positiveColorRes(R.color.dialog_button_attraction)
    //         .build()
    //         ?.also { it.window?.setType(FloatyWindowManger.getWindowType()) }
    //         ?.show()
    // }

    /**
     * Whether a pop menu action applies to the current selection; unavailable actions are left out
     * of the menu instead of being shown disabled.
     * zh-CN: 弹出菜单动作是否适用于当前选中节点; 不适用的动作直接不进菜单, 而非置灰显示.
     */
    private fun isActionAvailable(titleRes: Int) = when (titleRes) {
        R.string.text_select_parent_node -> mLayoutSelectedNode?.parent != null
        R.string.text_switch_window, R.string.text_save_layout_to_file -> !isOffline
        else -> true
    }

    protected fun getBubblePopMenu(): BubblePopupMenu {
        val availableActions = popMenuActions.filter { it == null || isActionAvailable(it.first) }
        val (keys, values) = availableActions.filterNotNull().unzip()
        val dividerPositions = mutableListOf<Int>()
        var realIndex = -1
        for (pair in availableActions) {
            if (pair != null) {
                realIndex += 1
                continue
            }
            dividerPositions.add(realIndex)
        }
        return BubblePopupMenu(mServiceContext, keys.map { context.getString(it) }, dividerPositions)
            .apply {
                setOnItemClickListener { _: View?, position: Int ->
                    dismiss()
                    values.elementAtOrNull(position)?.invoke()
                }
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
    }

    protected fun showNodeInfo() {
        mLayoutSelectedNode?.let { mNodeInfoDialog.show(it) }
    }

    protected fun showLayoutBounds() {
        close()
        LayoutBoundsFloatyWindow(capture, context, isServiceRelied)
            .apply {
                setLayoutSelectedNode(mLayoutSelectedNode)
                isOffline = this@LayoutFloatyWindow.isOffline
                searchSession = this@LayoutFloatyWindow.searchSession
            }
            .let { FloatyService.addWindow(it) }
    }

    protected fun showLayoutHierarchy() {
        close()
        LayoutHierarchyFloatyWindow(capture, context, isServiceRelied)
            .apply {
                setLayoutSelectedNode(mLayoutSelectedNode)
                isOffline = this@LayoutFloatyWindow.isOffline
                searchSession = this@LayoutFloatyWindow.searchSession
            }
            .let { FloatyService.addWindow(it) }
    }

    /**
     * Closes the window, then writes the captured tree to a layout file in the background and
     * reports the path; the explorer is refreshed so the new file shows up.
     * zh-CN: 关闭窗口, 然后在后台把捕获的节点树写入布局文件并提示路径; 刷新文件管理器以显示新文件.
     */
    protected fun saveLayoutToFile() {
        val root = capture.root
        close()
        thread(name = "LayoutSnapshotSave") {
            val result = runCatching { LayoutSnapshotFiles.save(root) }
            mMainHandler.post {
                result.onSuccess { file ->
                    Explorers.workspace().refreshAll()
                    ViewUtils.showToast(mServiceContext, mServiceContext.getString(R.string.text_layout_saved_to, file.path), true)
                }.onFailure { e ->
                    e.printStackTrace()
                    ViewUtils.showToast(mServiceContext, R.string.text_failed_to_save_layout, true)
                }
            }
        }
    }

    protected fun generateCode() {
        val target = mLayoutSelectedNode ?: return
        mCodeGenerateDialog?.dispose()
        mCodeGenerateDialog = CodeGenerateDialog(mServiceContext, capture.root, target)
            .also { it.build().showAdaptive() }
    }

    override fun close() {
        mCodeGenerateDialog?.dispose()
        mCodeGenerateDialog = null
        super.close()
    }

    /**
     * Moves the selection one level up to the parent of the selected node.
     * zh-CN: 把选中节点上移一级到其父节点.
     */
    protected fun selectParentNode() {
        val parent = mLayoutSelectedNode?.parent ?: return
        mLayoutSelectedNode = parent
        onParentNodeSelected(parent)
    }

    /** Lets the concrete window reflect a selection made through [selectParentNode]. zh-CN: 由具体窗口响应 [selectParentNode] 产生的选中变化. */
    protected open fun onParentNodeSelected(node: NodeInfo) {
        /* Empty body. */
    }

    protected fun switchWindow() {
        val windows = capture.windows
        val windowInfoList = windows.map { win: WindowInfo ->
            WindowInfoDataSummary(
                win,
                title = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_title), win.title, context.getString(R.string.text_captured_window_info_title_null)),
                order = WindowInfoOrderDataItem(context.getString(R.string.text_captured_window_info_order), win.order),
                type = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_type), parseWindowType(context, win.type), context.getString(R.string.text_captured_window_info_type_unknown)),
                packageName = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_package_name), win.packageName, context.getString(R.string.text_captured_window_info_package_name_unknown)),
                rootNode = WindowInfoRootNodeDataItem(context.getString(R.string.text_captured_window_info_root_node), win.rootClassName, context.getString(R.string.text_captured_window_info_root_node_unknown)),
            )
        }
        val builder = WindowSwitchingDialog(mServiceContext, windowInfoList).apply {
            sortItems(compareBy { it.order.rawValue })
        }
        val dialog = builder.build().showAdaptive()
        builder.itemsClickCallback = { _, position ->
            builder.itemList[position].window.root?.let {
                dialog.dismiss()
                capture.root = it
                searchSession?.setRoot(it)
                mLayoutSelectedNode = null
                showLayoutBounds()
            }
        }
    }

    protected fun excludeNode() {
        mLayoutSelectedNode?.let {
            it.hidden = true
            mLayoutSelectedNode = null
        }
    }

    protected fun excludeAllBoundsSameNode(layoutBoundsView: LayoutBoundsView) {
        mLayoutSelectedNode?.let {
            layoutBoundsView.hideAllBoundsSameNode(it)
            mLayoutSelectedNode = null
        }
    }

    companion object {

        @JvmStatic
        protected val SPLIT_LINE: Nothing? = null

        // private var sExitTipsShownInProcess = false

    }

}
