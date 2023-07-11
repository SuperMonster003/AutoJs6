package org.autojs.autojs.ui.edit.editor;

import static org.autojs.autojs.util.StringUtils.key;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.script.JsBeautifier;
import org.autojs.autojs.ui.edit.theme.Theme;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.DisplayUtils;
import org.autojs.autojs.util.StringUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.reactivex.Observable;

/**
 * Copyright 2018 WHO<980008027@qq.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modified by project: https://github.com/980008027/JsDroidEditor
 */
public class CodeEditor extends HVScrollView {

    private CodeEditText mCodeEditText;
    private TextViewUndoRedo mTextViewRedoUndo;
    private JavaScriptHighlighter mJavaScriptHighlighter;
    private ScaleGestureDetector mScaleGestureDetector;
    private ScaleGestureDetector mScaleGestureDetectorForChangeTextSize;
    private Theme mTheme;
    private JsBeautifier mJsBeautifier;
    private MaterialDialog mProcessDialog;

    private CharSequence mReplacement = "";
    private String mKeywords;
    private Matcher mMatcher;
    private int mFoundIndex = -1;

    private double mLastScaleFactor = 1;
    private int mLastTextSize = 0;

    private int mMinTextSize = 0;
    private int mMaxTextSize = 0;

    public CodeEditor(Context context) {
        super(context);
        init();
    }

    public CodeEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setLastTextSize(int size) {
        mLastTextSize = size;
    }

    private void init() {
        // setFillViewport(true);
        inflate(getContext(), R.layout.code_editor, this);
        mCodeEditText = findViewById(R.id.code_edit_text);
        mCodeEditText.addTextChangedListener(new AutoIndent(mCodeEditText));
        mLastTextSize = Pref.getEditorTextSize((int) DisplayUtils.pxToSp(getContext(), mCodeEditText.getTextSize()));
        mMinTextSize = Integer.parseInt(getContext().getString(R.string.text_text_size_min_value));
        mMaxTextSize = Integer.parseInt(getContext().getString(R.string.text_text_size_max_value));
        mTextViewRedoUndo = new TextViewUndoRedo(mCodeEditText);
        mJavaScriptHighlighter = new JavaScriptHighlighter(mTheme, mCodeEditText);
        mJsBeautifier = new JsBeautifier(this, "js-beautify");
        mScaleGestureDetectorForChangeTextSize = new ScaleGestureDetector(getContext(), getSimpleOnScaleGestureListener());
        applyScaleGesture();
    }

    private void applyScaleGesture() {
        applyScaleGesture(null);
    }

    private void applyScaleGesture(String key) {
        if (key == null) {
            String defKey = key(R.string.default_key_editor_pinch_to_zoom_strategy);
            key = Pref.getString(key(R.string.key_editor_pinch_to_zoom_strategy), defKey);
        }
        if (Objects.equals(key, key(R.string.key_editor_pinch_to_zoom_change_text_size))) {
            mScaleGestureDetector = mScaleGestureDetectorForChangeTextSize;
        } else if (Objects.equals(key, key(R.string.key_editor_pinch_to_zoom_scale_view))) {

            // TODO by SuperMonster003 on Oct 17, 2022.

        } else if (Objects.equals(key, key(R.string.key_editor_pinch_to_zoom_disable))) {
            mScaleGestureDetector = null;
        }
    }

    @NonNull
    private ScaleGestureDetector.SimpleOnScaleGestureListener getSimpleOnScaleGestureListener() {
        return new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                double currentFactor = Math.floor(detector.getScaleFactor() * 10) / 10;
                if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                    int currentTextSize = mLastTextSize + (currentFactor > mLastScaleFactor ? 1 : -1);
                    mLastTextSize = Math.max(mMinTextSize, Math.min(mMaxTextSize, currentTextSize));
                    mCodeEditText.setTextSize(mLastTextSize);

                    mLastScaleFactor = currentFactor;
                }
                return super.onScale(detector);
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                // TODO by SuperMonster003 on Oct 16, 2022.
                //  ! Show a floating text size changing bar.

                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                // TODO by SuperMonster003 on Oct 16, 2022.
                //  ! Dismiss a floating text size changing bar in 2 seconds.

                mLastScaleFactor = 1.0;
                Pref.setEditorTextSize(mLastTextSize);
                super.onScaleEnd(detector);
            }
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mScaleGestureDetector == null) {
            return super.onTouchEvent(ev);
        }
        mScaleGestureDetector.onTouchEvent(ev);
        return !mScaleGestureDetector.isInProgress() && super.onTouchEvent(ev);
    }

    public Observable<Integer> getLineCount() {
        return Observable.just(mCodeEditText.getLayout().getLineCount());
    }

    public void copyLine() {
        Layout layout = mCodeEditText.getLayout();
        int line = LayoutHelper.getLineOfChar(layout, mCodeEditText.getSelectionStart());
        if (line >= 0 && line < layout.getLineCount()) {
            Editable text = mCodeEditText.getText();
            CharSequence lineText = null;
            if (text != null) {
                lineText = text.subSequence(layout.getLineStart(line), layout.getLineEnd(line));
            }
            ClipboardUtils.setClip(getContext(), lineText);
            ViewUtils.showSnack(this, R.string.text_already_copied_to_clip, false);
        }
    }


    public void deleteLine() {
        Layout layout = mCodeEditText.getLayout();
        int line = LayoutHelper.getLineOfChar(layout, mCodeEditText.getSelectionStart());
        if (line >= 0 && line < layout.getLineCount()) {
            Editable text = mCodeEditText.getText();
            if (text != null) {
                text.replace(layout.getLineStart(line), layout.getLineEnd(line), "");
            }
        }
    }

    public void jumpToStart() {
        mCodeEditText.setSelection(0);
    }

    public void jumpToEnd() {
        Editable text = mCodeEditText.getText();
        if (text != null) {
            mCodeEditText.setSelection(text.length());
        }
    }

    public void jumpToLineStart() {
        Layout layout = mCodeEditText.getLayout();
        int line = LayoutHelper.getLineOfChar(layout, mCodeEditText.getSelectionStart());
        if (line >= 0 && line < layout.getLineCount()) {
            mCodeEditText.setSelection(layout.getLineStart(line));
        }
    }

    public void jumpToLineEnd() {
        Layout layout = mCodeEditText.getLayout();
        int line = LayoutHelper.getLineOfChar(layout, mCodeEditText.getSelectionStart());
        if (line >= 0 && line < layout.getLineCount()) {
            mCodeEditText.setSelection(layout.getLineEnd(line) - 1);
        }
    }

    public void setTheme(Theme theme) {
        mTheme = theme;
        setBackgroundColor(mTheme.getBackgroundColor());
        mJavaScriptHighlighter.setTheme(theme);
        Editable text = mCodeEditText.getText();
        if (text != null) {
            mJavaScriptHighlighter.updateTokens(text.toString());
        }
        mCodeEditText.setTheme(mTheme);
        invalidate();
    }

    public boolean isTextChanged() {
        return mTextViewRedoUndo.isTextChanged();
    }

    public boolean canUndo() {
        return mTextViewRedoUndo.canUndo();
    }

    public boolean canRedo() {
        return mTextViewRedoUndo.canRedo();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mCodeEditText.postInvalidate();
    }

    public CodeEditText getCodeEditText() {
        return mCodeEditText;
    }

    public void setInitialText(String text) {
        mCodeEditText.setText(text);
        mTextViewRedoUndo.setDefaultText(text);
    }

    public void jumpTo(int line, int col) {
        Layout layout = mCodeEditText.getLayout();
        if (line >= 0 && (layout == null || line < layout.getLineCount())) {
            mCodeEditText.setSelection(mCodeEditText.getLayout().getLineStart(line) + col);
        }
    }

    public void setReadOnly(boolean readOnly) {
        mCodeEditText.setEnabled(!readOnly);
    }

    public void setRedoUndoEnabled(boolean enabled) {
        mTextViewRedoUndo.setEnabled(enabled);
    }

    public void setProgress(boolean progress) {
        if (mProcessDialog != null) {
            mProcessDialog.dismiss();
        }
        mProcessDialog = !progress ? null : new MaterialDialog.Builder(getContext())
                .content(R.string.text_processing)
                .progress(true, 0)
                .cancelable(false)
                .show();
    }

    public void setText(String text) {
        mCodeEditText.setText(text);
    }

    public void addCursorChangeCallback(CursorChangeCallback callback) {
        mCodeEditText.addCursorChangeCallback(callback);
    }

    public void removeCursorChangeCallback(CursorChangeCallback callback) {
        mCodeEditText.removeCursorChangeCallback(callback);
    }

    public void undo() {
        mTextViewRedoUndo.undo();
    }

    public void redo() {
        mTextViewRedoUndo.redo();
    }

    public void find(String keywords, boolean usingRegex) throws CheckedPatternSyntaxException {
        if (usingRegex) {
            try {
                Editable text = mCodeEditText.getText();
                if (text != null) {
                    mMatcher = Pattern.compile(keywords).matcher(text);
                }
            } catch (PatternSyntaxException e) {
                throw new CheckedPatternSyntaxException(e);
            }
            mKeywords = null;
        } else {
            mKeywords = keywords;
            mMatcher = null;
        }
        findNext();
    }

    public void replace(String keywords, String replacement, boolean usingRegex) throws CheckedPatternSyntaxException {
        mReplacement = replacement == null ? "" : replacement;
        find(keywords, usingRegex);
    }

    public void replaceAll(String keywords, String replacement, boolean usingRegex) throws CheckedPatternSyntaxException {
        if (!usingRegex) {
            keywords = Pattern.quote(keywords);
        }
        Editable codeEditTextText = mCodeEditText.getText();
        String text = null;
        if (codeEditTextText != null) {
            text = codeEditTextText.toString();
        }
        try {
            if (text != null) {
                text = text.replaceAll(keywords, replacement);
            }
        } catch (PatternSyntaxException e) {
            throw new CheckedPatternSyntaxException(e);
        }
        setText(text);
    }

    public void findNext() {
        int foundIndex;
        if (mMatcher == null) {
            if (mKeywords == null) {
                return;
            }
            Editable text = mCodeEditText.getText();
            if (text != null) {
                foundIndex = StringUtils.indexOf(text, mKeywords, mFoundIndex + 1);
            } else {
                foundIndex = -1;
            }
            if (foundIndex >= 0)
                mCodeEditText.setSelection(foundIndex, foundIndex + mKeywords.length());
        } else if (mMatcher.find(mFoundIndex + 1)) {
            foundIndex = mMatcher.start();
            mCodeEditText.setSelection(foundIndex, foundIndex + mMatcher.group().length());
        } else {
            foundIndex = -1;
        }
        if (foundIndex < 0 && mFoundIndex >= 0) {
            mFoundIndex = -1;
            findNext();
        } else {
            mFoundIndex = foundIndex;
        }
    }

    public void findPrev() {
        if (mMatcher != null) {
            ViewUtils.showToast(getContext(), R.string.error_regex_find_prev, true);
            return;
        }
        int len = mCodeEditText.getText().length();
        if (mFoundIndex <= 0) {
            mFoundIndex = len;
        }
        int index = mCodeEditText.getText().toString().lastIndexOf(mKeywords, mFoundIndex - 1);
        if (index < 0) {
            if (mFoundIndex != len) {
                mFoundIndex = len;
                findPrev();
            }
            return;
        }
        mFoundIndex = index;
        mCodeEditText.setSelection(index, index + mKeywords.length());
    }

    public void replaceSelection() {
        mCodeEditText.getText().replace(mCodeEditText.getSelectionStart(), mCodeEditText.getSelectionEnd(), mReplacement);
    }

    public void beautifyCode() {
        setProgress(true);
        int pos = mCodeEditText.getSelectionStart();
        mJsBeautifier.beautify(mCodeEditText.getText().toString(), new JsBeautifier.Callback() {
            @Override
            public void onSuccess(String beautifiedCode) {
                setProgress(false);
                mCodeEditText.setText(beautifiedCode);
                //格式化后恢复光标位置
                mCodeEditText.setSelection(pos);
            }

            @Override
            public void onException(Exception e) {
                setProgress(false);
                e.printStackTrace();
            }
        });
    }

    public void commentLine() {
        //如果没有选中，则添加文本/，否则选中的行前加//
        String selectionText = getSelectionRaw();
        if (selectionText.equals("")) {
            insert("/");
        } else {
            String[] lines = selectionText.split("\\n");
            StringBuilder commentedText = new StringBuilder();
            //处理取消注释
            if (lines[0].startsWith("//")) {
                for (String line : lines) {
                    commentedText.append(line.substring(2)).append("\n");
                }
            } else {
                for (String line : lines) {
                    commentedText.append("//").append(line).append("\n");
                }
            }
            mReplacement = commentedText.toString().replaceAll("\\n$", "");
            replaceSelection();
        }
    }

    public void commentBlock() {
        String selectionText = getSelectionRaw();
        if (!selectionText.isEmpty()) {
            String regex = "/\\*([^*]|\\*+[^*/])*\\*/";
            if (selectionText.matches(regex)) {
                // 取消块注释
                mReplacement = selectionText.substring(2, selectionText.length() - 2);
            } else {
                // 增加块注释
                mReplacement = "/*" + selectionText + "*/";
            }
            replaceSelection();
        }
    }

    public void insert(String insertText) {
        int selection = Math.max(mCodeEditText.getSelectionStart(), 0);
        mCodeEditText.getText().insert(selection, insertText);
    }

    public void insert(int line, String insertText) {
        int selection = mCodeEditText.getLayout().getLineStart(line);
        mCodeEditText.getText().insert(selection, insertText);
    }

    public void moveCursor(int dCh) {
        mCodeEditText.setSelection(mCodeEditText.getSelectionStart() + dCh);
    }

    public String getText() {
        return mCodeEditText.getText().toString();
    }

    public String getSelectionRaw() {
        int s = mCodeEditText.getSelectionStart();
        int e = mCodeEditText.getSelectionEnd();
        if (s == e) {
            return "";
        }
        return mCodeEditText.getText().toString().substring(s, e);
    }

    public Observable<String> getSelection() {
        return Observable.just(getSelectionRaw());
    }


    public void markTextAsSaved() {
        mTextViewRedoUndo.markTextAsUnchanged();
    }

    public LinkedHashMap<Integer, Breakpoint> getBreakpoints() {
        return mCodeEditText.getBreakpoints();
    }

    public void setDebuggingLine(int line) {
        mCodeEditText.setDebuggingLine(line);
    }

    public void setBreakpointChangeListener(BreakpointChangeListener listener) {
        mCodeEditText.setBreakpointChangeListener(listener);
    }

    public void addOrRemoveBreakpoint(int line) {
        if (!mCodeEditText.removeBreakpoint(line)) {
            mCodeEditText.addBreakpoint(line);
        }
    }

    public void addOrRemoveBreakpointAtCurrentLine() {
        Layout layout = mCodeEditText.getLayout();
        int line = LayoutHelper.getLineOfChar(layout, mCodeEditText.getSelectionStart());
        if (line >= 0 && line < layout.getLineCount()) {
            addOrRemoveBreakpoint(line);
        }
    }

    public void removeAllBreakpoints() {
        mCodeEditText.removeAllBreakpoints();
    }

    public void destroy() {
        mJavaScriptHighlighter.shutdown();
        mJsBeautifier.shutdown();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int codeWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int codeHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (mCodeEditText.getMinWidth() != codeWidth || mCodeEditText.getMinWidth() != codeWidth) {
            mCodeEditText.setMinWidth(codeWidth);
            mCodeEditText.setMinHeight(codeHeight);
            invalidate();
        }
        super.onDraw(canvas);
    }

    public static class Breakpoint {

        public int line;
        public boolean enabled = true;

        public Breakpoint(int line) {
            this.line = line;
        }
    }

    public interface BreakpointChangeListener {
        void onBreakpointChange(int line, boolean enabled);

        void onAllBreakpointRemoved(int count);
    }

    public void notifyPinchToZoomStrategyChanged(String newKey) {
        applyScaleGesture(newKey);
    }

    public static class CheckedPatternSyntaxException extends Exception {

        public CheckedPatternSyntaxException(PatternSyntaxException cause) {
            super(cause);
        }

    }

    public interface CursorChangeCallback {

        void onCursorChange(String line, int ch);

    }

}
