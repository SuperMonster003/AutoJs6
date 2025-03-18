package org.autojs.autojs.theme.dialog;

import android.content.Context;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import com.afollestad.materialdialogs.Theme;
import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs.util.ColorUtils;

/**
 * Created by Stardust on Mar 5, 2017.
 */
public class ThemeColorMaterialDialogBuilder extends MaterialDialog.Builder implements ThemeColorMutable {
    public ThemeColorMaterialDialogBuilder(@NonNull Context context) {
        super(context);
        ThemeColorManager.add(this);
    }

    @Override
    public void setThemeColor(ThemeColor themeColor) {
        int color = ColorUtils.adjustColorForContrast(backgroundColor, themeColor.colorPrimary, 2.3);
        positiveColor(color);
        negativeColor(color);
        neutralColor(color);
    }
}
