package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.autojs.core.plugin.center.PluginCenterItem
import org.autojs.autojs.core.plugin.center.PluginCenterViewModel
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginEnabledState
import org.autojs.autojs.core.plugin.center.PluginIndexEntry
import org.autojs.autojs.core.plugin.center.PluginLocalNetworkPermission
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.center.PluginWakeManager
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Run with an installed MCP APK. Preserve both an absent preference and explicit user choices. Without a saved choice
 * the official signer counts as enabled and any other signer as disabled (maintainer decision of 2026-10-16).
 * zh-CN: 需已安装 MCP APK. 同时保留 "无偏好" 与用户的显式选择. 无已保存选择时, 官方签名视为启用, 其他签名视为关闭
 * (维护者 2026-10-16 的决定).
 */
class McpServerEnablementDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageName = McpServerIds.DEFAULT_PACKAGE_NAME
    private val preferences = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
    private val enableKey = "key_\$_enabled_plugin_\$_$packageName"
    private var savedEnabled: Boolean? = null
    private var stateSaved = false

    @Before
    fun prepareInstalledPlugin() = runBlocking {
        assumeTrue("Install the MCP Server plugin first", McpServerPluginHost.queryServiceCount(context) > 0)
        assumeTrue("Grant the plugin local network permission first", PluginLocalNetworkPermission.isGranted(context, packageName))
        savedEnabled = if (preferences.contains(enableKey)) preferences.getBoolean(enableKey, false) else null
        stateSaved = true
        // Activation is independent of logical enablement and must not seed an enable preference.
        val wake = checkNotNull(PluginWakeManager.buildWakeIntent(context, packageName))
        context.startActivity(wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        withTimeout(10_000L) {
            @Suppress("DEPRECATION")
            while (context.packageManager.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_STOPPED != 0) {
                delay(50)
            }
        }
    }

    @After
    fun restoreEnablement() {
        if (!stateSaved) return
        val editor = preferences.edit()
        savedEnabled?.let { editor.putBoolean(enableKey, it) } ?: editor.remove(enableKey)
        assertTrue(editor.commit())
        PluginStateChangeRegistry.notifyChanged(packageName)
    }

    @Test
    fun installWithoutEnableRecordFollowsTheSignerInCenterAndRuntime() = runBlocking {
        assertTrue(preferences.edit().remove(enableKey).commit())
        val official = PluginTrustManager.resolveTrustInfo(context, packageName).authorizedState == PluginAuthorizedState.OFFICIAL
        val repository = InstalledPluginRepository()
        assertState(official, repository.discoverInstalledSnapshot(context).single { it.packageName == packageName })
        assertState(official, repository.discoverInstalled(context).single { it.packageName == packageName })
        assertFalse("Discovery must not invent a saved enable choice", preferences.contains(enableKey))
    }

    @Test
    fun explicitEnableAndDisableSurviveCenterRecreation() = runBlocking {
        val local = InstalledPluginRepository().discoverInstalled(context).single { it.packageName == packageName }
        for (enabled in listOf(true, false, true)) {
            PluginEnableStore.setEnabled(context, packageName, enabled)
            repeat(2) { assertState(enabled, local) }
            assertTrue(preferences.contains(enableKey))
            assertEquals(enabled, preferences.getBoolean(enableKey, !enabled))
        }
    }

    private fun assertState(enabled: Boolean, local: InstalledPluginRepository.InstalledPlugin) {
        // Exercise the real UI mapping without an unrelated online index refresh.
        val mapper = PluginCenterViewModel::class.java.getDeclaredMethod(
            "toPluginCenterItem", Context::class.java, PluginIndexEntry::class.java,
            InstalledPluginRepository.InstalledPlugin::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val item = mapper.invoke(PluginCenterViewModel(), context, null, local, false) as PluginCenterItem
        val inspection = McpServerPluginInspector.inspect(context)
        val actual = "center=${item.isEnabled}, enabledState=${item.enabledState}, runtime=${inspection.state}"
        assertEquals(actual, enabled, item.isEnabled)
        assertEquals(actual, if (enabled) PluginEnabledState.READY else PluginEnabledState.DISABLED, item.enabledState)
        assertEquals(actual, if (enabled) McpServerUiState.AVAILABLE else McpServerUiState.PLUGIN_DISABLED, inspection.state)
    }
}
