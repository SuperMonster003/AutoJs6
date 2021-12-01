package com.stardust.theme.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ScrollView;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorMutable;
import com.stardust.theme.internal.ScrollingViewEdgeGlowColorHelper;
import com.stardust.theme.ThemeColorManager;

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
