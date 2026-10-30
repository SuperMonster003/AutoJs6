package org.autojs.autojs.core.plugin.ocr

import org.autojs.plugin.common.api.PluginInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picking an OCR plugin without a stored priority (D22) and with an explicit profile (D23), on fake [PluginInfo] lists.
 * zh-CN: 用假 [PluginInfo] 列表验证没有已存优先级时的插件选择 (D22) 与显式 profile 的匹配 (D23).
 */
class OcrPluginSelectionTest {

    private class Installed(val packageName: String, val info: PluginInfo, val profile: String?, val recommended: Boolean = false)

    private fun installed(packageName: String, id: String, variant: String, profile: String?, recommended: Boolean = false, engine: String = "paddle-ocr") =
        Installed(
            packageName = packageName,
            info = PluginInfo().apply {
                this.id = id
                this.engine = engine
                this.variant = variant
            },
            profile = profile,
            recommended = recommended,
        )

    private val zh = installed(PACKAGE_V3, "paddle-ocr-pp-ocrv3", "v3", "zh", recommended = true)
    private val ja = installed("$PACKAGE_V3.ja", "paddle-ocr-pp-ocrv3-ja", "v3", "ja")
    private val en = installed("$PACKAGE_V3.en", "paddle-ocr-pp-ocrv3-en", "v3", "en")
    private val lite = installed("$PACKAGE_V3.paddlelite", "paddle-ocr-pp-ocrv3-paddle-lite", "v3-paddle-lite", null)
    private val v4MobileEn = installed("io.github.supermonster003.autojs6.plugin.paddleocr.v4.mobileen", "paddle-ocr-pp-ocrv4-mobile-en", "v4", "Mobile EN")
    private val v5 = installed("io.github.supermonster003.autojs6.plugin.paddleocr.v5", "paddle-ocr-pp-ocrv5", "v5", "Mobile")
    private val rapid = installed("io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv3", "rapid-ocr-pp-ocrv3", "pp-ocrv3", null, engine = "rapid-ocr")

    private val everything = listOf(lite, en, ja, zh, v4MobileEn, v5, rapid)

    private fun profileOf(info: PluginInfo): String? = everything.single { it.info === info }.profile

    private fun selectDefault(candidates: List<Installed>): Installed? = OcrPluginSelection.selectDefault(
        candidates = candidates,
        pluginInfoOf = { it.info },
        packageNameOf = { it.packageName },
        recommendedDefaultOf = { info -> everything.single { it.info === info }.recommended },
    )

    private fun matching(engine: String? = null, variant: String? = null, profile: String? = null) = everything.filter {
        OcrPluginSelection.matches(it.info, engineId = null, engine = engine, variant = variant, profile = profile, modelProfileOf = ::profileOf)
    }

    @Test
    fun `equal variant numbers prefer the recommended default over package names and flavors`() {
        assertEquals(zh, selectDefault(listOf(lite, en, ja, zh)))
        assertEquals(zh, selectDefault(listOf(zh, ja, en, lite)))
        // ONNX Runtime "v3" and Paddle Lite "v3-paddle-lite" both weigh 3, so the recommended one decides.
        assertEquals(zh, selectDefault(listOf(lite, zh)))
    }

    @Test
    fun `without a recommended default the smallest package name wins`() {
        assertEquals(en, selectDefault(listOf(lite, ja, en)))
        assertEquals(ja, selectDefault(listOf(lite, ja)))
    }

    @Test
    fun `a higher variant number still comes first`() {
        assertEquals(v5, selectDefault(listOf(zh, v5, v4MobileEn, lite)))
        assertEquals(v4MobileEn, selectDefault(listOf(zh, v4MobileEn)))
    }

    @Test
    fun `an explicit profile only matches plugins declaring it`() {
        assertEquals(listOf(ja), matching(engine = "paddle-ocr", profile = "ja"))
        assertEquals(listOf(ja), matching(engine = "paddle-ocr", profile = " JA "))
        assertEquals(emptyList<Installed>(), matching(engine = "paddle-ocr", profile = "ko"))
        // Plugins that do not declare a model profile (Paddle Lite, Rapid OCR) never match an explicit one.
        assertEquals(emptyList<Installed>(), matching(variant = "v3-paddle-lite", profile = "zh"))
        assertEquals(listOf(zh), matching(profile = "zh"))
    }

    @Test
    fun `profiles compare case-insensitively with spaces, underscores and hyphens alike`() {
        assertEquals(listOf(v4MobileEn), matching(profile = "mobile-en"))
        assertEquals(listOf(v4MobileEn), matching(profile = "Mobile_EN"))
        assertEquals(listOf(v5), matching(profile = "mobile"))
    }

    @Test
    fun `the profile makes the criteria explicit`() {
        assertTrue(OcrPluginSelection.hasExplicitCriteria(null, null, null, "ja"))
        assertFalse(OcrPluginSelection.hasExplicitCriteria(null, null, null, null))
    }

    @Test
    fun `a matching profile narrows the default choice`() {
        assertEquals(ja, selectDefault(matching(engine = "paddle-ocr", profile = "ja")))
        assertEquals(zh, selectDefault(matching(engine = "paddle-ocr", variant = "v3")))
    }

    @Test
    fun `installed profiles follow the other criteria`() {
        val infos = everything.map { it.info }
        assertEquals(
            listOf("en", "ja", "zh", "Mobile EN", "Mobile"),
            OcrPluginSelection.installedProfiles(infos, engineId = null, engine = "paddle-ocr", variant = null, modelProfileOf = ::profileOf),
        )
        assertEquals(
            listOf("en", "ja", "zh"),
            OcrPluginSelection.installedProfiles(infos, engineId = null, engine = "paddle-ocr", variant = "v3", modelProfileOf = ::profileOf),
        )
        assertEquals(
            emptyList<String>(),
            OcrPluginSelection.installedProfiles(infos, engineId = null, engine = "rapid-ocr", variant = null, modelProfileOf = ::profileOf),
        )
    }

    private companion object {
        const val PACKAGE_V3 = "io.github.supermonster003.autojs6.plugin.paddleocr.v3"
    }

}
