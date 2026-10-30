package org.autojs.autojs.apkbuilder

import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderLibPolicyTest {

    @Test
    fun openCvUsesVersionLockedPluginRuntimeForPackaging() {
        val openCv = ApkBuilder.Lib.OPENCV
        assertTrue(openCv.isPlugin)
        assertTrue(openCv.libsToInclude.isEmpty())
        assertTrue(openCv.assetDirsToInclude.isEmpty())

        val plugin = openCv.toPluginPair().second
        assertEquals(OpenCvPluginContract.ACTION_RUNTIME, plugin.action)
        assertEquals(OpenCvPluginContract.ENGINE, plugin.engine)
        assertEquals(listOf(OpenCvPluginContract.VARIANT_4_8_0), plugin.variants.map { it.variant })
        assertEquals(
            listOf("libc++_shared.so", OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME),
            plugin.variants.single().libsToInclude,
        )
    }

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
        // Older releases link the shared OpenCV runtime; it is copied only when libRapidOcr.so needs it.
        ApkBuildLibrary.RAPID_OCR.requirePlugin().variants.forEach { variant ->
            assertTrue(variant.optionalLibsToInclude.isEmpty())
            assertEquals(listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME), variant.neededOnlyLibsToInclude)
        }
    }

    @Test
    fun paddleOcrPackagesOnlyTheEmbeddedPpOcrV3Engines() {
        val paddleOcr = ApkBuilder.Lib.PADDLE_OCR
        assertTrue(paddleOcr.isPlugin)
        assertTrue(paddleOcr.libsToInclude.isEmpty())
        assertTrue(paddleOcr.assetDirsToInclude.isEmpty())

        // PP-OCRv4 / v5 / v6 plugins run ONNX pipelines in their own processes (roadmap D10);
        // PP-OCRv3 is packaged on ONNX Runtime first, then on Paddle Lite (Paddle OCR PP-OCRv3 roadmap D21).
        val variants = paddleOcr.toPluginPair().second.variants
        assertEquals(listOf("v3", "v3-paddle-lite"), variants.map { it.variant })

        val (onnx, lite) = ApkBuildLibrary.PADDLE_OCR.requirePlugin().variants
        assertEquals(listOf("libonnxruntime.so", "libonnxruntime4j_jni.so", "libc++_shared.so"), onnx.libsToInclude)
        assertEquals(listOf("assets/models/ppocrv3/"), onnx.assetsToInclude.first)
        assertEquals(listOf("assets/models/ppocrv3-slim/"), onnx.assetsToInclude.second)
        assertEquals(listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME), onnx.neededOnlyLibsToInclude)
        assertTrue(onnx.requiresOpenCvPluginRuntime)

        assertEquals(
            listOf(
                "libc++_shared.so",
                "libpaddle_light_api_shared.so",
                "libNative.so",
            ),
            lite.libsToInclude,
        )
        assertEquals(listOf("assets/labels/ppocr_keys_v1.txt", "assets/models/ocr_v3_for_cpu/"), lite.assetsToInclude.first)
        assertEquals(listOf("assets/models/ocr_v3_for_cpu(slim)/"), lite.assetsToInclude.second)
        assertEquals(listOf(OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME), lite.neededOnlyLibsToInclude)
        assertFalse(lite.requiresOpenCvPluginRuntime)
    }

    @Test
    fun paddleOcrPluginSelectionIgnoresNewerUnsupportedVariants() {
        val supported = ApkBuildLibrary.PADDLE_OCR.requirePlugin().variants
        val v3 = supported.first()
        val v6 = ApkPluginLibraryVariant(variant = "v6")
        // Only matched variants reach the selector; an unmatched v6 can no longer outrank v3.
        assertEquals("v3-plugin" to v3, ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("v3-plugin" to v3), supported))
        assertEquals("v3-plugin", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("v6-plugin" to v6, "v3-plugin" to v3), supported)?.first)
    }

    @Test
    fun paddleOcrPluginSelectionPrefersOnnxRuntimeOverPaddleLite() {
        val supported = ApkBuildLibrary.PADDLE_OCR.requirePlugin().variants
        val (onnx, lite) = supported
        // Both report the number 3, so the catalog order decides regardless of the query order.
        assertEquals("onnx", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("lite" to lite, "onnx" to onnx), supported)?.first)
        assertEquals("onnx", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("onnx" to onnx, "lite" to lite), supported)?.first)
        assertEquals("lite", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("lite" to lite), supported)?.first)

        val compatSupported = ApkBuilder.Lib.PADDLE_OCR.toPluginPair().second.variants
        val (compatOnnx, compatLite) = compatSupported
        assertEquals("onnx", ApkBuilder.selectBestPluginVariantCandidate(listOf("lite" to compatLite, "onnx" to compatOnnx), compatSupported)?.first)
    }

    @Test
    fun rapidOcrPluginSelectionStillPrefersTheNewerModel() {
        val supported = ApkBuildLibrary.RAPID_OCR.requirePlugin().variants
        val (v3, v6) = supported
        assertEquals("v6", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("v3" to v3, "v6" to v6), supported)?.first)
        assertEquals("v6", ApkBuildLibraryCatalog.selectBestPluginVariantCandidate(listOf("v6" to v6, "v3" to v3), supported)?.first)
    }

    @Test
    fun imageQuantUsesVersionLockedPluginRuntimeForPackaging() {
        val imageQuant = ApkBuilder.Lib.IMAGE_QUANT
        assertTrue(imageQuant.isPlugin)
        assertTrue(imageQuant.libsToInclude.isEmpty())
        assertTrue(imageQuant.assetDirsToInclude.isEmpty())

        val plugin = imageQuant.toPluginPair().second
        assertEquals(ImageQuantPluginContract.ACTION_RUNTIME, plugin.action)
        assertEquals(ImageQuantPluginContract.ENGINE, plugin.engine)
        assertEquals(listOf(ImageQuantPluginContract.VARIANT_2_17_0), plugin.variants.map { it.variant })
        assertEquals(
            listOf(ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME),
            plugin.variants.single().libsToInclude,
        )
        assertEquals(
            listOf("assets/licenses/imagequant/"),
            plugin.variants.single().assetsToInclude.first,
        )
        assertTrue(plugin.variants.single().assetsToInclude.second.isEmpty())
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
