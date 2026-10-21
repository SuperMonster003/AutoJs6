package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.SettingEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppearanceKeys] (docs/settings/ROADMAP.md P2.1): the readings that `Language`, `ThemeColor`, `ViewUtils`, the night
 * mode switches and the deprecated `Pref.keyKeepScreenOnWhenInForeground` forward to.
 *
 * zh-CN: [AppearanceKeys] (ROADMAP P2.1): `Language`, `ThemeColor`, `ViewUtils`, 夜间模式开关与已弃用的
 * `Pref.keyKeepScreenOnWhenInForeground` 所转发的读法.
 */
class AppearanceKeysTest : DomainKeysTestBase() {

    @Test
    fun keysAndEffects() {
        assertEquals(11, AppearanceKeys.keys.size)
        val applied = AppearanceKeys.keys.filterIsInstance<org.autojs.autojs.core.pref.SettingKey<*>>().filter { it.effect == SettingEffect.APPLY }.map { it.name }
        assertEquals(
            listOf("key_app_language", "key_night_mode", "key_night_mode_enabled", "key_auto_night_mode_enabled", "key_theme_color_primary", "key_theme_color_primary_dark", "key_theme_color_accent").map(::key),
            applied,
        )
    }

    @Test
    fun languageIsReadAsTheStoredKey() {
        // Language.getPrefLanguageOrNull compares the raw stored key: missing and unknown keys find no language.
        assertNull(AppearanceKeys.APP_LANGUAGE.storedKey())
        AppearanceKeys.APP_LANGUAGE.set(key("key_app_language_ja"))
        assertEquals(key("key_app_language_ja"), AppearanceKeys.APP_LANGUAGE.storedKey())
        store.put(AppearanceKeys.APP_LANGUAGE.name, "key_\$_app_language_removed")
        assertEquals("key_\$_app_language_removed", AppearanceKeys.APP_LANGUAGE.storedKey())
        assertNull(AppearanceKeys.APP_LANGUAGE.storedOrNull())
        // Language.setPrefLanguage(...) writes null when no language matches.
        AppearanceKeys.APP_LANGUAGE.set(null)
        assertFalse(AppearanceKeys.APP_LANGUAGE.isStored())
        assertEquals(key("key_app_language_auto"), AppearanceKeys.APP_LANGUAGE.get())
    }

    @Test
    fun nightModeAcceptsTheModeKeysOnly() {
        assertEquals(key("key_night_mode_follow_system"), AppearanceKeys.NIGHT_MODE.get())
        listOf("key_night_mode_always_off", "key_night_mode_always_on", "key_night_mode_follow_system", "key_night_mode_null").forEach {
            AppearanceKeys.NIGHT_MODE.set(key(it))
            assertEquals(key(it), AppearanceKeys.NIGHT_MODE.get())
        }
        assertThrows(IllegalArgumentException::class.java) { AppearanceKeys.NIGHT_MODE.set("key_\$_night_mode_unknown") }
    }

    @Test
    fun nightModeSwitchesDefaultToFalseAndReadLikeGetBoolean() {
        assertFalse(AppearanceKeys.NIGHT_MODE_ENABLED.get())
        assertFalse(AppearanceKeys.NIGHT_MODE_ENABLED.isStored())
        AppearanceKeys.NIGHT_MODE_ENABLED.set(true)
        assertTrue(AppearanceKeys.NIGHT_MODE_ENABLED.get())
        assertTrue(AppearanceKeys.NIGHT_MODE_ENABLED.isStored())
        AppearanceKeys.AUTO_NIGHT_MODE_ENABLED.set(false)
        assertEquals(false, AppearanceKeys.AUTO_NIGHT_MODE_ENABLED.storedOrNull())
        // A value of another type throws like SharedPreferences.getBoolean.
        store.put(AppearanceKeys.NIGHT_MODE_ENABLED.name, "true")
        assertThrows(ClassCastException::class.java) { AppearanceKeys.NIGHT_MODE_ENABLED.get() }
    }

    @Test
    fun themeColorsAreThreeIntKeys() {
        listOf(AppearanceKeys.THEME_COLOR_PRIMARY, AppearanceKeys.THEME_COLOR_PRIMARY_DARK, AppearanceKeys.THEME_COLOR_ACCENT).forEachIndexed { i, k ->
            assertFalse(k.isStored())
            k.set(-16738680 + i)
            assertEquals(-16738680 + i, k.get())
        }
        assertEquals(-16738680, store.getInt(key("key_theme_color_primary"), 0))
        assertEquals(-16738678, store.getInt(key("key_theme_color_accent"), 0))
    }

    @Test
    fun keepScreenOnKeyIsTheStoredStringOrTheDefault() {
        assertEquals(key("key_keep_screen_on_when_in_foreground_disabled"), AppearanceKeys.keepScreenOnWhenInForegroundKey)
        AppearanceKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND.set(key("key_keep_screen_on_when_in_foreground_all_pages"))
        assertEquals(key("key_keep_screen_on_when_in_foreground_all_pages"), AppearanceKeys.keepScreenOnWhenInForegroundKey)
        // The last enabled state is copied as it is (ViewUtils.setKeepScreenOnWhenInForegroundFromLastEnabledState).
        AppearanceKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND.setStoredKey("key_\$_keep_screen_on_unknown")
        assertEquals("key_\$_keep_screen_on_unknown", AppearanceKeys.keepScreenOnWhenInForegroundKey)
        assertEquals(key("key_keep_screen_on_when_in_foreground_disabled"), AppearanceKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND.get())
        assertFalse(AppearanceKeys.KEEP_SCREEN_ON_WHEN_IN_FOREGROUND_ENABLED.get())
    }
}
