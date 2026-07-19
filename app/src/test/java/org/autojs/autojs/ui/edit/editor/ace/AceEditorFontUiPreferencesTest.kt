package org.autojs.autojs.ui.edit.editor.ace

import org.autojs.autojs.ui.edit.editor.ace.font.FontFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceEditorFontUiPreferencesTest {

    @Test
    fun sortDirectionsShareTypeLockFieldAndDefaults() {
        assertEquals(AceEditorFontSortField.NAME, AceEditorFontSortMode.NAME_ASC.field)
        assertEquals(AceEditorFontSortField.NAME, AceEditorFontSortMode.NAME_DESC.field)
        assertEquals(AceEditorFontSortField.SIZE, AceEditorFontSortMode.SIZE_ASC.field)
        assertEquals(AceEditorFontSortField.SIZE, AceEditorFontSortMode.SIZE_DESC.field)
        assertTrue(AceEditorFontSortField.NAME.typeLockedByDefault)
        assertFalse(AceEditorFontSortField.SIZE.typeLockedByDefault)
    }

    @Test
    fun typeLockUsesFixedPriorityBeforeRequestedNameDirection() {
        val options = listOf(
            option("not_z", "Zulu", AceEditorFontType.NOT_INSTALLED),
            option("installed_a", "Alpha", AceEditorFontType.INSTALLED),
            option("built_a", "Alpha", AceEditorFontType.BUILT_IN),
            option("not_a", "Alpha", AceEditorFontType.NOT_INSTALLED),
            option("installed_z", "Zulu", AceEditorFontType.INSTALLED),
            option("built_z", "Zulu", AceEditorFontType.BUILT_IN),
        )

        val sorted = AceEditorFontUiPreferences.sortedOptions(
            options,
            AceEditorFontSortMode.NAME_DESC,
            typeLocked = true,
        )

        assertEquals(
            listOf("built_z", "built_a", "installed_z", "installed_a", "not_z", "not_a"),
            sorted.map { it.id },
        )
    }

    @Test
    fun unlockedSizeSortIgnoresFontType() {
        val options = listOf(
            option("built", "Built", AceEditorFontType.BUILT_IN, sizeBytes = 300),
            option("installed", "Installed", AceEditorFontType.INSTALLED, sizeBytes = 200),
            option("not_installed", "Not installed", AceEditorFontType.NOT_INSTALLED, sizeBytes = 100),
        )

        val sorted = AceEditorFontUiPreferences.sortedOptions(
            options,
            AceEditorFontSortMode.SIZE_ASC,
            typeLocked = false,
        )

        assertEquals(listOf("not_installed", "installed", "built"), sorted.map { it.id })
    }

    @Test
    fun lockedSizeSortKeepsTypesTogetherBeforeSortingWithinGroups() {
        val options = listOf(
            option("not_small", "Not small", AceEditorFontType.NOT_INSTALLED, sizeBytes = 1),
            option("installed_large", "Installed large", AceEditorFontType.INSTALLED, sizeBytes = 20),
            option("built_large", "Built large", AceEditorFontType.BUILT_IN, sizeBytes = 30),
            option("installed_small", "Installed small", AceEditorFontType.INSTALLED, sizeBytes = 2),
            option("built_small", "Built small", AceEditorFontType.BUILT_IN, sizeBytes = 3),
        )

        val sorted = AceEditorFontUiPreferences.sortedOptions(
            options,
            AceEditorFontSortMode.SIZE_ASC,
            typeLocked = true,
        )

        assertEquals(
            listOf("built_small", "built_large", "installed_small", "installed_large", "not_small"),
            sorted.map { it.id },
        )
    }

    @Test
    fun allFeaturesSelectedMeansNoFeatureRestriction() {
        val filter = AceEditorFontPreviewFilter.DEFAULT

        assertTrue(filter.allows(option("plain", "Plain", AceEditorFontType.BUILT_IN)))
        assertTrue(
            filter.allows(
                option(
                    "nerd",
                    "Nerd",
                    AceEditorFontType.NOT_INSTALLED,
                    features = setOf(FontFeature.NERD),
                ),
            ),
        )
    }

    @Test
    fun narrowingFeaturesRequiresAtLeastOneExplicitSelectedFeature() {
        val cnOnly = AceEditorFontPreviewFilter(
            excludedFeatures = FontFeature.entries.toSet() - FontFeature.CN,
        )

        assertTrue(cnOnly.allows(optionWithFeatures(FontFeature.CN)))
        assertTrue(cnOnly.allows(optionWithFeatures(FontFeature.CN, FontFeature.NERD)))
        assertFalse(cnOnly.allows(optionWithFeatures(FontFeature.NERD)))
        assertFalse(cnOnly.allows(option("plain", "Plain", AceEditorFontType.NOT_INSTALLED)))
    }

    @Test
    fun selectedFeatureSubsetUsesOrSemantics() {
        val nerdOrCn = AceEditorFontPreviewFilter(
            excludedFeatures = setOf(FontFeature.VARIABLE),
        )

        assertTrue(nerdOrCn.allows(optionWithFeatures(FontFeature.NERD)))
        assertTrue(nerdOrCn.allows(optionWithFeatures(FontFeature.CN, FontFeature.VARIABLE)))
        assertFalse(nerdOrCn.allows(optionWithFeatures(FontFeature.VARIABLE)))
    }

    @Test
    fun typeFilterUsesBuiltInInstalledAndNotInstalledCategories() {
        val installedOnly = AceEditorFontPreviewFilter(
            excludedTypes = AceEditorFontType.entries.toSet() - AceEditorFontType.INSTALLED,
        )

        assertFalse(installedOnly.allows(option("built", "Built", AceEditorFontType.BUILT_IN)))
        assertTrue(installedOnly.allows(option("installed", "Installed", AceEditorFontType.INSTALLED)))
        assertFalse(
            installedOnly.allows(option("not", "Not installed", AceEditorFontType.NOT_INSTALLED)),
        )
    }

    @Test
    fun emptyFeatureOrTypeSelectionIsInvalid() {
        val noFeatures = AceEditorFontPreviewFilter(excludedFeatures = FontFeature.entries.toSet())
        val noTypes = AceEditorFontPreviewFilter(excludedTypes = AceEditorFontType.entries.toSet())

        assertFalse(noFeatures.hasSelectedFeatures)
        assertFalse(noFeatures.allows(optionWithFeatures(FontFeature.CN)))
        assertFalse(noTypes.hasSelectedTypes)
        assertFalse(noTypes.allows(optionWithFeatures(FontFeature.CN)))
    }

    private fun optionWithFeatures(vararg features: FontFeature): AceEditorFontOption = option(
        id = features.joinToString("_") { it.catalogValue },
        name = "Feature font",
        type = AceEditorFontType.NOT_INSTALLED,
        features = features.toSet(),
    )

    private fun option(
        id: String,
        name: String,
        type: AceEditorFontType,
        sizeBytes: Long? = null,
        features: Set<FontFeature> = emptySet(),
    ): AceEditorFontOption = AceEditorFontOption(
        id = id,
        displayName = name,
        family = name,
        author = "",
        licenseName = "",
        order = 0,
        delivery = if (type == AceEditorFontType.BUILT_IN) {
            AceEditorFontOption.Delivery.BUNDLED
        } else {
            AceEditorFontOption.Delivery.DOWNLOADABLE
        },
        type = type,
        features = features,
        sizeBytes = sizeBytes,
    )
}
