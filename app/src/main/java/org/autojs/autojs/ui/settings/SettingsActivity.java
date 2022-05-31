package org.autojs.autojs.ui.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.GlobalAppContext;
import com.stardust.theme.app.ColorSelectActivity;
import com.stardust.theme.preference.ThemeColorPreferenceFragment;
import com.stardust.theme.util.ListBuilder;
import com.stardust.util.MapBuilder;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.autojs.autojs.Pref;
import org.autojs.autojs.tool.UpdateUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.common.NotAskAgainDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by Stardust on 2017/2/2.
 */
@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_settings)
public class SettingsActivity extends BaseActivity {

    private static final List<Pair<Integer, Integer>> COLOR_ITEMS = new ListBuilder<Pair<Integer, Integer>>()
            .add(new Pair<>(R.color.theme_color_red, R.string.theme_color_red))
            .add(new Pair<>(R.color.theme_color_pink, R.string.theme_color_pink))
            .add(new Pair<>(R.color.theme_color_purple, R.string.theme_color_purple))
            .add(new Pair<>(R.color.theme_color_dark_purple, R.string.theme_color_dark_purple))
            .add(new Pair<>(R.color.theme_color_indigo, R.string.theme_color_indigo))
            .add(new Pair<>(R.color.theme_color_blue, R.string.theme_color_blue))
            .add(new Pair<>(R.color.theme_color_light_blue, R.string.theme_color_light_blue))
            .add(new Pair<>(R.color.theme_color_blue_green, R.string.theme_color_blue_green))
            .add(new Pair<>(R.color.theme_color_cyan, R.string.theme_color_cyan))
            .add(new Pair<>(R.color.theme_color_green, R.string.theme_color_green))
            .add(new Pair<>(R.color.theme_color_light_green, R.string.theme_color_light_green))
            .add(new Pair<>(R.color.theme_color_yellow_green, R.string.theme_color_yellow_green))
            .add(new Pair<>(R.color.theme_color_yellow, R.string.theme_color_yellow))
            .add(new Pair<>(R.color.theme_color_amber, R.string.theme_color_amber))
            .add(new Pair<>(R.color.theme_color_orange, R.string.theme_color_orange))
            .add(new Pair<>(R.color.theme_color_dark_orange, R.string.theme_color_dark_orange))
            .add(new Pair<>(R.color.theme_color_brown, R.string.theme_color_brown))
            .add(new Pair<>(R.color.theme_color_gray, R.string.theme_color_gray))
            .add(new Pair<>(R.color.theme_color_blue_gray, R.string.theme_color_blue_gray))
            .add(new Pair<>(R.color.theme_color_default, R.string.theme_color_default))
            .list();

    @AfterViews
    void setUpUI() {
        setUpToolbar();
        getFragmentManager().beginTransaction().replace(R.id.fragment_setting, new PreferenceFragment()).commit();
    }

    private void setUpToolbar() {
        Toolbar toolbar = $(R.id.toolbar);
        toolbar.setTitle(R.string.text_setting);
        setSupportActionBar(toolbar);
        setDisplayHomeAsUpEnabledIfNeeded();
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setDisplayHomeAsUpEnabledIfNeeded() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class PreferenceFragment extends ThemeColorPreferenceFragment {
        private Map<String, Runnable> ACTION_MAP;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

        @Override
        public void onStart() {
            super.onStart();
            Activity mActivity = getActivity();
            ACTION_MAP = new MapBuilder<String, Runnable>()
                    .put(getString(R.string.text_theme_color), () -> selectThemeColor(mActivity))
                    .put(getString(R.string.text_about_app_and_developer), () -> launchAboutAppAndDeveloper(mActivity))
                    .put(getString(R.string.text_app_language), () -> selectAppLanguage(mActivity))
                    .put(getString(R.string.text_check_for_updates), () -> checkForUpdates(mActivity))
                    .put(getString(R.string.text_manage_ignored_updates), () -> manageIgnoredUpdates(mActivity))
                    .build();
        }

        private void manageIgnoredUpdates(Activity mActivity) {
            // TODO by SuperMonster003 on May 31, 2022.
            //  ! Updates ignorance.

            new MaterialDialog.Builder(mActivity)
                    .title(R.string.text_prompt)
                    .content(R.string.text_under_development_content)
                    .positiveText(R.string.dialog_button_back)
                    .build()
                    .show();
        }

        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            CharSequence title = preference.getTitle();
            if (title != null) {
                Runnable action = ACTION_MAP.get(title.toString());
                if (action != null) {
                    action.run();
                    return true;
                }
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

    }

    public static void checkForUpdates(Context context) {
        UpdateUtils.getDialogChecker(context).checkNow();
    }

    public static void selectThemeColor(Context context) {
        List<ColorSelectActivity.ColorItem> colorItems = new ArrayList<>(COLOR_ITEMS.size());
        for (Pair<Integer, Integer> item : COLOR_ITEMS) {
            colorItems.add(new ColorSelectActivity.ColorItem(context.getString(item.second), ContextCompat.getColor(context, item.first)));
        }
        ColorSelectActivity.startColorSelect(context, context.getString(R.string.mt_color_picker_title), colorItems);
    }

    public static void selectAppLanguage(Context context) {
        LinkedHashMap<String, Runnable> languagesMap = getAvailableLanguages(context);
        Set<String> languagesKey = languagesMap.keySet();
        Collection<Runnable> languagesRunnable = languagesMap.values();

        new MaterialDialog.Builder(context)
                .title(R.string.text_app_language)
                .items(languagesKey)
                .itemsCallbackSingleChoice(Pref.getAppLanguageIndex(), (dialog, itemView, position, text) -> true)
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> {
                    int index = dialog.getSelectedIndex();
                    Runnable run = (Runnable) languagesRunnable.toArray()[index];
                    if (run != null) {
                        Pref.setAppLanguageIndex(index);
                        GlobalAppContext.post(run);
                    }
                    dialog.dismiss();
                })
                .negativeText(R.string.text_cancel)
                .onNegative((dialog, which) -> dialog.dismiss())
                .neutralText(R.string.dialog_button_go_to_settings)
                .onNeutral((dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.android.settings", "com.android.settings.LanguageSettings");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                })
                .autoDismiss(false)
                .show();

        showImperfectHint(context);
    }

    public static void launchAboutAppAndDeveloper(@NonNull Context context) {
        context.startActivity(new Intent(context, AboutActivity_.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @NonNull
    private static LinkedHashMap<String, Runnable> getAvailableLanguages(@NonNull Context context) {
        LinkedHashMap<String, Runnable> map = new LinkedHashMap<>();
        map.put(context.getString(R.string.text_app_language_follow_system),
                ((BaseActivity) context)::setLocaleFollowSystem);
        map.put(context.getString(R.string.text_app_language_simplified_chinese),
                () -> ((BaseActivity) context).updateLocale(Locale.SIMPLIFIED_CHINESE));
        map.put(context.getString(R.string.text_app_language_english),
                () -> ((BaseActivity) context).updateLocale(Locale.ENGLISH));
        return map;
    }

    private static void showImperfectHint(Context context) {
        new NotAskAgainDialog.Builder(context, "SettingsActivity.select_app_language_imperfect_hint")
                .title(R.string.text_notice)
                .content(R.string.text_imperfect_hint_for_app_language)
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> dialog.dismiss())
                .autoDismiss(false)
                .show();
    }

}
