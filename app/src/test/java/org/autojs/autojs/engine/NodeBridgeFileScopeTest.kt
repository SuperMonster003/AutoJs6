package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class NodeBridgeFileScopeTest {

    @Test
    fun rootMustBeAnExistingDirectory() = withTempRoot { root ->
        val missing = File(root, "missing")
        assertEquals(NodeBridgeFileScope.Kind.IO, failure { NodeBridgeFileScope(missing) })
        val scope = NodeBridgeFileScope(root)
        assertEquals(root.canonicalFile, scope.rootDirectory)
        assertEquals(".", scope.relativePathOf(scope.rootDirectory))
    }

    @Test
    fun writeReadStatListRenameDeleteRoundTrip() = withTempRoot { root ->
        val scope = NodeBridgeFileScope(root)
        val written = scope.write("notes/today.txt", "hello\nworld")
        assertTrue(written.created)
        assertFalse(written.appended)
        assertEquals("notes/today.txt", written.path)
        assertEquals(11, written.bytes)

        val appended = scope.write("notes/today.txt", "!", append = true)
        assertTrue(appended.appended)
        assertFalse(appended.created)

        val read = scope.read("notes/today.txt")
        assertEquals("hello\nworld!", read.content)
        assertEquals(NodeBridgeFileScope.ENCODING_UTF8, read.encoding)
        assertEquals(12, read.bytes)
        assertFalse(read.truncated)

        val stat = scope.stat("notes/today.txt")
        assertTrue(stat.exists && stat.isFile && !stat.isDirectory)
        assertEquals(12L, stat.size)
        assertEquals("today.txt", stat.name)
        assertFalse(scope.stat("nowhere").exists)

        assertTrue(scope.mkdir("notes/sub").created)
        assertFalse(scope.mkdir("notes/sub").created)
        scope.write("notes/sub/deep.txt", "x")

        val shallow = scope.list("notes")
        assertEquals(listOf("notes/sub", "notes/today.txt"), shallow.entries.map { it.path })
        assertEquals(listOf(true, false), shallow.entries.map { it.isDirectory })
        val deep = scope.list(".", recursive = true)
        assertEquals(listOf("notes", "notes/sub", "notes/sub/deep.txt", "notes/today.txt"), deep.entries.map { it.path })
        assertFalse(deep.truncated)
        val limited = scope.list(".", recursive = true, maxEntries = 2)
        assertEquals(2, limited.entries.size)
        assertTrue(limited.truncated)

        val renamed = scope.rename("notes/today.txt", "notes/yesterday.txt")
        assertEquals("notes/yesterday.txt", renamed.to)
        assertFalse(renamed.overwritten)
        assertFalse(File(root, "notes/today.txt").exists())
        assertEquals(NodeBridgeFileScope.Kind.EXISTS, failure { scope.rename("notes/sub/deep.txt", "notes/yesterday.txt") })
        assertTrue(scope.rename("notes/sub/deep.txt", "notes/yesterday.txt", overwrite = true).overwritten)
        assertEquals("x", scope.read("notes/yesterday.txt").content)

        assertEquals(1, scope.delete("notes/yesterday.txt").deleted)
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { scope.delete("notes/yesterday.txt") })
        scope.write("notes/sub/a.txt", "a")
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { scope.delete("notes") })
        assertEquals(3, scope.delete("notes", recursive = true).deleted)
        assertFalse(File(root, "notes").exists())
    }

    @Test
    fun base64AndLimitsAreHonoured() = withTempRoot { root ->
        val scope = NodeBridgeFileScope(root)
        val binary = byteArrayOf(0, 1, 2, 0xFF.toByte(), 0xFE.toByte())
        scope.write("bin.dat", Base64.getEncoder().encodeToString(binary), encoding = "base64")
        assertTrue(binary.contentEquals(File(root, "bin.dat").readBytes()))
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { scope.read("bin.dat") })
        val back = scope.read("bin.dat", encoding = "BASE64")
        assertEquals(Base64.getEncoder().encodeToString(binary), back.content)
        assertEquals(NodeBridgeFileScope.ENCODING_BASE64, back.encoding)

        scope.write("multi.txt", "你好世界")
        val cut = scope.read("multi.txt", maxBytes = 4)
        assertEquals("你", cut.content)
        assertTrue(cut.truncated)
        assertEquals(12L, cut.totalBytes)

        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.write("x.dat", "***", encoding = "base64") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.read("multi.txt", encoding = "latin1") })
        assertEquals(NodeBridgeFileScope.Kind.LIMIT, failure { scope.write("big.txt", "a".repeat(NodeBridgeFileScope.MAX_WRITE_BYTES + 1)) })
        assertEquals(NodeBridgeFileScope.Kind.EXISTS, failure { scope.write("multi.txt", "again", overwrite = false) })
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { scope.write("no/parent.txt", "x", createDirs = false) })
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { scope.list("multi.txt") })
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { scope.read("ghost.txt") })
    }

    @Test
    fun pathPolicyRejectsEscapesAndMalformedPaths() = withTempRoot { root ->
        val scope = NodeBridgeFileScope(root)
        scope.write("ok.txt", "ok")
        listOf(
            "../outside.txt",
            "a/../../outside.txt",
            "/etc/passwd",
            "C:relative.txt",
            "dir\\entry.txt",
            "entry\u0085.txt",
            "a//b",
            "./a/./b",
            "",
        ).forEach { path ->
            assertEquals("path $path", NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.stat(path) })
        }
        assertEquals("ok.txt", scope.stat("./ok.txt").path)
        assertEquals("ok.txt", scope.stat("ok.txt/").path)
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.delete(".") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.write("./", "x") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.rename("ok.txt", "/tmp/x") })
        scope.mkdir("dir")
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { scope.rename("dir", "dir/inner") })
    }

    @Test
    fun symbolicLinksCannotLeadOutside() = withTempRoot { root ->
        val outside = Files.createTempDirectory("bridge-files-outside-").toFile()
        try {
            File(outside, "secret.txt").writeText("secret")
            val link = File(root, "link")
            val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
            if (!created) {
                // Symbolic links need privileges on some Windows setups; the containment check is exercised elsewhere.
                return@withTempRoot
            }
            val scope = NodeBridgeFileScope(root)
            assertEquals(NodeBridgeFileScope.Kind.ESCAPE, failure { scope.read("link/secret.txt") })
            assertEquals(NodeBridgeFileScope.Kind.ESCAPE, failure { scope.write("link/new.txt", "x") })
            assertEquals(NodeBridgeFileScope.Kind.ESCAPE, failure { scope.list("link") })
            val listed = scope.list(".", recursive = true)
            assertTrue(listed.entries.none { it.path.startsWith("link/") })
        } finally {
            outside.deleteRecursively()
        }
    }

    private fun failure(block: () -> Any?): NodeBridgeFileScope.Kind {
        try {
            block()
        } catch (error: NodeBridgeFileScopeException) {
            return error.kind
        }
        fail("expected a NodeBridgeFileScopeException")
        throw AssertionError()
    }

    private fun withTempRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("bridge-files-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
