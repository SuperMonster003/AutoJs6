package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class NodeBridgeDocsCatalogTest {

    private val files = linkedMapOf(
        "index.html" to "<html>index</html>".toByteArray(),
        "app.html" to "<html>app 应用</html>".toByteArray(),
        "assets/style.css" to "body{}".toByteArray(),
        "assets/img/logo.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        "assets/vendor/lib.js" to "var x;".toByteArray(),
        "../escape.html" to "no".toByteArray(),
        "assets//broken.css" to "no".toByteArray(),
    )

    private var installed: FakeSource? = FakeSource(files)

    private val catalog = NodeBridgeDocsCatalog(source = { installed })

    private class FakeSource(private val files: Map<String, ByteArray>) : NodeBridgeDocsCatalog.Source {
        override val packageName = "io.github.supermonster003.autojs6.plugin.offlinedocs"
        override val contentVersion = "6.8.2"
        override val contractVersion = 1
        override val entryPoint = "index.html"
        override val fileCount = 5L
        override val totalBytes = 64L
        override val assetPaths: Set<String> = files.keys
        val opened = mutableListOf<String>()
        override fun open(relativePath: String): InputStream? {
            opened += relativePath
            return files[relativePath]?.let { ByteArrayInputStream(it) }
        }
    }

    @Test
    fun rootListingDerivesDirectoriesFromTheInventoryAndSkipsInvalidPaths() {
        val root = catalog.list()
        assertTrue(root.available)
        assertFalse(root.truncated)
        assertEquals(".", root.path)
        assertEquals(listOf("assets", "app.html", "index.html"), root.entries.map { it.path })
        assertEquals(listOf(true, false, false), root.entries.map { it.isDirectory })
        assertEquals("io.github.supermonster003.autojs6.plugin.offlinedocs", root.source!!.packageName)
        val assets = catalog.list("assets")
        assertEquals(listOf("assets/img", "assets/vendor", "assets/style.css"), assets.entries.map { it.path })
        assertEquals(listOf("img", "vendor", "style.css"), assets.entries.map { it.name })
    }

    @Test
    fun recursiveListingWalksDepthFirstAndHonoursTheLimit() {
        val deep = catalog.list(".", recursive = true)
        assertEquals(
            listOf("assets", "assets/img", "assets/img/logo.png", "assets/vendor", "assets/vendor/lib.js", "assets/style.css", "app.html", "index.html"),
            deep.entries.map { it.path },
        )
        val limited = catalog.list("./assets/", recursive = true, maxEntries = 2)
        assertEquals(listOf("assets/img", "assets/img/logo.png"), limited.entries.map { it.path })
        assertTrue(limited.truncated)
        assertEquals("assets", limited.path)
    }

    @Test
    fun listingRejectsFilesMissingPathsAndEscapes() {
        assertKind(NodeBridgeFileScope.Kind.WRONG_TYPE) { catalog.list("index.html") }
        assertKind(NodeBridgeFileScope.Kind.NOT_FOUND) { catalog.list("missing") }
        assertKind(NodeBridgeFileScope.Kind.WRONG_TYPE) { catalog.list("assets/img/logo.png/") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.list("../docs") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.list("/index.html") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.list("assets\\img") }
    }

    @Test
    fun withoutAnEligiblePluginTheRootIsUnavailableAndEverythingElseIsMissing() {
        installed = null
        val root = catalog.list()
        assertFalse(root.available)
        assertNull(root.source)
        assertTrue(root.entries.isEmpty())
        assertKind(NodeBridgeFileScope.Kind.NOT_FOUND) { catalog.list("assets") }
        assertKind(NodeBridgeFileScope.Kind.NOT_FOUND) { catalog.read("index.html") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.read("../secret") }
    }

    @Test
    fun readsReturnInventoryFilesOnlyWithTruncationAndBase64() {
        val text = catalog.read("./app.html")
        assertEquals("app.html", text.path)
        assertEquals("<html>app 应用</html>", text.content)
        assertEquals("utf-8", text.encoding)
        assertEquals("<html>app 应用</html>".toByteArray().size, text.bytes)
        assertFalse(text.truncated)
        val head = catalog.read("index.html", maxBytes = 6)
        assertEquals("<html>", head.content)
        assertTrue(head.truncated)
        assertEquals(6, head.bytes)
        val blob = catalog.read("assets/img/logo.png", encoding = "base64")
        assertEquals("iVBORw==", blob.content)
        assertEquals("base64", blob.encoding)
        assertEquals(listOf("app.html", "index.html", "assets/img/logo.png"), installed!!.opened)
    }

    @Test
    fun readRejectsDirectoriesUnknownFilesAndBadEncodings() {
        assertKind(NodeBridgeFileScope.Kind.WRONG_TYPE) { catalog.read(".") }
        assertKind(NodeBridgeFileScope.Kind.WRONG_TYPE) { catalog.read("assets") }
        assertKind(NodeBridgeFileScope.Kind.NOT_FOUND) { catalog.read("assets/missing.css") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.read("../escape.html") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.read("assets//broken.css") }
        assertKind(NodeBridgeFileScope.Kind.INVALID_PATH) { catalog.read("index.html", encoding = "latin1") }
        assertTrue(installed!!.opened.isEmpty())
    }

    @Test
    fun anUnreadableInventoryFileIsAnIoFailureNotAnEscape() {
        val source = object : NodeBridgeDocsCatalog.Source by installed!! {
            override fun open(relativePath: String): InputStream? = null
        }
        val catalog = NodeBridgeDocsCatalog(source = { source })
        assertKind(NodeBridgeFileScope.Kind.IO) { catalog.read("index.html") }
    }

    private fun assertKind(kind: NodeBridgeFileScope.Kind, block: () -> Any?) {
        try {
            block()
        } catch (error: NodeBridgeFileScopeException) {
            assertEquals(error.message, kind, error.kind)
            return
        }
        fail("expected $kind")
    }
}
