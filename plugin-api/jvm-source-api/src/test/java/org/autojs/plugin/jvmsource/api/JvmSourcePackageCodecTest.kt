package org.autojs.plugin.jvmsource.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class JvmSourcePackageCodecTest {
    private val sources = linkedMapOf(
        "demo/Main.java" to "package demo; public class Main {}".toByteArray(),
        "demo/Util.java" to "package demo; final class Util {}".toByteArray(),
    )

    @Test
    fun canonicalPackageRoundTripsAndDefensivelyCopiesSources() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val secondArchive = JvmSourcePackageCodec.encode("demo/Main.java", sources.toSortedMap())
        assertArrayEquals(archive, secondArchive)

        val decoded = JvmSourcePackageCodec.decode(archive)
        assertEquals("demo/Main.java", decoded.entrySourcePath)
        assertEquals(listOf("demo/Main.java", "demo/Util.java"), decoded.sourcePaths)
        assertEquals(2, decoded.sourceFileCount)
        assertEquals(sources.values.sumOf { it.size.toLong() }, decoded.totalSourceBytes)
        val returned = decoded.sourceBytes("demo/Util.java")
        returned.fill(0)
        assertArrayEquals(sources.getValue("demo/Util.java"), decoded.sourceBytes("demo/Util.java"))
    }

    @Test
    fun requestCodecBindsSourcePackageKindCountAndContentBytes() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val request = JvmSourceFixtures.request(sourceSizeBytes = archive.size.toLong()).copy(
            sourceSha256 = JvmSha256.digest(archive),
            entryClassName = "demo.Main",
            sourcePayloadKind = JvmSourcePayloadKind.SOURCE_ARCHIVE,
            sourceFileCount = sources.size,
            sourceContentBytes = sources.values.sumOf { it.size.toLong() },
        )

        assertEquals(request, JvmSourceCodec.decodeRequest(JvmSourceCodec.encodeRequest(request)))
    }

    @Test
    fun rejectsTraversalAndDuplicateZipNamesBeforeExtraction() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val traversal = archive.copyOf().also {
            patchZipNames(it, "demo/Util.java", "demo/../x.java")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.decode(traversal)
        }

        val duplicateSources = linkedMapOf(
            "demo/Main.java" to sources.getValue("demo/Main.java"),
            "demo/Twin.java" to sources.getValue("demo/Util.java"),
        )
        val duplicate = JvmSourcePackageCodec.encode("demo/Main.java", duplicateSources).copyOf().also {
            patchZipNames(it, "demo/Twin.java", "demo/Main.java")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.decode(duplicate)
        }
    }

    @Test
    fun rejectsCompressedBombProfileAndSymlinkExternalAttributes() {
        val archive = JvmSourcePackageCodec.encode("demo/Main.java", sources)
        val occurrences = archive.asciiOccurrences("demo/Util.java")
        assertEquals(3, occurrences.size)
        val localHeader = occurrences[1] - 30
        val centralHeader = occurrences[2] - 46

        val compressedBomb = archive.copyOf().also {
            it.putU16(localHeader + 8, 8)
            it.putU16(centralHeader + 10, 8)
            it.putU32(localHeader + 22, 0xffff_ffffL)
            it.putU32(centralHeader + 24, 0xffff_ffffL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.decode(compressedBomb)
        }

        val symbolicLink = archive.copyOf().also {
            it.putU32(centralHeader + 38, 0xa000_0000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.decode(symbolicLink)
        }
    }

    @Test
    fun rejectsUnsafeCaseCollisionsAndFileCountOverflowAtEncodingBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.encode(
                "Main.java",
                linkedMapOf(
                    "Main.java" to byteArrayOf(1),
                    "main.java" to byteArrayOf(2),
                ),
            )
        }
        val tooMany = (0..JvmSourceContract.MAX_SOURCE_FILES).associate { index ->
            "Source$index.java" to byteArrayOf(index.toByte())
        }
        assertTrue(tooMany.size > JvmSourceContract.MAX_SOURCE_FILES)
        assertThrows(IllegalArgumentException::class.java) {
            JvmSourcePackageCodec.encode("Source0.java", tooMany)
        }
    }

    private fun patchZipNames(bytes: ByteArray, from: String, to: String) {
        require(from.length == to.length)
        val occurrences = bytes.asciiOccurrences(from)
        require(occurrences.size == 3)
        val replacement = to.toByteArray(StandardCharsets.US_ASCII)
        // Keep the manifest unchanged; mutate only the local and central ZIP names.
        occurrences.drop(1).forEach { offset ->
            replacement.copyInto(bytes, offset)
        }
    }

    private fun ByteArray.asciiOccurrences(value: String): List<Int> {
        val needle = value.toByteArray(StandardCharsets.US_ASCII)
        return indices.filter { start ->
            start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        putU16(offset, (value and 0xffff).toInt())
        putU16(offset + 2, ((value ushr 16) and 0xffff).toInt())
    }
}
