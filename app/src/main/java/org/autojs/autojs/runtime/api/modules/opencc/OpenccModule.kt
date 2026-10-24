package org.autojs.autojs.runtime.api.modules.opencc

import org.autojs.autojs.runtime.api.augment.opencc.OpenCC
import org.autojs.autojs.runtime.api.augment.opencc.OpenccConversionPlan
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.mozilla.javascript.Wrapper
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
    fun hk2s(s: Any?): String = OpenCC.hk2sRhino(text("opencc.hk2s", s))

    @ScriptFunction
    fun hk2t(s: Any?): String = OpenCC.hk2tRhino(text("opencc.hk2t", s))

    @ScriptFunction
    fun jp2t(s: Any?): String = OpenCC.jp2tRhino(text("opencc.jp2t", s))

    @ScriptFunction
    fun s2hk(s: Any?): String = OpenCC.s2hkRhino(text("opencc.s2hk", s))

    @ScriptFunction
    fun s2t(s: Any?): String = OpenCC.s2tRhino(text("opencc.s2t", s))

    @ScriptFunction
    fun s2tw(s: Any?): String = OpenCC.s2twRhino(text("opencc.s2tw", s))

    @ScriptFunction
    fun s2twp(s: Any?): String = OpenCC.s2twpRhino(text("opencc.s2twp", s))

    @ScriptFunction
    fun t2hk(s: Any?): String = OpenCC.t2hkRhino(text("opencc.t2hk", s))

    @ScriptFunction
    fun t2s(s: Any?): String = OpenCC.t2sRhino(text("opencc.t2s", s))

    @ScriptFunction
    fun t2tw(s: Any?): String = OpenCC.t2twRhino(text("opencc.t2tw", s))

    @ScriptFunction
    fun t2jp(s: Any?): String = OpenCC.t2jpRhino(text("opencc.t2jp", s))

    @ScriptFunction
    fun tw2s(s: Any?): String = OpenCC.tw2sRhino(text("opencc.tw2s", s))

    @ScriptFunction
    fun tw2t(s: Any?): String = OpenCC.tw2tRhino(text("opencc.tw2t", s))

    @ScriptFunction
    fun tw2sp(s: Any?): String = OpenCC.tw2spRhino(text("opencc.tw2sp", s))

    @ScriptFunction
    fun s2twi(s: Any?): String = OpenCC.s2twiRhino(text("opencc.s2twi", s))

    @ScriptFunction
    fun twi2s(s: Any?): String = OpenCC.twi2sRhino(text("opencc.twi2s", s))

    @ScriptFunction
    fun s2jp(s: Any?): String = OpenCC.s2jpRhino(text("opencc.s2jp", s))

    @ScriptFunction
    fun t2twi(s: Any?): String = OpenCC.t2twiRhino(text("opencc.t2twi", s))

    @ScriptFunction
    fun hk2tw(s: Any?): String = OpenCC.hk2twRhino(text("opencc.hk2tw", s))

    @ScriptFunction
    fun hk2twi(s: Any?): String = OpenCC.hk2twiRhino(text("opencc.hk2twi", s))

    @ScriptFunction
    fun hk2jp(s: Any?): String = OpenCC.hk2jpRhino(text("opencc.hk2jp", s))

    @ScriptFunction
    fun tw2hk(s: Any?): String = OpenCC.tw2hkRhino(text("opencc.tw2hk", s))

    @ScriptFunction
    fun tw2twi(s: Any?): String = OpenCC.tw2twiRhino(text("opencc.tw2twi", s))

    @ScriptFunction
    fun tw2jp(s: Any?): String = OpenCC.tw2jpRhino(text("opencc.tw2jp", s))

    @ScriptFunction
    fun twi2t(s: Any?): String = OpenCC.twi2tRhino(text("opencc.twi2t", s))

    @ScriptFunction
    fun twi2hk(s: Any?): String = OpenCC.twi2hkRhino(text("opencc.twi2hk", s))

    @ScriptFunction
    fun twi2tw(s: Any?): String = OpenCC.twi2twRhino(text("opencc.twi2tw", s))

    @ScriptFunction
    fun twi2jp(s: Any?): String = OpenCC.twi2jpRhino(text("opencc.twi2jp", s))

    @ScriptFunction
    fun jp2s(s: Any?): String = OpenCC.jp2sRhino(text("opencc.jp2s", s))

    @ScriptFunction
    fun jp2hk(s: Any?): String = OpenCC.jp2hkRhino(text("opencc.jp2hk", s))

    @ScriptFunction
    fun jp2tw(s: Any?): String = OpenCC.jp2twRhino(text("opencc.jp2tw", s))

    @ScriptFunction
    fun jp2twi(s: Any?): String = OpenCC.jp2twiRhino(text("opencc.jp2twi", s))

    private fun convert(function: String, s: Any?, type: Any?): String {
        text(function, s)
        val name = when (val raw = (type as? Wrapper)?.unwrap() ?: type) {
            is CharSequence -> raw.toString()
            is Enum<*> -> raw.name
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("type", "string", ScriptArgs.describe(type)))
        }.lowercase(Locale.ROOT)
        if (name.uppercase(Locale.ROOT) !in OpenccConversionTypes.ALL && OpenccConversionPlan.chainFor(name) == null) {
            throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("type", UtilValues.quote(type)))
        }
        return OpenCC.convertRhino(s, type)
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
