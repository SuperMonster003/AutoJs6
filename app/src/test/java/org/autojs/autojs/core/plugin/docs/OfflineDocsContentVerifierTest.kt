package org.autojs.autojs.core.plugin.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.security.MessageDigest

class OfflineDocsContentVerifierTest {

    @Test
    fun `signed inventory verifies every declared content stream`() {
        val files = contentFiles()
        val inventory = inventory(files)

        val verified = verify(inventory, files)

        assertEquals(files.size.toLong(), verified.fileCount)
        assertEquals(files.values.sumOf { it.size.toLong() }, verified.totalBytes)
        assertEquals(inventory.sha256Hex(), verified.contentSha256)
        assertEquals(files.keys.sorted(), verified.assetPaths.toList())
    }

    @Test
    fun `unlisted APK content is not added to the serving allowlist`() {
        val declared = contentFiles()
        val actual = declared + ("hidden.html" to "must not be served".toByteArray())
        val inventory = inventory(declared)

        val verified = verify(inventory, actual, declared)

        assertFalse("hidden.html" in verified.assetPaths)
        assertEquals(declared.keys.toSet(), verified.assetPaths)
    }

    @Test
    fun `inventory hash count and byte metadata must match exactly`() {
        val files = contentFiles()
        val inventory = inventory(files)

        assertError(OfflineDocsPluginLoadError.CONTENT_SHA256_MISMATCH) {
            verify(inventory, files, expectedSha256 = "0".repeat(64))
        }
        assertError(OfflineDocsPluginLoadError.CONTENT_FILE_COUNT_MISMATCH) {
            verify(inventory, files, expectedFileCount = files.size.toLong() + 1L)
        }
        assertError(OfflineDocsPluginLoadError.CONTENT_TOTAL_BYTES_MISMATCH) {
            verify(inventory, files, expectedTotalBytes = files.values.sumOf { it.size.toLong() } + 1L)
        }
    }

    @Test
    fun `inventory encoding ordering and paths are strict`() {
        val files = contentFiles()
        val valid = inventory(files)
        val variants = listOf(
            valid.toString(Charsets.UTF_8).replace("\n", "\r\n").toByteArray(),
            valid.copyOf(valid.size - 1),
            "../index.html\t1\t${"a".repeat(64)}\n".toByteArray(),
            "index.html\t1\t${"a".repeat(64)}\nassets/main.js\t1\t${"b".repeat(64)}\n".toByteArray(),
            "index.html\t1\t${"A".repeat(64)}\n".toByteArray(),
            byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + valid,
            byteArrayOf(0xc3.toByte(), 0x28),
        )
        variants.forEach { malformed ->
            assertError(OfflineDocsPluginLoadError.INVENTORY_INVALID) {
                OfflineDocsContentVerifier.verify(
                    inventoryBytes = malformed,
                    expectedFileCount = malformed.toString(Charsets.UTF_8).count { it == '\n' }.toLong(),
                    expectedTotalBytes = 2L,
                    expectedContentSha256 = malformed.sha256Hex(),
                    openAsset = { ByteArrayInputStream(byteArrayOf(0)) },
                )
            }
        }
    }

    @Test
    fun `missing size-changed and hash-changed assets are rejected`() {
        val files = contentFiles()
        val inventory = inventory(files)

        assertError(OfflineDocsPluginLoadError.CONTENT_FILE_MISSING) {
            verify(inventory, files - "assets/main.js", declaredFiles = files)
        }
        assertError(OfflineDocsPluginLoadError.CONTENT_FILE_SIZE_MISMATCH) {
            verify(
                inventory,
                files + ("index.html" to "<html>changed length</html>".toByteArray()),
                declaredFiles = files,
            )
        }
        assertError(OfflineDocsPluginLoadError.CONTENT_FILE_HASH_MISMATCH) {
            val original = files.getValue("index.html")
            val changed = original.clone().also { bytes -> bytes[0] = (bytes[0].toInt() xor 1).toByte() }
            verify(inventory, files + ("index.html" to changed), declaredFiles = files)
        }
    }

    @Test
    fun `entry point must be explicitly declared`() {
        val files = mapOf("assets/main.js" to "const ready = true;".toByteArray())
        val inventory = inventory(files)

        assertError(OfflineDocsPluginLoadError.ENTRY_POINT_MISSING) {
            verify(inventory, files)
        }
    }

    private fun verify(
        inventory: ByteArray,
        actualFiles: Map<String, ByteArray>,
        declaredFiles: Map<String, ByteArray> = actualFiles,
        expectedFileCount: Long = declaredFiles.size.toLong(),
        expectedTotalBytes: Long = declaredFiles.values.sumOf { it.size.toLong() },
        expectedSha256: String = inventory.sha256Hex(),
    ): OfflineDocsVerifiedInventory = OfflineDocsContentVerifier.verify(
        inventoryBytes = inventory,
        expectedFileCount = expectedFileCount,
        expectedTotalBytes = expectedTotalBytes,
        expectedContentSha256 = expectedSha256,
        openAsset = { path ->
            ByteArrayInputStream(actualFiles[path] ?: throw FileNotFoundException(path))
        },
    )

    private fun inventory(files: Map<String, ByteArray>): ByteArray = files
        .toSortedMap()
        .entries
        .joinToString(separator = "") { (path, bytes) ->
            "$path\t${bytes.size}\t${bytes.sha256Hex()}\n"
        }
        .toByteArray(Charsets.UTF_8)

    private fun contentFiles(): Map<String, ByteArray> = mapOf(
        "index.html" to "<!doctype html><html></html>".toByteArray(),
        "assets/main.js" to "const ready = true;".toByteArray(),
        "assets/style.css" to "html { color: #222; }".toByteArray(),
    )

    private fun assertError(
        code: OfflineDocsPluginLoadError,
        block: () -> Unit,
    ) {
        val error = assertThrows(OfflineDocsPluginLoadException::class.java, block)
        assertEquals(code, error.code)
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
