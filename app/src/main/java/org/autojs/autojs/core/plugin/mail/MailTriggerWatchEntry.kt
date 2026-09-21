package org.autojs.autojs.core.plugin.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.mail.api.MailContract

/**
 * One background watch of the plugin as `IMailPlugin.listTriggers` reports it (mail roadmap P8):
 * what the task editor needs to offer a choice. Pure Kotlin.
 * zh-CN: `IMailPlugin.listTriggers` 报告的插件的一个后台守望 (邮件路线图 P8): 任务编辑器提供选择所需的内容. 纯 Kotlin.
 */
data class MailTriggerWatchEntry(
    val triggerId: String,
    val alias: String,
    val address: String?,
    val enabled: Boolean,
    val state: String?,
) {
    /** `<id> (<address or alias>)`, plus the state when the watch is not enabled or not connected. zh-CN: `<id> (<地址或别名>)`, 守望未启用或未连接时附带状态. */
    val label: String
        get() {
            val account = address?.takeIf { it.isNotBlank() } ?: alias
            val base = if (account.isBlank()) triggerId else "$triggerId ($account)"
            return when {
                !enabled -> "$base [off]"
                state != null && state != MailContract.TRIGGER_STATE_CONNECTED -> "$base [$state]"
                else -> base
            }
        }

    companion object {
        /** Parses the `triggers` document; damaged text or entries without an id yield nothing. zh-CN: 解析 `triggers` 文档; 损坏的文本或无 id 的条目不计. */
        @JvmStatic
        fun parseList(json: String?): List<MailTriggerWatchEntry> {
            if (json.isNullOrBlank()) return emptyList()
            val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
            val entries = root.get(MailContract.FIELD_TRIGGERS)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return entries.mapNotNull { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val triggerId = entry.string(MailContract.FIELD_TRIGGER_ID)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val status = entry.get(FIELD_STATUS)?.takeIf { it.isJsonObject }?.asJsonObject
                MailTriggerWatchEntry(
                    triggerId = triggerId,
                    alias = entry.string(MailContract.FIELD_ALIAS).orEmpty(),
                    address = entry.string(MailContract.FIELD_ADDRESS),
                    enabled = entry.get(MailContract.FIELD_ENABLED)?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrNull() ?: true,
                    state = status?.string(MailContract.FIELD_STATE),
                )
            }
        }

        /** The entry field that holds the watch status document. zh-CN: 存放守望状态文档的条目字段. */
        private const val FIELD_STATUS = "status"

        private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
