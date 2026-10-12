package org.autojs.autojs.util

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object TextUtils {

    private val mMarkdownParser by lazy {
        Parser.builder().build()
    }

    private val mHtmlRender by lazy {
        HtmlRenderer.builder()
            .extensions(setOf(HeadingAnchorExtension.Builder().build()))
            .build()
    }

    @JvmStatic
    fun markdownToHtml(markdown: String): String = mMarkdownParser.parse(markdown).let { mHtmlRender.render(it) }

    // The six helpers of Auto.js 4's com.stardust.util.TextUtils, kept because that class name resolves to this object
    // through StardustClassAliases (roadmap P7.3). Unlike the Auto.js 4 copies, the String fast paths honour fromIndex.
    // zh-CN: Auto.js 4 com.stardust.util.TextUtils 的 6 个方法, 因该类名经 StardustClassAliases 解析到本对象而保留
    // (roadmap P7.3). 与 Auto.js 4 的副本不同, String 快速路径同样遵守 fromIndex.

    @JvmStatic
    fun join(delimiter: CharSequence, vararg tokens: Any?): String = android.text.TextUtils.join(delimiter, tokens)

    @JvmStatic
    fun toEmptyIfNull(message: String?): String = message ?: ""

    @JvmStatic
    fun indexOf(text: CharSequence, ch: Char, fromIndex: Int): Int = when (text) {
        is String -> text.indexOf(ch, fromIndex)
        else -> android.text.TextUtils.indexOf(text, ch, fromIndex)
    }

    @JvmStatic
    fun lastIndexOf(text: CharSequence, ch: Char, fromIndex: Int): Int = when (text) {
        is String -> text.lastIndexOf(ch, fromIndex)
        else -> android.text.TextUtils.lastIndexOf(text, ch, fromIndex)
    }

    @JvmStatic
    fun indexOf(source: CharSequence, target: CharSequence, fromIndex: Int): Int = when {
        source is String && target is String -> source.indexOf(target, fromIndex)
        else -> android.text.TextUtils.indexOf(source, target, fromIndex)
    }

    @JvmStatic
    fun lastIndexOf(source: CharSequence, target: CharSequence, fromIndex: Int): Int {
        if (source is String && target is String) return source.lastIndexOf(target, fromIndex)
        var index = minOf(fromIndex, source.length - target.length)
        while (index >= 0) {
            if (android.text.TextUtils.regionMatches(source, index, target, 0, target.length)) return index
            index--
        }
        return -1
    }

    @JvmStatic
    @JvmOverloads
    fun splitSpanned(spanned: Spanned, delimiter: String = "\n"): List<Spanned> {
        val result = mutableListOf<SpannableString>()
        val fullText = spanned.toString()
        var start = 0

        while (true) {
            // 找到分隔符位置
            val index = fullText.indexOf(delimiter, start)
            // 如果没有更多分隔符, 则 end 指向文本结尾
            val end = if (index == -1) fullText.length else index
            // 截取当前部分文本
            val partText = spanned.subSequence(start, end)
            val spannablePart = SpannableString(partText)

            // 提取在当前区间内的所有 span 对象
            val spans = spanned.getSpans(start, end, Any::class.java)
            for (span in spans) {
                val spanStart = spanned.getSpanStart(span)
                val spanEnd = spanned.getSpanEnd(span)

                // 只复制完全落在当前部分中的 span
                if (spanStart >= start && spanEnd <= end) {
                    spannablePart.setSpan(
                        span,
                        spanStart - start,
                        spanEnd - start,
                        spanned.getSpanFlags(span)
                    )
                }
            }
            result.add(spannablePart)

            // 如果已经到达文本末尾则结束循环
            if (index == -1) {
                break
            }
            start = index + delimiter.length
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    fun joinSpanned(spannedList: List<Spanned>, delimiter: String = "\n"): Spanned {
        val builder = SpannableStringBuilder()
        spannedList.forEachIndexed { index, spanned ->
            builder.append(spanned)
            if (index != spannedList.lastIndex) {
                builder.append(delimiter)
            }
        }
        return builder
    }

}