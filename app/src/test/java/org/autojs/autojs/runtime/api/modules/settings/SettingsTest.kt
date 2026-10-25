package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.core.pref.InMemorySettingsStore
import org.autojs.autojs.core.pref.SettingsTestResources
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.runtime.api.modules.settings.Settings.Companion.Setting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * Appendix C Q10 (2) / (3) of docs/settings/ROADMAP.md: a switch that was never set reads the default of its
     * declaration, as the settings page shows it; names without a Boolean declaration stay false.
     */
    @Test
    fun `isEnabled falls back to the declared default for a missing key`() {
        val resources = SettingsTestResources.install()
        SettingsStores.install { InMemorySettingsStore(it.name) }
        try {
            fun enabled(alias: String) = Settings.isEnabledFor(Settings.resolvePreferenceKey(alias, SettingsTestResources::string))
            assertTrue(enabled("enable_accessibility_service_by_root"))
            assertTrue(enabled("key_enable_a11y_service_with_root_access"))
            assertTrue(enabled("stop_all_on_volume_up"))
            assertTrue(enabled("key_use_volume_control_running"))
            assertFalse(enabled("stable_mode"))
            assertFalse(enabled("foreground_service"))
            assertFalse(enabled("not_show_console"))
            assertFalse(enabled("arbitrary_preference"))
            // A stored value wins over the default.
            SettingsStores.default.put(SettingsTestResources.value("string", "key_use_volume_control_running"), false)
            assertFalse(enabled("stop_all_on_volume_up"))
            SettingsStores.default.put("arbitrary_preference", true)
            assertTrue(enabled("arbitrary_preference"))
        } finally {
            SettingsStores.reset()
            resources.close()
        }
    }

    private fun assertAliases(expected: Setting, vararg aliases: String) {
        aliases.forEach { alias ->
            assertEquals(expected, Settings.resolveSetting(alias))
        }
    }
}
