package com.stardust.theme.preference;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.SwitchPreference;

import androidx.appcompat.widget.SwitchCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.Switch;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorHelper;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;

/**
 * Created by Stardust on 2017/3/5.
 */
public class ThemeColorSwitchPreference extends SwitchPreference implements ThemeColorMutable {

    private View mCheckableView;
    private int mColor = Color.TRANSPARENT;

    public ThemeColorSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ThemeColorSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeColorSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorSwitchPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mCheckableView = view.findViewById(Resources.getSystem().getIdentifier("switch_widget", "id", "android"));

        if (mColor != Color.TRANSPARENT) {
            applyColor();
        }
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        mColor = color.colorPrimary;
        if (mCheckableView != null) {
            applyColor();
        }
    }

    public void applyColor() {
        if (mCheckableView != null && mCheckableView instanceof Checkable) {
            if (mCheckableView instanceof final Switch switchView) {
                ThemeColorHelper.setColorPrimary(switchView, mColor);
            }

            if (mCheckableView instanceof final SwitchCompat switchView) {
                ThemeColorHelper.setColorPrimary(switchView, mColor);

            }
        }
    }
}
