package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class FakeDexCompilerEngineTest {
    private val engine = FakeDexCompilerEngine()

    @Test
    fun normalScenarioProducesOnlyPinnedClassesDex() {
        val program = FakeDexTestFixtures.programJar()
        val artifact = engine.compile(
            FakeDexTestFixtures.request(program),
            ByteArrayInputStream(program),
            FakeDexCompilerScenario.NORMAL,
        )

        ZipInputStream(ByteArrayInputStream(artifact.bytes())).use { zip ->
            val entry = zip.nextEntry
            assertEquals(DeterministicDexZip.ENTRY_NAME, entry.name)
            assertArrayEquals(EmbeddedDexFixture.bytes(), zip.readBytes())
            assertNull(zip.nextEntry)
        }
        assertEquals(1, artifact.dexEntryCount)
        assertEquals(artifact.outputSizeBytes, artifact.bytes().size.toLong())
        assertEquals(artifact.outputSha256, FakeDexHashes.sha256(artifact.bytes()))
    }

    @Test
    fun normalOutputIsDeterministic() {
        val program = FakeDexTestFixtures.programJar()
        val request = FakeDexTestFixtures.request(program)

        val first = engine.compile(request, ByteArrayInputStream(program), FakeDexCompilerScenario.NORMAL)
        val second = engine.compile(request, ByteArrayInputStream(program), FakeDexCompilerScenario.NORMAL)

        assertArrayEquals(first.bytes(), second.bytes())
        assertEquals(first.outputSha256, second.outputSha256)
    }

    @Test
    fun invalidOutputScenarioReturnsWellFormedZipWithInvalidDex() {
        val program = FakeDexTestFixtures.programJar()
        val artifact = engine.compile(
            FakeDexTestFixtures.request(program),
            ByteArrayInputStream(program),
            FakeDexCompilerScenario.INVALID_OUTPUT,
        )

        ZipInputStream(ByteArrayInputStream(artifact.bytes())).use { zip ->
            assertEquals(DeterministicDexZip.ENTRY_NAME, zip.nextEntry.name)
            val invalidDex = zip.readBytes()
            assertFalse(invalidDex.contentEquals(EmbeddedDexFixture.bytes()))
            assertFalse(invalidDex.copyOfRange(0, 3).contentEquals("dex".toByteArray(Charsets.US_ASCII)))
        }
    }

    @Test
    fun rejectsProgramSizeMismatch() {
        val program = FakeDexTestFixtures.programJar()
        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program, programSizeBytes = program.size + 1L),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun rejectsProgramHashMismatch() {
        val program = FakeDexTestFixtures.programJar()
        val differentProgram = program.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(differentProgram),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_REQUEST, failure.code)
    }

    @Test
    fun rejectsJarWithoutProgramClasses() {
        val program = FakeDexTestFixtures.programJar(
            entryName = "META-INF/MANIFEST.MF",
            classBytes = "Manifest-Version: 1.0\n".toByteArray(Charsets.US_ASCII),
        )
        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.NO_PROGRAM_CLASSES, failure.code)
    }

    @Test
    fun rejectsNonCanonicalAndUnsafeEntryNames() {
        listOf(
            "../Program.class",
            "org//autojs/Program.class",
            "org/./autojs/Program.class",
            "C:/Program.class",
            "org/autojs/Program.class\u0000tail",
        ).forEach { entryName ->
            val program = FakeDexTestFixtures.programJar(entryName = entryName)
            val failure = assertThrows(FakeDexCompileFailure::class.java) {
                engine.compile(
                    FakeDexTestFixtures.request(program),
                    ByteArrayInputStream(program),
                    FakeDexCompilerScenario.NORMAL,
                )
            }
            assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
        }
    }

    @Test
    fun rejectsCanonicalEntryCollision() {
        val program = FakeDexTestFixtures.canonicalCollisionJar()
        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun rejectsInvalidZipStartSignature() {
        listOf(
            byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 8, 0, 0, 0, 0),
            byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 7, 8, 0, 0, 0, 0),
        ).forEach { program ->
            val failure = assertThrows(FakeDexCompileFailure::class.java) {
                engine.compile(
                    FakeDexTestFixtures.request(program),
                    ByteArrayInputStream(program),
                    FakeDexCompilerScenario.NORMAL,
                )
            }
            assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
        }
    }

    @Test
    fun rejectsExcessiveCompressionRatio() {
        val program = FakeDexTestFixtures.compressedProgramJar()
        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun rejectsPerEntryRatioBetweenOneHundredAndLegacyTwoHundredLimit() {
        val program = FakeDexTestFixtures.entryRatioBoundaryProgramJar()
        val entrySizes = mutableListOf<Pair<Long, Long>>()
        ZipInputStream(ByteArrayInputStream(program)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val uncompressedSize = zip.readBytes().size.toLong()
                entrySizes += uncompressedSize to entry.compressedSize
            }
        }
        val (classUncompressed, classCompressed) = entrySizes.first()
        assertTrue(classUncompressed > classCompressed * 100L)
        assertTrue(classUncompressed <= classCompressed * 200L)
        assertTrue(entrySizes.sumOf { it.first } <= entrySizes.sumOf { it.second } * 100L)

        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun rejectsJarTruncatedBeforeCentralDirectory() {
        val program = FakeDexTestFixtures.programJar()
        val eocdOffset = program.size - 22
        val centralOffset = program.leU32(eocdOffset + 16).toInt()
        val localRecordsOnly = program.copyOf(centralOffset)

        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(localRecordsOnly),
                ByteArrayInputStream(localRecordsOnly),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun rejectsLocalAndCentralEntryNameMismatch() {
        val program = FakeDexTestFixtures.programJar().copyOf()
        val eocdOffset = program.size - 22
        val centralOffset = program.leU32(eocdOffset + 16).toInt()
        val centralNameOffset = centralOffset + 46
        program[centralNameOffset] = (program[centralNameOffset].toInt() xor 1).toByte()

        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun rejectsBytesAfterEndOfCentralDirectory() {
        val program = FakeDexTestFixtures.programJar() + byteArrayOf(0x41, 0x4a, 0x36)

        val failure = assertThrows(FakeDexCompileFailure::class.java) {
            engine.compile(
                FakeDexTestFixtures.request(program),
                ByteArrayInputStream(program),
                FakeDexCompilerScenario.NORMAL,
            )
        }
        assertEquals(DexCompilerErrorCode.INVALID_ARCHIVE, failure.code)
    }

    @Test
    fun artifactBytesAreDefensivelyCopied() {
        val program = FakeDexTestFixtures.programJar()
        val artifact = engine.compile(
            FakeDexTestFixtures.request(program),
            ByteArrayInputStream(program),
            FakeDexCompilerScenario.NORMAL,
        )
        val first = artifact.bytes()
        first.fill(0)

        assertTrue(artifact.bytes().any { it.toInt() != 0 })
    }

    private fun ByteArray.leU32(offset: Int): Long =
        (this[offset].toLong() and 0xffL) or
            ((this[offset + 1].toLong() and 0xffL) shl 8) or
            ((this[offset + 2].toLong() and 0xffL) shl 16) or
            ((this[offset + 3].toLong() and 0xffL) shl 24)
}
