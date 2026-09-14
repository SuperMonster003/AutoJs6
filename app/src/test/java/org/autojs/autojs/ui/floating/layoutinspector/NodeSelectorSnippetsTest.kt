package org.autojs.autojs.ui.floating.layoutinspector

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Selector source generated from node attributes as the node info view displays them.
 * zh-CN: 由节点信息视图显示的属性生成的选择器源码.
 */
class NodeSelectorSnippetsTest {

    @Test
    fun quoteEscapesBackslashQuoteAndLineBreaks() {
        assertEquals("'a\\'b\\\\c\\nd\\re'", NodeSelectorSnippets.quote("a'b\\c\nd\re"))
    }

    @Test
    fun chainedSelectorJoinsCallsInRowOrder() {
        val attributes = listOf("id" to "btn_ok", "text" to "OK", "clickable" to "true", "depth" to "7")
        assertEquals("id('btn_ok').text('OK').clickable(true).depth(7)", NodeSelectorSnippets.chainedSelector(attributes))
    }

    @Test
    fun boundsKeepNegativeNumbers() {
        assertEquals("bounds(-10, 0, 100, 200)", NodeSelectorSnippets.selectorCall("bounds", "[ -10, 0, 100, 200 ]"))
        assertEquals(listOf("bounds" to "[-10, 0, 100, 200]"), NodeSelectorSnippets.objectEntries("bounds", "[ -10, 0, 100, 200 ]"))
    }

    @Test
    fun centerSplitsIntoAxesWithRangesForFractions() {
        assertEquals("centerX(45).centerY(99, 100)", NodeSelectorSnippets.selectorCall("center", "[ 45, 99.5 ]"))
        assertEquals(listOf("centerX" to "45", "centerY" to "[99, 100]"), NodeSelectorSnippets.objectEntries("center", "[ 45, 99.5 ]"))
    }

    @Test
    fun classNameDropsWidgetPackage() {
        assertEquals("className('TextView')", NodeSelectorSnippets.selectorCall("className", "android.widget.TextView"))
        assertEquals("className('android.view.View')", NodeSelectorSnippets.selectorCall("className", "android.view.View"))
    }

    @Test
    fun actionsBecomeActionCallAndArray() {
        assertEquals("action('CLICK', 'LONG_CLICK')", NodeSelectorSnippets.selectorCall("actionNames", "CLICK\nLONG_CLICK"))
        assertEquals(listOf("action" to "['CLICK', 'LONG_CLICK']"), NodeSelectorSnippets.objectEntries("actionNames", "CLICK\nLONG_CLICK"))
    }

    @Test
    fun objectSelectorIsIndentedWithTrailingCommas() {
        val attributes = listOf(
            "id" to "btn_ok",
            "text" to "It's OK",
            "bounds" to "[ 0, 0, 10, 10 ]",
            "center" to "[ 5, 4.5 ]",
            "clickable" to "true",
            "actionNames" to "CLICK",
        )
        val expected = "{\n" +
                "    id: 'btn_ok',\n" +
                "    text: 'It\\'s OK',\n" +
                "    bounds: [0, 0, 10, 10],\n" +
                "    centerX: 5,\n" +
                "    centerY: [4, 5],\n" +
                "    clickable: true,\n" +
                "    action: ['CLICK'],\n" +
                "}"
        assertEquals(expected, NodeSelectorSnippets.objectSelector(attributes))
    }

    @Test
    fun emptySelectionsGiveEmptySelectors() {
        assertEquals("", NodeSelectorSnippets.chainedSelector(emptyList()))
        assertEquals("{}", NodeSelectorSnippets.objectSelector(emptyList()))
    }

}
