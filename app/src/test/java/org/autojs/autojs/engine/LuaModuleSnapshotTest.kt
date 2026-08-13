package org.autojs.autojs.engine

import org.autojs.autojs.script.LuaFileSource
import org.autojs.plugin.lua.runtime.api.LuaContractException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.nio.file.Files

class LuaModuleSnapshotTest {

    @Test
    fun absentExplicitModuleDirectoryIgnoresUnrelatedSiblingScripts() {
        val root = Files.createTempDirectory("lua-modules-absent").toFile()
        try {
            val entry = root.resolve("main.lua").apply { writeText("return 1") }
            root.resolve("unrelated.lua").writeBytes(ByteArray(LuaModuleSnapshot.MAX_MODULE_BYTES + 1))

            assertEquals(0, LuaModuleSnapshot.create(LuaFileSource(entry)).moduleCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun originDirectoryTakesPriorityAndSourcesAreFrozen() {
        val root = Files.createTempDirectory("lua-modules-origin").toFile()
        try {
            val project = root.resolve("project").apply { mkdir() }
            val entry = project.resolve("main.lua").apply { writeText("return require('helper')") }
            val modules = project.resolve("main.modules").apply { mkdir() }
            val helper = modules.resolve("helper.lua").apply { writeText("return 'before'") }
            modules.resolve("autojs.lua").writeText("error('must not shadow the built-in')")
            modules.resolve("bad-name.lua").writeText("return 'not admitted'")
            modules.resolve("nested").apply {
                mkdir()
                resolve("nested.lua").writeText("return 'not flat'")
            }
            val cache = root.resolve("cache").apply { mkdir() }
            val temporaryEntry = cache.resolve("temporary.lua").apply { writeText(entry.readText()) }
            cache.resolve("decoy.lua").writeText("return 'wrong root'")
            val source = LuaFileSource(temporaryEntry).apply { overriddenFullPath = entry.absolutePath }

            val snapshot = LuaModuleSnapshot.create(source)
            helper.writeText("return 'after'")

            assertEquals(1, snapshot.moduleCount)
            assertEquals("return 'before'", snapshot.sourceFor("helper")?.toString(Charsets.UTF_8))
            assertNull(snapshot.sourceFor("autojs"))
            assertNull(snapshot.sourceFor("bad-name"))
            assertNull(snapshot.sourceFor("decoy"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun relativeOriginDoesNotFallBackToTheTemporaryLuaFile() {
        val root = Files.createTempDirectory("lua-modules-relative-origin").toFile()
        try {
            val entry = root.resolve("temporary.lua").apply { writeText("return 1") }
            root.resolve("temporary.modules").apply { mkdir() }.resolve("helper.lua").writeText("return 2")
            val source = LuaFileSource(entry).apply { overriddenFullPath = "main.lua" }

            assertEquals(0, LuaModuleSnapshot.create(source).moduleCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun moduleCountPerFileAndAggregateQuotasFailClosed() {
        val root = Files.createTempDirectory("lua-modules-quota").toFile()
        try {
            val entryCountRoot = root.resolve("entry-count").apply { mkdir() }
            val entryCountEntry = entryCountRoot.resolve("main.lua").apply { writeText("return 1") }
            val entryCountModules = entryCountRoot.resolve("main.modules").apply { mkdir() }
            repeat(LuaModuleSnapshot.MAX_DIRECTORY_ENTRIES + 1) { index ->
                entryCountModules.resolve("ignored-$index.txt").writeText("ignored")
            }
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(entryCountEntry))
            }

            val countRoot = root.resolve("count").apply { mkdir() }
            val countEntry = countRoot.resolve("main.lua").apply { writeText("return 1") }
            val countModules = countRoot.resolve("main.modules").apply { mkdir() }
            repeat(LuaModuleSnapshot.MAX_MODULES + 1) { index ->
                countModules.resolve("module$index.lua").writeText("return $index")
            }
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(countEntry))
            }

            val fileRoot = root.resolve("file").apply { mkdir() }
            val fileEntry = fileRoot.resolve("main.lua").apply { writeText("return 1") }
            fileRoot.resolve("main.modules").apply { mkdir() }.resolve("large.lua")
                .writeBytes(ByteArray(LuaModuleSnapshot.MAX_MODULE_BYTES + 1) { 'a'.code.toByte() })
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(fileEntry))
            }

            val totalRoot = root.resolve("total").apply { mkdir() }
            val totalEntry = totalRoot.resolve("main.lua").apply { writeText("return 1") }
            val totalModules = totalRoot.resolve("main.modules").apply { mkdir() }
            repeat(5) { index ->
                totalModules.resolve("part$index.lua").writeBytes(ByteArray(60 * 1024) { 'a'.code.toByte() })
            }
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(totalEntry))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun malformedUtf8AndSymbolicLinksAreRejected() {
        val root = Files.createTempDirectory("lua-modules-illegal").toFile()
        try {
            val entry = root.resolve("main.lua").apply { writeText("return 1") }
            val modules = root.resolve("main.modules").apply { mkdir() }
            modules.resolve("broken.lua").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(entry))
            }

            modules.resolve("broken.lua").delete()
            val target = root.resolve("target.txt").apply { writeText("return 2") }
            val link = modules.resolve("linked.lua")
            try {
                Files.createSymbolicLink(link.toPath(), target.toPath())
            } catch (unsupported: Exception) {
                assumeNoException(unsupported)
            }
            assertThrows(LuaContractException::class.java) {
                LuaModuleSnapshot.create(LuaFileSource(entry))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun returnedBytesCannotMutateTheSessionSnapshot() {
        val root = Files.createTempDirectory("lua-modules-copy").toFile()
        try {
            val entry = root.resolve("main.lua").apply { writeText("return 1") }
            val expected = "return 2".toByteArray()
            root.resolve("main.modules").apply { mkdir() }.resolve("helper.lua").writeBytes(expected)
            val snapshot = LuaModuleSnapshot.create(LuaFileSource(entry))

            snapshot.sourceFor("helper")!![0] = 0

            assertArrayEquals(expected, snapshot.sourceFor("helper"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun moduleNamesAreBoundedAsciiIdentifiers() {
        assertEquals(true, LuaModuleSnapshot.isValidModuleName("_module9"))
        assertEquals(true, LuaModuleSnapshot.isValidModuleName("a".repeat(64)))
        assertEquals(false, LuaModuleSnapshot.isValidModuleName("a".repeat(65)))
        assertEquals(false, LuaModuleSnapshot.isValidModuleName("nested.module"))
        assertEquals(false, LuaModuleSnapshot.isValidModuleName("autojs"))
        assertEquals(false, LuaModuleSnapshot.isValidModuleName("模块"))
    }

    @Test
    fun fileReaderIsInjectableAndRuntimeSelectionIsExplicit() {
        val root = Files.createTempDirectory("lua-modules-reader").toFile()
        try {
            val entry = root.resolve("main.lua").apply { writeText("return 1") }
            root.resolve("main.modules").apply { mkdir() }.resolve("helper.lua").writeText("disk")
            var admittedName: String? = null
            var admittedLimit = -1

            val snapshot = LuaModuleSnapshot.create(
                LuaFileSource(entry),
                LuaModuleFileReader { _, moduleName, maximumBytes ->
                    admittedName = moduleName
                    admittedLimit = maximumBytes
                    "injected".toByteArray()
                },
            )

            assertEquals("helper", admittedName)
            assertEquals(LuaModuleSnapshot.MAX_MODULE_BYTES, admittedLimit)
            assertEquals("injected", snapshot.sourceFor("helper")?.toString(Charsets.UTF_8))
            assertSame(
                LuaModuleFileReaders.runtime("OpenJDK 64-Bit Server VM"),
                LuaModuleFileReaders.runtime("OpenJDK 64-Bit Server VM"),
            )
            assertNotSame(
                LuaModuleFileReaders.runtime("Dalvik"),
                LuaModuleFileReaders.runtime("OpenJDK 64-Bit Server VM"),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
