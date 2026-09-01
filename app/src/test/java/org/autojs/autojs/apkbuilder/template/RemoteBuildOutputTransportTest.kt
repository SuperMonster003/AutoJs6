package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class RemoteBuildOutputTransportTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copyToFile_acceptsExactDeclaredAndMaximumBoundary() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val output = temporaryFolder.newFile("boundary.apk")

        val result = RemoteBuildOutputTransport.copyToFile(
            input = ByteArrayInputStream(bytes),
            outputFile = output,
            declaredSizeBytes = bytes.size.toLong(),
            expectedSha256 = sha256(bytes),
            maxBytes = bytes.size.toLong(),
        )

        assertArrayEquals(bytes, output.readBytes())
        assertEquals(bytes.size.toLong(), result.bytesCopied)
        assertEquals(sha256(bytes), result.sha256)
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_preservesExistingOutputWhenLimitIsExceeded() {
        val output = temporaryFolder.newFile("partial.apk")
        val previous = byteArrayOf(9, 8, 7)
        output.writeBytes(previous)

        assertThrows(IOException::class.java) {
            RemoteBuildOutputTransport.copyToFile(
                input = ChunkedInputStream(byteArrayOf(1, 2, 3, 4, 5), chunkSize = 2),
                outputFile = output,
                declaredSizeBytes = 0L,
                maxBytes = 4L,
            )
        }

        assertArrayEquals(previous, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_preservesExistingOutputWhenActualSizeDoesNotMatchDeclaration() {
        val output = temporaryFolder.newFile("mismatch.apk")
        val previous = byteArrayOf(7, 6, 5)
        output.writeBytes(previous)

        assertThrows(IOException::class.java) {
            RemoteBuildOutputTransport.copyToFile(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                outputFile = output,
                declaredSizeBytes = 4L,
                maxBytes = 4L,
            )
        }

        assertArrayEquals(previous, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_preservesExistingOutputWhenDigestDoesNotMatch() {
        val bytes = byteArrayOf(1, 2, 3)
        val previous = byteArrayOf(4, 5, 6)
        val output = temporaryFolder.newFile("digest-mismatch.apk").apply {
            writeBytes(previous)
        }

        assertThrows(IOException::class.java) {
            RemoteBuildOutputTransport.copyToFile(
                input = ByteArrayInputStream(bytes),
                outputFile = output,
                declaredSizeBytes = bytes.size.toLong(),
                expectedSha256 = sha256(byteArrayOf(8, 8, 8)),
            )
        }

        assertArrayEquals(previous, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_cancellationDoesNotPublishOrLeaveTemporaryFiles() {
        val previous = byteArrayOf(3, 2, 1)
        val output = temporaryFolder.newFile("cancelled.apk").apply {
            writeBytes(previous)
        }
        var cancellationChecks = 0

        assertThrows(CancellationException::class.java) {
            RemoteBuildOutputTransport.copyToFile(
                input = ChunkedInputStream(ByteArray(32) { it.toByte() }, chunkSize = 4),
                outputFile = output,
                declaredSizeBytes = 32L,
                isCancelled = { cancellationChecks++ >= 2 },
            )
        }

        assertArrayEquals(previous, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_replacesExistingOutputOnlyAfterValidation() {
        val bytes = byteArrayOf(5, 4, 3, 2, 1)
        val output = temporaryFolder.newFile("replace.apk").apply {
            writeBytes(byteArrayOf(9, 9, 9))
        }

        RemoteBuildOutputTransport.copyToFile(
            input = ByteArrayInputStream(bytes),
            outputFile = output,
            declaredSizeBytes = bytes.size.toLong(),
            expectedSha256 = sha256(bytes),
        )

        assertTrue(output.isFile)
        assertArrayEquals(bytes, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    @Test
    fun copyToFile_preservesExistingOutputWhenTemporaryFileValidatorRejects() {
        val bytes = byteArrayOf(6, 5, 4, 3)
        val previous = byteArrayOf(1, 2, 3)
        val output = temporaryFolder.newFile("validator-rejected.apk").apply {
            writeBytes(previous)
        }
        var validationCount = 0

        assertThrows(IOException::class.java) {
            RemoteBuildOutputTransport.copyToFile(
                input = ByteArrayInputStream(bytes),
                outputFile = output,
                declaredSizeBytes = bytes.size.toLong(),
                expectedSha256 = sha256(bytes),
                validateTemporaryFile = { temporary ->
                    validationCount++
                    assertArrayEquals(bytes, temporary.readBytes())
                    throw IOException("Rejected by test validator")
                },
            )
        }

        assertEquals(1, validationCount)
        assertArrayEquals(previous, output.readBytes())
        assertNoPublicationArtifacts(output)
    }

    private fun assertNoPublicationArtifacts(output: java.io.File) {
        val prefix = ".${output.name}."
        assertFalse(
            temporaryFolder.root.listFiles().orEmpty().any { file ->
                file.name.startsWith(prefix) &&
                        (file.name.endsWith(".tmp") || file.name.endsWith(".publish-backup"))
            }
        )
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private class ChunkedInputStream(
        bytes: ByteArray,
        private val chunkSize: Int,
    ) : InputStream() {

        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return delegate.read(buffer, offset, minOf(length, chunkSize))
        }
    }
}
