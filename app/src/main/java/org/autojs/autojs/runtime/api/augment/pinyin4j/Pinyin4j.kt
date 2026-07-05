package org.autojs.autojs.runtime.api.augment.pinyin4j

import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringUppercase
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.plugin.pinyin4j.api.Pinyin4jOptionKeys
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject

object Pinyin4j : Augmentable(), Invokable {

    private const val DEFAULT_SEPARATOR = ""
    private const val DEFAULT_CASE_TYPE_STRING = "LOWERCASE"
    private const val DEFAULT_TONE_TYPE_STRING = "WITHOUT_TONE"
    private const val DEFAULT_V_CHAR_TYPE_STRING = "WITH_V"

    override val selfAssignmentFunctions = listOf(
        ::`as`.name,
        ::of.name,
    )

    @RhinoSingletonFunctionInterface
    override fun invoke(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        of(argList)
    }

    @RhinoSingletonFunctionInterface
    fun of(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (string, options) = argList

        val str = coerceString(string, "").takeIf { it.isNotEmpty() } ?: return@ensureArgumentsLengthInRange ""
        var separator = DEFAULT_SEPARATOR
        val opt = when (options) {
            is String -> newNativeObject().also { separator = options }
            is NativeObject -> options
            else -> newNativeObject()
        }

        separator = opt.inquire(listOf("separator", "sep"), ::coerceString, separator)
        val toneType = normalizeToneType(opt.inquire(listOf("tone", "toneType")))
        val caseType = normalizeCaseType(opt.inquire(listOf("case", "caseType")))
        val vCharType = normalizeVCharType(opt.inquire(listOf("v", "vChar", "vCharType")), toneType)

        ofRhino(str, separator, caseType, toneType, vCharType)
    }

    @JvmStatic
    @JvmOverloads
    fun ofRhino(
        str: String,
        separator: String = DEFAULT_SEPARATOR,
        caseType: String = DEFAULT_CASE_TYPE_STRING,
        toneType: String = DEFAULT_TONE_TYPE_STRING,
        vCharType: String = DEFAULT_V_CHAR_TYPE_STRING,
    ): String {
        return runBlocking {
            Pinyin4jPluginHost.of(
                globalContext,
                str,
                Bundle().apply {
                    putString(Pinyin4jOptionKeys.SEPARATOR, separator)
                    putString(Pinyin4jOptionKeys.CASE_TYPE, caseType)
                    putString(Pinyin4jOptionKeys.TONE_TYPE, toneType)
                    putString(Pinyin4jOptionKeys.V_CHAR_TYPE, vCharType)
                },
            )
        }
    }

    @RhinoSingletonFunctionInterface
    fun `as`(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { o ->
        runBlocking {
            Pinyin4jPluginHost.asTone(globalContext, coerceString(o, ""))
        }
    }

    private fun normalizeCaseType(case: Any?): String {
        return when (coerceStringUppercase(case, DEFAULT_CASE_TYPE_STRING)) {
            "LOWERCASE", "LOW", "L", "0" -> "LOWERCASE"
            "UPPERCASE", "UP", "U", "1" -> "UPPERCASE"
            else -> throw IllegalArgumentException("Invalid case type: ${Context.toString(case)}")
        }
    }

    private fun normalizeToneType(tone: Any?): String {
        return when (coerceStringUppercase(tone, DEFAULT_TONE_TYPE_STRING)) {
            "WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM" -> "WITH_TONE_NUMBER"
            "WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "0" -> "WITHOUT_TONE"
            "WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE", "1" -> "WITH_TONE_MARK"
            else -> throw IllegalArgumentException("Invalid tone type: ${Context.toString(tone)}")
        }
    }

    private fun normalizeVCharType(vChar: Any?, toneType: String): String {
        val defaultValue = if (toneType == "WITH_TONE_MARK") "WITH_U_UNICODE" else DEFAULT_V_CHAR_TYPE_STRING
        return when (coerceStringUppercase(vChar, defaultValue)) {
            "WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:" -> "WITH_U_AND_COLON"
            "WITH_V", "V" -> "WITH_V"
            "WITH_U_UNICODE", "U_UNICODE", "UNICODE", "U", "Ü" -> "WITH_U_UNICODE"
            else -> throw IllegalArgumentException("Invalid v char type: ${Context.toString(vChar)}")
        }
    }
}
