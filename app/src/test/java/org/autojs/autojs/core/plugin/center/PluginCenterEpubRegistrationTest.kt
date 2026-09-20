package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.epub.api.EpubActions
import org.autojs.plugin.epub.api.EpubIds
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.explorer.api.ExplorerActionPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Readium EPUB Reader package exports an Explorer Action service and an EPUB service
 * (plugin roadmap D10). The plugin center keys its entry by the common info endpoint, so both
 * engines must resolve to their own service action, and both stay enabled by default (D29).
 */
class PluginCenterEpubRegistrationTest {

    @Test
    fun engineResolvesToDedicatedServiceAction() {
        assertEquals(EpubActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(EpubIds.ENGINE))
        assertEquals(EpubActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(" EPUB "))
        assertEquals(
            ExplorerActionPluginActions.EXPLORER_ACTION,
            PluginCenterViewModel.serviceActionFromEngine(ExplorerActionPluginIds.ENGINE),
        )
    }

    @Test
    fun unrelatedEnginesStayUnmapped() {
        assertNull(PluginCenterViewModel.serviceActionFromEngine("epub-reader"))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("readium"))
    }

    @Test
    fun bothServicesOfThePackageAreEnabledByDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(EpubIds.ENGINE))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(ExplorerActionPluginIds.ENGINE))
    }

    @Test
    fun commonInfoEndpointStillWinsForTheDualServicePackage() {
        assertNull(
            PluginDiscoveryEndpointPolicy.selectAction(
                listOf(null, ExplorerActionPluginActions.EXPLORER_ACTION, EpubActions.SERVICE_ACTION),
            ),
        )
        assertEquals(
            ExplorerActionPluginActions.EXPLORER_ACTION,
            PluginDiscoveryEndpointPolicy.selectAction(
                listOf(ExplorerActionPluginActions.EXPLORER_ACTION, EpubActions.SERVICE_ACTION),
            ),
        )
    }
}
