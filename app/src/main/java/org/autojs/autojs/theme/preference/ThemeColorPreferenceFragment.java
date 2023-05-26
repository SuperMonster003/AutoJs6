package org.autojs.autojs.theme.preference;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs.theme.internal.ScrollingViewEdgeGlowColorHelper;

/**
 * Created by Stardust on 2016/8/14.
 */
public class ThemeColorPreferenceFragment extends PreferenceFragmentCompat implements ThemeColorMutable {

    private int mThemeColor;
    private boolean hasAppliedThemeColor = false;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {

    }

    @Override
    public void setPreferencesFromResource(int preferencesResId, @Nullable String key) {
        super.setPreferencesFromResource(preferencesResId, key);
        ThemeColorManager.add(this);
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        if (mThemeColor == color.colorPrimary)
            return;
        mThemeColor = color.colorPrimary;
        hasAppliedThemeColor = false;
    }

    private void applyThemeColor() {
        RecyclerView listView = getListView();
        if (listView != null) {
            ScrollingViewEdgeGlowColorHelper.setEdgeGlowColor(listView, mThemeColor);
            hasAppliedThemeColor = true;
        }
    }

    public void onResume() {
        super.onResume();
        if (!hasAppliedThemeColor) {
            applyThemeColor();
        }
    }

}
