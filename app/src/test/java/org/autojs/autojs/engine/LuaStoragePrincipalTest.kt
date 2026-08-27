package org.autojs.autojs.engine

import org.autojs.autojs.script.LuaFileSource
import org.autojs.autojs.script.LuaScriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.nio.file.Files

class LuaStoragePrincipalTest {

    @Test
    fun canonicalFileIdentityIsStableAcrossContentAndAliases() {
        val directory = Files.createTempDirectory("lua-storage-principal").toFile()
        try {
            val entry = directory.resolve("main.lua").apply { writeText("return 1") }
            val first = checkNotNull(LuaStoragePrincipal.fromSource(LuaFileSource(entry)))
            entry.writeText("return 2")
            val second = checkNotNull(
                LuaStoragePrincipal.fromSource(LuaFileSource(directory.resolve("child/../main.lua"))),
            )

            assertEquals(first, second)
            assertTrue(first.digest.matches(Regex("[0-9a-f]{64}")))
            assertTrue(first.toString().contains(first.digest))
            assertTrue(!first.toString().contains(entry.path))

            val other = directory.resolve("other.lua").apply { writeText("return 2") }
            assertNotEquals(first, LuaStoragePrincipal.fromSource(LuaFileSource(other)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun semanticFileIdentityWinsOverAnEditorPayloadPath() {
        val directory = Files.createTempDirectory("lua-storage-semantic").toFile()
        try {
            val semantic = directory.resolve("project.lua").apply { writeText("return 1") }
            val payload = directory.resolve("cached.lua").apply { writeText("return 2") }
            val source = LuaFileSource(payload).apply { overriddenFullPath = semantic.absolutePath }

            assertEquals(
                LuaStoragePrincipal.fromSource(LuaFileSource(semantic)),
                LuaStoragePrincipal.fromSource(source),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun inMemoryAndMissingFileSourcesDoNotReceiveStoragePrincipals() {
        val memory = object : LuaScriptSource("memory") {
            override val script: String = "return 1"
            override val scriptReader: Reader? = null
        }

        assertNull(LuaStoragePrincipal.fromSource(memory))
        assertNull(LuaStoragePrincipal.fromSource(LuaFileSource("missing-${System.nanoTime()}.lua")))
    }
}
