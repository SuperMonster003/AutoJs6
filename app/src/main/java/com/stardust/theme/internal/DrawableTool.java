package com.stardust.theme.internal;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

/**
 * Created by Stardust on 2017/2/12.
 */
public class DrawableTool {

    public static Drawable filterDrawableColor(Drawable drawable, int color) {
        if (drawable == null || drawable.getConstantState() == null)
            return null;
        Drawable res = drawable.getConstantState().newDrawable().mutate();
        res.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return res;
    }
}
