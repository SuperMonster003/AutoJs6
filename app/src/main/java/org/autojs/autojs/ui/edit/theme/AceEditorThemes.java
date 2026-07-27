package org.autojs.autojs.ui.edit.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AceEditorThemes {

    private static final String DEFAULT_LIGHT_THEME = "Katzenmilch";
    private static final String DEFAULT_DARK_THEME = "Gruvbox";

    private static final Spec[] SPECS = {
            new Spec("Ambiance", "ace/theme/ambiance", true, "#202020", "#E6E1DC"),
            new Spec("Chaos", "ace/theme/chaos", true, "#161616", "#E6E1DC"),
            new Spec("Chrome", "ace/theme/chrome", false, "#FFFFFF", "#000000"),
            new Spec("Cloud Editor", "ace/theme/cloud_editor", false, "#FFFFFF", "#3A3A42"),
            new Spec("Cloud Editor Dark", "ace/theme/cloud_editor_dark", true, "#282C34", "#DCDFE4"),
            new Spec("Cloud9 Day", "ace/theme/cloud9_day", false, "#FBFBFB", "#000000"),
            new Spec("Cloud9 Night", "ace/theme/cloud9_night", true, "#181818", "#EBEBEB"),
            new Spec("Cloud9 Night Low Color", "ace/theme/cloud9_night_low_color", true, "#181818", "#EBEBEB"),
            new Spec("Clouds", "ace/theme/clouds", false, "#FFFFFF", "#000000"),
            new Spec("Clouds Midnight", "ace/theme/clouds_midnight", true, "#191919", "#929292"),
            new Spec("Cobalt", "ace/theme/cobalt", true, "#002240", "#FFFFFF"),
            new Spec("Crimson Editor", "ace/theme/crimson_editor", false, "#FFFFFF", "#404040"),
            new Spec("Dawn", "ace/theme/dawn", false, "#F9F9F9", "#080808"),
            new Spec("Dracula", "ace/theme/dracula", true, "#282A36", "#F8F8F2"),
            new Spec("Dreamweaver", "ace/theme/dreamweaver", false, "#FFFFFF", "#000000"),
            new Spec("Eclipse", "ace/theme/eclipse", false, "#FFFFFF", "#000000"),
            new Spec("GitHub", "ace/theme/github", false, "#FFFFFF", "#000000"),
            new Spec("GitHub Dark", "ace/theme/github_dark", true, "#24292E", "#FFFFFF"),
            new Spec("GitHub Light Default", "ace/theme/github_light_default", false, "#FFFFFF", "#24292E"),
            new Spec("Gob", "ace/theme/gob", true, "#0B0B0B", "#00FF00"),
            new Spec("Gruvbox", "ace/theme/gruvbox", true, "#1E1E1E", "#EBDAB4"),
            new Spec("Gruvbox Dark Hard", "ace/theme/gruvbox_dark_hard", true, "#1D2021", "#EBDBB2"),
            new Spec("Gruvbox Light Hard", "ace/theme/gruvbox_light_hard", false, "#F9F5D7", "#3C3836"),
            new Spec("Idle Fingers", "ace/theme/idle_fingers", true, "#323232", "#FFFFFF"),
            new Spec("Iplastic", "ace/theme/iplastic", false, "#EEEEEE", "#333333"),
            new Spec("Katzenmilch", "ace/theme/katzenmilch", false, "#F3F2F3", "#0F0009"),
            new Spec("KR Theme", "ace/theme/kr_theme", true, "#0B0A09", "#FCFFE0"),
            new Spec("Kuroir", "ace/theme/kuroir", false, "#E8E9E8", "#363636"),
            new Spec("Merbivore", "ace/theme/merbivore", true, "#161616", "#E6E1DC"),
            new Spec("Merbivore Soft", "ace/theme/merbivore_soft", true, "#1C1C1C", "#E6E1DC"),
            new Spec("Mono Industrial", "ace/theme/mono_industrial", true, "#222C28", "#FFFFFF"),
            new Spec("Monokai", "ace/theme/monokai", true, "#272822", "#F8F8F2"),
            new Spec("Nord Dark", "ace/theme/nord_dark", true, "#2E3440", "#D8DEE9"),
            new Spec("One Dark", "ace/theme/one_dark", true, "#282C34", "#ABB2BF"),
            new Spec("Pastel On Dark", "ace/theme/pastel_on_dark", true, "#2C2828", "#8F938F"),
            new Spec("Solarized Dark", "ace/theme/solarized_dark", true, "#002B36", "#839496"),
            new Spec("Solarized Light", "ace/theme/solarized_light", false, "#FDF6E3", "#586E75"),
            new Spec("SQL Server", "ace/theme/sqlserver", false, "#FFFFFF", "#000000"),
            new Spec("Terminal", "ace/theme/terminal", true, "#000000", "#DEDEDE"),
            new Spec("TextMate", "ace/theme/textmate", false, "#FFFFFF", "#000000"),
            new Spec("Tomorrow", "ace/theme/tomorrow", false, "#FFFFFF", "#4D4D4C"),
            new Spec("Tomorrow Night", "ace/theme/tomorrow_night", true, "#1D1F21", "#C5C8C6"),
            new Spec("Tomorrow Night Blue", "ace/theme/tomorrow_night_blue", true, "#002451", "#FFFFFF"),
            new Spec("Tomorrow Night Bright", "ace/theme/tomorrow_night_bright", true, "#000000", "#DEDEDE"),
            new Spec("Tomorrow Night Eighties", "ace/theme/tomorrow_night_eighties", true, "#2D2D2D", "#CCCCCC"),
            new Spec("Twilight", "ace/theme/twilight", true, "#141414", "#F8F8F8"),
            new Spec("Vibrant Ink", "ace/theme/vibrant_ink", true, "#0F0F0F", "#FFFFFF"),
            new Spec("Xcode", "ace/theme/xcode", false, "#FFFFFF", "#000000"),
    };

    private static List<Theme> sThemes;

    private AceEditorThemes() {
        throw new AssertionError();
    }

    public synchronized static List<Theme> getAll() {
        if (sThemes == null) {
            List<Theme> themes = new ArrayList<>(SPECS.length);
            for (Spec spec : SPECS) {
                themes.add(Theme.fromAceTheme(
                        spec.name,
                        spec.aceTheme,
                        spec.dark,
                        spec.backgroundColor,
                        spec.foregroundColor
                ));
            }
            sThemes = Collections.unmodifiableList(themes);
        }
        return sThemes;
    }

    public static Theme getDefault(boolean dark) {
        Theme theme = findByName(dark ? DEFAULT_DARK_THEME : DEFAULT_LIGHT_THEME);
        return theme != null ? theme : getAll().get(0);
    }

    public static Theme findByName(String name) {
        if (name == null) {
            return null;
        }
        String normalizedName = normalizeAlias(name);
        for (Theme theme : getAll()) {
            if (theme.getName().equals(normalizedName)) {
                return theme;
            }
        }
        return null;
    }

    public static String resolveName(String name, boolean dark) {
        Theme theme = findByName(name);
        if (theme != null) {
            return theme.getName();
        }
        return getDefault(dark).getName();
    }

    private static String normalizeAlias(String name) {
        if ("Quiet Light".equals(name)) {
            return DEFAULT_LIGHT_THEME;
        }
        if ("Visual Studio".equals(name)) {
            return DEFAULT_LIGHT_THEME;
        }
        if ("Dark (Visual Studio)".equals(name)) {
            return DEFAULT_DARK_THEME;
        }
        if ("Visual Studio Dark".equals(name)) {
            return DEFAULT_DARK_THEME;
        }
        return name;
    }

    private static final class Spec {

        final String name;
        final String aceTheme;
        final boolean dark;
        final String backgroundColor;
        final String foregroundColor;

        Spec(String name, String aceTheme, boolean dark, String backgroundColor, String foregroundColor) {
            this.name = name;
            this.aceTheme = aceTheme;
            this.dark = dark;
            this.backgroundColor = backgroundColor;
            this.foregroundColor = foregroundColor;
        }
    }
}
