package org.autojs.autojs.core.plugin.mail

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.plugin.mail.api.MailContract
import java.util.Locale

/**
 * The optional filter of a "mail arrived" task (mail roadmap P8, contract version 2 `filter`):
 * the same shape and rules as the plugin's watch filter, applied by the host to the event's
 * message before a script is launched. Every group that is not empty must match; inside a group
 * one entry is enough: a [from] entry is a case-insensitive substring of the sender's address or
 * display name, a [subject] entry a case-insensitive substring of the subject. Pure Kotlin.
 * zh-CN: "邮件到达" 任务的可选过滤 (邮件路线图 P8, 契约版本 2 的 `filter`): 与插件守望过滤同形同义, 由宿主在启动脚本前应用于事件的消息;
 * 非空的组都必须命中, 组内命中一项即可; `from` 项不区分大小写地匹配发件人地址或显示名称的子串, `subject` 项匹配主题子串. 纯 Kotlin.
 */
data class MailTriggerFilter(
    val from: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = from.isEmpty() && subject.isEmpty()

    /** True when the event's message passes; a message without the fields the filter needs fails. zh-CN: 事件的消息通过时为 true; 缺少过滤所需字段的消息视为不通过. */
    fun matches(message: JsonObject?): Boolean {
        if (isEmpty) return true
        message ?: return false
        if (from.isNotEmpty()) {
            val sender = (message.get("from") ?: message.get("sender"))?.takeIf { it.isJsonObject }?.asJsonObject
            val haystacks = listOfNotNull(sender?.string("address"), sender?.string("name")).map { it.lowercase(Locale.ROOT) }
            if (from.none { needle -> haystacks.any { it.contains(needle) } }) return false
        }
        if (subject.isNotEmpty()) {
            val text = message.string("subject")?.lowercase(Locale.ROOT) ?: return false
            if (subject.none { text.contains(it) }) return false
        }
        return true
    }

    fun toJson(): JsonObject = JsonObject().apply {
        add(MailContract.FIELD_FILTER_FROM, JsonArray().also { array -> from.forEach { array.add(it) } })
        add(MailContract.FIELD_FILTER_SUBJECT, JsonArray().also { array -> subject.forEach { array.add(it) } })
    }

    companion object {
        val NONE = MailTriggerFilter()

        /** Entries per group, as the plugin enforces. zh-CN: 每组的条目上限, 与插件一致. */
        const val MAX_ENTRIES = 16
        const val MAX_ENTRY_LENGTH = 256

        /** Reads a `filter` object (each group a string or an array of strings); null or a non-object means no filter. zh-CN: 读取 `filter` 对象 (每组为字符串或字符串数组); null 或非对象视为无过滤. */
        fun parse(element: JsonElement?): MailTriggerFilter {
            val filter = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return NONE
            return MailTriggerFilter(entries(filter.get(MailContract.FIELD_FILTER_FROM)), entries(filter.get(MailContract.FIELD_FILTER_SUBJECT)))
        }

        /** Splits one text field of the task editor into entries (commas, semicolons and line breaks). zh-CN: 把任务编辑器的一个文本框拆成条目 (逗号, 分号与换行). */
        fun split(text: String?): List<String> = normalize(text.orEmpty().split(',', ';', '\n', '\r'))

        /** Trimmed, lower-cased, blank entries dropped, duplicates collapsed, ceilings applied. zh-CN: 修剪, 小写, 去空白, 去重, 应用上限. */
        fun normalize(raw: List<String>): List<String> = raw
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .map { it.take(MAX_ENTRY_LENGTH) }
            .distinct()
            .take(MAX_ENTRIES)

        private fun entries(element: JsonElement?): List<String> = when {
            element == null || element.isJsonNull -> emptyList()
            element.isJsonArray -> normalize(element.asJsonArray.mapNotNull { item -> item.takeIf { it.isJsonPrimitive }?.asString })
            element.isJsonPrimitive -> normalize(listOf(element.asString))
            else -> emptyList()
        }

        private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
