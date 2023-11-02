package org.autojs.autojs.ui.edit.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import org.autojs.autojs.pref.Pref.getEditorTextSize
import org.autojs.autojs.pref.Pref.getString
import org.autojs.autojs.pref.Pref.setEditorTextSize
import org.autojs.autojs.script.JsBeautifier
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
 * Modified by SuperMonster003 as of Jul 16, 2023.
 * Transformed by SuperMonster003 on Jul 16, 2023.
 */
class CodeEditor : HVScrollView {

    val binding = CodeEditorBinding.inflate(LayoutInflater.from(context), this, true)

    val codeEditText: CodeEditText = binding.codeEditText.also {
        it.addTextChangedListener(AutoIndent(it))
        lastTextSize = getEditorTextSize(pxToSp(it.textSize).toInt())
    }

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

            // override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            //     return super.onScaleBegin(detector)
            // }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // TODO by SuperMonster003 on Oct 16, 2022.
                //  ! Dismiss a floating text size changing bar in 2 seconds.
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

    private var mMinTextSize = context.getString(R.string.text_text_size_min_value).toInt()
    private var mMaxTextSize = context.getString(R.string.text_text_size_max_value).toInt()
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

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    init {
        applyScaleGesture()
    }

    private fun applyScaleGesture(key: String? = null) {
        var niceKey = key
        if (niceKey == null) {
            val defKey = key(R.string.default_key_editor_pinch_to_zoom_strategy)
            niceKey = getString(key(R.string.key_editor_pinch_to_zoom_strategy), defKey)
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
        return mScaleGestureDetector?.let {
            it.onTouchEvent(ev)
            !it.isInProgress && super.onTouchEvent(ev)
        } ?: super.onTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
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

    fun jumpToStart() {
        codeEditText.setSelection(0)
    }

    fun jumpToEnd() {
        val text = codeEditText.text
        if (text != null) {
            codeEditText.setSelection(text.length)
        }
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
            codeEditText.setSelection(layout.getLineEnd(line) - 1)
        }
    }

    fun setTheme(theme: Theme?) {
        mTheme = theme
        setBackgroundColor(mTheme!!.backgroundColor)
        mJavaScriptHighlighter.setTheme(theme)
        val text = codeEditText.text
        if (text != null) {
            mJavaScriptHighlighter.updateTokens(text.toString())
        }
        codeEditText.setTheme(mTheme!!)
        invalidate()
    }

    fun canUndo(): Boolean {
        return mTextViewRedoUndo.canUndo()
    }

    fun canRedo(): Boolean {
        return mTextViewRedoUndo.canRedo()
    }

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
        codeEditText.isEnabled = !readOnly
    }

    fun setRedoUndoEnabled(enabled: Boolean) {
        mTextViewRedoUndo.isEnabled = enabled
    }

    fun setProgress(progress: Boolean) {
        if (mProcessDialog != null) {
            mProcessDialog!!.dismiss()
        }
        mProcessDialog = if (!progress) null else MaterialDialog.Builder(context)
            .content(R.string.text_processing)
            .progress(true, 0)
            .cancelable(false)
            .show()
    }

    fun addCursorChangeCallback(callback: CursorChangeCallback?) {
        codeEditText.addCursorChangeCallback(callback!!)
    }

    fun removeCursorChangeCallback(callback: CursorChangeCallback?) {
        codeEditText.removeCursorChangeCallback(callback!!)
    }

    fun undo() {
        mTextViewRedoUndo.undo()
    }

    fun redo() {
        mTextViewRedoUndo.redo()
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
                // @Hint by 抠脚本人 on Jul 11, 2023.
                //  ! 格式化后恢复光标位置
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

    inner class CommentHelper : CodeEditorCommentHelper {

        private val prefix = "//"

        override fun handle() = toggle()

        override fun comment() {
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
                // replaceSelectedLines(Regex(".*")) { matchResult ->
                //     prefix
                //         .also { selectionEnd += it.length }
                //         .let { "$it${matchResult.value}" }
                // }
            }

            codeEditText.setSelection(selectionEnd)
        }

        override fun uncomment() {
            var selectionEnd = codeEditText.selectionEnd

            replaceSelectedLines(Regex("(\\s*)($prefix\\s?)(.*)")) { matchResult ->
                val (_, former, feature, latter) = matchResult.groupValues
                selectionEnd -= feature.length
                "$former$latter"
            }

            codeEditText.setSelection(selectionEnd)
        }

        override fun isCommented(): Boolean {
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
    //  ! Reason: Replaced with CommentHelper class.
    // fun commentLine() {
    //     // 如果没有选中，则添加文本/，否则选中的行前加//
    //     val selectionText: String = getSelectionRaw()
    //     if (selectionText == "") {
    //         insert("/")
    //     } else {
    //         val lines = selectionText.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    //         val commentedText = StringBuilder()
    //         // 处理取消注释
    //         if (lines[0].startsWith("//")) {
    //             for (line in lines) {
    //                 commentedText.append(line.substring(2)).append("\n")
    //             }
    //         } else {
    //             for (line in lines) {
    //                 commentedText.append("//").append(line).append("\n")
    //             }
    //         }
    //         mReplacement = commentedText.toString().replace("\\n$".toRegex(), "")
    //         replaceSelection()
    //     }
    // }

    // @Archived by SuperMonster003 on Jul 12, 2023.
    //  ! Author: 抠脚本人
    //  ! Reason: Replaced with CommentHelper class.
    // fun commentBlock() {
    //     val selectionText: String = getSelectionRaw()
    //     if (!selectionText.isEmpty()) {
    //         val regex = "/\\*([^*]|\\*+[^*/])*\\*/"
    //         mReplacement = if (selectionText.matches(regex)) {
    //             // 取消块注释
    //             selectionText.substring(2, selectionText.length - 2)
    //         } else {
    //             // 增加块注释
    //             "/*$selectionText*/"
    //         }
    //         replaceSelection()
    //     }
    // }

}