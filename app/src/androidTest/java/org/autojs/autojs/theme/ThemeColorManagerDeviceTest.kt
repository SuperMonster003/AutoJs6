package org.autojs.autojs.theme

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorManagerDeviceTest {

    @Test fun aThemeCallbackCanCreateAnotherThemedWidgetWithoutCrashing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = ThemeColorManager.currentThemeColor
            var armed = false
            var registered = false
            val childColors = mutableListOf<Int>()
            val tailColors = mutableListOf<Int>()
            val child = ThemeColorMutable { childColors += it.colorPrimary }
            val parent = ThemeColorMutable {
                if (armed && !registered) {
                    registered = true
                    ThemeColorManager.add(child)
                }
            }
            val tail = ThemeColorMutable { tailColors += it.colorPrimary }
            ThemeColorManager.add(parent)
            ThemeColorManager.add(tail)
            tailColors.clear()
            try {
                armed = true
                val first = 0xff123456.toInt()
                val second = 0xff654321.toInt()
                ThemeColorManager.setThemeColor(ThemeColor(first))
                assertEquals("The newly registered widget gets the active color immediately", listOf(first), childColors)
                assertEquals("The original traversal must finish", listOf(first), tailColors)
                ThemeColorManager.setThemeColor(ThemeColor(second))
                assertEquals(listOf(first, second), childColors)
                assertEquals(listOf(first, second), tailColors)
            } finally {
                armed = false
                ThemeColorManager.setThemeColor(original)
            }
        }
    }
}
