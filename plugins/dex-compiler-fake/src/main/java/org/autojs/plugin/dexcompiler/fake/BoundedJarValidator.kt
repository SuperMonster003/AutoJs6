package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

data class ValidatedJar(
    val compressedSizeBytes: Long,
    val archiveEntryCount: Int,
    val classEntryCount: Int,
    val uncompressedSizeBytes: Long,
    val totalClassBytes: Long,
)

object BoundedJarValidator {
    fun readAndValidate(request: DexCompileRequest, input: InputStream): ValidatedJar {
        val limits = FakeDexCompilerFixture.capabilities.limits
        val archive = readBounded(input, limits.maxCompressedProgramBytes)
        if (archive.size.toLong() != request.programSizeBytes) {
            fail(
                DexCompilerErrorCode.INVALID_REQUEST,
                "Program size mismatch: expected ${request.programSizeBytes}, received ${archive.size}",
            )
        }
        val actualSha256 = FakeDexHashes.sha256(archive)
        if (actualSha256 != request.programSha256) {
            fail(DexCompilerErrorCode.INVALID_REQUEST, "Program SHA-256 mismatch")
        }
        return validateArchive(archive)
    }

    private fun readBounded(input: InputStream, maximumBytes: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, 64L * 1024L).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read == 0) {
                continue
            }
            total += read
            if (total > maximumBytes) {
                fail(DexCompilerErrorCode.INPUT_TOO_LARGE, "Program archive exceeds the fixture limit")
            }
            output.write(buffer, 0, read)
        }
        if (total == 0L) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "Program archive is empty")
        }
        return output.toByteArray()
    }

    private fun validateArchive(bytes: ByteArray): ValidatedJar {
        if (!hasZipSignature(bytes)) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "Program is not a ZIP-compatible JAR")
        }
        val limits = FakeDexCompilerFixture.capabilities.limits
        val framedEntryCount = StrictJarZipFraming.validate(bytes, limits.maxArchiveEntries)
        val seenNames = HashSet<String>()
        var entryCount = 0
        var classCount = 0
        var uncompressedBytes = 0L
        var totalClassBytes = 0L
        var totalCompressedBytes = 0L

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > limits.maxArchiveEntries) {
                        fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR contains too many entries")
                    }
                    val canonicalName = canonicalEntryName(entry.name, entry.isDirectory)
                    if (!seenNames.add(canonicalName)) {
                        fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR contains a duplicate entry: $canonicalName")
                    }
                    if (!entry.isDirectory) {
                        val isClass = entry.name.endsWith(".class")
                        val entryContent = readEntry(zip, isClass, limits.maxSingleClassBytes) { count ->
                            uncompressedBytes += count
                            if (uncompressedBytes > limits.maxUncompressedProgramBytes) {
                                fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR uncompressed data exceeds the fixture limit")
                            }
                        }
                        val compressedSize = entry.compressedSize
                        if (compressedSize < 0L) {
                            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR entry has no measurable compressed size")
                        }
                        enforceCompressionRatio(
                            uncompressedBytes = entryContent.sizeBytes,
                            compressedBytes = compressedSize,
                            maximumRatio = MAX_ENTRY_COMPRESSION_RATIO,
                            label = "JAR entry",
                        )
                        totalCompressedBytes += compressedSize
                        if (isClass) {
                            classCount++
                            totalClassBytes += entryContent.sizeBytes
                            if (totalClassBytes > limits.maxTotalClassBytes) {
                                fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR class data exceeds the fixture limit")
                            }
                            validateClassMagic(entry.name, requireNotNull(entryContent.classBytes))
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: FakeDexCompileFailure) {
            throw error
        } catch (error: IOException) {
            throw FakeDexCompileFailure(
                DexCompilerErrorCode.INVALID_ARCHIVE,
                DexCompilerFailurePhase.INPUT_VALIDATION,
                "Program JAR is malformed",
                error,
            )
        } catch (error: IllegalArgumentException) {
            throw FakeDexCompileFailure(
                DexCompilerErrorCode.INVALID_ARCHIVE,
                DexCompilerFailurePhase.INPUT_VALIDATION,
                "Program JAR contains malformed metadata",
                error,
            )
        }

        if (entryCount == 0) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "Program JAR contains no entries")
        }
        if (entryCount != framedEntryCount) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "Program JAR local and central entry counts disagree")
        }
        if (classCount == 0) {
            fail(DexCompilerErrorCode.NO_PROGRAM_CLASSES, "Program JAR contains no class files")
        }
        enforceCompressionRatio(
            uncompressedBytes = uncompressedBytes,
            compressedBytes = totalCompressedBytes,
            maximumRatio = MAX_AGGREGATE_COMPRESSION_RATIO,
            label = "JAR aggregate",
        )
        enforceCompressionRatio(
            uncompressedBytes = uncompressedBytes,
            compressedBytes = bytes.size.toLong(),
            maximumRatio = MAX_AGGREGATE_COMPRESSION_RATIO,
            label = "JAR envelope",
        )
        return ValidatedJar(
            compressedSizeBytes = bytes.size.toLong(),
            archiveEntryCount = entryCount,
            classEntryCount = classCount,
            uncompressedSizeBytes = uncompressedBytes,
            totalClassBytes = totalClassBytes,
        )
    }

    private fun readEntry(
        input: InputStream,
        isClass: Boolean,
        maxSingleClassBytes: Long,
        onBytes: (Int) -> Unit,
    ): EntryContent {
        val output = if (isClass) ByteArrayOutputStream() else null
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read == 0) {
                continue
            }
            entryBytes += read
            onBytes(read)
            if (isClass && entryBytes > maxSingleClassBytes) {
                fail(DexCompilerErrorCode.INVALID_ARCHIVE, "A class entry exceeds the fixture limit")
            }
            output?.write(buffer, 0, read)
        }
        return EntryContent(
            sizeBytes = entryBytes,
            classBytes = output?.toByteArray(),
        )
    }

    private fun canonicalEntryName(name: String, isDirectory: Boolean): String {
        val expectedBody = if (isDirectory) name.removeSuffix("/") else name
        val hasCanonicalDirectorySuffix = !isDirectory || (name.endsWith('/') && !name.endsWith("//"))
        val segments = expectedBody.split('/')
        if (
            name.isEmpty() ||
            name.startsWith('/') ||
            name.contains('\u0000') ||
            name.contains('\\') ||
            name.contains(':') ||
            !hasCanonicalDirectorySuffix ||
            Normalizer.normalize(expectedBody, Normalizer.Form.NFC) != expectedBody ||
            segments.any { it.isEmpty() || it == "." || it == ".." }
        ) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "JAR entry name is unsafe: $name")
        }
        return segments.joinToString("/")
    }

    private fun validateClassMagic(name: String, bytes: ByteArray) {
        if (bytes.size < CLASS_MAGIC.size || !bytes.copyOfRange(0, CLASS_MAGIC.size).contentEquals(CLASS_MAGIC)) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "Class entry has invalid magic: $name")
        }
    }

    private fun hasZipSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) {
            return false
        }
        val signature = (bytes[0].toInt() and 0xff) or
            ((bytes[1].toInt() and 0xff) shl 8) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 24)
        return signature in ZIP_START_SIGNATURES
    }

    private fun enforceCompressionRatio(
        uncompressedBytes: Long,
        compressedBytes: Long,
        maximumRatio: Long,
        label: String,
    ) {
        val exceedsLimit = when {
            uncompressedBytes == 0L -> false
            compressedBytes <= 0L -> true
            compressedBytes > Long.MAX_VALUE / maximumRatio -> false
            else -> uncompressedBytes > compressedBytes * maximumRatio
        }
        if (exceedsLimit) {
            fail(DexCompilerErrorCode.INVALID_ARCHIVE, "$label compression ratio exceeds the fixture limit")
        }
    }

    private fun fail(code: DexCompilerErrorCode, message: String): Nothing = throw FakeDexCompileFailure(
        code,
        DexCompilerFailurePhase.INPUT_VALIDATION,
        message,
    )

    private val CLASS_MAGIC = byteArrayOf(0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte())
    private val ZIP_START_SIGNATURES = setOf(0x04034b50)
    private const val MAX_ENTRY_COMPRESSION_RATIO = 100L
    private const val MAX_AGGREGATE_COMPRESSION_RATIO = 100L

    private data class EntryContent(
        val sizeBytes: Long,
        val classBytes: ByteArray?,
    )
}
