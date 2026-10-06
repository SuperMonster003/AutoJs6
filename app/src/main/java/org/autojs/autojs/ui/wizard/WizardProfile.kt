package org.autojs.autojs.ui.wizard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

enum class WizardProfileKind {
    /** Entries marked as standard by the wizard catalog. */
    STANDARD,

    /** Entries marked as minimal by the wizard catalog. */
    MINIMAL,

    /** Every entry of the wizard. */
    ALL,

    /** A user-defined member set. */
    CUSTOM,
}

/**
 * A named selection of a wizard. Built-in profiles resolve their members from the
 * catalog markers at display time; custom profiles carry an explicit member set.
 *
 * zh-CN: 向导中一组带名称的选择. 内置配置在展示时根据目录标记解析成员;
 * 自定义配置携带显式的成员集合.
 */
data class WizardProfile(
    val id: String,
    val kind: WizardProfileKind,
    val customName: String? = null,
    val memberIds: Set<String> = emptySet(),
) {

    val isCustom: Boolean
        get() = kind == WizardProfileKind.CUSTOM

    companion object {

        const val ID_STANDARD = "standard"
        const val ID_MINIMAL = "minimal"
        const val ID_ALL = "all"
        private const val CUSTOM_ID_PREFIX = "custom-"

        val STANDARD = WizardProfile(ID_STANDARD, WizardProfileKind.STANDARD)
        val MINIMAL = WizardProfile(ID_MINIMAL, WizardProfileKind.MINIMAL)
        val ALL = WizardProfile(ID_ALL, WizardProfileKind.ALL)

        val builtIns: List<WizardProfile> = listOf(STANDARD, MINIMAL, ALL)

        fun custom(name: String, memberIds: Set<String>, id: String = newCustomId()) = WizardProfile(
            id = id,
            kind = WizardProfileKind.CUSTOM,
            customName = name.trim(),
            memberIds = LinkedHashSet(memberIds),
        )

        fun newCustomId(): String = CUSTOM_ID_PREFIX + UUID.randomUUID().toString()

    }

}

/**
 * JSON codec of a custom profile list. Built without reflection so it works
 * unchanged under R8 and in plain JVM tests. [membersKey] names the member array,
 * which keeps documents written by earlier versions of a wizard readable.
 *
 * zh-CN: 自定义配置列表的 JSON 编解码. 不依赖反射, 在 R8 与纯 JVM 测试下均可直接使用.
 * [membersKey] 指定成员数组的键名, 以兼容向导早期版本写入的文档.
 */
class WizardProfileCodec(private val membersKey: String) {

    fun encode(profiles: List<WizardProfile>): String {
        val array = JsonArray()
        profiles.filter { it.isCustom }.forEach { profile ->
            array.add(JsonObject().apply {
                addProperty(KEY_ID, profile.id)
                addProperty(KEY_NAME, profile.customName.orEmpty())
                add(membersKey, JsonArray().also { members -> profile.memberIds.forEach(members::add) })
            })
        }
        return array.toString()
    }

    fun decode(json: String?): List<WizardProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
        if (root == null || !root.isJsonArray) return emptyList()
        val seenIds = HashSet<String>()
        return root.asJsonArray.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.stringOrNull(KEY_ID)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = obj.stringOrNull(KEY_NAME)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!seenIds.add(id)) return@mapNotNull null
            val members = obj.get(membersKey)
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { item -> item.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }
                .orEmpty()
            WizardProfile.custom(name, LinkedHashSet(members), id)
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive }
        ?.asString

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
    }

}
