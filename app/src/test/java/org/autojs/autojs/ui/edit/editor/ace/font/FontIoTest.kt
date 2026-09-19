package org.autojs.autojs.ui.edit.editor.ace.font

import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontIoTest {

    @Test
    fun overlappingJvmFileLockWaitsForTheOtherImplementation() {
        val root = Files.createTempDirectory("autojs6-font-lock-test").toFile()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val lockFile = FontIo.storeLockFile(root)
            assertTrue(checkNotNull(lockFile.parentFile).mkdirs())
            RandomAccessFile(lockFile, "rw").use { externalFile ->
                val externalLock = externalFile.channel.lock()
                val started = CountDownLatch(1)
                val result = executor.submit<Boolean> {
                    started.countDown()
                    FontIo.withFileLock(lockFile) { true }
                }
                try {
                    assertTrue(started.await(1, TimeUnit.SECONDS))
                    Thread.sleep(100L)
                    assertFalse(result.isDone)
                } finally {
                    externalLock.release()
                }
                assertTrue(result.get(2, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }
}
