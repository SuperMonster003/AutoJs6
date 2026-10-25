package org.autojs.autojs.runtime.api.modules.colors

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsComponentTest {

    private fun rgb(vararg components: Any?) = Colors.toUnit8RgbList(arrayOf(*components))

    @Test
    fun integerComponentsPassThrough() {
        assertEquals(listOf(64, 32, 224), rgb(64, 32, 224))
        assertEquals(listOf(255, 0, 7), rgb(300, 0, 7.4))
    }

    @Test
    fun fractionalComponentsAreScaledTo255() {
        /* Documented: colors.rgb(0.5, 0.25, 0.125) equals colors.rgb(128, 64, 32). */
        assertEquals(listOf(128, 64, 32), rgb(0.5, 0.25, 0.125))
        assertEquals(listOf(128, 64, 26), rgb(0.5, 0.25, 0.1))
    }

    @Test
    fun percentageStringsAreScaledTo255() {
        assertEquals(listOf(128, 64, 32), rgb("50%", "25%", "12.5%"))
        assertEquals(listOf(128, 64, 32), rgb(0.5, "25%", 32))
    }

    @Test
    fun oneIsAPercentageOnlyWhenEveryComponentIsAtMostOneAndNotAllAreOne() {
        assertEquals(listOf(255, 51, 128), rgb(1, 0.2, 0.5))
        assertEquals(listOf(1, 51, 224), rgb(1, 0.2, 224))
        assertEquals(listOf(1, 160, 224), rgb(1, 160, 224))
        assertEquals(listOf(1, 1, 1), rgb(1, 1, 1))
        assertEquals(listOf(255, 255, 128), rgb(1, 1, 0.5))
        assertEquals(listOf(255, 255, 128), rgb(1.0, 1.0, 0.5))
    }

    @Test
    fun zeroComponentsStayZero() {
        assertEquals(listOf(0, 0, 0), rgb(0, 0, 0))
        assertEquals(listOf(0, 0, 255), rgb(0, 0, 1.0))
    }
}
