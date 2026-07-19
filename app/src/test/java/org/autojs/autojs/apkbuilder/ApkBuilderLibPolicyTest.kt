package org.autojs.autojs.apkbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderLibPolicyTest {

    @Test
    fun installedNativeLibraryDirectoryMustMatchRequestedAbi() {
        assertTrue(ApkBuilder.nativeLibraryDirectoryMatchesAbi("arm64", listOf("arm64-v8a")))
        assertTrue(ApkBuilder.nativeLibraryDirectoryMatchesAbi("arm", listOf("armeabi-v7a")))
        assertTrue(ApkBuilder.nativeLibraryDirectoryMatchesAbi("x86_64", listOf("x86_64")))
        assertFalse(ApkBuilder.nativeLibraryDirectoryMatchesAbi("arm64", listOf("armeabi-v7a", "arm")))
        assertFalse(ApkBuilder.nativeLibraryDirectoryMatchesAbi("arm", listOf("arm64-v8a", "arm64")))
    }

    @Test
    fun mlKitOcrUsesPluginVariantForPackaging() {
        val mlKitOcr = ApkBuilder.Lib.MLKIT_OCR
        assertTrue(mlKitOcr.isPlugin)
        assertTrue(mlKitOcr.libsToInclude.isEmpty())
        assertTrue(mlKitOcr.assetDirsToInclude.isEmpty())

        val variants = mlKitOcr.toPluginPair().second.variants
        assertEquals(listOf("chinese"), variants.map { it.variant })
        assertEquals(listOf("libmlkit_google_ocr_pipeline.so"), variants.single().libsToInclude)
        assertEquals(listOf("assets/mlkit-google-ocr-models/"), variants.single().assetsToInclude.first)
        assertTrue(variants.single().assetsToInclude.second.isEmpty())
    }

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
    fun paddleOcrUsesPluginVariantsForPackaging() {
        val paddleOcr = ApkBuilder.Lib.PADDLE_OCR
        assertTrue(paddleOcr.isPlugin)
        assertTrue(paddleOcr.libsToInclude.isEmpty())
        assertTrue(paddleOcr.assetDirsToInclude.isEmpty())

        val variants = paddleOcr.toPluginPair().second.variants
        assertEquals(listOf("v3", "v4", "v5", "v6"), variants.map { it.variant })

        val v3 = variants.first { it.variant == "v3" }
        assertEquals(
            listOf(
                "libc++_shared.so",
                "libpaddle_light_api_shared.so",
                "libNative.so",
                "libopencv_java4.so",
            ),
            v3.libsToInclude,
        )
        assertEquals(listOf("assets/labels/ppocr_keys_v1.txt", "assets/models/ocr_v3_for_cpu/"), v3.assetsToInclude.first)
        assertEquals(listOf("assets/models/ocr_v3_for_cpu(slim)/"), v3.assetsToInclude.second)

        variants.filter { it.variant in setOf("v4", "v5", "v6") }.forEach { variant ->
            assertEquals(
                listOf(
                    "libc++_shared.so",
                    "libonnxruntime.so",
                    "libonnxruntime4j_jni.so",
                    "libopencv_java4.so",
                ),
                variant.libsToInclude,
            )
            assertEquals(listOf("assets/models/"), variant.assetsToInclude.first)
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
