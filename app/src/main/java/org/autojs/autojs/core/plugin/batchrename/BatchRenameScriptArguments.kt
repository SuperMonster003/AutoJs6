package org.autojs.autojs.core.plugin.batchrename

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.time.Instant
import java.time.format.DateTimeParseException
import org.autojs.plugin.batchrename.api.BatchRenameLimits
import org.autojs.plugin.batchrename.api.BatchRenameOptionKeys
import org.autojs.plugin.batchrename.api.BatchRenamePresetKeys
import org.autojs.plugin.batchrename.api.BatchRenameValues

/** The `source` and `flags` of a `RegExp` passed as `search`. zh-CN: 作为 `search` 传入的 `RegExp` 的 `source` 与 `flags`. */
data class BatchRenameRegExp(val source: String, val flags: String)

/**
 * One name of `preview` (plugin roadmap D37); [lastModified] is null when the script gave none, and the date variables
 * then use the time of the call.
 * zh-CN: `preview` 的一个名称 (插件路线图 D37); 脚本未给出 [lastModified] 时为 null, 日期变量改用调用时刻.
 */
data class BatchRenamePreviewItem(val name: String, val directory: Boolean = false, val lastModified: Long? = null)

/**
 * Checks and normalizes the arguments of `files.batchRename` (plugin roadmap appendix B), as JSON trees the way
 * `JSON.stringify` sees the script values; the Rhino layer extracts a `RegExp` search beforehand. Options become the
 * complete contract option map with the defaults of roadmap D17, which is also what `plan.options` shows.
 *
 * zh-CN: 检查并规范化 `files.batchRename` 的参数 (插件路线图附录 B), 输入为 `JSON.stringify` 所见的 JSON 树; `RegExp` 形式的
 * `search` 由 Rhino 层事先提取. 选项被规范化为带 D17 默认值的完整契约选项表, 也就是 `plan.options` 所显示的内容.
 */
object BatchRenameScriptArguments {

    /** Declared first: [CHOICES] refers to it. zh-CN: 先声明, [CHOICES] 引用它. */
    val CONFLICT_POLICIES: List<String> = listOf(
        BatchRenameValues.CONFLICT_FAIL,
        BatchRenameValues.CONFLICT_SKIP,
        BatchRenameValues.CONFLICT_NUMBER,
        BatchRenameValues.CONFLICT_OVERWRITE,
    )

    private val BOOLEAN_OPTIONS = listOf(
        BatchRenameOptionKeys.REGEX to false,
        BatchRenameOptionKeys.MATCH_ALL to false,
        BatchRenameOptionKeys.CASE_SENSITIVE to false,
        BatchRenameOptionKeys.INCLUDE_FILES to true,
        BatchRenameOptionKeys.INCLUDE_FOLDERS to true,
        BatchRenameOptionKeys.INCLUDE_SUBFOLDERS to true,
        BatchRenameOptionKeys.ENUMERATE to false,
        BatchRenameOptionKeys.RANDOM to false,
        BatchRenameOptionKeys.HALF_WIDTH to false,
        BatchRenameOptionKeys.SANITIZE to false,
        BatchRenameOptionKeys.TRIM to false,
        BatchRenameOptionKeys.CONTINUE_NUMBERING to false,
        BatchRenameOptionKeys.PINYIN to false,
        BatchRenameOptionKeys.PINYIN_INITIALS to false,
        BatchRenameOptionKeys.METADATA to false,
    ).toMap()

    private val CHOICES = mapOf(
        BatchRenameOptionKeys.TARGET to listOf(BatchRenameValues.TARGET_ALL, BatchRenameValues.TARGET_NAME, BatchRenameValues.TARGET_EXTENSION),
        BatchRenameOptionKeys.FORMAT to listOf(
            BatchRenameValues.FORMAT_NONE,
            BatchRenameValues.FORMAT_LOWER,
            BatchRenameValues.FORMAT_UPPER,
            BatchRenameValues.FORMAT_TITLE,
            BatchRenameValues.FORMAT_CAPITALIZE,
        ),
        BatchRenameOptionKeys.DATE_TIME to listOf(BatchRenameValues.DATE_TIME_MODIFIED, BatchRenameValues.DATE_TIME_NOW, BatchRenameValues.DATE_TIME_NONE),
        BatchRenameOptionKeys.NORMALIZE to listOf(BatchRenameValues.NORMALIZE_NONE, BatchRenameValues.NORMALIZE_NFC, BatchRenameValues.NORMALIZE_NFD),
        BatchRenameOptionKeys.ON_CONFLICT to CONFLICT_POLICIES,
        BatchRenameOptionKeys.PINYIN_TONE to BatchRenameValues.PINYIN_TONES,
        BatchRenameOptionKeys.PINYIN_SEPARATOR to BatchRenameValues.PINYIN_SEPARATORS,
        BatchRenameOptionKeys.PINYIN_CASE to BatchRenameValues.PINYIN_CASES,
    )

    /** The choice an option has when the script passes `false`. zh-CN: 脚本传入 `false` 时选项取的值. */
    private val FALSE_CHOICES = mapOf(
        BatchRenameOptionKeys.DATE_TIME to BatchRenameValues.DATE_TIME_NONE,
        BatchRenameOptionKeys.NORMALIZE to BatchRenameValues.NORMALIZE_NONE,
        BatchRenameOptionKeys.PINYIN_TONE to BatchRenameValues.PINYIN_TONE_NONE,
        BatchRenameOptionKeys.PINYIN_SEPARATOR to BatchRenameValues.PINYIN_SEPARATOR_NONE,
    )

    private val TEXT_OPTIONS = listOf(
        BatchRenameOptionKeys.REPLACE to "",
        BatchRenameOptionKeys.SANITIZE_REPLACEMENT to BatchRenameValues.DEFAULT_SANITIZE_REPLACEMENT,
        BatchRenameOptionKeys.CONFLICT_NUMBER_PATTERN to BatchRenameValues.DEFAULT_CONFLICT_NUMBER_PATTERN,
    ).toMap()

    private val DEFAULT_CHOICES = mapOf(
        BatchRenameOptionKeys.TARGET to BatchRenameValues.TARGET_ALL,
        BatchRenameOptionKeys.FORMAT to BatchRenameValues.FORMAT_NONE,
        BatchRenameOptionKeys.DATE_TIME to BatchRenameValues.DATE_TIME_MODIFIED,
        BatchRenameOptionKeys.NORMALIZE to BatchRenameValues.NORMALIZE_NONE,
        BatchRenameOptionKeys.ON_CONFLICT to BatchRenameValues.CONFLICT_FAIL,
        BatchRenameOptionKeys.PINYIN_TONE to BatchRenameValues.PINYIN_TONE_NONE,
        BatchRenameOptionKeys.PINYIN_SEPARATOR to BatchRenameValues.PINYIN_SEPARATOR_NONE,
        BatchRenameOptionKeys.PINYIN_CASE to BatchRenameValues.PINYIN_CASE_LOWER,
    )

    /** The options a plugin receives only with [BatchRenameValues.FEATURE_PINYIN]. zh-CN: 插件声明拼音功能时才收到的选项. */
    private val PINYIN_OPTIONS = listOf(
        BatchRenameOptionKeys.PINYIN,
        BatchRenameOptionKeys.PINYIN_TONE,
        BatchRenameOptionKeys.PINYIN_SEPARATOR,
        BatchRenameOptionKeys.PINYIN_CASE,
        BatchRenameOptionKeys.PINYIN_INITIALS,
    )

    private const val PINYIN_VARIABLE = "\${pinyin}"

    /** Longest first, like the plugin. zh-CN: 与插件一致, 长名称在前. */
    private val METADATA_VARIABLES = BatchRenameValues.METADATA_VARIABLES.sortedByDescending { it.length }

    /** Keys a `RegExp` search decides. zh-CN: 由 `RegExp` 形式的查找决定的键. */
    private val REGEXP_OPTIONS = setOf(
        BatchRenameOptionKeys.SEARCH,
        BatchRenameOptionKeys.REGEX,
        BatchRenameOptionKeys.REGEX_FLAGS,
        BatchRenameOptionKeys.MATCH_ALL,
        BatchRenameOptionKeys.CASE_SENSITIVE,
    )

    /** Regular expression flags the contract carries besides `g` and `i`. zh-CN: 契约在 `g` 与 `i` 之外携带的标志. */
    private const val CARRIED_FLAGS = "msuy"

    /** Flags that do not change what a regular expression matches. zh-CN: 不影响匹配结果的标志. */
    private const val IGNORED_FLAGS = "gid"

    /** `paths`: a path string or an array of them. zh-CN: `paths`: 路径字符串或其数组. */
    fun paths(value: JsonElement?): List<String> {
        val elements = when {
            value == null || value.isJsonNull -> invalidPaths("paths must be a path string or an array of path strings")
            value.isJsonArray -> value.asJsonArray.toList()
            else -> listOf(value)
        }
        if (elements.isEmpty()) invalidPaths("paths must not be empty")
        return elements.map { element ->
            val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
                ?: invalidPaths("every path must be a string, got $element")
            text.ifBlank { invalidPaths("paths must not be blank") }
        }
    }

    /**
     * The complete contract option map of `options`. [regExp] is the `RegExp` the script passed as `search`, which
     * decides `regex`, `matchAll` and `caseSensitive` (roadmap D17).
     * zh-CN: `options` 对应的完整契约选项表. [regExp] 为脚本作为 `search` 传入的 `RegExp`, 由它决定 `regex`, `matchAll` 与 `caseSensitive`.
     */
    fun options(value: JsonElement?, regExp: BatchRenameRegExp?): LinkedHashMap<String, Any> {
        val json = value?.takeUnless { it.isJsonNull }?.let {
            it as? JsonObject ?: invalidOptions("options must be an object")
        } ?: invalidOptions("options with a search are required")
        val preset = presetName(json)
        val complete = complete(json, regExp, searchRequired = preset == null)
        if (preset == null) return complete
        // A preset fills what the script leaves out, so only the given keys travel (plugin roadmap D36).
        // zh-CN: 预设补齐脚本未给出的选项, 因此只发送脚本给出的键 (插件路线图 D36).
        return LinkedHashMap<String, Any>().apply {
            put(BatchRenameOptionKeys.PRESET, preset)
            complete.forEach { (key, option) ->
                if (json.get(key)?.isJsonNull == false || (regExp != null && key in REGEXP_OPTIONS)) put(key, option)
            }
        }
    }

    /**
     * `preview(nameOrItems, options)` (plugin roadmap D37): a name, `{ name, isDirectory, lastModified }` or an array of
     * them; the flag tells whether the script passed an array, so the answer is one too. `lastModified` takes epoch
     * milliseconds or the ISO text `JSON.stringify` makes of a `Date`.
     * zh-CN: `preview(nameOrItems, options)` (插件路线图 D37): 名称, `{ name, isDirectory, lastModified }` 或其数组; 标志表示脚本是否
     * 传入数组, 结果随之为数组. `lastModified` 接受毫秒时间戳, 或 `JSON.stringify` 由 `Date` 得到的 ISO 文本.
     */
    fun previewItems(value: JsonElement?): Pair<List<BatchRenamePreviewItem>, Boolean> {
        if (value == null || value.isJsonNull) invalidOptions("preview needs a name, an item { name, isDirectory, lastModified } or an array of them")
        if (!value.isJsonArray) return listOf(previewItem(value, "the item")) to false
        val array = value.asJsonArray
        if (array.size() > BatchRenameLimits.MAX_ITEMS_PER_PLAN) {
            batchRenameFailure(BatchRenameScriptCodes.LIMIT_EXCEEDED, "${array.size()} names exceed ${BatchRenameLimits.MAX_ITEMS_PER_PLAN}")
        }
        return array.mapIndexed { index, element -> previewItem(element, "item $index") } to true
    }

    private fun previewItem(element: JsonElement, subject: String): BatchRenamePreviewItem {
        val item = when {
            element is JsonPrimitive && element.isString -> BatchRenamePreviewItem(element.asString)
            element is JsonObject -> BatchRenamePreviewItem(
                name = (element.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.asString ?: invalidOptions("$subject needs a string name"),
                directory = element.get("isDirectory")?.takeUnless { it.isJsonNull }?.let {
                    (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isBoolean }?.asBoolean ?: invalidOptions("isDirectory of $subject must be a boolean")
                } ?: false,
                lastModified = element.get("lastModified")?.takeUnless { it.isJsonNull }?.let { epochMillis(it, subject) },
            )
            else -> invalidOptions("$subject must be a name or { name, isDirectory, lastModified }, got $element")
        }
        val name = item.name
        if (name.isEmpty() || name == "." || name == ".." || '/' in name || name.any { it.code == 0 } || name.length > BatchRenameLimits.MAX_NAME_CHARS) {
            invalidOptions("the name of $subject must be one file name of 1 to ${BatchRenameLimits.MAX_NAME_CHARS} characters without \"/\", got \"$name\"")
        }
        return item
    }

    private fun epochMillis(element: JsonElement, subject: String): Long {
        val primitive = element as? JsonPrimitive
        return when {
            primitive != null && primitive.isNumber && primitive.asDouble == Math.floor(primitive.asDouble) -> primitive.asLong
            primitive != null && primitive.isString -> try {
                Instant.parse(primitive.asString).toEpochMilli()
            } catch (_: DateTimeParseException) {
                invalidOptions("lastModified of $subject must be epoch milliseconds or a Date, got $element")
            }
            else -> invalidOptions("lastModified of $subject must be epoch milliseconds or a Date, got $element")
        }
    }

    /**
     * The complete option map a plan starts from: the defaults, then [preset] (the complete options the plugin listed
     * for it), then the options the script gave besides `preset`.
     * zh-CN: 方案起始的完整选项表: 默认值, 然后是 [preset] (插件为其列出的完整选项), 最后是脚本给出的 `preset` 以外的选项.
     */
    fun withPreset(options: Map<String, Any>, preset: Map<String, Any>): LinkedHashMap<String, Any> {
        val result = complete(JsonObject().apply { addProperty(BatchRenameOptionKeys.SEARCH, "") }, null, searchRequired = true)
        preset.forEach { (key, value) -> if (key in result) result[key] = value }
        options.forEach { (key, value) -> if (key != BatchRenameOptionKeys.PRESET) result[key] = value }
        return result
    }

    /**
     * `importPresets(json, { onConflict })`: the conflict policy, skip when missing.
     * zh-CN: `importPresets(json, { onConflict })`: 同名策略, 缺省为 skip.
     */
    fun presetConflict(value: JsonElement?): String {
        val json = value?.takeUnless { it.isJsonNull }?.let {
            it as? JsonObject ?: invalidOptions("importPresets options must be an object")
        } ?: return BatchRenameValues.PRESET_CONFLICT_SKIP
        val element = json.get(BatchRenamePresetKeys.ON_CONFLICT)?.takeUnless { it.isJsonNull } ?: return BatchRenameValues.PRESET_CONFLICT_SKIP
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
        return text?.takeIf { it in BatchRenameValues.PRESET_CONFLICTS }
            ?: invalidOptions("onConflict must be one of ${BatchRenameValues.PRESET_CONFLICTS.joinToString { "\"$it\"" }}, got $element")
    }

    private fun presetName(json: JsonObject): String? {
        val element = json.get(BatchRenameOptionKeys.PRESET)?.takeUnless { it.isJsonNull } ?: return null
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
        return name?.takeIf { it.isNotBlank() } ?: invalidOptions("preset must be the name of a preset, got $element")
    }

    private fun complete(json: JsonObject, regExp: BatchRenameRegExp?, searchRequired: Boolean): LinkedHashMap<String, Any> {
        val result = LinkedHashMap<String, Any>()
        if (regExp != null) {
            val unsupported = regExp.flags.filter { it !in CARRIED_FLAGS && it !in IGNORED_FLAGS }
            if (unsupported.isNotEmpty()) invalidOptions("the RegExp flag \"$unsupported\" is not supported")
            result[BatchRenameOptionKeys.SEARCH] = regExp.source
            result[BatchRenameOptionKeys.REPLACE] = text(json, BatchRenameOptionKeys.REPLACE)
            result[BatchRenameOptionKeys.REGEX] = true
            result[BatchRenameOptionKeys.REGEX_FLAGS] = regExp.flags.filter { it in CARRIED_FLAGS }
            result[BatchRenameOptionKeys.MATCH_ALL] = 'g' in regExp.flags
            result[BatchRenameOptionKeys.CASE_SENSITIVE] = 'i' !in regExp.flags
        } else {
            val search = json.get(BatchRenameOptionKeys.SEARCH)
                ?.takeUnless { it.isJsonNull }
                ?: if (searchRequired) invalidOptions("search is required: a string or a RegExp") else JsonPrimitive("")
            result[BatchRenameOptionKeys.SEARCH] = (search as? JsonPrimitive)?.takeIf { it.isString }?.asString
                ?: invalidOptions("search must be a string or a RegExp")
            result[BatchRenameOptionKeys.REPLACE] = text(json, BatchRenameOptionKeys.REPLACE)
            result[BatchRenameOptionKeys.REGEX] = boolean(json, BatchRenameOptionKeys.REGEX)
            result[BatchRenameOptionKeys.REGEX_FLAGS] = ""
            result[BatchRenameOptionKeys.MATCH_ALL] = boolean(json, BatchRenameOptionKeys.MATCH_ALL)
            result[BatchRenameOptionKeys.CASE_SENSITIVE] = boolean(json, BatchRenameOptionKeys.CASE_SENSITIVE)
        }
        listOf(
            BatchRenameOptionKeys.TARGET,
            BatchRenameOptionKeys.INCLUDE_FILES,
            BatchRenameOptionKeys.INCLUDE_FOLDERS,
            BatchRenameOptionKeys.INCLUDE_SUBFOLDERS,
            BatchRenameOptionKeys.FORMAT,
            BatchRenameOptionKeys.ENUMERATE,
            BatchRenameOptionKeys.RANDOM,
            BatchRenameOptionKeys.DATE_TIME,
            BatchRenameOptionKeys.NORMALIZE,
            BatchRenameOptionKeys.HALF_WIDTH,
            BatchRenameOptionKeys.SANITIZE,
            BatchRenameOptionKeys.SANITIZE_REPLACEMENT,
            BatchRenameOptionKeys.TRIM,
            BatchRenameOptionKeys.CONTINUE_NUMBERING,
            BatchRenameOptionKeys.ON_CONFLICT,
            BatchRenameOptionKeys.CONFLICT_NUMBER_PATTERN,
            *PINYIN_OPTIONS.toTypedArray(),
            BatchRenameOptionKeys.METADATA,
        ).forEach { key ->
            result[key] = when (key) {
                in BOOLEAN_OPTIONS -> boolean(json, key)
                in CHOICES -> choice(json, key)
                else -> text(json, key)
            }
        }
        return result
    }

    /**
     * [options] as a plugin with [features] receives them: without [BatchRenameValues.FEATURE_PINYIN] (plugins before
     * 1.1.0) the pinyin options are left out, and a script that uses pinyin, through the `pinyin` option or the
     * `${pinyin}` variable of `replace`, fails with `PLUGIN_INCOMPATIBLE` instead of keeping its Chinese names.
     * zh-CN: 声明 [features] 的插件收到的 [options]: 没有 [BatchRenameValues.FEATURE_PINYIN] (1.1.0 之前的插件) 时省去拼音选项;
     * 脚本通过 `pinyin` 选项或 `replace` 中的 `${pinyin}` 变量用到拼音时以 `PLUGIN_INCOMPATIBLE` 失败, 而不是保留中文名称.
     */
    fun forPluginFeatures(options: Map<String, Any>, features: Collection<String>): Map<String, Any> {
        if (BatchRenameOptionKeys.PRESET in options && BatchRenameValues.FEATURE_PRESETS !in features) pluginWithoutPresets()
        val sent = if (BatchRenameValues.FEATURE_METADATA in features) options else {
            if (options[BatchRenameOptionKeys.METADATA] == true) {
                batchRenameFailure(
                    BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE,
                    "the installed Batch Rename plugin has no metadata variables; update it to 1.1.0 or later",
                )
            }
            options - BatchRenameOptionKeys.METADATA
        }
        return pinyinForPluginFeatures(sent, features)
    }

    /**
     * Whether [replace] holds a photo metadata variable (`$CAMERA_MAKE` ... `$COLOR_SPACE`) outside a `$$` escape, read
     * the way the plugin reads its variables.
     * zh-CN: [replace] 是否在 `$$` 转义之外含有照片元数据变量, 读法与插件读取变量的方式一致.
     */
    fun usesMetadataVariables(replace: String): Boolean {
        var index = 0
        while (index < replace.length) {
            when {
                replace.startsWith("$$", index) -> index += 2
                replace[index] == '$' && METADATA_VARIABLES.any { replace.startsWith(it, index + 1) } -> return true
                else -> index++
            }
        }
        return false
    }

    private fun pinyinForPluginFeatures(options: Map<String, Any>, features: Collection<String>): Map<String, Any> {
        if (BatchRenameValues.FEATURE_PINYIN in features) return options
        if (options[BatchRenameOptionKeys.PINYIN] == true || usesPinyinVariable(options[BatchRenameOptionKeys.REPLACE] as? String ?: "")) {
            batchRenameFailure(
                BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE,
                "the installed Batch Rename plugin does not convert to pinyin; update it to 1.1.0 or later",
            )
        }
        return options.filterKeys { it !in PINYIN_OPTIONS }
    }

    /**
     * Whether [replace] holds `${pinyin}` outside a `$$` escape, read the way the plugin reads its variables.
     * zh-CN: [replace] 是否在 `$$` 转义之外含有 `${pinyin}`, 读法与插件读取变量的方式一致.
     */
    fun usesPinyinVariable(replace: String): Boolean {
        var index = 0
        while (index < replace.length) {
            when {
                replace.startsWith("$$", index) -> index += 2
                replace.startsWith(PINYIN_VARIABLE, index) -> return true
                else -> index++
            }
        }
        return false
    }

    /**
     * The `onConflict` override of `apply(plan, applyOptions)`, or null to keep the plan's policy.
     * zh-CN: `apply(plan, applyOptions)` 中覆盖的 `onConflict`, 为 null 时沿用方案的策略.
     */
    fun conflictOverride(value: JsonElement?): String? {
        val json = value?.takeUnless { it.isJsonNull }?.let {
            it as? JsonObject ?: invalidOptions("apply options must be an object")
        } ?: return null
        if (json.get(BatchRenameOptionKeys.ON_CONFLICT)?.isJsonNull != false) return null
        return choice(json, BatchRenameOptionKeys.ON_CONFLICT)
    }

    /** `undo(result | batchId)`: the batch ID. zh-CN: `undo(result | batchId)` 中的批次 ID. */
    fun batchId(value: JsonElement?): String {
        val element = (value as? JsonObject)?.get("batchId") ?: value
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
        return text?.takeIf(BatchRenameJournalStore::isValidBatchId)
            ?: batchRenameFailure(BatchRenameScriptCodes.UNDO_UNAVAILABLE, "undo needs a batch ID or the result of apply, got $value")
    }

    /** `history(limit?)`: 1 to [maxLimit], all of them when omitted. zh-CN: `history(limit?)`: 1 到 [maxLimit], 省略时为全部. */
    fun historyLimit(value: JsonElement?, maxLimit: Int): Int {
        if (value == null || value.isJsonNull) return maxLimit
        val number = (value as? JsonPrimitive)?.takeIf { it.isNumber }?.asDouble
        if (number == null || number != Math.floor(number) || number < 0) {
            invalidOptions("history limit must be a non-negative integer, got $value")
        }
        return number.coerceAtMost(maxLimit.toDouble()).toInt()
    }

    private fun boolean(json: JsonObject, key: String): Boolean {
        val element = json.get(key)?.takeUnless { it.isJsonNull } ?: return BOOLEAN_OPTIONS.getValue(key)
        return (element as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean
            ?: invalidOptions("$key must be a boolean, got $element")
    }

    private fun text(json: JsonObject, key: String): String {
        val element = json.get(key)?.takeUnless { it.isJsonNull } ?: return TEXT_OPTIONS.getValue(key)
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.asString
            ?: invalidOptions("$key must be a string, got $element")
    }

    private fun choice(json: JsonObject, key: String): String {
        val element = json.get(key)?.takeUnless { it.isJsonNull } ?: return DEFAULT_CHOICES.getValue(key)
        val primitive = element as? JsonPrimitive
        if (primitive != null && primitive.isBoolean && !primitive.asBoolean) {
            FALSE_CHOICES[key]?.let { return it }
        }
        val choices = CHOICES.getValue(key)
        val text = primitive?.takeIf { it.isString }?.asString
        return text?.takeIf { it in choices }
            ?: invalidOptions("$key must be one of ${choices.joinToString { "\"$it\"" }}${if (key in FALSE_CHOICES) " or false" else ""}, got $element")
    }

    /** A plugin before 1.1.0 keeps no presets. zh-CN: 1.1.0 之前的插件不保存预设. */
    fun pluginWithoutPresets(): Nothing = batchRenameFailure(
        BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE,
        "the installed Batch Rename plugin keeps no presets; update it to 1.1.0 or later",
    )

    private fun invalidOptions(message: String): Nothing = batchRenameFailure(BatchRenameScriptCodes.INVALID_OPTIONS, message)

    private fun invalidPaths(message: String): Nothing = batchRenameFailure(BatchRenameScriptCodes.INVALID_PATHS, message)
}
