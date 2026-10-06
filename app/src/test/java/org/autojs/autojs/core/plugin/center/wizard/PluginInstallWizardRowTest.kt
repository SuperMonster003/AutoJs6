package org.autojs.autojs.core.plugin.center.wizard

import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardSelectionPolicy.Candidate
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardSelectionPolicy.NameValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallWizardSelectionPolicyTest {

    private val candidates = listOf(
        Candidate("editor", inStandard = true, inMinimal = true, selectable = true),
        Candidate("docs", inStandard = true, inMinimal = true, selectable = false),
        Candidate("opencv", inStandard = true, inMinimal = false, selectable = true),
        Candidate("lua", inStandard = false, inMinimal = false, selectable = true),
        Candidate("broken", inStandard = false, inMinimal = false, selectable = false),
    )

    @Test
    fun installedOrApkLessPluginsAreNotSelectable() {
        assertTrue(PluginInstallWizardSelectionPolicy.isSelectable(isInstalled = false, hasInstallableApk = true))
        assertFalse(PluginInstallWizardSelectionPolicy.isSelectable(isInstalled = true, hasInstallableApk = true))
        assertFalse(PluginInstallWizardSelectionPolicy.isSelectable(isInstalled = false, hasInstallableApk = false))
    }

    @Test
    fun builtInProfilesResolveFromMarkersAndSkipUnselectable() {
        assertEquals(
            listOf("editor", "opencv"),
            PluginInstallWizardSelectionPolicy.resolveSelection(PluginInstallWizardProfile.STANDARD, candidates).toList(),
        )
        assertEquals(
            listOf("editor"),
            PluginInstallWizardSelectionPolicy.resolveSelection(PluginInstallWizardProfile.MINIMAL, candidates).toList(),
        )
        assertEquals(
            listOf("editor", "opencv", "lua"),
            PluginInstallWizardSelectionPolicy.resolveSelection(PluginInstallWizardProfile.ALL, candidates).toList(),
        )
    }

    @Test
    fun customProfilesResolveFromTheirPackageSet() {
        val profile = PluginInstallWizardProfile.custom("mine", setOf("lua", "docs", "missing"), id = "custom-1")
        assertEquals(
            listOf("lua"),
            PluginInstallWizardSelectionPolicy.resolveSelection(profile, candidates).toList(),
        )
    }

    @Test
    fun customNamesMustBeNonBlankAndUnique() {
        val existing = listOf(
            PluginInstallWizardProfile.STANDARD,
            PluginInstallWizardProfile.custom("Work", setOf("a"), id = "custom-1"),
        )
        assertEquals(NameValidation.BLANK, PluginInstallWizardSelectionPolicy.validateCustomName("   ", existing))
        assertEquals(NameValidation.DUPLICATE, PluginInstallWizardSelectionPolicy.validateCustomName(" work ", existing))
        assertEquals(NameValidation.OK, PluginInstallWizardSelectionPolicy.validateCustomName("work", existing, excludingId = "custom-1"))
        assertEquals(NameValidation.OK, PluginInstallWizardSelectionPolicy.validateCustomName("Home", existing))
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

    private fun item(packageName: String, category: PluginInstallWizardCategory) = PluginInstallWizardItem(
        packageName = packageName,
        title = packageName,
        category = category,
        inStandard = false,
        inMinimal = false,
        isInstalled = false,
        apkUrl = "https://example.com/$packageName.apk",
        apkSha256 = null,
        apkSizeBytes = null,
        iconUrl = null,
        nightIconUrl = null,
    )

}
