package org.autojs.autojs.theme.widget;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;

/**
 * Created by Stardust on 2017/5/10.
 */
public class ThemeColorImageViewCompat extends AppCompatImageView implements ThemeColorMutable {
    private int mColor;

    public ThemeColorImageViewCompat(Context context) {
        super(context);
        this.init();
    }

    public ThemeColorImageViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init();
    }

    public ThemeColorImageViewCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }

    public void setThemeColor(ThemeColor color) {
        if (this.mColor != color.colorPrimary) {
            this.mColor = color.colorPrimary;
            this.setColor(color.colorPrimary);
        }
    }

    public void setImageResource(int resId) {
        super.setImageResource(resId);
        if (this.mColor != 0) {
            this.setColor(this.mColor);
        }

    }

    private void setColor(int color) {
        getDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

}
