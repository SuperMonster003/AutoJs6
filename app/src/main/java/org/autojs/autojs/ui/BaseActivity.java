package org.autojs.autojs.ui;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.stardust.app.GlobalAppContext;
import com.stardust.theme.ThemeColorManager;
import com.zeugmasolutions.localehelper.LocaleHelper;
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegate;
import com.zeugmasolutions.localehelper.LocaleHelperActivityDelegateImpl;

import org.autojs.autojs.Pref;
import org.autojs.autojs.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Stardust on 2017/1/23.
 * Modified by SuperMonster003 as of Feb 18, 2022.
 */

public abstract class BaseActivity extends AppCompatActivity {

    protected static final int PERMISSION_REQUEST_CODE = 11186;
    private boolean mShouldApplyDayNightModeForOptionsMenu = true;
    private final LocaleHelperActivityDelegate localeDelegate = new LocaleHelperActivityDelegateImpl();
    private final LocaleHelper localeHelper = LocaleHelper.INSTANCE;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localeDelegate.onCreate(this);
    }

    protected void applyDayNightMode() {
        GlobalAppContext.post(() -> {
            if (Pref.isNightModeEnabled()) {
                setNightModeEnabled(Pref.isNightModeEnabled());
            }
        });
    }

    @NonNull
    @Override
    public AppCompatDelegate getDelegate() {
        return localeDelegate.getAppCompatDelegate(super.getDelegate());
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(localeDelegate.attachBaseContext(newBase));
        if (localeHelper.hasLocaleSelection(this)) {
            localeHelper.setLocale(this, localeHelper.getLocale(this));
        }
    }

    public void setNightModeEnabled(boolean enabled) {
        if (enabled) {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        if (getDelegate().applyDayNight()) {
            recreate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if ((getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) == 0) {
            ThemeColorManager.addActivityStatusBar(this);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        localeDelegate.onResumed(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        localeDelegate.onPaused();
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        Context context = super.createConfigurationContext(overrideConfiguration);
        return localeHelper.onAttach(context);
    }

    @Override
    public Context getApplicationContext() {
        return localeDelegate.getApplicationContext(super.getApplicationContext());
    }

    public void updateLocale(Locale locale) {
        localeDelegate.setLocale(this, locale);
    }

    public void setLocaleFollowSystem() {
        Locale systemLocale = localeHelper.getSystemLocale();
        Locale currentLocale = localeHelper.getLocale(this);

        if (currentLocale != systemLocale) {
            localeDelegate.setLocale(this, systemLocale);
        }
        localeDelegate.clearLocaleSelection(this);
    }

    public <T extends View> T $(int resId) {
        return findViewById(resId);
    }

    protected void checkPermission(String... permissions) {
        String[] requestPermissions = getRequestPermissions(permissions);
        if (requestPermissions.length > 0) {
            requestPermissions(requestPermissions, PERMISSION_REQUEST_CODE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String[] getRequestPermissions(String[] permissions) {
        List<String> list = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) == PERMISSION_DENIED) {
                list.add(permission);
            }
        }
        return list.toArray(new String[0]);
    }

    public void setToolbarAsBack(String title) {
        setToolbarAsBack(this, R.id.toolbar, title);
    }

    public static void setToolbarAsBack(final AppCompatActivity activity, int id, String title) {
        Toolbar toolbar = activity.findViewById(id);
        toolbar.setTitle(title);
        activity.setSupportActionBar(toolbar);
        if (getSupportActionBar(activity) != null) {
            toolbar.setNavigationOnClickListener(v -> activity.finish());
            getSupportActionBar(activity).setDisplayHomeAsUpEnabled(true);
        }
    }

    private static ActionBar getSupportActionBar(AppCompatActivity activity) {
        return activity.getSupportActionBar();
    }

    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mShouldApplyDayNightModeForOptionsMenu && Pref.isNightModeEnabled()) {
            for (int i = 0; i < menu.size(); i++) {
                Drawable drawable = menu.getItem(i).getIcon();
                if (drawable != null) {
                    drawable.mutate();
                    drawable.setColorFilter(ContextCompat.getColor(this, R.color.toolbar), PorterDuff.Mode.SRC_ATOP);
                }
            }
            mShouldApplyDayNightModeForOptionsMenu = false;
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }
}
