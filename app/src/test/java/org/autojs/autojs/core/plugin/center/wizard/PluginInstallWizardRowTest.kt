package org.autojs.autojs.core.plugin.center.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallWizardRowTest {

    @Test
    fun installedOrApkLessPluginsAreNotSelectable() {
        assertTrue(item("a").isSelectable)
        assertFalse(item("b", installed = true).isSelectable)
        assertFalse(item("c", apkUrl = null).isSelectable)
    }

    @Test
    fun installedPluginsBecomeDoneCandidates() {
        val candidate = item("a", installed = true).toCandidate()
        assertTrue(candidate.done)
        assertFalse(candidate.selectable)
    }

    @Test
    fun rowsGroupItemsByCategoryInCatalogOrderAndDropEmptyCategories() {
        val items = listOf(
            item("tool", PluginInstallWizardCategory.TOOLS),
            item("img1", PluginInstallWizardCategory.IMAGE),
            item("editor", PluginInstallWizardCategory.EDITOR),
            item("img2", PluginInstallWizardCategory.IMAGE),
        )

        val rows = PluginInstallWizardRow.build(items)

        assertEquals(
            listOf(
                PluginInstallWizardRow.Header(PluginInstallWizardCategory.EDITOR, listOf("editor")),
                PluginInstallWizardRow.Plugin(items[2]),
                PluginInstallWizardRow.Header(PluginInstallWizardCategory.IMAGE, listOf("img1", "img2")),
                PluginInstallWizardRow.Plugin(items[1]),
                PluginInstallWizardRow.Plugin(items[3]),
                PluginInstallWizardRow.Header(PluginInstallWizardCategory.TOOLS, listOf("tool")),
                PluginInstallWizardRow.Plugin(items[0]),
            ),
            rows,
        )
    }

    @Test
    fun hiddenInstalledPluginsTakeFullyInstalledCategoriesWithThem() {
        val items = listOf(
            item("editor", PluginInstallWizardCategory.EDITOR, installed = true),
            item("img1", PluginInstallWizardCategory.IMAGE, installed = true),
            item("img2", PluginInstallWizardCategory.IMAGE),
        )

        val rows = PluginInstallWizardRow.build(items, showInstalled = false)

        assertEquals(
            listOf(
                PluginInstallWizardRow.Header(PluginInstallWizardCategory.IMAGE, listOf("img2")),
                PluginInstallWizardRow.Plugin(items[2]),
            ),
            rows,
        )
        assertEquals(5, PluginInstallWizardRow.build(items, showInstalled = true).size)
    }

    private fun item(
        packageName: String,
        category: PluginInstallWizardCategory = PluginInstallWizardCategory.TOOLS,
        installed: Boolean = false,
        apkUrl: String? = "https://example.com/$packageName.apk",
    ) = PluginInstallWizardItem(
        packageName = packageName,
        title = packageName,
        category = category,
        inStandard = false,
        inMinimal = false,
        isInstalled = installed,
        apkUrl = apkUrl,
        apkSha256 = null,
        apkSizeBytes = null,
        iconUrl = null,
        nightIconUrl = null,
    )

}
