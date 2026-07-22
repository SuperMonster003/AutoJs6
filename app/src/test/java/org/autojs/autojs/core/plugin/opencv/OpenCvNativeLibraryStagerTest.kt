package org.autojs.autojs.core.plugin.opencv

import org.autojs.plugin.opencv.api.OpenCvPluginContract
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

class OpenCvNativeLibraryStagerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `process ABI priority takes precedence over APK path order`() {
        val x64Payload = fakeElf(0x64)
        val arm64Payload = fakeElf(0x40)
        val baseApk = zip(
            "base.apk",
            "lib/x86_64/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to x64Payload,
        )
        val splitApk = zip(
            "split-arm64.apk",
            "lib/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to arm64Payload,
        )

        val staged = stage(
            apkPaths = listOf(baseApk.absolutePath, splitApk.absolutePath),
            supportedAbis = listOf("arm64-v8a", "x86_64"),
        )

        assertEquals("arm64-v8a", staged.abi)
        assertEquals(splitApk.absolutePath, staged.apkPath)
        assertEquals(
            "lib/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}",
            staged.entryName,
        )
        assertArrayEquals(arm64Payload, staged.file.readBytes())
    }

    @Test
    fun `matching CRC reuses cache and changed CRC refreshes same-size payload`() {
        val apk = temporaryFolder.newFile("runtime.apk")
        val initialPayload = fakeElf(0x01)
        val updatedPayload = fakeElf(0x02)
        assertEquals(initialPayload.size, updatedPayload.size)
        writeZip(
            apk,
            "lib/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to initialPayload,
        )

        val first = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        val cacheMarker = 1_700_000_000_000L
        assertTrue(first.file.setLastModified(cacheMarker))

        val reused = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        assertEquals(first.file.absolutePath, reused.file.absolutePath)
        assertEquals(cacheMarker, reused.file.lastModified())
        assertArrayEquals(initialPayload, reused.file.readBytes())

        writeZip(
            apk,
            "lib/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to updatedPayload,
        )
        val refreshed = stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        assertEquals(first.file.absolutePath, refreshed.file.absolutePath)
        assertArrayEquals(updatedPayload, refreshed.file.readBytes())
    }

    @Test
    fun `missing process ABI payload reports ABI not supported`() {
        val apk = zip(
            "x86-only.apk",
            "lib/x86/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to fakeElf(0x01),
        )

        val error = assertThrows(OpenCvPluginLoadException::class.java) {
            stage(listOf(apk.absolutePath), listOf("arm64-v8a", "armeabi-v7a"))
        }

        assertEquals(OpenCvPluginLoadError.ABI_NOT_SUPPORTED, error.code)
    }

    @Test
    fun `non-ELF payload is rejected and never published`() {
        val apk = zip(
            "invalid.apk",
            "lib/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}" to "not-an-elf".toByteArray(),
        )

        val error = assertThrows(OpenCvPluginLoadException::class.java) {
            stage(listOf(apk.absolutePath), listOf("arm64-v8a"))
        }

        assertEquals(OpenCvPluginLoadError.NATIVE_LIBRARY_INVALID, error.code)
        assertTrue(
            temporaryFolder.root
                .resolve("staged/test-package/arm64-v8a/${OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME}")
                .let { !it.exists() },
        )
    }

    @Test
    fun `unsafe native library name is rejected before reading payload`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            OpenCvNativeLibraryStager.stage(
                apkPaths = listOf(temporaryFolder.newFile("empty.apk").absolutePath),
                destinationRoot = temporaryFolder.newFolder("unsafe-staged"),
                packageIdentity = "test-package",
                supportedAbis = listOf("arm64-v8a"),
                nativeLibraryFileName = "../libopencv_java4.so",
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsafe OpenCV native library name"))
    }

    private fun stage(
        apkPaths: List<String>,
        supportedAbis: List<String>,
    ): OpenCvNativeLibraryStager.StagedLibrary = OpenCvNativeLibraryStager.stage(
        apkPaths = apkPaths,
        destinationRoot = temporaryFolder.root.resolve("staged"),
        packageIdentity = "test-package",
        supportedAbis = supportedAbis,
        nativeLibraryFileName = OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
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

    private fun fakeElf(marker: Int): ByteArray = byteArrayOf(
        0x7f,
        'E'.code.toByte(),
        'L'.code.toByte(),
        'F'.code.toByte(),
        0x02,
        0x01,
        marker.toByte(),
        0x00,
    )
}
