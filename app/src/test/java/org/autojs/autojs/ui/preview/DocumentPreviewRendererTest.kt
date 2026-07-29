package org.autojs.autojs.ui.preview

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPreviewRendererTest {

    private val renderer = DocumentPreviewRenderer()

    @Test
    fun markdownIsRenderedWithExtensionsAndUnsafeInputIsNeutralized() {
        val markdown = """
            # Preview Heading

            <script id="raw-markdown">alert(1)</script>

            [unsafe](javascript:alert(1))
            [safe](https://example.com/docs)

            | Name | Value |
            | --- | --- |
            | One | Two |

            ~~removed~~

            - [x] completed
            - [ ] pending
        """.trimIndent()

        val output = renderer.renderMarkdown(
            markdown = markdown,
            stylesheetName = "github-light.css",
            customCss = null,
        )
        val document = Jsoup.parse(output)
        val unsafeLink = requireNotNull(document.select("a").firstOrNull { it.text() == "unsafe" })
        val safeLink = requireNotNull(document.select("a").firstOrNull { it.text() == "safe" })

        assertTrue(output.contains("""&lt;script id="raw-markdown"&gt;"""))
        assertNull(document.selectFirst("script#raw-markdown"))
        assertFalse(unsafeLink.hasAttr("href"))
        assertEquals("https://example.com/docs", safeLink.attr("href"))
        assertNotNull(document.selectFirst("h1#preview-heading"))
        assertNotNull(document.selectFirst("table"))
        assertEquals("removed", document.selectFirst("del")?.text())
        assertEquals(2, document.select("li.task-list-item").size)
        assertEquals(2, document.select("ul.task-list input[type=checkbox][disabled]").size)
        assertEquals(1, document.select("input[type=checkbox][checked]").size)

        val csp = document.selectFirst("meta[http-equiv=Content-Security-Policy]")?.attr("content").orEmpty()
        assertTrue(csp.contains("script-src 'none'"))
        assertTrue(csp.contains("connect-src 'none'"))
        assertTrue(csp.contains("object-src 'none'"))
        assertTrue(csp.contains("base-uri 'none'"))
        assertTrue(csp.contains("form-action 'none'"))
    }

    @Test
    fun customCssCannotEscapeItsStyleElement() {
        val customCss =
            """body { color: red; }</StYlE><script id="escaped">alert(1)</script><style>"""

        val output = renderer.renderMarkdown(
            markdown = "# Safe",
            stylesheetName = "github-dark.css",
            customCss = customCss,
        )
        val document = Jsoup.parse(output)

        assertTrue(output.contains("<\\/style>", ignoreCase = true))
        assertFalse(output.contains("</style><script id=\"escaped\"", ignoreCase = true))
        assertNotNull(document.selectFirst("style#document-preview-custom-style"))
        assertNull(document.selectFirst("script#escaped"))
    }

    @Test
    fun htmlSanitizerRemovesExecutableContentAndKeepsSafeDocumentResources() {
        val html = """
            <!doctype html>
            <html>
            <head>
                <base href="https://evil.example/">
                <meta http-equiv="refresh" content="0; url=https://evil.example/">
                <link id="relative-css" rel="stylesheet" href="styles/site.css">
                <link id="remote-css" rel="stylesheet" href="https://evil.example/site.css">
                <script id="script">alert(1)</script>
            </head>
            <body onload="alert(1)">
                <iframe id="frame" src="https://evil.example/"></iframe>
                <img id="relative-image" src="images/picture.png" onerror="alert(1)">
                <img id="remote-image" src="https://evil.example/picture.png">
                <img id="script-image" src="javascript:alert(1)">
                <a id="safe-link" href="https://example.com/docs" onclick="alert(1)">Docs</a>
                <a id="script-link" href="javascript:alert(1)">Unsafe</a>
            </body>
            </html>
        """.trimIndent()

        val output = renderer.renderHtml(html)
        val document = Jsoup.parse(output)

        assertTrue(document.select("base, script, iframe, frame, object, embed, applet").isEmpty())
        assertTrue(
            document.select("meta[http-equiv]").none {
                it.attr("http-equiv").equals("refresh", ignoreCase = true)
            },
        )
        assertFalse(document.body().hasAttr("onload"))
        assertEquals("styles/site.css", document.selectFirst("#relative-css")?.attr("href"))
        assertFalse(document.selectFirst("#remote-css")!!.hasAttr("href"))
        assertEquals("images/picture.png", document.selectFirst("#relative-image")?.attr("src"))
        assertFalse(document.selectFirst("#relative-image")!!.hasAttr("onerror"))
        assertFalse(document.selectFirst("#remote-image")!!.hasAttr("src"))
        assertFalse(document.selectFirst("#script-image")!!.hasAttr("src"))
        assertEquals("https://example.com/docs", document.selectFirst("#safe-link")?.attr("href"))
        assertFalse(document.selectFirst("#safe-link")!!.hasAttr("onclick"))
        assertFalse(document.selectFirst("#script-link")!!.hasAttr("href"))
        assertNotNull(
            document.selectFirst(
                """link[href="${DocumentPreviewWebOrigin.PREVIEW_ASSET_BASE_URL}html-base.css"]""",
            ),
        )
    }
}
