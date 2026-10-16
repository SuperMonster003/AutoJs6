package org.autojs.autojs.core.ui.inflater

import org.autojs.autojs.core.ui.inflater.ResourceQualifiers.Companion.DENSITY_ANY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Host roadmap `docs/dev/android-xml-attributes-roadmap.md` A7: qualifier parsing, the directories a configuration
 * rules out, and Android's precedence order for the rest (locale before width before orientation before night mode
 * before density before version; density picks the closest, scaling down preferred).
 *
 * zh-CN: 宿主路线图 A7: 限定符解析, 配置排除的目录, 以及其余目录按 Android 优先级的选取.
 */
class ResourceQualifiersTest {

    private fun q(name: String) = ResourceQualifiers.parse(name, "values")

    private fun choose(configuration: ResourceConfiguration, vararg directories: String): String? =
        ResourceQualifiers.best(directories.map { q(it)!! to it }, configuration)

    private val zhCnNight = ResourceConfiguration(locales = listOf(Locale.SIMPLIFIED_CHINESE), night = true, sdkInt = 33, widthDp = 400, heightDp = 800, smallestWidthDp = 400)

    @Test
    fun directoryNamesParse() {
        assertEquals(ResourceQualifiers(), q("values"))
        assertEquals(ResourceQualifiers(language = "zh", region = "CN"), q("values-zh-rCN"))
        assertEquals(ResourceQualifiers(language = "zh", script = "Hant", region = "TW"), q("values-b+zh+Hant+TW"))
        assertEquals(ResourceQualifiers(language = "en", night = true, landscape = true, sdk = 26), q("values-en-land-night-v26"))
        assertEquals(ResourceQualifiers(smallestWidthDp = 600, widthDp = 720, heightDp = 400), q("values-sw600dp-w720dp-h400dp"))
        assertEquals(ResourceQualifiers(density = 480), q("values-xxhdpi"))
        assertEquals(ResourceQualifiers(density = DENSITY_ANY, sdk = 21), q("values-anydpi-v21"))
        assertEquals(ResourceQualifiers(density = 420), q("values-420dpi"))
        assertEquals(ResourceQualifiers(rtl = true), q("values-ldrtl"))
        // Unsupported qualifiers, repeated ones and other types.
        assertNull(q("values-large"))
        assertNull(q("values-mcc310"))
        assertNull(q("values-night-notnight"))
        assertNull(q("values-rCN"))
        assertNull(ResourceQualifiers.parse("layout-land", "values"))
        assertNull(ResourceQualifiers.parse("valuesx", "values"))
    }

    @Test
    fun theConfigurationRulesDirectoriesOut() {
        assertFalse(q("values-zh")!!.contradicts(zhCnNight))
        assertFalse(q("values-zh-rCN")!!.contradicts(zhCnNight))
        assertTrue(q("values-zh-rTW")!!.contradicts(zhCnNight))
        assertTrue(q("values-en")!!.contradicts(zhCnNight))
        assertTrue(q("values-notnight")!!.contradicts(zhCnNight))
        assertTrue(q("values-land")!!.contradicts(zhCnNight))
        assertTrue(q("values-sw600dp")!!.contradicts(zhCnNight))
        assertFalse(q("values-w400dp")!!.contradicts(zhCnNight))
        assertTrue(q("values-v34")!!.contradicts(zhCnNight))
        assertFalse(q("values-xxxhdpi")!!.contradicts(zhCnNight))
        // Chinese without a script in the configuration: Hans for CN, Hant for TW.
        assertFalse(q("values-b+zh+Hans")!!.contradicts(zhCnNight))
        assertTrue(q("values-b+zh+Hant")!!.contradicts(zhCnNight))
        assertFalse(q("values-b+zh+Hant")!!.contradicts(zhCnNight.copy(locales = listOf(Locale.TRADITIONAL_CHINESE))))
    }

    @Test
    fun precedenceFollowsAndroid() {
        // A locale match beats night mode; a region match beats a language-only one.
        assertEquals("values-zh", choose(zhCnNight, "values", "values-night", "values-zh"))
        assertEquals("values-zh-rCN", choose(zhCnNight, "values-zh", "values-zh-rCN", "values-night"))
        // The first configuration locale with a match wins.
        val frThenZh = zhCnNight.copy(locales = listOf(Locale.FRANCE, Locale.SIMPLIFIED_CHINESE))
        assertEquals("values-fr", choose(frThenZh, "values-zh-rCN", "values-fr"))
        // Widths: the largest one reached; smallest width before width before orientation.
        assertEquals("values-w320dp", choose(zhCnNight, "values-w200dp", "values-w320dp", "values-w600dp"))
        assertEquals("values-sw360dp", choose(zhCnNight, "values-w320dp", "values-sw360dp", "values-night"))
        // Night mode when nothing earlier decides, then the newest version reached.
        assertEquals("values-night", choose(zhCnNight, "values", "values-night", "values-v26"))
        assertEquals("values-night-v31", choose(zhCnNight, "values-night", "values-night-v26", "values-night-v31", "values-night-v34"))
        assertEquals(null, choose(zhCnNight, "values-en", "values-notnight"))
    }

    @Test
    fun densityPrefersTheClosestScalingDown() {
        val xhdpi = ResourceConfiguration(densityDpi = 320)
        assertEquals("values-xhdpi", choose(xhdpi, "values-hdpi", "values-xhdpi", "values-xxhdpi"))
        assertEquals("values-xxhdpi", choose(xhdpi, "values-hdpi", "values-xxhdpi"))
        // Scaling a far higher density down loses to a slightly lower one.
        assertEquals("values-300dpi", choose(xhdpi, "values-300dpi", "values-xxxhdpi"))
        assertEquals("values-anydpi", choose(xhdpi, "values-xhdpi", "values-anydpi"))
        // An unqualified directory counts as mdpi.
        assertEquals("values-hdpi", choose(xhdpi, "values", "values-hdpi"))
        assertTrue(ResourceQualifiers.betterDensity(480, 240, 320))
        assertFalse(ResourceQualifiers.betterDensity(160, 240, 320))
    }
}
