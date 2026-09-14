package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.KeyEvent
import android.view.View
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.event.BackCompat
import org.autojs.autojs.util.EventUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 12, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
open class LayoutBoundsFloatyWindow @JvmOverloads constructor(
    private val capture: Capture,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(capture, context, isServiceRelied) {

    private lateinit var mLayoutBoundsView: LayoutBoundsView

    override val popMenuActions = listOf(
        R.string.text_show_widget_information to ::showNodeInfo,
        R.string.text_show_layout_hierarchy to ::showLayoutHierarchy,
        R.string.text_generate_code to ::generateCode,
        R.string.text_select_parent_node to ::selectParentNode,
        SPLIT_LINE,
        R.string.text_hide_node to ::excludeNode,
        R.string.text_hide_same_frame_nodes to ::excludeAllBoundsSameNodeInternal,
        SPLIT_LINE,
        R.string.text_switch_window to ::switchWindow,
        R.string.text_save_layout_to_file to ::saveLayoutToFile,
        R.string.text_exit to ::close,
    )

    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService)

        return object : LayoutBoundsView(context) {
            @Suppress("DEPRECATION")
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { close() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { view ->
            mLayoutBoundsView = view
            BackCompat.installViewBackHandler(view, BackCompat.Priority.OVERLAY) { close() }
        }
    }

    override fun onViewCreated(v: View) {
        mLayoutBoundsView.let { view ->
            view.setOnNodeInfoSelectListener { info: NodeInfo ->
                setLayoutSelectedNode(info)
                showMenuForNode(info)
            }
            view.boundsPaint.strokeWidth = 2f
            view.setRootNode(capture.root)
            getLayoutSelectedNode()?.let { view.setSelectedNode(it) }
        }
    }

    /**
     * The parent is outlined and the menu reopened at its bottom edge, so the selection can be
     * walked up level by level without leaving the menu.
     * zh-CN: 勾出父节点边框并在其底边重新弹出菜单, 这样可以不离开菜单逐级向上选择.
     */
    override fun onParentNodeSelected(node: NodeInfo) {
        mLayoutBoundsView.setSelectedNode(node)
        mLayoutBoundsView.post { showMenuForNode(node) }
    }

    private fun showMenuForNode(info: NodeInfo) {
        val view = mLayoutBoundsView
        view.isFocusable = false
        getBubblePopMenu().let { menu ->
            val width = menu.contentView.measuredWidth
            val bounds = info.boundsInScreen
            val x = bounds.centerX() - width / 2
            val y = bounds.bottom - view.statusBarHeight
            if (width <= 0) {
                runCatching {
                    menu.preMeasure()
                }
            }
            menu.showAsDropDownAtLocation(view, bounds.height(), x, y)
        }
        view.isFocusable = true
    }

    private fun excludeAllBoundsSameNodeInternal() {
        excludeAllBoundsSameNode(mLayoutBoundsView)
    }

}
