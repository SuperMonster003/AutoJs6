package org.autojs.autojs.runtime.api.modules.opencc

import org.autojs.autojs.runtime.api.augment.opencc.OpenCC
import org.autojs.autojs.runtime.api.augment.opencc.OpenccConversionPlan
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
 *
 * zh-CN: 新脚本模块层上的 opencc (P4.1): opencc(s, type) 与 opencc.convert(s, type), 以及现有 augment/opencc/OpenCC 的 32 个具名转换 (经 OpenCC 插件转换,
 * 转换类型与 OpenccConversionPlan 的转换链). 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError; 参数先在此检查 (D10, 现有模块均为
 * IllegalArgumentException): nullish 文本为 TypeError (现有模块无法将其转为字符串), 既非字符串也非枚举常量的 type 为 TypeError, 未知的类型名为
 * RangeError. 转换本身由现有模块完成.
 */
@ScriptModule(name = "opencc")
class OpenccModule {

    @ScriptInvoke
    fun invoke(s: Any?, type: Any?): String = convert(MODULE, s, type)

    @ScriptFunction
    fun convert(s: Any?, type: Any?): String = convert(FUNCTION_CONVERT, s, type)

    @ScriptFunction
    fun hk2s(s: Any?): String = OpenCC.hk2s(arrayOf(text("opencc.hk2s", s)))

    @ScriptFunction
    fun hk2t(s: Any?): String = OpenCC.hk2t(arrayOf(text("opencc.hk2t", s)))

    @ScriptFunction
    fun jp2t(s: Any?): String = OpenCC.jp2t(arrayOf(text("opencc.jp2t", s)))

    @ScriptFunction
    fun s2hk(s: Any?): String = OpenCC.s2hk(arrayOf(text("opencc.s2hk", s)))

    @ScriptFunction
    fun s2t(s: Any?): String = OpenCC.s2t(arrayOf(text("opencc.s2t", s)))

    @ScriptFunction
    fun s2tw(s: Any?): String = OpenCC.s2tw(arrayOf(text("opencc.s2tw", s)))

    @ScriptFunction
    fun s2twp(s: Any?): String = OpenCC.s2twp(arrayOf(text("opencc.s2twp", s)))

    @ScriptFunction
    fun t2hk(s: Any?): String = OpenCC.t2hk(arrayOf(text("opencc.t2hk", s)))

    @ScriptFunction
    fun t2s(s: Any?): String = OpenCC.t2s(arrayOf(text("opencc.t2s", s)))

    @ScriptFunction
    fun t2tw(s: Any?): String = OpenCC.t2tw(arrayOf(text("opencc.t2tw", s)))

    @ScriptFunction
    fun t2jp(s: Any?): String = OpenCC.t2jp(arrayOf(text("opencc.t2jp", s)))

    @ScriptFunction
    fun tw2s(s: Any?): String = OpenCC.tw2s(arrayOf(text("opencc.tw2s", s)))

    @ScriptFunction
    fun tw2t(s: Any?): String = OpenCC.tw2t(arrayOf(text("opencc.tw2t", s)))

    @ScriptFunction
    fun tw2sp(s: Any?): String = OpenCC.tw2sp(arrayOf(text("opencc.tw2sp", s)))

    @ScriptFunction
    fun s2twi(s: Any?): String = OpenCC.s2twi(arrayOf(text("opencc.s2twi", s)))

    @ScriptFunction
    fun twi2s(s: Any?): String = OpenCC.twi2s(arrayOf(text("opencc.twi2s", s)))

    @ScriptFunction
    fun s2jp(s: Any?): String = OpenCC.s2jp(arrayOf(text("opencc.s2jp", s)))

    @ScriptFunction
    fun t2twi(s: Any?): String = OpenCC.t2twi(arrayOf(text("opencc.t2twi", s)))

    @ScriptFunction
    fun hk2tw(s: Any?): String = OpenCC.hk2tw(arrayOf(text("opencc.hk2tw", s)))

    @ScriptFunction
    fun hk2twi(s: Any?): String = OpenCC.hk2twi(arrayOf(text("opencc.hk2twi", s)))

    @ScriptFunction
    fun hk2jp(s: Any?): String = OpenCC.hk2jp(arrayOf(text("opencc.hk2jp", s)))

    @ScriptFunction
    fun tw2hk(s: Any?): String = OpenCC.tw2hk(arrayOf(text("opencc.tw2hk", s)))

    @ScriptFunction
    fun tw2twi(s: Any?): String = OpenCC.tw2twi(arrayOf(text("opencc.tw2twi", s)))

    @ScriptFunction
    fun tw2jp(s: Any?): String = OpenCC.tw2jp(arrayOf(text("opencc.tw2jp", s)))

    @ScriptFunction
    fun twi2t(s: Any?): String = OpenCC.twi2t(arrayOf(text("opencc.twi2t", s)))

    @ScriptFunction
    fun twi2hk(s: Any?): String = OpenCC.twi2hk(arrayOf(text("opencc.twi2hk", s)))

    @ScriptFunction
    fun twi2tw(s: Any?): String = OpenCC.twi2tw(arrayOf(text("opencc.twi2tw", s)))

    @ScriptFunction
    fun twi2jp(s: Any?): String = OpenCC.twi2jp(arrayOf(text("opencc.twi2jp", s)))

    @ScriptFunction
    fun jp2s(s: Any?): String = OpenCC.jp2s(arrayOf(text("opencc.jp2s", s)))

    @ScriptFunction
    fun jp2hk(s: Any?): String = OpenCC.jp2hk(arrayOf(text("opencc.jp2hk", s)))

    @ScriptFunction
    fun jp2tw(s: Any?): String = OpenCC.jp2tw(arrayOf(text("opencc.jp2tw", s)))

    @ScriptFunction
    fun jp2twi(s: Any?): String = OpenCC.jp2twi(arrayOf(text("opencc.jp2twi", s)))

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
        return OpenCC.convert(arrayOf(s, type))
    }

    private fun text(function: String, s: Any?): Any {
        if (ScriptArgs.isNullish(s)) throw UtilValues.typeError(function, ScriptMessages.argumentType("s", "string", ScriptArgs.describe(s)))
        return s!!
    }

    private companion object {
        const val MODULE = "opencc"
        const val FUNCTION_CONVERT = "opencc.convert"
    }

}
