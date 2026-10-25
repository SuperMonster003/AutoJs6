package org.autojs.autojs.runtime.api.modules.opencc

import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.text.OpenccPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.mozilla.javascript.Wrapper
import java.util.Locale

@Suppress("unused")
object OpenCC : ArgumentGuards() {

    fun invoke(vararg args: Any?): String = convert(args)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun convert(args: Array<out Any?>): String = ensureArgumentsLength(args, 2) {
        val (s, type) = it
        convertRhino(s, type)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun convertRhino(s: Any?, type: Any?): String {
        val rawType = (type as? Wrapper)?.unwrap() ?: type
        val name = when (rawType) {
            is String -> rawType
            is Enum<*> -> rawType.name
            else -> throw WrappedIllegalArgumentException("Argument \"type\" ${type.jsBrief()} for opencc.convert is invalid")
        }.lowercase(Locale.ROOT)
        val normalizedName = name.uppercase(Locale.ROOT)
        if (normalizedName in OpenccConversionTypes.ALL) return cvt(s, normalizedName)
        return OpenccConversionPlan.chainFor(name)?.let { cvtChain(s, it) }
            ?: throw WrappedIllegalArgumentException("Unknown OpenCC conversion type \"$type\"")
    }

    /* OpenCC internal conversion. */

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2sRhino(s: Any?) = cvt(s, OpenccConversionTypes.HK2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2tRhino(s: Any?) = cvt(s, OpenccConversionTypes.HK2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2tRhino(s: Any?) = cvt(s, OpenccConversionTypes.JP2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2hkRhino(s: Any?) = cvt(s, OpenccConversionTypes.S2HK)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2tRhino(s: Any?) = cvt(s, OpenccConversionTypes.S2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twRhino(s: Any?) = cvt(s, OpenccConversionTypes.S2TW)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2twp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twpRhino(s: Any?) = cvt(s, OpenccConversionTypes.S2TWP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2hkRhino(s: Any?) = cvt(s, OpenccConversionTypes.T2HK)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2sRhino(s: Any?) = cvt(s, OpenccConversionTypes.T2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2twRhino(s: Any?) = cvt(s, OpenccConversionTypes.T2TW)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2jpRhino(s: Any?) = cvt(s, OpenccConversionTypes.T2JP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2sRhino(s: Any?) = cvt(s, OpenccConversionTypes.TW2S)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2tRhino(s: Any?) = cvt(s, OpenccConversionTypes.TW2T)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2sp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2spRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2spRhino(s: Any?) = cvt(s, OpenccConversionTypes.TW2SP)

    /* Encapsulated conversion. */

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2twiRhino(s: Any?) = cvt(s, OpenccConversionTypes.S2TWP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2sRhino(s: Any?) = cvt(s, OpenccConversionTypes.TW2SP)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun s2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { s2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun s2jpRhino(s: Any?) = cvtComposite(s, ::s2jp.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun t2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { t2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun t2twiRhino(s: Any?) = cvtComposite(s, ::t2twi.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2twRhino(s: Any?) = cvtComposite(s, ::hk2tw.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2twiRhino(s: Any?) = cvtComposite(s, ::hk2twi.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun hk2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { hk2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun hk2jpRhino(s: Any?) = cvtComposite(s, ::hk2jp.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2hkRhino(s: Any?) = cvtComposite(s, ::tw2hk.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2twiRhino(s: Any?) = cvtComposite(s, ::tw2twi.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun tw2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { tw2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun tw2jpRhino(s: Any?) = cvtComposite(s, ::tw2jp.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2t(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2tRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2tRhino(s: Any?) = cvtComposite(s, ::twi2t.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2hkRhino(s: Any?) = cvtComposite(s, ::twi2hk.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2twRhino(s: Any?) = cvtComposite(s, ::twi2tw.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun twi2jp(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { twi2jpRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun twi2jpRhino(s: Any?) = cvtComposite(s, ::twi2jp.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2s(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2sRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2sRhino(s: Any?) = cvtComposite(s, ::jp2s.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2hk(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2hkRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2hkRhino(s: Any?) = cvtComposite(s, ::jp2hk.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2tw(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2twRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2twRhino(s: Any?) = cvtComposite(s, ::jp2tw.name)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun jp2twi(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { jp2twiRhino(it) }

    @JvmStatic
    @RhinoFunctionBody
    fun jp2twiRhino(s: Any?) = cvtComposite(s, ::jp2twi.name)

    /* Private methods. */

    private fun cvt(s: Any?, type: String): String = runBlocking {
        OpenccPluginHost.convert(GlobalAppContext.get(), coerceString(s), type.uppercase(Locale.ROOT))
    }

    private fun cvtComposite(s: Any?, name: String): String {
        return cvtChain(s, requireNotNull(OpenccConversionPlan.chainFor(name)))
    }

    private fun cvtChain(s: Any?, conversionTypes: List<String>): String = runBlocking {
        OpenccPluginHost.convertChain(GlobalAppContext.get(), coerceString(s), conversionTypes)
    }

}
