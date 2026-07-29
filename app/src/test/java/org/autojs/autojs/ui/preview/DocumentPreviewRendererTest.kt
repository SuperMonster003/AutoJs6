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

        assertNull(document.selectFirst("script#raw-markdown"))
        assertFalse(output.contains("alert(1)"))
        assertFalse(unsafeLink.hasAttr("href"))
        assertEquals("https://example.com/docs", safeLink.attr("href"))
        assertNotNull(document.selectFirst("h1#preview-heading"))
        assertNotNull(document.selectFirst("table"))
        assertTrue(document.selectFirst("table")?.parent()?.hasClass("markdown-table-wrapper") == true)
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
        assertTrue(csp.contains("img-src 'self' data: https:"))
        assertEquals(
            DocumentPreviewSecurityPolicy.REFERRER_POLICY,
            document.selectFirst("meta[name=referrer]")?.attr("content"),
        )
    }

    @Test
    fun markdownSupportsSanitizedGitHubHtmlWithoutShowingMarkup() {
        val markdown = """
            <!--suppress HtmlDeprecatedAttribute -->

            <div align="center">
              <p>
                <a href="https://github.com/example/project">
                  <img alt="Banner" src="https://raw.githubusercontent.com/example/project/main/banner.png" height="224"/>
                </a>
                <img id="badge-with-space" alt="Badge" src="https://img.shields.io/badge/auto.js->= 6.2.0-67a91b"/>
              </p>
              <p>Centered description</p>
            </div>

            <details open><summary>查看更多功能</summary><br>

            - 自动收取好友能量

            </details>

            [comment]: <> (Version history only shows last 3 versions)
        """.trimIndent()

        val output = renderer.renderMarkdown(
            markdown = markdown,
            stylesheetName = "github-light.css",
            customCss = null,
        )
        val document = Jsoup.parse(output)
        val image = requireNotNull(document.selectFirst("div[align=center] a > img"))

        assertEquals(
            "https://raw.githubusercontent.com/example/project/main/banner.png",
            image.attr("src"),
        )
        assertEquals("224", image.attr("height"))
        assertEquals("lazy", image.attr("loading"))
        assertEquals("async", image.attr("decoding"))
        assertEquals("no-referrer", image.attr("referrerpolicy"))
        assertEquals(
            "https://img.shields.io/badge/auto.js->= 6.2.0-67a91b",
            document.selectFirst("#badge-with-space")?.attr("src"),
        )
        assertNotNull(document.selectFirst("details[open] > summary"))
        assertEquals("自动收取好友能量", document.selectFirst("details li")?.text())
        assertFalse(document.body().text().contains("<!--suppress"))
        assertFalse(document.body().text().contains("<div"))
        assertFalse(document.body().text().contains("<img"))
        assertFalse(document.body().text().contains("Version history only shows"))
    }

    @Test
    fun markdownRawHtmlSanitizerKeepsOnlySafeElementsAttributesAndImageUrls() {
        val markdown = """
            <div id="safe" align="center" style="position:fixed" onclick="alert(1)">
              <script id="script">script payload</script>
              <style id="style">body { display: none; }</style>
              <iframe id="frame" src="https://example.com/"></iframe>
              <svg id="svg"><script>svg payload</script></svg>
              <form id="form"><input value="unsafe">form payload</form>
              <marquee><strong id="kept">Kept text</strong></marquee>
              <a id="link" href="https://example.com/docs" target="_blank" ping="https://tracker.example/">Docs</a>
              <img id="https-image" src="https://example.com/image.png" height="100" onerror="alert(1)" srcset="https://example.com/2x.png 2x" style="display:none">
              <img id="relative-image" src="images/picture.png">
              <img id="data-image" src="data:image/png;base64,iVBORw0KGgo=">
              <img id="http-image" src="http://example.com/image.png">
              <img id="protocol-relative-image" src="//example.com/image.png">
              <img id="script-image" src="javascript:alert(1)">
              <img id="text-data-image" src="data:text/html;base64,PGgxPkJhZDwvaDE+">
            </div>
        """.trimIndent()

        val document = Jsoup.parse(
            renderer.renderMarkdown(
                markdown = markdown,
                stylesheetName = "github-light.css",
                customCss = null,
            ),
        )

        assertTrue(document.select("script, style#style, iframe, svg, form, input").isEmpty())
        assertFalse(document.body().text().contains("script payload"))
        assertFalse(document.body().text().contains("svg payload"))
        assertFalse(document.body().text().contains("form payload"))
        assertNotNull(document.selectFirst("strong#kept"))
        assertNull(document.selectFirst("marquee"))

        val safe = requireNotNull(document.selectFirst("div#safe"))
        assertEquals("center", safe.attr("align"))
        assertFalse(safe.hasAttr("style"))
        assertFalse(safe.hasAttr("onclick"))

        val link = requireNotNull(document.selectFirst("a#link"))
        assertEquals("https://example.com/docs", link.attr("href"))
        assertEquals("noopener noreferrer", link.attr("rel"))
        assertFalse(link.hasAttr("target"))
        assertFalse(link.hasAttr("ping"))

        val httpsImage = requireNotNull(document.selectFirst("img#https-image"))
        assertEquals("https://example.com/image.png", httpsImage.attr("src"))
        assertEquals("100", httpsImage.attr("height"))
        assertFalse(httpsImage.hasAttr("onerror"))
        assertFalse(httpsImage.hasAttr("srcset"))
        assertFalse(httpsImage.hasAttr("style"))
        assertEquals("images/picture.png", document.selectFirst("#relative-image")?.attr("src"))
        assertTrue(document.selectFirst("#data-image")?.hasAttr("src") == true)
        listOf(
            "#http-image",
            "#protocol-relative-image",
            "#script-image",
            "#text-data-image",
        ).forEach { selector ->
            assertFalse(selector, document.selectFirst(selector)!!.hasAttr("src"))
        }
    }

    @Test
    fun markdownTablesScrollAndLegacyCommentsStayVisibleOnlyInsideCodeBlocks() {
        val markdown = """
            [comment]: <> (hidden paragraph)

            ```text
            [comment]: <> (visible code)
            ```

            | Left | Center | Right |
            | :--- | :----: | ----: |
            | One | Two | Three |

            | A | B |
            | --- | --- |
            | C | D |
        """.trimIndent()

        val document = Jsoup.parse(
            renderer.renderMarkdown(
                markdown = markdown,
                stylesheetName = "github-light.css",
                customCss = null,
            ),
        )

        assertTrue(
            document.select("p").none {
                it.text().contains("[comment]: <> (hidden paragraph)")
            },
        )
        assertEquals("[comment]: <> (visible code)", document.selectFirst("pre code")?.text())
        assertEquals(2, document.select(".markdown-table-wrapper").size)
        assertEquals(2, document.select(".markdown-table-wrapper > table").size)
        assertTrue(document.select(".markdown-table-wrapper .markdown-table-wrapper").isEmpty())
        assertEquals("left", document.selectFirst("th")?.attr("align"))
        assertEquals("center", document.select("th").getOrNull(1)?.attr("align"))
        assertEquals("right", document.select("th").getOrNull(2)?.attr("align"))
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
        assertEquals(
            "https://evil.example/picture.png",
            document.selectFirst("#remote-image")?.attr("src"),
        )
        assertEquals("no-referrer", document.selectFirst("#remote-image")?.attr("referrerpolicy"))
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
