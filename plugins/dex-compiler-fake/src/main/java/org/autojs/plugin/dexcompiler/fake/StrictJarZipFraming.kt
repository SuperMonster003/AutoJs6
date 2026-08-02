package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase

/**
 * Validates the ZIP records that [java.util.zip.ZipInputStream] deliberately does not require.
 *
 * JAR input must have one canonical, single-disk ZIP framing: local entries start at byte zero,
 * central entries describe those local entries in the same order, and an uncommented EOCD ends the
 * input exactly. ZIP64, encryption, record gaps, comments, and trailing bytes are rejected.
 */
internal object StrictJarZipFraming {

    fun validate(bytes: ByteArray, maximumEntries: Int): Int {
        require(maximumEntries > 0)
        if (bytes.size < ZIP_EOCD_SIZE) invalidArchive("JAR end-of-central-directory record is missing")

        val eocdOffset = bytes.size - ZIP_EOCD_SIZE
        if (bytes.leInt(eocdOffset) != ZIP_EOCD_SIGNATURE) {
            invalidArchive("JAR end-of-central-directory record is missing or has a comment")
        }
        if (bytes.leU16(eocdOffset + 4) != 0 || bytes.leU16(eocdOffset + 6) != 0 ||
            bytes.leU16(eocdOffset + 20) != 0
        ) {
            invalidArchive("Multi-disk, ZIP64, and commented JARs are forbidden")
        }

        val entriesOnDisk = bytes.leU16(eocdOffset + 8)
        val entryCount = bytes.leU16(eocdOffset + 10)
        if (entryCount == 0 || entryCount != entriesOnDisk || entryCount > maximumEntries ||
            entryCount == ZIP64_U16_SENTINEL
        ) {
            invalidArchive("JAR entry count is invalid or exceeds the fixture limit")
        }

        val centralSize = bytes.leU32(eocdOffset + 12)
        val centralOffset = bytes.leU32(eocdOffset + 16)
        if (centralSize == ZIP64_U32_SENTINEL || centralOffset == ZIP64_U32_SENTINEL ||
            checkedAdd(centralOffset, centralSize, "central directory") != eocdOffset.toLong()
        ) {
            invalidArchive("JAR central-directory bounds are inconsistent")
        }

        val entries = readCentralEntries(
            bytes = bytes,
            offset = centralOffset.toIntExact("central-directory offset"),
            declaredSize = centralSize.toIntExact("central-directory size"),
            count = entryCount,
        )
        entries.forEachIndexed { index, entry ->
            val expectedEnd = if (index + 1 < entries.size) {
                entries[index + 1].localHeaderOffset
            } else {
                centralOffset
            }
            validateLocalEntry(bytes, entry, expectedEnd)
        }
        return entryCount
    }

    private fun readCentralEntries(
        bytes: ByteArray,
        offset: Int,
        declaredSize: Int,
        count: Int,
    ): List<CentralEntry> {
        val entries = ArrayList<CentralEntry>(count)
        var cursor = offset
        repeat(count) {
            bytes.requireRange(cursor.toLong(), ZIP_CENTRAL_HEADER_SIZE.toLong(), "central-directory header")
            if (bytes.leInt(cursor) != ZIP_CENTRAL_SIGNATURE) invalidArchive("Invalid JAR central-directory signature")

            val flags = bytes.leU16(cursor + 8)
            val method = bytes.leU16(cursor + 10)
            validateFlagsAndMethod(flags, method)
            val crc32 = bytes.leU32(cursor + 16)
            val compressedSize = bytes.leU32(cursor + 20)
            val uncompressedSize = bytes.leU32(cursor + 24)
            val nameLength = bytes.leU16(cursor + 28)
            val extraLength = bytes.leU16(cursor + 30)
            val commentLength = bytes.leU16(cursor + 32)
            val diskStart = bytes.leU16(cursor + 34)
            val localHeaderOffset = bytes.leU32(cursor + 42)
            if (compressedSize == ZIP64_U32_SENTINEL || uncompressedSize == ZIP64_U32_SENTINEL ||
                localHeaderOffset == ZIP64_U32_SENTINEL || diskStart != 0
            ) {
                invalidArchive("ZIP64 and multi-disk JAR entries are forbidden")
            }
            if (nameLength == 0 || commentLength != 0) {
                invalidArchive("JAR entry names must be non-empty and entry comments are forbidden")
            }

            val recordSize = checkedAdd(
                ZIP_CENTRAL_HEADER_SIZE.toLong(),
                checkedAdd(
                    nameLength.toLong(),
                    checkedAdd(extraLength.toLong(), commentLength.toLong(), "entry metadata"),
                    "entry metadata",
                ),
                "central-directory entry",
            )
            bytes.requireRange(cursor.toLong(), recordSize, "central-directory entry")
            val nameOffset = cursor + ZIP_CENTRAL_HEADER_SIZE
            validateExtraFields(bytes, nameOffset + nameLength, extraLength)
            entries += CentralEntry(
                nameBytes = bytes.copyOfRange(nameOffset, nameOffset + nameLength),
                flags = flags,
                method = method,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )
            cursor = checkedAdd(cursor.toLong(), recordSize, "central-directory cursor")
                .toIntExact("central-directory cursor")
        }

        val expectedEnd = checkedAdd(offset.toLong(), declaredSize.toLong(), "central-directory size")
        if (cursor.toLong() != expectedEnd) {
            invalidArchive("JAR central-directory entry count does not match its declared size")
        }
        entries.zipWithNext().forEach { (first, second) ->
            if (first.localHeaderOffset >= second.localHeaderOffset) {
                invalidArchive("JAR local entries are duplicated or out of order")
            }
        }
        if (entries.first().localHeaderOffset != 0L) {
            invalidArchive("Bytes precede the first local JAR entry")
        }
        return entries
    }

    private fun validateLocalEntry(bytes: ByteArray, entry: CentralEntry, expectedEnd: Long) {
        val cursor = entry.localHeaderOffset.toIntExact("local-header offset")
        bytes.requireRange(cursor.toLong(), ZIP_LOCAL_HEADER_SIZE.toLong(), "local JAR header")
        if (bytes.leInt(cursor) != ZIP_LOCAL_SIGNATURE) invalidArchive("Invalid local JAR header signature")

        val flags = bytes.leU16(cursor + 6)
        val method = bytes.leU16(cursor + 8)
        if (flags != entry.flags || method != entry.method) {
            invalidArchive("Local and central JAR metadata disagree")
        }
        val localCrc32 = bytes.leU32(cursor + 14)
        val localCompressedSize = bytes.leU32(cursor + 18)
        val localUncompressedSize = bytes.leU32(cursor + 22)
        val nameLength = bytes.leU16(cursor + 26)
        val extraLength = bytes.leU16(cursor + 28)
        val metadataSize = checkedAdd(
            ZIP_LOCAL_HEADER_SIZE.toLong(),
            checkedAdd(nameLength.toLong(), extraLength.toLong(), "local entry metadata"),
            "local entry metadata",
        )
        bytes.requireRange(cursor.toLong(), metadataSize, "local JAR metadata")
        val nameOffset = cursor + ZIP_LOCAL_HEADER_SIZE
        if (!bytes.copyOfRange(nameOffset, nameOffset + nameLength).contentEquals(entry.nameBytes)) {
            invalidArchive("Local and central JAR entry names disagree")
        }
        validateExtraFields(bytes, nameOffset + nameLength, extraLength)

        val hasDescriptor = flags and ZIP_FLAG_DATA_DESCRIPTOR != 0
        if (!hasDescriptor && (localCrc32 != entry.crc32 || localCompressedSize != entry.compressedSize ||
                localUncompressedSize != entry.uncompressedSize)
        ) {
            invalidArchive("Local and central JAR sizes or CRC disagree")
        }
        if (hasDescriptor && !((localCrc32 == 0L || localCrc32 == entry.crc32) &&
                (localCompressedSize == 0L || localCompressedSize == entry.compressedSize) &&
                (localUncompressedSize == 0L || localUncompressedSize == entry.uncompressedSize))
        ) {
            invalidArchive("Local JAR data-descriptor placeholders are inconsistent")
        }

        val dataOffset = checkedAdd(cursor.toLong(), metadataSize, "local entry data")
        val dataEnd = checkedAdd(dataOffset, entry.compressedSize, "compressed entry data")
        if (dataEnd > expectedEnd) invalidArchive("Compressed JAR entry data exceeds its local record")
        if (hasDescriptor) {
            validateDataDescriptor(bytes, dataEnd, expectedEnd, entry)
        } else if (dataEnd != expectedEnd) {
            invalidArchive("Unaccounted bytes follow a local JAR entry")
        }
    }

    private fun validateDataDescriptor(
        bytes: ByteArray,
        offset: Long,
        expectedEnd: Long,
        entry: CentralEntry,
    ) {
        val descriptorLength = expectedEnd - offset
        val valuesOffset = when (descriptorLength) {
            12L -> offset
            16L -> {
                val signatureOffset = offset.toIntExact("data-descriptor offset")
                if (bytes.leInt(signatureOffset) != ZIP_DATA_DESCRIPTOR_SIGNATURE) {
                    invalidArchive("Invalid JAR data-descriptor signature")
                }
                offset + 4L
            }
            else -> invalidArchive("JAR data descriptor has an invalid size")
        }.toIntExact("data-descriptor values offset")
        bytes.requireRange(valuesOffset.toLong(), 12L, "JAR data descriptor")
        if (bytes.leU32(valuesOffset) != entry.crc32 ||
            bytes.leU32(valuesOffset + 4) != entry.compressedSize ||
            bytes.leU32(valuesOffset + 8) != entry.uncompressedSize
        ) {
            invalidArchive("JAR data descriptor disagrees with the central directory")
        }
    }

    private fun validateFlagsAndMethod(flags: Int, method: Int) {
        if (flags and ZIP_ALLOWED_FLAGS.inv() != 0) {
            invalidArchive("Encrypted or unsupported JAR flags are present")
        }
        if (method != ZIP_METHOD_STORED && method != ZIP_METHOD_DEFLATED) {
            invalidArchive("Unsupported JAR compression method $method")
        }
        if (method == ZIP_METHOD_STORED && flags and ZIP_DEFLATE_OPTION_FLAGS != 0) {
            invalidArchive("Deflate option flags are set on a stored JAR entry")
        }
    }

    private fun validateExtraFields(bytes: ByteArray, offset: Int, length: Int) {
        bytes.requireRange(offset.toLong(), length.toLong(), "JAR extra fields")
        var cursor = offset
        val end = offset + length
        while (cursor < end) {
            if (end - cursor < 4) invalidArchive("Malformed JAR extra field")
            val id = bytes.leU16(cursor)
            val size = bytes.leU16(cursor + 2)
            cursor += 4
            if (size > end - cursor || id == ZIP64_EXTRA_ID) invalidArchive("Malformed or ZIP64 JAR extra field")
            cursor += size
        }
    }

    private data class CentralEntry(
        val nameBytes: ByteArray,
        val flags: Int,
        val method: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
    )
}

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
private const val ZIP64_EXTRA_ID = 0x0001
private const val ZIP64_U16_SENTINEL = 0xffff
private const val ZIP64_U32_SENTINEL = 0xffffffffL

private fun ByteArray.leU16(offset: Int): Int {
    requireRange(offset.toLong(), 2L, "JAR uint16")
    return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}

private fun ByteArray.leU32(offset: Int): Long {
    requireRange(offset.toLong(), 4L, "JAR uint32")
    return (this[offset].toLong() and 0xffL) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
}

private fun ByteArray.leInt(offset: Int): Int = leU32(offset).toInt()

private fun ByteArray.requireRange(offset: Long, length: Long, label: String) {
    if (offset < 0L || length < 0L || offset > size.toLong() || length > size.toLong() - offset) {
        invalidArchive("$label exceeds the JAR container")
    }
}

private fun Long.toIntExact(label: String): Int {
    if (this < 0L || this > Int.MAX_VALUE) invalidArchive("JAR $label cannot be represented safely")
    return toInt()
}

private fun checkedAdd(left: Long, right: Long, label: String): Long {
    if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) invalidArchive("JAR $label overflows")
    return left + right
}

private fun invalidArchive(message: String): Nothing = throw FakeDexCompileFailure(
    DexCompilerErrorCode.INVALID_ARCHIVE,
    DexCompilerFailurePhase.INPUT_VALIDATION,
    message,
)
