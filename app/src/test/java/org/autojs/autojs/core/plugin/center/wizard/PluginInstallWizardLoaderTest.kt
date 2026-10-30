package org.autojs.autojs.core.plugin.center.wizard

import org.autojs.autojs.core.plugin.center.PluginIndexEntry
import org.autojs.autojs.core.plugin.center.PluginIndexRelease
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginInstallWizardLoaderTest {

    private val opencv = "io.github.supermonster003.autojs6.plugin.opencv"
    private val aceEditor = "io.github.supermonster003.autojs6.plugin.ace.editor"
    private val unknownFeatured = "io.github.supermonster003.autojs6.plugin.brand.new"
    private val unknownVariant = "io.github.supermonster003.autojs6.plugin.paddleocr.v5.server"

    @Test
    fun onlineIndexIsNeededWhenCacheIsEmptyOrIncomplete() {
        assertTrue(PluginInstallWizardLoader.needsOnlineIndex(emptyList()))
        assertTrue(PluginInstallWizardLoader.needsOnlineIndex(listOf(entry(opencv))))
        val complete = PluginInstallWizardCatalog.entries.map { entry(it.packageName) }
        assertFalse(PluginInstallWizardLoader.needsOnlineIndex(complete))
    }

    @Test
    fun itemsFollowCatalogOrderAndAppendUnknownFeaturedEntries() {
        val entries = listOf(
            entry(unknownVariant, title = "Paddle OCR (PP-OCRv5 Server)", isFeatured = false),
            entry(opencv, title = "OpenCV", release = release("https://example.com/opencv.apk", 12_345L)),
            entry(unknownFeatured, title = "Brand New"),
            entry(aceEditor, title = "Ace-Editor", release = release("https://example.com/ace.apk")),
            entry("third.party.pkg", source = PluginIndexSource.THIRD_PARTY),
        )

        val items = PluginInstallWizardLoader.buildItems(
            entries = entries,
            isInstalled = { it == aceEditor },
            deviceAbis = listOf("arm64-v8a"),
            pageSize = 4096L,
        )

        assertEquals(listOf(aceEditor, opencv, unknownFeatured), items.map { it.packageName })

        val ace = items[0]
        assertEquals("Ace-Editor", ace.title)
        assertEquals(PluginInstallWizardCategory.EDITOR, ace.category)
        assertTrue(ace.inMinimal && ace.inStandard)
        assertTrue(ace.isInstalled)
        assertFalse(ace.isSelectable)

        val cv = items[1]
        assertEquals(PluginInstallWizardCategory.IMAGE, cv.category)
        assertEquals("https://example.com/opencv.apk", cv.apkUrl)
        assertEquals(12_345L, cv.apkSizeBytes)
        assertTrue(cv.isSelectable)

        val extra = items[2]
        assertEquals(PluginInstallWizardCategory.OTHERS, extra.category)
        assertFalse(extra.inStandard)
        assertFalse(extra.hasInstallableApk)
        assertFalse(extra.isSelectable)
    }

    @Test
    fun indexTitleFallsBackToCatalogTitle() {
        val items = PluginInstallWizardLoader.buildItems(
            entries = listOf(entry(opencv, title = "")),
            isInstalled = { false },
            deviceAbis = listOf("arm64-v8a"),
            pageSize = 4096L,
        )
        assertEquals("OpenCV", items.single().title)
    }

    @Test
    fun paddleLiteRowFollowsTheOnnxRuntimePaddleOcrRow() {
        val prefix = "io.github.supermonster003.autojs6.plugin."
        val items = PluginInstallWizardLoader.buildItems(
            entries = listOf(
                entry(prefix + "paddleocr.v4.mobile"),
                entry(prefix + "paddleocr.v3.paddlelite", title = ""),
                entry(prefix + "paddleocr.v3"),
            ),
            isInstalled = { false },
            deviceAbis = listOf("arm64-v8a"),
            pageSize = 4096L,
        )
        assertEquals(
            listOf("paddleocr.v3", "paddleocr.v3.paddlelite", "paddleocr.v4.mobile").map { prefix + it },
            items.map { it.packageName },
        )
        val lite = items[1]
        assertEquals("Paddle OCR PP OCRv3 Paddle Lite", lite.title)
        assertEquals(PluginInstallWizardCategory.IMAGE, lite.category)
        assertFalse(lite.inStandard || lite.inMinimal)
    }

    @Test
    fun compatibilityFlagsComeFromAbisAndNativeAlignment() {
        val items = PluginInstallWizardLoader.buildItems(
            entries = listOf(
                entry(opencv, supportedAbis = listOf("x86_64"), release = release("https://example.com/a.apk", alignment = 4096L)),
                entry(aceEditor, supportedAbis = listOf("arm64-v8a"), release = release("https://example.com/b.apk", alignment = 16384L)),
            ),
            isInstalled = { false },
            deviceAbis = listOf("arm64-v8a"),
            pageSize = 16384L,
        )
        val cv = items.single { it.packageName == opencv }
        assertTrue(cv.isAbiMismatch)
        assertTrue(cv.isNativeAlignmentMismatch)
        assertTrue(cv.isPossiblyIncompatible)

        val ace = items.single { it.packageName == aceEditor }
        assertFalse(ace.isAbiMismatch)
        assertFalse(ace.isNativeAlignmentMismatch)
        assertFalse(ace.isPossiblyIncompatible)
    }

    private fun entry(
        packageName: String,
        title: String = packageName,
        isFeatured: Boolean = true,
        release: PluginIndexRelease? = null,
        supportedAbis: List<String>? = null,
        source: PluginIndexSource = PluginIndexSource.OFFICIAL,
    ) = PluginIndexEntry(
        packageName = packageName,
        title = title,
        isFeatured = isFeatured,
        releases = listOfNotNull(release),
        supportedAbis = supportedAbis,
        source = source,
    )

    private fun release(apkUrl: String, sizeBytes: Long? = null, alignment: Long? = null) = PluginIndexRelease(
        versionName = "1.0.0",
        versionCode = 1L,
        apkUrl = apkUrl,
        apkSizeBytes = sizeBytes,
        nativePageAlignment = alignment,
    )

}
