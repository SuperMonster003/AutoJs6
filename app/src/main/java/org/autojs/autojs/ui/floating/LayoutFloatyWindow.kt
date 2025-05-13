package org.autojs.autojs.ui.floating

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.app.AppLevelThemeDialogBuilder
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.WindowInfo
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataSummary
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoOrderDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoRootNodeDataItem
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.parseWindowType
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsView
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.NodeInfoView
import org.autojs.autojs.ui.widget.BubblePopupMenu
import org.autojs.autojs6.R
import kotlin.reflect.KFunction0

abstract class LayoutFloatyWindow(
    private val capture: Capture,
    private val context: Context,
    private val isServiceRelied: Boolean,
) : FullScreenFloatyWindow() {

    protected abstract val popMenuActions: LinkedHashMap<Int, KFunction0<Unit>>

    private lateinit var mServiceContext: Context

    private var mLayoutSelectedNode: NodeInfo? = null

    private val mNodeInfoView by lazy { NodeInfoView(mServiceContext) }

    private val mNodeInfoDialog by lazy {
        AppLevelThemeDialogBuilder(mServiceContext)
            .customView(mNodeInfoView, false)

            // @Overruled by SuperMonster003 on Jul 21, 2023.
            //  ! Author: 抠脚本人
            //  ! Related PR: http://pr.autojs6.com/98
            //  ! Reason: Pending processing [zh-CN: 将于后续版本继续处理].
            //  !
            //  # .positiveText("生成")
            //  # .onPositive { _, _ ->
            //  #     ViewUtils.showToast(context, "TODO")
            //  #     val selector = mNodeInfoView.getCheckedDate().joinToString(".")
            //  #     if (selector.isNotEmpty()) ClipboardUtils.setClip(context, selector)
            //  # }

            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
    }

    fun onCreate(floatyService: FloatyService) {
        mServiceContext = if (isServiceRelied) ContextThemeWrapper(floatyService, R.style.AppTheme) else context
    }

    fun setLayoutSelectedNode(selectedNode: NodeInfo?) {
        mLayoutSelectedNode = selectedNode
    }

    protected fun getLayoutSelectedNode() = mLayoutSelectedNode

    protected fun getBubblePopMenu(): BubblePopupMenu {
        return BubblePopupMenu(mServiceContext, popMenuActions.keys.map { context.getString(it) })
            .apply {
                setOnItemClickListener { _: View?, position: Int ->
                    dismiss()
                    popMenuActions.values.elementAtOrNull(position)?.invoke()
                }
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
    }

    protected fun showNodeInfo() {
        mLayoutSelectedNode?.let { mNodeInfoView.setNodeInfo(it) }
        mNodeInfoDialog.show()
    }

    protected fun showLayoutBounds() {
        close()
        LayoutBoundsFloatyWindow(capture, context, isServiceRelied)
            .apply { setLayoutSelectedNode(mLayoutSelectedNode) }
            .let { FloatyService.addWindow(it) }
    }

    protected fun showLayoutHierarchy() {
        close()
        LayoutHierarchyFloatyWindow(capture, context, isServiceRelied)
            .apply { setLayoutSelectedNode(mLayoutSelectedNode) }
            .let { FloatyService.addWindow(it) }
    }

    protected fun generateCode() {
        CodeGenerateDialog(context, capture.root, mLayoutSelectedNode)
            .build()
            .let { DialogUtils.showDialog(it) }
    }

    protected fun switchWindow() {
        val windows = capture.windows
        val windowInfoList = windows.map { win: WindowInfo ->
            WindowInfoDataSummary(win,
                title = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_title), win.title, context.getString(R.string.text_captured_window_info_title_null)),
                order = WindowInfoOrderDataItem(context.getString(R.string.text_captured_window_info_order), win.order),
                type = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_type), parseWindowType(context, win.type), context.getString(R.string.text_captured_window_info_type_unknown)),
                packageName = WindowInfoDataItem(context.getString(R.string.text_captured_window_info_package_name), win.packageName, context.getString(R.string.text_captured_window_info_package_name_unknown)),
                rootNode = WindowInfoRootNodeDataItem(context.getString(R.string.text_captured_window_info_root_node), win.rootClassName, context.getString(R.string.text_captured_window_info_root_node_unknown)),
            )
        }
        val builder = WindowSwitchingDialog(context, windowInfoList).apply {
            sortItems(compareBy { it.order.rawValue })
        }
        val dialog = DialogUtils.showDialog(builder.build())
        builder.itemsClickCallback = { _, position ->
            builder.itemList[position].window.root?.let {
                dialog.dismiss()
                capture.root = it
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

    protected fun excludeAllBoundsSameNode(layoutBoundsView: LayoutBoundsView ) {
        mLayoutSelectedNode?.let {
            layoutBoundsView.hideAllBoundsSameNode(it)
            mLayoutSelectedNode = null
        }
    }
}