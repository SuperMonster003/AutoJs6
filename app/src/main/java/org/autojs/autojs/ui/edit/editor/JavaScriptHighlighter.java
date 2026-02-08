package org.autojs.autojs.ui.edit.editor;

import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;

import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.rhino.TokenStream;
import org.autojs.autojs.ui.edit.theme.Theme;
import org.autojs.autojs.ui.widget.SimpleTextWatcher;
import org.mozilla.javascript.Token;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 8, 2026.
 */
public class JavaScriptHighlighter implements SimpleTextWatcher.AfterTextChangedListener {

    // Syntax highlight hard limit for performance and memory.
    // zh-CN: 为性能和内存设置的语法高亮硬限制.
    public static final int MAX_HIGHLIGHT_CHARS = 512 * 1024;

    public static class HighlightTokens {

        public final int[] colors;
        private final String mText;
        private int mCount;
        private final int mId;

        public HighlightTokens(String text, int id) {
            colors = new int[text.length()];
            mText = text;
            mId = id;
        }

        public int getId() {
            return mId;
        }

        public void addToken(int tokenStart, int tokenEnd, int color) {
            if (mCount < tokenStart) {
                int c = mCount > 0 ? colors[mCount - 1] : color;
                for (int i = mCount; i < tokenStart; i++) {
                    colors[i] = c;
                }
            }
            for (int i = tokenStart; i < tokenEnd; i++) {
                colors[i] = color;
            }
            mCount = tokenEnd;
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + "{count = " + mCount + ", length = " + mText.length() + "}";
        }

        public int getCharCount() {
            return mCount;
        }

        public String getText() {
            return mText;
        }
    }

    private Theme mTheme;
    private final CodeEditText mCodeEditText;
    private final ThreadPoolExecutor mExecutorService = new ThreadPoolExecutor(3, 6,
            2L, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
    private final AtomicInteger mRunningHighlighterId = new AtomicInteger();
    private final TextWatcher mTextWatcher;

    public JavaScriptHighlighter(Theme theme, CodeEditText codeEditText) {
        mExecutorService.allowCoreThreadTimeOut(true);
        mTheme = theme;
        mCodeEditText = codeEditText;
        mTextWatcher = new SimpleTextWatcher(this);
        codeEditText.addTextChangedListener(mTextWatcher);
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mTheme == null) {
            return;
        }

        // Skip highlighting during progressive loading to avoid UI-thread allocations and CPU contention.
        // zh-CN: 渐进式加载期间跳过高亮, 避免 UI 线程分配与 CPU 争用.
        if (mCodeEditText != null && mCodeEditText.isLoadingText()) {
            mRunningHighlighterId.incrementAndGet();
            mCodeEditText.clearHighlightTokens();
            return;
        }

        // Avoid calling toString() on huge Editable, which allocates a large String on UI thread.
        // zh-CN: 避免对超大的 Editable 调用 toString(), 否则会在 UI 线程分配巨大的 String.
        if (s != null && s.length() > MAX_HIGHLIGHT_CHARS) {
            mRunningHighlighterId.incrementAndGet();
            if (mCodeEditText != null) {
                mCodeEditText.clearHighlightTokens();
            }
            return;
        }

        updateTokens(s.toString());
    }

    public void setTheme(Theme theme) {
        mTheme = theme;
    }

    public void updateTokens(String sourceString) {
        if (mTheme == null) {
            return;
        }

        // Disable highlighting for very large text to avoid ANR/OOM on low-end devices.
        // zh-CN: 对超大文本禁用高亮, 避免低端设备出现 ANR/OOM.
        if (sourceString.length() > MAX_HIGHLIGHT_CHARS) {
            mRunningHighlighterId.incrementAndGet();
            mCodeEditText.clearHighlightTokens();
            return;
        }

        final int id = mRunningHighlighterId.incrementAndGet();
        if (mExecutorService.isShutdown() || mExecutorService.isTerminated() || mExecutorService.isTerminating()) {
            return;
        }

        // Keep only the latest highlight task.
        // zh-CN: 仅保留最新的高亮任务, 丢弃过期任务以避免队列堆积.
        mExecutorService.getQueue().clear();

        mExecutorService.execute(() -> {
            try {
                updateTokens(sourceString, id);
            } catch (IOException neverHappen) {
                throw new UncheckedIOException(neverHappen);
            }
        });
    }

    private void updateTokens(String sourceString, int id) throws IOException {
        // Drop stale tasks early.
        // zh-CN: 尽早丢弃过期任务.
        if (id != mRunningHighlighterId.get()) {
            return;
        }

        TokenStream ts = new TokenStream(null, sourceString, 0);
        HighlightTokens highlightTokens = new HighlightTokens(sourceString, id);
        int token;
        int color = mTheme.getColorForToken(Token.NAME);

        while ((token = ts.getToken()) != Token.EOF) {
            // Abort quickly if a newer task arrives.
            // zh-CN: 如果有更新任务到来, 则尽快中止.
            if (id != mRunningHighlighterId.get()) {
                return;
            }
            color = mTheme.getColorForToken(token);
            highlightTokens.addToken(ts.getTokenBeg(), ts.getTokenEnd(), color);
        }

        if (highlightTokens.getCharCount() < sourceString.length()) {
            highlightTokens.addToken(highlightTokens.getCharCount(), sourceString.length(), color);
        }
        mCodeEditText.updateHighlightTokens(highlightTokens);
    }

    public void shutdown() {
        mCodeEditText.removeTextChangedListener(mTextWatcher);
        mExecutorService.shutdownNow();
    }

}
