package org.autojs.autojs.core.console.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class PatternLayoutTest {

    // 1970-01-01T01:02:03.456Z
    private val timestamp = 3_723_456L

    private fun event(
        level: Level = Level.INFO,
        message: String = "hello",
        loggerName: String = "org.autojs.autojs.core.console.GlobalConsole",
        threadName: String = "main",
    ) = LogEvent(level, message, loggerName, loggerName, threadName, timestamp)

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
    fun defaultPatternIsMessageAndNewline() {
        assertEquals("hello\n", PatternLayout().format(event()))
        assertEquals(PatternLayout.DEFAULT_PATTERN, PatternLayout().pattern)
    }

    @Test
    fun legacyDefaultFilePatternMatchesLog4jLayout() = withUtc {
        val layout = PatternLayout(LogConfigurator.DEFAULT_FILE_PATTERN)
        assertEquals(
            "1970-01-01 01:02:03,456 - [INFO::org.autojs.autojs.core.console.GlobalConsole::org.autojs.autojs.core.console.GlobalConsole] - hello\n",
            layout.format(event()),
        )
    }

    @Test
    fun dateConversionSupportsNamedAndCustomFormats() = withUtc {
        assertEquals("1970-01-01 01:02:03,456", PatternLayout("%d").format(event()))
        assertEquals("1970-01-01 01:02:03,456", PatternLayout("%d{ISO8601}").format(event()))
        assertEquals("01:02:03,456", PatternLayout("%d{ABSOLUTE}").format(event()))
        assertEquals("01 Jan 1970 01:02:03,456", PatternLayout("%d{DATE}").format(event()))
        assertEquals("01:02", PatternLayout("%d{HH:mm}").format(event()))
        // A broken custom format falls back to ISO8601 instead of throwing.
        assertEquals("1970-01-01 01:02:03,456", PatternLayout("%d{HH:mm:ss zzzz'}").format(event()))
    }

    @Test
    fun levelAndMessageConversions() {
        assertEquals("WARN: boom", PatternLayout("%p: %m").format(event(Level.WARN, "boom")))
        assertEquals("FATAL", PatternLayout("%p").format(event(Level.FATAL)))
        assertEquals("main", PatternLayout("%t").format(event()))
        assertTrue(PatternLayout("%r").format(event()).matches(Regex("-?\\d+")))
    }

    @Test
    fun loggerNamePrecision() {
        assertEquals("GlobalConsole", PatternLayout("%c{1}").format(event()))
        assertEquals("console.GlobalConsole", PatternLayout("%c{2}").format(event()))
        assertEquals("org.autojs.autojs.core.console.GlobalConsole", PatternLayout("%c{9}").format(event()))
        assertEquals("org.autojs.autojs.core.console.GlobalConsole", PatternLayout("%c").format(event()))
        assertEquals("GlobalConsole", PatternLayout("%C{1}").format(event()))
        assertEquals("plain", PatternLayout("%c{1}").format(event(loggerName = "plain")))
    }

    @Test
    fun widthModifiersPadAndTruncateLikeLog4j() {
        assertEquals("INFO |", PatternLayout("%-5p|").format(event()))
        assertEquals("| INFO", PatternLayout("|%5p").format(event()))
        assertEquals("NFO", PatternLayout("%.3p").format(event()))
        assertEquals("  NFO", PatternLayout("%5.3p").format(event()))
        assertEquals("NFO  ", PatternLayout("%-5.3p").format(event()))
        assertEquals("WARN", PatternLayout("%2p").format(event(Level.WARN)))
    }

    @Test
    fun escapesUnknownConversionsAndLiterals() {
        assertEquals("100%", PatternLayout("100%%").format(event()))
        assertEquals("%q hello", PatternLayout("%q %m").format(event()))
        assertEquals("hello%", PatternLayout("%m%").format(event()))
        assertEquals("[?:?] ", PatternLayout("[%F:%L] %X{user}%x").format(event()))
        assertEquals("a - hello - b", PatternLayout("a - %m - b").format(event()))
    }

}
