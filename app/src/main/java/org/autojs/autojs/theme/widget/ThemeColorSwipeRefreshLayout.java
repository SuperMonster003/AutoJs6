package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Jan 23, 2018.
 */
public class ThemeColorSwipeRefreshLayout extends SwipeRefreshLayout implements ThemeColorMutable {

    public ThemeColorSwipeRefreshLayout(Context context) {
        super(context);
        init();
    }

    public ThemeColorSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
        setProgressBackgroundColorSchemeResource(R.color.swipe_refresh_background);
    }

    @Override
    public void setThemeColor(ThemeColor themeColor) {
        setColorSchemeColors(themeColor.colorPrimary);
    }

}
