package org.autojs.autojs.runtime.api.modules.plugins

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPluginExtensionRegistryTest {

    @Test
    fun selectedModulesAreNormalizedFlattenedAndDeduplicated() {
        val registry = LegacyPluginExtensionRegistry()

        assertEquals(emptyList<LegacyPluginExtensionRegistry.Action>(), registry.select(emptyArray()))

        val selected = registry.select(arrayOf(arrayOf("array", "Arrayx"), listOf("number")))

        assertEquals(
            listOf(
                LegacyPluginExtensionRegistry.Action.Preset("Arrayx"),
                LegacyPluginExtensionRegistry.Action.Preset("Numberx"),
            ),
            selected,
        )
    }

    @Test
    fun exclusionsArePersistentButDoNotAffectExplicitSelection() {
        val registry = LegacyPluginExtensionRegistry()

        assertEquals(
            listOf(LegacyPluginExtensionRegistry.Action.Preset("Mathx")),
            registry.selectAllBut(arrayOf(arrayOf("array", "number"))),
        )
        assertEquals(
            listOf(LegacyPluginExtensionRegistry.Action.Preset("Mathx")),
            registry.selectAll(),
        )
        assertEquals(
            listOf(LegacyPluginExtensionRegistry.Action.Preset("Arrayx")),
            registry.select(arrayOf("array")),
        )
    }

    @Test
    fun registeredModulesCanOverridePresetsAndParticipateInExtendAll() {
        val registry = LegacyPluginExtensionRegistry()

        registry.register(
            listOf(
                "Arrayx" to "replacement",
                "Customx" to "custom",
            ),
        )

        assertEquals(
            listOf(LegacyPluginExtensionRegistry.Action.Registered("replacement")),
            registry.select(arrayOf("array")),
        )
        assertEquals(
            listOf(
                LegacyPluginExtensionRegistry.Action.Registered("replacement"),
                LegacyPluginExtensionRegistry.Action.Preset("Numberx"),
                LegacyPluginExtensionRegistry.Action.Preset("Mathx"),
                LegacyPluginExtensionRegistry.Action.Registered("custom"),
            ),
            registry.selectAll(),
        )
    }

}
