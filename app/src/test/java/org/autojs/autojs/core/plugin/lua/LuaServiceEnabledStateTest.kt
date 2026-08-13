package org.autojs.autojs.core.plugin.lua

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaServiceEnabledStateTest {

    @Test
    fun defaultStateUsesTheManifestValue() {
        assertTrue(
            isLuaServiceEffectivelyEnabled(
                manifestEnabled = true,
                componentEnabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            ),
        )
        assertFalse(
            isLuaServiceEffectivelyEnabled(
                manifestEnabled = false,
                componentEnabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            ),
        )
    }

    @Test
    fun explicitEnabledStateOverridesADisabledManifestComponent() {
        assertTrue(
            isLuaServiceEffectivelyEnabled(
                manifestEnabled = false,
                componentEnabledSetting = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            ),
        )
    }

    @Test
    fun everyDisabledStateOverridesAnEnabledManifestComponent() {
        listOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        ).forEach { state ->
            assertFalse(
                isLuaServiceEffectivelyEnabled(
                    manifestEnabled = true,
                    componentEnabledSetting = state,
                ),
            )
        }
    }

    @Test
    fun unknownStateFailsClosed() {
        assertFalse(
            isLuaServiceEffectivelyEnabled(
                manifestEnabled = true,
                componentEnabledSetting = Int.MAX_VALUE,
            ),
        )
    }
}
