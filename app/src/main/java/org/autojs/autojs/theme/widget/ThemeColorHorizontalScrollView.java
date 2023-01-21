package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;

import java.lang.reflect.Field;

/**
 * Created by Stardust on 2016/8/14.
 */
public class ThemeColorHorizontalScrollView extends HorizontalScrollView implements ThemeColorMutable {
    private int mFadingEdgeColor;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;

    public ThemeColorHorizontalScrollView(Context context) {
        super(context);
        init();
    }

    public ThemeColorHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ThemeColorHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mEdgeGlowLeft = new EdgeEffect(getContext());
        mEdgeGlowRight = new EdgeEffect(getContext());
        ThemeColorManager.add(this);
    }

    public int getSolidColor() {
        return mFadingEdgeColor;
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        mFadingEdgeColor = color.colorPrimary;
        if (mEdgeGlowLeft != null && mEdgeGlowRight != null) {
            mEdgeGlowLeft.setColor(color.colorPrimary);
            mEdgeGlowRight.setColor(color.colorPrimary);
            syncEdgeEffect();
        }
        invalidate();
    }

    private void syncEdgeEffect() {
        try {
            Field f1 = HorizontalScrollView.class.getDeclaredField("mEdgeGlowLeft");
            f1.setAccessible(true);
            f1.set(this, mEdgeGlowLeft);

            Field f2 = HorizontalScrollView.class.getDeclaredField("mEdgeGlowRight");
            f2.setAccessible(true);
            f2.set(this, mEdgeGlowRight);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
