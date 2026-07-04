package org.autojs.autojs.apkbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderLibPolicyTest {

    @Test
    fun rapidOcrUsesPluginVariantsForPackaging() {
        val rapidOcr = ApkBuilder.Lib.RAPID_OCR
        assertTrue(rapidOcr.isPlugin)
        assertTrue(rapidOcr.libsToInclude.isEmpty())
        assertTrue(rapidOcr.assetDirsToInclude.isEmpty())

        val variants = rapidOcr.toPluginPair().second.variants
        assertEquals(listOf("pp-ocrv3", "pp-ocrv6"), variants.map { it.variant })
        variants.forEach { variant ->
            assertEquals(listOf("libRapidOcr.so", "libonnxruntime.so"), variant.libsToInclude)
            assertTrue(variant.assetsToInclude.first.all { it.startsWith("assets/models/") })
            assertTrue(variant.assetsToInclude.second.isEmpty())
        }
    }

    @Test
    fun imageQuantIncludesOnlyRuntimeBridgeLibrary() {
        assertEquals(
            listOf("libpngquant_bridge.so"),
            ApkBuilder.Lib.IMAGE_QUANT.libsToInclude,
        )
    }

    @Test
    fun selectableAssetDirectoriesAreExcludedUntilSelected() {
        val defaultExcludedDirs = ApkBuilder.Lib.defaultAssetDirsToExclude.toSet()
        ApkBuilder.Lib.entries
            .flatMap { it.assetDirsToInclude }
            .map { it.removePrefix("assets/") }
            .forEach { assetDir ->
                assertTrue("$assetDir should be excluded by default", defaultExcludedDirs.contains(assetDir))
            }
    }
}
