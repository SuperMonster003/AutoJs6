package org.autojs.autojs.core.plugin.center.wizard

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

enum class PluginInstallWizardProfileKind {
    /** Plugins marked as standard in the catalog. */
    STANDARD,

    /** Plugins marked as minimal in the catalog. */
    MINIMAL,

    /** Every installable plugin. */
    ALL,

    /** A user-defined package set. */
    CUSTOM,
}

/**
 * A named plugin selection. Built-in profiles resolve their members from the
 * catalog markers at display time; custom profiles carry an explicit package set.
 *
 * zh-CN: 一组带名称的插件选择. 内置配置在展示时根据目录标记解析成员;
 * 自定义配置携带显式的包名集合.
 */
data class PluginInstallWizardProfile(
    val id: String,
    val kind: PluginInstallWizardProfileKind,
    val customName: String? = null,
    val packageNames: Set<String> = emptySet(),
) {

    val isCustom: Boolean
        get() = kind == PluginInstallWizardProfileKind.CUSTOM

    companion object {

        const val ID_STANDARD = "standard"
        const val ID_MINIMAL = "minimal"
        const val ID_ALL = "all"
        private const val CUSTOM_ID_PREFIX = "custom-"

        val STANDARD = PluginInstallWizardProfile(ID_STANDARD, PluginInstallWizardProfileKind.STANDARD)
        val MINIMAL = PluginInstallWizardProfile(ID_MINIMAL, PluginInstallWizardProfileKind.MINIMAL)
        val ALL = PluginInstallWizardProfile(ID_ALL, PluginInstallWizardProfileKind.ALL)

        val builtIns: List<PluginInstallWizardProfile> = listOf(STANDARD, MINIMAL, ALL)

        fun custom(name: String, packageNames: Set<String>, id: String = newCustomId()) = PluginInstallWizardProfile(
            id = id,
            kind = PluginInstallWizardProfileKind.CUSTOM,
            customName = name.trim(),
            packageNames = LinkedHashSet(packageNames),
        )

        fun newCustomId(): String = CUSTOM_ID_PREFIX + UUID.randomUUID().toString()

    }

}

/**
 * JSON codec of the custom profile list. Built without reflection so it works
 * unchanged under R8 and in plain JVM tests.
 *
 * zh-CN: 自定义配置列表的 JSON 编解码. 不依赖反射, 在 R8 与纯 JVM 测试下均可直接使用.
 */
object PluginInstallWizardProfileCodec {

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_PACKAGES = "packages"

    fun encode(profiles: List<PluginInstallWizardProfile>): String {
        val array = JsonArray()
        profiles.filter { it.isCustom }.forEach { profile ->
            array.add(JsonObject().apply {
                addProperty(KEY_ID, profile.id)
                addProperty(KEY_NAME, profile.customName.orEmpty())
                add(KEY_PACKAGES, JsonArray().also { packages -> profile.packageNames.forEach(packages::add) })
            })
        }
        return array.toString()
    }

    fun decode(json: String?): List<PluginInstallWizardProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()
        if (root == null || !root.isJsonArray) return emptyList()
        val seenIds = HashSet<String>()
        return root.asJsonArray.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.stringOrNull(KEY_ID)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = obj.stringOrNull(KEY_NAME)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!seenIds.add(id)) return@mapNotNull null
            val packages = obj.get(KEY_PACKAGES)
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { item -> item.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }
                .orEmpty()
            PluginInstallWizardProfile.custom(name, LinkedHashSet(packages), id)
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive }
        ?.asString

}
