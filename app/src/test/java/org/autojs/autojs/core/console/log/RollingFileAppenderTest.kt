package org.autojs.autojs.core.console.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RollingFileAppenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun event(message: String) = LogEvent(Level.INFO, message, "test")

    private fun read(file: File): String = if (file.exists()) file.readText(Charsets.UTF_8) else "<missing>"

    @Test
    fun appendsToExistingFileAndCreatesParentDirectories() {
        val file = File(temp.root, "nested/dir/app.log")
        file.parentFile!!.mkdirs()
        file.writeText("old\n")
        val appender = RollingFileAppender(file, PatternLayout("%m%n"))
        appender.append(event("new"))
        appender.close()
        assertEquals("old\nnew\n", read(file))

        val fresh = File(temp.root, "deeper/still/app.log")
        RollingFileAppender(fresh, PatternLayout("%m%n")).apply {
            append(event("first"))
            close()
        }
        assertEquals("first\n", read(fresh))
    }

    @Test
    fun rollsOverAtMaximumFileSizeAndRotatesBackups() {
        val file = File(temp.root, "app.log")
        val appender = RollingFileAppender(file, PatternLayout("%m%n")).apply {
            maximumFileSize = 20
            maxBackupIndex = 2
        }
        // Each line is exactly 10 bytes, so every second line triggers a roll-over.
        (1..6).forEach { appender.append(event("line-%04d".format(it))) }
        appender.close()

        assertEquals("", read(file))
        assertEquals("line-0005\nline-0006\n", read(File(temp.root, "app.log.1")))
        assertEquals("line-0003\nline-0004\n", read(File(temp.root, "app.log.2")))
        assertFalse(File(temp.root, "app.log.3").exists())
    }

    @Test
    fun rollOverCountsExistingBytesAndUtf8Length() {
        val file = File(temp.root, "app.log")
        file.writeText("0123456789")
        val appender = RollingFileAppender(file, PatternLayout("%m%n")).apply {
            maximumFileSize = 17
            maxBackupIndex = 1
        }
        // 10 existing bytes + "中文\n" (7 bytes in UTF-8) reaches the limit.
        appender.append(event("中文"))
        appender.close()
        assertEquals("", read(file))
        assertEquals("0123456789中文\n", read(File(temp.root, "app.log.1")))
    }

    @Test
    fun zeroBackupsTruncatesInstead() {
        val file = File(temp.root, "app.log")
        val appender = RollingFileAppender(file, PatternLayout("%m%n")).apply {
            maximumFileSize = 10
            maxBackupIndex = 0
        }
        appender.append(event("123456789"))
        assertEquals("", read(file))
        assertFalse(File(temp.root, "app.log.1").exists())
        appender.append(event("after"))
        appender.close()
        assertEquals("after\n", read(file))
    }

    @Test
    fun withoutImmediateFlushWritesAreBufferedUntilFlushOrClose() {
        val file = File(temp.root, "app.log")
        val appender = RollingFileAppender(file, PatternLayout("%m%n"), immediateFlush = false)
        appender.append(event("buffered"))
        assertEquals("", read(file))
        appender.flush()
        assertEquals("buffered\n", read(file))
        appender.append(event("more"))
        appender.close()
        assertEquals("buffered\nmore\n", read(file))

        val immediate = RollingFileAppender(File(temp.root, "now.log"), PatternLayout("%m%n"))
        immediate.append(event("now"))
        assertEquals("now\n", read(File(temp.root, "now.log")))
        immediate.close()
    }

    @Test
    fun closedAppenderDropsFurtherEvents() {
        val file = File(temp.root, "app.log")
        val appender = RollingFileAppender(file, PatternLayout("%m%n"))
        appender.append(event("kept"))
        appender.close()
        appender.append(event("dropped"))
        assertEquals("kept\n", read(file))
        assertTrue(file.exists())
    }

}
