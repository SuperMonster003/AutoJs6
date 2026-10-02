package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.TreeMatch
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
open class LayoutHierarchyFloatyWindow @JvmOverloads constructor(
    private val capture: Capture,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(capture, context, isServiceRelied) {

    private lateinit var mLayoutHierarchyView: LayoutHierarchyView

    private lateinit var mSearchBar: InspectorSearchBar

    /** The match the tree was last pruned with; cursor-only changes do not reload the tree. zh-CN: 树上次据以裁剪的匹配结果; 仅游标变化时不重载树. */
    private var mAppliedMatch: TreeMatch<InspectorNode>? = null

    private val mSearchListener = InspectorSearchSession.Listener { session ->
        if (session.match !== mAppliedMatch) {
            mAppliedMatch = session.match
            mLayoutHierarchyView.setMatch(session)
        }
    }

    private val mSearchHost = object : InspectorSearchBar.Host {

        override fun onCurrentMatchChanged(node: NodeInfo) {
            setLayoutSelectedNode(node)
            mLayoutHierarchyView.setSelectedNode(node)
        }

        override fun onShowNodeInfo(node: NodeInfo) {
            setLayoutSelectedNode(node)
            mLayoutHierarchyView.setSelectedNode(node)
            showNodeInfo()
        }

        /** Regions are dragged on the screen, which only the bounds window shows. zh-CN: 区域需在屏幕上拖出, 只有范围窗口能提供. */
        override fun onSelectRegionRequested(): Boolean = false

        override fun onExpandedChanged(expanded: Boolean) {
            requireSearchSession().isBarExpandedInHierarchy = expanded
        }

    }

    override val popMenuActions = listOf(
        R.string.text_show_widget_information to ::showNodeInfo,
        R.string.text_show_layout_bounds to ::showLayoutBounds,
        R.string.text_generate_code to ::generateCode,
        R.string.text_select_parent_node to ::selectParentNode,
        SPLIT_LINE,
        R.string.text_switch_window to ::switchWindow,
        R.string.text_save_layout_to_file to ::saveLayoutToFile,
        R.string.text_exit to ::close,
    )

    /**
     * The search bar overlays the top of the window and the tree starts below it; the tree is inside
     * a horizontal scroller so that deeply nested rows, wider than the screen, can be read in full.
     * The bar view covers the whole window so that its history drop-down can overlay the tree (touches
     * it does not use fall through). The container is the window root and therefore handles keys (the
     * back key is offered to the search bar first).
     * zh-CN: 搜索栏叠在窗口顶部, 树从其下方开始; 树放在横向滚动容器内, 比屏幕更宽的深层行可以完整阅读. 搜索栏视图覆盖整个窗口,
     * 以便其历史下拉可以叠在树之上 (它不使用的触摸会透传下去). 容器是窗口根视图, 因而由它处理按键 (返回键先交给搜索栏).
     */
    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService)

        val hierarchyView = LayoutHierarchyView(context).also { mLayoutHierarchyView = it }

        val scroller = HorizontalScrollView(context).also { scroller ->
            scroller.isFillViewport = true
            scroller.addView(hierarchyView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // Bounds are drawn in screen coordinates; redraw them when the tree is scrolled sideways.
            // zh-CN: 边框按屏幕坐标绘制, 树横向滚动时需要重绘.
            scroller.setOnScrollChangeListener { _, _, _, _, _ -> hierarchyView.invalidate() }
        }

        val searchBar = createSearchBar(defaultExpanded = true, expanded = requireSearchSession().isBarExpandedInHierarchy, host = mSearchHost)
            .also { mSearchBar = it }

        return object : FrameLayout(context) {
            @Suppress("DEPRECATION")
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { onBack() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { container ->
            val scrollerParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = searchBar.barHeightPx
            }
            container.addView(scroller, scrollerParams)
            container.addView(searchBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP))
            BackCompat.installViewBackHandler(container, BackCompat.Priority.OVERLAY) { onBack() }
        }
    }

    override fun onViewCreated(v: View) {
        val background = serviceContext.getColor(R.color.layout_hierarchy_window_background) /* color_shadow */
        v.setBackgroundColor(background)
        val session = requireSearchSession()
        mLayoutHierarchyView.apply {
            setShowClickedNodeBounds(true)
            boundsPaint.strokeWidth = 3f
            boundsPaint.color = context.getColor(R.color.layout_hierarchy_window_node_bounds)
            setOnItemLongClickListener { view: View, info: NodeInfo ->
                setLayoutSelectedNode(info)
                showMenuForRow(view)
            }
            setRootNode(capture.root)
            mAppliedMatch = session.match
            setMatch(session)
            getLayoutSelectedNode()?.let { setSelectedNode(it) }
        }
        session.addListener(mSearchListener)
    }

    override fun close() {
        searchSession?.removeListener(mSearchListener)
        super.close()
    }

    override fun onParentNodeSelected(node: NodeInfo) {
        mLayoutHierarchyView.setSelectedNode(node)
    }

    private fun onBack() {
        if (!mSearchBar.onBackPressed()) close()
    }

    private fun showMenuForRow(row: View) {
        getBubblePopMenu().let { menu ->
            val width = menu.contentView.measuredWidth
            val x = row.width / 2 - width / 2
            val y = row.bottom
            if (width <= 0) {
                menu.preMeasure()
            }
            menu.showAsDropDownAtLocation(row, row.height, x, y)
        }
    }

}
