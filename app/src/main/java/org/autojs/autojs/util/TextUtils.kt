package org.autojs.autojs.util

import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object TextUtils {

    private val mParser by lazy {
        Parser.builder().build()
    }

    private val mHtmlRender by lazy {
        HtmlRenderer.builder()
            .extensions(setOf(HeadingAnchorExtension.Builder().build()))
            .build()
    }

    @JvmStatic
    fun renderMarkdown(markdown: String): String = mParser.parse(markdown).let { mHtmlRender.render(it) }

}