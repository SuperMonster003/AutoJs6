package org.autojs.autojs.ui.floating.layoutinspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.util.Collections

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

    var mStatusBarHeight = 0
        protected set

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        this.setWillNotDraw(false)
        mStatusBarHeight = ViewUtils.getStatusBarHeight(context)
    }

    fun setOnNodeInfoSelectListener(onNodeInfoSelectListener: OnNodeInfoSelectListener?) {
        mOnNodeInfoSelectListener = onNodeInfoSelectListener
    }

    fun setRootNode(rootNode: NodeInfo?) {
        mRootNode = rootNode
        mTouchedNode = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mBoundsInScreen == null) {
            mBoundsInScreen = IntArray(4)
            getLocationOnScreen(mBoundsInScreen)
            mStatusBarHeight = mBoundsInScreen!![1]
        }
        if (mTouchedNode != null) {
            canvas.save()
            if (mTouchedNodeBounds == null) {
                mTouchedNodeBounds = Rect(mTouchedNode!!.boundsInScreen)
                mTouchedNodeBounds!!.offset(0, -mStatusBarHeight)
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
            drawRect(canvas, mTouchedNode!!.boundsInScreen, mStatusBarHeight, boundsPaint)
        }
    }

    private fun draw(canvas: Canvas, node: NodeInfo?) {
        if (node == null) return
        drawRect(canvas, node.boundsInScreen, mStatusBarHeight, boundsPaint)
        for (child in node.children) {
            draw(canvas, child)
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mRootNode != null) {
            setSelectedNode(findNodeAt(mRootNode!!, event.rawX.toInt(), event.rawY.toInt()))
        }
        if (event.action == MotionEvent.ACTION_UP && mTouchedNode != null) {
            onNodeInfoClick(mTouchedNode!!)
            return true
        }
        return super.onTouchEvent(event)
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
                list.add(child)
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
        @JvmStatic
        fun drawRect(canvas: Canvas, rect: Rect?, statusBarHeight: Int, paint: Paint?) {
            val offsetRect = Rect(rect)
            offsetRect.offset(0, -statusBarHeight)
            canvas.drawRect(offsetRect, paint!!)
        }
    }

}