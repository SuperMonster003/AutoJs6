package org.autojs.autojs.core.plugin.center.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallWizardProfileCodecTest {

    @Test
    fun roundTripKeepsIdNameAndPackageOrder() {
        val first = PluginInstallWizardProfile.custom("OCR set", linkedSetOf("b.pkg", "a.pkg"), id = "custom-1")
        val second = PluginInstallWizardProfile.custom("  Runtime set ", emptySet(), id = "custom-2")

        val decoded = PluginInstallWizardProfileCodec.decode(PluginInstallWizardProfileCodec.encode(listOf(first, second)))

        assertEquals(listOf(first, second.copy(customName = "Runtime set")), decoded)
        assertEquals(listOf("b.pkg", "a.pkg"), decoded[0].packageNames.toList())
        assertTrue(decoded.all { it.isCustom })
    }

    @Test
    fun builtInProfilesAreNeverPersisted() {
        val json = PluginInstallWizardProfileCodec.encode(
            listOf(PluginInstallWizardProfile.STANDARD, PluginInstallWizardProfile.custom("x", setOf("a"), id = "custom-x")),
        )
        assertEquals(listOf("custom-x"), PluginInstallWizardProfileCodec.decode(json).map { it.id })
    }

    @Test
    fun decodeToleratesGarbage() {
        assertEquals(emptyList<PluginInstallWizardProfile>(), PluginInstallWizardProfileCodec.decode(null))
        assertEquals(emptyList<PluginInstallWizardProfile>(), PluginInstallWizardProfileCodec.decode(""))
        assertEquals(emptyList<PluginInstallWizardProfile>(), PluginInstallWizardProfileCodec.decode("{not json"))
        assertEquals(emptyList<PluginInstallWizardProfile>(), PluginInstallWizardProfileCodec.decode("{\"id\":\"x\"}"))
    }

    @Test
    fun decodeSkipsInvalidEntriesAndDuplicateIds() {
        val json = """
            [
              {"id": "custom-1", "name": "One", "packages": ["a", "", 3]},
              {"id": "custom-1", "name": "Duplicate", "packages": []},
              {"id": "", "name": "No id"},
              {"id": "custom-3", "name": "   "},
              {"id": "custom-4", "name": "Four"},
              "not an object"
            ]
        """.trimIndent()

        val decoded = PluginInstallWizardProfileCodec.decode(json)

        assertEquals(listOf("custom-1", "custom-4"), decoded.map { it.id })
        assertEquals(setOf("a", "3"), decoded[0].packageNames)
        assertEquals(emptySet<String>(), decoded[1].packageNames)
    }

}
