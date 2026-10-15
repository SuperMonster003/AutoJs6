package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.batchrename.BatchRenamePluginHost
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCatalog
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory
import org.autojs.plugin.batchrename.api.BatchRenamePluginActions
import org.autojs.plugin.batchrename.api.BatchRenamePluginIds
import org.autojs.plugin.explorer.api.ExplorerActionPluginActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Batch Rename package exports an INFO, a Batch Rename and an Explorer Action service (plugin roadmap 5.4). The
 * Plugin Center keys its entry by the common INFO endpoint and probes the package through the Batch Rename service;
 * the plugin is enabled by default and listed under the file manager category of the install wizard.
 *
 * zh-CN: Batch Rename 包导出 INFO, Batch Rename 与 Explorer Action 三个服务. 插件中心以公共 INFO 端点为条目键, 并经 Batch Rename
 * 服务探测该包; 插件默认启用, 并列在安装向导的文件管理分类下.
 */
class PluginCenterBatchRenameRegistrationTest {

    @Test
    fun engineResolvesToTheBatchRenameServiceAction() {
        assertEquals("org.autojs.plugin.BATCH_RENAME", BatchRenamePluginHost.ACTION_BATCH_RENAME)
        assertEquals(BatchRenamePluginActions.BATCH_RENAME, PluginCenterViewModel.serviceActionFromEngine(BatchRenamePluginIds.ENGINE))
        assertEquals(BatchRenamePluginActions.BATCH_RENAME, PluginCenterViewModel.serviceActionFromEngine(" Batch-Rename "))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("rename"))
    }

    @Test
    fun theCommonInfoEndpointWinsForTheThreeServicePackage() {
        assertNull(
            PluginDiscoveryEndpointPolicy.selectAction(
                listOf(null, ExplorerActionPluginActions.EXPLORER_ACTION, BatchRenamePluginActions.BATCH_RENAME),
            ),
        )
    }

    @Test
    fun thePluginIsEnabledByDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(BatchRenamePluginIds.ENGINE))
    }

    @Test
    fun theInstallWizardListsThePluginUnderFileManagement() {
        val entry = PluginInstallWizardCatalog.find(BatchRenamePluginIds.PACKAGE_NAME)
        assertEquals(PluginInstallWizardCategory.FILE_MANAGER, entry?.category)
        assertEquals("Batch Rename", entry?.fallbackTitle)
        // The built-in profiles are the maintainer's draft; the plugin joins them only by an explicit decision.
        assertFalse(entry?.inStandard == true)
        assertFalse(entry?.inMinimal == true)
    }
}
