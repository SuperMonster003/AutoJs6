package org.autojs.autojs.runtime.api.modules.pinyin

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.DefaultOptions
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.PinyinMode
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.PinyinStyle
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.parseMode
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.parseStyle
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.newNativeJavaMethod
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.autojs.plugin.pinyin.api.PinyinOptionKeys
import org.json.JSONArray
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY

object Pinyin : Augmentable(), Invokable {

    @Suppress("SpellCheckingInspection")
    override val selfAssignmentProperties: List<Pair<Any, Any?>> get() = scriptProperties

    /**
     * The data properties the `pinyin` script module defines on its module object, in their order (its install hook
     * defines them, roadmap P5.2).
     * zh-CN: `pinyin` 脚本模块在模块对象上定义的数据属性, 按顺序 (由其 install 钩子定义, P5.2).
     */
    val scriptProperties = listOf(
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
                val scope = getDeclarationScope(result)
                val thisObj = Context.javaToJS(Util, result) as Scriptable
                val boundFunction = BoundFunction(
                    /* cx = */ cx,
                    /* scope = */ scope,
                    /* targetFunction = */ newNativeJavaMethod(
                        scope,
                        Util::class.java.methods.first { it.name == funcName },
                        funcName,
                    ),
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
    fun compare(args: Array<out Any?>): Int = ensureArgumentsLength(args, 2) { argList ->
        compareInternal(coerceString(argList[0], ""), coerceString(argList[1], ""))
    }

    @RhinoSingletonFunctionInterface
    fun compact(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) { value ->
        Util.compact(parseRhinoMatrix(value)).map { it.toNativeArray() }.toNativeArray()
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

    internal fun compareInternal(hanA: String, hanB: String): Int {
        val options = Bundle().apply {
            putInt(PinyinOptionKeys.STYLE, PinyinStyle.TONE.value)
        }
        val (pinyinA, pinyinB) = runBlocking {
            parseNestedStringArray(PinyinPluginHost.convert(globalContext, hanA, options)) to
                    parseNestedStringArray(PinyinPluginHost.convert(globalContext, hanB, options))
        }
        return Util.compare(pinyinA, pinyinB)
    }

    private fun fromPhraseInternal(phrase: String): List<List<String>> = parseNestedStringArray(runBlocking {
        PinyinPluginHost.fromPhrase(globalContext, phrase)
    })

    private fun NativeObject.toPluginOptions(): Bundle {
        return Bundle().apply {
            putInt(PinyinOptionKeys.MODE, inquire("mode", { o, def -> parseMode(o) ?: def }, DefaultOptions.MODE.value))
            putInt(PinyinOptionKeys.STYLE, inquire("style", { o, def -> parseStyle(o) ?: def }, DefaultOptions.STYLE.value))
            putBoolean(PinyinOptionKeys.SEGMENT, inquire("segment", ::coerceBoolean, DefaultOptions.SEGMENT))
            putBoolean(PinyinOptionKeys.HETERONYM, inquire("heteronym", ::coerceBoolean, DefaultOptions.HETERONYM))
            putBoolean(PinyinOptionKeys.GROUP, inquire("group", ::coerceBoolean, false))
            if (this@toPluginOptions.has("customDictionary", this@toPluginOptions)) {
                PinyinCustomDictionary.encodeRhino(this@toPluginOptions.get("customDictionary", this@toPluginOptions))
                    ?.let { putString(PinyinOptionKeys.CUSTOM_DICTIONARY_JSON, it) }
            }
        }
    }

    private fun parseRhinoMatrix(value: Any?): List<List<String>> {
        val matrix = value.jsUnwrapped() as? NativeArray
            ?: throw IllegalArgumentException("pinyin.compact expects a two-dimensional array")
        require(matrix.length <= Int.MAX_VALUE.toLong()) { "pinyin.compact input is too large" }
        return (0 until matrix.length.toInt()).map { rowIndex ->
            val row = ScriptableObject.getProperty(matrix, rowIndex).jsUnwrapped() as? NativeArray
                ?: throw IllegalArgumentException("pinyin.compact row $rowIndex must be an array")
            require(row.length <= Int.MAX_VALUE.toLong()) { "pinyin.compact row $rowIndex is too large" }
            (0 until row.length.toInt()).map { itemIndex ->
                coerceString(ScriptableObject.getProperty(row, itemIndex).jsUnwrapped())
            }
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
