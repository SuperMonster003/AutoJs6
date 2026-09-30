package org.autojs.plugin.packagearchive

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Detects the presence of APK signature schemes without pulling the full Android apksig verifier
 * into the host. This intentionally reports scheme presence, not cryptographic validity.
 */
object ApkSignatureDetector {

    private const val BLOCK_ID_V2 = 0x7109871a
    private const val BLOCK_ID_V3 = 0xF05368C0.toInt()
    private const val BLOCK_ID_V3_1 = 0x1B93AD61
    private const val ZIP_EOCD_MIN_SIZE = 22
    private const val ZIP_MAX_COMMENT_SIZE = 65_535
    private const val SIGNING_BLOCK_FOOTER_SIZE = 24
    private const val SIGNING_BLOCK_MIN_SIZE = 32
    private val SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)

    fun detectSchemes(apkFile: File): String? {
        if (!apkFile.isFile) return null

        val blockIds = runCatching { readSigningBlockIds(apkFile) }.getOrDefault(emptySet())
        val hasV1 = runCatching { hasV1Signature(apkFile) }.getOrDefault(false)
        val hasV2 = BLOCK_ID_V2 in blockIds
        val hasV3 = BLOCK_ID_V3 in blockIds || BLOCK_ID_V3_1 in blockIds
        val hasV4 = File(apkFile.path + ".idsig").isFile

        return listOfNotNull(
            "V1".takeIf { hasV1 },
            "V2".takeIf { hasV2 },
            "V3".takeIf { hasV3 },
            "V4".takeIf { hasV4 },
        ).takeUnless { it.isEmpty() }?.joinToString(" + ")
    }

    fun readSigningBlockIds(apkFile: File): Set<Int> {
        RandomAccessFile(apkFile, "r").use { raf ->
            val centralDirectoryOffset = findCentralDirectoryOffset(raf)
            if (centralDirectoryOffset < SIGNING_BLOCK_MIN_SIZE) {
                return emptySet()
            }

            raf.seek(centralDirectoryOffset - SIGNING_BLOCK_FOOTER_SIZE)
            val blockSizeInFooter = raf.readLittleEndianLong()
            val magic = ByteArray(SIGNING_BLOCK_MAGIC.size)
            raf.readFully(magic)
            if (!magic.contentEquals(SIGNING_BLOCK_MAGIC)) {
                return emptySet()
            }
            if (blockSizeInFooter < SIGNING_BLOCK_FOOTER_SIZE || blockSizeInFooter > centralDirectoryOffset - 8) {
                throw IOException("Invalid APK Signing Block size: $blockSizeInFooter")
            }

            val blockStart = centralDirectoryOffset - blockSizeInFooter - 8
            raf.seek(blockStart)
            val blockSizeInHeader = raf.readLittleEndianLong()
            if (blockSizeInHeader != blockSizeInFooter) {
                throw IOException("APK Signing Block size fields do not match")
            }

            val entriesEnd = centralDirectoryOffset - SIGNING_BLOCK_FOOTER_SIZE
            val ids = linkedSetOf<Int>()
            while (raf.filePointer < entriesEnd) {
                val pairSize = raf.readLittleEndianLong()
                if (pairSize < 4 || pairSize > entriesEnd - raf.filePointer) {
                    throw IOException("Invalid APK Signing Block pair size: $pairSize")
                }
                ids += raf.readLittleEndianInt()
                raf.seek(raf.filePointer + pairSize - 4)
            }
            if (raf.filePointer != entriesEnd) {
                throw IOException("APK Signing Block pairs are not aligned")
            }
            return ids
        }
    }

    private fun findCentralDirectoryOffset(raf: RandomAccessFile): Long {
        val fileSize = raf.length()
        if (fileSize < ZIP_EOCD_MIN_SIZE) {
            throw IOException("APK is too small to contain ZIP EOCD")
        }
        val tailSize = minOf(fileSize, (ZIP_EOCD_MIN_SIZE + ZIP_MAX_COMMENT_SIZE).toLong()).toInt()
        val tail = ByteArray(tailSize)
        raf.seek(fileSize - tailSize)
        raf.readFully(tail)

        for (index in tailSize - ZIP_EOCD_MIN_SIZE downTo 0) {
            if (tail[index] != 0x50.toByte() ||
                tail[index + 1] != 0x4B.toByte() ||
                tail[index + 2] != 0x05.toByte() ||
                tail[index + 3] != 0x06.toByte()
            ) {
                continue
            }
            val commentLength = tail.readUnsignedShortLittleEndian(index + 20)
            if (index + ZIP_EOCD_MIN_SIZE + commentLength != tailSize) {
                continue
            }
            return tail.readUnsignedIntLittleEndian(index + 16)
        }
        throw IOException("ZIP EOCD was not found")
    }

    private fun RandomAccessFile.readLittleEndianLong(): Long {
        val bytes = ByteArray(8)
        readFully(bytes)
        var value = 0L
        for (index in bytes.indices.reversed()) {
            value = (value shl 8) or (bytes[index].toLong() and 0xFF)
        }
        if (value < 0) {
            throw IOException("Unsigned 64-bit value exceeds supported range")
        }
        return value
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b0 or b1 or b2 or b3 < 0) {
            throw EOFException()
        }
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun ByteArray.readUnsignedShortLittleEndian(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readUnsignedIntLittleEndian(offset: Int): Long {
        return (this[offset].toLong() and 0xFF) or
                ((this[offset + 1].toLong() and 0xFF) shl 8) or
                ((this[offset + 2].toLong() and 0xFF) shl 16) or
                ((this[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun hasV1Signature(apkFile: File): Boolean {
        JarFile(apkFile).use { jar ->
            var hasManifest = false
            var hasSF = false
            var hasSignatureBlock = false
            for (entry: JarEntry in jar.entries()) {
                val n = entry.name.uppercase(Locale.ROOT)
                when {
                    n == "META-INF/MANIFEST.MF" -> hasManifest = true
                    n.startsWith("META-INF/") && n.endsWith(".SF") -> hasSF = true
                    n.startsWith("META-INF/") &&
                            (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")) -> hasSignatureBlock = true
                }
                if (hasManifest && hasSF && hasSignatureBlock) return true
            }
        }
        return false
    }

}
