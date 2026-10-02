package org.autojs.autojs.ui.floating.layoutinspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.floating.layoutinspector.search.InspectorMatchView
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/**
 * Created by Stardust on Mar 10, 2017.
 * Modified by SuperMonster003 as of Sep 25, 2022.
 */
open class LayoutBoundsView : View {

    private var mRootNode: NodeInfo? = null
    private var mTouchedNode: NodeInfo? = null
    private var mOnNodeInfoSelectListener: OnNodeInfoSelectListener? = null
    private var mTouchedNodeBounds: Rect? = null
    private var mBoundsInScreen: IntArray? = null

    open var touchedNodeBoundsColor = Color.RED
    open var normalNodeBoundsColor = Color.GREEN
    open var boundsPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
    }

    private var mFillingPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.layout_bounds_view_shadow)
    }

    var statusBarHeight = 0
        protected set

    /**
     * The active search (roadmap D4): only the nodes it matches are drawn and hit-tested; null or an
     * unfiltered match shows every node. Hidden nodes stay hidden either way.
     * zh-CN: 当前搜索 (Roadmap D4): 只绘制并点选其匹配的节点; null 或无筛选的匹配结果显示全部节点. 被隐藏的节点无论如何都不显示.
     */
    var match: InspectorMatchView? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The active region condition in screen coordinates (roadmap P3.4), outlined so the user sees
     * what the filter is bound to; null draws nothing.
     * zh-CN: 生效中的区域条件, 屏幕坐标 (Roadmap P3.4), 勾出边框让用户看到筛选绑定的范围; null 时不绘制.
     */
    var region: Rect? = null
        set(value) {
            field = value?.let(::Rect)
            invalidate()
        }

    private var mRegionListener: ((Rect) -> Unit)? = null

    /** Scratch rect for drawing the region without allocating in onDraw. zh-CN: 绘制区域用的暂存矩形, 避免在 onDraw 中分配. */
    private val mRegionDrawRect = Rect()
    private var mRegionDragStart: Point? = null
    private var mRegionDrag: Rect? = null

    private val mRegionFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ColorUtils.applyAlpha(ThemeColorManager.colorPrimary, REGION_FILL_ALPHA)
    }

    private val mRegionStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        color = ThemeColorManager.colorPrimary
    }

    /** Whether the next touch gesture drags a region instead of selecting a node. zh-CN: 下一次触摸手势是否用于拖出区域而非点选节点. */
    val isSelectingRegion: Boolean get() = mRegionListener != null

    /**
     * The next drag on this view becomes a region (screen coordinates) handed to [listener]; a drag
     * smaller than the touch slop cancels. One gesture only.
     * zh-CN: 本视图上的下一次拖拽成为交给 [listener] 的区域 (屏幕坐标); 小于触摸阈值的拖拽视为取消. 仅一次手势.
     */
    fun startRegionSelection(listener: (Rect) -> Unit) {
        mRegionListener = listener
        mRegionDragStart = null
        mRegionDrag = null
        setSelectedNode(null)
    }

    fun cancelRegionSelection() {
        mRegionListener = null
        mRegionDragStart = null
        mRegionDrag = null
        invalidate()
    }

    private fun isShown(node: NodeInfo): Boolean = !node.hidden && match?.isMatched(node) != false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        this.setWillNotDraw(false)
        statusBarHeight = ViewUtils.getStatusBarHeight(context)
    }

    fun setOnNodeInfoSelectListener(onNodeInfoSelectListener: OnNodeInfoSelectListener?) {
        mOnNodeInfoSelectListener = onNodeInfoSelectListener
    }

    fun setRootNode(rootNode: NodeInfo?) {
        mRootNode = rootNode
        mTouchedNode = null
    }

    fun hideAllBoundsSameNode(targetNode: NodeInfo?) {
        if (mRootNode == null || targetNode == null) {
            return
        }
        hideNodes(mRootNode, targetNode)
    }

    private fun hideNodes(parent: NodeInfo?, targetNode: NodeInfo) {
        if (parent == null) {
            return
        }
        if (parent.boundsInScreen == targetNode.boundsInScreen) {
            parent.hidden = true
        }
        if (parent.children.size == 0) {
            return
        }
        for (child in parent.children) {
            hideNodes(child, targetNode)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBoundsInScreen == null) {
            mBoundsInScreen = IntArray(4)
            getLocationOnScreen(mBoundsInScreen)
            statusBarHeight = mBoundsInScreen!![1]
        }
        if (mTouchedNode != null) {
            canvas.save()
            if (mTouchedNodeBounds == null) {
                mTouchedNodeBounds = Rect(mTouchedNode!!.boundsInScreen)
                mTouchedNodeBounds!!.offset(0, -statusBarHeight)
            }
            @Suppress("DEPRECATION")
            canvas.clipRect(mTouchedNodeBounds!!, Region.Op.DIFFERENCE)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mFillingPaint)
        if (mTouchedNode != null) {
            canvas.restore()
        }
        boundsPaint.color = normalNodeBoundsColor
        draw(canvas, mRootNode)
        if (mTouchedNode != null) {
            boundsPaint.color = touchedNodeBoundsColor
            drawRect(canvas, mTouchedNode!!.boundsInScreen, statusBarHeight, boundsPaint)
        }
        (mRegionDrag ?: region)?.let { rect ->
            mRegionDrawRect.set(rect)
            mRegionDrawRect.offset(0, -statusBarHeight)
            canvas.drawRect(mRegionDrawRect, mRegionFillPaint)
            canvas.drawRect(mRegionDrawRect, mRegionStrokePaint)
        }
    }

    private fun draw(canvas: Canvas, node: NodeInfo?) {
        if (node == null) return
        if (isShown(node)) {
            drawRect(canvas, node.boundsInScreen, statusBarHeight, boundsPaint)
        }
        for (child in node.children) {
            draw(canvas, child)
        }
    }
    /**
     * Every event is consumed: the view claims the gesture on ACTION_DOWN so that, when it is nested
     * in a container (the inspector window puts the search bar over it), the later ACTION_UP that
     * opens the node menu is still delivered to it.
     * zh-CN: 消费全部事件: 视图在 ACTION_DOWN 时认领手势, 这样当它嵌套在容器中 (分析窗口把搜索栏叠在其上) 时, 之后打开节点菜单的
     * ACTION_UP 仍会送达本视图.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isSelectingRegion) {
            onRegionTouch(event)
            return true
        }
        if (mRootNode != null) {
            setSelectedNode(findNodeAt(mRootNode!!, event.rawX.toInt(), event.rawY.toInt()))
        }
        if (event.action == MotionEvent.ACTION_UP && mTouchedNode != null) {
            onNodeInfoClick(mTouchedNode!!)
        }
        return true
    }

    private fun onRegionTouch(event: MotionEvent) {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mRegionDragStart = Point(x, y)
                mRegionDrag = Rect(x, y, x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val start = mRegionDragStart ?: return
                mRegionDrag = Rect(min(start.x, x), min(start.y, y), max(start.x, x), max(start.y, y))
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val listener = mRegionListener
                val start = mRegionDragStart
                cancelRegionSelection()
                if (listener == null || start == null) return
                // The up position is part of the drag too (injected gestures may end without a trailing move).
                // zh-CN: 抬起位置同样属于拖拽 (注入的手势可能没有末尾的移动事件).
                val rect = Rect(min(start.x, x), min(start.y, y), max(start.x, x), max(start.y, y))
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (rect.width() >= slop && rect.height() >= slop) listener(rect)
            }
            MotionEvent.ACTION_CANCEL -> cancelRegionSelection()
        }
    }

    private fun onNodeInfoClick(nodeInfo: NodeInfo) {
        mOnNodeInfoSelectListener?.onNodeSelect(nodeInfo)
    }

    private fun findNodeAt(node: NodeInfo, x: Int, y: Int): NodeInfo? {
        val list = ArrayList<NodeInfo>()
        findNodeAt(node, x, y, list)
        return if (list.isEmpty()) {
            null
        } else Collections.min(list, Comparator.comparingInt { o: NodeInfo -> o.boundsInScreen.width() * o.boundsInScreen.height() })
    }

    private fun findNodeAt(node: NodeInfo, x: Int, y: Int, list: MutableList<NodeInfo>) {
        for (child in node.children) {
            if (child.boundsInScreen.contains(x, y)) {
                if (isShown(child)) {
                    list.add(child)
                }
                findNodeAt(child, x, y, list)
            }
        }
    }

    fun setSelectedNode(selectedNode: NodeInfo?) {
        mTouchedNode = selectedNode
        mTouchedNodeBounds = null
        invalidate()
    }

    companion object {

        private const val REGION_FILL_ALPHA = 0.12

        /**
         * Draws [rect] (screen coordinates) onto [canvas], shifted up by [statusBarHeight] and
         * right by [dx] (the horizontal scroll offset of a scrolled list drawing over the screen).
         * zh-CN: 把屏幕坐标的 [rect] 画到 [canvas] 上, 上移 [statusBarHeight], 右移 [dx] (横向滚动列表叠加屏幕绘制时的滚动偏移).
         */
        @JvmStatic
        @JvmOverloads
        fun drawRect(canvas: Canvas, rect: Rect?, statusBarHeight: Int, paint: Paint?, dx: Int = 0) {
            val offsetRect = Rect(rect)
            offsetRect.offset(dx, -statusBarHeight)
            canvas.drawRect(offsetRect, paint!!)
        }
    }

}