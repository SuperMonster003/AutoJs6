package org.autojs.autojs.apkbuilder.template

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeBuildAlignmentTest {
    @Test fun packagedNativeLibrariesProduceWarningsAndReviewableMetadataWithoutBlocking() {
        val dir = kotlin.io.path.createTempDirectory("native-build-input").toFile()
        try {
            val elf = ByteArray(120)
            val header = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
            header.put(byteArrayOf(0x7f, 69, 76, 70, 2, 1, 1))
            header.putShort(18, 183); header.putInt(20, 1)
            header.putLong(32, 64); header.putShort(54, 56); header.putShort(56, 1)
            header.putInt(64, 1); header.putLong(112, 4096)
            val archive = dir.resolve("input.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("lib/arm64-v8a/old.so")); zip.write(elf); zip.closeEntry()
                zip.putNextEntry(ZipEntry("lib/x86_64/corrupt.so")); zip.write(byteArrayOf(0)); zip.closeEntry()
            }
            val libraries = NativeBuildAlignment.inspect(archive)
            assertEquals(2, libraries.size)
            assertTrue(libraries.all { it.needsWarning })
            assertFalse(NativeBuildAlignment.Library("lib/armeabi-v7a/old.so", 4096).needsWarning)
            val metadata = dir.resolve("alignment.json")
            NativeBuildAlignment.writeMetadata(metadata, libraries)
            val entries = JsonParser.parseString(metadata.readText()).asJsonObject.getAsJsonArray("libraries")
            assertEquals(4096L, entries[0].asJsonObject["minLoadAlign"].asLong)
            assertTrue(entries[1].asJsonObject["minLoadAlign"].isJsonNull)
        } finally { dir.deleteRecursively() }
    }
}
