package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract

/** One view of the preview tree, in root pixels (see [UiPreviewContract.Bounds]). */
data class UiPreviewBoundsEntry(
    val elementIndex: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val className: String,
    val id: String?,
    val instance: Int,
    val visibility: Int,
)

/** A property-level problem that did not stop the layout (see [UiPreviewContract.Diagnostic]). */
data class UiPreviewDiagnostic(
    val code: String,
    val message: String,
    val elementIndex: Int,
    val attrName: String?,
    /** Contract v3: why an attribute was ignored ([UiPreviewContract.ATTRIBUTE_OUTCOMES]), for `ATTRIBUTE_IGNORED` only. */
    val outcome: String? = null,
)

/** The JSON of a bounds list after the entry limit was applied. */
data class UiPreviewEncodedBounds(val json: String, val count: Int, val truncated: Boolean)

/**
 * JSON encoding of bounds and diagnostics with a plain string builder: no org.json dependency, so the
 * JVM tests run it, and the member names come from the contract.
 *
 * zh-CN: 边界与诊断的 JSON 编码, 只用字符串拼接: 不依赖 org.json 以便 JVM 测试运行, 成员名来自契约.
 */
object UiPreviewBounds {

    fun encode(entries: List<UiPreviewBoundsEntry>, limit: Int = UiPreviewContract.MAX_BOUNDS_ENTRIES): UiPreviewEncodedBounds {
        val sorted = entries.sortedWith(compareBy({ it.elementIndex }, { it.instance }))
        val kept = if (sorted.size > limit) sorted.subList(0, limit) else sorted
        val json = StringBuilder(kept.size * 72 + 2).append('[')
        kept.forEachIndexed { position, entry ->
            if (position > 0) json.append(',')
            json.append('{')
                .append('"').append(UiPreviewContract.Bounds.ELEMENT_INDEX).append("\":").append(entry.elementIndex)
                .append(",\"").append(UiPreviewContract.Bounds.LEFT).append("\":").append(entry.left)
                .append(",\"").append(UiPreviewContract.Bounds.TOP).append("\":").append(entry.top)
                .append(",\"").append(UiPreviewContract.Bounds.RIGHT).append("\":").append(entry.right)
                .append(",\"").append(UiPreviewContract.Bounds.BOTTOM).append("\":").append(entry.bottom)
                .append(",\"").append(UiPreviewContract.Bounds.CLASS_NAME).append("\":").append(quote(entry.className))
            entry.id?.let { json.append(",\"").append(UiPreviewContract.Bounds.ID).append("\":").append(quote(it)) }
            json.append(",\"").append(UiPreviewContract.Bounds.INSTANCE).append("\":").append(entry.instance)
                .append(",\"").append(UiPreviewContract.Bounds.VISIBILITY).append("\":").append(entry.visibility)
                .append('}')
        }
        json.append(']')
        return UiPreviewEncodedBounds(json.toString(), kept.size, kept.size < sorted.size)
    }

    fun encodeDiagnostics(diagnostics: List<UiPreviewDiagnostic>): String {
        val json = StringBuilder().append('[')
        diagnostics.forEachIndexed { position, diagnostic ->
            if (position > 0) json.append(',')
            json.append('{')
                .append('"').append(UiPreviewContract.Diagnostic.CODE).append("\":").append(quote(diagnostic.code))
                .append(",\"").append(UiPreviewContract.Diagnostic.MESSAGE).append("\":").append(quote(diagnostic.message))
                .append(",\"").append(UiPreviewContract.Diagnostic.ELEMENT_INDEX).append("\":").append(diagnostic.elementIndex)
            diagnostic.attrName?.let { json.append(",\"").append(UiPreviewContract.Diagnostic.ATTR_NAME).append("\":").append(quote(it)) }
            diagnostic.outcome?.let { json.append(",\"").append(UiPreviewContract.Diagnostic.OUTCOME).append("\":").append(quote(it)) }
            json.append('}')
        }
        return json.append(']').toString()
    }

    /** A JSON string literal with the quotes, escaping control characters as `\uXXXX`. */
    fun quote(text: String): String {
        val out = StringBuilder(text.length + 2).append('"')
        for (ch in text) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' || ch == ' ' || ch == ' ' -> out.append("\\u").append(String.format("%04x", ch.code))
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }
}
