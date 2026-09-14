package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.event.BackCompat
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.EventUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 12, 2017.
 * Modified by SuperMonster003 as of Sep 14, 2026.
 */
open class LayoutHierarchyFloatyWindow @JvmOverloads constructor(
    private val capture: Capture,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(capture, context, isServiceRelied) {

    private lateinit var mLayoutHierarchyView: LayoutHierarchyView

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
     * The tree sits inside a horizontal scroller so that deeply nested rows, which are wider than
     * the screen, can be read in full; the scroller is the window root and therefore handles keys.
     * zh-CN: 树放在横向滚动容器内, 比屏幕更宽的深层行可以完整阅读; 滚动容器是窗口根视图, 因而由它处理按键.
     */
    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService)

        val hierarchyView = LayoutHierarchyView(context).also { mLayoutHierarchyView = it }

        return object : HorizontalScrollView(context) {
            @Suppress("DEPRECATION")
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { close() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { scroller ->
            scroller.isFillViewport = true
            scroller.addView(hierarchyView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            // Bounds are drawn in screen coordinates; redraw them when the tree is scrolled sideways.
            // zh-CN: 边框按屏幕坐标绘制, 树横向滚动时需要重绘.
            scroller.setOnScrollChangeListener { _, _, _, _, _ -> hierarchyView.invalidate() }
            BackCompat.installViewBackHandler(scroller, BackCompat.Priority.OVERLAY) { close() }
        }
    }

    override fun onViewCreated(v: View) {
        val background = context.getColor(R.color.layout_hierarchy_window_background) /* color_shadow */
        v.setBackgroundColor(background)
        mLayoutHierarchyView.apply {
            setShowClickedNodeBounds(true)
            boundsPaint.strokeWidth = 3f
            boundsPaint.color = context.getColor(R.color.layout_hierarchy_window_node_bounds)
            setClickedBackgroundColor(context.getColor(R.color.layout_hierarchy_window_clicked_background))
            // The theme color, corrected until it reads on the window background in both day and night mode.
            // zh-CN: 主题色, 经对比度修正后在日间与夜间的窗口背景上都可读.
            setHighlightTextColor(ColorUtils.adjustThemeColorForContrast(ColorUtils.applyAlpha(background, 1.0), 4.5))
            setOnItemLongClickListener { view: View, info: NodeInfo ->
                setLayoutSelectedNode(info)
                showMenuForRow(view)
            }
            setRootNode(capture.root)
            getLayoutSelectedNode()?.let { setSelectedNode(it) }
        }
    }

    override fun onParentNodeSelected(node: NodeInfo) {
        mLayoutHierarchyView.setSelectedNode(node)
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
