package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.APPEARANCE
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingEffect.APPLY
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Settings > Appearance (docs/settings/ROADMAP.md P2.1, appendix A.1 `appearance`): language, night mode with its two
 * mirror switches, the three theme colors and keeping the screen on in the foreground. Language, night mode and theme
 * colors take effect through their existing entries (`APPLY`, T12).
 *
 * zh-CN: 设置 > 外观 (ROADMAP P2.1, 附录 A.1 `appearance`): 语言, 夜间模式及其两个镜像开关, 主题色三项, 前台时保持屏幕常亮.
 * 语言, 夜间模式与主题色经现有入口生效 (`APPLY`, T12).
 */
object AppearanceKeys : SettingsDomain {

    private val APP_LANGUAGE_VALUES = intArrayOf(
        R.string.key_app_language_auto,
        R.string.key_app_language_zh_hans,
        R.string.key_app_language_zh_hant_hk,
        R.string.key_app_language_zh_hant_tw,
        R.string.key_app_language_en,
        R.string.key_app_language_fr,
        R.string.key_app_language_es,
        R.string.key_app_language_ja,
        R.string.key_app_language_ko,
        R.string.key_app_language_ru,
        R.string.key_app_language_ar,
    )

    private val NIGHT_MODE_VALUES = intArrayOf(
        R.string.key_night_mode_follow_system,
        R.string.key_night_mode_always_on,
        R.string.key_night_mode_always_off,
        R.string.key_night_mode_null,
    )

    private val KEEP_SCREEN_ON_VALUES = intArrayOf(
        R.string.key_keep_screen_on_when_in_foreground_disabled,
        R.string.key_keep_screen_on_when_in_foreground_all_pages,
        R.string.key_keep_screen_on_when_in_foreground_homepage_only,
    )

    /** `Language` reads the stored key and treats a missing or unknown one as automatic (`Language.getPrefLanguage`). */
    @JvmField
    val APP_LANGUAGE = SettingKey.Enum(
        R.string.key_app_language, APPEARANCE, keyChoices(*APP_LANGUAGE_VALUES),
        keyResource(R.string.default_key_app_language, "default_key_app_language"), APPLY,
    )

    /** The night mode list (follow system / always on / always off); the switches below mirror it. */
    @JvmField
    val NIGHT_MODE = SettingKey.Enum(
        R.string.key_night_mode, APPEARANCE, keyChoices(*NIGHT_MODE_VALUES),
        keyResource(R.string.default_key_night_mode, "default_key_night_mode"), APPLY,
    )

    /** Night mode on or off (`ViewUtils.isNightModeEnabled`); `Pref` writes the current system state when it is missing. */
    @JvmField
    val NIGHT_MODE_ENABLED = SettingKey.Bool(R.string.key_night_mode_enabled, APPEARANCE, SettingDefault.of(false), APPLY)

    /** Night mode follows the system (`ViewUtils.isAutoNightModeEnabled`); `Pref` writes the default when it is missing. */
    @JvmField
    val AUTO_NIGHT_MODE_ENABLED = SettingKey.Bool(
        R.string.key_auto_night_mode_enabled, APPEARANCE,
        SettingDefault.computed("AutoNightMode.isFunctional()") { ViewUtils.AutoNightMode.isFunctional() }, APPLY,
    )

    @JvmField
    val THEME_COLOR_PRIMARY = SettingKey.Int(
        R.string.key_theme_color_primary, APPEARANCE,
        SettingDefault.computed("ThemeColorManager.defaultThemeColor.colorPrimary") { ThemeColorManager.defaultThemeColor.colorPrimary }, APPLY,
    )

    @JvmField
    val THEME_COLOR_PRIMARY_DARK = SettingKey.Int(
        R.string.key_theme_color_primary_dark, APPEARANCE,
        SettingDefault.computed("ThemeColorManager.defaultThemeColor.colorPrimaryDark") { ThemeColorManager.defaultThemeColor.colorPrimaryDark }, APPLY,
    )

    @JvmField
    val THEME_COLOR_ACCENT = SettingKey.Int(
        R.string.key_theme_color_accent, APPEARANCE,
        SettingDefault.computed("ThemeColorManager.defaultThemeColor.colorAccent") { ThemeColorManager.defaultThemeColor.colorAccent }, APPLY,
    )

    @JvmField
    val KEEP_SCREEN_ON_WHEN_IN_FOREGROUND = SettingKey.Enum(
        R.string.key_keep_screen_on_when_in_foreground, APPEARANCE, keyChoices(*KEEP_SCREEN_ON_VALUES),
        keyResource(R.string.default_key_keep_screen_on_when_in_foreground, "default_key_keep_screen_on_when_in_foreground"),
    )

    /** Whether the screen is kept on (`ViewUtils.isKeepScreenOnWhenInForegroundEnabled`), written with the list above. */
    @JvmField
    val KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_ENABLED = SettingKey.Bool(R.string.key_keep_screen_on_when_in_foreground_enabled, APPEARANCE, SettingDefault.of(false))

    override val keys: List<PrefKey<*>> = listOf(
        APP_LANGUAGE, NIGHT_MODE, NIGHT_MODE_ENABLED, AUTO_NIGHT_MODE_ENABLED,
        THEME_COLOR_PRIMARY, THEME_COLOR_PRIMARY_DARK, THEME_COLOR_ACCENT,
        KEEP_SCREEN_ON_WHEN_IN_FOREGROUND, KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_ENABLED,
    )

    override val names: List<RegisteredName> = valueConstants(*APP_LANGUAGE_VALUES, *NIGHT_MODE_VALUES, *KEEP_SCREEN_ON_VALUES) +
            // The theme color page entry holds no value; the colors are the three keys above.
            excludedNames(R.string.key_theme_color)

    /**
     * The stored keep-screen-on key as it is, else the default key (`Pref.keyKeepScreenOnWhenInForeground`).
     * zh-CN: 原样返回存储的前台常亮键, 不存在时为默认键 (`Pref.keyKeepScreenOnWhenInForeground`).
     */
    @JvmStatic
    val keepScreenOnWhenInForegroundKey: String
        get() = KEEP_SCREEN_ON_WHEN_IN_FOREGROUND.storedKeyOrDefault()
}
