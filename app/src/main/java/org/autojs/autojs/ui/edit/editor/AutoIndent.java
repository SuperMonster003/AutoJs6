package org.autojs.autojs.ui.edit.editor;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * Created by Stardust on Feb 25, 2018.
 */
public class AutoIndent implements TextWatcher {

    // nbsp ×4
    private final String mIndent = "    ";
    private final CodeEditText mEditText;
    private boolean mInsertingIndent = false;
    private boolean mExtraIndent = false;
    private boolean mAutoIndent = false;
    private int mCursor;

    public AutoIndent(CodeEditText editText) {
        mEditText = editText;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        /* Empty body. */
    }

    /**
     * 判断是否是在光标处插入一个换行符的情况，是的话在下一个afterTextChanged回调中将调整缩进
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mInsertingIndent)
            return;
        //不是插入一个字符的情况
        if (count != 1 || before != 0) {
            return;
        }
        //边界检查，虽然可能并非必要
        if (start - 1 < 0 || start >= s.length()) {
            return;
        }
        char charInserted = s.charAt(start);
        //不是插入换行符的情况
        if (charInserted != '\n') {
            return;
        }
        mCursor = mEditText.getSelectionStart();
        //不是在光标处插入字符的情况
        if (mCursor != mEditText.getSelectionEnd() || mCursor != start + 1) {
            return;
        }
        //到这里已经可以判断为当前的字符变化为"在光标处插入一个换行符"的情况
        mAutoIndent = true;
        //我们再做一点额外判断。判断换行符之前的字符是否是括号，是的话下行将额外增加空格用于缩进
        char charBefore = s.charAt(start - 1);
        if (charBefore == '{' || charBefore == '(') {
            mExtraIndent = true;
        }

    }

    @Override
    public void afterTextChanged(Editable s) {
        // @Commented by SuperMonster003 on Apr 17, 2025.
        //  ! Does not work as expected.
        //  ! zh-CN: 未能达到预期效果.
        //  # // 换行符标准化: CRLF 或单独的 CR 替换成 LF
        //  # String original = s.toString();
        //  # String normalized = original
        //  #         .replace("\r\n", "\n")
        //  #         .replace("\r", "\n");
        //  # if (!normalized.equals(original)) {
        //  #     mInsertingIndent = true;
        //  #     s.replace(0, s.length(), normalized);
        //  #     mInsertingIndent = false;
        //  #     // 结束后返回等待下一次回调
        //  #     return;
        //  # }
        if (mInsertingIndent || !mAutoIndent) {
            return;
        }
        CharSequence indent = getLastLineIndent();
        if (mExtraIndent) {
            indent = mIndent + indent;
        }
        mInsertingIndent = true;
        s.insert(mCursor, indent);
        mInsertingIndent = false;
        mExtraIndent = mAutoIndent = false;
        mCursor = -1;
    }

    private CharSequence getLastLineIndent() {
        if (mCursor < 0 || mCursor > mEditText.length()) {
            return "";
        }
        int line = LayoutHelper.getLineOfChar(mEditText.getLayout(), mCursor);
        if (line == 0) {
            return "";
        }
        int lastLineStart = mEditText.getLayout().getLineStart(line - 1);
        int lastLineEnd = mEditText.getLayout().getLineEnd(line);
        for (int i = lastLineStart; i < lastLineEnd; i++) {
            if (mEditText.getText().charAt(i) != ' ') {
                if (i == lastLineStart) {
                    return "";
                } else {
                    return mEditText.getText().subSequence(lastLineStart, i);
                }
            }
        }
        return mEditText.getText().subSequence(lastLineStart, lastLineEnd);
    }

}
