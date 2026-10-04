package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginIconBackgroundPolicyTest {
    @Test fun threeColorsStayFixedAcrossInstallStatesAndOverrides() {
        for (name in listOf("three.hanzi.cc", "three.maple.image", "audioplayer", "threestoneai")) {
            val packageName = "io.github.supermonster003.autojs6.plugin.$name"
            assertEquals(0xfffafafa.toInt(), PluginIconBackgroundPolicy.resolve(packageName, false, "#ff0000", "transparent", 123))
            assertEquals(0xff212121.toInt(), PluginIconBackgroundPolicy.resolve(packageName, true, null, null))
        }
    }

    @Test fun customColorsAreIndependentFromPngAndTransparentIsExplicit() {
        assertEquals(0xff336699.toInt(), PluginIconBackgroundPolicy.resolve("other.plugin", false, "#336699", "transparent"))
        assertEquals(0, PluginIconBackgroundPolicy.resolve("other.plugin", true, "#336699", "transparent", 123))
        assertEquals(123, PluginIconBackgroundPolicy.resolve("other.plugin", false, null, null, 123))
        assertEquals(0, PluginIconBackgroundPolicy.resolve("other.plugin", false, "not-a-color", null))
        assertNull(PluginIconBackgroundPolicy.normalized("#123456;anything"))
    }
}
