package org.autojs.autojs.theme.intelligent

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.theme.intelligent.calendar.HolidayCalendar

/**
 * Storage codec of [IntelligentConfig] (the `config_json` column), versioned by [IntelligentConfig.VERSION].
 * zh-CN: [IntelligentConfig] 的存储编解码 (`config_json` 列), 版本号为 [IntelligentConfig.VERSION].
 *
 * Decoding is tolerant: blank or malformed input yields the default configuration, unknown fields are
 * ignored, and unknown types become `Unsupported` values whose raw JSON is written back unchanged.
 * Override targets are group row ids here; the exchange format (roadmap appendix B) maps them to indices.
 * zh-CN: 解码是宽容的: 空白或格式错误的输入得到默认配置, 未知字段被忽略, 未知类型成为 `Unsupported` 值,
 * 其原始 JSON 在编码时原样写回. 这里覆盖层的目标是组的行 id; 交换格式 (Roadmap 附录 B) 会将其映射为下标.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntelligentConfigJson {

    private const val KEY_VERSION = "version"
    private const val KEY_STATE_SOURCE = "stateSource"
    private const val KEY_ROTATION = "rotation"
    private const val KEY_ORDER = "order"
    private const val KEY_OVERRIDES = "overrides"
    private const val KEY_TYPE = "type"

    private const val TYPE_NONE = "none"
    private const val TYPE_NIGHT_MODE = "nightMode"
    private const val TYPE_TIME_PERIODS = "timePeriods"
    private const val TYPE_SUNRISE_SUNSET = "sunriseSunset"
    private const val TYPE_WEEKDAY = "weekday"
    private const val TYPE_SEASON = "season"
    private const val TYPE_BATTERY = "battery"
    private const val TYPE_CHARGING = "charging"
    private const val TYPE_POWER_SAVE = "powerSave"
    private const val TYPE_ACCESSIBILITY_SERVICE = "accessibilityService"
    private const val TYPE_SCRIPT_RUNNING = "scriptRunning"
    private const val TYPE_STATE_CHANGE = "stateChange"
    private const val TYPE_APP_START = "appStart"
    private const val TYPE_INTERVAL = "interval"
    private const val TYPE_DAILY = "daily"
    private const val TYPE_DATE_RANGE = "dateRange"
    private const val TYPE_HOLIDAY = "holiday"

    private const val WEEKDAY_MODE_DAYS = "days"
    private const val WEEKDAY_MODE_WORKDAYS = "workdays"

    private const val MODE_SEQUENTIAL = "sequential"
    private const val MODE_RANDOM = "random"

    private val DAYS_IN_MONTH = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    fun encode(config: IntelligentConfig): String = toJson(config).toString()

    fun toJson(config: IntelligentConfig) = JsonObject().apply {
        addProperty(KEY_VERSION, IntelligentConfig.VERSION)
        add(KEY_STATE_SOURCE, encodeStateSource(config.stateSource))
        add(KEY_ROTATION, JsonArray().apply { config.rotation.forEach { add(encodeTrigger(it)) } })
        add(KEY_ORDER, encodeOrder(config.order))
        add(KEY_OVERRIDES, JsonArray().apply { config.overrides.forEach { add(encodeOverride(it)) } })
    }

    fun decode(json: String?): IntelligentConfig {
        if (json.isNullOrBlank()) return IntelligentConfig()
        val root = runCatching { JsonParser.parseString(json) }.getOrNull()?.objectOrNull() ?: return IntelligentConfig()
        return fromJson(root)
    }

    fun fromJson(root: JsonObject) = IntelligentConfig(
        stateSource = root.objectOrNull(KEY_STATE_SOURCE)?.let(::decodeStateSource) ?: StateSource.None,
        rotation = root.arrayOrNull(KEY_ROTATION)?.mapNotNull { it.objectOrNull()?.let(::decodeTrigger) } ?: emptyList(),
        order = root.objectOrNull(KEY_ORDER)?.let(::decodeOrder) ?: GroupOrder.Sequential(),
        overrides = root.arrayOrNull(KEY_OVERRIDES)?.mapNotNull { it.objectOrNull()?.let(::decodeOverride) } ?: emptyList(),
    )

    private fun encodeStateSource(source: StateSource): JsonElement = when (source) {
        StateSource.None -> typed(TYPE_NONE)
        StateSource.NightMode -> typed(TYPE_NIGHT_MODE)
        is StateSource.TimePeriods -> typed(TYPE_TIME_PERIODS).apply { add("starts", times(source.starts)) }
        is StateSource.SunriseSunset -> typed(TYPE_SUNRISE_SUNSET).apply {
            source.location?.let {
                addProperty("latitude", it.latitude)
                addProperty("longitude", it.longitude)
            }
        }
        is StateSource.Weekday -> typed(TYPE_WEEKDAY).apply {
            addProperty("mode", if (source.workdaysOnly) WEEKDAY_MODE_WORKDAYS else WEEKDAY_MODE_DAYS)
        }
        is StateSource.Seasons -> typed(TYPE_SEASON).apply { source.hemisphere?.let { addProperty("hemisphere", it.key) } }
        is StateSource.Battery -> typed(TYPE_BATTERY).apply { add("thresholds", JsonArray().apply { source.thresholds.forEach { add(it) } }) }
        StateSource.Charging -> typed(TYPE_CHARGING)
        StateSource.PowerSave -> typed(TYPE_POWER_SAVE)
        StateSource.AccessibilityService -> typed(TYPE_ACCESSIBILITY_SERVICE)
        StateSource.ScriptRunning -> typed(TYPE_SCRIPT_RUNNING)
        is StateSource.Unsupported -> raw(source.raw, source.type)
    }

    private fun decodeStateSource(json: JsonObject): StateSource = when (val type = json.string(KEY_TYPE)) {
        null, TYPE_NONE -> StateSource.None
        TYPE_NIGHT_MODE -> StateSource.NightMode
        TYPE_TIME_PERIODS -> parseTimes(json.arrayOrNull("starts"))?.let(StateSource.TimePeriods::normalized)
            ?: StateSource.Unsupported(type, json.toString())
        TYPE_SUNRISE_SUNSET -> {
            val latitude = json.double("latitude")
            val longitude = json.double("longitude")
            when {
                latitude == null && longitude == null -> StateSource.SunriseSunset()
                latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0 ->
                    StateSource.SunriseSunset(GeoLocation(latitude, longitude))
                else -> StateSource.Unsupported(type, json.toString())
            }
        }
        TYPE_WEEKDAY -> when (json.string("mode")) {
            null, WEEKDAY_MODE_DAYS -> StateSource.Weekday(workdaysOnly = false)
            WEEKDAY_MODE_WORKDAYS -> StateSource.Weekday(workdaysOnly = true)
            else -> StateSource.Unsupported(type, json.toString())
        }
        TYPE_SEASON -> StateSource.Seasons(Hemisphere.of(json.string("hemisphere")))
        TYPE_BATTERY -> json.arrayOrNull("thresholds")?.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.takeIf { it == Math.floor(it) }?.toInt()
        }?.let(StateSource.Battery::normalized) ?: StateSource.Unsupported(type, json.toString())
        TYPE_CHARGING -> StateSource.Charging
        TYPE_POWER_SAVE -> StateSource.PowerSave
        TYPE_ACCESSIBILITY_SERVICE -> StateSource.AccessibilityService
        TYPE_SCRIPT_RUNNING -> StateSource.ScriptRunning
        else -> StateSource.Unsupported(type, json.toString())
    }

    /** Minutes of the day as `HH:mm` strings. zh-CN: 一天中的分钟数, 写为 `HH:mm` 字符串. */
    private fun times(minutes: List<Int>) = JsonArray().apply { minutes.forEach { add("%02d:%02d".format(it / 60, it % 60)) } }

    /** Parses `HH:mm` strings, skipping invalid ones; null when the array is missing. zh-CN: 解析 `HH:mm` 字符串并跳过无效项; 数组缺失时返回 null. */
    private fun parseTimes(array: JsonArray?): List<Int>? = array?.mapNotNull { element ->
        val text = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return@mapNotNull null
        val parts = text.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 2 || hour == null || minute == null || hour !in 0..23 || minute !in 0..59) null else hour * 60 + minute
    }

    private fun encodeTrigger(trigger: RotationTrigger): JsonElement = when (trigger) {
        RotationTrigger.StateChange -> typed(TYPE_STATE_CHANGE)
        RotationTrigger.AppStart -> typed(TYPE_APP_START)
        is RotationTrigger.Interval -> typed(TYPE_INTERVAL).apply {
            addProperty("every", trigger.every)
            addProperty("unit", trigger.unit.key)
            addProperty("anchor", trigger.anchor.key)
        }
        is RotationTrigger.Daily -> typed(TYPE_DAILY).apply { add("times", times(trigger.times)) }
        is RotationTrigger.Unsupported -> raw(trigger.raw, trigger.type)
    }

    private fun decodeTrigger(json: JsonObject): RotationTrigger {
        val type = json.string(KEY_TYPE) ?: ""
        return when (type) {
            TYPE_STATE_CHANGE -> RotationTrigger.StateChange
            TYPE_APP_START -> RotationTrigger.AppStart
            TYPE_INTERVAL -> {
                val unit = IntervalUnit.of(json.string("unit")) ?: return RotationTrigger.Unsupported(type, json.toString())
                RotationTrigger.Interval(
                    every = RotationTrigger.Interval.normalizedEvery(json.int("every") ?: 1, unit),
                    unit = unit,
                    anchor = IntervalAnchor.of(json.string("anchor")) ?: IntervalAnchor.NOW,
                )
            }
            TYPE_DAILY -> parseTimes(json.arrayOrNull("times"))?.let(RotationTrigger.Daily::normalized)
                ?: RotationTrigger.Unsupported(type, json.toString())
            else -> RotationTrigger.Unsupported(type, json.toString())
        }
    }

    private fun encodeOrder(order: GroupOrder): JsonElement = when (order) {
        GroupOrder.Random -> JsonObject().apply { addProperty("mode", MODE_RANDOM) }
        is GroupOrder.Sequential -> JsonObject().apply {
            addProperty("mode", MODE_SEQUENTIAL)
            addProperty("key", order.key.key)
            order.referenceState?.let { addProperty("referenceState", it) }
            addProperty("descending", order.descending)
        }
    }

    private fun decodeOrder(json: JsonObject): GroupOrder = when (json.string("mode")) {
        MODE_RANDOM -> GroupOrder.Random
        else -> GroupOrder.Sequential(
            key = SortKey.of(json.string("key")) ?: SortKey.MANUAL,
            referenceState = json.string("referenceState"),
            descending = json.boolean("descending") ?: false,
        )
    }

    private fun encodeOverride(override: DateOverride) = JsonObject().apply {
        add("when", encodeCondition(override.condition))
        addProperty("group", override.groupId)
    }

    private fun decodeOverride(json: JsonObject): DateOverride? {
        val groupId = json.long("group") ?: return null
        val condition = json.objectOrNull("when")?.let(::decodeCondition) ?: return null
        return DateOverride(condition, groupId)
    }

    private fun encodeCondition(condition: DateCondition): JsonElement = when (condition) {
        is DateCondition.DateRange -> typed(TYPE_DATE_RANGE).apply {
            addProperty("from", monthDay(condition.fromMonth, condition.fromDay))
            addProperty("to", monthDay(condition.toMonth, condition.toDay))
            condition.year?.let { addProperty("year", it) }
        }
        is DateCondition.Holiday -> typed(TYPE_HOLIDAY).apply {
            addProperty("id", condition.id)
            if (condition.daysBefore > 0) addProperty("before", condition.daysBefore)
            if (condition.daysAfter > 0) addProperty("after", condition.daysAfter)
        }
        is DateCondition.Unsupported -> raw(condition.raw, condition.type)
    }

    private fun decodeCondition(json: JsonObject): DateCondition {
        val type = json.string(KEY_TYPE) ?: ""
        if (type == TYPE_HOLIDAY) {
            // Holidays unknown to this version stay unsupported. zh-CN: 当前版本不认识的节日保持为不受支持.
            val id = json.string("id")?.takeIf { HolidayCalendar.find(it) != null } ?: return DateCondition.Unsupported(type, json.toString())
            fun extra(key: String) = (json.int(key) ?: 0).coerceIn(0, DateCondition.Holiday.MAX_EXTRA_DAYS)
            return DateCondition.Holiday(id, extra("before"), extra("after"))
        }
        if (type != TYPE_DATE_RANGE) return DateCondition.Unsupported(type, json.toString())
        val from = parseMonthDay(json.string("from"))
        val to = parseMonthDay(json.string("to"))
        if (from == null || to == null) return DateCondition.Unsupported(type, json.toString())
        return DateCondition.DateRange(from.first, from.second, to.first, to.second, json.int("year"))
    }

    private fun monthDay(month: Int, day: Int) = "%02d-%02d".format(month, day)

    private fun parseMonthDay(text: String?): Pair<Int, Int>? {
        val parts = text?.split('-') ?: return null
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull() ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..DAYS_IN_MONTH[month - 1]) return null
        return month to day
    }

    private fun typed(type: String) = JsonObject().apply { addProperty(KEY_TYPE, type) }

    private fun raw(raw: String, type: String): JsonElement {
        return runCatching { JsonParser.parseString(raw) }.getOrNull()?.objectOrNull() ?: typed(type)
    }

    private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.objectOrNull()

    private fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.primitive(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive

    private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.asString

    private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.takeIf { it.isBoolean }?.asBoolean

    private fun JsonObject.long(key: String): Long? = primitive(key)?.takeIf { it.isNumber }?.asNumber?.toDouble()
        ?.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toLong()

    private fun JsonObject.int(key: String): Int? = long(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun JsonObject.double(key: String): Double? = primitive(key)?.takeIf { it.isNumber }?.asDouble?.takeIf { it.isFinite() }

}
