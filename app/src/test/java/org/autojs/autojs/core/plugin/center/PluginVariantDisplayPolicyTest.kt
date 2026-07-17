package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginVariantDisplayPolicyTest {

    @Test
    fun standardListKeepsFeaturedAndInstalledAdditionalVariants() {
        assertTrue(
            shouldDisplay(isFeatured = true, isInstalled = false, mode = PluginVariantDisplayMode.STANDARD),
        )
        assertFalse(
            shouldDisplay(isFeatured = false, isInstalled = false, mode = PluginVariantDisplayMode.STANDARD),
        )
        assertTrue(
            shouldDisplay(isFeatured = false, isInstalled = true, mode = PluginVariantDisplayMode.STANDARD),
        )
    }

    @Test
    fun additionalListContainsOnlyAdditionalVariants() {
        assertFalse(
            shouldDisplay(
                isFeatured = true,
                isInstalled = false,
                mode = PluginVariantDisplayMode.ADDITIONAL_ONLY,
            ),
        )
        assertTrue(
            shouldDisplay(
                isFeatured = false,
                isInstalled = false,
                mode = PluginVariantDisplayMode.ADDITIONAL_ONLY,
            ),
        )
        assertTrue(
            shouldDisplay(
                isFeatured = false,
                isInstalled = true,
                mode = PluginVariantDisplayMode.ADDITIONAL_ONLY,
            ),
        )
    }

    private fun shouldDisplay(
        isFeatured: Boolean,
        isInstalled: Boolean,
        mode: PluginVariantDisplayMode,
    ) = PluginVariantDisplayPolicy.shouldDisplay(isFeatured, isInstalled, mode)
}
