package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;

/**
 * Created by Stardust on 2016/8/16.
 */
public class ThemeColorFloatingActionButton extends FloatingActionButton implements ThemeColorMutable {
    public ThemeColorFloatingActionButton(Context context) {
        super(context);
        init();
    }

    public ThemeColorFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        setBackgroundTintList(ColorStateList.valueOf(color.colorAccent));
    }

}
