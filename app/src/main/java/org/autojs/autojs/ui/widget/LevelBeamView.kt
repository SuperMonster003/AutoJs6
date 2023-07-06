package org.autojs.autojs.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/3/10.
 * Modified by SuperMonster003 as of May 3, 2023.
 * Transformed by SuperMonster003 on May 3, 2023.
 */
class LevelBeamView : View {

    private var mLevel = 0
    private var mPaddingLeft = 0f
    private var mPaddingRight = 0f
    private var mLinesWidth = 0f
    private var mLinesOffset = 0f
    private var mLinePaint: Paint
    private var mColors: IntArray

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    fun setLevel(level: Int) {
        mLevel = level
        requestLayout()
    }

    init {
        setWillNotDraw(false)
        mPaddingLeft = resources.getDimension(R.dimen.level_beam_view_padding_left)
        mPaddingRight = resources.getDimension(R.dimen.level_beam_view_padding_right)
        mLinesWidth = resources.getDimension(R.dimen.level_beam_view_line_width)
        mLinesOffset = resources.getDimension(R.dimen.level_beam_view_line_offset)
        mLinePaint = Paint().apply {
            isAntiAlias = true
            color = Color.RED
            style = Paint.Style.FILL
            strokeWidth = mLinesWidth
        }
        mColors = intArrayOf(
            context.getColor(R.color.layout_hierarchy_view_level_beam_1),
            context.getColor(R.color.layout_hierarchy_view_level_beam_2),
            context.getColor(R.color.layout_hierarchy_view_level_beam_3),
            context.getColor(R.color.layout_hierarchy_view_level_beam_4),
            context.getColor(R.color.layout_hierarchy_view_level_beam_5),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (mPaddingLeft + mPaddingRight + (mLevel + 1) * (mLinesWidth + mLinesOffset)).toInt()
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (lvl in 0..mLevel) {
            var lineX = (mPaddingLeft + lvl * mLinesWidth)
            if (lvl >= 1) {
                lineX += (lvl * mLinesOffset)
            }
            mLinePaint.color = getColorForLevel(lvl)
            canvas.drawLine(lineX, 0f, lineX, height.toFloat(), mLinePaint)
        }
    }

    private fun getColorForLevel(level: Int): Int {
        return mColors[level % mColors.size]
    }

}