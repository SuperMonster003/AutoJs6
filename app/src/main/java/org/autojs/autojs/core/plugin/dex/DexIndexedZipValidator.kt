package org.autojs.autojs.core.plugin.dex

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal data class DexIndexedZipLimits(
    val maxContainerBytes: Long = 256L * 1024L * 1024L,
    val maxDexEntries: Int = 64,
    val maxDexEntryBytes: Long = 256L * 1024L * 1024L,
    val maxTotalDexBytes: Long = 256L * 1024L * 1024L,
    val maxCompressionRatio: Int = 100,
    val allowedDexVersions: Set<String> = setOf("035", "037", "038", "039", "040"),
) {
    init {
        require(maxContainerBytes > 0L)
        require(maxDexEntries > 0)
        require(maxDexEntryBytes >= DEX_HEADER_SIZE)
        require(maxTotalDexBytes >= DEX_HEADER_SIZE)
        require(maxCompressionRatio > 0)
        require(allowedDexVersions.isNotEmpty() && allowedDexVersions.all(::isCanonicalDexVersion))
    }
}

internal data class DexIndexedZipValidationResult(
    val dexEntryCount: Int,
    val totalDexBytes: Long,
    val classDefinitionCount: Int,
    val dexVersions: Set<String>,
)

internal enum class DexOutputValidationFailure {
    CONTAINER_QUOTA,
    MALFORMED_ZIP,
    UNEXPECTED_ZIP_ENTRY,
    NON_CONTIGUOUS_DEX_ENTRIES,
    DEX_QUOTA,
    INVALID_DEX_MAGIC,
    UNSUPPORTED_DEX_VERSION,
    INVALID_DEX_HEADER,
    INVALID_DEX_CHECKSUM,
    INVALID_DEX_SIGNATURE,
    INVALID_DEX_SECTION,
    INVALID_DEX_MAP,
    INVALID_CLASS_DESCRIPTOR,
    DUPLICATE_CLASS_DEFINITION,
}

internal class DexOutputValidationException(
    val failure: DexOutputValidationFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Validates the bounded D8 v1 output profile before an artifact may enter the host cache.
 *
 * This validates ZIP framing, DEX integrity, fixed-width header sections, map-list consistency,
 * referenced class descriptors, and duplicate class definitions. It deliberately does not claim
 * bytecode verification: instruction, annotation, encoded-value, debug-info, and control-flow
 * constraints remain the responsibility of Android's DEX verifier when the trusted host later
 * loads an already structurally accepted artifact.
 */
internal object DexIndexedZipValidator {

    private val MAP_TYPES = mapOf(
        TYPE_HEADER_ITEM to DexMapType(4L, DEX_HEADER_SIZE.toInt(), false),
        TYPE_STRING_ID_ITEM to DexMapType(4L, 4, false),
        TYPE_TYPE_ID_ITEM to DexMapType(4L, 4, false),
        TYPE_PROTO_ID_ITEM to DexMapType(4L, 12, false),
        TYPE_FIELD_ID_ITEM to DexMapType(4L, 8, false),
        TYPE_METHOD_ID_ITEM to DexMapType(4L, 8, false),
        TYPE_CLASS_DEF_ITEM to DexMapType(4L, 32, false),
        TYPE_CALL_SITE_ID_ITEM to DexMapType(4L, 4, false),
        TYPE_METHOD_HANDLE_ITEM to DexMapType(4L, 8, false),
        TYPE_MAP_LIST to DexMapType(4L, 4, true),
        TYPE_TYPE_LIST to DexMapType(4L, 4, true),
        TYPE_ANNOTATION_SET_REF_LIST to DexMapType(4L, 4, true),
        TYPE_ANNOTATION_SET_ITEM to DexMapType(4L, 4, true),
        TYPE_CLASS_DATA_ITEM to DexMapType(1L, 1, true),
        TYPE_CODE_ITEM to DexMapType(4L, 16, true),
        TYPE_STRING_DATA_ITEM to DexMapType(1L, 2, true),
        TYPE_DEBUG_INFO_ITEM to DexMapType(1L, 1, true),
        TYPE_ANNOTATION_ITEM to DexMapType(1L, 2, true),
        TYPE_ENCODED_ARRAY_ITEM to DexMapType(1L, 1, true),
        TYPE_ANNOTATIONS_DIRECTORY_ITEM to DexMapType(4L, 16, true),
        TYPE_HIDDENAPI_CLASS_DATA_ITEM to DexMapType(1L, 1, true),
    )

    fun validate(
        container: ByteArray,
        limits: DexIndexedZipLimits = DexIndexedZipLimits(),
    ): DexIndexedZipValidationResult {
        if (container.isEmpty() || container.size.toLong() > limits.maxContainerBytes) {
            fail(DexOutputValidationFailure.CONTAINER_QUOTA, "DEX container size is outside the host quota")
        }
        val entries = IndexedZipReader(container, limits).readDexEntries()
        val globallyDefinedClasses = HashSet<String>()
        val versions = linkedSetOf<String>()
        var classCount = 0L
        entries.forEach { entry ->
            val dex = validateDex(entry.name, entry.bytes, limits)
            versions += dex.version
            dex.classDescriptors.forEach { descriptor ->
                if (!globallyDefinedClasses.add(descriptor)) {
                    fail(
                        DexOutputValidationFailure.DUPLICATE_CLASS_DEFINITION,
                        "Class descriptor is defined by more than one DEX entry: $descriptor",
                    )
                }
            }
            classCount = checkedAdd(classCount, dex.classDescriptors.size.toLong(), "class definition count")
            if (classCount > Int.MAX_VALUE) {
                fail(DexOutputValidationFailure.DEX_QUOTA, "Class definition count is too large")
            }
        }
        return DexIndexedZipValidationResult(
            dexEntryCount = entries.size,
            totalDexBytes = entries.sumOf { it.bytes.size.toLong() },
            classDefinitionCount = classCount.toInt(),
            dexVersions = versions,
        )
    }

    private fun validateDex(
        entryName: String,
        bytes: ByteArray,
        limits: DexIndexedZipLimits,
    ): ValidatedDex {
        if (bytes.size < DEX_HEADER_SIZE || bytes.size.toLong() > limits.maxDexEntryBytes) {
            fail(DexOutputValidationFailure.DEX_QUOTA, "$entryName is outside the per-DEX quota")
        }
        if (!bytes.copyOfRange(0, 4).contentEquals(DEX_MAGIC_PREFIX)) {
            fail(DexOutputValidationFailure.INVALID_DEX_MAGIC, "$entryName has invalid DEX magic")
        }
        val version = bytes.copyOfRange(4, 7).toString(StandardCharsets.US_ASCII)
        if (bytes[7] != 0.toByte() || !isCanonicalDexVersion(version)) {
            fail(DexOutputValidationFailure.INVALID_DEX_MAGIC, "$entryName has malformed DEX version bytes")
        }
        if (version !in limits.allowedDexVersions) {
            fail(DexOutputValidationFailure.UNSUPPORTED_DEX_VERSION, "$entryName uses unnegotiated DEX version $version")
        }

        val header = DexHeader.parse(bytes, entryName)
        if (header.fileSize != bytes.size.toLong() || header.headerSize != DEX_HEADER_SIZE.toLong()) {
            fail(DexOutputValidationFailure.INVALID_DEX_HEADER, "$entryName has inconsistent file or header size")
        }
        if (header.endianTag != DEX_ENDIAN_CONSTANT) {
            fail(DexOutputValidationFailure.INVALID_DEX_HEADER, "$entryName is not a little-endian DEX file")
        }
        if (header.linkSize != 0L || header.linkOffset != 0L) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName contains unsupported linked DEX data")
        }
        validateIntegrity(bytes, header, entryName)

        val sections = header.fixedSections(entryName, bytes.size)
        validateFixedSectionOrder(sections, header.dataOffset, entryName)
        validateDataSection(header, bytes.size, entryName)
        val map = validateMap(bytes, header, sections, entryName)
        val descriptors = validateIndicesAndReadClassDescriptors(bytes, header, map, entryName)
        return ValidatedDex(version, descriptors)
    }

    private fun validateIntegrity(bytes: ByteArray, header: DexHeader, entryName: String) {
        val adler32 = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        if (header.checksum != adler32) {
            fail(DexOutputValidationFailure.INVALID_DEX_CHECKSUM, "$entryName has an invalid Adler-32 checksum")
        }
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        if (!MessageDigest.isEqual(signature, bytes.copyOfRange(12, 32))) {
            fail(DexOutputValidationFailure.INVALID_DEX_SIGNATURE, "$entryName has an invalid SHA-1 signature")
        }
    }

    private fun validateFixedSectionOrder(sections: List<DexSection>, dataOffset: Long, entryName: String) {
        var previousEnd = DEX_HEADER_SIZE.toLong()
        sections.forEach { section ->
            if (section.size == 0L) {
                if (section.offset != 0L) {
                    fail(
                        DexOutputValidationFailure.INVALID_DEX_SECTION,
                        "$entryName ${section.label} has an offset without items",
                    )
                }
                return@forEach
            }
            if (section.offset % section.alignment != 0L || section.offset < previousEnd) {
                fail(
                    DexOutputValidationFailure.INVALID_DEX_SECTION,
                    "$entryName ${section.label} is misaligned, overlapping, or out of order",
                )
            }
            val end = section.checkedEnd(entryName)
            if (end > dataOffset) {
                fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName ${section.label} overlaps data")
            }
            previousEnd = end
        }
    }

    private fun validateDataSection(header: DexHeader, fileSize: Int, entryName: String) {
        if (header.dataSize == 0L || header.dataOffset < DEX_HEADER_SIZE || header.dataOffset % 4L != 0L) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has an invalid data section")
        }
        val end = checkedAdd(header.dataOffset, header.dataSize, "$entryName data section")
        if (end != fileSize.toLong() || header.mapOffset < header.dataOffset ||
            header.mapOffset % 4L != 0L || header.mapOffset > fileSize - 4L
        ) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has inconsistent data or map offsets")
        }
    }

    private fun validateMap(
        bytes: ByteArray,
        header: DexHeader,
        fixedSections: List<DexSection>,
        entryName: String,
    ): DexMap {
        val mapOffset = header.mapOffset.toIntExact(entryName, "map offset")
        val count = bytes.u32(mapOffset)
        if (count == 0L || count > MAX_MAP_ITEMS) {
            fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName has an invalid map item count")
        }
        val mapBytes = checkedAdd(4L, checkedMultiply(count, MAP_ITEM_SIZE.toLong(), "map size"), "map size")
        val mapEnd = checkedAdd(header.mapOffset, mapBytes, "map end")
        if (mapEnd > bytes.size || mapEnd > checkedAdd(header.dataOffset, header.dataSize, "data end")) {
            fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map list exceeds the data section")
        }

        val items = ArrayList<DexMapItem>(count.toInt())
        val seenTypes = HashSet<Int>()
        var previousOffset = -1L
        repeat(count.toInt()) { index ->
            val offset = mapOffset + 4 + index * MAP_ITEM_SIZE
            val type = bytes.u16(offset)
            val unused = bytes.u16(offset + 2)
            val size = bytes.u32(offset + 4)
            val itemOffset = bytes.u32(offset + 8)
            val typeInfo = MAP_TYPES[type]
                ?: fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map contains unknown type 0x${type.toString(16)}")
            if (unused != 0 || size == 0L || !seenTypes.add(type)) {
                fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map contains malformed or duplicate items")
            }
            if (itemOffset <= previousOffset || itemOffset % typeInfo.alignment != 0L || itemOffset >= bytes.size) {
                fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map offsets are invalid or out of order")
            }
            items += DexMapItem(type, size, itemOffset, typeInfo)
            previousOffset = itemOffset
        }

        val map = DexMap(items, bytes.size.toLong())
        map.requireExact(TYPE_HEADER_ITEM, 1L, 0L, entryName)
        map.requireExact(TYPE_MAP_LIST, 1L, header.mapOffset, entryName)
        fixedSections.forEach { section ->
            if (section.size == 0L) {
                if (map.item(section.mapType) != null) {
                    fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map invents empty ${section.label}")
                }
            } else {
                map.requireExact(section.mapType, section.size, section.offset, entryName)
            }
        }

        items.forEachIndexed { index, item ->
            val upperBound = if (index + 1 < items.size) items[index + 1].offset else bytes.size.toLong()
            val minimumBytes = when (item.type) {
                TYPE_HEADER_ITEM -> DEX_HEADER_SIZE.toLong()
                TYPE_STRING_ID_ITEM -> checkedMultiply(item.size, 4L, "string IDs")
                TYPE_TYPE_ID_ITEM -> checkedMultiply(item.size, 4L, "type IDs")
                TYPE_PROTO_ID_ITEM -> checkedMultiply(item.size, 12L, "proto IDs")
                TYPE_FIELD_ID_ITEM -> checkedMultiply(item.size, 8L, "field IDs")
                TYPE_METHOD_ID_ITEM -> checkedMultiply(item.size, 8L, "method IDs")
                TYPE_CLASS_DEF_ITEM -> checkedMultiply(item.size, 32L, "class definitions")
                TYPE_MAP_LIST -> mapBytes
                else -> checkedMultiply(item.size, item.typeInfo.minimumItemBytes.toLong(), "mapped data")
            }
            if (checkedAdd(item.offset, minimumBytes, "mapped section") > upperBound) {
                fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map sections overlap or exceed their range")
            }
            if (item.typeInfo.isDataItem && item.offset < header.dataOffset) {
                fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName data map item precedes data_off")
            }
        }
        val mapItem = map.item(TYPE_MAP_LIST)!!
        if (mapEnd > map.upperBound(mapItem)) {
            fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map list overlaps the next mapped section")
        }
        return map
    }

    private fun validateIndicesAndReadClassDescriptors(
        bytes: ByteArray,
        header: DexHeader,
        map: DexMap,
        entryName: String,
    ): Set<String> {
        if (header.typeIdsSize > MAX_16_BIT_INDEX_COUNT || header.protoIdsSize > MAX_16_BIT_INDEX_COUNT ||
            header.fieldIdsSize > MAX_16_BIT_INDEX_COUNT || header.methodIdsSize > MAX_16_BIT_INDEX_COUNT ||
            header.classDefsSize > MAX_16_BIT_INDEX_COUNT
        ) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName exceeds a 16-bit DEX index limit")
        }
        val stringCount = header.stringIdsSize
        val typeCount = header.typeIdsSize
        var previousDescriptorIndex = -1L
        repeat(typeCount.toInt()) { index ->
            val descriptorIndex = bytes.u32(header.typeIdsOffset.toInt() + index * 4)
            if (descriptorIndex >= stringCount || descriptorIndex <= previousDescriptorIndex) {
                fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has invalid or unsorted type IDs")
            }
            previousDescriptorIndex = descriptorIndex
        }
        repeat(header.protoIdsSize.toInt()) { index ->
            val offset = header.protoIdsOffset.toInt() + index * 12
            requireIndex(bytes.u32(offset), stringCount, entryName, "proto shorty index")
            requireIndex(bytes.u32(offset + 4), typeCount, entryName, "proto return type")
            requireMappedReference(bytes.u32(offset + 8), TYPE_TYPE_LIST, map, entryName, "proto parameters")
        }
        repeat(header.fieldIdsSize.toInt()) { index ->
            val offset = header.fieldIdsOffset.toInt() + index * 8
            requireIndex(bytes.u16(offset).toLong(), typeCount, entryName, "field class index")
            requireIndex(bytes.u16(offset + 2).toLong(), typeCount, entryName, "field type index")
            requireIndex(bytes.u32(offset + 4), stringCount, entryName, "field name index")
        }
        repeat(header.methodIdsSize.toInt()) { index ->
            val offset = header.methodIdsOffset.toInt() + index * 8
            requireIndex(bytes.u16(offset).toLong(), typeCount, entryName, "method class index")
            requireIndex(bytes.u16(offset + 2).toLong(), header.protoIdsSize, entryName, "method proto index")
            requireIndex(bytes.u32(offset + 4), stringCount, entryName, "method name index")
        }

        val descriptors = linkedSetOf<String>()
        var previousClassIndex = -1L
        repeat(header.classDefsSize.toInt()) { index ->
            val offset = header.classDefsOffset.toInt() + index * 32
            val classIndex = bytes.u32(offset)
            if (classIndex >= typeCount || classIndex <= previousClassIndex) {
                fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has invalid or unsorted class definitions")
            }
            previousClassIndex = classIndex
            val superclassIndex = bytes.u32(offset + 8)
            if (superclassIndex != NO_INDEX) requireIndex(superclassIndex, typeCount, entryName, "superclass index")
            requireMappedReference(bytes.u32(offset + 12), TYPE_TYPE_LIST, map, entryName, "interfaces")
            val sourceFileIndex = bytes.u32(offset + 16)
            if (sourceFileIndex != NO_INDEX) requireIndex(sourceFileIndex, stringCount, entryName, "source file index")
            requireMappedReference(
                bytes.u32(offset + 20),
                TYPE_ANNOTATIONS_DIRECTORY_ITEM,
                map,
                entryName,
                "annotations",
            )
            requireMappedReference(bytes.u32(offset + 24), TYPE_CLASS_DATA_ITEM, map, entryName, "class data")
            requireMappedReference(bytes.u32(offset + 28), TYPE_ENCODED_ARRAY_ITEM, map, entryName, "static values")

            val typeOffset = header.typeIdsOffset.toInt() + classIndex.toInt() * 4
            val descriptorIndex = bytes.u32(typeOffset)
            val stringIdOffset = header.stringIdsOffset.toInt() + descriptorIndex.toInt() * 4
            val stringDataOffset = bytes.u32(stringIdOffset)
            requireMappedReference(stringDataOffset, TYPE_STRING_DATA_ITEM, map, entryName, "class descriptor")
            val descriptor = readModifiedUtf8(bytes, stringDataOffset.toInt(), map.upperBound(TYPE_STRING_DATA_ITEM), entryName)
            if (!isClassDescriptor(descriptor)) {
                fail(DexOutputValidationFailure.INVALID_CLASS_DESCRIPTOR, "$entryName has invalid class descriptor $descriptor")
            }
            if (!descriptors.add(descriptor)) {
                fail(DexOutputValidationFailure.DUPLICATE_CLASS_DEFINITION, "$entryName defines $descriptor more than once")
            }
        }
        return descriptors
    }

    private fun requireIndex(index: Long, count: Long, entryName: String, label: String) {
        if (index >= count) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has an invalid $label")
        }
    }

    private fun requireMappedReference(
        offset: Long,
        type: Int,
        map: DexMap,
        entryName: String,
        label: String,
    ) {
        if (offset == 0L) return
        val item = map.item(type)
            ?: fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName $label has no corresponding map item")
        if (offset < item.offset || offset >= map.upperBound(item)) {
            fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName has an out-of-range $label offset")
        }
    }

    private fun readModifiedUtf8(
        bytes: ByteArray,
        offset: Int,
        upperBound: Long,
        entryName: String,
    ): String {
        var cursor = offset
        val (declaredUtf16Length, afterLength) = readUnsignedLeb128(bytes, cursor, upperBound, entryName)
        cursor = afterLength
        if (declaredUtf16Length > MAX_DESCRIPTOR_UTF16_UNITS) {
            fail(DexOutputValidationFailure.INVALID_CLASS_DESCRIPTOR, "$entryName class descriptor is too long")
        }
        val result = StringBuilder(declaredUtf16Length)
        while (cursor < upperBound) {
            val first = bytes[cursor].toInt() and 0xff
            cursor++
            if (first == 0) {
                if (result.length != declaredUtf16Length) {
                    fail(DexOutputValidationFailure.INVALID_CLASS_DESCRIPTOR, "$entryName descriptor length is inconsistent")
                }
                return result.toString()
            }
            when {
                first in 0x01..0x7f -> result.append(first.toChar())
                first in 0xc0..0xdf -> {
                    val second = continuationByte(bytes, cursor++, upperBound, entryName)
                    val value = ((first and 0x1f) shl 6) or second
                    if (value == 0) {
                        result.append('\u0000')
                    } else {
                        if (value < 0x80) invalidModifiedUtf8(entryName)
                        result.append(value.toChar())
                    }
                }
                first in 0xe0..0xef -> {
                    val second = continuationByte(bytes, cursor++, upperBound, entryName)
                    val third = continuationByte(bytes, cursor++, upperBound, entryName)
                    val value = ((first and 0x0f) shl 12) or (second shl 6) or third
                    if (value < 0x800) invalidModifiedUtf8(entryName)
                    result.append(value.toChar())
                }
                else -> invalidModifiedUtf8(entryName)
            }
            if (result.length > declaredUtf16Length) invalidModifiedUtf8(entryName)
        }
        invalidModifiedUtf8(entryName)
    }

    private fun continuationByte(bytes: ByteArray, offset: Int, upperBound: Long, entryName: String): Int {
        if (offset >= upperBound || offset >= bytes.size) invalidModifiedUtf8(entryName)
        val value = bytes[offset].toInt() and 0xff
        if (value and 0xc0 != 0x80) invalidModifiedUtf8(entryName)
        return value and 0x3f
    }

    private fun invalidModifiedUtf8(entryName: String): Nothing = fail(
        DexOutputValidationFailure.INVALID_CLASS_DESCRIPTOR,
        "$entryName contains malformed modified UTF-8",
    )

    private fun readUnsignedLeb128(
        bytes: ByteArray,
        offset: Int,
        upperBound: Long,
        entryName: String,
    ): Pair<Int, Int> {
        var result = 0L
        var cursor = offset
        var count = 0
        while (count < 5) {
            if (cursor >= upperBound || cursor >= bytes.size) invalidModifiedUtf8(entryName)
            val value = bytes[cursor++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl (count * 7))
            count++
            if (value and 0x80 == 0) {
                if (result > Int.MAX_VALUE || (count > 1 && value == 0)) invalidModifiedUtf8(entryName)
                return result.toInt() to cursor
            }
        }
        invalidModifiedUtf8(entryName)
    }

    private fun isClassDescriptor(value: String): Boolean {
        if (value.length < 3 || value.first() != 'L' || value.last() != ';') return false
        val body = value.substring(1, value.length - 1)
        if (body.startsWith('/') || body.endsWith('/') || "//" in body) return false
        return body.none { it == '.' || it == ';' || it == '[' || it == '\u0000' || it.isISOControl() }
    }

    private data class ValidatedDex(val version: String, val classDescriptors: Set<String>)

    private data class DexHeader(
        val checksum: Long,
        val fileSize: Long,
        val headerSize: Long,
        val endianTag: Long,
        val linkSize: Long,
        val linkOffset: Long,
        val mapOffset: Long,
        val stringIdsSize: Long,
        val stringIdsOffset: Long,
        val typeIdsSize: Long,
        val typeIdsOffset: Long,
        val protoIdsSize: Long,
        val protoIdsOffset: Long,
        val fieldIdsSize: Long,
        val fieldIdsOffset: Long,
        val methodIdsSize: Long,
        val methodIdsOffset: Long,
        val classDefsSize: Long,
        val classDefsOffset: Long,
        val dataSize: Long,
        val dataOffset: Long,
    ) {
        fun fixedSections(entryName: String, fileSize: Int): List<DexSection> = listOf(
            DexSection("string IDs", TYPE_STRING_ID_ITEM, stringIdsSize, stringIdsOffset, 4L),
            DexSection("type IDs", TYPE_TYPE_ID_ITEM, typeIdsSize, typeIdsOffset, 4L),
            DexSection("proto IDs", TYPE_PROTO_ID_ITEM, protoIdsSize, protoIdsOffset, 12L),
            DexSection("field IDs", TYPE_FIELD_ID_ITEM, fieldIdsSize, fieldIdsOffset, 8L),
            DexSection("method IDs", TYPE_METHOD_ID_ITEM, methodIdsSize, methodIdsOffset, 8L),
            DexSection("class definitions", TYPE_CLASS_DEF_ITEM, classDefsSize, classDefsOffset, 32L),
        ).onEach { section ->
            if (section.size > Int.MAX_VALUE || section.offset > fileSize.toLong()) {
                fail(DexOutputValidationFailure.INVALID_DEX_SECTION, "$entryName ${section.label} is outside the file")
            }
        }

        companion object {
            fun parse(bytes: ByteArray, entryName: String): DexHeader = try {
                DexHeader(
                    checksum = bytes.u32(8),
                    fileSize = bytes.u32(32),
                    headerSize = bytes.u32(36),
                    endianTag = bytes.u32(40),
                    linkSize = bytes.u32(44),
                    linkOffset = bytes.u32(48),
                    mapOffset = bytes.u32(52),
                    stringIdsSize = bytes.u32(56),
                    stringIdsOffset = bytes.u32(60),
                    typeIdsSize = bytes.u32(64),
                    typeIdsOffset = bytes.u32(68),
                    protoIdsSize = bytes.u32(72),
                    protoIdsOffset = bytes.u32(76),
                    fieldIdsSize = bytes.u32(80),
                    fieldIdsOffset = bytes.u32(84),
                    methodIdsSize = bytes.u32(88),
                    methodIdsOffset = bytes.u32(92),
                    classDefsSize = bytes.u32(96),
                    classDefsOffset = bytes.u32(100),
                    dataSize = bytes.u32(104),
                    dataOffset = bytes.u32(108),
                )
            } catch (e: IndexOutOfBoundsException) {
                fail(DexOutputValidationFailure.INVALID_DEX_HEADER, "$entryName has a truncated DEX header", e)
            }
        }
    }

    private data class DexSection(
        val label: String,
        val mapType: Int,
        val size: Long,
        val offset: Long,
        val itemWidth: Long,
        val alignment: Long = 4L,
    ) {
        fun checkedEnd(entryName: String): Long = checkedAdd(
            offset,
            checkedMultiply(size, itemWidth, "$entryName $label"),
            "$entryName $label",
        )
    }

    private data class DexMapType(
        val alignment: Long,
        val minimumItemBytes: Int,
        val isDataItem: Boolean,
    )

    private data class DexMapItem(
        val type: Int,
        val size: Long,
        val offset: Long,
        val typeInfo: DexMapType,
    )

    private class DexMap(
        private val items: List<DexMapItem>,
        private val fileSize: Long,
    ) {
        fun item(type: Int): DexMapItem? = items.firstOrNull { it.type == type }

        fun upperBound(type: Int): Long = item(type)?.let(::upperBound)
            ?: fail(DexOutputValidationFailure.INVALID_DEX_MAP, "Required DEX map type is absent")

        fun upperBound(item: DexMapItem): Long {
            val index = items.indexOf(item)
            return if (index + 1 < items.size) items[index + 1].offset else fileSize
        }

        fun requireExact(type: Int, size: Long, offset: Long, entryName: String) {
            val item = item(type)
            if (item == null || item.size != size || item.offset != offset) {
                fail(DexOutputValidationFailure.INVALID_DEX_MAP, "$entryName map does not match its header sections")
            }
        }
    }

    private data class DexZipEntry(val name: String, val bytes: ByteArray)

    private class IndexedZipReader(
        private val bytes: ByteArray,
        private val limits: DexIndexedZipLimits,
    ) {
        fun readDexEntries(): List<DexZipEntry> {
            if (bytes.size < ZIP_EOCD_SIZE || bytes.leInt(bytes.size - ZIP_EOCD_SIZE) != ZIP_EOCD_SIGNATURE) {
                malformedZip("ZIP end-of-central-directory record is missing or has a comment")
            }
            val eocd = bytes.size - ZIP_EOCD_SIZE
            if (bytes.leU16(eocd + 4) != 0 || bytes.leU16(eocd + 6) != 0 || bytes.leU16(eocd + 20) != 0) {
                malformedZip("Multi-disk, ZIP64, and commented output archives are forbidden")
            }
            val entriesOnDisk = bytes.leU16(eocd + 8)
            val entryCount = bytes.leU16(eocd + 10)
            if (entryCount == 0 || entryCount != entriesOnDisk || entryCount > limits.maxDexEntries ||
                entryCount == ZIP64_U16_SENTINEL
            ) {
                fail(DexOutputValidationFailure.DEX_QUOTA, "DEX entry count is invalid or exceeds quota")
            }
            val centralSize = bytes.leU32(eocd + 12)
            val centralOffset = bytes.leU32(eocd + 16)
            if (centralSize == ZIP64_U32_SENTINEL || centralOffset == ZIP64_U32_SENTINEL ||
                checkedAdd(centralOffset, centralSize, "ZIP central directory") != eocd.toLong()
            ) {
                malformedZip("ZIP central-directory bounds are inconsistent")
            }

            val centralEntries = readCentralEntries(centralOffset.toInt(), centralSize.toInt(), entryCount)
            val output = ArrayList<DexZipEntry>(centralEntries.size)
            var totalDexBytes = 0L
            var totalCompressedBytes = 0L
            centralEntries.forEachIndexed { index, central ->
                val expectedName = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
                if (central.name != expectedName) {
                    val failure = if (dexIndex(central.name) != null) {
                        DexOutputValidationFailure.NON_CONTIGUOUS_DEX_ENTRIES
                    } else {
                        DexOutputValidationFailure.UNEXPECTED_ZIP_ENTRY
                    }
                    fail(failure, "Expected $expectedName but found ${central.name}")
                }
                val nextOffset = if (index + 1 < centralEntries.size) {
                    centralEntries[index + 1].localHeaderOffset
                } else {
                    centralOffset
                }
                val content = readLocalEntry(central, nextOffset)
                totalDexBytes = checkedAdd(totalDexBytes, content.size.toLong(), "total DEX bytes")
                totalCompressedBytes = checkedAdd(totalCompressedBytes, central.compressedSize, "total compressed bytes")
                if (content.size.toLong() != central.uncompressedSize ||
                    content.size.toLong() > limits.maxDexEntryBytes || totalDexBytes > limits.maxTotalDexBytes
                ) {
                    fail(DexOutputValidationFailure.DEX_QUOTA, "DEX output exceeds its declared or negotiated quota")
                }
                enforceCompressionRatio(content.size.toLong(), central.compressedSize)
                output += DexZipEntry(central.name, content)
            }
            enforceCompressionRatio(totalDexBytes, totalCompressedBytes)
            return output
        }

        private fun readCentralEntries(offset: Int, declaredSize: Int, count: Int): List<CentralEntry> {
            val entries = ArrayList<CentralEntry>(count)
            var cursor = offset
            repeat(count) {
                ensureRange(cursor, ZIP_CENTRAL_HEADER_SIZE, bytes.size, "central directory header")
                if (bytes.leInt(cursor) != ZIP_CENTRAL_SIGNATURE) malformedZip("Invalid central-directory signature")
                val flags = bytes.leU16(cursor + 8)
                val method = bytes.leU16(cursor + 10)
                validateZipFlagsAndMethod(flags, method)
                val crc32 = bytes.leU32(cursor + 16)
                val compressedSize = bytes.leU32(cursor + 20)
                val uncompressedSize = bytes.leU32(cursor + 24)
                val nameLength = bytes.leU16(cursor + 28)
                val extraLength = bytes.leU16(cursor + 30)
                val commentLength = bytes.leU16(cursor + 32)
                val diskStart = bytes.leU16(cursor + 34)
                val localOffset = bytes.leU32(cursor + 42)
                if (compressedSize == ZIP64_U32_SENTINEL || uncompressedSize == ZIP64_U32_SENTINEL ||
                    localOffset == ZIP64_U32_SENTINEL || diskStart != 0
                ) {
                    malformedZip("ZIP64 and multi-disk entries are forbidden")
                }
                val recordSize = ZIP_CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength
                ensureRange(cursor, recordSize, bytes.size, "central directory entry")
                val name = decodeZipName(cursor + ZIP_CENTRAL_HEADER_SIZE, nameLength, flags)
                validateExtraFields(cursor + ZIP_CENTRAL_HEADER_SIZE + nameLength, extraLength)
                if (commentLength != 0) malformedZip("Per-entry ZIP comments are forbidden")
                if (entries.any { it.name == name }) malformedZip("Duplicate ZIP entry name: $name")
                entries += CentralEntry(
                    name = name,
                    flags = flags,
                    method = method,
                    crc32 = crc32,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    localHeaderOffset = localOffset,
                )
                cursor += recordSize
            }
            if (cursor != checkedAdd(offset.toLong(), declaredSize.toLong(), "central size").toInt()) {
                malformedZip("Central-directory entry count does not match its declared size")
            }
            entries.zipWithNext().forEach { (first, second) ->
                if (first.localHeaderOffset >= second.localHeaderOffset) {
                    malformedZip("Local ZIP entries are duplicated or out of order")
                }
            }
            if (entries.firstOrNull()?.localHeaderOffset != 0L) {
                malformedZip("Bytes precede the first local ZIP entry")
            }
            return entries
        }

        private fun readLocalEntry(entry: CentralEntry, expectedEnd: Long): ByteArray {
            val cursor = entry.localHeaderOffset.toIntExact("ZIP", "local header offset")
            ensureRange(cursor, ZIP_LOCAL_HEADER_SIZE, bytes.size, "local ZIP header")
            if (bytes.leInt(cursor) != ZIP_LOCAL_SIGNATURE) malformedZip("Invalid local ZIP header signature")
            val flags = bytes.leU16(cursor + 6)
            val method = bytes.leU16(cursor + 8)
            if (flags != entry.flags || method != entry.method) malformedZip("Local and central ZIP metadata disagree")
            val localCrc = bytes.leU32(cursor + 14)
            val localCompressed = bytes.leU32(cursor + 18)
            val localUncompressed = bytes.leU32(cursor + 22)
            val nameLength = bytes.leU16(cursor + 26)
            val extraLength = bytes.leU16(cursor + 28)
            val dataOffset = cursor + ZIP_LOCAL_HEADER_SIZE + nameLength + extraLength
            ensureRange(cursor, ZIP_LOCAL_HEADER_SIZE + nameLength + extraLength, bytes.size, "local ZIP metadata")
            if (decodeZipName(cursor + ZIP_LOCAL_HEADER_SIZE, nameLength, flags) != entry.name) {
                malformedZip("Local and central ZIP names disagree")
            }
            validateExtraFields(cursor + ZIP_LOCAL_HEADER_SIZE + nameLength, extraLength)
            val hasDescriptor = flags and ZIP_FLAG_DATA_DESCRIPTOR != 0
            if (!hasDescriptor && (localCrc != entry.crc32 || localCompressed != entry.compressedSize ||
                    localUncompressed != entry.uncompressedSize)
            ) {
                malformedZip("Local and central ZIP sizes or CRC disagree")
            }
            if (hasDescriptor && !((localCrc == 0L || localCrc == entry.crc32) &&
                    (localCompressed == 0L || localCompressed == entry.compressedSize) &&
                    (localUncompressed == 0L || localUncompressed == entry.uncompressedSize))
            ) {
                malformedZip("Local data-descriptor placeholders are inconsistent")
            }
            val dataEnd = checkedAdd(dataOffset.toLong(), entry.compressedSize, "compressed ZIP data")
            if (dataEnd > expectedEnd || dataEnd > bytes.size) malformedZip("Compressed ZIP data exceeds its entry")
            if (hasDescriptor) {
                validateDataDescriptor(dataEnd.toInt(), expectedEnd, entry)
            } else if (dataEnd != expectedEnd) {
                malformedZip("Unaccounted bytes follow a local ZIP entry")
            }
            val compressed = bytes.copyOfRange(dataOffset, dataEnd.toInt())
            val content = when (method) {
                ZIP_METHOD_STORED -> {
                    if (entry.compressedSize != entry.uncompressedSize) malformedZip("Stored ZIP entry sizes disagree")
                    compressed
                }
                ZIP_METHOD_DEFLATED -> inflate(compressed, entry.uncompressedSize)
                else -> malformedZip("Unsupported ZIP compression method")
            }
            val actualCrc = CRC32().apply { update(content) }.value
            if (actualCrc != entry.crc32) malformedZip("ZIP entry CRC is invalid")
            return content
        }

        private fun validateDataDescriptor(offset: Int, expectedEnd: Long, entry: CentralEntry) {
            val length = expectedEnd - offset
            val valuesOffset = when (length) {
                12L -> offset
                16L -> {
                    if (bytes.leInt(offset) != ZIP_DATA_DESCRIPTOR_SIGNATURE) {
                        malformedZip("Invalid ZIP data-descriptor signature")
                    }
                    offset + 4
                }
                else -> malformedZip("ZIP data descriptor has an invalid size")
            }
            ensureRange(valuesOffset, 12, bytes.size, "ZIP data descriptor")
            if (bytes.leU32(valuesOffset) != entry.crc32 ||
                bytes.leU32(valuesOffset + 4) != entry.compressedSize ||
                bytes.leU32(valuesOffset + 8) != entry.uncompressedSize
            ) {
                malformedZip("ZIP data descriptor disagrees with the central directory")
            }
        }

        private fun inflate(compressed: ByteArray, declaredSize: Long): ByteArray {
            if (declaredSize > limits.maxDexEntryBytes || declaredSize > Int.MAX_VALUE) {
                fail(DexOutputValidationFailure.DEX_QUOTA, "Declared DEX entry is too large")
            }
            val inflater = Inflater(true)
            return try {
                inflater.setInput(compressed)
                val output = ByteArrayOutputStream(minOf(declaredSize.toInt(), 64 * 1024))
                val buffer = ByteArray(8192)
                var total = 0L
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) {
                        if (inflater.needsDictionary() || inflater.needsInput()) malformedZip("Truncated deflated ZIP entry")
                        malformedZip("Deflated ZIP entry made no progress")
                    }
                    total = checkedAdd(total, count.toLong(), "inflated DEX bytes")
                    if (total > declaredSize || total > limits.maxDexEntryBytes || total > limits.maxTotalDexBytes) {
                        fail(DexOutputValidationFailure.DEX_QUOTA, "Inflated DEX entry exceeds quota")
                    }
                    output.write(buffer, 0, count)
                }
                if (inflater.remaining != 0 || total != declaredSize) malformedZip("Deflated ZIP size is inconsistent")
                output.toByteArray()
            } catch (e: DataFormatException) {
                malformedZip("Deflated ZIP data is malformed", e)
            } finally {
                inflater.end()
            }
        }

        private fun validateZipFlagsAndMethod(flags: Int, method: Int) {
            if (flags and ZIP_FORBIDDEN_FLAGS != 0 || flags and ZIP_ALLOWED_FLAGS.inv() != 0) {
                malformedZip("Encrypted or unsupported ZIP flags are present")
            }
            if (method != ZIP_METHOD_STORED && method != ZIP_METHOD_DEFLATED) {
                malformedZip("Unsupported ZIP compression method $method")
            }
            if (method == ZIP_METHOD_STORED && flags and ZIP_DEFLATE_OPTION_FLAGS != 0) {
                malformedZip("Deflate option flags are set on a stored ZIP entry")
            }
        }

        private fun validateExtraFields(offset: Int, length: Int) {
            var cursor = offset
            val end = offset + length
            while (cursor < end) {
                if (end - cursor < 4) malformedZip("Malformed ZIP extra field")
                val id = bytes.leU16(cursor)
                val size = bytes.leU16(cursor + 2)
                cursor += 4
                if (size > end - cursor || id == ZIP64_EXTRA_ID) malformedZip("Malformed or ZIP64 extra field")
                cursor += size
            }
        }

        private fun decodeZipName(offset: Int, length: Int, flags: Int): String {
            if (length <= 0 || offset < 0 || offset + length > bytes.size) malformedZip("Invalid ZIP entry name length")
            val nameBytes = ByteBuffer.wrap(bytes, offset, length)
            val charset = if (flags and ZIP_FLAG_UTF8 != 0) StandardCharsets.UTF_8 else StandardCharsets.US_ASCII
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                decoder.decode(nameBytes).toString().also { name ->
                    if ('\u0000' in name) malformedZip("NUL is forbidden in ZIP entry names")
                }
            } catch (e: RuntimeException) {
                malformedZip("ZIP entry name is not canonical UTF-8 or ASCII", e)
            }
        }

        private fun enforceCompressionRatio(uncompressed: Long, compressed: Long) {
            if (uncompressed == 0L) return
            if (compressed == 0L || uncompressed > checkedMultiply(
                    compressed,
                    limits.maxCompressionRatio.toLong(),
                    "compression ratio",
                )
            ) {
                fail(DexOutputValidationFailure.DEX_QUOTA, "DEX ZIP compression ratio exceeds quota")
            }
        }

        private fun malformedZip(message: String, cause: Throwable? = null): Nothing = fail(
            DexOutputValidationFailure.MALFORMED_ZIP,
            message,
            cause,
        )

        private data class CentralEntry(
            val name: String,
            val flags: Int,
            val method: Int,
            val crc32: Long,
            val compressedSize: Long,
            val uncompressedSize: Long,
            val localHeaderOffset: Long,
        )
    }
}

private const val DEX_HEADER_SIZE = 0x70L
private const val DEX_ENDIAN_CONSTANT = 0x12345678L
private const val NO_INDEX = 0xffffffffL
private const val MAX_16_BIT_INDEX_COUNT = 65_535L
private const val MAX_MAP_ITEMS = 256L
private const val MAP_ITEM_SIZE = 12
private const val MAX_DESCRIPTOR_UTF16_UNITS = 65_535
private val DEX_MAGIC_PREFIX = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte())

private const val TYPE_HEADER_ITEM = 0x0000
private const val TYPE_STRING_ID_ITEM = 0x0001
private const val TYPE_TYPE_ID_ITEM = 0x0002
private const val TYPE_PROTO_ID_ITEM = 0x0003
private const val TYPE_FIELD_ID_ITEM = 0x0004
private const val TYPE_METHOD_ID_ITEM = 0x0005
private const val TYPE_CLASS_DEF_ITEM = 0x0006
private const val TYPE_CALL_SITE_ID_ITEM = 0x0007
private const val TYPE_METHOD_HANDLE_ITEM = 0x0008
private const val TYPE_MAP_LIST = 0x1000
private const val TYPE_TYPE_LIST = 0x1001
private const val TYPE_ANNOTATION_SET_REF_LIST = 0x1002
private const val TYPE_ANNOTATION_SET_ITEM = 0x1003
private const val TYPE_CLASS_DATA_ITEM = 0x2000
private const val TYPE_CODE_ITEM = 0x2001
private const val TYPE_STRING_DATA_ITEM = 0x2002
private const val TYPE_DEBUG_INFO_ITEM = 0x2003
private const val TYPE_ANNOTATION_ITEM = 0x2004
private const val TYPE_ENCODED_ARRAY_ITEM = 0x2005
private const val TYPE_ANNOTATIONS_DIRECTORY_ITEM = 0x2006
private const val TYPE_HIDDENAPI_CLASS_DATA_ITEM = 0xf000

private const val ZIP_LOCAL_SIGNATURE = 0x04034b50
private const val ZIP_CENTRAL_SIGNATURE = 0x02014b50
private const val ZIP_EOCD_SIGNATURE = 0x06054b50
private const val ZIP_DATA_DESCRIPTOR_SIGNATURE = 0x08074b50
private const val ZIP_LOCAL_HEADER_SIZE = 30
private const val ZIP_CENTRAL_HEADER_SIZE = 46
private const val ZIP_EOCD_SIZE = 22
private const val ZIP_METHOD_STORED = 0
private const val ZIP_METHOD_DEFLATED = 8
private const val ZIP_FLAG_DATA_DESCRIPTOR = 0x0008
private const val ZIP_FLAG_UTF8 = 0x0800
private const val ZIP_DEFLATE_OPTION_FLAGS = 0x0006
private const val ZIP_ALLOWED_FLAGS = ZIP_FLAG_DATA_DESCRIPTOR or ZIP_FLAG_UTF8 or ZIP_DEFLATE_OPTION_FLAGS
private const val ZIP_FORBIDDEN_FLAGS = 0x0001 or 0x0020 or 0x0040 or 0x2000
private const val ZIP64_EXTRA_ID = 0x0001
private const val ZIP64_U16_SENTINEL = 0xffff
private const val ZIP64_U32_SENTINEL = 0xffffffffL

private fun isCanonicalDexVersion(version: String): Boolean = version.length == 3 && version.all { it in '0'..'9' }

private fun dexIndex(name: String): Int? {
    if (name == "classes.dex") return 1
    if (!name.startsWith("classes") || !name.endsWith(".dex")) return null
    val digits = name.substring(7, name.length - 4)
    if (digits.isEmpty() || digits.startsWith('0') || digits.any { it !in '0'..'9' }) return null
    return digits.toIntOrNull()?.takeIf { it >= 2 }
}

private fun ByteArray.u16(offset: Int): Int {
    ensureRange(offset, 2, size, "DEX uint16")
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}

private fun ByteArray.u32(offset: Int): Long {
    ensureRange(offset, 4, size, "DEX uint32")
    return (this[offset].toLong() and 0xffL) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
}

private fun ByteArray.leU16(offset: Int): Int = u16(offset)
private fun ByteArray.leU32(offset: Int): Long = u32(offset)
private fun ByteArray.leInt(offset: Int): Int = leU32(offset).toInt()

private fun ensureRange(offset: Int, length: Int, limit: Int, label: String) {
    if (offset < 0 || length < 0 || offset.toLong() + length.toLong() > limit.toLong()) {
        fail(DexOutputValidationFailure.MALFORMED_ZIP, "$label exceeds its container")
    }
}

private fun Long.toIntExact(owner: String, label: String): Int {
    if (this < 0L || this > Int.MAX_VALUE) {
        fail(DexOutputValidationFailure.MALFORMED_ZIP, "$owner $label cannot be represented safely")
    }
    return toInt()
}

private fun checkedAdd(left: Long, right: Long, label: String): Long {
    if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) {
        fail(DexOutputValidationFailure.DEX_QUOTA, "$label overflows")
    }
    return left + right
}

private fun checkedMultiply(left: Long, right: Long, label: String): Long {
    if (left < 0L || right < 0L || (left != 0L && right > Long.MAX_VALUE / left)) {
        fail(DexOutputValidationFailure.DEX_QUOTA, "$label overflows")
    }
    return left * right
}

private fun fail(
    failure: DexOutputValidationFailure,
    message: String,
    cause: Throwable? = null,
): Nothing = throw DexOutputValidationException(failure, message, cause)
