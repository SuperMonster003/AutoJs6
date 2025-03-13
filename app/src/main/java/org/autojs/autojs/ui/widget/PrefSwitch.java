package org.autojs.autojs.ui.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import org.autojs.autojs.core.ui.widget.JsSwitch;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.theme.ThemeColor;
import org.autojs.autojs.theme.ThemeColorHelper;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs.theme.ThemeColorMutable;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Aug 6, 2017.
 */
public class PrefSwitch extends JsSwitch implements SharedPreferences.OnSharedPreferenceChangeListener, ThemeColorMutable {

    private String mPrefKey;
    private boolean mDefaultChecked;
    private boolean mIsOnSharedPreferenceChangeListenerRegistered;

    public PrefSwitch(Context context) {
        super(context);
        init(null);
    }

    public PrefSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PrefSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        ThemeColorManager.add(this);
        if (attrs == null)
            return;
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.PrefSwitch);
        mPrefKey = a.getString(R.styleable.PrefSwitch_key);
        mDefaultChecked = a.getBoolean(R.styleable.PrefSwitch_defaultVal, false);
        if (mPrefKey != null) {
            registerOnSharedPreferenceChangeListenerIfNeeded();
            readInitialState();
        } else {
            setChecked(mDefaultChecked, false);
        }
        a.recycle();
    }

    @Override
    public void setThemeColor(ThemeColor color) {
        ThemeColorHelper.setColorPrimary(this, color.colorPrimary, true);
    }

    private void readInitialState() {
        if (mPrefKey != null) {
            setChecked(Pref.getBoolean(mPrefKey, mDefaultChecked), false);
        }
    }

    private void notifyPrefChanged(boolean isChecked) {
        if (mPrefKey != null) {
            Pref.putBoolean(mPrefKey, isChecked);
        }
    }

    private void registerOnSharedPreferenceChangeListenerIfNeeded() {
        if (!mIsOnSharedPreferenceChangeListenerRegistered) {
            mIsOnSharedPreferenceChangeListenerRegistered = true;
            Pref.registerOnSharedPreferenceChangeListener(this);
        }
    }

    public void setPrefKey(String prefKey) {
        mPrefKey = prefKey;
        registerOnSharedPreferenceChangeListenerIfNeeded();
        if (mPrefKey != null) {
            setChecked(Pref.getBoolean(mPrefKey, mDefaultChecked), false);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        notifyPrefChanged(checked);
    }

    public void setChecked(boolean checked, boolean notifyChange) {
        super.setChecked(checked, notifyChange);
        if (notifyChange) {
            notifyPrefChanged(checked);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mPrefKey != null && mPrefKey.equals(key)) {
            setChecked(Pref.getBoolean(mPrefKey, isChecked()), false);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility == VISIBLE) {
            readInitialState();
        }
    }

}
