package org.autojs.autojs.theme.intelligent

import org.autojs.autojs.theme.color.ColorMetrics
import org.autojs.autojs.theme.color.OkColor
import org.autojs.autojs.theme.color.ThemeColorDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentAutoFillTest {

    private val pink = 0xFFF8BBD0.toInt()
    private val teal = 0xFF00897B.toInt()

    @Test
    fun dayAndNightGetTheContrastPair() {
        val filled = IntelligentAutoFill.fill(listOf(StateKeys.DAY, StateKeys.NIGHT), mapOf(StateKeys.DAY to pink, StateKeys.NIGHT to null))!!
        // The filled day color stays as chosen; night is the nearest color with 3:1 against the night background.
        assertEquals(pink, filled[StateKeys.DAY])
        assertEquals(ThemeColorDerivation.dayNightPair(pink).second, filled[StateKeys.NIGHT])
        assertTrue(ColorMetrics.contrastRatio(filled[StateKeys.NIGHT]!!, ThemeColorDerivation.WINDOW_BACKGROUND_NIGHT) >= 3.0)
        // Filling from night works too.
        val fromNight = IntelligentAutoFill.fill(listOf(StateKeys.DAY, StateKeys.NIGHT), mapOf(StateKeys.NIGHT to teal))!!
        assertEquals(teal, fromNight[StateKeys.NIGHT])
        assertEquals(ThemeColorDerivation.dayNightPair(teal).first, fromNight[StateKeys.DAY])
    }

    @Test
    fun otherSourcesGetATonalScale() {
        val keys = (0 until 4).map(StateKeys::period)
        val filled = IntelligentAutoFill.fill(keys, mapOf("period:2" to teal))!!
        assertEquals(keys, filled.keys.toList())
        assertEquals(teal, filled["period:2"])
        val lightness = listOf("period:0", "period:1", "period:3").map { OkColor.toOkLch(filled[it]!!).l }
        assertTrue("light to dark: $lightness", lightness[0] > lightness[1] && lightness[1] > lightness[2])
    }

    @Test
    fun seedsGenerateLibraries() {
        val harmony = IntelligentSeedGenerator.generate(teal, IntelligentSeedGenerator.Scheme.Harmony(org.autojs.autojs.theme.color.ColorHarmony.Type.TRIADIC), nightMode = true, now = 1L) { index, hueOffset -> "$index:$hueOffset" }
        assertEquals(listOf("0:0", "1:120", "2:240"), harmony.groups.map { it.name })
        assertEquals(StateSource.NightMode, harmony.config.stateSource)
        assertEquals(listOf(RotationTrigger.StateChange), harmony.config.rotation)
        assertEquals(listOf(-1L, -2L, -3L), harmony.groups.map { it.id })
        assertEquals(teal, harmony.groups.first().states[StateKeys.DAY]?.primary)
        harmony.groups.forEach { group ->
            assertTrue(ColorMetrics.contrastRatio(group.states[StateKeys.NIGHT]!!.primary!!, ThemeColorDerivation.WINDOW_BACKGROUND_NIGHT) >= 3.0)
        }
        val tones = IntelligentSeedGenerator.generate(teal, IntelligentSeedGenerator.Scheme.Tones(), nightMode = false)
        assertEquals(StateSource.None, tones.config.stateSource)
        assertTrue(tones.config.rotation.isEmpty())
        assertEquals(5, tones.groups.size)
        assertTrue(tones.groups.all { it.states.keys == setOf(StateKeys.DEFAULT) && it.name == null })
    }

    @Test
    fun colorsBecomeOneGroupEach() {
        val result = IntelligentSeedGenerator.fromColors(listOf(teal, pink, teal and 0x80FFFFFF.toInt(), pink), nightMode = true, now = 1L, names = listOf("A", "B", "C", "D"))
        // Repeated opaque colors are dropped, names follow the kept colors. zh-CN: 重复的不透明颜色被去除, 名称跟随保留的颜色.
        assertEquals(listOf("A", "B"), result.groups.map { it.name })
        assertEquals(listOf(teal, pink), result.groups.map { it.states[StateKeys.DAY]?.primary })
        assertEquals(ThemeColorDerivation.dayNightPair(pink).second, result.groups[1].states[StateKeys.NIGHT]?.primary)
        assertEquals(listOf(-1L, -2L), result.groups.map { it.id })
        assertEquals(StateSource.None, IntelligentSeedGenerator.fromColors(listOf(teal), nightMode = false).config.stateSource)
    }

    @Test
    fun nothingToFillFrom() {
        assertNull(IntelligentAutoFill.fill(listOf(StateKeys.DAY, StateKeys.NIGHT), emptyMap()))
        assertEquals(mapOf(StateKeys.DEFAULT to teal), IntelligentAutoFill.fill(listOf(StateKeys.DEFAULT), mapOf(StateKeys.DEFAULT to teal)))
    }

}
