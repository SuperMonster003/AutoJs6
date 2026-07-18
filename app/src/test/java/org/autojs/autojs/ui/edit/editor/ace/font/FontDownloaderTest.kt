package org.autojs.autojs.ui.edit.editor.ace.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FontDownloaderTest {
    @Test
    fun storeStartupRemovesPartsLeftByDeadProcess() {
        val directory = Files.createTempDirectory("font-stale-part-test").toFile()
        try {
            val parts = directory.resolve(".parts").also { assertTrue(it.mkdirs()) }
            val stale = parts.resolve("stale-download.part").also { it.writeBytes(ByteArray(32)) }
            assertTrue(stale.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)))

            FontStore(directory)

            assertFalse(stale.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun storeStartupDoesNotDeletePartWithAnActiveLease() {
        val directory = Files.createTempDirectory("font-active-part-test").toFile()
        try {
            val bytes = FontTestFixtures.validWoff2()
            val font = FontTestFixtures.remoteFont(bytes)
            val owner = FontStore(directory)
            val activePart = owner.createPartFile(font)

            FontStore(directory)

            assertTrue(activePart.exists())
            owner.discardPart(activePart)
            assertFalse(activePart.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun downloadsValidFontReportsProgressAndResolvesByManifestDigest() {
        val directory = Files.createTempDirectory("font-download-test").toFile()
        val bytes = FontTestFixtures.validWoff2(size = 192)
        val font = FontTestFixtures.remoteFont(bytes)
        val events = Collections.synchronizedList(mutableListOf<FontDownloadEvent>())
        val downloader = FontDownloader(FontStore(directory), byteSource(mapOf(font.artifact.url to bytes)))
        try {
            val installed = downloader.enqueue(font) { events += it }.future.get(5, TimeUnit.SECONDS)
            val store = FontStore(directory)

            assertEquals(font.id, installed.fontId)
            assertEquals(font.artifact.sha256, installed.sha256)
            assertEquals(installed.file, store.installedFile(font.id, font.artifact.sha256))
            assertNull(store.installedFile(font.id, "0".repeat(64)))
            val opened = store.openInstalledFont(font.id, font.artifact.sha256)
            assertNotNull(opened)
            assertEquals(bytes.size.toLong(), opened!!.sizeBytes)
            opened.inputStream.use { assertArrayEquals(bytes, it.readBytes()) }
            assertTrue(events.any { it is FontDownloadEvent.Progress && it.bytesDownloaded == bytes.size.toLong() })
            assertTrue(events.last() is FontDownloadEvent.Success)
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun sameArtifactSubscribersShareTransferAndCancelIndependently() {
        val directory = Files.createTempDirectory("font-download-merge-test").toFile()
        val bytes = FontTestFixtures.validWoff2(size = 160)
        val font = FontTestFixtures.remoteFont(bytes)
        val opened = CountDownLatch(1)
        val release = CountDownLatch(1)
        val opens = AtomicInteger()
        val source = FontDownloadSource {
            opens.incrementAndGet()
            opened.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            ByteArrayConnection(bytes)
        }
        val downloader = FontDownloader(FontStore(directory), source)
        try {
            val first = downloader.enqueue(font)
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            val second = downloader.enqueue(font)
            assertTrue(first.cancel())
            release.countDown()

            assertNotNull(second.future.get(5, TimeUnit.SECONDS))
            assertTrue(first.future.isCancelled)
            assertEquals(1, opens.get())
        } finally {
            release.countDown()
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsDigestMismatchAndCleansPartialFile() {
        val directory = Files.createTempDirectory("font-download-invalid-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes, sha256 = "0".repeat(64))
        val downloader = FontDownloader(FontStore(directory), byteSource(mapOf(font.artifact.url to bytes)))
        try {
            val failure = runCatching { downloader.enqueue(font).future.get(5, TimeUnit.SECONDS) }.exceptionOrNull()
            assertNotNull(failure)
            assertNull(FontStore(directory).installedFont(font.id))
            assertTrue(directory.resolve(".parts").listFiles().isNullOrEmpty())
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonWoff2PayloadEvenWhenSizeAndDigestMatch() {
        val directory = Files.createTempDirectory("font-download-header-test").toFile()
        val bytes = ByteArray(96) { 3 }
        val font = FontTestFixtures.remoteFont(bytes)
        val downloader = FontDownloader(FontStore(directory), byteSource(mapOf(font.artifact.url to bytes)))
        try {
            val failure = runCatching { downloader.enqueue(font).future.get(5, TimeUnit.SECONDS) }.exceptionOrNull()
            assertNotNull(failure)
            assertNull(FontStore(directory).installedFont(font.id))
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellingLastSubscriberCancelsActiveConnection() {
        val directory = Files.createTempDirectory("font-download-cancel-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes)
        val connection = BlockingConnection(bytes.size.toLong())
        val downloader = FontDownloader(FontStore(directory), FontDownloadSource { connection })
        try {
            val subscription = downloader.enqueue(font)
            assertTrue(connection.readStarted.await(5, TimeUnit.SECONDS))
            // Cancelling the exposed future must be equivalent to cancelling the subscription.
            assertTrue(subscription.future.cancel(false))
            assertTrue(connection.cancelled.await(5, TimeUnit.SECONDS))
            assertTrue(subscription.future.isCancelled)
        } finally {
            connection.release.countDown()
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancelByFontIdAtomicallyCancelsAllSubscribers() {
        val directory = Files.createTempDirectory("font-download-cancel-all-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes)
        val connection = BlockingConnection(bytes.size.toLong())
        val downloader = FontDownloader(FontStore(directory), FontDownloadSource { connection })
        try {
            val first = downloader.enqueue(font)
            assertTrue(connection.readStarted.await(5, TimeUnit.SECONDS))
            val second = downloader.enqueue(font)

            assertTrue(downloader.cancel(font.id))
            assertTrue(connection.cancelled.await(5, TimeUnit.SECONDS))
            assertTrue(first.future.isCancelled)
            assertTrue(second.future.isCancelled)
            assertFalse(downloader.isDownloading(font.id))
        } finally {
            connection.release.countDown()
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun immediateRetryDoesNotAttachToCancelledTransfer() {
        val directory = Files.createTempDirectory("font-download-retry-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes)
        val first = StickyCancelledConnection(bytes.size.toLong())
        val opens = AtomicInteger()
        val downloader = FontDownloader(
            FontStore(directory),
            FontDownloadSource {
                if (opens.getAndIncrement() == 0) first else ByteArrayConnection(bytes)
            },
        )
        try {
            val cancelled = downloader.enqueue(font)
            assertTrue(first.readStarted.await(5, TimeUnit.SECONDS))
            assertTrue(cancelled.cancel())

            val retry = downloader.enqueue(font)
            assertNotNull(retry.future.get(5, TimeUnit.SECONDS))
            assertEquals(2, opens.get())
        } finally {
            first.release.countDown()
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun commitGateRejectsCancellationAndRevalidatesCurrentCatalog() {
        val directory = Files.createTempDirectory("font-download-commit-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes)
        val validationStarted = CountDownLatch(1)
        val releaseValidation = CountDownLatch(1)
        val downloader = FontDownloader(
            store = FontStore(directory),
            source = byteSource(mapOf(font.artifact.url to bytes)),
            commitValidator = FontDownloadCommitValidator {
                validationStarted.countDown()
                assertTrue(releaseValidation.await(5, TimeUnit.SECONDS))
            },
        )
        try {
            val subscription = downloader.enqueue(font)
            assertTrue(validationStarted.await(5, TimeUnit.SECONDS))
            assertFalse(subscription.cancel())
            assertFalse(downloader.cancel(font.id))
            assertFalse(subscription.future.isCancelled)
            releaseValidation.countDown()
            assertNotNull(subscription.future.get(5, TimeUnit.SECONDS))
        } finally {
            releaseValidation.countDown()
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun commitValidatorCanRejectArtifactChangedDuringDownload() {
        val directory = Files.createTempDirectory("font-download-revoked-test").toFile()
        val bytes = FontTestFixtures.validWoff2()
        val font = FontTestFixtures.remoteFont(bytes)
        val downloader = FontDownloader(
            store = FontStore(directory),
            source = byteSource(mapOf(font.artifact.url to bytes)),
            commitValidator = FontDownloadCommitValidator { throw RevokedFontException(it.id) },
        )
        try {
            val failure = runCatching { downloader.enqueue(font).future.get(5, TimeUnit.SECONDS) }.exceptionOrNull()
            assertNotNull(failure)
            assertNull(FontStore(directory).installedFont(font.id))
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun installingUpdateKeepsOnlyOneVersionPerId() {
        val directory = Files.createTempDirectory("font-update-test").toFile()
        val v1Bytes = FontTestFixtures.validWoff2(fill = 1)
        val v2Bytes = FontTestFixtures.validWoff2(fill = 2)
        val v1 = FontTestFixtures.remoteFont(v1Bytes, version = "1")
        val v2 = FontTestFixtures.remoteFont(v2Bytes, version = "2")
        val downloader = FontDownloader(
            FontStore(directory),
            byteSource(mapOf(v1.artifact.url to v1Bytes, v2.artifact.url to v2Bytes)),
        )
        try {
            val old = downloader.enqueue(v1).future.get(5, TimeUnit.SECONDS)
            val current = downloader.enqueue(v2).future.get(5, TimeUnit.SECONDS)
            val store = FontStore(directory)

            assertFalse(old.file.exists())
            assertEquals(v2.artifact.sha256, store.installedFont(v2.id)?.sha256)
            assertEquals(current.file, store.installedFile(v2.id))
            assertEquals(1, current.file.parentFile!!.listFiles { file -> file.extension == "woff2" }!!.size)
            assertFalse(store.deleteIfCurrent(v2.id, old.sha256))
            assertEquals(v2.artifact.sha256, store.installedFont(v2.id)?.sha256)
            assertTrue(store.deleteIfCurrent(v2.id, current.sha256))
            assertNull(store.installedFont(v2.id))
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun startupQueryRejectsLocallyCorruptedFont() {
        val directory = Files.createTempDirectory("font-corruption-test").toFile()
        val bytes = FontTestFixtures.validWoff2(size = 160)
        val font = FontTestFixtures.remoteFont(bytes)
        val downloader = FontDownloader(FontStore(directory), byteSource(mapOf(font.artifact.url to bytes)))
        try {
            val installed = downloader.enqueue(font).future.get(5, TimeUnit.SECONDS)
            RandomAccessFile(installed.file, "rw").use { file ->
                file.seek(80)
                file.write(file.read().xor(0x7f))
            }

            assertNull(FontStore(directory).installedFont(font.id))
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun webViewOpenReverifiesTimestampPreservingTampering() {
        val directory = Files.createTempDirectory("font-open-reverification-test").toFile()
        val bytes = FontTestFixtures.validWoff2(size = 160)
        val font = FontTestFixtures.remoteFont(bytes)
        val store = FontStore(directory)
        val downloader = FontDownloader(store, byteSource(mapOf(font.artifact.url to bytes)))
        try {
            val installed = downloader.enqueue(font).future.get(5, TimeUnit.SECONDS)
            val originalTimestamp = installed.file.lastModified()
            assertNotNull(store.installedFont(font.id))
            RandomAccessFile(installed.file, "rw").use { file ->
                file.seek(80)
                file.write(file.read().xor(0x7f))
            }
            assertTrue(installed.file.setLastModified(originalTimestamp))

            assertNull(store.openInstalledFont(font.id, font.artifact.sha256))
        } finally {
            downloader.close()
            directory.deleteRecursively()
        }
    }

    private fun byteSource(responses: Map<String, ByteArray>): FontDownloadSource = FontDownloadSource { url ->
        ByteArrayConnection(responses.getValue(url))
    }

    private class ByteArrayConnection(private val bytes: ByteArray) : FontDownloadConnection {
        private val cancelled = AtomicBoolean()
        override val contentLength: Long = bytes.size.toLong()
        override val inputStream: InputStream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (cancelled.get()) return -1
                return super.read(buffer, offset, length)
            }
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun close() = Unit
    }

    private class BlockingConnection(override val contentLength: Long) : FontDownloadConnection {
        val readStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val release = CountDownLatch(1)
        override val inputStream: InputStream = object : InputStream() {
            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                readStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
                return -1
            }
        }

        override fun cancel() {
            cancelled.countDown()
            release.countDown()
        }

        override fun close() = Unit
    }

    private class StickyCancelledConnection(override val contentLength: Long) : FontDownloadConnection {
        val readStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        override val inputStream: InputStream = object : InputStream() {
            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                readStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
                return -1
            }
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }
}
