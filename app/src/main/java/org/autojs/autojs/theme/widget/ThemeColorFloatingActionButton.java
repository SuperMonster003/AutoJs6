package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs.util.ColorUtils;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Aug 16, 2016.
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
        int colorAccent = color.colorAccent;
        setBackgroundTintList(ColorStateList.valueOf(colorAccent));
        var tintColorRes = ColorUtils.isLuminanceLight(colorAccent)
                ? R.color.fab_tint_dark
                : R.color.fab_tint_light;
        var tintColor = getResources().getColor(tintColorRes, null);
        setImageTintList(ColorStateList.valueOf(tintColor));
    }

}
