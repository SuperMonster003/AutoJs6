package org.autojs.autojs.core.plugin.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.mail.api.MailContract

/**
 * The `extras` column of a "mail arrived" intent task (mail roadmap P8): which background watch
 * of the plugin the task listens to ([triggerId], empty for every watch) and the optional
 * [filter] the event's message must pass. Stored as JSON `{"triggerId": "...", "filter": {...}}`.
 * zh-CN: "邮件到达" 意图任务的 `extras` 列 (邮件路线图 P8): 任务监听插件的哪个后台守望 ([triggerId], 空表示全部) 与事件消息须通过的可选 [filter].
 * 以 JSON `{"triggerId": "...", "filter": {...}}` 存储.
 */
data class MailTriggerTaskExtras(
    val triggerId: String = "",
    val filter: MailTriggerFilter = MailTriggerFilter.NONE,
) {
    /** True when the task listens to every watch. zh-CN: 任务监听全部守望时为 true. */
    val anyWatch: Boolean get() = triggerId.isEmpty()

    /** True when an event of [eventTriggerId] concerns this task. zh-CN: [eventTriggerId] 的事件与此任务相关时为 true. */
    fun accepts(eventTriggerId: String): Boolean = anyWatch || triggerId.equals(eventTriggerId, ignoreCase = true)

    fun toJson(): String = JsonObject().apply {
        addProperty(MailContract.FIELD_TRIGGER_ID, triggerId)
        add(MailContract.FIELD_FILTER, filter.toJson())
    }.toString()

    companion object {
        val ANY = MailTriggerTaskExtras()

        /** Parses the stored column; blank or damaged text means "every watch, no filter". zh-CN: 解析存储的列; 空白或损坏的文本视为 "全部守望, 无过滤". */
        @JvmStatic
        fun parse(json: String?): MailTriggerTaskExtras {
            if (json.isNullOrBlank()) return ANY
            val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return ANY
            val triggerId = root.get(MailContract.FIELD_TRIGGER_ID)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            return MailTriggerTaskExtras(triggerId, MailTriggerFilter.parse(root.get(MailContract.FIELD_FILTER)))
        }
    }
}
