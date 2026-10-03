package org.autojs.autojs.runtime.api.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The byte-stream-to-lines reader behind the `output` events (plugin roadmap P4.2), without Android. */
class TerminalOutputReaderTest {

    private class Recorder : TerminalOutputReader.Sink {
        val events = mutableListOf<String>()
        override fun onLine(line: String) {
            events += "line:$line"
        }

        override fun onOverflow(droppedBytes: Long) {
            events += "overflow:$droppedBytes"
        }

        override fun onEnd() {
            events += "end"
        }
    }

    @Test
    fun stripsAnsiSequencesAndSplitsLines() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder)
        reader.feed("\u001B[32mhello\u001B[0m \u001B[1;34mworld\u001B[m\r\n\u001B]0;window title\u0007second\u001B[2K\u001B[1A\n".toByteArray())
        reader.feed("\u001BP+q544e\u001B\\third\u001B(B\u001B7\u001B8 \u001B[?25h\u001B[38;5;208mdone\n".toByteArray())
        assertEquals(listOf("line:hello world", "line:second", "line:third done"), recorder.events)
        assertEquals(3L, reader.lineCount)
    }

    @Test
    fun keepsRawBytesWhenNotStripping() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder, stripAnsi = false)
        reader.feed("\u001B[32mhi\u001B[0m\r\nnext\u0007\n".toByteArray())
        assertEquals(listOf("line:\u001B[32mhi\u001B[0m\r", "line:next\u0007"), recorder.events)
    }

    @Test
    fun decodesUtf8AndEscapesAcrossChunkBoundaries() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder)
        val bytes = "中文 ok\n".toByteArray()
        reader.feed(bytes, 0, 2)
        reader.feed(bytes, 2, 3)
        reader.feed(bytes, 5, bytes.size - 5)
        reader.feed("\u001B[".toByteArray())
        reader.feed("31mred\u001B".toByteArray())
        reader.feed("]2;title\u0007 text\n".toByteArray())
        assertEquals(listOf("line:中文 ok", "line:red text"), recorder.events)
    }

    @Test
    fun capsLongLinesWithoutAddingAnEmptyLine() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder, maxLineBytes = 8)
        reader.feed("abcdefghijk\nabcdefgh\nxy\n".toByteArray())
        assertEquals(listOf("line:abcdefgh", "line:ijk", "line:abcdefgh", "line:xy"), recorder.events)
        assertEquals(TerminalOutputReader.MAX_LINE_BYTES, 65_536)
    }

    @Test
    fun capsUtf8BytesWithoutSplittingMultibyteCodePoints() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder, maxLineBytes = 8)
        "中文😀abc😀\n".toByteArray().forEach { reader.feed(byteArrayOf(it)) }
        reader.finish()
        assertEquals(listOf("line:中文", "line:😀abc", "line:😀", "end"), recorder.events)
    }

    @Test
    fun idlePromptIsDeliveredOnceAndALaterNewlineDoesNotInventAnEmptyLine() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder)
        reader.feed("name? ".toByteArray())
        reader.flushPartial()
        reader.flushPartial()
        reader.feed("\nanswer\n".toByteArray())
        reader.finish()
        assertEquals(listOf("line:name? ", "line:answer", "end"), recorder.events)
    }

    @Test
    fun overflowReportsKeepStreamOrderAndFinishFlushesThePendingLine() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder)
        reader.feed("a\nb".toByteArray())
        reader.noteOverflow(10)
        reader.noteOverflow(0)
        reader.feed("c\nd".toByteArray())
        assertFalse(reader.isEnded)
        reader.finish()
        reader.finish()
        reader.feed("ignored\n".toByteArray())
        reader.noteOverflow(5)
        assertTrue(reader.isEnded)
        assertEquals(listOf("line:a", "overflow:10", "line:bc", "line:d", "end"), recorder.events)
    }

    @Test
    fun dropsOtherControlCharactersButKeepsTabs() {
        val recorder = Recorder()
        val reader = TerminalOutputReader(recorder)
        reader.feed("a\tb\u0007\bc\u007F\r\u001B[0Kd\n\n".toByteArray())
        assertEquals(listOf("line:a\tbcd", "line:"), recorder.events)
    }

    @Test
    fun finishWithoutAnyInputOnlyEndsTheStream() {
        val recorder = Recorder()
        TerminalOutputReader(recorder).finish()
        assertEquals(listOf("end"), recorder.events)
    }
}
