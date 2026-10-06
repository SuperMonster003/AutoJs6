package org.autojs.autojs.ui.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardProfileCodecTest {

    private val codec = WizardProfileCodec(membersKey = "packages")

    @Test
    fun roundTripKeepsIdNameAndMemberOrder() {
        val first = WizardProfile.custom("OCR set", linkedSetOf("b.pkg", "a.pkg"), id = "custom-1")
        val second = WizardProfile.custom("  Runtime set ", emptySet(), id = "custom-2")

        val decoded = codec.decode(codec.encode(listOf(first, second)))

        assertEquals(listOf(first, second.copy(customName = "Runtime set")), decoded)
        assertEquals(listOf("b.pkg", "a.pkg"), decoded[0].memberIds.toList())
        assertTrue(decoded.all { it.isCustom })
    }

    @Test
    fun builtInProfilesAreNeverPersisted() {
        val json = codec.encode(listOf(WizardProfile.STANDARD, WizardProfile.custom("x", setOf("a"), id = "custom-x")))
        assertEquals(listOf("custom-x"), codec.decode(json).map { it.id })
    }

    @Test
    fun documentsOfThePluginWizardStayReadable() {
        // Written by the plugin install wizard before profiles were shared between wizards.
        // zh-CN: 由各向导共用配置之前的插件安装向导写入.
        val json = """[{"id":"custom-1","name":"Work","packages":["org.autojs.a","org.autojs.b"]}]"""
        val decoded = codec.decode(json)
        assertEquals(listOf("custom-1"), decoded.map { it.id })
        assertEquals(listOf("org.autojs.a", "org.autojs.b"), decoded[0].memberIds.toList())
    }

    @Test
    fun membersKeyIsConfigurable() {
        val permissions = WizardProfileCodec(membersKey = "permissions")
        val json = permissions.encode(listOf(WizardProfile.custom("Mine", linkedSetOf("all_files_access"), id = "custom-p")))
        assertTrue(json.contains("\"permissions\""))
        assertEquals(setOf("all_files_access"), permissions.decode(json).single().memberIds)
        assertEquals(emptySet<String>(), codec.decode(json).single().memberIds)
    }

    @Test
    fun decodeToleratesGarbage() {
        assertEquals(emptyList<WizardProfile>(), codec.decode(null))
        assertEquals(emptyList<WizardProfile>(), codec.decode(""))
        assertEquals(emptyList<WizardProfile>(), codec.decode("{not json"))
        assertEquals(emptyList<WizardProfile>(), codec.decode("{\"id\":\"x\"}"))
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

        val decoded = codec.decode(json)

        assertEquals(listOf("custom-1", "custom-4"), decoded.map { it.id })
        assertEquals(setOf("a", "3"), decoded[0].memberIds)
        assertEquals(emptySet<String>(), decoded[1].memberIds)
    }

}
