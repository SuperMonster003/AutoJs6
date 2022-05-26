package com.stardust.theme.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;
import com.stardust.theme.internal.ScrollingViewEdgeGlowColorHelper;

/**
 * Created by Stardust on 2016/8/14.
 */
public class ThemeColorScrollView extends ScrollView implements ThemeColorMutable {

    private int mFadingEdgeColor;

    public ThemeColorScrollView(Context context) {
        super(context);
        init();
    }

    public ThemeColorScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeColorScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mFadingEdgeColor = super.getSolidColor();
        ThemeColorManager.add(this);
    }

    public int getSolidColor() {
        return mFadingEdgeColor;
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        mFadingEdgeColor = color.colorPrimary;
        ScrollingViewEdgeGlowColorHelper.setEdgeGlowColor(this, mFadingEdgeColor);
        invalidate();
    }
}
