package com.stardust.theme.widget;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;

/**
 * Created by Stardust on 2017/3/5.
 */

public class ThemeColorTextView extends AppCompatTextView implements ThemeColorMutable {

    public ThemeColorTextView(Context context) {
        super(context);
        init();
    }

    public ThemeColorTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ThemeColorManager.add(this);
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        setTextColor(color.colorPrimary);
    }
}
