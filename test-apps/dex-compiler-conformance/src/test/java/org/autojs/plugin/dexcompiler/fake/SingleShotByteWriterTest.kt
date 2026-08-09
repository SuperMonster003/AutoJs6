package org.autojs.plugin.dexcompiler.fake

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class SingleShotByteWriterTest {
    @Test
    fun writesExactlyOnce() {
        val writer = SingleShotByteWriter()
        val output = ByteArrayOutputStream()

        writer.write(byteArrayOf(1, 2, 3), output)

        assertTrue(writer.hasClaimedWrite)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        assertThrows(IllegalStateException::class.java) {
            writer.write(byteArrayOf(4), output)
        }
    }

    @Test
    fun failedFirstAttemptStillConsumesWriter() {
        val writer = SingleShotByteWriter()
        val failingOutput = object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("expected")
            }
        }

        assertThrows(IOException::class.java) {
            writer.write(byteArrayOf(1), failingOutput)
        }
        assertThrows(IllegalStateException::class.java) {
            writer.write(byteArrayOf(2), ByteArrayOutputStream())
        }
    }
}
