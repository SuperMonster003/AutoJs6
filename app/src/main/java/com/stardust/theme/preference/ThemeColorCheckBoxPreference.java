package com.stardust.theme.preference;

import android.content.Context;
import android.os.Build;
import android.preference.CheckBoxPreference;

import androidx.annotation.RequiresApi;
import android.util.AttributeSet;

import org.autojs.autojs.R;

/**
 * Created by Stardust on 2017/3/5.
 */

public class ThemeColorCheckBoxPreference extends CheckBoxPreference {

    public ThemeColorCheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ThemeColorCheckBoxPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public ThemeColorCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorCheckBoxPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.mt_theme_color_check_box_preference);
    }

}
