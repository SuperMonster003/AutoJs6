package org.autojs.autojs.engine

import android.os.Build
import android.system.Os
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real Android filesystem coverage, including APIs absent from Android 24 / 25. */
class NodeBridgeFileScopeDeviceTest {
    @Test fun renameBinaryReadAndRecursiveDeletePreserveSymbolicLinkTargets() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.canonicalFile
        val base = File(cache, "mcp-p34-scope-${UUID.randomUUID()}").apply { mkdirs() }
        val root = File(base, "root").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        val link = File(root, "nested/link")
        try {
            val scope = NodeBridgeFileScope(root, legacyFileApi = Build.VERSION.SDK_INT < 26)
            scope.write("first.txt", "first")
            scope.write("second.txt", "second")
            try { scope.rename("first.txt", "second.txt"); fail("overwrite=false must preserve the destination") }
            catch (error: NodeBridgeFileScopeException) { assertEquals(NodeBridgeFileScope.Kind.EXISTS, error.kind) }
            assertEquals("second", scope.read("second.txt").content)
            scope.rename("first.txt", "second.txt", overwrite = true)
            assertEquals("first", scope.read("second.txt").content)
            File(root, "large.bin").writeBytes(ByteArray(1024 * 1024 + 1) { 0xff.toByte() })
            val read = scope.read("large.bin", encoding = "base64", maxBytes = 1024 * 1024)
            assertEquals(1024 * 1024, Base64.decode(read.content, Base64.DEFAULT).size)
            assertTrue(read.truncated)
            scope.mkdir("nested")
            File(outside, "keep.txt").writeText("keep")
            Os.symlink(outside.absolutePath, link.absolutePath)
            try { scope.read("nested/link/keep.txt"); fail("the link must not escape the workspace") }
            catch (error: NodeBridgeFileScopeException) { assertEquals(NodeBridgeFileScope.Kind.ESCAPE, error.kind) }
            assertEquals(1, scope.list("nested", recursive = true).entries.size)
            assertEquals(2, scope.delete("nested", recursive = true).deleted)
            assertEquals("keep", File(outside, "keep.txt").readText())
        } finally {
            // Unlink the only test symlink before walking this unique temporary directory.
            link.delete()
            check(base.canonicalPath.startsWith(cache.path + File.separator))
            base.deleteRecursively()
        }
    }
}
