package org.autojs.autojs.core.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ConsoleLogFormatTest {

    private val locale = Locale.ROOT

    private fun withUtc(block: () -> Unit) {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun levelCharsFollowAndroidLogLevels() {
        assertEquals("V", ConsoleLogFormat.levelChar(2))
        assertEquals("D", ConsoleLogFormat.levelChar(3))
        assertEquals("I", ConsoleLogFormat.levelChar(4))
        assertEquals("W", ConsoleLogFormat.levelChar(5))
        assertEquals("E", ConsoleLogFormat.levelChar(6))
        assertEquals("A", ConsoleLogFormat.levelChar(7))
        assertEquals("", ConsoleLogFormat.levelChar(42))
    }

    @Test
    fun prefixUsesLegacyLayout() = withUtc {
        // 1970-01-01T01:02:03.456Z
        val timestamp = 3_723_456L
        assertEquals("01:02:03.456/I: ", ConsoleLogFormat.prefix(timestamp, 4, ConsoleLogFormat.DEFAULT_TIME_PATTERN, locale))
    }

    @Test
    fun formatLineHonoursVisibilityAndPattern() = withUtc {
        val timestamp = 3_723_456L
        assertEquals("hello", ConsoleLogFormat.formatLine(timestamp, 3, "hello", timeVisible = false, locale = locale))
        assertEquals("01:02:03/D: hello", ConsoleLogFormat.formatLine(timestamp, 3, "hello", true, "HH:mm:ss", locale))
    }

    @Test
    fun invalidPatternsFallBackToDefault() {
        assertFalse(ConsoleLogFormat.isValidTimePattern(null))
        assertFalse(ConsoleLogFormat.isValidTimePattern("   "))
        assertFalse(ConsoleLogFormat.isValidTimePattern("HH:mm:ss 'unterminated"))
        assertFalse(ConsoleLogFormat.isValidTimePattern("y".repeat(65)))
        assertTrue(ConsoleLogFormat.isValidTimePattern("yyyy-MM-dd HH:mm:ss.SSS"))
        assertEquals(ConsoleLogFormat.DEFAULT_TIME_PATTERN, ConsoleLogFormat.normalizeTimePattern("HH:mm:ss 'unterminated"))
        assertEquals("HH:mm", ConsoleLogFormat.normalizeTimePattern(" HH:mm "))
    }

    @Test
    fun presetsAreAllValid() {
        ConsoleLogFormat.PRESET_TIME_PATTERNS.forEach { pattern ->
            assertTrue(pattern, ConsoleLogFormat.isValidTimePattern(pattern))
        }
        assertEquals(ConsoleLogFormat.DEFAULT_TIME_PATTERN, ConsoleLogFormat.PRESET_TIME_PATTERNS.first())
    }

}
