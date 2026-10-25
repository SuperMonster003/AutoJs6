package org.autojs.autojs.runtime.api.modules.pinyin4j

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

object Pinyin4j : ArgumentGuards() {

    @RhinoSingletonFunctionInterface
    fun invoke(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        of(argList)
    }

    @RhinoSingletonFunctionInterface
    fun of(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (string, options) = argList

        val str = coerceString(string, "").takeIf { it.isNotEmpty() } ?: return@ensureArgumentsLengthInRange ""
        var separator = Pinyin4jOptions.DEFAULT_SEPARATOR
        val opt = when (options) {
            is String -> newNativeObject().also { separator = options }
            is NativeObject -> options
            else -> newNativeObject()
        }

        separator = opt.inquire(listOf("separator", "sep"), ::coerceString, separator)
        val toneType = Pinyin4jOptions.normalizeToneType(
            opt.optionalString(Pinyin4jOptions.TONE_TYPE_NAMES),
            Pinyin4jOptions.DEFAULT_OF_TONE_TYPE,
        )
        val caseType = Pinyin4jOptions.normalizeCaseType(
            opt.optionalString(Pinyin4jOptions.CASE_TYPE_NAMES),
        )
        val vCharType = Pinyin4jOptions.normalizeVCharType(
            opt.optionalString(Pinyin4jOptions.V_CHAR_TYPE_NAMES),
            toneType,
        )

        ofRhino(str, separator, caseType, toneType, vCharType)
    }

    @JvmStatic
    @JvmOverloads
    fun ofRhino(
        str: String,
        separator: String = Pinyin4jOptions.DEFAULT_SEPARATOR,
        caseType: String = Pinyin4jOptions.DEFAULT_CASE_TYPE,
        toneType: String = Pinyin4jOptions.DEFAULT_OF_TONE_TYPE,
        vCharType: String = Pinyin4jOptions.DEFAULT_V_CHAR_TYPE,
    ): String {
        return runBlocking {
            Pinyin4jPluginHost.of(
                GlobalAppContext.get(),
                str,
                Pinyin4jOptions.ofBundle(separator, caseType, toneType, vCharType),
            )
        }
    }

    @RhinoSingletonFunctionInterface
    fun readings(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (source, options) = argList
        val text = coerceString(source, "")
        val opt = options as? NativeObject ?: newNativeObject()
        readingsRhino(text, opt).map { it.toNativeArray() }.toNativeArray()
    }

    internal fun readingsRhino(text: String, options: NativeObject): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        return runBlocking {
            Pinyin4jPluginHost.readings(GlobalAppContext.get(), text, options.toReadingsBundle())
        }
    }

    @RhinoSingletonFunctionInterface
    fun `as`(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { o ->
        runBlocking {
            Pinyin4jPluginHost.asTone(GlobalAppContext.get(), coerceString(o, ""))
        }
    }

    private fun NativeObject.toReadingsBundle(): Bundle {
        return Pinyin4jOptions.readingsBundle(
            romanization = optionalString(Pinyin4jOptions.ROMANIZATION_NAMES),
            caseType = optionalString(Pinyin4jOptions.CASE_TYPE_NAMES),
            toneType = optionalString(Pinyin4jOptions.TONE_TYPE_NAMES),
            vCharType = optionalString(Pinyin4jOptions.V_CHAR_TYPE_NAMES),
            hasExplicitCaseType = hasAny(Pinyin4jOptions.CASE_TYPE_NAMES),
            hasExplicitToneType = hasAny(Pinyin4jOptions.TONE_TYPE_NAMES),
            hasExplicitVCharType = hasAny(Pinyin4jOptions.V_CHAR_TYPE_NAMES),
        )
    }

    private fun NativeObject.hasAny(names: List<String>): Boolean = names.any { name -> has(name, this) }

    private fun NativeObject.optionalString(names: List<String>): String? {
        return if (hasAny(names)) inquire(names, ::coerceString, "") else null
    }
}
