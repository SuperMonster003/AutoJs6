package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Base64

class BunFilesBridgeTest {
    @get:Rule val folder = TemporaryFolder()
    private fun call(method: String, vararg args: Any?) = BunFilesBridge(folder.root).invoke(method, args.toList())
    private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test fun filesAreDurableAndDirectorySemanticsMatchThePublicNodeFacade() {
        assertEquals(true, call("ensureDir", "parent/child/file.txt"))
        assertFalse(folder.root.resolve("parent/child/file.txt").exists())
        call("write", "parent/child/file.txt", encoded("first"))
        call("append", "parent/child/file.txt", encoded(" second"))
        assertEquals("first second", folder.root.resolve("parent/child/file.txt").readText())
        assertEquals(encoded("first second"), call("read", "parent/child/file.txt"))
        assertEquals(true, call("copy", "parent/child/file.txt", "copied/deep/copy.txt"))
        assertEquals(true, call("move", "copied/deep/copy.txt", "copied/deep/moved.txt"))
        assertEquals(false, call("move", "copied/deep/moved.txt", "missing/new.txt"))
        assertEquals(false, call("copy", "missing", "copy.txt"))
        assertThrows(IOException::class.java) { call("remove", "parent") }
        assertEquals(true, call("remove", "copied/deep/moved.txt"))
        assertEquals(false, call("remove", "copied/deep/moved.txt"))
        assertEquals(true, call("ensureDir", "empty/"))
        assertEquals(true, call("isDir", "empty"))
        assertEquals(true, call("isFile", "parent/child/file.txt"))
    }
    @Test fun nullishPathsAndMalformedRawCallsHaveExplicitResults() {
        assertNull(call("path", null))
        assertEquals("", call("read", null))
        assertEquals(false, call("exists", null))
        assertEquals("[]", call("listDir", null).toString())
        assertThrows(IllegalArgumentException::class.java) { call("write", null, "") }
        assertThrows(IllegalArgumentException::class.java) { call("read", 3) }
        assertThrows(IllegalArgumentException::class.java) { call("open", "x") }
        assertThrows(IllegalArgumentException::class.java) { call("read", "x", "extra") }
        assertThrows(IllegalArgumentException::class.java) { call("read", "bad\u0000path") }
    }
    @Test fun prototypeMembersFollowTheRhinoFilesObject() {
        val bridge = BunFilesBridge(folder.root, assets = { name -> require(name == "init.js"); "asset".byteInputStream() }, simplify = { "simple:$it" })
        fun bun(method: String, vararg args: Any?) = bridge.invoke(method, args.toList())
        assertEquals(folder.root.path, bun("cwd"))
        assertThrows(IllegalArgumentException::class.java) { bun("cwd", "x") }
        assertEquals(true, bun("create", "a.txt"))
        assertEquals(true, bun("create", "a.txt"))
        assertEquals(true, bun("create", "dir/"))
        assertEquals(false, bun("create", "dir/"))
        assertEquals(true, bun("createIfNotExists", "deep/er/b.txt"))
        assertEquals(false, bun("createIfNotExists", "deep/er/b.txt"))
        assertEquals(false, bun("createWithDirs", "made/"))
        assertTrue(folder.root.resolve("made").isDirectory)
        assertEquals(true, bun("createWithDirs", "w/x/c.txt"))
        bun("writeBytes", "bytes.bin", Base64.getEncoder().encodeToString(byteArrayOf(0, -1, 127)))
        bun("appendBytes", "bytes.bin", Base64.getEncoder().encodeToString(byteArrayOf(5)))
        bun("appendBytes", "fresh.bin", Base64.getEncoder().encodeToString(byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(0, -1, 127, 5), Base64.getDecoder().decode(bun("readBytes", "bytes.bin") as String))
        assertArrayEquals(byteArrayOf(9), folder.root.resolve("fresh.bin").readBytes())
        assertThrows(IOException::class.java) { bun("writeBytes", "missing/x.bin", "") }
        assertThrows(IllegalArgumentException::class.java) { bun("readBytes", null) }
        assertThrows(IllegalArgumentException::class.java) { bun("writeBytes", null, "") }
        assertEquals(true, bun("rename", "a.txt", "renamed.txt"))
        assertTrue(folder.root.resolve("renamed.txt").isFile)
        assertEquals(true, bun("renameWithoutExtension", "renamed.txt", "plain"))
        assertTrue(folder.root.resolve("plain.txt").isFile)
        assertEquals(false, bun("rename", "gone.txt", "x"))
        assertThrows(IllegalArgumentException::class.java) { bun("rename", "plain.txt", null) }
        assertEquals(true, bun("isEmptyDir", "dir"))
        assertEquals(false, bun("isEmptyDir", "deep"))
        assertEquals(false, bun("isEmptyDir", "plain.txt"))
        assertEquals(false, bun("isEmptyDir", null))
        assertEquals(true, bun("removeDir", "deep"))
        assertFalse(folder.root.resolve("deep").exists())
        assertEquals(false, bun("removeDir", "deep"))
        assertEquals(false, bun("removeDir", null))
        assertEquals(Base64.getEncoder().encodeToString("asset".toByteArray()), bun("readAssets", "init.js"))
        assertThrows(IllegalArgumentException::class.java) { bun("readAssets", null) }
        assertEquals("simple:/sdcard/x", bun("getSimplifiedPath", "/sdcard/x"))
        assertThrows(IllegalArgumentException::class.java) { bun("getSimplifiedPath", null) }
    }
    @Test fun oversizedReadsRejectInsteadOfTruncating() {
        folder.root.resolve("large").writeBytes(ByteArray(BunFilesBridge.MAX_READ_BYTES + 1))
        assertThrows(BunFilePayloadException::class.java) { call("read", "large") }
        assertThrows(BunFilePayloadException::class.java) { call("readBytes", "large") }
    }
}
