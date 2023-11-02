/**
 * Copyright 2018 WHO<980008027@qq.com>
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Modified by project: https://github.com/980008027/JsDroidEditor
 */
package org.autojs.autojs.ui.edit.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.widget.TextViewHelper
import androidx.appcompat.widget.AppCompatEditText
import org.autojs.autojs.ui.edit.editor.CodeEditor.BreakpointChangeListener
import org.autojs.autojs.ui.edit.editor.CodeEditor.CursorChangeCallback
import org.autojs.autojs.ui.edit.editor.JavaScriptHighlighter.HighlightTokens
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.ui.edit.theme.TokenMapping
import org.autojs.autojs.util.StringUtils.indexOf
import org.autojs.autojs.util.StringUtils.lastIndexOf
import org.mozilla.javascript.Token
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by Administrator on 2018/2/11.
 * Modified by SuperMonster003 as of May 1, 2023.
 */
class CodeEditText : AppCompatEditText {

    // 文字范围
    private var mParentScrollView: HVScrollView? = null

    @Volatile
    private var mHighlightTokens: HighlightTokens? = null

    private var mTheme: Theme = Theme.getDefault(context)
    private val mLineHighlightPaint = Paint().apply { style = Paint.Style.FILL }
    private var mFirstLineForDraw = -1
    private var mLastLineForDraw = 0
    private val mMatchingBrackets = intArrayOf(-1, -1)
    private var mUnmatchedBracket = -1
    private var mDebuggingLine = -1
    private var mCursorChangeCallbacks: CopyOnWriteArrayList<CursorChangeCallback>? = null

    private val currentLine: Int
        get() = layout?.let { LayoutHelper.getLineOfChar(it, selectionStart) } ?: -1

    val breakpoints = LinkedHashMap<Int, CodeEditor.Breakpoint>()

    var breakpointChangeListener: BreakpointChangeListener? = null

    var debuggingLine: Int
        get() = mDebuggingLine
        set(debuggingLine) {
            mDebuggingLine = debuggingLine
            invalidate()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    init {
        // 设值背景透明
        setBackgroundColor(Color.TRANSPARENT)
        // 设置字体颜色
        setTextColor(Color.TRANSPARENT)
        // 设置字体
        typeface = Typeface.MONOSPACE
        gravity = Gravity.START
        setHorizontallyScrolling(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        }
        mCursorChangeCallbacks = CopyOnWriteArrayList()
    }

    @SuppressLint("WrongConstant")
    override fun getAutofillType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AUTOFILL_TYPE_NONE else 0

    fun setTheme(theme: Theme) {
        mTheme = theme
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (mParentScrollView == null) {
            mParentScrollView = parent as HVScrollView
        }
        if (layout == null) {
            super.onDraw(canvas)
            invalidate()
            return
        }
        updatePaddingForGutter()
        updateLineRangeForDraw(canvas)

        // 绘制行高亮需要在绘制光标之前
        drawLineHighlights(canvas)

        // 调用 super.onDraw 绘制光标和选择高亮
        // 因为字体颜色被设置为透明
        // 因此 super.onDraw 绘制的字体不显示
        // TODO: 2018/2/24 优化效率。不绘制透明字体。
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(0f, extendedPaddingTop.toFloat())
        drawText(canvas)
        canvas.restore()
    }

    private fun drawLineHighlights(canvas: Canvas) {
        val debugHighlightLine = mDebuggingLine
        if (debugHighlightLine != currentLine) {
            // 绘制当前行高亮
            mLineHighlightPaint.color = mTheme.lineHighlightBackgroundColor
            drawLineHighlight(canvas, mLineHighlightPaint, this.currentLine)
        }
        if (debugHighlightLine != -1) {
            mLineHighlightPaint.color = mTheme.debuggingLineBackgroundColor
            drawLineHighlight(canvas, mLineHighlightPaint, debugHighlightLine)
        }
    }

    private fun updateLineRangeForDraw(canvas: Canvas) {
        if (layout != null) {
            val lineRange = getLineRangeForDraw(layout, canvas)
            mFirstLineForDraw = LayoutHelper.unpackRangeStartFromLong(lineRange)
            mLastLineForDraw = LayoutHelper.unpackRangeEndFromLong(lineRange)
        }
    }

    private fun updatePaddingForGutter() {
        // 根据行号计算左边距padding 留出绘制行号的空间
        val max = lineCount.toString()
        val gutterWidth = paint.measureText(max) + 20
        if (paddingLeft.toFloat() != gutterWidth) {
            setPadding(gutterWidth.toInt(), 0, 0, 0)
        }
    }

    // 该方法中内联了很多函数来提高效率 但是 这是必要的吗？？？
    // 绘制文本着色
    private fun drawText(canvas: Canvas) {
        var line = mFirstLineForDraw
        if (line < 0) {
            return
        }
        val textLength = mHighlightTokens?.text?.length ?: 0
        val scrollX = (mParentScrollView!!.scrollX + scrollX - paddingLeft).coerceAtLeast(0)
        while (line <= mLastLineForDraw && line < lineCount) {
            val lineBottom = layout.getLineTop(line + 1)
            val lineTop = layout.getLineTop(line)
            val lineBaseline = lineBottom - layout.getLineDescent(line)
            val highlightTokens = mHighlightTokens

            // drawLineNumber
            val lineNumberText = (line + 1).toString()

            // if there is a breakpoint at this line, draw highlight background for line number
            if (breakpoints.containsKey(line)) {
                canvas.drawRect(0f, lineTop.toFloat(), (paddingLeft - 10).toFloat(), lineBottom.toFloat(), paint.apply { color = mTheme.breakpointColor })
            }
            canvas.drawText(lineNumberText, 0, lineNumberText.length, 10f, lineBaseline.toFloat(), paint.apply { color = mTheme.lineNumberColor })
            if (highlightTokens == null) {
                line++
                continue
            }

            // drawCode
            val lineStart = layout.getLineStart(line)
            if (lineStart >= textLength) {
                return
            }
            val lineEnd = layout.getLineVisibleEnd(line).coerceAtMost(highlightTokens.colors.size)
            val visibleCharStart = getVisibleCharIndex(paint, scrollX, lineStart, lineEnd)
            val visibleCharEnd = getVisibleCharIndex(paint, scrollX + mParentScrollView!!.width, lineStart, lineEnd) + 1
            var previousColorPos = visibleCharStart.coerceAtMost(highlightTokens.colors.size - 1)
            var previousColor = when (previousColorPos) {
                mUnmatchedBracket -> mTheme.getColorForToken(Token.ERROR)
                mMatchingBrackets[0], mMatchingBrackets[1] -> mTheme.getColorForToken(TokenMapping.TOKEN_MATCHED_BRACKET)
                else -> highlightTokens.colors[previousColorPos]
            }
            var i = previousColorPos
            while (i < visibleCharEnd) {
                val color = when (i) {
                    mUnmatchedBracket -> mTheme.getColorForToken(Token.ERROR)
                    mMatchingBrackets[0], mMatchingBrackets[1] -> mTheme.getColorForToken(TokenMapping.TOKEN_MATCHED_BRACKET)
                    else -> highlightTokens.colors[i]
                }
                if (previousColor != color) {
                    paint.color = previousColor
                    val offsetX = paint.measureText(text, lineStart, previousColorPos)
                    canvas.drawText(text!!, previousColorPos, i, paddingLeft + offsetX, lineBaseline.toFloat(), paint)
                    previousColor = color
                    previousColorPos = i
                }
                i++
            }
            paint.color = previousColor
            val offsetX = paint.measureText(text, lineStart, previousColorPos)
            if (previousColorPos < 0 || visibleCharEnd > textLength || previousColorPos >= visibleCharEnd) {
                Log.e(TAG, "IndexOutOfBounds: previousColorPos = $previousColorPos, visibleCharEnd = $visibleCharEnd, textLength = $textLength")
                // postInvalidate();
                return
            }
            try {
                canvas.drawText(text!!, previousColorPos, visibleCharEnd, paddingLeft + offsetX, lineBaseline.toFloat(), paint)
            } catch (ex: IndexOutOfBoundsException) {
                Log.e(TAG, "IndexOutOfBounds: previousColorPos = $previousColorPos, visibleCharEnd = $visibleCharEnd, textLength = $textLength")
                ex.printStackTrace()
                return
            }
            line++
        }
    }

    private fun drawLineHighlight(canvas: Canvas, paint: Paint, line: Int) {
        if (layout == null || line < 0 || line < mFirstLineForDraw || line > mLastLineForDraw || mFirstLineForDraw < 0) {
            return
        }
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineTop(line + 1)
        canvas.drawRect(0f, lineTop.toFloat(), canvas.width.toFloat(), lineBottom.toFloat(), paint)
    }

    private fun getVisibleCharIndex(paint: Paint, x: Int, lineStart: Int, lineEnd: Int): Int {
        if (x == 0) {
            return lineStart
        }
        var low = lineStart
        var high = lineEnd - 1
        while (low < high) {
            val mid = high + low ushr 1
            val midX = paint.measureText(text, lineStart, mid + 1)
            if (x < midX) {
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return low
    }

    private fun getLineRangeForDraw(layout: Layout, canvas: Canvas): Long {
        canvas.save()
        val scrollY = mParentScrollView!!.scrollY + scrollY
        val clipTop = (if (scrollY == 0) 0 else (extendedPaddingTop + scrollY - mParentScrollView!!.paddingTop)).toFloat()
        canvas.clipRect(0f, clipTop, width.toFloat(), (scrollY + mParentScrollView!!.height).toFloat())
        return LayoutHelper.getLineRangeForDraw(layout, canvas).also {
            canvas.restore()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // 调用父类的 onSelectionChanged 时会发送一个 AccessibilityEvent, 当文本过大时造成异常
        // super.onSelectionChanged(selStart, selEnd);
        // 父类构造函数会调用 onSelectionChanged, 此时 mCursorChangeCallbacks 还没有初始化
        super.onSelectionChanged(selStart, selEnd)
        mCursorChangeCallbacks?.let { it.takeUnless { it.isEmpty() } } ?: return
        if (selStart != selEnd) return
        callCursorChangeCallback(text, selStart)
        matchesBracket(text, selStart)
    }

    private fun matchesBracket(text: CharSequence?, cursor: Int) {
        if (checkBracketMatchingAt(text, cursor)) return
        if (checkBracketMatchingAt(text, cursor - 1)) return
        mMatchingBrackets[0] = -1
        mMatchingBrackets[1] = -1
        mUnmatchedBracket = -1
    }

    private fun checkBracketMatchingAt(text: CharSequence?, cursor: Int): Boolean {
        if (cursor < 0 || cursor >= text!!.length) {
            return false
        }
        val i = BracketMatching.bracketMatching(text, cursor)
        if (i >= 0) {
            mMatchingBrackets[0] = cursor
            mMatchingBrackets[1] = i
            mUnmatchedBracket = -1
            return true
        }
        if (i == BracketMatching.UNMATCHED_BRACKET) {
            mUnmatchedBracket = cursor
            mMatchingBrackets[0] = -1
            mMatchingBrackets[1] = -1
            return true
        }
        return false
    }

    private fun callCursorChangeCallback(text: CharSequence?, sel: Int) {
        if (text!!.isEmpty()) {
            return
        }
        if (mCursorChangeCallbacks!!.isEmpty()) {
            return
        }
        val lineStart = (lastIndexOf(text, '\n', sel - 1) + 1).coerceIn(0, text.length - 1)
        var lineEnd = indexOf(text, '\n', sel)
        if (lineEnd < 0) {
            lineEnd = text.length
        }
        if (lineEnd < lineStart || lineStart < 0 || lineEnd > text.length) {
            return
        }
        val line = text.subSequence(lineStart, lineEnd).toString()
        val cursor = sel - lineStart
        mCursorChangeCallbacks!!.forEach { it.onCursorChange(line, cursor) }
    }

    fun addCursorChangeCallback(callback: CursorChangeCallback) {
        mCursorChangeCallbacks!!.add(callback)
    }

    fun removeCursorChangeCallback(callback: CursorChangeCallback) = mCursorChangeCallbacks!!.remove(callback)

    fun updateHighlightTokens(highlightTokens: HighlightTokens) {
        if (mHighlightTokens != null && mHighlightTokens!!.id >= highlightTokens.id) {
            return
        }
        mHighlightTokens = highlightTokens
        postInvalidate()
    }

    override fun setSelection(index: Int) {
        super.setSelection(index.coerceIn(0, text?.length ?: 0))
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        val savedState = super.onSaveInstanceState() as SavedState?
        if ((text?.length ?: -1) > 50 * 1024) {
            // avoid TransactionTooLargeException
            TextViewHelper.setText(savedState, "")
        }
        bundle.putParcelable("super_data", savedState)
        bundle.putInt("debugging_line", mDebuggingLine)
        val breakpointsTmp = IntArray(breakpoints.size)
        var i = 0
        breakpoints.values.forEach { breakpointsTmp[i++] = it.line }
        return bundle.apply { putIntArray("breakpoints", breakpointsTmp) }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle

        @Suppress("DEPRECATION")
        val superData = bundle.getParcelable<Parcelable>("super_data")

        mDebuggingLine = bundle.getInt("debugging_line", -1)
        bundle.getIntArray("breakpoints")?.let { its -> its.forEach { breakpoints[it] = CodeEditor.Breakpoint(it) } }
        super.onRestoreInstanceState(superData)
    }

    private var mTouchedLine = -1
    private var mTouchValid = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果行号区域被按下
        if (event.action == MotionEvent.ACTION_DOWN && event.x < paddingLeft) {
            // 则计算当前行, 如果行号有效, 记录起来
            val line = layout.getLineForVertical(event.y.toInt())
            if (line >= 0) {
                mTouchedLine = line
                mTouchValid = true
                return true
            }
        } else if (mTouchedLine >= 0) {
            // 如果之前已经是行号区域被按下了, 则之后的事件也要处理
            // 如果之后的触摸区域超出行号区域, 或者触摸的行号与第一次触摸事件时的不同, 则这一系列的触摸无效
            if (event.x >= paddingLeft || layout.getLineForVertical(event.y.toInt()) != mTouchedLine) {
                mTouchValid = false
            }
            if (event.action == MotionEvent.ACTION_UP) {
                // 当触摸有效时, 对那一行设置断点或取消断点
                if (mTouchValid) {
                    if (!removeBreakpoint(mTouchedLine)) {
                        addBreakpoint(mTouchedLine)
                    }
                    invalidate()
                }
                mTouchedLine = -1
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun removeBreakpoint(line: Int): Boolean {
        breakpoints.remove(line) ?: return false
        breakpointChangeListener?.onBreakpointChange(line, false)
        invalidate()
        return true
    }

    fun addBreakpoint(line: Int) {
        breakpoints[line] = CodeEditor.Breakpoint(line)
        breakpointChangeListener?.onBreakpointChange(line, true)
        invalidate()
    }

    fun removeAllBreakpoints() {
        breakpoints.clear()
        breakpointChangeListener?.onAllBreakpointRemoved(breakpoints.size)
        invalidate()
    }

    companion object {

        const val TAG = "CodeEditText"

    }

}