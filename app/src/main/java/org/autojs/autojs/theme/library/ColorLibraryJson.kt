package org.autojs.autojs.theme.library

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentConfigJson
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.StateColors
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The AutoJs6 color library exchange format (roadmap D14, P2.1, appendix B): `format = "autojs6.color-library"`,
 * `schemaVersion = 1`, any number of plain and intelligent libraries per file.
 * zh-CN: AutoJs6 颜色库交换格式 (Roadmap D14, P2.1, 附录 B): `format = "autojs6.color-library"`, `schemaVersion = 1`,
 * 一个文件可含任意个普通库与智能库.
 *
 * - Colors are written as opaque `#RRGGBB`; `#AARRGGBB` is accepted and made opaque.
 * - Override targets are indices into the `groups` array of the same library. Decoded groups get their index as id,
 *   so the decoded configuration refers to groups by index too.
 * - Times are ISO 8601 with an offset; missing or unreadable times decode as `0` (the importer fills them in).
 * - Unknown fields are ignored; libraries of an unknown type are skipped and counted; invalid color values and
 *   overrides pointing at missing groups are dropped and counted per library.
 * - Limits: [MAX_FILE_BYTES], [MAX_LIBRARIES], [MAX_ITEMS_PER_LIBRARY]. A newer schema is rejected.
 * zh-CN:
 * - 颜色写为不透明的 `#RRGGBB`; 读取时也接受 `#AARRGGBB` 并去除透明度.
 * - 覆盖层的目标为同一库 `groups` 数组的下标. 解码后的组以下标为 id, 因此解码出的配置同样按下标引用组.
 * - 时间为带偏移的 ISO 8601; 缺失或无法解析时解码为 `0` (由导入方填充).
 * - 未知字段被忽略; 未知类型的库被跳过并计数; 无效色值与指向不存在组的覆盖层被丢弃, 按库计数.
 * - 限额: [MAX_FILE_BYTES], [MAX_LIBRARIES], [MAX_ITEMS_PER_LIBRARY]. 更高版本的 schema 被拒绝.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object ColorLibraryJson {

    const val FORMAT = "autojs6.color-library"
    const val SCHEMA_VERSION = 1

    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_LIBRARIES = 256
    const val MAX_ITEMS_PER_LIBRARY = 4096

    const val MIME_TYPE = "application/json"

    private const val KEY_FORMAT = "format"
    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_APP = "app"
    private const val KEY_LIBRARIES = "libraries"
    private const val KEY_TYPE = "type"
    private const val KEY_NAME = "name"
    private const val KEY_COLORS = "colors"
    private const val KEY_COLOR = "color"
    private const val KEY_CONFIG = "config"
    private const val KEY_GROUPS = "groups"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STATES = "states"
    private const val KEY_PRIMARY = "primary"
    private const val KEY_ACCENT = "accent"
    private const val KEY_DARK = "dark"
    private const val KEY_CREATED_AT = "createdAt"
    private const val KEY_MODIFIED_AT = "modifiedAt"

    private const val TYPE_PLAIN = "plain"
    private const val TYPE_INTELLIGENT = "intelligent"

    private val COLOR_PATTERN = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    private const val OPAQUE = 0xFF shl 24

    sealed interface Library {
        val name: String
        val itemCount: Int
    }

    data class PlainLibrary(override val name: String, val colors: List<Color>) : Library {
        override val itemCount get() = colors.size
    }

    data class IntelligentLibrary(override val name: String, val config: IntelligentConfig, val groups: List<IntelligentGroup>) : Library {
        override val itemCount get() = groups.size
    }

    data class Color(val color: Int, val name: String? = null, val createdTime: Long = 0L, val modifiedTime: Long = 0L)

    data class AppInfo(val versionName: String, val versionCode: Int)

    /** A library read from a file, with the number of values that were dropped. zh-CN: 从文件读取的库, 附带被丢弃的值的数量. */
    data class DecodedLibrary(val library: Library, val droppedValues: Int = 0)

    data class Document(
        val libraries: List<DecodedLibrary>,
        val exportedAt: Long? = null,
        val app: AppInfo? = null,
        val skippedLibraries: Int = 0,
    )

    enum class Reason { TOO_LARGE, MALFORMED, NOT_A_COLOR_LIBRARY, NEWER_SCHEMA, NO_LIBRARIES, TOO_MANY_LIBRARIES, TOO_MANY_ITEMS }

    /** [detail] is the schema version for [Reason.NEWER_SCHEMA] and the library name for [Reason.TOO_MANY_ITEMS]. */
    class ColorLibraryJsonException(val reason: Reason, val detail: String? = null) : Exception("$reason${detail?.let { ": $it" } ?: ""}")

    private val gson by lazy { GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create() }

    /**
     * Encodes [libraries] into a file. Throws [ColorLibraryJsonException] when a limit is exceeded.
     * zh-CN: 将 [libraries] 编码为文件内容. 超出限额时抛出 [ColorLibraryJsonException].
     */
    fun encode(libraries: List<Library>, exportedAt: Long, zone: ZoneId, app: AppInfo? = null): String {
        checkLimits(libraries.size) { index -> libraries[index].let { it.name to it.itemCount } }
        val root = JsonObject().apply {
            addProperty(KEY_FORMAT, FORMAT)
            addProperty(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            addProperty(KEY_EXPORTED_AT, formatTime(exportedAt, zone))
            app?.let { add(KEY_APP, JsonObject().apply { addProperty("versionName", it.versionName); addProperty("versionCode", it.versionCode) }) }
            add(KEY_LIBRARIES, JsonArray().apply { libraries.forEach { add(encodeLibrary(it, zone)) } })
        }
        return gson.toJson(root).also { text ->
            if (text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) throw ColorLibraryJsonException(Reason.TOO_LARGE)
        }
    }

    /**
     * Decodes a file. Throws [ColorLibraryJsonException] when the content cannot be imported as a whole.
     * Blank library names become [fallbackName].
     * zh-CN: 解码文件内容. 内容整体无法导入时抛出 [ColorLibraryJsonException]. 空白的库名替换为 [fallbackName].
     */
    fun decode(text: String, fallbackName: String = ""): Document {
        if (text.length > MAX_FILE_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
            throw ColorLibraryJsonException(Reason.TOO_LARGE)
        }
        val root = runCatching { JsonParser.parseString(text) }.getOrNull()?.objectOrNull()
            ?: throw ColorLibraryJsonException(Reason.MALFORMED)
        if (root.string(KEY_FORMAT) != FORMAT) throw ColorLibraryJsonException(Reason.NOT_A_COLOR_LIBRARY)
        val schemaVersion = root.long(KEY_SCHEMA_VERSION) ?: throw ColorLibraryJsonException(Reason.NOT_A_COLOR_LIBRARY)
        if (schemaVersion > SCHEMA_VERSION) throw ColorLibraryJsonException(Reason.NEWER_SCHEMA, schemaVersion.toString())
        if (schemaVersion < 1) throw ColorLibraryJsonException(Reason.NOT_A_COLOR_LIBRARY)
        val entries = root.arrayOrNull(KEY_LIBRARIES) ?: throw ColorLibraryJsonException(Reason.NOT_A_COLOR_LIBRARY)
        checkLimits(entries.size()) { index ->
            val entry = entries[index].objectOrNull()
            val items = entry?.arrayOrNull(KEY_COLORS)?.size() ?: entry?.arrayOrNull(KEY_GROUPS)?.size() ?: 0
            (entry?.string(KEY_NAME) ?: "") to items
        }
        var skipped = 0
        val libraries = entries.mapNotNull { element ->
            val entry = element.objectOrNull()
            val name = entry?.string(KEY_NAME)?.let(LibraryNaming::normalize)?.takeIf { it.isNotEmpty() } ?: fallbackName
            when (entry?.string(KEY_TYPE)) {
                TYPE_PLAIN -> decodePlain(entry, name)
                TYPE_INTELLIGENT -> decodeIntelligent(entry, name)
                else -> null
            } ?: run {
                skipped += 1
                null
            }
        }
        if (libraries.isEmpty()) throw ColorLibraryJsonException(Reason.NO_LIBRARIES)
        val app = root.objectOrNull(KEY_APP)?.let { app ->
            val versionName = app.string("versionName") ?: return@let null
            AppInfo(versionName, app.long("versionCode")?.toInt() ?: 0)
        }
        return Document(libraries, parseTime(root.string(KEY_EXPORTED_AT)), app, skipped)
    }

    /** Formats a color as `#RRGGBB`. zh-CN: 将颜色格式化为 `#RRGGBB`. */
    fun formatColor(color: Int) = "#%06X".format(color and 0xFFFFFF)

    /** Parses `#RRGGBB` or `#AARRGGBB` into an opaque color. zh-CN: 将 `#RRGGBB` 或 `#AARRGGBB` 解析为不透明颜色. */
    fun parseColor(text: String?): Int? {
        if (text == null || !COLOR_PATTERN.matches(text)) return null
        return text.substring(text.length - 6).toLong(16).toInt() or OPAQUE
    }

    private inline fun checkLimits(count: Int, nameAndItems: (Int) -> Pair<String, Int>) {
        if (count > MAX_LIBRARIES) throw ColorLibraryJsonException(Reason.TOO_MANY_LIBRARIES)
        for (index in 0 until count) {
            val (name, items) = nameAndItems(index)
            if (items > MAX_ITEMS_PER_LIBRARY) throw ColorLibraryJsonException(Reason.TOO_MANY_ITEMS, name)
        }
    }

    private fun encodeLibrary(library: Library, zone: ZoneId): JsonObject = when (library) {
        is PlainLibrary -> JsonObject().apply {
            addProperty(KEY_TYPE, TYPE_PLAIN)
            addProperty(KEY_NAME, library.name)
            add(KEY_COLORS, JsonArray().apply {
                library.colors.forEach { color ->
                    add(JsonObject().apply {
                        color.name?.let { addProperty(KEY_NAME, it) }
                        addProperty(KEY_COLOR, formatColor(color.color))
                        addTimes(color.createdTime, color.modifiedTime, zone)
                    })
                }
            })
        }
        is IntelligentLibrary -> JsonObject().apply {
            addProperty(KEY_TYPE, TYPE_INTELLIGENT)
            addProperty(KEY_NAME, library.name)
            val indices = library.groups.withIndex().associate { (index, group) -> group.id to index.toLong() }
            add(KEY_CONFIG, IntelligentConfigJson.toJson(library.config.remapGroupIds(indices)))
            add(KEY_GROUPS, JsonArray().apply {
                library.groups.forEach { group ->
                    add(JsonObject().apply {
                        group.name?.let { addProperty(KEY_NAME, it) }
                        addProperty(KEY_ENABLED, group.enabled)
                        addTimes(group.createdTime, group.modifiedTime, zone)
                        add(KEY_STATES, JsonObject().apply {
                            group.states.forEach { (key, colors) ->
                                val primary = colors.primary ?: return@forEach
                                add(key, JsonObject().apply {
                                    addProperty(KEY_PRIMARY, formatColor(primary))
                                    colors.accent?.let { addProperty(KEY_ACCENT, formatColor(it)) }
                                    colors.dark?.let { addProperty(KEY_DARK, formatColor(it)) }
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun decodePlain(json: JsonObject, name: String): DecodedLibrary {
        var dropped = 0
        val colors = json.arrayOrNull(KEY_COLORS)?.mapNotNull { element ->
            val entry = element.objectOrNull()
            val color = entry?.let { parseColor(it.string(KEY_COLOR)) }
            if (entry == null || color == null) {
                dropped += 1
                return@mapNotNull null
            }
            Color(
                color = color,
                name = entry.string(KEY_NAME)?.trim()?.takeIf { it.isNotEmpty() },
                createdTime = parseTime(entry.string(KEY_CREATED_AT)) ?: 0L,
                modifiedTime = parseTime(entry.string(KEY_MODIFIED_AT)) ?: 0L,
            )
        }.orEmpty()
        return DecodedLibrary(PlainLibrary(name, colors), dropped)
    }

    private fun decodeIntelligent(json: JsonObject, name: String): DecodedLibrary {
        var dropped = 0
        val groups = json.arrayOrNull(KEY_GROUPS)?.mapIndexedNotNull { index, element ->
            val entry = element.objectOrNull() ?: run {
                dropped += 1
                return@mapIndexedNotNull null
            }
            val states = entry.objectOrNull(KEY_STATES)?.entrySet()?.mapNotNull { (key, value) ->
                val state = value.objectOrNull()
                val primary = state?.let { parseColor(it.string(KEY_PRIMARY)) }
                if (state == null || primary == null) {
                    dropped += 1
                    return@mapNotNull null
                }
                fun optional(field: String): Int? {
                    if (!state.has(field)) return null
                    return parseColor(state.string(field)) ?: null.also { dropped += 1 }
                }
                key to StateColors(primary, optional(KEY_ACCENT), optional(KEY_DARK))
            }?.toMap().orEmpty()
            IntelligentGroup(
                id = index.toLong(),
                name = entry.string(KEY_NAME)?.trim()?.takeIf { it.isNotEmpty() },
                enabled = entry.boolean(KEY_ENABLED) ?: true,
                position = index,
                createdTime = parseTime(entry.string(KEY_CREATED_AT)) ?: 0L,
                modifiedTime = parseTime(entry.string(KEY_MODIFIED_AT)) ?: 0L,
                states = states,
            )
        }.orEmpty()
        val decoded = json.objectOrNull(KEY_CONFIG)?.let(IntelligentConfigJson::fromJson) ?: IntelligentConfig()
        // Group ids are the file indices; overrides pointing elsewhere are dropped.
        // zh-CN: 组 id 即文件中的下标; 指向其他位置的覆盖层被丢弃.
        val config = decoded.remapGroupIds(groups.associate { it.id to it.id })
        dropped += decoded.overrides.size - config.overrides.size
        return DecodedLibrary(IntelligentLibrary(name, config, groups), dropped)
    }

    private fun JsonObject.addTimes(created: Long, modified: Long, zone: ZoneId) {
        if (created > 0L) addProperty(KEY_CREATED_AT, formatTime(created, zone))
        if (modified > 0L) addProperty(KEY_MODIFIED_AT, formatTime(modified, zone))
    }

    private fun formatTime(millis: Long, zone: ZoneId): String {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun parseTime(text: String?): Long? {
        text ?: return null
        return runCatching { OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
    }

    private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.objectOrNull()

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.primitive(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.asString

    private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.takeIf { it.isBoolean }?.asBoolean

    private fun JsonObject.long(key: String): Long? = primitive(key)?.takeIf { it.isNumber }?.asNumber?.toDouble()
        ?.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()

}
