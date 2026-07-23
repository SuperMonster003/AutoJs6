package org.autojs.autojs.core.plugin.docs

import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

class OfflineDocsVerifiedInventory internal constructor(
    val assetPaths: Set<String>,
    val fileCount: Long,
    val totalBytes: Long,
    val contentSha256: String,
)

/** Verifies the signed inventory and every content stream without relying on asset enumeration. */
internal object OfflineDocsContentVerifier {

    private val decimalPattern = Regex("0|[1-9][0-9]*")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun verify(
        inventoryBytes: ByteArray,
        expectedFileCount: Long,
        expectedTotalBytes: Long,
        expectedContentSha256: String,
        openAsset: (String) -> InputStream,
    ): OfflineDocsVerifiedInventory {
        if (inventoryBytes.isEmpty() || inventoryBytes.size > OfflineDocsPluginContract.MAX_INVENTORY_BYTES) {
            fail(OfflineDocsPluginLoadError.INVENTORY_INVALID, "Offline Documentation inventory size is invalid.")
        }
        val inventorySha256 = inventoryBytes.sha256Hex()
        if (inventorySha256 != expectedContentSha256) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_SHA256_MISMATCH,
                "Offline Documentation inventory SHA-256 does not match plugin metadata.",
            )
        }

        val text = decodeStrictUtf8(inventoryBytes)
        if (text.startsWith('\uFEFF') || '\r' in text || !text.endsWith('\n')) {
            fail(
                OfflineDocsPluginLoadError.INVENTORY_INVALID,
                "Offline Documentation inventory must be UTF-8 without BOM and use LF-terminated lines.",
            )
        }
        val lines = text.dropLast(1).split('\n')
        if (lines.any(String::isEmpty)) {
            fail(OfflineDocsPluginLoadError.INVENTORY_INVALID, "Offline Documentation inventory has an empty line.")
        }
        if (lines.size.toLong() != expectedFileCount) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_FILE_COUNT_MISMATCH,
                "Offline Documentation inventory declares ${lines.size} files; metadata declares $expectedFileCount.",
            )
        }

        val entries = ArrayList<Entry>(lines.size)
        var previousPath: String? = null
        var declaredTotalBytes = 0L
        lines.forEach { line ->
            val firstTab = line.indexOf('\t')
            val secondTab = line.indexOf('\t', firstTab + 1)
            if (firstTab <= 0 || secondTab <= firstTab + 1 || secondTab == line.lastIndex ||
                line.indexOf('\t', secondTab + 1) >= 0
            ) {
                fail(OfflineDocsPluginLoadError.INVENTORY_INVALID, "Offline Documentation inventory line is malformed.")
            }
            val path = line.substring(0, firstTab)
            val sizeText = line.substring(firstTab + 1, secondTab)
            val sha256 = line.substring(secondTab + 1)
            if (OfflineDocsAssetPathPolicy.resolve(path) != path) {
                fail(OfflineDocsPluginLoadError.INVENTORY_INVALID, "Offline Documentation inventory path is unsafe: $path.")
            }
            if (previousPath?.let { it >= path } == true) {
                fail(
                    OfflineDocsPluginLoadError.INVENTORY_INVALID,
                    "Offline Documentation inventory paths must be unique and strictly sorted.",
                )
            }
            val size = sizeText.takeIf(decimalPattern::matches)?.toLongOrNull()
                ?.takeIf { it in 0L..OfflineDocsPluginContract.MAX_FILE_BYTES }
                ?: fail(
                    OfflineDocsPluginLoadError.INVENTORY_INVALID,
                    "Offline Documentation inventory file size is invalid for $path.",
                )
            if (!sha256Pattern.matches(sha256)) {
                fail(
                    OfflineDocsPluginLoadError.INVENTORY_INVALID,
                    "Offline Documentation inventory SHA-256 is invalid for $path.",
                )
            }
            declaredTotalBytes = try {
                Math.addExact(declaredTotalBytes, size)
            } catch (_: ArithmeticException) {
                fail(
                    OfflineDocsPluginLoadError.CONTENT_TOTAL_BYTES_MISMATCH,
                    "Offline Documentation inventory byte count overflowed.",
                )
            }
            if (declaredTotalBytes > OfflineDocsPluginContract.MAX_TOTAL_BYTES) {
                fail(
                    OfflineDocsPluginLoadError.CONTENT_TOTAL_BYTES_MISMATCH,
                    "Offline Documentation inventory exceeds the supported content size.",
                )
            }
            entries += Entry(path, size, sha256)
            previousPath = path
        }
        if (declaredTotalBytes != expectedTotalBytes) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_TOTAL_BYTES_MISMATCH,
                "Offline Documentation inventory declares $declaredTotalBytes bytes; metadata declares $expectedTotalBytes.",
            )
        }
        if (entries.none { it.path == OfflineDocsPluginContract.ENTRY_POINT }) {
            fail(
                OfflineDocsPluginLoadError.ENTRY_POINT_MISSING,
                "Offline Documentation inventory does not declare ${OfflineDocsPluginContract.ENTRY_POINT}.",
            )
        }

        var actualTotalBytes = 0L
        entries.forEach { entry ->
            val actual = try {
                openAsset(entry.path).use { stream -> hashContent(stream, entry) }
            } catch (error: OfflineDocsPluginLoadException) {
                throw error
            } catch (error: Exception) {
                fail(
                    OfflineDocsPluginLoadError.CONTENT_FILE_MISSING,
                    "Offline Documentation content is missing or unreadable: ${entry.path}.",
                    error,
                )
            }
            actualTotalBytes = Math.addExact(actualTotalBytes, actual)
        }
        if (actualTotalBytes != expectedTotalBytes) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_TOTAL_BYTES_MISMATCH,
                "Offline Documentation content contains $actualTotalBytes bytes; expected $expectedTotalBytes.",
            )
        }

        val paths = entries.mapTo(LinkedHashSet(entries.size), Entry::path)
        return OfflineDocsVerifiedInventory(
            assetPaths = Collections.unmodifiableSet(paths),
            fileCount = entries.size.toLong(),
            totalBytes = actualTotalBytes,
            contentSha256 = inventorySha256,
        )
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: Exception) {
        fail(OfflineDocsPluginLoadError.INVENTORY_INVALID, "Offline Documentation inventory is not valid UTF-8.", error)
    }

    private fun hashContent(stream: InputStream, entry: Entry): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            size = Math.addExact(size, read.toLong())
            if (size > entry.size || size > OfflineDocsPluginContract.MAX_FILE_BYTES) {
                fail(
                    OfflineDocsPluginLoadError.CONTENT_FILE_SIZE_MISMATCH,
                    "Offline Documentation content size does not match its inventory: ${entry.path}.",
                )
            }
            digest.update(buffer, 0, read)
        }
        if (size != entry.size) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_FILE_SIZE_MISMATCH,
                "Offline Documentation content size does not match its inventory: ${entry.path}.",
            )
        }
        if (digest.digest().toHex() != entry.sha256) {
            fail(
                OfflineDocsPluginLoadError.CONTENT_FILE_HASH_MISMATCH,
                "Offline Documentation content SHA-256 does not match its inventory: ${entry.path}.",
            )
        }
        return size
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun fail(
        code: OfflineDocsPluginLoadError,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw OfflineDocsPluginLoadException(code, message = message, cause = cause)

    private data class Entry(
        val path: String,
        val size: Long,
        val sha256: String,
    )
}
