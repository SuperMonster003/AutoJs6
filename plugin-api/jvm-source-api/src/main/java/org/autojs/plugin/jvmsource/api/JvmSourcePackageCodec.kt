package org.autojs.plugin.jvmsource.api

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.zip.CRC32

/**
 * Canonical bounded source-package container used by Protocol 1.6.
 *
 * The outer container is a deliberately small ZIP profile: every entry is STORED, metadata is
 * zeroed, names are ASCII, and local/central records must be byte-for-byte canonical. The first
 * entry is a binary manifest; Java sources follow in lexical path order. Decoding never delegates
 * extraction to a platform ZIP implementation, so archive names can never create links or escape
 * a provider workspace.
 */
object JvmSourcePackageCodec {
    const val MANIFEST_ENTRY_NAME = "META-INF/AUTOJS6-JVM-SOURCE.MF"

    @JvmStatic
    fun encode(entrySourcePath: String, sources: Map<String, ByteArray>): ByteArray {
        val normalized = validateSources(entrySourcePath, sources)
        val manifest = encodeManifest(entrySourcePath, normalized)
        val entries = buildList {
            add(RawEntry(MANIFEST_ENTRY_NAME, manifest))
            normalized.forEach { (path, bytes) -> add(RawEntry(path, bytes)) }
        }
        val output = ByteArrayOutputStream(estimateArchiveBytes(entries))
        val central = ArrayList<CentralEntry>(entries.size)
        entries.forEach { entry ->
            val offset = output.size()
            val name = entry.name.toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply { update(entry.bytes) }.value
            output.writeU32(LOCAL_SIGNATURE)
            output.writeU16(VERSION_NEEDED)
            output.writeU16(0)
            output.writeU16(METHOD_STORED)
            output.writeU16(0)
            output.writeU16(0)
            output.writeU32(crc)
            output.writeU32(entry.bytes.size.toLong())
            output.writeU32(entry.bytes.size.toLong())
            output.writeU16(name.size)
            output.writeU16(0)
            output.write(name)
            output.write(entry.bytes)
            central += CentralEntry(entry.name, entry.bytes.size, crc, offset)
        }
        val centralOffset = output.size()
        central.forEach { entry ->
            val name = entry.name.toByteArray(StandardCharsets.US_ASCII)
            output.writeU32(CENTRAL_SIGNATURE)
            output.writeU16(VERSION_MADE_BY)
            output.writeU16(VERSION_NEEDED)
            output.writeU16(0)
            output.writeU16(METHOD_STORED)
            output.writeU16(0)
            output.writeU16(0)
            output.writeU32(entry.crc32)
            output.writeU32(entry.size.toLong())
            output.writeU32(entry.size.toLong())
            output.writeU16(name.size)
            output.writeU16(0)
            output.writeU16(0)
            output.writeU16(0)
            output.writeU16(0)
            output.writeU32(0)
            output.writeU32(entry.localOffset.toLong())
            output.write(name)
        }
        val centralSize = output.size() - centralOffset
        output.writeU32(EOCD_SIGNATURE)
        output.writeU16(0)
        output.writeU16(0)
        output.writeU16(central.size)
        output.writeU16(central.size)
        output.writeU32(centralSize.toLong())
        output.writeU32(centralOffset.toLong())
        output.writeU16(0)
        return output.toByteArray().also { archive ->
            require(archive.size.toLong() <= JvmSourceContract.MAX_SOURCE_ARCHIVE_BYTES) {
                "Source archive exceeds the protocol payload limit"
            }
        }
    }

    @JvmStatic
    fun decode(archive: ByteArray): JvmSourcePackageContent {
        require(archive.size.toLong() in EOCD_SIZE.toLong()..JvmSourceContract.MAX_SOURCE_ARCHIVE_BYTES) {
            "Source archive size is outside the protocol limit"
        }
        val rawEntries = parseCanonicalZip(archive)
        require(rawEntries.first().name == MANIFEST_ENTRY_NAME) {
            "Source archive manifest must be the first entry"
        }
        require(rawEntries.first().bytes.size <= JvmSourceContract.MAX_SOURCE_MANIFEST_BYTES) {
            "Source archive manifest exceeds its limit"
        }
        val sourceEntries = rawEntries.drop(1)
        require(sourceEntries.size in 1..JvmSourceContract.MAX_SOURCE_FILES) {
            "Source archive file count is outside the protocol limit"
        }
        val paths = sourceEntries.map(RawEntry::name)
        validateOrderedPaths(paths)
        val manifest = decodeManifest(rawEntries.first().bytes)
        require(manifest.entrySourcePath in paths) { "Source archive entry path is absent" }
        require(manifest.entries.size == sourceEntries.size) {
            "Source archive manifest file count differs from the ZIP"
        }
        var total = 0L
        manifest.entries.zip(sourceEntries).forEach { (claimed, actual) ->
            require(claimed.path == actual.name) { "Source archive manifest order differs from the ZIP" }
            require(claimed.sizeBytes == actual.bytes.size.toLong()) {
                "Source archive manifest size differs from an entry"
            }
            require(claimed.sha256 == JvmSha256.digest(actual.bytes)) {
                "Source archive manifest digest differs from an entry"
            }
            total = Math.addExact(total, actual.bytes.size.toLong())
        }
        require(total == manifest.totalSourceBytes && total in 1L..JvmSourceContract.MAX_SOURCE_BYTES) {
            "Source archive total size differs from its manifest or protocol limit"
        }
        val sources = sourceEntries.associateTo(linkedMapOf()) { it.name to it.bytes }
        // This final identity check rejects every tolerated-but-noncanonical ZIP representation.
        require(archive.contentEquals(encode(manifest.entrySourcePath, sources))) {
            "Source archive is not in canonical wire form"
        }
        return JvmSourcePackageContent(manifest.entrySourcePath, sources)
    }

    private fun validateSources(
        entrySourcePath: String,
        sources: Map<String, ByteArray>,
    ): LinkedHashMap<String, ByteArray> {
        require(sources.size in 1..JvmSourceContract.MAX_SOURCE_FILES) {
            "Source package file count is outside the protocol limit"
        }
        val sorted = sources.entries.sortedBy(Map.Entry<String, ByteArray>::key)
        validateOrderedPaths(sorted.map(Map.Entry<String, ByteArray>::key))
        require(entrySourcePath in sources) { "Source package entry path is absent" }
        JvmSourcePackagePathPolicy.requireSourcePath(entrySourcePath)
        var total = 0L
        return linkedMapOf<String, ByteArray>().also { result ->
            sorted.forEach { (path, input) ->
                require(input.isNotEmpty()) { "Source package contains an empty source file" }
                total = Math.addExact(total, input.size.toLong())
                require(total <= JvmSourceContract.MAX_SOURCE_BYTES) {
                    "Source package exceeds the total source-byte limit"
                }
                result[path] = input.copyOf()
            }
        }
    }

    private fun validateOrderedPaths(paths: List<String>) {
        require(paths == paths.sorted()) { "Source package paths are not in canonical order" }
        require(paths.distinct().size == paths.size) { "Source package contains duplicate paths" }
        require(paths.map { it.lowercase(Locale.ROOT) }.distinct().size == paths.size) {
            "Source package contains case-folding path collisions"
        }
        paths.forEach(JvmSourcePackagePathPolicy::requireSourcePath)
    }

    private fun encodeManifest(
        entrySourcePath: String,
        sources: LinkedHashMap<String, ByteArray>,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MANIFEST_MAGIC)
            output.writeInt(MANIFEST_SCHEMA)
            output.writeFramedAscii(entrySourcePath)
            output.writeInt(sources.size)
            output.writeLong(sources.values.sumOf { it.size.toLong() })
            sources.forEach { (path, source) ->
                output.writeFramedAscii(path)
                output.writeLong(source.size.toLong())
                output.write(JvmSha256.digest(source).toByteArray())
            }
        }
    }.toByteArray().also { manifest ->
        require(manifest.size <= JvmSourceContract.MAX_SOURCE_MANIFEST_BYTES) {
            "Source package manifest exceeds its limit"
        }
    }

    private fun decodeManifest(bytes: ByteArray): Manifest = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MANIFEST_MAGIC) { "Source package manifest magic is invalid" }
        require(input.readInt() == MANIFEST_SCHEMA) { "Source package manifest schema is unsupported" }
        val entrySourcePath = input.readFramedAscii()
        JvmSourcePackagePathPolicy.requireSourcePath(entrySourcePath)
        val count = input.readInt()
        require(count in 1..JvmSourceContract.MAX_SOURCE_FILES) {
            "Source package manifest file count is outside the protocol limit"
        }
        val total = input.readLong()
        require(total in 1L..JvmSourceContract.MAX_SOURCE_BYTES) {
            "Source package manifest total size is outside the protocol limit"
        }
        val entries = ArrayList<ManifestEntry>(count)
        repeat(count) {
            val path = input.readFramedAscii()
            JvmSourcePackagePathPolicy.requireSourcePath(path)
            val size = input.readLong()
            require(size in 1L..JvmSourceContract.MAX_SOURCE_BYTES) {
                "Source package manifest entry size is outside the protocol limit"
            }
            val hash = ByteArray(JvmSha256.BYTE_COUNT).also(input::readFully)
            entries += ManifestEntry(path, size, JvmSha256.fromBytes(hash))
        }
        require(input.read() == -1) { "Source package manifest has trailing bytes" }
        validateOrderedPaths(entries.map(ManifestEntry::path))
        require(entries.sumOf(ManifestEntry::sizeBytes) == total) {
            "Source package manifest total is inconsistent"
        }
        Manifest(entrySourcePath, total, entries)
    }

    private fun parseCanonicalZip(bytes: ByteArray): List<RawEntry> {
        val eocd = bytes.size - EOCD_SIZE
        require(bytes.u32(eocd) == EOCD_SIGNATURE) { "Source archive has no terminal EOCD" }
        require(bytes.u16(eocd + 4) == 0 && bytes.u16(eocd + 6) == 0) {
            "Source archive is split across disks"
        }
        val diskCount = bytes.u16(eocd + 8)
        val totalCount = bytes.u16(eocd + 10)
        require(diskCount == totalCount && totalCount in 2..JvmSourceContract.MAX_SOURCE_FILES + 1) {
            "Source archive central-directory count is invalid"
        }
        val centralSize = bytes.u32(eocd + 12).toIntExact("central-directory size")
        val centralOffset = bytes.u32(eocd + 16).toIntExact("central-directory offset")
        require(bytes.u16(eocd + 20) == 0) { "Source archive comments are not supported" }
        require(centralOffset > 0 && centralOffset + centralSize == eocd) {
            "Source archive central directory is not exactly framed"
        }
        val central = ArrayList<CentralEntry>(totalCount)
        var cursor = centralOffset
        repeat(totalCount) {
            require(bytes.u32(cursor) == CENTRAL_SIGNATURE) { "Source archive central record is invalid" }
            require(bytes.u16(cursor + 4) == VERSION_MADE_BY &&
                bytes.u16(cursor + 6) == VERSION_NEEDED &&
                bytes.u16(cursor + 8) == 0 &&
                bytes.u16(cursor + 10) == METHOD_STORED &&
                bytes.u16(cursor + 12) == 0 &&
                bytes.u16(cursor + 14) == 0
            ) { "Source archive uses unsupported ZIP metadata or compression" }
            val crc = bytes.u32(cursor + 16)
            val compressed = bytes.u32(cursor + 20)
            val uncompressed = bytes.u32(cursor + 24)
            val nameLength = bytes.u16(cursor + 28)
            val extraLength = bytes.u16(cursor + 30)
            val commentLength = bytes.u16(cursor + 32)
            require(compressed == uncompressed && uncompressed <= JvmSourceContract.MAX_SOURCE_BYTES) {
                "Source archive entry size or compression is invalid"
            }
            require(nameLength in 1..JvmSourceContract.MAX_SOURCE_PATH_BYTES &&
                extraLength == 0 && commentLength == 0 &&
                bytes.u16(cursor + 34) == 0 && bytes.u16(cursor + 36) == 0 &&
                bytes.u32(cursor + 38) == 0L
            ) { "Source archive entry metadata or external attributes are not canonical" }
            val localOffset = bytes.u32(cursor + 42).toIntExact("local-entry offset")
            val nameStart = cursor + CENTRAL_HEADER_SIZE
            val nameEnd = nameStart + nameLength
            require(nameEnd <= eocd) { "Source archive central name is truncated" }
            val name = bytes.ascii(nameStart, nameLength)
            central += CentralEntry(name, uncompressed.toIntExact("entry size"), crc, localOffset)
            cursor = nameEnd
        }
        require(cursor == eocd) { "Source archive central directory has trailing records" }

        val result = ArrayList<RawEntry>(central.size)
        var localCursor = 0
        central.forEach { entry ->
            require(entry.localOffset == localCursor && bytes.u32(localCursor) == LOCAL_SIGNATURE) {
                "Source archive local records are reordered or invalid"
            }
            require(bytes.u16(localCursor + 4) == VERSION_NEEDED &&
                bytes.u16(localCursor + 6) == 0 &&
                bytes.u16(localCursor + 8) == METHOD_STORED &&
                bytes.u16(localCursor + 10) == 0 &&
                bytes.u16(localCursor + 12) == 0 &&
                bytes.u32(localCursor + 14) == entry.crc32 &&
                bytes.u32(localCursor + 18) == entry.size.toLong() &&
                bytes.u32(localCursor + 22) == entry.size.toLong()
            ) { "Source archive local metadata differs from its central record" }
            val nameLength = bytes.u16(localCursor + 26)
            val extraLength = bytes.u16(localCursor + 28)
            require(extraLength == 0) { "Source archive local extra fields are not supported" }
            val nameStart = localCursor + LOCAL_HEADER_SIZE
            val dataStart = nameStart + nameLength
            val dataEnd = dataStart + entry.size
            require(dataEnd <= centralOffset && bytes.ascii(nameStart, nameLength) == entry.name) {
                "Source archive local name or data framing differs from its central record"
            }
            val content = bytes.copyOfRange(dataStart, dataEnd)
            require(CRC32().apply { update(content) }.value == entry.crc32) {
                "Source archive entry CRC-32 is invalid"
            }
            result += RawEntry(entry.name, content)
            localCursor = dataEnd
        }
        require(localCursor == centralOffset) { "Source archive local region has trailing bytes" }
        val names = result.map(RawEntry::name)
        require(names.distinct().size == names.size) { "Source archive contains duplicate entries" }
        require(names.map { it.lowercase(Locale.ROOT) }.distinct().size == names.size) {
            "Source archive contains case-folding entry collisions"
        }
        return result
    }

    private fun estimateArchiveBytes(entries: List<RawEntry>): Int = entries.sumOf { entry ->
        LOCAL_HEADER_SIZE + CENTRAL_HEADER_SIZE + entry.name.length * 2 + entry.bytes.size
    } + EOCD_SIZE

    private fun DataOutputStream.writeFramedAscii(value: String) {
        val encoded = value.toByteArray(StandardCharsets.US_ASCII)
        require(String(encoded, StandardCharsets.US_ASCII) == value) { "Manifest text must be ASCII" }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readFramedAscii(): String {
        val length = readInt()
        require(length in 1..JvmSourceContract.MAX_SOURCE_PATH_BYTES) {
            "Manifest path length is outside the protocol limit"
        }
        val bytes = ByteArray(length).also(::readFully)
        return bytes.ascii(0, bytes.size)
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        require(offset >= 0 && length >= 0 && offset + length <= size) { "ZIP name is truncated" }
        for (index in offset until offset + length) {
            require(this[index].toInt() in 0x20..0x7e) { "ZIP names must use printable ASCII" }
        }
        return String(this, offset, length, StandardCharsets.US_ASCII)
    }

    private fun ByteArray.u16(offset: Int): Int {
        require(offset >= 0 && offset + 2 <= size) { "ZIP record is truncated" }
        return (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.u32(offset: Int): Long =
        (u16(offset).toLong() and 0xffffL) or ((u16(offset + 2).toLong() and 0xffffL) shl 16)

    private fun Long.toIntExact(label: String): Int {
        require(this in 0..Int.MAX_VALUE.toLong()) { "Source archive $label is too large" }
        return toInt()
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        require(value in 0..0xffff)
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeU32(value: Long) {
        require(value in 0..0xffff_ffffL)
        writeU16((value and 0xffffL).toInt())
        writeU16(((value ushr 16) and 0xffffL).toInt())
    }

    private data class RawEntry(val name: String, val bytes: ByteArray)
    private data class CentralEntry(val name: String, val size: Int, val crc32: Long, val localOffset: Int)
    private data class Manifest(val entrySourcePath: String, val totalSourceBytes: Long, val entries: List<ManifestEntry>)
    private data class ManifestEntry(val path: String, val sizeBytes: Long, val sha256: JvmSha256)

    private const val MANIFEST_MAGIC = 0x414a534d // AJSM
    private const val MANIFEST_SCHEMA = 1
    private const val METHOD_STORED = 0
    private const val VERSION_NEEDED = 10
    private const val VERSION_MADE_BY = 20
    private const val LOCAL_SIGNATURE = 0x04034b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val LOCAL_HEADER_SIZE = 30
    private const val CENTRAL_HEADER_SIZE = 46
    private const val EOCD_SIZE = 22
}

class JvmSourcePackageContent internal constructor(
    val entrySourcePath: String,
    sources: Map<String, ByteArray>,
) {
    private val sourceBytes = Collections.unmodifiableMap(
        sources.mapValuesTo(linkedMapOf()) { (_, bytes) -> bytes.copyOf() },
    )

    val sourcePaths: List<String> = Collections.unmodifiableList(sourceBytes.keys.toList())
    val sourceFileCount: Int get() = sourceBytes.size
    val totalSourceBytes: Long get() = sourceBytes.values.sumOf { it.size.toLong() }

    fun sourceBytes(path: String): ByteArray =
        requireNotNull(sourceBytes[path]) { "Unknown source package path: $path" }.copyOf()
}

object JvmSourcePackagePathPolicy {
    private val SOURCE_PATH = Regex(
        "(?:[A-Za-z_$][A-Za-z0-9_$]*/){0,${JvmSourceContract.MAX_SOURCE_PACKAGE_DEPTH}}" +
            "[A-Za-z_$][A-Za-z0-9_$]*\\.java",
    )

    @JvmStatic
    fun requireSourcePath(path: String) {
        require(path.toByteArray(StandardCharsets.UTF_8).size <= JvmSourceContract.MAX_SOURCE_PATH_BYTES &&
            SOURCE_PATH.matches(path)
        ) { "Java source package path is outside the canonical relative-path profile" }
    }

    @JvmStatic
    fun sourcePathForClassName(className: String): String {
        val path = className.replace('.', '/') + ".java"
        requireSourcePath(path)
        return path
    }
}
