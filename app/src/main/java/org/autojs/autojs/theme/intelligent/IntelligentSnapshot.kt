package org.autojs.autojs.theme.intelligent

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Everything the engine needs to evaluate the active library without touching the database (roadmap D9):
 * the library id and name, its configuration and its groups.
 * zh-CN: 引擎在不访问数据库的情况下评估激活库所需的全部数据 (Roadmap D9): 库 id 与名称, 配置以及全部组.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
data class IntelligentSnapshot(
    val libraryId: Int,
    val name: String,
    val config: IntelligentConfig,
    val groups: List<IntelligentGroup>,
) {

    fun group(id: Long?): IntelligentGroup? = groups.firstOrNull { it.id == id }

}

/**
 * Preference codecs for [IntelligentSnapshot] and [IntelligentRuntimeState]. Decoding never throws; malformed
 * input yields null (snapshot) or the default state (runtime).
 * zh-CN: [IntelligentSnapshot] 与 [IntelligentRuntimeState] 的偏好编解码. 解码不会抛出异常; 格式错误时快照返回 null, 运行时状态返回默认值.
 */
object IntelligentEngineJson {

    fun encodeSnapshot(snapshot: IntelligentSnapshot): String = JsonObject().apply {
        addProperty("libraryId", snapshot.libraryId)
        addProperty("name", snapshot.name)
        add("config", IntelligentConfigJson.toJson(snapshot.config))
        add("groups", JsonArray().apply { snapshot.groups.forEach { add(encodeGroup(it)) } })
    }.toString()

    fun decodeSnapshot(json: String?): IntelligentSnapshot? {
        val root = parseObject(json) ?: return null
        val libraryId = root.long("libraryId")?.toInt() ?: return null
        return IntelligentSnapshot(
            libraryId = libraryId,
            name = root.string("name") ?: "",
            config = root.objectOrNull("config")?.let(IntelligentConfigJson::fromJson) ?: IntelligentConfig(),
            groups = root.arrayOrNull("groups")?.mapNotNull { it.objectOrNull()?.let(::decodeGroup) } ?: emptyList(),
        )
    }

    fun encodeRuntime(state: IntelligentRuntimeState): String = JsonObject().apply {
        state.currentGroupId?.let { addProperty("currentGroupId", it) }
        state.lastStateKey?.let { addProperty("lastStateKey", it) }
        state.overrideGroupId?.let { addProperty("overrideGroupId", it) }
        state.intervalAnchorAt?.let { addProperty("intervalAnchorAt", it) }
        state.nextDueAt?.let { addProperty("nextDueAt", it) }
        state.lastRotatedAt?.let { addProperty("lastRotatedAt", it) }
    }.toString()

    fun decodeRuntime(json: String?): IntelligentRuntimeState {
        val root = parseObject(json) ?: return IntelligentRuntimeState()
        return IntelligentRuntimeState(
            currentGroupId = root.long("currentGroupId"),
            lastStateKey = root.string("lastStateKey"),
            overrideGroupId = root.long("overrideGroupId"),
            intervalAnchorAt = root.long("intervalAnchorAt"),
            nextDueAt = root.long("nextDueAt"),
            lastRotatedAt = root.long("lastRotatedAt"),
        )
    }

    private fun encodeGroup(group: IntelligentGroup) = JsonObject().apply {
        addProperty("id", group.id)
        group.name?.let { addProperty("name", it) }
        addProperty("enabled", group.enabled)
        addProperty("position", group.position)
        addProperty("createdTime", group.createdTime)
        addProperty("modifiedTime", group.modifiedTime)
        add("states", JsonObject().apply {
            group.states.forEach { (key, colors) ->
                add(key, JsonObject().apply {
                    colors.primary?.let { addProperty("primary", it) }
                    colors.accent?.let { addProperty("accent", it) }
                    colors.dark?.let { addProperty("dark", it) }
                })
            }
        })
    }

    private fun decodeGroup(json: JsonObject): IntelligentGroup? {
        val id = json.long("id") ?: return null
        val states = json.objectOrNull("states")?.entrySet()?.mapNotNull { (key, value) ->
            value.objectOrNull()?.let { colors ->
                key to StateColors(colors.long("primary")?.toInt(), colors.long("accent")?.toInt(), colors.long("dark")?.toInt())
            }
        }?.toMap() ?: emptyMap()
        return IntelligentGroup(
            id = id,
            name = json.string("name"),
            enabled = json.boolean("enabled") ?: true,
            position = json.long("position")?.toInt() ?: 0,
            createdTime = json.long("createdTime") ?: 0L,
            modifiedTime = json.long("modifiedTime") ?: 0L,
            states = states,
        )
    }

    private fun parseObject(json: String?): JsonObject? {
        if (json.isNullOrBlank()) return null
        return runCatching { JsonParser.parseString(json) }.getOrNull()?.objectOrNull()
    }

    private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.objectOrNull()

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.primitive(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.asString

    private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.takeIf { it.isBoolean }?.asBoolean

    private fun JsonObject.long(key: String): Long? = primitive(key)?.takeIf { it.isNumber }?.asLong

}
