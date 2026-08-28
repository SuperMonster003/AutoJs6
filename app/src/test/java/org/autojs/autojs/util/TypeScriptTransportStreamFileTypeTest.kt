package org.autojs.autojs.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TypeScriptTransportStreamFileTypeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun ordinaryTsSourceDefaultsToExecutableTypeScriptWithoutSizeHeuristics() {
        val file = temporaryFolder.newFile("main.TS").apply {
            writeText("// leading comment\nconst answer: number = 42\n")
        }

        val type = FileUtils.TYPE.determineBy(file)

        assertEquals(FileUtils.TYPE.TYPESCRIPT, type)
        assertTrue(type.isExecutable())
        assertTrue(type.isTextEditable())
        assertFalse(type.isVideoPlayable())
        assertEquals("T", type.icon.text)
    }

    @Test
    fun declarationTsAlwaysWinsEvenWhenContentLooksLikeTransportStream() {
        val file = temporaryFolder.newFile("library.d.ts")
        writeTransportPackets(file, packetSize = 188, syncOffset = 0)

        val type = FileUtils.TYPE.determineBy(file)

        assertEquals(FileUtils.TYPE.TYPESCRIPT_DECLARATION, type)
        assertFalse(type.isExecutable())
        assertTrue(type.isTextEditable())
    }

    @Test
    fun fourValid188BytePacketsIdentifyMpegTransportStream() {
        val file = temporaryFolder.newFile("broadcast.ts")
        writeTransportPackets(file, packetSize = 188, syncOffset = 0)

        val type = FileUtils.TYPE.determineBy(file)

        assertEquals(FileUtils.TYPE.MPEG2_TS, type)
        assertTrue(type.isVideoPlayable())
        assertFalse(type.isExecutable())
    }

    @Test
    fun m2tsStyle192ByteCadenceIsRecognizedFromFourByteSyncOffset() {
        val file = temporaryFolder.newFile("camera.ts")
        writeTransportPackets(file, packetSize = 192, syncOffset = 4)

        assertEquals(FileUtils.TYPE.MPEG2_TS, FileUtils.TYPE.determineBy(file))
    }

    @Test
    fun fecProtected204ByteCadenceIsRecognized() {
        val file = temporaryFolder.newFile("satellite.ts")
        writeTransportPackets(file, packetSize = 204, syncOffset = 0)

        assertEquals(FileUtils.TYPE.MPEG2_TS, FileUtils.TYPE.determineBy(file))
    }

    @Test
    fun oneLeadingSyncByteDoesNotMisclassifySourceAsVideo() {
        val file = temporaryFolder.newFile("not-video.ts")
        val bytes = ByteArray(4096) { 0x20 }
        bytes[0] = 0x47
        bytes[3] = 0x10
        file.writeBytes(bytes)

        assertEquals(FileUtils.TYPE.TYPESCRIPT, FileUtils.TYPE.determineBy(file))
    }

    @Test
    fun mtsUsesTheSameContentAwareDecisionAndDeclarationsRemainNonExecutable() {
        val source = temporaryFolder.newFile("module.mts").apply {
            writeText("export const value: number = 1\n")
        }
        val video = temporaryFolder.newFile("camera.mts")
        writeTransportPackets(video, packetSize = 188, syncOffset = 0)
        val declaration = temporaryFolder.newFile("module.d.mts")

        assertEquals(FileUtils.TYPE.MODULE_TYPESCRIPT, FileUtils.TYPE.determineBy(source))
        assertEquals(FileUtils.TYPE.MTS, FileUtils.TYPE.determineBy(video))
        assertEquals(FileUtils.TYPE.TYPESCRIPT_MODULE_DECLARATION, FileUtils.TYPE.determineBy(declaration))
        assertFalse(FileUtils.TYPE.determineBy(declaration).isExecutable())
    }

    @Test
    fun pluginBackedSourceTypesHaveDistinctExecutableIcons() {
        assertEquals("P", FileUtils.TYPE.PYTHON.icon.text)
        assertEquals("J", FileUtils.TYPE.JAVA.icon.text)
        assertEquals("K", FileUtils.TYPE.KOTLIN.icon.text)
        assertEquals("T", FileUtils.TYPE.REACT_TYPESCRIPT.icon.text)
        assertTrue(FileUtils.TYPE.PYTHON.isExecutable())
        assertTrue(FileUtils.TYPE.JAVA.isExecutable())
        assertTrue(FileUtils.TYPE.KOTLIN.isExecutable())
        assertTrue(FileUtils.TYPE.REACT_TYPESCRIPT.isExecutable())
    }

    private fun writeTransportPackets(file: File, packetSize: Int, syncOffset: Int) {
        val bytes = ByteArray(packetSize * 5) { 0x55 }
        repeat(5) { packetIndex ->
            val offset = packetIndex * packetSize + syncOffset
            bytes[offset] = 0x47
            bytes[offset + 1] = 0x40
            bytes[offset + 2] = packetIndex.toByte()
            bytes[offset + 3] = 0x10
        }
        file.writeBytes(bytes)
    }
}
