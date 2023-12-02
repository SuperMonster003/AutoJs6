package org.autojs.autojs.theme;

import androidx.core.content.ContextCompat;

import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Mar 12, 2017.
 */
public class ThemeColorManagerCompat {

    public static int getColorPrimary() {
        int color = ThemeColorManager.getColorPrimary();
        return color == 0 ? ContextCompat.getColor(GlobalAppContext.get(), R.color.colorPrimary) : color;
    }

}