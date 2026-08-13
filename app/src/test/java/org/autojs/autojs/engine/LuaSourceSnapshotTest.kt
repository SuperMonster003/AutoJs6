package org.autojs.autojs.engine

import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.autojs.plugin.lua.runtime.api.LuaContractException
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.Reader
import java.io.RandomAccessFile
import java.nio.file.Files

class LuaSourceSnapshotTest {

    @Test
    fun fileSnapshotIsStableAfterOriginalMutationAndDeletesOnClose() {
        val directory = Files.createTempDirectory("lua-snapshot-test").toFile()
        try {
            val original = directory.resolve("main.lua")
            val first = "return 'first'".toByteArray()
            original.writeBytes(first)

            val snapshot = LuaSourceSnapshot.create(directory, LuaFileSource(original))
            original.writeText("return 'second'")

            assertEquals("main.lua", snapshot.logicalName)
            assertEquals(first.size.toLong(), snapshot.lengthBytes)
            assertEquals(LuaSha256.digest(first), snapshot.sha256)
            assertArrayEquals(first, snapshot.snapshotFile.readBytes())
            assertTrue(snapshot.snapshotFile.isFile)

            snapshot.close()
            snapshot.close()
            assertFalse(snapshot.snapshotFile.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun inMemorySourceIsEncodedAsUtf8() {
        val directory = Files.createTempDirectory("lua-memory-snapshot-test").toFile()
        try {
            val text = "return '月'"
            val source = object : LuaScriptSource("memory") {
                override val script: String = text
                override val scriptReader: Reader? = null
            }
            LuaSourceSnapshot.create(directory, source).use { snapshot ->
                assertArrayEquals(text.toByteArray(Charsets.UTF_8), snapshot.snapshotFile.readBytes())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingSourceFailureDoesNotLeaveAPartialSnapshot() {
        val directory = Files.createTempDirectory("lua-missing-snapshot-test").toFile()
        try {
            val missingSource = LuaFileSource(directory.resolve("missing.lua"))

            assertThrows(FileNotFoundException::class.java) {
                LuaSourceSnapshot.create(directory, missingSource)
            }

            assertTrue(snapshotFiles(directory).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedSourceIsRejectedAndItsPartialSnapshotIsDeleted() {
        val directory = Files.createTempDirectory("lua-oversized-snapshot-test").toFile()
        try {
            val original = directory.resolve("oversized.lua")
            RandomAccessFile(original, "rw").use {
                it.setLength(LuaRuntimeContract.MAX_SOURCE_BYTES + 1L)
            }

            assertThrows(LuaContractException::class.java) {
                LuaSourceSnapshot.create(directory, LuaFileSource(original))
            }

            assertTrue(snapshotFiles(directory).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun snapshotFiles(directory: java.io.File): List<java.io.File> =
        directory.resolve("lua-source-snapshots").listFiles().orEmpty().toList()
}
