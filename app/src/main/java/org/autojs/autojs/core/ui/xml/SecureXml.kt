package org.autojs.autojs.core.ui.xml

import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.File
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses script-owned XML without allowing a document type declaration or custom entities.
 *
 * Android's DocumentBuilderFactory does not implement all of the security features available on
 * a desktop JVM. Rejecting declarations before parsing is therefore the fail-closed guarantee;
 * supported factory hardening switches are enabled as an additional layer.
 */
internal object SecureXml {

    fun parse(xml: String): Document {
        val source = xml.removePrefix("\uFEFF")
        if (source.length > MAX_XML_CHARACTERS) {
            throw SAXParseException(
                "Script XML exceeds the $MAX_XML_CHARACTERS character limit",
                null,
                null,
                -1,
                -1,
            )
        }
        validateStructureLimits(source)
        val builder = newDocumentBuilderFactory().newDocumentBuilder().apply {
            setEntityResolver { _, _ ->
                throw SAXException("External entities are not allowed in script XML")
            }
        }
        return builder.parse(InputSource(StringReader(source)))
    }

    fun parse(file: File): Document {
        return parse(read(file))
    }

    fun read(file: File): String {
        require(file.length() <= MAX_XML_BYTES) {
            "Script XML exceeds the $MAX_XML_BYTES byte limit: ${file.canonicalPath}"
        }
        return file.readText()
    }

    /**
     * Performs a non-recursive pass before Android's recursive DOM parser sees the input. Besides
     * rejecting declarations, this prevents a deeply nested document from exhausting the thread
     * stack before the layout inflater's own View-depth guard can run.
     */
    private fun validateStructureLimits(xml: String) {
        var cursor = 0
        var depth = 0
        while (cursor < xml.length) {
            val start = xml.indexOf('<', cursor)
            if (start < 0) {
                return
            }
            when {
                xml.startsWith("<!--", start) -> {
                    cursor = markerEnd(xml, start + 4, "-->", "XML comment")
                }
                xml.startsWith("<![CDATA[", start) -> {
                    cursor = markerEnd(xml, start + 9, "]]>", "CDATA section")
                }
                xml.startsWith("<?", start) -> {
                    cursor = markerEnd(xml, start + 2, "?>", "processing instruction")
                }
                xml.startsWith("</", start) -> {
                    cursor = tagEnd(xml, start) + 1
                    if (depth > 0) {
                        depth--
                    }
                }
                xml.startsWith("<!", start) -> {
                    val declarationStart = (start + 2 until xml.length)
                        .firstOrNull { !xml[it].isWhitespace() }
                        ?: throw parseError("Unterminated XML declaration")
                    val declaration = xml.substring(
                        declarationStart,
                        (declarationStart until xml.length)
                            .firstOrNull { !xml[it].isLetter() }
                            ?: xml.length,
                    )
                    if (declaration.equals("DOCTYPE", true) || declaration.equals("ENTITY", true)) {
                        throw parseError("DOCTYPE and ENTITY declarations are not allowed in script XML")
                    }
                    cursor = tagEnd(xml, start) + 1
                }
                else -> {
                    val end = tagEnd(xml, start)
                    var last = end - 1
                    while (last > start && xml[last].isWhitespace()) {
                        last--
                    }
                    if (xml[last] != '/') {
                        depth++
                        if (depth > MAX_XML_ELEMENT_DEPTH) {
                            throw parseError(
                                "Script XML element depth exceeds $MAX_XML_ELEMENT_DEPTH",
                            )
                        }
                    }
                    cursor = end + 1
                }
            }
        }
    }

    private fun markerEnd(
        xml: String,
        start: Int,
        marker: String,
        description: String,
    ): Int {
        val end = xml.indexOf(marker, start)
        if (end < 0) {
            throw parseError("Unterminated $description")
        }
        return end + marker.length
    }

    private fun tagEnd(xml: String, start: Int): Int {
        var quote: Char? = null
        for (index in start + 1 until xml.length) {
            val character = xml[index]
            if (quote != null) {
                if (character == quote) {
                    quote = null
                }
            } else {
                when (character) {
                    '\'', '"' -> quote = character
                    '>' -> return index
                }
            }
        }
        throw parseError("Unterminated XML tag")
    }

    private fun parseError(message: String) =
        SAXParseException(message, null, null, -1, -1)

    private fun newDocumentBuilderFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isValidating = false
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
        setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
        runCatching {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        }
        runCatching {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }

    private const val MAX_XML_CHARACTERS = 8 * 1024 * 1024
    private const val MAX_XML_BYTES = 8L * 1024L * 1024L
    private const val MAX_XML_ELEMENT_DEPTH = 128
}
