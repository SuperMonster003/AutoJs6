package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCatalog
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory
import org.autojs.autojs.core.plugin.speech.SpeechPluginHost
import org.autojs.plugin.speech.api.SpeechPluginActions
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The 3-Macaw Speech package exports an INFO and a SPEECH service (3-Macaw Speech roadmap 5.5, P1.3). The Plugin Center
 * keys its entry by the common INFO endpoint and probes the package through the SPEECH service; the plugin is enabled by
 * default, listed under the tools category of the install wizard (roadmap appendix D Q1 (a)), and the host may query
 * its package.
 * zh-CN: 3-Macaw Speech 包导出 INFO 与 SPEECH 服务 (3-Macaw Speech 路线图 5.5, P1.3). 插件中心以公共 INFO 端点为条目键, 并经 SPEECH 服务探测
 * 该包; 插件默认启用, 列在安装向导的工具分类下 (路线图附录 D Q1 (a)), 宿主可查询其包.
 */
class PluginCenterSpeechRegistrationTest {

    @Test
    fun engineResolvesToTheSpeechServiceAction() {
        assertEquals("org.autojs.plugin.SPEECH", SpeechPluginHost.ACTION_SPEECH)
        assertEquals(SpeechPluginActions.SPEECH, PluginCenterViewModel.serviceActionFromEngine(SpeechPluginIds.ENGINE))
        assertEquals(SpeechPluginActions.SPEECH, PluginCenterViewModel.serviceActionFromEngine(" Speech "))
        assertNull(PluginCenterViewModel.serviceActionFromEngine("speech-recognition"))
    }

    @Test
    fun theCommonInfoEndpointWinsForTheTwoServicePackage() {
        assertNull(PluginDiscoveryEndpointPolicy.selectAction(listOf(SpeechPluginActions.SPEECH, null)))
    }

    @Test
    fun thePluginIsEnabledByDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(SpeechPluginIds.ENGINE))
    }

    @Test
    fun theInstallWizardListsThePluginUnderTools() {
        val entry = PluginInstallWizardCatalog.find(SpeechPluginIds.OFFICIAL_PACKAGE_NAME)
        assertEquals(PluginInstallWizardCategory.TOOLS, entry?.category)
        assertEquals("Three Macaw Speech", entry?.fallbackTitle)
        // The built-in profiles are the maintainer's draft; the plugin joins them only by an explicit decision.
        assertFalse(entry?.inStandard == true)
        assertFalse(entry?.inMinimal == true)
    }

    @Test
    fun theManifestQueriesTheServiceAndThePackage() {
        val lines = locate("src/main/AndroidManifest.xml").readLines().map { it.trim() }
        val action = lines.indexOf("<action android:name=\"${SpeechPluginActions.SPEECH}\" />")
        assertTrue("the SPEECH intent is queried", action >= 0)
        assertEquals("<category android:name=\"${SpeechPluginActions.SERVICE_CATEGORY}\" />", lines[action + 1])
        assertTrue(lines.contains("<package android:name=\"${SpeechPluginIds.OFFICIAL_PACKAGE_NAME}\" />"))
    }

    private fun locate(relative: String): File =
        listOf(File(relative), File("app", relative)).firstOrNull(File::isFile) ?: error("$relative not found from ${File("").absolutePath}")
}
