package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Packaged sample text and binary reads, including Android 24's base64 compatibility path. */
class NodeBridgeSampleCatalogDeviceTest {
    @Test fun sampleAssetsRemainReadableAsTextAndBoundedBinary() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val catalog = NodeBridgeSampleCatalog(
            lister = { assets.list(it) },
            opener = { runCatching { assets.open(it) }.getOrNull() },
        )
        assertTrue(catalog.list().available)
        assertTrue(catalog.read("JavaScript/HelloWorld.js").content.contains("toast("))
        val raw = assets.open("sample/OCR/test.png").use { it.readBytes() }
        val read = catalog.read("OCR/test.png", encoding = "base64", maxBytes = 37)
        assertArrayEquals(raw.copyOf(37), Base64.decode(read.content, Base64.DEFAULT))
        assertEquals(37, read.bytes)
        assertTrue(read.truncated)
        try { catalog.read("../secret"); fail("sample paths must stay scoped") }
        catch (e: NodeBridgeFileScopeException) { assertEquals(NodeBridgeFileScope.Kind.INVALID_PATH, e.kind) }
    }
}
