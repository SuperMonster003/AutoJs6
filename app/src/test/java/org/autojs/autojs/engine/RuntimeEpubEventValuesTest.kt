package org.autojs.autojs.engine

import org.autojs.autojs.core.plugin.epub.EpubReaderEvent
import org.junit.Assert.*
import org.junit.Test

class RuntimeEpubEventValuesTest {
    @Test fun progressRetainsThePublicFieldNamesAndNullableLocator() {
        val result = RuntimeEpubBridge.eventValue(EpubReaderEvent("progress", progression = 0.5, title = "Chapter", href = "two.xhtml"))
        assertEquals(0.5, result["totalProgression"])
        assertEquals("Chapter", result["chapterTitle"])
        assertTrue(result.containsKey("locator"))
        assertNull(result["locator"])
        assertFalse(result.containsKey("progress"))
    }

    @Test fun bookmarkAndHighlightEventsExposeTheSingleChangedEntry() {
        val bookmark = RuntimeEpubBridge.eventValue(EpubReaderEvent("bookmark", change = "added",
            bookmarksJson = """[{"locator":{"href":"one.xhtml"},"createdAt":123,"title":"One"}]"""))
        assertEquals("added", bookmark["action"])
        assertEquals("One", bookmark["title"])
        assertTrue(bookmark["locator"] is Map<*, *>)
        assertTrue(bookmark.containsKey("text"))
        assertNull(bookmark["text"])
        val highlight = RuntimeEpubBridge.eventValue(EpubReaderEvent("highlight", change = "updated",
            annotationsJson = """[{"id":3,"style":"highlight","color":"#FFFF00","note":"note","quote":"word","updatedAt":456}]"""))
        assertEquals("updated", highlight["action"])
        assertEquals("note", highlight["note"])
        assertEquals("word", highlight["quote"])
        assertFalse(highlight.containsKey("annotations"))
    }
}
