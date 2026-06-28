package org.autojs.autojs.core.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackFrameParserTest {

    @Test
    fun parseAbsoluteJavaScriptFrame() {
        val frame = StackFrameParser.parse("at main (/sdcard/scripts/test.js:10:5)").single()

        assertEquals("/sdcard/scripts/test.js", frame.sourceName)
        assertEquals(9, frame.lineIndex)
        assertEquals(5, frame.columnNumber)
    }

    @Test
    fun parseRelativeJavaScriptFrame() {
        val frame = StackFrameParser.parse("script.js:42").single()

        assertEquals("script.js", frame.sourceName)
        assertEquals(41, frame.lineIndex)
        assertEquals(0, frame.columnNumber)
    }

    @Test
    fun parseSupportedNodeAndTypeScriptExtensions() {
        val extensions = listOf("js", "mjs", "cjs", "ts", "tsx", "mts", "cts", "node.js")

        extensions.forEach { extension ->
            val frame = StackFrameParser.parse("at run (/sdcard/project/main.$extension:7:3)").single()

            assertEquals("/sdcard/project/main.$extension", frame.sourceName)
            assertEquals(6, frame.lineIndex)
            assertEquals(3, frame.columnNumber)
        }
    }

    @Test
    fun parseFileUriFrame() {
        val frame = StackFrameParser.parse("at run (file:///sdcard/project/main.mjs:12:9)").single()

        assertEquals("file:///sdcard/project/main.mjs", frame.sourceName)
        assertEquals(11, frame.lineIndex)
        assertEquals(9, frame.columnNumber)
    }

    @Test
    fun ignoreStackFramesWithOverflowingNumbers() {
        val frames = StackFrameParser.parse("script.js:999999999999999999999:1")

        assertTrue(frames.isEmpty())
    }
}
