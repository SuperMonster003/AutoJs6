package com.stardust.theme.preference;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

/**
 * Created by Stardust on 2017/8/25.
 */
public class Test extends SwitchPreference {
    public Test(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public Test(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Test(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Test(Context context) {
        super(context);
    }
}
