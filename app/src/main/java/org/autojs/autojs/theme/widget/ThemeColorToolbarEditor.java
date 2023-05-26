package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

public class ThemeColorToolbarEditor extends ThemeColorToolbar {

    public ThemeColorToolbarEditor(Context context) {
        super(context);
        init();
    }

    public ThemeColorToolbarEditor(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorToolbarEditor(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setContentInsetStartWithNavigation(0);
    }

}
