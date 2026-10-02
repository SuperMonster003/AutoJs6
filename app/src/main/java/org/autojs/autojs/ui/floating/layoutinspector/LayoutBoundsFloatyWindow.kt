package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.event.BackCompat
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchBar
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchSession
import org.autojs.autojs.util.EventUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 12, 2017.
 * Modified by SuperMonster003 as of Oct 2, 2026.
 */
open class LayoutBoundsFloatyWindow @JvmOverloads constructor(
    private val capture: Capture,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(capture, context, isServiceRelied) {

    private lateinit var mLayoutBoundsView: LayoutBoundsView

    private lateinit var mSearchBar: InspectorSearchBar

    private val mSearchListener = InspectorSearchSession.Listener { mLayoutBoundsView.invalidate() }

    private val mSearchHost = object : InspectorSearchBar.Host {

        override fun onCurrentMatchChanged(node: NodeInfo) {
            setLayoutSelectedNode(node)
            mLayoutBoundsView.setSelectedNode(node)
        }

        override fun onShowNodeInfo(node: NodeInfo) {
            setLayoutSelectedNode(node)
            mLayoutBoundsView.setSelectedNode(node)
            showNodeInfo()
        }

        override fun onExpandedChanged(expanded: Boolean) {
            requireSearchSession().isBarExpandedInBounds = expanded
        }

    }

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

    /**
     * The bounds view fills the window; the search bar (collapsed to its pill by default, roadmap
     * D9) floats over it and gets the whole screen so the pill can be dragged anywhere, touches
     * outside the bar and the pill fall through to the bounds view. The container handles keys.
     * zh-CN: 边框视图占满窗口; 搜索栏 (默认收起为浮标, Roadmap D9) 浮于其上并占据整屏以便浮标可拖到任意位置, 搜索栏与浮标之外
     * 的触摸落到边框视图. 容器处理按键.
     */
    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService)

        val boundsView = LayoutBoundsView(context).also { mLayoutBoundsView = it }

        val searchBar = createSearchBar(defaultExpanded = false, expanded = requireSearchSession().isBarExpandedInBounds, host = mSearchHost)
            .also { mSearchBar = it }

        return object : FrameLayout(context) {
            @Suppress("DEPRECATION")
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { onBack() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { container ->
            container.addView(boundsView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            container.addView(searchBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
            BackCompat.installViewBackHandler(container, BackCompat.Priority.OVERLAY) { onBack() }
        }
    }

    override fun onViewCreated(v: View) {
        val session = requireSearchSession()
        mLayoutBoundsView.let { view ->
            view.match = session
            view.setOnNodeInfoSelectListener { info: NodeInfo ->
                setLayoutSelectedNode(info)
                session.setCurrentMatch(info)
                showMenuForNode(info)
            }
            view.boundsPaint.strokeWidth = 2f
            view.setRootNode(capture.root)
            getLayoutSelectedNode()?.let { view.setSelectedNode(it) }
        }
        session.addListener(mSearchListener)
    }

    override fun close() {
        searchSession?.removeListener(mSearchListener)
        super.close()
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

    private fun onBack() {
        if (!mSearchBar.onBackPressed()) close()
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
