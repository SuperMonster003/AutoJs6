package org.autojs.autojs.ui.edit.toolbar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import org.autojs.autojs.pio.PFiles.getName
import org.autojs.autojs.rhino.debug.DebugCallback
import org.autojs.autojs.rhino.debug.Debugger
import org.autojs.autojs.rhino.debug.Dim
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.debug.CodeEvaluator
import org.autojs.autojs.ui.edit.debug.DebuggerSingleton
import org.autojs.autojs.ui.edit.editor.CodeEditor.BreakpointChangeListener
import org.autojs.autojs.ui.edit.editor.CodeEditor.CursorChangeCallback
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FragmentDebugToolbarBinding
import java.lang.ref.WeakReference

/**
 * Transformed by SuperMonster003 on Oct 27, 2023.
 */
class DebugToolbarFragment : ToolbarFragment(), DebugCallback, CursorChangeCallback, CodeEvaluator {

    private lateinit var binding: FragmentDebugToolbarBinding

    private lateinit var mEditorView: EditorView
    private lateinit var mDebugger: Debugger
    private lateinit var mHandler: Handler
    private lateinit var mCurrentEditorSourceUrl: String
    private lateinit var mInitialEditorSourceUrl: String
    private lateinit var mInitialEditorSource: String

    private var mCursorChangeFromUser = true

    private val mVariableChangeObserver: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            updateWatchingVariables(positionStart, positionStart + itemCount)
        }
    }

    private val mBreakpointChangeListener: BreakpointChangeListener = object : BreakpointChangeListener {
        override fun onBreakpointChange(line: Int, enabled: Boolean) {
            mDebugger.breakpoint(line + 1, enabled)
        }

        override fun onAllBreakpointRemoved(count: Int) {
            mDebugger.clearAllBreakpoints()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHandler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDebugToolbarBinding.inflate(inflater, container, false)
            .also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mEditorView = findEditorView(view)
        mDebugger = DebuggerSingleton.get()
        mDebugger.setWeakDebugCallback(WeakReference(this))
        setInterrupted(false)
        mInitialEditorSourceUrl = mEditorView.uri.toString()
        mCurrentEditorSourceUrl = mInitialEditorSourceUrl
        mInitialEditorSource = mEditorView.editor.text
        setupEditor()
        val execution = mEditorView.run(false)
        if (execution != null) {
            mDebugger.attach(execution)
        } else {
            mEditorView.exitDebugging()
        }
        Log.d(LOG_TAG, "onViewCreated")

        // @Caution by SuperMonster003 on May 13, 2023.
        //  ! Do not place these listeners into onCreateView method.
        binding.stepOver.setOnClickListener { stepOver() }
        binding.stepInto.setOnClickListener { stepInto() }
        binding.stepOut.setOnClickListener { stepOut() }
        binding.stopScript.setOnClickListener { stopScript() }
        binding.resumeScript.setOnClickListener { resumeScript() }
    }

    private fun setupEditor() {
        mEditorView.editor.apply {
            setRedoUndoEnabled(false)
            addCursorChangeCallback(this@DebugToolbarFragment)
            setBreakpointChangeListener(mBreakpointChangeListener)
        }
        mEditorView.debugBar.apply {
            registerVariableChangeObserver(mVariableChangeObserver)
            setCodeEvaluator(this@DebugToolbarFragment)
        }
    }

    private fun setInterrupted(interrupted: Boolean) {
        setMenuItemStatus(R.id.step_into, interrupted)
        setMenuItemStatus(R.id.step_over, interrupted)
        setMenuItemStatus(R.id.step_out, interrupted)
        setMenuItemStatus(R.id.resume_script, interrupted)
        if (!interrupted) {
            mEditorView.editor.setDebuggingLine(-1)
        }
    }

    fun detachDebugger() {
        if (!mDebugger.isAttached) {
            return
        }
        Log.d(LOG_TAG, "detachDebugger")
        mDebugger.detach()
        mEditorView.editor.also {
            if (!TextUtils.equals(mInitialEditorSourceUrl, mCurrentEditorSourceUrl)) {
                it.text = mInitialEditorSource
            }
            it.setRedoUndoEnabled(true)
        }
        mEditorView.debugBar.apply {
            setTitle(null)
            setCodeEvaluator(null)
        }
    }

    private fun stepOver() = setInterrupted(false).also { mDebugger.stepOver() }

    private fun stepInto() = setInterrupted(false).also { mDebugger.stepInto() }

    private fun stepOut() = setInterrupted(false).also { mDebugger.stepOut() }

    private fun stopScript() = mEditorView.forceStop()

    private fun resumeScript() = setInterrupted(false).also { mDebugger.resume() }

    override fun updateSourceText(sourceInfo: Dim.SourceInfo) {
        Log.d(LOG_TAG, "updateSourceText: url = " + sourceInfo.url())
        sourceInfo.removeAllBreakpoints()
        for (breakpoint in mEditorView.editor.breakpoints.values) {
            val line = breakpoint.line + 1
            if (sourceInfo.breakableLine(line)) {
                sourceInfo.breakpoint(line, breakpoint.enabled)
                Log.d(LOG_TAG, "not breakable: $line")
            }
        }
    }

    override fun enterInterrupt(stackFrame: Dim.StackFrame, threadName: String, message: String) {
        showDebuggingLineOnEditor(stackFrame, message)
        mHandler.post { updateWatchingVariables() }
    }

    private fun updateWatchingVariables(start: Int = 0, end: Int = mEditorView.debugBar.watchingVariables.size) {
        if (!mDebugger.isAttached) {
            return
        }
        val debugBar = mEditorView.debugBar
        val variables = debugBar.watchingVariables
        for (i in start until end) {
            val variable = variables[i]
            variable.value = eval(variable.name)
        }
        debugBar.refresh(start, end - start)
    }

    override fun eval(expr: String): String = mDebugger.eval(expr)

    private fun showDebuggingLineOnEditor(stackFrame: Dim.StackFrame, message: String?) {
        // 标记是否需要更改编辑器文本
        val shouldChangeText = stackFrame.url != mCurrentEditorSourceUrl

        // 如果调试进入到其他脚本 (例如模块脚本), 则改变当前编辑器的文本为自动调试的脚本的代码
        val source: String? = if (shouldChangeText) stackFrame.sourceInfo().source() else null

        mCurrentEditorSourceUrl = stackFrame.url

        val line = stackFrame.lineNumber - 1

        mHandler.post {
            source?.let { mEditorView.editor.text = it }
            mCursorChangeFromUser = false
            mEditorView.editor.setDebuggingLine(line)
            mEditorView.editor.jumpTo(line, 0)
            mEditorView.debugBar.setTitle(getName(mCurrentEditorSourceUrl))
            setInterrupted(true)
            if (message != ScriptInterruptedException::class.java.name) {
                showToast(mEditorView.context, message, true)
            }
        }
    }

    override fun onCursorChange(line: String, cursor: Int) {
        if (cursor == 0 && !mCursorChangeFromUser) {
            mCursorChangeFromUser = true
            return
        }
        mCursorChangeFromUser = true
        if (!mDebugger.isAttached) {
            return
        }
        val variable = findVariableOnCursor(line, cursor)
        Log.d(LOG_TAG, "onCursorChange: variable = $variable, ch = $cursor, line = $line")
        variable?.let {
            eval(it)
            mEditorView.debugBar.updateCurrentVariable(variable, it)
        }
    }

    private fun findVariableOnCursor(line: String, ch: Int): String? {
        var end = ch
        while (end < line.length) {
            if (!isIdentifierChar(line[end])) {
                break
            }
            end++
        }
        var start = (ch - 1).coerceAtMost(line.length - 1)
        while (start >= 0) {
            if (!isIdentifierChar(line[start])) {
                break
            }
            start--
        }
        start++
        return if (start < end && start < line.length && start >= 0) {
            line.substring(start, end)
        } else null
    }

    private fun isIdentifierChar(c: Char): Boolean {
        return Character.isDigit(c) || Character.isLetter(c) || c == '.' || c == '_'
    }

    override fun getMenuItemIds(): List<Int> {
        return listOf(R.id.step_over, R.id.step_into, R.id.step_out, R.id.resume_script, R.id.stop_script)
    }

    override fun onDestroy() {
        super.onDestroy()
        mEditorView.editor.apply {
            removeCursorChangeCallback(this@DebugToolbarFragment)
            setBreakpointChangeListener(null)
        }
        mEditorView.debugBar.apply {
            unregisterVariableChangeObserver(mVariableChangeObserver)
        }
    }

    companion object {
        private const val LOG_TAG = "DebugToolbarFragment"
    }

}
