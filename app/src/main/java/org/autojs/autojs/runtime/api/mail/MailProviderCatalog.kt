package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonObject

/**
 * The provider preset table of the plugin (`listProviders`, mail roadmap P2.1) as the script
 * API `mail.providers` reads it (P3.1): [list] in catalog order, [get] by id (case-insensitive)
 * and [resolve] by the domain of a mail address. Presets never carry an account, so the values
 * go to scripts as they are.
 * zh-CN: 插件的服务商预设表 (`listProviders`, 邮件路线图 P2.1) 在脚本 API `mail.providers` 眼中的样子 (P3.1): [list] 按目录顺序, [get] 按
 * id (不区分大小写), [resolve] 按邮件地址的域名. 预设从不携带账户, 因此原样交给脚本.
 */
class MailProviderCatalog(json: String?) {

    private val providers: List<JsonObject>

    /** `version` of the catalog, 0 when the plugin sent none. zh-CN: 目录的 `version`, 插件未给出时为 0. */
    val version: Int

    init {
        val root = MailScriptValues.parse(json).takeIf { it.isJsonObject }?.asJsonObject
        version = root?.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
        providers = root?.get("providers")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonObject }
            ?.map { it.asJsonObject }
            ?.filter { it.id() != null }
            .orEmpty()
    }

    val ids: List<String>
        get() = providers.mapNotNull { it.id() }

    /** Every preset as a plain value. zh-CN: 每个预设的普通值. */
    fun list(): List<Any?> = providers.map(MailScriptValues::toPlain)

    /** The preset with [id], or null. zh-CN: id 为 [id] 的预设, 或 null. */
    fun get(id: String?): Any? {
        val key = id?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return providers.firstOrNull { it.id() == key }?.let(MailScriptValues::toPlain)
    }

    /** The preset whose `domains` contain the domain of [address], or null. zh-CN: `domains` 含 [address] 域名的预设, 或 null. */
    fun resolve(address: String?): Any? {
        val domain = address?.substringAfterLast('@', "")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return providers.firstOrNull { provider -> provider.domains().any { it == domain } }?.let(MailScriptValues::toPlain)
    }

    private fun JsonObject.id(): String? =
        get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.domains(): List<String> =
        get("domains")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.map { it.asString.trim().lowercase() }
            .orEmpty()
}
