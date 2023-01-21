package org.autojs.autojs.theme;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import org.autojs.autojs.pref.Pref;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on 2017/3/5.
 */
public class ThemeColor {

    public int colorPrimary, colorAccent, colorPrimaryDark;

    public ThemeColor() {

    }

    public ThemeColor(int color) {
        this(color, color, color);
    }

    public ThemeColor(int colorPrimary, int colorPrimaryDark, int colorAccent) {
        this.colorPrimary = colorPrimary;
        this.colorPrimaryDark = colorPrimaryDark;
        this.colorAccent = colorAccent;
    }

    public ThemeColor colorPrimary(int colorPrimary) {
        this.colorPrimary = colorPrimary;
        return this;
    }

    public ThemeColor colorPrimaryDark(int colorPrimaryDark) {
        this.colorPrimaryDark = colorPrimaryDark;
        return this;
    }

    public ThemeColor colorAccent(int colorAccent) {
        this.colorAccent = colorAccent;
        return this;
    }

    public void saveIn() {
        Pref.putInt(R.string.key_theme_color_primary, colorPrimary);
        Pref.putInt(R.string.key_theme_color_primary_dark, colorPrimaryDark);
        Pref.putInt(R.string.key_theme_color_accent, colorAccent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ThemeColor color) {
            return colorPrimary == color.colorPrimary && colorPrimaryDark == color.colorPrimaryDark
                    && colorAccent == color.colorAccent;
        }
        return false;
    }

    public ThemeColor readFrom() {
        colorPrimary = Pref.getInt(R.string.key_theme_color_primary, ThemeColorManager.getDefaultThemeColor().colorPrimary);
        colorAccent = Pref.getInt(R.string.key_theme_color_accent, ThemeColorManager.getDefaultThemeColor().colorAccent);
        colorPrimaryDark = Pref.getInt(R.string.key_theme_color_primary_dark, ThemeColorManager.getDefaultThemeColor().colorPrimaryDark);
        return this;
    }

    public static ThemeColor fromPreferences() {
        return Pref.containsKey(
                R.string.key_theme_color_primary,
                R.string.key_theme_color_primary_dark,
                R.string.key_theme_color_accent
        ) ? new ThemeColor().readFrom() : null;
    }

    public static ThemeColor fromColorRes(Context context, @ColorRes int colorPrimaryRes) {
        return fromColorRes(context, colorPrimaryRes, colorPrimaryRes, colorPrimaryRes);
    }

    public static ThemeColor fromColorRes(Context context, @ColorRes int colorPrimaryRes, @ColorRes int colorPrimaryDarkRes, @ColorRes int colorAccentRes) {
        return new ThemeColor()
                .colorPrimary(ContextCompat.getColor(context, colorPrimaryRes))
                .colorPrimaryDark(ContextCompat.getColor(context, colorPrimaryDarkRes))
                .colorAccent(ContextCompat.getColor(context, colorAccentRes));
    }

}