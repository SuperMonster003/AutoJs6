package org.autojs.autojs.runtime.api.modules.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PinyinOptionsTest {
    @Test fun numericStylesAndModesAcceptJsonAndRhinoNumberRepresentations() {
        for (style in PinyinOptions.PinyinStyle.entries) {
            for (number in listOf<Number>(style.value, style.value.toLong(), style.value.toDouble(), style.value.toFloat())) {
                assertEquals(style.value, PinyinOptions.parseStyle(number))
            }
        }
        for (mode in PinyinOptions.PinyinMode.entries) {
            for (number in listOf<Number>(mode.value, mode.value.toLong(), mode.value.toDouble(), mode.value.toFloat())) {
                assertEquals(mode.value, PinyinOptions.parseMode(number))
            }
        }
    }

    @Test fun invalidNumericConstantsAreRejectedWithoutTruncation() {
        for (number in listOf(-1.0, 0.5, 6.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 4294967296.0)) {
            assertThrows(IllegalArgumentException::class.java) { PinyinOptions.parseStyle(number) }
            assertThrows(IllegalArgumentException::class.java) { PinyinOptions.parseMode(number) }
        }
    }
}
