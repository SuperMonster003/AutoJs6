package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NodeBridgeModelPathPolicyTest {

    private lateinit var base: File
    private lateinit var work: File
    private lateinit var shared: File
    private var storageAccess = true

    @Before fun createTree() {
        base = Files.createTempDirectory("model-path-policy").toFile()
        work = File(base, "work").apply { mkdirs() }
        shared = File(base, "shared").apply { mkdirs() }
        File(work, "inside.png").writeText("x")
        File(shared, "Download").mkdirs()
        File(shared, "Download/photo.jpg").writeText("x")
        File(shared, "Android/data/com.example").mkdirs()
        File(shared, "Android/data/com.example/cache.bin").writeText("x")
        File(shared, "Android/obb/com.example").mkdirs()
        File(base, "elsewhere.png").writeText("x")
    }

    @After fun removeTree() {
        base.deleteRecursively()
    }

    private fun policy() = NodeBridgeModelPathPolicy(work, shared) { storageAccess }

    private val token = listOf("image", NodeBridgePermissionManifest.FILES_SHARED_READ)

    @Test fun theWorkingDirectoryIsReadableByRelativeAndAbsolutePath() {
        val relative = policy().resolveRead("inside.png", emptyList(), "image.readImage")
        assertEquals(NodeBridgeModelPathPolicy.Location.WORKING_DIRECTORY, relative.location)
        assertEquals(File(work, "inside.png").canonicalFile, relative.file)
        val absolute = policy().resolveRead(File(work, "inside.png").absolutePath, emptyList(), "image.readImage")
        assertEquals(NodeBridgeModelPathPolicy.Location.WORKING_DIRECTORY, absolute.location)
    }

    @Test fun escapesAndForeignAbsolutePathsAreDenied() {
        val escape = denied { policy().resolveRead("../elsewhere.png", token, "image.readImage") }
        assertTrue(escape, escape.contains("outside the working directory"))
        val foreign = denied { policy().resolveRead(File(base, "elsewhere.png").absolutePath, token, "mediainfo.read") }
        assertTrue(foreign, foreign.contains("mediainfo.read"))
    }

    @Test fun sharedStorageNeedsTheTokenAndTheAndroidPermission() {
        val photo = File(shared, "Download/photo.jpg").absolutePath
        val noToken = denied { policy().resolveRead(photo, listOf("image"), "image.readImage") }
        assertTrue(noToken, noToken.contains("files.shared_read"))
        storageAccess = false
        val noAccess = denied { policy().resolveRead(photo, token, "image.readImage") }
        assertTrue(noAccess, noAccess.contains("all-files access"))
        storageAccess = true
        val granted = policy().resolveRead(photo, token, "image.readImage")
        assertEquals(NodeBridgeModelPathPolicy.Location.SHARED_STORAGE, granted.location)
        assertEquals("Download/photo.jpg", policy().sharedRelativePath(granted.file))
    }

    @Test fun androidDataAndObbStayHiddenEvenWithTheToken() {
        val data = denied { policy().resolveRead(File(shared, "Android/data/com.example/cache.bin").absolutePath, token, "image.readImage") }
        assertTrue(data, data.contains("Android/data"))
        denied { policy().resolveRead(File(shared, "Android/obb").absolutePath, token, "files.list") }
        val p = policy()
        assertTrue(p.isHiddenInShared(File(shared, "Android/data").canonicalFile))
        assertTrue(p.isHiddenInShared(File(shared, "Android/obb/com.example").canonicalFile))
        assertTrue(!p.isHiddenInShared(File(shared, "Android").canonicalFile))
        assertTrue(!p.isHiddenInShared(File(shared, "Download").canonicalFile))
    }

    @Test fun writesStayInsideTheWorkingDirectory() {
        val inside = policy().resolveWrite("out/result.png", "image.saveImage")
        assertEquals(File(work, "out/result.png").absoluteFile.path, inside.file.path)
        val sharedWrite = denied { policy().resolveWrite(File(shared, "Download/out.png").absolutePath, "image.saveImage") }
        assertTrue(sharedWrite, sharedWrite.contains("only write inside the working directory"))
        denied { policy().resolveWrite("../escape.png", "image.saveImage") }
    }

    @Test fun aMissingSharedRootDeniesEveryAbsolutePathOutsideTheWorkingDirectory() {
        val noShared = NodeBridgeModelPathPolicy(work, null) { true }
        denied { noShared.resolveRead(File(shared, "Download/photo.jpg").absolutePath, token, "image.readImage") }
    }

    private fun denied(block: () -> Any?): String {
        try {
            block()
        } catch (error: SecurityException) {
            return error.message.orEmpty()
        }
        throw AssertionError("expected a SecurityException")
    }
}
