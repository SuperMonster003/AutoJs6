package org.autojs.autojs.ui.edit.toolbar;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.rhino.debug.DebugCallback;
import org.autojs.autojs.rhino.debug.Debugger;
import org.autojs.autojs.rhino.debug.Dim;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.ui.edit.EditorView;
import org.autojs.autojs.ui.edit.debug.CodeEvaluator;
import org.autojs.autojs.ui.edit.debug.DebugBar;
import org.autojs.autojs.ui.edit.debug.DebuggerSingleton;
import org.autojs.autojs.ui.edit.debug.WatchingVariable;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.FragmentDebugToolbarBinding;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

// FIXME by SuperMonster003 on Feb 24, 2024.
//  ! Converting to Kotlin (*.kt) will cause an IllegalStateException.
//  # java.lang.IllegalStateException: FAILED ASSERTION
//  #     at org.mozilla.javascript.Kit.codeBug(Kit.java:353)
//  #     at org.autojs.autojs.rhino.debug.Dim.interrupted(Dim.java:806)
//  #     ... ...
//  ! zh-CN:
//  ! 转换为 Kotlin (*.kt) 将导致 IllegalStateException 异常.
//  # java.lang.IllegalStateException: 断言失败
//  #     at org.mozilla.javascript.Kit.codeBug(Kit.java:353)
//  #     at org.autojs.autojs.rhino.debug.Dim.interrupted(Dim.java:806)
//  #     ... ...
public class DebugToolbarFragment extends ToolbarFragment implements DebugCallback, CodeEditor.CursorChangeCallback, CodeEvaluator {

    private static final String LOG_TAG = "DebugToolbarFragment";
    private EditorView mEditorView;
    private boolean mCursorChangeFromUser = true;
    private Debugger mDebugger;
    private Handler mHandler;
    private String mCurrentEditorSourceUrl;
    private String mInitialEditorSourceUrl;
    private String mInitialEditorSource;

    private final RecyclerView.AdapterDataObserver mVariableChangeObserver = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            updateWatchingVariables(positionStart, positionStart + itemCount);
        }
    };
    private final CodeEditor.BreakpointChangeListener mBreakpointChangeListener = new CodeEditor.BreakpointChangeListener() {
        @Override
        public void onBreakpointChange(int line, boolean enabled) {
            if (mDebugger != null) {
                mDebugger.breakpoint(line + 1, enabled);
            }
        }

        @Override
        public void onAllBreakpointRemoved(int count) {
            mDebugger.clearAllBreakpoints();
        }
    };
    private FragmentDebugToolbarBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDebugToolbarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEditorView = findEditorView(view);
        mDebugger = DebuggerSingleton.get();
        mDebugger.setWeakDebugCallback(new WeakReference<>(this));
        setInterrupted(false);
        mCurrentEditorSourceUrl = mInitialEditorSourceUrl = mEditorView.uri != null ? mEditorView.uri.toString() : null;
        mInitialEditorSource = mEditorView.editor.getText();
        setupEditor();
        ScriptExecution execution = mEditorView.run(false);
        if (execution != null) {
            mDebugger.attach(execution);
        } else {
            mEditorView.exitDebugging();
        }

        Log.d(LOG_TAG, "onViewCreated");

        // @Caution by SuperMonster003 on May 13, 2023.
        //  ! Do not place these listeners into onCreateView method.
        //  ! zh-CN: 切勿将这些监听器放在 onCreateView 方法中.

        binding.stepOver.setOnClickListener(v -> stepOver());
        binding.stepInto.setOnClickListener(v -> stepInto());
        binding.stepOut.setOnClickListener(v -> stepOut());
        binding.forceStop.setOnClickListener(v -> forceStop());
        binding.resume.setOnClickListener(v -> resume());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupEditor() {
        CodeEditor editor = mEditorView.editor;
        editor.setRedoUndoEnabled(false);
        editor.addCursorChangeCallback(this);
        editor.setBreakpointChangeListener(mBreakpointChangeListener);
        DebugBar debugBar = mEditorView.debugBar;
        debugBar.registerVariableChangeObserver(mVariableChangeObserver);
        debugBar.setCodeEvaluator(this);
    }

    private void setInterrupted(boolean interrupted) {
        setMenuItemStatus(R.id.step_into, interrupted);
        setMenuItemStatus(R.id.step_over, interrupted);
        setMenuItemStatus(R.id.step_out, interrupted);
        setMenuItemStatus(R.id.resume, interrupted);
        if (!interrupted && mEditorView != null) {
            mEditorView.editor.setDebuggingLine(-1);
        }
    }

    public void detachDebugger() {
        if (!mDebugger.isAttached()) {
            return;
        }
        Log.d(LOG_TAG, "detachDebugger");
        mDebugger.detach();
        if (mEditorView == null) {
            return;
        }
        CodeEditor editor = mEditorView.editor;
        if (!TextUtils.equals(mInitialEditorSourceUrl, mCurrentEditorSourceUrl)) {
            editor.setText(mInitialEditorSource);
        }
        editor.setRedoUndoEnabled(true);
        DebugBar debugBar = mEditorView.debugBar;
        debugBar.setTitle(null);
        debugBar.setCodeEvaluator(null);
    }

    void stepOver() {
        setInterrupted(false);
        mDebugger.stepOver();
    }

    void stepInto() {
        setInterrupted(false);
        mDebugger.stepInto();
    }

    void stepOut() {
        setInterrupted(false);
        mDebugger.stepOut();
    }

    void forceStop() {
        setInterrupted(true);
        mEditorView.forceStop();
    }

    void resume() {
        setInterrupted(false);
        mDebugger.resume();
    }

    @Override
    public void updateSourceText(Dim.SourceInfo sourceInfo) {
        Log.d(LOG_TAG, "updateSourceText: url = " + sourceInfo.url());
        sourceInfo.removeAllBreakpoints();
        for (CodeEditor.Breakpoint breakpoint : mEditorView.editor.getBreakpoints().values()) {
            int line = breakpoint.line + 1;
            if (sourceInfo.breakableLine(line)) {
                sourceInfo.breakpoint(line, breakpoint.enabled);
                Log.d(LOG_TAG, "not breakable: " + line);
            }
        }
    }

    @Override
    public void enterInterrupt(Dim.StackFrame stackFrame, String threadName, String message) {
        showDebuggingLineOnEditor(stackFrame, message);
        mHandler.post(this::updateWatchingVariables);
    }

    private void updateWatchingVariables() {
        updateWatchingVariables(0, mEditorView.debugBar.getWatchingVariables().size());
    }

    private void updateWatchingVariables(int start, int end) {
        if (!mDebugger.isAttached()) {
            return;
        }
        DebugBar debugBar = mEditorView.debugBar;
        List<WatchingVariable> variables = debugBar.getWatchingVariables();
        for (int i = start; i < end; i++) {
            WatchingVariable variable = variables.get(i);
            String value = eval(variable.getName());
            variable.setValue(value);
        }
        debugBar.refresh(start, end - start);
    }

    public String eval(String expr) {
        return mDebugger.eval(expr);
    }

    private void showDebuggingLineOnEditor(Dim.StackFrame stackFrame, String message) {
        // 如果调试进入到其他脚本 (例如模块脚本), 则改变当前编辑器的文本为自动调试的脚本的代码
        String source;
        // 标记是否需要更改编辑器文本
        boolean shouldChangeText = !stackFrame.getUrl().equals(mCurrentEditorSourceUrl);
        if (shouldChangeText) {
            source = stackFrame.sourceInfo().source();
        } else {
            source = null;
        }
        mCurrentEditorSourceUrl = stackFrame.getUrl();
        final int line = stackFrame.getLineNumber() - 1;
        mHandler.post(() -> {
            if (mEditorView == null) {
                return;
            }
            if (shouldChangeText) {
                mEditorView.editor.setText(source);
            }
            mCursorChangeFromUser = false;
            mEditorView.editor.setDebuggingLine(line);
            mEditorView.editor.jumpTo(line, 0);
            mEditorView.debugBar.setTitle(PFiles.getName(mCurrentEditorSourceUrl));
            setInterrupted(true);
            if (message != null && !message.contains(ScriptInterruptedException.class.getName())) {
                ViewUtils.showToast(mEditorView.getContext(), message, true);
            }
        });
    }

    @Override
    public void onCursorChange(@NotNull String line, int ch) {
        if (ch == 0 && !mCursorChangeFromUser) {
            mCursorChangeFromUser = true;
            return;
        }
        mCursorChangeFromUser = true;
        if (!mDebugger.isAttached()) {
            return;
        }
        String variable = findVariableOnCursor(line, ch);
        Log.d(LOG_TAG, "onCursorChange: variable = " + variable + ", ch = " + ch + ", line = " + line);
        String value = eval(variable);
        mEditorView.debugBar.updateCurrentVariable(variable, value);
    }

    private String findVariableOnCursor(String line, int ch) {
        int end;
        for (end = ch; end < line.length(); end++) {
            if (isNotIdentifierChar(line.charAt(end))) {
                break;
            }
        }
        int start;
        for (start = Math.min(ch - 1, line.length() - 1); start >= 0; start--) {
            if (isNotIdentifierChar(line.charAt(start))) {
                break;
            }
        }
        start++;
        if (start < end && start < line.length() && start >= 0) {
            return line.substring(start, end);
        }
        return null;
    }

    private boolean isNotIdentifierChar(char c) {
        return !Character.isDigit(c) && !Character.isLetter(c) && c != '.' && c != '_';
    }

    @Override
    public List<Integer> getMenuItemIds() {
        return Arrays.asList(R.id.step_over, R.id.step_into, R.id.step_out, R.id.resume, R.id.force_stop);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mEditorView != null) {
            CodeEditor editor = mEditorView.editor;
            editor.removeCursorChangeCallback(this);
            editor.setBreakpointChangeListener(null);
            DebugBar debugBar = mEditorView.debugBar;
            debugBar.unregisterVariableChangeObserver(mVariableChangeObserver);
        }
    }
}
