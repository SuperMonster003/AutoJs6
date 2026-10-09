package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLauncherIconPolicyTest {
    @Test fun hidingDisablesOnlyTheSuppliedLauncherComponentsAndShowingRestoresTheirExactStates() {
        val original = linkedMapOf("Auto" to 2, "Light" to 1, "Dark" to 0, "Transparent" to 0)
        val hidden = PluginLauncherIconPolicy.plan(false, original, null, true)
        assertEquals(original.keys, hidden.keys)
        assertTrue(hidden.values.all { it == 2 })
        assertEquals(original, PluginLauncherIconPolicy.plan(true, hidden, original, false))
    }

    @Test fun allowingNewIconsKeepsTheSingleSelectedVariant() {
        val current = mapOf("Auto" to 0, "Light" to 2, "Dark" to 2)
        assertEquals(current, PluginLauncherIconPolicy.plan(true, current, null, true))
        assertEquals(mapOf("Auto" to 0, "Light" to 0),
            PluginLauncherIconPolicy.plan(true, mapOf("Auto" to 2, "Light" to 2), null, false))
    }

    @Test fun updatedLauncherAliasesUseTheirManifestDefaultsInsteadOfEnablingEveryVariant() {
        val restored = PluginLauncherIconPolicy.plan(true, mapOf("Auto" to 2, "NewStyle" to 2), mapOf("Auto" to 1), false)
        assertEquals(mapOf("Auto" to 1, "NewStyle" to 0), restored)
        assertEquals(emptyMap<String, Int>(), PluginLauncherIconPolicy.plan(false, emptyMap(), null, false))
    }

    @Test fun aBatchNeverChangesExistingPluginsOrFailedInstallations() {
        for (atStart in listOf(false, true)) for (beforeAttempt in listOf(false, true)) for (success in listOf(false, true)) {
            assertEquals(success && !atStart && !beforeAttempt,
                PluginLauncherIconPolicy.appliesToNewInstall(atStart, beforeAttempt, success))
        }
        assertFalse(PluginLauncherIconPolicy.appliesToNewInstall(false, true, true))
    }

    @Test fun shellArgumentsKeepSpacesQuotesAndDollarSignsLiteral() {
        assertEquals("'a b'", PluginLauncherIconPolicy.shellQuote("a b"))
        assertEquals("'a'\\''b'", PluginLauncherIconPolicy.shellQuote("a'b"))
        assertEquals("'a\$b'", PluginLauncherIconPolicy.shellQuote("a\$b"))
    }
}
