package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.ui.edit.editor.ace.font.FontTestFixtures
import org.autojs.autojs.ui.edit.editor.ace.font.RemoteFont
import org.junit.Assert.assertEquals
import org.junit.Test

class AceEditorRemoteFontGroupResolverTest {

    @Test
    fun legacySingletonGroupsPreserveInputOrder() {
        val fonts = listOf(
            font(id = "second"),
            font(id = "first"),
        )

        val groups = resolveRemoteFontGroups(fonts, activeFontId = null) { null }

        assertEquals(listOf("second", "first"), groups.map { it.groupId })
        assertEquals(listOf("second", "first"), groups.map { it.selected.id })
        assertEquals(listOf(listOf("second"), listOf("first")), groups.map { group ->
            group.variants.map(RemoteFont::id)
        })
    }

    @Test
    fun variantsAreSortedByComplexityThenStableId() {
        val fonts = listOf(
            font("family_high", "family", variantOrder = 20, isDefault = true),
            font("family_mid_z", "family", variantOrder = 10, isDefault = false),
            font("family_low", "family", variantOrder = 0, isDefault = false),
            font("family_mid_a", "family", variantOrder = 10, isDefault = false),
        )

        val group = resolveRemoteFontGroups(fonts, activeFontId = null) { null }.single()

        assertEquals(
            listOf("family_low", "family_mid_a", "family_mid_z", "family_high"),
            group.variants.map(RemoteFont::id),
        )
    }

    @Test
    fun explicitCatalogDefaultCanOverrideHighestComplexity() {
        val triangle = font("geist_pixel_triangle", "geist_pixel", 10, isDefault = true)
        val circle = font("geist_pixel_circle", "geist_pixel", 20, isDefault = false)

        val group = resolveRemoteFontGroups(
            listOf(circle, triangle),
            activeFontId = null,
        ) { null }.single()

        assertEquals("geist_pixel_triangle", group.selected.id)
    }

    @Test
    fun activeVariantWinsPreferredVariantAndCatalogDefault() {
        val regular = font("family_regular", "family", 0, isDefault = true)
        val cn = font("family_cn", "family", 10, isDefault = false)
        val nerd = font("family_nerd", "family", 20, isDefault = false)
        val fonts = listOf(regular, cn, nerd)

        val active = resolveRemoteFontGroups(fonts, activeFontId = cn.id) { nerd.id }.single()
        val preferred = resolveRemoteFontGroups(fonts, activeFontId = null) { nerd.id }.single()
        val catalogDefault = resolveRemoteFontGroups(fonts, activeFontId = null) { null }.single()

        assertEquals(cn.id, active.selected.id)
        assertEquals(nerd.id, preferred.selected.id)
        assertEquals(regular.id, catalogDefault.selected.id)
    }

    @Test
    fun staleSelectionsFallBackToHighestComplexityAndStableId() {
        val lower = font("family_lower", "family", 10, isDefault = false)
        val highA = font("family_high_a", "family", 20, isDefault = false)
        val highZ = font("family_high_z", "family", 20, isDefault = false)

        val group = resolveRemoteFontGroups(
            listOf(highA, lower, highZ),
            activeFontId = "removed_variant",
        ) { "also_removed" }.single()

        assertEquals(highZ.id, group.selected.id)
    }

    private fun font(
        id: String,
        groupId: String = id,
        variantOrder: Int = 0,
        isDefault: Boolean = true,
    ): RemoteFont = FontTestFixtures.remoteFont(
        bytes = FontTestFixtures.validWoff2(fill = id.length.toByte()),
        id = id,
    ).copy(
        displayName = groupId,
        groupId = groupId,
        variantName = id,
        variantOrder = variantOrder,
        isDefaultVariant = isDefault,
    )
}
