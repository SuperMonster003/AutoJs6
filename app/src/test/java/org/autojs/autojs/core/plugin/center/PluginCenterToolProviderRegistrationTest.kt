package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.autojs.plugin.tool.api.PluginToolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A plugin may export nothing but a plugin tool provider (MCP roadmap D60): the Plugin Center lists such a package through
 * its provider, while packages that also declare INFO or a family service keep their endpoint, so that the official tool
 * pilots are listed and probed as before.
 *
 * zh-CN: 插件可以只导出插件工具提供者: 插件中心经提供者列出这样的包, 同时声明了 INFO 或家族服务的包保持原有端点, 官方试点插件的列出与探测不变.
 */
class PluginCenterToolProviderRegistrationTest {

    private data class Service(val packageName: String, val action: String?)

    @Test
    fun onlyPackagesWithoutAnotherServiceAreListedThroughTheirProvider() {
        val declared = listOf(
            Service("org.example.pilot", null),
            Service("org.example.archive", ExplorerActionPluginActions.EXPLORER_ACTION),
        )
        val providers = listOf(
            Service("org.example.pilot", PluginToolContract.SERVICE_ACTION),
            Service("org.example.archive", PluginToolContract.SERVICE_ACTION),
            Service("org.example.thirdparty", PluginToolContract.SERVICE_ACTION),
        )
        val listed = PluginDiscoveryEndpointPolicy.toolProviderOnly(declared, providers) { it.packageName }
        assertEquals(listOf(Service("org.example.thirdparty", PluginToolContract.SERVICE_ACTION)), listed)
    }

    @Test
    fun aProviderOnlyPackageIsProbedThroughItsProvider() {
        assertEquals("org.autojs.plugin.TOOL_PROVIDER", PluginToolContract.SERVICE_ACTION)
        assertEquals(PluginToolContract.SERVICE_ACTION, PluginDiscoveryEndpointPolicy.selectAction(listOf(PluginToolContract.SERVICE_ACTION)))
        assertNull(PluginDiscoveryEndpointPolicy.selectAction(listOf(null, PluginToolContract.SERVICE_ACTION)))
    }
}
