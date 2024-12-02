package org.autojs.autojs.runtime.api.augment.opencc

import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Context

@Suppress("unused")
object OpenCC : Augmentable(), Invokable {

    override val key = super.key.lowercase()

    override val selfAssignmentFunctions = listOf(
        ::convert.name,
        ::hk2s.name,
        ::hk2t.name,
        ::jp2t.name,
        ::s2hk.name,
        ::s2t.name,
        ::s2tw.name,
        ::s2twp.name,
        ::t2hk.name,
        ::t2s.name,
        ::t2tw.name,
        ::t2jp.name,
        ::tw2s.name,
        ::tw2t.name,
        ::tw2sp.name,
        ::s2twi.name,
        ::twi2s.name,
        ::s2jp.name,
        ::t2twi.name,
        ::hk2tw.name,
        ::hk2twi.name,
        ::hk2jp.name,
        ::tw2hk.name,
        ::tw2twi.name,
        ::tw2jp.name,
        ::twi2t.name,
        ::twi2hk.name,
        ::twi2tw.name,
        ::twi2jp.name,
        ::jp2s.name,
        ::jp2hk.name,
        ::jp2tw.name,
        ::jp2twi.name,
    )

    override fun invoke(vararg args: Any?): String = convert(args)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun convert(args: Array<out Any?>): String = ensureArgumentsLength(args, 2) {
        val (s, type) = it
        convertRhino(s, type)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun convertRhino(s: Any?, type: Any?): String = when (type) {
        is String -> runCatching {
            Context.toString(javaClass.getMethod("${type.lowercase()}Rhino", Any::class.java).invoke(this@OpenCC, s))
        }.getOrElse {
            it.printStackTrace()
            throw WrappedIllegalArgumentException("Unknown type \"$type\" for opencc")
        }
        is ConversionType -> ChineseConverter.convert(coerceString(s), type, globalContext)
        else -> throw WrappedIllegalArgumentException("Argument type ${type.jsBrief()} for opencc.convert is invalid")
    }

    /* OpenCC internal conversion. */

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2sRhino(s: Any?) = cvt(s, ConversionType.HK2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2tRhino(s: Any?) = cvt(s, ConversionType.HK2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2tRhino(s: Any?) = cvt(s, ConversionType.JP2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2hkRhino(s: Any?) = cvt(s, ConversionType.S2HK)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2tRhino(s: Any?) = cvt(s, ConversionType.S2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twRhino(s: Any?) = cvt(s, ConversionType.S2TW)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2twp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twpRhino(s: Any?) = cvt(s, ConversionType.S2TWP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2hkRhino(s: Any?) = cvt(s, ConversionType.T2HK)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2sRhino(s: Any?) = cvt(s, ConversionType.T2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2twRhino(s: Any?) = cvt(s, ConversionType.T2TW)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2jpRhino(s: Any?) = cvt(s, ConversionType.T2JP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2sRhino(s: Any?) = cvt(s, ConversionType.TW2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2tRhino(s: Any?) = cvt(s, ConversionType.TW2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2sp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2spRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2spRhino(s: Any?) = cvt(s, ConversionType.TW2SP)

    /* Encapsulated conversion. */

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twiRhino(s: Any?) = cvt(s, ConversionType.S2TWP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2sRhino(s: Any?) = cvt(s, ConversionType.TW2SP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2jpRhino(s: Any?) = t2jpRhino(s2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2twiRhino(s: Any?) = s2twiRhino(t2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2twRhino(s: Any?) = t2twRhino(hk2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2twiRhino(s: Any?) = s2twiRhino(hk2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2jpRhino(s: Any?) = t2jpRhino(hk2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2hkRhino(s: Any?) = t2hkRhino(tw2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2twiRhino(s: Any?) = s2twiRhino(tw2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2jpRhino(s: Any?) = t2jpRhino(tw2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2tRhino(s: Any?) = s2tRhino(twi2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2hkRhino(s: Any?) = s2hkRhino(twi2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2twRhino(s: Any?) = s2twRhino(twi2sRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2jpRhino(s: Any?) = t2jpRhino(s2tRhino(twi2sRhino(s)))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2sRhino(s: Any?) = t2sRhino(jp2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2hkRhino(s: Any?) = t2hkRhino(jp2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2twRhino(s: Any?) = t2twRhino(jp2tRhino(s))

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2twiRhino(s: Any?) = s2twiRhino(t2sRhino(jp2tRhino(s)))

    /* Private methods. */

    private fun cvt(s: Any?, type: ConversionType): String = ChineseConverter.convert(coerceString(s), type, globalContext)

}