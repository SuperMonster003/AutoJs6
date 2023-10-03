package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/3/5.
 * Modified by SuperMonster003 as of Dec 1, 2021.
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
        setContentInsetStartWithNavigation(getContext().getResources().getDimensionPixelSize(R.dimen.toolbar_content_inset_start_with_navigation));
        setTitleTextAppearance(getContext(), R.style.TextAppearanceMainTitle);
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        setBackgroundColor(color.colorPrimary);
    }

}
