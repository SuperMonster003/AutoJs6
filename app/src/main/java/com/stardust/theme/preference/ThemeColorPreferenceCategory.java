package com.stardust.theme.preference;

import android.content.Context;
import android.graphics.Color;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;


/**
 * Created by Stardust on 2016/8/8.
 */
public class ThemeColorPreferenceCategory extends PreferenceCategory implements ThemeColorMutable {

    private TextView mTitleTextView;
    private int mColor = Color.TRANSPARENT;

    public ThemeColorPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ThemeColorPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeColorPreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorPreferenceCategory(Context context) {
        super(context);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }

    public void setTitleTextColor(int titleTextColor) {
        mColor = titleTextColor;
        if (mTitleTextView != null)
            mTitleTextView.setTextColor(titleTextColor);
    }

    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        mTitleTextView = view.findViewById(android.R.id.title);
        if (mColor != Color.TRANSPARENT)
            mTitleTextView.setTextColor(mColor);
        return view;
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        mColor = color.colorPrimary;
        if (mTitleTextView != null)
            mTitleTextView.setTextColor(mColor);
    }
}
