package org.autojs.autojs.ui.edit.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.WindowManager
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import org.autojs.autojs.core.pref.Pref.getEditorTextSize
import org.autojs.autojs.core.pref.Pref.getString
import org.autojs.autojs.core.pref.Pref.setEditorTextSize
import org.autojs.autojs.script.JsBeautifier
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.ui.edit.theme.Theme
import org.autojs.autojs.util.ClipboardUtils.setClip
import org.autojs.autojs.util.DisplayUtils.pxToSp
import org.autojs.autojs.util.StringUtils.indexOf
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.CodeEditorBinding
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.floor

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
/**
 * Transformed by SuperMonster003 on Jul 16, 2023.
 * Modified by SuperMonster003 as of Jul 16, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 8, 2026.
 */
class CodeEditor : HVScrollView {

    val binding = CodeEditorBinding.inflate(LayoutInflater.from(context), this, true)

    val codeEditText: CodeEditText = binding.codeEditText.also {
        it.addTextChangedListener(AutoIndent(it))
        lastTextSize = getEditorTextSize(pxToSp(it.textSize).toInt())
        ThemeColorHelper.setThemeColorPrimary(it, true)
    }

    // Whether the user is interacting with the editor via touch.
    // zh-CN: 用户是否正在通过触摸与编辑器交互.
    @Volatile
    private var mUserTouching = false

    // Public getter for streaming loader to prioritize scroll responsiveness.
    // zh-CN: 给流式加载器使用的公开读取口, 用于优先保障滚动响应性.
    fun isUserTouching(): Boolean = mUserTouching

    val lineCount
        get() = Observable.just(codeEditText.layout.lineCount)

    val isTextChanged
        get() = mTextViewRedoUndo.isTextChanged

    val selection: Observable<String>
        get() = Observable.just(selectionText)

    val breakpoints: LinkedHashMap<Int, Breakpoint>
        get() = codeEditText.breakpoints

    var lastTextSize = 0

    var text: String
        get() = codeEditText.text?.toString() ?: ""
        set(text) {
            codeEditText.setText(text)
        }

    @JvmField
    val commentHelper = CommentHelper()

    private val simpleOnScaleGestureListener
        get() = object : SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentFactor = floor((detector.scaleFactor * 10).toDouble()) / 10
                if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                    val currentTextSize = lastTextSize + if (currentFactor > mLastScaleFactor) 1 else -1
                    lastTextSize = currentTextSize.coerceIn(mMinTextSize, mMaxTextSize).also {
                        codeEditText.textSize = it.toFloat()
                    }
                    mLastScaleFactor = currentFactor
                }
                return super.onScale(detector)
            }

            // TODO by SuperMonster003 on Oct 16, 2022.
            //  ! Show a floating text size changing bar.
            //  ! zh-CN: 显示一个浮动的字体大小调整栏.

            // override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            //     return super.onScaleBegin(detector)
            // }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // TODO by SuperMonster003 on Oct 16, 2022.
                //  ! Dismiss a floating text size changing bar in 2 seconds.
                //  ! zh-CN: 字体大小调整栏于 2 秒钟后消失.
                mLastScaleFactor = 1.0
                setEditorTextSize(lastTextSize)
                super.onScaleEnd(detector)
            }
        }

    private val selectionText: String
        get() {
            val sRaw = codeEditText.selectionStart
            val eRaw = codeEditText.selectionEnd
            val s = minOf(sRaw, eRaw)
            val e = maxOf(sRaw, eRaw)
            return if (s == e) "" else codeEditText.text?.substring(s, e) ?: ""
        }

    private var mMinTextSize = context.resources.getInteger(R.integer.editor_text_size_min_value)
    private var mMaxTextSize = context.resources.getInteger(R.integer.editor_text_size_max_value)
    private var mTextViewRedoUndo = TextViewUndoRedo(codeEditText)

    private var mTheme: Theme? = null
    private var mJavaScriptHighlighter = JavaScriptHighlighter(mTheme, codeEditText)

    private var mJsBeautifier = JsBeautifier(this)
    private var mScaleGestureDetectorForChangeTextSize = ScaleGestureDetector(context, simpleOnScaleGestureListener)
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mProcessDialog: MaterialDialog? = null
    private var mReplacement: CharSequence = ""
    private var mKeywords: String? = null
    private var mMatcher: Matcher? = null
    private var mFoundIndex = -1
    private var mLastScaleFactor = 1.0

    private val mUiHandler = Handler(Looper.getMainLooper())

    // Delay showing the loading dialog to avoid flicker for fast operations.
    // zh-CN: 延迟显示加载对话框, 避免快速操作产生闪烁.
    private val mProgressShowDelayMs = 1500L

    // Once shown, keep the dialog visible for at least this duration to avoid flash.
    // zh-CN: 对话框一旦出现, 至少显示一段时间, 避免一闪而过.
    private val mProgressMinShowMs = 500L

    private var mProgressRequested = false
    private var mProgressShownAtMs = 0L
    private var mProgressInteractive = false

    private val mShowProgressRunnable = Runnable {
        if (!mProgressRequested) return@Runnable
        if (mProcessDialog?.isShowing == true) return@Runnable

        mProcessDialog = MaterialDialog.Builder(context)
            .content(R.string.text_in_progress)
            // Text only, no progress spinner.
            // zh-CN: 仅显示文字, 不使用进度圆圈动画.
            .cancelable(false)
            .canceledOnTouchOutside(false)
            .show()

        mProgressShownAtMs = SystemClock.uptimeMillis()

        // Make it non-modal (optional) so user can scroll/view during loading.
        // zh-CN: 可选地设置为非模态, 使用户在加载时仍可滚动/查看.
        if (mProgressInteractive) {
            mProcessDialog?.window?.let { w ->
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.setDimAmount(0f)
                w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                w.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            }
        }
    }

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    init {
        applyScaleGesture()
    }

    fun refreshHighlightTokensIfAllowed() {
        // Force a highlight refresh after bulk loading, when loading flags are cleared.
        // zh-CN: 在批量加载结束且 loading 标记已清除后, 主动触发一次高亮刷新.
        val t = codeEditText.text?.toString() ?: return
        mJavaScriptHighlighter.updateTokens(t)
    }

    /**
     * Show or hide a "processing" indicator.
     *
     * Behavior:
     * - Delayed show to avoid flicker.
     * - Minimum show time once visible.
     * - Optional interactive mode: don't block touches and don't dim background.
     *
     * zh-CN:
     * 显示或隐藏 "处理中" 提示.
     *
     * 行为:
     * - 延迟显示以避免闪烁.
     * - 一旦出现则保证最短展示时间.
     * - 可选交互模式: 不拦截触摸/不压暗背景.
     */
    /**
     * Show or hide a "processing" indicator.
     *
     * Behavior:
     * - Delayed show to avoid flicker.
     * - Minimum show time once visible.
     * - Optional interactive mode: don't block touches and don't dim background.
     *
     * zh-CN:
     * 显示或隐藏 "处理中" 提示.
     *
     * 行为:
     * - 延迟显示以避免闪烁.
     * - 一旦出现则保证最短展示时间.
     * - 可选交互模式: 不拦截触摸/不压暗背景.
     */
    fun setProgress(progress: Boolean, interactive: Boolean = false) {
        mProgressInteractive = interactive

        if (progress) {
            mProgressRequested = true

            // If already showing, keep it.
            // zh-CN: 若已显示则保持不变.
            if (mProcessDialog?.isShowing == true) return

            // Schedule delayed show.
            // zh-CN: 延迟调度显示.
            mUiHandler.removeCallbacks(mShowProgressRunnable)
            mUiHandler.postDelayed(mShowProgressRunnable, mProgressShowDelayMs)
            return
        }

        // Hide requested.
        // zh-CN: 请求隐藏.
        mProgressRequested = false
        mUiHandler.removeCallbacks(mShowProgressRunnable)

        val dlg = mProcessDialog
        if (dlg == null || dlg.isShowing != true) {
            mProcessDialog = null
            return
        }

        val elapsed = SystemClock.uptimeMillis() - mProgressShownAtMs
        val remain = (mProgressMinShowMs - elapsed).coerceAtLeast(0L)

        if (remain == 0L) {
            dlg.dismiss()
            mProcessDialog = null
            return
        }

        mUiHandler.postDelayed({
            // Only dismiss if no new show request came in.
            // zh-CN: 仅当没有新的显示请求时才 dismiss.
            if (!mProgressRequested) {
                runCatching { dlg.dismiss() }
                mProcessDialog = null
            }
        }, remain)
    }

    private fun applyScaleGesture(key: String? = null) {
        var niceKey = key
        if (niceKey == null) {
            niceKey = getString(R.string.key_editor_pinch_to_zoom_strategy, R.string.default_key_editor_pinch_to_zoom_strategy)
        }
        when (niceKey) {
            key(R.string.key_editor_pinch_to_zoom_change_text_size) -> {
                mScaleGestureDetector = mScaleGestureDetectorForChangeTextSize
            }
            key(R.string.key_editor_pinch_to_zoom_scale_view) -> {
                // TODO by SuperMonster003 on Oct 17, 2022.
            }
            key(R.string.key_editor_pinch_to_zoom_disable) -> {
                mScaleGestureDetector = null
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE,
                -> {
                // Mark touching as early as possible.
                // zh-CN: 尽可能早地标记触摸中状态.
                mUserTouching = true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_UP,
                -> {
                // Clear touching flag when gesture ends.
                // zh-CN: 手势结束时清除触摸中标记.
                mUserTouching = false
            }
        }

        return mScaleGestureDetector?.let {
            it.onTouchEvent(ev)
            !it.isInProgress && super.onTouchEvent(ev)
        } ?: super.onTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        // Avoid scheduling extra invalidations during bulk loading.
        // zh-CN: 批量加载期间避免额外调度 invalidate, 降低重绘压力.
        if (codeEditText.isLoadingText()) {
            codeEditText.invalidate()
            return
        }

        codeEditText.postInvalidate()
    }

    fun copyLine() {
        val layout = codeEditText.layout
        val line = LayoutHelper.getLineOfChar(layout, minOf(codeEditText.selectionStart, codeEditText.selectionEnd))
        if (line >= 0 && line < layout.lineCount) {
            val text = codeEditText.text
            var lineText: CharSequence? = null
            if (text != null) {
                lineText = text.subSequence(layout.getLineStart(line), layout.getLineEnd(line))
            }
            setClip(context, lineText)
            showSnack(this, R.string.text_already_copied_to_clip, false)
        }
    }

    private fun getCoveredLinesText(): CharSequence {
        val layout = codeEditText.layout
        val lineStartRaw = LayoutHelper.getLineOfChar(layout, codeEditText.selectionStart)
        val lineEndRaw = LayoutHelper.getLineOfChar(layout, codeEditText.selectionEnd)
        val lineStart = minOf(lineStartRaw, lineEndRaw)
        val lineEnd = maxOf(lineStartRaw, lineEndRaw)
        if (lineStart >= 0 && lineStart < layout.lineCount) {
            if (lineEnd >= 0 && lineEnd < layout.lineCount) {
                return text.subSequence(layout.getLineStart(lineStart), layout.getLineEnd(lineEnd))
            }
        }
        return ""
    }

    private fun replaceSelectedLines(feature: Regex, transform: (MatchResult) -> CharSequence) {
        val text = codeEditText.text ?: return
        val layout = codeEditText.layout
        val lineStartRaw = LayoutHelper.getLineOfChar(layout, codeEditText.selectionStart)
        val lineEndRaw = LayoutHelper.getLineOfChar(layout, codeEditText.selectionEnd)
        val lineStart = minOf(lineStartRaw, lineEndRaw)
        val lineEnd = maxOf(lineStartRaw, lineEndRaw)
        val newText = getCoveredLinesText().split("\n").joinToString("\n") { it.replace(feature, transform) }
        text.replace(layout.getLineStart(lineStart), layout.getLineEnd(lineEnd), newText)
    }

    fun deleteLine() {
        val text = codeEditText.text ?: return
        val layout = codeEditText.layout
        val line = LayoutHelper.getLineOfChar(layout, minOf(codeEditText.selectionStart, codeEditText.selectionEnd))
        if (line >= 0 && line < layout.lineCount) {
            text.replace(layout.getLineStart(line), layout.getLineEnd(line), "")
        }
    }

    fun clear() {
        codeEditText.setText("")
    }

    fun jumpToStart() {
        codeEditText.setSelection(0)
    }

    fun jumpToEnd() {
        codeEditText.text?.let { codeEditText.setSelection(it.length) }
    }

    fun jumpToLineStart() {
        val layout = codeEditText.layout
        val line = LayoutHelper.getLineOfChar(layout, minOf(codeEditText.selectionStart, codeEditText.selectionEnd))
        if (line >= 0 && line < layout.lineCount) {
            codeEditText.setSelection(layout.getLineStart(line))
        }
    }

    fun jumpToLineEnd() {
        val layout = codeEditText.layout
        val line = LayoutHelper.getLineOfChar(layout, minOf(codeEditText.selectionStart, codeEditText.selectionEnd))
        if (line >= 0 && line < layout.lineCount) {
            codeEditText.setSelection(getLineEndWithoutTrailingNewline(layout, line))
        }
    }

    fun jumpToNextLine() {
        val layout = codeEditText.layout
        val cursor = minOf(codeEditText.selectionStart, codeEditText.selectionEnd)
        val line = LayoutHelper.getLineOfChar(layout, cursor)
        jumpToLinePreservingColumn(layout, line, line + 1, cursor)
    }

    fun jumpToPrevLine() {
        val layout = codeEditText.layout
        val cursor = minOf(codeEditText.selectionStart, codeEditText.selectionEnd)
        val line = LayoutHelper.getLineOfChar(layout, cursor)
        jumpToLinePreservingColumn(layout, line, line - 1, cursor)
    }

    private fun jumpToLinePreservingColumn(layout: Layout, fromLine: Int, targetLine: Int, cursor: Int) {
        if (fromLine !in 0 until layout.lineCount || targetLine !in 0 until layout.lineCount) {
            return
        }
        val fromLineStart = layout.getLineStart(fromLine)
        val fromLineEnd = getLineEndWithoutTrailingNewline(layout, fromLine)
        val fromLineCursor = cursor.coerceIn(fromLineStart, fromLineEnd)
        val desiredColumn = fromLineCursor - fromLineStart

        val targetLineStart = layout.getLineStart(targetLine)
        val targetLineEnd = getLineEndWithoutTrailingNewline(layout, targetLine)
        val targetCursor = (targetLineStart + desiredColumn).coerceAtMost(targetLineEnd)
        codeEditText.setSelection(targetCursor)
    }

    private fun getLineEndWithoutTrailingNewline(layout: Layout, line: Int): Int {
        val lineEnd = layout.getLineEnd(line)
        val text = codeEditText.text ?: return lineEnd
        return if (lineEnd > 0 && lineEnd <= text.length && text[lineEnd - 1] == '\n') {
            lineEnd - 1
        } else {
            lineEnd
        }
    }

    fun setTheme(theme: Theme) {
        mTheme = theme
        setBackgroundColor(theme.backgroundColor)
        mJavaScriptHighlighter.setTheme(theme)
        val text = codeEditText.text
        if (text != null) {
            mJavaScriptHighlighter.updateTokens(text.toString())
        }
        codeEditText.setTheme(theme)
        invalidate()
    }

    fun canUndo() = mTextViewRedoUndo.canUndo()

    fun canRedo() = mTextViewRedoUndo.canRedo()

    fun setInitialText(text: String?) {
        codeEditText.setText(text)
        mTextViewRedoUndo.setDefaultText(text)
    }

    fun jumpTo(line: Int, col: Int) {
        val layout = codeEditText.layout
        if (line >= 0 && (layout == null || line < layout.lineCount)) {
            codeEditText.setSelection(codeEditText.layout.getLineStart(line) + col)
        }
    }

    fun setReadOnly(readOnly: Boolean) {
        codeEditText.setReadOnly(readOnly)
    }

    fun setRedoUndoEnabled(enabled: Boolean) {
        mTextViewRedoUndo.isEnabled = enabled
    }

    fun markUndoRedoBaselineAsUnchanged() {
        // Reset undo/redo history but keep current text intact.
        // zh-CN: 重置 undo/redo 历史但保持当前文本不变.
        mTextViewRedoUndo.resetHistoryAsUnchanged()
    }

    fun addCursorChangeCallback(callback: CursorChangeCallback?) {
        codeEditText.addCursorChangeCallback(callback!!)
    }

    fun removeCursorChangeCallback(callback: CursorChangeCallback?) {
        codeEditText.removeCursorChangeCallback(callback!!)
    }

    fun undo() = mTextViewRedoUndo.undo()

    fun redo() = mTextViewRedoUndo.redo()

    // Expose current found index for UI logic (e.g., no-result prompt).
    // zh-CN: 暴露当前命中位置, 供 UI 逻辑使用 (例如无结果提示).
    fun getFoundIndex(): Int = mFoundIndex

    /**
     * Reset search cursor for "Find Next" based on current caret/selection.
     *
     * Behavior:
     * - Uses current selection start (min of start/end).
     * - Sets mFoundIndex to (cursor - 1), so findNext() starts at cursor.
     *
     * zh-CN:
     * 将 "查找下一个" 的起点对齐到当前光标/选择起点.
     * - 使用 selectionStart/End 的较小值作为光标位置
     * - mFoundIndex 设为 cursor-1, 使 findNext() 从 cursor 开始找
     */
    fun resetFoundIndexForFindNextFromCursor() {
        val textLen = codeEditText.text?.length ?: 0

        // Use selection END as the starting anchor for "next",
        // so when current match is selected we won't re-select it again.
        //
        // zh-CN: "下一个" 以 selectionEnd 作为起点锚点,
        // 这样当当前命中被选中时不会再次选中同一条.
        val cursor = maxOf(codeEditText.selectionStart, codeEditText.selectionEnd).coerceIn(0, textLen)

        // Make findNext() start from `cursor`.
        // zh-CN: 让 findNext() 从 cursor 开始找.
        mFoundIndex = (cursor - 1).coerceAtLeast(-1)
    }

    /**
     * Reset search cursor for "Find Prev" based on current caret/selection.
     *
     * Behavior:
     * - Uses current selection start (min of start/end).
     * - Sets mFoundIndex to cursor, so findPrev() searches before cursor.
     *
     * zh-CN:
     * 将 "查找上一个" 的起点对齐到当前光标/选择起点.
     * - 使用 selectionStart/End 的较小值作为光标位置
     * - mFoundIndex 设为 cursor, 使 findPrev() 从 cursor 往前找
     */
    fun resetFoundIndexForFindPrevFromCursor() {
        val textLen = codeEditText.text?.length ?: 0
        val cursor = minOf(codeEditText.selectionStart, codeEditText.selectionEnd).coerceIn(0, textLen)
        mFoundIndex = cursor
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun find(keywords: String, usingRegex: Boolean) {
        if (usingRegex) {
            try {
                val text = codeEditText.text
                if (text != null) {
                    mMatcher = Pattern.compile(keywords).matcher(text)
                }
            } catch (e: PatternSyntaxException) {
                throw CheckedPatternSyntaxException(e)
            }
            mKeywords = null
        } else {
            mKeywords = keywords
            mMatcher = null
        }
        findNext()
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replace(keywords: String, replacement: String, usingRegex: Boolean) {
        mReplacement = replacement
        find(keywords, usingRegex)
    }

    @Throws(CheckedPatternSyntaxException::class)
    fun replaceAll(keywords: String, replacement: String?, usingRegex: Boolean) {
        var niceKeywords = keywords
        if (!usingRegex) {
            niceKeywords = Pattern.quote(niceKeywords)
        }
        val codeEditTextText = codeEditText.text
        var text: String? = null
        if (codeEditTextText != null) {
            text = codeEditTextText.toString()
        }
        try {
            if (text != null) {
                text = text.replace(niceKeywords.toRegex(), replacement!!)
            }
        } catch (e: PatternSyntaxException) {
            throw CheckedPatternSyntaxException(e)
        }
        if (text != null) {
            this.text = text
        }
    }

    fun findNext() {
        val foundIndex: Int
        if (mMatcher == null) {
            if (mKeywords == null) {
                return
            }
            val text = codeEditText.text
            foundIndex = if (text != null) {
                indexOf(text, mKeywords!!, mFoundIndex + 1)
            } else {
                -1
            }
            if (foundIndex >= 0) codeEditText.setSelection(foundIndex, foundIndex + mKeywords!!.length)
        } else if (mMatcher!!.find(mFoundIndex + 1)) {
            foundIndex = mMatcher!!.start()
            codeEditText.setSelection(foundIndex, foundIndex + mMatcher!!.group().length)
        } else {
            foundIndex = -1
        }
        if (foundIndex < 0 && mFoundIndex >= 0) {
            mFoundIndex = -1
            findNext()
        } else {
            mFoundIndex = foundIndex
        }
    }

    fun findPrev() {
        if (mMatcher != null) {
            showToast(context, R.string.error_regex_find_prev, true)
            return
        }
        val len = codeEditText.text!!.length
        if (mFoundIndex <= 0) {
            mFoundIndex = len
        }
        val index = codeEditText.text.toString().lastIndexOf(mKeywords!!, mFoundIndex - 1)
        if (index < 0) {
            if (mFoundIndex != len) {
                mFoundIndex = len
                findPrev()
            }
            return
        }
        mFoundIndex = index
        codeEditText.setSelection(index, index + mKeywords!!.length)
    }

    fun replaceSelection() {
        val selectionStartRaw = codeEditText.selectionStart
        val selectionEndRaw = codeEditText.selectionEnd
        val selectionStart = minOf(selectionStartRaw, selectionEndRaw)
        val selectionEnd = maxOf(selectionStartRaw, selectionEndRaw)
        codeEditText.text?.replace(selectionStart, selectionEnd, mReplacement)
    }

    fun beautifyCode() {
        setProgress(true)
        val pos = minOf(codeEditText.selectionStart, codeEditText.selectionEnd)
        mJsBeautifier.beautify(codeEditText.text.toString(), object : JsBeautifier.Callback {
            override fun onSuccess(beautifiedCode: String) {
                codeEditText.setText(beautifiedCode)
                // @Hint by 抠脚本人 (https://github.com/little-alei) on Jul 11, 2023.
                //  ! 格式化后恢复光标位置.
                //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
                //  ! Return the cursor back to where it was before formatting.
                codeEditText.setSelection(pos)
                setProgress(false)
                showToast(context, R.string.text_formatting_completed)
            }

            override fun onException(e: Exception) {
                setProgress(false)
                showToast(context, R.string.text_failed_to_format, true)
                e.printStackTrace()
            }
        })
    }

    fun insert(insertText: String?) {
        val selection = minOf(codeEditText.selectionStart, codeEditText.selectionEnd).coerceAtLeast(0)
        codeEditText.text!!.insert(selection, insertText)
    }

    fun insert(line: Int, insertText: String?) {
        val selection = codeEditText.layout.getLineStart(line)
        codeEditText.text!!.insert(selection, insertText)
    }

    fun moveCursor(dCh: Int) {
        codeEditText.setSelection(minOf(codeEditText.selectionStart, codeEditText.selectionEnd) + dCh)
    }

    fun markTextAsSaved() {
        mTextViewRedoUndo.markTextAsUnchanged()
    }

    fun setDebuggingLine(line: Int) {
        codeEditText.debuggingLine = line
    }

    fun setBreakpointChangeListener(listener: BreakpointChangeListener?) {
        codeEditText.breakpointChangeListener = listener
    }

    private fun addOrRemoveBreakpoint(line: Int) {
        if (!codeEditText.removeBreakpoint(line)) {
            codeEditText.addBreakpoint(line)
        }
    }

    fun addOrRemoveBreakpointAtCurrentLine() {
        val layout = codeEditText.layout
        val line = LayoutHelper.getLineOfChar(layout, minOf(codeEditText.selectionStart, codeEditText.selectionEnd))
        if (line >= 0 && line < layout.lineCount) {
            addOrRemoveBreakpoint(line)
        }
    }

    fun removeAllBreakpoints() {
        codeEditText.removeAllBreakpoints()
    }

    fun destroy() {
        mJavaScriptHighlighter.shutdown()
        mJsBeautifier.shutdown()
    }

    override fun onDraw(canvas: Canvas) {
        val codeWidth = width - paddingLeft - paddingRight
        val codeHeight = height - paddingTop - paddingBottom
        if (codeEditText.minWidth != codeWidth || codeEditText.minWidth != codeWidth) {
            codeEditText.minWidth = codeWidth
            codeEditText.minHeight = codeHeight
            invalidate()
        }
        super.onDraw(canvas)
    }

    fun notifyPinchToZoomStrategyChanged(newKey: String?) {
        applyScaleGesture(newKey)
    }

    class Breakpoint(@JvmField var line: Int) {
        @JvmField
        var enabled = true
    }

    class CheckedPatternSyntaxException(cause: PatternSyntaxException?) : Exception(cause)

    interface BreakpointChangeListener {
        fun onBreakpointChange(line: Int, enabled: Boolean)
        fun onAllBreakpointRemoved(count: Int)
    }

    interface CursorChangeCallback {
        fun onCursorChange(line: String, cursor: Int)
    }

    inner class CommentHelper {

        private val prefix = "//"

        fun toggle() {
            if (isCommented()) {
                uncomment()
            } else {
                comment()
            }
        }

        fun comment() {
            var selectionEnd = codeEditText.selectionEnd
            val insetPosition = getProperWhitespaceAmount()
            var hasEverMatched = false

            @Suppress("RegExpSimplifiable")
            replaceSelectedLines(Regex("^(\\s{$insetPosition})(\\s*\\S+)")) { matchResult ->
                "$prefix\u0020".let {
                    selectionEnd += it.length
                    hasEverMatched = true
                    val (_, former, latter) = matchResult.groupValues
                    "$former$it$latter"
                }
            }

            @Suppress("ControlFlowWithEmptyBody")
            if (!hasEverMatched) {
                // FIXME by SuperMonster003 on Jul 20, 2023.
                //  ! Behaves abnormally for empty line(s).
                //  ! zh-CN: 对于空白行表现异常.
                //  !
                //  # replaceSelectedLines(Regex(".*")) { matchResult ->
                //  #     prefix
                //  #         .also { selectionEnd += it.length }
                //  #         .let { "$it${matchResult.value}" }
                //  # }
            }

            codeEditText.setSelection(selectionEnd)
        }

        fun uncomment() {
            var selectionEnd = codeEditText.selectionEnd

            replaceSelectedLines(Regex("(\\s*)($prefix\\s?)(.*)")) { matchResult ->
                val (_, former, feature, latter) = matchResult.groupValues
                selectionEnd -= feature.length
                "$former$latter"
            }

            codeEditText.setSelection(selectionEnd)
        }

        fun isCommented(): Boolean {
            var atLeastOneWithPrefix = false
            val allMatched = getCoveredLinesText().split("\n").all {
                it.matches(Regex("\\s*")) || it
                    .contains(Regex("^\\s*$prefix"))
                    .also { atLeastOneWithPrefix = true }
            }
            return allMatched and atLeastOneWithPrefix
        }

        private fun getProperWhitespaceAmount(): Int {
            var result = Int.MAX_VALUE
            val threshold = 0
            getCoveredLinesText().split("\n").forEach {
                Regex("^\\s*(?=\\S+)").find(it)?.let { matchResult ->
                    val len = matchResult.value.length
                    if (len < result) result = len
                    if (result == threshold) return threshold
                }
            }
            return if (result == Int.MAX_VALUE) threshold else result
        }

    }

    // @Archived by SuperMonster003 on Jul 12, 2023.
    //  ! Author: 抠脚本人
    //  ! Reason: Replaced with CommentHelper class [zh-CN: 已由 CommentHelper 类替代].
    //  !
    //  # fun commentLine() {
    //  #     // 如果没有选中, 则添加文本 "/", 否则选中的行前加 "//".
    //  #     val selectionText: String = getSelectionRaw()
    //  #     if (selectionText == "") {
    //  #         insert("/")
    //  #     } else {
    //  #         val lines = selectionText.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    //  #         val commentedText = StringBuilder()
    //  #         // 处理取消注释
    //  #         if (lines[0].startsWith("//")) {
    //  #             for (line in lines) {
    //  #                 commentedText.append(line.substring(2)).append("\n")
    //  #             }
    //  #         } else {
    //  #             for (line in lines) {
    //  #                 commentedText.append("//").append(line).append("\n")
    //  #             }
    //  #         }
    //  #         mReplacement = commentedText.toString().replace("\\n$".toRegex(), "")
    //  #         replaceSelection()
    //  #     }
    //  # }

    // @Archived by SuperMonster003 on Jul 12, 2023.
    //  ! Author: 抠脚本人
    //  ! Reason: Replaced with CommentHelper class [zh-CN: 已由 CommentHelper 类替代].
    //  !
    //  # fun commentBlock() {
    //  #     val selectionText: String = getSelectionRaw()
    //  #     if (!selectionText.isEmpty()) {
    //  #         val regex = "/\\*([^*]|\\*+[^*/])*\\*/"
    //  #         mReplacement = if (selectionText.matches(regex)) {
    //  #             // 取消块注释
    //  #             selectionText.substring(2, selectionText.length - 2)
    //  #         } else {
    //  #             // 增加块注释
    //  #             "/*$selectionText*/"
    //  #         }
    //  #         replaceSelection()
    //  #     }
    //  # }

}
