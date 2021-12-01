package com.stardust.theme.widget;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import android.util.AttributeSet;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;

/**
 * Created by Stardust on 2017/3/5.
 */

public class ThemeColorToolbar extends Toolbar implements ThemeColorMutable {

    public ThemeColorToolbar(Context context) {
        super(context);
        init();
    }


    public ThemeColorToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }


    @Override
    public void setThemeColor(ThemeColor color) {
        setBackgroundColor(color.colorPrimary);
    }
}
