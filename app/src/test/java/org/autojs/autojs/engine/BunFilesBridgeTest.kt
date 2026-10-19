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
    @Test fun oversizedReadsRejectInsteadOfTruncating() {
        folder.root.resolve("large").writeBytes(ByteArray(BunFilesBridge.MAX_READ_BYTES + 1))
        assertThrows(BunFilePayloadException::class.java) { call("read", "large") }
    }
}
