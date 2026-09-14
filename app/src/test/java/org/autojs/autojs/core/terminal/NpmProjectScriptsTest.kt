package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NpmProjectScriptsTest {

    @Test
    fun parseKeepsDeclarationOrderAndSkipsNonStringValues() {
        val scripts = NpmProjectScripts.parse(
            """
            {
              "name": "demo",
              "scripts": {
                "start": "node index.js",
                "test": "jest",
                "weird": 42,
                "build": "tsc -p ."
              }
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NpmProjectScripts.Script("start", "node index.js"),
                NpmProjectScripts.Script("test", "jest"),
                NpmProjectScripts.Script("build", "tsc -p ."),
            ),
            scripts,
        )
    }

    @Test
    fun parseReturnsEmptyWithoutScriptsTable() {
        assertTrue(NpmProjectScripts.parse("""{"name":"demo"}""").isEmpty())
        assertTrue(NpmProjectScripts.parse("""{"scripts":"oops"}""").isEmpty())
    }

    @Test
    fun parseRejectsNonObjectRoot() {
        try {
            NpmProjectScripts.parse("[1, 2]")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun readDistinguishesMissingFromEmpty() {
        val dir = Files.createTempDirectory("npm-scripts").toFile()
        try {
            assertNull(NpmProjectScripts.read(dir))
            File(dir, NpmProjectScripts.PACKAGE_JSON).writeText("""{"scripts":{"dev":"vite"}}""")
            assertEquals(listOf(NpmProjectScripts.Script("dev", "vite")), NpmProjectScripts.read(dir))
            File(dir, NpmProjectScripts.PACKAGE_JSON).writeText("not json")
            assertNull(NpmProjectScripts.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

}
