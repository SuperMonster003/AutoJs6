package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompileRequest
import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import org.autojs.plugin.dexcompiler.api.DexCompilerInputFormat
import org.autojs.plugin.dexcompiler.api.DexCompilerMode
import org.autojs.plugin.dexcompiler.api.DexCompilerOutputFormat
import org.autojs.plugin.dexcompiler.api.DexRequestId
import org.autojs.plugin.dexcompiler.api.DexRuntimeLibraryModel
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object FakeDexTestFixtures {
    private const val DOS_EPOCH_MILLIS = 315_532_800_000L

    fun programJar(
        entryName: String = "org/autojs/fixture/Program.class",
        classBytes: ByteArray = byteArrayOf(
            0xca.toByte(),
            0xfe.toByte(),
            0xba.toByte(),
            0xbe.toByte(),
            0,
            0,
            0,
            52,
        ),
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            val crc = CRC32().apply { update(classBytes) }
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = classBytes.size.toLong()
                compressedSize = classBytes.size.toLong()
                this.crc = crc.value
                time = DOS_EPOCH_MILLIS
                extra = byteArrayOf()
            }
            zip.putNextEntry(entry)
            zip.write(classBytes)
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    fun compressedProgramJar(classSizeBytes: Int = 128 * 1024): ByteArray {
        require(classSizeBytes >= 4)
        val classBytes = ByteArray(classSizeBytes).apply {
            this[0] = 0xca.toByte()
            this[1] = 0xfe.toByte()
            this[2] = 0xba.toByte()
            this[3] = 0xbe.toByte()
        }
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                val entry = ZipEntry("org/autojs/fixture/Compressed.class").apply {
                    time = DOS_EPOCH_MILLIS
                }
                zip.putNextEntry(entry)
                zip.write(classBytes)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }

    fun entryRatioBoundaryProgramJar(): ByteArray {
        val classBytes = compressionBoundaryClassBytes()
        val padding = ByteArray(4 * 1024).also { Random(0xA6D8L).nextBytes(it) }
        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("org/autojs/fixture/Boundary.class").apply { time = DOS_EPOCH_MILLIS })
                zip.write(classBytes)
                zip.closeEntry()

                val crc = CRC32().apply { update(padding) }
                zip.putNextEntry(ZipEntry("org/autojs/fixture/padding.bin").apply {
                    method = ZipEntry.STORED
                    size = padding.size.toLong()
                    compressedSize = padding.size.toLong()
                    this.crc = crc.value
                    time = DOS_EPOCH_MILLIS
                    extra = byteArrayOf()
                })
                zip.write(padding)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }

    fun canonicalCollisionJar(): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("org/autojs/fixture/").apply { time = DOS_EPOCH_MILLIS })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("org/autojs/fixture").apply { time = DOS_EPOCH_MILLIS })
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    fun request(
        program: ByteArray,
        programSizeBytes: Long = program.size.toLong(),
        maxOutputBytes: Long = FakeDexCompilerFixture.MAX_OUTPUT_BYTES,
    ): DexCompileRequest = DexCompileRequest(
        requestId = DexRequestId.fromUuid(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        protocolVersion = FakeDexCompilerFixture.protocolVersion,
        inputFormat = DexCompilerInputFormat.JAR,
        outputFormat = DexCompilerOutputFormat.DEX_ZIP,
        mode = DexCompilerMode.RELEASE,
        minApi = 24,
        programSizeBytes = programSizeBytes,
        programSha256 = FakeDexHashes.sha256(program),
        maxOutputBytes = maxOutputBytes,
        runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
        expectedRuntimeLibraryFingerprint = FakeDexCompilerFixture.runtimeLibraryFingerprint,
        canonicalizationPolicyVersion = DexCompilerContract.CANONICALIZATION_POLICY_VERSION,
        diagnosticByteLimit = DexCompilerContract.MAX_DIAGNOSTIC_BYTES,
        timeoutMillis = DexCompilerContract.DEFAULT_TIMEOUT_MILLIS,
    )

    private fun compressionBoundaryClassBytes(): ByteArray {
        val size = 128 * 1024
        for (noiseBytes in 512..4_096 step 64) {
            val candidate = ByteArray(size)
            Random(0xD8C0L).nextBytes(candidate, 4, noiseBytes)
            candidate[0] = 0xca.toByte()
            candidate[1] = 0xfe.toByte()
            candidate[2] = 0xba.toByte()
            candidate[3] = 0xbe.toByte()
            val compressedSize = rawDeflatedSize(candidate)
            if (candidate.size > compressedSize * 100L && candidate.size <= compressedSize * 200L) {
                return candidate
            }
        }
        error("Unable to create the deterministic 100:1 to 200:1 compression fixture")
    }

    private fun Random.nextBytes(target: ByteArray, offset: Int, length: Int) {
        val noise = ByteArray(length).also { nextBytes(it) }
        noise.copyInto(target, destinationOffset = offset)
    }

    private fun rawDeflatedSize(bytes: ByteArray): Int {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                check(count > 0) { "Deflater made no progress" }
                total += count
            }
            total
        } finally {
            deflater.end()
        }
    }
}
