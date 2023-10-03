package org.autojs.autojs.ui.floating

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.app.AppLevelThemeDialogBuilder
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.ui.codegeneration.CodeGenerateDialog
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.NodeInfoView
import org.autojs.autojs.ui.widget.BubblePopupMenu
import org.autojs.autojs6.R

abstract class LayoutFloatyWindow(private val rootNode: NodeInfo?, private val context: Context, private val isServiceRelied: Boolean) : FullScreenFloatyWindow() {

    private lateinit var mServiceContext: Context
    private lateinit var mActions: LinkedHashMap<Int, Runnable>

    private var mLayoutSelectedNode: NodeInfo? = null

    private val mNodeInfoView by lazy { NodeInfoView(mServiceContext) }

    private val mNodeInfoDialog by lazy {
        AppLevelThemeDialogBuilder(mServiceContext)
            .customView(mNodeInfoView, false)

            // @Overruled by SuperMonster003 on Jul 21, 2023.
            //  ! Author: 抠脚本人
            //  ! Related PR:
            //  ! http://pr.autojs6.com/98
            //  ! Reason:
            //  ! Pending processing.
            //  ! zh-CN: 将于后续版本继续处理.
            //  !
            // .positiveText("生成")
            // .onPositive { _, _ ->
            //     ViewUtils.showToast(context, "TODO")
            //     val selector = mNodeInfoView.getCheckedDate().joinToString(".")
            //     if (selector.isNotEmpty()) ClipboardUtils.setClip(context, selector)
            // }

            .build()
            .also { it.window!!.setType(FloatyWindowManger.getWindowType()) }
    }

    fun onCreate(floatyService: FloatyService, list: LinkedHashMap<Int, Runnable>) {
        mServiceContext = if (isServiceRelied) ContextThemeWrapper(floatyService, R.style.AppTheme) else context
        mActions = list
    }

    fun setLayoutSelectedNode(selectedNode: NodeInfo?) {
        mLayoutSelectedNode = selectedNode
    }

    protected fun getLayoutSelectedNode() = mLayoutSelectedNode

    protected fun getBubblePopMenu(): BubblePopupMenu {
        return BubblePopupMenu(mServiceContext, mActions.keys.map { context.getString(it) })
            .apply {
                setOnItemClickListener { _: View?, position: Int ->
                    dismiss()
                    mActions.values.elementAtOrNull(position)?.run()
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
        LayoutBoundsFloatyWindow(rootNode, context, isServiceRelied)
            .apply { setLayoutSelectedNode(mLayoutSelectedNode) }
            .let { FloatyService.addWindow(it) }
    }

    protected fun showLayoutHierarchy() {
        close()
        LayoutHierarchyFloatyWindow(rootNode, context, isServiceRelied)
            .apply { setLayoutSelectedNode(mLayoutSelectedNode) }
            .let { FloatyService.addWindow(it) }
    }

    protected fun generateCode() {
        CodeGenerateDialog(mServiceContext, context, rootNode, mLayoutSelectedNode)
            .build()
            .let { DialogUtils.showDialog(it) }
    }

}