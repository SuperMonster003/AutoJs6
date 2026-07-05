package org.autojs.autojs.runtime.api.augment.pinyin

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.DefaultOptions
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.PinyinMode
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.PinyinStyle
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.parseStyle
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.plugin.pinyin.api.PinyinOptionKeys
import org.json.JSONArray
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY

object Pinyin : Augmentable(), Invokable {

    @Suppress("SpellCheckingInspection")
    override val selfAssignmentProperties = listOf(
        "STYLE_NORMAL" to PinyinStyle.NORMAL,
        "STYLE_TONE" to PinyinStyle.TONE,
        "STYLE_TONE2" to PinyinStyle.TONE2,
        "STYLE_TO3NE" to PinyinStyle.TO3NE,
        "STYLE_INITIALS" to PinyinStyle.INITIALS,
        "STYLE_FIRST_LETTER" to PinyinStyle.FIRST_LETTER,
        "MODE_NORMAL" to PinyinMode.NORMAL,
        "MODE_SURNAME" to PinyinMode.SURNAME,
        "MODE_PLACENAME" to PinyinMode.PLACE_NAME,
        "MODE_PLACE_NAME" to PinyinMode.PLACE_NAME,
    )

    override val selfAssignmentFunctions = listOf(
        ::convert.name,
        ::simple.name,
        ::compare.name,
        ::compact.name,
        ::fromCodePoint.name,
        ::fromPhrase.name,
    )

    @RhinoSingletonFunctionInterface
    override fun invoke(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        convert(argList)
    }

    @RhinoSingletonFunctionInterface
    fun convert(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (hansArg, options) = argList
        val hans = coerceString(hansArg, "")
        val opt = options as? NativeObject ?: newNativeObject()
        convertRhino(hans, opt).map { it.toNativeArray() }.toNativeArray().also { result ->
            val funcName = "compact"
            withRhinoContext { cx ->
                val thisObj = Context.javaToJS(Util, result) as Scriptable
                val boundFunction = BoundFunction(
                    /* cx = */ cx,
                    /* scope = */ result,
                    /* targetFunction = */ NativeJavaMethod(Util::class.java.methods.find { it.name == funcName }, funcName),
                    /* boundThis = */ thisObj,
                    /* boundArgs = */ arrayOf(result),
                )
                result.defineProp(funcName, boundFunction, READONLY or DONTENUM or PERMANENT)
            }
        }
    }

    fun convertRhino(hans: String, opt: NativeObject): List<List<String>> = when {
        hans.isEmpty() -> emptyList()
        else -> parseNestedStringArray(runBlocking {
            PinyinPluginHost.convert(globalContext, hans, opt.toPluginOptions())
        })
    }

    @RhinoSingletonFunctionInterface
    fun compare(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
        ""
    }

    @RhinoSingletonFunctionInterface
    fun compact(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
        ""
    }

    @RhinoSingletonFunctionInterface
    fun simple(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
        val (str, enableNumericTone, enableSegment) = argList
        simpleRhino(coerceString(str, ""), coerceBoolean(enableNumericTone, false), coerceBoolean(enableSegment, false))
    }

    @JvmOverloads
    fun simpleRhino(s: String, enableNumericTone: Boolean = false, enableSegment: Boolean = false): String {
        return runBlocking {
            PinyinPluginHost.simple(globalContext, s, enableNumericTone, enableSegment)
        }
    }

    @RhinoSingletonFunctionInterface
    fun fromCodePoint(args: Array<out Any?>): String? = ensureArgumentsOnlyOne(args) { o ->
        fromCodePointInternal(coerceIntNumber(o))
    }

    @RhinoSingletonFunctionInterface
    fun fromPhrase(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) { o ->
        fromPhraseInternal(coerceString(o)).map { it.toNativeArray() }.toNativeArray()
    }

    internal fun fromCodePointInternal(codePoint: Int): String? = runBlocking {
        PinyinPluginHost.fromCodePoint(globalContext, codePoint)
    }

    private fun fromPhraseInternal(phrase: String): List<List<String>> = parseNestedStringArray(runBlocking {
        PinyinPluginHost.fromPhrase(globalContext, phrase)
    })

    private fun NativeObject.toPluginOptions(): Bundle {
        return Bundle().apply {
            putInt(PinyinOptionKeys.MODE, inquire("mode", { o, def -> PinyinCore.parseMode(o) ?: def }, DefaultOptions.MODE.value))
            putInt(PinyinOptionKeys.STYLE, inquire("style", { o, def -> parseStyle(o) ?: def }, DefaultOptions.STYLE.value))
            putBoolean(PinyinOptionKeys.SEGMENT, inquire("segment", ::coerceBoolean, DefaultOptions.SEGMENT))
            putBoolean(PinyinOptionKeys.HETERONYM, inquire("heteronym", ::coerceBoolean, DefaultOptions.HETERONYM))
            putBoolean(PinyinOptionKeys.GROUP, inquire("group", ::coerceBoolean, false))
        }
    }

    private fun parseNestedStringArray(json: String): List<List<String>> {
        if (json.isBlank()) return emptyList()
        val rows = JSONArray(json)
        return (0 until rows.length()).map { rowIndex ->
            val row = rows.optJSONArray(rowIndex)
            when {
                row != null -> (0 until row.length()).map { itemIndex -> row.optString(itemIndex) }
                else -> listOf(rows.optString(rowIndex))
            }
        }
    }
}
