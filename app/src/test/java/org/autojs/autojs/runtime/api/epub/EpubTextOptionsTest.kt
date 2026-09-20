package org.autojs.autojs.runtime.api.epub

import org.autojs.autojs.core.plugin.epub.EpubTextChunk
import org.autojs.plugin.epub.api.EpubContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `text()` / `textAll()` over the contract's text windows (plugin roadmap P6.1): window
 * boundaries, the shared `maxChars` cap, the resource separator and the surrogate-pair rule,
 * driven by fake chunk sources.
 */
class EpubTextOptionsTest {

    /** A fake resource whose `getText` answers windows of [content] like the plugin does. */
    private class FakeResource(val content: String, val index: Int = 0) : EpubTextAssembler.ChunkSource {
        val calls = ArrayList<Pair<Int, Int>>()

        override fun fetch(offset: Int, maxChars: Int): EpubTextChunk {
            calls += offset to maxChars
            val start = offset.coerceAtMost(content.length)
            var end = (start + maxChars).coerceAtMost(content.length)
            if (end < content.length && end > start && Character.isHighSurrogate(content[end - 1])) end--
            return EpubTextChunk("OEBPS/c$index.xhtml", index, EpubContract.FORMAT_TEXT, start, content.substring(start, end), end < content.length)
        }
    }

    @Test
    fun aResourceIsReadWindowByWindowUntilNoMoreText() {
        val resource = FakeResource("abcdefghij")
        assertEquals("abcdefghij", EpubTextAssembler.resource(0, 1_000, resource, window = 4))
        assertEquals(listOf(0 to 4, 4 to 4, 8 to 4), resource.calls)
        assertEquals("", EpubTextAssembler.resource(10, 1_000, FakeResource("abcdefghij"), window = 4))
        assertEquals("", EpubTextAssembler.resource(0, 5, FakeResource(""), window = 4))
    }

    @Test
    fun offsetAndMaxCharsBoundASingleResource() {
        val resource = FakeResource("abcdefghij")
        assertEquals("cdefg", EpubTextAssembler.resource(2, 5, resource, window = 3))
        assertEquals(listOf(2 to 3, 5 to 2), resource.calls)
        assertEquals("j", EpubTextAssembler.resource(9, 5, FakeResource("abcdefghij")))
        assertEquals("abc", EpubTextAssembler.resource(0, 3, FakeResource("abcdefghij")))
    }

    @Test
    fun theCapNeverSplitsASurrogatePairAndOversizedChunksAreCut() {
        val emoji = "a😀b"
        assertEquals("a", EpubTextAssembler.resource(0, 2, FakeResource(emoji)))
        assertEquals("a😀", EpubTextAssembler.resource(0, 3, FakeResource(emoji)))
        // A misbehaving source that ignores the window: the assembler keeps its own cap and stops.
        val greedy = EpubTextAssembler.ChunkSource { _, _ -> EpubTextChunk("h", 0, "text", 0, "0123456789", true) }
        assertEquals("01234", EpubTextAssembler.resource(0, 5, greedy))
        // An empty chunk ends the resource even when the source claims more.
        val empty = EpubTextAssembler.ChunkSource { _, _ -> EpubTextChunk("h", 0, "text", 0, "", true) }
        assertEquals("", EpubTextAssembler.resource(0, 5, empty))
    }

    @Test
    fun theWholeBookJoinsNonBlankResourcesUnderOneCap() {
        val resources = listOf(FakeResource("One", 0), FakeResource("   ", 1), FakeResource("", 2), FakeResource("Three", 3))
        assertEquals("One\n\nThree", EpubTextAssembler.book(resources.size, 1_000, { resources[it] }))
        assertEquals("One\n\nThr", EpubTextAssembler.book(resources.size, 8, { resources[it] }))
        assertEquals("One", EpubTextAssembler.book(resources.size, 4, { resources[it] }))
        assertEquals("On", EpubTextAssembler.book(resources.size, 2, { resources[it] }))
        assertEquals("", EpubTextAssembler.book(0, 100, { resources[it] }))
        assertEquals("\n\n", EpubTextAssembler.SEPARATOR)
    }

    @Test
    fun theWholeBookReadsEachResourceFromItsStartWithTheContractWindow() {
        val resources = listOf(FakeResource("x".repeat(EpubContract.MAX_TEXT_CHARS_PER_CALL + 5), 0), FakeResource("tail", 1))
        val text = EpubTextAssembler.book(2, Int.MAX_VALUE, { resources[it] })
        assertEquals(EpubContract.MAX_TEXT_CHARS_PER_CALL + 5 + 2 + 4, text.length)
        assertTrue(text.endsWith("\n\ntail"))
        assertEquals(listOf(0 to EpubContract.MAX_TEXT_CHARS_PER_CALL, EpubContract.MAX_TEXT_CHARS_PER_CALL to EpubContract.MAX_TEXT_CHARS_PER_CALL), resources[0].calls)
        assertEquals(listOf(0 to EpubContract.MAX_TEXT_CHARS_PER_CALL), resources[1].calls)
    }
}
