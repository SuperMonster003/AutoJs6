package org.autojs.autojs.ui.preview

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.AttributeProviderFactory
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.Locale

class DocumentPreviewRenderer {

    private val extensions: List<Extension> = listOf(
        AutolinkExtension.create(),
        TablesExtension.create(),
        StrikethroughExtension.create(),
        HeadingAnchorExtension.Builder().build(),
    )

    private val parser = Parser.builder()
        .extensions(extensions)
        .build()

    private val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .percentEncodeUrls(true)
        .attributeProviderFactory(AttributeProviderFactory {
            AttributeProvider { _, tagName, attributes ->
                when (tagName) {
                    "a" -> attributes["href"]?.let { href ->
                        if (!DocumentPreviewUrlPolicy.isSafeLink(href)) {
                            attributes.remove("href")
                        }
                    }
                    "img" -> attributes["src"]?.let { src ->
                        if (!DocumentPreviewUrlPolicy.isSafeResource(src)) {
                            attributes.remove("src")
                        }
                    }
                }
            }
        })
        .build()

    fun renderMarkdown(
        markdown: String,
        stylesheetName: String,
        customCss: String?,
    ): String {
        val renderedBody = renderer.render(parser.parse(markdown))
        val body = enhanceTaskLists(renderedBody)
        val customStyle = customCss
            ?.takeIf(String::isNotBlank)
            ?.let(DocumentPreviewSecurityPolicy::escapeInlineStyle)
            ?.let { """<style id="document-preview-custom-style">$it</style>""" }
            .orEmpty()

        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
                <meta name="color-scheme" content="light dark">
                <meta http-equiv="Content-Security-Policy" content="${DocumentPreviewSecurityPolicy.CONTENT_SECURITY_POLICY}">
                <link rel="stylesheet" href="${DocumentPreviewWebOrigin.PREVIEW_ASSET_BASE_URL}base.css">
                <link rel="stylesheet" href="${DocumentPreviewWebOrigin.PREVIEW_ASSET_BASE_URL}$stylesheetName">
                $customStyle
            </head>
            <body>
                <article class="markdown-body">$body</article>
            </body>
            </html>
        """.trimIndent()
    }

    fun renderHtml(html: String): String {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)

        document.select("base, script, iframe, frame, frameset, object, embed, applet").remove()
        document.select("meta[http-equiv]").remove()

        document.allElements.forEach(::sanitizeElement)
        document.selectFirst("html")?.addClass("document-preview-html")
        document.head().prepend(
            """
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
                <meta name="color-scheme" content="light dark">
                <meta http-equiv="Content-Security-Policy" content="${DocumentPreviewSecurityPolicy.CONTENT_SECURITY_POLICY}">
                <link rel="stylesheet" href="${DocumentPreviewWebOrigin.PREVIEW_ASSET_BASE_URL}html-base.css">
            """.trimIndent(),
        )
        return document.outerHtml()
    }

    private fun sanitizeElement(element: Element) {
        element.attributes().asList()
            .map { it.key }
            .filter { it.lowercase(Locale.ROOT).startsWith("on") }
            .forEach(element::removeAttr)

        element.removeAttr("ping")
        element.removeAttr("target")
        element.removeAttr("srcdoc")
        element.removeAttr("action")
        element.removeAttr("formaction")
        element.removeAttr("srcset")

        if (element.hasAttr("href")) {
            val href = element.attr("href")
            val linkElement = element.normalName() == "a" || element.normalName() == "area"
            val safe = if (linkElement) {
                DocumentPreviewUrlPolicy.isSafeLink(href)
            } else {
                DocumentPreviewUrlPolicy.isSafeResource(href)
            }
            if (!safe) element.removeAttr("href")
        }

        listOf("src", "poster", "background", "xlink:href").forEach { attribute ->
            if (element.hasAttr(attribute) && !DocumentPreviewUrlPolicy.isSafeResource(element.attr(attribute))) {
                element.removeAttr(attribute)
            }
        }
    }

    private fun enhanceTaskLists(renderedBody: String): String {
        val fragment = Jsoup.parseBodyFragment(renderedBody)
        fragment.outputSettings().prettyPrint(false)
        fragment.select("li").forEach { item ->
            val textNode = firstTextNode(item) ?: return@forEach
            val match = TASK_ITEM_PATTERN.find(textNode.wholeText) ?: return@forEach
            val checked = match.groupValues[1].equals("x", ignoreCase = true)
            textNode.text(textNode.wholeText.removeRange(match.range))
            item.addClass("task-list-item")
            item.parent()?.takeIf { it.normalName() == "ul" || it.normalName() == "ol" }
                ?.addClass("task-list")
            item.prependElement("input")
                .attr("type", "checkbox")
                .attr("disabled", "")
                .also { if (checked) it.attr("checked", "") }
        }
        return fragment.body().html()
    }

    private fun firstTextNode(node: Node): TextNode? {
        node.childNodes().forEach { child ->
            if (child is TextNode) return child
            firstTextNode(child)?.let { return it }
        }
        return null
    }

    companion object {
        private val TASK_ITEM_PATTERN = Regex("""^\s*\[([ xX])]\s+""")
    }
}

internal object DocumentPreviewSecurityPolicy {

    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
            "font-src 'self' data:; media-src 'self' data:; script-src 'none'; " +
            "connect-src 'none'; frame-src 'none'; child-src 'none'; object-src 'none'; " +
            "worker-src 'none'; manifest-src 'none'; base-uri 'none'; form-action 'none'"

    fun escapeInlineStyle(css: String): String =
        css.replace(Regex("</style", RegexOption.IGNORE_CASE)) { "<\\/style" }
}

internal object DocumentPreviewUrlPolicy {

    private val schemePattern = Regex("""^([a-z][a-z0-9+.-]*):""", RegexOption.IGNORE_CASE)
    private val safeDataResourcePattern = Regex(
        """^data:(?:image/(?:png|jpeg|gif|webp|avif|bmp|svg\+xml)|audio/(?:mpeg|ogg|wav|mp4)|video/(?:mp4|webm|ogg))(?:;|,)""",
        RegexOption.IGNORE_CASE,
    )

    fun isSafeLink(value: String): Boolean {
        val normalized = normalize(value) ?: return false
        if (normalized.startsWith("//") || normalized.startsWith("\\\\")) return false
        val scheme = schemePattern.find(normalized)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        return scheme == null || scheme == "http" || scheme == "https"
    }

    fun isSafeResource(value: String): Boolean {
        val normalized = normalize(value) ?: return false
        if (normalized.startsWith("//") || normalized.startsWith("\\\\")) return false
        val scheme = schemePattern.find(normalized)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        return when {
            scheme == null -> true
            scheme == "data" -> safeDataResourcePattern.containsMatchIn(normalized)
            else -> false
        }
    }

    private fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any { it == '\u0000' || it.code < 0x20 || it.code == 0x7f }) return null
        return trimmed
    }
}

internal object DocumentPreviewWebOrigin {
    const val DOMAIN = "appassets.androidplatform.net"
    const val DOCUMENT_PATH_PREFIX = "/document/"
    const val DOCUMENT_BASE_URL = "https://$DOMAIN$DOCUMENT_PATH_PREFIX"
    const val DOCUMENT_FILE_NAME = "__preview__.html"
    const val DOCUMENT_URL = "$DOCUMENT_BASE_URL$DOCUMENT_FILE_NAME"
    const val PREVIEW_ASSET_PATH_PREFIX = "/preview-assets/"
    const val PREVIEW_ASSET_BASE_URL = "https://$DOMAIN$PREVIEW_ASSET_PATH_PREFIX"
}
