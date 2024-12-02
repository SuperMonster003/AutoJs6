package org.autojs.autojs.core.ui.inflater.util;

import android.content.Context;
import android.view.View;

/**
 * Created by Stardust on Nov 5, 2017.
 */
public class Res {

    public static int parseStyle(View view, String value) {
        return parseStyle(view.getContext(), value);
    }

    public static int parseStyle(Context context, String value) {
        String prefix = "@style/";
        if (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        // FIXME by Stardust on Nov 5, 2017.
        //  ! Can or should it retrieve android.R.style or styleable?
        //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
        //  ! 是否可以, 或者说是否应该, 从 android.R.style 或 styleable 获取资源值?
        return context.getResources().getIdentifier(value, "style", context.getPackageName());
    }

}
