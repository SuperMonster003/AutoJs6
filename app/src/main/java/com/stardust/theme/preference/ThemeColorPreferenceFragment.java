package com.stardust.theme.preference;

import android.annotation.TargetApi;
import android.os.Build;
import android.preference.PreferenceFragment;
import android.widget.ListView;

import com.stardust.theme.ThemeColor;
import com.stardust.theme.internal.ScrollingViewEdgeGlowColorHelper;
import com.stardust.theme.ThemeColorManager;
import com.stardust.theme.ThemeColorMutable;

import java.lang.reflect.Field;

/**
 * Created by Stardust on 2016/8/14.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ThemeColorPreferenceFragment extends PreferenceFragment implements ThemeColorMutable {

    private static Field LIST_VIEW;
    private int mThemeColor;
    private boolean hasAppliedThemeColor = false;

    static {
        try {
            LIST_VIEW = PreferenceFragment.class.getDeclaredField("mList");
            LIST_VIEW.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private ListView getListView() {
        try {
            return (ListView) LIST_VIEW.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addPreferencesFromResource(int resId) {
        super.addPreferencesFromResource(resId);
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
        ListView listView = getListView();
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
