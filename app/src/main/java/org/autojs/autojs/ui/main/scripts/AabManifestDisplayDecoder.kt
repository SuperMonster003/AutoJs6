package org.autojs.autojs.ui.main.scripts

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A bounded, read-only decoder for the protobuf AndroidManifest.xml stored in an Android App
 * Bundle. It intentionally implements only the AAPT2 XML messages needed for display.
 */
internal object AabManifestDisplayDecoder {

    internal data class Limits(
        val maxInputBytes: Int = 4 * 1024 * 1024,
        val maxZipEntries: Int = 10_000,
        val maxFields: Int = 500_000,
        val maxNodes: Int = 50_000,
        val maxAttributes: Int = 100_000,
        val maxAttributesPerElement: Int = 4_096,
        val maxNamespaces: Int = 256,
        val maxDepth: Int = 256,
        val maxDecodedStringChars: Int = 4 * 1024 * 1024,
        val maxSingleStringChars: Int = 16 * 1024,
        val maxXmlNameChars: Int = 1_024,
        val maxOutputChars: Int = 4 * 1024 * 1024,
    )

    private val displayLimits = Limits()

    fun decode(aabFile: File): String = decode(aabFile, displayLimits)

    internal fun decode(aabFile: File, limits: Limits): String {
        if (!aabFile.isFile) {
            throw IOException("AAB file does not exist")
        }
        validateLimits(limits)
        return ZipFile(aabFile).use { zipFile ->
            val manifestEntry = findManifestEntry(zipFile, limits)
            if (manifestEntry.size > limits.maxInputBytes) {
                throw IOException(
                    "${manifestEntry.name} exceeds the AAB manifest display size limit",
                )
            }
            zipFile.getInputStream(manifestEntry).use { input ->
                decodeManifest(input.readBounded(limits.maxInputBytes), limits)
            }
        }
    }

    internal fun decodeManifest(
        bytes: ByteArray,
        limits: Limits = displayLimits,
    ): String {
        if (bytes.isEmpty()) {
            throw malformed("manifest is empty")
        }
        if (bytes.size > limits.maxInputBytes) {
            throw IOException("AAB manifest exceeds the display size limit")
        }
        validateLimits(limits)
        return ProtoXmlDecoder(bytes, limits).decode()
    }

    private fun findManifestEntry(zipFile: ZipFile, limits: Limits): ZipEntry {
        var entryCount = 0
        val candidates = ArrayList<ZipEntry>()
        val candidateNames = HashSet<String>()
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            entryCount++
            if (entryCount > limits.maxZipEntries) {
                throw IOException("AAB entry count exceeds the display limit")
            }
            if (entry.isDirectory || !isModuleManifest(entry.name)) continue
            if (!candidateNames.add(entry.name)) {
                throw IOException("AAB contains duplicate manifest entry ${entry.name}")
            }
            candidates += entry
        }
        return candidates.firstOrNull { it.name == BASE_MANIFEST_ENTRY }
            ?: candidates.minByOrNull { it.name }
            ?: throw IOException("AAB contains no module AndroidManifest.xml")
    }

    private fun isModuleManifest(name: String): Boolean {
        if (!name.endsWith(MODULE_MANIFEST_SUFFIX)) return false
        val moduleName = name.removeSuffix(MODULE_MANIFEST_SUFFIX)
        return moduleName.isNotEmpty() && '/' !in moduleName && '\\' !in moduleName
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read > maxBytes - total) {
                throw IOException("AAB manifest exceeds the display size limit")
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private class ProtoXmlDecoder(
        private val bytes: ByteArray,
        private val limits: Limits,
    ) {

        private var fieldCount = 0
        private var nodeCount = 0
        private var attributeCount = 0
        private var namespaceCount = 0
        private var decodedStringChars = 0

        fun decode(): String {
            val root = readNode(reader(0, bytes.size), depth = 1)
            if (root !is XmlNode.Element) {
                throw malformed("root node is not an element")
            }
            return ProtoXmlRenderer(limits).render(root.value)
        }

        private fun readNode(reader: ProtoReader, depth: Int): XmlNode {
            if (depth > limits.maxDepth) {
                throw malformed("XML nesting exceeds the display limit")
            }
            nodeCount++
            if (nodeCount > limits.maxNodes) {
                throw malformed("XML node count exceeds the display limit")
            }
            var node: XmlNode? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    XML_NODE_ELEMENT_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (node != null) throw malformed("XML node contains multiple values")
                        node = XmlNode.Element(
                            readElement(reader.readLengthDelimitedReader(), depth),
                        )
                    }
                    XML_NODE_TEXT_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (node != null) throw malformed("XML node contains multiple values")
                        node = XmlNode.Text(reader.readString("XML text"))
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return node ?: throw malformed("XML node contains no value")
        }

        private fun readElement(reader: ProtoReader, depth: Int): XmlElement {
            val namespaces = ArrayList<XmlNamespace>()
            val attributes = ArrayList<XmlAttribute>()
            val children = ArrayList<XmlNode>()
            var namespaceUri = ""
            var name: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    XML_ELEMENT_NAMESPACE_DECLARATION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        namespaceCount++
                        if (namespaceCount > limits.maxNamespaces) {
                            throw malformed("XML namespace count exceeds the display limit")
                        }
                        namespaces += readNamespace(reader.readLengthDelimitedReader())
                    }
                    XML_ELEMENT_NAMESPACE_URI_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        namespaceUri = reader.readString("element namespace URI")
                    }
                    XML_ELEMENT_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("element name")
                    }
                    XML_ELEMENT_ATTRIBUTE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (attributes.size >= limits.maxAttributesPerElement) {
                            throw malformed("attribute count exceeds the per-element display limit")
                        }
                        attributeCount++
                        if (attributeCount > limits.maxAttributes) {
                            throw malformed("XML attribute count exceeds the display limit")
                        }
                        attributes += readAttribute(reader.readLengthDelimitedReader())
                    }
                    XML_ELEMENT_CHILD_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        children += readNode(reader.readLengthDelimitedReader(), depth + 1)
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val elementName = name?.takeIf { it.isNotEmpty() }
                ?: throw malformed("element name is missing")
            return XmlElement(namespaceUri, elementName, namespaces, attributes, children)
        }

        private fun readNamespace(reader: ProtoReader): XmlNamespace {
            var prefix = ""
            var uri: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    XML_NAMESPACE_PREFIX_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        prefix = reader.readString("namespace prefix")
                    }
                    XML_NAMESPACE_URI_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        uri = reader.readString("namespace URI")
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return XmlNamespace(
                prefix = prefix,
                uri = uri?.takeIf { it.isNotEmpty() }
                    ?: throw malformed("namespace URI is missing"),
            )
        }

        private fun readAttribute(reader: ProtoReader): XmlAttribute {
            var namespaceUri = ""
            var name: String? = null
            var rawValue: String? = null
            var resourceId = 0
            var compiledValue: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    XML_ATTRIBUTE_NAMESPACE_URI_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        namespaceUri = reader.readString("attribute namespace URI")
                    }
                    XML_ATTRIBUTE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("attribute name")
                    }
                    XML_ATTRIBUTE_VALUE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        rawValue = reader.readString("attribute value")
                    }
                    XML_ATTRIBUTE_RESOURCE_ID_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        resourceId = reader.readVarint().toInt()
                    }
                    XML_ATTRIBUTE_COMPILED_ITEM_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        compiledValue = readItem(reader.readLengthDelimitedReader())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val attributeName = name?.takeIf { it.isNotEmpty() }
                ?: if (resourceId != 0) {
                    "_${hex8(resourceId)}_"
                } else {
                    "_unknown_"
                }
            return XmlAttribute(
                namespaceUri = namespaceUri,
                name = attributeName,
                value = rawValue ?: compiledValue.orEmpty(),
            )
        }

        private fun readItem(reader: ProtoReader): String? {
            var value: String? = null
            var hasValue = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                val decoded = when (tag.fieldNumber) {
                    ITEM_REFERENCE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readReference(reader.readLengthDelimitedReader())
                    }
                    ITEM_STRING_FIELD, ITEM_RAW_STRING_FIELD, ITEM_STYLED_STRING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readWrappedString(reader.readLengthDelimitedReader())
                    }
                    ITEM_PRIMITIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        readPrimitive(reader.readLengthDelimitedReader())
                    }
                    else -> {
                        reader.skip(tag.wireType)
                        continue
                    }
                }
                if (hasValue) throw malformed("compiled item contains multiple values")
                value = decoded
                hasValue = true
            }
            return value
        }

        private fun readWrappedString(reader: ProtoReader): String? {
            var value: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == WRAPPED_STRING_VALUE_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    value = reader.readString("compiled string")
                } else {
                    reader.skip(tag.wireType)
                }
            }
            return value
        }

        private fun readReference(reader: ProtoReader): String? {
            var type = REFERENCE_TYPE_RESOURCE
            var id = 0
            var name: String? = null
            var privateReference = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    REFERENCE_TYPE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        type = reader.readVarint().toInt()
                    }
                    REFERENCE_ID_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        id = reader.readVarint().toInt()
                    }
                    REFERENCE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("resource reference")
                    }
                    REFERENCE_PRIVATE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        privateReference = reader.readVarint() != 0L
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val target = name?.takeIf { it.isNotEmpty() } ?: id.takeIf { it != 0 }?.let(::hex8)
                ?: return null
            val marker = if (type == REFERENCE_TYPE_ATTRIBUTE) "?" else "@"
            val privateMarker = if (privateReference && marker == "@") "*" else ""
            return "$marker$privateMarker$target"
        }

        private fun readPrimitive(reader: ProtoReader): String? {
            var value: String? = null
            var hasValue = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                val decoded = when (tag.fieldNumber) {
                    PRIMITIVE_NULL_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        consumeMarker(reader.readLengthDelimitedReader())
                        "@null"
                    }
                    PRIMITIVE_EMPTY_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        consumeMarker(reader.readLengthDelimitedReader())
                        ""
                    }
                    PRIMITIVE_FLOAT_FIELD,
                    PRIMITIVE_DIMENSION_DEPRECATED_FIELD,
                    PRIMITIVE_FRACTION_DEPRECATED_FIELD,
                    -> {
                        requireWire(tag, WIRE_FIXED32)
                        formatNumber(Float.fromBits(reader.readFixed32()))
                    }
                    PRIMITIVE_INT_DECIMAL_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        reader.readVarint().toInt().toString()
                    }
                    PRIMITIVE_INT_HEXADECIMAL_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        hex8(reader.readVarint().toInt())
                    }
                    PRIMITIVE_BOOLEAN_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        (reader.readVarint() != 0L).toString()
                    }
                    PRIMITIVE_COLOR_ARGB8_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        "#%08x".format(Locale.ROOT, reader.readVarint().toInt())
                    }
                    PRIMITIVE_COLOR_RGB8_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        "#%06x".format(Locale.ROOT, reader.readVarint().toInt() and 0xFFFFFF)
                    }
                    PRIMITIVE_COLOR_ARGB4_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        "#%04x".format(Locale.ROOT, reader.readVarint().toInt() and 0xFFFF)
                    }
                    PRIMITIVE_COLOR_RGB4_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        "#%03x".format(Locale.ROOT, reader.readVarint().toInt() and 0xFFF)
                    }
                    PRIMITIVE_DIMENSION_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        formatComplex(
                            reader.readVarint().toInt(),
                            DIMENSION_UNITS,
                            scale = 1f,
                        )
                    }
                    PRIMITIVE_FRACTION_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        formatComplex(
                            reader.readVarint().toInt(),
                            FRACTION_UNITS,
                            scale = 100f,
                        )
                    }
                    else -> {
                        reader.skip(tag.wireType)
                        continue
                    }
                }
                if (hasValue) throw malformed("compiled primitive contains multiple values")
                value = decoded
                hasValue = true
            }
            return value
        }

        private fun consumeMarker(reader: ProtoReader) {
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                reader.skip(tag.wireType)
            }
        }

        private fun reader(start: Int, end: Int): ProtoReader =
            ProtoReader(bytes, start, end, this)

        private fun decodeString(start: Int, end: Int, label: String): String {
            val value = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, end - start))
                    .toString()
            } catch (_: Exception) {
                throw malformed("$label is not valid UTF-8")
            }
            if (value.length > limits.maxSingleStringChars) {
                throw malformed("$label exceeds the single-string display limit")
            }
            if (value.length > limits.maxDecodedStringChars - decodedStringChars) {
                throw malformed("decoded strings exceed the display limit")
            }
            decodedStringChars += value.length
            return value
        }

        private fun countField() {
            fieldCount++
            if (fieldCount > limits.maxFields) {
                throw malformed("protobuf field count exceeds the display limit")
            }
        }

        private fun requireWire(tag: Tag, expected: Int) {
            if (tag.wireType != expected) {
                throw malformed(
                    "field ${tag.fieldNumber} has wire type ${tag.wireType}, expected $expected",
                )
            }
        }

        private inner class ProtoReader(
            private val source: ByteArray,
            start: Int,
            private val end: Int,
            private val decoder: ProtoXmlDecoder,
        ) {

            private var position = start

            init {
                if (start < 0 || end < start || end > source.size) {
                    throw malformed("message bounds exceed the input")
                }
            }

            fun isAtEnd(): Boolean = position == end

            fun readTag(): Tag {
                decoder.countField()
                val rawTag = readVarint()
                if (rawTag <= 0L || rawTag > Int.MAX_VALUE) {
                    throw malformed("invalid protobuf field tag")
                }
                val value = rawTag.toInt()
                val fieldNumber = value ushr 3
                if (fieldNumber == 0) throw malformed("invalid protobuf field number")
                return Tag(fieldNumber, value and 0x7)
            }

            fun readVarint(): Long {
                var value = 0L
                var shift = 0
                repeat(MAX_VARINT_BYTES) {
                    val byte = readByte()
                    if (shift == 63 && byte and 0xFE != 0) {
                        throw malformed("protobuf varint overflows")
                    }
                    value = value or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) return value
                    shift += 7
                }
                throw malformed("unterminated protobuf varint")
            }

            fun readFixed32(): Int {
                requireRemaining(Int.SIZE_BYTES)
                val value = (source[position].toInt() and 0xFF) or
                        ((source[position + 1].toInt() and 0xFF) shl 8) or
                        ((source[position + 2].toInt() and 0xFF) shl 16) or
                        ((source[position + 3].toInt() and 0xFF) shl 24)
                position += Int.SIZE_BYTES
                return value
            }

            fun readString(label: String): String {
                val bounds = readLengthDelimitedBounds()
                return decoder.decodeString(bounds.first, bounds.second, label)
            }

            fun readLengthDelimitedReader(): ProtoReader {
                val bounds = readLengthDelimitedBounds()
                return decoder.reader(bounds.first, bounds.second)
            }

            fun skip(wireType: Int) {
                when (wireType) {
                    WIRE_VARINT -> readVarint()
                    WIRE_FIXED64 -> skipBytes(Long.SIZE_BYTES)
                    WIRE_LENGTH_DELIMITED -> {
                        val bounds = readLengthDelimitedBounds()
                        position = bounds.second
                    }
                    WIRE_FIXED32 -> skipBytes(Int.SIZE_BYTES)
                    else -> throw malformed("unsupported protobuf wire type $wireType")
                }
            }

            private fun readLengthDelimitedBounds(): Pair<Int, Int> {
                val rawLength = readVarint()
                if (rawLength < 0 || rawLength > Int.MAX_VALUE) {
                    throw malformed("invalid length-delimited field size")
                }
                val length = rawLength.toInt()
                if (length > end - position) {
                    throw EOFException("Unexpected end of AAB manifest protobuf field")
                }
                val start = position
                position += length
                return start to position
            }

            private fun readByte(): Int {
                if (position >= end) {
                    throw EOFException("Unexpected end of AAB manifest protobuf")
                }
                return source[position++].toInt() and 0xFF
            }

            private fun skipBytes(count: Int) {
                requireRemaining(count)
                position += count
            }

            private fun requireRemaining(count: Int) {
                if (count < 0 || count > end - position) {
                    throw EOFException("Unexpected end of AAB manifest protobuf")
                }
            }
        }
    }

    private class ProtoXmlRenderer(private val limits: Limits) {

        private val output = LimitedTextBuilder(limits.maxOutputChars)
        private val activeNamespaces = ArrayList<XmlNamespace>()
        private val sanitizedNameCache = HashMap<String, String>()
        private var generatedPrefixIndex = 0

        fun render(root: XmlElement): String {
            output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            renderElement(root, depth = 0)
            return output.toString().trimEnd()
        }

        private fun renderElement(element: XmlElement, depth: Int) {
            val scopeStart = activeNamespaces.size
            val declarations = ArrayList<XmlNamespace>()
            val localPrefixes = HashSet<String>()
            element.namespaces.forEach { namespace ->
                val prefix = sanitizePrefix(namespace.prefix)
                if (!localPrefixes.add(prefix)) {
                    throw malformed("duplicate namespace prefix $prefix")
                }
                val declaration = XmlNamespace(prefix, namespace.uri)
                declarations += declaration
                activeNamespaces += declaration
            }
            val elementName = qualify(
                namespaceUri = element.namespaceUri,
                localName = element.name,
                attribute = false,
                declarations = declarations,
            )
            val attributes = element.attributes.map { attribute ->
                qualify(
                    namespaceUri = attribute.namespaceUri,
                    localName = attribute.name,
                    attribute = true,
                    declarations = declarations,
                ) to attribute.value
            }
            val duplicateAttribute = attributes.groupingBy { it.first }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
            if (duplicateAttribute != null) {
                throw malformed("duplicate XML attribute ${duplicateAttribute.key}")
            }
            output.appendIndent(depth)
            output.append('<')
            output.append(elementName)
            declarations.forEach { declaration ->
                output.append(" xmlns")
                if (declaration.prefix.isNotEmpty()) {
                    output.append(':')
                    output.append(declaration.prefix)
                }
                output.append("=\"")
                output.appendEscapedAttribute(declaration.uri)
                output.append('"')
            }
            attributes.forEach { (name, value) ->
                output.append(' ')
                output.append(name)
                output.append("=\"")
                output.appendEscapedAttribute(value)
                output.append('"')
            }
            val children = element.children.filterNot {
                it is XmlNode.Text && it.value.isBlank()
            }
            if (children.isEmpty()) {
                output.append(" />\n")
            } else {
                output.append(">\n")
                children.forEach { child ->
                    when (child) {
                        is XmlNode.Element -> renderElement(child.value, depth + 1)
                        is XmlNode.Text -> {
                            output.appendIndent(depth + 1)
                            output.appendEscapedText(child.value.trim())
                            output.append('\n')
                        }
                    }
                }
                output.appendIndent(depth)
                output.append("</")
                output.append(elementName)
                output.append(">\n")
            }
            activeNamespaces.subList(scopeStart, activeNamespaces.size).clear()
        }

        private fun qualify(
            namespaceUri: String,
            localName: String,
            attribute: Boolean,
            declarations: MutableList<XmlNamespace>,
        ): String {
            val safeLocalName = sanitizeXmlName(localName, "node")
            if (namespaceUri.isEmpty()) return safeLocalName
            val effectiveNamespaces = LinkedHashMap<String, String>()
            activeNamespaces.forEach { namespace ->
                effectiveNamespaces[namespace.prefix] = namespace.uri
            }
            val mappedPrefix = effectiveNamespaces.entries
                .lastOrNull { (_, uri) -> uri == namespaceUri }
                ?.key
                ?.takeUnless { attribute && it.isEmpty() }
            val prefix = mappedPrefix ?: generatePrefix(namespaceUri, declarations)
            return if (prefix.isEmpty()) safeLocalName else "$prefix:$safeLocalName"
        }

        private fun generatePrefix(
            namespaceUri: String,
            declarations: MutableList<XmlNamespace>,
        ): String {
            if (activeNamespaces.size >= limits.maxNamespaces) {
                throw malformed("XML namespace count exceeds the display limit")
            }
            val usedPrefixes = activeNamespaces.mapTo(HashSet()) { it.prefix }
            val preferred = if (namespaceUri == ANDROID_NAMESPACE) "android" else "ns"
            var prefix = preferred
            while (prefix in usedPrefixes) {
                prefix = "$preferred${generatedPrefixIndex++}"
            }
            val namespace = XmlNamespace(prefix, namespaceUri)
            declarations += namespace
            activeNamespaces += namespace
            return prefix
        }

        private fun sanitizePrefix(prefix: String): String {
            if (prefix.isEmpty()) return ""
            val sanitized = sanitizeXmlName(prefix, "ns")
            return if (sanitized == "xml" || sanitized == "xmlns") "_$sanitized" else sanitized
        }

        private fun sanitizeXmlName(name: String, fallback: String): String {
            if (name.isEmpty()) return fallback
            if (name.length > limits.maxXmlNameChars) {
                throw malformed("XML name exceeds the display limit")
            }
            return sanitizedNameCache[name] ?: buildString(name.length) {
                name.forEachIndexed { index, character ->
                    val valid = if (index == 0) {
                        character == '_' || character.isLetter()
                    } else {
                        character == '_' ||
                                character == '-' ||
                                character == '.' ||
                                character.isLetterOrDigit()
                    }
                    append(if (valid) character else '_')
                }
            }.also { sanitizedNameCache[name] = it }
        }
    }

    private class LimitedTextBuilder(private val maxChars: Int) {

        private val builder = StringBuilder(minOf(maxChars, 64 * 1024))

        fun append(value: CharSequence) {
            ensureCapacity(value.length)
            builder.append(value)
        }

        fun append(value: Char) {
            ensureCapacity(1)
            builder.append(value)
        }

        fun appendIndent(depth: Int) {
            if (depth < 0 || depth > maxChars / INDENT.length) {
                throw IOException("Formatted AAB manifest exceeds the display limit")
            }
            ensureCapacity(depth * INDENT.length)
            repeat(depth) { builder.append(INDENT) }
        }

        fun appendEscapedAttribute(value: String) {
            appendEscaped(value, attribute = true)
        }

        fun appendEscapedText(value: String) {
            appendEscaped(value, attribute = false)
        }

        private fun appendEscaped(value: String, attribute: Boolean) {
            value.forEach { character ->
                when (character) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> if (attribute) append("&quot;") else append(character)
                    '\'' -> if (attribute) append("&apos;") else append(character)
                    '\t', '\n', '\r' -> append(character)
                    else -> append(if (character.code < 0x20) '\uFFFD' else character)
                }
            }
        }

        private fun ensureCapacity(additional: Int) {
            if (additional < 0 || builder.length > maxChars - additional) {
                throw IOException("Formatted AAB manifest exceeds the display limit")
            }
        }

        override fun toString(): String = builder.toString()
    }

    private sealed interface XmlNode {
        data class Element(val value: XmlElement) : XmlNode
        data class Text(val value: String) : XmlNode
    }

    private data class XmlElement(
        val namespaceUri: String,
        val name: String,
        val namespaces: List<XmlNamespace>,
        val attributes: List<XmlAttribute>,
        val children: List<XmlNode>,
    )

    private data class XmlNamespace(val prefix: String, val uri: String)

    private data class XmlAttribute(
        val namespaceUri: String,
        val name: String,
        val value: String,
    )

    private data class Tag(val fieldNumber: Int, val wireType: Int)

    private fun validateLimits(limits: Limits) {
        val values = listOf(
            limits.maxInputBytes,
            limits.maxZipEntries,
            limits.maxFields,
            limits.maxNodes,
            limits.maxAttributes,
            limits.maxAttributesPerElement,
            limits.maxNamespaces,
            limits.maxDepth,
            limits.maxDecodedStringChars,
            limits.maxSingleStringChars,
            limits.maxXmlNameChars,
            limits.maxOutputChars,
        )
        if (values.any { it < 0 }) throw IllegalArgumentException("AAB decoder limits must be non-negative")
    }

    private fun formatComplex(data: Int, units: Array<String>, scale: Float): String {
        val unit = data and COMPLEX_UNIT_MASK
        if (unit !in units.indices) return hex8(data)
        val radix = (data ushr COMPLEX_RADIX_SHIFT) and COMPLEX_RADIX_MASK
        val value = (data and COMPLEX_MANTISSA_MASK).toFloat() * RADIX_MULTS[radix] * scale
        return formatNumber(value) + units[unit]
    }

    private fun formatNumber(number: Float): String {
        if (!number.isFinite()) return number.toString()
        return "%.4f".format(Locale.ROOT, number).trimEnd('0').trimEnd('.')
    }

    private fun hex8(value: Int): String = "0x%08x".format(Locale.ROOT, value)

    private fun malformed(detail: String): IOException =
        IOException("Invalid AAB manifest protobuf: $detail")

    private const val BASE_MANIFEST_ENTRY = "base/manifest/AndroidManifest.xml"
    private const val MODULE_MANIFEST_SUFFIX = "/manifest/AndroidManifest.xml"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val INDENT = "  "
    private const val MAX_VARINT_BYTES = 10

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private const val XML_NODE_ELEMENT_FIELD = 1
    private const val XML_NODE_TEXT_FIELD = 2

    private const val XML_ELEMENT_NAMESPACE_DECLARATION_FIELD = 1
    private const val XML_ELEMENT_NAMESPACE_URI_FIELD = 2
    private const val XML_ELEMENT_NAME_FIELD = 3
    private const val XML_ELEMENT_ATTRIBUTE_FIELD = 4
    private const val XML_ELEMENT_CHILD_FIELD = 5

    private const val XML_NAMESPACE_PREFIX_FIELD = 1
    private const val XML_NAMESPACE_URI_FIELD = 2

    private const val XML_ATTRIBUTE_NAMESPACE_URI_FIELD = 1
    private const val XML_ATTRIBUTE_NAME_FIELD = 2
    private const val XML_ATTRIBUTE_VALUE_FIELD = 3
    private const val XML_ATTRIBUTE_RESOURCE_ID_FIELD = 5
    private const val XML_ATTRIBUTE_COMPILED_ITEM_FIELD = 6

    private const val ITEM_REFERENCE_FIELD = 1
    private const val ITEM_STRING_FIELD = 2
    private const val ITEM_RAW_STRING_FIELD = 3
    private const val ITEM_STYLED_STRING_FIELD = 4
    private const val ITEM_PRIMITIVE_FIELD = 7
    private const val WRAPPED_STRING_VALUE_FIELD = 1

    private const val REFERENCE_TYPE_FIELD = 1
    private const val REFERENCE_ID_FIELD = 2
    private const val REFERENCE_NAME_FIELD = 3
    private const val REFERENCE_PRIVATE_FIELD = 4
    private const val REFERENCE_TYPE_RESOURCE = 0
    private const val REFERENCE_TYPE_ATTRIBUTE = 1

    private const val PRIMITIVE_NULL_FIELD = 1
    private const val PRIMITIVE_EMPTY_FIELD = 2
    private const val PRIMITIVE_FLOAT_FIELD = 3
    private const val PRIMITIVE_DIMENSION_DEPRECATED_FIELD = 4
    private const val PRIMITIVE_FRACTION_DEPRECATED_FIELD = 5
    private const val PRIMITIVE_INT_DECIMAL_FIELD = 6
    private const val PRIMITIVE_INT_HEXADECIMAL_FIELD = 7
    private const val PRIMITIVE_BOOLEAN_FIELD = 8
    private const val PRIMITIVE_COLOR_ARGB8_FIELD = 9
    private const val PRIMITIVE_COLOR_RGB8_FIELD = 10
    private const val PRIMITIVE_COLOR_ARGB4_FIELD = 11
    private const val PRIMITIVE_COLOR_RGB4_FIELD = 12
    private const val PRIMITIVE_DIMENSION_FIELD = 13
    private const val PRIMITIVE_FRACTION_FIELD = 14

    private const val COMPLEX_UNIT_MASK = 0xF
    private const val COMPLEX_RADIX_SHIFT = 4
    private const val COMPLEX_RADIX_MASK = 0x3
    private const val COMPLEX_MANTISSA_MASK = -0x100
    private val RADIX_MULTS = floatArrayOf(
        1.0f / (1 shl 8),
        1.0f / (1 shl 15),
        1.0f / (1 shl 23),
        1.0f / (1L shl 31),
    )
    private val DIMENSION_UNITS = arrayOf("px", "dp", "sp", "pt", "in", "mm")
    private val FRACTION_UNITS = arrayOf("%", "%p")
}
