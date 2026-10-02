package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.TreeMatch
import org.autojs.autojs.event.BackCompat
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchBar
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorSearchSession
import org.autojs.autojs.util.ColorUtils
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
     * The search bar sits above the tree, which is inside a horizontal scroller so that deeply
     * nested rows, wider than the screen, can be read in full; the vertical container is the window
     * root and therefore handles keys (the back key is offered to the search bar first).
     * zh-CN: 搜索栏位于树的上方; 树放在横向滚动容器内, 比屏幕更宽的深层行可以完整阅读; 纵向容器是窗口根视图, 因而由它处理按键
     * (返回键先交给搜索栏).
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

        return object : LinearLayout(context) {
            @Suppress("DEPRECATION")
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { onBack() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { container ->
            container.orientation = LinearLayout.VERTICAL
            container.addView(searchBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            container.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            BackCompat.installViewBackHandler(container, BackCompat.Priority.OVERLAY) { onBack() }
        }
    }

    override fun onViewCreated(v: View) {
        val background = context.getColor(R.color.layout_hierarchy_window_background) /* color_shadow */
        v.setBackgroundColor(background)
        val session = requireSearchSession()
        mLayoutHierarchyView.apply {
            setShowClickedNodeBounds(true)
            boundsPaint.strokeWidth = 3f
            boundsPaint.color = context.getColor(R.color.layout_hierarchy_window_node_bounds)
            setClickedBackgroundColor(context.getColor(R.color.layout_hierarchy_window_clicked_background))
            // The theme color, corrected until it reads on the window background in both day and night mode.
            // zh-CN: 主题色, 经对比度修正后在日间与夜间的窗口背景上都可读.
            setHighlightTextColor(ColorUtils.adjustThemeColorForContrast(ColorUtils.applyAlpha(background, 1.0), 4.5))
            setMatchHighlightColor(ColorUtils.applyAlpha(ThemeColorManager.colorPrimary, MATCH_HIGHLIGHT_ALPHA))
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

    companion object {

        private const val MATCH_HIGHLIGHT_ALPHA = 0.3

    }

}
