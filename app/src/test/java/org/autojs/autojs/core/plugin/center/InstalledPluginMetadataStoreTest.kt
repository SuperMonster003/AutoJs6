package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledPluginMetadataStoreTest {

    @Test
    fun jsonRoundTripPreservesEntries() {
        val entries = mapOf(
            "org.autojs.plugin.full" to entryOf(
                packageName = "org.autojs.plugin.full",
                versionCode = 30L,
                lastUpdateTime = 1_726_000_000_000L,
                languageTag = "zh-CN",
                title = "OCR Plugin",
                description = "Recognizes text on screen.",
                author = "SuperMonster003",
                instruction = "Long-press to configure.",
                versionDate = "2026-08-01",
                requiresHostVersion = 6_800L,
                supportedAbis = listOf("arm64-v8a", "x86_64"),
                engine = "mlkit-ocr",
                variant = "bundled",
                engineId = "mlkit-ocr-google",
            ),
            "org.autojs.plugin.minimal" to entryOf(
                packageName = "org.autojs.plugin.minimal",
            ),
        )

        val restored = InstalledPluginMetadataStore.parseJson(
            InstalledPluginMetadataStore.toJson(entries.values),
        )

        assertEquals(entries, restored)
    }

    @Test
    fun parseJsonToleratesMalformedContent() {
        assertTrue(InstalledPluginMetadataStore.parseJson("").isEmpty())
        assertTrue(InstalledPluginMetadataStore.parseJson("not a json document").isEmpty())
        assertTrue(InstalledPluginMetadataStore.parseJson("{}").isEmpty())
        assertTrue(InstalledPluginMetadataStore.parseJson("""{"entries":null}""").isEmpty())

        val partiallyBroken = InstalledPluginMetadataStore.parseJson(
            """
            {
              "entries": [
                null,
                {"title": "No package name"},
                {"packageName": "  "},
                {"packageName": "org.autojs.plugin.valid", "title": "Valid", "supportedAbis": [null, "arm64-v8a"]}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("org.autojs.plugin.valid"), partiallyBroken.keys)
        val entry = partiallyBroken.getValue("org.autojs.plugin.valid")
        assertEquals("Valid", entry.title)
        assertEquals(listOf("arm64-v8a"), entry.supportedAbis)
        assertNull(entry.versionCode)
    }

    @Test
    fun versionCoupledValidityRequiresExactPackageMatch() {
        val entry = entryOf(
            packageName = "org.autojs.plugin.sample",
            versionCode = 12L,
            lastUpdateTime = 1_000L,
        )

        assertTrue(entry.matchesInstalledPackage(versionCode = 12L, lastUpdateTime = 1_000L))
        assertFalse(entry.matchesInstalledPackage(versionCode = 13L, lastUpdateTime = 1_000L))
        assertFalse(entry.matchesInstalledPackage(versionCode = 12L, lastUpdateTime = 2_000L))
        assertFalse(entry.matchesInstalledPackage(versionCode = null, lastUpdateTime = 1_000L))
        assertFalse(entry.matchesInstalledPackage(versionCode = 12L, lastUpdateTime = null))

        val entryWithoutFingerprint = entryOf(packageName = "org.autojs.plugin.sample")
        assertFalse(entryWithoutFingerprint.matchesInstalledPackage(versionCode = null, lastUpdateTime = null))
    }

    @Test
    fun mergeEntriesRefreshesRetainsAndPrunes() {
        val staleA = entryOf(packageName = "org.autojs.plugin.a", title = "A stale")
        val freshA = entryOf(packageName = "org.autojs.plugin.a", title = "A fresh")
        val retainedB = entryOf(packageName = "org.autojs.plugin.b", title = "B kept while probe fails")
        val uninstalledC = entryOf(packageName = "org.autojs.plugin.c", title = "C uninstalled")

        val merged = InstalledPluginMetadataStore.mergeEntries(
            existing = mapOf(
                staleA.packageName to staleA,
                retainedB.packageName to retainedB,
                uninstalledC.packageName to uninstalledC,
            ),
            fresh = listOf(freshA),
            retainedPackages = setOf("org.autojs.plugin.a", "org.autojs.plugin.b"),
        )

        assertEquals(
            mapOf(
                freshA.packageName to freshA,
                retainedB.packageName to retainedB,
            ),
            merged,
        )
    }

    private fun entryOf(
        packageName: String,
        versionCode: Long? = null,
        lastUpdateTime: Long? = null,
        languageTag: String? = null,
        title: String? = null,
        description: String? = null,
        author: String? = null,
        instruction: String? = null,
        versionDate: String? = null,
        requiresHostVersion: Long? = null,
        supportedAbis: List<String>? = null,
        engine: String? = null,
        variant: String? = null,
        engineId: String? = null,
    ) = InstalledPluginMetadataStore.Entry(
        packageName = packageName,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        languageTag = languageTag,
        title = title,
        description = description,
        author = author,
        instruction = instruction,
        versionDate = versionDate,
        requiresHostVersion = requiresHostVersion,
        supportedAbis = supportedAbis,
        engine = engine,
        variant = variant,
        engineId = engineId,
    )
}
