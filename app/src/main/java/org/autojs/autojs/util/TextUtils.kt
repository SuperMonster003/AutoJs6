package org.autojs.autojs.util

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
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