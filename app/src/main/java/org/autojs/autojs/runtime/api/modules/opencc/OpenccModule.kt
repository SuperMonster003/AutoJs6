package org.autojs.autojs.runtime.api.modules.opencc

import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import java.util.Locale

/**
 * `opencc` on the new script module layer (roadmap P4.1): `opencc(s, type)` and `opencc.convert(s, type)` with the 32
 * named conversions of the current `augment/opencc/OpenCC`, which converts through the OpenCC plugin (its conversion
 * types and the chains of `OpenccConversionPlan`).
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer; the
 * arguments are checked here first (D10, IllegalArgumentException in the current module): a nullish text is a TypeError
 * (the current module cannot convert it to a string), a type that is neither a string nor an enum constant a TypeError
 * and an unknown type name a RangeError. The conversion itself is the current module's.
 * Once the arguments are accepted, a failure of the current module (the plugin not installed or disabled among them)
 * is rethrown as the current augment layer does (`AugmentMembers.asCurrent`): the same InternalError in both states.
 *
 * zh-CN: 新脚本模块层上的 opencc (P4.1): opencc(s, type) 与 opencc.convert(s, type), 以及现有 augment/opencc/OpenCC 的 32 个具名转换 (经 OpenCC 插件转换,
 * 转换类型与 OpenccConversionPlan 的转换链). 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError; 参数先在此检查 (D10, 现有模块均为
 * IllegalArgumentException): nullish 文本为 TypeError (现有模块无法将其转为字符串), 既非字符串也非枚举常量的 type 为 TypeError, 未知的类型名为
 * RangeError. 转换本身由现有模块完成.
 * 参数通过检查后, 现有模块的失败 (含插件未安装或未启用) 按现有 augment 层的方式重新抛出 (AugmentMembers.asCurrent), 两态为同一个 InternalError.
 */
@ScriptModule(name = "opencc")
class OpenccModule {

    @ScriptInvoke
    fun invoke(s: Any?, type: Any?): String = convert(MODULE, s, type)

    @ScriptFunction
    fun convert(s: Any?, type: Any?): String = convert(FUNCTION_CONVERT, s, type)

    @ScriptFunction
    fun hk2s(s: Any?): String = named("hk2s", text("opencc.hk2s", s)) { OpenCC.hk2s(it) }

    @ScriptFunction
    fun hk2t(s: Any?): String = named("hk2t", text("opencc.hk2t", s)) { OpenCC.hk2t(it) }

    @ScriptFunction
    fun jp2t(s: Any?): String = named("jp2t", text("opencc.jp2t", s)) { OpenCC.jp2t(it) }

    @ScriptFunction
    fun s2hk(s: Any?): String = named("s2hk", text("opencc.s2hk", s)) { OpenCC.s2hk(it) }

    @ScriptFunction
    fun s2t(s: Any?): String = named("s2t", text("opencc.s2t", s)) { OpenCC.s2t(it) }

    @ScriptFunction
    fun s2tw(s: Any?): String = named("s2tw", text("opencc.s2tw", s)) { OpenCC.s2tw(it) }

    @ScriptFunction
    fun s2twp(s: Any?): String = named("s2twp", text("opencc.s2twp", s)) { OpenCC.s2twp(it) }

    @ScriptFunction
    fun t2hk(s: Any?): String = named("t2hk", text("opencc.t2hk", s)) { OpenCC.t2hk(it) }

    @ScriptFunction
    fun t2s(s: Any?): String = named("t2s", text("opencc.t2s", s)) { OpenCC.t2s(it) }

    @ScriptFunction
    fun t2tw(s: Any?): String = named("t2tw", text("opencc.t2tw", s)) { OpenCC.t2tw(it) }

    @ScriptFunction
    fun t2jp(s: Any?): String = named("t2jp", text("opencc.t2jp", s)) { OpenCC.t2jp(it) }

    @ScriptFunction
    fun tw2s(s: Any?): String = named("tw2s", text("opencc.tw2s", s)) { OpenCC.tw2s(it) }

    @ScriptFunction
    fun tw2t(s: Any?): String = named("tw2t", text("opencc.tw2t", s)) { OpenCC.tw2t(it) }

    @ScriptFunction
    fun tw2sp(s: Any?): String = named("tw2sp", text("opencc.tw2sp", s)) { OpenCC.tw2sp(it) }

    @ScriptFunction
    fun s2twi(s: Any?): String = named("s2twi", text("opencc.s2twi", s)) { OpenCC.s2twi(it) }

    @ScriptFunction
    fun twi2s(s: Any?): String = named("twi2s", text("opencc.twi2s", s)) { OpenCC.twi2s(it) }

    @ScriptFunction
    fun s2jp(s: Any?): String = named("s2jp", text("opencc.s2jp", s)) { OpenCC.s2jp(it) }

    @ScriptFunction
    fun t2twi(s: Any?): String = named("t2twi", text("opencc.t2twi", s)) { OpenCC.t2twi(it) }

    @ScriptFunction
    fun hk2tw(s: Any?): String = named("hk2tw", text("opencc.hk2tw", s)) { OpenCC.hk2tw(it) }

    @ScriptFunction
    fun hk2twi(s: Any?): String = named("hk2twi", text("opencc.hk2twi", s)) { OpenCC.hk2twi(it) }

    @ScriptFunction
    fun hk2jp(s: Any?): String = named("hk2jp", text("opencc.hk2jp", s)) { OpenCC.hk2jp(it) }

    @ScriptFunction
    fun tw2hk(s: Any?): String = named("tw2hk", text("opencc.tw2hk", s)) { OpenCC.tw2hk(it) }

    @ScriptFunction
    fun tw2twi(s: Any?): String = named("tw2twi", text("opencc.tw2twi", s)) { OpenCC.tw2twi(it) }

    @ScriptFunction
    fun tw2jp(s: Any?): String = named("tw2jp", text("opencc.tw2jp", s)) { OpenCC.tw2jp(it) }

    @ScriptFunction
    fun twi2t(s: Any?): String = named("twi2t", text("opencc.twi2t", s)) { OpenCC.twi2t(it) }

    @ScriptFunction
    fun twi2hk(s: Any?): String = named("twi2hk", text("opencc.twi2hk", s)) { OpenCC.twi2hk(it) }

    @ScriptFunction
    fun twi2tw(s: Any?): String = named("twi2tw", text("opencc.twi2tw", s)) { OpenCC.twi2tw(it) }

    @ScriptFunction
    fun twi2jp(s: Any?): String = named("twi2jp", text("opencc.twi2jp", s)) { OpenCC.twi2jp(it) }

    @ScriptFunction
    fun jp2s(s: Any?): String = named("jp2s", text("opencc.jp2s", s)) { OpenCC.jp2s(it) }

    @ScriptFunction
    fun jp2hk(s: Any?): String = named("jp2hk", text("opencc.jp2hk", s)) { OpenCC.jp2hk(it) }

    @ScriptFunction
    fun jp2tw(s: Any?): String = named("jp2tw", text("opencc.jp2tw", s)) { OpenCC.jp2tw(it) }

    @ScriptFunction
    fun jp2twi(s: Any?): String = named("jp2twi", text("opencc.jp2twi", s)) { OpenCC.jp2twi(it) }

    private fun convert(function: String, s: Any?, type: Any?): String {
        text(function, s)
        // The type as the current module reads it: unwrapped by its argument guard (a concatenated string is a String).
        // zh-CN: 与现有模块读取的 type 相同: 经其参数守卫解包 (拼接出的字符串即为 String).
        val name = when (val raw = RhinoUtils.unwrap(type)) {
            is String -> raw
            is Enum<*> -> raw.name
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("type", "string", ScriptArgs.describe(type)))
        }.lowercase(Locale.ROOT)
        if (name.uppercase(Locale.ROOT) !in OpenccConversionTypes.ALL && OpenccConversionPlan.chainFor(name) == null) {
            throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("type", UtilValues.quote(type)))
        }
        return AugmentMembers.asCurrent(OpenCC, if (function == MODULE) null else "convert") { OpenCC.convert(arrayOf(s, type)) }
    }

    /** The named conversion [function] of the current module, failing as there once [text] is checked. */
    private fun named(function: String, text: Any, convert: (Array<Any?>) -> String): String = AugmentMembers.asCurrent(OpenCC, function) { convert(arrayOf(text)) }

    private fun text(function: String, s: Any?): Any {
        if (ScriptArgs.isNullish(s)) throw UtilValues.typeError(function, ScriptMessages.argumentType("s", "string", ScriptArgs.describe(s)))
        return s!!
    }

    private companion object {
        const val MODULE = "opencc"
        const val FUNCTION_CONVERT = "opencc.convert"
    }

}
