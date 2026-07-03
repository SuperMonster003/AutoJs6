package org.autojs.autojs.apkbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderLibPolicyTest {

    @Test
    fun rapidOcrIncludesRuntimeModelAssets() {
        assertTrue(ApkBuilder.Lib.RAPID_OCR.assetDirsToInclude.contains("assets/models"))
        assertFalse(ApkBuilder.Lib.RAPID_OCR.assetDirsToInclude.contains("assets/labels"))
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
