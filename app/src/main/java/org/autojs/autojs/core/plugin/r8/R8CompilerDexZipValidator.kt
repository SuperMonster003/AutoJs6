package org.autojs.autojs.core.plugin.r8

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipFile

/** Host-side second opinion for the provider's DEX ZIP; no entry is loaded before this passes. */
internal object R8CompilerDexZipValidator {
    private const val DEX_HEADER_BYTES = 112
    private const val ZIP_EOCD_BYTES = 22
    private const val ZIP_EOCD_SIGNATURE = 0x06054b50
    private const val DEX_ENDIAN_CONSTANT = 0x12345678
    private val SUPPORTED_DEX_VERSIONS = 35..41

    fun validate(file: File, maximumEntries: Int, maximumUncompressedBytes: Long) {
        require(maximumEntries > 0)
        require(maximumUncompressedBytes > 0L)
        require(file.isFile && file.length() > ZIP_EOCD_BYTES)
        validateEndOfCentralDirectory(file, maximumEntries)
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size in 1..maximumEntries) { "R8 DEX ZIP entry count is invalid" }
            val expectedNames = List(entries.size) { index ->
                if (index == 0) "classes.dex" else "classes${index + 1}.dex"
            }
            require(entries.map { it.name } == expectedNames) { "R8 DEX ZIP entries are not canonical" }
            require(entries.none { it.isDirectory || it.comment != null }) { "R8 DEX ZIP contains metadata entries" }
            var total = 0L
            entries.forEach { entry ->
                require(entry.size >= DEX_HEADER_BYTES) { "R8 DEX entry is too small" }
                total = Math.addExact(total, entry.size)
                require(total <= maximumUncompressedBytes) { "R8 DEX payload exceeds its host limit" }
                zip.getInputStream(entry).use { input -> validateDex(input, entry.size) }
            }
        }
    }

    private fun validateEndOfCentralDirectory(file: File, maximumEntries: Int) {
        val tail = ByteArray(ZIP_EOCD_BYTES)
        file.inputStream().use { input ->
            var skipped = 0L
            val target = file.length() - ZIP_EOCD_BYTES
            while (skipped < target) {
                val count = input.skip(target - skipped)
                if (count <= 0L) require(input.read() >= 0) { "R8 DEX ZIP is truncated" } else skipped += count
                if (count <= 0L) skipped++
            }
            var offset = 0
            while (offset < tail.size) {
                val read = input.read(tail, offset, tail.size - offset)
                require(read > 0) { "R8 DEX ZIP EOCD is truncated" }
                offset += read
            }
            require(input.read() < 0) { "R8 DEX ZIP has trailing bytes" }
        }
        val eocd = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        require(eocd.int == ZIP_EOCD_SIGNATURE) { "R8 DEX ZIP EOCD is missing" }
        require(eocd.short.toInt() == 0 && eocd.short.toInt() == 0) { "Multi-disk R8 DEX ZIP is forbidden" }
        val entriesOnDisk = eocd.short.toInt() and 0xffff
        val entriesTotal = eocd.short.toInt() and 0xffff
        require(entriesOnDisk == entriesTotal && entriesTotal in 1..maximumEntries)
        val centralSize = eocd.int.toLong() and 0xffff_ffffL
        val centralOffset = eocd.int.toLong() and 0xffff_ffffL
        val commentLength = eocd.short.toInt() and 0xffff
        require(commentLength == 0) { "R8 DEX ZIP comments are forbidden" }
        require(Math.addExact(centralOffset, centralSize) == file.length() - ZIP_EOCD_BYTES) {
            "R8 DEX ZIP central directory is not canonical"
        }
    }

    private fun validateDex(input: InputStream, declaredSize: Long) {
        val header = ByteArray(DEX_HEADER_BYTES)
        var offset = 0
        while (offset < header.size) {
            val read = input.read(header, offset, header.size - offset)
            require(read > 0) { "R8 DEX header is truncated" }
            offset += read
        }
        require(header.copyOfRange(0, 4).contentEquals(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte())))
        require(header[7] == 0.toByte()) { "R8 DEX magic terminator is invalid" }
        val version = String(header, 4, 3, Charsets.US_ASCII).toIntOrNull()
        require(version in SUPPORTED_DEX_VERSIONS) { "R8 DEX version is unsupported" }
        val little = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val storedChecksum = little.getInt(8).toLong() and 0xffff_ffffL
        val storedSignature = header.copyOfRange(12, 32)
        val fileSize = little.getInt(32).toLong() and 0xffff_ffffL
        require(fileSize == declaredSize) { "R8 DEX file size header is inconsistent" }
        require(little.getInt(36) == DEX_HEADER_BYTES) { "R8 DEX header size is invalid" }
        require(little.getInt(40) == DEX_ENDIAN_CONSTANT) { "R8 DEX endian tag is invalid" }

        val signature = MessageDigest.getInstance("SHA-1")
        val checksum = Adler32()
        checksum.update(header, 12, header.size - 12)
        signature.update(header, 32, header.size - 32)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = header.size.toLong()
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total = Math.addExact(total, read.toLong())
            require(total <= declaredSize) { "R8 DEX entry exceeds its declared size" }
            checksum.update(buffer, 0, read)
            signature.update(buffer, 0, read)
        }
        require(total == declaredSize) { "R8 DEX entry is truncated" }
        require(signature.digest().contentEquals(storedSignature)) { "R8 DEX signature is invalid" }
        require(checksum.value == storedChecksum) { "R8 DEX checksum is invalid" }
    }
}
