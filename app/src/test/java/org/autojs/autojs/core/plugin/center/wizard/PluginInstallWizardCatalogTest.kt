package org.autojs.autojs.core.plugin.center.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallWizardCatalogTest {

    @Test
    fun packageNamesAreUnique() {
        val names = PluginInstallWizardCatalog.entries.map { it.packageName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun minimalIsSubsetOfStandard() {
        PluginInstallWizardCatalog.entries.filter { it.inMinimal }.forEach { entry ->
            assertTrue("${entry.packageName} is minimal but not standard", entry.inStandard)
        }
        assertTrue(PluginInstallWizardCatalog.standardPackageNames.containsAll(PluginInstallWizardCatalog.minimalPackageNames))
    }

    @Test
    fun builtInProfilesMatchTheDraft() {
        val prefix = "io.github.supermonster003.autojs6.plugin."
        assertEquals(
            setOf(
                "${prefix}ace.editor",
                "org.autojs.plugin.apkbuilder.template",
                "${prefix}offlinedocs",
            ),
            PluginInstallWizardCatalog.minimalPackageNames,
        )
        assertEquals(
            setOf(
                "${prefix}ace.editor",
                "org.autojs.plugin.apkbuilder.template",
                "${prefix}apkinspector",
                "${prefix}three.stack.archive",
                "${prefix}htmlpreviewer",
                "${prefix}markdownpreviewer",
                "${prefix}mediainfo",
                "${prefix}nodejs",
                "${prefix}typescript",
                "${prefix}three.setup.installer",
                "${prefix}three.maple.image",
                "${prefix}mlkitocr",
                "${prefix}opencv",
                "${prefix}paddleocr.v5",
                "${prefix}paddleocr.v6.small",
                "${prefix}rapidocr.ppocrv6",
                "${prefix}offlinedocs",
                "${prefix}mcp.server",
                "${prefix}threestoneai",
            ),
            PluginInstallWizardCatalog.standardPackageNames,
        )
    }

    @Test
    fun categoriesFollowTheDraftOrder() {
        val order = PluginInstallWizardCatalog.entries.map { it.category }.distinct()
        assertEquals(
            listOf(
                PluginInstallWizardCategory.ACCESSIBILITY,
                PluginInstallWizardCategory.EDITOR,
                PluginInstallWizardCategory.UI,
                PluginInstallWizardCategory.APK_BUILDER,
                PluginInstallWizardCategory.FILE_MANAGER,
                PluginInstallWizardCategory.RUNTIME,
                PluginInstallWizardCategory.IMAGE,
                PluginInstallWizardCategory.COMPILER,
                PluginInstallWizardCategory.TEXT_CONVERSION,
                PluginInstallWizardCategory.DOCS,
                PluginInstallWizardCategory.PLAYER,
                PluginInstallWizardCategory.TOOLS,
            ),
            order,
        )
        assertFalse(PluginInstallWizardCatalog.entries.any { it.category == PluginInstallWizardCategory.OTHERS })
        assertEquals(47, PluginInstallWizardCatalog.entries.size)
    }

    @Test
    fun lookupByPackageName() {
        val prefix = "io.github.supermonster003.autojs6.plugin."
        assertEquals("Three Maple Image", PluginInstallWizardCatalog.find(prefix + "three.maple.image")?.fallbackTitle)
        assertEquals("Three Stack Archive", PluginInstallWizardCatalog.find(prefix + "three.stack.archive")?.fallbackTitle)
        assertEquals("Three Hanzi CC", PluginInstallWizardCatalog.find(prefix + "three.hanzi.cc")?.fallbackTitle)
        for (old in listOf("imageviewer", "imagetools", "archivemanager", "opencc")) {
            assertEquals(null, PluginInstallWizardCatalog.find(prefix + old))
        }
        val terminal = PluginInstallWizardCatalog.find("io.github.supermonster003.autojs6.plugin.three.shell.terminal")
        assertEquals(PluginInstallWizardCategory.TOOLS, terminal?.category)
        assertEquals("Three Shell Terminal", terminal?.fallbackTitle)
        val entry = PluginInstallWizardCatalog.find("io.github.supermonster003.autojs6.plugin.opencv")
        assertEquals(PluginInstallWizardCategory.IMAGE, entry?.category)
        assertTrue(entry?.inStandard == true)
        val agent = PluginInstallWizardCatalog.find("io.github.supermonster003.autojs6.plugin.three.stove.agent")
        assertEquals(PluginInstallWizardCategory.TOOLS, agent?.category)
        assertEquals("Three Stove Agent", agent?.fallbackTitle)
        assertEquals(false, agent?.inStandard)
        assertEquals(false, agent?.inMinimal)
        val amber = PluginInstallWizardCatalog.find("io.github.supermonster003.autojs6.plugin.three.amber.player")
        assertEquals(PluginInstallWizardCategory.PLAYER, amber?.category)
        assertEquals("Three Amber Player", amber?.fallbackTitle)
        val installer = PluginInstallWizardCatalog.find("io.github.supermonster003.autojs6.plugin.three.setup.installer")
        assertEquals(PluginInstallWizardCategory.TOOLS, installer?.category)
        assertEquals("Three Setup Installer", installer?.fallbackTitle)
        assertEquals(true, installer?.inStandard)
        assertEquals(false, installer?.inMinimal)
        assertFalse(PluginInstallWizardCatalog.contains("io.github.supermonster003.autojs6.plugin.videoplayer"))
        assertFalse(PluginInstallWizardCatalog.contains("com.example.unknown"))
    }

}
