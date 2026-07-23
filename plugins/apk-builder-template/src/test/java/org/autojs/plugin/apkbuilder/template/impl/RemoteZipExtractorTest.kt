package org.autojs.plugin.apkbuilder.template.impl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class RemoteZipExtractorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun extract_acceptsExactArchiveEntryCountAndUncompressedBoundaries() {
        val archive = zip(
            "boundary.zip",
            listOf(
                "one.bin" to byteArrayOf(1, 2),
                "nested/two.bin" to byteArrayOf(3, 4),
            ),
        )
        val target = File(temporaryFolder.root, "boundary-target")
        val budget = budget(
            maxArchiveBytes = archive.length(),
            maxEntryCount = 2,
            maxEntryBytes = 2L,
            maxTotalBytes = 4L,
        )

        RemoteZipExtractor.extract(archive, target, budget)

        assertArrayEquals(byteArrayOf(1, 2), File(target, "one.bin").readBytes())
        assertArrayEquals(byteArrayOf(3, 4), File(target, "nested/two.bin").readBytes())
    }

    @Test
    fun extract_rejectsArchiveOneBytePastLimitWithoutCreatingTarget() {
        val archive = zip("archive-limit.zip", listOf("file.bin" to byteArrayOf(1)))
        val target = File(temporaryFolder.root, "archive-limit-target")

        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                archive,
                target,
                budget(maxArchiveBytes = archive.length() - 1L),
            )
        }
        assertFalse(target.exists())
    }

    @Test
    fun extract_rejectsEntryCountAndCumulativeSizePastBoundary() {
        val archive = zip(
            "aggregate-limit.zip",
            listOf(
                "one.bin" to byteArrayOf(1, 2, 3),
                "two.bin" to byteArrayOf(4, 5, 6),
            ),
        )

        val countTarget = File(temporaryFolder.root, "count-target")
        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                archive,
                countTarget,
                budget(maxArchiveBytes = archive.length(), maxEntryCount = 1),
            )
        }
        assertFalse(countTarget.exists())

        val totalTarget = File(temporaryFolder.root, "total-target")
        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                archive,
                totalTarget,
                budget(
                    maxArchiveBytes = archive.length(),
                    maxEntryBytes = 3L,
                    maxTotalBytes = 5L,
                ),
            )
        }
        assertFalse(totalTarget.exists())
    }

    @Test
    fun extract_rejectsCompressionRatioPastBoundary() {
        val data = ByteArray(64 * 1_024)
        val archive = zip("ratio.zip", listOf("zeros.bin" to data), Deflater.BEST_COMPRESSION)
        val compressedSize = ZipFile(archive).use { it.getEntry("zeros.bin").compressedSize }
        val allowedRatio = (data.size.toLong() + compressedSize - 1L) / compressedSize

        val acceptedTarget = File(temporaryFolder.root, "ratio-accepted")
        RemoteZipExtractor.extract(
            archive,
            acceptedTarget,
            budget(
                maxArchiveBytes = archive.length(),
                maxEntryBytes = data.size.toLong(),
                maxTotalBytes = data.size.toLong(),
                maxCompressionRatio = allowedRatio,
                compressionRatioThresholdBytes = 1L,
            ),
        )

        val rejectedTarget = File(temporaryFolder.root, "ratio-rejected")
        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                archive,
                rejectedTarget,
                budget(
                    maxArchiveBytes = archive.length(),
                    maxEntryBytes = data.size.toLong(),
                    maxTotalBytes = data.size.toLong(),
                    maxCompressionRatio = allowedRatio - 1L,
                    compressionRatioThresholdBytes = 1L,
                ),
            )
        }
        assertFalse(rejectedTarget.exists())
    }

    @Test
    fun extract_rejectsNormalizedDuplicatesAndTraversal() {
        val duplicateArchive = zip(
            "duplicates.zip",
            listOf(
                "dir\\file.bin" to byteArrayOf(1),
                "dir/file.bin" to byteArrayOf(2),
            ),
        )
        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                duplicateArchive,
                File(temporaryFolder.root, "duplicates-target"),
                budget(maxArchiveBytes = duplicateArchive.length()),
            )
        }

        val traversalArchive = zip("traversal.zip", listOf("../outside.bin" to byteArrayOf(1)))
        val traversalTarget = File(temporaryFolder.root, "traversal-target")
        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                traversalArchive,
                traversalTarget,
                budget(maxArchiveBytes = traversalArchive.length()),
            )
        }
        assertFalse(File(temporaryFolder.root, "outside.bin").exists())
        assertFalse(traversalTarget.exists())
    }

    @Test
    fun extract_removesPartiallyWrittenTargetAfterRuntimeFailure() {
        val archive = zip(
            "partial.zip",
            listOf(
                "first.bin" to byteArrayOf(1),
                "blocked" to byteArrayOf(2),
                "blocked/child.bin" to byteArrayOf(3),
            ),
        )
        val target = File(temporaryFolder.root, "partial-target")

        assertThrows(IOException::class.java) {
            RemoteZipExtractor.extract(
                archive,
                target,
                budget(maxArchiveBytes = archive.length()),
                cleanupTargetOnFailure = true,
            )
        }
        assertFalse(target.exists())
    }

    private fun zip(
        name: String,
        entries: List<Pair<String, ByteArray>>,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
    ): File {
        val file = temporaryFolder.newFile(name)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.setLevel(compressionLevel)
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun budget(
        maxArchiveBytes: Long = 1_024L * 1_024L,
        maxEntryCount: Int = 10,
        maxEntryBytes: Long = 1_024L,
        maxTotalBytes: Long = 10L * 1_024L,
        maxCompressionRatio: Long = 1_000L,
        compressionRatioThresholdBytes: Long = Long.MAX_VALUE,
    ) = RemoteZipBudget(
        label = "test archive",
        maxArchiveBytes = maxArchiveBytes,
        maxEntryCount = maxEntryCount,
        maxEntryUncompressedBytes = maxEntryBytes,
        maxTotalUncompressedBytes = maxTotalBytes,
        maxCompressionRatio = maxCompressionRatio,
        compressionRatioThresholdBytes = compressionRatioThresholdBytes,
    )
}
