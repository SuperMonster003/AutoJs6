package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCatalog
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiIds
import org.junit.Assert.*
import org.junit.Test

class PluginCenterComposeUiRegistrationTest {

    @Test fun officialPackageIsAnOptionalUiPlugin() {
        val entry = requireNotNull(PluginInstallWizardCatalog.find(ComposeUiIds.PLUGIN_PACKAGE_NAME))
        assertEquals("Three Block Compose", entry.fallbackTitle)
        assertEquals(PluginInstallWizardCategory.UI, entry.category)
        assertFalse(entry.inStandard)
        assertFalse(entry.inMinimal)
        assertFalse(ComposeUiIds.PLUGIN_PACKAGE_NAME in PluginInstallWizardCatalog.standardPackageNames)
        assertFalse(ComposeUiIds.PLUGIN_PACKAGE_NAME in PluginInstallWizardCatalog.minimalPackageNames)
    }

    @Test fun infoOnlyRendererUsesTheCommonDiscoveryEndpoint() {
        assertEquals(PluginActions.INFO, ComposeUiContract.INFO_ACTION)
        assertEquals(ComposeUiIds.PLUGIN_ID, ComposeUiContract.INFO_CATEGORY)
        assertNull(PluginCenterViewModel.serviceActionFromEngine(ComposeUiIds.ENGINE))
        assertNull(PluginCenterViewModel.serviceActionFromEngine(" COMPOSE "))
        assertNull(PluginDiscoveryEndpointPolicy.selectAction(listOf(null)))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("imgui"))
    }

    @Test fun defaultEnablementStillRequiresAuthorizationForInstallAutoEnable() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(ComposeUiIds.ENGINE))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(" COMPOSE "))
        listOf(PluginAuthorizedState.OFFICIAL, PluginAuthorizedState.TRUSTED, PluginAuthorizedState.USER_GRANTED).forEach {
            assertTrue(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(ComposeUiIds.ENGINE, it))
        }
        assertFalse(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(ComposeUiIds.ENGINE, PluginAuthorizedState.REQUIRED))
    }

    @Test fun minimumHostVersionIsTheFirstRegisteredV1Build() {
        val minimum = ComposeUiIds.REQUIRED_HOST_VERSION_CODE
        assertEquals(5316L, minimum)
        assertTrue(BuildConfig.VERSION_CODE.toLong() >= minimum)
        assertFalse(PluginCapabilityResolver.isHostVersionCompatible(minimum - 1, minimum))
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(minimum, minimum))
        assertTrue(PluginCapabilityResolver.isHostVersionCompatible(minimum + 1, minimum))
        assertEquals(minimum, PluginCapabilityResolver.coerceLong(minimum.toString()))
        assertEquals(minimum, PluginCapabilityResolver.coerceLong(minimum.toInt()))
    }
}
