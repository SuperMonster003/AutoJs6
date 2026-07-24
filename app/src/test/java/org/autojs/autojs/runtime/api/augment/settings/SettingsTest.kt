package org.autojs.autojs.runtime.api.augment.settings

import org.autojs.autojs.runtime.api.augment.settings.Settings.Companion.Setting
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `friendly legacy and current preference names map to the supported settings`() {
        assertAliases(Setting.STABLE_MODE, "stable_mode", "key_stable_mode")
        assertAliases(
            Setting.ROOT_ACCESSIBILITY_SERVICE,
            "enable_accessibility_service_by_root",
            "key_enable_accessibility_service_by_root",
            "key_enable_a11y_service_with_root_access",
        )
        assertAliases(
            Setting.STOP_ALL_ON_VOLUME_UP,
            "stop_all_on_volume_up",
            "key_use_volume_control_running",
        )
        assertAliases(
            Setting.NOT_SHOW_CONSOLE,
            "not_show_console",
            "key_dont_show_main_activity",
            "key_not_showing_main_activity",
        )
        assertAliases(
            Setting.FOREGROUND_SERVICE,
            "foreground_service",
            "key_foreground_service",
        )
    }

    @Test
    fun `unmapped preference names pass through like the Pro module`() {
        assertEquals(
            "arbitrary_preference",
            Settings.resolvePreferenceKey("arbitrary_preference") { "resolved_$it" },
        )
    }

    @Test
    fun `mapped preference names resolve to the current resource key`() {
        assertEquals(
            "resolved_${Setting.STABLE_MODE.keyRes}",
            Settings.resolvePreferenceKey("stable_mode") { "resolved_$it" },
        )
    }

    private fun assertAliases(expected: Setting, vararg aliases: String) {
        aliases.forEach { alias ->
            assertEquals(expected, Settings.resolveSetting(alias))
        }
    }
}
