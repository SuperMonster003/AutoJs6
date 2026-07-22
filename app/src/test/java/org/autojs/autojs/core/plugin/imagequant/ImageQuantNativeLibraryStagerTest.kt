package org.autojs.autojs.core.plugin.imagequant

import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImageQuantNativeLibraryStagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `process ABI priority takes precedence over APK path order`() {
        val x64Payload = fakeElf("x86_64", 0x64)
        val arm64Payload = fakeElf("arm64-v8a", 0x40)
        val baseApk = zip(
            "base.apk",
            "lib/x86_64/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to x64Payload,
        )
        val splitApk = zip(
            "split-arm64.apk",
            "lib/arm64-v8a/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to arm64Payload,
        )

        val staged = stage(
            apkPaths = listOf(baseApk.absolutePath, splitApk.absolutePath),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
        )

        assertEquals("arm64-v8a", staged.abi)
        assertEquals(splitApk.absolutePath, staged.apkPath)
        assertArrayEquals(arm64Payload, staged.file.readBytes())
    }

    @Test
    fun `matching CRC reuses cache and changed CRC refreshes same-size payload`() {
        val apk = temporaryFolder.newFile("runtime.apk")
        val initialPayload = fakeElf("arm64-v8a", 0x01)
        val updatedPayload = fakeElf("arm64-v8a", 0x02)
        writeZip(
            apk,
            "lib/arm64-v8a/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to initialPayload,
        )

        val first = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        val cacheMarker = 1_700_000_000_000L
        assertTrue(first.file.setLastModified(cacheMarker))
        val reused = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        assertEquals(cacheMarker, reused.file.lastModified())

        writeZip(
            apk,
            "lib/arm64-v8a/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to updatedPayload,
        )
        val refreshed = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        assertArrayEquals(updatedPayload, refreshed.file.readBytes())
    }

    @Test
    fun `libs entry root is supported`() {
        val payload = fakeElf("x86", 0x11)
        val apk = zip(
            "legacy-root.apk",
            "libs/x86/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to payload,
        )

        val staged = stage(listOf(apk.absolutePath), listOf("x86"))

        assertEquals("libs/x86/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}", staged.entryName)
        assertArrayEquals(payload, staged.file.readBytes())
    }

    @Test
    fun `missing process ABI payload reports ABI not supported`() {
        val apk = zip(
            "x86-only.apk",
            "lib/x86/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to fakeElf("x86", 0x01),
        )

        val error = assertThrows(ImageQuantPluginLoadException::class.java) {
            stage(listOf(apk.absolutePath), listOf("arm64-v8a", "armeabi-v7a"))
        }

        assertEquals(ImageQuantPluginLoadError.ABI_NOT_SUPPORTED, error.code)
    }

    @Test
    fun `non-ELF and wrong-architecture payloads are rejected`() {
        val invalidApk = zip(
            "invalid.apk",
            "lib/arm64-v8a/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to ByteArray(32),
        )
        val wrongAbiApk = zip(
            "wrong-abi.apk",
            "lib/arm64-v8a/${ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME}" to fakeElf("x86_64", 0x01),
        )

        listOf(invalidApk, wrongAbiApk).forEach { apk ->
            val error = assertThrows(ImageQuantPluginLoadException::class.java) {
                stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
            }
            assertEquals(ImageQuantPluginLoadError.NATIVE_LIBRARY_INVALID, error.code)
        }
    }

    @Test
    fun `unsafe native library name is rejected before reading payload`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ImageQuantNativeLibraryStager.stage(
                apkPaths = listOf(temporaryFolder.newFile("empty.apk").absolutePath),
                destinationRoot = temporaryFolder.newFolder("unsafe-staged"),
                packageIdentity = "test-package",
                supportedAbis = listOf("arm64-v8a"),
                nativeLibraryFileName = "../libpngquant_bridge.so",
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsafe Image Quantization native library name"))
    }

    private fun stage(
        apkPaths: List<String>,
        supportedAbis: List<String>,
    ): ImageQuantNativeLibraryStager.StagedLibrary = ImageQuantNativeLibraryStager.stage(
        apkPaths = apkPaths,
        destinationRoot = temporaryFolder.root.resolve("staged"),
        packageIdentity = "test-package",
        supportedAbis = supportedAbis,
        nativeLibraryFileName = ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME,
    )

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File =
        temporaryFolder.newFile(name).also { file -> writeZip(file, *entries) }

    private fun writeZip(file: File, vararg entries: Pair<String, ByteArray>) {
        ZipOutputStream(file.outputStream().buffered()).use { output ->
            entries.forEach { (name, payload) ->
                output.putNextEntry(ZipEntry(name))
                output.write(payload)
                output.closeEntry()
            }
        }
    }

    private fun fakeElf(abi: String, marker: Int): ByteArray {
        val (elfClass, machine) = when (abi) {
            "armeabi-v7a" -> 1 to 40
            "arm64-v8a" -> 2 to 183
            "x86" -> 1 to 3
            "x86_64" -> 2 to 62
            else -> error("Unsupported test ABI: $abi")
        }
        return ByteArray(32).apply {
            this[0] = 0x7f
            this[1] = 'E'.code.toByte()
            this[2] = 'L'.code.toByte()
            this[3] = 'F'.code.toByte()
            this[4] = elfClass.toByte()
            this[5] = 1
            this[6] = 1
            this[18] = (machine and 0xff).toByte()
            this[19] = ((machine ushr 8) and 0xff).toByte()
            this[31] = marker.toByte()
        }
    }
}
