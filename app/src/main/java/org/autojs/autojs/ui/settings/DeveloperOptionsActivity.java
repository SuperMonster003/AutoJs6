package org.autojs.autojs.ui.settings;

import static org.autojs.autojs.util.StringUtils.key;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.autojs.autojs.pref.Pref;
import org.autojs.autojs.tool.MapBuilder;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.util.Map;

/**
 * Created by SuperMonster003 on Jun 2, 2022.
 */
@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_developer_options)
public class DeveloperOptionsActivity extends BaseActivity {

    @AfterViews
    void setUpUI() {
        setUpToolbar();
        setDisplayHomeAsUpEnabledIfNeeded();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_developer_options, new PreferenceFragment())
                .disallowAddToBackStack()
                .commit();
    }

    private void setUpToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.text_developer_options);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setDisplayHomeAsUpEnabledIfNeeded() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class PreferenceFragment extends PreferenceFragmentCompat {

        private Activity mActivity;

        private final Map<String, Runnable> mActions = new MapBuilder<String, Runnable>()
                .put(key(R.string.key_updates_checked_states_cleared), () -> clearUpdatesCheckedStates(mActivity))
                .build();

        @Override
        public void onStart() {
            mActivity = getActivity();
            super.onStart();
        }

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            addPreferencesFromResource(R.xml.developer_options_preferences);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();
            if (key != null) {
                Runnable action = mActions.get(key);
                if (action != null) {
                    action.run();
                    return true;
                }
            }
            return super.onPreferenceTreeClick(preference);
        }

    }

    public static void clearUpdatesCheckedStates(Context context) {
        Pref.clearUpdatesCheckedStates();
        ViewUtils.showToast(context, R.string.text_updates_checked_states_cleared);
    }

}
