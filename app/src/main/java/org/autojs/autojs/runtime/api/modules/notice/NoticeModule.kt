package org.autojs.autojs.runtime.api.modules.notice

import androidx.core.app.NotificationCompat.Builder
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Notice.Companion.Config
import org.autojs.autojs.runtime.api.augment.notice.Notice
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.util.NotificationUtils
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import kotlin.reflect.KMutableProperty1

/**
 * The `notice` module on the new script module layer (roadmap P3.2): `notice(...)` with its eight documented overloads,
 * `isEnabled` / `ensureEnabled` / `launchSettings`, `config`, `cancel`, `getBuilder` and the `builder` getter, in the order
 * of the current `augment/notice/Notice`; `notice.channel` is [NoticeChannelModule]. The notification is still sent by
 * an instance of the current module (`Notice.invoke`, kept as an instance class by Q24; to be moved out before P5.2),
 * called with the checked options in the options overload, so the positional title and content fill in what the options
 * leave out as before. `cancel` and `getBuilder` call the current companions, `config` writes `runtime.notice.config`.
 *
 * Arguments follow the real signatures (T4, appendix C Q7). A first argument that is not a string, an object, a builder
 * or nullish is a TypeError (the current module sent the test notification), as is a second argument that is neither a
 * string nor an object, or an options value of another type; a builder is recognized through its Java wrapper (the
 * current module compared the wrapper with the builder class). Option words are matched in any case; an unknown word
 * is a RangeError (the current module threw IllegalArgumentException). `appendScriptName: 'bigContent'` appends to the
 * long text as documented (the current module compared the lowercased word with `'bigContent'` and always threw).
 * `config` checks every key and value before it changes any (the current module changed them one by one and threw
 * an IllegalArgumentException from reflection on a value of another type); an unknown key is a RangeError.
 *
 * zh-CN: 新脚本模块层上的 notice 模块 (P3.2): 带文档所列 8 个重载的 notice(...), isEnabled / ensureEnabled / launchSettings, config, cancel, getBuilder 与
 * builder 读取器, 顺序同现有 augment/notice/Notice; notice.channel 见 [NoticeChannelModule]. 通知仍由现有模块的实例发送 (Notice.invoke, Q24 保留为实例类;
 * P5.2 前迁出), 以选项重载传入检查后的选项, 位置参数的标题与内容照旧补上选项未给出的部分. cancel 与 getBuilder 调用现有伴生对象, config 写入
 * runtime.notice.config. 参数按真实签名 (T4, 附录 C Q7). 第一个参数不是字符串, 对象, 构建器或 nullish 为 TypeError (现有模块发送测试通知), 第二个参数
 * 既不是字符串也不是对象, 或选项值类型不符同为 TypeError; 构建器隔着 Java 包装也能识别 (现有模块拿包装对象与构建器类比较). 选项单词不分大小写; 未知单词
 * 为 RangeError (现有模块抛 IllegalArgumentException). appendScriptName: 'bigContent' 按文档附加到长文本 (现有模块拿小写后的单词与 'bigContent' 比较,
 * 总是抛出). config 先检查全部键与值再修改 (现有模块逐个修改, 值类型不符时由反射抛 IllegalArgumentException); 未知键为 RangeError.
 */
@ScriptModule(name = "notice")
class NoticeModule(private val runtime: ScriptRuntime) {

    private val legacy by lazy { Notice(runtime) }

    @ScriptInvoke(arity = [0, 3])
    fun invoke(vararg args: Any?): Int {
        if (args.size > 3) throw ScriptArgs.arityError(MODULE, 0, 3, args.size)
        if (args.isEmpty()) return legacy.invoke()
        val first = NoticeValues.plain(args[0])
        return when {
            first is Builder -> {
                if (args.size > 2) throw ScriptArgs.noOverload(MODULE, args, SIGNATURES)
                val options = NoticeValues.noticeOptions(MODULE, NoticeValues.objectOrNull(MODULE, "options", args.getOrNull(1)))
                legacy.invoke(first, withScriptName(options, defaultTexts = false))
            }
            first is NativeObject -> {
                if (args.size > 1) throw ScriptArgs.noOverload(MODULE, args, SIGNATURES)
                send(NoticeValues.noticeOptions(MODULE, first))
            }
            first is String || NoticeValues.isNullish(first) -> when (args.size) {
                1 -> send(NoticeValues.noticeOptions(MODULE, null, content = first))
                2 -> when (val second = NoticeValues.plain(args[1])) {
                    is NativeObject -> send(NoticeValues.noticeOptions(MODULE, second, content = first))
                    is String, null -> send(NoticeValues.noticeOptions(MODULE, null, title = first, content = second))
                    /* As with the current module, an undefined second argument is a missing options object. */
                    else -> if (Undefined.isUndefined(second)) send(NoticeValues.noticeOptions(MODULE, null, content = first))
                    else throw GlobalValues.typeError(MODULE, "options", "string | Object", args[1])
                }
                else -> {
                    val content = NoticeValues.text(MODULE, "content", args[1])
                    val options = NoticeValues.objectOrNull(MODULE, "options", args[2])
                    send(NoticeValues.noticeOptions(MODULE, options, title = first, content = content))
                }
            }
            else -> throw GlobalValues.typeError(MODULE, "content", "string | Object | ${Builder::class.java.name}", args[0])
        }
    }

    @ScriptFunction
    fun isEnabled(): Boolean = NotificationUtils.isEnabled()

    @ScriptFunction
    fun ensureEnabled() {
        NotificationUtils.ensureEnabled()
    }

    @ScriptFunction
    fun launchSettings() {
        NotificationUtils.launchSettings()
    }

    @ScriptFunction
    fun config(preset: Any?) {
        val function = "notice.config"
        val values = NoticeValues.objectOrNull(function, "preset", preset) ?: throw GlobalValues.typeError(function, "preset", "Object", preset)
        val config = runtime.notice.config
        val pristine by lazy { config.pristine() }
        val updates = values.entries.map { (k, v) ->
            val key = RhinoUtils.coerceString(k)
            val (property, kind) = PRESETS[key] ?: throw GlobalValues.rangeError(function, "preset key", PRESETS.keys.joinToString(" | ") { "'$it'" }, key)
            val value = when {
                NoticeValues.isNullish(v) -> property.get(pristine)
                else -> kind.check(function, "preset.$key", v)
            }
            property to value
        }
        updates.forEach { (property, value) -> property.set(config, value) }
    }

    @ScriptFunction
    fun cancel(id: Any?) {
        val number = GlobalValues.number("notice.cancel", "id", id)
        if (number.isInfinite()) throw GlobalValues.rangeError("notice.cancel", "id", "finite number", id)
        Notice.cancel(runtime, arrayOf(number))
    }

    @ScriptFunction
    fun getBuilder(): Builder = Notice.getBuilder(runtime, emptyArray())

    @ScriptGetter(name = "builder")
    fun builder(): Builder = getBuilder()

    private fun send(options: NativeObject): Int = legacy.invoke(withScriptName(options, defaultTexts = true))

    /**
     * Appends the script name to the long text for the target `'bigContent'`, which the current module never matched;
     * other targets are left to it. Without any text, the test notification texts are filled in first, as the current
     * module does before it appends ([defaultTexts], not for a builder).
     */
    private fun withScriptName(options: NativeObject, defaultTexts: Boolean): NativeObject {
        val target = options.prop("appendScriptName").takeUnless { NoticeValues.isNullish(it) } ?: runtime.notice.config.defaultAppendScriptName
        if (!NoticeValues.BIG_CONTENT.equals(target as? String, ignoreCase = true)) return options
        if (defaultTexts && TEXTS.all { NoticeValues.isNullish(options.prop(it)) }) {
            options.defineProp("title", NotificationUtils.defaultTitle)
            options.defineProp("content", NotificationUtils.defaultContent)
        }
        val scriptName = runtime.notice.config.defaultChannelId
        val bigContent = options.prop(NoticeValues.BIG_CONTENT)
        options.defineProp(NoticeValues.BIG_CONTENT, if (NoticeValues.isNullish(bigContent)) scriptName else "${RhinoUtils.coerceString(bigContent)} ($scriptName)")
        options.defineProp("appendScriptName", false)
        return options
    }

    /** The value types of the `notice.config` presets, checked as the options they are the default for. */
    private enum class Preset {
        BOOLEAN, TEXT, APPEND_TARGET, PRIORITY, IMPORTANCE, VIBRATION_PATTERN, COLOR, LOCKSCREEN_VISIBILITY;

        fun check(function: String, param: String, value: Any?): Any? = when (this) {
            BOOLEAN -> NoticeValues.boolean(function, param, value)
            TEXT -> NoticeValues.text(function, param, value)
            APPEND_TARGET -> NoticeValues.appendTarget(function, param, value)
            PRIORITY -> NoticeValues.choice(function, param, value, NoticeValues.PRIORITIES)
            IMPORTANCE -> NoticeValues.choice(function, param, value, NoticeValues.IMPORTANCES)
            VIBRATION_PATTERN -> NoticeValues.vibrationPattern(function, param, value)
            COLOR -> NoticeValues.plain(value)
            LOCKSCREEN_VISIBILITY -> NoticeValues.choice(function, param, value, NoticeValues.LOCKSCREEN_VISIBILITIES)
        }
    }

    private companion object {

        const val MODULE = "notice"

        const val SIGNATURES = "(), (content), (title, content), (options), (content, options), (title, content, options), (builder), (builder, options)"

        val TEXTS = listOf("title", "content", NoticeValues.BIG_CONTENT)

        /** The public mutable properties of `runtime.notice.config`, the keys the current module accepted. */
        @Suppress("UNCHECKED_CAST")
        val PRESETS: Map<String, Pair<KMutableProperty1<Config, Any?>, Preset>> = listOf(
            Config::useScriptNameAsDefaultChannelId to Preset.BOOLEAN,
            Config::useDynamicDefaultNotificationId to Preset.BOOLEAN,
            Config::enableChannelInvalidModificationWarnings to Preset.BOOLEAN,
            Config::defaultTitle to Preset.TEXT,
            Config::defaultContent to Preset.TEXT,
            Config::defaultBigContent to Preset.TEXT,
            Config::defaultAppendScriptName to Preset.APPEND_TARGET,
            Config::defaultAutoCancel to Preset.BOOLEAN,
            Config::defaultIsSilent to Preset.BOOLEAN,
            Config::defaultPriority to Preset.PRIORITY,
            Config::defaultChannelId to Preset.TEXT,
            Config::defaultChannelName to Preset.TEXT,
            Config::defaultChannelDescription to Preset.TEXT,
            Config::defaultImportanceForChannel to Preset.IMPORTANCE,
            Config::defaultEnableVibrationForChannel to Preset.BOOLEAN,
            Config::defaultVibrationPatternForChannel to Preset.VIBRATION_PATTERN,
            Config::defaultEnableLightsForChannel to Preset.BOOLEAN,
            Config::defaultLightColorForChannel to Preset.COLOR,
            Config::defaultLockscreenVisibilityForChannel to Preset.LOCKSCREEN_VISIBILITY,
        ).associate { (property, kind) -> property.name to ((property as KMutableProperty1<Config, Any?>) to kind) }

    }

}
