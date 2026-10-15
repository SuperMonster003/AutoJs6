package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.gallery.GalleryPluginHost
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.gallery.api.GalleryActions
import org.autojs.plugin.gallery.api.GalleryIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 3-Maple Image exports its Explorer Action services and, from 3.4.0, the gallery service of the script
 * object `gallery` (3-Maple Image roadmap M6). The Plugin Center keeps one entry per package, keyed by
 * the first discovered endpoint, and probes the package through the gallery service when it is the one
 * asked for; the engine is enabled by default.
 *
 * zh-CN: 3-Maple Image 导出 Explorer Action 服务, 并从 3.4.0 起导出脚本对象 gallery 的服务. 插件中心每个包保留一个条目,
 * 以最先发现的端点为键, 需要时经 gallery 服务探测该包; 该引擎默认启用.
 */
class PluginCenterGalleryRegistrationTest {

    @Test
    fun engineResolvesToTheGalleryServiceAction() {
        assertEquals("org.autojs.plugin.GALLERY", GalleryPluginHost.ACTION_GALLERY)
        assertEquals(GalleryActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(GalleryIds.ENGINE))
        assertEquals(GalleryActions.SERVICE_ACTION, PluginCenterViewModel.serviceActionFromEngine(" Gallery "))
    }

    @Test
    fun theExplorerActionEndpointKeepsKeyingThePackage() {
        assertEquals(
            ExplorerActionPluginActions.EXPLORER_ACTION,
            PluginDiscoveryEndpointPolicy.selectAction(listOf(ExplorerActionPluginActions.EXPLORER_ACTION, GalleryActions.SERVICE_ACTION)),
        )
    }

    @Test
    fun thePluginIsEnabledByDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(GalleryIds.ENGINE))
    }
}
