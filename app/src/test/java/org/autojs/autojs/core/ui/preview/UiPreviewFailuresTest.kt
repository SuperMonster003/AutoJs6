package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.xml.sax.SAXParseException

class UiPreviewFailuresTest {

    private val xml = """
        <vertical padding="16">
            <!-- a <comment> that must not count -->
            <text id="title" text="a < b"/>
            <?pi <not an element> ?>
            <![CDATA[ <cdata> ]]>
            <horizontal>
                <button id="ok" text="OK"/>
                <button id="cancel" text="Cancel"/>
            </horizontal>
        </vertical>
    """.trimIndent()

    @Test
    fun elementIndexBeforeCountsStartTagsOnly() {
        val cancelLine = xml.lines().indexOfFirst { it.contains("cancel") } + 1
        // Before <button id="cancel"> the elements vertical, text, horizontal and the first button were opened.
        assertEquals(3, UiPreviewFailures.elementIndexBefore(xml, cancelLine, 1))
        assertEquals(4, UiPreviewFailures.elementIndexBefore(xml, cancelLine, 40))
        assertEquals(-1, UiPreviewFailures.elementIndexBefore(xml, 1, 1))
        assertEquals(0, UiPreviewFailures.elementIndexBefore(xml, 2, 1))
        assertEquals(0, UiPreviewFailures.elementIndexBefore(xml, 3, 1))
        assertEquals(1, UiPreviewFailures.elementIndexBefore(xml, 4, 1))
        assertEquals(1, UiPreviewFailures.elementIndexBefore(xml, 6, 1))
        assertEquals(4, UiPreviewFailures.elementIndexBefore(xml, 100, 1))
    }

    @Test
    fun startTagOffsetsListElementsInPreOrder() {
        val offsets = UiPreviewFailures.startTagOffsets(xml)
        assertEquals(listOf("<vertical", "<text", "<horizontal", "<button id=\"ok\"", "<button id=\"cancel\"").map(xml::indexOf), offsets)
        assertEquals(offsets.take(2), UiPreviewFailures.startTagOffsets(xml, xml.indexOf("<horizontal")))
        assertEquals(listOf(0, 11), UiPreviewFailures.startTagOffsets("<a b=\"<c>\"><d/></a>"))
    }

    @Test
    fun offsetsFollowLinesAndColumns() {
        assertEquals(0, UiPreviewFailures.offsetOf("ab\ncd", 1, 1))
        assertEquals(3, UiPreviewFailures.offsetOf("ab\ncd", 2, 1))
        assertEquals(4, UiPreviewFailures.offsetOf("ab\ncd", 2, 2))
        assertEquals(3, UiPreviewFailures.offsetOf("ab\ncd", 2, -1))
        assertEquals(5, UiPreviewFailures.offsetOf("ab\ncd", 9, 9))
        assertEquals(0, UiPreviewFailures.offsetOf("ab\ncd", 0, 5))
    }

    @Test
    fun xmlSyntaxErrorsBecomeXmlInvalidWithPosition() {
        val cause = SAXParseException("The element type \"text\" must be terminated", null, null, 7, 12)
        val failure = UiPreviewFailures.classify(RuntimeException(IllegalStateException(cause)), 9, "text", xml)
        assertEquals(UiPreviewContract.ERROR_XML_INVALID, failure.code)
        assertEquals("The element type \"text\" must be terminated", failure.message)
        assertEquals(7, failure.line)
        assertEquals(12, failure.column)
        assertEquals(3, failure.elementIndex)
        assertNull(failure.attrName)
    }

    @Test
    fun otherExceptionsBecomeInflateFailedAtTheTrackedElement() {
        val failure = UiPreviewFailures.classify(RuntimeException("outer", IllegalArgumentException("Unknown element: nope")), 2, "w", xml)
        assertEquals(UiPreviewContract.ERROR_INFLATE_FAILED, failure.code)
        assertEquals("Unknown element: nope", failure.message)
        assertEquals(2, failure.elementIndex)
        assertEquals("w", failure.attrName)
        assertEquals(-1, failure.line)
    }

    @Test
    fun messagesFallBackToTheClassNameAndAreLimited() {
        assertEquals("IllegalStateException", UiPreviewFailures.messageOf(RuntimeException(" ", IllegalStateException())))
        val long = UiPreviewFailures.messageOf(RuntimeException("x".repeat(UiPreviewContract.MAX_ERROR_MESSAGE_CHARS + 10)))
        assertEquals(UiPreviewContract.MAX_ERROR_MESSAGE_CHARS, long.length)
        val loop = RuntimeException("loop")
        loop.initCause(RuntimeException("inner", loop).also { })
        assertEquals("inner", UiPreviewFailures.messageOf(loop))
    }
}
