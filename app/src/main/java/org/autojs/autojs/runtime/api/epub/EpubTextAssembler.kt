package org.autojs.autojs.runtime.api.epub

import org.autojs.autojs.core.plugin.epub.EpubTextChunk
import org.autojs.plugin.epub.api.EpubContract
import kotlin.math.min

/**
 * Joins the text windows of `IEpubBook.getText` into the strings the script API returns (plugin
 * roadmap P6.1, appendix A.3): one resource is read from `offset` window by window until the
 * plugin reports no more text or `maxChars` characters were collected; the whole book reads
 * every reading-order resource from its start and joins the non-blank ones with [SEPARATOR]
 * under one shared `maxChars`. A window never exceeds the contract's per-call ceiling, an empty
 * chunk ends a resource (whatever `hasMore` says) and a surrogate pair is never split by the cap.
 * Pure Kotlin over [EpubTextChunk], so the JVM tests drive it with fake chunk sources.
 * zh-CN: 把 `IEpubBook.getText` 的文本窗口拼成脚本 API 返回的字符串 (插件路线图 P6.1, 附录 A.3): 单个资源从 `offset` 起逐窗口读取, 直到插件报告
 * 没有更多文本或已收集 `maxChars` 个字符; 全书从头读取每个阅读顺序资源, 非空白者以 [SEPARATOR] 连接, 共用一个 `maxChars`. 窗口不超过契约的单次
 * 上限, 空分块结束一个资源 (不论 `hasMore`), 上限不会拆开代理对. 纯 Kotlin, 基于 [EpubTextChunk], JVM 测试用假分块源驱动.
 */
internal object EpubTextAssembler {

    /** Between two resources of the whole-book text (appendix A.3). zh-CN: 全书文本中两个资源之间 (附录 A.3). */
    const val SEPARATOR = "\n\n"

    /** One `getText` call of a fixed resource: the window starting at [offset] of at most [maxChars] characters. zh-CN: 固定资源的一次 `getText` 调用: 从 [offset] 起至多 [maxChars] 个字符的窗口. */
    fun interface ChunkSource {
        fun fetch(offset: Int, maxChars: Int): EpubTextChunk
    }

    /** The text of one resource from [offset], capped at [maxChars]. zh-CN: 一个资源从 [offset] 起的文本, 上限 [maxChars]. */
    fun resource(offset: Int, maxChars: Int, source: ChunkSource, window: Int = EpubContract.MAX_TEXT_CHARS_PER_CALL): String {
        require(offset >= 0) { "offset must not be negative" }
        require(window > 0) { "window must be positive" }
        val out = StringBuilder()
        var at = offset
        while (out.length < maxChars) {
            val wanted = min(maxChars - out.length, window)
            val chunk = source.fetch(at, wanted)
            if (chunk.text.isEmpty()) break
            val accepted = fit(chunk.text, wanted)
            out.append(accepted)
            at += accepted.length
            if (!chunk.hasMore || accepted.length < chunk.text.length) break
        }
        return out.toString()
    }

    /** The whole book: resources `0 until count` in reading order, joined by [SEPARATOR], capped at [maxChars]. zh-CN: 全书: 阅读顺序中的资源 `0 until count`, 以 [SEPARATOR] 连接, 上限 [maxChars]. */
    fun book(count: Int, maxChars: Int, source: (index: Int) -> ChunkSource, window: Int = EpubContract.MAX_TEXT_CHARS_PER_CALL): String {
        val out = StringBuilder()
        for (index in 0 until count) {
            val remaining = maxChars - out.length
            val budget = if (out.isEmpty()) remaining else remaining - SEPARATOR.length
            if (budget <= 0) break
            val text = resource(0, budget, source(index), window)
            if (text.isBlank()) continue
            if (out.isNotEmpty()) out.append(SEPARATOR)
            out.append(text)
        }
        return out.toString()
    }

    /** [text] cut to [maxChars] without ending on a high surrogate. zh-CN: [text] 截到 [maxChars], 不以高代理项结尾. */
    private fun fit(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        var end = maxChars
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }
}
