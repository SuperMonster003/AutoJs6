package org.autojs.autojs.core.plugin.tool

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Discovery and catalog cache of the plugin tool providers (MCP Server roadmap P12 host item 2) against the test-only
 * provider `test-apps/plugin-tool-provider-conformance` (package [FAKE_PACKAGE]); skipped when it is not installed.
 * The provider is authorized as USER_GRANTED by its signer fingerprints (its signer need not match the host's) and enabled
 * for the test; its Plugin Center state is restored afterwards.
 *
 * zh-CN: 以测试专用提供者验证插件工具提供者的发现与目录缓存; 未安装时跳过. 测试期间授权并启用该提供者, 结束后恢复其插件中心状态.
 */
class PluginToolRegistryAndroidTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun anAuthorizedProviderIsListedWithItsUsableToolsOnly() = withAuthorizedProvider {
        assertTrue("the first refresh rebuilds the snapshot", runBlocking { PluginToolRegistry.refresh(context) })
        val provider = fakeProvider()
        assertEquals(provider.detail, PluginToolProviderStatus.AVAILABLE, provider.status)
        assertEquals(PluginAuthorizedState.USER_GRANTED, provider.trust)
        val catalog = requireNotNull(provider.catalog)
        assertEquals("faketool", catalog.shortName)
        assertEquals(listOf("faketool_echo", "faketool_fail", "faketool_slow"), catalog.tools.map { it.fullName })
        assertTrue(catalog.diagnostics.toString(), catalog.diagnostics.single().contains("(bad_schema) dropped: inputSchema"))
        val echo = catalog.tools.first()
        assertEquals(5000L, echo.timeoutMs)
        assertEquals(2, echo.maxConcurrency)
        assertTrue(PluginToolRegistry.tools.any { (owner, tool) -> owner.packageName == FAKE_PACKAGE && tool.fullName == "faketool_echo" })
        assertFalse("an unchanged provider set does not rebuild the snapshot", runBlocking { PluginToolRegistry.refresh(context) })
    }

    @Test
    fun aDisabledOrUnauthorizedProviderKeepsItsToolsHidden() = withAuthorizedProvider { fingerprints ->
        runBlocking { PluginToolRegistry.refresh(context) }
        assertEquals(PluginToolProviderStatus.AVAILABLE, fakeProvider().status)

        PluginEnableStore.setEnabled(context, FAKE_PACKAGE, false)
        assertTrue("the enable switch changes the fingerprint", runBlocking { PluginToolRegistry.refresh(context) })
        assertEquals(PluginToolProviderStatus.DISABLED, fakeProvider().status)
        assertNull(fakeProvider().catalog)
        assertFalse(PluginToolRegistry.tools.any { (owner, _) -> owner.packageName == FAKE_PACKAGE })

        PluginEnableStore.setEnabled(context, FAKE_PACKAGE, true)
        fingerprints.forEach { PluginAuthorizationStore.revoke(context, FAKE_PACKAGE, it) }
        assertTrue(runBlocking { PluginToolRegistry.refresh(context) })
        assertEquals(PluginToolProviderStatus.UNAUTHORIZED, fakeProvider().status)

        fingerprints.forEach { PluginAuthorizationStore.grant(context, FAKE_PACKAGE, it) }
        assertTrue(runBlocking { PluginToolRegistry.refresh(context) })
        assertEquals(PluginToolProviderStatus.AVAILABLE, fakeProvider().status)
    }

    private fun fakeProvider(): PluginToolProvider =
        PluginToolRegistry.providers.single { it.packageName == FAKE_PACKAGE }

    private fun withAuthorizedProvider(block: (List<String>) -> Unit) {
        val installed = runCatching { context.packageManager.getPackageInfo(FAKE_PACKAGE, 0) }.isSuccess
        assumeTrue("install test-apps/plugin-tool-provider-conformance (adb install -t) to run this test", installed)
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, FAKE_PACKAGE)
        assertTrue("the fake provider has no signer fingerprint", fingerprints.isNotEmpty())
        val enable = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
        val enableKey = "key_\$_enabled_plugin_\$_$FAKE_PACKAGE"
        val enableBefore = if (enable.contains(enableKey)) enable.getBoolean(enableKey, false) else null
        val authorization = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
        val authorizationBefore = fingerprints.associateWith { fingerprint ->
            val key = "key_\$_plugin_auth_\$_$FAKE_PACKAGE\$_$fingerprint"
            if (authorization.contains(key)) authorization.getBoolean(key, false) else null
        }
        try {
            fingerprints.forEach { PluginAuthorizationStore.grant(context, FAKE_PACKAGE, it) }
            PluginEnableStore.setEnabled(context, FAKE_PACKAGE, true)
            PluginToolRegistry.invalidate()
            block(fingerprints)
        } finally {
            enable.edit().remove(enableKey).apply { enableBefore?.let { putBoolean(enableKey, it) } }.commit()
            authorization.edit().apply {
                authorizationBefore.forEach { (fingerprint, value) ->
                    val key = "key_\$_plugin_auth_\$_$FAKE_PACKAGE\$_$fingerprint"
                    remove(key)
                    value?.let { putBoolean(key, it) }
                }
            }.commit()
            PluginToolRegistry.invalidate()
        }
    }

    private companion object {
        const val FAKE_PACKAGE = "org.autojs.plugin.tool.fake"
        const val ENABLE_SP_NAME = "plugin_center_enable_state"
        const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
    }
}
