package org.autojs.autojs.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.util.AttributeSet;

import androidx.preference.PreferenceManager;

import org.autojs.autojs.Pref;
import org.autojs.autojs6.R;

/**
 * Created by SuperMonster003 on May 31, 2022.
 */

public class CheckForUpdatesPreference extends Preference implements SharedPreferences.OnSharedPreferenceChangeListener {
    public CheckForUpdatesPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public CheckForUpdatesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public CheckForUpdatesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CheckForUpdatesPreference(Context context) {
        super(context);
        init();
    }

    @Override
    protected void onAttachedToActivity() {
        setSummaryIfNeeded();
        super.onAttachedToActivity();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        setSummaryIfNeeded();
    }

    private void setSummaryIfNeeded() {
        String lastChecked = Pref.getLastUpdatesCheckedTimeString();
        if (lastChecked != null) {
            setSummary(getContext().getString(R.string.text_last_updates_checked_time, lastChecked));
        }
    }
}
