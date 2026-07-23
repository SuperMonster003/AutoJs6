package org.autojs.autojs.core.plugin.docs

import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineDocsAssetPathPolicyTest {

    @Test
    fun `origin root resolves to the fixed entry point`() {
        assertEquals(
            OfflineDocsPluginContract.ENTRY_POINT,
            OfflineDocsAssetPathPolicy.resolve(""),
        )
    }

    @Test
    fun `only documented static asset types are accepted`() {
        listOf(
            "index.html",
            "assets/style.css",
            "assets/sh_main.js",
            "images/logo.png",
            "assets/fonts/font.woff2",
        ).forEach { path ->
            assertEquals(path, OfflineDocsAssetPathPolicy.resolve(path))
        }
        listOf(
            OfflineDocsPluginContract.INVENTORY_FILE,
            "manifest.json",
            "script.mjs",
            "image.svg",
            "archive.zip",
            "README",
        ).forEach { path ->
            assertNull(path, OfflineDocsAssetPathPolicy.resolve(path))
        }
    }

    @Test
    fun `absolute traversal and encoded paths are rejected`() {
        listOf(
            "/index.html",
            "../index.html",
            "assets/../index.html",
            "assets/./style.css",
            "assets//style.css",
            "assets\\style.css",
            "C:/index.html",
            "%2e%2e/index.html",
            "%252e%252e/index.html",
            "index%2ehtml",
            "index.html?source=plugin",
            "index.html#anchor",
            "index.html\u0000.png",
        ).forEach { path ->
            assertNull(path, OfflineDocsAssetPathPolicy.resolve(path))
        }
    }

    @Test
    fun `mime types are deterministic and safe`() {
        assertEquals("text/html", OfflineDocsAssetPathPolicy.mimeType("index.html"))
        assertEquals("text/css", OfflineDocsAssetPathPolicy.mimeType("assets/style.css"))
        assertEquals("application/javascript", OfflineDocsAssetPathPolicy.mimeType("assets/main.js"))
        assertEquals("image/png", OfflineDocsAssetPathPolicy.mimeType("images/logo.png"))
        assertEquals("font/woff2", OfflineDocsAssetPathPolicy.mimeType("assets/font.woff2"))
        assertNull(OfflineDocsAssetPathPolicy.mimeType("assets/data.json"))
    }
}
