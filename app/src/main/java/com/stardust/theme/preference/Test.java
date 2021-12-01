package com.stardust.theme.preference;

import android.content.Context;
import android.os.Build;
import android.preference.SwitchPreference;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;

/**
 * Created by Stardust on 2017/8/25.
 */

public class Test extends SwitchPreference {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
