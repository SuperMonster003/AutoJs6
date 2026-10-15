package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import org.xml.sax.SAXParseException

/** A layout request that could not be applied: the error code, a message and where it happened. */
data class UiPreviewLayoutFailure(
    val code: String,
    val message: String,
    val elementIndex: Int,
    val attrName: String?,
    val line: Int,
    val column: Int,
)

/**
 * Turns an inflation exception into a contract failure. XML syntax errors (a [SAXParseException] in the
 * cause chain) become [UiPreviewContract.ERROR_XML_INVALID] with the parser's line / column and the index
 * of the last element opened before that position; everything else is [UiPreviewContract.ERROR_INFLATE_FAILED]
 * at the element (and attribute) the tracking delegate was in when the exception escaped.
 *
 * zh-CN: 把 inflate 异常转为契约失败. XML 语法错误 (原因链中的 [SAXParseException]) 成为 [UiPreviewContract.ERROR_XML_INVALID],
 * 带解析器给出的行 / 列与该位置之前最后打开的元素先序号; 其余为 [UiPreviewContract.ERROR_INFLATE_FAILED],
 * 定位到异常逃逸时跟踪 delegate 所在的元素 (与属性).
 */
object UiPreviewFailures {

    fun classify(error: Throwable, currentElement: Int, currentAttr: String?, xml: String): UiPreviewLayoutFailure {
        val parseError = causeChain(error).filterIsInstance<SAXParseException>().firstOrNull()
        if (parseError != null) {
            val line = parseError.lineNumber
            val column = parseError.columnNumber
            val element = if (line > 0) elementIndexBefore(xml, line, column) else -1
            return UiPreviewLayoutFailure(UiPreviewContract.ERROR_XML_INVALID, messageOf(parseError), element, null, line, column)
        }
        return UiPreviewLayoutFailure(UiPreviewContract.ERROR_INFLATE_FAILED, messageOf(error), currentElement, currentAttr, -1, -1)
    }

    /** The deepest non-blank message of the cause chain, limited to the contract length. */
    fun messageOf(error: Throwable): String {
        val chain = causeChain(error)
        val message = chain.asReversed().firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
            ?: chain.last().javaClass.simpleName
        return if (message.length > UiPreviewContract.MAX_ERROR_MESSAGE_CHARS) message.substring(0, UiPreviewContract.MAX_ERROR_MESSAGE_CHARS) else message
    }

    /**
     * The pre-order index (elements only) of the last element whose start tag begins before the 1-based
     * [line] / [column]; -1 when no element starts before it. Comments, CDATA sections, processing
     * instructions and quoted attribute values are skipped so a `<` inside them is not counted.
     */
    fun elementIndexBefore(xml: String, line: Int, column: Int): Int {
        val limit = offsetOf(xml, line, column)
        var count = 0
        var i = 0
        while (i < limit && i < xml.length) {
            val ch = xml[i]
            if (ch != '<') {
                i++
                continue
            }
            when {
                xml.startsWith("<!--", i) -> i = skipTo(xml, i + 4, "-->")
                xml.startsWith("<![CDATA[", i) -> i = skipTo(xml, i + 9, "]]>")
                xml.startsWith("<!", i) -> i = skipTo(xml, i + 2, ">")
                xml.startsWith("<?", i) -> i = skipTo(xml, i + 2, "?>")
                i + 1 < xml.length && (xml[i + 1].isLetter() || xml[i + 1] == '_') -> {
                    count++
                    i = skipTag(xml, i + 1)
                }
                else -> i++
            }
        }
        return count - 1
    }

    /** The 0-based char offset of a 1-based line / column; an unknown column means the start of the line. */
    fun offsetOf(xml: String, line: Int, column: Int): Int {
        if (line <= 0) return 0
        var offset = 0
        var currentLine = 1
        while (currentLine < line && offset < xml.length) {
            if (xml[offset] == '\n') currentLine++
            offset++
        }
        if (column > 1) offset += column - 1
        return offset.coerceIn(0, xml.length)
    }

    private fun causeChain(error: Throwable): List<Throwable> {
        val chain = ArrayList<Throwable>()
        var current: Throwable? = error
        while (current != null && current !in chain) {
            chain.add(current)
            current = current.cause
        }
        return chain
    }

    private fun skipTo(xml: String, from: Int, terminator: String): Int {
        val end = xml.indexOf(terminator, from)
        return if (end < 0) xml.length else end + terminator.length
    }

    /** Skips the rest of a start tag, honoring quoted attribute values. */
    private fun skipTag(xml: String, from: Int): Int {
        var i = from
        var quote = 0.toChar()
        while (i < xml.length) {
            val ch = xml[i]
            when {
                quote != 0.toChar() -> if (ch == quote) quote = 0.toChar()
                ch == '"' || ch == '\'' -> quote = ch
                ch == '>' -> return i + 1
            }
            i++
        }
        return i
    }
}
