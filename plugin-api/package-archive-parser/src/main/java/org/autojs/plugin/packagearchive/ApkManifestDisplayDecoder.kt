package org.autojs.plugin.packagearchive

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * A deliberately small, read-only AndroidManifest.xml decoder for the APK details screen.
 *
 * It only understands the binary XML container used by Android resources. It does not load
 * resources.arsc, mutate APK contents, verify signatures, or expose any APK-building API. Every
 * untrusted count and offset is checked against both the containing chunk and a display-oriented
 * budget before memory is allocated or text is appended.
 */
object ApkManifestDisplayDecoder {

    data class Limits(
        val maxInputBytes: Int = PackageInspectionLimits.MANIFEST_BYTES,
        val maxNodes: Int = 200_000,
        val maxStrings: Int = 800_000,
        val maxDecodedStringChars: Int = PackageInspectionLimits.MANIFEST_BYTES,
        val maxSingleStringChars: Int = 16 * 1024,
        val maxXmlNameChars: Int = 1_024,
        val maxDepth: Int = 256,
        val maxNamespaces: Int = 256,
        val maxAttributesPerElement: Int = 4_096,
        val maxOutputChars: Int = PackageInspectionLimits.MANIFEST_BYTES,
    )

    private val displayLimits = Limits()

    fun decode(apkFile: File): String {
        if (!apkFile.isFile) {
            throw IOException("APK file does not exist")
        }
        return ZipFile(apkFile).use { zipFile ->
            val entry = zipFile.getEntry(MANIFEST_ENTRY)
                ?.takeUnless { it.isDirectory }
                ?: throw IOException("$MANIFEST_ENTRY is missing")
            if (entry.size > displayLimits.maxInputBytes) {
                throw IOException("$MANIFEST_ENTRY exceeds the display size limit")
            }
            zipFile.getInputStream(entry).use { input ->
                decodeManifest(input.readBounded(displayLimits.maxInputBytes), displayLimits)
            }
        }
    }

    /**
     * Decodes an APK streamed from an outer package archive without materializing the nested APK.
     */
    fun decodeApk(input: InputStream): String {
        ZipInputStream(BufferedInputStream(BoundedInputStream(input, MAX_APK_SCAN_BYTES))).use { apk ->
            var entryCount = 0
            while (true) {
                val entry = apk.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_APK_ENTRY_COUNT) {
                    throw IOException("APK entry count exceeds the display limit")
                }
                if (!entry.isDirectory && entry.name == MANIFEST_ENTRY) {
                    if (entry.size > displayLimits.maxInputBytes) {
                        throw IOException("$MANIFEST_ENTRY exceeds the display size limit")
                    }
                    return decodeManifest(apk.readBounded(displayLimits.maxInputBytes), displayLimits)
                }
                apk.closeEntry()
            }
        }
        throw IOException("$MANIFEST_ENTRY is missing")
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maxBytes: Long,
    ) : FilterInputStream(input) {

        private var total = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) count(read.toLong())
            return read
        }

        override fun skip(byteCount: Long): Long {
            val skipped = super.skip(byteCount)
            if (skipped > 0L) count(skipped)
            return skipped
        }

        private fun count(bytes: Long) {
            total += bytes
            if (total > maxBytes) {
                throw IOException("APK manifest scan exceeds the display size limit")
            }
        }
    }

    fun decodeManifest(
        bytes: ByteArray,
        limits: Limits = displayLimits,
    ): String {
        if (bytes.isEmpty()) {
            throw IOException("$MANIFEST_ENTRY is empty")
        }
        if (bytes.size > limits.maxInputBytes) {
            throw IOException("$MANIFEST_ENTRY exceeds the display size limit")
        }
        return if (bytes.size >= CHUNK_HEADER_SIZE && readU16(bytes, 0) == RES_XML_TYPE) {
            BinaryXmlDecoder(bytes, limits).decode()
        } else {
            formatTextXml(decodeText(bytes), limits)
        }
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("$MANIFEST_ENTRY exceeds the display size limit")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        val (charset, start) = when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        return String(bytes, start, bytes.size - start, charset)
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { index -> this[index].toInt() and 0xFF == prefix[index] }
    }

    private fun formatTextXml(xml: String, limits: Limits): String {
        if (xml.firstOrNull { !it.isWhitespace() } != '<') {
            throw IOException("$MANIFEST_ENTRY is neither binary nor text XML")
        }
        val output = LimitedTextBuilder(limits.maxOutputChars)
        var cursor = 0
        var depth = 0
        while (cursor < xml.length) {
            val tagStart = xml.indexOf('<', cursor)
            if (tagStart < 0) {
                appendTextNode(output, xml.substring(cursor), depth)
                break
            }
            appendTextNode(output, xml.substring(cursor, tagStart), depth)
            val tagEnd = findTextTagEnd(xml, tagStart)
            val token = xml.substring(tagStart, tagEnd)
            val isClosing = token.startsWith("</")
            val isDeclaration = token.startsWith("<?")
            val isComment = token.startsWith("<!")
            val isSelfClosing = token.dropLast(1).trimEnd().endsWith("/")
            if (isClosing) {
                depth--
                if (depth < 0) throw IOException("Malformed text manifest nesting")
            }
            output.appendIndent(depth)
            output.append(token.trim())
            output.append('\n')
            if (!isClosing && !isDeclaration && !isComment && !isSelfClosing) {
                depth++
                if (depth > limits.maxDepth) {
                    throw IOException("Text manifest nesting exceeds the display limit")
                }
            }
            cursor = tagEnd
        }
        if (depth != 0) throw IOException("Malformed text manifest nesting")
        return output.toString().trimEnd()
    }

    private fun findTextTagEnd(xml: String, start: Int): Int {
        val terminator = when {
            xml.startsWith("<!--", start) -> "-->"
            xml.startsWith("<![CDATA[", start) -> "]]>"
            xml.startsWith("<?", start) -> "?>"
            else -> null
        }
        if (terminator != null) {
            val end = xml.indexOf(terminator, start + 2)
            if (end < 0) throw IOException("Unterminated text manifest token")
            return end + terminator.length
        }
        var quote = '\u0000'
        var cursor = start + 1
        while (cursor < xml.length) {
            val char = xml[cursor]
            when {
                quote != '\u0000' && char == quote -> quote = '\u0000'
                quote == '\u0000' && (char == '"' || char == '\'') -> quote = char
                quote == '\u0000' && char == '>' -> return cursor + 1
            }
            cursor++
        }
        throw IOException("Unterminated text manifest tag")
    }

    private fun appendTextNode(output: LimitedTextBuilder, text: String, depth: Int) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        output.appendIndent(depth)
        output.append(normalized)
        output.append('\n')
    }

    private class BinaryXmlDecoder(
        private val bytes: ByteArray,
        private val limits: Limits,
    ) {

        private var stringPool: StringPool? = null
        private var resourceIds = IntArray(0)
        private var nodeCount = 0
        private val renderer = BinaryXmlRenderer(limits)

        fun decode(): String {
            val root = readChunk(0, bytes.size)
            if (root.type != RES_XML_TYPE || root.headerSize < CHUNK_HEADER_SIZE) {
                throw IOException("Invalid binary XML header")
            }
            var cursor = root.start + root.headerSize
            while (cursor < root.end) {
                val chunk = readChunk(cursor, root.end)
                when (chunk.type) {
                    RES_STRING_POOL_TYPE -> readStringPool(chunk)
                    RES_XML_RESOURCE_MAP_TYPE -> readResourceMap(chunk)
                    RES_XML_START_NAMESPACE_TYPE -> {
                        countNode()
                        renderer.comment(readNodeComment(chunk))
                        readNamespace(chunk, start = true)
                    }
                    RES_XML_END_NAMESPACE_TYPE -> {
                        countNode()
                        readNamespace(chunk, start = false)
                    }
                    RES_XML_START_ELEMENT_TYPE -> {
                        countNode()
                        renderer.comment(readNodeComment(chunk))
                        readStartElement(chunk)
                    }
                    RES_XML_END_ELEMENT_TYPE -> {
                        countNode()
                        readEndElement(chunk)
                    }
                    RES_XML_CDATA_TYPE -> {
                        countNode()
                        renderer.comment(readNodeComment(chunk))
                        readCData(chunk)
                    }
                }
                cursor = chunk.end
            }
            if (cursor != root.end) throw IOException("Binary XML chunks do not fill the root")
            return renderer.finish()
        }

        private fun readStringPool(chunk: Chunk) {
            if (stringPool != null) throw IOException("Multiple binary XML string pools")
            stringPool = StringPool(bytes, chunk, limits)
        }

        private fun readResourceMap(chunk: Chunk) {
            val payloadSize = chunk.size - chunk.headerSize
            if (payloadSize % Int.SIZE_BYTES != 0) {
                throw IOException("Misaligned binary XML resource map")
            }
            val count = payloadSize / Int.SIZE_BYTES
            if (count > limits.maxStrings) {
                throw IOException("Binary XML resource map exceeds the display limit")
            }
            resourceIds = IntArray(count) { index ->
                readI32(bytes, chunk.start + chunk.headerSize + index * Int.SIZE_BYTES)
            }
        }

        private fun readNamespace(chunk: Chunk, start: Boolean) {
            val extension = nodeExtension(chunk, NAMESPACE_EXTENSION_SIZE)
            val prefix = strings().nullable(readI32(bytes, extension))
            val uri = strings().required(readI32(bytes, extension + Int.SIZE_BYTES))
            if (start) {
                renderer.startNamespace(prefix.orEmpty(), uri)
            } else {
                renderer.endNamespace(prefix.orEmpty(), uri)
            }
        }

        private fun readStartElement(chunk: Chunk) {
            val extension = nodeExtension(chunk, ATTRIBUTE_EXTENSION_SIZE)
            val namespace = strings().nullable(readI32(bytes, extension))
            val nameIndex = readI32(bytes, extension + 4)
            val name = strings().required(nameIndex)
            val attributeStart = readU16(bytes, extension + 8)
            val attributeSize = readU16(bytes, extension + 10)
            val attributeCount = readU16(bytes, extension + 12)
            if (attributeStart < ATTRIBUTE_EXTENSION_SIZE) {
                throw IOException("Invalid binary XML attribute start")
            }
            if (attributeSize < ATTRIBUTE_SIZE) {
                throw IOException("Invalid binary XML attribute size")
            }
            if (attributeCount > limits.maxAttributesPerElement) {
                throw IOException("Binary XML attribute count exceeds the display limit")
            }
            val attributesStart = checkedAdd(extension, attributeStart, chunk.end)
            val attributesBytes = checkedMultiply(attributeCount, attributeSize)
            val attributesEnd = checkedAdd(attributesStart, attributesBytes, chunk.end)
            if (attributesEnd > chunk.end) {
                throw IOException("Binary XML attributes exceed their chunk")
            }
            val attributes = ArrayList<DecodedAttribute>(attributeCount)
            var sharedUserIdSeen = false
            repeat(attributeCount) { index ->
                val offset = attributesStart + index * attributeSize
                val attributeNamespace = strings().nullable(readI32(bytes, offset))
                val attributeNameIndex = readI32(bytes, offset + 4)
                val declaredName = strings().nullable(attributeNameIndex)
                val verifiedName = ManifestRootAttributes.compiledName(attributeNamespace, declaredName,
                    resourceIds.getOrNull(attributeNameIndex) ?: 0)
                val attributeName = verifiedName
                    ?.takeIf { it.isNotEmpty() }
                    ?: fallbackAttributeName(attributeNameIndex)
                val rawValueIndex = readI32(bytes, offset + 8)
                val valueSize = readU16(bytes, offset + 12)
                if (valueSize < RES_VALUE_SIZE) {
                    throw IOException("Invalid binary XML typed value")
                }
                val dataType = readU8(bytes, offset + 15)
                val data = readI32(bytes, offset + 16)
                val rawValue = strings().nullable(rawValueIndex)
                val value = if (attributeNamespace == ManifestRootAttributes.ANDROID_NAMESPACE &&
                    attributeName == ManifestRootAttributes.SHARED_USER_ID) {
                    if (sharedUserIdSeen || dataType != TYPE_STRING) throw IOException("Shared UID attribute is duplicated or not a literal string")
                    sharedUserIdSeen = true
                    val typed = strings().required(data)
                    if (rawValue != null && rawValue != typed) throw IOException("Shared UID raw and compiled values disagree")
                    ManifestRootAttributes.requireLiteral(typed)
                } else rawValue ?: formatTypedValue(dataType, data)
                attributes += DecodedAttribute(attributeNamespace, attributeName, value)
            }
            renderer.startElement(namespace, name, attributes)
        }

        private fun readEndElement(chunk: Chunk) {
            val extension = nodeExtension(chunk, ELEMENT_EXTENSION_SIZE)
            val namespace = strings().nullable(readI32(bytes, extension))
            val name = strings().required(readI32(bytes, extension + 4))
            renderer.endElement(namespace, name)
        }

        private fun readCData(chunk: Chunk) {
            val extension = nodeExtension(chunk, CDATA_EXTENSION_SIZE)
            val dataIndex = readI32(bytes, extension)
            val raw = strings().nullable(dataIndex)
            val value = if (raw != null) {
                raw
            } else {
                val valueSize = readU16(bytes, extension + 4)
                if (valueSize < RES_VALUE_SIZE) {
                    throw IOException("Invalid binary XML CDATA value")
                }
                formatTypedValue(
                    dataType = readU8(bytes, extension + 7),
                    data = readI32(bytes, extension + 8),
                )
            }
            renderer.text(value)
        }

        private fun readNodeComment(chunk: Chunk): String? {
            if (chunk.headerSize < XML_NODE_HEADER_SIZE) {
                throw IOException("Invalid binary XML node header")
            }
            return strings().nullable(readI32(bytes, chunk.start + 12))
        }

        private fun nodeExtension(chunk: Chunk, requiredSize: Int): Int {
            if (chunk.headerSize < XML_NODE_HEADER_SIZE) {
                throw IOException("Invalid binary XML node header")
            }
            val extension = chunk.start + chunk.headerSize
            if (extension > chunk.end - requiredSize) {
                throw IOException("Truncated binary XML node extension")
            }
            return extension
        }

        private fun fallbackAttributeName(stringIndex: Int): String {
            val resourceId = resourceIds.getOrNull(stringIndex)
            return if (resourceId != null && resourceId != 0) {
                "attribute_${hex8(resourceId)}"
            } else {
                "attribute_$stringIndex"
            }
        }

        private fun formatTypedValue(dataType: Int, data: Int): String {
            return when (dataType) {
                TYPE_NULL -> if (data == DATA_NULL_EMPTY) "" else "@null"
                TYPE_REFERENCE, TYPE_DYNAMIC_REFERENCE -> "@${hex8(data)}"
                TYPE_ATTRIBUTE, TYPE_DYNAMIC_ATTRIBUTE -> "?${hex8(data)}"
                TYPE_STRING -> strings().required(data)
                TYPE_FLOAT -> formatNumber(Float.fromBits(data))
                TYPE_DIMENSION -> formatComplex(data, DIMENSION_UNITS, scale = 1f)
                TYPE_FRACTION -> formatComplex(data, FRACTION_UNITS, scale = 100f)
                TYPE_INT_DEC -> data.toString()
                TYPE_INT_HEX -> hex8(data)
                TYPE_INT_BOOLEAN -> (data != 0).toString()
                TYPE_INT_COLOR_ARGB8 -> "#%08x".format(Locale.ROOT, data)
                TYPE_INT_COLOR_RGB8 -> "#%06x".format(Locale.ROOT, data and 0xFFFFFF)
                TYPE_INT_COLOR_ARGB4 -> "#%04x".format(Locale.ROOT, data and 0xFFFF)
                TYPE_INT_COLOR_RGB4 -> "#%03x".format(Locale.ROOT, data and 0xFFF)
                in TYPE_FIRST_INT..TYPE_LAST_INT -> data.toString()
                else -> hex8(data)
            }
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

        private fun strings(): StringPool {
            return stringPool ?: throw IOException("Binary XML node precedes its string pool")
        }

        private fun countNode() {
            nodeCount++
            if (nodeCount > limits.maxNodes) {
                throw IOException("Binary XML node count exceeds the display limit")
            }
        }

        private fun readChunk(offset: Int, containerEnd: Int): Chunk {
            if (offset < 0 || offset > containerEnd - CHUNK_HEADER_SIZE) {
                throw EOFException("Truncated binary XML chunk header")
            }
            val type = readU16(bytes, offset)
            val headerSize = readU16(bytes, offset + 2)
            val size = readNonNegativeI32(bytes, offset + 4, "binary XML chunk size")
            if (headerSize < CHUNK_HEADER_SIZE || size < headerSize) {
                throw IOException("Invalid binary XML chunk size")
            }
            val end = checkedAdd(offset, size, containerEnd)
            return Chunk(type, headerSize, size, offset, end)
        }
    }

    private class StringPool(
        private val bytes: ByteArray,
        chunk: Chunk,
        private val limits: Limits,
    ) {

        private val stringCount: Int
        private val isUtf8: Boolean
        private val offsets: IntArray
        private val stringsStart: Int
        private val stringsEnd: Int
        private val cache: Array<String?>
        private var decodedChars = 0
        private val decodedFlags: BooleanArray

        init {
            if (chunk.headerSize < STRING_POOL_HEADER_SIZE) {
                throw IOException("Invalid binary XML string pool header")
            }
            stringCount = readNonNegativeI32(bytes, chunk.start + 8, "string count")
            val styleCount = readNonNegativeI32(bytes, chunk.start + 12, "style count")
            if (stringCount > limits.maxStrings || styleCount > limits.maxStrings) {
                throw IOException("Binary XML string pool exceeds the display limit")
            }
            val flags = readI32(bytes, chunk.start + 16)
            isUtf8 = flags and UTF8_FLAG != 0
            val stringsStartOffset = readNonNegativeI32(bytes, chunk.start + 20, "strings start")
            val stylesStartOffset = readNonNegativeI32(bytes, chunk.start + 24, "styles start")
            val offsetCount = checkedAdd(stringCount, styleCount, Int.MAX_VALUE)
            val offsetsBytes = checkedMultiply(offsetCount, Int.SIZE_BYTES)
            val offsetsEnd = checkedAdd(chunk.start + chunk.headerSize, offsetsBytes, chunk.end)
            if (offsetsEnd > chunk.end) {
                throw IOException("Binary XML string offsets exceed their chunk")
            }
            stringsStart = checkedAdd(chunk.start, stringsStartOffset, chunk.end)
            stringsEnd = if (stylesStartOffset == 0) {
                chunk.end
            } else {
                checkedAdd(chunk.start, stylesStartOffset, chunk.end)
            }
            if (stringsStart < offsetsEnd || stringsEnd < stringsStart) {
                throw IOException("Invalid binary XML string data range")
            }
            offsets = IntArray(stringCount) { index ->
                readNonNegativeI32(
                    bytes,
                    chunk.start + chunk.headerSize + index * Int.SIZE_BYTES,
                    "string offset",
                )
            }
            cache = arrayOfNulls(stringCount)
            decodedFlags = BooleanArray(stringCount)
        }

        fun required(index: Int): String {
            return nullable(index) ?: throw IOException("Missing binary XML string index")
        }

        fun nullable(index: Int): String? {
            if (index == NO_INDEX) return null
            if (index !in 0 until stringCount) {
                throw IOException("Binary XML string index is out of bounds")
            }
            if (decodedFlags[index]) return cache[index]
            val offset = checkedAdd(stringsStart, offsets[index], stringsEnd)
            val value = if (isUtf8) decodeUtf8(offset) else decodeUtf16(offset)
            if (value.length > limits.maxSingleStringChars) {
                throw IOException("Binary XML string exceeds the display limit")
            }
            decodedChars = checkedAdd(decodedChars, value.length, limits.maxDecodedStringChars)
            if (decodedChars > limits.maxDecodedStringChars) {
                throw IOException("Decoded binary XML strings exceed the display limit")
            }
            cache[index] = value
            decodedFlags[index] = true
            return value
        }

        private fun decodeUtf8(offset: Int): String {
            val (characterLength, afterUtf16Length) = readLength8(offset)
            val (byteLength, stringStart) = readLength8(afterUtf16Length)
            val maxByteLength = checkedMultiply(
                limits.maxSingleStringChars,
                MAX_UTF8_BYTES_PER_CHAR,
            )
            if (
                characterLength > limits.maxSingleStringChars ||
                byteLength > maxByteLength
            ) {
                throw IOException("Binary XML UTF-8 string exceeds the display limit")
            }
            val stringEnd = checkedAdd(stringStart, byteLength, stringsEnd)
            if (stringEnd >= stringsEnd || readU8(bytes, stringEnd) != 0) {
                throw IOException("Unterminated binary XML UTF-8 string")
            }
            return String(bytes, stringStart, byteLength, Charsets.UTF_8)
        }

        private fun decodeUtf16(offset: Int): String {
            val (characterLength, stringStart) = readLength16(offset)
            if (characterLength > limits.maxSingleStringChars) {
                throw IOException("Binary XML UTF-16 string exceeds the display limit")
            }
            val byteLength = checkedMultiply(characterLength, 2)
            val stringEnd = checkedAdd(stringStart, byteLength, stringsEnd)
            if (stringEnd > stringsEnd - 2 || readU16(bytes, stringEnd) != 0) {
                throw IOException("Unterminated binary XML UTF-16 string")
            }
            return String(bytes, stringStart, byteLength, Charset.forName("UTF-16LE"))
        }

        private fun readLength8(offset: Int): Pair<Int, Int> {
            val first = readU8Within(offset)
            return if (first and 0x80 == 0) {
                first to offset + 1
            } else {
                val second = readU8Within(offset + 1)
                (((first and 0x7F) shl 8) or second) to offset + 2
            }
        }

        private fun readLength16(offset: Int): Pair<Int, Int> {
            val first = readU16Within(offset)
            return if (first and 0x8000 == 0) {
                first to offset + 2
            } else {
                val second = readU16Within(offset + 2)
                (((first and 0x7FFF) shl 16) or second) to offset + 4
            }
        }

        private fun readU8Within(offset: Int): Int {
            if (offset !in stringsStart until stringsEnd) {
                throw EOFException("Truncated binary XML string")
            }
            return readU8(bytes, offset)
        }

        private fun readU16Within(offset: Int): Int {
            if (offset < stringsStart || offset > stringsEnd - 2) {
                throw EOFException("Truncated binary XML string")
            }
            return readU16(bytes, offset)
        }
    }

    private class BinaryXmlRenderer(private val limits: Limits) {

        private data class Namespace(val prefix: String, val uri: String)
        private data class Element(val namespace: String?, val localName: String, val qualifiedName: String)

        private val output = LimitedTextBuilder(limits.maxOutputChars)
        private val activeNamespaces = mutableListOf<Namespace>()
        private val pendingNamespaces = mutableListOf<Namespace>()
        private val elements = mutableListOf<Element>()
        private val sanitizedNameCache = HashMap<String, String>()
        private var pendingStart = false
        private var lastWasText = false
        private var rootSeen = false

        init {
            output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        }

        fun startNamespace(prefix: String, uri: String) {
            if (activeNamespaces.size >= limits.maxNamespaces) {
                throw IOException("Binary XML namespace count exceeds the display limit")
            }
            val normalizedPrefix = sanitizePrefix(prefix)
            if (pendingNamespaces.any { it.prefix == normalizedPrefix }) {
                throw IOException("Duplicate binary XML namespace declaration")
            }
            val namespace = Namespace(normalizedPrefix, uri)
            activeNamespaces += namespace
            pendingNamespaces += namespace
        }

        fun endNamespace(prefix: String, uri: String) {
            val normalizedPrefix = sanitizePrefix(prefix)
            val index = activeNamespaces.indexOfLast {
                it.prefix == normalizedPrefix && it.uri == uri
            }
            if (index < 0) throw IOException("Unbalanced binary XML namespace")
            activeNamespaces.removeAt(index)
            pendingNamespaces.removeAll { it.prefix == normalizedPrefix && it.uri == uri }
        }

        fun startElement(
            namespace: String?,
            localName: String,
            attributes: List<DecodedAttribute>,
        ) {
            flushPendingStart(newLine = true)
            if (elements.size >= limits.maxDepth) {
                throw IOException("Binary XML nesting exceeds the display limit")
            }
            val declarations = pendingNamespaces.toMutableList()
            pendingNamespaces.clear()
            val usedPrefixes = HashSet<String>(activeNamespaces.size + declarations.size).apply {
                activeNamespaces.forEach { add(it.prefix) }
                declarations.forEach { add(it.prefix) }
            }
            val currentNamespaceByPrefix = LinkedHashMap<String, Namespace>(activeNamespaces.size)
            activeNamespaces.forEach { active -> currentNamespaceByPrefix[active.prefix] = active }
            val elementPrefixes = HashMap<String, String>(currentNamespaceByPrefix.size)
            val attributePrefixes = HashMap<String, String>(currentNamespaceByPrefix.size)
            currentNamespaceByPrefix.values.forEach { active ->
                elementPrefixes[active.uri] = active.prefix
                if (active.prefix.isNotEmpty()) attributePrefixes[active.uri] = active.prefix
            }
            val qualifiedName = qualify(
                namespace,
                localName,
                attribute = false,
                declarations,
                usedPrefixes,
                elementPrefixes,
                attributePrefixes,
            )
            val qualifiedAttributes = attributes.map { attribute ->
                qualify(
                    namespace = attribute.namespace,
                    localName = attribute.name,
                    attribute = true,
                    declarations = declarations,
                    usedPrefixes = usedPrefixes,
                    elementPrefixes = elementPrefixes,
                    attributePrefixes = attributePrefixes,
                ) to attribute.value
            }
            if (lastWasText) output.append('\n')
            output.appendIndent(elements.size)
            output.append('<')
            output.append(qualifiedName)
            declarations.forEach { declaration ->
                output.append(' ')
                output.append("xmlns")
                if (declaration.prefix.isNotEmpty()) {
                    output.append(':')
                    output.append(declaration.prefix)
                }
                output.append("=\"")
                output.appendEscapedAttribute(declaration.uri)
                output.append('"')
            }
            qualifiedAttributes.forEach { (attributeName, attributeValue) ->
                output.append(' ')
                output.append(attributeName)
                output.append("=\"")
                output.appendEscapedAttribute(attributeValue)
                output.append('"')
            }
            elements += Element(namespace, localName, qualifiedName)
            pendingStart = true
            lastWasText = false
            rootSeen = true
        }

        fun endElement(namespace: String?, localName: String) {
            val element = elements.lastOrNull()
                ?: throw IOException("Unbalanced binary XML element")
            if (element.namespace != namespace || element.localName != localName) {
                throw IOException("Mismatched binary XML end element")
            }
            elements.removeAt(elements.lastIndex)
            if (pendingStart) {
                output.append(" />\n")
                pendingStart = false
            } else if (lastWasText) {
                output.append("</")
                output.append(element.qualifiedName)
                output.append(">\n")
            } else {
                output.appendIndent(elements.size)
                output.append("</")
                output.append(element.qualifiedName)
                output.append(">\n")
            }
            lastWasText = false
        }

        fun text(value: String) {
            flushPendingStart(newLine = false)
            output.appendEscapedText(value)
            lastWasText = true
        }

        fun comment(value: String?) {
            val comment = value?.takeIf { it.isNotBlank() } ?: return
            flushPendingStart(newLine = true)
            output.appendIndent(elements.size)
            output.append("<!-- ")
            output.append(comment.replace("--", "- -").trimEnd('-'))
            output.append(" -->\n")
            lastWasText = false
        }

        fun finish(): String {
            if (elements.isNotEmpty() || pendingStart) {
                throw IOException("Unclosed binary XML element")
            }
            if (!rootSeen) throw IOException("Binary XML contains no root element")
            return output.toString().trimEnd()
        }

        private fun flushPendingStart(newLine: Boolean) {
            if (!pendingStart) return
            output.append('>')
            if (newLine) output.append('\n')
            pendingStart = false
        }

        private fun qualify(
            namespace: String?,
            localName: String,
            attribute: Boolean,
            declarations: MutableList<Namespace>,
            usedPrefixes: MutableSet<String>,
            elementPrefixes: MutableMap<String, String>,
            attributePrefixes: MutableMap<String, String>,
        ): String {
            val safeLocalName = sanitizeXmlName(localName, "node")
            if (namespace.isNullOrEmpty()) return safeLocalName
            val mappedPrefix = if (attribute) {
                attributePrefixes[namespace]
            } else {
                elementPrefixes[namespace]
            }
            val prefix = mappedPrefix ?: generatePrefix(
                namespace,
                declarations,
                usedPrefixes,
                elementPrefixes,
                attributePrefixes,
            )
            return if (prefix.isEmpty()) safeLocalName else "$prefix:$safeLocalName"
        }

        private fun generatePrefix(
            namespace: String,
            declarations: MutableList<Namespace>,
            usedPrefixes: MutableSet<String>,
            elementPrefixes: MutableMap<String, String>,
            attributePrefixes: MutableMap<String, String>,
        ): String {
            if (declarations.size >= limits.maxNamespaces) {
                throw IOException("Binary XML namespace count exceeds the display limit")
            }
            val preferred = if (namespace == ANDROID_NAMESPACE) "android" else "ns"
            var candidate = preferred
            var suffix = 0
            while (candidate in usedPrefixes) {
                candidate = "$preferred${suffix++}"
            }
            declarations += Namespace(candidate, namespace)
            usedPrefixes += candidate
            elementPrefixes[namespace] = candidate
            attributePrefixes[namespace] = candidate
            return candidate
        }

        private fun sanitizePrefix(prefix: String): String {
            if (prefix.isEmpty()) return ""
            return sanitizeXmlName(prefix, "ns")
        }

        private fun sanitizeXmlName(name: String, fallback: String): String {
            if (name.isEmpty()) return fallback
            if (name.length > limits.maxXmlNameChars) {
                throw IOException("Binary XML name exceeds the display limit")
            }
            return sanitizedNameCache[name] ?: buildString(name.length) {
                name.forEachIndexed { index, char ->
                    val valid = if (index == 0) {
                        char == '_' || char.isLetter()
                    } else {
                        char == '_' || char == '-' || char == '.' || char.isLetterOrDigit()
                    }
                    append(if (valid) char else '_')
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
            val characters = checkedMultiply(depth, INDENT.length)
            ensureCapacity(characters)
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
                throw IOException("Formatted manifest exceeds the display limit")
            }
        }

        override fun toString(): String = builder.toString()
    }

    private data class Chunk(
        val type: Int,
        val headerSize: Int,
        val size: Int,
        val start: Int,
        val end: Int,
    )

    private data class DecodedAttribute(
        val namespace: String?,
        val name: String,
        val value: String,
    )

    private fun readU8(bytes: ByteArray, offset: Int): Int {
        if (offset !in bytes.indices) throw EOFException("Unexpected end of binary XML")
        return bytes[offset].toInt() and 0xFF
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > bytes.size - 2) {
            throw EOFException("Unexpected end of binary XML")
        }
        return readU8(bytes, offset) or (readU8(bytes, offset + 1) shl 8)
    }

    private fun readI32(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > bytes.size - 4) {
            throw EOFException("Unexpected end of binary XML")
        }
        return readU8(bytes, offset) or
                (readU8(bytes, offset + 1) shl 8) or
                (readU8(bytes, offset + 2) shl 16) or
                (readU8(bytes, offset + 3) shl 24)
    }

    private fun readNonNegativeI32(bytes: ByteArray, offset: Int, label: String): Int {
        return readI32(bytes, offset).takeIf { it >= 0 }
            ?: throw IOException("Invalid $label")
    }

    private fun checkedAdd(first: Int, second: Int, upperBound: Int): Int {
        if (first < 0 || second < 0 || first > upperBound - second) {
            throw IOException("Binary XML offset exceeds its container")
        }
        return first + second
    }

    private fun checkedMultiply(first: Int, second: Int): Int {
        if (first < 0 || second < 0 || first != 0 && second > Int.MAX_VALUE / first) {
            throw IOException("Binary XML size overflows")
        }
        return first * second
    }

    private fun hex8(value: Int): String = "0x%08x".format(Locale.ROOT, value)

    private const val MANIFEST_ENTRY = "AndroidManifest.xml"
    private const val MAX_APK_ENTRY_COUNT = PackageInspectionLimits.ARCHIVE_ENTRIES
    private const val MAX_APK_SCAN_BYTES = PackageInspectionLimits.PACKAGE_BYTES
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    private const val INDENT = "  "
    private const val MAX_UTF8_BYTES_PER_CHAR = 4

    private const val CHUNK_HEADER_SIZE = 8
    private const val STRING_POOL_HEADER_SIZE = 28
    private const val XML_NODE_HEADER_SIZE = 16
    private const val NAMESPACE_EXTENSION_SIZE = 8
    private const val ELEMENT_EXTENSION_SIZE = 8
    private const val ATTRIBUTE_EXTENSION_SIZE = 20
    private const val CDATA_EXTENSION_SIZE = 12
    private const val ATTRIBUTE_SIZE = 20
    private const val RES_VALUE_SIZE = 8

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

    private const val UTF8_FLAG = 0x00000100
    private const val NO_INDEX = -1

    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_DYNAMIC_REFERENCE = 0x07
    private const val TYPE_DYNAMIC_ATTRIBUTE = 0x08
    private const val TYPE_FIRST_INT = 0x10
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_INT_COLOR_ARGB8 = 0x1C
    private const val TYPE_INT_COLOR_RGB8 = 0x1D
    private const val TYPE_INT_COLOR_ARGB4 = 0x1E
    private const val TYPE_INT_COLOR_RGB4 = 0x1F
    private const val TYPE_LAST_INT = 0x1F
    private const val DATA_NULL_EMPTY = 1

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
