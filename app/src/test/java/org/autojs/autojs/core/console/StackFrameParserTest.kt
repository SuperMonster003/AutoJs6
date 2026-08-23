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
        assertEquals(4, frame.columnNumber)
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
            assertEquals(2, frame.columnNumber)
        }
    }

    @Test
    fun parseFileUriFrame() {
        val frame = StackFrameParser.parse("at run (file:///sdcard/project/main.mjs:12:9)").single()

        assertEquals("file:///sdcard/project/main.mjs", frame.sourceName)
        assertEquals(11, frame.lineIndex)
        assertEquals(8, frame.columnNumber)
    }

    @Test
    fun parseCompilationDiagnosticWithSpacesInSourceName() {
        val frame = StackFrameParser.parse(
            "TypeScript compilation failed\nmy script.ts:3:7 - error TS2322: Type mismatch",
        ).single()

        assertEquals("my script.ts", frame.sourceName)
        assertEquals(2, frame.lineIndex)
        assertEquals(6, frame.columnNumber)
    }

    @Test
    fun parseV8TypeScriptFramesWithSpacesWithoutCreatingSuffixDuplicates() {
        val frames = StackFrameParser.parse(
            """
                Error: runtime failure
                    at failAtSource (my runtime script.ts:3)
                    at another runtime script.ts:8
            """.trimIndent(),
        )

        assertEquals(2, frames.size)
        assertEquals("my runtime script.ts", frames[0].sourceName)
        assertEquals(2, frames[0].lineIndex)
        assertEquals(0, frames[0].columnNumber)
        assertEquals("another runtime script.ts", frames[1].sourceName)
        assertEquals(7, frames[1].lineIndex)
        assertEquals(0, frames[1].columnNumber)
    }

    @Test
    fun parseV8TypeScriptFrameAfterConsoleTimestampPrefix() {
        val frame = StackFrameParser.parse(
            "21:19:14.153/E:     at failAtTypeScriptLine (node runtime map.mts:4:11)",
        ).single()

        assertEquals("node runtime map.mts", frame.sourceName)
        assertEquals(3, frame.lineIndex)
        assertEquals(10, frame.columnNumber)
    }

    @Test
    fun ignoreStackFramesWithOverflowingNumbers() {
        val frames = StackFrameParser.parse(
            "script.js:999999999999999999999:1\nscript.ts:1:999999999999999999999",
        )

        assertTrue(frames.isEmpty())
    }
}
