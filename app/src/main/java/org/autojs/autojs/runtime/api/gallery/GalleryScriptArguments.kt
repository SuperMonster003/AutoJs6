package org.autojs.autojs.runtime.api.gallery

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.gallery.GalleryConvertOptions
import org.autojs.autojs.core.plugin.gallery.GalleryEncodeOptions
import org.autojs.autojs.core.plugin.gallery.GalleryErrorMapper
import org.autojs.autojs.core.plugin.gallery.GalleryPickerOptions
import org.autojs.autojs.core.plugin.gallery.GalleryPluginException
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import java.util.Locale
import kotlin.math.floor

/**
 * Argument normalization of the `gallery` script API (3-Maple Image roadmap M6, 10.1): the script
 * values arrive as Gson trees (the Rhino layer serializes each argument like `JSON.stringify`) and
 * leave as the request shapes of the host client. Unknown option keys are refused by name, ranges
 * follow the contract's ceilings and the converter's limits, and every refusal is an
 * `INVALID_ARGUMENT`. Output paths stay unresolved here; the script layer applies the working
 * directory rules. Nothing here touches Rhino or Android, so the JVM tests cover it.
 * zh-CN: `gallery` 脚本 API 的参数规范化 (3-Maple Image 路线图 M6, 10.1): 脚本值以 Gson 树到达 (Rhino 层像 `JSON.stringify` 一样序列化每个参数),
 * 离开时是宿主客户端的请求形态. 未知选项键按名拒绝, 范围遵循契约上限与转换器的限制, 每个拒绝都是 `INVALID_ARGUMENT`. 输出路径在这里不解析;
 * 工作目录规则由脚本层应用. 这里不触碰 Rhino 与 Android, 因此由 JVM 测试覆盖.
 */
internal object GalleryScriptArguments {

    /** `gallery.view(paths, options?)`: 1 to `MAX_VIEW_PAGES` script paths and the start page. zh-CN: `gallery.view(paths, options?)`: 1 到 `MAX_VIEW_PAGES` 个脚本路径与起始页. */
    class View(val paths: List<String>, val index: Int)

    /** `gallery.edit(path, options?)`: where the output goes and how it is encoded. zh-CN: `gallery.edit(path, options?)`: 输出去向与编码方式. */
    class Edit(val output: String?, val overwrite: Boolean, val encode: GalleryEncodeOptions)

    /** `gallery.convert(path, options?)`: where the output goes and the converter options. zh-CN: `gallery.convert(path, options?)`: 输出去向与转换器选项. */
    class Convert(val output: String?, val overwrite: Boolean, val options: GalleryConvertOptions)

    class Close(val finish: Boolean)

    const val MULTIPLE = "multiple"
    const val MAX_COUNT = "maxCount"
    const val MIME_TYPES = "mimeTypes"
    const val TITLE = "title"
    const val INDEX = "index"
    const val OUTPUT = "output"
    const val OVERWRITE = "overwrite"
    const val FORMAT = "format"
    const val QUALITY = "quality"
    const val KEEP_EXIF = "keepExif"
    const val KEEP_LOCATION = "keepLocation"
    const val LOSSLESS = "lossless"
    const val TARGET_BYTES = "targetBytes"
    const val SIZE_MODE = "sizeMode"
    const val PERCENT = "percent"
    const val LONG_EDGE = "longEdge"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val KEEP_ASPECT_RATIO = "keepAspectRatio"
    const val BACKGROUND = "background"
    const val FINISH = "finish"

    /** The largest integer a script number holds exactly (2^53 - 1). zh-CN: 脚本数字能精确表示的最大整数 (2^53 - 1). */
    const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    private val PICK_KEYS = setOf(MULTIPLE, MAX_COUNT, MIME_TYPES, TITLE)
    private val VIEW_KEYS = setOf(INDEX)
    private val ENCODE_KEYS = setOf(OUTPUT, OVERWRITE, FORMAT, QUALITY, KEEP_EXIF, KEEP_LOCATION)
    private val CONVERT_KEYS = ENCODE_KEYS + setOf(LOSSLESS, TARGET_BYTES, SIZE_MODE, PERCENT, LONG_EDGE, WIDTH, HEIGHT, KEEP_ASPECT_RATIO, BACKGROUND)
    private val CLOSE_KEYS = setOf(FINISH)

    /** `jpg` is accepted as an alias of `jpeg`. zh-CN: `jpg` 作为 `jpeg` 的别名接受. */
    private val FORMAT_ALIASES = mapOf("jpg" to GalleryContract.FORMAT_JPEG)

    /** `content://`, `file://`, `http://` and friends: the script side has no authorization model for them. zh-CN: `content://`, `file://`, `http://` 等: 脚本侧没有针对它们的授权模型. */
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+:")

    /** `type/subtype` of an image filter: the image wildcard or a concrete image type. zh-CN: 图片过滤的 `type/subtype`: 图片通配符或具体图片类型. */
    private val IMAGE_MIME_TYPE = Regex("^image/(\\*|[a-z0-9][a-z0-9!#$&^_.+-]{0,126})$")

    private val HEX_COLOR = Regex("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    /**
     * `gallery.pick(options?)`: single selection unless `multiple`; `maxCount` (multiple only) caps
     * the choice within `MAX_PICK_COUNT`; `mimeTypes` (a string or an array) narrows the images shown;
     * `title` names the picker.
     * zh-CN: `gallery.pick(options?)`: 除非 `multiple`, 否则单选; `maxCount` (仅多选) 在 `MAX_PICK_COUNT` 内限制选择数; `mimeTypes` (字符串或数组)
     * 缩小显示的图片; `title` 为选择器命名.
     */
    fun pick(options: JsonElement?): GalleryPickerOptions {
        val given = optionalObject(options, "options") ?: return GalleryPickerOptions()
        given.rejectUnknown("options", PICK_KEYS)
        val multiple = given.booleanOrNull("options", MULTIPLE) ?: false
        val maxCount = given.intOrNull("options", MAX_COUNT)
        if (maxCount != null) {
            if (!multiple) throw invalid("'options.maxCount' applies to multiple selection; set 'options.multiple' to true")
            if (maxCount !in 1..GalleryContract.MAX_PICK_COUNT) throw invalid("'options.maxCount' must be between 1 and ${GalleryContract.MAX_PICK_COUNT}, got $maxCount")
        }
        val mimeTypes = mimeTypes(given.get(MIME_TYPES), "options.mimeTypes")
        val title = given.stringOrNull("options", TITLE)?.trim()?.takeIf { it.isNotEmpty() }
        if (title != null && title.length > GalleryContract.MAX_TITLE_LENGTH) {
            throw invalid("'options.title' exceeds ${GalleryContract.MAX_TITLE_LENGTH} characters")
        }
        return GalleryPickerOptions(multiple, maxCount, mimeTypes, title)
    }

    /** `gallery.view(paths, options?)`: [paths] is one path or an array of them; `index` is the start page. zh-CN: `gallery.view(paths, options?)`: [paths] 为一个路径或路径数组; `index` 为起始页. */
    fun view(paths: JsonElement?, options: JsonElement?): View {
        val list = when {
            paths == null || paths.isJsonNull -> throw invalid("'paths' is required: an image path or an array of image paths")
            paths.isJsonArray -> paths.asJsonArray.mapIndexed { i, element -> path(element, "paths[$i]") }
            else -> listOf(path(paths, "paths"))
        }
        if (list.isEmpty()) throw invalid("'paths' must name at least one image")
        if (list.size > GalleryContract.MAX_VIEW_PAGES) {
            throw GalleryPluginException(GalleryErrorMapper.normalize(GalleryErrorCodes.LIMIT_EXCEEDED, "the viewer opens at most ${GalleryContract.MAX_VIEW_PAGES} images, not ${list.size}"))
        }
        val given = optionalObject(options, "options")
        given?.rejectUnknown("options", VIEW_KEYS)
        val index = given?.intOrNull("options", INDEX) ?: 0
        if (index !in list.indices) throw invalid("'options.index' must be between 0 and ${list.lastIndex}, got $index")
        return View(list, index)
    }

    /** `gallery.edit(path, options?)`: `output`, `overwrite`, `format`, `quality`, `keepExif` and `keepLocation`. zh-CN: `gallery.edit(path, options?)`: `output`, `overwrite`, `format`, `quality`, `keepExif` 与 `keepLocation`. */
    fun edit(options: JsonElement?): Edit {
        val given = optionalObject(options, "options") ?: return Edit(null, false, GalleryEncodeOptions())
        given.rejectUnknown("options", ENCODE_KEYS)
        return Edit(output(given), given.booleanOrNull("options", OVERWRITE) ?: false, encode(given))
    }

    /**
     * `gallery.convert(path, options?)`: the options of [edit] plus the converter's: `lossless`
     * (WebP), `targetBytes` (JPEG or lossy WebP, instead of `quality`), the size (`sizeMode`
     * `original`, `percent`, `longEdge` or `custom`, inferred from `percent`, `longEdge` or
     * `width` / `height` when absent) and `background` (the JPEG fill of transparent pixels, an ARGB
     * number or a `#RGB`, `#RRGGBB` or `#AARRGGBB` string).
     * zh-CN: `gallery.convert(path, options?)`: [edit] 的选项加上转换器的选项: `lossless` (WebP), `targetBytes` (JPEG 或有损 WebP, 代替 `quality`),
     * 尺寸 (`sizeMode` 为 `original`, `percent`, `longEdge` 或 `custom`, 省略时由 `percent`, `longEdge` 或 `width` / `height` 推断) 与 `background`
     * (JPEG 中透明像素的填充色, ARGB 数字或 `#RGB`, `#RRGGBB`, `#AARRGGBB` 字符串).
     */
    fun convert(options: JsonElement?): Convert {
        val given = optionalObject(options, "options") ?: return Convert(null, false, GalleryConvertOptions())
        given.rejectUnknown("options", CONVERT_KEYS)
        val encode = encode(given)
        val lossless = given.booleanOrNull("options", LOSSLESS) ?: false
        if (lossless && encode.format != null && encode.format != GalleryContract.FORMAT_WEBP) {
            throw invalid("'options.lossless' applies to WebP output, not ${encode.format}")
        }
        val targetBytes = given.longOrNull("options", TARGET_BYTES)
        if (targetBytes != null) {
            if (targetBytes < 1) throw invalid("'options.targetBytes' must be a positive integer, got $targetBytes")
            if (encode.format == GalleryContract.FORMAT_PNG || lossless) throw invalid("'options.targetBytes' applies to JPEG and lossy WebP output")
            if (encode.quality != null) throw invalid("'options.targetBytes' chooses the quality itself; leave out 'options.quality'")
        }
        val percent = given.intOrNull("options", PERCENT)
        val longEdge = given.intOrNull("options", LONG_EDGE)
        val width = given.intOrNull("options", WIDTH)
        val height = given.intOrNull("options", HEIGHT)
        val keepAspectRatio = given.booleanOrNull("options", KEEP_ASPECT_RATIO)
        val sizeMode = sizeMode(given.stringOrNull("options", SIZE_MODE), percent, longEdge, width, height)
        when (sizeMode) {
            GalleryContract.SIZE_PERCENT -> if (percent == null || percent !in GalleryContract.MIN_PERCENT..GalleryContract.MAX_PERCENT) {
                throw invalid("'options.percent' must be between ${GalleryContract.MIN_PERCENT} and ${GalleryContract.MAX_PERCENT}, got ${percent ?: "nothing"}")
            }
            GalleryContract.SIZE_LONG_EDGE -> if (longEdge == null || longEdge !in 1..GalleryContract.MAX_DIMENSION) {
                throw invalid("'options.longEdge' must be between 1 and ${GalleryContract.MAX_DIMENSION}, got ${longEdge ?: "nothing"}")
            }
            GalleryContract.SIZE_CUSTOM -> {
                if (width == null && height == null) throw invalid("'options.sizeMode' custom needs 'options.width' and/or 'options.height'")
                listOf(WIDTH to width, HEIGHT to height).forEach { (key, value) ->
                    if (value != null && value !in 1..GalleryContract.MAX_DIMENSION) throw invalid("'options.$key' must be between 1 and ${GalleryContract.MAX_DIMENSION}, got $value")
                }
                if (keepAspectRatio == false && (width == null || height == null)) {
                    throw invalid("'options.keepAspectRatio' false needs both 'options.width' and 'options.height'")
                }
            }
        }
        if (keepAspectRatio != null && sizeMode != GalleryContract.SIZE_CUSTOM) throw invalid("'options.keepAspectRatio' applies to the custom size mode")
        val background = background(given.get(BACKGROUND), "options.background")
        return Convert(
            output(given),
            given.booleanOrNull("options", OVERWRITE) ?: false,
            GalleryConvertOptions(
                encode = encode,
                lossless = lossless,
                targetBytes = targetBytes,
                sizeMode = sizeMode.takeUnless { it == GalleryContract.SIZE_ORIGINAL },
                percent = percent.takeIf { sizeMode == GalleryContract.SIZE_PERCENT },
                longEdge = longEdge.takeIf { sizeMode == GalleryContract.SIZE_LONG_EDGE },
                width = width.takeIf { sizeMode == GalleryContract.SIZE_CUSTOM },
                height = height.takeIf { sizeMode == GalleryContract.SIZE_CUSTOM },
                keepAspectRatio = keepAspectRatio ?: true,
                background = background,
            ),
        )
    }

    /** `session.close(options?)`: `finish` (default true) also closes the viewer. zh-CN: `session.close(options?)`: `finish` (默认 true) 同时关闭查看器. */
    fun close(options: JsonElement?): Close {
        val given = optionalObject(options, "options")
        given?.rejectUnknown("options", CLOSE_KEYS)
        return Close(given?.booleanOrNull("options", FINISH) ?: true)
    }

    /** A script path: a non-blank string that is not a URI; the caller applies the working-directory rules. zh-CN: 脚本路径: 非空白且不是 URI 的字符串; 工作目录规则由调用方应用. */
    fun path(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (URI_SCHEME.containsMatchIn(text)) {
            throw invalid("'$label' must be a file path (relative to the script directory or absolute); URIs such as content:// are not accepted, got $text")
        }
        return text
    }

    /** An `INVALID_ARGUMENT` failure as the script sees it. zh-CN: 脚本眼中的 `INVALID_ARGUMENT` 失败. */
    fun invalid(message: String): GalleryPluginException =
        GalleryPluginException(GalleryErrorMapper.normalize(GalleryErrorCodes.INVALID_ARGUMENT, message))

    // ------------------------------------------------------------------ parts

    private fun output(options: JsonObject): String? =
        options.get(OUTPUT)?.takeUnless { it.isJsonNull }?.let { path(it, "options.output") }

    private fun encode(options: JsonObject): GalleryEncodeOptions {
        val format = options.stringOrNull("options", FORMAT)?.trim()?.lowercase(Locale.ROOT)?.let { FORMAT_ALIASES[it] ?: it }
        if (format != null && format !in GalleryContract.FORMATS) {
            throw invalid("'options.format' must be one of ${GalleryContract.FORMATS.joinToString()}, got $format")
        }
        val quality = options.intOrNull("options", QUALITY)
        if (quality != null && quality !in GalleryContract.MIN_QUALITY..GalleryContract.MAX_QUALITY) throw invalid("'options.quality' must be between ${GalleryContract.MIN_QUALITY} and ${GalleryContract.MAX_QUALITY}, got $quality")
        return GalleryEncodeOptions(
            format = format,
            quality = quality,
            keepExif = options.booleanOrNull("options", KEEP_EXIF) ?: false,
            keepLocation = options.booleanOrNull("options", KEEP_LOCATION) ?: false,
        )
    }

    private fun mimeTypes(value: JsonElement?, label: String): List<String> {
        if (value == null || value.isJsonNull) return emptyList()
        val raw = when {
            value.isJsonArray -> value.asJsonArray.mapIndexed { i, element -> string(element, "$label[$i]") }
            else -> listOf(string(value, label))
        }
        val normalized = raw.map { it.trim().lowercase(Locale.ROOT) }.distinct()
        normalized.firstOrNull { !IMAGE_MIME_TYPE.matches(it) }?.let { throw invalid("'$label' must hold image types such as image/png or image/*, got $it") }
        if (normalized.size > GalleryContract.MAX_MIME_TYPES) throw invalid("'$label' holds at most ${GalleryContract.MAX_MIME_TYPES} types, got ${normalized.size}")
        return normalized
    }

    private fun sizeMode(given: String?, percent: Int?, longEdge: Int?, width: Int?, height: Int?): String {
        val implied = buildList {
            if (percent != null) add(GalleryContract.SIZE_PERCENT)
            if (longEdge != null) add(GalleryContract.SIZE_LONG_EDGE)
            if (width != null || height != null) add(GalleryContract.SIZE_CUSTOM)
        }
        if (given != null) {
            if (given !in GalleryContract.SIZE_MODES) throw invalid("'options.sizeMode' must be one of ${GalleryContract.SIZE_MODES.joinToString()}, got $given")
            implied.firstOrNull { it != given }?.let { throw invalid("'options.sizeMode' $given does not use the size options of $it") }
            return given
        }
        if (implied.size > 1) throw invalid("the size options name more than one size mode (${implied.joinToString()}); keep one of 'percent', 'longEdge' or 'width' / 'height'")
        return implied.firstOrNull() ?: GalleryContract.SIZE_ORIGINAL
    }

    /** An ARGB number or a `#RGB`, `#RRGGBB` (opaque) or `#AARRGGBB` string. zh-CN: ARGB 数字或 `#RGB`, `#RRGGBB` (不透明), `#AARRGGBB` 字符串. */
    private fun background(value: JsonElement?, label: String): Int? {
        if (value == null || value.isJsonNull) return null
        val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: throw invalid("'$label' must be an ARGB number or a color string such as #ffffff, got ${brief(value)}")
        if (primitive.isNumber) {
            val number = primitive.asDouble
            if (number.isNaN() || number != floor(number) || number < Int.MIN_VALUE || number > 0xFFFFFFFFL) {
                throw invalid("'$label' must be an ARGB integer, got ${primitive.asString}")
            }
            return number.toLong().toInt()
        }
        val text = primitive.takeIf { it.isString }?.asString?.trim()
            ?: throw invalid("'$label' must be an ARGB number or a color string such as #ffffff, got ${brief(value)}")
        val digits = HEX_COLOR.matchEntire(text)?.groupValues?.get(1) ?: throw invalid("'$label' must look like #RGB, #RRGGBB or #AARRGGBB, got $text")
        val expanded = when (digits.length) {
            3 -> "FF" + digits.map { "$it$it" }.joinToString("")
            6 -> "FF$digits"
            else -> digits
        }
        return expanded.toLong(16).toInt()
    }

    // ------------------------------------------------------------------ helpers

    private fun string(value: JsonElement?, label: String): String {
        if (value == null || value.isJsonNull) throw invalid("'$label' is required")
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun integral(value: JsonElement, label: String, min: Double, max: Double): Double {
        val number = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asDouble
            ?: throw invalid("'$label' must be an integer, got ${brief(value)}")
        if (number.isNaN() || number.isInfinite() || number != floor(number) || number < min || number > max) {
            throw invalid("'$label' must be an integer, got ${value.asJsonPrimitive.asString}")
        }
        return number
    }

    private fun optionalObject(value: JsonElement?, label: String): JsonObject? {
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonObject) throw invalid("'$label' must be an object, got ${brief(value)}")
        return value.asJsonObject
    }

    private fun JsonObject.rejectUnknown(label: String, known: Set<String>) {
        keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown option '$label.$key'") }
    }

    private fun JsonObject.stringOrNull(label: String, key: String): String? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return string(value, "$label.$key")
    }

    private fun JsonObject.intOrNull(label: String, key: String): Int? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return integral(value, "$label.$key", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }

    private fun JsonObject.longOrNull(label: String, key: String): Long? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return integral(value, "$label.$key", -MAX_SAFE_INTEGER.toDouble(), MAX_SAFE_INTEGER.toDouble()).toLong()
    }

    private fun JsonObject.booleanOrNull(label: String, key: String): Boolean? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) throw invalid("'$label.$key' must be a boolean, got ${brief(value)}")
        return value.asBoolean
    }

    private fun brief(value: JsonElement): String = when {
        value.isJsonNull -> "null"
        value.isJsonArray -> "an array"
        value.isJsonObject -> "an object"
        value.asJsonPrimitive.isString -> "\"${value.asString.take(40)}\""
        else -> value.asJsonPrimitive.asString
    }
}
