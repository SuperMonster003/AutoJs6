package org.autojs.autojs.runtime.api.modules.notice

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * Argument checks shared by `notice` and `notice.channel` (roadmap P3.2). A checked options object is a new object whose
 * prototype is the script's object, with the checked properties defined on it in the form the current module
 * (`augment/notice/Notice`, `Channel`) reads: a documented word as its integer constant, a string of any kind as a
 * String, a Java object without its wrapper.
 *
 * zh-CN: notice 与 notice.channel 共用的参数检查 (P3.2). 检查后的选项对象是以脚本对象为原型的新对象, 检查过的属性按现有模块 (augment/notice/Notice, Channel)
 * 读取的形式定义在其上: 文档所列单词换成对应的整数常量, 各种字符串换成 String, Java 对象去掉包装.
 */
internal object NoticeValues {

    val PRIORITIES = mapOf(
        "default" to NotificationCompat.PRIORITY_DEFAULT,
        "low" to NotificationCompat.PRIORITY_LOW,
        "min" to NotificationCompat.PRIORITY_MIN,
        "high" to NotificationCompat.PRIORITY_HIGH,
        "max" to NotificationCompat.PRIORITY_MAX,
    )

    val IMPORTANCES = mapOf(
        "unspecified" to NotificationManager.IMPORTANCE_UNSPECIFIED,
        "none" to NotificationManager.IMPORTANCE_NONE,
        "min" to NotificationManager.IMPORTANCE_MIN,
        "low" to NotificationManager.IMPORTANCE_LOW,
        "default" to NotificationManager.IMPORTANCE_DEFAULT,
        "high" to NotificationManager.IMPORTANCE_HIGH,
        "max" to NotificationManager.IMPORTANCE_MAX,
    )

    /** `'no_override'` is documented but unknown to the current module, which only reads the number. */
    val LOCKSCREEN_VISIBILITIES = mapOf(
        "public" to Notification.VISIBILITY_PUBLIC,
        "private" to Notification.VISIBILITY_PRIVATE,
        "secret" to Notification.VISIBILITY_SECRET,
        "no_override" to NOTIFICATION_VISIBILITY_NO_OVERRIDE,
    )

    /** The documented targets of `appendScriptName`, matched in any case, in their documented spelling. */
    val APPEND_TARGETS = listOf("auto", "title", "content", "bigContent").associateBy { it.lowercase() }

    const val BIG_CONTENT = "bigContent"

    /** `NotificationManager.VISIBILITY_NO_OVERRIDE`, a hidden constant on API levels before 34. */
    private const val NOTIFICATION_VISIBILITY_NO_OVERRIDE = -1000

    /** [value] without a Java wrapper, a string of any kind as a String. */
    fun plain(value: Any?): Any? = when (value) {
        is NativeObject, is NativeArray -> value
        is Wrapper -> plain(value.unwrap())
        is CharSequence -> value.toString()
        else -> value
    }

    fun isNullish(value: Any?) = GlobalValues.isNullish(value)

    /** A number, or one of [words] (any case) as its constant; nullish is null. */
    fun choice(function: String, param: String, value: Any?, words: Map<String, Int>): Any? = when (val v = plain(value)) {
        is Number -> v
        is String -> words[v.lowercase()] ?: throw GlobalValues.rangeError(function, param, expected(words.keys), v)
        else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, param, "number | ${expected(words.keys)}", value)
    }

    fun boolean(function: String, param: String, value: Any?): Boolean? = when (val v = plain(value)) {
        is Boolean -> v
        else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, param, "boolean", value)
    }

    /** `true` / `false` or a documented target; nullish is null. */
    fun appendTarget(function: String, param: String, value: Any?): Any? = when (val v = plain(value)) {
        is Boolean -> v
        is String -> APPEND_TARGETS[v.lowercase()] ?: throw GlobalValues.rangeError(function, param, expected(APPEND_TARGETS.values), v)
        else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, param, "boolean | string", value)
    }

    /** A channel id as the current module reads it (string or number); nullish is null. */
    fun channelId(function: String, param: String, value: Any?): Any? = when (val v = plain(value)) {
        is String, is Number -> v
        else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, param, "string | number", value)
    }

    fun text(function: String, param: String, value: Any?): String? = when (val v = plain(value)) {
        is String -> v
        else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, param, "string", value)
    }

    /** A plain object for an options parameter; nullish is null. */
    fun objectOrNull(function: String, param: String, value: Any?): NativeObject? = when (value) {
        is NativeObject -> value
        else -> if (isNullish(value)) null else throw GlobalValues.typeError(function, param, "Object", value)
    }

    fun vibrationPattern(function: String, param: String, value: Any?): LongArray? = when (value) {
        is NativeArray -> LongArray(value.size) { ScriptArgs.long(value[it], function, "$param[$it]") }
        else -> if (isNullish(value)) null else throw GlobalValues.typeError(function, param, "number[]", value)
    }

    /**
     * The checked copy of notice options ([NoticeModule.invoke]), with positional [title] and [content] filled in where
     * the options leave them out, as the current module does.
     */
    fun noticeOptions(function: String, options: NativeObject?, title: Any? = null, content: Any? = null): NativeObject {
        val checked = RhinoUtils.js_object_create(options)
        if (!isNullish(title) && isNullish(checked.prop("title"))) checked.defineProp("title", title)
        if (!isNullish(content) && isNullish(checked.prop("content"))) checked.defineProp("content", content)
        check(checked, "priority") { choice(function, "options.priority", it, PRIORITIES) }
        check(checked, "appendScriptName") { appendTarget(function, "options.appendScriptName", it) }
        check(checked, "autoCancel") { boolean(function, "options.autoCancel", it) }
        check(checked, "isSilent") { boolean(function, "options.isSilent", it) }
        check(checked, "channelId") { channelId(function, "options.channelId", it) }
        check(checked, "notificationId") {
            when (val v = plain(it)) {
                is Number -> v.also { n -> if (!n.toDouble().isFinite()) throw GlobalValues.rangeError(function, "options.notificationId", "finite number", it) }
                else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, "options.notificationId", "number", it)
            }
        }
        check(checked, "intent") {
            when (val v = plain(it)) {
                is NativeObject, is String, is Intent -> v
                else -> if (isNullish(v)) null else throw GlobalValues.typeError(function, "options.intent", "Object | string | android.content.Intent", it)
            }
        }
        return checked
    }

    /**
     * The checked copy of channel options ([NoticeChannelModule.create]), with the own properties of [options] copied:
     * the current module warns for each own property that cannot change an existing channel.
     */
    fun channelOptions(function: String, options: NativeObject?): NativeObject {
        val checked = RhinoUtils.js_object_create(options)
        options?.entries?.forEach { (key, value) -> checked.defineProp(RhinoUtils.coerceString(key), value) }
        check(checked, "importance") { choice(function, "options.importance", it, IMPORTANCES) }
        check(checked, "lockscreenVisibility") { choice(function, "options.lockscreenVisibility", it, LOCKSCREEN_VISIBILITIES) }
        check(checked, "enableVibration") { boolean(function, "options.enableVibration", it) }
        check(checked, "enableLights") { boolean(function, "options.enableLights", it) }
        check(checked, "vibrationPattern") { it.also { pattern -> vibrationPattern(function, "options.vibrationPattern", pattern) } }
        return checked
    }

    /** Checks the property [key] of [options] (when set) and defines the checked value on it. */
    private inline fun check(options: NativeObject, key: String, checker: (Any?) -> Any?) {
        val value = options.prop(key)
        if (isNullish(value)) return
        options.defineProp(key, checker(value) ?: Undefined.instance)
    }

    private fun expected(words: Collection<String>) = words.joinToString(" | ") { "'$it'" }

}
