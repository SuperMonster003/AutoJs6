package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class NodeBridgeSampleCatalogTest {

    private val directories = mapOf(
        "sample" to listOf("工具", ".idea", "readme.txt", "AutoJs6"),
        "sample/AutoJs6" to listOf("nested", "hello.js"),
        "sample/AutoJs6/nested" to listOf("deep.js"),
        "sample/工具" to listOf("a.js"),
        "sample/.idea" to listOf("workspace.xml"),
    )

    private val files = mapOf(
        "sample/readme.txt" to "hi".toByteArray(),
        "sample/AutoJs6/hello.js" to "console.log(1)".toByteArray(),
        "sample/AutoJs6/nested/deep.js" to "你好世界".toByteArray(),
        "sample/工具/a.js" to "a".toByteArray(),
        "sample/.idea/workspace.xml" to "<x/>".toByteArray(),
        "sample/bin.dat" to byteArrayOf(0, 0xFF.toByte(), 0xFE.toByte()),
    )

    private val catalog = NodeBridgeSampleCatalog(
        lister = { path -> directories[path]?.toTypedArray() ?: emptyArray() },
        opener = { path -> files[path]?.let { ByteArrayInputStream(it) } },
    )

    @Test
    fun rootListingSortsDirectoriesFirstAndHidesDotEntries() {
        val root = catalog.list()
        assertTrue(root.available)
        assertFalse(root.truncated)
        assertEquals(".", root.path)
        assertEquals(listOf("AutoJs6", "工具", "readme.txt"), root.entries.map { it.path })
        assertEquals(listOf(true, true, false), root.entries.map { it.isDirectory })
        assertEquals("readme.txt", root.entries.last().name)
    }

    @Test
    fun recursiveListingWalksDepthFirstAndHonoursTheLimit() {
        val deep = catalog.list(".", recursive = true)
        assertEquals(
            listOf("AutoJs6", "AutoJs6/nested", "AutoJs6/nested/deep.js", "AutoJs6/hello.js", "工具", "工具/a.js", "readme.txt"),
            deep.entries.map { it.path },
        )
        val limited = catalog.list("./AutoJs6/", recursive = true, maxEntries = 2)
        assertEquals(listOf("AutoJs6/nested", "AutoJs6/nested/deep.js"), limited.entries.map { it.path })
        assertTrue(limited.truncated)
        assertEquals("AutoJs6", limited.path)
    }

    @Test
    fun listingRejectsFilesMissingPathsAndEscapes() {
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { catalog.list("readme.txt") })
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { catalog.list("ghost") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.list("../secrets") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.list("/sample") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.list("AutoJs6\\nested") })
    }

    @Test
    fun readReturnsTextBase64AndCutsTruncatedUtf8OnBoundaries() {
        val hello = catalog.read("AutoJs6/hello.js")
        assertEquals("console.log(1)", hello.content)
        assertEquals(14, hello.bytes)
        assertFalse(hello.truncated)
        assertEquals(NodeBridgeFileScope.ENCODING_UTF8, hello.encoding)

        val cut = catalog.read("AutoJs6/nested/deep.js", maxBytes = 4)
        assertEquals("你", cut.content)
        assertEquals(4, cut.bytes)
        assertTrue(cut.truncated)

        val exact = catalog.read("AutoJs6/nested/deep.js", maxBytes = 12)
        assertEquals("你好世界", exact.content)
        assertFalse(exact.truncated)

        val binary = catalog.read("bin.dat", encoding = "base64")
        assertEquals(Base64.getEncoder().encodeToString(files.getValue("sample/bin.dat")), binary.content)
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { catalog.read("bin.dat") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.read("bin.dat", encoding = "latin1") })
    }

    @Test
    fun readRejectsDirectoriesRootMissingAndHiddenPaths() {
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { catalog.read("AutoJs6") })
        assertEquals(NodeBridgeFileScope.Kind.WRONG_TYPE, failure { catalog.read(".") })
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { catalog.read("AutoJs6/missing.js") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.read("") })
        assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, failure { catalog.read("AutoJs6/../readme.txt") })
        // Hidden entries are not listed but remain readable when addressed directly.
        assertEquals("<x/>", catalog.read(".idea/workspace.xml").content)
    }

    @Test
    fun missingSampleRootReportsUnavailable() {
        val empty = NodeBridgeSampleCatalog(lister = { emptyArray() }, opener = { null })
        val listing = empty.list()
        assertFalse(listing.available)
        assertTrue(listing.entries.isEmpty())
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { empty.list("AutoJs6") })
        assertEquals(NodeBridgeFileScope.Kind.NOT_FOUND, failure { empty.read("AutoJs6/hello.js") })
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
}
