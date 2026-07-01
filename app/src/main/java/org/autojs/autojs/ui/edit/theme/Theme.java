package org.autojs.autojs.ui.edit.theme;

import android.graphics.Color;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;

import org.autojs.autojs.model.editor.EditorColors;
import org.autojs.autojs.model.editor.EditorTheme;
import org.autojs.autojs.model.editor.TokenColor;
import org.autojs.autojs.util.ColorUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

/**
 * Created by Stardust on Feb 16, 2018.
 */
public class Theme {

    private int mBackgroundColor = Color.WHITE;
    private int mForegroundColor = Color.BLACK;
    private int mLineNumberColor = Color.GRAY;
    private final SparseIntArray mTokenColors = new SparseIntArray();
    private int mImeBarBackgroundColor = 0xDDFFFFFF;
    private int mImeBarForegroundColor = Color.WHITE;
    private final EditorTheme mEditorTheme;
    private final String mAceTheme;
    private final boolean mAceOnly;
    private int mLineHighlightBackground;
    private final int mBreakpointColor;
    private int mDebuggingLineBackground;

    public Theme(EditorTheme theme) {
        this(theme, null, false);
    }

    private Theme(EditorTheme theme, String aceTheme, boolean aceOnly) {
        mEditorTheme = theme;
        mAceTheme = aceTheme;
        mAceOnly = aceOnly;

        EditorColors editorColors = theme.getEditorColors();
        if (editorColors != null) {
            mBackgroundColor = parseColor(editorColors.getEditorBackground(), mBackgroundColor);
            mForegroundColor = parseColor(editorColors.getEditorForeground(), mForegroundColor);
            mLineNumberColor = parseColor(editorColors.getLineNumberForeground(), mLineNumberColor);
            mImeBarBackgroundColor = parseColor(editorColors.getImeBackgroundColor(), mImeBarBackgroundColor);
            mImeBarForegroundColor = parseColor(editorColors.getImeForegroundColor(), mImeBarForegroundColor);
            mLineHighlightBackground = parseColor(editorColors.getLineHighlightBackground(), mLineHighlightBackground);
            mDebuggingLineBackground = parseColor(editorColors.getDebuggingLineBackground(), mDebuggingLineBackground);
            mBreakpointColor = parseColor(editorColors.getBreakpointForeground(), mBackgroundColor);
        } else {
            mBreakpointColor = mBackgroundColor;
        }

        List<TokenColor> tokenColors = theme.getTokenColors();
        if (tokenColors == null) {
            tokenColors = Collections.emptyList();
        }
        for (TokenColor tokenColor : tokenColors) {
            String foregroundStr = tokenColor.getSettings().getForeground();
            if (foregroundStr == null)
                continue;
            int foreground = ColorUtils.toInt(foregroundStr);
            for (String scope : tokenColor.getScope()) {
                setTokenColor(scope, foreground);
            }
        }
    }

    private int parseColor(String color, int defaultValue) {
        if (color == null)
            return defaultValue;
        try {
            return ColorUtils.toInt(color);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public String getName() {
        return mEditorTheme.getName();
    }

    private void setTokenColor(String scope, int foreground) {
        for (int token : TokenMapping.getTokensForScope(scope)) {
            mTokenColors.put(token, foreground);
        }
    }

    public int getColorForToken(int token) {
        return mTokenColors.get(token, mForegroundColor);
    }

    public int getLineNumberColor() {
        return mLineNumberColor;
    }

    public static Theme getDefault(android.content.Context context) {
        return fromAssetsJson(context, "editor/theme/light_plus.json");
    }

    public static Theme fromJson(String json) {
        EditorTheme theme = EditorTheme.fromJson(json);
        if (theme == null)
            return null;
        return new Theme(theme);
    }

    public static Theme fromJson(Reader reader) {
        EditorTheme theme = EditorTheme.fromJson(reader);
        if (theme == null)
            return null;
        return new Theme(theme);
    }

    public static Theme fromAssetsJson(android.content.Context context, String assetsPath) {
        try {
            return fromJson(new InputStreamReader(context.getAssets().open(assetsPath)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Theme fromAceTheme(String name, String aceTheme, boolean dark, String backgroundColor, String foregroundColor) {
        EditorTheme editorTheme = new EditorTheme();
        editorTheme.setName(name);
        editorTheme.setTokenColors(Collections.emptyList());

        EditorColors editorColors = new EditorColors();
        editorColors.setEditorBackground(backgroundColor);
        editorColors.setEditorForeground(foregroundColor);
        editorColors.setLineNumberForeground(dark ? "#9E9E9E" : "#757575");
        editorColors.setImeBackgroundColor(dark ? "#dd1e1e1e" : "#ddf0f1f2");
        editorColors.setImeForegroundColor(dark ? "#f1f1f1" : "#222327");
        editorColors.setLineHighlightBackground(dark ? "#2e2e35" : "#e8ebf0");
        editorColors.setBreakpointForeground("#1976D2");
        editorColors.setDebuggingLineBackground("#40c4ff");
        editorTheme.setEditorColors(editorColors);

        return new Theme(editorTheme, aceTheme, true);
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public String getAceTheme() {
        return mAceTheme;
    }

    public boolean isAceOnly() {
        return mAceOnly;
    }

    public int getImeBarBackgroundColor() {
        return mImeBarBackgroundColor;
    }

    public void setImeBarBackgroundColor(int imeBarBackgroundColor) {
        mImeBarBackgroundColor = imeBarBackgroundColor;
    }

    public int getImeBarForegroundColor() {
        return mImeBarForegroundColor;
    }

    public void setImeBarForegroundColor(int imeBarForegroundColor) {
        mImeBarForegroundColor = imeBarForegroundColor;
    }

    public int getLineHighlightBackgroundColor() {
        return mLineHighlightBackground;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Theme theme = (Theme) o;

        return mEditorTheme.getName() != null
                ? mEditorTheme.getName().equals(theme.mEditorTheme.getName())
                : theme.mEditorTheme.getName() == null;
    }

    @NonNull
    public String toString() {
        return getName();
    }

    public int getBreakpointColor() {
        return mBreakpointColor;
    }

    public int getDebuggingLineBackgroundColor() {
        return mDebuggingLineBackground;
    }

}
