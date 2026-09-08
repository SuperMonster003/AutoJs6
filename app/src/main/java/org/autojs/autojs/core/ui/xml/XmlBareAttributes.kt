package org.autojs.autojs.core.ui.xml

/**
 * Rewrites valueless ("bare") attributes of script layout XML, such as `<console global>`, into
 * `global="true"` before the strict DOM parser sees the document. Only element start tags are
 * touched; comments, processing instructions, CDATA sections, closing tags and text stay as they
 * are. Android-free so the rewrite is unit-testable.
 *
 * zh-CN: 在严格的 DOM 解析器处理文档之前, 把脚本布局 XML 中无值的 ("裸") 属性 (如 `<console global>`) 改写为 `global="true"`.
 * 仅处理元素起始标签; 注释, 处理指令, CDATA 段, 结束标签与文本保持原样. 不依赖 Android, 便于单元测试.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
object XmlBareAttributes {

    @JvmStatic
    fun normalize(xml: String): String {
        if (xml.indexOf('<') < 0) return xml
        val out = StringBuilder(xml.length + 16)
        var cursor = 0
        while (cursor < xml.length) {
            val start = xml.indexOf('<', cursor)
            if (start < 0) {
                out.append(xml, cursor, xml.length)
                break
            }
            out.append(xml, cursor, start)
            val skipped = skipNonElement(xml, start)
            if (skipped > start) {
                out.append(xml, start, skipped)
                cursor = skipped
                continue
            }
            val end = findTagEnd(xml, start)
            if (end < 0) {
                out.append(xml, start, xml.length)
                break
            }
            out.append(rewriteStartTag(xml.substring(start, end + 1)))
            cursor = end + 1
        }
        return out.toString()
    }

    /** Returns the index right after a comment, PI, CDATA or closing tag starting at [start], or [start] for an element start tag. */
    private fun skipNonElement(xml: String, start: Int): Int {
        fun skipTo(terminator: String): Int {
            val index = xml.indexOf(terminator, start)
            return if (index < 0) xml.length else index + terminator.length
        }
        return when {
            xml.startsWith("<!--", start) -> skipTo("-->")
            xml.startsWith("<![CDATA[", start) -> skipTo("]]>")
            xml.startsWith("<!", start) -> skipTo(">")
            xml.startsWith("<?", start) -> skipTo("?>")
            xml.startsWith("</", start) -> skipTo(">")
            start + 1 >= xml.length || !isNameStart(xml[start + 1]) -> start + 1
            else -> start
        }
    }

    private fun findTagEnd(xml: String, start: Int): Int {
        var quote = 0.toChar()
        var index = start + 1
        while (index < xml.length) {
            val c = xml[index]
            when {
                quote != 0.toChar() -> if (c == quote) quote = 0.toChar()
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return index
            }
            index++
        }
        return -1
    }

    private fun rewriteStartTag(tag: String): String {
        // tag = "<name ... >" or "<name ... />"
        // zh-CN: tag 形如 "<name ... >" 或 "<name ... />".
        var index = 1
        while (index < tag.length && !tag[index].isWhitespace() && tag[index] != '>' && tag[index] != '/') index++
        val head = tag.substring(0, index)
        val selfClosing = tag.endsWith("/>")
        val bodyEnd = tag.length - if (selfClosing) 2 else 1
        val body = tag.substring(index, bodyEnd)
        if (body.isBlank()) return tag
        val rewritten = StringBuilder(body.length + 16)
        var cursor = 0
        while (cursor < body.length) {
            val c = body[cursor]
            if (c.isWhitespace()) {
                rewritten.append(c)
                cursor++
                continue
            }
            val nameStart = cursor
            while (cursor < body.length && !body[cursor].isWhitespace() && body[cursor] != '=') cursor++
            val name = body.substring(nameStart, cursor)
            var probe = cursor
            while (probe < body.length && body[probe].isWhitespace()) probe++
            if (probe < body.length && body[probe] == '=') {
                // Attribute with a value: copy "name = value" verbatim.
                // zh-CN: 带值的属性: 原样复制 "name = value".
                probe++
                while (probe < body.length && body[probe].isWhitespace()) probe++
                if (probe < body.length && (body[probe] == '"' || body[probe] == '\'')) {
                    val quote = body[probe]
                    val close = body.indexOf(quote, probe + 1)
                    probe = if (close < 0) body.length else close + 1
                } else {
                    while (probe < body.length && !body[probe].isWhitespace()) probe++
                }
                rewritten.append(body, nameStart, probe)
                cursor = probe
            } else {
                rewritten.append(name).append("=\"true\"")
            }
        }
        return head + rewritten + if (selfClosing) "/>" else ">"
    }

    private fun isNameStart(c: Char) = c.isLetter() || c == '_' || c == ':'

}
