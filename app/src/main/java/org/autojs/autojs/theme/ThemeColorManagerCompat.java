package org.autojs.autojs.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDelegate;

import com.stardust.app.GlobalAppContext;

import org.autojs.autojs6.R;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.ThemeColorManager;

/**
 * Created by Stardust on 2017/3/12.
 */
public class ThemeColorManagerCompat {

    private static SharedPreferences sSharedPreferences;
    private static final SharedPreferences.OnSharedPreferenceChangeListener sPreferenceChangeListener = (sharedPreferences, key) -> {
        if (key.equals(GlobalAppContext.get().getString(R.string.key_night_mode))) {
            setNightModeEnabled(sharedPreferences.getBoolean(key, false));
        }
    };

    public static int getColorPrimary() {
        int color = ThemeColorManager.getColorPrimary();
        if (color == 0) {
            return ContextCompat.getColor(GlobalAppContext.get(), R.color.colorPrimary);
        } else {
            return color;
        }
    }

    public static void setNightModeEnabled(boolean enabled) {
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            ThemeColor currentTheme = ThemeColor.fromPreferences(PreferenceManager.getDefaultSharedPreferences(GlobalAppContext.get()), null);
            if (currentTheme != null) {
                currentTheme.saveIn(sSharedPreferences);
            }
            ThemeColorManager.setThemeColor(ContextCompat.getColor(GlobalAppContext.get(), R.color.theme_color_black));
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            ThemeColor previousTheme = ThemeColor.fromPreferences(sSharedPreferences, null);
            if (previousTheme != null) {
                ThemeColorManager.setThemeColor(previousTheme.colorPrimary);
            }
        }
    }

    public static void init(ThemeColor defaultThemeColor) {
        Context mContext = GlobalAppContext.get();
        sSharedPreferences = mContext.getSharedPreferences("theme_color", Context.MODE_PRIVATE);
        ThemeColorManager.setDefaultThemeColor(defaultThemeColor);
        ThemeColorManager.init(mContext);
        PreferenceManager.getDefaultSharedPreferences(mContext).registerOnSharedPreferenceChangeListener(sPreferenceChangeListener);
    }
}