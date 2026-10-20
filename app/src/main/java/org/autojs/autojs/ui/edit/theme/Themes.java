package org.autojs.autojs.ui.edit.theme;

import android.content.Context;

import androidx.annotation.Nullable;

import org.autojs.autojs.core.pref.keys.EditorKeys;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by Stardust on Feb 22, 2018.
 */
public class Themes {

    private static final String ASSETS_THEMES_PATH = "editor/theme";
    private static final String DEFAULT_THEME = "Quiet Light";
    private static final String DARK_THEME = "Dark (Visual Studio)";

    private static List<Theme> sThemes;
    private static Theme sDefaultTheme;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static Observable<List<Theme>> getAllThemes(Context context) {
        if (sThemes != null) {
            return Observable.just(sThemes);
        }
        PublishSubject<List<Theme>> subject = PublishSubject.create();
        getAllThemesInner(context)
                .subscribeOn(Schedulers.io())
                .subscribe(themes -> {
                    setThemes(themes);
                    subject.onNext(sThemes);
                    subject.onComplete();
                }, Throwable::printStackTrace);
        return subject;
    }

    public static Observable<List<Theme>> getAllThemes(Context context, EditorEngineType engineType) {
        if (engineType == EditorEngineType.ACE) {
            return Observable.just(AceEditorThemes.getAll());
        }
        return getAllThemes(context);
    }

    public static Observable<Theme> getDefault(Context context) {
        if (sDefaultTheme != null)
            return Observable.just(sDefaultTheme);
        return getAllThemes(context)
                .map(themes -> sDefaultTheme);
    }

    public static Observable<Theme> getDefault(Context context, EditorEngineType engineType) {
        if (engineType == EditorEngineType.ACE) {
            boolean darkDefault = !isSeparateLightDarkMode() || ViewUtils.isNightModeYes(context);
            return Observable.just(AceEditorThemes.getDefault(darkDefault));
        }
        return getDefault(context);
    }

    private synchronized static void setThemes(List<Theme> themes) {
        if (sThemes != null)
            return;
        sThemes = Collections.unmodifiableList(themes);
        for (Theme theme : sThemes) {
            if (DEFAULT_THEME.equals(theme.getName())) {
                sDefaultTheme = theme;
                return;
            }
        }
        sDefaultTheme = sThemes.get(0);
    }

    private static Observable<List<Theme>> getAllThemesInner(Context context) {
        if (sThemes != null) {
            return Observable.just(sThemes);
        }
        try {
            return Observable.fromArray(context.getAssets().list(ASSETS_THEMES_PATH))
                    .map(file -> context.getAssets().open(ASSETS_THEMES_PATH + File.separator + file))
                    .map(stream -> Theme.fromJson(new InputStreamReader(stream)))
                    .collectInto((List<Theme>) new ArrayList<Theme>(), List::add)
                    .toObservable();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Observable<Theme> getCurrent(Context context) {
        return getCurrent(context, EditorEngineType.NATIVE);
    }

    public static Observable<Theme> getCurrent(Context context, EditorEngineType engineType) {
        String currentTheme = getCurrentThemeName(context, engineType);
        if (engineType == EditorEngineType.ACE) {
            Theme aceTheme = AceEditorThemes.findByName(currentTheme);
            if (aceTheme != null) {
                return Observable.just(aceTheme);
            }
            return getDefault(context, EditorEngineType.ACE);
        }
        if (currentTheme == null) {
            return getDefault(context);
        }
        return getAllThemes(context).map(themes -> {
            String nativeThemeName = resolveNativeThemeName(context, currentTheme);
            for (Theme theme : themes) {
                if (nativeThemeName.equals(theme.getName())) {
                    return theme;
                }
            }
            return themes.get(0);
        });
    }

    /**
     * Returns the current theme without scheduling asset IO when it is already available.
     *
     * <p>Ace themes are an in-memory catalog, so their saved selection can always be resolved
     * synchronously. Applying that theme before the plugin view is attached keeps the WebView's
     * first surface and the host editor chrome on the same palette. Native themes remain
     * asynchronous until their asset catalog has been loaded.</p>
     */
    @Nullable
    public static Theme getCurrentImmediatelyAvailable(Context context, EditorEngineType engineType) {
        String currentTheme = getCurrentThemeName(context, engineType);
        if (engineType == EditorEngineType.ACE) {
            Theme aceTheme = AceEditorThemes.findByName(currentTheme);
            if (aceTheme != null) {
                return aceTheme;
            }
            boolean darkDefault = !isSeparateLightDarkMode() || ViewUtils.isNightModeYes(context);
            return AceEditorThemes.getDefault(darkDefault);
        }
        if (sThemes == null) {
            return null;
        }
        String nativeThemeName = resolveNativeThemeName(context, currentTheme);
        for (Theme theme : sThemes) {
            if (nativeThemeName.equals(theme.getName())) {
                return theme;
            }
        }
        return sThemes.get(0);
    }

    public static void setCurrent(String name) {
        setCurrent(GlobalAppContext.get(), name);
    }

    public static void setCurrent(Context context, String name) {
        if (isSeparateLightDarkMode()) {
            if (ViewUtils.isNightModeYes(context)) {
                EditorKeys.EDITOR_THEME_DARK.set(name);
            } else {
                EditorKeys.EDITOR_THEME_LIGHT.set(name);
            }
        } else {
            EditorKeys.EDITOR_THEME.set(name);
        }
    }

    public static String getCurrentThemeName(Context context) {
        return getCurrentThemeName(context, EditorEngineType.NATIVE);
    }

    public static String getCurrentThemeName(Context context, EditorEngineType engineType) {
        if (engineType == EditorEngineType.ACE) {
            String currentTheme = getCurrentThemeNameRawWithoutDefault(context);
            Theme theme = AceEditorThemes.findByName(currentTheme);
            if (theme != null) {
                return theme.getName();
            }
            return getDefaultThemeName(context, engineType);
        }
        String currentTheme = getCurrentThemeNameRaw(context);
        return resolveNativeThemeName(context, currentTheme);
    }

    public static String getDefaultThemeName(Context context, EditorEngineType engineType) {
        if (engineType == EditorEngineType.ACE) {
            boolean darkDefault = !isSeparateLightDarkMode() || ViewUtils.isNightModeYes(context);
            return AceEditorThemes.getDefault(darkDefault).getName();
        }
        if (isSeparateLightDarkMode() && ViewUtils.isNightModeYes(context)) {
            return DARK_THEME;
        }
        return DEFAULT_THEME;
    }

    private static String getCurrentThemeNameRaw(Context context) {
        if (!isSeparateLightDarkMode()) {
            String activeSeparateTheme = ViewUtils.isNightModeYes(context)
                    ? EditorKeys.EDITOR_THEME_DARK.storedOrNull()
                    : EditorKeys.EDITOR_THEME_LIGHT.storedOrNull();
            return firstNonBlank(
                    EditorKeys.EDITOR_THEME.storedOrNull(),
                    activeSeparateTheme,
                    EditorKeys.EDITOR_THEME_LIGHT.storedOrNull(),
                    EditorKeys.EDITOR_THEME_DARK.storedOrNull(),
                    DEFAULT_THEME
            );
        }
        if (ViewUtils.isNightModeYes(context)) {
            return firstNonBlank(EditorKeys.EDITOR_THEME_DARK.storedOrNull(), DARK_THEME);
        }
        return firstNonBlank(
                EditorKeys.EDITOR_THEME_LIGHT.storedOrNull(),
                EditorKeys.EDITOR_THEME.storedOrNull(),
                DEFAULT_THEME
        );
    }

    private static String getCurrentThemeNameRawWithoutDefault(Context context) {
        if (!isSeparateLightDarkMode()) {
            return firstNonBlank(EditorKeys.EDITOR_THEME.storedOrNull());
        }
        if (ViewUtils.isNightModeYes(context)) {
            return firstNonBlank(EditorKeys.EDITOR_THEME_DARK.storedOrNull());
        }
        return firstNonBlank(EditorKeys.EDITOR_THEME_LIGHT.storedOrNull());
    }

    private static String resolveNativeThemeName(Context context, String name) {
        if (DARK_THEME.equals(name)) {
            return DARK_THEME;
        }
        if (DEFAULT_THEME.equals(name)) {
            return DEFAULT_THEME;
        }
        return ViewUtils.isNightModeYes(context) && isSeparateLightDarkMode()
                ? DARK_THEME
                : DEFAULT_THEME;
    }

    public static boolean isSeparateLightDarkMode() {
        String mode = EditorKeys.EDITOR_THEME_MODE.storedKeyOrDefault();
        return !org.autojs.autojs.util.StringUtils.key(R.string.key_editor_theme_mode_unified).equals(mode);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
