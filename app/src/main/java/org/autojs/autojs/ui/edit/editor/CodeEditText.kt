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
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.TextViewHelper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.withTranslation
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
 * Created by Administrator on Feb 11, 2018.
 * Modified by SuperMonster003 as of May 1, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 8, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 8, 2026.
 */
class CodeEditText : AppCompatEditText {

    // 文字范围
    private var mParentScrollView: HVScrollView? = null

    @Volatile
    private var mHighlightTokens: HighlightTokens? = null

    // Loading state, used to suppress expensive callbacks during bulk text insertion.
    // zh-CN: 加载状态, 用于在批量插入文本时抑制高开销回调.
    @Volatile
    private var mLoadingText = false

    // Fixed gutter digits used during loading to avoid frequent requestLayout().
    // zh-CN: 加载期间使用固定 gutter 位数, 避免频繁 requestLayout().
    @Volatile
    private var mLoadingGutterDigits: Int = 3

    // Accessibility payload guardrail threshold.
    // zh-CN: 无障碍事件负载护栏阈值.
    private val mA11yLargeTextThresholdChars: Int = 64 * 1024

    private var mTheme: Theme = Theme.getDefault(context)
    private val mLineHighlightPaint = Paint().apply { style = Paint.Style.FILL }
    private var mFirstLineForDraw = -1
    private var mLastLineForDraw = 0
    private val mMatchingBrackets = intArrayOf(-1, -1)
    private var mUnmatchedBracket = -1
    private var mDebuggingLine = -1
    private var mCursorChangeCallbacks: CopyOnWriteArrayList<CursorChangeCallback>? = null

    // Callback when user touches the editor text area (not gutter).
    // This is used by large-file loader to avoid forcing caret to 0 after user interaction.
    //
    // zh-CN:
    // 当用户触摸编辑器文本区域 (非行号区域) 时的回调.
    // 用于大文件加载逻辑: 若用户已交互, 则避免加载完成后强制将光标跳到 0.
    @Volatile
    var onUserTouchInTextArea: (() -> Unit)? = null

    // Read-only state.
    // zh-CN: 只读状态.
    private var mReadOnly = false

    // Backup of the original KeyListener, used to restore editable behavior.
    // zh-CN: 备份原始 KeyListener, 用于恢复可编辑行为.
    private var mOriginalKeyListener = keyListener

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

        // Prevent Android framework from saving/restoring huge editor text via View state.
        // Otherwise minimizing the Activity may trigger TransactionTooLargeException for large files.
        //
        // zh-CN:
        // 禁止系统通过 View state 自动保存/恢复超大的编辑器文本.
        // 否则在最小化 Activity 时, 大文件很容易触发 TransactionTooLargeException.
        isSaveEnabled = false
        freezesText = false

        // Ensure selection is possible by default.
        // zh-CN: 默认确保可以进行文本选择.
        setTextIsSelectable(true)
        isLongClickable = true

        // Update accessibility importance at init.
        // zh-CN: 初始化时更新无障碍重要性配置.
        updateAccessibilityImportanceIfNeeded()
    }

    // Toggle loading state.
    // zh-CN: 切换加载状态.
    fun setLoadingText(loading: Boolean) {
        mLoadingText = loading
        if (loading) {
            applyFixedGutterPaddingForLoading()
        } else {
            // Recompute gutter once after loading.
            // zh-CN: 加载结束后重新计算 gutter (仅一次).
            requestLayout()
            invalidate()
        }
    }

    // Expose loading state for outer components to suppress expensive UI updates.
    // zh-CN: 对外暴露加载状态, 以便外部组件抑制高开销 UI 更新.
    fun isLoadingText(): Boolean = mLoadingText

    // Configure a fixed gutter width for loading.
    // zh-CN: 配置加载期间的固定 gutter 宽度.
    fun setLoadingGutterDigits(digits: Int) {
        mLoadingGutterDigits = digits.coerceIn(2, 10)
        if (mLoadingText) {
            applyFixedGutterPaddingForLoading()
        }
    }

    private fun applyFixedGutterPaddingForLoading() {
        // Pre-allocate gutter width based on digits, e.g., "888888".
        // zh-CN: 基于位数预分配 gutter 宽度, 例如 "888888".
        val sample = "8".repeat(mLoadingGutterDigits)
        val gutterWidth = paint.measureText(sample) + 20
        if (paddingLeft.toFloat() != gutterWidth) {
            setPadding(gutterWidth.toInt(), 0, 0, 0)
        }
    }

    // Public API to toggle read-only mode.
    // zh-CN: 切换只读模式的公开接口.
    fun setReadOnly(readOnly: Boolean) {
        if (mReadOnly == readOnly) return
        mReadOnly = readOnly

        if (readOnly) {
            // Keep enabled/focusable so the user can select and copy text.
            // zh-CN: 保持 enabled/focusable, 让用户可以选择并复制文本.
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            setTextIsSelectable(true)
            isLongClickable = true
            isCursorVisible = true

            // Disable soft keyboard editing by removing KeyListener.
            // zh-CN: 通过移除 KeyListener 禁用软键盘编辑.
            if (mOriginalKeyListener == null) {
                mOriginalKeyListener = keyListener
            }
            keyListener = null

            showSoftInputOnFocus = false
        } else {
            // Restore editable behavior.
            // zh-CN: 恢复可编辑行为.
            keyListener = mOriginalKeyListener
            showSoftInputOnFocus = true
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        if (!mReadOnly) return ic

        // Block all text-mutating operations at the InputConnection layer.
        // zh-CN: 在 InputConnection 层阻止所有会修改文本的操作.
        return object : InputConnectionWrapper(ic, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = false
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = false
            override fun finishComposingText(): Boolean = false
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = false
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (mReadOnly) {
            // Allow copy/select actions, block cut/paste/replace actions.
            // zh-CN: 允许复制/选择相关操作, 阻止剪切/粘贴/替换等修改操作.
            when (id) {
                android.R.id.cut,
                android.R.id.paste,
                android.R.id.pasteAsPlainText,
                android.R.id.replaceText,
                    -> return false
            }
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mReadOnly) {
            // Block hardware-keyboard editing shortcuts.
            // zh-CN: 阻止硬件键盘的编辑快捷键.
            if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                return true
            }
            if (event.isCtrlPressed && (keyCode == KeyEvent.KEYCODE_V || keyCode == KeyEvent.KEYCODE_X)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("WrongConstant")
    override fun getAutofillType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AUTOFILL_TYPE_NONE else 0

    fun setTheme(theme: Theme) {
        mTheme = theme
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        try {
            mParentScrollView = parent as? HVScrollView ?: return super.onDraw(canvas)

            layout ?: return super.onDraw(canvas).also { postInvalidate() }

            updatePaddingForGutter()
            updateLineRangeForDraw(canvas)

            // 检查行范围是否有效
            if (mFirstLineForDraw < 0 || mFirstLineForDraw > mLastLineForDraw) {
                return super.onDraw(canvas)
            }

            // 绘制行高亮需要在绘制光标之前
            drawLineHighlights(canvas)

            // 调用 super.onDraw 绘制光标和选择高亮
            // 因为字体颜色被设置为透明
            // 因此 super.onDraw 绘制的字体不显示
            // TODO by Stardust on Feb 24, 2018.
            //  ! 优化效率.
            //  ! 不绘制透明字体.
            //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
            //  ! Optimize efficiency.
            //  ! Don't draw transparent fonts.
            super.onDraw(canvas)

            runCatching {
                // canvas.save()
                // canvas.translate(0f, extendedPaddingTop.toFloat())
                // drawText(canvas)
                // canvas.restore()
                canvas.withTranslation(0f, extendedPaddingTop.toFloat()) {
                    drawText(this)
                }
            }.onFailure { it.printStackTrace() }
        } catch (e: Exception) {
            e.printStackTrace()
            runCatching {
                super.onDraw(canvas)
            }.onFailure { it.printStackTrace() }
        }
    }

    private fun drawLineHighlights(canvas: Canvas) {
        try {
            if (lineCount <= 0) return

            val debugHighlightLine = mDebuggingLine
            val curLine = currentLine

            // 确保当前行在有效范围内
            if (curLine >= 0 && curLine < lineCount && debugHighlightLine != curLine) {
                // 绘制当前行高亮
                mLineHighlightPaint.color = mTheme.lineHighlightBackgroundColor
                drawLineHighlight(canvas, mLineHighlightPaint, curLine)
            }

            // 确保调试行在有效范围内
            if (debugHighlightLine >= 0 && debugHighlightLine < lineCount) {
                mLineHighlightPaint.color = mTheme.debuggingLineBackgroundColor
                drawLineHighlight(canvas, mLineHighlightPaint, debugHighlightLine)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        // During loading, keep gutter fixed to avoid setPadding() loops and relayout storms.
        // zh-CN: 加载期间保持 gutter 固定, 避免 setPadding() 循环与频繁 relayout.
        if (mLoadingText) {
            return
        }

        // 根据行号计算左边距 padding 留出绘制行号的空间
        val max = lineCount.toString()
        val gutterWidth = paint.measureText(max) + 20
        if (paddingLeft.toFloat() != gutterWidth) {
            setPadding(gutterWidth.toInt(), 0, 0, 0)
        }
    }

    // 该方法中内联了很多函数来提高效率, 但这是必要的吗?
    // 绘制文本着色
    private fun drawText(canvas: Canvas) {
        if (mFirstLineForDraw < 0) return

        val highlightTokens = mHighlightTokens
        val safeText = text ?: return
        val textLength = highlightTokens?.text?.length ?: 0
        val scrollX = (mParentScrollView!!.scrollX + scrollX - paddingLeft).coerceAtLeast(0)

        for (line in mFirstLineForDraw..lineCount.coerceAtMost(mLastLineForDraw)) {
            if (line >= lineCount) continue

            val lineNumber = line + 1
            val lineNumberText = "$lineNumber"
            val lineBottom = layout.getLineTop(lineNumber)
            val lineTop = layout.getLineTop(line)
            val lineBaseline = lineBottom - layout.getLineDescent(line)

            // if there is a breakpoint at this line, draw a highlight background for line number
            if (breakpoints.containsKey(line)) {
                canvas.drawRect(
                    /* left = */ 0f,
                    /* top = */ lineTop.toFloat(),
                    /* right = */ (paddingLeft - 10).toFloat(),
                    /* bottom = */ lineBottom.toFloat(),
                    /* paint = */ paint.apply { color = mTheme.breakpointColor },
                )
            }
            canvas.drawText(
                /* text = */ lineNumberText,
                /* start = */ 0,
                /* end = */ lineNumberText.length,
                /* x = */ 10f,
                /* y = */ lineBaseline.toFloat(),
                /* paint = */ paint.apply { color = mTheme.lineNumberColor },
            )

            val lineStart = layout.getLineStart(line)

            // Never draw line-break control characters.
            // zh-CN: 永远不要绘制换行等控制字符.
            var lineEnd = layout.getLineEnd(line).coerceAtMost(safeText.length)
            while (lineEnd > lineStart) {
                val ch = safeText[lineEnd - 1]
                if (ch == '\n' || ch == '\r') {
                    lineEnd--
                } else {
                    break
                }
            }

            // Fast path: no syntax highlighting, draw the line once with default color.
            // zh-CN: 快速路径: 无语法高亮时, 使用默认颜色一次性绘制整行.
            if (highlightTokens == null) {
                val visibleCharStart = getVisibleCharIndex(paint, scrollX, lineStart, lineEnd)
                val visibleCharEnd = (getVisibleCharIndex(paint, scrollX + mParentScrollView!!.width, lineStart, lineEnd) + 1)
                    .coerceAtMost(lineEnd)

                if (visibleCharStart >= visibleCharEnd) continue

                paint.color = mTheme.getColorForToken(Token.NAME)
                runCatching {
                    val offsetX = paint.measureText(safeText, lineStart, visibleCharStart)
                    canvas.drawText(safeText, visibleCharStart, visibleCharEnd, paddingLeft + offsetX, lineBaseline.toFloat(), paint)
                }.onFailure { it.printStackTrace() }
                continue
            }

            if (lineStart >= textLength) continue
            if (lineEnd > textLength) continue

            // If this is an empty line (or the line only contains line-break chars), skip drawing.
            // zh-CN: 如果这是空白行 (或该行只包含换行字符), 则跳过绘制.
            if (lineStart >= lineEnd) continue

            val localColors = highlightTokens.colors

            val visibleCharStart = getVisibleCharIndex(paint, scrollX, lineStart, lineEnd)
            var visibleCharEnd = getVisibleCharIndex(paint, scrollX + mParentScrollView!!.width, lineStart, lineEnd) + 1

            if (visibleCharStart >= visibleCharEnd) continue
            if (visibleCharStart >= safeText.length) continue
            if (visibleCharStart < 0) continue

            var previousColorPos = visibleCharStart.coerceIn(0, minOf(localColors.size, safeText.length) - 1)
            var previousColor = when (previousColorPos) {
                mUnmatchedBracket -> mTheme.getColorForToken(Token.ERROR)
                mMatchingBrackets[0], mMatchingBrackets[1] -> mTheme.getColorForToken(TokenMapping.TOKEN_MATCHED_BRACKET)
                else -> localColors[previousColorPos.coerceAtMost(localColors.size - 1)]
            }

            val safeVisibleCharEnd = minOf(visibleCharEnd, safeText.length, lineEnd)
            if (previousColorPos >= safeVisibleCharEnd) continue

            var i = previousColorPos
            while (i < safeVisibleCharEnd) {
                val color = when (i) {
                    mUnmatchedBracket -> mTheme.getColorForToken(Token.ERROR)
                    mMatchingBrackets[0], mMatchingBrackets[1] -> mTheme.getColorForToken(TokenMapping.TOKEN_MATCHED_BRACKET)
                    in 0 until localColors.size -> localColors[i]
                    else -> previousColor
                }

                if (previousColor != color) {
                    paint.color = previousColor
                    runCatching {
                        val offsetX = paint.measureText(safeText, lineStart, previousColorPos)
                        canvas.drawText(safeText, previousColorPos, i, paddingLeft + offsetX, lineBaseline.toFloat(), paint)
                    }
                    previousColor = color
                    previousColorPos = i
                }
                i++
            }
            paint.color = previousColor

            visibleCharEnd = minOf(visibleCharEnd.coerceAtMost(textLength), lineEnd)
            if (previousColorPos >= visibleCharEnd) continue

            val currentText = text ?: continue

            if (previousColorPos >= currentText.length || visibleCharEnd > currentText.length) {
                previousColorPos = previousColorPos.coerceIn(0, currentText.length - 1)
                visibleCharEnd = visibleCharEnd.coerceAtMost(currentText.length)
                if (previousColorPos >= visibleCharEnd) continue
            }

            runCatching {
                val offsetX = paint.measureText(safeText, lineStart, previousColorPos)
                canvas.drawText(safeText, previousColorPos, visibleCharEnd, paddingLeft + offsetX, lineBaseline.toFloat(), paint)
            }.onFailure {
                it.printStackTrace()
                runCatching {
                    paint.color = mTheme.lineNumberColor
                    canvas.drawText(
                        /* text = */ lineNumberText,
                        /* start = */ 0,
                        /* end = */ lineNumberText.length,
                        /* x = */ paddingLeft.toFloat(),
                        /* y = */ lineBaseline.toFloat(),
                        /* paint = */ paint,
                    )
                }
            }
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
        if (x <= 0 || lineStart >= lineEnd || lineStart >= text!!.length || lineEnd <= 0) {
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
        // Skip selection callbacks during bulk loading to keep UI responsive.
        // zh-CN: 批量加载期间跳过 selection 回调, 以保持 UI 响应.
        if (mLoadingText) {
            super.onSelectionChanged(selStart, selEnd)
            return
        }

        // Update accessibility importance when selection changes (input usually changes selection).
        // zh-CN: selection 变化时更新无障碍重要性 (输入通常会改变 selection).
        updateAccessibilityImportanceIfNeeded()

        // Avoid sending large accessibility events for huge text on every keystroke.
        // zh-CN: 避免在超大文本下每次按键都发送巨大的无障碍事件.
        if (shouldSuppressAccessibilityForLargeText()) {
            // Do NOT call super.onSelectionChanged() here because it may dispatch
            // TYPE_VIEW_TEXT_SELECTION_CHANGED with huge payload and cause Binder TTLE.
            // zh-CN:
            // 此处不要调用 super.onSelectionChanged(),
            // 因其可能派发携带巨大负载的 TYPE_VIEW_TEXT_SELECTION_CHANGED,
            // 从而导致 Binder TTLE.
        } else {
            // 调用父类的 onSelectionChanged 时会发送一个 AccessibilityEvent, 当文本过大时造成异常
            // super.onSelectionChanged(selStart, selEnd);
            // 父类构造函数会调用 onSelectionChanged, 此时 mCursorChangeCallbacks 还没有初始化
            super.onSelectionChanged(selStart, selEnd)
        }

        mCursorChangeCallbacks?.let { it.takeUnless { it.isEmpty() } } ?: return
        if (selStart != selEnd) return
        callCursorChangeCallback(text, selStart)
        matchesBracket(text, selStart)
    }

    override fun sendAccessibilityEventUnchecked(event: AccessibilityEvent) {
        // Guardrail: drop or shrink accessibility payload when text is huge.
        // zh-CN: 护栏: 文本很大时丢弃或瘦身无障碍事件负载.
        if (shouldSuppressAccessibilityForLargeText()) {
            // Clear potentially huge text payload to avoid Binder transaction overflow.
            // zh-CN: 清空可能非常大的 text 负载, 避免 Binder 事务溢出.
            runCatching { event.text.clear() }
            runCatching { event.contentDescription = null }

            // Also drop selection-changed events entirely in large-text mode to avoid TransactionTooLargeException.
            // zh-CN: 大文本模式下直接丢弃 selection-changed 事件以避免 TransactionTooLargeException.
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> return
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> return
                AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> return
                else -> Unit
            }
        }
        super.sendAccessibilityEventUnchecked(event)
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

    // Clear syntax highlight tokens and redraw with plain text.
    // zh-CN: 清空语法高亮 tokens, 并用纯文本方式重绘.
    fun clearHighlightTokens() {
        mHighlightTokens = null
        postInvalidate()
    }

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
        // Notify outer layer early when user touches inside text area (not gutter).
        // zh-CN: 当用户触摸文本区域 (非行号区域) 时尽早通知外层.
        if (event.action == MotionEvent.ACTION_DOWN && event.x >= paddingLeft) {
            onUserTouchInTextArea?.invoke()
        }

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

    // Whether accessibility should be suppressed for current content size.
    // zh-CN: 是否需要针对当前内容大小抑制无障碍事件.
    private fun shouldSuppressAccessibilityForLargeText(): Boolean {
        val len = text?.length ?: 0
        return len >= mA11yLargeTextThresholdChars
    }

    // Apply accessibility importance based on current text length.
    // zh-CN: 根据当前文本长度应用无障碍重要性配置.
    private fun updateAccessibilityImportanceIfNeeded() {
        val target = when (shouldSuppressAccessibilityForLargeText()) {
            true -> IMPORTANT_FOR_ACCESSIBILITY_NO
            else -> IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (importantForAccessibility != target) {
            importantForAccessibility = target
        }
    }

    companion object {
        const val TAG = "CodeEditText"
    }
}
